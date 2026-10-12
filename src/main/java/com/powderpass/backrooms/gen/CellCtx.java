package com.powderpass.backrooms.gen;

import org.bukkit.Material;

/**
 * Painting context for one cell of one floor, clipped to the chunk being generated.
 * Painters address blocks in cell-local coordinates (u along X, v along Z, absolute y).
 */
public final class CellCtx {
    public final LevelGenerator gen;
    public final Canvas canvas;
    public final int floor;
    public final int cx;
    public final int cz;
    /** World coordinates of the cell origin (its north-west wall corner). */
    public final int ox;
    public final int oz;
    /** Y of the walkable floor block top of this floor (feet stand at fy + 1). */
    public final int fy;
    /** Inclusive cell-local clip bounds that fall inside the chunk. */
    public final int u0;
    public final int u1;
    public final int v0;
    public final int v1;
    private final int chunkMinX;
    private final int chunkMinZ;

    CellCtx(LevelGenerator gen, Canvas canvas, int floor, int cx, int cz, int chunkMinX, int chunkMinZ) {
        this.gen = gen;
        this.canvas = canvas;
        this.floor = floor;
        this.cx = cx;
        this.cz = cz;
        this.ox = cx * gen.S;
        this.oz = cz * gen.S;
        this.fy = gen.floorY[floor];
        this.chunkMinX = chunkMinX;
        this.chunkMinZ = chunkMinZ;
        this.u0 = Math.max(0, chunkMinX - ox);
        this.u1 = Math.min(gen.S - 1, chunkMinX + 15 - ox);
        this.v0 = Math.max(0, chunkMinZ - oz);
        this.v1 = Math.min(gen.S - 1, chunkMinZ + 15 - oz);
    }

    public boolean in(int u, int v) {
        int lx = ox + u - chunkMinX;
        int lz = oz + v - chunkMinZ;
        return lx >= 0 && lx < 16 && lz >= 0 && lz < 16;
    }

    public void set(int u, int y, int v, B b) {
        int lx = ox + u - chunkMinX;
        int lz = oz + v - chunkMinZ;
        if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
            canvas.set(lx, y, lz, b);
        }
    }

    public Material type(int u, int y, int v) {
        int lx = ox + u - chunkMinX;
        int lz = oz + v - chunkMinZ;
        if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
            return canvas.type(lx, y, lz);
        }
        return Material.AIR;
    }

    /** Inclusive box fill (clipped). */
    public void fill(int ua, int ya, int va, int ub, int yb, int vb, B b) {
        int uMin = Math.max(Math.min(ua, ub), u0), uMax = Math.min(Math.max(ua, ub), u1);
        int vMin = Math.max(Math.min(va, vb), v0), vMax = Math.min(Math.max(va, vb), v1);
        int yMin = Math.min(ya, yb), yMax = Math.max(ya, yb);
        for (int u = uMin; u <= uMax; u++) {
            for (int v = vMin; v <= vMax; v++) {
                for (int y = yMin; y <= yMax; y++) {
                    set(u, y, v, b);
                }
            }
        }
    }

    public void column(int u, int v, int ya, int yb, B b) {
        if (!in(u, v)) {
            return;
        }
        for (int y = ya; y <= yb; y++) {
            set(u, y, v, b);
        }
    }

    public int wx(int u) {
        return ox + u;
    }

    public int wz(int v) {
        return oz + v;
    }

    /** Hash of a block position, salted; convenient for per-block variation. */
    public long h(int u, int y, int v, int salt) {
        return com.powderpass.backrooms.util.Rng.hash(gen.seed ^ salt, ox + u, y, oz + v);
    }
}
