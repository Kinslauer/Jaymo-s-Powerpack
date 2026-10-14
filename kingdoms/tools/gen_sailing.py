"""Writes Jaymo's Kingdoms' sailing assets (0.7.4): the ship's helm (a spruce wheel on a post, from vanilla's own block
textures) and the sail (0.7.6: bellied sailcloth on a yard, one model for each way its neighbours join it, set and
furled, and our own sailcloth and rope textures), their blockstates, item models, loot tables and recipes.
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


if __name__ == "__main__":
    main()
