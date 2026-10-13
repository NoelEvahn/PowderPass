package com.powderpass.backrooms.gen.levels;

import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.display.Model;
import com.powderpass.backrooms.display.ModelLibrary;
import com.powderpass.backrooms.display.Part;
import com.powderpass.backrooms.gen.DecorSpec;
import com.powderpass.backrooms.gen.Feature;
import com.powderpass.backrooms.gen.GenSettings;
import com.powderpass.backrooms.gen.Generators;
import com.powderpass.backrooms.gen.Mats;
import com.powderpass.backrooms.gen.MemoryCanvas;
import org.bukkit.Material;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Plains: empty fields, no roads or wires, sparse randomly placed houses, giant objects, and sinkholes about a
 * hundred blocks across that narrow into a shaft open to the void and never touch an exit or a safe spot.
 */
class PlainsLayoutTest {
    private static final long SEED = 0x5EEDBACCL;
    private final PlainsGenerator g = (PlainsGenerator) Generators.create(Level.PLAINS, SEED, GenSettings.defaults());

    @BeforeAll
    static void up() {
        MockBukkit.mock();
    }

    @AfterAll
    static void down() {
        MockBukkit.unmock();
    }

    /** Lazily painted full-height chunks. */
    private final class Chunks {
        final Map<Long, MemoryCanvas> map = new HashMap<>();

        Material type(int x, int y, int z) {
            long k = ((long) Math.floorDiv(x, 16) << 32) ^ (Math.floorDiv(z, 16) & 0xffffffffL);
            MemoryCanvas c = map.computeIfAbsent(k, kk -> {
                MemoryCanvas mc = new MemoryCanvas(-64, 160);
                g.paint(mc, Math.floorDiv(x, 16), Math.floorDiv(z, 16));
                return mc;
            });
            return c.type(Math.floorMod(x, 16), y, Math.floorMod(z, 16));
        }

        /** Highest non-passable block at or below {@code from}, or -999. */
        int top(int x, int z, int from) {
            for (int y = from; y >= -64; y--) if (!Mats.passable(type(x, y, z))) return y;
            return -999;
        }
    }

    @Test
    void noRoadsNoWiresJustFields() {
        Chunks ch = new Chunks();
        int path = 0, grassOrMoss = 0, cols = 0;
        for (int x = -256; x < 256; x += 2) {
            for (int z = -256; z < 256; z += 2) {
                int y = ch.top(x, z, 90);
                Material m = ch.type(x, y, z);
                cols++;
                if (m == Material.DIRT_PATH) path++;
                if (m == Material.GRASS_BLOCK || m == Material.MOSS_BLOCK) grassOrMoss++;
            }
        }
        assertEquals(0, path, "no roads or paths anywhere");
        assertTrue(grassOrMoss > cols * 0.75, "the Plains are mostly grass and moss: " + grassOrMoss + "/" + cols);
        for (int cx = -30; cx < 30; cx++) {
            for (int cz = -30; cz < 30; cz++) {
                for (DecorSpec d : g.decorations(cx, cz)) assertNotEquals("utility_pole", d.model, "no cable lines");
            }
        }
    }

    @Test
    void housesAreFewAndRandomlyPlaced() {
        int n = 48, houses = 0, lone = 0, groups = 0, rows = 0;
        Set<String> placements = new HashSet<>();
        Set<Integer> orientations = new HashSet<>();
        for (int cx = -n / 2; cx < n / 2; cx++) {
            for (int cz = -n / 2; cz < n / 2; cz++) {
                if (g.kind(cx, cz) != PlainsGenerator.K_HOUSE) continue;
                houses++;
                PlainsGenerator.House h = g.house(cx, cz);
                placements.add(h.bu() + "," + h.bv());
                orientations.add(h.o());
                int near = 0;
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if ((dx != 0 || dz != 0) && g.kind(cx + dx, cz + dz) == PlainsGenerator.K_HOUSE) near++;
                if (near == 0) lone++;
                if (near >= 2) groups++;
                // never lined up like a street: four in a row of cells is a rare accident, five never happens
                boolean rowX = true, rowZ = true;
                for (int k = 1; k < 4; k++) {
                    rowX &= g.kind(cx + k, cz) == PlainsGenerator.K_HOUSE;
                    rowZ &= g.kind(cx, cz + k) == PlainsGenerator.K_HOUSE;
                }
                if (rowX || rowZ) rows++;
                assertFalse(rowX && g.kind(cx + 4, cz) == PlainsGenerator.K_HOUSE || rowZ && g.kind(cx, cz + 4) == PlainsGenerator.K_HOUSE,
                        "houses in a street-like row at cell " + cx + "," + cz);
            }
        }
        double share = houses / (double) (n * n);
        System.out.printf("plains houses: %d in %d cells (%.1f%%), lone %d, in groups %d, %d placements, %d runs of four%n", houses, n * n, share * 100, lone, groups, placements.size(), rows);
        assertTrue(share > 0.02 && share < 0.12, "houses are rare: " + share);
        assertTrue(lone > 5, "lone houses scattered in the fields: " + lone);
        assertTrue(groups > 3, "small groups of houses: " + groups);
        assertEquals(4, orientations.size(), "houses face every way");
        assertTrue(placements.size() > houses / 4, "houses sit at varied spots in their cells, not on a grid: " + placements.size());
    }

    @Test
    void giantObjectsStandInTheFields() {
        int giants = 0, regions = 0;
        Set<String> kinds = new HashSet<>();
        for (int rx = -8; rx < 8; rx++) {
            for (int rz = -8; rz < 8; rz++) {
                regions++;
                for (int i = 0; i < g.R * g.R; i++) {
                    int cx = rx * g.R + i % g.R, cz = rz * g.R + i / g.R;
                    if (g.kind(cx, cz) == PlainsGenerator.K_COLOSSAL) {
                        giants++;
                        kinds.add(g.colossusModel(cx, cz));
                    }
                }
            }
        }
        System.out.printf("plains giants: %.2f per 128x128 region, %d kinds%n", giants / (double) regions, kinds.size());
        assertTrue(giants / (double) regions > 0.8, "giant objects are frequent");
        assertTrue(kinds.size() >= 12, "a varied catalogue in view: " + kinds);
        // the new everyday objects are roughly forty blocks tall and within the entity budget
        for (String id : PlainsGenerator.COLOSSI) {
            if (!id.startsWith("giant_") || id.equals("giant_figure") || id.equals("giant_outlet") || id.equals("giant_stairs")) continue;
            Model m = ModelLibrary.build(id, 7);
            assertNotNull(m, id);
            assertTrue(m.entityCount() <= 32, id + " uses " + m.entityCount() + " entities");
            float top = 0;
            for (Part p : m.parts) {
                for (int i = 0; i < 8; i++) top = Math.max(top, p.local.transformPosition(new Vector3f(i & 1, (i >> 1) & 1, (i >> 2) & 1)).y);
            }
            assertTrue(top >= 14, id + " is only " + top + " tall");
            assertTrue(m.viewRange >= 8, id + " must be visible from far");
        }
    }

    private List<PlainsGenerator.Hole> holes(int regions) {
        List<PlainsGenerator.Hole> out = new ArrayList<>();
        for (int rx = -regions; rx <= regions; rx++) for (int rz = -regions; rz <= regions; rz++) {
            PlainsGenerator.Hole h = g.regionHole(rx, rz);
            if (h != null) out.add(h);
        }
        return out;
    }

    @Test
    void sinkholesAreNearbyWideAtTheTopAndOpenIntoTheVoid() {
        List<PlainsGenerator.Hole> hs = holes(6);
        assertTrue(hs.size() >= 3, "sinkholes within ~800 blocks: " + hs.size());
        Chunks ch = new Chunks();
        int checked = 0;
        for (PlainsGenerator.Hole h : hs) {
            if (checked++ >= 3) break;
            int hx = (int) Math.floor(h.x()), hz = (int) Math.floor(h.z());
            // open straight down through the bottom of the world
            for (int y = 90; y >= -64; y--) assertTrue(Mats.passable(ch.type(hx, y, hz)), "shaft blocked at y=" + y);
            // width at the surface and near the bottom, along both axes
            for (int[] dir : new int[][]{{1, 0}, {0, 1}}) {
                int top = width(ch, hx, hz, dir, 63), bottom = width(ch, hx, hz, dir, -63), mid = width(ch, hx, hz, dir, 0);
                System.out.printf("sinkhole at %d,%d: width %d at the surface, %d at y=0, %d at the bottom%n", hx, hz, top, mid, bottom);
                assertTrue(top >= 80 && top <= 118, "about a hundred blocks wide at the top: " + top);
                assertTrue(bottom >= 3 && bottom <= 10, "about five wide at the bottom: " + bottom);
                assertTrue(mid < top && bottom < mid, "narrows with depth");
            }
            // stepping off the rim anywhere is a lethal fall (or straight into the void)
            for (int a = 0; a < 48; a++) {
                double ang = a * Math.PI * 2 / 48;
                int prevX = hx, prevZ = hz;
                for (double r = 0; r < h.maxRim() + 6; r += 0.5) {
                    int x = (int) Math.floor(h.x() + Math.cos(ang) * r), z = (int) Math.floor(h.z() + Math.sin(ang) * r);
                    int t = ch.top(x, z, 80);
                    if (t >= 55) {
                        int inside = ch.top(prevX, prevZ, t + 1);
                        assertTrue(t - inside >= 23, "survivable step off the rim at " + x + "," + z + ": drop " + (t - inside));
                        break;
                    }
                    prevX = x;
                    prevZ = z;
                }
            }
        }
    }

    private static int width(Chunks ch, int hx, int hz, int[] d, int y) {
        int w = 1;
        for (int s = -1; s <= 1; s += 2) {
            for (int k = 1; k < 80; k++) {
                if (!Mats.passable(ch.type(hx + d[0] * k * s, y, hz + d[1] * k * s))) break;
                w++;
            }
        }
        return w;
    }

    @Test
    void sinkholesNeverTouchFeaturesOrSafeSpots() {
        List<PlainsGenerator.Hole> hs = holes(12);
        assertTrue(hs.size() > 10, "enough sinkholes to check: " + hs.size());
        for (PlainsGenerator.Hole h : hs) {
            double reach = h.maxRim() + 6;
            assertFalse(Math.abs(h.x() - 64) < 64 && Math.abs(h.z() - 64) < 64, "no sinkhole in the spawn region");
            for (Feature f : g.featuresNear((int) h.x(), (int) h.z(), 220)) {
                // nearest point of the feature's cell to the hole centre
                double nx = Math.max(f.cx() * g.S, Math.min(h.x(), f.cx() * g.S + g.S)), nz = Math.max(f.cz() * g.S, Math.min(h.z(), f.cz() * g.S + g.S));
                assertTrue(Math.hypot(nx - h.x(), nz - h.z()) > reach + 20, f.id() + " too close to a sinkhole");
            }
            for (int cx = g.cellOf((int) (h.x() - reach - 40)); cx <= g.cellOf((int) (h.x() + reach + 40)); cx++) {
                for (int cz = g.cellOf((int) (h.z() - reach - 40)); cz <= g.cellOf((int) (h.z() + reach + 40)); cz++) {
                    if (!g.isSafeCell(0, cx, cz)) continue;
                    int[] l = g.safeLocal(0, cx, cz);
                    double d = Math.hypot(cx * g.S + l[0] + 0.5 - h.x(), cz * g.S + l[1] + 0.5 - h.z());
                    assertTrue(d > reach, "safe spot inside a sinkhole's area at cell " + cx + "," + cz);
                }
            }
            int[] s = g.safeSpot((int) h.x(), (int) h.z(), 0);
            assertTrue(Math.hypot(s[0] - h.x(), s[2] - h.z()) > reach, "arrival spot search escapes the sinkhole");
        }
    }

    @Test
    void groundRollsGentlyAndNeverStepsMoreThanOneBlock() {
        int raised = 0, total = 0;
        for (int x = -400; x < 400; x++) {
            for (int z = -400; z < 400; z++) {
                int a = g.lift(x, z);
                total++;
                if (a > 0) raised++;
                assertTrue(Math.abs(a - g.lift(x + 1, z)) <= 1 && Math.abs(a - g.lift(x, z + 1)) <= 1, "cliff at " + x + "," + z);
            }
        }
        assertTrue(raised > total * 0.1 && raised < total * 0.7, "subtle swells, not a flat sheet: " + raised + "/" + total);
    }
}
