package dev.powderpass.course;

/**
 * A gate: the line segment between two poles at height {@code y}, crossed in the direction of its facing
 * normal (nx, nz).
 */
public record GateLine(double ax, double az, double bx, double bz, double y, double nx, double nz) {

    /** How a movement segment crossed a gate line. */
    public record Crossing(double t, boolean inside, double outsideDistance) {
    }

    /**
     * Builds a gate centred on (cx, cz) facing {@code yaw} (Minecraft degrees), {@code width} blocks wide.
     */
    public static GateLine centered(double cx, double y, double cz, double yaw, double width) {
        double r = Math.toRadians(yaw);
        double fx = -Math.sin(r);
        double fz = Math.cos(r);
        // left of the facing direction is (cos, sin)
        double lx = Math.cos(r);
        double lz = Math.sin(r);
        double h = width / 2.0;
        return new GateLine(cx + lx * h, cz + lz * h, cx - lx * h, cz - lz * h, y, fx, fz);
    }

    public double centerX() {
        return (ax + bx) / 2.0;
    }

    public double centerZ() {
        return (az + bz) / 2.0;
    }

    public double width() {
        return Math.hypot(bx - ax, bz - az);
    }

    /**
     * Tests the movement p0 -&gt; p1 against this gate.
     *
     * @param maxOutside how far beyond the poles a crossing still counts as "passing the gate on the wrong
     *                   side" (anything further is ignored entirely)
     * @param maxDy      maximum height difference between mover and gate
     * @return the crossing, or {@code null} if the gate line was not crossed forwards
     */
    public Crossing cross(double p0x, double p0z, double p1x, double p1z, double py, double maxOutside, double maxDy) {
        if (Math.abs(py - y) > maxDy) {
            return null;
        }
        double dx = p1x - p0x;
        double dz = p1z - p0z;
        if (dx * nx + dz * nz <= 0.0) {
            return null; // moving backwards through (or parallel to) the gate
        }
        double ex = bx - ax;
        double ez = bz - az;
        double denom = dx * ez - dz * ex;
        if (Math.abs(denom) < 1.0e-12) {
            return null;
        }
        double qx = ax - p0x;
        double qz = az - p0z;
        double t = (qx * ez - qz * ex) / denom;
        double u = (qx * dz - qz * dx) / denom;
        if (t < 0.0 || t > 1.0) {
            return null;
        }
        double len = Math.hypot(ex, ez);
        if (u >= 0.0 && u <= 1.0) {
            return new Crossing(t, true, 0.0);
        }
        double outside = u < 0.0 ? -u * len : (u - 1.0) * len;
        if (outside > maxOutside) {
            return null;
        }
        return new Crossing(t, false, outside);
    }
}
