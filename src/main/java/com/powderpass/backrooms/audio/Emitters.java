package com.powderpass.backrooms.audio;

import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.gen.DecorSpec;
import com.powderpass.backrooms.gen.LevelGenerator;
import com.powderpass.backrooms.util.Rng;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Physical sound sources, derived from the same deterministic layout data as the display props, so every hum has a
 * fixture above it and every radio sits on a desk. Computed per chunk on demand and cached (bounded LRU); nothing
 * is stored in the world and nothing is scanned world-wide.
 */
final class Emitters {
    /** A looping physical source. {@code radius} is where it stops being scheduled for a listener. */
    record Emitter(String key, double x, double y, double z, String sound, float volume, double radius, Kind kind) {
    }

    enum Kind { HUM, MACHINE, WATER, AIR, MUSIC }

    private final Map<String, List<Emitter>> cache = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<Emitter>> e) {
            return size() > 768;
        }
    };

    List<Emitter> chunk(String world, Level level, LevelGenerator gen, int cx, int cz) {
        String key = world + ":" + cx + ":" + cz;
        List<Emitter> list = cache.get(key);
        if (list == null) {
            list = build(level, gen, cx, cz);
            cache.put(key, list);
        }
        return list;
    }

    void clear() {
        cache.clear();
    }

    private static String base(String model) {
        int c = model.indexOf(':');
        return c < 0 ? model : model.substring(0, c);
    }

    private static List<Emitter> build(Level level, LevelGenerator gen, int cx, int cz) {
        List<Emitter> out = new ArrayList<>();
        long ch = Rng.hash(0xA0D10L, cx, cz);
        boolean musicPlaced = false;
        for (DecorSpec d : gen.decorations(cx, cz)) {
            String m = d.model;
            String b = base(m);
            boolean dead = m.endsWith(":dead");
            String k = d.key();
            switch (b) {
                case "ceiling_panel", "panel_light", "panel_flicker", "tube_light", "cage_lamp", "wall_lamp" -> {
                    if (dead) break;
                    float v = level == Level.LEVEL_0 ? 0.35f : 0.22f;
                    out.add(new Emitter(k, d.x, d.y - 0.4, d.z, "hum.fluorescent", v, level == Level.LEVEL_0 ? 11 : 8, Kind.HUM));
                    // very rarely, music is coming from somewhere just past the lights: always behind a wall
                    if (level == Level.LEVEL_0 && !musicPlaced && Rng.chance(Rng.hash(ch, 1), 0.012)) {
                        musicPlaced = true;
                        out.add(new Emitter(k + "#music", d.x, d.y - 2, d.z, Rng.chance(ch, 0.5) ? "music.lobby_1" : "music.lobby_2", 1.4f, 22, Kind.MUSIC));
                    }
                }
                case "pool_lamp" -> out.add(new Emitter(k, d.x, d.y + 1, d.z, "water.lap", 0.45f, 14, Kind.WATER));
                case "caustics" -> {
                    if (Rng.chance(Rng.hash(ch, 2), 0.3)) out.add(new Emitter(k, d.x, d.y - 6, d.z, "water.circulation", 0.4f, 16, Kind.WATER));
                }
                case "skylight_panel" -> {
                    if (level == Level.POOLROOMS && !musicPlaced && Rng.chance(Rng.hash(ch, 3), 0.025)) {
                        musicPlaced = true;
                        out.add(new Emitter(k + "#music", d.x, d.y - 3, d.z, Rng.chance(ch, 0.5) ? "music.pool_1" : "music.pool_2", 2.2f, 34, Kind.MUSIC));
                    }
                }
                case "drip_emitter" -> out.add(new Emitter(k, d.x, d.y - 2, d.z, "room.damp", 0.3f, 10, Kind.WATER));
                case "crt_monitor" -> {
                    // an old radio left on a desk in a few offices, playing to nobody
                    if (level == Level.LEVEL_4 && !musicPlaced && Rng.chance(Rng.hash(ch, 4), 0.06)) {
                        musicPlaced = true;
                        out.add(new Emitter(k + "#radio", d.x, d.y, d.z, Rng.chance(ch, 0.5) ? "music.office_radio_1" : "music.office_radio_2", 1.6f, 26, Kind.MUSIC));
                    }
                }
                case "d" -> {
                    String kind = m.length() > 2 ? m.substring(2, m.indexOf(':', 2) < 0 ? m.length() : m.indexOf(':', 2)) : "";
                    switch (kind) {
                        case "duct" -> {
                            out.add(new Emitter(k, d.x + 7, d.y - 0.3, d.z, "vent.hvac", 0.4f, 14, Kind.AIR));
                            if (level == Level.LEVEL_1 && !musicPlaced && Rng.chance(Rng.hash(ch, 5), 0.05)) {
                                musicPlaced = true;
                                out.add(new Emitter(k + "#music", d.x + 7, d.y - 1, d.z, "music.garage_1", 2.0f, 32, Kind.MUSIC));
                            }
                        }
                        case "jbox" -> out.add(new Emitter(k, d.x, d.y, d.z, "transformer.hum", 0.25f, 6, Kind.MACHINE));
                        case "vent" -> out.add(new Emitter(k, d.x, d.y - 0.2, d.z, "office.ac", 0.3f, 9, Kind.AIR));
                        case "grille" -> out.add(new Emitter(k, d.x, d.y, d.z, "vent.small", 0.22f, 6, Kind.AIR));
                        default -> {
                        }
                    }
                }
                default -> {
                }
            }
        }
        // the garage hums with machinery that is always somewhere else: one heavy source per few chunks
        if (level == Level.LEVEL_1 && Rng.chance(Rng.hash(ch, 6), 0.12)) {
            out.add(new Emitter("machine@" + cx + "," + cz, cx * 16 + 8, gen.floorY[0] + 2, cz * 16 + 8, "machine.distant", 1.5f, 26, Kind.MACHINE));
        }
        return out;
    }
}
