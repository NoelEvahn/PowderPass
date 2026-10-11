package dev.powderpass.core.anomaly;

/** A rare, deterministic terrain anomaly. All fields are generation metadata usable by locate commands. */
public record Anomaly(
        long id,
        Type type,
        double x, double z,
        double radius,     // characteristic radius (blocks); for escarpments: half length
        double depth,      // depth/height/step (blocks), meaning depends on type
        double level,      // reference level (absolute Y) where relevant (flat floors), NaN otherwise
        double angle,      // orientation (escarpments) / phase
        boolean wet) {     // crater lake / wet pit

    public enum Type {
        SALT_FLAT("salt_flat"),
        IMPACT_CRATER("impact_crater"),
        DEEP_PIT("deep_pit"),
        FAULT_ESCARPMENT("fault_escarpment"),
        CIRCULAR_BASIN("circular_basin"),
        MONOLITH("monolith"),
        BARREN_FLAT("barren_flat");

        public final String key;

        Type(String key) {
            this.key = key;
        }

        public static Type byKey(String k) {
            for (Type t : values()) if (t.key.equals(k)) return t;
            return null;
        }
    }

    public double footprint() {
        return switch (type) {
            case IMPACT_CRATER -> radius * 2.2;
            case SALT_FLAT -> radius * 1.45 + 40;
            case BARREN_FLAT -> radius * 1.35;
            case CIRCULAR_BASIN -> radius * 1.3;
            case FAULT_ESCARPMENT -> radius + 120;
            case DEEP_PIT -> radius * 1.6 + 10;
            case MONOLITH -> radius * 1.8 + 10;
        };
    }
}
