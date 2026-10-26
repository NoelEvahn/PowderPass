package dev.powderpass.volleyball.physics;

import dev.powderpass.volleyball.math.Vec3;

/**
 * Free flight dynamics: gravity, quadratic drag with drag crisis, Magnus lift from spin
 * and the knuckle wobble of spinless float serves.
 */
public final class BallIntegrator {

    private final Vec3 acc = new Vec3();
    private final Vec3 tmp = new Vec3();
    private final Vec3 side = new Vec3();
    private final Vec3 lift = new Vec3();
    private static final Vec3 UP = new Vec3(0, 1, 0);

    /** Computes the acceleration acting on the body into {@code out}. */
    public Vec3 acceleration(BallBody body, PhysicsParams p, Vec3 out) {
        out.set(0, -p.gravity(), 0);
        Vec3 v = body.vel;
        double speed = v.length();
        if (speed < 1e-6) {
            return out;
        }
        double aero = p.aeroFactor();
        // Drag: a = -0.5 rho Cd A / m * |v| v
        double cd = p.dragCoefficient(speed);
        out.addScaled(v, -aero * cd * speed);

        // Magnus lift: direction w x v, coefficient grows with spin parameter S = r|w|/|v|
        double spinMag = body.spin.length();
        if (spinMag > 1e-4) {
            double s = p.radius() * spinMag / speed;
            double cl = Math.min(p.liftSlope() * s, p.maxLiftCoefficient());
            tmp.set(body.spin).cross(v);
            double tl = tmp.length();
            if (tl > 1e-9) {
                out.addScaled(tmp, aero * cl * speed * speed / tl);
            }
        }

        // Knuckle: spinless balls near the drag crisis shed asymmetric vortices -> lateral wobble.
        if (body.knuckle && p.knuckleStrength() > 0) {
            double spinFactor = Math.max(0, 1 - spinMag / Math.max(0.01, p.knuckleSpinThreshold()));
            double dv = (speed - p.dragCrisisSpeed()) / 6.0;
            double window = Math.exp(-dv * dv);
            double ck = 0.15 * p.knuckleStrength() * spinFactor * window;
            if (ck > 1e-5) {
                side.set(v).cross(UP);
                if (side.lengthSq() < 1e-9) {
                    side.set(1, 0, 0);
                }
                side.normalize();
                lift.set(side).cross(v).normalize();
                double t = body.flightTime;
                double mag = aero * ck * speed * speed;
                out.addScaled(side, mag * body.noise.a(t));
                out.addScaled(lift, mag * 0.6 * body.noise.b(t));
            }
        }
        return out;
    }

    /** Semi-implicit Euler step of length {@code h}. */
    public void step(BallBody body, PhysicsParams p, double h) {
        acceleration(body, p, acc);
        body.vel.addScaled(acc, h);
        body.pos.addScaled(body.vel, h);
        if (p.spinDecaySeconds() > 0) {
            body.spin.mul(Math.exp(-h / p.spinDecaySeconds()));
        }
        body.flightTime += h;
    }
}
