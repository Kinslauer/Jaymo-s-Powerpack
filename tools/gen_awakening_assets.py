"""Writes the assets of the Awakening picks' items and blocks (docs/Awakening-Plan.md, patches 6 and 7):

* the relic belt and its seven relics (drawn here, pixel by pixel);
* boneiron: the deepslate ore (vanilla's deepslate iron ore with its specks turned to old bone), the raw lump and ingot
  (vanilla's iron ones recoloured), the plate (drawn), the Bonding Vat (vanilla's cauldron shape, its textures recoloured,
  and a milky brine drawn here); their models, block states, loot, tags, recipes and the ore's worldgen;
* the threat tracker (drawn: a brass case with a needle; the needle's eight turns are separate textures).

Run pointing at a 26.3 client jar (for the textures recoloured from vanilla):

    python3 -I tools/gen_awakening_assets.py /path/to/minecraft-clientOnly-26.3.jar

Safe to run again (every file is overwritten).
"""
import colorsys
import io
import json
import math
import os
import sys
import zipfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymospowers")
DATA = os.path.join(RES, "data")
NS = "jaymospowers"

RELICS = {
    # kind: (light, dark) colours of its stone
    "heartseed": ((0xF0, 0x60, 0x68), (0x8A, 0x18, 0x24)),
    "wind_knot": ((0xE8, 0xF6, 0xFA), (0x80, 0xA8, 0xB8)),
    "focus_stone": ((0xC8, 0xA0, 0xFF), (0x50, 0x28, 0x90)),
    "hale_coin": ((0xFF, 0xE0, 0x70), (0x9A, 0x70, 0x18)),
    "last_ember": ((0xFF, 0xB0, 0x40), (0xA0, 0x30, 0x10)),
    "tide_scale": ((0x80, 0xE0, 0xE8), (0x18, 0x60, 0x78)),
    "stride_spur": ((0xD8, 0xDC, 0xE0), (0x58, 0x60, 0x68)),
}
BONE_LIGHT, BONE_DARK = (0xF2, 0xEA, 0xD2), (0x9C, 0x8E, 0x6C)


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def save(img, *parts):
    path = os.path.join(ASSETS, "textures", *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def tone(rgb, light, dark):
    """A pixel's brightness laid over a light-dark ramp (the shading kept, the colour changed)."""
    _, _, v = colorsys.rgb_to_hsv(*(c / 255 for c in rgb))
    return mix(dark, light, v)


def vanilla(jar, path):
    with zipfile.ZipFile(jar) as z:
        return Image.open(io.BytesIO(z.read("assets/minecraft/textures/" + path))).convert("RGBA")


def recolour(img, light, dark, keep=None):
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0 or keep is not None and not keep(x, y):
                continue
            px[x, y] = tone((r, g, b), light, dark) + (a,)
    return out


# ---- drawn items ----------------------------------------------------------------------------------------------------

def relic(light, dark):
    """A small rounded stone in a bone setting, a highlight up-left."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    cx, cy, r = 7.5, 8.0, 5.2
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, (y - cy) * 1.1)
            if d <= r + 1.2 and d > r:
                px[x, y] = mix(BONE_DARK, BONE_LIGHT, 0.4 if y > cy else 0.8) + (255,)
            elif d <= r:
                lit = max(0.0, min(1.0, 1.0 - (math.hypot(x - (cx - 2), y - (cy - 2)) / (r * 1.6))))
                px[x, y] = mix(dark, light, lit) + (255,)
    for x, y in ((5, 5), (6, 5), (5, 6)):
        px[x, y] = (255, 255, 255, 255)
    # the cord loop at the top
    for x, y in ((7, 1), (8, 1), (6, 2), (9, 2)):
        px[x, y] = BONE_DARK + (255,)
    return img


def belt():
    """A leather belt in a loop, with four little pouches and a brass buckle."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    leather_l, leather_d = (0x9A, 0x62, 0x34), (0x4E, 0x2C, 0x16)
    for y in range(16):
        for x in range(16):
            d = math.hypot((x - 7.5) / 7.2, (y - 8.5) / 4.2)
            if 0.72 <= d <= 1.0:
                px[x, y] = mix(leather_d, leather_l, 0.3 + 0.5 * (y < 8.5)) + (255,)
    for i, x0 in enumerate((2, 5, 9, 12)):
        for y in range(10, 14):
            for x in range(x0, x0 + 2):
                px[x, y] = (mix(leather_d, leather_l, 0.65) if y > 10 else leather_d) + (255,)
    for y in range(3, 7):
        for x in range(6, 10):
            edge = x in (6, 9) or y in (3, 6)
            px[x, y] = ((0xE0, 0xB8, 0x48) if edge else (0x6A, 0x48, 0x10)) + (255,)
    return img


def plate():
    """A curved boneiron plate with two rivets."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(3, 13):
        for x in range(2, 14):
            bow = abs(x - 7.5) / 6.0
            if y < 3 + bow * 2 or y > 12 - bow * 1:
                continue
            t = 1.0 - (y - 3) / 10.0 * 0.6 - bow * 0.25
            px[x, y] = mix(BONE_DARK, BONE_LIGHT, t) + (255,)
    for x, y in ((4, 7), (11, 7)):
        px[x, y] = (0x5A, 0x50, 0x40, 255)
    return img


def brine():
    """The vat's milky brine, faintly mottled."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            n = (math.sin(x * 1.7 + y * 0.6) + math.sin(y * 1.3 - x * 0.8)) * 0.25 + 0.5
            px[x, y] = mix((0xB8, 0xB0, 0x98), (0xE8, 0xE2, 0xCC), n) + (255,)
    return img


def tracker(turn):
    """The threat tracker: a dark-brass case, a pale face and a red needle at one of eight turns (0 = up, clockwise)."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d <= 7.0 and d > 5.6:
                px[x, y] = mix((0x5A, 0x40, 0x18), (0xC8, 0x98, 0x40), 0.7 if y < 8 else 0.35) + (255,)
            elif d <= 5.6:
                px[x, y] = mix((0x40, 0x3A, 0x34), (0x6A, 0x62, 0x58), 0.5) + (255,)
    a = turn * math.pi / 4.0
    for s in range(0, 9):
        t = s / 2.0
        x = round(7.5 + math.sin(a) * t)
        y = round(7.5 - math.cos(a) * t)
        if 0 <= x < 16 and 0 <= y < 16:
            px[x, y] = (0xE8, 0x30, 0x28, 255) if t > 0.6 else (0xF0, 0xE0, 0xC0, 255)
    return img


# ---- the models and data --------------------------------------------------------------------------------------------

def item_model(name, texture=None):
    write(os.path.join(ASSETS, "models", "item", name + ".json"),
          {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/" + (texture or name)}})


def item_def(name, model=None):
    write(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": model or NS + ":item/" + name}})


def loot_self(name):
    write(os.path.join(DATA, NS, "loot_table", "blocks", name + ".json"), {
        "type": "minecraft:block", "random_sequence": NS + ":blocks/" + name,
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": NS + ":" + name}],
                   "condition": {"type": "minecraft:survives_explosion"}}]})


def add_tag(path, values):
    full = os.path.join(DATA, *path.split("/")) + ".json"
    with open(full, encoding="utf-8") as f:
        tag = json.load(f)
    for v in values:
        if v not in tag["values"]:
            tag["values"].append(v)
    indent = "\t" if open(full, encoding="utf-8").read().startswith("{\n\t") else 2
    with open(full, "w", encoding="utf-8", newline="\n") as f:
        json.dump(tag, f, indent=indent, ensure_ascii=False)
        f.write("\n")


def main(jar):
    # the relics and the belt
    for kind, (light, dark) in RELICS.items():
        save(relic(light, dark), "item", "relic_" + kind + ".png")
        item_model("relic_" + kind)
        item_def("relic_" + kind)
    save(belt(), "item", "relic_belt.png")
    item_model("relic_belt")
    item_def("relic_belt")
    add_tag("jaymospowers/tags/item/baubles/belt", [NS + ":relic_belt"])
    write(os.path.join(DATA, NS, "recipe", "relic_belt.json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment", "pattern": ["LLL", "SGS"],
        "key": {"L": "minecraft:leather", "S": "minecraft:string", "G": "minecraft:gold_ingot"},
        "result": {"id": NS + ":relic_belt"}})

    # boneiron
    ore = vanilla(jar, "block/deepslate_iron_ore.png")
    base = vanilla(jar, "block/deepslate.png")
    bp, op = base.load(), ore.load()
    speck = lambda x, y: sum(abs(op[x, y][i] - bp[x, y][i]) for i in range(3)) > 40
    save(recolour(ore, BONE_LIGHT, BONE_DARK, speck), "block", "deepslate_boneiron_ore.png")
    save(recolour(vanilla(jar, "item/raw_iron.png"), BONE_LIGHT, (0x7A, 0x6A, 0x4A)), "item", "raw_boneiron.png")
    save(recolour(vanilla(jar, "item/iron_ingot.png"), (0xFA, 0xF4, 0xE0), (0x8A, 0x7C, 0x5A)), "item", "boneiron_ingot.png")
    save(plate(), "item", "boneiron_plate.png")
    for name in ("raw_boneiron", "boneiron_ingot", "boneiron_plate"):
        item_model(name)
        item_def(name)
    write(os.path.join(ASSETS, "models", "block", "deepslate_boneiron_ore.json"),
          {"parent": "minecraft:block/cube_all", "textures": {"all": NS + ":block/deepslate_boneiron_ore"}})
    write(os.path.join(ASSETS, "blockstates", "deepslate_boneiron_ore.json"), {"variants": {"": {"model": NS + ":block/deepslate_boneiron_ore"}}})
    item_def("deepslate_boneiron_ore", NS + ":block/deepslate_boneiron_ore")

    vat_light, vat_dark = (0xC8, 0xBC, 0x9C), (0x2E, 0x28, 0x22)
    for part in ("top", "side", "bottom", "inner"):
        save(recolour(vanilla(jar, "block/cauldron_" + part + ".png"), vat_light, vat_dark), "block", "bonding_vat_" + part + ".png")
    save(brine(), "block", "bonding_vat_brine.png")
    textures = {"particle": NS + ":block/bonding_vat_side", "top": NS + ":block/bonding_vat_top", "bottom": NS + ":block/bonding_vat_bottom",
                "side": NS + ":block/bonding_vat_side", "inside": NS + ":block/bonding_vat_inner"}
    write(os.path.join(ASSETS, "models", "block", "bonding_vat.json"), {"parent": "minecraft:block/cauldron", "textures": textures})
    for level, parent in (("level1", "template_cauldron_level1"), ("level2", "template_cauldron_level2"), ("full", "template_cauldron_full")):
        write(os.path.join(ASSETS, "models", "block", "bonding_vat_" + level + ".json"),
              {"parent": "minecraft:block/" + parent, "textures": dict(textures, content=NS + ":block/bonding_vat_brine")})
    variants = {}
    for metal in range(7):
        model = "bonding_vat" if metal == 0 else "bonding_vat_level1" if metal <= 2 else "bonding_vat_level2" if metal <= 4 else "bonding_vat_full"
        variants["metal=%d" % metal] = {"model": NS + ":block/" + model}
    write(os.path.join(ASSETS, "blockstates", "bonding_vat.json"), {"variants": variants})
    item_def("bonding_vat", NS + ":block/bonding_vat")

    write(os.path.join(DATA, NS, "loot_table", "blocks", "deepslate_boneiron_ore.json"), {
        "type": "minecraft:block", "random_sequence": NS + ":blocks/deepslate_boneiron_ore",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "condition": "minecraft:tool/can_silk_touch", "name": NS + ":deepslate_boneiron_ore"},
            {"type": "minecraft:item", "name": NS + ":raw_boneiron", "modifier": [
                {"type": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                {"type": "minecraft:explosion_decay"}]}]}]}]})
    loot_self("bonding_vat")
    add_tag("minecraft/tags/block/mineable/pickaxe", [NS + ":deepslate_boneiron_ore", NS + ":bonding_vat"])
    add_tag("minecraft/tags/block/needs_iron_tool", [NS + ":deepslate_boneiron_ore"])
    for kind, time in (("smelting", 200), ("blasting", 100)):
        write(os.path.join(DATA, NS, "recipe", "boneiron_ingot_" + kind + ".json"), {
            "type": "minecraft:" + kind, "category": "misc", "ingredient": NS + ":raw_boneiron",
            "result": {"id": NS + ":boneiron_ingot"}, "experience": 1.0, "cookingtime": time})
    write(os.path.join(DATA, NS, "recipe", "boneiron_plate.json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment", "pattern": ["III"],
        "key": {"I": NS + ":boneiron_ingot"}, "result": {"id": NS + ":boneiron_plate"}})
    write(os.path.join(DATA, NS, "recipe", "bonding_vat.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": ["minecraft:cauldron", NS + ":boneiron_ingot", NS + ":boneiron_ingot", "minecraft:bone_block"],
        "result": {"id": NS + ":bonding_vat"}})
    write(os.path.join(DATA, NS, "worldgen", "feature", "ore_boneiron.json"), {
        "type": "minecraft:ore", "size": 3, "discard_chance_on_air_exposure": 0.5,
        "targets": [{"target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables"},
                     "state": {"id": NS + ":deepslate_boneiron_ore"}}]})
    write(os.path.join(DATA, NS, "worldgen", "placed_feature", "ore_boneiron.json"), {
        "feature": NS + ":ore_boneiron",
        "placement": [{"type": "minecraft:rarity_filter", "chance": 3}, {"type": "minecraft:in_square"},
                      {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": -64},
                                                                    "max_inclusive": {"absolute": -40}}},
                      {"type": "minecraft:biome"}]})

    if "--no-tracker" in sys.argv:
        return
    # the threat tracker: the needle's eight turns, picked by the item's own number
    cases = []
    for turn in range(8):
        name = "threat_tracker_%d" % turn
        save(tracker(turn), "item", name + ".png")
        item_model(name)
        cases.append({"when": turn, "model": {"type": "minecraft:model", "model": NS + ":item/" + name}})
    write(os.path.join(ASSETS, "items", "threat_tracker.json"), {"model": {
        "type": "minecraft:select", "property": "minecraft:component", "component": NS + ":tracker_turn", "cases": cases,
        "fallback": {"type": "minecraft:model", "model": NS + ":item/threat_tracker_0"}}})


if __name__ == "__main__":
    main(sys.argv[1])
