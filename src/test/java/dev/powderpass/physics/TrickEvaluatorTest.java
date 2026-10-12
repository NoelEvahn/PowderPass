package dev.powderpass.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrickEvaluatorTest {

    @Test
    void tinyHopIsNotATrick() {
        assertNull(TrickEvaluator.evaluate(10, 0, 0, 4, false, false));
    }

    @Test
    void namesCombinedTricks() {
        TrickResult t = TrickEvaluator.evaluate(-720, -360, 10, 50, true, false);
        assertEquals("Switch Backflip 720 Japan Grab", t.name());
        assertEquals(-1, t.flips());
        assertEquals(720, t.spinDegrees());
        assertTrue(t.grab());
    }

    @Test
    void bigAirWithoutRotation() {
        assertEquals("Big Air", TrickEvaluator.evaluate(0, 0, 0, 45, false, false).name());
        assertEquals("Air", TrickEvaluator.evaluate(0, 0, 0, 12, false, false).name());
        assertEquals("Air", TrickEvaluator.evaluate(0, 0, 0, 12, true, false).name(), "switch alone is not a trick");
    }

    @Test
    void snowboardGrabNames() {
        assertEquals("Double Frontflip Method Grab", TrickEvaluator.evaluate(0, 720, 8, 60, false, true).name());
        assertEquals("540 Indy Grab", TrickEvaluator.evaluate(-540, 0, 8, 30, false, true).name());
    }

    @Test
    void harderTricksScoreMore() {
        int air = TrickEvaluator.evaluate(0, 0, 0, 20, false, false).score();
        int spin360 = TrickEvaluator.evaluate(360, 0, 0, 20, false, false).score();
        int spin720 = TrickEvaluator.evaluate(720, 0, 0, 20, false, false).score();
        int flip = TrickEvaluator.evaluate(0, -360, 0, 20, false, false).score();
        assertTrue(air < spin360 && spin360 < spin720);
        assertTrue(flip > spin360);
    }

    @Test
    void comboMultiplierGrowsAndExpires() {
        ComboTracker combo = new ComboTracker(60, 0.25, 3.0);
        assertEquals(100, combo.addTrick(100, 0));
        assertEquals(125, combo.addTrick(100, 30));
        assertEquals(150, combo.addTrick(100, 60));
        assertEquals(3, combo.count());
        assertEquals(-1, combo.tick(100));
        assertEquals(375, combo.tick(121));
        assertEquals(0, combo.count());
        // expired combos restart on the next trick
        combo.addTrick(100, 200);
        assertEquals(100, combo.addTrick(100, 400));
    }
}
