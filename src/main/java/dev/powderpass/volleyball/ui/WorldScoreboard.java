package dev.powderpass.volleyball.ui;

import dev.powderpass.volleyball.util.Displays;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A physical scoreboard made of stacked text displays, linked to a court. */
public final class WorldScoreboard {

    private final String name;
    private String court;
    private Location location;
    private float scale;
    private Display.Billboard billboard;

    private TextDisplay header;
    private TextDisplay score;
    private TextDisplay footer;
    private Component lastHeader;
    private Component lastScore;
    private Component lastFooter;

    public WorldScoreboard(String name, String court, Location location, float scale, Display.Billboard billboard) {
        this.name = name;
        this.court = court;
        this.location = location;
        this.scale = scale;
        this.billboard = billboard;
    }

    public String name() {
        return name;
    }

    public String court() {
        return court;
    }

    public void setCourt(String court) {
        this.court = court;
    }

    public Location location() {
        return location.clone();
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public float scale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = scale;
    }

    public Display.Billboard billboard() {
        return billboard;
    }

    public void setBillboard(Display.Billboard billboard) {
        this.billboard = billboard;
    }

    public void spawn() {
        remove();
        World w = location.getWorld();
        if (w == null) {
            return;
        }
        // stack: footer at the bottom, big score in the middle, header on top
        footer = text(w, 0, 0.9f);
        score = text(w, 0.32f * scale, 3.2f);
        header = text(w, 1.25f * scale, 1.1f);
        lastHeader = null;
        lastScore = null;
        lastFooter = null;
    }

    private TextDisplay text(World w, float yOffset, float size) {
        Location at = location.clone().add(0, yOffset, 0);
        at.setPitch(0);
        return w.spawn(at, TextDisplay.class, d -> {
            float s = size * scale;
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(s, s, s), new Quaternionf()));
            d.setBillboard(billboard);
            d.setBackgroundColor(Color.fromARGB(170, 12, 14, 22));
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setLineWidth(400);
            d.setShadowed(true);
            d.setViewRange(2f);
            d.setBrightness(new Display.Brightness(15, 15));
            Displays.tag(d, "board:" + name);
        });
    }

    public void show(Component headerText, Component scoreText, Component footerText) {
        if (header == null || !header.isValid()) {
            spawn();
        }
        if (header == null) {
            return;
        }
        if (!headerText.equals(lastHeader)) {
            header.text(headerText);
            lastHeader = headerText;
        }
        if (!scoreText.equals(lastScore)) {
            score.text(scoreText);
            lastScore = scoreText;
        }
        if (!footerText.equals(lastFooter)) {
            footer.text(footerText);
            lastFooter = footerText;
        }
    }

    public void remove() {
        for (TextDisplay d : new TextDisplay[]{header, score, footer}) {
            if (d != null) {
                d.remove();
            }
        }
        header = null;
        score = null;
        footer = null;
    }
}
