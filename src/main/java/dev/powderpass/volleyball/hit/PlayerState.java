package dev.powderpass.volleyball.hit;

import dev.powderpass.volleyball.math.Vec3;

/** Per-player input and movement tracking. */
final class PlayerState {

    long primaryUntil = -1;
    boolean primarySneak;
    boolean primaryAirborne;
    long secondaryUntil = -1;
    boolean secondarySneak;
    boolean secondaryAirborne;
    long clickTick = -100;
    boolean clickNearBall;

    long lastHitTick = -1000;
    long lastDiveTick = -1000;
    long lastRightClickTick = -1000;

    long blockStart = -1000;
    long blockUntil = -1000;

    boolean onGround = true;
    final Vec3 lastGround = new Vec3();
    long takeoffTick = -1000;
    boolean sprintTakeoff;
    double prevY = Double.NaN;
    double prevX = Double.NaN;
    double prevZ = Double.NaN;
    /** horizontal speed in m/s */
    double groundSpeed;
    /** vertical speed in m/s */
    double vy;
    long lastSeenTick;

    boolean reticle = true;

    boolean blocking(long tick) {
        return tick <= blockUntil;
    }
}
