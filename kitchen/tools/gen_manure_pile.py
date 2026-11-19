"""Dung heap (ManurePileBlock): texture, blockstate and the 8 heap models, and prints the outlines for SHAPES.

Each pat adds one lump: the first three spread out on the ground, the rest heap up in tiers to half a block.
Run from kitchen/: python3 tools/gen_manure_pile.py
"""
import json
import os
import random

from PIL import Image

ASSETS = "src/main/resources/assets/jaymoskitchen"
MAX = 8

# (x0, z0, x1, z1, y0, y1) per pat, in pixels
LUMPS = [
    (5, 5, 10, 10, 0, 2),
    (8, 7, 12, 12, 0, 2),
    (4, 7, 8, 11, 0, 2),
    (5, 5, 11, 11, 2, 4),
    (7, 4, 12, 8, 0, 2),
    (6, 6, 10, 10, 4, 6),
    (3, 3, 7, 7, 0, 3),
    (7, 7, 9, 9, 6, 8),
]


def texture():
    rnd = random.Random(7)
    dark, mid, light, straw = (52, 36, 19), (78, 55, 30), (101, 74, 41), (166, 140, 72)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            r = rnd.random()
            c = dark if r < 0.3 else mid if r < 0.75 else light
            if rnd.random() < 0.05:
                c = straw
            v = rnd.randint(-6, 6)
            img.putpixel((x, y), (max(0, c[0] + v), max(0, c[1] + v), max(0, c[2] + v), 255))
    # a few dark cracks
    for _ in range(5):
        x, y = rnd.randrange(16), rnd.randrange(16)
        for _ in range(rnd.randint(2, 4)):
            img.putpixel((x % 16, y % 16), (34, 23, 12, 255))
            x += rnd.choice((-1, 0, 1))
            y += rnd.choice((0, 1))
    img.save(f"{ASSETS}/textures/block/manure_pile.png")


def box(x0, z0, x1, z1, y0, y1):
    side = lambda a, b: [a, 16 - y1, b, 16 - y0]
    top = [x0, z0, x1, z1]
    return {
        "from": [x0, y0, z0],
        "to": [x1, y1, z1],
        "faces": {
            "north": {"texture": "#all", "uv": side(x0, x1)},
            "south": {"texture": "#all", "uv": side(x0, x1)},
            "east": {"texture": "#all", "uv": side(z0, z1)},
            "west": {"texture": "#all", "uv": side(z0, z1)},
            "up": {"texture": "#all", "uv": top},
            "down": {"texture": "#all", "uv": top},
        },
    }


def lump(x0, z0, x1, z1, y0, y1):
    """A lump with a rounded top: the box, and a cap one pixel in and one pixel up if it's wide enough."""
    parts = [box(x0, z0, x1, z1, y0, y1)]
    if x1 - x0 >= 4 and z1 - z0 >= 4:
        parts.append(box(x0 + 1, z0 + 1, x1 - 1, z1 - 1, y1, y1 + 1))
    return parts


def rotated(x0, z0, x1, z1, turns):
    for _ in range(turns):
        x0, z0, x1, z1 = 16 - z1, x0, 16 - z0, x1
    return x0, z0, x1, z1


def main():
    texture()
    variants = {}
    shapes = []
    for n in range(1, MAX + 1):
        elements = [e for l in LUMPS[:n] for e in lump(*l)]
        model = {
            "parent": "minecraft:block/block",
            "textures": {"all": "jaymoskitchen:block/manure_pile", "particle": "jaymoskitchen:block/manure_pile"},
            "elements": elements,
        }
        with open(f"{ASSETS}/models/block/manure_pile_{n}.json", "w") as f:
            json.dump(model, f, indent=2)
            f.write("\n")
        variants[f"pats={n}"] = [{"model": f"jaymoskitchen:block/manure_pile_{n}", **({"y": 90 * t} if t else {})} for t in range(4)]
        # outline: covers every rotation the blockstate picks
        xs, zs = [], []
        for e in elements:
            for t in range(4):
                x0, z0, x1, z1 = rotated(e["from"][0], e["from"][2], e["to"][0], e["to"][2], t)
                xs += [x0, x1]
                zs += [z0, z1]
        top = max(e["to"][1] for e in elements)
        shapes.append(f"Block.box({min(xs)}.0, 0.0, {min(zs)}.0, {max(xs)}.0, {top}.0, {max(zs)}.0),")
    with open(f"{ASSETS}/blockstates/manure_pile.json", "w") as f:
        json.dump({"variants": variants}, f, indent=2)
        f.write("\n")
    print("\n".join(shapes))


if __name__ == "__main__":
    os.chdir(os.path.join(os.path.dirname(__file__), ".."))
    main()
