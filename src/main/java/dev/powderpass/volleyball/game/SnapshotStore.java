package dev.powderpass.volleyball.game;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Saves a player's inventory and state to disk before a match and restores it afterwards.
 * Written to disk immediately so a crash or restart never loses items.
 */
public final class SnapshotStore {

    private final File dir;
    private final Logger log;

    public SnapshotStore(File dataFolder, Logger log) {
        this.dir = new File(dataFolder, "snapshots");
        this.log = log;
    }

    private File file(UUID id) {
        return new File(dir, id + ".yml");
    }

    public boolean has(UUID id) {
        return file(id).exists();
    }

    public void save(Player p) {
        if (has(p.getUniqueId())) {
            return; // never overwrite an unrestored snapshot
        }
        if (!dir.exists() && !dir.mkdirs()) {
            log.warning("Could not create " + dir);
        }
        YamlConfiguration y = new YamlConfiguration();
        ItemStack[] contents = p.getInventory().getContents();
        List<String> items = new ArrayList<>();
        for (ItemStack it : contents) {
            items.add(it == null || it.getType().isAir() ? "" : Base64.getEncoder().encodeToString(it.serializeAsBytes()));
        }
        y.set("items", items);
        y.set("gamemode", p.getGameMode().name());
        y.set("health", p.getHealth());
        y.set("food", p.getFoodLevel());
        y.set("saturation", p.getSaturation());
        y.set("level", p.getLevel());
        y.set("exp", p.getExp());
        y.set("allow-flight", p.getAllowFlight());
        y.set("location", p.getLocation());
        try {
            y.save(file(p.getUniqueId()));
        } catch (IOException e) {
            log.log(Level.SEVERE, "Could not save snapshot of " + p.getName(), e);
        }
    }

    /**
     * Restores and deletes the snapshot.
     *
     * @param teleport return the player to where they were before the match
     */
    public boolean restore(Player p, boolean teleport) {
        File f = file(p.getUniqueId());
        if (!f.exists()) {
            return false;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        List<String> items = y.getStringList("items");
        ItemStack[] contents = new ItemStack[p.getInventory().getSize()];
        for (int i = 0; i < Math.min(items.size(), contents.length); i++) {
            String s = items.get(i);
            if (s != null && !s.isEmpty()) {
                try {
                    contents[i] = ItemStack.deserializeBytes(Base64.getDecoder().decode(s));
                } catch (Exception e) {
                    log.log(Level.WARNING, "Could not restore an item of " + p.getName(), e);
                }
            }
        }
        p.getInventory().setContents(contents);
        try {
            p.setGameMode(GameMode.valueOf(y.getString("gamemode", "SURVIVAL")));
        } catch (IllegalArgumentException e) {
            p.setGameMode(Bukkit.getDefaultGameMode());
        }
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = max == null ? 20 : max.getValue();
        p.setHealth(Math.max(1, Math.min(maxHealth, y.getDouble("health", 20))));
        p.setFoodLevel(y.getInt("food", 20));
        p.setSaturation((float) y.getDouble("saturation", 5));
        p.setLevel(y.getInt("level", 0));
        p.setExp((float) y.getDouble("exp", 0));
        p.setAllowFlight(y.getBoolean("allow-flight", false));
        Location loc = y.getLocation("location");
        if (teleport && loc != null && loc.getWorld() != null) {
            p.teleport(loc);
        }
        if (!f.delete()) {
            log.warning("Could not delete " + f);
        }
        p.updateInventory();
        return true;
    }
}
