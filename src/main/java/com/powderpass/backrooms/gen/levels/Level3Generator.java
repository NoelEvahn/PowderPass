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
 * Level 3 — Electrical Station. Two stacked service floors, each with its own maze. Every region holds one
 * service ladder that links both floors at the same cell (each floor's maze is connected, so the whole
 * station is connected). Atrium cells are double height with a railed catwalk on the upper floor.
 */
public final class Level3Generator extends RoomLevel {
    private static final int F0 = 64, F1 = 73;
    static final int K_CONDUIT = 0, K_MECH = 1, K_SWITCH = 2, K_TRANSFORMER = 3, K_LADDER = 4, K_ATRIUM = 5;
    private static final String[] KINDS = {"conduit", "machine-room", "switch-room", "transformer-yard", "service-ladder", "atrium"};
    private static final B RAIL_NS = B.of(Material.IRON_BARS, "[north=true,south=true]");
    private static final B RAIL_EW = B.of(Material.IRON_BARS, "[east=true,west=true]");
    private static final B[][] PAL = {
            // wall, wallAlt, base, floor, floorAlt, ceil, light
            {B.of(Material.LIGHT_GRAY_CONCRETE), B.of(Material.POLISHED_ANDESITE), B.of(Material.GRAY_CONCRETE), B.of(Material.SMOOTH_STONE), B.of(Material.POLISHED_ANDESITE), B.of(Material.GRAY_CONCRETE), B.of(Material.SEA_LANTERN)},
            {B.of(Material.LIGHT_GRAY_CONCRETE), B.of(Material.LIGHT_GRAY_CONCRETE), B.of(Material.YELLOW_CONCRETE), B.of(Material.POLISHED_TUFF), B.of(Material.SMOOTH_STONE), B.of(Material.GRAY_CONCRETE), B.of(Material.SEA_LANTERN)},
            {B.of(Material.WAXED_WEATHERED_CUT_COPPER), B.of(Material.WAXED_EXPOSED_CUT_COPPER), B.of(Material.POLISHED_DEEPSLATE), B.of(Material.POLISHED_DEEPSLATE), B.of(Material.DEEPSLATE_TILES), B.of(Material.DEEPSLATE_TILES), B.of(Material.WAXED_COPPER_BULB, "[lit=true,powered=false]")},
    };
    private static final String[] NAMES = {"switchgear-grey", "hazard-stripe", "old-copper"};

    public Level3Generator(long seed, GenSettings gs) {
        super(Level.LEVEL_3, seed, gs, 10, 6, 3, new int[]{F0, F1}, F0 - 6, F1 + 9, 0.16, 0.0);
    }

    @Override
    protected List<FeatureDef> featureDefs() {
        return List.of(
                FeatureDef.transition("office_lift", Dest.to(Level.LEVEL_4), 1.0).onFloor(1).withEffect("dwell"),
                FeatureDef.transition("vent", Dest.to(Level.LEVEL_2), 1.0).onFloor(0),
                FeatureDef.landmark("control_room", gs.get("landmark-chance", 0.7)).onFloor(1),
                FeatureDef.landmark("turbine_hall", gs.get("landmark-chance", 0.7)).onFloor(0));
    }

    @Override
    public String biomeKey() {
        return "minecraft:the_void";
    }

    @Override
    protected double[] doorParams(int floor) {
        return new double[]{0.25, 2, 4};
    }

    private int palette(int cx, int cz) {
        return Math.min(2, (int) (zone(cx, cz, 0x3E1L, 80) * 3));
    }

    /** The ladder cell of a region, never on a feature cell of either floor. */
    private boolean isLadder(int cx, int cz) {
        int rx = Math.floorDiv(cx, R), rz = Math.floorDiv(cz, R);
        long h = Rng.hash(seed ^ 0x1ADDE7L, rx, rz);
        int a = Rng.range(h, 1, R - 2), b = Rng.range(Rng.hash(h, 1), 1, R - 2);
        for (int i = 0; i < R * R; i++) {
            int lx = 1 + Math.floorMod(a - 1 + i, R - 2), lz = 1 + Math.floorMod(b - 1 + i / (R - 2), R - 2);
            int tx = rx * R + lx, tz = rz * R + lz;
            if (featureAt(0, tx, tz) == null && featureAt(1, tx, tz) == null) {
                return tx == cx && tz == cz;
            }
        }
        return false;
    }

    private boolean isAtrium(int cx, int cz) {
        return !isLadder(cx, cz) && featureAt(0, cx, cz) == null && featureAt(1, cx, cz) == null
                && Rng.chance(Rng.hash(seed ^ 0xA791L, cx, cz), gs.get("atrium-chance", 0.06));
    }

    public int kind(int floor, int cx, int cz) {
        if (isLadder(cx, cz)) return K_LADDER;
        if (isAtrium(cx, cz)) return K_ATRIUM;
        double r = Rng.unit(ch(floor, cx, cz, 0x3333));
        if (r < 0.35) return K_MECH;
        if (r < 0.48) return K_SWITCH;
        if (r < 0.48 + gs.get("transformer-chance", 0.07)) return K_TRANSFORMER;
        return K_CONDUIT;
    }

    @Override
    protected Style style(int floor, int cx, int cz) {
        Style st = new Style();
        int p = palette(cx, cz);
        B[] pal = PAL[p];
        st.name = NAMES[p];
        st.under = B.of(Material.STONE);
        st.above = B.of(Material.STONE);
        st.wall = pal[0];
        st.wallAlt = pal[1];
        st.stripe = p == 0 ? 4 : 0;
        st.base = pal[2];
        st.floor = pal[3];
        st.floorAlt = pal[4];
        st.floorAltChance = 0.15;
        st.ceil = pal[5];
        st.light = pal[6];
        st.deadLight = pal[5];
        st.lit = 0.8;
        st.lightEveryX = 5;
        st.lightEveryZ = 5;
        st.lightOffX = 2;
        st.lightOffZ = 2;
        st.ceilY = floor == 0 ? F1 - 2 : F1 + 7;
        st.doorH = 3;
        return st;
    }

    @Override
    protected B wallBlock(CellCtx c, Style st, int u, int y, int v, int along) {
        if (palette(c.cx, c.cz) == 1 && y == c.fy + 1) {
            return Math.floorMod(c.wx(u) + c.wz(v), 2) == 0 ? B.of(Material.YELLOW_CONCRETE) : B.of(Material.BLACK_CONCRETE);
        }
        return super.wallBlock(c, st, u, y, v, along);
    }

    @Override
    public String zoneName(int floor, int cx, int cz) {
        return NAMES[palette(cx, cz)] + " floor=" + floor + " kind=" + KINDS[kind(floor, cx, cz)];
    }

    @Override
    public boolean isSafeCell(int floor, int cx, int cz) {
        int k = kind(floor, cx, cz);
        return k == K_CONDUIT || k == K_MECH || k == K_SWITCH;
    }

    @Override
    protected void interior(CellCtx c, Style st) {
        int k = kind(c.floor, c.cx, c.cz);
        long h = ch(c.floor, c.cx, c.cz, 0x31);
        switch (k) {
            case K_LADDER -> ladder(c);
            case K_ATRIUM -> atrium(c, st);
            case K_MECH -> machinesAlongWalls(c, h, 0.65);
            case K_SWITCH -> {
                for (int side = 0; side < 4; side++) {
                    if (!closed(c.floor, c.cx, c.cz, side)) continue;
                    for (int a = 2; a <= S - 2; a++) {
                        int[] uv = sideToUV(side, a, 1);
                        c.set(uv[0], c.fy + 1, uv[1], B.of(Material.POLISHED_BLACKSTONE));
                        c.set(uv[0], c.fy + 2, uv[1], (a & 1) == 0 ? B.of(Material.TARGET) : B.of(Material.POLISHED_BLACKSTONE_BRICKS));
                    }
                }
            }
            case K_TRANSFORMER -> {
                for (int u = 3; u <= 7; u++) {
                    for (int v = 3; v <= 7; v++) {
                        boolean ring = u == 3 || u == 7 || v == 3 || v == 7;
                        if (ring) {
                            c.set(u, c.fy + 1, v, (u == 3 || u == 7) ? RAIL_NS : RAIL_EW);
                            c.set(u, c.fy + 2, v, (u == 3 || u == 7) ? RAIL_NS : RAIL_EW);
                        } else {
                            c.set(u, c.fy, v, B.of(Material.WAXED_OXIDIZED_COPPER_GRATE));
                        }
                    }
                }
                c.set(5, c.fy + 1, 5, B.of(Material.IRON_BLOCK));
                c.set(5, c.fy + 2, 5, B.of(Material.LIGHTNING_ROD, "[facing=up]"));
            }
            default -> {
                for (int u = Math.max(1, c.u0); u <= c.u1; u++) {
                    for (int v = Math.max(1, c.v0); v <= c.v1; v++) {
                        if ((u == S / 2 || v == S / 2) && Rng.chance(c.h(u, 0, v, 0x6A7), 0.5)) {
                            c.set(u, c.fy, v, B.of(Material.WAXED_COPPER_GRATE));
                        }
                    }
                }
            }
        }
    }

    private void machinesAlongWalls(CellCtx c, long h, double fill) {
        B[] m = {B.of(Material.OBSERVER, "[facing=up]"), B.of(Material.DISPENSER, "[facing=up]"), B.of(Material.IRON_BLOCK), B.of(Material.CRAFTER),
                B.of(Material.PISTON, "[facing=up,extended=false]"), B.of(Material.WAXED_COPPER_GRATE), B.of(Material.LODESTONE), B.of(Material.REDSTONE_LAMP)};
        for (int side = 0; side < 4; side++) {
            if (!closed(c.floor, c.cx, c.cz, side)) continue;
            for (int a = 2; a <= S - 2; a++) {
                long hh = Rng.hash(h, side, a);
                if (!Rng.chance(hh, fill)) continue;
                int[] uv = sideToUV(side, a, 1);
                int height = 1 + Rng.pick(Rng.hash(hh, 1), 2);
                for (int y = 0; y < height; y++) c.set(uv[0], c.fy + 1 + y, uv[1], m[Rng.pick(Rng.hash(hh, 5 + y), m.length)]);
            }
        }
    }

    /** Service ladder at (5,6) against a 1x1 pillar at (5,5) spanning both floors, railed on the upper floor. */
    private void ladder(CellCtx c) {
        if (c.floor == 0) {
            for (int y = F0 + 1; y <= F1; y++) {
                c.set(5, y, 5, B.of(Material.POLISHED_ANDESITE));
                c.set(5, y, 6, B.of(Material.LADDER, "[facing=south]"));
            }
            c.set(5, F1 - 2, 4, B.of(Material.SEA_LANTERN));
        } else {
            for (int y = F1 - 2; y <= F1; y++) {
                c.set(5, y, 6, B.of(Material.LADDER, "[facing=south]"));
                c.set(5, y, 5, B.of(Material.POLISHED_ANDESITE));
            }
            c.set(4, F1 + 1, 6, RAIL_NS);
            c.set(6, F1 + 1, 6, RAIL_NS);
            c.set(5, F1 + 1, 7, RAIL_EW);
            c.set(4, F1 + 1, 7, B.of(Material.IRON_BARS, "[north=true,east=true]"));
            c.set(6, F1 + 1, 7, B.of(Material.IRON_BARS, "[north=true,west=true]"));
        }
    }

    private void atrium(CellCtx c, Style st) {
        if (c.floor == 0) {
            for (int u = 4; u <= 6; u++) {
                for (int v = 4; v <= 6; v++) {
                    c.set(u, c.fy + 1, v, B.of(Material.IRON_BLOCK));
                }
            }
            return;
        }
        int lo = 2, hi = S - 2;
        for (int u = lo; u <= hi; u++) {
            for (int v = lo; v <= hi; v++) {
                for (int y = F1 - 2; y <= F1; y++) c.set(u, y, v, B.AIR);
                boolean edge = u == lo || u == hi || v == lo || v == hi;
                if (edge) {
                    B rail = (u == lo || u == hi) && !(v == lo || v == hi) ? RAIL_NS : (v == lo || v == hi) && !(u == lo || u == hi) ? RAIL_EW : B.of(Material.IRON_BARS);
                    c.set(u, F1 + 1, v, rail);
                }
            }
        }
    }

    @Override
    protected void feature(CellCtx c, Style st, Feature f) {
        switch (f.id()) {
            case "office_lift" -> booth(c, B.of(Material.IRON_BLOCK), B.of(Material.POLISHED_ANDESITE), B.of(Material.IRON_BLOCK), B.of(Material.SEA_LANTERN));
            case "vent" -> booth(c, B.of(Material.WAXED_COPPER_GRATE), B.of(Material.WAXED_COPPER_GRATE), B.of(Material.WAXED_COPPER_GRATE), B.of(Material.WAXED_COPPER_GRATE));
            case "control_room" -> {
                for (int u = 2; u <= 8; u++) {
                    c.set(u, c.fy + 1, 2, B.of(Material.POLISHED_BLACKSTONE));
                    c.set(u, c.fy + 2, 2, B.of(Material.TARGET));
                }
            }
            case "turbine_hall" -> {
                for (int u = 3; u <= 7; u++) for (int v = 3; v <= 7; v++) c.set(u, c.fy, v, B.of(Material.WAXED_OXIDIZED_COPPER_GRATE));
            }
            default -> {
            }
        }
    }

    @Override
    protected int[] triggerBox(Feature f) {
        return switch (f.id()) {
            case "office_lift", "vent" -> boothTrigger();
            default -> null;
        };
    }

    @Override
    protected void cellDecor(int floor, int cx, int cz, List<DecorSpec> out) {
        Feature f = featureAt(floor, cx, cz);
        Style st = style(floor, cx, cz);
        int fy = floorY[floor] + 1;
        long h = ch(floor, cx, cz, 0xDEC4);
        if (f != null) {
            switch (f.id()) {
                case "office_lift" -> {
                    out.add(spec("elevator_doors", cx, cz, 4.5, fy, 6.0, 0f, f.seed()));
                    out.add(spec("lift_sign", cx, cz, 4.5, fy + 3.3, 6.02, 0f, f.seed()));
                }
                case "vent" -> out.add(spec("vent_fan", cx, cz, 4.5, fy + 1.0, 5.98, 0f, f.seed()));
                case "control_room" -> {
                    out.add(spec("switchboard", cx, cz, 5.0, fy + 1.0, 2.98, 0f, f.seed()));
                    out.add(spec("office_chair", cx, cz, 5.5, fy, 4.5, 180f, f.seed()));
                    out.add(spec("crt_monitor", cx, cz, 3.5, fy + 1.0, 3.2, 0f, f.seed()));
                }
                case "turbine_hall" -> out.add(spec("turbine", cx, cz, 5.5, fy, 5.5, 0f, f.seed())
                        .barrier(cx * S + 5, fy, cz * S + 5));
                default -> {
                }
            }
            return;
        }
        int k = kind(floor, cx, cz);
        double density = gs.get("decor-density", 1.0);
        double r = Rng.unit(h) / Math.max(0.05, density);
        switch (k) {
            case K_ATRIUM -> {
                if (floor == 0) {
                    out.add(spec("generator", cx, cz, 5.5, fy + 1, 5.5, 0f, h));
                    out.add(spec("wire_rack", cx, cz, 2.0, F1 + 6.5, 5.5, 0f, h));
                }
            }
            case K_TRANSFORMER -> {
                out.add(spec("transformer", cx, cz, 5.5, fy + 1, 5.5, 0f, h));
                out.add(spec("danger_sign", cx, cz, 5.5, fy + 1.6, 2.9, 180f, h));
            }
            case K_SWITCH -> {
                if (closed(floor, cx, cz, 1)) out.add(spec("switchboard", cx, cz, 5.0, fy + 1.0, 2.0, 0f, h));
                else if (r < 0.5) out.add(spec("gauge", cx, cz, 5.5, fy + 2.4, 1.0, 0f, h));
            }
            case K_LADDER -> out.add(spec("ladder_cage", cx, cz, 5.5, fy, 6.5, 0f, h));
            default -> {
                if (r < 0.18) {
                    out.add(spec("cable_tray", cx, cz, 0.0, st.ceilY - 0.6, 5.5, -90f, h));
                } else if (r < 0.28) {
                    out.add(spec("piston_pump", cx, cz, 8.5, fy, 8.5, 0f, h).barrier(cx * S + 8, fy, cz * S + 8));
                } else if (r < 0.36 && closed(floor, cx, cz, 0)) {
                    out.add(spec("gauge", cx, cz, 1.0, fy + 1.8, 4.5, -90f, h));
                } else if (r < 0.44) {
                    out.add(spec("wire_rack", cx, cz, 0.0, st.ceilY - 0.3, 2.5, -90f, h));
                } else if (r < 0.49 && closed(floor, cx, cz, 1)) {
                    out.add(spec("danger_sign", cx, cz, 5.0, fy + 2.0, 1.02, 0f, h));
                } else if (r < 0.53) {
                    out.add(spec("vibrating_motor", cx, cz, 2.5, fy, 8.5, 0f, h).barrier(cx * S + 2, fy, cz * S + 8));
                }
            }
        }
    }
}
