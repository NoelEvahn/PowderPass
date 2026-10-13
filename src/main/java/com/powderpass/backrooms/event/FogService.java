package com.powderpass.backrooms.event;

import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.Level;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fog without a resource pack or packets. The client draws its fog at the edge of its effective render distance,
 * which is the smaller of its own setting and the distance the server sends chunks to that player; so lowering a
 * player's send view distance ({@link Player#setSendViewDistance}) pulls the fog in. Each level has a resting fog distance, and
 * fog banks (the {@code fog} event) roll in for a minute or two, with drifting ash for haze. Everything is restored
 * when the player leaves the Backrooms.
 */
public final class FogService implements Listener {
    private record Bank(long until, int distance) {
    }

    private final BackroomsPlugin plugin;
    private final Map<UUID, Bank> banks = new HashMap<>();
    private BukkitTask task;
    private long ticks;
    private boolean unsupported;

    public FogService(BackroomsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        banks.clear();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (plugin.worlds().isBackrooms(p.getWorld())) apply(p, -1);
        }
    }

    /** Resting fog distance in chunks for a level, or 0 for "leave the view distance alone". */
    public int restingDistance(Level level) {
        return plugin.cfg().fogEnabled ? plugin.cfg().fogDistance.getOrDefault(level, 0) : 0;
    }

    /** Roll a fog bank in around this player for {@code seconds}. Returns false if fog is off for their level. */
    public boolean bank(Player p, int seconds) {
        Level level = plugin.worlds().levelOf(p.getWorld());
        if (level == null || !plugin.cfg().fogEnabled) return false;
        int d = Math.max(2, plugin.cfg().fogBankDistance);
        banks.put(p.getUniqueId(), new Bank(ticks + seconds, d));
        apply(p, d);
        return true;
    }

    public boolean inBank(Player p) {
        Bank b = banks.get(p.getUniqueId());
        return b != null && b.until > ticks;
    }

    private void tick() {
        ticks++;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            World w = p.getWorld();
            Level level = plugin.worlds().levelOf(w);
            if (level == null) continue;
            Bank b = banks.get(p.getUniqueId());
            if (b != null && b.until <= ticks) {
                banks.remove(p.getUniqueId());
                b = null;
            }
            int target = b != null ? b.distance : restingDistance(level);
            apply(p, target <= 0 ? -1 : target);
            if (b != null || (level == Level.PLAINS && target > 0)) haze(p, b != null);
        }
    }

    /** Drifting ash in the air close around the player: only they see it, so it costs nothing elsewhere. */
    private void haze(Player p, boolean thick) {
        Location l = p.getLocation().add(0, 1.2, 0);
        int count = thick ? 30 : 4;
        p.spawnParticle(Particle.WHITE_ASH, l, count, 7, 2.5, 7, 0.0);
        if (thick && ThreadLocalRandom.current().nextInt(4) == 0) p.spawnParticle(Particle.ASH, l, 10, 5, 2, 5, 0.0);
    }

    /** Sets the player's send view distance; -1 restores the world default. */
    private void apply(Player p, int distance) {
        if (unsupported) return;
        try {
            int d = distance < 0 ? -1 : Math.max(2, Math.min(32, distance));
            if (d < 0 ? p.getSendViewDistance() != p.getWorld().getSendViewDistance() : p.getSendViewDistance() != d) p.setSendViewDistance(d);
        } catch (UnsupportedOperationException | NoSuchMethodError ex) {
            unsupported = true;
            plugin.getLogger().warning("Per-player view distance is not supported by this server; Backrooms fog is disabled.");
        } catch (RuntimeException ex) {
            // e.g. a test server without the method implemented
            unsupported = true;
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (!plugin.worlds().isBackrooms(p.getWorld())) {
            banks.remove(p.getUniqueId());
            if (plugin.worlds().isBackrooms(e.getFrom())) apply(p, -1);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!plugin.worlds().isBackrooms(p.getWorld())) apply(p, -1);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        banks.remove(e.getPlayer().getUniqueId());
    }
}
