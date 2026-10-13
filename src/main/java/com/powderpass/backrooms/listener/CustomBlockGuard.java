package com.powderpass.backrooms.listener;

import com.powderpass.backrooms.BackroomsPlugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Keeps the Backrooms' custom blocks (powered note blocks in special states, see {@code CustomBlocks}) from ever
 * changing state: inside a Backrooms world a note block ignores neighbour updates (which would re-pick its
 * instrument and power), right-clicks (which would retune it) and never plays. Note blocks elsewhere are untouched.
 */
public final class CustomBlockGuard implements Listener {
    private final BackroomsPlugin plugin;

    public CustomBlockGuard(BackroomsPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean guarded(Block b) {
        return plugin.worlds().isBackrooms(b.getWorld());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent e) {
        Block b = e.getBlock();
        if (b.getType() == Material.NOTE_BLOCK && guarded(b)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onNote(NotePlayEvent e) {
        if (guarded(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTune(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || b == null || b.getType() != Material.NOTE_BLOCK || !guarded(b)) return;
        e.setUseInteractedBlock(Event.Result.DENY);
    }
}
