package dev.powderpass.physics;

import java.util.EnumMap;
import java.util.Map;

/**
 * Tunable constants for one rider. All speeds are in blocks per tick and all angles in degrees.
 * Instances are immutable; derived variants are produced with the {@code with*} methods.
 */
public record SkiParams(
        double gravity,
        double airDrag,
        double tuckDragFactor,
        double brakeFriction,
        double turnRate,
        double turnSpeedFalloff,
        double hardCarveYawOffset,
        double hardCarveTurnFactor,
        double tuckTurnFactor,
        double edgeGrip,
        double carveEfficiency,
        double pushAccel,
        double pushMaxSpeed,
        double jumpBase,
        double jumpPerChargeTick,
        int maxJumpCharge,
        double launchGap,
        double maxSpeed,
        double wallCrashSpeed,
        double landingTolerance,
        double flipTolerance,
        double safeImpact,
        double crashImpact,
        double airSpinRate,
        double airFlipRate,
        double smoothingRadius,
        double cliffSlope,
        double speedMultiplier,
        double gripMultiplier,
        SteeringMode steering,
        Map<SurfaceKind, Surface> surfaces) {

    /** Friction coefficient and edge-grip multiplier of a surface. */
    public record Surface(double friction, double grip) {
    }

    public SkiParams {
        EnumMap<SurfaceKind, Surface> copy = new EnumMap<>(SurfaceKind.class);
        copy.putAll(defaultSurfaces());
        copy.putAll(surfaces);
        surfaces = Map.copyOf(copy);
    }

    public Surface surface(SurfaceKind kind) {
        return surfaces.get(kind);
    }

    public static Map<SurfaceKind, Surface> defaultSurfaces() {
        EnumMap<SurfaceKind, Surface> map = new EnumMap<>(SurfaceKind.class);
        map.put(SurfaceKind.SNOW, new Surface(0.050, 1.00));
        map.put(SurfaceKind.POWDER, new Surface(0.110, 1.15));
        map.put(SurfaceKind.ICE, new Surface(0.018, 0.55));
        map.put(SurfaceKind.ROUGH, new Surface(0.450, 1.20));
        map.put(SurfaceKind.LIQUID, new Surface(1.000, 1.00));
        map.put(SurfaceKind.NONE, new Surface(0.050, 1.00));
        return map;
    }

    /** Sensible defaults for skis. */
    public static SkiParams defaultSkis() {
        return new SkiParams(
                0.060, 0.012, 0.55, 0.16,
                9.0, 0.9, 35.0, 1.5, 0.55,
                0.50, 0.85,
                0.012, 0.30,
                0.30, 0.025, 12,
                0.25, 2.2, 0.55,
                50.0, 70.0,
                0.75, 1.35,
                20.0, 18.0,
                2.0, 1.25,
                1.0, 1.0,
                SteeringMode.LOOK,
                defaultSurfaces());
    }

    /** Sensible defaults for a snowboard: grippy, spins easily, poor at skating on the flat. */
    public static SkiParams defaultBoard() {
        SkiParams s = defaultSkis();
        return new SkiParams(
                s.gravity, 0.013, 0.60, 0.14,
                10.0, 0.9, 40.0, 1.6, 0.6,
                0.45, 0.85,
                0.006, 0.20,
                0.36, 0.024, 12,
                s.launchGap, s.maxSpeed, s.wallCrashSpeed,
                55.0, 70.0,
                s.safeImpact, s.crashImpact,
                24.0, 18.0,
                s.smoothingRadius, s.cliffSlope,
                1.0, 1.0,
                s.steering,
                s.surfaces);
    }

    public SkiParams withTier(double speedMul, double gripMul) {
        return new SkiParams(gravity, airDrag, tuckDragFactor, brakeFriction, turnRate, turnSpeedFalloff,
                hardCarveYawOffset, hardCarveTurnFactor, tuckTurnFactor, edgeGrip, carveEfficiency, pushAccel,
                pushMaxSpeed, jumpBase, jumpPerChargeTick, maxJumpCharge, launchGap, maxSpeed, wallCrashSpeed,
                landingTolerance, flipTolerance, safeImpact, crashImpact, airSpinRate, airFlipRate,
                smoothingRadius, cliffSlope, speedMul, gripMul, steering, surfaces);
    }

    public SkiParams withSteering(SteeringMode mode) {
        return new SkiParams(gravity, airDrag, tuckDragFactor, brakeFriction, turnRate, turnSpeedFalloff,
                hardCarveYawOffset, hardCarveTurnFactor, tuckTurnFactor, edgeGrip, carveEfficiency, pushAccel,
                pushMaxSpeed, jumpBase, jumpPerChargeTick, maxJumpCharge, launchGap, maxSpeed, wallCrashSpeed,
                landingTolerance, flipTolerance, safeImpact, crashImpact, airSpinRate, airFlipRate,
                smoothingRadius, cliffSlope, speedMultiplier, gripMultiplier, mode, surfaces);
    }
}
