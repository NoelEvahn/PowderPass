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
 * The Plains. A perfectly flat field under a sky stuck at sunset. Straight dirt roads run to the horizon with
 * power poles beside them; houses line the roads, every house on a road identical to the next and all of them
 * wrong in the same way. Far out in the fields stand colossal objects (display models): a chair the size of a
 * building, a doorframe, a staircase into the sky, a figure on the horizon.
 */
public final class PlainsGenerator extends LevelGenerator {
    private static final int FLOOR = 64;
    static final int K_FIELD = 0, K_HOUSE = 1, K_COLOSSAL = 2, K_FEATURE = 3;
    static final int V_NORMAL = 0, V_NO_DOOR = 1, V_HIGH_DOOR = 2, V_SUNK = 3, V_FLOATING = 4, V_MONOLITH = 5, V_DOOR_TO_WALL = 6;
    private static final String[] VARIANTS = {"house", "doorless", "high-door", "sunken", "floating", "monolith", "door-to-wall"};
    static final String[] COLOSSI = {"giant_chair", "giant_stairs", "giant_outlet", "giant_figure", "floating_panel", "lone_wall", "giant_tv", "giant_chair"};

    /** Yaw facing the house front (out towards the road) / the back wall, per orientation. */
    private static final float[] FRONT_YAW = {180f, 90f, 0f, -90f};
    private static final float[] BACK_YAW = {0f, -90f, 180f, 90f};

    private static final B GRASS = B.of(Material.GRASS_BLOCK);
    private static final B DIRT = B.of(Material.DIRT);
    private static final B STONE = B.of(Material.STONE);
    private static final B PATH = B.of(Material.DIRT_PATH);
    private static final B MOSS = B.of(Material.MOSS_BLOCK);
    private static final B COARSE = B.of(Material.COARSE_DIRT);
    private static final B HOUSE_WALL = B.of(Material.WHITE_CONCRETE);
    private static final B HOUSE_FLOOR = B.of(Material.SPRUCE_PLANKS);
    private static final B WINDOW = B.of(Material.TINTED_GLASS);
    private static final B ROOF = B.of(Material.DEEPSLATE_TILES);
    private static final B CONCRETE = B.of(Material.GRAY_CONCRETE);
    private static final B TILE = B.of(Material.WHITE_CONCRETE);

    public PlainsGenerator(long seed, GenSettings gs) {
        super(Level.PLAINS, seed, gs, 32, 4, 3, new int[]{FLOOR}, FLOOR - 20, FLOOR + 40, 0.0, 0.0);
    }

    @Override
    protected List<FeatureDef> featureDefs() {
        return List.of(
                // the end of the chain: the only exit there is, in every district; a hatch back down to the Office
                FeatureDef.transition("giant_doorframe", Dest.EXIT, 1.0),
                FeatureDef.transition("bunker_hatch", Dest.to(Level.LEVEL_4), gs.get("back-chance", 0.4)),
                // still there, still wrong, but they lead nowhere now
                FeatureDef.landmark("yellow_house", gs.get("landmark-chance", 0.6)),
                FeatureDef.landmark("backyard_pool", gs.get("landmark-chance", 0.6)));
    }

    @Override
    public String biomeKey() {
        return "minecraft:pale_garden";
    }

    // ------------------------------------------------------------------ layout

    /** A road runs along this line (axis 0: along X at z=line*S; axis 1: along Z at x=line*S). */
    boolean road(int axis, int line) {
        return Rng.chance(Rng.hash(seed ^ 0x40ADL, axis, line), gs.get("road-chance", 0.22));
    }

    private boolean colossusCell(int cx, int cz) {
        int rx = Math.floorDiv(cx, R), rz = Math.floorDiv(cz, R);
        long h = Rng.hash(seed ^ 0xC0105L, rx, rz);
        if (!Rng.chance(h, gs.get("colossus-chance", 0.6))) return false;
        return cx == rx * R + 1 + Rng.pick(Rng.hash(h, 1), R - 2) && cz == rz * R + 1 + Rng.pick(Rng.hash(h, 2), R - 2);
    }

    public int kind(int cx, int cz) {
        if (featureAt(0, cx, cz) != null) return K_FEATURE;
        if (colossusCell(cx, cz)) return K_COLOSSAL;
        boolean byRoad = road(0, cz) || road(1, cx);
        double p = byRoad ? gs.get("roadside-house-chance", 0.5) : gs.get("house-chance", 0.06);
        return Rng.chance(ch(0, cx, cz, 0x4005E), p) ? K_HOUSE : K_FIELD;
    }

    /** Orientation the house front faces: 0 north (toward a north road), 1 west, 2 south, 3 east. */
    int orientation(int cx, int cz) {
        if (road(0, cz)) return 0;
        if (road(1, cx)) return 1;
        return Rng.pick(ch(0, cx, cz, 0x0E1), 4);
    }

    /** Every house along one road is the same house. */
    int variant(int cx, int cz) {
        long h = road(0, cz) ? Rng.hash(seed ^ 0x5A3EL, 0, cz) : road(1, cx) ? Rng.hash(seed ^ 0x5A3EL, 1, cx) : ch(0, cx, cz, 0x5A3E);
        return Rng.pick(h, VARIANTS.length);
    }

    // house frame: local hx 0..8 (across), hz 0..6 (front to back), front at hz = 0
    private static int[] toUV(int o, int hx, int hz) {
        return switch (o) {
            case 0 -> new int[]{12 + hx, 7 + hz};
            case 1 -> new int[]{7 + hz, 12 + (8 - hx)};
            case 2 -> new int[]{12 + (8 - hx), 24 - hz};
            default -> new int[]{24 - hz, 12 + hx};
        };
    }

    /** World direction names of local +hz (towards the back) and -hz (front). */
    private static String back(int o) {
        return switch (o) {
            case 0 -> "south";
            case 1 -> "east";
            case 2 -> "north";
            default -> "west";
        };
    }

    private static String front(int o) {
        return switch (o) {
            case 0 -> "north";
            case 1 -> "west";
            case 2 -> "south";
            default -> "east";
        };
    }

    // ------------------------------------------------------------------ painting

    @Override
    protected void paintCell(CellCtx c) {
        boolean roadN = road(0, c.cz), roadW = road(1, c.cx);
        for (int u = c.u0; u <= c.u1; u++) {
            for (int v = c.v0; v <= c.v1; v++) {
                c.set(u, bottomY, v, B.BEDROCK);
                for (int y = bottomY + 1; y < c.fy - 3; y++) c.set(u, y, v, STONE);
                for (int y = c.fy - 3; y < c.fy; y++) c.set(u, y, v, DIRT);
                boolean onRoad = (roadN && v <= 2) || (roadW && u <= 2);
                c.set(u, c.fy, v, onRoad ? PATH : ground(c.wx(u), c.wz(v)));
                if (!onRoad) vegetation(c, u, v);
            }
        }
        int k = kind(c.cx, c.cz);
        if (k == K_FIELD && !roadN && !roadW) hollow(c);
        if (k == K_HOUSE) {
            house(c, orientation(c.cx, c.cz), variant(c.cx, c.cz), false);
        } else if (k == K_FEATURE) {
            Feature f = featureAt(0, c.cx, c.cz);
            switch (f.id()) {
                case "yellow_house" -> house(c, 0, V_NORMAL, true);
                case "backyard_pool" -> pool(c);
                case "bunker_hatch" -> bunker(c);
                default -> {
                }
            }
        }
    }

    /**
     * Ground cover: about 90% grass, 7% moss, 3% coarse dirt, always in smooth correlated patches (the thresholds
     * are the noise's 93rd and 97th percentiles), never salt-and-pepper.
     */
    B ground(int wx, int wz) {
        if (ValueNoise.sample(seed ^ 0x3055L, wx / 14.0, wz / 14.0) > 0.822) return MOSS;
        if (ValueNoise.sample(seed ^ 0xC0A5L, wx / 11.0, wz / 11.0) > 0.8825) return COARSE;
        return GRASS;
    }

    /** Sparse short grass in loose drifts, a rare tall clump; most of the field is bare. */
    private void vegetation(CellCtx c, int u, int v) {
        int wx = c.wx(u), wz = c.wz(v);
        if (ValueNoise.sample(seed ^ 0x6A55L, wx / 5.0, wz / 5.0) < 0.86) return;
        long h = c.h(u, 0, v, 0x6A5);
        if (Rng.chance(h, 0.06)) {
            c.set(u, c.fy + 1, v, B.of(Material.TALL_GRASS, "[half=lower]"));
            c.set(u, c.fy + 2, v, B.of(Material.TALL_GRASS, "[half=upper]"));
        } else if (Rng.chance(Rng.hash(h, 1), 0.5)) {
            c.set(u, c.fy + 1, v, B.of(Material.SHORT_GRASS));
        }
    }

    /** A rare, very shallow hollow in an open field: one block down, soft-edged. */
    private void hollow(CellCtx c) {
        if (zone(c.cx, c.cz, 0x401L, 96) < 0.86) return;
        for (int u = Math.max(c.u0, 5); u <= Math.min(c.u1, S - 6); u++) {
            for (int v = Math.max(c.v0, 5); v <= Math.min(c.v1, S - 6); v++) {
                double d = Math.hypot(u - S / 2.0, v - S / 2.0) / 11.0;
                double n = ValueNoise.sample(seed ^ 0x4011L, c.wx(u) / 6.0, c.wz(v) / 6.0) * 0.5;
                if (d + n * 0.6 > 0.95) continue;
                Material top = c.type(u, c.fy, v);
                B g = top == Material.MOSS_BLOCK ? MOSS : top == Material.COARSE_DIRT ? COARSE : GRASS;
                c.set(u, c.fy - 1, v, g);
                c.set(u, c.fy, v, B.AIR);
                c.set(u, c.fy + 1, v, B.AIR);
                c.set(u, c.fy + 2, v, B.AIR);
            }
        }
    }

    private void house(CellCtx c, int o, int variant, boolean yellow) {
        int y0 = c.fy + (variant == V_SUNK ? -2 : variant == V_FLOATING ? 5 : 0);
        for (int hx = 0; hx <= 8; hx++) {
            for (int hz = 0; hz <= 6; hz++) {
                int[] uv = toUV(o, hx, hz);
                boolean wall = hx == 0 || hx == 8 || hz == 0 || hz == 6;
                c.set(uv[0], y0, uv[1], yellow ? B.of(Material.YELLOW_TERRACOTTA) : HOUSE_FLOOR);
                for (int hy = 1; hy <= 4; hy++) {
                    B b = B.AIR;
                    if (wall) {
                        b = HOUSE_WALL;
                        boolean windowSpot = (hy == 2 || hy == 3) && ((hz == 0 || hz == 6) && (hx == 2 || hx == 6) || (hx == 0 || hx == 8) && hz == 3);
                        if (windowSpot && variant != V_MONOLITH) b = variant == V_NO_DOOR && hz == 0 ? HOUSE_WALL : WINDOW;
                    } else if (yellow && (hx == 1 || hx == 7 || hz == 1 || hz == 5)) {
                        b = hy == 4 ? B.AIR : B.of(Material.SMOOTH_SANDSTONE);
                        if (hz == 1 && hx == 4 && hy <= 2) b = B.AIR;
                    }
                    c.set(uv[0], y0 + hy, uv[1], b);
                }
                // flat ceiling, then a gabled roof (ridge along hx)
                c.set(uv[0], y0 + 5, uv[1], ROOF);
            }
        }
        for (int k = 0; k <= 3; k++) {
            for (int hx = -1; hx <= 9; hx++) {
                int[] f = toUV(o, hx, -1 + k), b = toUV(o, hx, 7 - k);
                c.set(f[0], y0 + 5 + k, f[1], B.stairs(Material.DEEPSLATE_TILE_STAIRS, back(o), false));
                c.set(b[0], y0 + 5 + k, b[1], B.stairs(Material.DEEPSLATE_TILE_STAIRS, front(o), false));
                if (k > 0 && (hx == 0 || hx == 8)) {
                    for (int hz = k; hz <= 6 - k; hz++) {
                        int[] g = toUV(o, hx, hz);
                        c.set(g[0], y0 + 5 + k, g[1], HOUSE_WALL);
                    }
                }
            }
        }
        for (int hx = -1; hx <= 9; hx++) {
            int[] r = toUV(o, hx, 3);
            c.set(r[0], y0 + 9, r[1], B.slab(Material.DEEPSLATE_TILE_SLAB, false));
        }
        // the door, or what passes for one
        int[] d = toUV(o, 4, 0);
        String facing = front(o);
        switch (variant) {
            case V_NORMAL, V_SUNK, V_DOOR_TO_WALL -> {
                c.set(d[0], y0 + 1, d[1], B.of(Material.SPRUCE_DOOR, "[facing=" + facing + ",half=lower,hinge=left,open=false]"));
                c.set(d[0], y0 + 2, d[1], B.of(Material.SPRUCE_DOOR, "[facing=" + facing + ",half=upper,hinge=left,open=false]"));
                if (variant == V_DOOR_TO_WALL) {
                    int[] w = toUV(o, 4, 1);
                    c.set(w[0], y0 + 1, w[1], HOUSE_WALL);
                    c.set(w[0], y0 + 2, w[1], HOUSE_WALL);
                }
            }
            case V_HIGH_DOOR -> {
                c.set(d[0], y0 + 3, d[1], B.of(Material.SPRUCE_DOOR, "[facing=" + facing + ",half=lower,hinge=left,open=false]"));
                c.set(d[0], y0 + 4, d[1], B.of(Material.SPRUCE_DOOR, "[facing=" + facing + ",half=upper,hinge=left,open=false]"));
            }
            default -> {
            }
        }
        if (variant == V_SUNK) {
            // trench in front of the door so the sunken doorway is still reachable
            for (int dz = 1; dz <= 2; dz++) {
                int[] t = toUV(o, 4, -dz);
                c.set(t[0], c.fy, t[1], B.AIR);
                c.set(t[0], c.fy - 1, t[1], B.AIR);
                c.set(t[0], c.fy - 2, t[1], dz == 2 ? DIRT : HOUSE_FLOOR);
                if (dz == 2) c.set(t[0], c.fy - 1, t[1], B.stairs(Material.SPRUCE_STAIRS, front(o), false));
            }
        }
    }

    private void pool(CellCtx c) {
        int depth = 12;
        for (int u = 11; u <= 20; u++) {
            for (int v = 12; v <= 19; v++) {
                boolean rim = u == 11 || u == 20 || v == 12 || v == 19;
                if (rim) {
                    for (int y = c.fy - depth; y <= c.fy; y++) c.set(u, y, v, TILE);
                } else {
                    c.set(u, c.fy - depth, v, B.of(Material.LIGHT_BLUE_CONCRETE));
                    for (int y = c.fy - depth + 1; y <= c.fy; y++) c.set(u, y, v, B.WATER);
                }
            }
        }
        c.set(15, c.fy - depth + 1, 15, B.of(Material.LIGHT, "[level=12,waterlogged=true]"));
    }

    private void bunker(CellCtx c) {
        for (int u = 13; u <= 17; u++) {
            for (int v = 13; v <= 17; v++) {
                boolean edge = u == 13 || u == 17 || v == 13 || v == 17;
                for (int y = c.fy + 1; y <= c.fy + 3; y++) c.set(u, y, v, edge ? CONCRETE : B.AIR);
                c.set(u, c.fy + 4, v, CONCRETE);
            }
        }
        c.set(15, c.fy + 1, 13, B.AIR);
        c.set(15, c.fy + 2, 13, B.AIR);
        for (int u = 14; u <= 16; u++) {
            for (int k = 0; k < 3; k++) {
                int v = 14 + k;
                c.set(u, c.fy - k, v, B.stairs(Material.STONE_STAIRS, "north", false));
                for (int y = c.fy - k + 1; y <= c.fy; y++) c.set(u, y, v, B.AIR);
                for (int y = c.fy - 4; y < c.fy - k; y++) c.set(u, y, v, CONCRETE);
            }
            for (int v = 17; v <= 19; v++) {
                c.set(u, c.fy - 4, v, CONCRETE);
                c.set(u, c.fy - 3, v, CONCRETE);
                for (int y = c.fy - 2; y <= c.fy - 1; y++) c.set(u, y, v, B.AIR);
                c.set(u, c.fy, v, CONCRETE);
            }
        }
        for (int v = 14; v <= 20; v++) {
            for (int y = c.fy - 4; y <= c.fy; y++) {
                c.set(13, y, v, CONCRETE);
                c.set(17, y, v, CONCRETE);
            }
        }
        for (int u = 13; u <= 17; u++) for (int y = c.fy - 4; y <= c.fy; y++) c.set(u, y, 20, CONCRETE);
        c.set(15, c.fy - 2, 19, B.light(6));
    }

    @Override
    protected int[] triggerBox(Feature f) {
        return switch (f.id()) {
            case "yellow_house" -> new int[]{14, 0, 10, 18, 1, 11};
            case "backyard_pool" -> new int[]{12, -12, 13, 19, -10, 18};
            case "bunker_hatch" -> new int[]{14, -3, 17, 16, -2, 19};
            case "giant_doorframe" -> new int[]{14, 0, 16, 17, 2, 16};
            default -> null;
        };
    }

    @Override
    public boolean isSafeCell(int floor, int cx, int cz) {
        return kind(cx, cz) == K_FIELD && zone(cx, cz, 0x401L, 96) < 0.86; // never inside a hollow
    }

    @Override
    public String zoneName(int floor, int cx, int cz) {
        int k = kind(cx, cz);
        String s = switch (k) {
            case K_HOUSE -> VARIANTS[variant(cx, cz)];
            case K_COLOSSAL -> COLOSSI[Rng.pick(ch(0, cx, cz, 0xC010), COLOSSI.length)];
            case K_FEATURE -> featureAt(0, cx, cz).id();
            default -> "field";
        };
        return s + (road(0, cz) || road(1, cx) ? " (roadside)" : "");
    }

    // ------------------------------------------------------------------ decorations

    @Override
    protected void cellDecor(int floor, int cx, int cz, List<DecorSpec> out) {
        long h = ch(floor, cx, cz, 0xDEC7);
        int fy = FLOOR + 1;
        // power poles beside roads, one span of wire to the next pole each
        if (road(0, cz)) {
            for (int u : new int[]{8, 24}) out.add(new DecorSpec("utility_pole", cx * S + u + 0.5, fy, cz * S + 3.5, -90f, 0f, 0f, Rng.hash(h, u)));
        }
        if (road(1, cx)) {
            for (int v : new int[]{8, 24}) out.add(new DecorSpec("utility_pole", cx * S + 3.5, fy, cz * S + v + 0.5, 0f, 0f, 0f, Rng.hash(h, 50 + v)));
        }
        int k = kind(cx, cz);
        switch (k) {
            case K_HOUSE -> {
                int o = orientation(cx, cz), var = variant(cx, cz);
                int y0 = fy - 1 + (var == V_SUNK ? -2 : var == V_FLOATING ? 5 : 0);
                int[] mb = toUV(o, 4, -4);
                if (var != V_FLOATING && var != V_MONOLITH) out.add(spec("mailbox", cx, cz, mb[0] + 1.5, fy, mb[1] + 0.5, FRONT_YAW[o], h));
                if (var == V_NORMAL || var == V_DOOR_TO_WALL) {
                    int[] ch = toUV(o, 4, 5);
                    out.add(spec("chair", cx, cz, ch[0] + 0.5, y0 + 1, ch[1] + 0.5, BACK_YAW[o], h)); // facing the back wall
                    int[] lp = toUV(o, 2, 4);
                    out.add(lamp("floor_lamp", cx * S + lp[0] + 0.5, y0 + 1, cz * S + lp[1] + 0.5, 0f, h, 0, cx * S + lp[0], y0 + 2, cz * S + lp[1], 11));
                }
            }
            case K_COLOSSAL -> {
                String model = COLOSSI[Rng.pick(ch(0, cx, cz, 0xC010), COLOSSI.length)];
                out.add(new DecorSpec(model, cx * S + 16.0, model.equals("floating_panel") ? fy + 60 : fy, cz * S + 16.0,
                        (float) Rng.pick(h, 360), 0f, 0f, h));
            }
            case K_FEATURE -> {
                Feature f = featureAt(0, cx, cz);
                switch (f.id()) {
                    case "yellow_house" -> {
                        out.add(lamp("ceiling_panel", cx * S + 16.5, fy + 4, cz * S + 10.5, 0f, f.seed(), 0, cx * S + 16, fy + 3, cz * S + 10, 14));
                        int[] mb = toUV(0, 4, -4);
                        out.add(spec("mailbox", cx, cz, mb[0] + 1.5, fy, mb[1] + 0.5, 180f, h));
                    }
                    case "backyard_pool" -> {
                        out.add(spec("pool_lamp", cx, cz, 15.5, fy - 12 + 0.02, 15.5, 0f, f.seed()));
                        out.add(spec("pool_railing", cx, cz, 12.5, fy, 13.5, 0f, f.seed()));
                    }
                    case "bunker_hatch" -> out.add(spec("wall_lamp", cx, cz, 15.5, fy - 1.5, 19.98, 180f, f.seed()));
                    case "giant_doorframe" -> out.add(new DecorSpec("giant_doorframe", cx * S + 16.0, fy, cz * S + 16.5, 0f, 0f, 0f, f.seed()));
                    default -> {
                    }
                }
            }
            default -> {
                double r = Rng.unit(Rng.hash(h, 3)) / Math.max(0.05, gs.get("decor-density", 1.0));
                if (r < 0.04) out.add(spec("chair", cx, cz, 16.5, fy, 16.5, (float) Rng.pick(h, 360), h));
                else if (r < 0.07) out.add(spec("fallen_panel", cx, cz, 14.5, fy, 18.5, (float) Rng.pick(h, 360), h));
                else if (r < 0.09) out.add(spec("mailbox", cx, cz, 10.5, fy, 21.5, (float) Rng.pick(h, 360), h));
                else if (r < 0.11) out.add(spec("lone_doorframe", cx, cz, 20.5, fy, 12.5, (float) Rng.pick(h, 4) * 90f, h));
                else if (r < 0.12) out.add(spec("exit_sign_post", cx, cz, 8.5, fy, 8.5, (float) Rng.pick(h, 360), h));
            }
        }
    }

    @Override
    protected void cellDetails(int floor, int cx, int cz, List<DecorSpec> out) {
        if (kind(cx, cz) != K_FIELD || zone(cx, cz, 0x401L, 96) >= 0.86) return;
        long h = ch(floor, cx, cz, 0xD7A5);
        int fy = FLOOR + 1;
        // a few thin grass-blade clumps per field, now and then a pale dry one; nothing else
        for (int i = 0; i < 3; i++) {
            long hi = Rng.hash(h, i);
            double u = 5 + Rng.unit(hi) * (S - 10), v = 5 + Rng.unit(Rng.hash(hi, 1)) * (S - 10);
            boolean dry = Rng.chance(Rng.hash(hi, 2), 0.2);
            out.add(detail(DecorSpec.TIER_ENV, Rng.hash(hi, 3), "d:grass:" + (3 + Rng.pick(Rng.hash(hi, 4), 3)) + (dry ? ":dry" : ""),
                    cx * S + u, fy, cz * S + v, (float) Rng.pick(Rng.hash(hi, 5), 360), 0f));
        }
    }
}
