"""Moog's Paths networks shipped by Jaymo's Kingdoms (data/jaymoskingdoms/moogs_paths/...).

Moog's Paths (CC0) reads path types and networks from its own data registries, so these are new entries in our
namespace, not overrides of its files: no load-order games. Without Moog's Paths the registries don't exist and the
files are never read. Networks with an "origin" lay roads that start at a structure and lead away from it, so
following one brings you back to the village, kingdom, castle or inn it came from. Missing structure sets (Grim
Kingdoms or ATi not installed) are skipped by Moog's Paths.

Run from kingdoms/: python -I tools/gen_paths.py
"""
import json
import os

ROOT = os.path.join("src", "main", "resources", "data", "jaymoskingdoms", "moogs_paths")


def write(kind, name, obj):
    folder = os.path.join(ROOT, kind)
    os.makedirs(folder, exist_ok=True)
    with open(os.path.join(folder, name + ".json"), "w", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def blocks(*pairs):
    return [{"block": b if ":" in b else "minecraft:" + b, "weight": n} for b, n in pairs]


def road(surface, edge, fill, width, length):
    return {
        "surface_blocks": blocks(*surface),
        "edge_blocks": blocks(*edge),
        "fill_block": "minecraft:" + fill,
        "width": {"min": width[0], "max": width[1]},
        "rigidness": 0.35,
        "carver": 0.6,
        "length": {"type": "minecraft:uniform", "min_inclusive": length[0], "max_inclusive": length[1]},
        "fade": {"start_blocks": 0, "end_blocks": 6},
        "water_settings": {
            "surface_blocks": blocks(("spruce_planks", 4), ("oak_planks", 3), ("stripped_spruce_log", 1)),
            "edge_blocks": blocks(("spruce_planks", 2), ("oak_planks", 1)),
        },
    }


# the king's roads: paved, wider and longer than Moog's village trails
write("path_type", "kingdom_road", road(
    [("cobblestone", 6), ("gravel", 4), ("stone_bricks", 3), ("mossy_cobblestone", 2), ("andesite", 2), ("structure_void", 1)],
    [("coarse_dirt", 3), ("gravel", 2), ("structure_void", 5)], "cobblestone", (2, 3), (250, 700)))
write("path_type", "kingdom_road_desert", road(
    [("smooth_sandstone", 6), ("cut_sandstone", 4), ("sandstone", 3), ("chiseled_sandstone", 1), ("structure_void", 1)],
    [("sandstone", 2), ("structure_void", 6)], "sandstone", (2, 3), (300, 800)))
write("path_type", "kingdom_road_savanna", road(
    [("coarse_dirt", 5), ("terracotta", 4), ("packed_mud", 3), ("mud_bricks", 2), ("structure_void", 1)],
    [("coarse_dirt", 2), ("structure_void", 6)], "packed_mud", (2, 3), (250, 700)))
write("path_type", "kingdom_road_snowy", road(
    [("cobblestone", 5), ("gravel", 4), ("spruce_planks", 2), ("packed_ice", 1), ("structure_void", 2)],
    [("snow_block", 2), ("gravel", 1), ("structure_void", 6)], "cobblestone", (2, 3), (200, 600)))

TERMINATORS = {"piece": ["minecraft:village/*/terminators/*", "kaisyn:village/**/terminators/**"]}


def write_tag(name, values):
    folder = os.path.join("src", "main", "resources", "data", "jaymoskingdoms", "tags", "worldgen", "biome")
    os.makedirs(folder, exist_ok=True)
    entries = [v if v.startswith("#") else {"id": v, "required": False} for v in values]
    with open(os.path.join(folder, name + ".json"), "w", newline="\n") as f:
        json.dump({"replace": False, "values": entries}, f, indent=2)
        f.write("\n")


def network(name, path_type, decor, sets, structures, count, anchor=None, features=(), bushes=(), biomes="#minecraft:is_overworld"):
    origin = {"structure_set": sets, "path_count": count}
    if structures:
        origin["structure"] = structures
    if anchor:
        origin["anchor"] = anchor
    write("path_network", name, {
        "path_type": "jaymoskingdoms:" + path_type,
        "biomes": biomes,
        "structure_sets": decor,
        "feature_decorator_sets": list(features),
        "bush_decorator_sets": list(bushes),
        "origin": origin,
    })


# two more roads out of every village (so out of every kingdom seated in one), besides Moog's own two trails
network("village_road_plains", "kingdom_road", ["moogs_paths:simple_andesite_lamp"], ["minecraft:villages"],
        ["minecraft:village_plains"], 2, TERMINATORS, bushes=["moogs_paths:plains_bushes"])
network("village_road_taiga", "kingdom_road", ["moogs_paths:oak_posts"], ["minecraft:villages"],
        ["minecraft:village_taiga"], 2, TERMINATORS)
network("village_road_snowy", "kingdom_road_snowy", ["moogs_paths:snowy_cairns"], ["minecraft:villages"],
        ["minecraft:village_snowy"], 2, TERMINATORS)
network("village_road_desert", "kingdom_road_desert", ["moogs_paths:desert_lamps"], ["minecraft:villages"],
        ["minecraft:village_desert"], 2, TERMINATORS, features=["moogs_paths:desert_dead_bushes"])
network("village_road_savanna", "kingdom_road_savanna", ["moogs_paths:savanna_posts"], ["minecraft:villages"],
        ["minecraft:village_savanna"], 2, TERMINATORS)

# Grim Kingdoms' castles, keeps, citadels, forts and towns (realms can be seated in them too)
GRIM = ["blackridge_watch", "blackridge_watch_taiga", "chillhall_plains_refuge", "chill_hallmanor", "eldermoor",
        "frostbloom_bastion", "frozen_onion_fort", "frozen_onion_fort_taiga", "glacierfall_keep", "glacierfall_keep_plains",
        "glimmervault_fortress", "glimmervault_fortress_plains", "grain_sack_fort", "grain_sack_hold", "grain_sack_keep",
        "sandfell_outpost", "shardspire_castle", "silentgale_citadel", "silentgale_citadel_plains", "snowgrave_citadel",
        "stonyfell_outpost", "stonyfell_outpost_plains", "thatch_outpost", "travelers_lodge", "weathered_colosseum",
        "whitewarden_keep", "winterhold_citadel", "winterhold_citadel_taiga", "zweiberg", "zweiberg_plains", "zweiberg_taiga"]
network("grim_kingdoms_road", "kingdom_road", ["moogs_paths:simple_andesite_lamp"],
        ["grim_kingdoms:plains", "grim_kingdoms:forest", "grim_kingdoms:snowy", "grim_kingdoms:taiga"],
        ["grim_kingdoms:" + s for s in GRIM], 2)

# ATi's buildings. With Kingdoms its large and medium ones generate as compounds (AtiCompounds takes those two sets out
# of placement), so the roads start at the compounds' set; naming ATi's absorbed sets would lay roads from buildings
# that never generate. Paved roads off the sand, sandstone ones on it (a network's biomes must hold its start).
network("ati_compound_road", "kingdom_road", ["moogs_paths:oak_posts"], ["jaymoskingdoms:ati_compounds"], [], 2,
        biomes="#jaymoskingdoms:paved_roads")
network("ati_compound_desert_road", "kingdom_road_desert", ["moogs_paths:desert_lamps"], ["jaymoskingdoms:ati_compounds"], [], 2,
        biomes="#jaymoskingdoms:sand_roads")
# and the homesteads and camps of its small set, which still stand alone
network("ati_road", "kingdom_road", ["moogs_paths:oak_posts"], ["ati_structures:aboveground_small"],
        ["ati_structures:" + s for s in ["old_home", "storage_house", "storage_shack", "nomadic_camp", "ice_tribe"]], 1)

write_tag("paved_roads", ["#minecraft:is_forest", "#minecraft:is_taiga", "#minecraft:is_jungle", "#minecraft:is_savanna",
                          "#minecraft:is_mountain", "#minecraft:is_hill", "minecraft:plains", "minecraft:sunflower_plains",
                          "minecraft:meadow", "minecraft:snowy_plains", "minecraft:ice_spikes", "minecraft:swamp",
                          "minecraft:mangrove_swamp", "minecraft:cherry_grove", "minecraft:pale_garden"])
write_tag("sand_roads", ["minecraft:desert", "#minecraft:is_badlands"])
