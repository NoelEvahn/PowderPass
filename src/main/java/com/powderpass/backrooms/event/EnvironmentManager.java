package com.powderpass.backrooms.event;

import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.config.BackroomsConfig;
import com.powderpass.backrooms.gen.Feature;
import com.powderpass.backrooms.gen.LevelGenerator;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ambience and environmental events. Every light in the Backrooms is an invisible light block under a display
 * fixture; light events switch the light blocks off client-side ({@code sendBlockChange}) and dim the fixtures,
 * so the world is never modified and every effect restores itself, even across a crash.
 */
public final class EnvironmentManager {
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
        if (cfg().eventsEnabled && cfg().lightEventChance > 0) {
            tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, this::lightPass, 400L, 400L));
        }
    }

    /**
     * The lights misbehave anywhere, any time: every 20 s each occupied level has a chance of a flicker, a blackout or
     * the lights going out one by one around one of its players.
     */
    private void lightPass() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Map.Entry<Level, World> e : plugin.worlds().worlds().entrySet()) {
            List<Player> ps = new ArrayList<>();
            for (Player p : e.getValue().getPlayers()) if (p.getGameMode() != GameMode.SPECTATOR) ps.add(p);
            if (ps.isEmpty() || r.nextDouble() >= cfg().lightEventChance) continue;
            Player p = ps.get(r.nextInt(ps.size()));
            if (blackoutNear(p.getLocation())) continue;
            double roll = r.nextDouble();
            trigger(roll < 0.62 ? EventType.FLICKER : roll < 0.9 ? EventType.BLACKOUT : EventType.LIGHTS_OUT, p);
        }
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
                if (plugin.audio().custom(p) && cfg().audioAmbient) {
                    // the custom soundscape replaces this vanilla ambience for players who loaded the pack
                    featureCue(p, e.getKey());
                    continue;
                }
                Location near = p.getLocation().add(r.nextDouble(-6, 6), 2, r.nextDouble(-6, 6));
                Location far = p.getLocation().add(r.nextDouble(-24, 24), 0, r.nextDouble(-24, 24));
                switch (e.getKey()) {
                    case LEVEL_0 -> {
                        p.playSound(near, Sound.BLOCK_BEACON_AMBIENT, SoundCategory.AMBIENT, 0.25f, 1.35f + r.nextFloat() * 0.15f);
                        if (r.nextDouble() < 0.12) p.playSound(far, Sound.BLOCK_WOOL_STEP, SoundCategory.AMBIENT, 0.5f, 0.6f);
                    }
                    case LEVEL_1 -> {
                        p.playSound(near, Sound.BLOCK_BEACON_AMBIENT, SoundCategory.AMBIENT, 0.15f, 0.8f);
                        if (r.nextDouble() < 0.25) p.playSound(far, Sound.BLOCK_IRON_DOOR_CLOSE, SoundCategory.AMBIENT, 0.3f, 0.5f);
                    }
                    case POOLROOMS -> {
                        p.playSound(near, Sound.BLOCK_POINTED_DRIPSTONE_DRIP_WATER, SoundCategory.AMBIENT, 0.6f, 0.9f);
                        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                                p.playSound(far, Sound.BLOCK_POINTED_DRIPSTONE_DRIP_WATER, SoundCategory.AMBIENT, 0.3f, 0.85f), 7L);
                        if (p.isInWater()) p.playSound(p.getLocation(), Sound.AMBIENT_UNDERWATER_LOOP_ADDITIONS, SoundCategory.AMBIENT, 0.7f, 0.8f);
                    }
                    case LEVEL_4 -> {
                        if (r.nextDouble() < 0.4) {
                            for (int i = 0; i < 6; i++) {
                                plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                                        p.playSound(far, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, SoundCategory.AMBIENT, 0.25f, 1.8f + (float) Math.random() * 0.2f), i * 3L + r.nextInt(3));
                            }
                        }
                        p.playSound(near, Sound.ITEM_ELYTRA_FLYING, SoundCategory.AMBIENT, 0.04f, 0.5f);
                    }
                    case PLAINS -> {
                        p.playSound(p.getLocation(), Sound.ITEM_ELYTRA_FLYING, SoundCategory.AMBIENT, 0.08f, 0.45f);
                        if (r.nextDouble() < 0.06) p.playSound(far.clone().add(r.nextDouble(-60, 60), 0, r.nextDouble(-60, 60)), Sound.ENTITY_WOLF_AMBIENT, SoundCategory.AMBIENT, 0.25f, 0.6f);
                    }
                }
                featureCue(p, e.getKey());
            }
        }
    }

    /** Transitions announce themselves with a recognisable sound when you are close: they are meant to be found. */
    private void featureCue(Player p, Level level) {
        LevelGenerator g = plugin.worlds().generator(level);
        // Level 0's one way on is meant to be hard to find: it only gives itself away up close
        int radius = level == Level.LEVEL_0 ? 10 : 28;
        for (Feature f : g.featuresNear(p.getLocation().getBlockX(), p.getLocation().getBlockZ(), radius)) {
            if (!f.isTransition()) continue;
            double[] c = g.featureCenter(f);
            Location l = new Location(p.getWorld(), c[0], c[1] + 1, c[2]);
            p.playSound(l, f.dest().isExit() ? Sound.ITEM_ELYTRA_FLYING : Sound.BLOCK_BEACON_AMBIENT, SoundCategory.AMBIENT,
                    f.dest().isExit() ? 0.25f : 0.6f, f.dest().isExit() ? 1.6f : 0.55f);
        }
    }

    // ------------------------------------------------------------------ events

    private static final java.util.Set<EventType> SOUND_ONLY = java.util.EnumSet.of(EventType.FOOTSTEPS, EventType.KNOCKING,
            EventType.MACHINERY, EventType.POOL_ECHO, EventType.PHONE_RING, EventType.HUM_SHIFT, EventType.WIND);

    private void eventPass() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Map.Entry<Level, World> e : plugin.worlds().worlds().entrySet()) {
            for (Player p : e.getValue().getPlayers()) {
                if (p.getGameMode() == GameMode.SPECTATOR || r.nextDouble() >= cfg().eventChance) continue;
                List<EventType> options = new ArrayList<>();
                boolean custom = plugin.audio().custom(p);
                for (EventType t : EventType.values()) {
                    if (!t.levels.contains(e.getKey())) continue;
                    // sound-only events are covered by the positional soundscape for players with the pack
                    if (custom && SOUND_ONLY.contains(t)) continue;
                    options.add(t);
                }
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
                Sound step = level == Level.POOLROOMS ? Sound.ENTITY_PLAYER_SPLASH : level == Level.PLAINS ? Sound.BLOCK_GRASS_STEP
                        : level == Level.LEVEL_1 ? Sound.BLOCK_STONE_STEP : Sound.BLOCK_WOOL_STEP;
                for (int i = 0; i < 6; i++) {
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (!p.isOnline()) return;
                        Location behind = p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(-5));
                        p.playSound(behind, step, SoundCategory.AMBIENT, 0.6f, 0.8f);
                    }, i * 9L);
                }
            }
            case KNOCKING -> {
                Location wall = pl.clone().add(r.nextDouble(-4, 4), 1, r.nextDouble(-4, 4));
                for (int i = 0; i < 3; i++) {
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> p.playSound(wall, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, SoundCategory.AMBIENT, 0.35f, 0.7f), i * 8L);
                }
            }
            case DOOR_BEHIND -> {
                if (!doorBehind(p)) p.playSound(pl.clone().subtract(pl.getDirection().multiply(8)), Sound.BLOCK_WOODEN_DOOR_OPEN, SoundCategory.AMBIENT, 0.5f, 0.8f);
            }
            case MACHINERY -> {
                Location far = pl.clone().add(r.nextDouble(-30, 30), 0, r.nextDouble(-30, 30));
                p.playSound(far, Sound.BLOCK_ANVIL_LAND, SoundCategory.AMBIENT, 0.35f, 0.5f);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> p.playSound(far, Sound.BLOCK_PISTON_EXTEND, SoundCategory.AMBIENT, 0.6f, 0.5f), 15L);
            }
            case POOL_ECHO -> {
                Location far = pl.clone().add(r.nextDouble(-20, 20), 0, r.nextDouble(-20, 20));
                p.playSound(far, Sound.ENTITY_GENERIC_SPLASH, SoundCategory.AMBIENT, 0.6f, 0.6f);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> p.playSound(far, Sound.ENTITY_GENERIC_SPLASH, SoundCategory.AMBIENT, 0.3f, 0.55f), 10L);
            }
            case PHONE_RING -> {
                Location far = pl.clone().add(r.nextDouble(-18, 18), 1, r.nextDouble(-18, 18));
                for (int k = 0; k < 6; k++) {
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> p.playSound(far, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.AMBIENT, 0.6f, 1.9f), (k / 2) * 30L + (k % 2) * 5L);
                }
            }
            case FOG -> {
                if (!plugin.fog().bank(p, 60 + java.util.concurrent.ThreadLocalRandom.current().nextInt(60))) return false;
                p.playSound(pl, Sound.ITEM_ELYTRA_FLYING, SoundCategory.AMBIENT, 0.25f, 0.3f);
            }
            case WIND -> {
                p.playSound(pl, Sound.ITEM_ELYTRA_FLYING, SoundCategory.AMBIENT, 0.5f, 0.4f);
                w.spawnParticle(Particle.WHITE_ASH, pl.clone().add(0, 1.5, 0), 40, 8, 2, 8, 0.02);
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

    /** Opens the nearest closed wooden door behind the player for a few seconds (the block's own open state). */
    private boolean doorBehind(Player p) {
        Location c = p.getLocation();
        Vector look = c.getDirection().setY(0).normalize();
        Block best = null;
        double bd = Double.MAX_VALUE;
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                for (int y = -2; y <= 3; y++) {
                    Block b = c.getWorld().getBlockAt(c.getBlockX() + x, c.getBlockY() + y, c.getBlockZ() + z);
                    if (!(b.getBlockData() instanceof Door d) || d.getHalf() != org.bukkit.block.data.Bisected.Half.BOTTOM || d.isOpen()) continue;
                    if (b.getType() == Material.IRON_DOOR) continue;
                    Vector to = b.getLocation().toVector().subtract(c.toVector()).setY(0);
                    if (to.lengthSquared() < 9 || to.normalize().dot(look) > -0.3) continue;
                    double dd = b.getLocation().distanceSquared(c);
                    if (dd < bd) {
                        bd = dd;
                        best = b;
                    }
                }
            }
        }
        if (best == null) return false;
        Block door = best;
        Door d = (Door) door.getBlockData();
        d.setOpen(true);
        door.setBlockData(d, false);
        door.getWorld().playSound(door.getLocation(), Sound.BLOCK_WOODEN_DOOR_OPEN, SoundCategory.BLOCKS, 0.8f, 0.7f);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (door.getBlockData() instanceof Door now && now.isOpen()) {
                now.setOpen(false);
                door.setBlockData(now, false);
                door.getWorld().playSound(door.getLocation(), Sound.BLOCK_WOODEN_DOOR_CLOSE, SoundCategory.BLOCKS, 0.8f, 0.7f);
            }
        }, 60L);
        return true;
    }

    // ------------------------------------------------------------------ light effects (client-side + fixture displays)

    private List<Block> lightsAround(Location c, int radius) {
        List<Block> out = new ArrayList<>();
        World w = c.getWorld();
        int bx = c.getBlockX(), by = c.getBlockY(), bz = c.getBlockZ();
        for (int x = bx - radius; x <= bx + radius; x++) {
            for (int z = bz - radius; z <= bz + radius; z++) {
                if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
                for (int y = by - 2; y <= by + 14; y++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (b.getType() == Material.LIGHT) out.add(b);
                }
            }
        }
        return out;
    }

    private static BlockData off(Block b) {
        if (b.getBlockData() instanceof org.bukkit.block.data.Waterlogged wl && wl.isWaterlogged()) return Material.WATER.createBlockData();
        return Material.AIR.createBlockData();
    }

    private static List<Player> viewers(Location c, double r) {
        List<Player> out = new ArrayList<>();
        for (Player p : c.getWorld().getPlayers()) if (p.getLocation().distanceSquared(c) <= r * r) out.add(p);
        return out;
    }

    private static void send(List<Player> viewers, Block b, BlockData data) {
        for (Player p : viewers) if (p.isOnline()) p.sendBlockChange(b.getLocation(), data);
    }

    /**
     * Switch one light (client-side only) off or back on. A lit ceiling panel block right above the invisible light
     * goes dark with it, so block-built fixtures flicker exactly like display ones.
     */
    private static void lamp(List<Player> viewers, Block light, boolean on) {
        send(viewers, light, on ? light.getBlockData() : off(light));
        Block up = light.getRelative(org.bukkit.block.BlockFace.UP);
        com.powderpass.backrooms.gen.CustomBlocks.Def d = com.powderpass.backrooms.gen.CustomBlocks.of(up.getBlockData());
        if (d != null && d.id().startsWith("light_panel") && !d.id().equals("light_panel_off")) {
            send(viewers, up, on ? up.getBlockData() : com.powderpass.backrooms.gen.CustomBlocks.custom("light_panel_off").data());
        }
    }

    private static void restore(List<Block> lights, Location c) {
        List<Player> v = viewers(c, 64);
        for (Block b : lights) lamp(v, b, true);
    }

    /** Lights stutter randomly for {@code duration} ticks, then restore. */
    public void flicker(Location c, int radius, int duration) {
        if (plugin.worlds().levelOf(c.getWorld()) == null) return;
        plugin.decorations().dimLamps(c, radius, duration, true);
        plugin.audio().onFlicker(c);
        List<Block> lights = lightsAround(c, radius);
        if (lights.isEmpty()) return;
        List<Player> v = viewers(c, 48);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int t = 0; t < duration; t += 3) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                for (Block b : lights) {
                    if (r.nextDouble() < 0.35) lamp(v, b, r.nextBoolean());
                }
            }, t);
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> restore(lights, c), duration + 2L);
    }

    public void blackout(Location c, int radius, int duration) {
        if (plugin.worlds().levelOf(c.getWorld()) == null) return;
        List<Block> lights = lightsAround(c, radius);
        List<Player> v = viewers(c, 48);
        c.getWorld().playSound(c, Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.AMBIENT, 1f, 0.5f);
        plugin.audio().onBlackout(c, duration);
        plugin.decorations().dimLamps(c, radius + 4, duration, false);
        for (Block b : lights) lamp(v, b, false);
        for (Player p : v) p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, Math.min(duration, 80), 0, false, false));
        blackouts.add(new Blackout(c.getWorld(), c.clone(), radius + 8, tick + duration));
        // in the garage the dark is never empty: Smilers are there, all around, until the lights come back
        if (plugin.worlds().levelOf(c.getWorld()) == Level.LEVEL_1 && cfg().blackoutSmilers > 0) {
            for (Player p : v) {
                if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) {
                    plugin.creatures().smilersInTheDark(p, cfg().blackoutSmilers);
                }
            }
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            restore(lights, c);
            c.getWorld().playSound(c, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.AMBIENT, 0.6f, 1.4f);
        }, duration);
    }

    private void lightsOut(Player p) {
        Location c = p.getLocation();
        List<Block> lights = lightsAround(c, 22);
        lights.sort(Comparator.comparingDouble((Block b) -> b.getLocation().distanceSquared(c)).reversed());
        List<Player> v = viewers(c, 48);
        int n = lights.size();
        for (int i = 0; i < n; i++) {
            Block b = lights.get(i);
            long at = 2L + (long) i * 50 / Math.max(1, n);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                lamp(v, b, false);
                plugin.decorations().dimLamps(b.getLocation(), 2.6, (int) (140 - at), false);
                if (p.isOnline()) p.playSound(b.getLocation(), Sound.BLOCK_LEVER_CLICK, SoundCategory.AMBIENT, 0.6f, 0.5f);
            }, at);
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> restore(lights, c), 140L);
    }
}
