package com.powderpass.backrooms.creature;

import com.powderpass.backrooms.display.Model;
import com.powderpass.backrooms.display.Part;
import org.bukkit.Material;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Renders creature display models exactly as built (every BlockDisplay box, its transform and glow), lit by one
 * soft lamp, for design review. The worn player's body is a generic stand-in (the real one wears their skin).
 * Off by default; run with {@code RENDER_CREATURES=<dir> gradle test --tests '*CreatureRenderTest'}.
 */
class CreatureRenderTest {
    private static final int W = 1200, H = 760;
    private static final Map<Material, int[]> RGB = new EnumMap<>(Material.class);

    static {
        c(Material.GRAY_TERRACOTTA, 0x3A2A24); c(Material.BONE_BLOCK, 0xD9D3BE); c(Material.BLACK_CONCRETE, 0x0B0C10);
        c(Material.PINK_TERRACOTTA, 0xA14E4E); c(Material.WHITE_CONCRETE, 0xEDEFEF); c(Material.GRAY_CONCRETE, 0x45484D);
        c(Material.LIGHT_GRAY_CONCRETE, 0x8C8C84); c(Material.WHITE_TERRACOTTA, 0xD1B2A1); c(Material.BLACK_WOOL, 0x15161B);
        c(Material.LIGHT_GRAY_TERRACOTTA, 0x876B62); c(Material.YELLOW_TERRACOTTA, 0xBA8524); c(Material.BROWN_WOOL, 0x6B4226);
        c(Material.BLUE_WOOL, 0x35399D); c(Material.CYAN_TERRACOTTA, 0x575B5B);
        c(Material.MUSHROOM_STEM, 0xCBC4B9); c(Material.RED_TERRACOTTA, 0x8F3D2E); c(Material.BLACK_TERRACOTTA, 0x251610);
        c(Material.NETHER_WART_BLOCK, 0x732A1E); c(Material.BLACK_STAINED_GLASS, 0x191919); c(Material.WHITE_STAINED_GLASS, 0xFFFFFF);
    }

    private static void c(Material m, int rgb) {
        RGB.put(m, new int[]{(rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255});
    }

    @BeforeAll
    static void mock() {
        MockBukkit.mock();
    }

    @AfterAll
    static void unmock() {
        MockBukkit.unmock();
    }

    /** {@code alpha} < 1 for stained glass (seen through, tinted). */
    private record Box(Matrix4f inv, Matrix4f world, int[] rgb, boolean glow, double alpha) {
    }

    private static List<Box> boxes(Model m, Matrix4f base) {
        return boxes(m, base, Map.of());
    }

    /** The model in a pose: bone name to pose matrix, composed exactly as {@link Body#pose} does in game. */
    private static List<Box> boxes(Model m, Matrix4f base, Map<String, Matrix4f> poses) {
        List<Box> out = new ArrayList<>();
        for (Part p : m.parts) {
            if (p.kind != Part.Kind.BLOCK || p.block == null) continue;
            Matrix4f local = p.name == null ? p.local : Body.compose(p.name, poses, p.local);
            Matrix4f w = new Matrix4f(base).mul(local);
            if (Math.abs(w.determinant()) < 1e-12) continue;
            Material mat = p.block.getMaterial();
            double alpha = mat == Material.BLACK_STAINED_GLASS ? 0.55 : mat == Material.WHITE_STAINED_GLASS ? 0.3 : 1;
            out.add(new Box(new Matrix4f(w).invert(), w, RGB.getOrDefault(mat, new int[]{150, 150, 150}), p.brightness != null, alpha));
        }
        return out;
    }

    /** A generic standing player (the plugin shows the victim's real body and skin), feet at {@code y}. */
    private static Model standingBody(double y) {
        Model m = new Model();
        m.push().translate(0, y, 0);
        m.box(Material.YELLOW_TERRACOTTA, -0.25, 1.5, -0.25, 0.5, 0.5, 0.5);      // head (skin)
        m.box(Material.BROWN_WOOL, -0.26, 1.85, -0.26, 0.52, 0.17, 0.52);         // hair
        m.box(Material.CYAN_TERRACOTTA, -0.25, 0.75, -0.125, 0.5, 0.75, 0.25);    // shirt
        m.box(Material.YELLOW_TERRACOTTA, -0.5, 0.75, -0.125, 0.25, 0.75, 0.25);  // arms
        m.box(Material.YELLOW_TERRACOTTA, 0.25, 0.75, -0.125, 0.25, 0.75, 0.25);
        m.box(Material.BLUE_WOOL, -0.25, 0, -0.125, 0.25, 0.75, 0.25);            // legs
        m.box(Material.BLUE_WOOL, 0, 0, -0.125, 0.25, 0.75, 0.25);
        m.pop();
        return m;
    }

    private static final TentacleRig.Ground FLOOR = new TentacleRig.Ground() {
        @Override
        public double groundY(double x, double fromY, double z, double depth) {
            return fromY >= 0 && fromY - depth <= 0 ? 0 : Double.NaN;
        }

        @Override
        public double ceilingY(double x, double fromY, double z, double range) {
            return 4.5;
        }
    };

    /** The tentacle rig as boxes, for a body hovering {@code hover} above the floor (facing +Z, yaw 0). */
    private static List<Box> rig(TentacleRig r, double hover, double ageTicks) {
        r.update(new org.joml.Vector3d(0, hover, 0), 1.8, 0, 4.5, ageTicks, 0);
        List<Box> out = new ArrayList<>();
        Matrix4f origin = new Matrix4f().translation(0, (float) (hover + 1.8), 0);
        for (int i = 0; i < r.computed.size(); i++) {
            Matrix4f w = new Matrix4f(origin).mul(r.computed.get(i));
            if (Math.abs(w.determinant()) < 1e-12) continue;
            Material mat = r.blockFor(i).getMaterial();
            out.add(new Box(new Matrix4f(w).invert(), w, RGB.getOrDefault(mat, new int[]{20, 20, 24}), false, 1));
        }
        out.addAll(boxes(standingBody(hover), new Matrix4f()));
        return out;
    }

    @Test
    void renderSkinwalker() throws Exception {
        String out = System.getenv("RENDER_CREATURES");
        Assumptions.assumeTrue(out != null && !out.isBlank(), "set RENDER_CREATURES=<dir>");
        new File(out).mkdirs();
        // the transformation, frame by frame (the same parameters Possession drives over 5.5 s)
        double[][] frames = { // tick, wound, emerge, plant, hover
                {28, 0.4, 0, 0, 0}, {44, 1, 0.55, 0, 0}, {62, 1, 1, 0.35, 0}, {84, 1, 1, 1, 0.45}, {110, 1, 1, 1, 0.72}};
        String[] names = {"1 the back tears", "2 tentacles force out", "3 reaching for the floor", "4 lifting the body", "5 carried"};
        BufferedImage strip = new BufferedImage(W / 2 * frames.length, H / 2 + 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D sg = strip.createGraphics();
        for (int k = 0; k < frames.length; k++) {
            TentacleRig r = new TentacleRig(5);
            r.settle(FLOOR, new org.joml.Vector3d(), 0);
            r.wound = frames[k][1];
            r.emerge = frames[k][2];
            r.plant = frames[k][3];
            BufferedImage img = render(rig(r, frames[k][4], frames[k][0]), 150, 14, 6.4, 1.3, names[k], "").img;
            sg.drawImage(img, k * W / 2, 60, W / 2, H / 2, null);
        }
        sg.setColor(new Color(230, 225, 205));
        sg.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        sg.drawString("Skinwalker transformation (" + TentacleRig.partCount() + " display parts riding the victim)", 20, 42);
        sg.dispose();
        write(strip, new File(out, "skinwalker-transformation.png"));
        TentacleRig r = new TentacleRig(5);
        r.settle(FLOOR, new org.joml.Vector3d(), 0);
        r.wound = r.emerge = r.plant = 1;
        render(rig(r, 0.72, 40), 145, 16, 6.2, 1.3, "Skinwalker — possessed (back)", "The victim's own body, hovering, carried by six tentacle legs from its back; smaller tentacles crawl over the torso.")
                .let(f -> write(f, new File(out, "skinwalker-possessed-back.png")));
        render(rig(r, 0.72, 44), 35, 10, 6.2, 1.3, "Skinwalker — possessed (front)", "From the front: the legs arch over the shoulders; the striking tentacle coils over the right shoulder.")
                .let(f -> write(f, new File(out, "skinwalker-possessed-front.png")));
        render(rig(r, 0.72, 48), 95, 6, 6.4, 1.3, "Skinwalker — possessed (side)", "About 4.5 blocks across, 3 tall; it fits under a 4-block ceiling.")
                .let(f -> write(f, new File(out, "skinwalker-possessed-side.png")));
        r.strike = 1.6;
        r.strikeAt.set(0, 1.4, 3.6);
        render(rig(r, 0.72, 52), 70, 10, 6.6, 1.3, "Skinwalker — tentacle strike", "The striking tentacle lashes at someone who stood still too long.")
                .let(f -> write(f, new File(out, "skinwalker-strike.png")));
        // disguises: what it looks like before it reaches anyone
        List<Box> line = new ArrayList<>();
        line.addAll(boxes(Bodies.faceling(), new Matrix4f().translate(-1.6f, 0, 0)));
        line.addAll(boxes(Bodies.watcher(), new Matrix4f().translate(0, 0, 0)));
        line.addAll(boxes(Bodies.hound(), new Matrix4f().translate(1.6f, 0, 0).rotateY((float) Math.toRadians(-30))));
        render(line, 0, 6, 5.6, 1.1, "Skinwalker — borrowed shapes", "Before it reaches anyone it wears another creature of the level: a Faceling, a Watcher, a Hound (or a Smiler).")
                .let(f -> write(f, new File(out, "skinwalker-disguises.png")));
    }

    /** One sheet per creature: front three-quarter, side, and an action pose from {@link Poses}. */
    @Test
    void renderAll() throws Exception {
        String out = System.getenv("RENDER_CREATURES");
        Assumptions.assumeTrue(out != null && !out.isBlank(), "set RENDER_CREATURES=<dir>");
        String tag = System.getenv().getOrDefault("RENDER_TAG", "");
        new File(out).mkdirs();
        record Spec(String name, Model model, double look, double dist, String actionName, Map<String, Matrix4f> action, double actionYaw) {
        }
        Spec[] specs = {
                new Spec("smiler", Bodies.smiler(), 1.5, 3.4, "lunging: mouth open, arms out of the dark, more eyes", Poses.smiler(1, 1.25, 1, 1, 12, false, 1), 20),
                new Spec("faceling", Bodies.faceling(), 1.6, 3.6, "stared at too long", Poses.faceling(-38, 1, 1, 0), 10),
                new Spec("hound", Bodies.hound(), 0.55, 3.4, "rearing before the lunge", Poses.hound(0, 0, 1, 1, 0, 0.15), 60),
                new Spec("drifter", Bodies.drifter(), 0.4, 3.2, "reaching, mid-stroke", Poses.drifter(1.2, 0.4, 1, 1), 35),
                new Spec("watcher", Bodies.watcher(), 1.15, 4.0, "moving (edges smear)", Poses.watcherHaze(7, false), 25),
                new Spec("bacteria", Bodies.bacteria(), 1.7, 5.0, "hunting: glitching mid-stride", Poses.bacteria(0.9, 1, 12345L, 0, 0.2), 25),
                new Spec("bacteria-loom", Bodies.bacteria(), 1.7, 5.0, "leaning over someone who keeps still", Poses.bacteria(0, 0, 0, 1, 0.3), 40)};
        for (Spec s : specs) {
            List<Box> rest = new ArrayList<>(boxes(s.model, new Matrix4f()));
            List<Box> act = new ArrayList<>(boxes(s.model, new Matrix4f(), s.action));
            BufferedImage[] panels = {render(rest, 30, 12, s.dist, s.look, "rest, front", "").img,
                    render(rest, 95, 6, s.dist, s.look, "rest, side", "").img,
                    render(act, s.actionYaw, 10, s.dist, s.look, s.actionName, "").img};
            int pw = W / 2, ph = H / 2;
            BufferedImage sheet = new BufferedImage(pw * 3, ph + 70, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = sheet.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            for (int k = 0; k < 3; k++) g.drawImage(panels[k], k * pw, 70, pw, ph, null);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(230, 225, 205));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
            g.drawString(s.name + "  (" + countParts(s.model) + " display parts)", 20, 45);
            g.dispose();
            write(sheet, new File(out, tag + s.name + ".png"));
        }
    }

    private static int countParts(Model m) {
        return m.parts.size();
    }

    private static void write(BufferedImage img, File f) {
        try {
            ImageIO.write(img, "png", f);
            System.out.println("wrote " + f.getAbsolutePath());
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Small helper so a rendered image can be chained into a write. */
    private record Img(BufferedImage img) {
        void let(java.util.function.Consumer<BufferedImage> c) {
            c.accept(img);
        }
    }

    private static Img render(List<Box> boxes, double yawDeg, double pitchDeg, double dist, double lookY, String title, String sub) {
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
        double[] target = {0, lookY, 0};
        double[] eye = {target[0] + Math.sin(yaw) * Math.cos(pitch) * dist, target[1] + Math.sin(pitch) * dist, target[2] + Math.cos(yaw) * Math.cos(pitch) * dist};
        double[] fwd = norm(new double[]{target[0] - eye[0], target[1] - eye[1], target[2] - eye[2]});
        double[] right = norm(cross(fwd, new double[]{0, 1, 0}));
        double[] up = cross(right, fwd);
        double th = Math.tan(Math.toRadians(50) / 2), aspect = (double) W / H;
        double[] lamp = {1.5, 4.0, 2.5};
        int[] buf = new int[W * H];
        IntStream.range(0, H).parallel().forEach(py -> {
            for (int px = 0; px < W; px++) {
                double u = (2 * (px + 0.5) / W - 1) * th * aspect, v = (1 - 2 * (py + 0.5) / H) * th;
                double[] d = norm(new double[]{fwd[0] + right[0] * u + up[0] * v, fwd[1] + right[1] * u + up[1] * v, fwd[2] + right[2] * u + up[2] * v});
                buf[py * W + px] = trace(boxes, eye, d, lamp);
            }
        });
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, W, H, buf, 0, W);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(230, 225, 205));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
        g.drawString(title, 24, 44);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        g.setColor(new Color(170, 165, 150));
        g.drawString(sub, 24, 70);
        g.drawString("Rendered from the plugin's actual display-entity model (boxes, transforms, glow). The player body is a generic stand-in.", 24, H - 18);
        g.dispose();
        return new Img(img);
    }

    private static int trace(List<Box> boxes, double[] o, double[] d, double[] lamp) {
        // every hit along the ray, front to back; glass tints what is behind it
        List<double[]> hits = new ArrayList<>();
        for (int i = 0; i < boxes.size(); i++) {
            double[] r = hitBox(boxes.get(i), o, d);
            if (r != null) hits.add(new double[]{r[0], r[1], r[2], r[3], i});
        }
        hits.sort((x, y) -> Double.compare(x[0], y[0]));
        double[] acc = {0, 0, 0};
        double through = 1;
        for (double[] h : hits) {
            Box b = boxes.get((int) h[4]);
            int[] c = b.rgb;
            double[] col;
            if (b.glow) {
                col = new double[]{c[0], c[1], c[2]};
            } else {
                double[] p = {o[0] + d[0] * h[0], o[1] + d[1] * h[0], o[2] + d[2] * h[0]};
                double[] n = {h[1], h[2], h[3]};
                double l = lightAt(p, n, lamp) * (shadowed(boxes, new double[]{p[0] + n[0] * 0.01, p[1] + n[1] * 0.01, p[2] + n[2] * 0.01}, lamp) ? 0.45 : 1);
                col = new double[]{c[0] * l + 4, c[1] * l + 4, c[2] * l + 4};
            }
            for (int k = 0; k < 3; k++) acc[k] += col[k] * b.alpha * through;
            through *= 1 - b.alpha;
            if (through < 0.02) return rgb(acc[0], acc[1], acc[2]);
        }
        int bg = background(boxes, o, d, lamp);
        return rgb(acc[0] + ((bg >> 16) & 255) * through, acc[1] + ((bg >> 8) & 255) * through, acc[2] + (bg & 255) * through);
    }

    private static int background(List<Box> boxes, double[] o, double[] d, double[] lamp) {
        double tf = d[1] < 0 ? -o[1] / d[1] : 1e9;
        if (tf < 1e8) {
            double x = o[0] + d[0] * tf, z = o[2] + d[2] * tf;
            double l = lightAt(new double[]{x, 0, z}, new double[]{0, 1, 0}, lamp);
            boolean grid = Math.abs(x - Math.round(x)) < 0.02 || Math.abs(z - Math.round(z)) < 0.02;
            double shadow = shadowed(boxes, new double[]{x, 0.001, z}, lamp) ? 0.35 : 1;
            int base = grid ? 120 : 150;
            return rgb(base * 0.9 * l * shadow + 6, base * 0.8 * l * shadow + 5, base * 0.45 * l * shadow + 3);
        }
        return rgb(10, 9, 7);
    }

    private static boolean shadowed(List<Box> boxes, double[] p, double[] lamp) {
        double[] d = {lamp[0] - p[0], lamp[1] - p[1], lamp[2] - p[2]};
        double len = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        d = new double[]{d[0] / len, d[1] / len, d[2] / len};
        for (Box b : boxes) {
            if (b.alpha < 1) continue;
            double[] r = hitBox(b, p, d);
            if (r != null && r[0] > 1e-3 && r[0] < len) return true;
        }
        return false;
    }

    private static double lightAt(double[] p, double[] n, double[] lamp) {
        double[] l = norm(new double[]{lamp[0] - p[0], lamp[1] - p[1], lamp[2] - p[2]});
        double ndl = Math.max(0, n[0] * l[0] + n[1] * l[1] + n[2] * l[2]);
        return 0.22 + 0.95 * ndl;
    }

    private static double[] hitBox(Box b, double[] o, double[] d) {
        Vector3f lo = b.inv.transformPosition(new Vector3f((float) o[0], (float) o[1], (float) o[2]));
        Vector3f ld = b.inv.transformDirection(new Vector3f((float) d[0], (float) d[1], (float) d[2]));
        double[] O = {lo.x, lo.y, lo.z}, D = {ld.x, ld.y, ld.z};
        double tn = -1e9, tf = 1e9;
        int axis = -1;
        double sign = 0;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(D[i]) < 1e-12) {
                if (O[i] < 0 || O[i] > 1) return null;
                continue;
            }
            double a = -O[i] / D[i], c = (1 - O[i]) / D[i];
            double near = Math.min(a, c), far = Math.max(a, c);
            if (near > tn) {
                tn = near;
                axis = i;
                sign = a < c ? -1 : 1;
            }
            tf = Math.min(tf, far);
            if (tn > tf) return null;
        }
        if (tn <= 1e-4 || axis < 0) return null;
        Vector3f n = new Vector3f(axis == 0 ? (float) sign : 0, axis == 1 ? (float) sign : 0, axis == 2 ? (float) sign : 0);
        new Matrix4f(b.inv).transpose().transformDirection(n).normalize();
        return new double[]{tn, n.x, n.y, n.z};
    }

    private static double[] norm(double[] v) {
        double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new double[]{v[0] / l, v[1] / l, v[2] / l};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static int rgb(double r, double g, double b) {
        return ((int) Math.min(255, Math.max(0, r)) << 16) | ((int) Math.min(255, Math.max(0, g)) << 8) | (int) Math.min(255, Math.max(0, b));
    }
}
