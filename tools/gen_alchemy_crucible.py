"""Alchemy track B (crucible, alembic, essence jars, the things a catalyst makes): textures, models, blockstates, item
definitions, loot tables and crafting recipes.

Textures are painted as pixel grids after docs/Vanilla-Style.md: 16x16, no black outlines (the outline is the darkest
shade of what it wraps, one step lighter on the lit side), light from the top-left, vanilla's material ramps for iron,
copper, glass and oak. The orichalc ingot and nugget start from vanilla's iron ingot and nugget pixels, recoloured.
They are read from the Minecraft jar: pass its path (or an extracted `assets/minecraft/textures` folder) as the one
argument; the default is the jar the cloud tools use.

Run from the repository root: python3 -I tools/gen_alchemy_crucible.py [26.3.jar | textures-folder]
The catalyst recipes (data/jaymospowers/alchemy/crucible/*.json) are written here too.
"""
import io
import json
import math
import os
import random
import sys
import zipfile

from PIL import Image

ASSETS = "src/main/resources/assets/jaymospowers/"
DATA = "src/main/resources/data/jaymospowers/"
BLOCK_TEX = ASSETS + "textures/block/alchemy/"
ITEM_TEX = ASSETS + "textures/item/alchemy/"
random.seed(2603)


def hexc(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


# vanilla ramps (dark to light)
COPPER = [hexc(c) for c in ("6d3421", "9c4529", "9c4e31", "c15a36", "e77c56", "fc9982", "fbc3b6")]
IRON = [hexc(c) for c in ("353535", "5e5e5e", "727272", "828282", "a8a8a8", "d8d8d8", "ffffff")]
GLASS = [hexc(c) for c in ("7baeb7", "8bc1cd", "a8d0d9", "d0eae9")]
OAK = [hexc(c) for c in ("67502c", "7e6237", "967441", "9f844d", "af8f55", "b8945f", "c29d62")]
# our own: orichalc is a rose gold with a lilac gleam; mercury silver-blue; sulphur yellow; salt cool white
ORICHALC = [hexc(c) for c in ("6b3340", "8f4a52", "b3665c", "d18a72", "ecae92", "f9d0b8", "fff0e2")]
MERCURY = [hexc(c) for c in ("4b5663", "6c7a89", "93a1b0", "bcc8d4", "dde5ec", "f6f9fb")]
SULPHUR = [hexc(c) for c in ("6b560f", "9a7f14", "c4a91f", "e3cb38", "f4e66a", "fff7ad")]
SALT = [hexc(c) for c in ("6f7c93", "9aa7bd", "c3cddc", "e1e8f1", "f5f8fc", "ffffff")]
GLEAM = hexc("d9b8ff")
CLEAR = (0, 0, 0, 0)


def new():
    return Image.new("RGBA", (16, 16), CLEAR)


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def noise_fill(img, ramp, centre, spread, box=(0, 0, 16, 16)):
    """Fills a box with a ramp colour, mostly the centre step, sometimes a neighbour."""
    x0, y0, x1, y1 = box
    px = img.load()
    for x in range(x0, x1):
        for y in range(y0, y1):
            i = centre + (random.choice([-1, 0, 0, 0, 1]) if spread else 0)
            px[x, y] = ramp[max(0, min(len(ramp) - 1, i))]


def bevel(img, ramp, lit, dark, box=(0, 0, 16, 16)):
    x0, y0, x1, y1 = box
    px = img.load()
    for x in range(x0, x1):
        px[x, y0] = ramp[lit]
        px[x, y1 - 1] = ramp[dark]
    for y in range(y0, y1):
        px[x0, y] = ramp[lit]
        px[x1 - 1, y] = ramp[dark]
    px[x0, y1 - 1] = ramp[dark]
    px[x1 - 1, y0] = ramp[lit]


# ---------------------------------------------------------------- block textures
def crucible_body():
    img = new()
    noise_fill(img, COPPER, 3, True)
    px = img.load()
    for x in range(16):
        # two plate seams, each with a light line under it (light from above)
        for y, c in ((5, 1), (11, 1)):
            px[x, y] = COPPER[c]
            px[x, y + 1] = COPPER[4]
    # rivets
    for x in (2, 6, 10, 14):
        for y in (3, 9, 14):
            px[x, y] = COPPER[5]
            if y + 1 < 16:
                px[x, y + 1] = COPPER[1]
    # verdigris creeping up from the bottom (a few cool pixels)
    for x, y in ((3, 15), (4, 15), (4, 14), (11, 15), (12, 15), (12, 14), (13, 15)):
        px[x, y] = hexc("4f8f7c") if (x + y) % 2 else hexc("3e7564")
    save(img, BLOCK_TEX + "crucible_body.png")


def crucible_rim():
    img = new()
    noise_fill(img, IRON, 2, True)
    px = img.load()
    for x in range(16):
        px[x, 0] = IRON[4]
        px[x, 1] = IRON[3]
        px[x, 14] = IRON[1]
        px[x, 15] = IRON[0]
    for x in (3, 11):
        px[x, 7] = IRON[5]
        px[x, 8] = IRON[1]
    save(img, BLOCK_TEX + "crucible_rim.png")


def crucible_inner():
    img = new()
    noise_fill(img, COPPER, 1, True)
    px = img.load()
    for x in range(16):
        px[x, 0] = COPPER[0]
    save(img, BLOCK_TEX + "crucible_inner.png")


def alembic_copper():
    img = new()
    noise_fill(img, COPPER, 3, True)
    bevel(img, COPPER, 4, 1)
    save(img, BLOCK_TEX + "alembic_copper.png")


def glass(path, tint=0):
    """Glass like vanilla's: a frame, a clear middle, a diagonal glint."""
    img = new()
    px = img.load()
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            px[x, y] = GLASS[1 + (x + y) % 2] if tint == 0 else GLASS[(x + y) % 3]
    for x, y in ((2, 2), (3, 3), (2, 3), (3, 2), (4, 5), (5, 4)):
        px[x, y] = GLASS[3]
    px[12, 13] = GLASS[0]
    px[13, 12] = GLASS[0]
    save(img, path)


def jar_lid():
    img = new()
    noise_fill(img, OAK, 4, True)
    px = img.load()
    for x in range(16):
        px[x, 0] = OAK[6]
        px[x, 15] = OAK[1]
    for y in range(16):
        px[0, y] = OAK[5]
        px[15, y] = OAK[2]
    save(img, BLOCK_TEX + "jar_lid.png")


def liquid():
    """Grey ripples; tinted at render time by whatever is in the vessel."""
    img = new()
    px = img.load()
    for x in range(16):
        for y in range(16):
            w = math.sin((x + 2 * math.sin(y / 3.0)) * 0.9) + math.sin(y * 0.8 + x * 0.3)
            v = 0.78 + 0.1 * w / 2 + random.uniform(-0.02, 0.02)
            g = max(0, min(255, int(255 * v)))
            px[x, y] = (g, g, g, 255)
    save(img, BLOCK_TEX + "liquid.png")


def orichalc_block():
    img = new()
    noise_fill(img, ORICHALC, 3, True)
    px = img.load()
    # plates: a cross of seams
    for i in range(16):
        px[i, 7] = ORICHALC[1]
        px[i, 8] = ORICHALC[4]
        px[7, i] = ORICHALC[1]
        px[8, i] = ORICHALC[4]
    bevel(img, ORICHALC, 5, 1)
    for x, y in ((3, 3), (11, 4), (4, 11), (12, 12), (9, 2), (2, 9)):
        px[x, y] = GLEAM
    save(img, BLOCK_TEX + "orichalc_block.png")


# ---------------------------------------------------------------- the flame (block and item)
def flame(path):
    img = new()
    px = img.load()
    outer, mid, core, white = hexc("dc9613"), hexc("f8d26a"), hexc("fff3b0"), hexc("ffffff")
    dark = hexc("a8650c")
    for y in range(2, 15):
        t = (y - 2) / 12.0
        half = 0.4 + 4.6 * math.sin(min(1.0, t * 1.15) * math.pi * 0.62) ** 1.3
        for x in range(16):
            d = abs(x + 0.5 - 8.0 + (0.6 if y < 8 else 0.0))
            if d < half:
                c = outer
                if d < half * 0.72 and y > 4:
                    c = mid
                if d < half * 0.4 and y > 7:
                    c = core
                if d < half * 0.18 and y > 10:
                    c = white
                px[x, y] = c
    # darker foot and edge, lit edge on the left
    for y in range(2, 15):
        row = [x for x in range(16) if px[x, y][3]]
        if row:
            px[row[0], y] = outer if y < 12 else dark
            px[row[-1], y] = dark if y > 5 else outer
    for x in range(16):
        if px[x, 14][3]:
            px[x, 14] = dark
    # a violet spark: this is not a plain fire
    px[11, 3] = GLEAM
    px[4, 6] = GLEAM
    save(img, path)


# ---------------------------------------------------------------- items
def load_vanilla(source):
    def read(name):
        if os.path.isdir(source):
            return Image.open(os.path.join(source, name + ".png")).convert("RGBA")
        with zipfile.ZipFile(source) as z:
            return Image.open(io.BytesIO(z.read("assets/minecraft/textures/" + name + ".png"))).convert("RGBA")
    return read


def lum(c):
    return 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2]


def recolor(img, ramp):
    """Maps the colours of a sprite, darkest to lightest, onto a ramp, keeping its shading."""
    px = img.load()
    colours = sorted({px[x, y] for x in range(16) for y in range(16) if px[x, y][3]}, key=lum)
    out = {}
    for i, c in enumerate(colours):
        k = round(i * (len(ramp) - 1) / max(1, len(colours) - 1))
        out[c] = ramp[k]
    res = new()
    rp = res.load()
    for x in range(16):
        for y in range(16):
            if px[x, y][3]:
                rp[x, y] = out[px[x, y]]
    return res


def blob(img, ramp, cx, cy, rx, ry, tip=0, lit=(-0.45, -0.55)):
    """A shaded blob (a drop when tip > 0 pulls its top to a point). Outline = darkest step, lit side one lighter."""
    px = img.load()
    cells = {}
    for x in range(16):
        for y in range(16):
            dx, dy = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
            top = -dy if dy < 0 else 0
            inside = dx * dx + dy * dy <= 1.0
            if tip and dy < 0:
                w = max(0.0, 1.0 + dy * (1.0 + tip))
                inside = abs(dx) <= w * 0.9 and dy > -1.0 - tip * 0.9 and (dx * dx + dy * dy <= 1.0 or abs(dx) <= w * 0.5)
            if inside:
                n = max(-1.0, min(1.0, (dx * lit[0] + dy * lit[1]) * -1.6))
                cells[(x, y)] = n
    for (x, y), n in cells.items():
        edge = any((x + a, y + b) not in cells for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        k = round((n + 1) / 2 * (len(ramp) - 3)) + 1
        if edge:
            lit_side = (x + 0.5 - cx) * lit[0] + (y + 0.5 - cy) * lit[1] > 0
            k = 1 if lit_side else 0
        px[x, y] = ramp[max(0, min(len(ramp) - 1, k))]
    return cells


def glint(img, ramp, x, y):
    px = img.load()
    px[x, y] = ramp[-1]


QUICKSILVER = [
    "................",
    ".......ba.......",
    "......bdda......",
    "......bdda......",
    ".....beddda.....",
    ".....beddda.....",
    "....bedddcca....",
    "....bfeddcca....",
    "...bfeddddcca...",
    "...bedddddcca...",
    "...bdddddccca...",
    "...bdddddccca...",
    "....bdddcccca...",
    ".....bccccca....",
    "......aaaaa.....",
    "................",
]


def quicksilver():
    """A drop of mercury, drawn by hand: a=darkest outline, b=lit outline, c=shade, d=body, e=light, f=glint."""
    img = new()
    px = img.load()
    key = {"a": MERCURY[0], "b": MERCURY[1], "c": MERCURY[2], "d": MERCURY[3], "e": MERCURY[4], "f": MERCURY[5]}
    for y, row in enumerate(QUICKSILVER):
        assert len(row) == 16, row
        for x, ch in enumerate(row):
            if ch != ".":
                px[x, y] = key[ch]
    save(img, ITEM_TEX + "quicksilver.png")


def chunk(img, ramp, x, y, w, h):
    """A little crystal block: lit top-left edges, shaded bottom-right."""
    px = img.load()
    for i in range(w):
        for j in range(h):
            k = 3
            if i == 0 or j == 0:
                k = 4
            if i == w - 1 or j == h - 1:
                k = 1
            if i == w - 1 and j == h - 1:
                k = 0
            if (i, j) == (1, 1):
                k = len(ramp) - 1
            px[x + i, y + j] = ramp[min(k, len(ramp) - 1)]


def brimstone():
    img = new()
    px = img.load()
    for cx, cy, rx, ry, tilt in ((6.0, 10.5, 3.8, 3.2, -0.3), (10.6, 9.2, 3.3, 3.0, -0.5), (8.5, 6.4, 2.9, 2.7, -0.4)):
        blob(img, SULPHUR, cx, cy, rx, ry, lit=(tilt, -0.6))
    # angular facets: a lighter and a darker pixel line across the lumps
    for x, y in ((5, 9), (6, 10), (9, 8), (10, 9), (8, 5)):
        px[x, y] = SULPHUR[4]
    for x, y in ((7, 12), (11, 11), (9, 8)):
        px[x, y] = SULPHUR[1]
    save(img, ITEM_TEX + "brimstone.png")


def salt():
    img = new()
    chunk(img, SALT, 3, 9, 5, 4)
    chunk(img, SALT, 8, 8, 5, 5)
    chunk(img, SALT, 6, 4, 4, 5)
    chunk(img, SALT, 10, 5, 2, 3)
    save(img, ITEM_TEX + "alchemical_salt.png")


def orichalc_items(read):
    ingot = recolor(read("item/iron_ingot"), ORICHALC)
    nugget = recolor(read("item/iron_nugget"), ORICHALC)
    for img in (ingot, nugget):
        px = img.load()
        spots = [(x, y) for x in range(16) for y in range(16) if px[x, y][3] and px[x, y] in (ORICHALC[4], ORICHALC[5])]
        random.shuffle(spots)
        for x, y in spots[:2]:
            px[x, y] = GLEAM
    save(ingot, ITEM_TEX + "orichalc_ingot.png")
    save(nugget, ITEM_TEX + "orichalc_nugget.png")


# ---------------------------------------------------------------- models, states, items, loot, recipes
def put(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def box(frm, to, faces, tex):
    """A model element; `faces` is a string of face letters (n s e w u d), each given the texture `tex` (a dict by letter or one name)."""
    names = {"n": "north", "s": "south", "e": "east", "w": "west", "u": "up", "d": "down"}
    el = {"from": frm, "to": to, "faces": {}}
    for f in faces:
        t = tex[f] if isinstance(tex, dict) else tex
        el["faces"][names[f]] = {"texture": "#" + t}
    return el


def crucible_model():
    elements = []
    # floor: inside up, copper under
    elements.append(box([3, 2, 3], [13, 4, 13], "ud", {"u": "inner", "d": "body"}))
    # walls: outside body, inside inner (top covered by the rim)
    elements.append(box([2, 2, 2], [14, 13, 3], "ns", {"n": "body", "s": "inner"}))
    elements.append(box([2, 2, 13], [14, 13, 14], "ns", {"n": "inner", "s": "body"}))
    elements.append(box([2, 2, 3], [3, 13, 13], "ew", {"w": "body", "e": "inner"}))
    elements.append(box([13, 2, 3], [14, 13, 13], "ew", {"w": "inner", "e": "body"}))
    # the iron rim
    for frm, to in (([1, 13, 1], [15, 15, 3]), ([1, 13, 13], [15, 15, 15]), ([1, 13, 3], [3, 15, 13]), ([13, 13, 3], [15, 15, 13])):
        elements.append(box(frm, to, "nsewud", "rim"))
    # four iron feet
    for x in (2, 12):
        for z in (2, 12):
            elements.append(box([x, 0, z], [x + 2, 2, z + 2], "nsewud", "rim"))
    put(ASSETS + "models/block/alchemy/crucible.json", {
        "parent": "minecraft:block/block",
        "textures": {"particle": "jaymospowers:block/alchemy/crucible_body", "body": "jaymospowers:block/alchemy/crucible_body",
                     "rim": "jaymospowers:block/alchemy/crucible_rim", "inner": "jaymospowers:block/alchemy/crucible_inner"},
        "elements": elements})


def alembic_model():
    elements = [
        box([4, -1, 4], [12, 0, 12], "nsewud", "cu"),
        box([3, 0, 3], [13, 2, 13], "nsewud", "cu"),
        box([4, 2, 4], [12, 9, 12], "nsewud", "glass"),
        box([5, 9, 5], [11, 11, 11], "nsewud", "cu"),
        box([6, 11, 6], [10, 14, 10], "nsewud", "glass"),
        box([6, 14, 6], [10, 15, 10], "nsewud", "cu"),
        box([7, 10, 2], [9, 12, 6], "nsewud", "cu"),
        box([7, 10, 10], [9, 12, 14], "nsewud", "cu"),
        box([2, 10, 7], [6, 12, 9], "nsewud", "cu"),
        box([10, 10, 7], [14, 12, 9], "nsewud", "cu"),
    ]
    put(ASSETS + "models/block/alchemy/alembic.json", {
        "parent": "minecraft:block/block",
        "textures": {"particle": "jaymospowers:block/alchemy/alembic_copper", "cu": "jaymospowers:block/alchemy/alembic_copper",
                     "glass": "jaymospowers:block/alchemy/alembic_glass"},
        "elements": elements})


def jar_model():
    elements = [
        box([3, 0, 3], [13, 12, 13], "nsewud", "glass"),
        box([4, 12, 4], [12, 14, 12], "nsewud", "lid"),
    ]
    put(ASSETS + "models/block/alchemy/essence_jar.json", {
        "parent": "minecraft:block/block",
        "textures": {"particle": "jaymospowers:block/alchemy/jar_lid", "glass": "jaymospowers:block/alchemy/jar_glass",
                     "lid": "jaymospowers:block/alchemy/jar_lid"},
        "elements": elements})


def everflame_model():
    put(ASSETS + "models/block/alchemy/everflame.json", {
        "textures": {"particle": "jaymospowers:block/alchemy/everflame", "flame": "jaymospowers:block/alchemy/everflame"},
        "elements": [
            {"from": [3, 2, 8], "to": [13, 14, 8], "shade": False,
             "faces": {"north": {"texture": "#flame"}, "south": {"texture": "#flame"}}},
            {"from": [8, 2, 3], "to": [8, 14, 13], "shade": False,
             "faces": {"east": {"texture": "#flame"}, "west": {"texture": "#flame"}}},
        ]})


def orichalc_block_model():
    put(ASSETS + "models/block/alchemy/orichalc_block.json", {
        "parent": "minecraft:block/cube_all", "textures": {"all": "jaymospowers:block/alchemy/orichalc_block"}})


def item_models():
    for name in ("orichalc_ingot", "orichalc_nugget", "quicksilver", "brimstone", "alchemical_salt"):
        put(ASSETS + "models/item/alchemy/%s.json" % name,
            {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymospowers:item/alchemy/" + name}})
        put(ASSETS + "items/%s.json" % name, {"model": {"type": "minecraft:model", "model": "jaymospowers:item/alchemy/" + name}})
    # the Everflame in the hand is its flame sprite
    put(ASSETS + "models/item/alchemy/everflame.json",
        {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymospowers:item/alchemy/everflame"}})
    put(ASSETS + "items/everflame.json", {"model": {"type": "minecraft:model", "model": "jaymospowers:item/alchemy/everflame"}})
    for name in ("crucible", "alembic", "essence_jar", "orichalc_block"):
        put(ASSETS + "items/%s.json" % name, {"model": {"type": "minecraft:model", "model": "jaymospowers:block/alchemy/" + name}})


def blockstates():
    for name in ("crucible", "alembic", "essence_jar", "orichalc_block", "everflame"):
        put(ASSETS + "blockstates/%s.json" % name, {"variants": {"": {"model": "jaymospowers:block/alchemy/" + name}}})


def loot():
    for name in ("crucible", "alembic", "orichalc_block", "everflame"):
        put(DATA + "loot_table/blocks/%s.json" % name, {
            "type": "minecraft:block", "random_sequence": "jaymospowers:blocks/" + name,
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "jaymospowers:" + name}]}]})
    # the jar keeps what is in it on the dropped item
    put(DATA + "loot_table/blocks/essence_jar.json", {
        "type": "minecraft:block", "random_sequence": "jaymospowers:blocks/essence_jar",
        "pools": [{"rolls": 1, "entries": [{
            "type": "minecraft:item", "name": "jaymospowers:essence_jar",
            "modifier": {"type": "minecraft:copy_components", "include": ["jaymospowers:essences"], "source": "block_entity"}}]}]})


def recipes():
    d = DATA + "recipe/"

    def shaped(name, pattern, key, result, count=1, category="misc"):
        put(d + name + ".json", {"type": "minecraft:crafting_shaped", "category": category, "key": key, "pattern": pattern,
                                 "result": {"id": "jaymospowers:" + result, "count": count}})

    def shapeless(name, ingredients, result, count=1):
        put(d + name + ".json", {"type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": ingredients,
                                 "result": {"id": "jaymospowers:" + result, "count": count}})

    shaped("crucible", ["I I", "C C", "CCC"], {"I": "minecraft:iron_ingot", "C": "minecraft:copper_ingot"}, "crucible")
    shaped("alembic", ["CGC", "G G", "CCC"], {"C": "minecraft:copper_ingot", "G": "minecraft:glass"}, "alembic")
    shaped("essence_jar", [" S ", "G G", "GGG"], {"S": "#minecraft:wooden_slabs", "G": "minecraft:glass"}, "essence_jar")
    shaped("orichalc_ingot_from_nuggets", ["NNN", "NNN", "NNN"], {"N": "jaymospowers:orichalc_nugget"}, "orichalc_ingot")
    shapeless("orichalc_nugget_from_ingot", ["jaymospowers:orichalc_ingot"], "orichalc_nugget", 9)
    shaped("orichalc_block", ["III", "III", "III"], {"I": "jaymospowers:orichalc_ingot"}, "orichalc_block")
    shapeless("orichalc_ingot_from_block", ["jaymospowers:orichalc_block"], "orichalc_ingot", 9)


def catalysts():
    d = DATA + "alchemy/crucible/"

    def recipe(name, catalyst, essences, result, count, research):
        put(d + name + ".json", {"catalyst": catalyst, "essences": essences, "result": {"item": result, "count": count}, "research": research})

    recipe("orichalc_ingot", "minecraft:iron_ingot", {"metal": 4, "arcana": 2}, "jaymospowers:orichalc_ingot", 1, "orichalc")
    recipe("quicksilver", "minecraft:iron_nugget", {"mercury": 6}, "jaymospowers:quicksilver", 1, "principles")
    recipe("brimstone", "minecraft:gunpowder", {"sulphur": 6}, "jaymospowers:brimstone", 1, "principles")
    recipe("alchemical_salt", "minecraft:sugar", {"salt": 6}, "jaymospowers:alchemical_salt", 1, "principles")
    recipe("everflame", "minecraft:glowstone_dust", {"light": 4, "fire": 2}, "jaymospowers:everflame", 1, "everflame")
    recipe("transmute_gold_nugget", "minecraft:iron_nugget", {"wealth": 2}, "minecraft:gold_nugget", 1, "transmutation")
    recipe("transmute_iron_ingot", "minecraft:copper_ingot", {"metal": 2, "mercury": 2}, "minecraft:iron_ingot", 1, "transmutation")
    for crop in ("wheat_seeds", "beetroot_seeds", "melon_seeds", "pumpkin_seeds", "carrot", "potato"):
        recipe("double_" + crop, "minecraft:" + crop, {"plant": 4, "life": 2}, "minecraft:" + crop, 2, "transmutation")


def main():
    source = sys.argv[1] if len(sys.argv) > 1 else "/home/user/cc21/jars/26.3.jar"
    read = load_vanilla(source)
    crucible_body()
    crucible_rim()
    crucible_inner()
    alembic_copper()
    glass(BLOCK_TEX + "alembic_glass.png", 0)
    glass(BLOCK_TEX + "jar_glass.png", 1)
    jar_lid()
    liquid()
    orichalc_block()
    flame(BLOCK_TEX + "everflame.png")
    flame(ITEM_TEX + "everflame.png")
    quicksilver()
    brimstone()
    salt()
    orichalc_items(read)
    crucible_model()
    alembic_model()
    jar_model()
    everflame_model()
    orichalc_block_model()
    item_models()
    blockstates()
    loot()
    recipes()
    catalysts()


main()
