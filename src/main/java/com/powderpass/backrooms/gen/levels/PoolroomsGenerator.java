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
import org.bukkit.Material;

import java.util.List;

/**
 * The Poolrooms. All water is placed as still, fully contained source blocks whose surface is flush with
 * the walkways (y = floor), so nothing ever flows and every pool can be climbed out of at any edge.
 * Deep pools always have a submerged stairway. Tall tiled chambers form merged regions.
 */
public final class PoolroomsGenerator extends RoomLevel {
    private static final int FLOOR = 64;
    static final int K_WALK = 0, K_SHALLOW = 1, K_POOL = 2;

    private static final B[][] PAL = {
            // wall, wallAlt, base, floor, floorAlt, ceil, light, poolA, poolB
            {B.of(Material.WHITE_CONCRETE), B.of(Material.SMOOTH_QUARTZ), B.of(Material.LIGHT_BLUE_CONCRETE), B.of(Material.WHITE_CONCRETE), B.of(Material.SMOOTH_QUARTZ), B.of(Material.WHITE_CONCRETE), B.of(Material.SEA_LANTERN), B.of(Material.LIGHT_BLUE_CONCRETE), B.of(Material.WHITE_CONCRETE)},
            {B.of(Material.QUARTZ_BRICKS), B.of(Material.QUARTZ_BRICKS), B.of(Material.CYAN_TERRACOTTA), B.of(Material.QUARTZ_BRICKS), B.of(Material.SMOOTH_QUARTZ), B.of(Material.SMOOTH_QUARTZ), B.of(Material.SEA_LANTERN), B.of(Material.CYAN_CONCRETE), B.of(Material.QUARTZ_BRICKS)},
            {B.of(Material.CALCITE), B.of(Material.WHITE_CONCRETE), B.of(Material.LIGHT_BLUE_TERRACOTTA), B.of(Material.WHITE_CONCRETE), B.of(Material.CALCITE), B.of(Material.CALCITE), B.of(Material.PEARLESCENT_FROGLIGHT), B.of(Material.LIGHT_BLUE_CONCRETE), B.of(Material.CALCITE)},
    };
    private static final String[] NAMES = {"white-tile", "grid-tile", "calcite"};

    public PoolroomsGenerator(long seed, GenSettings gs) {
        super(Level.POOLROOMS, seed, gs, 10, 6, 3, new int[]{FLOOR}, FLOOR - 11, FLOOR + 18, 0.22, 0.0);
    }

    @Override
    protected List<FeatureDef> featureDefs() {
        return List.of(
                FeatureDef.transition("drain", Dest.to(Level.LEVEL_0), 1.0),
                FeatureDef.transition("service_door", Dest.to(Level.LEVEL_1), 1.0),
                FeatureDef.transition("sunlit_arch", Dest.EXIT, gs.get("exit-chance", 0.3)),
                new FeatureDef("slide_tower", null, gs.get("landmark-chance", 0.6), 0, "slide"),
                FeatureDef.landmark("infinite_shaft", gs.get("landmark-chance", 0.6) * 0.7),
                FeatureDef.landmark("lifeguard_post", gs.get("landmark-chance", 0.6)));
    }

    @Override
    public String biomeKey() {
        return "minecraft:warm_ocean";
    }

    @Override
    protected boolean mergedRegion(int floor, int rx, int rz) {
        return Rng.chance(Rng.hash(seed ^ 0xC4A3L, rx, rz), gs.get("chamber-chance", 0.16));
    }

    @Override
    protected double[] doorParams(int floor) {
        return new double[]{0.45, 2, 6};
    }

    private int palette(int cx, int cz) {
        return Math.min(2, (int) (zone(cx, cz, 0x7111L, 80) * 3));
    }

    public int kind(int floor, int cx, int cz) {
        if (featureAt(floor, cx, cz) != null) {
            return K_WALK;
        }
        double r = Rng.unit(ch(floor, cx, cz, 0x9001));
        double water = gs.get("water-ratio", 0.62);
        if (r < water * 0.48) return K_SHALLOW;
        if (r < water) return K_POOL;
        return K_WALK;
    }

    private int depth(int floor, int cx, int cz) {
        return 3 + Rng.pick(ch(floor, cx, cz, 0xDEE9), 4);
    }

    @Override
    protected Style style(int floor, int cx, int cz) {
        Style st = new Style();
        int p = palette(cx, cz);
        B[] pal = PAL[p];
        st.name = NAMES[p];
        st.under = pal[3];
        st.above = B.of(Material.STONE);
        st.wall = pal[0];
        st.wallAlt = pal[1];
        st.stripe = 2;
        st.base = pal[2];
        st.floor = pal[3];
        st.floorAlt = pal[4];
        st.ceil = pal[5];
        st.light = pal[6];
        st.deadLight = pal[5];
        st.lit = 0.97;
        st.lightEveryX = 5;
        st.lightEveryZ = 5;
        st.lightOffX = 2;
        st.lightOffZ = 2;
        boolean chamber = merged(floor, cx, cz);
        st.ceilY = chamber ? FLOOR + 15 : FLOOR + 7;
        st.doorH = 4;
        if (chamber) {
            st.keepCorner = true;
            st.pillar2x2 = true;
            st.lightEveryX = 5;
            st.lightEveryZ = 10;
            st.name += "/chamber";
        }
        return st;
    }

    @Override
    public String zoneName(int floor, int cx, int cz) {
        return style(floor, cx, cz).name + " kind=" + new String[]{"walk", "shallow", "pool"}[kind(floor, cx, cz)];
    }

    @Override
    public boolean isSafeCell(int floor, int cx, int cz) {
        return kind(floor, cx, cz) == K_WALK;
    }

    @Override
    protected com.powderpass.backrooms.gen.B floorBlock(CellCtx c, Style st, int u, int v) {
        return ((c.wx(u) + c.wz(v)) & 1) == 0 ? st.floor : st.floorAlt;
    }

    @Override
    protected B doorFloor(CellCtx c, Style st, int u, int v) {
        int ncx = u == 0 ? c.cx - 1 : c.cx;
        int ncz = u == 0 ? c.cz : c.cz - 1;
        if (kind(c.floor, c.cx, c.cz) == K_SHALLOW && kind(c.floor, ncx, ncz) == K_SHALLOW && !(u == 0 && v == 0)) {
            c.set(u, c.fy - 1, v, st.floor);
            return B.WATER;
        }
        return floorBlock(c, st, u, v);
    }

    /** Rotate an interior position about the cell centre. */
    private int[] rot(int u, int v, int r) {
        return switch (r & 3) {
            case 0 -> new int[]{u, v};
            case 1 -> new int[]{S - v, u};
            case 2 -> new int[]{S - u, S - v};
            default -> new int[]{v, S - u};
        };
    }

    @Override
    protected void interior(CellCtx c, Style st) {
        int k = kind(c.floor, c.cx, c.cz);
        int p = palette(c.cx, c.cz);
        if (k == K_SHALLOW) {
            for (int u = Math.max(1, c.u0); u <= c.u1; u++) {
                for (int v = Math.max(1, c.v0); v <= c.v1; v++) {
                    if (c.type(u, c.fy + 1, v) != Material.AIR) continue;
                    c.set(u, c.fy, v, B.WATER);
                    c.set(u, c.fy - 1, v, ((c.wx(u) + c.wz(v)) & 1) == 0 ? PAL[p][7] : PAL[p][8]);
                }
            }
        } else if (k == K_POOL) {
            pool(c, st, p, 2, S - 2, depth(c.floor, c.cx, c.cz), Rng.pick(ch(c.floor, c.cx, c.cz, 0x5737), 4), true);
        }
    }

    /** Square pool from lo..hi (inclusive, both axes) with optional submerged stairway on a rotated side. */
    private void pool(CellCtx c, Style st, int p, int lo, int hi, int depth, int r, boolean stairs) {
        for (int u = lo; u <= hi; u++) {
            for (int v = lo; v <= hi; v++) {
                c.set(u, c.fy - depth, v, ((u + v) % 3 == 0) ? PAL[p][7] : PAL[p][8]);
                for (int y = c.fy - depth + 1; y <= c.fy; y++) c.set(u, y, v, B.WATER);
            }
        }
        if (stairs) {
            for (int kk = 0; kk < depth - 1; kk++) {
                for (int w = 0; w < 2; w++) {
                    int[] uv = rot(lo + w, lo + kk, r);
                    for (int y = c.fy - depth + 1; y <= c.fy - 1 - kk; y++) c.set(uv[0], y, uv[1], st.floor);
                }
            }
        }
        // underwater lights in the pool walls
        int mid = (lo + hi) / 2;
        int ly = c.fy - Math.max(1, depth / 2);
        c.set(lo - 1, ly, mid, B.of(Material.SEA_LANTERN));
        c.set(hi + 1, ly, mid, B.of(Material.SEA_LANTERN));
        c.set(mid, ly, lo - 1, B.of(Material.SEA_LANTERN));
        c.set(mid, ly, hi + 1, B.of(Material.SEA_LANTERN));
    }

    @Override
    protected void feature(CellCtx c, Style st, Feature f) {
        int p = palette(c.cx, c.cz);
        switch (f.id()) {
            case "drain" -> {
                pool(c, st, p, 2, S - 2, 7, f.rot(), true);
                for (int u = 4; u <= 6; u++) {
                    for (int v = 4; v <= 6; v++) {
                        c.set(u, c.fy - 7, v, B.of(Material.BLACK_CONCRETE));
                    }
                }
                c.set(5, c.fy - 8, 5, B.of(Material.SEA_LANTERN));
            }
            case "service_door" -> booth(c, B.of(Material.GRAY_CONCRETE), B.of(Material.GRAY_CONCRETE), B.of(Material.GRAY_CONCRETE), B.of(Material.SHROOMLIGHT));
            case "sunlit_arch" -> {
                for (int u = 2; u <= 6; u++) {
                    for (int y = c.fy + 1; y < st.ceilY; y++) {
                        boolean opening = u >= 3 && u <= 5 && y <= c.fy + 4;
                        c.set(u, y, 4, opening ? B.AIR : st.wall);
                    }
                    c.set(u, c.fy, 4, B.of(Material.GLOWSTONE));
                }
                c.set(4, c.fy + 5, 4, B.of(Material.GLOWSTONE));
                for (int u = 3; u <= 5; u++) {
                    c.set(u, st.ceilY, 5, B.of(Material.GLOWSTONE));
                    c.set(u, st.ceilY, 3, B.of(Material.GLOWSTONE));
                }
            }
            case "slide_tower" -> {
                for (int kk = 1; kk <= 4; kk++) {
                    for (int u = 2; u <= 3; u++) {
                        for (int y = c.fy + 1; y < c.fy + kk; y++) c.set(u, y, kk, st.wall);
                        c.set(u, c.fy + kk, kk, B.stairs(Material.QUARTZ_STAIRS, "south", false));
                    }
                }
                for (int u = 2; u <= 3; u++) {
                    for (int v = 5; v <= 6; v++) {
                        for (int y = c.fy + 1; y <= c.fy + 4; y++) c.set(u, y, v, y == c.fy + 4 ? B.of(Material.SMOOTH_QUARTZ) : st.wall);
                    }
                }
                pool(c, st, p, 5, 8, 4, 0, false);
                for (int kk = 0; kk < 3; kk++) {
                    for (int y = c.fy - 3; y <= c.fy - 1 - kk; y++) c.set(5 + kk, y, 8, st.floor);
                }
            }
            case "infinite_shaft" -> {
                pool(c, st, p, 2, S - 2, 3, f.rot(), true);
                for (int u = 4; u <= 6; u++) {
                    for (int v = 4; v <= 6; v++) {
                        for (int y = bottomY + 1; y <= c.fy - 3; y++) c.set(u, y, v, B.WATER);
                    }
                }
                c.set(5, bottomY + 1, 5, B.of(Material.SEA_LANTERN));
                for (int u = 3; u <= 7; u++) {
                    for (int v = 3; v <= 7; v++) {
                        if (u == 3 || u == 7 || v == 3 || v == 7) {
                            for (int y = bottomY + 1; y <= c.fy - 4; y++) c.set(u, y, v, B.of(Material.CYAN_CONCRETE));
                        }
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
            case "drain" -> new int[]{4, -8, 4, 6, -5, 6};
            case "service_door" -> boothTrigger();
            case "sunlit_arch" -> new int[]{3, 0, 4, 5, 2, 4};
            case "slide_tower" -> new int[]{3, 4, 5, 3, 5, 6};
            default -> null;
        };
    }

    @Override
    protected void cellDecor(int floor, int cx, int cz, List<DecorSpec> out) {
        Feature f = featureAt(floor, cx, cz);
        Style st = style(floor, cx, cz);
        int fy = floorY[floor] + 1;
        long h = ch(floor, cx, cz, 0xDEC1);
        if (f != null) {
            switch (f.id()) {
                case "drain" -> {
                    out.add(spec("drain_vortex", cx, cz, 5.5, fy - 7.95, 5.5, 0f, f.seed()));
                    out.add(spec("caustics", cx, cz, 5.0, st.ceilY - 0.02, 5.0, 0f, f.seed()));
                }
                case "service_door" -> out.add(spec("staff_sign", cx, cz, 4.5, fy + 3.3, 6.02, 0f, f.seed()));
                case "sunlit_arch" -> out.add(spec("sunbeam", cx, cz, 4.5, fy, 4.5, 0f, f.seed()));
                case "slide_tower" -> out.add(spec("water_slide", cx, cz, 4.0, fy + 4, 5.5, -90f, f.seed()));
                case "infinite_shaft" -> out.add(spec("caustics", cx, cz, 5.0, st.ceilY - 0.02, 5.0, 0f, f.seed()));
                case "lifeguard_post" -> out.add(spec("lifeguard_chair", cx, cz, 5.5, fy, 5.5, (float) (f.rot() * 90), f.seed())
                        .barrier(cx * S + 5, fy, cz * S + 5));
                default -> {
                }
            }
            return;
        }
        int k = kind(floor, cx, cz);
        double density = gs.get("decor-density", 1.0);
        double r = Rng.unit(h) / Math.max(0.05, density);
        if ((k == K_POOL || k == K_SHALLOW) && Rng.chance(Rng.hash(h, 1), 0.55 * density)) {
            out.add(spec("caustics", cx, cz, 5.0, st.ceilY - 0.02, 5.0, (float) Rng.pick(h, 4) * 90f, h));
        }
        if (k == K_POOL) {
            int r4 = Rng.pick(ch(floor, cx, cz, 0x5737), 4);
            int[] uv = rot(2, 2, r4);
            if (r < 0.35) {
                out.add(spec("pool_railing", cx, cz, uv[0] + 0.5, fy, uv[1] + 0.5, (float) (-r4 * 90), h));
            } else if (r < 0.55) {
                out.add(spec("floating_chair_pool", cx, cz, 5.5, fy - 0.35, 5.5, (float) Rng.pick(h, 360), h));
            } else if (r < 0.65) {
                out.add(spec("submerged_bench", cx, cz, 5.5, fy - depth(floor, cx, cz) + 0.0, 5.5, (float) Rng.pick(h, 360), h));
            }
        } else if (k == K_SHALLOW) {
            if (r < 0.25) {
                out.add(spec("submerged_bench", cx, cz, 3.5 + Rng.pick(h, 4), fy - 1.0, 5.5, (float) Rng.pick(h, 4) * 90f, h));
            } else if (r < 0.35) {
                out.add(spec("drip_emitter", cx, cz, 5.5, st.ceilY - 0.05, 5.5, 0f, h));
            }
        } else {
            if (r < 0.12) {
                out.add(spec("drain_grate", cx, cz, 5.0, fy + 0.001, 5.0, 0f, h));
            } else if (r < 0.2) {
                out.add(spec("pool_bench", cx, cz, 2.5, fy, 5.5, 90f, h).barrier(cx * S + 2, fy, cz * S + 5));
            } else if (r < 0.26 && closed(floor, cx, cz, 1)) {
                out.add(spec("lifebuoy", cx, cz, 5.5, fy + 1.8, 1.0, 0f, h));
            } else if (r < 0.31) {
                out.add(new DecorSpec("impossible_stairs", cx * S + 7.5, fy + 2.0, cz * S + 7.5, 135f, 0f, 0f, h));
            }
        }
        if (merged(floor, cx, cz) && cornerOpen(floor, cx, cz) && Rng.chance(Rng.hash(h, 9), 0.5)) {
            out.add(spec("curved_tile", cx, cz, 2.0, fy, 2.0, 0f, h));
        }
    }
}
