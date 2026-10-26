package dev.powderpass.volleyball.ui;

import com.destroystokyo.paper.ParticleBuilder;
import dev.powderpass.volleyball.config.Settings;
import dev.powderpass.volleyball.config.SoundPair;
import dev.powderpass.volleyball.config.SoundSpec;
import dev.powderpass.volleyball.pack.PackManager;
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
    private final PackManager packs;

    public Fx(org.bukkit.plugin.Plugin plugin, Settings settings, PackManager packs) {
        this.plugin = plugin;
        this.settings = settings;
        this.packs = packs;
    }

    /**
     * Referee whistle. The pack sound is a recorded-style blast; vanilla players get a trill of flute notes
     * (two short blasts, or one long one).
     */
    public void whistle(Location at, boolean longBlast) {
        Settings.SoundId id = longBlast ? Settings.SoundId.WHISTLE_LONG : Settings.SoundId.WHISTLE;
        SoundPair pair = settings.sound(id);
        World world = at.getWorld();
        if (pair == null || world == null) {
            return;
        }
        int[] vanillaTicks = longBlast ? new int[]{0, 2, 4, 6, 8, 10} : new int[]{0, 1, 5, 6};
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(at) > 64 * 64) {
                continue;
            }
            boolean pack = packs.has(p) && pair.pack() != null;
            SoundSpec spec = pair.pick(pack);
            if (spec == null) {
                continue;
            }
            if (pack) {
                p.playSound(spec.toSound(0, 1f), at.getX(), at.getY(), at.getZ());
                continue;
            }
            for (int t : vanillaTicks) {
                Sound snd = spec.toSound(t % 2 == 0 ? 0f : -0.05f, 1f);
                if (t == 0) {
                    p.playSound(snd, at.getX(), at.getY(), at.getZ());
                } else {
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (p.isOnline()) {
                            p.playSound(snd, at.getX(), at.getY(), at.getZ());
                        }
                    }, t);
                }
            }
        }
    }

    public void sound(Location at, Settings.SoundId id) {
        sound(at, id, 1f);
    }

    /** Positional sound for everyone in earshot. */
    public void sound(Location at, Settings.SoundId id, float volumeScale) {
        SoundPair pair = settings.sound(id);
        World world = at.getWorld();
        if (pair == null || world == null) {
            return;
        }
        float jitter = (ThreadLocalRandom.current().nextFloat() - 0.5f) * 0.12f;
        // a sound of volume v carries 16 * v blocks
        float maxVolume = Math.max(pair.vanilla() == null ? 0 : pair.vanilla().volume(),
                pair.pack() == null ? 0 : pair.pack().volume()) * volumeScale;
        double range = Math.max(16, 16 * maxVolume);
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(at) > range * range) {
                continue;
            }
            SoundSpec spec = pair.pick(packs.has(p));
            if (spec != null) {
                p.playSound(spec.toSound(jitter, volumeScale), at.getX(), at.getY(), at.getZ());
            }
        }
    }

    /** Non-positional sound for one player. */
    public void sound(Player p, Settings.SoundId id) {
        SoundPair pair = settings.sound(id);
        if (pair == null) {
            return;
        }
        SoundSpec spec = pair.pick(packs.has(p));
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
