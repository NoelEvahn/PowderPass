package com.powderpass.backrooms.gen;

import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.gen.levels.Level0Generator;
import com.powderpass.backrooms.gen.levels.Level1Generator;
import com.powderpass.backrooms.gen.levels.Level2Generator;
import com.powderpass.backrooms.gen.levels.Level3Generator;
import com.powderpass.backrooms.gen.levels.Level4Generator;
import com.powderpass.backrooms.gen.levels.Level5Generator;
import com.powderpass.backrooms.gen.levels.PoolroomsGenerator;
import com.powderpass.backrooms.util.Rng;

/** Factory for level generators. Each level gets an independent seed stream derived from the base seed. */
public final class Generators {
    private Generators() {
    }

    public static long levelSeed(long baseSeed, Level level) {
        return Rng.hash(baseSeed, Rng.salt(level.key()));
    }

    public static LevelGenerator create(Level level, long baseSeed, GenSettings gs) {
        long s = levelSeed(baseSeed, level);
        return switch (level) {
            case LEVEL_0 -> new Level0Generator(s, gs);
            case POOLROOMS -> new PoolroomsGenerator(s, gs);
            case LEVEL_1 -> new Level1Generator(s, gs);
            case LEVEL_2 -> new Level2Generator(s, gs);
            case LEVEL_3 -> new Level3Generator(s, gs);
            case LEVEL_4 -> new Level4Generator(s, gs);
            case LEVEL_5 -> new Level5Generator(s, gs);
        };
    }
}
