"""The blossom land's building blocks (com.jaymoskingdoms.blossomland.BlossomBlocks; docs/BlossomLands-Plan.md, patch 3):
shoji panels, tatami, plaster, red and black lacquered planks, the paper lantern, the stone lantern, thatch (block, slab,
stairs) and the ward rope. Draws their textures (original, 16 px) and writes block and item models, blockstates, loot
tables, recipes, the mineable tags and the lang part. Safe to run again. Run from the kingdoms project root:
python -I tools/gen_blossom_blocks.py"""
from PIL import Image
import json
import os
import random

NS = "jaymoskingdoms"
RES = "src/main/resources/"
ASSETS = RES + "assets/" + NS + "/"
DATA = RES + "data/" + NS + "/"

NAMES = {
    "shoji": "Shoji Panel",
    "tatami": "Tatami",
    "plaster": "Plaster",
    "red_lacquered_planks": "Red Lacquered Planks",
    "black_lacquered_planks": "Black Lacquered Planks",
    "paper_lantern": "Paper Lantern",
    "stone_lantern": "Stone Lantern",
    "thatch": "Thatch",
    "thatch_slab": "Thatch Slab",
    "thatch_stairs": "Thatch Stairs",
    "ward_rope": "Ward Rope",
}
AXE = ["shoji", "red_lacquered_planks", "black_lacquered_planks", "paper_lantern"]
PICKAXE = ["plaster", "stone_lantern"]
HOE = ["tatami", "thatch", "thatch_slab", "thatch_stairs"]


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def tex(name):
    return NS + ":block/" + name


def mdl(name):
    return NS + ":block/" + name


# --- textures ----------------------------------------------------------------------------------------------------

def clamp(v):
    return max(0, min(255, int(round(v))))


def shade(c, k):
    return tuple(clamp(v * k) for v in c)


def canvas(fill=(0, 0, 0, 0)):
    return Image.new("RGBA", (16, 16), fill)


def put(img, x, y, c, a=255):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), tuple(c[:3]) + (a,))


def save(img, name):
    path = ASSETS + "textures/block/" + name + ".png"
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


WOOD = [(0x3b, 0x2a, 0x1a), (0x52, 0x3a, 0x22), (0x6a, 0x4c, 0x2d), (0x82, 0x60, 0x3a)]
PAPER = [(0xd9, 0xd2, 0xbc), (0xe6, 0xe0, 0xcc), (0xf0, 0xeb, 0xda), (0xf7, 0xf3, 0xe6)]
STRAW = [(0x7a, 0x62, 0x2a), (0x96, 0x7a, 0x34), (0xb3, 0x94, 0x42), (0xcc, 0xae, 0x55), (0xdd, 0xc4, 0x6e)]
RUSH = [(0x6f, 0x7a, 0x3a), (0x86, 0x91, 0x48), (0x9b, 0xa6, 0x57), (0xb0, 0xb9, 0x6a)]
STONE = [(0x55, 0x55, 0x57), (0x6b, 0x6b, 0x6d), (0x7f, 0x7f, 0x81), (0x93, 0x93, 0x95)]
GLOW = [(0xe8, 0x9a, 0x3c), (0xf6, 0xc0, 0x5a), (0xff, 0xe0, 0x8a), (0xff, 0xf2, 0xc0)]


def draw_shoji():
    """paper behind a wooden lattice: a frame, one upright and three rails"""
    rng = random.Random(11)
    img = canvas()
    for y in range(16):
        for x in range(16):
            put(img, x, y, PAPER[rng.choice([1, 2, 2, 2, 3])])
    for i in range(16):
        for edge in (0, 15):
            put(img, edge, i, WOOD[1 if i % 5 else 2])
            put(img, i, edge, WOOD[1 if i % 4 else 0])
        put(img, 7, i, WOOD[2])
        put(img, 8, i, WOOD[1])
        for rail in (4, 8, 12):
            put(img, i, rail, WOOD[2 if i % 6 else 3])
    # the paper a shade darker under each rail
    for rail in (4, 8, 12):
        for x in range(1, 15):
            if x not in (7, 8):
                put(img, x, rail + 1, PAPER[0])
    save(img, "shoji")


def draw_tatami():
    """woven rush: fine rows running one way, a dark cloth border down two edges (top), and the mat's edge (side)"""
    rng = random.Random(12)
    top = canvas()
    for y in range(16):
        for x in range(16):
            c = RUSH[2 if y % 2 else 1]
            if rng.random() < 0.12:
                c = RUSH[3 if y % 2 else 0]
            put(top, x, y, c)
    for y in range(16):
        for x in (0, 1, 14, 15):
            put(top, x, y, shade((0x2c, 0x33, 0x3a), 1.0 if (y + x) % 3 else 1.25))
    save(top, "tatami_top")
    side = canvas()
    for y in range(16):
        for x in range(16):
            c = STRAW[1 if (x + y // 3) % 2 else 2]
            if rng.random() < 0.1:
                c = STRAW[0]
            put(side, x, y, c)
    for x in range(16):
        put(side, x, 0, RUSH[2 if x % 2 else 1])
        put(side, x, 1, RUSH[0])
        put(side, x, 15, STRAW[0])
    save(side, "tatami_side")


def draw_plaster():
    """lime plaster: off-white, trowelled, a few faint hairline marks"""
    rng = random.Random(13)
    img = canvas()
    base = (0xe9, 0xe5, 0xd8)
    for y in range(16):
        for x in range(16):
            put(img, x, y, shade(base, 1.0 + rng.uniform(-0.035, 0.03)))
    for _ in range(5):
        x, y = rng.randrange(16), rng.randrange(16)
        for i in range(rng.randrange(3, 6)):
            put(img, (x + i) % 16, (y + (i // 3)) % 16, shade(base, 0.93))
    save(img, "plaster")


def draw_lacquer(name, pal):
    """planks under lacquer: four boards, hard seams, a long highlight on each"""
    rng = random.Random(len(name))
    img = canvas()
    for y in range(16):
        board = y // 4
        for x in range(16):
            c = pal[1]
            if y % 4 == 3:
                c = pal[0]
            elif y % 4 == 0:
                c = pal[2]
            if (x + board * 5) % 16 == 0:
                c = pal[0]
            put(img, x, y, shade(c, 1.0 + rng.uniform(-0.03, 0.03)))
        start = (board * 7 + 3) % 10
        for x in range(start, start + 4):
            put(img, x % 16, board * 4 + 1, pal[3])
    save(img, name)


def draw_paper_lantern():
    """the lantern's sheet: paper ribs lit from inside on the upper 10 rows, the dark caps on the rows below"""
    img = canvas()
    for y in range(10):
        for x in range(16):
            c = GLOW[2] if 2 <= y <= 7 else GLOW[1]
            if x % 4 == 0:
                c = GLOW[0]
            if y in (0, 9):
                c = shade(GLOW[0], 0.8)
            put(img, x, y, c)
    for x in range(5, 11):
        put(img, x, 4, GLOW[3])
        put(img, x, 5, GLOW[3])
    for y in range(10, 16):
        for x in range(16):
            put(img, x, y, WOOD[0] if (x + y) % 4 else WOOD[1])
    save(img, "paper_lantern")


def draw_stone_lantern():
    """the lantern's sheet: dressed stone on the upper 10 rows, its lit window on the rows below"""
    rng = random.Random(15)
    img = canvas()
    for y in range(10):
        for x in range(16):
            c = STONE[rng.choice([1, 1, 2, 2, 2, 3])]
            if y in (0, 9) or x in (0, 15):
                c = STONE[0]
            put(img, x, y, c)
    for y in range(10, 16):
        for x in range(16):
            c = STONE[1]
            if 3 <= x <= 12 and 11 <= y <= 14:
                c = GLOW[2] if x not in (7, 8) else STONE[0]
                if y in (12, 13) and x in (5, 6, 9, 10):
                    c = GLOW[3]
            put(img, x, y, c)
    save(img, "stone_lantern")


def draw_thatch():
    """bundled straw laid in courses: long stalks, each course's butt ends in shadow"""
    rng = random.Random(16)
    img = canvas()
    for y in range(16):
        for x in range(16):
            col = (x * 7 + 3) % 5
            c = STRAW[1 + (col + (y // 8)) % 3]
            if rng.random() < 0.18:
                c = STRAW[rng.choice([0, 4])]
            if y % 8 == 7:
                c = STRAW[0]
            elif y % 8 == 0:
                c = STRAW[4] if x % 3 else STRAW[3]
            put(img, x, y, c)
    save(img, "thatch")


def draw_ward_rope():
    """a twisted straw rope along the top, three folded paper streamers hanging from it; the rest is air"""
    img = canvas()
    for x in range(16):
        put(img, x, 1, STRAW[3 if x % 3 else 4])
        put(img, x, 2, STRAW[2 if (x + 1) % 3 else 1])
        put(img, x, 3, STRAW[0 if x % 3 else 1])
    for sx in (2, 7, 12):
        zig = [(0, 4), (0, 5), (1, 5), (1, 6), (0, 6), (0, 7), (-1, 7), (-1, 8), (0, 8), (0, 9), (1, 9), (1, 10), (0, 10), (0, 11)]
        for dx, y in zig:
            put(img, sx + dx + 1, y, PAPER[3 if (y + dx) % 2 else 2])
    save(img, "ward_rope")


def textures():
    draw_shoji()
    draw_tatami()
    draw_plaster()
    draw_lacquer("red_lacquered_planks", [(0x5c, 0x10, 0x12), (0x8e, 0x1b, 0x1c), (0xa6, 0x26, 0x24), (0xd8, 0x5a, 0x4c)])
    draw_lacquer("black_lacquered_planks", [(0x0e, 0x0d, 0x10), (0x1d, 0x1b, 0x20), (0x2a, 0x28, 0x2f), (0x62, 0x5f, 0x6c)])
    draw_paper_lantern()
    draw_stone_lantern()
    draw_thatch()
    draw_ward_rope()


# --- models and blockstates ---------------------------------------------------------------------------------------

def item(name, model=None):
    write(ASSETS + "items/" + name + ".json", {"model": {"type": "minecraft:model", "model": model or mdl(name)}})


def cube(name):
    write(ASSETS + "models/block/" + name + ".json", {"parent": "minecraft:block/cube_all", "textures": {"all": tex(name)}})
    write(ASSETS + "blockstates/" + name + ".json", {"variants": {"": {"model": mdl(name)}}})
    item(name)


BARS = ["post", "post_ends", "cap", "cap_alt", "side", "side_alt"]


def bars(name):
    t = tex(name)
    for part in BARS:
        write(ASSETS + "models/block/" + name + "_" + part + ".json", {
            "parent": "minecraft:block/template_bars_" + part, "textures": {"bars": t, "edge": t, "particle": t}})
    m = lambda part: mdl(name + "_" + part)
    f = {"north": "false", "east": "false", "south": "false", "west": "false"}
    only = lambda side: dict(f, **{side: "true"})
    write(ASSETS + "blockstates/" + name + ".json", {"multipart": [
        {"apply": {"model": m("post_ends")}},
        {"apply": {"model": m("post")}, "when": f},
        {"apply": {"model": m("cap")}, "when": only("north")},
        {"apply": {"model": m("cap"), "y": 90}, "when": only("east")},
        {"apply": {"model": m("cap_alt")}, "when": only("south")},
        {"apply": {"model": m("cap_alt"), "y": 90}, "when": only("west")},
        {"apply": {"model": m("side")}, "when": {"north": "true"}},
        {"apply": {"model": m("side"), "y": 90}, "when": {"east": "true"}},
        {"apply": {"model": m("side_alt")}, "when": {"south": "true"}},
        {"apply": {"model": m("side_alt"), "y": 90}, "when": {"west": "true"}},
    ]})
    write(ASSETS + "models/item/" + name + ".json", {"parent": "minecraft:item/generated", "textures": {"layer0": t}})
    item(name, NS + ":item/" + name)


def box(lo, hi, faces):
    """an element whose faces take explicit uv rectangles from #sheet: faces = {side: [u0, v0, u1, v1]}"""
    return {"from": list(lo), "to": list(hi), "faces": {side: {"uv": uv, "texture": "#sheet"} for side, uv in faces.items()}}


def all_faces(side_uv, end_uv):
    return {"north": side_uv, "south": side_uv, "east": side_uv, "west": side_uv, "up": end_uv, "down": end_uv}


def tatami():
    write(ASSETS + "models/block/tatami.json", {"parent": "minecraft:block/cube_column",
        "textures": {"end": tex("tatami_top"), "side": tex("tatami_side")}})
    # the mat lies flat whichever way it runs: axis only turns its weave
    write(ASSETS + "blockstates/tatami.json", {"variants": {
        "axis=y": {"model": mdl("tatami")},
        "axis=x": {"model": mdl("tatami"), "y": 90},
        "axis=z": {"model": mdl("tatami")}}})
    item("tatami")


def paper_lantern():
    paper, cap = [0, 0, 16, 10], [0, 10, 16, 16]
    standing = [
        box((5, 0, 5), (11, 1, 11), all_faces([0, 10, 12, 12], [0, 10, 12, 16])),
        box((4, 1, 4), (12, 11, 12), all_faces(paper, [0, 0, 16, 2])),
        box((5, 11, 5), (11, 12, 11), all_faces([0, 10, 12, 12], [0, 10, 12, 16])),
    ]
    hanging = [
        box((5, 3, 5), (11, 4, 11), all_faces([0, 10, 12, 12], [0, 10, 12, 16])),
        box((4, 4, 4), (12, 14, 12), all_faces(paper, [0, 0, 16, 2])),
        box((5, 14, 5), (11, 15, 11), all_faces([0, 10, 12, 12], [0, 10, 12, 16])),
        box((7.5, 15, 7.5), (8.5, 16, 8.5), all_faces([0, 14, 2, 16], [0, 14, 2, 16])),
    ]
    base = {"parent": "minecraft:block/block", "textures": {"sheet": tex("paper_lantern"), "particle": tex("paper_lantern")}}
    write(ASSETS + "models/block/paper_lantern.json", dict(base, elements=standing))
    write(ASSETS + "models/block/paper_lantern_hanging.json", dict(base, elements=hanging))
    write(ASSETS + "blockstates/paper_lantern.json", {"variants": {
        "hanging=false": {"model": mdl("paper_lantern")}, "hanging=true": {"model": mdl("paper_lantern_hanging")}}})
    item("paper_lantern")


def stone_lantern():
    stone, window = [0, 0, 16, 10], [0, 10, 16, 16]
    elements = [
        box((3, 0, 3), (13, 2, 13), all_faces([0, 0, 16, 4], [0, 0, 16, 10])),
        box((6, 2, 6), (10, 7, 10), all_faces([4, 0, 12, 10], [4, 2, 12, 8])),
        box((4, 7, 4), (12, 12, 12), all_faces(window, [0, 0, 16, 10])),
        box((2, 12, 2), (14, 14, 14), all_faces([0, 0, 16, 4], [0, 0, 16, 10])),
        box((6, 14, 6), (10, 16, 10), all_faces([4, 0, 12, 4], [4, 2, 12, 8])),
    ]
    write(ASSETS + "models/block/stone_lantern.json", {"parent": "minecraft:block/block",
        "textures": {"sheet": tex("stone_lantern"), "particle": tex("stone_lantern")}, "elements": elements})
    write(ASSETS + "blockstates/stone_lantern.json", {"variants": {"": {"model": mdl("stone_lantern")}}})
    item("stone_lantern")


def thatch_shapes():
    t = {"bottom": tex("thatch"), "top": tex("thatch"), "side": tex("thatch")}
    write(ASSETS + "models/block/thatch_slab.json", {"parent": "minecraft:block/slab", "textures": t})
    write(ASSETS + "models/block/thatch_slab_top.json", {"parent": "minecraft:block/slab_top", "textures": t})
    write(ASSETS + "blockstates/thatch_slab.json", {"variants": {
        "type=bottom": {"model": mdl("thatch_slab")}, "type=top": {"model": mdl("thatch_slab_top")}, "type=double": {"model": mdl("thatch")}}})
    item("thatch_slab")
    for part, parent in (("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")):
        write(ASSETS + "models/block/thatch_stairs" + part + ".json", {"parent": "minecraft:block/" + parent, "textures": t})
    turns = {"east": 0, "south": 90, "west": 180, "north": 270}
    variants = {}
    for facing, y in turns.items():
        for half in ("bottom", "top"):
            for shape in ("straight", "inner_left", "inner_right", "outer_left", "outer_right"):
                model = mdl("thatch_stairs" + ("" if shape == "straight" else "_" + shape.split("_")[0]))
                if shape == "straight":
                    turn = y
                elif shape.endswith("right"):
                    turn = y if half == "bottom" else y + 90
                else:
                    turn = y + 270 if half == "bottom" else y
                v = {"model": model}
                if half == "top":
                    v["x"] = 180
                if turn % 360:
                    v["y"] = turn % 360
                if len(v) > 1:
                    v["uvlock"] = True
                variants["facing=%s,half=%s,shape=%s" % (facing, half, shape)] = v
    write(ASSETS + "blockstates/thatch_stairs.json", {"variants": variants})
    item("thatch_stairs")


def assets():
    cube("plaster")
    cube("red_lacquered_planks")
    cube("black_lacquered_planks")
    cube("thatch")
    bars("shoji")
    bars("ward_rope")
    tatami()
    paper_lantern()
    stone_lantern()
    thatch_shapes()


# --- loot, recipes, tags, lang ------------------------------------------------------------------------------------

def loot(name):
    entry = {"type": "minecraft:item", "name": NS + ":" + name}
    if name.endswith("_slab"):
        # a double slab drops two (26.3's loot format: modifier, type, one condition)
        entry["modifier"] = [{"type": "minecraft:set_count", "count": 2, "condition": {
            "type": "minecraft:match_block", "blocks": NS + ":" + name, "state": {"type": "double"}}},
            {"type": "minecraft:explosion_decay"}]
        pool = {"entries": [entry], "rolls": 1}
    else:
        pool = {"condition": {"type": "minecraft:survives_explosion"}, "entries": [entry], "rolls": 1}
    write(DATA + "loot_table/blocks/" + name + ".json", {"type": "minecraft:block", "pools": [pool],
        "random_sequence": NS + ":blocks/" + name})


def shaped(name, pattern, key, count, category="building"):
    write(DATA + "recipe/" + name + ".json", {"type": "minecraft:crafting_shaped", "category": category, "pattern": pattern,
        "key": key, "result": {"count": count, "id": NS + ":" + name}})


def shapeless(name, ingredients, count, category="building"):
    write(DATA + "recipe/" + name + ".json", {"type": "minecraft:crafting_shapeless", "category": category,
        "ingredients": ingredients, "result": {"count": count, "id": NS + ":" + name}})


def recipes():
    mc = lambda n: "minecraft:" + n
    own = lambda n: NS + ":" + n
    shaped("shoji", ["SPS", "SPS"], {"S": mc("stick"), "P": mc("paper")}, 6)
    shaped("thatch", ["WW", "WW"], {"W": mc("wheat")}, 2)
    shaped("thatch_slab", ["TTT"], {"T": own("thatch")}, 6)
    shaped("thatch_stairs", ["T  ", "TT ", "TTT"], {"T": own("thatch")}, 4)
    shaped("tatami", ["SS", "TT"], {"S": mc("string"), "T": own("thatch")}, 2)
    shapeless("plaster", [mc("clay_ball"), mc("clay_ball"), mc("bone_meal"), mc("sand")], 4)
    for colour in ("red", "black"):
        shapeless(colour + "_lacquered_planks", ["#minecraft:planks"] * 4 + [mc(colour + "_dye"), mc("honeycomb")], 4)
    shaped("paper_lantern", ["SPS", "PTP", "SPS"], {"S": mc("stick"), "P": mc("paper"), "T": mc("torch")}, 2, "misc")
    shaped("stone_lantern", ["B", "T", "W"], {"B": mc("stone_brick_slab"), "T": mc("torch"), "W": mc("stone_brick_wall")}, 1, "misc")
    shaped("ward_rope", ["SSS", "P P"], {"S": mc("string"), "P": mc("paper")}, 3, "misc")


def merge_tag(path, values):
    have = {"replace": False, "values": []}
    if os.path.exists(path):
        with open(path) as f:
            have = json.load(f)
    for v in values:
        if v not in have["values"]:
            have["values"].append(v)
    write(path, have)


def tags():
    own = lambda names: [NS + ":" + n for n in names]
    merge_tag(RES + "data/minecraft/tags/block/mineable/axe.json", own(AXE))
    merge_tag(RES + "data/minecraft/tags/block/mineable/pickaxe.json", own(PICKAXE))
    merge_tag(RES + "data/minecraft/tags/block/mineable/hoe.json", own(HOE))
    merge_tag(RES + "data/minecraft/tags/block/slabs.json", own(["thatch_slab"]))
    merge_tag(RES + "data/minecraft/tags/block/stairs.json", own(["thatch_stairs"]))
    merge_tag(RES + "data/minecraft/tags/item/slabs.json", own(["thatch_slab"]))
    merge_tag(RES + "data/minecraft/tags/item/stairs.json", own(["thatch_stairs"]))
    merge_tag(RES + "data/minecraft/tags/item/planks.json", own(["red_lacquered_planks", "black_lacquered_planks"]))
    merge_tag(RES + "data/minecraft/tags/block/planks.json", own(["red_lacquered_planks", "black_lacquered_planks"]))


def lang():
    path = ASSETS + "lang/parts/blossom_blocks.json"
    write(path, {"block." + NS + "." + k: v for k, v in NAMES.items()})


if __name__ == "__main__":
    textures()
    assets()
    for n in NAMES:
        loot(n)
    recipes()
    tags()
    lang()
    print("blossom blocks: %d" % len(NAMES))
