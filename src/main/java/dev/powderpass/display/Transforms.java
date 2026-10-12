package dev.powderpass.display;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Builders for display transformation matrices. */
public final class Transforms {

    private static final Vector3f UNIT_Z = new Vector3f(0, 0, 1);

    private Transforms() {
    }

    /**
     * A block display box with its minimum corner at (x, y, z) and the given size, in the frame of {@code parent}.
     */
    public static Matrix4f box(Matrix4f parent, float x, float y, float z, float sx, float sy, float sz) {
        return new Matrix4f(parent).translate(x, y, z).scale(sx, sy, sz);
    }

    /** A box centred horizontally on (cx, cz) with its bottom at y. */
    public static Matrix4f pillar(Matrix4f parent, float cx, float y, float cz, float sx, float sy, float sz) {
        return box(parent, cx - sx / 2f, y, cz - sz / 2f, sx, sy, sz);
    }

    /** Rotation about the vertical axis matching a Minecraft yaw, so local +Z points along {@code yaw}. */
    public static Matrix4f yaw(float yawDegrees) {
        return new Matrix4f().rotateY((float) Math.toRadians(-yawDegrees));
    }

    /**
     * A thin block display beam from {@code a} to {@code b} (relative to the entity), {@code thickness} wide.
     */
    public static Matrix4f beam(Vector3f a, Vector3f b, float thickness) {
        Vector3f dir = new Vector3f(b).sub(a);
        float len = dir.length();
        if (len < 1.0e-5f) {
            return new Matrix4f().scale(0f);
        }
        Quaternionf rot = new Quaternionf().rotationTo(UNIT_Z, dir.div(len));
        return new Matrix4f().translate(a).rotate(rot).translate(-thickness / 2f, -thickness / 2f, 0f)
                .scale(thickness, thickness, len);
    }

    /** True when two matrices are equal within {@code epsilon} element-wise. */
    public static boolean close(Matrix4f a, Matrix4f b, float epsilon) {
        if (a == null || b == null) {
            return false;
        }
        return a.equals(b, epsilon);
    }
}
