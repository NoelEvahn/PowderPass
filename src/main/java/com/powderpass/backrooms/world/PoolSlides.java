package com.powderpass.backrooms.world;

import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.gen.LevelGenerator;
import com.powderpass.backrooms.gen.levels.PoolroomsGenerator;
import net.kyori.adventure.key.Key;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Poolrooms water slides. The chutes are pure layout ({@link PoolroomsGenerator#slideStartingAt}): stepping into the
 * first two blocks of a chute starts a ride, and every tick the rider is steered along the generator's path with
 * velocity (teleported back onto it only if they drift far off), so the ride needs no stored state beyond who is
 * riding and how far along they are. At the end they are thrown out over the landing pool with a splash.
 *
 * <p>Safety: fall damage is cancelled during the ride and for a few seconds after; a ride ends cleanly if the rider
 * quits, dies, changes world or game mode to spectator, is teleported away, or makes no progress for two seconds.</p>
 */
public final class PoolSlides implements Listener {
    private static final double START_SPEED = 0.42, MAX_SPEED = 0.9, ACCEL = 0.012;
    private static final long GRACE_MILLIS = 4000, COOLDOWN_MILLIS = 2500;

    private final BackroomsPlugin plugin;
    private final Map<UUID, Ride> rides = new HashMap<>();
    private final Map<UUID, Long> grace = new HashMap<>();
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private BukkitTask task;

    private static final class Ride {
        final PoolroomsGenerator.Slide slide;
        final World world;
        final double[] cum;
        double s, speed = START_SPEED;
        int ticks;

        Ride(PoolroomsGenerator.Slide slide, World world) {
            this.slide = slide;
            this.world = world;
            double[][] p = slide.points();
            cum = new double[p.length];
            for (int i = 1; i < p.length; i++) {
                cum[i] = cum[i - 1] + Math.sqrt(sq(p[i][0] - p[i - 1][0]) + sq(p[i][1] - p[i - 1][1]) + sq(p[i][2] - p[i - 1][2]));
            }
        }

        double length() {
            return cum[cum.length - 1];
        }

        /** Point at distance d along the path (linear between path points). */
        double[] at(double d) {
            double[][] p = slide.points();
            if (d <= 0) return p[0].clone();
            for (int i = 1; i < p.length; i++) {
                if (cum[i] >= d) {
                    double t = (d - cum[i - 1]) / Math.max(1e-6, cum[i] - cum[i - 1]);
                    return new double[]{p[i - 1][0] + (p[i][0] - p[i - 1][0]) * t, p[i - 1][1] + (p[i][1] - p[i - 1][1]) * t,
                            p[i - 1][2] + (p[i][2] - p[i - 1][2]) * t};
                }
            }
            return p[p.length - 1].clone();
        }
    }

    private static double sq(double d) {
        return d * d;
    }

    public PoolSlides(BackroomsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        for (UUID id : rides.keySet()) grace.put(id, System.currentTimeMillis());
        rides.clear();
    }

    /** Is this player on a slide right now? */
    public boolean riding(Player p) {
        return rides.containsKey(p.getUniqueId());
    }

    private PoolroomsGenerator generator(World w) {
        if (w == null || plugin.worlds() == null || plugin.worlds().levelOf(w) != Level.POOLROOMS) return null;
        LevelGenerator g = plugin.worlds().generator(Level.POOLROOMS);
        return g instanceof PoolroomsGenerator pg ? pg : null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location to = e.getTo();
        if (to.getBlockX() == e.getFrom().getBlockX() && to.getBlockY() == e.getFrom().getBlockY() && to.getBlockZ() == e.getFrom().getBlockZ()) return;
        Player p = e.getPlayer();
        if (rides.containsKey(p.getUniqueId()) || p.getGameMode() == GameMode.SPECTATOR || p.isFlying()) return;
        Long cd = cooldown.get(p.getUniqueId());
        if (cd != null && System.currentTimeMillis() < cd) return;
        PoolroomsGenerator g = generator(to.getWorld());
        if (g == null) return;
        PoolroomsGenerator.Slide s = g.slideStartingAt(to.getBlockX(), to.getBlockY(), to.getBlockZ());
        if (s != null) begin(p, s);
    }

    /** Start a ride (also usable by admin tools). */
    public void begin(Player p, PoolroomsGenerator.Slide s) {
        Ride r = new Ride(s, p.getWorld());
        rides.put(p.getUniqueId(), r);
        p.setFallDistance(0);
        playCustom(p, "slide.rush", Sound.ITEM_ELYTRA_FLYING, 0.9f, 1.0f, 0.35f, 1.7f);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_SPLASH, SoundCategory.PLAYERS, 0.6f, 1.3f);
        p.getWorld().spawnParticle(Particle.SPLASH, p.getLocation(), 20, 0.3, 0.1, 0.3, 0.1);
    }

    private void tick() {
        if (rides.isEmpty()) {
            if (!grace.isEmpty()) grace.values().removeIf(t -> System.currentTimeMillis() - t > GRACE_MILLIS);
            return;
        }
        for (Iterator<Map.Entry<UUID, Ride>> it = rides.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Ride> en = it.next();
            Player p = plugin.getServer().getPlayer(en.getKey());
            Ride r = en.getValue();
            if (p == null || !p.isOnline() || p.isDead() || p.getWorld() != r.world || p.getGameMode() == GameMode.SPECTATOR) {
                it.remove();
                if (p != null) end(p, false);
                continue;
            }
            r.ticks++;
            r.speed = Math.min(MAX_SPEED, r.speed + ACCEL);
            r.s += r.speed;
            Location loc = p.getLocation();
            if (r.s >= r.length()) {
                it.remove();
                // thrown out over the landing pool, still carrying the slide's momentum
                double[] d = r.slide.exitDir();
                p.setVelocity(new Vector(d[0] * 0.55, 0.05, d[2] * 0.55));
                end(p, true);
                continue;
            }
            double[] target = r.at(r.s), ahead = r.at(r.s + 1.0);
            double dx = target[0] - loc.getX(), dy = target[1] - loc.getY(), dz = target[2] - loc.getZ();
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > 12.0 || r.ticks > 20 * 20) {
                // teleported away by something else, or somehow still riding after 20 s: let go
                it.remove();
                end(p, false);
                continue;
            }
            if (dist > 3.5) {
                // knocked off the line: put them back on it
                p.teleport(new Location(r.world, target[0], target[1], target[2], loc.getYaw(), loc.getPitch()));
                continue;
            }
            // velocity along the chute plus a correction toward the target point
            double tx = ahead[0] - target[0], ty = ahead[1] - target[1], tz = ahead[2] - target[2];
            double tl = Math.max(1e-6, Math.sqrt(tx * tx + ty * ty + tz * tz));
            Vector vel = new Vector(tx / tl * r.speed + dx * 0.5, ty / tl * r.speed + dy * 0.5 + 0.04, tz / tl * r.speed + dz * 0.5);
            if (vel.length() > 1.6) vel.normalize().multiply(1.6);
            p.setVelocity(vel);
            p.setFallDistance(0);
            if (r.ticks % 2 == 0) {
                r.world.spawnParticle(Particle.SPLASH, loc.clone().add(0, 0.1, 0), 6, 0.25, 0.05, 0.25, 0.05);
            }
            if (r.ticks % 5 == 0) r.world.spawnParticle(Particle.BUBBLE_POP, loc.clone().add(0, 0.2, 0), 3, 0.2, 0.1, 0.2, 0.02);
        }
        grace.values().removeIf(t -> System.currentTimeMillis() - t > GRACE_MILLIS);
    }

    private void end(Player p, boolean splash) {
        UUID id = p.getUniqueId();
        grace.put(id, System.currentTimeMillis());
        cooldown.put(id, System.currentTimeMillis() + COOLDOWN_MILLIS);
        p.setFallDistance(0);
        if (!splash) return;
        // the splash when they hit the water, a few ticks later
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            p.setFallDistance(0);
            Location l = p.getLocation();
            l.getWorld().spawnParticle(Particle.SPLASH, l, 60, 0.8, 0.2, 0.8, 0.3);
            l.getWorld().spawnParticle(Particle.BUBBLE, l, 30, 0.6, 0.4, 0.6, 0.1);
            playCustom(p, "water.splash", Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.0f, 1.0f, 0.9f, 1.0f);
        }, 8L);
    }

    /** Custom pack sound when this player has the pack and the id exists, else a vanilla stand-in. */
    private void playCustom(Player p, String id, Sound fallback, float vol, float pitch, float fbVol, float fbPitch) {
        String event = null;
        try {
            if (plugin.pack() != null && plugin.pack().loaded(p) && plugin.audio() != null) event = plugin.audio().index().resolve(id, null, false);
        } catch (RuntimeException ignored) {
            event = null;
        }
        if (event != null) {
            p.playSound(net.kyori.adventure.sound.Sound.sound(Key.key("backrooms", event), net.kyori.adventure.sound.Sound.Source.PLAYER, vol, pitch),
                    net.kyori.adventure.sound.Sound.Emitter.self());
        } else {
            p.playSound(p.getLocation(), fallback, SoundCategory.PLAYERS, fbVol, fbPitch);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || e.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        UUID id = p.getUniqueId();
        Long t = grace.get(id);
        if (rides.containsKey(id) || (t != null && System.currentTimeMillis() - t < GRACE_MILLIS)) e.setCancelled(true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        rides.remove(e.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        rides.remove(id);
        grace.remove(id);
        cooldown.remove(id);
    }
}
