package dev.powderpass.volleyball.hit;

import dev.powderpass.volleyball.PowderPassPlugin;
import dev.powderpass.volleyball.ball.Volleyball;
import dev.powderpass.volleyball.config.AimProfile;
import dev.powderpass.volleyball.config.Settings;
import dev.powderpass.volleyball.court.Court;
import dev.powderpass.volleyball.items.CustomItems;
import dev.powderpass.volleyball.items.ItemType;
import dev.powderpass.volleyball.math.CourtFrame;
import dev.powderpass.volleyball.math.Vec3;
import dev.powderpass.volleyball.physics.TrajectorySolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Turns clicks into volleyball touches.
 * <p>
 * Minecraft only tells us that a player clicked, never how their arms move, so every touch is judged on the
 * server: a click arms an intent for a few ticks (input buffering), the ball path - rewound by the player's
 * ping (lag compensation) - is compared with the ideal contact point of the chosen technique, and the distance
 * becomes a contact quality that drives power and accuracy.
 */
public final class HitController {

    private static final double UP_TOUCH_DISTANCE = 5.0;

    private final PowderPassPlugin plugin;
    private final Map<UUID, PlayerState> states = new HashMap<>();
    private TrajectorySolver solver;
    private long tick;

    public HitController(PowderPassPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        solver = new TrajectorySolver(plugin.settings().physics);
        // warm the JIT so the first touch of the day does not stall a tick
        Vec3 start = new Vec3(0, 2, 0);
        for (int i = 0; i < 40; i++) {
            solver.solveArc(start, new Vec3(4 + i % 3, 0, 1), 0, 2.5, new Vec3());
            solver.solveDriven(start, new Vec3(9, 0, i % 4), 0, 20, TrajectorySolver.SpinFactory.topspin(30));
        }
    }

    public TrajectorySolver solver() {
        return solver;
    }

    private PlayerState state(Player p) {
        return states.computeIfAbsent(p.getUniqueId(), k -> {
            PlayerState s = new PlayerState();
            Location l = p.getLocation();
            s.lastGround.set(l.getX(), l.getY(), l.getZ());
            return s;
        });
    }

    public void forget(UUID id) {
        states.remove(id);
    }

    public boolean toggleReticle(Player p) {
        PlayerState s = state(p);
        s.reticle = !s.reticle;
        return s.reticle;
    }

    // ------------------------------------------------------------------------------------------------ input

    /** Left click. */
    public void primary(Player p) {
        PlayerState s = state(p);
        if (s.lastRightClickTick == tick) {
            return; // the swing belongs to a right click
        }
        if (tick - s.lastHitTick < plugin.settings().controls.hitCooldownTicks()) {
            return;
        }
        boolean air = !p.isOnGround();
        s.primaryUntil = tick + plugin.settings().controls.inputBufferTicks();
        s.primarySneak = p.isSneaking();
        s.primaryAirborne = air;
        s.secondaryUntil = -1;
        s.clickTick = tick;
        s.clickNearBall = touchableNear(p, 3.5);
        // dive: commit to a lunge when digging from the ground
        if (s.primarySneak && !air && tick - s.lastDiveTick > 20 && s.clickNearBall && isPlaying(p)) {
            s.lastDiveTick = tick;
            Vector dir = p.getLocation().getDirection().setY(0);
            if (dir.lengthSquared() > 1e-6) {
                dir.normalize().multiply(plugin.settings().controls.diveLunge());
                p.setVelocity(p.getVelocity().add(dir).setY(0.18));
            }
        }
        tryResolve(p, s);
    }

    /** Right click. */
    public void secondary(Player p) {
        PlayerState s = state(p);
        s.lastRightClickTick = tick;
        if (tick - s.lastHitTick < plugin.settings().controls.hitCooldownTicks()) {
            return;
        }
        boolean air = !p.isOnGround();
        if (tryStartBlock(p, s)) {
            return;
        }
        s.secondaryUntil = tick + plugin.settings().controls.inputBufferTicks();
        s.secondarySneak = p.isSneaking();
        s.secondaryAirborne = air;
        s.primaryUntil = -1;
        s.clickTick = tick;
        s.clickNearBall = touchableNear(p, 3.5);
        tryResolve(p, s);
    }

    private boolean touchableNear(Player p, double radius) {
        for (Volleyball b : plugin.balls().near(p.getEyeLocation(), radius)) {
            if (b.context().canTouch(p, b)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPlaying(Player p) {
        return plugin.matches().byPlayer(p) != null || !plugin.balls().near(p.getLocation(), 8).isEmpty();
    }

    /** Called from the jump event: sprinting approach jumps near a net get a little extra height. */
    public void onJump(Player p) {
        double boost = plugin.settings().controls.approachJumpBoost();
        if (boost <= 0 || !p.isSprinting()) {
            return;
        }
        Court court = courtFor(p);
        if (court == null) {
            return;
        }
        Location l = p.getLocation();
        double u = court.frame().toU(l.getX(), l.getZ());
        double w = court.frame().toW(l.getX(), l.getZ());
        double zone = Math.max(court.attackLine(), 3.0) + 1.0;
        if (Math.abs(u) <= zone && Math.abs(w) <= court.halfWidth() + 1) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline() && !p.isOnGround()) {
                    p.setVelocity(p.getVelocity().add(new Vector(0, boost, 0)));
                }
            });
        }
    }

    private Court courtFor(Player p) {
        var match = plugin.matches().byPlayer(p);
        if (match != null) {
            return match.court();
        }
        return plugin.courts().at(p.getLocation(), 0.5);
    }

    // ------------------------------------------------------------------------------------------------ tick

    public void tick() {
        tick++;
        Set<Player> active = new HashSet<>();
        for (Volleyball b : plugin.balls().all()) {
            for (Player p : b.world().getNearbyPlayers(b.location(), 24)) {
                active.add(p);
            }
        }
        active.addAll(plugin.matches().allPlayers());
        // movement is tracked for players holding volleyball items too, so a run-up before a toss is known
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (active.contains(p) || CustomItems.typeOf(p.getInventory().getItemInMainHand()) != null) {
                trackMovement(p, state(p));
            }
        }
        for (Player p : active) {
            PlayerState s = state(p);
            s.lastSeenTick = tick;
            if (tick <= s.primaryUntil || tick <= s.secondaryUntil) {
                tryResolve(p, s);
                if (tick == s.primaryUntil || tick == s.secondaryUntil) {
                    expireIntent(p, s);
                }
            }
            if (s.blocking(tick)) {
                checkBlock(p, s);
            }
            if (s.reticle && plugin.settings().controls.aimReticle() && tick % 2 == 0) {
                drawReticle(p, s);
            }
        }
        // drop states of players that left long ago
        Iterator<Map.Entry<UUID, PlayerState>> it = states.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PlayerState> e = it.next();
            if (tick - e.getValue().lastSeenTick > 20 * 120 && Bukkit.getPlayer(e.getKey()) == null) {
                it.remove();
            }
        }
    }

    private void trackMovement(Player p, PlayerState s) {
        Location l = p.getLocation();
        if (!Double.isNaN(s.prevY)) {
            s.vy = (l.getY() - s.prevY) * 20.0;
            double dx = l.getX() - s.prevX;
            double dz = l.getZ() - s.prevZ;
            s.groundSpeed = Math.sqrt(dx * dx + dz * dz) * 20.0;
        }
        s.prevY = l.getY();
        s.prevX = l.getX();
        s.prevZ = l.getZ();
        boolean ground = p.isOnGround();
        if (ground) {
            s.lastGround.set(l.getX(), l.getY(), l.getZ());
        } else if (s.onGround) {
            s.takeoffTick = tick;
            s.sprintTakeoff = runningUp(p, s);
        }
        s.onGround = ground;
    }

    private void expireIntent(Player p, PlayerState s) {
        if (!s.clickNearBall || !plugin.settings().hud.hitFeedback()) {
            return;
        }
        if (s.lastHitTick >= s.clickTick) {
            return;
        }
        p.sendActionBar(Component.text("MISS", NamedTextColor.GRAY, TextDecoration.BOLD)
                .append(Component.text("  time the click as the ball reaches your hands", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.BOLD, false)));
    }

    // ------------------------------------------------------------------------------------------------ resolution

    private void tryResolve(Player p, PlayerState s) {
        boolean primary = tick <= s.primaryUntil;
        boolean secondary = tick <= s.secondaryUntil;
        if (!primary && !secondary) {
            return;
        }
        Location eye = p.getEyeLocation();
        List<Volleyball> candidates = plugin.balls().near(eye, UP_TOUCH_DISTANCE);
        if (candidates.isEmpty()) {
            return;
        }
        Volleyball best = null;
        Contact bestContact = null;
        HitType bestType = null;
        for (Volleyball ball : candidates) {
            if (!ball.context().canTouch(p, ball)) {
                continue;
            }
            boolean tossedByMe = p.getUniqueId().equals(ball.tossedBy());
            if (ball.tick() - ball.lastHitTick() < 3 && !tossedByMe) {
                continue;
            }
            if (p.getUniqueId().equals(ball.lastToucher()) && ball.tick() - ball.lastHitTick() < 6) {
                continue;
            }
            HitType type = chooseType(p, s, ball, primary, tossedByMe);
            if (type == null) {
                continue;
            }
            Contact c = evaluate(p, s, ball, type);
            if (c != null && (bestContact == null || c.quality > bestContact.quality)) {
                best = ball;
                bestContact = c;
                bestType = type;
            }
        }
        if (best == null) {
            return;
        }
        s.primaryUntil = -1;
        s.secondaryUntil = -1;
        s.lastHitTick = tick;
        execute(p, s, best, bestType, bestContact);
    }

    private HitType chooseType(Player p, PlayerState s, Volleyball ball, boolean primary, boolean tossedByMe) {
        boolean air = primary ? s.primaryAirborne || !p.isOnGround() : s.secondaryAirborne || !p.isOnGround();
        boolean sneak = primary ? s.primarySneak : s.secondarySneak;
        if (tossedByMe) {
            if (!primary) {
                return null;
            }
            if (air) {
                return sneak ? HitType.SERVE_JUMP_FLOAT : HitType.SERVE_JUMP;
            }
            return sneak ? HitType.SERVE_TOPSPIN : HitType.SERVE_FLOAT;
        }
        if (primary) {
            if (air) {
                return sneak ? HitType.TIP : HitType.SPIKE;
            }
            if (sneak) {
                return HitType.DIG;
            }
            double above = ball.position().y - p.getLocation().getY();
            return above > p.getEyeHeight() + 0.25 ? HitType.STANDING_ATTACK : HitType.BUMP;
        }
        return sneak ? HitType.QUICK_SET : HitType.SET;
    }

    /** A candidate contact. */
    private record Contact(double quality, Vec3 point, Vec3 ideal, boolean rewound) {
    }

    private Contact evaluate(Player p, PlayerState s, Volleyball ball, HitType type) {
        Vec3 ideal = idealPoint(p, type);
        double reach = reach(type);
        Settings.Controls ctl = plugin.settings().controls;
        double maxRewind = 0;
        if (ctl.lagCompensation()) {
            double pingTicks = Math.min(p.getPing(), ctl.maxCompensationMs()) / 50.0;
            // + display interpolation delay seen by the client
            maxRewind = pingTicks + plugin.settings().ball.teleportInterpolation() * 0.5;
        }
        // never rewind across the last touch or the last floor contact
        maxRewind = Math.min(maxRewind, ball.tick() - Math.max(ball.lastHitTick(), ball.lastFloorTick()) - 0.01);
        maxRewind = Math.max(0, maxRewind);

        double bestDist = Double.MAX_VALUE;
        Vec3 bestPoint = null;
        boolean rewound = false;
        Vec3 a = new Vec3();
        Vec3 b = new Vec3();
        int segments = (int) Math.ceil(maxRewind);
        // segment [now-1, now] always checked so fast balls passing through the zone are caught
        segments = Math.max(1, segments);
        for (int i = 0; i < segments; i++) {
            double t0 = Math.min(i + 1, Math.max(maxRewind, 1));
            if (!ball.historicalPosition(i, a) || !ball.historicalPosition(t0, b)) {
                break;
            }
            Vec3 q = closestOnSegment(a, b, ideal);
            double d = q.distance(ideal);
            if (d < bestDist) {
                bestDist = d;
                bestPoint = q;
                rewound = i > 0 || q.distanceSq(ball.position()) > 1e-6;
            }
        }
        if (bestPoint == null) {
            bestPoint = ball.position().copy();
            bestDist = bestPoint.distance(ideal);
        }
        if (bestDist > reach) {
            return null;
        }
        double q = 1 - Math.pow(bestDist / reach, 1.6);
        double vin = ball.body().speed();
        switch (type) {
            case BUMP, DIG -> q *= 1 - clamp((vin - 10) / 35, 0, 0.4);
            case SET, QUICK_SET -> q *= 1 - clamp((vin - 8) / 25, 0, 0.5);
            case SPIKE, TIP, SERVE_JUMP, SERVE_JUMP_FLOAT -> q *= 1 - Math.min(0.35, Math.abs(s.vy) / 20.0);
            default -> {
            }
        }
        return new Contact(clamp(q, 0, 1), bestPoint, ideal, rewound);
    }

    private static Vec3 closestOnSegment(Vec3 a, Vec3 b, Vec3 p) {
        Vec3 ab = b.copy().sub(a);
        double len2 = ab.lengthSq();
        if (len2 < 1e-12) {
            return a.copy();
        }
        double t = clamp(p.copy().sub(a).dot(ab) / len2, 0, 1);
        return a.copy().addScaled(ab, t);
    }

    /** Ideal contact point of a technique, relative to the player's body. */
    private Vec3 idealPoint(Player p, HitType type) {
        Location l = p.getLocation();
        Vec3 feet = new Vec3(l.getX(), l.getY(), l.getZ());
        Vec3 dir = horizontalDir(l);
        double eye = p.getEyeHeight();
        return switch (type) {
            case BUMP -> feet.addScaled(dir, 0.55).add(0, 0.75, 0);
            case DIG -> feet.addScaled(dir, 0.9).add(0, 0.35, 0);
            case SET, QUICK_SET -> feet.addScaled(dir, 0.25).add(0, eye + 0.45, 0);
            case SPIKE, TIP -> feet.addScaled(dir, 0.45).add(0, eye + 0.7, 0);
            case STANDING_ATTACK -> feet.addScaled(dir, 0.4).add(0, eye + 0.6, 0);
            case SERVE_FLOAT, SERVE_TOPSPIN, SERVE_JUMP, SERVE_JUMP_FLOAT -> feet.addScaled(dir, 0.35).add(0, eye + 0.55, 0);
            case SERVE_UNDERHAND -> feet.addScaled(dir, 0.5).add(0, 0.8, 0);
            case BLOCK -> feet.add(0, eye + 0.6, 0);
        };
    }

    private double reach(HitType type) {
        Settings s = plugin.settings();
        return switch (type) {
            case BUMP -> s.bump.reach();
            case DIG -> s.dig.reach();
            case SET, QUICK_SET -> s.set.reach();
            case SPIKE -> s.spike.reach();
            case TIP -> s.tip.reach();
            case STANDING_ATTACK -> s.standingAttack.reach();
            case SERVE_FLOAT, SERVE_TOPSPIN, SERVE_JUMP, SERVE_JUMP_FLOAT, SERVE_UNDERHAND -> s.serve.reach();
            case BLOCK -> s.block.width();
        };
    }

    private static Vec3 horizontalDir(Location l) {
        double yaw = Math.toRadians(l.getYaw());
        return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    // ------------------------------------------------------------------------------------------------ execution

    private void execute(Player p, PlayerState s, Volleyball ball, HitType type, Contact contact) {
        Settings st = plugin.settings();
        double q = contact.quality;
        Location l = p.getLocation();
        boolean airborne = !p.isOnGround();
        Vec3 start = contact.rewound && contact.point.distance(contact.ideal) < ball.position().distance(contact.ideal)
                ? contact.point.copy() : ball.position().copy();
        Court court = ball.court();
        double floorY = court != null ? court.centerY() : s.lastGround.y;
        Vec3 dir = horizontalDir(l);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        Vec3 vel;
        Vec3 spin = new Vec3();
        boolean knuckle = false;
        switch (type) {
            case BUMP, DIG -> {
                Settings.Bump cfg = type == HitType.DIG ? st.dig : st.bump;
                double dist = cfg.aim().distanceFor(l.getPitch());
                double apex = cfg.apex() + 0.1 * dist;
                Vec3 d = dir.copy();
                if (q < 0.15) {
                    // shank: the ball flies off the arms
                    double turn = Math.toRadians((40 + rnd.nextDouble() * 50) * (rnd.nextBoolean() ? 1 : -1));
                    d = rotateY(dir, turn);
                    dist *= 0.4 + rnd.nextDouble() * 0.8;
                    apex *= 0.5 + rnd.nextDouble() * 0.8;
                }
                Vec3 target = target(l, d, dist, floorY, cfg.maxError() * Math.pow(1 - q, 1.4), rnd);
                spin = TrajectorySolver.SpinFactory.topspin(-3 - rnd.nextDouble() * 3).spinFor(d);
                vel = solver.solveArc(start, target, floorY, Math.max(0.4, apex), spin);
            }
            case SET, QUICK_SET -> {
                Settings.SetHit cfg = st.set;
                double dist = cfg.aim().distanceFor(l.getPitch());
                double apex = type == HitType.QUICK_SET ? cfg.quickApex() : cfg.apex();
                Vec3 target = target(l, dir, dist, floorY, cfg.maxError() * Math.pow(1 - q, 1.5), rnd);
                double wobble = (1 - q) * 10;
                spin.set(rnd.nextGaussian() * wobble, rnd.nextGaussian() * wobble, rnd.nextGaussian() * wobble);
                vel = solver.solveArc(start, target, floorY, apex, spin);
            }
            case TIP -> {
                Settings.Tip cfg = st.tip;
                double dist = cfg.aim().distanceFor(l.getPitch());
                Vec3 target = target(l, dir, dist, floorY, cfg.maxError() * Math.pow(1 - q, 1.4), rnd);
                vel = solver.solveArc(start, target, floorY, cfg.apex(), spin);
            }
            case SPIKE -> {
                Settings.Spike cfg = st.spike;
                double power = q * (s.sprintTakeoff ? 1.0 : 0.88);
                // input.isSprint() may be set without the sprint flag: treat a fast take-off as an approach
                double speed = lerp(cfg.minSpeed(), cfg.maxSpeed(), power);
                double rate = lerp(cfg.minTopspin(), cfg.maxTopspin(), q);
                double dist = cfg.aim().distanceFor(l.getPitch());
                Vec3 target = target(l, dir, dist, floorY, cfg.maxError() * Math.pow(1 - q, 1.3), rnd);
                TrajectorySolver.SpinFactory sf = TrajectorySolver.SpinFactory.topspin(rate);
                vel = solver.solveDriven(start, target, floorY, speed, sf);
                spin = sf.spinFor(vel);
            }
            case STANDING_ATTACK -> {
                Settings.StandingAttack cfg = st.standingAttack;
                double speed = lerp(cfg.minSpeed(), cfg.maxSpeed(), q);
                double dist = cfg.aim().distanceFor(l.getPitch());
                Vec3 target = target(l, dir, dist, floorY, cfg.maxError() * Math.pow(1 - q, 1.3), rnd);
                TrajectorySolver.SpinFactory sf = TrajectorySolver.SpinFactory.topspin(cfg.topspin());
                vel = solver.solveDriven(start, target, floorY, speed, sf);
                spin = sf.spinFor(vel);
            }
            case SERVE_FLOAT, SERVE_TOPSPIN, SERVE_JUMP, SERVE_JUMP_FLOAT, SERVE_UNDERHAND -> {
                Settings.Serve cfg = st.serve;
                double dist = cfg.aim().distanceFor(l.getPitch());
                double err = cfg.maxError() * Math.pow(1 - q, 1.3);
                Vec3 target = target(l, dir, dist, floorY, type == HitType.SERVE_UNDERHAND ? err * 0.5 : err, rnd);
                TrajectorySolver.SpinFactory sf;
                double speed;
                switch (type) {
                    case SERVE_JUMP -> {
                        speed = lerp(cfg.jumpMinSpeed(), cfg.jumpMaxSpeed(), q * (s.sprintTakeoff ? 1.0 : 0.85));
                        sf = TrajectorySolver.SpinFactory.topspin(cfg.jumpTopspin() * (0.6 + 0.4 * q));
                    }
                    case SERVE_TOPSPIN -> {
                        speed = lerp(cfg.topspinMinSpeed(), cfg.topspinMaxSpeed(), q);
                        sf = TrajectorySolver.SpinFactory.topspin(cfg.standingTopspin());
                    }
                    case SERVE_JUMP_FLOAT, SERVE_FLOAT -> {
                        speed = lerp(cfg.floatMinSpeed(), cfg.floatMaxSpeed(), q)
                                + (type == HitType.SERVE_JUMP_FLOAT ? 1.0 : 0);
                        sf = TrajectorySolver.SpinFactory.topspin(rnd.nextGaussian() * 0.4);
                        knuckle = true;
                    }
                    default -> {
                        speed = 0;
                        sf = TrajectorySolver.SpinFactory.NONE;
                    }
                }
                if (type == HitType.SERVE_UNDERHAND) {
                    vel = solver.solveArc(start, target, floorY, cfg.underhandApex(), new Vec3());
                } else {
                    vel = solver.solveDriven(start, target, floorY, speed, sf);
                    spin = sf.spinFor(vel);
                }
            }
            default -> throw new IllegalStateException("unexpected " + type);
        }
        if (!vel.isFinite()) {
            vel = dir.copy().mul(5).add(0, 4, 0);
        }
        ball.teleport(start);
        ball.launch(vel, spin, knuckle, p.getUniqueId(), type);

        // for jump touches the rules care about the take-off spot
        Vec3 feet = airborne ? s.lastGround.copy() : new Vec3(l.getX(), l.getY(), l.getZ());
        HitResult result = new HitResult(type, q, Grade.of(q), start, feet, vel.length(), airborne);
        effects(p, ball, result);
        ball.context().onHit(p, ball, result);
    }

    /** Aim point on the floor, with a quality dependent random error. */
    private static Vec3 target(Location l, Vec3 dir, double dist, double floorY, double sigma, ThreadLocalRandom rnd) {
        Vec3 t = new Vec3(l.getX() + dir.x * dist, floorY, l.getZ() + dir.z * dist);
        if (sigma > 1e-3) {
            t.x += rnd.nextGaussian() * sigma;
            t.z += rnd.nextGaussian() * sigma;
        }
        return t;
    }

    private static Vec3 rotateY(Vec3 v, double angle) {
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        return new Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
    }

    private void effects(Player p, Volleyball ball, HitResult r) {
        Location at = ball.location();
        var fx = plugin.fx();
        switch (r.type()) {
            case SPIKE, STANDING_ATTACK -> {
                p.swingMainHand();
                fx.sound(at, r.grade() == Grade.PERFECT ? Settings.SoundId.SPIKE_PERFECT : Settings.SoundId.SPIKE);
                fx.particle(at, Particle.CRIT, 12 + (int) (r.quality() * 14), 0.15, 0.35);
                if (r.grade() == Grade.PERFECT) {
                    fx.particle(at, Particle.SWEEP_ATTACK, 1, 0, 0);
                }
            }
            case TIP -> {
                p.swingMainHand();
                fx.sound(at, Settings.SoundId.TIP);
            }
            case SET, QUICK_SET -> {
                p.swingMainHand();
                p.swingOffHand();
                fx.sound(at, Settings.SoundId.SET);
                fx.particle(at, Particle.CLOUD, 3, 0.1, 0.01);
            }
            case BUMP, DIG -> {
                p.swingMainHand();
                p.swingOffHand();
                fx.sound(at, Settings.SoundId.BUMP, (float) Math.min(1.3, 0.6 + r.speed() / 20));
                fx.particle(at, Particle.CLOUD, 4, 0.12, 0.02);
            }
            case BLOCK -> {
                p.swingMainHand();
                p.swingOffHand();
                fx.sound(at, Settings.SoundId.BLOCK);
                fx.particle(at, Particle.CRIT, 10, 0.2, 0.2);
            }
            default -> {
                p.swingMainHand();
                fx.sound(at, Settings.SoundId.SERVE, r.type() == HitType.SERVE_JUMP ? 1.3f : 1f);
                fx.particle(at, Particle.CRIT, 8, 0.1, 0.25);
            }
        }
        if (r.grade() == Grade.PERFECT) {
            fx.particle(at, Particle.END_ROD, 6, 0.05, 0.06);
        }
        if (plugin.settings().hud.hitFeedback()) {
            Component msg = Component.text(r.grade().label + " ", r.grade().color, TextDecoration.BOLD)
                    .append(Component.text(r.type().displayName().toUpperCase(), NamedTextColor.WHITE, TextDecoration.BOLD));
            if (r.type().attack() || r.type().serve()) {
                msg = msg.append(Component.text(String.format(" · %.0f km/h", r.speed() * 3.6), NamedTextColor.GRAY)
                        .decoration(TextDecoration.BOLD, false));
            }
            p.sendActionBar(msg);
        }
    }

    // ------------------------------------------------------------------------------------------------ block

    private boolean tryStartBlock(Player p, PlayerState s) {
        if (p.isOnGround() && !canReachAboveNet(p)) {
            return false;
        }
        Court court = courtFor(p);
        if (court == null) {
            return false;
        }
        Location l = p.getLocation();
        double u = court.frame().toU(l.getX(), l.getZ());
        double w = court.frame().toW(l.getX(), l.getZ());
        if (Math.abs(u) > 1.3 || Math.abs(w) > court.halfWidth() + 0.6) {
            return false;
        }
        double handTop = l.getY() - court.centerY() + p.getEyeHeight() + 0.75;
        if (handTop < court.netHeight() + 0.05) {
            return false;
        }
        if (!s.blocking(tick)) {
            s.blockStart = tick;
            s.blockUntil = tick + plugin.settings().block.durationTicks();
            p.swingMainHand();
            p.swingOffHand();
            plugin.fx().particle(p.getEyeLocation().add(0, 0.6, 0), Particle.CLOUD, 2, 0.15, 0.0);
        }
        return true;
    }

    private boolean canReachAboveNet(Player p) {
        Court court = courtFor(p);
        if (court == null) {
            return false;
        }
        double handTop = p.getLocation().getY() - court.centerY() + p.getEyeHeight() + 0.75;
        return handTop >= court.netHeight() + 0.05;
    }

    private void checkBlock(Player p, PlayerState s) {
        Settings.Block cfg = plugin.settings().block;
        Location l = p.getLocation();
        for (Volleyball ball : plugin.balls().near(l, 5)) {
            Court court = ball.court();
            if (court == null || !ball.context().canTouch(p, ball)) {
                continue;
            }
            if (p.getUniqueId().equals(ball.lastToucher())) {
                continue;
            }
            CourtFrame f = court.frame();
            double pu = f.toU(l.getX(), l.getZ());
            double pw = f.toW(l.getX(), l.getZ());
            int side = pu < 0 ? -1 : 1;
            Vec3 lv = f.dirToLocal(ball.body().vel, new Vec3());
            if (lv.x * side <= 0.5) {
                continue; // not travelling toward the blocker's court
            }
            double r = plugin.settings().physics.radius();
            double handTop = l.getY() - court.centerY() + p.getEyeHeight() + 0.75;
            double uA = side * 0.25;
            double uB = -side * cfg.reachOver();
            double minU = Math.min(uA, uB) - r;
            double maxU = Math.max(uA, uB) + r;
            double minY = court.netHeight() - 0.35 - r;
            double maxY = handTop + r;
            double half = cfg.width() / 2;
            double minW = pw - half - r;
            double maxW = pw + half + r;
            Vec3 a = f.toLocal(ball.previousPosition(), new Vec3());
            Vec3 b = f.toLocal(ball.position(), new Vec3());
            double t = segmentBox(a, b, minU, maxU, minY, maxY, minW, maxW);
            if (t < 0) {
                continue;
            }
            Vec3 hit = a.copy().lerp(b, t);
            double age = tick - s.blockStart;
            double timing = age <= 2 ? 0.75 : age <= 8 ? 1.0 : Math.max(0.3, 1 - (age - 8) * 0.12);
            double offset = Math.abs(hit.z - pw) / Math.max(0.05, half);
            double q = clamp(timing * (1 - 0.6 * offset * offset), 0, 1);
            resolveBlock(p, s, ball, court, side, hit, lv, q, offset, hit.z - pw);
            return;
        }
    }

    private void resolveBlock(Player p, PlayerState s, Volleyball ball, Court court, int side, Vec3 hitLocal,
                              Vec3 vLocal, double q, double offset, double lateral) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double speed = vLocal.length();
        Vec3 out;
        if (offset > 0.8) {
            // tooled off the edge of the hands: keeps going, deflected outward
            out = new Vec3(vLocal.x * 0.65, Math.abs(vLocal.y) * 0.2 + 1.5, vLocal.z + Math.signum(lateral) * speed * 0.35);
        } else if (q < 0.35) {
            // soft block: pops up, usually staying on the blocker's side
            out = new Vec3(vLocal.x * 0.12, 2.5 + speed * 0.18, vLocal.z * 0.3 + rnd.nextGaussian());
        } else {
            // stuff block back down into the attacker's court
            double e = 0.35 + 0.3 * q;
            out = new Vec3(-vLocal.x * e, -Math.abs(vLocal.y) * 0.3 - 2.5 * q, vLocal.z * 0.4 + rnd.nextGaussian() * (1 - q));
        }
        Vec3 world = court.frame().dirToWorld(out.x, out.y, out.z, new Vec3());
        Vec3 pos = court.frame().toWorld(hitLocal, new Vec3());
        s.blockUntil = -1;
        s.lastHitTick = tick;
        ball.teleport(pos);
        Vec3 spin = new Vec3(rnd.nextGaussian() * 4, rnd.nextGaussian() * 4, rnd.nextGaussian() * 4);
        ball.launch(world, spin, false, p.getUniqueId(), HitType.BLOCK);
        Location l = p.getLocation();
        HitResult r = new HitResult(HitType.BLOCK, q, Grade.of(q), pos, new Vec3(l.getX(), l.getY(), l.getZ()),
                world.length(), !p.isOnGround());
        effects(p, ball, r);
        ball.context().onHit(p, ball, r);
    }

    /** Slab test: returns the entry parameter in [0,1] of segment a-b into the box, or -1. */
    private static double segmentBox(Vec3 a, Vec3 b, double minX, double maxX, double minY, double maxY,
                                     double minZ, double maxZ) {
        double t0 = 0;
        double t1 = 1;
        double[] o = {a.x, a.y, a.z};
        double[] d = {b.x - a.x, b.y - a.y, b.z - a.z};
        double[] mn = {minX, minY, minZ};
        double[] mx = {maxX, maxY, maxZ};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1e-12) {
                if (o[i] < mn[i] || o[i] > mx[i]) {
                    return -1;
                }
            } else {
                double inv = 1 / d[i];
                double ta = (mn[i] - o[i]) * inv;
                double tb = (mx[i] - o[i]) * inv;
                if (ta > tb) {
                    double tmp = ta;
                    ta = tb;
                    tb = tmp;
                }
                t0 = Math.max(t0, ta);
                t1 = Math.min(t1, tb);
                if (t0 > t1) {
                    return -1;
                }
            }
        }
        return t0;
    }

    // ------------------------------------------------------------------------------------------------ serve

    /**
     * Right click with the volleyball item: toss for a serve. Sprinting tosses higher and further in front for a
     * jump serve.
     */
    public Volleyball toss(Player p, boolean practice) {
        Vec3[] st = tossState(p);
        Volleyball ball = practice ? plugin.balls().spawnPractice(p, st[0]) : null;
        if (ball == null) {
            return null;
        }
        prepareToss(p, ball, st[1]);
        return ball;
    }

    /**
     * A run-up for a jump serve. The sprint flag alone is unreliable (some clients only send the sprint key), so
     * the key state and the measured ground speed count as well.
     */
    private boolean runningUp(Player p, PlayerState s) {
        return p.isSprinting() || p.getCurrentInput().isSprint() || s.groundSpeed > 4.0;
    }

    /** Gives a freshly spawned ball its toss velocity. */
    public void prepareToss(Player p, Volleyball ball, Vec3 vel) {
        ball.launch(vel, new Vec3(0, 2, 0), false, null, null);
        ball.markToss(p.getUniqueId());
        p.swingOffHand();
        plugin.fx().sound(ball.location(), Settings.SoundId.TOSS);
        PlayerState s = state(p);
        s.lastHitTick = tick - plugin.settings().controls.hitCooldownTicks();
    }

    /** Position and velocity of a toss for the player (used by matches that spawn their own ball). */
    public Vec3[] tossState(Player p) {
        Settings.Serve cfg = plugin.settings().serve;
        Location l = p.getLocation();
        Vec3 dir = horizontalDir(l);
        boolean jump = runningUp(p, state(p));
        if (plugin.settings().hud.hitFeedback()) {
            p.sendActionBar(Component.text(jump ? "JUMP TOSS" : "TOSS", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .append(Component.text(jump ? "  jump and hit at the top" : "  hit it as it falls to your hand",
                            NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false)));
        }
        Vec3 pos = new Vec3(l.getX(), l.getY(), l.getZ()).addScaled(dir, 0.45).add(0, p.getEyeHeight() - 0.15, 0);
        Vec3 vel = jump ? dir.copy().mul(cfg.jumpTossForward()).add(0, cfg.jumpTossSpeed(), 0)
                : dir.copy().mul(0.25).add(0, cfg.tossSpeed(), 0);
        return new Vec3[]{pos, vel};
    }

    /** Left click with the volleyball item and no toss in the air: underhand serve. */
    public void underhand(Player p, Volleyball ball) {
        PlayerState s = state(p);
        s.lastHitTick = tick;
        Contact c = new Contact(0.9, ball.position().copy(), ball.position().copy(), false);
        execute(p, s, ball, HitType.SERVE_UNDERHAND, c);
    }

    public Vec3 underhandPosition(Player p) {
        Location l = p.getLocation();
        return new Vec3(l.getX(), l.getY(), l.getZ()).addScaled(horizontalDir(l), 0.5).add(0, 0.8, 0);
    }

    // ------------------------------------------------------------------------------------------------ reticle

    private void drawReticle(Player p, PlayerState s) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        boolean holdingBall = CustomItems.is(hand, ItemType.VOLLEYBALL);
        AimProfile profile = null;
        Color color = null;
        Settings st = plugin.settings();
        if (holdingBall) {
            profile = st.serve.aim();
            color = Color.fromRGB(255, 215, 0);
        } else {
            Volleyball tossed = null;
            Volleyball nearest = null;
            double best = 36;
            for (Volleyball b : plugin.balls().near(p.getEyeLocation(), 6)) {
                if (p.getUniqueId().equals(b.tossedBy())) {
                    tossed = b;
                }
                double d = b.location().distanceSquared(p.getEyeLocation());
                if (d < best && b.context().canTouch(p, b) && !b.body().resting) {
                    best = d;
                    nearest = b;
                }
            }
            if (tossed != null) {
                profile = st.serve.aim();
                color = Color.fromRGB(255, 215, 0);
            } else if (nearest != null) {
                double above = nearest.position().y - p.getLocation().getY();
                if (!p.isOnGround()) {
                    profile = st.spike.aim();
                    color = Color.fromRGB(255, 80, 60);
                } else if (above > p.getEyeHeight() + 0.25) {
                    profile = st.set.aim();
                    color = Color.fromRGB(90, 200, 255);
                } else {
                    profile = st.bump.aim();
                    color = Color.fromRGB(120, 255, 120);
                }
            }
        }
        if (profile == null) {
            return;
        }
        Location l = p.getLocation();
        Court court = courtFor(p);
        double floorY = court != null ? court.centerY() : s.lastGround.y;
        Vec3 dir = horizontalDir(l);
        double dist = profile.distanceFor(l.getPitch());
        plugin.fx().ringFor(p, l.getX() + dir.x * dist, floorY, l.getZ() + dir.z * dist, 0.35, color, 10);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * clamp(t, 0, 1);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public long currentTick() {
        return tick;
    }
}
