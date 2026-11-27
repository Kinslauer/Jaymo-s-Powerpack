"""The swamp goblins' blocks and goods (com.jaymoskingdoms.SwampBlocks; village mods patch 8, Andy's pick rb_blocks):
red and brown toadstool caps, the toadstool stem, mossy oak planks, the swamp lantern (a jar of glowing marsh fungus,
standing or hung), the big lily pad, the umbrella leaf, and the gourd shaker, the eel and the cooked eel. Draws their
textures (original, 16 px, vanilla style: nothing copied) and writes block and item models, blockstates, loot tables,
recipes, the mineable tags and the lang part. Every model's elements name their textures by #variable (26.3 draws raw
sprite ids purple). Safe to run again. Run from the kingdoms project root: python3 -I tools/gen_swamp_blocks.py"""
from PIL import Image
import json
import os
import random

NS = "jaymoskingdoms"
RES = "src/main/resources/"
ASSETS = RES + "assets/" + NS + "/"
DATA = RES + "data/" + NS + "/"

BLOCKS = {
    "red_toadstool_cap": "Red Toadstool Cap",
    "brown_toadstool_cap": "Brown Toadstool Cap",
    "toadstool_stem": "Toadstool Stem",
    "mossy_oak_planks": "Mossy Oak Planks",
    "swamp_lantern": "Swamp Lantern",
    "big_lily_pad": "Big Lily Pad",
    "umbrella_leaf": "Umbrella Leaf",
}
ITEMS = {
    "gourd_shaker": "Gourd Shaker",
    "eel": "Raw Eel",
    "cooked_eel": "Cooked Eel",
}
AXE = ["red_toadstool_cap", "brown_toadstool_cap", "toadstool_stem", "mossy_oak_planks"]


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


def canvas(fill=(0, 0, 0, 0)):
    return Image.new("RGBA", (16, 16), fill)


def put(img, x, y, c, a=255):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), tuple(int(v) for v in c[:3]) + (a,))


def pick(ramp, r, i=None):
    return ramp[r.randrange(len(ramp)) if i is None else max(0, min(len(ramp) - 1, i))]


def save(img, name, kind="block"):
    path = ASSETS + "textures/" + kind + "/" + name + ".png"
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


RED = [(0x7a, 0x14, 0x12), (0x96, 0x1e, 0x18), (0xb2, 0x2a, 0x1e), (0xc8, 0x3a, 0x26)]
BROWN = [(0x5a, 0x3e, 0x26), (0x70, 0x4e, 0x30), (0x86, 0x60, 0x3c), (0x9a, 0x72, 0x48)]
SPOT = [(0xd8, 0xd0, 0xb8), (0xe8, 0xe2, 0xcc), (0xf4, 0xf0, 0xe0)]
STEM = [(0xb8, 0xae, 0x92), (0xc8, 0xbe, 0xa2), (0xd6, 0xce, 0xb4), (0xe2, 0xdc, 0xc4)]
OAK = [(0x6b, 0x52, 0x2f), (0x86, 0x68, 0x3c), (0x9c, 0x7c, 0x4a), (0xae, 0x8e, 0x58)]
MOSS = [(0x3c, 0x52, 0x1e), (0x4c, 0x66, 0x26), (0x5e, 0x7a, 0x2e), (0x70, 0x8c, 0x36)]
LEAF = [(0x2e, 0x52, 0x22), (0x3a, 0x66, 0x2a), (0x48, 0x7a, 0x32), (0x58, 0x8e, 0x3a)]
GLOW = [(0x9a, 0xd0, 0x4c), (0xb8, 0xe6, 0x5e), (0xd6, 0xf6, 0x7e), (0xee, 0xff, 0xb0)]
GLASS = [(0x6a, 0x7e, 0x6a), (0x84, 0x98, 0x80)]


def toadstool(name, ramp, seed):
    r = random.Random(seed)
    img = canvas()
    for x in range(16):
        for y in range(16):
            put(img, x, y, pick(ramp, r, 1 + (r.random() < 0.3) - (r.random() < 0.25)))
    # round pale spots, a few big and a few small, kept off the edge so caps tile without cut spots
    spots = [(3, 3, 2), (11, 4, 1), (7, 9, 2), (12, 12, 1), (2, 12, 1)]
    for cx, cy, rad in spots:
        for x in range(cx - rad, cx + rad + 1):
            for y in range(cy - rad, cy + rad + 1):
                if (x - cx) ** 2 + (y - cy) ** 2 <= rad * rad + 0.5:
                    put(img, x, y, pick(SPOT, r))
    save(img, name)


def stem():
    r = random.Random(41)
    side = canvas()
    for x in range(16):
        base = 1 + (x % 5 == 0) - (x % 7 == 3)
        for y in range(16):
            put(side, x, y, pick(STEM, r, base + (r.random() < 0.2) - (r.random() < 0.2)))
    save(side, "toadstool_stem")
    top = canvas()
    for x in range(16):
        for y in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            ring = int(d) % 3 == 0
            put(top, x, y, pick(STEM, r, 2 - ring + (r.random() < 0.15)))
    save(top, "toadstool_stem_top")


def planks():
    r = random.Random(77)
    img = canvas()
    for y in range(16):
        board = y // 4
        for x in range(16):
            edge = y % 4 == 3 or (x == (5 + board * 6) % 16)
            put(img, x, y, OAK[0] if edge else pick(OAK, r, 2 + (r.random() < 0.2) - (r.random() < 0.3)))
    # moss creeping in along the seams and from one corner
    for _ in range(46):
        x, y = r.randrange(16), r.randrange(16)
        if y % 4 == 3 or y % 4 == 0 or x + y < 9 or r.random() < 0.15:
            put(img, x, y, pick(MOSS, r))
    save(img, "mossy_oak_planks")


def lantern():
    r = random.Random(9)
    img = canvas()
    # the jar's glass (left, 6 x 7 at 0,0), the glow inside, the cap (6,0 4 x 4), the string (12,0 1 x 6)
    for x in range(6):
        for y in range(7):
            edge = x in (0, 5) or y == 6
            put(img, x, y, pick(GLASS, r) if edge else pick(GLOW, r, 2 + (r.random() < 0.4) - (r.random() < 0.4)))
    for x in range(6, 10):
        for y in range(4):
            put(img, x, y, pick(BROWN, r, 1 + (r.random() < 0.3)))
    for y in range(6):
        put(img, 12, y, (0x8a, 0x7a, 0x58))
    # the bottom, the jar's floor: moss
    for x in range(6):
        for y in range(8, 14):
            put(img, x, y, pick(MOSS, r))
    save(img, "swamp_lantern")


def lily():
    r = random.Random(5)
    img = canvas()
    for x in range(16):
        for y in range(16):
            dx, dy = x - 7.5, y - 7.5
            d = (dx * dx + dy * dy) ** 0.5
            notch = dx > 0 and abs(dy) < (dx * 0.45)
            if d <= 8.2 and not notch:
                vein = abs(dx) < 0.6 or abs(dy) < 0.6 or abs(abs(dx) - abs(dy)) < 0.7
                put(img, x, y, pick(LEAF, r, (3 if vein else 1 + (r.random() < 0.4)) + (d > 7.2) * -1))
    save(img, "big_lily_pad")


def umbrella():
    r = random.Random(13)
    leaf = canvas()
    for x in range(16):
        for y in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d <= 7.9:
                rib = (x - 7.5) * (y - 7.5) == 0 or abs(x - 7.5) < 0.6 or abs(y - 7.5) < 0.6
                put(leaf, x, y, pick(LEAF, r, (0 if rib else 2 + (r.random() < 0.3)) - (d > 7.0)))
    save(leaf, "umbrella_leaf")
    stalk = canvas()
    for y in range(16):
        for x in (7, 8):
            put(stalk, x, y, pick(LEAF, r, 1 + (x == 8)))
    save(stalk, "umbrella_leaf_stalk")


def shaker():
    r = random.Random(21)
    img = canvas()
    gourd = [(0xb0, 0x86, 0x3a), (0xc8, 0x9c, 0x44), (0xd8, 0xb0, 0x52), (0xe8, 0xc6, 0x6a)]
    for x in range(16):
        for y in range(16):
            d = ((x - 9.5) ** 2 + (y - 5.5) ** 2) ** 0.5
            if d <= 4.6:
                put(img, x, y, pick(gourd, r, 2 + (d < 2.5) - (d > 3.8)))
    for i in range(7):
        put(img, 6 - i // 2, 9 + i, (0x5a, 0x3e, 0x26))
        put(img, 7 - i // 2, 9 + i, (0x70, 0x4e, 0x30))
    for x, y in ((8, 4), (11, 6), (10, 3)):
        put(img, x, y, (0x7a, 0x14, 0x12))
    save(img, "gourd_shaker", "item")


def eel(name, body):
    r = random.Random(len(name))
    img = canvas()
    for i in range(13):
        x = 2 + i
        y = 8 + int(round(2.4 * __import__("math").sin(i * 0.7)))
        for dy in (-1, 0, 1):
            put(img, x, y + dy, pick(body, r, 1 + (dy == -1) - (dy == 1)))
    put(img, 14, 7 + int(round(2.4 * __import__("math").sin(12 * 0.7))), (0x10, 0x10, 0x10))
    save(img, name, "item")


def textures():
    toadstool("red_toadstool_cap", RED, 3)
    toadstool("brown_toadstool_cap", BROWN, 4)
    stem()
    planks()
    lantern()
    lily()
    umbrella()
    shaker()
    eel("eel", [(0x2c, 0x34, 0x22), (0x3e, 0x48, 0x2c), (0x52, 0x5e, 0x36)])
    eel("cooked_eel", [(0x5a, 0x34, 0x1a), (0x7a, 0x4a, 0x24), (0x96, 0x62, 0x32)])


# --- models ------------------------------------------------------------------------------------------------------

def face(uv, t):
    return {"uv": uv, "texture": t}


def item(name, model):
    write(ASSETS + "items/" + name + ".json", {"model": {"type": "minecraft:model", "model": model}})


def flat_item(name, texture=None):
    write(ASSETS + "models/item/" + name + ".json", {"parent": "minecraft:item/generated",
        "textures": {"layer0": texture or NS + ":item/" + name}})
    item(name, NS + ":item/" + name)


def cube(name):
    write(ASSETS + "models/block/" + name + ".json", {"parent": "minecraft:block/cube_all", "textures": {"all": tex(name)}})
    write(ASSETS + "blockstates/" + name + ".json", {"variants": {"": {"model": mdl(name)}}})
    item(name, mdl(name))


def pillar(name):
    write(ASSETS + "models/block/" + name + ".json", {"parent": "minecraft:block/cube_column",
        "textures": {"side": tex(name), "end": tex(name + "_top")}})
    write(ASSETS + "models/block/" + name + "_horizontal.json", {"parent": "minecraft:block/cube_column_horizontal",
        "textures": {"side": tex(name), "end": tex(name + "_top")}})
    write(ASSETS + "blockstates/" + name + ".json", {"variants": {
        "axis=y": {"model": mdl(name)},
        "axis=z": {"model": mdl(name + "_horizontal"), "x": 90},
        "axis=x": {"model": mdl(name + "_horizontal"), "x": 90, "y": 90}}})
    item(name, mdl(name))


def box(lo, hi, faces):
    return {"from": lo, "to": hi, "faces": faces}


def lantern_models():
    t = "#lantern"
    for hanging, dy in (("", 0), ("_hanging", 5)):
        glass = {k: face([0, 0, 6, 7], t) for k in ("north", "south", "east", "west")}
        glass["down"] = face([0, 8, 6, 14], t)
        glass["up"] = face([0, 8, 6, 14], t)
        cap = {k: face([6, 0, 10, 2], t) for k in ("north", "south", "east", "west")}
        cap["up"] = face([6, 0, 10, 4], t)
        cap["down"] = face([6, 0, 10, 4], t)
        elements = [box([5, dy, 5], [11, 7 + dy, 11], glass), box([6, 7 + dy, 6], [10, 9 + dy, 10], cap)]
        if hanging:
            for angle in (45, -45):
                elements.append({"from": [8, 9 + dy, 7.5], "to": [8, 16, 8.5], "shade": False,
                    "rotation": {"origin": [8, 12, 8], "axis": "y", "angle": angle},
                    "faces": {"east": face([12, 0, 13, 16 - 9 - dy], t), "west": face([12, 0, 13, 16 - 9 - dy], t)}})
        write(ASSETS + "models/block/swamp_lantern" + hanging + ".json", {"parent": "minecraft:block/block",
            "textures": {"particle": tex("swamp_lantern"), "lantern": tex("swamp_lantern")}, "elements": elements})
    variants = {}
    for h in ("false", "true"):
        for w in ("false", "true"):
            variants["hanging=%s,waterlogged=%s" % (h, w)] = {"model": mdl("swamp_lantern" + ("_hanging" if h == "true" else ""))}
    write(ASSETS + "blockstates/swamp_lantern.json", {"variants": variants})
    flat_item("swamp_lantern", NS + ":item/swamp_lantern")
    # its item: the jar drawn on its own
    img = Image.open(ASSETS + "textures/block/swamp_lantern.png")
    icon = canvas()
    for x in range(6):
        for y in range(7):
            icon.putpixel((5 + x, 6 + y), img.getpixel((x, y)))
    for x in range(4):
        for y in range(2):
            icon.putpixel((6 + x, 4 + y), img.getpixel((6 + x, y)))
    for y in range(4):
        icon.putpixel((8, y), img.getpixel((12, y)))
    save(icon, "swamp_lantern", "item")


def lily_models():
    t = "#pad"
    write(ASSETS + "models/block/big_lily_pad.json", {"parent": "minecraft:block/block", "ambientocclusion": False,
        "textures": {"particle": tex("big_lily_pad"), "pad": tex("big_lily_pad")},
        "elements": [box([0, 0.25, 0], [16, 0.25, 16], {"up": face([0, 0, 16, 16], t), "down": face([0, 16, 16, 0], t)})]})
    write(ASSETS + "blockstates/big_lily_pad.json", {"variants": {"": [{"model": mdl("big_lily_pad")},
        {"model": mdl("big_lily_pad"), "y": 90}, {"model": mdl("big_lily_pad"), "y": 180}, {"model": mdl("big_lily_pad"), "y": 270}]}})
    flat_item("big_lily_pad", tex("big_lily_pad"))


def umbrella_models():
    s, l = "#stalk", "#leaf"
    elements = []
    for angle in (45, -45):
        elements.append({"from": [8, 0, 0], "to": [8, 13, 16], "shade": False,
            "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": angle},
            "faces": {"east": face([0, 3, 16, 16], s), "west": face([0, 3, 16, 16], s)}})
    elements.append(box([-3, 13, -3], [19, 13.2, 19], {"up": face([0, 0, 16, 16], l), "down": face([0, 16, 16, 0], l)}))
    write(ASSETS + "models/block/umbrella_leaf.json", {"parent": "minecraft:block/block", "ambientocclusion": False,
        "textures": {"particle": tex("umbrella_leaf"), "stalk": tex("umbrella_leaf_stalk"), "leaf": tex("umbrella_leaf")},
        "elements": elements})
    write(ASSETS + "blockstates/umbrella_leaf.json", {"variants": {"": [{"model": mdl("umbrella_leaf")},
        {"model": mdl("umbrella_leaf"), "y": 90}]}})
    flat_item("umbrella_leaf", tex("umbrella_leaf"))


def assets():
    cube("red_toadstool_cap")
    cube("brown_toadstool_cap")
    cube("mossy_oak_planks")
    pillar("toadstool_stem")
    lantern_models()
    lily_models()
    umbrella_models()
    for name in ITEMS:
        flat_item(name)


# --- loot, recipes, tags, lang ------------------------------------------------------------------------------------

def loot(name):
    entry = {"type": "minecraft:item", "name": NS + ":" + name}
    write(DATA + "loot_table/blocks/" + name + ".json", {"type": "minecraft:block", "pools": [
        {"condition": {"type": "minecraft:survives_explosion"}, "entries": [entry], "rolls": 1}],
        "random_sequence": NS + ":blocks/" + name})


def shaped(name, pattern, key, count, category="building"):
    write(DATA + "recipe/" + name + ".json", {"type": "minecraft:crafting_shaped", "category": category, "pattern": pattern,
        "key": key, "result": {"count": count, "id": NS + ":" + name}})


def shapeless(name, ingredients, count, category="building"):
    write(DATA + "recipe/" + name + ".json", {"type": "minecraft:crafting_shapeless", "category": category,
        "ingredients": ingredients, "result": {"count": count, "id": NS + ":" + name}})


def cook(kind, name, time):
    write(DATA + "recipe/cooked_eel_from_" + name + ".json", {"type": "minecraft:" + kind, "category": "food",
        "cookingtime": time, "experience": 0.35, "ingredient": NS + ":eel", "result": {"id": NS + ":cooked_eel"}})


def recipes():
    mc = lambda n: "minecraft:" + n
    own = lambda n: NS + ":" + n
    shaped("red_toadstool_cap", ["MM", "MM"], {"M": mc("red_mushroom")}, 1)
    shaped("brown_toadstool_cap", ["MM", "MM"], {"M": mc("brown_mushroom")}, 1)
    shapeless("toadstool_stem", [mc("brown_mushroom"), mc("brown_mushroom"), mc("bone_meal"), mc("bone_meal")], 2)
    shapeless("mossy_oak_planks", [mc("oak_planks"), mc("vine")], 1)
    shaped("swamp_lantern", [" S ", "GLG", " G "], {"S": mc("string"), "G": mc("glass_pane"), "L": mc("glow_lichen")}, 1, "misc")
    shaped("big_lily_pad", ["LL", "LL"], {"L": mc("lily_pad")}, 1, "misc")
    shapeless("umbrella_leaf", [mc("big_dripleaf"), mc("fern")], 1, "misc")
    shaped("gourd_shaker", [" P", "S "], {"P": mc("pumpkin_seeds"), "S": mc("stick")}, 1, "misc")
    cook("smelting", "smelting", 200)
    cook("smoking", "smoking", 100)
    cook("campfire_cooking", "campfire_cooking", 600)


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
    merge_tag(RES + "data/minecraft/tags/block/planks.json", own(["mossy_oak_planks"]))
    merge_tag(RES + "data/minecraft/tags/item/planks.json", own(["mossy_oak_planks"]))
    merge_tag(RES + "data/minecraft/tags/block/sword_efficient.json", own(["umbrella_leaf"]))
    merge_tag(RES + "data/minecraft/tags/item/fishes.json", own(["eel", "cooked_eel"]))


def lang():
    names = {"block." + NS + "." + k: v for k, v in BLOCKS.items()}
    names.update({"item." + NS + "." + k: v for k, v in ITEMS.items()})
    write(ASSETS + "lang/parts/swamp_goblins.json", names)


if __name__ == "__main__":
    textures()
    assets()
    for n in BLOCKS:
        loot(n)
    recipes()
    tags()
    lang()
    print("swamp blocks: %d, items: %d" % (len(BLOCKS), len(ITEMS)))
