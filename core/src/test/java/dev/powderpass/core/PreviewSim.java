package dev.powderpass.core;

import dev.powderpass.core.export.Img;
import dev.powderpass.core.hydro.DomainSim;
import dev.powderpass.core.hydro.Domains;
import dev.powderpass.core.hydro.RiverNet;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

/** Developer preview: renders stitched domain simulations (grid resolution) for an area. */
public final class PreviewSim {
    public static void main(String[] a) throws Exception {
        long seed = Long.parseLong(a[0]);
        int cx = Integer.parseInt(a[1]), cz = Integer.parseInt(a[2]), size = Integer.parseInt(a[3]);
        boolean erode = a.length < 6 || !a[5].equals("noerode");
        PowderWorld world = new PowderWorld(seed, GenSettings.defaults(), 2L << 30, erode);
        int step = 8;
        int n = size / step;
        double x0 = cx - size / 2.0, z0 = cz - size / 2.0;
        // Pre-compute all sims in parallel.
        Set<Long> ids = new HashSet<>();
        for (int j = 0; j < n; j += 8) for (int i = 0; i < n; i += 8) ids.add(world.domains.owner(x0 + i * step, z0 + j * step));
        long t0 = System.nanoTime();
        AtomicLong simTime = new AtomicLong();
        ids.parallelStream().forEach(id -> {
            long s = System.nanoTime();
            world.sim(id);
            simTime.addAndGet(System.nanoTime() - s);
        });
        System.out.printf("%d domains, wall %.1fs, mean %.0f ms/domain%n", ids.size(), (System.nanoTime() - t0) / 1e9, simTime.get() / 1e6 / ids.size());
        double[] h = new double[n * n];
        float[] wat = new float[n * n];
        float[] ar = new float[n * n];
        IntStream.range(0, n).parallel().forEach(j -> {
            for (int i = 0; i < n; i++) {
                double x = x0 + i * step, z = z0 + j * step;
                long id = world.domains.owner(x, z);
                DomainSim sim = world.sim(id);
                int gi = (int) Math.round((x - sim.ox) / DomainSim.RES), gj = (int) Math.round((z - sim.oz) / DomainSim.RES);
                gi = Math.max(0, Math.min(sim.w - 1, gi));
                gj = Math.max(0, Math.min(sim.h - 1, gj));
                int k = gj * sim.w + gi;
                h[j * n + i] = sim.z[k];
                wat[j * n + i] = sim.water[k];
                ar[j * n + i] = sim.area[k];
            }
        });
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        RiverNet.Hit hit = new RiverNet.Hit();
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
            int k = j * n + i;
            int col = Img.shade(Img.elevationColor(h[k], 63), h, n, n, i, j, step);
            if (!Float.isNaN(wat[k]) && wat[k] > h[k]) col = 0x3060c0;
            img.setRGB(i, j, col);
        }
        // Draw rivers from the networks.
        for (long id : ids) {
            DomainSim sim = world.sim(id);
            double[] seg = new double[8];
            for (int sgi = 0; sgi < sim.rivers.segmentCount(); sgi++) {
                sim.rivers.segment(sgi, seg);
                double q = seg[6];
                int col = (sim.rivers.flagsOf(sgi) & RiverNet.FLAG_DELTA) != 0 ? 0x00ffff : (q > 1e6 ? 0x1030ff : 0x2a70ff);
                int steps = (int) Math.ceil(Math.hypot(seg[2] - seg[0], seg[3] - seg[1]) / 2);
                for (int t = 0; t <= steps; t++) {
                    double x = seg[0] + (seg[2] - seg[0]) * t / Math.max(1, steps), z = seg[1] + (seg[3] - seg[1]) * t / Math.max(1, steps);
                    int pi = (int) ((x - x0) / step), pj = (int) ((z - z0) / step);
                    if (pi >= 0 && pj >= 0 && pi < n && pj < n) img.setRGB(pi, pj, col);
                }
            }
        }
        double max = -1e9, ero = 0, dep = 0, exS = 0, exP = 0;
        int lakes = 0, glac = 0, br = 0;
        for (long id : ids) {
            DomainSim s = world.sim(id);
            ero += s.erodedVolume; dep += s.depositedVolume; exS += s.exportedSea; exP += s.exportedPass;
            lakes += s.lakeCells; glac += s.glacierCells; br += s.breaches;
        }
        for (double v : h) max = Math.max(max, v);
        System.out.printf("max %.1f eroded %.3g deposited %.3g exportSea %.3g exportPass %.3g lakeCells %d glacierCells %d breaches %d%n",
                max, ero, dep, exS, exP, lakes, glac, br);
        Img.save(img, new File(a[4] + "/sim_" + seed + "_" + cx + "_" + cz + (erode ? "" : "_noerode") + ".png"));
    }
}
