package dev.powderpass.lift;

import dev.powderpass.PowderPassPlugin;
import dev.powderpass.config.Messages;
import dev.powderpass.config.PluginConfig;
import dev.powderpass.lift.geom.LiftGeometry;
import dev.powderpass.lift.geom.LiftLayout;
import dev.powderpass.profile.PlayerData;
import dev.powderpass.ski.SkiSession;
import dev.powderpass.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Vector3d;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Loads, builds, ticks and persists chairlifts, and handles boarding and unloading. */
public final class LiftManager {

    /** A lift being surveyed: the base station is set, the summit is pending. */
    private record Pending(String name, Location bottom) {
    }

    private static final double BOARD_RADIUS = 0.9;

    private final PowderPassPlugin plugin;
    private final File file;
    private final Map<String, LiftDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, Lift> lifts = new LinkedHashMap<>();
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Set<UUID> allowedDismounts = new HashSet<>();
    private final Map<UUID, Long> deniedMessageAt = new HashMap<>();
    private long tick;

    public LiftManager(PowderPassPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "lifts.yml");
    }

    private PluginConfig config() {
        return plugin.config();
    }

    private Messages messages() {
        return plugin.messages();
    }

    // ------------------------------------------------------------------------------------------------ persistence

    public void load() {
        definitions.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("lifts");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            LiftDefinition d = s == null ? null : LiftDefinition.load(key, s, config());
            if (d == null) {
                plugin.getLogger().warning("Skipping malformed lift " + key);
                continue;
            }
            definitions.put(key.toLowerCase(Locale.ROOT), d);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        ConfigurationSection root = y.createSection("lifts");
        for (LiftDefinition d : definitions.values()) {
            d.save(root.createSection(d.name));
        }
        plugin.writeAsync(file, y.saveToString().getBytes(StandardCharsets.UTF_8));
    }

    /** (Re)builds the runtime of every lift whose world is loaded. */
    public void buildAll() {
        for (Lift lift : lifts.values()) {
            rescueRiders(lift);
            lift.despawn();
        }
        lifts.clear();
        for (LiftDefinition d : definitions.values()) {
            build(d);
        }
    }

    private Lift build(LiftDefinition d) {
        String key = d.name.toLowerCase(Locale.ROOT);
        Lift old = lifts.remove(key);
        if (old != null) {
            rescueRiders(old);
            old.despawn();
        }
        World world = Bukkit.getWorld(d.world);
        if (world == null || !d.hasGround()) {
            return null;
        }
        try {
            LiftLayout layout = LiftGeometry.build(d.spec(config()), d.groundFunction());
            Lift lift = new Lift(d, world, layout, d.seats, config().hangerLength);
            lifts.put(key, lift);
            return lift;
        } catch (IllegalArgumentException e) {
            plugin.getLogger().log(Level.WARNING, "Lift " + d.name + " could not be built: " + e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------------------------------------ admin

    public Collection<Lift> lifts() {
        return List.copyOf(lifts.values());
    }

    public Collection<LiftDefinition> definitions() {
        return List.copyOf(definitions.values());
    }

    public LiftDefinition definition(String name) {
        return name == null ? null : definitions.get(name.toLowerCase(Locale.ROOT));
    }

    public Lift lift(String name) {
        return name == null ? null : lifts.get(name.toLowerCase(Locale.ROOT));
    }

    public List<String> names() {
        return definitions.values().stream().map(d -> d.name).toList();
    }

    /** Step 1: remember the base station at the player's feet. */
    public boolean beginSurvey(Player p, String name) {
        if (!name.matches("[A-Za-z0-9_-]{1,32}") || definition(name) != null) {
            return false;
        }
        Location l = p.getLocation();
        Location bottom = new Location(l.getWorld(), l.getX(), Math.floor(l.getY() + 0.01), l.getZ());
        pending.put(p.getUniqueId(), new Pending(name, bottom));
        return true;
    }

    public boolean surveying(Player p) {
        return pending.containsKey(p.getUniqueId());
    }

    /**
     * Step 2: set the summit at the player's feet and build the lift.
     *
     * @return null on success, otherwise an error message
     */
    public String finishSurvey(Player p) {
        Pending pend = pending.get(p.getUniqueId());
        if (pend == null) {
            return messages().raw("lift.no-survey");
        }
        Location top = p.getLocation();
        if (!top.getWorld().equals(pend.bottom().getWorld())) {
            return messages().raw("lift.other-world");
        }
        Location bottom = pend.bottom();
        PluginConfig c = config();
        LiftDefinition d = new LiftDefinition(pend.name(), bottom.getWorld().getName(), bottom.getX(), bottom.getY(), bottom.getZ(),
                top.getX(), Math.floor(top.getY() + 0.01), top.getZ(), c.liftSpeed, c.chairSpacing, c.seatsPerChair, c.defaultLiftStyle);
        double len = d.horizontalLength();
        if (len > 1200) {
            return messages().raw("lift.too-long");
        }
        if (Math.abs(d.ty - d.by) > len * 1.1) {
            return messages().raw("lift.too-steep");
        }
        String sampled = sampleGround(d, bottom.getWorld());
        if (sampled != null) {
            return sampled;
        }
        try {
            LiftLayout layout = LiftGeometry.build(d.spec(c), d.groundFunction());
            for (String warning : layout.warnings()) {
                p.sendMessage(Text.mm("<#fbbf24>⚠ <gray>" + Text.escape(warning)));
            }
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        pending.remove(p.getUniqueId());
        definitions.put(d.name.toLowerCase(Locale.ROOT), d);
        build(d);
        save();
        return null;
    }

    /** Samples the terrain profile under the three rope lines. Returns an error message or null. */
    private String sampleGround(LiftDefinition d, World world) {
        double len = d.horizontalLength();
        double ux = (d.tx - d.bx) / len;
        double uz = (d.tz - d.bz) / len;
        double rx = -uz;
        double rz = ux;
        int n = (int) Math.ceil(len) + 1;
        float[][] lines = new float[3][n];
        double[] offsets = {-1.6, 0, 1.6};
        for (int li = 0; li < 3; li++) {
            for (int i = 0; i < n; i++) {
                double x = d.bx + ux * i + rx * offsets[li];
                double z = d.bz + uz * i + rz * offsets[li];
                int bx = (int) Math.floor(x);
                int bz = (int) Math.floor(z);
                if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
                    return messages().raw("lift.line-unloaded");
                }
                Block top = world.getHighestBlockAt(bx, bz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                lines[li][i] = top.getY() + 1f;
            }
        }
        d.ground(lines[0], lines[1], lines[2]);
        return null;
    }

    public boolean resample(LiftDefinition d) {
        World world = Bukkit.getWorld(d.world);
        if (world == null || sampleGround(d, world) != null) {
            return false;
        }
        build(d);
        save();
        return true;
    }

    public void remove(LiftDefinition d) {
        Lift lift = lifts.remove(d.name.toLowerCase(Locale.ROOT));
        if (lift != null) {
            rescueRiders(lift);
            lift.despawn();
        }
        definitions.remove(d.name.toLowerCase(Locale.ROOT));
        save();
    }

    /** Applies changed settings: rebuilds the runtime. */
    public void changed(LiftDefinition d) {
        build(d);
        save();
    }

    // ------------------------------------------------------------------------------------------------ ticking

    public void tick() {
        tick++;
        PluginConfig c = config();
        for (Lift lift : lifts.values()) {
            lift.tick(tick, c.liftUpdateInterval, c.liftActivationRange);
            if (!lift.active()) {
                continue;
            }
            for (Chair chair : lift.chairs()) {
                if (tick % 20 == 0) {
                    chair.validateRiders();
                }
                if (!chair.spawned()) {
                    continue;
                }
                stationLogic(lift, chair);
            }
            if (tick % 10 == 0) {
                riderHud(lift);
            }
            if (tick % 40 == 0) {
                lift.updateSigns(lift.riderCount());
            }
        }
        if (tick % 200 == 0) {
            deniedMessageAt.values().removeIf(t -> tick - t > 200);
        }
    }

    private void stationLogic(Lift lift, Chair chair) {
        LiftLayout layout = lift.layout();
        double len = layout.path().length();
        double s = chair.s;
        if (layout.topUnload().contains(s, len) || layout.bottomUnload().contains(s, len)) {
            if (chair.hasRiders()) {
                unload(lift, chair);
            }
            return;
        }
        boolean loading = layout.bottomLoad().contains(s, len)
                || (config().allowRideDown && layout.topLoad().contains(s, len));
        if (loading) {
            tryBoard(lift, chair);
        }
    }

    private void tryBoard(Lift lift, Chair chair) {
        for (int seat = 0; seat < chair.seats(); seat++) {
            if (chair.rider(seat) != null) {
                continue;
            }
            Vector3d sw = chair.seatWorld(seat);
            double feetY = sw.y - 0.6;
            for (Player p : lift.world().getPlayers()) {
                Location l = p.getLocation();
                double dx = l.getX() - sw.x;
                double dz = l.getZ() - sw.z;
                if (dx * dx + dz * dz > BOARD_RADIUS * BOARD_RADIUS || Math.abs(l.getY() - feetY) > 1.1) {
                    continue;
                }
                if (p.getGameMode() == GameMode.SPECTATOR || p.isDead() || p.isFlying() || p.isGliding()) {
                    continue;
                }
                SkiSession skiing = plugin.skis().session(p);
                if (p.isInsideVehicle() && skiing == null) {
                    continue;
                }
                if (!canRide(p, lift)) {
                    continue;
                }
                if (skiing != null) {
                    if (skiing.state().speed() > 0.3) {
                        continue;
                    }
                    plugin.skis().end(skiing, SkiSession.EndReason.UNCLIP, false);
                }
                if (chair.board(p, seat)) {
                    onBoarded(p, lift);
                    break;
                }
            }
        }
    }

    private boolean canRide(Player p, Lift lift) {
        if (!p.hasPermission("powderpass.lift.ride")) {
            return false;
        }
        boolean requirePass = lift.definition().requirePass != null ? lift.definition().requirePass : config().liftRequirePass;
        if (!requirePass || p.hasPermission("powderpass.bypass.pass")) {
            return true;
        }
        if (plugin.passes().validPass(p) != null) {
            return true;
        }
        Long last = deniedMessageAt.get(p.getUniqueId());
        if (last == null || tick - last > 40) {
            deniedMessageAt.put(p.getUniqueId(), tick);
            messages().actionBar(p, "lift.need-pass");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.8f, 0.6f);
        }
        return false;
    }

    private void onBoarded(Player p, Lift lift) {
        p.playSound(p.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, SoundCategory.PLAYERS, 0.8f, 0.8f);
        p.playSound(p.getLocation(), Sound.ENTITY_MINECART_RIDING, SoundCategory.PLAYERS, 0.15f, 1.6f);
        messages().send(p, "lift.boarded", Messages.p("lift", lift.definition().name));
    }

    private void unload(Lift lift, Chair chair) {
        Vector3d dir = chair.direction();
        for (int seat = 0; seat < chair.seats(); seat++) {
            UUID id = chair.rider(seat);
            if (id == null) {
                continue;
            }
            chair.clearRider(seat);
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) {
                continue;
            }
            Vector3d sw = chair.seatWorld(seat);
            Location landing = safeLanding(lift.world(), sw.x + dir.x * 0.9, sw.y, sw.z + dir.z * 0.9,
                    nearestStationFloor(lift, sw));
            float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
            landing.setYaw(yaw);
            landing.setPitch(p.getLocation().getPitch());
            dismount(p);
            p.teleport(landing);
            p.setVelocity(new Vector(dir.x * 0.3, 0.12, dir.z * 0.3));
            p.playSound(p.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_OPEN, SoundCategory.PLAYERS, 0.7f, 1.0f);
            PlayerData data = plugin.profiles().get(p);
            data.liftRides++;
            data.markDirty();
            plugin.badges().onTotals(p, data);
            messages().actionBar(p, "lift.unloaded");
        }
        chair.kick(0, 2.5);
    }

    private void dismount(Player p) {
        allowedDismounts.add(p.getUniqueId());
        try {
            p.leaveVehicle();
        } finally {
            allowedDismounts.remove(p.getUniqueId());
        }
    }

    private Location nearestStationFloor(Lift lift, Vector3d near) {
        LiftDefinition d = lift.definition();
        double db = Math.hypot(near.x - d.bx, near.z - d.bz);
        double dt = Math.hypot(near.x - d.tx, near.z - d.tz);
        return db < dt ? new Location(lift.world(), d.bx, d.by, d.bz) : new Location(lift.world(), d.tx, d.ty, d.tz);
    }

    /** Finds solid ground at (x, z) a little below {@code fromY}; falls back to {@code fallback}. */
    private Location safeLanding(World world, double x, double fromY, double z, Location fallback) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        if (world.isChunkLoaded(bx >> 4, bz >> 4)) {
            int start = (int) Math.floor(fromY + 1);
            for (int y = start; y > start - 6 && y > world.getMinHeight(); y--) {
                Block b = world.getBlockAt(bx, y, bz);
                Block above = b.getRelative(0, 1, 0);
                Block above2 = b.getRelative(0, 2, 0);
                if (!b.isPassable() && above.isPassable() && above2.isPassable() && !b.isLiquid()) {
                    return new Location(world, x, y + 1.0, z);
                }
                if (b.getType() == org.bukkit.Material.SNOW && above.isPassable()) {
                    return new Location(world, x, y + 0.2, z);
                }
            }
        }
        return fallback.clone().add(0, 0.05, 0);
    }

    private void riderHud(Lift lift) {
        LiftLayout layout = lift.layout();
        double len = layout.path().length();
        for (Chair chair : lift.chairs()) {
            for (int seat = 0; seat < chair.seats(); seat++) {
                UUID id = chair.rider(seat);
                Player p = id == null ? null : Bukkit.getPlayer(id);
                if (p == null) {
                    continue;
                }
                boolean up = chair.s < layout.topUnload().start();
                double target = up ? layout.topUnload().start() : layout.bottomUnload().start();
                double remaining = target - chair.s;
                if (remaining < 0) {
                    remaining += len;
                }
                double seconds = remaining / Math.max(0.01, lift.definition().speed) / 20.0;
                double progress = up ? 1.0 - remaining / Math.max(1, layout.topUnload().start() - layout.bottomLoad().end())
                        : 1.0 - remaining / Math.max(1, layout.bottomUnload().start() - layout.topLoad().end());
                int bars = (int) Math.round(Math.max(0, Math.min(1, progress)) * 12);
                p.sendActionBar(Text.mm("<#7dd3fc>🚡 <white>" + Text.escape(lift.definition().name) + "  <dark_gray>│  <gray>"
                        + (up ? "Summit" : "Base") + " in <white>" + formatSeconds(seconds) + "  <#38bdf8>"
                        + "▮".repeat(bars) + "<dark_gray>" + "▮".repeat(12 - bars)));
            }
        }
    }

    private static String formatSeconds(double seconds) {
        long s = Math.max(0, Math.round(seconds));
        return s >= 60 ? (s / 60) + "m " + (s % 60) + "s" : s + "s";
    }

    // ------------------------------------------------------------------------------------------------ events

    /** Whether a player dismounting from {@code vehicle} should be stopped. */
    public boolean shouldBlockDismount(Player player, Entity vehicle) {
        if (allowedDismounts.contains(player.getUniqueId()) || config().allowMidrideDismount) {
            return false;
        }
        for (Lift lift : lifts.values()) {
            for (Chair chair : lift.chairs()) {
                if (chair.seatOf(vehicle) >= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A rider left (allowed dismount, quit...): clear their seat. */
    public void onDismounted(Player player, Entity vehicle) {
        for (Lift lift : lifts.values()) {
            for (Chair chair : lift.chairs()) {
                int seat = chair.seatOf(vehicle);
                if (seat >= 0 && player.getUniqueId().equals(chair.rider(seat))) {
                    chair.clearRider(seat);
                }
            }
        }
    }

    /** Takes a player off any chair and puts them on the nearest station platform. */
    public boolean rescue(Player player) {
        for (Lift lift : lifts.values()) {
            for (Chair chair : lift.chairs()) {
                for (int seat = 0; seat < chair.seats(); seat++) {
                    if (player.getUniqueId().equals(chair.rider(seat))) {
                        chair.clearRider(seat);
                        Location floor = nearestStationFloor(lift, chair.seatWorld(seat));
                        dismount(player);
                        player.teleport(floor.add(0, 0.05, 0));
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void rescueRiders(Lift lift) {
        for (Chair chair : lift.chairs()) {
            for (int seat = 0; seat < chair.seats(); seat++) {
                UUID id = chair.rider(seat);
                Player p = id == null ? null : Bukkit.getPlayer(id);
                if (p != null) {
                    rescue(p);
                }
                chair.clearRider(seat);
            }
        }
    }

    public void onWorldUnload(World world) {
        for (Lift lift : List.copyOf(lifts.values())) {
            if (lift.world().equals(world)) {
                rescueRiders(lift);
                lift.despawn();
                lifts.remove(lift.definition().name.toLowerCase(Locale.ROOT));
            }
        }
    }

    public void onWorldLoad(World world) {
        for (LiftDefinition d : definitions.values()) {
            if (d.world.equals(world.getName()) && !lifts.containsKey(d.name.toLowerCase(Locale.ROOT))) {
                build(d);
            }
        }
    }

    public void forgetSurvey(UUID player) {
        pending.remove(player);
    }

    public void shutdown() {
        for (Lift lift : lifts.values()) {
            rescueRiders(lift);
            lift.despawn();
        }
        lifts.clear();
    }
}
