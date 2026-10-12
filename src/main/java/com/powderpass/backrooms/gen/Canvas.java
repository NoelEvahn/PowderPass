package com.powderpass.backrooms.gen;

import org.bukkit.Material;

/** Chunk-local block sink. Coordinates are chunk-relative x/z (0..15) and absolute y. */
public interface Canvas {
    void set(int lx, int y, int lz, B block);

    Material type(int lx, int y, int lz);

    int minY();

    int maxY();
}
