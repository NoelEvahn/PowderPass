package com.powderpass.backrooms.creature;

import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.config.BackroomsConfig;
import com.powderpass.backrooms.display.Model;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The skinwalker. It is not a monster of its own: it is a black tentacled organism that takes a person and walks
 * around in them.
 * <ol>
 *   <li><b>Borrowed shape</b>: until it finds someone, it wears the body of another creature from the level and
 *   stalks like one (see {@link Stalker}): it keeps its distance, circles, shuffles, slips out of view. When a
 *   player who has company stands still for about ten seconds, or walks into it unseen, it takes them. A lone
 *   player is not taken (there is nobody to fool): it strikes them instead and goes on stalking.</li>
 *   <li><b>Transformation</b> (about 5.5 s, see {@link Possession}): the player freezes, something cracks, their
 *   body jerks, their back tears open and black tentacles force their way out, reach down, plant on the ground and
 *   slowly lift them into the air.</li>
 *   <li><b>Possessed</b>: the player stays in their own body, in survival, visible with their own skin, carried by
 *   the organism on six tentacle legs while smaller tentacles crawl over them ({@link TentacleRig}). The AI moves
 *   them; they cannot. It stalks the other players the same way, and anyone who stands still for about ten
 *   seconds is approached and punched with the victim's own fist or lashed with a tentacle. It repeats what the
 *   victim said. Hitting the victim hurts the organism, not them: kill it (true-form health) and the victim is
 *   freed; if it lasts {@code skinwalker.possession-seconds}, or kills someone, the victim dies with it.</li>
 * </ol>
 */
final class Skinwalker extends Creature {
    enum Phase { MIMIC, TRANSFORMING, POSSESSED }

    private final BackroomsConfig.CreatureSettings settings;
    Phase phase = Phase.MIMIC;
    private CreatureType disguise = CreatureType.FACELING;
    Possession possession;
    private Player target;
    private Stalker stalker;
    private int possessedTicks, lonelyTicks, kills, wanderIn;

    Skinwalker(BackroomsPlugin plugin, CreatureManager mgr, BackroomsConfig.CreatureSettings s) {
        super(plugin, mgr, CreatureType.SKINWALKER, 10_000);
        this.settings = s;
    }

    @Override
    public void spawn(Location at, Player onlyViewer) {
        Level level = plugin.worlds().levelOf(at.getWorld());
        List<CreatureType> options = switch (level == null ? Level.LEVEL_0 : level) {
            // it only ever wears what belongs to the level
            case LEVEL_0 -> List.of(CreatureType.BACTERIA, CreatureType.WATCHER);
            case POOLROOMS -> List.of(CreatureType.WATCHER);
            case LEVEL_1 -> List.of(CreatureType.HOUND, CreatureType.WATCHER, CreatureType.SMILER);
            case LEVEL_4 -> List.of(CreatureType.HOUND, CreatureType.FACELING);
            case PLAINS -> List.of(CreatureType.WATCHER, CreatureType.SMILER);
        };
        disguise = options.get(ThreadLocalRandom.current().nextInt(options.size()));
        stalker = new Stalker(7, 13, 1.8, patienceTicks(), id.getLeastSignificantBits());
        super.spawn(at, onlyViewer);
    }

    private int patienceTicks() {
        return plugin.cfg().skinwalkerPatienceSeconds * 20 + ThreadLocalRandom.current().nextInt(30) - 15;
    }

    @Override
    public String state() {
        return "skinwalker:" + switch (phase) {
            case MIMIC -> "mimic:" + disguise.id;
            case TRANSFORMING -> "transforming";
            case POSSESSED -> "possessed";
        };
    }

    /** What it currently looks like, for status and tests. */
    CreatureType disguise() {
        return disguise;
    }

    /** The stalking mind, for tests. */
    Stalker stalker() {
        return stalker;
    }

    @Override
    protected Model model() {
        return switch (disguise) {
            case SMILER -> Bodies.smiler();
            case WATCHER -> Bodies.watcher();
            case HOUND -> Bodies.hound();
            case BACTERIA -> Bodies.bacteria();
            default -> Bodies.faceling();
        };
    }

    @Override
    protected float[] hitboxSize() {
        return switch (phase) {
            case MIMIC -> new float[]{0.7f, 2.0f};
            // riding the victim: its origin is the top of their head, the box covers the tentacle arch above
            default -> new float[]{1.8f, 1.6f};
        };
    }

    @Override
    protected double headHeight() {
        return 1.8;
    }

    @Override
    public double despawnRange() {
        return phase == Phase.MIMIC ? 0 : 256;
    }

    private Possessions possessions() {
        return mgr.possessions();
    }

    @Override
    protected void think() {
        int dt = plugin.cfg().creatureTickInterval;
        switch (phase) {
            case MIMIC -> mimic(dt);
            case TRANSFORMING, POSSESSED -> possessed(dt);
        }
    }

    // ------------------------------------------------------------------ 1. borrowed shape

    private void mimic(int dt) {
        if (target == null || !present(target) || target.getWorld() != world || dist(target) > 60 || possessions().isVictim(target)) target = prey(56);
        if (target == null || age > 20 * 240) {
            remove();
            return;
        }
        boolean watched = watchedBy(target, 35, 48);
        stalker.think(dt, x, y, z, target, watched);
        double d = dist(target);
        // watched, it moves like what it is wearing; unwatched it is a little too quick
        double speed = Math.max(0, stalker.speed) * (watched ? 0.8 : 1.15);
        boolean moving = false;
        if (speed > 0.01) {
            Location goal = new Location(world, stalker.gx, stalker.gy, stalker.gz);
            double before = x * x + z * z;
            moveToward(goal, speed, 0.6);
            moving = Math.abs(before - (x * x + z * z)) > 1e-6;
            if (!moving && path.isEmpty()) stalker.blocked();
        }
        if (stalker.faceTarget) {
            face(target.getLocation());
            place(x, y, z, yaw);
        }
        animateDisguise(moving);
        // the only tell: now and then, something cracks when you are not looking
        if (!watched && d < 14 && ThreadLocalRandom.current().nextDouble() < 0.02) {
            sfx(loc(), "skinwalker.crackle", Sound.BLOCK_BONE_BLOCK_BREAK, 0.35f, 0.5f);
        }
        boolean reach = stalker.strikeNow || (d <= 1.6 && !watched);
        if (reach && targetable(target) && !possessions().isVictim(target)) {
            if (hasCompany(target)) {
                if (!wear(target)) stalker.struck();
            } else {
                lunge(target);
                stalker.struck();
            }
        }
    }

    /** A lone player is struck, not taken: the borrowed shape lunges, then melts back into the dark. */
    private void lunge(Player p) {
        face(p.getLocation());
        place(x, y, z, yaw);
        double dmg = (settings.damage() > 0 ? settings.damage() : 9) * 0.55;
        p.damage(dmg);
        Vector kb = p.getLocation().toVector().subtract(loc().toVector()).setY(0);
        if (kb.lengthSquared() > 0.01) p.setVelocity(kb.normalize().multiply(0.45).setY(0.25));
        mgr.noteAttack(p, type);
        sfx(loc(), "skinwalker.screech", Sound.ENTITY_SPIDER_HURT, 0.9f, 0.4f);
        sfx(loc(), "skinwalker.crackle", Sound.BLOCK_BONE_BLOCK_BREAK, 0.8f, 0.5f);
    }

    private boolean hasCompany(Player victim) {
        double radius = plugin.cfg().skinwalkerGroupRadius;
        for (Player o : world.getPlayers()) {
            if (o == victim || !targetable(o) || possessions().isVictim(o)) continue;
            if (o.getLocation().distanceSquared(victim.getLocation()) <= radius * radius) return true;
        }
        return false;
    }

    /**
     * Moves like whatever it is wearing, almost. The one wrong thing: now and then, while nobody is looking, the
     * borrowed head turns all the way round.
     */
    private void animateDisguise(boolean moving) {
        if (body == null) return;
        int interp = plugin.cfg().creatureTickInterval + 1;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        switch (disguise) {
            case FACELING -> {
                animateWalk(Bodies.FACELING_HIP, Bodies.FACELING_SHOULDER, moving ? 0.3 : 0, moving);
                boolean wrong = moving && r.nextDouble() < 0.02;
                body.pose("head", wrong ? new Matrix4f().rotateY((float) Math.PI) : new Matrix4f(), wrong ? 3 : 8);
            }
            case WATCHER -> {
                animateWalk(Bodies.WATCHER_HIP, Bodies.WATCHER_SHOULDER, moving ? 0.25 : 0, moving);
                body.pose(Poses.watcherHaze(r.nextLong(), !moving), interp);
            }
            case HOUND -> body.pose(Poses.hound(walkPhase * 0.9, moving ? 0.6 : 0, 0, 0.05 + 0.05 * Math.sin(age * 0.2), Math.sin(age * 0.04) * 0.3, 0), interp);
            case SMILER -> body.pose(Poses.smiler(0.1, 1.0, 0, 0, Math.sin(age * 0.03) * 6, false, Math.sin(age * 0.06)), interp);
            case BACTERIA -> body.pose(Poses.bacteria(walkPhase * 0.75, moving ? 0.5 : 0, r.nextDouble() < 0.3 ? r.nextLong() | 1 : 0, 0, 0), interp);
            default -> {
            }
        }
    }

    /**
     * Take this player now: the borrowed shape dissolves into them and the transformation begins. Used when it
     * reaches someone with company and by {@code /backrooms creature skinwalker} (with or without company).
     */
    boolean wear(Player victim) {
        if (!targetable(victim) && victim.getGameMode() != GameMode.CREATIVE) return false;
        Location from = loc();
        Possession pos = possessions().begin(this, victim);
        if (pos == null) return false;
        possession = pos;
        phase = Phase.TRANSFORMING;
        health = settings.health() > 0 ? settings.health() : 80;
        // the disguise collapses into black smoke that pours into them
        world.spawnParticle(Particle.SQUID_INK, from.clone().add(0, 1, 0), 40, 0.3, 0.8, 0.3, 0.04);
        reshape(null);
        Location l = victim.getLocation();
        x = l.getX();
        y = l.getY();
        z = l.getZ();
        yaw = l.getYaw();
        makeHitbox();
        pos.mountHitbox(hitbox);
        possessions().bind(pos, hitbox);
        stalker = new Stalker(5, 10, 3.2, patienceTicks(), id.getMostSignificantBits());
        target = null;
        return true;
    }

    // ------------------------------------------------------------------ 2-3. transformation and possession

    private void possessed(int dt) {
        if (possession == null || !possession.active()) {
            // the victim is gone (released, disconnected, died): the organism dies with the body
            vanish();
            return;
        }
        Location root = possession.root();
        x = root.getX();
        y = root.getY();
        z = root.getZ();
        yaw = (float) possession.heading();
        if (possession.stage == Possession.Stage.TRANSFORMING) return;
        phase = Phase.POSSESSED;
        possessedTicks += dt;
        if (possessedTicks > plugin.cfg().skinwalkerPossessionSeconds * 20) {
            // it has had them too long
            possession.release(true);
            vanish();
            return;
        }
        Player victim = possession.victim();
        if (target == null || !targetable(target) || target.getWorld() != world || target.getLocation().distance(root) > 56 || possessions().isVictim(target)) {
            target = victim == null ? null : nearestPrey(root, 48, victim);
        }
        if (target == null) {
            wander(dt, root);
            return;
        }
        lonelyTicks = 0;
        boolean watched = watchedFrom(target, root);
        stalker.think(dt, root.getX(), root.getY(), root.getZ(), target, watched);
        possession.goal(stalker.gx, stalker.gy, stalker.gz, Math.max(0, stalker.speed), stalker.faceTarget ? target : null);
        if (possession.blocked() && stalker.mode != Stalker.Mode.ATTACK && ThreadLocalRandom.current().nextDouble() < 0.2) stalker.blocked();
        if (stalker.strikeNow && !possession.attacking()) {
            double dmg = settings.damage() > 0 ? settings.damage() : 9;
            double d = target.getLocation().distance(root);
            // close enough for a fist, or lash out with the tentacle
            if (d < 2.4 && ThreadLocalRandom.current().nextBoolean()) possession.punch(target, dmg * 0.5);
            else possession.strike(target, dmg);
            stalker.struck();
        }
    }

    /** Nobody to hunt: it drifts about with its passenger, slowly. */
    private void wander(int dt, Location root) {
        lonelyTicks += dt;
        if ((wanderIn -= dt) <= 0) {
            wanderIn = 60 + ThreadLocalRandom.current().nextInt(80);
            double a = ThreadLocalRandom.current().nextDouble(Math.PI * 2), r = 4 + ThreadLocalRandom.current().nextDouble(8);
            possession.goal(root.getX() + Math.cos(a) * r, root.getY(), root.getZ() + Math.sin(a) * r, 0.08, null);
        }
    }

    /** A blow from the organism landed on someone. */
    void landed(Player t, double damage, Location from) {
        if (!targetable(t)) return;
        t.damage(damage);
        t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1, false, false));
        Vector kb = t.getLocation().toVector().subtract(from.toVector()).setY(0);
        if (kb.lengthSquared() > 0.01) t.setVelocity(kb.normalize().multiply(0.55).setY(0.3));
        mgr.noteAttack(t, type);
        sfx(t.getLocation(), "skinwalker.whip", Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 0.6f);
        if (t.isDead() || t.getHealth() <= 0) {
            kills++;
            target = null;
            if (possession != null && possession.active()) {
                // it fed; the worn body is spent
                possession.release(true);
                vanish();
            }
        }
    }

    private boolean watchedFrom(Player p, Location root) {
        Location eye = p.getEyeLocation();
        Vector to = root.toVector().add(new Vector(0, 1.6, 0)).subtract(eye.toVector());
        double d = to.length();
        if (d > 48 || d < 0.01) return d < 0.01;
        return Math.toDegrees(eye.getDirection().angle(to)) <= 35 && p.hasLineOfSight(root.clone().add(0, 1.6, 0));
    }

    private Player prey(double max) {
        Player best = null;
        double bd = max * max;
        for (Player p : world.getPlayers()) {
            if (!present(p) || possessions().isVictim(p)) continue;
            double d = p.getLocation().distanceSquared(loc());
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    private Player nearestPrey(Location at, double max, Player victim) {
        Player best = null;
        double bd = max * max;
        for (Player p : world.getPlayers()) {
            if (p == victim || !present(p) || possessions().isVictim(p)) continue;
            double d = p.getLocation().distanceSquared(at);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    private void vanish() {
        world.spawnParticle(Particle.SQUID_INK, loc().add(0, 1, 0), 30, 0.6, 0.5, 0.6, 0.02);
        sfx(loc(), "skinwalker.crackle", Sound.BLOCK_BONE_BLOCK_BREAK, 0.8f, 0.4f);
        remove();
    }

    // ------------------------------------------------------------------ damage

    @Override
    protected void onHurt(Player by, double amount) {
        if (phase == Phase.MIMIC) {
            // the borrowed shape tears; it flees into the dark and comes back later
            sfx(loc(), "skinwalker.screech", Sound.ENTITY_SPIDER_HURT, 0.8f, 0.5f);
            world.spawnParticle(Particle.SQUID_INK, loc().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.03);
            if (amount >= 4 || ThreadLocalRandom.current().nextDouble() < 0.35) {
                remove();
                return;
            }
            stalker.blocked();
            return;
        }
        sfx(loc(), "skinwalker.screech", Sound.ENTITY_SPIDER_HURT, 0.9f, 0.5f);
        world.spawnParticle(Particle.SQUID_INK, loc().add(0, 2, 0), 12, 0.4, 0.4, 0.4, 0.05);
        target = by;
    }

    @Override
    protected void onDeath(Player by) {
        sfx(loc(), "skinwalker.screech", Sound.ENTITY_WARDEN_DEATH, 1.2f, 0.7f);
        sfx(loc(), "skinwalker.tear", Sound.ENTITY_SLIME_SQUISH, 1.0f, 0.5f);
        world.spawnParticle(Particle.SQUID_INK, loc().add(0, 1.5, 0), 60, 0.8, 0.8, 0.8, 0.06);
        // killing it frees the one it was carrying
        if (possession != null && possession.active()) possession.release(false);
    }

    @Override
    public void remove() {
        // whatever happens to the creature (cleared by an admin, plugin disabled, chunk unloaded), the victim is let go
        if (possession != null && possession.active()) possession.release(false);
        super.remove();
    }

    @Override
    public int entityCount() {
        return super.entityCount() + (possession != null && possession.active() ? possession.rig.size() : 0);
    }

    // ------------------------------------------------------------------ sound

    /**
     * Custom pack sound for players who have it, a vanilla stand-in for everyone else. Monster sounds carry far
     * (48 blocks) and play in the HOSTILE category so they are never drowned by the ambience.
     */
    void sfx(Location at, String custom, Sound fallback, float volume, float pitch) {
        boolean has = plugin.audio().index().has(custom);
        for (Player p : at.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(at) > 48 * 48) continue;
            if (has && plugin.audio().custom(p)) p.playSound(at, "backrooms:" + custom, SoundCategory.HOSTILE, volume, pitch < 0.8f ? 1f : pitch);
            else p.playSound(at, fallback, SoundCategory.HOSTILE, volume, pitch);
        }
    }

    BackroomsPlugin plugin() {
        return plugin;
    }

    int kills() {
        return kills;
    }
}
