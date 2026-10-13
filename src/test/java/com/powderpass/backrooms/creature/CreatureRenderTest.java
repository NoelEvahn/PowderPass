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

    private record Box(Matrix4f inv, Matrix4f world, int[] rgb, boolean glow) {
    }

    private static List<Box> boxes(Model m, Matrix4f base) {
        List<Box> out = new ArrayList<>();
        for (Part p : m.parts) {
            if (p.kind != Part.Kind.BLOCK || p.block == null) continue;
            Matrix4f w = new Matrix4f(base).mul(p.local);
            if (Math.abs(w.determinant()) < 1e-12) continue;
            out.add(new Box(new Matrix4f(w).invert(), w, RGB.getOrDefault(p.block.getMaterial(), new int[]{150, 150, 150}), p.brightness != null));
        }
        return out;
    }

    /** A generic player body lying face down under the creature (the plugin uses the victim's real skin). */
    private static Model strandedBody() {
        Model m = new Model();
        m.push().translate(0, 0.3, 0.15).rotate('x', -90);
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

    @Test
    void renderSkinwalker() throws Exception {
        String out = System.getenv("RENDER_CREATURES");
        Assumptions.assumeTrue(out != null && !out.isBlank(), "set RENDER_CREATURES=<dir>");
        new File(out).mkdirs();
        List<Box> trueForm = new ArrayList<>(boxes(Bodies.skinwalker(), new Matrix4f()));
        trueForm.addAll(boxes(strandedBody(), new Matrix4f()));
        render(trueForm, 35, 22, 5.2, 0.8, "Skinwalker — true form", "Eight jointed legs, ridged spine, a split skull full of teeth and a cluster of pale eyes; the worn player hangs in its ribs.")
                .let(f -> write(f, new File(out, "skinwalker-true-form.png")));
        render(trueForm, 100, 8, 5.4, 0.8, "Skinwalker — true form, side", "Low and wide (about 3.5 blocks across); it scuttles at about 6 blocks per second.")
                .let(f -> write(f, new File(out, "skinwalker-true-form-side.png")));
        // disguises: what it looks like before it reaches anyone
        List<Box> line = new ArrayList<>();
        line.addAll(boxes(Bodies.faceling(), new Matrix4f().translate(-1.6f, 0, 0)));
        line.addAll(boxes(Bodies.watcher(), new Matrix4f().translate(0, 0, 0)));
        line.addAll(boxes(Bodies.hound(), new Matrix4f().translate(1.6f, 0, 0).rotateY((float) Math.toRadians(-30))));
        render(line, 0, 6, 5.6, 1.1, "Skinwalker — borrowed shapes", "Before it reaches anyone it wears another creature of the level: a Faceling, a Watcher, a Hound (or a Smiler).")
                .let(f -> write(f, new File(out, "skinwalker-disguises.png")));
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
        double best = 1e9;
        Box hit = null;
        double[] hn = null;
        for (Box b : boxes) {
            double[] r = hitBox(b, o, d);
            if (r != null && r[0] < best) {
                best = r[0];
                hit = b;
                hn = new double[]{r[1], r[2], r[3]};
            }
        }
        // floor plane y=0
        double tf = d[1] < 0 ? -o[1] / d[1] : 1e9;
        if (hit == null && tf < 1e8) {
            double x = o[0] + d[0] * tf, z = o[2] + d[2] * tf;
            double l = lightAt(new double[]{x, 0, z}, new double[]{0, 1, 0}, lamp);
            boolean grid = Math.abs(x - Math.round(x)) < 0.02 || Math.abs(z - Math.round(z)) < 0.02;
            double shadow = shadowed(boxes, new double[]{x, 0.001, z}, lamp) ? 0.35 : 1;
            int base = grid ? 120 : 150;
            return rgb(base * 0.9 * l * shadow + 6, base * 0.8 * l * shadow + 5, base * 0.45 * l * shadow + 3);
        }
        if (hit == null) return rgb(10, 9, 7);
        int[] c = hit.rgb;
        if (hit.glow) return rgb(c[0], c[1], c[2]);
        double[] p = {o[0] + d[0] * best, o[1] + d[1] * best, o[2] + d[2] * best};
        double l = lightAt(p, hn, lamp) * (shadowed(boxes, new double[]{p[0] + hn[0] * 0.01, p[1] + hn[1] * 0.01, p[2] + hn[2] * 0.01}, lamp) ? 0.45 : 1);
        return rgb(c[0] * l + 4, c[1] * l + 4, c[2] * l + 4);
    }

    private static boolean shadowed(List<Box> boxes, double[] p, double[] lamp) {
        double[] d = {lamp[0] - p[0], lamp[1] - p[1], lamp[2] - p[2]};
        double len = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        d = new double[]{d[0] / len, d[1] / len, d[2] / len};
        for (Box b : boxes) {
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
