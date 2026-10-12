package com.powderpass.backrooms.player;

import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.config.BackroomsConfig;
import com.powderpass.backrooms.gen.Dest;
import com.powderpass.backrooms.gen.Feature;
import com.powderpass.backrooms.gen.LevelGenerator;
import com.powderpass.backrooms.util.Rng;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Level-to-level movement. Every transition is a physical, authored structure whose trigger volume is a pure
 * function of the layout (no block markers to break). Entering a volume plays a short "noclip" sequence and then
 * performs a safe teleport into the destination level near the corresponding coordinates.
 *
 * <p>Trigger kinds: immediate; {@code dwell} (stand still, e.g. elevators); {@code intent} (crouch into it or
 * stand against it, used by noclip seams so they are never triggered by accident); {@code slide} (a push).</p>
 */
public final class TransitionService {
    private final BackroomsPlugin plugin;
    private BukkitTask task;

    public TransitionService(BackroomsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::zonePass, 20L, 5L);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    private BackroomsConfig cfg() {
        return plugin.cfg();
    }

    /** Called on block-changing movement inside a Backrooms world. */
    public void onMove(Player p, Location to) {
        Level level = plugin.worlds().levelOf(to.getWorld());
        if (level == null || p.getGameMode() == GameMode.SPECTATOR) return;
        Session s = plugin.sessions().get(p);
        if (s.inTransition) return;
        Feature f = plugin.worlds().generator(level).triggerAt(to.getBlockX(), to.getBlockY(), to.getBlockZ());
        if (f != s.zone) {
            s.zone = f;
            s.zoneTicks = 0;
        }
        if (f == null || plugin.sessions().now() < s.cooldownUntil) return;
        String effect = f.def().effect();
        if (effect == null) {
            begin(p, level, f);
        } else if (effect.equals("slide")) {
            slide(p);
        } else if (effect.equals("intent") && p.isSneaking()) {
            begin(p, level, f);
        }
    }

    /** Dwell/intent timers (standing still fires no move events). */
    private void zonePass() {
        for (World w : plugin.worlds().worlds().values()) {
            Level level = plugin.worlds().levelOf(w);
            for (Player p : w.getPlayers()) {
                Session s = plugin.sessions().get(p);
                if (s.zone == null || s.inTransition || plugin.sessions().now() < s.cooldownUntil) continue;
                Feature now = plugin.worlds().generator(level).triggerAt(p.getLocation().getBlockX(), p.getLocation().getBlockY(), p.getLocation().getBlockZ());
                if (now != s.zone) {
                    s.zone = now;
                    s.zoneTicks = 0;
                    continue;
                }
                s.zoneTicks += 5;
                String effect = s.zone.def().effect();
                if ("dwell".equals(effect)) {
                    if (s.zoneTicks == 5) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.BLOCKS, 0.6f, 1.2f);
                    if (s.zoneTicks >= cfg().dwellTicks) begin(p, level, s.zone);
                } else if ("intent".equals(effect)) {
                    if (s.zoneTicks % 20 == 0) p.getWorld().spawnParticle(Particle.DRIPPING_WATER, p.getEyeLocation(), 4, 0.3, 0.3, 0.3);
                    if (p.isSneaking() || s.zoneTicks >= cfg().intentTicks) begin(p, level, s.zone);
                }
            }
        }
    }

    private void slide(Player p) {
        Session s = plugin.sessions().get(p);
        s.cooldownUntil = plugin.sessions().now() + 30;
        p.setVelocity(new Vector(1.15, 0.45, 0));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.PLAYERS, 0.6f, 1.4f);
    }

    /** Start the noclip sequence toward the feature's destination. */
    public void begin(Player p, Level from, Feature f) {
        Dest dest = f.dest();
        if (dest == null) return;
        Session s = plugin.sessions().get(p);
        if (!dest.isExit() && !plugin.worlds().available(dest.level())) {
            s.cooldownUntil = plugin.sessions().now() + 60;
            p.sendActionBar(cfg().raw("sealed"));
            p.setVelocity(p.getLocation().getDirection().multiply(-0.5).setY(0.2));
            return;
        }
        s.inTransition = true;
        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 50, 0, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 70, 0, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 25, 3, false, false));
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.AMBIENT, 0.5f, 0.4f);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.AMBIENT, 0.8f, 0.6f);
        p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, p.getLocation().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.02);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            if (dest.isExit()) {
                plugin.sessions().leave(p, null);
            } else {
                travel(p, from, dest.level(), f);
            }
        }, 22L);
    }

    /** Teleport into another level near the corresponding coordinates (deterministic per feature). */
    public void travel(Player p, Level from, Level to, Feature via) {
        World w = plugin.worlds().world(to);
        if (w == null) {
            plugin.sessions().get(p).inTransition = false;
            return;
        }
        LevelGenerator g = plugin.worlds().generator(to);
        long h = via == null ? Rng.hash(p.getUniqueId().getMostSignificantBits(), plugin.sessions().now()) : via.seed();
        int x = p.getLocation().getBlockX() + Rng.range(h, -48, 48);
        int z = p.getLocation().getBlockZ() + Rng.range(Rng.hash(h, 1), -48, 48);
        int[] s = g.safeSpot(x, z, 0);
        Location target = new Location(w, s[0] + 0.5, s[1], s[2] + 0.5, p.getLocation().getYaw(), 0);
        plugin.teleports().teleportSafe(p, target, l -> plugin.sessions().arrived(p, to)).thenAccept(ok -> {
            if (!ok) plugin.sessions().get(p).inTransition = false;
        });
    }

    /** Phone/radio hint: direction and distance to the nearest way onward. */
    public Component whisperHint(Player p) {
        Level level = plugin.worlds().levelOf(p.getWorld());
        if (level == null) return BackroomsConfig.mm("<gray><italic>Only static.");
        LevelGenerator g = plugin.worlds().generator(level);
        List<Feature> near = g.featuresNear(p.getLocation().getBlockX(), p.getLocation().getBlockZ(), 400);
        Feature best = null;
        double bd = Double.MAX_VALUE;
        for (Feature f : near) {
            if (!f.isTransition()) continue;
            double[] c = g.featureCenter(f);
            double d = Math.hypot(c[0] - p.getLocation().getX(), c[2] - p.getLocation().getZ());
            if (d < bd) {
                bd = d;
                best = f;
            }
        }
        if (best == null) return BackroomsConfig.mm("<gray><italic>\"...nothing near. keep walking...\"");
        double[] c = g.featureCenter(best);
        double ang = Math.toDegrees(Math.atan2(c[2] - p.getLocation().getZ(), c[0] - p.getLocation().getX()));
        String[] dirs = {"east", "south-east", "south", "south-west", "west", "north-west", "north", "north-east"};
        String dir = dirs[(int) Math.floorMod(Math.round(ang / 45.0), 8L)];
        String what = best.dest().isExit() ? "a way out" : "the way to " + best.dest().level().displayName();
        int paces = (int) Math.round(bd / 0.8 / 10.0) * 10;
        return BackroomsConfig.mm("<gray><italic>A tired voice: \"" + what + "... " + dir + "... about " + paces + " paces.\"");
    }
}
