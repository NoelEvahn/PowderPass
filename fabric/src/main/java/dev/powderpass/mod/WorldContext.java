package dev.powderpass.mod;

import dev.powderpass.core.Diagnostics;
import dev.powderpass.core.GenSettings;
import dev.powderpass.core.PowderWorld;
import dev.powderpass.core.biome.PBiome;
import dev.powderpass.core.deco.Decorator;
import dev.powderpass.core.terrain.ChunkComposer;
import dev.powderpass.core.terrain.TerrainSampler;
import dev.powderpass.core.util.BoundedCache;
import dev.powderpass.core.util.Hash;

/**
 * Everything derived from one (world seed, persisted generation settings) pair. Created lazily by the chunk
 * generator from the level's {@code RandomState}; shared by worldgen threads, the biome source, commands and the
 * volcano runtime.
 */
public final class WorldContext {
    public final long seed;
    public final PowderWorld world;
    public final ChunkComposer composer;
    public final Decorator decorator;
    public final Diagnostics diagnostics;
    private final BoundedCache<PBiome> quartBiomes = BoundedCache.ofCount(65536);

    public WorldContext(long seed, GenSettings settings, int minY, int height, long simCacheBytes) {
        this.seed = seed;
        this.world = new PowderWorld(seed, settings, simCacheBytes, true);
        this.composer = new ChunkComposer(world, minY, height);
        this.decorator = new Decorator(composer);
        this.diagnostics = new Diagnostics(world, composer.sampler, composer.biomes);
    }

    public TerrainSampler sampler() {
        return composer.sampler;
    }

    /** Surface biome of a quart column (4x4 blocks), sampled at its centre and cached. */
    public PBiome quartSurface(int qx, int qz) {
        return quartBiomes.getOrCompute(Hash.pack(qx, qz), k -> {
            TerrainSampler.Column c = new TerrainSampler.Column();
            composer.sampler.sample(qx * 4 + 2, qz * 4 + 2, c);
            return composer.biomes.surface(c);
        });
    }

    /** Biome at quart coordinates including cave biomes (single queries; chunks use a batched resolver). */
    public PBiome quartBiome(int qx, int qy, int qz) {
        PBiome surface = quartSurface(qx, qz);
        int y = qy * 4 + 2;
        if (surface.isOcean() || y > 40) return surface;
        TerrainSampler.Column c = new TerrainSampler.Column();
        composer.sampler.sample(qx * 4 + 2, qz * 4 + 2, c);
        return composer.biomes.at(c, surface, y);
    }
}
