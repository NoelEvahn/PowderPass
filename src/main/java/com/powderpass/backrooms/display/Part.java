package com.powderpass.backrooms.display;

import net.kyori.adventure.text.Component;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.joml.Matrix4f;

/** One display entity (or interaction hitbox) of a model, in model space (+Z = front, origin = anchor). */
public final class Part {
    public enum Kind { BLOCK, ITEM, TEXT, INTERACTION }

    public final Kind kind;
    public final Matrix4f local;
    public BlockData block;
    /** Alternate block for blinking/flickering parts. */
    public BlockData alt;
    public ItemStack item;
    public ItemDisplay.ItemDisplayTransform itemTransform = ItemDisplay.ItemDisplayTransform.FIXED;
    public Component text;
    public int background = 0x00000000;
    public int lineWidth = 200;
    public boolean shadow;
    public Integer brightness;
    public int group;
    public Display.Billboard billboard = Display.Billboard.FIXED;
    public String action;
    public float width;
    public float height;
    /** Name used by creature bodies to address parts. */
    public String name;
    Model owner;
    /** Glowing part of a light fixture: dimmed by flicker / blackout events. */
    public boolean lamp;

    Part(Kind kind, Matrix4f local) {
        this.kind = kind;
        this.local = local;
    }

    public Part glow(int light) {
        this.brightness = light;
        return this;
    }

    /** Mark as the glowing element of a lamp (full brightness, dimmed by events). */
    public Part lamp() {
        this.lamp = true;
        this.brightness = 15;
        return this;
    }

    public Part group(int g) {
        this.group = g;
        return this;
    }

    public Part alt(BlockData b) {
        this.alt = b;
        return this;
    }

    public Part named(String n) {
        this.name = n;
        return this;
    }

    /** Pops the owning model's transform stack (allows {@code m.push()...box(...).pop()} chains). */
    public Model pop() {
        return owner.pop();
    }

    public Part billboard(Display.Billboard b) {
        this.billboard = b;
        return this;
    }
}
