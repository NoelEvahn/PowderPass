package com.powderpass.backrooms.display;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Spawns display entities for model parts. Every entity is non-persistent (never written to disk, so no litter
 * survives a restart or crash) and tagged in its PersistentDataContainer for sweeps and lookups.
 */
public final class DisplayFactory {
    private DisplayFactory() {
    }

    public static Entity spawn(World world, Location anchor, Part p, Matrix4f world_, float viewRange, String tagKey, String tagValue) {
        return switch (p.kind) {
            case BLOCK -> world.spawn(anchor, BlockDisplay.class, e -> {
                common(e, p, world_, viewRange, tagKey, tagValue);
                e.setBlock(p.block);
            });
            case ITEM -> world.spawn(anchor, ItemDisplay.class, e -> {
                common(e, p, world_, viewRange, tagKey, tagValue);
                e.setItemStack(p.item);
                e.setItemDisplayTransform(p.itemTransform);
            });
            case TEXT -> world.spawn(anchor, TextDisplay.class, e -> {
                common(e, p, world_, viewRange, tagKey, tagValue);
                e.text(p.text);
                e.setLineWidth(p.lineWidth);
                e.setShadowed(p.shadow);
                e.setSeeThrough(false);
                e.setDefaultBackground(false);
                e.setBackgroundColor(Color.fromARGB(p.background));
                e.setAlignment(TextDisplay.TextAlignment.CENTER);
            });
            case INTERACTION -> {
                Vector3f pos = world_.getTranslation(new Vector3f());
                Location l = anchor.clone().add(pos.x, pos.y, pos.z);
                l.setYaw(0);
                l.setPitch(0);
                yield world.spawn(l, Interaction.class, e -> {
                    e.setPersistent(false);
                    e.setInteractionWidth(p.width);
                    e.setInteractionHeight(p.height);
                    e.setResponsive(true);
                    e.getPersistentDataContainer().set(Keys.of(tagKey), PersistentDataType.STRING, tagValue);
                    if (p.action != null) e.getPersistentDataContainer().set(Keys.ACTION, PersistentDataType.STRING, p.action);
                });
            }
        };
    }

    private static void common(Display e, Part p, Matrix4f m, float viewRange, String tagKey, String tagValue) {
        e.setPersistent(false);
        e.setTransformationMatrix(m);
        e.setInterpolationDuration(0);
        e.setTeleportDuration(0);
        e.setViewRange(viewRange);
        e.setShadowRadius(0);
        e.setShadowStrength(0);
        if (p.billboard != Display.Billboard.FIXED) e.setBillboard(p.billboard); // FIXED is the default
        if (p.brightness != null) e.setBrightness(new Display.Brightness(p.brightness, 15));
        e.getPersistentDataContainer().set(Keys.of(tagKey), PersistentDataType.STRING, tagValue);
    }
}
