package dev.powderpass.gui;

import dev.powderpass.PowderPassPlugin;
import dev.powderpass.profile.Badge;
import dev.powderpass.profile.PlayerData;
import dev.powderpass.profile.SuitPalette;
import dev.powderpass.util.ItemBuilder;
import dev.powderpass.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** All badges, earned ones lit up. */
public final class BadgesMenu extends Menu {

    public BadgesMenu(PowderPassPlugin plugin, Player player) {
        super(4, Text.mm("<#facc15><bold>★ Badges"));
        PlayerData d = plugin.profiles().get(player);
        Badge[] badges = Badge.values();
        for (int i = 0; i < badges.length && i < 27; i++) {
            Badge b = badges[i];
            boolean earned = d.badges.contains(b);
            String unlock = "";
            for (SuitPalette suit : SuitPalette.values()) {
                if (suit.unlock() == b) {
                    unlock = "<#f472b6>Unlocks suit: " + suit.display();
                }
            }
            ItemBuilder item = ItemBuilder.of(earned ? b.icon() : Material.GRAY_DYE)
                    .name((earned ? "<#facc15><bold>" : "<gray>") + b.display())
                    .lore("<gray>" + b.description(), "", earned ? "<#4ade80>✔ Earned" : "<dark_gray>✖ Locked")
                    .glow(earned).hideAll();
            if (!unlock.isEmpty()) {
                item.lore(unlock);
            }
            set(i, item.build());
        }
        set(31, ItemBuilder.of(Material.ARROW).name("<gray>Back").build(), e -> new MainMenu(plugin, player).open(player));
        fill(Material.BLACK_STAINED_GLASS_PANE);
    }
}
