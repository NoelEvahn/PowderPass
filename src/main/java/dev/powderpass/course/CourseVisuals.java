package dev.powderpass.course;

import dev.powderpass.display.Displays;
import dev.powderpass.display.StaticStructure;
import dev.powderpass.display.Transforms;
import dev.powderpass.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Builds the gates, start arch, finish arch and leaderboard board of a course out of displays. */
final class CourseVisuals {

    /** A flag panel that flutters between two poses. */
    record Flag(int part, Matrix4f a, Matrix4f b) {
    }

    private static final float VIEW = 2.0f;

    private final StaticStructure structure;
    private final List<Flag> flags = new ArrayList<>();
    private int leaderboardPart = -1;
    private int finishTextPart = -1;
    private boolean flutterToggle;

    CourseVisuals(World world, Course course, Supplier<Component> leaderboard, Supplier<Component> finishText) {
        this.structure = new StaticStructure(world);
        Course.GateDef start = course.start();
        buildStart(world, course, start, leaderboard);
        List<Course.GateDef> gates = course.slalomGates();
        for (int i = 0; i < gates.size(); i++) {
            buildGate(world, gates.get(i), i % 2 == 0);
        }
        if (course.finish() != null) {
            buildFinish(world, course.finish(), finishText);
        }
    }

    StaticStructure structure() {
        return structure;
    }

    private Location center(World world, Course.GateDef g) {
        return new Location(world, g.x(), g.y(), g.z());
    }

    private void buildStart(World world, Course course, Course.GateDef g, Supplier<Component> leaderboard) {
        Location at = center(world, g);
        Matrix4f frame = Transforms.yaw(g.yaw());
        float half = (float) g.width() / 2f;
        for (float side : new float[]{half, -half}) {
            Matrix4f m = Transforms.pillar(frame, side, 0f, 0f, 0.22f, 2.9f, 0.22f);
            structure.add(at, l -> Displays.block(l, Material.WHITE_CONCRETE.createBlockData(), m, VIEW, null));
        }
        Matrix4f beam = Transforms.box(frame, -half - 0.15f, 2.9f, -0.12f, half * 2 + 0.3f, 0.5f, 0.24f);
        structure.add(at, l -> Displays.block(l, Material.RED_CONCRETE.createBlockData(), beam, VIEW, null));
        Matrix4f title = new Matrix4f(frame).translate(0f, 3.55f, 0f).scale(1.6f);
        Component text = Text.mm("<gradient:#f87171:#fb923c><bold>START</bold></gradient>\n<white>" + Text.escape(course.name()));
        structure.add(at, l -> Displays.text(l, text, title, VIEW, t -> t.setBillboard(Display.Billboard.CENTER)));
        // Leaderboard board beside the left pole.
        Location board = at.clone();
        double r = Math.toRadians(g.yaw());
        board.add(Math.cos(r) * (half + 2.2), 0, Math.sin(r) * (half + 2.2));
        Matrix4f boardM = new Matrix4f().translate(0f, 1.2f, 0f);
        leaderboardPart = structure.add(board, l -> Displays.text(l, leaderboard.get(), boardM, VIEW, t -> {
            t.setBillboard(Display.Billboard.CENTER);
            t.setLineWidth(220);
        }));
    }

    private void buildGate(World world, Course.GateDef g, boolean red) {
        Location at = center(world, g);
        Matrix4f frame = Transforms.yaw(g.yaw());
        float half = (float) g.width() / 2f;
        Material pole = red ? Material.RED_CONCRETE : Material.BLUE_CONCRETE;
        Material panel = red ? Material.RED_WOOL : Material.BLUE_WOOL;
        for (float side : new float[]{half, -half}) {
            Matrix4f p = Transforms.pillar(frame, side, 0f, 0f, 0.09f, 1.9f, 0.09f);
            structure.add(at, l -> Displays.block(l, pole.createBlockData(), p, VIEW, null));
            // Flag panel pointing from the pole towards the gate centre, fluttering about the pole.
            float inward = side > 0 ? -1f : 1f;
            Matrix4f a = flagMatrix(frame, side, inward, 7f);
            Matrix4f b = flagMatrix(frame, side, inward, -7f);
            int part = structure.add(at, l -> Displays.block(l, panel.createBlockData(), a, VIEW, d -> d.setInterpolationDuration(30)));
            flags.add(new Flag(part, a, b));
        }
    }

    private static Matrix4f flagMatrix(Matrix4f frame, float poleX, float inward, float angle) {
        Matrix4f m = new Matrix4f(frame).translate(poleX, 1.25f, 0f).rotateY((float) Math.toRadians(angle));
        if (inward > 0) {
            return m.translate(0f, 0f, -0.015f).scale(0.62f, 0.55f, 0.03f);
        }
        return m.translate(-0.62f, 0f, -0.015f).scale(0.62f, 0.55f, 0.03f);
    }

    private void buildFinish(World world, Course.GateDef g, Supplier<Component> finishText) {
        Location at = center(world, g);
        Matrix4f frame = Transforms.yaw(g.yaw());
        float half = (float) g.width() / 2f;
        for (float side : new float[]{half, -half}) {
            Matrix4f m = Transforms.pillar(frame, side, 0f, 0f, 0.25f, 3.0f, 0.25f);
            structure.add(at, l -> Displays.block(l, Material.BLACK_CONCRETE.createBlockData(), m, VIEW, null));
        }
        int tiles = Math.max(4, (int) Math.round(g.width() * 1.2));
        float tile = (half * 2 + 0.3f) / tiles;
        for (int i = 0; i < tiles; i++) {
            for (int row = 0; row < 2; row++) {
                boolean white = (i + row) % 2 == 0;
                Matrix4f m = Transforms.box(frame, -half - 0.15f + i * tile, 3.0f + row * 0.28f, -0.1f, tile, 0.28f, 0.2f);
                structure.add(at, l -> Displays.block(l, (white ? Material.WHITE_CONCRETE : Material.BLACK_CONCRETE).createBlockData(), m, VIEW, null));
            }
        }
        Matrix4f title = new Matrix4f(frame).translate(0f, 3.9f, 0f).scale(1.6f);
        finishTextPart = structure.add(at, l -> Displays.text(l, finishText.get(), title, VIEW, t -> t.setBillboard(Display.Billboard.CENTER)));
    }

    void sync(boolean active) {
        structure.sync(active);
    }

    void despawn() {
        structure.despawn();
    }

    void flutter() {
        flutterToggle = !flutterToggle;
        for (Flag f : flags) {
            Entity e = structure.live(f.part());
            if (e instanceof Display d) {
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(30);
                d.setTransformationMatrix(flutterToggle ? f.b() : f.a());
            }
        }
    }

    void updateTexts(Component leaderboard, Component finish) {
        if (leaderboardPart >= 0 && structure.live(leaderboardPart) instanceof TextDisplay t) {
            t.text(leaderboard);
        }
        if (finishTextPart >= 0 && structure.live(finishTextPart) instanceof TextDisplay t) {
            t.text(finish);
        }
    }
}
