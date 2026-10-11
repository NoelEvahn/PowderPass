package dev.powderpass.mod;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.powderpass.core.biome.PBiome;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Biome source backed by the PowderPass climate/geology classifier. Biomes depend on the world seed, which biome
 * sources do not receive, so the chunk generator binds its {@link WorldContext} here when the level loads. Chunk
 * biomes are filled by the generator directly (batched); this class serves the remaining vanilla queries
 * (structure placement checks, /locate biome, spawning) at quart resolution.
 */
public final class PowderBiomeSource extends BiomeSource {
    public static final MapCodec<PowderBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryOps.retrieveGetter(Registries.BIOME)
    ).apply(i, i.stable(PowderBiomeSource::new)));

    private final Holder<Biome>[] holders;
    private volatile WorldContext ctx;

    @SuppressWarnings("unchecked")
    public PowderBiomeSource(HolderGetter<Biome> biomes) {
        PBiome[] all = PBiome.values();
        holders = new Holder[all.length];
        for (PBiome b : all) {
            holders[b.ordinal()] = biomes.getOrThrow(ResourceKey.create(Registries.BIOME, Identifier.parse(b.id())));
        }
    }

    void bind(WorldContext context) {
        this.ctx = context;
    }

    public Holder<Biome> holder(PBiome b) {
        return holders[b.ordinal()];
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Arrays.stream(holders);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int qx, int qy, int qz, Climate.Sampler sampler) {
        WorldContext c = ctx;
        if (c == null) {
            // Only reachable if vanilla queries biomes before the level finished loading.
            return holders[PBiome.ROLLING_GRASSLAND.ordinal()];
        }
        return holders[c.quartBiome(qx, qy, qz).ordinal()];
    }
}
