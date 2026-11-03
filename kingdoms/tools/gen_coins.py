"""Draws the coinage (0.7.487): original 16x16 vanilla-style art and the repetitive JSON around it. Run from kingdoms/:
python -I tools/gen_coins.py   (then, from the repo root, python -I tools/excalibur_style/grade.py)

Items: copper_coin, silver_coin, gold_coin (a stamped disc, lit from the top-left, a little mark pressed in), the three
coin stacks (a column of ten coins), coin_purse_0..4 (a leather pouch tied with string that bulges as it fills; the item
definition reads custom_model_data float 0 = fullness).
Blocks: coin_press_metal / coin_press_base, counting_table_top / counting_table_side (an abacus ruled into the top), and the
three coin piles' textures.
JSON: the pile block models (one to eight layers) and blockstates, their loot tables (a layer is a coin stack), the item
definitions and models, and the Coin Press and Counting Table block models.
"""
import json
import math
import os
import random

from PIL import Image

RES = os.path.join("src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymoskingdoms")
DATA = os.path.join(RES, "data", "jaymoskingdoms")
TEX = os.path.join(ASSETS, "textures")

# five-step ramps, darkest to lightest (vanilla's copper, iron and gold)
RAMPS = {
    "copper": [(0x5a, 0x2c, 0x1c), (0x8a, 0x4a, 0x2c), (0xb8, 0x6a, 0x3e), (0xd8, 0x8a, 0x58), (0xf0, 0xb0, 0x84)],
    "silver": [(0x58, 0x5e, 0x6a), (0x86, 0x8e, 0x9c), (0xb4, 0xbc, 0xc8), (0xd8, 0xde, 0xe6), (0xf6, 0xf8, 0xfc)],
    "gold": [(0x7a, 0x52, 0x10), (0xb0, 0x80, 0x1c), (0xe0, 0xb0, 0x2c), (0xf8, 0xd4, 0x48), (0xff, 0xf0, 0x9c)],
}
LEATHER = {"A": (0x35, 0x1e, 0x10), "B": (0x4f, 0x2f, 0x18), "C": (0x6e, 0x43, 0x24), "D": (0x8a, 0x58, 0x2e), "E": (0xa8, 0x72, 0x3e)}
STRING = (0xde, 0xd6, 0xbe)
IRON = [(0x18, 0x18, 0x18), (0x44, 0x44, 0x44), (0x6b, 0x6b, 0x6b), (0x96, 0x96, 0x96), (0xbe, 0xbe, 0xbe), (0xd8, 0xd8, 0xd8), (0xff, 0xff, 0xff)]
WOOD = [(0x5a, 0x3c, 0x20), (0x72, 0x4f, 0x2a), (0x8a, 0x62, 0x38), (0xa6, 0x7c, 0x4a), (0xb9, 0x92, 0x5e)]
STONE = [(0x50, 0x50, 0x54), (0x68, 0x68, 0x6c), (0x80, 0x80, 0x84), (0x96, 0x96, 0x9a)]


def canvas():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def put(img, x, y, color):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), color + (255,))


def save(img, kind, name):
    os.makedirs(os.path.join(TEX, kind), exist_ok=True)
    img.save(os.path.join(TEX, kind, name + ".png"))


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


# ---- coins -----------------------------------------------------------------------------------------------------------

def coin(ramp, seed):
    """A disc ten pixels across, lit from the top-left, with a small mark pressed into its face."""
    img = canvas()
    cx, cy, r = 8.0, 8.0, 5.0
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            if d > r:
                continue
            lit = (-dx - dy) / (2 * r)  # +1 at the top-left rim, -1 at the bottom-right
            if d > r - 1.3:
                # the rim: bright on the lit side, dark on the other
                c = ramp[4] if lit > 0.55 else ramp[3] if lit > 0.1 else ramp[1] if lit > -0.5 else ramp[0]
            else:
                c = ramp[3] if lit > 0.35 else ramp[2] if lit > -0.35 else ramp[1]
            put(img, x, y, c)
    # the mark: a little pressed plus, its lit edge up-left and shadow down-right
    for (x, y) in [(8, 6), (7, 7), (8, 7), (9, 7), (8, 8), (8, 9)]:
        put(img, x, y, ramp[1])
    for (x, y) in [(7, 6), (7, 8), (6, 7)]:
        put(img, x, y, ramp[4])
    # a glint on the rim
    put(img, 5, 5, ramp[4])
    return img


def coin_stack(ramp):
    """A column of coins seen from above and a little in front: a lit top, ridged sides."""
    img = canvas()
    cx, rx, ry = 8.0, 5.0, 2.6
    top, bottom = 4.0, 11.5
    for y in range(16):
        for x in range(16):
            dx = x + 0.5 - cx
            if abs(dx) > rx:
                continue
            k = math.sqrt(max(0.0, 1 - (dx / rx) ** 2))
            up = top - ry * k
            low_top = top + ry * k
            low_bot = bottom + ry * k
            py = y + 0.5
            if py < up or py > low_bot:
                continue
            if py <= low_top:
                lit = (-dx - (py - top)) / (rx + ry)
                c = ramp[4] if lit > 0.5 else ramp[3] if lit > 0.0 else ramp[2]
                if abs(dx) > rx - 1.0 or py < up + 1.0:
                    c = ramp[3] if lit > 0 else ramp[1]
            else:
                band = int(py - low_top) % 2
                shade = dx / rx  # -1 left (lit) .. +1 right (dark)
                base = 3 if shade < -0.5 else 2 if shade < 0.2 else 1
                c = ramp[base - (1 if band else 0)] if base - (1 if band else 0) >= 0 else ramp[0]
            put(img, x, y, c)
    # the mark on the top coin
    put(img, 8, 3, ramp[1])
    put(img, 8, 4, ramp[1])
    put(img, 7, 4, ramp[1])
    put(img, 9, 4, ramp[1])
    return img


# ---- the purse -------------------------------------------------------------------------------------------------------

def purse(level):
    """A leather pouch tied at the neck with string; it bulges as it fills and shows coins at its mouth once it holds any."""
    img = canvas()
    # body: an ellipse that grows with the fullness
    grow = [0.0, 0.6, 1.2, 1.8, 2.4][level]
    cx, cy = 8.0, 10.2 - grow * 0.2
    rx, ry = 3.4 + grow * 0.8, 3.0 + grow * 0.7
    for y in range(16):
        for x in range(16):
            dx, dy = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
            d = dx * dx + dy * dy
            if d > 1.0 or y < 6:
                continue
            lit = (-dx - dy) / 2.0
            edge = d > 0.72
            if lit > 0.35:
                c = LEATHER["E"] if not edge else LEATHER["D"]
            elif lit > -0.1:
                c = LEATHER["D"] if not edge else LEATHER["C"]
            elif lit > -0.45:
                c = LEATHER["C"] if not edge else LEATHER["B"]
            else:
                c = LEATHER["B"] if not edge else LEATHER["A"]
            put(img, x, y, c)
    # the neck, tied
    for y in (5, 6):
        for x in (7, 8, 9):
            put(img, x, y, LEATHER["C"] if x == 8 else LEATHER["B"])
    for x in (6, 7, 8, 9, 10):
        put(img, x, 6, STRING)
    put(img, 6, 7, STRING)
    put(img, 10, 7, STRING)
    put(img, 5, 7, (0xb0, 0xa8, 0x90))
    # the flared mouth above the tie
    mouth = {2: (6, 10), 3: (5, 11), 4: (6, 10)}
    for y, (x0, x1) in mouth.items():
        for x in range(x0, x1 + 1):
            edge = x in (x0, x1)
            put(img, x, y, LEATHER["B"] if edge else LEATHER["D"] if y == 2 else LEATHER["C"])
    put(img, 7, 4, LEATHER["B"])
    put(img, 9, 4, LEATHER["B"])
    put(img, 8, 5, LEATHER["B"])
    if level > 0:
        # coins at the mouth
        g = RAMPS["gold"]
        s = RAMPS["silver"]
        put(img, 7, 2, g[3])
        put(img, 8, 2, g[4])
        put(img, 9, 2, s[3])
        if level > 1:
            put(img, 7, 1, g[2])
            put(img, 8, 1, g[3])
        if level > 2:
            put(img, 9, 1, s[4])
            put(img, 6, 2, RAMPS["copper"][3])
        if level > 3:
            put(img, 8, 0, g[4])
            put(img, 7, 0, g[2])
            put(img, 10, 2, g[3])
    return img


# ---- blocks ----------------------------------------------------------------------------------------------------------

def coin_press_metal():
    img = canvas()
    rng = random.Random(486)
    for y in range(16):
        for x in range(16):
            c = IRON[2] if (x + y) % 7 else IRON[3]
            if rng.random() < 0.12:
                c = IRON[1]
            put(img, x, y, c)
    for i in range(16):
        for (x, y) in [(i, 0), (0, i)]:
            put(img, x, y, IRON[4])
        for (x, y) in [(i, 15), (15, i)]:
            put(img, x, y, IRON[1])
    for (x, y) in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        put(img, x, y, IRON[5])
        put(img, x + 1, y + 1, IRON[0])
    return img


def coin_press_base():
    img = canvas()
    for y in range(16):
        row = y // 4
        for x in range(16):
            c = STONE[2] if (x + (2 if row % 2 else 0)) % 8 else STONE[0]
            if y % 4 == 3:
                c = STONE[0]
            elif y % 4 == 0 and c != STONE[0]:
                c = STONE[3]
            put(img, x, y, c)
    return img


def counting_table_top():
    img = canvas()
    for y in range(16):
        for x in range(16):
            c = WOOD[2] if (x // 4 + y // 8) % 2 else WOOD[3]
            if x % 4 == 0:
                c = WOOD[1]
            put(img, x, y, c)
    # the abacus: four rods ruled across with beads on them
    beads = [(0xc0, 0x30, 0x28), (0xe8, 0xe0, 0xc8), (0xe0, 0xb0, 0x2c), (0xe8, 0xe0, 0xc8)]
    rng = random.Random(7)
    for i, y in enumerate((3, 6, 9, 12)):
        for x in range(1, 15):
            put(img, x, y, WOOD[0])
        for x in sorted(rng.sample(range(2, 14), 4)):
            put(img, x, y, beads[i])
            put(img, x + 1 if x < 13 else x - 1, y, beads[i])
    for i in range(16):
        put(img, i, 0, WOOD[4])
        put(img, 0, i, WOOD[4])
        put(img, i, 15, WOOD[0])
        put(img, 15, i, WOOD[0])
    return img


def counting_table_side():
    img = canvas()
    for y in range(16):
        for x in range(16):
            c = WOOD[2] if x % 5 else WOOD[1]
            if y < 3:
                c = WOOD[3] if y else WOOD[4]
            elif y == 3:
                c = WOOD[0]
            put(img, x, y, c)
    return img


def pile(ramp, seed):
    """Coins heaped: lots of small lit discs on a darker bed, drawn so it tiles."""
    img = canvas()
    rng = random.Random(seed)
    for y in range(16):
        for x in range(16):
            put(img, x, y, ramp[1] if (x + y) % 3 else ramp[0])
    for _ in range(11):
        cx, cy = rng.randrange(16), rng.randrange(16)
        for dy in range(-2, 3):
            for dx in range(-2, 3):
                d = math.hypot(dx, dy)
                if d > 2.1:
                    continue
                lit = (-dx - dy)
                c = ramp[3] if d < 1.4 and lit > 0 else ramp[2] if d < 1.4 else ramp[4] if lit > 1 else ramp[0]
                put(img, (cx + dx) % 16, (cy + dy) % 16, c)
        put(img, cx % 16, cy % 16, ramp[1])
    return img


# ---- json ------------------------------------------------------------------------------------------------------------

def model_item(name, definition=True):
    write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymoskingdoms:item/" + name}})
    if definition:
        write_json(os.path.join(ASSETS, "items", name + ".json"),
                   {"model": {"type": "minecraft:model", "model": "jaymoskingdoms:item/" + name}})


def box(frm, to, tex, uvs=None):
    faces = {}
    for f in ("down", "up", "north", "south", "west", "east"):
        faces[f] = {"texture": tex} if not isinstance(tex, dict) else {"texture": tex.get(f, tex["*"])}
    return {"from": frm, "to": to, "faces": faces}


def block_model(name, textures, elements, display=None):
    data = {"textures": textures, "elements": elements}
    if display:
        data["display"] = display
    write_json(os.path.join(ASSETS, "models", "block", name + ".json"), data)


def main():
    for metal, ramp in RAMPS.items():
        save(coin(ramp, 1), "item", metal + "_coin")
        save(coin_stack(ramp), "item", metal + "_coin_stack")
        save(pile(ramp, {"copper": 11, "silver": 22, "gold": 33}[metal]), "block", metal + "_coin_pile")
        model_item(metal + "_coin")
        model_item(metal + "_coin_stack")
    for level in range(5):
        save(purse(level), "item", "coin_purse_%d" % level)
        model_item("coin_purse_%d" % level, False)
    # the purse picks its sprite by how full it is (custom_model_data float 0, set by PurseContents)
    entries = [{"threshold": t, "model": {"type": "minecraft:model", "model": "jaymoskingdoms:item/coin_purse_%d" % i}}
               for i, t in ((1, 0.001), (2, 0.25), (3, 0.5), (4, 0.8))]
    write_json(os.path.join(ASSETS, "items", "coin_purse.json"), {"model": {
        "type": "minecraft:range_dispatch", "property": "minecraft:custom_model_data", "index": 0, "entries": entries,
        "fallback": {"type": "minecraft:model", "model": "jaymoskingdoms:item/coin_purse_0"}}})
    save(coin_press_metal(), "block", "coin_press_metal")
    save(coin_press_base(), "block", "coin_press_base")
    save(counting_table_top(), "block", "counting_table_top")
    save(counting_table_side(), "block", "counting_table_side")

    # the Coin Press: a stone base, an iron column and an iron head over a die
    block_model("coin_press", {"base": "jaymoskingdoms:block/coin_press_base", "metal": "jaymoskingdoms:block/coin_press_metal",
                               "particle": "jaymoskingdoms:block/coin_press_metal"}, [
        box([1, 0, 1], [15, 6, 15], "#base"),
        box([6, 6, 6], [10, 14, 10], "#metal"),
        box([3, 11, 3], [13, 14, 13], "#metal"),
        box([5, 6, 11], [11, 8, 13], "#metal"),
    ])
    write_json(os.path.join(ASSETS, "blockstates", "coin_press.json"), {"variants": {"": {"model": "jaymoskingdoms:block/coin_press"}}})
    write_json(os.path.join(ASSETS, "items", "coin_press.json"), {"model": {"type": "minecraft:model", "model": "jaymoskingdoms:block/coin_press"}})

    # the Counting Table: a ruled top on four legs
    side = {"*": "#side"}
    block_model("counting_table", {"top": "jaymoskingdoms:block/counting_table_top", "side": "jaymoskingdoms:block/counting_table_side",
                                   "particle": "jaymoskingdoms:block/counting_table_side"}, [
        box([0, 12, 0], [16, 16, 16], {"*": "#side", "up": "#top", "down": "#side"}),
        box([1, 0, 1], [4, 12, 4], side), box([12, 0, 1], [15, 12, 4], side),
        box([1, 0, 12], [4, 12, 15], side), box([12, 0, 12], [15, 12, 15], side),
    ])
    write_json(os.path.join(ASSETS, "blockstates", "counting_table.json"), {"variants": {"": {"model": "jaymoskingdoms:block/counting_table"}}})
    write_json(os.path.join(ASSETS, "items", "counting_table.json"), {"model": {"type": "minecraft:model", "model": "jaymoskingdoms:block/counting_table"}})

    # the piles: one to eight layers of coin stacks
    for metal in RAMPS:
        variants = {}
        for n in range(1, 9):
            h = 2 * n
            faces = {}
            for f in ("down", "up"):
                faces[f] = {"texture": "#pile"}
            for f in ("north", "south", "west", "east"):
                faces[f] = {"texture": "#pile", "uv": [0, 16 - h, 16, 16]}
            block_model("%s_coin_stack_%d" % (metal, n),
                        {"pile": "jaymoskingdoms:block/%s_coin_pile" % metal, "particle": "jaymoskingdoms:block/%s_coin_pile" % metal},
                        [{"from": [0, 0, 0], "to": [16, h, 16], "faces": faces}])
            variants["layers=%d" % n] = {"model": "jaymoskingdoms:block/%s_coin_stack_%d" % (metal, n)}
        write_json(os.path.join(ASSETS, "blockstates", metal + "_coin_stack.json"), {"variants": variants})
        name = "jaymoskingdoms:" + metal + "_coin_stack"
        functions = [{"function": "minecraft:set_count", "count": n, "add": False, "conditions": [
            {"condition": "minecraft:block_state_property", "block": name, "properties": {"layers": str(n)}}]} for n in range(2, 9)]
        write_json(os.path.join(DATA, "loot_table", "blocks", metal + "_coin_stack.json"), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": name, "functions": functions}]}],
            "random_sequence": "jaymoskingdoms:blocks/" + metal + "_coin_stack"})
        # ten coins make a stack and a stack makes ten coins again
        write_json(os.path.join(DATA, "recipe", metal + "_coin_stack.json"), {
            "type": "minecraft:crafting_shapeless", "category": "misc", "group": "jaymoskingdoms:coin_stack",
            "ingredients": ["jaymoskingdoms:%s_coin" % metal] * 10,
            "result": {"id": name, "count": 1}})
        write_json(os.path.join(DATA, "recipe", metal + "_coin_unstack.json"), {
            "type": "minecraft:crafting_shapeless", "category": "misc", "group": "jaymoskingdoms:coin_unstack",
            "ingredients": [name], "result": {"id": "jaymoskingdoms:%s_coin" % metal, "count": 10}})

    # the press and table drop themselves
    for n in ("coin_press", "counting_table"):
        write_json(os.path.join(DATA, "loot_table", "blocks", n + ".json"), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "condition": {"type": "minecraft:survives_explosion"},
                       "entries": [{"type": "minecraft:item", "name": "jaymoskingdoms:" + n}]}],
            "random_sequence": "jaymoskingdoms:blocks/" + n})


if __name__ == "__main__":
    main()
