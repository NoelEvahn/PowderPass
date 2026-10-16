package com.powderpass.backrooms.audio;

import com.powderpass.backrooms.Level;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What each level sounds like between its loops: one-shot events with weights, cooldowns and placement. Rare events
 * are scaled by {@code audio.rare-frequency}; voices and music also share their own long cooldowns in the engine.
 */
final class Soundscape {
    enum Kind { ONE, STEPS, VOICE, MUSIC }

    enum Place { LISTENER, BEHIND, NEAR, MID, FAR }

    /** One possible event. {@code id} is the event's identity for cooldowns; {@code sound} the pack id. */
    record Ev(String id, String sound, Kind kind, Place placement, double weight, int cooldownSeconds, float volume,
              boolean rare, boolean reflect, boolean underwaterOnly) {
        static Ev one(String sound, Place p, double w, int cd, float vol) {
            return new Ev(sound, sound, Kind.ONE, p, w, cd, vol, false, false, false);
        }

        Ev rarely() {
            return new Ev(id, sound, kind, placement, weight, cooldownSeconds, volume, true, reflect, underwaterOnly);
        }

        Ev reflecting() {
            return new Ev(id, sound, kind, placement, weight, cooldownSeconds, volume, rare, true, underwaterOnly);
        }

        Ev as(Kind k) {
            return new Ev(id, sound, k, placement, weight, cooldownSeconds, volume, rare, reflect, underwaterOnly);
        }

        Ev named(String newId) {
            return new Ev(newId, sound, kind, placement, weight, cooldownSeconds, volume, rare, reflect, underwaterOnly);
        }
    }

    private static final Map<Level, List<Ev>> TABLE = new EnumMap<>(Level.class);

    static {
        TABLE.put(Level.LEVEL_0, List.of(
                Ev.one("light.flicker", Place.NEAR, 5, 40, 0.5f),
                Ev.one("light.pop", Place.MID, 2, 90, 0.5f),
                Ev.one("knock.distant", Place.FAR, 2, 120, 0.8f).reflecting(),
                Ev.one("step.carpet", Place.MID, 2, 150, 0.6f).as(Kind.STEPS).rarely(),
                Ev.one("impact.distant", Place.FAR, 1, 300, 0.9f).rarely().reflecting(),
                Ev.one("voice.whisper", Place.BEHIND, 1, 400, 0.35f).as(Kind.VOICE).rarely(),
                Ev.one("light.arc", Place.MID, 0.6, 400, 0.6f).rarely(),
                Ev.one("movement.distant", Place.FAR, 1, 240, 0.7f).rarely(),
                Ev.one("music.lobby_1", Place.LISTENER, 0.25, 1200, 0.35f).as(Kind.MUSIC).rarely(),
                Ev.one("music.lobby_2", Place.LISTENER, 0.25, 1200, 0.35f).as(Kind.MUSIC).rarely()));
        TABLE.put(Level.POOLROOMS, List.of(
                Ev.one("water.drip", Place.NEAR, 6, 15, 0.6f),
                Ev.one("water.drip", Place.FAR, 4, 15, 0.5f).named("water.drip.far"),
                Ev.one("water.splash", Place.FAR, 1.2, 180, 0.7f).rarely(),
                Ev.one("water.bubbles", Place.NEAR, 1.5, 60, 0.5f),
                Ev.one("step.tile", Place.MID, 1, 240, 0.5f).as(Kind.STEPS).rarely(),
                Ev.one("voice.murmur", Place.FAR, 0.6, 500, 0.4f).as(Kind.VOICE).rarely(),
                Ev.one("music.pool_1", Place.LISTENER, 0.25, 900, 0.5f).as(Kind.MUSIC).rarely()));
        TABLE.put(Level.LEVEL_1, List.of(
                Ev.one("metal.creak", Place.MID, 4, 60, 0.6f).reflecting(),
                Ev.one("pipe.knock", Place.MID, 3, 90, 0.7f).reflecting(),
                Ev.one("water.drip", Place.NEAR, 3, 30, 0.5f),
                Ev.one("machine.start", Place.FAR, 1, 400, 0.9f).rarely().reflecting(),
                Ev.one("step.concrete", Place.FAR, 1.5, 200, 0.7f).as(Kind.STEPS).rarely(),
                Ev.one("knock.distant", Place.FAR, 1, 200, 0.8f).reflecting(),
                Ev.one("voice.conversation", Place.FAR, 0.6, 500, 0.5f).as(Kind.VOICE).rarely(),
                Ev.one("impact.distant", Place.FAR, 0.8, 400, 1.0f).rarely().reflecting(),
                Ev.one("steam.burst", Place.MID, 2.5, 90, 0.7f).reflecting(),
                Ev.one("metal.clank", Place.FAR, 1.8, 150, 0.8f).reflecting(),
                Ev.one("metal.groan", Place.FAR, 1.2, 240, 0.8f).reflecting(),
                Ev.one("music.garage_1", Place.LISTENER, 0.25, 1200, 0.4f).as(Kind.MUSIC).rarely()));
        TABLE.put(Level.LEVEL_4, List.of(
                Ev.one("office.click", Place.MID, 4, 40, 0.5f),
                Ev.one("office.printer", Place.FAR, 1.5, 300, 0.6f),
                Ev.one("office.phone", Place.FAR, 1, 400, 0.7f).rarely(),
                Ev.one("speaker.broken", Place.MID, 1, 300, 0.4f).rarely(),
                Ev.one("step.carpet", Place.MID, 1.2, 240, 0.6f).as(Kind.STEPS).rarely(),
                Ev.one("voice.conversation", Place.MID, 0.8, 420, 0.45f).as(Kind.VOICE).rarely(),
                Ev.one("voice.announce", Place.FAR, 0.4, 900, 0.6f).as(Kind.VOICE).rarely(),
                Ev.one("voice.phrase", Place.FAR, 0.3, 900, 0.45f).as(Kind.VOICE).rarely(),
                Ev.one("music.office_radio_1", Place.LISTENER, 0.2, 1200, 0.25f).as(Kind.MUSIC).rarely()));
        TABLE.put(Level.PLAINS, List.of(
                Ev.one("grass.rustle", Place.NEAR, 3, 60, 0.4f),
                Ev.one("plains.distant", Place.FAR, 0.8, 400, 0.8f).rarely(),
                Ev.one("music.plains_1", Place.LISTENER, 0.25, 1200, 0.45f).as(Kind.MUSIC).rarely(),
                Ev.one("music.plains_2", Place.LISTENER, 0.25, 1200, 0.45f).as(Kind.MUSIC).rarely(),
                Ev.one("voice.cry", Place.FAR, 0.15, 1800, 0.3f).as(Kind.VOICE).rarely()));
    }

    /** One layer of a level's constant bed: a long seamless loop played on the listener. */
    record Bed(String sound, float volume) {
    }

    private static final Map<Level, List<Bed>> BEDS = new EnumMap<>(Level.class);

    static {
        BEDS.put(Level.LEVEL_0, List.of(new Bed("bed.level0", 0.5f)));
        BEDS.put(Level.POOLROOMS, List.of(new Bed("bed.poolrooms", 0.55f)));
        BEDS.put(Level.LEVEL_1, List.of(new Bed("bed.garage", 0.55f)));
        BEDS.put(Level.LEVEL_4, List.of(new Bed("bed.office", 0.5f)));
        BEDS.put(Level.PLAINS, List.of(new Bed("bed.plains", 0.55f)));
    }

    private static final Map<Level, List<Ev>> ANNOUNCE = new EnumMap<>(Level.class);

    static {
        // roughly every 25 s (audio.announce-seconds): the building talks (PA, on the listener with ceiling echoes),
        // or someone does (positional, from where they are)
        ANNOUNCE.put(Level.LEVEL_0, List.of(
                Ev.one("voice.announce", Place.FAR, 4, 0, 0.55f).as(Kind.VOICE).named("announce"),
                Ev.one("voice.phrase", Place.FAR, 2, 0, 0.4f).as(Kind.VOICE).named("announce.phrase"),
                Ev.one("voice.whisper", Place.BEHIND, 1, 0, 0.3f).as(Kind.VOICE).named("announce.whisper")));
        ANNOUNCE.put(Level.POOLROOMS, List.of(
                Ev.one("voice.announce", Place.FAR, 4, 0, 0.6f).as(Kind.VOICE).named("announce"),
                Ev.one("voice.murmur", Place.FAR, 1.5, 0, 0.4f).as(Kind.VOICE).named("announce.murmur"),
                Ev.one("voice.phrase", Place.FAR, 1.5, 0, 0.4f).as(Kind.VOICE).named("announce.phrase")));
        ANNOUNCE.put(Level.LEVEL_1, List.of(
                Ev.one("voice.announce", Place.FAR, 4, 0, 0.7f).as(Kind.VOICE).named("announce"),
                Ev.one("voice.radio", Place.MID, 2, 0, 0.5f).as(Kind.VOICE).named("announce.radio"),
                Ev.one("voice.conversation", Place.FAR, 1.5, 0, 0.5f).as(Kind.VOICE).named("announce.talk")));
        ANNOUNCE.put(Level.LEVEL_4, List.of(
                Ev.one("voice.announce", Place.FAR, 4, 0, 0.6f).as(Kind.VOICE).named("announce"),
                Ev.one("voice.conversation", Place.MID, 2, 0, 0.45f).as(Kind.VOICE).named("announce.talk"),
                Ev.one("voice.phrase", Place.FAR, 1.5, 0, 0.4f).as(Kind.VOICE).named("announce.phrase")));
    }

    /** The voices and PA announcements of a level (none in the Plains). */
    static List<Ev> announcements(Level level) {
        return ANNOUNCE.getOrDefault(level, List.of());
    }

    static List<Bed> beds(Level level) {
        return BEDS.getOrDefault(level, List.of());
    }

    static List<Ev> events(Level level) {
        return TABLE.getOrDefault(level, List.of());
    }
}
