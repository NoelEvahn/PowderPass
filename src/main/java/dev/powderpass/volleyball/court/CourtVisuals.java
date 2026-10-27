package dev.powderpass.volleyball.court;

import dev.powderpass.volleyball.config.Settings;
import dev.powderpass.volleyball.math.Vec3;
import dev.powderpass.volleyball.util.Displays;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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

    /** Named groups of court decorations that can be highlighted. */
    public enum Mark {
        SIDE_POS, SIDE_NEG, END_POS, END_NEG, CENTER, ATTACK_POS, ATTACK_NEG, ANTENNA_POS, ANTENNA_NEG;

        public static Mark end(int side) {
            return side > 0 ? END_POS : END_NEG;
        }

        public static Mark sideline(int side) {
            return side > 0 ? SIDE_POS : SIDE_NEG;
        }

        public static Mark attack(int side) {
            return side > 0 ? ATTACK_POS : ATTACK_NEG;
        }

        public static Mark antenna(int side) {
            return side > 0 ? ANTENNA_POS : ANTENNA_NEG;
        }
    }

    private final org.bukkit.plugin.Plugin plugin;
    private final Court court;
    private final List<Display> displays = new ArrayList<>();
    private final java.util.Map<Mark, List<org.bukkit.entity.BlockDisplay>> marks = new java.util.EnumMap<>(Mark.class);
    private final java.util.Map<org.bukkit.entity.BlockDisplay, org.bukkit.block.data.BlockData> original = new java.util.HashMap<>();
    private final java.util.Map<Mark, Long> flashUntil = new java.util.EnumMap<>(Mark.class);
    private final java.util.Map<Mark, org.bukkit.Color> highlights = new java.util.EnumMap<>(Mark.class);
    private final List<org.bukkit.entity.Entity> temporary = new ArrayList<>();
    /** Seats: bench seats per half (side -1 / +1) and the referee's seat (side 0). */
    private final List<Seat> seats = new ArrayList<>();

    /** A sittable spot: an invisible interaction box players click; they ride on top of it. */
    public record Seat(org.bukkit.entity.Interaction entity, int side, Location front) {
    }
    private Mark current;
    private NetRenderer net;

    public CourtVisuals(org.bukkit.plugin.Plugin plugin, Court court) {
        this.plugin = plugin;
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
        current = Mark.SIDE_POS;
        rect(world, line, vr, owner, -hl, hl, hw - LINE, hw);
        current = Mark.SIDE_NEG;
        rect(world, line, vr, owner, -hl, hl, -hw, -hw + LINE);
        current = Mark.END_POS;
        rect(world, line, vr, owner, hl - LINE, hl, -hw, hw);
        current = Mark.END_NEG;
        rect(world, line, vr, owner, -hl, -hl + LINE, -hw, hw);
        // centre line under the net
        current = Mark.CENTER;
        rect(world, line, vr, owner, -LINE / 2, LINE / 2, -hw, hw);
        current = null;
        // attack lines with dashed extensions
        double al = court.attackLine();
        if (al > 0 && al < hl) {
            for (int s = -1; s <= 1; s += 2) {
                double u0 = s * al;
                double u1 = s * (al - LINE);
                current = Mark.attack(s);
                rect(world, line, vr, owner, Math.min(u0, u1), Math.max(u0, u1), -hw, hw);
                for (int k = 0; k < 5; k++) {
                    double start = hw + 0.2 + k * 0.35;
                    rect(world, line, vr, owner, Math.min(u0, u1), Math.max(u0, u1), start, start + 0.15);
                    rect(world, line, vr, owner, Math.min(u0, u1), Math.max(u0, u1), -start - 0.15, -start);
                }
                current = null;
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
        // posts: octagonal brushed metal with padded wraps, a white band and a base plate
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            double w = sgn * postW;
            octo(world, Material.IRON_BLOCK, vr, owner, 0, 0, w, 0.10, top + 0.12);
            octo(world, Material.BLUE_CONCRETE, vr, owner, 0, 0.12, w, 0.24, 1.6);
            octo(world, Material.WHITE_CONCRETE, vr, owner, 0, 1.42, w, 0.245, 0.08);
            octo(world, Material.LIGHT_GRAY_CONCRETE, vr, owner, 0, top + 0.1, w, 0.12, 0.04);
            octo(world, Material.GRAY_CONCRETE, vr, owner, 0, 0, w, 0.42, 0.12);
        }
        if (cd.furniture()) {
            furniture(world, vr, owner, postW, top);
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
            current = Mark.antenna(s);
            for (int k = 0; k < stripes; k++) {
                Material m = (k % 2 == 0) ? Material.WHITE_CONCRETE : Material.RED_CONCRETE;
                box(world, m, vr, owner, 0, antennaBottom + k * stripe, w, 0.028, stripe, 0.028);
            }
            current = null;
        }
        // net cable from the tape to the posts
        for (int s = -1; s <= 1; s += 2) {
            double from = s * court.net().geometry().halfLength();
            double to = s * postW;
            rectVertical(world, Material.GRAY_CONCRETE, vr, owner, Math.min(from, to), Math.max(from, to), top - 0.045, 0.012);
            // bottom rope and the tensioning winch on the post
            rectVertical(world, Material.GRAY_CONCRETE, vr, owner, Math.min(from, to), Math.max(from, to), bottomOfNet + 0.02, 0.008);
            box(world, Material.GRAY_CONCRETE, vr, owner, 0.0, top - 0.75, s * (postW + 0.1), 0.08, 0.14, 0.08);
            box(world, Material.IRON_BLOCK, vr, owner, 0.07, top - 0.70, s * (postW + 0.1), 0.06, 0.03, 0.03);
        }

        net = new NetRenderer(court, cd.netColumns(), cd.netRows(), cd.netInterpolation());
        net.spawn(world, vr, owner, cd.netFill());
    }

    /**
     * Round-looking column centred on (u, w), from height y: a plus of two slabs whose corners are the vertices
     * of a regular octagon, filled with a square touching its diagonal edges. Nothing pokes outside the octagon.
     */
    private void octo(World world, Material mat, float vr, String owner, double u, double y, double w,
                      double diameter, double height) {
        double a = diameter / 2;
        double s = 2 * a * Math.tan(Math.PI / 8);
        double h = a * Math.sqrt(2);
        box(world, mat, vr, owner, u, y, w, 2 * a, height, s);
        box(world, mat, vr, owner, u, y, w, s, height, 2 * a);
        box(world, mat, vr, owner, u, y, w, h, height, h);
    }

    /**
     * First referee's stand at the post opposite the benches, two team benches and a scorer's table.
     */
    private void furniture(World world, float vr, String owner, double postW, double top) {
        // referee stand: platform so the referee's eyes are about 50 cm above the net
        double w = postW + 0.75;
        double floor = Math.max(1.0, top - 1.1);
        for (int su = -1; su <= 1; su += 2) {
            for (int sw = -1; sw <= 1; sw += 2) {
                box(world, Material.LIGHT_GRAY_CONCRETE, vr, owner, su * 0.3, 0, w + sw * 0.3, 0.06, floor, 0.06);
            }
        }
        box(world, Material.GRAY_CONCRETE, vr, owner, 0, floor, w, 0.8, 0.06, 0.8);
        // seat: cushion on four short legs, slim backrest facing the net
        for (int su = -1; su <= 1; su += 2) {
            for (int sw = -1; sw <= 1; sw += 2) {
                box(world, Material.GRAY_CONCRETE, vr, owner, su * 0.16, floor + 0.06, w + 0.12 + sw * 0.14, 0.04, 0.38, 0.04);
            }
        }
        box(world, Material.BLUE_CONCRETE, vr, owner, 0, floor + 0.44, w + 0.12, 0.40, 0.07, 0.36);
        box(world, Material.BLUE_CONCRETE, vr, owner, 0, floor + 0.51, w + 0.31, 0.40, 0.36, 0.05);
        box(world, Material.LIGHT_GRAY_CONCRETE, vr, owner, 0, floor + 0.85, w - 0.38, 0.75, 0.05, 0.05);
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            box(world, Material.LIGHT_GRAY_CONCRETE, vr, owner, sgn * 0.36, floor, w - 0.38, 0.05, 0.9, 0.05);
        }
        seat(world, owner, 0, floor + 0.06, w + 0.12, 0.45, 0.45, 0, w - 0.6);
        // ladder on the far side
        for (int r = 1; r <= 4; r++) {
            box(world, Material.IRON_BLOCK, vr, owner, 0, floor * r / 5.0, w + 0.42, 0.5, 0.04, 0.04);
        }
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            box(world, Material.IRON_BLOCK, vr, owner, sgn * 0.27, 0, w + 0.45, 0.05, floor, 0.05);
        }

        // team benches along the opposite side line, one per half
        double bw = -(court.halfWidth() + Math.min(court.freeZone(), 3.0) * 0.7) - 0.45;
        double len = Math.min(4.0, court.halfLength() - 1.5);
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            double uc = sgn * (2.0 + len / 2 - 0.5);
            int places = Math.max(1, (int) Math.floor(len / 0.6));
            for (int k = 0; k < places; k++) {
                double u = uc - len / 2 + (k + 0.5) * len / places;
                seat(world, owner, u, 0, bw, 0.5, 0.48, sgn, bw + 0.75);
            }
            box(world, Material.SPRUCE_PLANKS, vr, owner, uc, 0.42, bw, len, 0.06, 0.38);
            box(world, Material.SPRUCE_PLANKS, vr, owner, uc, 0.55, bw - 0.2, len, 0.35, 0.05);
            for (int k = -1; k <= 1; k++) {
                box(world, Material.GRAY_CONCRETE, vr, owner, uc + k * (len / 2 - 0.15), 0, bw, 0.06, 0.42, 0.32);
            }
        }
        // scorer's table at the centre line
        box(world, Material.WHITE_CONCRETE, vr, owner, 0, 0.72, bw - 0.1, 1.8, 0.05, 0.6);
        box(world, Material.BLUE_CONCRETE, vr, owner, 0, 0, bw + 0.18, 1.8, 0.72, 0.04);
        box(world, Material.GRAY_CONCRETE, vr, owner, -0.8, 0, bw - 0.1, 0.05, 0.72, 0.5);
        box(world, Material.GRAY_CONCRETE, vr, owner, 0.8, 0, bw - 0.1, 0.05, 0.72, 0.5);
    }

    private void seat(World world, String owner, double u, double y, double w, double width, double height, int side,
                      double frontW) {
        Vec3 c = court.frame().toWorld(u, y, w, new Vec3());
        Location at = new Location(world, c.x, c.y, c.z);
        org.bukkit.entity.Interaction it = world.spawn(at, org.bukkit.entity.Interaction.class, e -> {
            e.setInteractionWidth((float) width);
            e.setInteractionHeight((float) height);
            e.setResponsive(true);
            Displays.tag(e, "seat:" + court.name());
        });
        Location front = court.location(u, 0.01, frontW);
        front.setYaw(court.frame().yawOf(0, frontW > w ? 1 : -1));
        seats.add(new Seat(it, side, front));
        seatEntities.add(it);
    }

    private final List<org.bukkit.entity.Interaction> seatEntities = new ArrayList<>();

    public List<Seat> seats() {
        return seats;
    }

    /** A free bench seat on the given half (side -1 / +1), preferring the {@code index}-th. */
    public Seat freeBenchSeat(int side, int index) {
        List<Seat> mine = new ArrayList<>();
        for (Seat s : seats) {
            if (s.side() == side && s.entity().isValid() && s.entity().getPassengers().isEmpty()) {
                mine.add(s);
            }
        }
        if (mine.isEmpty()) {
            return null;
        }
        return mine.get(Math.min(index, mine.size() - 1));
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
        org.bukkit.entity.BlockDisplay d = Displays.block(world, at, mat, m, vr, owner);
        displays.add(d);
        if (current != null) {
            marks.computeIfAbsent(current, k -> new ArrayList<>()).add(d);
            original.put(d, d.getBlock());
        }
    }

    // ------------------------------------------------------------------------------------------- highlights

    /** Briefly recolours and outlines a group (e.g. the line a ball landed beyond). */
    public void flash(Mark mark, Material block, org.bukkit.Color glow, int ticks) {
        List<org.bukkit.entity.BlockDisplay> group = marks.get(mark);
        if (group == null) {
            return;
        }
        long until = court.world().getFullTime() + ticks;
        flashUntil.put(mark, until);
        org.bukkit.block.data.BlockData data = block.createBlockData();
        for (org.bukkit.entity.BlockDisplay d : group) {
            d.setBlock(data);
            d.setGlowColorOverride(glow);
            d.setGlowing(true);
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Long u = flashUntil.get(mark);
            if (u != null && u == until) {
                flashUntil.remove(mark);
                restore(mark);
            }
        }, ticks);
    }

    /** Keeps a group outlined until cleared with {@code null} (used for the service zone). */
    public void highlight(Mark mark, org.bukkit.Color glow) {
        if (glow == null) {
            highlights.remove(mark);
        } else {
            highlights.put(mark, glow);
        }
        if (!flashUntil.containsKey(mark)) {
            restore(mark);
        }
    }

    private void restore(Mark mark) {
        List<org.bukkit.entity.BlockDisplay> group = marks.get(mark);
        if (group == null) {
            return;
        }
        org.bukkit.Color keep = highlights.get(mark);
        for (org.bukkit.entity.BlockDisplay d : group) {
            if (!d.isValid()) {
                continue;
            }
            d.setBlock(original.get(d));
            d.setGlowing(keep != null);
            d.setGlowColorOverride(keep);
        }
    }

    /**
     * Calls a landing like a line judge: flashes the crossed line red (out) or a nearby line green (close
     * call in), leaves a coloured ball mark and a floating IN / OUT.
     *
     * @return true when the ball is in
     */
    public boolean callLanding(dev.powderpass.volleyball.math.Vec3 pos, double ballRadius) {
        double u = court.frame().toU(pos.x, pos.z);
        double w = court.frame().toW(pos.x, pos.z);
        boolean in = court.isIn(u, w, ballRadius);
        double hl = court.halfLength();
        double hw = court.halfWidth();
        if (!in) {
            if (Math.abs(u) > hl + ballRadius * 0.5) {
                flash(Mark.end(u > 0 ? 1 : -1), Material.RED_CONCRETE, org.bukkit.Color.RED, 50);
            }
            if (Math.abs(w) > hw + ballRadius * 0.5) {
                flash(Mark.sideline(w > 0 ? 1 : -1), Material.RED_CONCRETE, org.bukkit.Color.RED, 50);
            }
        } else {
            double close = 0.35;
            if (hl - Math.abs(u) < close) {
                flash(Mark.end(u > 0 ? 1 : -1), Material.LIME_CONCRETE, org.bukkit.Color.LIME, 40);
            }
            if (hw - Math.abs(w) < close) {
                flash(Mark.sideline(w > 0 ? 1 : -1), Material.LIME_CONCRETE, org.bukkit.Color.LIME, 40);
            }
        }
        ballMark(pos, in);
        return in;
    }

    /** A flat coloured disc where the ball touched down plus a floating call, removed after a few seconds. */
    private void ballMark(dev.powderpass.volleyball.math.Vec3 pos, boolean in) {
        World world = court.world();
        if (world == null) {
            return;
        }
        Material mat = in ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
        double y = court.centerY() + 0.008;
        double r = 0.13;
        double s = 2 * r * Math.tan(Math.PI / 8);
        double[][] parts = {{2 * r, s}, {s, 2 * r}, {r * Math.sqrt(2), r * Math.sqrt(2)}};
        Location at = new Location(world, pos.x, y, pos.z);
        for (double[] part : parts) {
            Matrix4f m = Displays.box(-part[0] / 2, 0, -part[1] / 2, part[0], 0.004, part[1]);
            org.bukkit.entity.BlockDisplay d = Displays.block(world, at, mat, m, 2f, "court:" + court.name());
            d.setBrightness(new Display.Brightness(15, 15));
            temporary.add(d);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                d.remove();
                temporary.remove(d);
            }, 70);
        }
        org.bukkit.entity.TextDisplay text = world.spawn(at.clone().add(0, 0.55, 0), org.bukkit.entity.TextDisplay.class, t -> {
            t.text(Component.text(in ? "IN" : "OUT",
                    in ? NamedTextColor.GREEN : NamedTextColor.RED,
                    TextDecoration.BOLD));
            t.setBillboard(Display.Billboard.CENTER);
            t.setBackgroundColor(org.bukkit.Color.fromARGB(120, 0, 0, 0));
            t.setBrightness(new Display.Brightness(15, 15));
            t.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.Quaternionf(),
                    new org.joml.Vector3f(1.6f), new org.joml.Quaternionf()));
            Displays.tag(t, "court:" + court.name());
        });
        temporary.add(text);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            text.remove();
            temporary.remove(text);
        }, 50);
    }

    /** The net glows (e.g. red on a net touch). */
    public void flashNet(org.bukkit.Color glow, int ticks) {
        if (net != null) {
            net.flash(plugin, glow, ticks);
        }
    }

    public void tick() {
        if (net != null) {
            net.tick();
        }
    }

    public void remove() {
        for (Display d : displays) {
            if (d != null) {
                d.remove();
            }
        }
        displays.clear();
        for (org.bukkit.entity.Interaction it : seatEntities) {
            it.eject();
            it.remove();
        }
        seatEntities.clear();
        seats.clear();
        marks.clear();
        original.clear();
        flashUntil.clear();
        for (org.bukkit.entity.Entity e : new ArrayList<>(temporary)) {
            e.remove();
        }
        temporary.clear();
        if (net != null) {
            net.remove();
            net = null;
        }
    }

    public int entityCount() {
        return displays.size() + seatEntities.size() + (net == null ? 0 : net.entityCount());
    }
}
