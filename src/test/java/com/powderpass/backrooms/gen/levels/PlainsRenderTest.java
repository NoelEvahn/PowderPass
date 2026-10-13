package com.powderpass.backrooms.gen.levels;

import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.display.Model;
import com.powderpass.backrooms.display.ModelLibrary;
import com.powderpass.backrooms.display.Part;
import com.powderpass.backrooms.gen.B;
import com.powderpass.backrooms.gen.DecorSpec;
import com.powderpass.backrooms.gen.GenSettings;
import com.powderpass.backrooms.gen.Generators;
import com.powderpass.backrooms.gen.Mats;
import com.powderpass.backrooms.gen.MemoryCanvas;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

/**
 * First-person views of the Plains, ray-cast from the real generator output and the real display models under an
 * evening sky: low sun in the west, sky light that fades down a sinkhole, distance haze. Procedural colours, not
 * Minecraft's renderer. Off by default; run with {@code RENDER_VIEWS=<dir> gradle test --tests '*PlainsRenderTest'}.
 */
class PlainsRenderTest {
    private static final long SEED = 0x5EEDBACCL;
    private static final int W = 1280, H = 720;
    private static final int Y0 = PlainsGenerator.HOLE_BOTTOM - 8, Y1 = 150;
    private static final double MAX_T = 330;
    private static final Map<Material, int[]> RGB = new EnumMap<>(Material.class);
    /** Direction towards the low evening sun (west, slightly south). */
    private static final Vector3f SUN = new Vector3f(-1f, 0.16f, 0.32f).normalize();

    static {
        c(Material.GRASS_BLOCK, 0x7E9460); c(Material.MOSS_BLOCK, 0x5C7230); c(Material.COARSE_DIRT, 0x77553B);
        c(Material.DIRT, 0x866043); c(Material.ROOTED_DIRT, 0x90684C); c(Material.STONE, 0x7D7D7D); c(Material.TUFF, 0x6C6D66);
        c(Material.ANDESITE, 0x888888); c(Material.DEEPSLATE, 0x505052); c(Material.BEDROCK, 0x333333);
        c(Material.WHITE_CONCRETE, 0xCFD5D6); c(Material.TINTED_GLASS, 0x2E2A33); c(Material.SPRUCE_PLANKS, 0x73552F);
        c(Material.DEEPSLATE_TILES, 0x3A3A3E); c(Material.DEEPSLATE_TILE_STAIRS, 0x3A3A3E); c(Material.DEEPSLATE_TILE_SLAB, 0x3A3A3E);
        c(Material.SPRUCE_DOOR, 0x6A4D2A); c(Material.SPRUCE_STAIRS, 0x73552F); c(Material.YELLOW_TERRACOTTA, 0xBA8524);
        c(Material.SMOOTH_SANDSTONE, 0xD9CC93); c(Material.GRAY_CONCRETE, 0x36393D); c(Material.LIGHT_GRAY_CONCRETE, 0x7D7D73);
        c(Material.BLACK_CONCRETE, 0x101216); c(Material.LIGHT_BLUE_CONCRETE, 0x2389C6); c(Material.WATER, 0x3F76E4);
        c(Material.STONE_STAIRS, 0x7D7D7D); c(Material.ORANGE_CONCRETE, 0xE06101); c(Material.YELLOW_CONCRETE, 0xF0AF15);
        c(Material.BLUE_CONCRETE, 0x2C2E8F); c(Material.PINK_CONCRETE, 0xD5658E); c(Material.BROWN_WOOL, 0x724728);
        c(Material.BROWN_CONCRETE, 0x603B1F); c(Material.WHITE_TERRACOTTA, 0xD1B2A1); c(Material.RED_CONCRETE, 0x8E2020);
        c(Material.WHITE_WOOL, 0xE9ECEC); c(Material.WHITE_STAINED_GLASS, 0xDDE3E6); c(Material.GOLD_BLOCK, 0xF6D03D);
        c(Material.IRON_BLOCK, 0xD8D8D8); c(Material.BIRCH_PLANKS, 0xC9B77E); c(Material.DARK_OAK_PLANKS, 0x42301A);
        c(Material.QUARTZ_STAIRS, 0xEBE5DE); c(Material.STRIPPED_SPRUCE_LOG, 0x73593A); c(Material.GRAY_WOOL, 0x3E4447);
        c(Material.LIGHT_GRAY_WOOL, 0x8E8E86); c(Material.YELLOW_WOOL, 0xF8C527); c(Material.TERRACOTTA, 0x985E43);
        c(Material.BROWN_TERRACOTTA, 0x4D3323); c(Material.OAK_PLANKS, 0xA2834F);
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

    @Test
    void renderPlainsViews() throws Exception {
        String out = System.getenv("RENDER_VIEWS");
        Assumptions.assumeTrue(out != null && !out.isBlank(), "set RENDER_VIEWS=<dir>");
        PlainsGenerator g = (PlainsGenerator) Generators.create(Level.PLAINS, SEED, GenSettings.defaults());
        World w = new World(g);
        File dir = new File(out);
        dir.mkdirs();

        // 1. an empty field, looking towards the most giant objects
        int[] s = g.safeSpot(40, -30, 0);
        double bestYaw = 0, bestScore = -1;
        for (int i = 0; i < 24; i++) {
            double yaw = i * Math.PI / 12, score = 0;
            for (double[] c : giants(g, s[0], s[2], 260)) {
                double ang = Math.atan2(-(c[0] - s[0]), c[1] - s[2]);
                double diff = Math.abs(Math.atan2(Math.sin(ang - yaw), Math.cos(ang - yaw)));
                double dist = Math.hypot(c[0] - s[0], c[1] - s[2]);
                if (diff < Math.toRadians(30) && dist > 40) score += 1;
            }
            if (score > bestScore) {
                bestScore = score;
                bestYaw = yaw;
            }
        }
        write(dir, "plains-field.png", new View(w, s[0] + 0.5, s[1] + 1.62, s[2] + 0.5, bestYaw, Math.toRadians(6), "An empty field; giant objects in the distance"));

        // 2. a lone house, from its front yard
        int[] lone = null;
        int[] group = null;
        for (int r = 0; r < 30 && (lone == null || group == null); r++) {
            for (int cx = -r; cx <= r; cx++) for (int cz = -r; cz <= r; cz++) {
                if (Math.max(Math.abs(cx), Math.abs(cz)) != r || g.kind(cx, cz) != PlainsGenerator.K_HOUSE) continue;
                int near = 0;
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) if ((dx != 0 || dz != 0) && g.kind(cx + dx, cz + dz) == PlainsGenerator.K_HOUSE) near++;
                if (near == 0 && lone == null && g.house(cx, cz).variant() == PlainsGenerator.V_NORMAL) lone = new int[]{cx, cz};
                if (near >= 3 && group == null) group = new int[]{cx, cz};
            }
        }
        if (lone != null) write(dir, "plains-lone-house.png", houseView(g, w, lone[0], lone[1], 19, "A lone house in the grass"));
        if (group != null) {
            double sx = 0, sz = 0;
            int n = 0;
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                int cx = group[0] + dx, cz = group[1] + dz;
                if (g.kind(cx, cz) != PlainsGenerator.K_HOUSE) continue;
                PlainsGenerator.House hs = g.house(cx, cz);
                sx += cx * g.S + hs.bu() + hs.w() / 2.0;
                sz += cz * g.S + hs.bv() + hs.d() / 2.0;
                n++;
            }
            sx /= n;
            sz /= n;
            double ex = sx + 62, ez = sz - 50;
            double yaw = Math.atan2(-(sx - ex), sz - ez);
            write(dir, "plains-house-group.png", new View(w, ex, 64 + 14, ez, yaw, Math.toRadians(-9),
                    "A group of " + n + " houses dropped at odd angles, no street (seen from 12 blocks up)"));
        }

        // 3. a sinkhole from its rim, and looking down it
        PlainsGenerator.Hole hole = null;
        for (int r = 0; r < 12 && hole == null; r++) {
            for (int rx = -r; rx <= r && hole == null; rx++) for (int rz = -r; rz <= r && hole == null; rz++) {
                if (Math.max(Math.abs(rx), Math.abs(rz)) == r) hole = g.regionHole(rx, rz);
            }
        }
        if (hole != null) {
            // stand a few blocks back from the rim on the side facing the sun, looking across
            double ang = Math.PI * 0.85;
            double rim = PlainsGenerator.rim(hole, ang);
            double ex = hole.x() + Math.cos(ang) * (rim + 3), ez = hole.z() + Math.sin(ang) * (rim + 3);
            int top = 64 + g.lift((int) Math.floor(ex), (int) Math.floor(ez));
            double yawIn = Math.atan2(-(hole.x() - ex), hole.z() - ez);
            write(dir, "plains-sinkhole-rim.png", new View(w, ex, top + 1 + 1.62, ez, yawIn, Math.toRadians(-18), "A sinkhole, ~" + Math.round(2 * hole.r()) + " blocks across, from its rim"));
            double ex2 = hole.x() + Math.cos(ang) * (rim + 0.6), ez2 = hole.z() + Math.sin(ang) * (rim + 0.6);
            write(dir, "plains-sinkhole-down.png", new View(w, ex2, top + 1 + 1.62, ez2, yawIn, Math.toRadians(-62), "Looking down: it narrows to a shaft that falls out of the world"));
            double ex3 = hole.x() + Math.cos(ang) * (rim + 70), ez3 = hole.z() + Math.sin(ang) * (rim + 70);
            int top3 = 64 + g.lift((int) Math.floor(ex3), (int) Math.floor(ez3));
            write(dir, "plains-sinkhole-approach.png", new View(w, ex3, top3 + 1 + 1.62, ez3, yawIn, Math.toRadians(-2), "Walking up to a sinkhole"));
        }
    }

    private View houseView(PlainsGenerator g, World w, int cx, int cz, double back, String label) {
        PlainsGenerator.House hs = g.house(cx, cz);
        double hx = cx * g.S + hs.bu() + hs.w() / 2.0, hz = cz * g.S + hs.bv() + hs.d() / 2.0;
        // front direction per orientation: 0 north (-z), 1 west (-x), 2 south (+z), 3 east (+x)
        double[][] dir = {{0, -1}, {-1, 0}, {0, 1}, {1, 0}};
        double side = 0.35;
        double fx = dir[hs.o()][0] - dir[hs.o()][1] * side, fz = dir[hs.o()][1] + dir[hs.o()][0] * side;
        double len = Math.hypot(fx, fz);
        double ex = hx + fx / len * back, ez = hz + fz / len * back;
        int top = 64 + g.lift((int) Math.floor(ex), (int) Math.floor(ez));
        double yaw = Math.atan2(-(hx - ex), hz - ez);
        return new View(w, ex, top + 1 + 1.62, ez, yaw, Math.toRadians(4), label);
    }

    private static List<double[]> giants(PlainsGenerator g, int x, int z, int radius) {
        List<double[]> out = new ArrayList<>();
        for (int cx = g.cellOf(x - radius); cx <= g.cellOf(x + radius); cx++)
            for (int cz = g.cellOf(z - radius); cz <= g.cellOf(z + radius); cz++)
                if (g.kind(cx, cz) == PlainsGenerator.K_COLOSSAL) out.add(new double[]{cx * g.S + 16, cz * g.S + 16});
        return out;
    }

    private static void write(File dir, String name, View v) throws Exception {
        BufferedImage img = v.render();
        File f = new File(dir, name);
        ImageIO.write(img, "png", f);
        System.out.println("wrote " + f.getAbsolutePath() + " eye " + (int) v.ex + "," + (int) v.ey + "," + (int) v.ez);
    }

    // ------------------------------------------------------------------ world access

    /** Painted chunks, compact (palette indices), painted on demand from any render thread. */
    static final class World {
        final PlainsGenerator g;
        final Map<Long, short[]> chunks = new ConcurrentHashMap<>();
        final List<B> palette = new ArrayList<>();
        final Map<B, Short> index = new HashMap<>();
        volatile B[] pal = {B.AIR};
        final List<Box> boxes = new ArrayList<>();
        final Map<Long, List<Box>> grid = new HashMap<>();
        int bx0 = Integer.MAX_VALUE, bz0, bx1, bz1;

        World(PlainsGenerator g) {
            this.g = g;
            palette.add(B.AIR);
            index.put(B.AIR, (short) 0);
        }

        short[] chunk(int cx, int cz) {
            long k = ((long) cx << 32) ^ (cz & 0xffffffffL);
            short[] c = chunks.get(k);
            if (c != null) return c;
            MemoryCanvas mc = new MemoryCanvas(Y0, Y1);
            g.paint(mc, cx, cz);
            short[] arr = new short[16 * 16 * (Y1 - Y0)];
            synchronized (index) {
                for (int y = Y0; y < Y1; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                    B b = mc.get(x, y, z);
                    Short i = index.get(b);
                    if (i == null) {
                        i = (short) palette.size();
                        palette.add(b);
                        index.put(b, i);
                        pal = palette.toArray(new B[0]);
                    }
                    arr[((y - Y0) * 16 + z) * 16 + x] = i;
                }
            }
            chunks.putIfAbsent(k, arr);
            return chunks.get(k);
        }

        Material type(int x, int y, int z) {
            if (y < Y0 || y >= Y1) return Material.AIR;
            short[] c = chunk(x >> 4, z >> 4);
            return pal[c[((y - Y0) * 16 + (z & 15)) * 16 + (x & 15)]].mat;
        }

        /** Display boxes around the eye: everything big within range, small props and details only up close. */
        void loadModels(double ex, double ez) {
            int r = (int) MAX_T;
            for (int cx = (int) Math.floor((ex - r) / 16); cx <= (int) Math.floor((ex + r) / 16); cx++) {
                for (int cz = (int) Math.floor((ez - r) / 16); cz <= (int) Math.floor((ez + r) / 16); cz++) {
                    double d = Math.hypot(cx * 16 + 8 - ex, cz * 16 + 8 - ez);
                    if (d > r + 24) continue;
                    for (DecorSpec s : g.decorations(cx, cz)) {
                        if (s.tier > 0 && d > 48) continue;
                        addModel(s);
                    }
                }
            }
            for (Box b : boxes) {
                for (int gx = (int) Math.floor(b.minX / 8); gx <= (int) Math.floor(b.maxX / 8); gx++)
                    for (int gz = (int) Math.floor(b.minZ / 8); gz <= (int) Math.floor(b.maxZ / 8); gz++)
                        grid.computeIfAbsent(((long) gx << 32) ^ (gz & 0xffffffffL), k -> new ArrayList<>()).add(b);
            }
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
                if (Math.abs(world.determinant()) < 1e-12) continue;
                Vector3f lo = new Vector3f(Float.MAX_VALUE), hi = new Vector3f(-Float.MAX_VALUE);
                for (int i = 0; i < 8; i++) {
                    Vector3f c = world.transformPosition(new Vector3f(i & 1, (i >> 1) & 1, (i >> 2) & 1));
                    lo.min(c);
                    hi.max(c);
                }
                Matrix4f inv = new Matrix4f(world).invert();
                Matrix4f normalM = new Matrix4f(inv).transpose();
                boxes.add(new Box(inv, normalM, rgb(p.block.getMaterial()), p.brightness != null, lo.x, lo.y, lo.z, hi.x, hi.y, hi.z));
            }
        }
    }

    record Box(Matrix4f inv, Matrix4f normalM, int[] col, boolean glow, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
    }

    // ------------------------------------------------------------------ rendering

    static final class View {
        final World w;
        final double ex, ey, ez, yaw, pitch;
        final String label;

        View(World w, double ex, double ey, double ez, double yaw, double pitch, String label) {
            this.w = w;
            this.ex = ex;
            this.ey = ey;
            this.ez = ez;
            this.yaw = yaw;
            this.pitch = pitch;
            this.label = label;
        }

        BufferedImage render() {
            w.boxes.clear();
            w.grid.clear();
            w.loadModels(ex, ez);
            BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            double fov = Math.toRadians(70), th = Math.tan(fov / 2), aspect = (double) W / H;
            double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
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
            gr.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
            gr.setColor(new Color(255, 255, 255, 220));
            gr.drawString("The Plains: " + label, 14, 28);
            gr.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            gr.setColor(new Color(255, 255, 255, 150));
            gr.drawString("Ray-cast of the real generator output and display models, evening sky; procedural colours, not Minecraft's renderer.", 12, H - 12);
            gr.dispose();
            return img;
        }

        int trace(double dx, double dy, double dz) {
            double[] blk = traceBlocks(ex, ey, ez, dx, dy, dz, MAX_T);
            double bestT = blk != null ? blk[0] : MAX_T;
            Box hit = null;
            double[] hn = null;
            // walk the 8-block model grid along the ray
            double gx = ex / 8, gz = ez / 8;
            int cx = (int) Math.floor(gx), cz = (int) Math.floor(gz);
            int sx = dx > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
            double tdx = Math.abs(8 / dx), tdz = Math.abs(8 / dz);
            double tmx = (dx > 0 ? (cx + 1 - gx) : (gx - cx)) * tdx, tmz = (dz > 0 ? (cz + 1 - gz) : (gz - cz)) * tdz;
            double tCell = 0;
            while (tCell < bestT) {
                List<Box> list = w.grid.get(((long) cx << 32) ^ (cz & 0xffffffffL));
                if (list != null) {
                    for (Box b : list) {
                        double[] r = rayBox(b, dx, dy, dz, bestT);
                        if (r != null && r[0] < bestT) {
                            bestT = r[0];
                            hit = b;
                            hn = r;
                        }
                    }
                }
                if (tmx < tmz) {
                    tCell = tmx;
                    tmx += tdx;
                    cx += sx;
                } else {
                    tCell = tmz;
                    tmz += tdz;
                    cz += sz;
                }
            }
            int col;
            if (hit != null) {
                Vector3f n = new Vector3f(hn[1] == 0 ? (float) hn[2] : 0, hn[1] == 1 ? (float) hn[2] : 0, hn[1] == 2 ? (float) hn[2] : 0);
                hit.normalM.transformDirection(n).normalize();
                double hx = ex + dx * bestT, hy = ey + dy * bestT, hz = ez + dz * bestT;
                if (hit.glow) col = pack(hit.col[0], hit.col[1], hit.col[2]);
                else col = shade(hit.col, n.x, n.y, n.z, hx, hy, hz, 1.0);
            } else if (blk != null && blk[4] == -2) {
                return 0x07060A; // fell out of the world: the void
            } else if (blk != null) {
                col = blockColor(blk, dx, dy, dz);
            } else {
                return sky(dx, dy, dz);
            }
            return fog(col, bestT, dx, dy, dz);
        }

        /** Voxel DDA: {t, X, Y, Z, face} of the first solid block, or null. */
        double[] traceBlocks(double ox, double oy, double oz, double dx, double dy, double dz, double maxT) {
            int X = (int) Math.floor(ox), Y = (int) Math.floor(oy), Z = (int) Math.floor(oz);
            int stX = dx > 0 ? 1 : -1, stY = dy > 0 ? 1 : -1, stZ = dz > 0 ? 1 : -1;
            double tdX = Math.abs(1 / dx), tdY = Math.abs(1 / dy), tdZ = Math.abs(1 / dz);
            double tmX = (dx > 0 ? X + 1 - ox : ox - X) * tdX, tmY = (dy > 0 ? Y + 1 - oy : oy - Y) * tdY, tmZ = (dz > 0 ? Z + 1 - oz : oz - Z) * tdZ;
            double t = 0;
            int face = -1;
            while (t < maxT) {
                if (Y < Y0) return new double[]{t, X, Y, Z, -2};
                if (Y > 110 && dy > 0) return null;
                Material m = w.type(X, Y, Z);
                if (face >= 0 && !Mats.passable(m) && m != Material.BARRIER) return new double[]{t, X, Y, Z, face};
                if (tmX < tmY && tmX < tmZ) {
                    t = tmX; tmX += tdX; X += stX; face = stX > 0 ? 0 : 1;
                } else if (tmY < tmZ) {
                    t = tmY; tmY += tdY; Y += stY; face = stY > 0 ? 2 : 3;
                } else {
                    t = tmZ; tmZ += tdZ; Z += stZ; face = stZ > 0 ? 4 : 5;
                }
            }
            return null;
        }

        int blockColor(double[] blk, double dx, double dy, double dz) {
            double t = blk[0];
            int X = (int) blk[1], Y = (int) blk[2], Z = (int) blk[3], face = (int) blk[4];
            double nx = 0, ny = 0, nz = 0;
            switch (face) {
                case 0 -> nx = -1;
                case 1 -> nx = 1;
                case 2 -> ny = -1;
                case 3 -> ny = 1;
                case 4 -> nz = -1;
                default -> nz = 1;
            }
            double hx = ex + dx * t, hy = ey + dy * t, hz = ez + dz * t;
            Material m = w.type(X, Y, Z);
            int[] c = rgb(m);
            double u, v;
            if (nx != 0) { u = hz - Math.floor(hz); v = hy - Math.floor(hy); }
            else if (ny != 0) { u = hx - Math.floor(hx); v = hz - Math.floor(hz); }
            else { u = hx - Math.floor(hx); v = hy - Math.floor(hy); }
            // grass blocks: green top, dirt sides with a green fringe
            if (m == Material.GRASS_BLOCK && ny <= 0 && v < 0.8) c = rgb(Material.DIRT);
            int tu = (int) (u * 16), tv = (int) (v * 16);
            double n = hash(X * 16 + tu, Y * 16 + tv, Z * 16 + tu * 3 + tv, m.ordinal());
            double tex = 0.9 + n * 0.16;
            if (t > 60) tex = 1 + (tex - 1) * Math.max(0, 1 - (t - 60) / 80);
            return shade(c, nx, ny, nz, hx + nx * 0.01, hy + ny * 0.01, hz + nz * 0.01, tex);
        }

        /** Low warm sun, cool sky fill that fades with depth below the fields, shadows from blocks nearby. */
        int shade(int[] c, double nx, double ny, double nz, double hx, double hy, double hz, double tex) {
            // Minecraft sky light: full wherever the column above the lit side is open to the sky, dimmer under cover
            int ax = (int) Math.floor(hx + nx * 0.5), ay = (int) Math.floor(hy + ny * 0.5), az = (int) Math.floor(hz + nz * 0.5);
            boolean open = true;
            for (int y = ay + 1; y < 110 && open; y++) open = Mats.passable(w.type(ax, y, az));
            double sky = open ? 1.0 : 0.45;
            double ambient = (0.42 + 0.14 * ny) * sky;
            double ndl = nx * SUN.x + ny * SUN.y + nz * SUN.z;
            double sun = 0;
            if (ndl > 0) {
                double dist = Math.hypot(hx - ex, hz - ez);
                boolean lit = dist > 140 || traceBlocks(hx + nx * 0.05, hy + ny * 0.05, hz + nz * 0.05, SUN.x, SUN.y, SUN.z, 70) == null;
                sun = lit ? ndl * 0.85 : 0;
            }
            double r = c[0] * tex * (ambient * 0.92 + sun * 1.15);
            double g = c[1] * tex * (ambient * 0.95 + sun * 0.86);
            double b = c[2] * tex * (ambient * 1.12 + sun * 0.62);
            return pack(r, g, b);
        }

        static int sky(double dx, double dy, double dz) {
            double e = Math.max(-0.2, dy);
            Color horizon = new Color(0xF0A060), mid = new Color(0xC7787C), top = new Color(0x3C3F73);
            Color s;
            if (e < 0.12) s = blend(horizon, mid, (float) Math.max(0, e / 0.12));
            else s = blend(mid, top, (float) Math.min(1, (e - 0.12) / 0.5));
            double sd = dx * SUN.x + dy * SUN.y + dz * SUN.z;
            if (sd > 0.9992) return 0xFFE6B0;
            double glow = Math.max(0, (sd - 0.9) / 0.1);
            glow = glow * glow * 0.75;
            return pack(s.getRed() + (255 - s.getRed()) * glow, s.getGreen() + (215 - s.getGreen()) * glow * 0.9, s.getBlue() + (160 - s.getBlue()) * glow * 0.5);
        }

        static int fog(int col, double t, double dx, double dy, double dz) {
            double f = Math.max(0, Math.min(0.9, (t - 50) / 300));
            f = f * f * (3 - 2 * f);
            int s = sky(dx, Math.max(0, Math.min(0.05, dy)), dz);
            int r = (col >> 16) & 255, g = (col >> 8) & 255, b = col & 255;
            return pack(r + (((s >> 16) & 255) - r) * f, g + (((s >> 8) & 255) - g) * f, b + ((s & 255) - b) * f);
        }

        double[] rayBox(Box b, double dx, double dy, double dz, double maxT) {
            double t0 = 0, t1 = maxT;
            double[] o = {ex, ey, ez}, d = {dx, dy, dz}, lo = {b.minX(), b.minY(), b.minZ()}, hi = {b.maxX(), b.maxY(), b.maxZ()};
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
            Vector3f lo3 = b.inv().transformPosition(new Vector3f((float) ex, (float) ey, (float) ez));
            Vector3f ld = b.inv().transformDirection(new Vector3f((float) dx, (float) dy, (float) dz));
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

        static double hash(int x, int y, int z, int s) {
            long h = x * 73856093L ^ y * 19349663L ^ z * 83492791L ^ s * 2654435761L;
            h ^= (h >>> 13);
            h *= 0x5bd1e995L;
            h ^= (h >>> 15);
            return (h & 0xffff) / 65535.0;
        }

        static Color blend(Color a, Color b, float t) {
            return new Color(
                    Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                    Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                    Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
        }

        static int pack(double r, double g, double b) {
            return ((int) Math.min(255, Math.max(0, r)) << 16) | ((int) Math.min(255, Math.max(0, g)) << 8) | (int) Math.min(255, Math.max(0, b));
        }
    }
}
