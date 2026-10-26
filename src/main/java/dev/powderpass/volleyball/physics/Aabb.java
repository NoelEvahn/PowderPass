package dev.powderpass.volleyball.physics;

/** Axis aligned solid box in world space. */
public record Aabb(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
}
