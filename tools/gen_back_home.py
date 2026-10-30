"""Writes the assets and data of Track 7, "Back home" (SevTech picks; Powers 0.18.56, Kingdoms 0.7.387, Kitchen 0.5.38):

* Star metal: the Tinkering material (every part and tool layer recoloured from the iron ones, its models and a star_metal case
  in every item definition), the ore block and the ingot, molten star metal, the Starlight essence's symbol;
* Maw dust, the Starlit Lens, Verdant fruit, Glacia salt (items), the three trip-buff effect icons;
* the data: essences, the Starlit Lens infusion, the ore's loot table and tags;
* Jaymo's Kitchen's four far-world dishes (icons, item definitions, models, recipes, names).

Everything is our own art in the vanilla style: no black outlines (the darkest shade of the material outlines it), light from the
top left. The star-metal sprites come from the iron ones already in the repository, so run this after tools/tinkering_assets.py
(and tools/gen_steel.py), and point it at a 26.3 client jar (for vanilla's ingot, stone and bowl icons):

    python3 -I tools/gen_back_home.py ~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar

It is safe to run again (every file it writes is overwritten, every edit of an existing file is idempotent). 26.3 item and block
models name their faces through texture variables, never raw sprite ids; item definitions live in items/<id>.json.
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
sys.path.insert(0, HERE)
import gen_steel  # noqa: E402  (its folder and item lists: which sprites are material layers)

ROOT = os.path.dirname(HERE)
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymospowers")
DATA = os.path.join(RES, "data", "jaymospowers")
KRES = os.path.join(ROOT, "kitchen", "src", "main", "resources")
KASSETS = os.path.join(KRES, "assets", "jaymoskitchen")
KDATA = os.path.join(KRES, "data", "jaymoskitchen")
NS = "jaymospowers"
KNS = "jaymoskitchen"

# star metal: a pale violet-white, brighter than iron
HUE, SAT_LIGHT, SAT_DARK, BRIGHT = 0.73, 0.10, 0.42, 1.10
STARLIGHT = 0xC8D4FF
MOLTEN_STAR = 0xFFC9C2FF

DISHES = {
    # id: (name, recipe steps, perfect buffs: effect id, seconds, amplifier)
    "verdant_fruit_salad": "Verdant Fruit Salad",
    "glacia_salt_broth": "Glacia Salt Broth",
    "frosted_fruit_cup": "Frosted Fruit Cup",
    "salt_trail_bread": "Salt Trail Bread",
}

# the Powers items each dish's steps need (its recipe loads only when they exist)
NEEDS = {
    "verdant_fruit_salad": ["jaymospowers:verdant_fruit"],
    "glacia_salt_broth": ["jaymospowers:glacia_salt"],
    "frosted_fruit_cup": ["jaymospowers:verdant_fruit"],
    "salt_trail_bread": ["jaymospowers:verdant_fruit", "jaymospowers:glacia_salt"],
}

LANG = {
    # items and blocks
    "item.jaymospowers.maw_dust": "Maw Dust",
    "item.jaymospowers.maw_dust.tooltip": "Grey grit with a glint in it.",
    "item.jaymospowers.starlit_lens": "Starlit Lens",
    "item.jaymospowers.starlit_lens.tooltip": "Use on a pod to set it in the beacon mast.",
    "item.jaymospowers.verdant_fruit": "Verdant Fruit",
    "item.jaymospowers.glacia_salt": "Glacia Salt",
    "item.jaymospowers.star_metal_ingot": "Star Metal Ingot",
    "block.jaymospowers.star_metal_ore": "Star Metal Ore",
    # the material, the liquid, the traits
    "material.jaymospowers.star_metal": "Star Metal",
    "molten.jaymospowers.star_metal": "Molten Star Metal",
    "trait.jaymospowers.starlit": "Starlit",
    "trait.jaymospowers.starlit.desc": "Swings a tenth faster; a landing after a swing in the air does not hurt.",
    "trait.jaymospowers.cold_edge": "Cold Edge",
    "trait.jaymospowers.cold_edge.desc": "A hit slows what it strikes.",
    "trait.jaymospowers.hot_edge": "Hot Edge",
    "trait.jaymospowers.hot_edge.desc": "A hit sets what it strikes alight.",
    # the essence and its research
    "essence.jaymospowers.starlight": "Starlight",
    "research.jaymospowers.starlit_lens": "Glass That Reads the Dark",
    "research.jaymospowers.starlit_lens.text": "A spyglass, ground again with grit from the hole in the dark and a sliver of what fell. Set in a mast, it will mark the places on a world that are waiting.",
    # the effects the far dishes give
    "effect.jaymospowers.far_breath": "Far Breath",
    "effect.jaymospowers.far_ward": "Far Ward",
    "effect.jaymospowers.far_thrift": "Frugal Fire",
    # messages
    "message.jaymospowers.pod.lens_no_mast": "It needs a beacon mast to be set in.",
    "message.jaymospowers.pod.lens_fitted": "A lens is set in the mast already.",
    "message.jaymospowers.pod.lens_set": "The lens sits in the mast.",
    # JEI
    "jei.jaymospowers.quench_edge": "Quenching",
    "jei.jaymospowers.quench_edge.how": "Drop it in a water cauldron, then dip the hot piece.",
    "jei.jaymospowers.quench_edge.cold": "A Cold Edge: a hit slows what it strikes.",
    "jei.jaymospowers.quench_edge.hot": "A Hot Edge: a hit sets what it strikes alight.",
    "jei.jaymospowers.quench_edge.once": "It is used up; a piece takes one edge only.",
    "jei.jaymospowers.star_lens": "Starlit Lens",
    "jei.jaymospowers.star_lens.how": "Use the lens on a pod that has a beacon mast.",
    "jei.jaymospowers.star_lens.shows": "The flight shows the world's mission zones.",
    "jei.jaymospowers.found.maw_dust": "Taken from a wrecked pod's chest in the Maw, and sometimes shaken from the glass round the black hole. Sky hermits trade it for emeralds.",
    "jei.jaymospowers.found.verdant_fruit": "Sometimes falls from leaves broken on the Verdant World.",
    "jei.jaymospowers.found.glacia_salt": "Sometimes chipped from ice, snow and stone broken on Glacia.",
    "jei.jaymospowers.found.star_metal_ore": "Lies only in the craters fallen stars leave, near the towns. It melts in a smeltery, one ore to one ingot's worth.",
}
KITCHEN_LANG = {
    "item.jaymoskitchen.verdant_fruit_salad": "Verdant Fruit Salad",
    "item.jaymoskitchen.glacia_salt_broth": "Glacia Salt Broth",
    "item.jaymoskitchen.frosted_fruit_cup": "Frosted Fruit Cup",
    "item.jaymoskitchen.salt_trail_bread": "Salt Trail Bread",
}


# ---------------------------------------------------------------- helpers

def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def hexc(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def rgb(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255)


def mix(a, b, t):
    return tuple(int(round(a[i] * (1 - t) + b[i] * t)) for i in range(3))


def star_px(r, g, b):
    """One pixel of iron as star metal: its shading kept and lifted, violet deeper in the shadows."""
    _, _, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    v = min(1.0, v * BRIGHT + 0.03)
    r2, g2, b2 = colorsys.hsv_to_rgb(HUE, SAT_LIGHT + SAT_DARK * (1 - v), v)
    return round(r2 * 255), round(g2 * 255), round(b2 * 255)


def starred(im):
    im = im.convert("RGBA")
    px = im.load()
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = star_px(r, g, b) + (a,)
    return im


def molten(im):
    """Molten iron's frames as molten star metal: lavender-white at the bright end, violet in the dark."""
    im = im.convert("RGBA")
    px = im.load()
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = px[x, y]
            _, _, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            r2, g2, b2 = colorsys.hsv_to_rgb(0.72 + 0.04 * v, 0.75 - 0.6 * v, 0.55 + 0.45 * v)
            px[x, y] = (round(r2 * 255), round(g2 * 255), round(b2 * 255), a)
    return im


def ref(model):
    return {"model": {"type": "minecraft:model", "model": model}}


def flat_item(name, folder=None):
    """Item definition + flat model for textures/item/<folder/>name.png."""
    path = (folder + "/" if folder else "") + name
    write(os.path.join(ASSETS, "models", "item", path + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/" + path}})
    write(os.path.join(ASSETS, "items", name + ".json"), ref(NS + ":item/" + path))


def paint(rows, pal):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    im = Image.new("RGBA", (16, 16))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                im.putpixel((x, y), pal[ch] + (255,))
    return im


# ---------------------------------------------------------------- star metal as a Tinkering material

def tinker_material():
    """Every iron part and tool layer as star metal, its model, and a star_metal case beside iron's in each item definition."""
    tex = os.path.join(ASSETS, "textures", "item", "tinkering")
    models = os.path.join(ASSETS, "models", "item", "tinkering")
    count = 0
    for folder in gen_steel.TINKER_DIRS:
        for name in sorted(os.listdir(os.path.join(tex, folder))):
            if not name.endswith("_iron.png"):
                continue
            base = name[:-len("_iron.png")]
            starred(Image.open(os.path.join(tex, folder, name))).save(os.path.join(tex, folder, base + "_star_metal.png"))
            model = json.load(open(os.path.join(models, folder, base + "_iron.json")))
            model["textures"]["layer0"] = model["textures"]["layer0"][:-len("_iron")] + "_star_metal"
            write(os.path.join(models, folder, base + "_star_metal.json"), model)
            count += 1

    def add_case(node):
        if isinstance(node, list):
            for child in node:
                add_case(child)
            return
        if not isinstance(node, dict):
            return
        cases = node.get("cases")
        if node.get("property") == "minecraft:custom_model_data" and isinstance(cases, list):
            whens = [c.get("when") for c in cases]
            if "iron" in whens and "star_metal" not in whens:
                iron = cases[whens.index("iron")]
                text = json.dumps(iron["model"]).replace("_iron\"", "_star_metal\"")
                cases.append({"when": "star_metal", "model": json.loads(text)})
        for value in node.values():
            add_case(value)

    for item in gen_steel.TINKER_ITEMS:
        path = os.path.join(ASSETS, "items", item + ".json")
        definition = json.load(open(path))
        add_case(definition)
        write(path, definition)
    print("star metal sprites:", count)


# ---------------------------------------------------------------- items and blocks

def star_ore(stone):
    """Stone with a few clusters of star metal: pale lavender crystals, one pixel of white at the heart."""
    im = stone.copy().convert("RGBA")
    px = im.load()
    pale, mid, deep, white = (226, 220, 255), (176, 160, 238), (118, 100, 196), (255, 255, 255)
    for cx, cy in ((4, 3), (11, 6), (5, 11), (12, 12)):
        for dx, dy, c in ((0, 0, white), (-1, 0, pale), (1, 0, mid), (0, -1, pale), (0, 1, mid), (1, 1, deep), (-1, 1, deep)):
            x, y = cx + dx, cy + dy
            if 0 <= x < 16 and 0 <= y < 16:
                px[x, y] = c + (255,)
    return im


def maw_dust():
    pal = {"o": (30, 24, 44), "d": (58, 48, 82), "m": (96, 84, 128), "l": (146, 134, 182), "w": (244, 240, 255)}
    return paint([
        "................",
        "................",
        "...........w....",
        "................",
        ".......o........",
        "......oldo......",
        ".....oldmdo.....",
        "....oldmlmdo....",
        "...oldmmmmmdo.w.",
        "..olmmwmmlmmdo..",
        "..odmmmmmmmmdo..",
        ".oddmlmmmmldddo.",
        ".ooddddmmddddoo.",
        "...oooodddooo...",
        "................",
        "................",
    ], pal)


def starlit_lens():
    ring = [(236, 208, 120), (196, 152, 64), (140, 98, 38), (84, 56, 24)]
    glass = [(238, 246, 255), (176, 200, 246), (118, 140, 214)]
    im = Image.new("RGBA", (16, 16))
    cx, cy = 7.0, 7.0
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if d <= 6.4:
                lit = (x - cx) + (y - cy)
                if d > 5.0:
                    colour = ring[0] if lit < -2 else ring[1] if lit < 3 else ring[2]
                    if d > 5.9:
                        colour = ring[3] if lit > 2 else ring[2]
                elif d > 3.9:
                    colour = glass[2] if lit > 0 else glass[1]
                else:
                    colour = glass[0] if lit < 1 else glass[1]
                im.putpixel((x, y), colour + (255,))
    # the glint: a four-pointed star in the glass
    for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1), (2, 0), (0, -2)):
        im.putpixel((5 + dx + 1, 5 + dy + 1), (255, 255, 255, 255))
    # its handle, down and to the right
    for k in range(4):
        x, y = 11 + k, 11 + k
        if x < 16 and y < 16:
            im.putpixel((x, y), ring[1] + (255,))
            if x + 1 < 16:
                im.putpixel((x + 1, y), ring[2] + (255,))
    return im


def verdant_fruit():
    light, mid, dark, rim = (236, 236, 120), (150, 200, 72), (84, 140, 52), (44, 84, 38)
    leaf, leaf_dark, stem = (110, 188, 70), (60, 120, 46), (98, 70, 36)
    im = Image.new("RGBA", (16, 16))
    cx, cy = 7.5, 9.0
    for y in range(16):
        for x in range(16):
            d = math.hypot((x - cx) / 1.0, (y - cy) / 1.15)
            if d <= 5.2:
                lit = (x - cx) * 0.7 + (y - cy) * 0.7
                colour = rim if d > 4.5 and lit > 0 else light if lit < -2.4 else mid if lit < 1.5 else dark
                if d > 4.6:
                    colour = rim if lit > -1 else dark
                im.putpixel((x, y), colour + (255,))
    for y in (2, 3, 4):
        im.putpixel((8, y), stem + (255,))
    for x, y, c in ((9, 2, leaf), (10, 2, leaf), (11, 3, leaf), (9, 3, leaf_dark), (10, 3, leaf), (11, 2, leaf_dark)):
        im.putpixel((x, y), c + (255,))
    return im


def glacia_salt():
    pal = {"o": (86, 118, 160), "l": (246, 252, 255), "m": (200, 226, 246), "d": (150, 190, 226)}
    im = Image.new("RGBA", (16, 16))

    def crystal(cx, cy, rx, ry):
        for y in range(16):
            for x in range(16):
                dist = abs(x - cx) / rx + abs(y - cy) / ry
                if dist <= 1.0:
                    edge = dist > 0.78
                    colour = pal["o"] if edge and (x > cx or y > cy) else pal["l"] if x < cx - 0.5 and y < cy + 1 else pal["d"] if x > cx + 0.5 else pal["m"]
                    im.putpixel((x, y), colour + (255,))

    crystal(5.5, 9.5, 3.4, 4.6)
    crystal(10.5, 10.5, 3.2, 4.0)
    crystal(8.0, 6.0, 2.8, 4.2)
    return im


def star_ingot(iron):
    return starred(iron)


def effect_icons():
    """The three trip-buff effects' 18x18 icons."""
    out = {}
    dark = (40, 36, 58)
    # far breath: a bubble with a glint
    im = Image.new("RGBA", (18, 18))
    for y in range(18):
        for x in range(18):
            d = math.hypot(x - 8.5, y - 8.5)
            if d <= 7.0:
                im.putpixel((x, y), dark + (255,) if d > 6.0 else ((215, 240, 255, 255) if x + y < 15 else (140, 196, 240, 255)))
    for p in ((5, 5), (6, 5), (5, 6)):
        im.putpixel(p, (255, 255, 255, 255))
    out["far_breath"] = im
    # far ward: a disc half cold, half warm
    im = Image.new("RGBA", (18, 18))
    for y in range(18):
        for x in range(18):
            d = math.hypot(x - 8.5, y - 8.5)
            if d <= 7.0:
                if d > 6.0:
                    c = dark
                elif x < 8.5:
                    c = (150, 212, 255) if y < 9 else (110, 170, 236)
                else:
                    c = (255, 190, 100) if y < 9 else (238, 130, 60)
                im.putpixel((x, y), c + (255,))
    out["far_ward"] = im
    # frugal fire: a small flame with a coin of fuel beneath
    im = Image.new("RGBA", (18, 18))
    for y in range(18):
        for x in range(18):
            ry = (y - 8.0) / 7.0
            rx = (x - 8.5) / (4.6 * (1.0 - max(0.0, -ry) * 0.7) + 0.8)
            if rx * rx + ry * ry <= 1.0 and y >= 2:
                core = rx * rx + ry * ry < 0.35
                c = (255, 244, 170) if core else (242, 190, 70) if y > 7 else (232, 140, 50)
                if rx * rx + ry * ry > 0.82:
                    c = dark
                im.putpixel((x, y), c + (255,))
    out["far_thrift"] = im
    return out


def essence_symbol():
    """Starlight's glyph for the Codex: a four-pointed star with four small stars about it, in the style of the others."""
    px = set()
    c = 7.5
    for i in range(0, 41):
        t = (i / 40.0 - 0.5) * 2 * 6.4
        px.add((round(c + t), 7))
        px.add((round(c + t), 8))
        px.add((7, round(c + t)))
        px.add((8, round(c + t)))
    # thin the arms toward the tips
    for k in (-6, 6):
        for p in ((7 + k, 7), (7 + k, 8), (8 + k, 7), (8 + k, 8), (7, 7 + k), (8, 7 + k), (7, 8 + k), (8, 8 + k)):
            px.discard(p)
    for dx, dy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        for ox, oy in ((7, 7), (8, 7), (7, 8), (8, 8)):
            px.add((ox + dx * 2 - (1 if dx < 0 else 0), oy + dy * 2 - (1 if dy < 0 else 0)))
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px.add((x, y))
    px = {(x, y) for x, y in px if 0 <= x < 16 and 0 <= y < 16}
    base = rgb(STARLIGHT)
    light = mix(base, (255, 255, 255), 0.45)
    shade = mix(base, (0, 0, 0), 0.62)
    im = Image.new("RGBA", (16, 16))
    for x, y in px:
        if (x + 1, y + 1) not in px and 0 <= x + 1 < 16 and 0 <= y + 1 < 16:
            im.putpixel((x + 1, y + 1), shade + (255,))
    for x, y in px:
        lit = (x - 1, y - 1) not in px and ((x - 1, y) not in px or (x, y - 1) not in px)
        im.putpixel((x, y), (light if lit else base) + (255,))
    return im


def items(jar):
    with zipfile.ZipFile(jar) as z:
        def vanilla(rel):
            return Image.open(io.BytesIO(z.read("assets/minecraft/textures/" + rel + ".png"))).convert("RGBA")

        item_dir = os.path.join(ASSETS, "textures", "item")
        os.makedirs(item_dir, exist_ok=True)
        tink = os.path.join(item_dir, "tinkering")
        maw_dust().save(os.path.join(item_dir, "maw_dust.png"))
        starlit_lens().save(os.path.join(item_dir, "starlit_lens.png"))
        verdant_fruit().save(os.path.join(item_dir, "verdant_fruit.png"))
        glacia_salt().save(os.path.join(item_dir, "glacia_salt.png"))
        star_ingot(vanilla("item/iron_ingot")).save(os.path.join(tink, "star_metal_ingot.png"))
        block_dir = os.path.join(ASSETS, "textures", "block", "tinkering")
        star_ore(vanilla("block/stone")).save(os.path.join(block_dir, "star_metal_ore.png"))
        # molten star metal: molten iron's frames, lavender-white
        src = os.path.join(block_dir, "molten", "iron.png")
        hot = molten(Image.open(src))
        hot.save(os.path.join(block_dir, "molten", "star_metal.png"))
        with open(src + ".mcmeta") as f:
            meta = f.read()
        with open(os.path.join(block_dir, "molten", "star_metal.png.mcmeta"), "w", encoding="utf-8", newline="\n") as f:
            f.write(meta)
        hot.crop((0, 0, 16, 16)).save(os.path.join(ASSETS, "textures", "gui", "tinkering", "molten", "star_metal.png"))
        essence_symbol().save(os.path.join(ASSETS, "textures", "gui", "alchemy", "essence", "starlight.png"))
        for name, icon in effect_icons().items():
            path = os.path.join(ASSETS, "textures", "mob_effect", name + ".png")
            os.makedirs(os.path.dirname(path), exist_ok=True)
            icon.save(path)
        dishes(vanilla)

    for name in ("maw_dust", "starlit_lens", "verdant_fruit", "glacia_salt"):
        flat_item(name)
    write(os.path.join(ASSETS, "models", "item", "tinkering", "star_metal_ingot.json"),
          {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/tinkering/star_metal_ingot"}})
    write(os.path.join(ASSETS, "items", "star_metal_ingot.json"), ref(NS + ":item/tinkering/star_metal_ingot"))
    # the hot ingot of star metal (a case beside the other metals')
    hot_path = os.path.join(ASSETS, "items", "hot_ingot.json")
    hot_def = json.load(open(hot_path))
    hot_cases = hot_def["model"]["cases"]
    if not any(c["when"] == "star_metal" for c in hot_cases):
        hot_cases.append({"when": "star_metal", "model": json.loads(json.dumps(hot_cases[-1]["model"]).replace(hot_cases[-1]["when"] + "_ingot", "star_metal_ingot"))})
        write(hot_path, hot_def)
    write(os.path.join(ASSETS, "models", "block", "tinkering", "star_metal_ore.json"),
          {"parent": "minecraft:block/cube_all", "textures": {"all": NS + ":block/tinkering/star_metal_ore"}})
    write(os.path.join(ASSETS, "blockstates", "star_metal_ore.json"), {"variants": {"": {"model": NS + ":block/tinkering/star_metal_ore"}}})
    write(os.path.join(ASSETS, "items", "star_metal_ore.json"), ref(NS + ":block/tinkering/star_metal_ore"))


# ---------------------------------------------------------------- Jaymo's Kitchen's four dishes

def recolor(im, ramp):
    """Vanilla's stew bowl with a new soup: the three soup tones mapped onto ramp, dark to light."""
    soup = sorted([hexc("be785e"), hexc("cd8c6f"), hexc("cc9978")], key=lambda c: .299 * c[0] + .587 * c[1] + .114 * c[2])
    mapping = {c: ramp[min(len(ramp) - 1, i)] for i, c in enumerate(soup)}
    px = im.load()
    for y in range(16):
        for x in range(16):
            c = px[x, y]
            if c[3] and c[:3] in mapping:
                px[x, y] = mapping[c[:3]] + (255,)
    return im


def dots(im, pts, colour):
    px = im.load()
    for x, y in pts:
        px[x, y] = colour + (255,)
    return im


def dishes(vanilla):
    out = os.path.join(KASSETS, "textures", "item")
    os.makedirs(out, exist_ok=True)
    bowl = vanilla("item/mushroom_stew")
    # a salad of green and gold fruit
    salad = recolor(bowl.copy(), [hexc("7fb04a"), hexc("a2cc5e"), hexc("c6e486")])
    dots(salad, [(5, 6), (9, 6), (7, 7), (10, 7), (6, 7)], hexc("f2e46a"))
    dots(salad, [(8, 6), (4, 7), (11, 7)], hexc("5a9a3a"))
    salad.save(os.path.join(out, "verdant_fruit_salad.png"))
    # a pale broth with salt in it
    broth = recolor(bowl.copy(), [hexc("c8c2a8"), hexc("ddd8c0"), hexc("eeeadb")])
    dots(broth, [(5, 7), (8, 6), (10, 7), (7, 7)], hexc("f6fbff"))
    dots(broth, [(6, 6), (9, 7)], hexc("d98a3a"))
    broth.save(os.path.join(out, "glacia_salt_broth.png"))
    # a cup of cold fruit: blue-white with green and gold bits
    cup = recolor(bowl.copy(), [hexc("9ec6e4"), hexc("bedcf2"), hexc("dceefb")])
    dots(cup, [(5, 6), (8, 7), (10, 6), (6, 7)], hexc("f2e46a"))
    dots(cup, [(7, 6), (9, 7), (11, 7)], hexc("7fb04a"))
    dots(cup, [(4, 7), (8, 6)], hexc("ffffff"))
    cup.save(os.path.join(out, "frosted_fruit_cup.png"))
    # a loaf crusted with salt, a green fruit tucked in
    bread = vanilla("item/bread")
    px = bread.load()
    for y in range(16):
        for x in range(16):
            c = px[x, y]
            if c[3] and (x * 3 + y * 5) % 7 == 0 and sum(c[:3]) > 380:
                px[x, y] = (250, 252, 255, 255)
    dots(bread, [(7, 8), (8, 8)], hexc("7fb04a"))
    bread.save(os.path.join(out, "salt_trail_bread.png"))
    for name in DISHES:
        write(os.path.join(KASSETS, "models", "item", name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": KNS + ":item/" + name}})
        write(os.path.join(KASSETS, "items", name + ".json"), ref(KNS + ":item/" + name))
        write(os.path.join(KDATA, "recipe", "minigame", name + ".json"), {
            "fabric:load_conditions": [{
                "condition": "fabric:registry_contains", "registry": "minecraft:item",
                "values": NEEDS[name]}],
            "type": "jaymoskitchen:minigame", "category": "misc", "dish": name})


# ---------------------------------------------------------------- data and lang

def data():
    write(os.path.join(DATA, "alchemy", "essences", "back_home.json"), {"items": {
        "jaymospowers:maw_dust": {"starlight": 12, "shadow": 2},
        "jaymospowers:star_metal_ore": {"starlight": 14, "metal": 8, "crystal": 4},
        "jaymospowers:star_metal_ingot": {"starlight": 8, "metal": 8},
        "jaymospowers:starlit_lens": {"starlight": 16, "light": 8, "crystal": 6},
        "jaymospowers:verdant_fruit": {"plant": 4, "life": 2},
        "jaymospowers:glacia_salt": {"salt": 6, "frost": 3}}})
    write(os.path.join(DATA, "alchemy", "infusion", "starlit_lens.json"), {
        "centre": "minecraft:spyglass",
        "components": ["jaymospowers:star_metal_ingot", "jaymospowers:maw_dust", "jaymospowers:maw_dust", "minecraft:amethyst_shard", "minecraft:amethyst_shard"],
        "essences": {"starlight": 14, "light": 6},
        "instability": 5,
        "result": {"item": "jaymospowers:starlit_lens", "count": 1},
        "research": "starlit_lens",
        "flux": 2})
    write(os.path.join(DATA, "loot_table", "blocks", "star_metal_ore.json"), {
        "type": "minecraft:block", "random_sequence": NS + ":blocks/star_metal_ore", "pools": [{
            "rolls": 1, "entries": [{"type": "minecraft:item", "name": NS + ":star_metal_ore"}],
            "condition": {"type": "minecraft:survives_explosion"}}]})
    c = os.path.join(RES, "data", "c", "tags")
    write(os.path.join(c, "item", "ingots", "star_metal.json"), {"values": [NS + ":star_metal_ingot"]})
    mc = os.path.join(RES, "data", "minecraft", "tags")
    for tag in ("block/mineable/pickaxe", "block/needs_iron_tool"):
        path = os.path.join(mc, tag + ".json")
        old = json.load(open(path)) if os.path.exists(path) else {"values": []}
        if NS + ":star_metal_ore" not in old["values"]:
            old["values"].append(NS + ":star_metal_ore")
        write(path, old)


def lang():
    write(os.path.join(ASSETS, "lang", "parts", "back_home.json"), LANG)
    path = os.path.join(KASSETS, "lang", "en_us.json")
    table = json.load(open(path, encoding="utf-8"))
    table.update(KITCHEN_LANG)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(table, f, indent="\t", ensure_ascii=False)
        f.write("\n")


def main():
    if len(sys.argv) != 2 or not os.path.isfile(sys.argv[1]):
        sys.exit(__doc__)
    tinker_material()
    items(sys.argv[1])
    data()
    lang()


if __name__ == "__main__":
    main()
