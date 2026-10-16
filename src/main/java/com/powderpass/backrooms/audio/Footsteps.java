package com.powderpass.backrooms.audio;

import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.Level;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Footsteps that sound like the floor they are on. The Backrooms' textured blocks are note blocks to the client,
 * so vanilla plays a wooden step on carpet, tile and concrete alike; players with the pack also hear the right
 * surface (carpet, wet carpet, tile, concrete, water), from their feet, for themselves and anyone near. Running is
 * louder, sneaking silent. Falling into water splashes.
 */
public final class Footsteps implements Listener {
    private static final double STRIDE = 1.7;
    private final BackroomsPlugin plugin;
    private final Map<UUID, double[]> walked = new HashMap<>(); // distance since last step, was in water (0/1)

    public Footsteps(BackroomsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!plugin.cfg().audioFootsteps) return;
        Location from = e.getFrom(), to = e.getTo();
        if (from.getWorld() != to.getWorld()) return;
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        double dy = to.getY() - from.getY();
        if (dx * dx + dz * dz + dy * dy < 1e-4) return;
        Player p = e.getPlayer();
        Level level = plugin.worlds().levelOf(p.getWorld());
        if (level == null || p.getGameMode() == GameMode.SPECTATOR || p.isFlying() || p.isInsideVehicle()) return;
        if (plugin.possessions().isVictim(p)) return; // carried: the organism makes its own noise
        double[] st = walked.computeIfAbsent(p.getUniqueId(), k -> new double[2]);
        Block feet = to.getBlock();
        boolean water = feet.getType() == Material.WATER;
        // a splash on falling in
        if (water && st[1] == 0 && dy < -0.25) {
            plugin.audio().broadcast(to, "water.splash", org.bukkit.Sound.ENTITY_PLAYER_SPLASH, Math.min(1, 0.5 - dy), 1f, 28, SoundCategory.PLAYERS);
        }
        st[1] = water ? 1 : 0;
        if (!water && !p.isOnGround()) return;
        st[0] += Math.sqrt(dx * dx + dz * dz);
        double stride = p.isSprinting() ? STRIDE * 1.25 : STRIDE;
        if (st[0] < stride) return;
        st[0] = 0;
        if (p.isSneaking()) return;
        String id = surface(level, feet, water);
        if (id == null) return;
        double gain = p.isSprinting() ? 0.55 : 0.35;
        plugin.audio().broadcast(to, id, null, gain, 0.92f + (float) Math.random() * 0.16f, p.isSprinting() ? 20 : 14, SoundCategory.PLAYERS);
    }

    /** The step sound for what is under the player's feet, or null to leave it to vanilla. */
    static String surface(Level level, Block feet, boolean inWater) {
        if (inWater) return "step.water";
        Block under = feet.getRelative(0, -1, 0);
        Material m = under.getType();
        if (m == Material.AIR) under = under.getRelative(0, -1, 0); // slabs and carpets sit in the feet block
        if (feet.getType().name().endsWith("_CARPET")) return "step.carpet";
        String n = under.getType().name();
        if (n.endsWith("_WOOL") || n.endsWith("_CARPET")) return "step.carpet";
        if (n.contains("CONCRETE") || n.contains("STONE") || n.contains("DEEPSLATE") || n.contains("ANDESITE")) return level == Level.POOLROOMS ? "step.tile" : "step.concrete";
        if (n.contains("QUARTZ") || n.contains("TERRACOTTA") || n.contains("CALCITE") || n.contains("DIORITE")) return "step.tile";
        return switch (level) {
            case LEVEL_0 -> feet.getRelative(0, -1, 0).getType() == Material.NOTE_BLOCK ? "step.carpet" : null;
            case POOLROOMS -> "step.tile";
            case LEVEL_1 -> "step.concrete";
            case LEVEL_4 -> "step.carpet";
            case PLAINS -> null; // moss and grass: vanilla already sounds right
        };
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        walked.remove(e.getPlayer().getUniqueId());
    }
}
