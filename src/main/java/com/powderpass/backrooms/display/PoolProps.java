package com.powderpass.backrooms.display;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.joml.Vector3f;

import static com.powderpass.backrooms.display.Anim.Type.*;
import static com.powderpass.backrooms.display.Model.v;
import static org.bukkit.Material.*;

/**
 * Display props of the Poolrooms: floaties that bob and drift on the water (rings, a duck ring, an air mattress, a
 * beach ball), pool loungers, a small classical bust, floor signs on the stair towers and the running water sheet
 * of the slides. Returns true when {@code id} is one of its models and has been built into {@code m}.
 */
final class PoolProps {
    private PoolProps() {
    }

    private static final Vector3f X = v(1, 0, 0), Y = v(0, 1, 0), Z = v(0, 0, 1), O = v(0, 0, 0);
    private static final Material[][] RINGS = {
            {RED_CONCRETE, WHITE_CONCRETE}, {PINK_CONCRETE, WHITE_CONCRETE}, {YELLOW_CONCRETE, ORANGE_CONCRETE}, {LIGHT_BLUE_CONCRETE, WHITE_CONCRETE}};
    private static final String[] FLOOR_LABELS = {"LEVEL P-1", "LEVEL P0", "LEVEL P1"};

    /** Gentle bobbing, rocking and a slow drift, phase-shifted per prop so a pool never moves in step. */
    private static void floating(Model m, long seed, double drift) {
        double ph = (seed & 1023) / 1023.0;
        m.anim(BOB, 1, Y, O, 0.045, 64 + (seed & 15), ph);
        m.anim(SWAY, 1, X, O, 3.5, 90 + (seed >> 4 & 31), ph);
        m.anim(SWAY, 1, Z, O, 2.5, 117, ph + 0.3);
        if (drift > 0) m.anim(ORBIT, 1, Y, O, drift, 900 + (seed >> 9 & 255), ph);
    }

    /** A flat inflatable torus of eight segments in alternating colours, lying in the XZ plane. */
    private static void torus(Model m, Material a, Material b, double r, double t) {
        double side = 2 * r * Math.tan(Math.PI / 8) + 0.02;
        for (int i = 0; i < 8; i++) {
            m.push().rotate('y', i * 45).box(i % 2 == 0 ? a : b, -side / 2, 0, r - t / 2, side, t, t).pop();
        }
    }

    static boolean build(String id, String suffix, long seed, Model m) {
        switch (id) {
            case "floatie_ring" -> {
                int c = Math.floorMod(suffix.isEmpty() ? (int) seed : Integer.parseInt(suffix), RINGS.length);
                m.group(1);
                torus(m, RINGS[c][0], RINGS[c][1], 0.42, 0.22);
                floating(m, seed, 0.35);
            }
            case "floatie_duck" -> {
                m.group(1);
                torus(m, YELLOW_CONCRETE, YELLOW_CONCRETE, 0.48, 0.24);
                m.box(YELLOW_CONCRETE, -0.09, 0.12, 0.38, 0.18, 0.42, 0.16);             // neck
                m.box(YELLOW_CONCRETE, -0.13, 0.5, 0.36, 0.26, 0.24, 0.28);              // head
                m.box(ORANGE_CONCRETE, -0.08, 0.55, 0.64, 0.16, 0.07, 0.14);             // beak
                m.box(BLACK_CONCRETE, -0.135, 0.63, 0.52, 0.01, 0.05, 0.05);             // eyes
                m.box(BLACK_CONCRETE, 0.125, 0.63, 0.52, 0.01, 0.05, 0.05);
                m.box(YELLOW_CONCRETE, -0.08, 0.1, -0.6, 0.16, 0.16, 0.14);              // tail
                floating(m, seed, 0.3);
            }
            case "floatie_mattress" -> {
                m.group(1);
                boolean pink = (seed & 2) == 0;
                for (int i = 0; i < 5; i++) {
                    Material mat = i % 2 == 0 ? (pink ? PINK_CONCRETE : LIGHT_BLUE_CONCRETE) : WHITE_CONCRETE;
                    m.box(mat, -0.45 + i * 0.18, 0, -0.95, 0.18, 0.14, 1.6);
                }
                m.box(pink ? MAGENTA_CONCRETE : BLUE_CONCRETE, -0.45, 0, 0.65, 0.9, 0.2, 0.32); // pillow
                floating(m, seed, 0.4);
            }
            case "beach_ball" -> {
                m.group(1);
                Material[] cols = {RED_CONCRETE, YELLOW_CONCRETE, BLUE_CONCRETE, WHITE_CONCRETE};
                for (int i = 0; i < 4; i++) m.boxC(cols[i], 0, 0.24, 0, 0.44, 0.4, 0.44, 'y', i * 22.5);
                m.boxC(WHITE_CONCRETE, 0, 0.24, 0, 0.32, 0.5, 0.32, 'y', 11);
                m.anim(SPIN, 1, v(0.3, 1, 0.2).normalize(), v(0, 0.24, 0), 1, 700);
                m.anim(BOB, 1, Y, O, 0.05, 50);
                m.anim(ORBIT, 1, Y, O, 0.6, 1100);
            }
            case "pool_lounger" -> {
                // a white slatted sun lounger with a blue cushion, back raised toward -Z
                for (double x : new double[]{-0.33, 0.27}) {
                    m.box(WHITE_CONCRETE, x, 0, -0.85, 0.06, 0.3, 0.06);
                    m.box(WHITE_CONCRETE, x, 0, 0.75, 0.06, 0.3, 0.06);
                }
                m.box(WHITE_CONCRETE, -0.35, 0.28, -0.4, 0.7, 0.05, 1.25);
                m.box(LIGHT_BLUE_WOOL, -0.3, 0.33, -0.35, 0.6, 0.07, 1.15);
                m.push().translate(0, 0.3, -0.4).rotate('x', 55);
                m.box(WHITE_CONCRETE, -0.35, 0, -0.75, 0.7, 0.05, 0.75);
                m.box(LIGHT_BLUE_WOOL, -0.3, 0.05, -0.7, 0.6, 0.07, 0.68);
                m.pop();
                m.box(WHITE_CONCRETE, -0.04, 0.15, 0.55, 0.08, 0.04, 0.4);
            }
            case "greek_bust" -> {
                // a classical head on a plinth, about life size and a half: face toward +Z
                m.box(SMOOTH_QUARTZ, -0.35, 0, -0.35, 0.7, 1.0, 0.7);
                m.box(QUARTZ_PILLAR, -0.42, 1.0, -0.42, 0.84, 0.08, 0.84);
                m.box(SMOOTH_QUARTZ, -0.13, 1.08, -0.15, 0.26, 0.28, 0.28);                 // neck
                m.box(SMOOTH_QUARTZ, -0.34, 1.26, -0.3, 0.6, 0.12, 0.5);                   // shoulders
                m.box(SMOOTH_QUARTZ, -0.22, 1.36, -0.26, 0.44, 0.62, 0.5);                 // head
                m.box(SMOOTH_QUARTZ, -0.15, 1.33, 0.12, 0.3, 0.14, 0.14);                  // chin
                m.box(SMOOTH_QUARTZ, -0.2, 1.7, 0.22, 0.4, 0.05, 0.06);                    // brow
                m.box(SMOOTH_QUARTZ, -0.035, 1.5, 0.23, 0.07, 0.2, 0.09);                  // nose
                m.box(GRAY_CONCRETE, -0.16, 1.62, 0.235, 0.09, 0.04, 0.01);                // eye shadows
                m.box(GRAY_CONCRETE, 0.07, 1.62, 0.235, 0.09, 0.04, 0.01);
                m.box(LIGHT_GRAY_CONCRETE, -0.07, 1.44, 0.24, 0.14, 0.02, 0.01);           // lips
                m.box(CALCITE, -0.25, 1.82, -0.3, 0.5, 0.2, 0.52);                         // hair cap
                m.box(CALCITE, -0.25, 1.45, -0.3, 0.5, 0.4, 0.1);                          // back of the hair
                for (int i = 0; i < 5; i++) m.box(CALCITE, -0.22 + i * 0.1, 1.76, 0.18, 0.08, 0.08, 0.08); // fringe curls
                m.box(CALCITE, -0.27, 1.55, -0.2, 0.06, 0.3, 0.3);
                m.box(CALCITE, 0.21, 1.55, -0.2, 0.06, 0.3, 0.3);
            }
            case "floor_sign" -> {
                int f = suffix.isEmpty() ? 1 : Math.max(0, Math.min(2, Integer.parseInt(suffix)));
                m.box(LIGHT_BLUE_CONCRETE, -0.45, -0.22, 0, 0.9, 0.44, 0.04);
                m.text("<white><bold>" + FLOOR_LABELS[f] + "</bold><br><white>▲ ▼ stairs", 0, -0.18, 0.045, 0.32, 0x00000000);
            }
            case "slide_water" -> {
                // a thin sheet of running water lying on a chute; +Z = downhill, length in blocks
                int len = suffix.isEmpty() ? 8 : Math.max(1, Integer.parseInt(suffix));
                m.box(LIGHT_BLUE_STAINED_GLASS, -0.4, 0, 0, 0.8, 0.05, len);
                m.group(1);
                for (int i = 0; i < 3; i++) m.box(WHITE_STAINED_GLASS, -0.3 + i * 0.25, 0.055, i * 0.7, 0.07, 0.01, len - 2.4);
                m.anim(SLIDE, 1, Z, O, 1.2, 9);
                m.emit(Particle.SPLASH, null, 0, 0.2, len, 4, 0.4, 0.1, 6, 0.8);
                m.emit(Particle.FALLING_WATER, null, 0, 0.1, len * 0.5, 2, 1.2, 0.0, 10, 0.5);
                m.sound(Sound.BLOCK_WATER_AMBIENT, 0, 0, len * 0.5, 60, 0.7, 0.4, 1.3);
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
