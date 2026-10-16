package com.powderpass.backrooms.creature;

import com.powderpass.backrooms.display.Model;
import com.powderpass.backrooms.display.Part;
import org.bukkit.Material;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.bukkit.Material.*;

/**
 * Display-component bodies of the creatures. Origin = feet, front = +Z. Part names form a skeleton (see
 * {@link Body#pose}): {@code head.jaw} follows {@code head}, so animation can pose bones independently. Rest-pose
 * pivots that the animation code needs are the constants below.
 */
final class Bodies {
    private Bodies() {
    }

    // ------------------------------------------------------------------ rig pivots
    static final double FACELING_HIP = 1.35, FACELING_SHOULDER = 2.12, FACELING_NECK = 2.24, FACELING_MOUTH = 2.4, FACELING_WRIST = 0.68;
    static final double WATCHER_HIP = 1.0, WATCHER_SHOULDER = 1.68, WATCHER_NECK = 1.8;
    static final double SMILER_MOUTH = 1.5, SMILER_EYES = 1.9, SMILER_SHOULDER_Y = 1.62, SMILER_SHOULDER_X = 0.46;
    /** Hound: shoulder (y,z), hip (y,z), the elbow and knee pivots, neck and jaw hinge. */
    static final double HOUND_SHOULDER_Y = 0.74, HOUND_SHOULDER_Z = 0.36, HOUND_HIP_Y = 0.66, HOUND_HIP_Z = -0.48;
    static final double HOUND_ELBOW_Y = 0.86, HOUND_KNEE_Y = 0.34;
    static final double HOUND_NECK_Y = 0.74, HOUND_NECK_Z = 0.52, HOUND_JAW_Y = 0.46, HOUND_JAW_Z = 0.7;
    static final double DRIFTER_SEG = 0.46, DRIFTER_Y = 0.42;
    static final int DRIFTER_SEGMENTS = 6;
    static final double SKIN_HIP_Y = 1.2, SKIN_HIP_X = 0.42;
    /** The skull is modelled at rest size and scaled up around the neck. */
    static final double SKIN_HEAD_Y = 1.2, SKIN_HEAD_Z = 0.5, SKIN_HEAD_SCALE = 1.35;

    /** A point of the skull as modelled, moved where the scaled skull puts it. */
    static Vector3f skinHead(double x, double y, double z) {
        double k = SKIN_HEAD_SCALE;
        return v(x * k, SKIN_HEAD_Y + (y - SKIN_HEAD_Y) * k, SKIN_HEAD_Z + (z - SKIN_HEAD_Z) * k);
    }

    // ------------------------------------------------------------------ helpers

    /** A rhombus (pointed at top and bottom), centred, rolled around Z: teeth, claws, spikes. */
    private static Part fang(Model m, Material mat, double cx, double cy, double cz, double w, double h, double depth, double rollDeg) {
        Matrix4f l = new Matrix4f().translate((float) cx, (float) cy, (float) cz)
                .rotateZ((float) Math.toRadians(rollDeg))
                .scale((float) (w / Math.sqrt(2)), (float) (h / Math.sqrt(2)), (float) depth)
                .rotateZ((float) Math.toRadians(45))
                .translate(-0.5f, -0.5f, -0.5f);
        return m.boxM(mat, l);
    }

    /** Rods through consecutive points; every segment gets the same name. */
    private static void chain(Model m, Material mat, double thick, String name, Vector3f... pts) {
        for (int i = 0; i + 1 < pts.length; i++) m.rod(mat, pts[i], pts[i + 1], thick).named(name);
    }

    /** Linear interpolation between two points. */
    private static Vector3f lerp(Vector3f a, Vector3f b, double t) {
        return new Vector3f(a).lerp(b, (float) t);
    }

    private static Vector3f v(double x, double y, double z) {
        return Model.v(x, y, z);
    }

    /** Long jointed fingers fanned from a wrist, each in two segments that curl toward {@code curl}. */
    private static void fingers(Model m, Material skin, Material nail, String name, Vector3f wrist, Vector3f dir, Vector3f side,
                                int n, double len, double spread, double curl, double thick) {
        Vector3f d = new Vector3f(dir).normalize();
        Vector3f s = new Vector3f(side).normalize();
        Vector3f bend = new Vector3f(d).cross(s).normalize();
        for (int f = 0; f < n; f++) {
            double t = n == 1 ? 0 : (f / (double) (n - 1) - 0.5);
            double fl = len * (1 - Math.abs(t) * 0.45);
            Vector3f base = new Vector3f(wrist).add(new Vector3f(s).mul((float) (t * spread)));
            Vector3f fd = new Vector3f(d).add(new Vector3f(s).mul((float) (t * 0.6))).normalize();
            Vector3f knuckle = new Vector3f(base).add(new Vector3f(fd).mul((float) (fl * 0.55)));
            Vector3f fd2 = new Vector3f(fd).add(new Vector3f(bend).mul((float) curl)).normalize();
            Vector3f tip = new Vector3f(knuckle).add(new Vector3f(fd2).mul((float) (fl * 0.45)));
            m.rod(skin, base, knuckle, thick).named(name);
            m.rod(nail, knuckle, tip, thick * 0.8).named(name);
        }
    }

    // ===================================================================== Smiler

    /**
     * Entity 3: a shapeless dark mass you only ever see as reflective eyes and a grin of needle teeth. Up close the
     * dark has arms, long and black, that reach for you; more eyes open as it comes.
     * Bones: {@code eye} (blinks), {@code eyes2} (hidden in the mass, slide out), {@code grin} (upper teeth),
     * {@code grin.low} (lower teeth, drops to open the mouth), {@code arm_l/arm_r} with {@code .fore}, {@code mass}.
     */
    static Model smiler() {
        Model m = new Model();
        Material dark = BLACK_CONCRETE, haze = BLACK_STAINED_GLASS, tooth = WHITE_CONCRETE;
        double my = SMILER_MOUTH;
        // the mass: a lumpy dark core with smoky edges and wisps trailing to the floor
        m.boxC(dark, 0, 1.62, -0.34, 0.8, 0.82, 0.56, 'z', 8).named("mass");
        m.boxC(dark, -0.06, 1.66, -0.36, 0.7, 0.9, 0.5, 'z', -27).named("mass");
        m.boxC(dark, 0.08, 2.0, -0.4, 0.5, 0.3, 0.42, 'z', 19).named("mass");
        m.boxC(dark, 0.02, 1.2, -0.38, 0.55, 0.3, 0.4, 'z', -12).named("mass");
        m.boxC(haze, 0, 1.62, -0.42, 1.25, 1.15, 0.7, 'z', 14).named("mass");
        m.boxC(haze, 0.05, 1.55, -0.45, 1.05, 1.35, 0.62, 'z', -21).named("mass");
        m.boxC(haze, -0.05, 2.15, -0.5, 0.7, 0.35, 0.5, 'z', 33).named("mass");
        for (int i = 0; i < 5; i++) {
            double wx = -0.26 + i * 0.13 + ((i * 37) % 5) * 0.012;
            double len = 0.45 + ((i * 53) % 7) * 0.1;
            Material w = i % 2 == 0 ? haze : dark;
            m.rod(w, v(wx, 1.15, -0.35 - (i % 3) * 0.06), v(wx * 1.6 + 0.05, 1.15 - len, -0.5 - (i % 2) * 0.25), i % 2 == 0 ? 0.09 : 0.035).named("mass");
        }
        // eyes: slanted almonds that catch the light, with a slit of a pupil, not quite level
        double[][] eyes = {{-0.22, SMILER_EYES, -14}, {0.22, SMILER_EYES + 0.03, 11}};
        for (double[] e : eyes) {
            m.boxC(WHITE_CONCRETE, e[0], e[1], 0.0, 0.24, 0.075, 0.02, 'z', e[2]).glow(15).named("eye");
            m.boxC(WHITE_CONCRETE, e[0] + Math.signum(e[0]) * 0.05, e[1] + 0.018, 0.0, 0.14, 0.05, 0.022, 'z', e[2] * 1.8).glow(15).named("eye");
            m.boxC(dark, e[0] - Math.signum(e[0]) * 0.02, e[1], 0.016, 0.024, 0.085, 0.01, 'z', e[2] * 0.3).named("eye");
        }
        // more eyes, hidden inside the mass until it is close
        double[][] more = {{-0.08, 2.08, 0.07}, {0.34, 1.72, 0.05}, {-0.36, 1.66, 0.06}, {0.12, 2.0, 0.045}};
        for (double[] e : more) {
            m.boxC(WHITE_CONCRETE, e[0], e[1], -0.38, e[2] * 1.6, e[2] * 0.6, 0.02, 'z', (e[0] * 40)).glow(13).named("eyes2");
            m.boxC(dark, e[0], e[1], -0.365, 0.016, e[2] * 0.7, 0.01, 'z', 0).named("eyes2");
        }
        // a grin far too wide: interlocking rows of needle teeth along one arc
        int n = 9;
        for (int i = -n; i <= n; i++) {
            double a = Math.toRadians(i * 7.2);
            double x = Math.sin(a) * 0.56, y = my + (1 - Math.cos(a)) * 0.5;
            double h = 0.13 - Math.abs(i) * 0.006;
            fang(m, tooth, x, y + 0.035, 0.0, 0.05, h, 0.02, i * 7.2).glow(15).named("grin");
            if (i < n) {
                double a2 = Math.toRadians((i + 0.5) * 7.2);
                double x2 = Math.sin(a2) * 0.55, y2 = my + (1 - Math.cos(a2)) * 0.5;
                fang(m, tooth, x2, y2 - 0.035, 0.0, 0.046, h * 0.9, 0.02, (i + 0.5) * 7.2).glow(12).named("grin.low");
            }
        }
        // two long black arms with too many joints, reaching down out of the dark
        for (int s = -1; s <= 1; s += 2) {
            String arm = s < 0 ? "arm_l" : "arm_r";
            Vector3f sh = v(s * SMILER_SHOULDER_X, SMILER_SHOULDER_Y, -0.3);
            Vector3f el = v(s * 0.92, 1.16, -0.05);
            Vector3f wr = v(s * 0.78, 0.5, 0.22);
            m.rod(dark, sh, el, 0.075).named(arm);
            m.rod(dark, el, wr, 0.06).named(arm + ".fore");
            m.boxC(dark, wr.x, wr.y - 0.02, wr.z, 0.1, 0.06, 0.12, 'z', s * 15).named(arm + ".fore");
            fingers(m, dark, dark, arm + ".fore", v(wr.x, wr.y - 0.04, wr.z), v(s * 0.15, -1, 0.35), v(0, 0, 1), 4, 0.42, 0.13, 0.55, 0.026);
        }
        return m;
    }

    // ===================================================================== Faceling

    /**
     * Entity 9: tall and thin in a dated suit that fits wrong, arms long enough to brush the knees, and a smooth head
     * where a face should be, smudged like a bad photo edit. A seam where a mouth would be opens far too wide.
     * Bones: {@code leg_l/leg_r}, {@code torso}, {@code arm_l/arm_r} with {@code .hand}, {@code head}, {@code head.mouth}.
     */
    static Model faceling() {
        Model m = new Model();
        Material suit = GRAY_CONCRETE, suitDark = GRAY_TERRACOTTA, skin = WHITE_TERRACOTTA, dark = BLACK_CONCRETE,
                shirt = WHITE_CONCRETE, tie = RED_TERRACOTTA, pale = MUSHROOM_STEM;
        double hip = FACELING_HIP;
        // legs: trousers with a crease, a strip of bare ankle, long pointed shoes
        for (int s = -1; s <= 1; s += 2) {
            String leg = s < 0 ? "leg_l" : "leg_r";
            double lx = s < 0 ? -0.165 : 0.045;
            m.box(suit, lx, 0.12, -0.06, 0.12, hip - 0.12, 0.12).named(leg);
            m.box(suitDark, lx + 0.05, 0.14, 0.061, 0.02, hip - 0.2, 0.002).named(leg);
            m.box(pale, lx + 0.01, 0.07, -0.05, 0.1, 0.05, 0.1).named(leg);
            m.box(dark, lx - 0.005, 0, -0.08, 0.13, 0.07, 0.24).named(leg);
            m.box(dark, lx + 0.02, 0, 0.16, 0.08, 0.045, 0.06).named(leg);
        }
        // torso: narrow waist, high shoulders, lapels, a white shirt, a dark red tie, two buttons
        m.box(suit, -0.19, hip, -0.1, 0.38, 0.38, 0.2).named("torso");
        m.box(suit, -0.23, hip + 0.38, -0.11, 0.46, 0.42, 0.22).named("torso");
        m.box(suit, -0.25, hip + 0.72, -0.1, 0.5, 0.08, 0.2).named("torso");
        m.box(shirt, -0.07, hip + 0.42, 0.105, 0.14, 0.38, 0.01).named("torso");
        m.box(tie, -0.025, hip + 0.36, 0.113, 0.05, 0.42, 0.01).named("torso");
        m.boxC(tie, 0, hip + 0.36, 0.115, 0.045, 0.045, 0.01, 'z', 45).named("torso");
        m.boxC(suitDark, -0.085, hip + 0.6, 0.118, 0.06, 0.34, 0.012, 'z', -14).named("torso");
        m.boxC(suitDark, 0.085, hip + 0.6, 0.118, 0.06, 0.34, 0.012, 'z', 14).named("torso");
        m.boxC(shirt, -0.06, hip + 0.79, 0.11, 0.07, 0.04, 0.02, 'z', 20).named("torso");
        m.boxC(shirt, 0.06, hip + 0.79, 0.11, 0.07, 0.04, 0.02, 'z', -20).named("torso");
        m.box(dark, -0.015, hip + 0.22, 0.101, 0.03, 0.03, 0.01).named("torso");
        m.box(dark, -0.015, hip + 0.1, 0.101, 0.03, 0.03, 0.01).named("torso");
        // arms: far too long, a slight wrong bend at the elbow, white cuffs, long spidery fingers
        for (int s = -1; s <= 1; s += 2) {
            String arm = s < 0 ? "arm_l" : "arm_r";
            Vector3f sh = v(s * 0.29, FACELING_SHOULDER - 0.05, 0);
            Vector3f el = v(s * 0.33, 1.42, -0.06);
            Vector3f wr = v(s * 0.3, FACELING_WRIST + 0.08, 0.03);
            m.rod(suit, sh, el, 0.1).named(arm);
            m.rod(suit, el, wr, 0.09).named(arm);
            m.rod(shirt, lerp(el, wr, 0.92), lerp(el, wr, 1.03), 0.092).named(arm);
            m.box(skin, wr.x - 0.035, FACELING_WRIST - 0.08, wr.z - 0.04, 0.07, 0.14, 0.08).named(arm + ".hand");
            fingers(m, skin, pale, arm + ".hand", v(wr.x, FACELING_WRIST - 0.08, wr.z), v(0, -1, 0.05), v(0, 0, 1), 5, 0.3, 0.07, 0.35, 0.018);
        }
        // a neck a little too long, with one tendon showing
        m.box(skin, -0.04, FACELING_SHOULDER, -0.04, 0.08, 0.2, 0.08).named("head");
        m.rod(LIGHT_GRAY_TERRACOTTA, v(0.02, FACELING_SHOULDER + 0.02, 0.041), v(0.005, FACELING_SHOULDER + 0.18, 0.042), 0.012).named("head");
        // the head: smooth as an egg, no ears, no hair
        double hy = 2.28;
        m.box(skin, -0.12, hy + 0.03, -0.12, 0.24, 0.3, 0.24).named("head");
        m.box(skin, -0.105, hy + 0.33, -0.105, 0.21, 0.05, 0.21).named("head");
        m.box(skin, -0.085, hy, -0.095, 0.17, 0.04, 0.19).named("head");
        m.box(skin, -0.128, hy + 0.08, -0.09, 0.256, 0.2, 0.18).named("head");
        // where the face should be: smears of slightly wrong skin, a ghost of a nose, nothing else
        m.boxC(pale, -0.01, hy + 0.21, 0.122, 0.2, 0.05, 0.004, 'z', -6).named("head");
        m.boxC(LIGHT_GRAY_TERRACOTTA, -0.055, hy + 0.2, 0.123, 0.06, 0.022, 0.004, 'z', -9).named("head");
        m.boxC(LIGHT_GRAY_TERRACOTTA, 0.055, hy + 0.205, 0.123, 0.06, 0.022, 0.004, 'z', -4).named("head");
        m.boxC(pale, 0.01, hy + 0.12, 0.122, 0.16, 0.06, 0.004, 'z', 5).named("head");
        m.boxC(skin, 0, hy + 0.15, 0.125, 0.035, 0.07, 0.02, 'z', 0).named("head");
        // the seam that opens (scaled vertically by the animation)
        m.box(dark, -0.06, FACELING_MOUTH - 0.004, 0.121, 0.12, 0.008, 0.006).named("head.mouth");
        return m;
    }

    // ===================================================================== Hound

    /**
     * Entity 2: a starved, naked human shape on all fours. Back arched, every rib and vertebra showing, elbows bent
     * the wrong way above its shoulders, long clawed fingers, and a curtain of black hair over a mouth too wide for
     * its face. White eyes show between the strands. It rears onto its hind legs before it lunges.
     * Bones: {@code body} (root), {@code body.head}, {@code body.head.jaw}, {@code body.head.eyes},
     * {@code body.leg_fl/fr/bl/br} with {@code .lo}.
     */
    static Model hound() {
        Model m = new Model();
        Material skin = WHITE_TERRACOTTA, bone = LIGHT_GRAY_TERRACOTTA, hair = BLACK_WOOL, dark = BLACK_CONCRETE,
                claw = BLACK_TERRACOTTA, teeth = WHITE_CONCRETE, gum = RED_TERRACOTTA, pale = MUSHROOM_STEM;
        // torso: a deep narrow chest, a sunken belly, a bony pelvis, arched along the spine
        m.box(skin, -0.18, 0.56, 0.02, 0.36, 0.3, 0.48).named("body");
        m.box(skin, -0.13, 0.62, -0.28, 0.26, 0.2, 0.32).named("body");
        m.box(skin, -0.16, 0.56, -0.6, 0.32, 0.2, 0.32).named("body");
        m.box(bone, -0.185, 0.6, -0.56, 0.06, 0.08, 0.1).named("body");
        m.box(bone, 0.125, 0.6, -0.56, 0.06, 0.08, 0.1).named("body");
        for (int i = 0; i < 10; i++) {
            double z = -0.6 + i * 0.12;
            double arch = 0.86 + Math.sin(Math.PI * (i + 0.5) / 10) * 0.06;
            m.boxC(bone, 0, arch, z, 0.06, 0.05, 0.07, 'x', 10).named("body");
        }
        for (int s = -1; s <= 1; s += 2) {
            for (int r = 0; r < 5; r++) {
                double z = 0.44 - r * 0.085;
                m.rod(bone, v(s * 0.15, 0.84, z), v(s * 0.192, 0.64, z - 0.05), 0.022).named("body");
            }
            m.boxC(bone, s * 0.12, 0.88, 0.36, 0.1, 0.035, 0.18, 'z', s * -25).named("body");
        }
        // neck, hanging low
        m.rod(skin, v(0, HOUND_NECK_Y, HOUND_NECK_Z), v(0, 0.6, 0.68), 0.09).named("body.head");
        // head: low skull under a curtain of hair
        m.box(skin, -0.13, 0.46, 0.62, 0.26, 0.26, 0.26).named("body.head");
        m.box(hair, -0.145, 0.68, 0.6, 0.29, 0.07, 0.3).named("body.head");
        m.box(hair, -0.145, 0.42, 0.6, 0.04, 0.28, 0.28).named("body.head");
        m.box(hair, 0.105, 0.42, 0.6, 0.04, 0.28, 0.28).named("body.head");
        // white, pupil-less eyes between strands
        m.box(teeth, -0.085, 0.6, 0.881, 0.045, 0.03, 0.006).glow(10).named("body.head.eyes");
        m.box(teeth, 0.04, 0.605, 0.881, 0.045, 0.03, 0.006).glow(10).named("body.head.eyes");
        // the hair: long, stringy, uneven, almost to the floor, with a gap at the eyes
        double[] hx = {-0.14, -0.11, -0.075, 0.0, 0.03, 0.095, 0.125, -0.03};
        double[] hl = {0.52, 0.6, 0.4, 0.66, 0.45, 0.58, 0.5, 0.3};
        for (int i = 0; i < hx.length; i++) {
            double top = 0.74, len = hl[i];
            double z = 0.885 + (i % 3) * 0.008;
            double sway = ((i * 29) % 7 - 3) * 0.01;
            m.rod(hair, v(hx[i], top, z), v(hx[i] + sway, top - len, z + 0.04 + (i % 2) * 0.03), 0.03 + (i % 3) * 0.008).named("body.head");
        }
        // the mouth: wider than the face, upper teeth fixed, the jaw drops
        m.box(dark, -0.15, 0.43, 0.7, 0.3, 0.05, 0.2).named("body.head");
        for (int i = 0; i < 7; i++) fang(m, teeth, -0.135 + i * 0.045, 0.43, 0.895, 0.032, 0.07, 0.015, 0).named("body.head");
        m.box(gum, -0.15, 0.38, 0.66, 0.3, 0.04, 0.24).named("body.head.jaw");
        m.box(skin, -0.15, 0.35, 0.64, 0.3, 0.035, 0.26).named("body.head.jaw");
        for (int i = 0; i < 6; i++) fang(m, teeth, -0.11 + i * 0.045, 0.43, 0.885, 0.03, 0.06, 0.015, 0).named("body.head.jaw");
        // limbs: front arms bend outward with the elbow above the shoulder; hind legs fold like a dog's
        for (int s = -1; s <= 1; s += 2) {
            String fl = s < 0 ? "body.leg_fl" : "body.leg_fr";
            Vector3f sh = v(s * 0.17, HOUND_SHOULDER_Y, HOUND_SHOULDER_Z);
            Vector3f el = v(s * 0.38, HOUND_ELBOW_Y, HOUND_SHOULDER_Z + 0.02);
            Vector3f wr = v(s * 0.33, 0.05, HOUND_SHOULDER_Z + 0.24);
            m.rod(skin, sh, el, 0.075).named(fl);
            m.boxC(bone, el.x, el.y, el.z, 0.07, 0.07, 0.07, 'y', 45).named(fl);
            m.rod(skin, el, wr, 0.06).named(fl + ".lo");
            m.box(skin, wr.x - 0.06, 0.0, wr.z - 0.04, 0.12, 0.04, 0.12).named(fl + ".lo");
            fingers(m, pale, claw, fl + ".lo", v(wr.x, 0.025, wr.z + 0.06), v(s * 0.2, -0.05, 1), v(1, 0, 0), 4, 0.26, 0.12, -0.25, 0.022);

            String bl = s < 0 ? "body.leg_bl" : "body.leg_br";
            Vector3f hp = v(s * 0.15, HOUND_HIP_Y, HOUND_HIP_Z);
            Vector3f kn = v(s * 0.24, HOUND_KNEE_Y, HOUND_HIP_Z + 0.26);
            Vector3f an = v(s * 0.22, 0.12, HOUND_HIP_Z - 0.18);
            m.rod(skin, hp, kn, 0.085).named(bl);
            m.boxC(bone, kn.x, kn.y, kn.z, 0.07, 0.07, 0.07, 'y', 45).named(bl);
            m.rod(skin, kn, an, 0.065).named(bl + ".lo");
            m.rod(skin, an, v(an.x, 0.02, an.z + 0.08), 0.06).named(bl + ".lo");
            fingers(m, pale, claw, bl + ".lo", v(an.x, 0.02, an.z + 0.1), v(0, -0.02, 1), v(1, 0, 0), 3, 0.2, 0.08, -0.2, 0.022);
        }
        return m;
    }

    // ===================================================================== Drifter

    /**
     * A drowned thing from the Poolrooms: a pale, eel-long body behind a human face frozen mid-scream, hollow eyes,
     * hair trailing, two thin arms reaching ahead of it, a fin of clouded membrane along its back.
     * Bones form a chain so the undulation adds up: {@code s0}, {@code s0.s1}, {@code s0.s1.s2}, ...;
     * {@code s0.jaw}, {@code s0.hair}, {@code s0.arm_l/arm_r}.
     */
    static Model drifter() {
        Model m = new Model();
        Material skin = MUSHROOM_STEM, skin2 = WHITE_TERRACOTTA, bone = LIGHT_GRAY_TERRACOTTA, dark = BLACK_CONCRETE,
                fin = WHITE_STAINED_GLASS, hair = BLACK_WOOL;
        double y = DRIFTER_Y;
        // the face: gaunt, stretched, eyes hollow, mouth pulled down into a scream
        m.box(skin, -0.15, y - 0.08, 0.14, 0.3, 0.3, 0.38).named("s0");
        m.box(skin2, -0.17, y + 0.1, 0.16, 0.34, 0.1, 0.34).named("s0");
        m.box(skin, -0.165, y - 0.04, 0.2, 0.33, 0.12, 0.25).named("s0");
        m.box(bone, -0.155, y - 0.06, 0.44, 0.06, 0.06, 0.08).named("s0");
        m.box(bone, 0.095, y - 0.06, 0.44, 0.06, 0.06, 0.08).named("s0");
        m.boxC(dark, -0.075, y + 0.08, 0.522, 0.1, 0.12, 0.01, 'z', 12).named("s0");
        m.boxC(dark, 0.075, y + 0.075, 0.522, 0.1, 0.13, 0.01, 'z', -9).named("s0");
        m.box(WHITE_CONCRETE, -0.08, y + 0.08, 0.528, 0.018, 0.018, 0.006).glow(9).named("s0");
        m.box(WHITE_CONCRETE, 0.068, y + 0.07, 0.528, 0.018, 0.018, 0.006).glow(9).named("s0");
        m.box(bone, -0.018, y - 0.0, 0.52, 0.036, 0.05, 0.02).named("s0");
        for (int i = 0; i < 4; i++) fang(m, WHITE_CONCRETE, -0.06 + i * 0.04, y - 0.075, 0.522, 0.03, 0.06, 0.012, 0).named("s0");
        m.box(dark, -0.07, y - 0.24, 0.52, 0.14, 0.17, 0.01).named("s0.jaw");
        m.box(skin, -0.11, y - 0.3, 0.2, 0.22, 0.08, 0.31).named("s0.jaw");
        m.box(skin, -0.1, y - 0.24, 0.2, 0.025, 0.08, 0.3).named("s0.jaw");
        m.box(skin, 0.075, y - 0.24, 0.2, 0.025, 0.08, 0.3).named("s0.jaw");
        for (int i = 0; i < 3; i++) fang(m, WHITE_CONCRETE, -0.04 + i * 0.04, y - 0.215, 0.522, 0.028, 0.055, 0.012, 0).named("s0.jaw");
        // hair streaming back over the shoulders
        for (int i = 0; i < 6; i++) {
            double hx = -0.14 + i * 0.056;
            double len = 0.6 + ((i * 41) % 5) * 0.12;
            m.rod(hair, v(hx, y + 0.18, 0.36), v(hx * 1.4, y + 0.1 + (i % 2) * 0.06, 0.36 - len), 0.035).named("s0.hair");
        }
        // arms reaching ahead, fingers long and spread
        for (int s = -1; s <= 1; s += 2) {
            String arm = s < 0 ? "s0.arm_l" : "s0.arm_r";
            Vector3f sh = v(s * 0.16, y - 0.12, 0.02);
            Vector3f el = v(s * 0.36, y - 0.18, 0.42);
            Vector3f wr = v(s * 0.24, y - 0.08, 0.95);
            m.rod(skin, sh, el, 0.055).named(arm);
            m.rod(skin, el, wr, 0.045).named(arm);
            fingers(m, skin, bone, arm, wr, v(s * 0.1, 0.05, 1), v(1, 0, 0), 4, 0.32, 0.12, 0.35, 0.02);
        }
        // the body: segments tapering away, a spine ridge, rib lines on the first, a fin of clouded membrane
        String bone0 = "s0";
        for (int i = 0; i < DRIFTER_SEGMENTS; i++) {
            String name = i == 0 ? "s0" : bone0 + ".s" + i;
            if (i > 0) bone0 = name;
            double w = 0.32 - i * 0.042, h = 0.26 - i * 0.03;
            double z0 = 0.16 - i * DRIFTER_SEG;
            m.box(i % 2 == 0 ? skin : skin2, -w / 2, y - h / 2 - 0.04, z0 - DRIFTER_SEG - 0.02, w, h, DRIFTER_SEG + 0.04).named(name);
            m.box(bone, -0.025, y + h / 2 - 0.05, z0 - DRIFTER_SEG + 0.08, 0.05, 0.04, DRIFTER_SEG - 0.16).named(name);
            m.box(fin, -0.005, y + h / 2 - 0.04, z0 - DRIFTER_SEG, 0.01, 0.2 - i * 0.015, DRIFTER_SEG).named(name);
            if (i < 2) {
                for (int r = 0; r < 3; r++) {
                    double rz = z0 - 0.1 - r * 0.12;
                    m.box(bone, -w / 2 - 0.006, y - h / 2 + 0.02, rz, w + 0.012, 0.02, 0.025).named(name);
                }
            }
            if (i == DRIFTER_SEGMENTS - 1) {
                double tz = z0 - DRIFTER_SEG;
                m.box(fin, -0.36, y - 0.05, tz - 0.45, 0.72, 0.012, 0.46).named(name);
                m.rod(bone, v(0, y - 0.04, tz), v(-0.34, y - 0.04, tz - 0.44), 0.02).named(name);
                m.rod(bone, v(0, y - 0.04, tz), v(0.34, y - 0.04, tz - 0.44), 0.02).named(name);
            }
        }
        return m;
    }

    // ===================================================================== Watcher

    /**
     * A person-shaped absence: a flat black silhouette, too tall and too thin, arms hanging past the knees. Its edges
     * do not hold still while it moves, like a smudge on the film. Two pinpricks of eyes show only up close.
     * Bones: {@code leg_l/leg_r}, {@code torso}, {@code arm_l/arm_r}, {@code head}, {@code head.eyes}, {@code haze}.
     */
    static Model watcher() {
        Model m = new Model();
        Material black = BLACK_CONCRETE, haze = BLACK_STAINED_GLASS;
        double hip = WATCHER_HIP;
        for (int s = -1; s <= 1; s += 2) {
            String leg = s < 0 ? "leg_l" : "leg_r";
            double lx = s * 0.1;
            m.rod(black, v(lx, hip, 0), v(lx * 1.1, 0.5, 0.02), 0.13).named(leg);
            m.rod(black, v(lx * 1.1, 0.5, 0.02), v(lx * 1.15, 0.04, -0.01), 0.1).named(leg);
            m.box(black, lx * 1.15 - 0.055, 0, -0.06, 0.11, 0.05, 0.2).named(leg);
        }
        // torso narrowing to the waist, shoulders sloping forward
        m.box(black, -0.16, hip, -0.08, 0.32, 0.28, 0.16).named("torso");
        m.box(black, -0.22, hip + 0.28, -0.09, 0.44, 0.38, 0.18).named("torso");
        m.boxC(black, -0.2, WATCHER_SHOULDER - 0.01, 0, 0.2, 0.1, 0.17, 'z', 12).named("torso");
        m.boxC(black, 0.2, WATCHER_SHOULDER - 0.07, 0, 0.2, 0.1, 0.17, 'z', -26).named("torso");
        for (int s = -1; s <= 1; s += 2) {
            String arm = s < 0 ? "arm_l" : "arm_r";
            Vector3f sh = v(s * 0.3, WATCHER_SHOULDER - 0.05, 0);
            Vector3f el = v(s * 0.34, 1.12, -0.02);
            Vector3f wr = v(s * 0.33, 0.52, 0.02);
            m.rod(black, sh, el, 0.085).named(arm);
            m.rod(black, el, wr, 0.075).named(arm);
            fingers(m, black, black, arm, wr, v(0, -1, 0.04), v(0, 0, 1), 4, 0.38, 0.07, 0.3, 0.02);
        }
        // a neck too thin, a small head, tilted
        m.box(black, -0.04, WATCHER_SHOULDER, -0.04, 0.08, 0.16, 0.08).named("head");
        m.push().translate(0, WATCHER_NECK, 0).rotate('z', 14).rotate('x', 10).translate(0, -WATCHER_NECK, 0);
        m.box(black, -0.11, WATCHER_NECK, -0.11, 0.22, 0.27, 0.22).named("head");
        m.box(black, -0.09, WATCHER_NECK + 0.27, -0.09, 0.18, 0.04, 0.18).named("head");
        m.box(black, -0.09, WATCHER_NECK - 0.03, -0.08, 0.18, 0.03, 0.17).named("head");
        m.box(WHITE_CONCRETE, -0.06, WATCHER_NECK + 0.15, 0.111, 0.022, 0.022, 0.004).glow(5).named("head.eyes");
        m.box(WHITE_CONCRETE, 0.04, WATCHER_NECK + 0.15, 0.111, 0.022, 0.022, 0.004).glow(5).named("head.eyes");
        m.pop();
        // smudged edges
        m.box(haze, -0.27, hip + 0.02, -0.13, 0.54, 0.72, 0.26).named("haze");
        m.box(haze, -0.15, WATCHER_NECK - 0.04, -0.15, 0.3, 0.38, 0.3).named("haze");
        m.box(haze, -0.2, 0.02, -0.11, 0.4, hip - 0.02, 0.22).named("haze");
        m.box(haze, -0.42, 0.5, -0.08, 0.17, 1.15, 0.16).named("haze");
        m.box(haze, 0.25, 0.5, -0.08, 0.17, 1.15, 0.16).named("haze");
        return m;
    }

    // ===================================================================== Bacteria

    static final double BAC_HIP = 1.62, BAC_SHOULDER = 2.55, BAC_NECK = 2.66;

    /** Knee and elbow of the Bacteria at rest (for the gait pivots). */
    static final double BAC_KNEE_Y = 0.86, BAC_ELBOW_Y = 1.72;

    /**
     * The Bacteria ("the Lifeform"): very tall, black and thin, limbs too long and too many joints' worth of bend,
     * long fingers, a small blank head, and cable-like strands wound around its legs, body and arms.
     * Bones: {@code leg_l/leg_r} with {@code .lo}, {@code torso}, {@code arm_l/arm_r} with {@code .lo}, {@code head}.
     */
    static Model bacteria() {
        Model m = new Model();
        Material skin = BLACK_CONCRETE, cable = GRAY_TERRACOTTA, dull = BLACK_TERRACOTTA;
        for (int s = -1; s <= 1; s += 2) {
            String leg = s < 0 ? "leg_l" : "leg_r";
            Vector3f hip = v(s * 0.09, BAC_HIP, 0), knee = v(s * 0.12, BAC_KNEE_Y, 0.07), ankle = v(s * 0.11, 0.06, -0.03);
            m.rod(skin, hip, knee, 0.075).named(leg);
            m.rod(skin, knee, ankle, 0.06).named(leg + ".lo");
            m.box(skin, ankle.x - 0.04, 0, ankle.z - 0.02, 0.08, 0.04, 0.2).named(leg + ".lo");
            helix(m, cable, leg, hip, knee, 0.06, 2, s * 0.7);
            helix(m, cable, leg + ".lo", knee, ankle, 0.05, 2, s * 1.3);
        }
        // a narrow pelvis, a long thin trunk bent forward, ribs pressing through
        m.box(skin, -0.13, BAC_HIP - 0.06, -0.06, 0.26, 0.16, 0.12).named("torso");
        m.rod(skin, v(0, BAC_HIP + 0.08, 0), v(0, 2.12, 0.05), 0.11).named("torso");
        m.rod(skin, v(0, 2.12, 0.05), v(0, BAC_SHOULDER, 0.12), 0.15).named("torso");
        m.boxC(skin, 0, BAC_SHOULDER - 0.02, 0.12, 0.46, 0.08, 0.1, 'z', 6).named("torso");
        for (int i = 0; i < 4; i++) m.boxC(dull, 0, 2.18 + i * 0.08, 0.15, 0.2 - i * 0.02, 0.025, 0.06, 'x', 10).named("torso");
        helix(m, cable, "torso", v(0, BAC_HIP, 0), v(0, BAC_SHOULDER, 0.12), 0.11, 4, 0.4);
        for (int s = -1; s <= 1; s += 2) {
            String arm = s < 0 ? "arm_l" : "arm_r";
            Vector3f sh = v(s * 0.24, BAC_SHOULDER - 0.03, 0.12), el = v(s * 0.3, BAC_ELBOW_Y, 0.08), wr = v(s * 0.3, 0.92, 0.14);
            m.rod(skin, sh, el, 0.06).named(arm);
            m.rod(skin, el, wr, 0.05).named(arm + ".lo");
            fingers(m, skin, skin, arm + ".lo", wr, v(0, -1, 0.06), v(0, 0, 1), 4, 0.42, 0.08, 0.3, 0.018);
            helix(m, cable, arm, sh, el, 0.05, 1, s * 0.9);
            helix(m, cable, arm + ".lo", el, wr, 0.045, 1, s * 1.7);
        }
        // a long neck and a small blank head, pushed forward
        m.rod(skin, v(0, BAC_SHOULDER, 0.12), v(0, BAC_NECK + 0.2, 0.2), 0.06).named("head");
        m.boxC(skin, 0, BAC_NECK + 0.38, 0.24, 0.17, 0.32, 0.2, 'x', 18).named("head");
        m.boxC(dull, 0, BAC_NECK + 0.52, 0.22, 0.13, 0.08, 0.16, 'x', 18).named("head");
        return m;
    }

    /** Thin strands wound around a limb segment {@code a}-{@code b}: short rods around it in a helix. */
    private static void helix(Model m, Material mat, String name, Vector3f a, Vector3f b, double radius, int turns, double phase) {
        Vector3f axis = new Vector3f(b).sub(a);
        Vector3f side = new Vector3f(axis).cross(new Vector3f(0.3f, 0, 1)).normalize();
        Vector3f side2 = new Vector3f(axis).cross(side).normalize();
        int steps = turns * 3;
        Vector3f prev = null;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps, ang = phase + t * turns * Math.PI * 2;
            Vector3f p = lerp(a, b, t).add(new Vector3f(side).mul((float) (Math.cos(ang) * radius))).add(new Vector3f(side2).mul((float) (Math.sin(ang) * radius)));
            if (prev != null) m.rod(mat, prev, p, 0.018).named(name);
            prev = p;
        }
    }

    // ===================================================================== Skinwalker


    /** Hip and knee of a skinwalker leg in rest pose (for the gait). */
    static Vector3f[] skinwalkerLegJoints(int i) {
        int side = i < 4 ? -1 : 1;
        int k = i % 4;
        double hz = 0.45 - k * 0.42;
        double spread = (k - 1.5);
        return new Vector3f[]{v(side * SKIN_HIP_X, SKIN_HIP_Y, hz),
                v(side * (1.05 + 0.1 * Math.abs(spread)), 1.95 - Math.abs(spread) * 0.12 + (k % 2) * 0.12, hz + spread * 0.35)};
    }
}
