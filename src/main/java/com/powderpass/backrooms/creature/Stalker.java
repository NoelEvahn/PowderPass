package com.powderpass.backrooms.creature;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

/**
 * A stalking mind: it does not walk straight at its prey. It keeps its distance, circles, shuffles from side to
 * side, slips out of view to reposition, feints toward the target and backs off, and reacts when the target moves
 * toward it, runs, or looks at it. A target that stands still is not left in peace: it keeps moving round them,
 * and if they stay put for about ten seconds it closes in and strikes. Moving resets that timer.
 * <p>
 * The stalker only decides <i>where</i> to go and how fast; the owner moves there (on foot or with a physical
 * controller) and asks it to strike. Decisions are pure apart from the random source, so tests can drive it.
 */
final class Stalker {
    enum Mode { CIRCLE, SHUFFLE, REPOSITION, FEINT, RETREAT, PAUSE, ATTACK }

    /** Preferred distance band and attack reach. */
    final double minDist, maxDist, reach;
    /** Ticks a target must stand still before it closes in. */
    final int patience;
    private final Random rnd;

    Mode mode = Mode.CIRCLE;
    private int modeTicks, dir = 1, flipIn, cooldown;
    int stillTicks;
    private final Deque<double[]> trail = new ArrayDeque<>(); // target positions, newest last: x, z, tick
    private long tick;
    private boolean wasWatched;
    /** Current goal and speed (blocks per tick); the owner moves toward it. */
    double gx, gy, gz, speed;
    /** True while it wants the owner to face the target. */
    boolean faceTarget = true;
    /** Set for one decision when the target is in reach during an attack; the owner strikes and calls {@link #struck}. */
    boolean strikeNow;

    Stalker(double minDist, double maxDist, double reach, int patience, long seed) {
        this.minDist = minDist;
        this.maxDist = maxDist;
        this.reach = reach;
        this.patience = patience;
        this.rnd = new Random(seed);
        this.dir = rnd.nextBoolean() ? 1 : -1;
    }

    /** How far the target has moved (horizontally) over the last second. */
    double recentMotion() {
        if (trail.size() < 2) return 0;
        double[] a = trail.peekFirst(), b = trail.peekLast();
        return Math.hypot(b[0] - a[0], b[1] - a[1]);
    }

    /** Is the target standing still (ignoring looking around)? */
    boolean targetStill() {
        return trail.size() >= 2 && recentMotion() < 0.45;
    }

    /**
     * One decision. {@code sx,sy,sz} is where the stalker is, {@code watched} whether the target is looking at it.
     * Updates the goal, speed, mode and {@link #strikeNow}.
     */
    void think(int dt, double sx, double sy, double sz, Player target, boolean watched) {
        Location t = target.getLocation();
        think(dt, sx, sy, sz, t.getX(), t.getY(), t.getZ(), t.getYaw(), watched);
    }

    void think(int dt, double sx, double sy, double sz, double tx, double ty, double tz, float targetYaw, boolean watched) {
        tick += dt;
        strikeNow = false;
        cooldown = Math.max(0, cooldown - dt);
        trail.addLast(new double[]{tx, tz, tick});
        while (trail.size() > 2 && tick - trail.peekFirst()[2] > 20) trail.removeFirst();

        // the stillness timer: any real movement resets it
        boolean still = targetStill();
        if (still) stillTicks += dt;
        else stillTicks = 0;

        double dx = sx - tx, dz = sz - tz;
        double d = Math.hypot(dx, dz);
        double ax = d > 1e-3 ? dx / d : 1, az = d > 1e-3 ? dz / d : 0; // from target toward us
        // is the target coming at us?
        double approach = 0;
        if (trail.size() >= 2) {
            double[] a = trail.peekFirst(), b = trail.peekLast();
            approach = ((b[0] - a[0]) * ax + (b[1] - a[1]) * az);
        }
        modeTicks -= dt;

        // ---- choose a mode
        if (mode == Mode.ATTACK) {
            if (!still && recentMotion() > 1.6) {
                // they moved: the timer resets, and it melts back to the edge of the band
                switchTo(rnd.nextBoolean() ? Mode.RETREAT : Mode.REPOSITION, 30 + rnd.nextInt(30));
            } else if (d <= reach) {
                strikeNow = true;
            } else if (modeTicks <= -200) {
                switchTo(Mode.REPOSITION, 40);
            }
        } else if (stillTicks >= patience && cooldown == 0) {
            switchTo(Mode.ATTACK, 0);
        } else if (approach > 1.2 && d < maxDist && mode != Mode.RETREAT && mode != Mode.FEINT) {
            // the target is coming at it: back off, or slip sideways
            switchTo(rnd.nextDouble() < 0.6 ? Mode.RETREAT : Mode.SHUFFLE, 20 + rnd.nextInt(25));
        } else if (watched && !wasWatched && mode != Mode.FEINT) {
            // caught looking: freeze for a heartbeat, or sidestep out of the light
            double r = rnd.nextDouble();
            if (r < 0.35) switchTo(Mode.PAUSE, 6 + rnd.nextInt(14));
            else if (r < 0.75) switchTo(Mode.SHUFFLE, 20 + rnd.nextInt(30));
            else switchTo(Mode.REPOSITION, 60);
        } else if (d < minDist - 1.5 && mode != Mode.FEINT && mode != Mode.RETREAT) {
            switchTo(Mode.RETREAT, 25 + rnd.nextInt(20));
        } else if (d > maxDist + 6 && mode != Mode.REPOSITION) {
            switchTo(Mode.REPOSITION, 80);
        } else if (modeTicks <= 0) {
            pickNext(still, d);
        }
        wasWatched = watched;

        // ---- turn the mode into a goal
        double band = (minDist + maxDist) / 2;
        switch (mode) {
            case CIRCLE -> {
                double ang = Math.atan2(az, ax) + dir * 0.45;
                double rad = band + 1.5 * Math.sin(tick * 0.05);
                setGoal(tx + Math.cos(ang) * rad, ty, tz + Math.sin(ang) * rad, 0.13 + 0.05 * rnd.nextDouble());
                faceTarget = true;
            }
            case SHUFFLE -> {
                if ((flipIn -= dt) <= 0) {
                    flipIn = 8 + rnd.nextInt(14);
                    if (rnd.nextDouble() < 0.6) dir = -dir;
                }
                double px = -az * dir, pz = ax * dir;
                // a little in or out too, so it never sits on one ring
                double in = (d - band) * -0.15;
                setGoal(sx + px * 1.6 + ax * in, sy, sz + pz * 1.6 + az * in, flipIn < 3 ? 0.02 : 0.11);
                faceTarget = true;
            }
            case REPOSITION -> {
                if (speed <= 0 || Math.hypot(gx - sx, gz - sz) < 1.0 || modeTicks % 40 == 0) {
                    // somewhere at the edge of the band, preferably where the target is not looking
                    double look = Math.toRadians(targetYaw + 90); // yaw -> angle of the facing direction in x/z
                    double ang = look + Math.PI + (rnd.nextDouble() - 0.5) * Math.PI * 1.2;
                    double rad = minDist + rnd.nextDouble() * (maxDist - minDist);
                    setGoal(tx + Math.cos(ang) * rad, ty, tz + Math.sin(ang) * rad, 0.2);
                }
                faceTarget = rnd.nextDouble() < 0.7;
            }
            case FEINT -> {
                if (d < 3.4) switchTo(Mode.RETREAT, 25 + rnd.nextInt(15));
                setGoal(tx + ax * 2.5, ty, tz + az * 2.5, 0.26);
                faceTarget = true;
            }
            case RETREAT -> {
                setGoal(sx + ax * 3, sy, sz + az * 3, 0.2);
                faceTarget = true;
            }
            case PAUSE -> {
                setGoal(sx, sy, sz, 0);
                faceTarget = true;
            }
            case ATTACK -> {
                setGoal(tx, ty, tz, 0.27);
                faceTarget = true;
            }
        }
    }

    private void pickNext(boolean targetStill, double d) {
        double r = rnd.nextDouble();
        // a still target gets more restless movement round it, never a frozen stare
        if (targetStill) {
            if (r < 0.35) switchTo(Mode.CIRCLE, 40 + rnd.nextInt(60));
            else if (r < 0.6) switchTo(Mode.SHUFFLE, 25 + rnd.nextInt(30));
            else if (r < 0.82) switchTo(Mode.REPOSITION, 60);
            else switchTo(Mode.FEINT, 40);
            if (rnd.nextDouble() < 0.4) dir = -dir;
            return;
        }
        if (r < 0.3) switchTo(Mode.CIRCLE, 40 + rnd.nextInt(80));
        else if (r < 0.52) switchTo(Mode.SHUFFLE, 20 + rnd.nextInt(30));
        else if (r < 0.74) switchTo(Mode.REPOSITION, 80);
        else if (r < 0.86 && d < maxDist + 2) switchTo(Mode.FEINT, 40);
        else switchTo(Mode.PAUSE, 8 + rnd.nextInt(16));
        if (rnd.nextDouble() < 0.3) dir = -dir;
    }

    private void switchTo(Mode m, int ticks) {
        mode = m;
        modeTicks = ticks;
        speed = -1; // force a fresh goal for modes that keep one
    }

    private void setGoal(double x, double y, double z, double s) {
        gx = x;
        gy = y;
        gz = z;
        speed = s;
    }

    /** The owner struck: cool down, start the timer over, back off. */
    void struck() {
        cooldown = 70 + rnd.nextInt(40);
        stillTicks = 0;
        switchTo(Mode.RETREAT, 25 + rnd.nextInt(15));
    }

    /** The owner could not reach the goal (blocked): pick another plan. */
    void blocked() {
        dir = -dir;
        if (mode != Mode.ATTACK) switchTo(rnd.nextBoolean() ? Mode.REPOSITION : Mode.SHUFFLE, 30);
    }
}
