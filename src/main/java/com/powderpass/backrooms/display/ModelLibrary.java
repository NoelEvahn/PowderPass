package com.powderpass.backrooms.display;

import com.powderpass.backrooms.util.Rng;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.joml.Vector3f;

import static com.powderpass.backrooms.display.Anim.Type.*;
import static com.powderpass.backrooms.display.Model.v;
import static org.bukkit.Material.*;

/**
 * Every environmental display prop. Conventions: origin = anchor; +Z = front (away from a wall for wall props);
 * floor props stand on y=0; ceiling props hang below y=0; units are blocks.
 */
public final class ModelLibrary {
    private ModelLibrary() {
    }

    private static final Vector3f X = v(1, 0, 0), Y = v(0, 1, 0), Z = v(0, 0, 1), O = v(0, 0, 0);

    private static final String[] MONITOR_LINES = {
            "<#7CFC9A>LOGIN: _", "<#7CFC9A>NO SIGNAL", "<#7CFC9A>WHERE ARE YOU", "<#7CFC9A>ERROR 0x0BACK",
            "<#7CFC9A>SAVING...", "<#7CFC9A>YOU ARE LATE", "<#7CFC9A>FLOOR 4 OF 4", "<#7CFC9A>[ ] [ ] [x]"};
    private static final String[] WHITEBOARD = {
            "<black>Q3 TARGETS<br><dark_green>↑ ↑ ↑", "<dark_red>DO NOT LEAVE<br><black>(the room)", "<blue>WHO MOVED<br>THE EXIT?",
            "<black>MEETING 3:00<br><gray>(always)", "<black>stairs → B<br><red>NOT the window"};
    private static final String[] STENCILS = {"SECTOR 7-G", "AISLE 41", "BAY C", "LOAD ZONE", "STORAGE 1-1", "NO RETURN"};

    public static Model build(String id, long seed) {
        String base = id;
        int param = 0;
        int colon = id.indexOf(':');
        if (colon > 0) {
            base = id.substring(0, colon);
            try {
                param = Integer.parseInt(id.substring(colon + 1));
            } catch (NumberFormatException ignored) {
                // keep 0
            }
        }
        Model m = new Model();
        switch (base) {
            // ---------------------------------------------------------------- Level 0
            case "hanging_fixture" -> {
                m.group(1);
                // chain block models are centred in their cell: offset so the chains sit at x = +-0.45
                m.box(IRON_CHAIN, -0.95, -0.65, -0.5, 1, 0.65, 1);
                m.box(IRON_CHAIN, -0.05, -0.65, -0.5, 1, 0.65, 1);
                m.box(WHITE_CONCRETE, -0.62, -0.78, -0.18, 1.24, 0.12, 0.36);
                m.box(WHITE_CONCRETE, -0.56, -0.83, -0.1, 1.12, 0.06, 0.2).glow(15).group(2).alt(GRAY_CONCRETE.createBlockData());
                m.anim(SWAY, 1, X, O, 3, 90);
                m.anim(SWAY, 2, X, O, 3, 90);
                m.anim(FLICKER, 2, O, O, 0.05, 1);
                m.sound(Sound.BLOCK_BEACON_AMBIENT, 0, -0.8, 0, 120, 0.25, 0.12, 1.9);
            }
            case "chair" -> Shapes.chair(m, BIRCH_PLANKS, BROWN_WOOL);
            case "wall_chair" -> {
                m.push().translate(0, 0.35, -0.15).rotate('x', 28).rotate('z', 12);
                Shapes.chair(m, BIRCH_PLANKS, BROWN_WOOL);
                m.pop();
            }
            case "floating_chair" -> {
                m.group(1);
                m.push().translate(0, -0.5, 0);
                Shapes.chair(m, BIRCH_PLANKS, (seed & 1) == 0 ? BROWN_WOOL : YELLOW_WOOL);
                m.pop();
                m.anim(SPIN, 1, v(0.2, 1, 0.3).normalize(), O, 1, 600);
                m.anim(BOB, 1, Y, O, 0.12, 140);
            }
            case "office_chair" -> {
                m.push().rotate('z', (seed & 3) == 0 ? 80 : 0).translate(0, (seed & 3) == 0 ? 0.3 : 0, 0);
                Shapes.officeChair(m, (seed & 1) == 0 ? GRAY_WOOL : BLACK_WOOL);
                m.pop();
            }
            case "swivel_chair_spinning" -> {
                m.group(0);
                m.boxC(BLACK_CONCRETE, 0, 0.04, 0, 0.62, 0.05, 0.07, 'y', 30);
                m.boxC(BLACK_CONCRETE, 0, 0.04, 0, 0.62, 0.05, 0.07, 'y', -60);
                m.box(IRON_BLOCK, -0.04, 0.05, -0.04, 0.08, 0.38, 0.08);
                m.group(1);
                m.box(GRAY_WOOL, -0.26, 0.43, -0.26, 0.52, 0.09, 0.52);
                m.box(GRAY_WOOL, -0.24, 0.62, -0.33, 0.48, 0.48, 0.07);
                m.anim(SPIN, 1, Y, O, 1, 220);
                m.sound(Sound.BLOCK_WOODEN_TRAPDOOR_OPEN, 0, 0.5, 0, 80, 0.3, 0.2, 0.5);
            }
            case "sunk_desk" -> {
                Shapes.desk(m, STRIPPED_OAK_WOOD, OAK_PLANKS, 1.4, 0.7, 0.8);
                m.box(WHITE_TERRACOTTA, 0.25, 0.8, -0.1, 0.35, 0.25, 0.3);
            }
            case "warped_doorframe" -> {
                int w = Math.max(1, param);
                double h = 3.0;
                m.push().rotate('z', 2.5);
                m.box(DARK_OAK_PLANKS, -0.12, 0, -0.6, 0.12, h, 1.2);
                m.pop();
                m.push().translate(-w, 0, 0).rotate('z', -4);
                m.box(DARK_OAK_PLANKS, 0, 0, -0.6, 0.12, h, 1.2);
                m.pop();
                m.push().translate(0, h, 0).rotate('z', 5).rotate('x', 3);
                m.box(DARK_OAK_PLANKS, -w - 0.1, -0.12, -0.6, w + 0.25, 0.14, 1.2);
                m.pop();
            }
            case "stair_fragment" -> {
                m.group(1);
                for (int i = 0; i < 4; i++) {
                    m.box("minecraft:smooth_sandstone_stairs[facing=north]", -0.5, i * 0.55, -0.5 - i * 0.55, 1, 1, 1);
                }
                m.box("minecraft:smooth_sandstone_stairs[facing=south,half=top]", -0.5, 2.6, -2.2, 1, 1, 1);
                m.anim(SWAY, 1, Z, v(0, 1, -1), 2, 300);
            }
            case "wall_vent" -> {
                m.box(LIGHT_GRAY_CONCRETE, -0.45, -0.3, 0, 0.9, 0.6, 0.04);
                m.box("minecraft:iron_trapdoor[open=true,facing=north]", -0.4, -0.25, 0.03, 0.8, 0.5, 0.25);
                m.emit(Particle.WHITE_ASH, null, 0, 0, 0.2, 2, 0.2, 0.01, 30, 0.4);
                m.sound(Sound.ITEM_ELYTRA_FLYING, 0, 0, 0.2, 200, 0.15, 0.05, 0.5);
            }
            case "fake_exit_sign" -> {
                m.box(WHITE_CONCRETE, -0.36, -0.14, 0, 0.72, 0.28, 0.07);
                m.text("<#ff2b2b><bold>EXIT</bold> <#ff2b2b>→", 0, -0.09, 0.075, 0.55, 0x00000000).glow(15).group(1);
                m.anim(FLICKER, 1, O, O, 0.12, 1);
            }
            case "exit_sign" -> {
                m.box(WHITE_CONCRETE, -0.36, -0.14, 0, 0.72, 0.28, 0.07);
                m.text("<#36ff6b><bold>EXIT</bold>", 0, -0.09, 0.075, 0.6, 0x00000000).glow(15);
            }
            case "wet_floor_sign" -> {
                for (int s = 0; s < 2; s++) {
                    m.push().rotate('y', s * 180).translate(0, 0.78, 0).rotate('x', -14);
                    m.box(YELLOW_CONCRETE, -0.25, -0.78, 0, 0.5, 0.8, 0.03);
                    m.text("<black><bold>CAUTION</bold><br><black>wet floor", 0, -0.55, 0.035, 0.32, 0x00000000);
                    m.pop();
                }
            }
            case "dangling_tile" -> {
                m.group(1);
                m.push().translate(0, -0.02, -0.5).rotate('x', 62);
                m.box(END_STONE_BRICKS, -0.5, -0.08, 0, 1, 0.08, 1);
                m.pop();
                m.rod(BLACK_CONCRETE, v(0.3, 0, 0.1), v(0.35, -1.3, 0.2), 0.03);
                m.anim(SWAY, 1, X, v(0, 0, -0.5), 4, 110);
            }
            case "wall_clock" -> {
                Shapes.disc(m, BLACK_CONCRETE, 0.33, 0.015, 0.03);
                Shapes.disc(m, WHITE_CONCRETE, 0.28, 0.025, 0.03);
                m.group(1).box(BLACK_CONCRETE, -0.015, 0, 0.04, 0.03, 0.24, 0.01);
                m.group(2).box(BLACK_CONCRETE, -0.022, 0, 0.045, 0.044, 0.15, 0.01);
                m.anim(SPIN, 1, Z, O, -1, 160);
                m.anim(SPIN, 2, Z, O, -1, 1920);
                m.sound(Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0, 0, 0.1, 20, 1.0, 0.25, 0.4);
            }
            case "sign_text" -> {
                Shapes.plate(m, GRAY_CONCRETE, 1.1, 0.4, "<white><bold>STAIRWELL</bold> <red>▼", 0.5);
                m.box(REDSTONE_BLOCK, -0.05, 0.25, 0.0, 0.1, 0.1, 0.1).glow(15).alt(BLACK_CONCRETE.createBlockData()).group(1);
                m.anim(BLINK, 1, O, O, 0.5, 1);
            }
            case "peeled_wallpaper" -> {
                for (int i = 0; i < 4; i++) {
                    double x = -1.2 + i * 0.75;
                    m.push().translate(x, 2.2 - (i % 2) * 0.4, 0).rotate('x', -25 - i * 12).rotate('z', (i - 1.5) * 6);
                    m.box(SMOOTH_SANDSTONE, -0.18, -1.0 + i * 0.1, 0, 0.36, 1.0 - i * 0.1, 0.02);
                    m.pop();
                }
            }
            case "drip_emitter" -> {
                m.emit(Particle.DRIPPING_WATER, null, 0, -0.05, 0, 1, 0.3, 0, 8, 0.7);
                m.sound(Sound.BLOCK_POINTED_DRIPSTONE_DRIP_WATER, 0, -2, 0, 30, 0.5, 0.5, 0.8);
            }
            case "graffiti" -> m.text("<#4a3520><italic>it is cold here.<br>crouch into the tiles</italic>", 0, 0, 0.02, 0.35, 0x00000000);
            case "phone_desk" -> {
                Shapes.desk(m, DARK_OAK_PLANKS, DARK_OAK_PLANKS, 0.9, 0.6, 0.78);
                m.box(BLACK_CONCRETE, -0.15, 0.78, -0.12, 0.3, 0.1, 0.22);
                m.box(BLACK_CONCRETE, -0.17, 0.88, -0.05, 0.34, 0.06, 0.08).group(1);
                m.boxC(WHITE_CONCRETE, 0, 0.885, 0.1, 0.12, 0.012, 0.12, 'y', 45);
                m.hitbox("phone", 0, 0.7, 0, 0.6, 0.5);
                m.anim(JITTER, 1, O, O, 0.004, 1);
                m.behavior("phone");
            }
            // ---------------------------------------------------------------- Poolrooms
            case "caustics" -> {
                for (int i = 0; i < 5; i++) {
                    long h = Rng.hash(seed, i);
                    m.group(i + 1);
                    double x = Rng.unit(h) * 6 - 3, z = Rng.unit(Rng.hash(h, 1)) * 6 - 3;
                    m.boxC(LIGHT_BLUE_STAINED_GLASS, x, -0.03, z, 1.4 + Rng.unit(Rng.hash(h, 2)), 0.01, 0.6 + Rng.unit(Rng.hash(h, 3)), 'y',
                            Rng.pick(Rng.hash(h, 4), 180)).glow(13);
                    m.anim(ORBIT, i + 1, Y, O, 0.5 + Rng.unit(Rng.hash(h, 5)) * 0.6, 120 + Rng.pick(Rng.hash(h, 6), 120), Rng.unit(Rng.hash(h, 7)));
                }
            }
            case "drain_vortex" -> {
                m.box("minecraft:iron_trapdoor[half=bottom,facing=north]", -1.0, 0.0, -1.0, 2, 0.6, 2);
                m.group(1);
                for (int i = 0; i < 6; i++) {
                    m.push().rotate('y', i * 60).translate(1.0, 0.3, 0).rotate('z', 30);
                    m.box(DARK_PRISMARINE, -0.05, 0, -0.25, 0.1, 0.6, 0.5);
                    m.pop();
                }
                m.anim(SPIN, 1, Y, O, -1, 30);
                m.emit(Particle.BUBBLE, null, 0, 0.5, 0, 6, 0.8, 0.05, 6, 1.0);
                m.emit(Particle.NAUTILUS, null, 0, 2.5, 0, 4, 1.0, 0.1, 10, 0.8);
                m.sound(Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, 0, 1, 0, 40, 0.8, 0.6, 0.7);
            }
            case "staff_sign" -> Shapes.plate(m, GRAY_CONCRETE, 1.0, 0.32, "<white><bold>STAFF ONLY", 0.45);
            case "sunbeam" -> {
                for (int i = 0; i < 3; i++) {
                    m.group(i + 1);
                    m.push().translate(-0.7 + i * 0.7, 0, 0.6).rotate('x', -20);
                    m.box(YELLOW_STAINED_GLASS, -0.25, 0, -0.02, 0.5, 4.0, 0.04).glow(15);
                    m.pop();
                    m.anim(BOB, i + 1, X, O, 0.05, 80 + i * 17);
                }
                m.emit(Particle.END_ROD, null, 0, 1.5, 0.4, 1, 1.0, 0.0, 10, 0.6);
                m.sound(Sound.ENTITY_PARROT_AMBIENT, 0, 1.5, -1, 80, 0.5, 0.25, 1.3);
            }
            case "water_slide" -> {
                for (int i = 0; i < 6; i++) {
                    double z0 = i * 0.62, y0 = -i * 0.72;
                    m.push().translate(0, y0, z0).rotate('x', 49);
                    m.box(WHITE_CONCRETE, -0.45, -0.05, 0, 0.9, 0.06, 0.98);
                    m.box(LIGHT_BLUE_CONCRETE, -0.5, -0.05, 0, 0.06, 0.35, 0.98);
                    m.box(LIGHT_BLUE_CONCRETE, 0.44, -0.05, 0, 0.06, 0.35, 0.98);
                    m.pop();
                }
                m.emit(Particle.SPLASH, null, 0, -3.8, 3.6, 3, 0.4, 0.1, 12, 0.6);
            }
            case "lifeguard_chair" -> {
                for (double x : new double[]{-0.35, 0.3}) for (double z : new double[]{-0.35, 0.3}) m.box(WHITE_CONCRETE, x, 0, z, 0.07, 1.9, 0.07);
                for (int i = 1; i <= 3; i++) m.box(WHITE_CONCRETE, -0.35, i * 0.48, 0.3, 0.72, 0.05, 0.07);
                m.box(WHITE_CONCRETE, -0.4, 1.9, -0.4, 0.8, 0.08, 0.8);
                m.box(RED_CONCRETE, -0.4, 1.98, -0.42, 0.8, 0.6, 0.06);
                m.text("<red><bold>+", 0, 2.12, -0.355, 0.9, 0x00000000);
                m.boxC(WHITE_CONCRETE, 0, 3.0, 0, 1.6, 0.05, 1.6, 'y', 45);
                m.box(WHITE_CONCRETE, -0.03, 1.98, -0.03, 0.06, 1.0, 0.06);
            }
            case "pool_railing" -> {
                for (double x : new double[]{-0.4, 0.4}) {
                    m.rod(IRON_BLOCK, v(x, 0, -0.6), v(x, 0.95, -0.6), 0.06);
                    m.rod(IRON_BLOCK, v(x, 0.95, -0.6), v(x, 0.95, -0.1), 0.06);
                    m.rod(IRON_BLOCK, v(x, 0.95, -0.1), v(x, 0.2, 0.35), 0.06);
                    m.rod(IRON_BLOCK, v(x, 0.2, 0.35), v(x, -0.6, 0.4), 0.06);
                }
            }
            case "floating_chair_pool" -> {
                m.group(1);
                m.box(WHITE_CONCRETE, -0.3, 0, -0.8, 0.6, 0.08, 1.2);
                m.push().translate(0, 0.04, -0.8).rotate('x', -35);
                m.box(WHITE_CONCRETE, -0.3, 0, -0.55, 0.6, 0.06, 0.55);
                m.pop();
                m.anim(BOB, 1, Y, O, 0.05, 50);
                m.anim(SPIN, 1, Y, O, 1, 1600);
            }
            case "submerged_bench", "pool_bench" -> {
                m.box(SMOOTH_QUARTZ, -0.9, 0.4, -0.2, 1.8, 0.1, 0.42);
                m.box(SMOOTH_QUARTZ, -0.8, 0, -0.15, 0.15, 0.4, 0.32);
                m.box(SMOOTH_QUARTZ, 0.65, 0, -0.15, 0.15, 0.4, 0.32);
                if (base.equals("submerged_bench")) m.box(LIGHT_BLUE_CONCRETE, -0.9, 0.5, -0.2, 1.8, 0.02, 0.42);
            }
            case "drain_grate" -> {
                m.box("minecraft:iron_trapdoor[half=bottom,facing=north]", -0.5, 0, -0.5, 1, 0.1, 1);
                m.box(BLACK_CONCRETE, -0.45, -0.01, -0.45, 0.9, 0.01, 0.9);
            }
            case "lifebuoy" -> {
                for (int i = 0; i < 8; i++) {
                    m.push().rotate('z', i * 45).box(i % 2 == 0 ? RED_CONCRETE : WHITE_CONCRETE, -0.11, 0.24, 0.02, 0.22, 0.1, 0.1).pop();
                }
                m.box(IRON_BLOCK, -0.03, 0.3, 0, 0.06, 0.12, 0.08);
            }
            case "impossible_stairs" -> {
                for (int i = 0; i < 5; i++) {
                    boolean flip = i >= 3;
                    m.push().translate(0, i * 0.5, -i * 0.5).rotate('z', flip ? 180 : 0);
                    m.box(flip ? "minecraft:quartz_stairs[facing=south,half=top]" : "minecraft:quartz_stairs[facing=north]", -0.5, 0, -0.5, 1, 1, 1);
                    m.pop();
                }
            }
            case "curved_tile" -> {
                for (int i = 0; i < 4; i++) {
                    m.push().rotate('y', 180 + i * 22.5).translate(0, 0, 0).rotate('x', 45);
                    m.box(LIGHT_BLUE_CONCRETE, -0.12, 0, 0, 0.24, 0.3, 0.02);
                    m.pop();
                }
                m.push().translate(-2, 0, 0).rotate('x', 45);
                m.box(WHITE_CONCRETE, 0, 0, -0.02, 2, 0.32, 0.02);
                m.pop();
                m.push().translate(0, 0, -2).rotate('y', -90).rotate('x', 45);
                m.box(WHITE_CONCRETE, 0, 0, -0.02, 2, 0.32, 0.02);
                m.pop();
            }
            // ---------------------------------------------------------------- Level 1
            case "maintenance_sign" -> {
                Shapes.plate(m, YELLOW_CONCRETE, 1.3, 0.45, "<black><bold>MAINTENANCE ▼", 0.45);
                for (int i = 0; i < 6; i++) m.box(BLACK_CONCRETE, -0.62 + i * 0.22, 0.2, 0.051, 0.1, 0.03, 0.01);
            }
            case "noclip_shimmer" -> {
                for (int i = 0; i < 4; i++) {
                    m.group(i + 1);
                    m.box(SMOOTH_SANDSTONE, -0.5 + i * 0.3, 0.1 + i * 0.4, -0.55, 0.04, 0.8, 0.04).glow(12);
                    m.anim(JITTER, i + 1, O, O, 0.05, 1);
                }
                m.emit(Particle.WHITE_ASH, null, 0, 1, 0, 6, 0.5, 0.02, 10, 1);
                m.sound(Sound.BLOCK_BEACON_AMBIENT, 0, 1, 0, 60, 0.6, 0.4, 1.6);
            }
            case "elevator_doors", "freight_doors" -> {
                double half = base.equals("freight_doors") ? 1.5 : 0.5;
                double hgt = base.equals("freight_doors") ? 3.0 : 2.0;
                Material door = base.equals("freight_doors") ? IRON_BLOCK : WAXED_CUT_COPPER;
                m.group(1).box(door, -half, 0, -0.03, half, hgt, 0.06);
                m.group(2).box(door, 0, 0, -0.03, half, hgt, 0.06);
                m.group(0).box(BLACK_CONCRETE, -0.01, 0, -0.035, 0.02, hgt, 0.07);
                m.behavior("elevator");
            }
            case "elevator_panel" -> {
                m.box(IRON_BLOCK, -0.12, -0.3, 0, 0.24, 0.6, 0.04);
                m.box("minecraft:stone_button[face=wall,facing=south]", -0.05, 0.02, 0.03, 0.1, 0.1, 0.05).glow(10);
                m.box("minecraft:stone_button[face=wall,facing=south]", -0.05, -0.15, 0.03, 0.1, 0.1, 0.05);
                m.text("<#ffb347>4", 0, 0.12, 0.045, 0.45, 0x00000000).glow(15);
            }
            case "supply_sign" -> Shapes.plate(m, GRAY_CONCRETE, 1.4, 0.36, "<#ffd23f><bold>SUPPLIES</bold> <white>→", 0.45);
            case "bench" -> {
                m.box(SPRUCE_PLANKS, -1.0, 0.42, -0.25, 2.0, 0.08, 0.5);
                m.box(SPRUCE_PLANKS, -1.0, 0.55, -0.3, 2.0, 0.35, 0.06);
                m.box(IRON_BLOCK, -0.9, 0, -0.2, 0.06, 0.42, 0.4);
                m.box(IRON_BLOCK, 0.84, 0, -0.2, 0.06, 0.42, 0.4);
            }
            case "radio" -> {
                m.box(DARK_OAK_PLANKS, -0.2, 0, -0.1, 0.4, 0.25, 0.2);
                m.box(BLACK_CONCRETE, -0.17, 0.05, 0.1, 0.2, 0.15, 0.01);
                m.boxC(GOLD_BLOCK, 0.11, 0.14, 0.105, 0.06, 0.06, 0.02, 'z', 45);
                m.rod(IRON_BLOCK, v(0.15, 0.25, 0), v(0.3, 0.75, -0.1), 0.015);
                m.hitbox("radio", 0, 0, 0, 0.5, 0.35);
                m.sound(Sound.BLOCK_FIRE_AMBIENT, 0, 0.1, 0, 60, 0.6, 0.3, 1.8);
            }
            case "chalk_safe" -> m.text("<white><italic>SAFE</italic><br><gray><italic>they don't come in here</italic>", 0, -0.2, 0.01, 0.5, 0x00000000);
            case "forklift" -> {
                m.box(YELLOW_CONCRETE, -0.5, 0.25, -0.8, 1.0, 0.7, 1.4);
                m.box(BLACK_CONCRETE, -0.45, 0.95, -0.7, 0.9, 0.3, 0.6);
                for (double x : new double[]{-0.45, 0.4}) {
                    m.rod(BLACK_CONCRETE, v(x, 0.95, -0.15), v(x, 2.1, -0.15), 0.05);
                    m.rod(BLACK_CONCRETE, v(x, 0.95, -0.75), v(x, 2.1, -0.75), 0.05);
                }
                m.box(BLACK_CONCRETE, -0.47, 2.1, -0.78, 0.95, 0.05, 0.68);
                m.box(GRAY_CONCRETE, -0.45, 0, 0.6, 0.1, 2.4, 0.1);
                m.box(GRAY_CONCRETE, 0.35, 0, 0.6, 0.1, 2.4, 0.1);
                m.box(IRON_BLOCK, -0.4, 0.1, 0.7, 0.12, 0.05, 1.0);
                m.box(IRON_BLOCK, 0.28, 0.1, 0.7, 0.12, 0.05, 1.0);
                for (double x : new double[]{-0.55, 0.45}) for (double z : new double[]{-0.6, 0.35}) m.boxC(BLACK_CONCRETE, x + 0.05, 0.22, z, 0.1, 0.44, 0.44, 'x', 45);
            }
            case "cage_lamp" -> {
                m.group(1);
                m.box(BLACK_CONCRETE, -0.02, -0.7, -0.02, 0.04, 0.7, 0.04);
                m.box(GRAY_CONCRETE, -0.22, -0.8, -0.22, 0.44, 0.1, 0.44);
                m.box(SHROOMLIGHT, -0.09, -0.98, -0.09, 0.18, 0.18, 0.18).glow(15).group(2).alt(BROWN_CONCRETE.createBlockData());
                m.group(1);
                for (int i = 0; i < 4; i++) m.push().rotate('y', i * 90).box(BLACK_CONCRETE, -0.01, -1.05, 0.15, 0.02, 0.25, 0.02).pop();
                m.anim(SWAY, 1, Z, O, 2, 130);
                m.anim(SWAY, 2, Z, O, 2, 130);
                m.anim(FLICKER, 2, O, O, 0.04, 1);
            }
            case "ceiling_fan" -> {
                m.box(GRAY_CONCRETE, -0.04, -0.5, -0.04, 0.08, 0.5, 0.08);
                m.box(IRON_BLOCK, -0.18, -0.68, -0.18, 0.36, 0.2, 0.36);
                m.group(1);
                for (int i = 0; i < 4; i++) {
                    m.push().rotate('y', i * 90).translate(0, -0.6, 0).rotate('z', 12);
                    m.box(GRAY_CONCRETE, 0.15, -0.02, -0.12, 0.9, 0.03, 0.24);
                    m.pop();
                }
                m.anim(SPIN, 1, Y, O, 1, 24);
                m.sound(Sound.ITEM_ELYTRA_FLYING, 0, -0.6, 0, 100, 0.4, 0.06, 1.4);
            }
            case "ceiling_pipes" -> {
                int len = 12;
                m.box(GRAY_CONCRETE, -0.6, -0.2, 0, 0.35, 0.35, len);
                m.box(WAXED_EXPOSED_COPPER, -0.15, -0.1, 0, 0.22, 0.22, len);
                m.box(POLISHED_ANDESITE, 0.2, -0.25, 0, 0.3, 0.3, len);
                for (int z = 1; z < len; z += 3) m.box(BLACK_CONCRETE, -0.65, 0.1, z, 1.2, 0.05, 0.08);
            }
            case "fuse_box" -> {
                m.box(GRAY_CONCRETE, -0.3, -0.4, 0, 0.6, 0.8, 0.18);
                m.push().translate(-0.3, 0, 0.18).rotate('y', -35);
                m.box(LIGHT_GRAY_CONCRETE, 0, -0.39, 0, 0.58, 0.78, 0.03);
                m.pop();
                for (int i = 0; i < 4; i++) m.box(i == 2 ? REDSTONE_BLOCK : BLACK_CONCRETE, -0.2 + i * 0.11, 0.1, 0.17, 0.05, 0.12, 0.03).glow(i == 2 ? 15 : 4);
                m.emit(Particle.ELECTRIC_SPARK, null, 0, 0.1, 0.25, 3, 0.1, 0.1, 40, 0.3);
                m.sound(Sound.BLOCK_BEACON_AMBIENT, 0, 0, 0.2, 80, 0.4, 0.15, 2.0);
            }
            case "stencil_sign" -> m.text("<#e8c547><bold>" + STENCILS[Rng.pick(seed, STENCILS.length)], 0, -0.25, 0.02, 0.9, 0x00000000);
            case "pallet_stack" -> {
                m.box(SPRUCE_PLANKS, -0.5, 0, -0.5, 1, 0.15, 1);
                m.box(BROWN_TERRACOTTA, -0.45, 0.15, -0.45, 0.5, 0.45, 0.5);
                m.box(BROWN_TERRACOTTA, 0.05, 0.15, -0.4, 0.42, 0.4, 0.45);
                m.box(STRIPPED_OAK_WOOD, -0.4, 0.6, -0.35, 0.45, 0.35, 0.4);
                m.boxC(BROWN_TERRACOTTA, 0.7, 0.2, 0.5, 0.4, 0.4, 0.4, 'y', 33);
            }
            // ---------------------------------------------------------------- Level 2
            case "hatch_sign" -> Shapes.plate(m, YELLOW_CONCRETE, 1.2, 0.4, "<black><bold>HATCH ▼ L-3</bold>", 0.42);
            case "daylight_glow" -> {
                m.box(WHITE_STAINED_GLASS, -1.4, 2.5, -0.5, 2.8, 0.02, 1.0).glow(15);
                m.emit(Particle.END_ROD, null, 0, 2.0, 0, 1, 0.8, 0, 12, 0.6);
                m.sound(Sound.ITEM_ELYTRA_FLYING, 0, 2, 0, 160, 0.4, 0.08, 0.6);
            }
            case "boiler" -> {
                for (int i = 0; i < 2; i++) m.boxC(WAXED_EXPOSED_COPPER, 0, 1.2, 0, 1.6, 2.4, 1.6, 'y', i * 45);
                m.boxC(WAXED_COPPER_BLOCK, 0, 2.45, 0, 1.2, 0.1, 1.2, 'y', 22.5);
                m.rod(WAXED_EXPOSED_COPPER, v(0, 2.4, 0), v(0, 4.0, 0), 0.3);
                m.group(1);
                m.push().translate(0, 1.3, 0.85);
                Shapes.ring(m, RED_CONCRETE, 0.22, 0.05, 0.05);
                m.box(RED_CONCRETE, -0.02, -0.2, -0.02, 0.04, 0.4, 0.04);
                m.pop();
                m.anim(SPIN, 1, Z, v(0, 1.3, 0.85), 1, 900);
                m.emit(Particle.CLOUD, null, 0, 2.6, 0, 2, 0.4, 0.02, 8, 0.6);
                m.emit(Particle.LAVA, null, 0, 0.2, 0.9, 1, 0.3, 0, 40, 0.2);
                m.sound(Sound.BLOCK_FURNACE_FIRE_CRACKLE, 0, 1, 0, 30, 0.7, 0.6, 0.6);
                m.hazard("heat", 2.2, 1.0, 40, 0, 0, 1, 0);
            }
            case "steam_valve" -> {
                m.box(POLISHED_BASALT, -0.12, -0.12, 0, 0.24, 0.24, 0.35);
                m.group(1);
                m.push().translate(0, 0, 0.36);
                Shapes.ring(m, RED_CONCRETE, 0.18, 0.04, 0.04);
                m.box(RED_CONCRETE, -0.015, -0.18, -0.02, 0.03, 0.36, 0.04);
                m.box(RED_CONCRETE, -0.18, -0.015, -0.02, 0.36, 0.03, 0.04);
                m.pop();
                m.anim(SWAY, 1, Z, v(0, 0, 0.36), 40, 200);
                m.emit(Particle.CLOUD, null, 0.15, 0.1, 0.3, 2, 0.05, 0.03, 20, 0.35);
                m.sound(Sound.BLOCK_FIRE_EXTINGUISH, 0, 0, 0.3, 80, 0.3, 0.25, 1.4);
            }
            case "steam_vent_hazard" -> {
                m.box("minecraft:iron_trapdoor[half=bottom,facing=north]", -0.5, -0.12, -0.5, 1, 0.13, 1);
                m.emit(Particle.WHITE_ASH, null, 0, 0.2, 0, 2, 0.3, 0.01, 20, 0.5);
                m.hazard("steam", 2.0, 3.0, 160, 30, 0, 0.1, 0);
            }
            case "pipe_cluster" -> {
                m.box(POLISHED_BASALT, -0.5, 0, -4, 0.35, 0.35, 8);
                m.box(WAXED_EXPOSED_COPPER, 0.0, 0.1, -4, 0.28, 0.28, 8);
                m.box(GRAY_CONCRETE, 0.35, -0.05, -4, 0.25, 0.25, 8);
                m.box(POLISHED_BASALT, -0.5, 0.35, 1.0, 0.35, 1.6, 0.35);
            }
            case "gauge" -> {
                Shapes.disc(m, IRON_BLOCK, 0.18, 0.02, 0.04);
                m.boxC(WHITE_CONCRETE, 0, 0, 0.03, 0.24, 0.24, 0.02, 'z', 22.5);
                m.group(1).box(RED_CONCRETE, -0.008, 0, 0.035, 0.016, 0.13, 0.008);
                m.anim(SWAY, 1, Z, O, 55, 37);
                m.anim(JITTER, 1, O, O, 0.003, 1);
            }
            case "vent_fan" -> {
                m.box(GRAY_CONCRETE, -0.5, -0.5, 0, 1, 0.08, 0.1);
                m.box(GRAY_CONCRETE, -0.5, 0.42, 0, 1, 0.08, 0.1);
                m.box(GRAY_CONCRETE, -0.5, -0.5, 0, 0.08, 1, 0.1);
                m.box(GRAY_CONCRETE, 0.42, -0.5, 0, 0.08, 1, 0.1);
                m.group(1);
                for (int i = 0; i < 4; i++) m.push().rotate('z', i * 90).rotate('y', 20).box(LIGHT_GRAY_CONCRETE, -0.07, 0.04, -0.01, 0.14, 0.38, 0.02).pop();
                m.anim(SPIN, 1, Z, O, 1, 16);
                m.sound(Sound.ITEM_ELYTRA_FLYING, 0, 0, 0.3, 80, 0.4, 0.06, 1.8);
            }
            // ---------------------------------------------------------------- Level 3
            case "lift_sign" -> Shapes.plate(m, IRON_BLOCK, 1.0, 0.3, "<black><bold>OFFICES ▲", 0.42);
            case "switchboard" -> {
                m.box(GRAY_CONCRETE, -1.4, 0, -0.02, 2.8, 1.5, 0.08);
                for (int r = 0; r < 2; r++) {
                    for (int c = 0; c < 6; c++) {
                        boolean red = Rng.chance(Rng.hash(seed, r, c), 0.25);
                        m.group(1 + (r * 6 + c) % 4);
                        m.box(red ? REDSTONE_BLOCK : LIME_CONCRETE, -1.1 + c * 0.42, 1.0 + r * 0.22, 0.06, 0.08, 0.08, 0.03).glow(15)
                                .alt(BLACK_CONCRETE.createBlockData());
                    }
                }
                m.group(0);
                for (int c = 0; c < 3; c++) m.box("minecraft:lever[face=wall,facing=south]", -0.9 + c * 0.8, 0.35, 0.06, 0.25, 0.25, 0.2);
                for (int g = 1; g <= 4; g++) m.anim(BLINK, g, O, O, 0.18, 1);
                m.sound(Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0, 0.8, 0.2, 30, 0.4, 0.25, 1.6);
            }
            case "crt_monitor" -> {
                m.box(WHITE_TERRACOTTA, -0.24, 0.02, -0.25, 0.48, 0.42, 0.42);
                m.box(WHITE_TERRACOTTA, -0.15, 0, -0.15, 0.3, 0.03, 0.25);
                m.box(CYAN_TERRACOTTA, -0.2, 0.07, 0.17, 0.4, 0.32, 0.01).glow(11).group(1).alt(BLACK_CONCRETE.createBlockData());
                m.text(MONITOR_LINES[Rng.pick(seed, MONITOR_LINES.length)], 0, 0.17, 0.185, 0.22, 0x00000000).glow(15).named("screen_text");
                m.box(WHITE_TERRACOTTA, -0.22, 0, 0.25, 0.44, 0.03, 0.15);
                m.anim(FLICKER, 1, O, O, 0.08, 1);
                m.behavior("monitor");
            }
            case "turbine" -> {
                m.box(GRAY_CONCRETE, -1.0, 0, -0.5, 2.0, 0.4, 1.0);
                m.push().translate(0, 1.8, 0);
                Shapes.ring(m, IRON_BLOCK, 1.4, 0.15, 0.5);
                m.pop();
                m.group(1);
                for (int i = 0; i < 6; i++) m.push().translate(0, 1.8, 0).rotate('z', i * 60).rotate('y', 25).box(LIGHT_GRAY_CONCRETE, -0.12, 0.1, -0.02, 0.24, 1.2, 0.04).pop();
                m.box(IRON_BLOCK, -0.15, 1.65, -0.15, 0.3, 0.3, 0.3);
                m.anim(SPIN, 1, Z, v(0, 1.8, 0), 1, 20);
                m.sound(Sound.BLOCK_BEACON_AMBIENT, 0, 1.5, 0, 60, 0.8, 0.5, 0.5);
            }
            case "generator" -> {
                m.group(1);
                m.box(IRON_BLOCK, -1.2, 0, -0.7, 2.4, 1.1, 1.4);
                for (int i = 0; i < 7; i++) m.box(GRAY_CONCRETE, -1.1 + i * 0.33, 1.1, -0.6, 0.06, 0.25, 1.2);
                m.box(YELLOW_CONCRETE, -1.21, 0.2, -0.3, 0.02, 0.6, 0.6);
                m.box(LIME_CONCRETE, 1.19, 0.8, -0.1, 0.03, 0.12, 0.12).glow(15).alt(GREEN_CONCRETE.createBlockData()).group(2);
                m.anim(JITTER, 1, O, O, 0.012, 1);
                m.anim(JITTER, 2, O, O, 0.012, 1);
                m.anim(BLINK, 2, O, O, 0.3, 1);
                m.emit(Particle.ELECTRIC_SPARK, null, 0, 1.3, 0, 3, 0.6, 0.1, 30, 0.4);
                m.sound(Sound.BLOCK_BEACON_AMBIENT, 0, 0.6, 0, 40, 1.0, 0.8, 0.6);
            }
            case "wire_rack" -> {
                Material[] cols = {BLACK_CONCRETE, RED_CONCRETE, YELLOW_CONCRETE};
                for (int c = 0; c < 2; c++) {
                    double x = -0.2 + c * 0.4;
                    double sag = 0.45 + c * 0.2;
                    Vector3f prev = v(x, 0, 0);
                    for (int i = 1; i <= 4; i++) {
                        double z = i * 2.5;
                        double y = -sag * Math.sin(Math.PI * i / 4.0);
                        Vector3f nxt = v(x, y, z);
                        m.rod(cols[c], prev, nxt, 0.05);
                        prev = nxt;
                    }
                }
            }
            case "transformer" -> {
                m.box(GRAY_CONCRETE, -0.6, -1.0, -0.6, 1.2, 1.4, 1.2);
                for (int i = 0; i < 3; i++) {
                    double x = -0.4 + i * 0.4;
                    m.box(WHITE_TERRACOTTA, x - 0.07, 0.4, -0.07, 0.14, 0.55, 0.14);
                }
                for (int i = 0; i < 3; i++) {
                    m.group(1 + i);
                    m.rod(i == 1 ? YELLOW_CONCRETE : LIGHT_BLUE_CONCRETE, v(-0.4 + i * 0.4, 1.0, 0), v(-0.2 + i * 0.3, 1.5, 0.2), 0.03)
                            .glow(15).alt(AIR.createBlockData());
                    m.anim(BLINK, 1 + i, O, O, 0.5, 1);
                }
                m.emit(Particle.ELECTRIC_SPARK, null, 0, 1.2, 0, 5, 0.4, 0.2, 8, 0.8);
                m.sound(Sound.BLOCK_BEACON_AMBIENT, 0, 0.5, 0, 40, 1.0, 0.7, 1.7);
                m.hazard("shock", 2.4, 4.0, 100, 20, 0, 0.5, 0);
            }
            case "danger_sign" -> {
                Shapes.plate(m, YELLOW_CONCRETE, 0.9, 0.55, "<black><bold>DANGER</bold><br><black>HIGH VOLTAGE", 0.32);
                m.box(BLACK_CONCRETE, -0.45, 0.22, 0.051, 0.9, 0.05, 0.01);
            }
            case "ladder_cage" -> {
                for (int k = 0; k < 3; k++) {
                    double y = 2.2 + k * 3.0;
                    for (int i = 0; i < 3; i++) {
                        double a0 = Math.PI * i / 3, a1 = Math.PI * (i + 1) / 3;
                        m.rod(IRON_BLOCK, v(Math.cos(a0) * 0.5, y, Math.sin(a0) * 0.6 + 0.0), v(Math.cos(a1) * 0.5, y, Math.sin(a1) * 0.6), 0.04);
                    }
                }
                for (double a : new double[]{0.25, 0.75}) {
                    double ang = Math.PI * a;
                    m.rod(IRON_BLOCK, v(Math.cos(ang) * 0.5, 2.2, Math.sin(ang) * 0.6), v(Math.cos(ang) * 0.5, 8.2, Math.sin(ang) * 0.6), 0.04);
                }
            }
            case "cable_tray" -> {
                m.box(IRON_BLOCK, -0.4, -0.05, 0, 0.8, 0.04, 10);
                m.box(BLACK_CONCRETE, -0.3, -0.01, 0, 0.2, 0.08, 10);
                m.box(RED_CONCRETE, -0.05, -0.01, 0, 0.12, 0.07, 10);
                m.box(YELLOW_CONCRETE, 0.12, -0.01, 0, 0.16, 0.06, 10);
            }
            case "piston_pump" -> {
                m.box(SMOOTH_STONE, -0.45, 0, -0.45, 0.9, 0.3, 0.9);
                m.box(IRON_BLOCK, -0.3, 0.3, -0.3, 0.6, 0.9, 0.6);
                m.group(1);
                m.box(IRON_BLOCK, -0.06, 1.2, -0.06, 0.12, 0.8, 0.12);
                m.box("minecraft:piston[facing=up]", -0.3, 1.9, -0.3, 0.6, 0.35, 0.6);
                m.anim(SLIDE, 1, Y, O, 0.5, 40);
                m.sound(Sound.BLOCK_PISTON_EXTEND, 0, 1.5, 0, 40, 1.0, 0.15, 0.6);
            }
            case "vibrating_motor" -> {
                m.group(1);
                m.box(WAXED_EXPOSED_CUT_COPPER, -0.35, 0.1, -0.3, 0.7, 0.55, 0.6);
                for (int i = 0; i < 5; i++) m.box(GRAY_CONCRETE, -0.36, 0.15 + i * 0.1, -0.31, 0.72, 0.03, 0.62);
                m.box(BLACK_CONCRETE, -0.4, 0, -0.35, 0.8, 0.1, 0.7);
                m.group(2);
                m.box(IRON_BLOCK, -0.05, 0.33, 0.3, 0.1, 0.1, 0.35);
                m.anim(JITTER, 1, O, O, 0.015, 1);
                m.anim(SPIN, 2, Z, v(0, 0.38, 0), 1, 8);
                m.sound(Sound.BLOCK_BEACON_AMBIENT, 0, 0.4, 0, 50, 0.6, 0.3, 1.3);
            }
            // ---------------------------------------------------------------- Level 4
            case "floor_indicator" -> {
                m.box(GOLD_BLOCK, -0.4, -0.12, 0, 0.8, 0.3, 0.04);
                m.text("<#ffb347><bold>▲ 5", 0, -0.08, 0.045, 0.5, 0xFF101010).glow(15);
            }
            case "basement_sign" -> Shapes.plate(m, GRAY_CONCRETE, 1.3, 0.4, "<white><bold>B ▼ ELECTRICAL", 0.42);
            case "armchair" -> {
                Material fab = (seed & 1) == 0 ? RED_WOOL : GREEN_WOOL;
                for (double x : new double[]{-0.38, 0.32}) for (double z : new double[]{-0.35, 0.29}) m.box(DARK_OAK_PLANKS, x, 0, z, 0.06, 0.15, 0.06);
                m.box(fab, -0.42, 0.15, -0.4, 0.84, 0.3, 0.75);
                m.box(fab, -0.42, 0.45, -0.42, 0.84, 0.62, 0.18);
                m.box(fab, -0.45, 0.45, -0.38, 0.14, 0.25, 0.7);
                m.box(fab, 0.31, 0.45, -0.38, 0.14, 0.25, 0.7);
            }
            case "potted_plant" -> {
                m.box(TERRACOTTA, -0.22, 0, -0.22, 0.44, 0.42, 0.44);
                m.box(PODZOL, -0.18, 0.38, -0.18, 0.36, 0.05, 0.36);
                m.box(STRIPPED_JUNGLE_LOG, -0.03, 0.4, -0.03, 0.06, 0.8, 0.06);
                m.group(1);
                for (int i = 0; i < 4; i++) m.boxC(OAK_LEAVES, Math.cos(i * 1.6) * 0.18, 1.1 + i * 0.12, Math.sin(i * 1.6) * 0.18, 0.45, 0.4, 0.45, 'y', i * 25);
                m.anim(SWAY, 1, X, v(0, 0.4, 0), 1.5, 160);
            }
            case "whiteboard" -> {
                m.box(LIGHT_GRAY_CONCRETE, -1.05, -0.05, -0.02, 2.1, 1.1, 0.04);
                m.box(WHITE_CONCRETE, -1.0, 0, 0.0, 2.0, 1.0, 0.03);
                m.text(WHITEBOARD[Rng.pick(seed, WHITEBOARD.length)], 0, 0.25, 0.035, 0.6, 0x00000000);
            }
            case "water_cooler" -> {
                m.box(WHITE_CONCRETE, -0.2, 0, -0.2, 0.4, 0.9, 0.4);
                m.box(LIGHT_BLUE_STAINED_GLASS, -0.17, 0.9, -0.17, 0.34, 0.45, 0.34);
                m.box(GRAY_CONCRETE, -0.05, 0.6, 0.2, 0.1, 0.06, 0.06);
                m.sound(Sound.ENTITY_GENERIC_DRINK, 0, 1, 0, 200, 0.3, 0.3, 0.5);
            }
            case "vending_machine" -> {
                m.box(RED_CONCRETE, -0.45, 0, -0.35, 0.9, 1.95, 0.7);
                m.box(LIGHT_BLUE_STAINED_GLASS, -0.38, 0.6, 0.35, 0.55, 1.2, 0.01).glow(13).group(1).alt(BLACK_STAINED_GLASS.createBlockData());
                for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) m.item(POTION, -0.28 + c * 0.17, 0.75 + r * 0.38, 0.28, 0.25, 0, 0);
                m.box(BLACK_CONCRETE, 0.2, 1.0, 0.35, 0.18, 0.5, 0.02);
                m.text("<white><bold>ALMOND", 0, 1.67, 0.36, 0.35, 0x00000000).glow(15);
                m.hitbox("vending", 0, 0, 0.3, 0.9, 1.9);
                m.anim(FLICKER, 1, O, O, 0.05, 1);
                m.sound(Sound.BLOCK_BEACON_AMBIENT, 0, 1, 0, 80, 0.4, 0.15, 1.2);
            }
            case "copier" -> {
                m.box(WHITE_TERRACOTTA, -0.4, 0, -0.35, 0.8, 0.5, 0.7);
                m.box(GRAY_CONCRETE, -0.42, 0.5, -0.37, 0.84, 0.06, 0.74);
                m.group(1).box(LIME_CONCRETE, -0.38, 0.45, -0.3, 0.76, 0.02, 0.05).glow(15);
                m.group(2).box(WHITE_CONCRETE, -0.2, 0.25, 0.3, 0.4, 0.01, 0.3);
                m.anim(SLIDE, 1, Z, O, 0.55, 30);
                m.anim(SLIDE, 2, Z, O, 0.25, 60);
                m.sound(Sound.BLOCK_PISTON_CONTRACT, 0, 0.5, 0, 60, 0.4, 0.15, 1.8);
            }
            case "window_skyline" -> {
                for (int i = 0; i < 12; i++) {
                    long h = Rng.hash(seed, i);
                    double x = -2.3 + i * 0.4, w = 0.15 + Rng.unit(h) * 0.2, ht = 0.3 + Rng.unit(Rng.hash(h, 1)) * 1.3;
                    double z = 0.1 + Rng.unit(Rng.hash(h, 2)) * 0.25;
                    m.box((i & 1) == 0 ? GRAY_CONCRETE : LIGHT_GRAY_CONCRETE, x, 0, z, w, ht, 0.12);
                    if (Rng.chance(Rng.hash(h, 3), 0.6)) m.box(YELLOW_CONCRETE, x + w / 3, ht * 0.6, z - 0.01, 0.03, 0.03, 0.01).glow(15);
                }
                m.group(1).box(WHITE_CONCRETE, -1.5, 1.6, 0.35, 0.9, 0.12, 0.05).glow(15);
                m.group(2).box(WHITE_CONCRETE, 0.6, 1.75, 0.38, 0.7, 0.1, 0.05).glow(15);
                m.anim(BOB, 1, X, O, 0.4, 600);
                m.anim(BOB, 2, X, O, 0.3, 500);
            }
            case "window_mirror_office" -> {
                m.push().scale(0.3);
                for (int i = 0; i < 3; i++) {
                    m.push().translate(-4 + i * 4, 0, 1);
                    Shapes.desk(m, SPRUCE_PLANKS, SPRUCE_PLANKS, 1.4, 0.7, 0.8);
                    m.pop();
                }
                m.push().translate(0, 0, -0.5).rotate('y', 180);
                Shapes.figure(m, BLACK_CONCRETE, BLACK_CONCRETE, GRAY_CONCRETE, 1.9);
                m.pop();
                m.pop();
            }
            default -> {
                return Hotel.build(base, seed, m) ? m : null;
            }
        }
        return m;
    }
}
