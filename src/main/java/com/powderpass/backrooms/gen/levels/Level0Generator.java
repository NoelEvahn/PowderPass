package com.powderpass.backrooms.gen.levels;

import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.gen.B;
import com.powderpass.backrooms.gen.CellCtx;
import com.powderpass.backrooms.gen.DecorSpec;
import com.powderpass.backrooms.gen.Dest;
import com.powderpass.backrooms.gen.Door;
import com.powderpass.backrooms.gen.Feature;
import com.powderpass.backrooms.gen.FeatureDef;
import com.powderpass.backrooms.gen.GenSettings;
import com.powderpass.backrooms.gen.RoomLevel;
import com.powderpass.backrooms.gen.Style;
import com.powderpass.backrooms.util.Rng;
import org.bukkit.Material;

import java.util.List;

/**
 * Level 0 — The Lobby. Mono-yellow wallpaper, damp carpet, buzzing fluorescent panels. Sub-styles drift
 * in large noise patches; pillar halls and low-ceiling crawl sectors break the rhythm; whole regions
 * occasionally repeat their neighbour exactly.
 */
public final class Level0Generator extends RoomLevel {
    private static final int FLOOR = 64;
    private static final int K_PLAIN = 0, K_THICK = 1, K_STUB = 2, K_PILLAR = 3, K_HALL = 4;

    private static final B STONE = B.of(Material.STONE);
    private static final B[][] PALETTES = {
            // wall, wallAlt, base, floor, floorAlt, ceil, ceilAlt, light
            {B.of(Material.SMOOTH_SANDSTONE), B.of(Material.CUT_SANDSTONE), B.of(Material.BIRCH_PLANKS), B.of(Material.YELLOW_TERRACOTTA), B.of(Material.TERRACOTTA), B.of(Material.END_STONE_BRICKS), B.of(Material.SMOOTH_SANDSTONE), B.of(Material.OCHRE_FROGLIGHT)},
            {B.of(Material.SANDSTONE), B.of(Material.CHISELED_SANDSTONE), B.of(Material.STRIPPED_BIRCH_WOOD), B.of(Material.YELLOW_TERRACOTTA), B.of(Material.BROWN_TERRACOTTA), B.of(Material.END_STONE_BRICKS), null, B.of(Material.OCHRE_FROGLIGHT)},
            {B.of(Material.END_STONE_BRICKS), B.of(Material.SMOOTH_SANDSTONE), B.of(Material.SANDSTONE), B.of(Material.YELLOW_TERRACOTTA), B.of(Material.ORANGE_TERRACOTTA), B.of(Material.WHITE_TERRACOTTA), B.of(Material.SMOOTH_SANDSTONE), B.of(Material.PEARLESCENT_FROGLIGHT)},
            {B.of(Material.SMOOTH_SANDSTONE), B.of(Material.SANDSTONE), B.of(Material.BIRCH_PLANKS), B.of(Material.YELLOW_TERRACOTTA), B.of(Material.MUD), B.of(Material.END_STONE_BRICKS), null, B.of(Material.OCHRE_FROGLIGHT)},
    };
    private static final String[] PALETTE_NAMES = {"mono-yellow", "chevron", "faded-tile", "damp"};

    public Level0Generator(long seed, GenSettings gs) {
        super(Level.LEVEL_0, seed, gs, 8, 8, 3, new int[]{FLOOR}, FLOOR - 8, FLOOR + 14, 0.18, gs.get("repeat-chance", 0.06));
    }

    @Override
    protected List<FeatureDef> featureDefs() {
        return List.of(
                FeatureDef.transition("stairwell", Dest.to(Level.LEVEL_1), 1.0),
                FeatureDef.transition("seam", Dest.to(Level.POOLROOMS), 1.0).withEffect("intent"),
                FeatureDef.transition("exit_door", Dest.EXIT, gs.get("exit-chance", 0.35)),
                FeatureDef.landmark("phone_desk", gs.get("landmark-chance", 0.7)),
                FeatureDef.landmark("red_room", gs.get("landmark-chance", 0.7) * 0.6),
                FeatureDef.landmark("chair_circle", gs.get("landmark-chance", 0.7) * 0.6));
    }

    @Override
    public String biomeKey() {
        return "minecraft:swamp";
    }

    @Override
    protected boolean mergedRegion(int floor, int rx, int rz) {
        return Rng.chance(Rng.hash(seed ^ 0x4A11L, rx, rz), gs.get("hall-chance", 0.10));
    }

    @Override
    protected double[] doorParams(int floor) {
        return new double[]{0.32, 1, 4};
    }

    private int palette(int cx, int cz) {
        double n = zone(cx, cz, 0x9A1L, 72);
        return Math.min(3, (int) (n * 4.0));
    }

    private boolean dark(int cx, int cz) {
        return zone(cx, cz, 0xDA4CL, 90) > 1.0 - gs.get("dark-zone-chance", 0.2) * 0.6;
    }

    private boolean low(int cx, int cz) {
        return zone(cx, cz, 0x10E1L, 64) > 0.76;
    }

    int kind(int floor, int cx, int cz) {
        if (merged(floor, cx, cz)) {
            return K_HALL;
        }
        double r = Rng.unit(ch(floor, cx, cz, 0x4B1D));
        if (r < gs.get("thick-wall-chance", 0.12)) return K_THICK;
        if (r < 0.24) return K_STUB;
        if (r < 0.29) return K_PILLAR;
        return K_PLAIN;
    }

    @Override
    protected Style style(int floor, int cx, int cz) {
        Style st = new Style();
        int p = palette(cx, cz);
        B[] pal = PALETTES[p];
        st.name = PALETTE_NAMES[p];
        st.under = STONE;
        st.above = STONE;
        st.wall = pal[0];
        st.wallAlt = pal[1];
        st.stripe = p == 1 ? 5 : 3;
        st.base = pal[2];
        st.floor = pal[3];
        st.floorAlt = pal[4];
        st.floorAltChance = p == 3 ? 0.14 : 0.05;
        st.ceil = pal[5];
        st.ceilAlt = pal[6];
        st.light = pal[7];
        st.deadLight = B.of(Material.WHITE_TERRACOTTA);
        st.wallStain = p == 3 ? 0.07 : 0.015;
        st.wallStainBlock = B.of(Material.TERRACOTTA);
        st.lightEveryX = 4;
        st.lightEveryZ = 4;
        st.lightOffX = 1;
        st.lightOffZ = 2;
        st.lightLength = 2;
        st.lit = dark(cx, cz) ? 0.1 : 0.93;
        boolean hall = merged(floor, cx, cz);
        st.ceilY = hall ? FLOOR + 10 : low(cx, cz) ? FLOOR + 4 : FLOOR + 5;
        st.doorH = low(cx, cz) ? 2 : 3;
        if (hall) {
            st.keepCorner = true;
            st.pillar2x2 = Math.floorMod(cx + cz, 2) == 0;
            st.base = B.of(Material.CUT_SANDSTONE);
            st.lightEveryZ = 8;
            st.lightOffZ = 4;
            st.name = "pillar-hall";
        }
        if (dark(cx, cz)) {
            st.name += "/dark";
        }
        return st;
    }

    @Override
    public String zoneName(int floor, int cx, int cz) {
        return style(floor, cx, cz).name + (low(cx, cz) ? "/low" : "") + " kind=" + kind(floor, cx, cz);
    }

    @Override
    public boolean isSafeCell(int floor, int cx, int cz) {
        int k = kind(floor, cx, cz);
        return k != K_PILLAR && !low(cx, cz);
    }

    @Override
    protected void interior(CellCtx c, Style st) {
        int k = kind(c.floor, c.cx, c.cz);
        long h = ch(c.floor, c.cx, c.cz, 0x1A7E);
        switch (k) {
            case K_THICK -> {
                int side = Rng.pick(h, 4);
                for (int i = 0; i < 4; i++, side = (side + 1) % 4) {
                    int depth = 1 + Rng.pick(Rng.hash(h, 7), 2);
                    if (closed(c.floor, c.cx, c.cz, side) && thickWallFits(c.floor, c.cx, c.cz, side, depth)) {
                        for (int d = 1; d <= depth; d++) {
                            for (int a = 1; a < S; a++) {
                                int[] uv = sideToUV(side, a, d);
                                for (int y = c.fy + 1; y < st.ceilY; y++) {
                                    c.set(uv[0], y, uv[1], wallBlock(c, st, uv[0], y, uv[1], a));
                                }
                            }
                        }
                        break;
                    }
                }
            }
            case K_STUB -> {
                int side = Rng.pick(h, 4);
                for (int i = 0; i < 4; i++, side = (side + 1) % 4) {
                    if (closed(c.floor, c.cx, c.cz, side)) {
                        int len = 2 + Rng.pick(Rng.hash(h, 3), 2);
                        for (int d = 1; d <= len; d++) {
                            int[] uv = sideToUV(side, S / 2, d);
                            for (int y = c.fy + 1; y < st.ceilY; y++) {
                                c.set(uv[0], y, uv[1], wallBlock(c, st, uv[0], y, uv[1], d));
                            }
                        }
                        break;
                    }
                }
            }
            case K_PILLAR -> {
                for (int u = 3; u <= 4; u++) {
                    for (int v = 3; v <= 4; v++) {
                        for (int y = c.fy + 1; y < st.ceilY; y++) {
                            c.set(u, y, v, y == c.fy + 1 ? st.base : B.of(Material.SMOOTH_SANDSTONE));
                        }
                    }
                }
            }
            default -> {
            }
        }
        if (palette(c.cx, c.cz) == 3 && k != K_HALL) {
            // damp puddles (flush water, murky swamp tint), never in the centre 3x3
            for (int u = Math.max(1, c.u0); u <= c.u1; u++) {
                for (int v = Math.max(1, c.v0); v <= c.v1; v++) {
                    if (u >= 3 && u <= 5 && v >= 3 && v <= 5) continue;
                    double n = com.powderpass.backrooms.util.ValueNoise.sample(seed ^ 0x9D, c.wx(u) / 3.0, c.wz(v) / 3.0);
                    if (n > 0.78 && c.type(u, c.fy + 1, v) == Material.AIR) {
                        c.set(u, c.fy, v, B.WATER);
                        c.set(u, c.fy - 1, v, B.of(Material.MUD));
                    }
                }
            }
        }
    }

    @Override
    protected void feature(CellCtx c, Style st, Feature f) {
        switch (f.id()) {
            case "stairwell" -> stairPit(c, st, B.of(Material.GRAY_CONCRETE), B.of(Material.GRAY_CONCRETE), Material.ANDESITE_STAIRS, B.of(Material.SHROOMLIGHT));
            case "seam" -> {
                for (int u = 2; u <= 6; u++) {
                    for (int v = 2; v <= 6; v++) {
                        c.set(u, c.fy, v, ((u + v) & 1) == 0 ? B.of(Material.WHITE_CONCRETE) : B.of(Material.SMOOTH_QUARTZ));
                    }
                }
                for (int u = 3; u <= 5; u++) {
                    for (int v = 3; v <= 5; v++) {
                        for (int y = c.fy + 1; y < st.ceilY; y++) {
                            boolean crack = Rng.chance(c.h(u, y, v, 0x5EA), 0.18);
                            c.set(u, y, v, crack ? B.of(Material.LIGHT_BLUE_CONCRETE) : (((u + y + v) & 1) == 0 ? B.of(Material.WHITE_CONCRETE) : B.of(Material.SMOOTH_QUARTZ)));
                        }
                    }
                }
            }
            case "exit_door" -> {
                for (int u = 3; u <= 5; u++) {
                    for (int y = c.fy + 1; y <= c.fy + 3; y++) {
                        c.set(u, y, 4, st.wall);
                    }
                }
                c.set(4, c.fy + 1, 4, B.of(Material.DARK_OAK_DOOR, "[facing=south,half=lower,hinge=left,open=false]"));
                c.set(4, c.fy + 2, 4, B.of(Material.DARK_OAK_DOOR, "[facing=south,half=upper,hinge=left,open=false]"));
            }
            case "red_room" -> {
                for (int u = Math.max(1, c.u0); u <= c.u1; u++) {
                    for (int v = Math.max(1, c.v0); v <= c.v1; v++) {
                        c.set(u, c.fy, v, B.of(Material.RED_WOOL));
                        if (isLightPos(c, st, u, v)) c.set(u, st.ceilY, v, B.of(Material.SHROOMLIGHT));
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
            case "stairwell" -> stairPitTrigger();
            case "seam" -> new int[]{2, 0, 2, 6, 1, 6};
            case "exit_door" -> new int[]{4, 0, 4, 4, 1, 4};
            default -> null;
        };
    }

    @Override
    protected void cellDecor(int floor, int cx, int cz, List<DecorSpec> out) {
        Feature f = featureAt(floor, cx, cz);
        Style st = style(floor, cx, cz);
        int fy = floorY[floor] + 1;
        long h = ch(floor, cx, cz, 0xDEC0);
        if (f != null) {
            switch (f.id()) {
                case "stairwell" -> out.add(spec("sign_text", cx, cz, 4.5, fy + 1.2, 1.2, 0f, f.seed()));
                case "seam" -> {
                    out.add(spec("peeled_wallpaper", cx, cz, 4.5, fy, 2.95, 180f, f.seed()));
                    out.add(spec("drip_emitter", cx, cz, 4.5, st.ceilY - 0.05, 4.5, 0f, f.seed()));
                    out.add(spec("graffiti", cx, cz, 2.95, fy + 1.2, 4.5, 90f, f.seed()));
                }
                case "exit_door" -> {
                    out.add(spec("exit_sign", cx, cz, 4.5, fy + 2.75, 4.0, 180f, f.seed()));
                    out.add(spec("exit_sign", cx, cz, 4.5, fy + 2.75, 5.0, 0f, f.seed()));
                }
                case "phone_desk" -> out.add(spec("phone_desk", cx, cz, 4.5, fy, 4.5, (float) (Rng.pick(f.seed(), 4) * 90), f.seed())
                        .barrier(cx * S + 4, fy, cz * S + 4));
                case "red_room" -> out.add(spec("office_chair", cx, cz, 4.5, fy, 4.5, 37f, f.seed()));
                case "chair_circle" -> {
                    for (int i = 0; i < 6; i++) {
                        double a = Math.PI * 2 * i / 6;
                        out.add(spec("chair", cx, cz, 4.5 + Math.cos(a) * 2.4, fy, 4.5 + Math.sin(a) * 2.4,
                                (float) Math.toDegrees(Math.atan2(-Math.cos(a), Math.sin(a))) + 180f, Rng.hash(f.seed(), i)));
                    }
                    out.add(spec("hanging_fixture", cx, cz, 4.5, st.ceilY, 4.5, 0f, f.seed()).light(cx * S + 4, st.ceilY - 2, cz * S + 4, 11));
                }
                default -> {
                }
            }
            return;
        }
        double density = gs.get("decor-density", 1.0);
        double r = Rng.unit(h) / Math.max(0.05, density);
        int k = kind(floor, cx, cz);
        if (k == K_HALL) {
            if (r < 0.10) {
                out.add(spec("hanging_fixture", cx, cz, 4.5, st.ceilY, 4.5, (float) Rng.pick(h, 90), h).light(cx * S + 4, st.ceilY - 2, cz * S + 4, 12));
            } else if (r < 0.15) {
                out.add(spec("office_chair", cx, cz, 2.5 + Rng.pick(h, 4), fy, 5.5, (float) Rng.pick(Rng.hash(h, 1), 360), h));
            } else if (r < 0.18) {
                out.add(new DecorSpec("floating_chair", cx * S + 4.5, fy + 2.5, cz * S + 4.5, (float) Rng.pick(h, 360), 25f, 40f, h));
            }
            return;
        }
        int side = (k == K_THICK || k == K_STUB) ? -1 : closedSide(floor, cx, cz, h);
        if (r < 0.05 && side >= 0) {
            double along = 2.5 + Rng.pick(Rng.hash(h, 2), 4);
            double[] p = wallFace(cx, cz, side, along, fy);
            out.add(new DecorSpec("wall_chair", p[0], p[1], p[2], yawAwayFrom(side) + (float) (Rng.unit(Rng.hash(h, 3)) * 30 - 15), 0f, (float) (Rng.unit(Rng.hash(h, 4)) * 20 - 10), h));
        } else if (r < 0.09) {
            out.add(new DecorSpec("sunk_desk", cx * S + 2.5, fy - 0.55, cz * S + 2.5, (float) Rng.pick(h, 360), 0f, -12f, h));
        } else if (r < 0.12) {
            out.add(new DecorSpec("floating_chair", cx * S + 2.5 + Rng.pick(h, 4), fy + 1.6, cz * S + 5.5, (float) Rng.pick(h, 360), 18f, 33f, h));
        } else if (r < 0.17 && k != K_PILLAR) {
            out.add(spec("hanging_fixture", cx, cz, 4.5, st.ceilY, 4.5, 0f, h).light(cx * S + 4, st.ceilY - 2, cz * S + 4, 12));
        } else if (r < 0.21) {
            Door w = west(floor, cx, cz);
            if (w.open() && !w.full()) {
                out.add(new DecorSpec("warped_doorframe:" + w.width(), cx * S + 0.5, fy, cz * S + w.start(), -90f, 0f, 4f, h));
            }
        } else if (r < 0.23) {
            out.add(new DecorSpec("stair_fragment", cx * S + 1.5, fy + 0.8, cz * S + 1.5, 45f, 0f, 9f, h));
        } else if (r < 0.28 && side >= 0) {
            double[] p = wallFace(cx, cz, side, 4.5, st.ceilY - 1.2);
            out.add(new DecorSpec("wall_vent", p[0], p[1], p[2], yawAwayFrom(side), 0f, 0f, h));
        } else if (r < 0.30 && side >= 0) {
            double[] p = wallFace(cx, cz, side, 4.5, fy + 2.4);
            out.add(new DecorSpec("fake_exit_sign", p[0], p[1], p[2], yawAwayFrom(side), 0f, 0f, h));
        } else if (r < 0.32) {
            out.add(new DecorSpec("wet_floor_sign", cx * S + 5.5, fy, cz * S + 2.5, (float) Rng.pick(h, 360), 0f, 0f, h));
        } else if (r < 0.34 && st.ceilY - fy >= 4) {
            out.add(new DecorSpec("dangling_tile", cx * S + 5.5, st.ceilY, cz * S + 5.5, (float) Rng.pick(h, 4) * 90f, 0f, 0f, h));
        } else if (r < 0.36 && side >= 0) {
            double[] p = wallFace(cx, cz, side, 4.5, fy + 2.3);
            out.add(new DecorSpec("wall_clock", p[0], p[1], p[2], yawAwayFrom(side), 0f, 0f, h));
        }
    }

    private int closedSide(int floor, int cx, int cz, long h) {
        int s = Rng.pick(Rng.hash(h, 11), 4);
        for (int i = 0; i < 4; i++, s = (s + 1) % 4) {
            if (closed(floor, cx, cz, s)) {
                Door d = sideDoor(floor, cx, cz, s);
                if (!d.open()) return s;
            }
        }
        return -1;
    }
}
