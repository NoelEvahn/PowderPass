package dev.powderpass.core.geo;

/** Geological provinces. Resistance is the relative resistance of exposed bedrock to erosion (1 = average). */
public enum Province {
    OCEAN_BASIN(1.0),
    COASTAL_LOWLAND(0.6),
    SEDIMENTARY_BASIN(0.75),
    SHIELD(2.6),
    YOUNG_OROGEN(1.8),
    OLD_OROGEN(2.2),
    PLATEAU(1.3),
    VOLCANIC(2.4),
    RIFT(0.9),
    KARST(1.1);

    public final double resistance;

    Province(double resistance) {
        this.resistance = resistance;
    }

    private static final Province[] VALUES = values();

    public static Province byId(int id) {
        return VALUES[id];
    }
}
