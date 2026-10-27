package dev.powderpass.volleyball.ball;

import dev.powderpass.volleyball.PowderPassPlugin;
import dev.powderpass.volleyball.config.Settings;
import dev.powderpass.volleyball.court.BukkitCollisionWorld;
import dev.powderpass.volleyball.court.Court;
import dev.powderpass.volleyball.court.NetRenderer;
import dev.powderpass.volleyball.hit.HitType;
import dev.powderpass.volleyball.math.Vec3;
import dev.powderpass.volleyball.physics.Aabb;
import dev.powderpass.volleyball.physics.BallBody;
import dev.powderpass.volleyball.physics.BallSimulator;
import dev.powderpass.volleyball.physics.CourtNet;
import dev.powderpass.volleyball.physics.NetContact;
import dev.powderpass.volleyball.physics.SimulationListener;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.joml.Quaternionf;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A live volleyball: server-authoritative physics rendered as a block-display voxel sphere.
 */
public final class Volleyball implements SimulationListener {

    /** Ticks of position history kept for lag compensation. */
    public static final int HISTORY = 24;

    private final UUID id = UUID.randomUUID();
    private final PowderPassPlugin plugin;
    private final World world;
    private final BallBody body;
    private final BallSimulator simulator = new BallSimulator();
    private final BukkitCollisionWorld collisionWorld;
    private BallContext context;
    private final BallRenderer renderer = new BallRenderer();
    private final Quaternionf rotation = new Quaternionf();
    private final Vec3 prevPos = new Vec3();

    private final double[] histX = new double[HISTORY];
    private final double[] histY = new double[HISTORY];
    private final double[] histZ = new double[HISTORY];
    private final long[] histTick = new long[HISTORY];
    private int histHead;
    private int histSize;

    private long tick;
    private long lastHitTick = -1000;
    private long lastFloorTick = -1000;
    private long lastMoveTick;
    private UUID lastToucher;
    private HitType lastHitType;
    private UUID owner;
    private UUID tossedBy;
    private boolean tossPending;
    private boolean removed;
    /** Held in a player's hands: no physics, positioned by its owner every tick. */
    private boolean held;
    private boolean grown;
    private int soundCooldown;
    private Court cachedCourt;

    public Volleyball(PowderPassPlugin plugin, World world, Vec3 pos, BallContext context) {
        this.plugin = plugin;
        this.world = world;
        this.context = context;
        this.body = new BallBody(ThreadLocalRandom.current().nextLong());
        this.body.pos.set(pos);
        this.prevPos.set(pos);
        this.collisionWorld = new BukkitCollisionWorld(world);
        spawnDisplay();
        recordHistory();
    }

    private void spawnDisplay() {
        Location at = new Location(world, body.pos.x, body.pos.y, body.pos.z);
        renderer.spawn(world, at, plugin.ballModel(), plugin.settings().ball);
        grown = false;
    }

    public boolean held() {
        return held;
    }

    /** Puts the ball in someone's hands (kinematic) or releases it to the physics. */
    public void setHeld(boolean held) {
        this.held = held;
        // other clients smooth players over 3 ticks: match it so the ball stays in the hands
        renderer.setTeleportDuration(held ? 3 : plugin.settings().ball.teleportInterpolation());
        if (held) {
            body.vel.zero();
            body.spin.zero();
            body.resting = false;
        }
        lastMoveTick = tick;
    }

    /** Moves a held ball (and spins it a little, as if turned in the hands). */
    public void holdAt(Vec3 pos, double spinRad) {
        prevPos.set(body.pos);
        body.pos.set(pos);
        if (spinRad != 0) {
            rotation.premul(new Quaternionf().rotateY((float) spinRad)).normalize();
        }
    }

    public UUID id() {
        return id;
    }

    public World world() {
        return world;
    }

    public BallBody body() {
        return body;
    }

    public Vec3 position() {
        return body.pos;
    }

    public Vec3 previousPosition() {
        return prevPos;
    }

    public BallContext context() {
        return context;
    }

    public void setContext(BallContext context) {
        this.context = context;
    }

    public long tick() {
        return tick;
    }

    public Location location() {
        return new Location(world, body.pos.x, body.pos.y, body.pos.z);
    }

    public boolean removed() {
        return removed;
    }

    public UUID lastToucher() {
        return lastToucher;
    }

    public HitType lastHitType() {
        return lastHitType;
    }

    public long lastHitTick() {
        return lastHitTick;
    }

    public long lastFloorTick() {
        return lastFloorTick;
    }

    public UUID owner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public UUID tossedBy() {
        return tossPending ? tossedBy : null;
    }

    public long ticksSinceMove() {
        return tick - lastMoveTick;
    }

    /** Marks this ball as a service toss by {@code player}; the next touch by them is a serve. */
    public void markToss(UUID player) {
        this.tossedBy = player;
        this.tossPending = true;
    }

    /** Court used for net collisions (context court, or the court the ball is on during free play). */
    public Court court() {
        return cachedCourt;
    }

    /** Applies a touch: new velocity and spin. */
    public void launch(Vec3 velocity, Vec3 spin, boolean knuckle, UUID toucher, HitType type) {
        body.launch(velocity, spin, knuckle);
        lastHitTick = tick;
        lastMoveTick = tick;
        lastToucher = toucher;
        lastHitType = type;
        if (type != null) {
            tossPending = false;
        }
    }

    /** Moves the ball without changing velocity (used to place it at the contact point). */
    public void teleport(Vec3 pos) {
        body.pos.set(pos);
        prevPos.set(pos);
        body.resting = false;
    }

    public void doTick() {
        if (removed) {
            return;
        }
        tick++;
        if (!renderer.valid()) {
            spawnDisplay();
        }
        cachedCourt = context.court(this);
        if (!grown) {
            // pop in: displays spawn tiny and grow, instead of appearing out of nowhere
            renderer.resize(1f, 3);
            grown = true;
        }
        if (held) {
            recordHistory();
            renderer.moveTo(location());
            if (tick % 2 == 0) {
                renderer.rotate(rotation);
            }
            context.onTick(this);
            return;
        }
        prevPos.set(body.pos);
        boolean wasResting = body.resting;
        CourtNet net = cachedCourt != null && cachedCourt.world() == world ? cachedCourt.net() : null;
        simulator.tick(body, plugin.settings().physics, collisionWorld, net, this, BallSimulator.TICK);
        if (!body.pos.isFinite()) {
            plugin.getLogger().warning("Ball state became invalid, resetting it");
            body.pos.set(prevPos);
            body.vel.zero();
            body.spin.zero();
        }
        if (!wasResting && !body.resting) {
            lastMoveTick = tick;
        }
        if (soundCooldown > 0) {
            soundCooldown--;
        }
        recordHistory();
        render();
        context.onTick(this);
    }

    private void render() {
        Settings s = plugin.settings();
        Location current = renderer.location();
        if (!body.resting || current == null || current.distanceSquared(location()) > 1e-6) {
            renderer.moveTo(location());
        }
        // spin the model with the angular velocity
        double w = body.spin.length();
        if (w > 0.05) {
            double angle = Math.min(w * BallSimulator.TICK, 1.1);
            Quaternionf dq = new Quaternionf().fromAxisAngleRad(
                    (float) (body.spin.x / w), (float) (body.spin.y / w), (float) (body.spin.z / w), (float) angle);
            rotation.premul(dq).normalize();
            // the ball is several displays: refresh the pose every other tick and let the client interpolate
            if (tick % 2 == 0) {
                renderer.rotate(rotation);
            }
        }
        // speed trail
        double speed = body.speed();
        if (s.ball.trail() && speed > s.trailMinSpeed && !body.onGround) {
            Color c = speed > 22 ? Color.fromRGB(255, 170, 60) : speed > 15 ? Color.fromRGB(255, 240, 200) : Color.WHITE;
            int n = speed > 18 ? 3 : 2;
            for (int i = 0; i < n; i++) {
                double t = (i + 0.5) / n;
                Location l = new Location(world, prevPos.x + (body.pos.x - prevPos.x) * t,
                        prevPos.y + (body.pos.y - prevPos.y) * t, prevPos.z + (body.pos.z - prevPos.z) * t);
                plugin.fx().dust(l, c, speed > 22 ? 0.9f : 0.6f, 1, 0.02);
            }
        }
    }

    private void recordHistory() {
        histHead = (histHead + 1) % HISTORY;
        histX[histHead] = body.pos.x;
        histY[histHead] = body.pos.y;
        histZ[histHead] = body.pos.z;
        histTick[histHead] = tick;
        histSize = Math.min(HISTORY, histSize + 1);
    }

    /**
     * Position of the ball {@code ticksAgo} ticks ago (fractional ticks interpolate). Returns false when the
     * history does not reach back that far.
     */
    public boolean historicalPosition(double ticksAgo, Vec3 out) {
        if (ticksAgo <= 0) {
            out.set(body.pos);
            return true;
        }
        int whole = (int) Math.floor(ticksAgo);
        double frac = ticksAgo - whole;
        if (whole + 1 >= histSize) {
            return false;
        }
        int i0 = Math.floorMod(histHead - whole, HISTORY);
        int i1 = Math.floorMod(histHead - whole - 1, HISTORY);
        out.set(histX[i0] + (histX[i1] - histX[i0]) * frac,
                histY[i0] + (histY[i1] - histY[i0]) * frac,
                histZ[i0] + (histZ[i1] - histZ[i0]) * frac);
        return true;
    }

    // ---------------------------------------------------------------- simulation events

    @Override
    public void onBlockContact(Vec3 position, Vec3 normal, double impactSpeed, Aabb box) {
        boolean floor = normal.y > 0.7;
        if (floor && impactSpeed > 0.3) {
            lastFloorTick = tick;
            tossPending = false;
        }
        if (impactSpeed > 0.8 && soundCooldown == 0) {
            Location at = new Location(world, position.x, position.y, position.z);
            Block b = world.getBlockAt((int) Math.floor(position.x - normal.x * 0.01),
                    (int) Math.floor(position.y - normal.y * 0.01), (int) Math.floor(position.z - normal.z * 0.01));
            Material m = b.getType();
            boolean sand = m == Material.SAND || m == Material.RED_SAND || m == Material.SUSPICIOUS_SAND;
            float vol = (float) Math.min(1.0, 0.25 + impactSpeed / 12.0);
            plugin.fx().sound(at, floor ? (sand ? Settings.SoundId.SAND : Settings.SoundId.FLOOR) : Settings.SoundId.WALL, vol);
            if (!m.isAir()) {
                plugin.fx().blockDust(at, b.getBlockData(), (int) Math.min(18, 3 + impactSpeed * (sand ? 2.5 : 0.8)));
            }
            soundCooldown = 2;
        }
        context.onBlockContact(this, position, normal, impactSpeed, box);
    }

    @Override
    public void onNetContact(NetContact contact, Vec3 localPoint, double impactSpeed) {
        Court c = cachedCourt;
        if (c != null && impactSpeed > 0.2) {
            NetRenderer net = plugin.courts().netRenderer(c);
            if (net != null && (contact == NetContact.MESH || contact == NetContact.TAPE)) {
                double dir = -Math.signum(c.frame().toU(prevPos.x, prevPos.z));
                net.impact(localPoint.z, localPoint.y, dir * impactSpeed, contact == NetContact.TAPE);
            }
            if (soundCooldown == 0) {
                Settings.SoundId sid = switch (contact) {
                    case MESH -> Settings.SoundId.NET;
                    case TAPE -> Settings.SoundId.TAPE;
                    case ANTENNA -> Settings.SoundId.ANTENNA;
                    case POST -> Settings.SoundId.WALL;
                };
                plugin.fx().sound(location(), sid, (float) Math.min(1, 0.3 + impactSpeed / 10));
                soundCooldown = 3;
            }
        }
        context.onNetContact(this, contact, localPoint, impactSpeed);
    }

    @Override
    public void onNetPlaneCrossed(int fromSide, double localW, double localY) {
        context.onNetCross(this, fromSide, localW, localY);
    }

    public void remove() {
        if (removed) {
            return;
        }
        removed = true;
        renderer.remove();
    }

    /** Shrinks the ball away over a few ticks, then removes it. */
    public void removeAnimated() {
        if (removed) {
            return;
        }
        removed = true;
        renderer.resize(0.05f, 4);
        plugin.getServer().getScheduler().runTaskLater(plugin, renderer::remove, 5);
    }

    public int partCount() {
        return renderer.partCount();
    }
}
