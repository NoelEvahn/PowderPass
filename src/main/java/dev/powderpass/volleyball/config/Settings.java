package dev.powderpass.volleyball.config;

import dev.powderpass.volleyball.game.MatchSettings;
import dev.powderpass.volleyball.physics.PhysicsParams;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;

/** Typed, immutable view of config.yml. Rebuilt on /vb reload. */
public final class Settings {

    public record ResourcePack(boolean enabled, NamespacedKey ballModel, NamespacedKey whistleModel, NamespacedKey handsModel) {
    }

    public record Ball(String texture, double visualSize, int teleportInterpolation, int rotationInterpolation,
                       boolean shadow, boolean trail, int idleDespawnSeconds, int maxPracticeBalls) {
    }

    public record Controls(int inputBufferTicks, boolean lagCompensation, int maxCompensationMs, int hitCooldownTicks,
                           double approachJumpBoost, double diveLunge, boolean aimReticle) {
    }

    public record Bump(double reach, double apex, double maxError, AimProfile aim) {
    }

    public record SetHit(double reach, double apex, double quickApex, double maxError, AimProfile aim) {
    }

    public record Spike(double reach, double minSpeed, double maxSpeed, double minTopspin, double maxTopspin,
                        double maxError, AimProfile aim) {
    }

    public record Tip(double reach, double apex, double maxError, AimProfile aim) {
    }

    public record StandingAttack(double reach, double minSpeed, double maxSpeed, double topspin, double maxError,
                                 AimProfile aim) {
    }

    public record Block(int durationTicks, double width, double reachOver) {
    }

    public record Serve(double reach, double floatMinSpeed, double floatMaxSpeed, double topspinMinSpeed,
                        double topspinMaxSpeed, double jumpMinSpeed, double jumpMaxSpeed, double jumpTopspin,
                        double standingTopspin, double underhandApex, double maxError, double tossSpeed,
                        double jumpTossSpeed, double jumpTossForward, AimProfile aim) {
    }

    public record CourtDefaults(double freeZone, double netBandHeight, double antennaAboveNet, double postOffset,
                                Material courtFloor, Material freeZoneFloor, Material beachFloor,
                                Material beachFreeZoneFloor, int netColumns, int netRows, int netInterpolation,
                                Material lineMaterial, float viewRange) {
    }

    public record TeamStyle(String name, TextColor color) {
    }

    public record Hud(boolean sidebar, boolean bossbar, boolean hitFeedback, int sidebarUpdateTicks) {
    }

    public enum SoundId {
        BUMP, SET, SPIKE, SPIKE_PERFECT, TIP, SERVE, BLOCK, FLOOR, SAND, NET, TAPE, ANTENNA, WALL, WHISTLE, POINT, WIN, TOSS;

        String path() {
            return name().toLowerCase().replace('_', '-');
        }
    }

    public final ResourcePack resourcePack;
    public final Ball ball;
    public final PhysicsParams physics;
    public final Controls controls;
    public final Bump bump;
    public final Bump dig;
    public final SetHit set;
    public final Spike spike;
    public final Tip tip;
    public final StandingAttack standingAttack;
    public final Block block;
    public final Serve serve;
    public final CourtDefaults courtDefaults;
    public final MatchSettings matchDefaults;
    public final TeamStyle teamA;
    public final TeamStyle teamB;
    public final Map<SoundId, SoundSpec> sounds = new EnumMap<>(SoundId.class);
    public final Hud hud;
    public final double trailMinSpeed;
    public final double particleRange;

    public Settings(FileConfiguration c, Logger log) {
        resourcePack = new ResourcePack(
                c.getBoolean("resource-pack.enabled", false),
                key(c.getString("resource-pack.ball-model"), "powderpass:volleyball"),
                key(c.getString("resource-pack.whistle-model"), "powderpass:whistle"),
                key(c.getString("resource-pack.hands-model"), "powderpass:hands"));

        ball = new Ball(
                c.getString("ball.texture", ""),
                clamp(c.getDouble("ball.visual-size", 0.3), 0.1, 2),
                (int) clamp(c.getInt("ball.teleport-interpolation", 2), 0, 59),
                (int) clamp(c.getInt("ball.rotation-interpolation", 2), 0, 59),
                c.getBoolean("ball.shadow", true),
                c.getBoolean("ball.trail", true),
                Math.max(5, c.getInt("ball.idle-despawn-seconds", 45)),
                Math.max(1, c.getInt("ball.max-practice-balls", 2)));

        PhysicsParams d = PhysicsParams.realistic();
        String p = "physics.";
        physics = new PhysicsParams(
                c.getDouble(p + "gravity", d.gravity()),
                c.getDouble(p + "air-density", d.airDensity()),
                clamp(c.getDouble(p + "radius", d.radius()), 0.05, 0.5),
                clamp(c.getDouble(p + "mass", d.mass()), 0.05, 5),
                c.getDouble(p + "drag-coefficient-slow", d.dragCoefficientSlow()),
                c.getDouble(p + "drag-coefficient-fast", d.dragCoefficientFast()),
                c.getDouble(p + "drag-crisis-speed", d.dragCrisisSpeed()),
                c.getDouble(p + "drag-crisis-width", d.dragCrisisWidth()),
                c.getDouble(p + "lift-slope", d.liftSlope()),
                c.getDouble(p + "max-lift-coefficient", d.maxLiftCoefficient()),
                c.getDouble(p + "spin-decay-seconds", d.spinDecaySeconds()),
                c.getDouble(p + "knuckle-strength", d.knuckleStrength()),
                c.getDouble(p + "knuckle-spin-threshold", d.knuckleSpinThreshold()),
                clamp(c.getDouble(p + "floor-restitution", d.floorRestitution()), 0, 1),
                clamp(c.getDouble(p + "floor-friction", d.floorFriction()), 0, 2),
                clamp(c.getDouble(p + "wall-restitution", d.wallRestitution()), 0, 1),
                clamp(c.getDouble(p + "wall-friction", d.wallFriction()), 0, 2),
                clamp(c.getDouble(p + "net-restitution", d.netRestitution()), 0, 1),
                clamp(c.getDouble(p + "net-tangential-damping", d.netTangentialDamping()), 0, 1),
                clamp(c.getDouble(p + "tape-restitution", d.tapeRestitution()), 0, 1),
                clamp(c.getDouble(p + "antenna-restitution", d.antennaRestitution()), 0, 1),
                c.getDouble(p + "rolling-resistance", d.rollingResistance()),
                c.getDouble(p + "rest-speed", d.restSpeed()),
                (int) clamp(c.getInt(p + "max-substeps", d.maxSubsteps()), 4, 128));

        controls = new Controls(
                (int) clamp(c.getInt("controls.input-buffer-ticks", 4), 0, 20),
                c.getBoolean("controls.lag-compensation", true),
                (int) clamp(c.getInt("controls.max-compensation-ms", 250), 0, 1000),
                (int) clamp(c.getInt("controls.hit-cooldown-ticks", 6), 1, 40),
                clamp(c.getDouble("controls.approach-jump-boost", 0.1), 0, 0.5),
                clamp(c.getDouble("controls.dive-lunge", 0.55), 0, 2),
                c.getBoolean("controls.aim-reticle", true));

        String h = "hits.";
        bump = new Bump(c.getDouble(h + "bump.reach", 1.3), c.getDouble(h + "bump.apex", 2.4),
                c.getDouble(h + "bump.max-error", 2.2),
                AimProfile.load(c.getConfigurationSection(h + "bump.aim"), new AimProfile(1.5, 9, 60, -25, 4.5)));
        dig = new Bump(c.getDouble(h + "dig.reach", 1.7), c.getDouble(h + "dig.apex", 2.8),
                c.getDouble(h + "dig.max-error", 3.0),
                AimProfile.load(c.getConfigurationSection(h + "dig.aim"), new AimProfile(1.5, 8, 60, -25, 4.5)));
        set = new SetHit(c.getDouble(h + "set.reach", 1.1), c.getDouble(h + "set.apex", 3.0),
                c.getDouble(h + "set.quick-apex", 1.2), c.getDouble(h + "set.max-error", 1.5),
                AimProfile.load(c.getConfigurationSection(h + "set.aim"), new AimProfile(1, 8, 60, -15, 4)));
        spike = new Spike(c.getDouble(h + "spike.reach", 1.05), c.getDouble(h + "spike.min-speed", 15),
                c.getDouble(h + "spike.max-speed", 28), c.getDouble(h + "spike.min-topspin", 15),
                c.getDouble(h + "spike.max-topspin", 45), c.getDouble(h + "spike.max-error", 2),
                AimProfile.load(c.getConfigurationSection(h + "spike.aim"), new AimProfile(1.5, 11, 70, -10, 9)));
        tip = new Tip(c.getDouble(h + "tip.reach", 1.15), c.getDouble(h + "tip.apex", 0.8),
                c.getDouble(h + "tip.max-error", 0.8),
                AimProfile.load(c.getConfigurationSection(h + "tip.aim"), new AimProfile(1, 6, 60, -20, 3)));
        standingAttack = new StandingAttack(c.getDouble(h + "standing-attack.reach", 1.05),
                c.getDouble(h + "standing-attack.min-speed", 11), c.getDouble(h + "standing-attack.max-speed", 17),
                c.getDouble(h + "standing-attack.topspin", 20), c.getDouble(h + "standing-attack.max-error", 2),
                AimProfile.load(c.getConfigurationSection(h + "standing-attack.aim"), new AimProfile(3, 12, 50, -15, 9)));
        block = new Block((int) clamp(c.getInt(h + "block.duration-ticks", 12), 2, 60),
                clamp(c.getDouble(h + "block.width", 1.0), 0.3, 3), clamp(c.getDouble(h + "block.reach-over", 0.45), 0, 1.5));
        String s = h + "serve.";
        serve = new Serve(c.getDouble(s + "reach", 1.0),
                c.getDouble(s + "float-min-speed", 13.5), c.getDouble(s + "float-max-speed", 18.5),
                c.getDouble(s + "topspin-min-speed", 15), c.getDouble(s + "topspin-max-speed", 21),
                c.getDouble(s + "jump-min-speed", 19), c.getDouble(s + "jump-max-speed", 28),
                c.getDouble(s + "jump-topspin", 42), c.getDouble(s + "standing-topspin", 25),
                c.getDouble(s + "underhand-apex", 4), c.getDouble(s + "max-error", 2.5),
                c.getDouble(s + "toss-speed", 5.6), c.getDouble(s + "jump-toss-speed", 7),
                c.getDouble(s + "jump-toss-forward", 1.6),
                AimProfile.load(c.getConfigurationSection(s + "aim"), new AimProfile(10, 19, 35, -20, 16)));

        String cd = "court-defaults.";
        courtDefaults = new CourtDefaults(
                clamp(c.getDouble(cd + "free-zone", 3), 0, 10),
                clamp(c.getDouble(cd + "net-band-height", 1), 0.3, 2),
                clamp(c.getDouble(cd + "antenna-above-net", 0.8), 0, 2),
                clamp(c.getDouble(cd + "post-offset", 0.75), 0.1, 3),
                material(c.getString(cd + "floor.court"), Material.ORANGE_TERRACOTTA, log),
                material(c.getString(cd + "floor.free-zone"), Material.BLUE_TERRACOTTA, log),
                material(c.getString(cd + "floor.beach-court"), Material.SAND, log),
                material(c.getString(cd + "floor.beach-free-zone"), Material.SAND, log),
                (int) clamp(c.getInt(cd + "net-columns", 16), 4, 48),
                (int) clamp(c.getInt(cd + "net-rows", 4), 2, 12),
                (int) clamp(c.getInt(cd + "net-interpolation", 2), 0, 10),
                material(c.getString(cd + "line-material"), Material.WHITE_CONCRETE, log),
                (float) clamp(c.getDouble(cd + "view-range", 1.5), 0.1, 10));

        matchDefaults = MatchSettings.load(c.getConfigurationSection("match-defaults"), MatchSettings.standard());
        teamA = team(c.getConfigurationSection("teams.a"), "Red", "#ff5555");
        teamB = team(c.getConfigurationSection("teams.b"), "Blue", "#55aaff");

        for (SoundId id : SoundId.values()) {
            sounds.put(id, SoundSpec.load(c.getConfigurationSection("sounds." + id.path()), "minecraft:block.wool.hit"));
        }
        hud = new Hud(c.getBoolean("hud.sidebar", true), c.getBoolean("hud.bossbar", true),
                c.getBoolean("hud.hit-feedback", true), (int) clamp(c.getInt("hud.sidebar-update-ticks", 5), 1, 40));
        trailMinSpeed = c.getDouble("performance.trail-min-speed", 9);
        particleRange = clamp(c.getDouble("performance.particle-range", 64), 8, 256);
    }

    public SoundSpec sound(SoundId id) {
        return sounds.get(id);
    }

    private static TeamStyle team(ConfigurationSection s, String name, String color) {
        if (s == null) {
            return new TeamStyle(name, TextColor.fromHexString(color));
        }
        TextColor tc = TextColor.fromHexString(s.getString("color", color));
        return new TeamStyle(s.getString("name", name), tc == null ? TextColor.fromHexString(color) : tc);
    }

    private static NamespacedKey key(String raw, String def) {
        NamespacedKey k = raw == null ? null : NamespacedKey.fromString(raw);
        return k != null ? k : NamespacedKey.fromString(def);
    }

    private static Material material(String name, Material def, Logger log) {
        if (name == null) {
            return def;
        }
        Material m = Material.matchMaterial(name);
        if (m == null || !m.isBlock()) {
            log.warning("Unknown block material '" + name + "', using " + def);
            return def;
        }
        return m;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
