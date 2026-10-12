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
        assertTrue(seen.size() > 60, "expected a rich prop catalogue, saw " + seen.size() + ": " + seen.keySet());
        assertTrue(maxParts <= 32, "largest model " + biggest + " uses " + maxParts + " entities");
        System.out.println("decoration models in use: " + seen.size() + " largest=" + biggest + "(" + maxParts + ")");
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
        assertTrue(states.size() > 120);
    }

    @Test
    void decorationDensityStaysWithinBudget() {
        for (Level level : Level.values()) {
            LevelGenerator g = Generators.create(level, 7, GenSettings.defaults());
            int total = 0, chunks = 0, worst = 0;
            for (int cx = -12; cx < 12; cx++) {
                for (int cz = -12; cz < 12; cz++) {
                    int n = 0;
                    for (DecorSpec d : g.decorations(cx, cz)) {
                        Model m = ModelLibrary.build(d.model, d.seed);
                        if (m != null) n += m.entityCount();
                    }
                    total += n;
                    chunks++;
                    worst = Math.max(worst, n);
                }
            }
            double avg = total / (double) chunks;
            System.out.printf("%s: avg %.1f display entities/chunk, worst %d%n", level, avg, worst);
            assertTrue(avg < 25, level + " average density too high: " + avg);
        }
    }
}
