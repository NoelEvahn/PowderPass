package com.powderpass.backrooms.vehicle;

import com.powderpass.backrooms.display.GarageProps;
import com.powderpass.backrooms.display.Model;
import com.powderpass.backrooms.display.Part;
import com.powderpass.backrooms.gen.DecorSpec;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One drivable car: the display entities of its body, four invisible seats and two click hitboxes, all moved
 * together every tick from {@link CarPhysics}. Created from a parked car decoration (whose spec it remembers, so the
 * decoration can come back when the car is abandoned).
 */
final class Car {
    /** Seat positions in model space (+X = left): driver front left, passenger front right, then the rear bench. */
    static final double[][] SEATS = {{0.38, 0.13, 0.02}, {-0.38, 0.13, 0.02}, {0.38, 0.13, -0.95}, {-0.38, 0.13, -0.95}};
    static final int TELEPORT_TICKS = 2;

    final DecorSpec spec;
    final World world;
    final CarPhysics phys;
    final Model model;
    final List<Entity> body = new ArrayList<>();
    final List<Part> bodyParts = new ArrayList<>();
    final ItemDisplay[] seats = new ItemDisplay[4];
    final UUID[] riders = new UUID[4];
    final Interaction[] hitboxes = new Interaction[2];
    boolean engine;
    long abandonedAt = -1;
    /** Last light state applied: bit 0 engine, 1 braking, 2 reversing. */
    int lightState = -1;
    double lastSpin, lastSteer;
    double sentX, sentY, sentZ, sentYaw;
    long nextEngineSound;
    float enginePitch;
    boolean hornWas;
    final List<Location> fakeLights = new ArrayList<>();
    final List<UUID> fakeLightViewers = new ArrayList<>();

    Car(DecorSpec spec, World world, NamespacedKey tag) {
        this.spec = spec;
        this.world = world;
        this.phys = new CarPhysics(spec.x, spec.y, spec.z, spec.yaw);
        this.model = GarageProps.car(new Model(), spec.seed, true);
        Location at = location();
        for (Part p : model.parts) {
            if (p.kind != Part.Kind.BLOCK) continue;
            BlockDisplay d = world.spawn(at, BlockDisplay.class, e -> {
                e.setPersistent(false);
                e.setBlock(p.block);
                e.setTransformationMatrix(new Matrix4f(p.local));
                e.setTeleportDuration(TELEPORT_TICKS);
                e.setInterpolationDuration(1);
                e.setViewRange(0.9f);
                e.setShadowRadius(0);
                if (p.brightness != null) e.setBrightness(new Display.Brightness(p.brightness, 15));
                e.getPersistentDataContainer().set(tag, PersistentDataType.STRING, spec.key());
            });
            body.add(d);
            bodyParts.add(p);
        }
        for (int i = 0; i < 4; i++) {
            seats[i] = world.spawn(seatLocation(i), ItemDisplay.class, e -> {
                e.setPersistent(false);
                e.setTeleportDuration(TELEPORT_TICKS);
                e.getPersistentDataContainer().set(tag, PersistentDataType.STRING, spec.key());
            });
        }
        for (int i = 0; i < 2; i++) {
            int k = i;
            hitboxes[i] = world.spawn(hitboxLocation(i), Interaction.class, e -> {
                e.setPersistent(false);
                e.setInteractionWidth(2.0f);
                e.setInteractionHeight(1.5f);
                e.setResponsive(true);
                e.getPersistentDataContainer().set(tag, PersistentDataType.STRING, spec.key() + "#" + k);
            });
        }
        sentX = phys.x;
        sentY = phys.y;
        sentZ = phys.z;
        sentYaw = phys.yaw;
    }

    Location location() {
        return new Location(world, phys.x, phys.y, phys.z, (float) phys.yaw, 0);
    }

    Location seatLocation(int i) {
        double[] w = phys.toWorld(SEATS[i][0], SEATS[i][1], SEATS[i][2]);
        return new Location(world, w[0], w[1], w[2], (float) phys.yaw, 0);
    }

    Location hitboxLocation(int i) {
        double[] w = phys.toWorld(0, 0, i == 0 ? 1.0 : -1.0);
        return new Location(world, w[0], w[1], w[2]);
    }

    int seatOf(UUID player) {
        for (int i = 0; i < 4; i++) if (player.equals(riders[i])) return i;
        return -1;
    }

    int freeSeat() {
        for (int i = 0; i < 4; i++) if (riders[i] == null) return i;
        return -1;
    }

    boolean empty() {
        for (UUID r : riders) if (r != null) return false;
        return true;
    }

    UUID driver() {
        return riders[0];
    }

    boolean owns(Entity e) {
        for (Entity b : seats) if (b == e) return true;
        for (Entity b : hitboxes) if (b == e) return true;
        return body.contains(e);
    }

    boolean valid() {
        for (Entity s : seats) if (s == null || !s.isValid()) return false;
        return true;
    }

    /** Move every entity to the current pose (only when it changed). */
    @SuppressWarnings("removal") // EntityState is deprecated since 1.21.10 but still honoured; keeps riders seated
    void sync() {
        boolean moved = Math.abs(phys.x - sentX) > 1e-4 || Math.abs(phys.y - sentY) > 1e-4 || Math.abs(phys.z - sentZ) > 1e-4
                || Math.abs(phys.yaw - sentYaw) > 1e-3;
        if (moved) {
            Location at = location();
            for (Entity e : body) if (e.isValid()) e.teleport(at);
            for (int i = 0; i < 4; i++) {
                if (seats[i] != null && seats[i].isValid()) seats[i].teleport(seatLocation(i), TeleportFlag.EntityState.RETAIN_PASSENGERS);
            }
            for (int i = 0; i < 2; i++) if (hitboxes[i] != null && hitboxes[i].isValid()) hitboxes[i].teleport(hitboxLocation(i));
            sentX = phys.x;
            sentY = phys.y;
            sentZ = phys.z;
            sentYaw = phys.yaw;
        }
        // wheels: spin (capped so the strobing never reverses them) and front-wheel steering
        double spinStep = Math.max(-0.6, Math.min(0.6, phys.wheelSpin - lastSpin));
        if (Math.abs(phys.wheelSpin - lastSpin) > 1e-3 || Math.abs(phys.steer - lastSteer) > 1e-3) {
            lastSpin = phys.wheelSpin;
            lastSteer = phys.steer;
            visualSpin += spinStep;
            double steerDeg = -phys.steer * Math.toDegrees(CarPhysics.MAX_STEER);
            for (int i = 0; i < body.size(); i++) {
                Part p = bodyParts.get(i);
                if (p.group < GarageProps.G_WHEEL_FL || p.group > GarageProps.G_WHEEL_RR) continue;
                Vector3f c = GarageProps.wheelCentre(p.group);
                Matrix4f m = new Matrix4f().translate(c);
                if (p.group <= GarageProps.G_WHEEL_FR) m.rotateY((float) Math.toRadians(steerDeg));
                m.rotateX((float) visualSpin).translate(-c.x, -c.y, -c.z).mul(p.local);
                if (body.get(i) instanceof Display d && d.isValid()) {
                    d.setInterpolationDelay(0);
                    d.setInterpolationDuration(1);
                    d.setTransformationMatrix(m);
                }
            }
        }
    }

    private double visualSpin;

    /** Head, tail, brake and reversing lights from the engine and pedal state. */
    void lights() {
        int state = (engine ? 1 : 0) | (engine && phys.braking ? 2 : 0) | (engine && phys.reversing ? 4 : 0);
        if (state == lightState) return;
        lightState = state;
        boolean on = (state & 1) != 0, brake = (state & 2) != 0, rev = (state & 4) != 0;
        for (int i = 0; i < body.size(); i++) {
            Part p = bodyParts.get(i);
            if (!(body.get(i) instanceof BlockDisplay d) || !d.isValid()) continue;
            switch (p.group) {
                case GarageProps.G_HEAD -> set(d, on ? Material.WHITE_CONCRETE : Material.LIGHT_GRAY_CONCRETE, on ? 15 : -1);
                case GarageProps.G_TAIL -> set(d, Material.RED_CONCRETE, brake || rev && on ? 15 : on ? 8 : -1);
                case GarageProps.G_BRAKE -> set(d, brake ? Material.RED_CONCRETE : Material.RED_TERRACOTTA, brake ? 15 : -1);
                case GarageProps.G_REVERSE -> set(d, rev ? Material.WHITE_CONCRETE : Material.LIGHT_GRAY_CONCRETE, rev ? 15 : -1);
                default -> {
                }
            }
        }
    }

    private static void set(BlockDisplay d, Material m, int glow) {
        BlockData b = m.createBlockData();
        if (!d.getBlock().equals(b)) d.setBlock(b);
        d.setBrightness(glow < 0 ? null : new Display.Brightness(glow, 15));
    }

    /** Is this brightness/light state the brake state (for tests). */
    boolean brakeLightsOn() {
        return (lightState & 2) != 0;
    }

    List<Player> riderPlayers() {
        List<Player> out = new ArrayList<>();
        for (UUID r : riders) {
            if (r == null) continue;
            Player p = org.bukkit.Bukkit.getPlayer(r);
            if (p != null) out.add(p);
        }
        return out;
    }

    void removeEntities() {
        for (Entity e : body) if (e.isValid()) e.remove();
        for (Entity e : seats) if (e != null && e.isValid()) e.remove();
        for (Entity e : hitboxes) if (e != null && e.isValid()) e.remove();
        body.clear();
    }
}
