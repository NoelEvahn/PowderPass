package com.powderpass.backrooms.display;

import com.powderpass.backrooms.gen.SlideGeometry;
import com.powderpass.backrooms.gen.SlidePath;
import com.powderpass.backrooms.util.Rng;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.joml.Matrix4f;
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
 * The shape comes from {@link SlideGeometry}: solid walls that curve up and out (open, half-pipe) or a solid tube
 * lit from inside (enclosed). Wet slides carry a film of water and foam that runs downhill ({@link Anim.Type#FLOW}).
 */
public final class SlideModels {
    private static final int FLOW_TICKS = 8;

    private SlideModels() {
    }

    static Model build(String base, String suffix, long seed) {
        return switch (base) {
            case "slide" -> whole(suffix.isEmpty() ? "slide" : "slide:" + suffix, seed);
            case "slide_seg" -> segment(suffix, seed);
            case "slide_ride" -> ride(seed);
            case "slide_hall" -> hall(suffix);
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

    /** {@code <rot>:<i0>:<i1>}: a piece of a massive hall's slide, standing on legs down to the main floor. */
    static Model hall(String suffix) {
        String[] a = suffix.split(":");
        if (a.length != 3) return null;
        int rot, i0, i1;
        try {
            rot = Integer.parseInt(a[0]);
            i0 = Integer.parseInt(a[1]);
            i1 = Integer.parseInt(a[2]);
        } catch (NumberFormatException e) {
            return null;
        }
        SlidePath p = com.powderpass.backrooms.gen.levels.PoolroomsGenerator.hallSlidePath(Math.floorMod(rot, 4));
        if (i0 < 0 || i1 >= p.pts.length || i1 <= i0) return null;
        Model m = new Model();
        m.viewRange = 2.0f;
        m.translate(-p.pts[i0][0], -p.pts[i0][1], -p.pts[i0][2]);
        chute(m, p, i0, i1, com.powderpass.backrooms.gen.levels.PoolroomsGenerator.mainFloorSurface());
        return m;
    }

    // ------------------------------------------------------------------ the chute

    /**
     * Points i0..i1 as display parts, from {@link SlideGeometry} (the same shapes the previews draw). Foam of each
     * straight piece is one animation group that flows along that piece.
     */
    private static void chute(Model m, SlidePath p, int i0, int i1, double ground) {
        Material shell = Material.valueOf(SlideGeometry.colour(p).toUpperCase(java.util.Locale.ROOT) + "_CONCRETE");
        boolean tube = p.style == SlidePath.Style.ENCLOSED;
        java.util.Map<Integer, double[]> flows = new java.util.TreeMap<>();
        for (SlideGeometry.Box b : SlideGeometry.build(p, i0, i1, ground)) {
            Material mat = switch (b.role()) {
                case SHELL -> shell;
                case BED -> p.wet ? WHITE_CONCRETE : shell;
                case WATER -> LIGHT_BLUE_STAINED_GLASS;
                default -> WHITE_CONCRETE;
            };
            m.group(b.role() == SlideGeometry.Role.FOAM ? b.piece() + 1 : 0);
            Part part = m.boxM(mat, matrix(b));
            if (tube && (b.role() == SlideGeometry.Role.SHELL || b.role() == SlideGeometry.Role.BED)) part.glow(11); // lit inside, so riders can see the tube
            if (b.role() == SlideGeometry.Role.FOAM) {
                part.glow(13);
                flows.put(b.piece() + 1, b.f());
            }
        }
        m.group(0);
        for (java.util.Map.Entry<Integer, double[]> e : flows.entrySet()) {
            double[] f = e.getValue();
            m.anim(Anim.Type.FLOW, e.getKey(), new Vector3f((float) f[0], (float) f[1], (float) f[2]), Model.v(0, 0, 0),
                    SlideGeometry.FOAM_STEP, FLOW_TICKS);
        }
        if (p.wet && i1 - i0 >= 3) {
            double[] mid = p.pts[(i0 + i1) / 2];
            m.emit(Particle.FALLING_WATER, null, mid[0], mid[1] - 0.2, mid[2], 1, 0.3, 0, 12, 0.5);
        }
    }

    /** The unit block stretched and turned into a geometry box. */
    private static Matrix4f matrix(SlideGeometry.Box b) {
        double[] r = b.r(), u = b.u(), f = b.f(), o = b.o(), lo = b.lo(), hi = b.hi();
        return new Matrix4f((float) r[0], (float) r[1], (float) r[2], 0, (float) u[0], (float) u[1], (float) u[2], 0,
                (float) f[0], (float) f[1], (float) f[2], 0, (float) o[0], (float) o[1], (float) o[2], 1)
                .translate((float) lo[0], (float) lo[1], (float) lo[2])
                .scale((float) (hi[0] - lo[0]), (float) (hi[1] - lo[1]), (float) (hi[2] - lo[2]));
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
