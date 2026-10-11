package dev.powderpass.core;

import dev.powderpass.core.util.MathUtil;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every setting that changes generated blocks. An instance is persisted per world (inside the chunk generator's
 * serialized settings) when the world is created, so later config edits cannot alter future chunks of an existing
 * world. Runtime-only behaviour (volcano activity, caches) lives in the mod's runtime config instead.
 */
public record GenSettings(
        int generatorVersion,
        int terrainCap,
        int seaLevel,
        double horizontalScale,
        double mountainFrequency,
        double flatlandBias,
        double riverDensity,
        double erosionStrength,
        int erosionIterations,
        boolean glaciation,
        double biomeSize,
        double vegetationDensity,
        double slabDetail,
        double anomalyFrequency,
        double volcanoFrequency,
        boolean vanillaStructures) {

    /** Bumped whenever an algorithm change would alter geography for identical settings. */
    public static final int CURRENT_VERSION = 1;
    /** Absolute maximum generated block Y. Enforced; settings may lower it but never raise it. */
    public static final int HARD_CAP = 700;
    public static final int MIN_Y = -64;

    public static GenSettings defaults() {
        return new GenSettings(CURRENT_VERSION, HARD_CAP, 63, 1.0, 1.0, 0.0, 1.0, 1.0, 40, true,
                1.0, 1.0, 1.0, 1.0, 1.0, true);
    }

    /** Returns a copy with every field clamped to its valid range. */
    public GenSettings validated() {
        return new GenSettings(
                generatorVersion <= 0 ? CURRENT_VERSION : generatorVersion,
                MathUtil.clamp(terrainCap, 256, HARD_CAP),
                MathUtil.clamp(seaLevel, 40, 100),
                MathUtil.clamp(horizontalScale, 0.5, 2.0),
                MathUtil.clamp(mountainFrequency, 0.0, 2.0),
                MathUtil.clamp(flatlandBias, -1.0, 1.0),
                MathUtil.clamp(riverDensity, 0.25, 4.0),
                MathUtil.clamp(erosionStrength, 0.0, 2.0),
                MathUtil.clamp(erosionIterations, 0, 160),
                glaciation,
                MathUtil.clamp(biomeSize, 0.5, 2.0),
                MathUtil.clamp(vegetationDensity, 0.0, 2.0),
                MathUtil.clamp(slabDetail, 0.0, 2.0),
                MathUtil.clamp(anomalyFrequency, 0.0, 4.0),
                MathUtil.clamp(volcanoFrequency, 0.0, 3.0),
                vanillaStructures);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("generator_version", generatorVersion);
        m.put("terrain_cap", terrainCap);
        m.put("sea_level", seaLevel);
        m.put("horizontal_scale", horizontalScale);
        m.put("mountain_frequency", mountainFrequency);
        m.put("flatland_bias", flatlandBias);
        m.put("river_density", riverDensity);
        m.put("erosion_strength", erosionStrength);
        m.put("erosion_iterations", erosionIterations);
        m.put("glaciation", glaciation);
        m.put("biome_size", biomeSize);
        m.put("vegetation_density", vegetationDensity);
        m.put("slab_detail", slabDetail);
        m.put("anomaly_frequency", anomalyFrequency);
        m.put("volcano_frequency", volcanoFrequency);
        m.put("vanilla_structures", vanillaStructures);
        return m;
    }

    /** Lenient parse: missing keys take defaults; values are validated. */
    public static GenSettings fromMap(Map<String, ?> m) {
        GenSettings d = defaults();
        return new GenSettings(
                i(m, "generator_version", d.generatorVersion),
                i(m, "terrain_cap", d.terrainCap),
                i(m, "sea_level", d.seaLevel),
                n(m, "horizontal_scale", d.horizontalScale),
                n(m, "mountain_frequency", d.mountainFrequency),
                n(m, "flatland_bias", d.flatlandBias),
                n(m, "river_density", d.riverDensity),
                n(m, "erosion_strength", d.erosionStrength),
                i(m, "erosion_iterations", d.erosionIterations),
                b(m, "glaciation", d.glaciation),
                n(m, "biome_size", d.biomeSize),
                n(m, "vegetation_density", d.vegetationDensity),
                n(m, "slab_detail", d.slabDetail),
                n(m, "anomaly_frequency", d.anomalyFrequency),
                n(m, "volcano_frequency", d.volcanoFrequency),
                b(m, "vanilla_structures", d.vanillaStructures)).validated();
    }

    private static int i(Map<String, ?> m, String k, int def) {
        Object o = m.get(k);
        return o instanceof Number num ? num.intValue() : def;
    }

    private static double n(Map<String, ?> m, String k, double def) {
        Object o = m.get(k);
        return o instanceof Number num ? num.doubleValue() : def;
    }

    private static boolean b(Map<String, ?> m, String k, boolean def) {
        Object o = m.get(k);
        return o instanceof Boolean bo ? bo : def;
    }

    /** Stable fingerprint of all geography-affecting values, used in cache keys and diagnostics. */
    public long fingerprint() {
        long h = 1469598103934665603L;
        for (Map.Entry<String, Object> e : toMap().entrySet()) {
            h = (h ^ e.getKey().hashCode()) * 1099511628211L;
            h = (h ^ Double.doubleToLongBits(e.getValue() instanceof Number nn ? nn.doubleValue()
                    : (Boolean.TRUE.equals(e.getValue()) ? 1 : 0))) * 1099511628211L;
        }
        return h;
    }
}
