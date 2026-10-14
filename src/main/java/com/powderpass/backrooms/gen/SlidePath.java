package com.powderpass.backrooms.gen;

import com.powderpass.backrooms.util.Rng;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The centre line of a water slide's bed, as a pure function of a seed: straights, banked turns, helixes and short
 * steep drops, one point per block of run, starting at the origin heading +Z and ending exactly {@code drop} blocks
 * lower. It never comes close enough to itself to clip (a pass that would is rerolled), stays within a footprint
 * that grows with the drop, and always ends on a gentle runout so riders are thrown out level.
 *
 * <p>The same path drives everything: the display chute ({@code display.SlideModels}), the tunnels the generator bores
 * through walls and floors, and the ride ({@code world.SlideRides}), which moves the rider along it with momentum.</p>
 */
public final class SlidePath {
    public enum Style {
        /** a half-pipe open to the sky */
        OPEN,
        /** arched ribs over the chute, open between them */
        HALF,
        /** a closed tube of tinted glass */
        ENCLOSED;

        public static Style parse(String s) {
            if (s == null) return null;
            try {
                return valueOf(s.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public final long seed;
    public final Style style;
    public final boolean wet;
    public final double drop;
    /** Bed centre line in local space (x, y, z): pts[0] = origin, heading +Z, the last point at y = -drop. */
    public final double[][] pts;
    /** Lean into each turn, degrees (+ = the chute's right side, local -X, is lower). */
    public final double[] bank;
    /** Horizontal reach from the origin (for bounding boxes). */
    public final double reach;

    private SlidePath(long seed, Style style, boolean wet, double drop, double[][] pts, double[] bank) {
        this.seed = seed;
        this.style = style;
        this.wet = wet;
        this.drop = drop;
        this.pts = pts;
        this.bank = bank;
        double r = 0;
        for (double[] p : pts) r = Math.max(r, Math.hypot(p[0], p[2]));
        this.reach = r;
    }

    /** Footprint radius for a drop: short fun slides stay tight, storey-high ones may wander further. */
    public static double footprint(double drop) {
        return Math.max(7, Math.min(22, 5 + drop * 0.5));
    }

    public static SlidePath generate(long seed, Style style, boolean wet, double drop) {
        double d = Math.max(3, Math.min(80, drop));
        for (int attempt = 0; attempt < 40; attempt++) {
            List<double[]> pts = new ArrayList<>();
            List<Double> bank = new ArrayList<>();
            if (walk(Rng.hash(seed, attempt), d, wet, pts, bank) && clear(pts)) return build(seed, style, wet, d, pts, bank);
        }
        List<double[]> pts = new ArrayList<>();
        List<Double> bank = new ArrayList<>();
        helixFallback(d, pts, bank);
        return build(seed, style, wet, d, pts, bank);
    }

    private static SlidePath build(long seed, Style style, boolean wet, double drop, List<double[]> pts, List<Double> bank) {
        double[][] p = pts.toArray(new double[0][]);
        double[] b = new double[bank.size()];
        // smooth the lean so it rolls in and out of turns instead of snapping
        for (int i = 0; i < b.length; i++) {
            double s = 0;
            int n = 0;
            for (int k = Math.max(0, i - 2); k <= Math.min(b.length - 1, i + 2); k++) {
                s += bank.get(k);
                n++;
            }
            b[i] = s / n;
        }
        return new SlidePath(seed, style, wet, drop, p, b);
    }

    // ------------------------------------------------------------------ the walk

    private static final class Walk {
        double x, y, z, heading; // heading 0 = +Z, positive turns toward +X
        final List<double[]> pts;
        final List<Double> bank;

        Walk(List<double[]> pts, List<Double> bank) {
            this.pts = pts;
            this.bank = bank;
            pts.add(new double[]{0, 0, 0});
            bank.add(0.0);
        }

        /** One block of run: turn by {@code dh} radians, descend {@code dy}. */
        void step(double dh, double dy, double lean) {
            heading += dh;
            x += Math.sin(heading);
            z += Math.cos(heading);
            y -= dy;
            pts.add(new double[]{x, y, z});
            bank.add(lean);
        }
    }

    private static boolean walk(long h, double drop, boolean wet, List<double[]> pts, List<Double> bank) {
        Walk w = new Walk(pts, bank);
        double limit = footprint(drop);
        // the launch: two gentle blocks off the platform
        w.step(0, 0.2, 0);
        w.step(0, 0.35, 0);
        double runout = 2.0; // descent kept for the final runout
        int move = 0;
        while (-w.y < drop - runout - 0.01) {
            long m = Rng.hash(h, move++);
            if (move > 60) return false;
            double left = drop - runout + w.y; // descent still to place before the runout
            double dist = Math.hypot(w.x, w.z);
            double toCentre = Math.atan2(-w.x, -w.z);
            double off = wrap(toCentre - w.heading);
            boolean far = dist > limit * 0.62;
            double r = Rng.unit(Rng.hash(m, 1));
            if (far && Math.abs(off) > 0.5) {
                // swing back toward the middle of the footprint
                turn(w, Math.signum(off), 3 + Rng.pick(Rng.hash(m, 2), 3), Math.min(Math.PI, Math.abs(off) + 0.3), 0.22, left);
            } else if (r < 0.32) {
                int len = 3 + Rng.pick(Rng.hash(m, 3), far ? 2 : 6);
                double grade = 0.22 + Rng.unit(Rng.hash(m, 4)) * (wet ? 0.3 : 0.25);
                for (int i = 0; i < len && left > 0.05; i++) {
                    double dy = Math.min(grade, left);
                    w.step(0, dy, 0);
                    left -= dy;
                }
            } else if (r < 0.66) {
                double side = Rng.chance(Rng.hash(m, 5), 0.5) ? 1 : -1;
                double ang = Math.toRadians(60 + Rng.pick(Rng.hash(m, 6), 5) * 30);
                turn(w, side, 3 + Rng.pick(Rng.hash(m, 7), 4), ang, 0.18 + Rng.unit(Rng.hash(m, 8)) * 0.14, left);
            } else if (r < 0.86 && left >= 8) {
                // a helix: tight, banked hard, 1 to 2 laps
                double side = Rng.chance(Rng.hash(m, 9), 0.5) ? 1 : -1;
                double laps = 1 + Rng.pick(Rng.hash(m, 10), 3) * 0.5;
                turn(w, side, 4, laps * Math.PI * 2, 0.27, left);
            } else if (left >= 3) {
                // a short steep drop
                int len = 2 + Rng.pick(Rng.hash(m, 11), 2);
                for (int i = 0; i < len && left > 0.05; i++) {
                    double dy = Math.min(0.85, left);
                    w.step(0, dy, 0);
                    left -= dy;
                }
            } else {
                double dy = Math.min(0.3, left);
                w.step(0, dy, 0);
            }
        }
        // the runout: ends exactly at -drop, gently
        double rest = drop + w.y;
        int n = Math.max(3, (int) Math.ceil(rest / 0.3));
        for (int i = 0; i < n; i++) w.step(0, rest / n, 0);
        w.y = -drop;
        pts.get(pts.size() - 1)[1] = -drop;
        for (double[] p : pts) if (Math.hypot(p[0], p[2]) > limit + 2) return false;
        return pts.size() <= maxPoints(drop); // long, lazy slides cost too many display entities
    }

    /** The most points a slide of this drop may have (about 2.4 blocks of run per block of drop, plus the ends). */
    static int maxPoints(double drop) {
        return (int) (24 + drop * 2.4);
    }

    /** An arc of radius {@code radius}, total angle {@code angle}, side +1 = right (+X) / -1 = left, at a grade. */
    private static void turn(Walk w, double side, double radius, double angle, double grade, double left) {
        int n = Math.max(2, (int) Math.round(angle * radius));
        double dh = side * angle / n;
        double lean = side * Math.min(35, 120 / radius);
        for (int i = 0; i < n && left > 0.05; i++) {
            double dy = Math.min(grade, left);
            w.step(dh, dy, lean);
            left -= dy;
        }
    }

    private static void helixFallback(double drop, List<double[]> pts, List<Double> bank) {
        Walk w = new Walk(pts, bank);
        w.step(0, 0.2, 0);
        double left = drop - 1.5;
        while (left > 0.05) {
            double dy = Math.min(0.45, left);
            w.step(0.25, dy, 30);
            left -= dy;
        }
        for (int i = 0; i < 3; i++) w.step(0, (drop + w.y) / (3 - i), 0);
        pts.get(pts.size() - 1)[1] = -drop;
    }

    private static double wrap(double a) {
        while (a > Math.PI) a -= Math.PI * 2;
        while (a < -Math.PI) a += Math.PI * 2;
        return a;
    }

    /** No two parts of the chute (more than a few blocks apart along it) pass within a chute's size of each other. */
    static boolean clear(List<double[]> pts) {
        for (int i = 0; i < pts.size(); i++) {
            for (int j = i + 7; j < pts.size(); j++) {
                double[] a = pts.get(i), b = pts.get(j);
                if (Math.hypot(a[0] - b[0], a[2] - b[2]) < 2.6 && Math.abs(a[1] - b[1]) < 3.4) return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ placement

    /** World position of local point {@code p} for a slide anchored at (ox, oy, oz) whose local +Z faces {@code yaw}. */
    public static double[] toWorld(double[] p, double ox, double oy, double oz, double yaw) {
        double t = Math.toRadians(-yaw), c = Math.cos(t), s = Math.sin(t);
        return new double[]{ox + p[0] * c + p[2] * s, oy + p[1], oz - p[0] * s + p[2] * c};
    }

    public double[][] world(double ox, double oy, double oz, double yaw) {
        double[][] out = new double[pts.length][];
        for (int i = 0; i < pts.length; i++) out[i] = toWorld(pts[i], ox, oy, oz, yaw);
        return out;
    }

    /** Arc length of the whole path. */
    public double length() {
        double l = 0;
        for (int i = 1; i < pts.length; i++) {
            l += Math.sqrt(sq(pts[i][0] - pts[i - 1][0]) + sq(pts[i][1] - pts[i - 1][1]) + sq(pts[i][2] - pts[i - 1][2]));
        }
        return l;
    }

    private static double sq(double d) {
        return d * d;
    }

    /** The asset / model id of this slide ({@code slide:<style>[:dry]:<drop>}). */
    public String id() {
        return "slide:" + style.id() + (wet ? "" : ":dry") + ":" + (int) Math.round(drop);
    }

    /** Parse an id like {@code slide:enclosed:dry:12} (missing parts take defaults). Null if it is not a slide id. */
    public static SlidePath fromId(String id, long seed) {
        String[] p = id.toLowerCase(Locale.ROOT).split(":");
        if (p.length == 0 || !p[0].equals("slide")) return null;
        Style st = Style.OPEN;
        boolean wet = true;
        double drop = 8;
        for (int i = 1; i < p.length; i++) {
            Style s = Style.parse(p[i]);
            if (s != null) st = s;
            else if (p[i].equals("dry")) wet = false;
            else if (p[i].equals("wet")) wet = true;
            else {
                try {
                    drop = Double.parseDouble(p[i]);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return generate(seed, st, wet, drop);
    }
}
