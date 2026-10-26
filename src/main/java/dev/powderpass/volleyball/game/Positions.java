package dev.powderpass.volleyball.game;

/**
 * Starting spots for each rotation position, as fractions of the half court:
 * depth (0 = net, 1 = end line) and lateral (-1 = left, +1 = right, seen from the team facing the net).
 */
public final class Positions {

    private static final double[][][] LAYOUTS = {
            {},
            {{0.55, 0.0}},
            {{0.6, 0.45}, {0.6, -0.45}},
            {{0.7, 0.5}, {0.3, 0.0}, {0.7, -0.5}},
            {{0.7, 0.45}, {0.25, 0.45}, {0.25, -0.45}, {0.7, -0.45}},
            {{0.75, 0.5}, {0.25, 0.6}, {0.25, 0.0}, {0.25, -0.6}, {0.75, -0.5}},
            {{0.75, 0.6}, {0.22, 0.6}, {0.22, 0.0}, {0.22, -0.6}, {0.75, -0.6}, {0.75, 0.0}},
    };

    private Positions() {
    }

    /** {depth, lateral} of a 1-based position for a team of {@code size} players. */
    public static double[] spot(int size, int position) {
        int n = Math.max(1, Math.min(6, size));
        double[][] layout = LAYOUTS[n];
        return layout[Math.floorMod(position - 1, layout.length)];
    }

    /** Front row positions for the back-row attack rule (6v6: 2, 3, 4). */
    public static boolean isFrontRow(int size, int position) {
        if (size != 6) {
            return true;
        }
        return position >= 2 && position <= 4;
    }

    /** Back-row spot (deeper half of the court). For 6v6 these are positions 1, 5 and 6. */
    public static boolean isBackRow(int size, int position) {
        if (size == 6) {
            return position == 1 || position == 5 || position == 6;
        }
        return spot(size, position)[0] > 0.5;
    }

    public static String label(int size, int position) {
        if (size == 6) {
            return switch (position) {
                case 1 -> "P1 · right back (server)";
                case 2 -> "P2 · right front";
                case 3 -> "P3 · middle front";
                case 4 -> "P4 · left front";
                case 5 -> "P5 · left back";
                default -> "P6 · middle back";
            };
        }
        return "P" + position + (position == 1 ? " · server" : "");
    }
}
