package com.powderpass.backrooms.creature;

/** The Backrooms ecosystem. Classes: sighting (harmless), stalker (unsettling), threat (can hurt), environmental. */
public enum CreatureType {
    SMILER("smiler", "Smiler", "threat", "Hangs in the dark. Freezes while watched; avoids light; lunges if you look away."),
    FACELING("faceling", "Faceling", "sighting", "Tall, faceless and patient. Stalks at a distance and leaves when stared at."),
    HOUND("hound", "Hound", "threat", "Hunts by sound and sight. Sprinting draws it; sneaking and breaking line of sight lose it. Killable."),
    DRIFTER("drifter", "Drifter", "threat", "Glides under deep water and drags swimmers down. Leave the water or strike it."),
    WATCHER("watcher", "Watcher", "stalker", "A black silhouette at the end of the hall. It only moves while nobody is looking at it."),
    BACTERIA("bacteria", "Bacteria", "threat", "Tall, thin and black, wound in cable-like strands. Hunts by sound and movement: stand completely still and silent and it loses you."),
    SKINWALKER("skinwalker", "Skinwalker", "threat", "Wears other creatures, then people. Locks its victim inside their own body and walks with their friends. Kill its true form to free them.");

    public final String id;
    public final String displayName;
    public final String category;
    public final String description;

    CreatureType(String id, String displayName, String category, String description) {
        this.id = id;
        this.displayName = displayName;
        this.category = category;
        this.description = description;
    }

    public static CreatureType parse(String s) {
        for (CreatureType t : values()) if (t.id.equalsIgnoreCase(s) || t.name().equalsIgnoreCase(s)) return t;
        return null;
    }
}
