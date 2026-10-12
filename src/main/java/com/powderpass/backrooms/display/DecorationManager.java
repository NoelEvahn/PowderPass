package com.powderpass.backrooms.display;

import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.config.BackroomsConfig;
import com.powderpass.backrooms.gen.DecorSpec;
import com.powderpass.backrooms.gen.LevelGenerator;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Distance-activated display decorations. Decorations are a pure function of the layout, so they are never
 * saved: chunks near players get their props spawned (spread over ticks, within per-chunk and per-world entity
 * budgets), chunks that fall out of range or unload get them removed. All entities are non-persistent and
 * tagged, and any tagged entity found on chunk load (e.g. after a crash) is swept.
 */
public final class DecorationManager implements Listener {
    private final BackroomsPlugin plugin;
    private final Map<UUID, WorldState> worlds = new HashMap<>();
    private final Map<UUID, Instance> interactions = new HashMap<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private long tick;
    private long lastCostNanos;
    private final Map<UUID, Long> actionCooldown = new HashMap<>();

    private static final class WorldState {
        final World world;
        final Level level;
        final LevelGenerator gen;
        final Map<Long, List<Instance>> active = new HashMap<>();
        final ArrayDeque<Long> queue = new ArrayDeque<>();
        final Set<Long> queued = new HashSet<>();
        int entities;

        WorldState(World world, Level level, LevelGenerator gen) {
            this.world = world;
            this.level = level;
            this.gen = gen;
        }
    }

    public static final class Instance {
        final DecorSpec spec;
        final Model model;
        final Location anchor;
        final Matrix4f base;
        final Entity[] ents;
        long hazardAt = -1;
        boolean telegraphed;
        boolean open;
        long nextBehavior;

        Instance(DecorSpec spec, Model model, Location anchor, Matrix4f base) {
            this.spec = spec;
            this.model = model;
            this.anchor = anchor;
            this.base = base;
            this.ents = new Entity[model.parts.size()];
        }

        public String id() {
            return spec.model;
        }

        public Location anchor() {
            return anchor;
        }
    }

    public DecorationManager(BackroomsPlugin plugin) {
        this.plugin = plugin;
    }

    private BackroomsConfig cfg() {
        return plugin.cfg();
    }

    public void start() {
        stop();
        worlds.clear();
        for (Map.Entry<Level, World> e : plugin.worlds().worlds().entrySet()) {
            worlds.put(e.getValue().getUID(), new WorldState(e.getValue(), e.getKey(), plugin.worlds().generator(e.getKey())));
        }
        if (!cfg().displaysEnabled) {
            return;
        }
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, this::activationPass, 20L, 10L));
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, this::spawnPass, 21L, 1L));
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, this::animationPass, 22L, cfg().animationInterval));
    }

    public void stop() {
        for (BukkitTask t : tasks) t.cancel();
        tasks.clear();
        for (WorldState ws : worlds.values()) {
            for (List<Instance> list : ws.active.values()) for (Instance i : list) remove(i);
            ws.active.clear();
            ws.queue.clear();
            ws.queued.clear();
            ws.entities = 0;
        }
        interactions.clear();
    }

    // ------------------------------------------------------------------ activation

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    private void activationPass() {
        for (WorldState ws : worlds.values()) {
            int r = ws.level.outdoor() ? cfg().outdoorActivationRadius : cfg().activationRadius;
            Set<Long> desired = new HashSet<>();
            Set<Long> keep = new HashSet<>();
            for (Player p : ws.world.getPlayers()) {
                int pcx = p.getLocation().getBlockX() >> 4, pcz = p.getLocation().getBlockZ() >> 4;
                for (int dx = -r - 1; dx <= r + 1; dx++) {
                    for (int dz = -r - 1; dz <= r + 1; dz++) {
                        long k = key(pcx + dx, pcz + dz);
                        keep.add(k);
                        if (Math.abs(dx) <= r && Math.abs(dz) <= r) desired.add(k);
                    }
                }
            }
            for (long k : desired) {
                if (!ws.active.containsKey(k) && !ws.queued.contains(k)) {
                    int cx = (int) (k >> 32), cz = (int) k;
                    if (ws.world.isChunkLoaded(cx, cz)) {
                        ws.queue.add(k);
                        ws.queued.add(k);
                    }
                }
            }
            Iterator<Map.Entry<Long, List<Instance>>> it = ws.active.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Long, List<Instance>> e = it.next();
                if (!keep.contains(e.getKey())) {
                    despawnList(ws, e.getValue());
                    it.remove();
                }
            }
        }
    }

    private void spawnPass() {
        long start = System.nanoTime();
        int budget = cfg().chunksPerTick;
        for (WorldState ws : worlds.values()) {
            while (budget > 0 && !ws.queue.isEmpty()) {
                long k = ws.queue.poll();
                ws.queued.remove(k);
                int cx = (int) (k >> 32), cz = (int) k;
                if (!ws.world.isChunkLoaded(cx, cz) || ws.active.containsKey(k)) continue;
                ws.active.put(k, spawnChunk(ws, cx, cz));
                budget--;
            }
        }
        lastCostNanos = (lastCostNanos * 7 + (System.nanoTime() - start)) / 8;
    }

    private List<Instance> spawnChunk(WorldState ws, int cx, int cz) {
        List<Instance> out = new ArrayList<>();
        int used = 0;
        float view = cfg().viewRange;
        for (DecorSpec spec : ws.gen.decorations(cx, cz)) {
            Model model;
            try {
                model = ModelLibrary.build(spec.model, spec.seed);
            } catch (RuntimeException ex) {
                plugin.debug("model " + spec.model + " failed: " + ex);
                continue;
            }
            if (model == null) {
                plugin.debug("unknown decoration model " + spec.model);
                continue;
            }
            int n = model.entityCount();
            if (used + n > cfg().maxPerChunk || ws.entities + n > cfg().maxPerWorld) continue;
            Location anchor = new Location(ws.world, spec.x, spec.y, spec.z, 0, 0);
            float range = model.viewRange > 0 ? model.viewRange : view;
            Matrix4f base = new Matrix4f()
                    .rotateY((float) Math.toRadians(-spec.yaw))
                    .rotateX((float) Math.toRadians(spec.pitch))
                    .rotateZ((float) Math.toRadians(spec.roll));
            Instance inst = new Instance(spec, model, anchor, base);
            for (int i = 0; i < model.parts.size(); i++) {
                Part p = model.parts.get(i);
                Matrix4f m = new Matrix4f(base).mul(p.local);
                inst.ents[i] = DisplayFactory.spawn(ws.world, anchor, p, m, range, "decor", spec.key());
                if (p.kind == Part.Kind.INTERACTION) interactions.put(inst.ents[i].getUniqueId(), inst);
            }
            used += n;
            ws.entities += n;
            out.add(inst);
        }
        return out;
    }

    private void despawnList(WorldState ws, List<Instance> list) {
        for (Instance i : list) {
            ws.entities -= remove(i);
        }
    }

    private int remove(Instance i) {
        int n = 0;
        for (Entity e : i.ents) {
            if (e != null) {
                interactions.remove(e.getUniqueId());
                if (e.isValid()) e.remove();
                n++;
            }
        }
        return n;
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        WorldState ws = worlds.get(e.getWorld().getUID());
        if (ws == null) return;
        long k = key(e.getChunk().getX(), e.getChunk().getZ());
        List<Instance> list = ws.active.remove(k);
        if (list != null) despawnList(ws, list);
        ws.queued.remove(k);
    }

    /** Sweep tagged leftovers (should not exist since all are non-persistent, but crashes happen). */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        if (!worlds.containsKey(e.getWorld().getUID())) return;
        for (Entity en : e.getEntities()) {
            var pdc = en.getPersistentDataContainer();
            if (pdc.has(Keys.DECOR, PersistentDataType.STRING) || pdc.has(Keys.CREATURE, PersistentDataType.STRING)) {
                en.remove();
            }
        }
    }

    // ------------------------------------------------------------------ animation, emitters, hazards

    private void animationPass() {
        long start = System.nanoTime();
        tick += cfg().animationInterval;
        int interval = cfg().animationInterval;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (WorldState ws : worlds.values()) {
            List<Player> players = ws.world.getPlayers();
            if (players.isEmpty()) continue;
            for (List<Instance> list : ws.active.values()) {
                for (Instance inst : list) {
                    double nearest = nearestSq(players, inst.anchor);
                    if (nearest > 48 * 48) continue;
                    Model m = inst.model;
                    if (m.behavior != null) behavior(ws, inst, players, nearest);
                    if (!m.anims.isEmpty() && nearest < 40 * 40) animate(inst, interval, rnd);
                    if (!m.emitters.isEmpty() && nearest < 24 * 24) emit(ws.world, inst, rnd);
                    if (m.hazard != null && cfg().hazards) hazard(ws, inst, players, rnd);
                }
            }
        }
        lastCostNanos = (lastCostNanos * 7 + (System.nanoTime() - start)) / 8;
    }

    private static double nearestSq(List<Player> players, Location l) {
        double best = Double.MAX_VALUE;
        for (Player p : players) {
            Location pl = p.getLocation();
            double dx = pl.getX() - l.getX(), dy = pl.getY() - l.getY(), dz = pl.getZ() - l.getZ();
            best = Math.min(best, dx * dx + dy * dy * 4 + dz * dz);
        }
        return best;
    }

    private void animate(Instance inst, int interval, ThreadLocalRandom rnd) {
        Model m = inst.model;
        Map<Integer, Matrix4f> poses = new HashMap<>();
        for (Anim a : m.anims) {
            if (a.transforms()) poses.merge(a.group(), a.pose(tick), (x, y) -> x.mul(y));
        }
        for (int i = 0; i < m.parts.size(); i++) {
            Part p = m.parts.get(i);
            Entity e = inst.ents[i];
            if (!(e instanceof Display d) || !e.isValid()) continue;
            Matrix4f pose = poses.get(p.group);
            if (pose != null) {
                Matrix4f full = new Matrix4f(inst.base);
                if (pose != null) full.mul(pose);
                full.mul(p.local);
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(interval);
                d.setTransformationMatrix(full);
            }
            for (Anim a : m.anims) {
                if (a.group() != p.group) continue;
                if (a.type() == Anim.Type.BLINK && p.alt != null && d instanceof BlockDisplay bd && rnd.nextDouble() < a.amp()) {
                    bd.setBlock(bd.getBlock().equals(p.block) ? p.alt : p.block);
                } else if (a.type() == Anim.Type.FLICKER) {
                    boolean dim = rnd.nextDouble() < a.amp();
                    if (p.brightness != null) d.setBrightness(new Display.Brightness(dim ? 2 : p.brightness, 15));
                    if (p.alt != null && d instanceof BlockDisplay bd) bd.setBlock(dim ? p.alt : p.block);
                }
            }
        }
    }

    private Location at(Instance inst, float ox, float oy, float oz) {
        Vector3f v = inst.base.transformPosition(new Vector3f(ox, oy, oz));
        return inst.anchor.clone().add(v.x, v.y, v.z);
    }

    private void emit(World w, Instance inst, ThreadLocalRandom rnd) {
        for (Emitter em : inst.model.emitters) {
            if (tick % Math.max(1, em.interval()) >= cfg().animationInterval && em.interval() > cfg().animationInterval) continue;
            if (rnd.nextDouble() >= em.chance()) continue;
            Location l = at(inst, em.ox(), em.oy(), em.oz());
            if (em.particle() != null) {
                w.spawnParticle(em.particle(), l, em.count(), em.spread(), em.spread() * 0.5, em.spread(), em.speed());
            }
            if (em.sound() != null) {
                w.playSound(l, em.sound(), SoundCategory.AMBIENT, em.volume(), em.pitch() * (0.95f + rnd.nextFloat() * 0.1f));
            }
        }
    }

    private void hazard(WorldState ws, Instance inst, List<Player> players, ThreadLocalRandom rnd) {
        Hazard hz = inst.model.hazard;
        Location c = at(inst, hz.ox(), hz.oy(), hz.oz());
        if (inst.hazardAt < 0) inst.hazardAt = tick + rnd.nextInt(Math.max(1, hz.period()));
        if (hz.telegraph() > 0 && !inst.telegraphed && tick >= inst.hazardAt - hz.telegraph()) {
            inst.telegraphed = true;
            if (hz.type().equals("steam")) {
                ws.world.playSound(c, Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.8f, 0.6f);
                ws.world.spawnParticle(Particle.CLOUD, c, 6, 0.2, 0.1, 0.2, 0.01);
            } else {
                ws.world.playSound(c, Sound.BLOCK_BEACON_AMBIENT, SoundCategory.BLOCKS, 1.2f, 2.0f);
                ws.world.spawnParticle(Particle.ELECTRIC_SPARK, c, 20, 0.5, 0.5, 0.5, 0.3);
            }
        }
        if (tick < inst.hazardAt) return;
        inst.hazardAt = tick + hz.period() + rnd.nextInt(Math.max(1, hz.period() / 2));
        inst.telegraphed = false;
        switch (hz.type()) {
            case "steam" -> {
                ws.world.spawnParticle(Particle.CLOUD, c.clone().add(0, 0.6, 0), 60, 0.5, 1.0, 0.5, 0.06);
                ws.world.playSound(c, Sound.BLOCK_LAVA_EXTINGUISH, SoundCategory.BLOCKS, 1.0f, 0.5f);
            }
            case "shock" -> {
                ws.world.spawnParticle(Particle.ELECTRIC_SPARK, c, 80, 1.0, 1.0, 1.0, 0.6);
                ws.world.playSound(c, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.BLOCKS, 0.5f, 1.8f);
            }
            default -> {
            }
        }
        for (Player p : players) {
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) continue;
            if (p.getLocation().distanceSquared(c) <= hz.radius() * hz.radius()) {
                plugin.sessions().noteHazard(p, hz.type());
                p.damage(hz.damage());
                if (hz.type().equals("shock")) {
                    p.setVelocity(p.getLocation().toVector().subtract(c.toVector()).setY(0).normalize().multiply(0.6).setY(0.3));
                }
            }
        }
    }

    // ------------------------------------------------------------------ scripted behaviours

    private void behavior(WorldState ws, Instance inst, List<Player> players, double nearestSq) {
        Model m = inst.model;
        switch (m.behavior) {
            case "elevator" -> {
                boolean inside = false, near = false;
                Matrix4f inv = new Matrix4f(inst.base).invert();
                for (Player p : players) {
                    Vector3f local = inv.transformPosition(new Vector3f(
                            (float) (p.getLocation().getX() - inst.anchor.getX()), (float) (p.getLocation().getY() - inst.anchor.getY()),
                            (float) (p.getLocation().getZ() - inst.anchor.getZ())));
                    if (Math.abs(local.y) > 2.5) continue;
                    if (local.z < -0.2 && local.z > -3.2 && Math.abs(local.x) < 1.8) inside = true;
                    else if (local.lengthSquared() < 3.5 * 3.5) near = true;
                }
                boolean open = near && !inside;
                if (open != inst.open) {
                    inst.open = open;
                    double half = m.parts.get(0).local.getScale(new Vector3f()).x;
                    for (int i = 0; i < m.parts.size(); i++) {
                        Part p = m.parts.get(i);
                        if (!(inst.ents[i] instanceof Display d) || p.group == 0) continue;
                        float dx = open ? (float) (half * 0.92) * (p.group == 1 ? -1 : 1) : 0;
                        d.setInterpolationDelay(0);
                        d.setInterpolationDuration(14);
                        d.setTransformationMatrix(new Matrix4f(inst.base).translate(dx, 0, 0).mul(p.local));
                    }
                    ws.world.playSound(inst.anchor, open ? Sound.BLOCK_IRON_DOOR_OPEN : Sound.BLOCK_IRON_DOOR_CLOSE, SoundCategory.BLOCKS, 0.7f, 0.7f);
                    if (open) ws.world.playSound(inst.anchor, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.BLOCKS, 0.6f, 1.6f);
                }
            }
            case "monitor" -> {
                if (tick < inst.nextBehavior || nearestSq > 20 * 20) return;
                inst.nextBehavior = tick + 60 + ThreadLocalRandom.current().nextInt(160);
                for (int i = 0; i < m.parts.size(); i++) {
                    if ("screen_text".equals(m.parts.get(i).name) && inst.ents[i] instanceof TextDisplay td) {
                        Player near = players.get(ThreadLocalRandom.current().nextInt(players.size()));
                        String line = ThreadLocalRandom.current().nextDouble() < 0.15
                                ? "<#7CFC9A>" + near.getName().toUpperCase() + "?"
                                : MONITOR[ThreadLocalRandom.current().nextInt(MONITOR.length)];
                        td.text(BackroomsConfig.mm(line));
                    }
                }
            }
            case "phone" -> {
                if (tick < inst.nextBehavior || nearestSq > 16 * 16) return;
                inst.nextBehavior = tick + 240 + ThreadLocalRandom.current().nextInt(300);
                for (int k = 0; k < 3; k++) {
                    plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                            ws.world.playSound(inst.anchor, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.BLOCKS, 1.0f, 1.9f), k * 12L);
                }
            }
            default -> {
            }
        }
    }

    private static final String[] MONITOR = {"<#7CFC9A>LOGIN: _", "<#7CFC9A>NO SIGNAL", "<#7CFC9A>WHERE ARE YOU", "<#7CFC9A>ERROR 0x0BACK",
            "<#7CFC9A>PLEASE REMAIN", "<#7CFC9A>YOU ARE LATE", "<#7CFC9A>FLOOR 4 OF 4", "<#7CFC9A>DON'T TURN AROUND", "<#7CFC9A>it's quiet today"};

    // ------------------------------------------------------------------ interactions

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent e) {
        Instance inst = interactions.get(e.getRightClicked().getUniqueId());
        if (inst == null) return;
        e.setCancelled(true);
        String action = e.getRightClicked().getPersistentDataContainer().get(Keys.ACTION, PersistentDataType.STRING);
        if (action != null) handleAction(e.getPlayer(), inst, action);
    }

    /** Left-clicking an interaction hitbox counts as using it too. */
    public boolean handleAttack(Player p, Entity attacked) {
        Instance inst = interactions.get(attacked.getUniqueId());
        if (inst == null) return false;
        String action = attacked.getPersistentDataContainer().get(Keys.ACTION, PersistentDataType.STRING);
        if (action != null) handleAction(p, inst, action);
        return true;
    }

    private void handleAction(Player p, Instance inst, String action) {
        long now = System.currentTimeMillis();
        Long until = actionCooldown.get(p.getUniqueId());
        if (until != null && until > now) return;
        actionCooldown.put(p.getUniqueId(), now + 800);
        World w = inst.anchor.getWorld();
        switch (action) {
            case "phone" -> {
                w.playSound(inst.anchor, Sound.BLOCK_WOODEN_TRAPDOOR_OPEN, SoundCategory.BLOCKS, 0.8f, 1.4f);
                Component hint = plugin.transitions().whisperHint(p);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (p.isOnline()) {
                        p.playSound(p.getLocation(), Sound.AMBIENT_CAVE, SoundCategory.AMBIENT, 0.4f, 1.6f);
                        p.sendMessage(hint);
                    }
                }, 30L);
            }
            case "radio" -> {
                w.playSound(inst.anchor, Sound.BLOCK_FIRE_AMBIENT, SoundCategory.BLOCKS, 1.0f, 1.9f);
                String[] lines = {
                        "<gray><italic>\"...this is Unit Seven... the stairs only go down once...\"",
                        "<gray><italic>\"...if it smiles, look at it. Don't stop looking...\"",
                        "<gray><italic>\"...the dogs hear you run. Walk. Walk.\"",
                        "<gray><italic>\"...there's grass behind the shutters. don't go out there. it's always evening...\"",
                        "<gray><italic>\"...almond water. Find the water...\""};
                p.sendMessage(BackroomsConfig.mm(lines[ThreadLocalRandom.current().nextInt(lines.length)]));
            }
            case "vending" -> {
                w.playSound(inst.anchor, Sound.BLOCK_CHEST_LOCKED, SoundCategory.BLOCKS, 0.8f, 0.6f);
                if (plugin.sessions().tryVend(p)) {
                    w.dropItemNaturally(inst.anchor.clone().add(0, 0.6, 0), plugin.items().almondWater());
                } else {
                    p.sendActionBar(BackroomsConfig.mm("<red>OUT OF ORDER"));
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ lamp control (used by events)

    /**
     * Dim or flicker the glowing parts of light fixtures near a point for a while. The invisible light blocks are
     * handled separately (client-side); this is what players actually see go dark.
     */
    public void dimLamps(Location c, double radius, int ticks, boolean flicker) {
        WorldState ws = worlds.get(c.getWorld().getUID());
        if (ws == null) return;
        List<Display> lamps = new ArrayList<>();
        List<Part> parts = new ArrayList<>();
        for (List<Instance> list : ws.active.values()) {
            for (Instance inst : list) {
                if (inst.anchor.distanceSquared(c) > radius * radius) continue;
                for (int i = 0; i < inst.model.parts.size(); i++) {
                    Part p = inst.model.parts.get(i);
                    if (p.lamp && inst.ents[i] instanceof Display d && d.isValid()) {
                        lamps.add(d);
                        parts.add(p);
                    }
                }
            }
        }
        if (lamps.isEmpty()) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (flicker) {
            for (int t = 0; t < ticks; t += 3) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    for (int i = 0; i < lamps.size(); i++) {
                        if (r.nextDouble() < 0.35) setLamp(lamps.get(i), parts.get(i), r.nextBoolean());
                    }
                }, t);
            }
        } else {
            for (int i = 0; i < lamps.size(); i++) setLamp(lamps.get(i), parts.get(i), false);
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (int i = 0; i < lamps.size(); i++) setLamp(lamps.get(i), parts.get(i), true);
        }, ticks + 2L);
    }

    private static void setLamp(Display d, Part p, boolean on) {
        if (!d.isValid()) return;
        d.setBrightness(new Display.Brightness(on ? 15 : 1, on ? 15 : 0));
        if (p.alt != null && d instanceof BlockDisplay bd) bd.setBlock(on ? p.block : p.alt);
    }

    // ------------------------------------------------------------------ admin / status

    public int entityCount(World w) {
        WorldState ws = worlds.get(w.getUID());
        return ws == null ? 0 : ws.entities;
    }

    public int activeChunks(World w) {
        WorldState ws = worlds.get(w.getUID());
        return ws == null ? 0 : ws.active.size();
    }

    public double costMillis() {
        return lastCostNanos / 1_000_000.0;
    }

    /** Remove and re-spawn everything (used after reload). */
    public void refresh() {
        start();
    }
}
