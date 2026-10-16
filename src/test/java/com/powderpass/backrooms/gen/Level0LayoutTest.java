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
                if (g.zoneOfCell(cx, cz) != Level0Generator.Zone.ROOMS || g.featureAt(0, cx, cz) != null) continue;
                checked++;
                int[] l = g.safeLocal(0, cx, cz);
                Area a = new Area(g, cx * g.S, cz * g.S, cx * g.S + g.S, cz * g.S + g.S);
                int fy = g.floorY[0] + 1;
                // four blocks of air, then the ceiling
                if (g.isSafeCell(0, cx, cz)) {
                    for (int y = fy; y < fy + 4; y++) assertEquals(Material.AIR, a.type(cx * g.S + l[0], y, cz * g.S + l[1]), "room height at " + cx + "," + cz);
                    assertNotEquals(Material.AIR, a.type(cx * g.S + l[0], fy + 4, cz * g.S + l[1]), "ceiling at 4 blocks");
                }
                // jutting sections stay out of the middle and at least one block away from doorways on their wall
                boolean any = false;
                for (int u = 1; u < g.S; u++) {
                    for (int v = 1; v < g.S; v++) {
                        if (Mats.passable(a.type(cx * g.S + u, fy, cz * g.S + v))) continue;
                        any = true;
                        String zn = g.zoneName(0, cx, cz);
                        if (!zn.contains("pillar")) assertFalse(u >= 3 && u <= 5 && v >= 3 && v <= 5,
                                "section in the middle of room " + cx + "," + cz + " at " + u + "," + v);
                        // directly in front of a doorway on the west / north wall (a thickened wall may narrow a door
                        // at right angles to it, by design, but never close it)
                        if (zn.contains("thick")) continue;
                        Door w = g.west(0, cx, cz), n = g.north(0, cx, cz);
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
                int fy = g.floorY[0] + 1;
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
}
