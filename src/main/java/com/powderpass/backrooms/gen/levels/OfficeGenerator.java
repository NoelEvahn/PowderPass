package com.powderpass.backrooms.gen.levels;

import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.gen.B;
import com.powderpass.backrooms.gen.CellCtx;
import com.powderpass.backrooms.gen.CustomBlocks;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Level 4 — the Office Towers. A grid of 2000s office towers standing in an endless night: no ground, only the
 * dark far below. Every 64-block plot holds one tower of 3–8 floors (5 blocks each) around a central core with a
 * switchback stairwell and a lift. Floors are open plan: cubicle pods behind low partitions, private glass-fronted
 * offices and conference rooms along some facades for the more important people, a lobby with a reception desk on
 * the ground floor, glass curtain walls all round. Enclosed or railed sky bridges join neighbouring towers at
 * various floors: every tower is bridged to its east neighbour, and north–south bridges run at least once in every
 * three columns, so any window of towers is connected.
 *
 * <p>Everything is a pure function of the seed and coordinates: a tower's shape comes from its plot hash, its
 * bridges from the plot pair, and each floor's furniture plan ({@link Plan}) from the tower and floor index. The
 * painter evaluates one voxel at a time from those plans, so chunks paint independently and concurrently.</p>
 */
public final class OfficeGenerator extends LevelGenerator {
    public static final int BASE = 80;
    public static final int FH = 5;
    public static final int MAX_FLOORS = 14;
    public static final int CELL = 84;
    private static final int[] SIZES = {45, 53, 61, 69};

    // ------------------------------------------------------------------ palette
    private static final B FACADE = CustomBlocks.get("tower_facade");
    private static final B FACADE_DARK = CustomBlocks.get("tower_facade_dark");
    private static final B CARPET = CustomBlocks.get("tower_floor_tile");
    private static final B LOBBY = CustomBlocks.get("tower_lobby_tile");
    private static final B PARTITION = CustomBlocks.get("tower_partition");
    private static final B WALL = CustomBlocks.get("tower_wall");
    private static final B COLUMN = CustomBlocks.get("tower_column");
    private static final B STEEL = CustomBlocks.get("tower_elevator");
    private static final B ROOF = CustomBlocks.get("tower_roof");
    private static final B CEIL = CustomBlocks.get("tower_ceiling");
    private static final B PANEL = CustomBlocks.get("light_panel_cool");
    private static final B PANEL_OFF = CustomBlocks.get("light_panel_off");
    private static final B GLASS = B.of(Material.GLASS);
    private static final B GLASS_BLUE = B.of(Material.LIGHT_BLUE_STAINED_GLASS);
    private static final B GLASS_DEAD = B.of(Material.BLACK_STAINED_GLASS);
    private static final B BRIGHT_GLASS = B.of(Material.WHITE_STAINED_GLASS);
    private static final B PART_CAP = B.slab(Material.SMOOTH_STONE_SLAB, false);
    private static final B DESK = B.slab(Material.BIRCH_SLAB, true);
    private static final B EXEC_DESK = B.slab(Material.DARK_OAK_SLAB, true);
    private static final B TABLE = B.slab(Material.DARK_OAK_SLAB, true);
    private static final B SHELF = B.of(Material.BOOKSHELF);
    private static final B PLANTER = B.of(Material.BROWN_TERRACOTTA);
    private static final B PLANT = B.of(Material.AZALEA);
    private static final B PLANT_B = B.of(Material.FLOWERING_AZALEA);
    private static final B COUNTER = B.of(Material.SMOOTH_QUARTZ);
    private static final B RECEPTION = B.of(Material.STRIPPED_DARK_OAK_WOOD, "[axis=y]");
    private static final B RECEPTION_TOP = B.slab(Material.SMOOTH_QUARTZ_SLAB, false);
    private static final B EXEC_FLOOR = B.of(Material.RED_TERRACOTTA);
    private static final B CONF_FLOOR = B.of(Material.CYAN_TERRACOTTA);
    private static final B GRASS = B.of(Material.GRASS_BLOCK);
    private static final B LANDING = B.of(Material.POLISHED_ANDESITE);
    private static final B STAIR_S = B.stairs(Material.POLISHED_ANDESITE_STAIRS, "south", false);
    private static final B STAIR_N = B.stairs(Material.POLISHED_ANDESITE_STAIRS, "north", false);
    private static final B BARREL = B.of(Material.BARREL, "[facing=up]");
    private static final B SLAB_FILL = B.of(Material.GRAY_CONCRETE);
    private static final B BARS_EW = B.of(Material.IRON_BARS, "[east=true,north=false,south=false,waterlogged=false,west=true]");
    private static final B BARS_NS = B.of(Material.IRON_BARS, "[east=false,north=true,south=true,waterlogged=false,west=false]");
    private static final B BARS_NW = B.of(Material.IRON_BARS, "[east=true,north=false,south=true,waterlogged=false,west=false]");
    private static final B BARS_NE = B.of(Material.IRON_BARS, "[east=false,north=false,south=true,waterlogged=false,west=true]");
    private static final B BARS_SW = B.of(Material.IRON_BARS, "[east=true,north=true,south=false,waterlogged=false,west=false]");
    private static final B BARS_SE = B.of(Material.IRON_BARS, "[east=false,north=true,south=false,waterlogged=false,west=true]");

    // ------------------------------------------------------------------ plan codes
    static final byte FREE = 0, FACADE_C = 1, CORE = 2, PART = 3, DESK_C = 4, EXEC_C = 5, WALL_C = 6, GLASSW = 7, DOOR = 8,
            SHELF_C = 9, PLANTER_C = 10, COUNTER_C = 11, RECEPTION_C = 12, OPENING = 13, TABLE_C = 14, BRIGHT = 15, BARREL_C = 16,
            RACK_C = 17, MACHINE_C = 18, COLUMN_C = 19, VOID_C = 20, ATRIUM_C = 21;
    static final byte FM_CARPET = 0, FM_LOBBY = 1, FM_EXEC = 2, FM_CONF = 3, FM_GRASS = 4, FM_CONCRETE = 5;
    private static final B CONCRETE_FLOOR = B.of(Material.LIGHT_GRAY_CONCRETE);
    private static final B MACHINE = B.of(Material.GRAY_CONCRETE);
    private static final B MACHINE_TOP = B.of(Material.IRON_BLOCK);
    private static final B RACK = B.of(Material.SPRUCE_PLANKS);
    private static final B RACK_SHELF = B.slab(Material.SPRUCE_SLAB, true);
    private static final String[] DOOR_FACING = {"east", "south", "west", "north"}; // facing +d for bands W, N, E, S

    /** A sky bridge. {@code ew}: runs along X (walkway z = lo..lo+2) else along Z (walkway x = lo..lo+2). */
    public record Bridge(boolean ew, int floor, int lo, int from, int to, boolean enclosed) {
        public boolean contains(int x, int z) {
            int along = ew ? x : z, cross = ew ? z : x;
            return along >= from && along <= to && cross >= lo - 1 && cross <= lo + 3;
        }
    }

    /** A doorway in a tower facade where a bridge lands. side 0=W,1=N,2=E,3=S; lo = tower-local along coordinate. */
    record Opening(int side, int floor, int lo) {
    }

    /** One tower on its plot. Coordinates: world NW outer corner (x0,z0), outer size w (x) by l (z). */
    public static final class Tower {
        public final int cx, cz, x0, z0, w, l, floors, ci0, cj0;
        final boolean dark;
        final long h;
        final List<Opening> openings = new ArrayList<>();
        final List<Bridge> own = new ArrayList<>();

        Tower(int cx, int cz, int x0, int z0, int w, int l, int floors, int ci0, int cj0, boolean dark, long h) {
            this.cx = cx;
            this.cz = cz;
            this.x0 = x0;
            this.z0 = z0;
            this.w = w;
            this.l = l;
            this.floors = floors;
            this.ci0 = ci0;
            this.cj0 = cj0;
            this.dark = dark;
            this.h = h;
        }

        public boolean inside(int x, int z) {
            return x >= x0 && x < x0 + w && z >= z0 && z < z0 + l;
        }

        /** Ring aisle around the core: {ri0, rj0, ri1, rj1} tower-local. */
        int ri0() {
            return ci0 - 2;
        }

        int ri1() {
            return ci0 + 11;
        }

        int rj0() {
            return cj0 - 3;
        }

        int rj1() {
            return cj0 + 11;
        }

        public List<Bridge> bridges() {
            return own;
        }
    }

    /** Lift car of one tower: interior x carX..carX+2, z carZ..carZ+2; the door is at (doorX, doorZ) on the north wall. */
    public record Lift(int cx, int cz, int floors, int carX, int carZ, int doorX, int doorZ) {
        public int feetY(int floor) {
            return BASE + FH * floor + 1;
        }

        public boolean inCar(double x, double z) {
            return x >= carX && x < carX + 3 && z >= carZ && z < carZ + 3;
        }

        /** Floor whose car contains a feet y, or -1. */
        public int floorAt(double y) {
            int f = (int) Math.floor((y - BASE - 1) / FH + 0.2);
            if (f < 0 || f >= floors) return -1;
            double rel = y - feetY(f);
            return rel > -0.6 && rel < 2.5 ? f : -1;
        }
    }

    /** A display prop of a floor plan, tower-local (cell centre = i + 0.5), dy above the floor's feet level. */
    record Prop(String model, double i, double dy, double j, float yaw, long seed, int[] barriers) {
    }

    /** One floor of one tower: per-column codes, floor finish, door facing, props, light state. */
    static final class Plan {
        final int w, l;
        final byte[] code, fm, dir;
        final boolean[] reserved;
        final List<Prop> props = new ArrayList<>();
        double lit = 0.92;
        String kind = "open plan";
        int[] pit; // lobby feature pit {i0, j0}: steps j0..j0+2, landing j0+3..j0+4, i0..i0+2
        int[] window; // bright window {side, along0}
        /** A double-height atrium starting on this floor: tower-local {i0, j0, i1, j1} of the void, or null. */
        int[] atrium;
        /** Share of ceiling tiles that have fallen (abandoned floors). */
        double missingCeiling;

        Plan(int w, int l) {
            this.w = w;
            this.l = l;
            code = new byte[w * l];
            fm = new byte[w * l];
            dir = new byte[w * l];
            reserved = new boolean[w * l];
        }

        byte at(int i, int j) {
            return i < 0 || j < 0 || i >= w || j >= l ? FACADE_C : code[j * w + i];
        }

        void set(int i, int j, byte c) {
            if (i >= 0 && j >= 0 && i < w && j < l) code[j * w + i] = c;
        }

        boolean res(int i, int j) {
            return i < 0 || j < 0 || i >= w || j >= l || reserved[j * w + i];
        }

        void reserve(int i0, int j0, int i1, int j1) {
            for (int i = Math.max(0, Math.min(i0, i1)); i <= Math.min(w - 1, Math.max(i0, i1)); i++)
                for (int j = Math.max(0, Math.min(j0, j1)); j <= Math.min(l - 1, Math.max(j0, j1)); j++) reserved[j * w + i] = true;
        }
    }

    private final Map<Long, Tower> towers = new ConcurrentHashMap<>();
    private final Map<Long, Plan> plans = new ConcurrentHashMap<>();

    public OfficeGenerator(long seed, GenSettings gs) {
        super(Level.LEVEL_4, seed, gs, CELL, 3, 2, floorLevels(), 0, BASE + FH * MAX_FLOORS + 12, 0.0, 0.0);
    }

    private static int[] floorLevels() {
        int[] f = new int[MAX_FLOORS];
        for (int i = 0; i < MAX_FLOORS; i++) f[i] = BASE + FH * i;
        return f;
    }

    @Override
    protected List<FeatureDef> featureDefs() {
        return List.of(
                // onward to the Plains: a window on a tower's top floor that is far too bright, in every district
                FeatureDef.transition("bright_window", Dest.to(Level.PLAINS), 1.0),
                // back to the Habitable Zone: service stairs going down below a lobby, rarer
                FeatureDef.transition("basement_stairs", Dest.to(Level.LEVEL_1), gs.get("back-chance", 0.4)));
    }

    @Override
    public String biomeKey() {
        return "minecraft:the_void";
    }

    /** Chance that a lift ride ends somewhere else entirely (a random level). */
    public double liftStrayChance() {
        return gs.get("lift-stray-chance", 0.10);
    }

    // ------------------------------------------------------------------ towers and bridges

    private static long key(int a, int b) {
        return ((long) a << 32) ^ (b & 0xffffffffL);
    }

    private Tower base(int cx, int cz) {
        long h = ch(0, cx, cz, 0x70E3);
        int w = SIZES[Rng.pick(Rng.hash(h, 1), SIZES.length)];
        int l = SIZES[Rng.pick(Rng.hash(h, 2), SIZES.length)];
        int ox = (CELL - w) / 2 + Rng.range(Rng.hash(h, 3), -(CELL - w) / 2 + 3, (CELL - w) / 2 - 3);
        int oz = (CELL - l) / 2 + Rng.range(Rng.hash(h, 4), -(CELL - l) / 2 + 3, (CELL - l) / 2 - 3);
        // a skyline: broad swells of taller towers, with per-tower jitter
        double sky = zone(cx, cz, 0x5C1E, 260);
        int floors = 5 + (int) Math.round(Math.max(0, Math.min(1, (sky - 0.25) / 0.5)) * 6 + Rng.unit(Rng.hash(h, 5)) * 3.5);
        floors = Math.max(4, Math.min(MAX_FLOORS, floors));
        int ci0 = (w - 10) / 2 + Rng.range(Rng.hash(h, 6), -1, 1);
        int cj0 = (l - 10) / 2 + Rng.range(Rng.hash(h, 7), -1, 1);
        return new Tower(cx, cz, cx * CELL + ox, cz * CELL + oz, w, l, floors, ci0, cj0, Rng.chance(Rng.hash(h, 8), 0.45), h);
    }

    public Tower tower(int cx, int cz) {
        long k = key(cx, cz);
        Tower t = towers.get(k);
        if (t != null) return t;
        if (towers.size() > 4096) towers.clear();
        t = base(cx, cz);
        Tower e = base(cx + 1, cz), wn = base(cx - 1, cz), s = base(cx, cz + 1), n = base(cx, cz - 1);
        for (Bridge b : bridgesBetween(t, e, true)) {
            t.own.add(b);
            t.openings.add(new Opening(2, b.floor, b.lo - t.z0));
        }
        for (Bridge b : bridgesBetween(wn, t, true)) {
            t.own.add(b);
            t.openings.add(new Opening(0, b.floor, b.lo - t.z0));
        }
        for (Bridge b : bridgesBetween(t, s, false)) {
            t.own.add(b);
            t.openings.add(new Opening(3, b.floor, b.lo - t.x0));
        }
        for (Bridge b : bridgesBetween(n, t, false)) {
            t.own.add(b);
            t.openings.add(new Opening(1, b.floor, b.lo - t.x0));
        }
        towers.put(k, t);
        return t;
    }

    /** Bridges from tower a to its east (ew) or south neighbour b. */
    private List<Bridge> bridgesBetween(Tower a, Tower b, boolean ew) {
        long h = Rng.hash(seed ^ 0xB81D, a.cx, a.cz, ew ? 1 : 2);
        List<Bridge> out = new ArrayList<>(2);
        if (!ew && Math.floorMod(a.cx, 3) != 1 && !Rng.chance(h, gs.get("bridge-chance", 0.35))) return out;
        int minF = Math.min(a.floors, b.floors);
        int lo0, lo1, from, to;
        if (ew) {
            lo0 = Math.max(a.z0, b.z0) + 4;
            lo1 = Math.min(a.z0 + a.l, b.z0 + b.l) - 7;
            from = a.x0 + a.w;
            to = b.x0 - 1;
        } else {
            lo0 = Math.max(a.x0, b.x0) + 4;
            lo1 = Math.min(a.x0 + a.w, b.x0 + b.w) - 7;
            from = a.z0 + a.l;
            to = b.z0 - 1;
        }
        int f1 = 1 + Rng.pick(Rng.hash(h, 1), minF - 1);
        // every bridge is a fully enclosed glass corridor (see paintBridge and the skybridge display models)
        out.add(new Bridge(ew, f1, Rng.range(Rng.hash(h, 2), lo0, lo1), from, to, true));
        if (minF >= 5 && Rng.chance(Rng.hash(h, 4), 0.45)) {
            int f2 = f1 + 3 <= minF - 1 ? f1 + 3 : f1 - 3;
            if (f2 >= 1) out.add(new Bridge(ew, f2, Rng.range(Rng.hash(h, 5), lo0, lo1), from, to, true));
        }
        return out;
    }

    /** All bridges whose footprint may cross cell (cx,cz): its own east/south ones and its neighbours' toward it. */
    private List<Bridge> bridgesNear(int cx, int cz) {
        List<Bridge> out = new ArrayList<>(tower(cx, cz).own);
        return out;
    }

    public Lift lift(int cx, int cz) {
        Tower t = tower(cx, cz);
        return new Lift(cx, cz, t.floors, t.x0 + t.ci0 + 6, t.z0 + t.cj0 + 1, t.x0 + t.ci0 + 7, t.z0 + t.cj0);
    }

    /** The lift whose car contains this position (any floor), or null. */
    /** The lift of the tower whose plot contains (x, z). */
    public Lift liftNear(double x, double z) {
        return lift(cellOf((int) Math.floor(x)), cellOf((int) Math.floor(z)));
    }

    public Lift liftAt(double x, double y, double z) {
        Lift l = lift(cellOf((int) Math.floor(x)), cellOf((int) Math.floor(z)));
        return l.inCar(x, z) && l.floorAt(y) >= 0 ? l : null;
    }

    /** Floor finish of a tower-local cell on a floor: 0 carpet, 1 lobby, 2 private office, 3 conference, 4 grass. */
    public int finish(Tower t, int floor, int i, int j) {
        return plan(t, floor).fm[j * t.w + i];
    }

    /** Plan code of a tower-local cell on a floor (see the code constants; 0 = open floor). */
    public int code(Tower t, int floor, int i, int j) {
        return plan(t, floor).at(i, j);
    }

    /** What kind of floor this is ("lobby", "open plan", "private offices", "executive floor", "dark floor"...). */
    public String floorKind(Tower t, int floor) {
        return plan(t, floor).kind;
    }

    // ------------------------------------------------------------------ floor plans

    Plan plan(Tower t, int f) {
        long k = Rng.hash(key(t.cx, t.cz), f);
        Plan p = plans.get(k);
        if (p != null) return p;
        if (plans.size() > 2048) plans.clear();
        p = buildPlan(t, f);
        plans.put(k, p);
        return p;
    }

    /** Band of floor between the core ring and one facade. a = along the facade, d = depth from the glass. */
    private static final class Band {
        final int side, a0, a1, depth, w, l;

        Band(int side, int a0, int a1, int depth, int w, int l) {
            this.side = side;
            this.a0 = a0;
            this.a1 = a1;
            this.depth = depth;
            this.w = w;
            this.l = l;
        }

        int i(int a, int d) {
            return switch (side) {
                case 0 -> 1 + d;
                case 2 -> w - 2 - d;
                default -> a;
            };
        }

        int j(int a, int d) {
            return switch (side) {
                case 1 -> 1 + d;
                case 3 -> l - 2 - d;
                default -> a;
            };
        }

        /** Yaw of something facing deeper into the floor (away from this band's glass). */
        float yawIn() {
            return switch (side) {
                case 0 -> -90f;
                case 1 -> 0f;
                case 2 -> 90f;
                default -> 180f;
            };
        }

        float yawOut() {
            return yawIn() + 180f;
        }
    }

    private Band[] bands(Tower t) {
        int w = t.w, l = t.l;
        return new Band[]{
                new Band(0, t.rj0() + 2, t.rj1() - 2, t.ri0() - 1, w, l),
                new Band(1, 1, w - 2, t.rj0() - 1, w, l),
                new Band(2, t.rj0() + 2, t.rj1() - 2, w - 2 - t.ri1(), w, l),
                new Band(3, 1, w - 2, l - 2 - t.rj1(), w, l)};
    }

    /** Tower-local cell of a band position. */
    private static int[] ij(Band b, int a, int d) {
        return new int[]{b.i(a, d), b.j(a, d)};
    }

    private Plan buildPlan(Tower t, int f) {
        int w = t.w, l = t.l;
        Plan p = new Plan(w, l);
        long h = Rng.hash(t.h, 0xF100 + f);
        boolean lobby = f == 0;
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < l; j++) {
                if (i == 0 || j == 0 || i == w - 1 || j == l - 1) p.set(i, j, FACADE_C);
                else if (i >= t.ci0 && i <= t.ci0 + 9 && j >= t.cj0 && j <= t.cj0 + 9) p.set(i, j, CORE);
                if (lobby) p.fm[j * w + i] = FM_LOBBY;
            }
        }
        p.reserve(t.ri0(), t.rj0(), t.ri1(), t.rj1());
        // bridge doorways and their clear spokes to the ring
        for (Opening o : t.openings) {
            if (o.floor != f) continue;
            for (int k = 0; k < 3; k++) {
                int[] c = facadeCell(t, o.side, o.lo + k);
                p.set(c[0], c[1], OPENING);
            }
            spoke(t, p, o.side, o.lo);
        }
        Feature feat = featureAt(0, t.cx, t.cz);
        if (feat != null && feat.id().equals("bright_window") && f == t.floors - 1) {
            int[] win = brightWindow(t);
            p.window = win;
            for (int k = 0; k < 3; k++) {
                int[] c = facadeCell(t, win[0], win[1] + k);
                p.set(c[0], c[1], BRIGHT);
                int[] in = inward(t, win[0], win[1] + k, 1);
                p.fm[in[1] * w + in[0]] = FM_GRASS;
            }
            spoke(t, p, win[0], win[1]);
            p.props.add(new Prop("daylight_window", 0, 0, 0, 0, h, null)); // placed specially in decor
        }
        if (feat != null && feat.id().equals("basement_stairs") && lobby) {
            int i0 = t.ci0 + 3, j0 = t.rj1() + 2;
            p.pit = new int[]{i0, j0};
            p.reserve(i0 - 1, t.rj1() + 1, i0 + 3, j0 + 6);
        }
        Band[] bands = bands(t);
        if (lobby) {
            p.kind = "lobby";
            p.lit = 1.0;
            lobby(t, p, bands, h);
        } else {
            boolean top = f == t.floors - 1;
            // the upper floor of an atrium: its floor is open over the void below, behind a low wall
            Plan below = f >= 2 ? plan(t, f - 1) : null;
            if (below != null && below.atrium != null) {
                int[] a = below.atrium;
                for (int i = a[0]; i <= a[2]; i++) for (int j = a[1]; j <= a[3]; j++) p.set(i, j, VOID_C);
                for (int i = a[0] - 1; i <= a[2] + 1; i++) for (int j = a[1] - 1; j <= a[3] + 1; j++) {
                    boolean ring = i == a[0] - 1 || i == a[2] + 1 || j == a[1] - 1 || j == a[3] + 1;
                    if (ring && p.at(i, j) == FREE && !p.res(i, j)) p.set(i, j, PART);
                }
                p.reserve(a[0] - 1, a[1] - 1, a[2] + 1, a[3] + 1);
            }
            double r = Rng.unit(Rng.hash(h, 1));
            String kind = top ? "executive" : r < 0.34 ? "open" : r < 0.46 ? "offices" : r < 0.56 ? "meeting" : r < 0.63 ? "storage"
                    : r < 0.69 ? "maintenance" : r < 0.75 ? "abandoned" : r < 0.81 ? "empty" : r < 0.88 ? "warren" : r < 0.94 ? "atrium" : "open";
            if (kind.equals("atrium") && (f >= t.floors - 2 || below != null && below.atrium != null)) kind = "open";
            if (!top && Rng.chance(Rng.hash(h, 9), gs.get("dark-floor-chance", 0.12))) {
                p.lit = 0.12;
                p.kind = "dark floor";
            }
            int execBand = Rng.chance(Rng.hash(h, 2), 0.65) ? Rng.pick(Rng.hash(h, 3), 4) : -1;
            switch (kind) {
                case "executive" -> {
                    for (Band b : bands) {
                        if (Rng.chance(Rng.hash(h, 10 + b.side), 0.75) && b.depth >= 8) execRow(t, p, b, Rng.hash(h, 20 + b.side));
                        else openBand(t, p, b, Rng.hash(h, 30 + b.side));
                    }
                    p.kind = "executive floor";
                }
                case "offices" -> {
                    for (Band b : bands) {
                        if (b.depth >= 8 && (b.side == execBand || Rng.chance(Rng.hash(h, 40 + b.side), 0.6))) execRow(t, p, b, Rng.hash(h, 20 + b.side));
                        else openBand(t, p, b, Rng.hash(h, 30 + b.side));
                    }
                    p.kind = "private offices";
                }
                case "meeting" -> {
                    for (Band b : bands) {
                        if (b.depth >= 8) meetingRow(t, p, b, Rng.hash(h, 50 + b.side));
                        else openBand(t, p, b, Rng.hash(h, 30 + b.side));
                    }
                    p.kind = "meeting rooms";
                }
                case "storage" -> {
                    for (Band b : bands) storageBand(t, p, b, Rng.hash(h, 60 + b.side));
                    p.kind = "storage";
                    p.lit = Math.min(p.lit, 0.6);
                }
                case "maintenance" -> {
                    for (Band b : bands) maintenanceBand(t, p, b, Rng.hash(h, 70 + b.side));
                    p.kind = "maintenance";
                    p.lit = Math.min(p.lit, 0.35);
                }
                case "abandoned" -> {
                    for (Band b : bands) openBand(t, p, b, Rng.hash(h, 30 + b.side));
                    abandon(t, p, Rng.hash(h, 80));
                    p.kind = "abandoned floor";
                    p.lit = Math.min(p.lit, 0.22);
                    p.missingCeiling = 0.18;
                }
                case "empty" -> {
                    // nothing: a whole floor of carpet and columns stretching to the glass
                    for (int i = 4; i < t.w - 3; i += 8) for (int j = 4; j < t.l - 3; j += 8) {
                        if (!p.res(i, j) && p.at(i, j) == FREE) p.set(i, j, COLUMN_C);
                    }
                    p.kind = "empty floor";
                }
                case "warren" -> {
                    for (Band b : bands) warrenBand(t, p, b, Rng.hash(h, 90 + b.side));
                    p.kind = "corridors";
                    p.lit = Math.min(p.lit, 0.7);
                }
                case "atrium" -> {
                    Band best = bands[0];
                    for (Band b : bands) if (b.depth > best.depth) best = b;
                    if (!atrium(t, p, best, Rng.hash(h, 100))) {
                        for (Band b : bands) openBand(t, p, b, Rng.hash(h, 30 + b.side));
                    } else {
                        for (Band b : bands) if (b != best) openBand(t, p, b, Rng.hash(h, 30 + b.side));
                        p.kind = "atrium";
                    }
                }
                default -> {
                    for (Band b : bands) {
                        if (b.side == execBand && b.depth >= 8) execRow(t, p, b, Rng.hash(h, 20 + b.side));
                        else openBand(t, p, b, Rng.hash(h, 30 + b.side));
                    }
                }
            }
        }
        return p;
    }

    // ------------------------------------------------------------------ more kinds of floor

    /** A row of glass meeting rooms (each a conference room with its table and chairs). */
    private void meetingRow(Tower t, Plan p, Band b, long h) {
        boolean ns = b.side == 1 || b.side == 3;
        int start = ns ? 0 : b.a0, end = ns ? t.w - 1 : b.a1;
        int cur = start, idx = 0;
        while (cur + 10 <= end) {
            long hh = Rng.hash(h, idx++);
            int width = 10 + Rng.pick(hh, 3);
            int next = Math.min(end, cur + width);
            if (end - next < 10) next = end;
            office(t, p, b, cur, next, next - cur >= 10, hh);
            cur = next;
        }
    }

    /** Storage: rows of tall shelving racks parallel to the glass with narrow aisles, barrels between them. */
    private void storageBand(Tower t, Plan p, Band b, long h) {
        boolean ns = b.side == 1 || b.side == 3;
        int a0 = ns ? 2 : b.a0, a1 = ns ? t.w - 3 : b.a1;
        for (int d = 1; d <= b.depth - 2; d += 3) {
            for (int a = a0; a <= a1; a++) {
                if (Math.floorMod(a - a0, 9) == 8) continue; // a gap to cross between aisles
                if (!free(p, b, a, d, a, d)) continue;
                put(p, b, a, d, Rng.chance(Rng.hash(h, a, d), 0.08) ? BARREL_C : RACK_C);
            }
        }
    }

    /** Maintenance: bare concrete, banks of humming machinery, a few barrels; dim. */
    private void maintenanceBand(Tower t, Plan p, Band b, long h) {
        boolean ns = b.side == 1 || b.side == 3;
        int a0 = ns ? 2 : b.a0, a1 = ns ? t.w - 3 : b.a1;
        for (int a = a0; a <= a1; a++) for (int d = 0; d <= b.depth; d++) {
            int i = b.i(a, d), j = b.j(a, d);
            if (i > 0 && j > 0 && i < p.w - 1 && j < p.l - 1 && p.at(i, j) != CORE) p.fm[j * p.w + i] = FM_CONCRETE;
        }
        for (int a = a0 + 1; a + 3 <= a1; a += 7) {
            if (!Rng.chance(Rng.hash(h, a), 0.7)) continue;
            int depth = Math.min(b.depth - 3, 2 + Rng.pick(Rng.hash(h, a, 1), 3));
            if (depth < 1 || !free(p, b, a, 1, a + 3, depth)) continue;
            for (int x = a; x <= a + 3; x++) for (int d = 1; d <= depth; d++) put(p, b, x, d, MACHINE_C);
        }
        for (int a = a0; a <= a1; a += 5) if (free(p, b, a, b.depth - 1, a, b.depth - 1) && Rng.chance(Rng.hash(h, a, 2), 0.3)) put(p, b, a, b.depth - 1, BARREL_C);
    }

    /** An abandoned floor: partitions fallen, desks gone, chairs scattered and knocked over. */
    private void abandon(Tower t, Plan p, long h) {
        for (int i = 1; i < p.w - 1; i++) for (int j = 1; j < p.l - 1; j++) {
            byte c = p.at(i, j);
            if ((c == PART || c == DESK_C) && Rng.chance(Rng.hash(h, i, j), 0.45)) p.set(i, j, FREE);
        }
        p.props.removeIf(pr -> !pr.model().equals("task_chair") && Rng.chance(Rng.hash(h, (long) (pr.i() * 7), (long) (pr.j() * 7)), 0.6));
        List<Prop> fallen = new ArrayList<>();
        for (Prop pr : p.props) {
            if (pr.model().equals("task_chair")) fallen.add(new Prop("fallen_chair", pr.i(), 0, pr.j(), pr.yaw() + 70f, pr.seed(), null));
            else fallen.add(pr);
        }
        p.props.clear();
        p.props.addAll(fallen);
    }

    /** A warren: the band filled with plasterboard except narrow corridors every four rows, doors through. */
    private void warrenBand(Tower t, Plan p, Band b, long h) {
        boolean ns = b.side == 1 || b.side == 3;
        int a0 = ns ? 1 : b.a0, a1 = ns ? t.w - 2 : b.a1;
        for (int a = a0; a <= a1; a++) {
            for (int d = 0; d <= b.depth; d++) {
                int i = b.i(a, d), j = b.j(a, d);
                if (p.res(i, j) || p.at(i, j) != FREE) continue;
                boolean corridor = Math.floorMod(d, 4) == 1 || Math.floorMod(a - a0, 6) == 2;
                if (!corridor) put(p, b, a, d, WALL_C);
            }
        }
        // the odd dead end: some cross corridors walled shut
        for (int a = a0 + 2; a <= a1; a += 6) {
            if (!Rng.chance(Rng.hash(h, a), 0.3)) continue;
            int d = 2 + 4 * Rng.pick(Rng.hash(h, a, 1), Math.max(1, b.depth / 4));
            if (d < b.depth && free(p, b, a, d, a, d)) put(p, b, a, d, WALL_C);
        }
    }

    /** A double-height atrium in a band: a void through the floor above, with a planter at its middle. */
    private boolean atrium(Tower t, Plan p, Band b, long h) {
        if (b.depth < 10) return false;
        boolean ns = b.side == 1 || b.side == 3;
        int a0 = ns ? 4 : b.a0 + 1, a1 = ns ? t.w - 5 : b.a1 - 1;
        int len = Math.min(a1 - a0, 12 + Rng.pick(h, 8));
        if (len < 8) return false;
        int s = a0 + Rng.pick(Rng.hash(h, 1), Math.max(1, a1 - a0 - len));
        int d0 = 2, d1 = b.depth - 3;
        if (!free(p, b, s, d0, s + len, d1)) return false;
        int[] c0 = ij(b, s, d0), c1 = ij(b, s + len, d1);
        p.atrium = new int[]{Math.min(c0[0], c1[0]), Math.min(c0[1], c1[1]), Math.max(c0[0], c1[0]), Math.max(c0[1], c1[1])};
        for (int a = s; a <= s + len; a++) for (int d = d0; d <= d1; d++) put(p, b, a, d, ATRIUM_C);
        p.reserve(p.atrium[0], p.atrium[1], p.atrium[2], p.atrium[3]);
        int mi = (p.atrium[0] + p.atrium[2]) / 2, mj = (p.atrium[1] + p.atrium[3]) / 2;
        p.set(mi, mj, PLANTER_C);
        p.props.add(new Prop("lobby_sofa", mi + 2.5, 0, mj + 0.5, 90f, h, null));
        return true;
    }

    /** Tower-local facade cell at {@code along} on a side. */
    private static int[] facadeCell(Tower t, int side, int along) {
        return switch (side) {
            case 0 -> new int[]{0, along};
            case 1 -> new int[]{along, 0};
            case 2 -> new int[]{t.w - 1, along};
            default -> new int[]{along, t.l - 1};
        };
    }

    /** Interior cell {@code depth} rows in from a facade cell (depth 1 = first row inside the glass). */
    private static int[] inward(Tower t, int side, int along, int depth) {
        return switch (side) {
            case 0 -> new int[]{depth, along};
            case 1 -> new int[]{along, depth};
            case 2 -> new int[]{t.w - 1 - depth, along};
            default -> new int[]{along, t.l - 1 - depth};
        };
    }

    /** Keep a 3-wide lane clear from a facade doorway straight in to the core ring. */
    private static void spoke(Tower t, Plan p, int side, int lo) {
        switch (side) {
            case 0 -> p.reserve(1, lo, t.ri0(), lo + 2);
            case 2 -> p.reserve(t.ri1(), lo, t.w - 2, lo + 2);
            case 1 -> p.reserve(lo, 1, lo + 2, t.rj0());
            default -> p.reserve(lo, t.rj1(), lo + 2, t.l - 2);
        }
    }

    /** Where the far-too-bright window goes on a feature tower's top floor: {side, along0}. */
    int[] brightWindow(Tower t) {
        int top = t.floors - 1;
        int[] order = {1, 3, 0, 2};
        int start = Rng.pick(Rng.hash(t.h, 0xB417), 4);
        for (int k = 0; k < 4; k++) {
            int side = order[(start + k) % 4];
            boolean used = false;
            for (Opening o : t.openings) if (o.floor == top && o.side == side) used = true;
            if (!used) return new int[]{side, ((side == 0 || side == 2) ? t.l : t.w) / 2 - 1};
        }
        // every side carries a bridge on the top floor: use a quarter point clear of them
        int side = order[start];
        int len = (side == 0 || side == 2) ? t.l : t.w;
        for (int a : new int[]{len / 4, 3 * len / 4 - 3, len / 2 - 1}) {
            boolean clash = false;
            for (Opening o : t.openings) if (o.floor == top && o.side == side && Math.abs(o.lo - a) < 5) clash = true;
            if (!clash) return new int[]{side, a};
        }
        return new int[]{side, 4};
    }

    private static boolean free(Plan p, Band b, int a0, int d0, int a1, int d1) {
        for (int a = a0; a <= a1; a++) {
            for (int d = d0; d <= d1; d++) {
                int i = b.i(a, d), j = b.j(a, d);
                if (p.res(i, j) || p.at(i, j) != FREE) return false;
            }
        }
        return true;
    }

    private static void put(Plan p, Band b, int a, int d, byte c) {
        p.set(b.i(a, d), b.j(a, d), c);
    }

    private static Prop prop(Band b, String model, int a, int d, double dy, float yaw, long seed, boolean barrier, int height) {
        int i = b.i(a, d), j = b.j(a, d);
        int[] bar = null;
        if (barrier) {
            bar = new int[3 * height];
            for (int k = 0; k < height; k++) {
                bar[3 * k] = i;
                bar[3 * k + 1] = k;
                bar[3 * k + 2] = j;
            }
        }
        return new Prop(model, i + 0.5, dy, j + 0.5, yaw, seed, bar);
    }

    // ------------------------------------------------------------------ open-plan bands

    private void openBand(Tower t, Plan p, Band b, long h) {
        boolean ns = b.side == 1 || b.side == 3;
        int a0 = ns ? 3 : b.a0, a1 = ns ? t.w - 4 : b.a1;
        boolean dbl = b.depth >= 9;
        boolean single = !dbl && b.depth >= 6;
        int a = a0, n = 0;
        while (a <= a1) {
            int placed = -1;
            if (dbl || single) {
                for (int k : new int[]{4, 3, 2}) {
                    int width = 4 * k;
                    if (a + width > a1) continue;
                    if (k == 4 && Rng.chance(Rng.hash(h, a, 1), 0.4)) continue;
                    if (!free(p, b, a, 2, a + width, dbl ? 8 : 5)) continue;
                    pod(p, b, a, k, dbl, Rng.hash(h, a, 2));
                    placed = width;
                    break;
                }
            }
            if (placed > 0) {
                a += placed + 3;
                n++;
                continue;
            }
            // no pod fits here: a service nook against the aisle, then move on
            if (free(p, b, a, 2, a, 3) && Rng.chance(Rng.hash(h, a, 3), 0.55)) {
                nook(p, b, a, Rng.hash(h, a, 4));
                a += 3;
            } else {
                a++;
            }
        }
        if (n == 0) p.kind = "open floor";
    }

    /** A pod of k cubicles in one row (single) or two rows back to back (double), 1.5-block partitions. */
    private void pod(Plan p, Band b, int a, int k, boolean dbl, long h) {
        int dEnd = dbl ? 8 : 5;
        for (int m = 0; m <= k; m++) for (int d = 2; d <= dEnd; d++) put(p, b, a + 4 * m, d, PART);
        for (int x = a; x <= a + 4 * k; x++) put(p, b, x, 5, PART);
        for (int m = 0; m < k; m++) {
            int c = a + 4 * m + 2;
            for (int x = c - 1; x <= c + 1; x++) put(p, b, x, 4, DESK_C);
            workstation(p, b, c, 4, 3, b.yawOut(), b.yawIn(), Rng.hash(h, m, 1));
            if (dbl) {
                for (int x = c - 1; x <= c + 1; x++) put(p, b, x, 6, DESK_C);
                workstation(p, b, c, 6, 7, b.yawIn(), b.yawOut(), Rng.hash(h, m, 2));
            }
        }
    }

    /** Monitor on the desk (facing the chair), the chair in front of it, sometimes papers. */
    private void workstation(Plan p, Band b, int a, int deskD, int chairD, float monitorYaw, float chairYaw, long h) {
        double r = Rng.unit(h);
        if (r < 0.035) p.props.add(prop(b, "crt_monitor", a, deskD, 1.0, monitorYaw, h, false, 0));
        else if (r < 0.32) p.props.add(prop(b, "pc_monitor", a, deskD, 1.0, monitorYaw + (float) (Rng.unit(Rng.hash(h, 1)) * 16 - 8), h, false, 0));
        if (Rng.chance(Rng.hash(h, 2), 0.21)) {
            p.props.add(prop(b, "task_chair", a, chairD, 0, chairYaw + (float) (Rng.unit(Rng.hash(h, 3)) * 50 - 25), Rng.hash(h, 4), false, 0));
        }
        if (Rng.chance(Rng.hash(h, 5), 0.3)) p.props.add(prop(b, "paper_stack", a + 1, deskD, 1.0, (float) Rng.pick(Rng.hash(h, 6), 360), h, false, 0));
    }

    private void nook(Plan p, Band b, int a, long h) {
        int kind = Rng.pick(h, 6);
        switch (kind) {
            case 0 -> p.props.add(prop(b, "copier", a, 2, 0, b.yawOut(), h, true, 1));
            case 1 -> p.props.add(prop(b, "water_cooler", a, 2, 0, b.yawOut(), h, true, 2));
            case 2 -> {
                p.props.add(prop(b, "filing_cabinet", a, 2, 0, b.yawOut(), h, true, 2));
                if (free(p, b, a, 3, a, 3)) p.props.add(prop(b, "filing_cabinet", a, 3, 0, b.yawOut(), Rng.hash(h, 1), true, 2));
            }
            case 3 -> put(p, b, a, 2, BARREL_C);
            default -> put(p, b, a, 2, PLANTER_C);
        }
    }

    // ------------------------------------------------------------------ private offices

    /** A row of glass-fronted private offices against the facade (and now and then a conference room). */
    private void execRow(Tower t, Plan p, Band b, long h) {
        boolean ns = b.side == 1 || b.side == 3;
        int start = ns ? 0 : b.a0, end = ns ? t.w - 1 : b.a1;
        int cur = start, idx = 0;
        boolean conf = false;
        while (cur + 6 <= end) {
            long hh = Rng.hash(h, idx++);
            int width = !conf && Rng.chance(hh, 0.25) && cur + 11 <= end ? 11 : Rng.chance(Rng.hash(hh, 1), 0.5) ? 6 : 7;
            int next = cur + width;
            if (end - next < 6) next = end;
            boolean isConf = next - cur >= 10;
            conf |= isConf;
            if (office(t, p, b, cur, next, isConf, hh)) p.kind = isConf ? "offices & conference room" : "private offices";
            cur = next;
        }
    }

    private boolean office(Tower t, Plan p, Band b, int s, int e, boolean conf, long h) {
        for (int a = s; a <= e; a++) {
            for (int d = 0; d <= 5; d++) {
                int i = b.i(a, d), j = b.j(a, d);
                byte c = p.at(i, j);
                if (p.res(i, j) && c != FACADE_C) return false;
                if (c != FREE && c != FACADE_C && c != WALL_C && c != GLASSW) return false;
            }
        }
        for (int d = 0; d <= 5; d++) {
            for (int a : new int[]{s, e}) {
                if (p.at(b.i(a, d), b.j(a, d)) != FACADE_C) put(p, b, a, d, WALL_C);
            }
        }
        for (int a = s + 1; a < e; a++) put(p, b, a, 5, GLASSW);
        int door = (s + e) / 2;
        put(p, b, door, 5, DOOR);
        p.dir[b.j(door, 5) * p.w + b.i(door, 5)] = (byte) b.side;
        for (int a = s + 1; a < e; a++) for (int d = 0; d <= 4; d++) p.fm[b.j(a, d) * p.w + b.i(a, d)] = conf ? FM_CONF : FM_EXEC;
        if (conf) {
            for (int a = s + 2; a <= e - 2; a++) put(p, b, a, 2, TABLE_C);
            int seats = 0;
            for (int a = s + 3; a <= e - 2 && seats < 2; a += 3) {
                p.props.add(prop(b, "task_chair", a, 1, 0, b.yawIn(), Rng.hash(h, a, 1), false, 0));
                p.props.add(prop(b, "task_chair", a + 1, 3, 0, b.yawOut(), Rng.hash(h, a, 2), false, 0));
                seats += 2;
            }
            // whiteboard on the side wall's inner face
            int i = b.i(s, 2), j = b.j(s, 2);
            p.props.add(sideWallProp(b, "whiteboard", s, 2, true, 1.2, h));
            if (p.at(i, j) == FACADE_C) p.props.remove(p.props.size() - 1);
            p.props.add(prop(b, "pc_monitor", (s + e) / 2, 2, 1.0, b.yawIn(), Rng.hash(h, 9), false, 0));
        } else {
            int mid = (s + e) / 2;
            for (int a = mid - 1; a <= mid + 1; a++) put(p, b, a, 2, EXEC_C);
            p.props.add(prop(b, "exec_chair", mid, 1, 0, b.yawIn(), Rng.hash(h, 1), false, 0));
            p.props.add(prop(b, "pc_monitor", mid, 2, 1.0, b.yawOut() + 10f, Rng.hash(h, 2), false, 0));
            if (Rng.chance(Rng.hash(h, 3), 0.5)) p.props.add(prop(b, "desk_lamp", mid + 1, 2, 1.0, b.yawOut(), Rng.hash(h, 4), false, 0));
            for (int d = 1; d <= 3; d++) put(p, b, s + 1, d, SHELF_C);
            put(p, b, e - 1, 0, PLANTER_C);
            if (Rng.chance(Rng.hash(h, 5), 0.6)) p.props.add(prop(b, "filing_cabinet", e - 1, 3, 0, sideYaw(b, false), Rng.hash(h, 6), true, 2));
        }
        return true;
    }

    /** Yaw facing away from the side wall at the low (true) or high end of a band's along axis. */
    private static float sideYaw(Band b, boolean lowEnd) {
        // along axis is X for N/S bands, Z for W/E bands
        boolean alongX = b.side == 1 || b.side == 3;
        if (alongX) return lowEnd ? -90f : 90f;
        return lowEnd ? 0f : 180f;
    }

    private static Prop sideWallProp(Band b, String model, int wallA, int d, boolean lowEnd, double dy, long seed) {
        int i = b.i(wallA, d), j = b.j(wallA, d);
        boolean alongX = b.side == 1 || b.side == 3;
        double x = i + 0.5, z = j + 0.5;
        if (alongX) x = lowEnd ? i + 1.0 : i;
        else z = lowEnd ? j + 1.0 : j;
        return new Prop(model, x, dy, z, sideYaw(b, lowEnd), seed, null);
    }

    // ------------------------------------------------------------------ the lobby

    private void lobby(Tower t, Plan p, Band[] bands, long h) {
        for (Band b : bands) {
            boolean ns = b.side == 1 || b.side == 3;
            int a0 = ns ? 2 : b.a0, a1 = ns ? t.w - 3 : b.a1;
            // planters along the glass
            for (int a = a0; a <= a1; a += 6) {
                if (free(p, b, a, 0, a, 1)) put(p, b, a, 0, PLANTER_C);
            }
            if (b.side == 1 && b.depth >= 8) {
                // reception desk facing the glass, its staff side toward the lifts
                int d = b.depth - 4;
                int lo = t.ci0 + 1, hi = t.ci0 + 8;
                if (free(p, b, lo, d, hi, d + 1)) {
                    for (int a = lo; a <= hi; a++) put(p, b, a, d, RECEPTION_C);
                    put(p, b, lo, d + 1, RECEPTION_C);
                    put(p, b, hi, d + 1, RECEPTION_C);
                    p.props.add(prop(b, "pc_monitor", t.ci0 + 3, d, 1.5, b.yawIn(), Rng.hash(h, 1), false, 0));
                    p.props.add(prop(b, "task_chair", t.ci0 + 3, d + 1, 0, b.yawOut(), Rng.hash(h, 2), false, 0));
                    p.props.add(prop(b, "desk_lamp", t.ci0 + 6, d, 1.5, b.yawOut(), Rng.hash(h, 3), false, 0));
                }
                p.props.add(new Prop("revolving_door", t.w / 2.0, 0, 1.0, 0f, h, null));
            }
            if (!ns && b.depth >= 7) {
                int mid = (b.a0 + b.a1) / 2;
                if (free(p, b, mid - 1, 2, mid + 1, 4)) {
                    p.props.add(prop(b, "lobby_sofa", mid, 2, 0, b.yawIn(), Rng.hash(h, b.side, 1), true, 1));
                    int[] c = ij(b, mid - 1, 2), c2 = ij(b, mid + 1, 2);
                    Prop last = p.props.remove(p.props.size() - 1);
                    p.props.add(new Prop(last.model(), last.i(), 0, last.j(), last.yaw(), last.seed(),
                            new int[]{c[0], 0, c[1], b.i(mid, 2), 0, b.j(mid, 2), c2[0], 0, c2[1]}));
                    put(p, b, mid, 4, PLANTER_C);
                }
            }
            if (b.side == 3 && b.depth >= 6 && p.pit == null) {
                int mid = t.w / 2;
                if (free(p, b, mid - 1, 2, mid + 1, 3)) p.props.add(prop(b, "water_cooler", mid + 4 <= a1 ? mid + 4 : mid, 1, 0, b.yawIn(), h, true, 2));
            }
        }
    }

    // ------------------------------------------------------------------ painting

    @Override
    protected void paintCell(CellCtx c) {
        if (c.floor != 0) return; // towers are painted whole, as columns
        Tower t = tower(c.cx, c.cz);
        List<Bridge> near = bridgesNear(c.cx, c.cz);
        for (int u = c.u0; u <= c.u1; u++) {
            for (int v = c.v0; v <= c.v1; v++) {
                int x = c.ox + u, z = c.oz + v;
                if (t.inside(x, z)) {
                    int i = x - t.x0, j = z - t.z0;
                    int top = BASE + FH * t.floors + 6;
                    for (int y = bottomY + 1; y <= top; y++) {
                        B b = voxel(t, i, j, y);
                        if (b != null) c.set(u, y, v, b);
                    }
                    continue;
                }
                for (Bridge br : near) {
                    if (br.contains(x, z)) paintBridge(c, u, v, x, z, br);
                }
            }
        }
    }

    /**
     * A sky bridge: a fully enclosed glass corridor. Dark steel floor beam and edges, a tiled walkway with a darker
     * trim either side, a knee-high solid base, two courses of glass between steel mullions every four blocks, and a
     * tiled ceiling with light panels. Its trusses, window frames, ceiling beams and floor trims are display models
     * ({@code skybridge}, see the decorations); the blocks give it its walls and its collision.
     */
    private void paintBridge(CellCtx c, int u, int v, int x, int z, Bridge b) {
        int y = BASE + FH * b.floor;
        int cross = (b.ew ? z : x) - b.lo, along = b.ew ? x : z;
        boolean edge = cross == -1 || cross == 3;
        boolean mull = Math.floorMod(along, 4) == 0 || along == b.from || along == b.to;
        boolean lamp = Math.floorMod(along, 4) == 2;
        c.set(u, y - 1, v, FACADE_DARK);
        if (edge) {
            c.set(u, y, v, FACADE_DARK);
            c.set(u, y + 1, v, FACADE_DARK);
            c.set(u, y + 2, v, mull ? COLUMN : GLASS);
            c.set(u, y + 3, v, mull ? COLUMN : GLASS);
            c.set(u, y + 4, v, FACADE_DARK);
            return;
        }
        c.set(u, y, v, cross == 1 ? LOBBY : CARPET);
        for (int k = 1; k <= 3; k++) c.set(u, y + k, v, B.AIR);
        c.set(u, y + 4, v, cross == 1 && lamp ? PANEL : CEIL);
        if (cross == 1 && lamp) c.set(u, y + 3, v, B.light(13));
    }

    private static boolean mullion(Tower t, int i, int j) {
        boolean corner = (i == 0 || i == t.w - 1) && (j == 0 || j == t.l - 1);
        if (corner) return true;
        return (j == 0 || j == t.l - 1) ? i % 4 == 0 : j % 4 == 0;
    }

    /** The block at one position of a tower (tower-local i, j, world y), or null for air. */
    B voxel(Tower t, int i, int j, int y) {
        boolean perim = i == 0 || j == 0 || i == t.w - 1 || j == t.l - 1;
        B facade = t.dark ? FACADE_DARK : FACADE;
        if (y < BASE) {
            if (perim) {
                int rel = Math.floorMod(y - BASE, FH);
                if (rel == 0 || rel == 4) return FACADE_DARK;
                return mullion(t, i, j) ? FACADE_DARK : GLASS_DEAD;
            }
            if (y >= BASE - 4) {
                Plan p0 = plan(t, 0);
                if (p0.pit != null) {
                    B pit = pitVoxel(p0, i, j, y);
                    if (pit != NONE) return pit;
                }
                return SLAB_FILL;
            }
            return null;
        }
        int f = (y - BASE) / FH, r = (y - BASE) % FH;
        if (f > t.floors) return null;
        if (f == t.floors) return roofVoxel(t, i, j, r, perim, facade);
        Plan p = plan(t, f);
        if (i >= t.ci0 && i <= t.ci0 + 9 && j >= t.cj0 && j <= t.cj0 + 9) return coreVoxel(t, f, i - t.ci0, j - t.cj0, r);
        byte code = p.at(i, j);
        if (perim) {
            if (r == 0 || r == 4) return facade;
            if (code == OPENING) return null;
            if (code == BRIGHT) return BRIGHT_GLASS;
            return mullion(t, i, j) ? COLUMN : (t.dark ? GLASS_BLUE : GLASS);
        }
        if (f == 0 && p.pit != null && r == 0) {
            B pit = pitVoxel(p, i, j, y);
            if (pit != NONE) return pit;
        }
        boolean lightSpot = i % 4 == 2 && j % 4 == 2 && airAtHead(code);
        boolean lit = lightSpot && Rng.chance(Rng.hash(seed ^ 0x11C7, t.x0 + i, t.z0 + j, f), p.lit);
        if (p.window != null && r == 3 && code == FREE && p.fm[j * p.w + i] == FM_GRASS) {
            int[] mid = inward(t, p.window[0], p.window[1] + 1, 1);
            if (mid[0] == i && mid[1] == j) return B.light(15);
        }
        if (code == VOID_C && r < 4) return null; // open over the atrium below
        if (code == ATRIUM_C && r == 4) return null; // the ceiling opens into the floor above
        switch (r) {
            case 0 -> {
                return switch (p.fm[j * p.w + i]) {
                    case FM_LOBBY -> LOBBY;
                    case FM_EXEC -> EXEC_FLOOR;
                    case FM_CONF -> CONF_FLOOR;
                    case FM_GRASS -> GRASS;
                    case FM_CONCRETE -> CONCRETE_FLOOR;
                    default -> CARPET;
                };
            }
            case 4 -> {
                if (lightSpot) return lit ? PANEL : PANEL_OFF;
                if (tileMissing(t, p, i, j, f)) return null;
                return CEIL;
            }
            default -> {
                if (r == 3 && lit) return B.light(13);
                return codeBlock(p, code, i, j, r);
            }
        }
    }

    private static final B NONE = B.of(Material.STRUCTURE_VOID);

    /** Feature pit below the lobby floor: three steps down to a landing. NONE = not part of the pit. */
    private static B pitVoxel(Plan p, int i, int j, int y) {
        int i0 = p.pit[0], j0 = p.pit[1];
        if (i < i0 || i > i0 + 2 || j < j0 || j > j0 + 4) return NONE;
        int k = j - j0;
        if (k <= 2) {
            int sy = BASE - k;
            if (y == sy) return STAIR_N;
            if (y > sy && y <= BASE) return null;
            return NONE;
        }
        if (y == BASE - 3) return LOBBY;
        if (y == BASE - 2 && i == i0 + 1 && k == 4) return B.light(9);
        if (y > BASE - 3 && y <= BASE) return null;
        return NONE;
    }

    private static boolean airAtHead(byte code) {
        return code != WALL_C && code != GLASSW && code != DOOR && code != RACK_C && code != MACHINE_C && code != COLUMN_C;
    }

    private B codeBlock(Plan p, byte code, int i, int j, int r) {
        switch (code) {
            case PART -> {
                return r == 1 ? PARTITION : r == 2 ? PART_CAP : null;
            }
            case DESK_C -> {
                return r == 1 ? DESK : null;
            }
            case EXEC_C, TABLE_C -> {
                return r == 1 ? EXEC_DESK : null;
            }
            case WALL_C -> {
                return WALL;
            }
            case GLASSW -> {
                return r == 1 ? WALL : GLASS;
            }
            case DOOR -> {
                if (r == 3) return WALL;
                String facing = DOOR_FACING[p.dir[j * p.w + i]];
                return B.of(Material.DARK_OAK_DOOR, "[facing=" + facing + ",half=" + (r == 1 ? "lower" : "upper") + ",hinge=left,open=false,powered=false]");
            }
            case SHELF_C -> {
                return r <= 2 ? SHELF : null;
            }
            case PLANTER_C -> {
                return r == 1 ? PLANTER : r == 2 ? (((i + j) & 1) == 0 ? PLANT : PLANT_B) : null;
            }
            case COUNTER_C -> {
                return r == 1 ? COUNTER : null;
            }
            case RECEPTION_C -> {
                return r == 1 ? RECEPTION : r == 2 ? RECEPTION_TOP : null;
            }
            case BARREL_C -> {
                return r == 1 ? BARREL : null;
            }
            case RACK_C -> {
                return r == 1 || r == 3 ? RACK : r == 2 ? RACK_SHELF : null;
            }
            case MACHINE_C -> {
                return r == 1 ? MACHINE : r == 2 ? MACHINE_TOP : null;
            }
            case COLUMN_C -> {
                return COLUMN;
            }
            default -> {
                return null;
            }
        }
    }

    /**
     * The core (core-local ci, cj in 0..9): a switchback stairwell (ci 0..5) and the lift shaft (ci 5..9, cj 0..4),
     * with a solid service block behind the lift. Stairs: landing at cj 1..2, flight A rising south along ci 1..2,
     * mid landing at cj 6..7, flight B rising north along ci 3..4 to the next floor's landing.
     */
    private B coreVoxel(Tower t, int f, int ci, int cj, int r) {
        boolean roof = f == t.floors;
        if (ci <= 5) {
            boolean wall = ci == 0 || ci == 5 || cj == 0 || cj == 9;
            if (ci == 5 && cj <= 4) return roof ? FACADE_DARK : STEEL; // shared with the lift shaft
            if (wall) {
                if (cj == 0 && (ci == 1 || ci == 2) && (r == 1 || r == 2)) return null; // stair door
                if (roof && cj == 0 && (ci == 1 || ci == 2) && r == 3) return PANEL; // light over the roof door
                return roof ? FACADE_DARK : WALL;
            }
            int s = ci - 1, z = cj - 1;
            if (r == 0) {
                if (f == 0) return LANDING;
                if (z <= 1 || (z == 2 && s >= 2)) return LANDING;
                if (s >= 2 && z == 3) return STAIR_N;
                return null;
            }
            if (roof) return r == 4 ? FACADE_DARK : null;
            if (r == 4) {
                if (s >= 2 && z == 4) return STAIR_N;
                if (s == 1 && z == 6) return B.light(11);
                return null;
            }
            if (s <= 1 && z == 1 + r) return STAIR_S;
            if (r == 3 && (z == 5 || z == 6)) return LANDING;
            if (r == 3 && s == 3 && z == 0) return B.light(13);
            return null;
        }
        if (cj <= 4) {
            if (roof) return FACADE_DARK;
            boolean wall = ci == 9 || cj == 0 || cj == 4;
            if (wall) {
                if (cj == 0 && ci == 7 && (r == 1 || r == 2)) return null; // lift door
                return r == 0 || r == 4 ? WALL : STEEL;
            }
            if (r == 0) return LOBBY;
            if (r == 4) return ci == 7 && cj == 2 ? PANEL : CEIL;
            if (r == 3 && ci == 7 && cj == 2) return B.light(12);
            return null;
        }
        return roof ? FACADE_DARK : WALL;
    }

    private B roofVoxel(Tower t, int i, int j, int r, boolean perim, B facade) {
        if (i >= t.ci0 && i <= t.ci0 + 9 && j >= t.cj0 && j <= t.cj0 + 9) {
            if (r == 4 && (i - t.ci0 >= 6 || j - t.cj0 >= 5)) return FACADE_DARK;
            return coreVoxel(t, t.floors, i - t.ci0, j - t.cj0, r);
        }
        if (r == 0) return ROOF;
        if (perim) {
            if (r == 1) return facade;
            if (r == 2) {
                boolean w = i == 0, e = i == t.w - 1, n = j == 0, s = j == t.l - 1;
                if (n && w) return BARS_NW;
                if (n && e) return BARS_NE;
                if (s && w) return BARS_SW;
                if (s && e) return BARS_SE;
                return n || s ? BARS_EW : BARS_NS;
            }
            return null;
        }
        // rooftop plant: two boxed units, and a dim lamp over the stair door
        if (r <= 2) {
            if (i >= 3 && i <= 5 && j >= 3 && j <= 4) return r == 2 ? COLUMN : FACADE_DARK;
            if (Rng.chance(Rng.hash(t.h, 0xAC), 0.6) && i >= t.w - 7 && i <= t.w - 5 && j >= t.l - 6 && j <= t.l - 4) return r == 2 ? COLUMN : FACADE_DARK;
        }
        if (r == 3 && i == t.ci0 + 1 && j == t.cj0 - 1) return B.light(10);
        return null;
    }

    // ------------------------------------------------------------------ features

    @Override
    protected int[] triggerBox(Feature f) {
        Tower t = tower(f.cx(), f.cz());
        int ou = t.x0 - f.cx() * CELL, ov = t.z0 - f.cz() * CELL;
        if (f.id().equals("bright_window")) {
            int[] win = brightWindow(t);
            int[] a = inward(t, win[0], win[1], 1), b = inward(t, win[0], win[1] + 2, 1);
            int dy = FH * (t.floors - 1);
            return new int[]{ou + Math.min(a[0], b[0]), dy, ov + Math.min(a[1], b[1]), ou + Math.max(a[0], b[0]), dy + 1, ov + Math.max(a[1], b[1])};
        }
        if (f.id().equals("basement_stairs")) {
            int i0 = t.ci0 + 3, j0 = t.rj1() + 2;
            return new int[]{ou + i0, -3, ov + j0 + 3, ou + i0 + 2, -1, ov + j0 + 4};
        }
        return null;
    }

    @Override
    public int[] safeLocal(int floor, int cx, int cz) {
        Tower t = tower(cx, cz);
        return new int[]{t.x0 - cx * CELL + t.ci0 + 7, t.z0 - cz * CELL + t.cj0 - 2};
    }

    @Override
    public boolean isSafeCell(int floor, int cx, int cz) {
        return floor < tower(cx, cz).floors;
    }

    @Override
    public String zoneName(int floor, int cx, int cz) {
        Tower t = tower(cx, cz);
        if (floor >= t.floors) return "tower " + t.floors + "F (no floor " + floor + ")";
        return "tower " + t.floors + "F, floor " + floor + ": " + plan(t, floor).kind;
    }

    // ------------------------------------------------------------------ decorations

    @Override
    protected void cellDecor(int floor, int cx, int cz, List<DecorSpec> out) {
        Tower t = tower(cx, cz);
        if (floor >= t.floors) return;
        Plan p = plan(t, floor);
        int feet = BASE + FH * floor + 1;
        long h = Rng.hash(t.h, 0xDEC0 + floor);
        for (Prop pr : p.props) {
            if (pr.model().equals("daylight_window")) {
                int[] win = p.window;
                int[] a = facadeCell(t, win[0], win[1] + 1);
                double x = t.x0 + a[0] + 0.5, z = t.z0 + a[1] + 0.5;
                float yaw;
                switch (win[0]) {
                    case 0 -> { x = t.x0 + 1.03; yaw = -90f; }
                    case 1 -> { z = t.z0 + 1.03; yaw = 0f; }
                    case 2 -> { x = t.x0 + t.w - 1.03; yaw = 90f; }
                    default -> { z = t.z0 + t.l - 1.03; yaw = 180f; }
                }
                out.add(new DecorSpec("daylight_window", x, feet, z, yaw, 0, 0, pr.seed()));
                continue;
            }
            DecorSpec d = new DecorSpec(pr.model(), t.x0 + pr.i(), feet + pr.dy(), t.z0 + pr.j(), pr.yaw(), 0, 0, pr.seed());
            // desk clutter is an environmental extra: thinned by the detail density settings like any other
            if (pr.model().equals("paper_stack") || pr.model().equals("desk_lamp")) d.tier(DecorSpec.TIER_ENV, Rng.unit(Rng.hash(pr.seed(), 0x7A11)));
            if (pr.barriers() != null) {
                for (int k = 0; k + 2 < pr.barriers().length; k += 3) d.barrier(t.x0 + pr.barriers()[k], feet + pr.barriers()[k + 1], t.z0 + pr.barriers()[k + 2]);
            }
            out.add(d);
        }
        // the lift: a button panel inside the car, the floor indicator over the door
        int carX = t.x0 + t.ci0 + 6, carZ = t.z0 + t.cj0 + 1;
        out.add(new DecorSpec("lift_panel:" + t.floors + ":" + floor, carX + 2.97, feet + 0.85, carZ + 0.9, 90f, 0, 0, h));
        out.add(new DecorSpec("lift_sign:" + floor, carX + 1.5, feet + 2.45, carZ - 1.01, 180f, 0, 0, h));
        // stairwell: a floor sign beside the door, and a lamp on each landing
        out.add(new DecorSpec("stair_sign:" + floor, t.x0 + t.ci0 + 3.6, feet + 1.3, t.z0 + t.cj0 - 0.01, 180f, 0, 0, h));
        out.add(new DecorSpec("tower_lamp", t.x0 + t.ci0 + 4.5, feet + 2.2, t.z0 + t.cj0 + 1.0, 0f, 0, 0, Rng.hash(h, 1)));
        if (floor == 0) {
            out.add(new DecorSpec("directory_board", t.x0 + t.ci0 - 0.01, feet + 1.1, t.z0 + t.cj0 + 4.5, 90f, 0, 0, h));
            if (p.pit != null) {
                out.add(new DecorSpec("basement_sign", t.x0 + p.pit[0] + 1.5, feet + 1.7, t.z0 + p.pit[1] - 0.6, 0f, 0, 0, h));
            }
        }
        // fluorescent hum from a sparse subset of the lit ceiling panels (the panels themselves are blocks)
        for (int i = 2; i < t.w - 1; i += 8) {
            for (int j = 2; j < t.l - 1; j += 8) {
                if (i >= t.ci0 && i <= t.ci0 + 9 && j >= t.cj0 && j <= t.cj0 + 9) continue;
                if (!airAtHead(p.at(i, j)) || p.at(i, j) == FACADE_C) continue;
                if (!Rng.chance(Rng.hash(seed ^ 0x11C7, t.x0 + i, t.z0 + j, floor), p.lit)) continue;
                out.add(new DecorSpec("panel_light", t.x0 + i + 0.5, feet + 3, t.z0 + j + 0.5, 0, 0, 0, Rng.hash(h, i, j)));
            }
        }
        // the sky bridges leaving this tower to the east and south (each bridge is listed by its west/north tower only)
        for (Bridge b : t.own) {
            if (b.floor != floor) continue;
            boolean mine = b.ew ? b.from == t.x0 + t.w : b.from == t.z0 + t.l;
            if (!mine) continue;
            int y = BASE + FH * b.floor;
            double mid = b.lo + 1.5;
            for (int a = b.from; a <= b.to; a += 8) {
                int len = Math.min(8, b.to + 1 - a);
                if (len <= 0) break;
                out.add(b.ew ? new DecorSpec("skybridge:" + len, a, y, mid, -90f, 0, 0, Rng.hash(h, a)).structural()
                        : new DecorSpec("skybridge:" + len, mid, y, a, 0f, 0, 0, Rng.hash(h, a)).structural());
            }
            // door frames where it meets each facade
            if (b.ew) {
                out.add(new DecorSpec("skybridge_door", b.from + 0.02, y + 1, mid, -90f, 0, 0, h).structural());
                out.add(new DecorSpec("skybridge_door", b.to + 0.98, y + 1, mid, 90f, 0, 0, h).structural());
            } else {
                out.add(new DecorSpec("skybridge_door", mid, y + 1, b.from + 0.02, 0f, 0, 0, h).structural());
                out.add(new DecorSpec("skybridge_door", mid, y + 1, b.to + 0.98, 180f, 0, 0, h).structural());
            }
        }
        if (floor == t.floors - 1) {
            int roof = BASE + FH * t.floors + 1;
            if (t.floors >= 6) out.add(new DecorSpec("roof_beacon", t.x0 + t.ci0 + 7.5, roof + 4, t.z0 + t.cj0 + 2.5, 0, 0, 0, h));
        }
    }

    /** A ceiling tile fallen out on an abandoned floor (nothing hangs from it). */
    private boolean tileMissing(Tower t, Plan p, int i, int j, int f) {
        if (p.at(i, j) == ATRIUM_C) return true;   // open to the floor above
        return p.missingCeiling > 0 && Rng.chance(Rng.hash(seed ^ 0xCE1L, t.x0 + i, t.z0 + j, f), p.missingCeiling);
    }

    @Override
    protected void cellDetails(int floor, int cx, int cz, List<DecorSpec> out) {
        Tower t = tower(cx, cz);
        if (floor >= t.floors) return;
        Plan p = plan(t, floor);
        int ceil = BASE + FH * floor + 4;
        long h = Rng.hash(t.h, 0xD7A4 + floor);
        for (int i = 4; i < t.w - 2; i += 8) {
            for (int j = 4; j < t.l - 2; j += 8) {
                if (p.at(i, j) == CORE || !airAtHead(p.at(i, j)) || p.at(i, j) == FACADE_C) continue;
                long hh = Rng.hash(h, i, j);
                if (!Rng.chance(hh, 0.2) || tileMissing(t, p, i, j, floor)) continue;
                out.add(detail(DecorSpec.TIER_ARCH, hh, "d:vent:0.9", t.x0 + i + 0.05, ceil, t.z0 + j + 0.05, 0f, 0f));
            }
        }
        // damp stains on the ceiling tiles in worn patches
        double wr = wear(cx, cz, 0x3EA4L + floor);
        for (int i = 4; i < t.w - 3; i += 7) {
            for (int j = 4; j < t.l - 3; j += 7) {
                if (p.at(i, j) == CORE || !airAtHead(p.at(i, j)) || p.at(i, j) == FACADE_C || i % 4 == 2 && j % 4 == 2) continue;
                if (p.at(i + 1, j) == CORE || p.at(i, j + 1) == CORE) continue;
                long hh = Rng.hash(h, i, j, 7);
                if (!Rng.chance(hh, 0.01 + 0.12 * wr) || tileMissing(t, p, i, j, floor) || tileMissing(t, p, i + 1, j, floor)
                        || tileMissing(t, p, i, j + 1, floor) || tileMissing(t, p, i + 1, j + 1, floor)) continue;
                out.add(detail(DecorSpec.TIER_AGING, hh, "d:cpatch:terracotta:1.1:0.9", t.x0 + i + 0.1, ceil, t.z0 + j + 0.1, 0f, 0f));
            }
        }
    }
}
