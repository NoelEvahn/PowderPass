package com.powderpass.backrooms.creature;

import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.config.BackroomsConfig;
import com.powderpass.backrooms.display.Keys;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A player being worn by a skinwalker.
 * <p>
 * <b>The victim</b> is put in spectator mode, locked onto their own body (they watch through its eyes), and
 * cannot move, type in chat, run commands or talk on voice chat (see {@link Possessions} for the locks). Their
 * original game mode is stored in their PersistentDataContainer before anything changes, so a crash or a
 * disconnect always restores them.
 * <p>
 * <b>The body</b> is a {@link Mannequin} wearing the victim's skin, name and visible equipment. It acts human:
 * it keeps to the group a few blocks behind whoever leads, walks at their pace with footsteps, looks where
 * people look and at whoever is closest, copies sneaks, jumps and arm swings after a human-like delay, idles and
 * glances around, punches back (harmlessly) when punched, repeats things the victim said in chat, and, with
 * Simple Voice Chat, plays back recordings of the victim's own voice.
 * <p>
 * It gives itself away when punched too often, and after {@code skinwalker.disguise-seconds} it stops pretending.
 */
final class Possession {
    private final BackroomsPlugin plugin;
    private final Possessions registry;
    final Skinwalker walker;
    final UUID victimId;
    final String victimName;
    final Mannequin body;
    private final GameMode originalMode;
    private final AtomicReference<double[]> voicePos;
    private boolean active = true, reveal, carried;
    private int ticks, nextChat, nextVoice, sneakFor, jumpTicks, idleLookFor, stareFor;
    private float lookYaw, lookPitch;
    private final Deque<Long> punches = new ArrayDeque<>();
    private Location lastLeader;
    private int stepIn;

    Possession(BackroomsPlugin plugin, Possessions registry, Skinwalker walker, Player victim) {
        this.plugin = plugin;
        this.registry = registry;
        this.walker = walker;
        this.victimId = victim.getUniqueId();
        this.victimName = victim.getName();
        this.originalMode = victim.getGameMode();
        Location at = victim.getLocation();
        // persisted first: whatever happens next, the victim can be restored
        victim.getPersistentDataContainer().set(registry.victimKey(), PersistentDataType.STRING, originalMode.name());
        this.body = at.getWorld().spawn(at, Mannequin.class, m -> {
            m.setPersistent(false);
            m.setInvulnerable(true);
            try {
                m.setProfile(ResolvableProfile.resolvableProfile(victim.getPlayerProfile()));
            } catch (RuntimeException | LinkageError ex) {
                plugin.getLogger().warning("[Skinwalker] could not copy " + victim.getName() + "'s skin: " + ex);
            }
            m.customName(victim.name());
            m.setCustomNameVisible(true);
            try {
                m.setDescription(null);
            } catch (RuntimeException ignored) {
                // older builds may require a component; the default description is acceptable
            }
            m.setMainHand(victim.getMainHand());
            EntityEquipment eq = m.getEquipment();
            EntityEquipment ve = victim.getEquipment();
            if (eq != null && ve != null) {
                eq.setHelmet(copy(ve.getHelmet()));
                eq.setChestplate(copy(ve.getChestplate()));
                eq.setLeggings(copy(ve.getLeggings()));
                eq.setBoots(copy(ve.getBoots()));
                eq.setItemInMainHand(copy(ve.getItemInMainHand()));
                eq.setItemInOffHand(copy(ve.getItemInOffHand()));
            }
            m.getPersistentDataContainer().set(Keys.CREATURE, PersistentDataType.STRING, walker.id.toString());
            m.setRotation(at.getYaw(), at.getPitch());
        });
        this.voicePos = VoiceBridge.tracker(at);
        this.lookYaw = at.getYaw();
        this.lookPitch = at.getPitch();
        lock(victim);
        BackroomsConfig c = plugin.cfg();
        nextChat = 20 * (25 + ThreadLocalRandom.current().nextInt(40));
        nextVoice = 20 * (20 + ThreadLocalRandom.current().nextInt(40));
        if (!c.skinwalkerChatMimic) nextChat = Integer.MAX_VALUE;
        if (!c.skinwalkerVoiceMimic) nextVoice = Integer.MAX_VALUE;
    }

    private static ItemStack copy(ItemStack s) {
        return s == null ? null : s.clone();
    }

    private void lock(Player v) {
        registry.setReleasing(victimId, true);
        v.setGameMode(GameMode.SPECTATOR);
        registry.setReleasing(victimId, false);
        v.setSpectatorTarget(body);
        v.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 80, 0, false, false));
        v.showTitle(Title.title(Component.text("You can't move.").color(net.kyori.adventure.text.format.NamedTextColor.DARK_RED),
                Component.text("Something is wearing you.").color(net.kyori.adventure.text.format.NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(4), Duration.ofSeconds(2))));
        v.playSound(v.getLocation(), Sound.BLOCK_BONE_BLOCK_BREAK, SoundCategory.HOSTILE, 1f, 0.5f);
        registry.voice().mute(victimId, true);
        // spectator target needs the body to be tracked first; assert it again next tick
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player p = plugin.getServer().getPlayer(victimId);
            if (p != null && active) p.setSpectatorTarget(body);
        }, 2L);
    }

    boolean active() {
        return active;
    }

    boolean wantsReveal() {
        return reveal;
    }

    Player victim() {
        return plugin.getServer().getPlayer(victimId);
    }

    // ------------------------------------------------------------------ acting human

    /** One AI step while disguised. Returns the body's location (the creature follows it). */
    Location tick(int dt) {
        ticks += dt;
        Player v = victim();
        if (v == null || !body.isValid()) {
            release(false);
            reveal = true;
            return null;
        }
        if (ticks % 10 < dt && v.getSpectatorTarget() != body) v.setSpectatorTarget(body);
        BackroomsConfig c = plugin.cfg();
        World w = body.getWorld();
        Location here = body.getLocation();
        List<Player> group = registry.groupAround(here, c.skinwalkerGroupRadius, this);
        if (group.isEmpty() || ticks > c.skinwalkerDisguiseSeconds * 20) {
            reveal = true; // nobody left to fool, or it has waited long enough
            return here;
        }
        Player leader = group.get(0);
        Location ll = leader.getLocation();
        double leaderSpeed = lastLeader == null || lastLeader.getWorld() != ll.getWorld() ? 0 : horizontal(ll, lastLeader) / dt;
        boolean leaderJumped = lastLeader != null && ll.getY() - lastLeader.getY() > 0.25 && !leader.isFlying();
        lastLeader = ll.clone();

        // where a companion would walk: a little behind the leader, to one side
        Vector facing = ll.getDirection().setY(0);
        if (facing.lengthSquared() < 1e-4) facing = new Vector(0, 0, 1);
        facing.normalize();
        Vector side = new Vector(-facing.getZ(), 0, facing.getX()).multiply((victimId.hashCode() & 1) == 0 ? 1.3 : -1.3);
        Location want = ll.clone().add(facing.clone().multiply(-2.6)).add(side);
        double dist = horizontal(here, ll);
        Location next = here.clone();
        boolean moving = false;
        if (horizontal(here, want) > 1.3 && dist > 2.2) {
            double speed = Math.max(0.1, Math.min(0.29, leaderSpeed > 0.02 ? leaderSpeed : dist > 6 ? 0.22 : 0.14));
            if (dist > 12) speed = 0.28; // catching up
            int[] wp = waypoint(here, want);
            if (wp != null) {
                double dx = wp[0] + 0.5 - here.getX(), dz = wp[2] + 0.5 - here.getZ();
                double len = Math.sqrt(dx * dx + dz * dz);
                double step = Math.min(len, speed * dt);
                if (len > 1e-3) next.add(dx / len * step, 0, dz / len * step);
                next.setY(wp[1]);
                moving = step > 0.01;
            }
        }
        // jumps and sneaks copied a beat late, like a person reacting
        if (leaderJumped && jumpTicks == 0) jumpTicks = -(4 + ThreadLocalRandom.current().nextInt(4));
        if (jumpTicks < 0 && (jumpTicks += dt) >= 0) jumpTicks = 1;
        double hop = 0;
        if (jumpTicks > 0) {
            double t = jumpTicks / 12.0;
            hop = Math.max(0, 4 * 1.2 * t * (1 - t));
            if ((jumpTicks += dt) > 12) jumpTicks = 0;
        }
        sneakFor = leader.isSneaking() ? sneakFor + dt : Math.min(sneakFor, 0) - dt;
        boolean sneak = sneakFor > 8;
        try {
            body.setPose(sneak ? Pose.SNEAKING : Pose.STANDING, true);
        } catch (RuntimeException ignored) {
            // pose not supported for mannequins on this build
        }

        // where it looks: where it walks, the leader's gaze, the nearest face, or a slow idle glance
        Player near = group.stream().min((a, b) -> Double.compare(a.getLocation().distanceSquared(here), b.getLocation().distanceSquared(here))).orElse(leader);
        float targetYaw, targetPitch;
        if (moving) {
            targetYaw = yawTo(here, next.getX() - here.getX(), next.getZ() - here.getZ());
            targetPitch = 5;
        } else if (stareFor > 0) {
            stareFor -= dt; // the one tell: it looks at someone a little too long
            Location e = near.getEyeLocation();
            targetYaw = yawTo(here, e.getX() - here.getX(), e.getZ() - here.getZ());
            targetPitch = 0;
        } else if (idleLookFor > 0) {
            idleLookFor -= dt;
            targetYaw = lookYaw;
            targetPitch = lookPitch;
        } else {
            double r = ThreadLocalRandom.current().nextDouble();
            if (r < 0.02) {
                idleLookFor = 20 + ThreadLocalRandom.current().nextInt(40);
                lookYaw += (float) ThreadLocalRandom.current().nextDouble(-70, 70);
                lookPitch = (float) ThreadLocalRandom.current().nextDouble(-15, 20);
            } else if (r < 0.025) {
                stareFor = 60 + ThreadLocalRandom.current().nextInt(60);
            }
            targetYaw = r < 0.5 ? ll.getYaw() : yawTo(here, near.getX() - here.getX(), near.getZ() - here.getZ());
            targetPitch = r < 0.5 ? ll.getPitch() * 0.6f : 0;
        }
        lookYaw = approach(lookYaw, targetYaw, 18f);
        lookPitch += (targetPitch - lookPitch) * 0.3f;
        next.setYaw(lookYaw);
        next.setPitch(lookPitch);
        Location placed = next.clone().add(0, hop, 0);
        body.teleport(placed);
        voicePos.set(new double[]{placed.getX(), placed.getY() + 1.6, placed.getZ()});
        if (moving && (stepIn -= dt) <= 0) {
            stepIn = sneak ? 12 : leaderSpeed > 0.2 ? 6 : 8;
            Block below = placed.clone().add(0, -0.2, 0).getBlock();
            try {
                w.playSound(placed, below.getBlockData().getSoundGroup().getStepSound(), SoundCategory.PLAYERS, sneak ? 0.1f : 0.15f, 1f);
            } catch (RuntimeException ignored) {
                // no sound group in a test environment
            }
        }

        // its voice: things the victim said, in chat and out loud
        if ((nextChat -= dt) <= 0) {
            nextChat = 20 * (45 + ThreadLocalRandom.current().nextInt(75));
            String line = registry.recentLine(victimId);
            if (line != null) {
                Component msg = Component.text("<" + victimName + "> " + line);
                for (Player p : w.getPlayers()) p.sendMessage(msg);
            }
        }
        if ((nextVoice -= dt) <= 0) {
            nextVoice = 20 * (40 + ThreadLocalRandom.current().nextInt(80));
            registry.voice().play(victimId, w, voicePos, plugin.cfg().skinwalkerVoiceDistance);
        }
        // too many punches and people get suspicious; it stops pretending
        long now = ticks;
        while (!punches.isEmpty() && now - punches.peekFirst() > 20 * 20) punches.removeFirst();
        if (punches.size() >= plugin.cfg().skinwalkerPunchesToExpose) reveal = true;
        return placed;
    }

    /** A next foot position toward {@code want}, from the creature navigator (one node ahead). */
    private int[] waypoint(Location from, Location want) {
        List<int[]> path = Nav.path(from.getWorld(), from.getBlockX(), (int) Math.floor(from.getY() + 0.01), from.getBlockZ(),
                want.getBlockX(), want.getBlockY(), want.getBlockZ(), 2, Nav.Medium.GROUND, 400);
        if (path.isEmpty()) return null;
        return path.get(Math.min(1, path.size() - 1));
    }

    /** Punched by a player: take it like a friend would, and punch back a moment later. */
    void punched(Player by) {
        punches.addLast((long) ticks);
        int delay = 6 + ThreadLocalRandom.current().nextInt(8);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!active || !body.isValid() || !by.isOnline() || by.getWorld() != body.getWorld()) return;
            if (by.getLocation().distanceSquared(body.getLocation()) > 16) return;
            Location here = body.getLocation();
            here.setYaw(yawTo(here, by.getX() - here.getX(), by.getZ() - here.getZ()));
            body.teleport(here);
            body.swingMainHand();
            Vector kb = by.getLocation().toVector().subtract(here.toVector()).setY(0);
            if (kb.lengthSquared() > 1e-3) by.setVelocity(by.getVelocity().add(kb.normalize().multiply(0.32).setY(0.18)));
            by.getWorld().playSound(by.getLocation(), Sound.ENTITY_PLAYER_ATTACK_WEAK, SoundCategory.PLAYERS, 0.7f, 1f);
        }, delay);
    }

    /** Someone nearby swung their arm: sometimes it does too, a beat later. */
    void mirrorSwing() {
        if (!active || carried || ThreadLocalRandom.current().nextDouble() > 0.4) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (active && body.isValid()) body.swingMainHand();
        }, 6 + ThreadLocalRandom.current().nextInt(9));
    }

    // ------------------------------------------------------------------ true form

    /** The body is now carried under the true form: limp, horizontal, nameless. */
    void trueForm() {
        carried = true;
        body.setCustomNameVisible(false);
        try {
            body.setPose(Pose.SWIMMING, true);
        } catch (RuntimeException ignored) {
            // fall back to whatever pose it has
        }
        Player v = victim();
        if (v != null) {
            v.showTitle(Title.title(Component.empty(), Component.text("It is done pretending.").color(net.kyori.adventure.text.format.NamedTextColor.DARK_RED),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofSeconds(1))));
        }
    }

    void carry(Location creature, float yaw) {
        if (!active || !body.isValid()) return;
        // under the thorax, between the ribs, head toward the rear: the body is about 1.8 blocks long lying down
        double r = Math.toRadians(yaw);
        Location l = creature.clone().add(Math.sin(r) * 0.55, 0.3, -Math.cos(r) * 0.55);
        l.setYaw(yaw + 180);
        l.setPitch(0);
        body.teleport(l);
        if (ticks++ % 10 == 0) {
            Player v = victim();
            if (v != null && v.getSpectatorTarget() != body) v.setSpectatorTarget(body);
        }
    }

    // ------------------------------------------------------------------ release

    /**
     * Let the victim go: restore their game mode, stop spectating, put them on safe ground where their body was.
     * With {@code dies}, they come back only to die (the skinwalker kept them too long, or it fed).
     */
    void release(boolean dies) {
        if (!active) return;
        active = false;
        Location at = body.isValid() ? body.getLocation() : null;
        if (body.isValid()) body.remove();
        registry.voice().mute(victimId, false);
        registry.forget(this);
        Player v = victim();
        if (v == null) return; // restored from the PDC flag when they rejoin
        registry.restore(v, originalMode, at);
        if (dies) {
            v.sendMessage(Component.text("It has no more use for you.").color(net.kyori.adventure.text.format.NamedTextColor.DARK_RED));
            v.setHealth(0);
        } else {
            v.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 160, 0, false, false));
            v.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false));
            v.sendMessage(Component.text("You are yourself again. Mostly.").color(net.kyori.adventure.text.format.NamedTextColor.GRAY));
        }
    }

    private static double horizontal(Location a, Location b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static float yawTo(Location from, double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private static float approach(float from, float to, float maxStep) {
        float d = ((to - from) % 360 + 540) % 360 - 180;
        return from + Math.max(-maxStep, Math.min(maxStep, d));
    }
}
