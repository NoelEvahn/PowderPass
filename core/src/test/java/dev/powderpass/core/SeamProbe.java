package dev.powderpass.core;

import dev.powderpass.core.terrain.TerrainSampler;

/** Lists the worst border steps for diagnosis. */
public final class SeamProbe {
    public static void main(String[] a) {
        PowderWorld w = new PowderWorld(12345, GenSettings.defaults());
        TerrainSampler ts = new TerrainSampler(w);
        TerrainSampler.Column c1 = new TerrainSampler.Column(), c2 = new TerrainSampler.Column();
        int area = 1536;
        java.util.List<String> worst = new java.util.ArrayList<>();
        java.util.List<double[]> rows = new java.util.ArrayList<>();
        for (int z = -area / 2; z < area / 2; z += 2) for (int x = -area / 2; x < area / 2; x += 2) {
            ts.sample(x, z, c1);
            ts.sample(x + 2, z, c2);
            if (c1.domain == c2.domain) continue;
            double d = Math.abs(c1.height - c2.height) / 2;
            if (d > 2.5) rows.add(new double[]{d, x, z, c1.height, c2.height, c1.boundaryDist, c2.boundaryDist, c1.riverDist, c2.riverDist, c1.waterLevel, c2.waterLevel});
        }
        rows.sort((p, q) -> Double.compare(q[0], p[0]));
        for (int i = 0; i < Math.min(25, rows.size()); i++) {
            double[] r = rows.get(i);
            System.out.printf("d=%.2f at %.0f,%.0f h %.1f|%.1f bd %.1f|%.1f river %.1f|%.1f water %.1f|%.1f%n", r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7], r[8], r[9], r[10]);
        }
        System.out.println("count " + rows.size());
    }
}
