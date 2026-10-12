package dev.powderpass.lift;

import dev.powderpass.display.Displays;
import dev.powderpass.display.StaticStructure;
import dev.powderpass.display.Transforms;
import dev.powderpass.lift.geom.CablePath;
import dev.powderpass.lift.geom.LiftLayout;
import dev.powderpass.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** A built chairlift in a loaded world: its static structure, its moving chairs and its station logic. */
public final class Lift {

    private static final float VIEW = 2.5f;

    private final LiftDefinition def;
    private final World world;
    private final LiftLayout layout;
    private final StaticStructure structure;
    private final List<Chair> chairs = new ArrayList<>();
    private final List<int[]> wheelSpokes = new ArrayList<>(); // part indices per station
    private final List<Matrix4f> spokeBase = new ArrayList<>();
    private final int[] signParts = new int[2];
    private final double[] towerS;
    private double wheelAngle;
    private boolean active;

    Lift(LiftDefinition def, World world, LiftLayout layout, int seats, double hanger) {
        this.def = def;
        this.world = world;
        this.layout = layout;
        this.structure = new StaticStructure(world);
        double length = layout.path().length();
        int count = Math.max(2, (int) Math.floor(length / Math.max(6.0, def.spacing)));
        for (int i = 0; i < count; i++) {
            chairs.add(new Chair(length * i / count, seats, hanger));
        }
        List<Double> ts = new ArrayList<>();
        for (LiftLayout.Tower t : layout.towers()) {
            ts.add(t.sUp());
            ts.add(t.sDown());
        }
        towerS = ts.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        buildStructure();
        for (Chair c : chairs) {
            c.advance(this);
        }
    }

    public LiftDefinition definition() {
        return def;
    }

    public LiftLayout layout() {
        return layout;
    }

    public World world() {
        return world;
    }

    List<Chair> chairs() {
        return chairs;
    }

    public int chairCount() {
        return chairs.size();
    }

    public boolean active() {
        return active;
    }

    public StaticStructure structure() {
        return structure;
    }

    // ------------------------------------------------------------------------------------------------ structure

    private void buildStructure() {
        LiftStyle style = def.style;
        float yaw = (float) layout.axisYaw();
        Matrix4f frame = Transforms.yaw(yaw);
        float r = (float) layout.wheelRadius();

        // Towers
        for (LiftLayout.Tower t : layout.towers()) {
            Location base = new Location(world, t.x(), t.baseY(), t.z());
            float height = (float) (t.cableY() - t.baseY());
            Matrix4f pillar = Transforms.pillar(frame, 0f, 0f, 0f, 0.7f, height - 0.3f, 0.7f);
            structure.add(base, l -> Displays.block(l, style.tower().createBlockData(), pillar, VIEW, null));
            Matrix4f arm = Transforms.box(frame, -r - 0.55f, height - 0.55f, -0.2f, 2 * r + 1.1f, 0.35f, 0.4f);
            structure.add(base, l -> Displays.block(l, style.arm().createBlockData(), arm, VIEW, null));
            for (float side : new float[]{r, -r}) {
                Matrix4f sheave = Transforms.box(frame, side - 0.16f, height - 0.2f, -0.75f, 0.32f, 0.16f, 1.5f);
                structure.add(base, l -> Displays.block(l, Material.BLACK_CONCRETE.createBlockData(), sheave, VIEW, null));
            }
        }

        // Rope: straight-ish runs become long thin beams, curves get short ones.
        CablePath path = layout.path();
        Vector3d a = new Vector3d();
        Vector3d b = new Vector3d();
        Vector3d dirStart = new Vector3d();
        Vector3d d = new Vector3d();
        double length = path.length();
        double segStart = 0;
        path.position(0, a);
        path.direction(0, dirStart);
        for (double sPos = 0.5; sPos <= length + 0.01; sPos += 0.5) {
            path.direction(Math.min(sPos, length - 0.01), d);
            boolean bend = dirStart.angle(d) > Math.toRadians(2.5);
            if (bend || sPos - segStart >= 6.0 || sPos >= length) {
                path.position(Math.min(sPos, length), b);
                addCable(new Vector3d(a), new Vector3d(b), style);
                a.set(b);
                dirStart.set(d);
                segStart = sPos;
            }
        }

        // Stations
        buildStation(layout.bottomWheel(), def.by, frame, style, r, 0, true);
        buildStation(layout.topWheel(), def.ty, frame, style, r, 1, false);
    }

    private void addCable(Vector3d from, Vector3d to, LiftStyle style) {
        Location at = new Location(world, from.x, from.y, from.z);
        Vector3f rel = new Vector3f((float) (to.x - from.x), (float) (to.y - from.y), (float) (to.z - from.z));
        Matrix4f m = Transforms.beam(new Vector3f(), rel, 0.075f);
        structure.add(at, l -> Displays.block(l, style.cable().createBlockData(), m, VIEW, null));
    }

    private void buildStation(Vector3d wheel, double floorY, Matrix4f frame, LiftStyle style, float r, int index, boolean bottom) {
        Location floor = new Location(world, wheel.x, floorY, wheel.z);
        float wheelH = (float) (wheel.y - floorY);
        Matrix4f column = Transforms.pillar(frame, 0f, 0f, 0f, 0.8f, wheelH - 0.45f, 0.8f);
        structure.add(floor, l -> Displays.block(l, style.tower().createBlockData(), column, VIEW, null));
        float roofW = 2 * r + 2.6f;
        float roofD = 2 * r + 3.2f;
        Matrix4f roof = Transforms.box(frame, -roofW / 2f, wheelH + 0.55f, -roofD / 2f, roofW, 0.2f, roofD);
        structure.add(floor, l -> Displays.block(l, style.roof().createBlockData(), roof, VIEW, null));
        for (float sx : new float[]{roofW / 2f - 0.15f, -roofW / 2f + 0.15f}) {
            for (float sz : new float[]{roofD / 2f - 0.15f, -roofD / 2f + 0.15f}) {
                Matrix4f post = Transforms.pillar(frame, sx, 0f, sz, 0.16f, wheelH + 0.55f, 0.16f);
                structure.add(floor, l -> Displays.block(l, style.arm().createBlockData(), post, VIEW, null));
            }
        }
        // Rotating bullwheel: centred item displays rotate cleanly about their origin.
        Location hub = new Location(world, wheel.x, wheel.y + 0.15, wheel.z);
        int[] spokes = new int[5];
        for (int k = 0; k < 4; k++) {
            Matrix4f base = new Matrix4f().rotateY((float) Math.toRadians(45 * k)).scale(2 * r + 0.35f, 0.16f, 0.2f);
            spokeBase.add(base);
            spokes[k] = structure.add(hub, l -> Displays.item(l, Displays.blockItem(style.wheel()), base, VIEW,
                    disp -> disp.setInterpolationDuration(3)));
        }
        Matrix4f hubM = new Matrix4f().scale(0.7f, 0.35f, 0.7f);
        spokeBase.add(hubM);
        spokes[4] = structure.add(hub, l -> Displays.item(l, Displays.blockItem(Material.BLACK_CONCRETE), hubM, VIEW,
                disp -> disp.setInterpolationDuration(3)));
        wheelSpokes.add(spokes);

        Matrix4f signM = new Matrix4f().translate(0f, wheelH + 1.5f, 0f).scale(1.4f);
        signParts[index] = structure.add(floor, l -> Displays.text(l, signText(bottom), signM, VIEW, null));

        // Floating marker above the loading / unloading area of this station.
        double side = bottom ? 1 : -1;
        double along = bottom ? 2.5 : -2.5;
        Location mark = new Location(world,
                wheel.x + layout.rightX() * r * side + layout.axisX() * along, floorY,
                wheel.z + layout.rightZ() * r * side + layout.axisZ() * along);
        Matrix4f markM = new Matrix4f().translate(0f, 2.0f, 0f);
        Component markText = Text.mm(bottom ? "<#4ade80><bold>▼ LOAD HERE ▼" : "<#4ade80><bold>▼ RIDE DOWN ▼");
        structure.add(mark, l -> Displays.text(l, markText, markM, 1.0f, t -> t.setBillboard(Display.Billboard.CENTER)));
    }

    private Component signText(boolean bottom) {
        return Text.mm("<gradient:#7dd3fc:#e0f2fe><bold>🚡 " + Text.escape(def.name) + "</bold></gradient>\n<gray>"
                + (bottom ? "Base station" : "Summit") + " <dark_gray>· <gray>" + Math.round(layout.rideLengthUp()) + " m"
                + " <dark_gray>· <gray>" + Math.round(def.ty - def.by) + " m vertical");
    }

    // ------------------------------------------------------------------------------------------------ tick

    /**
     * Advances the lift one tick.
     *
     * @return chair arc-length crossings of towers this tick (for clunk sounds), handled internally
     */
    void tick(long now, int updateInterval, double activationRange) {
        if (now % 20 == 0) {
            boolean shouldBeActive = structure.playerNear(activationRange);
            if (!shouldBeActive && hasRiders()) {
                shouldBeActive = true;
            }
            active = shouldBeActive;
            structure.sync(active);
            if (!active) {
                for (Chair c : chairs) {
                    c.despawnEntities();
                }
            }
        }
        for (Chair c : chairs) {
            double before = c.s;
            double moved = c.advance(this);
            if (active) {
                clunk(before, moved);
            }
        }
        if (!active) {
            return;
        }
        boolean render = now % updateInterval == 0;
        for (Chair c : chairs) {
            if (!c.spawned()) {
                if (c.chunkLoaded(world)) {
                    c.spawn(world, def.style, updateInterval);
                }
                continue;
            }
            if (!c.chunkLoaded(world) && !c.hasRiders()) {
                c.despawnEntities();
                continue;
            }
            if (render) {
                c.render(world, nearPlayer(c.grip(), 64));
            }
        }
        if (now % 3 == 0) {
            spinWheels(updateInterval);
        }
    }

    private boolean nearPlayer(Vector3d p, double range) {
        double r2 = range * range;
        for (org.bukkit.entity.Player pl : world.getPlayers()) {
            Location l = pl.getLocation();
            double dx = l.getX() - p.x;
            double dy = l.getY() - p.y;
            double dz = l.getZ() - p.z;
            if (dx * dx + dy * dy + dz * dz < r2) {
                return true;
            }
        }
        return false;
    }

    private void clunk(double before, double moved) {
        if (towerS.length == 0) {
            return;
        }
        double length = layout.path().length();
        double after = before + moved;
        for (double ts : towerS) {
            boolean crossed = (before < ts && after >= ts) || (after > length && ts < after - length);
            if (crossed) {
                Vector3d p = layout.path().position(ts, new Vector3d());
                Location at = new Location(world, p.x, p.y, p.z);
                world.playSound(at, Sound.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, 0.9f, 0.6f);
                world.playSound(at, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, SoundCategory.BLOCKS, 0.35f, 0.5f);
            }
        }
    }

    private void spinWheels(int interval) {
        wheelAngle += def.speed / layout.wheelRadius() * 3.0;
        if (wheelAngle > Math.PI * 2) {
            wheelAngle -= Math.PI * 2;
        }
        int baseIndex = 0;
        for (int[] spokes : wheelSpokes) {
            for (int k = 0; k < spokes.length; k++) {
                Entity e = structure.live(spokes[k]);
                if (e instanceof ItemDisplay disp) {
                    Matrix4f m = new Matrix4f().rotateY((float) wheelAngle).mul(spokeBase.get(baseIndex + k));
                    disp.setInterpolationDelay(0);
                    disp.setInterpolationDuration(3);
                    disp.setTransformationMatrix(m);
                }
            }
            baseIndex += spokes.length;
        }
    }

    /** Updates the station signs with live information. */
    void updateSigns(int riders) {
        for (int i = 0; i < 2; i++) {
            Entity e = structure.live(signParts[i]);
            if (e instanceof TextDisplay t) {
                String base = Text.MM.serialize(signText(i == 0));
                t.text(Text.mm(base + "\n<#bae6fd>" + riders + " riding <dark_gray>· <#bae6fd>"
                        + Math.round(def.speed * 72) + " km/h rope"));
            }
        }
    }

    boolean hasRiders() {
        for (Chair c : chairs) {
            if (c.hasRiders()) {
                return true;
            }
        }
        return false;
    }

    int riderCount() {
        int n = 0;
        for (Chair c : chairs) {
            for (int i = 0; i < c.seats(); i++) {
                if (c.rider(i) != null) {
                    n++;
                }
            }
        }
        return n;
    }

    /** Horizontal distance from (x, z) to the lift axis segment. */
    public double distanceToLine(double x, double z) {
        double ax = def.bx;
        double az = def.bz;
        double bx = def.tx - ax;
        double bz = def.tz - az;
        double len2 = bx * bx + bz * bz;
        double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * bx + (z - az) * bz) / len2));
        double px = ax + bx * t - x;
        double pz = az + bz * t - z;
        return Math.sqrt(px * px + pz * pz);
    }

    void despawn() {
        for (Chair c : chairs) {
            c.despawnEntities();
        }
        structure.despawn();
        active = false;
    }
}
