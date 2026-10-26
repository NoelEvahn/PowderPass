package dev.powderpass.volleyball.court;

import dev.powderpass.volleyball.physics.Aabb;
import dev.powderpass.volleyball.physics.CollisionWorld;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

import java.util.List;

/** Exposes real block collision shapes (slabs, stairs, fences, barriers...) to the ball physics. */
public final class BukkitCollisionWorld implements CollisionWorld {

    private final World world;

    public BukkitCollisionWorld(World world) {
        this.world = world;
    }

    @Override
    public void collect(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, List<Aabb> out) {
        int x0 = (int) Math.floor(minX);
        int y0 = Math.max(world.getMinHeight(), (int) Math.floor(minY));
        int z0 = (int) Math.floor(minZ);
        int x1 = (int) Math.floor(maxX);
        int y1 = Math.min(world.getMaxHeight() - 1, (int) Math.floor(maxY));
        int z1 = (int) Math.floor(maxZ);
        // guard against absurd queries from a runaway ball
        if ((long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > 4096) {
            return;
        }
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    continue;
                }
                for (int y = y0; y <= y1; y++) {
                    Block b = world.getBlockAt(x, y, z);
                    Material type = b.getType();
                    if (type.isAir() || !type.isCollidable()) {
                        continue;
                    }
                    for (BoundingBox bb : b.getCollisionShape().getBoundingBoxes()) {
                        out.add(new Aabb(x + bb.getMinX(), y + bb.getMinY(), z + bb.getMinZ(),
                                x + bb.getMaxX(), y + bb.getMaxY(), z + bb.getMaxZ()));
                    }
                }
            }
        }
    }
}
