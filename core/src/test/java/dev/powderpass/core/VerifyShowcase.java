package dev.powderpass.core;

import dev.powderpass.core.anomaly.Anomaly;
import dev.powderpass.core.biome.BiomeClassifier;
import dev.powderpass.core.deco.Decorator;
import dev.powderpass.core.deco.Placements;
import dev.powderpass.core.terrain.ChunkComposer;
import dev.powderpass.core.terrain.TerrainSampler;
import dev.powderpass.core.volcano.VolcanoChannels;
import dev.powderpass.core.volcano.VolcanoSite;

import java.util.Locale;

/** Finds showcase locations and checks each claim numerically against generator output. */
public final class VerifyShowcase {
    public static void main(String[] a) {
        long seed = Long.parseLong(a[0]);
        PowderWorld w = new PowderWorld(seed, GenSettings.defaults());
        TerrainSampler ts = new TerrainSampler(w);
        BiomeClassifier bc = new BiomeClassifier(w);
        Diagnostics d = new Diagnostics(w, ts, bc);
        ChunkComposer comp = new ChunkComposer(w, -64, 832);
        Decorator deco = new Decorator(comp);
        TerrainSampler.Column c = new TerrainSampler.Column();
        System.out.println("seed " + seed);
        Diagnostics.Pos sp = d.findSpawn();
        ts.sample(sp.x(), sp.z(), c);
        System.out.printf(Locale.ROOT, "spawn %d %d %d biome %s slope %.2f water %s%n", sp.x(), sp.y(), sp.z(), bc.surface(c).key, c.gridSlope, c.waterLevel);

        // Broad plain: low relief over a 200-block window.
        Diagnostics.Pos pl = d.locate(Diagnostics.Landform.PLAIN, 0, 0, 40000);
        if (pl != null) {
            double lo = 1e9, hi = -1e9;
            for (int dz = -100; dz <= 100; dz += 10) for (int dx = -100; dx <= 100; dx += 10) {
                ts.sample(pl.x() + dx, pl.z() + dz, c);
                lo = Math.min(lo, c.height); hi = Math.max(hi, c.height);
            }
            System.out.printf(Locale.ROOT, "plain %d %d %d relief over 200x200: %.1f blocks%n", pl.x(), pl.y(), pl.z(), hi - lo);
        }
        // River-carved mountain valley: incision depth vs pre-erosion surface and valley-side heights.
        Diagnostics.Pos mv = d.locate(Diagnostics.Landform.MOUNTAIN_VALLEY, 0, 0, 40000);
        if (mv != null) {
            ts.sample(mv.x(), mv.z(), c);
            double floor = c.height, ero = c.erosionDepth, q = c.riverQ, wl = c.waterLevel;
            double side = 0;
            for (int k = 0; k < 8; k++) {
                double ang = k * Math.PI / 4;
                ts.sample(mv.x() + Math.cos(ang) * 60, mv.z() + Math.sin(ang) * 60, c);
                side = Math.max(side, c.height);
            }
            System.out.printf(Locale.ROOT, "mountain valley %d %d %d: river surface %.0f, floor %.0f, eroded %.1f blocks here, valley side +%.0f within 60 blocks, discharge %.3g%n",
                    mv.x(), mv.y(), mv.z(), wl, floor, ero, side - floor, q);
        }
        Diagnostics.Pos ht = d.locate(Diagnostics.Landform.HILL_TRANSITION, 0, 0, 12000);
        if (ht != null) {
            ts.sample(ht.x(), ht.z(), c);
            String b0 = bc.surface(c).key;
            String other = null;
            for (int r = 32; r <= 320 && other == null; r += 32) for (int k = 0; k < 8 && other == null; k++) {
                double ang = k * Math.PI / 4;
                ts.sample(ht.x() + Math.cos(ang) * r, ht.z() + Math.sin(ang) * r, c);
                String b = bc.surface(c).key;
                if (!b.equals(b0)) other = b + " at " + r + " blocks";
            }
            System.out.printf(Locale.ROOT, "hilly biome transition %d %d %d: %s -> %s%n", ht.x(), ht.y(), ht.z(), b0, other);
        }
        // Slab-detailed rock slope: count slabs placed in the chunk.
        Diagnostics.Pos rs = d.locate(Diagnostics.Landform.ROCK_SLOPE, 0, 0, 12000);
        int bestSlabs = 0; int[] bestPos = null;
        if (rs != null) {
            for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) {
                int cx = Math.floorDiv(rs.x(), 16) + dx, cz = Math.floorDiv(rs.z(), 16) + dz;
                Placements p = deco.decorate(cx, cz, comp.compose(cx, cz, false));
                int slabs = 0, sx = 0, sz = 0, sy = 0;
                for (int i = 0; i < p.size; i++) if (Placements.state(p.st[i]).contains("_slab")) { slabs++; sx = p.x[i]; sy = p.y[i]; sz = p.z[i]; }
                if (slabs > bestSlabs) { bestSlabs = slabs; bestPos = new int[]{sx, sy, sz}; }
            }
            if (bestPos != null) System.out.printf(Locale.ROOT, "slab-detailed rock slope near %d %d %d: %d slabs in best chunk%n", bestPos[0], bestPos[1], bestPos[2], bestSlabs);
        }
        Diagnostics.Pos fs = d.locate(Diagnostics.Landform.FAULT_SCARP, 0, 0, 20000);
        Anomaly esc = d.nearestAnomaly(0, 0, Anomaly.Type.FAULT_ESCARPMENT, 64000);
        if (fs != null) {
            ts.sample(fs.x(), fs.z(), c);
            System.out.printf(Locale.ROOT, "fault scarp (tectonic fault trace) %d %d %d: scarp offset %.1f, fault distance %.0f%n", fs.x(), fs.y(), fs.z(), c.geo.faultScarp, c.geo.faultDist);
        }
        if (esc != null) {
            double ca = Math.cos(esc.angle()), sa = Math.sin(esc.angle());
            ts.sample(esc.x() - sa * 40, esc.z() + ca * 40, c);
            double up = c.height;
            ts.sample(esc.x() + sa * 40, esc.z() - ca * 40, c);
            double dn = c.height;
            System.out.printf(Locale.ROOT, "straight fault escarpment %d %d (length %d): step across trace %.1f blocks%n", (int) esc.x(), (int) esc.z(), (int) (2 * esc.radius()), up - dn);
        }
        VolcanoSite v = d.nearestVolcano(0, 0, true, 32000);
        if (v != null) {
            VolcanoChannels.Geometry g = ts.volcanoChannels().geometry(v);
            System.out.printf(Locale.ROOT, "working volcano %s at %d %d: vent at y %d, %d lava channels, radius %d%n", v.type(), (int) v.x(), (int) v.z(), g.ventY(), g.channels().length, (int) v.radius());
        }
        Anomaly flat = d.nearestAnomaly(0, 0, Anomaly.Type.SALT_FLAT, 64000);
        if (flat != null) {
            double lo = 1e9, hi = -1e9; int n = 0;
            for (int dz = -400; dz <= 400; dz += 8) for (int dx = -400; dx <= 400; dx += 8) {
                if (Math.hypot(dx, dz) > flat.radius() * 0.6) continue;
                ts.sample(flat.x() + dx, flat.z() + dz, c);
                lo = Math.min(lo, Math.floor(c.height)); hi = Math.max(hi, Math.floor(c.height)); n++;
            }
            System.out.printf(Locale.ROOT, "flat basin (salt flat) %d %d radius %d: %d samples within 0.6R, top-block y range %.0f..%.0f%n", (int) flat.x(), (int) flat.z(), (int) flat.radius(), n, lo, hi);
        }
        Anomaly cr = d.nearestAnomaly(0, 0, Anomaly.Type.IMPACT_CRATER, 64000);
        if (cr != null) {
            ts.sample(cr.x(), cr.z(), c);
            double floor = c.height;
            double rim = 0;
            for (int k = 0; k < 16; k++) {
                double ang = k * Math.PI / 8;
                ts.sample(cr.x() + Math.cos(ang) * cr.radius(), cr.z() + Math.sin(ang) * cr.radius(), c);
                rim = Math.max(rim, c.height);
            }
            System.out.printf(Locale.ROOT, "impact crater %d %d radius %d: floor y %.0f, rim max y %.0f, depth %.0f%n", (int) cr.x(), (int) cr.z(), (int) cr.radius(), floor, rim, rim - floor);
        }
        Anomaly pit = d.nearestAnomaly(0, 0, Anomaly.Type.DEEP_PIT, 64000);
        if (pit != null) {
            ts.sample(pit.x(), pit.z(), c);
            double floor = c.height;
            ts.sample(pit.x() + pit.radius() * 1.3, pit.z(), c);
            System.out.printf(Locale.ROOT, "deep pit %d %d radius %d: floor y %.0f (bedrock floor kept at y<=-60), surrounding y %.0f, depth %.0f%n", (int) pit.x(), (int) pit.z(), (int) pit.radius(), floor, c.height, c.height - floor);
        }
    }
}
