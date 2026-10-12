package dev.powderpass.physics;

/** Why a run ended involuntarily. */
public enum CrashReason {
    WALL("Hit an obstacle"),
    BAD_LANDING("Landed sideways"),
    HEAD_PLANT("Over-rotated"),
    HARD_LANDING("Landed too hard"),
    LIQUID("Skied into water");

    private final String display;

    CrashReason(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }
}
