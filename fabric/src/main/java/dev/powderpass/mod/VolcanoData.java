package dev.powderpass.mod;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.powderpass.core.volcano.Lifecycle;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-world persistent state: volcano lifecycles (including every lava block the mod placed and has not cooled yet),
 * chunks known to contain player modifications, and whether the PowderPass spawn has been applied.
 */
public final class VolcanoData extends SavedData {
    private record Entry(long id, double x, double z, int phase, long elapsed, long duration, int cycle, int budget,
                         int cursor, boolean suppressed, String note, List<Long> lava) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("id").forGetter(Entry::id),
                Codec.DOUBLE.fieldOf("x").forGetter(Entry::x),
                Codec.DOUBLE.fieldOf("z").forGetter(Entry::z),
                Codec.INT.fieldOf("phase").forGetter(Entry::phase),
                Codec.LONG.fieldOf("elapsed").forGetter(Entry::elapsed),
                Codec.LONG.fieldOf("duration").forGetter(Entry::duration),
                Codec.INT.fieldOf("cycle").forGetter(Entry::cycle),
                Codec.INT.fieldOf("budget").forGetter(Entry::budget),
                Codec.INT.fieldOf("cursor").forGetter(Entry::cursor),
                Codec.BOOL.fieldOf("suppressed").forGetter(Entry::suppressed),
                Codec.STRING.optionalFieldOf("note", "").forGetter(Entry::note),
                Codec.LONG.listOf().fieldOf("lava").forGetter(Entry::lava)
        ).apply(i, Entry::new));

        static Entry of(Lifecycle.State s) {
            List<Long> lava = new ArrayList<>(s.lavaCount);
            for (int k = 0; k < s.lavaCount; k++) lava.add(s.lava[k]);
            return new Entry(s.id, s.x, s.z, s.phase.ordinal(), s.elapsed, s.duration, s.cycle, s.budgetLeft, s.cursor,
                    s.suppressed, s.note, lava);
        }

        Lifecycle.State toState() {
            Lifecycle.State s = new Lifecycle.State();
            s.id = id; s.x = x; s.z = z;
            s.phase = Lifecycle.Phase.values()[Math.max(0, Math.min(Lifecycle.Phase.values().length - 1, phase))];
            s.elapsed = elapsed; s.duration = duration; s.cycle = cycle; s.budgetLeft = budget; s.cursor = cursor;
            s.suppressed = suppressed; s.note = note;
            for (long l : lava) s.addLava(l);
            return s;
        }
    }

    public static final Codec<VolcanoData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Entry.CODEC.listOf().fieldOf("volcanoes").forGetter(VolcanoData::entries),
            Codec.LONG.listOf().fieldOf("touched_chunks").forGetter(d -> new ArrayList<>(d.touched)),
            Codec.BOOL.fieldOf("spawn_applied").forGetter(d -> d.spawnApplied)
    ).apply(i, VolcanoData::new));

    public static final SavedDataType<VolcanoData> TYPE = new SavedDataType<>("powderpass_state", VolcanoData::new, CODEC, null);

    final Map<Long, Lifecycle.State> volcanoes = new LinkedHashMap<>();
    final Set<Long> touched = new HashSet<>();
    boolean spawnApplied;

    public VolcanoData() {
    }

    private VolcanoData(List<Entry> entries, List<Long> touched, boolean spawnApplied) {
        for (Entry e : entries) volcanoes.put(e.id(), e.toState());
        this.touched.addAll(touched);
        this.spawnApplied = spawnApplied;
    }

    private List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        for (Lifecycle.State s : volcanoes.values()) out.add(Entry.of(s));
        return out;
    }

    public boolean touched(long chunkKey) {
        return touched.contains(chunkKey);
    }

    public void markTouched(long chunkKey) {
        if (touched.add(chunkKey)) setDirty();
    }
}
