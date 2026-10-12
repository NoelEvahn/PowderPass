package com.powderpass.backrooms;

import java.util.Locale;

/** The Backrooms levels. Each is backed by its own custom world. */
public enum Level {
    LEVEL_0("level0", "0", "Level 0", "The Lobby", "<#d8c46a>"),
    LEVEL_1("level1", "1", "Level 1", "Habitable Zone", "<#9da3a8>"),
    POOLROOMS("poolrooms", "poolrooms", "The Poolrooms", "Still water, endless tile", "<#7fe3e6>"),
    LEVEL_4("level4", "4", "Level 4", "Abandoned Office", "<#b8c6cf>"),
    PLAINS("plains", "plains", "The Plains", "It is always this evening", "<#a7b39a>");

    private final String key;
    private final String arg;
    private final String displayName;
    private final String subtitle;
    private final String color;

    Level(String key, String arg, String displayName, String subtitle, String color) {
        this.key = key;
        this.arg = arg;
        this.displayName = displayName;
        this.subtitle = subtitle;
        this.color = color;
    }

    public String key() {
        return key;
    }

    public String arg() {
        return arg;
    }

    public String displayName() {
        return displayName;
    }

    public String subtitle() {
        return subtitle;
    }

    /** MiniMessage colour tag for this level's accent colour. */
    public String color() {
        return color;
    }

    /** Levels open to the sky (no ceiling, daylight). */
    public boolean outdoor() {
        return this == PLAINS;
    }

    public static Level parse(String s) {
        if (s == null) {
            return null;
        }
        String low = s.toLowerCase(Locale.ROOT);
        String t = low.replace("level_", "").replace("level", "").replace("-", "").trim();
        for (Level l : values()) {
            if (l.arg.equals(t) || l.key.equals(low) || l.name().equalsIgnoreCase(s)) {
                return l;
            }
        }
        if (t.startsWith("pool")) return POOLROOMS;
        if (t.startsWith("office")) return LEVEL_4;
        if (t.startsWith("habit")) return LEVEL_1;
        if (t.startsWith("plain") || t.startsWith("field")) return PLAINS;
        return null;
    }
}
