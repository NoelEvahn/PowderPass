package com.powderpass.backrooms.display;

import com.powderpass.backrooms.util.Rng;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.joml.Vector3f;

import static com.powderpass.backrooms.display.Anim.Type.*;
import static com.powderpass.backrooms.display.Model.v;
import static org.bukkit.Material.*;

/** Terror Hotel furnishings. */
final class Hotel {
    private Hotel() {
    }

    private static final Vector3f X = v(1, 0, 0), Y = v(0, 1, 0), Z = v(0, 0, 1), O = v(0, 0, 0);

    static boolean build(String id, long seed, Model m) {
        switch (id) {
            case "revolving_door" -> {
                for (int i = 0; i < 12; i++) {
                    if (i == 2 || i == 3 || i == 8 || i == 9) continue; // two open quadrants
                    double a = Math.PI * 2 * i / 12;
                    m.boxC(GOLD_BLOCK, Math.cos(a) * 1.45, 1.4, Math.sin(a) * 1.45, 0.06, 2.8, 0.06, 'y', 0);
                }
                m.boxC(GOLD_BLOCK, 0, 2.85, 0, 3.0, 0.1, 3.0, 'y', 22.5);
                m.boxC(GOLD_BLOCK, 0, 2.85, 0, 3.0, 0.1, 3.0, 'y', 67.5);
                m.group(1);
                m.box(GOLD_BLOCK, -0.06, 0, -0.06, 0.12, 2.8, 0.12);
                for (int i = 0; i < 4; i++) {
                    m.push().rotate('y', i * 90);
                    m.box(GLASS, 0.06, 0.05, -0.02, 1.3, 2.6, 0.04);
                    m.box(GOLD_BLOCK, 0.06, 0.05, -0.03, 1.3, 0.06, 0.06);
                    m.box(GOLD_BLOCK, 1.3, 0.05, -0.03, 0.06, 2.6, 0.06);
                    m.pop();
                }
                m.anim(SPIN, 1, Y, O, 1, 160);
                m.sound(Sound.ITEM_ELYTRA_FLYING, 0, 1.4, 0, 120, 0.6, 0.08, 1.6);
                m.emit(Particle.END_ROD, null, 0, 1.5, 0, 1, 1.2, 0, 20, 0.4);
            }
            case "chandelier" -> {
                m.group(1);
                m.box(IRON_CHAIN, -0.5, -0.9, -0.5, 1, 0.9, 1);
                m.boxC(GOLD_BLOCK, 0, -1.05, 0, 0.3, 0.3, 0.3, 'y', 45);
                int arms = 6;
                for (int i = 0; i < arms; i++) {
                    m.push().rotate('y', i * 360.0 / arms);
                    m.box(GOLD_BLOCK, -0.02, -1.08, 0, 0.04, 0.04, 0.75);
                    m.box(WHITE_CONCRETE, -0.025, -1.04, 0.715, 0.05, 0.2, 0.05);
                    m.box(OCHRE_FROGLIGHT, -0.02, -0.84, 0.72, 0.04, 0.06, 0.04).glow(15);
                    m.pop();
                }
                m.boxC(WHITE_STAINED_GLASS, 0, -1.45, 0, 0.12, 0.3, 0.12, 'y', 45).glow(12);
                m.anim(SWAY, 1, X, O, 1.5, 200);
                m.sound(Sound.BLOCK_CHAIN_STEP, 0, -1, 0, 100, 0.3, 0.2, 0.6);
            }
            case "reception_bell" -> {
                m.box(GOLD_BLOCK, -0.1, 0, -0.1, 0.2, 0.02, 0.2);
                m.boxC(GOLD_BLOCK, 0, 0.06, 0, 0.16, 0.09, 0.16, 'y', 45);
                m.box(GOLD_BLOCK, -0.015, 0.1, -0.015, 0.03, 0.05, 0.03);
                m.hitbox("bell", 0, -0.05, 0, 0.4, 0.3);
            }
            case "key_board" -> {
                m.box(DARK_OAK_PLANKS, -0.9, -0.5, 0, 1.8, 1.0, 0.06);
                for (int r = 0; r < 2; r++) {
                    double y = 0.2 - r * 0.45;
                    m.box(GOLD_BLOCK, -0.8, y, 0.06, 1.6, 0.03, 0.04);
                    for (int c = 0; c < 5; c++) {
                        if (Rng.chance(Rng.hash(seed, r, c), 0.7)) m.item(TRIPWIRE_HOOK, -0.6 + c * 0.3, y - 0.1, 0.1, 0.22, 0, 0);
                    }
                }
            }
            case "elevator_dial" -> {
                m.box(GOLD_BLOCK, -0.35, -0.2, 0, 0.7, 0.4, 0.04);
                m.text("<#3a2a10>1 2 3 4 5", 0, -0.02, 0.045, 0.3, 0x00000000);
                m.group(1).box(BLACK_CONCRETE, -0.01, -0.18, 0.05, 0.02, 0.25, 0.01);
                m.anim(SWAY, 1, Z, v(0, -0.18, 0.05), 60, 300);
            }
            case "room_number" -> {
                int num = seed == 0 ? 0 : 100 + Rng.pick(seed, 400);
                m.box(GOLD_BLOCK, -0.22, -0.09, 0, 0.44, 0.18, 0.02);
                m.text("<#2a1a05><bold>" + String.format("%03d", num), 0, -0.07, 0.025, 0.42, 0x00000000);
            }
            case "dancers" -> {
                m.group(1);
                m.push().translate(-0.25, 0, 0).rotate('y', 90);
                Shapes.figure(m, BLACK_CONCRETE, BLACK_CONCRETE, GRAY_CONCRETE, 1.85);
                m.pop();
                m.push().translate(0.25, 0, 0).rotate('y', -90);
                Shapes.figure(m, WHITE_CONCRETE, WHITE_CONCRETE, GRAY_CONCRETE, 1.7);
                m.box(WHITE_CONCRETE, -0.3, 0, -0.3, 0.6, 0.8, 0.6);
                m.pop();
                m.anim(SPIN, 1, Y, v(0, 0, 0), 1, 140);
                m.anim(BOB, 1, Y, O, 0.05, 35);
                m.hideRadius = 6.5;
                m.behavior("dancers");
            }
            case "gramophone" -> {
                m.box(DARK_OAK_PLANKS, -0.25, 0, -0.25, 0.5, 0.3, 0.5);
                m.group(1).box(BLACK_CONCRETE, -0.2, 0.3, -0.2, 0.4, 0.02, 0.4);
                m.group(0);
                m.rod(GOLD_BLOCK, v(0.15, 0.3, 0.15), v(0.05, 0.6, 0.0), 0.04);
                for (int i = 0; i < 4; i++) {
                    m.push().translate(0.0, 0.6, 0.0).rotate('x', -40).rotate('z', i * 90 + 45).rotate('x', 25);
                    m.box(GOLD_BLOCK, -0.12, 0, -0.01, 0.24, 0.35, 0.02);
                    m.pop();
                }
                m.anim(SPIN, 1, Y, O, 1, 40);
                m.behavior("gramophone");
            }
            case "dining_chair" -> {
                for (double x : new double[]{-0.2, 0.15}) for (double z : new double[]{-0.2, 0.15}) m.box(DARK_OAK_PLANKS, x, 0, z, 0.05, 0.45, 0.05);
                m.box(RED_WOOL, -0.23, 0.45, -0.23, 0.46, 0.08, 0.46);
                m.box(DARK_OAK_PLANKS, -0.23, 0.53, -0.25, 0.46, 0.75, 0.05);
                m.box(RED_WOOL, -0.18, 0.62, -0.21, 0.36, 0.5, 0.02);
            }
            case "candelabra" -> {
                m.box(GOLD_BLOCK, -0.06, 0, -0.06, 0.12, 0.03, 0.12);
                m.box(GOLD_BLOCK, -0.015, 0.03, -0.015, 0.03, 0.35, 0.03);
                m.box(GOLD_BLOCK, -0.2, 0.36, -0.015, 0.4, 0.025, 0.03);
                for (double x : new double[]{-0.19, 0, 0.19}) {
                    double y = x == 0 ? 0.42 : 0.38;
                    m.box(WHITE_CONCRETE, x - 0.02, y, -0.02, 0.04, 0.14, 0.04);
                    m.box(OCHRE_FROGLIGHT, x - 0.012, y + 0.14, -0.012, 0.024, 0.04, 0.024).glow(15).group(1);
                    m.group(0);
                }
                m.anim(FLICKER, 1, O, O, 0.15, 1);
                m.emit(Particle.SMALL_FLAME, null, 0, 0.6, 0, 1, 0.12, 0, 20, 0.6);
            }
            case "grandfather_clock" -> {
                m.box(DARK_OAK_PLANKS, -0.3, 0, -0.22, 0.6, 2.25, 0.44);
                m.box(DARK_OAK_PLANKS, -0.36, 2.25, -0.26, 0.72, 0.12, 0.52);
                m.box(GLASS, -0.18, 0.35, 0.22, 0.36, 1.1, 0.01);
                m.boxC(WHITE_CONCRETE, 0, 1.85, 0.225, 0.4, 0.4, 0.01, 'z', 45);
                m.boxC(WHITE_CONCRETE, 0, 1.85, 0.226, 0.4, 0.4, 0.01, 'z', 0);
                m.group(1).box(BLACK_CONCRETE, -0.01, 1.85, 0.235, 0.02, 0.17, 0.01);
                m.group(2).box(BLACK_CONCRETE, -0.015, 1.85, 0.236, 0.03, 0.11, 0.01);
                m.group(3);
                m.box(GOLD_BLOCK, -0.01, 0.55, 0.15, 0.02, 0.95, 0.02);
                m.boxC(GOLD_BLOCK, 0, 0.55, 0.16, 0.14, 0.14, 0.02, 'z', 45);
                m.anim(SPIN, 1, Z, v(0, 1.85, 0.23), 1, 240);
                m.anim(SPIN, 2, Z, v(0, 1.85, 0.23), 1, 2880);
                m.anim(SWAY, 3, Z, v(0, 1.5, 0.15), 12, 40);
                m.sound(Sound.BLOCK_WOODEN_BUTTON_CLICK_OFF, 0, 1, 0.3, 20, 1.0, 0.35, 0.5);
            }
            case "hotel_bed" -> {
                m.box(DARK_OAK_PLANKS, -0.5, 0, -0.5, 1.0, 0.3, 2.0);
                m.box(DARK_OAK_PLANKS, -0.52, 0, -0.55, 1.04, 1.25, 0.08);
                m.box(DARK_OAK_PLANKS, -0.52, 0, 1.45, 1.04, 0.55, 0.06);
                m.box(WHITE_WOOL, -0.47, 0.3, -0.47, 0.94, 0.22, 1.9);
                m.box(RED_WOOL, -0.49, 0.38, 0.25, 0.98, 0.18, 1.2);
                m.box(WHITE_WOOL, -0.4, 0.52, -0.42, 0.35, 0.12, 0.3);
                m.box(WHITE_WOOL, 0.05, 0.52, -0.42, 0.35, 0.12, 0.3);
            }
            case "table_lamp" -> {
                m.box(GOLD_BLOCK, -0.07, 0, -0.07, 0.14, 0.03, 0.14);
                m.box(GOLD_BLOCK, -0.015, 0.03, -0.015, 0.03, 0.3, 0.03);
                m.box(ORANGE_TERRACOTTA, -0.15, 0.3, -0.15, 0.3, 0.2, 0.3).glow(14).group(1);
                m.anim(FLICKER, 1, O, O, 0.03, 1);
            }
            case "piano" -> {
                m.box(BLACK_CONCRETE, -0.8, 0.6, -0.6, 1.6, 0.35, 1.2);
                m.boxC(BLACK_CONCRETE, 0.5, 0.775, 0.5, 0.9, 0.35, 0.9, 'y', 45);
                m.push().translate(-0.8, 0.95, -0.6).rotate('x', -35);
                m.box(BLACK_CONCRETE, 0, 0, 0, 1.6, 0.03, 1.1);
                m.pop();
                m.box(WHITE_CONCRETE, -0.75, 0.75, -0.75, 1.5, 0.05, 0.16);
                for (int i = 0; i < 10; i++) m.box(BLACK_CONCRETE, -0.7 + i * 0.15, 0.8, -0.75, 0.05, 0.03, 0.09);
                for (double x : new double[]{-0.7, 0.6}) m.box(BLACK_CONCRETE, x, 0, -0.5, 0.1, 0.6, 0.1);
                m.box(BLACK_CONCRETE, 0.6, 0, 0.6, 0.1, 0.6, 0.1);
                m.behavior("piano");
            }
            case "service_cart" -> {
                m.box(IRON_BLOCK, -0.5, 0.75, -0.3, 1.0, 0.04, 0.6);
                m.box(IRON_BLOCK, -0.5, 0.25, -0.3, 1.0, 0.04, 0.6);
                for (double x : new double[]{-0.48, 0.44}) for (double z : new double[]{-0.28, 0.24}) m.box(IRON_BLOCK, x, 0.05, z, 0.04, 0.72, 0.04);
                m.box(WHITE_WOOL, -0.5, 0.79, -0.3, 1.0, 0.02, 0.6);
                m.boxC(IRON_BLOCK, 0, 0.92, 0, 0.42, 0.2, 0.42, 'y', 45);
                m.boxC(IRON_BLOCK, 0, 0.92, 0, 0.42, 0.2, 0.42, 'y', 0);
            }
            case "wall_sconce" -> {
                m.box(GOLD_BLOCK, -0.08, -0.15, 0, 0.16, 0.3, 0.03);
                for (double x : new double[]{-0.18, 0.18}) {
                    m.rod(GOLD_BLOCK, v(0, 0, 0.03), v(x, 0.12, 0.2), 0.025);
                    m.box(ORANGE_TERRACOTTA, x - 0.06, 0.12, 0.14, 0.12, 0.14, 0.12).glow(14).group(1);
                    m.group(0);
                }
                m.anim(FLICKER, 1, O, O, 0.04, 1);
            }
            case "portrait" -> {
                Material frame = (seed & 1) == 0 ? GOLD_BLOCK : DARK_OAK_PLANKS;
                m.box(frame, -0.45, -0.55, 0, 0.9, 1.1, 0.05);
                m.box(BROWN_TERRACOTTA, -0.38, -0.48, 0.051, 0.76, 0.96, 0.01);
                m.box(BLACK_CONCRETE, -0.25, -0.48, 0.062, 0.5, 0.35, 0.01);
                m.box(BLACK_CONCRETE, -0.13, -0.15, 0.062, 0.26, 0.32, 0.01);
                m.group(1);
                m.box(WHITE_CONCRETE, -0.07, 0.0, 0.074, 0.03, 0.03, 0.005).glow(15);
                m.box(WHITE_CONCRETE, 0.04, 0.0, 0.074, 0.03, 0.03, 0.005).glow(15);
                m.anim(BOB, 1, X, O, 0.025, 240);
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
