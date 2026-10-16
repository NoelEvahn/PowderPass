package com.powderpass.backrooms.cutscene;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A cutscene: a timeline (in ticks) of camera keyframes, fades, shakes, look targets and actions, built once and
 * played for one player by {@link CutsceneEngine}. Keyframes before a {@link #travel} are in the world the player
 * starts in; keyframes after it are in the destination world, which is resolved when the cutscene starts (so the
 * destination's chunks can load while the first part plays).
 * <p>
 * Camera keyframes give the position of the player's <b>eyes</b>; the engine converts that to its camera mount.
 * Between keyframes the camera follows a Catmull-Rom curve, so motion never jerks.
 */
public final class Cutscene {
    /** A camera keyframe: where the eyes are at tick {@code t}, and optionally what they look at. */
    public record Key(int t, double x, double y, double z, boolean afterTravel) {
    }

    /** Something that happens at tick {@code t}. */
    public record Action(int t, Consumer<Player> run) {
    }

    public record Fade(int t, int duration, boolean out) {
    }

    public record Shake(int t, int duration, double degrees) {
    }

    public record Look(int t, double x, double y, double z, boolean afterTravel, double yawDeg, double pitchDeg, boolean angles) {
    }

    final String id;
    final List<Key> keys = new ArrayList<>();
    final List<Action> actions = new ArrayList<>();
    final List<Fade> fades = new ArrayList<>();
    final List<Shake> shakes = new ArrayList<>();
    final List<Look> looks = new ArrayList<>();
    int duration;
    int travelAt = -1;
    /** Resolves the destination (the player's final standing spot, in the new world) when the cutscene starts. */
    Function<Player, Location> destination;
    /** Where to put the player if anything goes wrong before the travel (null = where they started). */
    Location fallback;
    Consumer<Player> onFinish;
    boolean skippable = true;
    /** Hide the player from everyone else while it plays (they are riding an invisible camera). */
    boolean hidden = true;
    /** End standing at the destination (true), or wherever the last keyframe is. */
    boolean endAtDestination = true;
    /** Short version used when the player has seen this one before (replay: first-time), or null. */
    Cutscene shortVersion;

    public Cutscene(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    private boolean after(int t) {
        return travelAt >= 0 && t >= travelAt;
    }

    /** Camera (eye) position at tick t, in the current world part (before or after the travel). */
    public Cutscene camera(int t, double x, double y, double z) {
        keys.add(new Key(t, x, y, z, after(t)));
        duration = Math.max(duration, t);
        return this;
    }

    public Cutscene camera(int t, Location l) {
        return camera(t, l.getX(), l.getY(), l.getZ());
    }

    /** Face a point from tick t on. */
    public Cutscene look(int t, double x, double y, double z) {
        looks.add(new Look(t, x, y, z, after(t), 0, 0, false));
        return this;
    }

    /** Face a direction from tick t on. */
    public Cutscene lookAngles(int t, double yawDeg, double pitchDeg) {
        looks.add(new Look(t, 0, 0, 0, after(t), yawDeg, pitchDeg, true));
        return this;
    }

    public Cutscene at(int t, Consumer<Player> run) {
        actions.add(new Action(t, run));
        duration = Math.max(duration, t);
        return this;
    }

    /** Fade to black over {@code duration} ticks (blindness: the closest a vanilla client gets to a fade). */
    public Cutscene fadeOut(int t, int duration) {
        fades.add(new Fade(t, duration, true));
        this.duration = Math.max(this.duration, t + duration);
        return this;
    }

    /** Fade back in from black over about {@code duration} ticks. */
    public Cutscene fadeIn(int t, int duration) {
        fades.add(new Fade(t, duration, false));
        this.duration = Math.max(this.duration, t + duration);
        return this;
    }

    public Cutscene shake(int t, int duration, double degrees) {
        shakes.add(new Shake(t, duration, degrees));
        this.duration = Math.max(this.duration, t + duration);
        return this;
    }

    /**
     * The world change: at tick t (which must be inside a full fade to black) the player is moved to the
     * destination world. Later keyframes are relative to the destination. {@code destination} gives the final
     * standing spot and is evaluated once, when the cutscene starts.
     */
    public Cutscene travel(int t, Function<Player, Location> destination) {
        this.travelAt = t;
        this.destination = destination;
        duration = Math.max(duration, t);
        return this;
    }

    public Cutscene length(int ticks) {
        duration = Math.max(duration, ticks);
        return this;
    }

    public Cutscene finish(Consumer<Player> onFinish) {
        this.onFinish = onFinish;
        return this;
    }

    public Cutscene fallback(Location l) {
        this.fallback = l;
        return this;
    }

    public Cutscene skippable(boolean s) {
        this.skippable = s;
        return this;
    }

    public Cutscene visibleToOthers() {
        this.hidden = false;
        return this;
    }

    public Cutscene endWhereTheCameraIs() {
        this.endAtDestination = false;
        return this;
    }

    public Cutscene shortVersion(Cutscene c) {
        this.shortVersion = c;
        return this;
    }

    public int duration() {
        return duration;
    }

    public int travelTick() {
        return travelAt;
    }
}
