package com.powderpass.backrooms.creature;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * What a creature knows about the players around it, out to {@link #RANGE} blocks: built up from sight and sound,
 * remembered for a while, forgotten slowly.
 *
 * <p>Each player has a <b>suspicion</b> from 0 upward. Being seen raises it (more when close, lit, moving, sprinting;
 * far less when sneaking in the dark; nothing out of the creature's field of view unless very close); noises raise
 * it by their loudness over distance. It decays when nothing new arrives. Thresholds give the creature's
 * {@link Mode}: {@code CALM} below {@link #SUSPECT}, {@code SUSPICIOUS} (go and look at the last known spot) below
 * {@link #ALERT}, then {@code ALERT} (hunt). A creature that loses its target keeps its <b>memory</b>, the last
 * known position and how long ago, and sweeps the area around it ({@link #searchPoint}) until it forgets.</p>
 *
 * <p>Pure bookkeeping: the caller supplies sight tests (line of sight is a world query) through {@link Senses}, so
 * the logic can be unit-tested without a server.</p>
 */
public final class Awareness {
    public static final double RANGE = 32;
    public static final double SUSPECT = 0.35, ALERT = 1.0;

    public enum Mode { CALM, SUSPICIOUS, ALERT, SEARCHING }

    /** World questions the model needs answered. */
    public interface Senses {
        boolean lineOfSight(Player p);

        /** Block light at the player (0..15). */
        int lightAt(Player p);
    }

    /** One player's standing with this creature. */
    static final class Trace {
        double suspicion;
        Location last;
        Location prevPos;
        int ticksSince;
    }

    private final Map<UUID, Trace> traces = new HashMap<>();
    /** Field of view, degrees either side of the facing; 180 = all round (creatures that do not see, but feel). */
    private final double fovHalf;
    /** How strongly sight counts (0 for creatures that hunt by sound and movement only). */
    private final double sight;
    private final int memoryTicks;
    private Mode mode = Mode.CALM;
    private UUID target;
    private Location memory;
    private int memoryAge = Integer.MAX_VALUE;
    private Location noise;
    private int noiseAge = Integer.MAX_VALUE;

    public Awareness(double fovHalfDeg, double sight, int memoryTicks) {
        this.fovHalf = fovHalfDeg;
        this.sight = sight;
        this.memoryTicks = memoryTicks;
    }

    public Mode mode() {
        return mode;
    }

    public UUID target() {
        return target;
    }

    /** The last place the target (or a noise) was placed, or null. */
    public Location lastKnown() {
        if (memory != null && memoryAge < memoryTicks) return memory;
        return noise != null && noiseAge < memoryTicks ? noise : null;
    }

    public double suspicion(Player p) {
        Trace t = traces.get(p.getUniqueId());
        return t == null ? 0 : t.suspicion;
    }

    /** A sound of this loudness (radius in blocks it carries) at a place. */
    public void hear(Location from, Location at, double loudness, Player source) {
        if (at.getWorld() != from.getWorld()) return;
        double d = at.distance(from);
        if (d > Math.min(RANGE, loudness)) return;
        double gain = 0.55 * (1 - d / Math.min(RANGE, loudness)) + 0.15;
        noise = at.clone();
        noiseAge = 0;
        if (source != null) {
            Trace t = traces.computeIfAbsent(source.getUniqueId(), k -> new Trace());
            t.suspicion = Math.min(2.0, t.suspicion + gain);
            t.last = at.clone();
            t.ticksSince = 0;
        }
    }

    /**
     * One look around: from the creature's eyes {@code eye}, facing {@code yawDeg}, over {@code dt} ticks. Returns
     * true when the mode changed (the caller plays its cue).
     */
    public boolean update(World w, Location eye, double yawDeg, int dt, Senses senses) {
        Mode before = mode;
        if (memoryAge < Integer.MAX_VALUE - dt) memoryAge += dt;
        if (noiseAge < Integer.MAX_VALUE - dt) noiseAge += dt;
        double best = 0;
        Player bestP = null;
        for (Player p : w.getPlayers()) {
            if (!p.isValid() || p.isDead() || p.getGameMode() == GameMode.SPECTATOR) continue;
            Trace t = traces.computeIfAbsent(p.getUniqueId(), k -> new Trace());
            Location pl = p.getLocation();
            double d = pl.distance(eye);
            boolean moved = t.prevPos != null && t.prevPos.getWorld() == pl.getWorld() && t.prevPos.distanceSquared(pl) > 0.01;
            t.prevPos = pl.clone();
            double gain = 0;
            if (d <= RANGE) {
                // sight
                if (sight > 0 && (d < 3 || inView(eye, yawDeg, pl)) && senses.lineOfSight(p)) {
                    double light = senses.lightAt(p) >= 7 ? 1.0 : senses.lightAt(p) >= 3 ? 0.65 : 0.35;
                    double stance = p.isSneaking() ? 0.4 : p.isSprinting() ? 1.4 : moved ? 1.0 : 0.6;
                    gain += sight * light * stance * Math.pow(1 - d / RANGE, 1.4) * 0.12 * dt;
                }
                // movement felt or heard close by (sneaking is nearly silent)
                double carry = p.isSprinting() ? 24 : moved && !p.isSneaking() ? 9 : moved ? 2.5 : 0;
                if (carry > 0 && d < carry) gain += (1 - d / carry) * 0.08 * dt;
            }
            // forgetting runs all the time (slowly at first, while it is still looking, faster later), so only
            // evidence stronger than the forgetting builds up: a glimpse of someone sneaking in the dark far off
            // never adds up to anything
            double decay = dt * (t.ticksSince < 60 ? 0.003 : 0.008);
            if (gain > decay) {
                t.last = pl.clone();
                t.ticksSince = 0;
            } else {
                t.ticksSince += dt;
            }
            t.suspicion = Math.max(0, Math.min(2.0, t.suspicion + gain - decay));
            if (t.suspicion > best) {
                best = t.suspicion;
                bestP = p;
            }
        }
        if (bestP != null && best >= SUSPECT) {
            Trace t = traces.get(bestP.getUniqueId());
            target = bestP.getUniqueId();
            if (t.last != null && t.ticksSince == 0 && t.suspicion > 0) {
                memory = t.last.clone();
                memoryAge = 0;
            }
            mode = best >= ALERT ? (t.ticksSince > 40 ? Mode.SEARCHING : Mode.ALERT) : before == Mode.ALERT || before == Mode.SEARCHING ? Mode.SEARCHING : Mode.SUSPICIOUS;
        } else if (lastKnown() != null && (before != Mode.CALM || noiseAge < memoryTicks)) {
            mode = before == Mode.ALERT || before == Mode.SEARCHING ? Mode.SEARCHING : Mode.SUSPICIOUS;
        } else {
            mode = Mode.CALM;
            target = null;
        }
        return mode != before;
    }

    private boolean inView(Location eye, double yawDeg, Location at) {
        if (fovHalf >= 180) return true;
        double yaw = Math.toRadians(yawDeg);
        Vector facing = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        Vector to = at.toVector().subtract(eye.toVector()).setY(0);
        if (to.lengthSquared() < 1e-6) return true;
        return Math.toDegrees(facing.angle(to)) <= fovHalf;
    }

    /** A place to look next while searching: around the last known spot, wider the longer it has been. */
    public Location searchPoint() {
        Location k = lastKnown();
        if (k == null) return null;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double spread = 2 + Math.min(10, Math.min(memoryAge, noiseAge) / 40.0);
        return k.clone().add(r.nextDouble(-spread, spread), 0, r.nextDouble(-spread, spread));
    }

    /** Forget everything (it gave up, or was driven off). */
    public void forget() {
        traces.clear();
        mode = Mode.CALM;
        target = null;
        memory = null;
        noise = null;
        memoryAge = noiseAge = Integer.MAX_VALUE;
    }
}
