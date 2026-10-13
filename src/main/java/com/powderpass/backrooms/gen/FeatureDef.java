package com.powderpass.backrooms.gen;

/**
 * A district-level authored structure: transitions to other levels, exits or landmarks.
 *
 * @param chance probability that one appears in a given district (1.0 = guaranteed in every district)
 * @param floor  floor index or -1 for any floor
 * @param effect optional special effect id handled by the transition service (e.g. "slide")
 */
public record FeatureDef(String id, Dest dest, double chance, int floor, String effect) {
    public static FeatureDef transition(String id, Dest dest, double chance) {
        return new FeatureDef(id, dest, chance, 0, null);
    }

    public static FeatureDef landmark(String id, double chance) {
        return new FeatureDef(id, null, chance, 0, null);
    }

    public FeatureDef onFloor(int f) {
        return new FeatureDef(id, dest, chance, f, effect);
    }

    public FeatureDef withEffect(String e) {
        return new FeatureDef(id, dest, chance, floor, e);
    }
}
