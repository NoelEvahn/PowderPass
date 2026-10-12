package dev.powderpass.course;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RaceTrackerTest {

    /** Start at z=0, gates every 10 blocks alternating left/right of x=0, finish at z=50. All facing +Z (yaw 0). */
    private static List<GateLine> course() {
        return List.of(
                GateLine.centered(0, 64, 0, 0, 6),
                GateLine.centered(3, 64, 10, 0, 4),
                GateLine.centered(-3, 64, 20, 0, 4),
                GateLine.centered(3, 64, 30, 0, 4),
                GateLine.centered(-3, 64, 40, 0, 4),
                GateLine.centered(0, 64, 50, 0, 8));
    }

    @Test
    void gateGeometry() {
        GateLine g = GateLine.centered(0, 64, 10, 0, 4);
        assertEquals(4.0, g.width(), 1e-9);
        GateLine.Crossing c = g.cross(0, 9, 0, 11, 64, 5, 4);
        assertNotNull(c);
        assertTrue(c.inside());
        assertEquals(0.5, c.t(), 1e-9);
        assertNull(g.cross(0, 11, 0, 9, 64, 5, 4), "crossing backwards doesn't count");
        GateLine.Crossing outside = g.cross(4, 9, 4, 11, 64, 5, 4);
        assertNotNull(outside);
        assertFalse(outside.inside());
        assertEquals(2.0, outside.outsideDistance(), 1e-9);
        assertNull(g.cross(20, 9, 20, 11, 64, 5, 4), "far away crossing is ignored");
        assertNull(g.cross(0, 9, 0, 11, 80, 5, 4), "different height is ignored");
    }

    @Test
    void cleanRunWithSubTickPrecision() {
        List<GateLine> gates = course();
        double start = RaceTracker.startCrossing(gates.get(0), 0, -0.25, 0, 0.75, 64, 10, 4);
        assertEquals(9.25, start, 1e-9);
        RaceTracker race = new RaceTracker(gates, start, RaceTracker.MissPolicy.PENALTY, 40, 8, 6);
        // Weave through the gates: x follows the gate centres, one block per tick.
        double x = 0;
        double z = 0.75;
        RaceEvent finish = null;
        int passed = 0;
        for (long tick = 11; tick < 200 && finish == null; tick++) {
            double nz = z + 1.0;
            double nx = 3 * Math.sin(Math.PI * (nz - 5) / 20.0 + Math.PI / 2) * -1;
            // simple target: aim for the next gate's centre
            int next = race.nextGate();
            if (next < gates.size()) {
                nx = gates.get(next).centerX();
            }
            for (RaceEvent e : race.update(x, 64, z, nx, 64, nz, tick)) {
                if (e.type() == RaceEvent.Type.GATE_PASSED) {
                    passed++;
                }
                if (e.type() == RaceEvent.Type.FINISHED) {
                    finish = e;
                }
                assertNotEquals(RaceEvent.Type.GATE_MISSED, e.type());
            }
            x = nx;
            z = nz;
        }
        assertNotNull(finish);
        assertEquals(4, passed);
        assertEquals(0, race.missed());
        // start line z=0 to finish line z=50 at exactly one block per tick
        assertEquals(50.0, finish.elapsedTicks(), 1e-6);
        assertEquals(finish.elapsedTicks(), finish.totalTicks(), 1e-9);
        assertTrue(race.done());
    }

    @Test
    void missedGatesArePenalised() {
        List<GateLine> gates = course();
        RaceTracker race = new RaceTracker(gates, 0, RaceTracker.MissPolicy.PENALTY, 40, 8, 6);
        RaceEvent last = null;
        int missed = 0;
        // Straight down x = 0: every slalom gate is passed on the wrong side.
        for (long tick = 1; tick <= 60; tick++) {
            for (RaceEvent e : race.update(0, 64, tick - 1 + 0.5, 0, 64, tick + 0.5, tick)) {
                if (e.type() == RaceEvent.Type.GATE_MISSED) {
                    missed++;
                }
                last = e;
            }
        }
        assertEquals(4, missed);
        assertNotNull(last);
        assertEquals(RaceEvent.Type.FINISHED, last.type());
        assertEquals(last.elapsedTicks() + 4 * 40, last.totalTicks(), 1e-9);
    }

    @Test
    void skippingAroundAGateIsStillAMiss() {
        List<GateLine> gates = course();
        RaceTracker race = new RaceTracker(gates, 0, RaceTracker.MissPolicy.PENALTY, 40, 1.0, 6);
        // Far outside gate 1 (beyond maxOutside) so it is not detected directly, then through gate 2 properly.
        race.update(-20, 64, 5, -20, 64, 15, 1);
        assertEquals(1, race.nextGate());
        List<RaceEvent> events = race.update(-3, 64, 19, -3, 64, 21, 2);
        assertEquals(2, events.size());
        assertEquals(RaceEvent.Type.GATE_MISSED, events.get(0).type());
        assertEquals(1, events.get(0).gate());
        assertEquals(RaceEvent.Type.GATE_PASSED, events.get(1).type());
        assertEquals(2, events.get(1).gate());
    }

    @Test
    void disqualificationEndsTheRun() {
        RaceTracker race = new RaceTracker(course(), 0, RaceTracker.MissPolicy.DISQUALIFY, 40, 8, 6);
        List<RaceEvent> events = race.update(0, 64, 9, 0, 64, 11, 5);
        assertEquals(1, events.size());
        assertEquals(RaceEvent.Type.DISQUALIFIED, events.get(0).type());
        assertTrue(race.done());
        assertTrue(race.update(0, 64, 49, 0, 64, 51, 6).isEmpty());
    }

    @Test
    void ghostRoundTripAndInterpolation() throws IOException {
        GhostRun run = new GhostRun(1000.5, 70, -2000.25, 100);
        for (int i = 0; i < 10; i++) {
            run.add(1000.5 + i, 70 - i * 0.5, -2000.25 + i * 2, 170 + i * 2, i * 10, i / 10.0, -i, i % 2 == 0, true);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        run.write(bytes);
        GhostRun copy = GhostRun.read(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(10, copy.size());
        GhostRun.Frame f = copy.sample(4.5);
        assertEquals(1005.0, f.x(), 1e-4);
        assertEquals(67.75, f.y(), 1e-4);
        assertEquals(-2000.25 + 9, f.z(), 1e-4);
        assertEquals(179.0, f.yaw(), 1e-3);
        assertEquals(45.0, f.flip(), 1e-3);
        assertTrue(f.board());
        // yaw interpolation takes the short way round across the -180/180 seam
        GhostRun seam = new GhostRun(0, 0, 0, 10);
        seam.add(0, 0, 0, 175, 0, 0, 0, false, false);
        seam.add(0, 0, 0, -175, 0, 0, 0, false, false);
        assertEquals(180.0, seam.sample(0.5).yaw(), 1e-3);
    }

    @Test
    void ghostRespectsFrameLimitAndRejectsGarbage() {
        GhostRun run = new GhostRun(0, 0, 0, 3);
        assertTrue(run.add(0, 0, 0, 0, 0, 0, 0, false, false));
        assertTrue(run.add(0, 0, 0, 0, 0, 0, 0, false, false));
        assertTrue(run.add(0, 0, 0, 0, 0, 0, 0, false, false));
        assertFalse(run.add(0, 0, 0, 0, 0, 0, 0, false, false));
        assertTrue(run.full());
        assertThrows(IOException.class, () -> GhostRun.read(new ByteArrayInputStream(new byte[]{1, 2, 3})));
    }
}
