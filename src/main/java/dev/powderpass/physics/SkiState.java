package dev.powderpass.physics;

import org.joml.Vector3d;

/** Mutable simulation state of one rider. Positions are the centre of the skis on the snow. */
public final class SkiState {

    public final Vector3d pos = new Vector3d();
    public final Vector3d vel = new Vector3d();
    public final Vector3d normal = new Vector3d(0, 1, 0);

    /** Direction the skis point, Minecraft yaw degrees. */
    public double heading;
    public boolean grounded = true;
    public SurfaceKind surface = SurfaceKind.SNOW;

    // --- air ---
    public int airTicks;
    /** Accumulated spin while airborne (degrees, signed). */
    public double spin;
    /** Accumulated flip while airborne (degrees, positive = front flip). */
    public double flip;
    public int grabTicks;
    public double launchY;
    public double peakY;

    // --- ground ---
    public int jumpCharge;
    public int groundTicks;

    // --- visual telemetry (smoothed) ---
    /** Lean into the turn in degrees, positive = leaning left. */
    public double lean;
    /** 0 = standing tall, 1 = full tuck. */
    public double crouch;
    /** Lateral speed scrubbed off this tick (drives spray particles). */
    public double skid;
    /** Heading change applied this tick (degrees). */
    public double turn;
    public boolean braking;
    public boolean tucking;
    public boolean pushing;

    public SkiState(double x, double y, double z, double heading) {
        pos.set(x, y, z);
        this.heading = heading;
        this.launchY = y;
        this.peakY = y;
    }

    public double speed() {
        return vel.length();
    }

    public double horizontalSpeed() {
        return Math.sqrt(vel.x * vel.x + vel.z * vel.z);
    }

    /** Slope steepness under the skier in degrees. */
    public double slopeDegrees() {
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, normal.y))));
    }
}
