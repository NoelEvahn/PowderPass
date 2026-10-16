package com.powderpass.backrooms.world;

import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.gen.levels.Level0Generator;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Level 0's endless vertical loop. Holes in the upper floor drop you onto the lower floor; shafts in the lower
 * floor drop you into the dark, and somewhere down there you are carried back up into a matching dark shaft above
 * the upper floor's ceiling, so you fall out of its ceiling onto the floor you started on, again and again.
 * <p>
 * The carry is a relative teleport (position, rotation and velocity all relative): the client keeps its camera
 * angle and momentum and sees no snap, and both shafts look the same from inside (dark stone, 2 x 2), under a
 * darkness effect that settles in as you fall. Falls in Level 0 never hurt.
 */
public final class Level0Loop implements Listener {
    private final BackroomsPlugin plugin;

    public Level0Loop(BackroomsPlugin plugin) {
        this.plugin = plugin;
    }

    private Level0Generator gen(Player p) {
        if (plugin.worlds().levelOf(p.getWorld()) != Level.LEVEL_0) return null;
        return plugin.worlds().generator(Level.LEVEL_0) instanceof Level0Generator g ? g : null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location to = e.getTo();
        if (to.getY() >= 40 || e.getFrom().getBlockY() == to.getBlockY() && to.getY() >= Level0Generator.LOOP_Y) return;
        Player p = e.getPlayer();
        Level0Generator g = gen(p);
        if (g == null || plugin.cutscenes().playing(p) || plugin.possessions().isVictim(p)) return;
        int x = to.getBlockX(), z = to.getBlockZ();
        boolean inShaft = g.shaftAt(x, z) || g.shaftAt((int) Math.floor(to.getX() + 0.3), z) || g.shaftAt((int) Math.floor(to.getX() - 0.3), z)
                || g.shaftAt(x, (int) Math.floor(to.getZ() + 0.3)) || g.shaftAt(x, (int) Math.floor(to.getZ() - 0.3));
        if (!inShaft && to.getY() >= Level0Generator.BOTTOM + 3) return;
        // the dark settles in as you fall
        if (to.getY() < 36 && !p.hasPotionEffect(PotionEffectType.DARKNESS)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false, false));
        }
        if (to.getY() >= Level0Generator.LOOP_Y) return;
        // carried back up into the shaft over the upper floor, keeping speed, facing and the offset in the shaft
        Location dest = to.clone();
        dest.setY(Level0Generator.loopTarget(to.getY()));
        if (!g.shaftAt(dest.getBlockX(), dest.getBlockZ())) {
            // not over a shaft column (brushing its side): the nearest one, or nothing
            int[] best = null;
            double bd = 99;
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                if (!g.shaftAt(x + dx, z + dz)) continue;
                double d = dx * dx + dz * dz;
                if (d < bd) {
                    bd = d;
                    best = new int[]{x + dx, z + dz};
                }
            }
            if (best == null) return;
            dest.setX(best[0] + 0.5);
            dest.setZ(best[1] + 0.5);
        }
        p.setFallDistance(0);
        p.teleport(dest, PlayerTeleportEvent.TeleportCause.PLUGIN, TeleportFlag.Relative.VELOCITY_X, TeleportFlag.Relative.VELOCITY_Y,
                TeleportFlag.Relative.VELOCITY_Z, TeleportFlag.Relative.YAW, TeleportFlag.Relative.PITCH,
                TeleportFlag.Relative.X, TeleportFlag.Relative.Y, TeleportFlag.Relative.Z);
        plugin.sessions().addSanity(p, -2);
    }

    /** Falls in Level 0 never hurt: the holes and shafts are a way around, not a trap. */
    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL || !(e.getEntity() instanceof Player p)) return;
        if (gen(p) != null) e.setCancelled(true);
    }
}
