"""Draws the goblin villages' art, all original: the goblins' skins (64x64, the humanoid layout plus the goblin model's
ears at 0,32 / 0,36 and nose at 12,32): four goblin faces (green hides in shades, yellow or red eyes, a wide toothy
mouth, rag tunics and loincloths, bare feet), two slingers (a hide hood and a sling strap) and the king (a fat belly, a
gold crown with gems, a purple robe, a gold chain); and the goblin mask, worn (the humanoid armour layer, 64x32) and in
hand (a 16x16 item). Run from the kingdoms project root: python -I tools/gen_goblins.py"""
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
ARMS = [faces(40, 16, 4, 12, 4), faces(32, 48, 4, 12, 4)]
LEGS = [faces(0, 16, 4, 12, 4), faces(16, 48, 4, 12, 4)]
EARS = [faces(0, 32, 4, 3, 1), faces(0, 36, 4, 3, 1)]
NOSE = faces(12, 32, 2, 2, 2)
SIDES = ("front", "back", "right", "left")

GREENS = [(98, 140, 62), (112, 150, 66), (84, 122, 56), (124, 138, 58)]
RAG = (107, 90, 58)
HIDE = (78, 59, 38)


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


def head(img, rnd, skin, eyes, hair):
    box(img, HEAD, skin, 7, rnd)
    fx, fy, fw, fh = HEAD["front"]
    # a few lank hairs on the crown (or none)
    if hair:
        tx, ty, tw, th = HEAD["top"]
        for _ in range(6):
            px(img, tx + rnd.randrange(tw), ty + rnd.randrange(th), hair)
    # heavy brows, eyes (bright, beady), a dark socket under each
    for ex in (fx + 1, fx + 5):
        px(img, ex, fy + 2, shade(skin, -45))
        px(img, ex + 1, fy + 2, shade(skin, -45))
        px(img, ex, fy + 3, eyes)
        px(img, ex + 1, fy + 3, (20, 16, 10))
        px(img, ex, fy + 4, shade(skin, -22))
        px(img, ex + 1, fy + 4, shade(skin, -22))
    # the nose's root (the nose itself is its own box), warts
    px(img, fx + 3, fy + 4, shade(skin, -18))
    px(img, fx + 4, fy + 4, shade(skin, -18))
    px(img, fx + rnd.choice((0, 7)), fy + rnd.choice((3, 5)), shade(skin, -30))
    # a wide mouth with crooked teeth
    for x in range(fx + 1, fx + 7):
        px(img, x, fy + 6, (60, 25, 20))
    for x in (fx + 2, fx + 5):
        px(img, x, fy + 6, (232, 222, 170))
    px(img, fx + rnd.choice((3, 4)), fy + 7, (232, 222, 170))
    # the nose box and ears: the same hide, darker inside the ears
    box(img, NOSE, shade(skin, 6), 6, rnd)
    nx, ny, nw, nh = NOSE["front"]
    px(img, nx, ny + 1, shade(skin, -25))
    px(img, nx + 1, ny + 1, shade(skin, -25))
    for ear in EARS:
        box(img, ear, skin, 7, rnd)
        ex, ey, ew, eh = ear["front"]
        for x in range(ex, ex + ew - 1):
            px(img, x, ey + 1, (150, 90, 85))
    # a notch bitten out of one
    img.putpixel((EARS[0]["front"][0] + 3, EARS[0]["front"][1]), (0, 0, 0, 0))


def limbs(img, rnd, skin, wraps):
    for arm in ARMS:
        box(img, arm, skin, 7, rnd)
        for name in SIDES:
            rx, ry, rw, rh = arm[name]
            # knobbly elbows and dark claws
            for x in range(rx, rx + rw):
                px(img, x, ry + rh - 1, shade(skin, -40))
            if wraps:
                for x in range(rx, rx + rw):
                    px(img, x, ry + rh - 3, wraps)
                    px(img, x, ry + rh - 4, shade(wraps, -15))
    for leg in LEGS:
        box(img, leg, skin, 7, rnd)
        paint(img, leg["bottom"], shade(skin, -30), 5, rnd)
        for name in SIDES:
            rx, ry, rw, rh = leg[name]
            for x in range(rx, rx + rw):
                px(img, x, ry + rh - 1, shade(skin, -35))


def rags(img, rnd, cloth, belt):
    """A ragged tunic to the hips, a rope belt, a loincloth over the tops of the legs, torn at the hem."""
    for name in SIDES:
        rx, ry, rw, rh = BODY[name]
        paint(img, (rx, ry + 2, rw, rh - 2), cloth, 14, rnd)
        for x in range(rx, rx + rw):
            px(img, x, ry + 8, belt)
            if rnd.random() < 0.4:
                px(img, x, ry + rh - 1, shade(cloth, -25))
    paint(img, BODY["top"], cloth, 10, rnd)
    for leg in LEGS:
        for name in SIDES:
            rx, ry, rw, rh = leg[name]
            for x in range(rx, rx + rw):
                for y in range(ry, ry + 2 + rnd.randint(0, 2)):
                    px(img, x, y, shade(cloth, rnd.randint(-18, 12)))
    # a patch or two
    fx, fy, fw, fh = BODY["front"]
    paint(img, (fx + rnd.randint(0, 4), fy + rnd.randint(3, 6), 2, 2), shade(cloth, 30), 6, rnd)


def goblin(i):
    rnd = random.Random("goblin-%d" % i)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin = GREENS[i % len(GREENS)]
    eyes = [(235, 205, 40), (220, 60, 40), (235, 205, 40), (240, 150, 30)][i % 4]
    head(img, rnd, skin, eyes, (40, 36, 30) if i % 2 else None)
    box(img, BODY, skin, 7, rnd)
    limbs(img, rnd, skin, HIDE if i % 2 == 0 else None)
    rags(img, rnd, RAG if i % 2 else shade(RAG, -20), (150, 125, 80))
    save(img, "entity/goblin/goblin_%d" % i)


def slinger(i):
    rnd = random.Random("slinger-%d" % i)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin = GREENS[(i + 2) % len(GREENS)]
    head(img, rnd, skin, (235, 205, 40), None)
    # the hood (hat layer): over the crown and down the back, open at the face
    hood = shade(HIDE, 8 * i)
    for name in ("top", "right", "left", "back"):
        rx, ry, rw, rh = HAT[name]
        paint(img, (rx, ry, rw, rh if name in ("top", "back") else 6), hood, 10, rnd)
    hx, hy, hw, hh = HAT["front"]
    paint(img, (hx, hy, hw, 2), hood, 10, rnd)
    for y in range(hy + 2, hy + 6):
        px(img, hx, y, hood)
        px(img, hx + hw - 1, y, hood)
    box(img, BODY, skin, 7, rnd)
    limbs(img, rnd, skin, HIDE)
    rags(img, rnd, shade(HIDE, 20), (120, 95, 60))
    # the sling's strap across the chest, a pouch of stones at the hip
    fx, fy, fw, fh = BODY["front"]
    for k in range(fw):
        px(img, fx + k, fy + min(fh - 1, k + 1), (60, 45, 30))
    paint(img, (fx + 5, fy + 8, 2, 2), (95, 80, 60), 6, rnd)
    save(img, "entity/goblin/slinger_%d" % i)


def king(skin=(104, 132, 58), robe=(91, 44, 111), seed="goblin-king", out="entity/goblin/king"):
    """The king (Kingdoms 0.7.292: {skin}, {robe}, {seed} and {out} for gen_uniforms' MCA outfits; the default is as before)."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    head(img, rnd, skin, (240, 200, 40), None)
    # a gold tooth
    fx, fy, fw, fh = HEAD["front"]
    px(img, fx + 4, fy + 6, (240, 200, 60))
    # the crown (hat layer): a gold band with points, a red and a green gem
    gold = (232, 190, 60)
    for name in SIDES:
        rx, ry, rw, rh = HAT[name]
        for x in range(rx, rx + rw):
            px(img, x, ry, shade(gold, rnd.randint(-15, 15)))
            px(img, x, ry + 1, shade(gold, -25))
            if (x - rx) % 3 == 0:
                img.putpixel((x, ry - 1 if ry > 0 else ry), clamp(gold) + (255,))
    hx, hy, hw, hh = HAT["front"]
    px(img, hx + 3, hy, (200, 30, 40))
    px(img, hx + 4, hy, (40, 170, 60))
    # a purple robe over a fat bare belly, a gold chain
    box(img, BODY, robe, 10, rnd)
    bx, by, bw, bh = BODY["front"]
    paint(img, (bx + 2, by + 3, 4, 6), shade(skin, 18), 6, rnd)
    px(img, bx + 3, by + 6, shade(skin, -20))
    for k in range(bw):
        px(img, bx + k, by + (1 if k in (0, bw - 1) else 2), gold)
    px(img, bx + 3, by + 3, (200, 30, 40))
    limbs(img, rnd, skin, None)
    for arm in ARMS:
        for name in SIDES:
            rx, ry, rw, rh = arm[name]
            paint(img, (rx, ry, rw, 5), robe, 10, rnd)
            for x in range(rx, rx + rw):
                px(img, x, ry + 5, (225, 210, 190))
    for leg in LEGS:
        for name in SIDES:
            rx, ry, rw, rh = leg[name]
            paint(img, (rx, ry, rw, 7), robe, 10, rnd)
            for x in range(rx, rx + rw):
                px(img, x, ry + 7, (225, 210, 190))
    save(img, out)


def mask_worn():
    """The mask on a player (the humanoid armour layer's head box): a green goblin face with ears and a nose painted on,
    eye holes, a leather strap round the back."""
    rnd = random.Random("goblin-mask")
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    green = (100, 138, 60)
    box(img, HEAD, green, 7, rnd)
    fx, fy, fw, fh = HEAD["front"]
    for ex in (fx + 1, fx + 5):
        px(img, ex, fy + 2, shade(green, -45))
        px(img, ex + 1, fy + 2, shade(green, -45))
        px(img, ex, fy + 3, (25, 20, 15))
        px(img, ex + 1, fy + 3, (25, 20, 15))
    paint(img, (fx + 3, fy + 3, 2, 3), shade(green, 12), 4, rnd)
    px(img, fx + 3, fy + 5, shade(green, -25))
    px(img, fx + 4, fy + 5, shade(green, -25))
    for x in range(fx + 1, fx + 7):
        px(img, x, fy + 6, (60, 25, 20))
    px(img, fx + 2, fy + 6, (232, 222, 170))
    px(img, fx + 5, fy + 6, (232, 222, 170))
    # ears painted along the sides, tips up
    for name in ("right", "left"):
        rx, ry, rw, rh = HEAD[name]
        for k in range(6):
            x = rx + 1 + k if name == "right" else rx + 6 - k
            y = ry + 4 - k // 2
            px(img, x, y, shade(green, 10))
            px(img, x, y + 1, (150, 90, 85) if 0 < k < 5 else shade(green, -10))
    # the strap
    for name in ("right", "left", "back"):
        rx, ry, rw, rh = HEAD[name]
        for x in range(rx, rx + rw):
            px(img, x, ry + 6, (95, 65, 40))
    save(img, "entity/equipment/humanoid/goblin_mask")


def mask_item():
    rnd = random.Random("goblin-mask-item")
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    green = (100, 138, 60)
    outline = (40, 60, 25)
    # the face
    for y in range(3, 14):
        for x in range(4, 12):
            if (y == 3 or y == 13) and x in (4, 11):
                continue
            px(img, x, y, shade(green, rnd.randint(-8, 8)))
    # ears out to the sides, tips up
    for k in range(4):
        px(img, 3 - k, 6 - k // 2, shade(green, 6))
        px(img, 12 + k, 6 - k // 2, shade(green, 6))
        px(img, 3 - k, 7 - k // 2, (150, 90, 85) if k < 3 else outline)
        px(img, 12 + k, 7 - k // 2, (150, 90, 85) if k < 3 else outline)
    # outline
    for y in range(3, 14):
        for x in (4, 11):
            px(img, x, y, outline)
    for x in range(5, 11):
        px(img, x, 2, outline)
        px(img, x, 14, outline)
    # eye holes, brows, nose, mouth and teeth
    for ex in (5, 9):
        px(img, ex, 6, shade(green, -45))
        px(img, ex + 1, 6, shade(green, -45))
        px(img, ex, 7, (20, 16, 10))
        px(img, ex + 1, 7, (20, 16, 10))
    px(img, 7, 8, shade(green, 20))
    px(img, 8, 8, shade(green, 20))
    px(img, 7, 9, shade(green, 10))
    px(img, 8, 9, shade(green, 10))
    for x in range(5, 11):
        px(img, x, 11, (60, 25, 20))
    px(img, 6, 11, (232, 222, 170))
    px(img, 9, 11, (232, 222, 170))
    save(img, "item/goblin_mask")


def save(img, name):
    path = ROOT + name + ".png"
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


if __name__ == "__main__":
    for i in range(4):
        goblin(i)
    for i in range(2):
        slinger(i)
    king()
    mask_worn()
    mask_item()
    print("goblins drawn")
