"""Draws the art and writes the model and worldgen JSON for Z Fighter patch 8 track A (0.17.62, Andy's NOEA picks): the Space Pod
block, the Conquest Flag item, and the six far places the pod flies to (the Pale Moon, Rustfall, Glacia, Cinder, the Maw). Everything is
our own pixel art (a one-pixel dark outline, three tones lit from the top left) and our own names.
Run it from the root:

    python -I tools/gen_zfighter_space.py

26.3 item models name their faces through texture variables (``layer0``), never raw sprite ids, or they render purple. Dimension
types copy the verdant world's, the Nether's and the Arsenal pocket's shapes; noise settings copy the Overworld's and the Nether's with
only the block, fluid, sea level and material rule changed (vanilla density functions are referenced by name, not copied).
"""
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "jaymospowers")
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "jaymospowers")
NS = "jaymospowers"

OUTLINE = (28, 28, 36, 255)
CLEAR = (0, 0, 0, 0)


def put(img, x, y, color):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), color)


def outline(img, color=OUTLINE):
    marks = []
    for y in range(img.height):
        for x in range(img.width):
            if img.getpixel((x, y))[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < img.width and 0 <= ny < img.height and img.getpixel((nx, ny))[3] != 0:
                        marks.append((x, y))
                        break
    for x, y in marks:
        img.putpixel((x, y), color)


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def write_json(path, obj):
    write(path, json.dumps(obj, indent=2) + "\n")


# ---------------------------------------------------------------- art


def pod_texture():
    """The pod's hull, one 16 x 16 sheet used on every face: pale plates, a red band, a round blue window."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    hull = [(232, 236, 244, 255), (200, 206, 220, 255), (150, 158, 178, 255)]
    for y in range(16):
        for x in range(16):
            shade = hull[0] if (x + y) % 7 == 0 else hull[1]
            if x == 0 or y == 0:
                shade = hull[0]
            if x == 15 or y == 15:
                shade = hull[2]
            img.putpixel((x, y), shade)
    # the plate seams
    for x in range(16):
        img.putpixel((x, 5), hull[2])
        img.putpixel((x, 11), hull[2])
    for y in range(16):
        img.putpixel((7, y), hull[2]) if y < 5 or y > 11 else None
    # the red band
    for y in (6, 7):
        for x in range(1, 15):
            img.putpixel((x, y), (214, 64, 56, 255) if y == 6 else (160, 40, 40, 255))
    # the window: a round blue pane with a glint
    cx, cy = 7.5, 9.5
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= 2.4 and 8 <= y <= 11:
                img.putpixel((x, y), (70, 150, 230, 255))
            elif d <= 3.2 and 7 < y < 12 and 5 <= x <= 10:
                img.putpixel((x, y), (60, 64, 78, 255))
    img.putpixel((6, 8), (210, 240, 255, 255))
    img.putpixel((7, 8), (170, 215, 250, 255))
    return img


def flag_texture():
    """The Conquest Flag: a pole with a red banner and a pale star, lit from the top left."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    pole = [(190, 150, 96, 255), (150, 110, 64, 255), (104, 72, 40, 255)]
    for y in range(1, 15):
        put(img, 3, y, pole[0])
        put(img, 4, y, pole[1])
    put(img, 3, 14, pole[2])
    put(img, 4, 14, pole[2])
    put(img, 3, 0, (255, 226, 120, 255))
    put(img, 4, 0, (222, 178, 60, 255))
    red = [(236, 84, 70, 255), (206, 56, 52, 255), (150, 34, 40, 255)]
    for y in range(2, 9):
        for x in range(5, 14):
            wave = 1 if (x + y) % 5 == 0 else 0
            color = red[0] if y <= 3 else red[1] if y <= 6 else red[2]
            put(img, x, y + wave // 2 * 0, color)
    # a star on the banner
    star = (255, 244, 190, 255)
    for dx, dy in ((9, 4), (8, 5), (9, 5), (10, 5), (9, 6)):
        put(img, dx, dy, star)
    outline(img)
    return img


# ---------------------------------------------------------------- models


def models():
    write_json(os.path.join(ASSETS, "blockstates", "space_pod.json"), {"variants": {"": {"model": f"{NS}:block/space_pod"}}})
    write_json(os.path.join(ASSETS, "items", "space_pod.json"), {"model": {"type": "minecraft:model", "model": f"{NS}:block/space_pod"}})
    tex = f"{NS}:block/space_pod"
    write_json(os.path.join(ASSETS, "models", "block", "space_pod.json"), {
        "textures": {"all": tex, "particle": tex},
        "elements": [
            {"from": [3, 0, 3], "to": [13, 9, 13], "faces": {d: {"texture": "#all"} for d in ("north", "east", "south", "west", "up", "down")}},
            {"from": [4, 9, 4], "to": [12, 13, 12], "faces": {d: {"texture": "#all"} for d in ("north", "east", "south", "west", "up", "down")}},
            {"from": [6, 13, 6], "to": [10, 16, 10], "faces": {d: {"texture": "#all"} for d in ("north", "east", "south", "west", "up", "down")}},
            {"from": [1, 0, 7], "to": [3, 5, 9], "faces": {d: {"texture": "#all"} for d in ("north", "east", "south", "west", "up", "down")}},
            {"from": [13, 0, 7], "to": [15, 5, 9], "faces": {d: {"texture": "#all"} for d in ("north", "east", "south", "west", "up", "down")}},
        ],
    })
    write_json(os.path.join(ASSETS, "items", "planet_flag.json"), {"model": {"type": "minecraft:model", "model": f"{NS}:item/planet_flag"}})
    write_json(os.path.join(ASSETS, "models", "item", "planet_flag.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/planet_flag"}})


# ---------------------------------------------------------------- worldgen


def light_type(sky, fog, cloud, ambient, mood=True):
    """A dimension type shaped like the verdant world's (open sky, a day) with these colours."""
    attributes = {
        "minecraft:audio/background_music": {
            "creative": {"max_delay": 24000, "min_delay": 12000, "sound": "minecraft:music.creative"},
            "default": {"max_delay": 24000, "min_delay": 12000, "sound": "minecraft:music.game"},
        },
        "minecraft:gameplay/bed_rule": {
            "can_set_spawn": "always", "can_sleep": "when_dark", "error_message": {"translate": "block.minecraft.bed.no_sleep"}},
        "minecraft:gameplay/nether_portal_spawns_piglin": False,
        "minecraft:gameplay/respawn_anchor_works": False,
        "minecraft:gameplay/straw_bed_rule": {
            "can_set_spawn": "never", "can_sleep": "when_dark", "destroy_on_leave": True,
            "error_message": {"translate": "block.minecraft.bed.no_sleep"}},
        "minecraft:visual/ambient_light_color": ambient,
        "minecraft:visual/cloud_color": cloud,
        "minecraft:visual/cloud_height": 192.33,
        "minecraft:visual/fog_color": fog,
        "minecraft:visual/sky_color": sky,
    }
    if mood:
        attributes["minecraft:audio/ambient_sounds"] = {
            "mood": {"block_search_extent": 8, "offset": 2.0, "sound": "minecraft:ambient.cave", "tick_delay": 6000}}
    return {
        "ambient_light": 0.0,
        "attributes": attributes,
        "coordinate_scale": 1.0,
        "default_clock": "minecraft:overworld",
        "has_ceiling": False,
        "has_ender_dragon_fight": False,
        "has_skylight": True,
        "height": 384,
        "infiniburn": "#minecraft:infiniburn_overworld",
        "logical_height": 384,
        "min_y": -64,
        "monster_spawn_block_light_limit": 0,
        "monster_spawn_light_level": {"type": "minecraft:uniform", "max_inclusive": 7, "min_inclusive": 0},
        "timelines": "#minecraft:in_overworld",
    }


def forge_type():
    """Cinder's: the Nether's shape (a ceiling, no sky, lava burns fast, water evaporates) at the Overworld's scale."""
    return {
        "ambient_light": 0.1,
        "attributes": {
            "minecraft:gameplay/bed_rule": {"can_set_spawn": "never", "can_sleep": "never", "destroy_on_use": True},
            "minecraft:gameplay/can_start_raid": False,
            "minecraft:gameplay/fast_lava": True,
            "minecraft:gameplay/piglins_zombify": False,
            "minecraft:gameplay/respawn_anchor_works": True,
            "minecraft:gameplay/sky_light_level": 4.0,
            "minecraft:gameplay/snow_golem_melts": True,
            "minecraft:gameplay/straw_bed_rule": {"can_set_spawn": "never", "can_sleep": "never", "destroy_on_use": True},
            "minecraft:gameplay/water_evaporates": True,
            "minecraft:visual/ambient_light_color": "#4a2a18",
            "minecraft:visual/default_dripstone_particle": {"type": "minecraft:dripping_dripstone_lava"},
            "minecraft:visual/fog_end_distance": 128.0,
            "minecraft:visual/fog_start_distance": 12.0,
            "minecraft:visual/sky_light_color": "#ff8a3a",
            "minecraft:visual/sky_light_factor": 0.0,
        },
        "cardinal_light": "nether",
        "coordinate_scale": 1.0,
        "has_ceiling": True,
        "has_ender_dragon_fight": False,
        "has_fixed_time": True,
        "has_skylight": False,
        "height": 256,
        "infiniburn": "#minecraft:infiniburn_nether",
        "logical_height": 128,
        "min_y": 0,
        "monster_spawn_block_light_limit": 15,
        "monster_spawn_light_level": 7,
        "skybox": "none",
        "timelines": "#minecraft:in_nether",
    }


def void_type():
    """The Maw's: the Arsenal pocket's shape (no sky light, a fixed time) with a black sky."""
    return {
        "ambient_light": 0.25,
        "attributes": {
            "minecraft:audio/background_music": {
                "creative": {"max_delay": 24000, "min_delay": 12000, "sound": "minecraft:music.creative"},
                "default": {"max_delay": 24000, "min_delay": 12000, "sound": "minecraft:music.game"},
            },
            "minecraft:gameplay/bed_rule": {
                "can_set_spawn": "never", "can_sleep": "when_dark", "error_message": {"translate": "block.minecraft.bed.no_sleep"}},
            "minecraft:gameplay/nether_portal_spawns_piglin": False,
            "minecraft:gameplay/respawn_anchor_works": False,
            "minecraft:gameplay/straw_bed_rule": {
                "can_set_spawn": "never", "can_sleep": "when_dark", "destroy_on_leave": True,
                "error_message": {"translate": "block.minecraft.bed.no_sleep"}},
            "minecraft:visual/ambient_light_color": "#6a5cff",
            "minecraft:visual/cloud_color": "#00000000",
            "minecraft:visual/cloud_height": 192.33,
            "minecraft:visual/fog_color": "#05030c",
            "minecraft:visual/sky_color": "#000000",
        },
        "coordinate_scale": 1.0,
        "default_clock": "minecraft:overworld",
        "has_ceiling": False,
        "has_ender_dragon_fight": False,
        "has_skylight": False,
        "height": 256,
        "infiniburn": "#minecraft:infiniburn_overworld",
        "logical_height": 256,
        "min_y": 0,
        "monster_spawn_block_light_limit": 0,
        "monster_spawn_light_level": 0,
        "has_fixed_time": True,
    }


def spawns(monster=None, ambient=None):
    return {
        "ambient": ambient or [],
        "axolotls": [],
        "creature": [],
        "misc": [],
        "monster": monster or [],
        "underground_water_creature": [],
        "water_ambient": [],
        "water_creature": [],
    }


def biome(sky, fog, water, features, monsters=None, carvers=None, temperature=0.5, extra=None):
    attributes = {
        "minecraft:gameplay/natural_mob_spawns": {
            "argument": {"spawn_costs": {}, "spawns_by_category": spawns(monsters)}, "modifier": "overlay"},
        "minecraft:visual/sky_color": sky,
        "minecraft:visual/fog_color": fog,
    }
    if extra:
        attributes.update(extra)
    return {
        "attributes": attributes,
        "carvers": carvers or [],
        "downfall": 0.0,
        "effects": {"water_color": water},
        "features": features,
        "has_precipitation": False,
        "temperature": temperature,
    }


def padded(steps):
    """11 feature steps, with {index: [placed features]} filled in."""
    out = [[] for _ in range(11)]
    for i, names in steps.items():
        out[i] = names
    return out


def noise_settings(base, **changes):
    """The vanilla file's shape, with the router and the rest read from the vanilla jar's copy at build time? No: written out here."""
    settings = dict(base)
    settings.update(changes)
    return settings


OVERWORLD_ROUTER = {
    "chunk_surface_level": "minecraft:overworld/chunk_surface_level",
    "continents": "minecraft:overworld/continents",
    "depth": "minecraft:overworld/depth",
    "erosion": "minecraft:overworld/erosion",
    "final_density": "minecraft:overworld/final_density",
    "ridges": "minecraft:overworld/ridges",
    "temperature": "minecraft:overworld/temperature",
    "vegetation": "minecraft:overworld/vegetation",
}

OVERWORLD_SPAWN = [
    {"minecraft:overworld/continents": [-0.11, 1.0], "minecraft:overworld/erosion": [-1.0, 1.0],
     "minecraft:overworld/ridges": [-1.0, -0.16], "minecraft:overworld/temperature": [-1.0, 1.0],
     "minecraft:overworld/vegetation": [-1.0, 1.0]},
    {"minecraft:overworld/continents": [-0.11, 1.0], "minecraft:overworld/erosion": [-1.0, 1.0],
     "minecraft:overworld/ridges": [0.16, 1.0], "minecraft:overworld/temperature": [-1.0, 1.0],
     "minecraft:overworld/vegetation": [-1.0, 1.0]},
]

# the vanilla Overworld's aquifer block with the water and lava noise silenced (the moon holds neither)
DRY_AQUIFERS = {
    "barrier": {"type": "minecraft:noise", "noise": "minecraft:aquifer_barrier", "xz_scale": 1.0, "y_scale": 0.5},
    "exclusion": {"type": "minecraft:min", "left": {"type": "minecraft:sub", "left": -0.225, "right": "minecraft:overworld/erosion"},
                  "right": {"type": "minecraft:max", "left": {"type": "minecraft:sub", "left": "minecraft:overworld/depth", "right": 0.9},
                            "right": 0.0}},
    "fluid_level_floodedness": -1.0,
    "fluid_level_spread": 0.0,
    "lava": -1.0,
    "surface_level": "minecraft:overworld/preliminary_surface_level",
}
WET_AQUIFERS = {
    "barrier": {"type": "minecraft:noise", "noise": "minecraft:aquifer_barrier", "xz_scale": 1.0, "y_scale": 0.5},
    "exclusion": DRY_AQUIFERS["exclusion"],
    "fluid_level_floodedness": {"type": "minecraft:noise", "noise": "minecraft:aquifer_fluid_level_floodedness", "xz_scale": 1.0, "y_scale": 0.67},
    "fluid_level_spread": {"type": "minecraft:noise", "noise": "minecraft:aquifer_fluid_level_spread", "xz_scale": 1.0, "y_scale": 0.7142857142857143},
    "lava": {"type": "minecraft:noise", "noise": "minecraft:aquifer_lava", "xz_scale": 1.0, "y_scale": 1.0},
    "surface_level": "minecraft:overworld/preliminary_surface_level",
}


def ore(name, state, size, count, low, high, discard=0.0, rare=0):
    """A forge-world ore: veins of {state} in netherrack, {count} tries a chunk between y {low} and {high}."""
    feature = {
        "type": "minecraft:ore", "discard_chance_on_air_exposure": discard, "size": size,
        "targets": [{"state": state, "target": {"block": "minecraft:netherrack", "predicate_type": "minecraft:block_match"}}],
    }
    write_json(os.path.join(DATA, "worldgen", "feature", name + ".json"), feature)
    placement = [{"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
                 {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": low},
                                                                "max_inclusive": {"absolute": high}}},
                 {"type": "minecraft:biome"}]
    if rare:
        placement.insert(0, {"type": "minecraft:rarity_filter", "chance": rare})
    write_json(os.path.join(DATA, "worldgen", "placed_feature", name + ".json"), {"feature": f"{NS}:{name}", "placement": placement})


def worldgen():
    d = lambda *p: os.path.join(DATA, *p)
    # ---- dimension types
    write_json(d("dimension_type", "moon.json"), light_type("#000000", "#0c0c12", "#00000000", "#26262e"))
    write_json(d("dimension_type", "rustfall.json"), light_type("#d9805f", "#b9684a", "#80d8a090", "#2a1a14"))
    write_json(d("dimension_type", "glacia.json"), light_type("#a9d6ff", "#dff0ff", "#e0ffffff", "#1c2230"))
    write_json(d("dimension_type", "cinder.json"), forge_type())
    write_json(d("dimension_type", "maw.json"), void_type())
    # ---- the moon's noise settings (the Overworld's shape, dry, grey) and surface
    moon = {
        "aquifers": DRY_AQUIFERS, "default_block": "minecraft:stone", "default_fluid": "minecraft:air",
        "disable_mob_generation": False, "legacy_random_source": False, "material_rule": f"{NS}:moon",
        "noise": {"height": 384, "min_y": -64}, "noise_router": OVERWORLD_ROUTER, "sea_level": -64, "spawn_target": OVERWORLD_SPAWN,
    }
    write_json(d("worldgen", "noise_settings", "moon.json"), moon)
    # glacia: the Overworld's, with the sea well up (most of it is frozen ocean)
    glacia = {
        "aquifers": WET_AQUIFERS, "default_block": "minecraft:stone", "default_fluid": "minecraft:water",
        "disable_mob_generation": False, "legacy_random_source": False, "material_rule": "minecraft:overworld",
        "noise": {"height": 384, "min_y": -64}, "noise_router": OVERWORLD_ROUTER, "sea_level": 84, "spawn_target": OVERWORLD_SPAWN,
    }
    write_json(d("worldgen", "noise_settings", "glacia.json"), glacia)
    # the moon's surface: bedrock at the floor, pale calcite dust over andesite over tuff over stone
    write_json(d("worldgen", "material_rule", "moon.json"), {
        "type": "minecraft:sequence",
        "sequence": [
            "minecraft:bedrock_floor",
            {"type": "minecraft:condition", "if_true": "minecraft:on_floor",
             "then_run": {"type": "minecraft:block", "result_state": {"Name": "minecraft:calcite"}}},
            {"type": "minecraft:condition", "if_true": "minecraft:under_floor",
             "then_run": {"type": "minecraft:block", "result_state": {"Name": "minecraft:andesite"}}},
            {"type": "minecraft:condition", "if_true": "minecraft:deep_under_floor",
             "then_run": {"type": "minecraft:block", "result_state": {"Name": "minecraft:tuff"}}},
        ],
    })
    # ---- biomes
    rock = ["minecraft:ore_coal_upper", "minecraft:ore_iron_upper", "minecraft:ore_iron_middle", "minecraft:ore_gold",
            "minecraft:ore_redstone", "minecraft:ore_diamond", "minecraft:ore_lapis", "minecraft:ore_copper", "minecraft:ore_emerald",
            "minecraft:ore_tuff", "minecraft:ore_andesite_upper", "minecraft:ore_diorite_upper"]
    write_json(d("worldgen", "biome", "moon_plains.json"), biome(
        "#000000", "#0c0c12", "#8a8a9a", padded({6: rock}),
        monsters=[{"type": "minecraft:enderman", "count": 2, "weight": 20}, {"type": "minecraft:skeleton", "count": 3, "weight": 60}],
        carvers=["minecraft:cave", "minecraft:canyon"], temperature=0.1))
    cinder_ores = ["minecraft:ore_magma", "minecraft:ore_gravel_nether", "minecraft:ore_blackstone", "minecraft:ore_gold_nether",
                   "minecraft:ore_quartz_nether", "minecraft:ore_ancient_debris_large", "minecraft:ore_debris_small",
                   f"{NS}:cinder_iron", f"{NS}:cinder_diamond", f"{NS}:cinder_emerald", f"{NS}:cinder_redstone"]
    write_json(d("worldgen", "biome", "cinder_forge.json"), biome(
        "#ff7a30", "#c0481a", "#3f76e4", padded({7: ["minecraft:spring_open", "minecraft:patch_fire", "minecraft:glowstone_extra",
                                                        "minecraft:glowstone"] + cinder_ores, 9: ["minecraft:spring_lava"]}),
        monsters=[{"type": "minecraft:magma_cube", "count": 4, "weight": 60}, {"type": "minecraft:blaze", "count": 3, "weight": 40},
                  {"type": "minecraft:zombified_piglin", "count": 4, "weight": 30}],
        carvers=["minecraft:nether_cave"], temperature=2.0))
    write_json(d("worldgen", "biome", "maw_void.json"), biome("#000000", "#05030c", "#1a0b2e", padded({})))
    ore("cinder_iron", "minecraft:iron_ore", 10, 16, 8, 118)
    ore("cinder_diamond", "minecraft:diamond_ore", 5, 5, 8, 100, discard=0.4)
    ore("cinder_emerald", "minecraft:emerald_ore", 2, 6, 8, 110, discard=0.2)
    ore("cinder_redstone", "minecraft:redstone_ore", 8, 10, 8, 100)
    # ---- dimensions
    write_json(d("dimension", "moon.json"), {"type": f"{NS}:moon", "generator": {
        "type": "minecraft:noise", "settings": f"{NS}:moon", "biome_source": {"type": "minecraft:fixed", "biome": f"{NS}:moon_plains"}}})
    write_json(d("dimension", "rustfall.json"), {"type": f"{NS}:rustfall", "generator": {
        "type": "minecraft:noise", "settings": "minecraft:overworld", "biome_source": {
            "type": "minecraft:checkerboard", "scale": 6,
            "biomes": ["minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands"]}}})
    write_json(d("dimension", "glacia.json"), {"type": f"{NS}:glacia", "generator": {
        "type": "minecraft:noise", "settings": f"{NS}:glacia", "biome_source": {
            "type": "minecraft:checkerboard", "scale": 6, "biomes": ["minecraft:snowy_plains", "minecraft:ice_spikes", "minecraft:snowy_taiga"]}}})
    write_json(d("dimension", "cinder.json"), {"type": f"{NS}:cinder", "generator": {
        "type": "minecraft:noise", "settings": "minecraft:nether", "biome_source": {"type": "minecraft:fixed", "biome": f"{NS}:cinder_forge"}}})
    write_json(d("dimension", "maw.json"), {"type": f"{NS}:maw", "generator": {
        "type": "minecraft:flat", "settings": {
            "biome": f"{NS}:maw_void", "features": False, "lakes": False,
            "layers": [{"block": "minecraft:air", "height": 1}], "structure_overrides": []}}})
    # ---- the Maw's wrecks
    def item(name, weight, low=1, high=1):
        entry = {"type": "minecraft:item", "name": name, "weight": weight}
        if high > 1:
            entry["modifier"] = {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": low, "max": high}}
        return entry
    write_json(d("loot_table", "chests", "maw_wreck.json"), {
        "type": "minecraft:chest",
        "pools": [
            {"rolls": {"type": "minecraft:uniform", "min": 3, "max": 5}, "entries": [
                item("minecraft:iron_ingot", 30, 4, 12), item("minecraft:gold_ingot", 24, 3, 8), item("minecraft:diamond", 12, 1, 3),
                item("minecraft:ender_pearl", 14, 1, 3), item("minecraft:redstone_block", 10, 1, 2), item("minecraft:lapis_block", 8, 1, 2),
                item("minecraft:amethyst_shard", 16, 4, 12), item("minecraft:obsidian", 12, 2, 6)]},
            {"rolls": {"type": "minecraft:uniform", "min": 0, "max": 1}, "entries": [
                item("minecraft:netherite_scrap", 12, 1, 2), item("minecraft:echo_shard", 8), item("minecraft:enchanted_golden_apple", 5),
                item("minecraft:heart_of_the_sea", 3), item("minecraft:elytra", 1), item("minecraft:netherite_ingot", 2),
                item("jaymospowers:hermit_bean", 6, 1, 2)]},
        ],
    })


def main():
    for rel, img in (("textures/block/space_pod.png", pod_texture()), ("textures/item/planet_flag.png", flag_texture())):
        path = os.path.join(ASSETS, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        img.save(path)
    models()
    worldgen()
    print("ok")


if __name__ == "__main__":
    main()
