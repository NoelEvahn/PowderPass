package com.powderpass.backrooms.display;

import com.powderpass.backrooms.config.BackroomsConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A display-entity model: parts in model space plus animations, emitters and an optional hazard.
 * Built with a small transform stack so composite props (chairs, desks...) can be reused at any pose.
 */
public final class Model {
    public final List<Part> parts = new ArrayList<>();
    public final List<Anim> anims = new ArrayList<>();
    public final List<Emitter> emitters = new ArrayList<>();
    public Hazard hazard;
    /** Scripted behaviour id handled by the decoration manager ("elevator", "dancers", "monitor", ...). */
    public String behavior;
    /** View range multiplier override (0 = use config); colossal props need to be seen from far away. */
    public float viewRange;
    /** Hide the whole model while a player is closer than this (0 = never). */
    public double hideRadius;
    private int group;
    private final Deque<Matrix4f> stack = new ArrayDeque<>();
    private Matrix4f cur = new Matrix4f();

    // ------------------------------------------------------------------ transform stack

    public Model push() {
        stack.push(new Matrix4f(cur));
        return this;
    }

    public Model pop() {
        cur = stack.pop();
        return this;
    }

    public Model translate(double x, double y, double z) {
        cur.translate((float) x, (float) y, (float) z);
        return this;
    }

    /** Rotate (degrees) around an axis through the current origin. */
    public Model rotate(char axis, double deg) {
        float r = (float) Math.toRadians(deg);
        switch (axis) {
            case 'x' -> cur.rotateX(r);
            case 'y' -> cur.rotateY(r);
            default -> cur.rotateZ(r);
        }
        return this;
    }

    public Model scale(double s) {
        cur.scale((float) s);
        return this;
    }

    public Model group(int g) {
        this.group = g;
        return this;
    }

    // ------------------------------------------------------------------ parts

    private Part add(Part p) {
        p.group = group;
        p.owner = this;
        parts.add(p);
        return p;
    }

    public Part box(Material m, double x, double y, double z, double sx, double sy, double sz) {
        return box(m.createBlockData(), x, y, z, sx, sy, sz);
    }

    public Part box(String blockState, double x, double y, double z, double sx, double sy, double sz) {
        return box(Bukkit.createBlockData(blockState), x, y, z, sx, sy, sz);
    }

    public Part box(BlockData d, double x, double y, double z, double sx, double sy, double sz) {
        Matrix4f l = new Matrix4f(cur).translate((float) x, (float) y, (float) z).scale((float) sx, (float) sy, (float) sz);
        Part p = new Part(Part.Kind.BLOCK, l);
        p.block = d;
        return add(p);
    }

    /** Box centred on (cx,cy,cz), rotated around its own centre. */
    public Part boxC(Material m, double cx, double cy, double cz, double sx, double sy, double sz, char axis, double deg) {
        Matrix4f l = new Matrix4f(cur).translate((float) cx, (float) cy, (float) cz);
        float r = (float) Math.toRadians(deg);
        switch (axis) {
            case 'x' -> l.rotateX(r);
            case 'y' -> l.rotateY(r);
            default -> l.rotateZ(r);
        }
        l.translate((float) (-sx / 2), (float) (-sy / 2), (float) (-sz / 2)).scale((float) sx, (float) sy, (float) sz);
        Part p = new Part(Part.Kind.BLOCK, l);
        p.block = m.createBlockData();
        return add(p);
    }

    /** A unit block transformed by {@code m} (after the current stack transform): shears, rhombi, anything. */
    public Part boxM(Material mat, Matrix4f m) {
        Part p = new Part(Part.Kind.BLOCK, new Matrix4f(cur).mul(m));
        p.block = mat.createBlockData();
        return add(p);
    }

    /** Thin rod between two points (cables, pipes, limbs). */
    public Part rod(Material m, Vector3f a, Vector3f b, double thickness) {
        Vector3f d = new Vector3f(b).sub(a);
        float len = d.length();
        Matrix4f l = new Matrix4f(cur).translate(a);
        if (len > 1e-4) {
            Vector3f dir = new Vector3f(d).normalize();
            l.rotate(new org.joml.Quaternionf().rotationTo(new Vector3f(0, 1, 0), dir));
        }
        l.translate((float) (-thickness / 2), 0, (float) (-thickness / 2)).scale((float) thickness, len, (float) thickness);
        Part p = new Part(Part.Kind.BLOCK, l);
        p.block = m.createBlockData();
        return add(p);
    }

    public Part item(Material m, double cx, double cy, double cz, double scale, double yaw, double pitch) {
        Matrix4f l = new Matrix4f(cur).translate((float) cx, (float) cy, (float) cz)
                .rotateY((float) Math.toRadians(yaw)).rotateX((float) Math.toRadians(pitch)).scale((float) scale);
        Part p = new Part(Part.Kind.ITEM, l);
        p.item = new ItemStack(m);
        return add(p);
    }

    /** Text plane centred horizontally at (x,y,z), facing +Z. */
    public Part text(String mini, double x, double y, double z, double scale, int argbBackground) {
        Matrix4f l = new Matrix4f(cur).translate((float) x, (float) y, (float) z).scale((float) scale);
        Part p = new Part(Part.Kind.TEXT, l);
        p.text = BackroomsConfig.mm(mini);
        p.background = argbBackground;
        return add(p);
    }

    /** Interaction hitbox standing at (x,y,z) in model space. */
    public Part hitbox(String action, double x, double y, double z, double w, double h) {
        Matrix4f l = new Matrix4f(cur).translate((float) x, (float) y, (float) z);
        Part p = new Part(Part.Kind.INTERACTION, l);
        p.action = action;
        p.width = (float) w;
        p.height = (float) h;
        return add(p);
    }

    // ------------------------------------------------------------------ behaviour

    public Model anim(Anim.Type type, int group, Vector3f axis, Vector3f pivot, double amp, double period) {
        anims.add(new Anim(group, type, axis, pivot, (float) amp, (float) Math.max(1, period), 0f));
        return this;
    }

    public Model anim(Anim.Type type, int group, Vector3f axis, Vector3f pivot, double amp, double period, double phase) {
        anims.add(new Anim(group, type, axis, pivot, (float) amp, (float) Math.max(1, period), (float) phase));
        return this;
    }

    public Model emit(Particle particle, Object data, double ox, double oy, double oz, int count, double spread, double speed,
                      int interval, double chance) {
        emitters.add(new Emitter(particle, data, (float) ox, (float) oy, (float) oz, count, (float) spread, (float) speed, interval, (float) chance, null, 0, 0));
        return this;
    }

    public Model sound(Sound sound, double ox, double oy, double oz, int interval, double chance, double volume, double pitch) {
        emitters.add(new Emitter(null, null, (float) ox, (float) oy, (float) oz, 0, 0, 0, interval, (float) chance, sound, (float) volume, (float) pitch));
        return this;
    }

    public Model hazard(String type, double radius, double damage, int period, int telegraph, double ox, double oy, double oz) {
        this.hazard = new Hazard(type, (float) radius, damage, period, telegraph, (float) ox, (float) oy, (float) oz);
        return this;
    }

    public Model behavior(String b) {
        this.behavior = b;
        return this;
    }

    public int entityCount() {
        return parts.size();
    }

    public static Vector3f v(double x, double y, double z) {
        return new Vector3f((float) x, (float) y, (float) z);
    }
}
