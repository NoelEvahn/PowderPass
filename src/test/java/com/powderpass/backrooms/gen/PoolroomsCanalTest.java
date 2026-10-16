package com.powderpass.backrooms.gen;

import com.powderpass.backrooms.gen.levels.PoolroomsGenerator;
import com.powderpass.backrooms.gen.levels.PoolroomsGenerator.Zone;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Canal courts: a railed promenade round a sunken canal, bridges to a pavilion with arches, windows, a statue
 * court and a roof terrace. The point of the room is that every part you can see across the water can be walked to.
 */
class PoolroomsCanalTest {
    private static final long SEED = 0x5EEDBACCL;
    private static final int MID = PoolroomsGenerator.MID, REG = 60;

    private static PoolroomsGenerator gen(double canalChance) {
        return new PoolroomsGenerator(SEED, new GenSettings(Map.of("canal-chance", canalChance)));
    }

    /** The first {@code n} regions whose main floor is a canal court (each is rotated differently, more or less). */
    private static List<int[]> findCourts(PoolroomsGenerator g, int n) {
        List<int[]> out = new ArrayList<>();
        for (int r = 0; r < 12 && out.size() < n; r++) {
            for (int rx = -r; rx <= r && out.size() < n; rx++) for (int rz = -r; rz <= r && out.size() < n; rz++) {
                if (Math.max(Math.abs(rx), Math.abs(rz)) == r && g.plan(rx, rz).zone[MID] == Zone.CANAL) out.add(new int[]{rx, rz});
            }
        }
        assertEquals(n, out.size(), "not enough canal courts near the origin");
        return out;
    }

    private static int[] findCourt(PoolroomsGenerator g) {
        return findCourts(g, 1).get(0);
    }

    @Test
    void worldsWithoutTheSettingNeverGetOne() {
        // older worlds have no canal-chance in their locked settings: their layout must not change at all
        PoolroomsGenerator old = new PoolroomsGenerator(SEED, GenSettings.defaults());
        for (int rx = -12; rx <= 12; rx++) for (int rz = -12; rz <= 12; rz++) {
            for (Zone z : old.plan(rx, rz).zone) assertNotEquals(Zone.CANAL, z);
        }
    }

    @Test
    void turningThemOnLeavesEveryOtherRoomWhereItWas() {
        PoolroomsGenerator before = gen(0), after = gen(0.15);
        int canals = 0;
        for (int rx = -12; rx <= 12; rx++) for (int rz = -12; rz <= 12; rz++) {
            for (int f = 0; f < 3; f++) {
                Zone z = after.plan(rx, rz).zone[f];
                if (z == Zone.CANAL) canals++;
                else assertEquals(before.plan(rx, rz).zone[f], z, "region " + rx + "," + rz + " floor " + f + " moved");
            }
        }
        assertTrue(canals > 10, "expected canal courts at 15%, found " + canals);
    }

    @Test
    void theStairTowerSitsInTheCornerWhereItsDoorMeetsThePromenade() {
        PoolroomsGenerator g = gen(1.0);
        int[] c = findCourt(g);
        PoolroomsGenerator.Plan p = g.plan(c[0], c[1]);
        assertEquals(c[0] * REG / g.S, p.towerCx);
        assertEquals(c[1] * REG / g.S, p.towerCz);
    }

    @Test
    void bridgesAndArchesAreOpenAndRailed() {
        PoolroomsGenerator g = gen(1.0);
        for (int[] c : findCourts(g, 6)) {
            int x0 = c[0] * REG, z0 = c[1] * REG, fy = g.floorY[MID];
            Area a = new Area(g, x0 - 4, z0 - 4, x0 + REG + 4, z0 + REG + 4);
            String at = " in the court at region " + c[0] + "," + c[1];
            // the four mid-lines are the four bridges whatever the court's rotation: promenade, bridge, arch, cloister
            for (int t : new int[]{28, 29, 30, 31}) {
                for (int along = 0; along <= 16; along++) {
                    int far = REG - 1 - along;
                    for (int[] p : new int[][]{{t, along}, {t, far}, {along, t}, {far, t}}) {
                        assertTrue(a.standable(x0 + p[0], fy + 1, z0 + p[1]), "bridge line blocked at " + p[0] + "," + p[1] + at);
                    }
                }
            }
            // 4 blocks of water under the bridge, a railing down both sides of it
            for (int along = 5; along <= 10; along++) {
                assertEquals(Material.WATER, a.type(x0 + 29, fy - 1, z0 + along), "no canal under the bridge" + at);
                assertEquals(Material.IRON_BARS, a.type(x0 + 27, fy + 1, z0 + along), "no railing on the bridge" + at);
                assertEquals(Material.IRON_BARS, a.type(x0 + 32, fy + 1, z0 + along), "no railing on the bridge" + at);
            }
            // the canal itself, away from any bridge or steps
            assertEquals(Material.WATER, a.type(x0 + 7, fy - 1, z0 + 20), at);
            assertEquals(Material.WATER, a.type(x0 + 7, fy - 4, z0 + 20), at);
            // the promenade's edge has a railing except at the mouth of the bridges
            assertEquals(Material.IRON_BARS, a.type(x0 + 4, fy + 1, z0 + 20), at);
            // flights of steps climb out of the canal (the stair tower stands on part of two of the eight)
            int stairs = 0;
            for (int x = x0; x < x0 + REG; x++) for (int z = z0; z < z0 + REG; z++) {
                int ring = Math.min(Math.min(x - x0, z - z0), Math.min(x0 + REG - 1 - x, z0 + REG - 1 - z));
                if (ring >= 5 && ring <= 9) for (int y = fy - 4; y <= fy; y++) if (a.type(x, y, z) == Material.QUARTZ_STAIRS) stairs++;
            }
            assertTrue(stairs >= 6 * 3 * 5, "walk-out steps are missing (" + stairs + ")" + at);
        }
    }

    @Test
    void theCourtAndTheRoofTerraceCanBeWalkedTo() {
        PoolroomsGenerator g = gen(1.0);
        for (int[] c : findCourts(g, 4)) {
            int x0 = c[0] * REG, z0 = c[1] * REG, fy = g.floorY[MID];
            String at = " in the court at region " + c[0] + "," + c[1];
            Area a = new Area(g, x0 - 2, z0 - 2, x0 + REG + 2, z0 + REG + 2);
            Walker w = new Walker(a, g.bottomY, g.roofY);
            w.run(x0 + 2, fy + 1, z0 + 30); // on the promenade
            // the court rim, inside the colonnade (ring 18)
            assertTrue(w.anyVisited(x0 + 18, fy + 1, z0 + 24, x0 + 18, fy + 1, z0 + 36), "the court cannot be reached" + at);
            assertTrue(w.anyVisited(x0 + 24, fy + 1, z0 + 41, x0 + 36, fy + 1, z0 + 41), "the court cannot be reached from the far side" + at);
            // inside the cloister, behind an arch
            assertTrue(w.anyVisited(x0 + 28, fy + 1, z0 + 13, x0 + 31, fy + 1, z0 + 16), "the cloister cannot be reached" + at);
            // the roof terrace (floor + 12), all the way round the court
            for (int[] box : new int[][]{{12, 12, 16, 16}, {43, 12, 47, 16}, {12, 43, 16, 47}, {43, 43, 47, 47}}) {
                assertTrue(w.anyVisited(x0 + box[0], fy + 12, z0 + box[1], x0 + box[2], fy + 12, z0 + box[3]),
                        "the roof terrace corner " + box[0] + "," + box[1] + " cannot be reached" + at);
            }
        }
    }

    @Test
    void canalColumnsAreDeterministicAndLit() {
        PoolroomsGenerator g = gen(1.0), other = gen(1.0);
        int[] c = findCourt(g);
        int lights = 0;
        for (int i = 0; i < REG * REG; i++) {
            int x = c[0] * REG + i % REG, z = c[1] * REG + i / REG;
            B[] col = g.column(x, z), again = other.column(x, z);
            assertArrayEquals(col, again);
            for (B b : col) if (b.mat == Material.LIGHT) lights++;
        }
        assertTrue(lights > 100, "the court is too dark (" + lights + " light blocks)");
    }
}
