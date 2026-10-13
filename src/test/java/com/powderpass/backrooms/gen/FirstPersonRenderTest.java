package com.powderpass.backrooms.gen;

import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.display.Model;
import com.powderpass.backrooms.display.ModelLibrary;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * A first-person view of a level, ray-cast from the real generator output: the painted blocks, every display
 * model (fixtures, surface details, props) built by the real {@link ModelLibrary}, and Minecraft-style block light
 * propagated from the invisible LIGHT blocks. Textures are procedural approximations, not Minecraft's.
 * Off by default; run with {@code RENDER_VIEWS=<dir> gradle test --tests '*FirstPersonRenderTest'}.
 */
class FirstPersonRenderTest {
    private static final long SEED = 0x5EEDBACCL;
    private static final int W = 1280, H = 720;
    private static final Map<Material, int[]> RGB = new EnumMap<>(Material.class);

    static {
        c(Material.END_STONE, 0xDCDEA3); c(Material.SMOOTH_SANDSTONE, 0xD9CC93); c(Material.BIRCH_PLANKS, 0xC9B77E);
        c(Material.STRIPPED_BIRCH_WOOD, 0xC7B07A); c(Material.YELLOW_TERRACOTTA, 0xBA8524); c(Material.BROWN_TERRACOTTA, 0x4D3323);
        c(Material.BONE_BLOCK, 0xE2DDC8); c(Material.STONE, 0x7D7D7D); c(Material.WHITE_CONCRETE, 0xCFD5D6);
        c(Material.LIGHT_GRAY_CONCRETE, 0x7D7D73); c(Material.GRAY_CONCRETE, 0x36393D); c(Material.BLACK_CONCRETE, 0x08090D);
        c(Material.TERRACOTTA, 0x985E43); c(Material.WHITE_TERRACOTTA, 0xD1B2A1); c(Material.BROWN_WOOL, 0x724728);
        c(Material.YELLOW_WOOL, 0xF8C527); c(Material.GRAY_WOOL, 0x3E4447); c(Material.BLACK_WOOL, 0x141519);
        c(Material.DARK_OAK_PLANKS, 0x42301A); c(Material.OAK_PLANKS, 0xA2834F); c(Material.SPRUCE_PLANKS, 0x73552F);
        c(Material.IRON_BLOCK, 0xD8D8D8); c(Material.RED_CONCRETE, 0x8E2020); c(Material.GREEN_CONCRETE, 0x495B24);
        c(Material.WATER, 0x3F76E4); c(Material.BEDROCK, 0x333333); c(Material.LIGHT_GRAY_WOOL, 0x8E8E86);
        c(Material.POLISHED_ANDESITE, 0x848685); c(Material.QUARTZ_BLOCK, 0xEBE5DE); c(Material.SMOOTH_QUARTZ, 0xEBE5DE);
    }

    private static void c(Material m, int rgb) {
        RGB.put(m, new int[]{(rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255});
    }

    private static int[] rgb(Material m) {
        return RGB.getOrDefault(m, new int[]{150, 150, 150});
    }

    @BeforeAll
    static void mock() {
        MockBukkit.mock();
    }

    @AfterAll
    static void unmock() {
        MockBukkit.unmock();
    }

    /** One display box: world->local (unit cube) transform, material, emissive level or -1. */
    private record Box(Matrix4f inv, int[] col, int glow, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
    }

    /** The pack's real textures for custom blocks (pack/java/assets/backrooms/textures/block), cached. */
    private static final Map<String, BufferedImage> TEX = new java.util.concurrent.ConcurrentHashMap<>();

    private static BufferedImage tex(String name) {
        return TEX.computeIfAbsent(name, n -> {
            try {
                return ImageIO.read(new File("pack/java/assets/backrooms/textures/block/" + n + ".png"));
            } catch (java.io.IOException e) {
                return null;
            }
        });
    }

    private static int[] rgbOf(org.bukkit.block.data.BlockData d) {
        CustomBlocks.Def c = CustomBlocks.of(d);
        if (c != null) return new int[]{(c.rgb() >> 16) & 255, (c.rgb() >> 8) & 255, c.rgb() & 255};
        return rgb(d.getMaterial());
    }

    @Test
    void renderLevel0() throws Exception {
        String out = System.getenv("RENDER_VIEWS");
        Assumptions.assumeTrue(out != null && !out.isBlank(), "set RENDER_VIEWS=<dir>");
        LevelGenerator g = Generators.create(Level.LEVEL_0, SEED, GenSettings.defaults());
        var l0 = (com.powderpass.backrooms.gen.levels.Level0Generator) g;
        // one viewpoint in the nearest region of each kind of space, plus one ordinary room further out
        java.util.Map<com.powderpass.backrooms.gen.levels.Level0Generator.Zone, int[]> probes = new java.util.LinkedHashMap<>();
        for (int r = 0; r < 12 && probes.size() < 4; r++) {
            for (int rx = -r; rx <= r; rx++) for (int rz = -r; rz <= r; rz++) {
                if (Math.max(Math.abs(rx), Math.abs(rz)) != r) continue;
                var z = l0.zoneOfCell(rx * g.R, rz * g.R);
                probes.putIfAbsent(z, new int[]{rx * g.S * g.R + g.S * g.R / 2 + 4, rz * g.S * g.R + g.S * g.R / 2 + 4});
            }
        }
        for (var e : probes.entrySet()) {
            Scene s = new Scene(g, e.getValue()[0], e.getValue()[1]);
            BufferedImage img = s.render();
            File f = new File(out, "level0-" + e.getKey().name().toLowerCase(java.util.Locale.ROOT) + ".png");
            f.getParentFile().mkdirs();
            ImageIO.write(img, "png", f);
            System.out.println("wrote " + f.getAbsolutePath() + " " + s.note);
        }
    }

    @Test
    void renderTexturedLevels() throws Exception {
        String out = System.getenv("RENDER_VIEWS");
        Assumptions.assumeTrue(out != null && !out.isBlank(), "set RENDER_VIEWS=<dir>");
        for (Level level : new Level[]{Level.POOLROOMS, Level.LEVEL_4}) {
            LevelGenerator g = Generators.create(level, SEED, GenSettings.defaults());
            Scene s = new Scene(g, 0, 0);
            s.label = level.displayName();
            File f = new File(out, level.key() + "-view.png");
            ImageIO.write(s.render(), "png", f);
            System.out.println("wrote " + f.getAbsolutePath() + " " + s.note);
        }
    }

    private static final class Scene {
        String label = "Level 0";
        final LevelGenerator g;
        final Area a;
        final int x0, z0, x1, z1, y0, y1;
        final float[][][] light;
        final List<Box> boxes = new ArrayList<>();
        double ex, ey, ez, yaw, pitch;
        String note;

        Scene(LevelGenerator g, int px, int pz) {
            this.g = g;
            int[] spot = g.safeSpot(px, pz, 0);
            int r = 40;
            x0 = spot[0] - r;
            z0 = spot[2] - r;
            x1 = spot[0] + r;
            z1 = spot[2] + r;
            a = new Area(g, x0, z0, x1, z1);
            y0 = g.floorY[0] - 2;
            y1 = g.floorY[0] + 26;
            light = new float[x1 - x0][y1 - y0][z1 - z0];
            propagateLight();
            for (int cx = Math.floorDiv(x0, 16); cx <= Math.floorDiv(x1 - 1, 16); cx++) {
                for (int cz = Math.floorDiv(z0, 16); cz <= Math.floorDiv(z1 - 1, 16); cz++) {
                    for (DecorSpec d : g.decorations(cx, cz)) addModel(d);
                }
            }
            // eye at the safe spot, looking down the longest open line
            ex = spot[0] + 0.5;
            ey = spot[1] + 1.62;
            ez = spot[2] + 0.5;
            int best = -1;
            double bestYaw = 0;
            for (int i = 0; i < 16; i++) {
                double yw = i * Math.PI / 8;
                int d = 0;
                while (d < 38 && passable((int) Math.floor(ex - Math.sin(yw) * d), (int) Math.floor(ey), (int) Math.floor(ez + Math.cos(yw) * d))) d++;
                if (d > best) {
                    best = d;
                    bestYaw = yw;
                }
            }
            yaw = bestYaw;
            pitch = Math.toRadians(-4);
            note = "eye " + (int) ex + "," + (int) ey + "," + (int) ez + " zone " + g.zoneName(0, g.cellOf((int) ex), g.cellOf((int) ez)) + " view " + best;
        }

        Material type(int x, int y, int z) {
            return a.type(x, y, z);
        }

        boolean passable(int x, int y, int z) {
            Material m = type(x, y, z);
            return Mats.passable(m) || m == Material.BARRIER;
        }

        boolean opaque(int x, int y, int z) {
            Material m = type(x, y, z);
            return !(Mats.passable(m) || m == Material.BARRIER || m == Material.WATER);
        }

        /** Minecraft block light: flood fill from LIGHT blocks, minus one per block through non-opaque space. */
        void propagateLight() {
            ArrayDeque<int[]> q = new ArrayDeque<>();
            for (int x = x0; x < x1; x++) for (int y = y0; y < y1; y++) for (int z = z0; z < z1; z++) {
                B b = a.get(x, y, z);
                if (b.mat == Material.LIGHT) {
                    int lv = 15;
                    int i = b.state.indexOf("level=");
                    if (i >= 0) lv = Integer.parseInt(b.state.substring(i + 6).replaceAll("[^0-9].*", ""));
                    light[x - x0][y - y0][z - z0] = lv;
                    q.add(new int[]{x, y, z});
                }
            }
            int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
            while (!q.isEmpty()) {
                int[] p = q.poll();
                float lv = light[p[0] - x0][p[1] - y0][p[2] - z0];
                if (lv <= 1) continue;
                for (int[] d : dirs) {
                    int x = p[0] + d[0], y = p[1] + d[1], z = p[2] + d[2];
                    if (x < x0 || x >= x1 || y < y0 || y >= y1 || z < z0 || z >= z1 || opaque(x, y, z)) continue;
                    if (light[x - x0][y - y0][z - z0] < lv - 1) {
                        light[x - x0][y - y0][z - z0] = lv - 1;
                        q.add(new int[]{x, y, z});
                    }
                }
            }
        }

        float lightAt(double x, double y, double z) {
            // trilinear over block centres, ignoring opaque cells (smooth lighting, roughly)
            double fx = x - 0.5, fy = y - 0.5, fz = z - 0.5;
            int ix = (int) Math.floor(fx), iy = (int) Math.floor(fy), iz = (int) Math.floor(fz);
            double tx = fx - ix, ty = fy - iy, tz = fz - iz, sum = 0, wsum = 0;
            for (int dx = 0; dx <= 1; dx++) for (int dy = 0; dy <= 1; dy++) for (int dz = 0; dz <= 1; dz++) {
                int X = ix + dx, Y = iy + dy, Z = iz + dz;
                if (X < x0 || X >= x1 || Y < y0 || Y >= y1 || Z < z0 || Z >= z1 || opaque(X, Y, Z)) continue;
                double w = (dx == 1 ? tx : 1 - tx) * (dy == 1 ? ty : 1 - ty) * (dz == 1 ? tz : 1 - tz);
                sum += w * light[X - x0][Y - y0][Z - z0];
                wsum += w;
            }
            return wsum > 1e-6 ? (float) (sum / wsum) : 0;
        }

        void addModel(DecorSpec d) {
            Model m;
            try {
                m = ModelLibrary.build(d.model, d.seed);
            } catch (RuntimeException ex) {
                return;
            }
            if (m == null) return;
            Matrix4f base = new Matrix4f().translate((float) d.x, (float) d.y, (float) d.z)
                    .rotateY((float) Math.toRadians(-d.yaw)).rotateX((float) Math.toRadians(d.pitch)).rotateZ((float) Math.toRadians(d.roll));
            for (Part p : m.parts) {
                if (p.kind != Part.Kind.BLOCK || p.block == null) continue;
                Matrix4f world = new Matrix4f(base).mul(p.local);
                Vector3f lo = new Vector3f(Float.MAX_VALUE), hi = new Vector3f(-Float.MAX_VALUE);
                for (int i = 0; i < 8; i++) {
                    Vector3f c = world.transformPosition(new Vector3f(i & 1, (i >> 1) & 1, (i >> 2) & 1));
                    lo.min(c);
                    hi.max(c);
                }
                if (hi.x < x0 || lo.x > x1 || hi.z < z0 || lo.z > z1) continue;
                Matrix4f inv = new Matrix4f(world);
                if (Math.abs(inv.determinant()) < 1e-12) continue;
                inv.invert();
                boxes.add(new Box(inv, rgbOf(p.block), p.brightness == null ? -1 : p.brightness, lo.x, lo.y, lo.z, hi.x, hi.y, hi.z));
            }
        }

        BufferedImage render() {
            BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            double fov = Math.toRadians(70), th = Math.tan(fov / 2), aspect = (double) W / H;
            double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
            // Minecraft yaw: 0 = +Z; forward = (-sin, 0, cos)
            double[] fwd = {-sy * cp, sp, cy * cp}, right = {-cy, 0, -sy}, up = {sy * sp, cp, -cy * sp};
            int[] buf = new int[W * H];
            IntStream.range(0, H).parallel().forEach(py -> {
                for (int px = 0; px < W; px++) {
                    double u = (2 * (px + 0.5) / W - 1) * th * aspect, v = (1 - 2 * (py + 0.5) / H) * th;
                    double dx = fwd[0] + right[0] * u + up[0] * v, dy = fwd[1] + right[1] * u + up[1] * v, dz = fwd[2] + right[2] * u + up[2] * v;
                    double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    buf[py * W + px] = trace(dx / len, dy / len, dz / len);
                }
            });
            img.setRGB(0, 0, W, H, buf, 0, W);
            Graphics2D gr = img.createGraphics();
            gr.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            gr.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            gr.setColor(new Color(255, 255, 255, 150));
            gr.drawString(label + " — first-person ray-cast of the real generator output: the pack's real textures on custom blocks, display models, block light. Not Minecraft's renderer.", 12, H - 12);
            gr.dispose();
            return img;
        }

        int trace(double dx, double dy, double dz) {
            // nearest display box hit
            double bestT = 60;
            Box hitBox = null;
            double[] bn = null;
            for (Box b : boxes) {
                double[] r = rayBox(b, dx, dy, dz, bestT);
                if (r != null && r[0] < bestT) {
                    bestT = r[0];
                    hitBox = b;
                    bn = r;
                }
            }
            // voxel DDA
            int X = (int) Math.floor(ex), Y = (int) Math.floor(ey), Z = (int) Math.floor(ez);
            int stX = dx > 0 ? 1 : -1, stY = dy > 0 ? 1 : -1, stZ = dz > 0 ? 1 : -1;
            double tdX = Math.abs(1 / dx), tdY = Math.abs(1 / dy), tdZ = Math.abs(1 / dz);
            double tmX = ((dx > 0 ? X + 1 - ex : ex - X)) * tdX, tmY = ((dy > 0 ? Y + 1 - ey : ey - Y)) * tdY, tmZ = ((dz > 0 ? Z + 1 - ez : ez - Z)) * tdZ;
            double t = 0;
            int face = -1;
            double tint = 1;
            double[] waterTint = {1, 1, 1};
            while (t < bestT) {
                if (X < x0 || X >= x1 || Z < z0 || Z >= z1 || Y < y0 || Y >= y1) break;
                Material m = type(X, Y, Z);
                if (m == Material.WATER) {
                    waterTint[0] *= 0.985; waterTint[1] *= 0.99; waterTint[2] *= 1.0;
                } else if (!(Mats.passable(m) || m == Material.BARRIER) && face >= 0) {
                    double hx = ex + dx * t, hy = ey + dy * t, hz = ez + dz * t;
                    int col = shadeBlock(m, a.get(X, Y, Z), face, hx, hy, hz, X, Y, Z, t);
                    return applyTint(col, waterTint);
                }
                if (tmX < tmY && tmX < tmZ) {
                    t = tmX; tmX += tdX; X += stX; face = stX > 0 ? 0 : 1;
                } else if (tmY < tmZ) {
                    t = tmY; tmY += tdY; Y += stY; face = stY > 0 ? 2 : 3;
                } else {
                    t = tmZ; tmZ += tdZ; Z += stZ; face = stZ > 0 ? 4 : 5;
                }
            }
            if (hitBox != null) {
                double hx = ex + dx * bestT, hy = ey + dy * bestT, hz = ez + dz * bestT;
                return applyTint(shadeBox(hitBox, bn, hx, hy, hz, bestT), waterTint);
            }
            return 0x050403;
        }

        static int applyTint(int c, double[] t) {
            int r = (int) (((c >> 16) & 255) * t[0]), g = (int) (((c >> 8) & 255) * t[1]), b = (int) ((c & 255) * t[2]);
            return (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
        }

        double[] rayBox(Box b, double dx, double dy, double dz, double maxT) {
            // AABB reject first
            double t0 = 0, t1 = maxT;
            double[] o = {ex, ey, ez}, d = {dx, dy, dz}, lo = {b.minX, b.minY, b.minZ}, hi = {b.maxX, b.maxY, b.maxZ};
            for (int i = 0; i < 3; i++) {
                if (Math.abs(d[i]) < 1e-12) {
                    if (o[i] < lo[i] || o[i] > hi[i]) return null;
                    continue;
                }
                double a1 = (lo[i] - o[i]) / d[i], a2 = (hi[i] - o[i]) / d[i];
                t0 = Math.max(t0, Math.min(a1, a2));
                t1 = Math.min(t1, Math.max(a1, a2));
                if (t0 > t1) return null;
            }
            Vector3f lo3 = b.inv.transformPosition(new Vector3f((float) ex, (float) ey, (float) ez));
            Vector3f ld = b.inv.transformDirection(new Vector3f((float) dx, (float) dy, (float) dz));
            double[] lo2 = {lo3.x, lo3.y, lo3.z}, ld2 = {ld.x, ld.y, ld.z};
            double tn = 0, tf = maxT;
            int axis = -1;
            double sign = 0;
            for (int i = 0; i < 3; i++) {
                if (Math.abs(ld2[i]) < 1e-12) {
                    if (lo2[i] < 0 || lo2[i] > 1) return null;
                    continue;
                }
                double a1 = (0 - lo2[i]) / ld2[i], a2 = (1 - lo2[i]) / ld2[i];
                double near = Math.min(a1, a2), far = Math.max(a1, a2);
                if (near > tn) {
                    tn = near;
                    axis = i;
                    sign = a1 < a2 ? -1 : 1;
                }
                tf = Math.min(tf, far);
                if (tn > tf) return null;
            }
            if (tn <= 1e-4 || axis < 0) return null;
            return new double[]{tn, axis, sign};
        }

        int shadeBox(Box b, double[] hit, double hx, double hy, double hz, double dist) {
            int[] c = b.col;
            if (b.glow >= 0) {
                double k = 0.35 + 0.65 * b.glow / 15.0;
                return pack(c[0] * k * 1.08, c[1] * k * 1.08, c[2] * k * 1.06);
            }
            // world normal of the local face
            Matrix4f world = new Matrix4f(b.inv).invert();
            Vector3f n = new Vector3f(hit[1] == 0 ? (float) hit[2] : 0, hit[1] == 1 ? (float) hit[2] : 0, hit[1] == 2 ? (float) hit[2] : 0);
            new Matrix4f(world).invert().transpose().transformDirection(n).normalize();
            double faceShade = Math.abs(n.y) > 0.7 ? (n.y > 0 ? 1.0 : 0.5) : Math.abs(n.x) > Math.abs(n.z) ? 0.6 : 0.8;
            double l = lightAt(hx + n.x * 0.3, hy + n.y * 0.3, hz + n.z * 0.3);
            double k = brightness(l) * faceShade;
            return warm(c, k, l);
        }

        int shadeBlock(Material m, B b, int face, double hx, double hy, double hz, int X, int Y, int Z, double dist) {
            // face: entered moving +x => hit the block's -x face (normal -x)
            double nx = 0, ny = 0, nz = 0, faceShade;
            switch (face) {
                case 0 -> { nx = -1; faceShade = 0.6; }
                case 1 -> { nx = 1; faceShade = 0.6; }
                case 2 -> { ny = -1; faceShade = 0.5; }
                case 3 -> { ny = 1; faceShade = 1.0; }
                case 4 -> { nz = -1; faceShade = 0.8; }
                default -> { nz = 1; faceShade = 0.8; }
            }
            double u, v;
            if (nx != 0) { u = hz - Math.floor(hz); v = hy - Math.floor(hy); }
            else if (ny != 0) { u = hx - Math.floor(hx); v = hz - Math.floor(hz); }
            else { u = hx - Math.floor(hx); v = hy - Math.floor(hy); }
            CustomBlocks.Def cd = CustomBlocks.of(b);
            BufferedImage ti = cd == null ? null : tex(cd.id());
            double tex;
            int[] c;
            if (ti != null) {
                // the real pack texture; side faces are drawn top-down
                int tw = ti.getWidth();
                int px = Math.min(tw - 1, (int) (u * tw)), py = Math.min(tw - 1, (int) ((ny != 0 ? v : 1 - v) * tw));
                int argb = ti.getRGB(px, py);
                c = new int[]{(argb >> 16) & 255, (argb >> 8) & 255, argb & 255};
                tex = 1;
                // emissive panel models: unshaded, full bright (block model light_emission 15)
                if (cd.id().startsWith("light_panel") && !cd.id().endsWith("_off")) return pack(c[0], c[1], c[2]);
            } else {
                tex = texture(m, ny != 0, u, v, X, Y, Z);
                c = rgb(m);
            }
            double l = lightAt(hx + nx * 0.5, hy + ny * 0.5, hz + nz * 0.5);
            double k = brightness(l) * faceShade * tex;
            return warm(c, k, l);
        }

        /**
         * Approximation of Minecraft's lightmap for block light only (no sky), at the default brightness setting
         * (gamma 0.5): the curve f/(4-3f), blended halfway toward 1-(1-b)^4, with a faint floor.
         */
        static double brightness(double level) {
            double f = Math.max(0, Math.min(15, level)) / 15.0;
            double b = f / (4 - 3 * f);
            double g = 1 - Math.pow(1 - b, 4);
            return 0.04 + 0.96 * (0.5 * b + 0.5 * g);
        }

        static double hash(int x, int y, int z, int s) {
            long h = x * 73856093L ^ y * 19349663L ^ z * 83492791L ^ s * 2654435761L;
            h ^= (h >>> 13);
            h *= 0x5bd1e995L;
            h ^= (h >>> 15);
            return (h & 0xffff) / 65535.0;
        }

        /** Procedural stand-ins for the block textures, at 16 texels per block. */
        static double texture(Material m, boolean horizontal, double u, double v, int X, int Y, int Z) {
            int tu = (int) (u * 16), tv = (int) (v * 16);
            double n = hash(X * 16 + tu, Y * 16 + tv, Z * 16 + tu * 3 + tv, m.ordinal());
            return switch (m) {
                case END_STONE -> n < 0.12 ? 0.86 : n > 0.93 ? 1.05 : 0.98 + n * 0.03;
                case BIRCH_PLANKS -> (tv % 4 == 0 ? 0.84 : 0.97 + n * 0.05) * (tu == (X * 7 + Y * 3) % 16 && tv % 4 != 0 ? 0.93 : 1);
                case STRIPPED_BIRCH_WOOD -> (tv % 5 == 0 ? 0.9 : 1.0) * (0.95 + n * 0.08);
                case BONE_BLOCK -> {
                    if (!horizontal) yield (tu % 4 == 1 ? 0.9 : 1.0) * (0.96 + n * 0.05);
                    double r = Math.hypot(tu - 7.5, tv - 7.5);
                    yield (r > 5 && r < 6.3 ? 0.84 : r < 2 ? 0.95 : 1.0) * (0.97 + n * 0.04);
                }
                case YELLOW_TERRACOTTA, BROWN_TERRACOTTA, TERRACOTTA -> 0.95 + n * 0.08;
                case SMOOTH_SANDSTONE -> 0.97 + n * 0.04;
                default -> 0.94 + n * 0.1;
            };
        }

        /** Block light in Minecraft is slightly warm: blue drops off more than red as it dims. */
        static int warm(int[] c, double k, double level) {
            double w = 1 - Math.max(0, Math.min(15, level)) / 15.0;
            return pack(c[0] * k * 1.04, c[1] * k * (1.0 - 0.06 * w), c[2] * k * (0.94 - 0.2 * w));
        }

        static int pack(double r, double g, double b) {
            return ((int) Math.min(255, Math.max(0, r)) << 16) | ((int) Math.min(255, Math.max(0, g)) << 8) | (int) Math.min(255, Math.max(0, b));
        }
    }
}
