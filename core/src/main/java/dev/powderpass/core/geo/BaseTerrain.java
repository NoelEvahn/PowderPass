package dev.powderpass.core.geo;

import dev.powderpass.core.GenSettings;
import dev.powderpass.core.anomaly.Anomalies;
import dev.powderpass.core.climate.Climate;
import dev.powderpass.core.volcano.VolcanoSite;
import dev.powderpass.core.volcano.Volcanoes;

/**
 * The complete pre-erosion surface: geology + volcanic edifices + anomalies, plus the marine mask. This is the
 * initial condition and the boundary condition of the erosion model, so every overlay is visible to drainage.
 */
public final class BaseTerrain {
    public static final int OVERLAY_VOLCANO_BASE = 50;

    private final Geology geo;
    private final Climate climate;
    private final Volcanoes volcanoes;
    private final Anomalies anomalies;
    private final GenSettings s;

    public BaseTerrain(GenSettings s, Geology geo, Climate climate, Volcanoes volcanoes, Anomalies anomalies) {
        this.s = s;
        this.geo = geo;
        this.climate = climate;
        this.volcanoes = volcanoes;
        this.anomalies = anomalies;
    }

    public void sample(double x, double z, Geology.GeoSample o) {
        geo.sample(x, z, o);
        o.resetOverlay();
        applyVolcanoes(x, z, o);
        anomalies.apply(x, z, o);
        o.h0 = Math.min(o.h0, s.terrainCap() - 12);
        o.sea = o.continental < 0.0 && o.h0 < s.seaLevel();
    }

    private void applyVolcanoes(double x, double z, Geology.GeoSample o) {
        double[] best = {0};
        VolcanoSite[] bestSite = {null};
        double base = o.h0;
        volcanoes.forEachNear(x, z, v -> {
            double dx = x - v.x(), dz = z - v.z();
            double fp = v.footprint();
            if (dx * dx + dz * dz > fp * fp) return;
            double r = Math.sqrt(dx * dx + dz * dz);
            double p = v.profile(r, volcanoes.angleNoise(v, dx, dz));
            if (bestSite[0] == null || Math.abs(p) > Math.abs(best[0]) || p > best[0]) {
                best[0] = p;
                bestSite[0] = v;
            }
        });
        VolcanoSite v = bestSite[0];
        if (v == null) return;
        double r = Math.hypot(x - v.x(), z - v.z());
        o.h0 = base + best[0];
        if (r < v.radius()) {
            o.overlay = OVERLAY_VOLCANO_BASE + v.type().ordinal();
            o.overlayStrength = 1 - r / v.radius();
            o.resistanceOverride = v.type() == VolcanoSite.Type.ERODED ? 1.4 : 2.6;
        }
        if (r < v.craterRadius() * 0.6) {
            o.sinkLevel = o.h0;
            if (v.type() == VolcanoSite.Type.CALDERA && climate.precipitation(v.x(), v.z()) > 0.55) {
                o.sinkWater = Math.floor(base - v.craterDepth() + 0.4 * (v.height() + v.craterDepth()));
            }
        }
    }

    public Geology geology() { return geo; }
    public Climate climate() { return climate; }
    public Volcanoes volcanoes() { return volcanoes; }
    public Anomalies anomalies() { return anomalies; }
    public GenSettings settings() { return s; }
}
