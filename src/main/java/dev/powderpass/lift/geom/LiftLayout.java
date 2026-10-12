package dev.powderpass.lift.geom;

import org.joml.Vector3d;

import java.util.List;

/**
 * The computed geometry of a chairlift: the closed haul-rope loop, the towers holding it up and the arc-length
 * ranges of the loading and unloading zones.
 */
public final class LiftLayout {

    /** A support tower standing on the lift axis. */
    public record Tower(double x, double baseY, double z, double cableY, double yaw, double sUp, double sDown) {
    }

    /** A range of arc length along the rope. Handles ranges that wrap around the loop seam. */
    public record Zone(double start, double end) {
        public boolean contains(double s, double length) {
            if (start <= end) {
                return s >= start && s <= end;
            }
            return s >= start || s <= end;
        }
    }

    private final CablePath path;
    private final List<Tower> towers;
    private final Vector3d bottomWheel;
    private final Vector3d topWheel;
    private final double axisX;
    private final double axisZ;
    private final double rightX;
    private final double rightZ;
    private final double hanger;
    private final double wheelRadius;
    private final Zone bottomLoad;
    private final Zone topUnload;
    private final Zone topLoad;
    private final Zone bottomUnload;
    private final double[] speedFactor;
    private final double sampleStep;
    private final double rideLengthUp;
    private final List<String> warnings;

    LiftLayout(CablePath path, List<Tower> towers, Vector3d bottomWheel, Vector3d topWheel,
               double axisX, double axisZ, double rightX, double rightZ, double hanger, double wheelRadius,
               Zone bottomLoad, Zone topUnload, Zone topLoad, Zone bottomUnload,
               double[] speedFactor, double sampleStep, double rideLengthUp, List<String> warnings) {
        this.path = path;
        this.towers = List.copyOf(towers);
        this.bottomWheel = bottomWheel;
        this.topWheel = topWheel;
        this.axisX = axisX;
        this.axisZ = axisZ;
        this.rightX = rightX;
        this.rightZ = rightZ;
        this.hanger = hanger;
        this.wheelRadius = wheelRadius;
        this.bottomLoad = bottomLoad;
        this.topUnload = topUnload;
        this.topLoad = topLoad;
        this.bottomUnload = bottomUnload;
        this.speedFactor = speedFactor;
        this.sampleStep = sampleStep;
        this.rideLengthUp = rideLengthUp;
        this.warnings = List.copyOf(warnings);
    }

    public CablePath path() {
        return path;
    }

    public List<Tower> towers() {
        return towers;
    }

    public Vector3d bottomWheel() {
        return new Vector3d(bottomWheel);
    }

    public Vector3d topWheel() {
        return new Vector3d(topWheel);
    }

    public double axisX() {
        return axisX;
    }

    public double axisZ() {
        return axisZ;
    }

    public double rightX() {
        return rightX;
    }

    public double rightZ() {
        return rightZ;
    }

    /** Yaw (Minecraft degrees) of the uphill direction. */
    public double axisYaw() {
        return Math.toDegrees(Math.atan2(-axisX, axisZ));
    }

    public double hanger() {
        return hanger;
    }

    public double wheelRadius() {
        return wheelRadius;
    }

    public Zone bottomLoad() {
        return bottomLoad;
    }

    public Zone topUnload() {
        return topUnload;
    }

    public Zone topLoad() {
        return topLoad;
    }

    public Zone bottomUnload() {
        return bottomUnload;
    }

    public double rideLengthUp() {
        return rideLengthUp;
    }

    public List<String> warnings() {
        return warnings;
    }

    /** Rope speed multiplier at arc length {@code s} (slower in stations). */
    public double speedFactor(double s) {
        double w = path.wrap(s) / sampleStep;
        int i = (int) Math.floor(w);
        int n = speedFactor.length;
        double t = w - i;
        double a = speedFactor[Math.floorMod(i, n)];
        double b = speedFactor[Math.floorMod(i + 1, n)];
        return a + (b - a) * t;
    }
}
