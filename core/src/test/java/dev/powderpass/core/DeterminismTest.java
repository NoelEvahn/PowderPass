package dev.powderpass.core;

import dev.powderpass.core.deco.Decorator;
import dev.powderpass.core.deco.Placements;
import dev.powderpass.core.terrain.ChunkComposer;
import dev.powderpass.core.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Same seed + settings + coordinates must give identical blocks regardless of request order, cache state,
 * concurrency, or coordinate sign.
 */
class DeterminismTest {
    private static final int MIN_Y = -64, HEIGHT = 832;

    private static short[] chunk(PowderWorld w, int cx, int cz) {
        ChunkComposer comp = w.attachment(ww -> new ChunkComposer(ww, MIN_Y, HEIGHT));
        return comp.compose(cx, cz).blocks;
    }

    private static String deco(PowderWorld w, int cx, int cz) {
        ChunkComposer comp = w.attachment(ww -> new ChunkComposer(ww, MIN_Y, HEIGHT));
        Placements p = new Decorator(comp).decorate(cx, cz, comp.compose(cx, cz));
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < p.size; i++) lines.add(p.x[i] + "," + p.y[i] + "," + p.z[i] + "=" + Placements.state(p.st[i]) + "/" + p.mode[i]);
        Collections.sort(lines);
        return String.join("\n", lines);
    }

    @Test
    void freshWorldsDifferentOrderSameBlocks() {
        int[][] chunks = {{0, 0}, {-1, -1}, {37, -12}, {-55, 40}, {3, 3}, {-2, 5}};
        PowderWorld a = new PowderWorld(TestWorlds.SEED_B, GenSettings.defaults(), 1L << 30, true);
        List<short[]> first = new ArrayList<>();
        for (int[] c : chunks) first.add(chunk(a, c[0], c[1]));
        // Fresh world, reversed request order, and a tiny simulation cache that forces eviction/recompute.
        PowderWorld b = new PowderWorld(TestWorlds.SEED_B, GenSettings.defaults(), 6L << 20, true);
        for (int i = chunks.length - 1; i >= 0; i--) {
            assertArrayEquals(first.get(i), chunk(b, chunks[i][0], chunks[i][1]), "chunk " + Arrays.toString(chunks[i]));
        }
        // Again after eviction pressure.
        for (int i = 0; i < chunks.length; i++) {
            assertArrayEquals(first.get(i), chunk(b, chunks[i][0], chunks[i][1]), "chunk after eviction " + Arrays.toString(chunks[i]));
        }
    }

    @Test
    void concurrentGenerationMatchesSequential() throws Exception {
        PowderWorld seq = new PowderWorld(TestWorlds.SEED_A, GenSettings.defaults(), 1L << 30, true);
        List<int[]> chunks = new ArrayList<>();
        for (int cz = -3; cz <= 2; cz++) for (int cx = -3; cx <= 2; cx++) chunks.add(new int[]{cx * 7, cz * 5});
        List<short[]> expect = new ArrayList<>();
        for (int[] c : chunks) expect.add(chunk(seq, c[0], c[1]));
        PowderWorld par = new PowderWorld(TestWorlds.SEED_A, GenSettings.defaults(), 1L << 30, true);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) order.add(i);
        Collections.shuffle(order, new Random(99));
        ExecutorService ex = Executors.newFixedThreadPool(4);
        try {
            List<Future<short[]>> fs = new ArrayList<>();
            for (int i : order) fs.add(ex.submit(() -> chunk(par, chunks.get(i)[0], chunks.get(i)[1])));
            for (int t = 0; t < order.size(); t++) {
                int i = order.get(t);
                assertArrayEquals(expect.get(i), fs.get(t).get(), "chunk " + Arrays.toString(chunks.get(i)));
            }
        } finally {
            ex.shutdownNow();
        }
    }

    @Test
    void decorationIndependentOfOrder() {
        PowderWorld a = new PowderWorld(TestWorlds.SEED_A, GenSettings.defaults(), 1L << 30, true);
        String d1 = deco(a, 10, -4) + deco(a, 11, -4);
        PowderWorld b = new PowderWorld(TestWorlds.SEED_A, GenSettings.defaults(), 1L << 30, true);
        String d2b = deco(b, 11, -4);
        String d1b = deco(b, 10, -4);
        assertEquals(d1, d1b + d2b);
    }

    @Test
    void negativeCoordinatesAndColumnsStable() {
        PowderWorld w = TestWorlds.world(TestWorlds.SEED_A);
        TerrainSampler s1 = new TerrainSampler(w);
        TerrainSampler s2 = new TerrainSampler(new PowderWorld(TestWorlds.SEED_A, GenSettings.defaults(), 6L << 20, true));
        TerrainSampler.Column c1 = new TerrainSampler.Column(), c2 = new TerrainSampler.Column();
        Random r = new Random(5);
        for (int i = 0; i < 120; i++) {
            double x = r.nextInt(3000) - 1500 - 0.0, z = r.nextInt(3000) - 1500;
            if (i % 3 == 0) { x = -x - 1; }
            s1.sample(x, z, c1);
            s2.sample(x, z, c2);
            assertEquals(c1.height, c2.height, 0.0, "height at " + x + "," + z);
            assertEquals(Double.doubleToLongBits(c1.waterLevel), Double.doubleToLongBits(c2.waterLevel), "water at " + x + "," + z);
        }
    }
}
