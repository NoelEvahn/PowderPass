package dev.powderpass.volleyball.ui;

import dev.powderpass.volleyball.PowderPassPlugin;
import dev.powderpass.volleyball.court.Court;
import dev.powderpass.volleyball.game.Match;
import dev.powderpass.volleyball.game.MatchSettings;
import dev.powderpass.volleyball.game.Rule;
import dev.powderpass.volleyball.game.TeamId;
import dev.powderpass.volleyball.game.Fault;
import dev.powderpass.volleyball.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Chest GUIs: rules editor and referee panel. */
public final class Menus implements Listener {

    private final PowderPassPlugin plugin;

    public Menus(PowderPassPlugin plugin) {
        this.plugin = plugin;
    }

    /** Base holder mapping slots to click handlers. */
    private static final class Menu implements InventoryHolder {
        final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();
        Inventory inventory;
        Runnable refresh;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    // ------------------------------------------------------------------------------------------- rules

    /**
     * Opens the rules editor.
     *
     * @param court  court whose settings are edited (saved to courts.yml), or null when editing a running match
     * @param target settings object to edit
     */
    public void openRules(Player p, Court court, MatchSettings target, String title) {
        Menu menu = new Menu();
        menu.inventory = Bukkit.createInventory(menu, 54, Msg.mm("<dark_gray>Rules · " + Msg.escape(title)));
        menu.refresh = () -> fillRules(p, menu, court, target, title);
        menu.refresh.run();
        p.openInventory(menu.inventory);
    }

    private void fillRules(Player p, Menu menu, Court court, MatchSettings s, String title) {
        Inventory inv = menu.inventory;
        inv.clear();
        menu.actions.clear();
        Runnable saved = () -> {
            if (court != null) {
                plugin.courts().save();
            }
            menu.refresh.run();
        };
        int slot = 0;
        for (Rule r : Rule.values()) {
            boolean on = s.rule(r);
            List<String> lore = new ArrayList<>();
            for (String d : r.description()) {
                lore.add("<gray>" + d);
            }
            lore.add("");
            lore.add(on ? "<green><b>ENABLED</b>" : "<red><b>DISABLED</b>");
            lore.add("<dark_gray>Click to toggle");
            inv.setItem(slot, icon(r.icon(), (on ? "<green>" : "<red>") + r.displayName(), on, lore));
            menu.actions.put(slot, click -> {
                s.setRule(r, !s.rule(r));
                saved.run();
            });
            slot++;
        }
        slot = 27;
        for (MatchSettings.Option o : MatchSettings.Option.values()) {
            int v = s.get(o);
            Material mat = o.isToggle() ? (v != 0 ? Material.LIME_DYE : Material.GRAY_DYE) : Material.PAPER;
            List<String> lore = new ArrayList<>();
            if (o.isToggle()) {
                lore.add(v != 0 ? "<green>ON" : "<red>OFF");
                lore.add("<dark_gray>Click to toggle");
            } else {
                lore.add("<white>Value: <yellow><b>" + v + "</b>");
                lore.add("<dark_gray>Left-click +" + o.step + " · Right-click -" + o.step);
                lore.add("<dark_gray>Shift-click ±5");
            }
            ItemStack it = icon(mat, "<yellow>" + o.label, false, lore);
            if (!o.isToggle()) {
                it.setAmount(Math.max(1, Math.min(64, v)));
            }
            inv.setItem(slot, it);
            menu.actions.put(slot, click -> {
                if (o.isToggle()) {
                    s.set(o, s.get(o) == 0 ? 1 : 0);
                } else {
                    int step = click.isShiftClick() ? 5 * o.step : o.step;
                    s.set(o, s.get(o) + (click.isRightClick() ? -step : step));
                }
                saved.run();
            });
            slot++;
        }
        // presets
        preset(menu, 45, Material.FEATHER, "<aqua>Quick game", "1 set to 15", s, 1, 15, 15, saved);
        preset(menu, 46, Material.IRON_INGOT, "<aqua>Standard", "Best of 3 to 25 (3rd set to 15)", s, 2, 25, 15, saved);
        preset(menu, 47, Material.GOLD_INGOT, "<aqua>Official", "Best of 5 to 25 (5th set to 15)", s, 3, 25, 15, saved);
        preset(menu, 48, Material.SAND, "<aqua>Beach", "Best of 3 to 21 (3rd set to 15)", s, 2, 21, 15, saved);
        inv.setItem(50, icon(Material.LIME_CONCRETE, "<green>All rules on", false, List.of("<gray>Official rule set")));
        menu.actions.put(50, c -> {
            for (Rule r : Rule.values()) {
                s.setRule(r, true);
            }
            saved.run();
        });
        inv.setItem(51, icon(Material.YELLOW_CONCRETE, "<yellow>Casual rules", false,
                List.of("<gray>Only in/out, four hits and antennas.", "<gray>Great for learning.")));
        menu.actions.put(51, c -> {
            for (Rule r : Rule.values()) {
                s.setRule(r, r == Rule.FOUR_HITS || r == Rule.ANTENNA || r == Rule.CEILING_OUT);
            }
            saved.run();
        });
        if (court != null) {
            inv.setItem(53, icon(Material.EMERALD_BLOCK, "<green><b>Start match</b>", true,
                    List.of("<gray>Starts with the players in the lobby", "<gray>of <white>" + Msg.escape(court.name()))));
            menu.actions.put(53, c -> {
                p.closeInventory();
                String err = plugin.matches().start(court, p);
                if (err != null) {
                    Msg.error(p, Msg.escape(err));
                }
            });
        }
    }

    private void preset(Menu menu, int slot, Material mat, String name, String desc, MatchSettings s,
                        int sets, int points, int deciding, Runnable saved) {
        menu.inventory.setItem(slot, icon(mat, name, false, List.of("<gray>" + desc, "<dark_gray>Click to apply")));
        menu.actions.put(slot, c -> {
            s.set(MatchSettings.Option.SETS_TO_WIN, sets);
            s.set(MatchSettings.Option.POINTS_PER_SET, points);
            s.set(MatchSettings.Option.DECIDING_SET_POINTS, deciding);
            saved.run();
        });
    }

    // ------------------------------------------------------------------------------------------- referee

    public void openReferee(Player p, Match m) {
        Menu menu = new Menu();
        menu.inventory = Bukkit.createInventory(menu, 27, Msg.mm("<dark_gray>Referee · " + Msg.escape(m.court().name())));
        menu.refresh = () -> fillReferee(p, menu, m);
        menu.refresh.run();
        p.openInventory(menu.inventory);
    }

    private void fillReferee(Player p, Menu menu, Match m) {
        Inventory inv = menu.inventory;
        inv.clear();
        menu.actions.clear();
        boolean paused = m.state() == dev.powderpass.volleyball.game.MatchState.PAUSED;
        inv.setItem(10, icon(paused ? Material.LIME_DYE : Material.ORANGE_DYE, paused ? "<green>Resume" : "<gold>Pause",
                false, List.of("<gray>Freeze the match.")));
        menu.actions.put(10, c -> {
            m.togglePause();
            menu.refresh.run();
        });
        inv.setItem(11, icon(Material.CLOCK, "<yellow>Replay rally", false, List.of("<gray>No point, serve again.")));
        menu.actions.put(11, c -> {
            m.replayRally();
            p.closeInventory();
        });
        for (TeamId id : TeamId.values()) {
            int slot = id == TeamId.A ? 12 : 14;
            var t = m.team(id);
            inv.setItem(slot, icon(id == TeamId.A ? Material.RED_BANNER : Material.BLUE_BANNER,
                    "<white>Point to <b>" + Msg.escape(t.name()) + "</b>", false, List.of("<gray>Referee decision.")));
            menu.actions.put(slot, c -> {
                m.awardPoint(id, Fault.REFEREE, null);
                p.closeInventory();
            });
        }
        inv.setItem(13, icon(Material.ARROW, "<yellow>Undo last point", false, List.of("<gray>Restore the previous score.")));
        menu.actions.put(13, c -> {
            if (!m.undoLastPoint()) {
                Msg.error(p, "Nothing to undo.");
            }
            p.closeInventory();
        });
        inv.setItem(15, icon(Material.WRITABLE_BOOK, "<aqua>Rules of this match", false, List.of("<gray>Change rules live.")));
        menu.actions.put(15, c -> openRules(p, null, m.settings(), m.court().name() + " (live)"));
        inv.setItem(16, icon(Material.BARRIER, "<red>End match", false, List.of("<gray>Stops the match, no winner.")));
        menu.actions.put(16, c -> {
            plugin.matches().stop(m.court(), "The referee ended the match.");
            p.closeInventory();
        });
    }

    // ------------------------------------------------------------------------------------------- events

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof Menu menu)) {
            return;
        }
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != menu.inventory) {
            return;
        }
        Consumer<ClickType> action = menu.actions.get(e.getSlot());
        if (action != null && e.getWhoClicked() instanceof Player p) {
            action.accept(e.getClick());
            p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder(false) instanceof Menu) {
            e.setCancelled(true);
        }
    }

    private static ItemStack icon(Material mat, String name, boolean glow, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Msg.mm(name).decoration(TextDecoration.ITALIC, false));
        List<Component> l = new ArrayList<>();
        for (String s : lore) {
            l.add(Msg.mm(s).decoration(TextDecoration.ITALIC, false).colorIfAbsent(NamedTextColor.GRAY));
        }
        meta.lore(l);
        meta.addItemFlags(ItemFlag.values());
        if (glow) {
            meta.setEnchantmentGlintOverride(true);
        }
        it.setItemMeta(meta);
        return it;
    }
}
