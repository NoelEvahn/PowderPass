package com.powderpass.backrooms.gen;

/** A placed feature instance; occupies exactly one cell. */
public record Feature(FeatureDef def, int floor, int cx, int cz, int rot, long seed) {
    public String id() {
        return def.id();
    }

    public Dest dest() {
        return def.dest();
    }

    public boolean isTransition() {
        return def.dest() != null;
    }
}
