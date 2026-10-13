package com.powderpass.backrooms.creature;

import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.config.BackroomsConfig;
import com.powderpass.backrooms.display.Model;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The Bacteria ("the Lifeform"): Level 0's hunter. Tall, thin, black, wound in cable-like strands, moving in glitching
 * jerks. It hunts by sound and movement, not by sight:
 * <ul>
 *   <li>It roams, and stops dead to listen when it hears something (sprinting is loud, walking less, sneaking barely).
 *   It then goes to look where the sound came from.</li>
 *   <li>Anyone moving near it, or making noise near where it is searching, is hunted: it screams and sprints, faster
 *   than a player can run.</li>
 *   <li>The counterplay is the found-footage one: stand completely still and silent. Then it loses you. If it is right
 *   next to you it leans over you, breathing, for a long few seconds, then wanders off, unless you move.</li>
 * </ul>
 * Now and then, roaming, it imitates a human voice from where it is. It can be hurt but not easily killed; hurt
 * badly, it flees and is gone.
 */
final class Bacteria extends Creature {
    enum State { ROAM, LISTEN, SEARCH, HUNT, LOOM, FLEE }

    private final double damage;
    State state = State.ROAM;
    private int stateTicks;
    private Location goal, heard;
    private int heardAt = -1000;
    private Player prey;
    private Location preyLast;
    private final java.util.Map<java.util.UUID, Location> lastPos = new java.util.HashMap<>();
    private int stillTicks, attackCooldown, screechCooldown, mimicIn = 400;
    private double tilt;

    Bacteria(BackroomsPlugin p, CreatureManager m, BackroomsConfig.CreatureSettings s) {
        super(p, m, CreatureType.BACTERIA, s.health());
        this.damage = s.damage();
    }

    @Override
    protected Model model() {
        return Bodies.bacteria();
    }

    @Override
    protected float[] hitboxSize() {
        return new float[]{0.7f, 3.2f};
    }

    @Override
    protected int navHeight() {
        return 3;
    }

    @Override
    protected double headHeight() {
        return 3.0;
    }

    @Override
    public String state() {
        return "bacteria:" + state.name().toLowerCase(java.util.Locale.ROOT);
    }

    private void set(State s) {
        state = s;
        stateTicks = 0;
    }

    @Override
    public void hear(Location where, double loudness) {
        if (state == State.FLEE || where.getWorld() != world) return;
        heard = where.clone();
        heardAt = age;
        if (state == State.ROAM) set(State.LISTEN);
    }

    /** Moved since the last look (turning the head does not count). */
    private boolean moved(Player p) {
        Location now = p.getLocation();
        Location last = lastPos.put(p.getUniqueId(), now.clone());
        if (p == prey) preyLast = now.clone();
        return last != null && last.getWorld() == now.getWorld() && last.distanceSquared(now) > 0.0064;
    }

    @Override
    protected void think() {
        int dt = plugin.cfg().creatureTickInterval;
        stateTicks += dt;
        attackCooldown = Math.max(0, attackCooldown - dt);
        screechCooldown = Math.max(0, screechCooldown - dt);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (age > 20 * 300 && state == State.ROAM) {
            remove();
            return;
        }
        boolean moving = false;
        double amp = 0, lean = 0;
        switch (state) {
            case ROAM -> {
                if (goal == null || stateTicks > 200 || moveToward(goal, 0.11, 1.5)) {
                    goal = loc().add(r.nextInt(-20, 21), 0, r.nextInt(-20, 21));
                    stateTicks = 0;
                }
                moving = r.nextDouble() > 0.08; // it stops for no reason, then carries on
                amp = moving ? 0.5 : 0;
                // anyone moving close by is felt through the floor
                for (Player p : world.getPlayers()) {
                    if (targetable(p) && dist(p) < 7 && (moved(p) || p.isSprinting())) {
                        hunt(p);
                        break;
                    }
                }
                if ((mimicIn -= dt) <= 0) {
                    mimicIn = 500 + r.nextInt(900);
                    mimic();
                }
            }
            case LISTEN -> {
                if (heard != null) face(heard);
                place(x, y, z, yaw);
                tilt = (stateTicks / 4 % 2 == 0 ? 0.35 : -0.25);
                if (stateTicks > 16) {
                    goal = heard;
                    set(State.SEARCH);
                }
            }
            case SEARCH -> {
                moving = true;
                amp = 0.7;
                if (goal == null || moveToward(goal, 0.22, 1.5) || stateTicks > 240) {
                    // arrived: anyone moving or noisy nearby is found
                    Player found = null;
                    for (Player p : world.getPlayers()) {
                        if (!targetable(p) || dist(p) > 9) continue;
                        boolean noisy = heard != null && age - heardAt < 40 && heard.distanceSquared(p.getLocation()) < 16;
                        if (moved(p) || noisy) found = p;
                    }
                    if (found != null) hunt(found);
                    else if (stateTicks > 60 || goal == null) set(State.ROAM);
                    moving = false;
                    amp = 0;
                }
            }
            case HUNT -> {
                if (prey == null || !targetable(prey) || prey.getWorld() != world || dist(prey) > 48) {
                    set(State.ROAM);
                    break;
                }
                boolean seen = moved(prey) || prey.isSprinting() || (heard != null && age - heardAt < 20 && heard.distanceSquared(prey.getLocation()) < 25);
                stillTicks = seen ? 0 : stillTicks + dt;
                double d = dist(prey);
                if (stillTicks > 50) {
                    // they went completely still: it has lost them
                    if (d < 3.2) set(State.LOOM);
                    else {
                        goal = prey.getLocation();
                        set(State.SEARCH);
                    }
                    break;
                }
                if (d <= 1.7 && attackCooldown == 0) {
                    face(prey.getLocation());
                    place(x, y, z, yaw);
                    prey.damage(damage > 0 ? damage : 12);
                    mgr.noteAttack(prey, type);
                    Vector kb = prey.getLocation().toVector().subtract(loc().toVector()).setY(0);
                    if (kb.lengthSquared() > 0.01) prey.setVelocity(kb.normalize().multiply(0.7).setY(0.35));
                    prey.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1, false, false));
                    sound(loc(), "bacteria.screech", Sound.ENTITY_ENDERMAN_SCREAM, 1f, 0.5f, 48);
                    attackCooldown = 24;
                } else {
                    moveToward(stillTicks > 0 ? preyLast : prey.getLocation(), 0.34, 1.2);
                    moving = true;
                    amp = 1;
                    if (age % 6 < dt) sound(loc(), "bacteria.skitter", Sound.ENTITY_SPIDER_STEP, 0.8f, 0.6f, 32);
                }
            }
            case LOOM -> {
                // right over them, listening; breathing on them; leaves if they hold still
                if (prey == null || !targetable(prey)) {
                    set(State.ROAM);
                    break;
                }
                face(prey.getLocation());
                place(x, y, z, yaw);
                lean = Math.min(1, stateTicks / 20.0);
                if (stateTicks % 40 < dt) sound(loc().add(0, 2.6, 0), "bacteria.breath", Sound.ENTITY_WARDEN_SNIFF, 0.9f, 0.6f, 16);
                if (moved(prey) || prey.isSprinting()) {
                    hunt(prey);
                } else if (stateTicks > 90 + r.nextInt(40)) {
                    goal = loc().add(r.nextInt(-24, 25), 0, r.nextInt(-24, 25));
                    prey = null;
                    set(State.ROAM);
                }
            }
            case FLEE -> {
                moving = true;
                amp = 1;
                if (goal == null || moveToward(goal, 0.4, 1.0) || stateTicks > 100) {
                    world.spawnParticle(Particle.SQUID_INK, loc().add(0, 1.6, 0), 30, 0.2, 0.9, 0.2, 0.02);
                    remove();
                    return;
                }
            }
        }
        if (dead) return;
        // it never moves cleanly: every frame some joints are a little wrong
        long glitch = r.nextDouble() < (state == State.HUNT ? 0.8 : 0.35) ? r.nextLong() | 1 : 0;
        if (r.nextDouble() < 0.1) tilt = r.nextGaussian() * 0.3;
        body.pose(Poses.bacteria(walkPhase * 0.75, moving ? amp : 0, glitch, lean, tilt), dt + 1);
    }

    private void hunt(Player p) {
        prey = p;
        preyLast = p.getLocation().clone();
        stillTicks = 0;
        if (state != State.HUNT && screechCooldown == 0) {
            sound(loc().add(0, 2.6, 0), "bacteria.screech", Sound.ENTITY_ENDERMAN_SCREAM, 1.2f, 0.5f, 64);
            screechCooldown = 160;
        }
        set(State.HUNT);
    }

    /** It imitates people: a human line from where it stands, for someone close enough to hear it. */
    private void mimic() {
        Player p = nearestPlayer(32);
        if (p == null || dist(p) < 8) return;
        sound(loc().add(0, 1.6, 0), "voice.phrase", Sound.ENTITY_VILLAGER_AMBIENT, 0.6f, 0.7f, 32);
    }

    @Override
    protected void onHurt(Player by, double amount) {
        sound(loc().add(0, 2.6, 0), "bacteria.screech", Sound.ENTITY_ENDERMAN_HURT, 1f, 0.5f, 48);
        world.spawnParticle(Particle.SQUID_INK, loc().add(0, 1.8, 0), 10, 0.2, 0.6, 0.2, 0.01);
        if (health < 30 && state != State.FLEE) {
            Vector away = loc().toVector().subtract(by.getLocation().toVector()).setY(0);
            goal = loc().add(away.lengthSquared() > 0.01 ? away.normalize().multiply(24) : new Vector(24, 0, 0));
            set(State.FLEE);
        } else if (state != State.FLEE) {
            hunt(by);
        }
    }

    @Override
    protected void onDeath(Player by) {
        sound(loc(), "bacteria.screech", Sound.ENTITY_ENDERMAN_DEATH, 1f, 0.4f, 48);
        world.spawnParticle(Particle.SQUID_INK, loc().add(0, 1.6, 0), 40, 0.3, 1.0, 0.3, 0.02);
    }
}
