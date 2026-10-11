package dev.powderpass.core.hydro;

import dev.powderpass.core.GenSettings;
import dev.powderpass.core.geo.BaseTerrain;
import dev.powderpass.core.geo.Geology;
import dev.powderpass.core.noise.Simplex;
import dev.powderpass.core.util.BoundedCache;
import dev.powderpass.core.util.Hash;
import dev.powderpass.core.util.MathUtil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Drainage domains: the unit of canonical ownership for hydrology and erosion.
 *
 * <p>The plane is partitioned into irregular cells (a gently warped jittered Voronoi diagram, ~768 blocks). Every
 * column belongs to exactly one domain, so every eroded/hydrological value has exactly one owner that computes it.
 * Domains are linked into a drainage graph: each domain either reaches the sea, is an intentional terminal (endorheic)
 * basin, or drains into exactly one neighbour whose representative elevation is strictly lower (ties broken by id),
 * so the graph is acyclic by construction. Water crosses a domain boundary only at a canonical <em>pass</em>
 * whose position, water level and discharge are functions of the unordered domain pair alone; both domains derive
 * identical values, which is what makes rivers continuous across simulation borders without ever generating or
 * reading neighbouring chunks.
 *
 * <p>Long-distance routing is bounded: the outlet level of a domain depends on its downstream chain (to the sea or
 * a terminal basin), and upstream discharge is accumulated by a deterministic breadth-first search capped at
 * {@link #MAX_UPSTREAM} domains. Beyond that cap discharge saturates; the cap is part of the generator definition,
 * so results never depend on query order.
 */
public final class Domains {
    public static final double SPACING = 768;
    public static final int COARSE = 48;
    public static final int MAX_UPSTREAM = 96;
    static final double WARP_AMP = 130, WARP_LAMBDA = 1200, WARP2_AMP = 22, WARP2_LAMBDA = 300;

    private final BaseTerrain base;
    private final GenSettings s;
    private final long seed, warpSeed;
    private final int sea;
    private final BoundedCache<Info> infos;
    private final BoundedCache<Flow> flows;
    private final BoundedCache<Pass> passes;
    private final BoundedCache<double[]> discharge;
    private final ThreadLocal<Geology.GeoSample> tl = ThreadLocal.withInitial(Geology.GeoSample::new);

    public Domains(long worldSeed, GenSettings s, BaseTerrain base, int coarseCacheEntries) {
        this.base = base;
        this.s = s;
        this.sea = s.seaLevel();
        this.seed = Hash.subSeed(worldSeed, "domains.seeds");
        this.warpSeed = Hash.subSeed(worldSeed, "domains.warp");
        infos = BoundedCache.ofCount(coarseCacheEntries);
        flows = BoundedCache.ofCount(coarseCacheEntries);
        passes = BoundedCache.ofCount(coarseCacheEntries * 2L);
        discharge = BoundedCache.ofCount(coarseCacheEntries);
    }

    // ------------------------------------------------------------------------------------------------ ownership

    public double seedX(int gx, int gz) {
        return (gx + 0.15 + 0.7 * Hash.unit(seed, gx, gz, 1)) * SPACING;
    }

    public double seedZ(int gx, int gz) {
        return (gz + 0.15 + 0.7 * Hash.unit(seed, gx, gz, 2)) * SPACING;
    }

    double warpX(double x, double z) {
        return x + WARP_AMP * Simplex.noise2(warpSeed, x / WARP_LAMBDA, z / WARP_LAMBDA)
                + WARP2_AMP * Simplex.noise2(warpSeed + 2, x / WARP2_LAMBDA, z / WARP2_LAMBDA);
    }

    double warpZ(double x, double z) {
        return z + WARP_AMP * Simplex.noise2(warpSeed + 1, x / WARP_LAMBDA, z / WARP_LAMBDA)
                + WARP2_AMP * Simplex.noise2(warpSeed + 3, x / WARP2_LAMBDA, z / WARP2_LAMBDA);
    }

    /** Mutable ownership result. */
    public static final class Owner {
        public long id, id2;
        /** Approximate distance to the boundary with id2 (blocks); 0 exactly on the boundary on both sides. */
        public double bd;
    }

    public long owner(double x, double z) {
        double wx = warpX(x, z), wz = warpZ(x, z);
        int cx = MathUtil.fastFloor(wx / SPACING), cz = MathUtil.fastFloor(wz / SPACING);
        double best = Double.MAX_VALUE;
        long id = 0;
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                int gx = cx + dx, gz = cz + dz;
                double sx = seedX(gx, gz) - wx, sz = seedZ(gx, gz) - wz;
                double d = sx * sx + sz * sz;
                if (d < best || (d == best && Hash.pack(gx, gz) < id)) { best = d; id = Hash.pack(gx, gz); }
            }
        }
        return id;
    }

    public void owner(double x, double z, Owner out) {
        double wx = warpX(x, z), wz = warpZ(x, z);
        int cx = MathUtil.fastFloor(wx / SPACING), cz = MathUtil.fastFloor(wz / SPACING);
        double b1 = Double.MAX_VALUE, b2 = Double.MAX_VALUE;
        long i1 = 0, i2 = 0;
        double p1x = 0, p1z = 0, p2x = 0, p2z = 0;
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                int gx = cx + dx, gz = cz + dz;
                double sx = seedX(gx, gz), sz = seedZ(gx, gz);
                double d = (sx - wx) * (sx - wx) + (sz - wz) * (sz - wz);
                long id = Hash.pack(gx, gz);
                if (d < b1 || (d == b1 && id < i1)) {
                    b2 = b1; i2 = i1; p2x = p1x; p2z = p1z;
                    b1 = d; i1 = id; p1x = sx; p1z = sz;
                } else if (d < b2 || (d == b2 && id < i2)) {
                    b2 = d; i2 = id; p2x = sx; p2z = sz;
                }
            }
        }
        out.id = i1;
        out.id2 = i2;
        out.bd = (b2 - b1) / (2.0 * Math.hypot(p2x - p1x, p2z - p1z));
    }

    // ------------------------------------------------------------------------------------------------ coarse info

    /** Coarse per-domain statistics (pure function of the domain id). */
    public static final class Info {
        public long id;
        public int gx, gz;
        public double minX, minZ, maxX, maxZ;  // real-space bounding box (block coords)
        public double area;                    // blocks^2
        public double minH, lowX, lowZ;        // lowest coarse sample
        public double meanH, precip;
        public int seaSamples;
        public double seaX, seaZ;
        public boolean coastal;
        public double E;                       // representative elevation used for graph ordering
    }

    public Info info(long id) {
        return infos.getOrCompute(id, this::computeInfo);
    }

    private Info computeInfo(long id) {
        int gx = Hash.unpackX(id), gz = Hash.unpackZ(id);
        Info in = new Info();
        in.id = id;
        in.gx = gx;
        in.gz = gz;
        double cx = seedX(gx, gz), cz = seedZ(gx, gz);
        double reach = 1.7 * SPACING;
        int i0 = MathUtil.fastFloor((cx - reach) / COARSE), i1 = MathUtil.fastFloor((cx + reach) / COARSE);
        int j0 = MathUtil.fastFloor((cz - reach) / COARSE), j1 = MathUtil.fastFloor((cz + reach) / COARSE);
        double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        int count = 0;
        double sumH = 0, sumP = 0, minH = Double.MAX_VALUE, lowX = cx, lowZ = cz;
        double seaBest = Double.MAX_VALUE;
        Geology.GeoSample o = tl.get();
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) {
                double x = (i + 0.5) * COARSE, z = (j + 0.5) * COARSE;
                if (owner(x, z) != id) continue;
                count++;
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
                base.sample(x, z, o);
                double h = o.h0;
                sumH += h;
                sumP += base.climate().precipitation(x, z);
                if (h < minH || (h == minH && (x < lowX || (x == lowX && z < lowZ)))) { minH = h; lowX = x; lowZ = z; }
                if (o.sea) {
                    in.seaSamples++;
                    double d = (x - cx) * (x - cx) + (z - cz) * (z - cz);
                    if (d < seaBest) { seaBest = d; in.seaX = x; in.seaZ = z; }
                }
            }
        }
        if (count == 0) {
            // Degenerate (fully squeezed) cell: give it a tiny box around the seed so callers stay robust.
            minX = maxX = cx; minZ = maxZ = cz; minH = sea; lowX = cx; lowZ = cz; count = 1; sumH = sea; sumP = 0.5;
        }
        in.minX = minX - COARSE; in.maxX = maxX + COARSE; in.minZ = minZ - COARSE; in.maxZ = maxZ + COARSE;
        in.area = count * (double) COARSE * COARSE;
        in.minH = minH; in.lowX = lowX; in.lowZ = lowZ;
        in.meanH = sumH / count;
        in.precip = sumP / count;
        in.coastal = in.seaSamples >= 2;
        in.E = in.coastal ? sea - 1 : minH;
        return in;
    }

    // ------------------------------------------------------------------------------------------------ passes

    /** Canonical crossing point between two adjacent domains. */
    public static final class Pass {
        public final long lo, hi;
        public final double x, z;   // a point on the shared boundary
        public final double h0;     // base elevation at the pass
        public final double tx, tz; // unit direction lo -> hi
        Pass(long lo, long hi, double x, double z, double h0, double tx, double tz) {
            this.lo = lo; this.hi = hi; this.x = x; this.z = z; this.h0 = h0; this.tx = tx; this.tz = tz;
        }
    }

    private static final Pass NONE = new Pass(0, 0, 0, 0, 0, 0, 0);

    /** Pass between a and b, or null when they do not share a boundary. Symmetric in (a,b). */
    public Pass pass(long a, long b) {
        long lo = Math.min(a, b), hi = Math.max(a, b);
        long key = Hash.hash(0x9A55L, lo, hi);
        Pass p = passes.getOrCompute(key, k -> computePass(lo, hi));
        return p == NONE ? null : p;
    }

    private Pass computePass(long lo, long hi) {
        double ax = seedX(Hash.unpackX(lo), Hash.unpackZ(lo)), az = seedZ(Hash.unpackX(lo), Hash.unpackZ(lo));
        double bx = seedX(Hash.unpackX(hi), Hash.unpackZ(hi)), bz = seedZ(Hash.unpackX(hi), Hash.unpackZ(hi));
        double dx = bx - ax, dz = bz - az;
        double len = Math.hypot(dx, dz);
        double tx = dx / len, tz = dz / len;
        double px = -tz, pz = tx;
        Geology.GeoSample o = tl.get();
        double bestH = Double.MAX_VALUE, bestX = 0, bestZ = 0;
        boolean found = false;
        for (int k = -4; k <= 4; k++) {
            double off = k * 0.07 * SPACING;
            double sx = ax + px * off, sz = az + pz * off, ex = bx + px * off, ez = bz + pz * off;
            if (owner(sx, sz) != lo || owner(ex, ez) != hi) continue;
            double t0 = 0, t1 = 1;
            for (int it = 0; it < 22; it++) {
                double tm = (t0 + t1) * 0.5;
                if (owner(sx + (ex - sx) * tm, sz + (ez - sz) * tm) == lo) t0 = tm; else t1 = tm;
            }
            double qx0 = sx + (ex - sx) * t0, qz0 = sz + (ez - sz) * t0;
            double qx1 = sx + (ex - sx) * t1, qz1 = sz + (ez - sz) * t1;
            if (owner(qx1, qz1) != hi) continue; // boundary is with a third domain here
            double qx = (qx0 + qx1) * 0.5, qz = (qz0 + qz1) * 0.5;
            base.sample(qx, qz, o);
            double h = o.h0;
            if (h < bestH) { bestH = h; bestX = qx; bestZ = qz; found = true; }
        }
        if (!found) return NONE;
        return new Pass(lo, hi, bestX, bestZ, bestH, tx, tz);
    }

    // ------------------------------------------------------------------------------------------------ flow graph

    public enum FlowType { SEA, BASIN, DOWN }

    public static final class Inflow {
        public final long from;
        public final Pass pass;
        public final int level;
        public final double q;
        Inflow(long from, Pass pass, int level, double q) { this.from = from; this.pass = pass; this.level = level; this.q = q; }
    }

    /** Downstream relation and outlet level of a domain. */
    public static final class Flow {
        public long id;
        public FlowType type;
        public long down;
        public Pass outPass;
        public int level;        // outlet water level (sea level, basin estimate, or pass level)
        public double outX, outZ;
    }

    private long[] neighbours(long id) {
        int gx = Hash.unpackX(id), gz = Hash.unpackZ(id);
        long[] n = new long[8];
        int k = 0;
        for (int dz = -1; dz <= 1; dz++)
            for (int dx = -1; dx <= 1; dx++)
                if (dx != 0 || dz != 0) n[k++] = Hash.pack(gx + dx, gz + dz);
        return n;
    }

    /** Strict total order on (E, id): guarantees an acyclic graph. */
    private static boolean lower(Info a, Info b) {
        return a.E < b.E || (a.E == b.E && a.id < b.id);
    }

    public Flow flow(long id) {
        return flows.getOrCompute(id, this::computeFlow);
    }

    private Flow computeFlow(long id) {
        Info in = info(id);
        Flow f = new Flow();
        f.id = id;
        if (in.coastal) {
            f.type = FlowType.SEA;
            f.level = sea;
            f.outX = in.seaX;
            f.outZ = in.seaZ;
            return f;
        }
        long[] nb = neighbours(id);
        Info[] ni = new Info[nb.length];
        for (int i = 0; i < nb.length; i++) ni[i] = info(nb[i]);
        Integer[] order = new Integer[nb.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, (p, q) -> lower(ni[p], ni[q]) ? -1 : (lower(ni[q], ni[p]) ? 1 : 0));
        for (int oi : order) {
            Info n = ni[oi];
            if (!lower(n, in)) break;
            Pass p = pass(id, n.id);
            if (p == null) continue;
            f.type = FlowType.DOWN;
            f.down = n.id;
            f.outPass = p;
            f.outX = p.x;
            f.outZ = p.z;
            Flow df = flow(n.id); // recursion strictly downhill in (E,id): terminates
            double dist = Math.hypot(p.x - df.outX, p.z - df.outZ);
            double zmin = df.level + 0.0004 * dist;
            if (df.type == FlowType.SEA && p.h0 <= sea + 1) zmin = sea;
            double est = p.h0 - 0.45 * (p.h0 - df.level);
            double dLow = Math.hypot(p.x - in.lowX, p.z - in.lowZ);
            est = Math.min(est, in.minH + 1 + 0.0008 * dLow);
            f.level = (int) Math.floor(Math.max(zmin, est));
            return f;
        }
        f.type = FlowType.BASIN;
        f.level = (int) Math.floor(in.minH);
        f.outX = in.lowX;
        f.outZ = in.lowZ;
        return f;
    }

    /** Own runoff contribution (blocks^2 x precipitation index). */
    public double ownRunoff(long id) {
        Info in = info(id);
        return in.area * in.precip;
    }

    /**
     * Discharge leaving domain id through its outlet: own runoff plus everything upstream, found by a deterministic
     * BFS capped at {@link #MAX_UPSTREAM} domains. Returns {total, visited}.
     */
    public double outflowDischarge(long id) {
        return discharge.getOrCompute(id, this::computeDischarge)[0];
    }

    private double[] computeDischarge(long id) {
        ArrayDeque<Long> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        queue.add(id);
        seen.add(id);
        double total = 0;
        int visited = 0;
        while (!queue.isEmpty() && visited < MAX_UPSTREAM) {
            long cur = queue.poll();
            visited++;
            total += ownRunoff(cur);
            long[] nb = neighbours(cur);
            Arrays.sort(nb);
            for (long n : nb) {
                if (seen.contains(n)) continue;
                Flow nf = flow(n);
                if (nf.type == FlowType.DOWN && nf.down == cur) {
                    seen.add(n);
                    queue.add(n);
                }
            }
        }
        return new double[]{total, visited};
    }

    /** Domains draining directly into id, in canonical order. */
    public List<Inflow> inflows(long id) {
        List<Inflow> out = new ArrayList<>();
        long[] nb = neighbours(id);
        Arrays.sort(nb);
        for (long n : nb) {
            Flow nf = flow(n);
            if (nf.type == FlowType.DOWN && nf.down == id) {
                out.add(new Inflow(n, nf.outPass, nf.level, outflowDischarge(n)));
            }
        }
        return out;
    }

    /** Callback for {@link #forEachPassNear}. */
    public interface PassVisitor {
        void visit(Flow upstream, double dist);
    }

    /**
     * Visits every active pass (upstream domain -> its downstream neighbour) within radius of (x,z). The result is a
     * function of position only, so the simulation and the sampler see exactly the same passes everywhere.
     */
    public void forEachPassNear(double x, double z, double radius, PassVisitor v) {
        double wx = warpX(x, z), wz = warpZ(x, z);
        int cx = MathUtil.fastFloor(wx / SPACING), cz = MathUtil.fastFloor(wz / SPACING);
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                long id = Hash.pack(cx + dx, cz + dz);
                // Cheap pre-check on seed distance: a pass lies within ~1.3 spacings of its domain seed.
                double sx = seedX(cx + dx, cz + dz) - wx, sz = seedZ(cx + dx, cz + dz) - wz;
                if (sx * sx + sz * sz > MathUtil.sq(1.6 * SPACING + radius)) continue;
                Flow f = flow(id);
                if (f.type != FlowType.DOWN) continue;
                double d = Math.hypot(f.outPass.x - x, f.outPass.z - z);
                if (d <= radius) v.visit(f, d);
            }
        }
    }

    public BaseTerrain base() {
        return base;
    }

    public void setCacheLimit(long entries) {
        infos.setMaxWeight(entries);
        flows.setMaxWeight(entries);
        passes.setMaxWeight(entries * 2);
        discharge.setMaxWeight(entries);
    }

    public String cacheStats() {
        return String.format("info=%d flow=%d pass=%d q=%d", infos.size(), flows.size(), passes.size(), discharge.size());
    }
}
