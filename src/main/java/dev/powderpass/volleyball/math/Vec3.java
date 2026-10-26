package dev.powderpass.volleyball.math;

/**
 * Small mutable 3D vector used on the physics hot path.
 * Methods mutate {@code this} and return it for chaining unless stated otherwise.
 */
public final class Vec3 {

    public double x;
    public double y;
    public double z;

    public Vec3() {
    }

    public Vec3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3(Vec3 o) {
        this(o.x, o.y, o.z);
    }

    public static Vec3 of(double x, double y, double z) {
        return new Vec3(x, y, z);
    }

    public Vec3 copy() {
        return new Vec3(x, y, z);
    }

    public Vec3 set(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public Vec3 set(Vec3 o) {
        return set(o.x, o.y, o.z);
    }

    public Vec3 zero() {
        return set(0, 0, 0);
    }

    public Vec3 add(Vec3 o) {
        x += o.x;
        y += o.y;
        z += o.z;
        return this;
    }

    public Vec3 add(double ax, double ay, double az) {
        x += ax;
        y += ay;
        z += az;
        return this;
    }

    /** this += o * s */
    public Vec3 addScaled(Vec3 o, double s) {
        x += o.x * s;
        y += o.y * s;
        z += o.z * s;
        return this;
    }

    public Vec3 sub(Vec3 o) {
        x -= o.x;
        y -= o.y;
        z -= o.z;
        return this;
    }

    public Vec3 mul(double s) {
        x *= s;
        y *= s;
        z *= s;
        return this;
    }

    public double dot(Vec3 o) {
        return x * o.x + y * o.y + z * o.z;
    }

    /** this = this x o */
    public Vec3 cross(Vec3 o) {
        double cx = y * o.z - z * o.y;
        double cy = z * o.x - x * o.z;
        double cz = x * o.y - y * o.x;
        return set(cx, cy, cz);
    }

    /** Returns a new vector a x b. */
    public static Vec3 cross(Vec3 a, Vec3 b) {
        return new Vec3(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x);
    }

    public double lengthSq() {
        return x * x + y * y + z * z;
    }

    public double length() {
        return Math.sqrt(lengthSq());
    }

    public double horizontalLength() {
        return Math.sqrt(x * x + z * z);
    }

    public double distance(Vec3 o) {
        return Math.sqrt(distanceSq(o));
    }

    public double distanceSq(Vec3 o) {
        double dx = x - o.x;
        double dy = y - o.y;
        double dz = z - o.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Normalizes in place; leaves a zero vector untouched. */
    public Vec3 normalize() {
        double l = length();
        if (l > 1e-12) {
            mul(1.0 / l);
        }
        return this;
    }

    public Vec3 lerp(Vec3 to, double t) {
        x += (to.x - x) * t;
        y += (to.y - y) * t;
        z += (to.z - z) * t;
        return this;
    }

    /** Removes the component along unit vector n. */
    public Vec3 rejectFrom(Vec3 n) {
        return addScaled(n, -dot(n));
    }

    public Vec3 clampLength(double max) {
        double l = length();
        if (l > max && l > 1e-12) {
            mul(max / l);
        }
        return this;
    }

    public boolean isFinite() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT, "(%.3f, %.3f, %.3f)", x, y, z);
    }
}
