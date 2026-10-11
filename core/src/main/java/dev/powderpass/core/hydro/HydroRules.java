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

    /** V-shaped valley notch through a divide at a pass: minimum allowed surface at distance d from the pass. */
    public static double notch(double d, int level) {
        double floorHalf = 6;
        return level + 1.5 + Math.max(0, d - floorHalf) * 0.62;
    }

    /** Canonical channel width for a discharge. */
    public static double width(double q) {
        return Math.max(1.5, Math.min(30, 1.0 + 0.0036 * Math.sqrt(Math.max(0, q))));
    }

    /** Canonical channel depth for a discharge. */
    public static double depth(double q) {
        return Math.max(1.0, Math.min(6, 0.8 + 0.0007 * Math.sqrt(Math.max(0, q))));
    }
}
