package dev.powderpass.physics;

/**
 * Read-only view of the world used by the physics engine. Implementations must be cheap to call
 * repeatedly within a tick and must never load chunks.
 */
public interface TerrainSampler {

    /**
     * Height of the walkable top surface of the block column (x, z), searching around {@code yHint}.
     *
     * @return the surface height in world units, or {@link Double#NaN} if no surface is available
     */
    double columnTop(int x, int z, double yHint);

    /** Kind of surface at the top of the column (x, z) near {@code yHint}. */
    SurfaceKind surface(int x, int z, double yHint);

    /** Whether the block at (x, y, z) obstructs a skier's body (walls, trunks, leaves...). */
    boolean solidAt(int x, int y, int z);
}
