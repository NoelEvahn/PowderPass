package com.powderpass.backrooms.gen;

import com.powderpass.backrooms.Level;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GenerationTest {
    private static final long SEED = 0x5EEDBACCL;

    private static Area regionWindow(LevelGenerator g, int rx0, int rz0, int regions) {
        int span = g.S * g.R;
        return new Area(g, rx0 * span, rz0 * span, (rx0 + regions) * span, (rz0 + regions) * span);
    }

    @ParameterizedTest
    @EnumSource(Level.class)
    void deterministicAcrossInstancesAndOrder(Level level) {
        LevelGenerator a = Generators.create(level, SEED, GenSettings.defaults());
        LevelGenerator b = Generators.create(level, SEED, GenSettings.defaults());
        for (int i = 0; i < 6; i++) {
            int cx = i * 7 - 20, cz = 13 - i * 5;
            // warm b's caches with other chunks first to make sure caching never changes output
            b.paint(new MemoryCanvas(-64, 320), cx + 3, cz - 2);
            MemoryCanvas ca = new MemoryCanvas(-64, 320), cb = new MemoryCanvas(-64, 320);
            a.paint(ca, cx, cz);
            b.paint(cb, cx, cz);
            for (int x = 0; x < 16; x++)
                for (int z = 0; z < 16; z++)
                    for (int y = 40; y < 100; y++)
                        assertSame(ca.get(x, y, z), cb.get(x, y, z), level + " differs at " + x + "," + y + "," + z);
        }
        assertEquals(a.decorations(4, -9).size(), b.decorations(4, -9).size());
    }

    @Test
    void differentSeedsDiffer() {
        LevelGenerator a = Generators.create(Level.LEVEL_0, 1, GenSettings.defaults());
        LevelGenerator b = Generators.create(Level.LEVEL_0, 2, GenSettings.defaults());
        int diff = 0;
        for (int cx = 0; cx < 4; cx++) {
            MemoryCanvas ca = new MemoryCanvas(-64, 320), cb = new MemoryCanvas(-64, 320);
            a.paint(ca, cx, 0);
            b.paint(cb, cx, 0);
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) if (ca.get(x, 66, z) != cb.get(x, 66, z)) diff++;
        }
        assertTrue(diff > 50, "seeds should change layout");
    }

    @ParameterizedTest
    @EnumSource(Level.class)
    void connectedSafeAndTransitionsReachable(Level level) {
        LevelGenerator g = Generators.create(level, SEED, GenSettings.defaults());
        // three windows of 3x3 regions in different places, plus (for levels whose transitions are rare) one
        // window centred on the nearest transition, so every transition type still gets walked to
        List<int[]> origins = new ArrayList<>(List.of(new int[]{0, 0}, new int[]{-7, 4}, new int[]{11, -9}));
        boolean rare = g.districtFeatures(0, 0).stream().noneMatch(f -> f.isTransition() && f.def().chance() >= 1.0);
        if (rare) {
            outer:
            for (int r = 0; r < 6; r++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        for (Feature f : g.districtFeatures(dx, dz)) {
                            if (!f.isTransition()) continue;
                            origins.add(new int[]{Math.floorDiv(f.cx(), g.R) - 1, Math.floorDiv(f.cz(), g.R) - 1});
                            break outer;
                        }
                    }
                }
            }
        }
        int windowsWithTransitions = 0;
        for (int[] o : origins) {
            Area area = regionWindow(g, o[0], o[1], 3);
            int[] start = null;
            List<int[]> safe = new ArrayList<>();
            int c0x = Math.floorDiv(area.minX, g.S), c0z = Math.floorDiv(area.minZ, g.S);
            int cells = (area.maxX - area.minX) / g.S;
            for (int f = 0; f < g.floorCount(); f++) {
                for (int cx = c0x; cx < c0x + cells; cx++) {
                    for (int cz = c0z; cz < c0z + cells; cz++) {
                        if (g.featureAt(f, cx, cz) == null && g.isSafeCell(f, cx, cz)) {
                            int[] l = g.safeLocal(f, cx, cz);
                            int[] p = {cx * g.S + l[0], g.floorY[f] + 1, cz * g.S + l[1]};
                            assertTrue(area.standable(p[0], p[1], p[2]), level + " safe cell centre not standable at " + p[0] + "," + p[1] + "," + p[2]
                                    + " feet=" + area.get(p[0], p[1], p[2]) + " below=" + area.get(p[0], p[1] - 1, p[2]) + " zone=" + g.zoneName(f, cx, cz));
                            assertNotEquals(Material.WATER, area.type(p[0], p[1] - 1, p[2]), "safe spot must be dry");
                            safe.add(p);
                            if (start == null) start = p;
                        }
                    }
                }
            }
            assertNotNull(start, "no safe cell in window");
            Walker w = new Walker(area, g.bottomY, g.roofY);
            w.run(start[0], start[1], start[2]);
            int unreachable = 0;
            StringBuilder sb = new StringBuilder();
            for (int[] p : safe) {
                if (!w.visited(p[0], p[1], p[2])) {
                    unreachable++;
                    if (sb.length() < 600) sb.append(" (").append(p[0]).append(',').append(p[1]).append(',').append(p[2]).append(' ')
                            .append(g.zoneName(g.floorAt(p[1]), g.cellOf(p[0]), g.cellOf(p[2]))).append(')');
                }
            }
            assertEquals(0, unreachable, level + " unreachable safe cells of " + safe.size() + ":" + sb);
            // every transition in the window must be enterable
            int transitions = 0;
            for (int dx = Math.floorDiv(Math.floorDiv(area.minX, g.S), g.R * g.D) - 1; dx <= Math.floorDiv(Math.floorDiv(area.maxX, g.S), g.R * g.D) + 1; dx++) {
                for (int dz = Math.floorDiv(Math.floorDiv(area.minZ, g.S), g.R * g.D) - 1; dz <= Math.floorDiv(Math.floorDiv(area.maxZ, g.S), g.R * g.D) + 1; dz++) {
                    for (Feature f : g.districtFeatures(dx, dz)) {
                        int x0 = f.cx() * g.S, z0 = f.cz() * g.S;
                        if (x0 < area.minX || z0 < area.minZ || x0 + g.S > area.maxX || z0 + g.S > area.maxZ) continue;
                        if (!f.isTransition() && f.def().effect() == null) continue;
                        int[] b = g.triggerBox(f);
                        int fy = g.floorY[f.floor()] + 1;
                        transitions++;
                        assertTrue(w.anyVisited(x0 + b[0], fy + b[1], z0 + b[2], x0 + b[3], fy + b[4], z0 + b[5]),
                                level + " transition " + f.id() + " at cell " + f.cx() + "," + f.cz() + " floor " + f.floor() + " unreachable");
                        // trigger lookup agrees with the box
                        boolean hit = false;
                        for (int x = x0 + b[0]; x <= x0 + b[3] && !hit; x++)
                            for (int z = z0 + b[2]; z <= z0 + b[5] && !hit; z++)
                                for (int y = fy + b[1]; y <= fy + b[4] && !hit; y++)
                                    hit = g.triggerAt(x, y, z) == f;
                        assertTrue(hit, "triggerAt mismatch for " + f.id());
                    }
                }
            }
            if (transitions > 0) windowsWithTransitions++;
            else assertTrue(rare, level + " window has no transitions");
        }
        assertTrue(windowsWithTransitions > 0, level + " no window with a transition");
    }

    @ParameterizedTest
    @EnumSource(Level.class)
    void waterIsContainedAndStill(Level level) {
        LevelGenerator g = Generators.create(level, SEED, GenSettings.defaults());
        Area area = regionWindow(g, 0, 0, 2);
        int water = 0;
        for (int x = area.minX + 1; x < area.maxX - 1; x++) {
            for (int z = area.minZ + 1; z < area.maxZ - 1; z++) {
                for (int y = g.bottomY; y <= g.roofY; y++) {
                    B wb = area.get(x, y, z);
                    if (wb.mat != Material.WATER && !wb.state.contains("waterlogged=true")) continue;
                    water++;
                    B bb = area.get(x, y - 1, z);
                    Material below = bb.state.contains("waterlogged=true") ? Material.WATER : bb.mat;
                    assertTrue(below != Material.AIR && below != Material.LIGHT, level + " water over air at " + x + "," + y + "," + z);
                    for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        B nb = area.get(x + d[0], y, z + d[1]);
                        Material n = nb.state.contains("waterlogged=true") ? Material.WATER : nb.mat;
                        assertTrue(n != Material.AIR && n != Material.LIGHT && !n.name().endsWith("_DOOR") && n != Material.LADDER,
                                level + " water would flow at " + x + "," + y + "," + z + " into " + n);
                    }
                }
            }
        }
        if (level == Level.POOLROOMS) assertTrue(water > 1000, "poolrooms needs water");
    }

    @ParameterizedTest
    @EnumSource(Level.class)
    void sealedShell(Level level) {
        LevelGenerator g = Generators.create(level, SEED, GenSettings.defaults());
        Area area = regionWindow(g, 0, 0, 1);
        for (int x = area.minX; x < area.maxX; x += 3) {
            for (int z = area.minZ; z < area.maxZ; z += 3) {
                assertEquals(Material.BEDROCK, area.type(x, g.bottomY, z), level + " missing bedrock floor");
                if (!level.openSky()) assertEquals(Material.BEDROCK, area.type(x, g.roofY, z), level + " missing bedrock roof at " + x + "," + z);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Level.class)
    void levelsGoInOrderAndOnlyThePlainsHasAnExit(Level level) {
        LevelGenerator g = Generators.create(level, SEED, GenSettings.defaults());
        java.util.Map<Level, Level> next = java.util.Map.of(Level.LEVEL_0, Level.POOLROOMS, Level.POOLROOMS, Level.LEVEL_1,
                Level.LEVEL_1, Level.LEVEL_4, Level.LEVEL_4, Level.PLAINS);
        java.util.Map<Level, Level> prev = java.util.Map.of(Level.POOLROOMS, Level.LEVEL_0, Level.LEVEL_1, Level.POOLROOMS,
                Level.LEVEL_4, Level.LEVEL_1, Level.PLAINS, Level.LEVEL_4);
        int forwardDistricts = 0;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                boolean forward = false;
                for (Feature f : g.districtFeatures(dx, dz)) {
                    if (!f.isTransition()) continue;
                    Dest d = f.dest();
                    if (d.isExit()) {
                        assertEquals(Level.PLAINS, level, "exits only exist in the Plains");
                        forward = true;
                    } else {
                        assertTrue(d.level() == next.get(level) || d.level() == prev.get(level),
                                level + " leads to " + d.level() + ", which is not next or previous in the chain");
                        if (d.level() == next.get(level)) forward = true;
                    }
                }
                if (forward) forwardDistricts++;
                // past Level 0 the way on is guaranteed in every district
                if (level != Level.LEVEL_0) assertTrue(forward, level + " district " + dx + "," + dz + " has no way on");
            }
        }
        if (level == Level.LEVEL_0) {
            // rare, but not absent: somewhere between 15% and 60% of districts
            assertTrue(forwardDistricts >= 7 && forwardDistricts <= 30, "Level 0 seams in " + forwardDistricts + "/49 districts");
        }
    }

    @ParameterizedTest
    @EnumSource(Level.class)
    void safeSpotSearchFindsStandableSpot(Level level) {
        LevelGenerator g = Generators.create(level, SEED, GenSettings.defaults());
        for (int i = 0; i < 5; i++) {
            int x = i * 523 - 1000, z = 777 - i * 311;
            int[] s = g.safeSpot(x, z, 0);
            Area area = new Area(g, s[0] - 8, s[2] - 8, s[0] + 8, s[2] + 8);
            assertTrue(area.standable(s[0], s[1], s[2]), level + " safe spot " + s[0] + "," + s[1] + "," + s[2]);
            assertNull(g.triggerAt(s[0], s[1], s[2]), "safe spot inside a trigger");
        }
    }
}
