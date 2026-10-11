package dev.powderpass.core;

import dev.powderpass.core.terrain.B;
import dev.powderpass.core.terrain.ChunkComposer;
import dev.powderpass.core.terrain.TerrainSampler;

public final class ProbeBlocks {
    public static void main(String[] a) {
        PowderWorld w = new PowderWorld(Long.parseLong(a[0]), GenSettings.defaults());
        ChunkComposer comp = new ChunkComposer(w, -64, 832);
        for (int i = 1; i + 1 < a.length; i += 2) {
            int x = Integer.parseInt(a[i]), z = Integer.parseInt(a[i + 1]);
            ChunkComposer.Result r = comp.compose(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
            int lx = Math.floorMod(x, 16), lz = Math.floorMod(z, 16);
            int top = r.columnMax[lz * 16 + lx];
            StringBuilder sb = new StringBuilder();
            for (int y = top; y > top - 8; y--) sb.append(y).append('=').append(B.byOrdinal(comp.blockAt(r, lx, lz, y)).name()).append(' ');
            TerrainSampler.Column c = r.grid.column(lx, lz);
            System.out.printf("(%d,%d) biome=%s lava=%d overlay=%d prov=%s :: %s%n", x, z, r.surfaceBiome[lz * 16 + lx], c.lavaFeature, c.geo.overlay, c.geo.province, sb);
        }
    }
}
