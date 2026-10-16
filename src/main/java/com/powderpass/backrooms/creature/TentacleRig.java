package com.powderpass.backrooms.creature;

import com.powderpass.backrooms.display.Keys;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The black organism a possessed player is carried by: long tapered tentacles bursting out of the player's back,
 * six of them planted on the ground like spider legs, smaller ones crawling round the torso and dragging behind,
 * one thick striking tentacle coiled over the shoulder, and the torn wound they come out of.
 * <p>
 * Every part is a {@link BlockDisplay} riding the victim as a passenger, so the tentacles stay fixed to the
 * player's back on every client with no drift or lag between the body and the organism. The client places
 * passengers at the top of the player's head; every transform here is computed in world space and converted to
 * that origin. Feet are planted in world space and only move during a step, so the legs do not slide.
 * <p>
 * Geometry and gait are pure functions of the inputs given to {@link #update}, with ground queries going through
 * {@link Ground}; that keeps it testable without a live world.
 */
final class TentacleRig {
    /** Ground and ceiling queries (a real world in game, a stub in tests). */
    interface Ground {
        /** Top surface y of the ground in this column at or below {@code fromY} (within {@code depth}), or NaN. */
        double groundY(double x, double fromY, double z, double depth);

        /** First solid block bottom above {@code fromY} in this column (within {@code range}), or +infinity. */
        double ceilingY(double x, double fromY, double z, double range);
    }

    static final int LEG_SEGMENTS = 6;
    static final int TENDRIL_SEGMENTS = 4;
    static final int STRIKER_SEGMENTS = 8;
    static final int LEGS = 6;
    static final int TENDRILS = 6;
    /** Back point: height above the feet, and how far behind the spine. */
    static final double BACK_Y = 1.36, BACK_OFF = 0.2;

    private static final BlockData FLESH = Material.BLACK_CONCRETE.createBlockData();
    private static final BlockData FLESH_DULL = Material.BLACK_CONCRETE_POWDER.createBlockData();
    private static final BlockData WOUND = Material.NETHER_WART_BLOCK.createBlockData();
    private static final BlockData BONE = Material.BONE_BLOCK.createBlockData();

    /** One leg: where its foot is planted and its step in progress. */
    static final class Leg {
        final int side; // +1 right, -1 left
        final int row; // 0 front, 1 middle, 2 rear
        final double angle, reach;
        final Vector3d plant = new Vector3d();
        final Vector3d from = new Vector3d();
        final Vector3d to = new Vector3d();
        boolean planted, stepping, hasGround;
        double step; // 0..1
        double phase;

        Leg(int side, int row, double angle, double reach, double phase) {
            this.side = side;
            this.row = row;
            this.angle = angle;
            this.reach = reach;
            this.phase = phase;
        }

        /** Tripod gait: front and rear of one side step with the middle of the other. */
        int group() {
            return (row == 1 ? -side : side) > 0 ? 0 : 1;
        }

        Vector3d foot() {
            if (!stepping) return new Vector3d(plant);
            double s = smooth(step);
            Vector3d f = new Vector3d(from).lerp(to, s);
            f.y += Math.sin(Math.PI * step) * (0.45 + 0.25 * from.distance(to) / 2.0);
            return f;
        }
    }

    final List<Leg> legs = new ArrayList<>();
    private final List<Entity> parts = new ArrayList<>();
    private final List<Matrix4f> sent = new ArrayList<>();
    private final Random rnd;
    private final int legBase, tendrilBase, strikerBase, massBase, partCount;

    // ---- animation state (set by the owner every update)
    /** 0..1: tentacles growing out of the back. */
    double emerge;
    /** 0..1: legs reaching down and planting on the ground. */
    double plant;
    /** 0..1: the wound in the back tearing open. */
    double wound;
    /** 0..1: twitching intensity (limbs jerk at random). */
    double twitch;
    /** Strike animation: 0 idle, windup (0..1), lash (1..2), recoil (2..3). */
    double strike;
    final Vector3d strikeAt = new Vector3d();
    private double age;

    TentacleRig(long seed) {
        this.rnd = new Random(seed);
        double[][] spec = { // row, angle from forward (deg), reach
                {0, 55, 2.1}, {1, 100, 2.35}, {2, 145, 2.15}};
        for (int side : new int[]{1, -1}) {
            for (double[] s : spec) legs.add(new Leg(side, (int) s[0], Math.toRadians(s[1]), s[2], rnd.nextDouble() * Math.PI * 2));
        }
        // legs: each segment, plus a second box turned 45 degrees round the two thickest segments (rounder)
        legBase = 0;
        tendrilBase = LEGS * (LEG_SEGMENTS + 2);
        strikerBase = tendrilBase + TENDRILS * TENDRIL_SEGMENTS;
        massBase = strikerBase + STRIKER_SEGMENTS + 1;
        partCount = massBase + 7;
    }

    // ------------------------------------------------------------------ entities

    /** Spawn every part (collapsed into the back, invisible) and mount it on the victim. */
    void spawn(Player host, String tag) {
        World w = host.getWorld();
        Location at = host.getLocation();
        at.setYaw(0);
        at.setPitch(0);
        for (int i = 0; i < partCount; i++) {
            BlockData data = blockFor(i);
            BlockDisplay d = w.spawn(at, BlockDisplay.class, e -> {
                e.setPersistent(false);
                e.setBlock(data);
                e.setTransformationMatrix(new Matrix4f().scaling(0));
                e.setInterpolationDuration(0);
                e.setTeleportDuration(0);
                e.setViewRange(1.2f);
                e.setShadowRadius(0);
                e.setShadowStrength(0);
                e.getPersistentDataContainer().set(Keys.CREATURE, PersistentDataType.STRING, tag);
            });
            parts.add(d);
            sent.add(new Matrix4f().scaling(0));
            host.addPassenger(d);
        }
    }

    BlockData blockFor(int i) {
        if (i >= massBase) {
            int k = i - massBase;
            return k < 3 ? FLESH : k < 5 ? WOUND : BONE;
        }
        if (i >= strikerBase) return FLESH;
        if (i >= tendrilBase) return (i - tendrilBase) % TENDRIL_SEGMENTS == 0 ? FLESH : FLESH_DULL;
        return FLESH;
    }

    /** All parts still exist and still ride the host (a world change or a dismount breaks this). */
    boolean attached(Player host) {
        if (parts.isEmpty()) return false;
        List<Entity> riding = host.getPassengers();
        for (Entity e : parts) if (e == null || !e.isValid() || !riding.contains(e)) return false;
        return true;
    }

    /** Re-mount any part that fell off (after a correction teleport, say). */
    void remount(Player host) {
        List<Entity> riding = host.getPassengers();
        for (Entity e : parts) if (e != null && e.isValid() && !riding.contains(e)) host.addPassenger(e);
    }

    void remove(Player host) {
        for (Entity e : parts) {
            if (e == null) continue;
            if (host != null) host.removePassenger(e);
            if (e.isValid()) e.remove();
        }
        parts.clear();
        sent.clear();
    }

    int size() {
        return parts.size();
    }

    List<Entity> entities() {
        return parts;
    }

    // ------------------------------------------------------------------ gait

    /** The resting foot spot of a leg for a body at {@code root} facing {@code yaw}, with a lead for motion. */
    static Vector3d ideal(Leg l, Vector3d root, double yawDeg, Vector3d velocity) {
        double yaw = Math.toRadians(yawDeg);
        Vector3d f = new Vector3d(-Math.sin(yaw), 0, Math.cos(yaw));
        Vector3d r = new Vector3d(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vector3d dir = new Vector3d(f).mul(Math.cos(l.angle)).add(new Vector3d(r).mul(l.side * Math.sin(l.angle)));
        return new Vector3d(root).add(dir.mul(l.reach)).add(new Vector3d(velocity).mul(5));
    }

    /**
     * A foothold near the ideal spot: the ground under it, or, in a corridor too narrow to spread, nearer the
     * body or turned along the corridor. Returns false if the leg has nothing to stand on (it then hangs).
     */
    static boolean foothold(Ground g, Leg l, Vector3d root, Vector3d ideal, Vector3d out) {
        double dx = ideal.x - root.x, dz = ideal.z - root.z;
        double base = Math.atan2(dz, dx);
        double reach = Math.sqrt(dx * dx + dz * dz);
        double[] scales = {1.0, 0.8, 0.6, 0.45};
        double[] turns = {0, 0.35, -0.35, 0.7, -0.7};
        for (double s : scales) {
            for (double t : turns) {
                double a = base + t * (l.row == 1 ? 1 : l.side * (l.row == 0 ? 1 : -1));
                double x = root.x + Math.cos(a) * reach * s, z = root.z + Math.sin(a) * reach * s;
                double y = g.groundY(x, root.y + 1.6, z, 3.6);
                if (Double.isNaN(y)) continue;
                // never through a wall: the line from the body to the foot must stay under the ceiling and over the floor
                double ceil = g.ceilingY(x, y + 0.05, z, 2.0);
                if (ceil - y < 0.6) continue;
                out.set(x, y, z);
                return true;
            }
        }
        return false;
    }

    /**
     * One gait step. Legs whose plant is too far from where they want to be step there, one tripod at a time; a
     * step takes {@code stepTicks}. Returns the number of feet that touched down this update (for footstep sounds).
     */
    int gait(Ground g, Vector3d root, double yawDeg, Vector3d velocity, double dt, double stepTicks, List<Vector3d> landed) {
        int down = 0;
        boolean[] groupStepping = new boolean[2];
        for (Leg l : legs) if (l.stepping) groupStepping[l.group()] = true;
        // advance steps in progress
        for (Leg l : legs) {
            if (!l.stepping) continue;
            l.step += dt / Math.max(2, stepTicks);
            if (l.step >= 1) {
                l.stepping = false;
                l.step = 0;
                l.plant.set(l.to);
                l.planted = l.hasGround;
                down++;
                if (landed != null) landed.add(new Vector3d(l.plant));
            }
        }
        groupStepping[0] = groupStepping[1] = false;
        for (Leg l : legs) if (l.stepping) groupStepping[l.group()] = true;
        double speed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        double threshold = speed > 0.02 ? 0.95 : 0.55;
        Vector3d want = new Vector3d();
        for (int grp = 0; grp < 2; grp++) {
            if (groupStepping[grp] || groupStepping[1 - grp]) continue;
            boolean go = false;
            for (Leg l : legs) {
                if (l.group() != grp) continue;
                Vector3d id = ideal(l, root, yawDeg, velocity);
                if (!l.planted || l.plant.distance(id.x, l.plant.y, id.z) > threshold || Math.abs(l.plant.y - root.y) > 2.2) go = true;
            }
            if (!go) continue;
            for (Leg l : legs) {
                if (l.group() != grp) continue;
                Vector3d id = ideal(l, root, yawDeg, velocity);
                l.hasGround = foothold(g, l, root, id, want);
                if (!l.hasGround) want.set(id.x, root.y + 0.6, id.z); // nothing to stand on: it hangs, curled
                l.from.set(l.planted || l.step > 0 ? l.plant : l.foot());
                if (!l.planted && l.plant.lengthSquared() == 0) l.from.set(root.x, root.y + 1.2, root.z);
                l.to.set(want);
                l.stepping = true;
                l.step = 0;
            }
            break; // one tripod at a time
        }
        return down;
    }

    /** Put every foot straight onto its foothold (used once the legs have reached the ground). */
    void settle(Ground g, Vector3d root, double yawDeg) {
        Vector3d out = new Vector3d();
        for (Leg l : legs) {
            Vector3d id = ideal(l, root, yawDeg, new Vector3d());
            l.hasGround = foothold(g, l, root, id, out);
            if (!l.hasGround) out.set(id.x, root.y + 0.6, id.z);
            l.plant.set(out);
            l.planted = l.hasGround;
            l.stepping = false;
        }
    }

    // ------------------------------------------------------------------ pose

    /**
     * Recompute every transform. {@code feet} is where the host's feet are (server position), {@code hostHeight} the
     * host's current height (the passenger origin sits on top of the head), {@code yawDeg} the body heading and
     * {@code ceiling} the ceiling height above the feet (tentacles arch under it).
     */
    void update(Vector3d feet, double hostHeight, double yawDeg, double ceiling, double dtTicks, int interp) {
        age += dtTicks;
        if (parts.isEmpty()) computed.clear();
        double yaw = Math.toRadians(yawDeg);
        Vector3d f = new Vector3d(-Math.sin(yaw), 0, Math.cos(yaw));
        Vector3d r = new Vector3d(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vector3d up = new Vector3d(0, 1, 0);
        Vector3d origin = new Vector3d(feet.x, feet.y + hostHeight, feet.z);
        Vector3d back = new Vector3d(feet).add(0, BACK_Y, 0).sub(new Vector3d(f).mul(BACK_OFF));
        double maxY = Math.min(feet.y + 3.4, ceiling - 0.25);
        int idx = 0;

        // --- legs
        for (int li = 0; li < legs.size(); li++) {
            Leg l = legs.get(li);
            Vector3d dir = new Vector3d(f).mul(Math.cos(l.angle)).add(new Vector3d(r).mul(l.side * Math.sin(l.angle)));
            Vector3d base = new Vector3d(back).add(new Vector3d(r).mul(l.side * 0.1)).add(0, 0.12 - l.row * 0.09, 0)
                    .sub(new Vector3d(f).mul(0.04 + 0.02 * l.row));
            // where the tip is: flailing in the air while emerging, then reaching down to the plant
            Vector3d air = new Vector3d(base).add(new Vector3d(dir).mul(0.5 + 1.6 * emerge))
                    .add(0, 0.6 + 0.5 * emerge + 0.35 * Math.sin(age * 0.45 + l.phase), 0)
                    .add(new Vector3d(f).mul(0.3 * Math.sin(age * 0.37 + l.phase * 2)));
            Vector3d foot = l.foot();
            if (l.plant.lengthSquared() == 0 && !l.stepping) foot = air;
            double p = clamp01((plant - l.row * 0.12 - (l.side > 0 ? 0 : 0.06)) / 0.7);
            Vector3d tip = new Vector3d(air).lerp(foot, smooth(p));
            jitter(tip, 0.18 * twitch);
            double len = 0.25 + 0.75 * smooth(emerge);
            Vector3d c1 = new Vector3d(base).add(new Vector3d(up).mul(0.95 * len)).add(new Vector3d(dir).mul(0.55 * len)).sub(new Vector3d(f).mul(0.12));
            double knee = 1.35 + 0.18 * Math.sin(age * 0.11 + l.phase);
            Vector3d c2 = new Vector3d(tip).add(new Vector3d(up).mul(knee * (0.4 + 0.6 * p))).sub(new Vector3d(dir).mul(0.25));
            c1.y = Math.min(c1.y, maxY);
            c2.y = Math.min(c2.y, maxY);
            Vector3d[] pts = new Vector3d[LEG_SEGMENTS + 1];
            for (int i = 0; i <= LEG_SEGMENTS; i++) {
                double t = (double) i / LEG_SEGMENTS * len;
                pts[i] = bezier(base, c1, c2, tip, t / Math.max(len, 1e-3) * (emerge >= 1 ? 1 : Math.min(1, emerge * 1.4)));
                double wob = (l.stepping || !l.planted ? 0.07 : 0.025) * Math.sin(age * 0.23 + i * 0.95 + l.phase) * Math.sin(Math.PI * i / LEG_SEGMENTS);
                pts[i].add(new Vector3d(r).mul(wob)).add(0, wob * 0.5, 0);
                if (i > 0 && i < LEG_SEGMENTS) jitter(pts[i], 0.08 * twitch);
            }
            for (int i = 0; i < LEG_SEGMENTS; i++) {
                double k = (double) i / (LEG_SEGMENTS - 1);
                double thick = (0.26 - 0.2 * k) * (i == 2 ? 1.12 : 1.0) * (0.35 + 0.65 * smooth(emerge));
                set(idx++, segment(origin, pts[i], pts[i + 1], thick, 0), interp);
            }
            for (int i = 0; i < 2; i++) {
                double thick = (0.26 - 0.04 * i) * 0.86 * (0.35 + 0.65 * smooth(emerge));
                set(idx++, segment(origin, pts[i], pts[i + 1], thick, Math.PI / 4), interp);
            }
        }

        // --- tendrils: four crawl round the torso, two drag on the ground behind
        for (int ti = 0; ti < TENDRILS; ti++) {
            Vector3d[] pts = new Vector3d[TENDRIL_SEGMENTS + 1];
            double grow = clamp01(emerge * 1.6 - 0.2 - ti * 0.08);
            if (ti < 4) {
                int side = (ti & 1) == 0 ? 1 : -1;
                double sweep = Math.toRadians(95 + 35 * ti + 30 * Math.sin(age * 0.06 + ti * 1.7)) * grow;
                double h0 = BACK_Y - 0.08, h1 = 0.72 + 0.16 * ti + 0.12 * Math.sin(age * 0.05 + ti);
                for (int i = 0; i <= TENDRIL_SEGMENTS; i++) {
                    double t = (double) i / TENDRIL_SEGMENTS;
                    double a = sweep * t;
                    double rad = 0.31 + 0.05 * t + 0.02 * Math.sin(age * 0.3 + i + ti);
                    Vector3d around = new Vector3d(f).mul(-Math.cos(a)).add(new Vector3d(r).mul(side * Math.sin(a))).mul(rad);
                    pts[i] = new Vector3d(feet).add(0, h0 + (h1 - h0) * t, 0).add(around);
                    if (i == TENDRIL_SEGMENTS) jitter(pts[i], 0.05 + 0.1 * twitch);
                }
                for (int i = 0; i < TENDRIL_SEGMENTS; i++) {
                    double thick = (0.11 - 0.065 * i / (TENDRIL_SEGMENTS - 1.0)) * (0.3 + 0.7 * grow);
                    set(idx++, grow <= 0.01 ? hidden(origin, back) : segment(origin, pts[i], pts[i + 1], thick, 0.3 * ti), interp);
                }
            } else {
                int side = ti == 4 ? 1 : -1;
                Vector3d start = new Vector3d(back).add(0, -0.25, 0).add(new Vector3d(r).mul(side * 0.08));
                double sway = Math.sin(age * 0.18 + ti) * 0.35;
                Vector3d end = new Vector3d(feet).sub(new Vector3d(f).mul(0.9 + 0.5 * grow)).add(new Vector3d(r).mul(side * (0.35 + sway)));
                end.y = feet.y - 0.6; // trails down to the floor below the hovering feet
                Vector3d mid = new Vector3d(start).lerp(end, 0.5).sub(new Vector3d(f).mul(0.4)).add(0, 0.15, 0);
                for (int i = 0; i <= TENDRIL_SEGMENTS; i++) {
                    double t = (double) i / TENDRIL_SEGMENTS * grow;
                    pts[i] = bezier(start, mid, mid, end, t);
                    pts[i].add(new Vector3d(r).mul(0.06 * Math.sin(age * 0.4 + i * 1.3 + ti)));
                }
                for (int i = 0; i < TENDRIL_SEGMENTS; i++) {
                    double thick = (0.12 - 0.07 * i / (TENDRIL_SEGMENTS - 1.0)) * (0.3 + 0.7 * grow);
                    set(idx++, grow <= 0.01 ? hidden(origin, back) : segment(origin, pts[i], pts[i + 1], thick, 0), interp);
                }
            }
        }

        // --- the striker: coiled over the right shoulder like a scorpion's tail, lashing at its prey
        {
            double grow = clamp01(emerge * 1.3 - 0.25);
            Vector3d base = new Vector3d(back).add(0, 0.1, 0).add(new Vector3d(r).mul(0.06));
            double sw = Math.sin(age * 0.09) * 0.25;
            Vector3d tipIdle = new Vector3d(feet).add(0, BACK_Y + 0.9 + 0.15 * Math.sin(age * 0.13), 0)
                    .add(new Vector3d(f).mul(0.55 + 0.15 * Math.sin(age * 0.07))).add(new Vector3d(r).mul(0.45 + sw));
            Vector3d c1 = new Vector3d(base).add(0, 1.0, 0).sub(new Vector3d(f).mul(0.35)).add(new Vector3d(r).mul(0.15));
            Vector3d c2 = new Vector3d(base).add(0, 1.75, 0).add(new Vector3d(f).mul(0.1)).add(new Vector3d(r).mul(0.4 + sw));
            Vector3d tip = tipIdle;
            if (strike > 0) {
                Vector3d wind = new Vector3d(base).add(0, 2.0, 0).sub(new Vector3d(f).mul(0.6)).add(new Vector3d(r).mul(0.3));
                Vector3d hit = new Vector3d(strikeAt);
                Vector3d toHit = new Vector3d(hit).sub(base);
                if (toHit.length() > 4.2) hit = new Vector3d(base).add(toHit.normalize().mul(4.2));
                if (strike < 1) tip = new Vector3d(tipIdle).lerp(wind, smooth(strike));
                else if (strike < 2) tip = new Vector3d(wind).lerp(hit, Math.min(1, (strike - 1) * 1.6));
                else tip = new Vector3d(hit).lerp(tipIdle, smooth(strike - 2));
                if (strike >= 1 && strike < 2.4) {
                    // lashing: the curve straightens toward the target
                    double s = strike < 2 ? strike - 1 : 1 - (strike - 2) / 0.4;
                    c1.lerp(new Vector3d(base).lerp(tip, 0.33).add(0, 0.5, 0), s);
                    c2.lerp(new Vector3d(base).lerp(tip, 0.66).add(0, 0.25, 0), s);
                }
            }
            c1.y = Math.min(c1.y, maxY + 0.2);
            c2.y = Math.min(c2.y, maxY + 0.2);
            tip.y = Math.min(tip.y, maxY + 0.2);
            Vector3d prev = null;
            for (int i = 0; i <= STRIKER_SEGMENTS; i++) {
                double t = (double) i / STRIKER_SEGMENTS * grow;
                Vector3d pnt = bezier(base, c1, c2, tip, t);
                pnt.add(new Vector3d(f).mul(0.04 * Math.sin(age * 0.5 + i)));
                if (prev != null) {
                    double k = (i - 1.0) / (STRIKER_SEGMENTS - 1);
                    double thick = (0.2 - 0.15 * k) * (0.3 + 0.7 * grow);
                    set(idx++, grow <= 0.01 ? hidden(origin, back) : segment(origin, prev, pnt, thick, 0.4), interp);
                }
                prev = pnt;
            }
            // a hooked barb at the tip
            Vector3d barbEnd = new Vector3d(prev).add(new Vector3d(f).mul(0.12)).add(0, -0.14, 0);
            set(idx++, grow <= 0.5 ? hidden(origin, back) : segment(origin, prev, barbEnd, 0.05, 0), interp);
        }

        // --- the wound: a torn black mass between the shoulder blades, wet red edges, splintered ribs
        {
            double o = smooth(wound);
            double pulse = 1 + 0.06 * Math.sin(age * 0.33);
            Vector3d c = new Vector3d(back).add(new Vector3d(f).mul(0.03));
            set(idx++, o <= 0.01 ? hidden(origin, back) : box(origin, c, yaw, 0.12, 0.42 * o * pulse, 0.48 * o * pulse, 0.16 * o, 0.0, 0), interp);
            set(idx++, o <= 0.01 ? hidden(origin, back) : box(origin, new Vector3d(c).add(0, 0.12, 0).sub(new Vector3d(f).mul(0.05)), yaw, 0.5, 0.3 * o, 0.3 * o, 0.18 * o, 0.3, 1), interp);
            set(idx++, o <= 0.01 ? hidden(origin, back) : box(origin, new Vector3d(c).add(0, -0.14, 0).sub(new Vector3d(f).mul(0.04)), yaw, -0.4, 0.34 * o, 0.26 * o, 0.15 * o, -0.25, 2), interp);
            set(idx++, o <= 0.01 ? hidden(origin, back) : box(origin, new Vector3d(c).add(new Vector3d(r).mul(0.16)).add(new Vector3d(f).mul(0.05)), yaw, 0.2, 0.07 * o, 0.44 * o, 0.06, 0.1, 3), interp);
            set(idx++, o <= 0.01 ? hidden(origin, back) : box(origin, new Vector3d(c).sub(new Vector3d(r).mul(0.16)).add(new Vector3d(f).mul(0.05)), yaw, -0.2, 0.07 * o, 0.44 * o, 0.06, -0.1, 4), interp);
            for (int k = 0; k < 2; k++) {
                int s = k == 0 ? 1 : -1;
                Vector3d a = new Vector3d(c).add(new Vector3d(r).mul(s * 0.14)).add(0, 0.05 - 0.12 * k, 0);
                Vector3d b = new Vector3d(a).add(new Vector3d(r).mul(s * 0.22 * o)).sub(new Vector3d(f).mul(0.26 * o)).add(0, 0.12 * o, 0);
                set(idx++, o <= 0.2 ? hidden(origin, back) : segment(origin, a, b, 0.045, 0), interp);
            }
        }
    }

    // ------------------------------------------------------------------ geometry helpers

    /** A box from {@code a} to {@code b} (world), {@code thick} across, turned {@code twist} round its axis. */
    static Matrix4f segment(Vector3d origin, Vector3d a, Vector3d b, double thick, double twist) {
        Vector3f d = new Vector3f((float) (b.x - a.x), (float) (b.y - a.y), (float) (b.z - a.z));
        float len = d.length();
        Matrix4f m = new Matrix4f().translate((float) (a.x - origin.x), (float) (a.y - origin.y), (float) (a.z - origin.z));
        if (len > 1e-4) m.rotate(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), d.normalize(new Vector3f())));
        m.rotateY((float) twist);
        float t = (float) thick;
        // a little overlap at both ends hides the seams at the joints
        float l = len + t * 0.35f;
        return m.translate(-t / 2, -t * 0.17f, -t / 2).scale(t, Math.max(l, 1e-3f), t);
    }

    /** A box centred at {@code c} (world), turned with the body, then tilted. */
    private static Matrix4f box(Vector3d origin, Vector3d c, double yaw, double tilt, double sx, double sy, double sz, double roll, int k) {
        return new Matrix4f().translate((float) (c.x - origin.x), (float) (c.y - origin.y), (float) (c.z - origin.z))
                .rotateY((float) -yaw).rotateX((float) tilt * 0.4f).rotateZ((float) roll)
                .rotateY(0.15f * k).translate((float) (-sx / 2), (float) (-sy / 2), (float) (-sz / 2))
                .scale((float) Math.max(sx, 1e-3), (float) Math.max(sy, 1e-3), (float) Math.max(sz, 1e-3));
    }

    private static Matrix4f hidden(Vector3d origin, Vector3d at) {
        return new Matrix4f().translate((float) (at.x - origin.x), (float) (at.y - origin.y), (float) (at.z - origin.z)).scale(0.001f);
    }

    static Vector3d bezier(Vector3d p0, Vector3d p1, Vector3d p2, Vector3d p3, double t) {
        double u = 1 - t;
        return new Vector3d(p0).mul(u * u * u).add(new Vector3d(p1).mul(3 * u * u * t)).add(new Vector3d(p2).mul(3 * u * t * t)).add(new Vector3d(p3).mul(t * t * t));
    }

    private void jitter(Vector3d v, double amp) {
        if (amp <= 0) return;
        v.add(rnd.nextGaussian() * amp, rnd.nextGaussian() * amp * 0.6, rnd.nextGaussian() * amp);
    }

    static double smooth(double t) {
        t = clamp01(t);
        return t * t * (3 - 2 * t);
    }

    static double clamp01(double t) {
        return t < 0 ? 0 : t > 1 ? 1 : t;
    }

    /** Send a transform only if it visibly changed. */
    private void set(int i, Matrix4f m, int interp) {
        if (i >= parts.size()) {
            computed.add(m);
            return;
        }
        Matrix4f old = sent.get(i);
        if (old.equals(m, 3e-3f)) return;
        old.set(m);
        if (parts.get(i) instanceof Display d && d.isValid()) {
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(interp);
            d.setTransformationMatrix(m);
        }
    }

    /** Transforms computed without spawned entities (tests and previews). */
    final List<Matrix4f> computed = new ArrayList<>();

    /**
     * The organism in a fixed pose as a plain display model (for {@code /backrooms assets spawn creature:skinwalker}):
     * the tentacles planted round an empty space where a body would hang, origin on the floor.
     */
    static com.powderpass.backrooms.display.Model preview() {
        TentacleRig rig = new TentacleRig(1);
        TentacleRig.Ground flat = new TentacleRig.Ground() {
            @Override
            public double groundY(double x, double fromY, double z, double depth) {
                return fromY >= 0 && fromY - depth <= 0 ? 0 : Double.NaN;
            }

            @Override
            public double ceilingY(double x, double fromY, double z, double range) {
                return Double.POSITIVE_INFINITY;
            }
        };
        rig.settle(flat, new Vector3d(), 0);
        rig.emerge = rig.plant = rig.wound = 1;
        Vector3d feet = new Vector3d(0, HOVER_PREVIEW, 0);
        rig.update(feet, 1.8, 0, 99, 0, 0);
        com.powderpass.backrooms.display.Model m = new com.powderpass.backrooms.display.Model();
        Matrix4f toFloor = new Matrix4f().translation(0, (float) (HOVER_PREVIEW + 1.8), 0);
        for (int i = 0; i < rig.computed.size(); i++) {
            Matrix4f t = new Matrix4f(toFloor).mul(rig.computed.get(i));
            if (Math.abs(t.determinant()) < 1e-9) continue;
            m.boxM(rig.blockFor(i).getMaterial(), t);
        }
        m.viewRange = 1.2f;
        return m;
    }

    private static final double HOVER_PREVIEW = 0.72;

    /** Number of display parts the rig uses. */
    static int partCount() {
        return LEGS * (LEG_SEGMENTS + 2) + TENDRILS * TENDRIL_SEGMENTS + STRIKER_SEGMENTS + 1 + 7;
    }

    int legIndex(int leg, int segment) {
        return legBase + leg * (LEG_SEGMENTS + 2) + segment;
    }
}
