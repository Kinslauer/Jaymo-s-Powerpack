"""Carpentry's data (com.jaymoskingdoms.carpentry, after Better With Mods' mini blocks and furniture): the timber trim
(siding, moulding, corner) cut from vanilla's woods and four stones, wooden benches and tables, and the wattle, grate
and slat panels. Writes block and item models (the trim's parent models take the material's own vanilla texture; the
blockstates turn one model to every placement), blockstates, loot tables, recipes, the mineable tags, the lang names,
and draws the three panels' textures (original, on vanilla's stick and oak plank colours). Safe to run again. Run from
the kingdoms project root: python -I tools/gen_carpentry.py"""
from PIL import Image
import json
import os

NS = "jaymoskingdoms"
RES = "src/main/resources/"
ASSETS = RES + "assets/" + NS + "/"
DATA = RES + "data/" + NS + "/"

# name, the block it's cut from, its slab, its texture, wood?
WOODS = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak", "bamboo"]
MATERIALS = [(w, w + "_planks", w + "_slab", w + "_planks", True) for w in WOODS] + [
    ("stone", "stone", "stone_slab", "stone", False),
    ("cobblestone", "cobblestone", "cobblestone_slab", "cobblestone", False),
    ("stone_brick", "stone_bricks", "stone_brick_slab", "stone_bricks", False),
    ("brick", "bricks", "brick_slab", "bricks", False),
]
PANELS = {"wattle": "Wattle", "grate": "Grate", "slats": "Slats"}


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def title(name):
    return " ".join(w.capitalize() for w in name.split("_"))


# --- rotations: vanilla turns a model by x first, then y, each in quarter turns about the block's middle -------------

def rot_x(p):  # x: 90 takes north (-z) to down (-y)
    x, y, z = p
    return (x, z, -y)


def rot_y(p):  # y: 90 takes east (+x) to south (+z)
    x, y, z = p
    return (-z, y, x)


def turn(box, rx, ry):
    """box ((x0,y0,z0),(x1,y1,z1)) in pixels, turned by rx then ry degrees"""
    corners = []
    for p in box:
        q = tuple(v - 8 for v in p)
        for _ in range(rx // 90):
            q = rot_x(q)
        for _ in range(ry // 90):
            q = rot_y(q)
        corners.append(tuple(v + 8 for v in q))
    lo = tuple(min(a, b) for a, b in zip(*corners))
    hi = tuple(max(a, b) for a, b in zip(*corners))
    return lo, hi


def rotation_to(base, target):
    for rx in (0, 90, 180, 270):
        for ry in (0, 90, 180, 270):
            if turn(base, rx, ry) == target:
                return rx, ry
    raise ValueError("no turn from %s to %s" % (base, target))


AXIS = {"down": (1, 0), "up": (1, 16), "north": (2, 0), "south": (2, 16), "west": (0, 0), "east": (0, 16)}


def cut(*sides):
    """the box of the halves toward each side (as the Java's TrimBlock.cut)"""
    lo, hi = [0, 0, 0], [16, 16, 16]
    for s in sides:
        i, end = AXIS[s]
        if end == 16:
            lo[i] = 8
        else:
            hi[i] = 8
    return tuple(lo), tuple(hi)


def element(lo, hi, texture="#all"):
    faces = {}
    for face, (i, end) in AXIS.items():
        f = {"texture": texture}
        if (end == 0 and lo[i] == 0) or (end == 16 and hi[i] == 16):
            f["cullface"] = face
        faces[face] = f
    return {"from": list(lo), "to": list(hi), "faces": faces}


def variant(model, rx, ry):
    v = {"model": model}
    if rx:
        v["x"] = rx
    if ry:
        v["y"] = ry
    if rx or ry:
        v["uvlock"] = True
    return v


# --- the trim ---------------------------------------------------------------------------------------------------------

SIDING = ["down", "up", "north", "south", "west", "east"]
EDGES = [("down", "north"), ("down", "south"), ("down", "west"), ("down", "east"), ("up", "north"), ("up", "south"), ("up", "west"),
         ("up", "east"), ("north", "west"), ("north", "east"), ("south", "west"), ("south", "east")]
OCTANTS = [(y, z, x) for y in ("down", "up") for z in ("north", "south") for x in ("west", "east")]
# each shape: its property, its values (each with the sides it's cut toward), and the models it's drawn from (a
# suffix and the sides of the model's own piece; a beam can't be stood upright by the blockstate's x and y turns, so
# the moulding has an upright model of its own)
SHAPES = {
    "siding": ("facing", [(s, (s,)) for s in SIDING], [("", ("north",))]),
    "moulding": ("edge", [("_".join(e), e) for e in EDGES], [("", ("down", "north")), ("_upright", ("north", "west"))]),
    "corner": ("corner", [("_".join(o), o) for o in OCTANTS], [("", ("down", "north", "west"))]),
}


def trim():
    for shape, (prop, values, bases) in SHAPES.items():
        for suffix, base in bases:
            write(ASSETS + "models/block/template_" + shape + suffix + ".json", {
                "parent": "minecraft:block/block",
                "textures": {"particle": "#all"},
                "elements": [element(*cut(*base))]})
        for name, block, slab, texture, wood in MATERIALS:
            bid = name + "_" + shape
            for suffix, base in bases:
                write(ASSETS + "models/block/" + bid + suffix + ".json", {
                    "parent": NS + ":block/template_" + shape + suffix, "textures": {"all": "minecraft:block/" + texture}})
            variants = {}
            for value, sides in values:
                for suffix, base in bases:
                    try:
                        rx, ry = rotation_to(cut(*base), cut(*sides))
                    except ValueError:
                        continue
                    variants[prop + "=" + value] = variant(NS + ":block/" + bid + suffix, rx, ry)
                    break
                else:
                    raise ValueError("no model for " + bid + " " + value)
            write(ASSETS + "blockstates/" + bid + ".json", {"variants": variants})
            write(ASSETS + "items/" + bid + ".json", {"model": {"type": "minecraft:model", "model": NS + ":block/" + bid}})
            loot(bid)


def trim_recipes():
    for name, block, slab, texture, wood in MATERIALS:
        s, m, c = (NS + ":" + name + "_" + k for k in ("siding", "moulding", "corner"))
        group = lambda k: NS + ":" + k
        shapeless(name + "_siding", ["minecraft:" + block], s, 2, group("siding"))
        write(DATA + "recipe/" + name + "_siding_from_slabs.json", {
            "type": "minecraft:crafting_shaped", "category": "building", "group": group("siding"),
            "pattern": ["#", "#"], "key": {"#": "minecraft:" + slab}, "result": {"count": 2, "id": s}})
        shapeless(name + "_moulding", [s], m, 2, group("moulding"))
        shapeless(name + "_corner", [m], c, 2, group("corner"))
        shapeless(name + "_moulding_from_corners", [c, c], m, 1, group("moulding"))
        shapeless(name + "_siding_from_mouldings", [m, m], s, 1, group("siding"))
        shapeless(block + "_from_" + name + "_sidings", [s, s], "minecraft:" + block, 1, None)
        if not wood:
            for k, count in (("siding", 2), ("moulding", 4), ("corner", 8)):
                write(DATA + "recipe/" + name + "_" + k + "_stonecutting.json", {
                    "type": "minecraft:stonecutting", "ingredient": "minecraft:" + block,
                    "result": {"count": count, "id": NS + ":" + name + "_" + k}})


def shapeless(rid, ingredients, result, count, group):
    r = {"type": "minecraft:crafting_shapeless", "category": "building"}
    if group:
        r["group"] = group
    r["ingredients"] = ingredients
    r["result"] = {"count": count, "id": result}
    write(DATA + "recipe/" + rid + ".json", r)


def loot(bid):
    write(DATA + "loot_table/blocks/" + bid + ".json", {
        "type": "minecraft:block",
        "pools": [{"condition": {"type": "minecraft:survives_explosion"},
                   "entries": [{"type": "minecraft:item", "name": NS + ":" + bid}], "rolls": 1}],
        "random_sequence": NS + ":blocks/" + bid})


# --- benches and tables -----------------------------------------------------------------------------------------------

# (as FurnitureBlock: a bench's seat 5 to 8 px, a table's top 13 to 16, legs 2 px square, 1 px in from the corner)
FURNITURE = {"bench": (5, 8), "table": (13, 16)}
# a leg stands at a corner when neither side beside it joins: corner -> the two sides
LEGS = {"north_west": ("north", "west"), "north_east": ("north", "east"), "south_east": ("south", "east"), "south_west": ("south", "west")}


def leg_box(bottom, corner):
    lo, hi = (1, 0, 1), (3, bottom, 3)
    return turn((lo, hi), 0, {"north_west": 0, "north_east": 90, "south_east": 180, "south_west": 270}[corner])


def furniture():
    for kind, (bottom, top) in FURNITURE.items():
        top_el = element((0, bottom, 0), (16, top, 16), "#planks")
        leg_el = element((1, 0, 1), (3, bottom, 3), "#planks")
        write(ASSETS + "models/block/template_" + kind + "_top.json", {
            "parent": "minecraft:block/block", "textures": {"particle": "#planks"}, "elements": [top_el]})
        write(ASSETS + "models/block/template_" + kind + "_leg.json", {
            "textures": {"particle": "#planks"}, "elements": [leg_el]})
        legs = [element(*leg_box(bottom, c), "#planks") for c in LEGS]
        write(ASSETS + "models/block/template_" + kind + "_inventory.json", {
            "parent": "minecraft:block/block", "textures": {"particle": "#planks"}, "elements": [top_el] + legs})
        for wood in WOODS:
            bid = wood + "_" + kind
            tex = {"planks": "minecraft:block/" + wood + "_planks"}
            for part in ("top", "leg", "inventory"):
                write(ASSETS + "models/block/" + bid + "_" + part + ".json", {"parent": NS + ":block/template_" + kind + "_" + part, "textures": tex})
            parts = [{"apply": {"model": NS + ":block/" + bid + "_top"}}]
            for corner, (a, b) in LEGS.items():
                _, ry = rotation_to(((1, 0, 1), (3, bottom, 3)), leg_box(bottom, corner))
                parts.append({"apply": variant(NS + ":block/" + bid + "_leg", 0, ry), "when": {a: "false", b: "false"}})
            write(ASSETS + "blockstates/" + bid + ".json", {"multipart": parts})
            write(ASSETS + "items/" + bid + ".json", {"model": {"type": "minecraft:model", "model": NS + ":block/" + bid + "_inventory"}})
            loot(bid)
            pattern = ["###", "| |"] if kind == "bench" else ["###", "| |", "| |"]
            write(DATA + "recipe/" + bid + ".json", {
                "type": "minecraft:crafting_shaped", "category": "decorations", "group": NS + ":" + kind,
                "pattern": pattern, "key": {"#": "minecraft:" + wood + "_slab", "|": "minecraft:stick"},
                "result": {"count": 2, "id": NS + ":" + bid}})


# --- panels -----------------------------------------------------------------------------------------------------------

BARS = ["post_ends", "post", "cap", "cap_alt", "side", "side_alt"]


def panels():
    for name in PANELS:
        tex = NS + ":block/" + name
        for part in BARS:
            write(ASSETS + "models/block/" + name + "_" + part + ".json", {
                "parent": "minecraft:block/template_bars_" + part, "textures": {"bars": tex, "edge": tex, "particle": tex}})
        m = lambda part: NS + ":block/" + name + "_" + part
        f = {"north": "false", "east": "false", "south": "false", "west": "false"}
        only = lambda side: dict(f, **{side: "true"})
        parts = [
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
        ]
        write(ASSETS + "blockstates/" + name + ".json", {"multipart": parts})
        write(ASSETS + "models/item/" + name + ".json", {"parent": "minecraft:item/generated", "textures": {"layer0": tex}})
        write(ASSETS + "items/" + name + ".json", {"model": {"type": "minecraft:model", "model": NS + ":item/" + name}})
        loot(name)
    recipe = lambda name, pattern, key, count: write(DATA + "recipe/" + name + ".json", {
        "type": "minecraft:crafting_shaped", "category": "building", "pattern": pattern, "key": key,
        "result": {"count": count, "id": NS + ":" + name}})
    recipe("wattle", ["SSS", "SSS"], {"S": "minecraft:stick"}, 6)
    recipe("grate", ["S S", " S ", "S S"], {"S": "minecraft:stick"}, 4)
    recipe("slats", ["#", "#", "#"], {"#": "#minecraft:wooden_slabs"}, 6)
    draw_panels()


# vanilla's stick (darkest to lightest) and oak planks (darkest to lightest)
STICK = [(0x28, 0x1e, 0x0b), (0x49, 0x36, 0x15), (0x68, 0x4e, 0x1e), (0x89, 0x67, 0x27)]
HAZEL = [(0x4f, 0x3d, 0x22), (0x6e, 0x58, 0x35), (0x8c, 0x74, 0x4a), (0xa6, 0x8d, 0x5e)]
OAK = [(0x67, 0x50, 0x2c), (0x7e, 0x62, 0x37), (0x96, 0x74, 0x41), (0x9f, 0x84, 0x4d), (0xaf, 0x8f, 0x55), (0xb8, 0x94, 0x5f), (0xc2, 0x9d, 0x62)]


def canvas():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def put(img, x, y, c):
    img.putpixel((x % 16, y % 16), tuple(c) + (255,))


def draw_wattle():
    """woven hurdle: two stakes, withies woven over and under them in bands, a crack of light between bands"""
    img = canvas()
    stakes = (3, 11)
    for row, y in enumerate(range(1, 16, 3)):
        # each withy a 2 px band: lit top, shaded bottom, a knot here and there
        for x in range(16):
            put(img, x, y, HAZEL[3] if (x + row) % 7 else HAZEL[2])
            put(img, x, y + 1, HAZEL[1] if (x + 2 * row) % 5 else HAZEL[0])
        # the stakes show where the withy passes behind them (alternating each band and each stake)
        for i, sx in enumerate(stakes):
            if (row + i) % 2 == 0:
                for yy in (y, y + 1):
                    put(img, sx, yy, STICK[2])
                    put(img, sx + 1, yy, STICK[1])
            else:
                # the withy bulges over the stake: darker either side
                put(img, sx - 1, y + 1, HAZEL[0])
                put(img, sx + 2, y + 1, HAZEL[0])
    # the stakes in the gaps between bands
    for y in range(0, 16, 3):
        for sx in stakes:
            put(img, sx, y, STICK[3])
            put(img, sx + 1, y, STICK[1])
    return img


def draw_grate():
    """a lattice of square laths, 2 px wide every 4 px, the cross laths laid over the uprights"""
    img = canvas()
    for x0 in range(0, 16, 4):
        for y in range(16):
            put(img, x0, y, OAK[4] if y % 4 != 3 else OAK[2])
            put(img, x0 + 1, y, OAK[2])
    for y0 in range(0, 16, 4):
        for x in range(16):
            put(img, x, y0, OAK[6] if x % 4 != 1 else OAK[5])
            put(img, x, y0 + 1, OAK[3] if x % 4 != 1 else OAK[1])
    return img


def draw_slats():
    """upright boards 3 px wide with a 1 px gap: lit left edge, shaded right, a little grain"""
    img = canvas()
    grain = {(1, 3), (1, 9), (5, 6), (5, 13), (9, 1), (9, 11), (13, 4), (13, 14)}
    for x0 in range(0, 16, 4):
        for y in range(16):
            put(img, x0, y, OAK[5])
            put(img, x0 + 1, y, OAK[1] if (x0 + 1, y) in grain else OAK[4])
            put(img, x0 + 2, y, OAK[2])
        # a nail near the top and bottom of each board
        put(img, x0 + 1, 2, STICK[0])
        put(img, x0 + 1, 13, STICK[0])
    return img


def draw_panels():
    out = ASSETS + "textures/block/"
    os.makedirs(out, exist_ok=True)
    for name, draw in (("wattle", draw_wattle), ("grate", draw_grate), ("slats", draw_slats)):
        draw().save(out + name + ".png")


# --- tags and names ---------------------------------------------------------------------------------------------------

def tags():
    wood, stone = [], []
    for name, block, slab, texture, w in MATERIALS:
        for shape in SHAPES:
            (wood if w else stone).append(NS + ":" + name + "_" + shape)
    for wd in WOODS:
        wood += [NS + ":" + wd + "_bench", NS + ":" + wd + "_table"]
    wood += [NS + ":" + p for p in PANELS]
    write(DATA + "tags/block/carpentry_wood.json", {"replace": False, "values": wood})
    write(DATA + "tags/block/carpentry_stone.json", {"replace": False, "values": stone})
    for tool, tag in (("axe", "#" + NS + ":carpentry_wood"), ("pickaxe", "#" + NS + ":carpentry_stone")):
        path = RES + "data/minecraft/tags/block/mineable/" + tool + ".json"
        with open(path) as f:
            text = f.read()
        if tag not in text:
            # (a small edit of the shared file: the new entry after the last)
            end = text.rindex("]")
            body = text[:end].rstrip()
            text = body + ", " + json.dumps(tag) + text[len(body):] if body.endswith('"') else body + json.dumps(tag) + text[len(body):]
            json.loads(text)
            with open(path, "w") as f:
                f.write(text)


def names():
    lang = {}
    for name, block, slab, texture, wood in MATERIALS:
        for shape in SHAPES:
            lang["block." + NS + "." + name + "_" + shape] = title(name) + " " + title(shape)
    for wd in WOODS:
        for kind in FURNITURE:
            lang["block." + NS + "." + wd + "_" + kind] = title(wd) + " " + title(kind)
    for p, label in PANELS.items():
        lang["block." + NS + "." + p] = label
    path = ASSETS + "lang/en_us.json"
    with open(path) as f:
        lines = f.read().split("\n")
    have = json.loads("\n".join(lines))
    new = ["\t%s: %s," % (json.dumps(k), json.dumps(v)) for k, v in lang.items() if k not in have]
    if not new:
        return
    # after the sandbags (the other building blocks), so other additions at the end don't collide
    at = next(i for i, l in enumerate(lines) if '"block.' + NS + '.sandbag"' in l) + 1
    lines[at:at] = new
    with open(path, "w") as f:
        f.write("\n".join(lines))
    json.loads("\n".join(lines))


if __name__ == "__main__":
    trim()
    trim_recipes()
    furniture()
    panels()
    tags()
    names()
