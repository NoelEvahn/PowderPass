package dev.powderpass.core.noise;

import dev.powderpass.core.util.Hash;

/**
 * Stateless simplex noise. Gradients come from hashing (seed, lattice coordinates), so there are no permutation
 * tables, no shared mutable state, and evaluation is safe from any thread. Output roughly in [-1, 1].
 */
public final class Simplex {
    private Simplex() {}

    private static final double F2 = 0.5 * (Math.sqrt(3.0) - 1.0);
    private static final double G2 = (3.0 - Math.sqrt(3.0)) / 6.0;
    private static final int GRAD2_COUNT = 24;
    private static final double[] GX2 = new double[GRAD2_COUNT];
    private static final double[] GZ2 = new double[GRAD2_COUNT];

    static {
        for (int i = 0; i < GRAD2_COUNT; i++) {
            double a = (i + 0.5) * Math.PI * 2.0 / GRAD2_COUNT;
            GX2[i] = Math.cos(a);
            GZ2[i] = Math.sin(a);
        }
    }

    private static int g2(long seed, int i, int j) {
        long h = Hash.mix(seed ^ (i * 0x9E3779B97F4A7C15L) ^ (j * 0xC2B2AE3D27D4EB4FL));
        return (int) ((h >>> 40) % GRAD2_COUNT);
    }

    public static double noise2(long seed, double x, double y) {
        double s = (x + y) * F2;
        int i = fastFloor(x + s);
        int j = fastFloor(y + s);
        double t = (i + j) * G2;
        double x0 = x - (i - t);
        double y0 = y - (j - t);
        int i1, j1;
        if (x0 > y0) { i1 = 1; j1 = 0; } else { i1 = 0; j1 = 1; }
        double x1 = x0 - i1 + G2, y1 = y0 - j1 + G2;
        double x2 = x0 - 1.0 + 2.0 * G2, y2 = y0 - 1.0 + 2.0 * G2;
        double n = 0.0;
        double t0 = 0.5 - x0 * x0 - y0 * y0;
        if (t0 > 0) {
            int g = g2(seed, i, j);
            t0 *= t0;
            n += t0 * t0 * (GX2[g] * x0 + GZ2[g] * y0);
        }
        double t1 = 0.5 - x1 * x1 - y1 * y1;
        if (t1 > 0) {
            int g = g2(seed, i + i1, j + j1);
            t1 *= t1;
            n += t1 * t1 * (GX2[g] * x1 + GZ2[g] * y1);
        }
        double t2 = 0.5 - x2 * x2 - y2 * y2;
        if (t2 > 0) {
            int g = g2(seed, i + 1, j + 1);
            t2 *= t2;
            n += t2 * t2 * (GX2[g] * x2 + GZ2[g] * y2);
        }
        return 70.0 * n;
    }

    // ---- 3D ----
    private static final double F3 = 1.0 / 3.0;
    private static final double G3 = 1.0 / 6.0;
    private static final int[][] GRAD3 = {
        {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
        {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
        {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}
    };

    private static int g3(long seed, int i, int j, int k) {
        long h = Hash.mix(seed ^ (i * 0x9E3779B97F4A7C15L) ^ (j * 0xC2B2AE3D27D4EB4FL) ^ (k * 0x165667B19E3779F9L));
        return (int) ((h >>> 40) % 12);
    }

    public static double noise3(long seed, double x, double y, double z) {
        double s = (x + y + z) * F3;
        int i = fastFloor(x + s), j = fastFloor(y + s), k = fastFloor(z + s);
        double t = (i + j + k) * G3;
        double x0 = x - (i - t), y0 = y - (j - t), z0 = z - (k - t);
        int i1, j1, k1, i2, j2, k2;
        if (x0 >= y0) {
            if (y0 >= z0) { i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 1; k2 = 0; }
            else if (x0 >= z0) { i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 0; k2 = 1; }
            else { i1 = 0; j1 = 0; k1 = 1; i2 = 1; j2 = 0; k2 = 1; }
        } else {
            if (y0 < z0) { i1 = 0; j1 = 0; k1 = 1; i2 = 0; j2 = 1; k2 = 1; }
            else if (x0 < z0) { i1 = 0; j1 = 1; k1 = 0; i2 = 0; j2 = 1; k2 = 1; }
            else { i1 = 0; j1 = 1; k1 = 0; i2 = 1; j2 = 1; k2 = 0; }
        }
        double x1 = x0 - i1 + G3, y1 = y0 - j1 + G3, z1 = z0 - k1 + G3;
        double x2 = x0 - i2 + 2 * G3, y2 = y0 - j2 + 2 * G3, z2 = z0 - k2 + 2 * G3;
        double x3 = x0 - 1 + 3 * G3, y3 = y0 - 1 + 3 * G3, z3 = z0 - 1 + 3 * G3;
        double n = 0;
        double t0 = 0.6 - x0 * x0 - y0 * y0 - z0 * z0;
        if (t0 > 0) { int[] g = GRAD3[g3(seed, i, j, k)]; t0 *= t0; n += t0 * t0 * (g[0] * x0 + g[1] * y0 + g[2] * z0); }
        double t1 = 0.6 - x1 * x1 - y1 * y1 - z1 * z1;
        if (t1 > 0) { int[] g = GRAD3[g3(seed, i + i1, j + j1, k + k1)]; t1 *= t1; n += t1 * t1 * (g[0] * x1 + g[1] * y1 + g[2] * z1); }
        double t2 = 0.6 - x2 * x2 - y2 * y2 - z2 * z2;
        if (t2 > 0) { int[] g = GRAD3[g3(seed, i + i2, j + j2, k + k2)]; t2 *= t2; n += t2 * t2 * (g[0] * x2 + g[1] * y2 + g[2] * z2); }
        double t3 = 0.6 - x3 * x3 - y3 * y3 - z3 * z3;
        if (t3 > 0) { int[] g = GRAD3[g3(seed, i + 1, j + 1, k + 1)]; t3 *= t3; n += t3 * t3 * (g[0] * x3 + g[1] * y3 + g[2] * z3); }
        return 32.0 * n;
    }

    private static int fastFloor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    // ---- fractal helpers ----

    /** Fractal Brownian motion, normalized to roughly [-1,1]. */
    public static double fbm(long seed, double x, double z, int octaves, double lacunarity, double gain) {
        double sum = 0, amp = 1, norm = 0, f = 1;
        for (int o = 0; o < octaves; o++) {
            // Rotate each octave to break lattice alignment.
            double rx = x * f, rz = z * f;
            double c = ROT_C[o & 7], s = ROT_S[o & 7];
            sum += amp * noise2(seed + o * 0x632BE59BD9B4E019L, rx * c - rz * s, rx * s + rz * c);
            norm += amp;
            amp *= gain;
            f *= lacunarity;
        }
        return sum / norm;
    }

    /**
     * Ridged multifractal (Musgrave), output roughly [0,1]. Sharp crests where the base noise crosses zero; each
     * octave is weighted by the previous signal so detail concentrates on ridges.
     */
    public static double ridged(long seed, double x, double z, int octaves, double lacunarity, double gain) {
        double sum = 0, amp = 0.5, f = 1, weight = 1, norm = 0;
        for (int o = 0; o < octaves; o++) {
            double rx = x * f, rz = z * f;
            double c = ROT_C[(o + 3) & 7], s = ROT_S[(o + 3) & 7];
            double n = noise2(seed + o * 0x5851F42D4C957F2DL, rx * c - rz * s, rx * s + rz * c);
            n = 1.0 - Math.abs(n);
            n *= n;
            n *= weight;
            weight = Math.min(1.0, Math.max(0.0, n * 2.0));
            sum += n * amp;
            norm += amp;
            amp *= gain;
            f *= lacunarity;
        }
        return sum / norm;
    }

    private static final double[] ROT_C = new double[8];
    private static final double[] ROT_S = new double[8];

    static {
        for (int i = 0; i < 8; i++) {
            double a = i * 0.7853981 + 0.31;
            ROT_C[i] = Math.cos(a);
            ROT_S[i] = Math.sin(a);
        }
    }
}
