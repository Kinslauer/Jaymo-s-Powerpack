"""The illager tongue's art (Kingdoms' IllagerTongue): their three writings (items) and their six paintings.

    python -I tools/gen_illager_tongue_art.py

Original pixel art drawn here, 16 px a block like vanilla's paintings. Writes into kingdoms/src/main/resources/assets.
"""
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms")
ITEMS = os.path.join(ROOT, "textures", "item")
PAINTINGS = os.path.join(ROOT, "textures", "painting")

# illager colours
SKIN, SKIN_DARK, NOSE = (142, 148, 148), (104, 110, 112), (120, 126, 126)
ROBE, ROBE_DARK = (58, 58, 66), (38, 38, 44)
BROW = (40, 44, 46)
OAK, OAK_DARK, OAK_LIGHT = (74, 50, 34), (52, 34, 22), (98, 70, 46)
BANNER, BANNER_GREY, BANNER_BLACK = (232, 228, 216), (150, 150, 150), (30, 30, 30)
PARCH, PARCH_DARK, INK = (222, 204, 160), (184, 160, 112), (52, 40, 34)
GOLD, TEAL = (214, 172, 64), (92, 196, 176)
EVOKER, EVOKER_TRIM = (44, 40, 52), (190, 150, 60)


def img(w, h, fill=(0, 0, 0, 0)):
    return Image.new("RGBA", (w, h), fill)


def px(im, x, y, c):
    if 0 <= x < im.width and 0 <= y < im.height:
        im.putpixel((x, y), c if len(c) == 4 else c + (255,))


def rect(im, x0, y0, x1, y1, c):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            px(im, x, y, c)


def glyph(im, x, y, rnd, c=INK):
    """One of their letters: a 3x3 mark."""
    shapes = [[(0, 0), (1, 0), (2, 0), (1, 1), (1, 2)], [(0, 0), (0, 1), (0, 2), (1, 1), (2, 2)], [(0, 2), (1, 1), (2, 0), (2, 1), (2, 2)],
              [(0, 0), (2, 0), (1, 1), (0, 2), (2, 2)], [(1, 0), (0, 1), (2, 1), (1, 2)], [(0, 0), (1, 0), (0, 1), (0, 2), (1, 2), (2, 2)]]
    for dx, dy in rnd.choice(shapes):
        px(im, x + dx, y + dy, c)


def illager(im, x, y, robe=ROBE, scale=1):
    """A standing illager, 4 wide and 9 tall at (x, y) its top left: head, brow, nose, folded arms, robe."""
    rect(im, x, y, x + 3, y + 3, SKIN)
    rect(im, x, y, x + 3, y, BROW)
    px(im, x + 1, y + 2, NOSE)
    px(im, x + 2, y + 2, NOSE)
    px(im, x + 1, y + 3, SKIN_DARK)
    px(im, x + 2, y + 3, SKIN_DARK)
    rect(im, x, y + 4, x + 3, y + 8, robe)
    rect(im, x, y + 5, x + 3, y + 5, SKIN_DARK)
    px(im, x, y + 8, ROBE_DARK)
    px(im, x + 3, y + 8, ROBE_DARK)


def small(im, x, y, robe=ROBE):
    """A far-off illager, 2 wide, 5 tall."""
    rect(im, x, y, x + 1, y + 1, SKIN)
    px(im, x, y, BROW)
    rect(im, x, y + 2, x + 1, y + 4, robe)


def ominous(im, x, y, w, h):
    """Their banner: white, the grey and black of their pattern."""
    rect(im, x, y, x + w - 1, y + h - 1, BANNER)
    cx = x + w // 2
    for i in range(h // 3):
        px(im, cx - 1, y + h // 3 + i, BANNER_BLACK)
        px(im, cx, y + h // 3 + i, BANNER_BLACK)
    rect(im, x, y + h // 2, x + w - 1, y + h // 2, BANNER_GREY)
    rect(im, x + 1, y + h // 3 - 1, x + w - 2, y + h // 3 - 1, BANNER_BLACK)
    rect(im, x, y + h - 2, x + w - 1, y + h - 1, BANNER_GREY)
    px(im, cx - 1, y + 1, BANNER_GREY)
    px(im, cx, y + 1, BANNER_GREY)


def dusk(im, top, bottom, ground_y, ground):
    for y in range(im.height):
        t = y / max(1, ground_y)
        c = tuple(int(top[i] + (bottom[i] - top[i]) * min(1, t)) for i in range(3))
        for x in range(im.width):
            px(im, x, y, c if y < ground_y else ground)


def frame(im):
    for x in range(im.width):
        px(im, x, 0, OAK_DARK)
        px(im, x, im.height - 1, OAK_DARK)
    for y in range(im.height):
        px(im, 0, y, OAK_DARK)
        px(im, im.width - 1, y, OAK_DARK)


# ---- items ---------------------------------------------------------------------------------------------------------

def scrawl():
    rnd = random.Random(11)
    im = img(16, 16)
    rect(im, 3, 2, 12, 13, PARCH)
    # torn edges
    for y in range(2, 14):
        if rnd.random() < 0.4:
            px(im, 12, y, (0, 0, 0, 0))
        if rnd.random() < 0.25:
            px(im, 3, y, PARCH_DARK)
    for x in range(3, 13):
        if rnd.random() < 0.35:
            px(im, x, 13, (0, 0, 0, 0))
        px(im, x, 2, PARCH_DARK)
    px(im, 11, 3, PARCH_DARK)
    px(im, 12, 2, (0, 0, 0, 0))
    for gy in (4, 8):
        glyph(im, 4, gy, rnd)
        glyph(im, 8, gy, rnd)
    rect(im, 4, 12, 9, 12, INK)
    return im


def ledger():
    rnd = random.Random(23)
    im = img(16, 16)
    rect(im, 3, 2, 12, 14, OAK)
    rect(im, 4, 3, 12, 13, OAK_LIGHT)
    rect(im, 3, 2, 3, 14, OAK_DARK)
    rect(im, 12, 3, 12, 13, PARCH)
    rect(im, 5, 4, 10, 11, (90, 64, 42))
    ominous(im, 6, 5, 4, 6)
    for y in range(3, 14, 3):
        px(im, 3, y, GOLD)
    return im


def grimoire():
    im = img(16, 16)
    rect(im, 3, 1, 12, 14, EVOKER)
    rect(im, 3, 1, 3, 14, (24, 22, 30))
    rect(im, 12, 2, 12, 13, PARCH)
    for (x, y) in ((4, 1), (11, 1), (4, 14), (11, 14), (4, 2), (11, 2), (4, 13), (11, 13)):
        px(im, x, y, EVOKER_TRIM)
    # a fang in teal
    for i, (x, y) in enumerate(((7, 4), (8, 4), (7, 5), (8, 5), (6, 6), (9, 6), (6, 7), (7, 7), (8, 7), (9, 7), (7, 8), (8, 8), (7, 9), (8, 9),
                                 (7, 10), (8, 10))):
        px(im, x, y, TEAL)
    px(im, 7, 11, (60, 140, 128))
    px(im, 8, 11, (60, 140, 128))
    return im


# ---- paintings -----------------------------------------------------------------------------------------------------

def raid():
    im = img(32, 16)
    dusk(im, (88, 58, 96), (214, 120, 70), 12, (52, 48, 40))
    # the banner on its pole
    rect(im, 5, 2, 5, 12, OAK_DARK)
    ominous(im, 6, 2, 5, 6)
    illager(im, 8, 4)
    illager(im, 14, 3, (52, 54, 60))
    illager(im, 20, 4)
    # crossbows over shoulders
    for x in (13, 19, 25):
        px(im, x, 7, OAK_LIGHT)
        px(im, x - 1, 8, OAK_LIGHT)
    for x in range(27, 31):
        px(im, x, 12 - (x - 27) // 2, (68, 62, 50))
    frame(im)
    return im


def warlord():
    im = img(16, 32)
    rect(im, 0, 0, 15, 31, (46, 40, 36))
    ominous(im, 4, 2, 8, 11)
    # throne
    rect(im, 2, 14, 13, 28, OAK_DARK)
    rect(im, 3, 15, 12, 27, OAK)
    illager(im, 6, 16, (34, 34, 40))
    rect(im, 5, 21, 10, 21, BANNER_GREY)
    # his axe
    rect(im, 12, 16, 12, 26, OAK_LIGHT)
    rect(im, 11, 16, 13, 18, (200, 200, 204))
    rect(im, 1, 29, 14, 30, (60, 52, 44))
    frame(im)
    return im


def rite():
    rnd = random.Random(5)
    im = img(32, 32)
    dusk(im, (24, 22, 40), (60, 44, 70), 24, (40, 36, 34))
    # the glow round him
    for y in range(6, 24):
        for x in range(8, 24):
            d = ((x - 15.5) ** 2 + (y - 15) ** 2) ** 0.5
            if d < 8 and rnd.random() < 0.5:
                px(im, x, y, (70, 110, 110))
    illager(im, 14, 10, EVOKER)
    # raised arms, gold trim
    px(im, 13, 9, SKIN_DARK)
    px(im, 12, 8, SKIN_DARK)
    px(im, 18, 9, SKIN_DARK)
    px(im, 19, 8, SKIN_DARK)
    rect(im, 14, 18, 17, 18, EVOKER_TRIM)
    # fangs out of the ground
    for fx in (4, 8, 23, 27):
        for i in range(4):
            px(im, fx, 24 - i, TEAL)
            px(im, fx + 1, 24 - i, (60, 150, 136))
        px(im, fx, 20, (200, 240, 230))
    for gx in range(3, 29, 6):
        glyph(im, gx, 27, rnd, (110, 96, 70))
    frame(im)
    return im


def glyphs():
    rnd = random.Random(9)
    im = img(16, 16)
    rect(im, 0, 0, 15, 15, PARCH)
    for y in range(1, 15):
        if rnd.random() < 0.5:
            px(im, 1, y, PARCH_DARK)
    for gy in (2, 7, 11):
        for gx in (2, 6, 10):
            glyph(im, gx, gy, rnd)
    frame(im)
    return im


def march():
    im = img(32, 16)
    dusk(im, (60, 52, 90), (190, 110, 80), 9, (60, 56, 46))
    # the road
    for x in range(32):
        px(im, x, 11, (110, 96, 74))
        px(im, x, 12, (96, 84, 64))
    # far hills
    for x in range(32):
        h = 7 + (x * 7 % 3)
        px(im, x, h, (70, 60, 72))
    for i, x in enumerate(range(3, 30, 4)):
        small(im, x, 7 - (i % 2), ROBE if i % 3 else (52, 54, 60))
    rect(im, 2, 3, 2, 10, OAK_DARK)
    ominous(im, 3, 3, 3, 4)
    frame(im)
    return im


def banner():
    rnd = random.Random(17)
    im = img(16, 32)
    rect(im, 0, 0, 15, 31, (54, 46, 40))
    rect(im, 3, 2, 12, 2, OAK_DARK)
    ominous(im, 4, 3, 8, 22)
    for gy in (5, 11, 17, 23):
        glyph(im, 1, gy, rnd, (180, 168, 140))
        glyph(im, 12, gy, rnd, (180, 168, 140))
    for gx in (2, 6, 10):
        glyph(im, gx, 27, rnd, (180, 168, 140))
    frame(im)
    return im


def main():
    os.makedirs(ITEMS, exist_ok=True)
    os.makedirs(PAINTINGS, exist_ok=True)
    for name, draw in (("illager_scrawl", scrawl), ("illager_ledger", ledger), ("illager_grimoire", grimoire)):
        draw().save(os.path.join(ITEMS, name + ".png"))
    for name, draw in (("illager_raid", raid), ("illager_warlord", warlord), ("illager_rite", rite), ("illager_glyphs", glyphs),
                       ("illager_march", march), ("illager_banner", banner)):
        draw().convert("RGB").save(os.path.join(PAINTINGS, name + ".png"))


if __name__ == "__main__":
    main()
