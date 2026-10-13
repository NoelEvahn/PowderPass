package com.powderpass.backrooms.creature;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Small bounded A* over real blocks (main thread only). Creatures stay near the ground plane they walk on;
 * steps of one block up or down are allowed. Unloaded chunks are never touched.
 */
final class Nav {
    enum Medium { GROUND, WATER }

    private Nav() {
    }

    static boolean loaded(World w, int x, int z) {
        return w.isChunkLoaded(x >> 4, z >> 4);
    }

    static boolean free(Block b, Medium m) {
        Material t = b.getType();
        if (m == Medium.WATER) return t == Material.WATER;
        return b.isPassable() && t != Material.WATER && t != Material.LAVA;
    }

    static boolean walkable(World w, int x, int y, int z, int height, Medium m) {
        if (!loaded(w, x, z)) return false;
        for (int i = 0; i < height; i++) {
            if (!free(w.getBlockAt(x, y + i, z), m)) return false;
        }
        if (m == Medium.WATER) return true;
        Block below = w.getBlockAt(x, y - 1, z);
        return below.getType().isSolid() || below.getType() == Material.WATER;
    }

    /** Path of block positions (excluding start) toward the target; partial if the target is unreachable. */
    static List<int[]> path(World w, int sx, int sy, int sz, int tx, int ty, int tz, int height, Medium m, int maxNodes) {
        record Node(int x, int y, int z, int g, int f) {
        }
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Integer.compare(a.f, b.f));
        Map<Long, Long> parent = new HashMap<>();
        Map<Long, Integer> best = new HashMap<>();
        long sk = key(sx, sy, sz);
        open.add(new Node(sx, sy, sz, 0, h(sx, sy, sz, tx, ty, tz)));
        best.put(sk, 0);
        long bestKey = sk;
        int bestH = h(sx, sy, sz, tx, ty, tz);
        int expanded = 0;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!open.isEmpty() && expanded < maxNodes) {
            Node n = open.poll();
            expanded++;
            long nk = key(n.x, n.y, n.z);
            int hh = h(n.x, n.y, n.z, tx, ty, tz);
            if (hh < bestH) {
                bestH = hh;
                bestKey = nk;
            }
            if (n.x == tx && n.z == tz && Math.abs(n.y - ty) <= 1) {
                bestKey = nk;
                break;
            }
            for (int[] d : dirs) {
                int nx = n.x + d[0], nz = n.z + d[1];
                for (int dy : m == Medium.WATER ? new int[]{0, 1, -1} : new int[]{0, 1, -1}) {
                    int ny = n.y + dy;
                    if (!walkable(w, nx, ny, nz, height, m)) continue;
                    if (dy == 1 && m == Medium.GROUND && !free(w.getBlockAt(n.x, n.y + height, n.z), m)) continue;
                    long k = key(nx, ny, nz);
                    int g = n.g + 10 + (dy != 0 ? 4 : 0);
                    Integer old = best.get(k);
                    if (old != null && old <= g) continue;
                    best.put(k, g);
                    parent.put(k, nk);
                    open.add(new Node(nx, ny, nz, g, g + h(nx, ny, nz, tx, ty, tz)));
                    break;
                }
            }
        }
        List<int[]> out = new ArrayList<>();
        long cur = bestKey;
        while (cur != sk && parent.containsKey(cur)) {
            out.add(unkey(cur));
            cur = parent.get(cur);
        }
        Collections.reverse(out);
        return out;
    }

    private static int h(int x, int y, int z, int tx, int ty, int tz) {
        return (Math.abs(x - tx) + Math.abs(z - tz) + Math.abs(y - ty)) * 10;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private static int[] unkey(long k) {
        int x = (int) (k >> 38);
        int z = (int) ((k >> 12) & 0x3FFFFFF);
        int y = (int) (k & 0xFFF);
        if ((x & 0x2000000) != 0) x |= ~0x3FFFFFF;
        if ((z & 0x2000000) != 0) z |= ~0x3FFFFFF;
        if ((y & 0x800) != 0) y |= ~0xFFF;
        return new int[]{x, y, z};
    }
}
