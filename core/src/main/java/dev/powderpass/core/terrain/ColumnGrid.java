package dev.powderpass.core.terrain;

/**
 * Per-chunk column data with a 1-column halo, including the water-containment ("bank") rule.
 *
 * <p>Water containment: a dry column next to water is raised so no water block is ever adjacent to air at its own
 * level. For lakes and sea the bank top must reach the top water block; for rivers it must reach one block higher,
 * because stepped reaches spill a thin layer of flowing water over the next pool after fluid ticks run. The rule
 * reads only neighbouring water levels (pure functions of position), never neighbouring raises, so it is identical
 * in every chunk that evaluates a column.
 */
public final class ColumnGrid {
    public static final int PAD = 1;
    public static final int SIZE = 16 + 2 * PAD;
    public static final int NO_WATER = Integer.MIN_VALUE;

    public final int x0, z0;               // world coords of grid index (0,0)
    public final TerrainSampler.Column[] cols = new TerrainSampler.Column[SIZE * SIZE];
    public final int[] rawTop = new int[SIZE * SIZE];
    public final int[] top = new int[SIZE * SIZE];       // top solid block y (after containment)
    public final int[] waterTop = new int[SIZE * SIZE];  // top water block y or NO_WATER
    public final byte[] waterKind = new byte[SIZE * SIZE];
    public final float[] slope = new float[SIZE * SIZE]; // gradient magnitude (rise/run), inner columns
    public final boolean[] raised = new boolean[SIZE * SIZE];

    public ColumnGrid(TerrainSampler sampler, int chunkX, int chunkZ) {
        x0 = chunkX * 16 - PAD;
        z0 = chunkZ * 16 - PAD;
        for (int j = 0; j < SIZE; j++) {
            for (int i = 0; i < SIZE; i++) {
                int k = j * SIZE + i;
                TerrainSampler.Column c = new TerrainSampler.Column();
                sampler.sample(x0 + i, z0 + j, c);
                cols[k] = c;
                rawTop[k] = (int) Math.floor(c.height);
                if (!Double.isNaN(c.waterLevel)) {
                    int wt = (int) Math.floor(c.waterLevel) - 1;
                    if (wt > rawTop[k]) {
                        waterTop[k] = wt;
                        waterKind[k] = c.waterKind;
                    } else {
                        waterTop[k] = NO_WATER;
                    }
                } else {
                    waterTop[k] = NO_WATER;
                }
            }
        }
        for (int j = 0; j < SIZE; j++) {
            for (int i = 0; i < SIZE; i++) {
                int k = j * SIZE + i;
                top[k] = containedTop(i, j);
                raised[k] = top[k] != rawTop[k];
            }
        }
        for (int j = 1; j < SIZE - 1; j++) {
            for (int i = 1; i < SIZE - 1; i++) {
                double dx = (cols[j * SIZE + i + 1].height - cols[j * SIZE + i - 1].height) * 0.5;
                double dz = (cols[(j + 1) * SIZE + i].height - cols[(j - 1) * SIZE + i].height) * 0.5;
                slope[j * SIZE + i] = (float) Math.sqrt(dx * dx + dz * dz);
            }
        }
    }

    private int containedTop(int i, int j) {
        int k = j * SIZE + i;
        int t = rawTop[k];
        if (waterTop[k] != NO_WATER) return t;
        int req = Integer.MIN_VALUE;
        for (int dj = -1; dj <= 1; dj++) {
            for (int di = -1; di <= 1; di++) {
                int ni = i + di, nj = j + dj;
                if (ni < 0 || nj < 0 || ni >= SIZE || nj >= SIZE || (di == 0 && dj == 0)) continue;
                int nk = nj * SIZE + ni;
                if (waterTop[nk] == NO_WATER) continue;
                int need = waterTop[nk] + (waterKind[nk] == TerrainSampler.WATER_RIVER ? 1 : 0);
                req = Math.max(req, need);
            }
        }
        // Halo columns cannot see their outer neighbours; they are only used for slope/biome context, while each
        // inner column is evaluated with all 8 neighbours available.
        return Math.max(t, req);
    }

    public int index(int localX, int localZ) {
        return (localZ + PAD) * SIZE + localX + PAD;
    }

    public TerrainSampler.Column column(int localX, int localZ) {
        return cols[index(localX, localZ)];
    }
}
