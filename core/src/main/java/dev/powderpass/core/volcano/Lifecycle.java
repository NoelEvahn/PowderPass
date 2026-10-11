package dev.powderpass.core.volcano;

import dev.powderpass.core.util.Hash;

import java.util.Arrays;

/**
 * Server-authoritative volcano lifecycle: DORMANT -> UNREST -> ERUPTION -> COOLING -> DORMANT, plus EXTINCT.
 *
 * <p>Time only advances while the volcano's area is loaded ({@link #advance} is called with the ticks that actually
 * elapsed in loaded state), so unloaded volcanoes pause and there is never a catch-up burst. Durations are drawn
 * deterministically from (volcano id, cycle) so a restart reproduces the same schedule. The world side (placing and
 * cooling lava) is performed by the mod; this class decides <em>when</em> and <em>where</em>.
 */
public final class Lifecycle {
    public enum Phase { DORMANT, UNREST, ERUPTION, COOLING, EXTINCT }

    /** Tunables (ticks). */
    public static final class Params {
        public long dormantMin = 4 * 24000L, dormantMax = 12 * 24000L;
        public long unrest = 24000L;
        public long eruption = 9000L;
        public long cooling = 6000L;
        public int lavaBudget = 180;
    }

    /** Mutable persisted state of one volcano. */
    public static final class State {
        public long id;
        public double x, z;
        public Phase phase = Phase.DORMANT;
        public long elapsed;        // ticks spent (loaded) in the current phase
        public long duration;       // planned length of the current phase
        public int cycle;
        public int budgetLeft;
        public int cursor;          // progress along the lava path in the current eruption
        public boolean suppressed;  // eruption downgraded to ambient-only (containment not guaranteed)
        public String note = "";
        public long[] lava = new long[0]; // packed positions of lava sources placed by this volcano (still to cool)
        public int lavaCount;

        public void addLava(long packed) {
            if (lavaCount == lava.length) lava = Arrays.copyOf(lava, Math.max(16, lavaCount * 2));
            lava[lavaCount++] = packed;
        }

        public long[] lavaPositions() {
            return Arrays.copyOf(lava, lavaCount);
        }
    }

    private Lifecycle() {}

    public static State initial(VolcanoSite v, Params p) {
        State s = new State();
        s.id = v.id();
        s.x = v.x();
        s.z = v.z();
        if (!v.canErupt()) {
            s.phase = Phase.EXTINCT;
            s.duration = Long.MAX_VALUE;
            return s;
        }
        s.phase = Phase.DORMANT;
        s.duration = dormantDuration(v.id(), 0, p);
        // Desynchronise volcanoes: start part-way through the first dormancy.
        s.elapsed = (long) (Hash.unit(Hash.mix(v.id() ^ 0xD0)) * s.duration * 0.6);
        return s;
    }

    static long dormantDuration(long id, int cycle, Params p) {
        double u = Hash.unit(Hash.hash(id, cycle, 0xD011));
        return p.dormantMin + (long) (u * (p.dormantMax - p.dormantMin));
    }

    /**
     * Advances by {@code dt} loaded ticks. {@code eruptionSlot} says whether another eruption may start now.
     * Returns the phase transition that happened (or null).
     */
    public static Phase advance(State s, long dt, Params p, boolean eruptionSlot) {
        if (s.phase == Phase.EXTINCT) return null;
        s.elapsed += dt;
        if (s.elapsed < s.duration) {
            if (s.phase == Phase.ERUPTION && s.budgetLeft <= 0 && s.elapsed > s.duration / 3) {
                return enter(s, Phase.COOLING, p);
            }
            return null;
        }
        return switch (s.phase) {
            case DORMANT -> enter(s, Phase.UNREST, p);
            case UNREST -> eruptionSlot ? enter(s, Phase.ERUPTION, p) : null; // waits (stays restless) for a slot
            case ERUPTION -> enter(s, Phase.COOLING, p);
            case COOLING -> s.lavaCount == 0 ? enter(s, Phase.DORMANT, p) : null; // finish converting lava first
            default -> null;
        };
    }

    /** Forces a phase (commands / tests). */
    public static Phase enter(State s, Phase next, Params p) {
        s.phase = next;
        s.elapsed = 0;
        switch (next) {
            case DORMANT -> {
                s.cycle++;
                s.duration = dormantDuration(s.id, s.cycle, p);
                s.suppressed = false;
                s.note = "";
            }
            case UNREST -> s.duration = p.unrest;
            case ERUPTION -> {
                s.duration = p.eruption;
                s.budgetLeft = p.lavaBudget;
                s.cursor = 0;
            }
            case COOLING -> s.duration = p.cooling;
            case EXTINCT -> s.duration = Long.MAX_VALUE;
        }
        return next;
    }

    /** One lava release target. */
    public record Target(int x, int y, int z) {}

    /**
     * The k-th lava release point of an eruption: the vent first, then successive points down each channel
     * (channels interleaved). Returns null when the path is exhausted. Pure function of the geometry and cursor.
     */
    public static Target target(VolcanoChannels.Geometry g, int cursor) {
        if (cursor == 0) return new Target(g.ventX(), g.ventY() + 1, g.ventZ());
        int n = g.channels().length;
        if (n == 0) return null;
        int idx = cursor - 1;
        int ch = idx % n, step = idx / n + 1;
        VolcanoChannels.Channel c = g.channels()[ch];
        // Release every 2nd vertex (~6 blocks); stop two thirds down so flows end inside the generated basin.
        int vi = step * 2;
        if (vi >= c.size() * 2 / 3) {
            // This channel is finished; check whether any channel still has points.
            boolean any = false;
            for (VolcanoChannels.Channel o : g.channels()) if (step * 2 < o.size() * 2 / 3) any = true;
            return any ? new Target(Integer.MIN_VALUE, 0, 0) : null;
        }
        return new Target((int) Math.floor(c.x()[vi]), (int) c.floor()[vi] + 1, (int) Math.floor(c.z()[vi]));
    }

    public static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    public static int unpackX(long p) {
        return (int) (p >> 38);
    }

    public static int unpackY(long p) {
        return (int) (p << 52 >> 52);
    }

    public static int unpackZ(long p) {
        return (int) (p << 26 >> 38);
    }
}
