package dev.powderpass.core;

import dev.powderpass.core.anomaly.Anomaly;
import dev.powderpass.core.biome.BiomeClassifier;
import dev.powderpass.core.terrain.TerrainSampler;
import dev.powderpass.core.volcano.VolcanoSite;

/** Prints spawn and showcase locations for a seed (metadata-based; used to build the showcase list). */
public final class Showcase {
    public static void main(String[] a) {
        long seed = Long.parseLong(a[0]);
        PowderWorld w = new PowderWorld(seed, GenSettings.defaults());
        TerrainSampler ts = new TerrainSampler(w);
        Diagnostics d = new Diagnostics(w, ts, new BiomeClassifier(w));
        long t = System.nanoTime();
        System.out.println("spawn " + d.findSpawn() + String.format(" (%.1fs)", (System.nanoTime() - t) / 1e9));
        for (Diagnostics.Landform lf : Diagnostics.Landform.values()) {
            t = System.nanoTime();
            System.out.println(lf + " " + d.locate(lf, 0, 0, 6000) + String.format(" (%.1fs)", (System.nanoTime() - t) / 1e9));
        }
        VolcanoSite v = d.nearestVolcano(0, 0, true, 32000);
        System.out.println("volcano " + (v == null ? null : v.type() + " " + (int) v.x() + " " + (int) v.z() + " id " + v.id()));
        for (Anomaly.Type ty : Anomaly.Type.values()) {
            Anomaly an = d.nearestAnomaly(0, 0, ty, 64000);
            System.out.println(ty + " " + (an == null ? null : (int) an.x() + " " + (int) an.z() + " r=" + (int) an.radius()));
        }
        for (String s : d.inspect(0, 0)) System.out.println("  " + s);
    }
}
