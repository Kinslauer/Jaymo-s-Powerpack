"""Draws the coin purse and the copper bit (0.7.498): original 16x16 vanilla-style art and the purse's JSON. Run from kingdoms/:
python -I tools/gen_coins.py   (then, from the repo root, python -I tools/excalibur_style/grade.py)

Items: copper_coin (the copper bit: a stamped disc, lit from the top-left, a little mark pressed in), coin_purse_0..4 (a
leather pouch tied with string that bulges as it fills; the item definition reads custom_model_data float 0 = fullness).
The realms' silver, dinars and the rest are Currencies' (HavenWares), and there is no Coin Press or Counting Table.
"""
import json
import math
import os

from PIL import Image

RES = os.path.join("src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymoskingdoms")
TEX = os.path.join(ASSETS, "textures")

# five-step ramps, darkest to lightest (vanilla's copper, iron and gold)
RAMPS = {
    "copper": [(0x5a, 0x2c, 0x1c), (0x8a, 0x4a, 0x2c), (0xb8, 0x6a, 0x3e), (0xd8, 0x8a, 0x58), (0xf0, 0xb0, 0x84)],
    "silver": [(0x58, 0x5e, 0x6a), (0x86, 0x8e, 0x9c), (0xb4, 0xbc, 0xc8), (0xd8, 0xde, 0xe6), (0xf6, 0xf8, 0xfc)],
    "gold": [(0x7a, 0x52, 0x10), (0xb0, 0x80, 0x1c), (0xe0, 0xb0, 0x2c), (0xf8, 0xd4, 0x48), (0xff, 0xf0, 0x9c)],
}
LEATHER = {"A": (0x35, 0x1e, 0x10), "B": (0x4f, 0x2f, 0x18), "C": (0x6e, 0x43, 0x24), "D": (0x8a, 0x58, 0x2e), "E": (0xa8, 0x72, 0x3e)}
STRING = (0xde, 0xd6, 0xbe)


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


# ---- json ------------------------------------------------------------------------------------------------------------

def model_item(name, definition=True):
    write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymoskingdoms:item/" + name}})
    if definition:
        write_json(os.path.join(ASSETS, "items", name + ".json"),
                   {"model": {"type": "minecraft:model", "model": "jaymoskingdoms:item/" + name}})


def main():
    save(coin(RAMPS["copper"], 1), "item", "copper_coin")
    model_item("copper_coin")
    for level in range(5):
        save(purse(level), "item", "coin_purse_%d" % level)
        model_item("coin_purse_%d" % level, False)
    # the purse picks its sprite by how full it is (custom_model_data float 0, set by PurseContents)
    entries = [{"threshold": t, "model": {"type": "minecraft:model", "model": "jaymoskingdoms:item/coin_purse_%d" % i}}
               for i, t in ((1, 0.001), (2, 0.25), (3, 0.5), (4, 0.8))]
    write_json(os.path.join(ASSETS, "items", "coin_purse.json"), {"model": {
        "type": "minecraft:range_dispatch", "property": "minecraft:custom_model_data", "index": 0, "entries": entries,
        "fallback": {"type": "minecraft:model", "model": "jaymoskingdoms:item/coin_purse_0"}}})


if __name__ == "__main__":
    main()
