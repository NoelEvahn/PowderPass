package com.powderpass.backrooms.listener;

import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.display.Keys;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.persistence.PersistentDataType;

/** Keeps the Backrooms intact and isolated from normal progression (each rule is configurable). */
public final class ProtectionListener implements Listener {
    private final BackroomsPlugin plugin;

    public ProtectionListener(BackroomsPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean br(World w) {
        return plugin.worlds().isBackrooms(w);
    }

    private boolean protect(Player p) {
        return plugin.cfg().protectBlocks && !p.hasPermission("backrooms.bypass.protection");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBreak(BlockBreakEvent e) {
        if (br(e.getBlock().getWorld()) && protect(e.getPlayer())) {
            e.setCancelled(true);
            e.getPlayer().sendActionBar(plugin.cfg().raw("protected"));
            plugin.creatures().noise(e.getBlock().getLocation(), 12);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlace(BlockPlaceEvent e) {
        if (!br(e.getBlock().getWorld())) return;
        if (protect(e.getPlayer())) {
            e.setCancelled(true);
            e.getPlayer().sendActionBar(plugin.cfg().raw("protected"));
            return;
        }
        if (e.getBlockPlaced().getState() instanceof Barrel b) {
            b.getPersistentDataContainer().set(Keys.PLACED, PersistentDataType.BYTE, (byte) 1);
            b.update();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (br(e.getBlock().getWorld()) && protect(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (br(e.getBlock().getWorld()) && protect(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent e) {
        if (e.getPlayer() != null && br(e.getBlock().getWorld()) && protect(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        if (br(e.getEntity().getWorld()) && e.getRemover() instanceof Player p && protect(p)) e.setCancelled(true);
    }

    /** Water must stay perfectly still: every pool is designed as contained source blocks. */
    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        if (br(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        if (br(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFade(BlockFadeEvent e) {
        if (br(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (br(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (br(e.getBlock().getWorld()) && (e.getPlayer() == null || protect(e.getPlayer()))) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (br(e.getEntity().getWorld())) e.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (br(e.getBlock().getWorld())) e.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onChangeBlock(EntityChangeBlockEvent e) {
        if (br(e.getBlock().getWorld()) && !(e.getEntity() instanceof Player p && !protect(p))) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (!br(e.getLocation().getWorld())) return;
        switch (e.getSpawnReason()) {
            case NATURAL, SPAWNER, PATROL, RAID, REINFORCEMENTS, VILLAGE_DEFENSE, VILLAGE_INVASION, NETHER_PORTAL, TRAP, JOCKEY, MOUNT, CHUNK_GEN -> e.setCancelled(true);
            default -> {
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent e) {
        if (plugin.cfg().disablePortals && br(e.getWorld())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent e) {
        if (plugin.cfg().disablePortals && br(e.getFrom().getWorld())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBed(PlayerBedEnterEvent e) {
        if (plugin.cfg().disableBeds && br(e.getBed().getWorld())) {
            e.setCancelled(true);
            e.getPlayer().sendActionBar(plugin.cfg().raw("protected"));
        }
    }
}
