"""Writes the Aether II structures' art and data (Powers 0.8.x, Aether II phase 4 track S): the vases, the animal stash, the outpost and
ambrosium campfires, the prayer candle, guardian wood and the faded holystone pillar and keystone (textures painted here in vanilla
style, our own designs; models, blockstates, item definitions, loot tables, recipes, tags), the structures' loot tables, their
worldgen data (structure, structure set, biome tags) and the names in lang/parts/aether_structures.json.

    python -I tools/gen_aether_structures.py

Needs only Pillow. Nothing is copied from The Aether II or anyone else: the notes gave behaviour, numbers and loot odds, the pictures
are ours. Every texture is seeded from its own name, so a rerun changes nothing.

TRACK_G: the loot tables name some Aether gear (zanite and holystone pickaxes, gliders) that track G adds. Until it is merged this
is False and those entries are vanilla stand-ins (see GEAR below); set it True, rerun, and the real items go in.
"""
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_aether as ga  # painters and blockstate helpers (only imported: it writes nothing until main())
from gen_aether_mobs import write, save

ASSETS = ga.ASSETS
DATA = ga.DATA
MCDATA = ga.MCDATA
NS = ga.NS
SUB = ga.SUB
BLOCK_DIR = os.path.join(ASSETS, "textures", "block", SUB)

tex = ga.tex
jp = ga.jp
mc = ga.mc
hx = ga.hx
ramp = ga.ramp
seeded = ga.seeded
mdl = lambda name: "%s:block/%s/%s" % (NS, SUB, name)

TRACK_G = False

# ---------------------------------------------------------------- the blocks

VASES = ["holystone_vase", "veradexian_vase", "brexallen_vase"]
WOODS = ["guardian_log", "guardian_wood", "stripped_guardian_log", "stripped_guardian_wood", "infected_guardian_log", "infected_guardian_wood"]
CUBES = ["guardian_planks", "guardian_roots", "lucent_guardian_roots", "guardian_lamp", "faded_holystone_keystone"]
ALL_BLOCKS = (VASES + ["animal_stash", "outpost_campfire", "ambrosium_campfire", "prayer_candle"] + WOODS + ["guardian_planks", "guardian_stairs", "guardian_slab",
              "guardian_roots", "lucent_guardian_roots", "guardian_lamp", "faded_holystone_pillar", "faded_holystone_keystone"])

NAMES = {
    "holystone_vase": "Holystone Vase", "veradexian_vase": "Veradexian Vase", "brexallen_vase": "Brexallen Vase", "animal_stash": "Animal Stash",
    "outpost_campfire": "Outpost Campfire", "ambrosium_campfire": "Ambrosium Campfire", "prayer_candle": "Prayer Candle",
    "guardian_log": "Guardian Log", "guardian_wood": "Guardian Wood", "stripped_guardian_log": "Stripped Guardian Log",
    "stripped_guardian_wood": "Stripped Guardian Wood", "infected_guardian_log": "Infected Guardian Log", "infected_guardian_wood": "Infected Guardian Wood",
    "guardian_planks": "Guardian Planks", "guardian_stairs": "Guardian Stairs", "guardian_slab": "Guardian Slab", "guardian_roots": "Guardian Roots",
    "lucent_guardian_roots": "Lucent Guardian Roots", "guardian_lamp": "Guardian Lamp", "faded_holystone_pillar": "Faded Holystone Pillar",
    "faded_holystone_keystone": "Faded Holystone Keystone",
}

# ---------------------------------------------------------------- painting

new = ga.new
FADED = ga.FADED
FADED_DARK = hx("9aa1ad")
GUARD_BARK = ramp("5a4630", "4c3a28", "684f36", "3e2f20")          # guardian wood: dark warm brown
GUARD_RING = ramp("c79a5a", "b38848", "a07638")
GUARD_STRIP = ramp("c28a4a", "b27c3f", "d19b5c", "a06c30")
GUARD_PLANK = ramp("c8924f", "b98644", "d7a561", "a87638")
GUARD_SEAM = hx("7a5428")
INFECTED = ramp("4a5a2e", "3c4a24", "5c6e38", "30391c")            # infected: sickly green-brown
INFECTED_RING = ramp("9aaa4e", "889a3e", "6c7c30")


def vase_texture(kind):
    """A pot: a round body shaded left to right, a band of pattern round the belly and one at the neck."""
    palettes = {
        "holystone": (ramp("dde1e8", "eceff3", "c9ced8", "aeb5c1"), hx("6f86ad"), hx("3f5074")),
        "veradexian": (ramp("e8dcc0", "f0e6cc", "d6c8a4", "bfae86"), hx("d9a93a"), hx("5a4a2a")),
        "brexallen": (ramp("8a6847", "9b7753", "7a5a3c", "66492f"), hx("9a7fc2"), hx("3c2d52")),
    }
    base, band, line = palettes[kind]
    rng = seeded(kind + "_vase")
    im = new()
    px = im.load()
    for y in range(16):
        for x in range(16):
            shade = 1 if x < 4 else 0 if x < 10 else 2 if x < 14 else 3
            c = base[shade] if rng.random() < 0.8 else base[rng.randrange(4)]
            px[x, y] = c + (255,)
    for x in range(16):
        for y in (4, 11):
            px[x, y] = line + (255,)
        for y in (5, 10):
            px[x, y] = band + (255,)
        px[x, 6 + (x % 4 in (1, 2))] = band + (255,)
        px[x, 8 - (x % 4 in (1, 2))] = band + (255,)
    return im


def weave(name, light, dark, rim):
    """Woven sticks: two directions of stick, over and under."""
    rng = seeded(name)
    im = new()
    px = im.load()
    for y in range(16):
        for x in range(16):
            horizontal = ((x // 4) + (y // 4)) % 2 == 0
            c = light[rng.randrange(len(light))] if horizontal else dark[rng.randrange(len(dark))]
            if (horizontal and y % 4 == 3) or (not horizontal and x % 4 == 3):
                c = rim
            px[x, y] = c + (255,)
    return im


def log_plain(name, colors, seam=None):
    return ga.bark(name, colors)


def flame(name, inner, mid, outer):
    """A flame: a tongue from the bottom, hottest in the middle, on a clear tile."""
    im = new()
    px = im.load()
    rows = [(14, 4), (13, 5), (12, 6), (11, 6), (10, 7), (9, 7), (8, 8), (7, 8), (6, 9), (5, 9), (4, 10), (3, 10), (2, 11), (1, 12)]
    for i, (y, half) in enumerate(rows):
        half = 3 + (14 - y) * 0 + (1 if y > 6 else 0) + (1 if y > 9 else 0) if y > 2 else 2
        for x in range(8 - half, 8 + half):
            if 0 <= x < 16:
                d = abs(x - 7.5) / half
                c = inner if d < 0.3 and y > 5 else mid if d < 0.65 else outer
                px[x, y] = c + (255,)
    for y in (15, 1, 0):
        pass
    px[7, 1] = outer + (255,)
    px[8, 2] = outer + (255,)
    px[8, 0] = (0, 0, 0, 0)
    return im


def fire_cutout(name, inner, mid, outer):
    rng = seeded(name)
    im = new()
    px = im.load()
    for y in range(16):
        width = max(0.0, 6.5 - abs(y - 11) * (1.1 if y < 11 else 1.6))
        for x in range(16):
            d = abs(x - 7.5)
            if d <= width + (rng.random() - 0.5) * 1.2:
                t = d / max(width, 1.0)
                c = inner if t < 0.3 and 6 < y < 14 else mid if t < 0.65 else outer
                px[x, y] = c + (255,)
    return im


def pillar_side():
    rng = seeded("faded_holystone_pillar")
    im = new()
    px = im.load()
    for x in range(16):
        for y in range(16):
            if x in (0, 15):
                c = FADED_DARK
            elif x in (1, 14):
                c = FADED[2]
            elif x in (4, 5, 10, 11):
                c = FADED[0] if rng.random() < 0.8 else FADED[3]
            else:
                c = FADED[1] if rng.random() < 0.8 else FADED[0]
            px[x, y] = c + (255,)
    for y in (0, 15):
        for x in range(16):
            px[x, y] = (FADED_DARK if rng.random() < 0.6 else FADED[3]) + (255,)
    return im


def pillar_top():
    rng = seeded("faded_holystone_pillar_top")
    im = new()
    px = im.load()
    for y in range(16):
        for x in range(16):
            d = min(x, y, 15 - x, 15 - y)
            c = FADED_DARK if d == 0 else FADED[2] if d in (1, 2) else FADED[3] if d == 3 else FADED[0] if rng.random() < 0.6 else FADED[1]
            px[x, y] = c + (255,)
    return im


def keystone():
    rng = seeded("faded_holystone_keystone")
    im = ga.speckle(rng, FADED, [5, 3, 2, 3])
    px = im.load()
    for y in range(16):
        for x in range(16):
            d = min(x, y, 15 - x, 15 - y)
            if d == 1:
                px[x, y] = FADED_DARK + (255,)
            elif d == 2:
                px[x, y] = FADED[2] + (255,)
            dd = abs(x - 7.5) + abs(y - 7.5)
            if 5.5 <= dd <= 6.5:
                px[x, y] = FADED_DARK + (255,)
            elif 2.5 < dd < 5.5:
                px[x, y] = (FADED[3] if (x + y) % 2 else FADED[2]) + (255,)
    for x, y in ((7, 7), (8, 7), (7, 8), (8, 8)):
        px[x, y] = FADED_DARK + (255,)
    return im


def roots(name, colors, glow=None):
    """Knotted roots: diagonal twists of two shades over a dark gap."""
    rng = seeded(name)
    im = new()
    px = im.load()
    for y in range(16):
        for x in range(16):
            twist = (x + y * 2 + (x // 4) * 3) % 8
            if twist in (0, 1, 2):
                c = colors[1]
            elif twist in (3, 4):
                c = colors[0]
            elif twist == 5:
                c = colors[2]
            else:
                c = colors[3]
            if rng.random() < 0.1:
                c = colors[rng.randrange(4)]
            px[x, y] = c + (255,)
    if glow:
        for _ in range(14):
            px[rng.randrange(16), rng.randrange(16)] = glow + (255,)
    return im


def lamp():
    rng = seeded("guardian_lamp")
    im = new()
    px = im.load()
    glow = ramp("ffe08a", "ffcf5a", "f2a93a", "fff3c4")
    for y in range(16):
        for x in range(16):
            d = min(x, y, 15 - x, 15 - y)
            if d == 0:
                c = GUARD_SEAM
            elif d <= 1:
                c = GUARD_PLANK[2]
            elif d == 2:
                c = GUARD_PLANK[0]
            else:
                c = glow[3] if abs(x - 7.5) + abs(y - 7.5) < 3 else glow[rng.choices([0, 1, 2], [4, 3, 1])[0]]
            px[x, y] = c + (255,)
    return im


def candle_wax():
    rng = seeded("prayer_candle_wax")
    im = new()
    px = im.load()
    wax = ramp("f2e8c8", "e8dcb4", "fff6dc", "d8cc9c")
    for y in range(16):
        for x in range(16):
            px[x, y] = wax[0 if rng.random() < 0.6 else rng.randrange(4)] + (255,)
    for x in (2, 6, 11):
        for y in range(rng.randrange(2, 5)):
            px[x, y] = wax[3] + (255,)
    return im


def make_textures():
    out = {}
    for kind in ("holystone", "veradexian", "brexallen"):
        out["%s_vase" % kind] = vase_texture(kind)
    out["animal_stash_side"] = weave("animal_stash_side", ramp("a89a72", "b8aa80", "98895e"), ramp("8b7a52", "7c6c46", "9a8a5c"), hx("5f5136"))
    out["animal_stash_top"] = weave("animal_stash_top", ramp("b8aa80", "c8ba90", "a89a72"), ramp("98895e", "8b7a52", "a89a72"), hx("6a5c3e"))
    out["outpost_campfire_log"] = ga.bark("outpost_campfire_log", ramp("7a5a3c", "684b30", "8a6a48", "563d26"))
    out["ambrosium_campfire_log"] = ga.bark("ambrosium_campfire_log", ramp("8a6a34", "7a5c2c", "9a7a40", "5a4420"))
    out["campfire_flame"] = fire_cutout("campfire_flame", hx("fff3c4"), hx("ffb347"), hx("e0541c"))
    out["ambrosium_flame"] = fire_cutout("ambrosium_flame", hx("fffbd0"), hx("ffe05a"), hx("e8b020"))
    out["prayer_candle_wax"] = candle_wax()
    out["prayer_candle_plate"] = ga.planks("prayer_candle_plate", GUARD_PLANK, GUARD_SEAM)
    out["candle_flame"] = fire_cutout("candle_flame", hx("fffbd0"), hx("ffe08a"), hx("f2a93a"))
    out["guardian_log"] = ga.bark("guardian_log", GUARD_BARK)
    out["guardian_log_top"] = ga.log_top("guardian_log_top", GUARD_BARK, GUARD_RING)
    out["stripped_guardian_log"] = ga.bark("stripped_guardian_log", GUARD_STRIP)
    out["stripped_guardian_log_top"] = ga.log_top("stripped_guardian_log_top", GUARD_STRIP, GUARD_RING)
    out["infected_guardian_log"] = ga.bark("infected_guardian_log", INFECTED)
    out["infected_guardian_log_top"] = ga.log_top("infected_guardian_log_top", INFECTED, INFECTED_RING)
    out["guardian_planks"] = ga.planks("guardian_planks", GUARD_PLANK, GUARD_SEAM)
    out["guardian_roots"] = roots("guardian_roots", ramp("5a4630", "684f36", "7b6040", "3e2f20"))
    out["lucent_guardian_roots"] = roots("lucent_guardian_roots", ramp("3f6a78", "4f8294", "5fa0b4", "2c4a58"), hx("c6f5ff"))
    out["guardian_lamp"] = lamp()
    out["faded_holystone_pillar"] = pillar_side()
    out["faded_holystone_pillar_top"] = pillar_top()
    out["faded_holystone_keystone"] = keystone()
    return out


def textures():
    for name, im in make_textures().items():
        save(im, os.path.join(BLOCK_DIR, name + ".png"))


# ---------------------------------------------------------------- models, blockstates, items

def assets():
    bs = os.path.join(ASSETS, "blockstates")

    def bstate(name, data):
        write(os.path.join(bs, name + ".json"), data)

    def item_def(name, model):
        write(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": model}})

    state = lambda model, **rot: dict({"model": mdl(model)}, **rot)
    box = ga.box_faces

    # vases: body, shoulder, neck and rim, all on one texture
    for kind in ("holystone", "veradexian", "brexallen"):
        name = kind + "_vase"
        parts = [((4, 0, 4), (12, 10, 12)), ((5, 10, 5), (11, 12, 11)), ((6, 12, 6), (10, 13, 10)), ((5, 13, 5), (11, 14, 11))]
        ga.block_model(name, {"parent": mc("block/block"), "textures": {"vase": tex(name), "particle": tex(name)},
                              "elements": [{"from": list(a), "to": list(b), "faces": box(a, b, "#vase")} for a, b in parts]})
        bstate(name, {"variants": {"": state(name)}})
        item_def(name, mdl(name))
    # the stash: a woven basket with a wider lid
    base = box((1, 0, 1), (15, 10, 15), "#side")
    lid = box((0, 10, 0), (16, 13, 16), "#side")
    lid["up"]["texture"] = "#top"
    lid["down"]["texture"] = "#top"
    ga.block_model("animal_stash", {"parent": mc("block/block"), "textures": {"side": tex("animal_stash_side"), "top": tex("animal_stash_top"),
                                                                              "particle": tex("animal_stash_side")},
                                    "elements": [{"from": [1, 0, 1], "to": [15, 10, 15], "faces": base}, {"from": [0, 10, 0], "to": [16, 13, 16], "faces": lid}]})
    bstate("animal_stash", {"variants": {"facing=north": state("animal_stash"), "facing=east": state("animal_stash", y=90),
                                         "facing=south": state("animal_stash", y=180), "facing=west": state("animal_stash", y=270)}})
    item_def("animal_stash", mdl("animal_stash"))

    def fire(texture, extent):
        """Two crossing planes of flame."""
        a = {"north": {"uv": [0, 0, 16, 16], "texture": texture}, "south": {"uv": [0, 0, 16, 16], "texture": texture}}
        b = {"west": {"uv": [0, 0, 16, 16], "texture": texture}, "east": {"uv": [0, 0, 16, 16], "texture": texture}}
        return [{"from": [8 - extent, 2, 8], "to": [8 + extent, 2 + 2 * extent, 8], "faces": a},
                {"from": [8, 2, 8 - extent], "to": [8, 2 + 2 * extent, 8 + extent], "faces": b}]

    # the outpost campfire (one of its four parts): a stone slab, a stack of logs; the lit one has fire in it
    slab = box((0, 0, 0), (16, 2, 16), "#stone")
    logs = [((2, 2, 2), (14, 5, 5)), ((2, 2, 11), (14, 5, 14)), ((2, 5, 2), (5, 8, 14)), ((11, 5, 2), (14, 8, 14))]
    elements = [{"from": [0, 0, 0], "to": [16, 2, 16], "faces": slab}] + [{"from": list(a), "to": list(b), "faces": box(a, b, "#log")} for a, b in logs]
    textures_ = {"stone": tex("faded_holystone_flagstones"), "log": tex("outpost_campfire_log"), "flame": tex("campfire_flame"), "particle": tex("outpost_campfire_log")}
    ga.block_model("outpost_campfire", {"parent": mc("block/block"), "textures": textures_, "elements": elements})
    ga.block_model("outpost_campfire_lit", {"parent": mc("block/block"), "textures": textures_, "elements": elements + fire("#flame", 6)})
    variants = {}
    for east in ("false", "true"):
        for south in ("false", "true"):
            for lit in ("false", "true"):
                variants["east=%s,lit=%s,south=%s" % (east, lit, south)] = state("outpost_campfire_lit" if lit == "true" else "outpost_campfire")
    bstate("outpost_campfire", {"variants": variants})
    item_def("outpost_campfire", mdl("outpost_campfire"))

    # the ambrosium campfire: two layers of crossed logs, golden fire
    logs = [((1, 0, 3), (15, 4, 7)), ((1, 0, 9), (15, 4, 13)), ((3, 4, 1), (7, 8, 15)), ((9, 4, 1), (13, 8, 15))]
    elements = [{"from": list(a), "to": list(b), "faces": box(a, b, "#log")} for a, b in logs]
    textures_ = {"log": tex("ambrosium_campfire_log"), "flame": tex("ambrosium_flame"), "particle": tex("ambrosium_campfire_log")}
    ga.block_model("ambrosium_campfire", {"parent": mc("block/block"), "textures": textures_, "elements": elements})
    ga.block_model("ambrosium_campfire_lit", {"parent": mc("block/block"), "textures": textures_, "elements": elements + fire("#flame", 6)})
    bstate("ambrosium_campfire", {"variants": {"lit=false": state("ambrosium_campfire"), "lit=true": state("ambrosium_campfire_lit")}})
    item_def("ambrosium_campfire", mdl("ambrosium_campfire"))

    # the prayer candle: a plate on the wall (to the south in the model), a candle on it, a flame when lit
    plate = box((5, 1, 14), (11, 15, 16), "#plate")
    wax = box((7, 3, 11), (9, 12, 13), "#wax")
    arm = box((6, 2, 12), (10, 3, 14), "#plate")
    el = [{"from": [5, 1, 14], "to": [11, 15, 16], "faces": plate}, {"from": [7, 3, 11], "to": [9, 12, 13], "faces": wax}, {"from": [6, 2, 12], "to": [10, 3, 14], "faces": arm}]
    flame_el = [{"from": [5.5, 12, 12], "to": [10.5, 17, 12], "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#flame"}, "south": {"uv": [0, 0, 16, 16], "texture": "#flame"}}},
                {"from": [8, 12, 9.5], "to": [8, 17, 14.5], "faces": {"west": {"uv": [0, 0, 16, 16], "texture": "#flame"}, "east": {"uv": [0, 0, 16, 16], "texture": "#flame"}}}]
    t = {"plate": tex("prayer_candle_plate"), "wax": tex("prayer_candle_wax"), "flame": tex("candle_flame"), "particle": tex("prayer_candle_plate")}
    ga.block_model("prayer_candle", {"parent": mc("block/block"), "textures": t, "elements": el})
    ga.block_model("prayer_candle_lit", {"parent": mc("block/block"), "textures": t, "elements": el + flame_el})
    pv = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for lit in ("false", "true"):
            pv["facing=%s,lit=%s" % (facing, lit)] = state("prayer_candle_lit" if lit == "true" else "prayer_candle", **({"y": y} if y else {}))
    bstate("prayer_candle", {"variants": pv})
    item_def("prayer_candle", mdl("prayer_candle"))

    # guardian wood: logs and wood (bark all round), planks, stairs, slab, roots, lamp; the faded pillar and keystone
    for b in WOODS:
        base = b.replace("_wood", "_log")
        top = tex(base + "_top")
        side = tex(base)
        end = side if b.endswith("_wood") else top
        ga.block_model(b, {"parent": mc("block/cube_column"), "textures": {"end": end, "side": side}})
        ga.block_model(b + "_horizontal", {"parent": mc("block/cube_column_horizontal"), "textures": {"end": end, "side": side}})
        bstate(b, {"variants": {"axis=x": state(b + "_horizontal", x=90, y=90), "axis=y": state(b), "axis=z": state(b + "_horizontal", x=90)}})
        item_def(b, mdl(b))
    for b in ("guardian_planks", "guardian_roots", "lucent_guardian_roots", "guardian_lamp", "faded_holystone_keystone"):
        ga.block_model(b, {"parent": mc("block/cube_all"), "textures": {"all": tex(b)}})
        bstate(b, {"variants": {"": state(b)}})
        item_def(b, mdl(b))
    ga.block_model("faded_holystone_pillar", {"parent": mc("block/cube_column"), "textures": {"end": tex("faded_holystone_pillar_top"), "side": tex("faded_holystone_pillar")}})
    ga.block_model("faded_holystone_pillar_horizontal", {"parent": mc("block/cube_column_horizontal"),
                                                         "textures": {"end": tex("faded_holystone_pillar_top"), "side": tex("faded_holystone_pillar")}})
    bstate("faded_holystone_pillar", {"variants": {"axis=x": state("faded_holystone_pillar_horizontal", x=90, y=90), "axis=y": state("faded_holystone_pillar"),
                                                   "axis=z": state("faded_holystone_pillar_horizontal", x=90)}})
    item_def("faded_holystone_pillar", mdl("faded_holystone_pillar"))
    t = {"bottom": tex("guardian_planks"), "side": tex("guardian_planks"), "top": tex("guardian_planks")}
    ga.block_model("guardian_stairs", {"parent": mc("block/stairs"), "textures": t})
    ga.block_model("guardian_stairs_inner", {"parent": mc("block/inner_stairs"), "textures": t})
    ga.block_model("guardian_stairs_outer", {"parent": mc("block/outer_stairs"), "textures": t})
    bstate("guardian_stairs", {"variants": ga.stairs_state(mdl("guardian_stairs"))})
    item_def("guardian_stairs", mdl("guardian_stairs"))
    ga.block_model("guardian_slab", {"parent": mc("block/slab"), "textures": t})
    ga.block_model("guardian_slab_top", {"parent": mc("block/slab_top"), "textures": t})
    bstate("guardian_slab", {"variants": {"type=bottom": state("guardian_slab"), "type=double": state("guardian_planks"), "type=top": state("guardian_slab_top")}})
    item_def("guardian_slab", mdl("guardian_slab"))


# ---------------------------------------------------------------- loot: blocks

def block_loot():
    out = os.path.join(DATA, "loot_table", "blocks")
    tables = {}
    for b in ALL_BLOCKS:
        tables[b] = ga.self_table(b)
    # vases give up their contents when they break (in code); whole only with Silk Touch
    for b in VASES:
        tables[b] = ga.table(b, [{"rolls": 1, "condition": ga.SILK, "entries": [ga.item_entry(jp(b))]}])
    # the outpost campfire drops its item in code (once, not once per part)
    tables["outpost_campfire"] = {"type": "minecraft:block", "pools": [], "random_sequence": "%s:blocks/outpost_campfire" % NS}
    double = {"type": "minecraft:match_block", "blocks": jp("guardian_slab"), "state": {"type": "double"}}
    tables["guardian_slab"] = ga.table("guardian_slab", [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
        ga.item_entry(jp("guardian_slab"), condition=double, modifier=[{"type": "minecraft:set_count", "count": 2, "add": False}, {"type": "minecraft:explosion_decay"}]),
        ga.item_entry(jp("guardian_slab"), condition=ga.SURVIVES)]}]}])
    for b, t in tables.items():
        write(os.path.join(out, b + ".json"), t)


# ---------------------------------------------------------------- loot: structures

# the Aether gear track G adds, with the vanilla stand-in used until it is merged (TRACK_G = False)
GEAR = {"zanite_pickaxe": "iron_pickaxe", "holystone_pickaxe": "stone_pickaxe", "cold_aercloud_glider": "elytra", "golden_aercloud_glider": "elytra"}


def item(name, lo=1, hi=1, weight=1):
    entry = {"type": "minecraft:item", "name": name, "weight": weight}
    if (lo, hi) != (1, 1):
        entry["modifier"] = [{"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
    return entry


def gear(name, lo=1, hi=1, weight=1):
    """An entry for Aether gear from track G: the real item when TRACK_G, else its vanilla stand-in."""
    return item(jp(name) if TRACK_G else mc(GEAR[name]), lo, hi, weight)


def pool(rolls, entries):
    return {"rolls": rolls if isinstance(rolls, int) else {"type": "minecraft:uniform", "min": rolls[0], "max": rolls[1]}, "entries": entries}


def table(kind, name, pools):
    write(os.path.join(DATA, "loot_table", kind, name + ".json"), {"type": "minecraft:chest", "pools": pools, "random_sequence": "%s:%s/%s" % (NS, kind, name)})


# stand-ins for Aether items that do not exist yet (documented in docs/aether.md): fossilized zanite = zanite gemstone, shifting glass =
# scatterglass, resonant stone = amethyst shard, pendants = a zanite gemstone or icestone, guidebook page = paper, scatterglass bolt and
# amber dart = arrow, skyroot bucket = bucket, rope = string, bedroll = a brown cloudwool carpet, swet jelly = swet gel, irradiated dust =
# glowstone dust, holystone bookshelf = bookshelf, trowel = iron shovel, discs = vanilla discs
DISCS = ["music_disc_cat", "music_disc_chirp", "music_disc_mellohi", "music_disc_stal", "music_disc_otherside"]


def disc_entries():
    return [item(mc(d)) for d in DISCS]


def loot():
    fruit = [item(jp("orange"), 1, 3, 3), item(jp("blue_berry"), 1, 3, 3), item(jp("wyndberry"), 1, 2, 2), item(jp("satival_bulb"), 1, 2, 2)]
    # camps
    table("chests", "camp/hunter", [
        pool((2, 3), [item(jp("raw_taegore_meat"), 1, 4, 3), item(jp("kirrid_loin"), 1, 3, 3), item(jp("burrukai_rib_cut"), 1, 3, 2), item(jp("prismallard_leg"), 1, 2, 2)]),
        pool((1, 3), [item(jp("beast_pelt"), 1, 2, 3), item(jp("cloudwool"), 2, 6, 3), item(jp("brown_cloudwool_carpet"), 1, 2, 1)]),
        pool(1, [item(mc("shield"), weight=2), item(jp("beast_pelt"), 2, 4, 2), item(jp("brown_cloudwool_carpet"), 1, 1, 2), item(mc("stone_sword"), weight=1)]),
    ])
    table("chests", "camp/farmer", [
        pool((2, 4), fruit),
        pool((1, 3), [item(jp("brettl_grass"), 1, 4, 3), item(jp("valkyrie_wings"), 1, 2, 2), item(jp("aechor_petal"), 1, 3, 2)]),
        pool(1, [item(mc("iron_shovel"), weight=2), item(mc("bucket"), weight=2), item(mc("water_bucket"), weight=1)]),
    ])
    table("chests", "camp/fletcher", [
        pool((2, 3), [item(mc("arrow"), 3, 8, 4), item(jp("scatterglass_shard"), 2, 5, 3), item(jp("toxic_dart"), 2, 5, 1)]),
        pool((1, 3), [item(jp("cloudtwine"), 1, 3, 3), item(jp("moa_feather"), 1, 4, 3), item(jp("cockatrice_feather"), 1, 2, 1)]),
        pool((0, 1), [item(mc("crossbow"), weight=4), item(mc("bow"), weight=2)]),
    ])
    # watchtower
    table("chests", "watchtower", [
        pool(1, [item(mc("crossbow"), weight=4), item(mc("crossbow"), weight=2), item(mc("bow"), weight=1)]),
        pool((2, 3), [item(jp("scatterglass_shard"), 2, 6, 3), item(jp("golden_amber"), 1, 3, 3)]),
        pool((1, 3), [item(mc("arrow"), 3, 8, 4), item(jp("toxic_dart"), 2, 5, 2)]),
        pool((1, 2), [item(jp("ambrosium_torch"), 2, 6, 3), item(jp("ambrosium_shard"), 1, 4, 3)]),
        pool((1, 2), fruit),
        pool(1, [item(jp("brown_cloudwool_carpet"), weight=3), item(jp("ambrosium_campfire"), weight=2), item(mc("bucket"), weight=2)]),
        pool((1, 2), [item(mc("string"), 2, 5, 2), item(jp("cloudtwine"), 1, 3, 2)]),
    ])
    # animal den
    table("chests", "animal_den", [
        pool(2, fruit),
        pool((2, 3), [item(jp("raw_taegore_meat"), 1, 3, 3), item(jp("burrukai_rib_cut"), 1, 3, 2), item(jp("kirrid_loin"), 1, 3, 3), item(jp("prismallard_leg"), 1, 2, 2)]),
        pool(1, [item(mc("stick"), 2, 6, 3), item(jp("beast_pelt"), 1, 2, 2), item(jp("burrukai_plate"), 1, 1, 1), item(jp("cloudwool"), 2, 4, 2)]),
        pool((0, 1), [item(jp("aechor_petal"), 1, 2, 1), item(jp("swet_gel"), 1, 2, 1)]),
    ])
    # brexallen ruins
    table("chests", "brexallen_ruins", [
        pool((3, 6), [item(jp("ambrosium_shard"), 1, 4, 3), item(jp("ambrosium_torch"), 2, 6, 3), item(jp("golden_amber"), 1, 3, 3), item(jp("icestone"), 2, 5, 2),
                      item(jp("zanite_gemstone"), 1, 2, 2)]),
        pool((1, 3), [item(jp("cloudtwine"), 1, 3, 2), item(jp("cockatrice_feather"), 1, 2, 2), item(jp("storm_aercloud"), 1, 2, 1), item(jp("swet_gel"), 1, 3, 2)]),
        pool(1, [item(jp("glint_coin"), 1, 4, 10), item(jp("scatterglass"), 1, 2, 6), gear("zanite_pickaxe", weight=3), item(mc("amethyst_shard"), 1, 3, 3),
                 item(jp("zanite_gemstone"), 1, 1, 2), item(mc("paper"), 1, 2, 2)] + disc_entries()),
    ])
    # undercloud mineshaft
    table("chests", "undercloud_mineshaft", [
        pool((2, 3), [item(jp("ambrosium_torch"), 2, 6, 3), item(jp("ambrosium_shard"), 1, 4, 3), item(jp("icestone"), 2, 5, 2), item(jp("zanite_gemstone"), 1, 2, 2)]),
        pool((2, 3), [item(jp("undershale"), 3, 8, 3), item(jp("agiosite"), 2, 6, 2), item(jp("holystone"), 3, 8, 3), item(mc("quartz"), 2, 5, 2)]),
        pool((1, 2), [item(jp("cloudtwine"), 1, 3, 2), item(jp("cockatrice_feather"), 1, 2, 2), item(jp("beast_pelt"), 1, 2, 2), item(jp("swet_gel"), 1, 3, 2)]),
        pool(1, [item(jp("glint_coin"), 1, 4, 12), gear("holystone_pickaxe", weight=3), gear("zanite_pickaxe", weight=3), item(mc("amethyst_shard"), 1, 3, 3),
                 item(jp("zanite_gemstone"), 1, 1, 2), item(jp("scatterglass"), 1, 2, 2), item(mc("water_bucket"), weight=2)] + disc_entries()),
    ])
    # library
    vault = [
        pool((3, 4), [item(mc("book"), 1, 3, 4), item(mc("paper"), 2, 6, 3), item(jp("beast_pelt"), 1, 2, 2), item(mc("paper"), 1, 1, 2)]),
        pool((2, 3), [item(jp("ambrosium_shard"), 2, 5, 3), item(jp("golden_amber"), 1, 3, 3), item(jp("zanite_gemstone"), 1, 3, 2), item(jp("glint_coin"), 2, 6, 3)]),
        pool((2, 3), [item(jp("wyndberry"), 1, 3, 3), item(jp("swet_gel"), 1, 3, 2)]),
        pool(1, [item(mc("bookshelf"), 1, 2, 4), item(jp("arkenium_lantern"), 1, 2, 3), item(jp("scatterglass"), 1, 2, 2), gear("golden_aercloud_glider", weight=1),
                 item(jp("zanite_gemstone"), 1, 1, 1), item(jp("icestone"), 2, 4, 1)]),
    ]
    table("chests", "veradexian_library/vaults", vault)
    table("chests", "veradexian_library/common", [
        pool((1, 2), [item(mc("book"), 1, 2, 4), item(mc("paper"), 2, 5, 3), item(jp("beast_pelt"), 1, 1, 1)]),
        pool((1, 2), [item(jp("ambrosium_shard"), 1, 3, 3), item(jp("golden_amber"), 1, 2, 3), item(jp("glint_coin"), 1, 3, 2)]),
        pool((1, 2), [item(jp("wyndberry"), 1, 2, 3), item(jp("swet_gel"), 1, 2, 2)]),
        pool((0, 1), [item(mc("bookshelf"), 1, 1, 3), item(jp("arkenium_lantern"), 1, 1, 2), item(jp("scatterglass"), 1, 1, 1)]),
    ])
    # irradiated remnants
    table("chests", "irradiated_remnants", [
        pool(1, [item(jp("blue_berry"), 2, 4, 1), item(mc("golden_apple"), 1, 1, 1)]),
        pool((0, 1), [item(jp("orange"), 1, 3, 1)]),
        pool((3, 4), [item(jp("ambrosium_shard"), 1, 4, 3), item(mc("glowstone_dust"), 2, 6, 3)]),
        pool(1, [item(jp("golden_amber"), 1, 2, 3), item(jp("zanite_gemstone"), 1, 2, 2)]),
        pool(1, [item(mc("iron_sword"), weight=2), item(mc("shield"), weight=2), item(mc("crossbow"), weight=2), item(mc("iron_axe"), weight=1)]),
        pool(1, [item(jp("linen_bandage"), 1, 3, 3), item(jp("splint"), 1, 2, 3), item(mc("paper"), 1, 2, 2), item(mc("bucket"), weight=2), item(jp("glint_coin"), 1, 3, 2)]),
    ])
    # the infected guardian tree: its floors, and the vault at the top (the Aether II tree has no boss and no loot, so these are ours)
    table("chests", "infected_guardian_tree", [
        pool((2, 3), fruit),
        pool((1, 3), [item(jp("ambrosium_shard"), 2, 5, 4), item(jp("golden_amber"), 1, 3, 3), item(jp("glint_coin"), 2, 6, 3), item(jp("zanite_gemstone"), 1, 2, 2)]),
        pool((1, 2), [item(jp("cloudtwine"), 1, 3, 2), item(jp("swet_gel"), 1, 3, 2), item(jp("cockatrice_feather"), 1, 2, 2), item(jp("beast_pelt"), 1, 2, 2)]),
        pool((0, 1), [item(mc("crossbow"), weight=2), item(mc("iron_pickaxe"), weight=2), item(mc("shield"), weight=1)]),
    ])
    table("chests", "infected_guardian_tree_vault", [
        pool((2, 3), [item(jp("zanite_gemstone"), 2, 5, 3), item(jp("inert_arkenium"), 1, 3, 2), item(jp("glint_gemstone"), 1, 3, 2), item(jp("enchanted_gravitite"), 1, 2, 2)]),
        pool((2, 3), [item(jp("glint_coin"), 3, 8, 3), item(jp("ambrosium_shard"), 3, 8, 3), item(jp("golden_amber_block"), 1, 1, 1)]),
        pool((1, 2), [item(mc("golden_apple"), 1, 2, 3), item(jp("wyndberry"), 2, 4, 2), item(mc("enchanted_golden_apple"), 1, 1, 1)]),
        pool(1, [item(mc("diamond_pickaxe"), weight=2), item(mc("diamond_chestplate"), weight=1), item(mc("diamond_helmet"), weight=1), item(jp("arkenium_lantern"), 1, 2, 2),
                 gear("golden_aercloud_glider", weight=1)] + disc_entries()),
    ])
    # vases: one roll each
    ruins = [item(jp("ambrosium_shard"), 1, 3, 20), item(jp("scatterglass_shard"), 1, 3, 16), item(jp("icestone"), 1, 3, 15), item(mc("quartz"), 1, 3, 15),
             item(jp("golden_amber"), 1, 2, 12), item(jp("cloudtwine"), 1, 2, 12), item(jp("beast_pelt"), 1, 1, 12), item(jp("scatterglass"), 1, 1, 11),
             item(jp("burrukai_plate"), 1, 1, 10), item(mc("paper"), 1, 2, 9), item(jp("golden_aercloud"), 1, 1, 8), item(jp("glint_coin"), 1, 2, 8),
             item(jp("zanite_gemstone"), 1, 1, 8), item(mc("crossbow"), 1, 1, 6), item(mc("shield"), 1, 1, 6), item(mc("bucket"), 1, 1, 6), item(mc("music_disc_cat"), 1, 1, 1)]
    table("vases", "veradexian_ruins", [pool(1, ruins)])
    table("vases", "brexallen_ruins", [pool(1, ruins + [item(jp("storm_aercloud"), 1, 1, 6), item(jp("cockatrice_feather"), 1, 2, 6)])])
    table("vases", "undercloud_mineshaft", [pool(1, ruins + [item(jp("undershale"), 2, 5, 10), item(jp("agiosite"), 2, 4, 8), item(jp("swet_gel"), 1, 2, 6)])])
    table("vases", "ancient_henge", [pool(1, ruins + [item(jp("cold_aercloud"), 1, 2, 2), item(jp("blue_aercloud"), 1, 2, 2), item(jp("golden_aercloud"), 1, 2, 2),
                                                   item(jp("golden_amber_block"), 1, 1, 1)])])


# ---------------------------------------------------------------- recipes and tags

def recipes():
    out = os.path.join(DATA, "recipe", SUB)
    shaped = lambda key, pattern, result, count=1, cat="building": {"type": "minecraft:crafting_shaped", "category": cat, "key": key, "pattern": pattern,
                                                                    "result": {"id": result, "count": count} if count > 1 else {"id": result}}
    shapeless = lambda ings, result, count=1, cat="misc": {"type": "minecraft:crafting_shapeless", "category": cat, "ingredients": ings,
                                                          "result": {"id": result, "count": count} if count > 1 else {"id": result}}
    cut = lambda ing, result, count=1: {"type": "minecraft:stonecutting", "ingredient": ing, "result": {"id": result, "count": count} if count > 1 else {"id": result}}
    r = {
        "guardian_planks_from_guardian_log": shapeless([jp("guardian_log")], jp("guardian_planks"), 4),
        "guardian_planks_from_guardian_wood": shapeless([jp("guardian_wood")], jp("guardian_planks"), 4),
        "guardian_planks_from_stripped_guardian_log": shapeless([jp("stripped_guardian_log")], jp("guardian_planks"), 4),
        "guardian_planks_from_infected_guardian_log": shapeless([jp("infected_guardian_log")], jp("guardian_planks"), 4),
        "guardian_wood": shaped({"#": jp("guardian_log")}, ["##", "##"], jp("guardian_wood"), 3),
        "stripped_guardian_wood": shaped({"#": jp("stripped_guardian_log")}, ["##", "##"], jp("stripped_guardian_wood"), 3),
        "infected_guardian_wood": shaped({"#": jp("infected_guardian_log")}, ["##", "##"], jp("infected_guardian_wood"), 3),
        "guardian_stairs": shaped({"#": jp("guardian_planks")}, ["#  ", "## ", "###"], jp("guardian_stairs"), 4),
        "guardian_slab": shaped({"#": jp("guardian_planks")}, ["###"], jp("guardian_slab"), 6),
        "prayer_candle": shapeless([jp("golden_amber"), jp("cloudtwine")], jp("prayer_candle"), 2),
        "ambrosium_campfire": shaped({"S": mc("stick"), "A": jp("ambrosium_shard"), "L": jp("skyroot_log")}, [" S ", "SAS", "LLL"], jp("ambrosium_campfire"), 1, "misc"),
        "outpost_campfire": shaped({"F": jp("faded_holystone_flagstones"), "C": jp("ambrosium_campfire")}, ["FFF", "FCF", "FFF"], jp("outpost_campfire"), 1, "misc"),
        "faded_holystone_pillar": shaped({"#": jp("faded_holystone_bricks")}, ["#", "#"], jp("faded_holystone_pillar"), 2),
        "faded_holystone_keystone": shaped({"#": jp("faded_holystone_bricks")}, ["##", "##"], jp("faded_holystone_keystone"), 1),
        "faded_holystone_pillar_from_faded_holystone_bricks_stonecutting": cut(jp("faded_holystone_bricks"), jp("faded_holystone_pillar")),
        "faded_holystone_keystone_from_faded_holystone_bricks_stonecutting": cut(jp("faded_holystone_bricks"), jp("faded_holystone_keystone")),
        "guardian_stairs_from_guardian_planks_stonecutting": cut(jp("guardian_planks"), jp("guardian_stairs")),
        "guardian_slab_from_guardian_planks_stonecutting": cut(jp("guardian_planks"), jp("guardian_slab"), 2),
    }
    for name, data in r.items():
        write(os.path.join(out, name + ".json"), data)


def tags():
    b = os.path.join(MCDATA, "tags", "block")
    i = os.path.join(MCDATA, "tags", "item")
    jb = os.path.join(DATA, "tags", "block")
    ji = os.path.join(DATA, "tags", "item")
    ids = lambda names: [jp(n) for n in names]
    logs = ["guardian_log", "guardian_wood", "stripped_guardian_log", "stripped_guardian_wood", "infected_guardian_log", "infected_guardian_wood"]
    ga.merge_tag(os.path.join(b, "mineable", "pickaxe.json"), ids(["faded_holystone_pillar", "faded_holystone_keystone"]))
    ga.merge_tag(os.path.join(b, "needs_stone_tool.json"), ids(["faded_holystone_pillar", "faded_holystone_keystone"]))
    ga.merge_tag(os.path.join(b, "mineable", "axe.json"), ids(logs + ["guardian_planks", "guardian_stairs", "guardian_slab", "guardian_roots", "lucent_guardian_roots",
                                                                       "animal_stash", "outpost_campfire", "ambrosium_campfire"]))
    for root in (b, i):
        ga.merge_tag(os.path.join(root, "logs.json"), ids(logs))
        ga.merge_tag(os.path.join(root, "logs_that_burn.json"), ids(logs))
        ga.merge_tag(os.path.join(root, "planks.json"), ids(["guardian_planks"]))
        ga.merge_tag(os.path.join(root, "stairs.json"), ids(["guardian_stairs"]))
        ga.merge_tag(os.path.join(root, "slabs.json"), ids(["guardian_slab"]))
        ga.merge_tag(os.path.join(root, "wooden_stairs.json"), ids(["guardian_stairs"]))
        ga.merge_tag(os.path.join(root, "wooden_slabs.json"), ids(["guardian_slab"]))
    ga.merge_tag(os.path.join(jb, "aether_logs.json"), ids(logs))
    ga.merge_tag(os.path.join(ji, "aether_logs.json"), ids(logs))
    ga.merge_tag(os.path.join(ji, "stripped_logs.json"), ids(["stripped_guardian_log", "stripped_guardian_wood"]))
    # vases are broken by a pot-shattering tool, but any tool does here; nothing else to tag


# ---------------------------------------------------------------- the structures' data

SURFACE_MID = ["flourishing_field", "verdant_woods", "shrouded_forest", "violet_highwoods", "turquoise_forest", "glistening_swamp", "magnetic_scar",
               "enduring_woodland", "frigid_sierra", "sheer_tundra"]
ARCTIC = ["frigid_sierra", "enduring_woodland", "sheer_tundra"]
UNDERGROUND = ["shimmering_basin", "flourishing_field", "verdant_woods", "shrouded_forest", "violet_highwoods", "turquoise_forest", "glistening_swamp",
               "magnetic_scar", "frozen_lakes", "sheer_tundra", "enduring_woodland", "frigid_sierra"]

# id: (plan, variant or None, biomes, step)
STRUCTURES = {
    "outpost": ("outpost", None, SURFACE_MID, "surface_structures"),
    "camp_highfields": ("camp", 0, ["flourishing_field", "verdant_woods"], "surface_structures"),
    "camp_magnetic": ("camp", 1, ["magnetic_scar", "turquoise_forest", "violet_highwoods"], "surface_structures"),
    "camp_arctic": ("camp", 2, ARCTIC, "surface_structures"),
    "watchtower": ("tower", None, SURFACE_MID, "surface_structures"),
    "animal_den": ("den", None, ["verdant_woods", "shrouded_forest", "violet_highwoods", "enduring_woodland"], "surface_structures"),
    "veradexian_ruins_temperate": ("ruins", 0, ["flourishing_field", "verdant_woods", "shrouded_forest", "glistening_swamp", "violet_highwoods", "turquoise_forest",
                                                "magnetic_scar"], "surface_structures"),
    "veradexian_ruins_arctic": ("ruins", 1, ARCTIC, "surface_structures"),
    "ancient_henge": ("henge", None, ["magnetic_scar", "turquoise_forest", "glistening_swamp", "violet_highwoods"], "surface_structures"),
    "veradexian_library_temperate": ("library", 0, ["verdant_woods", "shrouded_forest", "glistening_swamp", "violet_highwoods"], "underground_structures"),
    "veradexian_library_arctic": ("library", 1, ["enduring_woodland"], "underground_structures"),
    "veradexian_aqueduct": ("aqueduct", None, ["shimmering_basin"], "surface_structures"),
    "brexallen_ruins": ("brexallen", None, UNDERGROUND + ["hestveil_caverns"], "underground_structures"),
    "undercloud_mineshaft": ("mineshaft", None, UNDERGROUND, "underground_structures"),
    "irradiated_bunker_remnants": ("irradiated", 0, ["contaminated_jungle", "battleground_wastes"], "surface_structures"),
    "irradiated_settlement_remnants": ("irradiated", 1, ["contaminated_jungle", "battleground_wastes"], "surface_structures"),
    "infected_guardian_tree": ("guardian", None, ["flourishing_field", "verdant_woods", "shrouded_forest", "magnetic_scar", "violet_highwoods", "enduring_woodland",
                                                  "sheer_tundra"], "surface_structures"),
}

# set id: (spacing, separation, salt, [(structure, weight)], frequency, exclusion (other set, chunks) or None, spread)
SETS = {
    "aether_surface_ruins": (14, 8, 1380213101, [("veradexian_ruins_temperate", 3), ("veradexian_ruins_arctic", 3), ("ancient_henge", 4),
                                                 ("veradexian_library_temperate", 2), ("veradexian_library_arctic", 2)], 0.75, ("outposts", 6), "linear"),
    "aether_surface_structures": (18, 8, 1380213102, [("camp_highfields", 3), ("camp_magnetic", 3), ("camp_arctic", 3), ("watchtower", 2)], 1.0, ("outposts", 8), "linear"),
    "undercloud_mineshafts": (12, 6, 1380213103, [("undercloud_mineshaft", 1)], 1.0, None, "triangular"),
    "aether_underground_ruins": (4, 2, 1380213104, [("brexallen_ruins", 1)], 1.0, None, "linear"),
    "animal_dens": (8, 5, 1380213105, [("animal_den", 1)], 1.0, ("outposts", 4), "linear"),
    "aqueducts": (10, 5, 1380213106, [("veradexian_aqueduct", 1)], 1.0, ("outposts", 4), "linear"),
    "irradiated_remnants": (8, 3, 1380213107, [("irradiated_bunker_remnants", 1), ("irradiated_settlement_remnants", 1)], 1.0, ("sentry_ruins", 4), "linear"),
    "outposts": (28, 18, 1380213108, [("outpost", 1)], 1.0, None, "linear"),
    "infected_guardian_trees": (22, 10, 1380213109, [("infected_guardian_tree", 1)], 1.0, ("outposts", 5), "linear"),
}


def structures():
    for name, (plan, variant, biomes, step) in STRUCTURES.items():
        write(os.path.join(DATA, "tags", "worldgen", "biome", "has_structure", name + ".json"), {"values": [jp(x) for x in biomes]})
        data = {"type": jp("aether_structure"), "biomes": "#" + jp("has_structure/" + name), "step": step, "spawn_overrides": {}, "terrain_adaptation": "none",
                "plan": plan}
        if variant is not None:
            data["variant"] = variant
        write(os.path.join(DATA, "worldgen", "structure", name + ".json"), data)
    for name, (spacing, sep, salt, members, freq, exclusion, spread) in SETS.items():
        placement = {"type": "minecraft:random_spread", "spacing": spacing, "separation": sep, "salt": salt, "spread_type": spread}
        if freq != 1.0:
            placement["frequency"] = freq
        if exclusion:
            placement["exclusion_zone"] = {"other_set": jp(exclusion[0]), "chunk_count": exclusion[1]}
        write(os.path.join(DATA, "worldgen", "structure_set", name + ".json"),
              {"structures": [{"structure": jp(s), "weight": w} for s, w in members], "placement": placement})


# ---------------------------------------------------------------- lang

def lang():
    mine = {"block.%s.%s" % (NS, b): n for b, n in NAMES.items()}
    mine["message.jaymospowers.outpost.added"] = "Campfire remembered: you will wake here if you die with no bed."
    mine["message.jaymospowers.outpost.known"] = "You already remember this campfire."
    mine["message.jaymospowers.outpost.respawn"] = "You wake beside an outpost campfire."
    write(os.path.join(ASSETS, "lang", "parts", "aether_structures.json"), mine)


def main():
    textures()
    assets()
    block_loot()
    loot()
    recipes()
    tags()
    structures()
    lang()
    print("Aether II structures art and data written.")


if __name__ == "__main__":
    main()
