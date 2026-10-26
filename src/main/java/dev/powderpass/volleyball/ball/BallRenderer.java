package dev.powderpass.volleyball.ball;

import dev.powderpass.volleyball.config.Settings;
import dev.powderpass.volleyball.util.Displays;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a ball as a voxel sphere of block displays. The first part is the root; the others ride it as
 * passengers, so one interpolated teleport moves the whole ball on every client. Spin is applied by rotating
 * every part's transformation around the ball centre.
 */
public final class BallRenderer {

    private final List<BlockDisplay> parts = new ArrayList<>();
    private final List<BallModel.Part> model = new ArrayList<>();
    private final Quaternionf shown = new Quaternionf();
    private float radius;
    private int rotationInterpolation;

    public void spawn(World world, Location at, BallModel ballModel, Settings.Ball cfg) {
        remove();
        radius = (float) cfg.visualSize() / 2f;
        rotationInterpolation = cfg.rotationInterpolation();
        Material[] mats = cfg.colors();
        BlockDisplay root = null;
        for (BallModel.Part p : ballModel.parts()) {
            BlockData data = mats[p.color() % mats.length].createBlockData();
            boolean first = root == null;
            BlockDisplay d = world.spawn(at, BlockDisplay.class, e -> {
                e.setBlock(data);
                e.setTransformationMatrix(matrix(p, shown));
                e.setViewRange(4f);
                e.setBillboard(Display.Billboard.FIXED);
                e.setInterpolationDuration(rotationInterpolation);
                if (first) {
                    e.setTeleportDuration(cfg.teleportInterpolation());
                    if (cfg.shadow()) {
                        e.setShadowRadius(radius * 0.9f);
                        e.setShadowStrength(0.9f);
                    }
                }
                Displays.tag(e, "ball");
            });
            if (first) {
                root = d;
            } else {
                root.addPassenger(d);
            }
            parts.add(d);
            model.add(p);
        }
    }

    private Matrix4f matrix(BallModel.Part p, Quaternionf rotation) {
        return new Matrix4f()
                .rotate(rotation)
                .translate((float) p.minX() * radius, (float) p.minY() * radius, (float) p.minZ() * radius)
                .scale((float) p.sizeX() * radius, (float) p.sizeY() * radius, (float) p.sizeZ() * radius);
    }

    public boolean valid() {
        if (parts.isEmpty()) {
            return false;
        }
        for (BlockDisplay d : parts) {
            if (!d.isValid()) {
                return false;
            }
        }
        return true;
    }

    public Location location() {
        return parts.isEmpty() ? null : parts.get(0).getLocation();
    }

    /** Moves the whole ball; passengers follow the root on the client. */
    public void moveTo(Location loc) {
        if (!parts.isEmpty()) {
            parts.get(0).teleport(loc, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        }
    }

    /**
     * Shows a new orientation. Skips the update when it barely changed, which keeps idle balls packet free.
     */
    public void rotate(Quaternionf rotation) {
        if (shown.equals(rotation, 0.004f)) {
            return;
        }
        shown.set(rotation);
        for (int i = 0; i < parts.size(); i++) {
            BlockDisplay d = parts.get(i);
            d.setTransformationMatrix(matrix(model.get(i), shown));
            Displays.refreshInterpolation(d, rotationInterpolation + 1);
        }
    }

    public int partCount() {
        return parts.size();
    }

    public void remove() {
        for (BlockDisplay d : parts) {
            d.remove();
        }
        parts.clear();
        model.clear();
    }
}
