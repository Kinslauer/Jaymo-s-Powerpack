"""Alchemy track D (the infusion altar): textures, models, blockstates, item definitions, loot tables, crafting recipes
and the infusion recipes themselves (data/jaymospowers/alchemy/infusion/*.json).

Textures are painted as pixel grids after docs/Vanilla-Style.md: 16x16, no black outlines (the outline is the darkest
shade of what it wraps, one step lighter on the lit side), light from the top-left, 5 to 18 colours each. Everything is
drawn from nothing; no vanilla pixels are read, so no argument is needed.

Run from the repository root: python3 -I tools/gen_alchemy_infusion.py
"""
import json
import math
import os
import random

from PIL import Image

ASSETS = "src/main/resources/assets/jaymospowers/"
DATA = "src/main/resources/data/jaymospowers/"
BLOCK_TEX = ASSETS + "textures/block/alchemy/"
ITEM_TEX = ASSETS + "textures/item/alchemy/"
random.seed(2604)


def hexc(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


# dark to light
STONE = [hexc(c) for c in ("4a4a52", "62626b", "75757e", "898992", "9e9ea8", "b6b6c0")]
RUNE = [hexc(c) for c in ("2f4aa8", "4f7bff", "86adff", "c4dcff")]
MATRIX_DARK = [hexc(c) for c in ("231d36", "30284a", "3e3560", "4d4278")]
LILAC = [hexc(c) for c in ("6b4fb0", "9b7ae0", "c9b8ff", "efe6ff")]
WAX = [hexc(c) for c in ("b9b2a2", "d6d0c2", "ece8de", "fbf9f3")]
SALT = [hexc(c) for c in ("9aa7bd", "c3cddc", "e1e8f1", "ffffff")]
FLAME = [hexc(c) for c in ("e2572b", "ff9b21", "ffd95e", "fff7ad")]
GOLD = [hexc(c) for c in ("6a4a0c", "9a6f12", "d4a317", "f9c92d", "fde75e", "fffbb0")]
ICHOR = [hexc(c) for c in ("6e2f0b", "a8581a", "d68222", "f2b430", "ffd95e", "fff3a6")]
CLEAR = (0, 0, 0, 0)


def new():
    return Image.new("RGBA", (16, 16), CLEAR)


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def fill(img, ramp, centre, box=(0, 0, 16, 16), spread=True):
    x0, y0, x1, y1 = box
    px = img.load()
    for x in range(x0, x1):
        for y in range(y0, y1):
            i = centre + (random.choice([-1, 0, 0, 0, 1]) if spread else 0)
            px[x, y] = ramp[max(0, min(len(ramp) - 1, i))]


# ---------------------------------------------------------------- block textures
def pedestal_side():
    """Rows 2-3 are the head slab, 4-13 the shaft (stone bricks), 14-15 the foot: the model reads those rows."""
    img = new()
    fill(img, STONE, 2)
    px = img.load()
    for x in range(16):
        # head slab: lit top row, shade under it
        px[x, 2] = STONE[4]
        px[x, 3] = STONE[1]
        # foot: lit top row, dark bottom row
        px[x, 14] = STONE[3]
        px[x, 15] = STONE[0]
        # a seam under the shaft's top and above its foot
        px[x, 4] = STONE[0]
        px[x, 13] = STONE[1]
    # shaft bricks: mortar every 3 rows, offset by row, light line under each course
    for row, y in enumerate((7, 10)):
        for x in range(16):
            px[x, y] = STONE[1]
            px[x, y + 1] = STONE[3]
        for x in range(row * 3 + 2, 16, 6):
            for yy in range(y - 3 if row == 0 else y - 3, y):
                px[x, yy] = STONE[1]
    # a little rune scratched in the shaft
    for x, y in ((7, 5), (8, 5), (8, 6), (7, 11), (8, 12)):
        px[x, y] = RUNE[1]
    save(img, BLOCK_TEX + "pedestal.png")


def pedestal_top():
    img = new()
    fill(img, STONE, 3)
    px = img.load()
    for i in range(16):
        px[i, 0] = STONE[5]
        px[0, i] = STONE[5]
        px[i, 15] = STONE[1]
        px[15, i] = STONE[1]
    # a ring of rune light with three marks
    for a in range(32):
        t = a / 32 * math.tau
        x, y = round(7.5 + 4.5 * math.cos(t)), round(7.5 + 4.5 * math.sin(t))
        px[x, y] = RUNE[0] if a % 4 else RUNE[1]
    for x, y in ((7, 7), (8, 7), (7, 8), (8, 8)):
        px[x, y] = RUNE[2]
    save(img, BLOCK_TEX + "pedestal_top.png")


def matrix():
    """The floating stone: the model shows the middle 8x8 (x and y 4-11) on every face."""
    img = new()
    fill(img, MATRIX_DARK, 1)
    px = img.load()
    for i in range(4, 12):
        px[i, 4] = MATRIX_DARK[3]
        px[4, i] = MATRIX_DARK[3]
        px[i, 11] = MATRIX_DARK[0]
        px[11, i] = MATRIX_DARK[0]
    # glowing lines: a frame inside the edge, a cross, and a bright core
    for i in range(5, 11):
        px[i, 5] = RUNE[1]
        px[i, 10] = RUNE[0]
        px[5, i] = RUNE[1]
        px[10, i] = RUNE[0]
    for i in range(6, 10):
        px[i, 7] = RUNE[1]
        px[7, i] = RUNE[1]
    px[7, 7] = RUNE[3]
    px[8, 8] = RUNE[2]
    px[8, 7] = RUNE[2]
    px[7, 8] = RUNE[2]
    for x, y in ((5, 5), (10, 5), (5, 10), (10, 10)):
        px[x, y] = LILAC[2]
    save(img, BLOCK_TEX + "matrix.png")


def salt_candle():
    """Regions the model reads: side (0,0)-(4,6), top (4,0)-(8,4), flame (8,0)-(9,2)."""
    img = new()
    px = img.load()
    for x in range(4):
        for y in range(6):
            px[x, y] = WAX[1 + (1 if x == 0 else 0)]
        px[3, 0] = WAX[0]
    for y in range(6):
        px[3, y] = WAX[0]
        px[0, y] = WAX[2]
    # salt crystals crusting the wax
    for x, y in ((1, 2), (2, 4), (0, 5), (2, 1)):
        px[x, y] = SALT[2 if (x + y) % 2 else 3]
    # top: wax disc with a dark wick
    for x in range(4, 8):
        for y in range(4):
            px[x, y] = WAX[2]
    px[5, 1] = MATRIX_DARK[0]
    # flame: two pixels
    px[8, 0] = FLAME[3]
    px[8, 1] = FLAME[1]
    save(img, BLOCK_TEX + "salt_candle.png")


# ---------------------------------------------------------------- item textures
def disc(px, cx, cy, r, ramp, lit=-1):
    for x in range(16):
        for y in range(16):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d <= r:
                # light from the top-left: the further up-left, the lighter
                shade = (x + 0.5 - cx + y + 0.5 - cy) / (2 * r)
                i = 3 - round(shade * 2)
                px[x, y] = ramp[max(1, min(len(ramp) - 2, i))]
    # outline: darkest ramp step on the lower right, one step up on the upper left
    out = {}
    for x in range(16):
        for y in range(16):
            if px[x, y] != CLEAR:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and px[nx, ny] != CLEAR and px[nx, ny] not in (ramp[0], ramp[1]):
                    out[(x, y)] = ramp[1] if dx + dy > 0 else ramp[0]
    for (x, y), c in out.items():
        px[x, y] = c


def ichor_item():
    """A drop of liquid gold: a teardrop with a glint."""
    img = new()
    px = img.load()
    cx, cy = 8.0, 10.0
    for x in range(16):
        for y in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            # round belly below, narrowing to a point at the top
            if dy >= 0:
                inside = math.hypot(dx, dy) <= 4.6
            else:
                inside = abs(dx) <= 4.6 * (1 + dy / 6.5) and dy > -6.2
            if inside:
                shade = (dx + dy) / 9.0
                px[x, y] = ICHOR[max(1, min(4, 3 - round(shade * 3)))]
    edge = {}
    for x in range(16):
        for y in range(16):
            if px[x, y] != CLEAR:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and px[nx, ny] not in (CLEAR,) and (nx, ny) not in edge:
                    edge[(x, y)] = ICHOR[1] if dx + dy > 0 else ICHOR[0]
    for (x, y), c in edge.items():
        px[x, y] = c
    px[6, 9] = ICHOR[5]
    px[6, 10] = ICHOR[4]
    px[7, 8] = ICHOR[5]
    save(img, ITEM_TEX + "ichor.png")


def amulet_item():
    """A gold disc on a short chain, a lilac rune cut in it."""
    img = new()
    px = img.load()
    # chain: a loop above the disc
    for x, y in ((6, 1), (9, 1), (5, 2), (10, 2), (5, 3), (10, 3), (6, 4), (9, 4), (7, 0), (8, 0)):
        px[x, y] = GOLD[2] if (x + y) % 2 else GOLD[1]
    disc(px, 8.0, 10.0, 5.0, GOLD)
    # the rune
    for x, y in ((8, 8), (8, 9), (8, 10), (8, 11), (7, 9), (9, 9), (7, 12), (9, 12)):
        px[x, y] = LILAC[1] if y < 12 else LILAC[0]
    px[8, 9] = LILAC[3]
    save(img, ITEM_TEX + "runic_amulet.png")


# ---------------------------------------------------------------- models and the rest
def cube(frm, to, tex, uvs=None, skip=()):
    faces = {}
    for f in ("north", "south", "east", "west", "up", "down"):
        if f in skip:
            continue
        faces[f] = {"texture": tex(f) if callable(tex) else tex}
        if uvs and f in uvs:
            faces[f]["uv"] = uvs[f]
    return {"from": frm, "to": to, "faces": faces}


def models():
    write(ASSETS + "models/block/alchemy/pedestal.json", {
        "parent": "minecraft:block/block",
        "textures": {"particle": "jaymospowers:block/alchemy/pedestal", "side": "jaymospowers:block/alchemy/pedestal",
                     "top": "jaymospowers:block/alchemy/pedestal_top"},
        "elements": [
            cube([2, 0, 2], [14, 2, 14], lambda f: "#top" if f == "up" else "#side", skip=("down",)),
            cube([4, 2, 4], [12, 12, 12], "#side", skip=("up", "down")),
            cube([2, 12, 2], [14, 14, 14], lambda f: "#top" if f == "up" else "#side", skip=("down",)),
        ]})
    write(ASSETS + "models/block/alchemy/infusion_matrix.json", {
        "parent": "minecraft:block/block",
        "textures": {"particle": "jaymospowers:block/alchemy/matrix", "all": "jaymospowers:block/alchemy/matrix"},
        "elements": [cube([4, 4, 4], [12, 12, 12], "#all")]})
    side = [0, 0, 4, 6]
    write(ASSETS + "models/block/alchemy/salt_candle.json", {
        "parent": "minecraft:block/block",
        "textures": {"particle": "jaymospowers:block/alchemy/salt_candle", "candle": "jaymospowers:block/alchemy/salt_candle"},
        "elements": [
            cube([6, 0, 6], [10, 6, 10], "#candle",
                 {"north": side, "south": side, "east": side, "west": side, "up": [4, 0, 8, 4], "down": [4, 0, 8, 4]}),
            {"from": [7.5, 6, 7.5], "to": [8.5, 8, 8.5], "faces": {
                f: {"texture": "#candle", "uv": [8, 0, 9, 2]} for f in ("north", "south", "east", "west")}},
        ]})
    for name in ("ichor", "runic_amulet"):
        write(ASSETS + f"models/item/alchemy/{name}.json", {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"jaymospowers:item/alchemy/{name}"}})
        write(ASSETS + f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"jaymospowers:item/alchemy/{name}"}})
    for name, model in (("pedestal", "pedestal"), ("infusion_matrix", "infusion_matrix"), ("salt_candle", "salt_candle")):
        write(ASSETS + f"blockstates/{name}.json", {"variants": {"": {"model": f"jaymospowers:block/alchemy/{model}"}}})
        write(ASSETS + f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"jaymospowers:block/alchemy/{model}"}})
        write(DATA + f"loot_table/blocks/{name}.json", {
            "type": "minecraft:block", "random_sequence": f"jaymospowers:blocks/{name}",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"jaymospowers:{name}"}]}]})


def recipes():
    write(DATA + "recipe/pedestal.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "key": {"S": "minecraft:smooth_stone_slab", "B": "minecraft:stone_bricks"},
        "pattern": ["SSS", " B ", "BBB"],
        "result": {"id": "jaymospowers:pedestal", "count": 1}})
    write(DATA + "recipe/infusion_matrix.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "key": {"O": "jaymospowers:orichalc_ingot", "C": "minecraft:chiseled_stone_bricks", "A": "minecraft:amethyst_shard"},
        "pattern": ["AOA", "OCO", "AOA"],
        "result": {"id": "jaymospowers:infusion_matrix", "count": 1}})
    write(DATA + "recipe/salt_candle.json", {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": ["#minecraft:candles", "jaymospowers:alchemical_salt"],
        "result": {"id": "jaymospowers:salt_candle", "count": 1}})


def infusions():
    base = DATA + "alchemy/infusion/"
    write(base + "ichor.json", {
        "centre": "minecraft:diamond",
        "components": ["minecraft:ghast_tear", "minecraft:ghast_tear", "jaymospowers:orichalc_ingot", "jaymospowers:orichalc_ingot"],
        "essences": {"spirit": 8, "arcana": 8, "light": 8},
        "instability": 6,
        "result": {"item": "jaymospowers:ichor", "count": 1},
        "research": "ichor",
        "flux": 6})
    write(base + "runic_amulet.json", {
        "centre": "minecraft:gold_ingot",
        "components": ["jaymospowers:orichalc_ingot", "jaymospowers:alchemical_salt"],
        "essences": {"spirit": 6, "salt": 6},
        "instability": 3,
        "result": {"item": "jaymospowers:runic_amulet", "count": 1},
        "research": "runic_amulet",
        "flux": 2})
    write(base + "infused_enchanting.json", {
        "centre": "*enchanted",
        "components": ["minecraft:lapis_lazuli"] * 4 + ["jaymospowers:orichalc_ingot"] * 2 + ["minecraft:enchanted_book"],
        "essences": {"arcana": 16, "spirit": 10, "mind": 6},
        "instability": 14,
        "result": {"special": "infuse_enchantment"},
        "research": "infused_enchanting",
        "flux": 40})


if __name__ == "__main__":
    pedestal_side()
    pedestal_top()
    matrix()
    salt_candle()
    ichor_item()
    amulet_item()
    models()
    recipes()
    infusions()
    print("done")
