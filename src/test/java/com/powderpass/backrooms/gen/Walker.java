package com.powderpass.backrooms.gen;

import org.bukkit.Material;

import java.util.ArrayDeque;
import java.util.BitSet;

/**
 * Flood fill of player-reachable feet positions: walk, step/jump up one block, drop down, swim, climb ladders.
 * Deliberately conservative (no parkour), so passing tests mean ordinary players can get everywhere.
 */
final class Walker {
    final Area a;
    final int y0, y1;
    final BitSet seen = new BitSet();
    final int sx, sz, sy;

    Walker(Area a, int y0, int y1) {
        this.a = a;
        this.y0 = y0;
        this.y1 = y1;
        this.sx = a.maxX - a.minX;
        this.sz = a.maxZ - a.minZ;
        this.sy = y1 - y0 + 1;
    }

    int idx(int x, int y, int z) {
        return ((y - y0) * sz + (z - a.minZ)) * sx + (x - a.minX);
    }

    boolean visited(int x, int y, int z) {
        return a.inXZ(x, z) && y >= y0 && y <= y1 && seen.get(idx(x, y, z));
    }

    /** Any of the given feet positions within the box visited? */
    boolean anyVisited(int x0, int yA, int z0, int x1, int yB, int z1) {
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++)
                for (int y = yA; y <= yB; y++)
                    if (visited(x, y, z)) return true;
        return false;
    }

    void run(int x, int y, int z) {
        ArrayDeque<int[]> q = new ArrayDeque<>();
        if (!a.standable(x, y, z)) throw new IllegalStateException("start not standable " + x + "," + y + "," + z + " feet=" + a.get(x, y, z) + " below=" + a.get(x, y - 1, z));
        seen.set(idx(x, y, z));
        q.add(new int[]{x, y, z});
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] p = q.poll();
            int px = p[0], py = p[1], pz = p[2];
            Material here = a.type(px, py, pz);
            boolean climb = here == Material.LADDER || here == Material.WATER;
            if (climb) {
                push(q, px, py + 1, pz);
                push(q, px, py - 1, pz);
            }
            for (int[] d : dirs) {
                int nx = px + d[0], nz = pz + d[1];
                if (!a.inXZ(nx, nz)) continue;
                // same level or step up one (needs headroom above current position)
                push(q, nx, py, nz);
                if (Area.passable(a.type(px, py + 2, pz)) || climb) push(q, nx, py + 1, nz);
                // drop down
                for (int dy = 1; dy <= 12; dy++) {
                    int ny = py - dy;
                    if (ny < y0) break;
                    if (!Area.passable(a.type(nx, ny + 1, nz))) break;
                    if (a.standable(nx, ny, nz)) {
                        push(q, nx, ny, nz);
                        break;
                    }
                }
            }
        }
    }

    private void push(ArrayDeque<int[]> q, int x, int y, int z) {
        if (!a.inXZ(x, z) || y < y0 || y > y1) return;
        int i = idx(x, y, z);
        if (seen.get(i)) return;
        if (!a.standable(x, y, z)) return;
        seen.set(i);
        q.add(new int[]{x, y, z});
    }
}
