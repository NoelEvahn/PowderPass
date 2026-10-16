package com.powderpass.backrooms.vehicle;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The driving model on a little block world: a floor at y 64 (tops at 65) and whatever obstacles a test adds. */
class CarPhysicsTest {
    private static final double DT = 0.05;

    /** A sparse block world: full blocks by default on y 64 only. */
    private static final class World implements CarPhysics.Terrain {
        final Map<Long, Double> tops = new HashMap<>();
        int floorY = 64;
        /** x >= dropX: the floor is 3 blocks lower. */
        int dropX = Integer.MAX_VALUE;

        static long k(int x, int y, int z) {
            return ((long) x & 0x1FFFFF) | (((long) y & 0x1FFFFF) << 21) | (((long) z & 0x1FFFFF) << 42);
        }

        void put(int x, int y, int z, double top) {
            tops.put(k(x, y, z), top);
        }

        @Override
        public double top(int x, int y, int z) {
            Double t = tops.get(k(x, y, z));
            if (t != null) return t;
            int fy = x >= dropX ? floorY - 3 : floorY;
            return y == fy ? 1.0 : -1;
        }

        @Override
        public boolean blocked(double x, double y, double z) {
            int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
            Double t = tops.get(k(bx, by, bz));
            if (t != null) return y - by < t;
            int fy = bx >= dropX ? floorY - 3 : floorY;
            return by <= fy && by >= fy - 1;
        }

        void wall(int x0, int x1, int z, int y0, int y1) {
            for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) put(x, y, z, 1.0);
        }
    }

    private static final CarPhysics.Input FWD = new CarPhysics.Input(true, false, false, false, false);
    private static final CarPhysics.Input FWD_RIGHT = new CarPhysics.Input(true, false, false, true, false);
    private static final CarPhysics.Input BRAKE = new CarPhysics.Input(false, false, false, false, true);
    private static final CarPhysics.Input BACK = new CarPhysics.Input(false, true, false, false, false);

    private static void run(CarPhysics c, CarPhysics.Input in, int ticks, CarPhysics.Terrain t) {
        for (int i = 0; i < ticks; i++) c.step(in, DT, t);
    }

    @Test
    void acceleratesToTopSpeedAlongItsHeading() {
        World w = new World();
        CarPhysics c = new CarPhysics(0.5, 65, 0.5, 0);
        run(c, FWD, 20, w);
        assertTrue(c.speed > 3 && c.speed < 6, "about 1 s of throttle: " + c.speed);
        run(c, FWD, 200, w);
        assertEquals(CarPhysics.MAX_FORWARD, c.speed, 0.3, "top speed");
        assertTrue(c.z > 80 && Math.abs(c.x - 0.5) < 1e-6, "yaw 0 drives along +Z: " + c.x + "," + c.z);
        assertEquals(65, c.y, 1e-9, "stays on the floor");
    }

    @Test
    void brakesHardAndCoastsSlowly() {
        World w = new World();
        CarPhysics a = new CarPhysics(0.5, 65, 0.5, 0), b = new CarPhysics(0.5, 65, 0.5, 0);
        run(a, FWD, 120, w);
        run(b, FWD, 120, w);
        run(a, BRAKE, 10, w);
        run(b, CarPhysics.Input.NONE, 10, w);
        assertTrue(a.braking);
        assertTrue(a.speed < b.speed - 3, "braking " + a.speed + " vs coasting " + b.speed);
        run(a, BRAKE, 40, w);
        assertEquals(0, a.speed, 1e-9);
        // reverse from standstill, with the reversing state set
        run(a, BACK, 40, w);
        assertTrue(a.speed < -2 && a.speed >= -CarPhysics.MAX_REVERSE && a.reversing);
    }

    @Test
    void steeringRightIncreasesYawAndTurnsTowardMinusX() {
        World w = new World();
        CarPhysics c = new CarPhysics(0.5, 65, 0.5, 0);
        run(c, FWD, 40, w);
        double yaw0 = c.yaw;
        run(c, FWD_RIGHT, 30, w);
        assertTrue(c.yaw > yaw0 + 20, "turned right: " + c.yaw);
        assertTrue(c.x < 0, "right of +Z is -X: " + c.x);
        // no turning on the spot: a stopped car does not rotate
        CarPhysics s = new CarPhysics(0.5, 65, 0.5, 0);
        run(s, new CarPhysics.Input(false, false, false, true, false), 40, w);
        assertEquals(0, s.yaw, 1e-9);
    }

    @Test
    void stopsAtAWallInsteadOfDrivingThroughIt() {
        World w = new World();
        w.wall(-5, 5, 20, 65, 67);
        CarPhysics c = new CarPhysics(0.5, 65, 0.5, 0);
        run(c, FWD, 200, w);
        assertTrue(c.z + CarPhysics.HALF_LENGTH <= 20.0 + 1e-9, "nose stays out of the wall: " + c.z);
        assertTrue(c.z > 15, "but gets close to it: " + c.z);
        assertTrue(Math.abs(c.speed) < 1.5, "and is stopped by it: " + c.speed);
    }

    @Test
    void cannotSqueezeThroughATwoWideDoorway() {
        World w = new World();
        w.wall(-6, -1, 20, 65, 67);
        w.wall(2, 6, 20, 65, 67); // gap x 0..1, two blocks
        CarPhysics c = new CarPhysics(1.0, 65, 0.5, 0);
        run(c, FWD, 200, w);
        assertTrue(c.z < 20, "a car is wider than a 2-block door: " + c.z);
    }

    @Test
    void climbsSlabsButNotFullBlocksAndFallsOffLedges() {
        World w = new World();
        for (int x = -3; x <= 3; x++) for (int z = 10; z <= 30; z++) w.put(x, 65, z, 0.5);
        CarPhysics c = new CarPhysics(0.5, 65, 0.5, 0);
        run(c, FWD, 80, w);
        assertEquals(65.5, c.y, 1e-9, "up a half-block step");

        World ledge = new World();
        ledge.dropX = 10;
        CarPhysics f = new CarPhysics(0.5, 65, 0.5, -90); // heading +X
        run(f, FWD, 100, ledge);
        assertTrue(f.x > 14);
        assertEquals(62, f.y, 1e-9, "fell to the lower floor");
        assertTrue(f.onGround);
    }

    @Test
    void wheelsSpinWithDistance() {
        World w = new World();
        CarPhysics c = new CarPhysics(0.5, 65, 0.5, 0);
        run(c, FWD, 40, w);
        assertEquals((c.z - 0.5) / 0.33, c.wheelSpin, 0.05);
    }
}
