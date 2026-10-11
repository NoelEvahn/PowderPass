package dev.powderpass.core.deco;

import dev.powderpass.core.GenSettings;
import dev.powderpass.core.PowderWorld;
import dev.powderpass.core.biome.PBiome;
import dev.powderpass.core.geo.Province;
import dev.powderpass.core.noise.Simplex;
import dev.powderpass.core.terrain.B;
import dev.powderpass.core.terrain.ChunkComposer;
import dev.powderpass.core.terrain.ColumnGrid;
import dev.powderpass.core.terrain.TerrainSampler;
import dev.powderpass.core.util.Hash;
import dev.powderpass.core.util.MathUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * Natural surface decoration with deterministic, order-independent ownership.
 *
 * <p>Large objects (trees, bushes, boulders, fallen logs) come from a 5-block jittered candidate lattice. A
 * candidate is kept iff no other candidate with higher priority lies within the sum of their radii (+1). This
 * "local maximum" rule depends only on terrain functions, never on what another chunk already placed, so objects
 * never overlap and results are identical regardless of chunk generation order. Each object is emitted by the chunk
 * that contains its anchor; its footprint stays within one chunk of the anchor (the vanilla decoration window).
 * Small ground cover is owned per column and skips columns covered by any kept object's footprint.
 */
public final class Decorator {
    public static final int CELL = 5;

    private final PowderWorld world;
    private final ChunkComposer composer;
    private final GenSettings s;
    private final int cap;
    private final long candSeed, patchSeed, flowerSeed, slabSeed;

    public Decorator(ChunkComposer composer) {
        this.composer = composer;
        this.world = composer.world;
        this.s = world.settings;
        this.cap = s.terrainCap();
        candSeed = Hash.subSeed(world.seed, "deco.cand");
        patchSeed = Hash.subSeed(world.seed, "deco.patch");
        flowerSeed = Hash.subSeed(world.seed, "deco.flower");
        slabSeed = Hash.subSeed(world.seed, "deco.slab");
    }

    private enum Kind { NONE, TREE, BUSH, BOULDER, LOG }

    private static final class Cand {
        int x, z, top;
        Kind kind = Kind.NONE;
        Trees.Species species;
        Trees.BushKind bush;
        int radius;
        double priority;
        long seed;
        String rock;
        int logAxis, logLen;
    }

    /** Decoration for chunk (cx, cz). {@code res} is the composed chunk (ground cover reads its columns). */
    public Placements decorate(int cx, int cz, ChunkComposer.Result res) {
        Placements out = new Placements();
        Map<Long, Cand> cache = new HashMap<>();
        TerrainSampler.Column col = new TerrainSampler.Column();
        int x0 = cx * 16, z0 = cz * 16;
        int gx0 = Math.floorDiv(x0, CELL), gz0 = Math.floorDiv(z0, CELL);
        int gx1 = Math.floorDiv(x0 + 15, CELL), gz1 = Math.floorDiv(z0 + 15, CELL);
        boolean[] covered = new boolean[256];
        double vd = s.vegetationDensity();
        for (int gz = gz0; gz <= gz1; gz++) {
            for (int gx = gx0; gx <= gx1; gx++) {
                Cand c = cand(gx, gz, cache, col);
                if (c.kind == Kind.NONE) continue;
                if (c.x < x0 || c.x >= x0 + 16 || c.z < z0 || c.z >= z0 + 16) continue; // not owned here
                if (!kept(c, gx, gz, cache, col)) continue;
                emit(c, out, cache, col);
            }
        }
        // Footprints of kept objects owned by this chunk or its neighbours (for ground-cover exclusion).
        for (int gz = gz0 - 3; gz <= gz1 + 3; gz++) {
            for (int gx = gx0 - 3; gx <= gx1 + 3; gx++) {
                Cand c = cand(gx, gz, cache, col);
                if (c.kind == Kind.NONE || !kept(c, gx, gz, cache, col)) continue;
                int fr = c.kind == Kind.TREE ? 1 : c.radius;
                if (c.kind == Kind.LOG) fr = 0;
                for (int dz = -fr; dz <= fr; dz++) for (int dx = -fr; dx <= fr; dx++) {
                    int lx = c.x + dx - x0, lz = c.z + dz - z0;
                    if (c.kind == Kind.LOG) {
                        for (int i = 0; i < c.logLen; i++) {
                            int px = c.x + (c.logAxis == 0 ? i : 0) - x0, pz = c.z + (c.logAxis == 1 ? i : 0) - z0;
                            if (px >= 0 && pz >= 0 && px < 16 && pz < 16) covered[pz * 16 + px] = true;
                        }
                        break;
                    }
                    if (lx >= 0 && lz >= 0 && lx < 16 && lz < 16) covered[lz * 16 + lx] = true;
                }
            }
        }
        groundCover(cx, cz, res, covered, out, vd);
        return out;
    }

    // ------------------------------------------------------------------------------------------------ candidates

    private Cand cand(int gx, int gz, Map<Long, Cand> cache, TerrainSampler.Column col) {
        long key = Hash.pack(gx, gz);
        Cand c = cache.get(key);
        if (c != null) return c;
        c = new Cand();
        cache.put(key, c);
        long h = Hash.hash(candSeed, gx, gz);
        c.seed = h;
        c.x = gx * CELL + Hash.range(Hash.mix(h + 1), CELL);
        c.z = gz * CELL + Hash.range(Hash.mix(h + 2), CELL);
        c.priority = Hash.unit(Hash.mix(h + 3));
        composer.sampler.sample(c.x + 0.0, c.z + 0.0, col);
        if (!Double.isNaN(col.waterLevel) || col.channel || col.riverDist < col.riverWidth * 0.5 + 2) return c;
        c.top = (int) Math.floor(col.height);
        PBiome b = composer.biomes.surface(col);
        double slope = col.gridSlope;
        double elev = c.top - s.seaLevel();
        double u = Hash.unit(Hash.mix(h + 4));
        double v = Hash.unit(Hash.mix(h + 5));
        double vd = s.vegetationDensity();
        // Forest patches and glades: canopy gaps from a smooth field.
        double patch = Simplex.fbm(patchSeed, c.x / 95.0, c.z / 95.0, 3, 2.0, 0.5);
        double treeline = 1 - MathUtil.smoothstep(300, 395, elev);
        double slopeOk = 1 - MathUtil.smoothstep(0.65, 0.95, slope);
        double treeP = 0, bushP = 0, boulderP = 0, logP = 0;
        Trees.Species sp = Trees.Species.OAK;
        Trees.BushKind bk = Trees.BushKind.OAK;
        String rock = "minecraft:stone";
        switch (b) {
            case TEMPERATE_MIXED_FOREST -> {
                treeP = 0.55 * MathUtil.smoothstep(-0.45, 0.15, patch); bushP = 0.15; logP = 0.03;
                sp = v < 0.45 ? Trees.Species.OAK : v < 0.72 ? Trees.Species.BIRCH : v < 0.85 ? Trees.Species.BIG_OAK : Trees.Species.SPRUCE;
                bk = v < 0.5 ? Trees.BushKind.OAK : Trees.BushKind.AZALEA;
            }
            case FLOODPLAIN_WOODLAND -> {
                treeP = 0.45 * MathUtil.smoothstep(-0.5, 0.1, patch); bushP = 0.18; logP = 0.04;
                sp = v < 0.5 ? Trees.Species.WILLOW : v < 0.8 ? Trees.Species.OAK : Trees.Species.BIRCH;
                bk = Trees.BushKind.OAK;
            }
            case BIRCH_UPLANDS -> {
                treeP = 0.45 * MathUtil.smoothstep(-0.4, 0.2, patch); bushP = 0.12; logP = 0.02;
                sp = v < 0.72 ? Trees.Species.BIRCH : Trees.Species.OAK;
                bk = Trees.BushKind.BIRCH;
            }
            case CONIFER_FOOTHILLS -> {
                treeP = 0.55 * MathUtil.smoothstep(-0.5, 0.1, patch); bushP = 0.08; logP = 0.03; boulderP = 0.03;
                sp = v < 0.65 ? Trees.Species.SPRUCE : v < 0.92 ? Trees.Species.PINE : Trees.Species.DEAD_SPRUCE;
                bk = Trees.BushKind.SPRUCE;
            }
            case BOREAL_FOREST -> {
                treeP = 0.5 * MathUtil.smoothstep(-0.5, 0.0, patch); bushP = 0.1; logP = 0.03;
                sp = v < 0.6 ? Trees.Species.SPRUCE : v < 0.8 ? Trees.Species.BIRCH : Trees.Species.PINE;
                bk = Trees.BushKind.SPRUCE;
            }
            case WET_COASTAL_FOREST -> {
                treeP = 0.6; bushP = 0.2; logP = 0.05;
                sp = v < 0.35 ? Trees.Species.MEGA_SPRUCE : v < 0.7 ? Trees.Species.SPRUCE : Trees.Species.OAK;
                bk = v < 0.5 ? Trees.BushKind.AZALEA : Trees.BushKind.SPRUCE;
            }
            case TROPICAL_RAINFOREST -> {
                treeP = 0.65; bushP = 0.3; logP = 0.03;
                sp = v < 0.25 ? Trees.Species.MEGA_JUNGLE : Trees.Species.JUNGLE;
                bk = Trees.BushKind.JUNGLE;
            }
            case WARM_SAVANNA -> { treeP = 0.08; bushP = 0.08; sp = v < 0.8 ? Trees.Species.ACACIA : Trees.Species.OAK; bk = Trees.BushKind.ACACIA; }
            case DRY_SCRUBLAND -> { treeP = 0.03; bushP = 0.14; boulderP = 0.03; sp = c.x % 2 == 0 ? Trees.Species.ACACIA : Trees.Species.OAK; bk = v < 0.5 ? Trees.BushKind.ACACIA : Trees.BushKind.OAK; rock = "minecraft:andesite"; }
            case STEPPE -> { treeP = 0.006; bushP = 0.05; boulderP = 0.015; bk = Trees.BushKind.OAK; }
            case ROLLING_GRASSLAND -> {
                double grove = MathUtil.smoothstep(0.35, 0.6, patch);
                treeP = 0.02 + 0.35 * grove; bushP = 0.04 + 0.1 * grove; sp = v < 0.7 ? Trees.Species.OAK : Trees.Species.BIRCH; bk = Trees.BushKind.OAK;
            }
            case ALLUVIAL_MEADOW -> { treeP = 0.02 + 0.2 * MathUtil.smoothstep(0.4, 0.65, patch); bushP = 0.07; sp = Trees.Species.WILLOW; bk = Trees.BushKind.OAK; }
            case MARSH -> { treeP = 0.05; bushP = 0.05; sp = Trees.Species.WILLOW; }
            case MANGROVE_WETLAND -> { treeP = 0.3; sp = Trees.Species.MANGROVE; }
            case KARST_COUNTRY -> { treeP = 0.15; bushP = 0.12; boulderP = 0.05; sp = v < 0.6 ? Trees.Species.OAK : Trees.Species.BIRCH; bk = Trees.BushKind.AZALEA; rock = "minecraft:calcite"; }
            case BASALT_HIGHLANDS -> { treeP = 0.08; bushP = 0.06; boulderP = 0.06; sp = Trees.Species.SPRUCE; bk = Trees.BushKind.SPRUCE; rock = "minecraft:basalt"; }
            case GLACIAL_VALLEY -> { treeP = 0.12; bushP = 0.04; boulderP = 0.06; sp = Trees.Species.SPRUCE; bk = Trees.BushKind.SPRUCE; rock = v < 0.5 ? "minecraft:granite" : "minecraft:diorite"; }
            case ALPINE_MEADOW -> { treeP = 0.05; bushP = 0.03; boulderP = 0.04; sp = Trees.Species.KRUMMHOLZ; bk = Trees.BushKind.SPRUCE; }
            case ROCKY_ALPINE_RIDGE -> { boulderP = 0.05; treeP = elev < 380 ? 0.02 : 0; sp = Trees.Species.KRUMMHOLZ; }
            case WINDSWEPT_PLATEAU -> { treeP = 0.03; bushP = 0.04; boulderP = 0.04; sp = Trees.Species.KRUMMHOLZ; }
            case ASH_BARRENS -> { treeP = 0.015; boulderP = 0.05; sp = Trees.Species.SNAG; rock = "minecraft:blackstone"; }
            case CRATER_BASIN -> { boulderP = 0.07; rock = "minecraft:tuff"; }
            case FROZEN_TUNDRA -> { bushP = 0.02; boulderP = 0.03; bk = Trees.BushKind.SPRUCE; }
            case RED_BADLANDS, SANDY_DESERT -> { boulderP = 0.01; rock = "minecraft:red_sandstone"; }
            default -> { }
        }
        if (col.geo.province == Province.VOLCANIC) rock = "minecraft:basalt";
        else if (col.geo.province == Province.SHIELD) rock = "minecraft:granite";
        treeP *= vd * treeline * slopeOk;
        bushP *= vd * slopeOk;
        logP *= vd * slopeOk;
        boulderP *= 1 - MathUtil.smoothstep(0.55, 0.8, slope);
        if (sp == Trees.Species.KRUMMHOLZ) treeP = treeP / Math.max(0.2, treeline) * (elev > 250 ? 1 : 0.3);
        double acc = 0;
        if (u < (acc += treeP)) {
            c.kind = Kind.TREE;
            c.species = sp;
            c.radius = Trees.radius(sp);
            if (c.top + Trees.height(sp, h) + 4 > cap - 1) c.kind = Kind.NONE;
        } else if (u < (acc += bushP)) {
            c.kind = Kind.BUSH;
            c.bush = bk;
            c.radius = 2;
        } else if (u < (acc += boulderP)) {
            c.kind = Kind.BOULDER;
            c.rock = rock;
            c.radius = 1 + (int) (Hash.unit(Hash.mix(h + 6)) * 2.5);
        } else if (u < (acc += logP)) {
            c.kind = Kind.LOG;
            c.species = sp;
            c.logAxis = Hash.unit(Hash.mix(h + 7)) < 0.5 ? 0 : 1;
            c.logLen = 3 + (int) (Hash.unit(Hash.mix(h + 8)) * 4);
            c.radius = (c.logLen + 1) / 2 + 1;
        }
        if (c.top + 12 > cap) c.kind = Kind.NONE;
        return c;
    }

    private boolean kept(Cand c, int gx, int gz, Map<Long, Cand> cache, TerrainSampler.Column col) {
        int reach = 3; // cells: max radius 6 + 6 + 1 = 13 -> 3 cells of 5
        for (int dz = -reach; dz <= reach; dz++) {
            for (int dx = -reach; dx <= reach; dx++) {
                if (dx == 0 && dz == 0) continue;
                Cand o = cand(gx + dx, gz + dz, cache, col);
                if (o.kind == Kind.NONE) continue;
                double d = Math.hypot(o.x - c.x, o.z - c.z);
                if (d <= c.radius + o.radius + 1 && (o.priority > c.priority || (o.priority == c.priority && o.seed > c.seed))) return false;
            }
        }
        return true;
    }

    private void emit(Cand c, Placements out, Map<Long, Cand> cache, TerrainSampler.Column col) {
        switch (c.kind) {
            case TREE -> {
                double wa = world.climate.windAngle(c.x, c.z);
                Trees.Shape shape = Trees.build(c.species, c.seed, Math.cos(wa), Math.sin(wa));
                if (Trees.twoByTwo(c.species)) {
                    // Extend the 2x2 trunk down to uneven ground under the other three columns.
                    for (int[] off : new int[][]{{1, 0}, {0, 1}, {1, 1}}) {
                        composer.sampler.sample(c.x + off[0], c.z + off[1], col);
                        int t = (int) Math.floor(col.height);
                        for (int y = t + 1; y <= c.top; y++) {
                            out.add(c.x + off[0], y, c.z + off[1], "minecraft:" + c.species.wood + "_log[axis=y]", Placements.MODE_AIR);
                        }
                    }
                }
                Trees.emit(shape, c.species, null, c.x, c.top, c.z, cap, out);
                // Ground under the trunk becomes dirt; big trees get rooted dirt around their base.
                out.add(c.x, c.top, c.z, "minecraft:dirt", Placements.MODE_SOLID);
                if (Trees.twoByTwo(c.species) || c.species == Trees.Species.WILLOW) {
                    for (int[] off : new int[][]{{-1, 0}, {2, 0}, {0, -1}, {0, 2}}) {
                        if (Hash.unit(Hash.mix(c.seed + off[0] * 7 + off[1])) < 0.6) {
                            out.add(c.x + off[0], c.top, c.z + off[1], "minecraft:rooted_dirt", Placements.MODE_SOLID);
                        }
                    }
                }
            }
            case BUSH -> {
                Trees.Shape shape = Trees.bush(c.bush, c.seed);
                Trees.emit(shape, null, c.bush, c.x, c.top, c.z, cap, out);
            }
            case BOULDER -> {
                double r = c.radius + 0.4;
                int R = c.radius + 1;
                double ry = r * (0.6 + 0.3 * Hash.unit(Hash.mix(c.seed + 9)));
                String slab = slabFor(c.rock);
                for (int dz = -R; dz <= R; dz++) {
                    for (int dx = -R; dx <= R; dx++) {
                        composer.sampler.sample(c.x + dx, c.z + dz, col);
                        int t = (int) Math.floor(col.height);
                        double e = (dx * dx + dz * dz) / (r * r);
                        if (e > 1.0) continue;
                        int hTop = c.top + (int) Math.round(ry * Math.sqrt(1 - e));
                        if (hTop <= t) continue;
                        for (int y = t + 1; y <= hTop && y <= cap; y++) out.add(c.x + dx, y, c.z + dz, c.rock, Placements.MODE_AIR);
                        if (slab != null && e > 0.45 && Hash.unit(Hash.hash(slabSeed, c.x + dx, c.z + dz)) < 0.5 * s.slabDetail()) {
                            out.add(c.x + dx, hTop + 1, c.z + dz, slab + "[type=bottom,waterlogged=false]", Placements.MODE_AIR);
                        }
                    }
                }
            }
            case LOG -> {
                String st = "minecraft:" + c.species.wood + "_log[axis=" + (c.logAxis == 0 ? "x" : "z") + "]";
                for (int i = 0; i < c.logLen; i++) {
                    int px = c.x + (c.logAxis == 0 ? i : 0), pz = c.z + (c.logAxis == 1 ? i : 0);
                    composer.sampler.sample(px, pz, col);
                    if ((int) Math.floor(col.height) != c.top || !Double.isNaN(col.waterLevel)) return;
                }
                for (int i = 0; i < c.logLen; i++) {
                    int px = c.x + (c.logAxis == 0 ? i : 0), pz = c.z + (c.logAxis == 1 ? i : 0);
                    out.add(px, c.top + 1, pz, st, Placements.MODE_AIR);
                    if (Hash.unit(Hash.mix(c.seed + 50 + i)) < 0.35) out.add(px, c.top + 2, pz, "minecraft:moss_carpet", Placements.MODE_AIR);
                }
            }
            default -> { }
        }
    }

    static String slabFor(String rock) {
        return switch (rock) {
            case "minecraft:stone" -> "minecraft:stone_slab";
            case "minecraft:andesite" -> "minecraft:andesite_slab";
            case "minecraft:granite" -> "minecraft:granite_slab";
            case "minecraft:diorite" -> "minecraft:diorite_slab";
            case "minecraft:tuff" -> "minecraft:tuff_slab";
            case "minecraft:sandstone" -> "minecraft:sandstone_slab";
            case "minecraft:red_sandstone" -> "minecraft:red_sandstone_slab";
            case "minecraft:blackstone" -> "minecraft:blackstone_slab";
            default -> null;
        };
    }

    // ------------------------------------------------------------------------------------------------ ground cover

    private void groundCover(int cx, int cz, ChunkComposer.Result res, boolean[] covered, Placements out, double vd) {
        ColumnGrid g = res.grid;
        int x0 = cx * 16, z0 = cz * 16;
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int k = g.index(lx, lz);
                int x = x0 + lx, z = z0 + lz;
                TerrainSampler.Column c = g.cols[k];
                int top = g.top[k];
                if (top + 2 > cap) continue;
                PBiome b = res.surfaceBiome[lz * 16 + lx];
                B topBlock = B.byOrdinal(composer.blockAt(res, lx, lz, top));
                long h = Hash.hash(flowerSeed, x, z);
                double u = Hash.unit(h);
                double t = world.climate.temperatureAt(c.baseTemp, top);
                // Water surface plants.
                if (g.waterTop[k] != ColumnGrid.NO_WATER) {
                    int wt = g.waterTop[k];
                    double depth = wt - top;
                    if (g.waterKind[k] == TerrainSampler.WATER_LAKE && (b == PBiome.MARSH || b == PBiome.FLOODPLAIN_WOODLAND || b == PBiome.ALLUVIAL_MEADOW) && depth <= 3 && u < 0.08 && t > -0.3) {
                        out.add(x, wt + 1, z, "minecraft:lily_pad", Placements.MODE_AIR);
                    } else if (depth >= 2 && depth < 12 && u < 0.25 && t > -0.4 && g.waterKind[k] != TerrainSampler.WATER_RIVER) {
                        out.add(x, top + 1, z, "minecraft:seagrass", Placements.MODE_WATER);
                    }
                    continue;
                }
                if (covered[lz * 16 + lx]) continue;
                // Snow cover above the local freezing level.
                if (t < -0.45 && topBlock != B.WATER && topBlock != B.ICE && topBlock != B.AIR) {
                    int layers = g.slope[k] > 1.0 ? 1 : 1 + (int) (Hash.unit(Hash.mix(h)) * 2);
                    if (topBlock == B.GRASS) out.add(x, top, z, "minecraft:grass_block[snowy=true]", Placements.MODE_SOLID);
                    if (topBlock == B.PODZOL) out.add(x, top, z, "minecraft:podzol[snowy=true]", Placements.MODE_SOLID);
                    if (topBlock != B.SNOW_BLOCK) out.add(x, top + 1, z, "minecraft:snow[layers=" + layers + "]", Placements.MODE_AIR);
                    continue;
                }
                // Rock-slope ledges: a bottom slab against a one-block step up, only on matching rock.
                String slab = slabFor(topBlock.id);
                if (slab != null && g.slope[k] > 0.45 && s.slabDetail() > 0) {
                    boolean step = false;
                    int[][] d4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                    for (int[] d : d4) {
                        int nk = k + d[1] * ColumnGrid.SIZE + d[0];
                        if (g.top[nk] == top + 1 && g.waterTop[nk] == ColumnGrid.NO_WATER) step = true;
                    }
                    if (step && Hash.unit(Hash.hash(slabSeed, x, z)) < 0.42 * s.slabDetail()) {
                        out.add(x, top + 1, z, slab + "[type=bottom,waterlogged=false]", Placements.MODE_AIR);
                        continue;
                    }
                }
                if (topBlock == B.SAND || topBlock == B.RED_SAND) {
                    if (b == PBiome.SANDY_DESERT || b == PBiome.RED_BADLANDS || b == PBiome.DRY_SCRUBLAND) {
                        if (Math.floorMod(x, 4) == 0 && Math.floorMod(z, 4) == 0 && u < 0.08 * vd && b == PBiome.SANDY_DESERT) {
                            int ht = 1 + (int) (Hash.unit(Hash.mix(h + 1)) * 3);
                            for (int i = 1; i <= ht; i++) out.add(x, top + i, z, "minecraft:cactus", Placements.MODE_AIR);
                        } else if (u < 0.03 * vd) {
                            out.add(x, top + 1, z, "minecraft:dead_bush", Placements.MODE_AIR);
                        } else if (u < 0.07 * vd && b != PBiome.SANDY_DESERT) {
                            out.add(x, top + 1, z, "minecraft:short_dry_grass", Placements.MODE_AIR);
                        }
                    } else if (b == PBiome.BEACH_DUNES && u < 0.04 * vd) {
                        out.add(x, top + 1, z, "minecraft:short_dry_grass", Placements.MODE_AIR);
                    }
                    // Sugar cane on banks right at water level.
                    sugarCane(g, k, x, z, top, u, out);
                    continue;
                }
                if (topBlock == B.MUD && (b == PBiome.MANGROVE_WETLAND || b == PBiome.MARSH) && u < 0.1 * vd) {
                    out.add(x, top + 1, z, "minecraft:short_grass", Placements.MODE_AIR);
                    continue;
                }
                if (!(topBlock == B.GRASS || topBlock == B.PODZOL || topBlock == B.MOSS || topBlock == B.COARSE_DIRT || topBlock == B.DIRT)) continue;
                if (sugarCane(g, k, x, z, top, u, out)) continue;
                plants(b, x, z, top, u, h, topBlock, out, vd, c);
            }
        }
    }

    private boolean sugarCane(ColumnGrid g, int k, int x, int z, int top, double u, Placements out) {
        int[][] d4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : d4) {
            int nk = k + d[1] * ColumnGrid.SIZE + d[0];
            if (g.waterTop[nk] == top && g.waterKind[nk] != TerrainSampler.WATER_SEA && u < 0.12) {
                int ht = 1 + (int) (u * 25) % 3;
                for (int i = 1; i <= ht; i++) out.add(x, top + i, z, "minecraft:sugar_cane", Placements.MODE_AIR);
                return true;
            }
        }
        return false;
    }

    private void plants(PBiome b, int x, int z, int top, double u, long h, B topBlock, Placements out, double vd, TerrainSampler.Column c) {
        double fp = Simplex.fbm(flowerSeed, x / 22.0, z / 22.0, 2, 2.0, 0.5); // flower patches
        double v = Hash.unit(Hash.mix(h + 11));
        double grass, tall, fern, flower, extra;
        String[] flowers;
        String extraState = null;
        switch (b) {
            case ALLUVIAL_MEADOW -> { grass = 0.5; tall = 0.08; fern = 0; flower = 0.12; extra = 0.02; extraState = "minecraft:bush";
                flowers = new String[]{"minecraft:oxeye_daisy", "minecraft:cornflower", "minecraft:azure_bluet", "minecraft:dandelion", "minecraft:poppy"}; }
            case ROLLING_GRASSLAND -> { grass = 0.45; tall = 0.06; fern = 0; flower = 0.05; extra = 0.03; extraState = "minecraft:wildflowers[facing=north,flower_amount=3]";
                flowers = new String[]{"minecraft:dandelion", "minecraft:poppy", "minecraft:oxeye_daisy", "minecraft:cornflower"}; }
            case ALPINE_MEADOW -> { grass = 0.35; tall = 0.02; fern = 0.02; flower = 0.14; extra = 0;
                flowers = new String[]{"minecraft:allium", "minecraft:azure_bluet", "minecraft:cornflower", "minecraft:oxeye_daisy", "minecraft:lily_of_the_valley"}; }
            case TEMPERATE_MIXED_FOREST, FLOODPLAIN_WOODLAND -> { grass = 0.25; tall = 0.03; fern = 0.06; flower = 0.02; extra = 0.12; extraState = "minecraft:leaf_litter[facing=north,segment_amount=3]";
                flowers = new String[]{"minecraft:lily_of_the_valley", "minecraft:poppy", "minecraft:dandelion"}; }
            case BIRCH_UPLANDS -> { grass = 0.3; tall = 0.03; fern = 0.04; flower = 0.03; extra = 0.04; extraState = "minecraft:pink_petals[facing=north,flower_amount=2]";
                flowers = new String[]{"minecraft:lily_of_the_valley", "minecraft:white_tulip"}; }
            case CONIFER_FOOTHILLS, BOREAL_FOREST -> { grass = 0.18; tall = 0.02; fern = 0.18; flower = 0.01; extra = 0.02; extraState = "minecraft:sweet_berry_bush[age=3]";
                flowers = new String[]{"minecraft:blue_orchid", "minecraft:lily_of_the_valley"}; }
            case WET_COASTAL_FOREST, TROPICAL_RAINFOREST -> { grass = 0.3; tall = 0.06; fern = 0.25; flower = 0.01; extra = 0.12; extraState = "minecraft:moss_carpet";
                flowers = new String[]{"minecraft:blue_orchid"}; }
            case WARM_SAVANNA, STEPPE -> { grass = 0.4; tall = 0.1; fern = 0; flower = 0.01; extra = 0.12; extraState = "minecraft:short_dry_grass";
                flowers = new String[]{"minecraft:dandelion", "minecraft:orange_tulip"}; }
            case DRY_SCRUBLAND -> { grass = 0.15; tall = 0.02; fern = 0; flower = 0; extra = 0.18; extraState = "minecraft:short_dry_grass";
                flowers = new String[]{"minecraft:dead_bush"}; }
            case MARSH -> { grass = 0.5; tall = 0.15; fern = 0.05; flower = 0.04; extra = 0;
                flowers = new String[]{"minecraft:blue_orchid"}; }
            case KARST_COUNTRY, GLACIAL_VALLEY, WINDSWEPT_PLATEAU, BASALT_HIGHLANDS -> { grass = 0.3; tall = 0.03; fern = 0.03; flower = 0.03; extra = 0.02; extraState = "minecraft:bush";
                flowers = new String[]{"minecraft:azure_bluet", "minecraft:oxeye_daisy"}; }
            case FROZEN_TUNDRA -> { grass = 0.15; tall = 0; fern = 0.05; flower = 0; extra = 0; flowers = new String[]{"minecraft:dandelion"}; }
            default -> { grass = 0.2; tall = 0.02; fern = 0.0; flower = 0.01; extra = 0; flowers = new String[]{"minecraft:dandelion"}; }
        }
        if (topBlock == B.COARSE_DIRT) { grass *= 0.3; tall = 0; flower *= 0.2; }
        double flowerBoost = MathUtil.smoothstep(0.15, 0.45, fp);
        double acc = 0;
        if (u < (acc += flower * (0.25 + 2.5 * flowerBoost) * vd)) {
            String f = flowers[(int) (v * flowers.length)];
            out.add(x, top + 1, z, f, Placements.MODE_AIR);
        } else if (u < (acc += tall * vd)) {
            out.add(x, top + 1, z, "minecraft:tall_grass[half=lower]", Placements.MODE_AIR);
            out.add(x, top + 2, z, "minecraft:tall_grass[half=upper]", Placements.MODE_AIR);
        } else if (u < (acc += fern * vd)) {
            if (v < 0.25) {
                out.add(x, top + 1, z, "minecraft:large_fern[half=lower]", Placements.MODE_AIR);
                out.add(x, top + 2, z, "minecraft:large_fern[half=upper]", Placements.MODE_AIR);
            } else {
                out.add(x, top + 1, z, "minecraft:fern", Placements.MODE_AIR);
            }
        } else if (u < (acc += extra * vd) && extraState != null) {
            out.add(x, top + 1, z, extraState, Placements.MODE_AIR);
        } else if (u < (acc += grass * vd)) {
            out.add(x, top + 1, z, "minecraft:short_grass", Placements.MODE_AIR);
        }
    }
}
