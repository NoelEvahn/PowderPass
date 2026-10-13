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
import com.powderpass.backrooms.util.ValueNoise;
import org.bukkit.Material;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Plains. Empty fields of grass and moss rolling very gently under a sky stuck at sunset. No roads, no wires:
 * a house stands alone in the grass now and then, facing nowhere in particular, and here and there a handful of
 * them sit together at odd angles like they were dropped. Everyday things forty blocks tall stand in the fields
 * (display models): a chair, a lamp that is off, a traffic cone, a rubber duck, a telephone. Every so often the
 * ground opens into a sinkhole about a hundred blocks across that narrows into a shaft and falls out of the world.
 *
 * <p>Layout works per region ({@link #R} x {@link #R} cells of {@link #S} blocks): each region gets a plan that
 * says which cells hold a feature, a sinkhole, a giant object, a house, or nothing. A sinkhole only goes into a
 * region with no feature in it (and never the region at the world origin) and stays inside that region, so it
 * can never swallow an exit, the hatch back to Level 4 or a safe arrival spot.</p>
 */
public final class PlainsGenerator extends LevelGenerator {
    private static final int FLOOR = 64;
    /** The world's minimum build height (normal overworld): the sinkhole shafts are open below it, into the void. */
    public static final int HOLE_BOTTOM = -64;
    static final int K_FIELD = 0, K_HOUSE = 1, K_COLOSSAL = 2, K_FEATURE = 3, K_HOLE = 4;
    static final int V_NORMAL = 0, V_NO_DOOR = 1, V_HIGH_DOOR = 2, V_SUNK = 3, V_FLOATING = 4, V_MONOLITH = 5, V_DOOR_TO_WALL = 6;
    private static final String[] VARIANTS = {"house", "doorless", "high-door", "sunken", "floating", "monolith", "door-to-wall"};
    /** Relative weights of the variants: most houses look almost right. */
    private static final int[] VARIANT_WEIGHT = {6, 2, 1, 1, 1, 1, 2};
    /** The giant objects standing in the fields (display models, about forty blocks tall; a few older, smaller ones). */
    public static final String[] COLOSSI = {
            "giant_dining_chair", "giant_lamp", "giant_cone", "giant_duck", "giant_crt", "giant_phone", "giant_fan",
            "giant_shoe", "giant_mug", "giant_teddy", "giant_pencil", "giant_hydrant", "giant_bucket", "giant_lightbulb",
            "giant_figure", "giant_outlet", "giant_stairs", "lone_wall", "floating_panel"};

    /** Yaw facing the house front / the back wall, per orientation. */
    private static final float[] FRONT_YAW = {180f, 90f, 0f, -90f};
    private static final float[] BACK_YAW = {0f, -90f, 180f, 90f};

    private static final B GRASS = B.of(Material.GRASS_BLOCK);
    private static final B DIRT = B.of(Material.DIRT);
    private static final B STONE = B.of(Material.STONE);
    private static final B MOSS = B.of(Material.MOSS_BLOCK);
    private static final B COARSE = B.of(Material.COARSE_DIRT);
    private static final B ROOTED = B.of(Material.ROOTED_DIRT);
    private static final B TUFF = B.of(Material.TUFF);
    private static final B ANDESITE = B.of(Material.ANDESITE);
    private static final B DEEPSLATE = B.of(Material.DEEPSLATE);
    private static final B HOUSE_WALL = B.of(Material.WHITE_CONCRETE);
    private static final B HOUSE_FLOOR = B.of(Material.SPRUCE_PLANKS);
    private static final B WINDOW = B.of(Material.TINTED_GLASS);
    private static final B ROOF = B.of(Material.DEEPSLATE_TILES);
    private static final B CONCRETE = B.of(Material.GRAY_CONCRETE);
    private static final B TILE = B.of(Material.WHITE_CONCRETE);

    /** A sinkhole: centre (block coordinates), base radius at the surface, noise seed. */
    public record Hole(double x, double z, double r, long seed) {
        /** Largest possible surface radius (the rim noise stays within +-15%). */
        public double maxRim() {
            return r * 1.15;
        }
    }

    /** A house placed in a cell: orientation, variant, cell-local origin of its 11x12 (or 12x11) bounding box. */
    record House(int o, int variant, int bu, int bv) {
        int w() {
            return o % 2 == 0 ? 11 : 12;
        }

        int d() {
            return o % 2 == 0 ? 12 : 11;
        }
    }

    /** What one region holds. */
    static final class Plan {
        final int[] kind;
        final House[] house;
        Hole hole;

        Plan(int n) {
            kind = new int[n];
            house = new House[n];
        }
    }

    private final Map<Long, Plan> plans = new ConcurrentHashMap<>();

    public PlainsGenerator(long seed, GenSettings gs) {
        super(Level.PLAINS, seed, gs, 32, 4, 3, new int[]{FLOOR}, FLOOR - 20, FLOOR + 40, 0.0, 0.0);
    }

    @Override
    protected List<FeatureDef> featureDefs() {
        return List.of(
                // the end of the chain: the only exit there is, in every district; a hatch back down to the Office
                FeatureDef.transition("giant_doorframe", Dest.EXIT, 1.0),
                FeatureDef.transition("bunker_hatch", Dest.to(Level.LEVEL_4), gs.get("back-chance", 0.4)),
                // still there, still wrong, but they lead nowhere now
                FeatureDef.landmark("yellow_house", gs.get("landmark-chance", 0.6)),
                FeatureDef.landmark("backyard_pool", gs.get("landmark-chance", 0.6)));
    }

    @Override
    public String biomeKey() {
        return "minecraft:pale_garden";
    }

    // ------------------------------------------------------------------ region plans

    Plan plan(int rx, int rz) {
        long key = ((long) rx << 32) ^ (rz & 0xffffffffL);
        Plan p = plans.get(key);
        if (p != null) return p;
        if (plans.size() > 4096) plans.clear();
        p = buildPlan(rx, rz);
        plans.put(key, p);
        return p;
    }

    private Plan buildPlan(int rx, int rz) {
        Plan p = new Plan(R * R);
        long h = Rng.hash(seed ^ 0x9A1A5L, rx, rz);
        boolean anyFeature = false;
        for (Feature f : districtFeatures(Math.floorDiv(rx, D), Math.floorDiv(rz, D))) {
            if (Math.floorDiv(f.cx(), R) == rx && Math.floorDiv(f.cz(), R) == rz) {
                p.kind[(f.cx() - rx * R) + (f.cz() - rz * R) * R] = K_FEATURE;
                anyFeature = true;
            }
        }
        int span = S * R;
        // a sinkhole: only in a region with no feature, never where the world spawns, and fully inside the region
        if (!anyFeature && !(rx == 0 && rz == 0) && Rng.chance(Rng.hash(h, 1), gs.get("sinkhole-chance", 0.08))) {
            double r = 43 + Rng.unit(Rng.hash(h, 2)) * 4;
            double x = rx * span + span / 2.0 + (Rng.unit(Rng.hash(h, 3)) - 0.5) * 4;
            double z = rz * span + span / 2.0 + (Rng.unit(Rng.hash(h, 4)) - 0.5) * 4;
            p.hole = new Hole(x, z, r, Rng.hash(h, 5));
            double reach = p.hole.maxRim() + 6;
            for (int i = 0; i < R * R; i++) {
                int x0 = (rx * R + i % R) * S, z0 = (rz * R + i / R) * S;
                double nx = Math.max(x0, Math.min(x, x0 + S)), nz = Math.max(z0, Math.min(z, z0 + S));
                if (Math.hypot(nx - x, nz - z) < reach) p.kind[i] = K_HOLE;
            }
            return p;
        }
        // a small group of houses dropped together: no street, odd angles and spacing
        if (Rng.chance(Rng.hash(h, 10), gs.get("hamlet-chance", 0.15))) {
            int ccx = 1 + Rng.pick(Rng.hash(h, 11), R - 2), ccz = 1 + Rng.pick(Rng.hash(h, 12), R - 2);
            int want = 3 + Rng.pick(Rng.hash(h, 13), 5);
            int[] order = shuffled(Rng.hash(h, 14), 9);
            for (int k = 0; k < 9 && want > 0; k++) {
                int i = (ccx - 1 + order[k] % 3) + (ccz - 1 + order[k] / 3) * R;
                if (p.kind[i] != K_FIELD) continue;
                p.kind[i] = K_HOUSE;
                p.house[i] = randomHouse(Rng.hash(h, 20, i));
                want--;
            }
        }
        // the giant things: usually one per region, often a second
        int[] order = shuffled(Rng.hash(h, 30), R * R);
        double[] giants = {gs.get("giant-chance", 0.8), gs.get("giant-second-chance", 0.4)};
        int slot = 0;
        for (int g = 0; g < giants.length; g++) {
            if (!Rng.chance(Rng.hash(h, 31, g), giants[g])) continue;
            while (slot < order.length && p.kind[order[slot]] != K_FIELD) slot++;
            if (slot < order.length) p.kind[order[slot++]] = K_COLOSSAL;
        }
        // and a lone house in the grass now and then: away from the region's edge and from any other house
        for (int i = 0; i < R * R; i++) {
            int lu = i % R, lv = i / R;
            if (lu == 0 || lv == 0 || lu == R - 1 || lv == R - 1 || p.kind[i] != K_FIELD) continue;
            boolean alone = true;
            for (int j = 0; j < R * R; j++) {
                if (p.kind[j] == K_HOUSE && Math.abs(j % R - lu) <= 1 && Math.abs(j / R - lv) <= 1) alone = false;
            }
            if (alone && Rng.chance(Rng.hash(h, 40, i), gs.get("lone-house-chance", 0.08))) {
                p.kind[i] = K_HOUSE;
                p.house[i] = randomHouse(Rng.hash(h, 41, i));
            }
        }
        return p;
    }

    private static int[] shuffled(long h, int n) {
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = i;
        for (int i = n - 1; i > 0; i--) {
            int j = Rng.pick(Rng.hash(h, i), i + 1);
            int t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
        return a;
    }

    private House randomHouse(long h) {
        int o = Rng.pick(h, 4);
        int total = 0;
        for (int w : VARIANT_WEIGHT) total += w;
        int r = Rng.pick(Rng.hash(h, 1), total), variant = 0;
        while (r >= VARIANT_WEIGHT[variant]) r -= VARIANT_WEIGHT[variant++];
        // bounding box at least 6 blocks from the cell edge, anywhere else
        int w = o % 2 == 0 ? 11 : 12, d = o % 2 == 0 ? 12 : 11;
        int bu = Rng.range(Rng.hash(h, 2), 6, S - 6 - w), bv = Rng.range(Rng.hash(h, 3), 6, S - 6 - d);
        return new House(o, variant, bu, bv);
    }

    private Plan planOfCell(int cx, int cz) {
        return plan(Math.floorDiv(cx, R), Math.floorDiv(cz, R));
    }

    private static int idx(Plan p, int cx, int cz, int r) {
        return Math.floorMod(cx, r) + Math.floorMod(cz, r) * r;
    }

    public int kind(int cx, int cz) {
        Plan p = planOfCell(cx, cz);
        return p.kind[idx(p, cx, cz, R)];
    }

    House house(int cx, int cz) {
        Plan p = planOfCell(cx, cz);
        return p.house[idx(p, cx, cz, R)];
    }

    /** The sinkhole whose area contains (or could contain) the block column, or null. */
    public Hole holeAt(int x, int z) {
        Plan p = planOfCell(cellOf(x), cellOf(z));
        return p.hole;
    }

    /** The sinkhole of a region, or null. */
    public Hole regionHole(int rx, int rz) {
        return plan(rx, rz).hole;
    }

    /** Anchor of a cell's giant object (cell-local), jittered around the cell centre. */
    private double[] colossusAnchor(int cx, int cz) {
        long h = ch(0, cx, cz, 0xC0A7);
        return new double[]{S / 2.0 + (Rng.unit(h) - 0.5) * 6, S / 2.0 + (Rng.unit(Rng.hash(h, 1)) - 0.5) * 6};
    }

    String colossusModel(int cx, int cz) {
        return COLOSSI[Rng.pick(ch(0, cx, cz, 0xC010), COLOSSI.length)];
    }

    // ------------------------------------------------------------------ terrain

    /**
     * Height of the ground relative to the base floor at a column: gentle swells of 0 to 3 blocks. Around a
     * house or a giant object the ground levels out to the height it has at their spot; over features it levels
     * to the base floor; towards a sinkhole it sags a few blocks, so the hole shows from a distance.
     */
    public int lift(int x, int z) {
        int cx = cellOf(x), cz = cellOf(z), u = localOf(x), v = localOf(z);
        int k = kind(cx, cz);
        double h = rawLift(x, z);
        switch (k) {
            case K_FEATURE -> {
                int e = Math.min(Math.min(u, v), Math.min(S - 1 - u, S - 1 - v));
                h *= clamp01((5 - e) / 5.0);
            }
            case K_HOUSE -> {
                House hs = house(cx, cz);
                int du = Math.max(Math.max(hs.bu() - u, u - (hs.bu() + hs.w() - 1)), 0);
                int dv = Math.max(Math.max(hs.bv() - v, v - (hs.bv() + hs.d() - 1)), 0);
                double base = baseLift(cx, cz);
                h = base + (h - base) * clamp01((Math.max(du, dv) - 1) / 5.0);
            }
            case K_COLOSSAL -> {
                double[] a = colossusAnchor(cx, cz);
                double base = baseLift(cx, cz);
                h = base + (h - base) * clamp01((Math.hypot(u + 0.5 - a[0], v + 0.5 - a[1]) - 7) / 4.0);
            }
            case K_HOLE -> h -= sag(planOfCell(cx, cz).hole, x, z);
            default -> {
            }
        }
        return (int) Math.max(-4, Math.min(3, Math.round(h)));
    }

    /** The unflattened swell at a column, 0 to about 3. */
    private double rawLift(double x, double z) {
        double n = ValueNoise.fractal(seed ^ 0x7011L, x, z, 56);
        return Math.max(0, (n - 0.42) / 0.5) * 3.4;
    }

    /** Level a house or giant object stands on (relative to the base floor): the swell at its centre. */
    int baseLift(int cx, int cz) {
        double x, z;
        if (kind(cx, cz) == K_HOUSE) {
            House hs = house(cx, cz);
            x = cx * S + hs.bu() + hs.w() / 2.0;
            z = cz * S + hs.bv() + hs.d() / 2.0;
        } else {
            double[] a = colossusAnchor(cx, cz);
            x = cx * S + a[0];
            z = cz * S + a[1];
        }
        return (int) Math.max(0, Math.min(3, Math.round(rawLift(x, z))));
    }

    /** How far the ground sinks towards a sinkhole's rim (0 to 3), fading out before the edge of its cells. */
    static double sag(Hole h, int x, int z) {
        double dx = x + 0.5 - h.x(), dz = z + 0.5 - h.z();
        double d = Math.hypot(dx, dz);
        double rimR = rim(h, Math.atan2(dz, dx));
        double t = clamp01(1 - (d - rimR) / 15.0);
        double w = clamp01((h.maxRim() + 6 - d) / 4.0);
        return 3.2 * t * t * (3 - 2 * t) * w;
    }

    private static double clamp01(double d) {
        return Math.max(0, Math.min(1, d));
    }

    /** Surface radius of a sinkhole in a direction: irregular, constant with depth near the top. */
    static double rim(Hole h, double ang) {
        double c = Math.cos(ang), s = Math.sin(ang);
        double n1 = ValueNoise.sample(h.seed(), 40 + c * 2.2, 40 + s * 2.2);
        double n2 = ValueNoise.sample(h.seed() ^ 0x2L, 40 + c * 6.5, 40 + s * 6.5);
        double f = 1 + 0.2 * (n1 - 0.5) + 0.08 * (n2 - 0.5);
        return h.r() * Math.max(0.85, Math.min(1.15, f));
    }

    /**
     * Open radius of a sinkhole at height y in a direction: the surface rim, narrowing ever faster with depth
     * (1 - t^3) to a shaft about five blocks wide at the bottom of the world. The upper walls are close to vertical,
     * so stepping off the rim is a fall of thirty blocks or more; the shaft below is open into the void.
     */
    public static double holeRadius(Hole h, double ang, int y, double rimR, int top) {
        int k = Math.max(0, FLOOR - y);
        double t = Math.min(1, k / (double) (FLOOR - HOLE_BOTTOM));
        double base = 2.6 + (rimR - 2.6) * (1 - t * t * t);
        double c = Math.cos(ang), s = Math.sin(ang);
        double rough = (ValueNoise.sample(h.seed() ^ 0x3L, 30 + c * 4.5 + y * 0.19, 30 + s * 4.5 - y * 0.16) - 0.5) * 3.2;
        rough *= Math.min(1, Math.pow(k / 45.0, 1.5));
        // the upper walls are undercut under a three-block lip of ground, so nothing breaks a fall from the edge
        double undercut = 2.5 * Math.max(0, Math.min(1, (top - y) / 3.0)) * Math.max(0, 1 - k / 60.0);
        return Math.max(1.9, base + rough + undercut);
    }

    // ------------------------------------------------------------------ painting

    @Override
    protected void paintCell(CellCtx c) {
        int k = kind(c.cx, c.cz);
        Hole hole = k == K_HOLE ? planOfCell(c.cx, c.cz).hole : null;
        for (int u = c.u0; u <= c.u1; u++) {
            for (int v = c.v0; v <= c.v1; v++) {
                int wx = c.wx(u), wz = c.wz(v);
                int top = c.fy + lift(wx, wz);
                if (hole != null && sinkholeColumn(c, u, v, hole, top)) continue;
                c.set(u, bottomY, v, B.BEDROCK);
                for (int y = bottomY + 1; y < top - 3; y++) c.set(u, y, v, STONE);
                for (int y = top - 3; y < top; y++) c.set(u, y, v, DIRT);
                c.set(u, top, v, ground(wx, wz));
                vegetation(c, u, v, top);
            }
        }
        if (k == K_HOUSE) {
            House hs = house(c.cx, c.cz);
            house(c, hs, false);
        } else if (k == K_FEATURE) {
            Feature f = featureAt(0, c.cx, c.cz);
            switch (f.id()) {
                case "yellow_house" -> house(c, new House(0, V_NORMAL, 11, 3), true);
                case "backyard_pool" -> pool(c);
                case "bunker_hatch" -> bunker(c);
                default -> {
                }
            }
        }
    }

    /**
     * Paints one column near a sinkhole: solid rock from the bottom of the world up to the surface out to a few
     * blocks past the rim, with the funnel carved out of it. Returns false when the column is ordinary ground.
     */
    private boolean sinkholeColumn(CellCtx c, int u, int v, Hole h, int top) {
        int wx = c.wx(u), wz = c.wz(v);
        double dx = wx + 0.5 - h.x(), dz = wz + 0.5 - h.z();
        double d = Math.hypot(dx, dz);
        if (d > h.maxRim() + 6) return false;
        double ang = Math.atan2(dz, dx);
        double rimR = rim(h, ang);
        if (d > rimR + 5) return false;
        long salt = h.seed();
        for (int y = HOLE_BOTTOM; y <= top + 3; y++) {
            if (d < holeRadius(h, ang, y, rimR, top)) {
                c.set(u, y, v, B.AIR);
                continue;
            }
            B b;
            if (y > top) b = B.AIR;
            else if (y == top) b = d - rimR < 1.5 + 2 * ValueNoise.sample(salt ^ 0x5L, wx / 4.0, wz / 4.0)
                    ? (ValueNoise.sample(salt ^ 0x6L, wx / 3.0, wz / 3.0) > 0.55 ? ROOTED : COARSE) : ground(wx, wz);
            else if (y > top - 4 - (int) (3 * ValueNoise.sample(salt ^ 0x7L, wx / 6.0, wz / 6.0))) b = DIRT;
            else if (y < 0) b = DEEPSLATE;
            else {
                double band = ValueNoise.sample(salt ^ 0x8L, (wx + wz) / 9.0, y / 3.0);
                b = band > 0.72 ? TUFF : band < 0.22 ? ANDESITE : STONE;
            }
            c.set(u, y, v, b);
        }
        // nothing grows right at the edge
        if (d > rimR + 2.5) vegetation(c, u, v, top);
        return true;
    }

    /**
     * Ground cover: grass and moss, roughly three to one, in smooth correlated patches, with the odd coarse-dirt
     * scar; never salt-and-pepper.
     */
    B ground(int wx, int wz) {
        if (ValueNoise.sample(seed ^ 0x3055L, wx / 16.0, wz / 16.0) > 0.68) return MOSS;
        if (ValueNoise.sample(seed ^ 0xC0A5L, wx / 15.0, wz / 15.0) > 0.885) return COARSE;
        return GRASS;
    }

    /** Sparse short grass in loose drifts, a rare tall clump; most of the field is bare. */
    private void vegetation(CellCtx c, int u, int v, int top) {
        int wx = c.wx(u), wz = c.wz(v);
        if (ValueNoise.sample(seed ^ 0x6A55L, wx / 5.0, wz / 5.0) < 0.84) return;
        if (c.type(u, top, v) != Material.GRASS_BLOCK && c.type(u, top, v) != Material.MOSS_BLOCK) return;
        long h = c.h(u, 0, v, 0x6A5);
        if (Rng.chance(h, 0.06)) {
            c.set(u, top + 1, v, B.of(Material.TALL_GRASS, "[half=lower]"));
            c.set(u, top + 2, v, B.of(Material.TALL_GRASS, "[half=upper]"));
        } else if (Rng.chance(Rng.hash(h, 1), 0.5)) {
            c.set(u, top + 1, v, B.of(Material.SHORT_GRASS));
        }
    }

    // house frame: local hx 0..8 (across), hz 0..6 (front to back), front at hz = 0; the bounding box also covers
    // the roof overhang (hx -1..9, hz -1..7) and the yard in front (hz down to -4)
    private static int[] toUV(House h, int hx, int hz) {
        int a = hx + 1, b = hz + 4;
        return switch (h.o()) {
            case 0 -> new int[]{h.bu() + a, h.bv() + b};
            case 1 -> new int[]{h.bu() + b, h.bv() + (10 - a)};
            case 2 -> new int[]{h.bu() + (10 - a), h.bv() + (11 - b)};
            default -> new int[]{h.bu() + (11 - b), h.bv() + a};
        };
    }

    /** World direction names of local +hz (towards the back) and -hz (front). */
    private static String back(int o) {
        return switch (o) {
            case 0 -> "south";
            case 1 -> "east";
            case 2 -> "north";
            default -> "west";
        };
    }

    private static String front(int o) {
        return switch (o) {
            case 0 -> "north";
            case 1 -> "west";
            case 2 -> "south";
            default -> "east";
        };
    }

    private void house(CellCtx c, House hs, boolean yellow) {
        int o = hs.o(), variant = hs.variant();
        int fy = c.fy + (yellow ? 0 : baseLift(c.cx, c.cz));
        int y0 = fy + (variant == V_SUNK ? -2 : variant == V_FLOATING ? 5 : 0);
        for (int hx = 0; hx <= 8; hx++) {
            for (int hz = 0; hz <= 6; hz++) {
                int[] uv = toUV(hs, hx, hz);
                boolean wall = hx == 0 || hx == 8 || hz == 0 || hz == 6;
                c.set(uv[0], y0, uv[1], yellow ? B.of(Material.YELLOW_TERRACOTTA) : HOUSE_FLOOR);
                for (int hy = 1; hy <= 4; hy++) {
                    B b = B.AIR;
                    if (wall) {
                        b = HOUSE_WALL;
                        boolean windowSpot = (hy == 2 || hy == 3) && ((hz == 0 || hz == 6) && (hx == 2 || hx == 6) || (hx == 0 || hx == 8) && hz == 3);
                        if (windowSpot && variant != V_MONOLITH) b = variant == V_NO_DOOR && hz == 0 ? HOUSE_WALL : WINDOW;
                    } else if (yellow && (hx == 1 || hx == 7 || hz == 1 || hz == 5)) {
                        b = hy == 4 ? B.AIR : B.of(Material.SMOOTH_SANDSTONE);
                        if (hz == 1 && hx == 4 && hy <= 2) b = B.AIR;
                    }
                    c.set(uv[0], y0 + hy, uv[1], b);
                }
                // flat ceiling, then a gabled roof (ridge along hx)
                c.set(uv[0], y0 + 5, uv[1], ROOF);
            }
        }
        if (variant == V_FLOATING) {
            // clear any grass growing under the floating house
            for (int hx = -1; hx <= 9; hx++) for (int hz = -1; hz <= 7; hz++) {
                int[] uv = toUV(hs, hx, hz);
                for (int y = fy + 1; y < y0; y++) c.set(uv[0], y, uv[1], B.AIR);
            }
        }
        for (int k = 0; k <= 3; k++) {
            for (int hx = -1; hx <= 9; hx++) {
                int[] f = toUV(hs, hx, -1 + k), b = toUV(hs, hx, 7 - k);
                c.set(f[0], y0 + 5 + k, f[1], B.stairs(Material.DEEPSLATE_TILE_STAIRS, back(o), false));
                c.set(b[0], y0 + 5 + k, b[1], B.stairs(Material.DEEPSLATE_TILE_STAIRS, front(o), false));
                if (k > 0 && (hx == 0 || hx == 8)) {
                    for (int hz = k; hz <= 6 - k; hz++) {
                        int[] g = toUV(hs, hx, hz);
                        c.set(g[0], y0 + 5 + k, g[1], HOUSE_WALL);
                    }
                }
            }
        }
        for (int hx = -1; hx <= 9; hx++) {
            int[] r = toUV(hs, hx, 3);
            c.set(r[0], y0 + 9, r[1], B.slab(Material.DEEPSLATE_TILE_SLAB, false));
        }
        // the door, or what passes for one
        int[] d = toUV(hs, 4, 0);
        String facing = front(o);
        switch (variant) {
            case V_NORMAL, V_SUNK, V_DOOR_TO_WALL -> {
                c.set(d[0], y0 + 1, d[1], B.of(Material.SPRUCE_DOOR, "[facing=" + facing + ",half=lower,hinge=left,open=false]"));
                c.set(d[0], y0 + 2, d[1], B.of(Material.SPRUCE_DOOR, "[facing=" + facing + ",half=upper,hinge=left,open=false]"));
                if (variant == V_DOOR_TO_WALL) {
                    int[] w = toUV(hs, 4, 1);
                    c.set(w[0], y0 + 1, w[1], HOUSE_WALL);
                    c.set(w[0], y0 + 2, w[1], HOUSE_WALL);
                }
            }
            case V_HIGH_DOOR -> {
                c.set(d[0], y0 + 3, d[1], B.of(Material.SPRUCE_DOOR, "[facing=" + facing + ",half=lower,hinge=left,open=false]"));
                c.set(d[0], y0 + 4, d[1], B.of(Material.SPRUCE_DOOR, "[facing=" + facing + ",half=upper,hinge=left,open=false]"));
            }
            default -> {
            }
        }
        if (variant == V_SUNK) {
            // trench in front of the door so the sunken doorway is still reachable
            for (int dz = 1; dz <= 2; dz++) {
                int[] t = toUV(hs, 4, -dz);
                c.set(t[0], fy + 1, t[1], B.AIR);
                c.set(t[0], fy, t[1], B.AIR);
                c.set(t[0], fy - 1, t[1], B.AIR);
                c.set(t[0], fy - 2, t[1], dz == 2 ? DIRT : HOUSE_FLOOR);
                if (dz == 2) c.set(t[0], fy - 1, t[1], B.stairs(Material.SPRUCE_STAIRS, front(o), false));
            }
        }
    }

    private void pool(CellCtx c) {
        int depth = 12;
        for (int u = 11; u <= 20; u++) {
            for (int v = 12; v <= 19; v++) {
                boolean rim = u == 11 || u == 20 || v == 12 || v == 19;
                if (rim) {
                    for (int y = c.fy - depth; y <= c.fy; y++) c.set(u, y, v, TILE);
                    c.set(u, c.fy + 1, v, B.AIR);
                } else {
                    c.set(u, c.fy - depth, v, B.of(Material.LIGHT_BLUE_CONCRETE));
                    for (int y = c.fy - depth + 1; y <= c.fy; y++) c.set(u, y, v, B.WATER);
                    c.set(u, c.fy + 1, v, B.AIR);
                }
            }
        }
        c.set(15, c.fy - depth + 1, 15, B.of(Material.LIGHT, "[level=12,waterlogged=true]"));
    }

    private void bunker(CellCtx c) {
        for (int u = 13; u <= 17; u++) {
            for (int v = 13; v <= 17; v++) {
                boolean edge = u == 13 || u == 17 || v == 13 || v == 17;
                for (int y = c.fy + 1; y <= c.fy + 3; y++) c.set(u, y, v, edge ? CONCRETE : B.AIR);
                c.set(u, c.fy + 4, v, CONCRETE);
            }
        }
        c.set(15, c.fy + 1, 13, B.AIR);
        c.set(15, c.fy + 2, 13, B.AIR);
        for (int u = 14; u <= 16; u++) {
            for (int k = 0; k < 3; k++) {
                int v = 14 + k;
                c.set(u, c.fy - k, v, B.stairs(Material.STONE_STAIRS, "north", false));
                for (int y = c.fy - k + 1; y <= c.fy; y++) c.set(u, y, v, B.AIR);
                for (int y = c.fy - 4; y < c.fy - k; y++) c.set(u, y, v, CONCRETE);
            }
            for (int v = 17; v <= 19; v++) {
                c.set(u, c.fy - 4, v, CONCRETE);
                c.set(u, c.fy - 3, v, CONCRETE);
                for (int y = c.fy - 2; y <= c.fy - 1; y++) c.set(u, y, v, B.AIR);
                c.set(u, c.fy, v, CONCRETE);
            }
        }
        for (int v = 14; v <= 20; v++) {
            for (int y = c.fy - 4; y <= c.fy; y++) {
                c.set(13, y, v, CONCRETE);
                c.set(17, y, v, CONCRETE);
            }
        }
        for (int u = 13; u <= 17; u++) for (int y = c.fy - 4; y <= c.fy; y++) c.set(u, y, 20, CONCRETE);
        c.set(15, c.fy - 2, 19, B.light(6));
    }

    @Override
    protected int[] triggerBox(Feature f) {
        return switch (f.id()) {
            case "yellow_house" -> new int[]{14, 0, 10, 18, 1, 11};
            case "backyard_pool" -> new int[]{12, -12, 13, 19, -10, 18};
            case "bunker_hatch" -> new int[]{14, -3, 17, 16, -2, 19};
            case "giant_doorframe" -> new int[]{14, 0, 16, 17, 2, 16};
            default -> null;
        };
    }

    // ------------------------------------------------------------------ safety

    /** Cell-local spot on the base floor (no swell) closest to the cell centre, or null. */
    private int[] flatSpot(int cx, int cz) {
        for (int r = 0; r <= 9; r++) {
            for (int du = -r; du <= r; du++) {
                for (int dv = -r; dv <= r; dv++) {
                    if (Math.max(Math.abs(du), Math.abs(dv)) != r) continue;
                    int u = S / 2 + du, v = S / 2 + dv;
                    if (lift(cx * S + u, cz * S + v) == 0) return new int[]{u, v};
                }
            }
        }
        return null;
    }

    @Override
    public boolean isSafeCell(int floor, int cx, int cz) {
        return kind(cx, cz) == K_FIELD && flatSpot(cx, cz) != null;
    }

    @Override
    public int[] safeLocal(int floor, int cx, int cz) {
        int[] s = kind(cx, cz) == K_FIELD ? flatSpot(cx, cz) : null;
        return s != null ? s : super.safeLocal(floor, cx, cz);
    }

    @Override
    public String zoneName(int floor, int cx, int cz) {
        return switch (kind(cx, cz)) {
            case K_HOUSE -> VARIANTS[house(cx, cz).variant()];
            case K_COLOSSAL -> colossusModel(cx, cz);
            case K_FEATURE -> featureAt(0, cx, cz).id();
            case K_HOLE -> "sinkhole";
            default -> "field";
        };
    }

    // ------------------------------------------------------------------ decorations

    @Override
    protected void cellDecor(int floor, int cx, int cz, List<DecorSpec> out) {
        long h = ch(floor, cx, cz, 0xDEC7);
        int fy = FLOOR + 1;
        switch (kind(cx, cz)) {
            case K_HOUSE -> {
                House hs = house(cx, cz);
                int o = hs.o(), var = hs.variant();
                int base = baseLift(cx, cz);
                int y0 = fy + base - 1 + (var == V_SUNK ? -2 : var == V_FLOATING ? 5 : 0);
                int[] mb = toUV(hs, 6, -3);
                if (var != V_FLOATING && var != V_MONOLITH && Rng.chance(Rng.hash(h, 7), 0.6)) {
                    out.add(spec("mailbox", cx, cz, mb[0] + 0.5, fy + base, mb[1] + 0.5, FRONT_YAW[o], h));
                }
                if (var == V_NORMAL || var == V_DOOR_TO_WALL || var == V_SUNK) {
                    int[] chair = toUV(hs, 4, 5);
                    out.add(spec("chair", cx, cz, chair[0] + 0.5, y0 + 1, chair[1] + 0.5, BACK_YAW[o], h)); // facing the back wall
                    int[] lp = toUV(hs, 2, 4);
                    out.add(lamp("floor_lamp", cx * S + lp[0] + 0.5, y0 + 1, cz * S + lp[1] + 0.5, 0f, h, 0, cx * S + lp[0], y0 + 2, cz * S + lp[1], 11));
                }
            }
            case K_COLOSSAL -> {
                String model = colossusModel(cx, cz);
                double[] a = colossusAnchor(cx, cz);
                int gy = fy + baseLift(cx, cz);
                out.add(new DecorSpec(model, cx * S + a[0], model.equals("floating_panel") ? gy + 60 : gy, cz * S + a[1],
                        (float) Rng.pick(h, 360), 0f, 0f, h));
            }
            case K_FEATURE -> {
                Feature f = featureAt(0, cx, cz);
                switch (f.id()) {
                    case "yellow_house" -> {
                        out.add(lamp("ceiling_panel", cx * S + 16.5, fy + 4, cz * S + 10.5, 0f, f.seed(), 0, cx * S + 16, fy + 3, cz * S + 10, 14));
                        out.add(spec("mailbox", cx, cz, 17.5, fy, 5.5, 180f, h));
                    }
                    case "backyard_pool" -> {
                        out.add(spec("pool_lamp", cx, cz, 15.5, fy - 12 + 0.02, 15.5, 0f, f.seed()));
                        out.add(spec("pool_railing", cx, cz, 12.5, fy, 13.5, 0f, f.seed()));
                    }
                    case "bunker_hatch" -> out.add(spec("wall_lamp", cx, cz, 15.5, fy - 1.5, 19.98, 180f, f.seed()));
                    case "giant_doorframe" -> out.add(new DecorSpec("giant_doorframe", cx * S + 16.0, fy, cz * S + 16.5, 0f, 0f, 0f, f.seed()));
                    default -> {
                    }
                }
            }
            case K_FIELD -> {
                double r = Rng.unit(Rng.hash(h, 3)) / Math.max(0.05, gs.get("decor-density", 1.0));
                String model = null;
                double u = 0, v = 0;
                float yaw = (float) Rng.pick(h, 360);
                if (r < 0.03) {
                    model = "chair";
                    u = 9.5;
                    v = 22.5;
                } else if (r < 0.05) {
                    model = "fallen_panel";
                    u = 21.5;
                    v = 9.5;
                } else if (r < 0.065) {
                    model = "mailbox";
                    u = 10.5;
                    v = 21.5;
                } else if (r < 0.08) {
                    model = "lone_doorframe";
                    u = 20.5;
                    v = 12.5;
                    yaw = (float) Rng.pick(h, 4) * 90f;
                } else if (r < 0.088) {
                    model = "exit_sign_post";
                    u = 8.5;
                    v = 8.5;
                }
                if (model != null) {
                    int bx = cx * S + (int) u, bz = cz * S + (int) v;
                    out.add(spec(model, cx, cz, u, fy + lift(bx, bz), v, yaw, h));
                }
            }
            default -> {
            }
        }
    }

    @Override
    protected void cellDetails(int floor, int cx, int cz, List<DecorSpec> out) {
        if (kind(cx, cz) != K_FIELD) return;
        long h = ch(floor, cx, cz, 0xD7A5);
        int fy = FLOOR + 1;
        // a few thin grass-blade clumps per field, now and then a pale dry one; nothing else
        for (int i = 0; i < 3; i++) {
            long hi = Rng.hash(h, i);
            double u = 5 + Rng.unit(hi) * (S - 10), v = 5 + Rng.unit(Rng.hash(hi, 1)) * (S - 10);
            boolean dry = Rng.chance(Rng.hash(hi, 2), 0.2);
            double x = cx * S + u, z = cz * S + v;
            out.add(detail(DecorSpec.TIER_ENV, Rng.hash(hi, 3), "d:grass:" + (3 + Rng.pick(Rng.hash(hi, 4), 3)) + (dry ? ":dry" : ""),
                    x, fy + lift((int) Math.floor(x), (int) Math.floor(z)), z, (float) Rng.pick(Rng.hash(hi, 5), 360), 0f));
        }
    }
}
