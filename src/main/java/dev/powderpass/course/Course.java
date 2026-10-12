package dev.powderpass.course;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A timed slalom course: a start gate, slalom gates in order and a finish line, plus its leaderboard. */
public final class Course {

    /** A gate placed in the world. */
    public record GateDef(double x, double y, double z, float yaw, double width) {
        public GateLine line() {
            return GateLine.centered(x, y, z, yaw, width);
        }
    }

    /** A personal best on this course. */
    public record Entry(UUID uuid, String name, double ticks, long when) {
    }

    private final String name;
    private final String world;
    private final List<GateDef> gates = new ArrayList<>();
    private GateDef finish;
    private final Map<UUID, Entry> best = new HashMap<>();
    private double[] recordSplits = new double[0];
    private GhostRun ghost;
    private String ghostName;
    private double ghostTicks;

    public Course(String name, String world, GateDef start) {
        this.name = name;
        this.world = world;
        this.gates.add(start);
    }

    public String name() {
        return name;
    }

    public String world() {
        return world;
    }

    public GateDef start() {
        return gates.get(0);
    }

    /** Slalom gates between the start and the finish. */
    public List<GateDef> slalomGates() {
        return gates.subList(1, gates.size());
    }

    public void addGate(GateDef gate) {
        gates.add(gate);
    }

    public boolean removeLastGate() {
        if (gates.size() <= 1) {
            return false;
        }
        gates.remove(gates.size() - 1);
        return true;
    }

    public GateDef finish() {
        return finish;
    }

    public void finish(GateDef finish) {
        this.finish = finish;
    }

    public boolean complete() {
        return finish != null;
    }

    /** All gate lines in order: start, slalom gates, finish. */
    public List<GateLine> lines() {
        List<GateLine> out = new ArrayList<>(gates.size() + 1);
        for (GateDef g : gates) {
            out.add(g.line());
        }
        if (finish != null) {
            out.add(finish.line());
        }
        return out;
    }

    public int gateCount() {
        return gates.size() + (finish == null ? 0 : 1);
    }

    // ------------------------------------------------------------------------------------------------ results

    public Entry best(UUID uuid) {
        return best.get(uuid);
    }

    /** Records a time. Returns true if it is a personal best. */
    public boolean submit(UUID uuid, String playerName, double ticks, long when) {
        Entry old = best.get(uuid);
        if (old == null || ticks < old.ticks()) {
            best.put(uuid, new Entry(uuid, playerName, ticks, when));
            return true;
        }
        return false;
    }

    public List<Entry> top(int limit) {
        return best.values().stream().sorted(Comparator.comparingDouble(Entry::ticks)).limit(limit).toList();
    }

    /** 1-based rank of a player, or -1. */
    public int rank(UUID uuid) {
        Entry mine = best.get(uuid);
        if (mine == null) {
            return -1;
        }
        int rank = 1;
        for (Entry e : best.values()) {
            if (e.ticks() < mine.ticks()) {
                rank++;
            }
        }
        return rank;
    }

    public Entry record() {
        return best.values().stream().min(Comparator.comparingDouble(Entry::ticks)).orElse(null);
    }

    public int entries() {
        return best.size();
    }

    public void resetResults() {
        best.clear();
        recordSplits = new double[0];
        ghost = null;
        ghostName = null;
        ghostTicks = 0;
    }

    public double[] recordSplits() {
        return recordSplits;
    }

    public void recordSplits(double[] splits) {
        this.recordSplits = splits.clone();
    }

    public GhostRun ghost() {
        return ghost;
    }

    public String ghostName() {
        return ghostName;
    }

    public double ghostTicks() {
        return ghostTicks;
    }

    public void ghost(GhostRun run, String owner, double ticks) {
        this.ghost = run;
        this.ghostName = owner;
        this.ghostTicks = ticks;
    }

    // ------------------------------------------------------------------------------------------------ yaml

    public void save(ConfigurationSection s) {
        s.set("world", world);
        List<Map<String, Object>> list = new ArrayList<>();
        for (GateDef g : gates) {
            list.add(gateMap(g));
        }
        s.set("gates", list);
        s.set("finish", finish == null ? null : gateMap(finish));
        ConfigurationSection results = s.createSection("results");
        for (Entry e : best.values()) {
            ConfigurationSection r = results.createSection(e.uuid().toString());
            r.set("name", e.name());
            r.set("ticks", e.ticks());
            r.set("when", e.when());
        }
        List<Double> splits = new ArrayList<>();
        for (double d : recordSplits) {
            splits.add(d);
        }
        s.set("record-splits", splits);
        s.set("ghost-name", ghostName);
        s.set("ghost-ticks", ghostTicks);
    }

    @SuppressWarnings("unchecked")
    public static Course load(String name, ConfigurationSection s) {
        List<Map<?, ?>> list = s.getMapList("gates");
        if (list.isEmpty()) {
            return null;
        }
        Course c = new Course(name, s.getString("world", "world"), gate((Map<String, Object>) list.get(0)));
        for (int i = 1; i < list.size(); i++) {
            c.addGate(gate((Map<String, Object>) list.get(i)));
        }
        ConfigurationSection f = s.getConfigurationSection("finish");
        if (f != null) {
            c.finish(gate(f.getValues(false)));
        }
        ConfigurationSection results = s.getConfigurationSection("results");
        if (results != null) {
            for (String key : results.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    ConfigurationSection r = results.getConfigurationSection(key);
                    if (r != null) {
                        c.best.put(uuid, new Entry(uuid, r.getString("name", "?"), r.getDouble("ticks"), r.getLong("when")));
                    }
                } catch (IllegalArgumentException ignored) {
                    // skip malformed entry
                }
            }
        }
        List<Double> splits = s.getDoubleList("record-splits");
        c.recordSplits = splits.stream().mapToDouble(Double::doubleValue).toArray();
        c.ghostName = s.getString("ghost-name");
        c.ghostTicks = s.getDouble("ghost-ticks");
        return c;
    }

    private static Map<String, Object> gateMap(GateDef g) {
        Map<String, Object> m = new HashMap<>();
        m.put("x", g.x());
        m.put("y", g.y());
        m.put("z", g.z());
        m.put("yaw", (double) g.yaw());
        m.put("width", g.width());
        return m;
    }

    private static GateDef gate(Map<String, Object> m) {
        return new GateDef(num(m.get("x")), num(m.get("y")), num(m.get("z")), (float) num(m.get("yaw")), num(m.get("width")));
    }

    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0.0;
    }
}
