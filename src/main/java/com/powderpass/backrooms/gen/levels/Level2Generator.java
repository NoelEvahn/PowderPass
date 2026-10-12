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
 * Level 2 — Pipe Dreams. Cells are tunnel junctions carved out of solid rock; maze edges become 2–3 wide
 * service tunnels. Utility rooms, rare tall junction halls and steam-vented dead ends vary the rhythm.
 */
public final class Level2Generator extends LevelGenerator {
    private static final int FLOOR = 64;
    private static final int C0 = 3;
    static final int K_TUNNEL = 0, K_UTILITY = 1, K_JUNCTION = 2, K_HAZARD = 3, K_ROOMFEATURE = 4;
    private static final String[] KINDS = {"tunnel", "utility-room", "junction-hall", "steam-dead-end", "feature"};
    private static final B[][] WALLS = {
            {B.of(Material.DEEPSLATE_BRICKS), B.of(Material.CRACKED_DEEPSLATE_BRICKS), B.of(Material.DEEPSLATE_TILES)},
            {B.of(Material.GRAY_CONCRETE), B.of(Material.CYAN_TERRACOTTA), B.of(Material.LIGHT_GRAY_CONCRETE)},
            {B.of(Material.TUFF_BRICKS), B.of(Material.POLISHED_TUFF), B.of(Material.CHISELED_TUFF)},
    };
    private static final String[] NAMES = {"brick-tunnels", "concrete-ducts", "tuff-galleries"};
    private static final B FLOOR_B = B.of(Material.POLISHED_DEEPSLATE);
    private static final B FLOOR_ALT = B.of(Material.DEEPSLATE_TILES);
    private static final B UNDER = B.of(Material.DEEPSLATE);
    private static final B GRATE = B.of(Material.IRON_TRAPDOOR, "[half=top,open=false,facing=north]");

    public Level2Generator(long seed, GenSettings gs) {
        super(Level.LEVEL_2, seed, gs, 8, 8, 3, new int[]{FLOOR}, FLOOR - 6, FLOOR + 10, 0.12, 0.0);
    }

    @Override
    protected List<FeatureDef> featureDefs() {
        return List.of(
                FeatureDef.transition("ladder_hatch", Dest.to(Level.LEVEL_3), 1.0),
                FeatureDef.transition("stairs_up", Dest.to(Level.LEVEL_1), 1.0),
                FeatureDef.landmark("boiler_room", gs.get("landmark-chance", 0.7)),
                FeatureDef.landmark("valve_gallery", gs.get("landmark-chance", 0.7) * 0.8));
    }

    @Override
    public String biomeKey() {
        return "minecraft:basalt_deltas";
    }

    private int palette(int cx, int cz) {
        return Math.min(2, (int) (zone(cx, cz, 0x2B1L, 70) * 3));
    }

    /** 0..1 heat of the area; hot zones have more glowing grates and stronger steam. */
    public double heat(int x, int z) {
        return zone(cellOf(x), cellOf(z), 0x4EA7L, 60);
    }

    public int kind(int floor, int cx, int cz) {
        if (featureAt(floor, cx, cz) != null) {
            return K_ROOMFEATURE;
        }
        if (degree(floor, cx, cz) == 1 && Rng.chance(ch(floor, cx, cz, 0xDEAD), gs.get("hazard-dead-end-chance", 0.35))) {
            return K_HAZARD;
        }
        double r = Rng.unit(ch(floor, cx, cz, 0x2222));
        if (r < 0.08) return K_UTILITY;
        if (r < 0.08 + gs.get("junction-chance", 0.04)) return K_JUNCTION;
        return K_TUNNEL;
    }

    private int width(int floor, int cx, int cz, boolean east) {
        return 2 + Rng.pick(Rng.hash(seed ^ 0x3D1L, cx, cz, east ? 1 : 0), 2);
    }

    private boolean open(int floor, int cx, int cz, int u, int v, int k) {
        boolean e = edgeOpen(floor, cx, cz, true), w = edgeOpen(floor, cx - 1, cz, true);
        boolean s = edgeOpen(floor, cx, cz, false), n = edgeOpen(floor, cx, cz - 1, false);
        int we = e ? width(floor, cx, cz, true) : 0, ww = w ? width(floor, cx - 1, cz, true) : 0;
        int ws = s ? width(floor, cx, cz, false) : 0, wn = n ? width(floor, cx, cz - 1, false) : 0;
        int wc = Math.max(2, Math.max(Math.max(we, ww), Math.max(ws, wn)));
        if (u >= C0 && u < C0 + wc && v >= C0 && v < C0 + wc) return true;
        if (e && u >= C0 && v >= C0 && v < C0 + we) return true;
        if (w && u <= C0 && v >= C0 && v < C0 + ww) return true;
        if (s && v >= C0 && u >= C0 && u < C0 + ws) return true;
        if (n && v <= C0 && u >= C0 && u < C0 + wn) return true;
        if (k == K_UTILITY) return u >= 1 && u <= 6 && v >= 1 && v <= 6;
        // junctions and feature rooms: whole interior open; features only build inside [3,5]^2 so a
        // 2-wide ring always links every tunnel arm
        if (k == K_JUNCTION || k == K_ROOMFEATURE) return u >= 1 && v >= 1;
        return false;
    }

    private int ceil(int k) {
        return k == K_JUNCTION ? FLOOR + 6 : k == K_ROOMFEATURE ? FLOOR + 5 : FLOOR + 4;
    }

    @Override
    protected void paintCell(CellCtx c) {
        int k = kind(c.floor, c.cx, c.cz);
        int p = palette(c.cx, c.cz);
        B[] walls = WALLS[p];
        int ceilY = ceil(k);
        boolean hot = heat(c.wx(4), c.wz(4)) > 0.68;
        for (int u = c.u0; u <= c.u1; u++) {
            for (int v = c.v0; v <= c.v1; v++) {
                c.set(u, bottomY, v, B.BEDROCK);
                for (int y = bottomY + 1; y < c.fy; y++) c.set(u, y, v, UNDER);
                c.set(u, roofY, v, B.BEDROCK);
                if (open(c.floor, c.cx, c.cz, u, v, k)) {
                    long hh = c.h(u, 0, v, 0x6A7E);
                    boolean grate = Rng.chance(hh, hot ? 0.16 : 0.05);
                    if (grate) {
                        c.set(u, c.fy, v, GRATE);
                        c.set(u, c.fy - 1, v, B.of(Material.MAGMA_BLOCK));
                    } else {
                        c.set(u, c.fy, v, Rng.chance(Rng.hash(hh, 1), 0.2) ? FLOOR_ALT : FLOOR_B);
                    }
                    for (int y = c.fy + 1; y < ceilY; y++) c.set(u, y, v, B.AIR);
                    c.set(u, ceilY, v, walls[0]);
                    for (int y = ceilY + 1; y < roofY; y++) c.set(u, y, v, UNDER);
                } else {
                    for (int y = c.fy; y < roofY; y++) {
                        long hh = c.h(u, y, v, 0x3A11);
                        double r = Rng.unit(hh);
                        c.set(u, y, v, r < 0.12 ? walls[1] : r < 0.2 ? walls[2] : walls[0]);
                    }
                }
            }
        }
        pipes(c, k, hot);
        switch (k) {
            case K_UTILITY -> utility(c);
            case K_ROOMFEATURE -> feature(c, featureAt(c.floor, c.cx, c.cz));
            case K_HAZARD -> c.set(C0, c.fy, C0, GRATE);
            default -> {
            }
        }
    }

    /** Pipes embedded in the tunnel walls and hung under the ceiling of wide tunnels; sparse lights. */
    private void pipes(CellCtx c, int k, boolean hot) {
        long h = ch(c.floor, c.cx, c.cz, 0x919E);
        B pipeX = B.axis(Material.POLISHED_BASALT, 'x');
        B pipeZ = B.axis(Material.POLISHED_BASALT, 'z');
        B hotPipe = B.of(Material.WAXED_EXPOSED_COPPER);
        if (edgeOpen(c.floor, c.cx, c.cz, true)) {
            int w = width(c.floor, c.cx, c.cz, true);
            for (int u = C0 + 2; u < S; u++) {
                if (w == 3) c.set(u, c.fy + 3, C0, pipeX);
                if (k != K_TUNNEL && k != K_HAZARD) continue;
                c.set(u, c.fy + 2, C0 - 1, hot && (u & 1) == 0 ? hotPipe : pipeX);
                c.set(u, c.fy + 1, C0 + w, pipeX);
            }
        }
        if (edgeOpen(c.floor, c.cx, c.cz, false)) {
            int w = width(c.floor, c.cx, c.cz, false);
            for (int v = C0 + 2; v < S; v++) {
                if (w == 3) c.set(C0, c.fy + 3, v, pipeZ);
                if (k != K_TUNNEL && k != K_HAZARD) continue;
                c.set(C0 - 1, c.fy + 2, v, hot && (v & 1) == 0 ? hotPipe : pipeZ);
                c.set(C0 + w, c.fy + 1, v, pipeZ);
            }
        }
        if (k == K_TUNNEL || k == K_HAZARD) {
            if (Rng.chance(h, 0.4)) {
                c.set(C0 + 1, c.fy + 3, C0 + 1, B.of(Material.LANTERN, "[hanging=true]"));
            } else if (Rng.chance(Rng.hash(h, 1), 0.25) && !edgeOpen(c.floor, c.cx - 1, c.cz, true)) {
                // wall torch faces east, so it needs the solid west wall that only exists without a west arm
                c.set(C0, c.fy + 2, C0, B.of(Material.REDSTONE_WALL_TORCH, "[facing=east,lit=true]"));
            }
        }
    }

    private void utility(CellCtx c) {
        B[] machines = {B.of(Material.BLAST_FURNACE, "[facing=south]"), B.of(Material.SMOKER, "[facing=south]"), B.of(Material.CAULDRON),
                B.of(Material.LODESTONE), B.of(Material.IRON_BLOCK), B.of(Material.OBSERVER, "[facing=up]"), B.of(Material.BARREL, "[facing=up]")};
        for (int u = 1; u <= 6; u++) {
            for (int v = 1; v <= 6; v++) {
                boolean border = u == 1 || v == 1 || u == 6 || v == 6;
                if (!border) continue;
                if (u >= C0 && u <= C0 + 2 || v >= C0 && v <= C0 + 2) continue; // keep arm mouths free
                long hh = c.h(u, 1, v, 0x7111);
                if (Rng.chance(hh, 0.6)) {
                    c.set(u, c.fy + 1, v, machines[Rng.pick(Rng.hash(hh, 1), machines.length)]);
                }
            }
        }
        c.set(C0 + 1, c.fy + 4, C0 + 1, B.of(Material.SHROOMLIGHT));
    }

    private void feature(CellCtx c, Feature f) {
        switch (f.id()) {
            case "ladder_hatch" -> {
                for (int y = c.fy - 4; y <= c.fy; y++) {
                    c.set(4, y, 4, B.of(Material.LADDER, "[facing=south]"));
                    c.set(4, y, 3, B.of(Material.POLISHED_DEEPSLATE));
                }
                c.set(4, c.fy - 5, 4, B.of(Material.SHROOMLIGHT));
                c.set(3, c.fy, 4, GRATE);
                c.set(5, c.fy, 4, GRATE);
            }
            case "stairs_up" -> {
                for (int u = 3; u <= 5; u++) {
                    for (int kk = 0; kk < 2; kk++) {
                        int v = 3 + kk;
                        for (int y = c.fy + 1; y <= c.fy + kk; y++) c.set(u, y, v, B.of(Material.POLISHED_DEEPSLATE));
                        c.set(u, c.fy + 1 + kk, v, B.stairs(Material.POLISHED_DEEPSLATE_STAIRS, "south", false));
                        for (int y = c.fy + 2 + kk; y <= c.fy + 6; y++) c.set(u, y, v, B.AIR);
                    }
                    for (int y = c.fy + 1; y <= c.fy + 3; y++) c.set(u, y, 5, B.of(Material.POLISHED_DEEPSLATE));
                    for (int y = c.fy + 4; y <= c.fy + 6; y++) c.set(u, y, 5, B.AIR);
                    c.set(u, c.fy + 7, 5, B.of(Material.PEARLESCENT_FROGLIGHT));
                }
            }
            case "boiler_room" -> {
                for (int u = 2; u <= 5; u++) {
                    for (int v = 2; v <= 5; v++) {
                        if ((u == 2 || u == 5) && (v == 2 || v == 5)) continue;
                        if (u >= 3 && u <= 4 && v >= 3 && v <= 4) continue;
                        c.set(u, c.fy, v, GRATE);
                        c.set(u, c.fy - 1, v, B.of(Material.MAGMA_BLOCK));
                    }
                }
            }
            default -> {
            }
        }
    }

    @Override
    protected int[] triggerBox(Feature f) {
        return switch (f.id()) {
            case "ladder_hatch" -> new int[]{4, -5, 4, 4, -3, 4};
            case "stairs_up" -> new int[]{3, 3, 5, 5, 4, 5};
            default -> null;
        };
    }

    @Override
    public boolean isSafeCell(int floor, int cx, int cz) {
        int k = kind(floor, cx, cz);
        return k == K_TUNNEL || k == K_JUNCTION || k == K_UTILITY;
    }

    @Override
    public String zoneName(int floor, int cx, int cz) {
        return NAMES[palette(cx, cz)] + (heat(cx * S + 4, cz * S + 4) > 0.68 ? "/hot" : "") + " kind=" + KINDS[kind(floor, cx, cz)];
    }

    @Override
    protected void cellDecor(int floor, int cx, int cz, List<DecorSpec> out) {
        int k = kind(floor, cx, cz);
        int fy = floorY[floor] + 1;
        long h = ch(floor, cx, cz, 0xDEC3);
        if (k == K_ROOMFEATURE) {
            Feature f = featureAt(floor, cx, cz);
            switch (f.id()) {
                case "ladder_hatch" -> out.add(spec("hatch_sign", cx, cz, 4.5, fy + 1.6, 1.02, 0f, f.seed()));
                case "stairs_up" -> out.add(spec("daylight_glow", cx, cz, 4.5, fy + 3, 5.5, 0f, f.seed()));
                case "boiler_room" -> out.add(spec("boiler", cx, cz, 4.0, fy, 4.0, 0f, f.seed())
                        .barrier(cx * S + 3, fy, cz * S + 3, cx * S + 4, fy, cz * S + 3, cx * S + 3, fy, cz * S + 4, cx * S + 4, fy, cz * S + 4,
                                cx * S + 3, fy + 1, cz * S + 3, cx * S + 4, fy + 1, cz * S + 3, cx * S + 3, fy + 1, cz * S + 4, cx * S + 4, fy + 1, cz * S + 4));
                case "valve_gallery" -> {
                    for (int i = 0; i < 4; i++) {
                        out.add(spec("steam_valve", cx, cz, 1.5 + i * 1.4, fy + 1.3, 1.0, 0f, Rng.hash(f.seed(), i)));
                    }
                }
                default -> {
                }
            }
            return;
        }
        if (k == K_HAZARD) {
            out.add(spec("steam_vent_hazard", cx, cz, C0 + 0.5, fy, C0 + 0.5, 0f, h));
            return;
        }
        double density = gs.get("decor-density", 1.0);
        double r = Rng.unit(h) / Math.max(0.05, density);
        boolean e = edgeOpen(floor, cx, cz, true);
        if (r < 0.12 && e) {
            out.add(spec("steam_valve", cx, cz, 6.5, fy + 1.2, C0, 0f, h));
        } else if (r < 0.26) {
            out.add(spec("pipe_cluster", cx, cz, C0 + 1.0, fy + 2.6, C0 + 1.0, e ? -90f : 0f, h));
        } else if (r < 0.32) {
            out.add(spec("gauge", cx, cz, C0 + 0.5, fy + 1.6, C0 - 0.0, 0f, h));
        } else if (r < 0.37 && k == K_UTILITY) {
            out.add(spec("vent_fan", cx, cz, 3.5, fy + 2.2, 1.0, 0f, h));
        } else if (r < 0.40 && k == K_JUNCTION) {
            out.add(spec("ceiling_fan", cx, cz, 4.0, FLOOR + 6, 4.0, 0f, h));
        }
    }
}
