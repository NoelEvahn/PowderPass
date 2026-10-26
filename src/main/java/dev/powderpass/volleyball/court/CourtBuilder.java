package dev.powderpass.volleyball.court;

import dev.powderpass.volleyball.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Places the court floor and keeps an undo file with the original blocks so removing a court restores the world.
 */
public final class CourtBuilder {

    private final File undoDir;
    private final Logger log;

    public CourtBuilder(File dataFolder, Logger log) {
        this.undoDir = new File(dataFolder, "undo");
        this.log = log;
    }

    /** Places floor blocks for the court and free zone, saving originals. Returns the number of blocks changed. */
    public int placeFloor(Court court, Settings settings) {
        World world = court.world();
        if (world == null) {
            return 0;
        }
        Settings.CourtDefaults cd = settings.courtDefaults;
        Material inside = court.beach() ? cd.beachFloor() : cd.courtFloor();
        Material outside = court.beach() ? cd.beachFreeZoneFloor() : cd.freeZoneFloor();
        int floorY = (int) Math.floor(court.centerY()) - 1;
        double ext = court.freeZone();
        double hu = court.halfLength() + ext;
        double hw = court.halfWidth() + ext;

        List<String> undo = new ArrayList<>();
        int changed = 0;
        // world bounding box of the area
        double[] xs = new double[4];
        double[] zs = new double[4];
        int n = 0;
        for (int su = -1; su <= 1; su += 2) {
            for (int sw = -1; sw <= 1; sw += 2) {
                var v = court.frame().toWorld(su * hu, 0, sw * hw, new dev.powderpass.volleyball.math.Vec3());
                xs[n] = v.x;
                zs[n] = v.z;
                n++;
            }
        }
        int minX = (int) Math.floor(min(xs));
        int maxX = (int) Math.ceil(max(xs));
        int minZ = (int) Math.floor(min(zs));
        int maxZ = (int) Math.ceil(max(zs));
        for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z < maxZ; z++) {
                double u = court.frame().toU(x + 0.5, z + 0.5);
                double w = court.frame().toW(x + 0.5, z + 0.5);
                if (Math.abs(u) > hu || Math.abs(w) > hw) {
                    continue;
                }
                boolean in = Math.abs(u) < court.halfLength() && Math.abs(w) < court.halfWidth();
                Block b = world.getBlockAt(x, floorY, z);
                Material target = in ? inside : outside;
                undo.add(x + "," + floorY + "," + z + "," + b.getBlockData().getAsString());
                if (b.getType() != target) {
                    b.setType(target, false);
                    changed++;
                }
                // clear a little headroom (grass, flowers, snow) so the floor is playable
                Block above = b.getRelative(0, 1, 0);
                if (!above.getType().isAir() && !above.getType().isCollidable()) {
                    undo.add(x + "," + (floorY + 1) + "," + z + "," + above.getBlockData().getAsString());
                    above.setType(Material.AIR, false);
                }
            }
        }
        saveUndo(court.name(), undo);
        court.setFloorPlaced(true);
        return changed;
    }

    /** Restores blocks recorded when the floor was placed. */
    public int restoreFloor(Court court) {
        File f = undoFile(court.name());
        if (!f.exists()) {
            return 0;
        }
        World world = court.world();
        if (world == null) {
            return 0;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        List<String> entries = y.getStringList("blocks");
        int restored = 0;
        // reverse so headroom entries recorded after floor entries are restored last-in-first-out
        for (int i = entries.size() - 1; i >= 0; i--) {
            String e = entries.get(i);
            String[] parts = e.split(",", 4);
            if (parts.length < 4) {
                continue;
            }
            try {
                int x = Integer.parseInt(parts[0]);
                int yy = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);
                BlockData data = Bukkit.createBlockData(parts[3]);
                world.getBlockAt(x, yy, z).setBlockData(data, false);
                restored++;
            } catch (IllegalArgumentException ex) {
                log.warning("Skipping bad undo entry: " + e);
            }
        }
        if (!f.delete()) {
            log.warning("Could not delete " + f);
        }
        court.setFloorPlaced(false);
        return restored;
    }

    public void deleteUndo(String court) {
        File f = undoFile(court);
        if (f.exists() && !f.delete()) {
            log.warning("Could not delete " + f);
        }
    }

    private void saveUndo(String court, List<String> entries) {
        if (!undoDir.exists() && !undoDir.mkdirs()) {
            log.warning("Could not create " + undoDir);
        }
        File f = undoFile(court);
        // keep the oldest snapshot if the floor is rebuilt, so the original terrain is never lost
        if (f.exists()) {
            return;
        }
        YamlConfiguration y = new YamlConfiguration();
        y.set("blocks", entries);
        try {
            y.save(f);
        } catch (IOException e) {
            log.log(Level.SEVERE, "Could not save undo data for court " + court, e);
        }
    }

    private File undoFile(String court) {
        return new File(undoDir, court.toLowerCase() + ".yml");
    }

    private static double min(double[] a) {
        double m = a[0];
        for (double v : a) {
            m = Math.min(m, v);
        }
        return m;
    }

    private static double max(double[] a) {
        double m = a[0];
        for (double v : a) {
            m = Math.max(m, v);
        }
        return m;
    }
}
