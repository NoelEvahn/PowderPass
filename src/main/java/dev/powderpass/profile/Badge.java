package dev.powderpass.profile;

import org.bukkit.Material;

/** Achievements earned on the mountain. Several unlock new suits in the locker. */
public enum Badge {
    FIRST_TRACKS("First Tracks", "Click into your gear for the first time.", Material.SNOWBALL),
    CRUISER("Cruiser", "Reach 60 km/h.", Material.FEATHER),
    SPEED_DEMON("Speed Demon", "Reach 100 km/h.", Material.BLAZE_POWDER),
    TERMINAL_VELOCITY("Terminal Velocity", "Reach 130 km/h.", Material.FIREWORK_ROCKET),
    HANG_TIME("Hang Time", "Stay airborne for 2 seconds.", Material.PHANTOM_MEMBRANE),
    BIG_AIR("Big Air", "Stay airborne for 4 seconds.", Material.ELYTRA),
    FULL_CIRCLE("Full Circle", "Land a 360.", Material.CLOCK),
    TEN_EIGHTY("Ten-Eighty", "Land a 1080.", Material.RECOVERY_COMPASS),
    FLIP_SIDE("Flip Side", "Land a flip.", Material.SLIME_BALL),
    DOUBLE_TROUBLE("Double Trouble", "Land a double flip.", Material.ECHO_SHARD),
    SWITCH_HITTER("Switch Hitter", "Land a trick riding backwards.", Material.REPEATER),
    COMBO_KING("Combo King", "Bank a 2,500 point combo.", Material.GOLD_INGOT),
    POWDER_HOUND("Powder Hound", "Ski 500 m through powder snow.", Material.POWDER_SNOW_BUCKET),
    VERTICAL_KILOMETRE("Vertical Kilometre", "Descend 1,000 m in total.", Material.PACKED_ICE),
    MARATHON("Marathon", "Ski 42 km in total.", Material.LEATHER_BOOTS),
    CHAIR_WARMER("Chair Warmer", "Ride chairlifts 25 times.", Material.MINECART),
    GATEKEEPER("Gatekeeper", "Finish a race without missing a gate.", Material.RED_BANNER),
    PODIUM("Podium", "Finish in the top 3 of any course.", Material.GOLD_BLOCK),
    COURSE_RECORD("Course Record", "Set a course record.", Material.NETHER_STAR),
    YARD_SALE("Yard Sale", "Wipe out 10 times. Gear everywhere.", Material.BONE);

    private final String display;
    private final String description;
    private final Material icon;

    Badge(String display, String description, Material icon) {
        this.display = display;
        this.description = description;
        this.icon = icon;
    }

    public String display() {
        return display;
    }

    public String description() {
        return description;
    }

    public Material icon() {
        return icon;
    }
}
