package dev.powderpass.volleyball.ui;

import com.destroystokyo.paper.ParticleBuilder;
import dev.powderpass.volleyball.config.Settings;
import dev.powderpass.volleyball.config.SoundSpec;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Sounds and particles. Every sound is sent per player: players with the resource pack hear the custom
 * sound event, everyone else the mapped vanilla sound.
 */
public final class Fx {

    private final org.bukkit.plugin.Plugin plugin;
    private final Settings settings;

    public Fx(org.bukkit.plugin.Plugin plugin, Settings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    /** Referee whistle: the pack sound is a full blast, the vanilla fallback a short flute trill. */
    public void whistle(Location at, boolean longBlast) {
        Settings.SoundId id = longBlast ? Settings.SoundId.WHISTLE_LONG : Settings.SoundId.WHISTLE;
        if (settings.customSounds) {
            sound(at, id);
            return;
        }
        int[] ticks = longBlast ? new int[]{0, 2, 4, 6, 8, 10} : new int[]{0, 1, 5, 6};
        for (int t : ticks) {
            if (t == 0) {
                sound(at, id);
            } else {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> sound(at, id), t);
            }
        }
    }

    public void sound(Location at, Settings.SoundId id) {
        sound(at, id, 1f);
    }

    /** Positional sound for everyone in earshot. */
    public void sound(Location at, Settings.SoundId id, float volumeScale) {
        SoundSpec spec = settings.sound(id);
        World world = at.getWorld();
        if (spec == null || world == null) {
            return;
        }
        float jitter = (ThreadLocalRandom.current().nextFloat() - 0.5f) * 0.12f;
        world.playSound(spec.toSound(jitter, volumeScale), at.getX(), at.getY(), at.getZ());
    }

    /** Non-positional sound for one player. */
    public void sound(Player p, Settings.SoundId id) {
        SoundSpec spec = settings.sound(id);
        if (spec != null) {
            p.playSound(spec.toSound(0, 1f), Sound.Emitter.self());
        }
    }

    public void particle(Location at, Particle particle, int count, double spread, double speed) {
        new ParticleBuilder(particle).location(at).count(count).offset(spread, spread, spread).extra(speed)
                .receivers((int) settings.particleRange, true).spawn();
    }

    public void dust(Location at, Color color, float size, int count, double spread) {
        new ParticleBuilder(Particle.DUST).location(at).count(count).offset(spread, spread, spread)
                .data(new Particle.DustOptions(color, size)).receivers((int) settings.particleRange, true).spawn();
    }

    public void dustFor(Player viewer, Location at, Color color, float size) {
        new ParticleBuilder(Particle.DUST).location(at).count(1).offset(0, 0, 0)
                .data(new Particle.DustOptions(color, size)).receivers(viewer).spawn();
    }

    public void blockDust(Location at, BlockData data, int count) {
        new ParticleBuilder(Particle.BLOCK).location(at).count(count).offset(0.15, 0.02, 0.15).extra(0.1)
                .data(data).receivers((int) settings.particleRange, true).spawn();
    }

    /** Ring marker on the ground, visible to everyone. */
    public void ring(World world, double x, double y, double z, double radius, Color color, int points) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            dust(new Location(world, x + Math.cos(a) * radius, y + 0.05, z + Math.sin(a) * radius), color, 0.9f, 1, 0);
        }
    }

    public void ringFor(Player viewer, double x, double y, double z, double radius, Color color, int points) {
        World world = viewer.getWorld();
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            dustFor(viewer, new Location(world, x + Math.cos(a) * radius, y + 0.06, z + Math.sin(a) * radius), color, 0.7f);
        }
    }
}
