package dev.powderpass.core;

import dev.powderpass.core.hydro.Domains;

/** Fraction of domains by flow type and chain length statistics. */
public final class FlowStats {
    public static void main(String[] a) {
        long seed = Long.parseLong(a[0]);
        int r = Integer.parseInt(a[1]);
        PowderWorld w = new PowderWorld(seed, GenSettings.defaults());
        int sea = 0, basin = 0, down = 0, endBasin = 0, n = 0, hopsSum = 0, land = 0;
        for (int gz = -r; gz <= r; gz++) for (int gx = -r; gx <= r; gx++) {
            long id = dev.powderpass.core.util.Hash.pack(gx, gz);
            Domains.Flow f = w.domains.flow(id);
            n++;
            switch (f.type) { case SEA -> sea++; case BASIN -> { basin++; Domains.Info bi = w.domains.info(id); System.out.printf("  basin %d,%d precip %.2f E %.0f key %s%n", gx, gz, bi.precip, bi.E, java.util.Arrays.toString(w.domains.key(id))); } default -> down++; }
            if (f.type == Domains.FlowType.SEA) continue;
            land++;
            int hops = 0; Domains.Flow c = f;
            while (c.type == Domains.FlowType.DOWN && hops < 500) { c = w.domains.flow(c.down); hops++; }
            hopsSum += hops;
            if (c.type == Domains.FlowType.BASIN) endBasin++;
        }
        System.out.printf("domains %d: sea %d, basin %d, down %d; inland chains ending in basins %.2f, mean hops %.1f%n",
            n, sea, basin, down, endBasin / (double) land, hopsSum / (double) land);
    }
}
