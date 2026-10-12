package com.powderpass.backrooms.config;

import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.gen.GenSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Immutable snapshot of config.yml, rebuilt on reload. */
public final class BackroomsConfig {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public final long seed;
    public final String worldPrefix;
    public final Map<Level, Boolean> enabled = new EnumMap<>(Level.class);
    public final Map<Level, String> biome = new EnumMap<>(Level.class);
    public final Map<Level, GenSettings> generation = new EnumMap<>(Level.class);

    public final boolean allowSelfEnter, allowSelfLeave, entryTitle;
    public final List<String> allowedWorlds;
    public final int spreadRadius;
    public final String fallbackWorld;
    public final String deathMode;
    public final boolean keepInventory;

    public final boolean protectBlocks, blockPearls, blockChorus, disablePortals, disableBeds, suppressMusic;
    public final List<String> blockedCommands;

    public final int transitionCooldownTicks, dwellTicks, intentTicks, arrivalProtectionTicks;

    public final boolean displaysEnabled, hazards;
    public final int activationRadius, outdoorActivationRadius, maxPerChunk, maxPerWorld, chunksPerTick, animationInterval;
    public final float viewRange;
    public final boolean detailsEnabled, performanceMode;
    public final boolean fogEnabled;
    // audio
    public final boolean audioEnabled, audioMusic, audioAmbient, audioVoices;
    public final double audioDensity, audioRare, audioMusicFrequency, audioDistanceScale;
    public final int audioMaxSources, audioQuality;
    public final Map<Level, Double> audioLevelIntensity = new EnumMap<>(Level.class);
    // resource pack delivery
    public final boolean packEnabled, packRequired, packHostEnabled, bedrockCustomSounds;
    public final String packUrl, packPrompt, packHostBind, packHostPublicAddress;
    public final int packHostPort;
    public final int fogBankDistance;
    public final Map<Level, Integer> fogDistance = new EnumMap<>(Level.class);
    public final double detailDensity, detailAging, detailEnvironment;
    public final int detailMaxPerChunk;
    public final float detailViewRange;
    public final Map<Level, Boolean> detailLevelEnabled = new EnumMap<>(Level.class);
    public final Map<Level, Double> detailLevelDensity = new EnumMap<>(Level.class);

    public final boolean creaturesEnabled;
    public final boolean fallEnabled, suffocationEnter;
    public final int fallHeight;
    public final long suffocationMillis;
    public final int creatureTickInterval, spawnAttemptSeconds, maxPerPlayer, maxPerLevel, despawnDistance;
    public final Map<Level, Double> levelChance = new EnumMap<>(Level.class);
    public final Map<String, CreatureSettings> creatures = new HashMap<>();

    public final boolean eventsEnabled, ambient;
    public final int eventCheckSeconds, ambientSeconds, flickerRadius;
    public final double eventChance;

    public final boolean sanityEnabled, sanityActionbar;
    public final int flashlightSeconds, batterySeconds;
    public final boolean debug;

    private final Map<String, String> messages = new HashMap<>();

    private static final Map<String, CreatureSettings> DEFAULT_CREATURES = Map.of(
            "smiler", new CreatureSettings(true, 6, 20, Map.of(Level.LEVEL_0, 5.0, Level.LEVEL_1, 3.0)),
            "faceling", new CreatureSettings(true, 0, 1, Map.of(Level.LEVEL_0, 4.0, Level.LEVEL_1, 2.0, Level.POOLROOMS, 2.0, Level.LEVEL_4, 6.0, Level.PLAINS, 10.0)),
            "hound", new CreatureSettings(true, 5, 30, Map.of(Level.LEVEL_1, 6.0)),
            "drifter", new CreatureSettings(true, 2, 20, Map.of(Level.POOLROOMS, 8.0)),
            "watcher", new CreatureSettings(true, 4, 20, Map.of(Level.LEVEL_0, 4.0, Level.LEVEL_4, 6.0, Level.LEVEL_1, 1.0)));

    public record CreatureSettings(boolean enabled, double damage, double health, Map<Level, Double> weights) {
    }

    public BackroomsConfig(FileConfiguration c) {
        seed = c.getLong("seed", 0);
        worldPrefix = c.getString("world-prefix", "backrooms_");
        for (Level l : Level.values()) {
            String base = "levels." + l.key();
            enabled.put(l, c.getBoolean(base + ".enabled", true));
            biome.put(l, c.getString(base + ".biome", ""));
            Map<String, Double> g = new HashMap<>();
            ConfigurationSection gs = c.getConfigurationSection(base + ".generation");
            if (gs != null) {
                for (String k : gs.getKeys(false)) {
                    if (gs.isDouble(k) || gs.isInt(k)) g.put(k, gs.getDouble(k));
                }
            }
            generation.put(l, new GenSettings(g));
        }
        allowSelfEnter = c.getBoolean("entry.allow-self-enter", true);
        allowSelfLeave = c.getBoolean("entry.allow-self-leave", false);
        allowedWorlds = c.getStringList("entry.allowed-worlds");
        spreadRadius = Math.max(0, c.getInt("entry.spread-radius", 2500));
        fallEnabled = c.getBoolean("entry.fall.enabled", true);
        fallHeight = clamp(c.getInt("entry.fall.height", 90), 20, 300);
        suffocationEnter = c.getBoolean("entry.suffocation.enabled", true);
        suffocationMillis = (long) (Math.max(0.2, Math.min(30, c.getDouble("entry.suffocation.seconds", 2.0))) * 1000);
        entryTitle = c.getBoolean("entry.title", true);
        fallbackWorld = c.getString("return.fallback-world", "");
        deathMode = c.getString("death.mode", "RETURN").toUpperCase(Locale.ROOT);
        keepInventory = c.getBoolean("death.keep-inventory", true);

        protectBlocks = c.getBoolean("restrictions.protect-blocks", true);
        blockPearls = c.getBoolean("restrictions.block-ender-pearls", true);
        blockChorus = c.getBoolean("restrictions.block-chorus-fruit", true);
        disablePortals = c.getBoolean("restrictions.disable-portals", true);
        disableBeds = c.getBoolean("restrictions.disable-beds", true);
        suppressMusic = c.getBoolean("restrictions.suppress-music", true);
        List<String> bc = new ArrayList<>();
        for (String s : c.getStringList("restrictions.blocked-commands")) bc.add(s.toLowerCase(Locale.ROOT));
        blockedCommands = List.copyOf(bc);

        transitionCooldownTicks = (int) (c.getDouble("transitions.cooldown-seconds", 6) * 20);
        dwellTicks = (int) (c.getDouble("transitions.dwell-seconds", 2) * 20);
        intentTicks = (int) (c.getDouble("transitions.intent-seconds", 3) * 20);
        arrivalProtectionTicks = (int) (c.getDouble("transitions.arrival-protection-seconds", 3) * 20);

        displaysEnabled = c.getBoolean("displays.enabled", true);
        activationRadius = clamp(c.getInt("displays.activation-radius-chunks", 3), 1, 8);
        outdoorActivationRadius = clamp(c.getInt("displays.outdoor-activation-radius-chunks", 8), 1, 16);
        maxPerChunk = clamp(c.getInt("displays.max-entities-per-chunk", 80), 4, 400);
        maxPerWorld = clamp(c.getInt("displays.max-entities-per-world", 2500), 50, 20000);
        chunksPerTick = clamp(c.getInt("displays.chunks-per-tick", 3), 1, 32);
        audioEnabled = c.getBoolean("audio.enabled", true);
        audioMusic = c.getBoolean("audio.music", true);
        audioAmbient = c.getBoolean("audio.ambient", true);
        audioVoices = c.getBoolean("audio.voices", true);
        audioDensity = Math.max(0, Math.min(4, c.getDouble("audio.event-density", 1.0)));
        audioRare = Math.max(0, Math.min(4, c.getDouble("audio.rare-frequency", 1.0)));
        audioMusicFrequency = Math.max(0.05, Math.min(4, c.getDouble("audio.music-frequency", 1.0)));
        audioDistanceScale = Math.max(0.25, Math.min(3, c.getDouble("audio.distance-scale", 1.0)));
        audioMaxSources = clamp(c.getInt("audio.max-sources", 6), 1, 24);
        audioQuality = switch (c.getString("audio.acoustic-quality", "medium").toLowerCase(Locale.ROOT)) {
            case "low" -> 0;
            case "high" -> 2;
            default -> 1;
        };
        for (Level l : Level.values()) audioLevelIntensity.put(l, Math.max(0, Math.min(2, c.getDouble("audio.levels." + l.key(), 1.0))));
        packEnabled = c.getBoolean("resource-pack.enabled", true);
        packUrl = c.getString("resource-pack.url", "https://download.mc-packs.net/pack/{sha1}.zip");
        packRequired = c.getBoolean("resource-pack.required", false);
        packPrompt = c.getString("resource-pack.prompt", "<gray>The Backrooms uses custom ambient sound. Accept to hear it.");
        packHostEnabled = c.getBoolean("resource-pack.host.enabled", false);
        packHostBind = c.getString("resource-pack.host.bind", "0.0.0.0");
        packHostPort = clamp(c.getInt("resource-pack.host.port", 8163), 1, 65535);
        packHostPublicAddress = c.getString("resource-pack.host.public-address", "");
        bedrockCustomSounds = c.getBoolean("bedrock.custom-sounds", false);
        fogEnabled = c.getBoolean("fog.enabled", true);
        fogBankDistance = clamp(c.getInt("fog.bank-view-distance", 3), 2, 32);
        Map<Level, Integer> fogDefaults = Map.of(Level.LEVEL_0, 0, Level.POOLROOMS, 7, Level.LEVEL_1, 5, Level.LEVEL_4, 0, Level.PLAINS, 6);
        for (Level l : Level.values()) {
            int d = c.getInt("fog.levels." + l.key(), fogDefaults.getOrDefault(l, 0));
            fogDistance.put(l, d <= 0 ? 0 : clamp(d, 2, 32));
        }
        detailsEnabled = c.getBoolean("displays.details.enabled", true);
        performanceMode = c.getBoolean("displays.details.performance-mode", false);
        detailDensity = Math.max(0, Math.min(2, c.getDouble("displays.details.density", 1.0)));
        detailAging = Math.max(0, Math.min(2, c.getDouble("displays.details.aging", 1.0)));
        detailEnvironment = Math.max(0, Math.min(2, c.getDouble("displays.details.environment", 1.0)));
        detailMaxPerChunk = clamp(c.getInt("displays.details.max-per-chunk", 28), 0, 200);
        detailViewRange = (float) Math.max(0.1, Math.min(2.0, c.getDouble("displays.details.view-range", 0.45)));
        for (Level l : Level.values()) {
            detailLevelEnabled.put(l, c.getBoolean("displays.details.levels." + l.key() + ".enabled", true));
            detailLevelDensity.put(l, Math.max(0, Math.min(2, c.getDouble("displays.details.levels." + l.key() + ".density", 1.0))));
        }
        animationInterval = clamp(c.getInt("displays.animation-interval-ticks", 4), 1, 40);
        viewRange = (float) c.getDouble("displays.view-range", 0.7);
        hazards = c.getBoolean("displays.hazards", true);

        creaturesEnabled = c.getBoolean("creatures.enabled", true);
        creatureTickInterval = clamp(c.getInt("creatures.tick-interval", 2), 1, 10);
        spawnAttemptSeconds = clamp(c.getInt("creatures.spawn-attempt-seconds", 20), 2, 3600);
        maxPerPlayer = clamp(c.getInt("creatures.max-per-player", 2), 0, 20);
        maxPerLevel = clamp(c.getInt("creatures.max-per-level", 8), 0, 200);
        despawnDistance = clamp(c.getInt("creatures.despawn-distance", 64), 24, 256);
        for (Level l : Level.values()) levelChance.put(l, c.getDouble("creatures.level-chance." + l.key(), 0.2));
        ConfigurationSection types = c.getConfigurationSection("creatures.types");
        if (types != null) {
            for (String t : types.getKeys(false)) {
                ConfigurationSection s = types.getConfigurationSection(t);
                if (s == null) continue;
                Map<Level, Double> w = new EnumMap<>(Level.class);
                ConfigurationSection ws = s.getConfigurationSection("weight");
                if (ws != null) {
                    for (String lk : ws.getKeys(false)) {
                        Level l = Level.parse(lk);
                        if (l != null) w.put(l, ws.getDouble(lk));
                    }
                }
                creatures.put(t.toLowerCase(Locale.ROOT), new CreatureSettings(s.getBoolean("enabled", true), s.getDouble("damage", 4), s.getDouble("health", 20), w));
            }
        }
        // types missing from an older config.yml still spawn with their built-in defaults
        for (Map.Entry<String, CreatureSettings> d : DEFAULT_CREATURES.entrySet()) creatures.putIfAbsent(d.getKey(), d.getValue());

        eventsEnabled = c.getBoolean("events.enabled", true);
        eventCheckSeconds = clamp(c.getInt("events.check-seconds", 30), 5, 3600);
        eventChance = c.getDouble("events.chance", 0.3);
        ambient = c.getBoolean("events.ambient", true);
        ambientSeconds = clamp(c.getInt("events.ambient-seconds", 9), 2, 600);
        flickerRadius = clamp(c.getInt("events.flicker-radius", 14), 4, 32);

        sanityEnabled = c.getBoolean("sanity.enabled", true);
        sanityActionbar = c.getBoolean("sanity.show-actionbar", false);
        flashlightSeconds = clamp(c.getInt("items.flashlight-battery-seconds", 300), 10, 100000);
        batterySeconds = clamp(c.getInt("items.battery-seconds", 240), 10, 100000);
        debug = c.getBoolean("debug", false);

        ConfigurationSection m = c.getConfigurationSection("messages");
        if (m != null) {
            for (String k : m.getKeys(false)) messages.put(k, m.getString(k, ""));
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public CreatureSettings creature(String id) {
        return creatures.getOrDefault(id, DEFAULT_CREATURES.getOrDefault(id, new CreatureSettings(true, 4, 20, Map.of())));
    }

    /** Message with prefix; placeholders as alternating key/value pairs. */
    public Component msg(String key, String... kv) {
        return MM.deserialize(messages.getOrDefault("prefix", "") + messages.getOrDefault(key, key), resolver(kv));
    }

    /** Message without prefix. */
    public Component raw(String key, String... kv) {
        return MM.deserialize(messages.getOrDefault(key, key), resolver(kv));
    }

    public static Component mm(String s, String... kv) {
        return MM.deserialize(s, resolver(kv));
    }

    private static TagResolver resolver(String... kv) {
        TagResolver.Builder b = TagResolver.builder();
        for (int i = 0; i + 1 < kv.length; i += 2) b.resolver(Placeholder.unparsed(kv[i], kv[i + 1]));
        return b.build();
    }
}
