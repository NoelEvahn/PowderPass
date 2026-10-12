package dev.powderpass.lift;

import org.bukkit.Material;

/** Colour and material schemes for chairlifts. */
public enum LiftStyle {
    CLASSIC("Classic", Material.IRON_BLOCK, Material.GRAY_CONCRETE, Material.RED_CONCRETE, Material.RED_CONCRETE,
            Material.BLACK_CONCRETE, Material.RED_TERRACOTTA, Material.POLISHED_DEEPSLATE),
    ALPINE("Alpine", Material.SPRUCE_LOG, Material.DARK_OAK_PLANKS, Material.SPRUCE_PLANKS, Material.SPRUCE_PLANKS,
            Material.IRON_BLOCK, Material.SPRUCE_PLANKS, Material.STRIPPED_DARK_OAK_LOG),
    MODERN("Modern", Material.WHITE_CONCRETE, Material.LIGHT_GRAY_CONCRETE, Material.BLACK_CONCRETE, Material.GRAY_CONCRETE,
            Material.ORANGE_CONCRETE, Material.WHITE_CONCRETE, Material.YELLOW_CONCRETE),
    GLACIER("Glacier", Material.LIGHT_BLUE_CONCRETE, Material.WHITE_CONCRETE, Material.BLUE_CONCRETE, Material.LIGHT_BLUE_CONCRETE,
            Material.WHITE_CONCRETE, Material.PACKED_ICE, Material.CYAN_CONCRETE);

    private final String display;
    private final Material tower;
    private final Material arm;
    private final Material seat;
    private final Material back;
    private final Material bar;
    private final Material roof;
    private final Material wheel;

    LiftStyle(String display, Material tower, Material arm, Material seat, Material back, Material bar, Material roof, Material wheel) {
        this.display = display;
        this.tower = tower;
        this.arm = arm;
        this.seat = seat;
        this.back = back;
        this.bar = bar;
        this.roof = roof;
        this.wheel = wheel;
    }

    public String display() {
        return display;
    }

    public Material tower() {
        return tower;
    }

    public Material arm() {
        return arm;
    }

    public Material seat() {
        return seat;
    }

    public Material back() {
        return back;
    }

    public Material bar() {
        return bar;
    }

    public Material roof() {
        return roof;
    }

    public Material wheel() {
        return wheel;
    }

    public Material cable() {
        return Material.BLACK_CONCRETE;
    }

    public Material grip() {
        return Material.GRAY_CONCRETE;
    }
}
