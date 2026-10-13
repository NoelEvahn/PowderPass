package com.powderpass.backrooms.gen;

import java.util.HashMap;
import java.util.Map;

/** Numeric generation tuning for one level (snapshotted at world creation to keep worlds seamless). */
public final class GenSettings {
    private final Map<String, Double> values;

    public GenSettings(Map<String, Double> values) {
        this.values = new HashMap<>(values);
    }

    public static GenSettings defaults() {
        return new GenSettings(Map.of());
    }

    public double get(String key, double def) {
        Double v = values.get(key);
        return v == null ? def : v;
    }

    public Map<String, Double> asMap() {
        return Map.copyOf(values);
    }
}
