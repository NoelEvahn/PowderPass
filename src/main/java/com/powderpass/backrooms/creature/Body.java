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
import java.util.List;

/**
 * A creature's visual body: coordinated display entities sharing one origin. Moving the body teleports every
 * component with a client-side teleport interpolation; limb animation uses display transformation interpolation.
 * The yaw of the entities rotates the whole model, so part transforms stay in model space.
 */
public final class Body {
    final Model model;
    final Entity[] ents;

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

    /** Applies {@code extra * local} to every part with the given name. */
    void pose(String name, Matrix4f extra, int interp) {
        for (int i = 0; i < ents.length; i++) {
            Part p = model.parts.get(i);
            if (!name.equals(p.name) || !(ents[i] instanceof Display d)) continue;
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(interp);
            d.setTransformationMatrix(new Matrix4f(extra).mul(p.local));
        }
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

    void brightness(String name, int light) {
        for (int i = 0; i < ents.length; i++) {
            if (name.equals(model.parts.get(i).name) && ents[i] instanceof Display d) {
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
