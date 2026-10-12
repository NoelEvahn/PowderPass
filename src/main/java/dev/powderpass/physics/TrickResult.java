package dev.powderpass.physics;

/**
 * A judged trick.
 *
 * @param name          human readable name, e.g. "Switch Backflip 540 Mute Grab"
 * @param score         points awarded before combo multipliers
 * @param spinDegrees   completed spin rounded to 180 degrees
 * @param flips         completed flips (signed: negative = back flips)
 * @param grab          whether a grab was held long enough to count
 * @param switchLanding landed riding backwards
 * @param airTicks      time in the air
 */
public record TrickResult(String name, int score, int spinDegrees, int flips, boolean grab,
                          boolean switchLanding, int airTicks) {

    public double airSeconds() {
        return airTicks / 20.0;
    }
}
