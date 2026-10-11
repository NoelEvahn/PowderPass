package dev.powderpass.core.util;

/** Monotone cubic Hermite spline (Fritsch–Carlson): smooth, no overshoot, no flat steps at knots. */
public final class MonotoneSpline {
    private final double[] xs, ys, ms;

    public MonotoneSpline(double[] xs, double[] ys) {
        int n = xs.length;
        this.xs = xs.clone();
        this.ys = ys.clone();
        double[] d = new double[n - 1];
        for (int i = 0; i < n - 1; i++) d[i] = (ys[i + 1] - ys[i]) / (xs[i + 1] - xs[i]);
        ms = new double[n];
        ms[0] = d[0];
        ms[n - 1] = d[n - 2];
        for (int i = 1; i < n - 1; i++) ms[i] = (d[i - 1] * d[i] <= 0) ? 0 : (d[i - 1] + d[i]) * 0.5;
        for (int i = 0; i < n - 1; i++) {
            if (d[i] == 0) { ms[i] = 0; ms[i + 1] = 0; continue; }
            double a = ms[i] / d[i], b = ms[i + 1] / d[i];
            double s = a * a + b * b;
            if (s > 9) {
                double t = 3 / Math.sqrt(s);
                ms[i] = t * a * d[i];
                ms[i + 1] = t * b * d[i];
            }
        }
    }

    public double eval(double x) {
        int n = xs.length;
        if (x <= xs[0]) return ys[0] + ms[0] * (x - xs[0]);
        if (x >= xs[n - 1]) return ys[n - 1] + ms[n - 1] * (x - xs[n - 1]);
        int i = 0;
        while (x > xs[i + 1]) i++;
        double h = xs[i + 1] - xs[i];
        double t = (x - xs[i]) / h;
        double t2 = t * t, t3 = t2 * t;
        return (2 * t3 - 3 * t2 + 1) * ys[i] + (t3 - 2 * t2 + t) * h * ms[i]
                + (-2 * t3 + 3 * t2) * ys[i + 1] + (t3 - t2) * h * ms[i + 1];
    }
}
