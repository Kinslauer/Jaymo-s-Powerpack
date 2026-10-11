"""Draws the barbarian camps' skins (64x64, the humanoid layout), one per dye colour, the war paint in the camp's
colour: the raider (wild braided hair, a rough beard, paint slashed across the eyes and down the chin, bare painted
arms, a bone-toothed necklace on a bare chest, a fur kilt, hide leg wraps) and the chief (a grizzled beard, a ritual
scar, a bear-pelt hood with bone horns, a fur mantle, gold arm rings, a skull clasp, paint in broad bands). Original
art. Run from the kingdoms project root: python -I tools/gen_barbarians.py [barbarian chief] (both when none named)."""
from PIL import Image
import os
import random
import sys

OUT = "src/main/resources/assets/jaymoskingdoms/textures/entity/{}.png"


def faces(ox, oy, w, h, d):
    """(x, y, w, h) of a box's faces in the skin."""
    return {"top": (ox + d, oy, w, d), "bottom": (ox + d + w, oy, w, d), "right": (ox, oy + d, d, h), "front": (ox + d, oy + d, w, h),
            "left": (ox + d + w, oy + d, d, h), "back": (ox + d + w + d, oy + d, w, h)}


HEAD = faces(0, 0, 8, 8, 8)
HAT = faces(32, 0, 8, 8, 8)
BODY = faces(16, 16, 8, 12, 4)
JACKET = faces(16, 32, 8, 12, 4)
ARMS = [faces(40, 16, 4, 12, 4), faces(32, 48, 4, 12, 4)]
LEGS = [faces(0, 16, 4, 12, 4), faces(16, 48, 4, 12, 4)]
SIDES = ("front", "back", "right", "left")

DYES = {"white": (233, 236, 236), "orange": (240, 118, 19), "magenta": (189, 68, 179), "light_blue": (58, 175, 217),
        "yellow": (248, 197, 39), "lime": (112, 185, 25), "pink": (237, 141, 172), "gray": (62, 68, 71),
        "light_gray": (142, 142, 134), "cyan": (21, 137, 145), "purple": (121, 42, 172), "blue": (53, 57, 157),
        "brown": (114, 71, 40), "green": (84, 109, 27), "red": (161, 39, 34), "black": (30, 30, 34)}


def clamp(c):
    return tuple(max(0, min(255, v)) for v in c)


def shade(c, by):
    return clamp(tuple(v + by for v in c))


def px(img, x, y, colour):
    img.putpixel((x, y), clamp(colour) + (255,))


def paint(img, rect, base, spread, rnd):
    x0, y0, w, h = rect
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            px(img, x, y, shade(base, rnd.randint(-spread, spread)))


def box(img, b, base, spread, rnd):
    for rect in b.values():
        paint(img, rect, base, spread, rnd)


def war_paint(colour):
    """Paint reads on skin: very pale dyes are kept as they are, dark ones lifted a little."""
    return colour if sum(colour) > 120 else shade(colour, 25)


def head(img, rnd, skin, hair, eyes, beard, paint_colour, chief):
    box(img, HEAD, skin, 8, rnd)
    fx, fy, fw, fh = HEAD["front"]
    # hair: the crown and the back of the head, long down the sides
    paint(img, HEAD["top"], hair, 14, rnd)
    for name in ("right", "left", "back"):
        rx, ry, rw, rh = HEAD[name]
        paint(img, (rx, ry, rw, rh if name == "back" else 5), hair, 14, rnd)
    paint(img, (fx, fy, fw, 2), hair, 14, rnd)
    # braids hanging at the temples
    for bx in (fx, fx + fw - 1):
        for y in range(fy + 2, fy + 6):
            px(img, bx, y, shade(hair, -20 if y % 2 else 10))
    # brows, eyes
    for ex in (fx + 1, fx + 5):
        px(img, ex, fy + 3, shade(hair, -35))
        px(img, ex + 1, fy + 3, shade(hair, -35))
        px(img, ex, fy + 4, (235, 225, 210))
        px(img, ex + 1, fy + 4, eyes)
    # war paint: a band across the eyes (the eyes left clear), and stripes down the chin
    p = war_paint(paint_colour)
    for x in range(fx, fx + fw):
        if x in (fx + 1, fx + 2, fx + 5, fx + 6):
            continue
        px(img, x, fy + 4, p)
    px(img, fx + 3, fy + 5, shade(skin, -30))
    px(img, fx + 4, fy + 5, shade(skin, -30))
    # beard and mouth
    paint(img, (fx + 1, fy + 6, fw - 2, 2), beard, 12, rnd)
    for x in range(fx + 3, fx + 5):
        px(img, x, fy + 6, (95, 45, 40))
    for name in ("right", "left"):
        rx, ry, rw, rh = HEAD[name]
        paint(img, (rx, ry + 5, rw, 3), beard, 12, rnd)
    for x in (fx + 2, fx + 5):
        px(img, x, fy + 7, p)
    if chief:
        # a ritual scar across the left cheek, through the paint
        for i in range(3):
            px(img, fx + 5 + (i % 2), fy + 3 + i, (150, 70, 65))


def fur_kilt(img, rnd, fur):
    """A fur kilt over the hips and the tops of the legs, ragged at the hem."""
    for name in SIDES:
        rx, ry, rw, rh = BODY[name]
        paint(img, (rx, ry + 9, rw, 3), fur, 18, rnd)
    for leg in LEGS:
        for name in SIDES:
            rx, ry, rw, rh = leg[name]
            for x in range(rx, rx + rw):
                for y in range(ry, ry + 3 + rnd.randint(0, 2)):
                    px(img, x, y, shade(fur, rnd.randint(-20, 20)))


def leg_wraps(img, rnd, hide, skin):
    for leg in LEGS:
        box(img, leg, skin, 8, rnd)
        for name in SIDES:
            rx, ry, rw, rh = leg[name]
            for y in range(ry + 5, ry + rh):
                c = hide if y % 2 else shade(hide, -22)
                for x in range(rx, rx + rw):
                    px(img, x, y, shade(c, rnd.randint(-6, 6)))
        paint(img, leg["bottom"], shade(hide, -35), 6, rnd)


def arms(img, rnd, skin, paint_colour, bands, ring=None):
    p = war_paint(paint_colour)
    for arm in ARMS:
        box(img, arm, skin, 8, rnd)
        for name in SIDES:
            rx, ry, rw, rh = arm[name]
            for y in bands:
                for x in range(rx, rx + rw):
                    px(img, x, ry + y, p)
            if ring:
                for x in range(rx, rx + rw):
                    px(img, x, ry + 3, shade(ring, rnd.randint(-15, 15)))
            # hide wrist wraps
            for x in range(rx, rx + rw):
                px(img, x, ry + rh - 2, (120, 85, 55))
                px(img, x, ry + rh - 1, (100, 70, 45))


def barbarian(colour_name, colour):
    rnd = random.Random("barbarian-" + colour_name)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin = (186, 132, 96)
    hair = rnd.choice([(120, 66, 30), (70, 45, 28), (160, 110, 55), (40, 30, 24)])
    head(img, rnd, skin, hair, (90, 70, 45), shade(hair, 8), colour, chief=False)
    # a bare chest, painted, a hide strap across it and a necklace of teeth
    box(img, BODY, skin, 8, rnd)
    fx, fy, fw, fh = BODY["front"]
    p = war_paint(colour)
    for y in range(fy + 3, fy + 8):
        px(img, fx + 1, y, p)
        px(img, fx + 6, y, p)
    for i in range(fw):
        px(img, fx + i, fy + min(fh - 1, i + 1), (115, 80, 50))
    for x in range(fx + 1, fx + fw - 1):
        px(img, x, fy + 1, (230, 222, 200) if x % 2 else (90, 60, 40))
    bx, by, bw, bh = BODY["back"]
    for i in range(bw):
        px(img, bx + bw - 1 - i, by + min(bh - 1, i + 1), (115, 80, 50))
    fur = (95, 70, 45)
    arms(img, rnd, skin, colour, bands=(5, 6))
    leg_wraps(img, rnd, (150, 112, 74), skin)
    fur_kilt(img, rnd, fur)
    save(img, "barbarian/" + colour_name)


def chief(colour_name, colour):
    rnd = random.Random("chief-" + colour_name)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin = (176, 124, 90)
    hair = (95, 88, 82)
    head(img, rnd, skin, hair, (140, 110, 50), (150, 145, 138), colour, chief=True)
    bear = (82, 60, 42)
    # the bear-pelt hood (hat layer): its muzzle over the brow, two bone horns, the pelt down the back
    for name in ("top", "right", "left", "back"):
        rx, ry, rw, rh = HAT[name]
        paint(img, (rx, ry, rw, rh if name in ("top", "back") else 5), bear, 18, rnd)
    hx, hy, hw, hh = HAT["front"]
    paint(img, (hx, hy, hw, 2), bear, 18, rnd)
    px(img, hx + 2, hy + 1, (20, 15, 12))
    px(img, hx + 5, hy + 1, (20, 15, 12))
    for x in (hx, hx + hw - 1):
        px(img, x, hy, (232, 224, 200))
        px(img, x, hy + 1, (215, 205, 180))
    tx, ty, tw, th = HAT["top"]
    for x in (tx, tx + tw - 1):
        px(img, x, ty + 3, (232, 224, 200))
        px(img, x, ty + 4, (232, 224, 200))
    # a bare painted chest under a fur mantle (jacket layer: shoulders and back), broad bands of paint
    box(img, BODY, skin, 8, rnd)
    fx, fy, fw, fh = BODY["front"]
    p = war_paint(colour)
    for x in range(fx, fx + fw):
        px(img, x, fy + 4, p)
        px(img, x, fy + 5, p)
    for name in SIDES:
        rx, ry, rw, rh = JACKET[name]
        depth = 3 if name == "front" else 10 if name == "back" else 5
        for x in range(rx, rx + rw):
            for y in range(ry, ry + depth + (rnd.randint(0, 1) if name != "front" else 0)):
                px(img, x, y, shade(bear, rnd.randint(-22, 22)))
    paint(img, JACKET["top"], bear, 22, rnd)
    # the mantle's skull clasp at the throat, a belt of hide with a gold buckle
    px(img, fx + 3, fy + 2, (230, 222, 200))
    px(img, fx + 4, fy + 2, (230, 222, 200))
    px(img, fx + 3, fy + 3, (40, 30, 25))
    px(img, fx + 4, fy + 3, (40, 30, 25))
    for name in SIDES:
        rx, ry, rw, rh = BODY[name]
        for x in range(rx, rx + rw):
            px(img, x, ry + 8, (90, 60, 35))
    px(img, fx + 3, fy + 8, (230, 185, 60))
    px(img, fx + 4, fy + 8, (230, 185, 60))
    arms(img, rnd, skin, colour, bands=(6, 7, 8), ring=(225, 180, 55))
    leg_wraps(img, rnd, (120, 88, 60), skin)
    fur_kilt(img, rnd, bear)
    save(img, "chief/" + colour_name)


def save(img, name):
    path = OUT.format(name)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


if __name__ == "__main__":
    wanted = sys.argv[1:] or ["barbarian", "chief"]
    for what in wanted:
        for name, colour in DYES.items():
            {"barbarian": barbarian, "chief": chief}[what](name, colour)
    print("barbarians drawn:", ", ".join(wanted))
