package com.powderpass.backrooms.creature;

import com.powderpass.backrooms.display.Model;
import org.bukkit.Material;

import static org.bukkit.Material.*;

/** Display-component bodies of the creatures. Origin = feet, front = +Z; parts are named for animation. */
final class Bodies {
    private Bodies() {
    }

    /** Pivot heights used by the walk animation and head poses. */
    static final double FACELING_HIP = 1.35, FACELING_SHOULDER = 2.12, FACELING_NECK = 2.2;
    static final double WATCHER_HIP = 1.0, WATCHER_SHOULDER = 1.68;
    static final double SMILER_MOUTH = 1.5;

    /** Nothing but eyes and far too many teeth, hanging in the dark. */
    static Model smiler() {
        Model m = new Model();
        // eyes: narrow, slanted, not quite level
        m.boxC(WHITE_CONCRETE, -0.2, 1.86, 0, 0.13, 0.045, 0.02, 'z', -12).glow(15).named("eye");
        m.boxC(WHITE_CONCRETE, 0.21, 1.88, 0, 0.13, 0.045, 0.02, 'z', 9).glow(15).named("eye");
        // a grin that is too wide: an upper and a lower row of teeth along one arc
        for (int i = -6; i <= 6; i++) {
            double a = Math.toRadians(i * 10);
            double x = Math.sin(a) * 0.48, y = SMILER_MOUTH + (1 - Math.cos(a)) * 0.4;
            double tooth = 0.075 - Math.abs(i) * 0.004;
            m.boxC(WHITE_CONCRETE, x, y + 0.035, 0, 0.052, tooth, 0.02, 'z', i * 10).glow(15).named("grin");
            m.boxC(WHITE_CONCRETE, x, y - 0.05, 0, 0.052, tooth * 0.8, 0.02, 'z', i * 10).glow(13).named("grin");
        }
        return m;
    }

    /** Tall, thin, a suit that fits wrong, arms too long, and a smooth blank head. */
    static Model faceling() {
        Model m = new Model();
        Material suit = GRAY_CONCRETE, skin = WHITE_TERRACOTTA, dark = BLACK_CONCRETE;
        // legs and shoes
        m.box(dark, -0.16, 0.06, -0.06, 0.11, FACELING_HIP - 0.06, 0.12).named("leg_l");
        m.box(dark, 0.05, 0.06, -0.06, 0.11, FACELING_HIP - 0.06, 0.12).named("leg_r");
        m.box(dark, -0.17, 0, -0.07, 0.13, 0.06, 0.24).named("leg_l");
        m.box(dark, 0.04, 0, -0.07, 0.13, 0.06, 0.24).named("leg_r");
        // torso: jacket, shirt, a tie
        m.box(suit, -0.21, FACELING_HIP, -0.1, 0.42, 0.8, 0.2).named("torso");
        m.box(WHITE_CONCRETE, -0.05, 1.7, 0.1, 0.1, 0.45, 0.01).named("torso");
        m.box(dark, -0.02, 1.62, 0.105, 0.04, 0.5, 0.01).named("torso");
        // arms reach past the knees; long pale fingers
        double armLen = FACELING_SHOULDER - 0.8;
        for (int s = -1; s <= 1; s += 2) {
            String n = s < 0 ? "arm_l" : "arm_r";
            double ax = s < 0 ? -0.31 : 0.22;
            m.box(suit, ax, 0.8, -0.05, 0.09, armLen, 0.1).named(n);
            m.box(skin, ax + 0.005, 0.66, -0.04, 0.08, 0.14, 0.08).named(n);
            for (int f = 0; f < 3; f++) m.box(skin, ax + 0.01 + f * 0.026, 0.46, -0.02 + f * 0.01, 0.016, 0.2, 0.016).named(n);
        }
        // neck a little too long, head smooth with only faint hollows where a face should be
        m.box(skin, -0.045, 2.15, -0.045, 0.09, 0.15, 0.09).named("head");
        m.box(skin, -0.12, 2.28, -0.11, 0.24, 0.34, 0.22).named("head");
        m.box(skin, -0.1, 2.62, -0.09, 0.2, 0.04, 0.18).named("head");
        m.box(LIGHT_GRAY_CONCRETE, -0.08, 2.46, 0.111, 0.05, 0.03, 0.004).named("head");
        m.box(LIGHT_GRAY_CONCRETE, 0.03, 2.46, 0.111, 0.05, 0.03, 0.004).named("head");
        return m;
    }

    /** A pale, starved human shape on all fours, its face hidden behind long black hair. */
    static Model hound() {
        Model m = new Model();
        Material skin = WHITE_TERRACOTTA, hair = BLACK_WOOL, bone = LIGHT_GRAY_TERRACOTTA;
        m.box(skin, -0.2, 0.52, -0.6, 0.4, 0.3, 1.0).named("body");
        for (int i = 0; i < 4; i++) m.box(bone, -0.04, 0.82, -0.5 + i * 0.25, 0.08, 0.04, 0.1).named("body");
        m.box(bone, -0.21, 0.56, -0.15, 0.42, 0.02, 0.04).named("body");
        m.box(bone, -0.21, 0.56, 0.0, 0.42, 0.02, 0.04).named("body");
        // head hangs low, hair to the floor
        m.box(skin, -0.15, 0.48, 0.38, 0.3, 0.3, 0.3).named("head");
        for (int i = 0; i < 6; i++) {
            double hx = -0.15 + i * 0.054;
            double len = 0.38 + ((i * 7) % 3) * 0.06;
            m.box(hair, hx, 0.8 - len, 0.68, 0.05, len, 0.03).named("head");
        }
        m.box(hair, -0.16, 0.76, 0.36, 0.32, 0.05, 0.34).named("head");
        m.box(WHITE_CONCRETE, -0.08, 0.66, 0.705, 0.03, 0.03, 0.005).glow(9).named("head");
        m.box(WHITE_CONCRETE, 0.05, 0.66, 0.705, 0.03, 0.03, 0.005).glow(9).named("head");
        m.box(BLACK_CONCRETE, -0.1, 0.42, 0.44, 0.2, 0.07, 0.24).named("jaw");
        // limbs: thin, with flat splayed hands
        double[][] legs = {{-0.24, 0.26}, {0.15, 0.26}, {-0.24, -0.5}, {0.15, -0.5}};
        String[] names = {"leg_fl", "leg_fr", "leg_bl", "leg_br"};
        for (int i = 0; i < 4; i++) {
            m.box(skin, legs[i][0], 0.02, legs[i][1], 0.09, 0.56, 0.09).named(names[i]);
            m.box(skin, legs[i][0] - 0.03, 0, legs[i][1] - 0.02, 0.15, 0.03, 0.18).named(names[i]);
        }
        return m;
    }

    /** A pale ribbon of a thing that swims under the tiles. */
    static Model drifter() {
        Model m = new Model();
        for (int i = 0; i < 4; i++) {
            double w = 0.45 - i * 0.08;
            m.box(i == 0 ? WHITE_CONCRETE : WHITE_TERRACOTTA, -w / 2, 0.3, -i * 0.55, w, 0.26, 0.55).named("seg" + i);
        }
        m.box(BLACK_CONCRETE, -0.14, 0.48, 0.551, 0.06, 0.05, 0.01).named("seg0");
        m.box(BLACK_CONCRETE, 0.08, 0.48, 0.551, 0.06, 0.05, 0.01).named("seg0");
        m.box(BLACK_CONCRETE, -0.1, 0.33, 0.551, 0.2, 0.04, 0.01).named("seg0");
        // two long thin arms reaching forward
        m.box(WHITE_TERRACOTTA, -0.28, 0.36, 0.2, 0.05, 0.05, 0.9).named("seg0");
        m.box(WHITE_TERRACOTTA, 0.23, 0.36, 0.2, 0.05, 0.05, 0.9).named("seg0");
        m.box(WHITE_TERRACOTTA, -0.45, 0.4, -2.0, 0.9, 0.04, 0.3).named("seg3");
        return m;
    }

    /** A person-shaped absence: a flat black silhouette that only moves when nobody is looking. */
    static Model watcher() {
        Model m = new Model();
        Material black = BLACK_CONCRETE;
        m.box(black, -0.17, 0, -0.07, 0.13, WATCHER_HIP, 0.14).named("leg_l");
        m.box(black, 0.04, 0, -0.07, 0.13, WATCHER_HIP, 0.14).named("leg_r");
        m.box(black, -0.25, WATCHER_HIP, -0.1, 0.5, 0.72, 0.2).named("torso");
        m.box(black, -0.28, 1.6, -0.09, 0.56, 0.12, 0.18).named("torso");
        m.box(black, -0.34, 0.62, -0.06, 0.1, WATCHER_SHOULDER - 0.62, 0.12).named("arm_l");
        m.box(black, 0.24, 0.62, -0.06, 0.1, WATCHER_SHOULDER - 0.62, 0.12).named("arm_r");
        m.box(black, -0.06, 1.72, -0.05, 0.12, 0.1, 0.1).named("head");
        m.box(black, -0.13, 1.8, -0.12, 0.26, 0.3, 0.24).named("head");
        return m;
    }
}
