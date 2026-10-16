package com.powderpass.backrooms.item;

import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.config.BackroomsConfig;
import com.powderpass.backrooms.player.Session;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * A server-side "dynamic light": while a charged flashlight is held, two invisible light blocks are shown
 * (client-side only, via sendBlockChange) to the holder and nearby players: a bright one where the beam lands (up to
 * 16 blocks ahead) and a soft one just in front of them, so the light does not jump about in a single spot. They
 * follow the aim every other tick, and only blocks that actually change are sent. The world is never modified.
 * The look of the beam itself is the VHS overlay's flashlight frames (resource pack).
 */
public final class FlashlightService {
    private final BackroomsPlugin plugin;
    private BukkitTask task;
    private int counter;
    private static final int STEP = 2;

    public FlashlightService(BackroomsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::pass, 10L, STEP);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        for (Player p : plugin.getServer().getOnlinePlayers()) clear(p);
    }

    private void pass() {
        counter += STEP;
        boolean second = counter % 20 == 0;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            Session s = plugin.sessions().get(p);
            ItemStack hand = p.getInventory().getItemInMainHand();
            boolean holding = Items.FLASHLIGHT.equals(plugin.items().id(hand));
            if (!holding || !plugin.worlds().isBackrooms(p.getWorld())) {
                if (s.flashlightCharge >= 0) persist(p, s);
                clear(p);
                continue;
            }
            if (s.flashlightCharge < 0) s.flashlightCharge = plugin.items().charge(hand);
            if (s.flashlightCharge <= 0) {
                clear(p);
                if (second) p.sendActionBar(BackroomsConfig.mm("<dark_gray>The flashlight is dead."));
                continue;
            }
            if (second) {
                s.flashlightCharge--;
                if (++s.flashlightSaveIn >= 15) persist(p, s);
                if (s.flashlightCharge == 30) p.sendActionBar(BackroomsConfig.mm("<gray>The flashlight dims."));
            }
            int level = s.flashlightCharge < 30 ? 10 : 15;
            s.fakeLight = show(p, s.fakeLight, aim(p, 16.0), level);
            s.fakeSpill = show(p, s.fakeSpill, aim(p, 2.2), Math.max(6, level - 6));
        }
    }

    /** Is this player holding a flashlight that is on? */
    public boolean lit(Player p) {
        Session s = plugin.sessions().get(p);
        return s.fakeLight != null || s.fakeSpill != null;
    }

    /** Move one fake light from {@code old} to {@code target} (nothing sent when it did not move). */
    private Location show(Player p, Location old, Location target, int level) {
        if (target != null && target.equals(old)) return old;
        if (old != null) restore(old);
        if (target == null) return null;
        Light light = (Light) Material.LIGHT.createBlockData();
        light.setLevel(level);
        for (Player o : p.getWorld().getPlayers()) {
            if (o.getLocation().distanceSquared(target) < 48 * 48) o.sendBlockChange(target, light);
        }
        return target;
    }

    private void persist(Player p, Session s) {
        s.flashlightSaveIn = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && Items.FLASHLIGHT.equals(plugin.items().id(it))) {
                if (s.flashlightCharge >= 0) plugin.items().setCharge(it, s.flashlightCharge);
                break;
            }
        }
        if (!Items.FLASHLIGHT.equals(plugin.items().id(p.getInventory().getItemInMainHand()))) s.flashlightCharge = -1;
    }

    private Location aim(Player p, double reach) {
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult hit = w.rayTraceBlocks(eye, dir, reach);
        double dist = hit == null ? reach - 1 : Math.max(0.5, hit.getHitPosition().distance(eye.toVector()) - 0.6);
        Location l = eye.clone().add(dir.clone().multiply(dist));
        Block b = l.getBlock();
        for (int i = 0; i < 3 && b.getType() != Material.AIR; i++) {
            l.subtract(dir.clone().multiply(0.5));
            b = l.getBlock();
        }
        return b.getType() == Material.AIR ? b.getLocation() : null;
    }

    public void clear(Player p) {
        Session s = plugin.sessions().get(p);
        if (s.fakeLight != null) restore(s.fakeLight);
        if (s.fakeSpill != null) restore(s.fakeSpill);
        s.fakeLight = null;
        s.fakeSpill = null;
    }

    private void restore(Location l) {
        if (l.getWorld() == null || !l.getWorld().isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) return;
        BlockData real = l.getBlock().getBlockData();
        for (Player o : l.getWorld().getPlayers()) {
            if (o.getLocation().distanceSquared(l) < 64 * 64) o.sendBlockChange(l, real);
        }
    }
}
