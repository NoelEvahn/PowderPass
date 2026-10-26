package dev.powderpass.volleyball.physics;

import dev.powderpass.volleyball.math.Vec3;

/**
 * Sphere collisions against the net, its top band, the antennas and the posts.
 * Operates on court-local vectors (x = u, y = height, z = w).
 */
public final class NetCollider {

    private final Vec3 normal = new Vec3();

    /** Result of a collision test. */
    public static final class Hit {
        public NetContact contact;
        public double impactSpeed;
        /** contact point in local space */
        public final Vec3 point = new Vec3();

        void reset() {
            contact = null;
            impactSpeed = 0;
        }
    }

    /**
     * Tests and resolves collisions. {@code pos}, {@code vel} and {@code spin} are local and modified in place.
     *
     * @param side the side of the net the ball was on before this step (-1 / +1)
     * @return true if something was touched (details in {@code hit})
     */
    public boolean collide(NetGeometry g, PhysicsParams p, BallBody local, int side, Hit hit) {
        hit.reset();
        double r = p.radius();
        Vec3 pos = local.pos;
        double u = pos.x;
        double y = pos.y;
        double w = pos.z;
        double absW = Math.abs(w);

        // quick reject: far from the net plane
        if (Math.abs(u) > r + 0.3 || y > g.top() + g.antennaAbove() + r + 0.1 || absW > g.postW() + g.postRadius() + r) {
            return false;
        }

        // posts: vertical cylinders at (0, +-postW) from the floor to the top of the net
        if (y < g.top() + 0.1 + r) {
            double pw = Math.copySign(g.postW(), w);
            if (verticalCylinder(local, p, u, w, 0, pw, g.postRadius(), p.wallRestitution(), hit)) {
                hit.contact = NetContact.POST;
                hit.point.set(0, y, pw);
                return true;
            }
        }

        // antennas
        if (y > g.bottom() - r && y < g.top() + g.antennaAbove() + r) {
            double aw = Math.copySign(g.antennaW(), w);
            if (verticalCylinder(local, p, u, w, 0, aw, g.antennaRadius(), p.antennaRestitution(), hit)) {
                hit.contact = NetContact.ANTENNA;
                hit.point.set(0, y, aw);
                return true;
            }
        }

        if (absW > g.halfLength() + r) {
            return false;
        }

        // top band: horizontal cylinder along w
        double cy = g.tapeCenterY();
        double du = u;
        double dy = y - cy;
        double dist = Math.sqrt(du * du + dy * dy);
        double minDist = r + g.tapeRadius();
        if (dist < minDist && absW <= g.halfLength()) {
            if (dist < 1e-6) {
                normal.set(side, 0, 0);
                dist = 0;
            } else {
                normal.set(du / dist, dy / dist, 0);
            }
            pos.x = normal.x * minDist;
            pos.y = cy + normal.y * minDist;
            double speed = Contacts.bounce(local, normal, p.tapeRestitution(), 0.25, r);
            hit.contact = NetContact.TAPE;
            hit.impactSpeed = speed;
            hit.point.set(0, cy + normal.y * g.tapeRadius(), w);
            return true;
        }

        // mesh membrane between bottom and tape
        if (y <= cy && y >= g.bottom() - r * 0.5 && absW <= g.halfLength()) {
            double limit = r + g.meshHalfThickness();
            boolean penetrating = Math.abs(u) < limit || Math.signum(u) != side && side != 0;
            if (penetrating) {
                int s = side == 0 ? (u >= 0 ? 1 : -1) : side;
                pos.x = s * limit;
                normal.set(s, 0, 0);
                Vec3 v = local.vel;
                double vn = v.x * s;
                double speed = 0;
                if (vn < 0) {
                    speed = -vn;
                    v.x = -vn * p.netRestitution() * s;
                    v.y *= p.netTangentialDamping();
                    v.z *= p.netTangentialDamping();
                    local.spin.mul(0.5);
                }
                hit.contact = NetContact.MESH;
                hit.impactSpeed = speed;
                hit.point.set(0, y, w);
                return true;
            }
        }
        return false;
    }

    private boolean verticalCylinder(BallBody local, PhysicsParams p, double u, double w, double cu, double cw,
                                     double radius, double restitution, Hit hit) {
        double du = u - cu;
        double dw = w - cw;
        double d = Math.sqrt(du * du + dw * dw);
        double min = p.radius() + radius;
        if (d >= min) {
            return false;
        }
        if (d < 1e-6) {
            normal.set(1, 0, 0);
        } else {
            normal.set(du / d, 0, dw / d);
        }
        local.pos.x = cu + normal.x * min;
        local.pos.z = cw + normal.z * min;
        hit.impactSpeed = Contacts.bounce(local, normal, restitution, 0.2, p.radius());
        return true;
    }
}
