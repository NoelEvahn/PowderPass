package com.powderpass.backrooms.vehicle;

import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.display.GarageProps;
import com.powderpass.backrooms.gen.DecorSpec;
import com.powderpass.backrooms.gen.LevelGenerator;
import com.powderpass.backrooms.util.Rng;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The sound of Level 1's leaking steam pipes. The leak points come from the same deterministic layout as the pipe
 * models' particles ({@link GarageProps#leaks}), so every hiss has visible steam over it. Players with the pack hear
 * a positional {@code steam.hiss} loop from the nearest two leaks (re-triggered at its length); everyone gets now
 * and then a burst ({@code steam.burst}, or vanilla extinguish sounds) with a jet of cloud.
 */
final class SteamLeaks {
    private static final double HISS_RADIUS = 11, BURST_RADIUS = 20;
    private final BackroomsPlugin plugin;
    private final Map<Long, List<Location>> cache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, List<Location>> e) {
            return size() > 256;
        }
    };
    /** Per listener: leak -> tick at which its hiss must be re-triggered. */
    private final Map<UUID, Map<Location, Long>> playing = new HashMap<>();
    private BukkitTask task;
    private long tick;

    SteamLeaks(BackroomsPlugin plugin) {
        this.plugin = plugin;
    }

    void start() {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::pass, 40L, 10L);
    }

    void stop() {
        if (task != null) task.cancel();
        task = null;
        playing.clear();
        cache.clear();
    }

    /** Leak points of the pipe runs anchored in a chunk. */
    List<Location> leaks(World w, LevelGenerator g, int cx, int cz) {
        long k = ((long) cx << 32) ^ (cz & 0xffffffffL);
        return cache.computeIfAbsent(k, kk -> {
            List<Location> out = new ArrayList<>();
            for (DecorSpec d : g.decorations(cx, cz)) {
                if (!d.model.startsWith("garage_pipes")) continue;
                int c = d.model.indexOf(':');
                double len = c < 0 ? 15.5 : Double.parseDouble(d.model.substring(c + 1));
                Matrix4f base = new Matrix4f().rotateY((float) Math.toRadians(-d.yaw));
                for (Vector3f p : GarageProps.leaks(len, d.seed)) {
                    Vector3f v = base.transformPosition(new Vector3f(p));
                    out.add(new Location(w, d.x + v.x, d.y + v.y, d.z + v.z));
                }
            }
            return out;
        });
    }

    private void pass() {
        tick += 10;
        World w = plugin.worlds().world(Level.LEVEL_1);
        LevelGenerator g = plugin.worlds().generator(Level.LEVEL_1);
        if (w == null || g == null) return;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        var def = plugin.audio().index().def("steam.hiss");
        int loop = def == null ? 100 : Math.max(20, def.durationTicks() - 6);
        for (Player p : w.getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            Location pl = p.getLocation();
            List<Location> near = new ArrayList<>();
            int pcx = pl.getBlockX() >> 4, pcz = pl.getBlockZ() >> 4;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (Location l : leaks(w, g, pcx + dx, pcz + dz)) if (l.distanceSquared(pl) < BURST_RADIUS * BURST_RADIUS) near.add(l);
                }
            }
            near.sort(Comparator.comparingDouble(l -> l.distanceSquared(pl)));
            Map<Location, Long> mine = playing.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
            String hiss = plugin.audio().custom(p) && def != null ? plugin.audio().index().resolve("steam.hiss", null, false) : null;
            for (int i = 0; i < Math.min(2, near.size()); i++) {
                Location l = near.get(i);
                if (l.distanceSquared(pl) > HISS_RADIUS * HISS_RADIUS) break;
                Long until = mine.get(l);
                if (until != null && until > tick) continue;
                if (hiss != null) {
                    p.playSound(l, "backrooms:" + hiss, SoundCategory.AMBIENT, 0.55f, 0.95f + rnd.nextFloat() * 0.1f);
                    mine.put(l, tick + loop);
                } else {
                    // vanilla stand-in: a soft intermittent hiss
                    p.playSound(l, Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.AMBIENT, 0.12f, 1.7f + rnd.nextFloat() * 0.3f);
                    mine.put(l, tick + 60 + rnd.nextInt(80));
                }
            }
            mine.keySet().removeIf(l -> l.distanceSquared(pl) > BURST_RADIUS * BURST_RADIUS);
        }
        // bursts: each leak near anyone vents hard about once every two minutes
        for (Player p : w.getPlayers()) {
            Location pl = p.getLocation();
            for (Location l : leaks(w, g, pl.getBlockX() >> 4, pl.getBlockZ() >> 4)) {
                if (l.distanceSquared(pl) > BURST_RADIUS * BURST_RADIUS) continue;
                long h = Rng.hash(l.getBlockX(), l.getBlockZ(), tick / 10);
                if (!Rng.chance(h, 10.0 / (20 * 120))) continue;
                burst(w, l, rnd);
            }
        }
    }

    private void burst(World w, Location l, ThreadLocalRandom rnd) {
        for (int i = 0; i < 24; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            w.spawnParticle(Particle.CLOUD, l, 0, Math.cos(a) * 0.25, -0.15 - rnd.nextDouble() * 0.2, Math.sin(a) * 0.25, 1.0);
        }
        w.spawnParticle(Particle.WHITE_SMOKE, l, 20, 0.2, 0.1, 0.2, 0.05);
        var def = plugin.audio().index().def("steam.burst");
        String ev = def == null ? null : plugin.audio().index().resolve("steam.burst", null, false);
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(l) > 32 * 32) continue;
            if (ev != null && plugin.audio().custom(p)) {
                p.playSound(l, "backrooms:" + ev, SoundCategory.AMBIENT, 1.0f, 1.0f);
            } else {
                p.playSound(l, Sound.BLOCK_LAVA_EXTINGUISH, SoundCategory.AMBIENT, 0.6f, 0.8f);
                p.playSound(l, Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.AMBIENT, 0.5f, 0.6f);
            }
        }
    }
}
