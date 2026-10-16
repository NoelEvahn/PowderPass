package com.powderpass.backrooms.gen;

import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.util.Rng;
import com.powderpass.backrooms.util.ValueNoise;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base class for the level generators. Everything here is a pure, thread-safe function of the
 * seed and coordinates so Paper may call {@link #paint} concurrently from worker threads.
 *
 * <p>Space is a grid of square cells ({@link #S} blocks, wall lines on each cell's west/north edge),
 * grouped into regions ({@link #R} cells, the unit of maze connectivity) and districts
 * ({@link #D} regions, the unit of authored feature placement: every district receives its
 * guaranteed transitions, so exits are always within a few hundred blocks).</p>
 */
public abstract class LevelGenerator {
    public final Level level;
    public final long seed;
    protected final GenSettings gs;
    public final int S;
    public final int R;
    public final int D;
    public final int[] floorY;
    public final int bottomY;
    public final int roofY;
    protected final GridMaze[] mazes;
    private final List<FeatureDef> defs;
    private final Map<Long, List<Feature>> districtCache = new ConcurrentHashMap<>();

    @SuppressWarnings("this-escape")
    protected LevelGenerator(Level level, long seed, GenSettings gs, int cellSize, int regionCells, int districtRegions,
                             int[] floorY, int bottomY, int roofY, double loopChance, double echoChance) {
        this.level = level;
        this.seed = seed;
        this.gs = gs;
        this.S = cellSize;
        this.R = regionCells;
        this.D = districtRegions;
        this.floorY = floorY;
        this.bottomY = bottomY;
        this.roofY = roofY;
        this.mazes = new GridMaze[floorY.length];
        for (int f = 0; f < floorY.length; f++) {
            mazes[f] = new GridMaze(Rng.hash(seed, 0xA11CE + f), regionCells, gs.get("loop-chance", loopChance), echoChance);
        }
        this.defs = List.copyOf(featureDefs());
    }

    // ------------------------------------------------------------------ abstract surface

    /** Authored features for this level; called once from the constructor (must not use subclass fields). */
    protected abstract List<FeatureDef> featureDefs();

    protected abstract void paintCell(CellCtx c);

    /** Decorations owned by a cell. Must be deterministic. */
    protected abstract void cellDecor(int floor, int cx, int cz, List<DecorSpec> out);

    /** Trigger volume of a feature: {u0, dy0, v0, u1, dy1, v1} inclusive, y relative to the floor's feet level. */
    protected abstract int[] triggerBox(Feature f);

    /** True when the centre of the cell is guaranteed to be a dry, open, standable spot. */
    public abstract boolean isSafeCell(int floor, int cx, int cz);

    /** Human-readable zone name for debug output. */
    public abstract String zoneName(int floor, int cx, int cz);

    /** Biome key that gives this level its ambient particles/water colour. */
    public abstract String biomeKey();

    /** Cell-local {u, v} of the guaranteed-standable spot of a safe cell. */
    public int[] safeLocal(int floor, int cx, int cz) {
        return new int[]{S / 2, S / 2};
    }

    /**
     * A light fixture: a display-model lamp (the only visible light source anywhere in the Backrooms) plus an
     * invisible light block that does the actual lighting. {@code state}: 0 lit, 1 flickering, 2 dead.
     */
    protected static DecorSpec lamp(String model, double x, double y, double z, float yaw, long seed, int state,
                                    int lx, int ly, int lz, int level) {
        String id = state == 2 ? model + ":dead" : state == 1 ? model + ":flicker" : model;
        DecorSpec d = new DecorSpec(id, x, y, z, yaw, 0, 0, seed);
        if (state != 2) d.light(lx, ly, lz, level);
        return d;
    }

    /** Lit / flickering / dead roll for a lamp. */
    protected static int lampState(long h, double lit) {
        double r = Rng.unit(h);
        if (r >= lit) return 2;
        return r < lit * 0.06 ? 1 : 0;
    }

    /** Areas where creatures never spawn or path (safe rooms). */
    public boolean isSafeZone(int x, int y, int z) {
        return false;
    }

    /** Loot quality of a generated container at this position: 0 none, 1 sparse, 2 cache. */
    public int lootTier(int x, int y, int z) {
        return 1;
    }

    /** Merged regions get all internal edges fully open (halls, warehouses, ballrooms...). */
    protected boolean mergedRegion(int floor, int rx, int rz) {
        return false;
    }

    /** Door shape parameters: {fullChance, minWidth, maxWidth}. */
    protected double[] doorParams(int floor) {
        return new double[]{0.3, 1, 3};
    }

    // ------------------------------------------------------------------ cell / edge queries

    public int cellOf(int w) {
        return Math.floorDiv(w, S);
    }

    public int localOf(int w) {
        return Math.floorMod(w, S);
    }

    public int floorCount() {
        return floorY.length;
    }

    /** Floor index for a feet y (anything below the lowest floor counts as floor 0, e.g. stair pits). */
    public int floorAt(int y) {
        for (int f = floorY.length - 1; f >= 0; f--) {
            if (y > floorY[f] - 2) {
                return f;
            }
        }
        return 0;
    }

    public boolean merged(int floor, int cx, int cz) {
        return mergedRegion(floor, Math.floorDiv(cx, R), Math.floorDiv(cz, R));
    }

    private boolean internalMerged(int floor, int ax, int az, int bx, int bz) {
        int rax = Math.floorDiv(ax, R), raz = Math.floorDiv(az, R);
        int rbx = Math.floorDiv(bx, R), rbz = Math.floorDiv(bz, R);
        return rax == rbx && raz == rbz && mergedRegion(floor, rax, raz);
    }

    /** Edge between (cx,cz) and its east (east=true) or south neighbour. */
    public boolean edgeOpen(int floor, int cx, int cz, boolean east) {
        if (east ? internalMerged(floor, cx, cz, cx + 1, cz) : internalMerged(floor, cx, cz, cx, cz + 1)) {
            return true;
        }
        return east ? mazes[floor].east(cx, cz) : mazes[floor].south(cx, cz);
    }

    public Door door(int floor, int cx, int cz, boolean east) {
        if (!edgeOpen(floor, cx, cz, east)) {
            return Door.CLOSED;
        }
        boolean mergedInternal = east ? internalMerged(floor, cx, cz, cx + 1, cz) : internalMerged(floor, cx, cz, cx, cz + 1);
        if (mergedInternal) {
            return new Door(true, 1, S - 1, true);
        }
        double[] p = doorParams(floor);
        long h = Rng.hash(seed ^ 0xD00L, cx, cz, (east ? 1 : 0) + floor * 2L);
        if (Rng.chance(h, p[0])) {
            return new Door(true, 1, S - 1, true);
        }
        int w = Rng.range(Rng.hash(h, 1), (int) p[1], (int) p[2]);
        w = Math.min(w, S - 1);
        int start = Rng.range(Rng.hash(h, 2), 1, S - w);
        return new Door(true, start, w, false);
    }

    /** West wall line of cell (cx,cz). */
    public Door west(int floor, int cx, int cz) {
        return door(floor, cx - 1, cz, true);
    }

    /** North wall line of cell (cx,cz). */
    public Door north(int floor, int cx, int cz) {
        return door(floor, cx, cz - 1, false);
    }

    public Door east(int floor, int cx, int cz) {
        return door(floor, cx, cz, true);
    }

    public Door south(int floor, int cx, int cz) {
        return door(floor, cx, cz, false);
    }

    /** True when all four wall segments meeting at the cell's origin corner are fully open. */
    public boolean cornerOpen(int floor, int cx, int cz) {
        return west(floor, cx, cz).full() && west(floor, cx, cz - 1).full()
                && north(floor, cx, cz).full() && north(floor, cx - 1, cz).full();
    }

    public int degree(int floor, int cx, int cz) {
        int d = 0;
        if (edgeOpen(floor, cx, cz, true)) d++;
        if (edgeOpen(floor, cx - 1, cz, true)) d++;
        if (edgeOpen(floor, cx, cz, false)) d++;
        if (edgeOpen(floor, cx, cz - 1, false)) d++;
        return d;
    }

    /** Cell hash with a salt; the basis of every per-cell decision. */
    public long ch(int floor, int cx, int cz, long salt) {
        return Rng.hash(seed ^ salt, cx, cz, floor);
    }

    /** Smooth zone noise sampled at the cell centre so zone borders always fall on cell borders. */
    public double zone(int cx, int cz, long salt, double scaleBlocks) {
        return ValueNoise.fractal(seed ^ salt, cx * S + S / 2.0, cz * S + S / 2.0, scaleBlocks);
    }

    // ------------------------------------------------------------------ features

    public List<Feature> districtFeatures(int dx, int dz) {
        long key = ((long) dx << 32) ^ (dz & 0xffffffffL);
        List<Feature> cached = districtCache.get(key);
        if (cached != null) {
            return cached;
        }
        if (districtCache.size() > 2048) {
            districtCache.clear();
        }
        long h = Rng.hash(seed ^ 0xFEA7L, dx, dz);
        int slots = D * D;
        int[] order = new int[slots];
        for (int i = 0; i < slots; i++) order[i] = i;
        for (int i = slots - 1; i > 0; i--) {
            int j = Rng.pick(Rng.hash(h, 99, i), i + 1);
            int t = order[i];
            order[i] = order[j];
            order[j] = t;
        }
        List<Feature> out = new ArrayList<>();
        int slot = 0;
        for (int i = 0; i < defs.size() && slot < slots; i++) {
            FeatureDef def = defs.get(i);
            if (!Rng.chance(Rng.hash(h, i, 1), def.chance())) {
                continue;
            }
            int ri = order[slot++];
            int rx = dx * D + ri % D;
            int rz = dz * D + ri / D;
            int cx = rx * R + Rng.range(Rng.hash(h, i, 2), 1, R - 2);
            int cz = rz * R + Rng.range(Rng.hash(h, i, 3), 1, R - 2);
            int floor = def.floor() >= 0 ? Math.min(def.floor(), floorY.length - 1) : Rng.pick(Rng.hash(h, i, 4), floorY.length);
            out.add(new Feature(def, floor, cx, cz, Rng.pick(Rng.hash(h, i, 5), 4), Rng.hash(h, i, 6)));
        }
        List<Feature> result = List.copyOf(out);
        districtCache.put(key, result);
        return result;
    }

    public Feature featureAt(int floor, int cx, int cz) {
        int dx = Math.floorDiv(Math.floorDiv(cx, R), D);
        int dz = Math.floorDiv(Math.floorDiv(cz, R), D);
        for (Feature f : districtFeatures(dx, dz)) {
            if (f.cx() == cx && f.cz() == cz && f.floor() == floor) {
                return f;
            }
        }
        return null;
    }

    /** Features (any floor) whose cell centre lies within {@code radius} blocks of (x,z). */
    public List<Feature> featuresNear(int x, int z, int radius) {
        List<Feature> out = new ArrayList<>();
        int span = S * R * D;
        int dx0 = Math.floorDiv(x - radius, span), dx1 = Math.floorDiv(x + radius, span);
        int dz0 = Math.floorDiv(z - radius, span), dz1 = Math.floorDiv(z + radius, span);
        for (int dx = dx0; dx <= dx1; dx++) {
            for (int dz = dz0; dz <= dz1; dz++) {
                for (Feature f : districtFeatures(dx, dz)) {
                    double fx = f.cx() * S + S / 2.0, fz = f.cz() * S + S / 2.0;
                    if ((fx - x) * (fx - x) + (fz - z) * (fz - z) <= (double) radius * radius) {
                        out.add(f);
                    }
                }
            }
        }
        return out;
    }

    /** World-space centre of a feature cell at feet level. */
    public double[] featureCenter(Feature f) {
        return new double[]{f.cx() * S + S / 2.0, floorY[f.floor()] + 1, f.cz() * S + S / 2.0};
    }

    /** The feature whose trigger volume contains the block position, or null. */
    public Feature triggerAt(int x, int y, int z) {
        int cx = cellOf(x), cz = cellOf(z);
        for (int f = 0; f < floorY.length; f++) {
            Feature feat = featureAt(f, cx, cz);
            if (feat == null || (feat.dest() == null && feat.def().effect() == null)) {
                continue;
            }
            int[] b = triggerBox(feat);
            if (b == null) {
                continue;
            }
            int u = localOf(x), v = localOf(z), dy = y - (floorY[f] + 1);
            if (u >= b[0] && u <= b[3] && v >= b[2] && v <= b[5] && dy >= b[1] && dy <= b[4]) {
                return feat;
            }
        }
        return null;
    }

    /**
     * Nearest guaranteed-safe arrival spot (feet block coordinates) to (x,z), spiralling outward over
     * cells. Results are validated against real blocks again at teleport time.
     */
    public int[] safeSpot(int x, int z, int preferredFloor) {
        int floor = Math.max(0, Math.min(preferredFloor, floorY.length - 1));
        int ccx = cellOf(x), ccz = cellOf(z);
        for (int r = 0; r <= 24; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int cx = ccx + dx, cz = ccz + dz;
                    if (featureAt(floor, cx, cz) == null && isSafeCell(floor, cx, cz)) {
                        int[] l = safeLocal(floor, cx, cz);
                        return new int[]{cx * S + l[0], floorY[floor] + 1, cz * S + l[1]};
                    }
                }
            }
        }
        return new int[]{ccx * S + S / 2, floorY[floor] + 1, ccz * S + S / 2};
    }

    // ------------------------------------------------------------------ painting

    public final void paint(Canvas canvas, int chunkX, int chunkZ) {
        int minX = chunkX << 4, minZ = chunkZ << 4;
        int cx0 = cellOf(minX), cx1 = cellOf(minX + 15);
        int cz0 = cellOf(minZ), cz1 = cellOf(minZ + 15);
        for (int f = 0; f < floorY.length; f++) {
            for (int cx = cx0; cx <= cx1; cx++) {
                for (int cz = cz0; cz <= cz1; cz++) {
                    paintCell(new CellCtx(this, canvas, f, cx, cz, minX, minZ));
                }
            }
        }
        List<DecorSpec> decor = new ArrayList<>();
        for (int f = 0; f < floorY.length; f++) {
            for (int cx = cx0 - 1; cx <= cx1 + 1; cx++) {
                for (int cz = cz0 - 1; cz <= cz1 + 1; cz++) {
                    decor.clear();
                    cellDecor(f, cx, cz, decor);
                    for (DecorSpec d : decor) {
                        applyDecorBlocks(canvas, d, minX, minZ);
                    }
                }
            }
        }
    }

    private static void applyDecorBlocks(Canvas canvas, DecorSpec d, int minX, int minZ) {
        for (int i = 0; i + 2 < d.barriers.length; i += 3) {
            int lx = d.barriers[i] - minX, lz = d.barriers[i + 2] - minZ;
            if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16 && canvas.type(lx, d.barriers[i + 1], lz) == Material.AIR) {
                canvas.set(lx, d.barriers[i + 1], lz, B.BARRIER);
            }
        }
        for (int i = 0; i + 3 < d.lights.length; i += 4) {
            int lx = d.lights[i] - minX, lz = d.lights[i + 2] - minZ;
            if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16 && canvas.type(lx, d.lights[i + 1], lz) == Material.AIR) {
                canvas.set(lx, d.lights[i + 1], lz, B.light(d.lights[i + 3]));
            }
        }
    }

    /** Decorations whose anchor block lies inside the chunk. */
    public List<DecorSpec> decorations(int chunkX, int chunkZ) {
        int minX = chunkX << 4, minZ = chunkZ << 4;
        List<DecorSpec> all = new ArrayList<>();
        List<DecorSpec> out = new ArrayList<>();
        for (int f = 0; f < floorY.length; f++) {
            for (int cx = cellOf(minX) - 1; cx <= cellOf(minX + 15) + 1; cx++) {
                for (int cz = cellOf(minZ) - 1; cz <= cellOf(minZ + 15) + 1; cz++) {
                    all.clear();
                    cellDecor(f, cx, cz, all);
                    cellDetails(f, cx, cz, all);
                    for (DecorSpec d : all) {
                        int bx = (int) Math.floor(d.x), bz = (int) Math.floor(d.z);
                        if (bx >= minX && bx < minX + 16 && bz >= minZ && bz < minZ + 16) {
                            out.add(d);
                        }
                    }
                }
            }
        }
        // functional decorations first, then details by tier and rank, so budgets always drop details first
        out.sort(java.util.Comparator.comparingInt((DecorSpec d) -> d.tier).thenComparingDouble(d -> d.rank));
        return out;
    }

    /**
     * Surface details for one cell (thin display overlays: trims, seams, stains, pipes...). Pure and deterministic
     * like {@link #cellDecor}; the runtime filters them by tier and rank. Default: none.
     */
    protected void cellDetails(int floor, int cx, int cz, List<DecorSpec> out) {
    }

    /** Builds a detail spec with a deterministic rank. */
    protected static DecorSpec detail(int tier, long h, String model, double x, double y, double z, float yaw, float pitch) {
        return new DecorSpec(model, x, y, z, yaw, pitch, 0f, h).tier(tier, Rng.unit(Rng.hash(h, 0x7A11)));
    }

    /**
     * Localised deterioration in [0,1]: smooth noise so damage clusters in patches instead of being sprinkled
     * evenly. Multiply chances by this.
     */
    protected double wear(int cx, int cz, long salt) {
        double n = zone(cx, cz, salt, S * 6.0);
        return Math.max(0, Math.min(1, (n - 0.45) / 0.4));
    }

    // ------------------------------------------------------------------ shared painting helpers

    /** Solid shell for a column: bedrock bottom, fill up to floor, air, ceiling, filler, bedrock roof. */
    protected void shell(CellCtx c, int u, int v, int bottom, B under, B floor, int ceilY, B ceil, B above, int top) {
        if (!c.in(u, v)) {
            return;
        }
        c.set(u, bottom, v, B.BEDROCK);
        for (int y = bottom + 1; y < c.fy; y++) c.set(u, y, v, under);
        c.set(u, c.fy, v, floor);
        for (int y = c.fy + 1; y < ceilY; y++) c.set(u, y, v, B.AIR);
        c.set(u, ceilY, v, ceil);
        for (int y = ceilY + 1; y < top; y++) c.set(u, y, v, above);
        c.set(u, top, v, B.BEDROCK);
    }

    /** Deterministic per-cell decoration RNG helper. */
    protected static double u(long h) {
        return Rng.unit(h);
    }

    /** Converts a cell-local position to a world-space decoration anchor. */
    protected DecorSpec spec(String model, int cx, int cz, double u, double y, double v, float yaw, long seed) {
        return new DecorSpec(model, cx * S + u, y, cz * S + v, yaw, 0, 0, seed);
    }

    /** Yaw (degrees) facing away from a wall: 0=west wall(face +x), 1=north(+z), 2=east(-x), 3=south(-z). */
    protected static float yawAwayFrom(int side) {
        return switch (side) {
            case 0 -> -90f;  // facing +X
            case 1 -> 0f;    // facing +Z
            case 2 -> 90f;   // facing -X
            default -> 180f; // facing -Z
        };
    }
}
