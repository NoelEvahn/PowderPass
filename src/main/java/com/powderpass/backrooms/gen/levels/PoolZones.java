package com.powderpass.backrooms.gen.levels;

import com.powderpass.backrooms.gen.B;
import com.powderpass.backrooms.gen.DecorSpec;
import com.powderpass.backrooms.gen.levels.PoolroomsGenerator.Plan;
import com.powderpass.backrooms.gen.levels.PoolroomsGenerator.Zone;
import com.powderpass.backrooms.util.Rng;
import com.powderpass.backrooms.util.ValueNoise;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.powderpass.backrooms.gen.levels.PoolroomsGenerator.FLOORS;
import static com.powderpass.backrooms.gen.levels.PoolroomsGenerator.GRIME;
import static com.powderpass.backrooms.gen.levels.PoolroomsGenerator.HIGH;
import static com.powderpass.backrooms.gen.levels.PoolroomsGenerator.PANEL;
import static com.powderpass.backrooms.gen.levels.PoolroomsGenerator.POOL;
import static com.powderpass.backrooms.gen.levels.PoolroomsGenerator.REG;
import static com.powderpass.backrooms.gen.levels.PoolroomsGenerator.ROOF;
import static com.powderpass.backrooms.gen.levels.PoolroomsGenerator.TILE;
import static com.powderpass.backrooms.gen.levels.PoolroomsGenerator.UNDERWATER_LIGHT;
import static com.powderpass.backrooms.gen.levels.PoolroomsGenerator.get;
import static com.powderpass.backrooms.gen.levels.PoolroomsGenerator.light;
import static com.powderpass.backrooms.gen.levels.PoolroomsGenerator.set;

/**
 * The Poolrooms' newer architecture, modelled on the reference images: each kind of space is a deliberate
 * composition, not scattered decoration, and they differ a lot in how much is going on.
 * <ul>
 *   <li>{@link Zone#ENCLOSED}: a warren of fully enclosed tiled rooms (walls and ceilings all round) split by a
 *   deterministic BSP. Rooms are plain, pool rooms whose water fills nearly the whole floor, tall, low and dim,
 *   shower-tiled, or (rarely) hold a face in the wall.</li>
 *   <li>{@link Zone#LONGPOOL}: one long, wide pool running straight through three regions with nothing in the way:
 *   a lap pool that disappears into the distance between two walkways.</li>
 *   <li>{@link Zone#FLOODED}: a tall hall whose floor is entirely water, big square columns standing in it, a raised
 *   terrace along one side with steps down into the water, and deeper patches.</li>
 *   <li>{@link Zone#CORRIDORS}: solid tile pierced by narrow, dim, low corridors (some ankle-deep in water, glowing
 *   round portholes in the walls) that open, without warning, into a gigantic chamber with a deep pool.</li>
 *   <li>{@link Zone#CURVED}: a round hall with wavy curved walls, a kidney-shaped pool and a curving stream, round
 *   columns, a coved edge and a ceiling that steps up ring by ring into a central atrium (open to the sky on the
 *   upper storey, when the region is open).</li>
 *   <li>{@link Zone#MULTILEVEL}: a big hall with a balcony all round at mid height, a stair up to it, a bridge
 *   across, and a sunken pit in the middle, half flooded, with steps down.</li>
 * </ul>
 * Every region keeps the same doorway positions on all four sides ({@link #port}: three openings per side), and
 * keeps its border at floor level, so any two neighbouring regions always connect and no water can escape.
 * Everything is a pure function of seed and coordinates.
 */
final class PoolZones {
    private final PoolroomsGenerator g;
    private final Map<Long, Rooms> rooms = new ConcurrentHashMap<>();
    private final Map<Long, List<DecorSpec>> decor = new ConcurrentHashMap<>();

    PoolZones(PoolroomsGenerator g) {
        this.g = g;
    }

    static boolean custom(Zone z) {
        return switch (z) {
            case ENCLOSED, LONGPOOL, FLOODED, CORRIDORS, CURVED, MULTILEVEL -> true;
            default -> false;
        };
    }

    /** Doorway spans every region keeps on every side (so neighbours always line up). */
    static boolean port(int t) {
        return (t >= 12 && t <= 15) || (t >= 28 && t <= 31) || (t >= 44 && t <= 47);
    }

    /** Highest ceiling block a storey can have (the storey above starts with its deepest pools). */
    static int maxCeil(int f) {
        return f == HIGH ? ROOF - 1 : FLOORS[f + 1] - 10;
    }

    void paint(int f, Plan p, int lx, int lz, int x, int z, B[] col) {
        switch (p.zone[f]) {
            case ENCLOSED -> enclosed(f, p, lx, lz, x, z, col);
            case LONGPOOL -> longPool(f, p, lx, lz, x, z, col);
            case FLOODED -> flooded(f, p, lx, lz, x, z, col);
            case CORRIDORS -> corridors(f, p, lx, lz, x, z, col);
            case CURVED -> curved(f, p, lx, lz, x, z, col);
            case MULTILEVEL -> multilevel(f, p, lx, lz, x, z, col);
            default -> {
            }
        }
    }

    private static void air(B[] col, int from, int to) {
        for (int yy = from; yy <= to; yy++) set(col, yy, B.AIR);
    }

    /** A ceiling with emissive panels on a grid and invisible lights under them. */
    private static void ceiling(B[] col, int ceil, int x, int z, int spacing, int lightLevel) {
        int mx = Math.floorMod(x, spacing), mz = Math.floorMod(z, spacing);
        boolean panel = (mx == 1 || mx == 2) && (mz == 1 || mz == 2);
        set(col, ceil, panel ? PANEL : TILE);
        if (mx == 1 && mz == 1 && lightLevel > 0) light(col, ceil - 1, lightLevel);
    }

    /**
     * Invisible lights hanging in the air of tall rooms: block light fades one level per block, so ceiling lights
     * alone leave the floor of a 16-block room dark. The Poolrooms are meant to be bright.
     */
    private static void hang(B[] col, int fy, int ceil, int x, int z) {
        int h = ceil - fy;
        if (h >= 9 && Math.floorMod(x, 6) == 3 && Math.floorMod(z, 6) == 3) light(col, fy + 5, 15);
        if (h >= 16 && Math.floorMod(x, 8) == 5 && Math.floorMod(z, 8) == 5) light(col, fy + 12, 15);
        if (h >= 24 && Math.floorMod(x, 10) == 1 && Math.floorMod(z, 10) == 1) light(col, fy + 20, 15);
    }

    private static B lining(int x, int z) {
        return Math.floorMod(x, 4) == 0 || Math.floorMod(z, 4) == 0 ? TILE : POOL;
    }

    // ================================================================== enclosed rooms

    /** One room of the BSP: interior bounds (inclusive), type and ceiling height above the floor. */
    record Room(int a0, int b0, int a1, int b1, int type, int height, long h) {
        boolean contains(int a, int b) {
            return a >= a0 && a <= a1 && b >= b0 && b <= b1;
        }

        int area() {
            return (a1 - a0 + 1) * (b1 - b0 + 1);
        }
    }

    /** A wall from a BSP split: {@code vertical} = at a fixed a. Door spans are inclusive {from, to} pairs. */
    record Wall(boolean vertical, int at, int from, int to, int[] doors) {
        boolean on(int a, int b) {
            return vertical ? a == at && b >= from && b <= to : b == at && a >= from && a <= to;
        }

        boolean door(int a, int b) {
            int t = vertical ? b : a;
            for (int i = 0; i + 1 < doors.length; i += 2) if (t >= doors[i] && t <= doors[i + 1]) return true;
            return false;
        }
    }

    static final int R_PLAIN = 0, R_POOL = 1, R_TALL = 2, R_LOW = 3, R_SHOWER = 4, R_STATUE = 5;

    record Rooms(List<Room> rooms, List<Wall> walls) {
    }

    Rooms rooms(Plan p, int f) {
        long k = Rng.hash(p.var[f], p.rx, p.rz, f);
        Rooms r = rooms.get(k);
        if (r != null) return r;
        if (rooms.size() > 2048) rooms.clear();
        List<Room> out = new ArrayList<>();
        List<Wall> walls = new ArrayList<>();
        split(p.var[f], 1, 1, REG - 2, REG - 2, 0, out, walls, f);
        r = new Rooms(List.copyOf(out), List.copyOf(walls));
        rooms.put(k, r);
        return r;
    }

    private void split(long h, int a0, int b0, int a1, int b1, int depth, List<Room> out, List<Wall> walls, int f) {
        int w = a1 - a0 + 1, d = b1 - b0 + 1;
        int min = 7;
        boolean big = w > 26 || d > 26;
        boolean can = w >= 2 * min + 1 || d >= 2 * min + 1;
        if (can && (big || (depth < 5 && Rng.chance(Rng.hash(h, 1), 0.72)))) {
            boolean vertical = w >= 2 * min + 1 && (d < 2 * min + 1 || w > d || (w == d && Rng.chance(Rng.hash(h, 2), 0.5)));
            int lo = vertical ? a0 + min : b0 + min, hi = vertical ? a1 - min : b1 - min;
            int s = lo + Rng.pick(Rng.hash(h, 3), hi - lo + 1);
            int from = vertical ? b0 : a0, to = vertical ? b1 : a1;
            // one or two doorways, never at the very ends of the wall
            int span = to - from + 1;
            int n = span > 18 && Rng.chance(Rng.hash(h, 4), 0.6) ? 2 : 1;
            int[] doors = new int[n * 2];
            for (int i = 0; i < n; i++) {
                int dw = 2 + Rng.pick(Rng.hash(h, 5, i), 3);
                int seg0 = from + 1 + i * span / n, seg1 = from + (i + 1) * span / n - 2 - dw;
                int st = seg1 > seg0 ? seg0 + Rng.pick(Rng.hash(h, 6, i), seg1 - seg0 + 1) : seg0;
                doors[i * 2] = st;
                doors[i * 2 + 1] = Math.min(to - 1, st + dw - 1);
            }
            walls.add(new Wall(vertical, s, from, to, doors));
            if (vertical) {
                split(Rng.hash(h, 10), a0, b0, s - 1, b1, depth + 1, out, walls, f);
                split(Rng.hash(h, 11), s + 1, b0, a1, b1, depth + 1, out, walls, f);
            } else {
                split(Rng.hash(h, 10), a0, b0, a1, s - 1, depth + 1, out, walls, f);
                split(Rng.hash(h, 11), a0, s + 1, a1, b1, depth + 1, out, walls, f);
            }
            return;
        }
        int area = w * d;
        double r = Rng.unit(Rng.hash(h, 20));
        int type;
        if (area >= 300) type = r < 0.45 ? R_POOL : r < 0.8 ? R_TALL : R_PLAIN;
        else if (area <= 90) type = r < 0.35 ? R_SHOWER : r < 0.65 ? R_LOW : R_PLAIN;
        else type = r < 0.35 ? R_POOL : r < 0.55 ? R_TALL : r < 0.7 ? R_LOW : R_PLAIN;
        if (area >= 100 && Rng.chance(Rng.hash(h, 21), g.gs().get("statue-chance", 0.06) * 1.5)) type = R_STATUE;
        int max = maxCeil(f) - FLOORS[f];
        int height = switch (type) {
            case R_TALL -> 12 + Rng.pick(Rng.hash(h, 22), Math.max(1, Math.min(9, max - 12)));
            case R_LOW -> 4;
            case R_POOL -> 6 + Rng.pick(Rng.hash(h, 22), 4);
            case R_STATUE -> 9 + Rng.pick(Rng.hash(h, 22), 4);
            case R_SHOWER -> 5;
            default -> 5 + Rng.pick(Rng.hash(h, 22), 3);
        };
        out.add(new Room(a0, b0, a1, b1, type, Math.min(height, max), h));
    }

    private void enclosed(int f, Plan p, int lx, int lz, int x, int z, B[] col) {
        int fy = FLOORS[f];
        int ring = Math.min(Math.min(lx, lz), Math.min(REG - 1 - lx, REG - 1 - lz));
        if (ring == 0) {
            // the outer wall, with the three doorways every region keeps on every side
            boolean along = lx == 0 || lx == REG - 1 ? port(lz) : port(lx);
            boolean corner = (lx == 0 || lx == REG - 1) && (lz == 0 || lz == REG - 1);
            if (along && !corner) {
                set(col, fy, TILE);
                air(col, fy + 1, fy + 3);
            } else band(col, fy);
            return;
        }
        Rooms rs = rooms(p, f);
        for (Wall w : rs.walls) {
            if (!w.on(lx, lz)) continue;
            if (w.door(lx, lz)) {
                set(col, fy, TILE);
                air(col, fy + 1, fy + 3);
            } else band(col, fy);
            return;
        }
        Room room = null;
        for (Room r : rs.rooms) {
            if (r.contains(lx, lz)) {
                room = r;
                break;
            }
        }
        if (room == null) return; // solid
        int ceil = fy + room.height();
        int ia = lx - room.a0(), ib = lz - room.b0(), wa = room.a1() - room.a0(), wb = room.b1() - room.b0();
        boolean edge = ia == 0 || ib == 0 || ia == wa || ib == wb;
        switch (room.type()) {
            case R_POOL -> {
                // water fills nearly the whole floor: a one-block rim, steps at both ends
                int depth = 2 + (int) (Rng.unit(Rng.hash(room.h(), 30)) * 3);
                if (edge) set(col, fy, TILE);
                else {
                    boolean step = (ia == 1 || ia == wa - 1) && ib >= wb / 2 - 1 && ib <= wb / 2 + 1;
                    int bottom = step ? fy - 1 : fy - depth;
                    set(col, bottom, lining(x, z));
                    for (int yy = bottom + 1; yy <= fy; yy++) set(col, yy, B.WATER);
                    if (ia == wa / 2 && ib == wb / 2 && depth >= 3) set(col, bottom + 1, UNDERWATER_LIGHT);
                }
            }
            case R_SHOWER -> set(col, fy, (lx + lz) % 2 == 0 ? POOL : TILE);
            case R_LOW -> set(col, fy, Rng.chance(Rng.hash(g.seed ^ 0x10E, x, z), 0.08) ? GRIME : TILE);
            default -> set(col, fy, Math.floorMod(x, 5) == 0 || Math.floorMod(z, 5) == 0 ? POOL : TILE);
        }
        air(col, fy + 1, ceil - 1);
        if (room.type() == R_LOW) {
            // low and dim: one weak light in the middle
            set(col, ceil, ia == wa / 2 && ib == wb / 2 ? PANEL : TILE);
            if (ia == wa / 2 && ib == wb / 2) light(col, ceil - 1, 8);
        } else {
            ceiling(col, ceil, x, z, room.type() == R_TALL ? 5 : 4, 15);
            hang(col, fy, ceil, x, z);
        }
        // the blue band round the walls, at the height of the doors
        if (edge && room.type() != R_POOL) set(col, fy, POOL);
    }

    /** A wall column: tile with the pool-blue band at knee and lintel height. */
    private static void band(B[] col, int fy) {
        set(col, fy + 1, POOL);
        set(col, fy + 4, POOL);
    }

    // ================================================================== long pool

    private void longPool(int f, Plan p, int lx, int lz, int x, int z, B[] col) {
        boolean ax = p.alongX[f];
        int a = ax ? lx : lz, b = ax ? lz : lx;
        int fy = FLOORS[f], ceil = Math.min(maxCeil(f), fy + (f == HIGH ? 18 : 16));
        // the pool ends with a walkway where the chain of long-pool regions ends
        boolean endLo = !continues(p, f, -1), endHi = !continues(p, f, 1);
        boolean endWalk = (endLo && a <= 2) || (endHi && a >= REG - 3);
        if (b <= 5 || b >= REG - 6) {
            // side walls, pierced by the shared doorways into whatever is beside
            if (port(a) && !(endLo && a < 3) && !(endHi && a > REG - 4)) {
                set(col, fy, TILE);
                air(col, fy + 1, fy + 4);
                if (b == 5 || b == REG - 6) set(col, fy + 5, POOL);
            } else if (b == 5 || b == REG - 6) {
                band(col, fy);
            }
            return;
        }
        boolean walk = b <= 9 || b >= REG - 10 || endWalk;
        boolean pillar = (p.var[f] & 1) == 0 && (b == 8 || b == 9 || b == REG - 10 || b == REG - 9) && Math.floorMod(a, 10) <= 1;
        if (pillar) {
            for (int yy = fy; yy < ceil; yy++) set(col, yy, (yy == fy + 1 || yy == fy + 4) ? POOL : TILE);
            return;
        }
        if (walk) {
            set(col, fy, Math.floorMod(a, 4) == 0 ? POOL : TILE);
        } else {
            // lanes: a darker line on the bottom every six blocks, deeper in the middle
            int depth = b >= 20 && b <= REG - 21 ? 5 : 3;
            boolean steps = (b == 10 || b == REG - 11) && Math.floorMod(a, 20) >= 9 && Math.floorMod(a, 20) <= 11;
            boolean steps2 = (b == 11 || b == REG - 12) && Math.floorMod(a, 20) >= 9 && Math.floorMod(a, 20) <= 11;
            int bottom = steps ? fy - 1 : steps2 ? fy - 2 : fy - depth;
            set(col, bottom, (b - 10) % 6 == 3 ? TILE : POOL);
            for (int yy = bottom + 1; yy <= fy; yy++) set(col, yy, B.WATER);
            if (depth == 5 && b == 29 && Math.floorMod(a, 10) == 5) set(col, bottom + 1, UNDERWATER_LIGHT);
        }
        air(col, fy + 1, ceil - 1);
        // long strips of light along the pool
        boolean strip = (b == 14 || b == 24 || b == 35 || b == 45) && Math.floorMod(a, 3) != 0;
        set(col, ceil, strip ? PANEL : TILE);
        if (strip && Math.floorMod(a, 6) == 1) light(col, ceil - 1, 15);
        if ((b == 7 || b == REG - 8) && Math.floorMod(a, 8) == 4) light(col, fy + 5, 14);
        hang(col, fy, ceil, x, z);
    }

    private boolean continues(Plan p, int f, int dir) {
        boolean ax = p.alongX[f];
        Plan n = ax ? g.plan(p.rx + dir, p.rz) : g.plan(p.rx, p.rz + dir);
        return n.zone[f] == Zone.LONGPOOL && n.alongX[f] == ax;
    }

    // ================================================================== flooded hall

    private void flooded(int f, Plan p, int lx, int lz, int x, int z, B[] col) {
        int[] l = PoolroomsGenerator.w2l(lx, lz, p.face[f]);
        int a = l[0], b = l[1];
        int fy = FLOORS[f], ceil = Math.min(maxCeil(f), fy + (f == HIGH ? 24 : 18));
        int ring = Math.min(Math.min(lx, lz), Math.min(REG - 1 - lx, REG - 1 - lz));
        boolean terrace = a >= 1 && a <= 8;
        if (ring == 0) {
            set(col, fy, TILE); // a dry curb all round, so neighbours always meet a floor at this height
        } else if (terrace) {
            for (int yy = fy; yy <= fy + 5; yy++) set(col, yy, yy == fy + 5 ? (Math.floorMod(b, 4) == 0 ? POOL : TILE) : TILE);
        } else if (a >= 9 && a <= 14 && b >= 26 && b <= 33) {
            // steps from the water up to the terrace
            int top = fy + 5 - (a - 9);
            for (int yy = fy - 1; yy <= top; yy++) set(col, yy, TILE);
            if (top < fy) set(col, fy, B.WATER);
        } else {
            boolean pillar = a >= 12 && Math.floorMod(a - 12, 10) <= 1 && Math.floorMod(b, 10) >= 4 && Math.floorMod(b, 10) <= 5;
            if (pillar) {
                for (int yy = fy - 1; yy < ceil; yy++) set(col, yy, (yy == fy + 1 || yy == fy + 4) ? POOL : TILE);
                return;
            }
            double n = ValueNoise.fractal(g.seed ^ 0xF100DL, x, z, 18);
            int depth = n > 0.66 ? 3 : 1;
            set(col, fy - depth, lining(x, z));
            for (int yy = fy - depth + 1; yy <= fy; yy++) set(col, yy, B.WATER);
            if (depth == 3 && Math.floorMod(x, 9) == 4 && Math.floorMod(z, 9) == 4) set(col, fy - 2, UNDERWATER_LIGHT);
        }
        int floorTop = terrace && ring > 0 ? fy + 5 : fy;
        air(col, floorTop + 1, ceil - 1);
        // great rectangles of light in the ceiling, like skylights
        int ma = Math.floorMod(a, 14), mb = Math.floorMod(b, 14);
        boolean sky = ma >= 4 && ma <= 9 && mb >= 4 && mb <= 9;
        set(col, ceil, sky ? PANEL : TILE);
        if (sky && ma % 3 == 1 && mb % 3 == 1) light(col, ceil - 1, 15);
        hang(col, floorTop, ceil, x, z);
    }

    // ================================================================== corridors and the gigantic chamber

    private static final int[] LATTICE = {4, 14, 24, 34, 44, 54};

    private static boolean onLine(int t) {
        for (int l : LATTICE) if (t == l || t == l + 1) return true;
        return false;
    }

    private static int lineIndex(int t) {
        for (int i = 0; i < LATTICE.length; i++) if (t == LATTICE[i] || t == LATTICE[i] + 1) return i;
        return -1;
    }

    static boolean chamber(int a, int b) {
        return a >= 18 && a <= 41 && b >= 18 && b <= 41;
    }

    /** Is (a, b) inside the corridor network (not counting the chamber)? Also tells if that stretch is flooded. */
    private int corridorAt(Plan p, int f, int a, int b) {
        long h = p.var[f];
        // exits to the region edge, aligned with every other region's doorways
        if ((a <= 3 || a >= 56) && (b == 14 || b == 15 || b == 29 || b == 30 || b == 44 || b == 45)) return 1;
        if ((b <= 3 || b >= 56) && (a == 14 || a == 15 || a == 29 || a == 30 || a == 44 || a == 45)) return 1;
        if (a < 4 || a > 55 || b < 4 || b > 55) return 0;
        int ia = lineIndex(a), ib = lineIndex(b);
        // the outer ring is always there, so every stretch connects
        if (ia == 0 || ia == 5 || ib == 0 || ib == 5) return flooded(h, ia, ib, a, b) ? 2 : 1;
        if (ia >= 0) {
            int seg = Math.min(4, (b - 4) / 10);
            if (!Rng.chance(Rng.hash(h, 1, ia, seg), 0.3)) return flooded(h, ia, seg + 100, a, b) ? 2 : 1;
        }
        if (ib >= 0) {
            int seg = Math.min(4, (a - 4) / 10);
            if (!Rng.chance(Rng.hash(h, 2, ib, seg), 0.3)) return flooded(h, ib + 50, seg + 100, a, b) ? 2 : 1;
        }
        return 0;
    }

    private static boolean flooded(long h, int i, int j, int a, int b) {
        // ankle-deep water in about a quarter of the corridors, never at the ring corners
        return Rng.chance(Rng.hash(h, 7, i, j), 0.25) && !(a < 8 && b < 8) && !(a > 51 && b > 51);
    }

    private void corridors(int f, Plan p, int lx, int lz, int x, int z, B[] col) {
        int fy = FLOORS[f];
        int a = lx, b = lz;
        if (chamber(a, b)) {
            int ceil = maxCeil(f);
            boolean pool = a >= 22 && a <= 37 && b >= 22 && b <= 37;
            if (pool) {
                boolean steps = (a == 22 || a == 23) && b >= 28 && b <= 31;
                int bottom = steps ? (a == 22 ? fy - 1 : fy - 2) : fy - 7;
                set(col, bottom, lining(x, z));
                for (int yy = bottom + 1; yy <= fy; yy++) set(col, yy, B.WATER);
                if (a == 30 && b == 30) set(col, bottom + 1, UNDERWATER_LIGHT);
            } else {
                set(col, fy, Math.floorMod(x, 6) == 0 || Math.floorMod(z, 6) == 0 ? POOL : TILE);
            }
            air(col, fy + 1, ceil - 1);
            ceiling(col, ceil, x, z, 6, 15);
            hang(col, fy, ceil, x, z);
            return;
        }
        int c = corridorAt(p, f, a, b);
        if (c == 0) {
            // solid tile; glowing round portholes where a wall faces a corridor
            if (porthole(p, f, a, b)) set(col, fy + 2, PANEL);
            return;
        }
        if (c == 2) {
            set(col, fy - 1, POOL);
            set(col, fy, B.WATER);
        } else {
            set(col, fy, TILE);
        }
        air(col, fy + 1, fy + 3);
        set(col, fy + 4, TILE);
        // dim: a weak light now and then, and many stretches with none
        if (Math.floorMod(a + b, 9) == 0 && Rng.chance(Rng.hash(p.var[f], a / 9, b / 9), 0.55)) light(col, fy + 3, 9);
    }

    private boolean porthole(Plan p, int f, int a, int b) {
        if (Math.floorMod(a + b, 7) != 0) return false;
        int[][] n = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : n) {
            int na = a + d[0], nb = b + d[1];
            if (na < 0 || nb < 0 || na >= REG || nb >= REG || chamber(na, nb)) continue;
            if (corridorAt(p, f, na, nb) != 0) return true;
        }
        return false;
    }

    // ================================================================== curved hall

    private record Curve(double p1, double p2, double p3, double p4) {
    }

    private Curve curve(Plan p, int f) {
        long h = p.var[f];
        return new Curve(Rng.unit(Rng.hash(h, 1)) * 6.28, Rng.unit(Rng.hash(h, 2)) * 6.28, Rng.unit(Rng.hash(h, 3)) * 6.28, Rng.unit(Rng.hash(h, 4)) * 6.28);
    }

    /** Radius of the curved hall's wall at angle {@code t}. */
    static double wallRadius(double t, double p1, double p2) {
        return 23 + 2.5 * Math.sin(3 * t + p1) + 1.5 * Math.sin(5 * t + p2);
    }

    private void curved(int f, Plan p, int lx, int lz, int x, int z, B[] col) {
        Curve cv = curve(p, f);
        int fy = FLOORS[f];
        double dx = lx - 29.5, dz = lz - 29.5, r = Math.hypot(dx, dz), t = Math.atan2(dz, dx);
        double wr = wallRadius(t, cv.p1, cv.p2);
        if (r >= wr) {
            // the solid round the hall, pierced by arched passages to the shared doorways
            if (curvedTunnel(lx, lz)) {
                int along = port(lz) && (lx < 14 || lx > 45) ? lz : lx;
                int k = along >= 44 ? along - 44 : along >= 28 ? along - 28 : along - 12; // 0..3 across the passage
                boolean centre = k == 1 || k == 2; // a rounded top: the middle two are a block higher
                set(col, fy, TILE);
                air(col, fy + 1, fy + (centre ? 4 : 3));
            }
            return;
        }
        // floor: a kidney pool, a curving stream, round columns, tile everywhere else
        double pr = Math.hypot(dx - 6, dz + 4), pt = Math.atan2(dz + 4, dx - 6);
        double prad = 8 + 2.5 * Math.sin(2 * pt + cv.p4);
        double stream = Math.abs(r - 16.5 - 1.5 * Math.sin(4 * t + cv.p3));
        double colAngle = Math.floorMod((int) Math.round(Math.toDegrees(t - cv.p3) / 60.0), 6) * 60 + Math.toDegrees(cv.p3);
        double ca = Math.toRadians(colAngle);
        double cdist = Math.hypot(dx - Math.cos(ca) * 19.5, dz - Math.sin(ca) * 19.5);
        int ceil = fy + ceilingAt(f, r, t, wr, cv);
        if (cdist < 1.6 && pr > prad + 1) {
            for (int yy = fy; yy < ceil; yy++) set(col, yy, (yy == fy + 1 || yy == fy + 4) ? POOL : TILE);
            return;
        }
        if (pr < prad) {
            int depth = pr < prad - 4 ? 4 : pr < prad - 1.3 ? 2 : 1;
            set(col, fy - depth, lining(x, z));
            for (int yy = fy - depth + 1; yy <= fy; yy++) set(col, yy, B.WATER);
            if (pr < 1.2) set(col, fy - depth + 1, UNDERWATER_LIGHT);
        } else if (stream < 1.3 && r < wr - 2) {
            set(col, fy - 1, POOL);
            set(col, fy, B.WATER);
        } else {
            boolean seam = Math.abs(r % 4 - 2) < 0.35; // concentric seams in the floor tile
            set(col, fy, seam ? POOL : TILE);
        }
        air(col, fy + 1, ceil - 1);
        set(col, ceil, Math.abs(r % 5 - 2.5) < 0.6 && r < wr - 3 ? PANEL : TILE);
        if (Math.floorMod(x, 5) == 2 && Math.floorMod(z, 5) == 2) light(col, ceil - 1, 15);
        hang(col, fy, ceil, x, z);
    }

    /** The arched passages from the curved hall out to the shared doorways. */
    private static boolean curvedTunnel(int lx, int lz) {
        return (port(lz) && (lx < 14 || lx > 45)) || (port(lx) && (lz < 14 || lz > 45));
    }

    /**
     * Distance from the hall's centre (29.5, 29.5) along angle {@code t} to the first block that is actually wall,
     * the way the painter decides it (per block), so a face placed there sits against real stone.
     */
    private static double wallHit(Curve cv, double t) {
        for (double r = 15; r < 29; r += 0.05) {
            double px = 29.5 + Math.cos(t) * r, pz = 29.5 + Math.sin(t) * r;
            int lx = (int) Math.floor(px), lz = (int) Math.floor(pz);
            double dx = lx - 29.5, dz = lz - 29.5;
            if (Math.hypot(dx, dz) >= wallRadius(Math.atan2(dz, dx), cv.p1, cv.p2)) {
                // back off to the face of this block along the ray
                return r;
            }
        }
        return 23;
    }

    /** Ceiling height in the curved hall: coved low at the wall, then stepping up ring by ring into the atrium. */
    private static int ceilingAt(int f, double r, double t, double wr, Curve cv) {
        int max = maxCeil(f) - FLOORS[f];
        if (r > wr - 1.5) return 5;
        if (r > wr - 3) return 6;
        double atrium = 13 + 1.5 * Math.sin(2 * t + cv.p2);
        if (r > atrium) return 8;
        int steps = (int) ((atrium - r) / 2.2);
        return Math.min(max, 8 + 4 * (steps + 1));
    }

    // ================================================================== multi-level hall

    private void multilevel(int f, Plan p, int lx, int lz, int x, int z, B[] col) {
        int[] l = PoolroomsGenerator.w2l(lx, lz, p.face[f]);
        int a = l[0], b = l[1];
        int fy = FLOORS[f], ceil = Math.min(maxCeil(f), fy + (f == HIGH ? 26 : 20)), mez = fy + 8;
        int ring = Math.min(Math.min(a, b), Math.min(REG - 1 - a, REG - 1 - b));
        boolean bridge = b >= 28 && b <= 32 && a >= 7 && a <= REG - 8;
        boolean pit = a >= 16 && a <= 43 && b >= 16 && b <= 43;
        // floor
        if (pit) {
            boolean wet = b <= 29;
            if (wet) {
                set(col, fy - 5, lining(x, z));
                for (int yy = fy - 4; yy <= fy - 2; yy++) set(col, yy, B.WATER);
                for (int yy = fy - 1; yy <= fy; yy++) set(col, yy, B.AIR);
            } else {
                for (int yy = fy - 1; yy <= fy; yy++) set(col, yy, B.AIR);
                set(col, fy - 2, Math.floorMod(x, 3) == 0 ? POOL : TILE);
                // steps up to the main floor at the middle of the far side
                if (b == 43 && a >= 28 && a <= 31) set(col, fy - 1, TILE);
                if (b == 42 && a >= 28 && a <= 31) set(col, fy - 1, B.slab(Material.SMOOTH_QUARTZ_SLAB, false));
            }
        } else {
            set(col, fy, Math.floorMod(x, 6) == 0 || Math.floorMod(z, 6) == 0 ? POOL : TILE);
        }
        // the stair up to the balcony, along one side
        if ((a == 7 || a == 8) && b >= 20 && b <= 27) {
            int top = fy + 1 + (b - 20);
            for (int yy = fy + 1; yy <= top; yy++) set(col, yy, TILE);
            air(col, top + 1, ceil - 1);
            ceiling(col, ceil, x, z, 6, 15);
            return;
        }
        int under = pit ? fy - 1 : fy + 1;
        boolean balcony = ring >= 1 && ring <= 6;
        if (balcony || bridge) {
            boolean support = balcony && ring == 6 && Math.floorMod(a + b, 8) == 0 && !(a <= 8 && b >= 19 && b <= 28);
            if (support) {
                for (int yy = fy + 1; yy <= mez; yy++) set(col, yy, yy == fy + 1 || yy == fy + 4 ? POOL : TILE);
            } else {
                air(col, under, mez - 1);
                set(col, mez, Math.floorMod(x + z, 4) == 0 ? POOL : TILE);
            }
            // a low tile parapet along the open edges
            boolean parapet = (balcony && ring == 6 && !(a <= 7 && b >= 25 && b <= 28) && !(b >= 28 && b <= 32))
                    || (bridge && !balcony && (b == 28 || b == 32));
            if (parapet) set(col, mez + 1, POOL);
            air(col, mez + (parapet ? 2 : 1), ceil - 1);
            if (balcony && ring == 3 && Math.floorMod(a + b, 6) == 0) light(col, fy + 6, 14);
        } else {
            air(col, under, ceil - 1);
        }
        ceiling(col, ceil, x, z, 6, 15);
        if (!balcony && !bridge) hang(col, fy, ceil, x, z);
        else if (Math.floorMod(x, 8) == 5 && Math.floorMod(z, 8) == 5) light(col, mez + 6, 15);
        if (pit && Math.floorMod(a, 6) == 1 && Math.floorMod(b, 6) == 1) light(col, fy, 13);
    }

    // ================================================================== decorations

    void decor(Plan p, int f, int cx, int cz, List<DecorSpec> out) {
        long k = Rng.hash(p.var[f], p.rx, p.rz, f + 7);
        List<DecorSpec> all = decor.get(k);
        if (all == null) {
            if (decor.size() > 2048) decor.clear();
            all = List.copyOf(regionDecor(p, f));
            decor.put(k, all);
        }
        for (DecorSpec d : all) {
            if (g.cellOf((int) Math.floor(d.x)) == cx && g.cellOf((int) Math.floor(d.z)) == cz) out.add(d);
        }
    }

    private List<DecorSpec> regionDecor(Plan p, int f) {
        List<DecorSpec> out = new ArrayList<>();
        long h = Rng.hash(p.var[f], 0xDEC0);
        int x0 = p.rx * REG, z0 = p.rz * REG, fy = FLOORS[f] + 1;
        java.util.function.BiFunction<Integer, Integer, int[]> w = (a, b) -> PoolroomsGenerator.l2w(a, b, p.face[f]);
        switch (p.zone[f]) {
            case ENCLOSED -> {
                for (Room r : rooms(p, f).rooms()) {
                    long rh = Rng.hash(r.h(), 0xD3C);
                    double ca = (r.a0() + r.a1()) / 2.0 + 0.5, cb = (r.b0() + r.b1()) / 2.0 + 0.5;
                    if (r.type() == R_POOL && Rng.chance(rh, 0.7)) out.add(g.floatieAt(Rng.hash(rh, 1), x0 + ca, fy, z0 + cb));
                    if (r.type() == R_POOL && r.area() > 200 && Rng.chance(Rng.hash(rh, 2), 0.5))
                        out.add(g.floatieAt(Rng.hash(rh, 3), x0 + r.a0() + 2.5, fy, z0 + r.b0() + 2.5));
                    if (r.type() == R_PLAIN && Rng.chance(Rng.hash(rh, 4), 0.25))
                        out.add(new DecorSpec("pool_bench", x0 + r.a0() + 1.5, fy, z0 + cb, 90f, 0, 0, rh).barrier(x0 + r.a0() + 1, fy, (int) Math.floor(z0 + cb)));
                    if (r.type() == R_SHOWER && Rng.chance(Rng.hash(rh, 5), 0.5))
                        out.add(new DecorSpec("drain_grate", x0 + ca, fy + 0.001, z0 + cb, 0f, 0, 0, rh));
                    if (r.type() == R_STATUE) statueOnWall(out, rh, x0, z0, fy, r, rooms(p, f));
                }
            }
            case LONGPOOL -> {
                boolean ax = p.alongX[f];
                for (int i = 0; i < 6; i++) {
                    long ih = Rng.hash(h, i);
                    if (!Rng.chance(ih, 0.6)) continue;
                    double a = 4 + Rng.unit(Rng.hash(ih, 1)) * 52, b = 13 + Rng.unit(Rng.hash(ih, 2)) * 34;
                    out.add(g.floatieAt(Rng.hash(ih, 3), x0 + (ax ? a : b), fy, z0 + (ax ? b : a)));
                }
                for (int i = 0; i < 4; i++) {
                    long ih = Rng.hash(h, 20 + i);
                    if (!Rng.chance(ih, 0.5)) continue;
                    double a = 6 + i * 14 + 0.5, b = Rng.chance(Rng.hash(ih, 1), 0.5) ? 7.5 : REG - 7.5;
                    float yaw = ax ? (b < 30 ? 0f : 180f) : (b < 30 ? -90f : 90f);
                    out.add(new DecorSpec("pool_lounger", x0 + (ax ? a : b), fy, z0 + (ax ? b : a), yaw, 0, 0, ih));
                }
                if (Rng.chance(Rng.hash(h, 40), g.gs().get("statue-chance", 0.06) * 2)) {
                    // a face high in the side wall at the end of the pool, looking down the water
                    double a = 30 + 0.5, b = 6.002; // the inner face of the side wall
                    int scale = 5 + Rng.pick(Rng.hash(h, 41), 4);
                    float yaw = ax ? 0f : -90f;
                    out.add(statue(Rng.hash(h, 42), x0 + (ax ? a : b), fy + 3, z0 + (ax ? b : a), yaw, scale));
                }
            }
            case FLOODED -> {
                for (int i = 0; i < 8; i++) {
                    long ih = Rng.hash(h, i);
                    if (!Rng.chance(ih, 0.55)) continue;
                    int a = 16 + Rng.pick(Rng.hash(ih, 1), 40), b = 2 + Rng.pick(Rng.hash(ih, 2), 56);
                    int[] wl = w.apply(a, b);
                    out.add(g.floatieAt(Rng.hash(ih, 3), x0 + wl[0] + 0.5, fy, z0 + wl[1] + 0.5));
                }
                // the duck, as always
                int[] d = w.apply(24, 22);
                out.add(new DecorSpec("floatie_duck", x0 + d[0] + 0.5, fy - 0.12, z0 + d[1] + 0.5, (float) Rng.pick(Rng.hash(h, 50), 360), 0, 0, Rng.hash(h, 51)));
                for (int i = 0; i < 3; i++) {
                    int[] t = w.apply(4, 12 + i * 16);
                    out.add(new DecorSpec("pool_lounger", x0 + t[0] + 0.5, fy + 5, z0 + t[1] + 0.5, (float) (p.face[f] * 90 + 90), 0, 0, Rng.hash(h, 60 + i)));
                }
            }
            case CORRIDORS -> {
                out.add(g.floatieAt(Rng.hash(h, 1), x0 + 27.5, fy, z0 + 32.5));
                if (Rng.chance(Rng.hash(h, 2), 0.6)) out.add(g.floatieAt(Rng.hash(h, 3), x0 + 33.5, fy, z0 + 25.5));
                out.add(new DecorSpec("caustics", x0 + 29.5, maxCeil(f) - 0.02, z0 + 29.5, 0f, 0, 0, h));
                if (Rng.chance(Rng.hash(h, 4), g.gs().get("statue-chance", 0.06) * 3)) {
                    // a face in the chamber wall, its chin below the water line of the pool
                    int scale = 6 + Rng.pick(Rng.hash(h, 5), 5);
                    out.add(statue(Rng.hash(h, 6), x0 + 29.5, fy - 2, z0 + 18.001, 0f, scale));
                }
            }
            case CURVED -> {
                Curve cv = curve(p, f);
                out.add(g.floatieAt(Rng.hash(h, 1), x0 + 29.5 + 6, fy, z0 + 29.5 - 4));
                if (Rng.chance(Rng.hash(h, 2), 0.7)) out.add(g.floatieAt(Rng.hash(h, 3), x0 + 29.5 + 9, fy, z0 + 29.5 - 2));
                for (int i = 0; i < 4; i++) {
                    double t = cv.p1 + i * 1.57;
                    double r = 19;
                    out.add(new DecorSpec("pool_lounger", x0 + 29.5 + Math.cos(t) * r, fy, z0 + 29.5 + Math.sin(t) * r,
                            (float) Math.toDegrees(Math.atan2(Math.cos(t), -Math.sin(t))), 0, 0, Rng.hash(h, 10 + i)));
                }
                if (Rng.chance(Rng.hash(h, 20), g.gs().get("statue-chance", 0.06) * 2.5)) {
                    // a cluster of faces of different sizes set into the curved wall
                    int n = 1 + Rng.pick(Rng.hash(h, 21), 3);
                    double base = cv.p2;
                    for (int i = 0; i < n; i++) {
                        double t = base + i * 0.32;
                        double wr = wallHit(cv, t) + 0.02;
                        int bx = (int) Math.floor(29.5 + Math.cos(t) * (wr + 0.5)), bz = (int) Math.floor(29.5 + Math.sin(t) * (wr + 0.5));
                        if (curvedTunnel(bx, bz)) continue; // never over a passage through the wall
                        int scale = 2 + Rng.pick(Rng.hash(h, 30 + i), 4);
                        double yy = fy + 0.5 + Rng.pick(Rng.hash(h, 40 + i), 3);
                        float yaw = (float) Math.toDegrees(Math.atan2(Math.cos(t), -Math.sin(t))); // facing the centre
                        out.add(statue(Rng.hash(h, 50 + i), x0 + 29.5 + Math.cos(t) * wr, yy, z0 + 29.5 + Math.sin(t) * wr, yaw, scale));
                    }
                }
            }
            case MULTILEVEL -> {
                for (int i = 0; i < 4; i++) {
                    long ih = Rng.hash(h, i);
                    if (!Rng.chance(ih, 0.6)) continue;
                    int[] wl = w.apply(18 + Rng.pick(Rng.hash(ih, 1), 24), 18 + Rng.pick(Rng.hash(ih, 2), 10));
                    out.add(g.floatieAt(Rng.hash(ih, 3), x0 + wl[0] + 0.5, fy - 2, z0 + wl[1] + 0.5));
                }
                for (int i = 0; i < 4; i++) {
                    int[] wl = w.apply(14 + i * 9, 3);
                    out.add(new DecorSpec("pool_lounger", x0 + wl[0] + 0.5, fy + 9, z0 + wl[1] + 0.5, (float) (p.face[f] * 90), 0, 0, Rng.hash(h, 20 + i)));
                }
                if (Rng.chance(Rng.hash(h, 30), g.gs().get("statue-chance", 0.06) * 2)) {
                    // half drowned in the flooded side of the pit
                    int[] wl = w.apply(30, 16);
                    int[] fd = PoolroomsGenerator.rotDir(0, 1, p.face[f]);
                    out.add(statue(Rng.hash(h, 31), x0 + wl[0] + 0.5 - fd[0] * 0.5, fy - 4.5, z0 + wl[1] + 0.5 - fd[1] * 0.5,
                            (float) Math.toDegrees(Math.atan2(-fd[0], fd[1])), 7));
                }
            }
            default -> {
            }
        }
        return out;
    }

    /** A face set flush into one wall of a room (one without a doorway behind it), its size to fit the wall. */
    private void statueOnWall(List<DecorSpec> out, long h, int x0, int z0, int fy, Room r, Rooms rs) {
        int first = Rng.pick(h, 4);
        for (int k = 0; k < 4; k++) {
            int side = (first + k) % 4;
            int len = side < 2 ? r.b1() - r.b0() + 1 : r.a1() - r.a0() + 1;
            int scale = Math.max(2, Math.min(r.height() - 2, Math.min(len - 2, 3 + Rng.pick(Rng.hash(h, 1), 6))));
            int mid = side < 2 ? (r.b0() + r.b1()) / 2 : (r.a0() + r.a1()) / 2;
            int half = (int) Math.ceil(scale * 0.65) + 1;
            int at = switch (side) {
                case 0 -> r.a0() - 1;
                case 1 -> r.a1() + 1;
                case 2 -> r.b0() - 1;
                default -> r.b1() + 1;
            };
            if (!solidWall(rs, side < 2, at, mid - half, mid + half)) continue;
            double ca = (r.a0() + r.a1()) / 2.0 + 0.5, cb = (r.b0() + r.b1()) / 2.0 + 0.5;
            double y = fy + 0.5 + Rng.pick(Rng.hash(h, 2), Math.max(1, r.height() - scale - 1));
            switch (side) {
                case 0 -> out.add(statue(h, x0 + r.a0() + 0.001, y, z0 + cb, -90f, scale)); // west wall, facing east
                case 1 -> out.add(statue(h, x0 + r.a1() + 0.999, y, z0 + cb, 90f, scale));
                case 2 -> out.add(statue(h, x0 + ca, y, z0 + r.b0() + 0.001, 0f, scale));
                default -> out.add(statue(h, x0 + ca, y, z0 + r.b1() + 0.999, 180f, scale));
            }
            return;
        }
    }

    /** No doorway (or region port) along a wall line between {@code from} and {@code to}. */
    private static boolean solidWall(Rooms rs, boolean vertical, int at, int from, int to) {
        if (at == 0 || at == REG - 1) {
            for (int t = from; t <= to; t++) if (port(t)) return false;
            return true;
        }
        for (Wall w : rs.walls()) {
            if (w.vertical() != vertical || w.at() != at) continue;
            for (int t = from; t <= to; t++) {
                if (t < w.from() || t > w.to()) continue;
                if (vertical ? w.door(at, t) : w.door(t, at)) return false;
            }
        }
        return true;
    }

    /**
     * A sculpted face relief ({@code statue_face:<scale>:<expression>}) whose back is flush with a wall at (x, y, z),
     * facing {@code yaw}, chin at {@code y}. Expression, a slight tilt and how deep it sits vary per statue.
     */
    static DecorSpec statue(long h, double x, double y, double z, float yaw, int scale) {
        int expr = Rng.pick(Rng.hash(h, 7), 4);
        float roll = (float) ((Rng.unit(Rng.hash(h, 8)) - 0.5) * 16);
        float pitch = (float) ((Rng.unit(Rng.hash(h, 9)) - 0.5) * 8);
        return new DecorSpec("statue_face:" + scale + ":" + expr, x, y, z, yaw, pitch, roll, h).structural();
    }
}
