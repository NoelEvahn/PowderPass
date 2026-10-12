package com.powderpass.backrooms.gen;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interned, lazily resolved block state key. Generators describe blocks with these keys so the pure
 * layout code can be unit-tested without a running server; {@link #data()} resolves the real
 * {@link BlockData} once and caches it (BlockData parsing is thread-safe and the result immutable).
 */
public final class B {
    private static final Map<String, B> CACHE = new ConcurrentHashMap<>();

    public static final B AIR = of(Material.AIR);
    public static final B BEDROCK = of(Material.BEDROCK);
    public static final B BARRIER = of(Material.BARRIER);
    public static final B WATER = of(Material.WATER);

    public final Material mat;
    public final String state;
    private volatile BlockData data;

    private B(Material mat, String state) {
        this.mat = mat;
        this.state = state;
    }

    public static B of(Material m) {
        return of(m, "");
    }

    public static B of(Material m, String state) {
        return CACHE.computeIfAbsent(m.name() + state, k -> new B(m, state));
    }

    public static B light(int level) {
        return of(Material.LIGHT, "[level=" + Math.max(0, Math.min(15, level)) + "]");
    }

    public static B stairs(Material m, String facing, boolean top) {
        return of(m, "[facing=" + facing + ",half=" + (top ? "top" : "bottom") + "]");
    }

    public static B slab(Material m, boolean top) {
        return of(m, "[type=" + (top ? "top" : "bottom") + "]");
    }

    public static B axis(Material m, char axis) {
        return of(m, "[axis=" + axis + "]");
    }

    public BlockData data() {
        BlockData d = data;
        if (d == null) {
            d = state.isEmpty() ? Bukkit.createBlockData(mat) : Bukkit.createBlockData(mat.getKey() + state);
            data = d;
        }
        return d;
    }

    /** Conservative "can a player stand inside this block" classification, valid without a server. */
    public boolean passable() {
        return Mats.passable(mat);
    }

    @Override
    public String toString() {
        return mat.name() + state;
    }
}
