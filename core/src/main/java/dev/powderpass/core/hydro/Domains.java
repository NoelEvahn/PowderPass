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
    public static final int COARSE = 64;
    public static final int MAX_UPSTREAM = 96;
    public static final double MAX_GAP = 24;
    static final double WARP_AMP = 130, WARP_LAMBDA = 1200, WARP2_AMP = 22, WARP2_LAMBDA = 300;

    private final BaseTerrain base;
    private final GenSettings s;
    private final long seed, warpSeed;
    private final int sea;
    private final BoundedCache<Info> infos;
    private final BoundedCache<Flow> flows;
    private final BoundedCache<Pass> passes;
    private final BoundedCache<double[]> discharge;
    private final BoundedCache<double[]> keys;
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
        keys = BoundedCache.ofCount(coarseCacheEntries);
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
        public double regional;                // smoothed regional elevation around the domain
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
        double reach = 1.4 * SPACING;
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
                // Intentional sinks and anomaly overlays (craters, pits, flats) are local features: they must not
                // redefine the regional drainage graph, so they are excluded from the representative minimum.
                boolean overlaySink = !Double.isNaN(o.sinkLevel) || o.overlay >= 100 || !Double.isNaN(o.fixedLevel);
                if (!overlaySink && (h < minH || (h == minH && (x < lowX || (x == lowX && z < lowZ))))) { minH = h; lowX = x; lowZ = z; }
                if (o.sea) {
                    in.seaSamples++;
                    double d = (x - cx) * (x - cx) + (z - cz) * (z - cz);
                    if (d < seaBest) { seaBest = d; in.seaX = x; in.seaZ = z; }
                }
            }
        }
        if (minH == Double.MAX_VALUE && count > 0) { minH = sumH / count; }
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
        // Graph ordering uses a regional elevation (rings of the smooth geological proxy, ~1-3 km) blended with the
        // domain's own low point. Local noise depressions therefore do not trap drainage; only genuine regional
        // lows become terminal basins. Pass levels still use the real local elevations.
        double regional = 0;
        int rn = 0;
        for (int ring = 1; ring <= 2; ring++) {
            double rr = ring * 1800.0;
            for (int k = 0; k < 8; k++) {
                double ang = k * Math.PI / 4 + ring * 0.39;
                regional += base.geology().proxyHeight(cx + Math.cos(ang) * rr, cz + Math.sin(ang) * rr, o);
                rn++;
            }
        }
        regional = (regional / rn + base.geology().proxyHeight(cx, cz, o)) * 0.5;
        double cont = o.continental; // proxyHeight just sampled the domain seed
        in.regional = regional;
        // Continentality decreases smoothly toward coasts at the scale of whole continents, so including it makes
        // routing follow the continent's broad slope to the sea; genuine interior lows remain terminal basins.
        in.E = in.coastal ? sea - 1 : 0.5 * minH + 0.3 * regional + 0.2 * (sea + 260 * cont);
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
        Geology.GeoSample o = tl.get();
        double bestH = Double.MAX_VALUE, bestX = 0, bestZ = 0;
        boolean found = false;
        // Band search along the bisector for lo|hi boundary crossings: probes step across the bisector (direction
        // lo->hi), so diagonal and warped neighbours whose seed-to-seed line crosses a third domain are still found.
        double mx = (ax + bx) * 0.5, mz = (az + bz) * 0.5;
        double px = -tz, pz = tx; // along the bisector
        double halfAlong = 0.85 * SPACING, halfAcross = 2.6 * WARP_AMP;
        double stepAlong = 40, stepAcross = 40;
        for (double u = -halfAlong; u <= halfAlong; u += stepAlong) {
            long prevOwner = 0;
            double prevX = 0, prevZ = 0;
            boolean havePrev = false;
            for (double v = -halfAcross; v <= halfAcross; v += stepAcross) {
                double qx = mx + px * u + tx * v, qz = mz + pz * u + tz * v;
                long oq = owner(qx, qz);
                if (havePrev && prevOwner == lo && oq == hi) {
                    double x0 = prevX, z0 = prevZ, x1 = qx, z1 = qz;
                    for (int it = 0; it < 12; it++) {
                        double xm = (x0 + x1) * 0.5, zm = (z0 + z1) * 0.5;
                        if (owner(xm, zm) == lo) { x0 = xm; z0 = zm; } else { x1 = xm; z1 = zm; }
                    }
                    if (owner(x1, z1) == hi) {
                        double cx = (x0 + x1) * 0.5, cz = (z0 + z1) * 0.5;
                        base.sample(cx, cz, o);
                        if (Double.isNaN(o.fixedLevel) && Double.isNaN(o.sinkLevel)) {
                            double h = o.h0;
                            if (h < bestH || (h == bestH && (cx < bestX || (cx == bestX && cz < bestZ)))) {
                                bestH = h; bestX = cx; bestZ = cz; found = true;
                            }
                        }
                    }
                }
                prevOwner = oq; prevX = qx; prevZ = qz; havePrev = true;
            }
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

    public static final int KEY_WINDOW = 4;

    /**
     * Routing key of a domain: {filled level, hops, terminal flag}. Computed by a priority flood over the domain
     * graph inside a fixed (2*KEY_WINDOW+1)^2 window centred on the domain, seeded by coastal domains, arid
     * terminal depressions and the window rim. The filled level is the minimax spill elevation to an outlet; hops
     * break ties toward the outlet. Domains route only to neighbours with a strictly smaller key, so the graph is
     * acyclic by construction even though each key is computed in its own window.
     */
    public double[] key(long id) {
        return keys.getOrCompute(id, this::computeKey);
    }

    /** Arid local depressions stay closed (endorheic) instead of spilling: evaporation balances inflow. */
    private boolean aridTerminal(Info in) {
        if (in.coastal || in.precip >= 0.42) return false;
        for (long n : neighbours(in.id)) if (info(n).E < in.E) return false;
        return true;
    }

    private double[] computeKey(long id) {
        int gx = Hash.unpackX(id), gz = Hash.unpackZ(id);
        int W = KEY_WINDOW, size = 2 * W + 1;
        double[] fill = new double[size * size];
        int[] hops = new int[size * size];
        boolean[] done = new boolean[size * size];
        java.util.Arrays.fill(fill, Double.MAX_VALUE);
        java.util.PriorityQueue<double[]> pq = new java.util.PriorityQueue<>((a, b) -> {
            int c = Double.compare(a[0], b[0]);
            if (c != 0) return c;
            c = Double.compare(a[1], b[1]);
            return c != 0 ? c : Double.compare(a[2], b[2]);
        });
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                Info in = info(Hash.pack(gx + i - W, gz + j - W));
                boolean rim = i == 0 || j == 0 || i == size - 1 || j == size - 1;
                if (in.coastal || rim || aridTerminal(in)) {
                    int k = j * size + i;
                    fill[k] = in.E;
                    hops[k] = 0;
                    pq.add(new double[]{in.E, 0, k});
                }
            }
        }
        while (!pq.isEmpty()) {
            double[] top = pq.poll();
            int k = (int) top[2];
            if (done[k]) continue;
            done[k] = true;
            int ci = k % size, cj = k / size;
            long cid = Hash.pack(gx + ci - W, gz + cj - W);
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dz == 0) continue;
                    int ni = ci + dx, nj = cj + dz;
                    if (ni < 0 || nj < 0 || ni >= size || nj >= size) continue;
                    int nk = nj * size + ni;
                    if (done[nk]) continue;
                    long nid = Hash.pack(gx + ni - W, gz + nj - W);
                    Pass ps = pass(cid, nid);
                    if (ps == null) continue;
                    // Water leaving n through this pass must rise to the pass: low passes are preferred spillways.
                    double f = Math.max(Math.max(info(nid).E, ps.h0), fill[k]);
                    int hp = f == fill[k] ? hops[k] + 1 : 0;
                    if (f < fill[nk] || (f == fill[nk] && hp < hops[nk])) {
                        fill[nk] = f;
                        hops[nk] = hp;
                        pq.add(new double[]{f, hp, nk});
                    }
                }
            }
        }
        int c = W * size + W;
        Info self = info(id);
        if (fill[c] == Double.MAX_VALUE) return new double[]{self.E, 0, 1}; // isolated: terminal
        boolean terminal = self.coastal || aridTerminal(self);
        return new double[]{fill[c], terminal ? -1 : hops[c], terminal ? 1 : 0};
    }

    /** Strict total order on routing keys (filled level, hops, id). */
    private boolean lowerKey(long a, long b) {
        double[] ka = key(a), kb = key(b);
        if (ka[0] != kb[0]) return ka[0] < kb[0];
        if (ka[1] != kb[1]) return ka[1] < kb[1];
        return a < b;
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
        Arrays.sort(order, (p, q) -> lowerKey(nb[p], nb[q]) ? -1 : (lowerKey(nb[q], nb[p]) ? 1 : 0));
        boolean terminal = key(id)[2] == 1;
        // Among neighbours with a strictly lower key (acyclicity), take the one reached over the lowest pass.
        Pass bestPass = null;
        Info bestN = null;
        for (int oi : order) {
            if (terminal) break;
            Info n = ni[oi];
            if (!lowerKey(n.id, id)) break;
            Pass p = pass(id, n.id);
            if (p == null) continue;
            if (bestPass == null || p.h0 < bestPass.h0 - 1e-9) { bestPass = p; bestN = n; }
        }
        if (bestPass != null) {
            Info n = bestN;
            Pass p = bestPass;
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
            // Water gaps are at most MAX_GAP deep; anything deeper becomes a lake behind the divide instead.
            est = Math.max(est, p.h0 - MAX_GAP);
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
