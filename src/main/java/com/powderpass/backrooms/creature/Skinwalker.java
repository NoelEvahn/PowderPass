package com.powderpass.backrooms.creature;

import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.config.BackroomsConfig;
import com.powderpass.backrooms.display.Model;
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
 * The skinwalker. Three shapes:
 * <ol>
 *   <li><b>Borrowed shape</b>: it wears the body of another creature from the level (a Faceling, a Smiler, a
 *   Watcher, a Hound) and behaves almost like one, except that it keeps coming closer while nobody is looking.
 *   Strike it and the disguise splits open.</li>
 *   <li><b>Worn player</b>: when it reaches a player who has company, it takes them. The player is locked inside
 *   their own body (spectating it, unable to move, type, run commands or speak on voice chat) while the
 *   skinwalker walks among their friends wearing their skin: following the group, copying sneaks, jumps and arm
 *   swings, punching back playfully, repeating things the player said in chat and, with Simple Voice Chat, in
 *   their own recorded voice. See {@link Possession}.</li>
 *   <li><b>True form</b>: after a while, or when the group gets suspicious (too many punches), or when the
 *   player it reached was alone, its back cracks open into a spider-like thing with the player's body caught
 *   underneath, and it hunts. Kill it and the player is freed; if it lasts long enough, or feeds, the player
 *   dies with it.</li>
 * </ol>
 */
final class Skinwalker extends Creature {
    enum Phase { MIMIC, WEARING, TRUE }

    private final BackroomsConfig.CreatureSettings settings;
    Phase phase = Phase.MIMIC;
    private CreatureType disguise = CreatureType.FACELING;
    Possession possession;
    private Player target;
    private int trueTicks, idleTicks, attackCooldown, scuttleIn, chitterIn, kills;
    private boolean screamed;

    Skinwalker(BackroomsPlugin plugin, CreatureManager mgr, BackroomsConfig.CreatureSettings s) {
        super(plugin, mgr, CreatureType.SKINWALKER, 10_000);
        this.settings = s;
    }

    @Override
    public void spawn(Location at, Player onlyViewer) {
        Level level = plugin.worlds().levelOf(at.getWorld());
        List<CreatureType> options = switch (level == null ? Level.LEVEL_0 : level) {
            case LEVEL_0 -> List.of(CreatureType.FACELING, CreatureType.SMILER, CreatureType.WATCHER);
            case LEVEL_1 -> List.of(CreatureType.HOUND, CreatureType.FACELING);
            case LEVEL_4 -> List.of(CreatureType.WATCHER, CreatureType.FACELING);
            default -> List.of(CreatureType.FACELING);
        };
        disguise = options.get(ThreadLocalRandom.current().nextInt(options.size()));
        super.spawn(at, onlyViewer);
    }

    @Override
    public String state() {
        return "skinwalker:" + phase.name().toLowerCase(java.util.Locale.ROOT) + (phase == Phase.MIMIC ? ":" + disguise.id : "");
    }

    /** What it currently looks like, for status and tests. */
    CreatureType disguise() {
        return disguise;
    }

    @Override
    protected Model model() {
        return switch (disguise) {
            case SMILER -> Bodies.smiler();
            case WATCHER -> Bodies.watcher();
            case HOUND -> Bodies.hound();
            default -> Bodies.faceling();
        };
    }

    @Override
    protected float[] hitboxSize() {
        return switch (phase) {
            case WEARING -> null; // the mannequin is what gets punched
            case TRUE -> new float[]{2.2f, 1.7f};
            default -> new float[]{0.7f, 2.0f};
        };
    }

    @Override
    protected int navHeight() {
        return phase == Phase.TRUE ? 1 : 2;
    }

    @Override
    protected double headHeight() {
        return phase == Phase.TRUE ? 1.2 : 1.8;
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
        attackCooldown = Math.max(0, attackCooldown - dt);
        switch (phase) {
            case MIMIC -> mimic(dt);
            case WEARING -> wearing(dt);
            case TRUE -> trueForm(dt);
        }
    }

    // ------------------------------------------------------------------ 1. borrowed shape

    private void mimic(int dt) {
        Player p = nearestPlayer(56);
        if (p == null || age > 20 * 240) {
            remove();
            return;
        }
        face(p.getLocation());
        boolean watched = anyoneWatching(35, 48);
        double d = dist(p);
        if (!watched) {
            moveToward(p.getLocation(), 0.16, 1.2);
            if (disguise == CreatureType.FACELING) animateWalk(Bodies.FACELING_HIP, Bodies.FACELING_SHOULDER, 0.3, true);
            else if (disguise == CreatureType.WATCHER) animateWalk(Bodies.WATCHER_HIP, Bodies.WATCHER_SHOULDER, 0.25, true);
            // the only tell: now and then, something cracks when you are not looking
            if (d < 12 && ThreadLocalRandom.current().nextDouble() < 0.015) sfx(loc(), "skinwalker.crackle", Sound.BLOCK_BONE_BLOCK_BREAK, 0.25f, 0.5f);
        } else {
            place(x, y, z, yaw);
            if (disguise == CreatureType.FACELING) animateWalk(Bodies.FACELING_HIP, Bodies.FACELING_SHOULDER, 0, false);
        }
        if (d <= 2.0 && targetable(p) && !possessions().isVictim(p)) take(p);
    }

    /** It reached a player: wear them if they have company, otherwise there is nobody to fool. */
    private void take(Player victim) {
        double radius = plugin.cfg().skinwalkerGroupRadius;
        boolean company = false;
        for (Player o : world.getPlayers()) {
            if (o == victim || !targetable(o) || possessions().isVictim(o)) continue;
            if (o.getLocation().distanceSquared(victim.getLocation()) <= radius * radius) company = true;
        }
        if (!company) {
            reveal(null);
            target = victim;
            return;
        }
        Possession pos = possessions().begin(this, victim);
        if (pos == null) {
            reveal(null);
            return;
        }
        possession = pos;
        phase = Phase.WEARING;
        reshape(null);
        makeHitbox();
        Location l = victim.getLocation();
        place(l.getX(), l.getY(), l.getZ(), l.getYaw());
    }

    // ------------------------------------------------------------------ 2. worn player

    private void wearing(int dt) {
        if (possession == null || !possession.active()) {
            reveal(null);
            return;
        }
        Location l = possession.tick(dt);
        if (l != null) place(l.getX(), l.getY(), l.getZ(), l.getYaw());
        if (possession.wantsReveal()) reveal(possession);
    }

    // ------------------------------------------------------------------ 3. true form

    /** Crack open into the true form, carrying the worn body if there is one. */
    void reveal(Possession carried) {
        if (phase == Phase.TRUE) return;
        phase = Phase.TRUE;
        health = settings.health() > 0 ? settings.health() : 80;
        Location l = loc();
        world.spawnParticle(Particle.BLOCK, l.clone().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.1, org.bukkit.Material.BONE_BLOCK.createBlockData());
        sfx(l, "skinwalker.crackle", Sound.BLOCK_BONE_BLOCK_BREAK, 1.2f, 0.6f);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> sfx(loc(), "skinwalker.crackle", Sound.ENTITY_SKELETON_HURT, 1.0f, 0.5f), 12L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> sfx(loc(), "skinwalker.screech", Sound.ENTITY_GHAST_SCREAM, 1.0f, 0.5f), 26L);
        reshape(Bodies.skinwalker());
        makeHitbox();
        if (carried != null) carried.trueForm();
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(l) < 24 * 24) p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false));
        }
        screamed = true;
        idleTicks = 0;
    }

    private Player prey() {
        Player best = null;
        double bd = 48 * 48;
        for (Player p : world.getPlayers()) {
            if (!targetable(p) || possessions().isVictim(p)) continue;
            double d = p.getLocation().distanceSquared(loc());
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    private void trueForm(int dt) {
        trueTicks += dt;
        if (possession != null && possession.active() && trueTicks > plugin.cfg().skinwalkerTrueSeconds * 20) {
            // it has had them too long
            possession.release(true);
            vanish();
            return;
        }
        if (target == null || !targetable(target) || target.getWorld() != world || dist(target) > 56) target = prey();
        if (chitterIn-- <= 0) {
            chitterIn = 40;
            sfx(loc(), "skinwalker.chitter", Sound.ENTITY_SPIDER_AMBIENT, 0.7f, 0.6f);
        }
        if (target == null) {
            if ((idleTicks += dt) > 20 * 30) {
                if (possession != null) possession.release(true);
                vanish();
            }
            legs(false);
            carry();
            return;
        }
        idleTicks = 0;
        if (!screamed) {
            screamed = true;
            sfx(loc(), "skinwalker.screech", Sound.ENTITY_GHAST_SCREAM, 0.9f, 0.5f);
        }
        double d = dist(target);
        if (d <= 2.3 && attackCooldown == 0) {
            face(target.getLocation());
            place(x, y, z, yaw);
            target.damage(settings.damage() > 0 ? settings.damage() : 9);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, false));
            Vector kb = target.getLocation().toVector().subtract(loc().toVector()).setY(0);
            if (kb.lengthSquared() > 0.01) target.setVelocity(kb.normalize().multiply(0.5).setY(0.3));
            mgr.noteAttack(target, type);
            sfx(loc(), "skinwalker.screech", Sound.ENTITY_SPIDER_HURT, 0.6f, 0.4f);
            attackCooldown = 30;
            if (target.isDead() || target.getHealth() <= 0) {
                kills++;
                if (possession != null && possession.active()) possession.release(true); // it fed; the worn body is spent
                target = null;
            }
        } else {
            moveToward(target.getLocation(), 0.3, 1.8);
            if (scuttleIn-- <= 0) {
                scuttleIn = 3;
                sfx(loc(), "skinwalker.scuttle", Sound.ENTITY_SPIDER_STEP, 0.6f, 1.4f);
            }
        }
        legs(true);
        carry();
    }

    private void carry() {
        if (possession != null && possession.active()) possession.carry(loc(), yaw);
    }

    private void legs(boolean moving) {
        if (body == null) return;
        int interp = plugin.cfg().creatureTickInterval + 1;
        for (int i = 0; i < 8; i++) {
            double phaseOff = (i % 2 == 0 ? 0 : Math.PI) + (i < 4 ? 0 : Math.PI / 2);
            double a = moving ? Math.sin(walkPhase * 1.4 + phaseOff) * 0.35 : Math.sin(age * 0.05 + i) * 0.04;
            int side = i < 4 ? -1 : 1;
            double hz = 0.45 - (i % 4) * 0.42;
            body.pose("leg" + i, new Matrix4f().translate((float) (side * 0.42), 1.2f, (float) hz).rotateY((float) a)
                    .rotateZ((float) (moving ? Math.max(0, Math.cos(walkPhase * 1.4 + phaseOff)) * 0.25 * side : 0))
                    .translate((float) (-side * 0.42), -1.2f, (float) -hz), interp);
        }
        double jaw = moving ? 0.15 + 0.15 * Math.sin(age * 0.6) : 0.05;
        body.pose("jaw", new Matrix4f().translate(0, 1.0f, 0.65f).rotateX((float) jaw).translate(0, -1.0f, -0.65f), interp);
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
            // the borrowed shape splits open
            reveal(null);
            target = by;
            return;
        }
        if (phase == Phase.TRUE) {
            sfx(loc(), "skinwalker.screech", Sound.ENTITY_SPIDER_HURT, 0.7f, 0.5f);
            world.spawnParticle(Particle.BLOCK, loc().add(0, 1, 0), 12, 0.4, 0.4, 0.4, 0.1, org.bukkit.Material.BONE_BLOCK.createBlockData());
            target = by;
        }
    }

    @Override
    protected void onDeath(Player by) {
        sfx(loc(), "skinwalker.screech", Sound.ENTITY_GHAST_DEATH, 1.0f, 0.5f);
        sfx(loc(), "skinwalker.crackle", Sound.BLOCK_BONE_BLOCK_BREAK, 1.0f, 0.5f);
        world.spawnParticle(Particle.BLOCK, loc().add(0, 1, 0), 60, 0.8, 0.6, 0.8, 0.1, org.bukkit.Material.BONE_BLOCK.createBlockData());
        // killing it frees the one it was wearing
        if (possession != null && possession.active()) possession.release(false);
    }

    @Override
    public void remove() {
        // whatever happens to the creature (cleared by an admin, plugin disabled, chunk unloaded), the victim is let go
        if (possession != null && possession.active()) possession.release(false);
        super.remove();
    }

    // ------------------------------------------------------------------ sound

    /** Custom pack sound for players who have it, a vanilla stand-in for everyone else. */
    void sfx(Location at, String custom, Sound fallback, float volume, float pitch) {
        boolean has = plugin.audio().index().has(custom);
        for (Player p : at.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(at) > 40 * 40) continue;
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
