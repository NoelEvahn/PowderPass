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
        c(Material.BLACK_STAINED_GLASS, 0x1C1E22); c(Material.BLUE_TERRACOTTA, 0x4A3B5B); c(Material.RED_TERRACOTTA, 0x8F3D2E);
        c(Material.CYAN_TERRACOTTA, 0x565B5B); c(Material.GREEN_TERRACOTTA, 0x4C532A); c(Material.WAXED_EXPOSED_COPPER, 0xA17E68);
        c(Material.YELLOW_CONCRETE, 0xF0AF15); c(Material.RED_WOOL, 0xA12722);
        c(Material.POLISHED_ANDESITE, 0x848685); c(Material.POLISHED_ANDESITE_STAIRS, 0x848685); c(Material.WHITE_STAINED_GLASS, 0xFFFFFF);
        c(Material.BIRCH_SLAB, 0xC5B57B); c(Material.DARK_OAK_SLAB, 0x42301A); c(Material.SMOOTH_STONE_SLAB, 0xA8A8A8); c(Material.BOOKSHELF, 0x6B5434);
        c(Material.RED_TERRACOTTA, 0x8F3D2E); c(Material.CYAN_TERRACOTTA, 0x575B5B); c(Material.AZALEA, 0x5D7A2E); c(Material.FLOWERING_AZALEA, 0x7A7A48);
        c(Material.GRASS_BLOCK, 0x6E8C4E); c(Material.STRIPPED_DARK_OAK_WOOD, 0x4A3A28); c(Material.SMOOTH_QUARTZ_SLAB, 0xEBE5DE); c(Material.BARREL, 0x8A6439);
        c(Material.LIGHT_BLUE_CONCRETE, 0x3A9AD0); c(Material.BLUE_CONCRETE, 0x2C2E8F); c(Material.BROWN_CONCRETE, 0x603C20); c(Material.GOLD_BLOCK, 0xD8AF35);
        c(Material.GREEN_TERRACOTTA, 0x4C532A); c(Material.BLUE_TERRACOTTA, 0x4A3B5B); c(Material.LIGHT_GRAY_STAINED_GLASS, 0x99999D); c(Material.GRAY_CONCRETE, 0x36393D); c(Material.QUARTZ_BLOCK, 0xEBE5DE); c(Material.SMOOTH_QUARTZ, 0xEBE5DE);
        c(Material.CALCITE, 0xDCDDD8); c(Material.QUARTZ_STAIRS, 0xEBE5DE); c(Material.SMOOTH_QUARTZ_STAIRS, 0xEBE5DE);
        c(Material.QUARTZ_PILLAR, 0xE8E3DA); c(Material.WHITE_STAINED_GLASS_PANE, 0xE6EEF0); c(Material.LIGHT_BLUE_STAINED_GLASS, 0x6FB8E0);
        c(Material.WHITE_STAINED_GLASS, 0xF2F4F4); c(Material.PINK_CONCRETE, 0xD5658E); c(Material.YELLOW_CONCRETE, 0xF0AF15);
        c(Material.ORANGE_CONCRETE, 0xE06100); c(Material.LIGHT_BLUE_CONCRETE, 0x3A9AD0); c(Material.BLUE_CONCRETE, 0x2C2E8F);
        c(Material.MAGENTA_CONCRETE, 0xA9309F); c(Material.LIGHT_BLUE_WOOL, 0x3AAFD9); c(Material.LIGHT_GRAY_CONCRETE, 0x7D7D73);
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

    /** Level 1, the parking garage: the default view plus close-ups of a parked car and of a steam pipe. */
    @Test
    void renderLevel1() throws Exception {
        String out = System.getenv("RENDER_VIEWS");
        Assumptions.assumeTrue(out != null && !out.isBlank(), "set RENDER_VIEWS=<dir>");
        LevelGenerator g = Generators.create(Level.LEVEL_1, SEED, GenSettings.defaults());
        Scene s = new Scene(g, 0, 0);
        s.label = "Level 1";
        ImageIO.write(s.render(), "png", new File(out, "level1-view.png"));
        System.out.println("wrote level1-view " + s.note);
        // the nearest few parked cars: from the aisle, three-quarter front and rear
        int shots = 0;
        search:
        for (int r = 0; r < 12; r++) {
            for (int cx = -r; cx <= r; cx++) for (int cz = -r; cz <= r; cz++) {
                if (Math.max(Math.abs(cx), Math.abs(cz)) != r) continue;
                for (DecorSpec d : g.decorations(cx, cz)) {
                    if (!d.model.equals("garage_car")) continue;
                    double fz = Math.cos(Math.toRadians(d.yaw)); // the car's front points along +-Z
                    for (int k = 0; k < 2 && shots < 4; k++) {
                        Scene c = new Scene(g, (int) d.x, (int) d.z);
                        c.ex = d.x + (k == 0 ? 3.2 : 1.0);
                        c.ez = d.z + fz * (k == 0 ? 6.0 : -4.6);
                        c.ey = d.y + 1.62;
                        double dx = d.x - c.ex, dz = d.z - c.ez;
                        c.yaw = Math.atan2(-dx, dz);
                        c.pitch = Math.toRadians(-12);
                        c.label = "Level 1 car " + (k == 0 ? "front" : "rear") + (com.powderpass.backrooms.display.GarageProps.covered(d.seed) ? " (sheet)" : "");
                        ImageIO.write(c.render(), "png", new File(out, "level1-car" + shots + ".png"));
                        shots++;
                    }
                    if (shots >= 4) break search;
                }
            }
        }
        // under a pipe run, looking along it
        for (DecorSpec d : g.decorations(0, 0)) {
            if (!d.model.startsWith("garage_pipes")) continue;
            Scene c = new Scene(g, (int) d.x + 2, (int) d.z + 3);
            c.ex = d.x + 2.5;
            c.ey = d.y - 1.1;
            c.ez = d.z + 3.5;
            c.yaw = Math.toRadians(-115);
            c.pitch = Math.toRadians(25);
            c.label = "Level 1 steam pipes";
            ImageIO.write(c.render(), "png", new File(out, "level1-pipes.png"));
            break;
        }
    }

    /** Level 4: named viewpoints in the towers at night (cubicles, a private office, stairs, a sky bridge, the lift, outside). */
    @Test
    void renderOfficeTowers() throws Exception {
        String out = System.getenv("RENDER_VIEWS");
        Assumptions.assumeTrue(out != null && !out.isBlank(), "set RENDER_VIEWS=<dir>");
        var g = (com.powderpass.backrooms.gen.levels.OfficeGenerator) Generators.create(Level.LEVEL_4, SEED, GenSettings.defaults());
        int B0 = com.powderpass.backrooms.gen.levels.OfficeGenerator.BASE, FH = com.powderpass.backrooms.gen.levels.OfficeGenerator.FH;
        java.util.Map<String, double[]> views = new java.util.LinkedHashMap<>();
        // {eyeX, eyeY, eyeZ, yaw, pitch, floor}
        var t = g.tower(0, 0);
        int f = Math.min(2, t.floors - 1), y = B0 + FH * f + 1;
        views.put("cubicles", new double[]{t.x0 + 2.5, y + 1.62, t.z0 + t.l * 0.62, -125, -6, f});
        views.put("window", new double[]{t.x0 + t.w * 0.3, y + 1.62, t.z0 + 2.2, 200, 4, f});
        // a private office: search the towers near the origin for a red-carpet room
        outer:
        for (int r = 0; r < 4; r++) for (int cx = -r; cx <= r; cx++) for (int cz = -r; cz <= r; cz++) {
            var tt = g.tower(cx, cz);
            for (int ff = tt.floors - 1; ff >= 1; ff--) {
                for (int i = 2; i < tt.w - 2; i++) for (int j = 2; j < tt.l - 2; j++) {
                    if (g.code(tt, ff, i, j) != 8) continue; // an office door: step inside and look toward the window
                    for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        if (g.finish(tt, ff, i + d[0], j + d[1]) != 2 || g.code(tt, ff, i + d[0], j + d[1]) != 0) continue;
                        double yy = B0 + FH * ff + 1;
                        double yawIn = Math.toDegrees(Math.atan2(-d[0], d[1]));
                        views.put("private-office", new double[]{tt.x0 + i + 0.5 + d[0] * 0.6, yy + 1.62, tt.z0 + j + 0.5 + d[1] * 0.6, yawIn + 25, -12, ff});
                        break outer;
                    }
                }
            }
        }
        // the stairwell: on floor 1's landing looking up the first flight
        int sy = B0 + FH + 1;
        views.put("stairwell", new double[]{t.x0 + t.ci0 + 1.6, sy + 1.62, t.z0 + t.cj0 + 1.3, -15, 22, 1});
        // the lift: in the lift lobby, facing the doors; and inside the car facing the panel
        views.put("lift-lobby", new double[]{t.x0 + t.ci0 + 5.5, y + 1.62, t.z0 + t.cj0 - 2.6, -35, 2, f});
        views.put("lift-car", new double[]{t.x0 + t.ci0 + 6.6, y + 1.62, t.z0 + t.cj0 + 2.4, -100, 8, f});
        // a sky bridge: stand at one end and look along it
        var br = t.bridges().get(0);
        double by = B0 + FH * br.floor() + 1 + 1.62;
        double mid = (br.from() + br.to()) / 2.0;
        if (br.ew()) views.put("sky-bridge", new double[]{br.from() + 1.5, by, br.lo() + 1.5, -90, 2, br.floor()});
        else views.put("sky-bridge", new double[]{br.lo() + 1.5, by, br.from() + 1.5, 0, 2, br.floor()});
        views.put("bridge-outside", new double[]{br.ew() ? mid : br.lo() + 1.5, by, br.ew() ? br.lo() + 1.5 : mid, br.ew() ? 15 : -75, -2, br.floor()});
        // the roof, looking out over the other towers
        var tr = g.tower(1, 0);
        double ry = B0 + FH * tr.floors + 1 + 1.62;
        views.put("roof", new double[]{tr.x0 + 3.5, ry, tr.z0 + tr.l - 3.5, 135, -6, tr.floors});
        String only = System.getenv("RENDER_ONLY");
        for (var e : views.entrySet()) {
            if (only != null && !only.isBlank() && !e.getKey().matches(only)) continue;
            double[] v = e.getValue();
            int floorY = B0 + FH * (int) v[5];
            Scene sc = new Scene(g, v[0], v[1], v[2], v[3], v[4], 84, Math.max(g.bottomY, floorY - 16), Math.min(g.roofY, floorY + 24), true);
            sc.label = "Level 4 — " + e.getKey();
            File file = new File(out, "level4-" + e.getKey() + ".png");
            file.getParentFile().mkdirs();
            ImageIO.write(sc.render(), "png", file);
            System.out.println("wrote " + file.getAbsolutePath() + " " + sc.note);
        }
    }

    /** Named viewpoints in the Poolrooms: the main floor, each kind of space, the slide and a stair tower. */
    @Test
    void renderPoolroomsViews() throws Exception {
        String out = System.getenv("RENDER_VIEWS");
        Assumptions.assumeTrue(out != null && !out.isBlank(), "set RENDER_VIEWS=<dir>");
        var g = (com.powderpass.backrooms.gen.levels.PoolroomsGenerator) Generators.create(Level.POOLROOMS, SEED, GenSettings.defaults());
        int mid = g.floorY[1] + 1;
        java.util.Map<String, double[]> views = new java.util.LinkedHashMap<>();
        int[] s = g.safeSpot(0, 0, 0);
        views.put("main-floor", new double[]{s[0] + 0.5, s[1], s[2] + 0.5, Double.NaN, -4, 26});
        int[] bright = null, hall = null, massive = null, brightLow = null;
        for (int r = 0; r < 14; r++) for (int rx = -r; rx <= r; rx++) for (int rz = -r; rz <= r; rz++) {
            if (Math.max(Math.abs(rx), Math.abs(rz)) != r) continue;
            var p = g.plan(rx, rz);
            if (bright == null && p.zone[1] == com.powderpass.backrooms.gen.levels.PoolroomsGenerator.Zone.BRIGHT && g.brightStatue(rx, rz, 1)) bright = new int[]{rx, rz};
            if (brightLow == null && p.zone[2] == com.powderpass.backrooms.gen.levels.PoolroomsGenerator.Zone.BRIGHT) brightLow = new int[]{rx, rz};
            if (hall == null && p.zone[1] == com.powderpass.backrooms.gen.levels.PoolroomsGenerator.Zone.HALLWAY) hall = new int[]{rx, rz};
            if (massive == null && p.zone[1] == com.powderpass.backrooms.gen.levels.PoolroomsGenerator.Zone.MASSIVE) massive = new int[]{rx, rz};
        }
        if (bright != null) {
            int x0 = bright[0] * 60, z0 = bright[1] * 60;
            views.put("bright-room", new double[]{x0 + 6.5, mid, z0 + 6.5, Math.toDegrees(Math.atan2(-(30 - 6.5), 30 - 6.5)), 2, 40});
            int[] fd = g.brightStatueFacing(bright[0], bright[1], 1);
            double[] face = {x0 + 30.5 + fd[0] * 25, z0 + 30.5 + fd[1] * 25}, centre = {x0 + 30.5, z0 + 30.5};
            views.put("greek-head", new double[]{face[0] + fd[1] * 3, mid, face[1] - fd[0] * 3, yawTo(face, centre) + 6, 14, 40});
        }
        if (brightLow != null) {
            int x0 = brightLow[0] * 60, z0 = brightLow[1] * 60;
            views.put("bright-room-upper", new double[]{x0 + 52.5, g.floorY[2] + 1, z0 + 8.5, Math.toDegrees(Math.atan2(-(30 - 52.5), 30 - 8.5)), 4, 44});
        }
        if (hall != null) {
            int x0 = hall[0] * 60, z0 = hall[1] * 60;
            boolean ax = g.hallwayAlongX(hall[0], hall[1], 1);
            double ex = ax ? x0 + 1.5 : x0 + 28.5, ez = ax ? z0 + 28.5 : z0 + 1.5;
            views.put("tall-hallway", new double[]{ex, mid, ez, ax ? -90 : 0, 6, 40});
            views.put("hallway-hole", new double[]{ax ? x0 + 14.5 : x0 + 22.5, mid, ax ? z0 + 22.5 : z0 + 14.5, ax ? 180 : 90, -6, 26});
        }
        if (massive != null) {
            double[] a = g.hallPoint(massive[0], massive[1], 12.5, 34.5), b = g.hallPoint(massive[0], massive[1], 44.5, 20.5);
            views.put("massive-hall", new double[]{a[0], mid, a[1], yawTo(a, b), 14, 70});
            double[] c = g.hallPoint(massive[0], massive[1], 20.5, 21.5);
            views.put("statue", new double[]{c[0], mid, c[1], yawTo(c, b), 12, 60});
            double[] st = g.hallPoint(massive[0], massive[1], 1.5, 14.5), st2 = g.hallPoint(massive[0], massive[1], 20.5, 14.5);
            views.put("slide-top", new double[]{st[0], g.floorY[2] + 1, st[1], yawTo(st, st2), -22, 60});
            double[] lo = g.hallPoint(massive[0], massive[1], 44.5, 52.5), lo2 = g.hallPoint(massive[0], massive[1], 32.5, 30.5);
            views.put("slide-landing", new double[]{lo[0], mid, lo[1], yawTo(lo, lo2), 18, 70});
        }
        for (var e : views.entrySet()) {
            double[] v = e.getValue();
            Scene sc = new Scene(g, v[0], v[1], v[2], v[3], v[4], (int) v[5]);
            sc.label = "The Poolrooms (" + e.getKey() + ")";
            File f = new File(out, "poolrooms-" + e.getKey() + ".png");
            f.getParentFile().mkdirs();
            ImageIO.write(sc.render(), "png", f);
            System.out.println("wrote " + f.getAbsolutePath() + " " + sc.note);
        }
    }

    private static double yawTo(double[] from, double[] to) {
        return Math.toDegrees(Math.atan2(-(to[0] - from[0]), to[1] - from[1]));
    }

    private static final class Scene {
        String label = "Level 0";
        final LevelGenerator g;
        final Area a;
        final int x0, z0, x1, z1, y0, y1;
        final float[][][] light;
        final List<Box> boxes = new ArrayList<>();
        double ex, ey, ez, yaw, pitch, maxT = 60;
        String note;
        /** Open night sky: rays that leave the scene show stars, surfaces open to the sky get faint moonlight. */
        boolean night;
        double far = 60;
        int[][] skyTop;

        /** A scene with an explicit eye (feet + 1.62 is the player's eye), yaw/pitch in degrees, radius and y range. */
        Scene(LevelGenerator g, double ex, double ey, double ez, double yawDeg, double pitchDeg, int r, int yLo, int yHi, boolean night) {
            this.g = g;
            x0 = (int) Math.floor(ex) - r;
            z0 = (int) Math.floor(ez) - r;
            x1 = (int) Math.floor(ex) + r;
            z1 = (int) Math.floor(ez) + r;
            a = new Area(g, x0, z0, x1, z1);
            y0 = yLo;
            y1 = yHi;
            this.night = night;
            far = night ? 160 : 60;
            light = new float[x1 - x0][y1 - y0][z1 - z0];
            propagateLight();
            if (night) {
                skyTop = new int[x1 - x0][z1 - z0];
                for (int x = x0; x < x1; x++) for (int z = z0; z < z1; z++) {
                    int top = Integer.MIN_VALUE;
                    for (int y = g.roofY; y >= y0; y--) {
                        Material m = type(x, y, z);
                        if (!Mats.passable(m) && glassTint(m) == null) {
                            top = y;
                            break;
                        }
                    }
                    skyTop[x - x0][z - z0] = top;
                }
            }
            for (int cx = Math.floorDiv(x0, 16); cx <= Math.floorDiv(x1 - 1, 16); cx++) {
                for (int cz = Math.floorDiv(z0, 16); cz <= Math.floorDiv(z1 - 1, 16); cz++) {
                    for (DecorSpec d : g.decorations(cx, cz)) addModel(d);
                }
            }
            this.ex = ex;
            this.ey = ey;
            this.ez = ez;
            this.yaw = Math.toRadians(yawDeg);
            this.pitch = Math.toRadians(pitchDeg);
            note = "eye " + (int) ex + "," + (int) ey + "," + (int) ez;
        }

        /** Light-transmitting blocks: per-channel tint, or null for anything else. */
        static double[] glassTint(Material m) {
            return switch (m) {
                case GLASS -> new double[]{0.9, 0.94, 0.95};
                case LIGHT_BLUE_STAINED_GLASS -> new double[]{0.62, 0.8, 0.92};
                case LIGHT_GRAY_STAINED_GLASS -> new double[]{0.72, 0.72, 0.74};
                case BLACK_STAINED_GLASS -> new double[]{0.18, 0.18, 0.2};
                case IRON_BARS -> new double[]{0.62, 0.62, 0.64};
                default -> null;
            };
        }

        boolean skyOpen(double x, double y, double z) {
            if (skyTop == null) return false;
            int X = (int) Math.floor(x), Z = (int) Math.floor(z);
            if (X < x0 || X >= x1 || Z < z0 || Z >= z1) return true;
            return y > skyTop[X - x0][Z - z0];
        }

        /** The night sky: a dark gradient with stars above the horizon, the black drop below it. */
        static int sky(double dx, double dy, double dz) {
            if (dy < 0) {
                double t = Math.min(1, -dy * 4);
                return pack(14 * (1 - t) + 3 * t, 16 * (1 - t) + 3 * t, 26 * (1 - t) + 5 * t);
            }
            double t = Math.min(1, dy * 2.2);
            double r = 22 * (1 - t) + 4 * t, gg = 27 * (1 - t) + 6 * t, b = 46 * (1 - t) + 16 * t;
            long q = Math.round(dx * 700) * 73856093L ^ Math.round(dy * 700) * 19349663L ^ Math.round(dz * 700) * 83492791L;
            q ^= q >>> 13;
            q *= 0x5bd1e995L;
            q ^= q >>> 15;
            double hsh = (q & 0xffff) / 65535.0;
            if (dy > 0.03 && hsh > 0.9965) {
                double s = 120 + (hsh - 0.9965) / 0.0035 * 135;
                return pack(s, s, s * 1.05);
            }
            return pack(r, gg, b);
        }

        /** Faint blue moonlight on surfaces open to the sky. */
        int moonlit(int col, double faceShade, int[] base, double x, double y, double z) {
            if (!night || !skyOpen(x, y, z)) return col;
            double k = 0.13 * faceShade;
            int r = (col >> 16) & 255, gg = (col >> 8) & 255, b = col & 255;
            return pack(r + base[0] * k * 0.75, gg + base[1] * k * 0.85, b + base[2] * k * 1.15);
        }

        /** A fixed viewpoint: feet at (fx,fy,fz), yaw/pitch in degrees (NaN yaw = longest open line), lit volume to fy+up. */
        Scene(LevelGenerator g, double fx, double fy, double fz, double yawDeg, double pitchDeg, int up) {
            this.g = g;
            int r = 48;
            x0 = (int) Math.floor(fx) - r;
            z0 = (int) Math.floor(fz) - r;
            x1 = (int) Math.floor(fx) + r;
            z1 = (int) Math.floor(fz) + r;
            a = new Area(g, x0, z0, x1, z1);
            y0 = Math.max(g.bottomY, (int) fy - 12);
            y1 = Math.min(g.roofY + 1, (int) fy + up);
            light = new float[x1 - x0][y1 - y0][z1 - z0];
            maxT = 90;
            propagateLight();
            addDecor();
            ex = fx;
            ey = fy + 1.62;
            ez = fz;
            if (Double.isNaN(yawDeg)) {
                int best = -1;
                for (int i = 0; i < 16; i++) {
                    double yw = i * Math.PI / 8;
                    int d = 0;
                    while (d < 38 && passable((int) Math.floor(ex - Math.sin(yw) * d), (int) Math.floor(ey), (int) Math.floor(ez + Math.cos(yw) * d))) d++;
                    if (d > best) {
                        best = d;
                        yawDeg = Math.toDegrees(yw);
                    }
                }
            }
            yaw = Math.toRadians(yawDeg);
            pitch = Math.toRadians(pitchDeg);
            note = "eye " + (int) ex + "," + (int) ey + "," + (int) ez + " zone " + g.zoneName(g.floorAt((int) fy), g.cellOf((int) ex), g.cellOf((int) ez));
        }

        private void addDecor() {
            for (int cx = Math.floorDiv(x0, 16); cx <= Math.floorDiv(x1 - 1, 16); cx++) {
                for (int cz = Math.floorDiv(z0, 16); cz <= Math.floorDiv(z1 - 1, 16); cz++) {
                    for (DecorSpec d : g.decorations(cx, cz)) addModel(d);
                }
            }
        }

        Scene(LevelGenerator g, int px, int pz) {
            this.g = g;
            int[] spot = g.safeSpot(px, pz, 0);
            int r = 40;
            x0 = spot[0] - r;
            z0 = spot[2] - r;
            x1 = spot[0] + r;
            z1 = spot[2] + r;
            a = new Area(g, x0, z0, x1, z1);
            y0 = spot[1] - 3;
            y1 = spot[1] + 25;
            light = new float[x1 - x0][y1 - y0][z1 - z0];
            propagateLight();
            addDecor();
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
            double bestT = Math.max(far, maxT);
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
                double[] gt = night ? glassTint(m) : null;
                if (m == Material.WATER) {
                    waterTint[0] *= 0.985; waterTint[1] *= 0.99; waterTint[2] *= 1.0;
                } else if (gt != null && face >= 0) {
                    waterTint[0] *= gt[0]; waterTint[1] *= gt[1]; waterTint[2] *= gt[2];
                } else if (!(Mats.passable(m) || m == Material.BARRIER) && face >= 0) {
                    B blk = a.get(X, Y, Z);
                    boolean top = blk.state.contains("type=top"), bottom = blk.state.contains("type=bottom");
                    double th = t;
                    int fc = face;
                    if (top || bottom) {
                        // half slabs: find where the ray actually meets the occupied half of the voxel
                        double tExit = Math.min(tmX, Math.min(tmY, tmZ));
                        th = -1;
                        for (int k = 0; k < 10; k++) {
                            double tt = t + (tExit - t) * (k + 0.5) / 10;
                            double fy = ey + dy * tt - Y;
                            if (top ? fy >= 0.5 : fy <= 0.5) {
                                th = tt;
                                fc = k == 0 ? face : (dy < 0 ? 3 : 2);
                                break;
                            }
                        }
                    }
                    if (th >= 0) {
                        double hx = ex + dx * th, hy = ey + dy * th, hz = ez + dz * th;
                        int col = shadeBlock(m, blk, fc, hx, hy, hz, X, Y, Z, th);
                        return applyTint(col, waterTint);
                    }
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
            return night ? applyTint(sky(dx, dy, dz), waterTint) : 0x050403;
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
            return moonlit(warm(c, k, l), faceShade, c, hx + n.x * 0.3, hy + n.y * 0.3, hz + n.z * 0.3);
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
            return moonlit(warm(c, k, l), faceShade * tex, c, hx + nx * 0.5, hy + ny * 0.5, hz + nz * 0.5);
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
