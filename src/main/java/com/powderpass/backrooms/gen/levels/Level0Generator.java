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
import com.powderpass.backrooms.util.ValueNoise;
import org.bukkit.Material;

import java.util.List;

/**
 * Level 0 — The Lobby. The only maze. Four materials: wallpaper, carpet, a darker stain, ceiling tile.
 * Every light is a display fluorescent panel; whole sectors have simply stopped working.
 */
public final class Level0Generator extends RoomLevel {
    private static final int FLOOR = 64;
    static final int K_PLAIN = 0, K_THICK = 1, K_STUB = 2, K_PILLAR = 3, K_HALL = 4;
    private static final String[] KINDS = {"room", "thick-wall", "stub-wall", "pillar", "hall"};

    private static final B WALL = B.of(Material.SMOOTH_SANDSTONE);
    private static final B CARPET = B.of(Material.YELLOW_TERRACOTTA);
    private static final B STAIN = B.of(Material.BROWN_TERRACOTTA);
    private static final B CEILING = B.of(Material.WHITE_TERRACOTTA);
    private static final B UNDER = B.of(Material.STONE);

    public Level0Generator(long seed, GenSettings gs) {
        super(Level.LEVEL_0, seed, gs, 8, 8, 3, new int[]{FLOOR}, FLOOR - 8, FLOOR + 14, 0.16, gs.get("repeat-chance", 0.06));
    }

    @Override
    protected List<FeatureDef> featureDefs() {
        return List.of(
                FeatureDef.transition("stairwell", Dest.to(Level.LEVEL_1), 1.0),
                FeatureDef.transition("seam", Dest.to(Level.POOLROOMS), 1.0).withEffect("intent"),
                FeatureDef.transition("outside_door", Dest.to(Level.PLAINS), gs.get("plains-door-chance", 0.6)),
                FeatureDef.transition("exit_door", Dest.EXIT, gs.get("exit-chance", 0.3)),
                FeatureDef.landmark("phone_desk", gs.get("landmark-chance", 0.6)),
                FeatureDef.landmark("chair_circle", gs.get("landmark-chance", 0.6) * 0.6));
    }

    @Override
    public String biomeKey() {
        return "minecraft:swamp";
    }

    @Override
    protected boolean mergedRegion(int floor, int rx, int rz) {
        return Rng.chance(Rng.hash(seed ^ 0x4A11L, rx, rz), gs.get("hall-chance", 0.08));
    }

    @Override
    protected double[] doorParams(int floor) {
        return new double[]{0.28, 1, 4};
    }

    private boolean dark(int cx, int cz) {
        return zone(cx, cz, 0xDA4CL, 90) > 1.0 - gs.get("dark-zone-chance", 0.2) * 0.6;
    }

    private boolean low(int cx, int cz) {
        return zone(cx, cz, 0x10E1L, 64) > 0.77;
    }

    private boolean damp(int cx, int cz) {
        return zone(cx, cz, 0x9A1L, 72) > 0.72;
    }

    int kind(int floor, int cx, int cz) {
        if (merged(floor, cx, cz)) return K_HALL;
        double r = Rng.unit(ch(floor, cx, cz, 0x4B1D));
        if (r < gs.get("thick-wall-chance", 0.12)) return K_THICK;
        if (r < 0.24) return K_STUB;
        if (r < 0.29) return K_PILLAR;
        return K_PLAIN;
    }

    private int ceilY(int floor, int cx, int cz) {
        return merged(floor, cx, cz) ? FLOOR + 9 : low(cx, cz) ? FLOOR + 4 : FLOOR + 5;
    }

    @Override
    protected Style style(int floor, int cx, int cz) {
        Style st = new Style();
        st.name = dark(cx, cz) ? "dead-sector" : damp(cx, cz) ? "damp" : "lobby";
        st.under = UNDER;
        st.above = UNDER;
        st.wall = WALL;
        st.floor = CARPET;
        st.floorAlt = STAIN;
        st.floorAltChance = damp(cx, cz) ? 0.12 : 0.03;
        st.ceil = CEILING;
        st.ceilY = ceilY(floor, cx, cz);
        st.doorH = low(cx, cz) ? 2 : 3;
        if (merged(floor, cx, cz)) {
            st.keepCorner = true;
            st.pillar2x2 = Math.floorMod(cx + cz, 2) == 0;
            st.name = "pillar-hall";
        }
        return st;
    }

    @Override
    public String zoneName(int floor, int cx, int cz) {
        return style(floor, cx, cz).name + (low(cx, cz) ? "/low" : "") + " " + KINDS[kind(floor, cx, cz)];
    }

    @Override
    public boolean isSafeCell(int floor, int cx, int cz) {
        return kind(floor, cx, cz) != K_PILLAR && !low(cx, cz);
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
                                for (int y = c.fy + 1; y < st.ceilY; y++) c.set(uv[0], y, uv[1], WALL);
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
                            for (int y = c.fy + 1; y < st.ceilY; y++) c.set(uv[0], y, uv[1], WALL);
                        }
                        break;
                    }
                }
            }
            case K_PILLAR -> {
                for (int u = 3; u <= 4; u++) for (int v = 3; v <= 4; v++) for (int y = c.fy + 1; y < st.ceilY; y++) c.set(u, y, v, WALL);
            }
            default -> {
            }
        }
        if (damp(c.cx, c.cz) && k != K_HALL) {
            for (int u = Math.max(1, c.u0); u <= c.u1; u++) {
                for (int v = Math.max(1, c.v0); v <= c.v1; v++) {
                    if (u >= 3 && u <= 5 && v >= 3 && v <= 5) continue;
                    if (ValueNoise.sample(seed ^ 0x9D, c.wx(u) / 3.0, c.wz(v) / 3.0) > 0.8 && c.type(u, c.fy + 1, v) == Material.AIR) {
                        c.set(u, c.fy, v, B.WATER);
                        c.set(u, c.fy - 1, v, STAIN);
                    }
                }
            }
        }
    }

    @Override
    protected void feature(CellCtx c, Style st, Feature f) {
        switch (f.id()) {
            case "stairwell" -> stairPit(c, st, B.of(Material.GRAY_CONCRETE), B.of(Material.GRAY_CONCRETE), Material.ANDESITE_STAIRS, B.light(9));
            case "seam" -> {
                B tile = B.of(Material.WHITE_CONCRETE);
                for (int u = 2; u <= 6; u++) for (int v = 2; v <= 6; v++) c.set(u, c.fy, v, tile);
                for (int u = 3; u <= 5; u++) for (int v = 3; v <= 5; v++) for (int y = c.fy + 1; y < st.ceilY; y++) c.set(u, y, v, tile);
            }
            case "exit_door", "outside_door" -> {
                for (int u = 3; u <= 5; u++) for (int y = c.fy + 1; y <= c.fy + 3; y++) c.set(u, y, 4, WALL);
                Material door = f.id().equals("exit_door") ? Material.DARK_OAK_DOOR : Material.SPRUCE_DOOR;
                c.set(4, c.fy + 1, 4, B.of(door, "[facing=south,half=lower,hinge=left,open=false]"));
                c.set(4, c.fy + 2, 4, B.of(door, "[facing=south,half=upper,hinge=left,open=false]"));
                if (f.id().equals("outside_door")) {
                    c.set(4, c.fy, 3, B.of(Material.GRASS_BLOCK));
                    c.set(4, c.fy, 5, B.of(Material.GRASS_BLOCK));
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
            case "exit_door", "outside_door" -> new int[]{4, 0, 4, 4, 1, 4};
            default -> null;
        };
    }

    @Override
    protected void cellDecor(int floor, int cx, int cz, List<DecorSpec> out) {
        Feature f = featureAt(floor, cx, cz);
        int ceil = ceilY(floor, cx, cz);
        int fy = floorY[floor] + 1;
        long h = ch(floor, cx, cz, 0xDEC0);
        int k = kind(floor, cx, cz);
        double lit = dark(cx, cz) ? 0.06 : 0.9;
        // fluorescent panels: two per room, one per hall cell (halls are dimmer, on purpose)
        if (k == K_HALL) {
            out.add(lamp("ceiling_panel", cx * S + 4.5, ceil, cz * S + 4.5, 0f, h, lampState(Rng.hash(h, 40), lit * 0.8),
                    cx * S + 4, ceil - 1, cz * S + 4, 15));
        } else {
            for (int i = 0; i < 2; i++) {
                int u = i == 0 ? 2 : 6;
                out.add(lamp("ceiling_panel", cx * S + u + 0.5, ceil, cz * S + 4.5, 0f, Rng.hash(h, 41 + i), lampState(Rng.hash(h, 41 + i), lit),
                        cx * S + u, ceil - 1, cz * S + 4, 14));
            }
        }
        if (f != null) {
            switch (f.id()) {
                case "stairwell" -> {
                    out.add(spec("sign_text", cx, cz, 4.5, fy + 1.2, 1.2, 0f, f.seed()));
                    out.add(spec("wall_lamp", cx, cz, 4.5, fy - 1.5, 6.98, 180f, f.seed()));
                }
                case "seam" -> {
                    out.add(spec("drip_emitter", cx, cz, 4.5, ceil - 0.05, 4.5, 0f, f.seed()));
                    out.add(spec("graffiti", cx, cz, 2.95, fy + 1.2, 4.5, 90f, f.seed()));
                }
                case "exit_door" -> {
                    out.add(spec("exit_sign", cx, cz, 4.5, fy + 2.75, 4.0, 180f, f.seed()));
                    out.add(spec("exit_sign", cx, cz, 4.5, fy + 2.75, 5.0, 0f, f.seed()));
                }
                case "outside_door" -> out.add(spec("daylight_door", cx, cz, 4.5, fy, 4.5, 0f, f.seed()));
                case "phone_desk" -> out.add(spec("phone_desk", cx, cz, 4.5, fy, 4.5, (float) (Rng.pick(f.seed(), 4) * 90), f.seed())
                        .barrier(cx * S + 4, fy, cz * S + 4));
                case "chair_circle" -> {
                    for (int i = 0; i < 6; i++) {
                        double a = Math.PI * 2 * i / 6;
                        out.add(spec("chair", cx, cz, 4.5 + Math.cos(a) * 2.4, fy, 4.5 + Math.sin(a) * 2.4,
                                (float) Math.toDegrees(Math.atan2(-Math.cos(a), Math.sin(a))) + 180f, Rng.hash(f.seed(), i)));
                    }
                }
                default -> {
                }
            }
            return;
        }
        // sparse props: most rooms are simply empty, which is the point
        double r = Rng.unit(h) / Math.max(0.05, gs.get("decor-density", 1.0));
        if (k == K_HALL) {
            if (r < 0.05) out.add(spec("office_chair", cx, cz, 2.5 + Rng.pick(h, 4), fy, 5.5, (float) Rng.pick(Rng.hash(h, 1), 360), h));
            return;
        }
        int side = (k == K_THICK || k == K_STUB) ? -1 : closedSide(floor, cx, cz, h);
        if (r < 0.03 && side >= 0) {
            double[] p = wallFace(cx, cz, side, 2.5 + Rng.pick(Rng.hash(h, 2), 4), fy);
            out.add(new DecorSpec("wall_chair", p[0], p[1], p[2], yawAwayFrom(side) + (float) (Rng.unit(Rng.hash(h, 3)) * 30 - 15), 0f, 8f, h));
        } else if (r < 0.05) {
            out.add(new DecorSpec("floating_chair", cx * S + 2.5 + Rng.pick(h, 4), fy + 1.6, cz * S + 5.5, (float) Rng.pick(h, 360), 18f, 33f, h));
        } else if (r < 0.08) {
            Door w = west(floor, cx, cz);
            if (w.open() && !w.full()) out.add(new DecorSpec("warped_doorframe:" + w.width(), cx * S + 0.5, fy, cz * S + w.start(), -90f, 0f, 4f, h));
        } else if (r < 0.095 && side >= 0) {
            double[] p = wallFace(cx, cz, side, 4.5, fy + 2.4);
            out.add(new DecorSpec("fake_exit_sign", p[0], p[1], p[2], yawAwayFrom(side), 0f, 0f, h));
        } else if (r < 0.11) {
            out.add(new DecorSpec("wet_floor_sign", cx * S + 5.5, fy, cz * S + 2.5, (float) Rng.pick(h, 360), 0f, 0f, h));
        } else if (r < 0.125 && ceil - fy >= 4) {
            out.add(new DecorSpec("dangling_tile", cx * S + 5.5, ceil, cz * S + 6.5, (float) Rng.pick(h, 4) * 90f, 0f, 0f, h));
        } else if (r < 0.135) {
            out.add(new DecorSpec("stair_fragment", cx * S + 1.5, fy + 0.8, cz * S + 1.5, 45f, 0f, 9f, h));
        } else if (r < 0.145 && side >= 0) {
            double[] p = wallFace(cx, cz, side, 4.5, fy + 2.3);
            out.add(new DecorSpec("wall_clock", p[0], p[1], p[2], yawAwayFrom(side), 0f, 0f, h));
        }
    }

    private int closedSide(int floor, int cx, int cz, long h) {
        int s = Rng.pick(Rng.hash(h, 11), 4);
        for (int i = 0; i < 4; i++, s = (s + 1) % 4) {
            if (closed(floor, cx, cz, s)) return s;
        }
        return -1;
    }
}
