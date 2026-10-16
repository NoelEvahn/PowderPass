package com.powderpass.backrooms.world;

import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.display.AssetStore;
import com.powderpass.backrooms.display.DisplayFactory;
import com.powderpass.backrooms.display.Model;
import com.powderpass.backrooms.display.Part;
import com.powderpass.backrooms.display.SlideModels;
import com.powderpass.backrooms.gen.DecorSpec;
import com.powderpass.backrooms.gen.LevelGenerator;
import com.powderpass.backrooms.gen.SlidePath;
import com.powderpass.backrooms.gen.levels.PoolroomsGenerator;
import net.kyori.adventure.key.Key;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Riding water slides. A rider sits in an inflatable ring (display entities) carried by a seat entity, and the seat
 * is moved along the slide's exact path every tick, with client-side interpolation so it glides. Speed comes from
 * the slope: gravity along the chute, friction (low on wet slides and inside tubes) and air drag, so steep drops and
 * helixes pick up speed and the runout slows you before you are thrown out over the landing pool.
 *
 * <p>Slides: the generated ones and the massive halls' chutes (walk onto the start), and placed slide assets (click
 * the ladder or the deck). Fall, suffocation and wall damage are cancelled while riding and for a few seconds after;
 * a ride ends cleanly on quit, death, world change, a teleport from elsewhere, or when the plugin stops.</p>
 */
public final class SlideRides implements Listener {
    /**
     * Along-the-chute gravity (blocks/tick^2; about half a falling player's, so rides land at 8-15 m/s like real
     * slides), air drag, and speed limits in blocks/tick.
     */
    private static final double G = 0.045, DRAG = 0.04, MIN_SPEED = 0.12, MAX_SPEED = 0.8, START_SPEED = 0.2;
    private static final double SEAT = 0.15;
    private static final long GRACE_MILLIS = 4000, COOLDOWN_MILLIS = 2500;

    /**
     * A path to ride, in world space (the last two points are the throw over the pool). {@code seat} = height of the
     * ring above the path: generated and placed slides give the bed's centre line, the massive halls' tiled chutes
     * give the rider's feet, already above the bed.
     */
    public record Track(World world, double[][] pts, boolean wet, boolean enclosed, double[] exitDir, String name, double seat) {
    }

    private static final class Ride {
        final Track track;
        final double[] cum;
        final ItemDisplay seat;
        final List<Entity> ring = new ArrayList<>();
        double s, v = START_SPEED;
        int seg, ticks;
        boolean ending;

        Ride(Track track, ItemDisplay seat) {
            this.track = track;
            this.seat = seat;
            double[][] p = track.pts();
            cum = new double[p.length];
            for (int i = 1; i < p.length; i++) cum[i] = cum[i - 1] + dist(p[i - 1], p[i]);
        }

        double length() {
            return cum[cum.length - 1];
        }

        /** Advance the segment pointer to the one containing arc length s. */
        void locate() {
            while (seg < cum.length - 2 && cum[seg + 1] < s) seg++;
        }

        double[] at() {
            double[][] p = track.pts();
            double t = (s - cum[seg]) / Math.max(1e-6, cum[seg + 1] - cum[seg]);
            t = Math.max(0, Math.min(1, t));
            return new double[]{p[seg][0] + (p[seg + 1][0] - p[seg][0]) * t, p[seg][1] + (p[seg + 1][1] - p[seg][1]) * t,
                    p[seg][2] + (p[seg + 1][2] - p[seg][2]) * t};
        }

        /** Sine of the slope of the current segment (positive = downhill). */
        double slope() {
            double[][] p = track.pts();
            double len = Math.max(1e-6, cum[seg + 1] - cum[seg]);
            return (p[seg][1] - p[seg + 1][1]) / len;
        }

        float yaw() {
            double[][] p = track.pts();
            return (float) Math.toDegrees(Math.atan2(-(p[seg + 1][0] - p[seg][0]), p[seg + 1][2] - p[seg][2]));
        }
    }

    private final BackroomsPlugin plugin;
    private final Map<UUID, Ride> rides = new HashMap<>();
    private final Map<UUID, Long> grace = new HashMap<>();
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private final Set<UUID> releasing = new HashSet<>();
    private BukkitTask task;

    public SlideRides(BackroomsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        for (UUID id : new ArrayList<>(rides.keySet())) {
            Player p = plugin.getServer().getPlayer(id);
            end(id, p, false);
        }
    }

    public boolean riding(Player p) {
        return rides.containsKey(p.getUniqueId());
    }

    public int riders() {
        return rides.size();
    }

    // ------------------------------------------------------------------ finding slides

    private PoolroomsGenerator poolrooms(World w) {
        if (w == null || plugin.worlds() == null || plugin.worlds().levelOf(w) != Level.POOLROOMS) return null;
        LevelGenerator g = plugin.worlds().generator(Level.POOLROOMS);
        return g instanceof PoolroomsGenerator pg ? pg : null;
    }

    public static Track track(World w, PoolroomsGenerator.Slide s, String name) {
        return new Track(w, s.points(), s.wet(), "enclosed".equals(s.style()), s.exitDir(), name, s.style() == null ? -0.6 : SEAT);
    }

    /** The ride along a placed slide asset ({@code slide:...} spec), or null if the spec is not a slide. */
    public static Track track(World w, DecorSpec spec) {
        SlidePath path = SlidePath.fromId(spec.model, spec.seed);
        if (path == null) return null;
        double[][] bed = path.world(spec.x, spec.y, spec.z, spec.yaw);
        int n = bed.length;
        double ex = bed[n - 1][0] - bed[n - 2][0], ez = bed[n - 1][2] - bed[n - 2][2], el = Math.max(1e-6, Math.hypot(ex, ez));
        double[][] pts = new double[n + 2][];
        System.arraycopy(bed, 0, pts, 0, n);
        pts[n] = new double[]{bed[n - 1][0] + ex / el * 1.5, bed[n - 1][1] - 0.2, bed[n - 1][2] + ez / el * 1.5};
        pts[n + 1] = new double[]{bed[n - 1][0] + ex / el * 3.0, bed[n - 1][1] - 0.6, bed[n - 1][2] + ez / el * 3.0};
        return new Track(w, pts, path.wet, path.style == SlidePath.Style.ENCLOSED, new double[]{ex / el, 0, ez / el}, spec.model, SEAT);
    }

    /** Every slide whose start is within {@code radius} of a point: generated, massive-hall and placed ones. */
    public List<Track> near(Location l, double radius) {
        List<Track> out = new ArrayList<>();
        World w = l.getWorld();
        PoolroomsGenerator g = poolrooms(w);
        if (g != null) {
            int r = (int) Math.ceil(radius / 60) + 1, rx = Math.floorDiv(l.getBlockX(), 60), rz = Math.floorDiv(l.getBlockZ(), 60);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    PoolroomsGenerator.Slide hall = g.slide(rx + dx, rz + dz);
                    if (hall != null) out.add(track(w, hall, "massive hall slide"));
                    int k = 0;
                    for (PoolroomsGenerator.Slide s : g.generatedSlides(rx + dx, rz + dz)) {
                        double[][] p = s.points();
                        boolean storey = p[0][1] - p[p.length - 1][1] > 20;
                        out.add(track(w, s, (s.style() + (s.wet() ? "" : " dry") + (storey ? " storey" : " fun") + " slide #" + (rx + dx) + "," + (rz + dz) + "/" + k++)));
                    }
                }
            }
        }
        if (plugin.assets() != null) {
            for (AssetStore.Placed a : plugin.assets().near(l, radius + 30)) {
                if (!a.model().startsWith("slide:")) continue;
                Track t = track(w, a.spec());
                if (t != null) out.add(new Track(w, t.pts(), t.wet(), t.enclosed(), t.exitDir(), "placed " + a.model(), t.seat()));
            }
        }
        out.removeIf(t -> dist(t.pts()[0], new double[]{l.getX(), l.getY(), l.getZ()}) > radius);
        out.sort((a, b) -> Double.compare(dist(a.pts()[0], new double[]{l.getX(), l.getY(), l.getZ()}),
                dist(b.pts()[0], new double[]{l.getX(), l.getY(), l.getZ()})));
        return out;
    }

    // ------------------------------------------------------------------ starting

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location to = e.getTo();
        if (to.getBlockX() == e.getFrom().getBlockX() && to.getBlockY() == e.getFrom().getBlockY() && to.getBlockZ() == e.getFrom().getBlockZ()) return;
        Player p = e.getPlayer();
        if (rides.containsKey(p.getUniqueId()) || p.getGameMode() == GameMode.SPECTATOR || p.isFlying() || p.isInsideVehicle()) return;
        Long cd = cooldown.get(p.getUniqueId());
        if (cd != null && System.currentTimeMillis() < cd) return;
        PoolroomsGenerator g = poolrooms(to.getWorld());
        if (g == null) return;
        PoolroomsGenerator.Slide s = g.slideStartingAt(to.getBlockX(), to.getBlockY(), to.getBlockZ());
        if (s != null) begin(p, track(to.getWorld(), s, "slide"));
    }

    /** Clicked a placed slide's ladder or deck. */
    public void rideAsset(Player p, DecorSpec spec, World w) {
        Track t = track(w, spec);
        if (t != null && !riding(p)) begin(p, t);
    }

    /** Start a ride: the ring appears at the top of the slide and the player sits in it. */
    public boolean begin(Player p, Track t) {
        if (rides.containsKey(p.getUniqueId()) || p.isDead() || p.getGameMode() == GameMode.SPECTATOR || p.getWorld() != t.world()) return false;
        if (p.isInsideVehicle()) p.leaveVehicle();
        double[] a = t.pts()[0];
        World w = t.world();
        Location start = new Location(w, a[0], a[1] + t.seat(), a[2], 0, 0);
        ItemDisplay seat = w.spawn(start, ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.setTeleportDuration(2);
            e.getPersistentDataContainer().set(com.powderpass.backrooms.display.Keys.DECOR, PersistentDataType.STRING, "slide_seat");
        });
        Ride r = new Ride(t, seat);
        Model ring = SlideModels.ride(p.getUniqueId().getLeastSignificantBits());
        for (Part part : ring.parts) {
            Entity e = DisplayFactory.spawn(w, start, part, part.local, 1.0f, "decor", "slide_ring");
            seat.addPassenger(e);
            r.ring.add(e);
        }
        if (!seat.addPassenger(p)) {
            cleanup(r);
            return false;
        }
        rides.put(p.getUniqueId(), r);
        p.setFallDistance(0);
        playCustom(p, "slide.rush", Sound.ITEM_ELYTRA_FLYING, 0.9f, 1.0f, 0.3f, 1.7f);
        w.playSound(start, t.wet() ? Sound.ENTITY_PLAYER_SPLASH : Sound.ITEM_ARMOR_EQUIP_LEATHER, SoundCategory.PLAYERS, 0.6f, 1.3f);
        if (t.wet()) w.spawnParticle(Particle.SPLASH, start, 20, 0.3, 0.1, 0.3, 0.1);
        return true;
    }

    // ------------------------------------------------------------------ the ride

    private void tick() {
        long now = System.currentTimeMillis();
        grace.values().removeIf(t -> now - t > GRACE_MILLIS);
        if (rides.isEmpty()) return;
        for (Iterator<Map.Entry<UUID, Ride>> it = rides.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Ride> en = it.next();
            Ride r = en.getValue();
            Player p = plugin.getServer().getPlayer(en.getKey());
            if (p == null || !p.isOnline() || p.isDead() || p.getWorld() != r.track.world() || !r.seat.isValid()
                    || !r.seat.getPassengers().contains(p) || r.ticks > 20 * 90) {
                it.remove();
                finish(en.getKey(), p, r, false);
                continue;
            }
            r.ticks++;
            // momentum: gravity along the slope, friction against the bed, air drag
            r.locate();
            double sin = r.slope(), cos = Math.sqrt(Math.max(0, 1 - sin * sin));
            double mu = (r.track.wet() ? 0.03 : 0.09) * (r.track.enclosed() ? 0.85 : 1.0);
            boolean flying = r.seg >= r.track.pts().length - 3; // the throw over the pool
            double a = G * sin - (flying ? 0 : mu * G * cos) - DRAG * r.v * r.v;
            r.v = Math.max(MIN_SPEED, Math.min(MAX_SPEED, r.v + a));
            r.s += r.v;
            if (r.s >= r.length()) {
                it.remove();
                finish(en.getKey(), p, r, true);
                continue;
            }
            r.locate();
            double[] q = r.at();
            Location to = new Location(r.track.world(), q[0], q[1] + r.track.seat(), q[2], r.yaw(), 0);
            r.seat.teleport(to); // Paper keeps passengers on entity teleports
            p.setFallDistance(0);
            if (r.track.wet() && r.ticks % 2 == 0) {
                r.track.world().spawnParticle(Particle.SPLASH, to.clone().add(0, 0.1, 0), (int) (3 + r.v * 8), 0.25, 0.05, 0.25, 0.05);
            }
            if (r.ticks % 12 == 0) {
                p.playSound(to, Sound.ITEM_ELYTRA_FLYING, SoundCategory.PLAYERS, (float) Math.min(0.6, 0.1 + r.v * 0.5), (float) (0.8 + r.v * 0.6));
            }
        }
    }

    private void finish(UUID id, Player p, Ride r, boolean thrown) {
        r.ending = true;
        releasing.add(id);
        try {
            if (p != null && r.seat.isValid()) r.seat.removePassenger(p);
        } finally {
            releasing.remove(id);
        }
        cleanup(r);
        grace.put(id, System.currentTimeMillis());
        cooldown.put(id, System.currentTimeMillis() + COOLDOWN_MILLIS);
        if (p == null || !p.isOnline()) return;
        p.setFallDistance(0);
        if (!thrown) return;
        double[] d = r.track.exitDir();
        double sp = Math.min(0.6, r.v); // carries them a few blocks out over the landing pool
        p.setVelocity(new Vector(d[0] * sp, 0.12, d[2] * sp));
        // the splash as they hit the water, a few ticks later
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            p.setFallDistance(0);
            Location l = p.getLocation();
            l.getWorld().spawnParticle(Particle.SPLASH, l, 60, 0.8, 0.2, 0.8, 0.3);
            l.getWorld().spawnParticle(Particle.BUBBLE, l, 30, 0.6, 0.4, 0.6, 0.1);
            playCustom(p, "water.splash", Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.0f, 1.0f, 0.9f, 1.0f);
        }, 6L);
    }

    private void end(UUID id, Player p, boolean thrown) {
        Ride r = rides.remove(id);
        if (r != null) finish(id, p, r, thrown);
    }

    private static void cleanup(Ride r) {
        for (Entity e : r.ring) if (e.isValid()) e.remove();
        r.ring.clear();
        if (r.seat.isValid()) {
            r.seat.eject();
            r.seat.remove();
        }
    }

    // ------------------------------------------------------------------ guards

    @EventHandler(ignoreCancelled = true)
    public void onDismount(EntityDismountEvent e) {
        UUID id = e.getEntity().getUniqueId();
        Ride r = rides.get(id);
        if (r != null && !r.ending && !releasing.contains(id) && e.getDismounted() == r.seat) e.setCancelled(true); // no bailing out mid-slide
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        UUID id = p.getUniqueId();
        boolean riding = rides.containsKey(id);
        Long t = grace.get(id);
        boolean graced = t != null && System.currentTimeMillis() - t < GRACE_MILLIS;
        switch (e.getCause()) {
            case FALL -> {
                if (riding || graced) e.setCancelled(true);
            }
            case SUFFOCATION, FLY_INTO_WALL -> {
                if (riding) e.setCancelled(true);
            }
            default -> {
            }
        }
    }

    /** Teleported away by something else (a command, a transition): the ride is over. Moves with the seat are ignored. */
    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        Ride r = rides.get(id);
        if (r == null || releasing.contains(id)) return;
        Location to = e.getTo();
        if (to.getWorld() != r.seat.getWorld() || to.distanceSquared(r.seat.getLocation()) > 16) end(id, e.getPlayer(), false);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        forget(e.getEntity());
    }

    /** Off any slide, now (death, or the Backrooms death screen). */
    public void forget(Player p) {
        end(p.getUniqueId(), p, false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        end(id, e.getPlayer(), false);
        grace.remove(id);
        cooldown.remove(id);
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        end(e.getPlayer().getUniqueId(), e.getPlayer(), false);
    }

    // ------------------------------------------------------------------ sound

    /** Custom pack sound when this player has the pack and the id exists, else a vanilla stand-in. */
    private void playCustom(Player p, String id, Sound fallback, float vol, float pitch, float fbVol, float fbPitch) {
        String event = null;
        try {
            if (plugin.pack() != null && plugin.pack().loaded(p) && plugin.audio() != null) event = plugin.audio().index().resolve(id, null, false);
        } catch (RuntimeException ignored) {
            event = null;
        }
        if (event != null) {
            p.playSound(net.kyori.adventure.sound.Sound.sound(Key.key("backrooms", event), net.kyori.adventure.sound.Sound.Source.PLAYER, vol, pitch),
                    net.kyori.adventure.sound.Sound.Emitter.self());
        } else {
            p.playSound(p.getLocation(), fallback, SoundCategory.PLAYERS, fbVol, fbPitch * (0.95f + ThreadLocalRandom.current().nextFloat() * 0.1f));
        }
    }

    private static double dist(double[] a, double[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
