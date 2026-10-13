"""Draws the orc camps' skins, all original (64x64: the humanoid layout plus the orc model's tusks at 0,32 / 4,32, brow at
8,32 and ears at 0,36 / 8,36): four grunts (grey-green hides in shades, red or amber eyes, war paint, a black topknot,
a hide harness and kilt, wrapped forearms), two rangers (an ash-grey hood and a quiver strap), the shaman (a bone mask
over the eyes, feathers, a robe of hides strung with bones, red hand-prints) and the warchief (an iron jaw-guard, a
scarred face, iron pauldrons, a belt of skulls). Run from the kingdoms project root: python -I tools/gen_orcs.py"""
from PIL import Image
import os
import random

ROOT = "src/main/resources/assets/jaymoskingdoms/textures/"


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
TUSKS = [faces(0, 32, 1, 2, 1), faces(4, 32, 1, 2, 1)]
BROW = faces(8, 32, 8, 1, 1)
EARS = [faces(0, 36, 1, 3, 2), faces(8, 36, 1, 3, 2)]
SIDES = ("front", "back", "right", "left")

SKINS = [(92, 112, 72), (104, 118, 78), (80, 98, 66), (110, 106, 76)]
HIDE = (52, 44, 38)
BLOOD = (110, 36, 30)
ASH = (112, 108, 100)
BONE = (226, 218, 190)
IVORY = (236, 228, 200)
HAIR = (26, 22, 20)
IRON = (128, 130, 134)


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


def head(img, rnd, skin, eyes, paint_colour, topknot=True):
    box(img, HEAD, skin, 6, rnd)
    fx, fy, fw, fh = HEAD["front"]
    # deep-set eyes under the brow (the brow itself is its own box), a flat broad nose, a heavy jaw with a dark underbite
    for ex in (fx + 1, fx + 5):
        px(img, ex, fy + 2, shade(skin, -40))
        px(img, ex + 1, fy + 2, shade(skin, -40))
        px(img, ex, fy + 3, eyes)
        px(img, ex + 1, fy + 3, (18, 12, 10))
    paint(img, (fx + 3, fy + 3, 2, 2), shade(skin, -14), 3, rnd)
    px(img, fx + 3, fy + 4, shade(skin, -40))
    px(img, fx + 4, fy + 4, shade(skin, -40))
    for x in range(fx + 1, fx + 7):
        px(img, x, fy + 6, (48, 22, 18))
    for x in (fx + 1, fx + 6):
        px(img, x, fy + 6, IVORY)
    for x in range(fx, fx + 8):
        px(img, x, fy + 7, shade(skin, -18))
    # war paint: a band across the eyes, or stripes down the cheeks
    if paint_colour:
        if rnd.random() < 0.5:
            for x in range(fx, fx + 8):
                if x not in (fx + 1, fx + 2, fx + 5, fx + 6):
                    px(img, x, fy + 3, paint_colour)
        else:
            for y in range(fy + 3, fy + 6):
                px(img, fx, y, paint_colour)
                px(img, fx + 7, y, paint_colour)
    # a shaved head with a black topknot (hat layer at the crown and down the back)
    if topknot:
        tx, ty, tw, th = HAT["top"]
        paint(img, (tx + 3, ty + 2, 2, 4), HAIR, 6, rnd)
        bx, by, bw, bh = HAT["back"]
        paint(img, (bx + 3, by, 2, 6), HAIR, 6, rnd)
    # the features: ivory tusks (darker at the root), the brow ridge, the ears (dark inside, a ring in one)
    for tusk in TUSKS:
        box(img, tusk, IVORY, 6, rnd)
        for name in SIDES:
            rx, ry, rw, rh = tusk[name]
            px(img, rx, ry + rh - 1, shade(IVORY, -40))
    box(img, BROW, shade(skin, -12), 5, rnd)
    for ear in EARS:
        box(img, ear, skin, 6, rnd)
        ex, ey, ew, eh = ear["right"]
        px(img, ex, ey + 1, (130, 80, 70))
    rx, ry, rw, rh = EARS[1]["left"]
    px(img, rx + 1, ry + 2, (200, 170, 70))


def limbs(img, rnd, skin, wraps, boots):
    for arm in ARMS:
        box(img, arm, skin, 6, rnd)
        for name in SIDES:
            rx, ry, rw, rh = arm[name]
            # thick knuckles, wrapped forearms
            for x in range(rx, rx + rw):
                px(img, x, ry + rh - 1, shade(skin, -35))
                if wraps:
                    for y in range(ry + rh - 6, ry + rh - 2):
                        px(img, x, y, shade(wraps, -12 if (y + x) % 3 == 0 else 0))
    for leg in LEGS:
        box(img, leg, skin, 6, rnd)
        paint(img, leg["bottom"], shade(boots, -10), 4, rnd)
        for name in SIDES:
            rx, ry, rw, rh = leg[name]
            paint(img, (rx, ry + rh - 4, rw, 4), boots, 6, rnd)


def kilt(img, rnd, cloth, belt):
    """A hide kilt to the knee, a belt, a harness strap across the bare chest, a scar or two."""
    for leg in LEGS:
        for name in SIDES:
            rx, ry, rw, rh = leg[name]
            for x in range(rx, rx + rw):
                for y in range(ry, ry + 5 + (x % 2)):
                    px(img, x, y, shade(cloth, rnd.randint(-14, 10)))
    for name in SIDES:
        rx, ry, rw, rh = BODY[name]
        for x in range(rx, rx + rw):
            px(img, x, ry + rh - 2, belt)
            px(img, x, ry + rh - 1, shade(cloth, -10))
    fx, fy, fw, fh = BODY["front"]
    for k in range(fw):
        px(img, fx + k, fy + min(fh - 3, 1 + k), shade(belt, -10))
    bx, by, bw, bh = BODY["back"]
    for k in range(bw):
        px(img, bx + bw - 1 - k, by + min(bh - 3, 1 + k), shade(belt, -10))


def scar(img, rnd, skin, rect):
    x0, y0, w, h = rect
    x, y = x0 + rnd.randrange(w - 2), y0 + rnd.randrange(h - 3)
    for k in range(3):
        px(img, x + k % 2, y + k, shade(skin, 35))


def grunt(i):
    rnd = random.Random("orc-grunt-%d" % i)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin = SKINS[i % len(SKINS)]
    eyes = [(220, 40, 30), (235, 160, 30), (220, 40, 30), (240, 200, 40)][i % 4]
    head(img, rnd, skin, eyes, [(230, 228, 220), BLOOD, (30, 30, 30), BLOOD][i % 4])
    box(img, BODY, skin, 6, rnd)
    scar(img, rnd, skin, BODY["front"])
    limbs(img, rnd, skin, HIDE if i % 2 == 0 else ASH, HIDE)
    kilt(img, rnd, HIDE if i % 2 else shade(HIDE, 18), (90, 66, 44))
    save(img, "entity/orc/grunt_%d" % i)


def ranger(i):
    rnd = random.Random("orc-ranger-%d" % i)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin = SKINS[(i + 1) % len(SKINS)]
    head(img, rnd, skin, (235, 160, 30), (30, 30, 30), topknot=False)
    # the hood (hat layer): over the crown and down the back, open at the face
    hood = shade(ASH, -10 * i)
    for name in ("top", "right", "left", "back"):
        rx, ry, rw, rh = HAT[name]
        paint(img, (rx, ry, rw, rh if name in ("top", "back") else 6), hood, 8, rnd)
    hx, hy, hw, hh = HAT["front"]
    paint(img, (hx, hy, hw, 2), hood, 8, rnd)
    for y in range(hy + 2, hy + 6):
        px(img, hx, y, hood)
        px(img, hx + hw - 1, y, hood)
    box(img, BODY, skin, 6, rnd)
    limbs(img, rnd, skin, ASH, HIDE)
    kilt(img, rnd, shade(HIDE, 10), (80, 60, 40))
    # a quiver strap the other way across the chest, feathers showing over the shoulder at the back
    fx, fy, fw, fh = BODY["front"]
    for k in range(fw):
        px(img, fx + fw - 1 - k, fy + min(fh - 3, 1 + k), (60, 44, 30))
    bx, by, bw, bh = BODY["back"]
    paint(img, (bx + 1, by + 1, 3, 8), (84, 60, 38), 6, rnd)
    for x in (bx + 1, bx + 3):
        px(img, x, by, (220, 220, 210))
    save(img, "entity/orc/ranger_%d" % i)


def shaman():
    rnd = random.Random("orc-shaman")
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin = (96, 108, 80)
    head(img, rnd, skin, (150, 230, 90), None, topknot=False)
    # the bone mask over the eyes and nose (hat layer front), dark eye holes; feathers up the crown
    hx, hy, hw, hh = HAT["front"]
    paint(img, (hx + 1, hy + 1, 6, 4), BONE, 6, rnd)
    for ex in (hx + 1, hx + 5):
        px(img, ex, hy + 3, (20, 14, 12))
        px(img, ex + 1, hy + 3, (20, 14, 12))
    px(img, hx + 3, hy + 4, shade(BONE, -50))
    px(img, hx + 4, hy + 4, shade(BONE, -50))
    for k, colour in enumerate([(30, 30, 30), BLOOD, (230, 230, 220), (30, 30, 30), BLOOD, (230, 230, 220)]):
        rx, ry, rw, rh = HAT["back"]
        px(img, rx + 1 + k, ry, colour)
        px(img, rx + 1 + k, ry + 1, colour)
        tx, ty, tw, th = HAT["top"]
        px(img, tx + 1 + k, ty + 6, colour)
    # a robe of hides strung with bones, red hand-prints on it
    robe = shade(HIDE, 14)
    box(img, BODY, robe, 10, rnd)
    for name in SIDES:
        rx, ry, rw, rh = BODY[name]
        for x in range(rx, rx + rw, 2):
            px(img, x, ry + 1, BONE)
    fx, fy, fw, fh = BODY["front"]
    for (dx, dy) in ((1, 5), (2, 5), (1, 6), (2, 6), (5, 7), (6, 7), (5, 8), (6, 8)):
        px(img, fx + dx, fy + dy, BLOOD)
    limbs(img, rnd, skin, None, HIDE)
    for arm in ARMS:
        for name in SIDES:
            rx, ry, rw, rh = arm[name]
            paint(img, (rx, ry, rw, 6), robe, 10, rnd)
            for x in range(rx, rx + rw):
                px(img, x, ry + 6, BONE if x % 2 else robe)
    for leg in LEGS:
        for name in SIDES:
            rx, ry, rw, rh = leg[name]
            paint(img, (rx, ry, rw, 9), robe, 10, rnd)
    save(img, "entity/orc/shaman")


def warchief():
    rnd = random.Random("orc-warchief")
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin = (86, 102, 70)
    head(img, rnd, skin, (230, 30, 20), BLOOD)
    fx, fy, fw, fh = HEAD["front"]
    # a scar down through one eye
    for y in range(fy + 1, fy + 6):
        px(img, fx + 5, y, shade(skin, 40))
    # the iron jaw-guard (hat layer, the lower face and round the sides), rivets
    hx, hy, hw, hh = HAT["front"]
    paint(img, (hx, hy + 6, hw, 2), IRON, 8, rnd)
    for x in (hx + 1, hx + 6):
        px(img, x, hy + 6, shade(IRON, 50))
    for name in ("right", "left"):
        rx, ry, rw, rh = HAT[name]
        paint(img, (rx, ry + 6, rw, 2), shade(IRON, -12), 8, rnd)
    box(img, BODY, skin, 6, rnd)
    scar(img, rnd, skin, BODY["front"])
    scar(img, rnd, skin, BODY["back"])
    limbs(img, rnd, skin, BLOOD, (40, 36, 34))
    kilt(img, rnd, (40, 30, 26), (80, 60, 40))
    # a belt of little skulls
    for name in SIDES:
        rx, ry, rw, rh = BODY[name]
        for x in range(rx, rx + rw, 3):
            px(img, x, ry + rh - 2, BONE)
            px(img, x + 1, ry + rh - 2, shade(BONE, -30))
    # iron pauldrons with spikes (the jacket layer's shoulders and the arms' tops)
    for arm in ARMS:
        paint(img, arm["top"], IRON, 10, rnd)
        for name in SIDES:
            rx, ry, rw, rh = arm[name]
            paint(img, (rx, ry, rw, 3), IRON, 10, rnd)
            px(img, rx + rw // 2, ry, shade(IRON, 60))
    save(img, "entity/orc/warchief")


def save(img, name):
    path = ROOT + name + ".png"
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


if __name__ == "__main__":
    for i in range(4):
        grunt(i)
    for i in range(2):
        ranger(i)
    shaman()
    warchief()
    print("orcs drawn")
