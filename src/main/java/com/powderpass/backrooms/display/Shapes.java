package com.powderpass.backrooms.display;

import org.bukkit.Material;

import static org.bukkit.Material.*;

/** Reusable composite props, drawn at the model's current transform (origin = floor centre, front = +Z). */
final class Shapes {
    private Shapes() {
    }

    /** Simple four-legged chair; the sitter faces +Z. */
    static void chair(Model m, Material frame, Material seat) {
        double t = 0.06;
        for (double x : new double[]{-0.22, 0.16}) {
            for (double z : new double[]{-0.22, 0.16}) {
                m.box(frame, x, 0, z, t, 0.45, t);
            }
        }
        m.box(seat, -0.25, 0.45, -0.25, 0.5, 0.08, 0.5);
        m.box(frame, -0.22, 0.53, -0.25, t, 0.5, t);
        m.box(frame, 0.16, 0.53, -0.25, t, 0.5, t);
        m.box(seat, -0.25, 0.75, -0.26, 0.5, 0.3, 0.05);
    }

    static void officeChair(Model m, Material cushion) {
        // star base as two crossed bars keeps the silhouette at a fraction of the entity cost
        m.boxC(BLACK_CONCRETE, 0, 0.04, 0, 0.62, 0.05, 0.07, 'y', 30);
        m.boxC(BLACK_CONCRETE, 0, 0.04, 0, 0.62, 0.05, 0.07, 'y', -60);
        m.box(IRON_BLOCK, -0.04, 0.05, -0.04, 0.08, 0.38, 0.08);
        m.box(cushion, -0.26, 0.43, -0.26, 0.52, 0.09, 0.52);
        m.box(BLACK_CONCRETE, -0.03, 0.45, -0.3, 0.06, 0.3, 0.05);
        m.box(cushion, -0.24, 0.62, -0.33, 0.48, 0.48, 0.07);
        m.box(BLACK_CONCRETE, -0.3, 0.6, -0.2, 0.6, 0.04, 0.04);
    }

    /** Desk centred on origin, width along X, depth along Z. */
    static void desk(Model m, Material top, Material side, double w, double d, double h) {
        m.box(top, -w / 2, h - 0.06, -d / 2, w, 0.06, d);
        m.box(side, -w / 2, 0, -d / 2, 0.05, h - 0.06, d);
        m.box(side, w / 2 - 0.05, 0, -d / 2, 0.05, h - 0.06, d);
        m.box(side, -w / 2 + 0.05, 0.25, -d / 2 + 0.02, w - 0.1, h - 0.35, 0.03);
    }

    /** Humanoid silhouette standing at origin, facing +Z. Parts named for creature animation. */
    static void figure(Model m, Material legs, Material body, Material skin, double height) {
        double s = height / 1.9;
        m.push().scale(s);
        m.box(legs, -0.18, 0, -0.08, 0.14, 0.85, 0.16).named("leg_l");
        m.box(legs, 0.04, 0, -0.08, 0.14, 0.85, 0.16).named("leg_r");
        m.box(body, -0.22, 0.85, -0.11, 0.44, 0.65, 0.22).named("torso");
        m.box(body, -0.32, 0.9, -0.07, 0.1, 0.6, 0.14).named("arm_l");
        m.box(body, 0.22, 0.9, -0.07, 0.1, 0.6, 0.14).named("arm_r");
        m.box(skin, -0.13, 1.52, -0.12, 0.26, 0.3, 0.24).named("head");
        m.pop();
    }

    /** Octagonal ring in the XY plane (approximate circle) of radius r. */
    static void ring(Model m, Material mat, double r, double thickness, double depth) {
        double side = 2 * r * Math.tan(Math.PI / 8);
        for (int i = 0; i < 8; i++) {
            m.push().rotate('z', i * 45).box(mat, -side / 2, r - thickness / 2, -depth / 2, side, thickness, depth).pop();
        }
    }

    /** Flat octagon (two rotated squares) in the XY plane centred on (0,0,z): a cheap disc. */
    static void disc(Model m, Material mat, double r, double z, double depth) {
        double side = r * 2 * 0.92;
        m.boxC(mat, 0, 0, z, side, side, depth, 'z', 0);
        m.boxC(mat, 0, 0, z, side, side, depth, 'z', 45);
    }

    /** Text panel on a wall: backing plate plus text, front at +Z. */
    static void plate(Model m, Material back, double w, double h, String text, double textScale) {
        m.box(back, -w / 2, -h / 2, 0, w, h, 0.05);
        m.text(text, 0, -h / 2 + h * 0.18, 0.06, textScale, 0x00000000);
    }
}
