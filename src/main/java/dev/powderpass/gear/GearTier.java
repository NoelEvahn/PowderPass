package dev.powderpass.gear;

import org.bukkit.Material;

import java.util.Locale;

/** Gear quality. Better gear is faster and holds an edge better. */
public enum GearTier {
    TIMBER("Timber", "#d4a373", 0.92, 0.90, Material.OAK_PLANKS, Material.SPRUCE_PLANKS, "oak_boat"),
    ALPINE("Alpine", "#f87171", 1.00, 1.00, Material.RED_CONCRETE, Material.WHITE_CONCRETE, "birch_boat"),
    GLACIER("Glacier", "#7dd3fc", 1.08, 1.10, Material.LIGHT_BLUE_CONCRETE, Material.PACKED_ICE, "pale_oak_boat"),
    AVALANCHE("Avalanche", "#c084fc", 1.15, 1.20, Material.PURPLE_CONCRETE, Material.NETHERITE_BLOCK, "dark_oak_boat");

    private final String display;
    private final String color;
    private final double defaultSpeed;
    private final double defaultGrip;
    private final Material baseBlock;
    private final Material tipBlock;
    private final String skiModel;

    GearTier(String display, String color, double defaultSpeed, double defaultGrip, Material baseBlock,
             Material tipBlock, String skiModel) {
        this.display = display;
        this.color = color;
        this.defaultSpeed = defaultSpeed;
        this.defaultGrip = defaultGrip;
        this.baseBlock = baseBlock;
        this.tipBlock = tipBlock;
        this.skiModel = skiModel;
    }

    public String display() {
        return display;
    }

    /** MiniMessage colour, e.g. {@code <#7dd3fc>}. */
    public String colorTag() {
        return "<" + color + ">";
    }

    public double defaultSpeed() {
        return defaultSpeed;
    }

    public double defaultGrip() {
        return defaultGrip;
    }

    /** Block used to render the ski / board body. */
    public Material baseBlock() {
        return baseBlock;
    }

    /** Block used to render the upturned tips. */
    public Material tipBlock() {
        return tipBlock;
    }

    public String skiModel() {
        return skiModel;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public GearTier previous() {
        return ordinal() == 0 ? null : values()[ordinal() - 1];
    }

    public static GearTier byId(String id) {
        for (GearTier tier : values()) {
            if (tier.id().equalsIgnoreCase(id)) {
                return tier;
            }
        }
        return null;
    }
}
