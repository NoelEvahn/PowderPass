package dev.powderpass.volleyball.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Persistent data keys. */
public final class Keys {

    public static NamespacedKey ENTITY;
    public static NamespacedKey ITEM;

    private Keys() {
    }

    public static void init(Plugin plugin) {
        ENTITY = new NamespacedKey(plugin, "entity");
        ITEM = new NamespacedKey(plugin, "item");
    }
}
