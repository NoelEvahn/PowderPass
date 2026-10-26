package dev.powderpass.volleyball.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Helpers for spawning tagged, non-persistent display entities. */
public final class Displays {

    private Displays() {
    }

    /** Marks an entity as owned by the plugin so stray copies can always be cleaned up. */
    public static void tag(Entity e, String owner) {
        e.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, owner);
        e.setPersistent(false);
        e.setSilent(true);
        e.setInvulnerable(true);
    }

    public static BlockDisplay block(World world, Location at, Material material, Matrix4f matrix, float viewRange,
                                     String owner) {
        return world.spawn(at, BlockDisplay.class, d -> {
            d.setBlock(material.createBlockData());
            d.setTransformationMatrix(matrix);
            d.setViewRange(viewRange);
            d.setShadowRadius(0);
            d.setShadowStrength(0);
            tag(d, owner);
        });
    }

    /** Axis aligned box display spanning [min, min + size] relative to {@code at}. */
    public static Matrix4f box(double ox, double oy, double oz, double sx, double sy, double sz) {
        return new Matrix4f().translate((float) ox, (float) oy, (float) oz).scale((float) sx, (float) sy, (float) sz);
    }

    /**
     * Thin bar from a to b (relative to the entity position) with a rectangular cross-section
     * (thickness across, height up). Works for any direction.
     */
    public static Matrix4f bar(Vector3f a, Vector3f b, float thickness, float height) {
        Vector3f dir = new Vector3f(b).sub(a);
        float len = dir.length();
        Matrix4f m = new Matrix4f().translate(a);
        if (len < 1e-5f) {
            return m.scale(0.0001f);
        }
        dir.div(len);
        Quaternionf q = new Quaternionf().rotationTo(1, 0, 0, dir.x, dir.y, dir.z);
        return m.rotate(q).scale(len, height, thickness).translate(0, -0.5f, -0.5f);
    }

    public static void refreshInterpolation(Display d, int ticks) {
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(ticks);
    }
}
