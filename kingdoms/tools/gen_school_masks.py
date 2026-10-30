"""Draws the five school masks (Weapon Forms, patch 4), all original, on vanilla's own shading: for each school a worn mask
(the humanoid armour layer, 64x32, drawn on the head's faces) and the same in hand (16x16).
  blade  the Quiet Tide:    pale lacquer over the eyes with a blue wave line
  spear  the Skyward Reach: sky-blue over the eyes, white feathers swept back along the temples, a gold rim
  fist   the Stone Root:    a grey brow plate and cheeks, a red cloth band round the brow
  knife  the Low Lantern:   a dark veil over the nose and mouth, one small amber lantern at the cheek
  claw   the Ashen Fang:    ember-red with three dark rake marks and amber eye slits
Run from the kingdoms project root: python -I tools/gen_school_masks.py"""
from PIL import Image
import os
import random

ROOT = "src/main/resources/assets/jaymoskingdoms/textures/"


def faces(ox, oy, w, h, d):
    return {"top": (ox + d, oy, w, d), "bottom": (ox + d + w, oy, w, d), "right": (ox, oy + d, d, h), "front": (ox + d, oy + d, w, h),
            "left": (ox + d + w, oy + d, d, h), "back": (ox + d + w + d, oy + d, w, h)}


HEAD = faces(0, 0, 8, 8, 8)
DARK = (18, 16, 20)


def clamp(c):
    return tuple(max(0, min(255, v)) for v in c)


def shade(c, by):
    return clamp(tuple(v + by for v in c))


def px(img, x, y, c):
    img.putpixel((x, y), tuple(c) + (255,) if len(c) == 3 else tuple(c))


def save(img, name):
    path = ROOT + name + ".png"
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def rows(img, face, ys, xs, colour, rnd, spread=5):
    """Fills the given rows (y) and columns (x) of a head face with a colour, a little shaded."""
    x0, y0, w, h = HEAD[face]
    for y in ys:
        for x in xs:
            px(img, x0 + x, y0 + y, shade(colour, rnd.randint(-spread, spread) - (8 if y == max(ys) else 0)))


def dot(img, face, x, y, colour):
    x0, y0, w, h = HEAD[face]
    px(img, x0 + x, y0 + y, colour)


def sides(img, ys, colour, rnd, xs=range(8)):
    rows(img, "right", ys, xs, colour, rnd)
    rows(img, "left", ys, xs, colour, rnd)


def strap(img, y, colour, rnd):
    """The tie round the back of the head."""
    rows(img, "back", [y], range(8), colour, rnd, 3)
    rows(img, "right", [y], range(8), colour, rnd, 3)
    rows(img, "left", [y], range(8), colour, rnd, 3)


# ---- worn ----------------------------------------------------------------------------------------------------------------

def blade_worn():
    rnd = random.Random("blade-worn")
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    lacquer, wave = (228, 224, 212), (62, 88, 120)
    rows(img, "front", [2, 3, 4, 5], range(8), lacquer, rnd, 4)
    sides(img, [2, 3, 4], lacquer, rnd, range(0, 5))
    for x in (1, 2, 5, 6):
        dot(img, "front", x, 3, DARK)
    # a wave line low across the mask, and a curl over the brow
    for x, y in ((0, 5), (1, 5), (2, 4), (3, 5), (4, 5), (5, 4), (6, 5), (7, 5)):
        dot(img, "front", x, y, shade(wave, rnd.randint(-6, 6)))
    for x in (3, 4):
        dot(img, "front", x, 2, wave)
    strap(img, 3, shade(wave, -18), rnd)
    save(img, "entity/equipment/humanoid/school_mask_blade")


def spear_worn():
    rnd = random.Random("spear-worn")
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    sky, white, gold = (112, 168, 214), (240, 244, 248), (222, 178, 70)
    rows(img, "front", [3, 4], range(8), sky, rnd, 4)
    for x in range(8):
        dot(img, "front", x, 2, gold if x % 2 == 0 else shade(gold, -20))
    for x in (1, 2, 5, 6):
        dot(img, "front", x, 3, DARK)
    # feathers swept back along the temples
    sides(img, [2, 3, 4], white, rnd, range(0, 8))
    sides(img, [5], shade(white, -22), rnd, range(2, 7))
    strap(img, 3, shade(sky, -30), rnd)
    save(img, "entity/equipment/humanoid/school_mask_spear")


def fist_worn():
    rnd = random.Random("fist-worn")
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    stone, red = (118, 118, 124), (156, 46, 40)
    rows(img, "front", [2], range(8), red, rnd, 4)
    rows(img, "front", [3, 4, 5], range(8), stone, rnd, 7)
    for x in (1, 2, 5, 6):
        dot(img, "front", x, 3, DARK)
    for x in (0, 7):
        for y in (4, 5):
            dot(img, "front", x, y, shade(stone, -26))
    sides(img, [2], red, rnd)
    sides(img, [3, 4], stone, rnd, range(0, 4))
    strap(img, 2, shade(red, -20), rnd)
    save(img, "entity/equipment/humanoid/school_mask_fist")


def knife_worn():
    rnd = random.Random("knife-worn")
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    veil, amber = (34, 32, 42), (232, 168, 64)
    # over the nose and mouth, and down under the chin
    rows(img, "front", [5, 6, 7], range(8), veil, rnd, 4)
    rows(img, "front", [4], range(2, 6), shade(veil, 6), rnd, 3)
    sides(img, [5, 6, 7], veil, rnd, range(0, 6))
    rows(img, "bottom", range(8), range(8), shade(veil, -6), rnd, 3)
    # the little lantern at the left cheek
    dot(img, "front", 6, 6, amber)
    dot(img, "front", 6, 7, shade(amber, -40))
    dot(img, "front", 5, 6, shade(amber, -90))
    strap(img, 5, shade(veil, 10), rnd)
    save(img, "entity/equipment/humanoid/school_mask_knife")


def claw_worn():
    rnd = random.Random("claw-worn")
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    ember, char, amber = (150, 44, 36), (42, 28, 28), (240, 176, 64)
    rows(img, "front", [2, 3, 4, 5, 6], range(8), ember, rnd, 6)
    sides(img, [2, 3, 4, 5], ember, rnd, range(0, 5))
    # three rakes down the face, and eye slits that glow
    for x in (2, 4, 6):
        for y in range(2, 7):
            dot(img, "front", x, y, char)
    for x in (1, 5):
        dot(img, "front", x, 3, amber)
    strap(img, 3, shade(char, 6), rnd)
    save(img, "entity/equipment/humanoid/school_mask_claw")


# ---- in hand -------------------------------------------------------------------------------------------------------------

def mask_shape(img, rnd, colour, outline, top=3, bottom=12):
    """A mask's face: wide at the brow, narrowing to the chin. Returns the rows and their spans."""
    spans = {}
    for y in range(top, bottom + 1):
        t = (y - top) / (bottom - top)
        half = 6 - int(3.2 * t * t)
        spans[y] = (8 - half, 8 + half - 1)
        for x in range(8 - half, 8 + half):
            px(img, x, y, shade(colour, rnd.randint(-5, 5) + (14 if y == top else 0) - (12 if y == bottom else 0)))
    for y, (a, b) in spans.items():
        px(img, a - 1, y, outline)
        px(img, b + 1, y, outline)
    for x in range(spans[top][0], spans[top][1] + 1):
        px(img, x, top - 1, outline)
    for x in range(spans[bottom][0], spans[bottom][1] + 1):
        px(img, x, bottom + 1, outline)
    return spans


def eyes(img, y, colour=DARK):
    for x in (4, 5, 10, 11):
        img.putpixel((x, y), tuple(colour) + (255,))


def blade_item():
    rnd = random.Random("blade-item")
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    mask_shape(img, rnd, (228, 224, 212), (66, 70, 82))
    eyes(img, 6)
    for x, y in ((4, 10), (5, 10), (6, 9), (7, 10), (8, 10), (9, 9), (10, 10), (11, 10)):
        px(img, x, y, (62, 88, 120))
    save(img, "item/school_mask_blade")


def spear_item():
    rnd = random.Random("spear-item")
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    mask_shape(img, rnd, (112, 168, 214), (40, 70, 110))
    eyes(img, 6)
    for x in range(3, 13):
        px(img, x, 3, (222, 178, 70))
    # feathers either side
    for (x, y) in ((0, 5), (1, 6), (0, 7), (1, 8), (15, 5), (14, 6), (15, 7), (14, 8), (0, 6), (15, 6)):
        px(img, x, y, (240, 244, 248))
    save(img, "item/school_mask_spear")


def fist_item():
    rnd = random.Random("fist-item")
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    mask_shape(img, rnd, (118, 118, 124), (52, 52, 58))
    for x in range(3, 13):
        px(img, x, 4, (156, 46, 40))
    eyes(img, 6)
    for x, y in ((3, 8), (12, 8), (4, 9), (11, 9)):
        px(img, x, y, (84, 84, 90))
    save(img, "item/school_mask_fist")


def knife_item():
    rnd = random.Random("knife-item")
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    # a veil: the lower face only, a point at the chin
    outline = (14, 12, 18)
    for y in range(6, 14):
        half = 6 - (y - 6) * 3 // 4
        for x in range(8 - half, 8 + half):
            px(img, x, y, shade((34, 32, 42), rnd.randint(-5, 5) + (10 if y == 6 else 0)))
        px(img, 8 - half - 1, y, outline)
        px(img, 8 + half, y, outline)
    for x in range(2, 14):
        px(img, x, 5, outline)
    for x in range(8 - 2, 8 + 2):
        px(img, x, 14, outline)
    px(img, 11, 9, (232, 168, 64))
    px(img, 11, 10, (150, 98, 38))
    save(img, "item/school_mask_knife")


def claw_item():
    rnd = random.Random("claw-item")
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    mask_shape(img, rnd, (150, 44, 36), (52, 22, 20))
    for x in (6, 8, 10):
        for y in range(4, 12):
            px(img, x, y, (42, 28, 28))
    px(img, 4, 6, (240, 176, 64))
    px(img, 5, 6, (240, 176, 64))
    px(img, 11, 6, (240, 176, 64))
    save(img, "item/school_mask_claw")


if __name__ == "__main__":
    for make in (blade_worn, spear_worn, fist_worn, knife_worn, claw_worn, blade_item, spear_item, fist_item, knife_item, claw_item):
        make()
