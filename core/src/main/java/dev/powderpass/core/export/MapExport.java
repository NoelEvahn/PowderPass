package dev.powderpass.core.export;

import dev.powderpass.core.GenSettings;
import dev.powderpass.core.PowderWorld;
import dev.powderpass.core.anomaly.Anomalies;
import dev.powderpass.core.anomaly.Anomaly;
import dev.powderpass.core.biome.BiomeClassifier;
import dev.powderpass.core.biome.PBiome;
import dev.powderpass.core.geo.BaseTerrain;
import dev.powderpass.core.geo.Province;
import dev.powderpass.core.hydro.DomainSim;
import dev.powderpass.core.hydro.Domains;
import dev.powderpass.core.hydro.RiverNet;
import dev.powderpass.core.terrain.TerrainSampler;
import dev.powderpass.core.volcano.VolcanoSite;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Offline diagnostic map export (developer tool; never runs inside the game). Produces PNG layers for elevation,
 * slope, geology/faults/plate boundaries, precipitation, drainage, biomes, erosion change and anomaly/volcano
 * placement, plus a text report with terrain-class proportions over land.
 *
 * <p>Usage: {@code MapExport <seed> <centerX> <centerZ> <size> <outDir> [noErosion]}
 */
public final class MapExport {
    public static void main(String[] args) throws Exception {
        long seed = Long.parseLong(args[0]);
        int cx = Integer.parseInt(args[1]), cz = Integer.parseInt(args[2]);
        int size = Integer.parseInt(args[3]);
        File out = new File(args[4]);
        boolean noErosion = args.length > 5 && Boolean.parseBoolean(args[5]);
        export(seed, GenSettings.defaults(), cx, cz, size, Math.max(1, size / 1024), out, !noErosion);
    }

    public static Map<String, Double> export(long seed, GenSettings settings, int cx, int cz, int size, int step, File outDir, boolean erosion) throws Exception {
        PowderWorld world = new PowderWorld(seed, settings, 3L << 30, erosion);
        TerrainSampler sampler = new TerrainSampler(world);
        BiomeClassifier classifier = new BiomeClassifier(world);
        int n = size / step;
        double x0 = cx - size / 2.0, z0 = cz - size / 2.0;
        int sea = world.settings.seaLevel();
        double[] h = new double[n * n], h0 = new double[n * n], prec = new double[n * n], drain = new double[n * n];
        double[] water = new double[n * n], fault = new double[n * n];
        byte[] wk = new byte[n * n];
        int[] prov = new int[n * n], biome = new int[n * n], overlay = new int[n * n];
        double[] orog = new double[n * n];
        long t0 = System.nanoTime();
        IntStream.range(0, n).parallel().forEach(j -> {
            TerrainSampler.Column c = new TerrainSampler.Column();
            for (int i = 0; i < n; i++) {
                int k = j * n + i;
                sampler.sample(x0 + i * step, z0 + j * step, c);
                h[k] = Math.floor(c.height);
                h0[k] = c.height + c.erosionDepth;
                prec[k] = c.precip;
                drain[k] = c.drainage;
                water[k] = c.waterLevel;
                wk[k] = c.waterKind;
                prov[k] = c.geo.province.ordinal();
                biome[k] = classifier.surface(c).ordinal();
                overlay[k] = c.geo.overlay;
                orog[k] = c.geo.orogeny;
                fault[k] = c.geo.faultDist;
            }
        });
        double secs = (System.nanoTime() - t0) / 1e9;
        String tag = seed + "_" + cx + "_" + cz + "_" + size + (erosion ? "" : "_noerosion");
        outDir.mkdirs();

        // Elevation (shaded) with water.
        BufferedImage elev = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        BufferedImage slope = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        BufferedImage geo = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        BufferedImage pre = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        BufferedImage dr = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        BufferedImage bio = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        BufferedImage ero = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        BufferedImage base = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        int land = 0;
        double maxH = -1e9;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                int k = j * n + i;
                boolean wet = !Double.isNaN(water[k]) && water[k] - 1 > h[k];
                int col = Img.shade(Img.elevationColor(h[k], sea), h, n, n, i, j, step);
                if (wet) col = wk[k] == TerrainSampler.WATER_RIVER ? 0x1f4fff : (wk[k] == TerrainSampler.WATER_LAKE ? 0x3a78d8 : Img.elevationColor(h[k], sea));
                elev.setRGB(i, j, col);
                base.setRGB(i, j, Img.shade(Img.elevationColor(h0[k], sea), h0, n, n, i, j, step));
                int i0 = Math.max(0, i - 1), i1 = Math.min(n - 1, i + 1), j0 = Math.max(0, j - 1), j1 = Math.min(n - 1, j + 1);
                double gx = (h[j * n + i1] - h[j * n + i0]) / ((i1 - i0) * step), gz = (h[j1 * n + i] - h[j0 * n + i]) / ((j1 - j0) * step);
                double sl = Math.hypot(gx, gz);
                slope.setRGB(i, j, Img.ramp(Math.min(1, sl / 1.5)));
                int gc = Img.category(prov[k]);
                if (fault[k] < step * 0.7) gc = 0x000000;
                geo.setRGB(i, j, gc);
                pre.setRGB(i, j, wet ? 0x202020 : Img.ramp(Math.min(1, prec[k] / 1.3)));
                double la = Math.log10(Math.max(1, drain[k]));
                dr.setRGB(i, j, wet ? 0x0030a0 : Img.ramp(Math.min(1, (la - 2) / 5.0)));
                bio.setRGB(i, j, Img.shade(biomeColor(PBiome.byOrdinal(biome[k])), h, n, n, i, j, step));
                double d = h[k] - h0[k];
                int ec = d < 0 ? Img.rgb(255, 255 + d * 6, 255 + d * 6) : Img.rgb(255 - d * 18, 255 - d * 18, 255);
                ero.setRGB(i, j, wet ? 0x99aacc : ec);
                if (h[k] >= sea && !wet) land++;
                maxH = Math.max(maxH, h[k]);
            }
        }
        // Rivers onto drainage map and plate boundaries onto geology.
        Graphics2D g2 = dr.createGraphics();
        g2.setColor(new Color(0x00ffff));
        Set<Long> ids = new HashSet<>();
        for (int j = 0; j < n; j += 4) for (int i = 0; i < n; i += 4) ids.add(world.domains.owner(x0 + i * step, z0 + j * step));
        double[] seg = new double[8];
        int segments = 0;
        for (long id : ids) {
            DomainSim sim = world.sim(id);
            for (int s = 0; s < sim.rivers.segmentCount(); s++) {
                sim.rivers.segment(s, seg);
                g2.setStroke(new BasicStroke((float) Math.max(1, Math.min(3, Math.log10(Math.max(10, seg[6])) - 4))));
                g2.drawLine((int) ((seg[0] - x0) / step), (int) ((seg[1] - z0) / step), (int) ((seg[2] - x0) / step), (int) ((seg[3] - z0) / step));
                segments++;
            }
        }
        g2.dispose();

        // Anomalies and volcanoes.
        BufferedImage anomalies = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        Graphics2D ga = anomalies.createGraphics();
        ga.drawImage(elev, 0, 0, null);
        List<Anomaly> anos = world.anomalies.sitesIn(x0, z0, x0 + size, z0 + size);
        List<VolcanoSite> volcs = world.volcanoes.sitesIn(x0, z0, x0 + size, z0 + size);
        ga.setStroke(new BasicStroke(2));
        StringBuilder sites = new StringBuilder();
        for (Anomaly a : anos) {
            ga.setColor(new Color(0xff00ff));
            int r = (int) Math.max(3, a.radius() / step);
            int px = (int) ((a.x() - x0) / step), pz = (int) ((a.z() - z0) / step);
            ga.drawOval(px - r, pz - r, 2 * r, 2 * r);
            ga.drawString(a.type().key, px + r + 2, pz);
            sites.append(String.format(Locale.ROOT, "anomaly %s at %.0f %.0f radius %.0f depth %.0f level %.0f wet %b%n", a.type().key, a.x(), a.z(), a.radius(), a.depth(), a.level(), a.wet()));
        }
        for (VolcanoSite v : volcs) {
            ga.setColor(v.canErupt() ? new Color(0xff2000) : new Color(0x804000));
            int r = (int) Math.max(3, v.radius() / step);
            int px = (int) ((v.x() - x0) / step), pz = (int) ((v.z() - z0) / step);
            ga.drawOval(px - r, pz - r, 2 * r, 2 * r);
            ga.drawString(v.type().name().toLowerCase(Locale.ROOT), px + r + 2, pz);
            sites.append(String.format(Locale.ROOT, "volcano %s %s at %.0f %.0f radius %.0f height %.0f extinct %b id %d%n", v.type(), v.origin(), v.x(), v.z(), v.radius(), v.height(), v.extinct(), v.id()));
        }
        ga.dispose();

        Img.save(elev, new File(outDir, "elevation_" + tag + ".png"));
        Img.save(base, new File(outDir, "base_" + tag + ".png"));
        Img.save(slope, new File(outDir, "slope_" + tag + ".png"));
        Img.save(geo, new File(outDir, "geology_" + tag + ".png"));
        Img.save(pre, new File(outDir, "precipitation_" + tag + ".png"));
        Img.save(dr, new File(outDir, "drainage_" + tag + ".png"));
        Img.save(bio, new File(outDir, "biomes_" + tag + ".png"));
        Img.save(ero, new File(outDir, "erosion_" + tag + ".png"));
        Img.save(anomalies, new File(outDir, "features_" + tag + ".png"));

        // Terrain-class proportions over land (relief within ~5 samples).
        Map<String, Double> stats = classify(h, n, step, sea, prov, overlay, wk, water);
        Map<PBiome, Integer> bcount = new EnumMap<>(PBiome.class);
        for (int b : biome) bcount.merge(PBiome.byOrdinal(b), 1, Integer::sum);
        try (PrintWriter pw = new PrintWriter(new File(outDir, "report_" + tag + ".txt"))) {
            pw.printf(Locale.ROOT, "seed %d centre %d %d size %d step %d erosion %b%n", seed, cx, cz, size, step, erosion);
            pw.printf(Locale.ROOT, "columns %d sampled in %.1fs (%d threads); domains %d; river segments %d%n", n * n, secs,
                    Runtime.getRuntime().availableProcessors(), ids.size(), segments);
            pw.printf(Locale.ROOT, "land fraction %.3f  max surface y %.0f%n", land / (double) (n * n), maxH);
            for (Map.Entry<String, Double> e : stats.entrySet()) pw.printf(Locale.ROOT, "  %-28s %.3f%n", e.getKey(), e.getValue());
            pw.println("biome sample counts:");
            bcount.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue())
                    .forEach(e -> pw.printf(Locale.ROOT, "  %-24s %.4f%n", e.getKey().key, e.getValue() / (double) (n * n)));
            pw.print(sites);
        }
        stats.put("maxY", maxH);
        stats.put("land", land / (double) (n * n));
        return stats;
    }

    /** Broad landform classes (for tuning toward the target proportions). */
    public static Map<String, Double> classify(double[] h, int n, int step, int sea, int[] prov, int[] overlay, byte[] wk, double[] water) {
        int R = Math.max(1, 80 / step);
        long plains = 0, hills = 0, mountains = 0, plateau = 0, unusual = 0, landN = 0;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                int k = j * n + i;
                boolean wet = !Double.isNaN(water[k]) && water[k] - 1 > h[k];
                if (wet || h[k] < sea) continue;
                landN++;
                double lo = 1e9, hi = -1e9;
                for (int dj = -R; dj <= R; dj += Math.max(1, R / 3)) for (int di = -R; di <= R; di += Math.max(1, R / 3)) {
                    int ii = Math.min(n - 1, Math.max(0, i + di)), jj = Math.min(n - 1, Math.max(0, j + dj));
                    lo = Math.min(lo, h[jj * n + ii]);
                    hi = Math.max(hi, h[jj * n + ii]);
                }
                double relief = hi - lo, elev = h[k] - sea;
                int ov = overlay[k];
                if (ov >= BaseTerrain.OVERLAY_VOLCANO_BASE && ov < BaseTerrain.OVERLAY_VOLCANO_BASE + 10 || ov >= Anomalies.OVERLAY_BASE) unusual++;
                else if (prov[k] == Province.PLATEAU.ordinal()) plateau++;
                else if (elev > 150 || relief > 90) mountains++;
                else if (elev > 60 || relief > 20) hills++;
                else plains++;
            }
        }
        Map<String, Double> m = new java.util.LinkedHashMap<>();
        double L = Math.max(1, landN);
        m.put("plains/rolling lowland", plains / L);
        m.put("hills/foothills", hills / L);
        m.put("mountains/highlands", mountains / L);
        m.put("plateau/badlands/escarp.", plateau / L);
        m.put("unusual (anomaly/volcano)", unusual / L);
        return m;
    }

    static int biomeColor(PBiome b) {
        return switch (b) {
            case ALLUVIAL_MEADOW -> 0x9ccc65; case FLOODPLAIN_WOODLAND -> 0x558b2f; case ROLLING_GRASSLAND -> 0xc5e1a5;
            case TEMPERATE_MIXED_FOREST -> 0x2e7d32; case BIRCH_UPLANDS -> 0x81c784; case CONIFER_FOOTHILLS -> 0x1b5e20;
            case BOREAL_FOREST -> 0x33691e; case ALPINE_MEADOW -> 0xaed581; case ROCKY_ALPINE_RIDGE -> 0x8d8d8d;
            case SNOWY_SUMMIT -> 0xffffff; case FROZEN_TUNDRA -> 0xd7e3e8; case WET_COASTAL_FOREST -> 0x00695c;
            case DRY_SCRUBLAND -> 0xbcaaa4; case STEPPE -> 0xd4c58a; case WARM_SAVANNA -> 0xc0ca33;
            case TROPICAL_RAINFOREST -> 0x1de9b6; case SANDY_DESERT -> 0xfff59d; case RED_BADLANDS -> 0xd84315;
            case KARST_COUNTRY -> 0xe0e0c0; case MARSH -> 0x4e6e5d; case MANGROVE_WETLAND -> 0x3e5f3a;
            case WINDSWEPT_PLATEAU -> 0xa1887f; case BASALT_HIGHLANDS -> 0x5d4037; case ASH_BARRENS -> 0x3e2723;
            case CRATER_BASIN -> 0x7e57c2; case SALT_FLATS -> 0xf5f5f5; case BARREN_FLATS -> 0xbdbdbd;
            case BEACH_DUNES -> 0xffe082; case STONY_SHORE -> 0x9e9e9e; case GLACIAL_VALLEY -> 0x80cbc4;
            case RIVER -> 0x1565c0; case FROZEN_RIVER -> 0x90caf9;
            default -> b.isOcean() ? 0x0d47a1 : 0x000000;
        };
    }
}
