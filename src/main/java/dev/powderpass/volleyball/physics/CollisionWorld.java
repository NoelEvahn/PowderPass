package dev.powderpass.volleyball.physics;

import java.util.List;

/** Supplies solid geometry around the ball. Implementations must not allocate excessively. */
public interface CollisionWorld {

    CollisionWorld EMPTY = (minX, minY, minZ, maxX, maxY, maxZ, out) -> { };

    /** Adds every solid box intersecting the query region to {@code out}. */
    void collect(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, List<Aabb> out);
}
