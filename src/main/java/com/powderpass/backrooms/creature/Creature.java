package com.powderpass.backrooms.creature;

import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.display.Keys;
import com.powderpass.backrooms.display.Model;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;

import java.util.List;
import java.util.UUID;

/**
 * Base creature: a display {@link Body}, an optional {@link Interaction} hitbox for honest hit detection, a
 * position, and a behaviour implemented in {@link #think()}. All Bukkit access happens on the main thread.
 */
public abstract class Creature {
    protected final BackroomsPlugin plugin;
    protected final CreatureManager mgr;
    public final CreatureType type;
    public final UUID id = UUID.randomUUID();
    protected World world;
    protected double x, y, z;
    protected float yaw;
    protected double health;
    protected int age;
    protected boolean dead;
    protected Body body;
    protected Interaction hitbox;
    /** If set, only this player can see the creature (hallucinations). */
    protected Player viewer;
    protected List<int[]> path = List.of();
    protected int pathIndex;
    protected int repathIn;
    protected int walkPhase;

    protected Creature(BackroomsPlugin plugin, CreatureManager mgr, CreatureType type, double health) {
        this.plugin = plugin;
        this.mgr = mgr;
        this.type = type;
        this.health = health;
    }

    protected abstract Model model();

    protected abstract void think();

    /** Hitbox size {width, height}; null for un-hittable creatures. */
    protected float[] hitboxSize() {
        return new float[]{0.7f, 1.9f};
    }

    protected int navHeight() {
        return 2;
    }

    protected Nav.Medium medium() {
        return Nav.Medium.GROUND;
    }

    public void spawn(Location at, Player onlyViewer) {
        this.world = at.getWorld();
        this.x = at.getX();
        this.y = at.getY();
        this.z = at.getZ();
        this.yaw = at.getYaw();
        this.viewer = onlyViewer;
        body = new Body(model());
        int interval = plugin.cfg().creatureTickInterval;
        body.spawn(plugin, loc(), viewRange(), id.toString(), onlyViewer, interval + 1);
        if (onlyViewer == null) makeHitbox();
        onSpawn();
    }

    protected void onSpawn() {
    }

    /** (Re)create the Interaction hitbox from {@link #hitboxSize()} and register it with the manager. */
    protected void makeHitbox() {
        UUID old = hitboxId();
        if (hitbox != null && hitbox.isValid()) hitbox.remove();
        hitbox = null;
        float[] hb = hitboxSize();
        if (hb != null) {
            hitbox = world.spawn(loc(), Interaction.class, e -> {
                e.setPersistent(false);
                e.setInteractionWidth(hb[0]);
                e.setInteractionHeight(hb[1]);
                e.setResponsive(false);
                e.getPersistentDataContainer().set(Keys.CREATURE, PersistentDataType.STRING, id.toString());
            });
        }
        mgr.rebindHitbox(this, old);
    }

    /** Swap the display body for another model (the skinwalker changes shape). */
    protected void reshape(Model model) {
        if (body != null) body.remove();
        body = null;
        if (model == null) return;
        body = new Body(model);
        body.spawn(plugin, loc(), viewRange(), id.toString(), viewer, plugin.cfg().creatureTickInterval + 1);
    }

    /** Display view range (1.0 = 64 blocks). */
    protected float viewRange() {
        return 1.5f;
    }

    public Location loc() {
        return new Location(world, x, y, z, yaw, 0);
    }

    public World world() {
        return world;
    }

    public final void tick() {
        if (dead) return;
        age += plugin.cfg().creatureTickInterval;
        if ((body != null && !body.valid()) || (hitbox != null && !hitbox.isValid()) || !world.isChunkLoaded((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4)) {
            remove();
            return;
        }
        if (viewer != null && (!viewer.isOnline() || viewer.getWorld() != world)) {
            remove();
            return;
        }
        think();
    }

    // ------------------------------------------------------------------ movement helpers

    protected void place(double nx, double ny, double nz, float nyaw) {
        x = nx;
        y = ny;
        z = nz;
        yaw = nyaw;
        Location l = loc();
        if (body != null) body.move(l);
        if (hitbox != null) hitbox.teleport(new Location(world, x, y, z));
    }

    protected void face(Location target) {
        yaw = yawTo(target.getX(), target.getZ());
    }

    protected float yawTo(double tx, double tz) {
        return (float) Math.toDegrees(Math.atan2(-(tx - x), tz - z));
    }

    /** Follow a nav path toward a target; returns true when within reach. */
    protected boolean moveToward(Location target, double speed, double reach) {
        double dx = target.getX() - x, dz = target.getZ() - z;
        if (dx * dx + dz * dz <= reach * reach) {
            return true;
        }
        if (--repathIn <= 0 || pathIndex >= path.size()) {
            path = Nav.path(world, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z),
                    target.getBlockX(), target.getBlockY(), target.getBlockZ(), navHeight(), medium(), 900);
            pathIndex = 0;
            repathIn = 12;
            if (path.isEmpty()) return false;
        }
        int[] wp = path.get(pathIndex);
        double wx = wp[0] + 0.5, wy = wp[1], wz = wp[2] + 0.5;
        double ddx = wx - x, ddz = wz - z, ddy = wy - y;
        double d = Math.sqrt(ddx * ddx + ddz * ddz);
        double step = speed * plugin.cfg().creatureTickInterval;
        float ny = yawTo(wx, wz);
        if (d <= step) {
            pathIndex++;
            place(wx, wy, wz, ny);
        } else {
            place(x + ddx / d * step, y + Math.signum(ddy) * Math.min(Math.abs(ddy), step), z + ddz / d * step, ny);
        }
        walkPhase++;
        return false;
    }

    /** Walk cycle for named limbs (legs/arms swing around their hip/shoulder). */
    protected void animateWalk(double hipY, double shoulderY, double amp, boolean moving) {
        if (body == null) return;
        double a = moving ? Math.sin(walkPhase * 0.6) * amp : 0;
        int interp = plugin.cfg().creatureTickInterval + 1;
        body.pose("leg_l", swing(hipY, a), interp);
        body.pose("leg_r", swing(hipY, -a), interp);
        body.pose("arm_l", swing(shoulderY, -a * 0.8), interp);
        body.pose("arm_r", swing(shoulderY, a * 0.8), interp);
    }

    protected static Matrix4f swing(double pivotY, double radians) {
        return new Matrix4f().translate(0, (float) pivotY, 0).rotateX((float) radians).translate(0, (float) -pivotY, 0);
    }

    // ------------------------------------------------------------------ perception helpers

    protected Location head() {
        return new Location(world, x, y + headHeight(), z);
    }

    protected double headHeight() {
        return 1.7;
    }

    /** Can be hurt by creatures (survival / adventure). */
    protected static boolean targetable(Player p) {
        return present(p) && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE);
    }

    /**
     * Counts as someone being there: creatures notice, stalk and react to anyone but spectators. Creative players
     * are never hurt, but creatures no longer vanish the instant they spawn next to one.
     */
    protected static boolean present(Player p) {
        return p.isValid() && !p.isDead() && p.getGameMode() != GameMode.SPECTATOR;
    }

    protected Player nearestPlayer(double max) {
        Player best = null;
        double bd = max * max;
        for (Player p : world.getPlayers()) {
            if (!present(p)) continue;
            if (viewer != null && p != viewer) continue;
            double d = p.getLocation().distanceSquared(loc());
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    protected double dist(Player p) {
        return p.getLocation().distance(loc());
    }

    /** Is the player looking at us (within a cone) with clear line of sight? */
    protected boolean watchedBy(Player p, double coneDeg, double maxDist) {
        Location eye = p.getEyeLocation();
        Location h = head();
        Vector to = h.toVector().subtract(eye.toVector());
        double d = to.length();
        if (d > maxDist || d < 0.01) return d < 0.01;
        double angle = Math.toDegrees(eye.getDirection().angle(to));
        return angle <= coneDeg && p.hasLineOfSight(h);
    }

    protected boolean anyoneWatching(double coneDeg, double maxDist) {
        for (Player p : world.getPlayers()) {
            if (present(p) && (viewer == null || p == viewer) && watchedBy(p, coneDeg, maxDist)) return true;
        }
        return false;
    }

    protected int blockLight(Location l) {
        return l.getBlock().getLightFromBlocks();
    }

    // ------------------------------------------------------------------ damage & removal

    /** Called when a player strikes the hitbox. */
    public void hurt(Player by, double amount) {
        if (dead) return;
        health -= amount;
        onHurt(by, amount);
        if (health <= 0) {
            onDeath(by);
            remove();
        }
    }

    protected void onHurt(Player by, double amount) {
    }

    protected void onDeath(Player by) {
    }

    public void remove() {
        if (dead) return;
        dead = true;
        if (body != null) body.remove();
        if (hitbox != null && hitbox.isValid()) hitbox.remove();
        mgr.unregister(this);
    }

    public boolean dead() {
        return dead;
    }

    public UUID hitboxId() {
        return hitbox == null ? null : hitbox.getUniqueId();
    }

    public int entityCount() {
        return (body == null ? 0 : body.size()) + (hitbox == null ? 0 : 1);
    }

    /** Minimum distance before this creature despawns (0 = use the configured distance). */
    public double despawnRange() {
        return 0;
    }

    /** Short behaviour state for status output and tests. */
    public String state() {
        return type.id;
    }

    /** Harmless, private creatures do not count toward spawn caps. */
    public boolean hallucination() {
        return viewer != null;
    }

    /** A sound was heard at a location (hunters override). */
    public void hear(Location where, double loudness) {
    }
}
