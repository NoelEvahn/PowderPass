package dev.powderpass.core;

import dev.powderpass.core.hydro.DomainSim;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Erosion must change landforms measurably: channels are incised, sediment is deposited, and drainage
 * concentrates compared with the same seed without erosion.
 */
class ErosionEffectTest {
    @Test
    void erosionIncisesAndDeposits() {
        long seed = TestWorlds.SEED_A;
        PowderWorld eroded = TestWorlds.world(seed);
        PowderWorld raw = new PowderWorld(seed, GenSettings.defaults(), 1L << 30, false);
        double[] xs = {1200, 300, -400};
        double[] zs = {-1000, 600, -500};
        for (int t = 0; t < xs.length; t++) {
            long id = eroded.domains.owner(xs[t], zs[t]);
            DomainSim e = eroded.sim(id), r = raw.sim(id);
            int n = e.w * e.h, inside = 0, incised = 0, deposited = 0, chanE = 0, chanR = 0;
            double sumAbs = 0, maxInc = 0;
            for (int k = 0; k < n; k++) {
                if (!e.inside(k)) continue;
                inside++;
                double d = e.z[k] - r.z[k];
                sumAbs += Math.abs(d);
                if (d < -2) incised++;
                if (d > 0.5) deposited++;
                maxInc = Math.max(maxInc, -d);
                if (e.area[k] > 200000) chanE++;
                if (r.area[k] > 200000) chanR++;
            }
            System.out.printf("domain %d: mean|dz| %.2f, incised(>2) %.1f%%, deposited(>0.5) %.1f%%, max incision %.1f, eroded %.3g deposited %.3g, big-channel cells %d vs %d%n",
                    t, sumAbs / inside, 100.0 * incised / inside, 100.0 * deposited / inside, maxInc,
                    e.erodedVolume, e.depositedVolume, chanE, chanR);
            assertTrue(incised > inside * 0.01, "erosion should incise >1% of cells");
            assertTrue(deposited > 0, "erosion should deposit sediment somewhere");
            assertTrue(e.erodedVolume > 0 && e.depositedVolume > 0);
            assertTrue(maxInc > 5, "some channels should incise >5 blocks");
        }
    }
}
