package dev.powderpass.core;

import dev.powderpass.core.anomaly.Anomaly;
import dev.powderpass.core.volcano.VolcanoSite;

/** Lists volcano and anomaly metadata in an area (for showcase selection). */
public final class FindSites {
    public static void main(String[] a) {
        long seed = Long.parseLong(a[0]);
        int r = Integer.parseInt(a[1]);
        PowderWorld w = new PowderWorld(seed, GenSettings.defaults());
        for (VolcanoSite v : w.volcanoes.sitesIn(-r, -r, r, r))
            System.out.printf("volcano %s %s %.0f %.0f R=%.0f H=%.0f canErupt=%b%n", v.type(), v.origin(), v.x(), v.z(), v.radius(), v.height(), v.canErupt());
        for (Anomaly an : w.anomalies.sitesIn(-r, -r, r, r))
            System.out.printf("anomaly %s %.0f %.0f R=%.0f depth=%.0f level=%.0f wet=%b%n", an.type().key, an.x(), an.z(), an.radius(), an.depth(), an.level(), an.wet());
    }
}
