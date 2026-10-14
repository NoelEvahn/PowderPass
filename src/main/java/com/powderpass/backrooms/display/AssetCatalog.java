package com.powderpass.backrooms.display;

import com.powderpass.backrooms.creature.CreatureModels;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every display model the plugin can build, by category, for {@code /backrooms assets}. An asset id is a model id
 * ({@link ModelLibrary#build}); most take an optional {@code :suffix} (a lamp's {@code :flicker} or {@code :dead}, a
 * floatie's colour, a slide's type). Creatures are {@code creature:<type>}, slides {@code slide:<type>}.
 */
public final class AssetCatalog {
    private static final Map<String, List<String>> CATEGORIES = new LinkedHashMap<>();

    static {
        cat("pool", "floatie_ring", "floatie_duck", "floatie_mattress", "beach_ball", "pool_lounger", "pool_bench", "submerged_bench",
                "floating_chair_pool", "lifeguard_chair", "lifebuoy", "pool_railing", "pool_lamp", "drain_grate", "drain_vortex", "caustics",
                "greek_bust", "floor_sign", "water_slide", "slide_water");
        cat("slides", "slide:open", "slide:half", "slide:enclosed", "slide:open:dry", "slide:enclosed:dry", "slide_ride");
        cat("lights", "ceiling_panel", "panel_light", "panel_flicker", "tube_light", "cage_lamp", "wall_lamp", "skylight_panel",
                "floor_lamp", "table_lamp", "desk_lamp", "tower_lamp", "roof_beacon", "sunbeam", "daylight_door", "daylight_window");
        cat("office", "pc_monitor", "crt_monitor", "task_chair", "exec_chair", "office_chair", "paper_stack", "filing_cabinet",
                "lobby_sofa", "revolving_door", "phone_desk", "whiteboard", "water_cooler", "vending_machine", "copier", "potted_plant",
                "lift_panel", "lift_sign", "elevator_panel", "freight_doors", "stair_sign", "directory_board");
        cat("garage", "garage_car", "garage_pipes", "ceiling_pipes", "forklift", "fuse_box", "pallet_stack", "stencil_sign",
                "basement_sign", "supply_sign", "bench", "radio", "chalk_safe");
        cat("lobby", "chair", "wall_chair", "floating_chair", "wall_clock", "wet_floor_sign", "exit_sign", "fake_exit_sign", "staff_sign",
                "graffiti", "dangling_tile", "drip_emitter", "warped_doorframe", "stair_fragment", "impossible_stairs", "noclip_shimmer");
        cat("plains", "giant_chair", "giant_doorframe", "giant_stairs", "giant_outlet", "giant_figure", "giant_tv", "giant_dining_chair",
                "giant_lamp", "giant_cone", "giant_duck", "giant_crt", "giant_phone", "giant_fan", "giant_shoe", "giant_mug", "giant_teddy",
                "giant_pencil", "giant_hydrant", "giant_bucket", "giant_lightbulb", "utility_pole", "mailbox", "roll_shutter",
                "fallen_panel", "lone_doorframe", "exit_sign_post", "floating_panel", "lone_wall");
        List<String> creatures = new ArrayList<>();
        for (String id : CreatureModels.IDS) creatures.add("creature:" + id);
        CATEGORIES.put("creatures", Collections.unmodifiableList(creatures));
    }

    private AssetCatalog() {
    }

    private static void cat(String name, String... ids) {
        CATEGORIES.put(name, List.of(ids));
    }

    public static Map<String, List<String>> categories() {
        return Collections.unmodifiableMap(CATEGORIES);
    }

    public static List<String> all() {
        List<String> out = new ArrayList<>();
        for (List<String> l : CATEGORIES.values()) out.addAll(l);
        return out;
    }

    /** The category of an asset id (its base before any suffix also counts), or null. */
    public static String categoryOf(String id) {
        String lower = id.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> e : CATEGORIES.entrySet()) {
            for (String a : e.getValue()) if (lower.equals(a) || lower.startsWith(a + ":")) return e.getKey();
        }
        return null;
    }
}
