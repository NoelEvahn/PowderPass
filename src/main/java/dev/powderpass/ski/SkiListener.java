package dev.powderpass.ski;

import dev.powderpass.PowderPassPlugin;
import dev.powderpass.gear.Gear;
import dev.powderpass.gear.GearItems;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Wires player actions to ski sessions. */
public final class SkiListener implements Listener {

    private final PowderPassPlugin plugin;
    private final SkiManager skis;

    public SkiListener(PowderPassPlugin plugin, SkiManager skis) {
        this.plugin = plugin;
        this.skis = skis;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        EquipmentSlot hand = event.getHand();
        if (hand == null) {
            return;
        }
        ItemStack item = event.getItem();
        Gear gear = GearItems.identify(item);
        if (gear == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (hand == EquipmentSlot.OFF_HAND && GearItems.identify(player.getInventory().getItemInMainHand()) != null) {
            return; // main hand already handled
        }
        if (!skis.skiing(player)) {
            skis.start(player, gear);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        // Stop carrot-on-a-stick gear from interacting with entities (e.g. saddled pigs).
        if (GearItems.identify(event.getPlayer().getInventory().getItem(event.getHand())) != null
                && !(event.getRightClicked() instanceof org.bukkit.entity.Interaction)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        SkiSession session = skis.session(event.getPlayer());
        if (session != null) {
            event.setCancelled(true);
            skis.end(session, SkiSession.EndReason.UNCLIP, true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        SkiSession session = skis.session(player);
        if (session == null || session.dismountAllowed() || !event.getDismounted().equals(session.rig().anchor())) {
            return;
        }
        if (event.isCancellable()) {
            // Shift is the tuck, not "get off". F steps out of the gear.
            event.setCancelled(true);
        } else {
            plugin.getServer().getScheduler().runTask(plugin, () -> skis.end(session, SkiSession.EndReason.INVALID, true));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        // Must run before the server saves the player together with their vehicle.
        skis.end(event.getPlayer(), SkiSession.EndReason.QUIT, true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        skis.end(event.getPlayer(), SkiSession.EndReason.DEATH, false);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        SkiSession session = skis.session(event.getPlayer());
        if (session != null && !session.ended()) {
            skis.end(session, SkiSession.EndReason.TELEPORT, false);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        skis.end(event.getPlayer(), SkiSession.EndReason.WORLD, false);
    }

    @EventHandler(ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() == GameMode.SPECTATOR) {
            skis.end(event.getPlayer(), SkiSession.EndReason.INVALID, true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        SkiSession session = skis.session(event.getPlayer());
        if (session != null) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!session.ended()) {
                    session.visibility().broadcastFake();
                }
            });
        }
    }

    @EventHandler
    public void onTrack(PlayerTrackEntityEvent event) {
        Entity tracked = event.getEntity();
        if (!(tracked instanceof Player target)) {
            return;
        }
        SkiSession session = skis.session(target);
        if (session != null) {
            Player viewer = event.getPlayer();
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!session.ended()) {
                    session.visibility().sendFake(viewer);
                }
            }, 2L);
        }
    }
}
