package dev.powderpass.core;

import dev.powderpass.core.export.Img;
import dev.powderpass.core.terrain.TerrainSampler;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.stream.IntStream;

/** Developer preview of the final sampler at 1:step. */
public final class PreviewTerrain {
    public static void main(String[] a) throws Exception {
        long seed = Long.parseLong(a[0]);
        int cx = Integer.parseInt(a[1]), cz = Integer.parseInt(a[2]), size = Integer.parseInt(a[3]);
        int step = Integer.parseInt(a[4]);
        PowderWorld world = new PowderWorld(seed, GenSettings.defaults(), 3L << 30, true);
        TerrainSampler ts = new TerrainSampler(world);
        int n = size / step;
        double[] h = new double[n * n];
        double[] wl = new double[n * n];
        byte[] wk = new byte[n * n];
        long t0 = System.nanoTime();
        IntStream.range(0, n).parallel().forEach(j -> {
            TerrainSampler.Column c = new TerrainSampler.Column();
            for (int i = 0; i < n; i++) {
                ts.sample(cx - size / 2.0 + i * step, cz - size / 2.0 + j * step, c);
                h[j * n + i] = Math.floor(c.height);
                wl[j * n + i] = c.waterLevel;
                wk[j * n + i] = c.waterKind;
            }
        });
        System.out.printf("%d columns in %.1fs%n", n * n, (System.nanoTime() - t0) / 1e9);
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
            int k = j * n + i;
            int col = Img.shade(Img.elevationColor(h[k], 63), h, n, n, i, j, step);
            if (!Double.isNaN(wl[k]) && wl[k] - 1 > h[k]) {
                col = switch (wk[k]) {
                    case TerrainSampler.WATER_RIVER -> 0x2050ff;
                    case TerrainSampler.WATER_LAKE -> 0x3070d0;
                    default -> 0x2a5aa8;
                };
            }
            img.setRGB(i, j, col);
        }
        Img.save(img, new File(a[5] + "/terrain_" + seed + "_" + cx + "_" + cz + "_" + step + ".png"));
    }
}
