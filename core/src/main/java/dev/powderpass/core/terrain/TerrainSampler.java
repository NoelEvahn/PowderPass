package dev.powderpass.core.terrain;

import dev.powderpass.core.GenSettings;
import dev.powderpass.core.PowderWorld;
import dev.powderpass.core.geo.Geology;
import dev.powderpass.core.hydro.DomainSim;
import dev.powderpass.core.hydro.Domains;
import dev.powderpass.core.hydro.HydroRules;
import dev.powderpass.core.hydro.RiverNet;
import dev.powderpass.core.noise.Simplex;
import dev.powderpass.core.util.Hash;
import dev.powderpass.core.util.MathUtil;

import static dev.powderpass.core.util.MathUtil.smoothstep;

/**
 * Final per-column terrain reconstruction at block resolution.
 *
 * <p>Order: owning domain -> bicubic reconstruction of the eroded 8-block grid -> blend to the canonical boundary
 * height inside {@link HydroRules#BLEND_W} of a domain border (exactly equal on both sides at the border) ->
 * canonical pass notches -> pinned overlay levels (perfectly flat cores) -> channel carving from the river network
 * -> lakes/sea from the simulated water grid -> small detail -> elevation envelope. Every step is a pure function
 * of (x, z), so adjacent chunks and adjacent domains agree.
 */
public final class TerrainSampler {
    public static final byte WATER_NONE = 0, WATER_SEA = 1, WATER_LAKE = 2, WATER_RIVER = 3;

    private final PowderWorld world;
    private final GenSettings s;
    private final int sea;
    private final long detailSeed, barSeed;
    private final ThreadLocal<Scratch> tl = ThreadLocal.withInitial(Scratch::new);

    private static final class Scratch {
        final Geology.GeoSample geo = new Geology.GeoSample();
        final Domains.Owner owner = new Domains.Owner();
        long lastId = Long.MIN_VALUE;
        DomainSim lastSim;
    }

    public TerrainSampler(PowderWorld world) {
        this.world = world;
        this.s = world.settings;
        this.sea = s.seaLevel();
        this.detailSeed = Hash.subSeed(world.seed, "terrain.detail");
        this.barSeed = Hash.subSeed(world.seed, "terrain.bars");
    }

    public PowderWorld world() {
        return world;
    }

    /** Everything known about one column. Reusable. */
    public static final class Column {
        public double x, z;
        public double height;          // continuous terrain surface (top solid block = floor(height))
        public double waterLevel;      // water surface (blocks y < waterLevel are water) or NaN
        public byte waterKind;
        public double sediment;        // alluvium/talus thickness from the simulation
        public double erosionDepth;    // z0 - z (positive = eroded, negative = deposited)
        public double drainage;        // precipitation-weighted drainage area
        public double ice;             // past glacier flux
        public double riverDist = Double.MAX_VALUE, riverWidth, riverQ, riverGrad;
        public int riverFlags;
        public boolean channel;        // inside a river channel
        public long domain;
        public double boundaryDist;
        public final Geology.GeoSample geo = new Geology.GeoSample(); // base geology/overlays at this column
        public double precip, baseTemp;
        public double gridSlope;       // slope at the 8-block simulation scale (rise/run)
    }

    public void sample(double x, double z, Column c) {
        Scratch sc = tl.get();
        c.x = x;
        c.z = z;
        Domains dom = world.domains;
        dom.owner(x, z, sc.owner);
        c.domain = sc.owner.id;
        c.boundaryDist = sc.owner.bd;
        DomainSim sim;
        if (sc.lastId == sc.owner.id && sc.lastSim != null) {
            sim = sc.lastSim;
        } else {
            sim = world.sim(sc.owner.id);
            sc.lastId = sc.owner.id;
            sc.lastSim = sim;
        }
        Geology.GeoSample o = c.geo;
        world.base.sample(x, z, o);
        c.precip = world.climate.precipitation(x, z);
        c.baseTemp = world.climate.baseTemperatureInterp(x, z);

        double gx = (x - sim.ox) / DomainSim.RES, gz = (z - sim.oz) / DomainSim.RES;
        double h = bicubic(sim.z, sim.w, sim.h, gx, gz);
        double h0i = bicubic(sim.z0, sim.w, sim.h, gx, gz);
        c.erosionDepth = h0i - h;
        c.sediment = Math.max(0, bilinear(sim.sed, sim.w, sim.h, gx, gz));
        c.drainage = bilinear(sim.area, sim.w, sim.h, gx, gz);
        c.ice = bilinear(sim.ice, sim.w, sim.h, gx, gz);
        // Slope over a 32-block stencil: smooth enough that biome boundaries do not follow individual rills.
        double sx = bilinear(sim.z, sim.w, sim.h, gx + 2, gz) - bilinear(sim.z, sim.w, sim.h, gx - 2, gz);
        double sz = bilinear(sim.z, sim.w, sim.h, gx, gz + 2) - bilinear(sim.z, sim.w, sim.h, gx, gz - 2);
        c.gridSlope = Math.sqrt(sx * sx + sz * sz) / (4.0 * DomainSim.RES);

        // Seam blend toward the canonical boundary height.
        double bd = sc.owner.bd;
        if (bd < HydroRules.BLEND_W) {
            double hb = HydroRules.boundaryHeight(o, s);
            double t = smoothstep(0, HydroRules.BLEND_W, bd);
            h = hb + (h - hb) * t;
        }
        // Canonical pass notches and pass channels (identical on both sides of every border).
        final double hh = h;
        double[] hm = {hh, Double.NaN};
        dom.forEachPassNear(x, z, HydroRules.NOTCH_R, (f, d) -> {
            hm[0] = Math.min(hm[0], HydroRules.notch(d, f.level));
            // Straight canonical channel through the pass along the seed-to-seed direction.
            Domains.Pass p = f.outPass;
            double ux = x - p.x, uz = z - p.z;
            double along = ux * p.tx + uz * p.tz;
            double across = Math.abs(-ux * p.tz + uz * p.tx);
            double halfLen = HydroRules.PASS_CHANNEL_HALF;
            if (Math.abs(along) > halfLen + 8) return;
            double q = dom.outflowDischarge(f.id);
            double halfW = HydroRules.width(q) * 0.5;
            double endFade = 1 - smoothstep(halfLen, halfLen + 8, Math.abs(along));
            double target;
            if (across < halfW) {
                double t = across / halfW;
                target = f.level - 1 - HydroRules.depth(q) * Math.pow(1 - t * t, 0.6);
                if (Math.abs(along) <= halfLen) hm[1] = Double.isNaN(hm[1]) ? f.level : Math.min(hm[1], f.level);
            } else {
                target = f.level + 0.6 + (across - halfW) * 0.5;
            }
            double carved = hm[0] + (Math.min(hm[0], target) - hm[0]) * endFade;
            hm[0] = Math.min(hm[0], carved);
        });
        h = hm[0];
        double passWater = hm[1];
        boolean pinned = !Double.isNaN(o.fixedLevel);
        if (pinned) h = o.fixedLevel;

        // Lakes and sea from the simulated water grid (nearest wet cell among the four around the column).
        c.waterLevel = Double.NaN;
        c.waterKind = WATER_NONE;
        lakeWater(sim, gx, gz, c);

        // River channels: continuous minimum over every nearby segment's carve profile.
        c.channel = false;
        c.riverDist = Double.MAX_VALUE;
        c.riverFlags = 0;
        if (!pinned) {
            double carveFade = smoothstep(0, HydroRules.BLEND_W, bd);
            h = carveAll(sim, h, carveFade, x, z, c);
        }

        if (!Double.isNaN(passWater) && Math.floor(h) < passWater - 1) {
            c.channel = true;
            if (Double.isNaN(c.waterLevel)) {
                c.waterLevel = passWater;
                c.waterKind = WATER_RIVER;
            } else {
                c.waterLevel = Math.min(c.waterLevel, passWater);
            }
        }

        // Small-scale roughness, damped near water, on flats and in channels.
        if (!pinned) {
            double ownDamp = c.riverDist < Double.MAX_VALUE ? smoothstep(0, 24, c.riverDist - c.riverWidth * 0.5) : 1;
            double wDamp = 1 - (1 - ownDamp) * smoothstep(0, HydroRules.BLEND_W, bd);
            if (!Double.isNaN(c.waterLevel) || !Double.isNaN(passWater)) wDamp = 0;
            double rough = 0.6 + 2.2 * o.orogeny + 0.8 * o.hills;
            double fixedFade = o.overlay >= 100 ? 1 - o.overlayStrength : 1;
            h += Simplex.fbm(detailSeed, x / 23.0, z / 23.0, 2, 2.0, 0.5) * rough * wDamp * fixedFade;
        }
        // Elevation envelope and hard cap.
        h = world.geology.envelope(h);
        h = Math.min(h, s.terrainCap() - 6);
        h = Math.max(h, GenSettings.MIN_Y + 6);
        c.height = h;
        if (!Double.isNaN(c.waterLevel) && c.waterLevel <= Math.floor(h) + 1) {
            // No room for water above the floor.
            c.waterLevel = Double.NaN;
            c.waterKind = WATER_NONE;
        }
    }

    private static final double CARVE_REACH = 36;

    private double carveAll(DomainSim sim, double h0, double fade, double x, double z, Column c) {
        final double[] st = {h0, Double.NaN}; // carved minimum, channel water surface
        final double hIn = h0;
        sim.rivers.forEachNear(x, z, CARVE_REACH, (d, surf, q, width, depth, grad, flags) -> {
            double halfW = width * 0.5;
            if (d < c.riverDist) {
                c.riverDist = d;
                c.riverWidth = width;
                c.riverQ = q;
                c.riverGrad = grad;
                c.riverFlags = flags;
            }
            if ((flags & RiverNet.FLAG_LAKE) != 0) return; // lake water covers the route
            double target;
            double e = d - halfW;
            boolean inChannel = e < 0;
            double bed = 0;
            if (inChannel) {
                double t = d / halfW;
                bed = surf - 1 - depth * Math.pow(1 - t * t, 0.6);
                // Braided reaches: exposed gravel bars inside wide, moderately steep, sediment-rich channels.
                if (width > 9 && grad > 0.0015 && grad < 0.012 && c.sediment > 0.6 && t < 0.85
                        && Simplex.noise2(barSeed, x / 14.0, z / 14.0) > 0.45) {
                    bed = surf;
                }
                target = bed;
            } else {
                // Banks and floodplain: wide gentle floor on low-gradient rivers, steeper on small steep streams.
                double fp = grad < 0.004 ? Math.min(30, width * 2.0) : Math.min(8, width);
                double bankSlope = grad < 0.004 ? 0.3 : 0.85;
                target = surf + 0.6 + (e < fp ? e * 0.06 : fp * 0.06 + (e - fp) * bankSlope);
            }
            double reachFade = 1 - smoothstep(CARVE_REACH * 0.6, CARVE_REACH, e);
            double carved = hIn + (Math.min(hIn, target) - hIn) * fade * reachFade;
            if (carved < st[0]) st[0] = carved;
            if (inChannel && bed < surf - 0.5 && fade > 0.5) {
                st[1] = Double.isNaN(st[1]) ? surf : Math.min(st[1], surf);
            }
        });
        double h = st[0];
        if (!Double.isNaN(st[1]) && Math.floor(h) < st[1] - 1) {
            c.channel = true;
            if (Double.isNaN(c.waterLevel)) {
                c.waterLevel = st[1];
                c.waterKind = WATER_RIVER;
            } else {
                c.waterLevel = Math.min(c.waterLevel, st[1]);
            }
        }
        return h;
    }

    private void lakeWater(DomainSim sim, double gx, double gz, Column c) {
        int i0 = MathUtil.fastFloor(gx), j0 = MathUtil.fastFloor(gz);
        double best = Double.MAX_VALUE;
        float lv = Float.NaN;
        byte kind = 0;
        double wetW = 0;
        for (int dj = 0; dj <= 1; dj++) {
            for (int di = 0; di <= 1; di++) {
                int i = MathUtil.clamp(i0 + di, 0, sim.w - 1), j = MathUtil.clamp(j0 + dj, 0, sim.h - 1);
                int k = j * sim.w + i;
                double wx = 1 - Math.abs(gx - (i0 + di)), wz = 1 - Math.abs(gz - (j0 + dj));
                double wgt = Math.max(0, wx) * Math.max(0, wz);
                float wv = sim.water[k];
                if (Float.isNaN(wv)) continue;
                wetW += wgt;
                double d = MathUtil.sq(gx - (i0 + di)) + MathUtil.sq(gz - (j0 + dj));
                if (d < best) {
                    best = d;
                    lv = wv;
                    kind = sim.kind[k] == DomainSim.SEA ? WATER_SEA : WATER_LAKE;
                }
            }
        }
        if (wetW >= 0.25 && !Float.isNaN(lv)) {
            c.waterLevel = Math.floor(lv + 1e-3);
            if (kind == WATER_LAKE && Math.abs(lv - sea) < 0.01) kind = WATER_SEA;
            c.waterKind = kind;
        }
    }

    static double bicubic(float[] a, int w, int h, double gx, double gz) {
        int ix = MathUtil.fastFloor(gx), iz = MathUtil.fastFloor(gz);
        double fx = gx - ix, fz = gz - iz;
        double[] col = new double[4];
        for (int m = -1; m <= 2; m++) {
            int j = MathUtil.clamp(iz + m, 0, h - 1);
            int base = j * w;
            double p0 = a[base + MathUtil.clamp(ix - 1, 0, w - 1)], p1 = a[base + MathUtil.clamp(ix, 0, w - 1)];
            double p2 = a[base + MathUtil.clamp(ix + 1, 0, w - 1)], p3 = a[base + MathUtil.clamp(ix + 2, 0, w - 1)];
            col[m + 1] = MathUtil.cubic(p0, p1, p2, p3, fx);
        }
        double v = MathUtil.cubic(col[0], col[1], col[2], col[3], fz);
        // Clamp overshoot to the local 2x2 range (prevents ringing spikes at cliffs).
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (int dj = 0; dj <= 1; dj++) for (int di = 0; di <= 1; di++) {
            double q = a[MathUtil.clamp(iz + dj, 0, h - 1) * w + MathUtil.clamp(ix + di, 0, w - 1)];
            lo = Math.min(lo, q);
            hi = Math.max(hi, q);
        }
        double pad = (hi - lo) * 0.15 + 0.5;
        return MathUtil.clamp(v, lo - pad, hi + pad);
    }

    static double bilinear(float[] a, int w, int h, double gx, double gz) {
        int ix = MathUtil.fastFloor(gx), iz = MathUtil.fastFloor(gz);
        double fx = gx - ix, fz = gz - iz;
        int x0 = MathUtil.clamp(ix, 0, w - 1), x1 = MathUtil.clamp(ix + 1, 0, w - 1);
        int z0 = MathUtil.clamp(iz, 0, h - 1), z1 = MathUtil.clamp(iz + 1, 0, h - 1);
        double v00 = a[z0 * w + x0], v10 = a[z0 * w + x1], v01 = a[z1 * w + x0], v11 = a[z1 * w + x1];
        return MathUtil.lerp(fz, MathUtil.lerp(fx, v00, v10), MathUtil.lerp(fx, v01, v11));
    }
}
