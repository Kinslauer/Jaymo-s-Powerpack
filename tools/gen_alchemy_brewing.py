"""Brewing quality (0.20.13): textures for the Mortar and Pestle and the Brewer's Alembic's bottle rack.

Style as in tools/gen_alchemy_crucible.py: 16x16, flat shading, a 1 px darker rim, light from the top-left. The
Brewer's Alembic's pot and bottles reuse alembic_copper.png and alembic_glass.png, which are ours.
Run from the repository root: python3 -I tools/gen_alchemy_brewing.py
"""
import os
import random

from PIL import Image

BLOCK_TEX = "src/main/resources/assets/jaymospowers/textures/block/alchemy/"
random.seed(2610)


def hexc(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


# our palette: granite greys, oak browns, pale powder
STONE = [hexc(c) for c in ("4a4a4e", "5f5f64", "76767b", "8c8c91", "a3a3a8", "bcbcc0")]
OAK = [hexc(c) for c in ("67502c", "7e6237", "967441", "9f844d", "af8f55", "b8945f", "c29d62")]
DARK_OAK = [hexc(c) for c in ("2f2113", "3e2c18", "4b3720", "5a4328", "6a4f30")]
POWDER = [hexc(c) for c in ("c9c2ae", "ddd6c2", "efe9d8")]
CLEAR = (0, 0, 0, 0)


def new():
    return Image.new("RGBA", (16, 16), CLEAR)


def save(img, name):
    os.makedirs(BLOCK_TEX, exist_ok=True)
    img.save(BLOCK_TEX + name)


def fill(img, ramp, centre, spread=True, box=(0, 0, 16, 16)):
    x0, y0, x1, y1 = box
    px = img.load()
    for x in range(x0, x1):
        for y in range(y0, y1):
            i = centre + (random.choice([-1, 0, 0, 0, 1]) if spread else 0)
            px[x, y] = ramp[max(0, min(len(ramp) - 1, i))]


def rim(img, ramp, lit, dark):
    """A 1 px frame: lighter on the top and left, darker on the bottom and right."""
    px = img.load()
    for i in range(16):
        px[i, 0] = ramp[lit]
        px[0, i] = ramp[lit]
        px[i, 15] = ramp[dark]
        px[15, i] = ramp[dark]
    px[0, 15] = ramp[dark]
    px[15, 0] = ramp[lit]


def mortar_stone():
    img = new()
    fill(img, STONE, 3)
    px = img.load()
    # granite flecks, light and dark
    for _ in range(26):
        x, y = random.randrange(1, 15), random.randrange(1, 15)
        px[x, y] = STONE[5] if random.random() < 0.5 else STONE[0]
    rim(img, STONE, 4, 1)
    save(img, "mortar_stone.png")


def mortar_inner():
    img = new()
    fill(img, STONE, 1, spread=False)
    px = img.load()
    # a pale powder ring, where the pestle has been
    for x in range(16):
        for y in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if 4.5 <= d <= 6.0 and (x + y) % 3:
                px[x, y] = POWDER[(x * 7 + y * 3) % 3]
    rim(img, STONE, 2, 0)
    save(img, "mortar_inner.png")


def pestle():
    img = new()
    px = img.load()
    # the head (top) in pale stone, then a short oak handle
    for x in range(16):
        for y in range(16):
            if y < 6:
                px[x, y] = STONE[4 if y < 2 else 3 if y < 4 else 2]
            else:
                px[x, y] = OAK[3 + (1 if x % 4 < 2 else 0)]
    for x in range(16):
        px[x, 5] = STONE[1]
        px[x, 6] = OAK[1]
        px[x, 15] = OAK[0]
    save(img, "pestle.png")


def rack():
    img = new()
    fill(img, DARK_OAK, 2)
    px = img.load()
    for x in range(16):
        px[x, 0] = DARK_OAK[4]
        px[x, 15] = DARK_OAK[0]
        px[x, 7] = DARK_OAK[1]
        px[x, 8] = DARK_OAK[3]
    save(img, "brewers_alembic_rack.png")


if __name__ == "__main__":
    mortar_stone()
    mortar_inner()
    pestle()
    rack()
