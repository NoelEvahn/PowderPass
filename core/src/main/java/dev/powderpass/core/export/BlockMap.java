package dev.powderpass.core.export;

import dev.powderpass.core.GenSettings;
import dev.powderpass.core.PowderWorld;
import dev.powderpass.core.deco.Decorator;
import dev.powderpass.core.deco.Placements;
import dev.powderpass.core.terrain.B;
import dev.powderpass.core.terrain.ChunkComposer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Developer tool: composes and decorates real chunks with the generation core and renders the top-most block of
 * every column (after decoration), with simple height shading. This shows actual block choices (materials,
 * vegetation, water, slabs) at 1:1, but it is a diagnostic map, not an in-game screenshot.
 *
 * <p>Usage: {@code BlockMap <seed> <centerX> <centerZ> <sizeBlocks> <outDir>}
 */
public final class BlockMap {
    public static void main(String[] a) throws Exception {
        long seed = Long.parseLong(a[0]);
        int cx = Integer.parseInt(a[1]), cz = Integer.parseInt(a[2]), size = Integer.parseInt(a[3]);
        File out = new File(a[4]);
        PowderWorld w = new PowderWorld(seed, GenSettings.defaults(), 2L << 30, true);
        ChunkComposer comp = new ChunkComposer(w, GenSettings.MIN_Y, 832);
        Decorator deco = new Decorator(comp);
        int x0 = Math.floorDiv(cx - size / 2, 16) * 16, z0 = Math.floorDiv(cz - size / 2, 16) * 16;
        int chunks = size / 16;
        int[] topY = new int[size * size];
        String[] topId = new String[size * size];
        IntStream.range(0, chunks * chunks).parallel().forEach(ci -> {
            int ccx = Math.floorDiv(x0, 16) + ci % chunks, ccz = Math.floorDiv(z0, 16) + ci / chunks;
            ChunkComposer.Result r = comp.compose(ccx, ccz);
            Placements p = deco.decorate(ccx, ccz, r);
            int h = comp.height();
            Map<Long, String[]> placed = new HashMap<>();
            for (int i = 0; i < p.size; i++) {
                long k = ((long) p.x[i] << 32) ^ (p.z[i] & 0xffffffffL);
                String[] cur = placed.get(k);
                if (cur == null || Integer.parseInt(cur[1]) < p.y[i]) placed.put(k, new String[]{Placements.state(p.st[i]), String.valueOf(p.y[i])});
            }
            for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
                int base = (lz * 16 + lx) * h;
                int top = r.columnMax[lz * 16 + lx];
                int gx = ccx * 16 + lx, gz = ccz * 16 + lz;
                String id = B.byOrdinal(r.blocks[base + (top - comp.minY())]).id;
                int y = top;
                String[] d = placed.get(((long) gx << 32) ^ (gz & 0xffffffffL));
                if (d != null && Integer.parseInt(d[1]) >= y) {
                    id = d[0];
                    y = Integer.parseInt(d[1]);
                }
                int px = gx - x0, pz = gz - z0;
                if (px >= 0 && pz >= 0 && px < size && pz < size) {
                    topY[pz * size + px] = y;
                    topId[pz * size + px] = id;
                }
            }
        });
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        double[] hh = new double[size * size];
        for (int i = 0; i < hh.length; i++) hh[i] = topY[i];
        for (int z = 0; z < size; z++) for (int x = 0; x < size; x++) {
            int k = z * size + x;
            int col = color(topId[k]);
            img.setRGB(x, z, Img.shade(col, hh, size, size, x, z, 1.0));
        }
        Img.save(img, new File(out, "blocks_" + seed + "_" + cx + "_" + cz + "_" + size + ".png"));
    }

    static int color(String state) {
        if (state == null) return 0xff00ff;
        String id = state.contains("[") ? state.substring(0, state.indexOf('[')) : state;
        id = id.substring(id.indexOf(':') + 1);
        if (id.endsWith("_leaves")) {
            return switch (id) {
                case "birch_leaves" -> 0x80a755;
                case "spruce_leaves" -> 0x3d6040;
                case "acacia_leaves", "jungle_leaves" -> 0x4f9d2a;
                case "azalea_leaves", "flowering_azalea_leaves" -> 0x6d9a3a;
                case "mangrove_leaves" -> 0x58a032;
                case "dark_oak_leaves" -> 0x3f6b24;
                default -> 0x4f8a2a;
            };
        }
        if (id.endsWith("_log")) return 0x6b5232;
        if (id.endsWith("_slab")) return 0x9a9a9a;
        if (id.endsWith("_terracotta") || id.equals("terracotta")) {
            return switch (id) {
                case "white_terracotta" -> 0xd1b2a1; case "orange_terracotta" -> 0xa15325; case "yellow_terracotta" -> 0xba8523;
                case "red_terracotta" -> 0x8f3d2e; case "brown_terracotta" -> 0x4d3324; case "light_gray_terracotta" -> 0x876b62;
                default -> 0x985e43;
            };
        }
        return switch (id) {
            case "grass_block" -> 0x7cbd6b; case "short_grass", "tall_grass", "fern", "large_fern" -> 0x6fae5a;
            case "dirt", "coarse_dirt", "rooted_dirt" -> 0x8b6a4a; case "podzol" -> 0x6a4b2a; case "mud", "packed_mud" -> 0x3c3837;
            case "moss_block", "moss_carpet" -> 0x5a7a2e; case "sand" -> 0xdbd3a0; case "red_sand" -> 0xbe6621;
            case "sandstone", "smooth_sandstone" -> 0xd8cb94; case "red_sandstone" -> 0xb5621f; case "gravel" -> 0x857f7d;
            case "stone" -> 0x7d7d7d; case "andesite" -> 0x888889; case "granite" -> 0x9a6b56; case "diorite" -> 0xbcbcbc;
            case "calcite" -> 0xdfe0dc; case "tuff" -> 0x6c6d66; case "basalt", "smooth_basalt" -> 0x4a4a4f;
            case "blackstone" -> 0x2a2328; case "magma_block" -> 0x8e3f1f; case "deepslate" -> 0x4d4d50;
            case "snow_block", "snow" -> 0xf5fafa; case "ice", "packed_ice" -> 0x9cc2f5; case "water", "seagrass" -> 0x3f76e4;
            case "lily_pad" -> 0x208030; case "clay" -> 0xa0a6b3; case "sugar_cane" -> 0x94c065; case "cactus" -> 0x5c8e2b;
            case "dead_bush", "short_dry_grass", "tall_dry_grass" -> 0xa58b52; case "vine" -> 0x3e7a1e;
            case "dandelion", "sunflower" -> 0xf1d22a; case "poppy", "rose_bush" -> 0xd62a1f; case "cornflower" -> 0x4a6ce0;
            case "azure_bluet", "oxeye_daisy", "lily_of_the_valley", "white_tulip" -> 0xe6e6e6; case "allium" -> 0xb06ad8;
            case "blue_orchid" -> 0x2fa8e0; case "orange_tulip" -> 0xe07a2a; case "pink_petals", "wildflowers" -> 0xe8a0c8;
            case "sweet_berry_bush", "bush", "firefly_bush" -> 0x4f7a32; case "leaf_litter" -> 0x9a7a3a;
            case "mangrove_roots" -> 0x5a4630; case "lava" -> 0xff6a00; case "bedrock" -> 0x333333;
            default -> 0xff00ff;
        };
    }
}
