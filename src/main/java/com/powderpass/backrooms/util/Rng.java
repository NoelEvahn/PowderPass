package com.powderpass.backrooms.util;

/**
 * Stateless, deterministic hashing used by every generator. All world generation is a pure
 * function of (seed, coordinates), which is what makes chunks seamless and reproducible no matter
 * in which order or on which thread Paper generates them.
 */
public final class Rng {
    private Rng() {
    }

    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    public static long hash(long seed, long a) {
        return mix(seed ^ mix(a + 0x9E3779B97F4A7C15L));
    }

    public static long hash(long seed, long a, long b) {
        return hash(hash(seed, a), b);
    }

    public static long hash(long seed, long a, long b, long c) {
        return hash(hash(hash(seed, a), b), c);
    }

    public static long hash(long seed, long a, long b, long c, long d) {
        return hash(hash(hash(hash(seed, a), b), c), d);
    }

    /** Uniform double in [0, 1). */
    public static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    /** Uniform int in [0, n). */
    public static int pick(long h, int n) {
        return (int) Long.remainderUnsigned(h, n);
    }

    public static int range(long h, int min, int maxInclusive) {
        return min + pick(h, maxInclusive - min + 1);
    }

    public static boolean chance(long h, double p) {
        return unit(h) < p;
    }

    /** Stable salt from a string (FNV-1a), used to derive independent hash streams. */
    public static long salt(String s) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        return mix(h);
    }
}
