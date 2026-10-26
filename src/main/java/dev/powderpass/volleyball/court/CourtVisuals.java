package dev.powderpass.volleyball.court;

import dev.powderpass.volleyball.config.Settings;
import dev.powderpass.volleyball.math.Vec3;
import dev.powderpass.volleyball.util.Displays;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Display-entity decoration of a court: lines, posts, antennas, side bands and the animated net.
 * Nothing here is a real block, so courts can be removed without a trace.
 */
public final class CourtVisuals {

    private static final double LINE = 0.05;
    private static final double LINE_Y = 0.004;
    private static final double LINE_THICK = 0.006;

    private final Court court;
    private final List<Display> displays = new ArrayList<>();
    private NetRenderer net;

    public CourtVisuals(Court court) {
        this.court = court;
    }

    public NetRenderer net() {
        return net;
    }

    public void spawn(Settings settings) {
        remove();
        World world = court.world();
        if (world == null) {
            return;
        }
        Settings.CourtDefaults cd = settings.courtDefaults;
        String owner = "court:" + court.name();
        float vr = cd.viewRange();
        Material line = cd.lineMaterial();
        double hl = court.halfLength();
        double hw = court.halfWidth();

        // boundary lines (inside the court area, lines belong to the court)
        rect(world, line, vr, owner, -hl, hl, hw - LINE, hw);
        rect(world, line, vr, owner, -hl, hl, -hw, -hw + LINE);
        rect(world, line, vr, owner, hl - LINE, hl, -hw, hw);
        rect(world, line, vr, owner, -hl, -hl + LINE, -hw, hw);
        // centre line under the net
        rect(world, line, vr, owner, -LINE / 2, LINE / 2, -hw, hw);
        // attack lines with dashed extensions
        double al = court.attackLine();
        if (al > 0 && al < hl) {
            for (int s = -1; s <= 1; s += 2) {
                double u0 = s * al;
                double u1 = s * (al - LINE);
                rect(world, line, vr, owner, Math.min(u0, u1), Math.max(u0, u1), -hw, hw);
                for (int k = 0; k < 5; k++) {
                    double start = hw + 0.2 + k * 0.35;
                    rect(world, line, vr, owner, Math.min(u0, u1), Math.max(u0, u1), start, start + 0.15);
                    rect(world, line, vr, owner, Math.min(u0, u1), Math.max(u0, u1), -start - 0.15, -start);
                }
            }
        }
        // service zone marks: 15 cm long, 20 cm behind the end line, in line with the side lines
        for (int s = -1; s <= 1; s += 2) {
            double a = s * (hl + 0.2);
            double b = s * (hl + 0.35);
            rect(world, line, vr, owner, Math.min(a, b), Math.max(a, b), hw - LINE, hw);
            rect(world, line, vr, owner, Math.min(a, b), Math.max(a, b), -hw, -hw + LINE);
        }

        double top = court.netHeight();
        double bottomOfNet = top - court.netBand();
        double postW = court.net().geometry().postW();
        // posts with padding
        for (int s = -1; s <= 1; s += 2) {
            box(world, Material.LIGHT_GRAY_CONCRETE, vr, owner, 0, 0, s * postW, 0.11, top + 0.12, 0.11);
            box(world, Material.BLUE_CONCRETE, vr, owner, 0, 0.0, s * postW, 0.2, 1.7, 0.2);
            box(world, Material.GRAY_CONCRETE, vr, owner, 0, 0, s * postW, 0.5, 0.06, 0.5);
        }
        // side bands: white 5 cm x net height, above each side line
        for (int s = -1; s <= 1; s += 2) {
            double w = s * (hw - LINE / 2);
            box(world, Material.WHITE_CONCRETE, vr, owner, 0, bottomOfNet, w, 0.026, court.netBand(), LINE);
        }
        // antennas: 1.8 m rods, red/white 10 cm stripes
        double antennaBottom = top - court.netBand();
        double antennaLen = court.netBand() + court.antennaAbove();
        int stripes = (int) Math.round(antennaLen / 0.1);
        double stripe = antennaLen / stripes;
        for (int s = -1; s <= 1; s += 2) {
            double w = s * hw;
            for (int k = 0; k < stripes; k++) {
                Material m = (k % 2 == 0) ? Material.WHITE_CONCRETE : Material.RED_CONCRETE;
                box(world, m, vr, owner, 0, antennaBottom + k * stripe, w, 0.028, stripe, 0.028);
            }
        }
        // net cable from the tape to the posts
        for (int s = -1; s <= 1; s += 2) {
            double from = s * court.net().geometry().halfLength();
            double to = s * postW;
            rectVertical(world, Material.GRAY_CONCRETE, vr, owner, Math.min(from, to), Math.max(from, to), top - 0.045, 0.012);
        }

        net = new NetRenderer(court, cd.netColumns(), cd.netRows(), cd.netInterpolation());
        net.spawn(world, vr, owner);
    }

    /** Flat floor line spanning u in [u0, u1], w in [w0, w1]. */
    private void rect(World world, Material mat, float vr, String owner, double u0, double u1, double w0, double w1) {
        Vec3 a = court.frame().toWorld(u0, LINE_Y, w0, new Vec3());
        Vec3 b = court.frame().toWorld(u1, LINE_Y, w1, new Vec3());
        spawnAabb(world, mat, vr, owner, Math.min(a.x, b.x), a.y, Math.min(a.z, b.z),
                Math.abs(b.x - a.x), LINE_THICK, Math.abs(b.z - a.z));
    }

    private void rectVertical(World world, Material mat, float vr, String owner, double w0, double w1, double y, double size) {
        Vec3 a = court.frame().toWorld(-size / 2, y, w0, new Vec3());
        Vec3 b = court.frame().toWorld(size / 2, y, w1, new Vec3());
        spawnAabb(world, mat, vr, owner, Math.min(a.x, b.x), y + court.centerY(), Math.min(a.z, b.z),
                Math.abs(b.x - a.x), size, Math.abs(b.z - a.z));
    }

    /** Box centred on (u, w) horizontally, starting at height y. Sizes are along u, y, w. */
    private void box(World world, Material mat, float vr, String owner, double u, double y, double w,
                     double su, double sy, double sw) {
        Vec3 c = court.frame().toWorld(u, y, w, new Vec3());
        Vec3 size = court.frame().dirToWorld(su, sy, sw, new Vec3());
        double sx = Math.abs(size.x);
        double sz = Math.abs(size.z);
        spawnAabb(world, mat, vr, owner, c.x - sx / 2, c.y, c.z - sz / 2, sx, sy, sz);
    }

    private void spawnAabb(World world, Material mat, float vr, String owner, double x, double y, double z,
                           double sx, double sy, double sz) {
        double cx = x + sx / 2;
        double cz = z + sz / 2;
        Location at = new Location(world, cx, y, cz);
        Matrix4f m = Displays.box(-sx / 2, 0, -sz / 2, sx, sy, sz);
        displays.add(Displays.block(world, at, mat, m, vr, owner));
    }

    public void tick() {
        if (net != null) {
            net.tick();
        }
    }

    public void remove() {
        for (Display d : displays) {
            d.remove();
        }
        displays.clear();
        if (net != null) {
            net.remove();
            net = null;
        }
    }

    public int entityCount() {
        return displays.size() + (net == null ? 0 : net.entityCount());
    }
}
