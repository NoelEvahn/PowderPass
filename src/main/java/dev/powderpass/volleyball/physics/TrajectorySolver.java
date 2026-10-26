package dev.powderpass.volleyball.physics;

import dev.powderpass.volleyball.math.Vec3;

/**
 * Finds launch velocities that land the ball on a target using the same flight model as the live simulation
 * (gravity, drag, Magnus). Knuckle noise is ignored, so float serves drift away from the solution by design.
 */
public final class TrajectorySolver {

    /** Matches the live simulation's sub-step at normal ball speeds. */
    private static final double DT = 1.0 / 80.0;
    private static final double MAX_TIME = 6.0;

    private final PhysicsParams params;
    private final BallIntegrator integrator = new BallIntegrator();
    private final BallBody probe = new BallBody(1);
    private final Vec3 tmp = new Vec3();

    public TrajectorySolver(PhysicsParams params) {
        this.params = params;
    }

    /** Result of a simulated flight. */
    public record Flight(double landingDistance, double apexY, double time, Vec3 landing, boolean landed) {
    }

    /**
     * Simulates a free flight until the ball centre drops to {@code floorY + radius}.
     *
     * @param horizontalDir unit horizontal direction used to measure the landing distance
     */
    public Flight simulate(Vec3 start, Vec3 vel, Vec3 spin, double floorY, Vec3 horizontalDir) {
        probe.pos.set(start);
        probe.vel.set(vel);
        probe.spin.set(spin);
        probe.flightTime = 0;
        probe.knuckle = false;
        double ground = floorY + params.radius();
        double apex = start.y;
        double t = 0;
        double prevX = start.x;
        double prevY = start.y;
        double prevZ = start.z;
        while (t < MAX_TIME) {
            prevX = probe.pos.x;
            prevY = probe.pos.y;
            prevZ = probe.pos.z;
            integrator.step(probe, params, DT);
            t += DT;
            apex = Math.max(apex, probe.pos.y);
            if (probe.pos.y <= ground && probe.vel.y < 0) {
                double f = (prevY - ground) / Math.max(1e-9, prevY - probe.pos.y);
                Vec3 landing = new Vec3(prevX + (probe.pos.x - prevX) * f, ground, prevZ + (probe.pos.z - prevZ) * f);
                double dist = (landing.x - start.x) * horizontalDir.x + (landing.z - start.z) * horizontalDir.z;
                return new Flight(dist, apex, t, landing, true);
            }
        }
        double dist = (probe.pos.x - start.x) * horizontalDir.x + (probe.pos.z - start.z) * horizontalDir.z;
        return new Flight(dist, apex, t, probe.pos.copy(), false);
    }

    /**
     * Arc shot (bump / set / underhand serve): reaches roughly {@code apexAboveStart} above the contact point and
     * lands at {@code target} (on the floor).
     */
    public Vec3 solveArc(Vec3 start, Vec3 target, double floorY, double apexAboveStart, Vec3 spin) {
        Vec3 dir = horizontalDir(start, target);
        double distance = horizontalDistance(start, target);
        double apex = Math.max(0.2, apexAboveStart);
        double vy = Math.sqrt(2 * params.gravity() * apex);
        double vh = 0;
        for (int outer = 0; outer < 2; outer++) {
            vh = bisectHorizontal(start, dir, distance, vy, spin, floorY);
            Flight f = simulate(start, velocity(dir, vh, vy), spin, floorY, dir);
            double reached = f.apexY() - start.y;
            if (reached > 0.05) {
                vy *= Math.sqrt(apex / reached);
            }
        }
        vh = bisectHorizontal(start, dir, distance, vy, spin, floorY);
        return velocity(dir, vh, vy);
    }

    private double bisectHorizontal(Vec3 start, Vec3 dir, double distance, double vy, Vec3 spin, double floorY) {
        // vacuum flight time gives a good bracket, drag only ever needs a faster launch
        double g = params.gravity();
        double drop = Math.max(0, start.y - floorY - params.radius());
        double t = (vy + Math.sqrt(vy * vy + 2 * g * drop)) / g;
        double guess = distance / Math.max(0.1, t);
        double lo = 0;
        double hi = Math.min(45, guess * 2.2 + 1.0);
        for (int i = 0; i < 18; i++) {
            double mid = (lo + hi) * 0.5;
            Flight f = simulate(start, velocity(dir, mid, vy), spin, floorY, dir);
            if (f.landingDistance() < distance) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return (lo + hi) * 0.5;
    }

    /**
     * Driven shot (spike / jump serve / float serve): fixed launch speed, solves the elevation so the ball lands on
     * the target. Prefers the low (flat) trajectory. If out of range, uses the maximum range angle.
     *
     * @param spinFactory creates the spin vector for a given launch velocity (spin axis depends on direction)
     */
    public Vec3 solveDriven(Vec3 start, Vec3 target, double floorY, double speed, SpinFactory spinFactory) {
        Vec3 dir = horizontalDir(start, target);
        double distance = horizontalDistance(start, target);
        double minAngle = Math.toRadians(-80);
        // coarse scan for the angle of maximum range
        double bestAngle = minAngle;
        double bestRange = Double.NEGATIVE_INFINITY;
        for (int i = 0; i <= 26; i++) {
            double a = minAngle + i * Math.toRadians(5);
            double range = rangeAt(start, dir, speed, a, spinFactory, floorY);
            if (range > bestRange) {
                bestRange = range;
                bestAngle = a;
            }
        }
        if (bestRange < distance) {
            return directed(dir, speed, bestAngle);
        }
        double lo = minAngle;
        double hi = bestAngle;
        if (rangeAt(start, dir, speed, lo, spinFactory, floorY) > distance) {
            return directed(dir, speed, lo);
        }
        for (int i = 0; i < 18; i++) {
            double mid = (lo + hi) * 0.5;
            if (rangeAt(start, dir, speed, mid, spinFactory, floorY) < distance) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return directed(dir, speed, (lo + hi) * 0.5);
    }

    private double rangeAt(Vec3 start, Vec3 dir, double speed, double angle, SpinFactory spinFactory, double floorY) {
        Vec3 v = directed(dir, speed, angle);
        return simulate(start, v, spinFactory.spinFor(v), floorY, dir).landingDistance();
    }

    /** Predicts where a ball launched with the given state lands. */
    public Vec3 predictLanding(Vec3 start, Vec3 vel, Vec3 spin, double floorY) {
        tmp.set(vel.x, 0, vel.z);
        if (tmp.lengthSq() < 1e-9) {
            tmp.set(1, 0, 0);
        }
        tmp.normalize();
        return simulate(start, vel, spin, floorY, tmp).landing();
    }

    public static Vec3 horizontalDir(Vec3 from, Vec3 to) {
        Vec3 d = new Vec3(to.x - from.x, 0, to.z - from.z);
        if (d.lengthSq() < 1e-9) {
            return new Vec3(1, 0, 0);
        }
        return d.normalize();
    }

    public static double horizontalDistance(Vec3 a, Vec3 b) {
        double dx = b.x - a.x;
        double dz = b.z - a.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static Vec3 velocity(Vec3 dir, double vh, double vy) {
        return new Vec3(dir.x * vh, vy, dir.z * vh);
    }

    private static Vec3 directed(Vec3 dir, double speed, double angle) {
        double c = Math.cos(angle);
        return new Vec3(dir.x * speed * c, speed * Math.sin(angle), dir.z * speed * c);
    }

    /** Builds the spin vector for a launch velocity. */
    @FunctionalInterface
    public interface SpinFactory {
        SpinFactory NONE = v -> new Vec3();

        Vec3 spinFor(Vec3 velocity);

        /** Topspin (positive rate) or backspin (negative) about the horizontal axis perpendicular to the flight. */
        static SpinFactory topspin(double radPerSecond) {
            return v -> {
                Vec3 h = new Vec3(v.x, 0, v.z);
                if (h.lengthSq() < 1e-9) {
                    return new Vec3();
                }
                h.normalize();
                // axis = up x dir  => (w x v) points down for positive rates
                return new Vec3(h.z, 0, -h.x).mul(radPerSecond);
            };
        }
    }
}
