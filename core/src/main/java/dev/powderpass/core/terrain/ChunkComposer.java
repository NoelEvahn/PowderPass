package dev.powderpass.core.terrain;

import dev.powderpass.core.GenSettings;
import dev.powderpass.core.PowderWorld;
import dev.powderpass.core.anomaly.Anomalies;
import dev.powderpass.core.anomaly.Anomaly;
import dev.powderpass.core.biome.BiomeClassifier;
import dev.powderpass.core.biome.PBiome;
import dev.powderpass.core.geo.BaseTerrain;
import dev.powderpass.core.geo.Geology;
import dev.powderpass.core.geo.Province;
import dev.powderpass.core.noise.Simplex;
import dev.powderpass.core.util.Hash;
import dev.powderpass.core.util.MathUtil;

/**
 * Builds the block columns of one chunk: bedrock, deepslate, coherent tilted/faulted strata whose hardness matches
 * the erosion model, sediments, biome soils, water bodies with sealed beds, and noise caves that never breach water.
 * Output is a dense array indexed by (localX, localZ, y - minY); the mod copies it into chunk sections.
 */
public final class ChunkComposer {
    public final PowderWorld world;
    public final TerrainSampler sampler;
    public final BiomeClassifier biomes;
    private final GenSettings s;
    private final int minY, height, sea;
    private final long rockSeed, soilSeed, caveSeed, bedrockSeed, saltSeed, bandSeed;

    public ChunkComposer(PowderWorld world, int minY, int height) {
        this.world = world;
        this.s = world.settings;
        this.sampler = new TerrainSampler(world);
        this.biomes = new BiomeClassifier(world);
        this.minY = minY;
        this.height = height;
        this.sea = s.seaLevel();
        rockSeed = Hash.subSeed(world.seed, "mat.rock");
        soilSeed = Hash.subSeed(world.seed, "mat.soil");
        caveSeed = Hash.subSeed(world.seed, "mat.caves");
        bedrockSeed = Hash.subSeed(world.seed, "mat.bedrock");
        saltSeed = Hash.subSeed(world.seed, "mat.salt");
        bandSeed = Hash.subSeed(world.seed, "mat.bands");
    }

    public int minY() { return minY; }
    public int height() { return height; }

    /** Result of composing one chunk. */
    public static final class Result {
        public ColumnGrid grid;
        public final PBiome[] surfaceBiome = new PBiome[256];
        public short[] blocks;           // index ((lz*16+lx) * height + (y - minY))
        public final int[] columnMax = new int[256]; // highest non-air y per column
    }

    public Result compose(int chunkX, int chunkZ) {
        Result r = new Result();
        r.grid = new ColumnGrid(sampler, chunkX, chunkZ);
        r.blocks = new short[256 * height];
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int k = r.grid.index(lx, lz);
                TerrainSampler.Column c = r.grid.cols[k];
                PBiome b = biomes.surface(c);
                r.surfaceBiome[lz * 16 + lx] = b;
                fillColumn(r, lx, lz, k, c, b);
            }
        }
        carveCaves(r, chunkX, chunkZ);
        return r;
    }

    // ------------------------------------------------------------------------------------------------ columns

    private void fillColumn(Result r, int lx, int lz, int k, TerrainSampler.Column c, PBiome biome) {
        ColumnGrid g = r.grid;
        int top = Math.min(g.top[k], s.terrainCap());
        int wt = g.waterTop[k];
        int x = (int) c.x, z = (int) c.z;
        int base = (lz * 16 + lx) * height;
        double slope = g.slope[k];
        Geology.GeoSample geo = c.geo;
        boolean arid = c.precip < 0.42;
        double tempTop = world.climate.temperatureAt(c.baseTemp, top);
        int bedrockTop = minY + (int) (Hash.unit(bedrockSeed, x, z) * 4.99);
        double deepTrans = 4 * Simplex.noise2(rockSeed + 9, x / 37.0, z / 37.0);

        // Surface profile.
        Surface sf = surfaceFor(biome, c, slope, top, wt, tempTop, x, z, g.raised[k]);
        int sedDepth = (int) Math.min(8, Math.floor(c.sediment * (slope < 0.6 ? 1.0 : 0.3)));
        int maxY = top;
        for (int y = minY; y <= top; y++) {
            B blk;
            int depth = top - y;
            if (y <= bedrockTop) blk = B.BEDROCK;
            else if (depth < sf.topDepth) blk = depth == 0 ? sf.top : sf.under;
            else if (depth < sf.topDepth + sf.fillDepth) blk = sf.fill;
            else if (depth < sf.topDepth + sf.fillDepth + sedDepth) blk = sediment(c, y, x, z, slope);
            else if (y < deepTrans) blk = (y > -8 && Hash.unit(rockSeed, x, y, z) < 0.25) ? B.TUFF : B.DEEPSLATE;
            else blk = rock(c, geo, x, y, z, depth, arid);
            r.blocks[base + (y - minY)] = (short) blk.ordinal();
        }
        if (wt != ColumnGrid.NO_WATER) {
            boolean frozen = world.climate.temperatureAt(c.baseTemp, wt) < -0.55 && g.waterKind[k] != TerrainSampler.WATER_RIVER;
            for (int y = top + 1; y <= wt && y < minY + height; y++) {
                r.blocks[base + (y - minY)] = (short) ((frozen && y == wt) ? B.ICE : B.WATER).ordinal();
            }
            maxY = Math.max(maxY, wt);
        }
        r.columnMax[lz * 16 + lx] = maxY;
    }

    private static final class Surface {
        B top = B.GRASS, under = B.DIRT, fill = B.DIRT;
        int topDepth = 1, fillDepth = 3;
    }

    private Surface surfaceFor(PBiome b, TerrainSampler.Column c, double slope, int top, int wt, double temp, int x, int z, boolean raisedBank) {
        Surface sf = new Surface();
        double patch = Simplex.fbm(soilSeed, x / 11.0, z / 11.0, 2, 2.0, 0.5);
        double patch2 = Simplex.noise2(soilSeed + 3, x / 5.0, z / 5.0);
        boolean underwater = wt != ColumnGrid.NO_WATER;
        boolean steep = slope > 1.25;
        boolean rocky = slope > 0.85;
        int soil = rocky ? 1 : (slope > 0.5 ? 2 : 3 + (int) (1.5 + 1.5 * patch));
        sf.topDepth = 1;
        sf.fillDepth = soil;

        if (underwater) {
            byte kind = (byte) (c.waterKind);
            if (kind == TerrainSampler.WATER_RIVER || c.channel) {
                boolean fast = c.riverGrad > 0.006;
                sf.top = fast ? (patch2 > 0.3 ? B.STONE : B.GRAVEL) : (patch > 0.2 ? B.SAND : (patch < -0.35 ? B.CLAY : B.GRAVEL));
                sf.under = sf.top == B.STONE ? B.STONE : B.GRAVEL;
                sf.fill = B.GRAVEL;
                sf.fillDepth = 2;
            } else if (kind == TerrainSampler.WATER_LAKE) {
                sf.top = patch > 0.25 ? B.SAND : (patch < -0.2 ? B.MUD : B.CLAY);
                sf.under = B.CLAY;
                sf.fill = B.CLAY;
                sf.fillDepth = 2; // sealed bed
            } else {
                double depth = (wt + 1) - top;
                sf.top = depth < 12 ? (patch > -0.4 ? B.SAND : B.GRAVEL) : (patch > 0.3 ? B.CLAY : B.GRAVEL);
                sf.under = depth < 12 ? B.SAND : B.GRAVEL;
                sf.fill = B.SANDSTONE;
                sf.fillDepth = 3;
            }
            return sf;
        }
        if (raisedBank) {
            sf.top = c.channel || c.riverDist < 6 ? B.GRAVEL : B.DIRT;
            if (temp > -0.4 && !c.channel && c.riverDist > 3) sf.top = B.GRASS;
        }
        if (steep) {
            // Bare rock exposures (cliff bands); scree/gravel on the less steep parts.
            sf.topDepth = 0;
            sf.fillDepth = 0;
            return sf;
        }
        switch (b) {
            case SANDY_DESERT -> { sf.top = B.SAND; sf.under = B.SAND; sf.fill = B.SANDSTONE; sf.topDepth = 4; sf.fillDepth = 5; }
            case RED_BADLANDS -> {
                if (slope < 0.35) { sf.top = B.RED_SAND; sf.under = B.RED_SAND; sf.fill = B.ORANGE_TC; sf.topDepth = 2; sf.fillDepth = 2; }
                else { sf.topDepth = 0; sf.fillDepth = 0; }
            }
            case BEACH_DUNES -> { sf.top = B.SAND; sf.under = B.SAND; sf.fill = B.SANDSTONE; sf.topDepth = 4; sf.fillDepth = 3; }
            case STONY_SHORE -> { sf.top = patch > 0 ? B.GRAVEL : B.STONE; sf.under = B.GRAVEL; sf.fill = B.STONE; sf.fillDepth = 1; }
            case SALT_FLATS -> {
                // Polygonal salt crust: pale cell interiors, whiter ridges along cell edges.
                boolean cold = temp < -0.45;
                sf.top = cold ? B.SNOW_BLOCK : (saltEdge(x, z) ? B.WHITE_TC : B.CALCITE);
                sf.under = B.SAND;
                sf.fill = B.CLAY;
                sf.topDepth = 1;
                sf.fillDepth = 4;
            }
            case BARREN_FLATS -> { sf.top = patch > 0.25 ? B.COARSE_DIRT : (patch < -0.3 ? B.LIGHT_GRAY_TC : B.PACKED_MUD); sf.under = B.COARSE_DIRT; sf.fill = B.TERRACOTTA; }
            case ASH_BARRENS -> {
                sf.top = patch > 0.35 ? B.TUFF : (patch < -0.3 ? B.SMOOTH_BASALT : (patch2 > 0.4 ? B.BLACKSTONE : B.GRAVEL));
                sf.under = B.TUFF; sf.fill = B.BASALT; sf.fillDepth = 2;
            }
            case BASALT_HIGHLANDS -> { sf.top = rocky ? B.SMOOTH_BASALT : (patch > 0.45 ? B.COARSE_DIRT : B.GRASS); sf.under = B.DIRT; sf.fill = B.TUFF; sf.fillDepth = Math.min(soil, 2); }
            case CRATER_BASIN -> { sf.top = patch > 0.3 ? B.GRAVEL : (patch < -0.3 ? B.TUFF : B.COARSE_DIRT); sf.under = B.GRAVEL; sf.fill = B.TUFF; }
            case SNOWY_SUMMIT -> { sf.top = rocky ? B.STONE : B.SNOW_BLOCK; sf.under = rocky ? B.STONE : B.SNOW_BLOCK; sf.fill = B.PACKED_ICE; sf.topDepth = rocky ? 0 : 2; sf.fillDepth = rocky ? 0 : 1; }
            case ROCKY_ALPINE_RIDGE -> { sf.top = patch > 0.2 ? B.GRAVEL : B.STONE; sf.under = B.GRAVEL; sf.fill = B.STONE; sf.topDepth = rocky ? 0 : 1; sf.fillDepth = 1; }
            case ALPINE_MEADOW -> { sf.top = patch2 > 0.55 ? B.STONE : B.GRASS; sf.fillDepth = Math.min(soil, 2); }
            case FROZEN_TUNDRA -> { sf.top = patch > 0.3 ? B.COARSE_DIRT : B.GRASS; sf.fillDepth = Math.min(soil, 2); }
            case GLACIAL_VALLEY -> { sf.top = patch > 0.4 ? B.GRAVEL : (patch < -0.4 ? B.COARSE_DIRT : B.GRASS); }
            case MARSH -> { sf.top = patch > 0.15 ? B.GRASS : B.MUD; sf.under = B.MUD; sf.fill = B.CLAY; }
            case MANGROVE_WETLAND -> { sf.top = B.MUD; sf.under = B.MUD; sf.fill = B.CLAY; }
            case WET_COASTAL_FOREST, TROPICAL_RAINFOREST -> { sf.top = patch > 0.35 ? B.MOSS : (patch < -0.35 ? B.PODZOL : B.GRASS); sf.under = patch2 > 0.5 ? B.ROOTED_DIRT : B.DIRT; }
            case CONIFER_FOOTHILLS, BOREAL_FOREST -> { sf.top = patch > 0.25 ? B.PODZOL : (patch < -0.45 ? B.COARSE_DIRT : B.GRASS); }
            case TEMPERATE_MIXED_FOREST, FLOODPLAIN_WOODLAND -> { sf.top = patch > 0.5 ? B.PODZOL : (patch < -0.55 ? B.COARSE_DIRT : B.GRASS); sf.under = patch2 > 0.6 ? B.ROOTED_DIRT : B.DIRT; }
            case DRY_SCRUBLAND -> { sf.top = patch > 0.2 ? B.COARSE_DIRT : (patch < -0.4 ? (c.baseTemp > 0.6 ? B.RED_SAND : B.SAND) : B.GRASS); }
            case STEPPE, WARM_SAVANNA -> { sf.top = patch > 0.45 ? B.COARSE_DIRT : B.GRASS; }
            case KARST_COUNTRY -> { sf.top = (rocky || patch2 > 0.5) ? B.CALCITE : B.GRASS; sf.fillDepth = Math.min(soil, 2); }
            case WINDSWEPT_PLATEAU -> { sf.top = patch > 0.35 ? B.COARSE_DIRT : (patch2 > 0.6 ? B.GRAVEL : B.GRASS); sf.fillDepth = Math.min(soil, 2); }
            default -> { }
        }
        if (rocky && sf.top == B.GRASS) { sf.top = patch2 > 0 ? B.COARSE_DIRT : B.GRASS; sf.fillDepth = 1; }
        // Snowline: snow-covered tops above the local freezing level.
        if (temp < -0.55 && !rocky && sf.top.isSoil()) sf.top = B.SNOW_BLOCK;
        return sf;
    }

    private boolean saltEdge(int x, int z) {
        double cell = 9;
        int cx = MathUtil.fastFloor(x / cell), cz = MathUtil.fastFloor(z / cell);
        double b1 = 1e9, b2 = 1e9;
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            double px = (cx + dx + Hash.unit(saltSeed, cx + dx, cz + dz, 1)) * cell;
            double pz = (cz + dz + Hash.unit(saltSeed, cx + dx, cz + dz, 2)) * cell;
            double d = Math.hypot(px - x - 0.5, pz - z - 0.5);
            if (d < b1) { b2 = b1; b1 = d; } else if (d < b2) b2 = d;
        }
        return b2 - b1 < 1.1;
    }

    private B sediment(TerrainSampler.Column c, int y, int x, int z, double slope) {
        if (slope > 0.7) return Hash.unit(soilSeed, x, y, z) < 0.6 ? B.GRAVEL : B.ANDESITE; // talus / scree
        if (c.ice > 2000) return B.GRAVEL; // till / moraine
        double n = Simplex.noise3(soilSeed + 5, x / 9.0, y / 3.0, z / 9.0);
        if (c.riverDist < c.riverWidth * 2 + 8) return n > 0.1 ? B.GRAVEL : B.SAND; // channel deposits
        return n > 0.45 ? B.CLAY : (n < -0.4 ? B.GRAVEL : B.DIRT);                  // overbank alluvium
    }

    /** Bedrock strata. Hard layers (the ones that erode slowly in the simulation) use hard-looking rock. */
    private B rock(TerrainSampler.Column c, Geology.GeoSample g, int x, int y, int z, int depth, boolean arid) {
        Province p = g.province;
        int ov = g.overlay;
        if (ov >= BaseTerrain.OVERLAY_VOLCANO_BASE && ov < BaseTerrain.OVERLAY_VOLCANO_BASE + 10 && depth < 90) {
            double v = Hash.unit(rockSeed, x >> 1, y, z >> 1);
            return v < 0.5 ? B.BASALT : (v < 0.75 ? B.TUFF : (v < 0.9 ? B.BLACKSTONE : B.SMOOTH_BASALT));
        }
        if (ov >= Anomalies.OVERLAY_BASE && depth < 60) {
            Anomaly.Type at = Anomaly.Type.values()[ov - Anomalies.OVERLAY_BASE];
            if (at == Anomaly.Type.MONOLITH) return arid ? B.RED_SANDSTONE : (Hash.unit(rockSeed, x, y >> 2, z) < 0.8 ? B.GRANITE : B.DIORITE);
            if (at == Anomaly.Type.IMPACT_CRATER && g.overlayStrength > 0.8) {
                double v = Hash.unit(rockSeed, x >> 1, y >> 1, z >> 1);
                return v < 0.45 ? B.ANDESITE : (v < 0.7 ? B.TUFF : (v < 0.85 ? B.STONE : B.GRAVEL));
            }
        }
        // Deep crust is ore-bearing vanilla stone; strata are a near-surface (and cliff) phenomenon.
        if (depth > 64) return B.STONE;
        Geology geo = world.geology;
        double sc = geo.strataCoord(y, g, x, z);
        double hardness = geo.layerResistance(sc, p);
        boolean hard = hardness > 1.25;
        double thick = p == Province.PLATEAU ? 22 : (p == Province.SEDIMENTARY_BASIN || p == Province.KARST ? 14 : 30);
        int layer = MathUtil.fastFloor(sc / thick);
        double r = Hash.unit(Hash.hash(rockSeed, layer, p.ordinal()));
        switch (p) {
            case YOUNG_OROGEN:
                return hard ? (r < 0.5 ? B.STONE : (r < 0.85 ? B.ANDESITE : B.GRANITE)) : (r < 0.55 ? B.STONE : (r < 0.85 ? B.TUFF : B.ANDESITE));
            case OLD_OROGEN:
                return hard ? (r < 0.4 ? B.GRANITE : (r < 0.75 ? B.STONE : (r < 0.9 ? B.DIORITE : B.DEEPSLATE))) : (r < 0.6 ? B.STONE : (r < 0.8 ? B.ANDESITE : B.TUFF));
            case SHIELD:
                return hard ? (r < 0.45 ? B.GRANITE : (r < 0.75 ? B.DIORITE : (r < 0.9 ? B.STONE : B.DEEPSLATE))) : (r < 0.7 ? B.STONE : B.DIORITE);
            case VOLCANIC:
                return hard ? (r < 0.6 ? B.BASALT : (r < 0.8 ? B.SMOOTH_BASALT : B.BLACKSTONE)) : (r < 0.75 ? B.TUFF : B.ANDESITE);
            case RIFT:
                return hard ? (r < 0.5 ? B.BASALT : B.STONE) : (r < 0.65 ? B.TUFF : B.STONE);
            case KARST:
                return hard ? (r < 0.75 ? B.CALCITE : B.STONE) : (r < 0.55 ? B.STONE : (r < 0.8 ? B.DIORITE : B.DRIPSTONE));
            case PLATEAU:
            case SEDIMENTARY_BASIN: {
                if (arid) {
                    if (hard) return r < 0.6 ? B.RED_SANDSTONE : B.SANDSTONE;
                    // Thin terracotta bands within soft layers.
                    int band = MathUtil.fastFloor(sc / 3.0);
                    double bv = Hash.unit(Hash.hash(bandSeed, band));
                    return bv < 0.22 ? B.ORANGE_TC : bv < 0.38 ? B.TERRACOTTA : bv < 0.52 ? B.YELLOW_TC : bv < 0.64 ? B.WHITE_TC
                            : bv < 0.78 ? B.RED_TC : bv < 0.9 ? B.BROWN_TC : B.LIGHT_GRAY_TC;
                }
                return hard ? (r < 0.5 ? B.SANDSTONE : (r < 0.8 ? B.STONE : B.CALCITE)) : (r < 0.6 ? B.STONE : B.ANDESITE);
            }
            case COASTAL_LOWLAND:
                return hard ? (r < 0.5 ? B.SANDSTONE : B.STONE) : (r < 0.7 ? B.STONE : B.ANDESITE);
            default:
                return r < 0.7 ? B.STONE : (r < 0.85 ? B.ANDESITE : B.TUFF);
        }
    }

    // ------------------------------------------------------------------------------------------------ caves

    /**
     * Cheese and spaghetti caves from 3D noise sampled on a 4x8x4 lattice and trilinearly interpolated. Caves never
     * open within 7 blocks under any water column (or its neighbours), keeping lake/river/sea beds sealed; below
     * Y=-54 cave air becomes lava like vanilla's deep lava level. No aquifers: caves are dry.
     */
    private void carveCaves(Result r, int chunkX, int chunkZ) {
        int nx = 5, nz = 5;
        int maxTop = Integer.MIN_VALUE;
        for (int i = 0; i < 256; i++) maxTop = Math.max(maxTop, r.columnMax[i]);
        int yTopLimit = Math.min(maxTop, minY + height - 1);
        int ny = (yTopLimit - minY) / 8 + 2;
        double[] cheese = new double[nx * ny * nz];
        double[] spag = new double[nx * ny * nz];
        int bx = chunkX * 16, bz = chunkZ * 16;
        for (int j = 0; j < ny; j++) {
            for (int kz = 0; kz < nz; kz++) {
                for (int kx = 0; kx < nx; kx++) {
                    double x = bx + kx * 4, y = minY + j * 8, z = bz + kz * 4;
                    int idx = (j * nz + kz) * nx + kx;
                    double c1 = Simplex.noise3(caveSeed, x / 72.0, y / 40.0, z / 72.0)
                            + 0.35 * Simplex.noise3(caveSeed + 1, x / 24.0, y / 16.0, z / 24.0);
                    cheese[idx] = c1;
                    double s1 = Simplex.noise3(caveSeed + 2, x / 48.0, y / 30.0, z / 48.0);
                    double s2 = Simplex.noise3(caveSeed + 3, x / 48.0, y / 30.0, z / 48.0);
                    spag[idx] = Math.max(Math.abs(s1), Math.abs(s2));
                }
            }
        }
        short air = (short) B.AIR.ordinal(), lava = (short) B.LAVA.ordinal(), water = (short) B.WATER.ordinal();
        short bedrock = (short) B.BEDROCK.ordinal(), ice = (short) B.ICE.ordinal();
        ColumnGrid g = r.grid;
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int k = g.index(lx, lz);
                int top = g.top[k];
                boolean nearWater = false;
                for (int dz = -1; dz <= 1 && !nearWater; dz++)
                    for (int dx = -1; dx <= 1 && !nearWater; dx++)
                        if (g.waterTop[k + dz * ColumnGrid.SIZE + dx] != ColumnGrid.NO_WATER) nearWater = true;
                int ceiling = nearWater ? top - 9 : top - 4;
                // Surface openings only occasionally (where cheese noise is strongest), never near water.
                int base = (lz * 16 + lx) * height;
                for (int y = minY + 5; y <= Math.min(ceiling + 3, yTopLimit); y++) {
                    double fy = (y - minY) / 8.0;
                    int j0 = (int) fy;
                    if (j0 + 1 >= ny) break;
                    double ty = fy - j0;
                    double tx = (lx % 4) / 4.0, tz = (lz % 4) / 4.0;
                    int kx = lx / 4, kz = lz / 4;
                    double ch = tri(cheese, nx, nz, kx, j0, kz, tx, ty, tz);
                    double sp = tri(spag, nx, nz, kx, j0, kz, tx, ty, tz);
                    // Fewer caves high in mountains; cheese caves favour mid depths.
                    double depthFromTop = top - y;
                    double cheeseT = 0.62 + (y > 40 ? 0.12 : 0) + (depthFromTop < 12 ? 0.2 : 0);
                    boolean carve = ch > cheeseT || sp < 0.055 - (depthFromTop < 10 ? 0.03 : 0);
                    if (!carve) continue;
                    if (y > ceiling && !(ch > 0.95 && !nearWater)) continue;
                    int idx = base + (y - minY);
                    short cur = r.blocks[idx];
                    if (cur == air || cur == water || cur == bedrock || cur == ice) continue;
                    r.blocks[idx] = y < -54 ? lava : air;
                }
            }
        }
    }

    private static double tri(double[] a, int nx, int nz, int kx, int j, int kz, double tx, double ty, double tz) {
        int i000 = (j * nz + kz) * nx + kx;
        int i100 = i000 + 1, i010 = i000 + nx, i110 = i010 + 1;
        int up = nx * nz;
        double c00 = MathUtil.lerp(tx, a[i000], a[i100]), c10 = MathUtil.lerp(tx, a[i010], a[i110]);
        double c01 = MathUtil.lerp(tx, a[i000 + up], a[i100 + up]), c11 = MathUtil.lerp(tx, a[i010 + up], a[i110 + up]);
        return MathUtil.lerp(ty, MathUtil.lerp(tz, c00, c10), MathUtil.lerp(tz, c01, c11));
    }

    public short blockAt(Result r, int lx, int lz, int y) {
        if (y < minY || y >= minY + height) return (short) B.AIR.ordinal();
        return r.blocks[(lz * 16 + lx) * height + (y - minY)];
    }
}
