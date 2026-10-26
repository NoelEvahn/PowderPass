package dev.powderpass.volleyball.court;

/**
 * Mass-spring membrane approximating the net. Nodes form a (cols+1) x (rows+1) grid in the net plane and
 * move only along the net normal (u). The outer columns are tied to the posts; the top row is the stiff
 * cable. Pure Java so it can be unit tested.
 */
public final class NetSimulation {

    private final int cols;
    private final int rows;
    private final double halfLength;
    private final double bottom;
    private final double height;
    private final double[] d;
    private final double[] v;
    private final double dx;
    private final double dy;
    private boolean active;

    /** wave speed squared (m^2/s^2) */
    private static final double TENSION = 30.0;
    private static final double SPRING = 18.0;
    private static final double DAMPING = 5.5;
    private static final double MAX_DISPLACEMENT = 0.45;

    public NetSimulation(int cols, int rows, double halfLength, double bottom, double height) {
        this.cols = cols;
        this.rows = rows;
        this.halfLength = halfLength;
        this.bottom = bottom;
        this.height = height;
        this.d = new double[(cols + 1) * (rows + 1)];
        this.v = new double[(cols + 1) * (rows + 1)];
        this.dx = 2 * halfLength / cols;
        this.dy = height / rows;
    }

    public int cols() {
        return cols;
    }

    public int rows() {
        return rows;
    }

    public boolean active() {
        return active;
    }

    private int idx(int i, int j) {
        return j * (cols + 1) + i;
    }

    /** Lateral (w) coordinate of column i. */
    public double nodeW(int i) {
        return -halfLength + i * dx;
    }

    /** Height of row j (0 = bottom). */
    public double nodeY(int j) {
        return bottom + j * dy;
    }

    public double displacement(int i, int j) {
        return d[idx(i, j)];
    }

    /**
     * Applies an impulse from a ball hit.
     *
     * @param w        lateral impact position
     * @param y        impact height
     * @param velocity normal velocity transferred to the net (signed, along +u)
     * @param radius   radius of influence
     */
    public void impulse(double w, double y, double velocity, double radius) {
        double r2 = radius * radius;
        for (int j = 0; j <= rows; j++) {
            for (int i = 1; i < cols; i++) {
                double ddw = nodeW(i) - w;
                double ddy = nodeY(j) - y;
                double dist2 = ddw * ddw + ddy * ddy;
                if (dist2 > r2 * 4) {
                    continue;
                }
                double fall = Math.exp(-dist2 / r2);
                double stiff = j == rows ? 0.35 : 1.0;
                v[idx(i, j)] += velocity * fall * stiff;
            }
        }
        active = true;
    }

    /** Advances the simulation. Returns true while the net is moving. */
    public boolean step(double dt) {
        if (!active) {
            return false;
        }
        int sub = 6;
        double h = dt / sub;
        double kx = TENSION / (dx * dx);
        double ky = TENSION / (dy * dy);
        double energy = 0;
        for (int s = 0; s < sub; s++) {
            for (int j = 0; j <= rows; j++) {
                for (int i = 1; i < cols; i++) {
                    int k = idx(i, j);
                    double c = d[k];
                    double lap = kx * (d[idx(i - 1, j)] + d[idx(i + 1, j)] - 2 * c);
                    if (j > 0) {
                        lap += ky * (d[idx(i, j - 1)] - c);
                    }
                    if (j < rows) {
                        lap += ky * (d[idx(i, j + 1)] - c);
                    }
                    double spring = j == rows ? SPRING * 6 : SPRING;
                    double a = lap - spring * c - DAMPING * v[k];
                    v[k] += a * h;
                }
            }
            for (int j = 0; j <= rows; j++) {
                for (int i = 1; i < cols; i++) {
                    int k = idx(i, j);
                    d[k] += v[k] * h;
                    if (d[k] > MAX_DISPLACEMENT) {
                        d[k] = MAX_DISPLACEMENT;
                        v[k] = Math.min(0, v[k]);
                    } else if (d[k] < -MAX_DISPLACEMENT) {
                        d[k] = -MAX_DISPLACEMENT;
                        v[k] = Math.max(0, v[k]);
                    }
                }
            }
        }
        for (int k = 0; k < d.length; k++) {
            energy += d[k] * d[k] + v[k] * v[k] * 0.01;
        }
        if (energy < 1e-6) {
            java.util.Arrays.fill(d, 0);
            java.util.Arrays.fill(v, 0);
            active = false;
        }
        return true;
    }

    /** Bilinear displacement at an arbitrary point of the net. */
    public double displacementAt(double w, double y) {
        double fi = (w + halfLength) / dx;
        double fj = (y - bottom) / dy;
        if (fi < 0 || fj < 0 || fi > cols || fj > rows) {
            return 0;
        }
        int i = Math.min(cols - 1, (int) fi);
        int j = Math.min(rows - 1, (int) fj);
        double tx = fi - i;
        double ty = fj - j;
        double a = d[idx(i, j)] * (1 - tx) + d[idx(i + 1, j)] * tx;
        double b = d[idx(i, j + 1)] * (1 - tx) + d[idx(i + 1, j + 1)] * tx;
        return a * (1 - ty) + b * ty;
    }
}
