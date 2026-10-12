package dev.powderpass.ski;

import dev.powderpass.config.PluginConfig;
import dev.powderpass.physics.SurfaceKind;
import dev.powderpass.physics.TerrainSampler;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Snow;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link TerrainSampler} over a Bukkit world. Column lookups are cached for a short time because the physics
 * queries the same columns many times per tick. Unloaded chunks read as walls / no surface and are never loaded.
 */
public final class WorldTerrain implements TerrainSampler {

    private static final int CACHE_SIZE = 2048;
    private static final int CACHE_TTL = 40;
    private static final int SEARCH_UP = 4;
    private static final int SEARCH_DOWN = 14;

    private record Column(double top, SurfaceKind kind, double hint, long stamp) {
    }

    private final World world;
    private final PluginConfig config;
    private final int minY;
    private final int maxY;
    private long now;
    private final Map<Long, Column> cache = new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Column> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    public WorldTerrain(World world, PluginConfig config) {
        this.world = world;
        this.config = config;
        this.minY = world.getMinHeight();
        this.maxY = world.getMaxHeight() - 1;
    }

    public World world() {
        return world;
    }

    /** Advances the cache clock; call once per tick. */
    public void tick(long tick) {
        this.now = tick;
    }

    @Override
    public double columnTop(int x, int z, double yHint) {
        return column(x, z, yHint).top;
    }

    @Override
    public SurfaceKind surface(int x, int z, double yHint) {
        return column(x, z, yHint).kind;
    }

    @Override
    public boolean solidAt(int x, int y, int z) {
        if (y < minY || y > maxY) {
            return false;
        }
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return true;
        }
        Block b = world.getBlockAt(x, y, z);
        Material type = b.getType();
        if (type == Material.SNOW || type == Material.POWDER_SNOW || type.isAir()) {
            return false;
        }
        return !b.isPassable();
    }

    private Column column(int x, int z, double yHint) {
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        Column cached = cache.get(key);
        if (cached != null && now - cached.stamp <= CACHE_TTL && Math.abs(cached.hint - yHint) < 1.0) {
            return cached;
        }
        Column fresh = scan(x, z, yHint);
        cache.put(key, fresh);
        return fresh;
    }

    private Column scan(int x, int z, double yHint) {
        if (!world.isChunkLoaded(x >> 4, z >> 4) || Double.isNaN(yHint)) {
            return new Column(Double.NaN, SurfaceKind.NONE, yHint, now);
        }
        int start = clampY((int) Math.floor(yHint + 1.6));
        Block b = world.getBlockAt(x, start, z);
        // A solid block with air beneath it is an overhang (branches, cave ceiling): look below it instead.
        boolean overhang = isSurface(b) && start - 1 >= minY && !isSurface(world.getBlockAt(x, start - 1, z));
        if (overhang) {
            start--;
        } else if (isSurface(b)) {
            // Inside terrain: climb to the top of this wall / step.
            for (int up = 1; up <= SEARCH_UP; up++) {
                int y = start + up;
                if (y > maxY) {
                    break;
                }
                Block above = world.getBlockAt(x, y, z);
                if (!isSurface(above)) {
                    Block top = world.getBlockAt(x, y - 1, z);
                    return new Column(topOf(top), kindOf(top), yHint, now);
                }
            }
            return new Column(start + SEARCH_UP + 1.0, SurfaceKind.ROUGH, yHint, now);
        }
        for (int down = 1; down <= SEARCH_DOWN; down++) {
            int y = start - down;
            if (y < minY) {
                break;
            }
            Block below = world.getBlockAt(x, y, z);
            if (isSurface(below)) {
                return new Column(topOf(below), kindOf(below), yHint, now);
            }
        }
        return new Column(Double.NaN, SurfaceKind.NONE, yHint, now);
    }

    private boolean isSurface(Block b) {
        Material type = b.getType();
        if (type.isAir()) {
            return false;
        }
        if (type == Material.SNOW || type == Material.POWDER_SNOW || b.isLiquid()) {
            return true;
        }
        return !b.isPassable();
    }

    private double topOf(Block b) {
        Material type = b.getType();
        int y = b.getY();
        if (type == Material.SNOW) {
            BlockData data = b.getBlockData();
            int layers = data instanceof Snow snow ? snow.getLayers() : 1;
            return y + layers / 8.0;
        }
        if (type == Material.POWDER_SNOW) {
            return y + 0.9;
        }
        if (b.isLiquid()) {
            return y + 0.88;
        }
        if (type.isOccluding()) {
            return y + 1.0;
        }
        VoxelShape shape = b.getCollisionShape();
        double max = 0.0;
        for (BoundingBox box : shape.getBoundingBoxes()) {
            max = Math.max(max, box.getMaxY());
        }
        return y + (max <= 0.0 ? 1.0 : Math.min(1.5, max));
    }

    private SurfaceKind kindOf(Block b) {
        Material type = b.getType();
        if (b.isLiquid() || type == Material.BUBBLE_COLUMN) {
            return SurfaceKind.LIQUID;
        }
        if (config.powderBlocks.contains(type)) {
            return SurfaceKind.POWDER;
        }
        if (config.snowBlocks.contains(type)) {
            return SurfaceKind.SNOW;
        }
        if (config.iceBlocks.contains(type)) {
            return SurfaceKind.ICE;
        }
        return SurfaceKind.ROUGH;
    }

    private int clampY(int y) {
        return Math.max(minY, Math.min(maxY, y));
    }
}
