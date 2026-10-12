package com.powderpass.backrooms;

import com.powderpass.backrooms.gen.Mats;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.block.BlockMock;
import org.mockbukkit.mockbukkit.world.Coordinate;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * MockBukkit server with the few Paper methods the plugin relies on that the mock does not implement
 * (async chunk loading, Block#isPassable). Worlds start empty (all air), like a void.
 */
final class TestServer extends ServerMock {
    @Override
    public World createWorld(WorldCreator creator) {
        World existing = getWorld(creator.name());
        if (existing != null) return existing;
        TestWorld w = new TestWorld(creator);
        addWorld(w);
        return w;
    }

    static final class TestWorld extends WorldMock {
        private final Map<Coordinate, BlockMock> blocks = new HashMap<>();

        TestWorld(WorldCreator c) {
            super(c);
        }

        @Override
        public BlockMock getBlockAt(int x, int y, int z) {
            return getBlockAt(new Coordinate(x, y, z));
        }

        @Override
        public BlockMock getBlockAt(Coordinate c) {
            return blocks.computeIfAbsent(c, k -> new PassableBlock(Material.AIR, new Location(this, k.x, k.y, k.z)));
        }

        @Override
        public CompletableFuture<Chunk> getChunkAtAsync(int x, int z, boolean gen, boolean urgent) {
            return CompletableFuture.completedFuture(getChunkAt(x, z));
        }

        @Override
        public CompletableFuture<Chunk> getChunkAtAsync(int x, int z, boolean gen) {
            return CompletableFuture.completedFuture(getChunkAt(x, z));
        }
    }

    static final class PassableBlock extends BlockMock {
        PassableBlock(Material m, Location l) {
            super(m, l);
        }

        @Override
        public boolean isPassable() {
            return Mats.passable(getType());
        }

        @Override
        public byte getLightFromBlocks() {
            return 0;
        }
    }
}
