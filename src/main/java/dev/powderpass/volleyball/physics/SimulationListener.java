package dev.powderpass.volleyball.physics;

import dev.powderpass.volleyball.math.Vec3;

/** Receives contact events produced while a ball is simulated. Positions are world space unless noted. */
public interface SimulationListener {

    SimulationListener NONE = new SimulationListener() { };

    /** The ball touched a solid block. {@code normal} points from the surface toward the ball. */
    default void onBlockContact(Vec3 position, Vec3 normal, double impactSpeed, Aabb box) {
    }

    /** The ball touched part of the net system. {@code localPoint} is court-local (x=u, y, z=w). */
    default void onNetContact(NetContact contact, Vec3 localPoint, double impactSpeed) {
    }

    /**
     * The ball centre crossed the vertical plane of the net.
     *
     * @param fromSide side (-1/+1) it came from
     * @param localW   lateral position along the net at the crossing
     * @param localY   height at the crossing
     */
    default void onNetPlaneCrossed(int fromSide, double localW, double localY) {
    }
}
