package com.powderpass.backrooms;

import java.util.Locale;

/** The seven Backrooms levels. Each is backed by its own custom world. */
public enum Level {
    LEVEL_0("level0", "0", "Level 0", "The Lobby", "<#d8c46a>"),
    POOLROOMS("poolrooms", "poolrooms", "The Poolrooms", "Still water, endless tile", "<#7fe3e6>"),
    LEVEL_1("level1", "1", "Level 1", "Habitable Zone", "<#9da3a8>"),
    LEVEL_2("level2", "2", "Level 2", "Pipe Dreams", "<#c9773a>"),
    LEVEL_3("level3", "3", "Level 3", "Electrical Station", "<#e8d34a>"),
    LEVEL_4("level4", "4", "Level 4", "Abandoned Office", "<#b8c6cf>"),
    LEVEL_5("level5", "5", "Level 5", "Terror Hotel", "<#b3263a>");

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

    public static Level parse(String s) {
        if (s == null) {
            return null;
        }
        String t = s.toLowerCase(Locale.ROOT).replace("level_", "").replace("level", "").replace("-", "").trim();
        for (Level l : values()) {
            if (l.arg.equals(t) || l.key.equals(s.toLowerCase(Locale.ROOT)) || l.name().equalsIgnoreCase(s)) {
                return l;
            }
        }
        if (t.startsWith("pool")) {
            return POOLROOMS;
        }
        return null;
    }
}
