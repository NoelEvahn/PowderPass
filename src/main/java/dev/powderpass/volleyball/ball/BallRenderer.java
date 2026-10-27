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
    /** 0..1 size factor, animated on spawn and removal. */
    private float scale = 1f;

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
                e.setTransformationMatrix(matrix(p, shown, 0.05f));
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

    private Matrix4f matrix(BallModel.Part p, Quaternionf rotation, float factor) {
        float r = radius * factor;
        return new Matrix4f()
                .rotate(rotation)
                .translate((float) p.minX() * r, (float) p.minY() * r, (float) p.minZ() * r)
                .scale((float) p.sizeX() * r, (float) p.sizeY() * r, (float) p.sizeZ() * r);
    }

    /** Animates the ball to a new size over {@code ticks} (spawn pop-in, despawn shrink). */
    public void resize(float factor, int ticks) {
        scale = factor;
        for (int i = 0; i < parts.size(); i++) {
            BlockDisplay d = parts.get(i);
            if (d.isValid()) {
                d.setTransformationMatrix(matrix(model.get(i), shown, scale));
                Displays.refreshInterpolation(d, ticks);
            }
        }
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
            d.setTransformationMatrix(matrix(model.get(i), shown, scale));
            Displays.refreshInterpolation(d, rotationInterpolation + 1);
        }
    }

    /** Movement smoothing of the whole ball (the passengers follow the root). */
    public void setTeleportDuration(int ticks) {
        if (!parts.isEmpty() && parts.get(0).getTeleportDuration() != ticks) {
            parts.get(0).setTeleportDuration(ticks);
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
