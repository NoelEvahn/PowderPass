package dev.powderpass.profile;

import org.bukkit.Color;
import org.bukkit.Material;

/** Colour schemes for the animated skier. Some are unlocked by badges. */
public enum SuitPalette {
    CLASSIC("Classic", Material.RED_CONCRETE, Material.WHITE_CONCRETE, Material.BLACK_CONCRETE, Material.WHITE_WOOL, Color.fromRGB(0xC0392B), null),
    FOREST("Forest", Material.GREEN_CONCRETE, Material.LIME_CONCRETE, Material.BROWN_CONCRETE, Material.YELLOW_WOOL, Color.fromRGB(0x2E7D32), Badge.FIRST_TRACKS),
    GLACIER("Glacier", Material.LIGHT_BLUE_CONCRETE, Material.WHITE_CONCRETE, Material.BLUE_CONCRETE, Material.LIGHT_BLUE_WOOL, Color.fromRGB(0x7DD3FC), Badge.CRUISER),
    SUNSET("Sunset", Material.ORANGE_CONCRETE, Material.YELLOW_CONCRETE, Material.PURPLE_CONCRETE, Material.MAGENTA_WOOL, Color.fromRGB(0xF97316), Badge.HANG_TIME),
    MIDNIGHT("Midnight", Material.BLACK_CONCRETE, Material.GRAY_CONCRETE, Material.BLACK_CONCRETE, Material.MAGENTA_WOOL, Color.fromRGB(0x111827), Badge.SPEED_DEMON),
    RACING("Racing", Material.WHITE_CONCRETE, Material.RED_CONCRETE, Material.BLUE_CONCRETE, Material.RED_WOOL, Color.fromRGB(0xF5F5F5), Badge.GATEKEEPER),
    AURORA("Aurora", Material.CYAN_CONCRETE, Material.LIME_CONCRETE, Material.PURPLE_CONCRETE, Material.LIME_WOOL, Color.fromRGB(0x06B6D4), Badge.DOUBLE_TROUBLE),
    YARD_SALE("Yard Sale", Material.PINK_CONCRETE, Material.LIME_CONCRETE, Material.YELLOW_CONCRETE, Material.CYAN_WOOL, Color.fromRGB(0xF472B6), Badge.YARD_SALE),
    ROYAL("Royal", Material.PURPLE_CONCRETE, Material.YELLOW_CONCRETE, Material.WHITE_CONCRETE, Material.YELLOW_WOOL, Color.fromRGB(0x7E22CE), Badge.TERMINAL_VELOCITY),
    GOLD_MEDAL("Gold Medal", Material.YELLOW_CONCRETE, Material.GOLD_BLOCK, Material.WHITE_CONCRETE, Material.WHITE_WOOL, Color.fromRGB(0xFACC15), Badge.COURSE_RECORD);

    private final String display;
    private final Material jacket;
    private final Material accent;
    private final Material pants;
    private final Material pompom;
    private final Color icon;
    private final Badge unlock;

    SuitPalette(String display, Material jacket, Material accent, Material pants, Material pompom, Color icon, Badge unlock) {
        this.display = display;
        this.jacket = jacket;
        this.accent = accent;
        this.pants = pants;
        this.pompom = pompom;
        this.icon = icon;
        this.unlock = unlock;
    }

    public String display() {
        return display;
    }

    public Material jacket() {
        return jacket;
    }

    public Material accent() {
        return accent;
    }

    public Material pants() {
        return pants;
    }

    public Material pompom() {
        return pompom;
    }

    public Color icon() {
        return icon;
    }

    /** Badge required to wear this suit, or null if always available. */
    public Badge unlock() {
        return unlock;
    }

    public static SuitPalette byName(String name) {
        for (SuitPalette p : values()) {
            if (p.name().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return CLASSIC;
    }
}
