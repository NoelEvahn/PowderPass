package com.powderpass.backrooms.world;

import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.gen.LevelGenerator;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Safe teleports: the destination chunk is loaded asynchronously, the landing spot is validated against real
 * blocks on the main thread (feet + head free, solid non-hazardous floor, not inside a transition trigger), and a
 * nearby safe spot is searched if needed. Never teleports into unloaded chunks, solid blocks, liquids or voids.
 */
public final class TeleportService {
    private static final Set<Material> HAZARDS = Set.of(Material.MAGMA_BLOCK, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.FIRE,
            Material.SOUL_FIRE, Material.CACTUS, Material.LAVA, Material.POWDER_SNOW, Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE,
            Material.POINTED_DRIPSTONE);
    private final BackroomsPlugin plugin;

    public TeleportService(BackroomsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isSafe(Block feet) {
        World w = feet.getWorld();
        if (feet.getY() <= w.getMinHeight() || feet.getY() >= w.getMaxHeight() - 2) return false;
        Block head = feet.getRelative(0, 1, 0), below = feet.getRelative(0, -1, 0);
        if (!feet.isPassable() || !head.isPassable()) return false;
        if (feet.isLiquid() || head.isLiquid()) return false;
        Material b = below.getType();
        if (!b.isSolid() || HAZARDS.contains(b) || HAZARDS.contains(feet.getType())) return false;
        Level l = plugin.worlds().levelOf(w);
        if (l != null) {
            LevelGenerator g = plugin.worlds().generator(l);
            return g.triggerAt(feet.getX(), feet.getY(), feet.getZ()) == null;
        }
        return true;
    }

    /** Spiral search for a safe feet block around {@code l} inside loaded chunks. */
    public Location findSafe(Location l, int radius) {
        World w = l.getWorld();
        int bx = l.getBlockX(), by = l.getBlockY(), bz = l.getBlockZ();
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = bx + dx, z = bz + dz;
                    if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
                    for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3, 4, -4}) {
                        Block b = w.getBlockAt(x, by + dy, z);
                        if (isSafe(b)) {
                            Location out = b.getLocation().add(0.5, 0, 0.5);
                            out.setYaw(l.getYaw());
                            out.setPitch(l.getPitch());
                            return out;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Loads the target chunk(s) asynchronously, validates/adjusts the spot, then teleports. If no safe spot exists
     * near the target and the world is a Backrooms level, the generator's guaranteed safe cell is used instead.
     */
    public CompletableFuture<Boolean> teleportSafe(Player p, Location target, Consumer<Location> after) {
        return teleportSafe(p, target, after, true);
    }

    private CompletableFuture<Boolean> teleportSafe(Player p, Location target, Consumer<Location> after, boolean allowFallback) {
        World w = target.getWorld();
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        if (w == null) {
            result.complete(false);
            return result;
        }
        int cx = target.getBlockX() >> 4, cz = target.getBlockZ() >> 4;
        CompletableFuture<?>[] loads = new CompletableFuture<?>[9];
        int i = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) loads[i++] = w.getChunkAtAsync(cx + dx, cz + dz, true);
        CompletableFuture.allOf(loads).whenComplete((v, err) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (err != null || !p.isOnline()) {
                result.complete(false);
                return;
            }
            Location safe = findSafe(target, 8);
            if (safe == null) {
                Level l = plugin.worlds().levelOf(w);
                if (l != null) {
                    if (allowFallback) {
                        LevelGenerator g = plugin.worlds().generator(l);
                        int[] s = g.safeSpot(target.getBlockX(), target.getBlockZ(), g.floorAt(target.getBlockY()));
                        Location alt = new Location(w, s[0] + 0.5, s[1], s[2] + 0.5, target.getYaw(), target.getPitch());
                        teleportSafe(p, alt, after, false).thenAccept(result::complete);
                        return;
                    }
                } else {
                    Location top = w.getHighestBlockAt(target).getLocation().add(0.5, 1, 0.5);
                    safe = isSafe(top.getBlock()) ? top : w.getSpawnLocation();
                }
            }
            if (safe == null) {
                plugin.getLogger().warning("No safe landing spot near " + target + "; teleport aborted for " + p.getName());
                result.complete(false);
                return;
            }
            Location dest = safe;
            p.setFallDistance(0);
            p.teleportAsync(dest, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(ok -> {
                if (ok) {
                    p.setFallDistance(0);
                    p.setFireTicks(0);
                    if (after != null) after.accept(dest);
                }
                result.complete(ok);
            });
        }));
        return result;
    }
}
