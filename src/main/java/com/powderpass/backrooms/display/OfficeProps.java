package com.powderpass.backrooms.display;

import com.powderpass.backrooms.util.Rng;
import org.bukkit.Material;
import org.joml.Vector3f;

import static com.powderpass.backrooms.display.Anim.Type.*;
import static com.powderpass.backrooms.display.Model.v;
import static org.bukkit.Material.*;

/**
 * Display props of Level 4, the office towers. Kept deliberately cheap (2–6 entities each): every tower floor
 * stacks its furniture over the same chunks, so the per-chunk budget is shared by up to eight floors. Desks,
 * partitions, shelves and planters are real blocks; only what blocks cannot draw is a display.
 */
final class OfficeProps {
    private OfficeProps() {
    }

    private static final Vector3f Y = v(0, 1, 0), O = v(0, 0, 0);

    private static final String[] COMPANIES = {"Halvorsen & Pryce", "Meridian Data Systems", "Q3 Solutions", "Northgate Holdings",
            "Ellery Insurance Group", "Brightline Consulting", "Unit 7 (vacant)", "—", "Records", "Accounts Payable", "Human Resources"};

    /** Floor label as the lift and the stairwell show it: L for the lobby. */
    static String label(int floor) {
        return floor == 0 ? "L" : String.valueOf(floor);
    }

    static boolean build(String id, String suffix, long seed, Model m) {
        switch (id) {
            case "pc_monitor" -> {
                // a beige CRT on the desk, front +Z; most are asleep (dim blue), some on, some off
                boolean lcd = Rng.chance(seed, 0.2);
                double r = Rng.unit(Rng.hash(seed, 1));
                if (lcd) {
                    m.box(BLACK_CONCRETE, -0.24, 0.0, -0.08, 0.48, 0.34, 0.06);
                } else {
                    m.box(WHITE_TERRACOTTA, -0.22, 0.0, -0.24, 0.44, 0.38, 0.40);
                }
                double z = lcd ? -0.015 : 0.165;
                if (r < 0.35) {
                    m.group(1).box(LIGHT_BLUE_CONCRETE, -0.17, 0.06, z, 0.34, 0.25, 0.01).glow(10).alt(BLACK_CONCRETE.createBlockData());
                    m.anim(FLICKER, 1, O, O, 0.03, 1);
                } else if (r < 0.75) {
                    m.box(BLUE_CONCRETE, -0.17, 0.06, z, 0.34, 0.25, 0.01).glow(6);
                } else {
                    m.box(BLACK_CONCRETE, -0.17, 0.06, z, 0.34, 0.25, 0.01);
                }
            }
            case "task_chair" -> {
                Material[] fabric = {GRAY_WOOL, BLACK_WOOL, LIGHT_GRAY_WOOL, BLUE_TERRACOTTA};
                Material f = fabric[Rng.pick(seed, fabric.length)];
                m.box(BLACK_CONCRETE, -0.04, 0.0, -0.04, 0.08, 0.42, 0.08);
                m.box(f, -0.24, 0.42, -0.24, 0.48, 0.09, 0.48);
                m.push().translate(0, 0.52, -0.27).rotate('x', -8);
                m.box(f, -0.22, 0.0, -0.04, 0.44, 0.48, 0.07);
                m.pop();
            }
            case "exec_chair" -> {
                Material leather = Rng.chance(seed, 0.5) ? BROWN_CONCRETE : BLACK_CONCRETE;
                m.box(BLACK_CONCRETE, -0.05, 0.0, -0.05, 0.1, 0.43, 0.1);
                m.box(leather, -0.28, 0.43, -0.28, 0.56, 0.13, 0.56);
                m.push().translate(0, 0.55, -0.3).rotate('x', -10);
                m.box(leather, -0.26, 0.0, -0.06, 0.52, 0.8, 0.12);
                m.pop();
                m.box(BLACK_CONCRETE, -0.34, 0.56, -0.2, 0.68, 0.05, 0.08);
            }
            case "paper_stack" -> m.box(WHITE_CONCRETE, -0.11, 0.0, -0.15, 0.22, 0.05 + Rng.unit(seed) * 0.08, 0.3);
            case "desk_lamp" -> {
                // a banker's lamp, switched off
                m.box(GOLD_BLOCK, -0.02, 0.0, -0.01, 0.02, 0.28, 0.02);
                m.box(GREEN_TERRACOTTA, -0.16, 0.28, -0.07, 0.32, 0.08, 0.14);
            }
            case "filing_cabinet" -> {
                m.box(LIGHT_GRAY_CONCRETE, -0.25, 0.0, -0.31, 0.5, 1.32, 0.62);
                m.box(IRON_BLOCK, -0.08, 1.12, 0.31, 0.16, 0.03, 0.03); // top drawer handle
            }
            case "lobby_sofa" -> {
                m.box(BLACK_WOOL, -1.35, 0.0, -0.4, 2.7, 0.42, 0.8);
                m.box(BLACK_WOOL, -1.35, 0.42, -0.45, 2.7, 0.48, 0.22);
                m.box(BLACK_WOOL, -1.45, 0.0, -0.45, 0.14, 0.62, 0.85);
                m.box(BLACK_WOOL, 1.31, 0.0, -0.45, 0.14, 0.62, 0.85);
            }
            case "revolving_door" -> {
                // a revolving door turning slowly in the glass, opening onto nothing
                m.box(GRAY_CONCRETE, -1.0, 2.75, 0.0, 2.0, 0.2, 1.1);
                m.group(1);
                m.box(LIGHT_GRAY_STAINED_GLASS, -0.95, 0.0, 0.53, 1.9, 2.7, 0.04);
                m.box(LIGHT_GRAY_STAINED_GLASS, -0.02, 0.0, -0.4, 0.04, 2.7, 1.9);
                m.anim(SPIN, 1, Y, v(0, 0, 0.55), 1, 900);
            }
            case "lift_panel" -> {
                // suffix "<floors>:<this floor>": a steel plate, two columns of lit floor labels (one text display per
                // column, one line per row; a line is 0.16 blocks tall at this scale) and one hitbox per floor
                String[] a = suffix.split(":");
                int floors = Math.max(1, Math.min(8, parse(a[0], 1)));
                int here = a.length > 1 ? parse(a[1], 0) : 0;
                int rows = (floors + 1) / 2;
                m.box(IRON_BLOCK, -0.16, -0.06, 0.0, 0.32, rows * 0.16 + 0.1, 0.03);
                for (int col = 0; col < 2; col++) {
                    StringBuilder sb = new StringBuilder();
                    for (int row = rows - 1; row >= 0; row--) {
                        int k = row * 2 + col;
                        if (sb.length() > 0) sb.append("<br>");
                        sb.append(k >= floors ? " " : (k == here ? "<#ffb347>" : "<#e8eef0>") + "<bold>(" + label(k) + ")</bold>");
                    }
                    m.text(sb.toString(), col == 0 ? -0.075 : 0.075, -0.02, 0.035, 0.64, 0x00000000).glow(15);
                }
                for (int k = 0; k < floors; k++) {
                    m.hitbox("lift:" + k, k % 2 == 0 ? -0.075 : 0.075, (k / 2) * 0.16 - 0.02, 0.08, 0.14, 0.15);
                }
            }
            case "lift_sign" -> {
                int f = parse(suffix, 0);
                m.text("<#ffb347>▲▼  <bold>" + label(f), 0, 0.0, 0.01, 0.55, 0xF0080808).glow(15);
            }
            case "stair_sign" -> {
                int f = parse(suffix, 0);
                m.text("<white><bold>STAIR</bold><br><white>FLOOR " + label(f), 0, 0.0, 0.01, 0.4, 0xF0106030).glow(12);
            }
            case "tower_lamp" -> {
                m.box(GRAY_CONCRETE, -0.18, -0.1, 0.0, 0.36, 0.2, 0.05);
                m.group(1).box(WHITE_CONCRETE, -0.14, -0.07, 0.05, 0.28, 0.14, 0.07).lamp().alt(GRAY_CONCRETE.createBlockData());
            }
            case "directory_board" -> {
                m.box(BLACK_CONCRETE, -0.6, 0.0, 0.0, 1.2, 1.1, 0.04);
                StringBuilder sb = new StringBuilder("<white><bold>DIRECTORY</bold>");
                for (int k = 0; k < 6; k++) {
                    sb.append("<br><gray>").append(k == 0 ? "L" : String.valueOf(k)).append("  ")
                            .append(COMPANIES[Rng.pick(Rng.hash(seed, k), COMPANIES.length)]);
                }
                m.text(sb.toString(), 0, 0.12, 0.045, 0.32, 0x00000000).glow(10);
            }
            case "roof_beacon" -> {
                m.box(IRON_BLOCK, -0.04, 0.0, -0.04, 0.08, 3.2, 0.08);
                m.group(1).box(RED_CONCRETE, -0.1, 3.2, -0.1, 0.2, 0.18, 0.2).glow(15).alt(BLACK_CONCRETE.createBlockData());
                m.anim(BLINK, 1, O, O, 0.5, 1);
                m.viewRange = 4f;
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static int parse(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (RuntimeException e) {
            return def;
        }
    }
}
