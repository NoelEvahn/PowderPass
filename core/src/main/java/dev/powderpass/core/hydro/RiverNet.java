package dev.powderpass.core.hydro;

import dev.powderpass.core.util.Hash;
import dev.powderpass.core.util.MathUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Channel network extracted from a domain simulation.
 *
 * <p>Channels are cells whose drainage area exceeds a threshold; each polyline follows simulated receivers from a
 * channel head (or an upstream inflow pass) to a confluence, the sea, a lake/sink, or the outflow pass. Confluence and
 * pass endpoints are exact, so tributaries meet trunks and rivers cross domain borders at the canonical pass point.
 * Water levels come from the routed (filled) surface and are non-increasing downstream by construction.
 *
 * <p>Simulated: channel locations, discharge, water levels, confluences, lake through-flow. Reconstructed for
 * appearance (documented approximations): sub-cell smoothing of channel paths, meander displacement on low-gradient
 * reaches, braiding bars, oxbow lakes and delta distributaries.
 */
public final class RiverNet {
    public static final int FLAG_LAKE = 1, FLAG_DELTA = 2, FLAG_BRAIDED = 4, FLAG_OXBOW = 8, FLAG_PASS = 16;

    // Flat segment storage (structure of arrays).
    float[] ax, az, bx, bz;    // endpoints
    float[] sa, sb;            // water surface at endpoints
    float[] qa, qb;            // discharge at endpoints
    float[] grad;              // water-surface gradient along segment
    byte[] flags;
    int count;

    // Spatial hash over segments.
    private int gx0, gz0, gw, gh;
    private static final int BUCKET = 32;
    private int[] bucketStart;
    private int[] bucketItems;
    public int polylines, heads, oxbows, deltas;

    public long memoryBytes() {
        return (long) count * (4 * 11 + 1) + (bucketItems == null ? 0 : bucketItems.length * 4L) + (bucketStart == null ? 0 : bucketStart.length * 4L);
    }

    public int segmentCount() {
        return count;
    }

    // ------------------------------------------------------------------------------------------------ extraction

    static RiverNet extract(DomainSim sim, Domains dom) {
        RiverNet net = new RiverNet();
        net.build(sim, dom);
        sim.trim();
        return net;
    }

    private final List<float[]> tmp = new ArrayList<>();

    private void build(DomainSim sim, Domains dom) {
        int w = sim.w, h = sim.h, n = w * h;
        double density = dom.base().settings().riverDensity();
        double threshold = 48000.0 / density;
        boolean[] channel = new boolean[n];
        int[] donors = new int[n];
        for (int k = 0; k < n; k++) {
            if (!sim.inside(k) || sim.kind[k] == DomainSim.SEA || sim.kind[k] == DomainSim.ISO) continue;
            if (sim.area[k] >= threshold || sim.kind[k] == DomainSim.INFLOW) channel[k] = true;
        }
        for (int k = 0; k < n; k++) {
            if (channel[k] && sim.rec[k] >= 0 && channel[sim.rec[k]]) donors[sim.rec[k]]++;
        }
        // Heads in canonical index order; inflows first so trunks are traced before tributaries claim cells.
        List<Integer> starts = new ArrayList<>();
        for (int k = 0; k < n; k++) if (channel[k] && sim.kind[k] == DomainSim.INFLOW) starts.add(k);
        for (int k = 0; k < n; k++) if (channel[k] && donors[k] == 0 && sim.kind[k] != DomainSim.INFLOW) starts.add(k);
        heads = starts.size();
        boolean[] visited = new boolean[n];
        for (int head : starts) {
            if (visited[head]) continue;
            List<double[]> pts = new ArrayList<>(); // x, z, surface, q, lake
            Domains.Inflow inflow = null;
            if (sim.kind[head] == DomainSim.INFLOW) {
                for (Domains.Inflow in : sim.inflows) {
                    int ci = (int) Math.round((in.pass.x - sim.ox) / DomainSim.RES), cj = (int) Math.round((in.pass.z - sim.oz) / DomainSim.RES);
                    if (Math.abs(ci - head % w) <= 4 && Math.abs(cj - head / w) <= 4) { inflow = in; break; }
                }
                if (inflow != null) pts.add(new double[]{inflow.pass.x, inflow.pass.z, inflow.level, inflow.q, 0, 1});
            }
            int c = head;
            int endKind = 0; // 0 other, 1 confluence, 2 sea
            while (c >= 0) {
                boolean lake = !Float.isNaN(sim.water[c]) && sim.kind[c] != DomainSim.SEA;
                double surf = !Float.isNaN(sim.water[c]) ? sim.water[c] : sim.filled(c);
                double q = sim.area[c];
                pts.add(new double[]{sim.cellX(c % w), sim.cellZ(c / w), surf, q, lake ? 1 : 0, 0});
                if (visited[c]) { endKind = 1; break; } // confluence: stop on the shared cell
                visited[c] = true;
                byte kc = sim.kind[c];
                if (kc == DomainSim.OUTLET && sim.flow.outPass != null) {
                    pts.add(new double[]{sim.flow.outPass.x, sim.flow.outPass.z, sim.flow.level, sim.outflowQ, 0, 1});
                    break;
                }
                if (kc == DomainSim.SEA) { endKind = 2; break; }
                if (kc == DomainSim.SINK || kc == DomainSim.WETSINK) break;
                c = sim.rec[c];
            }
            if (pts.size() < 2) continue;
            int first = count;
            emitPolyline(pts, sim);
            polylines++;
            double[] last = pts.get(pts.size() - 1);
            if (endKind == 1) pendingJoins.add(new double[]{last[0], last[1], last[2], last[3], first, count});
            if (endKind == 2 && last[3] > 350000) addDelta(first, count, sim);
        }
        for (double[] j : pendingJoins) joinConfluence(j);
        buildIndex(sim);
    }

    private final List<double[]> pendingJoins = new ArrayList<>();

    /** Connects a tributary end to the nearest point of another channel (trunks may have meandered away). */
    private void joinConfluence(double[] j) {
        double x = j[0], z = j[1];
        int s0 = (int) j[4], s1 = (int) j[5];
        double best = Double.MAX_VALUE, bx0 = 0, bz0 = 0, bs = 0;
        double[] tt = new double[1];
        for (int s = 0; s < count; s++) {
            if (s >= s0 && s < s1) continue;
            if ((flags[s] & (FLAG_OXBOW | FLAG_DELTA)) != 0) continue;
            double d = MathUtil.distToSegment(x, z, ax[s], az[s], bx[s], bz[s], tt);
            if (d < best) {
                best = d;
                bx0 = ax[s] + (bx[s] - ax[s]) * tt[0];
                bz0 = az[s] + (bz[s] - az[s]) * tt[0];
                bs = sa[s] + (sb[s] - sa[s]) * tt[0];
            }
        }
        if (best < 0.5 || best > 64) return;
        double sEnd = Math.floor(Math.min(j[2], bs) + 1e-4);
        addSeg(x, z, bx0, bz0, j[2], sEnd, j[3], j[3], (j[2] - sEnd) / best, 0);
    }

    /** Delta distributaries fanning from near the mouth (appearance reconstruction where sediment reaches the sea). */
    private void addDelta(int s0, int s1, DomainSim sim) {
        if (s1 - s0 < 20) return;
        int mouth = s1 - 1;
        int apex = Math.max(s0, s1 - 16);
        double px = ax[apex], pz = az[apex], ps = sa[apex], pq = qa[apex];
        double dx = bx[mouth] - px, dz = bz[mouth] - pz;
        double l = Math.hypot(dx, dz);
        if (l < 8) return;
        dx /= l; dz /= l;
        int sea = sim.flow == null ? 63 : (int) Math.floor(sb[mouth]);
        long hs = Hash.hash(sim.id, (long) px, (long) pz);
        for (int b = 0; b < 2; b++) {
            double ang = (b == 0 ? -1 : 1) * (0.35 + 0.3 * Hash.unit(Hash.mix(hs + b)));
            double c = Math.cos(ang), sn = Math.sin(ang);
            double ex = dx * c - dz * sn, ez = dx * sn + dz * c;
            double len = l * (1.0 + 0.6 * Hash.unit(Hash.mix(hs + 7 + b)));
            int k = 6;
            double lx = px, lz = pz, ls = ps;
            for (int i = 1; i <= k; i++) {
                double t = i / (double) k;
                double nx = px + ex * len * t + (b == 0 ? 1 : -1) * Math.sin(t * Math.PI) * 6 * -ez;
                double nz = pz + ez * len * t + (b == 0 ? 1 : -1) * Math.sin(t * Math.PI) * 6 * ex;
                double ns = Math.floor(ps + (sea - ps) * t + 1e-4);
                addSeg(lx, lz, nx, nz, ls, ns, pq * 0.3, pq * 0.3, (ls - ns) / Math.max(1, len / k), FLAG_DELTA);
                lx = nx; lz = nz; ls = ns;
            }
        }
        for (int s = Math.max(s0, s1 - 16); s < s1; s++) flags[s] |= FLAG_DELTA;
        deltas++;
    }

    /** Monotone surfaces, Chaikin smoothing, meanders, then segments. */
    private void emitPolyline(List<double[]> pts, DomainSim sim) {
        // Water level never rises downstream; quantize to whole blocks (Minecraft water sits on block tops).
        double prev = Double.MAX_VALUE;
        for (double[] p : pts) {
            double sv = Math.min(prev, p[2]);
            p[2] = sv;
            prev = sv;
        }
        for (double[] p : pts) p[2] = Math.floor(p[2] + 1e-4);
        // Chaikin x2 keeping endpoints.
        List<double[]> cur = pts;
        for (int pass = 0; pass < 2; pass++) {
            List<double[]> nx = new ArrayList<>();
            nx.add(cur.get(0));
            for (int i = 0; i < cur.size() - 1; i++) {
                double[] a = cur.get(i), b = cur.get(i + 1);
                if (a[5] == 1 || b[5] == 1) { // keep segments touching a pass point exact
                    if (i > 0) nx.add(a);
                    continue;
                }
                nx.add(mix(a, b, 0.25));
                nx.add(mix(a, b, 0.75));
            }
            nx.add(cur.get(cur.size() - 1));
            cur = nx;
        }
        // Resample to ~4 blocks and apply meanders on low-gradient, large rivers (appearance reconstruction).
        List<double[]> rs = resample(cur, 4.0);
        double total = 0;
        double[] arc = new double[rs.size()];
        for (int i = 1; i < rs.size(); i++) {
            total += Math.hypot(rs.get(i)[0] - rs.get(i - 1)[0], rs.get(i)[1] - rs.get(i - 1)[1]);
            arc[i] = total;
        }
        long phaseSeed = Hash.hash(sim.id, (long) rs.get(0)[0], (long) rs.get(0)[1]);
        double phase = Hash.unit(phaseSeed) * Math.PI * 2;
        double[] offs = new double[rs.size()];
        for (int i = 1; i < rs.size() - 1; i++) {
            double[] p = rs.get(i);
            double wv = HydroRules.width(p[3]);
            int a = Math.max(0, i - 6), b = Math.min(rs.size() - 1, i + 6);
            double g = (rs.get(a)[2] - rs.get(b)[2]) / Math.max(1, arc[b] - arc[a]);
            double lowGrad = 1 - MathUtil.smoothstep(0.002, 0.012, g);
            double taper = MathUtil.smoothstep(0, 40, arc[i]) * MathUtil.smoothstep(0, 40, total - arc[i]);
            double amp = Math.min(1.3 * wv + 1.5, 11) * lowGrad * taper * (wv > 3 ? 1 : 0.35);
            if (p[4] == 1) amp = 0;
            double lambda = 10 * wv + 30;
            offs[i] = amp * Math.sin(arc[i] / lambda * Math.PI * 2 + phase);
        }
        for (int i = 1; i < rs.size() - 1; i++) {
            double[] p = rs.get(i), a = rs.get(i - 1), b = rs.get(i + 1);
            double tx = b[0] - a[0], tz = b[1] - a[1];
            double l = Math.hypot(tx, tz);
            if (l < 1e-6) continue;
            p[0] += -tz / l * offs[i];
            p[1] += tx / l * offs[i];
        }
        // Emit segments.
        for (int i = 0; i < rs.size() - 1; i++) {
            double[] a = rs.get(i), b = rs.get(i + 1);
            double len = Math.max(1e-3, Math.hypot(b[0] - a[0], b[1] - a[1]));
            int fl = 0;
            if (a[4] == 1 && b[4] == 1) fl |= FLAG_LAKE;
            if (a[5] == 1 || b[5] == 1) fl |= FLAG_PASS;
            addSeg(a[0], a[1], b[0], b[1], a[2], b[2], a[3], b[3], (a[2] - b[2]) / len, fl);
        }
        // Oxbows: rare crescent lakes beside strongly meandering lowland reaches (appearance approximation).
        for (int i = 8; i < rs.size() - 8; i += 8) {
            double wv = HydroRules.width(rs.get(i)[3]);
            if (Math.abs(offs[i]) < 6 || wv < 5) continue;
            long hsh = Hash.hash(phaseSeed, i);
            if (Hash.unit(hsh) > 0.12) continue;
            double[] p = rs.get(i), q = rs.get(i - 1);
            double tx = p[0] - q[0], tz = p[1] - q[1], l = Math.hypot(tx, tz);
            if (l < 1e-6) continue;
            double side = Math.signum(offs[i]);
            double cx = p[0] + -tz / l * side * (wv + 10), cz = p[1] + tx / l * side * (wv + 10);
            double r = wv * 1.6 + 5;
            int segs = 7;
            for (int k = 0; k < segs; k++) {
                double a0 = Math.PI * (0.15 + 0.7 * k / segs), a1 = Math.PI * (0.15 + 0.7 * (k + 1) / segs);
                double bx0 = Math.atan2(tz, tx) + (side > 0 ? -Math.PI / 2 : Math.PI / 2);
                double x0 = cx + Math.cos(bx0 + a0 - Math.PI / 2) * r, z0 = cz + Math.sin(bx0 + a0 - Math.PI / 2) * r;
                double x1 = cx + Math.cos(bx0 + a1 - Math.PI / 2) * r, z1 = cz + Math.sin(bx0 + a1 - Math.PI / 2) * r;
                addSeg(x0, z0, x1, z1, p[2], p[2], p[3] * 0.25, p[3] * 0.25, 0, FLAG_OXBOW);
            }
            oxbows++;
        }
    }

    private static double[] mix(double[] a, double[] b, double t) {
        return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t,
                a[3] + (b[3] - a[3]) * t, (a[4] == 1 && b[4] == 1) ? 1 : 0, 0};
    }

    private static List<double[]> resample(List<double[]> pts, double step) {
        List<double[]> out = new ArrayList<>();
        out.add(pts.get(0));
        for (int i = 0; i < pts.size() - 1; i++) {
            double[] a = pts.get(i), b = pts.get(i + 1);
            double l = Math.hypot(b[0] - a[0], b[1] - a[1]);
            int k = Math.max(1, (int) Math.ceil(l / step));
            for (int j = 1; j <= k; j++) {
                double[] m = mix(a, b, j / (double) k);
                if (j == k) m[5] = b[5];
                out.add(m);
            }
        }
        return out;
    }

    private void addSeg(double x0, double z0, double x1, double z1, double s0, double s1, double q0, double q1, double g, int fl) {
        if (ax == null || count == ax.length) grow();
        ax[count] = (float) x0; az[count] = (float) z0; bx[count] = (float) x1; bz[count] = (float) z1;
        sa[count] = (float) s0; sb[count] = (float) s1; qa[count] = (float) q0; qb[count] = (float) q1;
        grad[count] = (float) g; flags[count] = (byte) fl;
        count++;
    }

    private void grow() {
        int c = ax == null ? 256 : ax.length * 2;
        ax = ax == null ? new float[c] : Arrays.copyOf(ax, c);
        az = az == null ? new float[c] : Arrays.copyOf(az, c);
        bx = bx == null ? new float[c] : Arrays.copyOf(bx, c);
        bz = bz == null ? new float[c] : Arrays.copyOf(bz, c);
        sa = sa == null ? new float[c] : Arrays.copyOf(sa, c);
        sb = sb == null ? new float[c] : Arrays.copyOf(sb, c);
        qa = qa == null ? new float[c] : Arrays.copyOf(qa, c);
        qb = qb == null ? new float[c] : Arrays.copyOf(qb, c);
        grad = grad == null ? new float[c] : Arrays.copyOf(grad, c);
        flags = flags == null ? new byte[c] : Arrays.copyOf(flags, c);
    }

    private void buildIndex(DomainSim sim) {
        gx0 = Math.floorDiv(sim.ox - 64, BUCKET);
        gz0 = Math.floorDiv(sim.oz - 64, BUCKET);
        gw = (sim.w * DomainSim.RES + 128) / BUCKET + 2;
        gh = (sim.h * DomainSim.RES + 128) / BUCKET + 2;
        int nb = gw * gh;
        int[] counts = new int[nb + 1];
        double pad = 64;
        for (int pass = 0; pass < 2; pass++) {
            int[] fill = pass == 1 ? new int[nb] : null;
            for (int s = 0; s < count; s++) {
                int i0 = Math.max(0, Math.floorDiv((int) (Math.min(ax[s], bx[s]) - pad), BUCKET) - gx0);
                int i1 = Math.min(gw - 1, Math.floorDiv((int) (Math.max(ax[s], bx[s]) + pad), BUCKET) - gx0);
                int j0 = Math.max(0, Math.floorDiv((int) (Math.min(az[s], bz[s]) - pad), BUCKET) - gz0);
                int j1 = Math.min(gh - 1, Math.floorDiv((int) (Math.max(az[s], bz[s]) + pad), BUCKET) - gz0);
                for (int j = j0; j <= j1; j++) {
                    for (int i = i0; i <= i1; i++) {
                        int b = j * gw + i;
                        if (pass == 0) counts[b + 1]++;
                        else bucketItems[bucketStart[b] + fill[b]++] = s;
                    }
                }
            }
            if (pass == 0) {
                for (int b = 0; b < nb; b++) counts[b + 1] += counts[b];
                bucketStart = counts;
                bucketItems = new int[counts[nb]];
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ queries

    /** Result of a channel query at a column. */
    public static final class Hit {
        public double dist;     // distance to channel centreline
        public double surface;  // interpolated water surface (whole blocks, non-increasing downstream)
        public double q;        // discharge
        public double width, depth;
        public double grad;
        public int flags;
        public double t;        // parameter along segment
        public int segment;
        public boolean found;
    }

    /**
     * Finds the channel whose banks are closest to containing (x,z) within maxDist of its bank. Among overlapping
     * channels (confluences), the lowest water surface wins so water never spills over a lower neighbour.
     */
    public void query(double x, double z, double maxDist, Hit out) {
        out.found = false;
        if (count == 0) return;
        int bi = Math.floorDiv((int) Math.floor(x), BUCKET) - gx0, bj = Math.floorDiv((int) Math.floor(z), BUCKET) - gz0;
        if (bi < 0 || bj < 0 || bi >= gw || bj >= gh) return;
        int b = bj * gw + bi;
        double bestScore = Double.MAX_VALUE;
        double[] tt = new double[1];
        for (int p = bucketStart[b]; p < bucketStart[b + 1]; p++) {
            int s = bucketItems[p];
            double d = MathUtil.distToSegment(x, z, ax[s], az[s], bx[s], bz[s], tt);
            double t = tt[0];
            double q = qa[s] + (qb[s] - qa[s]) * t;
            double wv = (flags[s] & FLAG_OXBOW) != 0 ? 3 + q * 0 : HydroRules.width(q);
            if ((flags[s] & FLAG_OXBOW) != 0) wv = Math.max(4, HydroRules.width(q * 4) * 0.9);
            double edge = d - wv * 0.5;
            if (edge > maxDist) continue;
            double surf = sa[s] + (sb[s] - sa[s]) * t;
            // Score: inside-channel first, then lower surface; otherwise nearest bank.
            double score = edge <= 0 ? -1e6 + surf : edge;
            if (score < bestScore) {
                bestScore = score;
                out.found = true;
                out.dist = d;
                out.surface = Math.floor(surf + 1e-4);
                out.q = q;
                out.width = wv;
                out.depth = (flags[s] & FLAG_OXBOW) != 0 ? 1.5 : HydroRules.depth(q);
                out.grad = grad[s];
                out.flags = flags[s];
                out.t = t;
                out.segment = s;
            }
        }
    }

    /** Visitor over every channel segment within maxDist (of its bank) of (x,z). */
    public interface SegmentVisitor {
        void visit(double dist, double surface, double q, double width, double depth, double grad, int flags);
    }

    public void forEachNear(double x, double z, double maxDist, SegmentVisitor v) {
        if (count == 0) return;
        int bi = Math.floorDiv((int) Math.floor(x), BUCKET) - gx0, bj = Math.floorDiv((int) Math.floor(z), BUCKET) - gz0;
        if (bi < 0 || bj < 0 || bi >= gw || bj >= gh) return;
        int b = bj * gw + bi;
        double[] tt = new double[1];
        for (int p = bucketStart[b]; p < bucketStart[b + 1]; p++) {
            int s = bucketItems[p];
            double d = MathUtil.distToSegment(x, z, ax[s], az[s], bx[s], bz[s], tt);
            double t = tt[0];
            double q = qa[s] + (qb[s] - qa[s]) * t;
            boolean ox = (flags[s] & FLAG_OXBOW) != 0;
            double wv = ox ? Math.max(4, HydroRules.width(q * 4) * 0.9) : HydroRules.width(q);
            if (d - wv * 0.5 > maxDist) continue;
            double surf = Math.floor(sa[s] + (sb[s] - sa[s]) * t + 1e-4);
            v.visit(d, surf, q, wv, ox ? 1.5 : HydroRules.depth(q), grad[s], flags[s]);
        }
    }

    public void segment(int s, double[] out) {
        out[0] = ax[s]; out[1] = az[s]; out[2] = bx[s]; out[3] = bz[s];
        out[4] = sa[s]; out[5] = sb[s]; out[6] = qa[s]; out[7] = qb[s];
    }

    public int flagsOf(int s) {
        return flags[s];
    }
}
