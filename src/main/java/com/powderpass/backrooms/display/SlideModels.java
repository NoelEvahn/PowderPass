package com.powderpass.backrooms.display;

import com.powderpass.backrooms.gen.SlidePath;
import com.powderpass.backrooms.util.Rng;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.joml.Vector3f;

import java.util.Locale;

import static org.bukkit.Material.*;

/**
 * Water slides as display models, built along a {@link SlidePath}:
 * <ul>
 *   <li>{@code slide:<open|half|enclosed>[:dry][:<drop>]}: a whole free-standing slide (an asset) with supports down
 *   to the level of its end, a deck and ladder at the top, and a "ride" hitbox at the bottom and the top;</li>
 *   <li>{@code slide_seg:<style>:<wet|dry>:<drop>:<i0>:<i1>}: points i0..i1 of a generated slide (the generator
 *   cuts long slides into pieces that each live in one chunk), origin at point i0;</li>
 *   <li>{@code slide_ride}: the inflatable ring a rider sits in.</li>
 * </ul>
 * Cross-section, in a piece's frame (+Z downhill, +Y up): a bed 1.1 wide, two side walls leaning out (open, half),
 * arched ribs every third block (half), or upright tinted glass walls and a roof (enclosed). Wet slides carry
 * glints that run downhill ({@link Anim.Type#FLOW}) and drip and splash.
 */
public final class SlideModels {
    private static final Material[] SHELL = {LIGHT_BLUE_CONCRETE, YELLOW_CONCRETE, ORANGE_CONCRETE, LIME_CONCRETE, MAGENTA_CONCRETE, CYAN_CONCRETE};
    private static final Material[] GLASS = {LIGHT_BLUE_STAINED_GLASS, YELLOW_STAINED_GLASS, ORANGE_STAINED_GLASS, LIME_STAINED_GLASS,
            MAGENTA_STAINED_GLASS, CYAN_STAINED_GLASS};
    /** Water glints travel this far per cycle and sit this far apart, so the jump back is invisible. */
    private static final double FLOW_STEP = 2.0;
    private static final int FLOW_TICKS = 8;

    private SlideModels() {
    }

    static Model build(String base, String suffix, long seed) {
        return switch (base) {
            case "slide" -> whole(suffix.isEmpty() ? "slide" : "slide:" + suffix, seed);
            case "slide_seg" -> segment(suffix, seed);
            case "slide_ride" -> ride(seed);
            default -> null;
        };
    }

    // ------------------------------------------------------------------ whole slides (assets)

    static Model whole(String id, long seed) {
        SlidePath p = SlidePath.fromId(id, seed);
        if (p == null) return null;
        Model m = new Model();
        m.viewRange = 2.5f;
        double ground = -p.drop - 0.15;
        chute(m, p, 0, p.pts.length - 1, ground);
        // the deck and ladder at the top, behind the launch
        m.box(WHITE_CONCRETE, -0.9, -0.25, -1.9, 1.8, 0.2, 1.95);
        for (double x : new double[]{-0.45, 0.4}) m.box(GRAY_CONCRETE, x, ground, -2.0, 0.07, -ground - 0.05, 0.07);
        double rungs = Math.max(2, Math.min(10, -ground / 0.6));
        for (int i = 1; i < rungs; i++) m.box(GRAY_CONCRETE, -0.45, ground + i * (-ground / rungs), -2.0, 0.9, 0.06, 0.06);
        // rails round the deck
        m.box(GRAY_CONCRETE, -0.95, -0.05, -1.9, 0.05, 0.9, 1.9);
        m.box(GRAY_CONCRETE, 0.9, -0.05, -1.9, 0.05, 0.9, 1.9);
        // click the ladder (at the bottom) or the deck (at the top) to ride
        m.hitbox("slide", 0, ground, -2.0, 1.2, 2.2);
        m.hitbox("slide", 0, -0.05, -1.0, 1.4, 1.2);
        if (p.wet) {
            double[] end = p.pts[p.pts.length - 1];
            m.emit(Particle.SPLASH, null, end[0], end[1] + 0.3, end[2] + 1, 6, 0.5, 0.1, 6, 0.8);
            m.sound(Sound.BLOCK_WATER_AMBIENT, 0, -p.drop / 2, 0, 60, 0.6, 0.5, 1.2);
        }
        return m;
    }

    // ------------------------------------------------------------------ generated slide pieces

    /** {@code <style>:<wet|dry>:<drop>:<i0>:<i1>} */
    static Model segment(String suffix, long seed) {
        String[] a = suffix.toLowerCase(Locale.ROOT).split(":");
        if (a.length != 5) return null;
        SlidePath.Style st = SlidePath.Style.parse(a[0]);
        if (st == null) return null;
        int i0, i1;
        double drop;
        try {
            drop = Double.parseDouble(a[2]);
            i0 = Integer.parseInt(a[3]);
            i1 = Integer.parseInt(a[4]);
        } catch (NumberFormatException e) {
            return null;
        }
        SlidePath p = SlidePath.generate(seed, st, a[1].equals("wet"), drop);
        if (i0 < 0 || i1 >= p.pts.length || i1 <= i0) return null;
        Model m = new Model();
        m.viewRange = 1.6f;
        m.translate(-p.pts[i0][0], -p.pts[i0][1], -p.pts[i0][2]);
        chute(m, p, i0, i1, Double.NaN);
        return m;
    }

    // ------------------------------------------------------------------ the chute

    /**
     * Pieces from point i0 to i1 in the slide's own space. Straight, evenly sloped stretches become one long piece.
     * {@code ground} = local height of the floor for support legs (NaN = no legs; the generator builds real ones).
     */
    private static void chute(Model m, SlidePath p, int i0, int i1, double ground) {
        int colour = Rng.pick(Rng.hash(p.seed, 77), SHELL.length);
        int group = 1;
        int i = i0;
        while (i < i1) {
            int j = i + 1;
            while (j < i1 && j - i < 8 && same(p, i, j)) j++;
            group = piece(m, p, i, j, colour, group);
            i = j;
        }
        if (!Double.isNaN(ground)) {
            for (int k = Math.max(i0, 2); k <= i1; k += 6) {
                double[] q = p.pts[k];
                double h = q[1] - 0.1 - ground;
                if (h > 1.2) m.box(WHITE_CONCRETE, q[0] - 0.09, ground, q[2] - 0.09, 0.18, h, 0.18);
            }
        }
    }

    /** Does the step from k to k+1 run the same way as the step from i to i+1 (and lean the same)? */
    private static boolean same(SlidePath p, int i, int k) {
        double[] a = dir(p, i), b = dir(p, k);
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2] > 0.9995 && Math.abs(p.bank[i] - p.bank[k]) < 1;
    }

    private static double[] dir(SlidePath p, int i) {
        double[] a = p.pts[i], b = p.pts[i + 1];
        double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2], l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return new double[]{dx / l, dy / l, dz / l};
    }

    private static int piece(Model m, SlidePath p, int a, int b, int colour, int group) {
        double[] s = p.pts[a], e = p.pts[b];
        double dx = e[0] - s[0], dy = e[1] - s[1], dz = e[2] - s[2];
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double yaw = Math.toDegrees(Math.atan2(dx, dz)), pitch = Math.toDegrees(Math.asin(-dy / len));
        Material shell = SHELL[colour], glass = GLASS[colour];
        m.push().translate(s[0], s[1], s[2]).rotate('y', yaw).rotate('x', pitch).rotate('z', -p.bank[a]);
        double l = len + 0.04; // a hair of overlap hides the joints
        m.group(0);
        m.box(p.wet ? shell : WHITE_CONCRETE, -0.55, -0.1, -0.02, 1.1, 0.1, l);
        switch (p.style) {
            case ENCLOSED -> {
                m.box(glass, -0.63, -0.1, -0.02, 0.08, 1.65, l);
                m.box(glass, 0.55, -0.1, -0.02, 0.08, 1.65, l);
                m.box(glass, -0.63, 1.55, -0.02, 1.26, 0.08, l);
            }
            case HALF, OPEN -> {
                m.push().translate(-0.55, -0.05, 0).rotate('z', 24);
                m.box(shell, -0.08, 0, -0.02, 0.08, 0.62, l);
                m.pop();
                m.push().translate(0.55, -0.05, 0).rotate('z', -24);
                m.box(shell, 0, 0, -0.02, 0.08, 0.62, l);
                m.pop();
                if (p.style == SlidePath.Style.HALF) {
                    for (int k = a; k < b; k++) {
                        if (k % 3 != 0) continue;
                        double z = (k - a) * len / (b - a);
                        m.box(WHITE_CONCRETE, -0.8, 0, z, 0.08, 1.7, 0.12);
                        m.box(WHITE_CONCRETE, 0.72, 0, z, 0.08, 1.7, 0.12);
                        m.box(WHITE_CONCRETE, -0.8, 1.7, z, 1.6, 0.1, 0.12);
                    }
                }
            }
        }
        if (p.wet) {
            // glints running downhill: FLOW moves them FLOW_STEP along the piece, then they jump back unseen
            int n = Math.max(1, (int) Math.round(len / FLOW_STEP));
            m.group(group);
            for (int k = 0; k < n; k++) {
                double x = ((Rng.pick(Rng.hash(p.seed, a, k), 5)) - 2) * 0.16;
                m.box(WHITE_STAINED_GLASS, x - 0.05, 0.005, k * FLOW_STEP, 0.1, 0.012, 0.45).glow(12);
            }
            m.group(0);
            m.pop();
            m.anim(Anim.Type.FLOW, group, new Vector3f((float) (dx / len), (float) (dy / len), (float) (dz / len)), Model.v(0, 0, 0),
                    FLOW_STEP, FLOW_TICKS);
            if (len > 2 && (a & 3) == 0) m.emit(Particle.FALLING_WATER, null, s[0] + dx / 2, s[1] + dy / 2 - 0.2, s[2] + dz / 2, 1, 0.3, 0, 12, 0.5);
            return group + 1;
        }
        m.pop();
        return group;
    }

    // ------------------------------------------------------------------ the ring a rider sits in

    /** The ring a rider sits in (also an asset, {@code slide_ride}). */
    public static Model ride(long seed) {
        Model m = new Model();
        Material[] cols = {WHITE_CONCRETE, Rng.chance(seed, 0.5) ? LIGHT_BLUE_CONCRETE : YELLOW_CONCRETE};
        // an octagonal inflatable ring round the rider's hips, lying flat on the bed
        for (int k = 0; k < 8; k++) {
            m.push().rotate('y', k * 45).translate(0, 0, 0.42);
            m.box(cols[k & 1], -0.2, 0.0, -0.11, 0.4, 0.22, 0.22);
            m.pop();
        }
        m.box(BLACK_CONCRETE, -0.32, 0.02, -0.32, 0.64, 0.04, 0.64);
        // two grab handles
        m.box(GRAY_CONCRETE, -0.52, 0.2, -0.05, 0.06, 0.08, 0.1);
        m.box(GRAY_CONCRETE, 0.46, 0.2, -0.05, 0.06, 0.08, 0.1);
        return m;
    }
}
