package dev.powderpass.lift;

import dev.powderpass.display.Displays;
import dev.powderpass.display.Transforms;
import dev.powderpass.lift.geom.ChairSwing;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One chair on the haul rope. The chair itself is a set of block displays riding an invisible anchor at the
 * first seat; every further seat has its own anchor so each rider sits exactly in place. The whole assembly
 * hangs from the grip and swings as a pendulum.
 *
 * <p>Grip-local frame: origin on the rope, +Z = direction of travel, +X = left, +Y = up.
 */
final class Chair {

    private enum Part { GRIP, ARM, HANGER, SEAT, BACK, BAR, FOOTREST }

    private static final float EPSILON = 0.003f;
    private static final float BAR_UP = -105f;

    private final int seats;
    private final float hanger;
    private final float width;
    private final Vector3f[] seatLocal;
    private final ChairSwing swing = new ChairSwing();
    private final UUID[] riders;

    double s;
    private double prevSpeed;
    private final Vector3d prevDir = new Vector3d(0, 0, 1);
    private float barAngle = BAR_UP;

    private ItemDisplay[] anchors;
    private final List<BlockDisplay> parts = new ArrayList<>();
    private final List<Part> partKinds = new ArrayList<>();
    private final List<Matrix4f> sent = new ArrayList<>();

    private final Vector3d grip = new Vector3d();
    private final Vector3d dir = new Vector3d(0, 0, 1);
    private final Vector3d[] seatWorld;

    Chair(double s, int seats, double hanger) {
        this.s = s;
        this.seats = seats;
        this.hanger = (float) hanger;
        this.width = seats * 0.8f + 0.15f;
        this.riders = new UUID[seats];
        this.seatLocal = new Vector3f[seats];
        this.seatWorld = new Vector3d[seats];
        for (int i = 0; i < seats; i++) {
            float x = (seats - 1) * 0.4f - i * 0.8f;
            seatLocal[i] = new Vector3f(x, -this.hanger + 0.06f, -0.04f);
            seatWorld[i] = new Vector3d();
        }
    }

    // ------------------------------------------------------------------------------------------------ simulation

    /** Advances along the rope. Returns the arc length travelled. */
    double advance(Lift lift) {
        double v = lift.definition().speed * lift.layout().speedFactor(s);
        double before = s;
        s = lift.layout().path().wrap(s + v);
        lift.layout().path().direction(s, dir);
        double cross = prevDir.x * dir.z - prevDir.z * dir.x;
        double lateral = -cross * v;
        double tangential = v - prevSpeed;
        swing.tick(lateral, tangential);
        prevSpeed = v;
        prevDir.set(dir);
        boolean inStation = lift.layout().speedFactor(s) < 0.9;
        float target = inStation ? BAR_UP : 0f;
        barAngle += Math.max(-7f, Math.min(7f, target - barAngle));
        lift.layout().path().position(s, grip);
        computeSeats();
        double moved = s - before;
        return moved < 0 ? moved + lift.layout().path().length() : moved;
    }

    private Matrix4f rotation() {
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        return new Matrix4f().rotateY((float) Math.toRadians(-yaw))
                .rotateZ((float) Math.toRadians(-swing.roll()))
                .rotateX((float) Math.toRadians(swing.pitch()));
    }

    private void computeSeats() {
        Matrix4f r = rotation();
        for (int i = 0; i < seats; i++) {
            Vector3f local = r.transformPosition(new Vector3f(seatLocal[i]));
            seatWorld[i].set(grip.x + local.x, grip.y + local.y, grip.z + local.z);
        }
    }

    void kick(double roll, double pitch) {
        swing.impulse(roll, pitch);
    }

    // ------------------------------------------------------------------------------------------------ entities

    boolean spawned() {
        return anchors != null && anchors[0] != null && anchors[0].isValid();
    }

    boolean chunkLoaded(World world) {
        return world.isChunkLoaded((int) Math.floor(grip.x) >> 4, (int) Math.floor(grip.z) >> 4);
    }

    void spawn(World world, LiftStyle style, int teleportDuration) {
        if (spawned()) {
            return;
        }
        despawnEntities();
        anchors = new ItemDisplay[seats];
        Matrix4f rot = rotation();
        for (int i = 0; i < seats; i++) {
            Location at = new Location(world, seatWorld[i].x, seatWorld[i].y, seatWorld[i].z);
            anchors[i] = Displays.anchor(at, teleportDuration, null);
        }
        Location at0 = anchors[0].getLocation();
        for (Part kind : Part.values()) {
            Matrix4f m = partMatrix(kind, rot);
            BlockDisplay d = Displays.block(at0, material(kind, style).createBlockData(), m, 1.6f, null);
            d.setInterpolationDuration(teleportDuration);
            anchors[0].addPassenger(d);
            parts.add(d);
            partKinds.add(kind);
            sent.add(new Matrix4f(m));
        }
    }

    private static Material material(Part kind, LiftStyle style) {
        return switch (kind) {
            case GRIP, ARM -> style.grip();
            case HANGER -> style.arm();
            case SEAT -> style.seat();
            case BACK -> style.back();
            case BAR, FOOTREST -> style.bar();
        };
    }

    /** Matrix of a part relative to the first seat anchor. */
    private Matrix4f partMatrix(Part kind, Matrix4f rot) {
        Matrix4f frame = new Matrix4f(rot).translate(-seatLocal[0].x, -seatLocal[0].y, -seatLocal[0].z);
        float w = width;
        float h = hanger;
        return switch (kind) {
            case GRIP -> Transforms.box(frame, -0.15f, -0.14f, -0.22f, 0.3f, 0.28f, 0.44f);
            case ARM -> Transforms.box(frame, -0.05f, -0.24f, -0.5f, 0.1f, 0.1f, 0.5f);
            case HANGER -> Transforms.box(frame, -0.05f, -h + 0.62f, -0.5f, 0.1f, h - 0.76f, 0.1f);
            case SEAT -> Transforms.box(frame, -w / 2f, -h - 0.06f, -0.38f, w, 0.12f, 0.66f);
            case BACK -> Transforms.box(frame, -w / 2f, -h + 0.06f, -0.46f, w, 0.62f, 0.08f);
            case BAR -> new Matrix4f(frame).translate(0f, -h + 0.66f, -0.42f).rotateX((float) Math.toRadians(barAngle))
                    .translate(-w / 2f, -0.2f, 0.72f).scale(w, 0.07f, 0.07f);
            case FOOTREST -> new Matrix4f(frame).translate(0f, -h + 0.66f, -0.42f).rotateX((float) Math.toRadians(barAngle))
                    .translate(-w / 2f + 0.12f, -0.8f, 0.72f).scale(w - 0.24f, 0.06f, 0.28f);
        };
    }

    /** Pushes the current position and pose to the entities. */
    void render(World world, boolean detailed) {
        if (!spawned()) {
            return;
        }
        for (int i = 0; i < seats; i++) {
            ItemDisplay a = anchors[i];
            if (a != null && a.isValid()) {
                // Passengers (riders and chair parts) are retained by default since 1.21.10.
                a.teleport(new Location(world, seatWorld[i].x, seatWorld[i].y, seatWorld[i].z));
            }
        }
        if (!detailed) {
            return;
        }
        Matrix4f rot = rotation();
        for (int k = 0; k < parts.size(); k++) {
            BlockDisplay d = parts.get(k);
            if (!d.isValid()) {
                continue;
            }
            Matrix4f m = partMatrix(partKinds.get(k), rot);
            if (!Transforms.close(sent.get(k), m, EPSILON)) {
                d.setInterpolationDelay(0);
                d.setTransformationMatrix(m);
                sent.get(k).set(m);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ riders

    int seats() {
        return seats;
    }

    UUID rider(int seat) {
        return riders[seat];
    }

    boolean hasRiders() {
        for (UUID r : riders) {
            if (r != null) {
                return true;
            }
        }
        return false;
    }

    Vector3d seatWorld(int seat) {
        return seatWorld[seat];
    }

    Vector3d direction() {
        return dir;
    }

    Vector3d grip() {
        return grip;
    }

    /** Seats {@code player} in {@code seat}. */
    boolean board(Player player, int seat) {
        if (!spawned() || riders[seat] != null || anchors[seat] == null || !anchors[seat].isValid()) {
            return false;
        }
        if (!anchors[seat].addPassenger(player)) {
            return false;
        }
        riders[seat] = player.getUniqueId();
        kick(seat == 0 ? 1.5 : -1.5, -2.0);
        return true;
    }

    void clearRider(int seat) {
        riders[seat] = null;
    }

    /** The seat index whose anchor is {@code vehicle}, or -1. */
    int seatOf(Entity vehicle) {
        if (anchors == null) {
            return -1;
        }
        for (int i = 0; i < anchors.length; i++) {
            if (anchors[i] != null && anchors[i].equals(vehicle)) {
                return i;
            }
        }
        return -1;
    }

    /** Validates rider bookkeeping against reality (someone may have been removed by other means). */
    void validateRiders() {
        if (anchors == null) {
            return;
        }
        for (int i = 0; i < seats; i++) {
            if (riders[i] == null) {
                continue;
            }
            ItemDisplay a = anchors[i];
            boolean seated = false;
            if (a != null && a.isValid()) {
                for (Entity p : a.getPassengers()) {
                    if (p.getUniqueId().equals(riders[i])) {
                        seated = true;
                        break;
                    }
                }
            }
            if (!seated) {
                riders[i] = null;
            }
        }
    }

    void despawnEntities() {
        for (BlockDisplay d : parts) {
            if (d.isValid()) {
                d.remove();
            }
        }
        parts.clear();
        partKinds.clear();
        sent.clear();
        if (anchors != null) {
            for (ItemDisplay a : anchors) {
                if (a != null && a.isValid()) {
                    a.eject();
                    a.remove();
                }
            }
        }
        anchors = null;
        for (int i = 0; i < seats; i++) {
            riders[i] = null;
        }
    }

    ItemDisplay anchor(int seat) {
        return anchors == null ? null : anchors[seat];
    }
}
