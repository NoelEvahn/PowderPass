package com.powderpass.backrooms.creature;

import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.config.BackroomsConfig;
import com.powderpass.backrooms.gen.LevelGenerator;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spawns and ticks creatures. Spawning is rare and fair: per-player attempts on a slow timer, per-player and
 * per-level caps, never inside safe rooms, never in plain view (except Facelings, who like to be seen at a
 * distance), and type-specific placement rules (Smilers in the dark, Drifters in deep water...).
 */
public final class CreatureManager {
    private final BackroomsPlugin plugin;
    private final List<Creature> creatures = new ArrayList<>();
    private final Map<UUID, Creature> byHitbox = new HashMap<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private long lastCostNanos;

    public CreatureManager(BackroomsPlugin plugin) {
        this.plugin = plugin;
    }

    private BackroomsConfig cfg() {
        return plugin.cfg();
    }

    public void start() {
        stop();
        if (!cfg().creaturesEnabled) return;
        int interval = cfg().creatureTickInterval;
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickAll, 40L, interval));
        long attempt = cfg().spawnAttemptSeconds * 20L;
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, this::spawnPass, attempt, attempt));
    }

    public void stop() {
        for (BukkitTask t : tasks) t.cancel();
        tasks.clear();
        clearAll();
    }

    private void tickAll() {
        long start = System.nanoTime();
        int despawn = cfg().despawnDistance;
        for (Creature c : new ArrayList<>(creatures)) {
            if (c.dead()) continue;
            if (!c.hallucination() && nearestDistSq(c) > (double) despawn * despawn) {
                c.remove();
                continue;
            }
            try {
                c.tick();
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Creature " + c.type.id + " failed: " + ex);
                c.remove();
            }
        }
        lastCostNanos = (lastCostNanos * 7 + (System.nanoTime() - start)) / 8;
    }

    private static double nearestDistSq(Creature c) {
        double best = Double.MAX_VALUE;
        for (Player p : c.world().getPlayers()) best = Math.min(best, p.getLocation().distanceSquared(c.loc()));
        return best;
    }

    void unregister(Creature c) {
        creatures.remove(c);
        if (c.hitboxId() != null) byHitbox.remove(c.hitboxId());
    }

    // ------------------------------------------------------------------ spawning

    private void spawnPass() {
        for (Map.Entry<Level, World> e : plugin.worlds().worlds().entrySet()) {
            Level level = e.getKey();
            World w = e.getValue();
            for (Player p : w.getPlayers()) {
                if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) continue;
                double chance = cfg().levelChance.getOrDefault(level, 0.2);
                double sanity = plugin.sessions().sanity(p);
                if (sanity < 40) chance *= 1.5;
                if (plugin.environment().blackoutNear(p.getLocation())) chance *= 1.8;
                if (ThreadLocalRandom.current().nextDouble() >= chance) {
                    if (sanity < 30 && ThreadLocalRandom.current().nextDouble() < 0.25) hallucinate(p);
                    continue;
                }
                if (countNear(p.getLocation(), 48, false) >= cfg().maxPerPlayer) continue;
                if (countLevel(w) >= cfg().maxPerLevel) continue;
                CreatureType type = pickType(level);
                if (type != null) spawnNatural(type, p);
            }
        }
    }

    private CreatureType pickType(Level level) {
        Map<CreatureType, Double> weights = new EnumMap<>(CreatureType.class);
        double total = 0;
        for (CreatureType t : CreatureType.values()) {
            BackroomsConfig.CreatureSettings s = cfg().creature(t.id);
            if (!s.enabled()) continue;
            double wgt = s.weights().getOrDefault(level, 0.0);
            if (wgt > 0) {
                weights.put(t, wgt);
                total += wgt;
            }
        }
        if (total <= 0) return null;
        double r = ThreadLocalRandom.current().nextDouble() * total;
        for (Map.Entry<CreatureType, Double> e : weights.entrySet()) {
            r -= e.getValue();
            if (r <= 0) return e.getKey();
        }
        return null;
    }

    /** Natural spawn near a player following the type's placement rules. */
    public Creature spawnNatural(CreatureType type, Player p) {
        Location spot = switch (type) {
            case SMILER -> findSpot(p, 12, 26, 2, true, l -> l.getBlock().getLightFromBlocks() <= 5);
            case FACELING -> findSpot(p, 22, 36, 3, false, l -> true);
            case HOUND -> findSpot(p, 18, 32, 1, true, l -> true);
            case SPARK -> findSpot(p, 14, 28, 2, false, l -> true);
            case CONCIERGE -> findSpot(p, 12, 22, 2, true, l -> true);
            case DRIFTER -> findWaterSpot(p, 8, 22);
        };
        if (spot == null) return null;
        return spawn(type, spot, null);
    }

    public Creature spawn(CreatureType type, Location at, Player onlyViewer) {
        Creature c = Creatures.create(plugin, this, type);
        c.spawn(at, onlyViewer);
        creatures.add(c);
        if (c.hitboxId() != null) byHitbox.put(c.hitboxId(), c);
        plugin.debug("spawned " + type.id + " at " + at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ());
        return c;
    }

    /** A private, harmless sighting only one player can see. */
    public void hallucinate(Player p) {
        Location spot = findSpot(p, 16, 28, 3, false, l -> true);
        if (spot != null) spawn(CreatureType.FACELING, spot, p);
    }

    private interface SpotRule {
        boolean ok(Location l);
    }

    private Location findSpot(Player p, double min, double max, int height, boolean hidden, SpotRule rule) {
        World w = p.getWorld();
        LevelGenerator gen = plugin.worlds().generator(plugin.worlds().levelOf(w));
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location base = p.getLocation();
        for (int attempt = 0; attempt < 28; attempt++) {
            double a = r.nextDouble() * Math.PI * 2, d = min + r.nextDouble() * (max - min);
            int x = (int) Math.floor(base.getX() + Math.cos(a) * d), z = (int) Math.floor(base.getZ() + Math.sin(a) * d);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            for (int y = base.getBlockY() + 3; y >= base.getBlockY() - 4; y--) {
                if (!Nav.walkable(w, x, y, z, height, Nav.Medium.GROUND)) continue;
                if (gen != null && gen.isSafeZone(x, y, z)) break;
                Location l = new Location(w, x + 0.5, y, z + 0.5, (float) r.nextDouble(360), 0);
                if (hidden && p.hasLineOfSight(l.clone().add(0, 1.5, 0))) break;
                if (!rule.ok(l)) break;
                return l;
            }
        }
        return null;
    }

    private Location findWaterSpot(Player p, double min, double max) {
        World w = p.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location base = p.getLocation();
        for (int attempt = 0; attempt < 32; attempt++) {
            double a = r.nextDouble() * Math.PI * 2, d = min + r.nextDouble() * (max - min);
            int x = (int) Math.floor(base.getX() + Math.cos(a) * d), z = (int) Math.floor(base.getZ() + Math.sin(a) * d);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            for (int y = base.getBlockY() - 1; y >= base.getBlockY() - 8; y--) {
                Block b = w.getBlockAt(x, y, z);
                if (b.getType() == Material.WATER && w.getBlockAt(x, y - 1, z).getType() == Material.WATER
                        && w.getBlockAt(x, y - 2, z).getType() == Material.WATER) {
                    return new Location(w, x + 0.5, y - 1, z + 0.5);
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ world interaction

    /** Propagate a noise to creatures that listen (hounds). */
    public void noise(Location where, double radius) {
        for (Creature c : creatures) {
            if (c.world() == where.getWorld() && !c.hallucination() && c.loc().distanceSquared(where) <= radius * radius) {
                c.hear(where, radius);
            }
        }
    }

    void noteAttack(Player p, CreatureType type) {
        plugin.sessions().noteCreature(p, type);
    }

    void sanityHit(Player p, double amount) {
        plugin.sessions().addSanity(p, -amount);
    }

    void flickerAround(Location l, int radius) {
        plugin.environment().flicker(l, radius, 10);
    }

    public Creature byHitbox(Entity e) {
        return byHitbox.get(e.getUniqueId());
    }

    public int dismissNear(Location l, double radius, String typeId) {
        int n = 0;
        for (Creature c : new ArrayList<>(creatures)) {
            if (c.type.id.equals(typeId) && c.world() == l.getWorld() && c.loc().distanceSquared(l) <= radius * radius) {
                c.remove();
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ admin / status

    public int countNear(Location l, double radius, boolean includeHallucinations) {
        int n = 0;
        for (Creature c : creatures) {
            if ((includeHallucinations || !c.hallucination()) && c.world() == l.getWorld() && c.loc().distanceSquared(l) <= radius * radius) n++;
        }
        return n;
    }

    public int countLevel(World w) {
        int n = 0;
        for (Creature c : creatures) if (c.world() == w && !c.hallucination()) n++;
        return n;
    }

    public Map<CreatureType, Integer> counts() {
        Map<CreatureType, Integer> m = new EnumMap<>(CreatureType.class);
        for (Creature c : creatures) m.merge(c.type, 1, Integer::sum);
        return m;
    }

    public int entityCount() {
        int n = 0;
        for (Creature c : creatures) n += c.entityCount();
        return n;
    }

    public int clearRadius(Location l, double radius) {
        int n = 0;
        for (Creature c : new ArrayList<>(creatures)) {
            if (c.world() == l.getWorld() && c.loc().distanceSquared(l) <= radius * radius) {
                c.remove();
                n++;
            }
        }
        return n;
    }

    public int clearWorld(World w) {
        int n = 0;
        for (Creature c : new ArrayList<>(creatures)) {
            if (c.world() == w) {
                c.remove();
                n++;
            }
        }
        return n;
    }

    public int clearAll() {
        int n = creatures.size();
        for (Creature c : new ArrayList<>(creatures)) c.remove();
        creatures.clear();
        byHitbox.clear();
        return n;
    }

    public double costMillis() {
        return lastCostNanos / 1_000_000.0;
    }

    public int size() {
        return creatures.size();
    }
}
