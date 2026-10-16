package com.powderpass.backrooms.cutscene;

import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.gen.Feature;
import com.powderpass.backrooms.gen.LevelGenerator;
import com.powderpass.backrooms.gen.levels.PoolroomsGenerator;
import com.powderpass.backrooms.util.Rng;
import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * The level entrances as in-world sequences, each fitting where it goes. Before the world change the camera moves
 * through the real place the player is in; the change itself happens behind black; afterwards keyframes are
 * offsets from the arrival spot.
 * <ul>
 *   <li><b>Level 0 to the Poolrooms</b> (the cold seam): the floor gives way, a long fall through a dark shaft with
 *   the wind rising and water below getting louder, a plunge deep into a pool, and a slow rise to the surface of a
 *   bright tiled room.</li>
 *   <li><b>Poolrooms to Level 1</b> (the service door): through the door, it slams, footsteps echo in the dark, and
 *   you come to on cold concrete under flickering tubes, looking up, then sit up.</li>
 *   <li><b>to the Office</b> (the elevator): the doors close, the motor winds up, the car shakes as it climbs, a ding,
 *   the doors open on a different floor.</li>
 *   <li><b>Office to the Plains</b> (the too-bright window): toward the light, a gust, falling out into it, then a
 *   long slow descent out of an evening sky onto the grass.</li>
 *   <li><b>the Plains to the way out</b> (the doorframe): the light goes, a heartbeat, and you wake.</li>
 *   <li><b>the ways back</b> (drain, stairwell, hatch): sinking through the floor with a glitch, arriving below.</li>
 * </ul>
 */
public final class Transitions {
    private Transitions() {
    }

    /** The cutscene for a transition feature, or null to use the plain transition. */
    public static Cutscene forFeature(BackroomsPlugin plugin, Player p, Level from, Feature f) {
        if (f.dest() == null) return null;
        if (f.dest().isExit()) return exit(plugin, p);
        Level to = f.dest().level();
        Location dest = arrival(plugin, p, to, f);
        if (dest == null) return null;
        String id = f.id();
        return switch (to) {
            case POOLROOMS -> from == Level.LEVEL_0 ? intoThePool(plugin, p, dest, to) : sinkThrough(plugin, p, dest, to, "pool_return");
            case LEVEL_1 -> from == Level.POOLROOMS ? serviceDoor(plugin, p, dest, to) : sinkThrough(plugin, p, dest, to, "garage_return");
            case LEVEL_4 -> elevator(plugin, p, dest, to);
            case PLAINS -> throughTheWindow(plugin, p, dest, to);
            case LEVEL_0 -> id.equals("drain") ? drain(plugin, p, dest, to) : sinkThrough(plugin, p, dest, to, "lobby_return");
        };
    }

    /** Where the player ends up: deterministic per feature, near the corresponding coordinates. */
    static Location arrival(BackroomsPlugin plugin, Player p, Level to, Feature via) {
        World w = plugin.worlds().world(to);
        if (w == null) return null;
        LevelGenerator g = plugin.worlds().generator(to);
        long h = via == null ? Rng.hash(p.getUniqueId().getMostSignificantBits(), plugin.sessions().now()) : via.seed();
        int x = p.getLocation().getBlockX() + Rng.range(h, -48, 48);
        int z = p.getLocation().getBlockZ() + Rng.range(Rng.hash(h, 1), -48, 48);
        if (g instanceof PoolroomsGenerator pg && plugin.worlds().levelOf(p.getWorld()) == Level.LEVEL_0) {
            int[] pool = pg.arrivalPool(x, z);
            if (pool != null) return new Location(w, pool[0] + 0.5, pool[1] + 0.2, pool[2] + 0.5, p.getLocation().getYaw(), 0);
        }
        int[] s = g.safeSpot(x, z, 0);
        return new Location(w, s[0] + 0.5, s[1], s[2] + 0.5, p.getLocation().getYaw(), 0);
    }

    // ------------------------------------------------------------------ sound helpers

    /** A pack sound on the listener (or the vanilla stand-in for players without the pack). */
    static void hear(BackroomsPlugin plugin, Player p, String id, Sound fallback, float vol, float pitch) {
        if (id != null && plugin.audio().custom(p) && plugin.audio().index().has(id)) {
            p.playSound(net.kyori.adventure.sound.Sound.sound(Key.key("backrooms", id), net.kyori.adventure.sound.Sound.Source.AMBIENT, vol, 1f),
                    net.kyori.adventure.sound.Sound.Emitter.self());
        } else if (fallback != null) {
            p.playSound(p.getLocation(), fallback, SoundCategory.AMBIENT, vol, pitch);
        }
    }

    private static Vector forward(Player p) {
        Vector v = p.getLocation().getDirection().setY(0);
        return v.lengthSquared() < 1e-4 ? new Vector(0, 0, 1) : v.normalize();
    }

    // ------------------------------------------------------------------ Level 0 -> Poolrooms

    static Cutscene intoThePool(BackroomsPlugin plugin, Player p, Location dest, Level to) {
        Location eye = p.getEyeLocation();
        Vector fw = forward(p);
        int ceil = 0;
        if (plugin.worlds().generator(to) instanceof PoolroomsGenerator pg) {
            int[] pool = pg.arrivalPool(dest.getBlockX(), dest.getBlockZ());
            if (pool != null && pool[0] == dest.getBlockX() && pool[2] == dest.getBlockZ()) ceil = pool[3];
        }
        double drop = ceil > 0 ? Math.max(4, ceil - dest.getY() - 1.2) : 6;
        Cutscene c = new Cutscene("into_the_pool")
                // the floor gives: a glitch, sinking into the carpet
                .camera(0, eye).camera(14, eye.clone().add(fw.clone().multiply(0.3)).add(0, -0.5, 0))
                .camera(24, eye.clone().add(fw.clone().multiply(0.4)).add(0, -1.5, 0))
                .shake(0, 26, 3.5)
                .at(0, q -> {
                    hear(plugin, q, "light.arc", Sound.BLOCK_BEACON_DEACTIVATE, 0.9f, 0.5f);
                    hear(plugin, q, "skinwalker.crackle", Sound.BLOCK_GLASS_BREAK, 0.6f, 0.5f);
                    q.getWorld().spawnParticle(Particle.REVERSE_PORTAL, q.getLocation().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.02);
                })
                .fadeOut(12, 12)
                .at(22, q -> hear(plugin, q, null, Sound.ITEM_ELYTRA_FLYING, 0.5f, 0.7f))
                .travel(30, q -> dest)
                // the fall, blind: down the shaft, the wind and the water below
                .camera(30, 0, drop + 1.6, 0).camera(56, 0, drop * 0.55 + 1.6, 0).camera(70, 0, 1.8, 0)
                .lookAngles(30, p.getLocation().getYaw(), 70)
                .at(44, q -> hear(plugin, q, "water.lap", Sound.AMBIENT_UNDERWATER_LOOP_ADDITIONS, 0.7f, 1f))
                .at(58, q -> hear(plugin, q, "water.circulation", Sound.BLOCK_WATER_AMBIENT, 0.9f, 1f))
                // the plunge
                .fadeIn(70, 10)
                .at(71, q -> {
                    hear(plugin, q, "water.splash", Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1f, 0.8f);
                    q.getWorld().spawnParticle(Particle.SPLASH, q.getLocation().add(0, 1, 0), 60, 0.8, 0.3, 0.8, 0.3);
                })
                .camera(80, 0, -1.4, 0).camera(108, 0, -2.6, 0)
                .at(76, q -> hear(plugin, q, "water.underwater", Sound.AMBIENT_UNDERWATER_ENTER, 0.9f, 1f))
                .at(84, q -> q.getWorld().spawnParticle(Particle.BUBBLE_COLUMN_UP, q.getEyeLocation(), 30, 0.4, 0.4, 0.4, 0.05))
                .lookAngles(84, p.getLocation().getYaw() + 40, -55)
                // and up into the light
                .camera(146, 0, 1.0, 0).camera(158, 0, 1.55, 0)
                .lookAngles(128, p.getLocation().getYaw() + 70, -10)
                .at(150, q -> hear(plugin, q, "water.splash", Sound.ENTITY_PLAYER_SPLASH, 0.6f, 1.3f))
                .length(162)
                .finish(q -> plugin.sessions().arrived(q, to, true));
        c.shortVersion(new Cutscene("into_the_pool_short")
                .camera(0, eye).camera(10, eye.clone().add(0, -1, 0)).shake(0, 12, 3)
                .at(0, q -> hear(plugin, q, "light.arc", Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 0.5f))
                .fadeOut(4, 8).travel(14, q -> dest)
                .camera(14, 0, 3.5, 0).camera(24, 0, -1.6, 0).camera(50, 0, 1.55, 0)
                .fadeIn(22, 6).at(23, q -> hear(plugin, q, "water.splash", Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1f, 0.8f))
                .length(54).finish(q -> plugin.sessions().arrived(q, to, true)));
        return c;
    }

    // ------------------------------------------------------------------ Poolrooms -> Level 1

    static Cutscene serviceDoor(BackroomsPlugin plugin, Player p, Location dest, Level to) {
        Location eye = p.getEyeLocation();
        Vector fw = forward(p);
        float yaw = p.getLocation().getYaw();
        Cutscene c = new Cutscene("service_door")
                .camera(0, eye).camera(22, eye.clone().add(fw.clone().multiply(1.4)))
                .at(2, q -> hear(plugin, q, null, Sound.BLOCK_IRON_DOOR_OPEN, 0.9f, 0.7f))
                .fadeOut(14, 10)
                .at(24, q -> hear(plugin, q, null, Sound.BLOCK_IRON_DOOR_CLOSE, 1f, 0.5f))
                .at(30, q -> hear(plugin, q, "step.concrete", Sound.BLOCK_STONE_STEP, 0.7f, 0.8f))
                .at(40, q -> hear(plugin, q, "step.concrete", Sound.BLOCK_STONE_STEP, 0.5f, 0.8f))
                .at(52, q -> hear(plugin, q, "step.concrete", Sound.BLOCK_STONE_STEP, 0.3f, 0.8f))
                .travel(34, q -> dest)
                // come to on the concrete, looking up at the tubes, then sit up
                .camera(34, 0, 0.35, 0).camera(78, 0, 0.4, 0).camera(108, 0, 1.0, 0).camera(124, 0, 1.6, 0)
                .lookAngles(34, yaw, -75)
                .at(62, q -> hear(plugin, q, "light.flicker", Sound.BLOCK_BEACON_AMBIENT, 0.9f, 1.4f))
                .fadeIn(64, 20)
                .at(70, q -> hear(plugin, q, "hum.fluorescent", Sound.BLOCK_BEACON_AMBIENT, 0.6f, 1.8f))
                .lookAngles(96, yaw + 25, -20)
                .lookAngles(118, yaw, 0)
                .at(90, q -> hear(plugin, q, "metal.groan", Sound.BLOCK_ANVIL_LAND, 0.4f, 0.5f))
                .length(128)
                .finish(q -> plugin.sessions().arrived(q, to, true));
        c.shortVersion(generic(plugin, p, dest, to, "service_door_short", Sound.BLOCK_IRON_DOOR_CLOSE));
        return c;
    }

    // ------------------------------------------------------------------ to the Office (the lift)

    public static Cutscene elevator(BackroomsPlugin plugin, Player p, Location dest, Level to) {
        Location eye = p.getEyeLocation();
        float yaw = p.getLocation().getYaw();
        Cutscene c = new Cutscene("elevator_up")
                .camera(0, eye).camera(70, eye)
                .at(0, q -> hear(plugin, q, "lift.door", Sound.BLOCK_IRON_DOOR_CLOSE, 0.9f, 0.7f))
                .at(14, q -> hear(plugin, q, "lift.motor", Sound.BLOCK_BEACON_AMBIENT, 0.9f, 0.5f))
                .shake(16, 30, 0.8).shake(46, 20, 1.6)
                .at(34, q -> hear(plugin, q, "lift.motor", Sound.BLOCK_BEACON_AMBIENT, 0.9f, 0.6f))
                .fadeOut(52, 10)
                .travel(64, q -> dest)
                .camera(64, 0, 1.62, 0).camera(120, 0, 1.62, 0)
                .lookAngles(64, yaw, 0)
                .at(66, q -> hear(plugin, q, "lift.motor", Sound.BLOCK_BEACON_AMBIENT, 0.6f, 0.45f))
                .shake(66, 16, 0.8)
                .fadeIn(80, 10)
                .at(86, q -> hear(plugin, q, "lift.ding", Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.6f))
                .at(96, q -> hear(plugin, q, "lift.door", Sound.BLOCK_IRON_DOOR_OPEN, 0.9f, 0.8f))
                .length(110)
                .finish(q -> plugin.sessions().arrived(q, to, true));
        c.shortVersion(new Cutscene("elevator_up_short").camera(0, eye).camera(40, eye)
                .at(0, q -> hear(plugin, q, "lift.door", Sound.BLOCK_IRON_DOOR_CLOSE, 0.9f, 0.7f)).shake(4, 20, 1)
                .fadeOut(10, 8).travel(20, q -> dest).camera(20, 0, 1.62, 0).camera(40, 0, 1.62, 0).fadeIn(24, 6)
                .at(30, q -> hear(plugin, q, "lift.ding", Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.6f)).length(40)
                .finish(q -> plugin.sessions().arrived(q, to, true)));
        return c;
    }

    // ------------------------------------------------------------------ Office -> Plains

    static Cutscene throughTheWindow(BackroomsPlugin plugin, Player p, Location dest, Level to) {
        Location eye = p.getEyeLocation();
        Vector fw = forward(p);
        float yaw = p.getLocation().getYaw();
        Cutscene c = new Cutscene("through_the_window")
                .camera(0, eye).camera(26, eye.clone().add(fw.clone().multiply(1.8)))
                .lookAngles(0, yaw, -8)
                .at(4, q -> {
                    hear(plugin, q, "wind.plains", Sound.ITEM_ELYTRA_FLYING, 0.8f, 1.3f);
                    q.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 40, 0, false, false, false));
                })
                .at(18, q -> hear(plugin, q, null, Sound.BLOCK_GLASS_BREAK, 0.5f, 0.6f))
                .lookAngles(18, yaw, 50)
                .fadeOut(22, 10)
                .travel(34, q -> dest)
                // a long slow descent out of the evening sky onto the grass
                .camera(34, -fw.getX() * 4, 34, -fw.getZ() * 4).camera(90, -fw.getX() * 3, 16, -fw.getZ() * 3)
                .camera(140, -fw.getX() * 1, 4.5, -fw.getZ() * 1).camera(168, 0, 1.62, 0)
                .lookAngles(34, yaw, 25)
                .fadeIn(38, 20)
                .at(40, q -> hear(plugin, q, "wind.plains", Sound.ITEM_ELYTRA_FLYING, 0.5f, 0.7f))
                .lookAngles(110, yaw, 8)
                .at(150, q -> hear(plugin, q, "grass.rustle", Sound.BLOCK_GRASS_STEP, 0.7f, 1f))
                .length(172)
                .finish(q -> plugin.sessions().arrived(q, to, true));
        c.shortVersion(generic(plugin, p, dest, to, "through_the_window_short", Sound.ITEM_ELYTRA_FLYING));
        return c;
    }

    // ------------------------------------------------------------------ the ways back

    static Cutscene drain(BackroomsPlugin plugin, Player p, Location dest, Level to) {
        Location eye = p.getEyeLocation();
        Cutscene c = new Cutscene("drain")
                .camera(0, eye).camera(26, eye.clone().add(0, -2.2, 0))
                .lookAngles(0, p.getLocation().getYaw(), 80)
                .at(0, q -> hear(plugin, q, "water.bubbles", Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1f, 0.6f))
                .at(6, q -> q.getWorld().spawnParticle(Particle.BUBBLE_COLUMN_UP, q.getEyeLocation(), 40, 0.5, 0.5, 0.5, 0.1))
                .shake(4, 24, 2)
                .fadeOut(16, 10)
                .travel(30, q -> dest)
                .camera(30, 0, 0.6, 0).camera(70, 0, 1.62, 0)
                .lookAngles(30, p.getLocation().getYaw(), -10)
                .at(36, q -> hear(plugin, q, "hum.fluorescent", Sound.BLOCK_BEACON_AMBIENT, 0.8f, 1.6f))
                .fadeIn(40, 16)
                .length(76)
                .finish(q -> plugin.sessions().arrived(q, to, true));
        c.shortVersion(generic(plugin, p, dest, to, "drain_short", Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE));
        return c;
    }

    static Cutscene sinkThrough(BackroomsPlugin plugin, Player p, Location dest, Level to, String id) {
        Cutscene c = generic(plugin, p, dest, to, id, Sound.BLOCK_BEACON_DEACTIVATE);
        c.shortVersion(generic(plugin, p, dest, to, id + "_short", Sound.BLOCK_BEACON_DEACTIVATE));
        return c;
    }

    /** Sinking through the floor with a glitch, arriving below and getting up. */
    static Cutscene generic(BackroomsPlugin plugin, Player p, Location dest, Level to, String id, Sound cue) {
        Location eye = p.getEyeLocation();
        return new Cutscene(id)
                .camera(0, eye).camera(18, eye.clone().add(0, -1.2, 0))
                .shake(0, 20, 2.5)
                .at(0, q -> {
                    hear(plugin, q, "light.arc", cue, 0.8f, 0.6f);
                    q.getWorld().spawnParticle(Particle.REVERSE_PORTAL, q.getLocation().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.02);
                })
                .fadeOut(8, 10)
                .travel(22, q -> dest)
                .camera(22, 0, 0.7, 0).camera(56, 0, 1.62, 0)
                .lookAngles(22, p.getLocation().getYaw(), -20)
                .lookAngles(44, p.getLocation().getYaw(), 0)
                .fadeIn(28, 14)
                .length(60)
                .finish(q -> plugin.sessions().arrived(q, to, true));
    }

    // ------------------------------------------------------------------ the way out

    static Cutscene exit(BackroomsPlugin plugin, Player p) {
        Location eye = p.getEyeLocation();
        Vector fw = forward(p);
        return new Cutscene("exit")
                .camera(0, eye).camera(40, eye.clone().add(fw.clone().multiply(1.5)))
                .at(0, q -> hear(plugin, q, null, Sound.BLOCK_PORTAL_TRIGGER, 0.5f, 1.6f))
                .fadeOut(20, 16)
                .at(36, q -> hear(plugin, q, null, Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 0.9f))
                .at(52, q -> hear(plugin, q, null, Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 0.9f))
                .at(66, q -> hear(plugin, q, null, Sound.ENTITY_WARDEN_HEARTBEAT, 0.8f, 0.9f))
                .length(76)
                .endWhereTheCameraIs()
                .skippable(false)
                .finish(q -> {
                    plugin.sessions().leave(q, null);
                    q.playSound(q.getLocation(), Sound.ENTITY_PLAYER_BREATH, SoundCategory.PLAYERS, 1f, 0.8f);
                });
    }
}
