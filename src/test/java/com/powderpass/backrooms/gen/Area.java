package com.powderpass.backrooms.gen;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;

/** A painted rectangle of chunks held in memory, addressable in world coordinates. */
final class Area {
    final LevelGenerator gen;
    final int minX, minZ, maxX, maxZ; // block bounds (inclusive min, exclusive max)
    final int minY = -64, maxY = 320;
    private final Map<Long, MemoryCanvas> chunks = new HashMap<>();

    Area(LevelGenerator gen, int minX, int minZ, int maxX, int maxZ) {
        this.gen = gen;
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
        for (int cx = Math.floorDiv(minX, 16); cx <= Math.floorDiv(maxX - 1, 16); cx++) {
            for (int cz = Math.floorDiv(minZ, 16); cz <= Math.floorDiv(maxZ - 1, 16); cz++) {
                MemoryCanvas c = new MemoryCanvas(minY, maxY);
                gen.paint(c, cx, cz);
                chunks.put(key(cx, cz), c);
            }
        }
    }

    static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    B get(int x, int y, int z) {
        MemoryCanvas c = chunks.get(key(Math.floorDiv(x, 16), Math.floorDiv(z, 16)));
        if (c == null) return B.AIR;
        return c.get(Math.floorMod(x, 16), y, Math.floorMod(z, 16));
    }

    Material type(int x, int y, int z) {
        return get(x, y, z).mat;
    }

    boolean inXZ(int x, int z) {
        return x >= minX && x < maxX && z >= minZ && z < maxZ;
    }

    static boolean passable(Material m) {
        return Mats.passable(m) || m == Material.LANTERN;
    }

    /** Can a player's feet be at this block? */
    boolean standable(int x, int y, int z) {
        Material feet = type(x, y, z), head = type(x, y + 1, z), below = type(x, y - 1, z);
        if (!passable(feet) || !passable(head)) return false;
        return !passable(below) || below == Material.WATER || feet == Material.WATER || feet == Material.LADDER || below == Material.LADDER;
    }
}
