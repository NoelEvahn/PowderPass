package dev.powderpass.volleyball.game;

import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Format and rules of a match. Mutable; copy before handing to a running match. */
public final class MatchSettings {

    /** Numeric options, editable from command and GUI. */
    public enum Option {
        SETS_TO_WIN("sets-to-win", "Sets to win", 1, 5, 1),
        POINTS_PER_SET("points-per-set", "Points per set", 5, 99, 1),
        DECIDING_SET_POINTS("deciding-set-points", "Deciding set points", 5, 99, 1),
        WIN_BY("win-by", "Win by", 1, 5, 1),
        POINT_CAP("point-cap", "Point cap (0 = none)", 0, 99, 1),
        SERVE_TIME("serve-time-seconds", "Serve time (s)", 3, 60, 1),
        RALLY_END_DELAY("rally-end-delay-seconds", "Pause between rallies (s)", 1, 15, 1),
        TIMEOUTS_PER_SET("timeouts-per-set", "Timeouts per set", 0, 5, 1),
        TIMEOUT_SECONDS("timeout-seconds", "Timeout length (s)", 10, 120, 5),
        SWITCH_SIDES("switch-sides", "Switch sides", 0, 1, 1),
        AUTO_POSITION("auto-position", "Auto positioning", 0, 1, 1),
        REFEREE_WHISTLE("referee-whistle", "Referee whistle required", 0, 1, 1);

        public final String key;
        public final String label;
        public final int min;
        public final int max;
        public final int step;

        Option(String key, String label, int min, int max, int step) {
            this.key = key;
            this.label = label;
            this.min = min;
            this.max = max;
            this.step = step;
        }

        public boolean isToggle() {
            return min == 0 && max == 1;
        }

        public static Option byKey(String key) {
            for (Option o : values()) {
                if (o.key.equalsIgnoreCase(key) || o.name().equalsIgnoreCase(key)) {
                    return o;
                }
            }
            return null;
        }
    }

    private final Map<Option, Integer> values = new LinkedHashMap<>();
    private final EnumSet<Rule> rules = EnumSet.noneOf(Rule.class);

    public static MatchSettings standard() {
        MatchSettings s = new MatchSettings();
        s.set(Option.SETS_TO_WIN, 2);
        s.set(Option.POINTS_PER_SET, 25);
        s.set(Option.DECIDING_SET_POINTS, 15);
        s.set(Option.WIN_BY, 2);
        s.set(Option.POINT_CAP, 0);
        s.set(Option.SERVE_TIME, 8);
        s.set(Option.RALLY_END_DELAY, 3);
        s.set(Option.TIMEOUTS_PER_SET, 2);
        s.set(Option.TIMEOUT_SECONDS, 30);
        s.set(Option.SWITCH_SIDES, 1);
        s.set(Option.AUTO_POSITION, 1);
        s.set(Option.REFEREE_WHISTLE, 0);
        s.rules.addAll(EnumSet.allOf(Rule.class));
        return s;
    }

    public MatchSettings copy() {
        MatchSettings s = new MatchSettings();
        s.values.putAll(values);
        s.rules.addAll(rules);
        return s;
    }

    public int get(Option o) {
        return values.getOrDefault(o, o.min);
    }

    public void set(Option o, int value) {
        values.put(o, Math.max(o.min, Math.min(o.max, value)));
    }

    public boolean flag(Option o) {
        return get(o) != 0;
    }

    public boolean rule(Rule r) {
        return rules.contains(r);
    }

    public void setRule(Rule r, boolean on) {
        if (on) {
            rules.add(r);
        } else {
            rules.remove(r);
        }
    }

    public Set<Rule> rules() {
        return EnumSet.copyOf(rules.isEmpty() ? EnumSet.noneOf(Rule.class) : rules);
    }

    public int setsToWin() {
        return get(Option.SETS_TO_WIN);
    }

    public int pointsPerSet() {
        return get(Option.POINTS_PER_SET);
    }

    public int decidingSetPoints() {
        return get(Option.DECIDING_SET_POINTS);
    }

    public int winBy() {
        return get(Option.WIN_BY);
    }

    public int pointCap() {
        return get(Option.POINT_CAP);
    }

    public static MatchSettings load(ConfigurationSection s, MatchSettings defaults) {
        MatchSettings m = defaults.copy();
        if (s == null) {
            return m;
        }
        for (Option o : Option.values()) {
            if (s.contains(o.key)) {
                m.set(o, s.isBoolean(o.key) ? (s.getBoolean(o.key) ? 1 : 0) : s.getInt(o.key));
            }
        }
        ConfigurationSection r = s.getConfigurationSection("rules");
        if (r != null) {
            for (Rule rule : Rule.values()) {
                if (r.contains(rule.key())) {
                    m.setRule(rule, r.getBoolean(rule.key()));
                }
            }
        }
        return m;
    }

    public void save(ConfigurationSection s) {
        for (Option o : Option.values()) {
            if (o.isToggle()) {
                s.set(o.key, flag(o));
            } else {
                s.set(o.key, get(o));
            }
        }
        for (Rule rule : Rule.values()) {
            s.set("rules." + rule.key(), rule(rule));
        }
    }

    /** Human readable format, e.g. "Best of 3 · 25 pts". */
    public String describe() {
        int sets = setsToWin();
        String format = sets == 1 ? "1 set" : "Best of " + (sets * 2 - 1);
        return format + " · " + pointsPerSet() + " pts";
    }
}
