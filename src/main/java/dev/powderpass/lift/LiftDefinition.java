package dev.powderpass.lift;

import dev.powderpass.lift.geom.GroundFunction;
import dev.powderpass.lift.geom.LiftSpec;
import dev.powderpass.config.PluginConfig;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Persisted description of a chairlift. The terrain profile under the line is sampled once when the lift is
 * built, so the layout can be recomputed at any time without loading chunks.
 */
public final class LiftDefinition {

    public final String name;
    public final String world;
    public final double bx, by, bz;
    public final double tx, ty, tz;
    public double speed;
    public double spacing;
    public int seats;
    public LiftStyle style;
    /** Per-lift override of the global pass requirement, or null to use the config. */
    public Boolean requirePass;
    private float[] groundLeft;
    private float[] groundCenter;
    private float[] groundRight;

    public LiftDefinition(String name, String world, double bx, double by, double bz, double tx, double ty, double tz,
                          double speed, double spacing, int seats, LiftStyle style) {
        this.name = name;
        this.world = world;
        this.bx = bx;
        this.by = by;
        this.bz = bz;
        this.tx = tx;
        this.ty = ty;
        this.tz = tz;
        this.speed = speed;
        this.spacing = spacing;
        this.seats = seats;
        this.style = style;
    }

    public double horizontalLength() {
        return Math.hypot(tx - bx, tz - bz);
    }

    public void ground(float[] left, float[] center, float[] right) {
        this.groundLeft = left;
        this.groundCenter = center;
        this.groundRight = right;
    }

    public boolean hasGround() {
        return groundCenter != null && groundCenter.length > 1;
    }

    public LiftSpec spec(PluginConfig c) {
        return new LiftSpec(bx, by, bz, tx, ty, tz, 1.6, c.hangerLength, c.seatHeight, c.platformLength,
                c.maxTowerSpacing, c.minSeatClearance, c.sagRatio, c.stationSlow);
    }

    /** Terrain lookup backed by the sampled profile (nearest of the three sampled lines). */
    public GroundFunction groundFunction() {
        double len = horizontalLength();
        double ux = (tx - bx) / len;
        double uz = (tz - bz) / len;
        double rx = -uz;
        double rz = ux;
        return (x, z) -> {
            double d = (x - bx) * ux + (z - bz) * uz;
            double lat = (x - bx) * rx + (z - bz) * rz;
            float[] line = lat > 0.8 ? groundRight : (lat < -0.8 ? groundLeft : groundCenter);
            if (line == null || line.length == 0) {
                return by;
            }
            double i = Math.max(0, Math.min(line.length - 1, d));
            int i0 = (int) Math.floor(i);
            int i1 = Math.min(line.length - 1, i0 + 1);
            double t = i - i0;
            return line[i0] + (line[i1] - line[i0]) * t;
        };
    }

    public void save(ConfigurationSection s) {
        s.set("world", world);
        s.set("bottom", List.of(bx, by, bz));
        s.set("top", List.of(tx, ty, tz));
        s.set("speed", speed);
        s.set("spacing", spacing);
        s.set("seats", seats);
        s.set("style", style.name());
        s.set("require-pass", requirePass);
        s.set("ground.left", floats(groundLeft));
        s.set("ground.center", floats(groundCenter));
        s.set("ground.right", floats(groundRight));
    }

    public static LiftDefinition load(String name, ConfigurationSection s, PluginConfig c) {
        List<Double> b = s.getDoubleList("bottom");
        List<Double> t = s.getDoubleList("top");
        if (b.size() != 3 || t.size() != 3) {
            return null;
        }
        LiftStyle style;
        try {
            style = LiftStyle.valueOf(s.getString("style", c.defaultLiftStyle.name()));
        } catch (IllegalArgumentException e) {
            style = c.defaultLiftStyle;
        }
        LiftDefinition d = new LiftDefinition(name, s.getString("world", "world"), b.get(0), b.get(1), b.get(2),
                t.get(0), t.get(1), t.get(2), s.getDouble("speed", c.liftSpeed), s.getDouble("spacing", c.chairSpacing),
                Math.max(1, Math.min(4, s.getInt("seats", c.seatsPerChair))), style);
        d.requirePass = s.isBoolean("require-pass") ? s.getBoolean("require-pass") : null;
        d.ground(toFloats(s.getDoubleList("ground.left")), toFloats(s.getDoubleList("ground.center")),
                toFloats(s.getDoubleList("ground.right")));
        return d;
    }

    private static List<Double> floats(float[] arr) {
        List<Double> out = new ArrayList<>();
        if (arr != null) {
            for (float f : arr) {
                out.add((double) Math.round(f * 100) / 100.0);
            }
        }
        return out;
    }

    private static float[] toFloats(List<Double> list) {
        float[] out = new float[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i).floatValue();
        }
        return out;
    }
}
