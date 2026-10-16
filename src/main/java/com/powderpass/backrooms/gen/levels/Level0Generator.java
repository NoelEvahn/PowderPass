package com.powderpass.backrooms.gen.levels;

import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.gen.B;
import com.powderpass.backrooms.gen.CellCtx;
import com.powderpass.backrooms.gen.CustomBlocks;
import com.powderpass.backrooms.gen.DecorSpec;
import com.powderpass.backrooms.gen.Dest;
import com.powderpass.backrooms.gen.Door;
import com.powderpass.backrooms.gen.Feature;
import com.powderpass.backrooms.gen.FeatureDef;
import com.powderpass.backrooms.gen.GenSettings;
import com.powderpass.backrooms.gen.RoomLevel;
import com.powderpass.backrooms.gen.Style;
import com.powderpass.backrooms.util.Rng;
import com.powderpass.backrooms.util.ValueNoise;
import org.bukkit.Material;

import java.util.List;

/**
 * Level 0 — The Lobby. Mono-yellow wallpaper, damp mustard carpet, a drop ceiling of stained tiles and recessed
 * fluorescent troffers, all custom textured blocks. Every 64 x 64 region is one of four kinds of space:
 * <ul>
 *   <li><b>Rooms</b> (most of it): 4-block-tall rooms on an 8-block grid of wall lines with doorways. Short wall
 *   sections jut out of the main walls (fins, pilasters, thickened stretches) but never into a doorway's
 *   approach or the room's centre, so the walk through a room is the same with or without them.</li>
 *   <li><b>Long halls</b>: parallel 3-wide corridors running the whole region (and on into the next hall region),
 *   joined by two cross passages.</li>
 *   <li><b>Pillar fields</b>: one open floor with a 1 x 1 column every four blocks, floor to ceiling.</li>
 *   <li><b>Empty expanses</b> ({@code empty-expanse-chance}): 320 x 320 blocks with no walls at all, just carpet,
 *   ceiling and lights stretching away.</li>
 *   <li><b>Tall halls</b>: the same carpet and wallpaper, 21 blocks high, with 3 x 3 columns; its lights are far
 *   overhead and invisible lights hang in the air below them.</li>
 * </ul>
 * Lights are flush ceiling troffers (a panel block above an invisible light); whole dead sectors have failed, a
 * few panels stutter. The only way on is a rare cold seam into the Poolrooms; there is no exit here.
 */
public final class Level0Generator extends RoomLevel {
    /** The upper floor (where everyone arrives) and the lower floor 24 blocks under it. */
    private static final int FLOOR = 64, LOWER = 40;
    public static final int DOWN = 0, UP = 1;
    /** Ceiling heights above a floor. */
    private static final int CEIL = 5, CEIL_TALL_PILLARS = 9, CEIL_TALL = 22;
    /** The lower floor's shafts reach down to here; below {@link #LOOP_Y} a faller is carried back up. */
    public static final int BOTTOM = 10, LOOP_Y = 22;
    /** Top of the dark shafts above the upper floor's ceiling that fallers re-enter through. */
    public static final int CEILING_SHAFT_TOP = FLOOR + 27;
    private static final int DOOR_H = 3;

    public enum Zone { ROOMS, HALLS, PILLARS, TALL, EXPANSE }

    static final int K_PLAIN = 0, K_FINS = 1, K_THICK = 2, K_PILLAR = 3;
    private static final String[] KINDS = {"room", "room/fins", "room/thick-wall", "room/pillar"};

    private static final B WALL = CustomBlocks.get("l0_wall");
    private static final B WALL_B = CustomBlocks.get("l0_wall_b");
    private static final B WALL_STAIN = CustomBlocks.get("l0_wall_stain");
    private static final B CARPET = CustomBlocks.get("l0_carpet");
    private static final B CARPET_WET = CustomBlocks.get("l0_carpet_wet");
    private static final B TILES = CustomBlocks.get("l0_ceiling");
    private static final B TILES_STAIN = CustomBlocks.get("l0_ceiling_stain");
    private static final B PANEL = CustomBlocks.get("light_panel");
    private static final B PANEL_OFF = CustomBlocks.get("light_panel_off");
    private static final B SEAM_TILE = CustomBlocks.get("pool_tile");
    private static final B UNDER = B.of(Material.STONE);

    public Level0Generator(long seed, GenSettings gs) {
        super(Level.LEVEL_0, seed, gs, 8, 8, 3, new int[]{LOWER, FLOOR}, BOTTOM, FLOOR + 30, 0.16, gs.get("repeat-chance", 0.06));
    }

    @Override
    protected List<FeatureDef> featureDefs() {
        return List.of(
                // rare on purpose: roughly one cold seam per 550 blocks, and nothing else leads out
                FeatureDef.transition("seam", Dest.to(Level.POOLROOMS), gs.get("poolrooms-chance", 0.35)).withEffect("intent").onFloor(UP),
                FeatureDef.landmark("phone_desk", gs.get("landmark-chance", 0.6)).onFloor(UP),
                FeatureDef.landmark("chair_circle", gs.get("landmark-chance", 0.6) * 0.6).onFloor(UP));
    }

    @Override
    public String biomeKey() {
        return "minecraft:swamp";
    }

    // ================================================================== zones

    /** Empty expanses span 5 x 5 regions (320 blocks): nothing but carpet, ceiling and lights. Absent from older worlds. */
    private static final int EXPANSE_REGIONS = 5;

    private boolean expanse(int floor, int rx, int rz) {
        double c = gs.get("empty-expanse-chance", 0.0) * (floor == DOWN ? 0.6 : 1);
        return c > 0 && Rng.chance(Rng.hash(seed ^ 0xE4BA5EL ^ floor * 0x77L, Math.floorDiv(rx, EXPANSE_REGIONS), Math.floorDiv(rz, EXPANSE_REGIONS)), c);
    }

    /**
     * The kind of space of a region on a floor. The lower floor has its own layout: no tall halls (the upper floor
     * is right above), more long halls and pillar fields, and it is darker.
     */
    Zone zoneOf(int floor, int rx, int rz) {
        if (expanse(floor, rx, rz)) return Zone.EXPANSE;
        double r = Rng.unit(Rng.hash(seed ^ 0x2071L ^ floor * 0x5151L, rx, rz));
        double halls = gs.get("hallway-chance", 0.14), pillars = gs.get("pillar-field-chance", 0.13), tall = gs.get("tall-hall-chance", 0.08);
        if (floor == DOWN) {
            halls *= 1.5;
            pillars *= 1.4;
            tall = 0;
        }
        if (r < halls) return Zone.HALLS;
        if (r < halls + pillars) return Zone.PILLARS;
        if (r < halls + pillars + tall) return Zone.TALL;
        return Zone.ROOMS;
    }

    /** Zone of a cell on the upper floor (where everyone is most of the time). */
    public Zone zoneOfCell(int cx, int cz) {
        return zoneOfCell(UP, cx, cz);
    }

    public Zone zoneOfCell(int floor, int cx, int cz) {
        return zoneOf(floor, Math.floorDiv(cx, R), Math.floorDiv(cz, R));
    }

    /** Hall regions run along X (true) or Z. */
    private boolean hallsAlongX(int floor, int rx, int rz) {
        return Rng.chance(Rng.hash(seed ^ 0x4A11EL ^ floor * 0x31L, rx, rz), 0.5);
    }

    /** Cross passages between parallel halls, at two positions along the hall axis. */
    private boolean spine(int floor, int rx, int rz, int along) {
        int s = Rng.pick(Rng.hash(seed ^ 0x5B1EL ^ floor * 0x31L, rx, rz), R / 2);
        return along == s || along == s + R / 2;
    }

    @Override
    protected boolean mergedRegion(int floor, int rx, int rz) {
        Zone z = zoneOf(floor, rx, rz);
        return z == Zone.PILLARS || z == Zone.TALL || z == Zone.EXPANSE;
    }

    /** Both cells lie in an empty expanse: no wall between them at all, even across region borders. */
    private boolean expanseLink(int floor, int ax, int az, int bx, int bz) {
        return zoneOfCell(floor, ax, az) == Zone.EXPANSE && zoneOfCell(floor, bx, bz) == Zone.EXPANSE;
    }

    private boolean hallLink(int floor, int ax, int az, int bx, int bz) {
        return zoneOfCell(floor, ax, az) == Zone.HALLS && zoneOfCell(floor, bx, bz) == Zone.HALLS;
    }

    @Override
    public boolean edgeOpen(int floor, int cx, int cz, boolean east) {
        int bx = east ? cx + 1 : cx, bz = east ? cz : cz + 1;
        if (expanseLink(floor, cx, cz, bx, bz)) return true;
        if (hallLink(floor, cx, cz, bx, bz)) {
            int rax = Math.floorDiv(cx, R), raz = Math.floorDiv(cz, R), rbx = Math.floorDiv(bx, R), rbz = Math.floorDiv(bz, R);
            boolean ax = hallsAlongX(floor, rax, raz);
            if (rax == rbx && raz == rbz) {
                if (east == ax) return true; // along a hall
                return spine(floor, rax, raz, ax ? Math.floorMod(cx, R) : Math.floorMod(cz, R)); // a cross passage
            }
            // the next region is halls running the same way: the halls carry straight on
            if (ax == hallsAlongX(floor, rbx, rbz) && east == ax) return true;
        }
        return super.edgeOpen(floor, cx, cz, east);
    }

    @Override
    public Door door(int floor, int cx, int cz, boolean east) {
        int bx = east ? cx + 1 : cx, bz = east ? cz : cz + 1;
        if (expanseLink(floor, cx, cz, bx, bz)) return new Door(true, 1, S - 1, true);
        if (hallLink(floor, cx, cz, bx, bz)) return edgeOpen(floor, cx, cz, east) ? new Door(true, 3, 3, false) : Door.CLOSED;
        return super.door(floor, cx, cz, east);
    }

    @Override
    protected double[] doorParams(int floor) {
        return new double[]{0.28, 1, 4};
    }

    int ceilY(int floor, int cx, int cz) {
        int fy = floorY[floor];
        Zone z = zoneOfCell(floor, cx, cz);
        if (z == Zone.TALL) return fy + CEIL_TALL;
        if (z == Zone.PILLARS && Rng.chance(Rng.hash(seed ^ 0x7A11L ^ floor, Math.floorDiv(cx, R), Math.floorDiv(cz, R)), 0.35)) return fy + CEIL_TALL_PILLARS;
        return fy + CEIL;
    }

    /** Dead sectors: most panels have failed. The lower floor has far more of them. */
    private boolean dark(int floor, int cx, int cz) {
        double chance = gs.get("dark-zone-chance", 0.2) * (floor == DOWN ? 1.8 : 1.0);
        return zone(cx, cz, 0xDA4CL ^ floor * 0x1D1L, 90) > 1.0 - chance * 0.6;
    }

    private boolean damp(int cx, int cz) {
        return zone(cx, cz, 0x9A1L, 72) > 0.72;
    }

    /** Pilaster rhythm: in some neighbourhoods every closed wall has a column bump in its middle. */
    private boolean pilasters(int cx, int cz) {
        return zone(cx, cz, 0x91A5L, 80) > 0.6;
    }

    int kind(int floor, int cx, int cz) {
        if (zoneOfCell(floor, cx, cz) != Zone.ROOMS) return K_PLAIN;
        double r = Rng.unit(ch(floor, cx, cz, 0x4B1D));
        if (r < gs.get("thick-wall-chance", 0.1)) return K_THICK;
        if (r < 0.16) return K_PILLAR;
        if (r < 0.82) return K_FINS;
        return K_PLAIN;
    }

    // ================================================================== layout (pure)

    /**
     * What stands inside a cell, floor to ceiling: true = wall. Covers u,v in 0..S-1; wall lines (u=0 / v=0) are
     * only ever set here in the open (merged) zones, where they are columns, never in rooms or halls.
     */
    boolean[] layout(int floor, int cx, int cz) {
        boolean[] s = new boolean[S * S];
        if (featureAt(floor, cx, cz) != null) return s;
        switch (zoneOfCell(floor, cx, cz)) {
            case ROOMS -> rooms(floor, cx, cz, s);
            case HALLS -> halls(floor, cx, cz, s);
            case PILLARS -> {
                // a column every four blocks, but never in a doorway of the field's outer walls
                boolean westEdge = Math.floorMod(cx, R) == 0, northEdge = Math.floorMod(cz, R) == 0;
                for (int u = 0; u < S; u++) for (int v = 0; v < S; v++) {
                    if ((u == 0 && westEdge) || (v == 0 && northEdge)) continue;
                    if (Math.floorMod(cx * S + u, 4) == 0 && Math.floorMod(cz * S + v, 4) == 0) s[u * S + v] = true;
                }
            }
            case TALL -> {
                for (int u = 0; u < S; u++) for (int v = 0; v < S; v++) {
                    int mx = Math.floorMod(cx * S + u, 16), mz = Math.floorMod(cz * S + v, 16);
                    if (mx >= 6 && mx <= 8 && mz >= 6 && mz <= 8) s[u * S + v] = true;
                }
            }
        }
        return s;
    }

    private void rooms(int floor, int cx, int cz, boolean[] s) {
        int k = kind(floor, cx, cz);
        long h = ch(floor, cx, cz, 0x1A7E);
        switch (k) {
            case K_THICK -> {
                int side = Rng.pick(h, 4);
                for (int i = 0; i < 4; i++, side = (side + 1) % 4) {
                    int depth = 1 + Rng.pick(Rng.hash(h, 7), 2);
                    if (closed(floor, cx, cz, side) && thickWallFits(floor, cx, cz, side, depth)) {
                        for (int d = 1; d <= depth; d++) for (int a = 1; a < S; a++) mark(s, side, a, d);
                        break;
                    }
                }
            }
            case K_PILLAR -> {
                for (int u = 3; u <= 4; u++) for (int v = 3; v <= 4; v++) s[u * S + v] = true;
            }
            case K_FINS -> fins(floor, cx, cz, h, s);
            default -> {
            }
        }
        if (k != K_THICK && pilasters(cx, cz)) {
            for (int side = 0; side < 4; side++) {
                Door d = sideDoor(floor, cx, cz, side);
                if (!d.open() || d.end() < 3 || d.start() > 5) mark(s, side, 4, 1);
            }
        }
    }

    /**
     * Sections that come out of the main walls: a fin (a short wall at right angles), a pilaster (one block) or a
     * thickened stretch. They stay at least one block clear of every doorway on their own wall and out of the
     * room's middle 3 x 3, so they never change the route through the room.
     */
    private void fins(int floor, int cx, int cz, long h, boolean[] s) {
        for (int side = 0; side < 4; side++) {
            Door d = sideDoor(floor, cx, cz, side);
            long hs = Rng.hash(h, 100 + side);
            if (!Rng.chance(hs, d.open() ? 0.45 : 0.7)) continue;
            int shape = Rng.pick(Rng.hash(hs, 1), 3);
            int a = 2 + Rng.pick(Rng.hash(hs, 2), 5);           // 2..6 along the wall
            int len = shape == 2 ? 3 : 1;                       // a thickened stretch is three long
            if (a + len - 1 > 6) a = 6 - len + 1;
            if (d.open() && a + len - 1 >= d.start() - 1 && a <= d.end() + 1) continue;
            int depth = shape == 0 ? 2 : 1;                     // fins reach two blocks into the room
            for (int i = 0; i < len; i++) for (int dd = 1; dd <= depth; dd++) mark(s, side, a + i, dd);
        }
    }

    private void mark(boolean[] s, int side, int along, int depth) {
        int[] uv = sideToUV(side, along, depth);
        s[uv[0] * S + uv[1]] = true;
    }

    /** Halls: solid except a 3-wide corridor through the middle along the hall axis and a way to every door. */
    private void halls(int floor, int cx, int cz, boolean[] s) {
        java.util.Arrays.fill(s, true);
        for (int side = 0; side < 4; side++) {
            Door d = sideDoor(floor, cx, cz, side);
            if (!d.open()) continue;
            int a0 = Math.min(d.start(), 3), a1 = Math.max(d.end(), 5);
            // the doorway, then straight to the middle three
            for (int a = d.start(); a <= d.end(); a++) for (int dep = 1; dep <= 4; dep++) carve(s, side, a, dep);
            for (int a = a0; a <= a1; a++) for (int dep = 3; dep <= 5; dep++) carve(s, side, a, dep);
        }
        for (int u = 3; u <= 5; u++) for (int v = 3; v <= 5; v++) s[u * S + v] = false;
        // wall lines are painted separately; keep the line slots out of the solid map
        for (int i = 0; i < S; i++) {
            s[i] = false;
            s[i * S] = false;
        }
    }

    private void carve(boolean[] s, int side, int along, int depth) {
        if (along < 1 || along > S - 1 || depth < 1 || depth > S - 1) return;
        int[] uv = sideToUV(side, along, depth);
        s[uv[0] * S + uv[1]] = false;
    }

    // ================================================================== painting

    @Override
    protected Style style(int floor, int cx, int cz) {
        Style st = new Style();
        st.name = zoneOfCell(floor, cx, cz).name().toLowerCase(java.util.Locale.ROOT) + (dark(floor, cx, cz) ? "/dead-sector" : damp(cx, cz) ? "/damp" : "");
        st.under = UNDER;
        st.above = UNDER;
        st.wall = WALL;
        st.floor = CARPET;
        st.ceil = TILES;
        st.ceilY = ceilY(floor, cx, cz);
        st.doorH = DOOR_H;
        return st;
    }

    @Override
    public String zoneName(int floor, int cx, int cz) {
        Zone z = zoneOfCell(floor, cx, cz);
        return style(floor, cx, cz).name + (z == Zone.ROOMS ? " " + KINDS[kind(floor, cx, cz)] : "");
    }

    @Override
    public int[] safeLocal(int floor, int cx, int cz) {
        return zoneOfCell(floor, cx, cz) == Zone.PILLARS ? new int[]{2, 2} : new int[]{S / 2, S / 2};
    }

    /** Everyone arrives on the upper floor. */
    @Override
    public int[] safeSpot(int x, int z, int preferredFloor) {
        return super.safeSpot(x, z, UP);
    }

    @Override
    public boolean isSafeCell(int floor, int cx, int cz) {
        int[] l = safeLocal(floor, cx, cz);
        return !layout(floor, cx, cz)[l[0] * S + l[1]];
    }

    @Override
    protected void paintCell(CellCtx c) {
        Style st = style(c.floor, c.cx, c.cz);
        int ceil = st.ceilY;
        int top;
        boolean damp = damp(c.cx, c.cz) && !hasHoles(c.floor, c.cx, c.cz) && !hasShaft(c.floor, c.cx, c.cz);
        boolean warm = zone(c.cx, c.cz, 0x7E11L, 48) > 0.68;
        B wall = warm ? WALL_B : WALL;
        // ---- shell: each floor fills its own band (the lower floor from the bedrock up to just under the upper
        // floor's carpet, the upper floor from its carpet to the roof): floor, air, ceiling, filler
        boolean lower = c.floor == DOWN;
        if (!lower) top = roofY;
        else top = floorY[UP];
        for (int u = c.u0; u <= c.u1; u++) {
            for (int v = c.v0; v <= c.v1; v++) {
                if (lower) {
                    c.set(u, bottomY, v, B.BEDROCK);
                    for (int y = bottomY + 1; y < c.fy; y++) c.set(u, y, v, UNDER);
                }
                boolean wet = damp && ValueNoise.sample(seed ^ 0xC4E7, c.wx(u) / 5.0, c.wz(v) / 5.0) > 0.62;
                c.set(u, c.fy, v, wet ? CARPET_WET : CARPET);
                for (int y = c.fy + 1; y < ceil; y++) c.set(u, y, v, B.AIR);
                boolean stained = Rng.chance(c.h(u, ceil, v, 0x7113), damp ? 0.06 : 0.008);
                c.set(u, ceil, v, stained ? TILES_STAIN : TILES);
                for (int y = ceil + 1; y < top; y++) c.set(u, y, v, UNDER);
                if (!lower) c.set(u, top, v, B.BEDROCK);
            }
        }
        // ---- wall lines (west and north), as tall as the taller side, doorways as low as the lower side
        Door west = west(c.floor, c.cx, c.cz), north = north(c.floor, c.cx, c.cz);
        int ceilW = ceilY(c.floor, c.cx - 1, c.cz), ceilN = ceilY(c.floor, c.cx, c.cz - 1);
        boolean linkW = hallLink(c.floor, c.cx - 1, c.cz, c.cx, c.cz), linkN = hallLink(c.floor, c.cx, c.cz - 1, c.cx, c.cz);
        for (int v = 1; v < S; v++) line(c, 0, v, v, west, ceil, ceilW, linkW, wall, damp);
        for (int u = 1; u < S; u++) line(c, u, 0, u, north, ceil, ceilN, linkN, wall, damp);
        boolean[] lay = layout(c.floor, c.cx, c.cz);
        boolean openCorner = merged(c.floor, c.cx, c.cz) && cornerOpen(c.floor, c.cx, c.cz);
        if (!openCorner || lay[0]) {
            int ct = Math.max(Math.max(ceil, ceilW), Math.max(ceilN, ceilY(c.floor, c.cx - 1, c.cz - 1)));
            if (c.in(0, 0)) {
                for (int y = c.fy + 1; y < ct; y++) c.set(0, y, 0, wallBlock(c, 0, y, 0, wall, damp, ceil));
                c.set(0, ct, 0, TILES);
            }
        }
        Feature f = featureAt(c.floor, c.cx, c.cz);
        // ---- what stands in the room
        for (int u = 0; u < S; u++) {
            for (int v = 0; v < S; v++) {
                if (!lay[u * S + v] || !c.in(u, v) || (u == 0 && v == 0)) continue;
                for (int y = c.fy + 1; y < ceil; y++) c.set(u, y, v, wallBlock(c, u, y, v, wall, damp, ceil));
            }
        }
        // ---- puddles where the carpet is soaked through (never on the guaranteed-safe spot)
        int[] safe = safeLocal(c.floor, c.cx, c.cz);
        if (damp && f == null) {
            for (int u = Math.max(1, c.u0); u <= c.u1; u++) {
                for (int v = Math.max(1, c.v0); v <= c.v1; v++) {
                    if (Math.abs(u - safe[0]) <= 1 && Math.abs(v - safe[1]) <= 1) continue;
                    if (lay[u * S + v]) continue;
                    if (ValueNoise.sample(seed ^ 0x9D, c.wx(u) / 3.0, c.wz(v) / 3.0) > 0.8 && c.type(u, c.fy + 1, v) == Material.AIR) {
                        c.set(u, c.fy, v, B.WATER);
                        c.set(u, c.fy - 1, v, CARPET_WET);
                    }
                }
            }
        }
        // ---- light: flush troffers above invisible lights
        for (int[] t : troffers(c.floor, c.cx, c.cz, lay)) {
            int state = t[3];
            for (int i = 0; i < t[2]; i++) {
                int u = t[0] + i, v = t[1];
                c.set(u, ceil, v, state == 2 ? PANEL_OFF : PANEL);
                if (state != 2) c.set(u, ceil - 1, v, B.light(lit(15)));
            }
        }
        if (zoneOfCell(c.floor, c.cx, c.cz) == Zone.TALL && !dark(c.floor, c.cx, c.cz)) {
            // the ceiling is far up: light hangs in the air below it, invisible, in an even grid
            for (int u = 2; u < S; u += 4) {
                for (int v = 2; v < S; v += 4) {
                    if (lay[u * S + v]) continue;
                    c.set(u, c.fy + 5, v, B.light(lit(13)));
                    c.set(u, c.fy + 13, v, B.light(lit(11)));
                }
            }
        }
        if (f != null) feature(c, st, f);
        // ---- the vertical loop: holes down to the lower floor, shafts from it, and where fallers come back in
        for (int u = c.u0; u <= c.u1; u++) {
            for (int v = c.v0; v <= c.v1; v++) {
                int x = c.wx(u), z = c.wz(v);
                if (c.floor == UP && holeAt(x, z)) {
                    int lowCeil = ceilY(DOWN, c.cx, c.cz);
                    for (int y = c.fy; y >= lowCeil; y--) c.set(u, y, v, B.AIR);
                } else if (c.floor == UP && (holeAt(x + 1, z) || holeAt(x - 1, z) || holeAt(x, z + 1) || holeAt(x, z - 1))) {
                    // the hole's sides show carpet, not the slab's insides
                    int lowCeil = ceilY(DOWN, c.cx, c.cz);
                    for (int y = c.fy - 1; y > lowCeil; y--) c.set(u, y, v, CARPET);
                }
                if (c.floor == DOWN && shaftAt(x, z)) {
                    for (int y = c.fy; y > bottomY; y--) c.set(u, y, v, B.AIR);
                }
                if (c.floor == UP && shaftAt(x, z)) {
                    for (int y = ceil; y <= CEILING_SHAFT_TOP; y++) c.set(u, y, v, B.AIR);
                }
            }
        }
    }

    // ================================================================== the vertical loop

    /**
     * Regions of the upper floor's open spaces (pillar fields, empty expanses) where the floor has a grid of holes,
     * one 2 x 2 hole per cell between the columns, dropping onto the lower floor.
     */
    private boolean holeRegion(int rx, int rz) {
        Zone z = zoneOf(UP, rx, rz);
        if (z != Zone.PILLARS) return false; // pillar halls only, never the open expanses
        return Rng.chance(Rng.hash(seed ^ 0x401E5L, rx, rz), gs.get("floor-holes-chance", 0.12));
    }

    /** Regions of the lower floor's open spaces with shafts that drop away into the dark (and back to the top). */
    private boolean shaftRegion(int rx, int rz) {
        Zone z = zoneOf(DOWN, rx, rz);
        if (z != Zone.PILLARS && z != Zone.EXPANSE && z != Zone.HALLS) return false;
        return Rng.chance(Rng.hash(seed ^ 0x54AF7L, rx, rz), gs.get("loop-shaft-chance", 0.35));
    }

    boolean hasHoles(int floor, int cx, int cz) {
        if (floor != UP) return false;
        int x = cx * S + 4, z = cz * S + 4;
        return holeAt(x, z);
    }

    boolean hasShaft(int floor, int cx, int cz) {
        int x = cx * S + 1, z = cz * S + 5;
        return shaftAt(x, z);
    }

    /**
     * A column of a floor hole in the upper floor: four single-block holes one block apart at (4|6, 4|6) of a cell,
     * in about a quarter of the cells of a (rare) hole region, where the floor below is free to land on.
     */
    public boolean holeAt(int x, int z) {
        int u = localOf(x), v = localOf(z);
        if ((u != 4 && u != 6) || (v != 4 && v != 6)) return false;
        int cx = cellOf(x), cz = cellOf(z);
        if (!holeRegion(Math.floorDiv(cx, R), Math.floorDiv(cz, R))) return false;
        if (!Rng.chance(Rng.hash(seed ^ 0x40C3L, cx, cz), 0.25)) return false;
        if (featureAt(UP, cx, cz) != null || featureAt(DOWN, cx, cz) != null) return false;
        boolean[] lay = layout(DOWN, cx, cz);
        for (int a = 4; a <= 6; a++) for (int b = 4; b <= 6; b++) if (lay[a * S + b]) return false;
        return true;
    }

    /**
     * A column of a loop shaft: the 2 x 2 at (1..2, 5..6) of a lower-floor cell, about every other cell of a shaft
     * region, where the upper floor above is free too (fallers come back in through its ceiling there).
     */
    public boolean shaftAt(int x, int z) {
        int u = localOf(x), v = localOf(z);
        if (u < 1 || u > 2 || v < 5 || v > 6) return false;
        int cx = cellOf(x), cz = cellOf(z);
        if (!shaftRegion(Math.floorDiv(cx, R), Math.floorDiv(cz, R))) return false;
        if (!Rng.chance(Rng.hash(seed ^ 0x5AF7L, cx, cz), 0.5)) return false;
        if (featureAt(UP, cx, cz) != null || featureAt(DOWN, cx, cz) != null) return false;
        boolean[] down = layout(DOWN, cx, cz), up = layout(UP, cx, cz);
        for (int a = 1; a <= 2; a++) for (int b = 5; b <= 6; b++) if (down[a * S + b] || up[a * S + b]) return false;
        return true;
    }

    /** Where a faller in a loop shaft at {@code y} comes back in: the same depth into the shaft over the upper floor. */
    public static double loopTarget(double y) {
        return CEILING_SHAFT_TOP - 2.5 - (LOOP_Y - y);
    }

    private void line(CellCtx c, int u, int v, int along, Door door, int ceil, int other, boolean link, B wall, boolean damp) {
        if (!c.in(u, v)) return;
        int top = Math.max(ceil, other);
        int low = Math.min(ceil, other);
        int openTop = c.fy;
        if (door.covers(along)) openTop = door.full() || link ? low - 1 : Math.min(c.fy + DOOR_H, low - 1);
        for (int y = c.fy + 1; y < top; y++) c.set(u, y, v, y <= openTop ? B.AIR : wallBlock(c, u, y, v, wall, damp, low));
        c.set(u, top, v, TILES);
    }

    private B wallBlock(CellCtx c, int u, int y, int v, B wall, boolean damp, int ceil) {
        if (damp && y == ceil - 1 && Rng.chance(c.h(u, y, v, 0x57A1), 0.35)) return WALL_STAIN;
        return wall;
    }

    /**
     * Troffers of a cell: {u, v, length, state}; state 0 lit, 1 stuttering, 2 dead. Two-block fixtures along X in
     * a fixed staggered grid (rooms), down the middle of a hall, or between the columns (open zones). Skipped where
     * something stands under them.
     */
    List<int[]> troffers(int floor, int cx, int cz, boolean[] lay) {
        List<int[]> out = new java.util.ArrayList<>();
        Zone z = zoneOfCell(floor, cx, cz);
        int[][] spots;
        switch (z) {
            case HALLS -> {
                boolean ax = hallsAlongX(floor, Math.floorDiv(cx, R), Math.floorDiv(cz, R));
                spots = ax ? new int[][]{{1, 4, 2}, {5, 4, 2}} : new int[][]{{4, 2, 1}, {4, 6, 1}};
            }
            case PILLARS, TALL -> spots = new int[][]{{1, 2, 2}, {5, 2, 2}, {1, 6, 2}, {5, 6, 2}};
            default -> spots = featureAt(floor, cx, cz) != null ? new int[][]{{3, 2, 2}, {3, 6, 2}} : new int[][]{{2, 2, 2}, {5, 6, 2}};
        }
        double lit = dark(floor, cx, cz) ? 0.06 : z == Zone.PILLARS ? 0.88 : 0.93;
        for (int[] sp : spots) {
            boolean free = true;
            for (int i = 0; i < sp[2]; i++) free &= !lay[(sp[0] + i) * S + sp[1]];
            if (!free) continue;
            long h = Rng.hash(seed ^ 0x1167L, cx * S + sp[0], cz * S + sp[1]);
            out.add(new int[]{sp[0], sp[1], sp[2], lampState(h, lit)});
        }
        return out;
    }

    // ================================================================== RoomLevel hooks (painting is custom)

    @Override
    protected void interior(CellCtx c, Style st) {
    }

    @Override
    protected void feature(CellCtx c, Style st, Feature f) {
        if (f.id().equals("seam")) {
            // a cold seam: pool tiles where the carpet should be, and a block of tiles to the ceiling
            for (int u = 2; u <= 6; u++) for (int v = 2; v <= 6; v++) c.set(u, c.fy, v, SEAM_TILE);
            for (int u = 3; u <= 5; u++) for (int v = 3; v <= 5; v++) for (int y = c.fy + 1; y < st.ceilY; y++) c.set(u, y, v, SEAM_TILE);
        }
    }

    @Override
    protected int[] triggerBox(Feature f) {
        return f.id().equals("seam") ? new int[]{2, 0, 2, 6, 1, 6} : null;
    }

    // ================================================================== decorations

    @Override
    protected void cellDecor(int floor, int cx, int cz, List<DecorSpec> out) {
        Feature f = featureAt(floor, cx, cz);
        int ceil = ceilY(floor, cx, cz);
        int fy = floorY[floor] + 1;
        long h = ch(floor, cx, cz, 0xDEC0);
        boolean[] lay = layout(floor, cx, cz);
        Zone z = zoneOfCell(floor, cx, cz);
        // light fixtures are blocks; specs only for the hum and the ones that stutter
        boolean hum = false;
        for (int[] t : troffers(floor, cx, cz, lay)) {
            double x = cx * S + t[0] + t[2] / 2.0, zz = cz * S + t[1] + 0.5;
            if (t[3] == 1) out.add(new DecorSpec(t[2] == 2 ? "panel_flicker" : "panel_light", x, ceil, zz, 0f, 0f, 0f, Rng.hash(h, t[0], t[1])));
            else if (t[3] == 0 && !hum) {
                hum = true;
                out.add(new DecorSpec("panel_light", x, ceil, zz, 0f, 0f, 0f, Rng.hash(h, t[0], t[1])));
            }
        }
        if (f != null) {
            switch (f.id()) {
                case "seam" -> {
                    out.add(spec("drip_emitter", cx, cz, 4.5, ceil - 0.05, 4.5, 0f, f.seed()));
                    out.add(spec("graffiti", cx, cz, 2.95, fy + 1.2, 4.5, 90f, f.seed()));
                }
                case "phone_desk" -> out.add(spec("phone_desk", cx, cz, 4.5, fy, 4.5, (float) (Rng.pick(f.seed(), 4) * 90), f.seed())
                        .barrier(cx * S + 4, fy, cz * S + 4));
                case "chair_circle" -> {
                    for (int i = 0; i < 6; i++) {
                        double a = Math.PI * 2 * i / 6;
                        out.add(spec("chair", cx, cz, 4.5 + Math.cos(a) * 2.4, fy, 4.5 + Math.sin(a) * 2.4,
                                (float) Math.toDegrees(Math.atan2(-Math.cos(a), Math.sin(a))) + 180f, Rng.hash(f.seed(), i)));
                    }
                }
                default -> {
                }
            }
            return;
        }
        // sparse props: most rooms are simply empty, which is the point
        double r = Rng.unit(h) / Math.max(0.05, gs.get("decor-density", 1.0));
        if (z != Zone.ROOMS) {
            if (r < 0.04 && free(lay, 2, 5) && free(lay, 3, 5)) {
                out.add(spec("office_chair", cx, cz, 2.5 + Rng.pick(h, 2), fy, 5.5, (float) Rng.pick(Rng.hash(h, 1), 360), h));
            } else if (z == Zone.HALLS && r < 0.06) {
                out.add(new DecorSpec("wet_floor_sign", cx * S + 4.5, fy, cz * S + 4.5, (float) Rng.pick(h, 360), 0f, 0f, h));
            }
            return;
        }
        int side = clearSide(floor, cx, cz, h, lay);
        if (r < 0.03 && side >= 0) {
            double[] p = wallFace(cx, cz, side, 4.5, fy);
            out.add(new DecorSpec("wall_chair", p[0], p[1], p[2], yawAwayFrom(side) + (float) (Rng.unit(Rng.hash(h, 3)) * 30 - 15), 0f, 8f, h));
        } else if (r < 0.05 && free(lay, 4, 5)) {
            out.add(new DecorSpec("floating_chair", cx * S + 4.5, fy + 1.6, cz * S + 5.5, (float) Rng.pick(h, 360), 18f, 33f, h));
        } else if (r < 0.08) {
            Door w = west(floor, cx, cz);
            if (w.open() && !w.full()) out.add(new DecorSpec("warped_doorframe:" + w.width(), cx * S + 0.5, fy, cz * S + w.start(), -90f, 0f, 4f, h));
        } else if (r < 0.095 && side >= 0) {
            double[] p = wallFace(cx, cz, side, 4.5, fy + 2.4);
            out.add(new DecorSpec("fake_exit_sign", p[0], p[1], p[2], yawAwayFrom(side), 0f, 0f, h));
        } else if (r < 0.11 && free(lay, 5, 3)) {
            out.add(new DecorSpec("wet_floor_sign", cx * S + 5.5, fy, cz * S + 3.5, (float) Rng.pick(h, 360), 0f, 0f, h));
        } else if (r < 0.125 && free(lay, 4, 4) && ceil - fy >= 4) {
            out.add(new DecorSpec("dangling_tile", cx * S + 4.5, ceil, cz * S + 4.5, (float) Rng.pick(h, 4) * 90f, 0f, 0f, h));
        } else if (r < 0.145 && side >= 0) {
            double[] p = wallFace(cx, cz, side, 4.5, fy + 2.3);
            out.add(new DecorSpec("wall_clock", p[0], p[1], p[2], yawAwayFrom(side), 0f, 0f, h));
        }
    }

    private boolean free(boolean[] lay, int u, int v) {
        return u > 0 && v > 0 && u < S && v < S && !lay[u * S + v];
    }

    /** A closed side whose wall face is clear of jutting sections over its middle (along 3..5), or -1. */
    private int clearSide(int floor, int cx, int cz, long h, boolean[] lay) {
        int s = Rng.pick(Rng.hash(h, 11), 4);
        for (int i = 0; i < 4; i++, s = (s + 1) % 4) {
            if (!closed(floor, cx, cz, s)) continue;
            boolean ok = true;
            for (int a = 2; a <= 6; a++) {
                int[] uv = sideToUV(s, a, 1);
                ok &= !lay[uv[0] * S + uv[1]];
            }
            if (ok) return s;
        }
        return -1;
    }

    @Override
    protected void cellDetails(int floor, int cx, int cz, List<DecorSpec> out) {
        if (zoneOfCell(floor, cx, cz) != Zone.ROOMS || kind(floor, cx, cz) == K_THICK || featureAt(floor, cx, cz) != null) return;
        int ceil = ceilY(floor, cx, cz);
        int fy = floorY[floor] + 1;
        long h = ch(floor, cx, cz, 0xD7A1);
        boolean[] lay = layout(floor, cx, cz);
        // deterioration: rare, and clustered where the building is "worse"
        double w = wear(cx, cz, 0x3EA1L) + (damp(cx, cz) ? 0.5 : 0);
        int side = clearSide(floor, cx, cz, h, lay);
        if (side < 0) return;
        if (Rng.chance(Rng.hash(h, 3), 0.05 + 0.4 * w)) {
            // a brown water stain creeping down from the ceiling, with a run below it
            double along = 3.1 + Rng.unit(Rng.hash(h, 4)) * 0.4, sw = 0.8 + Rng.unit(Rng.hash(h, 5)) * 0.6;
            out.add(wallDetail(DecorSpec.TIER_AGING, Rng.hash(h, 6), "d:wpatch:brown_terracotta:" + f2(sw) + ":1.1", cx, cz, side, along, sw, ceil - 1.12));
            if (Rng.chance(Rng.hash(h, 7), 0.5)) {
                out.add(wallDetail(DecorSpec.TIER_AGING, Rng.hash(h, 8), "d:streak:brown_terracotta:1.4", cx, cz, side, along + sw * 0.5, 0, ceil - 1.1));
            }
        } else if (Rng.chance(Rng.hash(h, 9), 0.03 + 0.18 * w)) {
            // a faded rectangle where something hung for years
            out.add(wallDetail(DecorSpec.TIER_AGING, Rng.hash(h, 10), "d:wpatch:smooth_sandstone:1.3:0.9", cx, cz, side, 3.35, 1.3, fy + 1.3));
        }
    }

    static String f2(double d) {
        return String.format(java.util.Locale.ROOT, "%.2f", d);
    }

    /**
     * A light level with the level's brightness offset applied ({@code brightness}, default -2): the Lobby is meant
     * to be dimmer than a lit room. The client's own brightness slider cannot be touched by a server, so this is
     * done with the light the level gives off.
     */
    private int lit(int level) {
        return Math.max(1, Math.min(15, level + (int) Math.round(gs.get("brightness", -2))));
    }
}
