package dev.powderpass.core.volcano;

import dev.powderpass.core.terrain.TerrainSampler;
import dev.powderpass.core.util.BoundedCache;
import dev.powderpass.core.util.Hash;
import dev.powderpass.core.util.MathUtil;

/**
 * Generated lava pathways of eruption-capable volcanoes: a breach notch from the vent through the crater rim, then
 * shallow grooves down the flank that end in small enclosed lava-field basins. Floors descend monotonically from
 * the vent, so lava released at the vent by the runtime flows along the groove and pools in the terminal basin on
 * pristine terrain. Paths are deterministic metadata shared by generation and the eruption runtime.
 */
public final class VolcanoChannels {
    public static final double STEP = 3.0;
    public static final double GROOVE_HALF = 1.6;
    public static final double BASIN_R = 7.0;

    /** One channel: vertices from the vent outward with their floor heights (top solid block of the groove). */
    public record Channel(float[] x, float[] z, float[] floor) {
        public int size() {
            return x.length;
        }
    }

    public record Geometry(VolcanoSite site, int ventX, int ventY, int ventZ, Channel[] channels) {}

    private final TerrainSampler sampler;
    private final BoundedCache<Geometry> cache = BoundedCache.ofCount(256);

    public VolcanoChannels(TerrainSampler sampler) {
        this.sampler = sampler;
    }

    public Geometry geometry(VolcanoSite v) {
        return cache.getOrCompute(v.id(), k -> compute(v));
    }

    private Geometry compute(VolcanoSite v) {
        TerrainSampler.Column c = new TerrainSampler.Column();
        int vx = (int) Math.floor(v.x()), vz = (int) Math.floor(v.z());
        sampler.sampleBase(vx, vz, c);
        int ventY = (int) Math.floor(c.height);
        Channel[] chans = new Channel[v.channelCount()];
        for (int k = 0; k < chans.length; k++) {
            double a = v.channelPhase() + k * Math.PI * 2 / chans.length
                    + (Hash.unit(Hash.mix(v.id() + 31L * k)) - 0.5) * 0.6;
            int n = Math.max(4, (int) Math.ceil(v.radius() * 0.8 / STEP));
            float[] xs = new float[n], zs = new float[n], fl = new float[n];
            double floor = ventY;
            double wig = Hash.unit(Hash.mix(v.id() + 77L * k)) * 10;
            for (int i = 0; i < n; i++) {
                double r = i * STEP;
                double ang = a + 0.18 * Math.sin(r / 23.0 + wig);
                double x = v.x() + Math.cos(ang) * r, z = v.z() + Math.sin(ang) * r;
                double nat = ventY;
                if (i > 0) {
                    sampler.sampleBase(x, z, c);
                    nat = c.height;
                }
                double target = Math.min(floor - (i == 0 ? 0 : 0.1), nat - (i == 0 ? 0 : 1.5));
                floor = target;
                xs[i] = (float) x;
                zs[i] = (float) z;
                fl[i] = (float) Math.floor(floor);
            }
            chans[k] = new Channel(xs, zs, fl);
        }
        return new Geometry(v, vx, ventY, vz, chans);
    }

    /**
     * Applies grooves and basins to a column height. Returns the carved height and reports via {@code flags[0]} if
     * the column lies in a groove/basin (1) or at the vent (2).
     */
    public double apply(Geometry g, double x, double z, double h, int[] flags) {
        double out = h;
        // Levees: the lava-field basins get a low rim of solidified lava where natural ground falls away.
        for (Channel ch : g.channels()) {
            int n = ch.size();
            double be = Math.hypot(x - ch.x()[n - 1], z - ch.z()[n - 1]);
            if (be >= BASIN_R && be < BASIN_R + 2.5) {
                double rim = ch.floor()[n - 1] + 1;
                if (rim > out) { out = rim; if (flags[0] == 0) flags[0] = 3; }
            }
        }
        double vd = Math.hypot(x - g.ventX() - 0.5, z - g.ventZ() - 0.5);
        if (vd < 3.2) {
            out = Math.min(out, g.ventY());
            flags[0] = 2;
        }
        double[] tt = new double[1];
        for (Channel ch : g.channels()) {
            int n = ch.size();
            for (int i = 0; i + 1 < n; i++) {
                double d = MathUtil.distToSegment(x, z, ch.x()[i], ch.z()[i], ch.x()[i + 1], ch.z()[i + 1], tt);
                if (d > GROOVE_HALF + 3) continue;
                double fl = ch.floor()[i] + (ch.floor()[i + 1] - ch.floor()[i]) * tt[0];
                double target = d < GROOVE_HALF ? Math.floor(fl) : Math.floor(fl) + (d - GROOVE_HALF) * 1.2 + 0.5;
                if (target < out) {
                    out = target;
                    if (d < GROOVE_HALF && flags[0] == 0) flags[0] = 1;
                }
            }
            // Terminal lava-field basin: enclosed depression one block below the last groove floor.
            double ex = ch.x()[n - 1], ez = ch.z()[n - 1];
            double be = Math.hypot(x - ex, z - ez);
            if (be < BASIN_R + 3) {
                double bf = ch.floor()[n - 1] - 1;
                double target = be < BASIN_R ? bf : bf + (be - BASIN_R) * 1.3 + 1;
                if (target < out) {
                    out = target;
                    if (be < BASIN_R && flags[0] == 0) flags[0] = 1;
                }
            }
        }
        return out;
    }
}
