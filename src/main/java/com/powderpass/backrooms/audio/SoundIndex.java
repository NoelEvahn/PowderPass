package com.powderpass.backrooms.audio;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * What the embedded pack contains: per-sound metadata from {@code pack-index.json} (written by
 * {@code tools/audio/build_pack.py}) and the exact set of sound event names registered in the pack's sounds.json, so
 * the engine never sends an id the client does not have.
 */
public final class SoundIndex {
    /** One sound id. {@code profiles}: acoustic variants that exist as {@code id.profile}. */
    public record Def(String id, String category, int durationTicks, boolean loop, boolean positional, int variants,
                      Set<String> profiles, boolean muffled) {
    }

    private final Map<String, Def> defs = new HashMap<>();
    private final Set<String> events = new HashSet<>();

    public static SoundIndex load(InputStream index, InputStream packZip) throws IOException {
        SoundIndex s = new SoundIndex();
        if (index != null) {
            JsonObject root = JsonParser.parseString(new String(index.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject sounds = root.getAsJsonObject("sounds");
            for (Map.Entry<String, JsonElement> e : sounds.entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                Set<String> profiles = new HashSet<>();
                if (o.has("profiles")) for (JsonElement p : o.getAsJsonArray("profiles")) profiles.add(p.getAsString());
                s.defs.put(e.getKey(), new Def(e.getKey(), str(o, "category", "event"), num(o, "duration_ticks", 40),
                        bool(o, "loop"), !o.has("positional") || o.get("positional").getAsBoolean(), num(o, "variants", 1),
                        profiles, bool(o, "muffled")));
            }
        }
        if (packZip != null) {
            try (ZipInputStream z = new ZipInputStream(packZip)) {
                ZipEntry en;
                while ((en = z.getNextEntry()) != null) {
                    if (!en.getName().equals("assets/backrooms/sounds.json")) continue;
                    JsonObject sj = JsonParser.parseString(new String(z.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                    s.events.addAll(sj.keySet());
                    break;
                }
            }
        }
        return s;
    }

    private static String str(JsonObject o, String k, String d) {
        return o.has(k) ? o.get(k).getAsString() : d;
    }

    private static int num(JsonObject o, String k, int d) {
        return o.has(k) ? o.get(k).getAsInt() : d;
    }

    private static boolean bool(JsonObject o, String k) {
        return o.has(k) && o.get(k).getAsBoolean();
    }

    public Def def(String id) {
        return defs.get(id);
    }

    public boolean has(String event) {
        return events.contains(event);
    }

    public Set<String> ids() {
        return defs.keySet();
    }

    public Set<String> events() {
        return events;
    }

    public int size() {
        return defs.size();
    }

    /**
     * The most specific registered event for an id: {@code id.profile.muffled}, {@code id.profile},
     * {@code id.muffled}, then {@code id}. Returns null if nothing is registered.
     */
    public String resolve(String id, String profile, boolean muffled) {
        if (muffled && profile != null && has(id + "." + profile + ".muffled")) return id + "." + profile + ".muffled";
        if (profile != null && has(id + "." + profile)) return id + "." + profile;
        if (muffled && has(id + ".muffled")) return id + ".muffled";
        return has(id) ? id : null;
    }
}
