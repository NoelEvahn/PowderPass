package com.powderpass.backrooms.display;

import org.bukkit.Material;

/**
 * World supplies lying where someone left them: a bottle of almond water, a ration, a battery, a flashlight, or a
 * small supply crate. Each carries a "supply" hitbox; right-clicking it picks it up ({@code SupplyService}).
 */
final class SupplyModels {
    private SupplyModels() {
    }

    static boolean build(String kind, long seed, Model m) {
        double yaw = (seed & 0xFF) * 360.0 / 256;
        switch (kind) {
            case "almond" -> {
                m.item(Material.POTION, 0, 0.22, 0, 0.42, yaw, 0);
                if ((seed & 0x100) != 0) m.item(Material.POTION, 0.18, 0.22, 0.09, 0.42, yaw + 40, 0);
                m.hitbox("supply", 0, 0, 0, 0.55, 0.5);
            }
            case "ration" -> {
                m.item(Material.BREAD, 0, 0.03, 0, 0.45, yaw, 90);
                m.hitbox("supply", 0, 0, 0, 0.55, 0.3);
            }
            case "battery" -> {
                m.item(Material.COPPER_INGOT, 0, 0.02, 0, 0.32, yaw, 90);
                m.item(Material.COPPER_INGOT, 0.12, 0.04, 0.05, 0.32, yaw + 25, 90);
                m.hitbox("supply", 0, 0, 0, 0.5, 0.3);
            }
            case "flashlight" -> {
                m.item(Material.SPYGLASS, 0, 0.03, 0, 0.5, yaw, 90);
                m.hitbox("supply", 0, 0, 0, 0.55, 0.3);
            }
            case "crate" -> {
                // a small plywood supply crate with darker battens and a stencilled lid
                m.push().rotate('y', Math.round(yaw / 90) * 90 + (seed % 7) - 3);
                m.box(Material.STRIPPED_BIRCH_WOOD, -0.36, 0.0, -0.26, 0.72, 0.46, 0.52);
                for (int s = -1; s <= 1; s += 2) {
                    m.box(Material.STRIPPED_SPRUCE_WOOD, s * 0.36 - (s > 0 ? 0 : 0.03), 0.0, -0.27, 0.03, 0.47, 0.54);
                    m.box(Material.STRIPPED_SPRUCE_WOOD, -0.37, 0.0, s * 0.26 - (s > 0 ? 0 : 0.03), 0.74, 0.06, 0.03);
                    m.box(Material.STRIPPED_SPRUCE_WOOD, -0.37, 0.4, s * 0.26 - (s > 0 ? 0 : 0.03), 0.74, 0.06, 0.03);
                }
                m.box(Material.BLACK_CONCRETE, -0.12, 0.461, -0.08, 0.24, 0.005, 0.16);
                m.pop();
                m.hitbox("supply", 0, 0, 0, 0.85, 0.55);
            }
            default -> {
                return false;
            }
        }
        m.viewRange = 0.6f;
        return true;
    }
}
