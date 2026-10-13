package com.powderpass.backrooms.display;

import com.powderpass.backrooms.util.Rng;
import org.bukkit.Material;

import java.util.Locale;

import static org.bukkit.Material.*;

/**
 * Parametric surface details: the shared vocabulary every level uses for thin overlays. Ids look like
 * {@code d:<kind>:<arg>:<arg>...}; generators build them with {@code Detail} helpers, so the geometry here and the
 * placement there stay in one convention:
 * <ul>
 *   <li>Wall details lie in the XY plane, from z=0 (touching the wall) to a small thickness toward +Z (the room).
 *   Their +X runs along the wall.</li>
 *   <li>Floor details lie in the XZ plane from y=0 up; ceiling details from y=0 down.</li>
 *   <li>Long details run along +X from x=0; the anchor is the start, already offset off the surface.</li>
 * </ul>
 * Displays stretch their block texture when scaled, so large overlays only use near-uniform textures (concrete,
 * terracotta); patterned surfaces are made with real blocks in the generators instead.
 */
public final class Details {
    private Details() {
    }

    /** Thickness of every overlay: enough to never z-fight, thin enough to read as paint or paper. */
    static final double T = 0.008;

    static boolean build(String spec, long seed, Model m) {
        String[] a = spec.split(":");
        String kind = a[0];
        switch (kind) {
            case "strip" -> m.box(mat(a, 1), 0, 0, 0, d(a, 2), d(a, 3), d(a, 4));
            case "wpatch" -> patch(m, mat(a, 1), d(a, 2), d(a, 3), seed, 'w');
            case "fpatch" -> patch(m, mat(a, 1), d(a, 2), d(a, 3), seed, 'f');
            case "cpatch" -> patch(m, mat(a, 1), d(a, 2), d(a, 3), seed, 'c');
            case "streak" -> {
                // a vertical moisture run: wide at the top, narrowing into a drip line
                Material mt = mat(a, 1);
                double h = d(a, 2);
                double w = 0.25 + Rng.unit(Rng.hash(seed, 1)) * 0.25;
                m.box(mt, -w / 2, -h * 0.35, 0, w, h * 0.35, T);
                m.box(mt, -w * 0.22, -h, T * 0.5, w * 0.3, h * 0.7, T);
            }
            case "crack" -> crack(m, mat(a, 1), d(a, 2), a.length > 3 ? a[3].charAt(0) : 'w', seed);
            case "peel" -> {
                // a peeled strip of wallpaper: the bare backing, and the curled flap lifting off it
                Material paper = mat(a, 1), back = mat(a, 2);
                double w = d(a, 3), h = d(a, 4);
                m.box(back, 0, -h, 0, w, h, T);
                m.push().translate(0, 0, T * 2).rotate('x', -24 - Rng.pick(seed, 14));
                m.box(paper, 0.02, -h * 0.75, 0, w - 0.04, h * 0.75, T);
                m.pop();
            }
            case "pipe" -> {
                // horizontal pipe along +X, centred on the anchor, with brackets back to the surface (-Z / up)
                Material mt = mat(a, 1);
                double len = d(a, 2), r = d(a, 3);
                m.box(mt, 0, -r / 2, -r / 2, len, r, r);
                for (double x = 0.6; x < len - 0.3; x += 2.5) {
                    m.box(GRAY_CONCRETE, x, -r / 2 - 0.03, -r / 2 - 0.03, 0.08, r + 0.06, r + 0.06);
                }
                if (len > 3 && Rng.chance(seed, 0.5)) m.box(BROWN_TERRACOTTA, len * 0.5 - 0.2, -r / 2 - 0.004, -r / 2 - 0.004, 0.4, r * 0.6, r + 0.008);
            }
            case "duct" -> {
                double len = d(a, 1);
                m.box(LIGHT_GRAY_CONCRETE, 0, -0.5, -0.4, len, 0.5, 0.8);
                for (double x = 2; x < len - 0.5; x += 2) m.box(GRAY_CONCRETE, x, -0.53, -0.43, 0.06, 0.56, 0.86);
            }
            case "grille" -> {
                double w = d(a, 1), h = d(a, 2);
                m.box(BLACK_CONCRETE, 0, 0, 0, w, h, T);
                m.box(GRAY_CONCRETE, -0.04, -0.04, T, w + 0.08, 0.04, 0.03);
                m.box(GRAY_CONCRETE, -0.04, h, T, w + 0.08, 0.04, 0.03);
                for (double y = 0.08; y < h - 0.02; y += 0.12) m.box(LIGHT_GRAY_CONCRETE, 0, y, T, w, 0.03, 0.04);
            }
            case "jbox" -> {
                m.box(GRAY_CONCRETE, -0.17, -0.2, 0, 0.34, 0.4, 0.12);
                m.box(LIGHT_GRAY_CONCRETE, -0.13, -0.16, 0.12, 0.26, 0.32, 0.01);
                m.box(GRAY_CONCRETE, -0.03, 0.2, 0.03, 0.06, d(a, 1), 0.06); // conduit up the wall
            }
            case "outlet" -> {
                m.box(WHITE_CONCRETE, -0.06, -0.09, 0, 0.12, 0.18, 0.012);
                m.box(BLACK_CONCRETE, -0.025, -0.06, 0.012, 0.05, 0.12, 0.004);
            }
            case "drain" -> {
                double w = d(a, 1);
                m.box(GRAY_CONCRETE, -w / 2, 0, -w / 2, w, T, w);
                for (int i = 1; i < 4; i++) m.box(LIGHT_GRAY_CONCRETE, -w / 2 + i * w / 4 - 0.02, T, -w / 2 + 0.04, 0.04, 0.012, w - 0.08);
            }
            case "vent" -> {
                // ceiling vent panel: frame and slats hanging just below the ceiling
                double w = d(a, 1);
                m.box(LIGHT_GRAY_CONCRETE, -w / 2, -T, -w / 2, w, T, w);
                for (int i = 0; i < 4; i++) m.box(GRAY_CONCRETE, -w / 2 + 0.06, -T - 0.012, -w / 2 + 0.1 + i * (w - 0.2) / 3.5, w - 0.12, 0.012, 0.05);
            }
            case "sill" -> {
                double len = d(a, 1);
                m.box(LIGHT_GRAY_CONCRETE, 0, 0, 0, len, 0.06, 0.14);
                m.box(LIGHT_GRAY_CONCRETE, 0, d(a, 2), 0, len, 0.05, 0.05);
            }
            case "coping" -> {
                // a rounded-looking pool edge: a lip and a chamfer under it, along +X
                double len = d(a, 1);
                m.box(SMOOTH_QUARTZ, 0, 0, -0.06, len, 0.05, 0.3);
                m.push().translate(0, 0, 0.24).rotate('x', 45);
                m.box(SMOOTH_QUARTZ, 0, -0.035, -0.035, len, 0.07, 0.07);
                m.pop();
            }
            case "rcorner" -> {
                // three small segments that soften a 90° corner (vertical, along y)
                Material mt = mat(a, 1);
                double h = d(a, 2);
                for (int i = 0; i < 3; i++) {
                    m.push().rotate('y', 22.5 + i * 22.5);
                    m.box(mt, -0.06, 0, 0, 0.12, h, 0.02);
                    m.pop();
                }
            }
            case "grass" -> {
                int n = Math.max(2, Math.min(5, (int) d(a, 1)));
                boolean dry = a.length > 2 && a[2].equals("dry");
                for (int i = 0; i < n; i++) {
                    long h = Rng.hash(seed, i);
                    double x = (Rng.unit(h) - 0.5) * 0.5, z = (Rng.unit(Rng.hash(h, 1)) - 0.5) * 0.5;
                    double len = 0.35 + Rng.unit(Rng.hash(h, 2)) * 0.4;
                    m.push().translate(x, 0, z).rotate('y', Rng.pick(Rng.hash(h, 3), 180)).rotate('z', Rng.pick(Rng.hash(h, 4), 30) - 15);
                    m.box(dry || i == n - 1 && Rng.chance(h, 0.2) ? SANDSTONE : MOSS_BLOCK, -0.015, 0, -0.004, 0.03, len, 0.008);
                    m.pop();
                }
            }
            case "car" -> {
                // a parked car that has not moved in a very long time; front = +Z
                Material paint = switch (Rng.pick(seed, 5)) {
                    case 0 -> WHITE_CONCRETE;
                    case 1 -> GRAY_CONCRETE;
                    case 2 -> BLUE_TERRACOTTA;
                    case 3 -> RED_TERRACOTTA;
                    default -> LIGHT_GRAY_CONCRETE;
                };
                m.box(paint, -0.9, 0.3, -2.1, 1.8, 0.6, 4.2);
                m.box(paint, -0.8, 0.9, -1.1, 1.6, 0.5, 2.0);
                m.box(BLACK_STAINED_GLASS, -0.76, 0.92, -1.06, 1.52, 0.44, 1.92);
                for (int i = 0; i < 4; i++) {
                    double x = i % 2 == 0 ? -0.95 : 0.75, z = i < 2 ? 1.2 : -1.55;
                    m.box(BLACK_CONCRETE, x, 0, z, 0.2, 0.5, 0.5);
                }
                if (Rng.chance(Rng.hash(seed, 9), 0.35)) m.box(LIGHT_GRAY_WOOL, -0.92, 0.3, -2.12, 1.84, 1.12, 4.24); // dust sheet
            }
            default -> {
                return false;
            }
        }
        m.viewRange = kind.equals("car") ? 0.9f : 0.45f; // details are only worth drawing up close
        return true;
    }

    private static double d(String[] a, int i) {
        return Double.parseDouble(a[i]);
    }

    private static Material mat(String[] a, int i) {
        Material m = Material.matchMaterial(a[i].toUpperCase(Locale.ROOT));
        if (m == null) throw new IllegalArgumentException("unknown material " + a[i]);
        return m;
    }

    /** An irregular blotch: a main rectangle and two overlapping smaller ones, each a hair further off the surface. */
    private static void patch(Model m, Material mt, double w, double h, long seed, char plane) {
        double[][] r = {
                {0.1, 0.1, 0.8, 0.8},
                {Rng.unit(Rng.hash(seed, 1)) * 0.4, Rng.unit(Rng.hash(seed, 2)) * 0.5, 0.45, 0.5},
                {0.3 + Rng.unit(Rng.hash(seed, 3)) * 0.3, Rng.unit(Rng.hash(seed, 4)) * 0.3, 0.35, 0.7}};
        for (int i = 0; i < r.length; i++) {
            double x = r[i][0] * w, y = r[i][1] * h, sw = r[i][2] * w, sh = r[i][3] * h, off = T * 0.3 * i;
            switch (plane) {
                case 'w' -> m.box(mt, x, y, off, sw, sh, T);
                case 'f' -> m.box(mt, x, off, y, sw, T, sh);
                default -> m.box(mt, x, -T - off, y, sw, T, sh);
            }
        }
    }

    /** A branching hairline crack built from a few thin rotated segments. */
    private static void crack(Model m, Material mt, double len, char plane, long seed) {
        double x = 0, y = 0, ang = Rng.unit(seed) * 360;
        int segs = 3;
        double seg = len / segs;
        for (int i = 0; i < segs; i++) {
            ang += Rng.unit(Rng.hash(seed, i)) * 70 - 35;
            segment(m, mt, x, y, ang, seg, 0.025, plane);
            double rad = Math.toRadians(ang);
            x += Math.cos(rad) * seg;
            y += Math.sin(rad) * seg;
            if (i == 1) segment(m, mt, x, y, ang + 55, seg * 0.6, 0.018, plane);
        }
    }

    private static void segment(Model m, Material mt, double x, double y, double ang, double len, double w, char plane) {
        m.push();
        switch (plane) {
            case 'w' -> m.translate(x, y, 0).rotate('z', ang).box(mt, 0, -w / 2, 0, len, w, T);
            case 'f' -> m.translate(x, 0, y).rotate('y', -ang).box(mt, 0, 0, -w / 2, len, T, w);
            default -> m.translate(x, 0, y).rotate('y', -ang).box(mt, 0, -T, -w / 2, len, T, w);
        }
        m.pop();
    }
}
