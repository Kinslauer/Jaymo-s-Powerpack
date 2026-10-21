"""Draws the sailors' skins, all original (64x64, the humanoid layout): four pirate deckhands (sun-browned faces, stubble
or a beard, a red or black bandana, a striped shirt, tarred breeches, bare feet), two gunners (powder-blackened faces
and hands, a leather jerkin, a powder horn's strap), two powder monkeys (boys: no beard, a ragged shirt, sooty cheeks),
the pirate captain (a black tricorn with gold edging, a great black beard, an eyepatch, a long red coat with gold
buttons), the shipwright (a flat cap, a leather apron, tools at the belt) and the sea captain (a blue coat with gold
epaulettes and buttons, white breeches, a bicorne). (0.7.106) Two bombardiers: soot-streaked faces with singed brows,
a bandana, a striped shirt with its sleeves rolled, and a leather bandolier across the chest with red sticks of dynamite in
its loops (and more at the belt). Run from the kingdoms project root: python -I tools/gen_pirates.py"""
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
SLEEVES = [faces(40, 32, 4, 12, 4), faces(48, 48, 4, 12, 4)]
LEGS = [faces(0, 16, 4, 12, 4), faces(16, 48, 4, 12, 4)]
SIDES = ("front", "back", "right", "left")

TONES = [(176, 124, 88), (150, 100, 70), (198, 146, 108), (118, 80, 56)]
HAIRS = [(34, 26, 20), (70, 46, 28), (110, 74, 40), (52, 50, 48)]
RED = (150, 36, 32)
BLACK_CLOTH = (36, 32, 30)
WHITE = (214, 208, 192)
NAVY = (34, 48, 82)
TAR = (44, 40, 36)
LEATHER = (102, 70, 42)
GOLD = (214, 172, 60)
SOOT = (40, 36, 34)
COAT_RED = (118, 26, 24)


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


def rows(img, b, top, count, base, spread, rnd, sides=SIDES):
    """Paints {count} rows from {top} down the given sides of a box."""
    for side in sides:
        x0, y0, w, h = b[side]
        paint(img, (x0, y0 + top, w, min(count, h - top)), base, spread, rnd)


def face(img, rnd, skin, hair, beard, eyes=(40, 60, 90), patch=False, soot=False):
    box(img, HEAD, skin, 5, rnd)
    # hair over the top, back and sides
    paint(img, HEAD["top"], hair, 6, rnd)
    for side in ("back", "right", "left"):
        x0, y0, w, h = HEAD[side]
        paint(img, (x0, y0, w, 3 if side != "back" else 6), hair, 6, rnd)
    fx, fy, fw, fh = HEAD["front"]
    paint(img, (fx, fy, fw, 1), hair, 6, rnd)
    # eyes, a nose, a mouth
    for ex in (fx + 1, fx + 5):
        px(img, ex, fy + 3, WHITE)
        px(img, ex + 1, fy + 3, eyes)
    px(img, fx + 3, fy + 4, shade(skin, -24))
    px(img, fx + 4, fy + 4, shade(skin, -18))
    for x in range(fx + 2, fx + 6):
        px(img, x, fy + 6, shade(skin, -46))
    if patch:
        # an eyepatch and its strap across the brow
        px(img, fx + 5, fy + 3, (12, 10, 10))
        px(img, fx + 6, fy + 3, (12, 10, 10))
        px(img, fx + 5, fy + 2, (12, 10, 10))
        for x in range(fx, fx + 8):
            if x not in (fx + 5, fx + 6):
                px(img, x, fy + 2 if x > fx + 3 else fy + 1, (24, 20, 20))
    if beard == "stubble":
        for y in (fy + 5, fy + 6, fy + 7):
            for x in range(fx + 1, fx + 7):
                if rnd.random() < 0.45 and not (y == fy + 6 and fx + 2 <= x < fx + 6):
                    px(img, x, y, shade(hair, rnd.randint(-6, 12)))
    elif beard == "full":
        for y in (fy + 5, fy + 6, fy + 7):
            for x in range(fx, fx + 8):
                if not (y == fy + 6 and fx + 3 <= x < fx + 5):
                    px(img, x, y, shade(hair, rnd.randint(-8, 8)))
        for side in ("right", "left"):
            x0, y0, w, h = HEAD[side]
            paint(img, (x0, y0 + 5, w, 3), hair, 6, rnd)
    if soot:
        for _ in range(7):
            px(img, fx + rnd.randint(0, 7), fy + rnd.randint(3, 7), shade(SOOT, rnd.randint(-8, 20)))


def bandana(img, rnd, cloth):
    """A cloth tied over the head (the hat layer's top and upper rows), the knot's tails at the back."""
    paint(img, HAT["top"], cloth, 8, rnd)
    for side in SIDES:
        x0, y0, w, h = HAT[side]
        paint(img, (x0, y0, w, 2), cloth, 8, rnd)
    bx, by, bw, bh = HAT["back"]
    paint(img, (bx + 3, by + 2, 2, 3), cloth, 8, rnd)


def tricorn(img, rnd, felt, trim):
    paint(img, HAT["top"], felt, 5, rnd)
    for side in SIDES:
        x0, y0, w, h = HAT[side]
        paint(img, (x0, y0, w, 3), felt, 5, rnd)
        for x in range(x0, x0 + w):
            px(img, x, y0 + 2, trim)
    fx, fy, fw, fh = HAT["front"]
    px(img, fx + 3, fy, trim)
    px(img, fx + 4, fy, trim)


def cap(img, rnd, cloth):
    paint(img, HAT["top"], cloth, 6, rnd)
    for side in SIDES:
        x0, y0, w, h = HAT[side]
        paint(img, (x0, y0, w, 1), cloth, 6, rnd)
    fx, fy, fw, fh = HAT["front"]
    paint(img, (fx, fy + 1, fw, 1), shade(cloth, -20), 4, rnd)


def striped(img, rnd, a, b):
    """A sailor's shirt: rows of {a} and {b} round the body and down the sleeves."""
    for part in [BODY] + ARMS:
        for side in SIDES:
            x0, y0, w, h = part[side]
            for y in range(h):
                c = a if (y // 2) % 2 == 0 else b
                paint(img, (x0, y0 + y, w, 1), c, 5, rnd)
        paint(img, part["top"], a, 5, rnd)
        paint(img, part["bottom"], b, 5, rnd)


def hands(img, rnd, skin, soot=False):
    for arm in ARMS:
        for side in SIDES:
            x0, y0, w, h = arm[side]
            paint(img, (x0, y0 + h - 3, w, 3), soot and SOOT or skin, 6, rnd)
        paint(img, arm["bottom"], soot and SOOT or skin, 6, rnd)


def legs(img, rnd, cloth, feet, bare=False, skin=None):
    for leg in LEGS:
        box(img, leg, cloth, 6, rnd)
        rows(img, leg, 9, 3, (skin if bare else feet), 6, rnd)
        paint(img, leg["bottom"], skin if bare else feet, 6, rnd)
        # a rolled cuff at the shin
        rows(img, leg, 8, 1, shade(cloth, 18), 4, rnd)


def belt(img, rnd, colour, buckle):
    rows(img, BODY, 8, 1, colour, 4, rnd)
    fx, fy, fw, fh = BODY["front"]
    px(img, fx + 3, fy + 8, buckle)
    px(img, fx + 4, fy + 8, buckle)


def coat(img, rnd, cloth, trim, buttons=GOLD):
    """A long coat on the jacket layer and sleeves, open down the front over the shirt, with its buttons."""
    for side in SIDES:
        x0, y0, w, h = JACKET[side]
        paint(img, (x0, y0, w, h), cloth, 6, rnd)
    paint(img, JACKET["top"], cloth, 6, rnd)
    fx, fy, fw, fh = JACKET["front"]
    # open down the middle (clear: the shirt shows), the lapels in trim
    for y in range(fy, fy + 7):
        img.putpixel((fx + 3, y), (0, 0, 0, 0))
        img.putpixel((fx + 4, y), (0, 0, 0, 0))
        px(img, fx + 2, y, trim)
        px(img, fx + 5, y, trim)
    for y in (fy + 2, fy + 4, fy + 6, fy + 9):
        px(img, fx + 1, y, buttons)
        px(img, fx + 6, y, buttons)
    for sleeve in SLEEVES:
        for side in SIDES:
            x0, y0, w, h = sleeve[side]
            paint(img, (x0, y0, w, h - 3), cloth, 6, rnd)
            paint(img, (x0, y0 + h - 4, w, 1), trim, 4, rnd)
        paint(img, sleeve["top"], cloth, 6, rnd)


def blank():
    return Image.new("RGBA", (64, 64), (0, 0, 0, 0))


def deckhand(i):
    rnd = random.Random(7000 + i)
    img = blank()
    skin, hair = TONES[i % len(TONES)], HAIRS[(i * 3) % len(HAIRS)]
    face(img, rnd, skin, hair, "full" if i % 2 else "stubble")
    bandana(img, rnd, RED if i % 2 == 0 else BLACK_CLOTH)
    striped(img, rnd, WHITE, RED if i == 1 else NAVY if i != 3 else BLACK_CLOTH)
    hands(img, rnd, skin)
    legs(img, rnd, TAR, TAR, bare=i % 2 == 0, skin=skin)
    belt(img, rnd, LEATHER, GOLD)
    if i == 2:
        # a gold earring
        x0, y0, w, h = HEAD["left"]
        px(img, x0 + 3, y0 + 5, GOLD)
    save(img, "entity/pirate/deckhand_%d" % i)


def gunner(i):
    rnd = random.Random(7100 + i)
    img = blank()
    skin, hair = TONES[(i + 1) % len(TONES)], HAIRS[i % len(HAIRS)]
    face(img, rnd, skin, hair, "stubble", soot=True)
    bandana(img, rnd, BLACK_CLOTH if i == 0 else RED)
    box(img, BODY, WHITE, 6, rnd)
    for arm in ARMS:
        box(img, arm, WHITE, 6, rnd)
    # a leather jerkin over the shirt, a powder horn's strap across it
    for side in SIDES:
        x0, y0, w, h = JACKET[side]
        paint(img, (x0, y0, w, 10), LEATHER, 7, rnd)
    fx, fy, fw, fh = BODY["front"]
    for k in range(8):
        px(img, fx + k, fy + 1 + k, (60, 40, 22))
    hands(img, rnd, skin, soot=True)
    legs(img, rnd, TAR, (30, 26, 24))
    save(img, "entity/pirate/gunner_%d" % i)


def powder_monkey(i):
    rnd = random.Random(7200 + i)
    img = blank()
    skin, hair = TONES[(i + 2) % len(TONES)], HAIRS[(i + 2) % len(HAIRS)]
    face(img, rnd, skin, hair, None, soot=True)
    box(img, BODY, shade(WHITE, -36), 12, rnd)
    for arm in ARMS:
        box(img, arm, shade(WHITE, -36), 12, rnd)
    # torn sleeves to the elbow, bare forearms
    for arm in ARMS:
        rows(img, arm, 5, 7, skin, 6, rnd)
        paint(img, arm["bottom"], skin, 6, rnd)
    legs(img, rnd, (88, 76, 60), skin, bare=True, skin=skin)
    save(img, "entity/pirate/powder_monkey_%d" % i)


DYNAMITE = (196, 51, 32)
DYNAMITE_LIT = (220, 76, 46)
DYNAMITE_CAP = (210, 201, 177)


def bombardier(i):
    rnd = random.Random(7600 + i)
    img = blank()
    skin, hair = TONES[(i + 3) % len(TONES)], HAIRS[(i + 1) % len(HAIRS)]
    face(img, rnd, skin, hair, "stubble" if i == 0 else "full", soot=True)
    # singed brows: the hair row over the eyes scorched to soot
    fx, fy, fw, fh = HEAD["front"]
    for x in (fx + 1, fx + 2, fx + 5, fx + 6):
        px(img, x, fy + 2, shade(SOOT, rnd.randint(-6, 10)))
    bandana(img, rnd, RED if i == 0 else BLACK_CLOTH)
    striped(img, rnd, WHITE, BLACK_CLOTH if i == 0 else NAVY)
    # sleeves rolled to the elbow
    for arm in ARMS:
        rows(img, arm, 6, 6, skin, 6, rnd)
    hands(img, rnd, skin, soot=True)
    legs(img, rnd, TAR, (30, 26, 24), bare=i == 1, skin=skin)
    belt(img, rnd, LEATHER, (150, 150, 156))
    # the bandolier, two pixels wide from the right shoulder down to the left hip, three sticks of dynamite standing in it
    bx, by, bw, bh = BODY["front"]
    for k in range(9):
        for w in (0, 1):
            x, y = bx + 7 - k + w, by + k
            if bx <= x < bx + bw and y < by + bh:
                px(img, x, y, (84, 56, 32) if w == 0 else (110, 76, 44))
    for k in (1, 4, 7):
        x, y = bx + 7 - k, by + k
        px(img, x, y - 1, DYNAMITE_CAP)
        px(img, x, y, DYNAMITE_LIT)
        px(img, x, y + 1, DYNAMITE)
        px(img, x, y + 2, shade(DYNAMITE, -40))
    # and the strap over the back
    kx, ky, kw, kh = BODY["back"]
    for k in range(9):
        px(img, kx + min(k, kw - 1), ky + k, (84, 56, 32))
    save(img, "entity/pirate/bombardier_%d" % i)


def captain():
    rnd = random.Random(7300)
    img = blank()
    skin, hair = TONES[0], (22, 18, 16)
    face(img, rnd, skin, hair, "full", eyes=(70, 40, 30), patch=True)
    tricorn(img, rnd, (26, 22, 22), GOLD)
    box(img, BODY, WHITE, 5, rnd)
    for arm in ARMS:
        box(img, arm, WHITE, 5, rnd)
    hands(img, rnd, skin)
    coat(img, rnd, COAT_RED, shade(GOLD, -30))
    belt(img, rnd, (30, 24, 20), GOLD)
    legs(img, rnd, (30, 26, 26), (20, 16, 16))
    save(img, "entity/pirate/captain")


def shipwright():
    rnd = random.Random(7400)
    img = blank()
    skin, hair = TONES[2], (120, 116, 108)
    face(img, rnd, skin, hair, "full")
    cap(img, rnd, (74, 62, 50))
    box(img, BODY, (150, 128, 96), 6, rnd)
    for arm in ARMS:
        box(img, arm, (150, 128, 96), 6, rnd)
        rows(img, arm, 6, 6, skin, 6, rnd)
    # his apron, a hammer and chisel at his belt
    fx, fy, fw, fh = BODY["front"]
    paint(img, (fx + 1, fy + 2, 6, 10), LEATHER, 8, rnd)
    belt(img, rnd, (60, 40, 22), (150, 150, 156))
    px(img, fx + 1, fy + 9, (150, 150, 156))
    px(img, fx + 6, fy + 9, (110, 80, 50))
    legs(img, rnd, (80, 72, 60), (54, 40, 28))
    save(img, "entity/pirate/shipwright")


def sea_captain():
    rnd = random.Random(7500)
    img = blank()
    skin, hair = TONES[2], (90, 64, 40)
    face(img, rnd, skin, hair, None)
    # a bicorne: the tricorn's black felt, worn side to side
    tricorn(img, rnd, (22, 22, 30), GOLD)
    box(img, BODY, WHITE, 4, rnd)
    for arm in ARMS:
        box(img, arm, WHITE, 4, rnd)
    hands(img, rnd, skin)
    coat(img, rnd, NAVY, GOLD)
    # gold epaulettes
    for sleeve in SLEEVES:
        paint(img, sleeve["top"], GOLD, 10, rnd)
    legs(img, rnd, WHITE, (20, 18, 18))
    save(img, "entity/pirate/sea_captain")


def save(img, name):
    path = ROOT + name + ".png"
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


if __name__ == "__main__":
    for n in range(4):
        deckhand(n)
    for n in range(2):
        gunner(n)
        powder_monkey(n)
        bombardier(n)
    captain()
    shipwright()
    sea_captain()
