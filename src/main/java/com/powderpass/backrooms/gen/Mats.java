package com.powderpass.backrooms.gen;

import org.bukkit.Material;

/** Server-independent material classification used by layout validation and unit tests. */
public final class Mats {
    private Mats() {
    }

    public static boolean passable(Material m) {
        if (m == Material.AIR || m == Material.CAVE_AIR || m == Material.LIGHT || m == Material.WATER) {
            return true;
        }
        String n = m.name();
        return n.endsWith("_CARPET") || n.endsWith("_PRESSURE_PLATE") || n.endsWith("_BUTTON")
                || n.endsWith("_TORCH") || n.equals("TORCH") || n.endsWith("_DOOR") || n.equals("LADDER")
                || n.endsWith("_WALL_TORCH") || n.equals("LEVER") || n.equals("RAIL") || n.equals("TRIPWIRE")
                || n.endsWith("_CANDLE") || n.equals("CANDLE") || n.equals("COBWEB") || n.equals("REDSTONE_WIRE")
                || n.equals("SHORT_GRASS") || n.equals("TALL_GRASS");
    }

    public static boolean liquid(Material m) {
        return m == Material.WATER || m == Material.LAVA;
    }
}
