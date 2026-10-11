package dev.powderpass.core;

import java.util.concurrent.ConcurrentHashMap;

/** Shared worlds for tests (domain simulations are expensive; reuse them across test classes). */
final class TestWorlds {
    private TestWorlds() {}

    private static final ConcurrentHashMap<Long, PowderWorld> WORLDS = new ConcurrentHashMap<>();

    static PowderWorld world(long seed) {
        return WORLDS.computeIfAbsent(seed, s -> new PowderWorld(s, GenSettings.defaults(), 1L << 30, true));
    }

    static final long SEED_A = 12345L;
    static final long SEED_B = -7377001234567L;
}
