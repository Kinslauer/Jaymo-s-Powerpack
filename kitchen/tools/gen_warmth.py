"""Generates the Hardrock warmth patch's art for Jaymo's Kitchen: the hearth (stone, its mouth dark or burning), the
icebox (a plank chest with frosted ice panels) and the thermometer. Painted from scratch with seeded noise on our own
ramps (no vanilla or other mod's pixels), in vanilla's 16x16 style, plus the block and item model JSON.
Run from the kitchen root:  python -I tools/gen_warmth.py
"""
import json
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "jaymoskitchen"
TEX = ROOT / "textures"

STONE = [(84, 84, 86), (104, 104, 106), (122, 122, 124), (138, 138, 140)]
MORTAR = (66, 64, 64)
SOOT = [(30, 26, 24), (44, 38, 34)]
FIRE = [(255, 236, 140), (255, 190, 60), (236, 120, 30), (180, 60, 20)]
EMBER = [(120, 40, 20), (170, 70, 24)]
PLANK = [(98, 70, 40), (122, 90, 54), (146, 110, 68), (164, 126, 80)]
IRON = [(60, 60, 64), (96, 96, 102), (140, 140, 146)]
ICE = [(140, 180, 230), (166, 202, 242), (196, 224, 252), (232, 246, 255)]


def noise(rng, ramp, w=16, h=16, bias=0.0):
    img = Image.new("RGBA", (w, h))
    for y in range(h):
        for x in range(w):
            v = min(len(ramp) - 1, max(0, int(rng.random() * len(ramp) + bias)))
            img.putpixel((x, y), ramp[v] + (255,))
    return img


def bricks(rng):
    """Rough stone blocks in courses of 4 px, offset each course, mortar lines between."""
    img = noise(rng, STONE)
    for y in range(16):
        for x in range(16):
            course = y // 4
            off = 4 if course % 2 else 0
            if y % 4 == 3 or (x + off) % 8 == 7:
                img.putpixel((x, y), MORTAR + (255,))
    return img


def hearth_front(rng, lit):
    img = bricks(rng)
    # the mouth: an arch 8 wide, 7 tall, from the bottom
    for y in range(7, 16):
        for x in range(4, 12):
            if y == 7 and x in (4, 11):
                continue
            img.putpixel((x, y), SOOT[rng.random() < 0.3] + (255,))
    # a stone lintel over it
    for x in range(3, 13):
        img.putpixel((x, 6), STONE[3] + (255,))
    if lit:
        heights = [2, 4, 6, 5, 7, 5, 4, 2]
        for i, x in enumerate(range(4, 12)):
            for k in range(heights[i]):
                y = 15 - k
                shade = FIRE[min(3, max(0, 3 - k + (1 if rng.random() < 0.3 else 0)))] if k < heights[i] - 1 else FIRE[3]
                img.putpixel((x, y), shade + (255,))
        for x in range(4, 12):
            img.putpixel((x, 15), EMBER[rng.random() < 0.5] + (255,))
    else:
        for x in range(5, 11):
            img.putpixel((x, 15), (58, 52, 48, 255) if rng.random() < 0.6 else SOOT[1] + (255,))
    return img


def hearth_top(rng):
    img = bricks(rng)
    # the flue: a dark square
    for y in range(5, 11):
        for x in range(5, 11):
            img.putpixel((x, y), SOOT[0] + (255,) if 6 <= x <= 9 and 6 <= y <= 9 else MORTAR + (255,))
    return img


def planks(rng):
    img = noise(rng, PLANK, bias=0.3)
    for y in range(16):
        for x in range(16):
            if y % 4 == 0:
                img.putpixel((x, y), PLANK[0] + (255,))
    return img


def frame(img):
    """Iron corners and a rim round the edge."""
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel((x, y), IRON[1] + (255,))
    for (x, y) in ((0, 0), (15, 0), (0, 15), (15, 15), (1, 1), (14, 1), (1, 14), (14, 14)):
        img.putpixel((x, y), IRON[2] + (255,))
    return img


def ice_panel(rng, img, x0, y0, x1, y1):
    for y in range(y0, y1):
        for x in range(x0, x1):
            v = ICE[min(3, int(rng.random() * 3) + (1 if (x - x0) == (y - y0) or (x - x0) == (y - y0) + 1 else 0))]
            img.putpixel((x, y), v + (255,))
    for x in range(x0 - 1, x1 + 1):
        img.putpixel((x, y0 - 1), PLANK[0] + (255,))
        img.putpixel((x, y1), PLANK[0] + (255,))
    for y in range(y0 - 1, y1 + 1):
        img.putpixel((x0 - 1, y), PLANK[0] + (255,))
        img.putpixel((x1, y), PLANK[0] + (255,))


def icebox_front(rng, lit):
    img = frame(planks(rng))
    ice_panel(rng, img, 3, 3, 13, 10)
    # latch
    for (x, y) in ((7, 11), (8, 11), (7, 12), (8, 12)):
        img.putpixel((x, y), IRON[2] + (255,))
    if lit:
        # frost creeping on the wood
        for _ in range(14):
            x, y = rng.randrange(1, 15), rng.randrange(10, 15)
            img.putpixel((x, y), ICE[3] + (255,))
    return img


def icebox_side(rng):
    img = frame(planks(rng))
    ice_panel(rng, img, 4, 4, 12, 12)
    return img


def icebox_top(rng):
    img = frame(planks(rng))
    for x in range(2, 14):
        img.putpixel((x, 7), PLANK[0] + (255,))
        img.putpixel((x, 8), PLANK[3] + (255,))
    return img


THERMO = [
    "................",
    "..........kk....",
    ".........kgwk...",
    ".........kgwk...",
    "........kgggk...",
    "........kgrgk...",
    ".......kgrgk....",
    ".......kgrgk....",
    "......kgrgk.....",
    "......kgrgk.....",
    ".....kgrrgk.....",
    "....kbrrrrbk....",
    "....krrRrrrk....",
    "....kbrRRrbk....",
    ".....kbbbbk.....",
    "......kkkk......",
]
THERMO_PAL = {
    ".": (0, 0, 0, 0),
    "k": (52, 36, 18, 255),
    "b": (176, 128, 56, 255),
    "g": (200, 220, 230, 255),
    "w": (250, 252, 255, 255),
    "r": (210, 40, 30, 255),
    "R": (150, 20, 20, 255),
}


def grid(rows, pal):
    img = Image.new("RGBA", (16, 16))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            img.putpixel((x, y), pal[ch])
    return img


def save(img, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def orientable(name, front, side, top, bottom):
    return {
        "parent": "minecraft:block/orientable_with_bottom",
        "textures": {
            "front": "jaymoskitchen:block/" + front,
            "side": "jaymoskitchen:block/" + side,
            "top": "jaymoskitchen:block/" + top,
            "bottom": "jaymoskitchen:block/" + bottom,
            "particle": "jaymoskitchen:block/" + side,
        },
    }


def blockstate(name):
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for lit in ("false", "true"):
            model = {"model": "jaymoskitchen:block/" + name + ("_lit" if lit == "true" else "")}
            if y:
                model["y"] = y
            variants[f"facing={facing},lit={lit}"] = model
    return {"variants": variants}


def main():
    rng = random.Random(2611)
    save(hearth_front(rng, False), TEX / "block/hearth_front.png")
    save(hearth_front(rng, True), TEX / "block/hearth_front_lit.png")
    save(bricks(rng), TEX / "block/hearth_side.png")
    save(hearth_top(rng), TEX / "block/hearth_top.png")
    save(icebox_front(rng, False), TEX / "block/icebox_front.png")
    save(icebox_front(rng, True), TEX / "block/icebox_front_lit.png")
    save(icebox_side(rng), TEX / "block/icebox_side.png")
    save(icebox_top(rng), TEX / "block/icebox_top.png")
    save(grid(THERMO, THERMO_PAL), TEX / "item/thermometer.png")

    write(ROOT / "models/block/hearth.json", orientable("hearth", "hearth_front", "hearth_side", "hearth_top", "hearth_side"))
    write(ROOT / "models/block/hearth_lit.json", orientable("hearth", "hearth_front_lit", "hearth_side", "hearth_top", "hearth_side"))
    write(ROOT / "models/block/icebox.json", orientable("icebox", "icebox_front", "icebox_side", "icebox_top", "icebox_top"))
    write(ROOT / "models/block/icebox_lit.json", orientable("icebox", "icebox_front_lit", "icebox_side", "icebox_top", "icebox_top"))
    for name in ("hearth", "icebox"):
        write(ROOT / f"blockstates/{name}.json", blockstate(name))
        write(ROOT / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"jaymoskitchen:block/{name}"}})
    write(ROOT / "models/item/thermometer.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymoskitchen:item/thermometer"}})
    write(ROOT / "items/thermometer.json", {"model": {"type": "minecraft:model", "model": "jaymoskitchen:item/thermometer"}})


if __name__ == "__main__":
    main()
