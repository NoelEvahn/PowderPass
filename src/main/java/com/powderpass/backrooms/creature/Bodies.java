package com.powderpass.backrooms.creature;

import com.powderpass.backrooms.display.Model;
import org.bukkit.Material;

import static org.bukkit.Material.*;

/** Display-component bodies of the creatures. Origin = feet, front = +Z; parts are named for animation. */
final class Bodies {
    private Bodies() {
    }

    static Model smiler() {
        Model m = new Model();
        m.box(WHITE_CONCRETE, -0.17, 1.76, 0, 0.09, 0.07, 0.02).glow(15).named("eye_l");
        m.box(WHITE_CONCRETE, 0.08, 1.76, 0, 0.09, 0.07, 0.02).glow(15).named("eye_r");
        for (int i = -5; i <= 5; i++) {
            double a = Math.toRadians(i * 11);
            double x = Math.sin(a) * 0.3, y = 1.5 + (1 - Math.cos(a)) * 0.3;
            m.push().translate(x, y, 0).rotate('z', i * 11);
            m.box(WHITE_CONCRETE, -0.035, -0.02, 0, 0.07, 0.05, 0.02).glow(15).named("grin");
            if (i != 5) m.box(BLACK_CONCRETE, 0.03, -0.02, 0.005, 0.008, 0.05, 0.02).named("grin");
            m.pop();
        }
        return m;
    }

    static Model faceling() {
        Model m = new Model();
        m.push().scale(1.0);
        m.box(BLACK_CONCRETE, -0.16, 0, -0.06, 0.1, 1.25, 0.12).named("leg_l");
        m.box(BLACK_CONCRETE, 0.06, 0, -0.06, 0.1, 1.25, 0.12).named("leg_r");
        m.box(GRAY_CONCRETE, -0.2, 1.25, -0.09, 0.4, 0.75, 0.18).named("torso");
        m.box(GRAY_CONCRETE, -0.29, 0.75, -0.05, 0.08, 1.2, 0.1).named("arm_l");
        m.box(GRAY_CONCRETE, 0.21, 0.75, -0.05, 0.08, 1.2, 0.1).named("arm_r");
        m.box(LIGHT_GRAY_CONCRETE, -0.11, 2.02, -0.1, 0.22, 0.32, 0.2).named("head");
        m.box(GRAY_CONCRETE, -0.04, 1.98, -0.04, 0.08, 0.06, 0.08).named("neck");
        m.pop();
        return m;
    }

    static Model hound() {
        Model m = new Model();
        Material fur = GRAY_TERRACOTTA;
        m.box(fur, -0.25, 0.45, -0.55, 0.5, 0.42, 1.0).named("body");
        m.box(BLACK_TERRACOTTA, -0.2, 0.85, -0.45, 0.4, 0.06, 0.7).named("body");
        m.box(fur, -0.19, 0.62, 0.42, 0.38, 0.34, 0.36).named("head");
        m.box(BLACK_TERRACOTTA, -0.12, 0.5, 0.48, 0.24, 0.1, 0.36).named("jaw");
        m.box(REDSTONE_BLOCK, -0.15, 0.82, 0.775, 0.06, 0.04, 0.01).glow(15).named("eyes");
        m.box(REDSTONE_BLOCK, 0.09, 0.82, 0.775, 0.06, 0.04, 0.01).glow(15).named("eyes");
        m.box(fur, -0.24, 0, 0.25, 0.11, 0.5, 0.11).named("leg_fl");
        m.box(fur, 0.13, 0, 0.25, 0.11, 0.5, 0.11).named("leg_fr");
        m.box(fur, -0.24, 0, -0.5, 0.11, 0.5, 0.11).named("leg_bl");
        m.box(fur, 0.13, 0, -0.5, 0.11, 0.5, 0.11).named("leg_br");
        m.rod(BLACK_TERRACOTTA, Model.v(0, 0.8, -0.55), Model.v(0, 0.55, -1.0), 0.06).named("tail");
        return m;
    }



    static Model drifter() {
        Model m = new Model();
        for (int i = 0; i < 4; i++) {
            double w = 0.45 - i * 0.08;
            m.box(i == 0 ? WHITE_CONCRETE : WHITE_TERRACOTTA, -w / 2, 0.3, -i * 0.55, w, 0.26, 0.55).named("seg" + i);
        }
        m.box(BLACK_CONCRETE, -0.14, 0.48, 0.551, 0.06, 0.05, 0.01).named("seg0");
        m.box(BLACK_CONCRETE, 0.08, 0.48, 0.551, 0.06, 0.05, 0.01).named("seg0");
        m.box(WHITE_TERRACOTTA, -0.45, 0.4, -2.0, 0.9, 0.04, 0.3).named("seg3");
        return m;
    }
}
