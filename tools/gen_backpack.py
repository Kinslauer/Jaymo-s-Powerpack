"""Backpack textures (0.11.82): the worn/placed model's 64x64 skin, the item sprite and the block's particle tile.

Box layout follows ModelPart's UVs: a box w x h x d at (u, v) has its top at (u+d, v) and bottom at (u+d+w, v)
(each w x d), then a row at v+d of right (d x h), front (-z, w x h), left (d x h) and back (+z, w x h).
Run from the repository root: python -I tools/gen_backpack.py
"""
import random
from PIL import Image

OUT = "src/main/resources/assets/jaymospowers/textures/"
random.seed(26)

LEATHER = (124, 80, 46)
FLAP = (98, 61, 34)
POCKET = (138, 92, 54)
STRAP = (70, 44, 26)
ROLL = (92, 112, 74)
ROPE = (186, 156, 104)
BRASS = (214, 176, 70)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


def noisy(c):
    return shade(c, random.uniform(0.9, 1.07)) + (255,)


def face(img, x, y, w, h, c, stitch=True):
    for i in range(w):
        for j in range(h):
            col = noisy(c)
            if stitch and (i == 0 or j == 0 or i == w - 1 or j == h - 1):
                col = shade(c, 0.78) + (255,)
            img.putpixel((x + i, y + j), col)


def box(img, u, v, w, h, d, c, stitch=True):
    face(img, u + d, v, w, d, shade(c, 1.08), stitch)
    face(img, u + d + w, v, w, d, shade(c, 0.85), stitch)
    face(img, u, v + d, d, h, shade(c, 0.95), stitch)
    face(img, u + d, v + d, w, h, c, stitch)
    face(img, u + d + w, v + d, d, h, shade(c, 0.95), stitch)
    face(img, u + 2 * d + w, v + d, w, h, c, stitch)
    return (u + 2 * d + w, v + d)  # the back (+z, outer) face


def skin():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    # bag 8x10x4 at (0,0): two seams down its outer face
    bx, by = box(img, 0, 0, 8, 10, 4, LEATHER)
    for j in range(1, 9):
        img.putpixel((bx + 2, by + j), shade(LEATHER, 0.8) + (255,))
        img.putpixel((bx + 5, by + j), shade(LEATHER, 0.8) + (255,))
    # bedroll 10x3x3 at (0,16): wool with two rope bands
    box(img, 0, 16, 10, 3, 3, ROLL, stitch=False)
    for x in (3 + 2, 3 + 7, 16 + 2, 16 + 7, 3 + 10 + 3 + 2, 3 + 10 + 3 + 7):
        for y in range(16, 22):
            if img.getpixel((x, y))[3]:
                img.putpixel((x, y), noisy(ROPE))
    # front pocket 5x4x2 at (28,0), side pockets 2x5x3 at (44,0)
    px, py = box(img, 28, 0, 5, 4, 2, POCKET)
    img.putpixel((px + 2, py), BRASS + (255,))
    box(img, 44, 0, 2, 5, 3, POCKET)
    # flap: top 9x1x5 at (0,24), drop 9x4x1 at (0,32), buckle 2x2x1 at (22,32)
    box(img, 0, 24, 9, 1, 5, FLAP)
    box(img, 0, 32, 9, 4, 1, FLAP)
    box(img, 22, 32, 2, 2, 1, BRASS, stitch=False)
    # straps: over the shoulder 2x1x5 at (32,24), down the chest 2x5x1 at (32,32)
    box(img, 32, 24, 2, 1, 5, STRAP, stitch=False)
    box(img, 32, 32, 2, 5, 1, STRAP, stitch=False)
    img.save(OUT + "entity/backpack.png")


def sprite():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    def rect(x0, y0, x1, y1, c, edge=None):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                col = noisy(c)
                if edge and (x in (x0, x1) or y in (y0, y1)):
                    col = edge + (255,)
                img.putpixel((x, y), col)
    outline = (40, 24, 12)
    rect(6, 0, 9, 1, STRAP)                    # carry loop
    rect(3, 2, 12, 12, LEATHER, outline)       # bag
    rect(3, 2, 12, 6, FLAP, outline)           # flap
    rect(7, 6, 8, 7, BRASS)                    # buckle
    rect(5, 8, 10, 11, POCKET, outline)        # pocket
    rect(2, 12, 13, 14, ROLL, outline)         # bedroll
    for y in range(12, 15):
        img.putpixel((5, y), ROPE + (255,))
        img.putpixel((10, y), ROPE + (255,))
    rect(1, 5, 2, 10, POCKET, outline)         # side pockets
    rect(13, 5, 14, 10, POCKET, outline)
    img.save(OUT + "item/backpack.png")


def particle():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    face(img, 0, 0, 16, 16, LEATHER)
    img.save(OUT + "block/backpack.png")


if __name__ == "__main__":
    import os
    for sub in ("entity", "item", "block"):
        os.makedirs(OUT + sub, exist_ok=True)
    skin()
    sprite()
    particle()
