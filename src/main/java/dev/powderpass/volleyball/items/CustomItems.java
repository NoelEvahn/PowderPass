package dev.powderpass.volleyball.items;

import com.destroystokyo.paper.profile.PlayerProfile;
import dev.powderpass.volleyball.config.Settings;
import dev.powderpass.volleyball.util.Keys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerTextures;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/** Factory and identification of the plugin's custom items. */
public final class CustomItems {

    private static final UUID BALL_PROFILE = UUID.fromString("7c1f0b52-3f4e-4c8e-9b1a-56d0a1b2c3d4");
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Settings settings;
    private final Logger log;
    private PlayerProfile ballProfile;
    /** Built once: item creation parses MiniMessage lore, which is too slow to repeat for every kit. */
    private final java.util.Map<ItemType, ItemStack> templates = new java.util.EnumMap<>(ItemType.class);
    private ItemStack ballModelTemplate;

    public CustomItems(Settings settings, Logger log) {
        this.settings = settings;
        this.log = log;
        buildProfile();
    }

    private void buildProfile() {
        String tex = settings.ball.texture();
        if (tex == null || tex.isBlank()) {
            return;
        }
        try {
            PlayerProfile profile = Bukkit.createProfile(BALL_PROFILE, "Volleyball");
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(URI.create(tex).toURL());
            profile.setTextures(textures);
            ballProfile = profile;
        } catch (Exception e) {
            log.warning("Invalid ball texture URL '" + tex + "': " + e.getMessage());
        }
    }

    /** The item rendered by the ball display entity. */
    public ItemStack ballModel() {
        if (ballModelTemplate == null) {
            ballModelTemplate = buildBallModel();
        }
        return ballModelTemplate.clone();
    }

    private ItemStack buildBallModel() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (ballProfile != null) {
            meta.setPlayerProfile(ballProfile);
        }
        if (settings.resourcePack.enabled()) {
            meta.setItemModel(settings.resourcePack.ballModel());
        }
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack create(ItemType type) {
        return templates.computeIfAbsent(type, this::build).clone();
    }

    private ItemStack build(ItemType type) {
        return switch (type) {
            case VOLLEYBALL -> volleyball();
            case WHISTLE -> whistle();
            case HANDS -> hands();
        };
    }

    private ItemStack volleyball() {
        ItemStack item = buildBallModel();
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name("<gradient:#ffd34d:#4da6ff><b>Volleyball</b></gradient>"));
        meta.lore(lore(
                "<gray>Right-click</gray> <white>toss for a serve</white>",
                "<gray>Sprint + right-click</gray> <white>jump-serve toss</white>",
                "<gray>Left-click the tossed ball</gray> <white>serve</white>",
                "<gray>Left-click without tossing</gray> <white>underhand serve</white>",
                "",
                "<dark_gray>Aim with your view: look lower = shorter</dark_gray>"));
        meta.setMaxStackSize(1);
        tag(meta, ItemType.VOLLEYBALL);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack whistle() {
        ItemStack item = new ItemStack(Material.IRON_NUGGET);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name("<#e0e0e0><b>Referee Whistle</b>"));
        meta.lore(lore(
                "<gray>Right-click</gray> <white>blow the whistle / allow serve</white>",
                "<gray>Sneak + right-click</gray> <white>referee menu</white>"));
        meta.setMaxStackSize(1);
        if (settings.resourcePack.enabled()) {
            meta.setItemModel(settings.resourcePack.whistleModel());
        }
        tag(meta, ItemType.WHISTLE);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack hands() {
        ItemStack item = new ItemStack(Material.LEATHER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name("<#ffb347><b>Volleyball Hands</b>"));
        meta.lore(lore(
                "<yellow>Left-click</yellow> <white>bump  ·  in the air: spike</white>",
                "<yellow>Right-click</yellow> <white>set  ·  in the air at the net: block</white>",
                "<yellow>Sneak + left</yellow> <white>dive/dig  ·  in the air: tip</white>",
                "<yellow>Sneak + right</yellow> <white>quick (low) set</white>",
                "",
                "<dark_gray>Click when the ball reaches your hands.</dark_gray>",
                "<dark_gray>Look where you want the ball to go.</dark_gray>"));
        meta.setMaxStackSize(1);
        if (settings.resourcePack.enabled()) {
            meta.setItemModel(settings.resourcePack.handsModel());
        }
        tag(meta, ItemType.HANDS);
        item.setItemMeta(meta);
        return item;
    }

    private static void tag(ItemMeta meta, ItemType type) {
        meta.getPersistentDataContainer().set(Keys.ITEM, PersistentDataType.STRING, type.name());
    }

    /** Identifies a custom item. Called every tick, so it avoids copying item meta. */
    public static ItemType typeOf(ItemStack item) {
        if (item == null) {
            return null;
        }
        Material m = item.getType();
        if (m != Material.PLAYER_HEAD && m != Material.IRON_NUGGET && m != Material.LEATHER) {
            return null;
        }
        String s = item.getPersistentDataContainer().get(Keys.ITEM, PersistentDataType.STRING);
        return s == null ? null : ItemType.byName(s);
    }

    public static boolean is(ItemStack item, ItemType type) {
        return typeOf(item) == type;
    }

    private static Component name(String mm) {
        return MM.deserialize(mm).decoration(TextDecoration.ITALIC, false);
    }

    private static List<Component> lore(String... lines) {
        List<Component> l = new ArrayList<>();
        for (String s : lines) {
            l.add(MM.deserialize(s).decoration(TextDecoration.ITALIC, false).colorIfAbsent(NamedTextColor.GRAY));
        }
        return l;
    }
}
