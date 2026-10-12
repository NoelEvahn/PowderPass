package dev.powderpass.profile;

import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Loads and saves {@link PlayerData}. All disk access runs on one dedicated thread so reads and writes for the
 * same player can never overtake each other; YAML is serialised on the main thread and only the string is
 * handed to the IO thread.
 */
public final class ProfileStore {

    private final JavaPlugin plugin;
    private final File folder;
    private final ExecutorService io;
    private final Map<UUID, PlayerData> loaded = new HashMap<>();
    private final Map<UUID, PlayerData> preloaded = new ConcurrentHashMap<>();

    public ProfileStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "playerdata");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create " + folder);
        }
        this.io = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "PowderPass-IO");
            t.setDaemon(true);
            return t;
        });
    }

    /** Called from the async pre-login thread: reads the profile ahead of the join. */
    public void preload(UUID uuid, String name) {
        try {
            Future<PlayerData> future = io.submit(() -> read(uuid, name));
            preloaded.put(uuid, future.get(5, TimeUnit.SECONDS));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Could not preload profile of " + name, e);
        }
    }

    /** Profile of an online player, loading it synchronously if the preload was missed. */
    public PlayerData get(Player player) {
        PlayerData data = loaded.get(player.getUniqueId());
        if (data == null) {
            data = preloaded.remove(player.getUniqueId());
            if (data == null) {
                try {
                    data = io.submit(() -> read(player.getUniqueId(), player.getName())).get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Could not load profile of " + player.getName(), e);
                    data = new PlayerData(player.getUniqueId(), player.getName());
                }
            }
            data.name(player.getName());
            loaded.put(player.getUniqueId(), data);
        }
        return data;
    }

    public PlayerData getIfLoaded(UUID uuid) {
        return loaded.get(uuid);
    }

    /** Reads an offline player's profile (blocking; for commands only). */
    public PlayerData readOffline(UUID uuid, String name) {
        PlayerData online = loaded.get(uuid);
        if (online != null) {
            return online;
        }
        try {
            return io.submit(() -> read(uuid, name)).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            return new PlayerData(uuid, name);
        }
    }

    public void unload(Player player) {
        PlayerData data = loaded.remove(player.getUniqueId());
        preloaded.remove(player.getUniqueId());
        if (data != null && data.dirty()) {
            saveAsync(data);
        }
    }

    public void saveDirtyAsync() {
        for (PlayerData data : loaded.values()) {
            if (data.dirty()) {
                saveAsync(data);
            }
        }
    }

    public void saveAsync(PlayerData data) {
        String yaml = data.toYaml().saveToString();
        UUID uuid = data.uuid();
        data.clean();
        io.execute(() -> write(uuid, yaml));
    }

    /** Saves everything and waits for the IO thread to finish. Used on shutdown. */
    public void shutdown() {
        for (PlayerData data : loaded.values()) {
            if (data.dirty()) {
                saveAsync(data);
            }
        }
        loaded.clear();
        io.shutdown();
        try {
            if (!io.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Timed out waiting for profiles to save");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private PlayerData read(UUID uuid, String name) {
        File file = new File(folder, uuid + ".yml");
        if (!file.exists()) {
            return new PlayerData(uuid, name);
        }
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.load(file);
            return PlayerData.fromYaml(uuid, y);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.WARNING, "Corrupt profile " + file.getName() + ", starting fresh", e);
            return new PlayerData(uuid, name);
        }
    }

    private void write(UUID uuid, String yaml) {
        Path target = folder.toPath().resolve(uuid + ".yml");
        Path tmp = folder.toPath().resolve(uuid + ".yml.tmp");
        try {
            Files.writeString(tmp, yaml, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Bukkit.getLogger().log(Level.WARNING, "[PowderPass] Could not save profile " + uuid, e);
        }
    }
}
