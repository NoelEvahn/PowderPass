package dev.powderpass.volleyball.physics;

/**
 * Tunable physical constants. Units: blocks == metres, seconds, kilograms.
 */
public record PhysicsParams(
        double gravity,
        double airDensity,
        double radius,
        double mass,
        double dragCoefficientSlow,
        double dragCoefficientFast,
        double dragCrisisSpeed,
        double dragCrisisWidth,
        double liftSlope,
        double maxLiftCoefficient,
        double spinDecaySeconds,
        double knuckleStrength,
        double knuckleSpinThreshold,
        double floorRestitution,
        double floorFriction,
        double wallRestitution,
        double wallFriction,
        double netRestitution,
        double netTangentialDamping,
        double tapeRestitution,
        double antennaRestitution,
        double rollingResistance,
        double restSpeed,
        int maxSubsteps
) {

    /** Real-world volleyball values (FIVB ball: 260-280 g, 65-67 cm circumference). */
    public static PhysicsParams realistic() {
        return new PhysicsParams(
                9.81, 1.2, 0.105, 0.27,
                0.47, 0.22, 17.0, 1.6,
                1.0, 0.35, 4.0,
                1.0, 2.5,
                0.70, 0.45, 0.55, 0.35,
                0.12, 0.55, 0.35, 0.45,
                0.6, 0.08, 32);
    }

    public double area() {
        return Math.PI * radius * radius;
    }

    /** 0.5 * rho * A / m, multiply by a coefficient and v^2 to get an acceleration. */
    public double aeroFactor() {
        return 0.5 * airDensity * area() / mass;
    }

    /** Drag coefficient with a smooth drag-crisis transition (smooth panel balls drop around 15-20 m/s). */
    public double dragCoefficient(double speed) {
        double t = 1.0 / (1.0 + Math.exp((speed - dragCrisisSpeed) / Math.max(0.05, dragCrisisWidth)));
        return dragCoefficientFast + (dragCoefficientSlow - dragCoefficientFast) * t;
    }
}
