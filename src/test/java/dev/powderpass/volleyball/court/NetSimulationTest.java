package dev.powderpass.volleyball.court;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NetSimulationTest {

    @Test
    void impactBulgesThenSettles() {
        NetSimulation n = new NetSimulation(16, 4, 5.0, 1.43, 1.0);
        n.impulse(0.2, 1.9, 3.0, 0.5);
        double peak = 0;
        int ticks = 0;
        while (n.active() && ticks < 400) {
            n.step(0.05);
            peak = Math.max(peak, n.displacementAt(0.2, 1.9));
            ticks++;
        }
        assertTrue(peak > 0.05, "net should bulge: " + peak);
        assertTrue(peak <= 0.45);
        assertFalse(n.active(), "net should come to rest");
        assertTrue(ticks < 200, "settles in under 10 s: " + ticks);
        assertEquals(0, n.displacement(0, 2), "posts stay fixed");
    }
}
