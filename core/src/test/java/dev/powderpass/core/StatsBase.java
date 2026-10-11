package dev.powderpass.core;

import dev.powderpass.core.export.MapExport;
import dev.powderpass.core.geo.Geology;

import java.util.stream.IntStream;

/** Landform proportions on the pre-erosion surface over large areas (fast tuning aid). */
public final class StatsBase {
    public static void main(String[] a) {
        int size = Integer.parseInt(a[0]), step = Integer.parseInt(a[1]);
        for (int si = 2; si < a.length; si++) {
            long seed = Long.parseLong(a[si]);
            PowderWorld w = new PowderWorld(seed, GenSettings.defaults());
            int n = size / step;
            double[] h = new double[n * n];
            int[] prov = new int[n * n], ov = new int[n * n];
            byte[] wk = new byte[n * n];
            double[] water = new double[n * n];
            IntStream.range(0, n).parallel().forEach(j -> {
                Geology.GeoSample o = new Geology.GeoSample();
                for (int i = 0; i < n; i++) {
                    int k = j * n + i;
                    w.base.sample(-size / 2.0 + i * step, -size / 2.0 + j * step, o);
                    h[k] = o.h0;
                    prov[k] = o.province.ordinal();
                    ov[k] = o.overlay;
                    water[k] = Double.NaN;
                }
            });
            var st = MapExport.classify(h, n, step, 63, prov, ov, wk, water);
            double max = 0; int above500 = 0, above600 = 0, land = 0;
            for (double v : h) { max = Math.max(max, v); if (v > 500) above500++; if (v > 600) above600++; if (v >= 63) land++; }
            System.out.printf("seed %d land %.2f max %.0f >500 %.4f >600 %.5f %s%n", seed, land / (double) h.length, max, above500 / (double) land, above600 / (double) land, st);
        }
    }
}
