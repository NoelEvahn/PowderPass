package com.powderpass.backrooms.gen;

/** Opening in a cell wall line. {@code start..start+width-1} are open positions along the line. */
public record Door(boolean open, int start, int width, boolean full) {
    public static final Door CLOSED = new Door(false, 0, 0, false);

    public boolean covers(int i) {
        return open && i >= start && i < start + width;
    }

    public int end() {
        return start + width - 1;
    }
}
