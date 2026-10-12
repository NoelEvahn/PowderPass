package dev.powderpass.physics;

import org.joml.Vector3d;

/**
 * Turns Minecraft's stair-stepped block terrain into a continuous, skiable surface.
 *
 * <p>Column heights are blended with a tent kernel so that staircases of full blocks and snow layers
 * become smooth slopes, while genuine cliffs (height differences steeper than {@code cliffSlope}) are
 * excluded from the blend and stay sharp, which lets skiers launch off ledges.
 */
public final class Heightfield {

    private final TerrainSampler sampler;
    private final double radius;
    private final double cliffSlope;
    private final int reach;

    public Heightfield(TerrainSampler sampler, double radius, double cliffSlope) {
        this.sampler = sampler;
        this.radius = Math.max(0.75, radius);
        this.cliffSlope = cliffSlope;
        this.reach = (int) Math.ceil(this.radius);
    }

    public TerrainSampler sampler() {
        return sampler;
    }

    /** Smoothed surface height at (x, z), or NaN when the skier's own column has no surface. */
    public double height(double x, double z, double yHint) {
        int ox = (int) Math.floor(x);
        int oz = (int) Math.floor(z);
        double own = sampler.columnTop(ox, oz, yHint);
        if (Double.isNaN(own)) {
            return Double.NaN;
        }
        double sum = 0.0;
        double weights = 0.0;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                int cx = ox + dx;
                int cz = oz + dz;
                double ddx = cx + 0.5 - x;
                double ddz = cz + 0.5 - z;
                double dist = Math.sqrt(ddx * ddx + ddz * ddz);
                double w = 1.0 - dist / radius;
                if (w <= 0.0) {
                    continue;
                }
                double top = (dx == 0 && dz == 0) ? own : sampler.columnTop(cx, cz, own);
                if (Double.isNaN(top) || Math.abs(top - own) > cliffSlope * Math.max(dist, 0.5) + 0.6) {
                    top = own;
                }
                sum += top * w;
                weights += w;
            }
        }
        return weights <= 0.0 ? own : sum / weights;
    }

    /**
     * Upward-facing unit normal of the smoothed surface. Samples that fall on a cliff or wall column are
     * ignored (one-sided differences are used instead). Falls back to straight up when undefined.
     */
    public Vector3d normal(double x, double z, double yHint, Vector3d out) {
        final double e = 0.5;
        double h0 = height(x, z, yHint);
        if (Double.isNaN(h0)) {
            return out.set(0.0, 1.0, 0.0);
        }
        double gx = gradient(h0, height(x + e, z, h0), height(x - e, z, h0), e);
        double gz = gradient(h0, height(x, z + e, h0), height(x, z - e, h0), e);
        out.set(-gx, 1.0, -gz);
        return out.normalize();
    }

    private double gradient(double h0, double plus, double minus, double e) {
        double limit = cliffSlope * e + 0.3;
        boolean okPlus = !Double.isNaN(plus) && Math.abs(plus - h0) <= limit;
        boolean okMinus = !Double.isNaN(minus) && Math.abs(minus - h0) <= limit;
        if (okPlus && okMinus) {
            return (plus - minus) / (2.0 * e);
        }
        if (okPlus) {
            return (plus - h0) / e;
        }
        if (okMinus) {
            return (h0 - minus) / e;
        }
        return 0.0;
    }
}
