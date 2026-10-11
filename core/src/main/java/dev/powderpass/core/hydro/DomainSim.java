package dev.powderpass.core.hydro;

import dev.powderpass.core.GenSettings;
import dev.powderpass.core.geo.BaseTerrain;
import dev.powderpass.core.geo.Geology;
import dev.powderpass.core.geo.Province;
import dev.powderpass.core.util.MathUtil;

import java.util.List;

/**
 * Generation-time landscape evolution for one drainage domain on an 8-block grid.
 *
 * <p>Processes actually simulated (each iteration):
 * <ol>
 *   <li>Depression handling and flow routing: epsilon priority-flood from the domain's outlets (sea, canonical pass,
 *       intentional sinks, or the terminal-basin low point), then D8 steepest descent on the filled surface. Filled
 *       depressions are lakes. The resulting receiver graph is acyclic by construction.</li>
 *   <li>Drainage area: precipitation-weighted contributing area accumulated in topological order, plus canonical
 *       inflow discharge at upstream passes.</li>
 *   <li>Fluvial incision: implicit stream-power law (Braun &amp; Willett 2013), dz = -K A^m S^n with n = 1,
 *       erodibility K inversely proportional to the resistance of the rock layer exposed at the current elevation,
 *       plus tectonic uplift in young orogens.</li>
 *   <li>Sediment transport: eroded volume is routed downstream; where it exceeds a transport capacity
 *       (proportional to discharge x slope) the excess is deposited (alluvium, fans, lake infill). Sediment reaching
 *       the sea builds shelves and delta lobes. Sediment is conserved within the domain; volume leaving through a
 *       pass or to the open sea is counted as exported (not handed to the neighbour simulation).</li>
 *   <li>Thermal erosion (talus relaxation above a resistance-dependent critical slope) and hillslope creep.</li>
 * </ol>
 * Optional ancient glaciation runs first: ice flux accumulates above an equilibrium-line altitude, erodes in
 * proportion to sqrt(ice flux) (producing overdeepenings/tarns and hanging tributaries) and widens valley floors
 * (U-shaped cross-sections). Cells pinned by overlays (flat cores, pits) and the boundary ring never change.
 */
public final class DomainSim {
    public static final int RES = 8;
    static final double CELL_AREA = RES * RES;

    public static final byte OUT = 0, IN = 1, RING = 2, SEA = 3, OUTLET = 4, INFLOW = 5, SINK = 6, WETSINK = 7,
            FIXED = 8, ISO = 9;

    public final long id;
    public final int ox, oz, w, h;
    public final float[] z;       // final eroded surface
    public final float[] z0;      // initial (pre-erosion) surface
    public final float[] water;   // water surface (NaN = dry)
    public final float[] sed;     // deposited thickness (alluvium, talus, moraine)
    public final float[] area;    // precipitation-weighted drainage area
    public final float[] ice;     // peak ice flux during glaciation (0 = never glaciated)
    public final byte[] kind;
    public final int[] rec;
    public final Domains.Flow flow;
    public final List<Domains.Inflow> inflows;
    public final double outflowQ;
    public double erodedVolume, depositedVolume, exportedPass, exportedSea, exportedSink;
    public int lakeCells, glacierCells, breaches;
    public int rejKeepLarge, rejCut, rejFixed, rejLen, cappedLakeCells;
    public RiverNet rivers;

    private final float[] res, upl, prec, ringH, sinkW;
    private final GenSettings s;
    private final int sea;

    private DomainSim(long id, int ox, int oz, int w, int h, Domains.Flow flow, List<Domains.Inflow> inflows,
                      double outflowQ, GenSettings s) {
        this.id = id;
        this.ox = ox;
        this.oz = oz;
        this.w = w;
        this.h = h;
        int n = w * h;
        z = new float[n];
        z0 = new float[n];
        water = new float[n];
        sed = new float[n];
        area = new float[n];
        ice = new float[n];
        kind = new byte[n];
        rec = new int[n];
        res = new float[n];
        upl = new float[n];
        prec = new float[n];
        ringH = new float[n];
        sinkW = new float[n];
        this.flow = flow;
        this.inflows = inflows;
        this.outflowQ = outflowQ;
        this.s = s;
        this.sea = s.seaLevel();
    }

    public double cellX(int i) { return ox + i * (double) RES; }
    public double cellZ(int j) { return oz + j * (double) RES; }

    public long memoryBytes() {
        return (long) w * h * (4 * 12 + 1 + 4) + (rivers != null ? rivers.memoryBytes() : 0) + 256;
    }

    static boolean fixedKind(byte k) {
        return k == RING || k == OUTLET || k == INFLOW || k == SINK || k == WETSINK || k == FIXED || k == ISO || k == OUT;
    }

    static boolean seedKind(byte k) {
        return k == SEA || k == OUTLET || k == SINK || k == WETSINK;
    }

    // ------------------------------------------------------------------------------------------------ build

    public static DomainSim build(Domains dom, long id, boolean erode) {
        Domains.Info info = dom.info(id);
        Domains.Flow flow = dom.flow(id);
        List<Domains.Inflow> inflows = dom.inflows(id);
        double outQ = flow.type == Domains.FlowType.DOWN ? dom.outflowDischarge(id) : 0;
        BaseTerrain base = dom.base();
        GenSettings s = base.settings();
        int ox = (int) Math.floor(info.minX / RES) * RES - 3 * RES;
        int oz = (int) Math.floor(info.minZ / RES) * RES - 3 * RES;
        int w = (int) Math.ceil((info.maxX - ox) / RES) + 4;
        int h = (int) Math.ceil((info.maxZ - oz) / RES) + 4;
        DomainSim sim = new DomainSim(id, ox, oz, w, h, flow, inflows, outQ, s);
        sim.init(dom, info);
        if (erode) sim.run(dom);
        else sim.finishWithoutErosion();
        sim.rivers = RiverNet.extract(sim, dom);
        return sim;
    }

    private void init(Domains dom, Domains.Info info) {
        BaseTerrain base = dom.base();
        Geology geo = base.geology();
        Geology.GeoSample o = new Geology.GeoSample();
        int n = w * h;
        float[] fixedLv = new float[n];
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int k = j * w + i;
                double x = cellX(i), zz = cellZ(j);
                base.sample(x, zz, o);
                boolean inside = dom.owner(x, zz) == id;
                double hb = HydroRules.boundaryHeight(o, s);
                double h0 = o.h0;
                fixedLv[k] = (float) (Double.isNaN(o.fixedLevel) ? Double.NaN : o.fixedLevel);
                ringH[k] = (float) hb;
                z0[k] = (float) h0;
                prec[k] = (float) base.climate().precipitation(x, zz);
                double r = !Double.isNaN(o.resistanceOverride) ? o.resistanceOverride
                        : o.province.resistance * geo.layerResistance(geo.strataCoord(h0, o, x, zz), o.province);
                res[k] = (float) Math.max(0.3, r);
                upl[k] = (float) o.uplift;
                sinkW[k] = Float.NaN;
                if (!inside) {
                    kind[k] = OUT;
                    z[k] = (float) hb;
                    continue;
                }
                z[k] = (float) h0;
                if (!Double.isNaN(o.sinkLevel)) {
                    kind[k] = Double.isNaN(o.sinkWater) ? SINK : WETSINK;
                    sinkW[k] = (float) o.sinkWater;
                } else if (o.sea) {
                    kind[k] = SEA;
                } else if (!Double.isNaN(o.fixedLevel)) {
                    kind[k] = FIXED;
                    z[k] = (float) o.fixedLevel;
                } else {
                    kind[k] = IN;
                }
            }
        }
        // Boundary ring: inside cells with an outside 8-neighbour are pinned to the shared boundary height.
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int k = j * w + i;
                if (kind[k] == OUT || kind[k] == SEA || kind[k] == SINK || kind[k] == WETSINK) continue;
                boolean edge = i == 0 || j == 0 || i == w - 1 || j == h - 1;
                for (int dj = -1; dj <= 1 && !edge; dj++)
                    for (int di = -1; di <= 1 && !edge; di++)
                        if (kind[(j + dj) * w + i + di] == OUT) edge = true;
                if (edge) {
                    kind[k] = RING;
                    z[k] = ringH[k];
                }
            }
        }
        // Canonical notches at every pass near this domain (same rule the sampler applies).
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int k = j * w + i;
                if (kind[k] == SEA) continue;
                double x = cellX(i), zz = cellZ(j);
                final int kk = k;
                dom.forEachPassNear(x, zz, HydroRules.NOTCH_R, (f, d) -> {
                    float nv = (float) HydroRules.notch(d, f.level);
                    if (nv < z[kk] && kind[kk] != FIXED && kind[kk] != SINK && kind[kk] != WETSINK) z[kk] = nv;
                });
            }
        }
        // Outlet and inflow cells.
        if (flow.type == Domains.FlowType.DOWN) {
            int k = nearestInside(flow.outPass.x, flow.outPass.z);
            if (k >= 0) {
                kind[k] = OUTLET;
                z[k] = flow.level;
            }
        } else if (flow.type == Domains.FlowType.BASIN) {
            int low = -1;
            for (int k = 0; k < w * h; k++) {
                if (kind[k] == IN && (low < 0 || z[k] < z[low])) low = k;
            }
            if (low >= 0) {
                double p = info.precip;
                if (p > 0.6) {
                    double lvl = z[low] + MathUtil.clamp((p - 0.5) * 40, 1, 22);
                    double maxLvl = Double.MAX_VALUE;
                    for (int k = 0; k < w * h; k++) if (kind[k] == RING) maxLvl = Math.min(maxLvl, z[k] - 2);
                    for (Domains.Inflow in : inflows) maxLvl = Math.min(maxLvl, in.level - 1);
                    lvl = Math.floor(Math.min(lvl, maxLvl));
                    kind[low] = WETSINK;
                    sinkW[low] = (float) Math.max(z[low], lvl);
                } else {
                    kind[low] = SINK;
                }
            }
        }
        for (Domains.Inflow in : inflows) {
            int k = nearestInside(in.pass.x, in.pass.z);
            if (k >= 0 && kind[k] != OUTLET) {
                kind[k] = INFLOW;
                z[k] = in.level;
            }
        }
        System.arraycopy(z, 0, z0, 0, n);
    }

    private int nearestInside(double x, double zz) {
        int bi = -1;
        double bd = Double.MAX_VALUE;
        int ci = (int) Math.round((x - ox) / RES), cj = (int) Math.round((zz - oz) / RES);
        for (int j = Math.max(0, cj - 4); j <= Math.min(h - 1, cj + 4); j++) {
            for (int i = Math.max(0, ci - 4); i <= Math.min(w - 1, ci + 4); i++) {
                int k = j * w + i;
                if (kind[k] == OUT) continue;
                double d = MathUtil.sq(cellX(i) - x) + MathUtil.sq(cellZ(j) - zz);
                if (d < bd) { bd = d; bi = k; }
            }
        }
        return bi;
    }

    // ------------------------------------------------------------------------------------------------ routing

    private float[] F;
    private int[] order;
    private int orderLen;
    private float[] rdist;
    private int[] par;
    private MinHeap heap;
    private static final int[] DI = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] DJ = {0, 0, 1, -1, 1, -1, 1, -1};
    private static final double[] DD = {RES, RES, RES, RES, RES * Math.sqrt(2), RES * Math.sqrt(2), RES * Math.sqrt(2), RES * Math.sqrt(2)};
    private static final float EPS = 1e-3f;

    /** Epsilon priority flood; fills F, the processing order, receivers and receiver distances. */
    private void flood() {
        int n = w * h;
        if (F == null) {
            F = new float[n];
            order = new int[n];
            rdist = new float[n];
            par = new int[n];
            heap = new MinHeap(n);
        }
        heap.clear();
        boolean[] done = new boolean[n];
        for (int k = 0; k < n; k++) {
            rec[k] = -1;
            par[k] = -1;
            F[k] = Float.NaN;
            byte kk = kind[k];
            if (kk == OUT) continue;
            if (seedKind(kk)) {
                float f = z[k];
                if (kk == SEA) f = Math.max(z[k], sea);
                if (kk == WETSINK && !Float.isNaN(sinkW[k])) f = Math.max(z[k], sinkW[k]);
                if (kk == OUTLET) f = z[k];
                F[k] = f;
                heap.push(f, k);
            }
        }
        orderLen = 0;
        while (!heap.isEmpty()) {
            int c = heap.pop();
            if (done[c]) continue;
            done[c] = true;
            order[orderLen++] = c;
            // Divides never carry through-flow: ring and inflow cells drain inward but do not route water onward.
            if (kind[c] == RING || kind[c] == INFLOW) continue;
            int ci = c % w, cj = c / w;
            float fc = F[c];
            for (int d = 0; d < 8; d++) {
                int ni = ci + DI[d], nj = cj + DJ[d];
                if (ni < 0 || nj < 0 || ni >= w || nj >= h) continue;
                int nk = nj * w + ni;
                if (done[nk] || kind[nk] == OUT) continue;
                float nf = Math.max(z[nk], fc + EPS);
                if (Float.isNaN(F[nk]) || nf < F[nk]) {
                    F[nk] = nf;
                    par[nk] = c;
                    heap.push(nf, nk);
                }
            }
        }
        // Unreached inside cells (fragments cut off by the warp) become pinned isolates.
        for (int k = 0; k < n; k++) {
            if (kind[k] != OUT && !done[k]) {
                kind[k] = ISO;
                z[k] = ringH[k];
                F[k] = Float.NaN;
            }
        }
        // Receivers: steepest descent on F (strictly lower), so the graph is acyclic.
        for (int t = 0; t < orderLen; t++) {
            int c = order[t];
            if (seedKind(kind[c])) continue;
            int ci = c % w, cj = c / w;
            double best = 0;
            int bk = -1;
            double bd = RES;
            for (int d = 0; d < 8; d++) {
                int ni = ci + DI[d], nj = cj + DJ[d];
                if (ni < 0 || nj < 0 || ni >= w || nj >= h) continue;
                int nk = nj * w + ni;
                if (kind[nk] == OUT || kind[nk] == ISO || Float.isNaN(F[nk])) continue;
                double sl = (F[c] - F[nk]) / DD[d];
                if (sl > best) { best = sl; bk = nk; bd = DD[d]; }
            }
            rec[c] = bk;
            rdist[c] = (float) bd;
        }
    }

    private void accumulate(double[] A) {
        int n = w * h;
        for (int k = 0; k < n; k++) A[k] = kind[k] == OUT || kind[k] == ISO ? 0 : CELL_AREA * prec[k];
        for (Domains.Inflow in : inflows) {
            int k = nearestInside(in.pass.x, in.pass.z);
            if (k >= 0) A[k] += in.q;
        }
        for (int t = orderLen - 1; t >= 0; t--) {
            int c = order[t];
            int r = rec[c];
            if (r >= 0) A[r] += A[c];
        }
    }

    /** Breaches barriers between inflow passes and the outlet so inflow water levels stay canonical. */
    private boolean breachInflows() {
        boolean changed = false;
        for (Domains.Inflow in : inflows) {
            int k = nearestInside(in.pass.x, in.pass.z);
            if (k < 0 || kind[k] != INFLOW || Float.isNaN(F[k])) continue;
            if (F[k] <= in.level + 0.25f) continue;
            int c = rec[k];
            double target = in.level;
            int steps = 0;
            while (c >= 0 && steps < 600) {
                target -= 0.0015 * RES;
                if (F[c] <= target) break;
                if (!fixedKind(kind[c]) && kind[c] != SEA && z[c] > target - 0.5) {
                    z[c] = (float) (target - 0.5);
                    changed = true;
                }
                c = rec[c];
                steps++;
            }
            if (changed) breaches++;
        }
        return changed;
    }

    /**
     * Hybrid depression handling (after Lindsay 2016): each enclosed depression is either kept as a lake (shallow
     * ponds, or basins too deep/long to breach) or breached by carving a monotone channel from its pit along the
     * flood-parent chain until the path drops below the pit. Breached channels are then deepened and widened by the
     * erosion model like any other channel, giving water gaps and gorges where rivers cross ridges.
     */
    private boolean breachDepressions() {
        int n = w * h;
        int[] label = new int[n];
        java.util.Arrays.fill(label, -1);
        int[] stack = new int[n];
        boolean changed = false;
        int labels = 0;
        for (int k0 = 0; k0 < n; k0++) {
            if (label[k0] >= 0 || !isDepression(k0)) continue;
            // Flood-fill this depression; find pit, size and depth.
            int sp = 0, sizeCells = 0, pit = k0;
            stack[sp++] = k0;
            label[k0] = labels;
            while (sp > 0) {
                int c = stack[--sp];
                sizeCells++;
                if (z[c] < z[pit] || (z[c] == z[pit] && c < pit)) pit = c;
                int ci = c % w, cj = c / w;
                for (int d = 0; d < 8; d++) {
                    int ni = ci + DI[d], nj = cj + DJ[d];
                    if (ni < 0 || nj < 0 || ni >= w || nj >= h) continue;
                    int nk = nj * w + ni;
                    if (label[nk] < 0 && isDepression(nk)) { label[nk] = labels; stack[sp++] = nk; }
                }
            }
            labels++;
            double depth = F[pit] - z[pit];
            if (depth < 2.0 && sizeCells < 40 && (pit * 0x9E3779B1L & 0xFF) < 40) continue; // occasional small pond
            if (sizeCells > 1500 && depth > 25) { rejKeepLarge++; continue; }              // genuine large basin: keep as lake
            // Least-cost breach path from the pit to lower ground outside the depression: cost favours low
            // terrain (little excavation) with slight noise, so channels follow natural lows instead of straight lines.
            int[] path = breachPath(pit, label, labels - 1);
            if (path == null) { rejCut++; continue; }
            double target = z[pit];
            for (int c : path) {
                target -= 0.0008 * RES;
                if (z[c] > target && !fixedKind(kind[c]) && !seedKind(kind[c]) && kind[c] != SEA) z[c] = (float) target;
            }
            changed = true;
            breaches++;
        }
        return changed;
    }

    private MinHeap bheap;
    private float[] bcost;
    private int[] bprev;
    private int[] btouched;

    /** Dijkstra from the pit; returns the cell sequence (excluding the pit) or null if too long/deep. */
    private int[] breachPath(int pit, int[] label, int myLabel) {
        int n = w * h;
        if (bheap == null) {
            bheap = new MinHeap(1024);
            bcost = new float[n];
            bprev = new int[n];
            btouched = new int[n];
            java.util.Arrays.fill(bcost, Float.MAX_VALUE);
        }
        bheap.clear();
        int touched = 0;
        bcost[pit] = 0;
        bprev[pit] = -1;
        btouched[touched++] = pit;
        bheap.push(0, pit);
        float zp = z[pit];
        int goal = -1, expanded = 0;
        while (!bheap.isEmpty() && expanded < 30000) {
            int c = bheap.pop();
            double cc = bheap.lastKey;
            if (cc > bcost[c]) continue;
            expanded++;
            if (c != pit && label[c] != myLabel && (seedKind(kind[c]) || (!Float.isNaN(F[c]) && F[c] < zp - 0.2))) { goal = c; break; }
            int ci = c % w, cj = c / w;
            for (int d = 0; d < 8; d++) {
                int ni = ci + DI[d], nj = cj + DJ[d];
                if (ni < 0 || nj < 0 || ni >= w || nj >= h) continue;
                int nk = nj * w + ni;
                byte nkind = kind[nk];
                if (nkind == OUT || nkind == ISO || nkind == RING || nkind == INFLOW || nkind == FIXED) continue;
                double dig = Math.max(0, z[nk] - zp);
                if (dig > 70) continue;
                double noise = ((nk * 0x9E3779B1L) >>> 20 & 0xFF) / 255.0;
                double step = DD[d] / RES * (0.6 + 0.5 * noise) + dig * 1.5;
                float nc = (float) (cc + step);
                if (nc < bcost[nk]) {
                    if (bcost[nk] == Float.MAX_VALUE) btouched[touched++] = nk;
                    bcost[nk] = nc;
                    bprev[nk] = c;
                    bheap.push(nc, nk);
                }
            }
        }
        int[] path = null;
        if (goal >= 0) {
            int len = 0;
            for (int c = goal; c != pit && c >= 0; c = bprev[c]) len++;
            if (len <= 300) {
                path = new int[len];
                int i = len - 1;
                for (int c = goal; c != pit && c >= 0; c = bprev[c]) path[i--] = c;
            }
        }
        for (int t = 0; t < touched; t++) bcost[btouched[t]] = Float.MAX_VALUE;
        return path;
    }

    static final boolean DEBUG = Boolean.getBoolean("powderpass.debugSim");

    private int countDepr() {
        int c = 0;
        for (int k = 0; k < w * h; k++) if (isDepression(k)) c++;
        return c;
    }

    private boolean isDepression(int k) {
        byte kk = kind[k];
        if (kk == OUT || kk == ISO || seedKind(kk) || Float.isNaN(F[k])) return false;
        return F[k] - z[k] > 0.5f;
    }

    // ------------------------------------------------------------------------------------------------ evolution

    private void run(Domains dom) {
        int n = w * h;
        double[] A = new double[n];
        double es = s.erosionStrength();
        int iters = s.erosionIterations();
        flood();
        if (breachInflows()) flood();
        for (int pass = 0; pass < 3; pass++) {
            if (!breachDepressions()) break;
            flood();
        }

        if (DEBUG) System.out.println("  after breach: depressions=" + countDepr());
        // ---- ancient glaciation ----
        if (s.glaciation() && iters > 0) {
            float[] ela = new float[n];
            boolean any = false;
            for (int j = 0; j < h; j++) {
                for (int i = 0; i < w; i++) {
                    int k = j * w + i;
                    double tb = dom.base().climate().baseTemperatureInterp(cellX(i), cellZ(j));
                    ela[k] = (float) (sea + 330 + 260 * tb - 70);
                    if (kind[k] != OUT && z[k] > ela[k]) any = true;
                }
            }
            if (any) glaciate(ela, Math.max(4, iters / 4), es);
        }

        if (DEBUG) { flood(); System.out.println("  after glacial: depressions=" + countDepr()); }
        // ---- fluvial + hillslope ----
        double[] qs = new double[n];
        float[] delta = new float[n];
        double kBase = 0.02 * es;
        for (int it = 0; it < iters; it++) {
            flood();
            boolean again = breachInflows();
            if (it % 4 == 3 || it == iters - 1) again |= breachDepressions();
            if (again) flood();
            accumulate(A);
            lastA = A;
            // Implicit stream-power incision, downstream first.
            float[] before = delta; // reuse buffer to store pre-incision heights
            for (int t = 0; t < orderLen; t++) {
                int c = order[t];
                before[c] = z[c];
                if (fixedKind(kind[c]) || seedKind(kind[c])) continue;
                int r = rec[c];
                if (r < 0) continue;
                if (F[c] - z[c] > 0.5f) continue; // submerged in a lake: no incision
                double baseLv = Math.max(z[r], kind[r] == SEA ? sea : (F[r] - z[r] > 0.5f ? F[r] : z[r]));
                double K = kBase / res[c];
                double f = K * Math.sqrt(A[c]) / rdist[c];
                double zc = z[c] + 0.25 * upl[c];
                if (zc > baseLv) {
                    double zn = (zc + f * baseLv) / (1 + f);
                    z[c] = (float) Math.max(baseLv, zn);
                } else {
                    z[c] = (float) zc;
                }
            }
            // Sediment routing, upstream first.
            java.util.Arrays.fill(qs, 0);
            for (int t = orderLen - 1; t >= 0; t--) {
                int c = order[t];
                double eroded = Math.max(0, before[c] - z[c]);
                if (eroded > 0) {
                    erodedVolume += eroded * CELL_AREA;
                    sed[c] = (float) Math.max(0, sed[c] - eroded);
                }
                double q = qs[c] + eroded * CELL_AREA;
                byte kc = kind[c];
                if (seedKind(kc)) {
                    if (kc == SEA) q = depositAtSea(c, q);
                    else if (kc == OUTLET) exportedPass += q;
                    else exportedSink += q;
                    continue;
                }
                int r = rec[c];
                if (r < 0) { exportedSink += q; continue; }
                double slope = Math.max(0, (F[c] - F[r]) / rdist[c]);
                boolean lake = F[c] - z[c] > 0.5f;
                double cap = lake ? 0 : 2.0 * A[c] * slope;
                if (q > cap && !fixedKind(kc)) {
                    // Lakes may infill completely (becoming flat basin deposits); channel deposition may not raise a
                    // cell more than a gentle grade above its receiver, so deposits never dam the flow path.
                    double grade = Math.max(z[r], F[r]) + 0.004 * rdist[c];
                    double room = lake ? Math.max(0, F[c] + 0.05 - z[c]) : Math.min(1.2, Math.max(0, grade - z[c]));
                    double dep = Math.min((q - cap) * 0.5 / CELL_AREA, room);
                    if (dep > 0) {
                        z[c] += (float) dep;
                        sed[c] += (float) dep;
                        depositedVolume += dep * CELL_AREA;
                        q -= dep * CELL_AREA;
                    }
                }
                qs[r] += q;
            }
            thermalAndCreep(delta);
            if (DEBUG && (it % 10 == 9)) { flood(); System.out.println("  iter " + it + ": depressions=" + countDepr()); }
        }
        finish(A);
    }

    private double depositAtSea(int c, double q) {
        int cur = c;
        for (int step = 0; step < 24 && q > 1; step++) {
            double room = Math.max(0, (sea - 1.0) - z[cur]);
            double dep = Math.min(q * 0.6 / CELL_AREA, Math.min(room, 3.0));
            if (dep > 0) {
                z[cur] += (float) dep;
                sed[cur] += (float) dep;
                depositedVolume += dep * CELL_AREA;
                q -= dep * CELL_AREA;
            }
            // Move seaward: deepest sea neighbour.
            int ci = cur % w, cj = cur / w, next = -1;
            float bz = z[cur];
            for (int d = 0; d < 8; d++) {
                int ni = ci + DI[d], nj = cj + DJ[d];
                if (ni < 0 || nj < 0 || ni >= w || nj >= h) continue;
                int nk = nj * w + ni;
                if (kind[nk] == SEA && z[nk] < bz) { bz = z[nk]; next = nk; }
            }
            if (next < 0) break;
            cur = next;
        }
        exportedSea += q;
        return q;
    }

    private double[] lastA;

    private void thermalAndCreep(float[] delta) {
        int n = w * h;
        java.util.Arrays.fill(delta, 0);
        for (int j = 1; j < h - 1; j++) {
            for (int i = 1; i < w - 1; i++) {
                int c = j * w + i;
                if (kind[c] == OUT || kind[c] == ISO || fixedKind(kind[c])) continue;
                double talus = 0.9 + 0.75 * res[c];
                for (int d = 0; d < 8; d++) {
                    int nk = (j + DJ[d]) * w + i + DI[d];
                    if (kind[nk] == OUT || kind[nk] == ISO || fixedKind(kind[nk])) continue;
                    double dh = z[c] - z[nk];
                    double lim = talus * DD[d];
                    if (dh > lim) {
                        double mv = Math.min((dh - lim) * 0.12, 4.0);
                        delta[c] -= (float) mv;
                        delta[nk] += (float) mv;
                    }
                }
                // Hillslope creep (linear diffusion), stronger on old, soil-mantled ranges.
                double lap = (z[c - 1] + z[c + 1] + z[c - w] + z[c + w]) * 0.25 - z[c];
                double kappa = 0.05 * (1.0 - 0.6 * upl[c]) / Math.max(0.6, res[c] * 0.6);
                if (lastA != null && lastA[c] > 6000) kappa *= 0.15; // channels: creep is outpaced by transport
                delta[c] += (float) (kappa * lap);
            }
        }
        for (int c = 0; c < n; c++) {
            if (delta[c] != 0) {
                z[c] += delta[c];
                if (delta[c] > 0) sed[c] += delta[c] * 0.5f;
            }
        }
    }

    private void glaciate(float[] ela, int giters, double es) {
        int n = w * h;
        double[] iceIn = new double[n];
        double[] flux = new double[n];
        double[] moraine = new double[n];
        float[] delta = new float[n];
        double kg = 0.0055 * es;
        for (int it = 0; it < giters; it++) {
            flood();
            java.util.Arrays.fill(iceIn, 0);
            for (int t = orderLen - 1; t >= 0; t--) {
                int c = order[t];
                if (kind[c] == OUT || kind[c] == ISO) { flux[c] = 0; continue; }
                double b = MathUtil.clamp((z[c] - ela[c]) / 120.0, -1.5, 1.0) * CELL_AREA;
                double q = Math.max(0, b + iceIn[c]);
                flux[c] = q;
                if (q > 0) ice[c] = (float) Math.max(ice[c], q);
                int r = rec[c];
                if (r >= 0) iceIn[r] += q;
                else if (q > 0) moraine[c] += q * 0.001;
            }
            java.util.Arrays.fill(delta, 0);
            for (int t = 0; t < orderLen; t++) {
                int c = order[t];
                if (flux[c] <= 0 || fixedKind(kind[c]) || seedKind(kind[c])) continue;
                int r = rec[c];
                double slope = r >= 0 ? Math.max(0, (z[c] - z[r]) / rdist[c]) : 0;
                double e = Math.min(4.0, kg * Math.sqrt(flux[c]) * (0.3 + Math.min(1.0, slope)));
                delta[c] -= (float) e;
                erodedVolume += e * CELL_AREA;
                // Valley widening: abrade adjacent higher walls toward the glacier surface -> U-shaped profile.
                if (flux[c] > 400) {
                    int ci = c % w, cj = c / w;
                    double wf = Math.min(1.0, flux[c] / 6000.0);
                    for (int d = 0; d < 4; d++) {
                        int ni = ci + DI[d], nj = cj + DJ[d];
                        if (ni < 0 || nj < 0 || ni >= w || nj >= h) continue;
                        int nk = nj * w + ni;
                        if (fixedKind(kind[nk]) || seedKind(kind[nk])) continue;
                        double dh = z[nk] - z[c];
                        if (dh > 1) delta[nk] -= (float) Math.min(3.0, dh * 0.12 * wf);
                    }
                }
                // Moraine at the terminus (ice flux vanishes downstream).
                if (r >= 0 && flux[r] <= 0) moraine[r] += e * 0.4;
            }
            for (int c = 0; c < n; c++) if (delta[c] != 0) z[c] += delta[c];
        }
        for (int c = 0; c < n; c++) {
            if (moraine[c] > 0 && !fixedKind(kind[c]) && !seedKind(kind[c])) {
                float m = (float) Math.min(6, moraine[c]);
                z[c] += m;
                sed[c] += m;
                depositedVolume += m * CELL_AREA;
            }
            if (ice[c] > 0) glacierCells++;
        }
    }

    private void finishWithoutErosion() {
        flood();
        breachInflows();
        flood();
        for (int pass = 0; pass < 3; pass++) {
            if (!breachDepressions()) break;
            flood();
        }
        finish(new double[w * h]);
    }

    private void finish(double[] A) {
        flood();
        if (breachInflows()) flood();
        for (int pass = 0; pass < 2; pass++) {
            if (!breachDepressions()) break;
            flood();
        }
        accumulate(A);
        int n = w * h;
        for (int k = 0; k < n; k++) {
            area[k] = (float) A[k];
            water[k] = Float.NaN;
            byte kk = kind[k];
            if (kk == OUT || kk == ISO) continue;
            if (kk == SEA) {
                water[k] = sea;
            } else if (kk == WETSINK && !Float.isNaN(sinkW[k])) {
                if (sinkW[k] > z[k]) water[k] = sinkW[k];
            } else if (kk != RING && kk != INFLOW && !Float.isNaN(F[k]) && F[k] - z[k] > 0.3f) {
                water[k] = F[k];
                lakeCells++;
            }
        }
        capLakesBelowDivides();
    }

    /**
     * Lakes may never stand above the divide that encloses them: ring cells are leaves of the routing graph, so a
     * lake can otherwise reach a level higher than an adjacent boundary cell and would spill into the neighbouring
     * domain in game. Such lakes are lowered to one block under their lowest bounding divide (closed lakes).
     */
    private void capLakesBelowDivides() {
        int n = w * h;
        int[] label = new int[n];
        java.util.Arrays.fill(label, -1);
        int[] stack = new int[n];
        int[] members = new int[n];
        for (int k0 = 0; k0 < n; k0++) {
            if (label[k0] >= 0 || Float.isNaN(water[k0]) || kind[k0] == SEA) continue;
            int sp = 0, m = 0;
            stack[sp++] = k0;
            label[k0] = k0;
            float minDivide = Float.MAX_VALUE;
            while (sp > 0) {
                int c = stack[--sp];
                members[m++] = c;
                int ci = c % w, cj = c / w;
                for (int d = 0; d < 8; d++) {
                    int ni = ci + DI[d], nj = cj + DJ[d];
                    if (ni < 0 || nj < 0 || ni >= w || nj >= h) continue;
                    int nk = nj * w + ni;
                    byte nkind = kind[nk];
                    if (nkind == RING || nkind == OUT || nkind == ISO || nkind == INFLOW) minDivide = Math.min(minDivide, z[nk]);
                    if (label[nk] < 0 && !Float.isNaN(water[nk]) && kind[nk] != SEA) {
                        label[nk] = k0;
                        stack[sp++] = nk;
                    }
                }
            }
            if (minDivide == Float.MAX_VALUE) continue;
            float cap = (float) Math.floor(minDivide - 1);
            for (int t = 0; t < m; t++) {
                int c = members[t];
                if (water[c] > cap) {
                    water[c] = cap > z[c] + 0.3f ? cap : Float.NaN;
                    cappedLakeCells++;
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ accessors

    /** Filled water-routing surface (lake levels / channel levels) of the last flood. */
    public float filled(int k) {
        return F == null ? z[k] : F[k];
    }

    public boolean inside(int k) {
        return kind[k] != OUT;
    }

    public float resistance(int k) {
        return res[k];
    }

    public float precipitation(int k) {
        return prec[k];
    }

    public float boundaryHeight(int k) {
        return ringH[k];
    }

    /** Releases routing scratch buffers that are not needed after extraction. */
    void trim() {
        heap = null;
        order = null;
        rdist = null;
    }

    public static Province dummy() {
        return Province.SHIELD;
    }
}
