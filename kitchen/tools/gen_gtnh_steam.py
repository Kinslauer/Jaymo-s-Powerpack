"""Steam and wind (GT New Horizons picks, patch 8; docs/GTNH-Plan.md, "Patch 8"): the art, models, blockstates, loot
tables, tags and recipes of the windmill's sails, the windsock, the firebox, the copper and bronze boilers, the steam
engine and its parts. The names and JEI pages are in lang/parts/gtnh_steam.json (written by hand).

All art is vanilla's own textures recoloured or drawn over (and the hemp sail's worn and torn looks are Kitchen's own
windmill sailcloth, holed and ragged), so point it at the 26.3 client jar:

    python3 -I kitchen/tools/gen_gtnh_steam.py .gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-clientOnly-41d41c9318/26.3/minecraft-clientOnly-41d41c9318-26.3.jar

The ids must match com.jaymoskitchen.mill.power.Power, SailKind and com.jaymoskitchen.mill.steam.Steam. Safe to run
again (every file is overwritten; the shared pickaxe tag is edited idempotently).
"""
import io
import json
import random
import sys
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
ASSETS = ROOT / "assets" / "jaymoskitchen"
DATA = ROOT / "data" / "jaymoskitchen"
NS = "jaymoskitchen"

SAILS = ["cloth", "wool", "leather", "paper"]
METALS = ["copper", "bronze"]
# what patches each sail (and repairs it at an anvil)
PATCH = {"cloth": ["jaymoskitchen:hemp_cloth"], "wool": ["#minecraft:wool"], "leather": ["minecraft:leather"],
         "paper": ["minecraft:paper"]}

JAR = None


# ---- helpers ------------------------------------------------------------------------------------------------------

def vanilla(path):
    """A 16x16 RGBA texture from the client jar (the first frame of an animated one)."""
    with JAR.open(f"assets/minecraft/textures/{path}.png") as f:
        img = Image.open(io.BytesIO(f.read())).convert("RGBA")
    return img.crop((0, 0, 16, 16))


def lum(p):
    return (0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]) / 255.0


def recolour(img, dark, light, contrast=1.0):
    """Every pixel's brightness (stretched over the image's own range) mapped onto dark..light."""
    px = [img.getpixel((x, y)) for y in range(16) for x in range(16)]
    ls = [lum(p) for p in px if p[3] > 0]
    lo, hi = min(ls), max(ls)
    out = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            p = img.getpixel((x, y))
            if p[3] == 0:
                continue
            t = (lum(p) - lo) / (hi - lo) if hi > lo else 0.5
            t = max(0.0, min(1.0, 0.5 + (t - 0.5) * contrast))
            out.putpixel((x, y), tuple(int(dark[i] + (light[i] - dark[i]) * t) for i in range(3)) + (p[3],))
    return out


def blend(img, rgb, amount, brighten=1.0):
    out = img.copy()
    for y in range(16):
        for x in range(16):
            p = img.getpixel((x, y))
            if p[3] == 0:
                continue
            c = [min(255, int((p[i] * (1 - amount) + rgb[i] * amount) * brighten)) for i in range(3)]
            out.putpixel((x, y), tuple(c) + (p[3],))
    return out


def shade(rgb, f):
    return tuple(max(0, min(255, int(c * f))) for c in rgb[:3]) + (255,)


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def save(img, kind, name):
    path = ASSETS / "textures" / kind / f"{name}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)


def item_def(name, model):
    write_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": model}})


def flat_item(name):
    write_json(ASSETS / "models" / "item" / f"{name}.json",
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
    item_def(name, f"{NS}:item/{name}")


def loot(name):
    write_json(DATA / "loot_table" / "blocks" / f"{name}.json", {
        "type": "minecraft:block",
        "pools": [{"condition": {"type": "minecraft:survives_explosion"},
                   "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}], "rolls": 1}],
        "random_sequence": f"{NS}:blocks/{name}"})


def shaped(name, pattern, key, result, count=1, conditions=None):
    data = {}
    if conditions:
        data["fabric:load_conditions"] = conditions
    data.update({"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern, "key": key,
                 "result": {"id": result, "count": count}})
    write_json(DATA / "recipe" / f"{name}.json", data)


def tag_add(path, values):
    """Adds values to a tag file (kept if already there)."""
    data = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {"replace": False, "values": []}
    for v in values:
        if v not in data["values"]:
            data["values"].append(v)
    write_json(path, data)


# ---- the sails ----------------------------------------------------------------------------------------------------

def sail_whole(kind):
    if kind == "cloth":
        # Kitchen's own windmill sailcloth (Better With Mods', MIT): the hemp sail keeps its look
        return Image.open(ASSETS / "textures" / "block" / "windmill_cloth.png").convert("RGBA").crop((0, 0, 16, 16))
    wool = vanilla("block/white_wool")
    if kind == "wool":
        return recolour(wool, (176, 168, 152), (240, 234, 220), 1.1)
    if kind == "leather":
        img = recolour(wool, (84, 50, 28), (150, 98, 58), 0.7)
        # stitched hems along both long edges
        for y in range(16):
            for x in (0, 15):
                img.putpixel((x, y), (62, 36, 20, 255) if y % 2 == 0 else (186, 150, 104, 255))
        return img
    # paper: fine and pale, in glued sheets
    img = recolour(vanilla("block/white_concrete_powder"), (206, 198, 170), (240, 234, 210), 0.5)
    for x in range(16):
        img.putpixel((x, 7), (190, 182, 152, 255))
        img.putpixel((x, 15), (190, 182, 152, 255))
    return img


def sail_worn(img, seed):
    """Holes worn through, and the outer edge frayed."""
    rng = random.Random(seed)
    out = img.copy()
    for _ in range(7):
        cx, cy = rng.randint(3, 13), rng.randint(0, 15)
        for dx, dy in ((0, 0), (1, 0), (0, 1)) if rng.random() < 0.6 else ((0, 0),):
            out.putpixel((min(15, cx + dx), (cy + dy) % 16), (0, 0, 0, 0))
    for y in range(16):
        if rng.random() < 0.5:
            out.putpixel((15, y), (0, 0, 0, 0))
        if rng.random() < 0.2:
            out.putpixel((14, y), (0, 0, 0, 0))
    return out


def sail_torn(img, seed):
    """In rags: a strip left on the spar side and a couple of ribbons streaming out."""
    rng = random.Random(seed)
    out = Image.new("RGBA", (16, 16))
    keep = [[False] * 16 for _ in range(16)]
    for y in range(16):
        edge = 2 + rng.randint(0, 2)
        for x in range(edge):
            keep[y][x] = True
    for start in (rng.randint(1, 4), rng.randint(9, 12)):
        reach = rng.randint(8, 13)
        for y in (start, start + 1):
            for x in range(reach - (y - start) * 2):
                keep[y][x] = True
    for y in range(16):
        for x in range(16):
            if keep[y][x]:
                out.putpixel((x, y), img.getpixel((x, y)))
    return out


def sail_icon(cloth, wood):
    """A sail furled on its yard: the yard across the top, the cloth hanging in folds under it."""
    img = Image.new("RGBA", (16, 16))
    light, dark = wood
    for x in range(1, 15):
        img.putpixel((x, 2), light)
        img.putpixel((x, 3), dark)
    for y in range(4, 14):
        right = 13 - (y - 4) // 3
        for x in range(3, right + 1):
            p = cloth.getpixel((x, y))
            if p[3] == 0:
                p = cloth.getpixel((x, 15 - y))
            if p[3] == 0:
                p = cloth.getpixel((8, y))
            f = 0.75 if x in (3, right) or y == 13 else (0.9 if (x + y) % 4 == 0 else 1.0)
            img.putpixel((x, y), shade(p, f))
    return img


# ---- the windsock -------------------------------------------------------------------------------------------------

def windsock_assets(wood):
    # the sock's cloth: pale wool, banded red and white by the renderer's tint
    save(recolour(vanilla("block/white_wool"), (214, 214, 214), (255, 255, 255), 0.8), "block", "windsock_cloth")
    write_json(ASSETS / "models" / "block" / "windsock.json", {
        "parent": "minecraft:block/block",
        "textures": {"pole": "minecraft:block/stripped_spruce_log", "cap": "minecraft:block/iron_block",
                     "particle": "minecraft:block/stripped_spruce_log"},
        "elements": [
            {"from": [7, 0, 7], "to": [9, 15, 9], "faces": {
                f: {"uv": [7, 0, 9, 15], "texture": "#pole"} for f in ("north", "south", "east", "west")} | {
                "down": {"uv": [7, 7, 9, 9], "texture": "#pole", "cullface": "down"}}},
            {"from": [6.5, 14, 6.5], "to": [9.5, 16, 9.5], "faces": {
                f: {"uv": [6, 0, 9, 2], "texture": "#cap"} for f in ("north", "south", "east", "west", "up", "down")}},
        ]})
    write_json(ASSETS / "blockstates" / "windsock.json", {"variants": {"": {"model": f"{NS}:block/windsock"}}})
    # its icon: the pole with the sock streaming out
    icon = Image.new("RGBA", (16, 16))
    light, dark = wood
    for y in range(2, 16):
        icon.putpixel((3, y), light)
        icon.putpixel((4, y), dark)
    for x in range(5, 15):
        half = 3 - (x - 5) // 4
        band = (200, 52, 42, 255) if ((x - 5) // 2) % 2 == 0 else (242, 238, 230, 255)
        for y in range(5 - half, 5 + half + 1):
            icon.putpixel((x, y), shade(band, 0.8 if y in (5 - half, 5 + half) else 1.0))
    save(icon, "item", "windsock")
    flat_item("windsock")
    loot("windsock")
    shaped("windsock", ["SCC", "S  ", "S  "], {"S": "minecraft:stick", "C": "#jaymoskitchen:sail_cloth"}, f"{NS}:windsock")


# ---- the firebox --------------------------------------------------------------------------------------------------

def firebox_assets():
    side = blend(vanilla("block/bricks"), (46, 30, 26), 0.3)
    save(side, "block", "firebox_side")
    iron = recolour(vanilla("block/iron_block"), (44, 44, 50), (104, 104, 114), 1.0)
    for lit in (False, True):
        front = side.copy()
        for y in range(5, 15):
            for x in range(3, 13):
                frame = x in (3, 12) or y in (5, 14)
                p = shade(iron.getpixel((x, y)), 0.8) if frame else iron.getpixel((x, y))
                if not frame and y in (8, 10, 12) and 4 <= x <= 11:
                    p = ((255, 196, 72, 255) if (x + y) % 3 else (255, 238, 150, 255)) if lit else (22, 20, 22, 255)
                front.putpixel((x, y), p)
        if lit:
            front = front.copy()
            for x in range(4, 12):
                front.putpixel((x, 14), (196, 70, 30, 255))
        save(front, "block", "firebox_front_on" if lit else "firebox_front")
    top = side.copy()
    bars = vanilla("block/iron_bars")
    for y in range(16):
        for x in range(16):
            if 2 <= x <= 13 and 2 <= y <= 13:
                b = bars.getpixel((x, y))
                top.putpixel((x, y), shade(b, 0.55) if b[3] > 0 else (30, 22, 20, 255))
    save(top, "block", "firebox_top")
    for lit in (False, True):
        name = "firebox_on" if lit else "firebox"
        write_json(ASSETS / "models" / "block" / f"{name}.json", {
            "parent": "minecraft:block/orientable_with_bottom",
            "textures": {"top": f"{NS}:block/firebox_top", "bottom": f"{NS}:block/firebox_side",
                         "side": f"{NS}:block/firebox_side",
                         "front": f"{NS}:block/firebox_front_on" if lit else f"{NS}:block/firebox_front"}})
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for lit in ("false", "true"):
            v = {"model": f"{NS}:block/firebox_on" if lit == "true" else f"{NS}:block/firebox"}
            if y:
                v["y"] = y
            variants[f"facing={facing},lit={lit}"] = v
    write_json(ASSETS / "blockstates" / "firebox.json", {"variants": variants})
    write_json(ASSETS / "models" / "item" / "firebox.json", {"parent": f"{NS}:block/firebox"})
    item_def("firebox", f"{NS}:item/firebox")
    loot("firebox")
    shaped("firebox", ["BIB", "B B", "BBB"], {"B": "minecraft:brick", "I": "minecraft:iron_bars"}, f"{NS}:firebox")


# ---- the boilers --------------------------------------------------------------------------------------------------

def metal_base(metal, name):
    img = vanilla(f"block/{name}")
    if metal == "bronze":
        img = recolour(img, (104, 70, 34), (220, 172, 96), 1.0)
    return img


def rivets(img, metal):
    out = img.copy()
    dark = (92, 48, 28, 255) if metal == "copper" else (78, 54, 24, 255)
    for x in range(1, 16, 3):
        out.putpixel((x, 1), dark)
        out.putpixel((x, 14), dark)
    for x in range(16):
        out.putpixel((x, 0), shade(img.getpixel((x, 0)), 0.7))
        out.putpixel((x, 15), shade(img.getpixel((x, 15)), 0.7))
    return out


def red_hot(img):
    return blend(img, (255, 76, 28), 0.5, 1.1)


def gauge(img, metal, level):
    """The gauge glass: a tube up the front, filled to the water's level (0 empty, 3 full)."""
    out = img.copy()
    frame = (70, 44, 26, 255) if metal == "copper" else (64, 46, 22, 255)
    top_water = {0: 99, 1: 11, 2: 7, 3: 3}[level]
    for y in range(2, 14):
        out.putpixel((6, y), frame)
        out.putpixel((9, y), frame)
        for x in (7, 8):
            if y in (2, 13):
                out.putpixel((x, y), frame)
            elif y >= top_water:
                out.putpixel((x, y), (70, 110, 210, 255) if x == 7 else (52, 86, 178, 255))
            else:
                out.putpixel((x, y), (196, 214, 222, 255) if x == 7 else (150, 170, 182, 255))
    return out


def boiler_assets():
    for metal in METALS:
        side = rivets(metal_base(metal, "copper_block"), metal)
        top = metal_base(metal, "cut_copper")
        for x in range(6, 10):
            for y in range(6, 10):
                if (x, y) not in ((6, 6), (9, 6), (6, 9), (9, 9)):
                    top.putpixel((x, y), (40, 30, 26, 255))
        for hot in (False, True):
            suffix = "_hot" if hot else ""
            s = red_hot(side) if hot else side
            t = red_hot(top) if hot else top
            save(s, "block", f"{metal}_boiler_side{suffix}")
            save(t, "block", f"{metal}_boiler_top{suffix}")
            for level in range(4):
                save(gauge(s, metal, level), "block", f"{metal}_boiler_front_{level}{suffix}")
                write_json(ASSETS / "models" / "block" / f"{metal}_boiler_{level}{suffix}.json", {
                    "parent": "minecraft:block/orientable_with_bottom",
                    "textures": {"top": f"{NS}:block/{metal}_boiler_top{suffix}", "bottom": f"{NS}:block/{metal}_boiler_top{suffix}",
                                 "side": f"{NS}:block/{metal}_boiler_side{suffix}",
                                 "front": f"{NS}:block/{metal}_boiler_front_{level}{suffix}"}})
        variants = {}
        for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            for level in range(4):
                for heat in range(3):
                    v = {"model": f"{NS}:block/{metal}_boiler_{level}{'_hot' if heat == 2 else ''}"}
                    if y:
                        v["y"] = y
                    variants[f"facing={facing},heat={heat},water={level}"] = v
        name = f"{metal}_boiler"
        write_json(ASSETS / "blockstates" / f"{name}.json", {"variants": variants})
        write_json(ASSETS / "models" / "item" / f"{name}.json", {"parent": f"{NS}:block/{metal}_boiler_3"})
        item_def(name, f"{NS}:item/{name}")
        loot(name)
    shaped("copper_boiler", ["CCC", "CVC", "CCC"], {"C": "#c:ingots/copper", "V": f"{NS}:safety_valve"}, f"{NS}:copper_boiler")
    # bronze (Jaymo's Powers' tin and bronze): only when something is a bronze ingot
    shaped("bronze_boiler", ["CCC", "CVC", "CCC"], {"C": "#c:ingots/bronze", "V": f"{NS}:safety_valve"}, f"{NS}:bronze_boiler",
           conditions=[{"condition": "fabric:tags_populated", "registry": "minecraft:item", "values": ["c:ingots/bronze"]}])


# ---- the engine ---------------------------------------------------------------------------------------------------

def box(frm, to, tex, faces=("north", "south", "east", "west", "up", "down"), rotation=None):
    """A model element with each face's uv cut from the box's own size."""
    x0, y0, z0 = frm
    x1, y1, z1 = to
    uvs = {"north": [16 - x1, 16 - y1, 16 - x0, 16 - y0], "south": [x0, 16 - y1, x1, 16 - y0],
           "west": [z0, 16 - y1, z1, 16 - y0], "east": [16 - z1, 16 - y1, 16 - z0, 16 - y0],
           "up": [x0, z0, x1, z1], "down": [x0, 16 - z1, x1, 16 - z0]}
    el = {"from": list(frm), "to": list(to),
          "faces": {f: {"uv": [round(max(0, min(16, v)), 3) for v in uvs[f]], "texture": tex} for f in faces}}
    if rotation:
        el["rotation"] = rotation
    return el


def engine_assets():
    iron = recolour(vanilla("block/iron_block"), (50, 52, 58), (122, 124, 132), 1.2)
    for x in range(16):
        iron.putpixel((x, 0), (40, 40, 46, 255))
        iron.putpixel((x, 15), (40, 40, 46, 255))
    save(iron, "block", "steam_engine_iron")
    cyl = vanilla("block/cut_copper")
    for y in range(16):
        for x in (0, 1, 14, 15):
            cyl.putpixel((x, y), shade(cyl.getpixel((x, y)), 0.7))
    save(cyl, "block", "steam_engine_cylinder")
    i, c = "#iron", "#cylinder"
    write_json(ASSETS / "models" / "block" / "steam_engine.json", {
        "parent": "minecraft:block/block",
        "textures": {"iron": f"{NS}:block/steam_engine_iron", "cylinder": f"{NS}:block/steam_engine_cylinder",
                     "particle": f"{NS}:block/steam_engine_iron"},
        "elements": [
            # the bed, the bearing the shaft runs in at the front, the cylinder lying back to front, its steam pipe
            box((0, 0, 0), (16, 3, 16), i),
            box((5, 3, 4), (11, 7, 7), i),
            box((7, 7, 4), (9, 9, 7), i),
            box((3, 3, 8), (13, 12, 15), c),
            box((2, 3, 7), (14, 4, 16), i),
            box((7, 12, 10), (9, 16, 12), c),
            box((6, 15, 9.5), (10, 16, 12.5), i),
            # the crank's connecting rod to the front
            box((7.5, 7, 7), (8.5, 8, 8), i),
        ]})
    # the flywheel turning on the shaft at the front (a part the renderer spins about the block's centre)
    corners = []
    for (cx, cy), angle in (((12.24, 12.24), -45), ((3.76, 12.24), 45), ((3.76, 3.76), -45), ((12.24, 3.76), 45)):
        corners.append(box((cx - 2.5, cy - 1, 1), (cx + 2.5, cy + 1, 4), i,
                           rotation={"origin": [cx, cy, 2.5], "axis": "z", "angle": angle}))
    write_json(ASSETS / "models" / "block" / "engine_flywheel.json", {
        "parent": "minecraft:block/block",
        "textures": {"iron": f"{NS}:block/steam_engine_iron", "particle": f"{NS}:block/steam_engine_iron"},
        "elements": [
            box((6, 6, 0), (10, 10, 4), i),
            box((7, 2, 2), (9, 14, 3), i),
            box((2, 7, 2), (14, 9, 3), i),
            box((5.5, 13, 1), (10.5, 15, 4), i),
            box((5.5, 1, 1), (10.5, 3, 4), i),
            box((1, 5.5, 1), (3, 10.5, 4), i),
            box((13, 5.5, 1), (15, 10.5, 4), i),
        ] + corners})
    # the engine's item: the bed and cylinder with the flywheel on
    write_json(ASSETS / "models" / "item" / "steam_engine.json", {
        "parent": f"{NS}:block/steam_engine",
        "elements": json.loads((ASSETS / "models" / "block" / "steam_engine.json").read_text())["elements"]
        + json.loads((ASSETS / "models" / "block" / "engine_flywheel.json").read_text())["elements"],
        "textures": {"iron": f"{NS}:block/steam_engine_iron", "cylinder": f"{NS}:block/steam_engine_cylinder",
                     "particle": f"{NS}:block/steam_engine_iron"}})
    item_def("steam_engine", f"{NS}:item/steam_engine")
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        v = {"model": f"{NS}:block/steam_engine"}
        if y:
            v["y"] = y
        variants[f"facing={facing}"] = v
    write_json(ASSETS / "blockstates" / "steam_engine.json", {"variants": variants})
    states = ASSETS / "blockstates" / "power_part.json"
    parts = json.loads(states.read_text(encoding="utf-8"))
    parts["variants"]["part=engine_flywheel"] = {"model": f"{NS}:block/engine_flywheel"}
    write_json(states, parts)
    loot("steam_engine")
    shaped("steam_engine", [" Y ", "PHP", "SSS"], {"Y": f"{NS}:steam_cylinder", "P": "#minecraft:planks", "H": f"{NS}:shaft",
                                                   "S": "minecraft:cobblestone"}, f"{NS}:steam_engine")


def part_icons():
    copper = vanilla("block/copper_block")
    lo, hi = (120, 62, 40, 255), (232, 150, 110, 255)
    mid = copper.getpixel((8, 8))
    iron = (150, 152, 160, 255)
    iron_dark = (82, 84, 92, 255)
    # the steam cylinder: a copper drum lying down with its end caps, and the piston rod out of one end
    img = Image.new("RGBA", (16, 16))
    for x in range(1, 12):
        for y in range(4, 12):
            p = hi if y == 4 else lo if y == 11 else shade(copper.getpixel((x, y)), 1.0 if y < 8 else 0.85)
            if x in (1, 11) or x in (4, 8):
                p = shade(p, 0.7)
            img.putpixel((x, y), p)
    for x in range(12, 16):
        img.putpixel((x, 7), iron)
        img.putpixel((x, 8), iron_dark)
    for y in (2, 3):
        img.putpixel((5, y), lo)
        img.putpixel((6, y), mid)
    save(img, "item", "steam_cylinder")
    flat_item("steam_cylinder")
    # the safety valve: a copper stub and dome, a lever across it and the weight that holds it shut
    img = Image.new("RGBA", (16, 16))
    for y in range(9, 16):
        for x in range(6, 10):
            img.putpixel((x, y), hi if x == 6 else lo if x == 9 else mid)
    for y in range(5, 9):
        for x in range(5, 11):
            if (x, y) not in ((5, 5), (10, 5)):
                img.putpixel((x, y), hi if y == 5 or x == 5 else lo if x == 10 else mid)
    for x in range(1, 14):
        img.putpixel((x, 4), iron)
    for y in range(2, 7):
        for x in range(11, 15):
            img.putpixel((x, y), iron_dark if x in (11, 14) or y in (2, 6) else (60, 62, 70, 255))
    save(img, "item", "safety_valve")
    flat_item("safety_valve")
    shaped("steam_cylinder", [" C ", "CPC", " C "], {"C": "#c:ingots/copper", "P": "minecraft:piston"}, f"{NS}:steam_cylinder")
    shaped("safety_valve", [" N ", "CLC"], {"N": "minecraft:iron_nugget", "L": "minecraft:lever", "C": "#c:ingots/copper"},
           f"{NS}:safety_valve")


# ---- main ---------------------------------------------------------------------------------------------------------

def main():
    global JAR
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    JAR = zipfile.ZipFile(sys.argv[1])
    stick = vanilla("item/stick")
    wood = (stick.getpixel((7, 8)), stick.getpixel((8, 8)))
    wood = tuple(w if w[3] else (137, 103, 61, 255) for w in wood)

    for seed, kind in enumerate(SAILS):
        whole = sail_whole(kind)
        if kind != "cloth":
            save(whole, "block", f"windmill_sail_{kind}")
        save(sail_worn(whole, seed * 17 + 3), "block", f"windmill_sail_{kind}_worn")
        save(sail_torn(whole, seed * 31 + 5), "block", f"windmill_sail_{kind}_torn")
        name = f"{kind}_sail"
        save(sail_icon(whole, wood), "item", name)
        flat_item(name)
        tag_add(DATA / "tags" / "item" / "sail_patch" / f"{kind}.json", PATCH[kind])
        material = PATCH[kind][0]
        shaped(name, ["SMM", "SMM"], {"S": "minecraft:stick", "M": material}, f"{NS}:{name}")
    # the windmill is now its hub and bare spars: its sails go on by hand
    shaped("windmill", ["SSS", "SLS", "SSS"], {"S": "minecraft:stick", "L": "#minecraft:logs"}, f"{NS}:windmill")

    windsock_assets(wood)
    firebox_assets()
    boiler_assets()
    engine_assets()
    part_icons()

    tag_add(DATA / "tags" / "item" / "boiler_water.json",
            ["#c:buckets/water", {"id": "jaymospowers:clay_water_bucket", "required": False}])
    tag_add(ROOT / "data" / "minecraft" / "tags" / "block" / "mineable" / "pickaxe.json",
            [f"{NS}:firebox", f"{NS}:copper_boiler", f"{NS}:bronze_boiler", f"{NS}:steam_engine"])


if __name__ == "__main__":
    main()
