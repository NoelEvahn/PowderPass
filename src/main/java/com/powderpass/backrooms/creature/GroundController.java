package com.powderpass.backrooms.creature;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * The physical body of a possessed player: a grounded, collision-aware point mass. It walks where the AI tells
 * it, steps up single blocks, cannot pass through walls, falls with gravity when nothing is under it, floats down
 * slowly in water, and never leaves loaded chunks. The player is then pulled along to it (see {@link Possession}),
 * so the player's own client also collides with real blocks and nothing ever clips.
 * <p>
 * Its footprint is a box {@link #HALF} x2 wide and {@link #height} tall from {@link #y} (the ground under the
 * hovering player), so it only goes where the hovering body fits.
 */
final class GroundController implements TentacleRig.Ground {
    /** Half the footprint: a little wider than a player (0.3), so the player never brushes what the body passes. */
    static final double HALF = 0.36;
    static final double GRAVITY = 0.08, DRAG = 0.98, TERMINAL = -1.6;

    private final World world;
    double x, y, z;
    double vy;
    boolean onGround, inWater;
    /** Clearance the body needs (player height plus the smallest hover). */
    double height = 1.95;
    /** Smoothed y for the visible body: step-ups glide instead of snapping. */
    double smoothY;
    /** Distance moved horizontally by the last {@link #move}. */
    double lastMoved;
    /** The last horizontal move was blocked (by a wall the step-up could not climb). */
    boolean blocked;

    GroundController(World world, double x, double y, double z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.smoothY = y;
    }

    World world() {
        return world;
    }

    // ------------------------------------------------------------------ block queries

    private boolean loaded(int bx, int bz) {
        return world.isChunkLoaded(bx >> 4, bz >> 4);
    }

    /** Collides (unloaded chunks count as solid, so the body never wanders into them). */
    boolean solid(int bx, int by, int bz) {
        if (!loaded(bx, bz)) return true;
        if (by < world.getMinHeight()) return true;
        if (by >= world.getMaxHeight()) return false;
        Block b = world.getBlockAt(bx, by, bz);
        return !b.isPassable();
    }

    private boolean water(int bx, int by, int bz) {
        if (!loaded(bx, bz)) return false;
        Material m = world.getBlockAt(bx, by, bz).getType();
        return m == Material.WATER || m == Material.BUBBLE_COLUMN;
    }

    /** Is the body's box at (cx, cy, cz) free of solid blocks? */
    boolean fits(double cx, double cy, double cz, double h) {
        int x0 = (int) Math.floor(cx - HALF), x1 = (int) Math.floor(cx + HALF);
        int z0 = (int) Math.floor(cz - HALF), z1 = (int) Math.floor(cz + HALF);
        int y0 = (int) Math.floor(cy + 0.01), y1 = (int) Math.floor(cy + h - 0.01);
        for (int bx = x0; bx <= x1; bx++)
            for (int bz = z0; bz <= z1; bz++)
                for (int by = y0; by <= y1; by++) if (solid(bx, by, bz)) return false;
        return true;
    }

    /** Something solid right under the footprint. */
    boolean supported(double cx, double cy, double cz) {
        int by = (int) Math.floor(cy - 0.02);
        int x0 = (int) Math.floor(cx - HALF + 0.05), x1 = (int) Math.floor(cx + HALF - 0.05);
        int z0 = (int) Math.floor(cz - HALF + 0.05), z1 = (int) Math.floor(cz + HALF - 0.05);
        for (int bx = x0; bx <= x1; bx++)
            for (int bz = z0; bz <= z1; bz++) if (solid(bx, by, bz)) return true;
        return false;
    }

    /** Free height above the body's ground (up to {@code max}). */
    double clearance(double max) {
        double c = ceilingY(x, y + 0.05, z, max);
        return Math.min(max, c - y);
    }

    @Override
    public double groundY(double gx, double fromY, double gz, double depth) {
        int bx = (int) Math.floor(gx), bz = (int) Math.floor(gz);
        if (!loaded(bx, bz)) return Double.NaN;
        int top = (int) Math.floor(fromY);
        for (int by = top; by >= fromY - depth; by--) {
            if (solid(bx, by, bz) && !solid(bx, by + 1, bz)) return by + 1;
            if (water(bx, by, bz) && !water(bx, by + 1, bz) && !solid(bx, by + 1, bz)) return by + 0.9; // tentacles rest on the surface
        }
        return Double.NaN;
    }

    @Override
    public double ceilingY(double gx, double fromY, double gz, double range) {
        int bx = (int) Math.floor(gx), bz = (int) Math.floor(gz);
        for (int by = (int) Math.floor(fromY); by <= fromY + range; by++) {
            if (solid(bx, by, bz)) return by;
        }
        return Double.POSITIVE_INFINITY;
    }

    // ------------------------------------------------------------------ motion

    /**
     * Walk by (dx, dz) this tick: axis by axis, stepping up one block where the way is blocked but the step is
     * free, then gravity. Never moves into a box the body does not fit.
     */
    void move(double dx, double dz) {
        double sx = x, sz = z;
        blocked = false;
        inWater = water((int) Math.floor(x), (int) Math.floor(y + 0.3), (int) Math.floor(z));
        // split long moves so nothing tunnels through a thin wall
        int steps = (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dz)) / 0.3);
        steps = Math.max(1, Math.min(steps, 6));
        for (int i = 0; i < steps; i++) {
            axis(dx / steps, 0);
            axis(0, dz / steps);
        }
        // gravity
        if (supported(x, y, z) && vy <= 0) {
            vy = 0;
            onGround = true;
            // stand exactly on the surface
            double top = Math.floor(y + 0.02);
            if (y - top < 0.05 && fits(x, top, z, height)) y = top;
        } else {
            onGround = false;
            vy = inWater ? Math.max((vy - 0.02) * 0.8, -0.12) : Math.max(TERMINAL, (vy - GRAVITY) * DRAG);
            double ny = y + vy;
            if (vy < 0) {
                // land on whatever is below
                int from = (int) Math.floor(y - 0.02), to = (int) Math.floor(ny);
                for (int by = from; by >= to; by--) {
                    if (supported(x, by + 1, z)) {
                        if (by + 1 > ny) {
                            ny = by + 1;
                            vy = 0;
                            onGround = true;
                        }
                        break;
                    }
                }
            } else if (!fits(x, ny, z, height)) {
                vy = 0;
                ny = y;
            }
            y = ny;
        }
        double target = y;
        // the visible body glides up steps, but drops with the fall
        smoothY = target > smoothY ? Math.min(target, smoothY + 0.22) : target;
        lastMoved = Math.hypot(x - sx, z - sz);
    }

    private void axis(double dx, double dz) {
        if (dx == 0 && dz == 0) return;
        double nx = x + dx, nz = z + dz;
        if (fits(nx, y, nz, height)) {
            // never walk off into a drop deeper than 3 blocks while on the ground: it is a creature, not a lemming
            if (onGround && !supported(nx, y, nz) && dropBelow(nx, y, nz) > 3) {
                blocked = true;
                return;
            }
            x = nx;
            z = nz;
            return;
        }
        if ((onGround || inWater) && fits(x, y + 1, z, height) && fits(nx, y + 1, nz, height) && supported(nx, y + 1, nz)) {
            y += 1; // climb a single block
            x = nx;
            z = nz;
            return;
        }
        blocked = true;
    }

    /** How far down the ground is from (cx, cy, cz), up to 8. */
    double dropBelow(double cx, double cy, double cz) {
        for (int d = 1; d <= 8; d++) if (supported(cx, cy - d, cz)) return d;
        return 9;
    }

    /** Put the body somewhere directly (on a teleport or at the start). */
    void reset(double nx, double ny, double nz) {
        x = nx;
        y = ny;
        z = nz;
        vy = 0;
        smoothY = ny;
        // settle onto the ground under it, within a few blocks
        for (int d = 0; d < 4 && !supported(x, y, z); d++) {
            if (fits(x, y - 1, z, height)) y -= 1;
            else break;
        }
        if (supported(x, y, z)) {
            double top = Math.floor(y + 0.02);
            if (y - top < 0.05) y = top;
        }
        smoothY = y;
    }
}
