package dev.powderpass.core.volcano;

import dev.powderpass.core.GenSettings;
import dev.powderpass.core.geo.Geology;
import dev.powderpass.core.geo.Tectonics;
import dev.powderpass.core.noise.Simplex;
import dev.powderpass.core.util.BoundedCache;
import dev.powderpass.core.util.Hash;
import dev.powderpass.core.util.MathUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Deterministic volcano placement. Most volcanoes sit on tectonically volcanic belts (arcs above convergent
 * boundaries, divergent rifts); rare intraplate hotspots produce chains whose age increases along plate motion,
 * so only the youngest member is potentially active. Sites are pure functions of the seed and are listed per
 * coarse cell, which is what locate/inspect commands and the runtime lifecycle use.
 */
public final class Volcanoes {
    public static final double CELL = 1500;
    public static final double HOT_CELL = 9500;

    private final Geology geo;
    private final GenSettings s;
    private final long seed, hotSeed, noiseSeed;
    private final double freq;
    private final BoundedCache<VolcanoSite[]> cellCache = BoundedCache.ofCount(8192);
    private final ThreadLocal<Geology.GeoSample> tl = ThreadLocal.withInitial(Geology.GeoSample::new);

    public Volcanoes(long worldSeed, GenSettings settings, Geology geo) {
        this.geo = geo;
        this.s = settings;
        this.seed = Hash.subSeed(worldSeed, "volcano.sites");
        this.hotSeed = Hash.subSeed(worldSeed, "volcano.hotspot");
        this.noiseSeed = Hash.subSeed(worldSeed, "volcano.noise");
        this.freq = settings.volcanoFrequency();
    }

    public long noiseSeed() {
        return noiseSeed;
    }

    /** Sites whose centre lies in belt cell (cx,cz). */
    public VolcanoSite[] beltCell(int cx, int cz) {
        return cellCache.getOrCompute(Hash.pack(cx, cz), k -> computeBeltCell(cx, cz));
    }

    private VolcanoSite[] computeBeltCell(int cx, int cz) {
        if (freq <= 0) return new VolcanoSite[0];
        double px = (cx + 0.15 + 0.7 * Hash.unit(seed, cx, cz, 1)) * CELL;
        double pz = (cz + 0.15 + 0.7 * Hash.unit(seed, cx, cz, 2)) * CELL;
        Geology.GeoSample o = tl.get();
        geo.proxyHeight(px, pz, o);
        double v = o.volcanism;
        double r = Hash.unit(seed, cx, cz, 3);
        boolean belt = v > 0.25 && r < v * 0.55 * freq;
        if (!belt) return new VolcanoSite[0];
        if (o.continental < -0.25) return new VolcanoSite[0]; // keep to land / shelves
        long id = Hash.hash(seed, cx, cz);
        double t = Hash.unit(seed, cx, cz, 4);
        VolcanoSite.Type type;
        boolean rift = o.rift > 0.2;
        if (rift) type = t < 0.45 ? VolcanoSite.Type.SHIELD : (t < 0.8 ? VolcanoSite.Type.CINDER : VolcanoSite.Type.CALDERA);
        else type = t < 0.45 ? VolcanoSite.Type.STRATO : (t < 0.65 ? VolcanoSite.Type.CINDER
                : (t < 0.8 ? VolcanoSite.Type.ERODED : (t < 0.9 ? VolcanoSite.Type.CALDERA : VolcanoSite.Type.SHIELD)));
        boolean extinct = type == VolcanoSite.Type.ERODED || type == VolcanoSite.Type.CALDERA || Hash.unit(seed, cx, cz, 5) < 0.4;
        return new VolcanoSite[]{make(id, type, px, pz, extinct, rift ? VolcanoSite.Origin.RIFT : VolcanoSite.Origin.ARC, o)};
    }

    private VolcanoSite make(long id, VolcanoSite.Type type, double x, double z, boolean extinct,
                             VolcanoSite.Origin origin, Geology.GeoSample o) {
        double a = Hash.unit(Hash.mix(id ^ 11)), b = Hash.unit(Hash.mix(id ^ 12)), c = Hash.unit(Hash.mix(id ^ 13));
        double R, H, cr, cd;
        switch (type) {
            case SHIELD -> { R = 480 + 380 * a; H = 70 + 110 * b; cr = 30 + 30 * c; cd = 14 + 10 * c; }
            case STRATO -> { R = 300 + 200 * a; H = 190 + 190 * b; cr = 22 + 22 * c; cd = 18 + 18 * c; }
            case CINDER -> { R = 60 + 60 * a; H = 24 + 34 * b; cr = 10 + 8 * c; cd = 6 + 6 * c; }
            case CALDERA -> { R = 520 + 300 * a; H = 60 + 70 * b; cr = 230 + 170 * c; cd = 70 + 80 * b; }
            default -> { R = 320 + 200 * a; H = 110 + 120 * b; cr = 26 + 20 * c; cd = 10 + 10 * c; }
        }
        // Keep summits under the generation cap: shrink tall cones on already-high ground.
        double headroom = (s.terrainCap() - 40) - o.h0;
        if (H > headroom) H = Math.max(20, headroom);
        int channels = type == VolcanoSite.Type.CINDER ? 2 : (type == VolcanoSite.Type.CALDERA ? 0 : 3 + (int) (c * 3));
        return new VolcanoSite(id, type, x, z, R, H, cr, cd, extinct, origin, channels, a * Math.PI * 2);
    }

    /** Hotspot chain in hotspot cell (hx,hz): 2-4 volcanoes aging along plate motion; youngest may be active. */
    public VolcanoSite[] hotspotCell(int hx, int hz) {
        return cellCache.getOrCompute(Hash.pack(hx, hz) ^ 0x7F4A7C15F00DL, k -> computeHotspot(hx, hz));
    }

    private VolcanoSite[] computeHotspot(int hx, int hz) {
        if (freq <= 0) return new VolcanoSite[0];
        if (Hash.unit(hotSeed, hx, hz, 0) > 0.33 * Math.min(1.5, freq)) return new VolcanoSite[0];
        double px = (hx + 0.25 + 0.5 * Hash.unit(hotSeed, hx, hz, 1)) * HOT_CELL;
        double pz = (hz + 0.25 + 0.5 * Hash.unit(hotSeed, hx, hz, 2)) * HOT_CELL;
        Geology.GeoSample o = tl.get();
        geo.proxyHeight(px, pz, o);
        Tectonics t = geo.tectonics();
        int ax = o.plate.ax, az = o.plate.az;
        double vx = t.velX(ax, az), vz = t.velZ(ax, az);
        double vl = Math.max(1e-6, Math.hypot(vx, vz));
        vx /= vl; vz /= vl;
        int n = 2 + Hash.range(Hash.hash(hotSeed, hx, hz, 3), 3);
        List<VolcanoSite> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double x = px + vx * 950 * i, z = pz + vz * 950 * i;
            geo.proxyHeight(x, z, o);
            if (o.continental < -0.3 && i > 0) continue;
            long id = Hash.hash(hotSeed, hx, hz, 100 + i);
            VolcanoSite.Type type = i == 0 ? (Hash.unit(id) < 0.6 ? VolcanoSite.Type.SHIELD : VolcanoSite.Type.STRATO)
                    : (i == 1 ? VolcanoSite.Type.ERODED : VolcanoSite.Type.CALDERA);
            list.add(make(id, type, x, z, i > 0, VolcanoSite.Origin.HOTSPOT, o));
        }
        return list.toArray(new VolcanoSite[0]);
    }

    /** Visits all sites whose footprint may cover (x,z). */
    public void forEachNear(double x, double z, Consumer<VolcanoSite> c) {
        int cx = MathUtil.fastFloor(x / CELL), cz = MathUtil.fastFloor(z / CELL);
        for (int dz = -1; dz <= 1; dz++)
            for (int dx = -1; dx <= 1; dx++)
                for (VolcanoSite v : beltCell(cx + dx, cz + dz)) c.accept(v);
        int hx = MathUtil.fastFloor(x / HOT_CELL), hz = MathUtil.fastFloor(z / HOT_CELL);
        for (int dz = -1; dz <= 1; dz++)
            for (int dx = -1; dx <= 1; dx++)
                for (VolcanoSite v : hotspotCell(hx + dx, hz + dz)) c.accept(v);
    }

    /** All sites with centres inside the block rectangle (for locate/diagnostics). */
    public List<VolcanoSite> sitesIn(double x0, double z0, double x1, double z1) {
        List<VolcanoSite> out = new ArrayList<>();
        for (int cz = MathUtil.fastFloor(z0 / CELL); cz <= MathUtil.fastFloor(z1 / CELL); cz++)
            for (int cx = MathUtil.fastFloor(x0 / CELL); cx <= MathUtil.fastFloor(x1 / CELL); cx++)
                for (VolcanoSite v : beltCell(cx, cz)) if (in(v, x0, z0, x1, z1)) out.add(v);
        for (int hz = MathUtil.fastFloor((z0 - 4000) / HOT_CELL); hz <= MathUtil.fastFloor((z1 + 4000) / HOT_CELL); hz++)
            for (int hx = MathUtil.fastFloor((x0 - 4000) / HOT_CELL); hx <= MathUtil.fastFloor((x1 + 4000) / HOT_CELL); hx++)
                for (VolcanoSite v : hotspotCell(hx, hz)) if (in(v, x0, z0, x1, z1)) out.add(v);
        return out;
    }

    private static boolean in(VolcanoSite v, double x0, double z0, double x1, double z1) {
        return v.x() >= x0 && v.x() < x1 && v.z() >= z0 && v.z() < z1;
    }

    public VolcanoSite byId(long id, double nearX, double nearZ) {
        VolcanoSite[] found = new VolcanoSite[1];
        forEachNear(nearX, nearZ, v -> { if (v.id() == id) found[0] = v; });
        return found[0];
    }

    /** Angular noise in [-1,1] that roughens edifice outlines. */
    public double angleNoise(VolcanoSite v, double dx, double dz) {
        double ang = Math.atan2(dz, dx);
        return Simplex.noise2(noiseSeed ^ v.id(), Math.cos(ang) * 1.6, Math.sin(ang) * 1.6);
    }
}
