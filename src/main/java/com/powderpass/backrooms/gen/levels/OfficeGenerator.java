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
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Level 4 — Abandoned Office. Not a maze: city blocks. Every 48x48 block is ringed by corridors (each block owns
 * its north and west strip) and recursively split by more corridors into rooms (binary space partitioning).
 * Every room's sides face corridors, and every room gets a door on one of them, so the floor is connected by
 * construction. Rooms become cubicle farms, meeting rooms, break rooms, offices or closets by size.
 */
public final class OfficeGenerator extends LevelGenerator {
    private static final int FLOOR = 64;
    private static final int CW = 3; // corridor width
    static final byte CORRIDOR = 0, WALL = 1, ROOM = 2, DOOR = 3, GLASS = 4;
    static final int T_FARM = 0, T_MEETING = 1, T_BREAK = 2, T_OFFICE = 3, T_CLOSET = 4, T_STORAGE = 5;
    private static final String[] TYPES = {"cubicle-farm", "meeting-room", "break-room", "office", "closet", "storage"};

    private static final B WALL_B = com.powderpass.backrooms.gen.CustomBlocks.get("office_wall");
    private static final B CARPET = com.powderpass.backrooms.gen.CustomBlocks.get("office_carpet");
    private static final B CARPET_BLUE = com.powderpass.backrooms.gen.CustomBlocks.get("office_carpet_b");
    private static final B CARPET_TILE = B.of(Material.LIGHT_GRAY_WOOL);
    private static final B HALL_CARPET = com.powderpass.backrooms.gen.CustomBlocks.get("office_carpet");
    private static final B TRIM = B.of(Material.LIGHT_GRAY_CONCRETE);
    private static final B CEIL = com.powderpass.backrooms.gen.CustomBlocks.get("office_ceiling");
    private static final B GLASS_B = B.of(Material.LIGHT_GRAY_STAINED_GLASS);
    private static final B PART = B.of(Material.LIGHT_GRAY_WOOL);
    private static final B PART_TOP = B.slab(Material.SMOOTH_STONE_SLAB, false);
    private static final B DESK = B.slab(Material.SPRUCE_SLAB, true);
    private static final B UNDER = B.of(Material.STONE);

    /** A room: interior rectangle (inclusive), wall ring around it, door position + side, type. */
    record Room(int x0, int z0, int x1, int z1, int doorU, int doorV, int doorSide, int type, boolean lit) {
        int area() {
            return (x1 - x0 + 1) * (z1 - z0 + 1);
        }

        boolean contains(int u, int v) {
            return u >= x0 && u <= x1 && v >= z0 && v <= z1;
        }
    }

    record Layout(byte[] cells, List<Room> rooms) {
    }

    private final Map<Long, Layout> cache = new ConcurrentHashMap<>();

    public OfficeGenerator(long seed, GenSettings gs) {
        super(Level.LEVEL_4, seed, gs, 48, 3, 2, new int[]{FLOOR}, FLOOR - 6, FLOOR + 8, 0.0, 0.0);
    }

    @Override
    protected List<FeatureDef> featureDefs() {
        return List.of(
                // onward to the Plains through a far too bright window in every district; back down the stairs rarer
                FeatureDef.transition("bright_window", Dest.to(Level.PLAINS), 1.0),
                FeatureDef.transition("stairwell", Dest.to(Level.LEVEL_1), gs.get("back-chance", 0.4)),
                FeatureDef.landmark("conference_hall", gs.get("landmark-chance", 0.5)));
    }

    @Override
    public String biomeKey() {
        return "minecraft:the_void";
    }

    // ------------------------------------------------------------------ layout (BSP), cached per block

    Layout layout(int cx, int cz) {
        long key = ((long) cx << 32) ^ (cz & 0xffffffffL);
        Layout l = cache.get(key);
        if (l == null) {
            if (cache.size() > 512) cache.clear();
            l = build(cx, cz);
            cache.put(key, l);
        }
        return l;
    }

    private Layout build(int cx, int cz) {
        byte[] cells = new byte[S * S];
        java.util.Arrays.fill(cells, WALL);
        for (int u = 0; u < S; u++) for (int v = 0; v < S; v++) if (u < CW || v < CW) cells[v * S + u] = CORRIDOR;
        SplittableRandom rnd = new SplittableRandom(ch(0, cx, cz, 0xB5B));
        List<int[]> leaves = new ArrayList<>();
        split(cells, CW, CW, S - 1, S - 1, rnd, leaves, 0);
        List<Room> rooms = new ArrayList<>();
        Feature f = featureAt(0, cx, cz);
        int featureLeaf = -1;
        if (f != null) {
            int best = -1;
            for (int i = 0; i < leaves.size(); i++) {
                int[] q = leaves.get(i);
                int a = (q[2] - q[0] - 1) * (q[3] - q[1] - 1);
                if (a > best) {
                    best = a;
                    featureLeaf = i;
                }
            }
        }
        for (int i = 0; i < leaves.size(); i++) {
            int[] q = leaves.get(i); // outer rect incl. wall ring
            int x0 = q[0] + 1, z0 = q[1] + 1, x1 = q[2] - 1, z1 = q[3] - 1;
            long h = Rng.hash(ch(0, cx, cz, 0x500), i);
            int area = (x1 - x0 + 1) * (z1 - z0 + 1);
            int type;
            double r = Rng.unit(Rng.hash(h, 1));
            if (area >= 200) type = r < 0.75 ? T_FARM : T_STORAGE;
            else if (area >= 70) type = r < 0.4 ? T_MEETING : r < 0.7 ? T_BREAK : T_OFFICE;
            else type = r < 0.6 ? T_OFFICE : r < 0.85 ? T_CLOSET : T_STORAGE;
            if (i == featureLeaf) type = T_MEETING;
            // door: first side (from a random start) that faces a corridor; all leaf sides do, so side 0 always works
            int side = Rng.pick(Rng.hash(h, 2), 4);
            int du, dv;
            switch (side) {
                case 0 -> { du = q[0]; dv = (z0 + z1) / 2; }
                case 1 -> { du = (x0 + x1) / 2; dv = q[1]; }
                case 2 -> { du = q[2]; dv = (z0 + z1) / 2; }
                default -> { du = (x0 + x1) / 2; dv = q[3]; }
            }
            boolean lit = i == featureLeaf || Rng.chance(Rng.hash(h, 3), gs.get("room-light-chance", 0.7));
            Room room = new Room(x0, z0, x1, z1, du, dv, side, type, lit);
            rooms.add(room);
            for (int u = x0; u <= x1; u++) for (int v = z0; v <= z1; v++) cells[v * S + u] = ROOM;
            if (type == T_MEETING) {
                for (int u = q[0]; u <= q[2]; u++) for (int v = q[1]; v <= q[3]; v++) {
                    boolean ring = u == q[0] || u == q[2] || v == q[1] || v == q[3];
                    boolean corner = (u == q[0] || u == q[2]) && (v == q[1] || v == q[3]);
                    if (ring && !corner) cells[v * S + u] = GLASS;
                }
            }
            cells[dv * S + du] = DOOR;
            if (area >= 70) { // wide openings for big rooms: two columns
                int du2 = (side == 1 || side == 3) ? du + 1 : du, dv2 = (side == 0 || side == 2) ? dv + 1 : dv;
                cells[dv2 * S + du2] = DOOR;
            }
        }
        return new Layout(cells, rooms);
    }

    /** Recursive split of an outer rectangle [x0..x1]x[z0..z1] (inclusive); leaves keep their own wall ring. */
    private void split(byte[] cells, int x0, int z0, int x1, int z1, SplittableRandom rnd, List<int[]> leaves, int depth) {
        int w = x1 - x0 + 1, h = z1 - z0 + 1;
        boolean canX = w >= 2 * 7 + CW, canZ = h >= 2 * 7 + CW;
        boolean want = depth < 2 || (w > 16 || h > 16) && rnd.nextDouble() < 0.85;
        if (!want || (!canX && !canZ)) {
            leaves.add(new int[]{x0, z0, x1, z1});
            return;
        }
        boolean alongX = canX && (!canZ || w > h || (w == h && rnd.nextBoolean()));
        if (alongX) {
            int p = x0 + 7 + rnd.nextInt(w - 2 * 7 - CW + 1);
            for (int u = p; u < p + CW; u++) for (int v = z0; v <= z1; v++) cells[v * S + u] = CORRIDOR;
            split(cells, x0, z0, p - 1, z1, rnd, leaves, depth + 1);
            split(cells, p + CW, z0, x1, z1, rnd, leaves, depth + 1);
        } else {
            int p = z0 + 7 + rnd.nextInt(h - 2 * 7 - CW + 1);
            for (int v = p; v < p + CW; v++) for (int u = x0; u <= x1; u++) cells[v * S + u] = CORRIDOR;
            split(cells, x0, z0, x1, p - 1, rnd, leaves, depth + 1);
            split(cells, x0, p + CW, x1, z1, rnd, leaves, depth + 1);
        }
    }

    private Room roomAt(Layout l, int u, int v) {
        for (Room r : l.rooms) if (r.contains(u, v)) return r;
        return null;
    }

    // ------------------------------------------------------------------ painting

    @Override
    protected void paintCell(CellCtx c) {
        Layout l = layout(c.cx, c.cz);
        int ceil = FLOOR + 5;
        for (int u = c.u0; u <= c.u1; u++) {
            for (int v = c.v0; v <= c.v1; v++) {
                c.set(u, bottomY, v, B.BEDROCK);
                for (int y = bottomY + 1; y < c.fy; y++) c.set(u, y, v, UNDER);
                for (int y = ceil + 1; y < roofY; y++) c.set(u, y, v, UNDER);
                c.set(u, roofY, v, B.BEDROCK);
                c.set(u, ceil, v, CEIL);
                byte t = l.cells[v * S + u];
                switch (t) {
                    case CORRIDOR -> {
                        c.set(u, c.fy, v, HALL_CARPET);
                        for (int y = c.fy + 1; y < ceil; y++) c.set(u, y, v, B.AIR);
                    }
                    case ROOM -> {
                        c.set(u, c.fy, v, roomCarpet(c, l, u, v));
                        for (int y = c.fy + 1; y < ceil; y++) c.set(u, y, v, B.AIR);
                    }
                    case DOOR -> {
                        c.set(u, c.fy, v, HALL_CARPET);
                        for (int y = c.fy + 1; y < ceil; y++) c.set(u, y, v, y <= c.fy + 3 ? B.AIR : WALL_B);
                    }
                    case GLASS -> {
                        c.set(u, c.fy, v, WALL_B);
                        for (int y = c.fy + 1; y < ceil; y++) c.set(u, y, v, y == c.fy + 1 ? TRIM : GLASS_B);
                    }
                    default -> {
                        c.set(u, c.fy, v, WALL_B);
                        for (int y = c.fy + 1; y < ceil; y++) c.set(u, y, v, y == c.fy + 1 ? TRIM : WALL_B);
                    }
                }
            }
        }
        Feature f = featureAt(c.floor, c.cx, c.cz);
        Room fr = f == null ? null : featureRoom(c.cx, c.cz);
        for (Room r : l.rooms) {
            singleDoor(c, l, r);
            if (r != fr) furnish(c, r);
        }
        if (f != null) feature(c, f, featureRoom(c.cx, c.cz));
    }

    /**
     * Commercial carpet: every room is laid in one colour section (mostly grey, some light blue), and the open-plan
     * farms in a 2x2 tile pattern of two close greys.
     */
    private B roomCarpet(CellCtx c, Layout l, int u, int v) {
        for (Room r : l.rooms) {
            if (!r.contains(u, v)) continue;
            if (r.type == T_FARM) return (((c.wx(u) >> 1) + (c.wz(v) >> 1)) & 1) == 0 ? CARPET : CARPET_TILE;
            return Rng.chance(Rng.hash(seed ^ 0xCA4E, c.cx, c.cz, r.x0 * 64L + r.z0), 0.3) ? CARPET_BLUE : CARPET;
        }
        return CARPET;
    }

    /** Small rooms get a real door block in their single-column doorway. */
    private void singleDoor(CellCtx c, Layout l, Room r) {
        if (r.area() >= 70) return;
        String facing = switch (r.doorSide) {
            case 0 -> "west";
            case 1 -> "north";
            case 2 -> "east";
            default -> "south";
        };
        c.set(r.doorU, c.fy + 1, r.doorV, B.of(Material.BIRCH_DOOR, "[facing=" + facing + ",half=lower,hinge=left,open=false]"));
        c.set(r.doorU, c.fy + 2, r.doorV, B.of(Material.BIRCH_DOOR, "[facing=" + facing + ",half=upper,hinge=left,open=false]"));
    }

    private void furnish(CellCtx c, Room r) {
        switch (r.type) {
            case T_FARM -> {
                // cubicle clusters on a 6-block grid with 2-wide aisles; partitions are 1.5 blocks high
                for (int u = r.x0 + 2; u + 3 <= r.x1 - 1; u += 6) {
                    for (int v = r.z0 + 2; v + 3 <= r.z1 - 1; v += 6) {
                        for (int k = 0; k < 4; k++) {
                            c.set(u + k, c.fy + 1, v + 2, PART);
                            c.set(u + k, c.fy + 2, v + 2, PART_TOP);
                        }
                        for (int k = 0; k < 4; k++) {
                            c.set(u + 2, c.fy + 1, v + k, PART);
                            c.set(u + 2, c.fy + 2, v + k, PART_TOP);
                        }
                        c.set(u + 1, c.fy + 1, v + 1, DESK);
                        c.set(u + 3, c.fy + 1, v + 3, DESK);
                    }
                }
            }
            case T_MEETING -> {
                int mz = (r.z0 + r.z1) / 2;
                for (int u = r.x0 + 2; u <= r.x1 - 2; u++) c.set(u, c.fy + 1, mz, B.slab(Material.DARK_OAK_SLAB, true));
            }
            case T_BREAK -> {
                for (int u = r.x0 + 1; u <= r.x1 - 1; u++) {
                    if (r.doorSide == 1 && Math.abs(u - r.doorU) <= 1 || r.doorSide == 1 && u == r.doorU + 2) continue;
                    c.set(u, c.fy + 1, r.z0, (u & 3) == 0 ? B.of(Material.CAULDRON) : B.of(Material.POLISHED_ANDESITE));
                }
            }
            case T_OFFICE -> {
                int mu = (r.x0 + r.x1) / 2;
                for (int u = mu - 1; u <= mu + 1; u++) c.set(u, c.fy + 1, r.z0 + 1, DESK);
                for (int u = r.x0; u <= r.x1; u++) {
                    if (u == mu) continue;
                    c.set(u, c.fy + 1, r.z1, B.of(Material.BOOKSHELF));
                }
            }
            case T_STORAGE -> {
                for (int u = r.x0; u <= r.x1; u += 2) {
                    if (r.doorSide == 1 && u >= r.doorU - 1 && u <= r.doorU + 2) continue;
                    c.set(u, c.fy + 1, r.z0, B.of(Material.BARREL, "[facing=up]"));
                    c.set(u, c.fy + 2, r.z0, B.of(Material.STRIPPED_SPRUCE_WOOD));
                }
            }
            default -> {
            }
        }
    }

    Room featureRoom(int cx, int cz) {
        Layout l = layout(cx, cz);
        Room best = null;
        for (Room r : l.rooms) if (best == null || r.area() > best.area()) best = r;
        return best;
    }

    private void feature(CellCtx c, Feature f, Room r) {
        int mu = (r.x0 + r.x1) / 2, mv = (r.z0 + r.z1) / 2;
        switch (f.id()) {
            case "stairwell" -> {
                // three steps down into a pit in the room centre (room is at least 7x7)
                for (int u = mu - 1; u <= mu + 1; u++) {
                    for (int k = 0; k < 3; k++) {
                        int v = mv - 2 + k;
                        c.set(u, c.fy - k, v, B.stairs(Material.STONE_STAIRS, "north", false));
                        for (int y = c.fy - k + 1; y <= c.fy; y++) c.set(u, y, v, B.AIR);
                    }
                    for (int v = mv + 1; v <= mv + 2; v++) {
                        c.set(u, c.fy - 3, v, B.of(Material.SMOOTH_STONE));
                        for (int y = c.fy - 2; y <= c.fy; y++) c.set(u, y, v, B.AIR);
                    }
                }
                c.set(mu, c.fy - 1, mv + 2, B.light(8));
            }
            case "bright_window" -> {
                for (int u = mu - 2; u <= mu + 2; u++) {
                    for (int y = c.fy + 1; y <= c.fy + 3; y++) c.set(u, y, mv, (u >= mu - 1 && u <= mu + 1 && y >= c.fy + 1) ? B.AIR : WALL_B);
                    c.set(u, c.fy, mv, B.of(Material.GRASS_BLOCK));
                }
                c.set(mu, c.fy + 2, mv, B.light(15));
            }
            default -> {
            }
        }
    }

    @Override
    protected int[] triggerBox(Feature f) {
        Room r = featureRoom(f.cx(), f.cz());
        int mu = (r.x0 + r.x1) / 2, mv = (r.z0 + r.z1) / 2;
        return switch (f.id()) {
            case "stairwell" -> new int[]{mu - 1, -3, mv + 1, mu + 1, -1, mv + 2};
            case "bright_window" -> new int[]{mu - 1, 0, mv, mu + 1, 1, mv};
            default -> null;
        };
    }

    @Override
    public int[] safeLocal(int floor, int cx, int cz) {
        return new int[]{1, 1}; // perimeter corridor corner of the block
    }

    @Override
    public boolean isSafeCell(int floor, int cx, int cz) {
        return true;
    }

    @Override
    public String zoneName(int floor, int cx, int cz) {
        return "office block " + layout(cx, cz).rooms.size() + " rooms";
    }

    public String roomName(int x, int z) {
        Room r = roomAt(layout(cellOf(x), cellOf(z)), localOf(x), localOf(z));
        return r == null ? "corridor" : TYPES[r.type] + (r.lit ? "" : " (dark)");
    }

    // ------------------------------------------------------------------ decorations

    @Override
    protected void cellDecor(int floor, int cx, int cz, List<DecorSpec> out) {
        Layout l = layout(cx, cz);
        long h = ch(floor, cx, cz, 0xDEC5);
        int fy = FLOOR + 1, ceil = FLOOR + 5;
        double lit = zone(cx, cz, 0xDA44L, 90) > 0.8 ? 0.15 : 0.85;
        // corridor panels on a sparse grid, only where the grid lands in a corridor
        for (int u = 1; u < S; u += 10) {
            for (int v = 1; v < S; v += 10) {
                if (l.cells[v * S + u] != CORRIDOR) continue;
                long hh = Rng.hash(h, u, v);
                out.add(lamp("ceiling_panel", cx * S + u + 0.5, ceil, cz * S + v + 0.5, 0f, hh, lampState(hh, lit), cx * S + u, ceil - 1, cz * S + v, 13));
            }
        }
        Feature f = featureAt(floor, cx, cz);
        Room fr = f == null ? null : featureRoom(cx, cz);
        int idx = 0;
        for (Room r : l.rooms) {
            idx++;
            long hr = Rng.hash(h, 100, idx);
            for (int u = r.x0 + 2; u <= r.x1 - 1; u += 8) {
                for (int v = r.z0 + 2; v <= r.z1 - 1; v += 8) {
                    long hl = Rng.hash(hr, u, v);
                    int state = r.lit ? lampState(hl, lit + 0.1) : 2;
                    out.add(lamp("ceiling_panel", cx * S + u + 0.5, ceil, cz * S + v + 0.5, 0f, hl, state, cx * S + u, ceil - 1, cz * S + v, 13));
                }
            }
            if (r == fr) continue;
            double d = Rng.unit(Rng.hash(hr, 7)) / Math.max(0.05, gs.get("decor-density", 1.0));
            switch (r.type) {
                case T_FARM -> {
                    int n = 0;
                    for (int u = r.x0 + 2; u + 3 <= r.x1 - 1 && n < 4; u += 6) {
                        for (int v = r.z0 + 2; v + 3 <= r.z1 - 1 && n < 4; v += 6) {
                            long hc = Rng.hash(hr, u * 7, v);
                            if (Rng.chance(hc, 0.3)) out.add(spec("crt_monitor", cx, cz, u + 1.5, fy + 1.0, v + 1.5, 135f, hc));
                            if (Rng.chance(Rng.hash(hc, 1), 0.2)) out.add(spec("office_chair", cx, cz, u + 0.6, fy, v + 0.6, (float) Rng.pick(hc, 360), hc));
                            n++;
                        }
                    }
                }
                case T_MEETING -> {
                    int mz = (r.z0 + r.z1) / 2;
                    for (int i = 0; i < 2; i++) {
                        out.add(spec("office_chair", cx, cz, r.x0 + 2.5 + i * 2, fy, mz - 0.8, 180f, Rng.hash(hr, i)));
                        out.add(spec("office_chair", cx, cz, r.x0 + 3.5 + i * 2, fy, mz + 1.8, 0f, Rng.hash(hr, i + 5)));
                    }
                    if (d < 0.6) out.add(spec("whiteboard", cx, cz, (r.x0 + r.x1) / 2.0 + 0.5, fy + 1.2, r.z1 + 0.98, 180f, hr));
                }
                case T_BREAK -> {
                    out.add(spec("water_cooler", cx, cz, r.x1 + 0.5, fy, r.z1 + 0.5, 225f, hr).barrier(cx * S + r.x1, fy, cz * S + r.z1));
                    if (d < 0.5) out.add(spec("vending_machine", cx, cz, r.x0 + 0.5, fy, r.z1 + 0.5, -90f, hr)
                            .barrier(cx * S + r.x0, fy, cz * S + r.z1, cx * S + r.x0, fy + 1, cz * S + r.z1));
                }
                case T_OFFICE -> {
                    int mu = (r.x0 + r.x1) / 2;
                    out.add(spec("crt_monitor", cx, cz, mu + 0.5, fy + 1.0, r.z0 + 1.5, 180f, hr));
                    out.add(spec("office_chair", cx, cz, mu + 0.5, fy, r.z0 + 2.7, 180f, hr));
                    if (d < 0.5) out.add(spec("potted_plant", cx, cz, r.x0 + 0.5, fy, r.z0 + 0.5, 0f, hr).barrier(cx * S + r.x0, fy, cz * S + r.z0));
                    if (d < 0.3) out.add(spec("wall_clock", cx, cz, mu + 0.5, fy + 2.4, r.z0 + 0.0, 0f, hr));
                }
                case T_CLOSET -> {
                    if (d < 0.4) out.add(spec("office_chair", cx, cz, (r.x0 + r.x1) / 2.0 + 0.5, fy, (r.z0 + r.z1) / 2.0 + 0.5, (float) Rng.pick(hr, 360), hr));
                }
                default -> {
                    if (d < 0.3) out.add(spec("copier", cx, cz, r.x1 + 0.5, fy, (r.z0 + r.z1) / 2.0 + 0.5, -90f, hr).barrier(cx * S + r.x1, fy, cz * S + (r.z0 + r.z1) / 2));
                }
            }
        }
        if (f != null) {
            int mu = (fr.x0 + fr.x1) / 2, mv = (fr.z0 + fr.z1) / 2;
            switch (f.id()) {
                case "stairwell" -> out.add(spec("basement_sign", cx, cz, mu + 0.5, fy + 1.2, mv - 2.8, 0f, f.seed()));
                case "bright_window" -> out.add(spec("daylight_window", cx, cz, mu + 0.5, fy, mv + 0.5, 0f, f.seed()));
                case "conference_hall" -> {
                    for (int i = 0; i < 3; i++) out.add(spec("office_chair", cx, cz, fr.x0 + 2.5 + i * 2, fy, mv + 1.8, 0f, Rng.hash(f.seed(), i)));
                }
                default -> {
                }
            }
        }
    }

    // ------------------------------------------------------------------ surface details

    @Override
    protected void cellDetails(int floor, int cx, int cz, List<DecorSpec> out) {
        Layout l = layout(cx, cz);
        long h = ch(floor, cx, cz, 0xD7A4);
        double ox = cx * S, oz = cz * S;
        int fy = FLOOR + 1, ceil = FLOOR + 5;
        double w = wear(cx, cz, 0x3EA4L);
        Feature f = featureAt(floor, cx, cz);
        Room fr = f == null ? null : featureRoom(cx, cz);
        int idx = 0;
        for (Room r : l.rooms) {
            idx++;
            if (r == fr) continue;
            long hr = Rng.hash(h, idx);
            int wx = r.x1 - r.x0 + 1, wz = r.z1 - r.z0 + 1;
            // suspended ceiling grid: a line each way through the room
            if (wx >= 5 && wz >= 5) {
                out.add(detail(DecorSpec.TIER_ARCH, Rng.hash(hr, 1), "d:strip:light_gray_concrete:" + wx + ":0.008:0.04",
                        ox + r.x0, ceil - 0.009, oz + r.z0 + wz / 2.0 + 0.48, 0f, 0f));
                out.add(detail(DecorSpec.TIER_ARCH, Rng.hash(hr, 2), "d:strip:light_gray_concrete:" + wz + ":0.008:0.04",
                        ox + r.x0 + wx / 2.0 + 0.52, ceil - 0.009, oz + r.z0, 90f, 0f));
            }
            // details on the north wall's inside face (z = z0), kept clear of a door in that wall
            boolean doorNorth = r.doorV == r.z0 - 1;
            // clear of a door in that wall and of the corner where a plant may stand
            double safeX = doorNorth && r.doorU - r.x0 < wx / 2 ? r.x1 - 1.2 : r.x0 + 1.6;
            double faceZ = oz + r.z0 + 0.003;
            boolean counterWall = r.type == T_BREAK || r.type == T_STORAGE; // counters and shelving line z0
            if (wx >= 4 && !counterWall) {
                out.add(detail(DecorSpec.TIER_ENV, Rng.hash(hr, 3), "d:outlet", ox + safeX + 0.3, fy + 0.35, faceZ, 0f, 0f));
                if (Rng.chance(Rng.hash(hr, 4), 0.35)) {
                    // a drywall joint running floor to ceiling
                    out.add(detail(DecorSpec.TIER_ARCH, Rng.hash(hr, 5), "d:strip:light_gray_concrete:0.03:" + (ceil - fy - 1) + ":0.006",
                            ox + r.x0 + wx / 2.0, fy + 1, faceZ, 0f, 0f));
                }
            }
            if (r.type == T_FARM && wx >= 6 && !doorNorth) {
                out.add(detail(DecorSpec.TIER_ENV, Rng.hash(hr, 6), "d:strip:light_gray_concrete:" + (wx - 1) + ":0.08:0.05",
                        ox + r.x0 + 0.5, fy + 1.05, faceZ, 0f, 0f)); // cable channel above the skirting
            }
            if (r.type == T_MEETING && wx >= 4) {
                // window sill and head along the glass on the south side
                out.add(detail(DecorSpec.TIER_ARCH, Rng.hash(hr, 7), "d:sill:" + wx + ":2.9", ox + r.x1 + 1, fy + 1, oz + r.z1 + 1 - 0.003, 180f, 0f));
            }
            if (wx * wz >= 30 && Rng.chance(Rng.hash(hr, 8), 0.5)) {
                out.add(detail(DecorSpec.TIER_ARCH, Rng.hash(hr, 9), "d:vent:0.9", ox + r.x0 + 1.5, ceil, oz + r.z1 - 0.5, 0f, 0f));
            }
            // rare decay: peeling paint, a damp stain on the ceiling tiles
            if (wx >= 4 && Rng.chance(Rng.hash(hr, 10), 0.02 + 0.15 * w)) {
                out.add(detail(DecorSpec.TIER_AGING, Rng.hash(hr, 11), "d:peel:white_concrete:light_gray_concrete:0.5:0.8",
                        ox + r.x1 - 1.0, ceil - 0.6, faceZ, 0f, 0f));
            }
            if (Rng.chance(Rng.hash(hr, 12), 0.015 + 0.12 * w)) {
                out.add(detail(DecorSpec.TIER_AGING, Rng.hash(hr, 13), "d:cpatch:terracotta:1.4:1.1",
                        ox + r.x0 + 1, ceil, oz + r.z0 + 1, 0f, 0f));
            }
        }
    }
}
