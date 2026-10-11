package dev.powderpass.core.geo;

import dev.powderpass.core.noise.Simplex;
import dev.powderpass.core.util.BoundedCache;
import dev.powderpass.core.util.Hash;
import dev.powderpass.core.util.MathUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Seeded plate field. Plates are cells of a domain-warped jittered Voronoi diagram; each plate has a crust type and
 * a motion vector. Plate boundaries are computed explicitly as clipped Voronoi edge segments (cached per plate), and
 * each edge carries its kinematics: convergence (closing speed along the edge normal) and shear (sliding speed).
 *
 * <p>Every derived quantity is a continuous function of position: boundary effects are evaluated as distances to
 * edge segments (not "distance to second-nearest seed", which jumps at triple junctions), and crust type is a softmax
 * over squared seed distances. This is what keeps mountain belts and coastlines free of seams.
 */
public final class Tectonics {
    public static final double BASE_PLATE_SIZE = 5600.0;
    public static final int MAX_EDGES = 48;

    private final long seed;
    private final long warpSeed;
    private final long clusterSeed;
    private final double cell;
    private final double warpAmp;
    private final BoundedCache<Edge[]> edgeCache = BoundedCache.ofCount(4096);

    public Tectonics(long worldSeed, double horizontalScale) {
        this.seed = Hash.subSeed(worldSeed, "tectonics.plates");
        this.warpSeed = Hash.subSeed(worldSeed, "tectonics.warp");
        this.clusterSeed = Hash.subSeed(worldSeed, "tectonics.cluster");
        this.cell = BASE_PLATE_SIZE * horizontalScale;
        this.warpAmp = 0.20 * cell;
    }

    /** A plate boundary segment between plates lo and hi (canonical order), in warped coordinates. */
    public static final class Edge {
        public final long lo, hi;
        public final double ax, az, bx, bz;
        public final double nx, nz;          // unit normal pointing from lo toward hi
        public final double convergence;     // [-1,1] >0 convergent
        public final double shear;           // [0,1]
        public final double crustLo, crustHi;
        public final double age;             // [0,1] 0 = young/active
        public final double pairRand;        // [0,1] per-pair random

        Edge(long lo, long hi, double ax, double az, double bx, double bz, double nx, double nz,
             double convergence, double shear, double crustLo, double crustHi, double age, double pairRand) {
            this.lo = lo; this.hi = hi; this.ax = ax; this.az = az; this.bx = bx; this.bz = bz;
            this.nx = nx; this.nz = nz; this.convergence = convergence; this.shear = shear;
            this.crustLo = crustLo; this.crustHi = crustHi; this.age = age; this.pairRand = pairRand;
        }
    }

    /** Mutable result holder (one per thread / call site). */
    public static final class Sample {
        public double wx, wz;                 // warped position
        public int ax, az;                    // owning plate cell
        public double crust;                  // softmax-blended crust continentality [-1,1]
        public double nearestDist;            // distance to nearest boundary edge
        public int edgeCount;
        public final Edge[] edges = new Edge[MAX_EDGES];
        public final double[] edgeDist = new double[MAX_EDGES];
        /** Signed distance: positive on the hi-plate side of the edge's normal. */
        public final double[] edgeSigned = new double[MAX_EDGES];
    }

    public double cellSize() {
        return cell;
    }

    private double seedX(int gx, int gz) {
        return (gx + 0.5 + 0.8 * (Hash.unit(seed, gx, gz, 1) - 0.5)) * cell;
    }

    private double seedZ(int gx, int gz) {
        return (gz + 0.5 + 0.8 * (Hash.unit(seed, gx, gz, 2) - 0.5)) * cell;
    }

    public double warpX(double x, double z) {
        return x + warpAmp * Simplex.fbm(warpSeed, x / (cell * 0.55), z / (cell * 0.55), 3, 2.1, 0.5);
    }

    public double warpZ(double x, double z) {
        return z + warpAmp * Simplex.fbm(warpSeed + 77, x / (cell * 0.55), z / (cell * 0.55), 3, 2.1, 0.5);
    }

    /**
     * Samples plate information. Edges within {@code maxEdgeDist} (warped blocks) are reported with distances.
     */
    public void sample(double x, double z, double maxEdgeDist, Sample out) {
        double wx = warpX(x, z), wz = warpZ(x, z);
        out.wx = wx;
        out.wz = wz;
        int cx = MathUtil.fastFloor(wx / cell), cz = MathUtil.fastFloor(wz / cell);
        // Softmax crust blend over squared distance differences (continuous everywhere).
        double minD2 = Double.MAX_VALUE;
        double[] d2s = new double[25];
        int idx = 0;
        int ownX = 0, ownZ = 0;
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                int gx = cx + dx, gz = cz + dz;
                double sx = seedX(gx, gz) - wx, sz = seedZ(gx, gz) - wz;
                double d2 = sx * sx + sz * sz;
                d2s[idx++] = d2;
                if (d2 < minD2) { minD2 = d2; ownX = gx; ownZ = gz; }
            }
        }
        out.ax = ownX;
        out.az = ownZ;
        double tau = 0.16 * cell;
        double wsum = 0, csum = 0;
        idx = 0;
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                double e = (d2s[idx++] - minD2) / (2.0 * cell * tau);
                if (e > 12) continue;
                double w = Math.exp(-e);
                wsum += w;
                csum += w * crust(cx + dx, cz + dz);
            }
        }
        out.crust = csum / wsum;

        // Gather edges of plates in the 5x5 neighbourhood, dedupe by (lo,hi) using the lo-owner rule.
        int n = 0;
        double nearest = Double.MAX_VALUE;
        for (int dz = -2; dz <= 2 && n < MAX_EDGES; dz++) {
            for (int dx = -2; dx <= 2 && n < MAX_EDGES; dx++) {
                int gx = cx + dx, gz = cz + dz;
                long self = Hash.pack(gx, gz);
                for (Edge e : edgesOf(gx, gz)) {
                    if (e.lo != self) continue; // each edge reported once, by its lo plate
                    double d = MathUtil.distToSegment(wx, wz, e.ax, e.az, e.bx, e.bz, null);
                    if (d < nearest) nearest = d;
                    if (d > maxEdgeDist) continue;
                    if (n >= MAX_EDGES) break;
                    out.edges[n] = e;
                    out.edgeDist[n] = d;
                    double side = (wx - e.ax) * e.nx + (wz - e.az) * e.nz;
                    out.edgeSigned[n] = side >= 0 ? d : -d;
                    n++;
                }
            }
        }
        out.edgeCount = n;
        out.nearestDist = nearest;
    }

    /** Clipped Voronoi edges of plate (gx,gz). Cached; pure function of the seed. */
    public Edge[] edgesOf(int gx, int gz) {
        return edgeCache.getOrCompute(Hash.pack(gx, gz), k -> computeEdges(gx, gz));
    }

    private Edge[] computeEdges(int gx, int gz) {
        double jx = seedX(gx, gz), jz = seedZ(gx, gz);
        int R = 3;
        int cnt = (2 * R + 1) * (2 * R + 1) - 1;
        double[] ox = new double[cnt], oz = new double[cnt];
        int[] ogx = new int[cnt], ogz = new int[cnt];
        int m = 0;
        for (int dz = -R; dz <= R; dz++) {
            for (int dx = -R; dx <= R; dx++) {
                if (dx == 0 && dz == 0) continue;
                ogx[m] = gx + dx;
                ogz[m] = gz + dz;
                ox[m] = seedX(gx + dx, gz + dz);
                oz[m] = seedZ(gx + dx, gz + dz);
                m++;
            }
        }
        List<Edge> edges = new ArrayList<>();
        long self = Hash.pack(gx, gz);
        for (int k = 0; k < m; k++) {
            double kx = ox[k], kz = oz[k];
            double mx = (jx + kx) * 0.5, mz = (jz + kz) * 0.5;
            double ddx = kx - jx, ddz = kz - jz;
            double len = Math.hypot(ddx, ddz);
            double nx = ddx / len, nz = ddz / len;
            double tx = -nz, tz = nx;
            // Parametric bisector: P(t) = M + t*T, clip to points closer to j than to every other seed.
            double tMin = -4 * cell, tMax = 4 * cell;
            for (int q = 0; q < m && tMin < tMax; q++) {
                if (q == k) continue;
                // Half-plane: |P-j|^2 <= |P-q|^2  <=>  2 P.(q-j) <= |q|^2 - |j|^2
                double qx = ox[q] - jx, qz = oz[q] - jz;
                double rhs = 0.5 * (qx * qx + qz * qz); // using coordinates relative to j
                double px = mx - jx, pz = mz - jz;
                double a = px * qx + pz * qz;           // value at t=0
                double b = tx * qx + tz * qz;           // derivative
                // need a + b t <= rhs
                if (Math.abs(b) < 1e-12) {
                    if (a > rhs) { tMin = 1; tMax = 0; }
                } else if (b > 0) {
                    tMax = Math.min(tMax, (rhs - a) / b);
                } else {
                    tMin = Math.max(tMin, (rhs - a) / b);
                }
            }
            if (tMax - tMin < 1e-6) continue;
            long other = Hash.pack(ogx[k], ogz[k]);
            long lo = Math.min(self, other), hi = Math.max(self, other);
            double ax = mx + tMin * tx, az = mz + tMin * tz, bx = mx + tMax * tx, bz = mz + tMax * tz;
            // Canonical orientation: normal from lo plate to hi plate; endpoints ordered canonically.
            double cnx = self == lo ? nx : -nx, cnz = self == lo ? nz : -nz;
            if (ax > bx || (ax == bx && az > bz)) { double t = ax; ax = bx; bx = t; t = az; az = bz; bz = t; }
            int lox = Hash.unpackX(lo), loz = Hash.unpackZ(lo), hix = Hash.unpackX(hi), hiz = Hash.unpackZ(hi);
            double rx = velX(lox, loz) - velX(hix, hiz), rz = velZ(lox, loz) - velZ(hix, hiz);
            double conv = MathUtil.clamp((rx * cnx + rz * cnz) * 0.5, -1, 1);
            double shear = MathUtil.clamp(Math.abs(rx * cnz - rz * cnx) * 0.5, 0, 1);
            long ph = Hash.hash(seed ^ 0x5EEDL, lo, hi);
            edges.add(new Edge(lo, hi, ax, az, bx, bz, cnx, cnz, conv, shear,
                    crust(lox, loz), crust(hix, hiz), Hash.unit(ph), Hash.unit(Hash.mix(ph))));
        }
        return edges.toArray(new Edge[0]);
    }

    /** Plate crust continentality in [-1,1]; clustered by a very low frequency field so continents group. */
    public double crust(int gx, int gz) {
        double px = (gx + 0.5) * cell, pz = (gz + 0.5) * cell;
        double cluster = Simplex.fbm(clusterSeed, px / (cell * 4.0), pz / (cell * 4.0), 2, 2.0, 0.5);
        double r = Hash.unit(seed, gx, gz, 3);
        return MathUtil.clamp((r - 0.36) * 1.6 + cluster * 0.9, -1, 1);
    }

    public double velX(int gx, int gz) {
        double a = Hash.unit(seed, gx, gz, 4) * Math.PI * 2;
        double s = 0.35 + 0.65 * Hash.unit(seed, gx, gz, 5);
        return Math.cos(a) * s;
    }

    public double velZ(int gx, int gz) {
        double a = Hash.unit(seed, gx, gz, 4) * Math.PI * 2;
        double s = 0.35 + 0.65 * Hash.unit(seed, gx, gz, 5);
        return Math.sin(a) * s;
    }
}
