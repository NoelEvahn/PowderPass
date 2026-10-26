package dev.powderpass.volleyball.hit;

import dev.powderpass.volleyball.math.Vec3;

/**
 * Outcome of a touch.
 *
 * @param contact   world position of the contact
 * @param feet      player's feet position at the contact (or take-off spot for jump serves)
 * @param speed     outgoing ball speed (m/s)
 */
public record HitResult(HitType type, double quality, Grade grade, Vec3 contact, Vec3 feet, double speed,
                        boolean airborne) {
}
