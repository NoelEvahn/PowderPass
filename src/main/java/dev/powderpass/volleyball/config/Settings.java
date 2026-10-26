package dev.powderpass.volleyball.config;

import dev.powderpass.volleyball.game.MatchSettings;
import dev.powderpass.volleyball.physics.PhysicsParams;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** Typed, immutable view of config.yml. Rebuilt on /vb reload. */
public final class Settings {

    /** How the plugin decides which players have the optional resource pack. */
    public enum PackMode { AUTO, EVERYONE, NOBODY }

    public record ResourcePack(PackMode mode, String url, String sha1, boolean required, String prompt) {
    }

    public record Ball(int detail, dev.powderpass.volleyball.ball.BallModel.Style style, Material[] colors, double visualSize, int teleportInterpolation,
                       int rotationInterpolation, boolean shadow, boolean trail, int idleDespawnSeconds,
                       int maxPracticeBalls) {
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
                                Material lineMaterial, float viewRange, boolean furniture, boolean netFill) {
    }

    public record TeamStyle(String name, TextColor color) {
    }

    public record Hud(boolean sidebar, boolean bossbar, boolean hitFeedback, int sidebarUpdateTicks) {
    }

    /** Every sound the plugin plays, with its vanilla fallback and the resource-pack sound event. */
    public enum SoundId {
        BUMP("minecraft:block.wool.hit", 1.4f, 0.7f, "powderpass:ball.bump"),
        DIG("minecraft:block.wool.fall", 1.4f, 0.8f, "powderpass:ball.dig"),
        SET("minecraft:block.wool.place", 1.2f, 1.3f, "powderpass:ball.set"),
        SPIKE("minecraft:entity.player.attack.strong", 1.6f, 0.8f, "powderpass:ball.spike"),
        SPIKE_PERFECT("minecraft:entity.player.attack.crit", 1.6f, 0.9f, "powderpass:ball.spike_perfect"),
        TIP("minecraft:block.wool.step", 1.0f, 1.5f, "powderpass:ball.tip"),
        SERVE("minecraft:entity.player.attack.knockback", 1.5f, 1.0f, "powderpass:ball.serve"),
        SERVE_JUMP("minecraft:entity.player.attack.strong", 1.7f, 1.1f, "powderpass:ball.serve_jump"),
        BLOCK("minecraft:entity.player.attack.sweep", 1.4f, 0.7f, "powderpass:ball.block"),
        BODY("minecraft:entity.player.attack.weak", 1.2f, 0.9f, "powderpass:ball.body"),
        FLOOR("minecraft:block.wood.hit", 1.3f, 0.8f, "powderpass:ball.floor"),
        SAND("minecraft:block.sand.hit", 1.3f, 0.9f, "powderpass:ball.sand"),
        WALL("minecraft:block.stone.hit", 1.0f, 1.0f, "powderpass:ball.wall"),
        NET("minecraft:block.wool.break", 1.0f, 0.6f, "powderpass:net.hit"),
        TAPE("minecraft:block.chain.hit", 1.0f, 1.4f, "powderpass:net.tape"),
        ANTENNA("minecraft:block.bamboo.hit", 1.0f, 1.6f, "powderpass:net.antenna"),
        TOSS("minecraft:entity.egg.throw", 0.8f, 0.6f, "powderpass:ball.toss"),
        WHISTLE("minecraft:block.note_block.flute", 2.0f, 2.0f, "powderpass:whistle.short"),
        WHISTLE_LONG("minecraft:block.note_block.flute", 2.0f, 1.9f, "powderpass:whistle.long"),
        POINT("minecraft:entity.experience_orb.pickup", 1.0f, 1.2f, "powderpass:ui.point"),
        CHEER("minecraft:entity.villager.celebrate", 1.2f, 1.1f, "powderpass:crowd.cheer"),
        APPLAUSE("minecraft:entity.firework_rocket.twinkle", 1.5f, 1.0f, "powderpass:crowd.applause"),
        OOH("minecraft:entity.villager.ambient", 1.0f, 1.3f, "powderpass:crowd.ooh"),
        WIN("minecraft:ui.toast.challenge_complete", 1.0f, 1.0f, "powderpass:crowd.win"),
        COUNTDOWN("minecraft:block.note_block.hat", 1.0f, 1.0f, "powderpass:ui.countdown"),
        GO("minecraft:block.note_block.pling", 1.0f, 2.0f, "powderpass:ui.go"),
        JUMP("minecraft:entity.rabbit.jump", 0.8f, 1.3f, "powderpass:player.jump"),
        DIVE("minecraft:entity.player.small_fall", 1.2f, 0.9f, "powderpass:player.dive"),
        WHIFF("minecraft:entity.player.attack.nodamage", 0.9f, 1.2f, "powderpass:player.whiff");

        final String vanillaKey;
        final float volume;
        final float pitch;
        final String packKey;

        SoundId(String vanillaKey, float volume, float pitch, String packKey) {
            this.vanillaKey = vanillaKey;
            this.volume = volume;
            this.pitch = pitch;
            this.packKey = packKey;
        }

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
    /** Jersey colour of liberos (contrasting, as the rules require). */
    public final TextColor liberoColor;
    public final Map<SoundId, SoundPair> sounds = new EnumMap<>(SoundId.class);
    public final Hud hud;
    public final double trailMinSpeed;
    public final double particleRange;

    public Settings(FileConfiguration c, Logger log) {
        PackMode mode;
        try {
            mode = PackMode.valueOf(c.getString("resource-pack.mode", "auto").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warning("resource-pack.mode must be auto, everyone or nobody");
            mode = PackMode.AUTO;
        }
        resourcePack = new ResourcePack(mode, c.getString("resource-pack.url", "").trim(),
                c.getString("resource-pack.sha1", "").trim(), c.getBoolean("resource-pack.required", false),
                c.getString("resource-pack.prompt", ""));

        List<String> colorNames = c.getStringList("ball.colors");
        Material[] colors = {Material.YELLOW_CONCRETE, Material.WHITE_CONCRETE, Material.BLUE_CONCRETE};
        for (int i = 0; i < Math.min(3, colorNames.size()); i++) {
            colors[i] = material(colorNames.get(i), colors[i], log);
        }
        int detail = (int) clamp(c.getInt("ball.detail", 12), 6, 14);
        dev.powderpass.volleyball.ball.BallModel.Style style;
        try {
            style = dev.powderpass.volleyball.ball.BallModel.Style.valueOf(
                    c.getString("ball.style", "classic").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warning("ball.style must be classic or caps");
            style = dev.powderpass.volleyball.ball.BallModel.Style.CLASSIC;
        }
        ball = new Ball(
                detail + detail % 2,
                style,
                colors,
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
                (int) clamp(c.getInt(cd + "net-columns", 20), 4, 48),
                (int) clamp(c.getInt(cd + "net-rows", 6), 2, 12),
                (int) clamp(c.getInt(cd + "net-interpolation", 2), 0, 10),
                material(c.getString(cd + "line-material"), Material.WHITE_CONCRETE, log),
                (float) clamp(c.getDouble(cd + "view-range", 1.5), 0.1, 10),
                c.getBoolean(cd + "furniture", true),
                c.getBoolean(cd + "net-fill", true));

        matchDefaults = MatchSettings.load(c.getConfigurationSection("match-defaults"), MatchSettings.standard());
        teamA = team(c.getConfigurationSection("teams.a"), "Red", "#ff5555");
        teamB = team(c.getConfigurationSection("teams.b"), "Blue", "#55aaff");
        TextColor lib = TextColor.fromHexString(c.getString("teams.libero-color", "#f2f2f2"));
        liberoColor = lib == null ? TextColor.color(0xf2f2f2) : lib;

        for (SoundId id : SoundId.values()) {
            ConfigurationSection sec = c.getConfigurationSection("sounds." + id.path());
            ConfigurationSection vanilla = sec == null ? null : sec.getConfigurationSection("vanilla");
            ConfigurationSection pack = sec == null ? null : sec.getConfigurationSection("pack");
            // older configs had key/volume/pitch directly under the sound
            if (vanilla == null && sec != null && sec.contains("key")) {
                vanilla = sec;
            }
            sounds.put(id, new SoundPair(
                    SoundSpec.load(vanilla, id.vanillaKey, id.volume, id.pitch),
                    SoundSpec.load(pack, id.packKey, 1.0f, 1.0f)));
        }
        hud = new Hud(c.getBoolean("hud.sidebar", true), c.getBoolean("hud.bossbar", true),
                c.getBoolean("hud.hit-feedback", true), (int) clamp(c.getInt("hud.sidebar-update-ticks", 5), 1, 40));
        trailMinSpeed = c.getDouble("performance.trail-min-speed", 9);
        particleRange = clamp(c.getDouble("performance.particle-range", 64), 8, 256);
    }

    public SoundPair sound(SoundId id) {
        return sounds.get(id);
    }

    private static TeamStyle team(ConfigurationSection s, String name, String color) {
        if (s == null) {
            return new TeamStyle(name, TextColor.fromHexString(color));
        }
        TextColor tc = TextColor.fromHexString(s.getString("color", color));
        return new TeamStyle(s.getString("name", name), tc == null ? TextColor.fromHexString(color) : tc);
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
