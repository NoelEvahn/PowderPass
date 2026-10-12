package dev.powderpass.gui;

import dev.powderpass.PowderPassPlugin;
import dev.powderpass.gear.GearTier;
import dev.powderpass.pass.PassService;
import dev.powderpass.profile.Badge;
import dev.powderpass.profile.PlayerData;
import dev.powderpass.util.ItemBuilder;
import dev.powderpass.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/** The /pp hub. */
public final class MainMenu extends Menu {

    public MainMenu(PowderPassPlugin plugin, Player player) {
        super(5, Text.mm("<gradient:#38bdf8:#e0f2fe><bold>⛷ PowderPass</bold></gradient>"));
        PlayerData d = plugin.profiles().get(player);
        boolean mph = d.mph;

        ItemStack head = ItemBuilder.of(Material.PLAYER_HEAD).name("<#7dd3fc><bold>" + Text.escape(player.getName()))
                .lore(statLines(d, mph)).meta(m -> ((SkullMeta) m).setPlayerProfile(player.getPlayerProfile())).build();
        set(4, head);

        set(19, ItemBuilder.of(Material.NETHER_STAR).name("<#facc15><bold>Badges")
                .lore("<gray>Earned <white>" + d.badges.size() + "<gray>/" + Badge.values().length, "",
                        "<#bae6fd>Click to view").build(), e -> new BadgesMenu(plugin, player).open(player));

        set(21, ItemBuilder.of(Material.LEATHER_CHESTPLATE).color(d.suit.icon()).name("<#f472b6><bold>Locker")
                .lore("<gray>Suit: <white>" + d.suit.display(),
                        "<gray>Steering: <white>" + (d.steering != null ? d.steering : plugin.config().defaultSteering),
                        "<gray>Units: <white>" + (mph ? "mph" : "km/h"), "", "<#bae6fd>Click to customise")
                .hideAll().build(), e -> new LockerMenu(plugin, player).open(player));

        set(23, ItemBuilder.of(Material.RED_BANNER).name("<#f87171><bold>Race Courses")
                .lore("<gray>" + plugin.courses().courses().size() + " course(s)", "", "<#bae6fd>Click to view")
                .hideAll().build(), e -> new CoursesMenu(plugin, player).open(player));

        set(25, ItemBuilder.of(Material.MINECART).name("<#4ade80><bold>Chairlifts")
                .lore("<gray>" + plugin.lifts().definitions().size() + " lift(s)", "", "<#bae6fd>Click to view")
                .build(), e -> new LiftsMenu(plugin, player).open(player));

        PassService.PassInfo pass = plugin.passes().validPass(player);
        set(38, ItemBuilder.of(Material.PAPER).name("<#7dd3fc><bold>Your Lift Pass")
                .lore(pass == null
                        ? new String[]{"<gray>No valid pass.", "<dark_gray>Buy one at a ticket kiosk."}
                        : new String[]{"<white>" + pass.type().display(), "<gray>Valid until <white>" + PassService.formatExpiry(pass.expiry())})
                .glow(pass != null).build());

        set(40, ItemBuilder.of(Material.KNOWLEDGE_BOOK).name("<#e0f2fe><bold>How to ride")
                .lore("<#bae6fd>Right-click <gray>gear on snow: click in",
                        "<#bae6fd>Mouse <gray>steer · <#bae6fd>A/D <gray>carve harder",
                        "<#bae6fd>W <gray>pole / skate · <#bae6fd>S <gray>brake & skid",
                        "<#bae6fd>Shift <gray>tuck (faster)",
                        "<#bae6fd>Space <gray>hold to load, release to jump",
                        "<#bae6fd>F <gray>step out of your gear",
                        "",
                        "<white>In the air:",
                        "<#bae6fd>A/D <gray>spin · <#bae6fd>W/S <gray>front/back flip",
                        "<#bae6fd>Shift <gray>grab · land with skis straight!",
                        "",
                        "<white>Chairlifts: <gray>stand on the loading line.",
                        "<white>Racing: <gray>cross a START gate on skis.").build());

        set(42, ItemBuilder.of(Material.CRAFTING_TABLE).name("<#d4a373><bold>Gear Workshop")
                .lore("<white>Timber: <gray>planks around string",
                        "<white>Alpine: <gray>iron around Timber gear",
                        "<white>Glacier: <gray>diamonds around Alpine gear",
                        "<white>Avalanche: <gray>smith Glacier gear with netherite",
                        "",
                        "<gray>Skis: <white>M_M / MCM / M_M",
                        "<gray>Board: <white>_M_ / MCM / _M_",
                        "",
                        "<dark_gray>Tiers: " + GearTier.TIMBER.display() + " → " + GearTier.AVALANCHE.display()).build());
        fill(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
    }

    public static String[] statLines(PlayerData d, boolean mph) {
        return new String[]{
                "<gray>Top speed: <white>" + Text.speed(d.topSpeed, mph),
                "<gray>Distance: <white>" + Text.distance(d.distance),
                "<gray>Vertical: <white>" + Text.distance(d.vertical),
                "<gray>Air time: <white>" + Text.oneDecimal(d.airTicks / 20.0) + "s",
                "<gray>Best trick: <white>" + (d.bestTrickName.isEmpty() ? "-" : Text.escape(d.bestTrickName) + " <dark_gray>(" + Text.number(d.bestTrickScore) + ")"),
                "<gray>Best combo: <white>" + Text.number(d.bestCombo),
                "<gray>Tricks landed: <white>" + d.tricksLanded,
                "<gray>Lift rides: <white>" + d.liftRides,
                "<gray>Races finished: <white>" + d.racesFinished,
                "<gray>Wipeouts: <white>" + d.crashes};
    }
}
