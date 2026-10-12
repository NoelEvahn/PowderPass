package com.powderpass.backrooms.display;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** PersistentDataContainer keys shared by the plugin. */
public final class Keys {
    public static NamespacedKey DECOR;
    public static NamespacedKey CREATURE;
    public static NamespacedKey ACTION;
    public static NamespacedKey ITEM;
    public static NamespacedKey BATTERY;
    public static NamespacedKey LOOTED;
    public static NamespacedKey PLACED;
    public static NamespacedKey RETURN;
    public static NamespacedKey INSIDE;
    public static NamespacedKey SANITY;

    private Keys() {
    }

    public static NamespacedKey of(String name) {
        return switch (name) {
            case "creature" -> CREATURE;
            default -> DECOR;
        };
    }

    public static void init(Plugin p) {
        DECOR = new NamespacedKey(p, "decor");
        CREATURE = new NamespacedKey(p, "creature");
        ACTION = new NamespacedKey(p, "action");
        ITEM = new NamespacedKey(p, "item");
        BATTERY = new NamespacedKey(p, "battery");
        LOOTED = new NamespacedKey(p, "looted");
        PLACED = new NamespacedKey(p, "placed");
        RETURN = new NamespacedKey(p, "return");
        INSIDE = new NamespacedKey(p, "inside");
        SANITY = new NamespacedKey(p, "sanity");
    }
}
