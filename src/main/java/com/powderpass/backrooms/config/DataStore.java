package com.powderpass.backrooms.config;

import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.gen.GenSettings;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Persistent plugin state that must survive restarts: the resolved base seed, the generation snapshot each
 * world was created with, and admin spawns. Writes go to a temp file and are atomically moved in place, with
 * the previous version kept as a .bak, so a crash mid-write never loses world metadata.
 */
public final class DataStore {
    private final File file;
    private final Logger log;
    private YamlConfiguration yaml = new YamlConfiguration();

    public DataStore(File dataFolder, Logger log) {
        this.file = new File(new File(dataFolder, "data"), "worlds.yml");
        this.log = log;
    }

    public void load() {
        yaml = new YamlConfiguration();
        File f = file.exists() ? file : new File(file.getPath() + ".bak");
        if (f.exists()) {
            try {
                yaml.load(f);
            } catch (Exception e) {
                log.severe("Could not read " + f + " (" + e.getMessage() + "); trying backup");
                try {
                    yaml.load(new File(file.getPath() + ".bak"));
                } catch (Exception e2) {
                    log.severe("Backup unreadable too; continuing with empty metadata. Existing worlds keep their files.");
                }
            }
        }
    }

    /** Synchronous save (startup/shutdown only). */
    public void save() {
        write(yaml.saveToString());
    }

    /** Snapshot on the calling (main) thread, write on an async thread. */
    public void saveAsync(org.bukkit.plugin.Plugin plugin) {
        String snapshot = yaml.saveToString();
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(snapshot));
    }

    private synchronized void write(String content) {
        try {
            Files.createDirectories(file.getParentFile().toPath());
            File tmp = new File(file.getPath() + ".tmp");
            Files.writeString(tmp.toPath(), content);
            if (file.exists()) {
                Files.copy(file.toPath(), new File(file.getPath() + ".bak").toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.severe("Failed to save " + file + ": " + e.getMessage());
        }
    }

    public boolean hasBaseSeed() {
        return yaml.contains("base-seed");
    }

    public long baseSeed() {
        return yaml.getLong("base-seed");
    }

    public void setBaseSeed(long seed) {
        yaml.set("base-seed", seed);
    }

    public boolean hasLevel(Level l) {
        return yaml.contains("levels." + l.key() + ".seed");
    }

    public long levelBaseSeed(Level l) {
        return yaml.getLong("levels." + l.key() + ".seed");
    }

    public GenSettings levelGen(Level l) {
        Map<String, Double> m = new HashMap<>();
        ConfigurationSection s = yaml.getConfigurationSection("levels." + l.key() + ".generation");
        if (s != null) for (String k : s.getKeys(false)) m.put(k, s.getDouble(k));
        return new GenSettings(m);
    }

    public void putLevel(Level l, long baseSeed, GenSettings gs, String worldName) {
        String p = "levels." + l.key();
        yaml.set(p + ".seed", baseSeed);
        yaml.set(p + ".world", worldName);
        yaml.set(p + ".created", System.currentTimeMillis());
        yaml.set(p + ".generator-version", com.powderpass.backrooms.gen.Generators.VERSION);
        for (Map.Entry<String, Double> e : gs.asMap().entrySet()) yaml.set(p + ".generation." + e.getKey(), e.getValue());
    }

    public int generatorVersion(Level l) {
        return yaml.getInt("levels." + l.key() + ".generator-version", 1);
    }

    public double[] spawn(Level l) {
        String p = "spawns." + l.key();
        if (!yaml.contains(p + ".x")) return null;
        return new double[]{yaml.getDouble(p + ".x"), yaml.getDouble(p + ".y"), yaml.getDouble(p + ".z"), yaml.getDouble(p + ".yaw"), yaml.getDouble(p + ".pitch")};
    }

    public void clearSpawn(Level l) {
        yaml.set("spawns." + l.key(), null);
    }

    public void setSpawn(Level l, double x, double y, double z, float yaw, float pitch) {
        String p = "spawns." + l.key();
        yaml.set(p + ".x", x);
        yaml.set(p + ".y", y);
        yaml.set(p + ".z", z);
        yaml.set(p + ".yaw", (double) yaw);
        yaml.set(p + ".pitch", (double) pitch);
    }
}
