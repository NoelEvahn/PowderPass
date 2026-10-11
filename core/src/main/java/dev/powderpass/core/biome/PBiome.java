package dev.powderpass.core.biome;

/**
 * Biome identities. Custom biomes live in the {@code powderpass} namespace and use only vanilla assets; a few
 * vanilla biomes are reused where vanilla behaviour matters (oceans for monuments/shipwrecks, rivers for their
 * spawns, deep dark for ancient cities, cave biomes underground).
 */
public enum PBiome {
    // ---- custom surface biomes ----
    ALLUVIAL_MEADOW("alluvial_meadow", false),
    FLOODPLAIN_WOODLAND("floodplain_woodland", false),
    ROLLING_GRASSLAND("rolling_grassland", false),
    TEMPERATE_MIXED_FOREST("temperate_mixed_forest", false),
    BIRCH_UPLANDS("birch_uplands", false),
    CONIFER_FOOTHILLS("conifer_foothills", false),
    BOREAL_FOREST("boreal_forest", false),
    ALPINE_MEADOW("alpine_meadow", false),
    ROCKY_ALPINE_RIDGE("rocky_alpine_ridge", false),
    SNOWY_SUMMIT("snowy_summit", false),
    FROZEN_TUNDRA("frozen_tundra", false),
    WET_COASTAL_FOREST("wet_coastal_forest", false),
    DRY_SCRUBLAND("dry_scrubland", false),
    STEPPE("steppe", false),
    WARM_SAVANNA("warm_savanna", false),
    TROPICAL_RAINFOREST("tropical_rainforest", false),
    SANDY_DESERT("sandy_desert", false),
    RED_BADLANDS("red_badlands", false),
    KARST_COUNTRY("karst_country", false),
    MARSH("marsh", false),
    MANGROVE_WETLAND("mangrove_wetland", false),
    WINDSWEPT_PLATEAU("windswept_plateau", false),
    BASALT_HIGHLANDS("basalt_highlands", false),
    ASH_BARRENS("ash_barrens", false),
    CRATER_BASIN("crater_basin", false),
    SALT_FLATS("salt_flats", false),
    BARREN_FLATS("barren_flats", false),
    BEACH_DUNES("beach_dunes", false),
    STONY_SHORE("stony_shore", false),
    GLACIAL_VALLEY("glacial_valley", false),
    // ---- reused vanilla biomes ----
    RIVER("river", true),
    FROZEN_RIVER("frozen_river", true),
    OCEAN("ocean", true),
    DEEP_OCEAN("deep_ocean", true),
    WARM_OCEAN("warm_ocean", true),
    LUKEWARM_OCEAN("lukewarm_ocean", true),
    DEEP_LUKEWARM_OCEAN("deep_lukewarm_ocean", true),
    COLD_OCEAN("cold_ocean", true),
    DEEP_COLD_OCEAN("deep_cold_ocean", true),
    FROZEN_OCEAN("frozen_ocean", true),
    DEEP_FROZEN_OCEAN("deep_frozen_ocean", true),
    DEEP_DARK("deep_dark", true),
    DRIPSTONE_CAVES("dripstone_caves", true),
    LUSH_CAVES("lush_caves", true);

    public final String key;
    public final boolean vanilla;

    PBiome(String key, boolean vanilla) {
        this.key = key;
        this.vanilla = vanilla;
    }

    public String id() {
        return (vanilla ? "minecraft:" : "powderpass:") + key;
    }

    public boolean isOcean() {
        return switch (this) {
            case OCEAN, DEEP_OCEAN, WARM_OCEAN, LUKEWARM_OCEAN, DEEP_LUKEWARM_OCEAN, COLD_OCEAN, DEEP_COLD_OCEAN,
                 FROZEN_OCEAN, DEEP_FROZEN_OCEAN -> true;
            default -> false;
        };
    }

    public boolean isCave() {
        return this == DEEP_DARK || this == DRIPSTONE_CAVES || this == LUSH_CAVES;
    }

    private static final PBiome[] VALUES = values();

    public static PBiome byOrdinal(int o) {
        return VALUES[o];
    }

    public static PBiome byKey(String k) {
        for (PBiome b : VALUES) if (b.key.equals(k)) return b;
        return null;
    }
}
