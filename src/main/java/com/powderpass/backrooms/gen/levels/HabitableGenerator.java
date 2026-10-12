package com.powderpass.backrooms.gen.levels;

import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.gen.B;
import com.powderpass.backrooms.gen.CellCtx;
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
 * Level 1 — Habitable Zone. Not a maze: an endless grid of straight corridors running along every cell line,
 * each line with its own width (3 to 9) and height (4 to 12). Every corridor crosses every other one, so the
 * whole level is connected by construction. Between corridors is solid concrete with rooms carved into it,
 * each opening onto the corridor beside it.
 */
public final class HabitableGenerator extends LevelGenerator {
    private static final int FLOOR = 64;
    static final int R_NONE = 0, R_STORAGE = 1, R_CACHE = 2, R_SAFE = 3, R_EMPTY = 4, R_FLOODED = 5;
    private static final String[] ROOMS = {"none", "storage", "cache", "safe-room", "empty", "flooded"};

    private static final B MASS = B.of(Material.GRAY_CONCRETE);
    private static final B FLOOR_B = B.of(Material.SMOOTH_STONE);
    private static final B CEIL = B.of(Material.LIGHT_GRAY_CONCRETE);
    private static final B LINE = B.of(Material.YELLOW_CONCRETE);
    private static final B WOOD = B.of(Material.SPRUCE_PLANKS);
    private static final B BARREL = B.of(Material.BARREL, "[facing=up]");

    public HabitableGenerator(long seed, GenSettings gs) {
        super(Level.LEVEL_1, seed, gs, 40, 3, 2, new int[]{FLOOR}, FLOOR - 6, FLOOR + 14, 0.0, 0.0);
    }

    @Override
    protected List<FeatureDef> featureDefs() {
        return List.of(
                FeatureDef.transition("wallpaper_door", Dest.to(Level.LEVEL_0), 1.0),
                FeatureDef.transition("freight_elevator", Dest.to(Level.LEVEL_4), 1.0).withEffect("dwell"),
                FeatureDef.transition("loading_shutter", Dest.to(Level.PLAINS), gs.get("plains-chance", 0.7)),
                FeatureDef.landmark("supply_depot", 1.0));
    }

    @Override
    public String biomeKey() {
        return "minecraft:plains";
    }

    // ------------------------------------------------------------------ corridor lines

    /** Half-width of the corridor on a line (axis 0 = runs along X at z=line*S, axis 1 = runs along Z at x=line*S). */
    int half(int axis, int line) {
        double r = Rng.unit(Rng.hash(seed ^ 0xC0EEL, axis, line));
        if (r < gs.get("grand-hall-chance", 0.15)) return 4;
        if (r < 0.45) return 1;
        if (r < 0.8) return 2;
        return 3;
    }

    int height(int axis, int line) {
        int h = half(axis, line);
        return h == 4 ? 12 : h == 1 ? 4 : 5 + Rng.pick(Rng.hash(seed ^ 0x4E16L, axis, line), 2);
    }

    private boolean dark(int cx, int cz) {
        return zone(cx, cz, 0xDA41L, 140) > 1.0 - gs.get("dark-zone-chance", 0.25) * 0.6;
    }

    /** Which corridor lines cover a cell-local column: bit 1 north, 2 south, 4 west, 8 east. */
    private int corridorMask(int cx, int cz, int u, int v) {
        int m = 0;
        if (v <= half(0, cz)) m |= 1;
        if (v >= S - half(0, cz + 1)) m |= 2;
        if (u <= half(1, cx)) m |= 4;
        if (u >= S - half(1, cx + 1)) m |= 8;
        return m;
    }

    private int corridorCeil(int cx, int cz, int mask) {
        int h = 0;
        if ((mask & 1) != 0) h = Math.max(h, height(0, cz));
        if ((mask & 2) != 0) h = Math.max(h, height(0, cz + 1));
        if ((mask & 4) != 0) h = Math.max(h, height(1, cx));
        if ((mask & 8) != 0) h = Math.max(h, height(1, cx + 1));
        return FLOOR + 1 + h;
    }

    // ------------------------------------------------------------------ rooms

    /** Room carved along the north (side 0) or south (side 1) corridor: {u0, v0, u1, v1, doorU, kind} interior, inclusive. */
    int[] room(int cx, int cz, int side) {
        Feature f = featureAt(0, cx, cz);
        if (f != null) {
            if (side != 0) return null;
            int v0 = half(0, cz) + 2;
            return new int[]{10, v0, 21, v0 + 8, 15, R_NONE};
        }
        long h = ch(0, cx, cz, 0x2001 + side);
        if (!Rng.chance(h, gs.get("room-chance", 0.7))) return null;
        int west = half(1, cx) + 3, east = S - half(1, cx + 1) - 4;
        int w = 6 + Rng.pick(Rng.hash(h, 1), 9);
        int u0 = west + Rng.pick(Rng.hash(h, 2), Math.max(1, east - west - w));
        int depth = 4 + Rng.pick(Rng.hash(h, 3), 5);
        double r = Rng.unit(Rng.hash(h, 4));
        int kind = r < 0.35 ? R_STORAGE : r < 0.35 + gs.get("cache-chance", 0.08) ? R_CACHE : r < 0.55 ? R_SAFE : r < 0.65 ? R_FLOODED : R_EMPTY;
        if (side == 0) {
            int v0 = half(0, cz) + 2;
            return new int[]{u0, v0, u0 + w - 1, v0 + depth - 1, u0 + w / 2, kind};
        }
        int v1 = S - half(0, cz + 1) - 2;
        return new int[]{u0, v1 - depth + 1, u0 + w - 1, v1, u0 + w / 2, kind};
    }

    private int roomKindAt(int cx, int cz, int u, int v) {
        for (int side = 0; side < 2; side++) {
            int[] r = room(cx, cz, side);
            if (r != null && u >= r[0] && u <= r[2] && v >= r[1] && v <= r[3]) return r[5];
        }
        return -1;
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

    // ------------------------------------------------------------------ painting

    @Override
    protected void paintCell(CellCtx c) {
        int[][] rooms = {room(c.cx, c.cz, 0), room(c.cx, c.cz, 1)};
        boolean darkZone = dark(c.cx, c.cz);
        for (int u = c.u0; u <= c.u1; u++) {
            for (int v = c.v0; v <= c.v1; v++) {
                c.set(u, bottomY, v, B.BEDROCK);
                for (int y = bottomY + 1; y < c.fy; y++) c.set(u, y, v, MASS);
                c.set(u, roofY, v, B.BEDROCK);
                int mask = corridorMask(c.cx, c.cz, u, v);
                int ceil;
                B floor = FLOOR_B;
                if (mask != 0) {
                    ceil = corridorCeil(c.cx, c.cz, mask);
                    boolean centre = (v == 0 && (mask & 1) != 0) || (u == 0 && (mask & 4) != 0);
                    boolean intersection = (mask & 3) != 0 && (mask & 12) != 0;
                    if (centre && !intersection && Math.floorMod(c.wx(u) + c.wz(v), 3) != 0) floor = LINE;
                    boolean nearX = u <= half(1, c.cx) + 1 || u >= S - half(1, c.cx + 1) - 1;
                    boolean nearZ = v <= half(0, c.cz) + 1 || v >= S - half(0, c.cz + 1) - 1;
                    if (!(nearX && nearZ) && ValueNoise.sample(seed ^ 0x9E, c.wx(u) / 4.0, c.wz(v) / 4.0) > (darkZone ? 0.74 : 0.82)) {
                        c.set(u, c.fy - 1, v, MASS);
                        floor = B.WATER;
                    }
                } else {
                    int[] room = null;
                    for (int[] r : rooms) if (r != null && u >= r[0] && u <= r[2] && v >= r[1] && v <= r[3]) room = r;
                    boolean door = false;
                    for (int[] r : rooms) {
                        if (r == null || u < r[4] - 1 || u > r[4]) continue;
                        if ((r[1] == half(0, c.cz) + 2 && v == r[1] - 1) || (r[3] == S - half(0, c.cz + 1) - 2 && v == r[3] + 1)) door = true;
                    }
                    if (room == null && !door) {
                        for (int y = c.fy; y < roofY; y++) c.set(u, y, v, MASS);
                        continue;
                    }
                    ceil = c.fy + (door ? 4 : 5);
                    if (room != null && room[5] == R_SAFE) floor = WOOD;
                    if (room != null && room[5] == R_FLOODED) {
                        c.set(u, c.fy - 1, v, MASS);
                        floor = B.WATER;
                    }
                }
                c.set(u, c.fy, v, floor);
                for (int y = c.fy + 1; y < ceil; y++) c.set(u, y, v, B.AIR);
                c.set(u, ceil, v, CEIL);
                for (int y = ceil + 1; y < roofY; y++) c.set(u, y, v, MASS);
            }
        }
        // pillar rows down the middle of grand halls
        if (half(0, c.cz) == 4) {
            for (int u = 8; u < S - 6; u += 8) if (u > half(1, c.cx) + 2) c.column(u, 0, c.fy + 1, corridorCeil(c.cx, c.cz, 1) - 1, MASS);
        }
        if (half(1, c.cx) == 4) {
            for (int v = 8; v < S - 6; v += 8) if (v > half(0, c.cz) + 2) c.column(0, v, c.fy + 1, corridorCeil(c.cx, c.cz, 4) - 1, MASS);
        }
        for (int[] r : rooms) if (r != null) furnish(c, r);
        Feature f = featureAt(c.floor, c.cx, c.cz);
        if (f != null) feature(c, f, rooms[0]);
    }

    private void furnish(CellCtx c, int[] r) {
        long h = Rng.hash(seed ^ 0xF00D, c.cx, c.cz, r[0] * 31L + r[1]);
        switch (r[5]) {
            case R_STORAGE, R_CACHE -> {
                double fill = r[5] == R_CACHE ? 0.3 : 0.55;
                B[] goods = r[5] == R_CACHE ? new B[]{BARREL} : new B[]{BARREL, B.of(Material.STRIPPED_SPRUCE_WOOD), MASS};
                for (int u = r[0]; u <= r[2]; u++) {
                    for (int v : new int[]{r[1], r[3]}) {
                        if (u >= r[4] - 1 && u <= r[4]) continue;
                        long hh = Rng.hash(h, u, v);
                        if (!Rng.chance(hh, fill)) continue;
                        int height = r[5] == R_CACHE ? 1 : 1 + Rng.pick(Rng.hash(hh, 1), 2);
                        for (int y = 0; y < height; y++) c.set(u, c.fy + 1 + y, v, goods[Rng.pick(Rng.hash(hh, 2 + y), goods.length)]);
                    }
                }
            }
            default -> {
            }
        }
    }

    private void feature(CellCtx c, Feature f, int[] r) {
        int far = r[3];
        switch (f.id()) {
            case "wallpaper_door" -> {
                for (int u = 13; u <= 17; u++) {
                    for (int y = c.fy + 1; y <= c.fy + 4; y++) {
                        c.set(u, y, far, (u == 15 && y <= c.fy + 2) ? B.AIR : B.of(Material.SMOOTH_SANDSTONE));
                    }
                    c.set(u, c.fy, far, B.of(Material.YELLOW_TERRACOTTA));
                }
                c.set(15, c.fy + 1, far + 1, B.AIR);
                c.set(15, c.fy + 2, far + 1, B.AIR);
                c.set(15, c.fy, far + 1, B.of(Material.YELLOW_TERRACOTTA));
            }
            case "freight_elevator" -> {
                for (int u = 13; u <= 17; u++) {
                    for (int y = c.fy + 1; y <= c.fy + 4; y++) {
                        c.set(u, y, far - 3, (u >= 14 && u <= 16) ? B.AIR : B.of(Material.IRON_BLOCK));
                    }
                }
                c.fill(13, c.fy + 1, far - 2, 13, c.fy + 4, far, B.of(Material.IRON_BLOCK));
                c.fill(17, c.fy + 1, far - 2, 17, c.fy + 4, far, B.of(Material.IRON_BLOCK));
                c.set(15, c.fy + 4, far - 1, B.light(10));
            }
            case "loading_shutter" -> {
                for (int u = 12; u <= 18; u++) c.set(u, c.fy, far, B.of(Material.GRASS_BLOCK));
            }
            default -> {
            }
        }
    }

    @Override
    protected int[] triggerBox(Feature f) {
        int far = half(0, f.cz()) + 2 + 8;
        return switch (f.id()) {
            case "wallpaper_door" -> new int[]{15, 0, far + 1, 15, 1, far + 1};
            case "freight_elevator" -> new int[]{14, 0, far - 2, 16, 1, far};
            case "loading_shutter" -> new int[]{12, 0, far, 18, 1, far};
            default -> null;
        };
    }

    @Override
    public int[] safeLocal(int floor, int cx, int cz) {
        return new int[]{0, 0}; // corridor intersection
    }

    @Override
    public boolean isSafeCell(int floor, int cx, int cz) {
        return true;
    }

    @Override
    public String zoneName(int floor, int cx, int cz) {
        return (dark(cx, cz) ? "dead-sector" : "concrete") + " halls N" + (half(0, cz) * 2 + 1) + "/W" + (half(1, cx) * 2 + 1);
    }

    // ------------------------------------------------------------------ decorations

    @Override
    protected void cellDecor(int floor, int cx, int cz, List<DecorSpec> out) {
        long h = ch(floor, cx, cz, 0xDEC2);
        int fy = FLOOR + 1;
        double lit = dark(cx, cz) ? 0.05 : 0.8;
        // corridor lamps hang along the north (z) and west (x) lines this cell owns
        int hn = half(0, cz), hw = half(1, cx);
        int ceilN = FLOOR + 1 + height(0, cz), ceilW = FLOOR + 1 + height(1, cx);
        for (int u = 6; u < S - 2; u += 10) {
            if (u <= hw + 1) continue;
            int off = hn == 4 ? 2 : 0;
            long hh = Rng.hash(h, 1, u);
            out.add(lamp(hn >= 3 ? "cage_lamp" : "tube_light", cx * S + u + 0.5, ceilN, cz * S + off + 0.5, 90f, hh, lampState(hh, lit),
                    cx * S + u, ceilN - 2, cz * S + off, 14));
        }
        for (int v = 6; v < S - 2; v += 10) {
            if (v <= hn + 1) continue;
            int off = hw == 4 ? 2 : 0;
            long hh = Rng.hash(h, 2, v);
            out.add(lamp(hw >= 3 ? "cage_lamp" : "tube_light", cx * S + off + 0.5, ceilW, cz * S + v + 0.5, 0f, hh, lampState(hh, lit),
                    cx * S + off, ceilW - 2, cz * S + v, 14));
        }
        if (Rng.chance(Rng.hash(h, 3), 0.3)) {
            out.add(spec("ceiling_pipes", cx, cz, hw + 2.0, ceilN - 0.5, Math.max(1, hn) - 0.5, -90f, h));
        }
        Feature f = featureAt(floor, cx, cz);
        for (int side = 0; side < 2; side++) {
            int[] r = room(cx, cz, side);
            if (r == null) continue;
            double mu = (r[0] + r[2]) / 2.0 + 0.5, mv = (r[1] + r[3]) / 2.0 + 0.5;
            int ceil = FLOOR + 5;
            long hr = Rng.hash(h, 10, side);
            boolean safe = r[5] == R_SAFE;
            out.add(lamp("cage_lamp", cx * S + mu, ceil, cz * S + mv, 0f, hr, safe ? 0 : lampState(hr, lit * 0.8),
                    cx * S + (int) mu, ceil - 2, cz * S + (int) mv, safe ? 13 : 11));
            if (f != null) continue;
            int wallV = side == 0 ? r[1] - 1 : r[3] + 1;
            switch (r[5]) {
                case R_SAFE -> {
                    out.add(spec("bench", cx, cz, mu, fy, (side == 0 ? r[3] : r[1]) + 0.5, side == 0 ? 180f : 0f, hr));
                    out.add(new DecorSpec("chalk_safe", cx * S + r[0] + 1.5, fy + 2.6, cz * S + wallV + (side == 0 ? 1.0 : 0.0), side == 0 ? 0f : 180f, 0f, 0f, hr));
                    if (Rng.chance(hr, 0.4)) out.add(spec("radio", cx, cz, r[0] + 0.5, fy, r[1] + 0.5, 30f, hr));
                }
                case R_CACHE -> out.add(new DecorSpec("supply_sign", cx * S + r[0] + 2.0, fy + 3.0, cz * S + wallV + (side == 0 ? 0.0 : 1.0), side == 0 ? 180f : 0f, 0f, 0f, hr));
                case R_FLOODED -> out.add(spec("drip_emitter", cx, cz, mu, ceil - 0.05, mv, 0f, hr));
                default -> {
                }
            }
        }
        if (f != null) {
            int[] r = room(cx, cz, 0);
            int far = r[3];
            switch (f.id()) {
                case "wallpaper_door" -> out.add(spec("noclip_shimmer", cx, cz, 15.5, fy, far + 0.5, 0f, f.seed()));
                case "freight_elevator" -> {
                    out.add(spec("freight_doors", cx, cz, 15.5, fy, far - 3.0, 180f, f.seed()));
                    out.add(spec("elevator_panel", cx, cz, 13.0, fy + 1.4, far - 3.02, 180f, f.seed()));
                }
                case "loading_shutter" -> out.add(spec("roll_shutter", cx, cz, 15.5, fy, far + 0.98, 180f, f.seed()));
                case "supply_depot" -> {
                    for (int i = 0; i < 4; i++) out.add(spec("pallet_stack", cx, cz, 12.5 + i * 2.5, fy, far - 1.5, (float) (i * 17), Rng.hash(f.seed(), i)));
                }
                default -> {
                }
            }
            return;
        }
        double r = Rng.unit(Rng.hash(h, 5)) / Math.max(0.05, gs.get("decor-density", 1.0));
        if (r < 0.15) {
            out.add(spec("pallet_stack", cx, cz, hw + 1.5 + Rng.pick(h, 20), fy, hn + 0.5 - (hn == 1 ? 0 : 0.0), (float) Rng.pick(h, 40) - 20f, h));
        } else if (r < 0.22) {
            out.add(new DecorSpec("stencil_sign", cx * S + hw + 4.5, fy + 2.0, cz * S + hn + 1.0, 180f, 0f, 0f, h));
        } else if (r < 0.28) {
            out.add(new DecorSpec("fuse_box", cx * S + hw + 4.5, fy + 1.4, cz * S + hn + 1.0, 180f, 0f, 0f, h));
        } else if (r < 0.31 && hn >= 2) {
            out.add(spec("forklift", cx, cz, hw + 14.5, fy, 0.5, 90f, h));
        }
    }
}
