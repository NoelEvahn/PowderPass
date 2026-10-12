package com.powderpass.backrooms.util;

/** Smooth 2D value noise in [0,1]; used for zoning so that architectural sub-styles blend in large patches. */
public final class ValueNoise {
    private ValueNoise() {
    }

    public static double sample(long seed, double x, double z) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double fx = x - x0;
        double fz = z - z0;
        double sx = fx * fx * (3 - 2 * fx);
        double sz = fz * fz * (3 - 2 * fz);
        double a = Rng.unit(Rng.hash(seed, x0, z0));
        double b = Rng.unit(Rng.hash(seed, x0 + 1, z0));
        double c = Rng.unit(Rng.hash(seed, x0, z0 + 1));
        double d = Rng.unit(Rng.hash(seed, x0 + 1, z0 + 1));
        double ab = a + (b - a) * sx;
        double cd = c + (d - c) * sx;
        return ab + (cd - ab) * sz;
    }

    /** Two-octave fractal noise; scale is in blocks per noise unit. */
    public static double fractal(long seed, double x, double z, double scale) {
        double n = sample(seed, x / scale, z / scale) * 0.7
                + sample(seed ^ 0x5DEECE66DL, x / (scale * 0.45), z / (scale * 0.45)) * 0.3;
        return Math.max(0, Math.min(1, n));
    }
}
