package dev.powderpass.lift.geom;

import org.joml.Vector3d;

import java.util.List;

/** A closed polyline parameterised by arc length; the haul rope of a lift. */
public final class CablePath {

    private final double[] xs;
    private final double[] ys;
    private final double[] zs;
    private final double[] cum;
    private final double length;

    public CablePath(List<Vector3d> points) {
        if (points.size() < 3) {
            throw new IllegalArgumentException("A loop needs at least three points");
        }
        int n = points.size();
        xs = new double[n + 1];
        ys = new double[n + 1];
        zs = new double[n + 1];
        cum = new double[n + 1];
        for (int i = 0; i <= n; i++) {
            Vector3d p = points.get(i % n);
            xs[i] = p.x;
            ys[i] = p.y;
            zs[i] = p.z;
            if (i > 0) {
                double dx = xs[i] - xs[i - 1];
                double dy = ys[i] - ys[i - 1];
                double dz = zs[i] - zs[i - 1];
                cum[i] = cum[i - 1] + Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
        }
        length = cum[n];
    }

    public double length() {
        return length;
    }

    /** Wraps any arc-length value into [0, length). */
    public double wrap(double s) {
        double w = s % length;
        return w < 0 ? w + length : w;
    }

    private int segment(double s) {
        int lo = 0;
        int hi = cum.length - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (cum[mid] <= s) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /** Position on the rope at arc length {@code s}. */
    public Vector3d position(double s, Vector3d out) {
        double w = wrap(s);
        int i = segment(w);
        double segLen = cum[i + 1] - cum[i];
        double t = segLen <= 1.0e-9 ? 0.0 : (w - cum[i]) / segLen;
        return out.set(xs[i] + (xs[i + 1] - xs[i]) * t,
                ys[i] + (ys[i + 1] - ys[i]) * t,
                zs[i] + (zs[i + 1] - zs[i]) * t);
    }

    /** Unit direction of travel at arc length {@code s}. */
    public Vector3d direction(double s, Vector3d out) {
        double w = wrap(s);
        int i = segment(w);
        out.set(xs[i + 1] - xs[i], ys[i + 1] - ys[i], zs[i + 1] - zs[i]);
        double len = out.length();
        return len < 1.0e-9 ? out.set(0, 0, 1) : out.div(len);
    }

    public int pointCount() {
        return xs.length - 1;
    }

    public Vector3d point(int i, Vector3d out) {
        return out.set(xs[i], ys[i], zs[i]);
    }
}
