package com.powderpass.backrooms.gen.levels;

import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.gen.B;
import com.powderpass.backrooms.gen.CellCtx;
import com.powderpass.backrooms.gen.CustomBlocks;
import com.powderpass.backrooms.gen.DecorSpec;
import com.powderpass.backrooms.gen.Dest;
import com.powderpass.backrooms.gen.Feature;
import com.powderpass.backrooms.gen.FeatureDef;
import com.powderpass.backrooms.gen.GenSettings;
import com.powderpass.backrooms.gen.LevelGenerator;
import com.powderpass.backrooms.util.Rng;
import com.powderpass.backrooms.util.ValueNoise;
import org.bukkit.Material;

import java.util.List;

/**
 * Level 1 — Habitable Zone, as an endless underground parking garage. One continuous low concrete floor plate:
 * 32-block bays, each with a drive aisle down the middle (v 12..19) and a row of stalls either side, polished
 * andesite columns on an 8-block grid, beams under the ceiling, yellow stall lines. Open by construction, so every
 * spot is reachable. Some bays have a back wall with gaps; a rare few hide a supply room or a safe room.
 * Deterioration (andesite and tuff patches, puddles, stains) clusters in localised zones.
 */
public final class HabitableGenerator extends LevelGenerator {
    private static final int FLOOR = 64;
    private static final int CEIL = FLOOR + 5;
    static final int AISLE0 = 12, AISLE1 = 19;
    static final int R_NONE = 0, R_CACHE = 1, R_SAFE = 2;
    /** Decoration ids of the parked (drivable) cars and the steam pipe runs; see GarageProps. */
    public static final String CAR_MODEL = "garage_car", PIPE_MODEL = "garage_pipes:15.5";

    // the garage's own textured concrete (see CustomBlocks and tools/textures/recipes_level1.py)
    private static final B MASS = CustomBlocks.get("garage_mass");
    private static final B FLOOR_B = CustomBlocks.get("garage_floor");
    /** Broad discoloured patches: patched repairs in darker, rougher concrete. */
    private static final B FLOOR_ALT = CustomBlocks.get("garage_mass");
    private static final B DAMAGED = CustomBlocks.get("garage_floor_cracked");
    private static final B OIL = CustomBlocks.get("garage_floor_oil");
    private static final B WALL = CustomBlocks.get("garage_wall");
    private static final B WALL_STAINED = CustomBlocks.get("garage_wall_stained");
    private static final B CEIL_B = CustomBlocks.get("garage_ceiling");
    private static final B PILLAR = CustomBlocks.get("garage_column");
    private static final B HAZARD = CustomBlocks.get("garage_hazard");
    private static final B BEAM = CustomBlocks.get("garage_wall");
    private static final B LINE = CustomBlocks.get("garage_line");
    private static final B WOOD = B.of(Material.SPRUCE_PLANKS);
    private static final B BARREL = B.of(Material.BARREL, "[facing=up]");
    private static final B TILE = B.of(Material.WHITE_CONCRETE);
    private static final B IRON = B.of(Material.IRON_BLOCK);

    public HabitableGenerator(long seed, GenSettings gs) {
        super(Level.LEVEL_1, seed, gs, 32, 4, 2, new int[]{FLOOR}, FLOOR - 8, FLOOR + 10, 0.0, 0.0);
    }

    @Override
    protected List<FeatureDef> featureDefs() {
        return List.of(
                // onward to the Office in every district; back down to the Poolrooms more rarely
                FeatureDef.transition("elevator_lobby", Dest.to(Level.LEVEL_4), 1.0).withEffect("dwell"),
                FeatureDef.transition("flooded_stairs", Dest.to(Level.POOLROOMS), gs.get("back-chance", 0.4)),
                FeatureDef.landmark("supply_depot", gs.get("landmark-chance", 0.3)));
    }

    @Override
    public String biomeKey() {
        return "minecraft:plains";
    }

    // ------------------------------------------------------------------ layout queries

    private boolean dark(int cx, int cz) {
        return zone(cx, cz, 0xDA41L, 160) > 1.0 - gs.get("dark-zone-chance", 0.25) * 0.6;
    }

    /** Columns stand on the stall lines at the back of each stall row, never inside a stall or the aisle. */
    static boolean pillar(int u, int v) {
        return Math.floorMod(u, 8) == 2 && (v == 4 || v == 27);
    }

    /** Back wall along v=1 with two gaps (bays where the garage has been partitioned). */
    boolean backWall(int cx, int cz) {
        return zone(cx, cz, 0xBA77L, 96) > 0.62;
    }

    static boolean backWallGap(int u) {
        int m = Math.floorMod(u, 16);
        return m >= 5 && m <= 9;
    }

    /** Rare enclosed room in the north stall row: {u0, v0, u1, v1, kind}, interior inclusive; door at v1+1. */
    int[] room(int cx, int cz) {
        if (featureAt(0, cx, cz) != null) return null;
        long h = ch(0, cx, cz, 0x2001);
        if (!Rng.chance(h, gs.get("supply-room-chance", 0.06))) return null;
        int kind = Rng.chance(Rng.hash(h, 1), 0.35) ? R_SAFE : R_CACHE;
        return new int[]{18, 3, 23, 8, kind};
    }

    private int roomKindAt(int cx, int cz, int u, int v) {
        int[] r = room(cx, cz);
        return r != null && u >= r[0] && u <= r[2] && v >= r[1] && v <= r[3] ? r[4] : -1;
    }

    @Override
    public boolean isSafeZone(int x, int y, int z) {
        return roomKindAt(cellOf(x), cellOf(z), localOf(x), localOf(z)) == R_SAFE;
    }

    @Override
    public int lootTier(int x, int y, int z) {
        int k = roomKindAt(cellOf(x), cellOf(z), localOf(x), localOf(z));
        Feature f = featureAt(0, cellOf(x), cellOf(z));
        return k == R_CACHE || (f != null && f.id().equals("supply_depot")) ? 2 : 1;
    }

    /** Is this column a stall row (where cars, puddles and damage go), not the aisle? */
    static boolean stallRow(int v) {
        return (v >= 3 && v <= 11) || (v >= 20 && v <= 28);
    }

    private boolean puddle(int wx, int wz, int cx, int cz) {
        double w = wear(cx, cz, 0x3EA1L);
        return w > 0.25 && ValueNoise.sample(seed ^ 0x9E, wx / 4.0, wz / 4.0) > 0.93 - 0.1 * w;
    }

    // ------------------------------------------------------------------ painting

    @Override
    protected void paintCell(CellCtx c) {
        int[] room = room(c.cx, c.cz);
        boolean back = backWall(c.cx, c.cz);
        double w = wear(c.cx, c.cz, 0x3EA1L);
        for (int u = c.u0; u <= c.u1; u++) {
            for (int v = c.v0; v <= c.v1; v++) {
                c.set(u, bottomY, v, B.BEDROCK);
                for (int y = bottomY + 1; y < c.fy; y++) c.set(u, y, v, MASS);
                c.set(u, roofY, v, B.BEDROCK);
                for (int y = CEIL + 1; y < roofY; y++) c.set(u, y, v, MASS);
                c.set(u, CEIL, v, CEIL_B);
                int wx = c.wx(u), wz = c.wz(v);
                // floor: smooth stone, broad andesite discolouration, tuff where it is breaking up
                double n = ValueNoise.sample(seed ^ 0xF10, wx / 9.0, wz / 9.0);
                B floor = n > 0.76 ? FLOOR_ALT : FLOOR_B;
                if (oil(wx, wz, u, v)) floor = OIL;
                if (w > 0.3 && ValueNoise.sample(seed ^ 0x7FF, wx / 3.0, wz / 3.0) > 0.88 - 0.1 * w) floor = DAMAGED;
                // stall lines and the dashed aisle centre line
                int su = Math.floorMod(u, 4);
                if (su == 2 && ((v >= 5 && v <= 10) || (v >= 21 && v <= 26))) floor = LINE;
                if ((v == 15 || v == 16) && Math.floorMod(wx, 6) < 3 && v == 15) floor = LINE;
                c.set(u, c.fy, v, floor);
                for (int y = c.fy + 1; y < CEIL; y++) c.set(u, y, v, B.AIR);
                if (stallRow(v) && floor != LINE && room == null && featureAt(c.floor, c.cx, c.cz) == null && puddle(wx, wz, c.cx, c.cz)) {
                    c.set(u, c.fy - 1, v, MASS);
                    c.set(u, c.fy, v, B.WATER);
                }
                // beams under the ceiling on every 8th line across the bay
                if (Math.floorMod(u, 8) == 2) c.set(u, CEIL - 1, v, BEAM);
                if (pillar(u, v)) {
                    c.set(u, c.fy + 1, v, HAZARD); // yellow/black band at the foot of every column
                    c.column(u, v, c.fy + 2, CEIL - 1, PILLAR);
                }
                if (back && v == 1 && !backWallGap(u)) wallColumn(c, u, v);
            }
        }
        if (room != null) paintRoom(c, room);
        Feature f = featureAt(c.floor, c.cx, c.cz);
        if (f != null) feature(c, f);
    }

    /** Oil drips under where engines stand: a block or two near the middle of some stalls. */
    private boolean oil(int wx, int wz, int u, int v) {
        boolean row0 = v >= 6 && v <= 9, row1 = v >= 22 && v <= 25;
        if (!row0 && !row1 || Math.floorMod(u, 4) == 2) return false;
        long h = Rng.hash(seed ^ 0x0111L, Math.floorDiv(wx - 3, 4), wz >> 5, row0 ? 0 : 1);
        if (!Rng.chance(h, 0.4)) return false;
        int cu = Math.floorMod(u, 4) == 0 ? 0 : 1, cv = row0 ? (Rng.chance(Rng.hash(h, 1), 0.5) ? 7 : 8) : (Rng.chance(Rng.hash(h, 1), 0.5) ? 23 : 24);
        return cu == 0 && (v == cv || (Rng.chance(Rng.hash(h, 2), 0.4) && Math.abs(v - cv) == 1));
    }

    /** One column of wall: a grimy splash zone at the bottom, water-stained courses in patches, clean board-formed concrete. */
    private void wallColumn(CellCtx c, int u, int v) {
        int wx = c.wx(u), wz = c.wz(v);
        boolean damp = ValueNoise.sample(seed ^ 0x57A1, wx / 7.0, wz / 7.0) > 0.66;
        for (int y = c.fy + 1; y < CEIL; y++) c.set(u, y, v, y == c.fy + 1 || damp ? WALL_STAINED : WALL);
    }

    /** Box of wall with a 2-wide doorway onto the aisle side (v1+1). */
    private void enclose(CellCtx c, int u0, int v0, int u1, int v1, int doorU, B wall) {
        for (int u = u0 - 1; u <= u1 + 1; u++) {
            for (int v = v0 - 1; v <= v1 + 1; v++) {
                boolean edge = u == u0 - 1 || u == u1 + 1 || v == v0 - 1 || v == v1 + 1;
                if (!c.in(u, v)) continue;
                if (edge) wallColumn(c, u, v);
                else for (int y = c.fy + 1; y < CEIL; y++) c.set(u, y, v, B.AIR);
                if (!edge) c.set(u, c.fy, v, FLOOR_B);
            }
        }
        for (int u = doorU; u <= doorU + 1; u++) {
            if (!c.in(u, v1 + 1)) continue;
            for (int y = c.fy + 1; y <= c.fy + 3; y++) c.set(u, y, v1 + 1, B.AIR);
            c.set(u, c.fy, v1 + 1, FLOOR_B);
        }
    }

    private void paintRoom(CellCtx c, int[] r) {
        enclose(c, r[0], r[1], r[2], r[3], r[0] + 2, WALL);
        long h = Rng.hash(seed ^ 0xF00D, c.cx, c.cz);
        for (int u = r[0]; u <= r[2]; u++) {
            for (int v = r[1]; v <= r[3]; v++) {
                if (!c.in(u, v)) continue;
                if (r[4] == R_SAFE) c.set(u, c.fy, v, WOOD);
                boolean shelf = r[4] == R_CACHE && (u == r[0] || u == r[2]) && v < r[3];
                if (shelf && Rng.chance(Rng.hash(h, u, v), 0.55)) c.set(u, c.fy + 1, v, BARREL);
            }
        }
    }

    private void feature(CellCtx c, Feature f) {
        switch (f.id()) {
            case "elevator_lobby" -> {
                enclose(c, 11, 1, 20, 9, 15, WALL);
                // the car: iron walls, open toward the lobby
                for (int u = 13; u <= 18; u++) {
                    for (int v = 1; v <= 5; v++) {
                        boolean wall = u == 13 || u == 18 || v == 1 || (v == 5 && (u < 15 || u > 16));
                        if (!c.in(u, v)) continue;
                        for (int y = c.fy + 1; y < CEIL; y++) c.set(u, y, v, wall ? IRON : B.AIR);
                    }
                }
                if (c.in(15, 3)) c.set(15, CEIL - 1, 3, B.light(10));
            }
            case "flooded_stairs" -> {
                // a tiled stairwell going down from the aisle into black water
                enclose(c, 13, 2, 17, 10, 15, WALL);
                for (int u = 13; u <= 17; u++) {
                    for (int v = 2; v <= 10; v++) {
                        if (!c.in(u, v)) continue;
                        int drop = Math.max(0, Math.min(6, 10 - v));
                        for (int y = c.fy - 7; y < c.fy - drop; y++) c.set(u, y, v, TILE);
                        c.set(u, c.fy - drop, v, drop > 0 ? B.stairs(Material.STONE_STAIRS, "south", false) : TILE);
                        for (int y = c.fy - drop + 1; y < CEIL; y++) c.set(u, y, v, B.AIR);
                        for (int y = c.fy - 6; y <= c.fy - 3; y++) {
                            if (c.type(u, y, v) == Material.AIR || c.type(u, y, v) == Material.STONE_STAIRS) {
                                c.set(u, y, v, c.type(u, y, v) == Material.STONE_STAIRS
                                        ? B.of(Material.STONE_STAIRS, "[facing=south,half=bottom,waterlogged=true]") : B.WATER);
                            }
                        }
                    }
                }
            }
            case "supply_depot" -> {
                for (int u = 10; u <= 21; u += 3) {
                    for (int v = 4; v <= 8; v += 4) {
                        if (c.in(u, v)) c.set(u, c.fy + 1, v, BARREL);
                    }
                }
            }
            default -> {
            }
        }
    }

    @Override
    protected int[] triggerBox(Feature f) {
        return switch (f.id()) {
            case "elevator_lobby" -> new int[]{14, 0, 2, 17, 1, 4};
            case "flooded_stairs" -> new int[]{13, -6, 2, 17, -5, 4};
            default -> null;
        };
    }

    @Override
    public int[] safeLocal(int floor, int cx, int cz) {
        return new int[]{6, 15}; // the drive aisle, away from columns and puddles
    }

    @Override
    public boolean isSafeCell(int floor, int cx, int cz) {
        return true;
    }

    @Override
    public String zoneName(int floor, int cx, int cz) {
        int[] r = room(cx, cz);
        return (dark(cx, cz) ? "dead-sector" : "garage") + (backWall(cx, cz) ? "/partitioned" : "")
                + (r == null ? "" : r[4] == R_SAFE ? " +safe-room" : " +supply-room");
    }

    // ------------------------------------------------------------------ decorations

    @Override
    protected void cellDecor(int floor, int cx, int cz, List<DecorSpec> out) {
        long h = ch(floor, cx, cz, 0xDEC2);
        double ox = cx * S, oz = cz * S;
        int fy = FLOOR + 1;
        double lit = dark(cx, cz) ? 0.05 : 0.75;
        // tube lights down the aisle, cage lamps over the stall rows
        for (int u = 4; u < S; u += 8) {
            long hh = Rng.hash(h, 1, u);
            out.add(lamp("tube_light", ox + u + 0.5, CEIL, oz + 16.0, 90f, hh, lampState(hh, lit), cx * S + u, CEIL - 2, cz * S + 15, 14));
        }
        for (int u = 4; u < S; u += 16) {
            for (int v : new int[]{7, 24}) {
                long hh = Rng.hash(h, 2, u * 31L + v);
                out.add(lamp("cage_lamp", ox + u + 0.5, CEIL, oz + v + 0.5, 0f, hh, lampState(hh, lit * 0.7), cx * S + u, CEIL - 2, cz * S + v, 12));
            }
        }
        int[] room = room(cx, cz);
        if (room != null) {
            long hr = Rng.hash(h, 10);
            double mu = (room[0] + room[2]) / 2.0 + 0.5, mv = (room[1] + room[3]) / 2.0 + 0.5;
            boolean safe = room[4] == R_SAFE;
            out.add(lamp("cage_lamp", ox + mu, CEIL, oz + mv, 0f, hr, safe ? 0 : lampState(hr, 0.7), cx * S + (int) mu, CEIL - 2, cz * S + (int) mv, safe ? 13 : 11));
            out.add(new DecorSpec(safe ? "chalk_safe" : "supply_sign", ox + room[0] + 1.0, fy + 2.2, oz + room[3] + 2.0, 0f, 0f, 0f, hr));
            if (safe) out.add(spec("bench", cx, cz, mu, fy, room[1] + 0.5, 0f, hr));
        }
        Feature f = featureAt(floor, cx, cz);
        if (f != null) {
            switch (f.id()) {
                case "elevator_lobby" -> {
                    out.add(spec("freight_doors", cx, cz, 16.0, fy, 5.5, 0f, f.seed()));
                    out.add(spec("elevator_panel", cx, cz, 17.6, fy + 1.4, 6.02, 0f, f.seed()));
                    out.add(new DecorSpec("stencil_sign", ox + 16.0, fy + 3.4, oz + 11.02, 0f, 0f, 0f, f.seed()));
                }
                case "flooded_stairs" -> out.add(spec("drip_emitter", cx, cz, 15.5, CEIL - 0.05, 5.5, 0f, f.seed()));
                case "supply_depot" -> {
                    for (int i = 0; i < 3; i++) out.add(spec("pallet_stack", cx, cz, 25.5 + i * 2.2, fy, 6.5, (float) (i * 17), Rng.hash(f.seed(), i)));
                }
                default -> {
                }
            }
        }
        // parked cars in a few stalls; most of the garage is empty
        double density = gs.get("decor-density", 1.0);
        for (int row = 0; row < 2; row++) {
            for (int s = 0; s < 7; s++) {
                double su = s * 4 + 4.5;
                long hs = Rng.hash(h, 20 + row, s);
                if (!Rng.chance(hs, 0.09 * density)) continue;
                if (row == 0 && (f != null || room != null) && su > 9 && su < 25) continue;
                double sv = row == 0 ? 7.5 : 24.5;
                out.add(new DecorSpec(CAR_MODEL, ox + su, fy, oz + sv, row == 0 ? 0f : 180f, 0f, 0f, hs));
            }
        }
    }

    @Override
    protected void cellDetails(int floor, int cx, int cz, List<DecorSpec> out) {
        long h = ch(floor, cx, cz, 0xD7A3);
        double ox = cx * S, oz = cz * S;
        int fy = FLOOR + 1;
        double w = wear(cx, cz, 0x3EA1L);
        // overhead services: two steam pipe runs (some leaking) over the north stalls, a duct over the south ones in some bays
        out.add(detail(DecorSpec.TIER_ARCH, Rng.hash(h, 1), PIPE_MODEL, ox + 0.3, CEIL - 1.2, oz + 2.5, 0f, 0f));
        out.add(detail(DecorSpec.TIER_ARCH, Rng.hash(h, 2), PIPE_MODEL, ox + 16.3, CEIL - 1.2, oz + 2.5, 0f, 0f));
        if (zone(cx, cz, 0xD0C7L, 80) > 0.55) {
            out.add(detail(DecorSpec.TIER_ARCH, Rng.hash(h, 3), "d:duct:15", ox + 1.5, CEIL - 1.0, oz + 29.5, 0f, 0f));
        }
        // a construction joint in the ceiling slab, parallel to the beams and between them
        out.add(detail(DecorSpec.TIER_ARCH, Rng.hash(h, 4), "d:strip:light_gray_concrete:" + S + ":0.008:0.05", ox + 6.02, CEIL - 0.009, oz, 90f, 0f));
        // a junction box with conduit on one column
        if (Rng.chance(Rng.hash(h, 5), 0.5)) {
            out.add(detail(DecorSpec.TIER_ENV, Rng.hash(h, 6), "d:jbox:1.6", ox + 10.5, fy + 1.4, oz + 28.003, 0f, 0f));
        }
        if (backWall(cx, cz)) {
            // panels and a vent grille on the partition wall
            out.add(detail(DecorSpec.TIER_ARCH, Rng.hash(h, 7), "d:grille:0.9:0.45", ox + 2.0, fy + 2.6, oz + 2.003, 0f, 0f));
            for (int u = 4; u < S; u += 16) {
                out.add(detail(DecorSpec.TIER_ARCH, Rng.hash(h, 8, u), "d:strip:gray_concrete:0.05:" + (CEIL - fy) + ":0.01", ox + u, fy, oz + 2.003, 0f, 0f));
            }
            if (Rng.chance(Rng.hash(h, 9), 0.1 + 0.5 * w)) {
                out.add(detail(DecorSpec.TIER_AGING, Rng.hash(h, 10), "d:streak:brown_terracotta:2.2", ox + 11.5, CEIL - 0.3, oz + 2.006, 0f, 0f));
            }
        }
        // deterioration, clustered: discoloured slab, cracks, stains on columns and ceiling
        if (Rng.chance(Rng.hash(h, 11), 0.1 + 0.6 * w)) {
            out.add(detail(DecorSpec.TIER_AGING, Rng.hash(h, 12), "d:fpatch:andesite:3.2:2.4", ox + 3 + Rng.pick(Rng.hash(h, 13), 20), fy + 0.001, oz + 12.5, (float) Rng.pick(h, 4) * 90f, 0f));
        }
        if (Rng.chance(Rng.hash(h, 14), 0.08 + 0.5 * w)) {
            out.add(detail(DecorSpec.TIER_AGING, Rng.hash(h, 15), "d:crack:gray_concrete:2.6:f", ox + 6 + Rng.pick(Rng.hash(h, 16), 20), fy + 0.002, oz + 14 + Rng.pick(Rng.hash(h, 17), 4), 0f, 0f));
        }
        if (Rng.chance(Rng.hash(h, 18), 0.05 + 0.45 * w)) {
            // a moisture run down the south face of a column
            out.add(detail(DecorSpec.TIER_AGING, Rng.hash(h, 19), "d:streak:gray_concrete:2.2", ox + 18.5, CEIL - 1.1, oz + 5.003, 0f, 0f));
        }
        if (Rng.chance(Rng.hash(h, 20), 0.04 + 0.35 * w)) {
            out.add(detail(DecorSpec.TIER_AGING, Rng.hash(h, 21), "d:cpatch:brown_terracotta:2.0:1.4", ox + 20, CEIL, oz + 4, 0f, 0f));
        }
        if (Rng.chance(Rng.hash(h, 22), 0.03 + 0.3 * w)) {
            out.add(detail(DecorSpec.TIER_AGING, Rng.hash(h, 23), "d:crack:gray_concrete:1.8:c", ox + 28, CEIL, oz + 14, 0f, 0f));
        }
    }
}
