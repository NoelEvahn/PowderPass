package dev.powderpass.volleyball.physics;

import dev.powderpass.volleyball.math.CourtFrame;
import dev.powderpass.volleyball.math.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Advances a ball by one server tick using adaptive sub-stepping, resolving world and net collisions.
 * Not thread safe: one simulator per ball.
 */
public final class BallSimulator {

    public static final double TICK = 0.05;

    private final BallIntegrator integrator = new BallIntegrator();
    private final NetCollider netCollider = new NetCollider();
    private final NetCollider.Hit netHit = new NetCollider.Hit();
    private final List<Aabb> boxes = new ArrayList<>();
    private final Vec3 normal = new Vec3();
    private final Vec3 tmp = new Vec3();
    private final Vec3 localPoint = new Vec3();
    private final BallBody local = new BallBody(0);
    private int lastSubsteps;

    public int lastSubsteps() {
        return lastSubsteps;
    }

    /** Simulates {@code dt} seconds (normally one tick). */
    public void tick(BallBody body, PhysicsParams p, CollisionWorld world, CourtNet net, SimulationListener listener, double dt) {
        if (body.resting) {
            return;
        }
        double r = p.radius();
        double speed = body.vel.length();
        int steps = (int) Math.ceil(speed * dt / (r * 0.5));
        steps = Math.max(4, Math.min(p.maxSubsteps(), steps));
        lastSubsteps = steps;
        double h = dt / steps;

        // broad phase: one query covering the whole tick of motion
        double reach = speed * dt + r + 0.6;
        boxes.clear();
        world.collect(body.pos.x - reach, body.pos.y - reach - 0.6, body.pos.z - reach,
                body.pos.x + reach, body.pos.y + reach, body.pos.z + reach, boxes);

        body.onGround = false;
        for (int i = 0; i < steps; i++) {
            int sideBefore = net != null ? sideOf(net.frame(), body.pos) : 0;
            double uBefore = 0;
            double yBefore = 0;
            double wBefore = 0;
            if (net != null) {
                CourtFrame f = net.frame();
                uBefore = f.toU(body.pos.x, body.pos.z);
                wBefore = f.toW(body.pos.x, body.pos.z);
                yBefore = f.toLocalY(body.pos.y);
            }

            integrator.step(body, p, h);

            if (net != null) {
                collideNet(body, p, net, sideBefore, listener);
                CourtFrame f = net.frame();
                double uAfter = f.toU(body.pos.x, body.pos.z);
                if (uBefore != 0 && Math.signum(uAfter) != Math.signum(uBefore) && uAfter != 0) {
                    double t = uBefore / (uBefore - uAfter);
                    double wAt = wBefore + (f.toW(body.pos.x, body.pos.z) - wBefore) * t;
                    double yAt = yBefore + (f.toLocalY(body.pos.y) - yBefore) * t;
                    listener.onNetPlaneCrossed(uBefore < 0 ? -1 : 1, wAt, yAt);
                }
            }

            collideWorld(body, p, listener);

            if (body.onGround) {
                applyRolling(body, p, h);
                if (body.vel.lengthSq() < p.restSpeed() * p.restSpeed()) {
                    body.vel.zero();
                    body.spin.zero();
                    body.resting = true;
                    return;
                }
            }
        }
    }

    private static int sideOf(CourtFrame f, Vec3 pos) {
        return f.toU(pos.x, pos.z) < 0 ? -1 : 1;
    }

    private void collideNet(BallBody body, PhysicsParams p, CourtNet net, int side, SimulationListener listener) {
        CourtFrame f = net.frame();
        f.toLocal(body.pos, local.pos);
        f.dirToLocal(body.vel, local.vel);
        f.dirToLocal(body.spin, local.spin);
        if (netCollider.collide(net.geometry(), p, local, side, netHit)) {
            f.toWorld(local.pos, body.pos);
            f.dirToWorld(local.vel.x, local.vel.y, local.vel.z, body.vel);
            f.dirToWorld(local.spin.x, local.spin.y, local.spin.z, body.spin);
            localPoint.set(netHit.point);
            listener.onNetContact(netHit.contact, localPoint, netHit.impactSpeed);
        }
    }

    private void collideWorld(BallBody body, PhysicsParams p, SimulationListener listener) {
        double r = p.radius();
        for (int pass = 0; pass < 2; pass++) {
            boolean any = false;
            for (int i = 0, n = boxes.size(); i < n; i++) {
                Aabb b = boxes.get(i);
                double pen = sphereAabb(body.pos, r, b, normal);
                if (pen <= 0) {
                    continue;
                }
                any = true;
                body.pos.addScaled(normal, pen);
                boolean floorLike = normal.y > 0.7;
                double approach = -body.vel.dot(normal);
                double impact;
                if (floorLike && approach < 0.9) {
                    // resting / rolling contact: kill the normal component
                    if (approach > 0) {
                        body.vel.addScaled(normal, approach);
                    }
                    impact = Math.max(0, approach);
                } else {
                    impact = Contacts.bounce(body, normal, floorLike ? p.floorRestitution() : p.wallRestitution(),
                            floorLike ? p.floorFriction() : p.wallFriction(), r);
                }
                if (floorLike) {
                    body.onGround = true;
                }
                if (impact > 0.05 || floorLike) {
                    tmp.set(body.pos).addScaled(normal, -r);
                    listener.onBlockContact(tmp, normal, impact, b);
                }
            }
            if (!any) {
                break;
            }
        }
    }

    /** Rolling resistance and spin sync while the ball sits on the floor. */
    private void applyRolling(BallBody body, PhysicsParams p, double h) {
        Vec3 v = body.vel;
        double hs = Math.sqrt(v.x * v.x + v.z * v.z);
        if (hs > 1e-6) {
            double dec = Math.min(hs, p.rollingResistance() * h);
            v.x -= v.x / hs * dec;
            v.z -= v.z / hs * dec;
            // rolling without slipping: w = (n x v) / r with n = up
            body.spin.set(v.z / p.radius(), 0, -v.x / p.radius());
        }
    }

    /**
     * Penetration depth of a sphere into a box, writing the push-out normal.
     *
     * @return penetration (&lt;= 0 when not touching)
     */
    static double sphereAabb(Vec3 c, double r, Aabb b, Vec3 outNormal) {
        double qx = clamp(c.x, b.minX(), b.maxX());
        double qy = clamp(c.y, b.minY(), b.maxY());
        double qz = clamp(c.z, b.minZ(), b.maxZ());
        double dx = c.x - qx;
        double dy = c.y - qy;
        double dz = c.z - qz;
        double d2 = dx * dx + dy * dy + dz * dz;
        if (d2 >= r * r) {
            return 0;
        }
        if (d2 > 1e-12) {
            double d = Math.sqrt(d2);
            outNormal.set(dx / d, dy / d, dz / d);
            return r - d;
        }
        // centre inside the box: push out through the nearest face
        double[] dist = {c.x - b.minX(), b.maxX() - c.x, c.y - b.minY(), b.maxY() - c.y, c.z - b.minZ(), b.maxZ() - c.z};
        int best = 0;
        for (int i = 1; i < 6; i++) {
            if (dist[i] < dist[best]) {
                best = i;
            }
        }
        switch (best) {
            case 0 -> outNormal.set(-1, 0, 0);
            case 1 -> outNormal.set(1, 0, 0);
            case 2 -> outNormal.set(0, -1, 0);
            case 3 -> outNormal.set(0, 1, 0);
            case 4 -> outNormal.set(0, 0, -1);
            default -> outNormal.set(0, 0, 1);
        }
        return r + dist[best];
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
