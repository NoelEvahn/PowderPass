package dev.powderpass.pass;

import java.util.Locale;

/** Kinds of lift ticket. */
public enum PassType {
    DAY("Day Pass"),
    SEASON("Season Pass");

    private final String display;

    PassType(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static PassType byId(String id) {
        for (PassType t : values()) {
            if (t.id().equalsIgnoreCase(id)) {
                return t;
            }
        }
        return null;
    }
}
