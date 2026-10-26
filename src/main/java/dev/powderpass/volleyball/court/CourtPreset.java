package dev.powderpass.volleyball.court;

/** Court size presets. Distances in metres. */
public enum CourtPreset {
    INDOOR(18, 9, 2.43, 3, false, "Official indoor court (men's net 2.43 m)"),
    WOMEN(18, 9, 2.24, 3, false, "Official indoor court (women's net 2.24 m)"),
    BEACH(16, 8, 2.43, 0, true, "Beach court on sand, no attack line"),
    MINI(12, 6, 2.15, 2, false, "Small court for 2v2 - 4v4"),
    DUEL(9, 4.5, 2.0, 0, false, "Tiny court for 1v1");

    public final double length;
    public final double width;
    public final double netHeight;
    public final double attackLine;
    public final boolean beach;
    public final String description;

    CourtPreset(double length, double width, double netHeight, double attackLine, boolean beach, String description) {
        this.length = length;
        this.width = width;
        this.netHeight = netHeight;
        this.attackLine = attackLine;
        this.beach = beach;
        this.description = description;
    }

    public static CourtPreset byName(String s) {
        for (CourtPreset p : values()) {
            if (p.name().equalsIgnoreCase(s)) {
                return p;
            }
        }
        return null;
    }
}
