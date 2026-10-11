#!/usr/bin/env python3
"""Generates PowderPass data resources (biomes, tags, dimension type, world preset, lang).

Biome JSON follows the 1.21.11 format observed in Fabric API's 1.21.11 generated test data
(environment "attributes" + slimmed "effects"). Vegetation is intentionally absent from biome feature
lists: PowderPass places all vegetation itself so decoration is independent of chunk order.

Usage: python3 tools/gen_resources.py   (writes into fabric/src/main/resources)
"""
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "fabric", "src", "main", "resources")

ORES = [
    "minecraft:ore_dirt", "minecraft:ore_gravel", "minecraft:ore_granite_upper", "minecraft:ore_granite_lower",
    "minecraft:ore_diorite_upper", "minecraft:ore_diorite_lower", "minecraft:ore_andesite_upper",
    "minecraft:ore_andesite_lower", "minecraft:ore_tuff", "minecraft:ore_coal_upper", "minecraft:ore_coal_lower",
    "minecraft:ore_iron_upper", "minecraft:ore_iron_middle", "minecraft:ore_iron_small", "minecraft:ore_gold",
    "minecraft:ore_gold_lower", "minecraft:ore_redstone", "minecraft:ore_redstone_lower", "minecraft:ore_diamond",
    "minecraft:ore_diamond_medium", "minecraft:ore_diamond_large", "minecraft:ore_diamond_buried",
    "minecraft:ore_lapis", "minecraft:ore_lapis_buried", "minecraft:ore_copper", "minecraft:underwater_magma",
    "minecraft:disk_sand", "minecraft:disk_clay", "minecraft:disk_gravel"]
MOUNTAIN_ORES = ["minecraft:ore_emerald", "minecraft:ore_infested"]


def features(extra_ores=(), fossils=False):
    return [
        [],
        ["minecraft:lake_lava_underground"],
        ["minecraft:amethyst_geode"],
        ["minecraft:monster_room", "minecraft:monster_room_deep"] + (["minecraft:fossil_upper", "minecraft:fossil_lower"] if fossils else []),
        [],
        [],
        ORES + list(extra_ores),
        [],
        ["minecraft:spring_water", "minecraft:spring_lava"],
        ["minecraft:glow_lichen"],
        [],
    ]


def sp(t, w, lo, hi):
    return {"type": "minecraft:" + t, "maxCount": hi, "minCount": lo, "weight": w}


STD_MON = [sp("spider", 100, 4, 4), sp("zombie", 95, 4, 4), sp("zombie_villager", 5, 1, 1), sp("skeleton", 100, 4, 4),
           sp("creeper", 100, 4, 4), sp("slime", 100, 4, 4), sp("enderman", 10, 1, 4), sp("witch", 5, 1, 1)]
SNOW_MON = [sp("spider", 100, 4, 4), sp("zombie", 95, 4, 4), sp("zombie_villager", 5, 1, 1), sp("skeleton", 20, 4, 4),
            sp("stray", 80, 4, 4), sp("creeper", 100, 4, 4), sp("slime", 100, 4, 4), sp("enderman", 10, 1, 4), sp("witch", 5, 1, 1)]
DRY_MON = [sp("spider", 100, 4, 4), sp("zombie", 19, 4, 4), sp("zombie_villager", 1, 1, 1), sp("husk", 80, 4, 4),
           sp("skeleton", 100, 4, 4), sp("creeper", 100, 4, 4), sp("slime", 100, 4, 4), sp("enderman", 10, 1, 4), sp("witch", 5, 1, 1)]
FARM = [sp("sheep", 12, 4, 4), sp("pig", 10, 4, 4), sp("chicken", 10, 4, 4), sp("cow", 8, 4, 4)]

C = {
    "grass": FARM + [sp("horse", 5, 2, 6), sp("donkey", 1, 1, 3)],
    "forest": FARM + [sp("wolf", 5, 4, 4), sp("rabbit", 4, 2, 3)],
    "birch": FARM,
    "taiga": FARM + [sp("wolf", 8, 4, 4), sp("rabbit", 4, 2, 3), sp("fox", 8, 2, 4)],
    "alpine": [sp("donkey", 1, 1, 2), sp("rabbit", 2, 2, 6), sp("sheep", 2, 2, 4)],
    "peaks": [sp("goat", 5, 1, 3)],
    "tundra": [sp("rabbit", 10, 2, 3), sp("polar_bear", 1, 1, 2)],
    "savanna": FARM + [sp("horse", 1, 2, 6), sp("donkey", 1, 1, 1), sp("llama", 8, 4, 4), sp("armadillo", 10, 2, 3)],
    "scrub": [sp("rabbit", 4, 2, 3), sp("armadillo", 6, 1, 2), sp("sheep", 4, 2, 4)],
    "steppe": [sp("rabbit", 6, 2, 3), sp("horse", 4, 2, 6), sp("donkey", 1, 1, 3), sp("sheep", 6, 2, 4)],
    "jungle": FARM + [sp("parrot", 40, 1, 2), sp("panda", 1, 1, 2), sp("ocelot", 2, 1, 3)],
    "desert": [sp("rabbit", 4, 2, 3), sp("camel", 1, 1, 1)],
    "badlands": [sp("armadillo", 6, 1, 2)],
    "marsh": [sp("frog", 10, 2, 5)] + FARM[:2],
    "mangrove": [sp("frog", 10, 2, 5)],
    "beach": [sp("turtle", 5, 2, 5)],
    "none": [],
    "rabbit": [sp("rabbit", 4, 2, 3)],
}
FRESH_AMBIENT = [sp("salmon", 5, 1, 5)]
FRESH_CREATURE = [sp("squid", 2, 1, 4)]

# key: (temperature, downfall, precipitation, creature set, monsters, water color, sky, grass, foliage, mountain ores, fossils, freshwater)
B = {
    "alluvial_meadow": (0.8, 0.6, True, "grass", STD_MON, "#3f76e4", "#78a7ff", None, None, False, False, True),
    "floodplain_woodland": (0.75, 0.85, True, "forest", STD_MON, "#3f76e4", "#78a7ff", None, None, False, False, True),
    "rolling_grassland": (0.8, 0.4, True, "grass", STD_MON, "#3f76e4", "#78a7ff", None, None, False, False, False),
    "temperate_mixed_forest": (0.7, 0.8, True, "forest", STD_MON, "#3f76e4", "#79a6ff", None, None, False, False, True),
    "birch_uplands": (0.6, 0.6, True, "birch", STD_MON, "#3f76e4", "#7aa5ff", None, None, False, False, False),
    "conifer_foothills": (0.25, 0.8, True, "taiga", STD_MON, "#3d57d6", "#7ba4ff", None, None, True, False, True),
    "boreal_forest": (0.2, 0.8, True, "taiga", STD_MON, "#3d57d6", "#7ba4ff", None, None, False, False, True),
    "alpine_meadow": (0.5, 0.8, True, "alpine", STD_MON, "#0e4ecf", "#7ba4ff", "#83bb6d", None, True, False, False),
    "rocky_alpine_ridge": (-0.2, 0.3, True, "peaks", SNOW_MON, "#3f76e4", "#7fa1ff", None, None, True, False, False),
    "snowy_summit": (-0.7, 0.9, True, "peaks", SNOW_MON, "#3f76e4", "#85a0ff", None, None, True, False, False),
    "frozen_tundra": (-0.5, 0.5, True, "tundra", SNOW_MON, "#3938c9", "#7fa1ff", None, None, False, False, False),
    "wet_coastal_forest": (0.4, 1.0, True, "taiga", STD_MON, "#3d57d6", "#7ba4ff", "#4f8f3a", None, False, False, True),
    "dry_scrubland": (1.4, 0.15, False, "scrub", DRY_MON, "#3f76e4", "#6eb1ff", "#b0ab5b", "#a3a050", False, True, False),
    "steppe": (0.9, 0.2, True, "steppe", STD_MON, "#3f76e4", "#77a8ff", "#a8b45b", None, False, False, False),
    "warm_savanna": (1.6, 0.1, False, "savanna", STD_MON, "#3f76e4", "#6eb1ff", None, None, False, False, False),
    "tropical_rainforest": (0.95, 0.9, True, "jungle", STD_MON, "#3f76e4", "#77a8ff", None, None, False, False, True),
    "sandy_desert": (2.0, 0.0, False, "desert", DRY_MON, "#3f76e4", "#6eb1ff", None, None, False, True, False),
    "red_badlands": (2.0, 0.0, False, "badlands", STD_MON, "#3f76e4", "#6eb1ff", "#90814d", "#9e814d", False, False, False),
    "karst_country": (0.65, 0.7, True, "forest", STD_MON, "#44aff5", "#79a6ff", None, None, False, False, True),
    "marsh": (0.8, 0.9, True, "marsh", STD_MON, "#617b64", "#78a7ff", "#6a7039", "#6a7039", False, True, True),
    "mangrove_wetland": (0.8, 0.9, True, "mangrove", STD_MON, "#3a7a6a", "#78a7ff", None, "#8db127", False, False, True),
    "windswept_plateau": (0.6, 0.3, True, "steppe", STD_MON, "#3f76e4", "#7aa5ff", "#8db360", None, True, False, False),
    "basalt_highlands": (0.5, 0.5, True, "birch", STD_MON, "#3f76e4", "#7aa5ff", "#7d9a55", None, True, False, False),
    "ash_barrens": (1.0, 0.1, False, "none", STD_MON, "#4b6f7a", "#8f8f9f", "#8a8a62", "#8a8a62", False, False, False),
    "crater_basin": (0.7, 0.3, True, "rabbit", STD_MON, "#3f76e4", "#79a6ff", "#9aa35a", None, False, False, False),
    "salt_flats": (1.2, 0.05, False, "none", DRY_MON, "#5fb7d0", "#7db6ff", None, None, False, False, False),
    "barren_flats": (1.3, 0.05, False, "none", DRY_MON, "#3f76e4", "#77b0ff", "#a29a6a", None, False, False, False),
    "beach_dunes": (0.8, 0.4, True, "beach", STD_MON, "#3f76e4", "#78a7ff", None, None, False, False, False),
    "stony_shore": (0.2, 0.3, True, "none", STD_MON, "#3f76e4", "#7da3ff", None, None, False, False, False),
    "glacial_valley": (0.1, 0.6, True, "taiga", STD_MON, "#3d57d6", "#7ca3ff", None, None, True, False, True),
}

NAMES = {k: " ".join(w.capitalize() for w in k.split("_")) for k in B}


def write(rel, obj):
    p = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def biome(key):
    t, dl, precip, cset, mons, water, sky, grass, foliage, mtn, fossils, fresh = B[key]
    effects = {"water_color": water}
    if grass:
        effects["grass_color"] = grass
    if foliage:
        effects["foliage_color"] = foliage
    return {
        "attributes": {"minecraft:visual/sky_color": sky},
        "carvers": [],
        "downfall": dl,
        "effects": effects,
        "features": features(MOUNTAIN_ORES if mtn else (), fossils),
        "has_precipitation": precip,
        "spawn_costs": {},
        "spawners": {
            "ambient": [sp("bat", 10, 8, 8)],
            "axolotls": [],
            "creature": C[cset],
            "misc": [],
            "monster": mons,
            "underground_water_creature": [sp("glow_squid", 10, 4, 6)],
            "water_ambient": FRESH_AMBIENT if fresh else [],
            "water_creature": FRESH_CREATURE if fresh else [],
        },
        "temperature": t,
    }


def pp(*keys):
    return ["powderpass:" + k for k in keys]


def main():
    for k in B:
        write(f"data/powderpass/worldgen/biome/{k}.json", biome(k))
    land = pp(*B.keys())
    low_land = pp("alluvial_meadow", "floodplain_woodland", "rolling_grassland", "temperate_mixed_forest", "birch_uplands",
                  "conifer_foothills", "boreal_forest", "wet_coastal_forest", "dry_scrubland", "steppe", "warm_savanna",
                  "tropical_rainforest", "sandy_desert", "red_badlands", "karst_country", "marsh", "mangrove_wetland",
                  "windswept_plateau", "basalt_highlands", "beach_dunes", "glacial_valley", "frozen_tundra")
    tags = {
        "is_overworld": land,
        "stronghold_biased_to": low_land,
        "has_structure/village_plains": pp("rolling_grassland", "alluvial_meadow"),
        "has_structure/village_taiga": pp("boreal_forest"),
        "has_structure/village_savanna": pp("warm_savanna"),
        "has_structure/village_desert": pp("sandy_desert"),
        "has_structure/village_snowy": pp("frozen_tundra"),
        "has_structure/pillager_outpost": pp("rolling_grassland", "warm_savanna", "sandy_desert", "boreal_forest", "steppe"),
        "has_structure/mineshaft": [b for b in land if b != "powderpass:red_badlands"],
        "has_structure/mineshaft_mesa": pp("red_badlands"),
        "has_structure/desert_pyramid": pp("sandy_desert"),
        "has_structure/jungle_temple": pp("tropical_rainforest"),
        "has_structure/swamp_hut": pp("marsh"),
        "has_structure/igloo": pp("frozen_tundra"),
        "has_structure/woodland_mansion": pp("temperate_mixed_forest"),
        "has_structure/trail_ruins": pp("boreal_forest", "birch_uplands", "conifer_foothills"),
        "has_structure/trial_chambers": land,
        "has_structure/ruined_portal_standard": pp("rolling_grassland", "temperate_mixed_forest", "birch_uplands", "boreal_forest",
                                                   "alluvial_meadow", "steppe", "karst_country", "beach_dunes", "stony_shore"),
        "has_structure/ruined_portal_desert": pp("sandy_desert"),
        "has_structure/ruined_portal_jungle": pp("tropical_rainforest"),
        "has_structure/ruined_portal_swamp": pp("marsh", "mangrove_wetland"),
        "has_structure/buried_treasure": pp("beach_dunes"),
        "has_structure/shipwreck_beached": pp("beach_dunes"),
        "is_beach": pp("beach_dunes"),
        "is_badlands": pp("red_badlands"),
        "is_jungle": pp("tropical_rainforest"),
        "is_savanna": pp("warm_savanna"),
        "is_taiga": pp("boreal_forest", "conifer_foothills", "wet_coastal_forest"),
        "is_forest": pp("temperate_mixed_forest", "birch_uplands", "floodplain_woodland"),
        "is_mountain": pp("rocky_alpine_ridge", "snowy_summit", "alpine_meadow"),
        "is_hill": pp("windswept_plateau", "basalt_highlands"),
        "allows_surface_slime_spawns": pp("marsh", "mangrove_wetland"),
        "spawns_snow_foxes": pp("frozen_tundra", "snowy_summit"),
        "spawns_white_rabbits": pp("frozen_tundra", "snowy_summit"),
        "increased_fire_burnout": pp("tropical_rainforest", "mangrove_wetland", "marsh"),
    }
    for name, values in tags.items():
        write(f"data/minecraft/tags/worldgen/biome/{name}.json", {"replace": False, "values": values})

    write("data/powderpass/dimension_type/tall_overworld.json", {
        "ultrawarm": False,
        "natural": True,
        "coordinate_scale": 1.0,
        "ambient_light": 0.0,
        "has_skylight": True,
        "has_ceiling": False,
        "infiniburn": "#minecraft:infiniburn_overworld",
        "logical_height": 832,
        "has_raids": True,
        "respawn_anchor_works": False,
        "bed_works": True,
        "piglin_safe": False,
        "height": 832,
        "min_y": -64,
        "effects": "minecraft:overworld",
        "monster_spawn_block_light_limit": 0,
        "monster_spawn_light_level": {"type": "minecraft:uniform", "min_inclusive": 0, "max_inclusive": 7},
    })
    write("data/powderpass/worldgen/world_preset/powderpass.json", {
        "dimensions": {
            "minecraft:overworld": {
                "type": "powderpass:tall_overworld",
                "generator": {"type": "powderpass:powderpass", "biome_source": {"type": "powderpass:powderpass"}},
            },
            "minecraft:the_nether": {
                "type": "minecraft:the_nether",
                "generator": {"type": "minecraft:noise",
                              "biome_source": {"type": "minecraft:multi_noise", "preset": "minecraft:nether"},
                              "settings": "minecraft:nether"},
            },
            "minecraft:the_end": {
                "type": "minecraft:the_end",
                "generator": {"type": "minecraft:noise", "biome_source": {"type": "minecraft:the_end"},
                              "settings": "minecraft:end"},
            },
        }
    })
    write("data/minecraft/tags/worldgen/world_preset/normal.json", {"replace": False, "values": ["powderpass:powderpass"]})
    lang = {"generator.powderpass.powderpass": "PowderPass"}
    for k, n in NAMES.items():
        lang[f"biome.powderpass.{k}"] = n
    write("assets/powderpass/lang/en_us.json", lang)
    print(f"wrote {len(B)} biomes, {len(tags)} biome tags, dimension type, world preset, lang")


if __name__ == "__main__":
    main()
