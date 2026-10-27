package dev.powderpass.volleyball.listener;

import dev.powderpass.volleyball.PowderPassPlugin;
import dev.powderpass.volleyball.util.Keys;
import dev.powderpass.volleyball.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.persistence.PersistentDataType;

/** Keeps match players safe and their kit intact; restores inventories after crashes. */
public final class ProtectionListener implements Listener {

    private final PowderPassPlugin plugin;

    public ProtectionListener(PowderPassPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean inMatch(Player p) {
        return plugin.matches().byPlayer(p) != null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && inMatch(p)) {
            e.setCancelled(true);
            return;
        }
        if (e.getEntity().getPersistentDataContainer().has(Keys.ENTITY, PersistentDataType.STRING)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && inMatch(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (inMatch(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (inMatch(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventory(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && inMatch(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        if (inMatch(e.getPlayer())) {
            plugin.matches().leave(e.getPlayer(), false);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        plugin.matches().leave(p, true);
        plugin.pepper().leave(p);
        plugin.hits().forget(p.getUniqueId());
        plugin.balls().removeOwnedBy(p.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (plugin.snapshots().has(p.getUniqueId()) && !inMatch(p)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && !inMatch(p) && plugin.snapshots().restore(p, true)) {
                    Msg.send(p, "<gray>Your inventory from your last match was restored.");
                }
            }, 10);
        }
    }
}
