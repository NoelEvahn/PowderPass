package dev.powderpass.core.hydro;

/** Binary min-heap of (double key, int value) with deterministic tie-breaking on value. Allocation-free when warm. */
final class MinHeap {
    private double[] keys;
    private int[] vals;
    private int size;

    MinHeap(int cap) {
        keys = new double[Math.max(16, cap)];
        vals = new int[Math.max(16, cap)];
    }

    void clear() {
        size = 0;
    }

    boolean isEmpty() {
        return size == 0;
    }

    private boolean less(int a, int b) {
        return keys[a] < keys[b] || (keys[a] == keys[b] && vals[a] < vals[b]);
    }

    void push(double k, int v) {
        if (size == keys.length) {
            keys = java.util.Arrays.copyOf(keys, size * 2);
            vals = java.util.Arrays.copyOf(vals, size * 2);
        }
        int i = size++;
        keys[i] = k;
        vals[i] = v;
        while (i > 0) {
            int p = (i - 1) >> 1;
            if (!less(i, p)) break;
            swap(i, p);
            i = p;
        }
    }

    /** Returns the value of the minimum and removes it; key available via {@link #lastKey}. */
    double lastKey;

    int pop() {
        int v = vals[0];
        lastKey = keys[0];
        size--;
        if (size > 0) {
            keys[0] = keys[size];
            vals[0] = vals[size];
            int i = 0;
            while (true) {
                int l = 2 * i + 1, r = l + 1, m = i;
                if (l < size && less(l, m)) m = l;
                if (r < size && less(r, m)) m = r;
                if (m == i) break;
                swap(i, m);
                i = m;
            }
        }
        return v;
    }

    private void swap(int a, int b) {
        double k = keys[a]; keys[a] = keys[b]; keys[b] = k;
        int v = vals[a]; vals[a] = vals[b]; vals[b] = v;
    }
}
