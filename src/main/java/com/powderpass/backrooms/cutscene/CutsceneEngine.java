package com.powderpass.backrooms.cutscene;

import com.powderpass.backrooms.BackroomsPlugin;
import io.papermc.paper.entity.LookAnchor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Plays {@link Cutscene}s, one independent playback per player, with nothing but the Paper API.
 * <p>
 * <b>The camera</b> is an invisible {@link ItemDisplay} that the player rides, visible only to that player. It is
 * moved every tick along the keyframe curve with client-side teleport interpolation, so the first-person view
 * glides; riding also takes the player's movement away. Where the player looks is set with {@code lookAt}
 * (smooth enough for slow pans; shakes add small jitters to it). Vanilla clients cannot be given a real fade, so a
 * fade to black is blindness (the fog closes in over about a second) and a fade in is its removal.
 * <p>
 * <b>The world change</b> only ever happens while the screen is black: the player gets off the camera, is
 * teleported (its destination chunks were requested when the cutscene began), and gets on a new camera there.
 * <p>
 * <b>Safety</b>: players are invulnerable and hidden from others while a cutscene plays; sneaking skips it (if the
 * cutscene allows); a disconnect, death, reload or disable ends it at once with the player standing safely at the
 * destination (or back where they started, before the travel), so nobody is ever left inside a camera path.
 * Cutscenes seen before can be replaced by their short version ({@code cutscenes.replay}).
 */
public final class CutsceneEngine implements Listener {
    /** Height of the rider's eyes above the camera entity (passenger seat 0.6 below, eye height 1.62). */
    static final double EYE = 1.02;

    private final BackroomsPlugin plugin;
    private final Map<UUID, Playback> active = new HashMap<>();
    private final NamespacedKey seenKey;
    private BukkitTask task;

    public CutsceneEngine(BackroomsPlugin plugin) {
        this.plugin = plugin;
        this.seenKey = new NamespacedKey(plugin, "cutscenes_seen");
    }

    public void start() {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    /** Ends every playback now (each player put safely at their destination). */
    public void stop() {
        if (task != null) task.cancel();
        task = null;
        for (Playback pb : new ArrayList<>(active.values())) pb.end(true);
        active.clear();
    }

    public boolean playing(Player p) {
        return active.containsKey(p.getUniqueId());
    }

    public int count() {
        return active.size();
    }

    /**
     * Play a cutscene. Returns false (and plays nothing) if cutscenes are off, the player is already in one, or it
     * could not start; the caller then falls back to its plain behaviour.
     */
    public boolean play(Player p, Cutscene c) {
        if (!plugin.cfg().cutscenesEnabled || active.containsKey(p.getUniqueId()) || !p.isOnline()) return false;
        Cutscene use = c;
        if (c.shortVersion != null && seen(p, c.id)) {
            String replay = plugin.cfg().cutsceneReplay;
            if (replay.equals("first-time")) use = c.shortVersion;
        }
        Playback pb = new Playback(p, use);
        if (!pb.begin()) return false;
        active.put(p.getUniqueId(), pb);
        markSeen(p, c.id);
        return true;
    }

    private boolean seen(Player p, String id) {
        String s = p.getPersistentDataContainer().get(seenKey, PersistentDataType.STRING);
        return s != null && (";" + s + ";").contains(";" + id + ";");
    }

    private void markSeen(Player p, String id) {
        if (seen(p, id)) return;
        String s = p.getPersistentDataContainer().get(seenKey, PersistentDataType.STRING);
        p.getPersistentDataContainer().set(seenKey, PersistentDataType.STRING, s == null || s.isEmpty() ? id : s + ";" + id);
    }

    private void tick() {
        for (Playback pb : new ArrayList<>(active.values())) {
            try {
                pb.tick();
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("[Cutscene] " + pb.c.id + " for " + pb.p.getName() + " failed, ending it: " + ex);
                pb.end(true);
            }
        }
    }

    // ------------------------------------------------------------------ one playback

    private final class Playback {
        final Player p;
        final Cutscene c;
        final Location origin;
        Location dest;
        ItemDisplay cam;
        int t = -1;
        boolean travelled, ending, skip, blind;
        final List<Player> hiddenFrom = new ArrayList<>();
        double lookX = Double.NaN, lookY, lookZ;
        double yawSet = Double.NaN, pitchSet;

        Playback(Player p, Cutscene c) {
            this.p = p;
            this.c = c;
            this.origin = p.getLocation();
        }

        boolean begin() {
            if (c.destination != null) {
                dest = c.destination.apply(p);
                if (dest == null || dest.getWorld() == null) return false;
                // let the destination load while the first part plays
                World w = dest.getWorld();
                int cx = dest.getBlockX() >> 4, cz = dest.getBlockZ() >> 4;
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) w.getChunkAtAsync(cx + dx, cz + dz, true);
            }
            Location start = keyAt(0);
            if (start == null) start = p.getEyeLocation();
            if (!mount(start)) return false;
            if (c.hidden) {
                for (Player o : p.getWorld().getPlayers()) {
                    if (o == p) continue;
                    o.hidePlayer(plugin, p);
                    hiddenFrom.add(o);
                }
            }
            return true;
        }

        /** World and position of the camera (eyes) at tick t. */
        Location keyAt(int tick) {
            boolean after = travelled || (c.travelAt >= 0 && tick >= c.travelAt);
            List<Cutscene.Key> ks = new ArrayList<>();
            for (Cutscene.Key k : c.keys) if (k.afterTravel() == after) ks.add(k);
            if (ks.isEmpty()) return null;
            World w = after ? dest.getWorld() : origin.getWorld();
            double ox = after ? dest.getX() : 0, oy = after ? dest.getY() : 0, oz = after ? dest.getZ() : 0;
            int i = 0;
            while (i + 1 < ks.size() && ks.get(i + 1).t() <= tick) i++;
            Cutscene.Key a = ks.get(i);
            if (i + 1 >= ks.size() || tick <= a.t()) return new Location(w, ox + a.x(), oy + a.y(), oz + a.z());
            Cutscene.Key b = ks.get(i + 1);
            Cutscene.Key a0 = ks.get(Math.max(0, i - 1)), b1 = ks.get(Math.min(ks.size() - 1, i + 2));
            double u = (tick - a.t()) / (double) (b.t() - a.t());
            return new Location(w, ox + cr(a0.x(), a.x(), b.x(), b1.x(), u), oy + cr(a0.y(), a.y(), b.y(), b1.y(), u), oz + cr(a0.z(), a.z(), b.z(), b1.z(), u));
        }

        boolean mount(Location eye) {
            Location at = eye.clone().subtract(0, EYE, 0);
            at.setYaw(0);
            at.setPitch(0);
            if (p.isInsideVehicle()) p.leaveVehicle();
            cam = at.getWorld().spawn(at, ItemDisplay.class, d -> {
                d.setPersistent(false);
                d.setTeleportDuration(2);
                d.setViewRange(0.1f);
                try {
                    d.setVisibleByDefault(false); // empty, so invisible anyway; this also keeps it off others' clients
                } catch (RuntimeException ignored) {
                    // not supported by this server implementation
                }
                d.getPersistentDataContainer().set(com.powderpass.backrooms.display.Keys.DECOR, PersistentDataType.STRING, "cutscene_camera");
            });
            try {
                p.showEntity(plugin, cam);
            } catch (RuntimeException ignored) {
                // not supported by this server implementation
            }
            return cam.addPassenger(p);
        }

        void unmount() {
            ending = true;
            if (cam != null) {
                cam.removePassenger(p);
                if (cam.isValid()) cam.remove();
            }
            cam = null;
            ending = false;
        }

        void tick() {
            if (!p.isOnline()) {
                end(true);
                return;
            }
            t++;
            if (skip) {
                end(false);
                return;
            }
            for (Cutscene.Fade f : c.fades) {
                if (f.t() != t) continue;
                if (f.out()) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 60, 0, false, false, false));
                    blind = true;
                } else {
                    p.removePotionEffect(PotionEffectType.BLINDNESS);
                    p.removePotionEffect(PotionEffectType.DARKNESS);
                    blind = false;
                }
            }
            if (c.travelAt == t && !travelled) travel();
            for (Cutscene.Action a : c.actions) {
                if (a.t() != t) continue;
                try {
                    a.run().accept(p);
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("[Cutscene] action in " + c.id + ": " + ex);
                }
            }
            // camera
            Location eye = keyAt(t);
            if (eye != null && cam != null && cam.isValid()) {
                if (eye.getWorld() == cam.getWorld()) {
                    Location to = eye.clone().subtract(0, EYE, 0);
                    to.setYaw(0);
                    to.setPitch(0);
                    cam.teleport(to);
                }
                if (!cam.getPassengers().contains(p)) cam.addPassenger(p);
            }
            look(eye);
            if (t >= c.duration) end(false);
        }

        void look(Location eye) {
            for (Cutscene.Look l : c.looks) {
                if (l.t() != t) continue;
                if (l.angles()) {
                    yawSet = l.yawDeg();
                    pitchSet = l.pitchDeg();
                    lookX = Double.NaN;
                } else {
                    double ox = l.afterTravel() && dest != null ? dest.getX() : 0, oy = l.afterTravel() && dest != null ? dest.getY() : 0,
                            oz = l.afterTravel() && dest != null ? dest.getZ() : 0;
                    lookX = ox + l.x();
                    lookY = oy + l.y();
                    lookZ = oz + l.z();
                    yawSet = Double.NaN;
                }
            }
            if (eye == null) eye = p.getEyeLocation();
            double jitter = 0;
            for (Cutscene.Shake s : c.shakes) if (t >= s.t() && t < s.t() + s.duration()) jitter = Math.max(jitter, s.degrees() * (1 - (t - s.t()) / (double) s.duration()));
            Vector dir;
            if (!Double.isNaN(lookX)) dir = new Vector(lookX - eye.getX(), lookY - eye.getY(), lookZ - eye.getZ());
            else if (!Double.isNaN(yawSet)) {
                double y = Math.toRadians(yawSet), pi = Math.toRadians(pitchSet);
                dir = new Vector(-Math.sin(y) * Math.cos(pi), -Math.sin(pi), Math.cos(y) * Math.cos(pi));
            } else if (jitter > 0) dir = p.getEyeLocation().getDirection();
            else return;
            if (dir.lengthSquared() < 1e-6) return;
            dir.normalize();
            if (jitter > 0) {
                ThreadLocalRandom r = ThreadLocalRandom.current();
                double j = Math.toRadians(jitter);
                dir.add(new Vector(r.nextGaussian() * j, r.nextGaussian() * j, r.nextGaussian() * j)).normalize();
            }
            Location target = eye.clone().add(dir.multiply(8));
            try {
                p.lookAt(target.getX(), target.getY(), target.getZ(), LookAnchor.EYES);
            } catch (RuntimeException | AbstractMethodError ignored) {
                // not available (test server)
            }
        }

        /** The world change, behind black. */
        void travel() {
            travelled = true;
            if (dest == null) return;
            unmount();
            Location firstEye = keyAt(t);
            Location stand = firstEye != null ? firstEye.clone().subtract(0, 1.62, 0) : dest.clone();
            stand.setYaw(p.getLocation().getYaw());
            stand.setPitch(p.getLocation().getPitch());
            plugin.cutsceneTeleport(p, stand);
            if (firstEye != null) mount(firstEye);
            if (c.hidden) {
                for (Player o : hiddenFrom) if (o.isOnline()) o.showPlayer(plugin, p);
                hiddenFrom.clear();
                for (Player o : stand.getWorld().getPlayers()) {
                    if (o == p) continue;
                    o.hidePlayer(plugin, p);
                    hiddenFrom.add(o);
                }
            }
        }

        /**
         * End the cutscene: off the camera, onto safe ground at the destination (or where they started if the
         * travel never happened and there is none), visible again, sight back. {@code abrupt}: something went
         * wrong (quit, death, disable): no finish hook beyond the essentials.
         */
        void end(boolean abrupt) {
            if (!active.containsKey(p.getUniqueId()) && !abrupt) return;
            active.remove(p.getUniqueId());
            unmount();
            // skipped, aborted or finished: always onto the destination (never left mid-path), else the fallback
            Location to = dest != null && c.endAtDestination ? dest : c.fallback;
            if (to != null && p.isOnline()) {
                Location t2 = to.clone();
                t2.setYaw(p.getLocation().getYaw());
                t2.setPitch(Math.max(-30, Math.min(30, p.getLocation().getPitch())));
                plugin.cutsceneTeleport(p, t2);
            }
            p.removePotionEffect(PotionEffectType.BLINDNESS);
            p.setFallDistance(0);
            for (Player o : hiddenFrom) if (o.isOnline()) o.showPlayer(plugin, p);
            hiddenFrom.clear();
            if (c.onFinish != null && p.isOnline()) {
                try {
                    c.onFinish.accept(p);
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("[Cutscene] finish of " + c.id + ": " + ex);
                }
            }
        }
    }

    private static double cr(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        return 0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && active.containsKey(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDismount(EntityDismountEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Playback pb = active.get(p.getUniqueId());
        if (pb == null || pb.ending || e.getDismounted() != pb.cam) return;
        if (e.isCancellable()) e.setCancelled(true);
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        if (!e.isSneaking()) return;
        Playback pb = active.get(e.getPlayer().getUniqueId());
        if (pb != null && pb.c.skippable && plugin.cfg().cutscenesSkippable && pb.t > 10) pb.skip = true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Playback pb = active.get(e.getPlayer().getUniqueId());
        if (pb == null || plugin.inCutsceneTeleport(e.getPlayer())) return;
        // something else moved them (an admin, another plugin): the cutscene gives way
        if (e.getCause() != PlayerTeleportEvent.TeleportCause.DISMOUNT && e.getCause() != PlayerTeleportEvent.TeleportCause.UNKNOWN) {
            active.remove(e.getPlayer().getUniqueId());
            pb.unmount();
            e.getPlayer().removePotionEffect(PotionEffectType.BLINDNESS);
            for (Player o : pb.hiddenFrom) if (o.isOnline()) o.showPlayer(plugin, e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent e) {
        Playback pb = active.get(e.getPlayer().getUniqueId());
        if (pb != null) pb.end(true); // put down safely before their position is saved
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent e) {
        Playback pb = active.get(e.getPlayer().getUniqueId());
        if (pb != null) pb.end(true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // someone joining while others watch a cutscene does not see them floating on their cameras
        for (Playback pb : active.values()) {
            if (pb.c.hidden && pb.p.getWorld() == e.getPlayer().getWorld()) {
                e.getPlayer().hidePlayer(plugin, pb.p);
                pb.hiddenFrom.add(e.getPlayer());
            }
        }
    }

    /** Remove leftover camera entities (they are non-persistent, but crashes happen). */
    public static boolean isCamera(Entity e) {
        return e instanceof Display && "cutscene_camera".equals(e.getPersistentDataContainer().get(com.powderpass.backrooms.display.Keys.DECOR, PersistentDataType.STRING));
    }
}
