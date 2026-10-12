package dev.powderpass.ski;

import dev.powderpass.config.Messages;
import dev.powderpass.config.PluginConfig;
import dev.powderpass.gear.Gear;
import dev.powderpass.gear.GearType;
import dev.powderpass.physics.ComboTracker;
import dev.powderpass.physics.Heightfield;
import dev.powderpass.physics.SkiInput;
import dev.powderpass.physics.SkiParams;
import dev.powderpass.physics.SkiPhysics;
import dev.powderpass.physics.SkiState;
import dev.powderpass.physics.StepResult;
import dev.powderpass.physics.SurfaceKind;
import dev.powderpass.physics.TrickEvaluator;
import dev.powderpass.physics.TrickResult;
import dev.powderpass.profile.PlayerData;
import dev.powderpass.util.MathUtil;
import dev.powderpass.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.joml.Vector3d;

import java.time.Duration;

/** One player riding skis or a snowboard. Created and ticked by {@link SkiManager}. */
public final class SkiSession {

    public enum EndReason {
        UNCLIP, CRASH, ROUGH, QUIT, DEATH, TELEPORT, WORLD, DISABLE, INVALID
    }

    private final SkiManager manager;
    private final Player player;
    private final Gear gear;
    private final SkiParams params;
    private final SkiState state;
    private final WorldTerrain terrain;
    private final Heightfield field;
    private final StepResult result = new StepResult();
    private final SkierRig rig;
    private final SkierVisibility visibility;
    private final ComboTracker combo;
    private final PlayerData data;
    private final long startTick;
    private final Vector3d prevPos = new Vector3d();

    private boolean ended;
    private boolean dismountAllowed;
    private int roughTicks;
    private double pushPhase = -1;
    private long windStartedAt = -1;
    private Component trickLine;
    private long trickLineUntil;
    private Object raceState;

    SkiSession(SkiManager manager, Player player, Gear gear, SkiParams params, SkiState state, WorldTerrain terrain,
               SkierRig rig, PlayerData data, long tick) {
        this.manager = manager;
        this.player = player;
        this.gear = gear;
        this.params = params;
        this.state = state;
        this.terrain = terrain;
        this.field = new Heightfield(terrain, params.smoothingRadius(), params.cliffSlope());
        this.rig = rig;
        this.visibility = new SkierVisibility(player);
        PluginConfig c = manager.config();
        this.combo = new ComboTracker(c.comboWindowTicks, c.comboStep, c.comboMax);
        this.data = data;
        this.startTick = tick;
        this.prevPos.set(state.pos);
    }

    void begin(long tick) {
        if (manager.config().hidePlayerModel) {
            visibility.apply(tick);
        }
        rig.hideFromRider(player);
    }

    // ------------------------------------------------------------------------------------------------ tick

    void tick(long now) {
        if (ended) {
            return;
        }
        if (!player.isOnline() || player.isDead() || !rig.valid() || player.getVehicle() != rig.anchor()) {
            manager.end(this, EndReason.INVALID, true);
            return;
        }
        if (player.getWorld() != terrain.world()) {
            manager.end(this, EndReason.WORLD, false);
            return;
        }
        terrain.tick(now);
        Input input = player.getCurrentInput();
        Location eye = player.getLocation();
        SkiInput in = new SkiInput(eye.getYaw(), input.isForward(), input.isBackward(), input.isLeft(), input.isRight(),
                input.isJump(), input.isSneak());

        prevPos.set(state.pos);
        SkiPhysics.step(state, in, field, params, result);
        if (result.crashed()) {
            manager.crash(this, result.crash);
            return;
        }

        advancePushAnimation();
        rig.moveTo(state.pos.x, state.pos.y, state.pos.z);
        rig.pose(pose(eye.getYaw(), eye.getPitch(), in.sneak()));

        World world = terrain.world();
        SkiEffects effects = manager.effects();
        Location feet = new Location(world, state.pos.x, state.pos.y, state.pos.z);
        if (result.jumped) {
            effects.jump(feet);
        }
        if (result.wallBump) {
            effects.bump(feet);
        }
        if (result.landed) {
            onLanded(now, feet);
            if (ended) {
                return;
            }
        }

        recordStats();
        long banked = combo.tick(now);
        if (banked > 0) {
            onComboBanked(banked);
        }

        if (state.grounded && state.surface == SurfaceKind.ROUGH && state.speed() < 0.12) {
            if (++roughTicks > manager.config().autoUnclipRoughTicks) {
                manager.end(this, EndReason.ROUGH, true);
                return;
            }
        } else {
            roughTicks = 0;
        }

        manager.courses().onSkierMove(this, prevPos, state.pos, now);
        if (ended) {
            return;
        }

        effects.ride(player, world, state, now);
        wind(now);
        if (now % 2 == 0) {
            hud(now);
        }
        if (now % 10 == 0) {
            rig.nametag(Text.mm("<white>" + Text.escape(player.getName()) + "\n" + speedColor(state.speed())
                    + Text.speed(state.speed(), data.mph)));
            visibility.broadcastFake();
        }
        if (now % 20 == 0) {
            visibility.refresh(now);
        }
    }

    private RigPose pose(float lookYaw, float lookPitch, boolean sneak) {
        double visualYaw = state.heading + state.spin;
        return new RigPose(visualYaw, MathUtil.wrapDegrees(lookYaw - visualYaw), lookPitch,
                state.normal.x, state.normal.y, state.normal.z, state.lean, state.crouch, state.flip,
                !state.grounded, sneak && !state.grounded, pushPhase, gear.type() == GearType.SNOWBOARD);
    }

    private void advancePushAnimation() {
        if (state.pushing && gear.type() == GearType.SKIS) {
            pushPhase = pushPhase < 0 ? 0 : pushPhase + 1.0 / 14.0;
            if (pushPhase >= 1) {
                pushPhase -= 1;
            }
        } else if (pushPhase >= 0) {
            pushPhase += 1.0 / 14.0;
            if (pushPhase >= 1) {
                pushPhase = -1;
            }
        }
    }

    private void onLanded(long now, Location feet) {
        manager.effects().land(feet, result.landingImpact, state.surface);
        PluginConfig c = manager.config();
        TrickResult trick = TrickEvaluator.evaluate(result.landingSpin, result.landingFlip, result.landingGrabTicks,
                result.landingAirTicks, result.landingSwitch, gear.type() == GearType.SNOWBOARD);
        manager.badges().onAir(player, data, result.landingAirTicks);
        if (trick != null) {
            int earned = combo.addTrick(trick.score(), now);
            data.tricksLanded++;
            if (trick.score() > data.bestTrickScore) {
                data.bestTrickScore = trick.score();
                data.bestTrickName = trick.name();
            }
            data.markDirty();
            manager.badges().onTrick(player, data, trick);
            String multiplier = combo.count() > 1 ? " <gold>x" + Text.oneDecimal(combo.multiplier()) : "";
            trickLine = Text.mm("<#e0f2fe><bold>" + Text.escape(trick.name()) + "</bold> <yellow>+" + Text.number(earned) + multiplier);
            trickLineUntil = now + 40;
            player.showTitle(Title.title(Component.empty(), trickLine,
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ofMillis(350))));
            manager.effects().trick(player, combo.count(), trick.score() >= 600);
        }
        if (c.landingDamage && result.landingImpact > params.safeImpact()) {
            double damage = (result.landingImpact - params.safeImpact()) * c.impactDamagePerUnit;
            if (damage >= 0.5) {
                manager.damage(player, damage);
            }
        }
    }

    private void onComboBanked(long total) {
        if (total > data.bestCombo) {
            data.bestCombo = total;
            data.markDirty();
        }
        manager.badges().onCombo(player, data, total);
        if (combo.count() == 0 && total > 0) {
            trickLine = manager.messages().get("trick.combo-banked", Messages.p("points", Text.number(total)));
            trickLineUntil = manager.tick() + 30;
        }
    }

    private void recordStats() {
        double speed = state.speed();
        if (speed > data.topSpeed) {
            data.topSpeed = speed;
            data.markDirty();
        }
        manager.badges().onSpeed(player, data, speed);
        if (result.horizontalDistance > 0) {
            data.distance += result.horizontalDistance;
            if (state.grounded && state.surface == SurfaceKind.POWDER) {
                data.powderDistance += result.horizontalDistance;
            }
            data.markDirty();
        }
        if (result.verticalDrop > 0 && state.grounded) {
            data.vertical += result.verticalDrop;
        }
        if (!state.grounded) {
            data.airTicks++;
        }
    }

    private void wind(long now) {
        if (!manager.config().sounds) {
            return;
        }
        double speed = state.speed();
        if (speed > 0.95) {
            if (windStartedAt < 0 || now - windStartedAt > 180) {
                player.playSound(player.getLocation(), Sound.ITEM_ELYTRA_FLYING, SoundCategory.PLAYERS,
                        (float) Math.min(0.6, (speed - 0.8) * 0.8), 1.0f);
                windStartedAt = now;
            }
        } else if (speed < 0.8 && windStartedAt >= 0) {
            stopWind();
        }
    }

    void stopWind() {
        if (windStartedAt >= 0) {
            player.stopSound(Sound.ITEM_ELYTRA_FLYING, SoundCategory.PLAYERS);
            windStartedAt = -1;
        }
    }

    // ------------------------------------------------------------------------------------------------ HUD

    private void hud(long now) {
        StringBuilder sb = new StringBuilder();
        double speed = state.speed();
        if (state.grounded) {
            sb.append(speedColor(speed)).append("⛷ <bold>").append(Text.speed(speed, data.mph)).append("</bold> ")
                    .append(speedBar(speed)).append("  <dark_gray>│ <#bae6fd>◢ ")
                    .append(Math.round(state.slopeDegrees())).append("°  <dark_gray>│ ")
                    .append(surfaceLabel(state.surface));
            if (state.jumpCharge > 0) {
                sb.append("  <dark_gray>│ <yellow>⇪ ").append("▮".repeat(Math.max(1, state.jumpCharge / 2)));
            }
        } else {
            sb.append("<white>✈ <bold>").append(Text.oneDecimal(state.airTicks / 20.0)).append("s</bold>");
            int spin = (int) (Math.round(Math.abs(state.spin) / 180.0) * 180);
            if (spin > 0) {
                sb.append("  <aqua>⟳ ").append(spin);
            }
            int flips = (int) Math.round(Math.abs(state.flip) / 360.0);
            if (Math.abs(state.flip) > 60) {
                sb.append("  <light_purple>⇅ ").append(flips > 1 ? flips + "x " : "").append(state.flip < 0 ? "Back" : "Front");
            }
            if (state.grabTicks >= TrickEvaluator.MIN_GRAB_TICKS) {
                sb.append("  <yellow>✋ Grab");
            }
        }
        if (combo.count() > 0) {
            int bars = (int) Math.ceil(combo.remaining(now) * 6);
            sb.append("  <dark_gray>│ <gold>✦ x").append(Text.oneDecimal(combo.multiplier())).append(" ")
                    .append(Text.number(combo.total())).append(" <#f59e0b>").append("▮".repeat(bars));
        }
        Component line = Text.mm(sb.toString());
        if (trickLine != null && now < trickLineUntil && state.grounded) {
            line = trickLine;
        }
        player.sendActionBar(line);
    }

    private static String speedColor(double speed) {
        double kmh = MathUtil.toKmh(speed);
        if (kmh < 40) {
            return "<white>";
        }
        if (kmh < 80) {
            return "<#7dd3fc>";
        }
        if (kmh < 110) {
            return "<#fde047>";
        }
        return "<#f87171>";
    }

    private static String speedBar(double speed) {
        int filled = (int) Math.round(MathUtil.clamp(MathUtil.toKmh(speed) / 130.0, 0, 1) * 8);
        return "<#38bdf8>" + "▮".repeat(filled) + "<dark_gray>" + "▮".repeat(8 - filled);
    }

    private static String surfaceLabel(SurfaceKind kind) {
        return switch (kind) {
            case POWDER -> "<white>❄ Powder";
            case ICE -> "<#a5f3fc>◆ Ice";
            case ROUGH -> "<#fca5a5>⚠ Off-piste";
            default -> "<#e0f2fe>❄ Groomed";
        };
    }

    // ------------------------------------------------------------------------------------------------ accessors

    public Player player() {
        return player;
    }

    public Gear gear() {
        return gear;
    }

    public SkiState state() {
        return state;
    }

    public SkiParams params() {
        return params;
    }

    public SkierRig rig() {
        return rig;
    }

    public SkierVisibility visibility() {
        return visibility;
    }

    public ComboTracker combo() {
        return combo;
    }

    public PlayerData data() {
        return data;
    }

    public long startTick() {
        return startTick;
    }

    public World world() {
        return terrain.world();
    }

    public boolean ended() {
        return ended;
    }

    void markEnded() {
        ended = true;
    }

    public boolean dismountAllowed() {
        return dismountAllowed;
    }

    void allowDismount() {
        dismountAllowed = true;
    }

    /** Opaque per-session state owned by the course system. */
    public Object raceState() {
        return raceState;
    }

    public void raceState(Object state) {
        this.raceState = state;
    }

    /** Current visual pose; used for ghost recording. */
    public RigPose currentPose() {
        Location eye = player.getLocation();
        return pose(eye.getYaw(), eye.getPitch(), state.tucking);
    }
}
