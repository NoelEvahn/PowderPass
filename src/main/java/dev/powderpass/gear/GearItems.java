package dev.powderpass.gear;

import dev.powderpass.config.PluginConfig;
import dev.powderpass.util.ItemBuilder;
import dev.powderpass.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Creates, identifies and registers recipes for skis and snowboards. */
public final class GearItems {

    private final Plugin plugin;
    private final List<NamespacedKey> recipeKeys = new ArrayList<>();
    private PluginConfig config;

    public GearItems(Plugin plugin, PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void reload(PluginConfig config) {
        this.config = config;
    }

    public ItemStack create(Gear gear) {
        PluginConfig.Tier stats = config.tiers.get(gear.tier());
        int speedPct = (int) Math.round(stats.speed() * 100);
        int gripPct = (int) Math.round(stats.grip() * 100);
        String model = gear.type() == GearType.SKIS ? gear.tier().skiModel() : "bamboo_raft";
        String tag = gear.tier().colorTag();
        return ItemBuilder.of(Material.CARROT_ON_A_STICK)
                .name(tag + "<bold>" + gear.displayName())
                .lore("<gray>" + (gear.type() == GearType.SKIS ? "A pair of carving skis." : "A freestyle snowboard."),
                        "",
                        "<dark_gray>▸ <gray>Speed  " + bar(stats.speed(), 0.85, 1.2) + " <white>" + speedPct + "%",
                        "<dark_gray>▸ <gray>Grip   " + bar(stats.grip(), 0.85, 1.25) + " <white>" + gripPct + "%",
                        "",
                        "<#bae6fd>Right-click <gray>on snow to click in",
                        "<#bae6fd>F <gray>to step out")
                .model(NamespacedKey.minecraft(model))
                .glow(gear.tier() == GearTier.AVALANCHE)
                .unbreakable()
                .stackSize(1)
                .hideAll()
                .data(Keys.GEAR, PersistentDataType.STRING, gear.id())
                .build();
    }

    /** The gear an item represents, or null. */
    public static Gear identify(ItemStack stack) {
        if (stack == null || stack.getType() != Material.CARROT_ON_A_STICK || !stack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        return Gear.parse(meta.getPersistentDataContainer().get(Keys.GEAR, PersistentDataType.STRING));
    }

    private static String bar(double value, double min, double max) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, (value - min) / (max - min))) * 8);
        return "<#7dd3fc>" + "▮".repeat(filled) + "<dark_gray>" + "▮".repeat(8 - filled);
    }

    public void registerRecipes() {
        unregisterRecipes();
        for (GearType type : GearType.values()) {
            for (GearTier tier : GearTier.values()) {
                Recipe recipe = recipe(new Gear(type, tier));
                if (recipe != null && Bukkit.addRecipe(recipe)) {
                    recipeKeys.add(((org.bukkit.Keyed) recipe).getKey());
                }
            }
        }
        if (!Bukkit.getOnlinePlayers().isEmpty()) {
            Bukkit.updateRecipes();
        }
    }

    public void unregisterRecipes() {
        for (NamespacedKey key : recipeKeys) {
            Bukkit.removeRecipe(key);
        }
        recipeKeys.clear();
    }

    public List<NamespacedKey> recipeKeys() {
        return List.copyOf(recipeKeys);
    }

    /**
     * The gear a PowderPass upgrade recipe expects as its input, or null for recipes without a gear input.
     */
    public Gear requiredInput(NamespacedKey key) {
        if (!isOurRecipe(key)) {
            return null;
        }
        int sep = key.getKey().indexOf('_');
        if (sep < 0) {
            return null;
        }
        GearType type = GearType.byId(key.getKey().substring(0, sep));
        GearTier tier = GearTier.byId(key.getKey().substring(sep + 1));
        if (type == null || tier == null || tier.previous() == null) {
            return null;
        }
        return new Gear(type, tier.previous());
    }

    public boolean isOurRecipe(NamespacedKey key) {
        return key != null && key.getNamespace().equals(plugin.getName().toLowerCase(Locale.ROOT));
    }

    private Recipe recipe(Gear gear) {
        NamespacedKey key = new NamespacedKey(plugin, gear.type().id() + "_" + gear.tier().id());
        ItemStack result = create(gear);
        String[] shape = gear.type() == GearType.SKIS
                ? new String[]{"M M", "MCM", "M M"}
                : new String[]{" M ", "MCM", " M "};
        switch (gear.tier()) {
            case TIMBER -> {
                ShapedRecipe r = new ShapedRecipe(key, result);
                r.shape(shape);
                r.setIngredient('M', new RecipeChoice.MaterialChoice(Tag.PLANKS));
                r.setIngredient('C', Material.STRING);
                return r;
            }
            case ALPINE, GLACIER -> {
                ShapedRecipe r = new ShapedRecipe(key, result);
                r.shape(shape);
                r.setIngredient('M', gear.tier() == GearTier.ALPINE ? Material.IRON_INGOT : Material.DIAMOND);
                // Any carrot-on-a-stick matches here; GeneralListener checks it really is the previous tier
                // (by its PDC tag, so renamed gear or changed lore still upgrades).
                r.setIngredient('C', Material.CARROT_ON_A_STICK);
                return r;
            }
            case AVALANCHE -> {
                return new SmithingTransformRecipe(key, result,
                        new RecipeChoice.MaterialChoice(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                        new RecipeChoice.MaterialChoice(Material.CARROT_ON_A_STICK),
                        new RecipeChoice.MaterialChoice(Material.NETHERITE_INGOT));
            }
            default -> {
                return null;
            }
        }
    }
}
