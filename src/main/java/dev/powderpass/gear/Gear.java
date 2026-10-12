package dev.powderpass.gear;

/** A specific piece of gear: type + tier. */
public record Gear(GearType type, GearTier tier) {

    public String id() {
        return type.id() + ":" + tier.id();
    }

    public String displayName() {
        return tier.display() + " " + type.display();
    }

    public static Gear parse(String id) {
        if (id == null) {
            return null;
        }
        int colon = id.indexOf(':');
        if (colon < 0) {
            return null;
        }
        GearType type = GearType.byId(id.substring(0, colon));
        GearTier tier = GearTier.byId(id.substring(colon + 1));
        return type == null || tier == null ? null : new Gear(type, tier);
    }
}
