package dev.powderpass.core.deco;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * A list of block placements (world coordinates + block-state string such as
 * {@code minecraft:oak_leaves[distance=2,persistent=false]}). State strings are interned to small ints so the mod
 * parses each distinct state once.
 */
public final class Placements {
    public static final int MODE_AIR = 0;        // only into air / replaceable plants
    public static final int MODE_REPLACE = 1;    // replace anything except bedrock (used for embedded boulders)
    public static final int MODE_SOLID = 2;      // only replace solid ground blocks (surface tweaks)
    public static final int MODE_WATER = 3;      // only replace water source blocks (submerged plants)

    private static final Map<String, Integer> INTERN = new HashMap<>();
    private static String[] STATES = new String[64];
    private static int stateCount;

    public static synchronized int intern(String state) {
        Integer i = INTERN.get(state);
        if (i != null) return i;
        if (stateCount == STATES.length) STATES = Arrays.copyOf(STATES, stateCount * 2);
        STATES[stateCount] = state;
        INTERN.put(state, stateCount);
        return stateCount++;
    }

    public static synchronized String state(int id) {
        return STATES[id];
    }

    public static synchronized int stateCount() {
        return stateCount;
    }

    public int[] x = new int[256], y = new int[256], z = new int[256], st = new int[256];
    public byte[] mode = new byte[256];
    public int size;

    public void add(int px, int py, int pz, int stateId, int m) {
        if (size == x.length) {
            int n = size * 2;
            x = Arrays.copyOf(x, n); y = Arrays.copyOf(y, n); z = Arrays.copyOf(z, n);
            st = Arrays.copyOf(st, n); mode = Arrays.copyOf(mode, n);
        }
        x[size] = px; y[size] = py; z[size] = pz; st[size] = stateId; mode[size] = (byte) m;
        size++;
    }

    public void add(int px, int py, int pz, String state, int m) {
        add(px, py, pz, intern(state), m);
    }
}
