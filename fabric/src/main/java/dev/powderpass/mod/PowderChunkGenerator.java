package dev.powderpass.mod;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.powderpass.core.GenSettings;
import dev.powderpass.core.biome.PBiome;
import dev.powderpass.core.deco.Placements;
import dev.powderpass.core.terrain.B;
import dev.powderpass.core.terrain.ChunkComposer;
import dev.powderpass.core.terrain.ColumnGrid;
import dev.powderpass.core.terrain.TerrainSampler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.material.Fluids;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The PowderPass overworld generator. All terrain, water, materials, caves and natural decoration come from the
 * generation core; vanilla supplies ores and other biome features, structures, lighting and heightmaps.
 *
 * <p>Generation settings are stored in the serialized generator (level data). A freshly created world takes them
 * from the config file; an existing world always uses the stored copy, so editing the config never changes the
 * geography of future chunks in existing worlds.
 */
public final class PowderChunkGenerator extends ChunkGenerator {
    public static final Codec<GenSettings> SETTINGS_CODEC = Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).xmap(
            m -> {
                Map<String, Object> mm = new HashMap<>();
                for (Map.Entry<String, Double> e : m.entrySet()) {
                    boolean flag = e.getKey().equals("glaciation") || e.getKey().equals("vanilla_structures");
                    mm.put(e.getKey(), flag ? (Object) (e.getValue() != 0) : (Object) e.getValue());
                }
                return GenSettings.fromMap(mm);
            },
            gs -> {
                Map<String, Double> m = new HashMap<>();
                for (Map.Entry<String, Object> e : gs.toMap().entrySet()) {
                    Object v = e.getValue();
                    m.put(e.getKey(), v instanceof Boolean b ? (b ? 1.0 : 0.0) : ((Number) v).doubleValue());
                }
                return m;
            });

    public static final MapCodec<PowderChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
            SETTINGS_CODEC.optionalFieldOf("settings").forGetter(g -> Optional.of(g.settings))
    ).apply(i, i.stable(PowderChunkGenerator::new)));

    private static final int MIN_Y = GenSettings.MIN_Y;
    private static final int HEIGHT = 832;

    private final GenSettings settings;
    private final Map<Long, WorldContext> contexts = new ConcurrentHashMap<>();

    public PowderChunkGenerator(BiomeSource biomeSource, Optional<GenSettings> settings) {
        super(biomeSource);
        this.settings = settings.orElseGet(() -> PowderPassMod.config().newWorld).validated();
        if (!(biomeSource instanceof PowderBiomeSource)) {
            PowderPassMod.LOGGER.warn("PowderPass generator is paired with a foreign biome source ({}); biomes will not follow PowderPass terrain",
                    biomeSource.getClass().getName());
        }
    }

    public GenSettings settings() {
        return settings;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    /** World-specific seed derived deterministically from the level seed via the RandomState. */
    static long derivedSeed(RandomState rs) {
        return rs.getOrCreateRandomFactory(Identifier.fromNamespaceAndPath(PowderPassMod.MOD_ID, "world")).at(0, 0, 0).nextLong();
    }

    public WorldContext context(RandomState rs) {
        long seed = derivedSeed(rs);
        WorldContext c = contexts.get(seed);
        if (c != null) return c;
        WorldContext created = contexts.computeIfAbsent(seed, s -> new WorldContext(s, settings, MIN_Y, HEIGHT,
                (long) PowderPassMod.config().runtime.simulationCacheMb << 20));
        if (biomeSource instanceof PowderBiomeSource pbs) pbs.bind(created);
        return created;
    }

    // ------------------------------------------------------------------------------------------------ biomes

    @Override
    public CompletableFuture<ChunkAccess> createBiomes(RandomState rs, Blender blender, StructureManager structureManager, ChunkAccess chunk) {
        WorldContext ctx = context(rs);
        if (!(biomeSource instanceof PowderBiomeSource pbs)) return super.createBiomes(rs, blender, structureManager, chunk);
        ChunkPos cp = chunk.getPos();
        int qx0 = cp.x() * 4, qz0 = cp.z() * 4;
        TerrainSampler.Column[] cols = new TerrainSampler.Column[16];
        PBiome[] surf = new PBiome[16];
        for (int j = 0; j < 4; j++) for (int i = 0; i < 4; i++) {
            TerrainSampler.Column c = new TerrainSampler.Column();
            ctx.sampler().sample((qx0 + i) * 4 + 2, (qz0 + j) * 4 + 2, c);
            cols[j * 4 + i] = c;
            surf[j * 4 + i] = ctx.composer.biomes.surface(c);
        }
        chunk.fillBiomesFromNoise((qx, qy, qz, sampler) -> {
            int i = Math.floorMod(qx - qx0, 4), j = Math.floorMod(qz - qz0, 4);
            int k = j * 4 + i;
            return pbs.holder(ctx.composer.biomes.at(cols[k], surf[k], qy * 4 + 2));
        }, rs.sampler());
        return CompletableFuture.completedFuture(chunk);
    }

    // ------------------------------------------------------------------------------------------------ terrain

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState rs, StructureManager structureManager, ChunkAccess chunk) {
        WorldContext ctx = context(rs);
        ChunkPos cp = chunk.getPos();
        ChunkComposer.Result res = ctx.composer.compose(cp.x(), cp.z());
        int minY = ctx.composer.minY(), height = ctx.composer.height();
        BlockState[] palette = new BlockState[B.values().length];
        for (int i = 0; i < palette.length; i++) palette[i] = BlockPalette.base(i);
        int sections = chunk.getSectionsCount();
        for (int si = 0; si < sections; si++) {
            int sectionY = chunk.getSectionYFromSectionIndex(si) << 4;
            if (sectionY + 15 < minY || sectionY >= minY + height) continue;
            LevelChunkSection section = chunk.getSection(si);
            section.acquire();
            try {
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        int base = (lz * 16 + lx) * height;
                        int colMax = res.columnMax[lz * 16 + lx];
                        for (int ly = 0; ly < 16; ly++) {
                            int y = sectionY + ly;
                            if (y > colMax) break;
                            int idx = y - minY;
                            if (idx < 0 || idx >= height) continue;
                            short b = res.blocks[base + idx];
                            if (b == 0) continue;
                            section.setBlockState(lx, ly, lz, palette[b], false);
                        }
                    }
                }
            } finally {
                section.release();
            }
        }
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState rs, ChunkAccess chunk) {
        // Surfaces are composed together with the terrain in fillFromNoise.
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState rs, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk) {
        // Caves are carved by the core (water-aware: they never breach lake, river or sea beds).
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        super.applyBiomeDecoration(level, chunk, structureManager);
        if (!(level instanceof WorldGenRegion region)) return;
        WorldContext ctx = context(region.getLevel().getChunkSource().randomState());
        ChunkPos cp = chunk.getPos();
        ChunkComposer.Result res = ctx.composer.compose(cp.x(), cp.z(), false);
        // Overflow points of stepped river reaches get a fluid update once the chunk is placed.
        for (int t = 0; t < res.tickCount; t++) {
            level.scheduleTick(new BlockPos(res.tickX[t], res.tickY[t], res.tickZ[t]), Fluids.WATER, 1);
        }
        Placements p = ctx.decorator.decorate(cp.x(), cp.z(), res);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int cap = settings.terrainCap();
        for (int i = 0; i < p.size; i++) {
            int y = p.y[i];
            if (y > cap || y < level.getMinY() || y > level.getMaxY()) continue;
            pos.set(p.x[i], y, p.z[i]);
            BlockState cur = level.getBlockState(pos);
            int mode = p.mode[i];
            boolean ok = switch (mode) {
                case Placements.MODE_AIR -> cur.isAir() || (cur.canBeReplaced() && cur.getFluidState().isEmpty());
                case Placements.MODE_SOLID -> !cur.isAir() && cur.getFluidState().isEmpty() && !cur.is(Blocks.BEDROCK);
                case Placements.MODE_WATER -> cur.is(Blocks.WATER) && cur.getFluidState().isSource();
                default -> !cur.is(Blocks.BEDROCK);
            };
            if (!ok) continue;
            BlockState st = BlockPalette.state(Placements.state(p.st[i]));
            if (st.isAir()) continue;
            if (mode == Placements.MODE_WATER && st.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)) {
                st = st.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true);
            }
            level.setBlock(pos, st, 2);
        }
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        ChunkPos cp = region.getCenter();
        Holder<Biome> biome = region.getBiome(cp.getWorldPosition().atY(region.getMaxY() - 1));
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        random.setDecorationSeed(region.getSeed(), cp.getMinBlockX(), cp.getMinBlockZ());
        NaturalSpawner.spawnMobsForChunkGeneration(region, biome, cp, random);
    }

    /**
     * Vanilla structures are a persisted per-world option. When disabled no structure starts are created; when
     * enabled, structure biome tags only include biomes whose terrain stays well below the generation cap.
     */
    @Override
    public void createStructures(net.minecraft.core.RegistryAccess registryAccess,
                                 net.minecraft.world.level.chunk.ChunkGeneratorStructureState structureState,
                                 StructureManager structureManager, ChunkAccess chunk,
                                 net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templates,
                                 net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> level) {
        if (!settings.vanillaStructures()) return;
        super.createStructures(registryAccess, structureState, structureManager, chunk, templates, level);
    }

    // ------------------------------------------------------------------------------------------------ queries

    @Override
    public int getGenDepth() {
        return HEIGHT;
    }

    @Override
    public int getSeaLevel() {
        return settings.seaLevel();
    }

    @Override
    public int getMinY() {
        return MIN_Y;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState rs) {
        WorldContext ctx = context(rs);
        TerrainSampler.Column c = new TerrainSampler.Column();
        ctx.sampler().sample(x, z, c);
        int top = (int) Math.floor(c.height);
        boolean countsFluid = type == Heightmap.Types.WORLD_SURFACE_WG || type == Heightmap.Types.WORLD_SURFACE
                || type == Heightmap.Types.MOTION_BLOCKING;
        if (countsFluid && !Double.isNaN(c.waterLevel)) {
            int wt = (int) Math.floor(c.waterLevel) - 1;
            if (wt > top) top = wt;
        }
        return top + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState rs) {
        WorldContext ctx = context(rs);
        short[] col = ctx.composer.column(x, z);
        BlockState[] states = new BlockState[col.length];
        for (int i = 0; i < col.length; i++) states[i] = BlockPalette.base(col[i]);
        return new NoiseColumn(ctx.composer.minY(), states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState rs, BlockPos pos) {
        WorldContext ctx = context(rs);
        TerrainSampler.Column c = new TerrainSampler.Column();
        ctx.sampler().sample(pos.getX(), pos.getZ(), c);
        info.add(String.format(java.util.Locale.ROOT, "PowderPass %s surface %.0f province %s precip %.2f",
                ctx.composer.biomes.surface(c).key, c.height, c.geo.province, c.precip));
    }

    static boolean isWaterTop(ColumnGrid g, int k) {
        return g.waterTop[k] != ColumnGrid.NO_WATER;
    }

    @SuppressWarnings("unused")
    private static boolean water(BlockState s) {
        return s.getFluidState().getType() == Fluids.WATER;
    }
}
