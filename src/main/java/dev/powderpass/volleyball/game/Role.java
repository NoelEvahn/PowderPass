package dev.powderpass.volleyball.game;

/** Playing positions (specialisations) a player can pick before a match. */
public enum Role {
    SETTER("Setter", "S"),
    OUTSIDE("Outside hitter", "OH"),
    MIDDLE("Middle blocker", "MB"),
    OPPOSITE("Opposite", "OPP"),
    LIBERO("Libero", "L"),
    ANY("Any position", "");

    private final String displayName;
    private final String shortName;

    Role(String displayName, String shortName) {
        this.displayName = displayName;
        this.shortName = shortName;
    }

    public String displayName() {
        return displayName;
    }

    public String shortName() {
        return shortName;
    }

    public static Role byName(String s) {
        if (s == null) {
            return null;
        }
        for (Role r : values()) {
            if (r.name().equalsIgnoreCase(s) || r.shortName.equalsIgnoreCase(s)) {
                return r;
            }
        }
        return switch (s.toLowerCase(java.util.Locale.ROOT)) {
            case "outside-hitter", "hitter", "wing" -> OUTSIDE;
            case "middle-blocker", "blocker" -> MIDDLE;
            case "opp", "opposite-hitter" -> OPPOSITE;
            default -> null;
        };
    }
}
