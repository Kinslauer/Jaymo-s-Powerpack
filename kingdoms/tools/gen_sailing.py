"""Writes Jaymo's Kingdoms' sailing assets (0.7.4): the ship's helm (a spruce wheel on a post, from vanilla's own block
textures) and the sail (0.7.6: bellied sailcloth on a yard, one model for each way its neighbours join it, set and
furled, and our own sailcloth and rope textures), their blockstates, item models, loot tables and recipes.
(0.7.12) Also oars (shipped, their lock, and the oar alone, which the ship sweeps), the ship's gun, the frames build
kits lay down, round shot and the kits themselves (our own textures, drawn here).
Run from kingdoms/: python3 -I tools/gen_sailing.py"""
import json
import math
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymoskingdoms")
DATA = os.path.join(RES, "data")


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def cube(fr, to, tex, faces="nsewud"):
    names = {"n": "north", "s": "south", "e": "east", "w": "west", "u": "up", "d": "down"}
    return {"from": fr, "to": to, "faces": {names[f]: {"texture": tex} for f in faces}}


def facing_variants(model, turns):
    return {"variants": {f"facing={f}": ({"model": model, "y": y} if y else {"model": model}) for f, y in turns.items()}}

# ---- the sail ----
# A sail faces north in its model and bellies out that way (toward -z), hanging from a yard across its top just in
# front of its block (and so in front of a mast beside it, a fence or a whole log). Its sheet's
# depth at (x, y) is BASE + BELLY * across(x) * down(y): the most in the middle of the sheet, less at its free edges,
# none at the yard. A mast beside it (left is west, seen from behind) is crossed by the sheet and the yard, 8 pixels in.
BASE, BELLY = 8.5, 5.5
EDGE = 0.35
CLOTH, THICK, STEP = "#cloth", 0.5, 2


def sail_texture():
    rng = random.Random(7)
    os.makedirs(os.path.join(ASSETS, "textures", "block"), exist_ok=True)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            n = rng.randint(-5, 5)
            r, g, b = 236 + n, 229 + n, 208 + n
            if x % 8 == 0:
                r, g, b = r - 22, g - 24, b - 28  # the seams between its panels of canvas
            elif y % 4 == 1:
                r, g, b = r - 5, g - 5, b - 6  # the weave
            img.putpixel((x, y), (r, g, b, 255))
    img.save(os.path.join(ASSETS, "textures", "block", "sailcloth.png"))
    rope = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            strand = (x + y) % 4
            base = [(150, 117, 74), (176, 140, 92), (189, 154, 104), (128, 98, 60)][strand]
            n = rng.randint(-6, 6)
            rope.putpixel((x, y), (base[0] + n, base[1] + n, base[2] + n, 255))
    rope.save(os.path.join(ASSETS, "textures", "block", "sail_rope.png"))


def uv_box(x0, y0, x1, y1):
    u0 = x0 % 16
    u1 = u0 + (x1 - x0)
    if u1 > 16:
        u0, u1 = 16 - (x1 - x0), 16
    return [round(u0, 3), round(16 - y1, 3), round(u1, 3), round(16 - y0, 3)]


def element(fr, to, tex, uv=None, rotation=None):
    faces = {}
    for f in ("north", "south", "east", "west", "up", "down"):
        face = {"texture": tex}
        if uv is not None and f in ("north", "south"):
            face["uv"] = uv
        faces[f] = face
    e = {"from": [round(v, 3) for v in fr], "to": [round(v, 3) for v in to], "faces": faces}
    if rotation:
        e["rotation"] = rotation
    return e


def across(left, right, u):
    if left == "none" and right == "none":
        return EDGE + (1 - EDGE) * math.sin(math.pi * u)
    if left == "none":
        return EDGE + (1 - EDGE) * math.sin(math.pi / 2 * u)
    if right == "none":
        return EDGE + (1 - EDGE) * math.sin(math.pi / 2 * (1 - u))
    return 1.0


def down(up, below, t):
    if not up and not below:
        return math.sin(0.75 * math.pi * t)
    if not up:
        return math.sin(math.pi / 2 * t)
    if not below:
        return 1 - 0.3 * t * t
    return 1.0


def sail_model(left, right, up, below, furled):
    x0 = -8 if left == "mast" else 0
    x1 = 24 if right == "mast" else 16

    def z(x, y):
        u = (x - x0) / (x1 - x0)
        return 8 - (BASE + BELLY * across(left, right, u) * down(up, below, (16 - y) / 16))

    elements = []
    if not furled:
        x = x0
        while x < x1:
            for y in range(0, 16, STEP):
                xa, xb, ya, yb = x, x + STEP, y, y + STEP
                zc = (z(xa, ya) + z(xb, ya) + z(xa, yb) + z(xb, yb)) / 4
                dzdy = ((z(xa, yb) + z(xb, yb)) - (z(xa, ya) + z(xb, ya))) / 2 / STEP
                dzdx = ((z(xb, ya) + z(xb, yb)) - (z(xa, ya) + z(xa, yb))) / 2 / STEP
                pitch, yaw = math.degrees(math.atan(dzdy)), -math.degrees(math.atan(dzdx))
                # stretched to close the gaps its tilt leaves
                hx = STEP / 2 / math.cos(math.radians(yaw)) + 0.05
                hy = STEP / 2 / math.cos(math.radians(pitch)) + 0.05
                cx, cy = (xa + xb) / 2, (ya + yb) / 2
                elements.append(element([cx - hx, cy - hy, zc - THICK / 2], [cx + hx, cy + hy, zc + THICK / 2], CLOTH,
                                        uv_box(xa, ya, xb, yb),
                                        {"origin": [cx, cy, zc], "x": round(pitch, 2), "y": round(yaw, 2), "z": 0}))
            x += STEP
        # the sheets: ropes from its free lower corners, down and aft
        if not below:
            for side, cx in (("left", x0), ("right", x1)):
                if (left if side == "left" else right) == "none":
                    cz = z(cx, 0)
                    elements.append(element([cx - 0.25, -9, cz - 0.25], [cx + 0.25, 0, cz + 0.25], "#rope", None,
                                            {"origin": [cx, 0, cz], "axis": "x", "angle": -35}))
    elif not up:
        # furled: the cloth rolled up on the yard and lashed
        elements.append(element([x0, 12.5, -2.5], [x1, 15.5, 1.5], CLOTH, uv_box(0, 12.5, min(16, x1 - x0), 15.5)))
        for gx in range(x0 + 3, x1, 6):
            elements.append(element([gx, 12.25, -2.75], [gx + 1, 15.75, 1.75], "#rope"))
    if not up:
        # the yard it hangs from, out past its free edges as yardarms; lashed to the mast
        ya = x0 - (2 if left == "none" else 0)
        yb = x1 + (2 if right == "none" else 0)
        elements.append(element([ya, 15, -1.5], [yb, 17, 0.5], "#yard"))
        for side, mx in (("left", -8), ("right", 24)):
            if (left if side == "left" else right) == "mast":
                elements.append(element([mx - 1.5, 15.25, 0.5], [mx + 1.5, 16.75, 7], "#rope"))
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"particle": "jaymoskingdoms:block/sailcloth", "cloth": "jaymoskingdoms:block/sailcloth",
                         "rope": "jaymoskingdoms:block/sail_rope", "yard": "minecraft:block/stripped_spruce_log"},
            "elements": elements}


def sails():
    sail_texture()
    variants = {}
    turns = {"north": 0, "east": 90, "south": 180, "west": 270}
    for left in ("none", "sail", "mast"):
        for right in ("none", "sail", "mast"):
            for up in (False, True):
                for below in (False, True):
                    for furled in (False, True):
                        name = f"sail_{left}_{right}_{'u' if up else 'x'}{'d' if below else 'x'}{'_furled' if furled else ''}"
                        write_json(os.path.join(ASSETS, "models", "block", "sail", f"{name}.json"),
                                   sail_model(left, right, up, below, furled))
                        for facing, y in turns.items():
                            key = (f"down={str(below).lower()},facing={facing},furled={str(furled).lower()},"
                                   f"left={left},right={right},up={str(up).lower()}")
                            v = {"model": f"jaymoskingdoms:block/sail/{name}"}
                            if y:
                                v["y"] = y
                            variants[key] = v
    write_json(os.path.join(ASSETS, "blockstates", "sail.json"), {"variants": variants})
    old = os.path.join(ASSETS, "models", "block", "sail.json")
    if os.path.exists(old):
        os.remove(old)


TURNS = {"north": 0, "east": 90, "south": 180, "west": 270}


def variant(model, y):
    v = {"model": model}
    if y:
        v["y"] = y
    return v


def block_model(textures, elements, ao=True):
    m = {"parent": "minecraft:block/block", "textures": textures, "elements": elements}
    if not ao:
        m["ambientocclusion"] = False
    return m


# ---- oars (0.7.12) ----
# Facing north is outboard (-z). The lock is an iron fork on the outboard edge of a low board on the gunwale; the oar
# turns in it at (8, 5.5, 1). Shipped, the oar lies along the gunwale; run out, the ship draws the loom alone, swung
# and stretched along its length (ShipRenderer).
def oars():
    tex = {"particle": "minecraft:block/spruce_planks", "wood": "minecraft:block/spruce_planks",
           "oar": "minecraft:block/stripped_birch_log", "iron": "minecraft:block/anvil"}
    lock = [cube([0, 0, 0], [16, 4, 6], "#wood"),
            cube([6.25, 4, 0.25], [9.75, 5, 1.75], "#iron"),
            cube([6.25, 5, 0.25], [7.25, 8, 1.75], "#iron"),
            cube([8.75, 5, 0.25], [9.75, 8, 1.75], "#iron")]
    shipped = lock + [cube([-3, 4, 2.5], [17, 5.5, 4], "#oar"),
                      cube([-7, 4, 2], [-3, 5, 4.5], "#oar"),
                      cube([17, 4.25, 2.75], [20, 5.25, 3.75], "#oar")]
    # the loom alone: its shaft through the lock, its blade outboard, its grip inboard (drawn 2.5 times as long)
    loom = [cube([7.4, 4.9, -12], [8.6, 6.1, 3], "#oar"),
            cube([7.6, 2.5, -16], [8.4, 8.5, -11.5], "#oar"),
            cube([7.5, 5.0, 3], [8.5, 6.0, 4.5], "#wood")]
    for name, els in (("lock", lock), ("shipped", shipped), ("loom", loom)):
        write_json(os.path.join(ASSETS, "models", "block", "oar", f"{name}.json"), block_model(tex, els))
    variants = {}
    for facing, y in TURNS.items():
        for oar in ("shipped", "lock", "loom"):
            variants[f"facing={facing},oar={oar}"] = variant(f"jaymoskingdoms:block/oar/{oar}", y)
    write_json(os.path.join(ASSETS, "blockstates", "oar.json"), {"variants": variants})


# ---- the ship's gun (0.7.12): an iron barrel on a wooden carriage, its muzzle north ----
def cannon():
    tex = {"particle": "minecraft:block/anvil", "iron": "minecraft:block/anvil", "wood": "minecraft:block/dark_oak_planks",
           "bore": "minecraft:block/black_concrete", "rope": "jaymoskingdoms:block/sail_rope"}
    els = [cube([3, 1, 2], [5, 6, 15], "#wood"),
           cube([11, 1, 2], [13, 6, 15], "#wood"),
           cube([5, 1.5, 3], [11, 3, 14], "#wood"),
           # the trucks (its little wheels)
           cube([1.5, 0, 3], [3, 3.5, 6.5], "#wood"),
           cube([13, 0, 3], [14.5, 3.5, 6.5], "#wood"),
           cube([1.5, 0, 10.5], [3, 3.5, 14], "#wood"),
           cube([13, 0, 10.5], [14.5, 3.5, 14], "#wood"),
           # the barrel, its muzzle swell, its breech and knob
           cube([5.5, 5, -1], [10.5, 10, 13], "#iron"),
           cube([5, 4.5, -2.5], [11, 10.5, -1], "#iron"),
           cube([5, 4.5, 11], [11, 10.5, 14], "#iron"),
           cube([7, 6.5, 14], [9, 8.5, 15.5], "#iron"),
           cube([6.5, 6, -2.6], [9.5, 9, -2.4], "#bore", "n"),
           # the breeching rope
           cube([2.5, 6, 12], [13.5, 7, 13], "#rope")]
    write_json(os.path.join(ASSETS, "models", "block", "ship_cannon.json"), block_model(tex, els))
    variants = {}
    for facing, y in TURNS.items():
        for load in ("empty", "powder", "shot"):
            variants[f"facing={facing},load={load}"] = variant("jaymoskingdoms:block/ship_cannon", y)
    write_json(os.path.join(ASSETS, "blockstates", "ship_cannon.json"), {"variants": variants})


# ---- frames (0.7.12): bare ribs round the cell, and a small token of what it wants in the middle ----
def frames():
    tex = {"particle": "minecraft:block/scaffolding_side", "rib": "minecraft:block/stripped_oak_log",
           "planks": "minecraft:block/oak_planks", "mast": "minecraft:block/stripped_spruce_log",
           "cloth": "jaymoskingdoms:block/sailcloth", "dark": "minecraft:block/dark_oak_planks",
           "iron": "minecraft:block/anvil", "oar": "minecraft:block/stripped_birch_log"}
    ribs = []
    for a in (0, 15):
        for b in (0, 15):
            ribs.append(cube([a, 0, b], [a + 1, 16, b + 1], "#rib"))
            ribs.append(cube([0, a, b], [16, a + 1, b + 1], "#rib"))
            ribs.append(cube([a, b, 0], [a + 1, b + 1, 16], "#rib"))
    tokens = {
        "hull": [cube([5, 5, 5], [11, 11, 11], "#planks")],
        "rail": [cube([7, 1, 7], [9, 13, 9], "#planks"), cube([7.5, 9, 1], [8.5, 11, 15], "#planks")],
        "mast": [cube([6, 0, 6], [10, 16, 10], "#mast")],
        "sail": [cube([2, 3, 2], [14, 14, 3], "#cloth"), cube([1, 14, 1.5], [15, 15, 3.5], "#mast")],
        "helm": [cube([7, 1, 7], [9, 8, 9], "#dark"), cube([5, 8, 8], [11, 9, 9], "#dark"), cube([5, 13, 8], [11, 14, 9], "#dark"),
                 cube([4, 9, 8], [5, 13, 9], "#dark"), cube([11, 9, 8], [12, 13, 9], "#dark"), cube([7.5, 10.5, 7.5], [8.5, 11.5, 9.5], "#iron")],
        "oar": [dict(cube([7.5, 2, 7.5], [8.5, 15, 8.5], "#oar"), rotation={"origin": [8, 8, 8], "axis": "x", "angle": -45}),
                dict(cube([7.25, 0, 3], [8.75, 4, 7], "#oar"), rotation={"origin": [8, 8, 8], "axis": "x", "angle": -45})],
        "cannon": [cube([5, 1, 5], [11, 4, 12], "#dark"), cube([6.5, 4, 3], [9.5, 7, 12], "#iron")],
        "chest": [cube([4, 1, 4], [12, 8, 12], "#planks"), cube([7.5, 5, 3.5], [8.5, 7, 4], "#iron")],
    }
    variants = {}
    for want, els in tokens.items():
        write_json(os.path.join(ASSETS, "models", "block", "ship_frame", f"{want}.json"), block_model(tex, ribs + els, ao=False))
        for facing, y in TURNS.items():
            for wet in ("false", "true"):
                variants[f"facing={facing},want={want},waterlogged={wet}"] = variant(f"jaymoskingdoms:block/ship_frame/{want}", y)
    write_json(os.path.join(ASSETS, "blockstates", "ship_frame.json"), {"variants": variants})


# ---- item sprites (0.7.12): round shot, and the three kits (a rolled plan of each ship) ----
def sprites():
    out = os.path.join(ASSETS, "textures", "item")
    os.makedirs(out, exist_ok=True)
    shot = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d <= 5.2:
                light = max(0.0, 1.0 - math.hypot(x - 5.5, y - 5.5) / 6.5)
                v = int(38 + 70 * light)
                shot.putpixel((x, y), (v, v, v + 4, 255))
            elif d <= 5.9:
                shot.putpixel((x, y), (22, 22, 26, 255))
    shot.save(os.path.join(out, "round_shot.png"))
    ink, wood, cloth = (40, 62, 120, 255), (110, 74, 40, 255), (236, 229, 208, 255)
    for name, masts in (("rowboat_kit", 0), ("sloop_kit", 1), ("brig_kit", 2)):
        img = Image.new("RGBA", (16, 16))
        rng = random.Random(name)
        for y in range(3, 14):
            for x in range(1, 15):
                n = rng.randint(-6, 6)
                img.putpixel((x, y), (222 + n, 204 + n, 160 + n, 255))
        for y in range(2, 15):
            for x in (0, 1, 14, 15):
                img.putpixel((x, y), (176, 150, 104, 255) if x in (0, 15) else (196, 172, 124, 255))
        # the hull, in ink
        for x in range(4, 12):
            img.putpixel((x, 11), ink)
        for x in range(3, 13):
            img.putpixel((x, 10), ink)
        img.putpixel((3, 9), ink)
        img.putpixel((12, 9), ink)
        img.putpixel((13, 8), ink)
        for m in range(masts):
            mx = 8 if masts == 1 else 6 + 4 * m
            for y in range(3, 10):
                img.putpixel((mx, y), wood)
            for y in range(4, 8):
                for x in range(mx - 2, mx + 3):
                    if x != mx:
                        img.putpixel((x, y), cloth)
        if masts == 0:
            # her oars
            img.putpixel((5, 9), wood)
            img.putpixel((4, 8), wood)
            img.putpixel((10, 9), wood)
            img.putpixel((11, 8), wood)
        img.save(os.path.join(out, f"{name}.png"))


def stage_two():
    oars()
    cannon()
    frames()
    sprites()
    for name, model in (("oar", "jaymoskingdoms:block/oar/shipped"), ("ship_cannon", "jaymoskingdoms:block/ship_cannon")):
        write_json(os.path.join(ASSETS, "items", f"{name}.json"), {"model": {"type": "minecraft:model", "model": model}})
        write_json(os.path.join(DATA, "jaymoskingdoms", "loot_table", "blocks", f"{name}.json"), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"jaymoskingdoms:{name}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    for name in ("round_shot", "rowboat_kit", "sloop_kit", "brig_kit"):
        write_json(os.path.join(ASSETS, "models", "item", f"{name}.json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"jaymoskingdoms:item/{name}"}})
        write_json(os.path.join(ASSETS, "items", f"{name}.json"),
                   {"model": {"type": "minecraft:model", "model": f"jaymoskingdoms:item/{name}"}})
    recipes = {
        "oar": {"type": "minecraft:crafting_shaped", "category": "misc",
                "key": {"S": "minecraft:stick", "P": "#minecraft:planks", "I": "minecraft:iron_nugget"},
                "pattern": ["  P", " S ", "I  "], "result": {"id": "jaymoskingdoms:oar"}},
        "ship_cannon": {"type": "minecraft:crafting_shaped", "category": "misc",
                        "key": {"I": "minecraft:iron_ingot", "B": "minecraft:iron_block", "P": "#minecraft:planks"},
                        "pattern": ["IBI", "P P"], "result": {"id": "jaymoskingdoms:ship_cannon"}},
        "round_shot": {"type": "minecraft:crafting_shaped", "category": "misc",
                       "key": {"N": "minecraft:iron_nugget", "I": "minecraft:iron_ingot"},
                       "pattern": [" N ", "NIN", " N "], "result": {"id": "jaymoskingdoms:round_shot", "count": 4}},
        "rowboat_kit": {"type": "minecraft:crafting_shapeless", "category": "misc",
                        "ingredients": ["minecraft:paper", "minecraft:string", "minecraft:stick"],
                        "result": {"id": "jaymoskingdoms:rowboat_kit"}},
        "sloop_kit": {"type": "minecraft:crafting_shapeless", "category": "misc",
                      "ingredients": ["minecraft:paper", "minecraft:paper", "minecraft:string", "minecraft:stick", "#minecraft:wool"],
                      "result": {"id": "jaymoskingdoms:sloop_kit"}},
        "brig_kit": {"type": "minecraft:crafting_shapeless", "category": "misc",
                     "ingredients": ["minecraft:paper", "minecraft:paper", "minecraft:paper", "minecraft:string", "minecraft:stick",
                                     "#minecraft:wool", "minecraft:iron_ingot"],
                     "result": {"id": "jaymoskingdoms:brig_kit"}},
    }
    for name, r in recipes.items():
        write_json(os.path.join(DATA, "jaymoskingdoms", "recipe", f"{name}.json"), r)


def main():
    # the helm, facing north (the way the ship sails): a post with its wheel toward the one steering, behind it
    write_json(os.path.join(ASSETS, "models", "block", "ship_helm.json"), {
        "parent": "minecraft:block/block",
        "textures": {"particle": "minecraft:block/spruce_planks", "wood": "minecraft:block/spruce_planks",
                     "dark": "minecraft:block/dark_oak_planks", "post": "minecraft:block/stripped_spruce_log",
                     "iron": "minecraft:block/iron_block"},
        "elements": [
            cube([4, 0, 4], [12, 1, 12], "#dark"),
            cube([6, 1, 6], [10, 11, 10], "#post"),
            cube([7.25, 11.25, 8], [8.75, 12.75, 11], "#iron"),
            # the rim
            cube([4, 16, 11], [12, 17, 12], "#wood"),
            cube([4, 7, 11], [12, 8, 12], "#wood"),
            cube([3, 8, 11], [4, 16, 12], "#wood"),
            cube([12, 8, 11], [13, 16, 12], "#wood"),
            # the spokes, the hub, the handles
            cube([4, 11.5, 11.25], [12, 12.5, 11.75], "#dark"),
            cube([7.5, 8, 11.25], [8.5, 16, 11.75], "#dark"),
            cube([7, 11, 10.75], [9, 13, 12.25], "#iron"),
            cube([7.5, 17, 11.25], [8.5, 19, 11.75], "#dark"),
            cube([7.5, 5, 11.25], [8.5, 7, 11.75], "#dark"),
            cube([1, 11.5, 11.25], [3, 12.5, 11.75], "#dark"),
            cube([13, 11.5, 11.25], [15, 12.5, 11.75], "#dark"),
        ]})
    write_json(os.path.join(ASSETS, "blockstates", "ship_helm.json"),
               facing_variants("jaymoskingdoms:block/ship_helm", {"north": 0, "east": 90, "south": 180, "west": 270}))
    sails()
    for name in ("ship_helm", "sail"):
        model = "jaymoskingdoms:block/sail/sail_none_none_xx" if name == "sail" else f"jaymoskingdoms:block/{name}"
        write_json(os.path.join(ASSETS, "items", f"{name}.json"), {"model": {"type": "minecraft:model", "model": model}})
        write_json(os.path.join(DATA, "jaymoskingdoms", "loot_table", "blocks", f"{name}.json"), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"jaymoskingdoms:{name}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    write_json(os.path.join(DATA, "jaymoskingdoms", "recipe", "ship_helm.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "key": {"S": "minecraft:stick", "P": "#minecraft:planks", "C": "minecraft:compass", "I": "minecraft:iron_ingot"},
        "pattern": ["SPS", "PCP", "SIS"], "result": {"id": "jaymoskingdoms:ship_helm"}})
    write_json(os.path.join(DATA, "jaymoskingdoms", "recipe", "sail.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "key": {"S": "minecraft:stick", "W": "#minecraft:wool"},
        "pattern": [" S ", "WWW", "WWW"], "result": {"id": "jaymoskingdoms:sail", "count": 4}})
    stage_two()


if __name__ == "__main__":
    main()
