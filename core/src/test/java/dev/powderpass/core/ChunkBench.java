package dev.powderpass.core;

import dev.powderpass.core.deco.Decorator;
import dev.powderpass.core.deco.Placements;
import dev.powderpass.core.terrain.ChunkComposer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Chunk generation throughput benchmark: compose + decorate a square of chunks around a point on N threads. */
public final class ChunkBench {
    public static void main(String[] a) throws Exception {
        long seed = Long.parseLong(a[0]);
        int cx0 = Integer.parseInt(a[1]), cz0 = Integer.parseInt(a[2]), radius = Integer.parseInt(a[3]), threads = Integer.parseInt(a[4]);
        PowderWorld w = new PowderWorld(seed, GenSettings.defaults(), 512L << 20, true);
        ChunkComposer comp = new ChunkComposer(w, -64, 832);
        Decorator deco = new Decorator(comp);
        List<int[]> chunks = new ArrayList<>();
        // spiral-ish: sort by distance
        for (int dz = -radius; dz <= radius; dz++) for (int dx = -radius; dx <= radius; dx++) chunks.add(new int[]{cx0 + dx, cz0 + dz});
        chunks.sort((p, q) -> Integer.compare(Math.max(Math.abs(p[0] - cx0), Math.abs(p[1] - cz0)), Math.max(Math.abs(q[0] - cx0), Math.abs(q[1] - cz0))));
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        Runtime rt = Runtime.getRuntime();
        long t0 = System.nanoTime();
        List<Future<long[]>> fs = new ArrayList<>();
        for (int[] c : chunks) {
            fs.add(ex.submit(() -> {
                long s = System.nanoTime();
                ChunkComposer.Result r = comp.compose(c[0], c[1]);
                long m = System.nanoTime();
                Placements p = deco.decorate(c[0], c[1], r);
                long e = System.nanoTime();
                return new long[]{m - s, e - m, p.size};
            }));
        }
        double[] comp_ = new double[fs.size()], dec = new double[fs.size()];
        long placements = 0;
        for (int i = 0; i < fs.size(); i++) {
            long[] v = fs.get(i).get();
            comp_[i] = v[0] / 1e6; dec[i] = v[1] / 1e6; placements += v[2];
        }
        double wall = (System.nanoTime() - t0) / 1e9;
        ex.shutdown();
        System.gc();
        long used = rt.totalMemory() - rt.freeMemory();
        Arrays.sort(comp_); Arrays.sort(dec);
        System.out.printf("chunks %d threads %d wall %.1fs -> %.1f chunks/s (cold, includes domain simulations)%n", chunks.size(), threads, wall, chunks.size() / wall);
        System.out.printf("compose ms: median %.1f p95 %.1f max %.1f | decorate ms: median %.1f p95 %.1f max %.1f | placements %d%n",
            comp_[comp_.length / 2], comp_[(int) (comp_.length * 0.95)], comp_[comp_.length - 1], dec[dec.length / 2], dec[(int) (dec.length * 0.95)], dec[dec.length - 1], placements);
        System.out.printf("sim cache: %d sims, %.1f MB; heap used after GC %.0f MB; %s%n", w.simCache().size(), w.simCache().weight() / 1048576.0, used / 1048576.0, w.domains.cacheStats());
        // Warm pass: regenerate the same chunks with caches populated.
        ex = Executors.newFixedThreadPool(threads);
        t0 = System.nanoTime();
        fs.clear();
        for (int[] c : chunks) fs.add(ex.submit(() -> { ChunkComposer.Result r = comp.compose(c[0], c[1]); deco.decorate(c[0], c[1], r); return new long[0]; }));
        for (Future<long[]> f : fs) f.get();
        wall = (System.nanoTime() - t0) / 1e9;
        ex.shutdown();
        System.out.printf("warm: %.1f chunks/s%n", chunks.size() / wall);
    }
}
