package com.powderpass.backrooms.display;

import org.bukkit.Material;

import static com.powderpass.backrooms.display.Model.v;
import static org.bukkit.Material.*;

/**
 * Display props of the Plains: everyday things about forty blocks tall standing in the fields, each built from a
 * handful of large scaled blocks (every one well under the 32-entity budget). None of them gives off light; the
 * lamp and the light bulb are off. Origin = ground centre, front = +Z.
 * Returns true when {@code id} is one of its models and has been built into {@code m}.
 */
final class PlainsProps {
    private PlainsProps() {
    }

    /** Seen from far across the fields (multiplier of 64 blocks; the server's entity tracking range still caps it). */
    private static final float FAR = 10f;

    static boolean build(String id, String suffix, long seed, Model m) {
        switch (id) {
            case "giant_dining_chair" -> {
                m.push().scale(38);
                Shapes.chair(m, SPRUCE_PLANKS, WHITE_WOOL);
                m.pop();
            }
            case "giant_lamp" -> {
                // a floor lamp, switched off
                oct(m, BLACK_CONCRETE, 0, 0, 0, 5, 1.2);
                oct(m, BLACK_CONCRETE, 0, 1.2, 0, 2.2, 1);
                m.box(BLACK_CONCRETE, -0.6, 2.2, -0.6, 1.2, 27.8, 1.2);
                m.box(GRAY_CONCRETE, -1.2, 28.5, -1.2, 2.4, 2.5, 2.4);
                oct(m, WHITE_TERRACOTTA, 0, 28, 0, 8, 4);
                oct(m, WHITE_TERRACOTTA, 0, 32, 0, 7, 4);
                oct(m, WHITE_TERRACOTTA, 0, 36, 0, 6, 3.6);
                m.box(GRAY_CONCRETE, -0.8, 39.6, -0.8, 1.6, 0.8, 1.6);
                m.rod(IRON_BLOCK, v(3.2, 28, 3.2), v(3.2, 22.5, 3.2), 0.22);
                m.box(GOLD_BLOCK, 2.85, 21.8, 2.85, 0.7, 0.9, 0.7);
            }
            case "giant_cone" -> {
                // a traffic cone: orange, two white bands
                m.box(ORANGE_CONCRETE, -9, 0, -9, 18, 1.4, 18);
                for (int i = 0; i < 7; i++) {
                    Material mat = i == 2 || i == 4 ? WHITE_CONCRETE : ORANGE_CONCRETE;
                    oct(m, mat, 0, 1.4 + i * 4, 0, 6.5 - i * 0.8, 4);
                }
                oct(m, ORANGE_CONCRETE, 0, 29.4, 0, 1.1, 1.0);
            }
            case "giant_duck" -> {
                // a rubber duck, looking at you
                m.box(YELLOW_CONCRETE, -7, 0, -10, 14, 2, 20);
                m.boxC(YELLOW_CONCRETE, 0, 7.5, 0, 18, 11, 24, 'y', 0);
                m.boxC(YELLOW_CONCRETE, 0, 7, 0, 14, 10, 28, 'y', 0);
                m.boxC(YELLOW_CONCRETE, 0, 8.5, -1, 21, 7, 18, 'y', 0);
                m.boxC(YELLOW_CONCRETE, 0, 13.5, -11.5, 9, 7, 6, 'x', -30);
                m.boxC(YELLOW_CONCRETE, 0, 20, 7, 12, 11, 11, 'y', 0);
                m.boxC(YELLOW_CONCRETE, 0, 20, 7, 10, 13.5, 9, 'y', 0);
                m.boxC(YELLOW_CONCRETE, 0, 20, 7, 13.5, 9, 9, 'y', 0);
                m.boxC(ORANGE_CONCRETE, 0, 18.6, 14, 7, 2.4, 5, 'y', 0);
                m.boxC(ORANGE_CONCRETE, 0, 17.3, 13.4, 5.6, 1.2, 4, 'y', 0);
                for (int s = -1; s <= 1; s += 2) {
                    m.box(BLACK_CONCRETE, s * 3.4 - 0.9, 21, 12.45, 1.8, 2.4, 0.4);
                    m.box(WHITE_CONCRETE, s * 3.4 - 0.5, 22.3, 12.8, 0.6, 0.6, 0.2);
                    m.boxC(YELLOW_CONCRETE, s * 10.4, 9, -2, 1.6, 6, 12, 'z', s * -8);
                }
            }
            case "giant_crt" -> {
                // a box television, off: grey glass and a dead power light
                for (int i = 0; i < 4; i++) {
                    m.box(BLACK_CONCRETE, (i % 2 == 0 ? -10 : 8), 0, (i < 2 ? -8 : 6), 2, 3, 2);
                }
                m.box(WHITE_TERRACOTTA, -12, 3, -10, 24, 20, 20);
                m.box(WHITE_TERRACOTTA, -9, 5, -19, 18, 16, 9);
                m.box(WHITE_TERRACOTTA, -6, 7.5, -23, 12, 11, 4);
                m.box(BLACK_CONCRETE, -10.5, 5, 9.9, 15.5, 16, 0.5);
                m.box(GRAY_CONCRETE, -9.6, 5.9, 10.2, 13.7, 14.2, 0.5);
                m.box(LIGHT_GRAY_CONCRETE, 6, 5, 10.0, 4.5, 16, 0.4);
                m.box(BLACK_CONCRETE, 7.3, 16, 10.2, 2, 2, 1);
                m.box(BLACK_CONCRETE, 7.3, 12, 10.2, 2, 2, 1);
                m.box(RED_CONCRETE, 7.9, 9, 10.3, 0.8, 0.8, 0.3);
                for (int i = 0; i < 3; i++) m.box(GRAY_CONCRETE, 6.8, 6 + i * 0.9, 10.3, 3, 0.35, 0.2);
                m.box(BLACK_CONCRETE, -1.5, 23, -2, 3, 1.2, 3);
                m.rod(IRON_BLOCK, v(-0.5, 24, -0.5), v(-7, 35, -3), 0.35);
                m.rod(IRON_BLOCK, v(0.5, 24, -0.5), v(8, 33, -2), 0.35);
            }
            case "giant_phone" -> {
                // a rotary telephone, red, receiver on the hook
                m.box(RED_CONCRETE, -13, 0, -11, 26, 3, 22);
                m.box(RED_CONCRETE, -12, 3, -10, 24, 7, 20);
                m.box(RED_CONCRETE, -10, 10, -9, 20, 4, 14);
                m.box(RED_CONCRETE, -9.5, 14, -3.5, 2.5, 4, 3);
                m.box(RED_CONCRETE, 7, 14, -3.5, 2.5, 4, 3);
                m.box(RED_CONCRETE, -14, 18, -3, 28, 2.6, 4.4);
                m.box(RED_CONCRETE, -16, 15.5, -4, 6, 3.4, 6.5);
                m.box(RED_CONCRETE, 10, 15.5, -4, 6, 3.4, 6.5);
                // the dial on the sloped front
                m.push().translate(0, 8, 10.05).rotate('x', -18);
                Shapes.disc(m, WHITE_CONCRETE, 6.2, 0, 0.5);
                Shapes.disc(m, RED_CONCRETE, 2.4, 0.3, 0.4);
                for (int i = 0; i < 6; i++) {
                    double a = Math.toRadians(-40 + i * 40);
                    m.box(BLACK_CONCRETE, Math.cos(a) * 4.3 - 0.6, Math.sin(a) * 4.3 - 0.6, 0.2, 1.2, 1.2, 0.2);
                }
                m.pop();
                // the cord, sagging to the ground and back up into the side
                m.rod(BLACK_CONCRETE, v(-16, 16, 0), v(-19, 4, 3), 0.6);
                m.rod(BLACK_CONCRETE, v(-19, 4, 3), v(-17, 0.4, 9), 0.6);
                m.rod(BLACK_CONCRETE, v(-17, 0.4, 9), v(-12.5, 5, 6), 0.6);
            }
            case "giant_fan" -> {
                // a desk fan, not turning
                oct(m, GRAY_CONCRETE, 0, 0, 0, 7, 2);
                m.box(GRAY_CONCRETE, -1, 2, -1, 2, 20, 2);
                m.box(GRAY_CONCRETE, -3, 22, -7, 6, 6, 9);
                m.box(GRAY_CONCRETE, -1.5, 28, 0, 3, 2, 3);
                m.push().translate(0, 30, 4);
                Shapes.ring(m, LIGHT_GRAY_CONCRETE, 10, 0.6, 1.4);
                m.boxC(LIGHT_GRAY_CONCRETE, 0, 0, 0.7, 20, 0.3, 0.3, 'z', 45);
                m.boxC(LIGHT_GRAY_CONCRETE, 0, 0, 0.7, 20, 0.3, 0.3, 'z', -45);
                m.box(GRAY_CONCRETE, -1.6, -1.6, -0.6, 3.2, 3.2, 2);
                for (int i = 0; i < 3; i++) {
                    m.push().rotate('z', i * 120 + 15);
                    m.box(LIGHT_BLUE_CONCRETE, -2, 1.4, -0.2, 4, 7.6, 0.4);
                    m.pop();
                }
                m.pop();
            }
            case "giant_shoe" -> {
                // one sneaker, laces undone
                m.box(WHITE_CONCRETE, -7, 0, -19, 14, 3, 38);
                m.box(BLUE_CONCRETE, -6.5, 3, -18, 13, 12, 16);
                m.box(BLUE_CONCRETE, -6.5, 3, -2, 13, 9, 10);
                m.box(BLUE_CONCRETE, -6, 3, 8, 12, 5.5, 9);
                m.box(WHITE_CONCRETE, -6.2, 3, 16, 12.4, 4, 2.6);
                m.boxC(BLUE_CONCRETE, 0, 12.5, 0, 6, 1.6, 10, 'x', -25);
                m.box(BLACK_CONCRETE, -4.5, 14.9, -15, 9, 0.3, 9);
                m.box(WHITE_CONCRETE, -1.2, 12, -19.2, 2.4, 5, 1.4);
                for (int i = 0; i < 4; i++) m.boxC(WHITE_CONCRETE, 0, 12.3 - i * 1.2, -4 + i * 2.6, 7, 0.6, 0.8, 'y', i % 2 == 0 ? 8 : -8);
                for (int s = -1; s <= 1; s += 2) m.boxC(WHITE_CONCRETE, s * 6.6, 7.5, -4, 0.3, 3, 16, 'x', -12);
                m.rod(WHITE_CONCRETE, v(3, 9, 7), v(10, 0.3, 12), 0.5);
                m.rod(WHITE_CONCRETE, v(-3, 9, 7), v(-9, 0.3, 4), 0.5);
            }
            case "giant_mug" -> {
                // a coffee mug, full, cold
                Material[] cols = {WHITE_CONCRETE, RED_CONCRETE, LIGHT_BLUE_CONCRETE, YELLOW_CONCRETE};
                Material body = cols[(int) Long.remainderUnsigned(seed, cols.length)];
                for (int i = 0; i < 4; i++) m.boxC(body, 0, 11, 0, 18, 22, 18, 'y', i * 22.5);
                m.boxC(BROWN_CONCRETE, 0, 21.5, 0, 15.5, 1.1, 15.5, 'y', 0);
                m.boxC(BROWN_CONCRETE, 0, 21.5, 0, 15.5, 1.1, 15.5, 'y', 45);
                m.box(body, 8, 16, -1.4, 6.5, 2.6, 2.8);
                m.box(body, 8, 4.5, -1.4, 6.5, 2.6, 2.8);
                m.box(body, 12, 7, -1.4, 2.6, 9.2, 2.8);
                m.text("<black><bold>WORLD'S BEST", 0, 11, 9.1, 3.0, 0x00000000);
            }
            case "giant_teddy" -> {
                // a teddy bear sitting in the field
                Material fur = BROWN_WOOL, light = WHITE_TERRACOTTA;
                for (int s = -1; s <= 1; s += 2) {
                    m.box(fur, s * 5 - 3, 0, -2, 6, 7, 14);
                    m.box(light, s * 5 - 2.5, 0.5, 12, 5, 6, 0.4);
                    m.boxC(fur, s * 9.8, 11, 2, 4, 12, 4.5, 'x', -35);
                    m.box(fur, s * 6.5 - 2.5, 30, -2.5, 5, 5, 2.5);
                    m.box(light, s * 6.5 - 1.5, 31, 0.05, 3, 3, 0.3);
                    m.box(BLACK_CONCRETE, s * 3.5 - 0.8, 26.5, 6.55, 1.6, 1.6, 0.4);
                }
                m.box(fur, -8, 0, -7, 16, 18, 13);
                m.box(light, -5, 3, 6, 10, 11, 0.5);
                m.box(fur, -8, 18, -6, 16, 13.5, 12.5);
                m.box(light, -3.5, 20, 6.5, 7, 5, 3);
                m.box(BLACK_CONCRETE, -1.3, 23.4, 9.4, 2.6, 1.6, 0.4);
                m.boxC(RED_CONCRETE, -2.2, 17.5, 6.7, 3.6, 3, 1, 'z', 15);
                m.boxC(RED_CONCRETE, 2.2, 17.5, 6.7, 3.6, 3, 1, 'z', -15);
            }
            case "giant_pencil" -> {
                // a pencil standing on its eraser, leaning a little
                m.push().rotate('x', 6).rotate('z', -3);
                hex(m, PINK_CONCRETE, 0, 2.8, 4);
                hex(m, IRON_BLOCK, 4, 3.0, 3.5);
                hex(m, YELLOW_CONCRETE, 7.5, 2.9, 28.5);
                hex(m, BIRCH_PLANKS, 36, 2.2, 3);
                hex(m, BIRCH_PLANKS, 39, 1.3, 3);
                m.box(BLACK_CONCRETE, -0.7, 42, -0.7, 1.4, 2.4, 1.4);
                m.pop();
            }
            case "giant_hydrant" -> {
                // a fire hydrant
                oct(m, RED_CONCRETE, 0, 0, 0, 6, 1.5);
                oct(m, RED_CONCRETE, 0, 1.5, 0, 4.5, 22);
                oct(m, RED_CONCRETE, 0, 23.5, 0, 5.3, 1.5);
                oct(m, RED_CONCRETE, 0, 25, 0, 4.3, 3);
                oct(m, RED_CONCRETE, 0, 28, 0, 2.8, 2.4);
                m.box(GRAY_CONCRETE, -1.1, 30.4, -1.1, 2.2, 2, 2.2);
                for (int s = -1; s <= 1; s += 2) {
                    m.boxC(RED_CONCRETE, s * 5.8, 16, 0, 3, 3.2, 3.2, 'y', 0);
                    m.boxC(GRAY_CONCRETE, s * 7.6, 16, 0, 0.8, 3.8, 3.8, 'y', 0);
                }
                m.boxC(RED_CONCRETE, 0, 13.5, 5.5, 4.4, 4.4, 3, 'y', 0);
                m.boxC(GRAY_CONCRETE, 0, 13.5, 7.3, 5, 5, 0.8, 'y', 0);
            }
            case "giant_bucket" -> {
                // a plastic bucket with its handle down
                for (int i = 0; i < 4; i++) oct(m, BLUE_CONCRETE, 0, i * 6.5, 0, 9.5 + i * 0.6, 6.5);
                oct(m, BLUE_CONCRETE, 0, 26, 0, 12.4, 1.2);
                oct(m, GRAY_CONCRETE, 0, 26.3, 0, 11.2, 1.0);
                double r = 12.6;
                for (int s = -1; s <= 1; s += 2) m.box(BLUE_CONCRETE, s * r - 0.8, 22, -1.5, 1.6, 4, 3);
                m.rod(LIGHT_GRAY_CONCRETE, v(-r, 24, 0), v(-r + 2, 14, 7), 0.6);
                m.rod(LIGHT_GRAY_CONCRETE, v(-r + 2, 14, 7), v(0, 10.5, 9.5), 0.6);
                m.rod(LIGHT_GRAY_CONCRETE, v(0, 10.5, 9.5), v(r - 2, 14, 7), 0.6);
                m.rod(LIGHT_GRAY_CONCRETE, v(r - 2, 14, 7), v(r, 24, 0), 0.6);
            }
            case "giant_lightbulb" -> {
                // a light bulb standing on its screw cap, dead
                for (int i = 0; i < 4; i++) oct(m, i % 2 == 0 ? IRON_BLOCK : LIGHT_GRAY_CONCRETE, 0, i * 1.6, 0, i % 2 == 0 ? 4.2 : 3.9, 1.6);
                oct(m, IRON_BLOCK, 0, 6.4, 0, 4.6, 2.6);
                double[][] layers = {{5.2, 3}, {7.6, 3}, {9.4, 3.5}, {10.2, 4}, {9.6, 3.5}, {7.8, 3}, {4.8, 2.4}};
                double y = 9;
                for (double[] l : layers) {
                    oct(m, WHITE_STAINED_GLASS, 0, y, 0, l[0], l[1]);
                    y += l[1];
                }
                m.rod(BLACK_CONCRETE, v(-1.5, 9, 0), v(-2.5, 19, 0), 0.25);
                m.rod(BLACK_CONCRETE, v(1.5, 9, 0), v(2.5, 19, 0), 0.25);
            }
            default -> {
                return false;
            }
        }
        m.viewRange = FAR;
        return true;
    }

    /** Octagonal prism standing on y0 (two squares rotated 45 degrees), radius r, height h, centred at (cx, cz). */
    private static void oct(Model m, Material mat, double cx, double y0, double cz, double r, double h) {
        double side = r * 2 * 0.92;
        m.boxC(mat, cx, y0 + h / 2, cz, side, h, side, 'y', 0);
        m.boxC(mat, cx, y0 + h / 2, cz, side, h, side, 'y', 45);
    }

    /** Hexagonal prism standing on y0 with side length s (three boxes rotated 60 degrees apart). */
    private static void hex(Model m, Material mat, double y0, double s, double h) {
        for (int i = 0; i < 3; i++) m.boxC(mat, 0, y0 + h / 2, 0, s, h, s * Math.sqrt(3), 'y', i * 60);
    }
}
