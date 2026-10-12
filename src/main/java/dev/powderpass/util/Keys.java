package dev.powderpass.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** All persistent-data and recipe keys used by the plugin. */
public final class Keys {

    public static NamespacedKey MANAGED;
    public static NamespacedKey GEAR;
    public static NamespacedKey PASS_OWNER;
    public static NamespacedKey PASS_EXPIRY;
    public static NamespacedKey PASS_TYPE;
    public static NamespacedKey PASS_SERIAL;
    public static NamespacedKey KIOSK;
    public static NamespacedKey MENU_ITEM;

    private Keys() {
    }

    public static void init(Plugin plugin) {
        MANAGED = new NamespacedKey(plugin, "managed");
        GEAR = new NamespacedKey(plugin, "gear");
        PASS_OWNER = new NamespacedKey(plugin, "pass_owner");
        PASS_EXPIRY = new NamespacedKey(plugin, "pass_expiry");
        PASS_TYPE = new NamespacedKey(plugin, "pass_type");
        PASS_SERIAL = new NamespacedKey(plugin, "pass_serial");
        KIOSK = new NamespacedKey(plugin, "kiosk");
        MENU_ITEM = new NamespacedKey(plugin, "menu_item");
    }
}
