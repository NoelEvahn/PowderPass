package dev.powderpass.display;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.joml.Matrix4f;

import java.util.function.Consumer;

/** Spawn helpers for display entities. Entities are configured before they are added to the world. */
public final class Displays {

    private Displays() {
    }

    public static BlockDisplay block(Location at, BlockData data, Matrix4f transform, float viewRange,
                                     Consumer<BlockDisplay> extra) {
        return at.getWorld().spawn(at, BlockDisplay.class, d -> {
            common(d, viewRange);
            d.setBlock(data);
            d.setTransformationMatrix(transform);
            if (extra != null) {
                extra.accept(d);
            }
        });
    }

    public static ItemDisplay item(Location at, ItemStack stack, Matrix4f transform, float viewRange,
                                   Consumer<ItemDisplay> extra) {
        return at.getWorld().spawn(at, ItemDisplay.class, d -> {
            common(d, viewRange);
            d.setItemStack(stack);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformationMatrix(transform);
            if (extra != null) {
                extra.accept(d);
            }
        });
    }

    /** An invisible display used purely as a vehicle / anchor for other entities. */
    public static ItemDisplay anchor(Location at, int teleportDuration, Consumer<ItemDisplay> extra) {
        return at.getWorld().spawn(at, ItemDisplay.class, d -> {
            common(d, 1.0f);
            d.setItemStack(null);
            d.setTeleportDuration(teleportDuration);
            if (extra != null) {
                extra.accept(d);
            }
        });
    }

    public static TextDisplay text(Location at, Component text, Matrix4f transform, float viewRange,
                                   Consumer<TextDisplay> extra) {
        return at.getWorld().spawn(at, TextDisplay.class, d -> {
            common(d, viewRange);
            d.text(text);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setBackgroundColor(Color.fromARGB(110, 10, 20, 35));
            d.setTransformationMatrix(transform);
            if (extra != null) {
                extra.accept(d);
            }
        });
    }

    public static Interaction interaction(Location at, float width, float height, Consumer<Interaction> extra) {
        return at.getWorld().spawn(at, Interaction.class, i -> {
            Managed.mark(i);
            i.setInteractionWidth(width);
            i.setInteractionHeight(height);
            i.setResponsive(true);
            if (extra != null) {
                extra.accept(i);
            }
        });
    }

    public static ItemStack blockItem(Material material) {
        return new ItemStack(material.isItem() ? material : Material.STONE);
    }

    private static void common(Display d, float viewRange) {
        Managed.mark(d);
        d.setViewRange(viewRange);
        d.setShadowRadius(0f);
        d.setShadowStrength(0f);
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(0);
    }
}
