"""Alchemy track C (flux, blight and corruption): textures, models, blockstates, item definitions, loot tables, the
crafting recipe for the Ward Stone and the crucible recipes of the cures.

Textures are painted as pixel grids after docs/Vanilla-Style.md: 16x16 (the effect icon 18x18), no black outlines (the
outline is the darkest shade of what it wraps, lighter on the lit side), light from the top-left, 5-18 colours, no
half-transparent pixels. Blight is a sickly purple-grey; stone, glass and dirt use vanilla-like ramps.

Run from the repository root: python3 -I tools/gen_alchemy_flux.py
Needs only Pillow; reads nothing from the Minecraft jar.
"""
import json
import math
import os
import random

from PIL import Image

ASSETS = "src/main/resources/assets/jaymospowers/"
DATA = "src/main/resources/data/jaymospowers/"
BLOCK_TEX = ASSETS + "textures/block/alchemy/"
ITEM_TEX = ASSETS + "textures/item/alchemy/"
EFFECT_TEX = ASSETS + "textures/mob_effect/"
random.seed(2604)


def hexc(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


# dark to light
BLIGHT = [hexc(c) for c in ("2b2433", "40364d", "574a66", "6f6080", "8a7b9a", "a89bb5", "c9bfd3")]
DIRT = [hexc(c) for c in ("4f3624", "5f4230", "725238", "86603f", "9a7048")]
STONE = [hexc(c) for c in ("4f4f4f", "626262", "747474", "868686", "9a9a9a", "adadad")]
GOO = [hexc(c) for c in ("3d1a58", "5a2682", "7a34ad", "9a52cc", "bf84e6", "e3c4f5")]
WITHER = [hexc(c) for c in ("2f2a36", "453d4e", "5d5368", "776a84", "948698")]
SALT = [hexc(c) for c in ("6f7c93", "9aa7bd", "c3cddc", "e1e8f1", "f5f8fc", "ffffff")]
GLASS = [hexc(c) for c in ("6a9ba5", "86bac6", "a8d0d9", "d0eae9")]
OAK = [hexc(c) for c in ("67502c", "7e6237", "967441", "af8f55")]
INK = [hexc(c) for c in ("171122", "271c3b", "3a2b57", "55427f", "7860a8")]
DRAUGHT = [hexc(c) for c in ("8c7ec4", "ab9fe0", "cbc2f2", "ece8fb", "ffffff")]
GLEAM = hexc("d9b8ff")
RUNE = [hexc(c) for c in ("6b3fa0", "9158d6", "c39bf5", "efe0ff")]
CLEAR = (0, 0, 0, 0)


def new(size=16):
    return Image.new("RGBA", (size, size), CLEAR)


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def noise_fill(img, ramp, centre, spread=True, box=None):
    w, h = img.size
    x0, y0, x1, y1 = box or (0, 0, w, h)
    px = img.load()
    for x in range(x0, x1):
        for y in range(y0, y1):
            i = centre + (random.choice([-1, 0, 0, 0, 1]) if spread else 0)
            px[x, y] = ramp[max(0, min(len(ramp) - 1, i))]


def grid(rows, legend):
    """A sprite from rows of characters ('.' clear)."""
    img = new(len(rows[0]))
    px = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                px[x, y] = legend[ch]
    return img


# ---------------------------------------------------------------- blocks
def blighted_soil_top():
    img = new()
    noise_fill(img, BLIGHT, 3)
    px = img.load()
    # hairline cracks (darker) and a few lilac flecks of flux
    for x, y in ((2, 3), (3, 3), (3, 4), (4, 5), (10, 9), (11, 9), (11, 10), (12, 11), (6, 13), (7, 13), (7, 14)):
        px[x, y] = BLIGHT[1]
    for x, y in ((9, 2), (5, 8), (13, 5), (1, 12), (14, 14)):
        px[x, y] = BLIGHT[6]
    save(img, BLOCK_TEX + "blighted_soil_top.png")


def blighted_soil_side():
    img = new()
    noise_fill(img, DIRT, 2)
    px = img.load()
    # the crust: three rows of blight with a ragged lower edge, lit at the top
    for x in range(16):
        depth = 3 + random.choice([0, 0, 1, 1, 2])
        for y in range(depth):
            px[x, y] = BLIGHT[4 if y == 0 else 3 if y == 1 else 2 if random.random() < 0.6 else 3]
        px[x, depth] = BLIGHT[1] if random.random() < 0.7 else DIRT[0]
    for x, y in ((3, 8), (11, 12), (7, 6)):
        px[x, y] = DIRT[0]
    save(img, BLOCK_TEX + "blighted_soil_side.png")


def blight_growth():
    """A wilted plant: three stems drooping to the right, limp leaves, a closed grey bud."""
    rows = [
        "................",
        "................",
        ".....hh.........",
        "....hHHh....hh..",
        "....hHh....hHHh.",
        ".....h.....hHh..",
        "..mm.......mh...",
        ".mMMm......m....",
        ".mMm..s..s.m....",
        "..m...s.ss.m....",
        "..m...ss.s.m....",
        "..sm..s..s.m.m..",
        "...sm.ss.s.mm...",
        "....sm.s.s.m....",
        ".....sssss.s....",
        "......ssss......",
    ]
    legend = {"h": BLIGHT[5], "H": BLIGHT[4], "m": BLIGHT[3], "M": BLIGHT[2], "s": BLIGHT[1]}
    img = grid(rows, legend)
    px = img.load()
    # a faint lilac mote on the bud
    px[5, 2] = BLIGHT[6]
    px[12, 3] = GLEAM
    save(img, BLOCK_TEX + "blight_growth.png")


def flux_goo():
    img = new()
    noise_fill(img, GOO, 2)
    px = img.load()
    # glossy blobs: lit on the upper left, dark under the lower right
    for cx, cy, r in ((4, 4, 2), (11, 6, 3), (6, 11, 2), (13, 13, 2)):
        for x in range(16):
            for y in range(16):
                d = math.hypot(x - cx, y - cy)
                if d <= r:
                    px[x, y] = GOO[3]
                if d <= r - 1:
                    px[x, y] = GOO[4]
        px[cx - 1, cy - 1] = GOO[5]
    save(img, BLOCK_TEX + "flux_goo.png")


def withered_leaves():
    img = new()
    px = img.load()
    for x in range(16):
        for y in range(16):
            if random.random() < 0.22:
                continue
            i = 2 + random.choice([-2, -1, 0, 0, 1, 2])
            px[x, y] = WITHER[max(0, min(4, i))]
    # a few lifeless brown-grey leaf veins
    for x, y in ((3, 3), (4, 4), (5, 5), (10, 8), (11, 9), (12, 10), (6, 12)):
        px[x, y] = WITHER[0]
    save(img, BLOCK_TEX + "withered_leaves.png")


def ward_stone():
    side = new()
    noise_fill(side, STONE, 3)
    px = side.load()
    for x in range(16):
        px[x, 0] = STONE[5]
        px[x, 15] = STONE[0]
    for y in range(16):
        px[0, y] = STONE[4]
        px[15, y] = STONE[1]
    # a carved diamond ring with a lilac glow, a dot at its centre
    ring = [(7, 3), (8, 3), (6, 4), (9, 4), (5, 5), (10, 5), (4, 6), (11, 6), (3, 7), (12, 7), (3, 8), (12, 8), (4, 9), (11, 9),
            (5, 10), (10, 10), (6, 11), (9, 11), (7, 12), (8, 12)]
    for x, y in ring:
        px[x, y] = RUNE[1]
    for x, y in ((7, 3), (6, 4), (5, 5), (4, 6), (3, 7)):
        px[x, y] = RUNE[2]
    for x, y in ((7, 7), (8, 7), (7, 8), (8, 8)):
        px[x, y] = RUNE[3] if (x, y) == (7, 7) else RUNE[2]
    save(side, BLOCK_TEX + "ward_stone.png")
    top = new()
    noise_fill(top, STONE, 2)
    px = top.load()
    for x in range(16):
        px[x, 0] = STONE[4]
        px[x, 15] = STONE[1]
        px[0, x] = STONE[4]
        px[15, x] = STONE[1]
    for i in range(3, 13):
        for x, y in ((i, 3), (i, 12), (3, i), (12, i)):
            px[x, y] = RUNE[0]
    for x, y in ((4, 4), (11, 4), (4, 11), (11, 11)):
        px[x, y] = RUNE[1]
    for x, y in ((7, 7), (8, 7), (7, 8), (8, 8)):
        px[x, y] = RUNE[2]
    px[7, 7] = RUNE[3]
    save(top, BLOCK_TEX + "ward_stone_top.png")


# ---------------------------------------------------------------- items
BOTTLE_ROWS = {  # row -> half width of the glass body (centre between columns 7 and 8)
    3: 2, 4: 2, 5: 2, 6: 3, 7: 4, 8: 5, 9: 5, 10: 5, 11: 5, 12: 5, 13: 4,
}


def bottle(liquid, fill_from, cork=True):
    img = new()
    px = img.load()
    inside = {}
    for y, hw in BOTTLE_ROWS.items():
        for x in range(8 - hw, 8 + hw):
            inside[(x, y)] = True
    for (x, y) in inside:
        edge = any((x + dx, y + dy) not in inside for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            px[x, y] = GLASS[3] if (x + y) % 2 == 0 and (x < 8 and y < 8) else GLASS[1] if x < 8 and y < 11 else GLASS[0]
        elif y >= fill_from:
            lit = x <= 5
            px[x, y] = liquid[3] if lit else liquid[2] if x < 9 else liquid[1] if y < 12 else liquid[0]
        else:
            px[x, y] = GLASS[3] if (x, y) == (6, 6) else CLEAR
    if cork:
        for y, (a, b) in ((1, (6, 9)), (2, (6, 9))):
            for x in range(a, b + 1):
                px[x, y] = OAK[3] if y == 1 and x < 8 else OAK[2] if x < 8 else OAK[1]
        px[9, 2] = OAK[0]
    # a glint on the glass shoulder
    px[4, 9] = liquid[-1]
    px[4, 10] = liquid[-1]
    return img


def purifying_draught():
    img = bottle(DRAUGHT, 8)
    px = img.load()
    px[10, 9] = GLEAM
    save(img, ITEM_TEX + "purifying_draught.png")


def shadow_ink():
    img = bottle(INK, 8)
    px = img.load()
    px[10, 10] = INK[4]
    px[7, 9] = INK[4]
    save(img, ITEM_TEX + "shadow_ink.png")


def purifying_salts():
    """A little linen pouch of white salt, tied at the neck with violet cord."""
    rows = {2: 1, 3: 1, 4: 2, 5: 3, 6: 4, 7: 5, 8: 5, 9: 5, 10: 5, 11: 5, 12: 4, 13: 3}
    img = new()
    px = img.load()
    inside = {}
    for y, hw in rows.items():
        for x in range(8 - hw, 8 + hw):
            inside[(x, y)] = True
    for (x, y) in inside:
        edge = any((x + dx, y + dy) not in inside for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            px[x, y] = SALT[3] if (x < 8 and y < 9) else SALT[0] if (x >= 8 or y > 11) else SALT[1]
        else:
            i = 4 if x < 7 and y < 9 else 3 if x < 9 else 2
            px[x, y] = SALT[max(0, min(5, i + random.choice([-1, 0, 0, 0])))]
    # the cord and its knot
    for x in range(6, 10):
        px[x, 4] = RUNE[1] if x < 8 else RUNE[0]
    px[7, 3] = RUNE[2]
    px[8, 3] = RUNE[1]
    # spilled grains and a glint
    for x, y in ((3, 14), (5, 14), (10, 14), (12, 13)):
        px[x, y] = SALT[4]
    px[5, 8] = GLEAM
    px[9, 10] = GLEAM
    save(img, ITEM_TEX + "purifying_salts.png")


def flux_wisp():
    """A glowing mote: white core, violet halo, a tail trailing down and to the left (all opaque, drawn fully lit)."""
    img = new()
    px = img.load()
    core = (10.0, 5.5)
    ramp = [hexc("5a1f80"), hexc("8a2bb8"), hexc("b45fe0"), hexc("d9b8ff"), hexc("f4ebff")]
    for x in range(16):
        for y in range(16):
            d = math.hypot(x + 0.5 - core[0], y + 0.5 - core[1])
            if d < 4.2:
                i = 0 if d > 3.4 else 1 if d > 2.5 else 2 if d > 1.6 else 3 if d > 0.8 else 4
                px[x, y] = ramp[i]
    # the tail
    for t, (x, y) in enumerate(((7, 8), (6, 9), (5, 10), (4, 11), (3, 12), (2, 13))):
        px[x, y] = ramp[2 if t < 2 else 1 if t < 4 else 0]
        if t < 3:
            px[x - 1, y] = ramp[1 if t < 2 else 0]
    px[8, 8] = ramp[1]
    px[7, 7] = ramp[2]
    px[13, 9] = ramp[0]
    px[12, 1] = ramp[0]
    save(img, ITEM_TEX + "flux_wisp.png")


def blighted_icon():
    """Mob effect icon (18x18): a sickly purple drop with a lit edge, two spores."""
    img = new(18)
    px = img.load()
    for y in range(2, 17):
        hw = (y - 1.5) * 0.7 if y <= 8 else math.sqrt(max(0.0, 28.0 - (y - 11) ** 2))
        for x in range(18):
            d = x + 0.5 - 9.0
            if abs(d) < hw:
                c = BLIGHT[4]
                if d > hw * 0.35:
                    c = BLIGHT[3]
                if d > hw * 0.7:
                    c = BLIGHT[2]
                if d < -hw * 0.45 and y > 7:
                    c = BLIGHT[5]
                px[x, y] = c
    # a darker edge on the lower right, lit edge on the upper left
    for y in range(18):
        row = [x for x in range(18) if px[x, y][3]]
        if row:
            px[row[0], y] = BLIGHT[5]
            px[row[-1], y] = BLIGHT[1]
    for x in range(18):
        col = [y for y in range(18) if px[x, y][3]]
        if col:
            px[x, col[-1]] = BLIGHT[1]
    px[7, 8] = BLIGHT[6]
    px[7, 9] = BLIGHT[6]
    px[4, 4] = GLEAM
    px[13, 6] = GLEAM
    px[12, 2] = BLIGHT[4]
    save(img, EFFECT_TEX + "blighted.png")


# ---------------------------------------------------------------- json
def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def item_def(name, model):
    write_json(ASSETS + "items/%s.json" % name, {"model": {"type": "minecraft:model", "model": model}})


def blockstate(name, model):
    write_json(ASSETS + "blockstates/%s.json" % name, {"variants": {"": {"model": model}}})


def item_model(name, texture):
    write_json(ASSETS + "models/item/alchemy/%s.json" % name,
               {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymospowers:item/alchemy/" + texture}})


def drops_self(name):
    write_json(DATA + "loot_table/blocks/%s.json" % name, {
        "type": "minecraft:block",
        "random_sequence": "jaymospowers:blocks/" + name,
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "jaymospowers:" + name}]}],
    })


def models_and_data():
    block_models = ASSETS + "models/block/alchemy/"
    write_json(block_models + "blighted_soil.json", {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {
            "top": "jaymospowers:block/alchemy/blighted_soil_top",
            "side": "jaymospowers:block/alchemy/blighted_soil_side",
            "bottom": "minecraft:block/dirt",
        },
    })
    write_json(block_models + "blight_growth.json", {
        "parent": "minecraft:block/cross",
        "textures": {"cross": "jaymospowers:block/alchemy/blight_growth"},
    })
    face = {"texture": "#goo"}
    write_json(block_models + "flux_goo.json", {
        "textures": {"particle": "jaymospowers:block/alchemy/flux_goo", "goo": "jaymospowers:block/alchemy/flux_goo"},
        "elements": [{
            "from": [0, 0, 0], "to": [16, 2, 16],
            "faces": {
                "up": {"texture": "#goo", "uv": [0, 0, 16, 16]},
                "down": {"texture": "#goo", "uv": [0, 0, 16, 16], "cullface": "down"},
                "north": {"texture": "#goo", "uv": [0, 14, 16, 16], "cullface": "north"},
                "south": {"texture": "#goo", "uv": [0, 14, 16, 16], "cullface": "south"},
                "east": {"texture": "#goo", "uv": [0, 14, 16, 16], "cullface": "east"},
                "west": {"texture": "#goo", "uv": [0, 14, 16, 16], "cullface": "west"},
            },
        }],
    })
    write_json(block_models + "withered_leaves.json", {
        "parent": "minecraft:block/leaves",
        "textures": {"all": "jaymospowers:block/alchemy/withered_leaves"},
    })
    write_json(block_models + "ward_stone.json", {
        "parent": "minecraft:block/cube_column",
        "textures": {"end": "jaymospowers:block/alchemy/ward_stone_top", "side": "jaymospowers:block/alchemy/ward_stone"},
    })
    for name in ("blighted_soil", "blight_growth", "flux_goo", "withered_leaves", "ward_stone"):
        blockstate(name, "jaymospowers:block/alchemy/" + name)
    # block items: the soil, leaves and stone show their block; the plant and the puddle a flat icon
    for name in ("blighted_soil", "withered_leaves", "ward_stone"):
        item_def(name, "jaymospowers:block/alchemy/" + name)
    write_json(ASSETS + "models/item/alchemy/blight_growth.json", {
        "parent": "minecraft:item/generated", "textures": {"layer0": "jaymospowers:block/alchemy/blight_growth"}})
    write_json(ASSETS + "models/item/alchemy/flux_goo.json", {
        "parent": "minecraft:item/generated", "textures": {"layer0": "jaymospowers:block/alchemy/flux_goo"}})
    item_def("blight_growth", "jaymospowers:item/alchemy/blight_growth")
    item_def("flux_goo", "jaymospowers:item/alchemy/flux_goo")
    for name in ("purifying_salts", "purifying_draught", "shadow_ink", "flux_wisp"):
        item_model(name, name)
        item_def(name, "jaymospowers:item/alchemy/" + name)
    for name in ("blighted_soil", "blight_growth", "ward_stone"):
        drops_self(name)
    # leaves: a stick now and then
    write_json(DATA + "loot_table/blocks/withered_leaves.json", {
        "type": "minecraft:block",
        "random_sequence": "jaymospowers:blocks/withered_leaves",
        "pools": [{
            "rolls": 1,
            "conditions": [{"condition": "minecraft:random_chance", "chance": 0.25}],
            "entries": [{"type": "minecraft:item", "name": "minecraft:stick"}],
        }],
    })

    # Ward Stone: stone bricks round a pouch of Purifying Salts and an orichalc ingot
    write_json(DATA + "recipe/ward_stone.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "key": {
            "B": "minecraft:stone_bricks",
            "S": "jaymospowers:purifying_salts",
            "O": "jaymospowers:orichalc_ingot",
        },
        "pattern": ["BSB", "BOB", "BBB"],
        "result": {"id": "jaymospowers:ward_stone", "count": 1},
    })
    # crucible recipes (format: Crucibles.Recipe)
    crucible = DATA + "alchemy/crucible/"
    write_json(crucible + "purifying_salts.json", {
        "catalyst": "jaymospowers:alchemical_salt",
        "essences": {"salt": 6, "light": 4},
        "result": {"item": "jaymospowers:purifying_salts", "count": 2},
        "research": "cleansing",
    })
    write_json(crucible + "purifying_draught.json", {
        "catalyst": "minecraft:glass_bottle",
        "essences": {"salt": 4, "life": 4, "water": 4},
        "result": {"item": "jaymospowers:purifying_draught", "count": 1},
        "research": "cleansing",
    })
    write_json(crucible + "shadow_ink.json", {
        "catalyst": "minecraft:ink_sac",
        "essences": {"shadow": 6, "mind": 3, "flux": 3},
        "result": {"item": "jaymospowers:shadow_ink", "count": 1},
        "research": "whispers",
    })


if __name__ == "__main__":
    blighted_soil_top()
    blighted_soil_side()
    blight_growth()
    flux_goo()
    withered_leaves()
    ward_stone()
    purifying_draught()
    shadow_ink()
    purifying_salts()
    flux_wisp()
    blighted_icon()
    models_and_data()
    print("done")
