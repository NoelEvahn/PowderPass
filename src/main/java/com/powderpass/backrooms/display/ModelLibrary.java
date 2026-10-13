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
        String suffix = "";
        int param = 0;
        int colon = id.indexOf(':');
        if (colon > 0) {
            base = id.substring(0, colon);
            suffix = id.substring(colon + 1);
            try {
                param = Integer.parseInt(suffix);
            } catch (NumberFormatException ignored) {
                // a word suffix such as "dead" or "flicker"
            }
        }
        Model m = new Model();
        if (base.equals("d")) return Details.build(suffix, seed, m) ? m : null;
        switch (base) {
            // ---------------------------------------------------------------- Level 0
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
            case "noclip_shimmer" -> {
                for (int i = 0; i < 4; i++) {
                    m.group(i + 1);
                    m.box(SMOOTH_SANDSTONE, -0.5 + i * 0.3, 0.1 + i * 0.4, -0.55, 0.04, 0.8, 0.04).glow(12);
                    m.anim(JITTER, i + 1, O, O, 0.05, 1);
                }
                m.emit(Particle.WHITE_ASH, null, 0, 1, 0, 6, 0.5, 0.02, 10, 1);
                m.sound(Sound.BLOCK_BEACON_AMBIENT, 0, 1, 0, 60, 0.6, 0.4, 1.6);
            }
            case "freight_doors" -> {
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
            case "crt_monitor" -> {
                m.box(WHITE_TERRACOTTA, -0.24, 0.02, -0.25, 0.48, 0.42, 0.42);
                m.box(WHITE_TERRACOTTA, -0.15, 0, -0.15, 0.3, 0.03, 0.25);
                m.box(CYAN_TERRACOTTA, -0.2, 0.07, 0.17, 0.4, 0.32, 0.01).glow(11).group(1).alt(BLACK_CONCRETE.createBlockData());
                m.text(MONITOR_LINES[Rng.pick(seed, MONITOR_LINES.length)], 0, 0.17, 0.185, 0.22, 0x00000000).glow(15).named("screen_text");
                m.box(WHITE_TERRACOTTA, -0.22, 0, 0.25, 0.44, 0.03, 0.15);
                m.anim(FLICKER, 1, O, O, 0.08, 1);
                m.behavior("monitor");
            }
            case "basement_sign" -> Shapes.plate(m, GRAY_CONCRETE, 1.3, 0.4, "<white><bold>STAIRS ▼ B1", 0.42);
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
            default -> {
                // per-level prop catalogues, each owned by its level
                boolean built = Fixtures.build(base, suffix, seed, m) || PoolProps.build(base, suffix, seed, m)
                        || GarageProps.build(base, suffix, seed, m) || OfficeProps.build(base, suffix, seed, m)
                        || PlainsProps.build(base, suffix, seed, m);
                return built ? m : null;
            }
        }
        return m;
    }
}
