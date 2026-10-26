package dev.powderpass.volleyball.court;

import dev.powderpass.volleyball.PowderPassPlugin;
import dev.powderpass.volleyball.config.Settings;
import dev.powderpass.volleyball.math.Vec3;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/** Owns all courts: persistence, visuals and chunk tickets. */
public final class CourtManager {

    private final PowderPassPlugin plugin;
    private final File file;
    private final CourtBuilder builder;
    private final Map<String, Court> courts = new HashMap<>();
    private final Map<String, CourtVisuals> visuals = new HashMap<>();
    private final Map<String, List<long[]>> tickets = new HashMap<>();

    public CourtManager(PowderPassPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "courts.yml");
        this.builder = new CourtBuilder(plugin.getDataFolder(), plugin.getLogger());
    }

    public CourtBuilder builder() {
        return builder;
    }

    public void load() {
        courts.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("courts");
        if (root == null) {
            return;
        }
        for (String name : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(name);
            if (s == null) {
                continue;
            }
            try {
                Court c = Court.load(name, s, plugin.settings().matchDefaults);
                courts.put(key(name), c);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Could not load court " + name, e);
            }
        }
        plugin.getLogger().info("Loaded " + courts.size() + " court(s).");
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        ConfigurationSection root = y.createSection("courts");
        for (Court c : courts.values()) {
            c.save(root.createSection(c.name()));
        }
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save courts.yml", e);
        }
    }

    public void spawnAll() {
        for (Court c : courts.values()) {
            spawnVisuals(c);
        }
    }

    public void spawnVisuals(Court c) {
        despawnVisuals(c);
        World world = c.world();
        if (world == null) {
            plugin.getLogger().warning("World '" + c.worldName() + "' of court " + c.name() + " is not loaded.");
            return;
        }
        addTickets(c, world);
        CourtVisuals v = new CourtVisuals(plugin, c);
        v.spawn(plugin.settings());
        visuals.put(key(c.name()), v);
    }

    public void despawnVisuals(Court c) {
        CourtVisuals v = visuals.remove(key(c.name()));
        if (v != null) {
            v.remove();
        }
        removeTickets(c);
    }

    private void addTickets(Court c, World world) {
        List<long[]> list = new ArrayList<>();
        double r = Math.max(c.halfLength(), c.halfWidth()) + c.freeZone() + 2;
        int minCx = (int) Math.floor((c.centerX() - r)) >> 4;
        int maxCx = (int) Math.floor((c.centerX() + r)) >> 4;
        int minCz = (int) Math.floor((c.centerZ() - r)) >> 4;
        int maxCz = (int) Math.floor((c.centerZ() + r)) >> 4;
        for (int x = minCx; x <= maxCx; x++) {
            for (int z = minCz; z <= maxCz; z++) {
                world.addPluginChunkTicket(x, z, plugin);
                list.add(new long[]{x, z});
            }
        }
        tickets.put(key(c.name()), list);
    }

    private void removeTickets(Court c) {
        List<long[]> list = tickets.remove(key(c.name()));
        World world = c.world();
        if (list == null || world == null) {
            return;
        }
        for (long[] t : list) {
            world.removePluginChunkTicket((int) t[0], (int) t[1], plugin);
        }
    }

    public void tick() {
        for (CourtVisuals v : visuals.values()) {
            v.tick();
        }
    }

    public void shutdown() {
        for (Court c : new ArrayList<>(courts.values())) {
            despawnVisuals(c);
        }
    }

    /**
     * Creates a court centred on the player's feet, with its length along the player's facing direction.
     */
    public Court create(String name, Location at, double length, double width, double netHeight, double attackLine,
                        boolean beach, boolean floor) {
        Settings s = plugin.settings();
        BlockFace face = yawToFace(at.getYaw());
        int dx = face.getModX();
        int dz = face.getModZ();
        double cy = at.getBlockY();
        // align the court with the block grid: even sizes centre on a block edge, odd sizes on a block centre
        double alongCoord = dx != 0 ? at.getX() : at.getZ();
        double acrossCoord = dx != 0 ? at.getZ() : at.getX();
        double along = snap(alongCoord, length);
        double across = snap(acrossCoord, width);
        double cx = dx != 0 ? along : across;
        double cz = dx != 0 ? across : along;
        Court c = new Court(name, at.getWorld().getName(), cx, cy, cz, dx, dz);
        Settings.CourtDefaults cd = s.courtDefaults;
        c.applyDimensions(length, width, netHeight, attackLine, cd.freeZone(), cd.netBandHeight(), cd.antennaAboveNet(),
                cd.postOffset(), beach);
        c.setSettings(s.matchDefaults.copy());
        courts.put(key(name), c);
        if (floor) {
            builder.placeFloor(c, s);
        }
        spawnVisuals(c);
        save();
        return c;
    }

    private static double snap(double coord, double size) {
        double rounded = Math.rint(size);
        boolean integral = Math.abs(rounded - size) < 1e-6;
        if (integral && ((long) rounded) % 2 == 0) {
            return Math.rint(coord);
        }
        return Math.floor(coord) + 0.5;
    }

    private static BlockFace yawToFace(float yaw) {
        float y = ((yaw % 360) + 360) % 360;
        if (y >= 45 && y < 135) {
            return BlockFace.WEST;
        }
        if (y >= 135 && y < 225) {
            return BlockFace.NORTH;
        }
        if (y >= 225 && y < 315) {
            return BlockFace.EAST;
        }
        return BlockFace.SOUTH;
    }

    public boolean remove(String name, boolean restoreFloor) {
        Court c = courts.remove(key(name));
        if (c == null) {
            return false;
        }
        despawnVisuals(c);
        if (restoreFloor) {
            builder.restoreFloor(c);
        } else {
            builder.deleteUndo(c.name());
        }
        save();
        return true;
    }

    public Court get(String name) {
        return name == null ? null : courts.get(key(name));
    }

    public Collection<Court> all() {
        return courts.values();
    }

    public List<String> names() {
        List<String> l = new ArrayList<>();
        for (Court c : courts.values()) {
            l.add(c.name());
        }
        l.sort(String.CASE_INSENSITIVE_ORDER);
        return l;
    }

    public CourtVisuals visuals(Court c) {
        return visuals.get(key(c.name()));
    }

    public NetRenderer netRenderer(Court c) {
        CourtVisuals v = visuals(c);
        return v == null ? null : v.net();
    }

    /** The court whose area (incl. free zone + margin) contains the location, nearest centre first. */
    public Court at(Location loc, double margin) {
        Court best = null;
        double bestDist = Double.MAX_VALUE;
        for (Court c : courts.values()) {
            if (!c.inArea(loc, margin)) {
                continue;
            }
            double d = loc.distanceSquared(c.center());
            if (d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        return best;
    }

    public Court at(World world, Vec3 pos, double margin) {
        return at(new Location(world, pos.x, pos.y, pos.z), margin);
    }

    public Court nearest(Location loc, double maxDistance) {
        Court best = null;
        double bestDist = maxDistance * maxDistance;
        for (Court c : courts.values()) {
            if (!c.worldName().equals(loc.getWorld().getName())) {
                continue;
            }
            double d = loc.distanceSquared(c.center());
            if (d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        return best;
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
