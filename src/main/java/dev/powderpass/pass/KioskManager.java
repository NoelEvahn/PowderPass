package dev.powderpass.pass;

import dev.powderpass.PowderPassPlugin;
import dev.powderpass.display.Displays;
import dev.powderpass.display.StaticStructure;
import dev.powderpass.display.Transforms;
import dev.powderpass.util.Keys;
import dev.powderpass.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Matrix4f;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Ticket booths built from displays; right-clicking one opens the ticket window. */
public final class KioskManager {

    private record Kiosk(String id, String world, double x, double y, double z, float yaw) {
    }

    private static final float VIEW = 1.5f;

    private final PowderPassPlugin plugin;
    private final File file;
    private final Map<String, Kiosk> kiosks = new LinkedHashMap<>();
    private final Map<String, StaticStructure> structures = new LinkedHashMap<>();
    private long tick;

    public KioskManager(PowderPassPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "kiosks.yml");
    }

    public void load() {
        kiosks.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("kiosks");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s != null) {
                kiosks.put(id, new Kiosk(id, s.getString("world", "world"), s.getDouble("x"), s.getDouble("y"),
                        s.getDouble("z"), (float) s.getDouble("yaw")));
            }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        ConfigurationSection root = y.createSection("kiosks");
        for (Kiosk k : kiosks.values()) {
            ConfigurationSection s = root.createSection(k.id());
            s.set("world", k.world());
            s.set("x", k.x());
            s.set("y", k.y());
            s.set("z", k.z());
            s.set("yaw", (double) k.yaw());
        }
        plugin.writeAsync(file, y.saveToString().getBytes(StandardCharsets.UTF_8));
    }

    public void create(Player p) {
        Location l = p.getLocation();
        float yaw = Math.round((l.getYaw() + 180f) / 90f) * 90f; // face the player who placed it
        String id = UUID.randomUUID().toString().substring(0, 8);
        Kiosk k = new Kiosk(id, l.getWorld().getName(), l.getBlockX() + 0.5, l.getBlockY(), l.getBlockZ() + 0.5, yaw);
        kiosks.put(id, k);
        build(k);
        save();
    }

    /** Removes the kiosk nearest to the player within 6 blocks. */
    public boolean removeNearest(Player p) {
        Location l = p.getLocation();
        Kiosk best = null;
        double bestD = 36;
        for (Kiosk k : kiosks.values()) {
            if (!k.world().equals(l.getWorld().getName())) {
                continue;
            }
            double d = l.distanceSquared(new Location(l.getWorld(), k.x(), k.y(), k.z()));
            if (d < bestD) {
                bestD = d;
                best = k;
            }
        }
        if (best == null) {
            return false;
        }
        kiosks.remove(best.id());
        StaticStructure s = structures.remove(best.id());
        if (s != null) {
            s.despawn();
        }
        save();
        return true;
    }

    public int count() {
        return kiosks.size();
    }

    public void buildAll() {
        for (StaticStructure s : structures.values()) {
            s.despawn();
        }
        structures.clear();
        for (Kiosk k : kiosks.values()) {
            build(k);
        }
    }

    private void build(Kiosk k) {
        World world = Bukkit.getWorld(k.world());
        if (world == null) {
            return;
        }
        StaticStructure s = new StaticStructure(world);
        Location at = new Location(world, k.x(), k.y(), k.z());
        Matrix4f f = Transforms.yaw(k.yaw());
        addBlock(s, at, Transforms.box(f, -1.0f, 0f, -0.3f, 2.0f, 1.0f, 0.6f), Material.SPRUCE_PLANKS);
        addBlock(s, at, Transforms.box(f, -1.1f, 1.0f, -0.4f, 2.2f, 0.08f, 0.8f), Material.STRIPPED_BIRCH_LOG);
        addBlock(s, at, Transforms.box(f, -1.1f, 0f, -1.7f, 2.2f, 2.5f, 0.12f), Material.WHITE_CONCRETE);
        addBlock(s, at, Transforms.box(f, -1.1f, 0f, -1.7f, 0.12f, 2.5f, 1.4f), Material.WHITE_CONCRETE);
        addBlock(s, at, Transforms.box(f, 0.98f, 0f, -1.7f, 0.12f, 2.5f, 1.4f), Material.WHITE_CONCRETE);
        for (int i = 0; i < 5; i++) {
            Matrix4f stripe = new Matrix4f(f).translate(-1.25f + i * 0.5f, 2.55f, -1.8f)
                    .rotateX((float) Math.toRadians(15)).scale(0.5f, 0.08f, 2.4f);
            addBlock(s, at, stripe, i % 2 == 0 ? Material.RED_WOOL : Material.WHITE_WOOL);
        }
        Matrix4f lantern = new Matrix4f(f).translate(0.75f, 1.3f, 0f).scale(0.45f);
        s.add(at, l -> Displays.item(l, new org.bukkit.inventory.ItemStack(Material.LANTERN), lantern, VIEW, null));
        Matrix4f signM = new Matrix4f().translate(0f, 3.25f, 0f).scale(1.2f);
        Component sign = Text.mm("<gradient:#fbbf24:#f87171><bold>🎟 LIFT TICKETS</bold></gradient>\n<gray>Right-click to buy a <white>PowderPass");
        s.add(at, l -> Displays.text(l, sign, signM, VIEW, null));
        double r = Math.toRadians(k.yaw());
        Location front = at.clone().add(-Math.sin(r) * 0.2, 0, Math.cos(r) * 0.2);
        s.add(front, l -> Displays.interaction(l, 2.6f, 2.6f,
                i -> i.getPersistentDataContainer().set(Keys.KIOSK, PersistentDataType.STRING, k.id())));
        structures.put(k.id(), s);
    }

    private static void addBlock(StaticStructure s, Location at, Matrix4f m, Material material) {
        s.add(at, l -> Displays.block(l, material.createBlockData(), m, VIEW, null));
    }

    public static boolean isKiosk(Entity e) {
        return e.getPersistentDataContainer().has(Keys.KIOSK, PersistentDataType.STRING);
    }

    public void tick() {
        tick++;
        if (tick % 20 == 0) {
            for (StaticStructure s : structures.values()) {
                s.sync(s.playerNear(96));
            }
        }
    }

    public void onWorldUnload(World world) {
        for (StaticStructure s : structures.values()) {
            if (s.world().equals(world)) {
                s.despawn();
            }
        }
    }

    public void shutdown() {
        for (StaticStructure s : structures.values()) {
            s.despawn();
        }
        structures.clear();
    }
}
