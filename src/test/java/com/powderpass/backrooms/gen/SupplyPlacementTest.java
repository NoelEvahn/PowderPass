package com.powderpass.backrooms.gen;

import com.powderpass.backrooms.Level;
import org.bukkit.Material;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/** Generated supplies lie on something solid, with room to stand at them, and never in a transition trigger. */
class SupplyPlacementTest {
    @ParameterizedTest
    @EnumSource(Level.class)
    void suppliesLieOnTheFloor(Level level) {
        LevelGenerator g = Generators.create(level, 0xC0FFEEL, GenSettings.defaults());
        int span = g.S * g.R * 2;
        Area a = new Area(g, 0, 0, span, span);
        int found = 0;
        for (int cx = 0; cx < span / 16; cx++) {
            for (int cz = 0; cz < span / 16; cz++) {
                for (DecorSpec d : g.decorations(cx, cz)) {
                    if (!d.model.startsWith("supply:")) continue;
                    found++;
                    int x = (int) Math.floor(d.x), y = (int) Math.floor(d.y), z = (int) Math.floor(d.z);
                    String at = level + " " + d.model + " at " + d.x + "," + d.y + "," + d.z;
                    assertFalse(Mats.passable(a.type(x, y - 1, z)), at + " floats over " + a.type(x, y - 1, z));
                    assertTrue(Mats.passable(a.type(x, y, z)) && a.type(x, y, z) != Material.WATER, at + " buried in " + a.type(x, y, z));
                    assertTrue(Mats.passable(a.type(x, y + 1, z)), at + " has no headroom");
                    assertNull(g.triggerAt(x, y, z), at + " inside a transition trigger");
                }
            }
        }
        assertTrue(found > 0, level + ": no supplies generated in " + span + "x" + span);
        System.out.println(level + ": " + found + " supplies");
    }
}
