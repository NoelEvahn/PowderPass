package com.powderpass.backrooms.vehicle;

/**
 * Driving model of one car, independent of the server so it can be unit-tested: throttle, braking and drag on a
 * single speed along the car's heading, a bicycle steering model whose lock shrinks with speed, block collision of
 * the body's footprint, stepping up half blocks, gravity and falling onto lower ground.
 *
 * <p>Coordinates are world blocks; {@code yaw} is Minecraft's (degrees, 0 = +Z, 90 = -X), so the heading is
 * (-sin yaw, 0, cos yaw) and steering right increases yaw.</p>
 */
public final class CarPhysics {
    /** What the world looks like to the car. */
    public interface Terrain {
        /** Does a solid obstacle occupy this point (blocks, other cars)? */
        boolean blocked(double x, double y, double z);

        /** Height of the walkable top inside this block (0..1), or a negative value when it cannot carry a wheel. */
        double top(int x, int y, int z);
    }

    /** Driver controls for one tick. */
    public record Input(boolean forward, boolean back, boolean left, boolean right, boolean brake) {
        public static final Input NONE = new Input(false, false, false, false, false);
        public static final Input PARK = new Input(false, false, false, false, true);
    }

    public static final double MAX_FORWARD = 10.0, MAX_REVERSE = 4.0;
    public static final double ACCEL = 5.0, REVERSE_ACCEL = 3.5, BRAKE = 16.0, DRAG = 1.2, GRAVITY = 24.0;
    public static final double WHEELBASE = 2.67, MAX_STEER = Math.toRadians(34), STEP = 0.55;
    /** Collision footprint: half length and half width (a touch wider than the body, so 2-wide doorways stay closed). */
    public static final double HALF_LENGTH = 2.15, HALF_WIDTH = 1.0;
    private static final double[] BODY_HEIGHTS = {0.6, 1.25};

    public double x, y, z, yaw;
    /** Signed speed along the heading, blocks per second. */
    public double speed;
    /** Steering position -1 (full left) .. 1 (full right). */
    public double steer;
    public double vy;
    /** Accumulated wheel rotation (radians). */
    public double wheelSpin;
    public boolean braking, reversing, onGround = true;
    /** Speed at the last impact (0 when the last step did not hit anything). */
    public double impact;

    public CarPhysics(double x, double y, double z, double yaw) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
    }

    public void step(Input in, double dt, Terrain t) {
        impact = 0;
        // ---------------------------------------------------------------- longitudinal
        braking = in.brake() || (in.forward() && speed < -0.1) || (in.back() && speed > 0.1);
        double target;
        double rate;
        if (braking) {
            target = 0;
            rate = BRAKE;
        } else if (in.forward()) {
            target = MAX_FORWARD;
            rate = ACCEL * (1 - 0.5 * Math.max(0, speed) / MAX_FORWARD); // tails off toward top speed
        } else if (in.back()) {
            target = -MAX_REVERSE;
            rate = REVERSE_ACCEL;
        } else {
            target = 0;
            rate = DRAG + 0.12 * Math.abs(speed);
        }
        if (!onGround) rate = 0.2; // no grip in the air
        speed = approach(speed, target, rate * dt);
        reversing = speed < -0.05 || (in.back() && !braking);
        // ---------------------------------------------------------------- steering
        double want = in.left() == in.right() ? 0 : in.left() ? -1 : 1;
        steer = approach(steer, want, (want == 0 ? 4.0 : 2.8) * dt);
        double lock = MAX_STEER * (1 - 0.55 * Math.min(1, Math.abs(speed) / MAX_FORWARD));
        double yawRate = speed / WHEELBASE * Math.tan(steer * lock); // rad/s
        double nyaw = yaw + Math.toDegrees(yawRate * dt);
        double r = Math.toRadians(nyaw);
        double nx = x - Math.sin(r) * speed * dt, nz = z + Math.cos(r) * speed * dt;
        // ---------------------------------------------------------------- collision: try the full move, then turning in place, then nothing
        if (!collides(nx, y, nz, nyaw, t)) {
            x = nx;
            z = nz;
            yaw = nyaw;
        } else {
            impact = Math.abs(speed);
            speed = -speed * 0.15;
            if (!collides(x, y, z, nyaw, t)) yaw = nyaw;
        }
        yaw = wrap(yaw);
        wheelSpin += speed * dt / 0.33;
        // ---------------------------------------------------------------- ground, step up, gravity
        double ground = ground(t);
        if (ground >= y - 1e-3 && ground <= y + STEP) {
            if (ground > y && collides(x, ground, z, yaw, t)) ground = y;
            y = ground;
            vy = 0;
            onGround = true;
        } else if (ground < y) {
            vy -= GRAVITY * dt;
            y = Math.max(ground, y + vy * dt);
            onGround = y <= ground + 1e-3;
            if (onGround) vy = 0;
        }
    }

    /** Is the car's body, at this pose, inside anything? */
    public boolean collides(double cx, double cy, double cz, double yawDeg, Terrain t) {
        double r = Math.toRadians(yawDeg);
        double fx = -Math.sin(r), fz = Math.cos(r); // forward
        double rx = -Math.cos(r), rz = -Math.sin(r); // right
        int nl = 10, nw = 5;
        for (double h : BODY_HEIGHTS) {
            for (int i = 0; i <= nl; i++) {
                double a = -HALF_LENGTH + 2 * HALF_LENGTH * i / nl;
                for (int s = -1; s <= 1; s += 2) {
                    if (t.blocked(cx + fx * a + rx * s * HALF_WIDTH, cy + h, cz + fz * a + rz * s * HALF_WIDTH)) return true;
                }
            }
            for (int j = 1; j < nw; j++) {
                double b = -HALF_WIDTH + 2 * HALF_WIDTH * j / nw;
                for (int s = -1; s <= 1; s += 2) {
                    if (t.blocked(cx + fx * s * HALF_LENGTH + rx * b, cy + h, cz + fz * s * HALF_LENGTH + rz * b)) return true;
                }
            }
            if (t.blocked(cx, cy + h, cz)) return true;
        }
        return false;
    }

    /** Highest wheel support under the four tyre contact points and the centre, searching from a step above to 3 below. */
    double ground(Terrain t) {
        double r = Math.toRadians(yaw);
        double fx = -Math.sin(r), fz = Math.cos(r), rx = -Math.cos(r), rz = -Math.sin(r);
        double best = Double.NEGATIVE_INFINITY;
        double[][] pts = {{0, 0}, {1.35, 0.8}, {1.35, -0.8}, {-1.32, 0.8}, {-1.32, -0.8}};
        for (double[] p : pts) {
            double px = x + fx * p[0] + rx * p[1], pz = z + fz * p[0] + rz * p[1];
            int bx = (int) Math.floor(px), bz = (int) Math.floor(pz);
            for (int by = (int) Math.floor(y + STEP); by >= (int) Math.floor(y) - 3; by--) {
                double top = t.top(bx, by, bz);
                if (top >= 0 && by + top <= y + STEP + 1e-6) {
                    best = Math.max(best, by + top);
                    break;
                }
            }
        }
        return best == Double.NEGATIVE_INFINITY ? y - 3 : best;
    }

    /** World position of a point given in the car's model space (+Z forward, +X left). */
    public double[] toWorld(double lx, double ly, double lz) {
        double r = Math.toRadians(yaw), c = Math.cos(r), s = Math.sin(r);
        // rotateY(-yaw): x' = x cos + z * (-sin) ... (matches the display transform)
        return new double[]{x + lx * c - lz * s, y + ly, z + lx * s + lz * c};
    }

    public double kmh() {
        return Math.abs(speed) * 3.6;
    }

    static double approach(double v, double target, double d) {
        return v < target ? Math.min(target, v + d) : Math.max(target, v - d);
    }

    static double wrap(double deg) {
        deg %= 360;
        if (deg >= 180) deg -= 360;
        if (deg < -180) deg += 360;
        return deg;
    }
}
