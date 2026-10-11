package dev.powderpass.core.terrain;

/**
 * Vanilla blocks used by the generator, as resource ids. The core stays free of Minecraft classes; the mod maps each
 * constant to a {@code BlockState} once at startup.
 */
public enum B {
    AIR("air"), BEDROCK("bedrock"), STONE("stone"), DEEPSLATE("deepslate"), TUFF("tuff"), ANDESITE("andesite"),
    GRANITE("granite"), DIORITE("diorite"), CALCITE("calcite"), BASALT("basalt"), SMOOTH_BASALT("smooth_basalt"),
    BLACKSTONE("blackstone"), MAGMA("magma_block"), OBSIDIAN("obsidian"), SANDSTONE("sandstone"),
    RED_SANDSTONE("red_sandstone"), SMOOTH_SANDSTONE("smooth_sandstone"), TERRACOTTA("terracotta"),
    WHITE_TC("white_terracotta"), ORANGE_TC("orange_terracotta"), YELLOW_TC("yellow_terracotta"),
    RED_TC("red_terracotta"), BROWN_TC("brown_terracotta"), LIGHT_GRAY_TC("light_gray_terracotta"),
    GRAVEL("gravel"), SAND("sand"), RED_SAND("red_sand"), DIRT("dirt"), COARSE_DIRT("coarse_dirt"),
    ROOTED_DIRT("rooted_dirt"), PODZOL("podzol"), GRASS("grass_block"), MOSS("moss_block"), MUD("mud"),
    PACKED_MUD("packed_mud"), CLAY("clay"), SNOW_BLOCK("snow_block"), ICE("ice"), PACKED_ICE("packed_ice"),
    WATER("water"), LAVA("lava"), DRIPSTONE("dripstone_block");

    public final String id;

    B(String key) {
        this.id = "minecraft:" + key;
    }

    private static final B[] VALUES = values();

    public static B byOrdinal(int o) {
        return VALUES[o];
    }

    public boolean isSoil() {
        return this == GRASS || this == DIRT || this == COARSE_DIRT || this == PODZOL || this == ROOTED_DIRT
                || this == MOSS || this == MUD;
    }
}
