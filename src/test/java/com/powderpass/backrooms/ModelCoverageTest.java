package com.powderpass.backrooms;

import com.powderpass.backrooms.display.Model;
import com.powderpass.backrooms.display.ModelLibrary;
import com.powderpass.backrooms.display.Part;
import com.powderpass.backrooms.gen.DecorSpec;
import com.powderpass.backrooms.gen.GenSettings;
import com.powderpass.backrooms.gen.Generators;
import com.powderpass.backrooms.gen.LevelGenerator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/** Every decoration the generators can emit must build into a valid, budget-friendly model. */
class ModelCoverageTest {
    @BeforeAll
    static void up() {
        MockBukkit.mock();
    }

    @AfterAll
    static void down() {
        MockBukkit.unmock();
    }

    @Test
    void allEmittedDecorationsBuild() {
        Map<String, Integer> seen = new TreeMap<>();
        for (Level level : Level.values()) {
            LevelGenerator g = Generators.create(level, 99, GenSettings.defaults());
            for (int cx = -40; cx < 40; cx += 1) {
                for (int cz = -40; cz < 40; cz += 3) {
                    for (DecorSpec d : g.decorations(cx, cz)) {
                        seen.merge(d.model, 1, Integer::sum);
                    }
                }
            }
        }
        StringBuilder missing = new StringBuilder();
        int maxParts = 0;
        String biggest = "";
        for (String id : seen.keySet()) {
            Model m;
            try {
                m = ModelLibrary.build(id, 12345L);
            } catch (RuntimeException e) {
                fail("model " + id + " threw " + e);
                return;
            }
            if (m == null) {
                missing.append(id).append(' ');
                continue;
            }
            for (Part p : m.parts) {
                if (p.kind == Part.Kind.BLOCK) assertNotNull(p.block, id + " has a block part without block data");
            }
            if (m.entityCount() > maxParts) {
                maxParts = m.entityCount();
                biggest = id;
            }
        }
        assertEquals("", missing.toString().trim(), "decoration ids without a model");
        assertTrue(seen.size() > 40, "expected a rich prop catalogue, saw " + seen.size() + ": " + seen.keySet());
        assertTrue(maxParts <= 32, "largest model " + biggest + " uses " + maxParts + " entities");
        System.out.println("decoration models in use: " + seen.size() + " largest=" + biggest + "(" + maxParts + ")");
    }

    /** Everything /backrooms assets offers builds, and so does every piece of a generated slide. */
    @Test
    void everyAssetAndSlidePieceBuilds() {
        for (String id : com.powderpass.backrooms.display.AssetCatalog.all()) {
            Model m = ModelLibrary.build(id, 4242L);
            assertNotNull(m, "asset " + id + " does not build");
            // some assets are deliberately entity-free: block-built panels (panel_light) and pure particle/sound emitters
            boolean blockBuilt = id.equals("panel_light") || id.startsWith("panel_light:");
            assertTrue(m.entityCount() > 0 || !m.emitters.isEmpty() || blockBuilt, "asset " + id + " is empty");
        }
        LevelGenerator g = new com.powderpass.backrooms.gen.levels.PoolroomsGenerator(99,
                new GenSettings(Map.of("slide-chance", 0.6, "fun-slide-chance", 0.5)));
        int pieces = 0;
        for (int cx = -15; cx < 15; cx++) {
            for (int cz = -15; cz < 15; cz++) {
                for (DecorSpec d : g.decorations(cx, cz)) {
                    if (!d.model.startsWith("slide_seg")) continue;
                    Model m = ModelLibrary.build(d.model, d.seed);
                    assertNotNull(m, d.model);
                    assertTrue(m.entityCount() <= 40, d.model + " uses " + m.entityCount() + " entities");
                    pieces++;
                }
            }
        }
        assertTrue(pieces > 20, "expected slide pieces near the origin, found " + pieces);
    }

    /** Paint real chunks and resolve every block key through the server's block-data parser. */
    @Test
    void everyGeneratorBlockStateParses() {
        java.util.Set<String> states = new java.util.TreeSet<>();
        java.util.Set<String> unverifiable = new java.util.TreeSet<>();
        for (Level level : Level.values()) {
            LevelGenerator g = Generators.create(level, 4242, GenSettings.defaults());
            for (int cx = -10; cx < 10; cx++) {
                for (int cz = -10; cz < 10; cz++) {
                    com.powderpass.backrooms.gen.MemoryCanvas c = new com.powderpass.backrooms.gen.MemoryCanvas(-64, 320);
                    g.paint(c, cx, cz);
                    for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 50; y < 90; y++) {
                        com.powderpass.backrooms.gen.B b = c.get(x, y, z);
                        if (states.add(b.toString())) {
                            try {
                                assertNotNull(b.data(), "unparseable block state " + b);
                                assertEquals(b.mat, b.data().getMaterial(), "state " + b + " resolved to another material");
                            } catch (IllegalArgumentException e) {
                                // MockBukkit lacks property tables for some blocks; only tolerate keys the mock does not know at all
                                String msg = String.valueOf(e.getMessage());
                                assertTrue(msg.startsWith("Can not apply block data key"), "invalid state " + b + ": " + msg);
                                unverifiable.add(b.toString());
                            }
                        }
                    }
                }
            }
        }
        System.out.println("distinct generator block states: " + states.size() + "; not verifiable by the mock: " + unverifiable);
        assertTrue(states.size() > 40, "only " + states.size() + " states");
    }

    @Test
    void decorationDensityStaysWithinBudget() {
        // runtime caps (config defaults): functional props per chunk, and surface details per chunk on top
        int funcCap = 80, detailCap = 28;
        for (Level level : Level.values()) {
            LevelGenerator g = Generators.create(level, 7, GenSettings.defaults());
            int funcTotal = 0, detailTotal = 0, spawned = 0, chunks = 0, worstFunc = 0;
            for (int cx = -12; cx < 12; cx++) {
                for (int cz = -12; cz < 12; cz++) {
                    int func = 0, det = 0;
                    for (DecorSpec d : g.decorations(cx, cz)) {
                        Model m = ModelLibrary.build(d.model, d.seed);
                        if (m == null) continue;
                        if (d.tier == 0) func += m.entityCount();
                        else det += m.entityCount();
                    }
                    funcTotal += func;
                    detailTotal += det;
                    spawned += Math.min(func, funcCap) + Math.min(det, detailCap);
                    chunks++;
                    worstFunc = Math.max(worstFunc, func);
                }
            }
            double avgFunc = funcTotal / (double) chunks, avgDetail = detailTotal / (double) chunks, avgSpawned = spawned / (double) chunks;
            System.out.printf("%s: functional %.1f/chunk (worst %d), details %.1f/chunk, spawned after caps %.1f/chunk%n",
                    level, avgFunc, worstFunc, avgDetail, avgSpawned);
            assertTrue(avgFunc < 28, level + " functional density too high: " + avgFunc);
            assertTrue(avgDetail < detailCap, level + " detail density above its cap on average: " + avgDetail);
            assertTrue(avgSpawned < 50, level + " spawned density too high: " + avgSpawned);
        }
    }
}
