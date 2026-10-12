package dev.powderpass.physics;

/**
 * One tick of rider input.
 *
 * @param lookYaw  the rider's camera yaw in degrees
 * @param forward  W - pole push / skate (front flip in the air)
 * @param backward S - brake &amp; skid (back flip in the air)
 * @param left     A - hard carve left (spin left in the air)
 * @param right    D - hard carve right (spin right in the air)
 * @param jump     space - hold to load, release to pop
 * @param sneak    shift - aerodynamic tuck (grab in the air)
 */
public record SkiInput(double lookYaw, boolean forward, boolean backward, boolean left, boolean right,
                       boolean jump, boolean sneak) {

    public static SkiInput idle(double yaw) {
        return new SkiInput(yaw, false, false, false, false, false, false);
    }
}
