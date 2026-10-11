package dev.powderpass.core.deco;

import dev.powderpass.core.util.Hash;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Procedural natural trees and bushes built from vanilla logs/leaves. Shapes are pure functions of (species, anchor,
 * seed). Every leaf is given its correct vanilla {@code distance} to the nearest log of the same tree (computed by
 * BFS over the tree's own voxels), so leaves do not decay; leaves farther than 6 from any log are not emitted.
 */
public final class Trees {
    private Trees() {}

    public enum Species {
        OAK("oak", "oak"), BIG_OAK("dark_oak", "dark_oak"), BIRCH("birch", "birch"), SPRUCE("spruce", "spruce"),
        PINE("spruce", "spruce"), MEGA_SPRUCE("spruce", "spruce"), KRUMMHOLZ("spruce", "spruce"),
        ACACIA("acacia", "acacia"), JUNGLE("jungle", "jungle"), MEGA_JUNGLE("jungle", "jungle"),
        WILLOW("oak", "oak"), MANGROVE("mangrove", "mangrove"), SNAG("oak", null), DEAD_SPRUCE("spruce", null);

        public final String wood, leaves;

        Species(String wood, String leaves) {
            this.wood = wood;
            this.leaves = leaves;
        }
    }

    public enum BushKind { OAK, SPRUCE, BIRCH, ACACIA, JUNGLE, AZALEA, FLOWERING_AZALEA }

    /** A tree's voxels relative to its anchor (anchor = top solid block of the ground under the trunk). */
    public static final class Shape {
        final Map<Long, int[]> vox = new HashMap<>(); // key -> {dx,dy,dz,type}; type 0 log(y),1 log(x),2 log(z),3 leaf,4 vine(face),5 roots
        final List<int[]> vines = new ArrayList<>();
        int maxDy;

        static long key(int dx, int dy, int dz) {
            return ((long) (dx + 512) << 40) | ((long) (dy + 512) << 20) | (dz + 512);
        }

        void log(int dx, int dy, int dz, int axisType) {
            vox.put(key(dx, dy, dz), new int[]{dx, dy, dz, axisType});
            maxDy = Math.max(maxDy, dy);
        }

        void leaf(int dx, int dy, int dz) {
            long k = key(dx, dy, dz);
            if (!vox.containsKey(k)) vox.put(k, new int[]{dx, dy, dz, 3});
            maxDy = Math.max(maxDy, dy);
        }

        void roots(int dx, int dy, int dz) {
            vox.put(key(dx, dy, dz), new int[]{dx, dy, dz, 5});
        }

        int[] get(int dx, int dy, int dz) {
            return vox.get(key(dx, dy, dz));
        }
    }

    public static int height(Species sp, long h) {
        double u = Hash.unit(h);
        return switch (sp) {
            case OAK, WILLOW -> 5 + (int) (u * 3);
            case BIG_OAK -> 7 + (int) (u * 4);
            case BIRCH -> 6 + (int) (u * 4);
            case SPRUCE -> 9 + (int) (u * 7);
            case PINE -> 12 + (int) (u * 7);
            case MEGA_SPRUCE -> 18 + (int) (u * 9);
            case KRUMMHOLZ -> 2 + (int) (u * 2);
            case ACACIA -> 5 + (int) (u * 3);
            case JUNGLE -> 8 + (int) (u * 6);
            case MEGA_JUNGLE -> 16 + (int) (u * 10);
            case MANGROVE -> 6 + (int) (u * 4);
            case SNAG, DEAD_SPRUCE -> 4 + (int) (u * 5);
        };
    }

    /** Horizontal crown radius used for spacing. */
    public static int radius(Species sp) {
        return switch (sp) {
            case OAK, WILLOW, ACACIA, MANGROVE -> 4;
            case BIG_OAK, MEGA_JUNGLE -> 6;
            case BIRCH, SPRUCE, KRUMMHOLZ, SNAG, DEAD_SPRUCE -> 3;
            case PINE, JUNGLE -> 4;
            case MEGA_SPRUCE -> 5;
        };
    }

    public static boolean twoByTwo(Species sp) {
        return sp == Species.BIG_OAK || sp == Species.MEGA_SPRUCE || sp == Species.MEGA_JUNGLE;
    }

    public static Shape build(Species sp, long seed, double windX, double windZ) {
        Shape s = new Shape();
        int H = height(sp, seed);
        long r = seed;
        switch (sp) {
            case OAK -> {
                trunk(s, H, false);
                blob(s, 0, H, 0, 2.6, 2.0, r);
                int branches = 1 + (int) (Hash.unit(Hash.mix(r + 1)) * 3);
                for (int b = 0; b < branches; b++) {
                    double a = Hash.unit(Hash.mix(r + 10 + b)) * Math.PI * 2;
                    int bx = (int) Math.round(Math.cos(a) * 2), bz = (int) Math.round(Math.sin(a) * 2);
                    int by = H - 2 - b;
                    branch(s, 0, by, 0, bx, by + 1, bz);
                    blob(s, bx, by + 1, bz, 2.0, 1.5, Hash.mix(r + 20 + b));
                }
            }
            case WILLOW -> {
                trunk(s, H, false);
                blob(s, 0, H, 0, 3.2, 1.6, r);
                // Drooping vines on the crown's outer faces.
                for (int i = 0; i < 10; i++) {
                    double a = Hash.unit(Hash.mix(r + 40 + i)) * Math.PI * 2;
                    int vx = (int) Math.round(Math.cos(a) * 4), vz = (int) Math.round(Math.sin(a) * 4);
                    s.vines.add(new int[]{vx, H, vz, 1 + (int) (Hash.unit(Hash.mix(r + 60 + i)) * 4)});
                }
            }
            case BIG_OAK -> {
                trunk(s, H, true);
                blob(s, 0, H, 0, 4.0, 2.4, r);
                for (int b = 0; b < 4; b++) {
                    double a = (b + Hash.unit(Hash.mix(r + 10 + b))) * Math.PI / 2;
                    int bx = (int) Math.round(Math.cos(a) * 3.5), bz = (int) Math.round(Math.sin(a) * 3.5);
                    branch(s, 0, H - 3, 0, bx, H - 1, bz);
                    blob(s, bx, H - 1, bz, 2.6, 1.8, Hash.mix(r + 20 + b));
                }
                rootFlare(s, true, r);
            }
            case BIRCH -> {
                trunk(s, H, false);
                ellipsoid(s, 0, H - 1, 0, 2.0, 3.2, r);
            }
            case SPRUCE, MEGA_SPRUCE -> {
                boolean big = sp == Species.MEGA_SPRUCE;
                trunk(s, H, big);
                int start = big ? 6 : 2 + (int) (Hash.unit(Hash.mix(r + 3)) * 2);
                double maxR = big ? 4.5 : 2.8;
                for (int y = start; y <= H + 1; y++) {
                    double t = (double) (y - start) / Math.max(1, H + 1 - start);
                    double rr = maxR * (1 - t) + 0.6;
                    if (((y - start) % 2 == 1) && t < 0.85) rr *= 0.55; // whorl gaps
                    disc(s, big ? 0.5 : 0, y, big ? 0.5 : 0, rr);
                }
                s.leaf(0, H + 1, 0);
                s.leaf(0, H + 2, 0);
                if (big) rootFlare(s, true, r);
            }
            case PINE -> {
                trunk(s, H, false);
                for (int y = H - 4; y <= H + 1; y++) {
                    double t = (double) (y - (H - 4)) / 5.0;
                    disc(s, 0, y, 0, 2.6 * Math.sin(Math.PI * (0.2 + 0.8 * t)) + 0.5);
                }
            }
            case KRUMMHOLZ -> {
                // Wind-shaped: stunted trunk leaning downwind, foliage streamed to the lee side, hugging the ground.
                int lx = (int) Math.round(windX), lz = (int) Math.round(windZ);
                for (int y = 1; y <= H; y++) s.log(0, y, 0, 0);
                if (lx != 0 || lz != 0) s.log(lx, H, lz, lx != 0 ? 1 : 2);
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 1; dy <= H + 1; dy++) {
                    double along = dx * windX + dz * windZ;
                    double side = Math.abs(dx * windZ - dz * windX);
                    if (along < -0.6 || along > 2.6 || side > 1.6 - dy * 0.2) continue;
                    s.leaf(dx, dy, dz);
                }
            }
            case ACACIA -> {
                int bendAt = 2 + (int) (Hash.unit(Hash.mix(r + 5)) * 2);
                double a = Hash.unit(Hash.mix(r + 6)) * Math.PI * 2;
                int ox = 0, oz = 0;
                for (int y = 1; y <= H; y++) {
                    if (y > bendAt) {
                        ox = (int) Math.round(Math.cos(a) * (y - bendAt));
                        oz = (int) Math.round(Math.sin(a) * (y - bendAt));
                    }
                    s.log(ox, y, oz, 0);
                }
                flatCrown(s, ox, H + 1, oz, 3.2);
                flatCrown(s, ox, H + 2, oz, 2.0);
            }
            case JUNGLE, MEGA_JUNGLE -> {
                boolean big = sp == Species.MEGA_JUNGLE;
                trunk(s, H, big);
                blob(s, 0, H, 0, big ? 4.5 : 3.0, big ? 2.2 : 1.8, r);
                if (big) {
                    for (int b = 0; b < 3; b++) {
                        double a = (b * 2.1 + Hash.unit(Hash.mix(r + b))) ;
                        int bx = (int) Math.round(Math.cos(a) * 4), bz = (int) Math.round(Math.sin(a) * 4);
                        int by = H - 5 - b * 2;
                        branch(s, 0, by, 0, bx, by + 2, bz);
                        blob(s, bx, by + 2, bz, 2.4, 1.4, Hash.mix(r + 30 + b));
                    }
                    rootFlare(s, true, r);
                }
                for (int i = 0; i < 6; i++) {
                    double a = Hash.unit(Hash.mix(r + 70 + i)) * Math.PI * 2;
                    s.vines.add(new int[]{(int) Math.round(Math.cos(a) * (big ? 5 : 4)), H, (int) Math.round(Math.sin(a) * (big ? 5 : 4)), 2 + (int) (Hash.unit(Hash.mix(r + 80 + i)) * 5)});
                }
            }
            case MANGROVE -> {
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) != 1) continue;
                    s.roots(dx, 1, dz);
                    s.roots(dx * 2, 0, dz * 2);
                }
                s.roots(0, 1, 0);
                for (int y = 2; y <= H; y++) s.log(0, y, 0, 0);
                blob(s, 0, H, 0, 3.0, 1.8, r);
            }
            case SNAG, DEAD_SPRUCE -> {
                trunk(s, H, false);
                if (Hash.unit(Hash.mix(r + 2)) < 0.6) {
                    int dx = Hash.unit(Hash.mix(r + 3)) < 0.5 ? 1 : -1;
                    s.log(dx, H - 2, 0, 1);
                }
            }
        }
        return s;
    }

    public static Shape bush(BushKind kind, long seed) {
        Shape s = new Shape();
        s.log(0, 1, 0, 0);
        double rx = 1.4 + Hash.unit(seed) * 1.2;
        double ry = 1.0 + Hash.unit(Hash.mix(seed)) * 0.8;
        for (int dx = -3; dx <= 3; dx++) for (int dy = 0; dy <= 3; dy++) for (int dz = -3; dz <= 3; dz++) {
            double e = (dx * dx + dz * dz) / (rx * rx) + MathSq(dy - 1.2) / (ry * ry);
            double jitter = (Hash.unit(Hash.hash(seed, dx * 31 + dz, dy)) - 0.5) * 0.5;
            if (e + jitter <= 1.0 && dy >= 1) s.leaf(dx, dy, dz);
        }
        return s;
    }

    private static double MathSq(double v) {
        return v * v;
    }

    private static void trunk(Shape s, int H, boolean twoByTwo) {
        for (int y = 1; y <= H; y++) {
            s.log(0, y, 0, 0);
            if (twoByTwo) {
                s.log(1, y, 0, 0);
                s.log(0, y, 1, 0);
                s.log(1, y, 1, 0);
            }
        }
    }

    private static void rootFlare(Shape s, boolean twoByTwo, long r) {
        int[][] dirs = {{-1, 0}, {2, 0}, {0, -1}, {0, 2}, {-1, 1}, {2, 1}, {1, -1}, {1, 2}};
        for (int i = 0; i < dirs.length; i++) {
            if (Hash.unit(Hash.mix(r + 100 + i)) < 0.55) {
                int ax = Math.abs(dirs[i][0] - 0) > 0 && dirs[i][1] >= 0 && dirs[i][1] <= 1 ? 1 : 2;
                s.log(dirs[i][0], 1, dirs[i][1], ax);
            }
        }
    }

    private static void branch(Shape s, int x0, int y0, int z0, int x1, int y1, int z1) {
        int steps = Math.max(Math.abs(x1 - x0), Math.max(Math.abs(y1 - y0), Math.abs(z1 - z0)));
        for (int i = 1; i <= steps; i++) {
            int x = x0 + Math.round((x1 - x0) * i / (float) steps);
            int y = y0 + Math.round((y1 - y0) * i / (float) steps);
            int z = z0 + Math.round((z1 - z0) * i / (float) steps);
            int axis = Math.abs(x1 - x0) >= Math.abs(z1 - z0) ? (Math.abs(x1 - x0) > Math.abs(y1 - y0) ? 1 : 0) : (Math.abs(z1 - z0) > Math.abs(y1 - y0) ? 2 : 0);
            s.log(x, y, z, axis);
        }
    }

    private static void blob(Shape s, int cx, int cy, int cz, double r, double ry, long seed) {
        int R = (int) Math.ceil(r) + 1;
        for (int dx = -R; dx <= R; dx++) for (int dy = -R; dy <= R; dy++) for (int dz = -R; dz <= R; dz++) {
            double e = (dx * dx + dz * dz) / (r * r) + (dy * dy) / (ry * ry);
            double jitter = (Hash.unit(Hash.hash(seed, (cx + dx) * 73 + (cz + dz), cy + dy)) - 0.5) * 0.35;
            if (e + jitter <= 1.0) s.leaf(cx + dx, cy + dy, cz + dz);
        }
    }

    private static void ellipsoid(Shape s, int cx, int cy, int cz, double r, double ry, long seed) {
        blob(s, cx, cy, cz, r, ry, seed);
    }

    private static void disc(Shape s, double cx, int y, double cz, double r) {
        int R = (int) Math.ceil(r + 1);
        for (int dx = -R; dx <= R + 1; dx++) for (int dz = -R; dz <= R + 1; dz++) {
            double ex = dx - cx, ez = dz - cz;
            if (ex * ex + ez * ez <= r * r) s.leaf(dx, y, dz);
        }
    }

    private static void flatCrown(Shape s, int cx, int y, int cz, double r) {
        int R = (int) Math.ceil(r);
        for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
            if (dx * dx + dz * dz <= r * r && !(Math.abs(dx) == R && Math.abs(dz) == R)) s.leaf(cx + dx, y, cz + dz);
        }
    }

    /**
     * Emits block states for a shape at an anchor, computing vanilla leaf distances. Only leaves with distance <= 6
     * are emitted (others would decay). {@code maxY} clips anything above the generation cap.
     */
    public static void emit(Shape s, Species sp, BushKind bush, int ax, int ay, int az, int maxY, Placements out) {
        String wood = sp != null ? sp.wood : bushWood(bush);
        String leaves = sp != null ? sp.leaves : bushLeaves(bush);
        // BFS leaf distance from logs (6-connectivity, as vanilla).
        Map<Long, Integer> dist = new HashMap<>();
        ArrayList<int[]> queue = new ArrayList<>();
        for (int[] v : s.vox.values()) {
            if (v[3] <= 2) {
                dist.put(Shape.key(v[0], v[1], v[2]), 0);
                queue.add(v);
            }
        }
        int head = 0;
        int[][] n6 = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (head < queue.size()) {
            int[] v = queue.get(head++);
            int d = dist.get(Shape.key(v[0], v[1], v[2]));
            if (d >= 6) continue;
            for (int[] o : n6) {
                int[] nv = s.get(v[0] + o[0], v[1] + o[1], v[2] + o[2]);
                if (nv == null || nv[3] != 3) continue;
                long k = Shape.key(nv[0], nv[1], nv[2]);
                if (dist.containsKey(k)) continue;
                dist.put(k, d + 1);
                queue.add(nv);
            }
        }
        for (int[] v : s.vox.values()) {
            int y = ay + v[1];
            if (y > maxY) continue;
            int x = ax + v[0], z = az + v[2];
            switch (v[3]) {
                case 0 -> out.add(x, y, z, "minecraft:" + wood + "_log[axis=y]", Placements.MODE_AIR);
                case 1 -> out.add(x, y, z, "minecraft:" + wood + "_log[axis=x]", Placements.MODE_AIR);
                case 2 -> out.add(x, y, z, "minecraft:" + wood + "_log[axis=z]", Placements.MODE_AIR);
                case 5 -> out.add(x, y, z, "minecraft:mangrove_roots[waterlogged=false]", Placements.MODE_AIR);
                case 3 -> {
                    if (leaves == null) break;
                    Integer d = dist.get(Shape.key(v[0], v[1], v[2]));
                    if (d == null || d > 6) break;
                    out.add(x, y, z, "minecraft:" + leaves + "_leaves[distance=" + d + ",persistent=false,waterlogged=false]", Placements.MODE_AIR);
                }
                default -> { }
            }
        }
        // Vines hang on the outer face of crown leaves.
        for (int[] vn : s.vines) {
            int vx = vn[0], vy = vn[1], vz = vn[2], len = vn[3];
            // Find the outermost leaf toward the trunk at this height and attach on its outer side.
            int sx = Integer.signum(vx), sz = Integer.signum(vz);
            int lx = vx, lz = vz, ly = vy;
            boolean found = false;
            for (int step = 0; step < 6 && !found; step++) {
                for (int dy = 1; dy >= -2 && !found; dy--) {
                    int[] l = s.get(lx, vy + dy, lz);
                    if (l != null && l[3] == 3) { ly = vy + dy; found = true; }
                }
                if (!found) { lx -= sx; lz -= sz; }
            }
            if (!found) continue;
            String face;
            int px = lx, pz = lz;
            if (Math.abs(sx) >= Math.abs(sz) && sx != 0) { px = lx + sx; face = sx > 0 ? "west" : "east"; }
            else if (sz != 0) { pz = lz + sz; face = sz > 0 ? "north" : "south"; }
            else continue;
            if (s.get(px, ly, pz) != null) continue;
            for (int i = 0; i < len; i++) {
                int y = ay + ly - i;
                if (s.get(px, ly - i, pz) != null || y > maxY) break;
                out.add(ax + px, y, az + pz, "minecraft:vine[" + face + "=true]", Placements.MODE_AIR);
            }
        }
    }

    private static String bushWood(BushKind k) {
        return switch (k) {
            case OAK, AZALEA, FLOWERING_AZALEA -> "oak";
            case SPRUCE -> "spruce";
            case BIRCH -> "birch";
            case ACACIA -> "acacia";
            case JUNGLE -> "jungle";
        };
    }

    private static String bushLeaves(BushKind k) {
        return switch (k) {
            case OAK -> "oak";
            case SPRUCE -> "spruce";
            case BIRCH -> "birch";
            case ACACIA -> "acacia";
            case JUNGLE -> "jungle";
            case AZALEA -> "azalea";
            case FLOWERING_AZALEA -> "flowering_azalea";
        };
    }
}
