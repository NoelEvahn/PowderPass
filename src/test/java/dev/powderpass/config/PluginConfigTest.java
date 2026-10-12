package dev.powderpass.config;

import dev.powderpass.course.RaceTracker;
import dev.powderpass.gear.GearTier;
import dev.powderpass.lift.LiftStyle;
import dev.powderpass.physics.SkiParams;
import dev.powderpass.physics.SteeringMode;
import dev.powderpass.physics.SurfaceKind;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class PluginConfigTest {

    private static YamlConfiguration resource(String name) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(PluginConfigTest.class.getClassLoader().getResourceAsStream(name)), StandardCharsets.UTF_8));
    }

    @Test
    void shippedPhysicsMatchTheUnitTestedDefaults() {
        // PluginConfig itself needs a running server (Material registry), so the YAML is checked directly.
        YamlConfiguration y = resource("config.yml");
        SkiParams d = SkiParams.defaultSkis();
        String k = "skiing.physics.skis.";
        assertEquals(d.gravity(), y.getDouble(k + "gravity"), 1e-12);
        assertEquals(d.airDrag(), y.getDouble(k + "air-drag"), 1e-12);
        assertEquals(d.tuckDragFactor(), y.getDouble(k + "tuck-drag-factor"), 1e-12);
        assertEquals(d.brakeFriction(), y.getDouble(k + "brake-friction"), 1e-12);
        assertEquals(d.turnRate(), y.getDouble(k + "turn-rate"), 1e-12);
        assertEquals(d.turnSpeedFalloff(), y.getDouble(k + "turn-speed-falloff"), 1e-12);
        assertEquals(d.hardCarveYawOffset(), y.getDouble(k + "hard-carve-yaw-offset"), 1e-12);
        assertEquals(d.edgeGrip(), y.getDouble(k + "edge-grip"), 1e-12);
        assertEquals(d.carveEfficiency(), y.getDouble(k + "carve-efficiency"), 1e-12);
        assertEquals(d.pushAccel(), y.getDouble(k + "push-accel"), 1e-12);
        assertEquals(d.jumpBase(), y.getDouble(k + "jump-base"), 1e-12);
        assertEquals(d.maxJumpCharge(), y.getInt(k + "max-jump-charge"));
        assertEquals(d.launchGap(), y.getDouble(k + "launch-gap"), 1e-12);
        assertEquals(d.maxSpeed(), y.getDouble(k + "max-speed"), 1e-12);
        assertEquals(d.wallCrashSpeed(), y.getDouble(k + "wall-crash-speed"), 1e-12);
        assertEquals(d.landingTolerance(), y.getDouble(k + "landing-tolerance"), 1e-12);
        assertEquals(d.flipTolerance(), y.getDouble(k + "flip-tolerance"), 1e-12);
        assertEquals(d.crashImpact(), y.getDouble(k + "crash-impact"), 1e-12);
        assertEquals(d.airSpinRate(), y.getDouble(k + "air-spin-rate"), 1e-12);
        assertEquals(d.smoothingRadius(), y.getDouble(k + "smoothing-radius"), 1e-12);
        assertEquals(d.cliffSlope(), y.getDouble(k + "cliff-slope"), 1e-12);

        SkiParams b = SkiParams.defaultBoard();
        String kb = "skiing.physics.snowboard.";
        assertEquals(b.turnRate(), y.getDouble(kb + "turn-rate"), 1e-12);
        assertEquals(b.edgeGrip(), y.getDouble(kb + "edge-grip"), 1e-12);
        assertEquals(b.pushAccel(), y.getDouble(kb + "push-accel"), 1e-12);
        assertEquals(b.jumpBase(), y.getDouble(kb + "jump-base"), 1e-12);
        assertEquals(b.airSpinRate(), y.getDouble(kb + "air-spin-rate"), 1e-12);

        assertEquals(SkiParams.defaultSurfaces().get(SurfaceKind.ICE).friction(), y.getDouble("skiing.surfaces.ice.friction"), 1e-12);
        assertEquals(SkiParams.defaultSurfaces().get(SurfaceKind.ROUGH).grip(), y.getDouble("skiing.surfaces.rough.grip"), 1e-12);
        for (GearTier tier : GearTier.values()) {
            assertEquals(tier.defaultSpeed(), y.getDouble("tiers." + tier.id() + ".speed"), 1e-12);
            assertEquals(tier.defaultGrip(), y.getDouble("tiers." + tier.id() + ".grip"), 1e-12);
        }
    }

    @Test
    void shippedEnumsAndMaterialsAreSpelledCorrectly() {
        YamlConfiguration y = resource("config.yml");
        SteeringMode.valueOf(y.getString("skiing.default-steering"));
        LiftStyle.valueOf(y.getString("lifts.default-style"));
        RaceTracker.MissPolicy.valueOf(y.getString("courses.miss-policy"));
        java.util.List<String> materials = new java.util.ArrayList<>(y.getStringList("skiing.click-in-blocks"));
        materials.addAll(y.getStringList("skiing.surface-blocks.snow"));
        materials.addAll(y.getStringList("skiing.surface-blocks.powder"));
        materials.addAll(y.getStringList("skiing.surface-blocks.ice"));
        materials.add(y.getString("passes.day.cost-material"));
        materials.add(y.getString("passes.season.cost-material"));
        for (String m : materials) {
            assertDoesNotThrow(() -> Material.valueOf(m), m);
        }
    }

    @Test
    void everyMessageKeyUsedInCodeExists() {
        YamlConfiguration messages = resource("messages.yml");
        String[] keys = {"prefix", "no-permission", "player-only", "world-disabled", "unknown-player", "usage",
                "invalid-name", "invalid-value", "reloaded", "gave", "help", "help-admin",
                "ski.clicked-in", "ski.controls", "ski.not-now", "ski.cooldown", "ski.too-many", "ski.need-snow",
                "ski.not-skiing", "ski.unclipped", "ski.rough-unclip", "ski.crash-title", "ski.crash-subtitle",
                "trick.combo-banked", "badge.unlocked", "badge.suit-unlocked", "badge.title", "badge.subtitle",
                "locker.suit", "lift.survey-started", "lift.survey-cancelled", "lift.no-survey", "lift.other-world",
                "lift.too-long", "lift.too-steep", "lift.line-unloaded", "lift.unloaded-short", "lift.built",
                "lift.rebuilt", "lift.removed", "lift.unknown", "lift.updated", "lift.list-header", "lift.boarded",
                "lift.unloaded", "lift.hold-on", "lift.need-pass", "course.created", "course.gate-added",
                "course.finish-set", "course.undone", "course.nothing-to-undo", "course.reset", "course.removed",
                "course.unknown", "course.other-world", "course.list-header", "course.started", "course.missed",
                "course.missed-penalty", "course.disqualified", "course.timeout", "course.dnf", "course.aborted",
                "course.finished", "course.finish-record", "course.finish-pb", "course.finish-slower",
                "course.record-broadcast", "pass.bought", "pass.cannot-afford", "kiosk.created", "kiosk.removed",
                "kiosk.none-nearby"};
        for (String key : keys) {
            assertTrue(messages.isString(key), "missing message " + key);
        }
    }
}
