package dev.powderpass.core.anomaly;

import dev.powderpass.core.GenSettings;
import dev.powderpass.core.climate.Climate;
import dev.powderpass.core.geo.Geology;
import dev.powderpass.core.geo.Province;
import dev.powderpass.core.noise.Simplex;
import dev.powderpass.core.util.BoundedCache;
import dev.powderpass.core.util.Hash;
import dev.powderpass.core.util.MathUtil;
import dev.powderpass.core.volcano.Volcanoes;

import java.util.ArrayList;
import java.util.List;

import static dev.powderpass.core.util.MathUtil.lerp;
import static dev.powderpass.core.util.MathUtil.smoothstep;
import static dev.powderpass.core.util.MathUtil.smootherstep;

/**
 * Rare uncanny landforms placed on a coarse jittered lattice. At most one anomaly per cell; each candidate is
 * validated against geology, climate and nearby volcanoes, so placement is deterministic metadata that locate
 * commands can enumerate without generating terrain. Overlays modify the pre-erosion surface so drainage and erosion
 * respond to them (craters collect water, flats become terminal sinks); flat cores are pinned so erosion cannot
 * disturb their perfectly level surfaces.
 */
public final class Anomalies {
    public static final double CELL = 4200;
    public static final int OVERLAY_BASE = 100;

    private final Geology geo;
    private final Climate climate;
    private final Volcanoes volcanoes;
    private final GenSettings s;
    private final long seed, noiseSeed;
    private final BoundedCache<Anomaly[]> cache = BoundedCache.ofCount(4096);
    private final ThreadLocal<Geology.GeoSample> tl = ThreadLocal.withInitial(Geology.GeoSample::new);

    public Anomalies(long worldSeed, GenSettings settings, Geology geo, Climate climate, Volcanoes volcanoes) {
        this.geo = geo;
        this.climate = climate;
        this.volcanoes = volcanoes;
        this.s = settings;
        this.seed = Hash.subSeed(worldSeed, "anomaly.sites");
        this.noiseSeed = Hash.subSeed(worldSeed, "anomaly.noise");
    }

    public Anomaly[] cell(int cx, int cz) {
        return cache.getOrCompute(Hash.pack(cx, cz), k -> compute(cx, cz));
    }

    private static final Anomaly.Type[] ORDER = Anomaly.Type.values();
    private static final double[] WEIGHTS = {0.17, 0.22, 0.12, 0.14, 0.10, 0.17, 0.08};

    private Anomaly[] compute(int cx, int cz) {
        double f = s.anomalyFrequency();
        if (f <= 0) return new Anomaly[0];
        if (Hash.unit(seed, cx, cz, 0) > Math.min(0.95, 0.5 * f)) return new Anomaly[0];
        double x = (cx + 0.2 + 0.6 * Hash.unit(seed, cx, cz, 1)) * CELL;
        double z = (cz + 0.2 + 0.6 * Hash.unit(seed, cx, cz, 2)) * CELL;
        double r = Hash.unit(seed, cx, cz, 3);
        int first = 0;
        double acc = 0;
        for (int i = 0; i < WEIGHTS.length; i++) {
            acc += WEIGHTS[i];
            if (r <= acc) { first = i; break; }
            first = i;
        }
        long id = Hash.hash(seed, cx, cz);
        // Try the chosen type, then fall back to the more flexible types.
        int[] tries = {first, 5, 1};
        for (int t : tries) {
            Anomaly a = tryMake(id, ORDER[t], x, z);
            if (a != null) return new Anomaly[]{a};
        }
        return new Anomaly[0];
    }

    private Anomaly tryMake(long id, Anomaly.Type type, double x, double z) {
        Geology.GeoSample o = tl.get();
        double hc = geo.proxyHeight(x, z, o);
        double orog = o.orogeny, cont = o.continental;
        Province prov = o.province;
        int sea = s.seaLevel();
        if (cont < 0.06 || hc < sea + 3) return null;
        double u1 = Hash.unit(Hash.mix(id ^ 21)), u2 = Hash.unit(Hash.mix(id ^ 22)), u3 = Hash.unit(Hash.mix(id ^ 23));
        double precip = climate.precipitation(x, z);
        Anomaly a;
        switch (type) {
            case SALT_FLAT -> {
                if (orog > 0.12 || precip > 0.75) return null;
                double R = 260 + 420 * u1;
                double lo = ringMin(x, z, R * 1.25, o);
                double hi = ringMax(x, z, R * 1.25, o);
                if (hi - lo > 70) return null;
                double level = Math.floor(Math.min(lo, hc) - 3);
                if (level < sea + 2) level = sea + 2;
                a = new Anomaly(id, type, x, z, R, 0, level, u2 * Math.PI * 2, false);
            }
            case IMPACT_CRATER -> {
                if (orog > 0.45) return null;
                double R = 110 + 380 * u1 * u1;
                double ref = Math.min(hc, ringMin(x, z, R, o) + 10);
                double depth = Math.min(0.32 * R, ref - (-40));
                a = new Anomaly(id, type, x, z, R, depth, ref, u2 * Math.PI * 2, u3 < 0.45);
            }
            case DEEP_PIT -> {
                if (orog > 0.3) return null;
                double R = 45 + 90 * u1;
                double floor = Math.max(-40, hc - (140 + 180 * u2));
                a = new Anomaly(id, type, x, z, R, hc - floor, Math.floor(floor), u3 * Math.PI * 2,
                        prov == Province.KARST && u3 < 0.6);
            }
            case FAULT_ESCARPMENT -> {
                if (orog > 0.25) return null;
                double L = 700 + 1100 * u1;
                double step = 22 + 45 * u2;
                if (hc + step > s.terrainCap() - 60) return null;
                a = new Anomaly(id, type, x, z, L, step, Double.NaN, u3 * Math.PI, false);
            }
            case CIRCULAR_BASIN -> {
                if (orog > 0.3) return null;
                double R = 380 + 480 * u1;
                a = new Anomaly(id, type, x, z, R, 26 + 36 * u2, hc, u3, false);
            }
            case MONOLITH -> {
                if (orog > 0.35) return null;
                double R = 26 + 46 * u1;
                double H = 55 + 110 * u2;
                if (hc + H > s.terrainCap() - 40) H = s.terrainCap() - 40 - hc;
                if (H < 30) return null;
                a = new Anomaly(id, type, x, z, R, H, Math.floor(hc), u3 * Math.PI * 2, false);
            }
            case BARREN_FLAT -> {
                if (orog > 0.1 || precip > 0.55) return null;
                double R = 750 + 600 * u1;
                double lo = ringMin(x, z, R, o), hi = ringMax(x, z, R, o);
                if (hi - lo > 90) return null;
                a = new Anomaly(id, type, x, z, R, 0, Math.floor((lo + hc) * 0.5), u2, false);
            }
            default -> { return null; }
        }
        // Keep clear of volcanoes.
        boolean[] clash = {false};
        double fp = a.footprint();
        volcanoes.forEachNear(x, z, v -> {
            if (Math.hypot(v.x() - x, v.z() - z) < fp + v.footprint() + 100) clash[0] = true;
        });
        return clash[0] ? null : a;
    }

    private double ringMin(double x, double z, double r, Geology.GeoSample o) {
        double m = Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            m = Math.min(m, geo.proxyHeight(x + Math.cos(a) * r, z + Math.sin(a) * r, o));
        }
        return m;
    }

    private double ringMax(double x, double z, double r, Geology.GeoSample o) {
        double m = -Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            m = Math.max(m, geo.proxyHeight(x + Math.cos(a) * r, z + Math.sin(a) * r, o));
        }
        return m;
    }

    public void forEachNear(double x, double z, java.util.function.Consumer<Anomaly> c) {
        int cx = MathUtil.fastFloor(x / CELL), cz = MathUtil.fastFloor(z / CELL);
        for (int dz = -1; dz <= 1; dz++)
            for (int dx = -1; dx <= 1; dx++)
                for (Anomaly a : cell(cx + dx, cz + dz)) c.accept(a);
    }

    public List<Anomaly> sitesIn(double x0, double z0, double x1, double z1) {
        List<Anomaly> out = new ArrayList<>();
        for (int cz = MathUtil.fastFloor(z0 / CELL); cz <= MathUtil.fastFloor(z1 / CELL); cz++)
            for (int cx = MathUtil.fastFloor(x0 / CELL); cx <= MathUtil.fastFloor(x1 / CELL); cx++)
                for (Anomaly a : cell(cx, cz))
                    if (a.x() >= x0 && a.x() < x1 && a.z() >= z0 && a.z() < z1) out.add(a);
        return out;
    }

    private double angular(Anomaly a, double dx, double dz, double amp) {
        double ang = Math.atan2(dz, dx);
        return 1 + amp * Simplex.fbm(noiseSeed ^ a.id(), Math.cos(ang) * 1.3, Math.sin(ang) * 1.3, 2, 2.0, 0.5);
    }

    /** Applies all anomalies covering (x,z) to the base sample (h0 already set by geology). */
    public void apply(double x, double z, Geology.GeoSample o) {
        int cx = MathUtil.fastFloor(x / CELL), cz = MathUtil.fastFloor(z / CELL);
        for (int dz = -1; dz <= 1; dz++)
            for (int dx = -1; dx <= 1; dx++)
                for (Anomaly a : cell(cx + dx, cz + dz)) {
                    double ddx = x - a.x(), ddz = z - a.z();
                    if (ddx * ddx + ddz * ddz > a.footprint() * a.footprint()) continue;
                    applyOne(a, x, z, ddx, ddz, o);
                }
    }

    private void applyOne(Anomaly a, double x, double z, double dx, double dz, Geology.GeoSample o) {
        double r = Math.sqrt(dx * dx + dz * dz);
        double hb = o.h0;
        int kind = OVERLAY_BASE + a.type().ordinal();
        switch (a.type()) {
            case SALT_FLAT -> {
                double R = a.radius() * angular(a, dx, dz, 0.22);
                double core = R * 0.8, outer = R * 1.42;
                double L = a.level();
                if (r <= core) {
                    o.h0 = L;
                    o.fixedLevel = L;
                    o.sinkLevel = L;
                    mark(o, kind, 1);
                } else if (r < outer) {
                    double t = smootherstep(core, outer, r);
                    // Shore: a faint rise then the surrounding terrain, never below the flat.
                    double target = lerp(t, L + 0.6, Math.max(hb, L + 1));
                    o.h0 = target;
                    mark(o, kind, 1 - t);
                }
            }
            case IMPACT_CRATER -> {
                double R = a.radius() * angular(a, dx, dz, 0.09);
                double rimH = 0.075 * a.radius();
                double floor = a.level() - a.depth();
                if (r < R) {
                    double w = Math.pow(r / R, 2.6);
                    double h = lerp(w, floor, hb + rimH);
                    if (a.radius() > 280) {
                        double cu = 0.28 * a.depth() * Math.exp(-MathUtil.sq(r / (0.12 * a.radius())));
                        h += cu;
                    }
                    // Fractured terraced walls.
                    h += 2.5 * Simplex.noise2(noiseSeed + a.id(), x / 18.0, z / 18.0) * w * (1 - w) * 4;
                    o.h0 = h;
                    o.resistanceOverride = 1.6;
                    if (r < 0.45 * R) {
                        o.sinkLevel = floor;
                        if (a.wet()) o.sinkWater = Math.floor(floor + 0.45 * a.depth());
                    }
                    mark(o, kind, 1);
                } else {
                    double e = (r - R) / (0.35 * a.radius());
                    double rim = rimH * Math.exp(-e);
                    double ejecta = rimH * 0.5 * Math.exp(-(r - R) / (0.55 * a.radius()))
                            * (0.5 + 0.5 * Simplex.fbm(noiseSeed + a.id() + 1, x / 40.0, z / 40.0, 2, 2.0, 0.5));
                    double fade = 1 - smoothstep(a.radius() * 1.7, a.footprint(), r);
                    o.h0 = hb + (rim + ejecta) * fade;
                    mark(o, kind, fade * 0.6);
                }
            }
            case DEEP_PIT -> {
                double R = a.radius() * angular(a, dx, dz, 0.25);
                double floor = a.level();
                if (r < R * 0.82) {
                    o.h0 = floor;
                    o.fixedLevel = floor;
                    o.sinkLevel = floor;
                    if (a.wet()) o.sinkWater = floor + Math.min(40, a.depth() * 0.3);
                    mark(o, kind, 1);
                } else if (r < R) {
                    double t = smoothstep(R * 0.82, R, r);
                    double h = lerp(Math.pow(t, 0.35), floor, hb + 2);
                    o.h0 = h;
                    o.fixedLevel = h;
                    o.resistanceOverride = 3.0;
                    mark(o, kind, 1);
                } else if (r < a.footprint()) {
                    o.h0 = hb + 2 * (1 - smoothstep(R, a.footprint(), r));
                    mark(o, kind, 0.3);
                }
            }
            case FAULT_ESCARPMENT -> {
                double ca = Math.cos(a.angle()), sa = Math.sin(a.angle());
                double u = dx * ca + dz * sa;        // along strike
                double v = -dx * sa + dz * ca;       // across
                double L = a.radius();
                double taper = 1 - smoothstep(L * 0.7, L, Math.abs(u));
                if (taper <= 0) return;
                double wiggle = 3 * Simplex.noise2(noiseSeed + a.id(), u / 220.0, 0);
                double w = 7;
                double side = smoothstep(-w, w, v + wiggle);
                // The uplifted block slopes back gently; the scarp face is sharp and straight.
                double back = Math.exp(-Math.max(0, v) / 900.0);
                o.h0 = hb + a.depth() * taper * side * back;
                if (Math.abs(v) < 30) o.resistanceOverride = 2.2;
                mark(o, kind, taper * (Math.abs(v) < 60 ? 1 : 0.3));
            }
            case CIRCULAR_BASIN -> {
                double R = a.radius() * angular(a, dx, dz, 0.05);
                double bowl = r < R ? a.depth() * Math.pow(1 - MathUtil.sq(r / R), 1.4) : 0;
                double ring = 14 * Math.exp(-MathUtil.sq((r - R) / 55.0));
                o.h0 = hb - bowl + ring;
                mark(o, kind, r < R ? 1 : 0.4);
            }
            case MONOLITH -> {
                double R = a.radius() * angular(a, dx, dz, 0.3);
                double top = a.level() + a.depth() + 6 * Simplex.noise2(noiseSeed + a.id(), x / 30.0, z / 30.0);
                if (r < R) {
                    double t = smoothstep(R * 0.72, R, r);
                    double dome = top - 8 * MathUtil.sq(r / R);
                    double h = lerp(Math.pow(t, 3.0), dome, hb + 4);
                    o.h0 = Math.max(hb, h);
                    o.resistanceOverride = 3.2;
                    mark(o, kind, 1);
                } else if (r < a.footprint()) {
                    double talus = 10 * (1 - smoothstep(R, a.footprint(), r));
                    o.h0 = hb + talus;
                    mark(o, kind, 0.4);
                }
            }
            case BARREN_FLAT -> {
                double R = a.radius() * angular(a, dx, dz, 0.2);
                double w = 1 - smoothstep(R * 0.7, R * 1.3, r);
                double target = a.level() + 1.2 * Simplex.fbm(noiseSeed + a.id(), x / 90.0, z / 90.0, 2, 2.0, 0.5);
                o.h0 = lerp(w, hb, target);
                o.resistanceOverride = 2.5;
                mark(o, kind, w);
            }
        }
    }

    private static void mark(Geology.GeoSample o, int kind, double strength) {
        if (strength >= o.overlayStrength) {
            o.overlay = kind;
            o.overlayStrength = strength;
        }
    }
}
