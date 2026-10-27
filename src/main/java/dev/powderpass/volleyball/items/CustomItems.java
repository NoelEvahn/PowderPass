package dev.powderpass.volleyball.items;

import dev.powderpass.volleyball.util.Keys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Factory and identification of the plugin's items. They are plain vanilla items (sunflower, iron nugget,
 * leather) tagged with a custom_model_data string: players without the resource pack see the vanilla item,
 * players with it see the custom model, and ordinary sunflowers stay sunflowers for everyone.
 */
public final class CustomItems {

    public static final String MODEL_VOLLEYBALL = "powderpass:volleyball";
    public static final String MODEL_WHISTLE = "powderpass:whistle";
    public static final String MODEL_HANDS = "powderpass:hands";

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** Built once: item creation parses MiniMessage lore, which is too slow to repeat for every kit. */
    private final Map<ItemType, ItemStack> templates = new EnumMap<>(ItemType.class);

    public ItemStack create(ItemType type) {
        return templates.computeIfAbsent(type, this::build).clone();
    }

    private ItemStack matchHands;

    /**
     * Hands for matches: the vanilla empty item model, so players play bare-handed (the arm shows in first
     * person and nothing is held in third person). Same item type as {@link ItemType#HANDS} for input.
     */
    public ItemStack matchHands() {
        if (matchHands == null) {
            ItemStack it = build(ItemType.HANDS);
            ItemMeta meta = it.getItemMeta();
            meta.setItemModel(org.bukkit.NamespacedKey.minecraft("air"));
            it.setItemMeta(meta);
            matchHands = it;
        }
        return matchHands.clone();
    }

    private ItemStack build(ItemType type) {
        return switch (type) {
            case VOLLEYBALL -> item(Material.SUNFLOWER, MODEL_VOLLEYBALL, ItemType.VOLLEYBALL,
                    "<gradient:#ffd34d:#4da6ff><b>Volleyball</b></gradient>",
                    "<gray>Right-click</gray> <white>toss for a serve</white>",
                    "<gray>Sprint + right-click</gray> <white>jump-serve toss</white>",
                    "<gray>Left-click the tossed ball</gray> <white>serve</white>",
                    "<gray>Left-click without tossing</gray> <white>underhand serve</white>",
                    "",
                    "<dark_gray>Aim with your view: look lower = shorter</dark_gray>");
            case WHISTLE -> item(Material.IRON_NUGGET, MODEL_WHISTLE, ItemType.WHISTLE,
                    "<#e0e0e0><b>Referee Whistle</b>",
                    "<gray>Right-click</gray> <white>blow the whistle / allow serve</white>",
                    "<gray>Sneak + right-click</gray> <white>referee menu</white>");
            case HANDS -> item(Material.LEATHER, MODEL_HANDS, ItemType.HANDS,
                    "<#ffb347><b>Volleyball Hands</b>",
                    "<yellow>Left-click</yellow> <white>bump  ·  in the air: spike</white>",
                    "<yellow>Right-click</yellow> <white>set  ·  in the air at the net: block</white>",
                    "<yellow>Sneak + left</yellow> <white>dive/dig  ·  in the air: tip</white>",
                    "<yellow>Sneak + right</yellow> <white>quick (low) set</white>",
                    "<yellow>Serving</yellow> <white>right-click toss · left-click underhand</white>",
                    "",
                    "<dark_gray>Click when the ball reaches your hands.</dark_gray>",
                    "<dark_gray>Look where you want the ball to go.</dark_gray>");
        };
    }

    private static ItemStack item(Material material, String model, ItemType type, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(name).decoration(TextDecoration.ITALIC, false));
        List<Component> lines = new ArrayList<>();
        for (String s : lore) {
            lines.add(MM.deserialize(s).decoration(TextDecoration.ITALIC, false).colorIfAbsent(NamedTextColor.GRAY));
        }
        meta.lore(lines);
        meta.setMaxStackSize(1);
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setStrings(List.of(model));
        meta.setCustomModelDataComponent(cmd);
        meta.getPersistentDataContainer().set(Keys.ITEM, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    private final Map<Integer, ItemStack[]> jerseys = new java.util.HashMap<>();

    /**
     * Team kit: dyed leather boots, leggings and chestplate (armor slot order). Cached per colour.
     */
    public ItemStack[] jersey(net.kyori.adventure.text.format.TextColor color) {
        ItemStack[] cached = jerseys.computeIfAbsent(color.value(), v -> new ItemStack[]{
                dyed(Material.LEATHER_BOOTS, v), dyed(Material.LEATHER_LEGGINGS, v), dyed(Material.LEATHER_CHESTPLATE, v), null});
        ItemStack[] out = new ItemStack[4];
        for (int i = 0; i < 3; i++) {
            out[i] = cached[i].clone();
        }
        return out;
    }

    private static ItemStack dyed(Material m, int rgb) {
        ItemStack it = new ItemStack(m);
        org.bukkit.inventory.meta.LeatherArmorMeta meta = (org.bukkit.inventory.meta.LeatherArmorMeta) it.getItemMeta();
        meta.setColor(org.bukkit.Color.fromRGB(rgb));
        meta.setUnbreakable(true);
        meta.displayName(Component.text("Team jersey", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
        meta.setEnchantmentGlintOverride(false);
        it.setItemMeta(meta);
        return it;
    }

    /** Identifies a custom item. Called every tick, so it avoids copying item meta. */
    public static ItemType typeOf(ItemStack item) {
        if (item == null) {
            return null;
        }
        Material m = item.getType();
        if (m != Material.SUNFLOWER && m != Material.IRON_NUGGET && m != Material.LEATHER) {
            return null;
        }
        String s = item.getPersistentDataContainer().get(Keys.ITEM, PersistentDataType.STRING);
        return s == null ? null : ItemType.byName(s);
    }

    public static boolean is(ItemStack item, ItemType type) {
        return typeOf(item) == type;
    }
}
