"""The hero add-on picks' art: the signal brazier (two textures, unlit and lit models, blockstate, item, loot table,
recipe) and the bundle of evidence a night crook drops (icon, item definition and model). Run from kingdoms/:
python -I tools/gen_hero_addons.py

Every face names a #variable (26.3 models: a raw sprite id renders purple). The brazier texture is a strip atlas: a face
uses the whole band it belongs to, so the bands are drawn to look right stretched."""
import json
import os
from PIL import Image

RES = "src/main/resources"
ASSETS = RES + "/assets/jaymoskingdoms"
DATA = RES + "/data/jaymoskingdoms"


def put(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


# bands (rows): stone pillar, iron bowl, coals, flame
STONE, IRON, COALS, FLAME = [0, 0, 16, 6], [0, 6, 16, 12], [0, 12, 16, 14], [0, 14, 16, 16]


def brazier_texture(lit):
    img = Image.new("RGB", (16, 16))
    px = img.load()
    brick = [(150, 150, 150), (131, 131, 131), (112, 112, 112)]
    mortar = (97, 97, 97)
    iron = [(168, 170, 176), (140, 142, 150), (108, 110, 120), (82, 84, 94)]
    for y in range(0, 6):  # stone bricks
        for x in range(16):
            row = y % 3
            offset = 0 if y < 3 else 4
            seam = row == 2 or (x + offset) % 8 == 7
            c = brick[1]
            if seam:
                c = mortar
            elif row == 0 or (x + offset) % 8 == 0:
                c = brick[0]
            elif (x * 7 + y * 3) % 11 == 0:
                c = brick[2]
            px[x, y] = c
    for y in range(6, 12):  # the iron bowl, riveted
        for x in range(16):
            row = y - 6
            c = iron[1]
            if row == 0:
                c = iron[0]
            elif row == 5:
                c = iron[3]
            elif x % 4 == 3:
                c = iron[2]
            if row in (1, 4) and x % 8 == 2:
                c = iron[3]
            px[x, y] = c
    for y in range(12, 14):  # coals
        for x in range(16):
            if lit:
                px[x, y] = (255, 140, 40) if (x + y) % 3 else (214, 84, 24)
            else:
                px[x, y] = (46, 42, 40) if (x + y) % 3 else (30, 28, 28)
    for x in range(16):  # the flame
        for y in (14, 15):
            if lit:
                px[x, y] = (255, 226, 120) if (x + y) % 2 else (255, 176, 60)
            else:
                px[x, y] = (46, 42, 40)
    img.save(ASSETS + "/textures/block/signal_brazier%s.png" % ("_lit" if lit else ""))


def box(lo, hi, band, tex, faces=("north", "south", "east", "west", "up", "down")):
    return {"from": lo, "to": hi, "faces": {f: {"uv": band, "texture": tex} for f in faces}}


def elements(lit):
    t = "#brazier"
    out = [
        box([4, 0, 4], [12, 3, 12], STONE, t),
        box([6, 3, 6], [10, 8, 10], STONE, t),
        box([2, 8, 2], [14, 9, 14], IRON, t),
        box([3, 9, 3], [13, 12, 13], IRON, t, ("north", "south", "east", "west", "down")),
        box([4, 11, 4], [12, 12, 12], COALS, t, ("up",)),
    ]
    if lit:
        out.append(box([5, 12, 5], [11, 16, 11], FLAME, t, ("north", "south", "east", "west", "up")))
    return out


def model(lit):
    tex = "jaymoskingdoms:block/signal_brazier" + ("_lit" if lit else "")
    return {"parent": "minecraft:block/block", "textures": {"particle": tex, "brazier": tex}, "elements": elements(lit)}


def evidence_icon():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    paper, light, dark, edge = (214, 196, 156, 255), (232, 218, 182, 255), (176, 156, 118, 255), (96, 76, 52, 255)
    twine, wax = (150, 112, 70, 255), (168, 48, 44, 255)
    for y in range(3, 14):  # a folded bundle of papers
        for x in range(3, 13):
            c = paper
            if y == 3 or x == 3:
                c = light
            elif y == 13 or x == 12:
                c = dark
            px[x, y] = c
    for x in range(3, 13):
        px[x, 2], px[x, 14] = edge, edge
    for y in range(2, 15):
        px[2, y], px[13, y] = edge, edge
    for y in range(3, 14):  # tied with twine, sealed in red wax
        px[7, y] = twine
        px[8, y] = twine
    for x in range(3, 13):
        px[x, 8] = twine
    for (x, y) in [(7, 7), (8, 7), (7, 9), (8, 9), (6, 8), (9, 8)]:
        px[x, y] = wax
    for y in range(5, 8):  # a line of writing
        for x in range(4, 7):
            if (x + y) % 2 == 0:
                px[x, y] = edge
    img.save(ASSETS + "/textures/item/crook_evidence.png")


def main():
    brazier_texture(False)
    brazier_texture(True)
    put(ASSETS + "/models/block/signal_brazier.json", model(False))
    put(ASSETS + "/models/block/signal_brazier_lit.json", model(True))
    put(ASSETS + "/blockstates/signal_brazier.json", {"variants": {
        "lit=false": {"model": "jaymoskingdoms:block/signal_brazier"},
        "lit=true": {"model": "jaymoskingdoms:block/signal_brazier_lit"}}})
    put(ASSETS + "/items/signal_brazier.json", {"model": {"type": "minecraft:model", "model": "jaymoskingdoms:block/signal_brazier"}})
    put(DATA + "/loot_table/blocks/signal_brazier.json", {
        "type": "minecraft:block", "random_sequence": "jaymoskingdoms:blocks/signal_brazier",
        "pools": [{"rolls": 1, "conditions": [{"condition": "minecraft:survives_explosion"}],
                   "entries": [{"type": "minecraft:item", "name": "jaymoskingdoms:signal_brazier"}]}]})
    put(DATA + "/recipe/signal_brazier.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["I I", "ICI", "BBB"],
        "key": {"I": "minecraft:iron_ingot", "C": "minecraft:coal", "B": "minecraft:stone_bricks"},
        "result": {"count": 1, "id": "jaymoskingdoms:signal_brazier"}})
    evidence_icon()
    put(ASSETS + "/models/item/crook_evidence.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymoskingdoms:item/crook_evidence"}})
    put(ASSETS + "/items/crook_evidence.json", {"model": {"type": "minecraft:model", "model": "jaymoskingdoms:item/crook_evidence"}})


main()


# ---- Kingdoms track 2: sketchbook and sketch, the six sky shards, the sky crown and its stand, the five trial chests ----

def item_def(name, model=None):
    put(ASSETS + "/items/%s.json" % name, {"model": {"type": "minecraft:model", "model": model or "jaymoskingdoms:item/" + name}})


def flat_item(name, img):
    img.save(ASSETS + "/textures/item/%s.png" % name)
    put(ASSETS + "/models/item/%s.json" % name, {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymoskingdoms:item/" + name}})
    item_def(name)


def sketchbook_icon():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    cover, spine, page, edge = (92, 66, 44, 255), (66, 46, 30, 255), (226, 210, 170, 255), (40, 28, 20, 255)
    for y in range(2, 14):
        for x in range(3, 13):
            px[x, y] = cover
        px[3, y] = spine
        px[4, y] = spine
    for x in range(3, 13):
        px[x, 2], px[x, 13] = edge, edge
    for y in range(2, 14):
        px[12, y] = edge
    for y in range(4, 11):
        px[11, y] = page
    for (x, y) in [(6, 5), (7, 6), (8, 7), (9, 8), (8, 9), (7, 9), (6, 10)]:
        px[x, y] = (24, 20, 18, 255)
    flat_item("sketchbook", img)


def sketch_icon():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    paper, edge, ink = (230, 214, 174, 255), (120, 96, 66, 255), (30, 26, 24, 255)
    for y in range(2, 15):
        for x in range(2, 14):
            px[x, y] = paper
    for x in range(2, 14):
        px[x, 2], px[x, 14] = edge, edge
    for y in range(2, 15):
        px[2, y], px[13, y] = edge, edge
    for (x, y) in [(4, 11), (5, 10), (6, 9), (7, 8), (8, 7), (9, 6), (10, 5), (11, 6), (11, 8), (10, 9), (9, 10), (7, 11), (5, 12)]:
        px[x, y] = ink
    flat_item("sketch", img)


SHARD_TINTS = {"step": (190, 230, 255), "lode": (170, 170, 190), "cold": (150, 210, 240), "night": (120, 90, 200), "breath": (80, 190, 210),
               "heart": (230, 110, 120)}


def shard_icon(kind):
    r, g, b = SHARD_TINTS[kind]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(2, 14):
        half = (y - 2) // 2 if y < 8 else (13 - y) // 2 + 1
        for x in range(8 - half - 1, 8 + half + 1):
            shade = 1.15 if x < 8 else 0.8
            px[x, y] = (min(255, int(r * shade)), min(255, int(g * shade)), min(255, int(b * shade)), 235)
    for y in range(2, 14):
        for x in range(16):
            if px[x, y][3] and (x == 0 or px[x - 1, y][3] == 0):
                px[x, y] = (255, 255, 255, 255)
    flat_item("sky_shard_" + kind, img)


def crown_icon():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    gold, dark = (240, 200, 70, 255), (170, 130, 40, 255)
    for x in range(2, 14):
        for y in range(9, 13):
            px[x, y] = gold if y < 12 else dark
    for x in (2, 5, 8, 11, 13):
        for y in range(5, 9):
            px[x, y] = gold
    for (x, y), k in zip([(3, 10), (6, 10), (9, 10), (12, 10), (7, 6), (10, 7)], ("step", "lode", "cold", "night", "breath", "heart")):
        r, g, b = SHARD_TINTS[k]
        px[x, y] = (r, g, b, 255)
    flat_item("sky_crown", img)


def stand_textures():
    stone = Image.new("RGB", (16, 16))
    px = stone.load()
    for y in range(16):
        for x in range(16):
            n = (x * 7 + y * 13) % 5
            px[x, y] = (128 + n * 4, 130 + n * 4, 136 + n * 4)
            if y % 8 == 7 or (x + (4 if y >= 8 else 0)) % 8 == 7:
                px[x, y] = (96, 98, 104)
    stone.save(ASSETS + "/textures/block/crown_stand.png")
    glass = Image.new("RGBA", (16, 16), (200, 230, 255, 220))
    px = glass.load()
    for y in range(16):
        for x in range(16):
            if (x + y) % 5 == 0:
                px[x, y] = (255, 255, 255, 240)
    glass.save(ASSETS + "/textures/block/crown_shard.png")


def cube(lo, hi, tex):
    return {"from": lo, "to": hi, "faces": {f: {"texture": tex} for f in ("north", "south", "east", "west", "up", "down")}}


def stand_models():
    base = {"textures": {"stand": "jaymoskingdoms:block/crown_stand", "particle": "jaymoskingdoms:block/crown_stand"}, "elements": [
        cube([3, 0, 3], [13, 2, 13], "#stand"), cube([6, 2, 6], [10, 9, 10], "#stand"), cube([3, 9, 3], [13, 11, 13], "#stand")]}
    put(ASSETS + "/models/block/crown_stand.json", base)
    slots = [(3.5, 3.5), (6.5, 3.5), (9.5, 3.5), (3.5, 9.5), (6.5, 9.5), (9.5, 9.5)]
    mp = [{"apply": {"model": "jaymoskingdoms:block/crown_stand"}}]
    for i, (x, z) in enumerate(slots):
        put(ASSETS + "/models/block/crown_stand_s%d.json" % i, {"textures": {"shard": "jaymoskingdoms:block/crown_shard"},
            "elements": [cube([x, 11, z], [x + 3, 15, z + 3], "#shard")]})
        mp.append({"when": {"s%d" % i: "true"}, "apply": {"model": "jaymoskingdoms:block/crown_stand_s%d" % i}})
    put(ASSETS + "/blockstates/crown_stand.json", {"multipart": mp})
    item_def("crown_stand", "jaymoskingdoms:block/crown_stand")


GLYPHS = {
    0: [(4, 11), (5, 10), (6, 9), (7, 8), (8, 7), (9, 6), (10, 5), (11, 4), (11, 5), (11, 6), (10, 6), (9, 6)],
    1: [(7, 7), (8, 7), (7, 8), (8, 8), (6, 6), (9, 6), (6, 9), (9, 9), (5, 5), (10, 5), (5, 10), (10, 10)],
    2: [(5, 4), (5, 5), (5, 6), (5, 7), (5, 8), (10, 4), (10, 5), (10, 6), (10, 7), (10, 8), (6, 9), (7, 10), (8, 10), (9, 9)],
    3: [(8, 3), (8, 4), (8, 5), (8, 6), (8, 7), (8, 8), (7, 8), (9, 8), (6, 7), (10, 7), (8, 11), (8, 12)],
    4: [(5, 6), (6, 6), (7, 6), (9, 6), (10, 6), (11, 6), (4, 7), (12, 7), (5, 8), (6, 9), (10, 9), (11, 8), (7, 10), (9, 10)],
}


def trial_chest(kind):
    img = Image.new("RGB", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            n = (x * 5 + y * 11) % 6
            px[x, y] = (84 + n * 3, 86 + n * 3, 94 + n * 3)
            if x in (0, 15) or y in (0, 15):
                px[x, y] = (46, 48, 56)
            elif x in (1, 14) or y in (1, 14):
                px[x, y] = (150, 120, 60)
    for (x, y) in GLYPHS[kind]:
        px[x, y] = (236, 208, 120)
    img.save(ASSETS + "/textures/block/trial_chest_%d.png" % kind)
    put(ASSETS + "/models/block/trial_chest_%d.json" % kind, {"parent": "minecraft:block/cube_all", "textures": {"all": "jaymoskingdoms:block/trial_chest_%d" % kind}})


def track2():
    sketchbook_icon()
    sketch_icon()
    for kind in SHARD_TINTS:
        shard_icon(kind)
    crown_icon()
    stand_textures()
    stand_models()
    for k in range(5):
        trial_chest(k)
    put(ASSETS + "/blockstates/trial_chest.json", {"variants": {"kind=%d" % k: {"model": "jaymoskingdoms:block/trial_chest_%d" % k} for k in range(5)}})
    item_def("trial_chest", "jaymoskingdoms:block/trial_chest_0")
    put(DATA + "/loot_table/blocks/crown_stand.json", {
        "type": "minecraft:block", "random_sequence": "jaymoskingdoms:blocks/crown_stand",
        "pools": [{"rolls": 1, "conditions": [{"condition": "minecraft:survives_explosion"}],
                   "entries": [{"type": "minecraft:item", "name": "jaymoskingdoms:crown_stand"}]}]})
    put(DATA + "/recipe/crown_stand.json", {
        "type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["GAG", " S ", "SSS"],
        "key": {"G": "minecraft:gold_ingot", "A": "minecraft:amethyst_shard", "S": "minecraft:stone_bricks"},
        "result": {"count": 1, "id": "jaymoskingdoms:crown_stand"}})
    put(DATA + "/recipe/sketchbook.json", {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": ["minecraft:paper", "minecraft:paper", "minecraft:paper", "minecraft:charcoal"],
        "result": {"count": 1, "id": "jaymoskingdoms:sketchbook"}})


track2()
