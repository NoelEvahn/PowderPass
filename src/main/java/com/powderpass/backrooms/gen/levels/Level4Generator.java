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

/** Level 4 — Abandoned Office: endless floors of cubicles, meeting rooms, break rooms and lying windows. */
public final class Level4Generator extends RoomLevel {
    private static final int FLOOR = 64;
    static final int K_CORRIDOR = 0, K_CUBICLE = 1, K_MEETING = 2, K_BREAK = 3, K_OFFICE = 4, K_PRINTER = 5, K_WINDOW = 6;
    private static final String[] KINDS = {"corridor", "cubicles", "meeting-room", "break-room", "private-office", "copier-room", "window-room"};
    private static final String[] VIEWS = {"sky", "void", "flooded", "mirror-office"};
    private static final B[][] PAL = {
            // wall, base, carpetA, carpetB, ceil, ceilGrid, light
            {B.of(Material.WHITE_TERRACOTTA), B.of(Material.GRAY_CONCRETE), B.of(Material.GRAY_WOOL), B.of(Material.LIGHT_GRAY_WOOL), B.of(Material.SMOOTH_QUARTZ), B.of(Material.WHITE_CONCRETE), B.of(Material.SEA_LANTERN)},
            {B.of(Material.SMOOTH_QUARTZ), B.of(Material.LIGHT_GRAY_CONCRETE), B.of(Material.BROWN_WOOL), B.of(Material.GRAY_WOOL), B.of(Material.WHITE_CONCRETE), B.of(Material.SMOOTH_QUARTZ), B.of(Material.SEA_LANTERN)},
            {B.of(Material.LIGHT_GRAY_CONCRETE), B.of(Material.CYAN_TERRACOTTA), B.of(Material.BLUE_TERRACOTTA), B.of(Material.GRAY_WOOL), B.of(Material.SMOOTH_QUARTZ), B.of(Material.WHITE_CONCRETE), B.of(Material.VERDANT_FROGLIGHT)},
    };
    private static final String[] NAMES = {"beige-admin", "white-corporate", "blue-grey-records"};
    private static final B PART = B.of(Material.LIGHT_GRAY_WOOL);
    private static final B PART_TOP = B.slab(Material.SMOOTH_STONE_SLAB, false);
    private static final B DESK = B.slab(Material.SPRUCE_SLAB, true);

    public Level4Generator(long seed, GenSettings gs) {
        super(Level.LEVEL_4, seed, gs, 8, 8, 3, new int[]{FLOOR}, FLOOR - 6, FLOOR + 10, 0.2, 0.0);
    }

    @Override
    protected List<FeatureDef> featureDefs() {
        return List.of(
                FeatureDef.transition("elevator_lobby", Dest.to(Level.LEVEL_5), 1.0).withEffect("dwell"),
                FeatureDef.transition("basement_stairs", Dest.to(Level.LEVEL_3), 1.0),
                FeatureDef.transition("fire_exit", Dest.EXIT, gs.get("exit-chance", 0.4)),
                FeatureDef.landmark("executive_suite", gs.get("landmark-chance", 0.6)),
                FeatureDef.landmark("conference_hall", gs.get("landmark-chance", 0.6)));
    }

    @Override
    public String biomeKey() {
        return "minecraft:the_void";
    }

    @Override
    protected boolean mergedRegion(int floor, int rx, int rz) {
        return Rng.chance(Rng.hash(seed ^ 0x0FF1CEL, rx, rz), gs.get("open-plan-chance", 0.3));
    }

    @Override
    protected double[] doorParams(int floor) {
        return new double[]{0.2, 1, 3};
    }

    private int palette(int cx, int cz) {
        return Math.min(2, (int) (zone(cx, cz, 0x4F1L, 80) * 3));
    }

    public int kind(int floor, int cx, int cz) {
        if (merged(floor, cx, cz)) return K_CUBICLE;
        double r = Rng.unit(ch(floor, cx, cz, 0x4444));
        if (r < 0.12) return K_MEETING;
        if (r < 0.20) return K_BREAK;
        if (r < 0.34) return K_OFFICE;
        if (r < 0.39) return K_PRINTER;
        if (r < 0.39 + gs.get("window-chance", 0.12)) return K_WINDOW;
        return K_CORRIDOR;
    }

    private int view(int floor, int cx, int cz) {
        return Rng.pick(ch(floor, cx, cz, 0x51E3), VIEWS.length);
    }

    /** Side used for a window pocket, or -1. */
    private int windowSide(int floor, int cx, int cz) {
        int s = Rng.pick(ch(floor, cx, cz, 0x515E), 4);
        for (int i = 0; i < 4; i++, s = (s + 1) % 4) {
            if (closed(floor, cx, cz, s) && thickWallFits(floor, cx, cz, s, 3)) return s;
        }
        return -1;
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
        st.base = pal[1];
        st.floor = pal[2];
        st.floorAlt = pal[3];
        st.ceil = pal[4];
        st.ceilAlt = pal[5];
        st.light = pal[6];
        st.deadLight = pal[4];
        st.lit = zone(cx, cz, 0xDA44L, 70) > 0.8 ? 0.2 : 0.9;
        st.lightEveryX = 4;
        st.lightEveryZ = 4;
        st.lightOffX = 1;
        st.lightOffZ = 2;
        st.lightLength = 2;
        st.ceilY = FLOOR + 5;
        st.doorH = 3;
        if (merged(floor, cx, cz)) {
            st.name += "/open-plan";
            st.keepCorner = Math.floorMod(cx, 2) == 0 && Math.floorMod(cz, 2) == 0;
        }
        return st;
    }

    @Override
    protected B floorBlock(CellCtx c, Style st, int u, int v) {
        return (((c.wx(u) >> 1) + (c.wz(v) >> 1)) & 1) == 0 ? st.floor : st.floorAlt;
    }

    @Override
    public String zoneName(int floor, int cx, int cz) {
        int k = kind(floor, cx, cz);
        return style(floor, cx, cz).name + " kind=" + KINDS[k] + (k == K_WINDOW ? " view=" + VIEWS[view(floor, cx, cz)] : "");
    }

    @Override
    public boolean isSafeCell(int floor, int cx, int cz) {
        int k = kind(floor, cx, cz);
        return k == K_CORRIDOR || k == K_BREAK || k == K_OFFICE || k == K_WINDOW || k == K_PRINTER;
    }

    @Override
    protected void interior(CellCtx c, Style st) {
        int k = kind(c.floor, c.cx, c.cz);
        switch (k) {
            case K_CUBICLE -> cubicles(c);
            case K_MEETING -> {
                for (int side = 0; side < 2; side++) {
                    if (!closed(c.floor, c.cx, c.cz, side)) continue;
                    for (int a = 1; a < S; a++) {
                        int[] uv = side == 0 ? new int[]{0, a} : new int[]{a, 0};
                        c.set(uv[0], c.fy + 2, uv[1], B.of(Material.GLASS));
                        c.set(uv[0], c.fy + 3, uv[1], B.of(Material.GLASS));
                    }
                }
                for (int u = 3; u <= 5; u++) c.set(u, c.fy + 1, 4, B.slab(Material.DARK_OAK_SLAB, true));
            }
            case K_BREAK -> {
                for (int side = 0; side < 4; side++) {
                    if (!closed(c.floor, c.cx, c.cz, side)) continue;
                    for (int a = 2; a <= S - 2; a++) {
                        int[] uv = sideToUV(side, a, 1);
                        c.set(uv[0], c.fy + 1, uv[1], a == 4 ? B.of(Material.CAULDRON) : B.of(Material.POLISHED_ANDESITE));
                        c.set(uv[0], c.fy + 3, uv[1], B.of(Material.SPRUCE_TRAPDOOR, "[half=top,open=false,facing=north]"));
                    }
                    break;
                }
            }
            case K_OFFICE -> {
                int done = 0;
                for (int side = 0; side < 4 && done < 2; side++) {
                    if (!closed(c.floor, c.cx, c.cz, side)) continue;
                    for (int a = 2; a <= S - 2; a++) {
                        int[] uv = sideToUV(side, a, 1);
                        if (done == 0) {
                            c.set(uv[0], c.fy + 1, uv[1], B.of(Material.BOOKSHELF));
                            c.set(uv[0], c.fy + 2, uv[1], B.of(Material.BOOKSHELF));
                        } else if (a >= 3 && a <= 5) {
                            int[] d = sideToUV(side, a, 2);
                            c.set(d[0], c.fy + 1, d[1], DESK);
                        }
                    }
                    done++;
                }
            }
            case K_PRINTER -> {
                c.set(2, c.fy + 1, 2, B.of(Material.IRON_BLOCK));
                c.set(2, c.fy + 1, 3, B.of(Material.DROPPER, "[facing=up]"));
            }
            case K_WINDOW -> window(c, st);
            default -> {
            }
        }
    }

    private void cubicles(CellCtx c) {
        for (int u = 2; u <= 6; u++) {
            c.set(u, c.fy + 1, 4, PART);
            c.set(u, c.fy + 2, 4, PART_TOP);
        }
        for (int v = 2; v <= 6; v++) {
            c.set(4, c.fy + 1, v, PART);
            c.set(4, c.fy + 2, v, PART_TOP);
        }
        int[][] desks = {{3, 3}, {5, 3}, {3, 5}, {5, 5}};
        for (int[] d : desks) {
            c.set(d[0], c.fy + 1, d[1], DESK);
        }
    }

    private void window(CellCtx c, Style st) {
        int side = windowSide(c.floor, c.cx, c.cz);
        if (side < 0) return;
        int view = view(c.floor, c.cx, c.cz);
        for (int a = 1; a < S; a++) {
            for (int d = 1; d <= 3; d++) {
                int[] uv = sideToUV(side, a, d);
                for (int y = c.fy + 1; y < st.ceilY; y++) {
                    boolean pocketZone = a >= 2 && a <= 6 && y >= c.fy + 2 && y <= c.fy + 3;
                    B b;
                    if (!pocketZone) {
                        b = y == c.fy + 1 ? st.base : st.wall;
                    } else if (d == 3) {
                        b = B.of(Material.GLASS);
                    } else if (d == 2) {
                        b = view == 2 ? B.WATER : B.AIR;
                    } else {
                        b = switch (view) {
                            case 0 -> Rng.chance(c.h(a, y, d, 0xC10D), 0.3) ? B.of(Material.WHITE_WOOL) : B.of(Material.LIGHT_BLUE_CONCRETE);
                            case 1 -> (a == 4 && y == c.fy + 3) ? B.of(Material.SHROOMLIGHT) : B.of(Material.BLACK_CONCRETE);
                            case 2 -> a == 4 ? B.of(Material.SEA_LANTERN) : B.of(Material.PRISMARINE);
                            default -> st.wall;
                        };
                    }
                    c.set(uv[0], y, uv[1], b);
                }
            }
        }
        if (view == 0) {
            int[] uv = sideToUV(side, 4, 2);
            c.set(uv[0], c.fy + 3, uv[1], B.light(15));
        }
    }

    @Override
    protected void feature(CellCtx c, Style st, Feature f) {
        switch (f.id()) {
            case "elevator_lobby" -> booth(c, B.of(Material.DARK_OAK_PLANKS), B.of(Material.RED_WOOL), B.of(Material.DARK_OAK_PLANKS), B.of(Material.SHROOMLIGHT));
            case "basement_stairs" -> stairPit(c, st, B.of(Material.GRAY_CONCRETE), B.of(Material.SMOOTH_STONE), Material.STONE_STAIRS, B.of(Material.SEA_LANTERN));
            case "fire_exit" -> {
                for (int u = 3; u <= 5; u++) {
                    for (int y = c.fy + 1; y <= c.fy + 3; y++) c.set(u, y, 4, B.of(Material.LIGHT_GRAY_CONCRETE));
                }
                c.set(4, c.fy + 1, 4, B.of(Material.WAXED_COPPER_DOOR, "[facing=south,half=lower,hinge=left,open=false]"));
                c.set(4, c.fy + 2, 4, B.of(Material.WAXED_COPPER_DOOR, "[facing=south,half=upper,hinge=left,open=false]"));
            }
            case "executive_suite" -> {
                for (int u = 3; u <= 5; u++) c.set(u, c.fy + 1, 2, DESK);
                for (int u = 1; u < S; u++) {
                    c.set(u, c.fy, 1, B.of(Material.RED_WOOL));
                }
            }
            case "conference_hall" -> {
                for (int u = 2; u <= 6; u++) c.set(u, c.fy + 1, 4, B.slab(Material.DARK_OAK_SLAB, true));
            }
            default -> {
            }
        }
    }

    @Override
    protected int[] triggerBox(Feature f) {
        return switch (f.id()) {
            case "elevator_lobby" -> boothTrigger();
            case "basement_stairs" -> stairPitTrigger();
            case "fire_exit" -> new int[]{4, 0, 4, 4, 1, 4};
            default -> null;
        };
    }

    @Override
    protected void cellDecor(int floor, int cx, int cz, List<DecorSpec> out) {
        Feature f = featureAt(floor, cx, cz);
        Style st = style(floor, cx, cz);
        int fy = floorY[floor] + 1;
        long h = ch(floor, cx, cz, 0xDEC5);
        if (f != null) {
            switch (f.id()) {
                case "elevator_lobby" -> {
                    out.add(spec("elevator_doors", cx, cz, 4.5, fy, 6.0, 0f, f.seed()));
                    out.add(spec("floor_indicator", cx, cz, 4.5, fy + 3.3, 6.02, 0f, f.seed()));
                }
                case "basement_stairs" -> out.add(spec("basement_sign", cx, cz, 4.5, fy + 1.2, 1.2, 0f, f.seed()));
                case "fire_exit" -> {
                    out.add(spec("exit_sign", cx, cz, 4.5, fy + 2.75, 4.0, 180f, f.seed()));
                    out.add(spec("exit_sign", cx, cz, 4.5, fy + 2.75, 5.0, 0f, f.seed()));
                }
                case "executive_suite" -> {
                    out.add(spec("crt_monitor", cx, cz, 4.5, fy + 1.0, 2.5, 0f, f.seed()));
                    out.add(spec("armchair", cx, cz, 4.5, fy, 3.6, 180f, f.seed()));
                    out.add(spec("potted_plant", cx, cz, 1.5, fy, 1.5, 0f, f.seed()).barrier(cx * S + 1, fy, cz * S + 1));
                    out.add(spec("wall_clock", cx, cz, 4.5, fy + 2.4, 7.99, 180f, f.seed()));
                }
                case "conference_hall" -> {
                    for (int i = 0; i < 3; i++) {
                        out.add(spec("office_chair", cx, cz, 2.5 + i * 2, fy, 3.2, 180f, Rng.hash(f.seed(), i)));
                        out.add(spec("office_chair", cx, cz, 3.5 + i * 2, fy, 5.8, 0f, Rng.hash(f.seed(), i + 9)));
                    }
                    out.add(spec("whiteboard", cx, cz, 4.5, fy + 1.2, 7.98, 180f, f.seed()));
                }
                default -> {
                }
            }
            return;
        }
        int k = kind(floor, cx, cz);
        double density = gs.get("decor-density", 1.0);
        double r = Rng.unit(h) / Math.max(0.05, density);
        switch (k) {
            case K_CUBICLE -> {
                if (r < 0.3) out.add(spec("crt_monitor", cx, cz, 3.5, fy + 1.0, 3.5, 135f, h));
                if (Rng.chance(Rng.hash(h, 1), 0.25 * density)) out.add(spec("office_chair", cx, cz, 2.6, fy, 2.4, (float) Rng.pick(h, 360), h));
                if (Rng.chance(Rng.hash(h, 2), 0.08 * density)) out.add(spec("swivel_chair_spinning", cx, cz, 5.6, fy, 6.4, 0f, h));
            }
            case K_MEETING -> {
                out.add(spec("office_chair", cx, cz, 3.5, fy, 3.2, 0f, h));
                out.add(spec("office_chair", cx, cz, 5.5, fy, 5.8, 180f, Rng.hash(h, 1)));
                if (closed(floor, cx, cz, 3)) out.add(spec("whiteboard", cx, cz, 4.0, fy + 1.2, 7.98, 180f, h));
            }
            case K_BREAK -> {
                out.add(spec("water_cooler", cx, cz, 6.5, fy, 6.5, 225f, h).barrier(cx * S + 6, fy, cz * S + 6));
                if (r < 0.5) out.add(spec("vending_machine", cx, cz, 1.5, fy, 6.5, -90f, h).barrier(cx * S + 1, fy, cz * S + 6, cx * S + 1, fy + 1, cz * S + 6));
            }
            case K_OFFICE -> {
                out.add(spec("potted_plant", cx, cz, 6.5, fy, 6.5, 0f, h).barrier(cx * S + 6, fy, cz * S + 6));
                if (r < 0.6) out.add(spec("wall_clock", cx, cz, 4.5, fy + 2.4, 1.0, 0f, h));
            }
            case K_PRINTER -> out.add(spec("copier", cx, cz, 2.5, fy + 1.0, 2.5, 90f, h));
            case K_WINDOW -> {
                int side = windowSide(floor, cx, cz);
                int v = view(floor, cx, cz);
                if (side >= 0 && (v == 0 || v == 3)) {
                    int[] uv = sideToUV(side, 4, 2);
                    out.add(new DecorSpec(v == 0 ? "window_skyline" : "window_mirror_office", cx * S + uv[0] + 0.5, fy + 1.0,
                            cz * S + uv[1] + 0.5, yawAwayFrom(side) + 180f, 0f, 0f, h));
                }
            }
            default -> {
                if (r < 0.10) out.add(spec("water_cooler", cx, cz, 1.5, fy, 1.5, 135f, h).barrier(cx * S + 1, fy, cz * S + 1));
                else if (r < 0.18) out.add(spec("potted_plant", cx, cz, 6.5, fy, 1.5, 0f, h).barrier(cx * S + 6, fy, cz * S + 1));
                else if (r < 0.22) out.add(spec("crt_monitor", cx, cz, 4.5, fy + 0.0, 6.5, (float) Rng.pick(h, 360), h));
                else if (r < 0.26 && closed(floor, cx, cz, 1)) out.add(spec("wall_clock", cx, cz, 4.5, fy + 2.4, 1.0, 0f, h));
                else if (r < 0.30) out.add(new DecorSpec("floating_chair", cx * S + 4.5, fy + 1.4, cz * S + 4.5, (float) Rng.pick(h, 360), 10f, 22f, h));
            }
        }
    }
}
