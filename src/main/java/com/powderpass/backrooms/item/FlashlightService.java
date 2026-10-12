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
 * A server-side "dynamic light": while a charged flashlight is held, an invisible light block is shown
 * (client-side only, via sendBlockChange) a few blocks ahead of the holder to them and to nearby players.
 * The world is never modified; the fake block is restored as soon as it moves or the light is put away.
 */
public final class FlashlightService {
    private final BackroomsPlugin plugin;
    private BukkitTask task;
    private int counter;

    public FlashlightService(BackroomsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::pass, 10L, 4L);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        for (Player p : plugin.getServer().getOnlinePlayers()) clear(p);
    }

    private void pass() {
        counter += 4;
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
            Location target = aim(p);
            if (target != null && !target.equals(s.fakeLight)) {
                clear(p);
                Light light = (Light) Material.LIGHT.createBlockData();
                light.setLevel(s.flashlightCharge < 30 ? 9 : 14);
                for (Player o : p.getWorld().getPlayers()) {
                    if (o.getLocation().distanceSquared(target) < 48 * 48) o.sendBlockChange(target, light);
                }
                s.fakeLight = target;
            }
        }
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

    private Location aim(Player p) {
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult hit = w.rayTraceBlocks(eye, dir, 8.0);
        double dist = hit == null ? 7.0 : Math.max(0.5, hit.getHitPosition().distance(eye.toVector()) - 0.6);
        Location l = eye.clone().add(dir.multiply(dist));
        Block b = l.getBlock();
        for (int i = 0; i < 3 && b.getType() != Material.AIR; i++) {
            l.subtract(eye.getDirection().multiply(0.5));
            b = l.getBlock();
        }
        return b.getType() == Material.AIR ? b.getLocation() : null;
    }

    public void clear(Player p) {
        Session s = plugin.sessions().get(p);
        if (s.fakeLight == null) return;
        Location l = s.fakeLight;
        s.fakeLight = null;
        if (l.getWorld() == null || !l.getWorld().isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) return;
        BlockData real = l.getBlock().getBlockData();
        for (Player o : l.getWorld().getPlayers()) {
            if (o.getLocation().distanceSquared(l) < 64 * 64) o.sendBlockChange(l, real);
        }
    }
}
