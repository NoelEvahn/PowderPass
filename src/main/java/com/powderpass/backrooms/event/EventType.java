package com.powderpass.backrooms.event;

import com.powderpass.backrooms.Level;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** Environmental events: rare, quiet, and almost never directly dangerous. */
public enum EventType {
    FLICKER("flicker", "Nearby fixtures stutter and buzz.", EnumSet.allOf(Level.class)),
    BLACKOUT("blackout", "The lights die for a few seconds. Things move in the dark (in Level 1, Smilers).", EnumSet.allOf(Level.class)),
    LIGHTS_OUT("lights_out", "Lights switch off one by one, coming toward you.", EnumSet.allOf(Level.class)),
    FOOTSTEPS("footsteps", "Footsteps follow a few paces behind.", EnumSet.allOf(Level.class)),
    KNOCKING("knocking", "Something knocks on the other side of a wall.", EnumSet.of(Level.LEVEL_0, Level.LEVEL_1, Level.LEVEL_4, Level.PLAINS)),
    DOOR_BEHIND("door_behind", "A door somewhere behind you opens. Then closes.", EnumSet.of(Level.LEVEL_0, Level.LEVEL_4, Level.PLAINS)),
    MACHINERY("machinery", "Distant industrial activity.", EnumSet.of(Level.LEVEL_1)),
    POOL_ECHO("pool_echo", "Echoing splashes and drips across the water.", EnumSet.of(Level.POOLROOMS)),
    PHONE_RING("phone_ring", "A phone rings somewhere nearby.", EnumSet.of(Level.LEVEL_0, Level.LEVEL_4, Level.PLAINS)),
    WIND("wind", "Wind across the field, and nothing moves.", EnumSet.of(Level.PLAINS)),
    FOG("fog", "A fog bank rolls in. You can see a few chunks, then less.", EnumSet.of(Level.PLAINS, Level.POOLROOMS, Level.LEVEL_1)),
    DEJA_VU("deja_vu", "You've been here before. Something else was too.", EnumSet.allOf(Level.class)),
    HUM_SHIFT("hum_shift", "The fluorescent hum bends in pitch.", EnumSet.of(Level.LEVEL_0, Level.LEVEL_4, Level.POOLROOMS));

    public final String id;
    public final String description;
    public final Set<Level> levels;

    EventType(String id, String description, Set<Level> levels) {
        this.id = id;
        this.description = description;
        this.levels = levels;
    }

    public static EventType parse(String s) {
        for (EventType t : values()) if (t.id.equalsIgnoreCase(s) || t.name().equalsIgnoreCase(s.toUpperCase(Locale.ROOT))) return t;
        return null;
    }
}
