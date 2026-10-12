package dev.powderpass.gear;

import java.util.Locale;

/** What the rider has strapped on. */
public enum GearType {
    SKIS("Skis"),
    SNOWBOARD("Snowboard");

    private final String display;

    GearType(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static GearType byId(String id) {
        for (GearType type : values()) {
            if (type.id().equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }
}
