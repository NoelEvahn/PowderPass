package dev.powderpass.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Fluent ItemStack construction. */
public final class ItemBuilder {

    private final ItemStack stack;
    private final ItemMeta meta;
    private final List<Component> lore = new ArrayList<>();

    private ItemBuilder(Material material, int amount) {
        this.stack = new ItemStack(material, amount);
        this.meta = stack.getItemMeta();
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material, 1);
    }

    public static ItemBuilder of(Material material, int amount) {
        return new ItemBuilder(material, Math.max(1, Math.min(material.getMaxStackSize(), amount)));
    }

    public ItemBuilder name(String miniMessage) {
        meta.displayName(Text.item(miniMessage));
        return this;
    }

    public ItemBuilder name(Component component) {
        meta.displayName(component);
        return this;
    }

    public ItemBuilder lore(String... lines) {
        lore.addAll(Text.lore(lines));
        return this;
    }

    public ItemBuilder lore(List<Component> lines) {
        lore.addAll(lines);
        return this;
    }

    public ItemBuilder glow(boolean glow) {
        meta.setEnchantmentGlintOverride(glow ? Boolean.TRUE : null);
        return this;
    }

    public ItemBuilder model(NamespacedKey key) {
        if (key != null) {
            meta.setItemModel(key);
        }
        return this;
    }

    public ItemBuilder stackSize(int max) {
        meta.setMaxStackSize(max);
        return this;
    }

    public ItemBuilder hideAll() {
        meta.addItemFlags(ItemFlag.values());
        return this;
    }

    public ItemBuilder unbreakable() {
        meta.setUnbreakable(true);
        return this;
    }

    public ItemBuilder color(Color color) {
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(color);
        }
        return this;
    }

    public <T, Z> ItemBuilder data(NamespacedKey key, PersistentDataType<T, Z> type, Z value) {
        meta.getPersistentDataContainer().set(key, type, value);
        return this;
    }

    public ItemBuilder meta(Consumer<ItemMeta> consumer) {
        consumer.accept(meta);
        return this;
    }

    public ItemStack build() {
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        stack.setItemMeta(meta);
        return stack;
    }
}
