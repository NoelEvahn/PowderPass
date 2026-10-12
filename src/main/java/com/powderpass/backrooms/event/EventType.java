package com.powderpass.backrooms.event;

import com.powderpass.backrooms.Level;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** Environmental events. Most are atmospheric; steam bursts and power surges are telegraphed hazards. */
public enum EventType {
    FLICKER("flicker", "Nearby lights stutter and buzz.", EnumSet.allOf(Level.class)),
    BLACKOUT("blackout", "The lights die for a few seconds. Things move in the dark.", EnumSet.of(Level.LEVEL_0, Level.LEVEL_1, Level.LEVEL_3, Level.LEVEL_4, Level.LEVEL_5)),
    LIGHTS_OUT("lights_out", "Lights switch off one by one, coming toward you.", EnumSet.of(Level.LEVEL_0, Level.LEVEL_1, Level.LEVEL_4)),
    FOOTSTEPS("footsteps", "Footsteps follow a few paces behind.", EnumSet.allOf(Level.class)),
    KNOCKING("knocking", "Something knocks on the other side of the wall.", EnumSet.of(Level.LEVEL_0, Level.LEVEL_1, Level.LEVEL_4, Level.LEVEL_5)),
    MACHINERY("machinery", "Distant industrial activity.", EnumSet.of(Level.LEVEL_1, Level.LEVEL_2, Level.LEVEL_3)),
    STEAM_BURST("steam_burst", "A pipe hisses, then vents scalding steam (telegraphed).", EnumSet.of(Level.LEVEL_2)),
    POWER_SURGE("power_surge", "A surge: sparks, flicker, standing water becomes live (telegraphed).", EnumSet.of(Level.LEVEL_3)),
    POOL_ECHO("pool_echo", "Echoing splashes and drips across the water.", EnumSet.of(Level.POOLROOMS)),
    PHONE_RING("phone_ring", "A phone rings somewhere nearby.", EnumSet.of(Level.LEVEL_0, Level.LEVEL_4)),
    WALTZ("waltz", "An out-of-tune waltz drifts from somewhere.", EnumSet.of(Level.LEVEL_5)),
    ELEVATOR_DING("elevator_ding", "An elevator arrives. No one gets out.", EnumSet.of(Level.LEVEL_4, Level.LEVEL_5)),
    DEJA_VU("deja_vu", "You've been here before. Something else was too.", EnumSet.allOf(Level.class)),
    HUM_SHIFT("hum_shift", "The fluorescent hum bends in pitch.", EnumSet.of(Level.LEVEL_0, Level.LEVEL_3, Level.LEVEL_4));

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
