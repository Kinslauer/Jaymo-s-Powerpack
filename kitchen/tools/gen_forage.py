"""The forager's kitchen and bees: data, models and art that are our own (the Spore & Stem art comes in through
port_spore_and_stem.py). Run with `python3 -I kitchen/tools/gen_forage.py`; safe to run again.

Writes: loot tables, recipes, tags, worldgen, the Harvest Hive's blockstate, models and textures, the Bee Jar's and the
Psithurism dishes' icons, item definitions and models, the dishes' minigame recipes, and the lang part.
"""
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources"
DATA = ROOT / "data/jaymoskitchen"
ASSETS = ROOT / "assets/jaymoskitchen"
NS = "jaymoskitchen"

SMALL = ["morel", "chanterelle", "deathcap", "frostcap", "emberstool", "puffball"]
DISHES = ["roasted_morel", "chanterelle_toast", "glowcap_soup", "bouncecap_jelly", "frostcap_tea", "ember_skewer",
          "truffle_stew", "foragers_stew"]
PSI_DISHES = {"soy_rice_bowl": "Soy Rice Bowl", "seared_fugu": "Seared Fugu", "fugu_rice_stew": "Fugu Rice Stew"}
# dishes that need another mod's item: the recipe loads only with it
NEEDS = {
    "glowcap_soup": ["jaymoskingdoms:glowcap"],
    "bouncecap_jelly": ["jaymoskingdoms:bouncecap"],
    "soy_rice_bowl": ["psithurism:soybeans"],
    "seared_fugu": ["psithurism:fugu", "psithurism:soybeans"],
    "fugu_rice_stew": ["psithurism:fugu"],
}


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8")


def add_tag(path, values, plain=True):
    """Adds values to a tag file (made if missing)."""
    obj = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {"replace": False, "values": []}
    have = [v if isinstance(v, str) else v["id"] for v in obj["values"]]
    for v in values:
        key = v if isinstance(v, str) else v["id"]
        if key not in have:
            obj["values"].append(v)
    write(path, obj)


def optional(*ids):
    return [{"id": i, "required": False} for i in ids]


# ---------------------------------------------------------------- loot tables
def block_drop(name, item=None):
    return {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": f"{NS}:{item or name}"}],
                   "condition": {"type": "minecraft:survives_explosion"}}],
        "random_sequence": f"{NS}:blocks/{name}",
    }


def loot():
    out = DATA / "loot_table/blocks"
    for name in SMALL:
        # a foraging knife takes two
        write(out / f"{name}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "bonus_rolls": 0.0, "entries": [{
                "type": "minecraft:item", "name": f"{NS}:{name}",
                "modifier": [{"type": "minecraft:set_count", "count": 2, "add": False,
                              "condition": {"type": "minecraft:match_tool", "predicate": {"items": f"#{NS}:foraging_tools"}}}]}],
                "condition": {"type": "minecraft:survives_explosion"}}],
            "random_sequence": f"{NS}:blocks/{name}"})
        write(out / f"potted_{name}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": "minecraft:flower_pot"}],
                       "condition": {"type": "minecraft:survives_explosion"}},
                      {"rolls": 1, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                       "condition": {"type": "minecraft:survives_explosion"}}],
            "random_sequence": f"{NS}:blocks/potted_{name}"})
    write(out / "mycelial_soil.json", block_drop("mycelial_soil"))
    write(out / "harvest_hive.json", block_drop("harvest_hive"))
    silk = {"type": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
        {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}
    write(out / "truffle_soil.json", {
        "type": "minecraft:block",
        "pools": [
            {"rolls": 1, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:alternatives", "children": [
                {"type": "minecraft:item", "name": f"{NS}:truffle_soil", "condition": silk},
                {"type": "minecraft:item", "name": f"{NS}:truffle", "modifier": [
                    {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 2}, "add": False},
                    {"type": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:uniform_bonus_count",
                     "parameters": {"bonusMultiplier": 1}},
                    {"type": "minecraft:explosion_decay"}]}]}]},
            {"rolls": 1, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": "minecraft:dirt"}],
             "condition": {"type": "minecraft:all_of", "terms": [{"type": "minecraft:inverted", "term": silk},
                                                                  {"type": "minecraft:survives_explosion"}]}}],
        "random_sequence": f"{NS}:blocks/truffle_soil"})
    # the log drops itself, and what is ripe on it
    pools = [{"rolls": 1, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": f"{NS}:cultivation_log"}],
              "condition": {"type": "minecraft:survives_explosion"}}]
    for fungus in ["morel", "chanterelle", "frostcap", "emberstool"]:
        pools.append({"rolls": 1, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": f"{NS}:{fungus}", "modifier": [
            {"type": "minecraft:set_count", "count": 2, "add": False}]}],
            "condition": {"type": "minecraft:all_of", "terms": [
                {"type": "minecraft:match_block", "blocks": f"{NS}:cultivation_log", "state": {"fungus": fungus, "stage": "3"}},
                {"type": "minecraft:survives_explosion"}]}})
    write(out / "cultivation_log.json", {"type": "minecraft:block", "pools": pools, "random_sequence": f"{NS}:blocks/cultivation_log"})


# ---------------------------------------------------------------- recipes
def recipes():
    out = DATA / "recipe"
    write(out / "cultivation_log.json", {"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["LLL", "TST"],
          "key": {"L": "#minecraft:logs_that_burn", "T": f"{NS}:mycelial_thread", "S": f"{NS}:mycelial_soil"},
          "result": {"id": f"{NS}:cultivation_log", "count": 2}})
    write(out / "foraging_knife.json", {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": ["  I", " T ", "S  "],
          "key": {"I": "minecraft:iron_ingot", "T": f"{NS}:mycelial_thread", "S": "minecraft:stick"},
          "result": {"id": f"{NS}:foraging_knife"}})
    write(out / "mycelial_soil.json", {"type": "minecraft:crafting_shapeless", "category": "building",
          "ingredients": ["minecraft:dirt", "minecraft:bone_meal", "#c:mushrooms", "#c:mushrooms"],
          "result": {"id": f"{NS}:mycelial_soil", "count": 2}})
    write(out / "string_from_mycelial_thread.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
          "ingredients": [f"{NS}:mycelial_thread", f"{NS}:mycelial_thread"], "result": {"id": "minecraft:string"}})
    write(out / "harvest_hive.json", {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["PPP", "HBH", "PPP"],
          "key": {"P": "#minecraft:planks", "H": "minecraft:honeycomb", "B": "minecraft:barrel"},
          "result": {"id": f"{NS}:harvest_hive"}})
    write(out / "bee_jar.json", {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": [" S ", "G G", " G "],
          "key": {"S": "#minecraft:wooden_slabs", "G": "minecraft:glass_pane"},
          "result": {"id": f"{NS}:bee_jar", "count": 2}})
    mini = out / "minigame"
    for dish in DISHES + list(PSI_DISHES):
        obj = {"type": "jaymoskitchen:minigame", "category": "misc", "dish": dish}
        if dish in NEEDS:
            obj = {"fabric:load_conditions": [{"condition": "fabric:registry_contains", "registry": "minecraft:item",
                                                "values": NEEDS[dish]}], **obj}
        write(mini / f"{dish}.json", obj)


# ---------------------------------------------------------------- tags
def tags():
    t = DATA / "tags"
    add_tag(t / "block/fungus_soil.json", ["#minecraft:dirt", "#minecraft:grass_blocks", "minecraft:moss_block", "minecraft:mycelium",
                                           "#minecraft:nylium", "minecraft:mud", "minecraft:muddy_mangrove_roots",
                                           f"{NS}:mycelial_soil", f"{NS}:truffle_soil"])
    add_tag(t / "block/mineable/foraging_knife.json", [f"{NS}:{n}" for n in SMALL] + [
        "minecraft:red_mushroom", "minecraft:brown_mushroom", "minecraft:red_mushroom_block", "minecraft:brown_mushroom_block",
        "minecraft:mushroom_stem"])
    # seasons: morels in spring, chanterelles late summer into autumn, the autumn flush for most, frostcaps into winter
    seasons = {"spring": ["morel", "frostcap", "puffball", "emberstool"], "summer": ["chanterelle", "deathcap", "puffball", "emberstool"],
               "autumn": ["chanterelle", "deathcap", "frostcap", "puffball", "emberstool"],
               "winter": ["frostcap", "emberstool"]}
    for season, names in seasons.items():
        add_tag(t / f"block/seasons/{season}.json", [f"{NS}:{n}" for n in names])
    add_tag(ROOT / "data/minecraft/tags/block/mineable/shovel.json", [f"{NS}:mycelial_soil", f"{NS}:truffle_soil"])
    add_tag(ROOT / "data/minecraft/tags/block/mineable/axe.json", [f"{NS}:cultivation_log", f"{NS}:harvest_hive"])
    add_tag(ROOT / "data/minecraft/tags/block/overrides_mushroom_light_requirement.json", [f"{NS}:mycelial_soil", f"{NS}:truffle_soil"])
    mush = [f"{NS}:{n}" for n in SMALL]
    add_tag(ROOT / "data/c/tags/item/mushrooms.json", mush + ["minecraft:red_mushroom", "minecraft:brown_mushroom"])
    add_tag(t / "item/foraging_tools.json", [f"{NS}:foraging_knife"])
    add_tag(t / "item/glowcaps.json", optional("jaymoskingdoms:glowcap"))
    add_tag(t / "item/bouncecaps.json", optional("jaymoskingdoms:bouncecap"))
    add_tag(t / "item/inkcaps.json", optional("jaymoskingdoms:inkcap"))
    add_tag(t / "item/cooking/glowcaps.json", [{"id": "#jaymoskitchen:glowcaps", "required": False}])
    add_tag(t / "item/cooking/bouncecaps.json", [{"id": "#jaymoskitchen:bouncecaps", "required": False}])
    # any good mushroom for the forager's stew (not the deathcap, and the truffle is its own ingredient)
    add_tag(t / "item/cooking/foraged.json", [f"{NS}:morel", f"{NS}:chanterelle", f"{NS}:frostcap", f"{NS}:emberstool", f"{NS}:puffball",
                                              "minecraft:red_mushroom", "minecraft:brown_mushroom"] + optional(
        "jaymoskingdoms:glowcap", "jaymoskingdoms:bouncecap"))
    add_tag(t / "item/cooking/mushroom.json", [f"{NS}:morel", f"{NS}:chanterelle", f"{NS}:puffball"])
    add_tag(t / "item/cooking/rice.json", optional("psithurism:rice"))


# ---------------------------------------------------------------- worldgen
def patch(name, block, chance, count, spread=5, extra=None):
    write(DATA / f"worldgen/feature/{name}.json", {"type": "minecraft:simple_block", "to_place": {"id": f"{NS}:{block}"}})
    offset = lambda r, v: {"type": "minecraft:trapezoid", "min": -r, "max": r, "plateau": 0}
    placement = []
    if chance:
        placement.append({"type": "minecraft:rarity_filter", "chance": chance})
    placement += extra or [{"type": "minecraft:in_square"}, {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING_NO_LEAVES"},
                           {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0}]
    placement += [{"type": "minecraft:biome"}, {"type": "minecraft:count", "count": count},
                  {"type": "minecraft:offset", "x": offset(spread, 0), "y": {"type": "minecraft:trapezoid", "min": -2, "max": 2, "plateau": 0},
                   "z": offset(spread, 0)},
                  {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": [
                      {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
                      {"type": "minecraft:would_survive", "state": {"id": f"{NS}:{block}"}}]}}]
    write(DATA / f"worldgen/placed_feature/{name}.json", {"feature": f"{NS}:{name}", "placement": placement})


def worldgen():
    # sparse: one patch of attempts per this many chunks (Spore & Stem's own numbers are 4 to 6)
    patch("forage_morel", "morel", 8, 16)
    patch("forage_chanterelle", "chanterelle", 7, 20)
    patch("forage_deathcap", "deathcap", 6, 16)
    patch("forage_frostcap", "frostcap", 7, 20)
    patch("forage_puffball", "puffball", 8, 20)
    # the Nether's: one patch a layer
    patch("forage_emberstool_nether", "emberstool", 3, 24, 6, [{"type": "minecraft:count_on_every_layer", "count": 1}])
    # deep caves: on a cave floor under the surface
    patch("forage_emberstool_cave", "emberstool", 0, 16, 4, [
        {"type": "minecraft:count", "count": 2}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"above_bottom": 4}, "max_inclusive": {"absolute": 0}}},
        {"type": "minecraft:environment_scan", "direction_of_search": "down", "max_steps": 16,
         "allowed_search_condition": {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
         "target_condition": {"type": "minecraft:solid"}},
        {"type": "minecraft:offset", "x": 0, "y": 1, "z": 0},
        {"type": "minecraft:surface_relative_threshold_filter", "heightmap": "OCEAN_FLOOR_WG", "max_inclusive": -8}])
    write(DATA / "worldgen/feature/forage_truffle_soil.json", {"type": "minecraft:ore", "size": 8, "discard_chance_on_air_exposure": 0.3,
          "targets": [{"target": {"predicate_type": "minecraft:block_match", "block": "minecraft:dirt"}, "state": {"Name": f"{NS}:truffle_soil"}}]})
    write(DATA / "worldgen/placed_feature/forage_truffle_soil.json", {"feature": f"{NS}:forage_truffle_soil", "placement": [
        {"type": "minecraft:count", "count": 3}, {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR_WG"},
        {"type": "minecraft:offset", "x": 0, "y": {"type": "minecraft:uniform", "min_inclusive": -6, "max_inclusive": -2}, "z": 0},
        {"type": "minecraft:biome"}]})


# ---------------------------------------------------------------- hive blockstate and models
def hive_assets():
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for honey in range(6):
            model = "harvest_hive_honey" if honey == 5 else "harvest_hive"
            entry = {"model": f"{NS}:block/forage/{model}"}
            if y:
                entry["y"] = y
            variants[f"facing={facing},honey={honey}"] = entry
    write(ASSETS / "blockstates/harvest_hive.json", {"variants": variants})
    for model, front in (("harvest_hive", "harvest_hive_front"), ("harvest_hive_honey", "harvest_hive_front_honey")):
        write(ASSETS / f"models/block/forage/{model}.json", {"parent": "minecraft:block/orientable", "textures": {
            "top": f"{NS}:block/forage/harvest_hive_top", "front": f"{NS}:block/forage/{front}",
            "side": f"{NS}:block/forage/harvest_hive_side"}})
    write(ASSETS / "models/item/forage/harvest_hive.json", {"parent": f"{NS}:block/forage/harvest_hive"})
    for item, model in (("harvest_hive", "harvest_hive"),):
        write(ASSETS / f"items/{item}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/forage/{model}"}})
    for item in ("bee_jar", "bee_in_a_jar"):
        write(ASSETS / f"models/item/forage/{item}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/forage/{item}"}})
        write(ASSETS / f"items/{item}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/forage/{item}"}})
    for dish in PSI_DISHES:
        write(ASSETS / f"models/item/forage/{dish}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/forage/{dish}"}})
        write(ASSETS / f"items/{dish}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/forage/{dish}"}})


# ---------------------------------------------------------------- art
def px(img, pts, c):
    for x, y in pts:
        img.putpixel((x, y), c)


def planks(w=16, h=16):
    img = Image.new("RGBA", (w, h))
    tones = [(178, 142, 88, 255), (164, 129, 78, 255), (150, 117, 70, 255), (170, 135, 82, 255)]
    for y in range(h):
        for x in range(w):
            img.putpixel((x, y), tones[(y // 4 + (x // 8 if y // 4 % 2 else 0)) % 4])
    for y in range(3, h, 4):
        for x in range(w):
            img.putpixel((x, y), (112, 86, 52, 255))
    return img


COMB = (236, 176, 44, 255)
COMB_DARK = (200, 136, 20, 255)
HOLE = (46, 30, 18, 255)
HONEY = (246, 196, 56, 255)
HONEY_HI = (255, 226, 120, 255)


def comb_band(img, y0, y1):
    for y in range(y0, y1 + 1):
        for x in range(16):
            img.putpixel((x, y), COMB if (x + y) % 3 else COMB_DARK)
    for x in range(16):
        img.putpixel((x, y0 - 1), (112, 86, 52, 255))
        img.putpixel((x, y1 + 1), (112, 86, 52, 255))


def hive_textures():
    out = ASSETS / "textures/block/forage"
    out.mkdir(parents=True, exist_ok=True)
    side = planks()
    comb_band(side, 6, 9)
    side.save(out / "harvest_hive_side.png")
    top = planks()
    for i in range(16):
        for c in ((i, 0), (i, 15), (0, i), (15, i)):
            top.putpixel(c, (112, 86, 52, 255))
    for y in range(5, 11):
        for x in range(5, 11):
            top.putpixel((x, y), COMB if (x + y) % 3 else COMB_DARK)
    top.save(out / "harvest_hive_top.png")
    front = side.copy()
    for y in range(5, 11):
        for x in range(5, 11):
            front.putpixel((x, y), HOLE)
    for x in range(4, 12):
        front.putpixel((x, 11), (200, 164, 108, 255))
        front.putpixel((x, 12), (140, 108, 64, 255))
    front.save(out / "harvest_hive_front.png")
    honey = front.copy()
    for x in (5, 7, 8, 10):
        px(honey, [(x, 11), (x, 12), (x, 13)], HONEY)
    px(honey, [(6, 11), (9, 11), (6, 12), (9, 12)], HONEY)
    px(honey, [(7, 12), (10, 13)], HONEY_HI)
    honey.save(out / "harvest_hive_front_honey.png")


GLASS = (190, 222, 232, 255)
GLASS_FILL = (214, 238, 246, 120)
LID = (150, 108, 62, 255)
LID_DARK = (110, 78, 44, 255)


def jar(with_bee):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(5, 14):
        for x in range(4, 12):
            edge = x in (4, 11) or y in (5, 13)
            img.putpixel((x, y), GLASS if edge else GLASS_FILL)
    for x in range(5, 11):
        img.putpixel((x, 3), LID)
        img.putpixel((x, 4), LID_DARK)
    px(img, [(5, 6), (5, 7)], (255, 255, 255, 255))
    if with_bee:
        body = (240, 198, 40, 255)
        black = (34, 28, 22, 255)
        px(img, [(6, 9), (7, 9), (8, 9), (9, 9), (6, 10), (7, 10), (8, 10), (9, 10)], body)
        px(img, [(7, 9), (7, 10), (9, 9), (9, 10)], black)
        px(img, [(6, 8), (8, 8)], (236, 244, 250, 230))
        px(img, [(5, 9)], black)
    return img


BOWL = (92, 112, 134, 255)
BOWL_HI = (132, 154, 176, 255)
OUTLINE = (40, 24, 12, 255)
RICE = (246, 244, 234, 255)
SOY = (196, 170, 88, 255)
SAUCE = (74, 42, 24, 255)
BROTH = (206, 160, 98, 255)
FISH = (150, 168, 184, 255)
FISH_BELLY = (236, 236, 224, 255)
GRILL = (62, 40, 28, 255)


def bowl_base():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(7, 13):
        inset = 0 if y < 11 else (y - 10)
        for x in range(1 + inset, 15 - inset):
            img.putpixel((x, y), BOWL)
    for x in range(1, 15):
        img.putpixel((x, 7), BOWL_HI)
    for x in range(2, 14):
        img.putpixel((x, 13), OUTLINE)
    for y in range(8, 13):
        img.putpixel((0, y), OUTLINE)
        img.putpixel((15, y), OUTLINE)
    return img


def soy_rice_bowl():
    img = bowl_base()
    for y in (5, 6):
        for x in range(3 if y == 6 else 4, 13 if y == 6 else 12):
            img.putpixel((x, y), RICE)
    for x in range(3, 13):
        img.putpixel((x, 7), RICE)
    px(img, [(5, 6), (8, 5), (10, 6), (6, 7), (11, 7)], SOY)
    px(img, [(7, 6), (9, 7)], SAUCE)
    return img


def fugu_rice_stew():
    img = bowl_base()
    for x in range(3, 13):
        img.putpixel((x, 7), BROTH)
    px(img, [(4, 6), (5, 6), (6, 6), (9, 6), (10, 6), (11, 6)], RICE)
    px(img, [(6, 5), (7, 5), (7, 6), (8, 6)], FISH_BELLY)
    px(img, [(8, 5)], FISH)
    px(img, [(5, 7), (10, 7)], SOY)
    return img


def seared_fugu():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(4, 12):
        for x in range(2, 13):
            dx, dy = (x - 7) / 5.5, (y - 7.5) / 4.0
            if dx * dx + dy * dy <= 1.0:
                img.putpixel((x, y), FISH if y < 8 else FISH_BELLY)
    px(img, [(12, 6), (13, 5), (13, 6), (13, 7), (14, 4), (14, 8)], FISH)
    px(img, [(4, 6)], OUTLINE)
    for x in (5, 7, 9):
        px(img, [(x, 5), (x + 1, 6), (x + 2, 7)], GRILL)
    px(img, [(3, 8), (4, 9)], (200, 150, 70, 255))
    return img


def items_art():
    out = ASSETS / "textures/item/forage"
    out.mkdir(parents=True, exist_ok=True)
    jar(False).save(out / "bee_jar.png")
    jar(True).save(out / "bee_in_a_jar.png")
    soy_rice_bowl().save(out / "soy_rice_bowl.png")
    seared_fugu().save(out / "seared_fugu.png")
    fugu_rice_stew().save(out / "fugu_rice_stew.png")


# ---------------------------------------------------------------- lang
def lang():
    names = {
        "morel": "Morel", "chanterelle": "Chanterelle", "deathcap": "Deathcap", "frostcap": "Frostcap",
        "emberstool": "Emberstool", "puffball": "Puffball", "truffle_soil": "Truffle Soil", "mycelial_soil": "Mycelial Soil",
        "cultivation_log": "Cultivation Log", "harvest_hive": "Harvest Hive",
    }
    out = {}
    for key, name in names.items():
        out[f"block.{NS}.{key}"] = name
        out[f"item.{NS}.{key}"] = name
    for key in SMALL:
        out[f"block.{NS}.potted_{key}"] = f"Potted {names[key]}"
    out.update({
        f"item.{NS}.truffle": "Truffle", f"item.{NS}.mycelial_thread": "Mycelial Thread", f"item.{NS}.foraging_knife": "Foraging Knife",
        f"item.{NS}.bee_jar": "Bee Jar", f"item.{NS}.bee_in_a_jar": "Bee in a Jar",
        f"item.{NS}.roasted_morel": "Roasted Morel", f"item.{NS}.chanterelle_toast": "Chanterelle Toast",
        f"item.{NS}.glowcap_soup": "Glowcap Soup", f"item.{NS}.bouncecap_jelly": "Bouncecap Jelly",
        f"item.{NS}.frostcap_tea": "Frostcap Tea", f"item.{NS}.ember_skewer": "Ember Skewer",
        f"item.{NS}.truffle_stew": "Truffle Stew", f"item.{NS}.foragers_stew": "Forager's Stew",
        f"container.{NS}.harvest_hive": "Harvest Hive",
        f"subtitles.{NS}.block.cultivation_log.inoculate": "Log inoculated",
        f"subtitles.{NS}.block.cultivation_log.harvest": "Mushrooms picked",
        f"tag.item.{NS}.foraging_tools": "Foraging Tools",
        # ingredient buttons of the cooking minigame
        f"step.{NS}.morel": "Morel", f"step.{NS}.chanterelle": "Chanterelle", f"step.{NS}.frostcap": "Frostcap",
        f"step.{NS}.emberstool": "Emberstool", f"step.{NS}.truffle": "Truffle", f"step.{NS}.glowcap": "Glowcap",
        f"step.{NS}.bouncecap": "Bouncecap", f"step.{NS}.foraged": "Any Good Mushroom",
        f"step.{NS}.soybeans": "Soybeans", f"step.{NS}.fugu": "Fugu",
    })
    for dish, name in PSI_DISHES.items():
        out[f"item.{NS}.{dish}"] = name
    write(ASSETS / "lang/parts/forage.json", out)

    sounds = ASSETS / "sounds.json"
    obj = json.loads(sounds.read_text(encoding="utf-8"))
    for ev in ("inoculate", "harvest"):
        obj[f"block.cultivation_log.{ev}"] = {"sounds": [f"{NS}:block/cultivation_log/{ev}1", f"{NS}:block/cultivation_log/{ev}2"],
                                              "subtitle": f"subtitles.{NS}.block.cultivation_log.{ev}"}
    sounds.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    loot()
    recipes()
    tags()
    worldgen()
    hive_assets()
    hive_textures()
    items_art()
    lang()
    print("done")
