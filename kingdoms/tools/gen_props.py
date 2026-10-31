"""Motion patch 9 props (Kingdoms 0.7.440): models, blockstates, item models, loot, recipes, lang and the MCA gift file.

Our own shapes on the game's own textures (no new art). Run from kingdoms/: python3 tools/gen_props.py
"""
import json
import os

NS = "jaymoskingdoms"
RES = "src/main/resources"
A = f"{RES}/assets/{NS}"
D = f"{RES}/data/{NS}"


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def cube(frm, to, tex, faces="nsewud", uv=None, tint=None, rot=None):
    """An element with the same texture variable on the faces asked for (uv per face optional)."""
    names = {"n": "north", "s": "south", "e": "east", "w": "west", "u": "up", "d": "down"}
    el = {"from": frm, "to": to, "faces": {}}
    for c in faces:
        face = {"texture": tex if isinstance(tex, str) else tex.get(c, tex.get("*"))}
        if uv and (uv.get(c) or uv.get("*")):
            face["uv"] = uv.get(c) or uv.get("*")
        el["faces"][names[c]] = face
    if rot:
        el["rotation"] = rot
    return el


TALL_DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, -2.5, 0], "scale": [0.42, 0.42, 0.42]},
    "ground": {"translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"scale": [0.4, 0.4, 0.4]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.3, 0.3, 0.3]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.3, 0.3, 0.3]},
}
SMALL_DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 2, 0], "scale": [0.9, 0.9, 0.9]},
    "ground": {"translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
    "fixed": {"scale": [0.8, 0.8, 0.8]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.5, 0.5, 0.5]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.6, 0.6, 0.6]},
}


def model(name, textures, elements, display=None):
    m = {"parent": "minecraft:block/block", "textures": textures, "elements": elements}
    if display:
        m["display"] = display
    write(f"{A}/models/block/{name}.json", m)


def facing_variants(model_of, extra=None):
    """Variants for the four facings (north = 0); extra: list of (state suffix, model)."""
    out = {}
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    for f, y in rot.items():
        for suffix, m in (extra or [("", model_of)]):
            key = f"facing={f}" + suffix
            v = {"model": f"{NS}:block/{m}"}
            if y:
                v["y"] = y
            out[key] = v
    return out


def simple_item(name, block_model):
    write(f"{A}/items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{block_model}"}})


def loot(name):
    write(f"{D}/loot_table/blocks/{name}.json", {
        "type": "minecraft:block",
        "pools": [{"condition": {"type": "minecraft:survives_explosion"}, "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}], "rolls": 1}],
        "random_sequence": f"{NS}:blocks/{name}"})


def recipe(name, pattern, key, count=1):
    write(f"{D}/recipe/{name}.json", {
        "type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
        "key": key, "result": {"count": count, "id": f"{NS}:{name}"}})


# ---- scarecrow: a post and crossbar, a hay body, a carved pumpkin head ----------------------------------------------
model("scarecrow", {
    "particle": "minecraft:block/hay_block_side", "log": "minecraft:block/oak_log", "log_top": "minecraft:block/oak_log_top",
    "hay": "minecraft:block/hay_block_side", "hay_top": "minecraft:block/hay_block_top",
    "face": "minecraft:block/carved_pumpkin", "pumpkin": "minecraft:block/pumpkin_side", "pumpkin_top": "minecraft:block/pumpkin_top"},
    [
        cube([7, 0, 7], [9, 24, 9], {"*": "#log", "u": "#log_top", "d": "#log_top"}, uv={"n": [7, 0, 9, 16], "s": [7, 0, 9, 16], "e": [7, 0, 9, 16], "w": [7, 0, 9, 16]}),
        cube([1, 18, 7.5], [15, 19.5, 8.5], "#log", uv={"*": [1, 7, 15, 8.5]}),
        cube([4.5, 11, 5.5], [11.5, 21, 10.5], {"*": "#hay", "u": "#hay_top", "d": "#hay_top"}),
        cube([4.5, 21, 4.5], [11.5, 28, 11.5], {"n": "#face", "s": "#pumpkin", "e": "#pumpkin", "w": "#pumpkin", "u": "#pumpkin_top", "d": "#pumpkin_top"}),
    ], TALL_DISPLAY)

# ---- gravestone: a mossy footing and a rounded stone --------------------------------------------------------------
model("gravestone", {"particle": "minecraft:block/stone", "stone": "minecraft:block/stone", "moss": "minecraft:block/mossy_cobblestone",
    "chisel": "minecraft:block/chiseled_stone_bricks"},
    [
        cube([2, 0, 5], [14, 2, 11], "#moss"),
        cube([3, 2, 6.5], [13, 12, 9.5], {"*": "#stone", "n": "#chisel"}, uv={"n": [3, 4, 13, 14]}),
        cube([4, 12, 6.5], [12, 13.5, 9.5], "#stone"),
        cube([5.5, 13.5, 6.5], [10.5, 14, 9.5], "#stone"),
    ], SMALL_DISPLAY)

# ---- lamp post: a stone footing, a dark post and arm, a lantern on top --------------------------------------------
model("lamp_post", {"particle": "minecraft:block/dark_oak_log", "post": "minecraft:block/dark_oak_log", "base": "minecraft:block/smooth_stone",
    "lantern": "minecraft:block/lantern"},
    [
        cube([5, 0, 5], [11, 2, 11], "#base"),
        cube([7, 2, 7], [9, 25, 9], "#post", uv={"*": [7, 0, 9, 16]}),
        cube([5, 25, 5], [11, 31, 11], {"*": "#lantern", "u": "#lantern", "d": "#lantern"},
             uv={"n": [0, 2, 6, 8], "s": [0, 2, 6, 8], "e": [0, 2, 6, 8], "w": [0, 2, 6, 8], "u": [0, 9, 6, 15], "d": [0, 9, 6, 15]}),
        cube([6, 31, 6], [10, 32, 10], "#lantern", uv={"*": [1, 10, 5, 11]}),
    ], TALL_DISPLAY)

# ---- candle stand: an iron foot, stem and arm, three candles (lit: the lit candle texture) ------------------------
for lit in (False, True):
    candle = "minecraft:block/candle_lit" if lit else "minecraft:block/candle"
    els = [cube([5, 0, 6], [11, 1, 10], "#iron"), cube([7.5, 1, 7.5], [8.5, 10, 8.5], "#iron"), cube([3, 10, 7.5], [13, 11, 8.5], "#iron")]
    for x in (3, 7, 11):
        els.append(cube([x, 11, 7], [x + 2, 16, 9], "#candle", uv={"n": [0, 8, 2, 13], "s": [0, 8, 2, 13], "e": [0, 8, 2, 13], "w": [0, 8, 2, 13], "u": [0, 6, 2, 8], "d": [0, 6, 2, 8]}))
        els.append({"from": [x + 0.5, 16, 8], "to": [x + 1.5, 17, 8], "rotation": {"angle": 45, "axis": "y", "origin": [x + 1, 16, 8]},
                    "faces": {"north": {"texture": "#candle", "uv": [0, 5, 1, 6]}, "south": {"texture": "#candle", "uv": [0, 5, 1, 6]}}})
    model("candle_stand" + ("_lit" if lit else ""), {"particle": "minecraft:block/iron_block", "iron": "minecraft:block/iron_block", "candle": candle},
          els, SMALL_DISPLAY)

# ---- plushies: soft wool shapes, black button eyes ----------------------------------------------------------------
def plush(name, wool, light, parts):
    model(name, {"particle": f"minecraft:block/{wool}", "wool": f"minecraft:block/{wool}", "light": f"minecraft:block/{light}",
                 "eye": "minecraft:block/black_wool"}, parts, SMALL_DISPLAY)


eyes = lambda y, z: [cube([6, y, z], [7, y + 1, z + 0.1], "#eye"), cube([9, y, z], [10, y + 1, z + 0.1], "#eye")]
plush("bear_plush", "brown_wool", "white_wool", [
    cube([5, 0, 6.5], [11, 6, 11], "#wool"), cube([5.5, 6, 5], [10.5, 10.5, 9.5], "#wool"),
    cube([5.5, 10.5, 7], [7, 12, 8], "#wool"), cube([9, 10.5, 7], [10.5, 12, 8], "#wool"),
    cube([7, 6.8, 4], [9, 8.3, 5], "#light"), cube([4, 2, 7.5], [5, 5.5, 9.5], "#wool"), cube([11, 2, 7.5], [12, 5.5, 9.5], "#wool"),
    cube([5.5, 0, 4.5], [7.5, 2, 6.5], "#wool"), cube([8.5, 0, 4.5], [10.5, 2, 6.5], "#wool")] + eyes(8.5, 4.9))
plush("bunny_plush", "white_wool", "pink_wool", [
    cube([5.5, 0, 7], [10.5, 5, 11], "#wool"), cube([6, 5, 5.5], [10, 9, 9.5], "#wool"),
    cube([6.3, 9, 7], [7.3, 13.5, 8], "#wool"), cube([8.7, 9, 7], [9.7, 13.5, 8], "#wool"),
    cube([7.5, 6, 5.3], [8.5, 7, 5.5], "#light"), cube([7, 1, 11], [9, 3, 12], "#wool"),
    cube([6, 0, 5.5], [7.5, 1.5, 7], "#wool"), cube([8.5, 0, 5.5], [10, 1.5, 7], "#wool")] + eyes(7.3, 5.4))
plush("cat_plush", "orange_wool", "white_wool", [
    cube([5.5, 0, 7], [10.5, 5, 11], "#wool"), cube([5.5, 5, 5.5], [10.5, 9, 9.5], "#wool"),
    cube([5.5, 9, 7], [7, 10.5, 8], "#wool"), cube([9, 9, 7], [10.5, 10.5, 8], "#wool"),
    cube([7, 5.3, 5.2], [9, 6.8, 5.5], "#light"), cube([7.5, 1, 11], [8.5, 2, 15], "#wool"),
    cube([6, 0, 5.5], [7.5, 1.5, 7], "#light"), cube([8.5, 0, 5.5], [10, 1.5, 7], "#light")] + eyes(7, 5.4))
plush("frog_plush", "lime_wool", "white_wool", [
    cube([4.5, 0, 5], [11.5, 4, 11], "#wool"), cube([5, 4, 5.5], [11, 6, 10], "#wool"),
    cube([5, 6, 6], [7.5, 8, 8], "#light"), cube([8.5, 6, 6], [11, 8, 8], "#light"),
    cube([5.7, 6.5, 5.9], [6.7, 7.5, 6.0], "#eye"), cube([9.3, 6.5, 5.9], [10.3, 7.5, 6.0], "#eye"),
    cube([3.5, 0, 6], [4.5, 1, 9], "#wool"), cube([11.5, 0, 6], [12.5, 1, 9], "#wool")])

# ---- planters: a window box and a pot, each with soil, and a small flower that grows ------------------------------
FLOWERS = ["dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley"]
SIZES = [0.35, 0.55, 0.75, 0.95]


def flower(x, y, z, size):
    w, h = 8 * size, 14 * size
    planes = []
    for angle in (45, -45):
        planes.append({"from": [x - w / 2, y, z], "to": [x + w / 2, y + h, z], "shade": False,
                       "rotation": {"angle": angle, "axis": "y", "origin": [x, y, z]},
                       "faces": {"north": {"texture": "#plant", "uv": [0, 0, 16, 16]}, "south": {"texture": "#plant", "uv": [0, 0, 16, 16]}}})
    return planes


def planter(name, textures, body, spots):
    model(name, textures, body, SMALL_DISPLAY)
    variants = []
    for fi, fl in enumerate(FLOWERS, start=1):
        for age, size in enumerate(SIZES):
            m = f"{name}_{fl}_{age}"
            els = list(body)
            for (x, y, z) in spots:
                els += flower(x, y, z, size)
            model(m, dict(textures, plant=f"minecraft:block/{fl}"), els)
            variants.append((f",flower={fi},age={age}", m))
    for age in range(4):
        variants.append((f",flower=0,age={age}", name))
    write(f"{A}/blockstates/{name}.json", {"variants": facing_variants(name, variants)})
    simple_item(name, name)


planter("window_box", {"particle": "minecraft:block/spruce_planks", "wood": "minecraft:block/spruce_planks", "soil": "minecraft:block/rooted_dirt"},
        [cube([0, 0, 9], [16, 1, 16], "#wood"), cube([0, 1, 9], [1, 7, 16], "#wood"), cube([15, 1, 9], [16, 7, 16], "#wood"),
         cube([1, 1, 9], [15, 7, 10], "#wood"), cube([1, 1, 15], [15, 7, 16], "#wood"), cube([1, 1, 10], [15, 6, 15], "#soil", faces="u")],
        [(4.5, 6, 12.5), (11.5, 6, 12.5)])
planter("pot_planter", {"particle": "minecraft:block/terracotta", "pot": "minecraft:block/terracotta", "soil": "minecraft:block/rooted_dirt"},
        [cube([3, 0, 3], [13, 1, 13], "#pot"), cube([3, 1, 3], [4, 8, 13], "#pot"), cube([12, 1, 3], [13, 8, 13], "#pot"),
         cube([4, 1, 3], [12, 8, 4], "#pot"), cube([4, 1, 12], [12, 8, 13], "#pot"), cube([4, 1, 4], [12, 7, 12], "#soil", faces="u")],
        [(8, 7, 8)])

# ---- blockstates and item models for the rest ---------------------------------------------------------------------
for name in ["scarecrow", "gravestone", "lamp_post", "bear_plush", "bunny_plush", "cat_plush", "frog_plush"]:
    write(f"{A}/blockstates/{name}.json", {"variants": facing_variants(name)})
    simple_item(name, name)
write(f"{A}/blockstates/candle_stand.json", {"variants": facing_variants("candle_stand", [(",lit=false", "candle_stand"), (",lit=true", "candle_stand_lit")])})
simple_item("candle_stand", "candle_stand")

ALL = ["scarecrow", "gravestone", "lamp_post", "candle_stand", "bear_plush", "bunny_plush", "cat_plush", "frog_plush", "window_box", "pot_planter"]
for name in ALL:
    loot(name)

recipe("scarecrow", [" P ", "SHS", " S "], {"P": "minecraft:carved_pumpkin", "H": "minecraft:hay_block", "S": "minecraft:stick"})
recipe("gravestone", [" C ", "CCC", "MMM"], {"C": "minecraft:cobblestone", "M": "minecraft:mossy_cobblestone"})
recipe("lamp_post", ["L", "B", "B"], {"L": "minecraft:lantern", "B": "minecraft:iron_bars"})
recipe("candle_stand", ["CCC", " I ", " I "], {"C": "#minecraft:candles", "I": "minecraft:iron_ingot"})
for name, wool in [("bear_plush", "brown_wool"), ("bunny_plush", "white_wool"), ("cat_plush", "orange_wool"), ("frog_plush", "lime_wool")]:
    recipe(name, [" W ", "WSW"], {"W": f"minecraft:{wool}", "S": "minecraft:string"})
recipe("window_box", ["P P", "PDP"], {"P": "minecraft:spruce_planks", "D": "minecraft:dirt"}, 2)
recipe("pot_planter", ["T T", "TDT"], {"T": "minecraft:terracotta", "D": "minecraft:dirt"}, 2)

# ---- MCA: plushies are gifts children love, and the married like ---------------------------------------------------
write(f"{RES}/data/mca/gifts/plushies.json", {
    "conditions": [{"age_group": "child", "satisfaction_boost": 20}, {"is_married": True, "satisfaction_boost": 10}],
    "items": {f"{NS}:{p}": 8 for p in ["bear_plush", "bunny_plush", "cat_plush", "frog_plush"]}})

# ---- lang ----------------------------------------------------------------------------------------------------------
NAMES = {"scarecrow": "Scarecrow", "gravestone": "Gravestone", "lamp_post": "Lamp Post", "candle_stand": "Candle Stand",
         "bear_plush": "Bear Plush", "bunny_plush": "Bunny Plush", "cat_plush": "Cat Plush", "frog_plush": "Frog Plush",
         "window_box": "Window Box", "pot_planter": "Pot Planter"}
lang_path = f"{A}/lang/en_us.json"
with open(lang_path) as f:
    lang = json.load(f)
added = {f"block.{NS}.{k}": v for k, v in NAMES.items() if f"block.{NS}.{k}" not in lang}
if added:
    text = open(lang_path).read().rstrip()
    assert text.endswith("}")
    body = text[:-1].rstrip()
    lines = "".join(f',\n\t{json.dumps(k)}: {json.dumps(v)}' for k, v in added.items())
    open(lang_path, "w").write(body + lines + "\n}\n")
print("props:", len(ALL), "blocks")
