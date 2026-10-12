package com.powderpass.backrooms.gen.levels;

import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.gen.B;
import com.powderpass.backrooms.gen.CellCtx;
import com.powderpass.backrooms.gen.DecorSpec;
import com.powderpass.backrooms.gen.Dest;
import com.powderpass.backrooms.gen.Feature;
import com.powderpass.backrooms.gen.FeatureDef;
import com.powderpass.backrooms.gen.GenSettings;
import com.powderpass.backrooms.gen.RoomLevel;
import com.powderpass.backrooms.gen.Style;
import com.powderpass.backrooms.util.Rng;
import com.powderpass.backrooms.util.ValueNoise;
import org.bukkit.Material;

import java.util.List;

/** Level 1 — Habitable Zone: immense concrete corridors, warehouses, caches, safe rooms, loading docks. */
public final class Level1Generator extends RoomLevel {
    private static final int FLOOR = 64;
    static final int K_PLAIN = 0, K_STORAGE = 1, K_CACHE = 2, K_SAFE = 3, K_DOCK = 4, K_WAREHOUSE = 5;
    private static final String[] KINDS = {"corridor", "storage", "cache", "safe-room", "dock", "warehouse"};
    private static final B CONC = B.of(Material.LIGHT_GRAY_CONCRETE);
    private static final B GRAY = B.of(Material.GRAY_CONCRETE);
    private static final B[][] PAL = {
            {B.of(Material.LIGHT_GRAY_CONCRETE), B.of(Material.GRAY_CONCRETE), GRAY, B.of(Material.SMOOTH_STONE)},
            {B.of(Material.POLISHED_ANDESITE), B.of(Material.ANDESITE), B.of(Material.STONE_BRICKS), B.of(Material.STONE)},
            {B.of(Material.LIGHT_GRAY_CONCRETE), B.of(Material.LIGHT_GRAY_CONCRETE), B.of(Material.CYAN_TERRACOTTA), B.of(Material.SMOOTH_STONE)},
    };
    private static final String[] NAMES = {"bare-concrete", "cinderblock", "painted-institutional"};

    public Level1Generator(long seed, GenSettings gs) {
        super(Level.LEVEL_1, seed, gs, 12, 6, 3, new int[]{FLOOR}, FLOOR - 8, FLOOR + 14, 0.2, 0.0);
    }

    @Override
    protected List<FeatureDef> featureDefs() {
        return List.of(
                FeatureDef.transition("maintenance_stairs", Dest.to(Level.LEVEL_2), 1.0),
                FeatureDef.transition("wallpaper_door", Dest.to(Level.LEVEL_0), 1.0),
                FeatureDef.transition("freight_elevator", Dest.to(Level.LEVEL_4), gs.get("elevator-chance", 0.7)).withEffect("dwell"),
                FeatureDef.landmark("supply_depot", 1.0),
                FeatureDef.landmark("safe_haven", gs.get("landmark-chance", 0.8)),
                FeatureDef.landmark("flooded_room", gs.get("landmark-chance", 0.8) * 0.6),
                FeatureDef.landmark("forklift_bay", gs.get("landmark-chance", 0.8) * 0.6));
    }

    @Override
    public String biomeKey() {
        return "minecraft:plains";
    }

    @Override
    protected boolean mergedRegion(int floor, int rx, int rz) {
        return Rng.chance(Rng.hash(seed ^ 0x3A4EL, rx, rz), gs.get("warehouse-chance", 0.14));
    }

    @Override
    protected double[] doorParams(int floor) {
        return new double[]{0.35, 2, 5};
    }

    private int palette(int cx, int cz) {
        return Math.min(2, (int) (zone(cx, cz, 0x1EE1L, 90) * 3));
    }

    private boolean dark(int cx, int cz) {
        return zone(cx, cz, 0xDA41L, 110) > 1.0 - gs.get("dark-zone-chance", 0.25) * 0.6;
    }

    public int kind(int floor, int cx, int cz) {
        if (merged(floor, cx, cz)) {
            return K_WAREHOUSE;
        }
        Feature f = featureAt(floor, cx, cz);
        if (f != null) {
            return f.id().equals("safe_haven") ? K_SAFE : f.id().equals("supply_depot") ? K_CACHE : K_PLAIN;
        }
        double r = Rng.unit(ch(floor, cx, cz, 0x11D));
        if (r < 0.12) return K_STORAGE;
        if (r < 0.12 + gs.get("cache-chance", 0.05)) return K_CACHE;
        if (r < 0.21) return K_SAFE;
        if (r < 0.27) return K_DOCK;
        return K_PLAIN;
    }

    @Override
    public boolean isSafeZone(int x, int y, int z) {
        return kind(0, cellOf(x), cellOf(z)) == K_SAFE;
    }

    @Override
    public int lootTier(int x, int y, int z) {
        int k = kind(0, cellOf(x), cellOf(z));
        return k == K_CACHE ? 2 : k == K_WAREHOUSE ? 0 : 1;
    }

    @Override
    protected Style style(int floor, int cx, int cz) {
        Style st = new Style();
        int p = palette(cx, cz);
        st.name = NAMES[p];
        st.under = B.of(Material.STONE);
        st.above = B.of(Material.STONE);
        st.wall = PAL[p][0];
        st.wallAlt = PAL[p][1];
        st.stripe = p == 1 ? 2 : 0;
        st.base = PAL[p][2];
        st.floor = PAL[p][3];
        st.floorAlt = GRAY;
        st.floorAltChance = 0.08;
        st.ceil = GRAY;
        st.light = B.of(Material.PEARLESCENT_FROGLIGHT);
        st.deadLight = B.of(Material.LIGHT_GRAY_CONCRETE);
        st.lightEveryX = 6;
        st.lightEveryZ = 6;
        st.lightOffX = 3;
        st.lightOffZ = 3;
        st.lightLength = 2;
        st.lit = dark(cx, cz) ? 0.08 : 0.72;
        st.wallStain = 0.03;
        st.wallStainBlock = B.of(Material.GRAY_CONCRETE);
        st.ceilY = FLOOR + 7;
        st.doorH = 4;
        int k = kind(floor, cx, cz);
        if (k == K_WAREHOUSE) {
            st.ceilY = FLOOR + 10;
            st.keepCorner = true;
            st.lightEveryZ = 12;
            st.lightOffZ = 6;
            st.name = "warehouse";
        } else if (k == K_SAFE) {
            st.floor = B.of(Material.SPRUCE_PLANKS);
            st.floorAlt = B.of(Material.SPRUCE_PLANKS);
            st.light = B.of(Material.SHROOMLIGHT);
            st.lit = 1.0;
            st.lightEveryX = 4;
            st.lightEveryZ = 4;
            st.lightLength = 1;
        }
        if (dark(cx, cz)) {
            st.name += "/dark";
        }
        return st;
    }

    @Override
    protected B floorBlock(CellCtx c, Style st, int u, int v) {
        int mid = S / 2;
        int k = kind(c.floor, c.cx, c.cz);
        if (k != K_SAFE && (u == mid || v == mid) && Math.floorMod(c.wx(u) + c.wz(v), 3) != 0 && u > 0 && v > 0) {
            return B.of(Material.YELLOW_CONCRETE);
        }
        return super.floorBlock(c, st, u, v);
    }

    @Override
    public String zoneName(int floor, int cx, int cz) {
        return style(floor, cx, cz).name + " kind=" + KINDS[kind(floor, cx, cz)];
    }

    @Override
    public boolean isSafeCell(int floor, int cx, int cz) {
        int k = kind(floor, cx, cz);
        return k != K_WAREHOUSE;
    }

    @Override
    protected void interior(CellCtx c, Style st) {
        int k = kind(c.floor, c.cx, c.cz);
        long h = ch(c.floor, c.cx, c.cz, 0x77);
        switch (k) {
            case K_WAREHOUSE -> racks(c, st, h);
            case K_STORAGE -> alongClosed(c, h, 0.7, new B[]{B.of(Material.BARREL, "[facing=up]"), B.of(Material.STRIPPED_SPRUCE_WOOD), B.of(Material.COMPOSTER), B.of(Material.HAY_BLOCK)}, 2);
            case K_CACHE -> alongClosed(c, h, 0.5, new B[]{B.of(Material.BARREL, "[facing=up]")}, 1);
            case K_DOCK -> dock(c, st, h);
            default -> {
            }
        }
        if (k != K_SAFE) {
            puddles(c, 0.8);
        }
    }

    private void puddles(CellCtx c, double threshold) {
        for (int u = Math.max(1, c.u0); u <= c.u1; u++) {
            for (int v = Math.max(1, c.v0); v <= c.v1; v++) {
                if (Math.abs(u - S / 2) <= 1 && Math.abs(v - S / 2) <= 1) continue;
                double n = ValueNoise.sample(seed ^ 0x9E, c.wx(u) / 4.0, c.wz(v) / 4.0);
                if (n > threshold && c.type(u, c.fy + 1, v) == Material.AIR) {
                    c.set(u, c.fy, v, B.WATER);
                    c.set(u, c.fy - 1, v, B.of(Material.GRAY_CONCRETE));
                }
            }
        }
    }

    /** Stack props against closed walls (first interior row), never in front of the centre. */
    private void alongClosed(CellCtx c, long h, double fill, B[] blocks, int maxH) {
        for (int side = 0; side < 4; side++) {
            if (!closed(c.floor, c.cx, c.cz, side)) continue;
            for (int a = 2; a <= S - 2; a++) {
                long hh = Rng.hash(h, side, a);
                if (!Rng.chance(hh, fill)) continue;
                int[] uv = sideToUV(side, a, 1);
                int height = 1 + Rng.pick(Rng.hash(hh, 1), maxH);
                for (int y = 0; y < height; y++) {
                    c.set(uv[0], c.fy + 1 + y, uv[1], blocks[Rng.pick(Rng.hash(hh, 2 + y), blocks.length)]);
                }
            }
        }
    }

    private void racks(CellCtx c, Style st, long h) {
        B post = B.of(Material.IRON_BARS);
        B shelf = B.slab(Material.SPRUCE_SLAB, true);
        B[] goods = {B.of(Material.STRIPPED_SPRUCE_WOOD), B.of(Material.HAY_BLOCK), B.of(Material.BARREL, "[facing=up]"), B.of(Material.COMPOSTER), B.AIR, B.of(Material.MUSHROOM_STEM)};
        for (int row : new int[]{3, 8}) {
            for (int v = row; v <= row + 1; v++) {
                for (int u = 2; u <= S - 3; u++) {
                    boolean postCol = (u - 2) % 4 == 0 || u == S - 3;
                    for (int y = 1; y <= 4; y++) {
                        B b;
                        if (postCol) b = post;
                        else if (y == 2 || y == 4) b = shelf;
                        else b = goods[Rng.pick(c.h(u, y, v, 0x6005), goods.length)];
                        c.set(u, c.fy + y, v, b);
                    }
                }
            }
        }
    }

    private void dock(CellCtx c, Style st, long h) {
        for (int side = 0; side < 4; side++) {
            if (!closed(c.floor, c.cx, c.cz, side) || !thickWallFits(c.floor, c.cx, c.cz, side, 3)) continue;
            for (int a = 1; a < S; a++) {
                for (int d = 1; d <= 3; d++) {
                    int[] uv = sideToUV(side, a, d);
                    c.set(uv[0], c.fy + 1, uv[1], d == 3 ? B.slab(Material.SMOOTH_STONE_SLAB, false) : B.of(Material.SMOOTH_STONE));
                }
                int[] edge = sideToUV(side, a, 3);
                if (a % 3 == 0) c.set(edge[0], c.fy + 1, edge[1], B.of(Material.YELLOW_CONCRETE));
            }
            return;
        }
    }

    @Override
    protected void feature(CellCtx c, Style st, Feature f) {
        switch (f.id()) {
            case "maintenance_stairs" -> stairPit(c, st, B.of(Material.POLISHED_DEEPSLATE), B.of(Material.DEEPSLATE_TILES), Material.DEEPSLATE_TILE_STAIRS, B.of(Material.SHROOMLIGHT));
            case "wallpaper_door" -> {
                for (int u = 4; u <= 8; u++) {
                    for (int y = c.fy + 1; y <= c.fy + 4; y++) {
                        boolean opening = u == 6 && y <= c.fy + 2;
                        c.set(u, y, 6, opening ? B.AIR : (y == c.fy + 1 ? B.of(Material.BIRCH_PLANKS) : B.of(Material.SMOOTH_SANDSTONE)));
                    }
                    c.set(u, c.fy, 6, B.of(Material.YELLOW_TERRACOTTA));
                }
                c.set(6, c.fy + 3, 6, B.of(Material.OCHRE_FROGLIGHT));
            }
            case "freight_elevator" -> {
                for (int u = 4; u <= 8; u++) for (int v = 4; v <= 8; v++) c.set(u, c.fy, v, B.of(Material.POLISHED_ANDESITE));
                c.fill(4, c.fy + 1, 4, 8, c.fy + 5, 4, B.of(Material.IRON_BLOCK));
                c.fill(4, c.fy + 1, 4, 4, c.fy + 5, 7, B.of(Material.IRON_BLOCK));
                c.fill(8, c.fy + 1, 4, 8, c.fy + 5, 7, B.of(Material.IRON_BLOCK));
                c.fill(4, c.fy + 5, 4, 8, c.fy + 5, 7, B.of(Material.IRON_BLOCK));
                c.set(6, c.fy + 5, 6, B.of(Material.SEA_LANTERN));
            }
            case "supply_depot" -> {
                alongClosed(c, f.seed(), 0.35, new B[]{B.of(Material.BARREL, "[facing=up]")}, 1);
                for (int u = 4; u <= 7; u++) c.set(u, c.fy + 1, 3, B.of(Material.BARREL, "[facing=up]"));
            }
            case "safe_haven" -> {
                for (int u = Math.max(1, c.u0); u <= c.u1; u++) {
                    for (int v = Math.max(1, c.v0); v <= c.v1; v++) {
                        c.set(u, c.fy, v, ((u + v) & 1) == 0 ? B.of(Material.SPRUCE_PLANKS) : B.of(Material.DARK_OAK_PLANKS));
                    }
                }
                c.set(2, c.fy + 1, 2, B.of(Material.BARREL, "[facing=up]"));
            }
            case "flooded_room" -> {
                for (int u = Math.max(1, c.u0); u <= c.u1; u++) {
                    for (int v = Math.max(1, c.v0); v <= c.v1; v++) {
                        c.set(u, c.fy, v, B.WATER);
                        c.set(u, c.fy - 1, v, B.of(Material.GRAY_CONCRETE));
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
            case "maintenance_stairs" -> stairPitTrigger();
            case "wallpaper_door" -> new int[]{6, 0, 6, 6, 1, 6};
            case "freight_elevator" -> new int[]{5, 0, 5, 7, 1, 7};
            default -> null;
        };
    }

    @Override
    protected void cellDecor(int floor, int cx, int cz, List<DecorSpec> out) {
        Feature f = featureAt(floor, cx, cz);
        Style st = style(floor, cx, cz);
        int fy = floorY[floor] + 1;
        long h = ch(floor, cx, cz, 0xDEC2);
        if (f != null) {
            switch (f.id()) {
                case "maintenance_stairs" -> out.add(spec("maintenance_sign", cx, cz, 4.5, fy + 1.2, 1.2, 0f, f.seed()));
                case "wallpaper_door" -> out.add(spec("noclip_shimmer", cx, cz, 6.5, fy, 6.5, 0f, f.seed()));
                case "freight_elevator" -> {
                    out.add(spec("freight_doors", cx, cz, 6.5, fy, 8.0, 0f, f.seed()));
                    out.add(spec("elevator_panel", cx, cz, 5.02, fy + 1.4, 7.0, -90f, f.seed()));
                }
                case "supply_depot" -> out.add(spec("supply_sign", cx, cz, 6.0, fy + 2.8, 1.02, 0f, f.seed()));
                case "safe_haven" -> {
                    out.add(spec("bench", cx, cz, 6.0, fy, 9.5, 180f, f.seed()).barrier(cx * S + 5, fy, cz * S + 9, cx * S + 6, fy, cz * S + 9));
                    out.add(spec("radio", cx, cz, 2.5, fy + 1.0, 2.5, 30f, f.seed()));
                    out.add(spec("chalk_safe", cx, cz, 6.0, fy + 1.5, 1.02, 0f, f.seed()));
                }
                case "flooded_room" -> out.add(spec("drip_emitter", cx, cz, 6.5, st.ceilY - 0.05, 6.5, 0f, f.seed()));
                case "forklift_bay" -> out.add(spec("forklift", cx, cz, 6.5, fy, 6.5, (float) (f.rot() * 90 + 20), f.seed())
                        .barrier(cx * S + 6, fy, cz * S + 6, cx * S + 6, fy, cz * S + 7));
                default -> {
                }
            }
            return;
        }
        int k = kind(floor, cx, cz);
        double density = gs.get("decor-density", 1.0);
        double r = Rng.unit(h) / Math.max(0.05, density);
        switch (k) {
            case K_SAFE -> {
                out.add(spec("bench", cx, cz, 6.0, fy, 9.5, 180f, h).barrier(cx * S + 5, fy, cz * S + 9, cx * S + 6, fy, cz * S + 9));
                out.add(spec("chalk_safe", cx, cz, 6.0, fy + 1.5, 1.02, 0f, h));
            }
            case K_CACHE -> out.add(spec("supply_sign", cx, cz, 6.0, fy + 2.8, 1.02, 0f, h));
            case K_DOCK -> {
                if (r < 0.6) out.add(spec("cage_lamp", cx, cz, 6.5, st.ceilY, 6.5, 0f, h).light(cx * S + 6, st.ceilY - 2, cz * S + 6, 12));
            }
            case K_WAREHOUSE -> {
                if (r < 0.25) out.add(spec("cage_lamp", cx, cz, 6.5, st.ceilY, 6.5, 0f, h).light(cx * S + 6, st.ceilY - 2, cz * S + 6, 13));
                else if (r < 0.35) out.add(spec("ceiling_fan", cx, cz, 6.5, st.ceilY, 6.5, 0f, h));
            }
            default -> {
                if (r < 0.2) {
                    boolean alongX = Rng.chance(Rng.hash(h, 3), 0.5);
                    out.add(spec("ceiling_pipes", cx, cz, alongX ? 0.0 : 3.5, st.ceilY - 0.5, alongX ? 3.5 : 0.0, alongX ? -90f : 0f, h));
                } else if (r < 0.3) {
                    out.add(spec("cage_lamp", cx, cz, 6.5, st.ceilY, 6.5, 0f, h).light(cx * S + 6, st.ceilY - 2, cz * S + 6, 12));
                } else if (r < 0.36) {
                    out.add(spec("ceiling_fan", cx, cz, 6.5, st.ceilY, 6.5, 0f, h));
                } else if (r < 0.42 && closed(floor, cx, cz, 1)) {
                    out.add(spec("fuse_box", cx, cz, 3.5, fy + 1.4, 1.0, 0f, h));
                } else if (r < 0.46 && closed(floor, cx, cz, 0)) {
                    out.add(spec("stencil_sign", cx, cz, 1.0, fy + 2.2, 6.0, -90f, h));
                } else if (r < 0.52) {
                    out.add(spec("pallet_stack", cx, cz, 9.5, fy, 2.5, (float) Rng.pick(h, 30) - 15f, h).barrier(cx * S + 9, fy, cz * S + 2));
                }
            }
        }
    }
}
