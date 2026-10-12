package dev.powderpass.ski;

import dev.powderpass.display.Transforms;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;

/**
 * Pure skeletal model of a skier built from boxes. Computes one transformation matrix per {@link RigPart},
 * relative to the vehicle anchor which sits {@code riderOffset} above the snow under the skier.
 *
 * <p>Local frame: +Z = ski tips, +X = rider's left, +Y = up. Skis follow the full slope normal and roll onto
 * their edges in turns; the body takes half the slope tilt (skiers stay upright), leans into the turn, folds
 * into a tuck and swings its arms and poles.
 */
public final class SkierModel {

    /** Height of the head item relative to its size (player heads in item displays sit below their origin). */
    public static final float HEAD_SIZE = 0.9f;

    private SkierModel() {
    }

    public static void compute(RigPose p, float riderOffset, boolean skullHead, Map<RigPart, Matrix4f> out) {
        float c = (float) Math.max(0, Math.min(1, p.crouch()));
        float yawRad = rad(-p.visualYaw());
        Matrix4f base = new Matrix4f().translate(0, -riderOffset, 0).rotateY(yawRad);

        Quaternionf slope = new Quaternionf();
        if (!p.airborne()) {
            Vector3f n = new Vector3f((float) p.nx(), (float) p.ny(), (float) p.nz());
            if (n.lengthSquared() > 1.0e-6f) {
                n.normalize().rotateY(-yawRad);
                slope.rotationTo(0, 1, 0, n.x, n.y, n.z);
            }
        }
        Quaternionf halfSlope = new Quaternionf().slerp(slope, 0.5f);

        Matrix4f flip = new Matrix4f();
        if (Math.abs(p.flip()) > 0.01) {
            flip.translate(0, 0.9f, 0).rotateX(rad(p.flip())).translate(0, -0.9f, 0);
        }
        float lean = (float) p.lean();
        Matrix4f gear = new Matrix4f(base).rotate(slope).mul(flip).rotateZ(rad(-lean * 0.5f));
        Matrix4f body = new Matrix4f(base).rotate(halfSlope).mul(flip).rotateZ(rad(-lean));
        if (p.board()) {
            body.rotateY(rad(90));
        }

        // ---------------------------------------------------------------- gear
        if (p.board()) {
            out.put(RigPart.BOARD, Transforms.box(gear, -0.17f, 0f, -0.75f, 0.34f, 0.05f, 1.5f));
            out.put(RigPart.NOSE, new Matrix4f(gear).translate(-0.17f, 0f, 0.75f).rotateX(rad(-25)).scale(0.34f, 0.04f, 0.22f));
            out.put(RigPart.TAIL, new Matrix4f(gear).translate(-0.17f, 0f, -0.75f).rotateX(rad(25))
                    .translate(0f, 0f, -0.22f).scale(0.34f, 0.04f, 0.22f));
        } else {
            for (int side = 1; side >= -1; side -= 2) {
                float x = side * 0.16f;
                out.put(side > 0 ? RigPart.SKI_L : RigPart.SKI_R, Transforms.box(gear, x - 0.065f, 0f, -0.85f, 0.13f, 0.045f, 1.6f));
                out.put(side > 0 ? RigPart.TIP_L : RigPart.TIP_R, new Matrix4f(gear).translate(x - 0.065f, 0f, 0.75f)
                        .rotateX(rad(-28)).scale(0.13f, 0.035f, 0.3f));
            }
        }

        // ---------------------------------------------------------------- legs and torso
        float legLen = 0.74f - 0.30f * c;
        float footSpread = p.board() ? 0.24f : 0.13f;
        out.put(RigPart.LEG_L, Transforms.box(body, footSpread - 0.1f, 0.045f, -0.11f, 0.2f, legLen, 0.22f));
        out.put(RigPart.LEG_R, Transforms.box(body, -footSpread - 0.1f, 0.045f, -0.11f, 0.2f, legLen, 0.22f));
        float hipY = 0.045f + legLen;
        boolean poling = p.pushPhase() >= 0;
        float torsoPitch = 8f + 50f * c + (poling ? 12f : 0f);
        Matrix4f torso = new Matrix4f(body).translate(0, hipY, 0).rotateX(rad(torsoPitch));
        out.put(RigPart.TORSO, Transforms.box(torso, -0.25f, 0f, -0.14f, 0.5f, 0.62f, 0.28f));

        // ---------------------------------------------------------------- arms and poles
        float armForward;
        float spread;
        float trail = 20f;
        if (p.grab() && p.airborne()) {
            armForward = 80f;
            spread = 15f;
        } else if (p.board()) {
            armForward = 15f + 20f * c;
            spread = p.airborne() ? 55f : 40f;
        } else {
            armForward = 30f + 25f * c;
            spread = p.airborne() ? 30f : 10f;
            if (poling) {
                double wave = Math.sin(p.pushPhase() * Math.PI * 2.0);
                armForward += (float) (25.0 * wave);
                trail = (float) (20.0 - 35.0 * Math.max(0.0, wave));
            }
        }
        for (int side = 1; side >= -1; side -= 2) {
            Matrix4f arm = new Matrix4f(torso).translate(side * 0.33f, 0.58f, 0f)
                    .rotateX(rad(-armForward)).rotateZ(rad(side * spread));
            out.put(side > 0 ? RigPart.ARM_L : RigPart.ARM_R, Transforms.box(arm, -0.085f, -0.6f, -0.085f, 0.17f, 0.6f, 0.17f));
            if (!p.board()) {
                Matrix4f pole = new Matrix4f(arm).translate(0f, -0.56f, 0.02f).rotateX(rad(armForward + torsoPitch + trail));
                out.put(side > 0 ? RigPart.POLE_L : RigPart.POLE_R, Transforms.box(pole, -0.025f, -1.15f, -0.025f, 0.05f, 1.3f, 0.05f));
            }
        }

        // ---------------------------------------------------------------- head
        double relativeYaw = p.board() ? p.headYaw() + 90.0 : p.headYaw();
        float headYaw = (float) Math.max(-80, Math.min(80, wrap(relativeYaw)));
        float headPitch = (float) Math.max(-50, Math.min(50, p.headPitch()));
        Matrix4f neck = new Matrix4f(torso).translate(0f, 0.64f, 0f).rotateX(rad(-torsoPitch * 0.75f))
                .rotateY(rad(-headYaw)).rotateX(rad(headPitch));
        float hs = HEAD_SIZE;
        if (skullHead) {
            out.put(RigPart.HEAD, new Matrix4f(neck).translate(0f, 0.5f * hs, 0f).scale(hs));
        } else {
            float cube = 0.5f * hs;
            out.put(RigPart.HEAD, new Matrix4f(neck).translate(0f, cube / 2f, 0f).scale(cube));
        }
        out.put(RigPart.GOGGLES, Transforms.box(neck, -0.27f * hs, 0.2f * hs, 0.25f * hs - 0.01f, 0.54f * hs, 0.1f * hs, 0.04f));
        out.put(RigPart.POMPOM, Transforms.box(neck, -0.07f, 0.5f * hs - 0.01f, -0.07f, 0.14f, 0.12f, 0.14f));

        out.put(RigPart.NAMETAG, new Matrix4f().translate(0f, 2.2f - riderOffset, 0f));
    }

    private static float rad(double degrees) {
        return (float) Math.toRadians(degrees);
    }

    private static double wrap(double d) {
        double w = d % 360.0;
        if (w >= 180.0) {
            w -= 360.0;
        } else if (w < -180.0) {
            w += 360.0;
        }
        return w;
    }
}
