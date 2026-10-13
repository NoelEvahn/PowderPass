package com.powderpass.backrooms.gen;

import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.gen.levels.OfficeGenerator;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Level 4, the office towers, against painted chunks: several floors per tower joined by walkable stairs, sky
 * bridges between two towers at one floor, a lift stop on every floor, guarded edges and no vanilla light sources.
 */
class OfficeTowersTest {
    private static final long SEED = 0x5EEDBACCL;
    private static final int B0 = OfficeGenerator.BASE, FH = OfficeGenerator.FH;
    private static final Set<String> LIGHT_EMITTERS = Set.of("GLOWSTONE", "SEA_LANTERN", "LANTERN", "SOUL_LANTERN", "TORCH", "WALL_TORCH",
            "SOUL_TORCH", "END_ROD", "SHROOMLIGHT", "REDSTONE_LAMP", "OCHRE_FROGLIGHT", "PEARLESCENT_FROGLIGHT", "VERDANT_FROGLIGHT",
            "CAMPFIRE", "SOUL_CAMPFIRE", "JACK_O_LANTERN", "BEACON", "MAGMA_BLOCK", "COPPER_BULB", "CANDLE");

    private static OfficeGenerator gen() {
        return (OfficeGenerator) Generators.create(Level.LEVEL_4, SEED, GenSettings.defaults());
    }

    private static int[] safe(OfficeGenerator g, OfficeGenerator.Tower t, int floor) {
        int[] l = g.safeLocal(floor, t.cx, t.cz);
        return new int[]{t.cx * g.S + l[0], g.floorY[floor] + 1, t.cz * g.S + l[1]};
    }

    @Test
    void towersHaveSeveralFloorsJoinedByStairs() {
        OfficeGenerator g = gen();
        int stairsSeen = 0;
        java.util.Set<Integer> heights = new java.util.TreeSet<>();
        for (int k = 0; k < 6; k++) {
            OfficeGenerator.Tower t = g.tower(k * 3 - 7, 5 - k * 2);
            heights.add(t.floors);
            assertTrue(t.floors >= 3 && t.floors <= OfficeGenerator.MAX_FLOORS, "floors " + t.floors);
            // only the tower itself: bridges cannot help, so every floor must be reached by the stairs
            Area a = new Area(g, t.x0, t.z0, t.x0 + t.w, t.z0 + t.l);
            for (int x = t.x0; x < t.x0 + t.w; x++)
                for (int z = t.z0; z < t.z0 + t.l; z++)
                    for (int y = B0; y < B0 + FH * t.floors; y++) if (a.type(x, y, z) == Material.POLISHED_ANDESITE_STAIRS) stairsSeen++;
            Walker w = new Walker(a, g.bottomY, g.roofY);
            int[] s0 = safe(g, t, 0);
            w.run(s0[0], s0[1], s0[2]);
            for (int f = 0; f < t.floors; f++) {
                int[] s = safe(g, t, f);
                assertTrue(w.visited(s[0], s[1], s[2]), "tower " + t.cx + "," + t.cz + " floor " + f + " of " + t.floors + " not reachable by the stairs");
            }
            // and the roof, through the stair penthouse
            int roofFeet = B0 + FH * t.floors + 1;
            assertTrue(w.anyVisited(t.x0 + 1, roofFeet, t.z0 + 1, t.x0 + t.w - 2, roofFeet, t.z0 + t.l - 2), "roof not reachable");
        }
        assertTrue(stairsSeen > 50, "stairs " + stairsSeen);
        assertTrue(heights.size() >= 2, "towers should differ in height: " + heights);
    }

    @Test
    void skyBridgesJoinTwoTowersAtOneFloor() {
        OfficeGenerator g = gen();
        int checked = 0;
        for (int cx = -2; cx <= 2 && checked < 4; cx++) {
            OfficeGenerator.Tower t = g.tower(cx, 1);
            for (OfficeGenerator.Bridge b : t.bridges()) {
                // only bridges this tower owns (towards east or south), so each is checked once
                boolean own = b.ew() ? b.from() == t.x0 + t.w : b.from() == t.z0 + t.l;
                if (!own) continue;
                OfficeGenerator.Tower o = b.ew() ? g.tower(cx + 1, 1) : g.tower(cx, 2);
                assertTrue(b.floor() >= 1 && b.floor() < Math.min(t.floors, o.floors), "bridge floor " + b.floor());
                int y = g.floorY[b.floor()];
                int minX = Math.min(t.x0, o.x0), minZ = Math.min(t.z0, o.z0);
                int maxX = Math.max(t.x0 + t.w, o.x0 + o.w), maxZ = Math.max(t.z0 + t.l, o.z0 + o.l);
                Area a = new Area(g, minX, minZ, maxX, maxZ);
                for (int along = b.from(); along <= b.to(); along++) {
                    for (int c = 0; c < 3; c++) {
                        int x = b.ew() ? along : b.lo() + c, z = b.ew() ? b.lo() + c : along;
                        assertTrue(a.standable(x, y + 1, z), "bridge walkway not standable at " + x + "," + (y + 1) + "," + z);
                    }
                    for (int c : new int[]{-1, 3}) {
                        int x = b.ew() ? along : b.lo() + c, z = b.ew() ? b.lo() + c : along;
                        assertFalse(Mats.passable(a.type(x, y + 1, z)), "bridge edge open at " + x + "," + z);
                        assertFalse(Mats.passable(a.type(x, y + 2, z)), "bridge rail too low at " + x + "," + z);
                    }
                }
                // walk across: from this tower's safe spot on that floor to the other tower's
                Walker w = new Walker(a, g.bottomY, g.roofY);
                int[] s = safe(g, t, b.floor()), e = safe(g, o, b.floor());
                w.run(s[0], s[1], s[2]);
                assertTrue(w.visited(e[0], e[1], e[2]), "bridge from " + t.cx + "," + t.cz + " floor " + b.floor() + " does not lead into the next tower");
                checked++;
            }
        }
        assertTrue(checked >= 3, "only " + checked + " bridges checked");
    }

    @Test
    void everyFloorHasALiftStop() {
        OfficeGenerator g = gen();
        for (int k = 0; k < 5; k++) {
            OfficeGenerator.Tower t = g.tower(k - 2, k * 2 - 3);
            OfficeGenerator.Lift lift = g.lift(t.cx, t.cz);
            Area a = new Area(g, lift.carX() - 2, lift.carZ() - 3, lift.carX() + 5, lift.carZ() + 5);
            for (int f = 0; f < t.floors; f++) {
                int feet = lift.feetY(f);
                for (int dx = 0; dx < 3; dx++) for (int dz = 0; dz < 3; dz++) {
                    assertTrue(a.standable(lift.carX() + dx, feet, lift.carZ() + dz), "car floor " + f + " not standable");
                    assertEquals(lift, g.liftAt(lift.carX() + dx + 0.5, feet, lift.carZ() + dz + 0.5));
                }
                assertEquals(f, lift.floorAt(feet));
                assertTrue(a.standable(lift.doorX(), feet, lift.doorZ()), "lift door closed on floor " + f);
                assertTrue(a.standable(lift.doorX(), feet, lift.doorZ() - 1), "nothing in front of the lift door on floor " + f);
            }
            assertNull(g.liftAt(lift.carX() + 1.5, lift.feetY(t.floors), lift.carZ() + 1.5), "no lift stop on the roof");
            assertNull(g.liftAt(lift.carX() - 1.5, lift.feetY(0), lift.carZ() + 1.5), "outside the car is not in the lift");
        }
    }

    @Test
    void edgesAreGuardedAndNoVanillaLightSources() {
        OfficeGenerator g = gen();
        Area a = new Area(g, -64, -64, 2 * g.S + 64, 2 * g.S + 64);
        int openings = 0;
        for (int cx = -1; cx <= 2; cx++) {
            for (int cz = -1; cz <= 2; cz++) {
                OfficeGenerator.Tower t = g.tower(cx, cz);
                if (t.x0 < a.minX + 2 || t.z0 < a.minZ + 2 || t.x0 + t.w > a.maxX - 2 || t.z0 + t.l > a.maxZ - 2) continue;
                for (int f = 0; f <= t.floors; f++) {
                    int feet = B0 + FH * f + 1;
                    for (int i = 0; i < t.w; i++) {
                        for (int j = 0; j < t.l; j++) {
                            if (i != 0 && j != 0 && i != t.w - 1 && j != t.l - 1) continue;
                            int x = t.x0 + i, z = t.z0 + j;
                            if (f == t.floors) {
                                // roof parapet: two blocks, nothing to step over
                                assertFalse(Mats.passable(a.type(x, feet, z)) && Mats.passable(a.type(x, feet + 1, z)), "roof edge open at " + x + "," + z);
                                continue;
                            }
                            if (!Mats.passable(a.type(x, feet, z))) continue;
                            // a doorway in the glass: there must be a bridge walkway right outside
                            openings++;
                            int ox = i == 0 ? x - 1 : i == t.w - 1 ? x + 1 : x, oz = j == 0 ? z - 1 : j == t.l - 1 ? z + 1 : z;
                            assertTrue(a.standable(ox, feet, oz), "facade opening onto the drop at " + x + "," + feet + "," + z);
                        }
                    }
                }
            }
        }
        assertTrue(openings > 0, "no bridge doorways found");
        for (int x = a.minX; x < a.maxX; x += 1) {
            for (int z = a.minZ; z < a.maxZ; z += 1) {
                for (int y = g.bottomY; y <= g.roofY; y++) {
                    Material m = a.type(x, y, z);
                    if (m == Material.AIR) continue;
                    assertFalse(LIGHT_EMITTERS.contains(m.name()) || m.name().endsWith("_CANDLE"), "vanilla light source " + m + " at " + x + "," + y + "," + z);
                }
            }
        }
    }

    @Test
    void transitionsSitInTowersOnReachableFloors() {
        OfficeGenerator g = gen();
        int window = 0, stairs = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (Feature f : g.districtFeatures(dx, dz)) {
                    OfficeGenerator.Tower t = g.tower(f.cx(), f.cz());
                    int[] b = ((LevelGenerator) g).triggerBox(f);
                    int x0 = f.cx() * g.S + b[0], z0 = f.cz() * g.S + b[2];
                    assertTrue(t.inside(x0, z0), f.id() + " trigger outside its tower");
                    if (f.id().equals("bright_window")) {
                        window++;
                        assertEquals(FH * (t.floors - 1), b[1], "the bright window is on the top floor");
                    } else {
                        stairs++;
                        assertEquals(-3, b[1], "the basement stairs go down below the lobby");
                    }
                }
            }
        }
        assertEquals(25, window, "one window onward in every district");
        assertTrue(stairs >= 4 && stairs <= 18, "basement stairs in " + stairs + "/25 districts");
    }
}
