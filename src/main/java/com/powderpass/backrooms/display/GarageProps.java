package com.powderpass.backrooms.display;

import com.powderpass.backrooms.util.Rng;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static com.powderpass.backrooms.display.Anim.Type.BLINK;
import static com.powderpass.backrooms.display.Model.v;
import static org.bukkit.Material.*;

/**
 * Display props of Level 1, the parking garage: the parked cars (which {@code VehicleManager} turns into drivable
 * ones, built from the same geometry) and the overhead steam pipe runs.
 */
public final class GarageProps {
    private GarageProps() {
    }

    // ------------------------------------------------------------------ car geometry (model space: front = +Z, ground = y 0)

    /** Wheel centre: half track, axle height (= tyre radius), front and rear axle positions. */
    public static final double WHEEL_X = 0.85, WHEEL_R = 0.33, WHEEL_ZF = 1.35, WHEEL_ZR = -1.32;
    /** Body length and width (bumper to bumper, mirror-free). */
    public static final double CAR_LENGTH = 4.3, CAR_WIDTH = 1.8;
    /** Part groups of a car model. */
    public static final int G_BODY = 0, G_WHEEL_FL = 1, G_WHEEL_FR = 2, G_WHEEL_RL = 3, G_WHEEL_RR = 4, G_TAIL = 5, G_HEAD = 6,
            G_BRAKE = 7, G_REVERSE = 8;

    static boolean build(String id, String suffix, long seed, Model m) {
        switch (id) {
            case "garage_car" -> {
                car(m, seed, false);
                m.viewRange = 0.9f;
            }
            case "garage_pipes" -> pipes(m, suffix.isEmpty() ? 15.5 : Double.parseDouble(suffix), seed);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** The paint of the car with this seed (garage cars are all a little faded). */
    public static Material paint(long seed) {
        return switch (Rng.pick(seed, 7)) {
            case 0 -> WHITE_CONCRETE;
            case 1 -> GRAY_CONCRETE;
            case 2 -> BLUE_TERRACOTTA;
            case 3 -> RED_TERRACOTTA;
            case 4 -> CYAN_TERRACOTTA;
            case 5 -> GREEN_TERRACOTTA;
            default -> LIGHT_GRAY_CONCRETE;
        };
    }

    /** Is the parked car with this seed under a dust sheet? */
    public static boolean covered(long seed) {
        return Rng.chance(Rng.hash(seed, 9), 0.3);
    }

    /**
     * A compact saloon. {@code live}: the drivable version (interior, brake and reversing lights, no hitboxes and
     * never covered), which the vehicle manager animates by group; otherwise the parked decoration (at most 32
     * entities, two interaction hitboxes, maybe a dust sheet, maybe its lights left on).
     */
    public static Model car(Model m, long seed, boolean live) {
        Material paint = paint(seed);
        boolean sheet = !live && covered(seed);
        Material glass = BLACK_STAINED_GLASS;
        m.group(G_BODY);
        // lower body and sills
        m.box(paint, -0.86, 0.3, -2.05, 1.72, 0.48, 4.1);
        // bonnet, sloping a touch toward the nose; boot lid sloping toward the tail
        m.push().translate(0, 0.78, 0.72).rotate('x', 3.5).box(paint, -0.84, 0, 0, 1.68, 0.08, 1.32).pop();
        m.push().translate(0, 0.78, -1.22).rotate('x', -4).box(paint, -0.84, 0, -0.8, 1.68, 0.08, 0.8).pop();
        // greenhouse: side glass, roof, windscreen and rear window raked back, B pillar
        m.box(glass, -0.78, 0.84, -0.86, 1.56, 0.48, 1.18);
        m.box(paint, -0.77, 1.32, -0.95, 1.54, 0.1, 1.3);
        m.push().translate(0, 0.84, 1.06).rotate('x', -54).box(glass, -0.77, 0, -0.02, 1.54, 0.86, 0.04).pop();
        m.push().translate(0, 0.84, -1.25).rotate('x', 39).box(glass, -0.77, 0, -0.02, 1.54, 0.65, 0.04).pop();
        m.box(paint, -0.795, 0.84, -0.3, 1.59, 0.49, 0.09);
        // one dark plastic band: both bumpers and the rubbing strips along the sills
        m.box(GRAY_CONCRETE, -0.88, 0.24, -2.15, 1.76, 0.24, 4.3);
        if (!sheet) m.box(BLACK_CONCRETE, -0.42, 0.5, 2.04, 0.84, 0.2, 0.03); // grille
        // headlights
        double rnd = Rng.unit(Rng.hash(seed, 11));
        boolean lightsOn = !live && rnd < 0.22, hazards = !live && rnd >= 0.22 && rnd < 0.32;
        for (int s = -1; s <= 1; s += 2) {
            Part h = m.group(G_HEAD).box(live ? WHITE_CONCRETE : LIGHT_GRAY_CONCRETE, s < 0 ? -0.82 : 0.54, 0.56, 2.03, 0.28, 0.15, 0.04);
            if (live) h.glow(6);
        }
        // tail lights: dark red when off; some abandoned cars still have them on, a few blink their hazards
        for (int s = -1; s <= 1; s += 2) {
            Part t = m.group(G_TAIL).box(RED_CONCRETE, s < 0 ? -0.84 : 0.52, 0.58, -2.07, 0.32, 0.14, 0.04);
            if (lightsOn) t.glow(10);
            if (hazards) t.glow(15).alt(BLACK_CONCRETE.createBlockData());
            if (live) t.glow(9);
        }
        if (hazards) m.anim(BLINK, G_TAIL, v(0, 0, 0), v(0, 0, 0), 0.5, 1);
        // wheels: a twelve-sided tyre (three squares 30 degrees apart) and a rim plate on the outer face
        int[] groups = {G_WHEEL_FL, G_WHEEL_FR, G_WHEEL_RL, G_WHEEL_RR};
        double side = WHEEL_R * 2 * 0.74;
        for (int i = 0; i < 4; i++) {
            double x = (i % 2 == 0 ? 1 : -1) * WHEEL_X, z = i < 2 ? WHEEL_ZF : WHEEL_ZR;
            m.group(groups[i]);
            for (int k = 0; k < 3; k++) m.boxC(BLACK_CONCRETE, x, WHEEL_R, z, 0.22, side, side, 'x', 15 + k * 30);
            m.boxC(sheet ? GRAY_CONCRETE : IRON_BLOCK, x + Math.signum(x) * 0.115, WHEEL_R, z, 0.02, 0.32, 0.32, 'x', 45);
        }
        m.group(G_BODY);
        if (sheet) {
            m.box(LIGHT_GRAY_WOOL, -0.9, 0.3, -2.12, 1.8, 1.15, 4.24);
        }
        if (live) {
            // interior seen through the glass: seat backs, rear bench, steering wheel
            m.box(BLACK_WOOL, -0.68, 0.55, -0.2, 0.56, 0.62, 0.12);
            m.box(BLACK_WOOL, 0.12, 0.55, -0.2, 0.56, 0.62, 0.12);
            m.box(BLACK_WOOL, -0.7, 0.55, -1.12, 1.4, 0.6, 0.14);
            m.push().translate(0.4, 0.98, 0.42).rotate('x', -30);
            m.box(BLACK_CONCRETE, -0.17, -0.17, 0, 0.34, 0.34, 0.03);
            m.pop();
            // high-level brake light and reversing lights
            m.group(G_BRAKE).box(RED_CONCRETE, -0.2, 1.3, -1.02, 0.4, 0.04, 0.03);
            m.group(G_REVERSE).box(LIGHT_GRAY_CONCRETE, -0.48, 0.58, -2.065, 0.12, 0.1, 0.03);
            m.group(G_REVERSE).box(LIGHT_GRAY_CONCRETE, 0.36, 0.58, -2.065, 0.12, 0.1, 0.03);
            m.group(G_BODY);
        } else {
            // two hitboxes (front and rear half) so the whole car can be clicked
            m.hitbox("car", 0, 0, 1.0, 2.0, 1.5);
            m.hitbox("car", 0, 0, -1.0, 2.0, 1.5);
        }
        return m;
    }

    /** Wheel centre in model space for a wheel group. */
    public static Vector3f wheelCentre(int group) {
        return v(group == G_WHEEL_FL || group == G_WHEEL_RL ? WHEEL_X : -WHEEL_X, WHEEL_R, group <= G_WHEEL_FR ? WHEEL_ZF : WHEEL_ZR);
    }

    // ------------------------------------------------------------------ steam pipes

    /** Joint positions (local x) of a pipe run of this length: flanged couplings every 4 blocks. */
    private static List<Double> joints(double len) {
        List<Double> out = new ArrayList<>();
        for (double x = 2.0; x < len - 0.3; x += 4) out.add(x);
        return out;
    }

    /** Valve position (local x) along a run. */
    private static double valveX(double len, long seed) {
        return Math.min(len - 1, 7.4 + Rng.pick(Rng.hash(seed, 3), 2));
    }

    /**
     * The leaking points of a pipe run in model space (the run lies along +X from the anchor): a failed coupling on
     * most runs and sometimes the valve gland. Shared by the model's particles and the steam hiss sources.
     */
    public static List<Vector3f> leaks(double len, long seed) {
        List<Vector3f> out = new ArrayList<>();
        List<Double> j = joints(len);
        if (!j.isEmpty() && Rng.chance(Rng.hash(seed, 1), 0.65)) out.add(v(j.get(Rng.pick(Rng.hash(seed, 2), j.size())), -0.14, 0));
        if (Rng.chance(Rng.hash(seed, 4), 0.35)) out.add(v(valveX(len, seed), -0.42, 0));
        return out;
    }

    /** Ceiling services: an insulated steam main with flanged couplings, a condensate line, hangers and a valve. */
    static void pipes(Model m, double len, long seed) {
        double r = 0.24, hang = 1.2;
        m.box(LIGHT_GRAY_CONCRETE, 0, -r / 2, -r / 2, len, r, r);
        m.box(WAXED_EXPOSED_COPPER, 0, -0.04, r / 2 + 0.06, len, 0.11, 0.11);
        for (double x : joints(len)) m.box(GRAY_CONCRETE, x - 0.05, -r / 2 - 0.04, -r / 2 - 0.04, 0.1, r + 0.08, r + 0.08);
        for (double x : new double[]{0.9, 5.5, 12.5}) {
            if (x > len - 0.2) continue;
            m.rod(BLACK_CONCRETE, v(x, r / 2, 0), v(x, hang, 0), 0.04);
        }
        double vx = valveX(len, seed);
        m.box(GRAY_CONCRETE, vx - 0.13, -r / 2 - 0.16, -0.1, 0.26, 0.18, 0.2);
        m.rod(GRAY_CONCRETE, v(vx, -r / 2 - 0.16, 0), v(vx, -0.5, 0), 0.04);
        m.boxC(RED_CONCRETE, vx, -0.5, 0, 0.3, 0.025, 0.3, 'y', 0);
        m.boxC(RED_CONCRETE, vx, -0.5, 0, 0.3, 0.025, 0.3, 'y', 45);
        if (Rng.chance(seed, 0.5)) m.box(BROWN_TERRACOTTA, len * 0.3, -r / 2 - 0.004, -r / 2 - 0.004, 0.5, r * 0.55, r + 0.008);
        for (Vector3f p : leaks(len, seed)) {
            // a steady thin plume, puffs, and now and then a heavier cloud
            m.emit(Particle.WHITE_SMOKE, null, p.x, p.y, p.z, 2, 0.04, 0.012, 4, 0.8);
            m.emit(Particle.CLOUD, null, p.x, p.y - 0.05, p.z, 1, 0.06, 0.015, 12, 0.6);
            m.emit(Particle.CLOUD, null, p.x, p.y - 0.1, p.z, 6, 0.15, 0.03, 80, 0.35);
        }
    }
}
