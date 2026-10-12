package dev.powderpass.ski;

import dev.powderpass.PowderPassPlugin;
import dev.powderpass.config.Messages;
import dev.powderpass.config.PluginConfig;
import dev.powderpass.course.CourseManager;
import dev.powderpass.gear.Gear;
import dev.powderpass.gear.GearType;
import dev.powderpass.physics.CrashReason;
import dev.powderpass.physics.Heightfield;
import dev.powderpass.physics.SkiParams;
import dev.powderpass.physics.SkiState;
import dev.powderpass.physics.SteeringMode;
import dev.powderpass.profile.Badge;
import dev.powderpass.profile.BadgeService;
import dev.powderpass.profile.PlayerData;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Owns every active {@link SkiSession}: starting, ticking, crashing and ending them. */
public final class SkiManager {

    /** A piece of gear flying off after a wipeout. */
    private static final class Debris {
        final Display display;
        final Location pos;
        final Vector vel;
        final double floor;
        final float spinX;
        final float spinY;
        final Matrix4f base;
        int age;

        Debris(Display display, Location pos, Vector vel, double floor) {
            this.display = display;
            this.pos = pos;
            this.vel = vel;
            this.floor = floor;
            ThreadLocalRandom r = ThreadLocalRandom.current();
            this.spinX = (float) r.nextDouble(-0.5, 0.5);
            this.spinY = (float) r.nextDouble(-0.6, 0.6);
            org.bukkit.util.Transformation t = display.getTransformation();
            this.base = new Matrix4f().translate(t.getTranslation()).rotate(t.getLeftRotation())
                    .scale(t.getScale()).rotate(t.getRightRotation());
        }
    }

    private final PowderPassPlugin plugin;
    private final Map<UUID, SkiSession> sessions = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final List<Debris> debris = new ArrayList<>();
    private SkiEffects effects;
    private long tick;

    public SkiManager(PowderPassPlugin plugin) {
        this.plugin = plugin;
        this.effects = new SkiEffects(plugin.config());
    }

    public void reload() {
        this.effects = new SkiEffects(plugin.config());
    }

    // ------------------------------------------------------------------------------------------------ access

    public PluginConfig config() {
        return plugin.config();
    }

    public Messages messages() {
        return plugin.messages();
    }

    public SkiEffects effects() {
        return effects;
    }

    public BadgeService badges() {
        return plugin.badges();
    }

    public CourseManager courses() {
        return plugin.courses();
    }

    public long tick() {
        return tick;
    }

    public SkiSession session(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public boolean skiing(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public Collection<SkiSession> sessions() {
        return List.copyOf(sessions.values());
    }

    /** The session whose rig anchor is {@code vehicle}, if any. */
    public SkiSession byAnchor(Entity vehicle) {
        for (SkiSession s : sessions.values()) {
            if (s.rig().anchor().equals(vehicle)) {
                return s;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------------ start

    /** Attempts to click {@code player} into {@code gear}. Sends feedback on failure. */
    public boolean start(Player player, Gear gear) {
        PluginConfig c = config();
        Messages m = messages();
        if (skiing(player)) {
            return false;
        }
        if (!player.hasPermission("powderpass.ski")) {
            m.send(player, "no-permission");
            return false;
        }
        if (!c.worldEnabled(player.getWorld().getName())) {
            m.send(player, "world-disabled");
            return false;
        }
        if (player.isInsideVehicle() || player.isGliding() || player.isFlying() || player.isSwimming()
                || player.isSleeping() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
            m.actionBar(player, "ski.not-now");
            return false;
        }
        Long until = cooldowns.get(player.getUniqueId());
        if (until != null && until > tick) {
            m.actionBar(player, "ski.cooldown");
            return false;
        }
        if (sessions.size() >= c.maxActiveSkiers) {
            m.send(player, "ski.too-many");
            return false;
        }
        Block feet = player.getLocation().getBlock();
        Block below = feet.getRelative(0, -1, 0);
        if (!c.clickInBlocks.contains(feet.getType()) && !c.clickInBlocks.contains(below.getType())) {
            m.actionBar(player, "ski.need-snow");
            return false;
        }

        PlayerData data = plugin.profiles().get(player);
        PluginConfig.Tier tier = c.tiers.get(gear.tier());
        SteeringMode steering = data.steering != null ? data.steering : c.defaultSteering;
        SkiParams params = (gear.type() == GearType.SNOWBOARD ? c.boardParams : c.skiParams)
                .withTier(tier.speed(), tier.grip()).withSteering(steering);

        Location loc = player.getLocation();
        WorldTerrain terrain = new WorldTerrain(player.getWorld(), c);
        terrain.tick(tick);
        Heightfield field = new Heightfield(terrain, params.smoothingRadius(), params.cliffSlope());
        double ground = field.height(loc.getX(), loc.getZ(), loc.getY());
        double y = Double.isNaN(ground) || Math.abs(ground - loc.getY()) > 1.5 ? loc.getY() : ground;
        SkiState state = new SkiState(loc.getX(), y, loc.getZ(), loc.getYaw());
        Vector v = player.getVelocity();
        state.vel.set(clamp(v.getX(), 0.4), 0, clamp(v.getZ(), 0.4));

        RigPose pose = new RigPose(loc.getYaw(), 0, loc.getPitch(), 0, 1, 0, 0, 0.1, 0, false, false, -1,
                gear.type() == GearType.SNOWBOARD);
        SkierRig.Look look = new SkierRig.Look(gear.tier(), data.suit, gear.type() == GearType.SNOWBOARD, player,
                false, c.nametags);
        SkierRig rig = new SkierRig(plugin, new Location(loc.getWorld(), state.pos.x, state.pos.y, state.pos.z), look, pose,
                (float) c.riderOffset, c.movementSmoothing, c.poseSmoothing, c.viewRange, plugin.headYawOffset(), null);
        if (!rig.anchor().addPassenger(player)) {
            rig.remove();
            m.actionBar(player, "ski.not-now");
            return false;
        }

        SkiSession session = new SkiSession(this, player, gear, params, state, terrain, rig, data, tick);
        sessions.put(player.getUniqueId(), session);
        session.begin(tick);
        effects.clickIn(player);
        m.send(player, "ski.clicked-in", Messages.p("gear", gear.displayName()));
        if (!data.seenControls) {
            data.seenControls = true;
            data.markDirty();
            for (String line : m.raw("ski.controls").split("\n")) {
                player.sendMessage(dev.powderpass.util.Text.mm(line));
            }
        }
        badges().award(player, data, Badge.FIRST_TRACKS);
        return true;
    }

    private static double clamp(double v, double max) {
        return Math.max(-max, Math.min(max, v));
    }

    // ------------------------------------------------------------------------------------------------ ticking

    public void tickAll() {
        tick++;
        for (SkiSession session : List.copyOf(sessions.values())) {
            if (!session.ended()) {
                try {
                    session.tick(tick);
                } catch (RuntimeException e) {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error ticking skier " + session.player().getName(), e);
                    end(session, SkiSession.EndReason.INVALID, true);
                }
            }
        }
        tickDebris();
        if (tick % 200 == 0) {
            cooldowns.values().removeIf(t -> t <= tick);
        }
    }

    private void tickDebris() {
        Iterator<Debris> it = debris.iterator();
        while (it.hasNext()) {
            Debris d = it.next();
            d.age++;
            if (d.age > 50 || !d.display.isValid()) {
                if (d.display.isValid()) {
                    d.display.remove();
                }
                it.remove();
                continue;
            }
            d.vel.setY(d.vel.getY() - 0.05);
            d.pos.add(d.vel);
            if (d.pos.getY() < d.floor) {
                d.pos.setY(d.floor);
                d.vel.setY(-d.vel.getY() * 0.35);
                d.vel.setX(d.vel.getX() * 0.6);
                d.vel.setZ(d.vel.getZ() * 0.6);
            }
            d.display.teleport(d.pos);
            if (d.age % 2 == 0) {
                Matrix4f m = new Matrix4f().rotateY(d.spinY * d.age).rotateX(d.spinX * d.age).mul(d.base);
                d.display.setInterpolationDelay(0);
                d.display.setInterpolationDuration(2);
                d.display.setTransformationMatrix(m);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ ending

    public void crash(SkiSession session, CrashReason reason) {
        if (session.ended()) {
            return;
        }
        Player player = session.player();
        SkiState s = session.state();
        PlayerData data = session.data();
        data.crashes++;
        data.markDirty();
        session.combo().lose();

        Location feet = new Location(session.world(), s.pos.x, s.pos.y, s.pos.z);
        for (Display part : session.rig().detachGear()) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            Vector vel = new Vector(s.vel.x * 0.6 + r.nextDouble(-0.15, 0.15), 0.3 + r.nextDouble(0.25),
                    s.vel.z * 0.6 + r.nextDouble(-0.15, 0.15));
            part.setTeleportDuration(1);
            debris.add(new Debris(part, part.getLocation(), vel, s.pos.y + 0.6));
        }
        effects.crash(feet);
        Vector tumble = new Vector(s.vel.x * 0.7, 0.25, s.vel.z * 0.7);
        end(session, SkiSession.EndReason.CRASH, true);
        if (player.isOnline() && !player.isDead()) {
            player.setVelocity(tumble);
            Messages m = messages();
            player.showTitle(Title.title(m.get("ski.crash-title"),
                    m.get("ski.crash-subtitle", Messages.p("reason", reason.display())),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1300), Duration.ofMillis(500))));
            if (config().crashDamage > 0 && reason != CrashReason.LIQUID) {
                damage(player, config().crashDamage);
            }
            cooldowns.put(player.getUniqueId(), tick + config().crashCooldownTicks);
        }
        badges().onTotals(player, data);
    }

    /**
     * Ends a session.
     *
     * @param teleport place the player back on the snow where they were (false when they are already being moved)
     */
    public void end(SkiSession session, SkiSession.EndReason reason, boolean teleport) {
        if (session.ended()) {
            return;
        }
        session.markEnded();
        sessions.remove(session.player().getUniqueId());
        Player player = session.player();
        try {
            courses().onSkierEnd(session, reason);
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Error ending race", e);
        }
        long banked = session.combo().total();
        if (reason != SkiSession.EndReason.CRASH && banked > 0 && banked > session.data().bestCombo) {
            session.data().bestCombo = banked;
        }
        session.visibility().restore(tick);
        session.stopWind();
        session.allowDismount();
        SkiState s = session.state();
        Location safe = player.getLocation();
        safe.set(s.pos.x, s.pos.y + 0.05, s.pos.z);
        if (player.getVehicle() != null && player.getVehicle().equals(session.rig().anchor())) {
            player.leaveVehicle();
        }
        session.rig().remove();
        if (teleport && player.isOnline() && !player.isDead() && player.getWorld().equals(session.world())) {
            player.teleport(safe);
        }
        if (player.isOnline()) {
            player.sendActionBar(net.kyori.adventure.text.Component.empty());
        }
        PlayerData data = session.data();
        data.runs++;
        data.markDirty();
        if (player.isOnline()) {
            badges().onTotals(player, data);
            switch (reason) {
                case UNCLIP -> {
                    effects.unclip(safe);
                    messages().actionBar(player, "ski.unclipped");
                }
                case ROUGH -> {
                    effects.unclip(safe);
                    messages().send(player, "ski.rough-unclip");
                }
                default -> {
                }
            }
        }
    }

    public void end(Player player, SkiSession.EndReason reason, boolean teleport) {
        SkiSession s = sessions.get(player.getUniqueId());
        if (s != null) {
            end(s, reason, teleport);
        }
    }

    public void damage(Player player, double amount) {
        player.damage(amount, DamageSource.builder(DamageType.FALL).build());
    }

    public void shutdown() {
        for (SkiSession s : List.copyOf(sessions.values())) {
            end(s, SkiSession.EndReason.DISABLE, true);
        }
        for (Debris d : debris) {
            if (d.display.isValid()) {
                d.display.remove();
            }
        }
        debris.clear();
    }

    /** Whether a material counts as a click-in surface. */
    public boolean snowy(Material m) {
        return config().clickInBlocks.contains(m);
    }
}
