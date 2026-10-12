package com.powderpass.backrooms.display;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.joml.Vector3f;

import static com.powderpass.backrooms.display.Anim.Type.*;
import static com.powderpass.backrooms.display.Model.v;
import static org.bukkit.Material.*;

/**
 * Light fixtures (the only visible light sources anywhere in the Backrooms: every one is a display model over an
 * invisible light block), The Plains' roadside props and its colossal objects.
 */
final class Fixtures {
    private Fixtures() {
    }

    private static final Vector3f X = v(1, 0, 0), Y = v(0, 1, 0), Z = v(0, 0, 1), O = v(0, 0, 0);

    /** Lamp state from the id suffix: lit, flickering or dead. */
    private static void diffuser(Model m, Part p, String suffix, Material deadMat) {
        if (suffix.equals("dead")) {
            p.block = deadMat.createBlockData();
            p.brightness = null;
            return;
        }
        p.lamp().alt(deadMat.createBlockData());
        if (suffix.equals("flicker")) {
            m.anim(FLICKER, p.group, O, O, 0.3, 1);
            m.sound(Sound.BLOCK_BEACON_AMBIENT, 0, -0.3, 0, 40, 0.6, 0.25, 1.9);
        }
    }

    static boolean build(String id, String suffix, long seed, Model m) {
        switch (id) {
            // ---------------------------------------------------------------- light fixtures
            case "ceiling_panel" -> {
                // a long recessed troffer: dark frame, housing, and a narrow diffuser
                m.box(GRAY_CONCRETE, -0.95, -0.012, -0.26, 1.9, 0.012, 0.52);
                m.box(LIGHT_GRAY_CONCRETE, -0.88, -0.05, -0.2, 1.76, 0.04, 0.4);
                diffuser(m, m.group(1).box(WHITE_CONCRETE, -0.82, -0.07, -0.13, 1.64, 0.025, 0.26), suffix, GRAY_CONCRETE);
                if (!suffix.equals("dead")) m.sound(Sound.BLOCK_BEACON_AMBIENT, 0, -0.5, 0, 160, 0.15, 0.08, 1.8);
            }
            case "tube_light" -> {
                m.group(2);
                m.rod(BLACK_CONCRETE, v(-0.5, 0, 0), v(-0.5, -1.1, 0), 0.025);
                m.rod(BLACK_CONCRETE, v(0.5, 0, 0), v(0.5, -1.1, 0), 0.025);
                m.box(LIGHT_GRAY_CONCRETE, -0.75, -1.18, -0.12, 1.5, 0.08, 0.24);
                diffuser(m, m.group(1).box(WHITE_CONCRETE, -0.7, -1.23, -0.06, 1.4, 0.05, 0.12), suffix, GRAY_CONCRETE);
                m.anim(SWAY, 1, X, O, 2, 110);
                m.anim(SWAY, 2, X, O, 2, 110);
            }
            case "cage_lamp" -> {
                m.group(2);
                m.box(BLACK_CONCRETE, -0.02, -0.7, -0.02, 0.04, 0.7, 0.04);
                m.box(GRAY_CONCRETE, -0.22, -0.8, -0.22, 0.44, 0.1, 0.44);
                for (int i = 0; i < 4; i++) m.push().rotate('y', i * 90).box(BLACK_CONCRETE, -0.01, -1.05, 0.15, 0.02, 0.25, 0.02).pop();
                diffuser(m, m.group(1).box(WHITE_CONCRETE, -0.09, -0.98, -0.09, 0.18, 0.18, 0.18), suffix, GRAY_CONCRETE);
                m.anim(SWAY, 1, Z, O, 2, 130);
                m.anim(SWAY, 2, Z, O, 2, 130);
            }
            case "wall_lamp" -> {
                m.box(GRAY_CONCRETE, -0.2, -0.14, 0, 0.4, 0.28, 0.06);
                diffuser(m, m.group(1).box(WHITE_CONCRETE, -0.15, -0.09, 0.05, 0.3, 0.18, 0.1), suffix, GRAY_CONCRETE);
                for (int i = 0; i < 3; i++) m.group(0).box(BLACK_CONCRETE, -0.16 + i * 0.15, -0.1, 0.15, 0.015, 0.2, 0.015);
            }
            case "skylight_panel" -> {
                m.box(LIGHT_GRAY_CONCRETE, -1.6, -0.05, -1.6, 3.2, 0.05, 3.2);
                diffuser(m, m.group(1).box(WHITE_CONCRETE, -1.45, -0.07, -1.45, 2.9, 0.03, 2.9), suffix, LIGHT_GRAY_CONCRETE);
            }
            case "pool_lamp" -> {
                Shapes.disc(m, LIGHT_GRAY_CONCRETE, 0.35, 0, 0.02);
                m.parts.forEach(p -> p.local.rotateLocalX((float) Math.toRadians(-90)));
                Part glow = m.group(1).box(WHITE_CONCRETE, -0.22, 0.0, -0.22, 0.44, 0.03, 0.44);
                diffuser(m, glow, suffix, GRAY_CONCRETE);
            }
            case "floor_lamp" -> {
                m.box(BLACK_CONCRETE, -0.12, 0, -0.12, 0.24, 0.04, 0.24);
                m.box(BLACK_CONCRETE, -0.02, 0.04, -0.02, 0.04, 1.3, 0.04);
                diffuser(m, m.group(1).box(WHITE_WOOL, -0.2, 1.3, -0.2, 0.4, 0.32, 0.4), suffix, LIGHT_GRAY_WOOL);
            }
            case "table_lamp" -> {
                m.box(GRAY_CONCRETE, -0.07, 0, -0.07, 0.14, 0.03, 0.14);
                m.box(GRAY_CONCRETE, -0.015, 0.03, -0.015, 0.03, 0.3, 0.03);
                diffuser(m, m.group(1).box(WHITE_WOOL, -0.15, 0.3, -0.15, 0.3, 0.2, 0.3), suffix, LIGHT_GRAY_WOOL);
            }
            case "daylight_door" -> {
                // light spilling around and under a closed door, from a place with a sun
                m.box(WHITE_CONCRETE, -0.5, 0.0, 0.5, 1.0, 0.01, 0.6).lamp();
                m.box(WHITE_CONCRETE, -0.5, 0.0, -0.1, 1.0, 0.01, 0.6).lamp();
                m.box(WHITE_STAINED_GLASS, -0.52, 0.0, 0.47, 0.02, 2.0, 0.06).lamp();
                m.box(WHITE_STAINED_GLASS, 0.5, 0.0, 0.47, 0.02, 2.0, 0.06).lamp();
                m.emit(Particle.WHITE_ASH, null, 0, 1, 0.6, 2, 0.4, 0.01, 10, 0.6);
                m.sound(Sound.ITEM_ELYTRA_FLYING, 0, 1, 0.5, 120, 0.6, 0.12, 0.5);
                m.sound(Sound.ENTITY_PARROT_AMBIENT, 0, 1, 0.5, 200, 0.4, 0.2, 1.4);
            }
            case "daylight_window" -> {
                m.box(WHITE_STAINED_GLASS, -1.5, 0.0, -0.02, 3.0, 3.0, 0.04).lamp();
                m.emit(Particle.WHITE_ASH, null, 0, 1.5, 0.3, 3, 1.0, 0.01, 10, 0.6);
                m.sound(Sound.ITEM_ELYTRA_FLYING, 0, 1.5, 0, 120, 0.6, 0.12, 0.5);
                m.sound(Sound.ENTITY_PARROT_AMBIENT, 0, 1.5, 0, 200, 0.4, 0.2, 1.4);
            }
            case "roll_shutter" -> {
                for (int i = 0; i < 8; i++) m.box(IRON_BLOCK, -3.0, 0.45 + i * 0.4, 0, 6.0, 0.36, 0.06);
                m.box(GRAY_CONCRETE, -3.2, 3.6, -0.1, 6.4, 0.4, 0.3);
                m.box(WHITE_CONCRETE, -3.0, 0.0, 0.03, 6.0, 0.45, 0.02).lamp();
                m.emit(Particle.WHITE_ASH, null, 0, 0.3, 0.2, 3, 2.5, 0.01, 10, 0.6);
                m.sound(Sound.ITEM_ELYTRA_FLYING, 0, 0.5, 0.2, 100, 0.6, 0.12, 0.5);
            }
            // ---------------------------------------------------------------- the plains
            case "utility_pole" -> {
                m.box("minecraft:stripped_spruce_log", -0.15, 0, -0.15, 0.3, 10.4, 0.3);
                m.box("minecraft:stripped_spruce_log", -1.4, 9.4, -0.1, 2.8, 0.22, 0.2);
                for (double x : new double[]{-1.2, 0, 1.2}) {
                    m.box(WHITE_TERRACOTTA, x - 0.05, 9.62, -0.05, 0.1, 0.18, 0.1);
                    Vector3f prev = v(x, 9.8, 0);
                    for (int i = 1; i <= 3; i++) {
                        double t = i / 3.0;
                        Vector3f nxt = v(x, 9.8 - Math.sin(Math.PI * t) * 0.9, 16 * t);
                        m.rod(BLACK_CONCRETE, prev, nxt, 0.035);
                        prev = nxt;
                    }
                }
                m.sound(Sound.BLOCK_BEACON_AMBIENT, 0, 9, 0, 100, 0.5, 0.25, 0.55);
                m.viewRange = 2.5f;
            }
            case "mailbox" -> {
                m.box("minecraft:stripped_spruce_log", -0.05, 0, -0.05, 0.1, 1.0, 0.1);
                m.box(GRAY_CONCRETE, -0.17, 1.0, -0.3, 0.34, 0.3, 0.6);
                m.box(RED_CONCRETE, 0.17, 1.15, -0.1, 0.03, 0.35, 0.06);
                m.box(WHITE_CONCRETE, -0.1, 1.05, 0.29, 0.2, 0.15, 0.03);
            }
            case "fallen_panel" -> {
                m.push().rotate('z', 8).rotate('x', 4);
                m.box(LIGHT_GRAY_CONCRETE, -0.72, 0.02, -0.27, 1.44, 0.06, 0.54);
                m.box(WHITE_CONCRETE, -0.66, 0.08, -0.21, 1.32, 0.03, 0.42).lamp().group(1);
                m.pop();
                m.anim(FLICKER, 1, O, O, 0.08, 1);
                m.sound(Sound.BLOCK_BEACON_AMBIENT, 0, 0.3, 0, 80, 0.6, 0.3, 1.9);
            }
            case "lone_doorframe" -> {
                m.box(DARK_OAK_PLANKS, -0.65, 0, -0.08, 0.15, 2.3, 0.16);
                m.box(DARK_OAK_PLANKS, 0.5, 0, -0.08, 0.15, 2.3, 0.16);
                m.box(DARK_OAK_PLANKS, -0.65, 2.15, -0.08, 1.3, 0.15, 0.16);
                m.push().translate(-0.5, 0, 0).rotate('y', -65);
                m.box("minecraft:spruce_planks", 0, 0, -0.03, 1.0, 2.1, 0.06);
                m.pop();
            }
            case "exit_sign_post" -> {
                m.box("minecraft:stripped_spruce_log", -0.06, 0, -0.06, 0.12, 2.4, 0.12);
                m.box(WHITE_CONCRETE, -0.4, 2.4, -0.04, 0.8, 0.3, 0.08);
                m.text("<#36ff6b><bold>EXIT</bold> →", 0, 2.45, 0.045, 0.6, 0x00000000).lamp();
            }
            // ---------------------------------------------------------------- colossi
            case "giant_chair" -> {
                m.push().scale(22);
                Shapes.chair(m, BIRCH_PLANKS, BROWN_WOOL);
                m.pop();
                m.viewRange = 8f;
            }
            case "giant_doorframe" -> {
                m.box(DARK_OAK_PLANKS, -2.8, 0, -0.4, 0.8, 14, 0.8);
                m.box(DARK_OAK_PLANKS, 2.0, 0, -0.4, 0.8, 14, 0.8);
                m.box(DARK_OAK_PLANKS, -2.8, 14, -0.4, 5.6, 0.8, 0.8);
                m.push().translate(2.0, 0, 0).rotate('y', 70);
                m.box("minecraft:spruce_planks", -4.0, 0, -0.15, 4.0, 13.6, 0.3);
                m.box(GOLD_BLOCK, -3.5, 6.3, 0.15, 0.3, 0.3, 0.3);
                m.pop();
                m.emit(Particle.END_ROD, null, 0, 3, 0, 2, 1.5, 0.0, 10, 0.6);
                m.sound(Sound.ITEM_ELYTRA_FLYING, 0, 2, 0, 100, 0.8, 0.3, 0.6);
                m.viewRange = 8f;
            }
            case "giant_stairs" -> {
                for (int i = 0; i < 12; i++) {
                    m.box("minecraft:quartz_stairs[facing=north]", -3, i * 5.0, -i * 5.0, 6, 6, 6);
                }
                m.viewRange = 8f;
            }
            case "giant_outlet" -> {
                m.box(WHITE_CONCRETE, -7, 0, -0.6, 14, 20, 1.2);
                for (double y : new double[]{4.5, 12.5}) {
                    m.box(BLACK_CONCRETE, -2.5, y, 0.6, 0.8, 3.2, 0.05);
                    m.box(BLACK_CONCRETE, 1.7, y, 0.6, 0.8, 3.2, 0.05);
                    m.box(BLACK_CONCRETE, -0.6, y - 2.0, 0.6, 1.2, 1.2, 0.05);
                }
                m.viewRange = 8f;
            }
            case "giant_figure" -> {
                m.push().scale(28);
                Shapes.figure(m, BLACK_CONCRETE, BLACK_CONCRETE, BLACK_CONCRETE, 1.9);
                m.pop();
                m.viewRange = 10f;
            }
            case "floating_panel" -> {
                m.group(1);
                m.box(LIGHT_GRAY_CONCRETE, -15, 0, -5, 30, 0.8, 10);
                m.box(WHITE_CONCRETE, -14, -0.2, -4, 28, 0.2, 8).lamp();
                m.anim(BOB, 1, Y, O, 0.6, 400);
                m.sound(Sound.BLOCK_BEACON_AMBIENT, 0, -50, 0, 60, 0.7, 2.0, 1.6);
                m.viewRange = 10f;
            }
            case "lone_wall" -> {
                m.box(SMOOTH_SANDSTONE, -20, 0, -0.5, 40, 14, 1);
                m.box(BROWN_TERRACOTTA, -20, 0, -0.52, 40, 0.6, 1.04);
                m.box(LIGHT_GRAY_CONCRETE, -1.5, 11.5, 0.5, 3, 0.15, 0.8);
                m.box(WHITE_CONCRETE, -1.4, 11.45, 0.55, 2.8, 0.05, 0.7).lamp().group(1);
                m.box(WHITE_CONCRETE, 6, 8, 0.5, 2.2, 0.8, 0.12);
                m.text("<#ff2b2b><bold>EXIT", 7.1, 8.1, 0.63, 2.0, 0x00000000).lamp();
                m.anim(FLICKER, 1, O, O, 0.05, 1);
                m.viewRange = 8f;
            }
            case "giant_tv" -> {
                m.box(GRAY_CONCRETE, -7, 0, -5, 14, 10, 10);
                m.box(BLACK_CONCRETE, -6, 1.2, 5.0, 12, 7.6, 0.1);
                m.group(1).box(LIGHT_GRAY_CONCRETE, -5.6, 1.6, 5.1, 11.2, 6.8, 0.05).lamp().alt(WHITE_CONCRETE.createBlockData());
                m.group(0);
                m.text("<white><bold>NO SIGNAL", 0, 4.4, 5.2, 5.0, 0x00000000).lamp();
                m.rod(BLACK_CONCRETE, v(-1, 10, 0), v(-5, 17, -1), 0.25);
                m.rod(BLACK_CONCRETE, v(1, 10, 0), v(5, 17, 1), 0.25);
                m.anim(BLINK, 1, O, O, 0.5, 1);
                m.sound(Sound.BLOCK_FIRE_AMBIENT, 0, 5, 5, 40, 1.0, 1.5, 1.8);
                m.viewRange = 8f;
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
