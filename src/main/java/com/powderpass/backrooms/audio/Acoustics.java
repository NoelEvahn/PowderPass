package com.powderpass.backrooms.audio;

import com.powderpass.backrooms.Level;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Cheap acoustic approximations. A vanilla client cannot run reverb or filters chosen by a server, so the pack
 * carries pre-reverberated variants per room profile and pre-filtered "muffled" variants, and this class only
 * decides which one fits. Nothing here is physically accurate; it is bounded and inexpensive.
 */
final class Acoustics {
    private Acoustics() {
    }

    /**
     * Room profile for a listener: the level's character, refined by how far the listener can "hear" horizontally
     * (four bounded probes at head height) and how high the ceiling is.
     */
    static String profile(Level level, Player p) {
        if (level == null) return null;
        if (level == Level.PLAINS) return "open";
        if (level == Level.POOLROOMS) return "pool";
        Location eye = p.getEyeLocation();
        double open = (free(eye, 1, 0, 16) + free(eye, -1, 0, 16) + free(eye, 0, 1, 16) + free(eye, 0, -1, 16)) / 4.0;
        double narrow = Math.min(free(eye, 1, 0, 16) + free(eye, -1, 0, 16), free(eye, 0, 1, 16) + free(eye, 0, -1, 16));
        int ceiling = up(eye, 12);
        return switch (level) {
            case LEVEL_1 -> narrow < 4 ? "corridor" : "concrete";
            case LEVEL_4 -> narrow < 4 ? "corridor" : open < 5 ? "carpet" : "office";
            default -> ceiling >= 6 || open > 9 ? "office" : narrow < 3 ? "corridor" : "carpet";
        };
    }

    /** Free blocks from the eye in one horizontal direction, up to {@code max}. */
    private static int free(Location eye, int dx, int dz, int max) {
        World w = eye.getWorld();
        int x = eye.getBlockX(), y = eye.getBlockY(), z = eye.getBlockZ();
        for (int i = 1; i <= max; i++) {
            if (!w.isChunkLoaded((x + dx * i) >> 4, (z + dz * i) >> 4)) return i;
            if (w.getBlockAt(x + dx * i, y, z + dz * i).getType().isOccluding()) return i - 1;
        }
        return max;
    }

    private static int up(Location eye, int max) {
        World w = eye.getWorld();
        for (int i = 1; i <= max; i++) {
            if (w.getBlockAt(eye.getBlockX(), eye.getBlockY() + i, eye.getBlockZ()).getType().isOccluding()) return i;
        }
        return max;
    }

    /**
     * Occluding blocks between two points (0..3), marched in half-block steps over at most {@code maxDistance}
     * blocks. Unloaded chunks and anything beyond range count as one obstruction. Water counts as half.
     */
    static int obstruction(Location from, Location to, double maxDistance) {
        if (from.getWorld() != to.getWorld()) return 3;
        Vector d = to.toVector().subtract(from.toVector());
        double len = d.length();
        if (len < 0.5) return 0;
        if (len > maxDistance) return 1;
        d.multiply(0.5 / len);
        World w = from.getWorld();
        Location c = from.clone();
        int lastX = Integer.MIN_VALUE, lastY = 0, lastZ = 0;
        double hits = 0;
        for (double t = 0; t < len - 0.5; t += 0.5) {
            c.add(d);
            int bx = c.getBlockX(), by = c.getBlockY(), bz = c.getBlockZ();
            if (bx == lastX && by == lastY && bz == lastZ) continue;
            lastX = bx;
            lastY = by;
            lastZ = bz;
            if (!w.isChunkLoaded(bx >> 4, bz >> 4)) return Math.max(1, (int) hits);
            Material m = w.getBlockAt(bx, by, bz).getType();
            if (m.isOccluding()) hits += 1;
            else if (m == Material.WATER) hits += 0.5;
            if (hits >= 3) return 3;
        }
        return (int) hits;
    }
}
