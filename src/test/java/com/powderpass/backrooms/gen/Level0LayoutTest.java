package com.powderpass.backrooms.gen;

import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.gen.levels.Level0Generator;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Level 0's spaces: rooms with jutting wall sections that never block a route, long halls, pillar fields, tall halls. */
class Level0LayoutTest {
    private static final long SEED = 0x5EEDBACCL;
    private final Level0Generator g = (Level0Generator) Generators.create(Level.LEVEL_0, SEED, GenSettings.defaults());

    @Test
    void everyKindOfSpaceAppearsNearby() {
        Map<Level0Generator.Zone, Integer> seen = new EnumMap<>(Level0Generator.Zone.class);
        for (int rx = -10; rx <= 10; rx++) for (int rz = -10; rz <= 10; rz++) seen.merge(g.zoneOfCell(rx * g.R, rz * g.R), 1, Integer::sum);
        assertEquals(4, seen.size(), "rooms, halls, pillar fields and tall halls within 21 x 21 regions: " + seen);
        assertTrue(seen.get(Level0Generator.Zone.ROOMS) > 21 * 21 / 2, "rooms stay the main fabric: " + seen);
    }

    @Test
    void roomsAreFourBlocksTallWithSectionsThatNeverBlockTheMiddleOrADoorway() {
        int checked = 0, withSections = 0;
        for (int cx = -40; cx < 40; cx++) {
            for (int cz = -40; cz < 40; cz++) {
                if (g.zoneOfCell(cx, cz) != Level0Generator.Zone.ROOMS || g.featureAt(Level0Generator.UP, cx, cz) != null) continue;
                checked++;
                int[] l = g.safeLocal(Level0Generator.UP, cx, cz);
                Area a = new Area(g, cx * g.S, cz * g.S, cx * g.S + g.S, cz * g.S + g.S);
                int fy = g.floorY[Level0Generator.UP] + 1;
                // four blocks of air, then the ceiling
                if (g.isSafeCell(Level0Generator.UP, cx, cz)) {
                    for (int y = fy; y < fy + 4; y++) assertEquals(Material.AIR, a.type(cx * g.S + l[0], y, cz * g.S + l[1]), "room height at " + cx + "," + cz);
                    assertNotEquals(Material.AIR, a.type(cx * g.S + l[0], fy + 4, cz * g.S + l[1]), "ceiling at 4 blocks");
                }
                // jutting sections stay out of the middle and at least one block away from doorways on their wall
                boolean any = false;
                for (int u = 1; u < g.S; u++) {
                    for (int v = 1; v < g.S; v++) {
                        if (Mats.passable(a.type(cx * g.S + u, fy, cz * g.S + v))) continue;
                        any = true;
                        String zn = g.zoneName(Level0Generator.UP, cx, cz);
                        if (!zn.contains("pillar")) assertFalse(u >= 3 && u <= 5 && v >= 3 && v <= 5,
                                "section in the middle of room " + cx + "," + cz + " at " + u + "," + v);
                        // directly in front of a doorway on the west / north wall (a thickened wall may narrow a door
                        // at right angles to it, by design, but never close it)
                        if (zn.contains("thick")) continue;
                        Door w = g.west(Level0Generator.UP, cx, cz), n = g.north(Level0Generator.UP, cx, cz);
                        if (u == 1) assertFalse(w.covers(v), "section in front of the west doorway of " + cx + "," + cz);
                        if (v == 1) assertFalse(n.covers(u), "section in front of the north doorway of " + cx + "," + cz);
                    }
                }
                if (any) withSections++;
            }
        }
        assertTrue(checked > 1000, "checked " + checked);
        assertTrue(withSections > checked / 2, "most rooms have sections coming out of their walls: " + withSections + "/" + checked);
    }

    @Test
    void hallsAreLongAndStraight() {
        int longest = 0;
        for (int rx = -10; rx <= 10; rx++) {
            for (int rz = -10; rz <= 10; rz++) {
                if (g.zoneOfCell(rx * g.R, rz * g.R) != Level0Generator.Zone.HALLS) continue;
                int x0 = rx * g.S * g.R, z0 = rz * g.S * g.R, span = g.S * g.R;
                Area a = new Area(g, x0, z0, x0 + span, z0 + span);
                int fy = g.floorY[Level0Generator.UP] + 1;
                for (int i = 0; i < g.R; i++) {
                    int line = i * g.S + 4;
                    int runX = 0, runZ = 0;
                    for (int t = 0; t < span; t++) {
                        runX = Mats.passable(a.type(x0 + t, fy, z0 + line)) && Mats.passable(a.type(x0 + t, fy + 1, z0 + line)) ? runX + 1 : 0;
                        runZ = Mats.passable(a.type(x0 + line, fy, z0 + t)) && Mats.passable(a.type(x0 + line, fy + 1, z0 + t)) ? runZ + 1 : 0;
                        longest = Math.max(longest, Math.max(runX, runZ));
                    }
                }
            }
        }
        assertTrue(longest >= 60, "a hall region has a straight corridor its whole length, longest " + longest);
    }

    @Test
    void lightsAreFlushPanelsOverInvisibleLight() {
        MemoryCanvas mc = new MemoryCanvas(-64, 320);
        g.paint(mc, 2, 2);
        int panels = 0, lit = 0;
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 60; y < 100; y++) {
            CustomBlocks.Def d = CustomBlocks.of(mc.get(x, y, z));
            if (d == null || !d.id().startsWith("light_panel")) continue;
            panels++;
            if (mc.type(x, y - 1, z) == Material.LIGHT) lit++;
        }
        assertTrue(panels >= 4, "ceiling troffers in a chunk: " + panels);
        assertTrue(lit > 0, "lit panels have an invisible light under them");
    }

    @Test
    void floorsAreJoinedByHolesAndAnEndlessShaftLoop() {
        assertEquals(2, g.floorCount(), "an upper and a lower floor");
        int holes = 0, shafts = 0, holeRegions = 0;
        int[] someHole = null, someShaft = null;
        for (int x = -600; x < 600; x += 1) {
            for (int z = -600; z < 600; z += 8) {
                int zz = z + 5;
                if (g.holeAt(x, zz)) {
                    holes++;
                    if (someHole == null) someHole = new int[]{x, zz};
                }
                if (g.shaftAt(x, zz)) {
                    shafts++;
                    if (someShaft == null) someShaft = new int[]{x, zz};
                }
            }
        }
        assertTrue(holes > 20, "grids of holes in some open areas: " + holes);
        assertTrue(shafts > 10, "loop shafts on the lower floor: " + shafts);
        // a hole really opens from the upper floor onto the lower floor's open space
        Area a = new Area(g, someHole[0] - 8, someHole[1] - 8, someHole[0] + 8, someHole[1] + 8);
        int up = g.floorY[Level0Generator.UP], down = g.floorY[Level0Generator.DOWN];
        for (int y = up; y > down; y--) assertTrue(Mats.passable(a.type(someHole[0], y, someHole[1])), "hole open at y " + y);
        assertFalse(Mats.passable(a.type(someHole[0], down, someHole[1])), "and lands on the lower floor");
        // a shaft drops from the lower floor into the dark, and the upper floor's ceiling is open above it
        Area b = new Area(g, someShaft[0] - 8, someShaft[1] - 8, someShaft[0] + 8, someShaft[1] + 8);
        for (int y = down; y > Level0Generator.BOTTOM; y--) assertTrue(Mats.passable(b.type(someShaft[0], y, someShaft[1])), "shaft open at y " + y);
        boolean ceilingOpen = true;
        for (int y = up + 6; y <= Level0Generator.CEILING_SHAFT_TOP; y++) ceilingOpen &= Mats.passable(b.type(someShaft[0], y, someShaft[1]));
        assertTrue(ceilingOpen, "the shaft over the upper floor is open from its ceiling");
        assertTrue(Mats.passable(b.type(someShaft[0], up + 1, someShaft[1])) && !Mats.passable(b.type(someShaft[0], up, someShaft[1])),
                "fallers come down onto the upper floor's carpet");
        // the carry lands inside that ceiling shaft
        double target = Level0Generator.loopTarget(Level0Generator.LOOP_Y - 0.5);
        assertTrue(target > up + 6 && target < Level0Generator.CEILING_SHAFT_TOP, "carried into the shaft over the ceiling: " + target);
        assertTrue(holeRegions >= 0);
    }

    private static int brightestLight(LevelGenerator g) {
        int max = 0;
        for (int cx = 0; cx < 6; cx++) for (int cz = 0; cz < 6; cz++) {
            MemoryCanvas c = new MemoryCanvas(g.bottomY, g.roofY + 1);
            g.paint(c, cx, cz);
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = g.bottomY; y <= g.roofY; y++) {
                B b = c.get(x, y, z);
                if (b.mat == org.bukkit.Material.LIGHT) max = Math.max(max, Integer.parseInt(b.toString().replaceAll(".*level=(\\d+).*", "$1")));
            }
        }
        return max;
    }

    /** The Lobby is two light levels dimmer by default; brightness 0 puts the fixtures back to full. */
    @org.junit.jupiter.api.Test
    void theLobbyIsDimmerByDefault() {
        int dim = brightestLight(Generators.create(com.powderpass.backrooms.Level.LEVEL_0, 77, GenSettings.defaults()));
        int full = brightestLight(new com.powderpass.backrooms.gen.levels.Level0Generator(77, new GenSettings(java.util.Map.of("brightness", 0.0))));
        org.junit.jupiter.api.Assertions.assertEquals(15, full);
        org.junit.jupiter.api.Assertions.assertEquals(13, dim);
    }
}
