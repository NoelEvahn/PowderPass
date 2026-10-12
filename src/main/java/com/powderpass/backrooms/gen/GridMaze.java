package com.powderpass.backrooms.gen;

import com.powderpass.backrooms.util.Rng;

import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Infinite, deterministic, globally connected cell maze.
 *
 * <p>The plane of cells is split into square regions of {@code regionSize} cells. Inside every region
 * a random spanning tree (randomised Kruskal) connects all cells. Every pair of neighbouring regions
 * shares between one and three open boundary edges, chosen by a hash of the shared boundary so both
 * sides agree. Extra "loop" edges are opened independently per edge. Because opening edges can only
 * add connectivity, every cell is reachable from every other cell, which guarantees that mandatory
 * exits and transitions can never be sealed off, independent of chunk generation order.</p>
 *
 * <p>Edge convention: {@code east(cx, cz)} is the edge between (cx,cz) and (cx+1,cz);
 * {@code south(cx, cz)} is the edge between (cx,cz) and (cx,cz+1).</p>
 */
public final class GridMaze {
    private static final int CACHE_LIMIT = 4096;

    private final long seed;
    private final int regionSize;
    private final double loopChance;
    private final double echoChance;
    private final Map<Long, boolean[][]> regions = new ConcurrentHashMap<>();

    /**
     * @param echoChance probability that a region repeats the internal layout of its western neighbour
     *                   (the "subtle repetition" of Level 0); 0 disables
     */
    public GridMaze(long seed, int regionSize, double loopChance, double echoChance) {
        this.seed = seed;
        this.regionSize = regionSize;
        this.loopChance = loopChance;
        this.echoChance = echoChance;
    }

    public int regionSize() {
        return regionSize;
    }

    public boolean east(int cx, int cz) {
        int rx = Math.floorDiv(cx, regionSize);
        int rz = Math.floorDiv(cz, regionSize);
        int lx = cx - rx * regionSize;
        int lz = cz - rz * regionSize;
        if (loop(cx, cz, 0)) {
            return true;
        }
        if (lx == regionSize - 1) {
            return boundary(rx, rz, 0, lz);
        }
        return region(rx, rz)[0][lz * regionSize + lx];
    }

    public boolean south(int cx, int cz) {
        int rx = Math.floorDiv(cx, regionSize);
        int rz = Math.floorDiv(cz, regionSize);
        int lx = cx - rx * regionSize;
        int lz = cz - rz * regionSize;
        if (loop(cx, cz, 1)) {
            return true;
        }
        if (lz == regionSize - 1) {
            return boundary(rx, rz, 1, lx);
        }
        return region(rx, rz)[1][lz * regionSize + lx];
    }

    /** Number of open edges around a cell (1 = dead end). */
    public int degree(int cx, int cz) {
        int d = 0;
        if (east(cx, cz)) d++;
        if (east(cx - 1, cz)) d++;
        if (south(cx, cz)) d++;
        if (south(cx, cz - 1)) d++;
        return d;
    }

    private boolean loop(int cx, int cz, int dir) {
        return loopChance > 0 && Rng.chance(Rng.hash(seed ^ 0x100F, cx, cz, dir), loopChance);
    }

    /** Boundary between region (rx,rz) and its east (dir 0) or south (dir 1) neighbour. */
    private boolean boundary(int rx, int rz, int dir, int index) {
        long h = Rng.hash(seed ^ 0xB0DAL, rx, rz, dir);
        int count = 1 + Rng.pick(h, 3);
        for (int i = 0; i < count; i++) {
            if (Rng.pick(Rng.hash(h, i), regionSize) == index) {
                return true;
            }
        }
        return false;
    }

    private boolean[][] region(int rx, int rz) {
        int srcX = rx;
        if (echoChance > 0 && Rng.chance(Rng.hash(seed ^ 0xEC40L, rx, rz), echoChance)) {
            srcX = rx - 1; // déjà vu: identical internal layout to the western neighbour
        }
        long key = ((long) srcX << 32) ^ (rz & 0xffffffffL);
        boolean[][] r = regions.get(key);
        if (r == null) {
            if (regions.size() > CACHE_LIMIT) {
                regions.clear();
            }
            r = buildTree(srcX, rz);
            regions.put(key, r);
        }
        return r;
    }

    private boolean[][] buildTree(int rx, int rz) {
        int n = regionSize;
        boolean[] east = new boolean[n * n];
        boolean[] south = new boolean[n * n];
        int edgeCount = 2 * n * (n - 1);
        int[] edges = new int[edgeCount];
        int e = 0;
        for (int z = 0; z < n; z++) {
            for (int x = 0; x < n; x++) {
                if (x < n - 1) edges[e++] = (z * n + x) << 1;
                if (z < n - 1) edges[e++] = ((z * n + x) << 1) | 1;
            }
        }
        SplittableRandom rnd = new SplittableRandom(Rng.hash(seed ^ 0x7EEEL, rx, rz));
        for (int i = edgeCount - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int t = edges[i];
            edges[i] = edges[j];
            edges[j] = t;
        }
        int[] parent = new int[n * n];
        for (int i = 0; i < parent.length; i++) parent[i] = i;
        for (int edge : edges) {
            int cell = edge >> 1;
            boolean isSouth = (edge & 1) == 1;
            int other = isSouth ? cell + n : cell + 1;
            int a = find(parent, cell);
            int b = find(parent, other);
            if (a != b) {
                parent[a] = b;
                if (isSouth) south[cell] = true;
                else east[cell] = true;
            }
        }
        return new boolean[][]{east, south};
    }

    private static int find(int[] p, int x) {
        while (p[x] != x) {
            p[x] = p[p[x]];
            x = p[x];
        }
        return x;
    }
}
