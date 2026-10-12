package com.powderpass.backrooms.event;

import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.config.BackroomsConfig;
import com.powderpass.backrooms.creature.CreatureType;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ambience and environmental events. Light effects are purely client-side ({@code sendBlockChange}): the world
 * is never modified, so a crash or reload can never leave lights broken, and every effect restores itself.
 */
public final class EnvironmentManager {
    private static final Set<Material> LIGHTS = Set.of(Material.OCHRE_FROGLIGHT, Material.PEARLESCENT_FROGLIGHT, Material.VERDANT_FROGLIGHT,
            Material.SEA_LANTERN, Material.SHROOMLIGHT, Material.GLOWSTONE, Material.LANTERN, Material.WAXED_COPPER_BULB, Material.LIGHT,
            Material.REDSTONE_WALL_TORCH);
    private static final Map<Level, Material> OFF = new EnumMap<>(Level.class);

    static {
        OFF.put(Level.LEVEL_0, Material.WHITE_TERRACOTTA);
        OFF.put(Level.POOLROOMS, Material.WHITE_CONCRETE);
        OFF.put(Level.LEVEL_1, Material.LIGHT_GRAY_CONCRETE);
        OFF.put(Level.LEVEL_2, Material.DEEPSLATE_BRICKS);
        OFF.put(Level.LEVEL_3, Material.GRAY_CONCRETE);
        OFF.put(Level.LEVEL_4, Material.SMOOTH_QUARTZ);
        OFF.put(Level.LEVEL_5, Material.DARK_OAK_PLANKS);
    }

    private final BackroomsPlugin plugin;
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final List<Blackout> blackouts = new ArrayList<>();
    private long tick;

    private record Blackout(World world, Location center, double radius, long until) {
    }

    public EnvironmentManager(BackroomsPlugin plugin) {
        this.plugin = plugin;
    }

    private BackroomsConfig cfg() {
        return plugin.cfg();
    }

    public void start() {
        stop();
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick++, 1L, 1L));
        if (cfg().ambient) {
            tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, this::ambientPass, 60L, cfg().ambientSeconds * 20L));
        }
        if (cfg().eventsEnabled) {
            long period = cfg().eventCheckSeconds * 20L;
            tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, this::eventPass, period, period));
        }
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, this::housekeeping, 100L, 200L));
    }

    public void stop() {
        for (BukkitTask t : tasks) t.cancel();
        tasks.clear();
        blackouts.clear();
    }

    private void housekeeping() {
        blackouts.removeIf(b -> b.until < tick);
        if (!cfg().suppressMusic) return;
        for (World w : plugin.worlds().worlds().values()) {
            for (Player p : w.getPlayers()) p.stopSound(SoundCategory.MUSIC);
        }
    }

    public boolean blackoutNear(Location l) {
        for (Blackout b : blackouts) {
            if (b.world == l.getWorld() && b.until >= tick && b.center.distanceSquared(l) <= b.radius * b.radius) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ ambience

    private void ambientPass() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Map.Entry<Level, World> e : plugin.worlds().worlds().entrySet()) {
            for (Player p : e.getValue().getPlayers()) {
                Location near = p.getLocation().add(r.nextDouble(-6, 6), 2, r.nextDouble(-6, 6));
                Location far = p.getLocation().add(r.nextDouble(-24, 24), 0, r.nextDouble(-24, 24));
                switch (e.getKey()) {
                    case LEVEL_0 -> {
                        p.playSound(near, Sound.BLOCK_BEACON_AMBIENT, SoundCategory.AMBIENT, 0.25f, 1.35f + r.nextFloat() * 0.15f);
                        if (r.nextDouble() < 0.15) p.playSound(far, Sound.BLOCK_WOOD_STEP, SoundCategory.AMBIENT, 0.4f, 0.6f);
                    }
                    case POOLROOMS -> {
                        p.playSound(near, Sound.BLOCK_POINTED_DRIPSTONE_DRIP_WATER, SoundCategory.AMBIENT, 0.6f, 0.9f);
                        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                                p.playSound(far, Sound.BLOCK_POINTED_DRIPSTONE_DRIP_WATER, SoundCategory.AMBIENT, 0.3f, 0.85f), 7L);
                        if (p.isInWater()) p.playSound(p.getLocation(), Sound.AMBIENT_UNDERWATER_LOOP_ADDITIONS, SoundCategory.AMBIENT, 0.7f, 0.8f);
                    }
                    case LEVEL_1 -> {
                        p.playSound(near, Sound.BLOCK_BEACON_AMBIENT, SoundCategory.AMBIENT, 0.15f, 0.8f);
                        if (r.nextDouble() < 0.25) p.playSound(far, Sound.BLOCK_IRON_DOOR_CLOSE, SoundCategory.AMBIENT, 0.3f, 0.5f);
                    }
                    case LEVEL_2 -> {
                        p.playSound(near, Sound.BLOCK_LAVA_AMBIENT, SoundCategory.AMBIENT, 0.3f, 0.6f);
                        if (r.nextDouble() < 0.3) p.playSound(near, Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.AMBIENT, 0.2f, 1.6f);
                        if (plugin.worlds().generator(Level.LEVEL_2) instanceof com.powderpass.backrooms.gen.levels.Level2Generator g
                                && g.heat(p.getLocation().getBlockX(), p.getLocation().getBlockZ()) > 0.68 && p.getGameMode() == GameMode.SURVIVAL) {
                            p.setExhaustion(p.getExhaustion() + 0.6f);
                            p.getWorld().spawnParticle(Particle.WHITE_ASH, p.getLocation().add(0, 1.5, 0), 6, 1.5, 0.5, 1.5, 0.01);
                        }
                    }
                    case LEVEL_3 -> {
                        p.playSound(near, Sound.BLOCK_BEACON_AMBIENT, SoundCategory.AMBIENT, 0.3f, 0.75f);
                        if (r.nextDouble() < 0.3) p.playSound(near, Sound.BLOCK_CONDUIT_AMBIENT, SoundCategory.AMBIENT, 0.25f, 0.7f);
                    }
                    case LEVEL_4 -> {
                        if (r.nextDouble() < 0.5) {
                            for (int i = 0; i < 6; i++) {
                                plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                                        p.playSound(far, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, SoundCategory.AMBIENT, 0.25f, 1.8f + (float) Math.random() * 0.2f), i * 3L + r.nextInt(3));
                            }
                        }
                        p.playSound(near, Sound.ITEM_ELYTRA_FLYING, SoundCategory.AMBIENT, 0.04f, 0.5f);
                    }
                    case LEVEL_5 -> {
                        if (r.nextDouble() < 0.4) p.playSound(far, Sound.BLOCK_WOOD_STEP, SoundCategory.AMBIENT, 0.5f, 0.5f);
                        if (r.nextDouble() < 0.1) p.playSound(far, Sound.BLOCK_WOODEN_DOOR_CLOSE, SoundCategory.AMBIENT, 0.4f, 0.7f);
                    }
                }
                featureCue(p, e.getKey());
            }
        }
    }

    /** Transitions announce themselves with a recognisable sound when you are close: they are meant to be found. */
    private void featureCue(Player p, Level level) {
        var g = plugin.worlds().generator(level);
        for (var f : g.featuresNear(p.getLocation().getBlockX(), p.getLocation().getBlockZ(), 28)) {
            if (!f.isTransition()) continue;
            double[] c = g.featureCenter(f);
            Location l = new Location(p.getWorld(), c[0], c[1] + 1, c[2]);
            if (f.dest().isExit()) {
                p.playSound(l, Sound.ITEM_ELYTRA_FLYING, SoundCategory.AMBIENT, 0.25f, 1.6f);
            } else {
                p.playSound(l, Sound.BLOCK_BEACON_AMBIENT, SoundCategory.AMBIENT, 0.6f, 0.55f);
            }
        }
    }

    // ------------------------------------------------------------------ events

    private void eventPass() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Map.Entry<Level, World> e : plugin.worlds().worlds().entrySet()) {
            for (Player p : e.getValue().getPlayers()) {
                if (p.getGameMode() == GameMode.SPECTATOR || r.nextDouble() >= cfg().eventChance) continue;
                List<EventType> options = new ArrayList<>();
                for (EventType t : EventType.values()) if (t.levels.contains(e.getKey())) options.add(t);
                if (!options.isEmpty()) trigger(options.get(r.nextInt(options.size())), p);
            }
        }
    }

    public boolean trigger(EventType type, Player p) {
        Level level = plugin.worlds().levelOf(p.getWorld());
        if (level == null) return false;
        plugin.debug("event " + type.id + " for " + p.getName());
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location pl = p.getLocation();
        World w = p.getWorld();
        switch (type) {
            case FLICKER -> {
                flicker(pl, cfg().flickerRadius, 40 + r.nextInt(40));
                w.playSound(pl.clone().add(0, 3, 0), Sound.BLOCK_BEACON_AMBIENT, SoundCategory.AMBIENT, 0.5f, 1.9f);
            }
            case BLACKOUT -> blackout(pl, cfg().flickerRadius + 6, 120 + r.nextInt(80));
            case LIGHTS_OUT -> lightsOut(p);
            case FOOTSTEPS -> {
                for (int i = 0; i < 6; i++) {
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (!p.isOnline()) return;
                        Location behind = p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(-5));
                        p.playSound(behind, level == Level.POOLROOMS ? Sound.ENTITY_PLAYER_SPLASH : Sound.BLOCK_STONE_STEP, SoundCategory.AMBIENT, 0.5f, 0.8f);
                    }, i * 9L);
                }
            }
            case KNOCKING -> {
                Location wall = pl.clone().add(r.nextDouble(-4, 4), 1, r.nextDouble(-4, 4));
                for (int i = 0; i < 3; i++) {
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> p.playSound(wall, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, SoundCategory.AMBIENT, 0.35f, 0.7f), i * 8L);
                }
            }
            case MACHINERY -> {
                Location far = pl.clone().add(r.nextDouble(-30, 30), 0, r.nextDouble(-30, 30));
                p.playSound(far, Sound.BLOCK_ANVIL_LAND, SoundCategory.AMBIENT, 0.35f, 0.5f);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> p.playSound(far, Sound.BLOCK_PISTON_EXTEND, SoundCategory.AMBIENT, 0.6f, 0.5f), 15L);
            }
            case STEAM_BURST -> steamBurst(p);
            case POWER_SURGE -> powerSurge(p);
            case POOL_ECHO -> {
                Location far = pl.clone().add(r.nextDouble(-20, 20), 0, r.nextDouble(-20, 20));
                p.playSound(far, Sound.ENTITY_GENERIC_SPLASH, SoundCategory.AMBIENT, 0.6f, 0.6f);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> p.playSound(far, Sound.ENTITY_GENERIC_SPLASH, SoundCategory.AMBIENT, 0.3f, 0.55f), 10L);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> p.playSound(far, Sound.ENTITY_DOLPHIN_AMBIENT_WATER, SoundCategory.AMBIENT, 0.4f, 0.5f), 30L);
            }
            case PHONE_RING -> {
                Location far = pl.clone().add(r.nextDouble(-18, 18), 1, r.nextDouble(-18, 18));
                for (int k = 0; k < 6; k++) {
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> p.playSound(far, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.AMBIENT, 0.6f, 1.9f), (k / 2) * 30L + (k % 2) * 5L);
                }
            }
            case WALTZ -> playWaltz(pl.clone().add(r.nextDouble(-10, 10), 1, r.nextDouble(-10, 10)));
            case ELEVATOR_DING -> {
                Location far = pl.clone().add(r.nextDouble(-16, 16), 1, r.nextDouble(-16, 16));
                p.playSound(far, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.AMBIENT, 0.8f, 1.6f);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> p.playSound(far, Sound.BLOCK_IRON_DOOR_OPEN, SoundCategory.AMBIENT, 0.6f, 0.6f), 10L);
            }
            case DEJA_VU -> {
                p.playSound(pl, Sound.ENTITY_VEX_AMBIENT, SoundCategory.AMBIENT, 0.4f, 0.5f);
                p.sendActionBar(BackroomsConfig.mm("<gray><italic>You've been here before."));
                plugin.creatures().hallucinate(p);
            }
            case HUM_SHIFT -> {
                for (int i = 0; i < 8; i++) {
                    float pitch = 0.5f + i * 0.12f;
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> p.playSound(p.getLocation().add(0, 2, 0), Sound.BLOCK_BEACON_AMBIENT, SoundCategory.AMBIENT, 0.6f, pitch), i * 5L);
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ light effects (client-side only)

    private List<Block> lightsAround(Location c, int radius) {
        List<Block> out = new ArrayList<>();
        World w = c.getWorld();
        int bx = c.getBlockX(), by = c.getBlockY(), bz = c.getBlockZ();
        for (int x = bx - radius; x <= bx + radius; x++) {
            for (int z = bz - radius; z <= bz + radius; z++) {
                if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
                for (int y = by - 2; y <= by + 12; y++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (LIGHTS.contains(b.getType())) out.add(b);
                }
            }
        }
        return out;
    }

    private BlockData off(Block b, Level level) {
        BlockData d = b.getBlockData();
        Material t = b.getType();
        if (t == Material.LANTERN && d instanceof Lantern l) {
            Lantern s = (Lantern) Material.SOUL_LANTERN.createBlockData();
            s.setHanging(l.isHanging());
            return s;
        }
        if (t == Material.LIGHT) return Material.AIR.createBlockData();
        if (d instanceof Lightable lit) {
            Lightable c = (Lightable) lit.clone();
            c.setLit(false);
            return c;
        }
        return OFF.getOrDefault(level, Material.GRAY_CONCRETE).createBlockData();
    }

    private static List<Player> viewers(Location c, double r) {
        List<Player> out = new ArrayList<>();
        for (Player p : c.getWorld().getPlayers()) if (p.getLocation().distanceSquared(c) <= r * r) out.add(p);
        return out;
    }

    private void send(List<Player> viewers, Block b, BlockData data) {
        for (Player p : viewers) if (p.isOnline()) p.sendBlockChange(b.getLocation(), data);
    }

    private void restore(List<Block> lights, Location c) {
        List<Player> v = viewers(c, 64);
        for (Block b : lights) send(v, b, b.getBlockData());
    }

    /** Lights stutter randomly for {@code duration} ticks, then restore. */
    public void flicker(Location c, int radius, int duration) {
        Level level = plugin.worlds().levelOf(c.getWorld());
        if (level == null) return;
        List<Block> lights = lightsAround(c, radius);
        if (lights.isEmpty()) return;
        List<Player> v = viewers(c, 48);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int t = 0; t < duration; t += 3) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                for (Block b : lights) {
                    if (r.nextDouble() < 0.35) send(v, b, r.nextBoolean() ? off(b, level) : b.getBlockData());
                }
            }, t);
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> restore(lights, c), duration + 2L);
    }

    public void blackout(Location c, int radius, int duration) {
        Level level = plugin.worlds().levelOf(c.getWorld());
        if (level == null) return;
        List<Block> lights = lightsAround(c, radius);
        List<Player> v = viewers(c, 48);
        c.getWorld().playSound(c, Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.AMBIENT, 1f, 0.5f);
        for (Block b : lights) send(v, b, off(b, level));
        for (Player p : v) p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, Math.min(duration, 80), 0, false, false));
        blackouts.add(new Blackout(c.getWorld(), c.clone(), radius + 8, tick + duration));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            restore(lights, c);
            c.getWorld().playSound(c, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.AMBIENT, 0.6f, 1.4f);
        }, duration);
    }

    private void lightsOut(Player p) {
        Level level = plugin.worlds().levelOf(p.getWorld());
        Location c = p.getLocation();
        List<Block> lights = lightsAround(c, 22);
        lights.sort(Comparator.comparingDouble((Block b) -> b.getLocation().distanceSquared(c)).reversed());
        List<Player> v = viewers(c, 48);
        int n = lights.size();
        for (int i = 0; i < n; i++) {
            Block b = lights.get(i);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                send(v, b, off(b, level));
                if (p.isOnline()) p.playSound(b.getLocation(), Sound.BLOCK_LEVER_CLICK, SoundCategory.AMBIENT, 0.6f, 0.5f);
            }, 2L + (long) i * 50 / Math.max(1, n));
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> restore(lights, c), 140L);
    }

    private void steamBurst(Player p) {
        Location at = p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(3)).add(0, 1, 0);
        World w = p.getWorld();
        w.playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 1f, 0.6f);
        w.spawnParticle(Particle.CLOUD, at, 8, 0.2, 0.2, 0.2, 0.01);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            w.spawnParticle(Particle.CLOUD, at, 70, 0.8, 0.8, 0.8, 0.06);
            w.playSound(at, Sound.BLOCK_LAVA_EXTINGUISH, SoundCategory.BLOCKS, 1f, 0.5f);
            for (Player o : w.getPlayers()) {
                if ((o.getGameMode() == GameMode.SURVIVAL || o.getGameMode() == GameMode.ADVENTURE) && o.getLocation().distanceSquared(at) < 2.2 * 2.2) {
                    plugin.sessions().noteHazard(o, "steam");
                    o.damage(3);
                }
            }
        }, 30L);
    }

    private void powerSurge(Player p) {
        Location c = p.getLocation();
        World w = p.getWorld();
        w.playSound(c, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.BLOCKS, 1f, 0.5f);
        flicker(c, 18, 60);
        for (Block b : lightsAround(c, 12)) {
            if (ThreadLocalRandom.current().nextDouble() < 0.3) w.spawnParticle(Particle.ELECTRIC_SPARK, b.getLocation().add(0.5, -0.2, 0.5), 10, 0.2, 0.1, 0.2, 0.2);
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (Player o : w.getPlayers()) {
                if ((o.getGameMode() == GameMode.SURVIVAL || o.getGameMode() == GameMode.ADVENTURE) && o.getLocation().distanceSquared(c) < 16 * 16 && o.isInWater()) {
                    plugin.sessions().noteHazard(o, "shock");
                    o.damage(2);
                    w.spawnParticle(Particle.ELECTRIC_SPARK, o.getLocation(), 30, 0.4, 0.3, 0.4, 0.3);
                }
            }
            if (ThreadLocalRandom.current().nextDouble() < 0.35 && p.isOnline()) plugin.creatures().spawnNatural(CreatureType.SPARK, p);
        }, 40L);
    }

    /** A slow, slightly detuned waltz on harp notes, positioned in the world. */
    public void playWaltz(Location at) {
        float[] melody = {0.749f, 0.943f, 1.122f, 1.0f, 0.943f, 0.749f, 0.707f, 0.841f, 1.0f, 0.943f, 0.841f, 0.707f, 0.667f, 0.749f, 0.943f, 0.749f};
        float[] bass = {0.5f, 0.0f, 0.0f, 0.471f, 0.0f, 0.0f, 0.445f, 0.0f, 0.0f, 0.5f, 0.0f, 0.0f, 0.42f, 0.0f, 0.0f, 0.5f};
        World w = at.getWorld();
        for (int i = 0; i < melody.length; i++) {
            float m = melody[i] * 0.985f, b = bass[i];
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                w.playSound(at, Sound.BLOCK_NOTE_BLOCK_HARP, SoundCategory.RECORDS, 0.7f, m);
                if (b > 0) w.playSound(at, Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.RECORDS, 0.6f, b * 2);
            }, i * 9L);
        }
    }

    public void shutdown() {
        stop();
        Bukkit.getScheduler().cancelTasks(plugin);
    }
}
