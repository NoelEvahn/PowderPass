package dev.powderpass.physics;

import dev.powderpass.util.MathUtil;
import org.joml.Vector3d;

/**
 * The PowderPass ski simulation. Stateless: all state lives in {@link SkiState}.
 *
 * <p>On the ground the rider is a point mass constrained to the smoothed {@link Heightfield}. Gravity is
 * projected onto the slope, the skis' edges scrub off lateral velocity (converting most of it into forward
 * speed when the skis are carving, and very little when they are skidding), and kinetic friction plus
 * quadratic air drag bound the top speed. When the terrain falls away faster than the rider's momentum
 * follows it, the rider launches; in the air they can spin, flip and grab, and the landing is judged on ski
 * alignment, rotation and impact.
 */
public final class SkiPhysics {

    private static final double MAX_SUBSTEP = 0.45;
    private static final double REST_SPEED = 0.004;
    private static final double MIN_AIR_TICKS_FOR_JUDGING = 6;

    private SkiPhysics() {
    }

    public static void step(SkiState s, SkiInput in, Heightfield field, SkiParams p, StepResult out) {
        out.reset();
        double startY = s.pos.y;
        double startX = s.pos.x;
        double startZ = s.pos.z;

        if (s.grounded) {
            groundForces(s, in, field, p, out);
            if (out.crashed()) {
                return;
            }
        } else {
            airForces(s, in, p);
        }

        double speed = s.vel.length();
        if (speed > p.maxSpeed()) {
            s.vel.mul(p.maxSpeed() / speed);
        }

        move(s, field, p, out);

        double dx = s.pos.x - startX;
        double dz = s.pos.z - startZ;
        out.horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        out.verticalDrop = Math.max(0.0, startY - s.pos.y);
        updateVisuals(s, p);
    }

    // ------------------------------------------------------------------------------------------------ ground

    private static void groundForces(SkiState s, SkiInput in, Heightfield field, SkiParams p, StepResult out) {
        final Vector3d v = s.vel;
        final Vector3d n = field.normal(s.pos.x, s.pos.z, s.pos.y, s.normal);
        final SurfaceKind kind = field.sampler().surface(floor(s.pos.x), floor(s.pos.z), s.pos.y);
        s.surface = kind;
        if (kind == SurfaceKind.LIQUID) {
            out.crash = CrashReason.LIQUID;
            return;
        }
        final SkiParams.Surface surf = p.surface(kind);
        final double g = p.gravity();
        s.groundTicks++;
        s.tucking = in.sneak();
        s.braking = in.backward() && !in.forward();

        // Stay on the surface plane, then let gravity pull along it.
        double into = v.dot(n);
        v.sub(n.x * into, n.y * into, n.z * into);
        v.add(n.x * g * n.y, -g + n.y * g * n.y, n.z * g * n.y);

        // --- steering
        double speed = v.length();
        boolean hardCarve = in.left() != in.right();
        double turnCap = p.turnRate() * surf.grip() * p.gripMultiplier() / (1.0 + speed * p.turnSpeedFalloff());
        if (s.tucking) {
            turnCap *= p.tuckTurnFactor();
        }
        if (s.braking) {
            turnCap *= 3.0; // pivot the skis sideways to skid
        }
        double applied;
        if (p.steering() == SteeringMode.KEYS) {
            double dir = (in.right() ? 1.0 : 0.0) - (in.left() ? 1.0 : 0.0);
            applied = dir * turnCap * p.hardCarveTurnFactor();
        } else {
            double target = in.lookYaw();
            if (hardCarve) {
                target += in.right() ? p.hardCarveYawOffset() : -p.hardCarveYawOffset();
                turnCap *= p.hardCarveTurnFactor();
            }
            applied = MathUtil.clamp(MathUtil.wrapDegrees(target - s.heading), -turnCap, turnCap);
        }
        s.heading = MathUtil.wrapDegrees(s.heading + applied);
        s.turn = applied;

        // --- edge grip: split velocity into along-ski and across-ski components
        Vector3d f = MathUtil.forward(s.heading, new Vector3d());
        double fn = f.dot(n);
        f.sub(n.x * fn, n.y * fn, n.z * fn);
        if (f.lengthSquared() < 1.0e-9) {
            f.set(0, 0, 1);
        }
        f.normalize();
        double fwd = v.dot(f);
        Vector3d lat = new Vector3d(v).sub(f.x * fwd, f.y * fwd, f.z * fwd);
        double latMag = lat.length();
        double grip = MathUtil.clamp(p.edgeGrip() * surf.grip() * p.gripMultiplier(), 0.0, 0.95);
        double removed = latMag * grip;
        lat.mul(1.0 - grip);
        // Carving (small skid angle) converts scrubbed speed back into forward motion; skidding doesn't.
        double skidAngleCos = (Math.abs(fwd) + 1.0e-9) / (Math.sqrt(fwd * fwd + latMag * latMag) + 1.0e-9);
        double efficiency = p.carveEfficiency() * Math.pow(skidAngleCos, 4);
        if (s.braking) {
            efficiency *= 0.15;
        }
        double converted = removed * efficiency;
        fwd += (fwd >= 0.0 ? 1.0 : -1.0) * converted;
        s.skid = removed - converted;
        v.set(f).mul(fwd).add(lat);

        // --- friction and drag
        double mu = surf.friction() / Math.sqrt(p.speedMultiplier()) + (s.braking ? p.brakeFriction() : 0.0);
        speed = v.length();
        if (speed > 0.0) {
            double decel = mu * g * Math.max(n.y, 0.2);
            v.mul(Math.max(0.0, speed - decel) / speed);
        }
        double drag = p.airDrag() / p.speedMultiplier() * (s.tucking ? p.tuckDragFactor() : 1.0)
                * (s.braking ? 1.5 : 1.0);
        speed = v.length();
        v.mul(Math.max(0.0, 1.0 - drag * speed));

        // --- poling / skating
        s.pushing = false;
        if (in.forward() && !in.backward() && fwd >= -0.01 && speed < p.pushMaxSpeed()) {
            v.add(f.x * p.pushAccel(), f.y * p.pushAccel(), f.z * p.pushAccel());
            s.pushing = true;
        }

        // --- come to rest instead of jittering on gentle slopes
        if (v.length() < REST_SPEED && n.y > 0.985 && !s.pushing) {
            v.zero();
        }

        // --- jump: hold to load, release to pop
        if (in.jump()) {
            s.jumpCharge = Math.min(p.maxJumpCharge(), s.jumpCharge + 1);
        } else if (s.jumpCharge > 0) {
            double pop = p.jumpBase() + p.jumpPerChargeTick() * s.jumpCharge;
            s.jumpCharge = 0;
            v.add(n.x * pop * 0.35, pop, n.z * pop * 0.35);
            becomeAirborne(s);
            out.jumped = true;
            out.launched = true;
        }
    }

    // ------------------------------------------------------------------------------------------------ air

    private static void airForces(SkiState s, SkiInput in, SkiParams p) {
        final Vector3d v = s.vel;
        v.y -= p.gravity();
        double speed = v.length();
        v.mul(Math.max(0.0, 1.0 - p.airDrag() * 0.6 / p.speedMultiplier() * speed));
        s.airTicks++;
        s.jumpCharge = 0;
        s.braking = false;
        s.pushing = false;
        s.skid = 0.0;
        s.turn = 0.0;
        boolean spinKeys = in.left() || in.right();
        boolean flipKeys = in.forward() || in.backward();
        if (s.airTicks == 1) {
            s.spinLocked = spinKeys;
            s.flipLocked = flipKeys;
        }
        if (!spinKeys) {
            s.spinLocked = false;
        }
        if (!flipKeys) {
            s.flipLocked = false;
        }
        if (!s.spinLocked && in.left() != in.right()) {
            s.spin += in.right() ? p.airSpinRate() : -p.airSpinRate();
        }
        if (!s.flipLocked && in.forward() != in.backward()) {
            s.flip += in.forward() ? p.airFlipRate() : -p.airFlipRate();
        }
        s.tucking = in.sneak();
        if (in.sneak()) {
            s.grabTicks++;
        }
        s.peakY = Math.max(s.peakY, s.pos.y);
    }

    private static void becomeAirborne(SkiState s) {
        s.grounded = false;
        s.airTicks = 0;
        s.spin = 0.0;
        s.flip = 0.0;
        s.grabTicks = 0;
        s.groundTicks = 0;
        s.launchY = s.pos.y;
        s.peakY = s.pos.y;
    }

    // ------------------------------------------------------------------------------------------------ motion

    private static void move(SkiState s, Heightfield field, SkiParams p, StepResult out) {
        final Vector3d v = s.vel;
        double travel = Math.max(Math.sqrt(v.x * v.x + v.z * v.z), Math.abs(v.y));
        int steps = (int) MathUtil.clamp(Math.ceil(travel / MAX_SUBSTEP), 1, 8);
        for (int i = 0; i < steps; i++) {
            double x = s.pos.x;
            double z = s.pos.z;
            double y = s.pos.y;
            double nx = x + v.x / steps;
            double nz = z + v.z / steps;
            double ny = y + v.y / steps;

            boolean crossesX = floor(nx) != floor(x);
            boolean crossesZ = floor(nz) != floor(z);
            if ((crossesX || crossesZ) && blocked(field.sampler(), nx, ny, nz)) {
                boolean bx = crossesX && blocked(field.sampler(), nx, ny, z);
                boolean bz = crossesZ && blocked(field.sampler(), x, ny, nz);
                if (!bx && !bz) {
                    bx = crossesX;
                    bz = crossesZ;
                }
                double ix = bx ? v.x : 0.0;
                double iz = bz ? v.z : 0.0;
                double impact = Math.sqrt(ix * ix + iz * iz);
                if (impact > p.wallCrashSpeed()) {
                    out.crash = CrashReason.WALL;
                    return;
                }
                out.wallBump = true;
                if (bx) {
                    v.x = -v.x * 0.2;
                    nx = x;
                }
                if (bz) {
                    v.z = -v.z * 0.2;
                    nz = z;
                }
            }

            double ground = field.height(nx, nz, s.grounded ? y : ny);
            if (s.grounded) {
                if (Double.isNaN(ground) || ny - ground > p.launchGap()) {
                    s.pos.set(nx, ny, nz);
                    becomeAirborne(s);
                    out.launched = true;
                } else {
                    s.pos.set(nx, ground, nz);
                }
            } else if (!Double.isNaN(ground) && ny <= ground) {
                s.pos.set(nx, ground, nz);
                land(s, field, p, out);
                if (out.crashed()) {
                    return;
                }
            } else {
                s.pos.set(nx, ny, nz);
            }
        }
    }

    private static void land(SkiState s, Heightfield field, SkiParams p, StepResult out) {
        Vector3d n = field.normal(s.pos.x, s.pos.z, s.pos.y, s.normal);
        Vector3d v = s.vel;
        double impact = Math.max(0.0, -v.dot(n));
        double visualHeading = s.heading + s.spin;
        double horizontal = Math.sqrt(v.x * v.x + v.z * v.z);
        boolean judged = s.airTicks >= MIN_AIR_TICKS_FOR_JUDGING;
        boolean switchLanding = false;

        if (horizontal > 0.15) {
            double travelYaw = MathUtil.yawOf(v.x, v.z);
            double diff = Math.abs(MathUtil.wrapDegrees(travelYaw - visualHeading));
            double axisDiff = Math.min(diff, 180.0 - diff);
            switchLanding = diff > 90.0;
            if (judged && axisDiff > p.landingTolerance()) {
                out.crash = CrashReason.BAD_LANDING;
                return;
            }
        }
        if (judged && Math.abs(MathUtil.wrapDegrees(s.flip)) > p.flipTolerance()) {
            out.crash = CrashReason.HEAD_PLANT;
            return;
        }
        if (impact > p.crashImpact()) {
            out.crash = CrashReason.HARD_LANDING;
            return;
        }

        out.landed = true;
        out.landingImpact = impact;
        out.landingSpin = s.spin;
        out.landingFlip = s.flip;
        out.landingGrabTicks = s.grabTicks;
        out.landingAirTicks = s.airTicks;
        out.landingSwitch = switchLanding;
        out.landingDrop = Math.max(0.0, s.peakY - s.pos.y);

        s.heading = MathUtil.wrapDegrees(visualHeading);
        double into = v.dot(n);
        v.sub(n.x * into, n.y * into, n.z * into);
        // A heavy landing costs some speed.
        if (impact > p.safeImpact()) {
            v.mul(0.8);
        }
        s.grounded = true;
        s.groundTicks = 0;
        s.spin = 0.0;
        s.flip = 0.0;
        s.grabTicks = 0;
        s.crouch = Math.min(1.0, s.crouch + impact * 1.2);
    }

    private static boolean blocked(TerrainSampler t, double x, double y, double z) {
        int bx = floor(x);
        int bz = floor(z);
        return t.solidAt(bx, floor(y + 1.05), bz) || t.solidAt(bx, floor(y + 1.65), bz);
    }

    // ------------------------------------------------------------------------------------------------ visuals

    private static void updateVisuals(SkiState s, SkiParams p) {
        double targetLean = 0.0;
        if (s.grounded) {
            double omega = Math.toRadians(s.turn);
            double centripetal = s.horizontalSpeed() * omega;
            // leaning into the turn: turning right (positive) means leaning right (negative)
            targetLean = -Math.toDegrees(Math.atan2(centripetal, p.gravity()));
            targetLean = MathUtil.clamp(targetLean, -50.0, 50.0);
        }
        s.lean += (targetLean - s.lean) * 0.35;

        double targetCrouch;
        if (!s.grounded) {
            targetCrouch = s.tucking ? 0.9 : 0.35;
        } else if (s.jumpCharge > 0) {
            targetCrouch = 0.4 + 0.6 * s.jumpCharge / (double) Math.max(1, p.maxJumpCharge());
        } else if (s.tucking) {
            targetCrouch = 1.0;
        } else if (s.braking) {
            targetCrouch = 0.35;
        } else {
            targetCrouch = 0.12 + Math.min(0.2, Math.abs(s.lean) / 200.0);
        }
        s.crouch += (targetCrouch - s.crouch) * 0.3;
    }

    private static int floor(double d) {
        return (int) Math.floor(d);
    }
}
