package dev.powderpass.lift;

import dev.powderpass.lift.geom.CablePath;
import dev.powderpass.lift.geom.ChairSwing;
import dev.powderpass.lift.geom.GroundFunction;
import dev.powderpass.lift.geom.LiftGeometry;
import dev.powderpass.lift.geom.LiftLayout;
import dev.powderpass.lift.geom.LiftSpec;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LiftGeometryTest {

    /** A mountain rising along +X with a bump in the middle and level ground at both stations. */
    private static final GroundFunction MOUNTAIN = (x, z) -> {
        double base = 64 + Math.max(0, Math.min(180, x - 10)) * 0.45;
        double bump = 12 * Math.exp(-Math.pow((x - 90) / 15.0, 2));
        return Math.floor(base + bump);
    };

    private static LiftSpec spec() {
        return new LiftSpec(0, 64, 0, 200, 145, 0,
                1.6, 2.4, 0.55, 5, 28, 3.0, 0.012, 0.35);
    }

    @Test
    void loopIsClosedAndContinuous() {
        LiftLayout layout = LiftGeometry.build(spec(), MOUNTAIN);
        CablePath path = layout.path();
        Vector3d a = new Vector3d();
        Vector3d b = new Vector3d();
        for (int i = 0; i < path.pointCount(); i++) {
            path.point(i, a);
            path.point(i + 1, b);
            double gap = a.distance(b);
            assertTrue(gap < 1.2, "gap of " + gap + " at point " + i);
        }
        // roughly two lines plus two semicircles
        assertTrue(path.length() > 400 && path.length() < 480, "length " + path.length());
    }

    @Test
    void seatsClearTheTerrainEverywhereOnTheLine() {
        LiftLayout layout = LiftGeometry.build(spec(), MOUNTAIN);
        CablePath path = layout.path();
        Vector3d p = new Vector3d();
        double minClearance = Double.MAX_VALUE;
        for (double s = layout.bottomLoad().end() + 12; s < layout.topUnload().start() - 12; s += 0.5) {
            path.position(s, p);
            double seat = p.y - layout.hanger();
            minClearance = Math.min(minClearance, seat - MOUNTAIN.groundAt(p.x, p.z));
        }
        assertTrue(minClearance >= 2.9, "min clearance " + minClearance);
        assertTrue(layout.warnings().isEmpty(), layout.warnings().toString());
    }

    @Test
    void towersAreSpacedAndStandOnTheGround() {
        LiftLayout layout = LiftGeometry.build(spec(), MOUNTAIN);
        List<LiftLayout.Tower> towers = layout.towers();
        assertTrue(towers.size() >= 6 && towers.size() <= 8, "towers " + towers.size());
        double prev = 5;
        for (LiftLayout.Tower t : towers) {
            assertTrue(t.x() - prev <= 28.0001, "spacing");
            assertTrue(t.cableY() > t.baseY() + 5, "tower must be tall enough");
            assertTrue(t.sUp() < t.sDown());
            prev = t.x();
        }
    }

    @Test
    void zonesAreOrderedAroundTheLoop() {
        LiftLayout layout = LiftGeometry.build(spec(), MOUNTAIN);
        double len = layout.path().length();
        assertTrue(layout.bottomLoad().start() < layout.bottomLoad().end());
        assertTrue(layout.bottomLoad().end() < layout.topUnload().start());
        assertTrue(layout.topUnload().end() < layout.topLoad().start());
        assertTrue(layout.topLoad().end() < layout.bottomUnload().start());
        assertTrue(layout.bottomUnload().end() < len);
        Vector3d p = new Vector3d();
        layout.path().position((layout.bottomLoad().start() + layout.bottomLoad().end()) / 2, p);
        assertEquals(64 + 0.55 + 2.4, p.y, 1e-6, "loading seat height");
        assertTrue(p.z > 1.0, "up line runs on the right-hand side (+Z when facing +X)");
        assertTrue(layout.rideLengthUp() > 180);
    }

    @Test
    void chairsSlowDownInStations() {
        LiftLayout layout = LiftGeometry.build(spec(), MOUNTAIN);
        double mid = (layout.bottomLoad().end() + layout.topUnload().start()) / 2;
        assertEquals(1.0, layout.speedFactor(mid), 1e-6);
        double loading = (layout.bottomLoad().start() + layout.bottomLoad().end()) / 2;
        assertTrue(layout.speedFactor(loading) < 0.5);
    }

    @Test
    void rejectsStationsThatAreTooClose() {
        LiftSpec tooShort = new LiftSpec(0, 64, 0, 10, 70, 0, 1.6, 2.4, 0.55, 5, 28, 3, 0.012, 0.35);
        assertThrows(IllegalArgumentException.class, () -> LiftGeometry.build(tooShort, MOUNTAIN));
    }

    @Test
    void pathWrapsAround() {
        LiftLayout layout = LiftGeometry.build(spec(), MOUNTAIN);
        CablePath path = layout.path();
        Vector3d a = path.position(10, new Vector3d());
        Vector3d b = path.position(10 + path.length() * 3, new Vector3d());
        Vector3d c = path.position(10 - path.length(), new Vector3d());
        assertEquals(0, a.distance(b), 1e-6);
        assertEquals(0, a.distance(c), 1e-6);
        Vector3d dir = path.direction(10, new Vector3d());
        assertEquals(1.0, dir.length(), 1e-9);
    }

    @Test
    void swingSettlesAfterAKick() {
        ChairSwing swing = new ChairSwing();
        swing.impulse(3, 2);
        double peak = 0;
        for (int i = 0; i < 40; i++) {
            swing.tick(0, 0);
            peak = Math.max(peak, Math.abs(swing.roll()));
        }
        assertTrue(peak > 1.0);
        for (int i = 0; i < 600; i++) {
            swing.tick(0, 0);
        }
        assertEquals(0.0, swing.roll(), 0.05);
        assertEquals(0.0, swing.pitch(), 0.05);
        // rounding a bullwheel towards the left swings the bottom out to the right
        ChairSwing turn = new ChairSwing();
        for (int i = 0; i < 30; i++) {
            turn.tick(0.01, 0);
        }
        assertTrue(turn.roll() > 0);
    }
}
