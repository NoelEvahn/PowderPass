package dev.powderpass.util;

import org.joml.Vector3d;

/** Small numeric helpers shared by the physics, lift and course code. Pure Java, no server types. */
public final class MathUtil {

    private MathUtil() {
    }

    /** Wraps an angle in degrees to the range [-180, 180). */
    public static double wrapDegrees(double degrees) {
        double d = degrees % 360.0;
        if (d >= 180.0) {
            d -= 360.0;
        } else if (d < -180.0) {
            d += 360.0;
        }
        return d;
    }

    public static double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }

    public static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Minecraft-style yaw (degrees) to a horizontal unit forward vector. Yaw 0 = +Z, yaw 90 = -X. */
    public static Vector3d forward(double yawDegrees, Vector3d out) {
        double r = Math.toRadians(yawDegrees);
        return out.set(-Math.sin(r), 0.0, Math.cos(r));
    }

    /** Minecraft-style yaw (degrees) of a horizontal direction. */
    public static double yawOf(double dx, double dz) {
        return Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** Converts blocks per tick to kilometres per hour (1 block = 1 metre, 20 ticks per second). */
    public static double toKmh(double blocksPerTick) {
        return blocksPerTick * 72.0;
    }

    public static double smoothstep(double edge0, double edge1, double x) {
        double t = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }
}
