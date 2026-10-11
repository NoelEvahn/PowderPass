package dev.powderpass.core.geo;

import dev.powderpass.core.GenSettings;
import dev.powderpass.core.noise.Simplex;
import dev.powderpass.core.util.Hash;
import dev.powderpass.core.util.MathUtil;
import dev.powderpass.core.util.MonotoneSpline;

import static dev.powderpass.core.util.MathUtil.lerp;
import static dev.powderpass.core.util.MathUtil.smoothstep;

/**
 * Pre-erosion geology. Converts the plate field into continuous geological quantities and a base (initial)
 * elevation. Nothing here knows about water: drainage and erosion operate on this field afterwards.
 *
 * <p>Logical order: plates -> continentality -> orogeny / rifts / transforms -> provinces & rock resistance ->
 * uplift -> base elevation. All outputs are deterministic functions of (seed, settings, x, z).
 */
public final class Geology {
    private final Tectonics tect;
    private final GenSettings s;
    private final double sc;
    private final int sea;
    private final long cSeed, cSeed2, hillSeed, hillEnvSeed, mtnSeed, alongSeed, peakSeed, plateauSeed, plateauHSeed,
            provSeed, karstSeed, faultSeed, interiorSeed, detailSeed, strataSeed;
    private final MonotoneSpline baseCurve;

    public Geology(long worldSeed, GenSettings settings) {
        this.s = settings;
        this.sc = settings.horizontalScale();
        this.sea = settings.seaLevel();
        this.tect = new Tectonics(worldSeed, sc);
        cSeed = Hash.subSeed(worldSeed, "geo.cont");
        cSeed2 = Hash.subSeed(worldSeed, "geo.cont2");
        hillSeed = Hash.subSeed(worldSeed, "geo.hill");
        hillEnvSeed = Hash.subSeed(worldSeed, "geo.hillenv");
        mtnSeed = Hash.subSeed(worldSeed, "geo.mtn");
        alongSeed = Hash.subSeed(worldSeed, "geo.along");
        peakSeed = Hash.subSeed(worldSeed, "geo.peak");
        plateauSeed = Hash.subSeed(worldSeed, "geo.plateau");
        plateauHSeed = Hash.subSeed(worldSeed, "geo.plateauh");
        provSeed = Hash.subSeed(worldSeed, "geo.province");
        karstSeed = Hash.subSeed(worldSeed, "geo.karst");
        faultSeed = Hash.subSeed(worldSeed, "geo.fault");
        interiorSeed = Hash.subSeed(worldSeed, "geo.interior");
        detailSeed = Hash.subSeed(worldSeed, "geo.detail");
        strataSeed = Hash.subSeed(worldSeed, "geo.strata");
        baseCurve = new MonotoneSpline(
                new double[]{-1.0, -0.45, -0.2, -0.07, -0.015, 0.02, 0.12, 0.35, 0.7, 1.2},
                new double[]{-58, -46, -30, -12, -3, 2, 7, 16, 30, 44});
    }

    public Tectonics tectonics() {
        return tect;
    }

    /** Reusable output record (not thread-safe; use one per thread). */
    public static final class GeoSample {
        public final Tectonics.Sample plate = new Tectonics.Sample();
        public double continental;   // [-1,1] (<~0 ocean)
        public double orogeny;       // [0,1] mountain-building intensity
        public double youth;         // [0,1] 1 = young active range
        public double plateau;       // [0,1]
        public double hills;         // [0,1]
        public double rift;          // [0,1]
        public double volcanism;     // [0,1] tectonic volcanic potential (arcs, rifts)
        public double uplift;        // relative uplift rate for the erosion model
        public double resistance;    // bedrock resistance multiplier
        public Province province;
        public double faultThrow;    // vertical strata offset across faults (blocks)
        public double faultDist;     // distance to nearest fault trace (blocks)
        public double faultScarp;    // elevation contribution of fault scarps (blocks)
        public double strataOffset;  // tilt/fold offset applied to strata (blocks)
        public double signedBoundaryDist;
        public double h0;            // base elevation, absolute block Y (pre-erosion, envelope applied)
        // Overlay results (anomalies, volcanic edifices) written by BaseTerrain:
        public double fixedLevel = Double.NaN; // if set, erosion must keep this cell at exactly this level
        public int overlay;                    // overlay kind id (0 none)
        public double overlayStrength;         // [0,1] blend of the overlay at this point
        public boolean sea;                    // marine (connected-ocean) cell by continentality
        public double sinkLevel = Double.NaN;  // intentional terminal sink (flat floors, craters, pits)
        public double sinkWater = Double.NaN;  // standing water level for wet sinks (NaN = dry)
        public double resistanceOverride = Double.NaN;

        public void resetOverlay() {
            fixedLevel = Double.NaN;
            overlay = 0;
            overlayStrength = 0;
            sinkLevel = Double.NaN;
            sinkWater = Double.NaN;
            resistanceOverride = Double.NaN;
        }
    }

    public void sample(double x, double z, GeoSample o) {
        sampleInternal(x, z, o, true);
    }

    /** Cheaper large-scale elevation proxy (no small detail), used by climate and coarse graph statistics. */
    public double proxyHeight(double x, double z, GeoSample o) {
        sampleInternal(x, z, o, false);
        return o.h0;
    }

    private void sampleInternal(double x, double z, GeoSample o, boolean full) {
        Tectonics.Sample ts = o.plate;
        double mf = s.mountainFrequency();
        double widthScale = sc * (0.6 + 0.4 * mf);
        tect.sample(x, z, 2400 * widthScale + 200, ts);

        // ---- continentality ----
        double C = ts.crust * 0.62
                + 0.40 * Simplex.fbm(cSeed, x / (3800 * sc), z / (3800 * sc), 4, 2.05, 0.5)
                + (full ? 0.10 * Simplex.fbm(cSeed2, x / (700 * sc), z / (700 * sc), 3, 2.1, 0.5) : 0)
                - 0.07;
        o.continental = C;

        // ---- boundary kinematics -> orogeny, rifts, transforms, volcanic belts (aggregated over edges) ----
        double notO = 1, youthW = 0, youthSum = 0, notRift = 1, notVolc = 1;
        double riftAdj = 0, trAdj = 0, morAdj = 0, strataOff = 0;
        double alongPhase = Simplex.fbm(alongSeed + 5, x / (1400 * sc), z / (1400 * sc), 2, 2.0, 0.5);
        for (int i = 0; i < ts.edgeCount; i++) {
            Tectonics.Edge e = ts.edges[i];
            double d = ts.edgeDist[i], sgn = ts.edgeSigned[i];
            double conv = e.convergence;
            boolean contLo = e.crustLo > 0, contHi = e.crustHi > 0;
            double width = (700 + 1100 * e.pairRand) * widthScale * (0.7 + 0.6 * (0.5 + 0.5 * alongPhase));
            double cs = smoothstep(0.02, 0.30, conv);
            double oi = 0, volc = 0;
            if (cs > 0) {
                if (contLo && contHi) {
                    oi = cs * profile(d, width);
                } else if (contLo || contHi) {
                    double toCont = contHi ? sgn : -sgn; // positive on the continental side
                    oi = cs * profile(Math.abs(toCont - 0.45 * width), width * 0.75);
                    volc = cs * profile(Math.abs(toCont - 0.8 * width), 500 * sc);
                } else {
                    oi = cs * 0.35 * profile(d, width * 0.5);
                    volc = cs * profile(Math.abs(d - 0.6 * width), 450 * sc);
                }
            }
            if (conv < -0.12) {
                double dv = smoothstep(0.12, 0.5, -conv);
                volc = Math.max(volc, smoothstep(0.12, 0.6, -conv) * profile(d, 900 * sc));
                if (contLo && contHi) {
                    double r = dv * profile(d, 520 * sc);
                    notRift *= 1 - r;
                    riftAdj += dv * (-55 * profile(d, 260 * sc) + 40 * profile(Math.abs(d - 480 * sc), 220 * sc));
                } else if (!contLo && !contHi) {
                    morAdj += dv * profile(d, 1200 * sc) * 22;
                }
            }
            double tr = MathUtil.clamp(e.shear * (1 - Math.abs(conv) * 2.0), 0, 1) * (1 - smoothstep(60 * sc, 260 * sc, d));
            trAdj += tr * (-22 * profile(d, 70 * sc) + 12 * profile(Math.abs(d - 140 * sc), 70 * sc));
            notO *= 1 - oi;
            notVolc *= 1 - volc;
            youthW += oi;
            youthSum += oi * (1 - e.age);
            double fold = Math.sin(d / (90 * sc) + alongPhase * 2.0) * 14;
            strataOff += oi * (0.18 * sgn + fold);
        }
        double oBoundary = (1 - notO) * Math.min(1.0, mf);
        double youth = youthW > 1e-6 ? youthSum / youthW : 0.5;

        // Intraplate (old) ranges: rare ridged belts far from boundaries.
        double interior = Simplex.ridged(interiorSeed, x / (5200 * sc), z / (5200 * sc), 2, 2.0, 0.5);
        double oInterior = smoothstep(0.80 - 0.10 * (mf - 1), 0.97, interior) * 0.7 * Math.min(1.0, mf);
        double orogeny = MathUtil.clamp(1 - (1 - oBoundary) * (1 - oInterior), 0, 1);
        if (orogeny > 1e-6) youth = (youth * oBoundary + 0.15 * oInterior) / (oBoundary + oInterior);
        o.orogeny = orogeny;
        o.youth = youth;
        double rift = 1 - notRift;
        o.rift = rift;
        double volc = 1 - notVolc;
        o.volcanism = volc;
        o.strataOffset = strataOff;
        o.signedBoundaryDist = ts.nearestDist;

        // ---- heights ----
        double base = sea + baseCurve.eval(C);
        double landF = smoothstep(-0.03, 0.07, C);
        double mtnLandF = smoothstep(-0.35, 0.02, C);

        // Rolling hills with their own envelope; flatlandBias moves the threshold.
        double fb = s.flatlandBias() * 0.35;
        double hillEnv = smoothstep(-0.20 + fb, 0.38 + fb, Simplex.fbm(hillEnvSeed, x / (2300 * sc), z / (2300 * sc), 3, 2.0, 0.5));
        hillEnv = Math.max(hillEnv, smoothstep(0.02, 0.35, orogeny) * 0.8); // foothills
        double hillAmp = 16 + 52 * (0.5 + 0.5 * Simplex.fbm(hillSeed + 3, x / (1700 * sc), z / (1700 * sc), 2, 2.0, 0.5));
        double hillShape = full
                ? 0.5 + 0.5 * Simplex.fbm(hillSeed, x / (330 * sc), z / (330 * sc), 4, 2.0, 0.5)
                : 0.5;
        double hillH = hillEnv * landF * hillAmp * hillShape;
        o.hills = hillEnv * landF;

        // Mountains: spine profile x along-strike modulation (saddles, passes, uneven peaks) x ridged texture.
        double along = Simplex.fbm(alongSeed, x / (2100 * sc), z / (2100 * sc), 3, 2.0, 0.5);
        double peak = Simplex.fbm(peakSeed, x / (2600 * sc), z / (2600 * sc), 2, 2.0, 0.5);
        double landmark = smoothstep(0.38, 0.62, peak) * 140;
        double mtnAmp = (220 + 520 * youth) * (0.55 + 0.45 * along) + landmark * youth;
        double ridged;
        if (full) {
            double ridgeYoung = Simplex.ridged(mtnSeed, x / (950 * sc), z / (950 * sc), 5, 2.05, 0.5);
            double ridgeOld = 0.5 + 0.5 * Simplex.fbm(mtnSeed + 9, x / (1100 * sc), z / (1100 * sc), 4, 2.0, 0.45);
            ridged = lerp(youth, ridgeOld, ridgeYoung);
        } else {
            ridged = 0.5;
        }
        double mShape = Math.pow(orogeny, 1.15) * (0.42 + 0.68 * ridged);
        double mtnH = mtnLandF * mtnAmp * mShape;

        // Plateaus / tablelands with escarpment edges, mostly in stable sedimentary regions.
        double plN = Simplex.fbm(plateauSeed, x / (7000 * sc), z / (7000 * sc), 2, 2.0, 0.4)
                + 0.07 * Simplex.fbm(plateauSeed + 3, x / (900 * sc), z / (900 * sc), 2, 2.0, 0.5)
                + (full ? 0.025 * Simplex.noise2(plateauSeed + 4, x / (160 * sc), z / (160 * sc)) : 0);
        double plEnv = smoothstep(0.24, 0.27, plN) * (1 - smoothstep(0.05, 0.3, orogeny)) * landF;
        double plH = plEnv * (48 + 90 * (0.5 + 0.5 * Simplex.fbm(plateauHSeed, x / (5000 * sc), z / (5000 * sc), 2, 2.0, 0.5)));
        o.plateau = plEnv;

        // ---- faults: coherent fault blocks with strata throw and occasional scarps ----
        faultField(x, z, o, full);
        double scarp = o.faultScarp * landF * (1 - 0.6 * orogeny);

        double detail = 0;
        if (full) {
            detail = Simplex.fbm(detailSeed, x / (120 * sc), z / (120 * sc), 3, 2.0, 0.5) * (2.0 + 6 * orogeny + 2 * hillEnv) * landF;
        }

        double h = base + hillH + mtnH + plH + (riftAdj + trAdj) * landF + morAdj * (1 - landF) + scarp + detail;
        o.h0 = envelope(h);

        // ---- provinces & resistance ----
        Province p;
        if (C < -0.04 && mtnH < 20) p = Province.OCEAN_BASIN;
        else if (orogeny > 0.32) p = youth > 0.5 ? Province.YOUNG_OROGEN : Province.OLD_OROGEN;
        else if (volc > 0.45) p = Province.VOLCANIC;
        else if (rift > 0.35) p = Province.RIFT;
        else if (plEnv > 0.5) p = Province.PLATEAU;
        else if (C < 0.07) p = Province.COASTAL_LOWLAND;
        else {
            double pv = Simplex.fbm(provSeed, x / (4200 * sc), z / (4200 * sc), 2, 2.0, 0.5);
            double kv = Simplex.fbm(karstSeed, x / (3000 * sc), z / (3000 * sc), 2, 2.0, 0.5);
            if (pv > 0.30) p = Province.SHIELD;
            else if (kv > 0.38) p = Province.KARST;
            else p = Province.SEDIMENTARY_BASIN;
        }
        o.province = p;
        o.resistance = p.resistance;
        o.uplift = orogeny * youth;
    }

    private void faultField(double x, double z, GeoSample o, boolean full) {
        double cell = 1900 * sc;
        double wx = x + 160 * sc * Simplex.noise2(faultSeed + 1, x / (2400 * sc), z / (2400 * sc));
        double wz = z + 160 * sc * Simplex.noise2(faultSeed + 2, x / (2400 * sc), z / (2400 * sc));
        int cx = MathUtil.fastFloor(wx / cell), cz = MathUtil.fastFloor(wz / cell);
        double b1 = Double.MAX_VALUE, b2 = Double.MAX_VALUE, b3 = Double.MAX_VALUE;
        int g1x = 0, g1z = 0, g2x = 0, g2z = 0;
        double p1x = 0, p1z = 0, p2x = 0, p2z = 0;
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                int gx = cx + dx, gz = cz + dz;
                double sx = (gx + 0.5 + 0.7 * (Hash.unit(faultSeed, gx, gz, 1) - 0.5)) * cell;
                double sz = (gz + 0.5 + 0.7 * (Hash.unit(faultSeed, gx, gz, 2) - 0.5)) * cell;
                double dd = (sx - wx) * (sx - wx) + (sz - wz) * (sz - wz);
                if (dd < b1) {
                    b3 = b2;
                    b2 = b1; g2x = g1x; g2z = g1z; p2x = p1x; p2z = p1z;
                    b1 = dd; g1x = gx; g1z = gz; p1x = sx; p1z = sz;
                } else if (dd < b2) {
                    b3 = b2;
                    b2 = dd; g2x = gx; g2z = gz; p2x = sx; p2z = sz;
                } else if (dd < b3) {
                    b3 = dd;
                }
            }
        }
        double sep = Math.hypot(p2x - p1x, p2z - p1z);
        double ds = (b2 - b1) / (2 * sep);
        o.faultDist = ds;
        // Strata throw: fault blocks are offset rigidly (sharp step across the trace).
        double t1 = throwOf(g1x, g1z), t2 = throwOf(g2x, g2z);
        double w = smoothstep(-1.5, 1.5, ds);
        o.faultThrow = lerp(0.5 + 0.5 * w, t2, t1);
        // Surface scarps only on selected fault traces: a step across the trace that decays away from it and tapers
        // to zero toward fault junctions, so scarps read as long linear features rather than raised polygons.
        long k1 = Hash.pack(g1x, g1z), k2 = Hash.pack(g2x, g2z);
        long lo = Math.min(k1, k2), hi = Math.max(k1, k2);
        long ph = Hash.hash(faultSeed ^ 0xFA17L, lo, hi);
        double amp = 0;
        if (Hash.unit(ph) < 0.08) {
            amp = (8 + 22 * Hash.unit(Hash.mix(ph))) * (Hash.unit(Hash.mix(ph + 1)) < 0.5 ? -1 : 1);
        }
        if (amp != 0) {
            double signedLo = k1 == lo ? ds : -ds; // positive on the lo-block side; identical from both sides
            double sw = (6 + 18 * Hash.unit(Hash.mix(ph + 2))) * sc;
            double junction = (b3 - b1) / (2 * cell);          // distance-like gap to the third block
            double taper = smoothstep(0, 260 * sc, junction - ds) * smoothstep(0, 260 * sc, junction);
            o.faultScarp = amp * (smoothstep(-sw, sw, signedLo) - 0.5) * Math.exp(-Math.abs(ds) / (520 * sc)) * taper;
        } else {
            o.faultScarp = 0;
        }
    }

    private double throwOf(int gx, int gz) {
        return (Hash.unit(faultSeed, gx, gz, 3) * 2 - 1) * 14;
    }

    /** Smooth bell profile: 1 at 0, 0 at >= w. */
    private static double profile(double d, double w) {
        double t = MathUtil.clamp(d / w, 0, 1);
        double u = 1 - t * t;
        return u * u;
    }

    /**
     * Soft elevation envelope: heights pass unchanged up to the knee, then compress asymptotically toward the
     * ceiling so tall ranges keep their shape instead of being sliced flat. Ceiling leaves headroom below the
     * hard cap for snow and vegetation.
     */
    public double envelope(double h) {
        double ceiling = s.terrainCap() - 14;
        double knee = ceiling - 170;
        if (h <= knee) return h;
        double span = ceiling - knee;
        return knee + span * Math.tanh((h - knee) / span);
    }

    /** Strata layer coordinate at absolute block position, including tilt, folds and fault throw. */
    public double strataCoord(double y, GeoSample o, double x, double z) {
        double warp = 3.5 * Simplex.noise2(strataSeed, x / (180 * sc), z / (180 * sc));
        return y + o.strataOffset + o.faultThrow + warp;
    }

    public long strataSeed() {
        return strataSeed;
    }

    /**
     * Resistance multiplier of the rock layer exposed at elevation y: alternating hard/soft beds produce benches,
     * cliff bands and caprocks as erosion cuts down through them.
     */
    public double layerResistance(double strataCoord, Province p) {
        double thick = p == Province.PLATEAU ? 22 : (p == Province.SEDIMENTARY_BASIN || p == Province.KARST ? 14 : 30);
        int layer = MathUtil.fastFloor(strataCoord / thick);
        double r = Hash.unit(Hash.hash(strataSeed, layer, p.ordinal()));
        if (p == Province.PLATEAU) return r < 0.4 ? 2.4 : 0.7;
        return 0.7 + 0.9 * r;
    }
}
