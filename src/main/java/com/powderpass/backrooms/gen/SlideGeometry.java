package com.powderpass.backrooms.gen;

import java.util.ArrayList;
import java.util.List;

/**
 * The solid shape of a water slide as oriented boxes, in the slide's own space. Pure, so the display model
 * ({@code display.SlideModels}) and the preview renderer draw exactly the same thing.
 *
 * <p>Cross-sections (looking downhill, the bed's top surface at height 0):</p>
 * <ul>
 *   <li>{@link SlidePath.Style#OPEN}: a flat bed whose walls curve up and out in two panels to a rolled lip, like a
 *   playground slide;</li>
 *   <li>{@link SlidePath.Style#HALF}: a deep half-pipe, the walls curving up to nearly vertical;</li>
 *   <li>{@link SlidePath.Style#ENCLOSED}: a closed, solid octagonal tube (lit from inside).</li>
 * </ul>
 * Wet slides carry a thin film of water lying in the trough and foam streaks that run downhill, over a white bed so the
 * water shows (dry slides are one colour).
 */
public final class SlideGeometry {
    public enum Role { BED, SHELL, LIP, WATER, FOAM, LEG }

    /**
     * A box: origin {@code o}, unit axes {@code r} (across), {@code u} (its own up) and {@code f} (along), spanning
     * {@code lo..hi} in those axes. {@code piece} numbers the straight piece it belongs to (foam of one piece flows
     * together along {@code f}).
     */
    public record Box(double[] o, double[] r, double[] u, double[] f, double[] lo, double[] hi, Role role, int piece) {
    }

    /**
     * Shell colours (concrete). Wet slides take one of the first six, never blue, so the water shows against them;
     * dry ones may be any.
     */
    public static final String[] COLOURS = {"yellow", "orange", "lime", "magenta", "red", "white", "light_blue", "cyan"};

    public static String colour(SlidePath p) {
        int n = p.wet ? 6 : COLOURS.length;
        return COLOURS[Math.floorMod(Long.hashCode(p.seed * 0x9E3779B97F4A7C15L), n)];
    }

    /** Tube radius (to the inside of the walls) and how far foam travels per animation cycle (= its spacing). */
    public static final double TUBE_R = 0.86, FOAM_STEP = 2.0;
    private static final double T = 0.1; // wall thickness

    private SlideGeometry() {
    }

    /** Boxes for points i0..i1; {@code ground} = local height of the floor for support legs (NaN = none). */
    public static List<Box> build(SlidePath p, int i0, int i1, double ground) {
        List<Box> out = new ArrayList<>();
        int piece = 0;
        int i = i0;
        while (i < i1) {
            int j = i + 1;
            while (j < i1 && j - i < 8 && same(p, i, j)) j++;
            piece(out, p, i, j, piece++);
            i = j;
        }
        if (!Double.isNaN(ground)) {
            for (int k = Math.max(i0, 2); k <= i1; k += 6) {
                double[] q = p.pts[k];
                double h = q[1] - T - ground;
                if (h > 1.2) out.add(new Box(new double[]{q[0], ground, q[2]}, X, Y, Z, new double[]{-0.09, 0, -0.09}, new double[]{0.09, h, 0.09}, Role.LEG, -1));
            }
        }
        return out;
    }

    private static final double[] X = {1, 0, 0}, Y = {0, 1, 0}, Z = {0, 0, 1};

    private static boolean same(SlidePath p, int i, int k) {
        double[] a = dir(p, i), b = dir(p, k);
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2] > 0.9995 && Math.abs(p.bank[i] - p.bank[k]) < 1;
    }

    private static double[] dir(SlidePath p, int i) {
        double[] a = p.pts[i], b = p.pts[i + 1];
        return norm(new double[]{b[0] - a[0], b[1] - a[1], b[2] - a[2]});
    }

    private static void piece(List<Box> out, SlidePath p, int a, int b, int piece) {
        double[] s = p.pts[a], e = p.pts[b];
        double[] d = {e[0] - s[0], e[1] - s[1], e[2] - s[2]};
        double len = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        double[] f = norm(d);
        double[] r = norm(cross(Y, f));
        double[] u = cross(f, r);
        // lean into the turn: a positive bank lowers the +r side
        double bk = Math.toRadians(p.bank[a]), c = Math.cos(bk), sn = Math.sin(bk);
        double[] r2 = add(scale(r, c), scale(u, -sn)), u2 = add(scale(u, c), scale(r, sn));
        r = r2;
        u = u2;
        double f0 = -0.16, f1 = len + 0.16; // walls overlap so the outside of a bend has no gaps
        switch (p.style) {
            case OPEN -> trough(out, s, r, u, f, f0, f1, 0.34, new double[]{28, 62}, 0.3, piece);
            case HALF -> trough(out, s, r, u, f, f0, f1, 0.3, new double[]{24, 52, 80}, 0.33, piece);
            case ENCLOSED -> tube(out, s, r, u, f, f0, f1, piece);
        }
        if (p.wet) {
            double half = p.style == SlidePath.Style.OPEN ? 0.5 : p.style == SlidePath.Style.HALF ? 0.46 : 0.5;
            out.add(new Box(s, r, u, f, new double[]{-half, 0, -0.06}, new double[]{half, 0.035, len + 0.06}, Role.WATER, piece));
            int n = Math.max(1, (int) Math.round(len / FOAM_STEP));
            for (int k = 0; k < n; k++) {
                double x = ((Math.floorMod(Long.hashCode(p.seed) + a * 31 + k * 7, 5)) - 2) * 0.13;
                out.add(new Box(s, r, u, f, new double[]{x - 0.05, 0.036, k * FOAM_STEP}, new double[]{x + 0.05, 0.048, k * FOAM_STEP + 0.5},
                        Role.FOAM, piece));
            }
        }
    }

    /**
     * An open trough: a flat bed {@code bed} each side of the centre, then panels of width {@code w} tilted at the
     * given angles (degrees from horizontal), each starting where the last ended, finished with a rolled lip.
     */
    private static void trough(List<Box> out, double[] s, double[] r, double[] u, double[] f, double f0, double f1,
                               double bed, double[] angles, double w, int piece) {
        out.add(new Box(s, r, u, f, new double[]{-bed - 0.02, -T, f0}, new double[]{bed + 0.02, 0, f1}, Role.BED, piece));
        for (int side = -1; side <= 1; side += 2) {
            double px = side * bed, py = 0;
            for (double deg : angles) {
                double ang = Math.toRadians(deg), cx = Math.cos(ang), sy = Math.sin(ang);
                double ex = px + side * w * cx, ey = py + w * sy;
                // panel frame: across = from the panel's inner end toward its outer end, mirrored so it stays right-handed
                double[] pr, pu, po;
                if (side > 0) {
                    pr = add(scale(r, cx), scale(u, sy));
                    pu = add(scale(r, -sy), scale(u, cx));
                    po = point(s, r, u, px, py);
                } else {
                    pr = add(scale(r, cx), scale(u, -sy));   // from the outer end (ex, ey) back toward (px, py)
                    pu = add(scale(r, sy), scale(u, cx));
                    po = point(s, r, u, ex, ey);
                }
                out.add(new Box(po, pr, pu, f, new double[]{-0.015, -T, f0}, new double[]{w + 0.015, 0, f1}, Role.SHELL, piece));
                px = ex;
                py = ey;
            }
            // the rolled lip along the top edge
            double[] lo = point(s, r, u, px, py);
            out.add(new Box(lo, r, u, f, new double[]{-0.08, -0.04, f0}, new double[]{0.08, 0.1, f1}, Role.LIP, piece));
        }
    }

    /** A closed octagonal tube whose bottom face is the bed. */
    private static void tube(List<Box> out, double[] s, double[] r, double[] u, double[] f, double f0, double f1, int piece) {
        double apothem = TUBE_R * Math.cos(Math.PI / 8), chord = 2 * TUBE_R * Math.sin(Math.PI / 8);
        double cy = apothem; // the bottom face lies at height 0
        for (int k = 0; k < 8; k++) {
            double phi = Math.toRadians(-90 + 45 * k);
            double nx = Math.cos(phi), ny = Math.sin(phi);
            double[] o = point(s, r, u, nx * apothem, cy + ny * apothem);
            double[] tr = add(scale(r, -ny), scale(u, nx));   // along the face
            double[] tu = add(scale(r, -nx), scale(u, -ny));  // toward the tube's centre
            out.add(new Box(o, tr, tu, f, new double[]{-chord / 2 - 0.03, -T, f0}, new double[]{chord / 2 + 0.03, 0, f1}, k == 0 ? Role.BED : Role.SHELL, piece));
        }
    }

    private static double[] point(double[] s, double[] r, double[] u, double x, double y) {
        return new double[]{s[0] + r[0] * x + u[0] * y, s[1] + r[1] * x + u[1] * y, s[2] + r[2] * x + u[2] * y};
    }

    static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static double[] norm(double[] a) {
        double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        return new double[]{a[0] / l, a[1] / l, a[2] / l};
    }

    private static double[] scale(double[] a, double k) {
        return new double[]{a[0] * k, a[1] * k, a[2] * k};
    }

    private static double[] add(double[] a, double[] b) {
        return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }
}
