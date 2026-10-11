package dev.powderpass.core;

import dev.powderpass.core.hydro.Domains;
import dev.powderpass.core.util.Hash;

public final class BasinWhy {
    public static void main(String[] a) {
        PowderWorld w = new PowderWorld(Long.parseLong(a[0]), GenSettings.defaults());
        int r = Integer.parseInt(a[1]);
        int basins = 0, withLowerNoPass = 0;
        for (int gz = -r; gz <= r; gz++) for (int gx = -r; gx <= r; gx++) {
            long id = Hash.pack(gx, gz);
            Domains.Flow f = w.domains.flow(id);
            if (f.type != Domains.FlowType.BASIN) continue;
            basins++;
            Domains.Info in = w.domains.info(id);
            int lower = 0, lowerNoPass = 0;
            StringBuilder sb = new StringBuilder();
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) continue;
                long n = Hash.pack(gx + dx, gz + dz);
                Domains.Info ni = w.domains.info(n);
                boolean pass = w.domains.pass(id, n) != null;
                sb.append(String.format(" [%d,%d E=%.1f %s%s]", dx, dz, ni.E, pass ? "P" : "-", ni.coastal ? "C" : ""));
                if (ni.E < in.E) { lower++; if (!pass) lowerNoPass++; }
            }
            if (lowerNoPass > 0 && lowerNoPass == lower) withLowerNoPass++;
            if (basins <= 6) System.out.printf("basin %d,%d E=%.1f minH=%.0f%s%n", gx, gz, in.E, in.minH, sb);
        }
        System.out.printf("basins %d, of which all lower neighbours lacked a pass: %d%n", basins, withLowerNoPass);
    }
}
