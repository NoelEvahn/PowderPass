package dev.powderpass.gui;

import dev.powderpass.PowderPassPlugin;
import dev.powderpass.physics.SteeringMode;
import dev.powderpass.profile.PlayerData;
import dev.powderpass.profile.SuitPalette;
import dev.powderpass.util.ItemBuilder;
import dev.powderpass.util.Text;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

/** Suit colours and riding preferences. */
public final class LockerMenu extends Menu {

    public LockerMenu(PowderPassPlugin plugin, Player player) {
        super(4, Text.mm("<#f472b6><bold>👕 Locker"));
        PlayerData d = plugin.profiles().get(player);
        SuitPalette[] suits = SuitPalette.values();
        for (int i = 0; i < suits.length; i++) {
            SuitPalette suit = suits[i];
            boolean unlocked = d.unlocked(suit);
            boolean worn = d.suit == suit;
            ItemBuilder item = ItemBuilder.of(unlocked ? Material.LEATHER_CHESTPLATE : Material.CHAINMAIL_CHESTPLATE)
                    .color(suit.icon())
                    .name((unlocked ? "<white><bold>" : "<gray>") + suit.display())
                    .glow(worn).hideAll();
            if (worn) {
                item.lore("<#4ade80>✔ Wearing");
            } else if (unlocked) {
                item.lore("<#bae6fd>Click to wear");
            } else {
                item.lore("<dark_gray>Locked", "<gray>Earn the <white>" + suit.unlock().display() + " <gray>badge");
            }
            int slot = i < 5 ? 2 + i : 11 + (i - 5);
            set(slot, item.build(), e -> {
                if (d.unlocked(suit)) {
                    d.suit = suit;
                    d.markDirty();
                    player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, SoundCategory.PLAYERS, 1f, 1f);
                    plugin.messages().send(player, "locker.suit", dev.powderpass.config.Messages.p("suit", suit.display()));
                    new LockerMenu(plugin, player).open(player);
                } else {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 1f, 0.6f);
                }
            });
        }
        SteeringMode steering = d.steering != null ? d.steering : plugin.config().defaultSteering;
        set(21, ItemBuilder.of(steering == SteeringMode.LOOK ? Material.SPYGLASS : Material.OAK_BOAT)
                .name("<#7dd3fc><bold>Steering: " + steering)
                .lore(steering == SteeringMode.LOOK
                                ? new String[]{"<gray>Your skis follow your camera.", "<gray>A/D carve harder."}
                                : new String[]{"<gray>A/D turn your skis like a boat.", "<gray>Look around freely."})
                .lore("", "<#bae6fd>Click to switch", "<dark_gray>Applies next time you click in.")
                .build(), e -> {
            d.steering = steering == SteeringMode.LOOK ? SteeringMode.KEYS : SteeringMode.LOOK;
            d.markDirty();
            new LockerMenu(plugin, player).open(player);
        });
        set(23, ItemBuilder.of(Material.CLOCK).name("<#7dd3fc><bold>Speed units: " + (d.mph ? "mph" : "km/h"))
                .lore("<#bae6fd>Click to switch").build(), e -> {
            d.mph = !d.mph;
            d.markDirty();
            new LockerMenu(plugin, player).open(player);
        });
        set(31, ItemBuilder.of(Material.ARROW).name("<gray>Back").build(), e -> new MainMenu(plugin, player).open(player));
        fill(Material.PINK_STAINED_GLASS_PANE);
    }
}
