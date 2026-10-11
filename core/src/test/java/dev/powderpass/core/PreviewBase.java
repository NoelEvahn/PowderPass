package dev.powderpass.core;

import dev.powderpass.core.export.Img;
import dev.powderpass.core.geo.Geology;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.stream.IntStream;

/** Developer preview of base (pre-erosion) elevation. Run: java -cp ... dev.powderpass.core.PreviewBase seed size step out */
public final class PreviewBase {
    public static void main(String[] a) throws Exception {
        long seed = Long.parseLong(a[0]);
        int size = Integer.parseInt(a[1]);
        int step = Integer.parseInt(a[2]);
        GenSettings s = GenSettings.defaults();
        Geology g = new Geology(seed, s);
        int n = size / step;
        double[] h = new double[n * n];
        int[] prov = new int[n * n];
        long t0 = System.nanoTime();
        IntStream.range(0, n).parallel().forEach(j -> {
            Geology.GeoSample o = new Geology.GeoSample();
            for (int i = 0; i < n; i++) {
                g.sample(-size / 2.0 + i * step, -size / 2.0 + j * step, o);
                h[j * n + i] = o.h0;
                prov[j * n + i] = o.province.ordinal();
            }
        });
        System.out.printf("samples %d in %.1f ms%n", n * n, (System.nanoTime() - t0) / 1e6);
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        BufferedImage pi = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        double max = -1e9; int land = 0;
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
            double v = h[j * n + i];
            max = Math.max(max, v);
            if (v >= s.seaLevel()) land++;
            img.setRGB(i, j, Img.shade(Img.elevationColor(v, s.seaLevel()), h, n, n, i, j, step));
            pi.setRGB(i, j, Img.category(prov[j * n + i]));
        }
        System.out.printf("max %.1f land %.2f%n", max, land / (double) (n * n));
        Img.save(img, new File(a[3] + "/base_" + seed + ".png"));
        Img.save(pi, new File(a[3] + "/prov_" + seed + ".png"));
    }
}
