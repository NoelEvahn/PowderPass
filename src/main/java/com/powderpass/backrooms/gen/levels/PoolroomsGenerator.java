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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Poolrooms: three stacked storeys of white and pale-blue tile (a lower, a main and an upper floor, 32 blocks
 * apart; everyone arrives on the main floor) tied together by tiled stair towers. Every 60 x 60 region of every
 * storey is one of five kinds of space:
 * <ul>
 *   <li><b>Bays</b>: the classic continuous hall of 12-block bays between 2 x 2 pillars, with dry walkways, wading
 *   pools and deep lakes following smooth noise.</li>
 *   <li><b>Dry tile</b>: the same bays without water: tile walks, raised platforms and more free-standing screens.</li>
 *   <li><b>Bright rooms</b>: no walls or pillars at all, a wide tiled floor under a very high ceiling covered in light
 *   panels, often with a reflecting pool and a giant half-sunken Greek head.</li>
 *   <li><b>Long tall hallways</b>: a 22-block-high corridor through the whole region whose side walls are pierced by
 *   2 x 2 holes leading into small places (alcoves, pools, steps down into sunken pools, busts, glowing niches).</li>
 *   <li><b>Massive halls</b> (main + upper storey): a 56-block-high hall with a gallery at the upper floor, a grand
 *   staircase up to it, a giant Greek head in its own pool and a working water slide from the gallery down into
 *   the big pool ({@link #slideStartingAt}, ridden by {@code world.PoolSlides}).</li>
 * </ul>
 * All layout is a pure function of seed and coordinates ({@link #column}), so painting, the safe-spot search, the
 * slide rides and the tests all agree. Lights are emissive {@code light_panel_cool} blocks over invisible lights
 * plus invisible lights hanging in the air of tall spaces; nothing vanilla emits light.
 */
public final class PoolroomsGenerator extends LevelGenerator {
    public static final int LOW = 0, MID = 1, HIGH = 2;
    private static final int[] FLOORS = {32, 64, 96};
    private static final int BOTTOM = 16, ROOF = 128, H = ROOF - BOTTOM + 1;
    private static final int REG = 60;
    /** Ceiling of a massive hall (its floor is the main floor). */
    public static final int HALL_CEIL = 120;
    static final int K_DRY = 0, K_SHALLOW = 1, K_DEEP = 2, K_RAISED = 3, K_TERRACE = 4;
    private static final String[] KINDS = {"dry", "shallow", "deep", "raised", "terrace-pool"};
    private static final String[] FLOOR_NAMES = {"lower", "main", "upper"};

    public enum Zone { BAYS, DRY, BRIGHT, HALLWAY, MASSIVE }

    private static final B TILE = CustomBlocks.get("pool_tile");
    private static final B POOL = CustomBlocks.get("pool_tile_blue");
    private static final B GRIME = CustomBlocks.get("pool_tile_grime");
    private static final B PANEL = CustomBlocks.get("light_panel_cool");
    private static final B UNDER = B.of(Material.WHITE_CONCRETE);
    private static final B STEP = B.of(Material.QUARTZ_STAIRS, "[facing=west,half=bottom,waterlogged=true]");
    private static final B UNDERWATER_LIGHT = B.of(Material.LIGHT, "[level=12,waterlogged=true]");
    private static final B BED = B.of(Material.SMOOTH_QUARTZ);
    private static final B LIP = B.slab(Material.SMOOTH_QUARTZ_SLAB, false);
    private static final B GRAY = B.of(Material.GRAY_CONCRETE);
    private static final B DRAIN = B.of(Material.BLACK_CONCRETE);
    private static final B SKIN = B.of(Material.SMOOTH_QUARTZ);
    private static final B HAIR = B.of(Material.CALCITE);

    private final Map<Long, Plan> plans = new ConcurrentHashMap<>();

    public PoolroomsGenerator(long seed, GenSettings gs) {
        super(Level.POOLROOMS, seed, gs, 12, 5, 3, FLOORS.clone(), BOTTOM, ROOF, 0.0, 0.0);
    }

    @Override
    protected List<FeatureDef> featureDefs() {
        return List.of(
                // onward to the Habitable Zone in every district; the drain back to Level 0 is rarer. Both on the main floor.
                FeatureDef.transition("service_door", Dest.to(Level.LEVEL_1), 1.0).onFloor(MID),
                FeatureDef.transition("drain", Dest.to(Level.LEVEL_0), gs.get("back-chance", 0.4)).onFloor(MID),
                FeatureDef.landmark("lifeguard_post", gs.get("landmark-chance", 0.5)).onFloor(MID));
    }

    @Override
    public String biomeKey() {
        return "minecraft:warm_ocean";
    }

    private static int y(int y) {
        return y - BOTTOM;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    // ================================================================== region plans

    /** Everything decided once per 60 x 60 region (all three storeys). Immutable. */
    public final class Plan {
        public final int rx, rz;
        public final Zone[] zone = new Zone[3];
        final boolean[] alongX = new boolean[3];
        final boolean[] brightPool = new boolean[3];
        final boolean[] statue = new boolean[3];
        final int[] face = new int[3];
        /** Cell of the stair tower (always a corner cell of the region). */
        public final int towerCx, towerCz;
        final Hall hall;

        Plan(int rx, int rz) {
            this.rx = rx;
            this.rz = rz;
            boolean feat = false;
            for (Feature f : districtFeatures(Math.floorDiv(rx, D), Math.floorDiv(rz, D))) {
                if (Math.floorDiv(f.cx(), R) == rx && Math.floorDiv(f.cz(), R) == rz) feat = true;
            }
            long h = Rng.hash(seed ^ 0x9E61L, rx, rz);
            boolean massive = !feat && Rng.chance(Rng.hash(h, 1), gs.get("massive-chance", 0.1));
            for (int f = 0; f < 3; f++) {
                if (massive && f >= MID) {
                    zone[f] = Zone.MASSIVE;
                } else {
                    // long tall hallways run through three regions in a row (along X) or a column (along Z)
                    double hp = gs.get("hallway-chance", 0.06);
                    boolean hx = Rng.chance(Rng.hash(seed ^ 0x4A11L, Math.floorDiv(rx, 3), rz, f), hp);
                    boolean hz = Rng.chance(Rng.hash(seed ^ 0x4A12L, rx, Math.floorDiv(rz, 3), f), hp);
                    double r = Rng.unit(Rng.hash(h, 10 + f));
                    boolean free = !(feat && f == MID);
                    if (free && (hx || hz)) zone[f] = Zone.HALLWAY;
                    else if (free && r < gs.get("bright-room-chance", 0.12)) zone[f] = Zone.BRIGHT;
                    else zone[f] = Rng.chance(Rng.hash(h, 20 + f), new double[]{0.2, 0.3, 0.42}[f]) ? Zone.DRY : Zone.BAYS;
                    alongX[f] = hx;
                    rollExtras(f, h);
                    continue;
                }
                rollExtras(f, h);
            }
            if (massive) {
                hall = new Hall(rx, rz, Rng.pick(Rng.hash(h, 2), 4));
                int[] w = l2w(6, 54, hall.rot);
                towerCx = rx * R + w[0] / S;
                towerCz = rz * R + w[1] / S;
            } else {
                hall = null;
                int corner = Rng.pick(Rng.hash(h, 3), 4);
                towerCx = rx * R + (corner & 1) * (R - 1);
                towerCz = rz * R + (corner >> 1) * (R - 1);
            }
        }

        private void rollExtras(int f, long h) {
            brightPool[f] = Rng.chance(Rng.hash(h, 40 + f), 0.7);
            statue[f] = brightPool[f] && Rng.chance(Rng.hash(h, 50 + f), 0.55);
            face[f] = Rng.pick(Rng.hash(h, 60 + f), 4);
        }

        /** Top storey the stair tower serves. */
        int towerTop() {
            return hall != null ? MID : HIGH;
        }
    }

    public Plan plan(int rx, int rz) {
        long k = key(rx, rz);
        Plan p = plans.get(k);
        if (p != null) return p;
        if (plans.size() > 4096) plans.clear();
        p = new Plan(rx, rz);
        plans.put(k, p);
        return p;
    }

    public Plan planAt(int x, int z) {
        return plan(Math.floorDiv(x, REG), Math.floorDiv(z, REG));
    }

    public Zone zoneAt(int floor, int cx, int cz) {
        return plan(Math.floorDiv(cx, R), Math.floorDiv(cz, R)).zone[floor];
    }

    /** World direction {dx, dz} a bright room's head faces. */
    public int[] brightStatueFacing(int rx, int rz, int floor) {
        return rotDir(0, 1, plan(rx, rz).face[floor]);
    }

    public boolean brightStatue(int rx, int rz, int floor) {
        return plan(rx, rz).statue[floor];
    }

    public boolean hallwayAlongX(int rx, int rz, int floor) {
        return plan(rx, rz).alongX[floor];
    }

    /** World (x,z) of the centre of block (a,b) in a massive hall's own layout coordinates (for previews and tests). */
    public double[] hallPoint(int rx, int rz, double a, double b) {
        Plan p = plan(rx, rz);
        int[] w = l2w((int) Math.floor(a), (int) Math.floor(b), p.hall == null ? 0 : p.hall.rot);
        return new double[]{rx * REG + w[0] + 0.5, rz * REG + w[1] + 0.5};
    }

    /** Region-local (a,b) of a rotated layout to world-local (x,z). */
    static int[] l2w(int a, int b, int rot) {
        return switch (rot) {
            case 0 -> new int[]{a, b};
            case 1 -> new int[]{REG - 1 - b, a};
            case 2 -> new int[]{REG - 1 - a, REG - 1 - b};
            default -> new int[]{b, REG - 1 - a};
        };
    }

    static int[] w2l(int lx, int lz, int rot) {
        return switch (rot) {
            case 0 -> new int[]{lx, lz};
            case 1 -> new int[]{lz, REG - 1 - lx};
            case 2 -> new int[]{REG - 1 - lx, REG - 1 - lz};
            default -> new int[]{REG - 1 - lz, lx};
        };
    }

    static int[] rotDir(int da, int db, int rot) {
        return switch (rot) {
            case 0 -> new int[]{da, db};
            case 1 -> new int[]{-db, da};
            case 2 -> new int[]{-da, -db};
            default -> new int[]{db, -da};
        };
    }

    static String facing(int dx, int dz) {
        if (dx > 0) return "east";
        if (dx < 0) return "west";
        return dz > 0 ? "south" : "north";
    }

    // ================================================================== massive halls and slides

    /**
     * A water slide: the deterministic path a rider follows (feet positions, one per chute block, then two exit
     * points over the landing pool) and the two chute blocks whose feet space starts the ride.
     */
    public record Slide(double[][] points, int[][] start, double[] exitDir, int landingWaterY) {
        public double length() {
            double l = 0;
            for (int i = 1; i < points.length; i++) {
                l += Math.sqrt(Math.pow(points[i][0] - points[i - 1][0], 2) + Math.pow(points[i][1] - points[i - 1][1], 2)
                        + Math.pow(points[i][2] - points[i - 1][2], 2));
            }
            return l;
        }
    }

    /**
     * A massive hall, laid out in region-local (a,b) coordinates and rotated as a whole: a gallery ring at the upper
     * floor, a grand staircase up to it along one side, a slide from the gallery into a big pool, and a giant head
     * in a second pool. Pools: P1 a 22-42 b 36-55 (5 deep), P2 a 35-55 b 8-31 (6 deep).
     */
    final class Hall {
        final int rot, x0, z0;
        static final int N = 60, BED0 = 96;
        final int[] px = new int[N], pz = new int[N], py = new int[N];
        final B[] bed = new B[N];
        final Map<Long, Integer> path = new HashMap<>();
        final Map<Long, Integer> rim = new HashMap<>();
        final Slide slide;
        final int statueX, statueZ, statueY;
        final int[] statueFace;

        Hall(int rx, int rz, int rot) {
            this.rot = rot;
            this.x0 = rx * REG;
            this.z0 = rz * REG;
            for (int i = 0; i < N; i++) {
                int a = i < 30 ? 3 + i : 32, b = i < 30 ? 14 : 15 + (i - 30);
                int[] w = l2w(a, b, rot);
                px[i] = x0 + w[0];
                pz[i] = z0 + w[1];
                py[i] = BED0 - i / 2;
                path.put(key(px[i], pz[i]), i);
            }
            for (int i = 0; i < N; i++) {
                int dx = i < N - 1 ? px[i + 1] - px[i] : px[i] - px[i - 1];
                int dz = i < N - 1 ? pz[i + 1] - pz[i] : pz[i] - pz[i - 1];
                boolean full = i % 2 == 0 || i == 29;
                bed[i] = full ? BED : B.stairs(Material.SMOOTH_QUARTZ_STAIRS, facing(-dx, -dz), false);
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = px[i] + d[0], nz = pz[i] + d[1];
                    if (path.containsKey(key(nx, nz))) continue;
                    if (i == 0 && d[0] == -dx && d[1] == -dz) continue; // the way in from the gallery
                    rim.merge(key(nx, nz), py[i], Math::max);
                }
            }
            double[][] pts = new double[N + 2][];
            for (int i = 0; i < N; i++) pts[i] = new double[]{px[i] + 0.5, feet(i), pz[i] + 0.5};
            int ex = px[N - 1] - px[N - 2], ez = pz[N - 1] - pz[N - 2];
            pts[N] = new double[]{px[N - 1] + 0.5 + ex * 1.5, feet(N - 1) - 0.6, pz[N - 1] + 0.5 + ez * 1.5};
            pts[N + 1] = new double[]{px[N - 1] + 0.5 + ex * 3.0, feet(N - 1) - 1.8, pz[N - 1] + 0.5 + ez * 3.0};
            slide = new Slide(pts, new int[][]{{px[0], BED0 + 1, pz[0]}, {px[1], BED0 + 1, pz[1]}}, new double[]{ex, 0, ez}, FLOORS[MID]);
            int[] s = l2w(46, 20, rot);
            statueX = x0 + s[0];
            statueZ = z0 + s[1];
            statueY = FLOORS[MID] - 4;
            statueFace = rotDir(-1, 0, rot);
        }

        /** Rider feet height over chute block i: always above the stepped bed, never touching it. */
        double feet(int i) {
            return BED0 + 1 - (i + 0.5) / 2.0 + 1.0;
        }
    }

    /** The slide whose start the feet block (x,y,z) is in, or null. */
    public Slide slideStartingAt(int x, int y, int z) {
        Plan p = planAt(x, z);
        if (p.hall == null) return null;
        for (int[] s : p.hall.slide.start()) {
            if (s[0] == x && s[2] == z && y >= s[1] && y <= s[1] + 1) return p.hall.slide;
        }
        return null;
    }

    /** The slide of a region, or null (one per massive hall). */
    public Slide slide(int rx, int rz) {
        Plan p = plan(rx, rz);
        return p.hall == null ? null : p.hall.slide;
    }

    // ================================================================== cells (bays)

    /** Per-cell decisions for one storey. */
    final class Cell {
        final Zone zone;
        final int kind, depth, ceil;
        final int[] sn, sw;
        final boolean pillars, tower, band;
        final Feature feat;

        Cell(int f, int cx, int cz, Plan p) {
            zone = p.zone[f];
            feat = f == MID ? featureAt(MID, cx, cz) : null;
            tower = cx == p.towerCx && cz == p.towerCz;
            int across = p.alongX[f] ? Math.floorMod(cz, R) : Math.floorMod(cx, R);
            band = zone == Zone.HALLWAY && across >= 1 && across <= 3;
            pillars = zone == Zone.BAYS || zone == Zone.DRY || zone == Zone.HALLWAY;
            int fy = FLOORS[f];
            double w = wetness(f, cx, cz) + (gs.get("water-ratio", 0.55) - 0.55) + new double[]{0.06, 0.0, -0.05}[f];
            double e = zone(cx, cz, 0xE1E7L + f * 17L, 60);
            int k;
            if (feat != null) k = feat.id().equals("drain") ? K_DEEP : K_DRY;
            else if (tower || band || zone == Zone.BRIGHT || zone == Zone.MASSIVE) k = K_DRY;
            else if (zone == Zone.DRY) k = e > 0.62 ? K_RAISED : K_DRY;
            else if (w > 0.57) k = K_DEEP;
            else if (w > 0.45) k = K_SHALLOW;
            else if (e > 0.7) k = Rng.chance(ch(f, cx, cz, 0x7E44), 0.4) ? K_TERRACE : K_RAISED;
            else k = K_DRY;
            kind = k;
            depth = feat != null && feat.id().equals("drain") ? 8 : 3 + Math.max(0, Math.min(5, (int) ((w - 0.57) * 30)));
            double n = zone(cx, cz, 0xCE11L + f * 131L, 90);
            int c;
            if (zone == Zone.DRY) c = n > 0.6 ? 9 : 6;
            else c = n > 0.74 ? 16 : n > 0.55 ? 10 : 7;
            if (k >= K_RAISED) c = Math.max(c, 10);
            ceil = fy + c;
            boolean screens = pillars && !tower && !band && feat == null;
            sn = screens ? screen(f, cx, cz, 0, zone == Zone.DRY) : null;
            sw = screens ? screen(f, cx, cz, 1, zone == Zone.DRY) : null;
        }

        boolean wet() {
            return kind == K_SHALLOW || kind == K_DEEP;
        }
    }

    private double wetness(int f, int cx, int cz) {
        return zone(cx, cz, 0x3A7E4L + f * 7919L, 70) * 0.8 + Rng.unit(ch(f, cx, cz, 0x7E7)) * 0.2;
    }

    /** Free-standing screen on the north (side 0) or west (side 1) edge: {openingStart, openingWidth, height} or null. */
    private int[] screen(int f, int cx, int cz, int side, boolean dry) {
        long h = ch(f, cx, cz, 0x5C4 + side);
        if (!Rng.chance(h, gs.get("screen-chance", 0.2) * (dry ? 1.9 : 1.0))) return null;
        int w = 4 + Rng.pick(Rng.hash(h, 1), 3);
        int start = 2 + Rng.pick(Rng.hash(h, 2), S - 2 - w);
        int height = Rng.chance(Rng.hash(h, 3), 0.5) ? 3 : 99;
        return new int[]{start, w, height};
    }

    Cell cell(int f, int cx, int cz) {
        return new Cell(f, cx, cz, plan(Math.floorDiv(cx, R), Math.floorDiv(cz, R)));
    }

    public int kind(int floor, int cx, int cz) {
        return cell(floor, cx, cz).kind;
    }

    // ================================================================== the column painter (pure)

    /** The whole column at (x,z), bottom to roof: index y - bottomY. */
    public B[] column(int x, int z) {
        int cx = cellOf(x), cz = cellOf(z);
        Cell[] cells = {cell(LOW, cx, cz), cell(MID, cx, cz), cell(HIGH, cx, cz)};
        B[] col = new B[H];
        column(x, z, col, planAt(x, z), cells);
        return col;
    }

    private void column(int x, int z, B[] col, Plan p, Cell[] cells) {
        Arrays.fill(col, TILE);
        for (int yy = BOTTOM + 1; yy < FLOORS[LOW] - 9; yy++) col[y(yy)] = UNDER;
        col[0] = B.BEDROCK;
        col[H - 1] = B.BEDROCK;
        int lx = x - p.rx * REG, lz = z - p.rz * REG;
        int u = localOf(x), v = localOf(z);
        for (int f = 0; f < 3; f++) {
            switch (p.zone[f]) {
                case MASSIVE -> {
                    if (f == MID) massive(p.hall, lx, lz, x, z, col);
                }
                case BRIGHT -> bright(f, p, lx, lz, x, z, col);
                default -> {
                    bay(f, cells[f], u, v, x, z, col);
                    if (p.zone[f] == Zone.HALLWAY) hallway(f, p, lx, lz, x, z, col);
                }
            }
        }
        if (cellOf(x) == p.towerCx && cellOf(z) == p.towerCz && u >= 2 && v >= 2) tower(p, u, v, col);
    }

    private static void set(B[] col, int yy, B b) {
        if (yy > BOTTOM && yy < ROOF) col[y(yy)] = b;
    }

    private static B get(B[] col, int yy) {
        return col[y(yy)];
    }

    private static void light(B[] col, int yy, int level) {
        if (get(col, yy) == B.AIR) set(col, yy, B.light(level));
    }

    // ---------------------------------------------------------------- bays

    private void bay(int f, Cell c, int u, int v, int x, int z, B[] col) {
        int fy = FLOORS[f], ceil = c.ceil, k = c.kind, d = c.depth;
        int top = f == HIGH ? ROOF - 1 : FLOORS[f + 1] - 9;
        if (c.pillars && u <= 1 && v <= 1) {
            for (int yy = fy; yy < ceil; yy++) set(col, yy, (yy == fy + 1 || yy == fy + 4) ? POOL : TILE);
            return;
        }
        boolean grid = Math.floorMod(x, 4) == 0 || Math.floorMod(z, 4) == 0;
        B tile = TILE, lining = grid ? TILE : POOL;
        switch (k) {
            case K_SHALLOW -> {
                set(col, fy - 1, lining);
                set(col, fy, B.WATER);
            }
            case K_DEEP -> {
                set(col, fy - d, lining);
                for (int yy = fy - d + 1; yy <= fy; yy++) set(col, yy, B.WATER);
                // submerged steps along the west side, so you can walk out instead of climbing
                if (u <= 3 && v >= 4 && v <= 8 && d >= 4) {
                    int st = fy - u;
                    for (int yy = fy - d + 1; yy < st; yy++) set(col, yy, POOL);
                    set(col, st, STEP);
                }
                if (u == S / 2 && v == S / 2) set(col, fy - d + 1, UNDERWATER_LIGHT);
            }
            case K_RAISED, K_TERRACE -> {
                set(col, fy, tile);
                boolean inner = u >= 3 && u <= S - 2 && v >= 3 && v <= S - 2;
                if (k == K_TERRACE && inner) {
                    set(col, fy, lining);
                    set(col, fy + 1, B.WATER);
                } else if (u >= 1 && v >= 1) {
                    set(col, fy + 1, tile);
                }
            }
            default -> set(col, fy, c.zone == Zone.DRY && Rng.chance(Rng.hash(seed ^ 0x6E1, x, z, f), 0.04) ? GRIME : tile);
        }
        int air0 = (k == K_RAISED || k == K_TERRACE) && u >= 1 && v >= 1 ? fy + 2 : fy + 1;
        for (int yy = air0; yy < ceil; yy++) set(col, yy, B.AIR);
        boolean panel = u >= 4 && u <= 7 && v >= 4 && v <= 7 && (u == 5 || u == 6 || v == 5 || v == 6) && !c.tower;
        set(col, ceil, panel ? PANEL : TILE);
        for (int yy = ceil + 1; yy <= top; yy++) set(col, yy, TILE);
        boolean screenN = c.sn != null && v == 0 && (u < c.sn[0] || u >= c.sn[0] + c.sn[1]);
        boolean screenW = c.sw != null && u == 0 && (v < c.sw[0] || v >= c.sw[0] + c.sw[1]);
        if (screenN || screenW) {
            int st = Math.min(ceil - 1, fy + (screenN ? c.sn[2] : c.sw[2]));
            int from = k == K_DEEP ? fy - d + 1 : k == K_SHALLOW ? fy : k >= K_RAISED ? fy + 2 : fy + 1;
            for (int yy = from; yy <= st; yy++) set(col, yy, TILE);
        }
        if (!c.tower) {
            if ((u == 5 || u == 6) && u == v) light(col, ceil - 1, 15);
            if (u == 6 && v == 5 && ceil - fy > 8) light(col, fy + 5, 15);
        }
        if (c.feat != null) {
            switch (c.feat.id()) {
                case "drain" -> {
                    if (u >= 5 && u <= 7 && v >= 5 && v <= 7) set(col, fy - 8, DRAIN);
                }
                case "service_door" -> {
                    if (u >= 5 && u <= 7 && v >= 5 && v <= 7) {
                        boolean edge = u == 5 || u == 7 || v == 5;
                        for (int yy = fy + 1; yy <= fy + 3; yy++) set(col, yy, edge ? GRAY : B.AIR);
                        set(col, fy + 4, GRAY);
                        set(col, fy, GRAY);
                        if (u == 6 && v == 6) set(col, fy + 3, B.light(8));
                    }
                }
                default -> {
                }
            }
        }
    }

    // ---------------------------------------------------------------- bright rooms

    private int brightCeil(int f) {
        return FLOORS[f] + (f == HIGH ? 28 : 22);
    }

    private boolean inBrightPool(int lx, int lz) {
        return lx >= 18 && lx <= 41 && lz >= 18 && lz <= 41;
    }

    private void bright(int f, Plan p, int lx, int lz, int x, int z, B[] col) {
        int fy = FLOORS[f], ceil = brightCeil(f);
        boolean pool = p.brightPool[f] && inBrightPool(lx, lz);
        boolean rimEdge = p.brightPool[f] && !pool && lx >= 17 && lx <= 42 && lz >= 17 && lz <= 42;
        if (pool) {
            set(col, fy - 2, POOL);
            set(col, fy - 1, B.WATER);
            set(col, fy, B.WATER);
        } else {
            boolean grid = Math.floorMod(x, 6) == 0 || Math.floorMod(z, 6) == 0;
            set(col, fy, rimEdge || grid ? POOL : TILE);
        }
        for (int yy = fy + 1; yy < ceil; yy++) set(col, yy, B.AIR);
        int mx = Math.floorMod(x, 4), mz = Math.floorMod(z, 4);
        set(col, ceil, (mx == 1 || mx == 2) && (mz == 1 || mz == 2) ? PANEL : TILE);
        if (Math.floorMod(x, 8) == 1 && Math.floorMod(z, 8) == 1) light(col, ceil - 1, 15);
        if (Math.floorMod(x, 6) == 3 && Math.floorMod(z, 6) == 3) light(col, fy + 6, 15);
        if (Math.floorMod(x, 8) == 5 && Math.floorMod(z, 8) == 5) light(col, fy + 14, 15);
        if (p.statue[f]) {
            int[] fd = rotDir(0, 1, p.face[f]);
            statue(col, HEAD, x - (p.rx * REG + 30), z - (p.rz * REG + 30), fy - 3, fd);
        }
    }

    // ---------------------------------------------------------------- the long tall hallway

    private int hallwayCeil(int f) {
        return FLOORS[f] + (f == HIGH ? 26 : 22);
    }

    /** Cubicle content behind hole k on side s: 0 plain, 1 flush pool, 2 sunken pool with steps, 3 bust, 4 glowing, 5 plain. */
    int cubicle(Plan p, int f, int k, int side) {
        int t = Rng.pick(Rng.hash(seed ^ 0xC0B1L, p.rx * 31L + p.rz, f * 64L + k, side), 6);
        if ((t == 1 || t == 2) && (k < 1 || k > 8)) return 0;
        return t;
    }

    private void hallway(int f, Plan p, int lx, int lz, int x, int z, B[] col) {
        boolean ax = p.alongX[f];
        int a = ax ? lx : lz, b = ax ? lz : lx;
        if (b < 16 || b > 43) return;
        int fy = FLOORS[f], ceil = hallwayCeil(f);
        int side = b <= 29 ? 0 : 1;
        int m = side == 0 ? b : REG - 1 - b; // mirrored across: 16 outer wall, 17-22 cubicles, 23-26 wall, 27-29 hall
        for (int yy = fy; yy <= ceil; yy++) set(col, yy, TILE);
        int k = a / 6;
        if (m >= 27) {
            for (int yy = fy + 1; yy < ceil; yy++) set(col, yy, B.AIR);
            if (m == 29 && a >= 2 && a <= REG - 3) {
                set(col, fy - 1, POOL);
                set(col, fy, B.WATER);
            } else if (m == 27 && a % 3 == 0) {
                set(col, fy, POOL);
            }
            set(col, ceil, m == 29 ? PANEL : m == 28 && a % 6 == 3 ? PANEL : TILE);
            if (m == 29 && a % 4 == 0) light(col, ceil - 1, 15);
            if (m == 28 && a % 6 == 3) {
                light(col, fy + 4, 15);
                light(col, fy + 12, 15);
            }
        } else if (m >= 23) {
            // the wall: a walkable 2x2 hole into every cubicle, and glowing niches high up
            if (a % 6 >= 1 && a % 6 <= 2 && a > 0 && a < REG - 1) {
                set(col, fy + 1, B.AIR);
                set(col, fy + 2, B.AIR);
            }
            if (a % 6 >= 3 && a % 6 <= 4 && m >= 25) {
                int ny = (k % 2 == 0) ? fy + 9 : fy + 15;
                for (int yy = ny; yy <= ny + 1; yy++) set(col, yy, m == 26 ? B.AIR : PANEL);
                if (m == 26 && a % 6 == 3) set(col, ny, B.light(12));
            }
            if (m == 23 && (a % 6 == 0 || a % 6 == 3)) set(col, fy + 3, POOL); // a blue course along the hole tops
        } else if (m >= 17) {
            boolean partition = a % 6 == 5 || a == 0;
            if (!partition) {
                int t = cubicle(p, f, k, side);
                for (int yy = fy + 1; yy <= fy + 4; yy++) set(col, yy, B.AIR);
                int ai = a % 6;
                boolean centre = ai == 2 && m == 19;
                set(col, fy + 5, t == 4 || centre ? PANEL : TILE);
                if (centre) light(col, fy + 4, t == 4 ? 15 : 13);
                if (t == 4) {
                    set(col, fy, POOL);
                } else if (t == 1 && m <= 21) {
                    set(col, fy - 3, POOL);
                    for (int yy = fy - 2; yy <= fy; yy++) set(col, yy, B.WATER);
                } else if (t == 2 && m <= 21) {
                    String up = ax ? (side == 0 ? "south" : "north") : (side == 0 ? "east" : "west");
                    if (m == 21) {
                        set(col, fy, B.AIR);
                        set(col, fy - 1, B.stairs(Material.QUARTZ_STAIRS, up, false));
                    } else if (m == 20) {
                        set(col, fy, B.AIR);
                        set(col, fy - 1, B.AIR);
                        set(col, fy - 2, B.stairs(Material.QUARTZ_STAIRS, up, false));
                    } else {
                        set(col, fy, B.AIR);
                        set(col, fy - 1, B.AIR);
                        for (int yy = fy - 6; yy <= fy - 2; yy++) set(col, yy, B.WATER);
                        set(col, fy - 7, POOL);
                    }
                } else if (m == 22) {
                    set(col, fy, TILE);
                } else {
                    set(col, fy, (ai + m) % 2 == 0 ? POOL : TILE);
                }
            }
        } else {
            // outer wall: plain cubicles open onto the bays behind them
            int t = cubicle(p, f, k, side);
            if ((t == 0 || t == 3 || t == 5) && a % 6 >= 1 && a % 6 <= 2 && a > 0) {
                set(col, fy + 1, B.AIR);
                set(col, fy + 2, B.AIR);
            }
        }
    }

    // ---------------------------------------------------------------- stair towers

    /**
     * A square spiral stair (2-wide lanes round a solid core, 16 blocks of rise per lap) from the lower floor to the
     * upper one (to the main floor under a massive hall), with a door on every floor at the north-west landing.
     */
    private void tower(Plan p, int u, int v, B[] col) {
        int base = FLOORS[LOW], topFloor = FLOORS[p.towerTop()], top = topFloor + 5;
        boolean wall = u == 2 || u == 11 || v == 2 || v == 11;
        boolean core = u >= 5 && u <= 8 && v >= 5 && v <= 8;
        for (int yy = base; yy <= top; yy++) set(col, yy, TILE);
        if (wall) {
            for (int f = LOW; f <= p.towerTop(); f++) {
                int fy = FLOORS[f];
                set(col, fy + 4, POOL);
                if (u == 2 && (v == 3 || v == 4)) for (int yy = fy + 1; yy <= fy + 3; yy++) set(col, yy, B.AIR);
            }
            return;
        }
        if (core) {
            boolean rimC = u == 5 || u == 8 || v == 5 || v == 8;
            for (int yy = base; yy < top; yy++) if (rimC && Math.floorMod(yy - base, 8) == 4) set(col, yy, PANEL);
            return;
        }
        for (int yy = base + 1; yy < top; yy++) set(col, yy, B.AIR);
        int off;
        B blk;
        boolean corner = false;
        if (v <= 4 && u <= 4) { off = 0; blk = TILE; corner = true; }
        else if (v <= 4 && u <= 8) { off = u - 4; blk = B.stairs(Material.QUARTZ_STAIRS, "east", false); }
        else if (v <= 4) { off = 4; blk = TILE; corner = true; }
        else if (u >= 9 && v <= 8) { off = v; blk = B.stairs(Material.QUARTZ_STAIRS, "south", false); }
        else if (u >= 9) { off = 8; blk = TILE; corner = true; }
        else if (u >= 5) { off = 8 + (9 - u); blk = B.stairs(Material.QUARTZ_STAIRS, "west", false); }
        else if (v >= 9) { off = 12; blk = TILE; corner = true; }
        else { off = 12 + (9 - v); blk = B.stairs(Material.QUARTZ_STAIRS, "north", false); }
        int laps = (topFloor - base) / 16;
        for (int lap = 0; lap <= laps; lap++) {
            int yy = base + lap * 16 + off;
            if (lap == laps && off != 0) break;
            set(col, yy, blk);
            if (corner && u % 2 == 1 && v % 2 == 1) light(col, yy + 3, 15);
        }
    }

    // ---------------------------------------------------------------- massive halls

    private void massive(Hall hl, int lx, int lz, int x, int z, B[] col) {
        int[] l = w2l(lx, lz, hl.rot);
        int a = l[0], b = l[1];
        int fy = FLOORS[MID];
        set(col, fy, Math.floorMod(x, 6) == 0 || Math.floorMod(z, 6) == 0 ? POOL : TILE);
        for (int yy = fy + 1; yy < HALL_CEIL; yy++) set(col, yy, B.AIR);
        int mx = Math.floorMod(x, 6), mz = Math.floorMod(z, 6);
        set(col, HALL_CEIL, (mx == 2 || mx == 3) && (mz == 2 || mz == 3) ? PANEL : TILE);
        // pools
        boolean p1 = a >= 22 && a <= 42 && b >= 36 && b <= 55, p2 = a >= 35 && a <= 55 && b >= 8 && b <= 31;
        if (p1 || p2) {
            int d = p1 ? 5 : 6;
            set(col, fy - d, POOL);
            for (int yy = fy - d + 1; yy <= fy; yy++) set(col, yy, B.WATER);
            if ((p1 && a == 32 && b == 50) || (p2 && a == 37 && b == 29)) set(col, fy - d + 1, UNDERWATER_LIGHT);
        }
        // gallery at the upper floor, carried on a deep beam so neighbouring pools stay sealed
        int ring = Math.min(Math.min(a, b), Math.min(REG - 1 - a, REG - 1 - b));
        if (ring <= 3) {
            for (int yy = FLOORS[HIGH] - 10; yy <= FLOORS[HIGH]; yy++) set(col, yy, yy == FLOORS[HIGH] - 10 ? POOL : TILE);
            boolean opening = (b == 3 && a >= 17 && a <= 23) || hl.path.containsKey(key(x, z));
            if (ring == 3 && !opening) {
                set(col, FLOORS[HIGH] + 1, POOL);
                set(col, FLOORS[HIGH] + 2, pane(hl, a, b));
            }
            if (ring == 1 && (a + b) % 6 == 0) light(col, FLOORS[HIGH] + 4, 15);
            if (ring == 1 && (a + b) % 8 == 0) light(col, FLOORS[HIGH] - 13, 15);
        }
        // grand staircase from the hall floor up to the gallery along one side
        if (b >= 4 && b <= 6 && a >= 21 && a <= 52) {
            int k = 52 - a;
            for (int yy = fy + 1; yy < fy + 1 + k; yy++) set(col, yy, TILE);
            int[] dir = rotDir(-1, 0, hl.rot);
            set(col, fy + 1 + k, B.stairs(Material.QUARTZ_STAIRS, facing(dir[0], dir[1]), false));
        } else if (b >= 4 && b <= 6 && a >= 17 && a <= 20) {
            for (int yy = fy + 1; yy <= FLOORS[HIGH]; yy++) set(col, yy, yy == FLOORS[HIGH] ? POOL : TILE);
        } else if (b == 7 && a >= 21 && a <= 52) {
            int k = 52 - a;
            for (int yy = fy + 1; yy <= fy + 2 + k; yy++) set(col, yy, yy == fy + 2 + k ? POOL : TILE);
        } else if ((b == 7 && a >= 16 && a <= 20) || (a == 16 && b >= 4 && b <= 6)) {
            for (int yy = fy + 1; yy <= FLOORS[HIGH] + 1; yy++) set(col, yy, yy == FLOORS[HIGH] + 1 ? POOL : TILE);
            set(col, FLOORS[HIGH] + 2, B.of(Material.WHITE_STAINED_GLASS_PANE));
        }
        // the slide: stepped bed, blue rims, piers every six blocks
        Integer i = hl.path.get(key(x, z));
        if (i != null) {
            if (i % 6 == 3 && i > 3) for (int yy = fy + 1; yy < hl.py[i]; yy++) set(col, yy, TILE);
            if (i % 6 == 3 && i > 3 && (p1 || p2)) for (int yy = fy - 6; yy <= fy; yy++) if (get(col, yy) == B.WATER) set(col, yy, TILE);
            set(col, hl.py[i], hl.bed[i]);
            for (int yy = hl.py[i] + 1; yy <= hl.py[i] + 4; yy++) set(col, yy, B.AIR);
        }
        Integer r = hl.rim.get(key(x, z));
        if (r != null) {
            set(col, r, POOL);
            set(col, r + 1, POOL);
            set(col, r + 2, LIP);
        }
        // the giant head, half sunk in the second pool, looking out over the hall
        statue(col, HEAD_BIG, x - hl.statueX, z - hl.statueZ, hl.statueY, hl.statueFace);
        // invisible lights hanging in the air
        if (Math.floorMod(a, 8) == 4 && Math.floorMod(b, 8) == 4) {
            light(col, fy + 7, 15);
            light(col, fy + 22, 15);
            light(col, fy + 40, 15);
        }
        if (Math.floorMod(x, 6) == 2 && Math.floorMod(z, 6) == 2) light(col, HALL_CEIL - 1, 15);
    }

    private B pane(Hall hl, int a, int b) {
        // a glass balustrade: connect along the ring
        boolean alongA = b == 3 || b == REG - 4;
        int[] d = alongA ? rotDir(1, 0, hl.rot) : rotDir(0, 1, hl.rot);
        return d[0] != 0 ? B.of(Material.WHITE_STAINED_GLASS_PANE, "[east=true,west=true]")
                : B.of(Material.WHITE_STAINED_GLASS_PANE, "[north=true,south=true]");
    }

    // ---------------------------------------------------------------- the giant Greek head

    /** A voxelised classical head at one scale: face toward +z, chin at y = 0, centred on x = z = 0. */
    static final class Head {
        final double s;
        final int hx, hy0, hy1, hz;
        final B[] g;

        Head(double s) {
            this.s = s;
            hx = (int) Math.ceil(8.6 * s);
            hz = (int) Math.ceil(10.2 * s);
            hy0 = (int) Math.floor(-9 * s);
            hy1 = (int) Math.ceil(21.6 * s);
            g = new B[(2 * hx + 1) * (hy1 - hy0 + 1) * (2 * hz + 1)];
            for (int x = -hx; x <= hx; x++) for (int yy = hy0; yy <= hy1; yy++) for (int z = -hz; z <= hz; z++) {
                g[idx(x, yy, z)] = headAt(x / s, (yy + 0.5) / s, z / s);
            }
        }

        int idx(int x, int y, int z) {
            return ((x + hx) * (hy1 - hy0 + 1) + (y - hy0)) * (2 * hz + 1) + (z + hz);
        }

        B at(int x, int y, int z) {
            if (Math.abs(x) > hx || Math.abs(z) > hz || y < hy0 || y > hy1) return null;
            return g[idx(x, y, z)];
        }
    }

    private static final Head HEAD = new Head(1.0), HEAD_BIG = new Head(1.25);

    private static double sq(double d) {
        return d * d;
    }

    private static boolean ell(double x, double y, double z, double cx, double cy, double cz, double rx, double ry, double rz) {
        return sq((x - cx) / rx) + sq((y - cy) / ry) + sq((z - cz) / rz) <= 1;
    }

    private static boolean hairZone(double x, double y, double z) {
        return y > 13.3 || (z < -1.6 && y > 5.5) || (Math.abs(x) > 5.6 && y > 11 && z < 2.5);
    }

    /**
     * The head in head units (about 13 wide, 21 tall): cranium and jaw volumes cut flat at the face, a heavy brow,
     * deep eye sockets with blank eyeballs, a straight Greek nose, full parted lips, a rounded chin, ears, and hair
     * in curls (a curly shell over the crown and the back, a fringe of round curls over the forehead).
     */
    static B headAt(double x, double y, double z) {
        double ax = Math.abs(x);
        if (y < 0.6) return y > -9 && sq(x / 3.9) + sq((z + 1.2) / 4.2) <= 1 ? SKIN : null; // the neck
        if (ell(ax, y, z, 3.0, 9.8, 4.6, 1.25, 0.9, 1.0)) return SKIN;                     // blank eyeballs
        if (ell(ax, y, z, 3.0, 9.9, 6.0, 1.9, 1.2, 1.9)) return null;                      // eye sockets
        if (ell(ax, y, z, 0.95, 5.7, 6.7, 0.55, 0.45, 0.9)) return null;                   // nostrils
        if (ell(x, y, z, 0, 3.65, 6.3, 2.8, 0.4, 1.4)) return null;                        // parted lips
        if (y >= 11.0 && y <= 12.4 && ax <= 5.7 && z >= 3 && z <= 6.6 - 0.05 * ax * ax) return SKIN; // brow
        if (y >= 5.2 && y <= 11.4) {                                                        // nose
            double w = 0.7 + (11.4 - y) * 0.14, zm = 5.6 + (11.4 - y) * 0.33;
            if (ax <= w && z <= zm && z >= 3) return SKIN;
        }
        if (ell(x, y, z, 0, 4.45, 5.7, 2.7, 0.65, 1.0)) return SKIN;                       // upper lip
        if (ell(x, y, z, 0, 2.85, 5.55, 2.2, 0.75, 1.0)) return SKIN;                      // lower lip
        if (ell(x, y, z, 0, 1.5, 4.7, 2.4, 1.7, 1.4)) return SKIN;                         // chin
        if (ell(ax, y, z, 3.7, 6.8, 3.9, 1.9, 2.3, 1.7)) return SKIN;                      // cheeks
        if (ell(ax, y, z, 6.7, 8.6, -0.6, 0.9, 2.3, 1.3)) return SKIN;                     // ears
        for (double xi = -4.6; xi <= 4.7; xi += 2.3) {
            if (ell(x, y, z, xi, 13.9, 5.0, 1.2, 1.2, 1.2)) return HAIR;                    // fringe curls
        }
        if (ell(ax, y, z, 6.1, 12.2, 2.8, 1.3, 1.6, 1.3)) return HAIR;                     // curls at the temples
        boolean cran = ell(x, y, z, 0, 11.6, -0.8, 6.6, 8.6, 7.6), jaw = ell(x, y, z, 0, 5.6, 0.3, 5.3, 5.9, 6.2);
        if ((cran || jaw) && z <= 5.3) return hairZone(x, y, z) ? HAIR : SKIN;
        if (ell(x, y, z, 0, 11.8, -1.0, 7.7, 9.7, 8.6) && hairZone(x, y, z) && y > 6
                && Math.sin(x * 1.35) + Math.sin(y * 1.35 + 1.1) + Math.sin(z * 1.35 + 2.3) > 0.8) return HAIR; // curls
        return null;
    }

    /**
     * Paints a head into one column: (ox, oz) = offset of the column from the head's centre, chin at {@code y0},
     * facing world direction {@code fd}. The statue only ever adds blocks, so below the water line its hollows stay
     * water. Two invisible lights hang in front of the face so it is lit from the front.
     */
    private static void statue(B[] col, Head hd, int ox, int oz, int y0, int[] fd) {
        int reach = Math.max(hd.hx, hd.hz) + 3;
        if (Math.abs(ox) > reach || Math.abs(oz) > reach) return;
        int zl = ox * fd[0] + oz * fd[1];
        int xl = -ox * fd[1] + oz * fd[0];
        for (int yy = hd.hy0; yy <= hd.hy1; yy++) {
            B b = hd.at(xl, yy, zl);
            if (b != null) set(col, y0 + yy, b);
        }
        if (zl == (int) Math.round(9.5 * hd.s) + 2 && Math.abs(xl) == (int) Math.round(3 * hd.s)) {
            light(col, y0 + (int) Math.round(8 * hd.s), 15);
            light(col, y0 + (int) Math.round(14 * hd.s), 15);
        }
    }

    // ================================================================== LevelGenerator surface

    @Override
    protected void paintCell(CellCtx c) {
        if (c.floor != 0) return; // the whole column, all three storeys, is painted at once
        Plan p = plan(Math.floorDiv(c.cx, R), Math.floorDiv(c.cz, R));
        Cell[] cells = {new Cell(LOW, c.cx, c.cz, p), new Cell(MID, c.cx, c.cz, p), new Cell(HIGH, c.cx, c.cz, p)};
        B[] col = new B[H];
        for (int u = c.u0; u <= c.u1; u++) {
            for (int v = c.v0; v <= c.v1; v++) {
                column(c.wx(u), c.wz(v), col, p, cells);
                for (int i = 0; i < H; i++) c.set(u, BOTTOM + i, v, col[i]);
            }
        }
    }

    @Override
    protected int[] triggerBox(Feature f) {
        return switch (f.id()) {
            case "drain" -> new int[]{5, -9, 5, 7, -6, 7};
            case "service_door" -> new int[]{6, 0, 6, 6, 1, 6};
            default -> null;
        };
    }

    @Override
    public boolean isSafeCell(int floor, int cx, int cz) {
        if (featureAt(floor, cx, cz) != null) return false;
        Plan p = plan(Math.floorDiv(cx, R), Math.floorDiv(cz, R));
        if (p.zone[floor] == Zone.MASSIVE && floor == HIGH) return false;
        B[] col = column(cx * S + S / 2, cz * S + S / 2);
        int fy = FLOORS[floor];
        B fl = get(col, fy), feet = get(col, fy + 1), head = get(col, fy + 2);
        return !fl.passable() && !fl.state.contains("waterlogged") && feet.passable() && feet.mat != Material.WATER
                && head.passable() && head.mat != Material.WATER;
    }

    /** Every arrival lands on the main floor. */
    @Override
    public int[] safeSpot(int x, int z, int preferredFloor) {
        return super.safeSpot(x, z, MID);
    }

    @Override
    public String zoneName(int floor, int cx, int cz) {
        Plan p = plan(Math.floorDiv(cx, R), Math.floorDiv(cz, R));
        Zone zn = p.zone[floor];
        String s = FLOOR_NAMES[floor] + " floor, " + zn.name().toLowerCase(java.util.Locale.ROOT);
        if (cx == p.towerCx && cz == p.towerCz) return s + " stair tower";
        if (zn == Zone.BAYS || zn == Zone.DRY || zn == Zone.HALLWAY) {
            Cell c = cell(floor, cx, cz);
            s += " bay " + KINDS[c.kind] + (c.kind == K_DEEP ? " d" + c.depth : "") + " ceiling " + (c.ceil - FLOORS[floor] - 1);
        }
        return s;
    }

    // ================================================================== decorations

    private static final String[] FLOATIES = {"floatie_ring:0", "floatie_ring:1", "floatie_ring:2", "floatie_ring:3", "floatie_duck",
            "floatie_mattress", "beach_ball", "floatie_ring:1", "floatie_mattress", "floatie_duck"};

    private DecorSpec floatie(long h, double x, double surfaceY, double z) {
        return new DecorSpec(FLOATIES[Rng.pick(h, FLOATIES.length)], x, surfaceY - 0.12, z, (float) Rng.pick(Rng.hash(h, 1), 360), 0, 0, h);
    }

    @Override
    protected void cellDecor(int floor, int cx, int cz, List<DecorSpec> out) {
        Plan p = plan(Math.floorDiv(cx, R), Math.floorDiv(cz, R));
        Zone zn = p.zone[floor];
        long h = ch(floor, cx, cz, 0xDEC1);
        int fy = FLOORS[floor] + 1;
        double dens = Math.max(0.05, gs.get("decor-density", 1.0));
        if (cx == p.towerCx && cz == p.towerCz) {
            if (floor <= p.towerTop()) {
                out.add(new DecorSpec("floor_sign:" + floor, cx * S + 2 - 0.002, fy + 2.3, cz * S + 6.5, 90f, 0, 0, h));
            }
            return;
        }
        if (zn == Zone.MASSIVE) {
            if (floor == MID) for (DecorSpec d : hallDecor(p)) if (cellOf((int) Math.floor(d.x)) == cx && cellOf((int) Math.floor(d.z)) == cz) out.add(d);
            return;
        }
        if (zn == Zone.BRIGHT) {
            for (DecorSpec d : brightDecor(p, floor)) if (cellOf((int) Math.floor(d.x)) == cx && cellOf((int) Math.floor(d.z)) == cz) out.add(d);
            return;
        }
        Cell c = cell(floor, cx, cz);
        if (c.band) {
            hallwayDecor(p, floor, cx, cz, out);
            return;
        }
        int k = c.kind;
        if (k == K_DEEP) out.add(spec("pool_lamp", cx, cz, 6.5, fy - c.depth + 0.02, 6.5, 0f, h));
        if (k != K_DRY && k != K_RAISED && Rng.chance(Rng.hash(h, 1), 0.3)) out.add(spec("caustics", cx, cz, 6.0, c.ceil - 0.02, 6.0, (float) Rng.pick(h, 4) * 90f, h));
        Feature f = c.feat;
        if (f != null) {
            switch (f.id()) {
                case "drain" -> out.add(spec("drain_vortex", cx, cz, 6.5, fy - 8.95, 6.5, 0f, f.seed()));
                case "service_door" -> out.add(spec("staff_sign", cx, cz, 6.5, fy + 3.3, 8.02, 0f, f.seed()));
                case "lifeguard_post" -> out.add(spec("lifeguard_chair", cx, cz, 6.5, fy, 6.5, (float) (f.rot() * 90), f.seed())
                        .barrier(cx * S + 6, fy, cz * S + 6));
                default -> {
                }
            }
            return;
        }
        double r = Rng.unit(Rng.hash(h, 2)) / dens;
        switch (k) {
            case K_DEEP -> {
                if (r < 0.32) out.add(floatie(Rng.hash(h, 3), cx * S + 3.5 + Rng.pick(Rng.hash(h, 4), 6), fy, cz * S + 3.5 + Rng.pick(Rng.hash(h, 5), 6)));
                else if (r < 0.42) out.add(spec("floating_chair_pool", cx, cz, 4.5 + Rng.pick(h, 5), fy - 0.35, 6.5, (float) Rng.pick(h, 360), h));
                else if (r < 0.5) out.add(spec("submerged_bench", cx, cz, 6.5, fy - c.depth, 3.5, (float) Rng.pick(h, 360), h));
            }
            case K_SHALLOW -> {
                if (r < 0.18) out.add(floatie(Rng.hash(h, 3), cx * S + 4.5 + Rng.pick(Rng.hash(h, 4), 4), fy, cz * S + 4.5 + Rng.pick(Rng.hash(h, 5), 4)));
                else if (r < 0.28) out.add(spec("submerged_bench", cx, cz, 4.5 + Rng.pick(h, 4), fy - 1.0, 7.5, (float) Rng.pick(h, 4) * 90f, h));
                else if (r < 0.36) out.add(spec("drip_emitter", cx, cz, 7.5, c.ceil - 0.05, 7.5, 0f, h));
            }
            case K_TERRACE -> {
                if (r < 0.4) out.add(floatie(Rng.hash(h, 3), cx * S + 6.5, fy + 1, cz * S + 6.5));
            }
            case K_RAISED -> {
            }
            default -> {
                if (r < 0.08) out.add(spec("drain_grate", cx, cz, 6.0, fy + 0.001, 9.0, 0f, h));
                else if (r < 0.14) out.add(spec("pool_bench", cx, cz, 6.5, fy, 3.5, 0f, h).barrier(cx * S + 6, fy, cz * S + 3));
                else if (r < 0.2) out.add(spec("pool_lounger", cx, cz, 8.5, fy, 4.5, (float) (Rng.pick(Rng.hash(h, 6), 4) * 90), h));
                else if (r < 0.23) out.add(new DecorSpec("impossible_stairs", cx * S + 9.5, fy + 2.0, cz * S + 9.5, 135f, 0f, 0f, h));
                else if (r < 0.27) out.add(spec("lifebuoy", cx, cz, 2.0, fy + 1.8, 1.0, -90f, h));
                else if (r < 0.3 && zn == Zone.DRY) out.add(floatie(Rng.hash(h, 3), cx * S + 7.5, fy + 0.1, cz * S + 8.5));
            }
        }
    }

    private List<DecorSpec> hallDecor(Plan p) {
        Hall hl = p.hall;
        List<DecorSpec> out = new ArrayList<>();
        long h = Rng.hash(seed ^ 0x4A11DL, p.rx, p.rz);
        int fy = FLOORS[MID] + 1;
        // the water sheet running down each straight run of the chute
        for (int[] seg : new int[][]{{0, 29}, {30, Hall.N - 1}}) {
            int i0 = seg[0];
            int dx = hl.px[i0 + 1] - hl.px[i0], dz = hl.pz[i0 + 1] - hl.pz[i0];
            double x = hl.px[i0] + 0.5 - dx * 0.5, z = hl.pz[i0] + 0.5 - dz * 0.5;
            double yy = Hall.BED0 + 1 - i0 / 2.0 + 0.78;
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            out.add(new DecorSpec("slide_water:" + (seg[1] - seg[0] + 1), x, yy, z, yaw, (float) Math.toDegrees(Math.atan(0.5)), 0, Rng.hash(h, i0)));
        }
        // floaties drifting in both pools, lamps in their floors
        int[][] spots = {{28, 44}, {37, 51}, {26, 52}, {40, 40}, {36, 30}, {24, 47}};
        for (int i = 0; i < spots.length; i++) {
            if (!Rng.chance(Rng.hash(h, 100 + i), 0.75)) continue;
            int[] w = l2w(spots[i][0], spots[i][1], hl.rot);
            out.add(floatie(Rng.hash(h, 200 + i), hl.x0 + w[0] + 0.5, fy, hl.z0 + w[1] + 0.5));
        }
        for (int[] s : new int[][]{{27, 41, 5}, {38, 48, 5}, {37, 9, 6}}) {
            int[] w = l2w(s[0], s[1], hl.rot);
            out.add(new DecorSpec("pool_lamp", hl.x0 + w[0] + 0.5, fy - 1 - s[2] + 0.02, hl.z0 + w[1] + 0.5, 0f, 0, 0, h));
        }
        // loungers along the big pool
        for (int i = 0; i < 4; i++) {
            int[] w = l2w(23 + i * 5, 33, hl.rot);
            int[] d = rotDir(0, 1, hl.rot);
            out.add(new DecorSpec("pool_lounger", hl.x0 + w[0] + 0.5, fy, hl.z0 + w[1] + 0.5, (float) Math.toDegrees(Math.atan2(-d[0], d[1])), 0, 0, Rng.hash(h, 300 + i)));
        }
        return out;
    }

    private List<DecorSpec> brightDecor(Plan p, int f) {
        List<DecorSpec> out = new ArrayList<>();
        long h = Rng.hash(seed ^ 0xB417L, p.rx, p.rz, f);
        int fy = FLOORS[f] + 1, x0 = p.rx * REG, z0 = p.rz * REG;
        if (p.brightPool[f]) {
            int[][] spots = {{21, 22}, {37, 24}, {23, 37}, {36, 38}};
            for (int i = 0; i < spots.length; i++) {
                if (!Rng.chance(Rng.hash(h, i), 0.6)) continue;
                out.add(floatie(Rng.hash(h, 10 + i), x0 + spots[i][0] + 0.5, fy, z0 + spots[i][1] + 0.5));
            }
            for (int i = 0; i < 5; i++) {
                if (!Rng.chance(Rng.hash(h, 20 + i), 0.6)) continue;
                out.add(new DecorSpec("pool_lounger", x0 + 20.5 + i * 4, fy, z0 + 14.5, 0f, 0, 0, Rng.hash(h, 30 + i)));
            }
        } else {
            for (int i = 0; i < 3; i++) {
                if (!Rng.chance(Rng.hash(h, 40 + i), 0.5)) continue;
                out.add(floatie(Rng.hash(h, 50 + i), x0 + 15.5 + Rng.pick(Rng.hash(h, 60 + i), 30), fy + 0.1, z0 + 15.5 + Rng.pick(Rng.hash(h, 70 + i), 30)));
            }
        }
        return out;
    }

    private void hallwayDecor(Plan p, int f, int cx, int cz, List<DecorSpec> out) {
        boolean ax = p.alongX[f];
        int fy = FLOORS[f] + 1;
        int x0 = p.rx * REG, z0 = p.rz * REG;
        for (int side = 0; side < 2; side++) {
            for (int k = 0; k < 10; k++) {
                int t = cubicle(p, f, k, side);
                double a = k * 6 + 2.5, b = side == 0 ? 18.5 : REG - 18.5;
                double x = x0 + (ax ? a : b), z = z0 + (ax ? b : a);
                if (cellOf((int) Math.floor(x)) != cx || cellOf((int) Math.floor(z)) != cz) continue;
                long h = Rng.hash(seed ^ 0xC0BDL, p.rx * 31L + p.rz, f * 64L + k, side);
                // facing toward the hallway
                int[] toHall = ax ? new int[]{0, side == 0 ? 1 : -1} : new int[]{side == 0 ? 1 : -1, 0};
                float yaw = (float) Math.toDegrees(Math.atan2(-toHall[0], toHall[1]));
                switch (t) {
                    case 1, 2 -> out.add(floatie(h, x, t == 1 ? fy : fy - 2, z));
                    case 3 -> out.add(new DecorSpec("greek_bust", x, fy, z, yaw, 0, 0, h).barrier((int) Math.floor(x), fy, (int) Math.floor(z)));
                    case 0 -> out.add(new DecorSpec(Rng.chance(h, 0.5) ? "pool_bench" : "pool_lounger", x, fy, z, yaw, 0, 0, h));
                    default -> {
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ surface details

    private boolean wetCell(int floor, int cx, int cz) {
        Cell c = cell(floor, cx, cz);
        return (c.zone == Zone.BAYS || c.zone == Zone.DRY || c.zone == Zone.HALLWAY) && c.wet();
    }

    @Override
    protected void cellDetails(int floor, int cx, int cz, List<DecorSpec> out) {
        if (featureAt(floor, cx, cz) != null) return;
        Cell c = cell(floor, cx, cz);
        if (!c.pillars || c.band || c.tower) return;
        int k = c.kind;
        long h = ch(floor, cx, cz, 0xD7A2);
        double ox = cx * S, oz = cz * S;
        double fy = FLOORS[floor] + 1;
        int len = S - 2;
        if (k == K_DRY) {
            if (wetCell(floor, cx - 1, cz) && c.sw == null)
                out.add(detail(DecorSpec.TIER_ARCH, Rng.hash(h, 1), "d:coping:" + len, ox, fy, oz + 2, 90f, 0f));
            if (wetCell(floor, cx, cz - 1) && c.sn == null)
                out.add(detail(DecorSpec.TIER_ARCH, Rng.hash(h, 2), "d:coping:" + len, ox + S, fy, oz, 180f, 0f));
            if (wetCell(floor, cx + 1, cz) && cell(floor, cx + 1, cz).sw == null && !isTower(cx + 1, cz))
                out.add(detail(DecorSpec.TIER_ARCH, Rng.hash(h, 3), "d:coping:" + len, ox + S, fy, oz + S, -90f, 0f));
            if (wetCell(floor, cx, cz + 1) && cell(floor, cx, cz + 1).sn == null && !isTower(cx, cz + 1))
                out.add(detail(DecorSpec.TIER_ARCH, Rng.hash(h, 4), "d:coping:" + len, ox + 2, fy, oz + S, 0f, 0f));
        }
        if (k == K_DEEP) out.add(detail(DecorSpec.TIER_ARCH, Rng.hash(h, 5), "d:drain:1.2", ox + 9.5, fy - c.depth + 0.002, oz + 9.5, 0f, 0f));
        out.add(detail(DecorSpec.TIER_ARCH, Rng.hash(h, 6), "d:rcorner:smooth_quartz:" + (c.ceil - FLOORS[floor] - 1), ox + 2, fy, oz + 2, 0f, 0f));
        if (k == K_DRY && Rng.chance(Rng.hash(h, 7), 0.25)) {
            out.add(detail(DecorSpec.TIER_ENV, Rng.hash(h, 8), "d:grille:0.6:0.3", ox + 2.003, fy + 0.25, oz + 1.7, -90f, 0f));
        }
    }

    private boolean isTower(int cx, int cz) {
        Plan p = plan(Math.floorDiv(cx, R), Math.floorDiv(cz, R));
        return p.towerCx == cx && p.towerCz == cz;
    }
}
