package dev.powderpass.core;

import dev.powderpass.core.geo.Geology;
import dev.powderpass.core.hydro.DomainSim;
import dev.powderpass.core.hydro.Domains;

public final class Probe {
    public static void main(String[] a) {
        long seed = Long.parseLong(a[0]);
        PowderWorld w = new PowderWorld(seed, GenSettings.defaults());
        for (int i = 1; i + 1 < a.length; i += 2) {
            double x = Double.parseDouble(a[i]), z = Double.parseDouble(a[i + 1]);
            long id = w.domains.owner(x, z);
            DomainSim s = w.sim(id);
            int gi = (int) Math.round((x - s.ox) / 8.0), gj = (int) Math.round((z - s.oz) / 8.0);
            int k = gj * s.w + gi;
            Geology.GeoSample o = new Geology.GeoSample();
            w.base.sample(x, z, o);
            Domains.Flow f = w.domains.flow(id);
            Domains.Info in = w.domains.info(id);
            System.out.printf("(%.0f,%.0f) dom=%d,%d kind=%d z0=%.1f z=%.1f water=%.1f filled=%.1f C=%.3f sea=%b | flow=%s lvl=%d E=%.1f minH=%.1f coastal=%b inflows=%d%n",
                x, z, (int) (id >> 32), (int) id, s.kind[k], s.z0[k], s.z[k], s.water[k], s.filled(k), o.continental, o.sea, f.type, f.level, in.E, in.minH, in.coastal, s.inflows.size());
            System.out.printf("   lakes=%d breaches=%d keepLarge=%d cut=%d fixed=%d len=%d%n", s.lakeCells, s.breaches, s.rejKeepLarge, s.rejCut, s.rejFixed, s.rejLen);
            for (var inf : s.inflows) System.out.printf("   inflow from %d,%d level %d q %.3g%n", (int) (inf.from >> 32), (int) inf.from, inf.level, inf.q);
        }
    }
}
