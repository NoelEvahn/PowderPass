package com.powderpass.backrooms.creature;

import com.powderpass.backrooms.BackroomsPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Projectile;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
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
 * Every active possession, and the locks that keep a taken player inside their own body (they stay a normal
 * player in their own game mode: no spectator mode is involved). Also keeps the small,
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
    private final Set<UUID> ownTeleports = new HashSet<>();
    private final NamespacedKey victimKey, flySpeedKey, flightKey;
    /** Keys of every attribute modifier a possession puts on a player (removed on restore, also after a crash). */
    static final String[] LOCKS = {"speed", "jump", "gravity", "step", "fall"};
    private BukkitTask lockTask;

    public Possessions(BackroomsPlugin plugin) {
        this.plugin = plugin;
        this.voice = new VoiceBridge(plugin);
        this.victimKey = new NamespacedKey(plugin, "skinwalker_victim");
        this.flySpeedKey = new NamespacedKey(plugin, "skinwalker_flyspeed");
        this.flightKey = new NamespacedKey(plugin, "skinwalker_flight");
    }

    public void start() {
        if (lockTask != null) lockTask.cancel();
        // every tick: transformation, locomotion, the pull on the player, the organism's animation
        lockTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickAll, 1L, 1L);
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

    NamespacedKey flightKey() {
        return flightKey;
    }

    NamespacedKey lockKey(String name) {
        return new NamespacedKey(plugin, "skinwalker_lock_" + name);
    }

    /** Teleports the possession makes itself (corrections) are let through; any other teleport is refused. */
    void ownTeleport(UUID id, boolean on) {
        if (on) ownTeleports.add(id);
        else ownTeleports.remove(id);
    }

    private void tickAll() {
        for (Possession p : new ArrayList<>(byVictim.values())) {
            try {
                p.tick();
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("[Skinwalker] possession of " + p.victimName + " failed, releasing: " + ex);
                p.release(false);
            }
        }
    }

    /** The possession of this victim, or null. */
    Possession of(Player victim) {
        return byVictim.get(victim.getUniqueId());
    }

    public boolean isVictim(Player p) {
        return byVictim.containsKey(p.getUniqueId());
    }

    /** The organism's display parts riding this victim (empty if not taken). */
    public List<Entity> organismOf(Player victim) {
        Possession p = byVictim.get(victim.getUniqueId());
        return p == null ? List.of() : p.parts();
    }

    /** Is the victim still being transformed (true) or already carried (false)? Null if not taken. */
    public Boolean transforming(Player victim) {
        Possession p = byVictim.get(victim.getUniqueId());
        return p == null ? null : p.stage == Possession.Stage.TRANSFORMING;
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
            restore(victim, null, null);
            return null;
        }
        byVictim.put(victim.getUniqueId(), p);
        for (Entity e : p.parts()) byBody.put(e.getUniqueId(), p);
        plugin.debug("[Skinwalker] took " + victim.getName());
        return p;
    }

    /** A part (the hitbox) added after the possession began. */
    void bind(Possession p, Entity e) {
        if (e != null) byBody.put(e.getUniqueId(), p);
    }

    void forget(Possession p) {
        byVictim.remove(p.victimId, p);
        byBody.values().removeIf(x -> x == p);
    }

    void setReleasing(UUID id, boolean on) {
        if (on) releasing.add(id);
        else releasing.remove(id);
    }

    /**
     * Give a victim back everything a possession changed: the attribute modifiers (by key, so this also works
     * after a crash), flight, fly speed and gravity, a game mode an older build may have changed, and solid ground.
     * {@code mode} null keeps the current game mode (unless it is spectator, a leftover of older builds).
     */
    void restore(Player v, GameMode mode, Location at) {
        setReleasing(v.getUniqueId(), true);
        try {
            for (Entity e : new ArrayList<>(v.getPassengers())) {
                if (e.getPersistentDataContainer().has(com.powderpass.backrooms.display.Keys.CREATURE, PersistentDataType.STRING)) {
                    v.removePassenger(e);
                    e.remove();
                }
            }
            GameMode target = mode != null ? mode : v.getGameMode();
            if (target == GameMode.SPECTATOR) target = GameMode.SURVIVAL;
            if (v.getGameMode() == GameMode.SPECTATOR) v.setSpectatorTarget(null);
            if (v.getGameMode() != target) v.setGameMode(target);
        } finally {
            setReleasing(v.getUniqueId(), false);
        }
        Attribute[] attrs = {Attribute.MOVEMENT_SPEED, Attribute.JUMP_STRENGTH, Attribute.GRAVITY, Attribute.STEP_HEIGHT, Attribute.SAFE_FALL_DISTANCE};
        for (Attribute a : attrs) {
            for (String name : LOCKS) {
                try {
                    AttributeInstance inst = v.getAttribute(a);
                    if (inst != null) inst.removeModifier(lockKey(name));
                } catch (RuntimeException ignored) {
                    // not present, or not supported by this server
                }
            }
        }
        var pdc = v.getPersistentDataContainer();
        pdc.remove(victimKey);
        Float fly = pdc.get(flySpeedKey, PersistentDataType.FLOAT);
        if (fly != null) {
            v.setFlySpeed(fly > 0 ? fly : 0.1f);
            pdc.remove(flySpeedKey);
        }
        Byte flight = pdc.get(flightKey, PersistentDataType.BYTE);
        boolean creativeLike = v.getGameMode() == GameMode.CREATIVE || v.getGameMode() == GameMode.SPECTATOR;
        if (flight != null) {
            v.setAllowFlight((flight & 1) != 0 || creativeLike);
            v.setFlying(((flight & 2) != 0 || v.getGameMode() == GameMode.SPECTATOR) && v.getAllowFlight());
            v.setGravity((flight & 4) != 0);
            pdc.remove(flightKey);
        } else {
            v.setGravity(true);
            if (!creativeLike) {
                v.setFlying(false);
                v.setAllowFlight(false);
            }
        }
        v.setVelocity(new org.bukkit.util.Vector());
        Location dest = at == null ? null : plugin.teleports().findSafe(at, 5);
        if (dest == null && at != null) dest = at;
        if (dest != null) {
            dest.setYaw(v.getLocation().getYaw());
            dest.setPitch(v.getLocation().getPitch());
            ownTeleport(v.getUniqueId(), true);
            try {
                v.teleport(dest);
            } finally {
                ownTeleport(v.getUniqueId(), false);
            }
        }
        v.setFallDistance(0);
    }

    /** A victim left over from a crash or a disconnect: give them back their game mode. */
    private void recover(Player p) {
        var pdc = p.getPersistentDataContainer();
        String mode = pdc.get(victimKey, PersistentDataType.STRING);
        if (mode == null && pdc.has(flightKey, PersistentDataType.BYTE)) mode = p.getGameMode().name();
        if (mode == null || isVictim(p)) return;
        GameMode gm;
        try {
            gm = GameMode.valueOf(mode);
        } catch (IllegalArgumentException ex) {
            gm = GameMode.SURVIVAL;
        }
        plugin.getLogger().info("[Skinwalker] Restoring " + p.getName() + " (was taken by a skinwalker when they left).");
        restore(p, gm, p.getLocation());
    }

    /** Players near a point who could be prey, nearest first ({@code except} excluded). */
    List<Player> groupAround(Location at, double radius, UUID except) {
        List<Player> out = new ArrayList<>();
        for (Player p : at.getWorld().getPlayers()) {
            if (p.getUniqueId().equals(except) || isVictim(p)) continue;
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
    public void onTeleport(PlayerTeleportEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (!byVictim.containsKey(id) || ownTeleports.contains(id) || releasing.contains(id)) return;
        // nothing else moves a taken player: no pearls, no commands, no other plugins
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (byVictim.containsKey(id) && !releasing.contains(id)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent e) {
        if (e.isFlying() && byVictim.containsKey(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent e) {
        if (e.isGliding() && byVictim.containsKey(e.getEntity().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDismount(EntityDismountEvent e) {
        // the organism's parts never come off their host while it lives
        if (byBody.containsKey(e.getEntity().getUniqueId()) && byVictim.containsKey(e.getDismounted().getUniqueId())
                && !releasing.contains(e.getDismounted().getUniqueId()) && e.isCancellable()) e.setCancelled(true);
    }

    // the body is not theirs to use
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        if (byVictim.containsKey(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (byVictim.containsKey(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (byVictim.containsKey(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (byVictim.containsKey(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        if (byVictim.containsKey(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (byVictim.containsKey(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (byVictim.containsKey(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventory(InventoryOpenEvent e) {
        if (byVictim.containsKey(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    /**
     * Hitting a taken player (or one of the organism's parts) hurts the organism, never the player inside: their
     * friends can fight it without killing them. The victim's own swings do nothing.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPunch(PrePlayerAttackEntityEvent e) {
        if (byVictim.containsKey(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        Possession p = byVictim.get(e.getAttacked().getUniqueId());
        // the hitbox goes through the creature manager (CombatListener); display parts cannot be hit anyway
        if (p == null && !(e.getAttacked() instanceof Interaction)) p = byBody.get(e.getAttacked().getUniqueId());
        if (p == null) return;
        e.setCancelled(true);
        AttributeInstance a = e.getPlayer().getAttribute(Attribute.ATTACK_DAMAGE);
        double dmg = (a == null ? 1 : a.getValue()) * Math.max(0.2, e.getPlayer().getAttackCooldown());
        e.getPlayer().resetCooldown();
        p.walker.hurt(e.getPlayer(), dmg);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBodyDamage(EntityDamageEvent e) {
        Entity en = e.getEntity();
        if (byBody.containsKey(en.getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        Possession p = byVictim.get(en.getUniqueId());
        if (p == null) return;
        e.setCancelled(true);
        // arrows and the like from other players hurt the organism instead
        if (e instanceof EntityDamageByEntityEvent by) {
            Entity src = by.getDamager();
            if (src instanceof Projectile pr && pr.getShooter() instanceof Player sh) src = sh;
            if (src instanceof Player shooter && !shooter.getUniqueId().equals(p.victimId)) p.walker.hurt(shooter, e.getDamage());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent e) {
        Possession p = byVictim.get(e.getPlayer().getUniqueId());
        if (p != null) p.release(false);
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
