package com.powderpass.backrooms.creature;

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;
import com.powderpass.backrooms.BackroomsPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Every active possession, and the locks that keep a worn player inside their own body. Also keeps the small,
 * in-memory record of what players say inside the Backrooms (last few chat lines; voice via {@link VoiceBridge})
 * that a skinwalker repeats. Nothing is written to disk except the one flag that guarantees a victim gets their
 * game mode back.
 */
public final class Possessions implements Listener {
    private static final int CHAT_LINES = 15;

    private final BackroomsPlugin plugin;
    private final VoiceBridge voice;
    private final Map<UUID, Possession> byVictim = new ConcurrentHashMap<>();
    private final Map<UUID, Possession> byBody = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<String>> chat = new ConcurrentHashMap<>();
    private final Set<UUID> releasing = new HashSet<>();
    private final NamespacedKey victimKey, flySpeedKey;
    private BukkitTask lockTask;
    private int lockRuns;

    public Possessions(BackroomsPlugin plugin) {
        this.plugin = plugin;
        this.voice = new VoiceBridge(plugin);
        this.victimKey = new NamespacedKey(plugin, "skinwalker_victim");
        this.flySpeedKey = new NamespacedKey(plugin, "skinwalker_flyspeed");
    }

    public void start() {
        if (lockTask != null) lockTask.cancel();
        // every 5 ticks: nobody worn can drift away from their body; once a second: their tab entry looks normal
        lockTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::enforce, 5L, 5L);
        voice.start();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            recover(p);
            if (plugin.worlds().isBackrooms(p.getWorld())) voice.record(p.getUniqueId(), plugin.cfg().skinwalkerVoiceMimic);
        }
    }

    /** Release everyone (alive), forget everything said. */
    public void stop() {
        if (lockTask != null) lockTask.cancel();
        lockTask = null;
        for (Possession p : new ArrayList<>(byVictim.values())) p.release(false);
        byVictim.clear();
        byBody.clear();
        chat.clear();
        voice.stop();
    }

    VoiceBridge voice() {
        return voice;
    }

    public String voiceState() {
        return voice.state();
    }

    NamespacedKey victimKey() {
        return victimKey;
    }

    NamespacedKey flySpeedKey() {
        return flySpeedKey;
    }

    private void enforce() {
        boolean tabs = lockRuns++ % 4 == 0;
        for (Possession p : byVictim.values()) {
            Player v = p.victim();
            if (v == null || !p.active()) continue;
            p.keepLocked(v);
            if (tabs) TabList.showAsSurvival(plugin, v, plugin.getServer().getOnlinePlayers());
        }
    }

    public boolean isVictim(Player p) {
        return byVictim.containsKey(p.getUniqueId());
    }

    /** The body a skinwalker is walking around in for this victim, or null. */
    public Entity bodyOf(Player victim) {
        Possession p = byVictim.get(victim.getUniqueId());
        return p == null ? null : p.body;
    }

    public int count() {
        return byVictim.size();
    }

    Possession begin(Skinwalker walker, Player victim) {
        if (isVictim(victim)) return null;
        Possession p;
        try {
            p = new Possession(plugin, this, walker, victim);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("[Skinwalker] could not take " + victim.getName() + ": " + ex);
            restore(victim, GameMode.SURVIVAL, null);
            return null;
        }
        byVictim.put(victim.getUniqueId(), p);
        byBody.put(p.body.getUniqueId(), p);
        plugin.debug("[Skinwalker] wearing " + victim.getName());
        return p;
    }

    void forget(Possession p) {
        byVictim.remove(p.victimId, p);
        byBody.remove(p.body.getUniqueId(), p);
    }

    void setReleasing(UUID id, boolean on) {
        if (on) releasing.add(id);
        else releasing.remove(id);
    }

    /** Give a victim their game mode back and put them on safe ground. */
    void restore(Player v, GameMode mode, Location at) {
        setReleasing(v.getUniqueId(), true);
        try {
            v.setSpectatorTarget(null);
            v.setGameMode(mode == GameMode.SPECTATOR ? GameMode.SURVIVAL : mode);
        } finally {
            setReleasing(v.getUniqueId(), false);
        }
        v.getPersistentDataContainer().remove(victimKey);
        Float fly = v.getPersistentDataContainer().get(flySpeedKey, PersistentDataType.FLOAT);
        if (fly != null) {
            v.setFlySpeed(fly > 0 ? fly : 0.1f);
            v.getPersistentDataContainer().remove(flySpeedKey);
        }
        Location dest = at == null ? null : plugin.teleports().findSafe(at, 5);
        if (dest == null && at != null) dest = at;
        if (dest != null) v.teleport(dest);
        v.setFallDistance(0);
    }

    /** A victim left over from a crash or a disconnect: give them back their game mode. */
    private void recover(Player p) {
        String mode = p.getPersistentDataContainer().get(victimKey, PersistentDataType.STRING);
        if (mode == null || isVictim(p)) return;
        GameMode gm;
        try {
            gm = GameMode.valueOf(mode);
        } catch (IllegalArgumentException ex) {
            gm = GameMode.SURVIVAL;
        }
        plugin.getLogger().info("[Skinwalker] Restoring " + p.getName() + " (was being worn when they left).");
        restore(p, gm, p.getLocation());
    }

    /** Players the body can pretend for, nearest first. */
    List<Player> groupAround(Location at, double radius, Possession self) {
        List<Player> out = new ArrayList<>();
        for (Player p : at.getWorld().getPlayers()) {
            if (p.getUniqueId().equals(self.victimId) || isVictim(p)) continue;
            if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) continue;
            if (p.getLocation().distanceSquared(at) <= radius * radius) out.add(p);
        }
        out.sort(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(at)));
        return out;
    }

    /** One of the last few things this player said in chat inside the Backrooms. */
    String recentLine(UUID player) {
        Deque<String> d = chat.get(player);
        if (d == null || d.isEmpty()) return null;
        List<String> l = new ArrayList<>(d);
        return l.get(ThreadLocalRandom.current().nextInt(l.size()));
    }

    // ------------------------------------------------------------------ locks

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (byVictim.containsKey(p.getUniqueId())) {
            e.setCancelled(true);
            p.sendMessage(Component.text("Your mouth doesn't move.").color(net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY));
            return;
        }
        if (!plugin.cfg().skinwalkerChatMimic || !plugin.worlds().isBackrooms(p.getWorld())) return;
        String text = PlainTextComponentSerializer.plainText().serialize(e.message());
        if (text.isBlank() || text.length() > 120) return;
        Deque<String> d = chat.computeIfAbsent(p.getUniqueId(), k -> new ArrayDeque<>());
        synchronized (d) {
            d.addLast(text);
            while (d.size() > CHAT_LINES) d.removeFirst();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!byVictim.containsKey(p.getUniqueId()) || !plugin.cfg().skinwalkerLockCommands) return;
        if (p.hasPermission("backrooms.bypass.skinwalker")) return;
        e.setCancelled(true);
        p.sendMessage(Component.text("Nothing happens.").color(net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY));
    }

    @EventHandler(ignoreCancelled = true)
    public void onStopSpectating(PlayerStopSpectatingEntityEvent e) {
        if (byVictim.containsKey(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE && byVictim.containsKey(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (byVictim.containsKey(id) && !releasing.contains(id)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPunch(PrePlayerAttackEntityEvent e) {
        Possession p = byBody.get(e.getAttacked().getUniqueId());
        if (p == null) return;
        e.setCancelled(true);
        p.punched(e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBodyDamage(EntityDamageEvent e) {
        Entity en = e.getEntity();
        if (byBody.containsKey(en.getUniqueId()) || byVictim.containsKey(en.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent e) {
        if (byVictim.isEmpty()) return;
        Location l = e.getPlayer().getLocation();
        for (Possession p : byVictim.values()) {
            if (p.body.isValid() && p.body.getWorld() == l.getWorld() && p.body.getLocation().distanceSquared(l) < 20 * 20) p.mirrorSwing();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Possession pos = byVictim.get(p.getUniqueId());
        if (pos != null) pos.release(false); // restored now, before their data is saved
        voice.record(p.getUniqueId(), false);
        chat.remove(p.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        recover(e.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        boolean inside = plugin.worlds().isBackrooms(p.getWorld());
        voice.record(p.getUniqueId(), inside && plugin.cfg().skinwalkerVoiceMimic);
        if (!inside) chat.remove(p.getUniqueId());
    }

    /** Test hook. */
    void injectVoice(UUID id, List<byte[]> frames) {
        voice.inject(id, frames);
    }

    Set<UUID> victims() {
        return new HashSet<>(byVictim.keySet());
    }
}
