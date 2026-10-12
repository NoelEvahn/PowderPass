package dev.powderpass.physics;

import java.util.ArrayList;
import java.util.List;

/** Names and scores landed tricks. */
public final class TrickEvaluator {

    public static final int MIN_AIR_TICKS = 8;
    public static final int MIN_GRAB_TICKS = 6;
    public static final int BIG_AIR_TICKS = 40;

    private TrickEvaluator() {
    }

    /**
     * @return the trick, or {@code null} when the jump was too small to count
     */
    public static TrickResult evaluate(double spin, double flip, int grabTicks, int airTicks,
                                       boolean switchLanding, boolean snowboard) {
        int halfSpins = (int) Math.round(Math.abs(spin) / 180.0);
        int flips = (int) Math.round(Math.abs(flip) / 360.0);
        boolean grab = grabTicks >= MIN_GRAB_TICKS;
        if (airTicks < MIN_AIR_TICKS && halfSpins == 0 && flips == 0) {
            return null;
        }

        List<String> parts = new ArrayList<>();
        if (switchLanding && (halfSpins > 0 || flips > 0)) {
            parts.add("Switch");
        }
        if (flips > 0) {
            String multiple = switch (flips) {
                case 1 -> "";
                case 2 -> "Double ";
                case 3 -> "Triple ";
                default -> flips + "x ";
            };
            parts.add(multiple + (flip < 0 ? "Backflip" : "Frontflip"));
        }
        if (halfSpins > 0) {
            parts.add(Integer.toString(halfSpins * 180));
        }
        if (grab) {
            parts.add(grabName(halfSpins > 0, flips > 0, spin < 0, snowboard) + " Grab");
        }
        if (parts.isEmpty() || (parts.size() == 1 && parts.get(0).equals("Switch"))) {
            parts.clear();
            parts.add(airTicks >= BIG_AIR_TICKS ? "Big Air" : "Air");
        }

        double score = airTicks * 4.0;
        if (halfSpins > 0) {
            score += halfSpins * 90.0 * (1.0 + 0.15 * (halfSpins - 1));
        }
        if (flips > 0) {
            score += flips * 350.0 * (1.0 + 0.3 * (flips - 1));
        }
        if (grab) {
            score += 60.0 + grabTicks * 3.0;
        }
        if (switchLanding && (halfSpins > 0 || flips > 0)) {
            score *= 1.2;
        }
        int signedFlips = flip < 0 ? -flips : flips;
        return new TrickResult(String.join(" ", parts), (int) Math.round(score), halfSpins * 180, signedFlips,
                grab, switchLanding, airTicks);
    }

    private static String grabName(boolean spun, boolean flipped, boolean spinLeft, boolean snowboard) {
        if (snowboard) {
            if (flipped) {
                return "Method";
            }
            if (spun) {
                return spinLeft ? "Indy" : "Melon";
            }
            return "Stalefish";
        }
        if (flipped) {
            return "Japan";
        }
        if (spun) {
            return spinLeft ? "Mute" : "Tail";
        }
        return "Safety";
    }
}
