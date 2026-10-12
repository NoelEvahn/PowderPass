package dev.powderpass.config;

import dev.powderpass.course.RaceTracker;
import dev.powderpass.gear.GearTier;
import dev.powderpass.lift.LiftStyle;
import dev.powderpass.physics.SkiParams;
import dev.powderpass.physics.SteeringMode;
import dev.powderpass.physics.SurfaceKind;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** Typed, validated snapshot of config.yml. Rebuilt on every reload. */
public final class PluginConfig {

    public record Tier(double speed, double grip) {
    }

    public record PassType(Material costMaterial, int costAmount, long durationMillis) {
    }

    // general
    public final Set<String> enabledWorlds;
    public final int maxActiveSkiers;
    public final int autosaveSeconds;

    // skiing
    public final SteeringMode defaultSteering;
    public final Set<Material> clickInBlocks;
    public final int autoUnclipRoughTicks;
    public final int crashCooldownTicks;
    public final boolean landingDamage;
    public final double impactDamagePerUnit;
    public final double crashDamage;
    public final Set<Material> snowBlocks;
    public final Set<Material> powderBlocks;
    public final Set<Material> iceBlocks;
    public final SkiParams skiParams;
    public final SkiParams boardParams;
    public final Map<GearTier, Tier> tiers;

    // tricks
    public final int comboWindowTicks;
    public final double comboStep;
    public final double comboMax;

    // visuals
    public final boolean hidePlayerModel;
    public final boolean nametags;
    public final int movementSmoothing;
    public final int poseSmoothing;
    public final float viewRange;
    public final boolean particles;
    public final boolean sounds;
    public final double riderOffset;

    // lifts
    public final double liftSpeed;
    public final double chairSpacing;
    public final int seatsPerChair;
    public final LiftStyle defaultLiftStyle;
    public final double hangerLength;
    public final double seatHeight;
    public final double platformLength;
    public final double maxTowerSpacing;
    public final double minSeatClearance;
    public final double sagRatio;
    public final double stationSlow;
    public final int liftUpdateInterval;
    public final double liftActivationRange;
    public final boolean allowMidrideDismount;
    public final boolean allowRideDown;
    public final boolean liftRequirePass;

    // courses
    public final double gateWidth;
    public final double finishWidth;
    public final RaceTracker.MissPolicy missPolicy;
    public final double missPenaltyTicks;
    public final int maxRunTicks;
    public final boolean ghosts;
    public final int leaderboardSize;
    public final boolean announceRecords;

    // passes
    public final PassType dayPass;
    public final PassType seasonPass;

    public PluginConfig(FileConfiguration c, Logger log) {
        enabledWorlds = new HashSet<>(c.getStringList("general.enabled-worlds"));
        maxActiveSkiers = Math.max(1, c.getInt("general.max-active-skiers", 40));
        autosaveSeconds = Math.max(30, c.getInt("general.autosave-seconds", 300));

        defaultSteering = enumValue(SteeringMode.class, c.getString("skiing.default-steering"), SteeringMode.LOOK, log);
        clickInBlocks = materials(c.getStringList("skiing.click-in-blocks"), log);
        autoUnclipRoughTicks = (int) Math.round(Math.max(0.5, c.getDouble("skiing.auto-unclip-rough-seconds", 4)) * 20);
        crashCooldownTicks = (int) Math.round(Math.max(0, c.getDouble("skiing.crash-cooldown-seconds", 2)) * 20);
        landingDamage = c.getBoolean("skiing.landing-damage", true);
        impactDamagePerUnit = Math.max(0, c.getDouble("skiing.impact-damage-per-unit", 10.0));
        crashDamage = Math.max(0, c.getDouble("skiing.crash-damage", 2.0));
        snowBlocks = materials(c.getStringList("skiing.surface-blocks.snow"), log);
        powderBlocks = materials(c.getStringList("skiing.surface-blocks.powder"), log);
        iceBlocks = materials(c.getStringList("skiing.surface-blocks.ice"), log);

        Map<SurfaceKind, SkiParams.Surface> surfaces = new EnumMap<>(SurfaceKind.class);
        for (SurfaceKind kind : List.of(SurfaceKind.SNOW, SurfaceKind.POWDER, SurfaceKind.ICE, SurfaceKind.ROUGH)) {
            String path = "skiing.surfaces." + kind.name().toLowerCase(Locale.ROOT);
            SkiParams.Surface def = SkiParams.defaultSurfaces().get(kind);
            surfaces.put(kind, new SkiParams.Surface(
                    clamp(c.getDouble(path + ".friction", def.friction()), 0, 2),
                    clamp(c.getDouble(path + ".grip", def.grip()), 0.05, 3)));
        }
        skiParams = physics(c.getConfigurationSection("skiing.physics.skis"), SkiParams.defaultSkis(), defaultSteering, surfaces);
        boardParams = physics(c.getConfigurationSection("skiing.physics.snowboard"), SkiParams.defaultBoard(), defaultSteering, surfaces);

        tiers = new EnumMap<>(GearTier.class);
        for (GearTier tier : GearTier.values()) {
            String path = "tiers." + tier.id();
            tiers.put(tier, new Tier(clamp(c.getDouble(path + ".speed", tier.defaultSpeed()), 0.5, 2.0),
                    clamp(c.getDouble(path + ".grip", tier.defaultGrip()), 0.3, 3.0)));
        }

        comboWindowTicks = Math.max(10, c.getInt("tricks.combo-window-ticks", 60));
        comboStep = clamp(c.getDouble("tricks.combo-step", 0.25), 0, 2);
        comboMax = clamp(c.getDouble("tricks.combo-max", 4.0), 1, 20);

        hidePlayerModel = c.getBoolean("visuals.hide-player-model", true);
        nametags = c.getBoolean("visuals.nametags", true);
        movementSmoothing = clampInt(c.getInt("visuals.movement-smoothing", 2), 0, 10);
        poseSmoothing = clampInt(c.getInt("visuals.pose-smoothing", 2), 0, 10);
        viewRange = (float) clamp(c.getDouble("visuals.view-range", 1.5), 0.2, 8);
        particles = c.getBoolean("visuals.particles", true);
        sounds = c.getBoolean("visuals.sounds", true);
        riderOffset = clamp(c.getDouble("visuals.rider-offset", 0.6), 0, 2);

        liftSpeed = clamp(c.getDouble("lifts.default-speed", 0.2), 0.03, 0.8);
        chairSpacing = clamp(c.getDouble("lifts.chair-spacing", 14), 6, 80);
        seatsPerChair = clampInt(c.getInt("lifts.seats-per-chair", 2), 1, 4);
        defaultLiftStyle = enumValue(LiftStyle.class, c.getString("lifts.default-style"), LiftStyle.CLASSIC, log);
        hangerLength = clamp(c.getDouble("lifts.hanger-length", 2.4), 1.6, 5);
        seatHeight = clamp(c.getDouble("lifts.seat-height", 0.55), 0.2, 1.2);
        platformLength = clamp(c.getDouble("lifts.platform-length", 5), 3, 16);
        maxTowerSpacing = clamp(c.getDouble("lifts.max-tower-spacing", 28), 10, 80);
        minSeatClearance = clamp(c.getDouble("lifts.min-seat-clearance", 3), 1, 20);
        sagRatio = clamp(c.getDouble("lifts.sag-ratio", 0.012), 0, 0.08);
        stationSlow = clamp(c.getDouble("lifts.station-slow", 0.35), 0.1, 1);
        liftUpdateInterval = clampInt(c.getInt("lifts.update-interval", 2), 1, 10);
        liftActivationRange = clamp(c.getDouble("lifts.activation-range", 160), 32, 512);
        allowMidrideDismount = c.getBoolean("lifts.allow-midride-dismount", false);
        allowRideDown = c.getBoolean("lifts.allow-ride-down", true);
        liftRequirePass = c.getBoolean("lifts.require-pass", false);

        gateWidth = clamp(c.getDouble("courses.gate-width", 5), 2, 20);
        finishWidth = clamp(c.getDouble("courses.finish-width", 9), 3, 30);
        missPolicy = enumValue(RaceTracker.MissPolicy.class, c.getString("courses.miss-policy"), RaceTracker.MissPolicy.PENALTY, log);
        missPenaltyTicks = clamp(c.getDouble("courses.miss-penalty-seconds", 3), 0, 60) * 20.0;
        maxRunTicks = clampInt(c.getInt("courses.max-run-seconds", 300), 10, 1800) * 20;
        ghosts = c.getBoolean("courses.ghosts", true);
        leaderboardSize = clampInt(c.getInt("courses.leaderboard-size", 10), 1, 25);
        announceRecords = c.getBoolean("courses.announce-records", true);

        dayPass = passType(c, "passes.day", Material.EMERALD, 2, 24, log);
        seasonPass = passType(c, "passes.season", Material.DIAMOND, 6, 720, log);
    }

    public boolean worldEnabled(String worldName) {
        return enabledWorlds.isEmpty() || enabledWorlds.contains(worldName);
    }

    private static SkiParams physics(ConfigurationSection s, SkiParams d, SteeringMode steering,
                                     Map<SurfaceKind, SkiParams.Surface> surfaces) {
        if (s == null) {
            return d.withSteering(steering);
        }
        return new SkiParams(
                clamp(s.getDouble("gravity", d.gravity()), 0.005, 0.3),
                clamp(s.getDouble("air-drag", d.airDrag()), 0, 0.2),
                clamp(s.getDouble("tuck-drag-factor", d.tuckDragFactor()), 0.1, 1.5),
                clamp(s.getDouble("brake-friction", d.brakeFriction()), 0, 2),
                clamp(s.getDouble("turn-rate", d.turnRate()), 0.5, 45),
                clamp(s.getDouble("turn-speed-falloff", d.turnSpeedFalloff()), 0, 10),
                clamp(s.getDouble("hard-carve-yaw-offset", d.hardCarveYawOffset()), 0, 120),
                clamp(s.getDouble("hard-carve-turn-factor", d.hardCarveTurnFactor()), 1, 5),
                clamp(s.getDouble("tuck-turn-factor", d.tuckTurnFactor()), 0.05, 1.5),
                clamp(s.getDouble("edge-grip", d.edgeGrip()), 0, 0.95),
                clamp(s.getDouble("carve-efficiency", d.carveEfficiency()), 0, 1),
                clamp(s.getDouble("push-accel", d.pushAccel()), 0, 0.2),
                clamp(s.getDouble("push-max-speed", d.pushMaxSpeed()), 0, 2),
                clamp(s.getDouble("jump-base", d.jumpBase()), 0, 2),
                clamp(s.getDouble("jump-per-charge-tick", d.jumpPerChargeTick()), 0, 0.5),
                clampInt(s.getInt("max-jump-charge", d.maxJumpCharge()), 0, 60),
                clamp(s.getDouble("launch-gap", d.launchGap()), 0.05, 2),
                clamp(s.getDouble("max-speed", d.maxSpeed()), 0.2, 5),
                clamp(s.getDouble("wall-crash-speed", d.wallCrashSpeed()), 0.05, 5),
                clamp(s.getDouble("landing-tolerance", d.landingTolerance()), 5, 90),
                clamp(s.getDouble("flip-tolerance", d.flipTolerance()), 5, 180),
                clamp(s.getDouble("safe-impact", d.safeImpact()), 0.05, 5),
                clamp(s.getDouble("crash-impact", d.crashImpact()), 0.1, 10),
                clamp(s.getDouble("air-spin-rate", d.airSpinRate()), 0, 90),
                clamp(s.getDouble("air-flip-rate", d.airFlipRate()), 0, 90),
                clamp(s.getDouble("smoothing-radius", d.smoothingRadius()), 0.75, 4),
                clamp(s.getDouble("cliff-slope", d.cliffSlope()), 0.2, 5),
                1.0, 1.0, steering, surfaces);
    }

    private static PassType passType(FileConfiguration c, String path, Material defMat, int defAmount, double defHours, Logger log) {
        Material m = Material.matchMaterial(c.getString(path + ".cost-material", defMat.name()));
        if (m == null || !m.isItem() || m.isAir()) {
            log.warning("Invalid " + path + ".cost-material, using " + defMat);
            m = defMat;
        }
        int amount = clampInt(c.getInt(path + ".cost-amount", defAmount), 0, 64 * 36);
        double hours = clamp(c.getDouble(path + ".duration-hours", defHours), 0.1, 24 * 3650);
        return new PassType(m, amount, (long) (hours * 3_600_000L));
    }

    private static Set<Material> materials(List<String> names, Logger log) {
        Set<Material> out = EnumSet.noneOf(Material.class);
        for (String name : names) {
            Material m = Material.matchMaterial(name);
            if (m == null) {
                log.warning("Unknown material in config: " + name);
            } else {
                out.add(m);
            }
        }
        return out;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String raw, E def, Logger log) {
        if (raw == null) {
            return def;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warning("Unknown " + type.getSimpleName() + " '" + raw + "', using " + def);
            return def;
        }
    }

    private static double clamp(double v, double min, double max) {
        if (Double.isNaN(v)) {
            return min;
        }
        return Math.max(min, Math.min(max, v));
    }

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
