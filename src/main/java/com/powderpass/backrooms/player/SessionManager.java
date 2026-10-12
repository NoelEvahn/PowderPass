package com.powderpass.backrooms.player;

import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.config.BackroomsConfig;
import com.powderpass.backrooms.creature.CreatureType;
import com.powderpass.backrooms.display.Keys;
import com.powderpass.backrooms.gen.Feature;
import com.powderpass.backrooms.gen.LevelGenerator;
import com.powderpass.backrooms.util.Rng;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Entry/exit rules and per-player state.
 *
 * <p>Persistence: the return location and an "inside" flag are stored in the player's own
 * PersistentDataContainer, so they survive restarts, crashes and plugin reloads together with the player
 * data itself. A player found outside the Backrooms with the inside flag still set (e.g. their level was
 * disabled) is "stranded" and is sent home on join.</p>
 */
public final class SessionManager {
    private final BackroomsPlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private long ticks;

    public SessionManager(BackroomsPlugin plugin) {
        this.plugin = plugin;
    }

    private BackroomsConfig cfg() {
        return plugin.cfg();
    }

    public void start() {
        stop();
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, this::sanityPass, 40L, 40L));
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, () -> ticks++, 1L, 1L));
        for (Player p : Bukkit.getOnlinePlayers()) load(p);
    }

    public void stop() {
        for (BukkitTask t : tasks) t.cancel();
        tasks.clear();
        for (Player p : Bukkit.getOnlinePlayers()) save(p);
    }

    public long now() {
        return ticks;
    }

    public Session get(Player p) {
        return sessions.computeIfAbsent(p.getUniqueId(), k -> {
            Session s = new Session();
            Double san = p.getPersistentDataContainer().get(Keys.SANITY, PersistentDataType.DOUBLE);
            if (san != null) s.sanity = san;
            return s;
        });
    }

    public void load(Player p) {
        get(p);
    }

    public void save(Player p) {
        Session s = sessions.get(p.getUniqueId());
        if (s != null) p.getPersistentDataContainer().set(Keys.SANITY, PersistentDataType.DOUBLE, s.sanity);
    }

    public void unload(Player p) {
        save(p);
        sessions.remove(p.getUniqueId());
    }

    // ------------------------------------------------------------------ return locations

    public boolean inside(Player p) {
        return plugin.worlds().isBackrooms(p.getWorld());
    }

    public void saveReturn(Player p, Location l) {
        if (l == null || l.getWorld() == null || plugin.worlds().isBackrooms(l.getWorld())) return;
        String v = l.getWorld().getUID() + ";" + l.getWorld().getName() + ";" + l.getX() + ";" + l.getY() + ";" + l.getZ() + ";" + l.getYaw() + ";" + l.getPitch();
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(Keys.RETURN, PersistentDataType.STRING, v);
        pdc.set(Keys.INSIDE, PersistentDataType.BYTE, (byte) 1);
    }

    public Location getReturn(Player p) {
        String v = p.getPersistentDataContainer().get(Keys.RETURN, PersistentDataType.STRING);
        if (v != null) {
            String[] a = v.split(";");
            try {
                World w = Bukkit.getWorld(UUID.fromString(a[0]));
                if (w == null) w = Bukkit.getWorld(a[1]);
                if (w != null && !plugin.worlds().isBackrooms(w)) {
                    return new Location(w, Double.parseDouble(a[2]), Double.parseDouble(a[3]), Double.parseDouble(a[4]),
                            Float.parseFloat(a[5]), Float.parseFloat(a[6]));
                }
            } catch (RuntimeException ignored) {
                // malformed: fall back
            }
        }
        return fallback(p);
    }

    public boolean hasReturn(Player p) {
        return p.getPersistentDataContainer().has(Keys.RETURN, PersistentDataType.STRING);
    }

    private Location fallback(Player p) {
        World w = cfg().fallbackWorld.isBlank() ? null : Bukkit.getWorld(cfg().fallbackWorld);
        if (w == null || plugin.worlds().isBackrooms(w)) {
            for (World x : Bukkit.getWorlds()) {
                if (!plugin.worlds().isBackrooms(x)) {
                    w = x;
                    break;
                }
            }
        }
        Location bed = p.getRespawnLocation();
        if (bed != null && bed.getWorld() != null && !plugin.worlds().isBackrooms(bed.getWorld())) return bed;
        return w == null ? null : w.getSpawnLocation();
    }

    public void clearInside(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.remove(Keys.RETURN);
        pdc.remove(Keys.INSIDE);
    }

    public boolean insideFlag(Player p) {
        return p.getPersistentDataContainer().has(Keys.INSIDE, PersistentDataType.BYTE);
    }

    // ------------------------------------------------------------------ enter / leave

    /** Enter Level 0 (or move to Level 0 if already inside). */
    public boolean enter(Player p, CommandSender feedback) {
        World w0 = plugin.worlds().world(Level.LEVEL_0);
        if (w0 == null) {
            if (feedback != null) feedback.sendMessage(cfg().msg("level-disabled", "level", Level.LEVEL_0.displayName()));
            return false;
        }
        if (!inside(p)) saveReturn(p, p.getLocation());
        Location target = entryTarget(p, true);
        p.sendMessage(cfg().msg("entering"));
        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 50, 0, false, false));
        p.playSound(p.getLocation(), Sound.BLOCK_SAND_FALL, SoundCategory.MASTER, 1f, 0.5f);
        plugin.teleports().teleportSafe(p, target, l -> arrived(p, Level.LEVEL_0));
        return true;
    }

    /**
     * Where this player's next entry lands: a different region of Level 0 per player and per entry, so players
     * are not forced into one shared spot. Always the centre of a generator-guaranteed safe cell.
     */
    public Location entryTarget(Player p, boolean consume) {
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "entries");
        int count = p.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, 0);
        if (consume) p.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, count + 1);
        int spread = cfg().spreadRadius;
        long h = Rng.hash(Rng.salt(p.getUniqueId().toString()), count);
        int x = spread == 0 ? 0 : Rng.range(h, -spread, spread);
        int z = spread == 0 ? 0 : Rng.range(Rng.hash(h, 1), -spread, spread);
        int[] s = plugin.worlds().generator(Level.LEVEL_0).safeSpot(x, z, 0);
        return new Location(plugin.worlds().world(Level.LEVEL_0), s[0] + 0.5, s[1], s[2] + 0.5, (float) (Rng.unit(Rng.hash(h, 2)) * 360), 0);
    }

    /** Teleport to a level's admin/default spawn. */
    public void toLevel(Player p, Level level) {
        Location spawn = plugin.worlds().levelSpawn(level);
        if (spawn == null) return;
        if (!inside(p)) saveReturn(p, p.getLocation());
        plugin.teleports().teleportSafe(p, spawn, l -> arrived(p, level));
    }

    public void arrived(Player p, Level level) {
        Session s = get(p);
        s.inTransition = false;
        s.zone = null;
        s.zoneTicks = 0;
        s.arrivalTick = ticks;
        s.cooldownUntil = ticks + cfg().transitionCooldownTicks;
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(Keys.INSIDE, PersistentDataType.BYTE, (byte) 1);
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, cfg().arrivalProtectionTicks, 4, false, false));
        if (cfg().entryTitle) {
            p.showTitle(Title.title(BackroomsConfig.mm(level.color() + "<bold>" + level.displayName().toUpperCase()),
                    BackroomsConfig.mm("<gray><italic>" + level.subtitle()),
                    Title.Times.times(Duration.ofMillis(800), Duration.ofMillis(2500), Duration.ofMillis(1500))));
        }
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.AMBIENT, 0.6f, 0.5f);
        if (cfg().suppressMusic) p.stopSound(SoundCategory.MUSIC);
    }

    /** Leave the Backrooms to the saved return location (or fallback). */
    public boolean leave(Player p, CommandSender feedback) {
        Location target = getReturn(p);
        if (target == null) {
            plugin.getLogger().warning("No return location or fallback world for " + p.getName());
            return false;
        }
        Session s = get(p);
        s.inTransition = true;
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, false, false));
        plugin.teleports().teleportSafe(p, target, l -> {
            s.inTransition = false;
            clearInside(p);
            p.sendMessage(cfg().msg("left"));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_BREATH, SoundCategory.PLAYERS, 1f, 1f);
        }).thenAccept(ok -> {
            if (!ok) s.inTransition = false;
        });
        return true;
    }

    // ------------------------------------------------------------------ sanity

    public double sanity(Player p) {
        return get(p).sanity;
    }

    public void addSanity(Player p, double delta) {
        Session s = get(p);
        s.sanity = Math.max(0, Math.min(100, s.sanity + delta));
    }

    private void sanityPass() {
        if (!cfg().sanityEnabled) return;
        for (World w : plugin.worlds().worlds().values()) {
            Level level = plugin.worlds().levelOf(w);
            for (Player p : w.getPlayers()) {
                if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) continue;
                Session s = get(p);
                int light = p.getEyeLocation().getBlock().getLightFromBlocks();
                double d = 0;
                if (light <= 4) d -= 0.7;
                else if (light <= 8) d -= 0.25;
                else if (light >= 12) d += 0.15;
                if (level == Level.POOLROOMS) d += 0.1;
                if (level == Level.LEVEL_4) d -= 0.1;
                if (plugin.worlds().generator(level).isSafeZone(p.getLocation().getBlockX(), p.getLocation().getBlockY(), p.getLocation().getBlockZ())) d += 0.8;
                for (Player o : w.getPlayers()) {
                    if (o != p && o.getLocation().distanceSquared(p.getLocation()) < 64) {
                        d += 0.3;
                        break;
                    }
                }
                if (plugin.creatures().countNear(p.getLocation(), 16, false) > 0) d -= 0.6;
                s.sanity = Math.max(0, Math.min(100, s.sanity + d));
                effects(p, s);
            }
        }
    }

    private void effects(Player p, Session s) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (s.sanity < 50 && r.nextDouble() < 0.08) {
            Location behind = p.getLocation().add(p.getLocation().getDirection().multiply(-3));
            p.playSound(behind, Sound.ENTITY_VEX_AMBIENT, SoundCategory.AMBIENT, 0.3f, 0.5f);
        }
        if (s.sanity < 25 && r.nextDouble() < 0.12) {
            p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.AMBIENT, 0.9f, 1f);
        }
        if (s.sanity < 12 && r.nextDouble() < 0.06) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 120, 0, false, false));
        }
        if (cfg().sanityActionbar && Math.abs(s.lastShownSanity - s.sanity) >= 5) {
            s.lastShownSanity = s.sanity;
            p.sendActionBar(BackroomsConfig.mm("<gray>Sanity: <white>" + (int) s.sanity + "%"));
        }
    }

    // ------------------------------------------------------------------ bookkeeping used by other systems

    public void noteCreature(Player p, CreatureType t) {
        Session s = get(p);
        s.lastCreature = t;
        s.lastCreatureAt = ticks;
        addSanity(p, -4);
    }

    public void noteHazard(Player p, String type) {
        Session s = get(p);
        s.lastHazard = type;
        s.lastHazardAt = ticks;
    }

    public Component deathMessage(Player p) {
        Session s = get(p);
        if (s.lastCreature != null && ticks - s.lastCreatureAt < 200) {
            return BackroomsConfig.mm("<gray>" + p.getName() + " was taken by a " + s.lastCreature.displayName);
        }
        if (s.lastHazard != null && ticks - s.lastHazardAt < 200) {
            return BackroomsConfig.mm("<gray>" + p.getName() + (s.lastHazard.equals("shock") ? " touched the wrong cable" : " got too close to the pipes"));
        }
        return BackroomsConfig.mm("<gray>" + p.getName() + " stopped wandering");
    }

    public boolean tryVend(Player p) {
        Session s = get(p);
        if (s.vendCooldownUntil > ticks) return false;
        s.vendCooldownUntil = ticks + 20L * 600;
        return ThreadLocalRandom.current().nextDouble() < 0.12;
    }

    public boolean debug(Player p) {
        return get(p).debug;
    }

    public void setDebug(Player p, boolean on) {
        get(p).debug = on;
    }

    public Feature zone(Player p) {
        return get(p).zone;
    }
}
