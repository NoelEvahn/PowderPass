package com.powderpass.backrooms.creature;

import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.config.BackroomsConfig;
import com.powderpass.backrooms.display.Model;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;

import java.util.concurrent.ThreadLocalRandom;

/** Implementations of every creature type. */
final class Creatures {
    private Creatures() {
    }

    static Creature create(BackroomsPlugin plugin, CreatureManager mgr, CreatureType type) {
        BackroomsConfig.CreatureSettings s = plugin.cfg().creature(type.id);
        return switch (type) {
            case SMILER -> new Smiler(plugin, mgr, s);
            case FACELING -> new Faceling(plugin, mgr);
            case HOUND -> new Hound(plugin, mgr, s);
            case DRIFTER -> new Drifter(plugin, mgr, s);
            case WATCHER -> new Watcher(plugin, mgr, s);
            case SKINWALKER -> new Skinwalker(plugin, mgr, s);
            case BACTERIA -> new Bacteria(plugin, mgr, s);
        };
    }

    private static ThreadLocalRandom rnd() {
        return ThreadLocalRandom.current();
    }

    // ===================================================================== Smiler
    /** Hangs in darkness. Freezes while watched; creeps forward when unobserved and the victim is in the dark. */
    static final class Smiler extends Creature {
        private final double damage;
        private int watchedTicks;
        private int giggleIn = 40;
        /** Animation state, eased toward targets each think. */
        private double open, reach, emerge, tilt, tiltGoal;
        /** Came with a blackout: leaves when the lights return. */
        private boolean blackoutBound;

        void bindToBlackout() {
            blackoutBound = true;
        }

        Smiler(BackroomsPlugin p, CreatureManager m, BackroomsConfig.CreatureSettings s) {
            super(p, m, CreatureType.SMILER, 10);
            this.damage = s.damage();
        }

        @Override
        protected Model model() {
            return Bodies.smiler();
        }

        @Override
        protected float[] hitboxSize() {
            return new float[]{0.7f, 0.6f};
        }

        @Override
        public void spawn(Location at, Player onlyViewer) {
            super.spawn(at, onlyViewer);
            if (hitbox != null) hitbox.teleport(new Location(world, x, y + 1.4, z));
        }

        @Override
        protected void place(double nx, double ny, double nz, float nyaw) {
            super.place(nx, ny, nz, nyaw);
            if (hitbox != null) hitbox.teleport(new Location(world, x, y + 1.4, z));
        }

        @Override
        protected void think() {
            int dt = plugin.cfg().creatureTickInterval;
            Player p = nearestPlayer(40);
            if (p == null || age > 20 * 120) {
                vanish(false);
                return;
            }
            face(p.getLocation());
            boolean watched = watchedBy(p, 28, 40);
            boolean blackout = plugin.environment().blackoutNear(p.getLocation());
            if (blackoutBound && !blackout) {
                vanish(true);
                return;
            }
            // the lights are out (client-side): to a Smiler it is dark
            int playerLight = blackout ? 0 : p.getEyeLocation().getBlock().getLightFromBlocks();
            double d = dist(p);
            animate(watched, d, dt);
            if (watched) {
                watchedTicks += dt;
                place(x, y, z, yaw);
                if (watchedTicks > 90) {
                    vanish(true);
                }
                return;
            }
            watchedTicks = Math.max(0, watchedTicks - dt);
            if ((giggleIn -= dt) <= 0) {
                giggleIn = 50 + rnd().nextInt(80);
                float vol = d < 10 ? 0.7f : 0.35f;
                world.playSound(head(), Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, SoundCategory.HOSTILE, vol, 0.5f);
            }
            if (playerLight >= 11) {
                // light keeps it at bay; it backs into the dark
                if (d < 7) {
                    Vector away = loc().toVector().subtract(p.getLocation().toVector()).setY(0).normalize().multiply(3);
                    moveToward(loc().add(away), 0.12, 0.5);
                }
                return;
            }
            if (d <= 1.4) {
                if (!targetable(p)) {
                    vanish(true);
                    return;
                }
                p.damage(damage);
                mgr.noteAttack(p, type);
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false));
                world.playSound(p.getLocation(), Sound.ENTITY_PHANTOM_BITE, SoundCategory.HOSTILE, 1f, 0.5f);
                vanish(false);
                return;
            }
            double speed = playerLight <= 4 ? 0.32 : 0.18;
            moveToward(p.getLocation(), speed, 1.2);
        }

        /**
         * Watched: the mouth closes, the arms sink back into the dark, the eyes blink slowly. Unwatched and close:
         * the grin widens and opens, more eyes slide out of the mass, the arms reach.
         */
        private void animate(boolean watched, double d, int dt) {
            if (age % 4 >= dt && !watched) return; // every other think is plenty; interpolation fills the gap
            double near = Math.max(0, Math.min(1, (10 - d) / 8));
            double k = watched ? 0.5 : 0.25;
            open += ((watched ? 0 : near) - open) * k;
            reach += ((watched ? 0 : Math.max(0, (5.5 - d) / 4)) - reach) * k;
            emerge += ((watched ? 0 : near) - emerge) * 0.2;
            if (rnd().nextDouble() < 0.06) tiltGoal = rnd().nextGaussian() * (watched ? 4 : 14);
            tilt += (tiltGoal - tilt) * 0.3;
            boolean blink = watched ? watchedTicks % 30 < dt * 2 : rnd().nextDouble() < 0.03;
            double wide = 1.0 + near * 0.25;
            body.pose(Poses.smiler(Math.min(1, open), wide, Math.min(1, reach), Math.min(1, emerge), tilt, blink, Math.sin(age * 0.05)), 5);
        }

        private void vanish(boolean stared) {
            if (stared) {
                world.playSound(head(), Sound.ENTITY_ALLAY_HURT, SoundCategory.HOSTILE, 0.5f, 0.4f);
                world.spawnParticle(Particle.SQUID_INK, head(), 8, 0.2, 0.2, 0.2, 0.01);
            }
            remove();
        }

        @Override
        protected void onHurt(Player by, double amount) {
            world.playSound(head(), Sound.ENTITY_ALLAY_HURT, SoundCategory.HOSTILE, 0.8f, 0.3f);
            remove();
        }
    }

    // ===================================================================== Faceling
    /** Stalks at a distance, peeks, and leaves when stared at. Harmless (a sighting), but it wears you down. */
    static final class Faceling extends Creature {
        private int watchedTicks;
        private int unseen;
        private boolean leaving;
        private Location leaveTo;
        private double tilt, mouth, curl;

        Faceling(BackroomsPlugin p, CreatureManager m) {
            super(p, m, CreatureType.FACELING, 1);
        }

        @Override
        protected Model model() {
            return Bodies.faceling();
        }

        @Override
        protected double headHeight() {
            return 2.45;
        }

        @Override
        protected float viewRange() {
            com.powderpass.backrooms.Level lvl = plugin.worlds().levelOf(world);
            return lvl != null && lvl.outdoor() ? 2.5f : 1.5f;
        }

        @Override
        protected float[] hitboxSize() {
            return new float[]{0.6f, 2.7f};
        }

        @Override
        protected void think() {
            int dt = plugin.cfg().creatureTickInterval;
            com.powderpass.backrooms.Level lvl = plugin.worlds().levelOf(world);
            boolean outdoor = lvl != null && lvl.outdoor();
            Player p = nearestPlayer(outdoor ? 140 : 56);
            if (p == null || age > 20 * (outdoor ? 150 : 90)) {
                remove();
                return;
            }
            if (outdoor) {
                fieldWatcher(p, dt);
                return;
            }
            boolean watched = watchedBy(p, 25, 48);
            if (watched) {
                watchedTicks += dt;
                if (watchedTicks % 20 < dt) mgr.sanityHit(p, 2.0);
            }
            if (!leaving && watchedTicks > 30) {
                leaving = true;
                // it does not like being looked at: the head drops sideways and the seam of a mouth tears open
                tilt = rnd().nextBoolean() ? 78 : -78;
                mouth = 1;
                body.pose(Poses.faceling(tilt, mouth, 1, 0), 3);
                Vector away = loc().toVector().subtract(p.getLocation().toVector()).setY(0);
                if (away.lengthSquared() < 0.01) away = new Vector(1, 0, 0);
                leaveTo = loc().add(away.normalize().multiply(14));
                world.playSound(loc(), Sound.ENTITY_WARDEN_STEP, SoundCategory.HOSTILE, 0.25f, 1.6f);
            }
            if (leaving) {
                boolean done = moveToward(leaveTo, 0.16, 1.0);
                animateWalk(Bodies.FACELING_HIP, Bodies.FACELING_SHOULDER, 0.5, true);
                mouth = Math.max(0.35, mouth - 0.02);
                body.pose(Poses.faceling(tilt, mouth, 0.6 + 0.4 * Math.sin(age * 0.9), Math.sin(age * 0.08)), dt + 1);
                if (done || (!watchedBy(p, 40, 64) && age % 20 < dt) || age > 20 * 100) remove();
                return;
            }
            double d = dist(p);
            if (d > 20) {
                moveToward(p.getLocation(), 0.11, 16);
                animateWalk(Bodies.FACELING_HIP, Bodies.FACELING_SHOULDER, 0.4, true);
            } else if (d < 12) {
                Vector away = loc().toVector().subtract(p.getLocation().toVector()).setY(0);
                if (away.lengthSquared() > 0.01) moveToward(loc().add(away.normalize().multiply(4)), 0.1, 0.5);
                animateWalk(Bodies.FACELING_HIP, Bodies.FACELING_SHOULDER, 0.35, true);
            } else {
                face(p.getLocation());
                place(x, y, z, yaw);
                animateWalk(Bodies.FACELING_HIP, Bodies.FACELING_SHOULDER, 0, false);
                idle(dt);
            }
        }

        @Override
        public double despawnRange() {
            com.powderpass.backrooms.Level lvl = plugin.worlds().levelOf(world);
            return lvl != null && lvl.outdoor() ? 160 : 0;
        }

        /**
         * In the open: a still figure far out in the field. It turns to face you, drifts closer only while
         * unobserved, and is simply gone if you stare too long or walk toward it.
         */
        private void fieldWatcher(Player p, int dt) {
            face(p.getLocation());
            double d = dist(p);
            if (watchedBy(p, 12, 140)) {
                watchedTicks += dt;
                unseen = 0;
                if (watchedTicks % 20 < dt) mgr.sanityHit(p, 1.0);
            } else {
                unseen += dt;
            }
            if (watchedTicks > 100 || d < 18) {
                world.playSound(loc(), Sound.ITEM_ELYTRA_FLYING, SoundCategory.AMBIENT, 0.3f, 0.5f);
                remove();
                return;
            }
            if (unseen > 140 && d > 26) {
                unseen = 0;
                Vector step = p.getLocation().toVector().subtract(loc().toVector()).setY(0).normalize().multiply(7);
                Location to = loc().add(step);
                to.setY(world.getHighestBlockYAt(to) + 1);
                place(to.getX(), to.getY(), to.getZ(), yaw);
            } else {
                place(x, y, z, yaw);
            }
            animateWalk(Bodies.FACELING_HIP, Bodies.FACELING_SHOULDER, 0, false);
            idle(dt);
        }

        /** Standing: slow breathing, fingers that never stop moving, a head that tilts and holds there. */
        private void idle(int dt) {
            if (age % 40 < dt) tilt = rnd().nextInt(-40, 41);
            if (age % 8 >= dt && age % 40 >= dt) return;
            if (rnd().nextDouble() < 0.4) curl = rnd().nextDouble() * 2 - 1;
            body.pose(Poses.faceling(tilt, 0, curl, Math.sin(age * 0.08)), age % 40 < dt ? 14 : 9);
        }

        @Override
        protected void onHurt(Player by, double amount) {
            world.playSound(loc(), Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 0.6f, 0.4f);
            remove();
        }
    }

    // ===================================================================== Hound
    /** Hunts by sound and sight with a telegraphed lunge. Killable. */
    static final class Hound extends Creature {
        enum State { WANDER, INVESTIGATE, HUNT, TELEGRAPH, LUNGE, RECOVER, FLEE }

        private final double damage;
        private State state = State.WANDER;
        private Location goal;
        private Player prey;
        private int stateTicks;
        private int lostTicks;
        private Vector lungeDir;
        private double headTilt;

        Hound(BackroomsPlugin p, CreatureManager m, BackroomsConfig.CreatureSettings s) {
            super(p, m, CreatureType.HOUND, s.health());
            this.damage = s.damage();
        }

        @Override
        protected Model model() {
            return Bodies.hound();
        }

        @Override
        protected float[] hitboxSize() {
            return new float[]{0.8f, 1.0f};
        }

        @Override
        protected int navHeight() {
            return 1;
        }

        @Override
        protected double headHeight() {
            return 0.8;
        }

        private void set(State s) {
            state = s;
            stateTicks = 0;
        }

        @Override
        public void hear(Location where, double loudness) {
            if (state == State.WANDER || state == State.INVESTIGATE) {
                goal = where.clone();
                if (state == State.WANDER) world.playSound(loc(), Sound.ENTITY_WOLF_GROWL, SoundCategory.HOSTILE, 0.6f, 0.6f);
                set(State.INVESTIGATE);
            }
        }

        private boolean sees(Player p) {
            double d = dist(p);
            if (d > 14) return false;
            if (p.isSneaking() && d > 5 && p.getLocation().getBlock().getLightFromBlocks() < 8) return false;
            return p.hasLineOfSight(head());
        }

        @Override
        protected void think() {
            int dt = plugin.cfg().creatureTickInterval;
            stateTicks += dt;
            if (age > 20 * 240 && state != State.HUNT) {
                remove();
                return;
            }
            Player near = nearestPlayer(32);
            if (near != null && state != State.FLEE && state != State.HUNT && state != State.TELEGRAPH && state != State.LUNGE && sees(near)) {
                prey = near;
                world.playSound(loc(), Sound.ENTITY_WOLF_GROWL, SoundCategory.HOSTILE, 1f, 0.5f);
                set(State.HUNT);
            }
            boolean moving = true;
            switch (state) {
                case WANDER -> {
                    if (goal == null || stateTicks > 160 || moveToward(goal, 0.09, 1.2)) {
                        goal = loc().add(rnd().nextInt(-10, 11), 0, rnd().nextInt(-10, 11));
                        stateTicks = 0;
                    }
                }
                case INVESTIGATE -> {
                    if (goal == null || moveToward(goal, 0.17, 1.5) || stateTicks > 200) {
                        world.playSound(loc(), Sound.ENTITY_WOLF_SHAKE, SoundCategory.HOSTILE, 0.5f, 0.6f);
                        goal = null;
                        set(State.WANDER);
                    }
                }
                case HUNT -> {
                    if (prey == null || !present(prey) || prey.getWorld() != world) {
                        set(State.WANDER);
                        break;
                    }
                    lostTicks = sees(prey) ? 0 : lostTicks + dt;
                    if (lostTicks > 160) {
                        goal = prey.getLocation();
                        set(State.INVESTIGATE);
                        break;
                    }
                    double d = dist(prey);
                    if (d < 4.2 && targetable(prey) && prey.hasLineOfSight(head())) {
                        face(prey.getLocation());
                        world.playSound(loc(), Sound.ENTITY_WOLF_GROWL, SoundCategory.HOSTILE, 1.2f, 0.4f);
                        set(State.TELEGRAPH);
                        break;
                    }
                    moveToward(prey.getLocation(), 0.36, 1.0);
                }
                case TELEGRAPH -> {
                    moving = false;
                    body.brightness("body.head.eyes", 15);
                    if (prey != null) face(prey.getLocation());
                    place(x, y, z, yaw);
                    if (stateTicks >= 22) {
                        lungeDir = prey == null ? new Vector(0, 0, 1) : prey.getLocation().toVector().subtract(loc().toVector()).setY(0).normalize();
                        world.playSound(loc(), Sound.ENTITY_WOLF_HURT, SoundCategory.HOSTILE, 1f, 0.4f);
                        body.brightness("body.head.eyes", 10);
                        set(State.LUNGE);
                    }
                }
                case LUNGE -> {
                    double step = 0.75 * dt;
                    double nx = x + lungeDir.getX() * step, nz = z + lungeDir.getZ() * step;
                    if (Nav.walkable(world, (int) Math.floor(nx), (int) Math.floor(y), (int) Math.floor(nz), 1, Nav.Medium.GROUND)) {
                        place(nx, y, nz, yaw);
                    }
                    if (prey != null && targetable(prey) && dist(prey) < 1.6) {
                        prey.damage(damage);
                        mgr.noteAttack(prey, type);
                        prey.setVelocity(lungeDir.clone().multiply(0.6).setY(0.25));
                        world.playSound(loc(), Sound.ENTITY_WOLF_GROWL, SoundCategory.HOSTILE, 1f, 1.2f);
                        set(State.RECOVER);
                    } else if (stateTicks > 10) {
                        set(State.RECOVER);
                    }
                }
                case RECOVER -> {
                    if (prey != null) {
                        Vector away = loc().toVector().subtract(prey.getLocation().toVector()).setY(0);
                        if (away.lengthSquared() > 0.01) moveToward(loc().add(away.normalize().multiply(3)), 0.12, 0.5);
                    }
                    if (stateTicks > 30) set(State.HUNT);
                }
                case FLEE -> {
                    if (goal == null || moveToward(goal, 0.4, 1.0) || stateTicks > 120) remove();
                }
            }
            if (!dead) animateLegs(moving && state != State.TELEGRAPH);
        }

        /** Gait, the rear-up before a lunge, a jaw that works, a head that scans and twitches. */
        private void animateLegs(boolean moving) {
            int interp = plugin.cfg().creatureTickInterval + 1;
            double amp = !moving ? 0 : state == State.HUNT || state == State.FLEE ? 1.0 : 0.6;
            double rear = state == State.TELEGRAPH ? Math.min(1, stateTicks / 10.0) : state == State.LUNGE ? Math.max(0, 1 - stateTicks / 4.0) : 0;
            double jaw = state == State.TELEGRAPH || state == State.LUNGE ? 1 : state == State.HUNT ? 0.35 + 0.25 * Math.sin(age * 0.8) : 0.05 + 0.05 * Math.sin(age * 0.3);
            double look = state == State.WANDER || state == State.INVESTIGATE ? Math.sin(age * 0.045) * 0.45 : 0;
            if (rnd().nextDouble() < 0.08) headTilt = rnd().nextDouble() < 0.3 ? rnd().nextGaussian() * 0.5 : 0;
            body.pose(Poses.hound(walkPhase * 0.9, amp, rear, jaw, look, headTilt), interp);
        }

        @Override
        protected void onHurt(Player by, double amount) {
            world.playSound(loc(), Sound.ENTITY_WOLF_HURT, SoundCategory.HOSTILE, 1f, 0.5f);
            world.spawnParticle(Particle.SMOKE, head(), 6, 0.2, 0.2, 0.2, 0.02);
            prey = by;
            if (health < 10 && state != State.FLEE) {
                Vector away = loc().toVector().subtract(by.getLocation().toVector()).setY(0);
                goal = loc().add(away.lengthSquared() > 0.01 ? away.normalize().multiply(16) : new Vector(16, 0, 0));
                set(State.FLEE);
            } else if (state == State.WANDER || state == State.INVESTIGATE) {
                set(State.HUNT);
            }
        }

        @Override
        protected void onDeath(Player by) {
            world.playSound(loc(), Sound.ENTITY_WOLF_DEATH, SoundCategory.HOSTILE, 1f, 0.4f);
            world.spawnParticle(Particle.SQUID_INK, loc().add(0, 0.6, 0), 20, 0.3, 0.3, 0.3, 0.02);
            if (rnd().nextDouble() < 0.35) world.dropItemNaturally(loc().add(0, 0.5, 0), plugin.items().ration());
        }
    }

    // ===================================================================== Drifter
    /** Glides through deep water; drags swimmers down after a bubbling telegraph. */
    static final class Drifter extends Creature {
        private final double damage;
        private Player held;
        private int holdTicks;
        private int nearTicks;
        private Location goal;

        Drifter(BackroomsPlugin p, CreatureManager m, BackroomsConfig.CreatureSettings s) {
            super(p, m, CreatureType.DRIFTER, s.health());
            this.damage = s.damage();
        }

        @Override
        protected Model model() {
            return Bodies.drifter();
        }

        @Override
        protected Nav.Medium medium() {
            return Nav.Medium.WATER;
        }

        @Override
        protected int navHeight() {
            return 1;
        }

        @Override
        protected float[] hitboxSize() {
            return new float[]{0.9f, 0.8f};
        }

        @Override
        protected double headHeight() {
            return 0.4;
        }

        private static boolean inWater(Player p) {
            return p.isInWater() || p.getLocation().getBlock().getType() == Material.WATER;
        }

        @Override
        protected void think() {
            int dt = plugin.cfg().creatureTickInterval;
            if (age > 20 * 180) {
                release();
                remove();
                return;
            }
            undulate();
            if (held != null) {
                holdTicks += dt;
                if (!targetable(held) || !inWater(held) || holdTicks > 70) {
                    release();
                    return;
                }
                held.setVelocity(new Vector(0, -0.28, 0));
                place(held.getLocation().getX(), held.getLocation().getY() - 0.6, held.getLocation().getZ(), yaw);
                if (holdTicks % 20 < dt) {
                    held.damage(damage);
                    mgr.noteAttack(held, type);
                }
                return;
            }
            Player p = nearestPlayer(24);
            if (p != null && inWater(p)) {
                double d = dist(p);
                if (d < 6) {
                    nearTicks += dt;
                    world.spawnParticle(Particle.BUBBLE, loc().add(0, 0.5, 0), 6, 0.3, 0.2, 0.3, 0.05);
                    if (nearTicks % 16 < dt) world.playSound(loc(), Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundCategory.HOSTILE, 1f, 0.6f);
                } else {
                    nearTicks = 0;
                }
                if (d < 1.6 && nearTicks > 30 && targetable(p)) {
                    held = p;
                    holdTicks = 0;
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, false, false));
                    world.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.HOSTILE, 0.3f, 1.6f);
                    return;
                }
                moveToward(p.getLocation(), nearTicks > 0 ? 0.16 : 0.1, 1.0);
            } else {
                if (goal == null || moveToward(goal, 0.06, 1.0) || age % 160 < dt) {
                    goal = loc().add(rnd().nextInt(-6, 7), rnd().nextInt(-1, 2), rnd().nextInt(-6, 7));
                }
                if (p == null && age > 20 * 30) remove();
            }
        }

        /** Swims with its whole length; reaches and screams when it is close to someone in the water. */
        private void undulate() {
            int dt = plugin.cfg().creatureTickInterval;
            if (age % 4 >= dt) return;
            int interp = 5;
            boolean hunting = held != null || nearTicks > 0;
            double amp = held != null ? 0.45 : hunting ? 0.35 : 0.18;
            double speed = hunting ? 0.4 : 0.22;
            body.pose(Poses.drifter(age * speed, amp, hunting ? 1 : 0, held != null ? 1 : hunting ? 0.6 : 0.15), interp);
        }

        private void release() {
            if (held != null) held.removePotionEffect(PotionEffectType.SLOWNESS);
            held = null;
            nearTicks = 0;
        }

        @Override
        protected void onHurt(Player by, double amount) {
            release();
            world.playSound(loc(), Sound.ENTITY_GUARDIAN_HURT, SoundCategory.HOSTILE, 0.8f, 0.5f);
            Vector away = loc().toVector().subtract(by.getLocation().toVector());
            goal = loc().add(away.lengthSquared() > 0.01 ? away.normalize().multiply(8) : new Vector(0, -2, 0));
            nearTicks = -60;
        }

        @Override
        public void remove() {
            release();
            super.remove();
        }
    }

    // ===================================================================== Watcher
    /**
     * A black silhouette that stands at the far end of a hall. While anyone is looking at it, it does not move at
     * all. The moment nobody is, it closes the distance fast and without a sound. Stare it down long enough and it is
     * simply gone; let it reach you and the lights go with it.
     */
    static final class Watcher extends Creature {
        private final double damage;
        private int watchedTicks;
        private int unseenTicks;
        private int tilt;

        Watcher(BackroomsPlugin p, CreatureManager m, BackroomsConfig.CreatureSettings s) {
            super(p, m, CreatureType.WATCHER, s.health());
            this.damage = s.damage();
        }

        @Override
        protected Model model() {
            return Bodies.watcher();
        }

        @Override
        protected double headHeight() {
            return 1.95;
        }

        @Override
        protected float[] hitboxSize() {
            return new float[]{0.6f, 2.1f};
        }

        @Override
        protected void think() {
            int dt = plugin.cfg().creatureTickInterval;
            Player p = nearestPlayer(64);
            if (p == null || age > 20 * 150) {
                remove();
                return;
            }
            if (anyoneWatching(42, 64)) {
                // frozen solid; not even the head turns, and the smear settles into a clean silhouette
                if (unseenTicks > 0) body.pose(Poses.watcherHaze(0, true), 1);
                unseenTicks = 0;
                watchedTicks += dt;
                if (watchedTicks % 20 < dt) mgr.sanityHit(p, 1.5);
                if (watchedTicks > 20 * 7) {
                    world.playSound(head(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, SoundCategory.HOSTILE, 0.15f, 0.5f);
                    remove();
                }
                return;
            }
            watchedTicks = Math.max(0, watchedTicks - dt);
            unseenTicks += dt;
            double d = dist(p);
            if (d <= 1.3) {
                if (targetable(p)) {
                    p.damage(damage);
                    mgr.noteAttack(p, type);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0, false, false));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, false, false));
                    mgr.flickerAround(p.getLocation(), 10);
                }
                world.playSound(p.getLocation(), Sound.ENTITY_WARDEN_NEARBY_CLOSEST, SoundCategory.HOSTILE, 0.8f, 0.6f);
                remove();
                return;
            }
            if (d < 14 && unseenTicks % 30 < dt) {
                world.playSound(head(), Sound.AMBIENT_CAVE, SoundCategory.HOSTILE, 0.25f, 0.6f);
            }
            moveToward(p.getLocation(), 0.42, 1.0);
            animateWalk(Bodies.WATCHER_HIP, Bodies.WATCHER_SHOULDER, 0.25, true);
            // moving, its edges smear like a bad frame of film
            body.pose(Poses.watcherHaze(rnd().nextLong(), false), 1);
            if (++tilt % 20 == 0) {
                body.pose("head", new Matrix4f().translate(0, (float) Bodies.WATCHER_NECK, 0).rotateZ((float) Math.toRadians(rnd().nextInt(-25, 26)))
                        .translate(0, (float) -Bodies.WATCHER_NECK, 0), 1);
            }
        }

        @Override
        protected void onHurt(Player by, double amount) {
            world.playSound(head(), Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 0.5f, 0.3f);
            world.spawnParticle(Particle.SQUID_INK, head(), 12, 0.2, 0.6, 0.2, 0.01);
            remove();
        }
    }
}
