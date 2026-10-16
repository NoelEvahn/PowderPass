package com.powderpass.backrooms.world;

import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.gen.LevelGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The way in: the player appears high above the level, falls straight down with their horizontal position locked
 * over the landing spot, and a 3x3 shaft is cut through the roof and ceiling just before they reach it. The shaft
 * is put back exactly as it was a couple of seconds after they land. Fall damage is cancelled for the whole fall.
 * <p>
 * Also hosts the old suffocation trigger: being buried in a falling block (sand, gravel, concrete powder) outside
 * the Backrooms for a moment swallows you into it.
 * <p>
 * Every shaft is written to {@code data/open-shafts.yml} before any block is removed and erased once restored, so a
 * crash mid-fall never leaves a permanent hole: pending shafts are restored on the next start.
 */
public final class FallEntry implements Listener {
    /** Gap between suffocation damage events that still counts as "continuous". */
    private static final long SUFFOCATION_GAP_MILLIS = 600;
    private static final int OPEN_RADIUS = 1;
    /** The shaft opens once the falling player is this far above the roof. */
    private static final int OPEN_MARGIN = 28;
    private static final long SHAFT_MIN_OPEN_MILLIS = 2000;
    private static final long LANDING_GRACE_MILLIS = 3000;
    private static final int MAX_FALL_TICKS = 20 * 25;

    private record Removed(int x, int y, int z, String data) {
    }

    private static final class Fall {
        final Level level;
        final Location landing;
        final double openY;
        int ticks;
        boolean opened;
        long openedAt;
        String shaftKey;
        List<Removed> removed = List.of();
        BukkitTask sound;

        Fall(Level level, Location landing, double openY) {
            this.level = level;
            this.landing = landing;
            this.openY = openY;
        }
    }

    private final BackroomsPlugin plugin;
    private final Map<UUID, Fall> falls = new HashMap<>();
    private final Map<UUID, Long> landedAt = new HashMap<>();
    private final Map<UUID, long[]> suffocation = new HashMap<>();
    private final File shaftFile;
    private final YamlConfiguration shafts;
    private BukkitTask watchdog;
    private int shaftCounter;

    public FallEntry(BackroomsPlugin plugin) {
        this.plugin = plugin;
        this.shaftFile = new File(new File(plugin.getDataFolder(), "data"), "open-shafts.yml");
        this.shafts = YamlConfiguration.loadConfiguration(shaftFile);
    }

    public void start() {
        stop();
        restorePendingShafts();
        watchdog = plugin.getServer().getScheduler().runTaskTimer(plugin, this::watch, 5L, 5L);
    }

    /** Lands everyone still falling and closes every shaft (used on disable and reload). */
    public void stop() {
        if (watchdog != null) watchdog.cancel();
        watchdog = null;
        for (UUID id : new ArrayList<>(falls.keySet())) {
            Player p = Bukkit.getPlayer(id);
            Fall f = falls.get(id);
            if (p != null && p.isOnline()) {
                p.teleport(f.landing);
                finish(p, f, true);
            } else {
                abort(id, true);
            }
        }
    }

    public boolean falling(Player p) {
        return falls.containsKey(p.getUniqueId());
    }

    // ------------------------------------------------------------------ the fall

    /**
     * Drop the player into the level that {@code target} belongs to. Chunks around the landing spot are loaded
     * asynchronously and the spot is validated first; if anything is wrong the normal safe teleport is used.
     */
    public void begin(Player p, Location target) {
        World w = target.getWorld();
        Level level = plugin.worlds().levelOf(w);
        if (w == null || level == null) {
            plugin.teleports().teleportSafe(p, target, l -> plugin.sessions().arrived(p, level));
            return;
        }
        plugin.sessions().get(p).inTransition = true;
        int cx = target.getBlockX() >> 4, cz = target.getBlockZ() >> 4;
        CompletableFuture<?>[] loads = new CompletableFuture<?>[9];
        int i = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) loads[i++] = w.getChunkAtAsync(cx + dx, cz + dz, true);
        CompletableFuture.allOf(loads).whenComplete((v, err) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            Location landing = err == null ? plugin.teleports().findSafe(target, 8) : null;
            if (landing == null) {
                plugin.debug("fall entry: no safe landing near target, using a normal teleport");
                plugin.teleports().teleportSafe(p, target, l -> plugin.sessions().arrived(p, level));
                return;
            }
            LevelGenerator gen = plugin.worlds().generator(level);
            double top = level.outdoor() ? landing.getY() : Math.max(gen.roofY, landing.getY());
            double skyY = Math.min(w.getMaxHeight() - 4, top + plugin.cfg().fallHeight);
            Location sky = landing.clone();
            sky.setY(skyY);
            sky.setPitch(70); // looking down at what is coming
            double openY = level.outdoor() ? Double.NEGATIVE_INFINITY : top + OPEN_MARGIN;
            Fall fall = new Fall(level, landing, openY);
            p.setFallDistance(0);
            p.setVelocity(new Vector());
            p.teleportAsync(sky, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(ok -> {
                if (!ok || !p.isOnline()) {
                    plugin.sessions().get(p).inTransition = false;
                    plugin.teleports().teleportSafe(p, landing, l -> plugin.sessions().arrived(p, level));
                    return;
                }
                if (p.isFlying()) p.setFlying(false);
                p.setFallDistance(0);
                falls.put(p.getUniqueId(), fall);
                p.playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.MASTER, 1f, 0.5f);
                fall.sound = plugin.getServer().getScheduler().runTaskTimer(plugin,
                        () -> p.playSound(p.getLocation(), Sound.ITEM_ELYTRA_FLYING, SoundCategory.MASTER, 0.8f, 1f), 0L, 20L);
                if (skyY <= openY) openShaft(w, fall);
            });
        }));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Fall f = falls.get(p.getUniqueId());
        if (f == null) return;
        Location to = e.getTo();
        if (to.getWorld() != f.landing.getWorld()) return;
        if (Math.abs(to.getX() - f.landing.getX()) > 1e-3 || Math.abs(to.getZ() - f.landing.getZ()) > 1e-3) {
            Location fixed = to.clone();
            fixed.setX(f.landing.getX());
            fixed.setZ(f.landing.getZ());
            e.setTo(fixed);
            to = fixed;
        }
        if (p.isFlying()) p.setFlying(false);
        if (!f.opened && to.getY() <= f.openY) openShaft(to.getWorld(), f);
        if (to.getY() <= f.landing.getY() + 0.01) {
            finish(p, f, false);
        } else if (p.isOnGround() && f.ticks > 10 && to.getY() > f.landing.getY() + 1.5) {
            // stopped on something that should not be there: put them down properly
            p.teleport(f.landing);
            finish(p, f, false);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            Long at = landedAt.get(p.getUniqueId());
            if (falls.containsKey(p.getUniqueId()) || (at != null && System.currentTimeMillis() - at < LANDING_GRACE_MILLIS)) {
                e.setCancelled(true);
            }
        } else if (e.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION) {
            suffocating(p, e);
        }
    }

    private void watch() {
        for (UUID id : new ArrayList<>(falls.keySet())) {
            Fall f = falls.get(id);
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) {
                abort(id, true);
                continue;
            }
            f.ticks += 5;
            if (p.getWorld() != f.landing.getWorld()) {
                abort(id, true);
                plugin.sessions().get(p).inTransition = false;
            } else if (f.ticks > MAX_FALL_TICKS) {
                p.teleport(f.landing);
                finish(p, f, false);
            }
        }
        long now = System.currentTimeMillis();
        landedAt.values().removeIf(t -> now - t > LANDING_GRACE_MILLIS);
        suffocation.values().removeIf(s -> now - s[1] > SUFFOCATION_GAP_MILLIS * 4);
    }

    private void finish(Player p, Fall f, boolean immediateRestore) {
        falls.remove(p.getUniqueId());
        landedAt.put(p.getUniqueId(), System.currentTimeMillis());
        if (f.sound != null) f.sound.cancel();
        p.stopSound(Sound.ITEM_ELYTRA_FLYING, SoundCategory.MASTER);
        p.setFallDistance(0);
        p.setVelocity(new Vector());
        if (immediateRestore) {
            restore(f);
        } else {
            long wait = f.opened ? Math.max(1, (SHAFT_MIN_OPEN_MILLIS - (System.currentTimeMillis() - f.openedAt)) / 50) : 1;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> restore(f), Math.max(wait, 40L));
        }
        p.playSound(p.getLocation(), Sound.BLOCK_SAND_PLACE, SoundCategory.MASTER, 1f, 0.6f);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_BIG_FALL, SoundCategory.MASTER, 0.8f, 0.7f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 2, false, false));
        if (!plugin.sessions().wakeUp(p, f.level)) plugin.sessions().arrived(p, f.level);
    }

    /** Ends a fall without landing logic (offline, world change, shutdown). */
    private void abort(UUID id, boolean restoreNow) {
        Fall f = falls.remove(id);
        if (f == null) return;
        if (f.sound != null) f.sound.cancel();
        if (restoreNow) restore(f);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        suffocation.remove(p.getUniqueId());
        Fall f = falls.get(p.getUniqueId());
        if (f == null) return;
        // log them out on the ground, not in the sky
        p.teleport(f.landing);
        finish(p, f, true);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Fall f = falls.get(e.getPlayer().getUniqueId());
        if (f != null && e.getPlayer().getWorld() != f.landing.getWorld()) {
            abort(e.getPlayer().getUniqueId(), true);
            plugin.sessions().get(e.getPlayer()).inTransition = false;
        }
    }

    // ------------------------------------------------------------------ the shaft

    private void openShaft(World w, Fall f) {
        f.opened = true;
        f.openedAt = System.currentTimeMillis();
        if (f.level.outdoor()) return;
        LevelGenerator gen = plugin.worlds().generator(f.level);
        int bx = f.landing.getBlockX(), bz = f.landing.getBlockZ();
        int from = f.landing.getBlockY() + 2, to = Math.min(w.getMaxHeight() - 1, gen.roofY + 1);
        List<Removed> removed = new ArrayList<>();
        for (int dx = -OPEN_RADIUS; dx <= OPEN_RADIUS; dx++) {
            for (int dz = -OPEN_RADIUS; dz <= OPEN_RADIUS; dz++) {
                for (int y = from; y <= to; y++) {
                    Block b = w.getBlockAt(bx + dx, y, bz + dz);
                    if (b.getType().isAir() || b.getState() instanceof org.bukkit.block.TileState) continue;
                    removed.add(new Removed(b.getX(), y, b.getZ(), b.getBlockData().getAsString()));
                }
            }
        }
        f.removed = removed;
        f.shaftKey = w.getName() + "-" + System.currentTimeMillis() + "-" + (shaftCounter++);
        persist(f.shaftKey, w, removed);
        for (Removed r : removed) w.getBlockAt(r.x(), r.y(), r.z()).setType(org.bukkit.Material.AIR, false);
        w.playSound(new Location(w, bx + 0.5, from + 2, bz + 0.5), Sound.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 1f, 0.5f);
    }

    private void restore(Fall f) {
        if (f.removed.isEmpty() || f.shaftKey == null) return;
        World w = f.landing.getWorld();
        if (w != null) {
            for (Removed r : f.removed) w.getBlockAt(r.x(), r.y(), r.z()).setBlockData(Bukkit.createBlockData(r.data()), false);
        }
        f.removed = List.of();
        shafts.set(f.shaftKey, null);
        save();
    }

    private void persist(String key, World w, List<Removed> removed) {
        List<String> rows = new ArrayList<>(removed.size());
        for (Removed r : removed) rows.add(r.x() + " " + r.y() + " " + r.z() + " " + r.data());
        shafts.set(key + ".world", w.getName());
        shafts.set(key + ".blocks", rows);
        save();
    }

    /** Close any shaft a crash left open. */
    private void restorePendingShafts() {
        int n = 0;
        for (String key : new ArrayList<>(shafts.getKeys(false))) {
            World w = Bukkit.getWorld(shafts.getString(key + ".world", ""));
            if (w == null) continue;
            for (String row : shafts.getStringList(key + ".blocks")) {
                String[] parts = row.split(" ", 4);
                if (parts.length < 4) continue;
                try {
                    w.getBlockAt(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]))
                            .setBlockData(Bukkit.createBlockData(parts[3]), false);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Could not restore shaft block '" + row + "': " + ex.getMessage());
                }
            }
            shafts.set(key, null);
            n++;
        }
        if (n > 0) {
            plugin.getLogger().info("Closed " + n + " entry shaft(s) left open by an unclean shutdown.");
            save();
        }
    }

    private void save() {
        try {
            File dir = shaftFile.getParentFile();
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
            shafts.save(shaftFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save " + shaftFile.getName() + ": " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ the old way in

    /** Buried in sand/gravel/concrete powder outside the Backrooms for long enough: it takes you. */
    private void suffocating(Player p, EntityDamageEvent e) {
        var cfg = plugin.cfg();
        if (!cfg.suffocationEnter || !p.hasPermission("backrooms.use")) return;
        if (plugin.worlds().isBackrooms(p.getWorld())) return;
        if (!cfg.allowedWorlds.isEmpty() && !cfg.allowedWorlds.contains(p.getWorld().getName())) return;
        if (!p.getEyeLocation().getBlock().getType().hasGravity()) {
            suffocation.remove(p.getUniqueId());
            return;
        }
        if (falling(p) || plugin.sessions().get(p).inTransition || plugin.worlds().world(Level.LEVEL_0) == null) return;
        e.setCancelled(true); // no damage while it decides
        long now = System.currentTimeMillis();
        long[] s = suffocation.get(p.getUniqueId());
        if (s == null || now - s[1] > SUFFOCATION_GAP_MILLIS) {
            s = new long[]{now, now};
            suffocation.put(p.getUniqueId(), s);
            p.playSound(p.getLocation(), Sound.BLOCK_SAND_BREAK, SoundCategory.MASTER, 1f, 0.5f);
        }
        s[1] = now;
        if (now - s[0] < cfg.suffocationMillis) return;
        suffocation.remove(p.getUniqueId());
        p.playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.MASTER, 1f, 0.6f);
        plugin.getLogger().info(p.getName() + " suffocated into the Backrooms.");
        plugin.sessions().enter(p, null);
    }
}
