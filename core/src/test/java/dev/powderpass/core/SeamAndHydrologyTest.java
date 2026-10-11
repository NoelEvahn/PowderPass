package dev.powderpass.core;

import dev.powderpass.core.hydro.DomainSim;
import dev.powderpass.core.hydro.Domains;
import dev.powderpass.core.hydro.RiverNet;
import dev.powderpass.core.terrain.ColumnGrid;
import dev.powderpass.core.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Borders between simulation domains and chunks, drainage-graph validity, and water containment. */
class SeamAndHydrologyTest {
    static final int AREA = 1536;

    /**
     * Height steps between adjacent columns that straddle a domain border must not be larger than the steps found
     * between ordinary adjacent columns (same terrain type), i.e. borders are not visible as cliffs.
     */
    @Test
    void domainBordersAreSeamless() {
        PowderWorld w = TestWorlds.world(TestWorlds.SEED_A);
        TerrainSampler ts = new TerrainSampler(w);
        int n = AREA / 2;
        double[] h = new double[n * n];
        long[] owner = new long[n * n];
        TerrainSampler.Column c = new TerrainSampler.Column();
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
            ts.sample(-AREA / 2.0 + i * 2, -AREA / 2.0 + j * 2, c);
            h[j * n + i] = c.height;
            owner[j * n + i] = c.domain;
        }
        List<Double> border = new ArrayList<>(), interior = new ArrayList<>();
        for (int j = 0; j < n; j++) for (int i = 0; i + 1 < n; i++) {
            int k = j * n + i;
            double d = Math.abs(h[k + 1] - h[k]) / 2.0;
            (owner[k] != owner[k + 1] ? border : interior).add(d);
        }
        double b99 = pct(border, 0.99), i99 = pct(interior, 0.99), bMean = mean(border), iMean = mean(interior);
        System.out.printf("seam: border pairs %d mean %.3f p99 %.3f | interior mean %.3f p99 %.3f%n", border.size(), bMean, b99, iMean, i99);
        assertTrue(border.size() > 100, "test area must cross borders");
        assertTrue(b99 <= Math.max(1.5, i99 * 1.5), "border slope p99 " + b99 + " vs interior " + i99);
    }

    /** Halo columns of neighbouring chunks agree exactly with the owning chunk's columns. */
    @Test
    void chunkBordersAgree() {
        PowderWorld w = TestWorlds.world(TestWorlds.SEED_A);
        TerrainSampler ts = new TerrainSampler(w);
        for (int cx = -3; cx < 3; cx++) {
            ColumnGrid a = new ColumnGrid(ts, cx, 2), b = new ColumnGrid(ts, cx + 1, 2);
            for (int lz = 0; lz < 16; lz++) {
                // a's column at local x=15 equals b's halo at x=-1 and vice versa (raw height and water).
                int ka = a.index(15, lz), kb = b.index(-1, lz);
                assertEquals(a.cols[ka].height, b.cols[kb].height, 0.0);
                assertEquals(a.waterTop[ka], b.waterTop[kb]);
                assertEquals(a.rawTop[ka], b.rawTop[kb]);
            }
        }
    }

    /** Receivers are acyclic, every routed cell reaches an outlet, and routed water surfaces strictly descend. */
    @Test
    void drainageGraphsAcyclic() {
        PowderWorld w = TestWorlds.world(TestWorlds.SEED_A);
        Set<Long> ids = new HashSet<>();
        for (int z = -1200; z <= 1200; z += 300) for (int x = -1200; x <= 1200; x += 300) ids.add(w.domains.owner(x, z));
        int domains = 0, basins = 0;
        for (long id : ids) {
            DomainSim s = w.sim(id);
            domains++;
            int n = s.w * s.h;
            for (int k = 0; k < n; k++) {
                if (!s.inside(k) || s.kind[k] == DomainSim.ISO) continue;
                int c = k, steps = 0;
                while (s.rec[c] >= 0) {
                    int r = s.rec[c];
                    assertTrue(s.filled(r) < s.filled(c), "routed surface must strictly descend");
                    c = r;
                    assertTrue(++steps <= n, "cycle in receivers");
                }
            }
            // Domain graph: strictly decreasing representative elevation along the downstream chain.
            Domains.Flow f = w.domains.flow(id);
            long cur = id;
            int hops = 0;
            while (f.type == Domains.FlowType.DOWN) {
                double[] ka = w.domains.key(cur), kb = w.domains.key(f.down);
                boolean lowerKey = kb[0] < ka[0] || (kb[0] == ka[0] && (kb[1] < ka[1] || (kb[1] == ka[1] && f.down < cur)));
                assertTrue(lowerKey, "domain graph must strictly descend in routing key");
                assertTrue(w.domains.flow(f.down).level <= f.level, "outlet levels must not rise downstream");
                cur = f.down;
                f = w.domains.flow(cur);
                assertTrue(++hops < 10000, "domain cycle");
            }
            if (f.type == Domains.FlowType.BASIN) basins++;
        }
        System.out.printf("drainage: %d domains checked, %d chains end in terminal basins%n", domains, basins);
    }

    /** River water surfaces never rise downstream within a polyline, and match exactly at canonical passes. */
    @Test
    void riversDescendAndMatchAtPasses() {
        PowderWorld w = TestWorlds.world(TestWorlds.SEED_A);
        Set<Long> ids = new HashSet<>();
        for (int z = -1500; z <= 1500; z += 250) for (int x = -1500; x <= 1500; x += 250) ids.add(w.domains.owner(x, z));
        int passesChecked = 0, segs = 0;
        double[] seg = new double[8];
        for (long id : ids) {
            DomainSim s = w.sim(id);
            for (int i = 0; i < s.rivers.segmentCount(); i++) {
                s.rivers.segment(i, seg);
                if ((s.rivers.flagsOf(i) & RiverNet.FLAG_OXBOW) != 0) continue;
                assertTrue(seg[5] <= seg[4] + 1e-6, "segment surface rises downstream");
                segs++;
            }
            Domains.Flow f = w.domains.flow(id);
            if (f.type == Domains.FlowType.DOWN) {
                // Both sides see the same canonical pass level.
                DomainSim down = w.sim(f.down);
                boolean found = false;
                for (Domains.Inflow in : down.inflows) {
                    if (in.from == id) {
                        assertEquals(f.level, in.level);
                        assertEquals(f.outPass.x, in.pass.x, 0.0);
                        found = true;
                    }
                }
                assertTrue(found, "downstream domain must list the inflow");
                passesChecked++;
            }
        }
        System.out.printf("rivers: %d segments, %d passes checked%n", segs, passesChecked);
        assertTrue(segs > 50);
    }

    /** No column ever exceeds the cap; water is always contained by adjacent terrain at its level. */
    @Test
    void capAndWaterContainment() {
        PowderWorld w = TestWorlds.world(TestWorlds.SEED_A);
        TerrainSampler ts = new TerrainSampler(w);
        int leaks = 0, waterCols = 0;
        // Chunks that contain river segments and lakes (sampled from the simulated networks).
        java.util.LinkedHashSet<Long> chunks = new java.util.LinkedHashSet<>();
        double[] seg = new double[8];
        for (int zz = -1200; zz <= 1200; zz += 600) for (int xx = -1200; xx <= 1200; xx += 600) {
            DomainSim s = w.sim(w.domains.owner(xx, zz));
            for (int i = 0; i < s.rivers.segmentCount(); i += 97) {
                s.rivers.segment(i, seg);
                chunks.add(dev.powderpass.core.util.Hash.pack(Math.floorDiv((int) seg[0], 16), Math.floorDiv((int) seg[1], 16)));
            }
            for (int k = 0; k < s.w * s.h; k += 211) {
                if (!Float.isNaN(s.water[k]) && s.kind[k] != DomainSim.SEA)
                    chunks.add(dev.powderpass.core.util.Hash.pack(Math.floorDiv((int) s.cellX(k % s.w), 16), Math.floorDiv((int) s.cellZ(k / s.w), 16)));
            }
        }
        for (long key : chunks) {
            int cx = dev.powderpass.core.util.Hash.unpackX(key), cz = dev.powderpass.core.util.Hash.unpackZ(key);
            {
                {
                ColumnGrid g = new ColumnGrid(ts, cx, cz);
                for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
                    int k = g.index(lx, lz);
                    assertTrue(g.top[k] <= w.settings.terrainCap(), "terrain above cap");
                    if (g.waterTop[k] == ColumnGrid.NO_WATER) continue;
                    waterCols++;
                    int wt = g.waterTop[k];
                    int[][] d4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                    for (int[] d : d4) {
                        int nk = k + d[1] * ColumnGrid.SIZE + d[0];
                        boolean ok = g.waterTop[nk] != ColumnGrid.NO_WATER || g.top[nk] >= wt;
                        if (!ok) leaks++;
                    }
                }
                }
            }
        }
        System.out.printf("containment: %d chunks%n", chunks.size());
        assertTrue(waterCols > 500, "test must cover water");
        System.out.printf("containment: %d water columns, %d exposed sides%n", waterCols, leaks);
        assertEquals(0, leaks);
    }

    static double pct(List<Double> v, double p) {
        double[] a = v.stream().mapToDouble(Double::doubleValue).toArray();
        Arrays.sort(a);
        return a.length == 0 ? 0 : a[Math.min(a.length - 1, (int) (a.length * p))];
    }

    static double mean(List<Double> v) {
        return v.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }
}
