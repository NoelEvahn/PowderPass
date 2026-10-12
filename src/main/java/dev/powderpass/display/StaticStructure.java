package dev.powderpass.display;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A set of entities that make up a fixed structure (lift towers, gates, kiosks...). Parts are spawned lazily
 * while a player is nearby and their chunk is loaded, and forgotten/removed otherwise, so structures never
 * keep chunks loaded and never leave entities behind.
 */
public final class StaticStructure {

    /** One entity of the structure: where it lives and how to spawn it. */
    public record Part(Location location, Function<Location, Entity> spawner) {
    }

    private final World world;
    private final List<Part> parts = new ArrayList<>();
    private Entity[] live = new Entity[0];
    private double minX = Double.MAX_VALUE;
    private double minZ = Double.MAX_VALUE;
    private double maxX = -Double.MAX_VALUE;
    private double maxZ = -Double.MAX_VALUE;

    public StaticStructure(World world) {
        this.world = world;
    }

    /** Adds a part and returns its index. Must be called before the first {@link #sync}. */
    public int add(Location location, Function<Location, Entity> spawner) {
        parts.add(new Part(location.clone(), spawner));
        live = java.util.Arrays.copyOf(live, parts.size());
        minX = Math.min(minX, location.getX());
        minZ = Math.min(minZ, location.getZ());
        maxX = Math.max(maxX, location.getX());
        maxZ = Math.max(maxZ, location.getZ());
        return parts.size() - 1;
    }

    public World world() {
        return world;
    }

    public int size() {
        return parts.size();
    }

    /** The live entity of part {@code index}, or null if it is not currently spawned. */
    public Entity live(int index) {
        Entity e = live[index];
        return e != null && e.isValid() ? e : null;
    }

    /** Horizontal distance squared from (x, z) to this structure's bounding box. */
    public double distanceSquared(double x, double z) {
        double dx = Math.max(0, Math.max(minX - x, x - maxX));
        double dz = Math.max(0, Math.max(minZ - z, z - maxZ));
        return dx * dx + dz * dz;
    }

    /** True when any player in the world is within {@code range} blocks of the structure. */
    public boolean playerNear(double range) {
        double r2 = range * range;
        for (Player p : world.getPlayers()) {
            Location l = p.getLocation();
            if (distanceSquared(l.getX(), l.getZ()) <= r2) {
                return true;
            }
        }
        return false;
    }

    /**
     * Spawns missing parts in loaded chunks when {@code active}, and removes everything when not.
     */
    public void sync(boolean active) {
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i);
            Entity e = live[i];
            boolean valid = e != null && e.isValid();
            Location loc = part.location();
            boolean loaded = world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
            if (active && loaded) {
                if (!valid) {
                    live[i] = part.spawner().apply(loc.clone());
                }
            } else if (e != null) {
                if (valid) {
                    e.remove();
                }
                live[i] = null;
            }
        }
    }

    public void despawn() {
        for (int i = 0; i < live.length; i++) {
            Entity e = live[i];
            if (e != null && e.isValid()) {
                e.remove();
            }
            live[i] = null;
        }
    }
}
