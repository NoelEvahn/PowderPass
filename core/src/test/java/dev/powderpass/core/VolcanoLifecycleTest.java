package dev.powderpass.core;

import dev.powderpass.core.terrain.TerrainSampler;
import dev.powderpass.core.volcano.Lifecycle;
import dev.powderpass.core.volcano.VolcanoChannels;
import dev.powderpass.core.volcano.VolcanoSite;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VolcanoLifecycleTest {
    static VolcanoSite eruptive(PowderWorld w) {
        List<VolcanoSite> sites = w.volcanoes.sitesIn(-12000, -12000, 12000, 12000);
        for (VolcanoSite v : sites) if (v.canErupt() && v.type() == VolcanoSite.Type.STRATO) return v;
        for (VolcanoSite v : sites) if (v.canErupt()) return v;
        throw new AssertionError("no eruptive volcano in test area");
    }

    @Test
    void fullCycleAndNoCatchUp() {
        PowderWorld w = TestWorlds.world(TestWorlds.SEED_A);
        VolcanoSite v = eruptive(w);
        Lifecycle.Params p = new Lifecycle.Params();
        Lifecycle.State s = Lifecycle.initial(v, p);
        assertEquals(Lifecycle.Phase.DORMANT, s.phase);
        // Unloaded time never reaches advance(): a huge real-time gap changes nothing.
        Lifecycle.State copy = Lifecycle.initial(v, p);
        assertEquals(s.elapsed, copy.elapsed);
        // Loaded ticks: dormant -> unrest.
        long guard = 0;
        while (s.phase == Lifecycle.Phase.DORMANT && guard++ < 1_000_000) Lifecycle.advance(s, 1000, p, true);
        assertEquals(Lifecycle.Phase.UNREST, s.phase);
        // No eruption slot: stays restless.
        for (int i = 0; i < 100; i++) Lifecycle.advance(s, 1000, p, false);
        assertEquals(Lifecycle.Phase.UNREST, s.phase);
        Lifecycle.advance(s, 1, p, true);
        assertEquals(Lifecycle.Phase.ERUPTION, s.phase);
        assertEquals(p.lavaBudget, s.budgetLeft);
        s.addLava(Lifecycle.pack(10, 70, -20));
        while (s.phase == Lifecycle.Phase.ERUPTION) Lifecycle.advance(s, 500, p, true);
        assertEquals(Lifecycle.Phase.COOLING, s.phase);
        // Cooling does not finish while tracked lava remains.
        for (int i = 0; i < 100; i++) Lifecycle.advance(s, 1000, p, true);
        assertEquals(Lifecycle.Phase.COOLING, s.phase);
        s.lavaCount = 0;
        Lifecycle.advance(s, 1, p, true);
        assertEquals(Lifecycle.Phase.DORMANT, s.phase);
        assertEquals(1, s.cycle);
    }

    @Test
    void packRoundTrip() {
        int[][] pts = {{0, 0, 0}, {-1, -64, -1}, {29_999_000, 700, -29_999_000}, {-123456, 200, 654321}};
        for (int[] p : pts) {
            long k = Lifecycle.pack(p[0], p[1], p[2]);
            assertEquals(p[0], Lifecycle.unpackX(k));
            assertEquals(p[1], Lifecycle.unpackY(k));
            assertEquals(p[2], Lifecycle.unpackZ(k));
        }
    }

    @Test
    void lavaPathDescendsAndStaysUnderCap() {
        PowderWorld w = TestWorlds.world(TestWorlds.SEED_A);
        VolcanoSite v = eruptive(w);
        VolcanoChannels vc = new TerrainSampler(w).volcanoChannels();
        VolcanoChannels.Geometry g = vc.geometry(v);
        assertTrue(g.channels().length > 0);
        for (VolcanoChannels.Channel c : g.channels()) {
            for (int i = 1; i < c.size(); i++) assertTrue(c.floor()[i] <= c.floor()[i - 1], "channel floor must not rise");
        }
        Lifecycle.Target t0 = Lifecycle.target(g, 0);
        assertNotNull(t0);
        int count = 0;
        for (int k = 0; k < 10_000; k++) {
            Lifecycle.Target t = Lifecycle.target(g, k);
            if (t == null) break;
            if (t.x() == Integer.MIN_VALUE) continue;
            assertTrue(t.y() <= w.settings.terrainCap(), "lava above cap");
            count++;
        }
        assertTrue(count > 3);
        assertNull(Lifecycle.target(g, 100_000));
        System.out.printf("volcano %s at %.0f,%.0f: %d channels, %d release points, vent y %d%n", v.type(), v.x(), v.z(), g.channels().length, count, g.ventY());
    }
}
