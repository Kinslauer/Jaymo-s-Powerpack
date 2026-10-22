"""Draws the bounty board's and the bandits' art, all original, on vanilla's own shading: the bandit mask (a black
cloth band across the eyes, eye holes cut, knotted at the back) and the bandana (a red kerchief with white spots over the
nose and mouth, knotted at the back), each worn (the humanoid armour layer, 64x32) and in hand (16x16); and the bounty
board's face (16x12 used of 16x16: a spruce frame round dark planks, three wanted posters pinned to it). Run from the kingdoms project
root: python -I tools/gen_bounties.py"""
from PIL import Image
import os
import random

ROOT = "src/main/resources/assets/jaymoskingdoms/textures/"


def faces(ox, oy, w, h, d):
    return {"top": (ox + d, oy, w, d), "bottom": (ox + d + w, oy, w, d), "right": (ox, oy + d, d, h), "front": (ox + d, oy + d, w, h),
            "left": (ox + d + w, oy + d, d, h), "back": (ox + d + w + d, oy + d, w, h)}


HEAD = faces(0, 0, 8, 8, 8)


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


CLOTH = (38, 36, 42)
RED = (168, 38, 34)
SPOT = (226, 214, 196)


def band(img, rows, color, rnd, holes=()):
    """A cloth band round the head over {rows} (face rows 0-7 from the top), with holes on the front."""
    for name in ("front", "right", "left", "back"):
        x0, y0, w, h = HEAD[name]
        for r in rows:
            for x in range(x0, x0 + w):
                if name == "front" and (x - x0, r) in holes:
                    continue
                c = shade(color, rnd.randint(-6, 6) + (-10 if r == rows[-1] else 0) + (6 if r == rows[0] else 0))
                px(img, x, y0 + r, c)


def mask_worn():
    rnd = random.Random("bandit-mask")
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    # across the eyes (Steve's sit on face row 4, columns 1-2 and 5-6)
    band(img, (3, 4, 5), CLOTH, rnd, holes={(1, 4), (2, 4), (5, 4), (6, 4)})
    # the knot at the back and its tails
    bx, by, bw, bh = HEAD["back"]
    for (x, y) in ((3, 3), (4, 3), (3, 4), (4, 4), (3, 6), (4, 6), (2, 7), (5, 7)):
        px(img, bx + x, by + y, shade(CLOTH, 14 if y < 5 else -4))
    save(img, "entity/equipment/humanoid/bandit_mask")


def bandana_worn():
    rnd = random.Random("bandana")
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    # over the nose and mouth, below the eyes
    band(img, (5, 6, 7), RED, rnd)
    fx, fy, fw, fh = HEAD["front"]
    for (x, y) in ((1, 6), (4, 5), (6, 7), (2, 7)):
        px(img, fx + x, fy + y, SPOT)
    for name in ("right", "left"):
        x0, y0, w, h = HEAD[name]
        px(img, x0 + 3, y0 + 6, SPOT)
    # the knot at the back
    bx, by, bw, bh = HEAD["back"]
    for (x, y) in ((3, 5), (4, 5), (3, 4), (4, 4)):
        px(img, bx + x, by + y, shade(RED, 18))
    # and the bottom of the head, the cloth's fold under the chin
    ux, uy, uw, uh = HEAD["bottom"]
    for x in range(ux, ux + uw):
        px(img, x, uy + uh - 1, shade(RED, -24))
    save(img, "entity/equipment/humanoid/bandana")


def mask_item():
    rnd = random.Random("bandit-mask-item")
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    outline = (16, 15, 18)
    # the band, wider at the eyes, ties out to each side
    for x in range(2, 14):
        top, bottom = (6, 9) if 3 <= x <= 12 else (7, 8)
        for y in range(top, bottom + 1):
            px(img, x, y, shade(CLOTH, rnd.randint(-5, 5) + (26 if y == top else 12) - (14 if y == bottom else 0)))
    # the eye holes, cut through (an outline round them)
    for ex in (4, 9):
        for x in (ex, ex + 1, ex + 2):
            img.putpixel((x, 7), (0, 0, 0, 0))
        img.putpixel((ex + 1, 8), (0, 0, 0, 0))
        px(img, ex - 1, 7, outline)
        px(img, ex + 3, 7, outline)
        px(img, ex, 8, outline)
        px(img, ex + 2, 8, outline)
    for x in range(2, 14):
        px(img, x, 5 if 3 <= x <= 12 else 6, outline)
        px(img, x, 10 if 3 <= x <= 12 else 9, outline)
    # the tails of the knot
    for (x, y) in ((1, 9), (0, 10), (14, 9), (15, 10), (1, 8), (14, 8)):
        px(img, x, y, shade(CLOTH, 6))
    save(img, "item/bandit_mask")


def bandana_item():
    rnd = random.Random("bandana-item")
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    outline = (88, 18, 16)
    # a kerchief folded into a triangle, point down
    for y in range(3, 14):
        half = (13 - y) * 6 // 10 + 1
        for x in range(8 - half, 8 + half):
            px(img, x, y, shade(RED, rnd.randint(-8, 8) + (14 if y <= 4 else 0)))
        px(img, 8 - half - 1, y, outline)
        px(img, 8 + half, y, outline)
    for x in range(1, 15):
        px(img, x, 2, outline)
    for (x, y) in ((4, 5), (8, 4), (11, 5), (6, 7), (10, 8), (8, 10), (5, 4)):
        px(img, x, y, SPOT)
    # the ties
    for (x, y) in ((0, 3), (15, 3), (0, 4), (15, 4)):
        px(img, x, y, shade(RED, -10))
    save(img, "item/bandana")


def board_front():
    rnd = random.Random("bounty-board")
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    frame = (114, 84, 48)  # spruce
    plank = (66, 43, 20)   # dark oak
    for y in range(16):
        for x in range(16):
            # (the board is 12 pixels tall: rows 12-15 are never seen)
            edge = x in (0, 15) or y in (0, 11)
            c = shade(frame, rnd.randint(-8, 8) - (14 if y == 11 or x == 15 else 0)) if edge \
                else shade(plank, rnd.randint(-6, 6) - (10 if y % 5 == 0 else 0))
            px(img, x, y, c)
    paper = (222, 208, 170)
    # three posters, each: a red band (WANTED), a face, lines of writing
    for (x0, y0, w, h) in ((2, 2, 5, 7), (8, 1, 6, 6), (9, 7, 4, 3)):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                px(img, x, y, shade(paper, rnd.randint(-10, 6) - (12 if y == y0 + h - 1 else 0)))
        for x in range(x0, x0 + w):
            px(img, x, y0, (150, 40, 34))
        cx = x0 + w // 2
        px(img, cx, y0 + 2, (60, 48, 40))
        if w > 5:
            px(img, cx - 1, y0 + 2, (60, 48, 40))
        for x in range(x0 + 1, x0 + w - 1):
            if rnd.random() < 0.7:
                px(img, x, y0 + h - 2, (120, 104, 84))
        px(img, x0 + w // 2, y0, (200, 200, 205))  # the pin
    save(img, "block/bounty_board_front")


if __name__ == "__main__":
    mask_worn()
    bandana_worn()
    mask_item()
    bandana_item()
    # (0.7.168) the board is three blocks wide now: tools/vanilla_style/bounty_board.py paints it
