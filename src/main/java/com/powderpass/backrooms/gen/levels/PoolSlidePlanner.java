package com.powderpass.backrooms.gen.levels;

import com.powderpass.backrooms.gen.B;
import com.powderpass.backrooms.gen.CustomBlocks;
import com.powderpass.backrooms.gen.DecorSpec;
import com.powderpass.backrooms.gen.SlidePath;
import com.powderpass.backrooms.gen.levels.PoolroomsGenerator.Zone;
import com.powderpass.backrooms.util.Rng;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Water slides all over the Poolrooms, decided per 60 x 60 region and storey (switched on by {@code slide-chance} and
 * {@code fun-slide-chance}; absent from older worlds' settings = none, so they never change).
 * <ul>
 *   <li><b>Storey slides</b> start on a platform on one floor and end in a landing pool one floor down, boring
 *   through walls and the floor slab on the way; some land in a <b>pocket</b>: a small walled pool room with one
 *   door, a place you only find by riding there.</li>
 *   <li><b>Fun slides</b> start on a taller platform and land in a pool on the same floor.</li>
 * </ul>
 * Every slide is a {@link SlidePath} placed at one of up to 16 deterministic candidate spots and rotations; the first
 * that fits wins: platform and landing on solid floor in ordinary space, and a tunnel that never touches a stair
 * tower, a transition, a massive hall or any water it could let out. Its block edits (platform, stairs, landing pool,
 * pocket, tunnel bore, support pillars, tunnel lights) are applied on top of the painted column, so the whole thing
 * stays a pure function of seed and coordinates. The chute itself is display entities ({@code slide_seg} decor).
 */
final class PoolSlidePlanner {
    static final int FORCE = 0, CARVE = 1, FILL_AIR = 2;
    private static final int REG = 60, CANDIDATES = 16, STOREY = 0, FUN = 1;

    record Edit(int y0, int y1, B b, int mode) {
    }

    /** One placed slide: its path in world space, what it changes, and its ride. */
    static final class Placed {
        final SlidePath path;
        final long seed;
        final double yaw;
        final double[][] pts;
        final boolean pocket;
        final int from, to;
        final PoolroomsGenerator.Slide slide;
        final Map<Long, List<Edit>> edits = new HashMap<>();
        final List<DecorSpec> extras = new ArrayList<>();
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

        Placed(SlidePath path, long seed, double yaw, double[][] pts, boolean pocket, int from, int to, PoolroomsGenerator.Slide slide) {
            this.path = path;
            this.seed = seed;
            this.yaw = yaw;
            this.pts = pts;
            this.pocket = pocket;
            this.from = from;
            this.to = to;
            this.slide = slide;
        }

        void add(int x, int z, Edit e) {
            edits.computeIfAbsent(key(x, z), k -> new ArrayList<>()).add(e);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }

        boolean covers(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        /** The model id of the chute between points i0 and i1. */
        String segmentId(int i0, int i1) {
            return "slide_seg:" + path.style.id() + ":" + (path.wet ? "wet" : "dry") + ":" + (int) Math.round(path.drop) + ":" + i0 + ":" + i1;
        }
    }

    private final PoolroomsGenerator g;
    private final Map<Long, List<Placed>> cache = new ConcurrentHashMap<>();

    PoolSlidePlanner(PoolroomsGenerator g) {
        this.g = g;
    }

    static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static long cell3(int x, int y, int z) {
        return ((long) (x & 0x3FFFFF) << 42) | ((long) (y & 0xFFFFF) << 22) | (z & 0x3FFFFF);
    }

    boolean enabled() {
        return g.gs().get("slide-chance", 0.0) > 0 || g.gs().get("fun-slide-chance", 0.0) > 0;
    }

    // ------------------------------------------------------------------ lookups

    List<Placed> region(int rx, int rz) {
        if (!enabled()) return List.of();
        long k = key(rx, rz);
        List<Placed> l = cache.get(k);
        if (l != null) return l;
        if (cache.size() > 2048) cache.clear();
        l = compute(rx, rz);
        cache.put(k, l);
        return l;
    }

    /** Slides of the regions around a column (a slide reaches at most ~26 blocks from its own region). */
    List<Placed> near(int x, int z) {
        if (!enabled()) return List.of();
        int rx = Math.floorDiv(x, REG), rz = Math.floorDiv(z, REG);
        List<Placed> out = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (Placed p : region(rx + dx, rz + dz)) if (p.covers(x, z)) out.add(p);
        return out;
    }

    void apply(int x, int z, B[] col) {
        for (Placed p : near(x, z)) {
            List<Edit> es = p.edits.get(key(x, z));
            if (es == null) continue;
            for (Edit e : es) {
                for (int y = e.y0; y <= e.y1; y++) {
                    if (y <= g.bottom() || y >= g.roof()) continue;
                    int i = y - g.bottom();
                    B cur = col[i];
                    switch (e.mode) {
                        case FORCE -> col[i] = e.b;
                        case CARVE -> {
                            if (cur != B.WATER && cur.mat != Material.WATER && cur != B.BEDROCK) col[i] = B.AIR;
                        }
                        default -> {
                            if (cur == B.AIR) col[i] = e.b;
                        }
                    }
                }
            }
        }
    }

    PoolroomsGenerator.Slide startingAt(int x, int y, int z) {
        for (Placed p : near(x, z)) {
            for (int[] s : p.slide.start()) if (s[0] == x && s[2] == z && y >= s[1] && y <= s[1] + 1) return p.slide;
        }
        return null;
    }

    /** Chute pieces anchored in this cell (once per cell, floor 0), plus the extras of slides that start here. */
    void decor(int cx, int cz, List<DecorSpec> out) {
        int x0 = cx * g.S, z0 = cz * g.S;
        Set<Placed> seen = new HashSet<>();
        for (int dx = 0; dx < g.S; dx += g.S - 1) for (int dz = 0; dz < g.S; dz += g.S - 1) seen.addAll(near(x0 + dx, z0 + dz));
        for (Placed p : seen) {
            int n = p.pts.length;
            int i = 0;
            while (i < n - 1) {
                if (g.cellOf((int) Math.floor(p.pts[i][0])) != cx || g.cellOf((int) Math.floor(p.pts[i][2])) != cz) {
                    i++;
                    continue;
                }
                int j = i + 1;
                while (j < n - 1 && j - i < 5 && g.cellOf((int) Math.floor(p.pts[j][0])) == cx && g.cellOf((int) Math.floor(p.pts[j][2])) == cz) j++;
                out.add(new DecorSpec(p.segmentId(i, j), p.pts[i][0], p.pts[i][1], p.pts[i][2], (float) p.yaw, 0, 0, p.seed));
                i = j;
            }
            for (DecorSpec d : p.extras) if (g.cellOf((int) Math.floor(d.x)) == cx && g.cellOf((int) Math.floor(d.z)) == cz) out.add(d);
        }
    }

    // ------------------------------------------------------------------ placement

    private List<Placed> compute(int rx, int rz) {
        List<Placed> out = new ArrayList<>();
        long h = Rng.hash(g.seed ^ 0x51DE5L, rx, rz);
        Map<Long, B[]> raw = new HashMap<>();
        double storey = g.gs().get("slide-chance", 0.0), fun = g.gs().get("fun-slide-chance", 0.0);
        for (int f = PoolroomsGenerator.MID; f <= PoolroomsGenerator.HIGH; f++) {
            if (!Rng.chance(Rng.hash(h, f), storey)) continue;
            Placed p = place(rx, rz, STOREY, f, Rng.hash(h, 100 + f), raw);
            if (p != null) out.add(p);
        }
        for (int f = PoolroomsGenerator.LOW; f <= PoolroomsGenerator.HIGH; f++) {
            if (!Rng.chance(Rng.hash(h, 10 + f), fun)) continue;
            Placed p = place(rx, rz, FUN, f, Rng.hash(h, 200 + f), raw);
            if (p != null) out.add(p);
        }
        return List.copyOf(out);
    }

    private B rawAt(Map<Long, B[]> raw, int x, int y, int z) {
        if (y <= g.bottom() || y >= g.roof()) return B.BEDROCK;
        B[] col = raw.computeIfAbsent(key(x, z), k -> g.rawColumn(x, z));
        return col[y - g.bottom()];
    }

    private boolean ordinary(int floor, int x, int z, boolean landing) {
        PoolroomsGenerator.Plan p = g.planAt(x, z);
        int cx = g.cellOf(x), cz = g.cellOf(z);
        if (cx == p.towerCx && cz == p.towerCz) return false;
        if (g.featureAt(floor, cx, cz) != null) return false;
        Zone zn = p.zone[floor];
        return zn != Zone.MASSIVE && (!landing || (zn != Zone.HALLWAY && zn != Zone.CANAL));
    }

    private Placed place(int rx, int rz, int kind, int f, long h, Map<Long, B[]> raw) {
        Zone zn = g.plan(rx, rz).zone[f];
        if (zn != Zone.BAYS && zn != Zone.DRY && zn != Zone.BRIGHT) return null;
        int to = kind == STOREY ? f - 1 : f;
        int fy = PoolroomsGenerator.floorY(f), ty = PoolroomsGenerator.floorY(to);
        for (int k = 0; k < CANDIDATES; k++) {
            int height = kind == STOREY ? 3 : 5 + Rng.pick(Rng.hash(h, k, 9), 5);
            double startY = fy + height + 1, endY = ty + 1, drop = startY - endY;
            double yaw = Rng.pick(Rng.hash(h, k, 1), 4) * 90.0;
            int bx = rx * REG + 12 + Rng.pick(Rng.hash(h, k, 2), 36), bz = rz * REG + 12 + Rng.pick(Rng.hash(h, k, 3), 36);
            SlidePath.Style st = SlidePath.Style.values()[Rng.pick(Rng.hash(h, k, 4), 3)];
            boolean wet = Rng.chance(Rng.hash(h, k, 5), 0.75);
            long sseed = Rng.hash(h, k, 6);
            SlidePath path = SlidePath.generate(sseed, st, wet, drop);
            double[][] pts = path.world(bx + 0.5, startY, bz + 0.5, yaw);
            Placed p = build(path, sseed, yaw, pts, bx, bz, f, to, fy, ty, height, kind == STOREY && Rng.chance(Rng.hash(h, k, 7), 0.4), raw);
            if (p != null) return p;
        }
        return null;
    }

    /** Local (lx, lz) cell of a slide anchored at block (bx, bz) with yaw, as a world block. */
    private static int[] cell(int bx, int bz, double yaw, int lx, int lz) {
        double[] w = SlidePath.toWorld(new double[]{lx, 0, lz}, bx + 0.5, 0, bz + 0.5, yaw);
        return new int[]{(int) Math.floor(w[0]), (int) Math.floor(w[2])};
    }

    private Placed build(SlidePath path, long sseed, double yaw, double[][] pts, int bx, int bz, int f, int to, int fy, int ty, int height,
                         boolean wantPocket, Map<Long, B[]> raw) {
        // ---- the rider's envelope and the tunnel: validate first
        Set<Long> envelope = new HashSet<>();
        Map<Long, int[]> carve = new HashMap<>(); // column -> {yMin, yMax}
        int n = pts.length;
        for (int i = 0; i < n - 1; i++) {
            for (double t : new double[]{0, 0.5}) {
                double cx = pts[i][0] + (pts[i + 1][0] - pts[i][0]) * t, cy = pts[i][1] + (pts[i + 1][1] - pts[i][1]) * t,
                        cz = pts[i][2] + (pts[i + 1][2] - pts[i][2]) * t;
                int y0 = (int) Math.floor(cy + 0.05), y1 = (int) Math.floor(cy + 2.2);
                for (int x = (int) Math.floor(cx - 1.3); x <= (int) Math.floor(cx + 1.3); x++) {
                    for (int z = (int) Math.floor(cz - 1.3); z <= (int) Math.floor(cz + 1.3); z++) {
                        if (Math.hypot(x + 0.5 - cx, z + 0.5 - cz) > 1.25) continue;
                        if (!ordinary(g.floorAt(y0), x, z, false)) return null;
                        for (int y = y0; y <= y1; y++) {
                            B b = rawAt(raw, x, y, z);
                            if (b == B.BEDROCK) return null;
                            if (b.mat == Material.WATER && i < n - 4) return null;
                            if (!b.passable() && i < n - 4 && touchesWater(raw, x, y, z)) return null;
                            envelope.add(cell3(x, y, z));
                        }
                        if (i >= 2) carve.merge(key(x, z), new int[]{y0, y1}, (a, b) -> new int[]{Math.min(a[0], b[0]), Math.max(a[1], b[1])});
                    }
                }
            }
        }
        // ---- the platform and its stair, on the start floor
        List<int[]> platform = new ArrayList<>();
        for (int lx = -1; lx <= 1; lx++) for (int lz = -2; lz <= 0; lz++) platform.add(cell(bx, bz, yaw, lx, lz));
        List<int[]> stairs = new ArrayList<>();
        for (int s = 0; s < height; s++) stairs.add(cell(bx, bz, yaw, 0, -3 - s));
        for (List<int[]> l : List.of(platform, stairs)) {
            for (int[] c : l) {
                if (!ordinary(f, c[0], c[1], true)) return null;
                if (!solidFloor(raw, c[0], fy, c[1])) return null;
                for (int y = fy + 1; y <= fy + height + 3; y++) if (!clearAir(rawAt(raw, c[0], y, c[1]))) return null;
            }
        }
        // ---- the landing pool, along the last stretch's direction, one storey down (or on the same floor)
        double ex = pts[n - 1][0] - pts[n - 2][0], ez = pts[n - 1][2] - pts[n - 2][2];
        int dx = Math.abs(ex) >= Math.abs(ez) ? (int) Math.signum(ex) : 0, dz = dx == 0 ? (int) Math.signum(ez) : 0;
        int px = -dz, pz = dx; // across the pool
        int lx0 = (int) Math.floor(pts[n - 1][0]), lz0 = (int) Math.floor(pts[n - 1][2]);
        List<int[]> pool = new ArrayList<>();
        for (int r = 0; r <= 6; r++) for (int w = -2; w <= 2; w++) pool.add(new int[]{lx0 + dx * (r - 1) + px * w, lz0 + dz * (r - 1) + pz * w, r});
        for (int[] c : pool) {
            if (!ordinary(to, c[0], c[1], true)) return null;
            if (rawAt(raw, c[0], ty, c[1]) == B.AIR) return null;
        }
        for (int r = -1; r <= 7; r++) {
            for (int w = -3; w <= 3; w++) {
                if (r >= 0 && r <= 6 && w >= -2 && w <= 2) continue;
                int x = lx0 + dx * (r - 1) + px * w, z = lz0 + dz * (r - 1) + pz * w;
                for (int y = ty - 4; y <= ty; y++) if (rawAt(raw, x, y, z) == B.AIR) return null; // the pool's sides must hold water
            }
        }
        boolean pocket = wantPocket;
        if (pocket) {
            for (int r = -1; r <= 7 && pocket; r++) {
                for (int w = -3; w <= 3 && pocket; w++) {
                    int x = lx0 + dx * (r - 1) + px * w, z = lz0 + dz * (r - 1) + pz * w;
                    if (!ordinary(to, x, z, true)) pocket = false;
                    for (int y = ty + 1; y <= ty + 7 && pocket; y++) if (!clearAir(rawAt(raw, x, y, z)) && rawAt(raw, x, y, z).mat != Material.WATER) pocket = false;
                }
            }
        }

        // ---- it fits: the ride and the edits, in the order they apply
        int[][] start = {platform.get(5), platform.get(4)}; // local (0,0) and (0,-1): index (lx + 1) * 3 + (lz + 2)
        int feet = fy + height + 1;
        double[][] ride = new double[n + 2][];
        for (int i = 0; i < n; i++) ride[i] = pts[i].clone();
        double el = Math.max(1e-6, Math.hypot(ex, ez));
        ride[n] = new double[]{pts[n - 1][0] + ex / el * 1.5, pts[n - 1][1] - 0.4, pts[n - 1][2] + ez / el * 1.5};
        ride[n + 1] = new double[]{pts[n - 1][0] + ex / el * 3.0, pts[n - 1][1] - 1.4, pts[n - 1][2] + ez / el * 3.0};
        PoolroomsGenerator.Slide slide = new PoolroomsGenerator.Slide(ride, new int[][]{{start[0][0], feet, start[0][1]}, {start[1][0], feet, start[1][1]}},
                new double[]{ex / el, 0, ez / el}, ty, path.wet, path.style.id());
        Placed out = new Placed(path, sseed, yaw, pts, pocket, f, to, slide);
        B tile = CustomBlocks.get("pool_tile"), blue = CustomBlocks.get("pool_tile_blue"), panel = CustomBlocks.get("light_panel_cool");
        // platform: solid, blue edge, rails on the sides and back
        for (int i = 0; i < platform.size(); i++) {
            int[] c = platform.get(i);
            out.add(c[0], c[1], new Edit(fy + 1, fy + height - 1, tile, FORCE));
            out.add(c[0], c[1], new Edit(fy + height, fy + height, blue, FORCE));
        }
        String up = facing(SlidePath.toWorld(new double[]{0, 0, 1}, 0, 0, 0, yaw));
        for (int s = 0; s < stairs.size(); s++) {
            int[] c = stairs.get(s);
            int top = fy + height - s;
            if (top - 1 >= fy + 1) out.add(c[0], c[1], new Edit(fy + 1, top - 1, tile, FORCE));
            out.add(c[0], c[1], new Edit(top, top, B.of(Material.QUARTZ_STAIRS, "[facing=" + up + ",half=bottom]"), FORCE));
        }
        // side rails, joined along the platform's length (the direction the slide leaves in)
        String along = up.equals("east") || up.equals("west") ? "[east=true,west=true]" : "[north=true,south=true]";
        for (int lz = -2; lz <= 0; lz++) {
            for (int lx : new int[]{-1, 1}) {
                int[] c = cell(bx, bz, yaw, lx, lz);
                out.add(c[0], c[1], new Edit(feet, feet, B.of(Material.IRON_BARS, along), FORCE));
            }
        }
        // landing pool, 4 deep, with a flight of steps out at the far end
        String outward = facing(new double[]{dx, 0, dz});
        for (int[] c : pool) {
            int r = c[2];
            out.add(c[0], c[1], new Edit(ty + 1, ty + 3, B.AIR, FORCE));
            if (r <= 4) {
                if (rawAt(raw, c[0], ty - 4, c[1]).mat != Material.WATER) out.add(c[0], c[1], new Edit(ty - 4, ty - 4, blue, FORCE));
                out.add(c[0], c[1], new Edit(ty - 3, ty, B.WATER, FORCE));
            } else {
                int stepTop = r == 5 ? ty - 1 : ty;
                out.add(c[0], c[1], new Edit(ty - 4, stepTop - 1, tile, FORCE));
                out.add(c[0], c[1], new Edit(stepTop, stepTop, B.of(Material.QUARTZ_STAIRS, "[facing=" + outward + ",half=bottom,waterlogged=true]"), FORCE));
                if (stepTop < ty) out.add(c[0], c[1], new Edit(stepTop + 1, ty, B.WATER, FORCE));
            }
        }
        out.extras.add(new DecorSpec("pool_lamp", lx0 + dx * 1 + 0.5, ty - 3 + 0.02, lz0 + dz * 1 + 0.5, 0f, 0, 0, sseed));
        // the pocket: walls, a lit roof, one door past the steps
        if (pocket) {
            for (int r = -1; r <= 7; r++) {
                for (int w = -3; w <= 3; w++) {
                    int x = lx0 + dx * (r - 1) + px * w, z = lz0 + dz * (r - 1) + pz * w;
                    boolean ring = r == -1 || r == 7 || w == -3 || w == 3;
                    if (ring) {
                        boolean door = r == 7 && (w == 0 || w == 1);
                        out.add(x, z, new Edit(ty + 1, ty + 6, tile, FORCE));
                        out.add(x, z, new Edit(ty + 3, ty + 3, blue, FORCE));
                        if (door) out.add(x, z, new Edit(ty + 1, ty + 3, B.AIR, FORCE));
                    } else {
                        out.add(x, z, new Edit(ty + 4, ty + 6, B.AIR, FORCE));
                    }
                    boolean lamp = Math.floorMod(r, 3) == 1 && Math.floorMod(w, 3) == 0;
                    out.add(x, z, new Edit(ty + 7, ty + 7, lamp ? panel : tile, FORCE));
                    if (lamp && !ring) out.add(x, z, new Edit(ty + 6, ty + 6, B.light(14), FILL_AIR));
                }
            }
            out.extras.add(new DecorSpec("floatie_ring", lx0 + dx * 2 + 0.5, ty + 1, lz0 + dz * 2 + 0.5, 0f, 0, 0, sseed ^ 7));
        }
        // the tunnel: bored through anything solid the rider would pass through
        for (Map.Entry<Long, int[]> e : carve.entrySet()) {
            int x = (int) (e.getKey() >> 32), z = (int) (long) e.getKey();
            out.add(x, z, new Edit(e.getValue()[0], e.getValue()[1], B.AIR, CARVE));
        }
        // support pillars under open stretches, lights in bored stretches
        for (int i = 2; i < n - 3; i++) {
            double[] q = pts[i];
            int x = (int) Math.floor(q[0]), z = (int) Math.floor(q[2]), top = (int) Math.floor(q[1] + 0.05) - 1;
            if (i % 6 == 2) {
                int y = top;
                while (y > g.bottom() + 1 && rawAt(raw, x, y, z) == B.AIR && !envelope.contains(cell3(x, y, z))) y--;
                int ground = y;
                B gb = rawAt(raw, x, ground, z);
                if (top - ground >= 2 && top - ground <= 24 && gb.mat != Material.WATER && !gb.passable() && !envelope.contains(cell3(x, ground, z))) {
                    out.add(x, z, new Edit(ground + 1, top, B.axis(Material.QUARTZ_PILLAR, 'y'), FILL_AIR));
                }
            }
            if (i % 4 == 0 && !rawAt(raw, x, top + 3, z).passable()) out.add(x, z, new Edit(top + 3, top + 3, B.light(13), FILL_AIR));
        }
        return out;
    }

    private boolean touchesWater(Map<Long, B[]> raw, int x, int y, int z) {
        return rawAt(raw, x + 1, y, z).mat == Material.WATER || rawAt(raw, x - 1, y, z).mat == Material.WATER
                || rawAt(raw, x, y, z + 1).mat == Material.WATER || rawAt(raw, x, y, z - 1).mat == Material.WATER
                || rawAt(raw, x, y + 1, z).mat == Material.WATER;
    }

    private boolean solidFloor(Map<Long, B[]> raw, int x, int y, int z) {
        B b = rawAt(raw, x, y, z);
        return !b.passable() && b.mat != Material.WATER && !b.state.contains("waterlogged");
    }

    private static boolean clearAir(B b) {
        return b == B.AIR || b.mat == Material.LIGHT;
    }

    private static String facing(double[] d) {
        if (Math.abs(d[0]) >= Math.abs(d[2])) return d[0] > 0 ? "east" : "west";
        return d[2] > 0 ? "south" : "north";
    }
}
