package com.powderpass.backrooms.gen;

import com.powderpass.backrooms.gen.levels.PoolroomsGenerator;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Generated water slides: they exist when switched on, never in older worlds, their riders' path is clear the whole
 * way, the platform can be climbed, they land in deep water, a pocket can be walked out of, and boring the tunnel
 * never lets water out.
 */
class PoolroomsSlideTest {
    private static final long SEED = 0x5EEDBACCL;
    private static final int REG = 60;

    private static PoolroomsGenerator gen(double storey, double fun) {
        return new PoolroomsGenerator(SEED, new GenSettings(Map.of("slide-chance", storey, "fun-slide-chance", fun)));
    }

    private record Found(int rx, int rz, PoolroomsGenerator.Slide slide) {
    }

    private static List<Found> slides(PoolroomsGenerator g, int radius) {
        List<Found> out = new ArrayList<>();
        for (int rx = -radius; rx <= radius; rx++) for (int rz = -radius; rz <= radius; rz++) {
            for (PoolroomsGenerator.Slide s : g.generatedSlides(rx, rz)) out.add(new Found(rx, rz, s));
        }
        return out;
    }

    @Test
    void olderWorldsHaveNone() {
        // older worlds have no slide settings in their locked snapshot: no slides, and not one block changes
        PoolroomsGenerator old = new PoolroomsGenerator(SEED, GenSettings.defaults()), zero = gen(0, 0);
        assertTrue(slides(old, 6).isEmpty());
        for (int i = 0; i < 400; i++) {
            int x = i * 37 - 3000, z = i * 53 - 4000;
            assertArrayEquals(old.column(x, z), zero.column(x, z), "column " + x + "," + z);
        }
    }

    @Test
    void manySlidesOfEveryStyle() {
        List<Found> all = slides(gen(0.6, 0.5), 6);
        assertTrue(all.size() >= 20, "expected plenty of slides, found " + all.size());
        java.util.Set<String> styles = new java.util.HashSet<>();
        int dry = 0, storey = 0;
        for (Found f : all) {
            styles.add(f.slide.style());
            if (!f.slide.wet()) dry++;
            double[][] p = f.slide.points();
            if (p[0][1] - p[p.length - 1][1] > 20) storey++;
        }
        assertEquals(3, styles.size(), "open, half and enclosed slides: " + styles);
        assertTrue(dry > 0, "some slides run dry");
        assertTrue(storey > 0, "some slides go down a storey");
    }

    @Test
    void ridersPathIsClearAndLandsInDeepWater() {
        PoolroomsGenerator g = gen(0.6, 0.5);
        int checked = 0;
        for (Found f : slides(g, 3)) {
            double[][] p = f.slide.points();
            int n = p.length - 2; // the last two are the throw over the pool
            for (int i = 1; i < n - 3; i++) {
                int x = (int) Math.floor(p[i][0]), z = (int) Math.floor(p[i][2]);
                B[] col = g.column(x, z);
                for (double dy : new double[]{0.3, 1.0, 1.5}) {
                    int y = (int) Math.floor(p[i][1] + dy);
                    B b = col[y - g.bottomY];
                    assertTrue(b.passable() && b.mat != Material.WATER, "slide at region " + f.rx + "," + f.rz + " blocked by " + b.mat
                            + " at point " + i + " (" + x + "," + y + "," + z + ")");
                }
            }
            double[] d = f.slide.exitDir();
            double[] end = p[n - 1];
            for (int k = 1; k <= 3; k++) {
                int x = (int) Math.floor(end[0] + d[0] * k), z = (int) Math.floor(end[2] + d[2] * k);
                B[] col = g.column(x, z);
                for (int dd = 0; dd < 3; dd++) {
                    assertEquals(Material.WATER, col[f.slide.landingWaterY() - dd - g.bottomY].mat,
                            "landing pool too shallow at " + x + "," + z + " for the slide in region " + f.rx + "," + f.rz);
                }
            }
            // the start is found by the trigger query
            int[] st = f.slide.start()[0];
            assertSame(f.slide, g.slideStartingAt(st[0], st[1], st[2]));
            checked++;
        }
        assertTrue(checked >= 5, "too few slides checked: " + checked);
    }

    @Test
    void platformsCanBeClimbedAndPocketsLeft() {
        PoolroomsGenerator g = gen(0.6, 0.5);
        int climbed = 0;
        for (Found f : slides(g, 2)) {
            int[] st = f.slide.start()[0];
            Area a = new Area(g, st[0] - 20, st[2] - 20, st[0] + 21, st[2] + 21);
            Walker w = new Walker(a, g.bottomY, g.roofY);
            int floorFeet = g.floorY[g.floorAt(st[1] - 2)] + 1;
            // start somewhere on the floor round the platform
            int[] from = null;
            for (int r = 3; r <= 12 && from == null; r++) {
                for (int[] c : new int[][]{{st[0] + r, st[2]}, {st[0] - r, st[2]}, {st[0], st[2] + r}, {st[0], st[2] - r}}) {
                    if (a.standable(c[0], floorFeet, c[1])) {
                        from = c;
                        break;
                    }
                }
            }
            if (from == null) continue;
            w.run(from[0], floorFeet, from[1]);
            if (w.visited(st[0], st[1], st[2])) climbed++;
        }
        assertTrue(climbed >= 4, "too few slide platforms reachable on foot: " + climbed);
    }

    @Test
    void boringTunnelsNeverLetWaterOut() {
        PoolroomsGenerator off = gen(0, 0), on = gen(0.6, 0.5);
        for (Found f : slides(on, 1)) {
            double[][] p = f.slide.points();
            int x0 = (int) Math.floor(p[0][0]) - 26, z0 = (int) Math.floor(p[0][2]) - 26;
            int leaksOff = leaks(off, x0, z0), leaksOn = leaks(on, x0, z0);
            assertTrue(leaksOn <= leaksOff, "slide in region " + f.rx + "," + f.rz + " opened " + (leaksOn - leaksOff) + " leaks");
        }
    }

    /** Water blocks with air beside or below them (water that would flow) in a 52 x 52 area. */
    private static int leaks(PoolroomsGenerator g, int x0, int z0) {
        B[][] cols = new B[54 * 54][];
        for (int i = 0; i < 54; i++) for (int j = 0; j < 54; j++) cols[i * 54 + j] = g.column(x0 - 1 + i, z0 - 1 + j);
        int n = 0;
        for (int i = 1; i < 53; i++) {
            for (int j = 1; j < 53; j++) {
                B[] c = cols[i * 54 + j];
                for (int y = 1; y < c.length - 1; y++) {
                    if (c[y].mat != Material.WATER) continue;
                    if (c[y - 1] == B.AIR || cols[(i + 1) * 54 + j][y] == B.AIR || cols[(i - 1) * 54 + j][y] == B.AIR
                            || cols[i * 54 + j + 1][y] == B.AIR || cols[i * 54 + j - 1][y] == B.AIR) n++;
                }
            }
        }
        return n;
    }
}
