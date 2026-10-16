package com.powderpass.backrooms.item;

import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.config.BackroomsConfig;
import com.powderpass.backrooms.gen.DecorSpec;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
 * Supplies lying in the levels (generated {@code supply:<kind>} decorations): right-click one to pick it up.
 * <ul>
 *   <li>almond water, a ration, batteries, a flashlight (charged), or a supply crate whose contents are rolled
 *   from the cache loot table;</li>
 *   <li>shared by default: once someone takes a supply it is gone for everyone (its display is hidden);
 *   {@code supplies.per-player: true} lets every player take each supply once instead;</li>
 *   <li>taken supplies are remembered across restarts in {@code data/supplies.yml};
 *   {@code supplies.respawn-minutes} (0 = never) brings shared ones back after a while.</li>
 * </ul>
 */
public final class SupplyService {
    private final BackroomsPlugin plugin;
    private final File file;
    /** world name | spec key -> time taken (ms). */
    private final Map<String, Long> shared = new HashMap<>();
    private final Map<UUID, Set<String>> mine = new HashMap<>();
    private BukkitTask task;
    private boolean dirty;

    public SupplyService(BackroomsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(new File(plugin.getDataFolder(), "data"), "supplies.yml");
    }

    private static String key(World w, String specKey) {
        return w.getName() + "|" + specKey;
    }

    public void start() {
        stop();
        load();
        // hide whatever was taken before the restart (still within its respawn time)
        for (String k : new ArrayList<>(shared.keySet())) {
            int bar = k.indexOf('|');
            World w = plugin.getServer().getWorld(k.substring(0, bar));
            if (w != null && !expired(shared.get(k))) plugin.decorations().hide(w, k.substring(bar + 1));
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1200L, 1200L);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        if (dirty) save(true);
    }

    private boolean expired(Long at) {
        int minutes = plugin.cfg().supplyRespawnMinutes;
        return at == null || minutes > 0 && System.currentTimeMillis() - at > minutes * 60_000L;
    }

    /** Every minute: supplies past their respawn time come back; changes are saved. */
    private void tick() {
        Iterator<Map.Entry<String, Long>> it = shared.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> e = it.next();
            if (!expired(e.getValue())) continue;
            it.remove();
            dirty = true;
            int bar = e.getKey().indexOf('|');
            World w = plugin.getServer().getWorld(e.getKey().substring(0, bar));
            if (w != null) plugin.decorations().unhide(w, e.getKey().substring(bar + 1));
        }
        if (dirty) save(false);
    }

    public boolean taken(Player p, World w, DecorSpec spec) {
        String k = key(w, spec.key());
        if (plugin.cfg().suppliesPerPlayer) return mine.getOrDefault(p.getUniqueId(), Set.of()).contains(k);
        return shared.containsKey(k) && !expired(shared.get(k));
    }

    /** A player picks up a supply. Returns true if they got something. */
    public boolean take(Player p, World w, DecorSpec spec) {
        if (!spec.model.startsWith("supply:")) return false;
        if (taken(p, w, spec)) {
            p.sendActionBar(BackroomsConfig.mm("<gray>You already took what was here."));
            return false;
        }
        String kind = spec.model.substring(7);
        Level level = plugin.worlds().levelOf(w);
        List<ItemStack> got = contents(kind, level);
        if (got.isEmpty()) return false;
        String k = key(w, spec.key());
        if (plugin.cfg().suppliesPerPlayer) {
            mine.computeIfAbsent(p.getUniqueId(), u -> new HashSet<>()).add(k);
        } else {
            shared.put(k, System.currentTimeMillis());
            plugin.decorations().hide(w, spec.key());
        }
        dirty = true;
        for (ItemStack s : got) {
            for (ItemStack over : p.getInventory().addItem(s).values()) w.dropItemNaturally(p.getLocation(), over);
        }
        boolean crate = kind.equals("crate");
        p.playSound(p.getLocation(), crate ? Sound.BLOCK_BARREL_OPEN : Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.8f, crate ? 0.9f : 1.1f);
        p.sendActionBar(BackroomsConfig.mm("<#e8dcb5>" + describe(kind, got)));
        return true;
    }

    private List<ItemStack> contents(String kind, Level level) {
        Items it = plugin.items();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<ItemStack> out = new ArrayList<>();
        switch (kind) {
            case "almond" -> out.add(it.almondWater());
            case "ration" -> {
                ItemStack s = it.ration();
                s.setAmount(1 + r.nextInt(2));
                out.add(s);
            }
            case "battery" -> {
                ItemStack s = it.battery();
                s.setAmount(1 + (r.nextDouble() < 0.35 ? 1 : 0));
                out.add(s);
            }
            case "flashlight" -> out.add(it.flashlight(plugin.cfg().flashlightSeconds));
            case "crate" -> {
                Inventory tmp = plugin.getServer().createInventory(null, 9);
                plugin.loot().fill(tmp, 2, level == null ? Level.LEVEL_0 : level);
                for (ItemStack s : tmp.getContents()) if (s != null && s.getType() != Material.AIR) out.add(s);
                if (out.isEmpty()) out.add(it.almondWater());
            }
            default -> {
            }
        }
        return out;
    }

    private static String describe(String kind, List<ItemStack> got) {
        return switch (kind) {
            case "almond" -> "Picked up almond water.";
            case "ration" -> "Picked up a ration.";
            case "battery" -> got.get(0).getAmount() > 1 ? "Picked up two batteries." : "Picked up a battery.";
            case "flashlight" -> "Picked up a flashlight.";
            default -> "You pry the crate open: " + got.size() + " item" + (got.size() == 1 ? "" : "s") + ".";
        };
    }

    // ------------------------------------------------------------------ persistence

    private void load() {
        shared.clear();
        mine.clear();
        File f = file.exists() ? file : new File(file.getPath() + ".bak");
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        var s = y.getConfigurationSection("shared");
        if (s != null) for (String k : s.getKeys(false)) shared.put(unescape(k), s.getLong(k));
        var p = y.getConfigurationSection("players");
        if (p != null) {
            for (String u : p.getKeys(false)) {
                try {
                    Set<String> set = new HashSet<>(p.getStringList(u));
                    mine.put(UUID.fromString(u), set);
                } catch (IllegalArgumentException ignored) {
                    // not a player id
                }
            }
        }
    }

    /** YAML section keys may not contain dots; spec keys do. */
    private static String escape(String k) {
        return k.replace(".", "~");
    }

    private static String unescape(String k) {
        return k.replace("~", ".");
    }

    private void save(boolean sync) {
        dirty = false;
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, Long> e : shared.entrySet()) y.set("shared." + escape(e.getKey()), e.getValue());
        for (Map.Entry<UUID, Set<String>> e : mine.entrySet()) y.set("players." + e.getKey(), new ArrayList<>(e.getValue()));
        String content = y.saveToString();
        Runnable write = () -> write(content);
        if (sync || !plugin.isEnabled()) write.run();
        else plugin.getServer().getScheduler().runTaskAsynchronously(plugin, write);
    }

    private synchronized void write(String content) {
        try {
            Files.createDirectories(file.getParentFile().toPath());
            File tmp = new File(file.getPath() + ".tmp");
            Files.writeString(tmp.toPath(), content);
            if (file.exists()) Files.copy(file.toPath(), new File(file.getPath() + ".bak").toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save " + file + ": " + e.getMessage());
        }
    }
}
