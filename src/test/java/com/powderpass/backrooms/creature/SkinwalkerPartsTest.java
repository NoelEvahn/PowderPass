package com.powderpass.backrooms.creature;

import org.bukkit.Material;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The skinwalker's parts on their own: the physical controller, the tentacle rig and gait, the stalking mind. */
class SkinwalkerPartsTest {
    private WorldMock world;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        world = new WorldMock(Material.STONE, 64); // solid up to y=63, floor surface at y=64
        for (int cx = -2; cx <= 2; cx++) for (int cz = -2; cz <= 2; cz++) world.getChunkAt(cx, cz);
        for (int x = -20; x <= 20; x++) for (int z = -20; z <= 20; z++) for (int y = 64; y < 72; y++) world.getBlockAt(x, y, z).setType(Material.AIR);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // ------------------------------------------------------------------ controller

    @Test
    void theBodyCollidesWithWallsAndClimbsSingleSteps() {
        for (int y = 64; y < 68; y++) for (int z = -5; z <= 5; z++) world.getBlockAt(3, y, z).setType(Material.STONE);
        GroundController g = new GroundController(world, 0.5, 64, 0.5);
        g.reset(0.5, 64, 0.5);
        for (int i = 0; i < 40; i++) g.move(0.25, 0);
        assertTrue(g.x < 3 - GroundController.HALF + 1e-6, "stopped at the wall: " + g.x);
        assertTrue(g.blocked);
        // a single step is climbed
        world.getBlockAt(0, 64, 3).setType(Material.STONE);
        world.getBlockAt(1, 64, 3).setType(Material.STONE);
        world.getBlockAt(0, 64, 4).setType(Material.STONE);
        world.getBlockAt(1, 64, 4).setType(Material.STONE);
        g.reset(0.5, 64, 0.5);
        for (int i = 0; i < 16; i++) g.move(0, 0.25);
        assertEquals(65, g.y, 1e-6, "stepped up one block");
        // the visible body glides up instead of snapping
        GroundController h = new GroundController(world, 0.5, 64, 0.5);
        h.reset(0.5, 64, 0.5);
        double maxRise = 0, prev = h.smoothY;
        for (int i = 0; i < 16; i++) {
            h.move(0, 0.25);
            maxRise = Math.max(maxRise, h.smoothY - prev);
            prev = h.smoothY;
        }
        assertTrue(maxRise <= 0.23, "smooth step: " + maxRise);
    }

    @Test
    void itFallsWithGravityAndWillNotWalkOffACliff() {
        // a pit 2 deep and a cliff 6 deep
        for (int x = 2; x <= 4; x++) for (int z = -2; z <= 2; z++) for (int y = 62; y < 64; y++) world.getBlockAt(x, y, z).setType(Material.AIR);
        GroundController g = new GroundController(world, 3.5, 64, 0.5);
        g.reset(3.5, 66, 0.5); // dropped above the pit
        for (int i = 0; i < 40; i++) g.move(0, 0);
        assertEquals(62, g.y, 1e-6, "fell to the bottom");
        assertTrue(g.onGround);
        for (int x = -8; x <= -5; x++) for (int z = -20; z <= 20; z++) for (int y = 56; y < 64; y++) world.getBlockAt(x, y, z).setType(Material.AIR);
        GroundController c = new GroundController(world, -3.5, 64, 0.5);
        c.reset(-3.5, 64, 0.5);
        for (int i = 0; i < 40; i++) c.move(-0.2, 0);
        assertEquals(64, c.y, 1e-6, "it does not step off a deep drop");
    }

    @Test
    void itNeverTunnelsThroughThinWalls() {
        for (int y = 64; y < 70; y++) for (int z = -5; z <= 5; z++) world.getBlockAt(2, y, z).setType(Material.STONE);
        GroundController g = new GroundController(world, 0.5, 64, 0.5);
        g.reset(0.5, 64, 0.5);
        for (int i = 0; i < 10; i++) g.move(1.5, 0); // absurd speed
        assertTrue(g.x < 2, "still on this side: " + g.x);
    }

    // ------------------------------------------------------------------ rig

    /** Flat floor at y=0, ceiling at 4.5. */
    private static final TentacleRig.Ground FLAT = new TentacleRig.Ground() {
        @Override
        public double groundY(double x, double fromY, double z, double depth) {
            return fromY >= 0 && fromY - depth <= 0 ? 0 : Double.NaN;
        }

        @Override
        public double ceilingY(double x, double fromY, double z, double range) {
            return 4.5;
        }
    };

    @Test
    void legsPlantOnTheFloorAroundTheBody() {
        TentacleRig rig = new TentacleRig(7);
        Vector3d root = new Vector3d(0, 0, 0);
        rig.settle(FLAT, root, 0);
        for (TentacleRig.Leg l : rig.legs) {
            assertTrue(l.planted, "planted");
            assertEquals(0, l.plant.y, 1e-9, "on the floor");
            double r = Math.hypot(l.plant.x, l.plant.z);
            assertTrue(r > 1.2 && r < 2.8, "spread like a spider's: " + r);
        }
        // three legs on each side
        long right = rig.legs.stream().filter(l -> l.side > 0).count();
        assertEquals(3, right);
    }

    @Test
    void thePoseIsCompleteConnectedAndUnderTheCeiling() {
        TentacleRig rig = new TentacleRig(3);
        Vector3d root = new Vector3d(0, 0, 0);
        rig.settle(FLAT, root, 0);
        rig.emerge = 1;
        rig.plant = 1;
        rig.wound = 1;
        Vector3d feet = new Vector3d(0, 0.7, 0);
        rig.update(feet, 1.8, 0, 4.5, 2, 3);
        List<Matrix4f> m = rig.computed;
        assertEquals(TentacleRig.partCount(), m.size());
        Vector3d origin = new Vector3d(0, 2.5, 0);
        for (Matrix4f t : m) {
            Vector3f p = t.transformPosition(new Vector3f(0.5f, 0.5f, 0.5f));
            assertTrue(Float.isFinite(p.x) && Float.isFinite(p.y) && Float.isFinite(p.z));
            double wy = origin.y + p.y;
            assertTrue(wy < 4.5 + 0.35, "under the ceiling: " + wy);
            assertTrue(Math.hypot(p.x, p.z) < 4.6, "stays near the body: " + p);
        }
        // each leg is one connected chain from the back down to its foot
        for (int leg = 0; leg < TentacleRig.LEGS; leg++) {
            Vector3f prevEnd = null;
            for (int s = 0; s < TentacleRig.LEG_SEGMENTS; s++) {
                Matrix4f t = m.get(rig.legIndex(leg, s));
                Vector3f start = t.transformPosition(new Vector3f(0.5f, 0.17f, 0.5f));
                Vector3f end = t.transformPosition(new Vector3f(0.5f, 1f, 0.5f));
                if (prevEnd != null) assertTrue(prevEnd.distance(start) < 0.2, "joint gap " + prevEnd.distance(start));
                prevEnd = end;
            }
            double footY = origin.y + prevEnd.y;
            assertTrue(footY < 0.3, "the leg reaches the floor: " + footY);
        }
    }

    @Test
    void itWalksWithAnAlternatingTripodGait() {
        TentacleRig rig = new TentacleRig(11);
        Vector3d root = new Vector3d(0, 0, 0);
        rig.settle(FLAT, root, 0);
        Vector3d vel = new Vector3d(0, 0, 0.15);
        int steps = 0;
        List<Vector3d> landed = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            root.add(new Vector3d(vel).mul(2));
            steps += rig.gait(FLAT, root, 0, vel, 2, 5, landed);
            int stepping = 0;
            java.util.Set<Integer> groups = new java.util.HashSet<>();
            for (TentacleRig.Leg l : rig.legs) {
                if (l.stepping) {
                    stepping++;
                    groups.add(l.group());
                }
            }
            assertTrue(stepping <= 3, "never more than one tripod in the air");
            assertTrue(groups.size() <= 1, "one tripod at a time");
            for (TentacleRig.Leg l : rig.legs) {
                if (!l.stepping) assertEquals(0, l.plant.y, 1e-9, "planted feet stay on the floor");
                if (!l.stepping) assertTrue(l.plant.distance(root.x, 0, root.z) < 3.6, "planted feet keep up");
            }
        }
        assertTrue(steps >= 12, "it took steps: " + steps);
        for (Vector3d f : landed) assertEquals(0, f.y, 1e-9);
    }

    // ------------------------------------------------------------------ stalking

    @Test
    void aStillTargetIsCircledThenAttackedAndMovingResetsTheTimer() {
        Stalker s = new Stalker(5, 10, 2.5, 200, 42);
        double sx = 8, sz = 0;
        java.util.Set<Stalker.Mode> seen = new java.util.HashSet<>();
        int attackAt = -1;
        for (int t = 0; t < 400; t += 2) {
            s.think(2, sx, 0, sz, 0, 0, 0, 0f, false);
            seen.add(s.mode);
            if (s.mode == Stalker.Mode.ATTACK && attackAt < 0) attackAt = t;
            // move toward the goal like an owner would
            double dx = s.gx - sx, dz = s.gz - sz, d = Math.hypot(dx, dz);
            if (d > 1e-3 && s.speed > 0) {
                double step = Math.min(d, s.speed * 2);
                sx += dx / d * step;
                sz += dz / d * step;
            }
            if (attackAt < 0) assertNotEquals(Stalker.Mode.ATTACK, s.mode);
        }
        assertTrue(attackAt >= 190 && attackAt <= 215, "attacks after about ten seconds of stillness: " + attackAt);
        assertTrue(seen.size() >= 3, "keeps moving unpredictably round a still target: " + seen);
        // and when the target moves, the timer resets and it backs off
        Stalker m = new Stalker(5, 10, 2.5, 200, 1);
        for (int t = 0; t < 190; t += 2) m.think(2, 8, 0, 0, 0, 0, 0, 0f, false);
        assertTrue(m.stillTicks >= 180);
        for (int t = 0; t < 20; t += 2) m.think(2, 8, 0, 0, t * 0.2, 0, 0, 0f, false);
        assertEquals(0, m.stillTicks, "moving resets the timer");
    }

    @Test
    void itKeepsItsDistanceFromAMovingTarget() {
        Stalker s = new Stalker(6, 12, 2.5, 200, 5);
        double sx = 10, sz = 0, tx = 0, tz = 0;
        double minD = 99;
        for (int t = 0; t < 600; t += 2) {
            tx += 0.12 * Math.cos(t * 0.01);
            tz += 0.12 * Math.sin(t * 0.013);
            s.think(2, sx, 0, sz, tx, 0, tz, 0f, false);
            double dx = s.gx - sx, dz = s.gz - sz, d = Math.hypot(dx, dz);
            if (d > 1e-3 && s.speed > 0) {
                double step = Math.min(d, s.speed * 2);
                sx += dx / d * step;
                sz += dz / d * step;
            }
            if (t > 40) minD = Math.min(minD, Math.hypot(sx - tx, sz - tz));
        }
        assertTrue(minD > 2.5, "it never simply walks into a moving target: " + minD);
    }
}
