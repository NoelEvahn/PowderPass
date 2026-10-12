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
 * Level 5 — Terror Hotel. Corridors of patterned wallpaper; maze dead ends become numbered guest rooms behind
 * real doors; ballrooms and dining halls occupy merged regions; a grey service wing threads between them.
 */
public final class Level5Generator extends RoomLevel {
    private static final int FLOOR = 64;
    static final int K_CORRIDOR = 0, K_GUEST = 1, K_BALLROOM = 2, K_DINING = 3, K_SERVICE = 4;
    private static final String[] KINDS = {"corridor", "guest-room", "ballroom", "dining-hall", "service-corridor"};
    private static final B[][] WALLPAPER = {
            {B.of(Material.RED_TERRACOTTA), B.of(Material.BROWN_TERRACOTTA)},
            {B.of(Material.GREEN_TERRACOTTA), B.of(Material.BROWN_TERRACOTTA)},
            {B.of(Material.YELLOW_TERRACOTTA), B.of(Material.ORANGE_TERRACOTTA)},
    };
    private static final String[] NAMES = {"crimson-damask", "emerald-wing", "faded-gold"};
    private static final B WAINSCOT = B.of(Material.STRIPPED_DARK_OAK_WOOD);
    private static final B RAIL = B.of(Material.DARK_OAK_PLANKS);

    public Level5Generator(long seed, GenSettings gs) {
        super(Level.LEVEL_5, seed, gs, 10, 6, 3, new int[]{FLOOR}, FLOOR - 6, FLOOR + 14, 0.12, 0.0);
    }

    @Override
    protected List<FeatureDef> featureDefs() {
        return List.of(
                FeatureDef.transition("grand_lobby", Dest.EXIT, gs.get("exit-chance", 0.6)),
                FeatureDef.transition("old_elevator", Dest.to(Level.LEVEL_4), 1.0).withEffect("dwell"),
                FeatureDef.transition("room_000", Dest.to(Level.LEVEL_0), 1.0),
                FeatureDef.landmark("dancers", gs.get("landmark-chance", 0.6)),
                FeatureDef.landmark("banquet", gs.get("landmark-chance", 0.6) * 0.8),
                FeatureDef.landmark("front_desk", gs.get("landmark-chance", 0.6)));
    }

    @Override
    public String biomeKey() {
        return "minecraft:soul_sand_valley";
    }

    private int regionType(int rx, int rz) {
        long h = Rng.hash(seed ^ 0xBA11L, rx, rz);
        double r = Rng.unit(h);
        double b = gs.get("ballroom-chance", 0.08);
        if (r < b) return K_BALLROOM;
        if (r < b + gs.get("dining-chance", 0.08)) return K_DINING;
        return K_CORRIDOR;
    }

    @Override
    protected boolean mergedRegion(int floor, int rx, int rz) {
        return regionType(rx, rz) != K_CORRIDOR;
    }

    @Override
    protected double[] doorParams(int floor) {
        return new double[]{0.18, 2, 4};
    }

    private boolean service(int cx, int cz) {
        return zone(cx, cz, 0x5E4AL, 60) > 0.74;
    }

    public boolean isGuestRoom(int floor, int cx, int cz) {
        if (merged(floor, cx, cz) || featureAt(floor, cx, cz) != null || service(cx, cz)) return false;
        return mazes[floor].degree(cx, cz) == 1 && degree(floor, cx, cz) == 1
                && Rng.chance(ch(floor, cx, cz, 0x6E57), gs.get("guest-room-chance", 0.8));
    }

    @Override
    public Door door(int floor, int cx, int cz, boolean east) {
        Door d = super.door(floor, cx, cz, east);
        if (d.open() && (isGuestRoom(floor, cx, cz) || isGuestRoom(floor, east ? cx + 1 : cx, east ? cz : cz + 1))) {
            return new Door(true, S / 2, 1, false);
        }
        return d;
    }

    public int kind(int floor, int cx, int cz) {
        if (merged(floor, cx, cz)) return regionType(Math.floorDiv(cx, R), Math.floorDiv(cz, R));
        if (isGuestRoom(floor, cx, cz)) return K_GUEST;
        if (service(cx, cz)) return K_SERVICE;
        return K_CORRIDOR;
    }

    @Override
    protected Style style(int floor, int cx, int cz) {
        Style st = new Style();
        int p = Math.min(2, (int) (zone(cx, cz, 0x5A1L, 90) * 3));
        int k = kind(floor, cx, cz);
        st.name = NAMES[p];
        st.under = B.of(Material.STONE);
        st.above = B.of(Material.STONE);
        st.wall = WALLPAPER[p][0];
        st.wallAlt = WALLPAPER[p][1];
        st.base = WAINSCOT;
        st.trim = RAIL;
        st.floor = B.of(Material.RED_WOOL);
        st.floorAlt = B.of(Material.YELLOW_WOOL);
        st.ceil = B.of(Material.DARK_OAK_PLANKS);
        st.ceilAlt = B.of(Material.SPRUCE_PLANKS);
        st.light = null;
        st.ceilY = FLOOR + 6;
        st.doorH = 3;
        if (k == K_SERVICE) {
            st.name = "service-wing";
            st.wall = B.of(Material.LIGHT_GRAY_CONCRETE);
            st.wallAlt = null;
            st.base = B.of(Material.GRAY_CONCRETE);
            st.trim = null;
            st.floor = B.of(Material.SMOOTH_STONE);
            st.floorAlt = B.of(Material.GRAY_CONCRETE);
            st.ceil = B.of(Material.GRAY_CONCRETE);
            st.ceilAlt = null;
        } else if (k == K_BALLROOM) {
            st.name = "ballroom";
            st.ceilY = FLOOR + 11;
            st.keepCorner = true;
            st.pillar2x2 = true;
            st.base = B.of(Material.POLISHED_BLACKSTONE);
            st.trim = B.of(Material.GOLD_BLOCK);
            st.ceilAlt = B.of(Material.YELLOW_TERRACOTTA);
        } else if (k == K_DINING) {
            st.name = "dining-hall";
            st.ceilY = FLOOR + 7;
            st.keepCorner = true;
        }
        return st;
    }

    @Override
    protected B wallBlock(CellCtx c, Style st, int u, int y, int v, int along) {
        if (y == c.fy + 2 && st.base == WAINSCOT) {
            return RAIL;
        }
        if (y > c.fy + 2 && y < st.ceilY - 1 && st.wallAlt != null) {
            int a = c.wx(u) + c.wz(v);
            return (Math.floorMod(a + y, 4) == 0 || Math.floorMod(a - y, 4) == 0) ? st.wallAlt : st.wall;
        }
        return super.wallBlock(c, st, u, y, v, along);
    }

    @Override
    protected B floorBlock(CellCtx c, Style st, int u, int v) {
        int k = kind(c.floor, c.cx, c.cz);
        int wx = c.wx(u), wz = c.wz(v);
        return switch (k) {
            case K_BALLROOM -> ((wx + wz) & 1) == 0 ? B.of(Material.BLACK_CONCRETE) : B.of(Material.WHITE_CONCRETE);
            case K_DINING -> ((wx + wz) & 1) == 0 ? B.of(Material.DARK_OAK_PLANKS) : B.of(Material.SPRUCE_PLANKS);
            case K_SERVICE -> super.floorBlock(c, st, u, v);
            case K_GUEST -> (u == 1 || v == 1 || u == S - 1 || v == S - 1) ? B.of(Material.RED_WOOL) : B.of(Material.BROWN_WOOL);
            default -> {
                if (u == 0 || v == 0 || u == 1 || v == 1 || u == S - 1 || v == S - 1) yield B.of(Material.DARK_OAK_PLANKS);
                if (u == 2 || v == 2 || u == S - 2 || v == S - 2) yield B.of(Material.YELLOW_WOOL);
                yield B.of(Material.RED_WOOL);
            }
        };
    }

    @Override
    protected void wallColumn(CellCtx c, Style st, int u, int v, int along, Door door) {
        super.wallColumn(c, st, u, v, along, door);
        if (door != null && door.covers(along) && door.width() == 1 && c.in(u, v)) {
            int ncx = u == 0 ? c.cx - 1 : c.cx, ncz = u == 0 ? c.cz : c.cz - 1;
            if (isGuestRoom(c.floor, c.cx, c.cz) || isGuestRoom(c.floor, ncx, ncz)) {
                String facing = u == 0 ? "east" : "south";
                c.set(u, c.fy + 1, v, B.of(Material.DARK_OAK_DOOR, "[facing=" + facing + ",half=lower,hinge=left,open=false]"));
                c.set(u, c.fy + 2, v, B.of(Material.DARK_OAK_DOOR, "[facing=" + facing + ",half=upper,hinge=left,open=false]"));
                c.set(u, c.fy + 3, v, RAIL);
            }
        }
    }

    @Override
    public String zoneName(int floor, int cx, int cz) {
        return style(floor, cx, cz).name + " kind=" + KINDS[kind(floor, cx, cz)];
    }

    @Override
    public boolean isSafeCell(int floor, int cx, int cz) {
        int k = kind(floor, cx, cz);
        return k == K_CORRIDOR || k == K_SERVICE;
    }

    /** Centre of the nearest guest room to (x,z) (feet coordinates), or null. */
    public int[] guestRoomNear(int x, int z, long salt) {
        int ccx = cellOf(x), ccz = cellOf(z);
        int ox = Rng.range(salt, -8, 8), oz = Rng.range(Rng.hash(salt, 1), -8, 8);
        for (int r = 0; r <= 10; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int cx = ccx + ox + dx, cz = ccz + oz + dz;
                    if (isGuestRoom(0, cx, cz)) return new int[]{cx * S + S / 2, FLOOR + 1, cz * S + S / 2};
                }
            }
        }
        return null;
    }

    @Override
    protected void interior(CellCtx c, Style st) {
        int k = kind(c.floor, c.cx, c.cz);
        switch (k) {
            case K_GUEST -> {
                int side = guestBedSide(c.floor, c.cx, c.cz);
                int[] w1 = sideToUV((side + 1) % 4, 2, 1);
                c.set(w1[0], c.fy + 1, w1[1], B.of(Material.DARK_OAK_PLANKS));
                c.set(w1[0], c.fy + 2, w1[1], B.of(Material.DARK_OAK_PLANKS));
                int[] w2 = sideToUV((side + 1) % 4, 3, 1);
                c.set(w2[0], c.fy + 1, w2[1], B.of(Material.DARK_OAK_PLANKS));
                c.set(w2[0], c.fy + 2, w2[1], B.of(Material.DARK_OAK_PLANKS));
                int[] ns = sideToUV(side, 2, 1);
                c.set(ns[0], c.fy + 1, ns[1], B.of(Material.BARREL, "[facing=up]"));
                c.set(S / 2, st.ceilY - 1, S / 2, B.of(Material.LANTERN, "[hanging=true]"));
            }
            case K_BALLROOM -> {
                for (int u = 2; u < S; u += 4) {
                    for (int v = 2; v < S; v += 4) {
                        c.set(u, st.ceilY - 2, v, B.light(13));
                    }
                }
            }
            case K_DINING -> {
                for (int u = 3; u <= 6; u++) {
                    for (int v : new int[]{3, 6}) {
                        c.set(u, c.fy + 1, v, B.of(Material.DARK_OAK_FENCE));
                        c.set(u, c.fy + 2, v, B.of(Material.WHITE_CARPET));
                    }
                }
                c.set(S / 2, st.ceilY - 1, S / 2, B.of(Material.LANTERN, "[hanging=true]"));
            }
            default -> lanterns(c, st, k == K_SERVICE ? 0.5 : 0.85);
        }
    }

    private void lanterns(CellCtx c, Style st, double lit) {
        for (int u = Math.max(1, c.u0); u <= c.u1; u++) {
            for (int v = Math.max(1, c.v0); v <= c.v1; v++) {
                if (Math.floorMod(c.wx(u), 5) == 2 && Math.floorMod(c.wz(v), 5) == 2 && c.type(u, st.ceilY - 1, v) == Material.AIR
                        && Rng.chance(c.h(u, 0, v, 0x1A47), lit)) {
                    c.set(u, st.ceilY - 1, v, B.of(Material.LANTERN, "[hanging=true]"));
                }
            }
        }
    }

    private int guestBedSide(int floor, int cx, int cz) {
        for (int s = 0; s < 4; s++) {
            if (!closed(floor, cx, cz, s) && closed(floor, cx, cz, (s + 2) % 4)) return (s + 2) % 4;
        }
        return 0;
    }

    @Override
    protected void feature(CellCtx c, Style st, Feature f) {
        switch (f.id()) {
            case "grand_lobby" -> {
                for (int u = Math.max(1, c.u0); u <= c.u1; u++) {
                    for (int v = Math.max(1, c.v0); v <= c.v1; v++) {
                        c.set(u, c.fy, v, ((u + v) & 1) == 0 ? B.of(Material.POLISHED_DIORITE) : B.of(Material.CALCITE));
                    }
                }
                for (int u = 2; u <= 7; u++) {
                    c.set(u, c.fy + 1, 2, B.of(Material.DARK_OAK_PLANKS));
                    c.set(u, c.fy + 2, 2, B.slab(Material.DARK_OAK_SLAB, false));
                }
                c.set(5, c.fy, 5, B.of(Material.GOLD_BLOCK));
                c.set(5, st.ceilY - 1, 5, B.light(14));
            }
            case "old_elevator" -> booth(c, B.of(Material.WAXED_COPPER_BLOCK), B.of(Material.DARK_OAK_PLANKS), B.of(Material.WAXED_CUT_COPPER), B.of(Material.SHROOMLIGHT));
            case "room_000" -> booth(c, B.of(Material.DARK_OAK_PLANKS), B.of(Material.YELLOW_TERRACOTTA), B.of(Material.DARK_OAK_PLANKS), B.of(Material.OCHRE_FROGLIGHT));
            case "dancers" -> {
                for (int u = Math.max(1, c.u0); u <= c.u1; u++) {
                    for (int v = Math.max(1, c.v0); v <= c.v1; v++) {
                        c.set(u, c.fy, v, ((u + v) & 1) == 0 ? B.of(Material.BLACK_CONCRETE) : B.of(Material.WHITE_CONCRETE));
                    }
                }
                c.set(5, st.ceilY - 2, 5, B.light(10));
            }
            case "banquet" -> {
                for (int u = 2; u <= 7; u++) {
                    c.set(u, c.fy + 1, 5, B.of(Material.DARK_OAK_FENCE));
                    c.set(u, c.fy + 2, 5, B.of(Material.WHITE_CARPET));
                }
                c.set(5, st.ceilY - 1, 3, B.of(Material.LANTERN, "[hanging=true]"));
            }
            case "front_desk" -> {
                for (int u = 3; u <= 6; u++) {
                    c.set(u, c.fy + 1, 3, B.of(Material.DARK_OAK_PLANKS));
                    c.set(u, c.fy + 2, 3, B.slab(Material.DARK_OAK_SLAB, false));
                }
                lanterns(c, st, 1.0);
            }
            default -> {
            }
        }
    }

    @Override
    protected int[] triggerBox(Feature f) {
        return switch (f.id()) {
            case "grand_lobby" -> new int[]{4, 0, 4, 6, 1, 6};
            case "old_elevator", "room_000" -> boothTrigger();
            default -> null;
        };
    }

    @Override
    protected void cellDecor(int floor, int cx, int cz, List<DecorSpec> out) {
        Feature f = featureAt(floor, cx, cz);
        Style st = style(floor, cx, cz);
        int fy = floorY[floor] + 1;
        long h = ch(floor, cx, cz, 0xDEC6);
        if (f != null) {
            switch (f.id()) {
                case "grand_lobby" -> {
                    out.add(spec("revolving_door", cx, cz, 5.5, fy, 5.5, 0f, f.seed()));
                    out.add(spec("chandelier", cx, cz, 5.5, st.ceilY, 2.5, 0f, f.seed()));
                    out.add(spec("reception_bell", cx, cz, 4.5, fy + 1.5, 2.5, 0f, f.seed()));
                    out.add(spec("key_board", cx, cz, 4.5, fy + 1.4, 1.02, 0f, f.seed()));
                }
                case "old_elevator" -> {
                    out.add(spec("elevator_doors", cx, cz, 4.5, fy, 6.0, 0f, f.seed()));
                    out.add(spec("elevator_dial", cx, cz, 4.5, fy + 3.4, 6.02, 0f, f.seed()));
                }
                case "room_000" -> out.add(spec("room_number", cx, cz, 4.5, fy + 2.6, 6.02, 0f, 0L));
                case "dancers" -> {
                    out.add(spec("dancers", cx, cz, 5.0, fy, 5.0, 0f, f.seed()));
                    out.add(spec("chandelier", cx, cz, 5.0, st.ceilY, 5.0, 0f, f.seed()));
                    out.add(spec("gramophone", cx, cz, 1.6, fy, 1.6, 135f, f.seed()).barrier(cx * S + 1, fy, cz * S + 1));
                }
                case "banquet" -> {
                    for (int i = 0; i < 6; i++) {
                        out.add(spec("dining_chair", cx, cz, 2.5 + i, fy, 4.3, 180f, Rng.hash(f.seed(), i)));
                        out.add(spec("dining_chair", cx, cz, 2.5 + i, fy, 6.7, 0f, Rng.hash(f.seed(), i + 7)));
                    }
                    out.add(spec("candelabra", cx, cz, 4.5, fy + 1.07, 5.5, 0f, f.seed()));
                }
                case "front_desk" -> {
                    out.add(spec("reception_bell", cx, cz, 4.5, fy + 1.5, 3.5, 0f, f.seed()));
                    out.add(spec("key_board", cx, cz, 4.5, fy + 1.4, 1.02, 0f, f.seed()));
                    out.add(spec("grandfather_clock", cx, cz, 8.5, fy, 1.6, 0f, f.seed()).barrier(cx * S + 8, fy, cz * S + 1, cx * S + 8, fy + 1, cz * S + 1));
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
            case K_GUEST -> {
                int side = guestBedSide(floor, cx, cz);
                int[] bed = sideToUV(side, S / 2, 1);
                float yaw = yawAwayFrom(side);
                int[] bed2 = sideToUV(side, S / 2, 2);
                out.add(spec("hotel_bed", cx, cz, bed[0] + 0.5, fy, bed[1] + 0.5, yaw, h)
                        .barrier(cx * S + bed[0], fy, cz * S + bed[1], cx * S + bed2[0], fy, cz * S + bed2[1]));
                int[] chair = sideToUV((side + 3) % 4, 3, 2);
                out.add(spec("armchair", cx, cz, chair[0] + 0.5, fy, chair[1] + 0.5, yawAwayFrom((side + 3) % 4), h));
                int[] lamp = sideToUV(side, 2, 1);
                out.add(spec("table_lamp", cx, cz, lamp[0] + 0.5, fy + 1.0, lamp[1] + 0.5, 0f, h));
                Door d = null;
                int dside = -1;
                for (int s = 0; s < 4; s++) {
                    if (!closed(floor, cx, cz, s)) {
                        d = sideDoor(floor, cx, cz, s);
                        dside = s;
                    }
                }
                if (d != null) {
                    double[] p = wallFace(cx, cz, dside, d.start() + 0.5, fy + 2.35);
                    // number plate on the corridor side of the door wall
                    double nx = p[0], nz = p[2];
                    switch (dside) {
                        case 0 -> nx -= 1.04;
                        case 1 -> nz -= 1.04;
                        case 2 -> nx += 1.04;
                        default -> nz += 1.04;
                    }
                    out.add(new DecorSpec("room_number", nx, p[1], nz, yawAwayFrom(dside) + 180f, 0f, 0f, h));
                }
            }
            case K_BALLROOM -> {
                if (Rng.chance(Rng.hash(h, 1), 0.3 * density)) out.add(spec("chandelier", cx, cz, 5.0, st.ceilY, 5.0, 0f, h));
                if (r < 0.06) out.add(spec("piano", cx, cz, 6.5, fy, 6.5, 200f, h).barrier(cx * S + 6, fy, cz * S + 6, cx * S + 7, fy, cz * S + 6));
            }
            case K_DINING -> {
                out.add(spec("candelabra", cx, cz, 4.5, fy + 1.07, 3.5, 0f, h));
                if (r < 0.5) {
                    for (int i = 0; i < 2; i++) out.add(spec("dining_chair", cx, cz, 3.5 + i * 2, fy, 2.3, 0f, Rng.hash(h, i)));
                }
            }
            case K_SERVICE -> {
                if (r < 0.15) out.add(spec("service_cart", cx, cz, 2.5, fy, 7.5, (float) Rng.pick(h, 360), h).barrier(cx * S + 2, fy, cz * S + 7));
                else if (r < 0.25) out.add(spec("cage_lamp", cx, cz, 5.5, st.ceilY, 5.5, 0f, h).light(cx * S + 5, st.ceilY - 2, cz * S + 5, 10));
            }
            default -> {
                int side = -1;
                for (int s = 0; s < 4; s++) if (closed(floor, cx, cz, s)) side = s;
                if (r < 0.3 && side >= 0) {
                    double[] p = wallFace(cx, cz, side, 3.5, fy + 2.0);
                    int[] lp = sideToUV(side, 3, 1);
                    out.add(new DecorSpec("wall_sconce", p[0], p[1], p[2], yawAwayFrom(side), 0f, 0f, h).light(cx * S + lp[0], fy + 2, cz * S + lp[1], 11));
                } else if (r < 0.42 && side >= 0) {
                    double[] p = wallFace(cx, cz, side, 5.0, fy + 1.6);
                    out.add(new DecorSpec("portrait", p[0], p[1], p[2], yawAwayFrom(side), 0f, 0f, h));
                } else if (r < 0.48 && side >= 0) {
                    int[] uv = sideToUV(side, 7, 1);
                    out.add(new DecorSpec("grandfather_clock", cx * S + uv[0] + 0.5, fy, cz * S + uv[1] + 0.5, yawAwayFrom(side), 0f, 0f, h)
                            .barrier(cx * S + uv[0], fy, cz * S + uv[1], cx * S + uv[0], fy + 1, cz * S + uv[1]));
                } else if (r < 0.55 && side >= 0) {
                    int[] uv = sideToUV(side, 2, 1);
                    out.add(new DecorSpec("armchair", cx * S + uv[0] + 0.5, fy, cz * S + uv[1] + 0.5, yawAwayFrom(side), 0f, 0f, h));
                } else if (r < 0.57) {
                    out.add(spec("chandelier", cx, cz, 5.0, st.ceilY, 5.0, 0f, h).light(cx * S + 5, st.ceilY - 3, cz * S + 5, 12));
                }
            }
        }
    }
}
