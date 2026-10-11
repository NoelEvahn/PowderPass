package dev.powderpass.core.export;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/** Tiny raster helpers for diagnostic maps (developer tooling, not used at runtime by the game). */
public final class Img {
    private Img() {}

    public static void save(BufferedImage img, File f) throws IOException {
        f.getParentFile().mkdirs();
        ImageIO.write(img, "png", f);
    }

    public static int rgb(double r, double g, double b) {
        int ri = (int) Math.max(0, Math.min(255, r)), gi = (int) Math.max(0, Math.min(255, g)), bi = (int) Math.max(0, Math.min(255, b));
        return (ri << 16) | (gi << 8) | bi;
    }

    /** Hypsometric tint for absolute block elevation (sea level aware). */
    public static int elevationColor(double h, int sea) {
        if (h < sea) {
            double t = Math.max(0, Math.min(1, (sea - h) / 60.0));
            return rgb(70 - 50 * t, 130 - 80 * t, 200 - 70 * t);
        }
        double[][] stops = {
                {0, 92, 150, 80}, {25, 120, 170, 90}, {70, 170, 180, 100}, {150, 190, 160, 110},
                {260, 150, 120, 90}, {380, 130, 115, 105}, {500, 200, 200, 200}, {640, 255, 255, 255}};
        double e = h - sea;
        for (int i = 0; i < stops.length - 1; i++) {
            if (e <= stops[i + 1][0]) {
                double t = (e - stops[i][0]) / (stops[i + 1][0] - stops[i][0]);
                t = Math.max(0, Math.min(1, t));
                return rgb(stops[i][1] + t * (stops[i + 1][1] - stops[i][1]),
                        stops[i][2] + t * (stops[i + 1][2] - stops[i][2]),
                        stops[i][3] + t * (stops[i + 1][3] - stops[i][3]));
            }
        }
        return rgb(255, 255, 255);
    }

    /** Multiplies a color by a Lambertian hillshade computed from a height grid. */
    public static int shade(int color, double[] h, int w, int hgt, int x, int z, double cellSize) {
        int x0 = Math.max(0, x - 1), x1 = Math.min(w - 1, x + 1), z0 = Math.max(0, z - 1), z1 = Math.min(hgt - 1, z + 1);
        double dx = (h[z * w + x1] - h[z * w + x0]) / ((x1 - x0) * cellSize);
        double dz = (h[z1 * w + x] - h[z0 * w + x]) / ((z1 - z0) * cellSize);
        double nx = -dx, ny = 1, nz = -dz;
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        double lx = -0.5, ly = 0.7, lz = -0.5;
        double ll = Math.sqrt(lx * lx + ly * ly + lz * lz);
        double d = (nx * lx + ny * ly + nz * lz) / (len * ll);
        double f = 0.35 + 0.75 * Math.max(0, d);
        int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
        return rgb(r * f, g * f, b * f);
    }

    public static int category(int id) {
        int[] pal = {0x1f77b4, 0xff7f0e, 0x2ca02c, 0xd62728, 0x9467bd, 0x8c564b, 0xe377c2, 0x7f7f7f, 0xbcbd22, 0x17becf,
                0xaec7e8, 0xffbb78, 0x98df8a, 0xff9896, 0xc5b0d5, 0xc49c94, 0xf7b6d2, 0xc7c7c7, 0xdbdb8d, 0x9edae5,
                0x393b79, 0x637939, 0x8c6d31, 0x843c39, 0x7b4173, 0x3182bd, 0xe6550d, 0x31a354, 0x756bb1, 0x636363};
        return pal[Math.floorMod(id, pal.length)];
    }

    public static int ramp(double t) {
        t = Math.max(0, Math.min(1, t));
        // viridis-like
        double r = 68 + t * (253 - 68) * t, g = 1 + t * 230, b = 84 + (1 - t) * 80 - t * 50;
        return rgb(r, g, b);
    }
}
