package dev.powderpass.core.biome;

import dev.powderpass.core.PowderWorld;
import dev.powderpass.core.anomaly.Anomalies;
import dev.powderpass.core.anomaly.Anomaly;
import dev.powderpass.core.geo.BaseTerrain;
import dev.powderpass.core.geo.Province;
import dev.powderpass.core.noise.Simplex;
import dev.powderpass.core.terrain.TerrainSampler;
import dev.powderpass.core.util.Hash;
import dev.powderpass.core.volcano.VolcanoSite;

/**
 * Assigns biomes from climate (temperature with lapse rate, precipitation with rain shadow), terrain (elevation,
 * slope at the 8-block simulation scale), hydrology (river proximity, discharge, wetness), substrate (province,
 * overlays) and ocean influence. Terrain shape and biome identity are related but separate: forests cover both flats
 * and hills; mountains exist in several climates. Inputs are smooth at the scale of hundreds of blocks, which keeps
 * biomes in coherent regions rather than one-chunk patches.
 */
public final class BiomeClassifier {
    private final PowderWorld world;
    private final int sea;
    private final long caveSeed, edgeSeed;

    public BiomeClassifier(PowderWorld world) {
        this.world = world;
        this.sea = world.settings.seaLevel();
        this.caveSeed = Hash.subSeed(world.seed, "biome.caves");
        this.edgeSeed = Hash.subSeed(world.seed, "biome.edge");
    }

    /** Surface biome of a sampled column. */
    public PBiome surface(TerrainSampler.Column c) {
        double h = c.height;
        double elev = h - sea;
        // Jitter thresholds slightly with a smooth field so transitions are natural, not contour lines.
        double jit = 0.06 * Simplex.fbm(edgeSeed, c.x / 180.0, c.z / 180.0, 2, 2.0, 0.5);
        double t = world.climate.temperatureAt(c.baseTemp, h) + jit;
        double p = c.precip + jit;
        double slope = c.gridSlope;
        Province prov = c.geo.province;

        // ---- water bodies ----
        if (c.waterKind == TerrainSampler.WATER_SEA && !Double.isNaN(c.waterLevel)) {
            double depth = c.waterLevel - h;
            boolean deep = depth > 28;
            if (t < -0.55) return deep ? PBiome.DEEP_FROZEN_OCEAN : PBiome.FROZEN_OCEAN;
            if (t < -0.12) return deep ? PBiome.DEEP_COLD_OCEAN : PBiome.COLD_OCEAN;
            if (t < 0.38) return deep ? PBiome.DEEP_OCEAN : PBiome.OCEAN;
            if (t < 0.66) return deep ? PBiome.DEEP_LUKEWARM_OCEAN : PBiome.LUKEWARM_OCEAN;
            return PBiome.WARM_OCEAN;
        }
        if (c.channel && c.waterKind == TerrainSampler.WATER_RIVER && c.riverWidth >= 5) {
            return t < -0.45 ? PBiome.FROZEN_RIVER : PBiome.RIVER;
        }

        // ---- overlays: anomalies and volcanic edifices ----
        int ov = c.geo.overlay;
        double os = c.geo.overlayStrength;
        if (ov >= Anomalies.OVERLAY_BASE) {
            Anomaly.Type at = Anomaly.Type.values()[ov - Anomalies.OVERLAY_BASE];
            if (at == Anomaly.Type.SALT_FLAT && os > 0.35) return PBiome.SALT_FLATS;
            if (at == Anomaly.Type.BARREN_FLAT && os > 0.4) return PBiome.BARREN_FLATS;
            if ((at == Anomaly.Type.IMPACT_CRATER || at == Anomaly.Type.DEEP_PIT) && os > 0.9) return PBiome.CRATER_BASIN;
        } else if (ov >= BaseTerrain.OVERLAY_VOLCANO_BASE && ov < BaseTerrain.OVERLAY_VOLCANO_BASE + 10) {
            VolcanoSite.Type vt = VolcanoSite.Type.values()[ov - BaseTerrain.OVERLAY_VOLCANO_BASE];
            boolean young = vt == VolcanoSite.Type.STRATO || vt == VolcanoSite.Type.CINDER || vt == VolcanoSite.Type.SHIELD;
            if (young && os > 0.25) return PBiome.ASH_BARRENS;
            if (os > 0.1 && t > -0.4) return PBiome.BASALT_HIGHLANDS;
        }

        // ---- cold / high ----
        if ((t < -0.78 && elev > 180) || (elev > 430 && t < -0.25)) return slope > 1.25 ? PBiome.ROCKY_ALPINE_RIDGE : PBiome.SNOWY_SUMMIT;
        if (elev > 230 && slope > 1.35) return PBiome.ROCKY_ALPINE_RIDGE;
        boolean aboveTreeline = t < -0.42 || elev > 395;
        if (aboveTreeline) {
            if (slope > 0.95) return PBiome.ROCKY_ALPINE_RIDGE;
            if (elev < 160) return PBiome.FROZEN_TUNDRA;
            return t < -0.62 ? PBiome.SNOWY_SUMMIT : PBiome.ALPINE_MEADOW;
        }
        if (c.ice > 2000 && elev > 60 && slope < 0.7 && t < 0.05) return PBiome.GLACIAL_VALLEY;

        // ---- coasts ----
        boolean nearSea = c.geo.continental < 0.12 && elev < 6 && elev > -2;
        if (nearSea) {
            if (t > 0.5 && p > 0.85 && slope < 0.15) return PBiome.MANGROVE_WETLAND;
            if (t < -0.25 || slope > 0.55 || prov == Province.VOLCANIC) return PBiome.STONY_SHORE;
            return PBiome.BEACH_DUNES;
        }

        // ---- wetlands and floodplains ----
        boolean flat = slope < 0.09;
        boolean lowland = elev < 55;
        if (flat && lowland && p > 0.92 && (c.drainage > 60000 || c.sediment > 0.8)) {
            return t > 0.5 ? PBiome.MANGROVE_WETLAND : PBiome.MARSH;
        }
        boolean floodplain = c.riverQ > 300000 && c.riverDist < c.riverWidth * 3 + 40 && c.riverGrad < 0.004 && slope < 0.18;
        if (floodplain && t > -0.3) {
            return p > 0.8 ? PBiome.FLOODPLAIN_WOODLAND : PBiome.ALLUVIAL_MEADOW;
        }

        // ---- substrate-driven regions ----
        if (prov == Province.KARST && p > 0.5 && t > -0.3) return PBiome.KARST_COUNTRY;
        if (prov == Province.PLATEAU && elev > 70) {
            if (p < 0.45 && t > 0.15) return PBiome.RED_BADLANDS;
            if (p < 0.75) return PBiome.WINDSWEPT_PLATEAU;
        }
        if (prov == Province.VOLCANIC && t > -0.4) return PBiome.BASALT_HIGHLANDS;

        // ---- climate matrix ----
        if (t > 0.52) {
            if (p < 0.28) return (prov == Province.SEDIMENTARY_BASIN || prov == Province.PLATEAU) && elev > 40 ? PBiome.RED_BADLANDS : PBiome.SANDY_DESERT;
            if (p < 0.52) return PBiome.DRY_SCRUBLAND;
            if (p < 0.9) return slope < 0.12 ? PBiome.WARM_SAVANNA : PBiome.DRY_SCRUBLAND;
            return PBiome.TROPICAL_RAINFOREST;
        }
        if (t > 0.08) {
            if (p < 0.3) return PBiome.STEPPE;
            if (p < 0.5) return slope < 0.2 ? PBiome.ROLLING_GRASSLAND : PBiome.DRY_SCRUBLAND;
            if (p > 1.0 && c.geo.continental < 0.35) return PBiome.WET_COASTAL_FOREST;
            if (slope < 0.1 && p < 0.85) return PBiome.ROLLING_GRASSLAND;
            if (elev > 150 && c.geo.orogeny > 0.12) return PBiome.CONIFER_FOOTHILLS;
            if (elev > 95 && p < 0.9) return PBiome.BIRCH_UPLANDS;
            return PBiome.TEMPERATE_MIXED_FOREST;
        }
        if (t > -0.38) {
            if (p < 0.32) return PBiome.STEPPE;
            if (c.geo.orogeny > 0.1 || elev > 160) return PBiome.CONIFER_FOOTHILLS;
            if (slope < 0.1 && p < 0.7) return PBiome.ROLLING_GRASSLAND;
            if (elev > 90) return PBiome.BIRCH_UPLANDS;
            return PBiome.BOREAL_FOREST;
        }
        return p < 0.5 || slope < 0.15 ? PBiome.FROZEN_TUNDRA : PBiome.BOREAL_FOREST;
    }

    /** Biome at an absolute height in a column: cave biomes deep underground, the surface biome elsewhere. */
    public PBiome at(TerrainSampler.Column c, PBiome surface, double y) {
        double depthBelow = c.height - y;
        if (depthBelow < 28 || surface.isOcean()) return surface;
        double n1 = Simplex.fbm(caveSeed, c.x / 260.0, c.z / 260.0, 2, 2.0, 0.5);
        double n2 = Simplex.noise3(caveSeed + 1, c.x / 180.0, y / 90.0, c.z / 180.0);
        if (y < -12 && c.height > 120 && n1 > 0.25) return PBiome.DEEP_DARK;
        if (n2 > 0.42 && c.precip > 0.75) return PBiome.LUSH_CAVES;
        if (n2 < -0.45) return PBiome.DRIPSTONE_CAVES;
        return surface;
    }
}
