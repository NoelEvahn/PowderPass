package com.powderpass.backrooms.creature;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parametric poses for the creature skeletons in {@link Bodies}: bone name to transform (applied around rest-pose
 * pivots). Shared by the live animation and the design renders so both show the same thing.
 */
final class Poses {
    private Poses() {
    }

    private static Matrix4f about(double x, double y, double z, Matrix4f m) {
        return new Matrix4f().translate((float) x, (float) y, (float) z).mul(m).translate((float) -x, (float) -y, (float) -z);
    }

    private static Matrix4f rx(double r) {
        return new Matrix4f().rotateX((float) r);
    }

    private static Matrix4f ry(double r) {
        return new Matrix4f().rotateY((float) r);
    }

    private static Matrix4f rz(double r) {
        return new Matrix4f().rotateZ((float) r);
    }

    // ------------------------------------------------------------------ smiler

    /**
     * @param open   mouth open 0..1 (lower teeth drop)
     * @param wide   grin width factor (1 = rest)
     * @param reach  arms reaching out of the dark 0..1
     * @param emerge extra eyes sliding out of the mass 0..1
     * @param tilt   head tilt, degrees
     * @param blink  eyes squeezed shut
     * @param breath -1..1 slow swell of the mass
     */
    static Map<String, Matrix4f> smiler(double open, double wide, double reach, double emerge, double tilt, boolean blink, double breath) {
        Map<String, Matrix4f> p = new LinkedHashMap<>();
        double fy = (Bodies.SMILER_MOUTH + Bodies.SMILER_EYES) / 2;
        Matrix4f face = about(0, fy, 0, rz(Math.toRadians(tilt)));
        p.put("eye", new Matrix4f(face).mul(about(0, Bodies.SMILER_EYES, 0, new Matrix4f().scale(1f, blink ? 0.12f : 1f, 1f))));
        p.put("eyes2", new Matrix4f(face).translate(0, 0, (float) (0.4 * emerge)));
        p.put("grin", new Matrix4f(face).mul(about(0, Bodies.SMILER_MOUTH, 0, new Matrix4f().scale((float) wide, 1f, 1f))));
        p.put("grin.low", new Matrix4f().translate(0, (float) (-0.26 * open), (float) (0.02 * open)));
        p.put("mass", about(0, 1.55, -0.3, new Matrix4f().scale((float) (1 + 0.04 * breath), (float) (1 + 0.03 * breath), 1f)));
        for (int s = -1; s <= 1; s += 2) {
            String arm = s < 0 ? "arm_l" : "arm_r";
            double sx = s * Bodies.SMILER_SHOULDER_X;
            p.put(arm, about(sx, Bodies.SMILER_SHOULDER_Y, -0.3, new Matrix4f().rotateX((float) (-0.95 * reach)).rotateZ((float) (-s * 0.12 * reach))));
            p.put(arm + ".fore", about(s * 0.92, 1.16, -0.05, rx(-0.3 * reach)));
        }
        return p;
    }

    // ------------------------------------------------------------------ faceling

    /**
     * @param tilt  head tilt around the neck, degrees (it likes ninety)
     * @param mouth the seam where a mouth should be, 0 shut .. 1 gaping far past the chin
     * @param curl  finger curl -1..1
     */
    static Map<String, Matrix4f> faceling(double tilt, double mouth, double curl, double breath) {
        Map<String, Matrix4f> p = new LinkedHashMap<>();
        p.put("head", about(0, Bodies.FACELING_NECK, 0, rz(Math.toRadians(tilt))));
        p.put("head.mouth", about(0, Bodies.FACELING_MOUTH + 0.004, 0.124, new Matrix4f().scale((float) (1 + 0.2 * mouth), (float) (1 + 52 * mouth), 1f)));
        p.put("torso", about(0, Bodies.FACELING_HIP, 0, new Matrix4f().scale(1f, (float) (1 + 0.012 * breath), (float) (1 + 0.03 * breath))));
        p.put("arm_l.hand", about(-0.3, Bodies.FACELING_WRIST, 0.03, rx(0.5 * curl)));
        p.put("arm_r.hand", about(0.3, Bodies.FACELING_WRIST, 0.03, rx(0.5 * curl)));
        return p;
    }

    // ------------------------------------------------------------------ hound

    /**
     * @param phase gait phase (radians)
     * @param amp   gait amplitude (0 = standing)
     * @param rear  0..1 rising onto its hind legs before a lunge
     * @param jaw   0..1 mouth open
     * @param look  head yaw, radians
     * @param tilt  head roll, radians
     */
    static Map<String, Matrix4f> hound(double phase, double amp, double rear, double jaw, double look, double tilt) {
        Map<String, Matrix4f> p = new LinkedHashMap<>();
        double hy = Bodies.HOUND_HIP_Y, hz = Bodies.HOUND_HIP_Z;
        double bob = Math.abs(Math.sin(phase)) * 0.04 * amp;
        p.put("body", new Matrix4f().translate(0, (float) (bob + rear * 0.12), 0).mul(about(0, hy, hz, rx(-0.75 * rear))));
        p.put("body.head", about(0, Bodies.HOUND_NECK_Y, Bodies.HOUND_NECK_Z,
                new Matrix4f().rotateY((float) look).rotateZ((float) tilt).rotateX((float) (0.55 * rear))));
        p.put("body.head.jaw", about(0, Bodies.HOUND_JAW_Y, Bodies.HOUND_JAW_Z - 0.06, rx(0.75 * jaw)));
        String[] legs = {"body.leg_fl", "body.leg_fr", "body.leg_bl", "body.leg_br"};
        for (int i = 0; i < 4; i++) {
            boolean front = i < 2;
            int s = i % 2 == 0 ? -1 : 1;
            double ph = phase + (i == 0 || i == 3 ? 0 : Math.PI);
            double swing = Math.sin(ph) * 0.55 * amp;
            double lift = Math.max(0, Math.cos(ph)) * 0.7 * amp;
            if (front) {
                double sy = Bodies.HOUND_SHOULDER_Y, sz = Bodies.HOUND_SHOULDER_Z;
                p.put(legs[i], about(s * 0.17, sy, sz, new Matrix4f().rotateX((float) (swing + 0.55 * rear)).rotateZ((float) (s * 0.2 * rear))));
                p.put(legs[i] + ".lo", about(s * 0.38, Bodies.HOUND_ELBOW_Y, sz + 0.02, rx(-lift - 0.2 * rear)));
            } else {
                p.put(legs[i], about(s * 0.15, hy, hz, rx(-swing + 0.75 * rear)));
                p.put(legs[i] + ".lo", about(s * 0.24, Bodies.HOUND_KNEE_Y, hz + 0.26, rx(lift * 0.8)));
            }
        }
        return p;
    }

    // ------------------------------------------------------------------ drifter

    /** Bone name of drifter segment {@code i} ("s0", "s0.s1", ...). */
    static String drifterBone(int i) {
        StringBuilder b = new StringBuilder("s0");
        for (int k = 1; k <= i; k++) b.append(".s").append(k);
        return b.toString();
    }

    /**
     * @param t     swim time (radians)
     * @param amp   undulation amplitude (0.15 drifting .. 0.4 lunging)
     * @param reach arms reaching 0..1
     * @param jaw   0..1
     */
    static Map<String, Matrix4f> drifter(double t, double amp, double reach, double jaw) {
        Map<String, Matrix4f> p = new LinkedHashMap<>();
        double y = Bodies.DRIFTER_Y;
        p.put("s0", about(0, y, 0.16, new Matrix4f().rotateY((float) (Math.sin(t) * amp * 0.25)).rotateX((float) (Math.sin(t * 0.5) * 0.06))));
        for (int i = 1; i < Bodies.DRIFTER_SEGMENTS; i++) {
            double z = 0.16 - i * Bodies.DRIFTER_SEG;
            p.put(drifterBone(i), about(0, y, z, ry(Math.sin(t - i * 0.85) * amp)));
        }
        p.put("s0.jaw", about(0, y - 0.12, 0.3, rx(0.5 * jaw)));
        p.put("s0.hair", about(0, y + 0.18, 0.36, ry(Math.sin(t - 1.2) * 0.25 * (amp + 0.1))));
        for (int s = -1; s <= 1; s += 2) {
            double wave = Math.sin(t * 1.3 + s) * 0.12;
            p.put(s < 0 ? "s0.arm_l" : "s0.arm_r", about(s * 0.16, y - 0.12, 0.02,
                    new Matrix4f().rotateY((float) (s * (0.25 - 0.3 * reach))).rotateX((float) (wave - 0.25 * reach))));
        }
        return p;
    }

    // ------------------------------------------------------------------ watcher

    /** Edges that will not hold still: a different small offset each call (from {@code seed}). */
    static Map<String, Matrix4f> watcherHaze(long seed, boolean still) {
        Map<String, Matrix4f> p = new LinkedHashMap<>();
        if (still) {
            p.put("haze", new Matrix4f());
            return p;
        }
        java.util.Random r = new java.util.Random(seed);
        float dx = (float) (r.nextGaussian() * 0.035), dy = (float) (r.nextGaussian() * 0.02), dz = (float) (r.nextGaussian() * 0.035);
        float s = (float) (1 + r.nextGaussian() * 0.05);
        p.put("haze", about(0, 1.1, 0, new Matrix4f().translate(dx, dy, dz).scale(s, 1f, s)));
        return p;
    }

    // ------------------------------------------------------------------ skinwalker

    /**
     * @param phase  gait phase (radians)
     * @param moving legs stepping
     * @param jaw    0..1 lower jaw open
     * @param split  0..1 mandibles opening sideways
     * @param strike 0..1 mantis arms lashing forward
     * @param twitch head roll, radians
     * @param rear   0..1 front lifted (screech / pounce)
     */
    static Map<String, Matrix4f> skinwalker(double phase, boolean moving, double age, double jaw, double split, double strike, double twitch, double rear) {
        Map<String, Matrix4f> p = new LinkedHashMap<>();
        double bob = moving ? Math.abs(Math.sin(phase * 2)) * 0.05 : Math.sin(age * 0.08) * 0.025;
        Matrix4f lift = new Matrix4f().translate(0, (float) bob, 0).mul(about(0, 1.2, -0.85, rx(-0.35 * rear)));
        p.put("body", new Matrix4f(lift));
        p.put("head", new Matrix4f(lift).mul(about(0, Bodies.SKIN_HEAD_Y, Bodies.SKIN_HEAD_Z, new Matrix4f().rotateZ((float) twitch).rotateX((float) (-0.25 * rear)))));
        Vector3f hinge = Bodies.skinHead(0, 1.02, 0.62), ml = Bodies.skinHead(-0.2, 1.04, 0.72), mr = Bodies.skinHead(0.2, 1.04, 0.72);
        p.put("head.jaw", about(hinge.x, hinge.y, hinge.z, rx(0.7 * jaw)));
        p.put("head.mand_l", about(ml.x, ml.y, ml.z, ry(-0.6 * split)));
        p.put("head.mand_r", about(mr.x, mr.y, mr.z, ry(0.6 * split)));
        p.put("head.eyes", new Matrix4f());
        for (int s = -1; s <= 1; s += 2) {
            String arm = s < 0 ? "arm_l" : "arm_r";
            double idle = Math.sin(age * 0.11 + s) * 0.06;
            p.put(arm, new Matrix4f(lift).mul(about(s * 0.3, 1.0, 0.45, rx(-1.1 * strike + idle))));
            p.put(arm + ".lo", about(s * 0.42, 0.55, 0.62, rx(0.9 * strike)));
        }
        for (int i = 0; i < 8; i++) {
            int side = i < 4 ? -1 : 1;
            Vector3f[] j = Bodies.skinwalkerLegJoints(i);
            double off = (i % 2 == 0 ? 0 : Math.PI) + (i < 4 ? 0 : Math.PI / 2);
            double swing = moving ? Math.sin(phase + off) * 0.38 : Math.sin(age * 0.05 + i) * 0.04;
            double up = moving ? Math.max(0, Math.cos(phase + off)) * 0.28 : 0;
            Matrix4f hip = new Matrix4f().translate(0, (float) bob, 0).mul(about(j[0].x, j[0].y, j[0].z, new Matrix4f().rotateY((float) swing).rotateZ((float) (side * up))));
            if (i % 4 == 0 && rear > 0) hip.mul(about(j[0].x, j[0].y, j[0].z, rx(-0.6 * rear)));
            p.put("leg" + i, hip);
            p.put("leg" + i + ".lo", about(j[1].x, j[1].y, j[1].z, rz(-side * up * 0.9)));
        }
        return p;
    }
}
