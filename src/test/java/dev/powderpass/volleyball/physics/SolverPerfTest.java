package dev.powderpass.volleyball.physics;

import dev.powderpass.volleyball.math.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SolverPerfTest {

    @Test
    void solvesQuickly() {
        TrajectorySolver s = new TrajectorySolver(PhysicsParams.realistic());
        Vec3 start = new Vec3(-8, 2.5, 0);
        // warm up
        for (int i = 0; i < 200; i++) {
            s.solveArc(start, new Vec3(-1, 0, i % 5), 0, 3, new Vec3());
            s.solveDriven(start, new Vec3(6, 0, i % 5), 0, 22, TrajectorySolver.SpinFactory.topspin(40));
        }
        long t0 = System.nanoTime();
        int n = 200;
        for (int i = 0; i < n; i++) {
            s.solveArc(start, new Vec3(-1, 0, i % 5), 0, 3, new Vec3());
        }
        long t1 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            s.solveDriven(start, new Vec3(6, 0, i % 5), 0, 22, TrajectorySolver.SpinFactory.topspin(40));
        }
        long t2 = System.nanoTime();
        double arcMs = (t1 - t0) / 1e6 / n;
        double drivenMs = (t2 - t1) / 1e6 / n;
        System.out.printf("arc %.3f ms, driven %.3f ms%n", arcMs, drivenMs);
        assertTrue(arcMs < 2.0 && drivenMs < 2.0, "solver too slow");
    }
}
