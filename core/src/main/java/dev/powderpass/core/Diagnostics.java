package dev.powderpass.core;

import dev.powderpass.core.anomaly.Anomaly;
import dev.powderpass.core.biome.BiomeClassifier;
import dev.powderpass.core.biome.PBiome;
import dev.powderpass.core.hydro.Domains;
import dev.powderpass.core.terrain.TerrainSampler;
import dev.powderpass.core.util.Hash;
import dev.powderpass.core.volcano.VolcanoSite;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Deterministic metadata queries used by commands: spawn selection, locate, and column inspection. Every query is
 * bounded (fixed sample budgets / search radii) and evaluates the generator functions directly; nothing here
 * generates chunks.
 */
public final class Diagnostics {
    private final PowderWorld world;
    private final TerrainSampler sampler;
    private final BiomeClassifier biomes;

    public Diagnostics(PowderWorld world, TerrainSampler sampler, BiomeClassifier biomes) {
        this.world = world;
        this.sampler = sampler;
        this.biomes = biomes;
    }

    public record Pos(int x, int y, int z, String note) {}

    // ------------------------------------------------------------------------------------------------ spawn

    private static boolean friendly(PBiome b) {
        return switch (b) {
            case ROLLING_GRASSLAND, ALLUVIAL_MEADOW, TEMPERATE_MIXED_FOREST, BIRCH_UPLANDS, FLOODPLAIN_WOODLAND,
                 BOREAL_FOREST, WARM_SAVANNA, KARST_COUNTRY, CONIFER_FOOTHILLS, WET_COASTAL_FOREST -> true;
            default -> false;
        };
    }

    private static boolean wooded(PBiome b) {
        return switch (b) {
            case TEMPERATE_MIXED_FOREST, BIRCH_UPLANDS, FLOODPLAIN_WOODLAND, BOREAL_FOREST, CONIFER_FOOTHILLS,
                 WET_COASTAL_FOREST, TROPICAL_RAINFOREST -> true;
            default -> false;
        };
    }

    /**
     * Stable, accessible spawn: dry gentle land at moderate elevation, away from volcanoes/anomalies, with wood and
     * water within ~160 blocks. Spiral search on a 48-block lattice, at most ~6000 candidates.
     */
    public Pos findSpawn() {
        TerrainSampler.Column c = new TerrainSampler.Column(), r = new TerrainSampler.Column();
        int sea = world.settings.seaLevel();
        int step = 48;
        int maxRing = 85;
        Pos fallback = null;
        for (int ring = 0; ring <= maxRing; ring++) {
            for (int i = -ring; i <= ring; i++) {
                for (int side = 0; side < 4; side++) {
                    int gx, gz;
                    switch (side) {
                        case 0 -> { gx = i; gz = -ring; }
                        case 1 -> { gx = ring; gz = i; }
                        case 2 -> { gx = -i; gz = ring; }
                        default -> { gx = -ring; gz = -i; }
                    }
                    if (ring > 0 && Math.abs(gx) != ring && Math.abs(gz) != ring) continue;
                    if (ring == 0 && side > 0) continue;
                    int x = gx * step + 8, z = gz * step + 8;
                    sampler.sample(x, z, c);
                    double elev = c.height - sea;
                    if (!Double.isNaN(c.waterLevel) || c.channel || elev < 3 || elev > 140) continue;
                    if (c.gridSlope > 0.22 || c.geo.overlay != 0) continue;
                    PBiome b = biomes.surface(c);
                    if (!friendly(b)) continue;
                    if (nearVolcano(x, z, 600)) continue;
                    if (fallback == null) fallback = new Pos(x, (int) Math.floor(c.height) + 1, z, "fallback");
                    boolean water = false, wood = wooded(b);
                    for (int k = 0; k < 16 && !(water && wood); k++) {
                        double a = k * Math.PI / 8;
                        for (int d = 40; d <= 160; d += 60) {
                            sampler.sample(x + Math.cos(a) * d, z + Math.sin(a) * d, r);
                            if (!Double.isNaN(r.waterLevel)) water = true;
                            if (wooded(biomes.surface(r))) wood = true;
                        }
                    }
                    if (water && wood) return new Pos(x, (int) Math.floor(c.height) + 1, z, b.key);
                }
            }
        }
        return fallback != null ? fallback : new Pos(8, sea + 40, 8, "default");
    }

    public boolean nearVolcano(double x, double z, double margin) {
        boolean[] near = {false};
        world.volcanoes.forEachNear(x, z, v -> {
            if (Math.hypot(v.x() - x, v.z() - z) < v.radius() + margin) near[0] = true;
        });
        return near[0];
    }

    // ------------------------------------------------------------------------------------------------ locate

    public VolcanoSite nearestVolcano(double x, double z, boolean eruptiveOnly, double maxRadius) {
        VolcanoSite best = null;
        double bd = Double.MAX_VALUE;
        for (double r = 2000; r <= maxRadius * 1.01; r *= 2) {
            for (VolcanoSite v : world.volcanoes.sitesIn(x - r, z - r, x + r, z + r)) {
                if (eruptiveOnly && !v.canErupt()) continue;
                double d = Math.hypot(v.x() - x, v.z() - z);
                if (d < bd) { bd = d; best = v; }
            }
            if (best != null && bd <= r) return best;
        }
        return best;
    }

    public Anomaly nearestAnomaly(double x, double z, Anomaly.Type type, double maxRadius) {
        Anomaly best = null;
        double bd = Double.MAX_VALUE;
        for (double r = 4000; r <= maxRadius * 1.01; r *= 2) {
            for (Anomaly a : world.anomalies.sitesIn(x - r, z - r, x + r, z + r)) {
                if (type != null && a.type() != type) continue;
                double d = Math.hypot(a.x() - x, a.z() - z);
                if (d < bd) { bd = d; best = a; }
            }
            if (best != null && bd <= r) return best;
        }
        return best;
    }

    public enum Landform {
        PLAIN, MOUNTAIN_VALLEY, HILL_TRANSITION, FAULT_SCARP, LARGE_RIVER, LAKE, GLACIAL_VALLEY, ROCK_SLOPE, DELTA
    }

    /** Bounded lattice search (spiral, 32-block step, at most {@code budget} samples) for a landform. */
    public Pos locate(Landform lf, double x0, double z0, int budget) {
        TerrainSampler.Column c = new TerrainSampler.Column(), n = new TerrainSampler.Column();
        int sea = world.settings.seaLevel();
        Predicate<TerrainSampler.Column> pred = switch (lf) {
            case PLAIN -> col -> Double.isNaN(col.waterLevel) && col.gridSlope < 0.03 && col.height - sea > 4 && col.height - sea < 70
                    && (biomes.surface(col) == PBiome.ROLLING_GRASSLAND || biomes.surface(col) == PBiome.ALLUVIAL_MEADOW);
            case MOUNTAIN_VALLEY -> col -> col.channel && col.height - sea > 120 && col.riverQ > 150000 && col.erosionDepth > 15;
            case HILL_TRANSITION -> col -> Double.isNaN(col.waterLevel) && col.gridSlope > 0.12 && col.gridSlope < 0.4 && col.height - sea > 40;
            case FAULT_SCARP -> col -> Math.abs(col.geo.faultScarp) > 10 && col.geo.faultDist < 20 || col.geo.overlay == 100 + Anomaly.Type.FAULT_ESCARPMENT.ordinal();
            case LARGE_RIVER -> col -> col.channel && col.riverWidth > 14;
            case LAKE -> col -> col.waterKind == TerrainSampler.WATER_LAKE;
            case GLACIAL_VALLEY -> col -> col.ice > 4000 && Double.isNaN(col.waterLevel);
            case ROCK_SLOPE -> col -> col.gridSlope > 0.7 && col.gridSlope < 1.3 && Double.isNaN(col.waterLevel);
            case DELTA -> col -> (col.riverFlags & dev.powderpass.core.hydro.RiverNet.FLAG_DELTA) != 0;
        };
        int step = switch (lf) { case FAULT_SCARP, LARGE_RIVER, LAKE, DELTA, GLACIAL_VALLEY -> 64; default -> 32; };
        int used = 0;
        for (int ring = 0; used < budget; ring++) {
            for (int i = -ring; i <= ring && used < budget; i++) {
                for (int side = 0; side < 4 && used < budget; side++) {
                    int gx, gz;
                    switch (side) {
                        case 0 -> { gx = i; gz = -ring; }
                        case 1 -> { gx = ring; gz = i; }
                        case 2 -> { gx = -i; gz = ring; }
                        default -> { gx = -ring; gz = -i; }
                    }
                    if (ring > 0 && Math.abs(gx) != ring && Math.abs(gz) != ring) continue;
                    if (ring == 0 && side > 0) continue;
                    double x = x0 + gx * step, z = z0 + gz * step;
                    sampler.sample(x, z, c);
                    used++;
                    if (pred.test(c)) {
                        int y = (int) Math.floor(Double.isNaN(c.waterLevel) ? c.height : Math.max(c.height, c.waterLevel)) + 1;
                        return new Pos((int) Math.floor(x), y, (int) Math.floor(z), lf.name().toLowerCase(Locale.ROOT));
                    }
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------------ inspect

    public List<String> inspect(double x, double z) {
        TerrainSampler.Column c = new TerrainSampler.Column();
        sampler.sample(x, z, c);
        List<String> out = new ArrayList<>();
        PBiome b = biomes.surface(c);
        out.add(String.format(Locale.ROOT, "column %.0f %.0f: surface y=%d biome=%s", x, z, (int) Math.floor(c.height), b.id()));
        out.add(String.format(Locale.ROOT, "geology: province=%s orogeny=%.2f youth=%.2f rift=%.2f volcanism=%.2f resistance=%.2f",
                c.geo.province, c.geo.orogeny, c.geo.youth, c.geo.rift, c.geo.volcanism,
                Double.isNaN(c.geo.resistanceOverride) ? c.geo.resistance : c.geo.resistanceOverride));
        out.add(String.format(Locale.ROOT, "faults: distance=%.0f strataThrow=%.1f scarp=%.1f | plate edge distance=%.0f",
                c.geo.faultDist, c.geo.faultThrow, c.geo.faultScarp, c.geo.signedBoundaryDist));
        out.add(String.format(Locale.ROOT, "climate: baseTemp=%.2f tempHere=%.2f precip=%.2f slope=%.2f",
                c.baseTemp, world.climate.temperatureAt(c.baseTemp, c.height), c.precip, c.gridSlope));
        out.add(String.format(Locale.ROOT, "erosion: pre-erosion y=%.1f change=%+.1f sediment=%.1f pastIce=%.0f drainage=%.3g",
                c.height + c.erosionDepth, -c.erosionDepth, c.sediment, c.ice, c.drainage));
        String water = Double.isNaN(c.waterLevel) ? "none" : String.format(Locale.ROOT, "%s surface=%.0f",
                c.waterKind == TerrainSampler.WATER_SEA ? "sea" : c.waterKind == TerrainSampler.WATER_LAKE ? "lake" : "river", c.waterLevel);
        out.add("water: " + water + (c.riverDist < 1e9 ? String.format(Locale.ROOT, " | nearest channel %.0f blocks, width %.0f, discharge %.3g, gradient %.4f",
                c.riverDist, c.riverWidth, c.riverQ, c.riverGrad) : ""));
        long id = c.domain;
        Domains.Flow f = world.domains.flow(id);
        StringBuilder chain = new StringBuilder();
        int hops = 0;
        long cur = id;
        Domains.Flow cf = f;
        while (cf.type == Domains.FlowType.DOWN && hops < 64) {
            hops++;
            cur = cf.down;
            cf = world.domains.flow(cur);
        }
        chain.append(hops).append(" domain hops to ").append(cf.type == Domains.FlowType.SEA ? "the sea" : "a terminal basin")
                .append(" (outlet level ").append(cf.level).append(")");
        out.add(String.format(Locale.ROOT, "watershed: domain %d,%d (%s, outlet level %d, border distance %.0f); %s",
                Hash.unpackX(id), Hash.unpackZ(id), f.type, f.level, c.boundaryDist, chain));
        if (c.geo.overlay != 0) out.add("overlay id " + c.geo.overlay + " strength " + String.format(Locale.ROOT, "%.2f", c.geo.overlayStrength));
        return out;
    }
}
