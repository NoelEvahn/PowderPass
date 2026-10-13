package com.powderpass.backrooms.gen;

import com.powderpass.backrooms.Level;
import org.bukkit.Material;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Renders a top-down map of each level from the real generators (painted chunks, not a sketch).
 * Off by default; run with {@code RENDER_MAPS=<dir> gradle test --tests '*MapRenderTest'}.
 */
class MapRenderTest {
    private static final long SEED = 0x5EEDBACCL;
    private static final Map<Material, Color> COLORS = new EnumMap<>(Material.class);
    private static final TreeSet<String> UNKNOWN = new TreeSet<>();

    static {
        c(Material.SMOOTH_SANDSTONE, 0xD8CC94); c(Material.YELLOW_TERRACOTTA, 0xBA8524); c(Material.BROWN_TERRACOTTA, 0x5A3B26);
        c(Material.WHITE_TERRACOTTA, 0xD1B2A1); c(Material.STONE, 0x7D7D7D); c(Material.WHITE_CONCRETE, 0xDDE2E3);
        c(Material.LIGHT_BLUE_CONCRETE, 0x3A9AD0); c(Material.WATER, 0x2F6FD8); c(Material.GRAY_CONCRETE, 0x45484D);
        c(Material.LIGHT_GRAY_CONCRETE, 0x8C8C84); c(Material.SMOOTH_STONE, 0x9E9E9E); c(Material.YELLOW_CONCRETE, 0xF0AF15);
        c(Material.SPRUCE_PLANKS, 0x73552F); c(Material.BARREL, 0x8A6439); c(Material.GRAY_WOOL, 0x3F4448);
        c(Material.LIGHT_GRAY_WOOL, 0x8E8E87); c(Material.SMOOTH_QUARTZ, 0xEBE5DE); c(Material.GLASS, 0xA9DCE6);
        c(Material.SMOOTH_STONE_SLAB, 0xA8A8A8); c(Material.SPRUCE_SLAB, 0x7A5A32); c(Material.GRASS_BLOCK, 0x6E8C4E);
        c(Material.DIRT, 0x866043); c(Material.DIRT_PATH, 0xA88A4E); c(Material.TINTED_GLASS, 0x2E2A33);
        c(Material.DEEPSLATE_TILES, 0x37373B); c(Material.DEEPSLATE_TILE_STAIRS, 0x37373B); c(Material.DEEPSLATE_TILE_SLAB, 0x37373B);
        c(Material.BEDROCK, 0x333333); c(Material.SPRUCE_DOOR, 0x6A4D2A); c(Material.BIRCH_DOOR, 0xD7C185);
        c(Material.DARK_OAK_DOOR, 0x4A3018); c(Material.WAXED_COPPER_DOOR, 0xC06A4A); c(Material.IRON_BLOCK, 0xD8D8D8);
        c(Material.STRIPPED_SPRUCE_WOOD, 0x8C6E44); c(Material.STONE_STAIRS, 0x7D7D7D); c(Material.SPRUCE_STAIRS, 0x73552F);
        c(Material.QUARTZ_STAIRS, 0xEBE5DE); c(Material.POLISHED_ANDESITE, 0x868886); c(Material.ANDESITE_STAIRS, 0x868886);
        c(Material.DARK_OAK_SLAB, 0x42301A); c(Material.CAULDRON, 0x4A4A4A); c(Material.BOOKSHELF, 0x6B5434);
        c(Material.BLACK_CONCRETE, 0x101216);
        c(Material.END_STONE, 0xDBDE9E); c(Material.BONE_BLOCK, 0xE5E1CF); c(Material.BIRCH_PLANKS, 0xC5B57B);
        c(Material.STRIPPED_BIRCH_WOOD, 0xC4B07B); c(Material.LIGHT_BLUE_WOOL, 0x3AAFD9); c(Material.LIGHT_GRAY_STAINED_GLASS, 0x99999D);
        c(Material.MOSS_BLOCK, 0x596E2D); c(Material.COARSE_DIRT, 0x77553B); c(Material.TUFF, 0x6C6D66); c(Material.ANDESITE, 0x888888);
        c(Material.SHORT_GRASS, 0x7FA35A); c(Material.TALL_GRASS, 0x7FA35A); c(Material.STONE_STAIRS, 0x7D7D7D); c(Material.BARRIER, 0xFF00FF); c(Material.LADDER, 0x9C7B45);
        c(Material.CALCITE, 0xDCDDD8); c(Material.SMOOTH_QUARTZ_STAIRS, 0xEBE5DE); c(Material.WHITE_STAINED_GLASS_PANE, 0xE6EEF0);
    }

    private static void c(Material m, int rgb) {
        COLORS.put(m, new Color(rgb));
    }

    /** Colour of a painted block: a custom textured block's average colour, else the material's. */
    private static Color colorAt(MemoryCanvas mc, int lx, int y, int lz) {
        CustomBlocks.Def d = CustomBlocks.of(mc.get(lx, y, lz));
        return d != null ? new Color(d.rgb()) : color(mc.type(lx, y, lz));
    }

    private static Color color(Material m) {
        Color col = COLORS.get(m);
        if (col != null) return col;
        UNKNOWN.add(m.name());
        return new Color(0xFF00FF);
    }

    private static final Map<String, Color> DEST = new LinkedHashMap<>();

    static {
        DEST.put("Level 0", new Color(0xFFD400));
        DEST.put("Level 1", new Color(0xFF7A00));
        DEST.put("The Poolrooms", new Color(0x00E5FF));
        DEST.put("Level 4", new Color(0xC04CFF));
        DEST.put("The Plains", new Color(0x2EE04A));
        DEST.put("EXIT", new Color(0xFF2040));
    }

    private record Spec(Level level, int blocks, int px, int floor) {
        Spec(Level level, int blocks, int px) {
            this(level, blocks, px, 0);
        }
    }

    /** The Poolrooms has three storeys: one plan per floor. */
    @Test
    void renderPoolroomsFloors() throws Exception {
        String out = System.getenv("RENDER_MAPS");
        Assumptions.assumeTrue(out != null && !out.isBlank(), "set RENDER_MAPS=<dir> to render maps");
        File dir = new File(out);
        dir.mkdirs();
        String[] names = {"lower", "main", "upper"};
        for (int f = 0; f < 3; f++) {
            BufferedImage img = render(new Spec(Level.POOLROOMS, 360, 3, f));
            File file = new File(dir, "map-poolrooms-" + names[f] + ".png");
            ImageIO.write(img, "png", file);
            System.out.println("wrote " + file.getAbsolutePath());
        }
        if (!UNKNOWN.isEmpty()) System.out.println("uncoloured materials: " + UNKNOWN);
    }

    @Test
    void renderAllLevels() throws Exception {
        String out = System.getenv("RENDER_MAPS");
        Assumptions.assumeTrue(out != null && !out.isBlank(), "set RENDER_MAPS=<dir> to render maps");
        File dir = new File(out);
        dir.mkdirs();
        List<Spec> specs = List.of(
                new Spec(Level.LEVEL_0, 240, 4),
                new Spec(Level.LEVEL_1, 480, 2),
                new Spec(Level.POOLROOMS, 320, 3),
                new Spec(Level.LEVEL_4, 480, 2),
                new Spec(Level.PLAINS, 640, 2));
        for (Spec s : specs) {
            BufferedImage img = render(s);
            File f = new File(dir, "map-" + s.level.key() + ".png");
            ImageIO.write(img, "png", f);
            System.out.println("wrote " + f.getAbsolutePath());
        }
        if (!UNKNOWN.isEmpty()) System.out.println("uncoloured materials: " + UNKNOWN);
    }

    private static BufferedImage render(Spec s) {
        LevelGenerator g = Generators.create(s.level, SEED, GenSettings.defaults());
        int n = s.blocks, px = s.px, header = 64, legendH = 120;
        int x0 = -n / 2, z0 = -n / 2;
        int fy = g.floorY[s.floor];
        boolean outdoor = s.level.outdoor();
        int w = n * px, h = n * px + header + legendH;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D gr = img.createGraphics();
        gr.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        gr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gr.setColor(new Color(0x111111));
        gr.fillRect(0, 0, w, h);

        int[][] lampMask = new int[n][n];
        for (int cx = Math.floorDiv(x0, 16); cx <= Math.floorDiv(x0 + n - 1, 16); cx++) {
            for (int cz = Math.floorDiv(z0, 16); cz <= Math.floorDiv(z0 + n - 1, 16); cz++) {
                MemoryCanvas mc = new MemoryCanvas(g.bottomY, g.roofY + 1);
                g.paint(mc, cx, cz);
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        int x = cx * 16 + lx, z = cz * 16 + lz;
                        int ix = x - x0, iz = z - z0;
                        if (ix < 0 || iz < 0 || ix >= n || iz >= n) continue;
                        Color col = outdoor ? outdoorColumn(mc, lx, lz, fy, g.roofY) : indoorColumn(mc, lx, lz, fy, lampMask, ix, iz);
                        gr.setColor(col);
                        gr.fillRect(ix * px, header + iz * px, px, px);
                    }
                }
            }
        }
        // lamps (invisible LIGHT blocks under display fixtures)
        if (!outdoor) {
            gr.setColor(new Color(255, 255, 225, 200));
            int d = Math.max(1, px - 1);
            for (int ix = 0; ix < n; ix++) for (int iz = 0; iz < n; iz++) {
                if (lampMask[ix][iz] == 1) gr.fillRect(ix * px + (px - d) / 2, header + iz * px + (px - d) / 2, d, d);
            }
        }
        // display models that are big enough to matter from above
        if (outdoor) {
            gr.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            for (int cx = Math.floorDiv(x0, 16); cx <= Math.floorDiv(x0 + n - 1, 16); cx++) {
                for (int cz = Math.floorDiv(z0, 16); cz <= Math.floorDiv(z0 + n - 1, 16); cz++) {
                    for (DecorSpec d : g.decorations(cx, cz)) {
                        int ix = (int) Math.floor(d.x) - x0, iz = (int) Math.floor(d.z) - z0;
                        if (ix < 0 || iz < 0 || ix >= n || iz >= n) continue;
                        String m = d.model.split(":")[0];
                        boolean colossus = m.startsWith("giant_") || m.equals("floating_panel") || m.equals("lone_wall");
                        if (colossus) {
                            gr.setColor(new Color(0, 0, 0, 170));
                            gr.fillOval(ix * px - 9, header + iz * px - 9, 18, 18);
                            gr.setColor(Color.WHITE);
                            gr.setStroke(new BasicStroke(2));
                            gr.drawOval(ix * px - 9, header + iz * px - 9, 18, 18);
                            gr.drawString(m.replace('_', ' '), ix * px + 12, header + iz * px + 4);
                        } else if (m.equals("utility_pole")) {
                            gr.setColor(new Color(0x2B1D10));
                            gr.fillRect(ix * px - 1, header + iz * px - 1, 3, 3);
                        }
                    }
                }
            }
        }
        // transition triggers, outlined so they read over anything
        Map<String, int[]> found = new LinkedHashMap<>();
        for (int ix = 0; ix < n; ix++) {
            for (int iz = 0; iz < n; iz++) {
                Feature f = null;
                for (int dy = 3; dy >= -14 && f == null; dy--) f = g.triggerAt(x0 + ix, fy + dy, z0 + iz);
                if (f == null || !f.isTransition()) continue;
                Color c = DEST.getOrDefault(f.dest().describe(), Color.MAGENTA);
                gr.setColor(c);
                gr.fillRect(ix * px - 2, header + iz * px - 2, px + 4, px + 4);
                found.putIfAbsent(f.dest().describe(), new int[]{ix, iz});
            }
        }
        // spawn / safe spot nearest the centre
        int[] safe = g.safeSpot(0, 0, 0);
        int sx = safe[0] - x0, sz = safe[2] - z0;
        gr.setColor(Color.WHITE);
        gr.setStroke(new BasicStroke(3));
        gr.drawLine(sx * px - 10, header + sz * px, sx * px + 10, header + sz * px);
        gr.drawLine(sx * px, header + sz * px - 10, sx * px, header + sz * px + 10);

        // header
        gr.setColor(new Color(0x111111));
        gr.fillRect(0, 0, w, header);
        gr.setColor(Color.WHITE);
        gr.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
        gr.drawString(s.level.displayName() + " — " + s.level.subtitle() + (g.floorCount() > 1 ? " (floor " + s.floor + ")" : ""), 14, 34);
        gr.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        gr.setColor(new Color(0xBBBBBB));
        gr.drawString("Top-down, " + n + "×" + n + " blocks around 0,0 · seed " + Long.toHexString(SEED)
                + (outdoor ? " · highest block per column" : " · floor plan at head height"), 14, 54);
        // scale bar: 32 blocks
        int bar = 32 * px;
        gr.setColor(Color.WHITE);
        gr.fillRect(w - bar - 16, 40, bar, 5);
        gr.drawString("32 blocks", w - bar - 16, 34);

        // legend
        int ly = header + n * px + 22;
        gr.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        gr.setColor(Color.WHITE);
        gr.drawString("Transitions in view:", 14, ly);
        int lx = 170;
        gr.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        for (Map.Entry<String, Color> e : DEST.entrySet()) {
            if (e.getKey().equals(s.level.displayName())) continue;
            gr.setColor(e.getValue());
            gr.fillRect(lx, ly - 11, 12, 12);
            gr.setColor(found.containsKey(e.getKey()) ? Color.WHITE : new Color(0x777777));
            String label = "→ " + e.getKey() + (found.containsKey(e.getKey()) ? "" : " (not in view)");
            gr.drawString(label, lx + 18, ly);
            lx += gr.getFontMetrics().stringWidth(label) + 36;
            if (lx > w - 160) {
                lx = 170;
                ly += 20;
            }
        }
        ly += 26;
        gr.setColor(new Color(0xBBBBBB));
        gr.drawString(outdoor
                ? "Ringed labels = colossal display-entity structures · dark dots = utility poles · white cross = spawn"
                : "Pale dots = lamps (light block + display fixture) · darker = walls · white cross = safe spawn", 14, ly);
        ly += 20;
        gr.drawString(legendFor(s.level), 14, ly);
        gr.dispose();
        return img;
    }

    private static String legendFor(Level l) {
        return switch (l) {
            case LEVEL_0 -> "Rooms with jutting wall sections, long parallel halls, pillar fields, tall halls; dots = lit panels; blue = puddles.";
            case LEVEL_1 -> "Parking garage: drive aisles, yellow stall lines, columns, rare supply/safe rooms; blue = puddles.";
            case POOLROOMS -> "Three storeys: tiled bays, dry tile, bright open rooms, tall hallways, massive halls; dark squares = stair towers.";
            case LEVEL_4 -> "Office blocks split into rooms; grey / light-blue carpet sections, corridors, glass meeting rooms.";
            case PLAINS -> "Grass with moss and coarse-dirt patches, dirt roads, wrong houses. Shading = height.";
        };
    }

    private static Color indoorColumn(MemoryCanvas mc, int lx, int lz, int fy, int[][] lamps, int ix, int iz) {
        for (int y = fy + 1; y < Math.min(mc.maxY(), fy + 18); y++) {
            if (mc.type(lx, y, lz) == Material.LIGHT) {
                lamps[ix][iz] = 1;
                break;
            }
        }
        Material feet = mc.type(lx, fy + 1, lz), head = mc.type(lx, fy + 2, lz), floor = mc.type(lx, fy, lz);
        if (feet == Material.WATER || floor == Material.WATER) {
            int depth = 0;
            for (int y = fy + 1; y > mc.minY() && (mc.type(lx, y, lz) == Material.WATER || mc.type(lx, y, lz) == Material.LIGHT); y--) depth++;
            float t = Math.min(1f, depth / 9f);
            return blend(new Color(0x7CC8F0), new Color(0x0B3F9A), t);
        }
        Color cFeet = colorAt(mc, lx, fy + 1, lz), cHead = colorAt(mc, lx, fy + 2, lz), cFloor = colorAt(mc, lx, fy, lz);
        if (!Mats.passable(feet) && Mats.passable(head) && feet != Material.BARREL) return blend(cFeet, Color.WHITE, 0.15f); // raised floor
        boolean open = Mats.passable(feet) && Mats.passable(head);
        if (!open) {
            return blend(Mats.passable(feet) ? cHead : cFeet, Color.BLACK, 0.45f);
        }
        if (!Mats.passable(feet)) return cFeet;
        if (floor == Material.AIR) return new Color(0x050505); // a shaft or stairwell going down
        // low furniture, doors, etc. at foot level
        if (feet != Material.AIR && feet != Material.LIGHT && feet != Material.CAVE_AIR) return blend(cFeet, cFloor, 0.3f);
        return cFloor;
    }

    private static Color outdoorColumn(MemoryCanvas mc, int lx, int lz, int fy, int roof) {
        for (int y = Math.min(mc.maxY() - 1, roof); y > mc.minY(); y--) {
            Material m = mc.type(lx, y, lz);
            if (m == Material.AIR || m == Material.LIGHT || m == Material.BARRIER || m == Material.CAVE_AIR) continue;
            Color c = colorAt(mc, lx, y, lz);
            float shade = Math.max(-0.5f, Math.min(0.5f, (y - fy) / 16f));
            if (m == Material.WATER) {
                int d = 0;
                for (int yy = y; yy > mc.minY() && mc.type(lx, yy, lz) == Material.WATER; yy--) d++;
                return blend(new Color(0x5AA8E8), new Color(0x0B3F9A), Math.min(1f, d / 10f));
            }
            return shade >= 0 ? blend(c, Color.WHITE, shade * 0.6f) : blend(c, Color.BLACK, -shade);
        }
        return Color.BLACK;
    }

    private static Color blend(Color a, Color b, float t) {
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }
}
