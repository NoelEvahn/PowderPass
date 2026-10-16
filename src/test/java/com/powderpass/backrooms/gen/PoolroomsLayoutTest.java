package com.powderpass.backrooms.gen;

import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.gen.levels.PoolroomsGenerator;
import com.powderpass.backrooms.gen.levels.PoolroomsGenerator.Zone;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The Poolrooms: three stacked storeys tied by stair towers, five kinds of space, working slides, arrivals in the middle. */
class PoolroomsLayoutTest {
    private static final long SEED = 0x5EEDBACCL;
    private final PoolroomsGenerator g = (PoolroomsGenerator) Generators.create(Level.POOLROOMS, SEED, GenSettings.defaults());

    @Test
    void threeStoreysAndEveryoneArrivesOnTheMainFloor() {
        assertEquals(3, g.floorCount());
        for (int f = 1; f < 3; f++) assertTrue(g.floorY[f] - g.floorY[f - 1] >= 28, "storeys must be far apart");
        assertTrue(g.roofY - g.floorY[2] >= 28, "room above the upper floor");
        for (int i = 0; i < 6; i++) {
            int x = i * 397 - 900, z = 611 - i * 263;
            int[] s = g.safeSpot(x, z, 0);
            assertEquals(g.floorY[PoolroomsGenerator.MID] + 1, s[1], "arrival must be on the main floor");
            Area a = new Area(g, s[0] - 4, s[2] - 4, s[0] + 4, s[2] + 4);
            assertTrue(a.standable(s[0], s[1], s[2]));
        }
    }

    @Test
    void everyKindOfSpaceAppears() {
        List<Map<Zone, Integer>> perFloor = new ArrayList<>();
        Map<Zone, Integer> all = new EnumMap<>(Zone.class);
        for (int f = 0; f < 3; f++) {
            Map<Zone, Integer> seen = new EnumMap<>(Zone.class);
            for (int rx = -10; rx <= 10; rx++) for (int rz = -10; rz <= 10; rz++) seen.merge(g.plan(rx, rz).zone[f], 1, Integer::sum);
            perFloor.add(seen);
            seen.forEach((k, v) -> all.merge(k, v, Integer::sum));
        }
        assertEquals(Zone.values().length, all.size(), "every kind of space: " + all);
        for (Zone z : new Zone[]{Zone.BAYS, Zone.DRY, Zone.BRIGHT, Zone.HALLWAY}) {
            for (int f = 0; f < 3; f++) assertTrue(perFloor.get(f).getOrDefault(z, 0) > 0, z + " missing on floor " + f + ": " + perFloor.get(f));
        }
        assertEquals(0, perFloor.get(0).getOrDefault(Zone.MASSIVE, 0), "massive halls start on the main floor");
        int classic = all.getOrDefault(Zone.BAYS, 0) + all.getOrDefault(Zone.DRY, 0);
        assertTrue(classic > 3 * 441 / 5 && classic < 3 * 441 * 3 / 5, "plain bays are common but no longer everything: " + all);
        int custom = 0;
        for (Zone z : new Zone[]{Zone.ENCLOSED, Zone.LONGPOOL, Zone.FLOODED, Zone.CORRIDORS, Zone.CURVED, Zone.MULTILEVEL}) custom += all.getOrDefault(z, 0);
        assertTrue(custom > 3 * 441 / 4, "the new architecture is a large share: " + all);
    }

    @Test
    void stairTowersConnectAllThreeFloors() {
        int checked = 0;
        for (int rx = -3; rx <= 3 && checked < 4; rx++) {
            for (int rz = -3; rz <= 3 && checked < 4; rz++) {
                PoolroomsGenerator.Plan p = g.plan(rx, rz);
                if (p.zone[PoolroomsGenerator.MID] == Zone.MASSIVE) continue;
                int x0 = p.towerCx * g.S, z0 = p.towerCz * g.S;
                Area a = new Area(g, x0 - 2, z0 - 2, x0 + g.S + 2, z0 + g.S + 2);
                Walker w = new Walker(a, g.bottomY, g.roofY);
                // just outside each door, on the floor of each storey
                int[][] doors = new int[3][];
                for (int f = 0; f < 3; f++) doors[f] = new int[]{x0 + 1, g.floorY[f] + 1, z0 + 3};
                w.run(doors[0][0], doors[0][1], doors[0][2]);
                for (int f = 1; f < 3; f++) {
                    assertTrue(w.visited(doors[f][0], doors[f][1], doors[f][2]),
                            "tower at cell " + p.towerCx + "," + p.towerCz + " does not reach floor " + f);
                }
                // and the stairs really are stairs
                int stairs = 0;
                for (int y = g.floorY[0]; y <= g.floorY[2]; y++) if (a.type(x0 + 6, y, z0 + 3) == Material.QUARTZ_STAIRS) stairs++;
                assertTrue(stairs >= 4, "a flight of quartz stairs in the tower");
                checked++;
            }
        }
        assertTrue(checked >= 3, "no towers checked");
    }

    @Test
    void slidesStartHighRunClearAndLandInDeepWater() {
        int slides = 0;
        for (int rx = -8; rx <= 8; rx++) {
            for (int rz = -8; rz <= 8; rz++) {
                PoolroomsGenerator.Slide s = g.slide(rx, rz);
                if (s == null) continue;
                slides++;
                Area a = new Area(g, rx * 60, rz * 60, rx * 60 + 60, rz * 60 + 60);
                double[][] pts = s.points();
                assertTrue(pts[0][1] >= g.floorY[PoolroomsGenerator.HIGH] + 1, "a slide starts at the upper floor");
                assertTrue(pts[pts.length - 1][1] < pts[0][1] - 20, "and drops a long way");
                for (double[] q : pts) {
                    int x = (int) Math.floor(q[0]), z = (int) Math.floor(q[2]);
                    for (double dy : new double[]{0.05, 1.0, 1.8}) {
                        Material m = a.type(x, (int) Math.floor(q[1] + dy), z);
                        assertTrue(Mats.passable(m) && m != Material.WATER || dy == 0.05 && m == Material.WATER,
                                "slide path blocked by " + m + " at " + x + "," + (q[1] + dy) + "," + z);
                    }
                }
                // the chute bed is directly below the path, right under the rider
                for (int i = 0; i < pts.length - 2; i++) {
                    int x = (int) Math.floor(pts[i][0]), z = (int) Math.floor(pts[i][2]);
                    int y = (int) Math.floor(pts[i][1]);
                    boolean bed = false;
                    for (int d = 1; d <= 2; d++) bed |= !Mats.passable(a.type(x, y - d, z));
                    assertTrue(bed, "no chute under the path at " + x + "," + y + "," + z);
                }
                // landing: deep water ahead of the end of the chute
                double[] end = pts[pts.length - 1], dir = s.exitDir();
                int wy = s.landingWaterY();
                for (int k = 0; k <= 4; k++) {
                    int x = (int) Math.floor(end[0] + dir[0] * k), z = (int) Math.floor(end[2] + dir[2] * k);
                    for (int d = 0; d < 3; d++) assertEquals(Material.WATER, a.type(x, wy - d, z), "landing pool too shallow at " + x + "," + z);
                }
                // the start is found by the trigger query and is at the gallery
                int[] st = s.start()[0];
                assertSame(s, g.slideStartingAt(st[0], st[1], st[2]));
            }
        }
        assertTrue(slides >= 2, "expected slides near the origin, found " + slides);
    }

    @Test
    void slideStartIsReachableOnFoot() {
        // find the nearest massive hall and walk from the main floor to the top of its slide
        for (int r = 0; r < 10; r++) {
            for (int rx = -r; rx <= r; rx++) for (int rz = -r; rz <= r; rz++) {
                if (Math.max(Math.abs(rx), Math.abs(rz)) != r) continue;
                PoolroomsGenerator.Slide s = g.slide(rx, rz);
                if (s == null) continue;
                Area a = new Area(g, (rx - 1) * 60, (rz - 1) * 60, (rx + 2) * 60, (rz + 2) * 60);
                int[] start = g.safeSpot(rx * 60 + 30, rz * 60 + 30, 1);
                Walker w = new Walker(a, g.bottomY, g.roofY);
                w.run(start[0], start[1], start[2]);
                int[] st = s.start()[0];
                assertTrue(w.anyVisited(st[0] - 1, st[1], st[2] - 1, st[0] + 1, st[1], st[2] + 1), "slide start not reachable on foot");
                return;
            }
        }
        fail("no massive hall near the origin");
    }

    @Test
    void columnsAreDeterministicAndLit() {
        PoolroomsGenerator other = (PoolroomsGenerator) Generators.create(Level.POOLROOMS, SEED, GenSettings.defaults());
        int lights = 0;
        for (int i = 0; i < 64 * 64; i++) {
            int x = i % 64 - 32, z = i / 64 - 32;
            B[] a = g.column(x, z), b = other.column(x, z);
            assertArrayEquals(a, b);
            for (B blk : a) {
                assertFalse(blk.mat == Material.SEA_LANTERN || blk.mat == Material.GLOWSTONE || blk.mat == Material.LANTERN
                        || blk.mat.name().contains("FROGLIGHT") || blk.mat == Material.SHROOMLIGHT || blk.mat == Material.END_ROD,
                        "vanilla light source " + blk);
                if (blk.mat == Material.LIGHT) lights++;
            }
        }
        assertTrue(lights > 40, "too few lights: " + lights);
    }

    @Test
    void darkStoreysAreRareAndAlmostUnlit() {
        int dark = 0, total = 0;
        int[] darkRegion = null;
        for (int rx = -12; rx <= 12; rx++) for (int rz = -12; rz <= 12; rz++) for (int f = 0; f < 3; f++) {
            total++;
            if (g.plan(rx, rz).dark[f]) {
                dark++;
                if (darkRegion == null) darkRegion = new int[]{rx, rz, f};
            }
        }
        assertTrue(dark > 0 && dark < total * 0.12, "dark storeys are uncommon: " + dark + "/" + total);
        int lo = g.floorY[darkRegion[2]] - 8, hi = g.floorY[darkRegion[2]] + 22, lights = 0;
        for (int x = darkRegion[0] * 60; x < darkRegion[0] * 60 + 60; x++) for (int z = darkRegion[1] * 60; z < darkRegion[1] * 60 + 60; z++) {
            if (g.cellOf(x) == g.plan(darkRegion[0], darkRegion[1]).towerCx && g.cellOf(z) == g.plan(darkRegion[0], darkRegion[1]).towerCz) continue;
            B[] col = g.column(x, z);
            for (int y = lo; y <= hi; y++) if (col[y - g.bottomY].mat == Material.LIGHT) lights++;
        }
        assertTrue(lights < 40, "a dark storey keeps only a few isolated lights: " + lights);
    }

    @Test
    void someUpperHallsAreOpenToTheSky() {
        int open = 0, checked = 0;
        for (int rx = -12; rx <= 12 && checked < 6; rx++) for (int rz = -12; rz <= 12 && checked < 6; rz++) {
            var p = g.plan(rx, rz);
            if (!p.open[PoolroomsGenerator.HIGH]) continue;
            open++;
            checked++;
            int sky = 0;
            for (int x = rx * 60 + 10; x < rx * 60 + 50; x += 3) for (int z = rz * 60 + 10; z < rz * 60 + 50; z += 3) {
                B[] col = g.column(x, z);
                boolean clear = true;
                for (int y = g.floorY[PoolroomsGenerator.HIGH] + 1; y <= g.roofY; y++) {
                    if (!col[y - g.bottomY].passable()) {
                        clear = false;
                        break;
                    }
                }
                if (clear) sky++;
                // never any water left hanging up there
                for (int y = g.floorY[PoolroomsGenerator.HIGH] + 2; y <= g.roofY; y++) assertNotEquals(Material.WATER, col[y - g.bottomY].mat);
            }
            assertTrue(sky > 20, "an open hall really has no roof over it: " + sky + " open columns in region " + rx + "," + rz);
        }
        assertTrue(open > 0, "some open-air halls near the origin");
    }

    @Test
    void enclosedRoomsHaveWallsAndCeilings() {
        int found = 0;
        for (int rx = -10; rx <= 10 && found < 3; rx++) for (int rz = -10; rz <= 10 && found < 3; rz++) {
            var p = g.plan(rx, rz);
            for (int f = 0; f < 3; f++) {
                if (p.zone[f] != Zone.ENCLOSED || p.open[f]) continue;
                found++;
                int fy = g.floorY[f], covered = 0, cols = 0;
                for (int x = rx * 60 + 1; x < rx * 60 + 59; x += 2) for (int z = rz * 60 + 1; z < rz * 60 + 59; z += 2) {
                    B[] col = g.column(x, z);
                    if (!col[fy + 1 - g.bottomY].passable() || col[fy + 1 - g.bottomY].mat == Material.WATER) continue;
                    cols++;
                    for (int y = fy + 3; y <= fy + 22; y++) if (!col[y - g.bottomY].passable()) {
                        covered++;
                        break;
                    }
                }
                assertEquals(cols, covered, "every enclosed room has a ceiling");
            }
        }
        assertTrue(found > 0, "enclosed rooms near the origin");
    }

    @Test
    void statuesAreRareAndSetIntoSolidWalls() {
        int statues = 0, regions = 0;
        for (int rx = -14; rx <= 14; rx++) for (int rz = -14; rz <= 14; rz++) {
            regions++;
            for (int cx = rx * 5; cx < rx * 5 + 5; cx++) for (int cz = rz * 5; cz < rz * 5 + 5; cz++) {
                for (int f = 0; f < 3; f++) {
                    List<DecorSpec> out = new ArrayList<>();
                    for (DecorSpec d : decorOf(f, cx, cz)) if (d.model.startsWith("statue_face")) out.add(d);
                    for (DecorSpec d : out) {
                        statues++;
                        assertTrue(d.isStructural());
                        // the block just behind the face's back is solid: it is set into a wall
                        double yaw = Math.toRadians(d.yaw);
                        double bx = d.x + Math.sin(yaw) * 0.3, bz = d.z - Math.cos(yaw) * 0.3; // behind = -front
                        int scale = Integer.parseInt(d.model.split(":")[1]);
                        int by = (int) Math.floor(d.y + scale * 0.5);
                        B[] col = g.column((int) Math.floor(bx), (int) Math.floor(bz));
                        assertFalse(col[by - g.bottomY].passable(), "face " + d.model + " at " + d.x + "," + d.y + "," + d.z + " is not against a wall");
                    }
                }
            }
        }
        assertTrue(statues > 0, "some statues exist");
        assertTrue(statues < regions * 0.6, "statues stay rare: " + statues + " in " + regions + " regions");
    }

    private List<DecorSpec> decorOf(int f, int cx, int cz) {
        List<DecorSpec> out = new ArrayList<>();
        for (DecorSpec d : g.decorations(Math.floorDiv(cx * g.S, 16), Math.floorDiv(cz * g.S, 16))) {
            if (g.cellOf((int) Math.floor(d.x)) == cx && g.cellOf((int) Math.floor(d.z)) == cz && g.floorAt((int) Math.floor(d.y) + 1) == f) out.add(d);
        }
        return out;
    }
}
