package dev.powderpass.mod;

import dev.powderpass.core.terrain.B;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the core's block ids and block-state strings to vanilla {@link BlockState}s. Parsing is done once per
 * distinct string. Ids that do not exist in the running game version fall back to a close vanilla substitute and
 * are logged once, so a renamed block can never silently become air.
 */
public final class BlockPalette {
    private static final BlockState[] BASE = new BlockState[B.values().length];
    private static final Map<String, BlockState> STATES = new ConcurrentHashMap<>();
    private static final Map<String, String> FALLBACK = Map.of(
            "minecraft:short_dry_grass", "minecraft:short_grass",
            "minecraft:tall_dry_grass", "minecraft:tall_grass",
            "minecraft:bush", "minecraft:fern",
            "minecraft:firefly_bush", "minecraft:fern",
            "minecraft:wildflowers", "minecraft:dandelion",
            "minecraft:leaf_litter", "minecraft:air",
            "minecraft:pink_petals", "minecraft:dandelion");

    private BlockPalette() {}

    public static BlockState base(int ordinal) {
        BlockState s = BASE[ordinal];
        if (s == null) {
            s = parse(B.byOrdinal(ordinal).id);
            BASE[ordinal] = s;
        }
        return s;
    }

    public static BlockState state(String spec) {
        return STATES.computeIfAbsent(spec, BlockPalette::parse);
    }

    /** Parses {@code namespace:id[prop=value,...]} using the block's own state definition. */
    static BlockState parse(String spec) {
        String id = spec;
        String props = null;
        int br = spec.indexOf('[');
        if (br >= 0) {
            id = spec.substring(0, br);
            props = spec.substring(br + 1, spec.length() - 1);
        }
        Identifier key = Identifier.parse(id);
        if (!BuiltInRegistries.BLOCK.containsKey(key)) {
            String fb = FALLBACK.get(id);
            PowderPassMod.LOGGER.warn("PowderPass: block {} not present in this game version; using {}", id, fb == null ? "stone" : fb);
            return fb == null ? Blocks.STONE.defaultBlockState() : parse(fb);
        }
        Block block = BuiltInRegistries.BLOCK.getValue(key);
        BlockState state = block.defaultBlockState();
        if (props != null && !props.isEmpty()) {
            for (String kv : props.split(",")) {
                String[] p = kv.split("=", 2);
                if (p.length != 2) continue;
                Property<?> prop = block.getStateDefinition().getProperty(p[0].trim());
                if (prop == null) continue;
                state = with(state, prop, p[1].trim());
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState s, Property<T> prop, String value) {
        Optional<T> v = prop.getValue(value);
        return v.isPresent() ? s.setValue(prop, v.get()) : s;
    }
}
