package dev.powderpass.lift.geom;

/** Terrain height lookup used while laying out a lift. */
@FunctionalInterface
public interface GroundFunction {

    /** Height of the terrain surface (top of the highest solid block) at (x, z). */
    double groundAt(double x, double z);
}
