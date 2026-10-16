package com.powderpass.backrooms.display;

import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.gen.DecorSpec;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Assets placed with {@code /backrooms assets spawn}, kept in {@code plugins/Backrooms/data/assets.yml}. Like the
 * generated decorations they are only entities while a player is near (the {@link DecorationManager} spawns them with
 * the chunk's props, in any world); unlike them they are saved, so they come back after a restart.
 */
public final class AssetStore {
    /** One placed asset. */
    public record Placed(UUID id, String world, String model, double x, double y, double z, float yaw, long seed, String by, long at) {
        public DecorSpec spec() {
            return new DecorSpec(model, x, y, z, yaw, 0, 0, seed);
        }

        int chunkX() {
            return Math.floorDiv((int) Math.floor(x), 16);
        }

        int chunkZ() {
            return Math.floorDiv((int) Math.floor(z), 16);
        }
    }

    private final BackroomsPlugin plugin;
    private final File file;
    /** world name -> chunk key -> assets anchored in that chunk */
    private final Map<String, Map<Long, List<Placed>>> byChunk = new HashMap<>();
    private int count;

    public AssetStore(BackroomsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(new File(plugin.getDataFolder(), "data"), "assets.yml");
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    public void load() {
        byChunk.clear();
        count = 0;
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> m : y.getMapList("assets")) {
            try {
                Placed p = new Placed(UUID.fromString(String.valueOf(m.get("id"))), String.valueOf(m.get("world")), String.valueOf(m.get("model")),
                        num(m.get("x")), num(m.get("y")), num(m.get("z")), (float) num(m.get("yaw")), (long) num(m.get("seed")),
                        String.valueOf(m.get("by")), (long) num(m.get("at")));
                index(p);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("[Assets] Skipping a broken entry in assets.yml: " + m + " (" + ex.getMessage() + ")");
            }
        }
        plugin.getLogger().info("[Assets] " + count + " placed assets loaded.");
    }

    private static double num(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(o));
    }

    /** Write the file off the main thread (a snapshot is taken first). */
    public void save() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<Long, List<Placed>> w : byChunk.values()) {
            for (List<Placed> l : w.values()) {
                for (Placed p : l) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", p.id().toString());
                    m.put("world", p.world());
                    m.put("model", p.model());
                    m.put("x", p.x());
                    m.put("y", p.y());
                    m.put("z", p.z());
                    m.put("yaw", (double) p.yaw());
                    m.put("seed", p.seed());
                    m.put("by", p.by());
                    m.put("at", p.at());
                    rows.add(m);
                }
            }
        }
        YamlConfiguration y = new YamlConfiguration();
        y.set("assets", rows);
        String text = y.saveToString();
        Runnable write = () -> {
            try {
                File dir = file.getParentFile();
                if (!dir.exists() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
                File tmp = new File(dir, "assets.yml.tmp");
                java.nio.file.Files.writeString(tmp.toPath(), text);
                java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ex) {
                plugin.getLogger().warning("[Assets] Could not save assets.yml: " + ex.getMessage());
            }
        };
        if (plugin.isEnabled()) plugin.getServer().getScheduler().runTaskAsynchronously(plugin, write);
        else write.run();
    }

    private void index(Placed p) {
        byChunk.computeIfAbsent(p.world(), k -> new HashMap<>()).computeIfAbsent(key(p.chunkX(), p.chunkZ()), k -> new ArrayList<>()).add(p);
        count++;
    }

    public Placed add(Location at, String model, float yaw, long seed, String by) {
        Placed p = new Placed(UUID.randomUUID(), at.getWorld().getName(), model, at.getX(), at.getY(), at.getZ(), yaw, seed, by, System.currentTimeMillis());
        index(p);
        save();
        return p;
    }

    /** Remove these (they must have come from this store). */
    public void remove(List<Placed> gone) {
        for (Placed p : gone) {
            Map<Long, List<Placed>> w = byChunk.get(p.world());
            if (w == null) continue;
            List<Placed> l = w.get(key(p.chunkX(), p.chunkZ()));
            if (l != null && l.remove(p)) count--;
        }
        save();
    }

    public List<DecorSpec> specs(World w, int cx, int cz) {
        Map<Long, List<Placed>> m = byChunk.get(w.getName());
        if (m == null) return List.of();
        List<Placed> l = m.get(key(cx, cz));
        if (l == null) return List.of();
        List<DecorSpec> out = new ArrayList<>(l.size());
        for (Placed p : l) out.add(p.spec());
        return out;
    }

    /** Assets within {@code radius} of a point, nearest first. */
    public List<Placed> near(Location l, double radius) {
        List<Placed> out = new ArrayList<>();
        Map<Long, List<Placed>> m = byChunk.get(l.getWorld().getName());
        if (m == null) return out;
        int r = (int) Math.ceil(radius / 16) + 1, cx = l.getBlockX() >> 4, cz = l.getBlockZ() >> 4;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                List<Placed> c = m.get(key(cx + dx, cz + dz));
                if (c == null) continue;
                for (Placed p : c) if (dist2(p, l) <= radius * radius) out.add(p);
            }
        }
        out.sort((a, b) -> Double.compare(dist2(a, l), dist2(b, l)));
        return out;
    }

    public List<Placed> all(World w) {
        List<Placed> out = new ArrayList<>();
        Map<Long, List<Placed>> m = byChunk.get(w.getName());
        if (m != null) for (List<Placed> l : m.values()) out.addAll(l);
        return out;
    }

    /** Worlds that have placed assets (so the decoration manager watches them even outside the Backrooms). */
    public List<String> worlds() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Map<Long, List<Placed>>> e : byChunk.entrySet()) {
            for (List<Placed> l : e.getValue().values()) {
                if (!l.isEmpty()) {
                    out.add(e.getKey());
                    break;
                }
            }
        }
        return out;
    }

    public int count() {
        return count;
    }

    private static double dist2(Placed p, Location l) {
        double dx = p.x() - l.getX(), dy = p.y() - l.getY(), dz = p.z() - l.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
