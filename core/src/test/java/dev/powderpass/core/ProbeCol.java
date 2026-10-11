package dev.powderpass.core;

import dev.powderpass.core.terrain.TerrainSampler;

public final class ProbeCol {
    public static void main(String[] a) {
        PowderWorld w = new PowderWorld(Long.parseLong(a[0]), GenSettings.defaults());
        TerrainSampler ts = new TerrainSampler(w);
        TerrainSampler.Column c = new TerrainSampler.Column();
        for (int i = 1; i + 1 < a.length; i += 2) {
            double x = Double.parseDouble(a[i]), z = Double.parseDouble(a[i + 1]);
            ts.sample(x, z, c);
            System.out.printf("(%.0f,%.0f) h=%.2f water=%.1f kind=%d bd=%.1f dom=%d,%d riverDist=%.1f w=%.1f q=%.3g grad=%.4f flags=%d ch=%b sed=%.2f ero=%.1f overlay=%d%n",
                x, z, c.height, c.waterLevel, c.waterKind, c.boundaryDist, (int) (c.domain >> 32), (int) c.domain, c.riverDist, c.riverWidth, c.riverQ, c.riverGrad, c.riverFlags, c.channel, c.sediment, c.erosionDepth, c.geo.overlay);
        }
    }
}
