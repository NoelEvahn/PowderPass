package com.powderpass.backrooms.gen.levels;

import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.gen.B;
import com.powderpass.backrooms.gen.CellCtx;
import com.powderpass.backrooms.gen.DecorSpec;
import com.powderpass.backrooms.gen.Dest;
import com.powderpass.backrooms.gen.Feature;
import com.powderpass.backrooms.gen.FeatureDef;
import com.powderpass.backrooms.gen.GenSettings;
import com.powderpass.backrooms.gen.LevelGenerator;
import com.powderpass.backrooms.util.Rng;
import org.bukkit.Material;

import java.util.List;

/**
 * The Poolrooms. Not rooms at all: one continuous tiled hall of 12x12 bays with pillars at the corners.
 * Water depth and ceiling height follow smooth noise, so dry walkways melt into wading pools and then into
 * deep lakes. All water is still and flush with the floor, so you can climb out anywhere. The only walls are
 * free-standing tile screens, each with an opening; nothing is ever closed off.
 */
public final class PoolroomsGenerator extends LevelGenerator {
    private static final int FLOOR = 64;
    static final int K_DRY = 0, K_SHALLOW = 1, K_DEEP = 2, K_RAISED = 3, K_TERRACE = 4;
    private static final String[] KINDS = {"dry", "shallow", "deep", "raised", "terrace-pool"};

    private static final B TILE = B.of(Material.SMOOTH_QUARTZ);
    private static final B GROUT = B.of(Material.WHITE_CONCRETE);
    private static final B POOL = B.of(Material.LIGHT_BLUE_CONCRETE);
    private static final B ACCENT = B.of(Material.LIGHT_BLUE_CONCRETE);
    private static final B UNDER = B.of(Material.WHITE_CONCRETE);
    private static final B STEP = B.of(Material.QUARTZ_STAIRS, "[facing=west,half=bottom,waterlogged=true]");
    private static final B UNDERWATER_LIGHT = B.of(Material.LIGHT, "[level=12,waterlogged=true]");

    public PoolroomsGenerator(long seed, GenSettings gs) {
        super(Level.POOLROOMS, seed, gs, 12, 5, 3, new int[]{FLOOR}, FLOOR - 11, FLOOR + 19, 0.0, 0.0);
    }

    @Override
    protected List<FeatureDef> featureDefs() {
        return List.of(
                // onward to the Habitable Zone in every district; the drain back to Level 0 is rarer
                FeatureDef.transition("service_door", Dest.to(Level.LEVEL_1), 1.0),
                FeatureDef.transition("drain", Dest.to(Level.LEVEL_0), gs.get("back-chance", 0.4)),
                new FeatureDef("slide_tower", null, gs.get("landmark-chance", 0.5), 0, "slide"),
                FeatureDef.landmark("lifeguard_post", gs.get("landmark-chance", 0.5)));
    }

    @Override
    public String biomeKey() {
        return "minecraft:warm_ocean";
    }

    private double wet(int cx, int cz) {
        return zone(cx, cz, 0x3A7E4L, 70) * 0.8 + com.powderpass.backrooms.util.Rng.unit(ch(0, cx, cz, 0x7E7)) * 0.2;
    }

    public int kind(int floor, int cx, int cz) {
        Feature f = featureAt(floor, cx, cz);
        if (f != null) return f.id().equals("drain") ? K_DEEP : K_DRY;
        double w = wet(cx, cz) + (gs.get("water-ratio", 0.55) - 0.55);
        if (w > 0.60) return K_DEEP;
        if (w > 0.48) return K_SHALLOW;
        // dry bays: some are raised platforms, a few of those hold a pool of their own one step up
        double e = zone(cx, cz, 0xE1E7L, 60);
        if (e > 0.72) return Rng.chance(ch(floor, cx, cz, 0x7E44), 0.4) ? K_TERRACE : K_RAISED;
        return K_DRY;
    }

    int depth(int cx, int cz) {
        Feature f = featureAt(0, cx, cz);
        if (f != null && f.id().equals("drain")) return 8;
        return 3 + Math.min(5, (int) ((wet(cx, cz) - 0.60) * 30));
    }

    int ceil(int cx, int cz) {
        double n = zone(cx, cz, 0xCE11L, 90);
        if (kind(0, cx, cz) >= K_RAISED) return Math.max(FLOOR + 10, n > 0.74 ? FLOOR + 16 : FLOOR + 10);
        if (n > 0.74) return FLOOR + 16;
        if (n > 0.55) return FLOOR + 10;
        return FLOOR + 7;
    }

    /** Free-standing screen on the north (side 0) or west (side 1) edge: {openingStart, openingWidth, height} or null. */
    private int[] screen(int cx, int cz, int side) {
        long h = ch(0, cx, cz, 0x5C4 + side);
        if (!Rng.chance(h, gs.get("screen-chance", 0.2))) return null;
        int w = 4 + Rng.pick(Rng.hash(h, 1), 3);
        int start = 2 + Rng.pick(Rng.hash(h, 2), S - 2 - w);
        int height = Rng.chance(Rng.hash(h, 3), 0.5) ? 3 : 99;
        return new int[]{start, w, height};
    }

    @Override
    protected void paintCell(CellCtx c) {
        int k = kind(c.floor, c.cx, c.cz);
        int d = depth(c.cx, c.cz);
        int ceil = ceil(c.cx, c.cz);
        int[] north = screen(c.cx, c.cz, 0), west = screen(c.cx, c.cz, 1);
        for (int u = c.u0; u <= c.u1; u++) {
            for (int v = c.v0; v <= c.v1; v++) {
                c.set(u, bottomY, v, B.BEDROCK);
                for (int y = bottomY + 1; y < c.fy; y++) c.set(u, y, v, UNDER);
                c.set(u, roofY, v, B.BEDROCK);
                boolean pillar = u <= 1 && v <= 1;
                if (pillar) {
                    // tiled pillar with a pale blue accent band at knee and head height
                    for (int y = c.fy; y < roofY; y++) c.set(u, y, v, (y == c.fy + 1 || y == c.fy + 4) ? ACCENT : TILE);
                    continue;
                }
                int wx = c.wx(u), wz = c.wz(v);
                boolean grid = Math.floorMod(wx, 4) == 0 || Math.floorMod(wz, 4) == 0;
                B tile = grid ? GROUT : TILE, lining = grid ? GROUT : POOL;
                switch (k) {
                    case K_SHALLOW -> {
                        c.set(u, c.fy - 1, v, lining);
                        c.set(u, c.fy, v, B.WATER);
                    }
                    case K_DEEP -> {
                        c.set(u, c.fy - d, v, lining);
                        for (int y = c.fy - d + 1; y <= c.fy; y++) c.set(u, y, v, B.WATER);
                        // submerged steps along the west side, so you can walk out instead of climbing
                        if (u <= 3 && v >= 4 && v <= 8 && d >= 4) {
                            int top = c.fy - u;
                            for (int y = c.fy - d + 1; y < top; y++) c.set(u, y, v, POOL);
                            c.set(u, top, v, STEP);
                        }
                    }
                    case K_RAISED, K_TERRACE -> {
                        c.set(u, c.fy, v, tile);
                        boolean inner = u >= 3 && u <= S - 2 && v >= 3 && v <= S - 2;
                        if (k == K_TERRACE && inner) {
                            c.set(u, c.fy, v, lining);
                            c.set(u, c.fy + 1, v, B.WATER);
                        } else if (u >= 1 && v >= 1) {
                            c.set(u, c.fy + 1, v, tile);
                        }
                    }
                    default -> c.set(u, c.fy, v, tile);
                }
                int air0 = (k == K_RAISED || k == K_TERRACE) && u >= 1 && v >= 1 ? c.fy + 2 : c.fy + 1;
                for (int y = air0; y < ceil; y++) c.set(u, y, v, B.AIR);
                for (int y = ceil; y < roofY; y++) c.set(u, y, v, TILE);
                boolean screenN = north != null && v == 0 && (u < north[0] || u >= north[0] + north[1]);
                boolean screenW = west != null && u == 0 && (v < west[0] || v >= west[0] + west[1]);
                if (screenN || screenW) {
                    int top = Math.min(ceil - 1, c.fy + (screenN ? north[2] : west[2]));
                    int from = k == K_DEEP ? c.fy - d + 1 : k == K_SHALLOW ? c.fy : k >= K_RAISED ? c.fy + 2 : c.fy + 1;
                    for (int y = from; y <= top; y++) c.set(u, y, v, TILE);
                }
            }
        }
        if (k == K_DEEP) c.set(S / 2, c.fy - d + 1, S / 2, UNDERWATER_LIGHT);
        Feature f = featureAt(c.floor, c.cx, c.cz);
        if (f != null) feature(c, f, ceil);
    }

    private void feature(CellCtx c, Feature f, int ceil) {
        switch (f.id()) {
            case "drain" -> {
                for (int u = 5; u <= 7; u++) for (int v = 5; v <= 7; v++) c.set(u, c.fy - 8, v, B.of(Material.BLACK_CONCRETE));
            }
            case "service_door" -> booth(c, B.of(Material.GRAY_CONCRETE), B.of(Material.GRAY_CONCRETE));
            case "slide_tower" -> {
                for (int kk = 1; kk <= 4; kk++) {
                    for (int u = 3; u <= 4; u++) {
                        for (int y = c.fy + 1; y < c.fy + kk; y++) c.set(u, y, kk + 1, TILE);
                        c.set(u, c.fy + kk, kk + 1, B.stairs(Material.QUARTZ_STAIRS, "south", false));
                    }
                }
                for (int u = 3; u <= 4; u++) for (int v = 6; v <= 7; v++) for (int y = c.fy + 1; y <= c.fy + 4; y++) c.set(u, y, v, TILE);
                for (int u = 6; u <= 10; u++) {
                    for (int v = 4; v <= 10; v++) {
                        c.set(u, c.fy - 4, v, POOL);
                        for (int y = c.fy - 3; y <= c.fy; y++) c.set(u, y, v, B.WATER);
                    }
                }
            }
            default -> {
            }
        }
    }

    /** Small grey booth in the bay centre, open to the south; interior (6,6)-(6,7). */
    private void booth(CellCtx c, B wall, B floor) {
        for (int u = 5; u <= 7; u++) {
            for (int v = 5; v <= 7; v++) {
                boolean edge = u == 5 || u == 7 || v == 5;
                for (int y = c.fy + 1; y <= c.fy + 3; y++) c.set(u, y, v, edge ? wall : B.AIR);
                c.set(u, c.fy + 4, v, wall);
                c.set(u, c.fy, v, floor);
            }
        }
        c.set(6, c.fy + 3, 6, B.light(8));
    }

    @Override
    protected int[] triggerBox(Feature f) {
        return switch (f.id()) {
            case "drain" -> new int[]{5, -9, 5, 7, -6, 7};
            case "service_door" -> new int[]{6, 0, 6, 6, 1, 6};
            case "slide_tower" -> new int[]{4, 4, 6, 4, 5, 7};
            default -> null;
        };
    }

    @Override
    public boolean isSafeCell(int floor, int cx, int cz) {
        return kind(floor, cx, cz) == K_DRY;
    }

    @Override
    public String zoneName(int floor, int cx, int cz) {
        int k = kind(floor, cx, cz);
        return "bay " + KINDS[k] + (k == K_DEEP ? " d" + depth(cx, cz) : "") + " ceiling " + (ceil(cx, cz) - FLOOR - 1);
    }

    @Override
    protected void cellDecor(int floor, int cx, int cz, List<DecorSpec> out) {
        long h = ch(floor, cx, cz, 0xDEC1);
        int fy = FLOOR + 1;
        int k = kind(floor, cx, cz);
        int ceil = ceil(cx, cz);
        out.add(lamp("skylight_panel", cx * S + 6.5, ceil, cz * S + 6.5, 0f, h, lampState(Rng.hash(h, 40), 0.95),
                cx * S + 6, ceil - 1, cz * S + 6, 15));
        if (k == K_DEEP) out.add(spec("pool_lamp", cx, cz, 6.5, fy - depth(cx, cz) + 0.02, 6.5, 0f, h));
        if (k != K_DRY && Rng.chance(Rng.hash(h, 1), 0.45)) out.add(spec("caustics", cx, cz, 6.0, ceil - 0.02, 6.0, (float) Rng.pick(h, 4) * 90f, h));
        Feature f = featureAt(floor, cx, cz);
        if (f != null) {
            switch (f.id()) {
                case "drain" -> out.add(spec("drain_vortex", cx, cz, 6.5, fy - 8.95, 6.5, 0f, f.seed()));
                case "service_door" -> out.add(spec("staff_sign", cx, cz, 6.5, fy + 3.3, 8.02, 0f, f.seed()));
                case "slide_tower" -> out.add(spec("water_slide", cx, cz, 5.0, fy + 4, 7.0, -90f, f.seed()));
                case "lifeguard_post" -> out.add(spec("lifeguard_chair", cx, cz, 6.5, fy, 6.5, (float) (f.rot() * 90), f.seed())
                        .barrier(cx * S + 6, fy, cz * S + 6));
                default -> {
                }
            }
            return;
        }
        double r = Rng.unit(Rng.hash(h, 2)) / Math.max(0.05, gs.get("decor-density", 1.0));
        switch (k) {
            case K_DEEP -> {
                if (r < 0.2) out.add(spec("floating_chair_pool", cx, cz, 4.5 + Rng.pick(h, 5), fy - 0.35, 6.5, (float) Rng.pick(h, 360), h));
                else if (r < 0.3) out.add(spec("submerged_bench", cx, cz, 6.5, fy - depth(cx, cz), 3.5, (float) Rng.pick(h, 360), h));
            }
            case K_SHALLOW -> {
                if (r < 0.15) out.add(spec("submerged_bench", cx, cz, 4.5 + Rng.pick(h, 4), fy - 1.0, 7.5, (float) Rng.pick(h, 4) * 90f, h));
                else if (r < 0.25) out.add(spec("drip_emitter", cx, cz, 7.5, ceil - 0.05, 7.5, 0f, h));
            }
            default -> {
                if (r < 0.08) out.add(spec("drain_grate", cx, cz, 6.0, fy + 0.001, 9.0, 0f, h));
                else if (r < 0.13) out.add(spec("pool_bench", cx, cz, 6.5, fy, 3.5, 0f, h).barrier(cx * S + 6, fy, cz * S + 3));
                else if (r < 0.16) out.add(new DecorSpec("impossible_stairs", cx * S + 9.5, fy + 2.0, cz * S + 9.5, 135f, 0f, 0f, h));
                else if (r < 0.19) out.add(spec("lifebuoy", cx, cz, 2.0, fy + 1.8, 1.0, -90f, h));
            }
        }
    }

    // ------------------------------------------------------------------ surface details

    private boolean wetKind(int k) {
        return k == K_SHALLOW || k == K_DEEP;
    }

    @Override
    protected void cellDetails(int floor, int cx, int cz, List<DecorSpec> out) {
        if (featureAt(floor, cx, cz) != null) return;
        int k = kind(floor, cx, cz);
        long h = ch(floor, cx, cz, 0xD7A2);
        double ox = cx * S, oz = cz * S;
        double fy = FLOOR + 1;
        int len = S - 2;
        if (k == K_DRY) {
            // coping along every edge where this dry bay meets water (skipping edges with a screen)
            if (wetKind(kind(floor, cx - 1, cz)) && screen(cx, cz, 1) == null)
                out.add(detail(DecorSpec.TIER_ARCH, Rng.hash(h, 1), "d:coping:" + len, ox, fy, oz + 2, 90f, 0f));
            if (wetKind(kind(floor, cx, cz - 1)) && screen(cx, cz, 0) == null)
                out.add(detail(DecorSpec.TIER_ARCH, Rng.hash(h, 2), "d:coping:" + len, ox + S, fy, oz, 180f, 0f));
            if (wetKind(kind(floor, cx + 1, cz)) && screen(cx + 1, cz, 1) == null)
                out.add(detail(DecorSpec.TIER_ARCH, Rng.hash(h, 3), "d:coping:" + len, ox + S, fy, oz + S, -90f, 0f));
            if (wetKind(kind(floor, cx, cz + 1)) && screen(cx, cz + 1, 0) == null)
                out.add(detail(DecorSpec.TIER_ARCH, Rng.hash(h, 4), "d:coping:" + len, ox + 2, fy, oz + S, 0f, 0f));
        }
        if (k == K_DEEP) {
            // a recessed drain cover on the pool floor
            out.add(detail(DecorSpec.TIER_ARCH, Rng.hash(h, 5), "d:drain:1.2", ox + 9.5, fy - depth(cx, cz) + 0.002, oz + 9.5, 0f, 0f));
        }
        // softened corner on the pillar edge facing into the bay
        int top = ceil(cx, cz);
        out.add(detail(DecorSpec.TIER_ARCH, Rng.hash(h, 6), "d:rcorner:smooth_quartz:" + (top - FLOOR - 1), ox + 2, fy, oz + 2, 0f, 0f));
        // a small recessed grille low on the pillar's east face
        if (k != K_DEEP && Rng.chance(Rng.hash(h, 7), 0.25)) {
            out.add(detail(DecorSpec.TIER_ENV, Rng.hash(h, 8), "d:grille:0.6:0.3", ox + 2.003, fy + 0.25, oz + 1.7, -90f, 0f));
        }
    }
}
