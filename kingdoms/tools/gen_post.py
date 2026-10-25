"""Draws the post's art, all original: the messenger pigeon (a rock dove on the parrot model's 32x32 layout: blue-grey,
a green and violet sheen on the neck, two black bars on each wing, a dark-tipped tail, coral feet, an orange eye; the
parrot's head feather left clear) and the letters (16x16: a blank folded letter, and the same sealed with red wax).
Run from the kingdoms project root: python -I tools/gen_post.py"""
from PIL import Image
import os
import random

ROOT = "src/main/resources/assets/jaymoskingdoms/textures/"
rng = random.Random(26_3_200)


def clamp(c):
    return tuple(max(0, min(255, int(v))) for v in c)


def jitter(c, by=8):
    d = rng.randint(-by, by)
    return clamp((c[0] + d, c[1] + d, c[2] + d))


def faces(u, v, w, h, d):
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}


def fill(img, rect, colour, by=8):
    x0, y0, w, h = rect
    for x in range(x0, x0 + w):
        for y in range(y0, y0 + h):
            img.putpixel((x, y), jitter(colour, by) + (255,))


def box(img, u, v, w, h, d, colour, by=8):
    for rect in faces(u, v, w, h, d).values():
        fill(img, rect, colour, by)
    return faces(u, v, w, h, d)


def row(img, rect, r, colour, by=6):
    x0, y0, w, h = rect
    for x in range(x0, x0 + w):
        img.putpixel((x, y0 + r), jitter(colour, by) + (255,))


GREY = (138, 146, 162)
BELLY = (152, 158, 172)
DARK = (104, 111, 128)
WING = (168, 174, 186)
BAR = (34, 34, 40)
TIP = (88, 93, 106)
GREEN = (78, 140, 112)
VIOLET = (126, 88, 142)
BEAK = (58, 58, 64)
CERE = (232, 230, 222)
FEET = (204, 84, 92)
EYE = (232, 124, 40)


def pigeon():
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    # body (its top end is the neck): the sheen round the neck, a paler breast
    body = box(img, 2, 8, 3, 6, 3, GREY)
    fill(img, body["top"], DARK)
    for side in ("right", "front", "left", "back"):
        x0, y0, w, h = body[side]
        for x in range(x0, x0 + w):
            for r in range(2):
                img.putpixel((x, y0 + r), jitter(GREEN if (x + r) % 2 == 0 else VIOLET, 6) + (255,))
    x0, y0, w, h = body["front"]
    fill(img, (x0, y0 + 2, w, h - 2), BELLY)
    # wings: grey, two black bars, dark tips
    wing = box(img, 19, 8, 1, 5, 3, WING)
    for side in ("right", "front", "left", "back"):
        row(img, wing[side], 2, BAR)
        row(img, wing[side], 3, BAR)
        row(img, wing[side], 4, TIP)
    # tail: grey, a black band at the end
    tail = box(img, 22, 1, 3, 4, 1, DARK)
    for side in ("right", "front", "left", "back"):
        row(img, tail[side], 3, BAR)
    fill(img, tail["bottom"], BAR)
    # head and crown: dark blue-grey, an orange eye each side
    head = box(img, 2, 2, 2, 3, 2, DARK)
    box(img, 10, 0, 2, 1, 4, DARK)
    img.putpixel((3, 4), EYE + (255,))
    img.putpixel((6, 4), EYE + (255,))
    # beak (both parts): dark, the white cere on top
    for u in (11, 16):
        b = box(img, u, 7, 1, 2, 1, BEAK, 4)
        fill(img, b["top"], CERE, 4)
        for side in ("right", "front", "left", "back"):
            row(img, b[side], 0, CERE, 4)
    # feet
    box(img, 14, 18, 1, 2, 1, FEET, 6)
    os.makedirs(ROOT + "entity", exist_ok=True)
    img.save(ROOT + "entity/pigeon.png")


PAPER = (232, 222, 196)
PAPER_DARK = (196, 182, 150)
EDGE = (120, 104, 78)
WAX = (176, 34, 38)
WAX_LIGHT = (214, 72, 70)
WAX_DARK = (118, 20, 26)


def letter(sealed):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    x0, y0, x1, y1 = 1, 4, 14, 12
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            edge = x in (x0, x1) or y in (y0, y1)
            img.putpixel((x, y), (EDGE if edge else jitter(PAPER, 5)) + (255,))
    # the flap's fold: a V from the top corners to the middle
    mid = (x0 + x1) // 2
    for i in range(0, mid - x0 + 1):
        y = y0 + 1 + i * (y1 - y0 - 3) // (mid - x0)
        for x in (x0 + i, x1 - i):
            if y < y1:
                img.putpixel((x, y), PAPER_DARK + (255,))
    if sealed:
        cx, cy = mid, y0 + (y1 - y0) // 2 + 1
        for dx in range(-2, 3):
            for dy in range(-2, 3):
                if dx * dx + dy * dy <= 5:
                    c = WAX_DARK if dx * dx + dy * dy >= 4 else WAX
                    img.putpixel((cx + dx, cy + dy), c + (255,))
        img.putpixel((cx - 1, cy - 1), WAX_LIGHT + (255,))
    else:
        # a few lines of ink showing through
        for y in (y0 + 5, y0 + 6):
            for x in range(x0 + 3, x1 - 2):
                if rng.random() < 0.6:
                    img.putpixel((x, y), (150, 140, 120, 255))
    os.makedirs(ROOT + "item", exist_ok=True)
    img.save(ROOT + "item/" + ("sealed_letter" if sealed else "letter") + ".png")


if __name__ == "__main__":
    pigeon()
    letter(False)
    letter(True)
