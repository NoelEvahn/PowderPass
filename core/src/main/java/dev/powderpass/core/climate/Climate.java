package dev.powderpass.core.climate;

import dev.powderpass.core.GenSettings;
import dev.powderpass.core.geo.Geology;
import dev.powderpass.core.noise.Simplex;
import dev.powderpass.core.util.BoundedCache;
import dev.powderpass.core.util.Hash;
import dev.powderpass.core.util.MathUtil;

/**
 * Climate: base temperature, prevailing wind, and precipitation with orographic lift and rain shadows. Rain shadow
 * integrates the geological elevation proxy upwind, so dry interiors and wet windward slopes follow the mountains
 * that geology actually placed. Values are computed on a 64-block lattice (cached in 1024-block tiles) and
 * bilinearly interpolated; the lattice is global, so neighbouring tiles agree exactly on shared points.
 */
public final class Climate {
    private static final int STEP = 64;
    private static final int TILE = 16; // lattice steps per tile

    private final Geology geo;
    private final GenSettings s;
    private final long tSeed, mSeed, wSeed;
    private final double bs;
    private final BoundedCache<float[]> tiles = BoundedCache.ofCount(1024);
    private final ThreadLocal<Geology.GeoSample> tl = ThreadLocal.withInitial(Geology.GeoSample::new);

    public Climate(long worldSeed, GenSettings settings, Geology geo) {
        this.geo = geo;
        this.s = settings;
        this.bs = settings.biomeSize() * settings.horizontalScale();
        tSeed = Hash.subSeed(worldSeed, "climate.temp");
        mSeed = Hash.subSeed(worldSeed, "climate.moist");
        wSeed = Hash.subSeed(worldSeed, "climate.wind");
    }

    /** Sea-level temperature in [-1,1] (-1 frozen, 1 hot). Cheap, analytic. */
    public double baseTemperature(double x, double z) {
        return MathUtil.clamp(1.15 * Simplex.fbm(tSeed, x / (10000 * bs), z / (10000 * bs), 3, 2.0, 0.5)
                + 0.12 * Simplex.noise2(tSeed + 1, x / (1600 * bs), z / (1600 * bs)), -1, 1);
    }

    /** Temperature at an absolute elevation (lapse rate: peaks near the cap are about 1.6 units colder). */
    public double temperature(double x, double z, double y) {
        return temperatureAt(baseTemperatureInterp(x, z), y);
    }

    public double temperatureAt(double baseT, double y) {
        double above = Math.max(0, y - s.seaLevel()) / (double) (s.terrainCap() - s.seaLevel());
        return baseT - 1.65 * above;
    }

    public double windAngle(double x, double z) {
        return Math.PI * 2 * (0.5 + 0.8 * Simplex.fbm(wSeed, x / 40000.0, z / 40000.0, 2, 2.0, 0.5));
    }

    private float[] tile(int tx, int tz) {
        return tiles.getOrCompute(Hash.pack(tx, tz), k -> computeTile(tx, tz));
    }

    private float[] computeTile(int tx, int tz) {
        int n = TILE + 1;
        float[] out = new float[n * n * 2];
        Geology.GeoSample o = tl.get();
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                double x = (tx * TILE + i) * (double) STEP, z = (tz * TILE + j) * (double) STEP;
                out[(j * n + i) * 2] = (float) baseTemperature(x, z);
                out[(j * n + i) * 2 + 1] = (float) computePrecip(x, z, o);
            }
        }
        return out;
    }

    private double computePrecip(double x, double z, Geology.GeoSample o) {
        double hHere = geo.proxyHeight(x, z, o);
        double cont = o.continental;
        double moist = 0.52 + 0.42 * Simplex.fbm(mSeed, x / (8000 * bs), z / (8000 * bs), 3, 2.0, 0.5);
        // Maritime influence.
        moist += 0.22 * (1 - MathUtil.smoothstep(-0.05, 0.45, cont));
        double a = windAngle(x, z);
        double ux = -Math.cos(a), uz = -Math.sin(a); // upwind direction
        double barrier = 0, sumUp = 0;
        double[] dists = {300, 700, 1300, 2200, 3400};
        for (double d : dists) {
            double hu = geo.proxyHeight(x + ux * d, z + uz * d, o);
            double decay = 1.0 / (1.0 + d / 2500.0);
            barrier = Math.max(barrier, (hu - hHere) * decay);
            sumUp += hu;
        }
        double meanUp = sumUp / dists.length;
        // Rain shadow: precipitation drops behind barriers taller than the local terrain.
        moist *= Math.exp(-Math.max(0, barrier - 25) / 260.0);
        // Orographic lift on windward slopes.
        moist += 0.25 * MathUtil.smoothstep(0, 220, hHere - meanUp);
        return MathUtil.clamp(moist, 0.02, 1.4);
    }

    private double interp(double x, double z, int ch) {
        double gx = x / STEP, gz = z / STEP;
        int ix = MathUtil.fastFloor(gx), iz = MathUtil.fastFloor(gz);
        double fx = gx - ix, fz = gz - iz;
        int tx = Math.floorDiv(ix, TILE), tz = Math.floorDiv(iz, TILE);
        int lx = ix - tx * TILE, lz = iz - tz * TILE;
        float[] t = tile(tx, tz);
        int n = TILE + 1;
        double v00 = t[(lz * n + lx) * 2 + ch], v10 = t[(lz * n + lx + 1) * 2 + ch];
        double v01 = t[((lz + 1) * n + lx) * 2 + ch], v11 = t[((lz + 1) * n + lx + 1) * 2 + ch];
        return MathUtil.lerp(fz, MathUtil.lerp(fx, v00, v10), MathUtil.lerp(fx, v01, v11));
    }

    /** Precipitation index, ~0 (hyper-arid) to ~1.4 (very wet). */
    public double precipitation(double x, double z) {
        return interp(x, z, 1);
    }

    public double baseTemperatureInterp(double x, double z) {
        return interp(x, z, 0);
    }

    public BoundedCache<float[]> cache() {
        return tiles;
    }
}
