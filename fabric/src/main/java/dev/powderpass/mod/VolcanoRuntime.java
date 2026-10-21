package dev.powderpass.mod;

import dev.powderpass.core.volcano.Lifecycle;
import dev.powderpass.core.volcano.VolcanoChannels;
import dev.powderpass.core.volcano.VolcanoSite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import dev.powderpass.core.util.Hash;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Live volcanism. Runs on the server thread from the world tick, does a bounded amount of work per tick, and only
 * touches loaded chunks.
 *
 * <p>Safety model (default, non-destructive):
 * <ul>
 *   <li>Lava is only placed into air directly above generated volcanic rock along the generated channel, and only
 *       where every chunk of the eruption zone is loaded, has no recorded player modification, and has an inhabited
 *       time below the configured threshold (a conservative fallback for modifications we cannot observe, such as
 *       explosions or other mods).</li>
 *   <li>No flammable block may exist within 4 blocks of a release point.</li>
 *   <li>A runaway guard samples the channel margins; lava found outside the expected footprint aborts the
 *       eruption and immediately cools all lava in the footprint.</li>
 *   <li>Cooling only converts blocks that are still lava at positions this volcano placed (plus flowing lava
 *       directly derived from them inside the footprint); other blocks are never rewritten.</li>
 * </ul>
 * If containment cannot be established, the eruption is downgraded to ambient effects only. With
 * {@code destructiveVolcanism} the zone checks are skipped and vanilla lava/fire may damage builds.
 */
public final class VolcanoRuntime {
    private static final int SCAN_INTERVAL = 200;
    private static final int STEP = 20; // lifecycle updated every 20 ticks

    private VolcanoRuntime() {}

    static Lifecycle.Params params() {
        PowderConfig.Runtime r = PowderPassMod.config().runtime;
        Lifecycle.Params p = new Lifecycle.Params();
        p.dormantMin = (long) (r.dormancyDaysMin * 24000);
        p.dormantMax = (long) (r.dormancyDaysMax * 24000);
        p.unrest = (long) (r.unrestDays * 24000);
        p.eruption = r.eruptionTicks;
        p.cooling = r.coolingTicks;
        p.lavaBudget = r.eruptionLavaBudget;
        return p;
    }

    public static VolcanoData data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(VolcanoData.TYPE);
    }

    public static void tick(ServerLevel level) {
        if (!(level.getChunkSource().getGenerator() instanceof PowderChunkGenerator gen)) return;
        PowderConfig.Runtime cfg = PowderPassMod.config().runtime;
        if (!cfg.volcanoActivity) return;
        long time = level.getGameTime();
        VolcanoData data = data(level);
        WorldContext ctx = gen.context(level.getChunkSource().randomState());
        Lifecycle.Params p = params();
        if (time % SCAN_INTERVAL == 0) discover(level, ctx, data, p, cfg);
        if (time % STEP != 0) {
            // Lava release / cooling work runs every tick within the per-tick budget.
            work(level, ctx, data, cfg);
            return;
        }
        int active = 0;
        for (Lifecycle.State s : data.volcanoes.values()) if (s.phase == Lifecycle.Phase.ERUPTION) active++;
        for (Lifecycle.State s : data.volcanoes.values()) {
            if (s.phase == Lifecycle.Phase.EXTINCT) continue;
            if (!loaded(level, (int) s.x, (int) s.z)) continue; // paused while unloaded: no catch-up later
            Lifecycle.Phase before = s.phase;
            Lifecycle.Phase t = Lifecycle.advance(s, STEP, p, active < cfg.maxActiveEruptions);
            if (t == Lifecycle.Phase.ERUPTION) {
                active++;
                VolcanoSite v = ctx.world.volcanoes.byId(s.id, s.x, s.z);
                String why = v == null ? "unknown volcano" : containment(level, ctx, v, data, cfg);
                s.suppressed = why != null;
                s.note = why == null ? "erupting" : "ambient only: " + why;
                if (!s.suppressed) registerCheckpoints(ctx, v, s);
                PowderPassMod.LOGGER.info("PowderPass volcano {} at {},{} begins erupting ({})", s.id, (int) s.x, (int) s.z, s.note);
            }
            if (t != null || before != s.phase) data.setDirty();
            if (cfg.ambientEffects) ambient(level, s);
        }
        work(level, ctx, data, cfg);
        if (time % 100 == 0) data.setDirty();
    }

    /** Creates state entries for volcano sites near players (deterministic metadata; no chunk generation). */
    private static void discover(ServerLevel level, WorldContext ctx, VolcanoData data, Lifecycle.Params p, PowderConfig.Runtime cfg) {
        for (ServerPlayer pl : level.players()) {
            double r = cfg.volcanoScanRadius;
            for (VolcanoSite v : ctx.world.volcanoes.sitesIn(pl.getX() - r, pl.getZ() - r, pl.getX() + r, pl.getZ() + r)) {
                if (!data.volcanoes.containsKey(v.id())) {
                    data.volcanoes.put(v.id(), Lifecycle.initial(v, p));
                    data.setDirty();
                }
            }
        }
    }

    static boolean loaded(ServerLevel level, int x, int z) {
        return level.getChunkSource().getChunkNow(x >> 4, z >> 4) != null;
    }

    /** Returns null when the eruption zone is provably pristine and contained; otherwise the reason. */
    static String containment(ServerLevel level, WorldContext ctx, VolcanoSite v, VolcanoData data, PowderConfig.Runtime cfg) {
        VolcanoChannels.Geometry g = ctx.sampler().volcanoChannels().geometry(v);
        double zoneR = v.radius() * 0.85 + 16;
        int c0x = (int) Math.floor((v.x() - zoneR) / 16), c1x = (int) Math.floor((v.x() + zoneR) / 16);
        int c0z = (int) Math.floor((v.z() - zoneR) / 16), c1z = (int) Math.floor((v.z() + zoneR) / 16);
        for (int cz = c0z; cz <= c1z; cz++) {
            for (int cx = c0x; cx <= c1x; cx++) {
                LevelChunk ch = level.getChunkSource().getChunkNow(cx, cz);
                if (ch == null) return "eruption zone not fully loaded";
                if (cfg.destructiveVolcanism) continue;
                if (data.touched(Hash.pack(cx, cz))) return "player modifications recorded in zone";
                if (ch.getInhabitedTime() > cfg.inhabitedTicksThreshold) return "zone inhabited too long (possible builds)";
            }
        }
        if (cfg.destructiveVolcanism) return null;
        // Flammables near any release point -> not contained (lava ignites fire up to a few blocks away).
        for (int k = 0; k < 10_000; k++) {
            Lifecycle.Target t = Lifecycle.target(g, k);
            if (t == null) break;
            if (t.x() == Integer.MIN_VALUE) continue;
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int dy = -1; dy <= 4; dy++) for (int dz = -4; dz <= 4; dz++) for (int dx = -4; dx <= 4; dx++) {
                m.set(t.x() + dx, t.y() + dy, t.z() + dz);
                BlockState s = level.getBlockState(m);
                if (s.ignitedByLava()) return "flammable blocks near the lava path";
            }
        }
        return null;
    }

    /** Bounded per-tick world changes: release lava during eruptions, cool lava during cooling. */
    private static void work(ServerLevel level, WorldContext ctx, VolcanoData data, PowderConfig.Runtime cfg) {
        int budget = cfg.lavaPlacementsPerTick;
        for (Lifecycle.State s : data.volcanoes.values()) {
            if (budget <= 0) return;
            if (!loaded(level, (int) s.x, (int) s.z)) continue;
            if (s.phase == Lifecycle.Phase.ERUPTION && !s.suppressed && s.budgetLeft > 0) {
                VolcanoSite v = ctx.world.volcanoes.byId(s.id, s.x, s.z);
                if (v == null) continue;
                VolcanoChannels.Geometry g = ctx.sampler().volcanoChannels().geometry(v);
                if (level.getGameTime() % 40 == 0 && runaway(level, g, s)) {
                    s.note = "aborted: lava outside expected footprint";
                    s.suppressed = true;
                    s.budgetLeft = 0;
                    Lifecycle.enter(s, Lifecycle.Phase.COOLING, params());
                    data.setDirty();
                    continue;
                }
                // Release at most one point per tick per volcano, spread over the eruption duration.
                long spacing = Math.max(1, s.duration / Math.max(1, cfg.eruptionLavaBudget));
                if (s.elapsed % spacing != 0 && s.cursor > 0) continue;
                Lifecycle.Target t = Lifecycle.target(g, s.cursor);
                if (t == null) { s.budgetLeft = 0; continue; }
                s.cursor++;
                if (t.x() == Integer.MIN_VALUE) continue;
                if (t.y() > ctx.world.settings.terrainCap()) continue;
                BlockPos pos = new BlockPos(t.x(), t.y(), t.z());
                if (!loaded(level, t.x(), t.z())) continue;
                BlockState cur = level.getBlockState(pos);
                BlockState below = level.getBlockState(pos.below());
                boolean natural = below.is(Blocks.BASALT) || below.is(Blocks.SMOOTH_BASALT) || below.is(Blocks.BLACKSTONE)
                        || below.is(Blocks.MAGMA_BLOCK) || below.is(Blocks.TUFF) || below.is(Blocks.GRAVEL) || below.is(Blocks.LAVA);
                if (!(cur.isAir() || cur.is(Blocks.LAVA)) || (!natural && !cfg.destructiveVolcanism)) continue;
                level.setBlock(pos, Blocks.LAVA.defaultBlockState(), 3);
                s.addLava(Lifecycle.pack(t.x(), t.y(), t.z()));
                s.budgetLeft--;
                budget--;
                data.setDirty();
            } else if (s.phase == Lifecycle.Phase.COOLING && s.lavaCount > 0) {
                // Cool from the newest placement backwards; convert lava (source or flowing) in a small box around
                // each tracked source, leaving any non-lava block untouched.
                long packed = s.lava[s.lavaCount - 1];
                int x = Lifecycle.unpackX(packed), y = Lifecycle.unpackY(packed), z = Lifecycle.unpackZ(packed);
                if (!loaded(level, x, z)) continue;
                coolAround(level, x, y, z);
                s.lavaCount--;
                budget--;
                data.setDirty();
            }
        }
    }

    /**
     * Cooling checkpoints along every channel and basin. They are stored first in the tracked list (cooling runs from
     * the end), so after the placed sources are cooled the whole path down to the basins is swept as well.
     */
    static void registerCheckpoints(WorldContext ctx, VolcanoSite v, Lifecycle.State s) {
        VolcanoChannels.Geometry g = ctx.sampler().volcanoChannels().geometry(v);
        long[] old = s.lavaPositions();
        s.lavaCount = 0;
        for (VolcanoChannels.Channel c : g.channels()) {
            int n = c.size();
            for (int i = 0; i < n; i += 2) s.addLava(Lifecycle.pack((int) Math.floor(c.x()[i]), (int) c.floor()[i] + 1, (int) Math.floor(c.z()[i])));
            double ex = c.x()[n - 1], ez = c.z()[n - 1];
            int by = (int) c.floor()[n - 1];
            for (int a = 0; a < 8; a++) {
                double ang = a * Math.PI / 4;
                s.addLava(Lifecycle.pack((int) Math.floor(ex + Math.cos(ang) * 4), by, (int) Math.floor(ez + Math.sin(ang) * 4)));
            }
            s.addLava(Lifecycle.pack((int) Math.floor(ex), by, (int) Math.floor(ez)));
        }
        for (long o : old) s.addLava(o);
    }

    private static void coolAround(ServerLevel level, int x, int y, int z) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dy = -6; dy <= 1; dy++) for (int dz = -4; dz <= 4; dz++) for (int dx = -4; dx <= 4; dx++) {
            m.set(x + dx, y + dy, z + dz);
            BlockState s = level.getBlockState(m);
            if (!s.is(Blocks.LAVA)) continue;
            boolean source = s.getFluidState().isSource();
            BlockState rock = source ? (dx == 0 && dz == 0 ? Blocks.MAGMA_BLOCK : Blocks.BASALT).defaultBlockState()
                    : ((x + dx + z + dz) % 3 == 0 ? Blocks.BLACKSTONE : Blocks.SMOOTH_BASALT).defaultBlockState();
            level.setBlock(m, rock, 3);
        }
    }

    /** Samples positions 4-6 blocks beside the channel; any lava there means the flow left the groove. */
    private static boolean runaway(ServerLevel level, VolcanoChannels.Geometry g, Lifecycle.State s) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (VolcanoChannels.Channel c : g.channels()) {
            for (int i = 1; i + 1 < c.size(); i += 2) {
                double tx = c.x()[i + 1] - c.x()[i - 1], tz = c.z()[i + 1] - c.z()[i - 1];
                double l = Math.hypot(tx, tz);
                if (l < 1e-6) continue;
                double nx = -tz / l, nz = tx / l;
                for (int side = -1; side <= 1; side += 2) {
                    for (int off = 4; off <= 6; off += 2) {
                        int x = (int) Math.floor(c.x()[i] + nx * off * side), z = (int) Math.floor(c.z()[i] + nz * off * side);
                        if (!loaded(level, x, z)) continue;
                        for (int dy = -2; dy <= 3; dy++) {
                            m.set(x, (int) c.floor()[i] + dy, z);
                            if (level.getBlockState(m).is(Blocks.LAVA)) return true;
                        }
                    }
                }
            }
        }
        // Basin rims: lava beyond the levee means the basin overflowed.
        for (VolcanoChannels.Channel c : g.channels()) {
            int n = c.size();
            double ex = c.x()[n - 1], ez = c.z()[n - 1];
            for (int a = 0; a < 12; a++) {
                double ang = a * Math.PI / 6;
                int x = (int) Math.floor(ex + Math.cos(ang) * (VolcanoChannels.BASIN_R + 4));
                int z = (int) Math.floor(ez + Math.sin(ang) * (VolcanoChannels.BASIN_R + 4));
                if (!loaded(level, x, z)) continue;
                for (int dy = -3; dy <= 2; dy++) {
                    m.set(x, (int) c.floor()[n - 1] + dy, z);
                    if (level.getBlockState(m).is(Blocks.LAVA)) return true;
                }
            }
        }
        return false;
    }

    private static void ambient(ServerLevel level, Lifecycle.State s) {
        if (s.phase == Lifecycle.Phase.DORMANT) return;
        WorldContext ctx = ((PowderChunkGenerator) level.getChunkSource().getGenerator()).context(level.getChunkSource().randomState());
        VolcanoSite v = ctx.world.volcanoes.byId(s.id, s.x, s.z);
        if (v == null) return;
        VolcanoChannels.Geometry g = ctx.sampler().volcanoChannels().geometry(v);
        double x = g.ventX() + 0.5, y = g.ventY() + 1.5, z = g.ventZ() + 0.5;
        switch (s.phase) {
            case UNREST -> {
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, 2, 2.0, 0.5, 2.0, 0.01);
                if (level.getRandom().nextInt(12) == 0) level.playSound(null, BlockPos.containing(x, y, z), SoundEvents.LAVA_AMBIENT, SoundSource.BLOCKS, 2.0f, 0.5f);
            }
            case ERUPTION -> {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 2, z, 12, 3.0, 2.0, 3.0, 0.05);
                level.sendParticles(ParticleTypes.LAVA, x, y, z, 6, 1.5, 0.5, 1.5, 0.1);
                level.sendParticles(ParticleTypes.ASH, x, y + 6, z, 30, 12.0, 6.0, 12.0, 0.02);
                if (level.getRandom().nextInt(4) == 0) level.playSound(null, BlockPos.containing(x, y, z), SoundEvents.LAVA_POP, SoundSource.BLOCKS, 3.0f, 0.6f);
            }
            case COOLING -> level.sendParticles(ParticleTypes.SMOKE, x, y, z, 3, 2.0, 0.5, 2.0, 0.01);
            default -> { }
        }
    }

    /** Manual trigger for diagnostics. Returns a status line. */
    public static String trigger(ServerLevel level, double px, double pz) {
        if (!(level.getChunkSource().getGenerator() instanceof PowderChunkGenerator gen)) return "Not a PowderPass world.";
        WorldContext ctx = gen.context(level.getChunkSource().randomState());
        VolcanoSite v = ctx.diagnostics.nearestVolcano(px, pz, true, 16000);
        if (v == null) return "No eruption-capable volcano within 16000 blocks.";
        VolcanoData data = data(level);
        Lifecycle.Params p = params();
        Lifecycle.State s = data.volcanoes.computeIfAbsent(v.id(), id -> Lifecycle.initial(v, p));
        if (s.phase == Lifecycle.Phase.ERUPTION || s.phase == Lifecycle.Phase.COOLING) return "Volcano already " + s.phase + ".";
        Lifecycle.enter(s, Lifecycle.Phase.ERUPTION, p);
        String why = containment(level, ctx, v, data, PowderPassMod.config().runtime);
        s.suppressed = why != null;
        s.note = why == null ? "test eruption" : "ambient only: " + why;
        if (!s.suppressed) registerCheckpoints(ctx, v, s);
        data.setDirty();
        return String.format(java.util.Locale.ROOT, "Triggered %s volcano at %d %d (vent y %d): %s", v.type(), (int) v.x(), (int) v.z(),
                ctx.sampler().volcanoChannels().geometry(v).ventY(), s.note);
    }

    public static List<String> status(ServerLevel level, double px, double pz) {
        List<String> out = new ArrayList<>();
        if (!(level.getChunkSource().getGenerator() instanceof PowderChunkGenerator gen)) {
            out.add("Not a PowderPass world.");
            return out;
        }
        WorldContext ctx = gen.context(level.getChunkSource().randomState());
        VolcanoSite v = ctx.diagnostics.nearestVolcano(px, pz, false, 16000);
        if (v == null) {
            out.add("No volcano within 16000 blocks.");
            return out;
        }
        Lifecycle.State s = data(level).volcanoes.get(v.id());
        out.add(String.format(java.util.Locale.ROOT, "%s %s volcano at %d %d, radius %d, height %d, %s", v.origin(), v.type(),
                (int) v.x(), (int) v.z(), (int) v.radius(), (int) v.height(), v.canErupt() ? "eruption-capable" : "extinct"));
        if (s == null) out.add("No runtime state yet (it is created when a player comes within the scan radius).");
        else out.add(String.format(java.util.Locale.ROOT, "phase %s, %d/%d ticks, cycle %d, lava budget left %d, tracked lava %d %s",
                s.phase, s.elapsed, s.duration == Long.MAX_VALUE ? -1 : s.duration, s.cycle, s.budgetLeft, s.lavaCount, s.note));
        return out;
    }

    public static String calm(ServerLevel level, double px, double pz) {
        if (!(level.getChunkSource().getGenerator() instanceof PowderChunkGenerator gen)) return "Not a PowderPass world.";
        WorldContext ctx = gen.context(level.getChunkSource().randomState());
        VolcanoSite v = ctx.diagnostics.nearestVolcano(px, pz, true, 16000);
        if (v == null) return "No eruption-capable volcano nearby.";
        Lifecycle.State s = data(level).volcanoes.get(v.id());
        if (s == null) return "Volcano has no runtime state.";
        if (s.phase == Lifecycle.Phase.ERUPTION) {
            Lifecycle.enter(s, Lifecycle.Phase.COOLING, params());
            data(level).setDirty();
            return "Eruption stopped; cooling " + s.lavaCount + " tracked lava sources.";
        }
        return "Volcano is " + s.phase + ".";
    }
}
