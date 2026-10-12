package dev.powderpass.gui;

import dev.powderpass.PowderPassPlugin;
import dev.powderpass.config.Messages;
import dev.powderpass.config.PluginConfig;
import dev.powderpass.pass.PassService;
import dev.powderpass.pass.PassType;
import dev.powderpass.util.ItemBuilder;
import dev.powderpass.util.Text;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.Locale;

/** The ticket window opened from a kiosk. */
public final class TicketMenu extends Menu {

    public TicketMenu(PowderPassPlugin plugin, Player player) {
        super(3, Text.mm("<#fbbf24><bold>🎟 Ticket Window"));
        offer(plugin, player, PassType.DAY, 11, Material.PAPER);
        offer(plugin, player, PassType.SEASON, 15, Material.MAP);
        PassService.PassInfo current = plugin.passes().validPass(player);
        set(13, ItemBuilder.of(current == null ? Material.GRAY_DYE : Material.LIME_DYE)
                .name(current == null ? "<gray>You have no valid pass" : "<#4ade80>Current: " + current.type().display())
                .lore(current == null ? new String[]{"<dark_gray>Buy one to ride the lifts."}
                        : new String[]{"<gray>Valid until <white>" + PassService.formatExpiry(current.expiry())})
                .build());
        fill(Material.ORANGE_STAINED_GLASS_PANE);
    }

    private void offer(PowderPassPlugin plugin, Player player, PassType type, int slot, Material icon) {
        PluginConfig.PassType cost = plugin.passes().settings(type);
        String price = cost.costAmount() <= 0 ? "Free" : cost.costAmount() + " × " + pretty(cost.costMaterial());
        set(slot, ItemBuilder.of(icon).name("<white><bold>" + type.display())
                .lore("<gray>Valid for <white>" + Text.duration(cost.durationMillis()),
                        "<gray>Price: <#fbbf24>" + price,
                        "",
                        "<#bae6fd>Click to buy")
                .glow(type == PassType.SEASON).hideAll().build(), e -> {
            PassService.Purchase result = plugin.passes().purchase(player, type);
            if (result == PassService.Purchase.OK) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, SoundCategory.PLAYERS, 1f, 1.1f);
                player.playSound(player.getLocation(), Sound.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundCategory.PLAYERS, 1f, 1f);
                plugin.messages().send(player, "pass.bought", Messages.p("pass", type.display()));
                player.closeInventory();
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 1f, 1f);
                plugin.messages().send(player, "pass.cannot-afford", Messages.p("price", price));
            }
        });
    }

    private static String pretty(Material m) {
        String[] words = m.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }
}
