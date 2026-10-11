package dev.powderpass.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.powderpass.core.GenSettings;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code config/powderpass.json}. Two sections:
 * <ul>
 *   <li>{@code new_world} - generation settings used only when a new world is created with the PowderPass preset.
 *       They are then stored in that world's level data and never re-read for it.</li>
 *   <li>{@code runtime} - safely reloadable behaviour (volcano activity, performance limits). {@code /powderpass reload}
 *       re-reads it.</li>
 * </ul>
 */
public final class PowderConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final class Runtime {
        public boolean volcanoActivity = true;
        /** Allow eruptions on terrain that is not provably pristine. Vanilla lava and fire can then destroy builds. */
        public boolean destructiveVolcanism = false;
        public boolean ambientEffects = true;
        public int maxActiveEruptions = 1;
        public int lavaPlacementsPerTick = 3;
        public int eruptionLavaBudget = 180;
        public double dormancyDaysMin = 4;
        public double dormancyDaysMax = 12;
        public double unrestDays = 1.0;
        public int eruptionTicks = 9000;
        public int coolingTicks = 6000;
        /** Chunks where players have spent longer than this (ticks) are treated as possibly modified. */
        public long inhabitedTicksThreshold = 6000;
        public int simulationCacheMb = 384;
        public int volcanoScanRadius = 640;
    }

    public GenSettings newWorld = GenSettings.defaults();
    public Runtime runtime = new Runtime();

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("powderpass.json");
    }

    public static PowderConfig load() {
        PowderConfig cfg = new PowderConfig();
        Path p = path();
        try {
            if (Files.exists(p)) {
                try (Reader r = Files.newBufferedReader(p)) {
                    JsonObject root = GSON.fromJson(r, JsonObject.class);
                    if (root != null && root.has("new_world")) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("new_world").entrySet()) {
                            JsonPrimitive v = e.getValue().getAsJsonPrimitive();
                            m.put(e.getKey(), v.isBoolean() ? (Object) v.getAsBoolean() : (Object) v.getAsDouble());
                        }
                        cfg.newWorld = GenSettings.fromMap(m);
                    }
                    if (root != null && root.has("runtime")) {
                        cfg.runtime = GSON.fromJson(root.get("runtime"), Runtime.class);
                    }
                }
            }
        } catch (Exception e) {
            PowderPassMod.LOGGER.error("PowderPass: could not read {} ({}); using defaults", p, e.toString());
        }
        cfg.validate();
        cfg.save();
        return cfg;
    }

    void validate() {
        Runtime r = runtime;
        r.maxActiveEruptions = Math.max(0, Math.min(8, r.maxActiveEruptions));
        r.lavaPlacementsPerTick = Math.max(1, Math.min(32, r.lavaPlacementsPerTick));
        r.eruptionLavaBudget = Math.max(0, Math.min(2000, r.eruptionLavaBudget));
        r.dormancyDaysMin = Math.max(0.05, r.dormancyDaysMin);
        r.dormancyDaysMax = Math.max(r.dormancyDaysMin, r.dormancyDaysMax);
        r.unrestDays = Math.max(0.01, r.unrestDays);
        r.eruptionTicks = Math.max(200, Math.min(72000, r.eruptionTicks));
        r.coolingTicks = Math.max(200, Math.min(72000, r.coolingTicks));
        r.simulationCacheMb = Math.max(64, Math.min(8192, r.simulationCacheMb));
        r.volcanoScanRadius = Math.max(128, Math.min(4096, r.volcanoScanRadius));
        newWorld = newWorld.validated();
    }

    public void save() {
        JsonObject root = new JsonObject();
        JsonObject nw = new JsonObject();
        for (Map.Entry<String, Object> e : newWorld.toMap().entrySet()) {
            Object v = e.getValue();
            if (v instanceof Boolean b) nw.addProperty(e.getKey(), b);
            else nw.addProperty(e.getKey(), (Number) v);
        }
        root.addProperty("_comment", "new_world applies only when creating a new world with the PowderPass preset; "
                + "it is then stored in that world. runtime is reloadable with /powderpass reload. terrain_cap can be lowered but never raised above 700.");
        root.add("new_world", nw);
        root.add("runtime", GSON.toJsonTree(runtime));
        try {
            Files.createDirectories(path().getParent());
            try (Writer w = Files.newBufferedWriter(path())) {
                GSON.toJson(root, w);
            }
        } catch (IOException e) {
            PowderPassMod.LOGGER.error("PowderPass: could not write {}: {}", path(), e.toString());
        }
    }
}
