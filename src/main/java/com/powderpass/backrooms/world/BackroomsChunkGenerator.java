package com.powderpass.backrooms.world;

import com.powderpass.backrooms.gen.ChunkDataCanvas;
import com.powderpass.backrooms.gen.LevelGenerator;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

/**
 * Bridges a pure {@link LevelGenerator} into Paper. Only {@link #generateNoise} writes blocks; every vanilla
 * stage (surface, caves, decorations, structures, mob spawning) is disabled so the level is exactly what the
 * layout describes. Thread-safe: Paper may call this from several worker threads at once.
 */
public final class BackroomsChunkGenerator extends ChunkGenerator {
    private final LevelGenerator gen;
    private final String biomeKey;

    public BackroomsChunkGenerator(LevelGenerator gen, String biomeKey) {
        this.gen = gen;
        this.biomeKey = biomeKey == null || biomeKey.isBlank() ? gen.biomeKey() : biomeKey;
    }

    public LevelGenerator layout() {
        return gen;
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        gen.paint(new ChunkDataCanvas(chunkData), chunkX, chunkZ);
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        Biome b = null;
        try {
            NamespacedKey key = NamespacedKey.fromString(biomeKey);
            if (key != null) b = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).get(key);
        } catch (RuntimeException ignored) {
            // fall through to plains
        }
        Biome biome = b != null ? b : Biome.PLAINS;
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
                return biome;
            }

            @Override
            public List<Biome> getBiomes(WorldInfo worldInfo) {
                return List.of(biome);
            }
        };
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        int[] s = gen.safeSpot(0, 0, 0);
        return new Location(world, s[0] + 0.5, s[1], s[2] + 0.5);
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }
}
