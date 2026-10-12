package dev.powderpass.ski;

import dev.powderpass.config.PluginConfig;
import dev.powderpass.physics.SkiState;
import dev.powderpass.physics.SurfaceKind;
import dev.powderpass.util.MathUtil;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.joml.Vector3d;

import java.util.concurrent.ThreadLocalRandom;

/** Particles and sounds for skiing. */
public final class SkiEffects {

    private static final BlockData SNOW = Material.SNOW_BLOCK.createBlockData();
    private static final BlockData POWDER = Material.POWDER_SNOW.createBlockData();
    private static final BlockData ICE = Material.PACKED_ICE.createBlockData();

    private final PluginConfig config;

    public SkiEffects(PluginConfig config) {
        this.config = config;
    }

    /** Continuous effects while riding on the ground. */
    public void ride(Player rider, World world, SkiState s, long tick) {
        double speed = s.speed();
        if (config.particles && s.grounded && speed > 0.05) {
            Vector3d side = MathUtil.forward(s.heading + (s.turn >= 0 ? 90 : -90), new Vector3d());
            double intensity = Math.min(1.0, s.skid * 6.0 + Math.abs(s.turn) * speed * 0.06 + (s.braking ? 0.4 : 0));
            Location at = new Location(world, s.pos.x - side.x * 0.3, s.pos.y + 0.05, s.pos.z - side.z * 0.3);
            switch (s.surface) {
                case POWDER -> {
                    int n = 2 + (int) (speed * 6 + intensity * 10);
                    world.spawnParticle(Particle.BLOCK, at, n, 0.35, 0.2, 0.35, 0.15, POWDER);
                    world.spawnParticle(Particle.SNOWFLAKE, at.clone().add(0, 0.3, 0), 1 + (int) (speed * 3), 0.4, 0.3, 0.4, 0.02);
                }
                case ICE -> {
                    if (intensity > 0.1) {
                        world.spawnParticle(Particle.BLOCK, at, (int) (intensity * 8), 0.2, 0.05, 0.2, 0.1, ICE);
                    }
                    if (tick % 3 == 0) {
                        world.spawnParticle(Particle.WHITE_ASH, at, 2, 0.3, 0.05, 0.3, 0.0);
                    }
                }
                case ROUGH -> {
                    if (tick % 2 == 0) {
                        world.spawnParticle(Particle.CRIT, at, 2, 0.2, 0.05, 0.2, 0.05);
                        world.spawnParticle(Particle.SMOKE, at, 1, 0.1, 0.02, 0.1, 0.0);
                    }
                }
                default -> {
                    if (intensity > 0.05) {
                        world.spawnParticle(Particle.BLOCK, at, 1 + (int) (intensity * 14), 0.25, 0.1, 0.25, 0.12, SNOW);
                    }
                    if (speed > 0.4 && tick % 2 == 0) {
                        world.spawnParticle(Particle.SNOWFLAKE, at, 1, 0.15, 0.02, 0.15, 0.0);
                    }
                }
            }
        }
        if (config.particles && speed > 1.0 && tick % 2 == 0) {
            speedLines(rider, s);
        }
        if (config.sounds && s.grounded && speed > 0.08 && tick % 4 == 0) {
            float volume = (float) Math.min(0.9, 0.15 + s.skid * 4 + speed * 0.2);
            float pitch = (float) Math.min(1.6, 0.7 + speed * 0.35);
            Sound sound = switch (s.surface) {
                case POWDER -> Sound.BLOCK_POWDER_SNOW_STEP;
                case ICE -> Sound.BLOCK_GLASS_STEP;
                case ROUGH -> Sound.BLOCK_GRAVEL_STEP;
                default -> Sound.BLOCK_SNOW_STEP;
            };
            world.playSound(new Location(world, s.pos.x, s.pos.y, s.pos.z), sound, SoundCategory.PLAYERS, volume, pitch);
        }
    }

    /** Rider-only streaks rushing past at high speed. */
    private void speedLines(Player rider, SkiState s) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vector3d dir = new Vector3d(s.vel).normalize();
        for (int i = 0; i < 2; i++) {
            double ox = r.nextDouble(-2.5, 2.5);
            double oy = r.nextDouble(0.2, 2.8);
            double oz = r.nextDouble(-2.5, 2.5);
            Location at = new Location(rider.getWorld(), s.pos.x + dir.x * 7 + ox, s.pos.y + oy + dir.y * 7, s.pos.z + dir.z * 7 + oz);
            rider.spawnParticle(Particle.CLOUD, at, 0, -dir.x, -dir.y, -dir.z, s.speed() * 0.9);
        }
    }

    public void clickIn(Player p) {
        if (config.sounds) {
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, SoundCategory.PLAYERS, 0.7f, 1.7f);
            p.getWorld().playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, SoundCategory.PLAYERS, 0.8f, 1.0f);
        }
        if (config.particles) {
            p.getWorld().spawnParticle(Particle.BLOCK, p.getLocation().add(0, 0.1, 0), 20, 0.4, 0.05, 0.4, 0.1, SNOW);
        }
    }

    public void unclip(Location at) {
        if (config.sounds) {
            at.getWorld().playSound(at, Sound.BLOCK_IRON_TRAPDOOR_OPEN, SoundCategory.PLAYERS, 0.7f, 1.5f);
        }
    }

    public void jump(Location at) {
        if (config.sounds) {
            at.getWorld().playSound(at, Sound.ENTITY_GOAT_LONG_JUMP, SoundCategory.PLAYERS, 0.6f, 1.3f);
        }
        if (config.particles) {
            at.getWorld().spawnParticle(Particle.BLOCK, at, 12, 0.3, 0.05, 0.3, 0.1, SNOW);
        }
    }

    public void land(Location at, double impact, SurfaceKind kind) {
        if (config.sounds) {
            at.getWorld().playSound(at, Sound.BLOCK_SNOW_FALL, SoundCategory.PLAYERS, (float) Math.min(1.0, 0.4 + impact), 0.9f);
            if (impact > 0.4) {
                at.getWorld().playSound(at, Sound.BLOCK_POWDER_SNOW_FALL, SoundCategory.PLAYERS, 0.8f, 0.8f);
            }
        }
        if (config.particles) {
            BlockData data = kind == SurfaceKind.ICE ? ICE : SNOW;
            at.getWorld().spawnParticle(Particle.BLOCK, at, 10 + (int) (impact * 40), 0.5, 0.1, 0.5, 0.2, data);
            if (impact > 0.4) {
                at.getWorld().spawnParticle(Particle.SNOWFLAKE, at.clone().add(0, 0.3, 0), 15, 0.6, 0.3, 0.6, 0.05);
            }
        }
    }

    public void trick(Player p, int combo, boolean big) {
        if (!config.sounds) {
            return;
        }
        float pitch = (float) Math.min(2.0, 0.9 + combo * 0.12);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8f, pitch);
        if (big) {
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.5f, 1.4f);
        }
    }

    public void bump(Location at) {
        if (config.sounds) {
            at.getWorld().playSound(at, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.PLAYERS, 0.6f, 0.8f);
        }
    }

    public void crash(Location at) {
        World w = at.getWorld();
        if (config.sounds) {
            w.playSound(at, Sound.ENTITY_PLAYER_HURT_FREEZE, SoundCategory.PLAYERS, 0.9f, 1.0f);
            w.playSound(at, Sound.BLOCK_SNOW_BREAK, SoundCategory.PLAYERS, 1.0f, 0.7f);
            w.playSound(at, Sound.ENTITY_ARMOR_STAND_BREAK, SoundCategory.PLAYERS, 0.8f, 1.2f);
        }
        if (config.particles) {
            w.spawnParticle(Particle.BLOCK, at.clone().add(0, 0.4, 0), 60, 0.7, 0.4, 0.7, 0.3, SNOW);
            w.spawnParticle(Particle.SNOWFLAKE, at.clone().add(0, 0.6, 0), 25, 0.8, 0.5, 0.8, 0.08);
            w.spawnParticle(Particle.POOF, at.clone().add(0, 0.5, 0), 6, 0.4, 0.3, 0.4, 0.02);
        }
    }

    public void gate(Player p, Location at, boolean red, int index) {
        if (config.sounds) {
            float pitch = (float) Math.min(2.0, 0.8 + index * 0.08);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.PLAYERS, 0.7f, pitch);
        }
        if (config.particles) {
            Color color = red ? Color.fromRGB(0xEF4444) : Color.fromRGB(0x3B82F6);
            at.getWorld().spawnParticle(Particle.DUST, at, 20, 0.6, 0.6, 0.6, 0, new Particle.DustOptions(color, 1.4f));
        }
    }

    public void missed(Player p) {
        if (config.sounds) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 1.0f, 0.6f);
        }
    }

    public void finish(Player p, Location at, boolean record) {
        if (config.sounds) {
            p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, SoundCategory.PLAYERS, 1.0f, 1.0f);
            p.playSound(p.getLocation(), record ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP,
                    SoundCategory.PLAYERS, 0.8f, 1.2f);
        }
        if (config.particles) {
            at.getWorld().spawnParticle(Particle.FIREWORK, at.clone().add(0, 1.5, 0), record ? 120 : 50, 1.2, 1.0, 1.2, 0.12);
            if (record) {
                at.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, at.clone().add(0, 1.5, 0), 80, 1.0, 1.0, 1.0, 0.4);
            }
        }
    }
}
