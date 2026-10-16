package com.powderpass.backrooms.creature;

import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.config.BackroomsConfig;
import io.papermc.paper.entity.LookAnchor;
import io.papermc.paper.entity.TeleportFlag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A player taken by a skinwalker. The player is <b>not</b> a spectator: they stay in their own body, in their own
 * game mode, visible to everyone with their own skin, and are carried by a black tentacled organism that grows out
 * of their back ({@link TentacleRig}). The AI moves them, not the player.
 * <p>
 * <b>How the body is moved.</b> A grounded, collision-aware controller ({@link GroundController}) is the real body:
 * it paths over real blocks, steps up single blocks, falls with gravity and never leaves loaded chunks. The player
 * hovers just above it. Their own movement and jumping are taken away with attribute modifiers (speed, jump
 * strength and gravity set to zero), so the only thing that moves them is the velocity the server sends every
 * tick: the controller's motion plus a correction toward it. The player's client therefore still collides with
 * real blocks and simulates the motion smoothly (no teleport jitter, no flying, no clipping). Only when the client
 * drifts more than two blocks off (lag, a hacked client) is it put back with a teleport.
 * <p>
 * <b>Safety.</b> Before anything changes, a flag with the player's original state is written to their
 * PersistentDataContainer, and every modifier carries a fixed key, so a disconnect, a death, a reload or a crash
 * always gives the player back exactly what they had (see {@link Possessions#restore}).
 */
final class Possession {
    enum Stage { TRANSFORMING, ACTIVE }

    /** Length of the transformation, in ticks (5.5 s). */
    static final int TRANSFORM_TICKS = 110;
    static final double HOVER = 0.72;

    private final BackroomsPlugin plugin;
    private final Possessions registry;
    final Skinwalker walker;
    final UUID victimId;
    final String victimName;
    private final AtomicReference<double[]> voicePos;
    final GroundController ground;
    final TentacleRig rig;
    private boolean active = true;
    Stage stage = Stage.TRANSFORMING;
    int ticks;
    private int nextChat, nextVoice, nextJerk, breathIn, repathIn, nextLook;
    private double hover, prevTx, prevTy, prevTz, heading;
    private List<int[]> path = List.of();
    private int pathIndex;
    // goal set by the AI
    private double goalX, goalY, goalZ, goalSpeed;
    private boolean faceTarget;
    private Player lookTarget;
    // attacks
    private int strikeTick = -1, punchTick = -1;
    private Player strikeTarget;
    private double strikeDamage;
    private Interaction hitbox;
    private int lastRigUpdate;

    Possession(BackroomsPlugin plugin, Possessions registry, Skinwalker walker, Player victim) {
        this.plugin = plugin;
        this.registry = registry;
        this.walker = walker;
        this.victimId = victim.getUniqueId();
        this.victimName = victim.getName();
        Location at = victim.getLocation();
        // persisted first: whatever happens next, the victim can be restored
        PersistentDataContainer pdc = victim.getPersistentDataContainer();
        pdc.set(registry.victimKey(), PersistentDataType.STRING, victim.getGameMode().name());
        pdc.set(registry.flySpeedKey(), PersistentDataType.FLOAT, victim.getFlySpeed());
        pdc.set(registry.flightKey(), PersistentDataType.BYTE, (byte) ((victim.getAllowFlight() ? 1 : 0) | (victim.isFlying() ? 2 : 0) | (victim.hasGravity() ? 4 : 0)));
        this.ground = new GroundController(at.getWorld(), at.getX(), at.getY(), at.getZ());
        ground.reset(at.getX(), at.getY(), at.getZ());
        this.heading = at.getYaw();
        this.goalX = ground.x;
        this.goalY = ground.y;
        this.goalZ = ground.z;
        this.prevTx = ground.x;
        this.prevTy = ground.smoothY;
        this.prevTz = ground.z;
        this.voicePos = VoiceBridge.tracker(at);
        lock(victim);
        this.rig = new TentacleRig(victimId.getMostSignificantBits() ^ System.nanoTime());
        rig.spawn(victim, walker.id.toString());
        BackroomsConfig c = plugin.cfg();
        nextChat = c.skinwalkerChatMimic ? 20 * (25 + ThreadLocalRandom.current().nextInt(40)) : Integer.MAX_VALUE;
        nextVoice = c.skinwalkerVoiceMimic ? 20 * (20 + ThreadLocalRandom.current().nextInt(40)) : Integer.MAX_VALUE;
    }

    // ------------------------------------------------------------------ locks

    /** Freeze the player in their body: no walking, no jumping, no gravity of their own, no flying. */
    private void lock(Player v) {
        modifier(v, Attribute.MOVEMENT_SPEED, registry.lockKey("speed"), -1, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        modifier(v, Attribute.JUMP_STRENGTH, registry.lockKey("jump"), -1, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        modifier(v, Attribute.GRAVITY, registry.lockKey("gravity"), -1, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        modifier(v, Attribute.STEP_HEIGHT, registry.lockKey("step"), 0.5, AttributeModifier.Operation.ADD_NUMBER);
        modifier(v, Attribute.SAFE_FALL_DISTANCE, registry.lockKey("fall"), 1000, AttributeModifier.Operation.ADD_NUMBER);
        // allowed flight only so the server never kicks a hovering player for flying; flight itself is cancelled
        // (Possessions#onToggleFlight) and has no speed
        v.setAllowFlight(true);
        v.setFlying(false);
        v.setFlySpeed(0f);
        v.setGravity(false);
        v.setVelocity(new Vector());
        v.setFallDistance(0);
        if (v.isInsideVehicle()) v.leaveVehicle();
        registry.voice().mute(victimId, true);
        v.sendActionBar(Component.text("You can't move.").color(NamedTextColor.DARK_RED));
    }

    static void modifier(Player v, Attribute a, NamespacedKey key, double amount, AttributeModifier.Operation op) {
        try {
            AttributeInstance inst = v.getAttribute(a);
            if (inst == null) return;
            inst.removeModifier(key);
            inst.addModifier(new AttributeModifier(key, amount, op, EquipmentSlotGroup.ANY));
        } catch (RuntimeException ex) {
            // an attribute the server does not support for players: the velocity control still holds them
        }
    }

    boolean active() {
        return active;
    }

    Player victim() {
        return plugin.getServer().getPlayer(victimId);
    }

    /** Where the organism's body is (the ground under the hovering player). */
    Location root() {
        return new Location(ground.world(), ground.x, ground.y, ground.z, (float) heading, 0);
    }

    double heading() {
        return heading;
    }

    void mountHitbox(Interaction hb) {
        this.hitbox = hb;
        Player v = victim();
        if (v != null && hb != null) v.addPassenger(hb);
    }

    // ------------------------------------------------------------------ AI input

    /** Where the AI wants the body to go, and how fast (blocks per tick); {@code look} is who to stare at, or null. */
    void goal(double x, double y, double z, double speed, Player look) {
        boolean moved = Math.abs(x - goalX) + Math.abs(z - goalZ) > 1.5;
        goalX = x;
        goalY = y;
        goalZ = z;
        goalSpeed = speed;
        lookTarget = look;
        faceTarget = look != null;
        if (moved) repathIn = 0;
    }

    boolean blocked() {
        return ground.blocked;
    }

    /** Lash at the target with the striking tentacle (damage lands when the tip arrives). */
    void strike(Player target, double damage) {
        if (strikeTick >= 0 || punchTick >= 0) return;
        strikeTarget = target;
        strikeDamage = damage;
        strikeTick = 0;
        walker.sfx(root(), "skinwalker.screech", Sound.ENTITY_WARDEN_ROAR, 0.7f, 1.7f);
    }

    /** Punch with the victim's own arm. */
    void punch(Player target, double damage) {
        if (strikeTick >= 0 || punchTick >= 0) return;
        strikeTarget = target;
        strikeDamage = damage;
        punchTick = 0;
    }

    boolean attacking() {
        return strikeTick >= 0 || punchTick >= 0;
    }

    // ------------------------------------------------------------------ every tick

    /** One server tick: transformation or locomotion, the pull on the player, attacks, sounds, the rig. */
    void tick() {
        if (!active) return;
        Player v = victim();
        if (v == null || !v.isOnline() || v.isDead()) {
            release(false);
            return;
        }
        ticks++;
        if (v.getWorld() != ground.world()) {
            // something moved them to another world: that ends it
            release(false);
            return;
        }
        if (stage == Stage.TRANSFORMING) transform(v);
        else locomotion(v);
        pull(v);
        attacks(v);
        if (stage == Stage.ACTIVE) ambience(v);
        animate(v);
        if (hitbox != null && hitbox.isValid() && !v.getPassengers().contains(hitbox)) v.addPassenger(hitbox);
    }

    /** The transformation: a freeze, cracking and jerks, the back tearing open, tentacles, the lift, the reveal. */
    private void transform(Player v) {
        int t = ticks;
        World w = ground.world();
        Location back = backPoint(v);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (t == 1) {
            // a loud crack in the silence, as everything in them locks
            walker.sfx(back, "skinwalker.crackle", Sound.BLOCK_BONE_BLOCK_BREAK, 2.0f, 0.5f);
            walker.sfx(back, "skinwalker.crackle", Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.8f, 0.6f);
            nextJerk = 5;
        }
        // unnatural jerks: the head snaps round, the body jolts, the arms twitch, the knees buckle
        if (t < 96 && t >= nextJerk) {
            nextJerk = t + (t < 40 ? 4 + r.nextInt(6) : 7 + r.nextInt(10));
            jerk(v, t < 40 ? 1.0 : 0.6);
            walker.sfx(back, "skinwalker.crackle", Sound.BLOCK_BONE_BLOCK_HIT, 1.1f, 0.4f + r.nextFloat() * 0.3f);
            w.spawnParticle(Particle.BLOCK, back, 6, 0.12, 0.15, 0.12, 0.05, Material.BONE_BLOCK.createBlockData());
        }
        if (t == 24) {
            // the back tears open
            walker.sfx(back, "skinwalker.tear", Sound.ENTITY_SLIME_SQUISH, 1.6f, 0.5f);
            walker.sfx(back, "skinwalker.tear", Sound.BLOCK_HONEY_BLOCK_BREAK, 1.2f, 0.5f);
            w.spawnParticle(Particle.BLOCK, back, 24, 0.15, 0.2, 0.15, 0.1, Material.NETHER_WART_BLOCK.createBlockData());
            w.spawnParticle(Particle.SQUID_INK, back, 18, 0.1, 0.15, 0.1, 0.05);
        }
        rig.wound = TentacleRig.clamp01((t - 24) / 10.0);
        // tentacles force their way out and flail
        rig.emerge = TentacleRig.smooth((t - 30) / 26.0);
        if (t >= 30 && t < 58 && t % 3 == 0) {
            w.spawnParticle(Particle.SQUID_INK, back, 4, 0.12, 0.12, 0.12, 0.03);
            if (t % 6 == 0) walker.sfx(back, "skinwalker.tear", Sound.ENTITY_SQUID_SQUIRT, 0.9f, 0.5f);
        }
        // they reach down and plant
        if (t == 56) rig.settle(ground, new Vector3d(ground.x, ground.y, ground.z), heading);
        rig.plant = TentacleRig.clamp01((t - 56) / 16.0);
        if (t == 66 || t == 70) {
            for (TentacleRig.Leg l : rig.legs) {
                if (!l.hasGround) continue;
                Location foot = new Location(w, l.plant.x, l.plant.y + 0.05, l.plant.z);
                w.spawnParticle(Particle.BLOCK, foot, 8, 0.15, 0.02, 0.15, 0.1, foot.clone().add(0, -0.5, 0).getBlock().getBlockData());
            }
            walker.sfx(back, "skinwalker.scuttle", Sound.ENTITY_IRON_GOLEM_STEP, 1.2f, 0.5f);
        }
        // and slowly lift the body off the ground
        double lift = TentacleRig.smooth((t - 72) / 24.0);
        hover = lift * (HOVER + 0.25 * Math.sin(Math.PI * Math.min(1, (t - 72) / 24.0)));
        if (t == 74 || t == 84) walker.sfx(back, "skinwalker.crackle", Sound.BLOCK_SCULK_SPREAD, 1.2f, 0.5f);
        // twitching, then the reveal
        rig.twitch = t >= 92 && t < 106 ? 0.9 : t >= 36 && t < 56 ? 0.4 : 0;
        if (t == 104) {
            Player near = nearestOther(v, 24);
            if (near != null) look(v, near.getEyeLocation());
            walker.sfx(back, "skinwalker.screech", Sound.ENTITY_WARDEN_ROAR, 1.6f, 0.8f);
            for (Player p : w.getPlayers()) {
                if (p != v && p.getLocation().distanceSquared(back) < 20 * 20) p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 50, 0, false, false));
            }
        }
        if (t >= TRANSFORM_TICKS) {
            stage = Stage.ACTIVE;
            rig.emerge = 1;
            rig.plant = 1;
            rig.wound = 1;
            rig.twitch = 0;
            breathIn = 20;
        }
        // keep the body where it was grabbed (gravity still applies to it)
        ground.move(0, 0);
    }

    private void jerk(Player v, double strength) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location eye = v.getEyeLocation();
        double yaw = Math.toRadians(eye.getYaw() + r.nextGaussian() * 70 * strength);
        double pitch = Math.max(-80, Math.min(80, r.nextGaussian() * 45 * strength));
        look(v, eye.clone().add(-Math.sin(yaw) * 5, -Math.tan(Math.toRadians(pitch)) * 5, Math.cos(yaw) * 5));
        swing(v, r.nextBoolean());
        // the knees buckle for a moment (everyone sees a crouch flicker)
        if (r.nextDouble() < 0.5 * strength) {
            v.setSneaking(true);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Player p = victim();
                if (p != null && active) p.setSneaking(false);
            }, 2 + r.nextInt(3));
        }
    }

    private void look(Player v, Location at) {
        try {
            v.lookAt(at.getX(), at.getY(), at.getZ(), LookAnchor.EYES);
        } catch (RuntimeException | AbstractMethodError ignored) {
            // not supported (test server)
        }
    }

    /** Walk the controller along a path toward the AI's goal. */
    private void locomotion(Player v) {
        double clearance = ground.clearance(4);
        ground.height = 1.85 + Math.min(0.1, Math.max(0, clearance - 1.9));
        double dx = goalX - ground.x, dz = goalZ - ground.z;
        double dist = Math.hypot(dx, dz);
        double mx = 0, mz = 0;
        if (goalSpeed > 0.005 && dist > 0.4) {
            if (--repathIn <= 0 || pathIndex >= path.size()) {
                int bx = (int) Math.floor(ground.x), by = (int) Math.floor(ground.y + 0.01), bz = (int) Math.floor(ground.z);
                path = Nav.path(ground.world(), bx, by, bz, (int) Math.floor(goalX), (int) Math.floor(goalY), (int) Math.floor(goalZ), 3, Nav.Medium.GROUND, 500);
                if (path.isEmpty()) path = Nav.path(ground.world(), bx, by, bz, (int) Math.floor(goalX), (int) Math.floor(goalY), (int) Math.floor(goalZ), 2, Nav.Medium.GROUND, 500);
                pathIndex = 0;
                repathIn = 10;
            }
            double wx, wz;
            if (pathIndex < path.size()) {
                int[] wp = path.get(pathIndex);
                wx = wp[0] + 0.5;
                wz = wp[2] + 0.5;
                if (Math.hypot(wx - ground.x, wz - ground.z) < 0.35) {
                    pathIndex++;
                    if (pathIndex < path.size()) {
                        wp = path.get(pathIndex);
                        wx = wp[0] + 0.5;
                        wz = wp[2] + 0.5;
                    }
                }
            } else if (dist < 2.5) {
                wx = goalX;
                wz = goalZ;
            } else {
                wx = ground.x;
                wz = ground.z;
            }
            double ddx = wx - ground.x, ddz = wz - ground.z, dd = Math.hypot(ddx, ddz);
            if (dd > 1e-3) {
                // ease in and out so it never jerks from standing to full speed
                double s = Math.min(goalSpeed, dd);
                mx = ddx / dd * s;
                mz = ddz / dd * s;
            }
        }
        ground.move(mx, mz);
        // the body turns toward its prey, or where it is going; never faster than a creature can turn
        double want = heading;
        if (faceTarget && lookTarget != null && lookTarget.isValid() && lookTarget.getWorld() == ground.world()) {
            want = yawTo(lookTarget.getLocation().getX() - ground.x, lookTarget.getLocation().getZ() - ground.z);
        } else if (ground.lastMoved > 0.02) {
            want = yawTo(mx, mz);
        }
        heading = approach(heading, want, 9);
        // the player's head follows the body's heading: it is what the organism looks through
        Location eye = v.getEyeLocation();
        double off = Math.abs(wrap(eye.getYaw() - heading));
        if (ticks >= nextLook && (off > 30 || (faceTarget && ThreadLocalRandom.current().nextDouble() < 0.05))) {
            nextLook = ticks + 12 + ThreadLocalRandom.current().nextInt(20);
            if (faceTarget && lookTarget != null && lookTarget.getWorld() == v.getWorld()) look(v, lookTarget.getEyeLocation());
            else {
                double h = Math.toRadians(heading);
                look(v, eye.clone().add(-Math.sin(h) * 6, -0.4, Math.cos(h) * 6));
            }
        }
        // hover: lower where the ceiling is low, a slow breathing bob, a sway with each tripod
        double room = Math.max(0, clearance - 1.85);
        double bob = 0.06 * Math.sin(ticks * 0.21) + (ground.lastMoved > 0.02 ? 0.05 * Math.sin(ticks * 0.9) : 0);
        hover = Math.max(0, Math.min(HOVER + bob, room));
    }

    /**
     * Pull the player to the body: the body's own motion plus a share of the error, as a velocity their client
     * simulates with real collisions. A player more than two blocks off is put back.
     */
    @SuppressWarnings("removal") // RETAIN_PASSENGERS: harmless where passengers are already kept by default
    private void pull(Player v) {
        double tx = ground.x, ty = ground.smoothY + hover, tz = ground.z;
        Location at = v.getLocation();
        double ex = tx - at.getX(), ey = ty - at.getY(), ez = tz - at.getZ();
        double err = Math.sqrt(ex * ex + ey * ey + ez * ez);
        if (err > 2.2) {
            Location dest = new Location(ground.world(), tx, ty, tz, at.getYaw(), at.getPitch());
            registry.ownTeleport(victimId, true);
            try {
                v.teleport(dest, TeleportFlag.EntityState.RETAIN_PASSENGERS);
            } finally {
                registry.ownTeleport(victimId, false);
            }
            rig.remount(v);
            v.setVelocity(new Vector());
        } else {
            double fx = tx - prevTx, fy = ty - prevTy, fz = tz - prevTz;
            double k = 0.4;
            Vector vel = new Vector(clamp(fx + ex * k, 0.9), clamp(fy + ey * k, 0.9), clamp(fz + ez * k, 0.9));
            v.setVelocity(vel);
        }
        prevTx = tx;
        prevTy = ty;
        prevTz = tz;
        v.setFallDistance(0);
        if (v.isFlying()) v.setFlying(false);
        voicePos.set(new double[]{tx, ty + 1.6, tz});
    }

    private void attacks(Player v) {
        if (strikeTick >= 0) {
            strikeTick++;
            // windup 6 ticks, lash 5, recoil 8
            rig.strike = strikeTick <= 6 ? strikeTick / 6.0 : strikeTick <= 11 ? 1 + (strikeTick - 6) / 5.0 : Math.min(3, 2 + (strikeTick - 11) / 8.0);
            if (strikeTarget != null && strikeTarget.isValid()) {
                Location t = strikeTarget.getLocation();
                rig.strikeAt.set(t.getX(), t.getY() + 1.1, t.getZ());
            }
            if (strikeTick == 7) walker.sfx(root(), "skinwalker.whip", Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.4f, 0.5f);
            if (strikeTick == 9) land(v, 4.4);
            if (strikeTick >= 19) {
                strikeTick = -1;
                rig.strike = 0;
            }
        }
        if (punchTick >= 0) {
            punchTick++;
            if (punchTick == 1 && strikeTarget != null) look(v, strikeTarget.getEyeLocation());
            if (punchTick == 3) swing(v, true);
            if (punchTick == 4) land(v, 2.8);
            if (punchTick >= 10) punchTick = -1;
        }
    }

    private static void swing(Player v, boolean main) {
        try {
            if (main) v.swingMainHand();
            else v.swingOffHand();
        } catch (RuntimeException ignored) {
            // not supported (test server)
        }
    }

    /** The blow lands if the target is still within reach and in sight. */
    private void land(Player v, double reach) {
        Player t = strikeTarget;
        if (t == null || !t.isValid() || t.getWorld() != ground.world()) return;
        Location c = v.getLocation().add(0, 1.2, 0);
        if (t.getLocation().add(0, 1, 0).distance(c) > reach) {
            walker.sfx(t.getLocation(), "skinwalker.whip", Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, 0.8f, 0.6f);
            return;
        }
        walker.landed(t, strikeDamage, c);
    }

    private void ambience(Player v) {
        Location here = v.getLocation();
        // breathing through the victim's mouth, wet and slow
        if (--breathIn <= 0) {
            breathIn = 50 + ThreadLocalRandom.current().nextInt(30);
            walker.sfx(here.clone().add(0, 1.5, 0), "breath.heavy", Sound.ENTITY_WOLF_PANT, 0.9f, 0.45f);
        }
        if (ThreadLocalRandom.current().nextDouble() < 0.008) walker.sfx(backPoint(v), "skinwalker.crackle", Sound.BLOCK_BONE_BLOCK_HIT, 0.8f, 0.5f);
        // its voice: things the victim said, in chat and out loud
        if ((nextChat -= 1) <= 0) {
            nextChat = 20 * (45 + ThreadLocalRandom.current().nextInt(75));
            String line = registry.recentLine(victimId);
            if (line != null) {
                Component msg = Component.text("<" + victimName + "> " + line);
                for (Player p : here.getWorld().getPlayers()) if (p != v) p.sendMessage(msg);
            }
        }
        if ((nextVoice -= 1) <= 0) {
            nextVoice = 20 * (40 + ThreadLocalRandom.current().nextInt(80));
            registry.voice().play(victimId, here.getWorld(), voicePos, plugin.cfg().skinwalkerVoiceDistance);
        }
    }

    private final List<Vector3d> landed = new ArrayList<>();

    /** Gait and pose, every second tick (every fourth when nobody but the victim is near). */
    private void animate(Player v) {
        int every = 2;
        if (stage == Stage.ACTIVE && nearestOther(v, 32) == null) every = 4;
        if (ticks - lastRigUpdate < every) return;
        int dt = ticks - lastRigUpdate;
        lastRigUpdate = ticks;
        Location at = v.getLocation();
        Vector3d feet = new Vector3d(at.getX(), at.getY(), at.getZ());
        Vector3d root = new Vector3d(ground.x, ground.y, ground.z);
        if (stage == Stage.ACTIVE) {
            Vector3d vel = new Vector3d(ground.x - prevRootX, 0, ground.z - prevRootZ).div(Math.max(1, dt));
            landed.clear();
            double speed = Math.hypot(vel.x, vel.z);
            int down = rig.gait(ground, root, heading, vel, dt, speed > 0.15 ? 4 : 6, landed);
            if (down > 0) {
                Location foot = new Location(ground.world(), landed.get(0).x, landed.get(0).y, landed.get(0).z);
                walker.sfx(foot, "skinwalker.crawl", Sound.ENTITY_SPIDER_STEP, 0.55f + 0.1f * down, 0.55f);
                if (ThreadLocalRandom.current().nextDouble() < 0.4) walker.sfx(foot, "skinwalker.scuttle", Sound.BLOCK_SLIME_BLOCK_STEP, 0.5f, 0.6f);
            }
            // now and then a limb twitches on its own
            rig.twitch = ThreadLocalRandom.current().nextDouble() < 0.03 ? 0.6 : Math.max(0, rig.twitch - 0.2);
        }
        prevRootX = ground.x;
        prevRootZ = ground.z;
        double ceiling = ground.ceilingY(ground.x, ground.y + 0.05, ground.z, 5);
        rig.update(feet, v.getHeight(), heading, ceiling, dt, dt + 1);
    }

    private double prevRootX, prevRootZ;

    private Location backPoint(Player v) {
        Location l = v.getLocation();
        double h = Math.toRadians(heading);
        return l.add(Math.sin(h) * 0.25, TentacleRig.BACK_Y, -Math.cos(h) * 0.25);
    }

    private Player nearestOther(Player v, double max) {
        Player best = null;
        double bd = max * max;
        for (Player p : v.getWorld().getPlayers()) {
            if (p == v || p.getGameMode() == GameMode.SPECTATOR) continue;
            double d = p.getLocation().distanceSquared(v.getLocation());
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ release

    /**
     * Let the victim go: the organism tears away, every lock comes off, they are put on safe ground. With
     * {@code dies}, they come back only to die (it kept them too long, or it fed).
     */
    void release(boolean dies) {
        if (!active) return;
        active = false;
        Player v = victim();
        Location at = v != null ? v.getLocation() : root();
        if (rig != null) {
            World w = at.getWorld();
            if (w != null && rig.size() > 0) {
                w.spawnParticle(Particle.SQUID_INK, at.clone().add(0, 1.3, 0), 30, 0.6, 0.6, 0.6, 0.04);
                walker.sfx(at, "skinwalker.tear", Sound.ENTITY_SLIME_SQUISH, 1.0f, 0.4f);
            }
            rig.remove(v);
        }
        if (hitbox != null && v != null) v.removePassenger(hitbox);
        registry.voice().mute(victimId, false);
        registry.forget(this);
        if (v == null) return; // restored from the PDC flag when they rejoin
        registry.restore(v, null, root());
        if (dies) {
            v.sendMessage(Component.text("It has no more use for you.").color(NamedTextColor.DARK_RED));
            v.setHealth(0);
        } else {
            v.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 160, 0, false, false));
            v.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false));
            v.sendMessage(Component.text("You are yourself again. Mostly.").color(NamedTextColor.GRAY));
        }
    }

    // ------------------------------------------------------------------ math

    static double clamp(double v, double m) {
        return Math.max(-m, Math.min(m, v));
    }

    static double yawTo(double dx, double dz) {
        return Math.toDegrees(Math.atan2(-dx, dz));
    }

    static double wrap(double a) {
        return ((a % 360) + 540) % 360 - 180;
    }

    static double approach(double from, double to, double maxStep) {
        double d = wrap(to - from);
        return from + Math.max(-maxStep, Math.min(maxStep, d));
    }

    /** All entities of the organism (for sweeps and tests). */
    List<Entity> parts() {
        List<Entity> out = new ArrayList<>(rig.entities());
        if (hitbox != null) out.add(hitbox);
        return out;
    }

    double hover() {
        return hover;
    }
}
