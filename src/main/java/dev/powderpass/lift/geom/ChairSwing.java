package dev.powderpass.lift.geom;

/**
 * A damped pendulum for one chair. Lateral acceleration (rounding a bullwheel) swings the chair outwards,
 * tangential acceleration (slowing into a station) tips it forwards, and riders boarding add a bounce.
 * Angles are in degrees.
 */
public final class ChairSwing {

    private static final double STIFFNESS = 0.035;
    private static final double DAMPING = 0.07;
    private static final double INPUT_GAIN = 260.0;
    private static final double MAX_ANGLE = 18.0;

    private double roll;
    private double rollVelocity;
    private double pitch;
    private double pitchVelocity;

    /**
     * Advances the pendulum one tick.
     *
     * @param lateralAccel    centripetal acceleration in blocks/tick², positive towards the chair's left
     * @param tangentialAccel acceleration along the direction of travel in blocks/tick²
     */
    public void tick(double lateralAccel, double tangentialAccel) {
        double rollAcc = -STIFFNESS * roll - DAMPING * rollVelocity + lateralAccel * INPUT_GAIN;
        rollVelocity += rollAcc;
        roll = clamp(roll + rollVelocity);
        double pitchAcc = -STIFFNESS * pitch - DAMPING * pitchVelocity + tangentialAccel * INPUT_GAIN;
        pitchVelocity += pitchAcc;
        pitch = clamp(pitch + pitchVelocity);
    }

    /** Kicks the chair, e.g. when somebody sits down. */
    public void impulse(double rollKick, double pitchKick) {
        rollVelocity += rollKick;
        pitchVelocity += pitchKick;
    }

    /** Roll in degrees, positive = the chair's bottom swings to its right (top leans left). */
    public double roll() {
        return roll;
    }

    /** Pitch in degrees, positive = the chair's bottom swings backwards. */
    public double pitch() {
        return pitch;
    }

    private static double clamp(double angle) {
        return Math.max(-MAX_ANGLE, Math.min(MAX_ANGLE, angle));
    }
}
