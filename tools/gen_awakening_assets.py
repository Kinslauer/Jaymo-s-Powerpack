"""Writes the assets of the Awakening picks' items and blocks (docs/Awakening-Plan.md, patches 6 and 7):

* the relic belt and its seven relics (drawn here, pixel by pixel);
* boneiron: the deepslate ore (vanilla's deepslate iron ore with its specks turned to old bone), the raw lump and ingot
  (vanilla's iron ones recoloured), the plate (drawn), the Bonding Vat (vanilla's cauldron shape, its textures recoloured,
  and a milky brine drawn here); their models, block states, loot, tags, recipes and the ore's worldgen;
* the threat tracker (drawn: a brass case with a needle; the needle's sixteen turns are separate textures, picked by
  vanilla's compass property aimed at the item's lodestone tracker, which the server sets to the nearest threat).

And in Jaymo's Kingdoms (patch 7): the Cordon's five skins (64x64 player layout, drawn here) and the Cordon summons.

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
    """The threat tracker: a dark-brass case, a pale face and a red needle at one of sixteen turns (0 = up, clockwise)."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d <= 7.0 and d > 5.6:
                px[x, y] = mix((0x5A, 0x40, 0x18), (0xC8, 0x98, 0x40), 0.7 if y < 8 else 0.35) + (255,)
            elif d <= 5.6:
                px[x, y] = mix((0x40, 0x3A, 0x34), (0x6A, 0x62, 0x58), 0.5) + (255,)
    a = turn * math.pi / 8.0
    for s in range(0, 9):
        t = s / 2.0
        x = round(7.5 + math.sin(a) * t)
        y = round(7.5 - math.cos(a) * t)
        if 0 <= x < 16 and 0 <= y < 16:
            px[x, y] = (0xE8, 0x30, 0x28, 255) if t > 0.6 else (0xF0, 0xE0, 0xC0, 255)
    return img


# ---- Jaymo's Kingdoms: the Cordon ------------------------------------------------------------------------------------

K_ASSETS = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms")
SKIN, BAND = (0xC8, 0xA0, 0x84), (0x16, 0x14, 0x16)

# a box in the 64x64 player skin: its texture corner and size (w, h, d)
BOXES = {
    "head": ((0, 0), (8, 8, 8)), "hat": ((32, 0), (8, 8, 8)),
    "body": ((16, 16), (8, 12, 4)), "jacket": ((16, 32), (8, 12, 4)),
    "right_arm": ((40, 16), (4, 12, 4)), "left_arm": ((32, 48), (4, 12, 4)),
    "right_leg": ((0, 16), (4, 12, 4)), "left_leg": ((16, 48), (4, 12, 4)),
}


def faces(box):
    """Each face of a box: name -> (x, y, w, h) in the skin."""
    (u, v), (w, h, d) = BOXES[box]
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}


def paint(px, box, colour):
    """Fills a box: colour(face, x, y, w, h) gives each pixel's (r, g, b) or None (left clear)."""
    for face, (x0, y0, w, h) in faces(box).items():
        for y in range(h):
            for x in range(w):
                c = colour(face, x, y, w, h)
                if c is not None:
                    px[x0 + x, y0 + y] = tuple(c) + (255,)


def grain(c, x, y, k=10):
    n = int((math.sin(x * 2.3 + y * 1.7) + math.sin(x * 0.7 - y * 2.9)) * k / 2)
    return tuple(max(0, min(255, v + n)) for v in c)


def cordon_skin(rank):
    """One rank's skin: ash robes, the black band over the eyes, and the rank's own marks (see CordonRenderer)."""
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()
    robe = {"sentinel": (0x8A, 0x85, 0x80), "stalker": (0x5E, 0x5A, 0x56), "enforcer": (0x7A, 0x76, 0x72),
            "overseer": (0x8A, 0x85, 0x80), "arbiter": (0x24, 0x22, 0x24)}[rank]
    dark = mix(robe, (0, 0, 0), 0.35)
    red = (0xB0, 0x20, 0x2A)

    def head(face, x, y, w, h):
        if face == "top":
            return grain((0x3A, 0x36, 0x34), x, y)
        if face == "bottom":
            return SKIN
        if face in ("front", "right", "left", "back") and y in (3, 4):
            return BAND
        if rank == "arbiter" and face == "front":
            return red if y == 6 and 2 <= x <= 5 else grain((0xB8, 0xB4, 0xAC), x, y, 6)
        if face == "front" and y == 6 and 3 <= x <= 4:
            return mix(SKIN, (0x60, 0x30, 0x28), 0.5)
        if face == "back" or y < 2:
            return grain((0x3A, 0x36, 0x34), x, y)
        return SKIN

    def hood(face, x, y, w, h):
        if rank not in ("stalker", "overseer", "arbiter"):
            return None
        c = {"stalker": (0x3A, 0x37, 0x34), "overseer": (0x6A, 0x22, 0x26), "arbiter": (0x18, 0x16, 0x18)}[rank]
        if face == "bottom" or face == "front" and y >= 2 and 1 <= x <= w - 2:
            return None
        if face in ("right", "left") and y >= 7:
            return None
        return grain(c, x, y, 8)

    def body(face, x, y, w, h):
        if face in ("top", "bottom"):
            return dark
        c = grain(robe, x, y)
        if y == 0:
            c = dark
        if y == 7:
            c = (0xC8, 0xA0, 0x40) if rank == "overseer" else dark
        if rank == "enforcer" and face in ("front", "back") and y < 7:
            c = (0xB8, 0xB8, 0xB8) if (x + y) % 2 == 0 else (0x70, 0x70, 0x74)
        if face == "front" and rank in ("enforcer", "overseer") and x == (y * w) // h:
            c = red
        if rank == "arbiter" and y == h - 1:
            c = red
        return c

    def arm(face, x, y, w, h):
        if face == "bottom" or y >= 10:
            return SKIN
        if face == "top":
            return dark
        if rank == "arbiter" and y == 9:
            return red
        return grain(robe, x, y)

    def leg(face, x, y, w, h):
        if face == "bottom" or y >= 9:
            return (0x2A, 0x26, 0x22)
        if face == "top":
            return dark
        if rank == "arbiter" and y == 8:
            return red
        return grain(mix(robe, dark, 0.2), x, y)

    paint(px, "head", head)
    paint(px, "hat", hood)
    paint(px, "body", body)
    for a in ("right_arm", "left_arm"):
        paint(px, a, arm)
    for l in ("right_leg", "left_leg"):
        paint(px, l, leg)
    return img


def summons():
    """The Cordon's summons: a folded grey letter, its binding-red wax seal."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(3, 13):
        for x in range(2, 14):
            edge = y in (3, 12) or x in (2, 13)
            px[x, y] = (mix((0xB8, 0xB0, 0xA0), (0xE0, 0xD8, 0xC8), 0.3 if edge else 0.8 - 0.04 * abs(x - 8))) + (255,)
    for x in range(3, 13):
        y = 4 + abs(x - 7.5) // 1.5
        px[x, int(y)] = (0x90, 0x88, 0x78, 255)
    for y in range(7, 11):
        for x in range(6, 10):
            if math.hypot(x - 7.5, y - 8.5) <= 2.0:
                px[x, y] = (0xB0, 0x20, 0x2A, 255) if math.hypot(x - 7.0, y - 8.0) > 0.8 else (0xE0, 0x50, 0x50, 255)
    return img


def kingdoms():
    for rank in ("sentinel", "stalker", "enforcer", "overseer", "arbiter"):
        path = os.path.join(K_ASSETS, "textures", "entity", "cordon", rank + ".png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        cordon_skin(rank).save(path)
    path = os.path.join(K_ASSETS, "textures", "item", "cordon_summons.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    summons().save(path)
    write(os.path.join(K_ASSETS, "models", "item", "cordon_summons.json"),
          {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymoskingdoms:item/cordon_summons"}})
    write(os.path.join(K_ASSETS, "items", "cordon_summons.json"),
          {"model": {"type": "minecraft:model", "model": "jaymoskingdoms:item/cordon_summons"}})


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

    kingdoms()
    if "--no-tracker" in sys.argv:
        return
    # the threat tracker: the needle's sixteen turns, by vanilla's compass angle to the item's lodestone tracker (0 = the
    # target straight ahead, the needle up; it turns clockwise), spinning when there's none; no swap bob as it updates
    entries = []
    for turn in range(16):
        name = "threat_tracker_%d" % turn
        save(tracker(turn), "item", name + ".png")
        item_model(name)
        entries.append({"threshold": 0.0 if turn == 0 else turn - 0.5, "model": {"type": "minecraft:model", "model": NS + ":item/" + name}})
    entries.append({"threshold": 15.5, "model": {"type": "minecraft:model", "model": NS + ":item/threat_tracker_0"}})
    write(os.path.join(ASSETS, "items", "threat_tracker.json"), {"hand_animation_on_swap": False, "model": {
        "type": "minecraft:range_dispatch", "property": "minecraft:compass", "target": "lodestone", "scale": 16.0,
        "entries": entries}})


if __name__ == "__main__":
    main(sys.argv[1])
