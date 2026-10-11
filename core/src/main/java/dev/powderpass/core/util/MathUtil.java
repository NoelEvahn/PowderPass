package dev.powderpass.core.util;

public final class MathUtil {
    private MathUtil() {}

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    public static double smoothstep(double e0, double e1, double x) {
        double t = clamp((x - e0) / (e1 - e0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    public static double smootherstep(double e0, double e1, double x) {
        double t = clamp((x - e0) / (e1 - e0), 0.0, 1.0);
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    public static int floorDiv(int a, int b) {
        return Math.floorDiv(a, b);
    }

    public static int fastFloor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    public static double sq(double v) {
        return v * v;
    }

    /** Smooth minimum (polynomial), k = blend width. */
    public static double smin(double a, double b, double k) {
        if (k <= 0) return Math.min(a, b);
        double h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
        return lerp(h, b, a) - k * h * (1.0 - h);
    }

    public static double smax(double a, double b, double k) {
        return -smin(-a, -b, k);
    }

    /** Catmull-Rom cubic interpolation of four samples at t in [0,1]. */
    public static double cubic(double p0, double p1, double p2, double p3, double t) {
        return p1 + 0.5 * t * (p2 - p0 + t * (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3 + t * (3.0 * (p1 - p2) + p3 - p0)));
    }

    /** Distance from point to segment, also writes the projection parameter into out[0]. */
    public static double distToSegment(double px, double pz, double ax, double az, double bx, double bz, double[] tOut) {
        double dx = bx - ax, dz = bz - az;
        double len2 = dx * dx + dz * dz;
        double t = len2 > 1e-9 ? ((px - ax) * dx + (pz - az) * dz) / len2 : 0.0;
        t = clamp(t, 0.0, 1.0);
        if (tOut != null) tOut[0] = t;
        double cx = ax + t * dx - px, cz = az + t * dz - pz;
        return Math.sqrt(cx * cx + cz * cz);
    }
}
