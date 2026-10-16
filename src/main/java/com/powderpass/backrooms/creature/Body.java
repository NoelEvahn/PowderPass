package com.powderpass.backrooms.creature;

import com.powderpass.backrooms.display.DisplayFactory;
import com.powderpass.backrooms.display.Model;
import com.powderpass.backrooms.display.Part;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A creature's visual body: coordinated display entities sharing one origin. Moving the body teleports every
 * component with a client-side teleport interpolation; limb animation uses display transformation interpolation.
 * The yaw of the entities rotates the whole model, so part transforms stay in model space.
 */
public final class Body {
    final Model model;
    final Entity[] ents;
    private final Map<String, Matrix4f> poses = new HashMap<>();

    Body(Model model) {
        this.model = model;
        this.ents = new Entity[model.parts.size()];
    }

    void spawn(Plugin plugin, Location at, float viewRange, String tag, Player onlyViewer, int teleportDuration) {
        for (int i = 0; i < model.parts.size(); i++) {
            Part p = model.parts.get(i);
            Entity e = DisplayFactory.spawn(at.getWorld(), at, p, new Matrix4f(p.local), viewRange, "creature", tag);
            if (e instanceof Display d) d.setTeleportDuration(teleportDuration);
            if (onlyViewer != null) {
                e.setVisibleByDefault(false);
                onlyViewer.showEntity(plugin, e);
            }
            ents[i] = e;
        }
    }

    void move(Location to) {
        for (Entity e : ents) {
            if (e != null && e.isValid()) {
                e.teleport(to);
            }
        }
    }

    /**
     * Sets the pose of a bone. Part names form a skeleton: a part named {@code head.jaw} follows {@code head} and
     * then its own {@code head.jaw} pose, so every pose pivots around its rest position in model space. Only the
     * parts whose final transform actually changed are sent.
     */
    void pose(String name, Matrix4f extra, int interp) {
        Matrix4f old = poses.get(name);
        if (old != null && old.equals(extra, 2e-3f)) return; // too small to see
        poses.put(name, new Matrix4f(extra));
        String prefix = name + ".";
        for (int i = 0; i < ents.length; i++) {
            Part p = model.parts.get(i);
            if (p.name == null || !(p.name.equals(name) || p.name.startsWith(prefix)) || !(ents[i] instanceof Display d)) continue;
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(interp);
            d.setTransformationMatrix(compose(p.name, poses, p.local));
        }
    }

    /** Applies a whole pose (see {@link Poses}). */
    void pose(Map<String, Matrix4f> pose, int interp) {
        for (Map.Entry<String, Matrix4f> e : pose.entrySet()) pose(e.getKey(), e.getValue(), interp);
    }

    /** {@code pose(a) * pose(a.b) * ... * local} for a part named {@code a.b...}. */
    static Matrix4f compose(String partName, Map<String, Matrix4f> poses, Matrix4f local) {
        Matrix4f m = new Matrix4f();
        int from = 0;
        while (true) {
            int dot = partName.indexOf('.', from);
            String bone = dot < 0 ? partName : partName.substring(0, dot);
            Matrix4f e = poses.get(bone);
            if (e != null) m.mul(e);
            if (dot < 0) break;
            from = dot + 1;
        }
        return m.mul(local);
    }

    /** Arbitrary per-part transform (index-based), used for crackling arcs. */
    void raw(int index, Matrix4f m, int interp) {
        if (ents[index] instanceof Display d) {
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(interp);
            d.setTransformationMatrix(m);
        }
    }

    List<Integer> indices(String prefix) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < ents.length; i++) {
            String n = model.parts.get(i).name;
            if (n != null && n.startsWith(prefix)) out.add(i);
        }
        return out;
    }

    /** Glow for a bone and everything under it. */
    void brightness(String name, int light) {
        for (int i = 0; i < ents.length; i++) {
            String n = model.parts.get(i).name;
            if (n != null && (n.equals(name) || n.startsWith(name + ".")) && ents[i] instanceof Display d) {
                d.setBrightness(new Display.Brightness(light, 15));
            }
        }
    }

    boolean valid() {
        for (Entity e : ents) if (e == null || !e.isValid()) return false;
        return true;
    }

    void remove() {
        for (Entity e : ents) if (e != null && e.isValid()) e.remove();
    }

    int size() {
        return ents.length;
    }
}
