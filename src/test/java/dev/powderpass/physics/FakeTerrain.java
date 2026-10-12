package dev.powderpass.physics;

import java.util.HashSet;
import java.util.Set;
import java.util.function.DoubleBinaryOperator;

/** Function-defined terrain for tests. Heights are quantised like Minecraft snow layers or full blocks. */
final class FakeTerrain implements TerrainSampler {

    private final DoubleBinaryOperator height;
    private final double quantum;
    private final Set<Long> walls = new HashSet<>();
    private SurfaceKind kind = SurfaceKind.SNOW;

    FakeTerrain(DoubleBinaryOperator height, double quantum) {
        this.height = height;
        this.quantum = quantum;
    }

    static FakeTerrain flat(double y) {
        return new FakeTerrain((x, z) -> y, 1.0);
    }

    /** A slope descending towards +Z at the given angle, built from full blocks. */
    static FakeTerrain slope(double degrees, double quantum) {
        double grade = Math.tan(Math.toRadians(degrees));
        return new FakeTerrain((x, z) -> 200.0 - z * grade, quantum);
    }

    FakeTerrain surface(SurfaceKind kind) {
        this.kind = kind;
        return this;
    }

    /** Adds a solid wall column from y=0 to 400 at (x, z). */
    FakeTerrain wall(int x, int z) {
        walls.add(key(x, z));
        return this;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    @Override
    public double columnTop(int x, int z, double yHint) {
        if (walls.contains(key(x, z))) {
            return 400.0;
        }
        double h = height.applyAsDouble(x + 0.5, z + 0.5);
        return Math.floor(h / quantum) * quantum;
    }

    @Override
    public SurfaceKind surface(int x, int z, double yHint) {
        return kind;
    }

    @Override
    public boolean solidAt(int x, int y, int z) {
        if (walls.contains(key(x, z))) {
            return true;
        }
        return y < columnTop(x, z, y) - 1.0e-9;
    }
}
