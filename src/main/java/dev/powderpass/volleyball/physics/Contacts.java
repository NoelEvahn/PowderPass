package dev.powderpass.volleyball.physics;

import dev.powderpass.volleyball.math.Vec3;

/** Impulse based contact response for a hollow sphere (I = 2/3 m r^2). */
public final class Contacts {

    private Contacts() {
    }

    /**
     * Resolves a collision against a static surface with unit normal {@code n} (pointing toward the ball).
     *
     * @return the approach speed along the normal (positive when an impact happened)
     */
    public static double bounce(BallBody body, Vec3 n, double restitution, double friction, double radius) {
        Vec3 v = body.vel;
        double vn = v.dot(n);
        if (vn >= 0) {
            return 0;
        }
        // contact point velocity: v + w x (-r n)
        Vec3 wxn = Vec3.cross(body.spin, n);
        double cx = v.x - radius * wxn.x;
        double cy = v.y - radius * wxn.y;
        double cz = v.z - radius * wxn.z;
        double cn = cx * n.x + cy * n.y + cz * n.z;
        // tangential slip velocity
        double tx = cx - cn * n.x;
        double ty = cy - cn * n.y;
        double tz = cz - cn * n.z;
        double slip = Math.sqrt(tx * tx + ty * ty + tz * tz);

        // normal impulse
        double jn = -(1 + restitution) * vn;
        v.addScaled(n, jn);

        if (slip > 1e-6 && friction > 0) {
            // impulse needed to stop slipping for a hollow sphere: dv = -slip / (1 + m r^2 / I) = -0.4 slip
            double stop = 0.4 * slip;
            double max = friction * jn;
            double dvMag = Math.min(stop, max);
            double dx = -tx / slip * dvMag;
            double dy = -ty / slip * dvMag;
            double dz = -tz / slip * dvMag;
            v.add(dx, dy, dz);
            // dw = -(3 / 2r) (n x dv)
            Vec3 dv = new Vec3(dx, dy, dz);
            Vec3 dw = Vec3.cross(n, dv).mul(-1.5 / radius);
            body.spin.add(dw);
        }
        return -vn;
    }
}
