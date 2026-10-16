package com.powderpass.backrooms.gen;

import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.gen.levels.HabitableGenerator;
import com.powderpass.backrooms.gen.levels.Level0Generator;
import com.powderpass.backrooms.gen.levels.OfficeGenerator;
import com.powderpass.backrooms.gen.levels.PlainsGenerator;
import com.powderpass.backrooms.gen.levels.PoolroomsGenerator;
import com.powderpass.backrooms.util.Rng;

/** Factory for level generators. Each level gets an independent seed stream derived from the base seed. */
public final class Generators {
    /** Bumped whenever generation changes in a way that would leave seams in previously generated worlds. */
    public static final int VERSION = 5;

    private Generators() {
    }

    public static long levelSeed(long baseSeed, Level level) {
        return Rng.hash(baseSeed, Rng.salt(level.key()));
    }

    public static LevelGenerator create(Level level, long baseSeed, GenSettings gs) {
        long s = levelSeed(baseSeed, level);
        return switch (level) {
            case LEVEL_0 -> new Level0Generator(s, gs);
            case LEVEL_1 -> new HabitableGenerator(s, gs);
            case POOLROOMS -> new PoolroomsGenerator(s, gs);
            case LEVEL_4 -> new OfficeGenerator(s, gs);
            case PLAINS -> new PlainsGenerator(s, gs);
        };
    }
}
