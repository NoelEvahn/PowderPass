package com.powderpass.backrooms.gen;

import com.powderpass.backrooms.Level;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Surface details against the real painted blocks: wall overlays sit on a wall face with air in front, ceiling
 * overlays touch the ceiling, floor overlays lie on something solid, nothing sits in a transition trigger, and
 * chunk boundaries neither drop nor duplicate anything.
 */
class DetailPlacementTest {
    private static final long SEED = 0x5EEDBACCL;

    private static boolean solid(Area a, double x, double y, double z) {
        Material m = a.type((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        return !Mats.passable(m);
    }

    private static boolean open(Area a, double x, double y, double z) {
        Material m = a.type((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        return Mats.passable(m) && m != Material.WATER;
    }

    /** Unit normal (x, z) a wall detail faces, from its yaw (the room side). */
    private static double[] normal(float yaw) {
        double r = Math.toRadians(yaw);
        return new double[]{-Math.sin(r), Math.cos(r)};
    }

    @ParameterizedTest
    @EnumSource(Level.class)
    void detailsSitOnTheirSurfaces(Level level) {
        LevelGenerator g = Generators.create(level, SEED, GenSettings.defaults());
        int span = g.S * g.R * 2;
        Area a = new Area(g, 0, 0, span, span);
        int walls = 0, ceilings = 0, floors = 0;
        for (int cx = 1; cx < span / 16 - 1; cx++) {
            for (int cz = 1; cz < span / 16 - 1; cz++) {
                for (DecorSpec d : g.decorations(cx, cz)) {
                    if (d.tier == 0) continue;
                    String m = d.model;
                    assertNull(g.triggerAt((int) Math.floor(d.x), (int) Math.floor(d.y), (int) Math.floor(d.z)),
                            level + " detail " + m + " inside a transition trigger at " + d.x + "," + d.y + "," + d.z);
                    if (m.startsWith("d:wpatch") || m.startsWith("d:peel") || m.startsWith("d:streak") || m.startsWith("d:outlet")
                            || m.startsWith("d:grille") || m.startsWith("d:jbox")) {
                        double[] n = normal(d.yaw);
                        double dy = m.startsWith("d:wpatch") || m.startsWith("d:grille") ? 0.2 : m.startsWith("d:jbox") || m.startsWith("d:outlet") ? 0 : -0.2;
                        // a hair along the wall from the anchor, so the probe is inside the overlay's extent
                        double along = m.startsWith("d:wpatch") || m.startsWith("d:peel") || m.startsWith("d:grille") ? 0.15 : 0;
                        double ax = d.x + Math.cos(Math.toRadians(d.yaw)) * along, az = d.z + Math.sin(Math.toRadians(d.yaw)) * along;
                        assertTrue(solid(a, ax - n[0] * 0.2, d.y + dy, az - n[1] * 0.2),
                                level + " " + m + " has no wall behind it at " + d.x + "," + d.y + "," + d.z + " yaw " + d.yaw);
                        assertTrue(open(a, ax + n[0] * 0.2, d.y + dy, az + n[1] * 0.2),
                                level + " " + m + " faces into " + a.type((int) Math.floor(ax + n[0] * 0.2), (int) Math.floor(d.y + dy), (int) Math.floor(az + n[1] * 0.2))
                                        + " at " + d.x + "," + d.y + "," + d.z + " yaw " + d.yaw);
                        walls++;
                    } else if (m.startsWith("d:cpatch") || m.startsWith("d:vent") || (m.startsWith("d:crack") && m.endsWith(":c"))
                            || (m.startsWith("d:strip") && m.contains(":0.008:"))) {
                        assertTrue(solid(a, d.x + 0.02, d.y + 0.05, d.z + 0.02), level + " " + m + " not under a ceiling at " + d.x + "," + d.y + "," + d.z);
                        assertTrue(open(a, d.x + 0.02, d.y - 0.3, d.z + 0.02), level + " " + m + " buried at " + d.x + "," + d.y + "," + d.z);
                        ceilings++;
                    } else if (m.startsWith("d:fpatch") || (m.startsWith("d:crack") && m.endsWith(":f")) || m.startsWith("d:drain")
                            || m.startsWith("d:coping") || m.startsWith("d:grass")) {
                        assertTrue(solid(a, d.x, d.y - 0.05, d.z), level + " " + m + " floats at " + d.x + "," + d.y + "," + d.z);
                        floors++;
                    }
                }
            }
        }
        System.out.printf("%s: %d wall, %d ceiling, %d floor details checked%n", level, walls, ceilings, floors);
        assertTrue(walls + ceilings + floors > 0, level + " has no checkable details");
    }

    @ParameterizedTest
    @EnumSource(Level.class)
    void chunkBordersNeitherDropNorDuplicate(Level level) {
        LevelGenerator g = Generators.create(level, SEED, GenSettings.defaults());
        Set<String> keys = new HashSet<>();
        for (int cx = -3; cx < 3; cx++) {
            for (int cz = -3; cz < 3; cz++) {
                List<DecorSpec> first = g.decorations(cx, cz);
                List<DecorSpec> again = g.decorations(cx, cz);
                assertEquals(first.size(), again.size(), "repeated generation of a chunk differs");
                for (int i = 0; i < first.size(); i++) assertEquals(first.get(i).key(), again.get(i).key());
                int prevTier = 0;
                for (DecorSpec d : first) {
                    assertEquals(cx, Math.floorDiv((int) Math.floor(d.x), 16), "decoration anchored in another chunk");
                    assertEquals(cz, Math.floorDiv((int) Math.floor(d.z), 16), "decoration anchored in another chunk");
                    assertTrue(keys.add(d.key()), level + " duplicate decoration across chunks: " + d.key());
                    assertTrue(d.tier >= prevTier, "functional decorations must come before details (budget order)");
                    prevTier = d.tier;
                    assertTrue(d.rank >= 0 && d.rank < 1);
                }
            }
        }
    }

    @Test
    void plainsGroundIsMostlyGrassInCorrelatedPatches() {
        LevelGenerator g = Generators.create(Level.PLAINS, SEED, GenSettings.defaults());
        Area a = new Area(g, 0, 0, 384, 384);
        int fy = g.floorY[0];
        int grass = 0, moss = 0, coarse = 0, total = 0, mossSame = 0, mossPairs = 0;
        for (int x = 0; x < 384; x++) {
            for (int z = 0; z < 384; z++) {
                Material m = a.type(x, fy, z);
                if (m != Material.GRASS_BLOCK && m != Material.MOSS_BLOCK && m != Material.COARSE_DIRT) continue;
                total++;
                if (m == Material.GRASS_BLOCK) grass++;
                if (m == Material.COARSE_DIRT) coarse++;
                if (m == Material.MOSS_BLOCK) {
                    moss++;
                    if (x + 1 < 384) {
                        mossPairs++;
                        if (a.type(x + 1, fy, z) == Material.MOSS_BLOCK) mossSame++;
                    }
                }
            }
        }
        double gp = grass / (double) total, mp = moss / (double) total, cp = coarse / (double) total;
        System.out.printf("plains ground: grass %.1f%%, moss %.1f%%, coarse %.1f%%, moss neighbour same %.2f%n", gp * 100, mp * 100, cp * 100,
                mossSame / (double) Math.max(1, mossPairs));
        // grass and moss, roughly three to one
        assertTrue(gp > 0.65 && gp < 0.88, "grass share " + gp);
        assertTrue(mp > 0.1 && mp < 0.32, "moss share " + mp);
        assertTrue(cp > 0.01 && cp < 0.06, "coarse dirt share " + cp);
        assertTrue(mossSame / (double) mossPairs > 0.75, "moss should come in patches, not noise");
    }
}
