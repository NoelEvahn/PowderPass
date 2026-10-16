package com.powderpass.backrooms.gen;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator.ChunkData;

/** Canvas backed by Paper's {@link ChunkData}; only used from the generator callback. */
public final class ChunkDataCanvas implements Canvas {
    private final ChunkData data;

    public ChunkDataCanvas(ChunkData data) {
        this.data = data;
    }

    @Override
    public void set(int lx, int y, int lz, B block) {
        if (y >= data.getMinHeight() && y < data.getMaxHeight()) {
            data.setBlock(lx, y, lz, block.data());
        }
    }

    @Override
    public Material type(int lx, int y, int lz) {
        if (y < data.getMinHeight() || y >= data.getMaxHeight()) {
            return Material.AIR;
        }
        return data.getType(lx, y, lz);
    }

    @Override
    public int minY() {
        return data.getMinHeight();
    }

    @Override
    public int maxY() {
        return data.getMaxHeight();
    }
}
