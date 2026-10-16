package com.powderpass.backrooms.gen;

import org.bukkit.Material;

/** In-memory chunk canvas for headless validation (unit tests, layout previews). */
public final class MemoryCanvas implements Canvas {
    private final int minY;
    private final int maxY;
    private final B[] blocks;

    public MemoryCanvas(int minY, int maxY) {
        this.minY = minY;
        this.maxY = maxY;
        this.blocks = new B[16 * 16 * (maxY - minY)];
    }

    private int idx(int lx, int y, int lz) {
        return ((y - minY) * 16 + lz) * 16 + lx;
    }

    @Override
    public void set(int lx, int y, int lz, B block) {
        if (y >= minY && y < maxY && lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
            blocks[idx(lx, y, lz)] = block;
        }
    }

    public B get(int lx, int y, int lz) {
        if (y < minY || y >= maxY) {
            return B.AIR;
        }
        B b = blocks[idx(lx, y, lz)];
        return b == null ? B.AIR : b;
    }

    @Override
    public Material type(int lx, int y, int lz) {
        return get(lx, y, lz).mat;
    }

    @Override
    public int minY() {
        return minY;
    }

    @Override
    public int maxY() {
        return maxY;
    }
}
