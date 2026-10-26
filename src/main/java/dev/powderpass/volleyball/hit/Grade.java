package dev.powderpass.volleyball.hit;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/** Contact quality bucket shown to the player. */
public enum Grade {
    PERFECT("PERFECT", NamedTextColor.GOLD),
    GOOD("GOOD", NamedTextColor.GREEN),
    OK("OK", NamedTextColor.YELLOW),
    POOR("POOR", NamedTextColor.RED);

    public final String label;
    public final TextColor color;

    Grade(String label, TextColor color) {
        this.label = label;
        this.color = color;
    }

    public static Grade of(double quality) {
        if (quality >= 0.85) {
            return PERFECT;
        }
        if (quality >= 0.6) {
            return GOOD;
        }
        if (quality >= 0.3) {
            return OK;
        }
        return POOR;
    }
}
