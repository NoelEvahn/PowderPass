package dev.powderpass.core;

import dev.powderpass.core.biome.BiomeClassifier;
import dev.powderpass.core.terrain.TerrainSampler;

public final class Inspect {
    public static void main(String[] a) {
        PowderWorld w = new PowderWorld(Long.parseLong(a[0]), GenSettings.defaults());
        TerrainSampler ts = new TerrainSampler(w);
        Diagnostics d = new Diagnostics(w, ts, new BiomeClassifier(w));
        for (int i = 1; i + 1 < a.length; i += 2) {
            for (String s : d.inspect(Double.parseDouble(a[i]), Double.parseDouble(a[i + 1]))) System.out.println(s);
            System.out.println("--");
        }
    }
}
