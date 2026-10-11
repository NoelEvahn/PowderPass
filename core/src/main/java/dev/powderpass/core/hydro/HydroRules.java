package dev.powderpass.core.hydro;

import dev.powderpass.core.GenSettings;
import dev.powderpass.core.geo.Geology;

/**
 * Shared, position-only rules that both the erosion simulation and the final terrain sampler must evaluate
 * identically. Anything that has to agree across a domain border lives here.
 */
public final class HydroRules {
    private HydroRules() {}

    /** Width (blocks) of the blend zone on each side of a domain boundary. */
    public static final double BLEND_W = 28;
    /** Radius around a pass inside which the canonical notch and canonical channel apply. */
    public static final double NOTCH_R = 112;
    /** Lookup radius for passes; larger than the trough's extent so the constraint never ends abruptly. */
    public static final double PASS_LOOKUP_R = 160;
    /** Half length of the canonical straight channel carved through every pass. */
    public static final double PASS_CHANNEL_HALF = 30;

    /**
     * Elevation enforced on domain boundaries (both sides converge to this exactly at the boundary). Divides keep
     * most of their pre-erosion height minus a smooth regional denudation estimate, plus a small bump so interior
     * channels do not run along the boundary.
     */
    public static double boundaryHeight(Geology.GeoSample o, GenSettings s) {
        if (!Double.isNaN(o.fixedLevel)) return o.fixedLevel;
        int sea = s.seaLevel();
        if (o.h0 <= sea) return o.h0;
        double above = o.h0 - sea;
        double denud = Math.min(45, 0.05 * s.erosionStrength() * above);
        double bump = 0.5 + 0.004 * above;
        return o.h0 - denud + bump;
    }

    /**
     * Water gap through a divide at a pass: a V-shaped trough aligned with the flow axis (seed-to-seed direction),
     * floor at the pass level rising gently away from it, tapering out at both ends. Returns the maximum allowed
     * surface at (along, across) relative to the pass, or +infinity outside the trough. Elongated (not radial) so it
     * reads as a gorge rather than a crater.
     */
    public static double notch(double along, double across, int level) {
        double a = Math.abs(along), c = Math.abs(across);
        if (a > NOTCH_LEN || c > NOTCH_R) return Double.POSITIVE_INFINITY;
        double floor = level + 1.5 + 0.035 * a;
        double v = floor + Math.max(0, c - 5) * 0.8;
        double t = (a - NOTCH_LEN * 0.55) / (NOTCH_LEN * 0.45);
        if (t > 0) v += t * t * 400; // fade into the surrounding terrain toward the trough ends
        double sTaper = (c - NOTCH_R * 0.6) / (NOTCH_R * 0.4);
        if (sTaper > 0) v += sTaper * sTaper * 400; // and sideways, well before the lookup radius
        return v;
    }

    public static final double NOTCH_LEN = 100;

    /** Canonical channel width for a discharge. */
    public static double width(double q) {
        return Math.max(1.5, Math.min(30, 1.0 + 0.0036 * Math.sqrt(Math.max(0, q))));
    }

    /** Canonical channel depth for a discharge. */
    public static double depth(double q) {
        return Math.max(1.0, Math.min(6, 0.8 + 0.0007 * Math.sqrt(Math.max(0, q))));
    }
}
