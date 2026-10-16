package com.powderpass.backrooms.gen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The Backrooms' own textured blocks (see {@code custom-blocks.json}). Each one is a powered note block in a state
 * that ordinary play almost never produces (it needs a mob head on top and redstone power); the resource pack gives
 * those states their textures. Inside the Backrooms note blocks are frozen ({@code CustomBlockGuard}) so the states
 * never change. With {@code textures.custom-blocks: false} every block resolves to its vanilla fallback instead.
 *
 * <p>The switch is read when a generator class is first loaded, so changing it needs a restart, and chunks that
 * were already generated keep the blocks they were made with.</p>
 */
public final class CustomBlocks {
    /** One custom block: its note block state, the vanilla stand-in, and its average colour. */
    public record Def(String id, String instrument, int note, B custom, B fallback, int rgb) {
    }

    private static final Map<String, Def> DEFS;
    private static final Map<String, Def> BY_STATE = new LinkedHashMap<>();
    private static volatile boolean enabled = true;

    static {
        Map<String, Def> m = new LinkedHashMap<>();
        try (InputStream in = CustomBlocks.class.getResourceAsStream("/custom-blocks.json")) {
            if (in == null) throw new IllegalStateException("custom-blocks.json missing from the plugin");
            JsonObject blocks = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("blocks");
            for (Map.Entry<String, JsonElement> e : blocks.entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                String inst = o.get("instrument").getAsString();
                int note = o.get("note").getAsInt();
                B custom = B.of(Material.NOTE_BLOCK, state(inst, note));
                Def d = new Def(e.getKey(), inst, note, custom, parse(o.get("fallback").getAsString()),
                        Integer.parseInt(o.get("color").getAsString().substring(1), 16));
                m.put(d.id(), d);
                BY_STATE.put(custom.state, d);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("cannot read custom-blocks.json", ex);
        }
        DEFS = Collections.unmodifiableMap(m);
    }

    private CustomBlocks() {
    }

    static String state(String instrument, int note) {
        return "[instrument=" + instrument + ",note=" + note + ",powered=true]";
    }

    /** "minecraft:bone_block[axis=y]" -> B. */
    private static B parse(String s) {
        String name = s, st = "";
        int i = s.indexOf('[');
        if (i >= 0) {
            name = s.substring(0, i);
            st = s.substring(i);
        }
        Material mat = Material.matchMaterial(name);
        if (mat == null) throw new IllegalStateException("custom-blocks.json: unknown fallback block " + s);
        return B.of(mat, st);
    }

    /** Use the textured blocks (true) or their vanilla fallbacks. Set before any generator is created. */
    public static void setEnabled(boolean on) {
        enabled = on;
    }

    public static boolean enabled() {
        return enabled;
    }

    /** The block to paint for a custom block id (the vanilla fallback when custom blocks are off). */
    public static B get(String id) {
        Def d = DEFS.get(id);
        if (d == null) throw new IllegalArgumentException("unknown custom block " + id);
        return enabled ? d.custom() : d.fallback();
    }

    /** Always the textured version, whatever the switch says (display models, client-side swaps). */
    public static B custom(String id) {
        Def d = DEFS.get(id);
        if (d == null) throw new IllegalArgumentException("unknown custom block " + id);
        return d.custom();
    }

    public static Map<String, Def> all() {
        return DEFS;
    }

    /** The custom block a painted key stands for, or null. */
    public static Def of(B b) {
        return b.mat == Material.NOTE_BLOCK ? BY_STATE.get(b.state) : null;
    }

    /** The custom block a live block state is, or null. */
    public static Def of(BlockData data) {
        if (data.getMaterial() != Material.NOTE_BLOCK) return null;
        String s = data.getAsString();
        int i = s.indexOf('[');
        return i < 0 ? null : BY_STATE.get(s.substring(i));
    }
}
