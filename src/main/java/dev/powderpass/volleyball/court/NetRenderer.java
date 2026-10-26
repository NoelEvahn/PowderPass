package dev.powderpass.volleyball.court;

import dev.powderpass.volleyball.math.Vec3;
import dev.powderpass.volleyball.util.Displays;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the net as thin block-display bars between the nodes of a {@link NetSimulation} and animates
 * them with client-side transformation interpolation when the net is hit.
 */
public final class NetRenderer {

    private static final float MESH_THICKNESS = 0.012f;
    private static final float TAPE_HEIGHT = 0.07f;
    private static final float TAPE_THICKNESS = 0.02f;
    private static final float BOTTOM_HEIGHT = 0.05f;

    private final Court court;
    private final NetSimulation sim;
    private final int interpolation;
    private final List<Segment> segments = new ArrayList<>();
    private boolean dirty;
    private int frame;

    private static final class Segment {
        final BlockDisplay display;
        final int i0;
        final int j0;
        final int i1;
        final int j1;
        final Vector3f origin;
        final float thickness;
        final float height;
        float lastA;
        float lastB;

        Segment(BlockDisplay display, int i0, int j0, int i1, int j1, Vector3f origin, float thickness, float height) {
            this.display = display;
            this.i0 = i0;
            this.j0 = j0;
            this.i1 = i1;
            this.j1 = j1;
            this.origin = origin;
            this.thickness = thickness;
            this.height = height;
        }
    }

    public NetRenderer(Court court, int cols, int rows, int interpolation) {
        this.court = court;
        double halfLength = court.net().geometry().halfLength();
        this.sim = new NetSimulation(cols, rows, halfLength, court.netHeight() - court.netBand(), court.netBand());
        this.interpolation = interpolation;
    }

    public NetSimulation simulation() {
        return sim;
    }

    public void spawn(World world, float viewRange, String owner) {
        int cols = sim.cols();
        int rows = sim.rows();
        // vertical mesh strands
        for (int i = 0; i <= cols; i++) {
            for (int j = 0; j < rows; j++) {
                add(world, viewRange, owner, i, j, i, j + 1, Material.BLACK_CONCRETE, MESH_THICKNESS, MESH_THICKNESS);
            }
        }
        // horizontal strands, bottom band and top tape
        for (int j = 0; j <= rows; j++) {
            for (int i = 0; i < cols; i++) {
                if (j == rows) {
                    add(world, viewRange, owner, i, j, i + 1, j, Material.WHITE_CONCRETE, TAPE_THICKNESS, TAPE_HEIGHT);
                } else if (j == 0) {
                    add(world, viewRange, owner, i, j, i + 1, j, Material.WHITE_CONCRETE, TAPE_THICKNESS * 0.8f, BOTTOM_HEIGHT);
                } else {
                    add(world, viewRange, owner, i, j, i + 1, j, Material.BLACK_CONCRETE, MESH_THICKNESS, MESH_THICKNESS);
                }
            }
        }
    }

    private void add(World world, float viewRange, String owner, int i0, int j0, int i1, int j1, Material mat,
                     float thickness, float height) {
        Vec3 a = node(i0, j0, 0);
        Vec3 b = node(i1, j1, 0);
        Vec3 mid = a.copy().lerp(b, 0.5);
        Location at = new Location(world, mid.x, mid.y, mid.z);
        Vector3f origin = new Vector3f((float) mid.x, (float) mid.y, (float) mid.z);
        Matrix4f m = matrix(a, b, origin, thickness, height, j0 == sim.rows() && j1 == sim.rows());
        BlockDisplay d = Displays.block(world, at, mat, m, viewRange, owner);
        segments.add(new Segment(d, i0, j0, i1, j1, origin, thickness, height));
    }

    /** World position of a node with its current displacement. */
    private Vec3 node(int i, int j, double displacement) {
        double w = sim.nodeW(i);
        double y = sim.nodeY(j);
        if (j == sim.rows()) {
            y -= TAPE_HEIGHT / 2.0;
        } else if (j == 0) {
            y += BOTTOM_HEIGHT / 2.0;
        }
        return court.frame().toWorld(displacement, y, w, new Vec3());
    }

    private static Matrix4f matrix(Vec3 a, Vec3 b, Vector3f origin, float thickness, float height, boolean tape) {
        Vector3f ra = new Vector3f((float) a.x, (float) a.y, (float) a.z).sub(origin);
        Vector3f rb = new Vector3f((float) b.x, (float) b.y, (float) b.z).sub(origin);
        return Displays.bar(ra, rb, thickness, height);
    }

    /** Ball hit the mesh or tape: push the membrane. */
    public void impact(double w, double y, double normalVelocity, boolean tape) {
        double transfer = Math.max(-6, Math.min(6, normalVelocity)) * (tape ? 0.25 : 0.55);
        sim.impulse(w, y, transfer, tape ? 0.9 : 0.55);
        dirty = true;
    }

    /** A body (player) brushing the net. */
    public void brush(double w, double y, double velocity) {
        sim.impulse(w, y, velocity, 0.6);
        dirty = true;
    }

    public void tick() {
        if (!sim.active() && !dirty) {
            return;
        }
        sim.step(0.05);
        dirty = false;
        // the simulation runs every tick, the clients get a new pose every other tick and interpolate between
        if (++frame % 2 != 0 && sim.active()) {
            return;
        }
        for (Segment s : segments) {
            if (!s.display.isValid()) {
                continue;
            }
            float da = (float) sim.displacement(s.i0, s.j0);
            float db = (float) sim.displacement(s.i1, s.j1);
            if (Math.abs(da - s.lastA) < 0.006f && Math.abs(db - s.lastB) < 0.006f) {
                continue;
            }
            s.lastA = da;
            s.lastB = db;
            Vec3 a = node(s.i0, s.j0, da);
            Vec3 b = node(s.i1, s.j1, db);
            s.display.setTransformationMatrix(matrix(a, b, s.origin, s.thickness, s.height, false));
            Displays.refreshInterpolation(s.display, Math.max(2, interpolation + 1));
        }
    }

    public void remove() {
        for (Segment s : segments) {
            s.display.remove();
        }
        segments.clear();
    }

    public int entityCount() {
        return segments.size();
    }
}
