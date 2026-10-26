package dev.powderpass.volleyball.physics;

import dev.powderpass.volleyball.math.Vec3;

/** Rigid body state of a ball. */
public final class BallBody {

    public final Vec3 pos = new Vec3();
    public final Vec3 vel = new Vec3();
    /** Angular velocity in rad/s (world space, right-hand rule). */
    public final Vec3 spin = new Vec3();
    /** Seconds since the last hit, drives the knuckle noise. */
    public double flightTime;
    public boolean resting;
    public boolean onGround;
    /** Float serve wobble enabled for the current flight. */
    public boolean knuckle;
    public final KnuckleNoise noise;

    public BallBody(long seed) {
        this.noise = new KnuckleNoise(seed);
    }

    public void launch(Vec3 velocity, Vec3 angular, boolean knuckleFlight) {
        vel.set(velocity);
        spin.set(angular);
        flightTime = 0;
        resting = false;
        onGround = false;
        knuckle = knuckleFlight;
        noise.reseed();
    }

    public double speed() {
        return vel.length();
    }
}
