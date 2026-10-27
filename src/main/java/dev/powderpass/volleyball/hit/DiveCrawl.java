package dev.powderpass.volleyball.hit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Puts a diving player into the crawl pose for real: a client-side barrier above their head makes their own client
 * drop into the swimming pose (lower camera), and a fixed server pose shows the same dive to everyone else.
 */
final class DiveCrawl {

    private static final BlockData BARRIER = Material.BARRIER.createBlockData();

    private static final class Dive {
        long until;
        Location fake;
    }

    private final Map<UUID, Dive> dives = new HashMap<>();

    void start(Player p, long now, int ticks) {
        Dive d = dives.computeIfAbsent(p.getUniqueId(), k -> new Dive());
        d.until = now + ticks;
        p.setPose(Pose.SWIMMING, true);
        place(p, d);
    }

    boolean diving(Player p) {
        return dives.containsKey(p.getUniqueId());
    }

    void tick(long now) {
        Iterator<Map.Entry<UUID, Dive>> it = dives.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Dive> e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            Dive d = e.getValue();
            if (p == null) {
                it.remove();
                continue;
            }
            // stay down until the timer ends and the player is back on the floor
            if (now >= d.until && p.isOnGround()) {
                end(p, d);
                it.remove();
            } else {
                place(p, d);
            }
        }
    }

    void shutdown() {
        for (Map.Entry<UUID, Dive> e : dives.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) {
                end(p, e.getValue());
            }
        }
        dives.clear();
    }

    private static void place(Player p, Dive d) {
        Location head = p.getLocation().getBlock().getLocation().add(0, 1, 0);
        if (d.fake != null && d.fake.equals(head)) {
            return;
        }
        restore(p, d);
        if (head.getBlock().getType().isAir()) {
            p.sendBlockChange(head, BARRIER);
            d.fake = head;
        }
    }

    private static void restore(Player p, Dive d) {
        if (d.fake != null) {
            p.sendBlockChange(d.fake, d.fake.getBlock().getBlockData());
            d.fake = null;
        }
    }

    private static void end(Player p, Dive d) {
        restore(p, d);
        p.setPose(Pose.STANDING, false);
    }
}
