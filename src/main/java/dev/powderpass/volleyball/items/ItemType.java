package dev.powderpass.volleyball.items;

public enum ItemType {
    VOLLEYBALL, WHISTLE, HANDS;

    public static ItemType byName(String s) {
        for (ItemType t : values()) {
            if (t.name().equalsIgnoreCase(s)) {
                return t;
            }
        }
        return null;
    }
}
