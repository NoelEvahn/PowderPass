package dev.powderpass.physics;

import dev.powderpass.util.MathUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkiPhysicsTest {

    private static final SkiParams PARAMS = SkiParams.defaultSkis();

    private static SkiState spawn(Heightfield field, double x, double z, double heading) {
        double y = field.height(x, z, 300);
        return new SkiState(x, y, z, heading);
    }

    private static Heightfield field(TerrainSampler t) {
        return new Heightfield(t, PARAMS.smoothingRadius(), PARAMS.cliffSlope());
    }

    @Test
    void acceleratesDownAStaircaseSlopeAndReachesPlausibleTerminalSpeed() {
        Heightfield field = field(FakeTerrain.slope(20, 1.0));
        SkiState s = spawn(field, 0.5, 0.5, 0.0);
        StepResult r = new StepResult();
        int launches = 0;
        for (int tick = 0; tick < 600; tick++) {
            SkiPhysics.step(s, SkiInput.idle(0.0), field, PARAMS, r);
            assertNull(r.crash, "should not crash on a clean slope");
            if (r.launched) {
                launches++;
            }
        }
        double kmh = MathUtil.toKmh(s.speed());
        assertTrue(kmh > 45 && kmh < 130, "terminal speed " + kmh);
        assertTrue(launches <= 2, "staircase should feel smooth, launched " + launches + " times");
        assertTrue(s.pos.z > 100, "should have travelled downhill");
    }

    @Test
    void steeperSlopesAreFaster() {
        double[] speeds = new double[3];
        double[] angles = {8, 18, 30};
        for (int i = 0; i < angles.length; i++) {
            Heightfield field = field(FakeTerrain.slope(angles[i], 0.125));
            SkiState s = spawn(field, 0.5, 0.5, 0.0);
            StepResult r = new StepResult();
            for (int tick = 0; tick < 400; tick++) {
                SkiPhysics.step(s, SkiInput.idle(0.0), field, PARAMS, r);
            }
            speeds[i] = s.speed();
        }
        assertTrue(speeds[0] < speeds[1] && speeds[1] < speeds[2]);
    }

    @Test
    void tuckIsFasterThanStandingTall() {
        Heightfield field = field(FakeTerrain.slope(22, 0.125));
        SkiState tall = spawn(field, 0.5, 0.5, 0.0);
        SkiState tuck = spawn(field, 0.5, 0.5, 0.0);
        StepResult r = new StepResult();
        for (int tick = 0; tick < 500; tick++) {
            SkiPhysics.step(tall, SkiInput.idle(0.0), field, PARAMS, r);
            SkiPhysics.step(tuck, new SkiInput(0.0, false, false, false, false, false, true), field, PARAMS, r);
        }
        assertTrue(tuck.speed() > tall.speed() * 1.1);
    }

    @Test
    void glidesToAStopOnTheFlat() {
        Heightfield field = field(FakeTerrain.flat(64));
        SkiState s = spawn(field, 0.5, 0.5, 0.0);
        s.vel.set(0, 0, 1.0);
        StepResult r = new StepResult();
        int ticks = 0;
        while (s.speed() > 0 && ticks < 2000) {
            SkiPhysics.step(s, SkiInput.idle(0.0), field, PARAMS, r);
            ticks++;
        }
        assertEquals(0.0, s.speed(), 1e-9);
        assertTrue(ticks > 60 && ticks < 1000, "glide ticks " + ticks);
    }

    @Test
    void brakingStopsMuchSoonerThanGliding() {
        Heightfield field = field(FakeTerrain.flat(64));
        SkiState glide = spawn(field, 0.5, 0.5, 0.0);
        SkiState brake = spawn(field, 0.5, 0.5, 0.0);
        glide.vel.set(0, 0, 1.0);
        brake.vel.set(0, 0, 1.0);
        StepResult r = new StepResult();
        for (int tick = 0; tick < 40; tick++) {
            SkiPhysics.step(glide, SkiInput.idle(0.0), field, PARAMS, r);
            SkiPhysics.step(brake, new SkiInput(90.0, false, true, false, false, false, false), field, PARAMS, r);
        }
        assertTrue(brake.speed() < glide.speed() * 0.5, "brake " + brake.speed() + " glide " + glide.speed());
    }

    @Test
    void carvingKeepsMoreSpeedThanSkidding() {
        Heightfield field = field(FakeTerrain.flat(64));
        SkiState carve = spawn(field, 0.5, 0.5, 0.0);
        SkiState skid = spawn(field, 0.5, 0.5, 0.0);
        carve.vel.set(0, 0, 1.0);
        skid.vel.set(0, 0, 1.0);
        StepResult r = new StepResult();
        for (int tick = 0; tick < 30; tick++) {
            // gentle continuous turn vs. slamming the skis sideways
            SkiPhysics.step(carve, SkiInput.idle(carve.heading + 3.0), field, PARAMS, r);
            SkiPhysics.step(skid, new SkiInput(90.0, false, true, false, false, false, false), field, PARAMS, r);
        }
        assertTrue(carve.speed() > skid.speed() * 1.5);
        assertTrue(Math.abs(carve.heading) > 45, "carver should have turned, heading " + carve.heading);
    }

    @Test
    void leansIntoTurns() {
        Heightfield field = field(FakeTerrain.flat(64));
        SkiState s = spawn(field, 0.5, 0.5, 0.0);
        s.vel.set(0, 0, 0.8);
        StepResult r = new StepResult();
        for (int tick = 0; tick < 10; tick++) {
            SkiPhysics.step(s, SkiInput.idle(s.heading + 20.0), field, PARAMS, r); // turning right
        }
        assertTrue(s.lean < -5.0, "should lean right, lean " + s.lean);
    }

    @Test
    void launchesOffACliffAndLandsCleanly() {
        // Flat shelf for z < 20, then a 6 block drop to a 30 degree landing slope.
        FakeTerrain t = new FakeTerrain((x, z) -> z < 20 ? 100.0 : 94.0 - (z - 20) * Math.tan(Math.toRadians(30)), 0.125);
        Heightfield field = field(t);
        SkiState s = spawn(field, 0.5, 2.5, 0.0);
        s.vel.set(0, 0, 0.9);
        StepResult r = new StepResult();
        boolean launched = false;
        boolean landed = false;
        for (int tick = 0; tick < 200 && !landed; tick++) {
            SkiPhysics.step(s, SkiInput.idle(0.0), field, PARAMS, r);
            assertNull(r.crash);
            launched |= r.launched;
            landed = r.landed;
        }
        assertTrue(launched, "should launch off the lip");
        assertTrue(landed, "should land");
        assertTrue(r.landingAirTicks > 5);
        assertTrue(s.grounded);
    }

    @Test
    void chargedJumpGetsAirOnTheFlat() {
        Heightfield field = field(FakeTerrain.flat(64));
        SkiState s = spawn(field, 0.5, 0.5, 0.0);
        s.vel.set(0, 0, 0.5);
        StepResult r = new StepResult();
        SkiInput hold = new SkiInput(0.0, false, false, false, false, true, false);
        for (int tick = 0; tick < 12; tick++) {
            SkiPhysics.step(s, hold, field, PARAMS, r);
        }
        SkiPhysics.step(s, SkiInput.idle(0.0), field, PARAMS, r);
        assertTrue(r.jumped);
        assertFalse(s.grounded);
        int air = 0;
        while (!s.grounded && air < 100) {
            SkiPhysics.step(s, SkiInput.idle(0.0), field, PARAMS, r);
            air++;
        }
        assertTrue(s.grounded);
        assertTrue(air >= TrickEvaluator.MIN_AIR_TICKS, "air ticks " + air);
    }

    @Test
    void spinningAHalfRotationAndLandingSidewaysCrashes() {
        Heightfield field = field(FakeTerrain.flat(64));
        SkiState s = spawn(field, 0.5, 0.5, 0.0);
        s.grounded = false;
        s.pos.y += 3.0;
        s.vel.set(0, 0.4, 0.8);
        StepResult r = new StepResult();
        CrashReason crash = null;
        for (int tick = 0; tick < 100; tick++) {
            boolean spinning = tick > 0 && Math.abs(s.spin) < 90.0;
            SkiPhysics.step(s, new SkiInput(0.0, false, false, false, spinning, false, false), field, PARAMS, r);
            if (r.crash != null) {
                crash = r.crash;
                break;
            }
            if (r.landed) {
                break;
            }
        }
        assertEquals(CrashReason.BAD_LANDING, crash);
    }

    @Test
    void fullSpinLandsAndIsNamed() {
        Heightfield field = field(FakeTerrain.flat(64));
        SkiState s = spawn(field, 0.5, 0.5, 0.0);
        s.grounded = false;
        s.pos.y += 4.0;
        s.vel.set(0, 0.5, 0.8);
        StepResult r = new StepResult();
        for (int tick = 0; tick < 100; tick++) {
            boolean spinning = tick > 0 && Math.abs(s.spin) < 360.0;
            SkiPhysics.step(s, new SkiInput(0.0, false, false, false, spinning, false, false), field, PARAMS, r);
            assertNull(r.crash);
            if (r.landed) {
                break;
            }
        }
        assertTrue(r.landed);
        TrickResult trick = TrickEvaluator.evaluate(r.landingSpin, r.landingFlip, r.landingGrabTicks,
                r.landingAirTicks, r.landingSwitch, false);
        assertNotNull(trick);
        assertEquals("360", trick.name());
    }

    @Test
    void fastWallHitCrashesSlowOneBumps() {
        FakeTerrain t = FakeTerrain.flat(64).wall(0, 4);
        Heightfield field = field(t);
        SkiState fast = spawn(field, 0.5, 0.5, 0.0);
        fast.vel.set(0, 0, 1.0);
        StepResult r = new StepResult();
        CrashReason crash = null;
        for (int tick = 0; tick < 20 && crash == null; tick++) {
            SkiPhysics.step(fast, SkiInput.idle(0.0), field, PARAMS, r);
            crash = r.crash;
        }
        assertEquals(CrashReason.WALL, crash);

        SkiState slow = spawn(field, 0.5, 0.5, 0.0);
        slow.vel.set(0, 0, 0.2);
        boolean bumped = false;
        for (int tick = 0; tick < 60; tick++) {
            SkiPhysics.step(slow, SkiInput.idle(0.0), field, PARAMS, r);
            assertNull(r.crash);
            bumped |= r.wallBump;
        }
        assertTrue(bumped);
        assertTrue(slow.pos.z < 4.0, "must not pass through the wall");
    }

    @Test
    void waterEndsTheRun() {
        Heightfield field = field(FakeTerrain.flat(64).surface(SurfaceKind.LIQUID));
        SkiState s = spawn(field, 0.5, 0.5, 0.0);
        StepResult r = new StepResult();
        SkiPhysics.step(s, SkiInput.idle(0.0), field, PARAMS, r);
        assertEquals(CrashReason.LIQUID, r.crash);
    }

    @Test
    void iceIsSlipperierThanSnow() {
        Heightfield snowField = field(FakeTerrain.flat(64));
        Heightfield iceField = field(FakeTerrain.flat(64).surface(SurfaceKind.ICE));
        SkiState snow = spawn(snowField, 0.5, 0.5, 0.0);
        SkiState ice = spawn(iceField, 0.5, 0.5, 0.0);
        snow.vel.set(0, 0, 0.6);
        ice.vel.set(0, 0, 0.6);
        StepResult r = new StepResult();
        for (int tick = 0; tick < 60; tick++) {
            SkiPhysics.step(snow, SkiInput.idle(0.0), snowField, PARAMS, r);
            SkiPhysics.step(ice, SkiInput.idle(0.0), iceField, PARAMS, r);
        }
        assertTrue(ice.speed() > snow.speed());
    }

    @Test
    void keysSteeringTurnsWithoutTheCamera() {
        SkiParams keys = PARAMS.withSteering(SteeringMode.KEYS);
        Heightfield field = field(FakeTerrain.flat(64));
        SkiState s = spawn(field, 0.5, 0.5, 0.0);
        s.vel.set(0, 0, 0.5);
        StepResult r = new StepResult();
        for (int tick = 0; tick < 10; tick++) {
            SkiPhysics.step(s, new SkiInput(0.0, false, false, true, false, false, false), field, keys, r);
        }
        assertTrue(s.heading < -20.0, "A should turn left, heading " + s.heading);
    }

    @Test
    void keysHeldThroughTakeoffDoNotSpinOrFlip() {
        FakeTerrain t = new FakeTerrain((x, z) -> z < 20 ? 100.0 : 94.0 - (z - 20) * Math.tan(Math.toRadians(30)), 0.125);
        Heightfield field = field(t);
        SkiState s = spawn(field, 0.5, 2.5, 0.0);
        s.vel.set(0, 0, 0.9);
        StepResult r = new StepResult();
        // Hold W and D the whole time (poling + hard carve habit), straight off the cliff.
        SkiInput held = new SkiInput(0.0, true, false, false, true, false, false);
        boolean landed = false;
        for (int tick = 0; tick < 200 && !landed; tick++) {
            SkiPhysics.step(s, held, field, PARAMS, r);
            assertNull(r.crash, "held keys must not cause a crash");
            landed = r.landed;
        }
        assertTrue(landed);
        assertEquals(0.0, r.landingSpin, 1e-9);
        assertEquals(0.0, r.landingFlip, 1e-9);
    }

    @Test
    void releasingAndPressingAgainInTheAirSpins() {
        Heightfield field = field(FakeTerrain.flat(64));
        SkiState s = spawn(field, 0.5, 0.5, 0.0);
        s.grounded = false;
        s.pos.y += 6.0;
        s.vel.set(0, 0.5, 0.6);
        StepResult r = new StepResult();
        SkiPhysics.step(s, new SkiInput(0.0, false, false, false, true, false, false), field, PARAMS, r);
        SkiPhysics.step(s, new SkiInput(0.0, false, false, false, true, false, false), field, PARAMS, r);
        assertEquals(0.0, s.spin, 1e-9, "locked while still held from takeoff");
        SkiPhysics.step(s, SkiInput.idle(0.0), field, PARAMS, r);
        SkiPhysics.step(s, new SkiInput(0.0, false, false, false, true, false, false), field, PARAMS, r);
        assertEquals(PARAMS.airSpinRate(), s.spin, 1e-9);
    }
}
