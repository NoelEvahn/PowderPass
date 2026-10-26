package dev.powderpass.volleyball.physics;

import java.util.SplittableRandom;

/**
 * Smooth pseudo random 2D signal used for the lateral "knuckle" forces of a float serve.
 * A sum of a few incommensurate sines gives a continuous, non repeating wobble without allocation.
 */
public final class KnuckleNoise {

    private static final int OCTAVES = 3;
    private final SplittableRandom random;
    private final double[] freqA = new double[OCTAVES];
    private final double[] freqB = new double[OCTAVES];
    private final double[] phaseA = new double[OCTAVES];
    private final double[] phaseB = new double[OCTAVES];

    public KnuckleNoise(long seed) {
        this.random = new SplittableRandom(seed);
        reseed();
    }

    public void reseed() {
        for (int i = 0; i < OCTAVES; i++) {
            freqA[i] = (0.35 + random.nextDouble() * 0.75) * (1 + i * 1.6);
            freqB[i] = (0.35 + random.nextDouble() * 0.75) * (1 + i * 1.6);
            phaseA[i] = random.nextDouble() * Math.PI * 2;
            phaseB[i] = random.nextDouble() * Math.PI * 2;
        }
    }

    public double a(double t) {
        return sample(t, freqA, phaseA);
    }

    public double b(double t) {
        return sample(t, freqB, phaseB);
    }

    private static double sample(double t, double[] f, double[] p) {
        double sum = 0;
        double amp = 1;
        double norm = 0;
        for (int i = 0; i < OCTAVES; i++) {
            sum += Math.sin(t * f[i] * Math.PI * 2 + p[i]) * amp;
            norm += amp;
            amp *= 0.5;
        }
        return sum / norm;
    }
}
