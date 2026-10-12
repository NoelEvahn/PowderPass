package dev.powderpass.profile;

import dev.powderpass.physics.SteeringMode;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/** Persistent per-player statistics, badges and preferences. Main-thread only. */
public final class PlayerData {

    private final UUID uuid;
    private String name;
    private boolean dirty;

    public double topSpeed;
    public double distance;
    public double vertical;
    public double powderDistance;
    public long airTicks;
    public int bestTrickScore;
    public String bestTrickName = "";
    public long bestCombo;
    public int tricksLanded;
    public int crashes;
    public int liftRides;
    public int racesFinished;
    public int runs;
    public final Set<Badge> badges = EnumSet.noneOf(Badge.class);
    public SuitPalette suit = SuitPalette.CLASSIC;
    public SteeringMode steering;
    public boolean mph;
    public boolean seenControls;

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        if (name != null && !name.equals(this.name)) {
            this.name = name;
            dirty = true;
        }
    }

    public void markDirty() {
        dirty = true;
    }

    public boolean dirty() {
        return dirty;
    }

    public void clean() {
        dirty = false;
    }

    public boolean unlocked(SuitPalette palette) {
        return palette.unlock() == null || badges.contains(palette.unlock());
    }

    public YamlConfiguration toYaml() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("name", name);
        y.set("stats.top-speed", topSpeed);
        y.set("stats.distance", distance);
        y.set("stats.vertical", vertical);
        y.set("stats.powder-distance", powderDistance);
        y.set("stats.air-ticks", airTicks);
        y.set("stats.best-trick-score", bestTrickScore);
        y.set("stats.best-trick-name", bestTrickName);
        y.set("stats.best-combo", bestCombo);
        y.set("stats.tricks-landed", tricksLanded);
        y.set("stats.crashes", crashes);
        y.set("stats.lift-rides", liftRides);
        y.set("stats.races-finished", racesFinished);
        y.set("stats.runs", runs);
        y.set("badges", badges.stream().map(Enum::name).toList());
        y.set("prefs.suit", suit.name());
        y.set("prefs.steering", steering == null ? null : steering.name());
        y.set("prefs.mph", mph);
        y.set("prefs.seen-controls", seenControls);
        return y;
    }

    public static PlayerData fromYaml(UUID uuid, YamlConfiguration y) {
        PlayerData d = new PlayerData(uuid, y.getString("name", "?"));
        d.topSpeed = y.getDouble("stats.top-speed");
        d.distance = y.getDouble("stats.distance");
        d.vertical = y.getDouble("stats.vertical");
        d.powderDistance = y.getDouble("stats.powder-distance");
        d.airTicks = y.getLong("stats.air-ticks");
        d.bestTrickScore = y.getInt("stats.best-trick-score");
        d.bestTrickName = y.getString("stats.best-trick-name", "");
        d.bestCombo = y.getLong("stats.best-combo");
        d.tricksLanded = y.getInt("stats.tricks-landed");
        d.crashes = y.getInt("stats.crashes");
        d.liftRides = y.getInt("stats.lift-rides");
        d.racesFinished = y.getInt("stats.races-finished");
        d.runs = y.getInt("stats.runs");
        for (String b : y.getStringList("badges")) {
            try {
                d.badges.add(Badge.valueOf(b));
            } catch (IllegalArgumentException ignored) {
                // badge removed in a newer version
            }
        }
        d.suit = SuitPalette.byName(y.getString("prefs.suit", "CLASSIC"));
        String steering = y.getString("prefs.steering");
        if (steering != null) {
            try {
                d.steering = SteeringMode.valueOf(steering);
            } catch (IllegalArgumentException ignored) {
                d.steering = null;
            }
        }
        d.mph = y.getBoolean("prefs.mph");
        d.seenControls = y.getBoolean("prefs.seen-controls");
        if (!d.unlocked(d.suit)) {
            d.suit = SuitPalette.CLASSIC;
        }
        return d;
    }
}
