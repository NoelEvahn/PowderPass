package dev.powderpass.ski;

/**
 * Everything needed to pose an animated skier for one frame.
 *
 * @param visualYaw  yaw of the skis including any in-air spin (Minecraft degrees)
 * @param headYaw    where the rider looks relative to the skis (degrees, Minecraft convention)
 * @param headPitch  rider camera pitch (degrees, positive = looking down)
 * @param nx         slope normal x (world)
 * @param ny         slope normal y (world)
 * @param nz         slope normal z (world)
 * @param lean       lean into the turn, degrees, positive = left
 * @param crouch     0 = tall, 1 = full tuck
 * @param flip       in-air flip rotation in degrees, positive = forwards
 * @param airborne   whether the skier is in the air
 * @param grab       holding a grab
 * @param pushPhase  0..1 pole-plant animation phase, or a negative value when not poling
 * @param board      snowboard stance instead of skis
 */
public record RigPose(double visualYaw, double headYaw, double headPitch, double nx, double ny, double nz,
                      double lean, double crouch, double flip, boolean airborne, boolean grab, double pushPhase,
                      boolean board) {
}
