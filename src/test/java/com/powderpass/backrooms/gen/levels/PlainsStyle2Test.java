package com.powderpass.backrooms.gen.levels;

import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.display.Model;
import com.powderpass.backrooms.display.ModelLibrary;
import com.powderpass.backrooms.gen.DecorSpec;
import com.powderpass.backrooms.gen.GenSettings;
import com.powderpass.backrooms.gen.Generators;
import com.powderpass.backrooms.gen.MemoryCanvas;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plains terrain style 2: moss only, flat fields or small hills, terraces forty blocks down with stairs that can be
 * climbed, exponential-horn craters and spikes, many houses with fences, rarer giants three times the size.
 */
class PlainsStyle2Test {
    private static final long SEED = 0x5EEDBACCL;
    private final GenSettings gs = PlainsRenderTest.style2();
    private final PlainsGenerator g = (PlainsGenerator) Generators.create(Level.PLAINS, SEED, gs);
    private final PlainsGenerator old = (PlainsGenerator) Generators.create(Level.PLAINS, SEED, GenSettings.defaults());
    private final Map<Long, MemoryCanvas> chunks = new HashMap<>();

    @BeforeAll
    static void up() {
        MockBukkit.mock();
    }

    @AfterAll
    static void down() {
        MockBukkit.unmock();
    }

    private Material type(int x, int y, int z) {
        long k = ((long) Math.floorDiv(x, 16) << 32) ^ (Math.floorDiv(z, 16) & 0xffffffffL);
        MemoryCanvas c = chunks.computeIfAbsent(k, kk -> {
            MemoryCanvas mc = new MemoryCanvas(-64, 200);
            g.paint(mc, Math.floorDiv(x, 16), Math.floorDiv(z, 16));
            return mc;
        });
        return c.type(Math.floorMod(x, 16), y, Math.floorMod(z, 16));
    }

    @Test
    void groundIsMossAndNothingElse() {
        int cols = 0;
        for (int x = -400; x < 400; x += 7) {
            for (int z = -400; z < 400; z += 7) {
                int k = g.kind(g.cellOf(x), g.cellOf(z));
                if (k != PlainsGenerator.K_FIELD && k != PlainsGenerator.K_STAIR) continue;
                int top = 64 + g.lift(x, z);
                assertEquals(Material.MOSS_BLOCK, type(x, top, z), "surface at " + x + "," + z);
                assertEquals(Material.MOSS_BLOCK, type(x, top - 8, z), "no stone or dirt below either at " + x + "," + z);
                Material above = type(x, top + 1, z);
                assertTrue(above == Material.AIR || above == Material.MOSS_CARPET || above.name().endsWith("_FENCE"), above + " at " + x + "," + z);
                cols++;
            }
        }
        assertTrue(cols > 5000, "sampled " + cols);
    }

    @Test
    void groundIsFlatOrSmallHills() {
        int flat = 0, hilly = 0, n = 0, max = 0;
        for (int x = -1500; x < 1500; x += 9) {
            for (int z = -1500; z < 1500; z += 9) {
                if (g.kind(g.cellOf(x), g.cellOf(z)) != PlainsGenerator.K_FIELD) continue;
                boolean low = g.low(Math.floorDiv(x, g.S * g.R), Math.floorDiv(z, g.S * g.R));
                int h = g.lift(x, z) + (low ? PlainsGenerator.TERRACE_DROP : 0);
                n++;
                if (h == 0) flat++;
                if (h >= 8) hilly++;
                max = Math.max(max, h);
                assertTrue(h >= 0 && h <= 20, "height " + h + " at " + x + "," + z);
            }
        }
        assertTrue(flat > n * 0.35, "flat " + flat + "/" + n);
        assertTrue(hilly > n * 0.03, "hilly " + hilly + "/" + n);
        assertTrue(max >= 15, "tallest hill " + max);
    }

    @Test
    void terracesDropFortyAndTheirStairsClimbToTheTop() {
        int lows = 0, stairs = 0;
        int span = g.S * g.R;
        for (int rx = -12; rx <= 12; rx++) {
            for (int rz = -12; rz <= 12; rz++) {
                if (!g.low(rx, rz)) continue;
                lows++;
                assertNull(g.regionHole(rx, rz), "no crater on a terrace");
                boolean any = false;
                for (int side = 0; side < 4; side++) {
                    int j = g.stairStart(rx, rz, side);
                    if (j < 0) continue;
                    any = true;
                    stairs++;
                    // walk the flight: centre line two blocks out from the cliff, one block up per step, ending level
                    // with the ground on the far side of the edge
                    int prev = Integer.MIN_VALUE;
                    for (int s = 0; s <= 40; s++) {
                        int along = j * g.S + 4 + s, e = 2;
                        int x = rx * span + switch (side) {
                            case 0, 1 -> along;
                            case 2 -> e;
                            default -> span - 1 - e;
                        };
                        int z = rz * span + switch (side) {
                            case 0 -> e;
                            case 1 -> span - 1 - e;
                            default -> along;
                        };
                        int h = g.lift(x, z);
                        if (prev != Integer.MIN_VALUE) assertTrue(Math.abs(h - prev) <= 1, "step " + prev + "->" + h + " at " + x + "," + z);
                        prev = h;
                        if (s == 40) {
                            int ox = x + (side == 2 ? -3 : side == 3 ? 3 : 0), oz = z + (side == 0 ? -3 : side == 1 ? 3 : 0);
                            assertTrue(Math.abs(h - g.lift(ox, oz)) <= 1, "top of the flight " + h + " vs " + g.lift(ox, oz));
                        }
                    }
                }
                boolean enclosed = g.low(rx + 1, rz) && g.low(rx - 1, rz) && g.low(rx, rz + 1) && g.low(rx, rz - 1);
                assertTrue(any || enclosed, "a terrace with an upper neighbour has stairs: " + rx + "," + rz);
            }
        }
        assertTrue(lows > 50 && stairs > 50, lows + " terraces, " + stairs + " flights");
        assertFalse(g.low(0, 0), "never where the world spawns");
    }

    @Test
    void cratersAreExponentialHornsAndSomePointUp() {
        int craters = 0, spikes = 0;
        for (int rx = -25; rx <= 25; rx++) {
            for (int rz = -25; rz <= 25; rz++) {
                PlainsGenerator.Hole h = g.regionHole(rx, rz);
                if (h == null) continue;
                boolean up = g.zoneName(0, g.cellOf((int) h.x()), g.cellOf((int) h.z())).equals("spike");
                if (up) spikes++;
                else craters++;
                if (craters > 3 && up == false) continue;
                int cx = (int) Math.floor(h.x()), cz = (int) Math.floor(h.z());
                if (up) {
                    // the column rises ever faster toward the middle
                    int prev = 64, prevRise = 0;
                    for (int d = (int) h.r() - 1; d >= 2; d--) {
                        int y = topAt(cx + d, cz);
                        assertTrue(y >= prev, "rising toward the tip");
                        assertTrue(y - prev >= prevRise - 1, "steepening");
                        prevRise = y - prev;
                        prev = y;
                    }
                    assertTrue(prev > 64 + 50, "tall: " + prev);
                } else {
                    int prev = 65;
                    for (int d = (int) h.r() - 1; d >= 3; d--) {
                        int y = topAt(cx + d, cz);
                        assertTrue(y <= prev, "deepening toward the throat");
                        assertEquals(Material.MOSS_BLOCK, type(cx + d, y, cz), "moss walls, no stone");
                        prev = y;
                    }
                    for (int y = -64; y < 70; y++) assertEquals(Material.AIR, type(cx, y, cz), "the throat is open to the void");
                }
            }
        }
        assertTrue(craters > 3, "craters " + craters);
        assertTrue(spikes >= 1, "spikes " + spikes);
    }

    private int topAt(int x, int z) {
        for (int y = 199; y >= -64; y--) if (type(x, y, z) != Material.AIR) return y;
        return -999;
    }

    @Test
    void manyMoreHousesAndFencesRoundThem() {
        int houses2 = 0, houses1 = 0, cells = 0, fenced = 0, checked = 0;
        for (int cx = -40; cx < 40; cx++) {
            for (int cz = -40; cz < 40; cz++) {
                cells++;
                if (old.kind(cx, cz) == PlainsGenerator.K_HOUSE) houses1++;
                if (g.kind(cx, cz) != PlainsGenerator.K_HOUSE) continue;
                houses2++;
                if (checked++ >= 25) continue;
                boolean fence = false;
                for (int u = 0; u < g.S && !fence; u++) for (int v = 0; v < g.S && !fence; v++) {
                    int x = cx * g.S + u, z = cz * g.S + v;
                    if (type(x, 64 + g.lift(x, z) + 1, z).name().endsWith("_FENCE")) fence = true;
                }
                if (fence) fenced++;
            }
        }
        assertTrue(houses2 > houses1 * 3, "style 2 houses " + houses2 + " vs " + houses1);
        assertTrue(houses2 > cells / 8, houses2 + " houses in " + cells + " cells");
        assertTrue(fenced >= 20, "fenced " + fenced);
    }

    @Test
    void giantsAreRarerAndThreeTimesTheSize() {
        int giants2 = 0, giants1 = 0;
        DecorSpec one = null;
        for (int cx = -60; cx < 60; cx++) {
            for (int cz = -60; cz < 60; cz++) {
                if (old.kind(cx, cz) == PlainsGenerator.K_COLOSSAL) giants1++;
                if (g.kind(cx, cz) == PlainsGenerator.K_COLOSSAL) giants2++;
            }
        }
        assertTrue(giants2 * 3 < giants1, giants2 + " vs " + giants1);
        outer:
        for (int cx = -60; cx < 60; cx++) for (int cz = -60; cz < 60; cz++) {
            if (g.kind(cx, cz) != PlainsGenerator.K_COLOSSAL) continue;
            for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++)
                for (DecorSpec s : g.decorations(cx * 2 + x, cz * 2 + z)) if (s.model.contains("@")) {
                    one = s;
                    break outer;
                }
        }
        assertNotNull(one);
        String base = one.model.substring(0, one.model.indexOf('@'));
        Model big = ModelLibrary.build(one.model, one.seed), small = ModelLibrary.build(base, one.seed);
        assertNotNull(big);
        assertEquals(3.0, height(big) / height(small), 0.01, one.model);
    }

    private static double height(Model m) {
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (var p : m.parts) {
            for (int i = 0; i < 8; i++) {
                var c = p.local.transformPosition(new org.joml.Vector3f(i & 1, (i >> 1) & 1, (i >> 2) & 1));
                lo = Math.min(lo, c.y);
                hi = Math.max(hi, c.y);
            }
        }
        return hi - lo;
    }
}
