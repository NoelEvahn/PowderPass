package com.powderpass.backrooms.gen;

import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.util.Rng;
import org.bukkit.Material;

/**
 * Generic painter for levels made of rooms separated by wall lines with doorways. Subclasses supply
 * the palette ({@link #style}), interior furnishing and authored feature interiors.
 */
public abstract class RoomLevel extends LevelGenerator {

    protected RoomLevel(Level level, long seed, GenSettings gs, int cellSize, int regionCells, int districtRegions,
                        int[] floorY, int bottomY, int roofY, double loopChance, double echoChance) {
        super(level, seed, gs, cellSize, regionCells, districtRegions, floorY, bottomY, roofY, loopChance, echoChance);
    }

    protected abstract Style style(int floor, int cx, int cz);

    /** Furnish the interior (u,v in 1..S-1). Called after shell + walls. */
    protected abstract void interior(CellCtx c, Style st);

    /** Paint an authored feature cell interior. Called instead of {@link #interior}. */
    protected abstract void feature(CellCtx c, Style st, Feature f);

    /** Bottom Y of this floor's painted slice. */
    protected int sliceBottom(int floor) {
        return floor == 0 ? bottomY : floorY[floor] - 1;
    }

    /** Top Y of this floor's painted slice. */
    protected int sliceTop(int floor) {
        return floor == floorY.length - 1 ? roofY : floorY[floor + 1] - 2;
    }

    @Override
    protected void paintCell(CellCtx c) {
        Style st = style(c.floor, c.cx, c.cz);
        int bottom = sliceBottom(c.floor);
        int top = sliceTop(c.floor);
        for (int u = c.u0; u <= c.u1; u++) {
            for (int v = c.v0; v <= c.v1; v++) {
                if (c.floor == 0) {
                    c.set(u, bottom, v, B.BEDROCK);
                    for (int y = bottom + 1; y < c.fy; y++) c.set(u, y, v, st.under);
                } else {
                    for (int y = bottom; y < c.fy; y++) c.set(u, y, v, st.under);
                }
                c.set(u, c.fy, v, floorBlock(c, st, u, v));
                for (int y = c.fy + 1; y < st.ceilY; y++) c.set(u, y, v, B.AIR);
                c.set(u, st.ceilY, v, ceilBlock(c, st, u, v));
                for (int y = st.ceilY + 1; y < top; y++) c.set(u, y, v, st.above);
                c.set(u, top, v, c.floor == floorY.length - 1 ? B.BEDROCK : st.above);
            }
        }
        paintWalls(c, st);
        Feature f = featureAt(c.floor, c.cx, c.cz);
        if (f != null) {
            feature(c, st, f);
        } else {
            interior(c, st);
        }
    }

    protected void paintWalls(CellCtx c, Style st) {
        Door west = west(c.floor, c.cx, c.cz);
        Door north = north(c.floor, c.cx, c.cz);
        boolean corner = !cornerOpen(c.floor, c.cx, c.cz) || st.keepCorner;
        if (corner) {
            wallColumn(c, st, 0, 0, 0, null);
            if (st.pillar2x2 && cornerOpen(c.floor, c.cx, c.cz)) {
                wallColumn(c, st, 1, 0, 1, null);
                wallColumn(c, st, 0, 1, 1, null);
                wallColumn(c, st, 1, 1, 1, null);
            }
        }
        for (int v = 1; v < S; v++) {
            wallColumn(c, st, 0, v, v, west);
        }
        for (int u = 1; u < S; u++) {
            wallColumn(c, st, u, 0, u, north);
        }
    }

    /** One column of a wall line; {@code along} is the position along the line, used for door + stripes. */
    protected void wallColumn(CellCtx c, Style st, int u, int v, int along, Door door) {
        if (!c.in(u, v)) {
            return;
        }
        int openTop = c.fy;
        if (door != null && door.covers(along)) {
            openTop = door.full() ? st.ceilY - 1 : c.fy + st.doorH;
            openTop = Math.min(openTop, st.ceilY - 1);
            if (door.full() && st.doorH >= st.ceilY - c.fy - 1) {
                openTop = st.ceilY - 1;
            }
            c.set(u, c.fy, v, doorFloor(c, st, u, v));
        }
        for (int y = openTop + 1; y < st.ceilY; y++) {
            c.set(u, y, v, wallBlock(c, st, u, y, v, along));
        }
        if (openTop >= c.fy + 1) {
            for (int y = c.fy + 1; y <= openTop; y++) c.set(u, y, v, B.AIR);
        }
    }

    protected B doorFloor(CellCtx c, Style st, int u, int v) {
        return floorBlock(c, st, u, v);
    }

    protected B wallBlock(CellCtx c, Style st, int u, int y, int v, int along) {
        if (y == c.fy + 1 && st.base != null) {
            return st.base;
        }
        if (y == st.ceilY - 1 && st.trim != null) {
            return st.trim;
        }
        if (st.wallStain > 0 && st.wallStainBlock != null && Rng.chance(c.h(u, y, v, 0x57A1), st.wallStain)) {
            return st.wallStainBlock;
        }
        if (st.stripe > 0 && st.wallAlt != null && Math.floorMod(c.wx(u) + c.wz(v), st.stripe) == 0) {
            return st.wallAlt;
        }
        return st.wall;
    }

    protected B floorBlock(CellCtx c, Style st, int u, int v) {
        if (st.floorAlt != null && Rng.chance(c.h(u, 0, v, 0xF100), st.floorAltChance)) {
            return st.floorAlt;
        }
        return st.floor;
    }

    protected boolean isLightPos(CellCtx c, Style st, int u, int v) {
        if (st.light == null || u == 0 || v == 0) {
            return false;
        }
        int wx = c.wx(u), wz = c.wz(v);
        int mx = Math.floorMod(wx - st.lightOffX, st.lightEveryX);
        return mx < st.lightLength && Math.floorMod(wz - st.lightOffZ, st.lightEveryZ) == 0;
    }

    protected B ceilBlock(CellCtx c, Style st, int u, int v) {
        if (isLightPos(c, st, u, v)) {
            int wx = c.wx(u), wz = c.wz(v);
            long h = Rng.hash(seed ^ 0x1167, Math.floorDiv(wx - st.lightOffX, st.lightEveryX), Math.floorDiv(wz, st.lightEveryZ), c.floor);
            return Rng.chance(h, st.lit) ? st.light : (st.deadLight != null ? st.deadLight : st.ceil);
        }
        if (st.ceilAlt != null && (Math.floorMod(c.wx(u), 4) == 0 || Math.floorMod(c.wz(v), 4) == 0)) {
            return st.ceilAlt;
        }
        return st.ceil;
    }

    // --------------------------------------------------------------- helpers for subclasses

    /** True if no doorway on the perpendicular wall lines would be blocked by filling {@code depth} columns. */
    protected boolean thickWallFits(int floor, int cx, int cz, int side, int depth) {
        Door a, b;
        if (side == 0 || side == 2) {
            a = north(floor, cx, cz);
            b = south(floor, cx, cz);
        } else {
            a = west(floor, cx, cz);
            b = east(floor, cx, cz);
        }
        return fitsDoor(a, side, depth) && fitsDoor(b, side, depth);
    }

    private boolean fitsDoor(Door d, int side, int depth) {
        if (!d.open()) {
            return true;
        }
        if (side == 0 || side == 1) {
            return d.end() >= depth + 1;
        }
        return d.start() <= S - 1 - depth - 1;
    }

    /** Whether the given side of the cell is closed (0=west,1=north,2=east,3=south). */
    protected boolean closed(int floor, int cx, int cz, int side) {
        return !sideDoor(floor, cx, cz, side).open();
    }

    protected Door sideDoor(int floor, int cx, int cz, int side) {
        return switch (side) {
            case 0 -> west(floor, cx, cz);
            case 1 -> north(floor, cx, cz);
            case 2 -> east(floor, cx, cz);
            default -> south(floor, cx, cz);
        };
    }

    /**
     * Converts (along, depth) relative to a side into cell-local (u,v). depth 1 = first interior row next to
     * that side's wall line. Side 2/3 walls belong to the neighbour, so their first interior row is S-1.
     */
    protected int[] sideToUV(int side, int along, int depth) {
        return switch (side) {
            case 0 -> new int[]{depth, along};
            case 1 -> new int[]{along, depth};
            case 2 -> new int[]{S - depth, along};
            default -> new int[]{along, S - depth};
        };
    }

    /** World-space anchor at the face of a wall (side), position along, height y. */
    protected double[] wallFace(int cx, int cz, int side, double along, double y) {
        double ox = cx * S, oz = cz * S;
        return switch (side) {
            case 0 -> new double[]{ox + 1.0, y, oz + along};
            case 1 -> new double[]{ox + along, y, oz + 1.0};
            case 2 -> new double[]{ox + S, y, oz + along};
            default -> new double[]{ox + along, y, oz + S};
        };
    }

    /** Generic "stairs going down into a pit" transition structure used by several levels. */
    protected void stairPit(CellCtx c, Style st, B wall, B floorB, Material stairMat, B light) {
        int y0 = c.fy;
        // pit: u 3..5, steps at v=2..4 descending south, landing v=5..6 at depth 3
        for (int u = 2; u <= 6; u++) {
            for (int v = 1; v <= 7; v++) {
                boolean inside = u >= 3 && u <= 5 && v >= 2 && v <= 6;
                if (!inside && (u == 2 || u == 6 || v == 7) && v >= 2) {
                    for (int y = y0 - 3; y <= y0; y++) c.set(u, y, v, wall);
                }
            }
        }
        for (int u = 3; u <= 5; u++) {
            for (int k = 0; k < 3; k++) {
                int v = 2 + k;
                int sy = y0 - k;
                c.set(u, sy, v, B.stairs(stairMat, "north", false));
                for (int y = sy - 1; y >= y0 - 3; y--) c.set(u, y, v, wall);
                for (int y = sy + 1; y <= y0; y++) c.set(u, y, v, B.AIR);
            }
            for (int v = 5; v <= 6; v++) {
                c.set(u, y0 - 4, v, wall);
                c.set(u, y0 - 3, v, floorB);
                for (int y = y0 - 2; y <= y0; y++) c.set(u, y, v, B.AIR);
            }
        }
        c.set(4, y0 - 1, 7, light);
    }

    /** Trigger box for {@link #stairPit}: the landing. */
    protected static int[] stairPitTrigger() {
        return new int[]{3, -3, 5, 5, -1, 6};
    }

    /** A 3x3 booth (elevator / closet) centred in the cell, open on the south side. Interior is u=4,v=4. */
    protected void booth(CellCtx c, B wall, B floorB, B ceil, B light) {
        int y0 = c.fy;
        for (int u = 3; u <= 5; u++) {
            for (int v = 3; v <= 5; v++) {
                boolean edge = u == 3 || u == 5 || v == 3;
                for (int y = y0 + 1; y <= y0 + 3; y++) {
                    c.set(u, y, v, edge ? wall : B.AIR);
                }
                c.set(u, y0 + 4, v, ceil);
                c.set(u, y0, v, floorB);
            }
        }
        c.set(4, y0 + 4, 4, light);
    }

    protected static int[] boothTrigger() {
        return new int[]{4, 0, 4, 4, 1, 4};
    }
}
