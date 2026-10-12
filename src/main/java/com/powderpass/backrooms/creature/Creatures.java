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
            int playerLight = p.getEyeLocation().getBlock().getLightFromBlocks();
            double d = dist(p);
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

        Faceling(BackroomsPlugin p, CreatureManager m) {
            super(p, m, CreatureType.FACELING, 1);
        }

        @Override
        protected Model model() {
            return Bodies.faceling();
        }

        @Override
        protected double headHeight() {
            return 2.15;
        }

        @Override
        protected float[] hitboxSize() {
            return new float[]{0.6f, 2.4f};
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
                Vector away = loc().toVector().subtract(p.getLocation().toVector()).setY(0);
                if (away.lengthSquared() < 0.01) away = new Vector(1, 0, 0);
                leaveTo = loc().add(away.normalize().multiply(14));
                world.playSound(loc(), Sound.ENTITY_WARDEN_STEP, SoundCategory.HOSTILE, 0.25f, 1.6f);
            }
            if (leaving) {
                boolean done = moveToward(leaveTo, 0.16, 1.0);
                animateWalk(1.25, 1.95, 0.5, true);
                if (done || (!watchedBy(p, 40, 64) && age % 20 < dt) || age > 20 * 100) remove();
                return;
            }
            double d = dist(p);
            if (d > 20) {
                moveToward(p.getLocation(), 0.11, 16);
                animateWalk(1.25, 1.95, 0.4, true);
            } else if (d < 12) {
                Vector away = loc().toVector().subtract(p.getLocation().toVector()).setY(0);
                if (away.lengthSquared() > 0.01) moveToward(loc().add(away.normalize().multiply(4)), 0.1, 0.5);
                animateWalk(1.25, 1.95, 0.35, true);
            } else {
                face(p.getLocation());
                place(x, y, z, yaw);
                animateWalk(1.25, 1.95, 0, false);
                if (age % 40 < dt) body.pose("head", new Matrix4f().translate(0, 2.18f, 0).rotateZ((float) Math.toRadians(rnd().nextInt(-35, 35))).translate(0, -2.18f, 0), 10);
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
            animateWalk(1.25, 1.95, 0, false);
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
                    if (prey == null || !targetable(prey) || prey.getWorld() != world) {
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
                    if (d < 4.2 && prey.hasLineOfSight(head())) {
                        face(prey.getLocation());
                        world.playSound(loc(), Sound.ENTITY_WOLF_GROWL, SoundCategory.HOSTILE, 1.2f, 0.4f);
                        set(State.TELEGRAPH);
                        break;
                    }
                    moveToward(prey.getLocation(), 0.36, 1.0);
                }
                case TELEGRAPH -> {
                    moving = false;
                    body.pose("head", new Matrix4f().translate(0, -0.12f, 0.05f), 6);
                    body.brightness("eyes", 15);
                    if (prey != null) face(prey.getLocation());
                    place(x, y, z, yaw);
                    if (stateTicks >= 22) {
                        lungeDir = prey == null ? new Vector(0, 0, 1) : prey.getLocation().toVector().subtract(loc().toVector()).setY(0).normalize();
                        world.playSound(loc(), Sound.ENTITY_WOLF_HURT, SoundCategory.HOSTILE, 1f, 0.4f);
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
                    body.pose("head", new Matrix4f(), 4);
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

        private void animateLegs(boolean moving) {
            double a = moving ? Math.sin(walkPhase * 0.9) * (state == State.HUNT ? 0.7 : 0.4) : 0;
            int interp = plugin.cfg().creatureTickInterval + 1;
            body.pose("leg_fl", swingZ(0.5, 0.3, a), interp);
            body.pose("leg_br", swingZ(0.5, -0.45, a), interp);
            body.pose("leg_fr", swingZ(0.5, 0.3, -a), interp);
            body.pose("leg_bl", swingZ(0.5, -0.45, -a), interp);
            double jaw = state == State.TELEGRAPH || state == State.LUNGE ? 0.5 : 0.05 * Math.sin(age * 0.3);
            body.pose("jaw", new Matrix4f().translate(0, 0.6f, 0.48f).rotateX((float) jaw).translate(0, -0.6f, -0.48f), interp);
        }

        private static Matrix4f swingZ(double pivotY, double pivotZ, double rad) {
            return new Matrix4f().translate(0, (float) pivotY, (float) pivotZ).rotateX((float) rad).translate(0, (float) -pivotY, (float) -pivotZ);
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
                if (d < 1.6 && nearTicks > 30) {
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

        private void undulate() {
            int interp = plugin.cfg().creatureTickInterval + 1;
            for (int i = 1; i < 4; i++) {
                double a = Math.sin(age * 0.25 - i * 0.9) * 0.25 * i;
                body.pose("seg" + i, new Matrix4f().translate(0, 0, (float) (-i * 0.55 + 0.55)).rotateY((float) a).translate(0, 0, (float) (i * 0.55 - 0.55)), interp);
            }
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
}
