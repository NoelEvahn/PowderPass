package dev.powderpass.core.util;

/**
 * Stateless, coordinate-derived hashing. Every random decision in the generator is a pure function of
 * (subsystem seed, integer coordinates), so results never depend on generation order or thread scheduling.
 */
public final class Hash {
    private Hash() {}

    private static final long K1 = 0x9E3779B97F4A7C15L;
    private static final long K2 = 0xC2B2AE3D27D4EB4FL;
    private static final long K3 = 0x165667B19E3779F9L;

    /** SplitMix64 finalizer. */
    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Derives an independent seed stream for a named subsystem. */
    public static long subSeed(long worldSeed, String name) {
        long h = 0x84222325CBF29CE4L;
        for (int i = 0; i < name.length(); i++) {
            h ^= name.charAt(i);
            h *= 0x100000001B3L;
        }
        return mix(worldSeed ^ mix(h));
    }

    public static long hash(long seed, long a) {
        return mix(seed ^ mix(a * K1));
    }

    public static long hash(long seed, long a, long b) {
        return mix(mix(seed ^ (a * K1)) ^ (b * K2));
    }

    public static long hash(long seed, long a, long b, long c) {
        return mix(mix(mix(seed ^ (a * K1)) ^ (b * K2)) ^ (c * K3));
    }

    /** Uniform double in [0,1). */
    public static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    public static double unit(long seed, long a, long b) {
        return unit(hash(seed, a, b));
    }

    public static double unit(long seed, long a, long b, long c) {
        return unit(hash(seed, a, b, c));
    }

    /** Uniform double in [-1,1). */
    public static double signed(long seed, long a, long b) {
        return unit(hash(seed, a, b)) * 2.0 - 1.0;
    }

    public static int range(long h, int bound) {
        return (int) Math.floorMod(h >>> 1, (long) bound);
    }

    /** Packs two ints into a long key (handles negatives). */
    public static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public static int unpackX(long key) {
        return (int) (key >> 32);
    }

    public static int unpackZ(long key) {
        return (int) key;
    }
}
