"""Bomb glider textures (0.13.7, Strains C): the entity's 128x64 skin and its 16x16 item sprite. Our own art.

An iron deck with rivets and a hazard stripe, a brass burner with a glowing nozzle under the tail, a bundle of red
sticks slung under the middle, leather foot straps, and two bat wings: a dark plum membrane on finger bones, scalloped
along the trailing edge (cut out), an iron spar along the leading edge.

Box layout follows ModelPart's UVs (see BombGliderModel): a box w x h x d at (u, v) has its top (model -y, the side
seen from above once the renderer flips it) at (u+d, v) and its underside at (u+d+w, v), each w x d with +x to the
right and +z (the nose, or a wing's leading edge) at the top row; then a row at v+d of west (d x h), north (-z, w x h),
east (d x h) and south (+z, w x h). Mirrored wings put their hinge on the left of the sheet too.
Run from the repository root: python -I tools/gen_bomb_glider.py
"""
import math
import random
from PIL import Image

OUT = "src/main/resources/assets/jaymospowers/textures/"
random.seed(1307)

IRON = (148, 150, 158)
IRON_DARK = (92, 94, 104)
RIVET = (204, 206, 212)
STRIPE = (214, 120, 34)
STRIPE_DARK = (40, 36, 34)
BRASS = (190, 146, 64)
GLOW = (255, 170, 60)
GLOW_HOT = (255, 236, 150)
STICK = (176, 38, 34)
WRAP = (196, 168, 118)
LEATHER = (98, 64, 38)
MEMBRANE = (78, 52, 74)
MEMBRANE_LIGHT = (104, 72, 96)
BONE = (46, 34, 44)
CLEAR = (0, 0, 0, 0)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


def noisy(c, spread=0.06):
    return shade(c, random.uniform(1.0 - spread, 1.0 + spread)) + (255,)


def face(img, x, y, w, h, c, edge=True, spread=0.06):
    for i in range(w):
        for j in range(h):
            col = noisy(c, spread)
            if edge and (i == 0 or j == 0 or i == w - 1 or j == h - 1):
                col = shade(c, 0.78) + (255,)
            img.putpixel((x + i, y + j), col)


def box(img, u, v, w, h, d, c, edge=True):
    """Every face of a box in one colour; returns the top face's corner (u+d, v)."""
    face(img, u + d, v, w, d, shade(c, 1.08), edge)
    face(img, u + d + w, v, w, d, shade(c, 0.8), edge)
    face(img, u, v + d, d, h, shade(c, 0.92), edge)
    face(img, u + d, v + d, w, h, c, edge)
    face(img, u + d + w, v + d, d, h, shade(c, 0.92), edge)
    face(img, u + 2 * d + w, v + d, w, h, c, edge)
    return u + d, v


def deck(img):
    # 12 x 2 x 24 at (0, 0): riveted plate, a hazard stripe across the tail, a grip panel down the middle
    tx, ty = box(img, 0, 0, 12, 2, 24, IRON)
    for j in range(3, 21):
        for i in range(3, 9):
            img.putpixel((tx + i, ty + j), noisy(IRON_DARK, 0.1))
    for j in range(1, 5):
        for i in range(1, 11):
            img.putpixel((tx + i, ty + 24 - 1 - j), (STRIPE if (i + j) % 4 < 2 else STRIPE_DARK) + (255,))
    for i in (1, 10):
        for j in range(2, 23, 4):
            img.putpixel((tx + i, ty + j), RIVET + (255,))
    # the underside: plain, darker, two ribs
    bx = tx + 12
    for j in range(24):
        img.putpixel((bx + 3, ty + j), shade(IRON_DARK, 0.9) + (255,))
        img.putpixel((bx + 8, ty + j), shade(IRON_DARK, 0.9) + (255,))


def nose(img):
    box(img, 0, 28, 8, 2, 4, IRON)
    box(img, 24, 28, 4, 2, 2, shade(IRON, 1.1))
    # a stripe on the nose's top
    for i in range(1, 7):
        img.putpixel((4 + i, 29), STRIPE + (255,))


def fin(img):
    # 1 x 6 x 6 at (40, 28): a bat's-ear fin of membrane on an iron edge
    box(img, 40, 28, 1, 6, 6, MEMBRANE, edge=False)
    for j in range(6):
        img.putpixel((40 + 6, 28 + 6 + j), IRON_DARK + (255,))


def burner(img):
    # 6 x 4 x 6 at (56, 28): brass, banded; the nozzle 4 x 3 x 2 at (80, 28) glows from its rear (-z) face
    box(img, 56, 28, 6, 4, 6, BRASS)
    for x in range(56, 56 + 24):
        if img.getpixel((x, 28 + 6 + 1))[3]:
            img.putpixel((x, 28 + 6 + 1), shade(BRASS, 0.7) + (255,))
    box(img, 80, 28, 4, 3, 2, IRON_DARK)
    nx, ny = 80 + 2, 28 + 2
    for i in range(4):
        for j in range(3):
            hot = 1 <= i <= 2 and j == 1
            img.putpixel((nx + i, ny + j), (GLOW_HOT if hot else GLOW) + (255,))


def sticks(img):
    # 4 x 2 x 6 at (96, 28): red sticks lengthwise, bound with two wraps
    box(img, 96, 28, 4, 2, 6, STICK, edge=False)
    for x in range(96, 96 + 20):
        for y in range(28, 28 + 8):
            if not img.getpixel((x, y))[3]:
                continue
            if (x - 96) % 2 == 1:
                img.putpixel((x, y), shade(STICK, 0.8) + (255,))
    for y in (28 + 1, 28 + 4):
        for x in range(96 + 6, 96 + 10):
            img.putpixel((x, y), WRAP + (255,))


def straps(img):
    box(img, 0, 40, 6, 1, 1, LEATHER, edge=False)


def wing(img):
    # 20 x 1 x 18 at (0, 44): top at (18, 44), underside at (38, 44), hinge on the left, leading edge on the top row
    w, d = 20, 18
    for side, (ox, oy) in enumerate(((18, 44), (38, 44))):
        light = 1.0 if side == 0 else 0.82
        for i in range(w):
            # the trailing edge: three scallops between the finger tips
            phase = (i % 7) / 7.0
            cut = int(round(3.2 * math.sin(math.pi * phase))) if i >= 3 else 0
            for j in range(d):
                if j >= d - cut:
                    img.putpixel((ox + i, oy + j), CLEAR)
                    continue
                c = MEMBRANE_LIGHT if j < 3 else MEMBRANE
                img.putpixel((ox + i, oy + j), noisy(shade(c, light), 0.08))
        # the leading-edge bone, and three fingers fanning from the root to the scallops' points
        for i in range(w):
            img.putpixel((ox + i, oy), shade(BONE, light) + (255,))
        for tip in (6, 13, 19):
            for t in range(40):
                f = t / 39.0
                x = int(round(f * tip))
                y = int(round(1 + f * (d - 2)))
                if img.getpixel((ox + x, oy + y))[3]:
                    img.putpixel((ox + x, oy + y), shade(BONE, light) + (255,))
    # the wing's edges (west/north/east/south, 1 tall) and the spar 20 x 1 x 1 at (80, 40)
    for (x, y, ww) in ((0, 62, 18), (18, 62, 20), (38, 62, 18), (56, 62, 20)):
        for i in range(ww):
            img.putpixel((x + i, y), shade(BONE, 1.1) + (255,))
    box(img, 80, 40, 20, 1, 1, IRON, edge=False)


def skin():
    img = Image.new("RGBA", (128, 64), CLEAR)
    deck(img)
    nose(img)
    fin(img)
    burner(img)
    sticks(img)
    straps(img)
    wing(img)
    img.save(OUT + "entity/bomb_glider.png")


def sprite():
    """Seen from above, nose up: the deck down the middle, bat wings spread, sticks slung, a flame at the tail."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    px = lambda x, y, c: img.putpixel((x, y), c + (255,)) if 0 <= x < 16 and 0 <= y < 16 else None
    # wings: leading edge from the deck's shoulders out and up a little, scalloped underneath
    for side in (-1, 1):
        for k in range(7):
            x = 8 + side * (k + 1) - (1 if side < 0 else 0)
            top = 6 - k // 3
            bottom = 10 - (1 if k in (2, 5) else 0) - k // 4
            for y in range(top, bottom + 1):
                px(x, y, shade(MEMBRANE, 0.9 + 0.05 * (y == top + 1)))
            px(x, top, BONE)
        for k in (2, 5):
            x = 8 + side * (k + 1) - (1 if side < 0 else 0)
            for y in range(6 - k // 3, 10 - k // 4):
                px(x, y, BONE)
    # the deck: 2 wide, a pointed nose
    for y in range(2, 14):
        for x in (7, 8):
            px(x, y, shade(IRON, 1.05 if x == 7 else 0.85))
    px(7, 1, IRON)
    px(8, 1, shade(IRON, 0.85))
    for y in (11, 12):
        px(7, y, STRIPE)
        px(8, y, STRIPE_DARK)
    # the sticks, peeking out each side of the deck
    for y in (7, 8):
        px(6, y, STICK)
        px(9, y, STICK)
    # the burner's flame
    px(7, 14, GLOW_HOT)
    px(8, 14, GLOW)
    px(7, 15, GLOW)
    img.save(OUT + "item/armory/bomb_glider.png")


if __name__ == "__main__":
    skin()
    sprite()
