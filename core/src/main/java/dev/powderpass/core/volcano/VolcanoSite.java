package dev.powderpass.core.volcano;

/**
 * A volcano placed by deterministic geology. Geometry is fixed at generation; the live lifecycle (dormant, unrest,
 * eruption, cooling) is tracked separately by the server runtime using {@link #id}.
 */
public record VolcanoSite(
        long id,
        Type type,
        double x, double z,
        double radius,        // base radius of the edifice (blocks)
        double height,        // edifice height above surrounding terrain (blocks)
        double craterRadius,
        double craterDepth,
        boolean extinct,      // permanently extinct (never erupts)
        Origin origin,
        int channelCount,     // number of radial lava channels
        double channelPhase) {

    public enum Type { SHIELD, STRATO, CINDER, CALDERA, ERODED }

    public enum Origin { ARC, RIFT, HOTSPOT }

    /** Edifice elevation contribution at horizontal distance r from the centre (may be negative in craters). */
    public double profile(double r, double angleNoise) {
        double R = radius * (1 + 0.12 * angleNoise);
        switch (type) {
            case SHIELD -> {
                if (r >= R) return 0;
                double t = r / R;
                double cone = height * Math.pow(1 - t * t, 0.85);
                return cone - crater(r);
            }
            case STRATO, ERODED -> {
                if (r >= R) return 0;
                double t = r / R;
                // Concave flanks steepening toward the summit.
                double cone = height * Math.pow(1 - t, 1.7);
                double summit = height * (1 - Math.pow(craterRadius * 1.4 / R, 1.0));
                cone = Math.min(cone, summit + (cone - summit) * 0.25);
                return cone - crater(r);
            }
            case CINDER -> {
                if (r >= R) return 0;
                double t = r / R;
                double cone = height * Math.min(1.0, (1 - t) * 1.6);
                return cone - crater(r);
            }
            case CALDERA -> {
                // Raised rim ring around a collapsed, flat-floored depression.
                double rim = craterRadius;
                if (r >= R) return 0;
                double outer = height * Math.pow(Math.max(0, 1 - Math.max(0, r - rim) / (R - rim)), 1.6);
                if (r >= rim) return outer;
                double wall = Math.min(1, (rim - r) / (rim * 0.18));
                return height - (height + craterDepth) * (wall * wall * (3 - 2 * wall));
            }
        }
        return 0;
    }

    private double crater(double r) {
        if (r >= craterRadius) return 0;
        double t = r / craterRadius;
        return craterDepth * (1 - t * t);
    }

    public boolean canErupt() {
        return !extinct && type != Type.CALDERA && type != Type.ERODED;
    }

    /** Footprint radius including the outer apron. */
    public double footprint() {
        return radius * 1.15 + 16;
    }
}
