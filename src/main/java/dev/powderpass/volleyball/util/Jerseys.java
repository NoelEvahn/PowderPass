package dev.powderpass.volleyball.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

/** Team kit: dyed leather boots, leggings and chestplate. */
public final class Jerseys {

    private Jerseys() {
    }

    /** Armor contents (boots, leggings, chestplate, helmet) in the team colour. */
    public static ItemStack[] of(int rgb) {
        return new ItemStack[]{dyed(Material.LEATHER_BOOTS, rgb), dyed(Material.LEATHER_LEGGINGS, rgb),
                dyed(Material.LEATHER_CHESTPLATE, rgb), null};
    }

    private static ItemStack dyed(Material m, int rgb) {
        ItemStack it = new ItemStack(m);
        LeatherArmorMeta meta = (LeatherArmorMeta) it.getItemMeta();
        meta.setColor(Color.fromRGB(rgb));
        meta.setUnbreakable(true);
        meta.displayName(Component.text("Team jersey", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }
}
