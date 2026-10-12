"""Draws the casters' art for Kingdoms 0.5.35 (Sorcery), all original: the skins (64x64, the humanoid layout) of the
necromancer (two faces: a hooded black-and-violet robe, a pale sunken face with violet eyes, a bone belt and skull clasp),
the camp shaman (one per camp colour: war paint in it, a wolf-skull headdress with feathers, a fur mantle, a bone
necklace, a hide kilt) and the court wizard (one per realm colour: a robe in it with gold trim and stars, a long grey
beard, and his pointed hat, drawn in the skin's free corners for the hat model of SorcerySkins); and the items (16x16): the three staffs and
the five spell scrolls. Run from the kingdoms project root: python -I tools/gen_sorcery.py"""
from PIL import Image
import os
import random

ROOT = "src/main/resources/assets/jaymoskingdoms/textures/"

DYES = {"white": (233, 236, 236), "orange": (240, 118, 19), "magenta": (189, 68, 179), "light_blue": (58, 175, 217),
        "yellow": (248, 197, 39), "lime": (112, 185, 25), "pink": (237, 141, 172), "gray": (62, 68, 71),
        "light_gray": (142, 142, 134), "cyan": (21, 137, 145), "purple": (121, 42, 172), "blue": (53, 57, 157),
        "brown": (114, 71, 40), "green": (84, 109, 27), "red": (161, 39, 34), "black": (30, 30, 34)}


def faces(ox, oy, w, h, d):
    """(x, y, w, h) of a box's faces in the skin."""
    return {"top": (ox + d, oy, w, d), "bottom": (ox + d + w, oy, w, d), "right": (ox, oy + d, d, h), "front": (ox + d, oy + d, w, h),
            "left": (ox + d + w, oy + d, d, h), "back": (ox + d + w + d, oy + d, w, h)}


HEAD = faces(0, 0, 8, 8, 8)
HAT = faces(32, 0, 8, 8, 8)
BODY = faces(16, 16, 8, 12, 4)
ARMS = [faces(40, 16, 4, 12, 4), faces(32, 48, 4, 12, 4)]
LEGS = [faces(0, 16, 4, 12, 4), faces(16, 48, 4, 12, 4)]
SIDES = ("front", "back", "right", "left")
# the wizard's hat (SorcerySkins.hat), in the parts of the skin his model doesn't use: a brim 10x1x10 at 0,32, then tiers
# 6x3x6 at 40,32, 4x3x4 at 0,48 and 2x3x2 at 48,48, and the tip 1x2x1 at 56,48
BRIM = faces(0, 32, 10, 1, 10)
TIERS = [faces(40, 32, 6, 3, 6), faces(0, 48, 4, 3, 4), faces(48, 48, 2, 3, 2)]
TIP = faces(56, 48, 1, 2, 1)

BONE = (226, 218, 192)
GOLD = (226, 180, 60)


def clamp(c):
    return tuple(max(0, min(255, int(v))) for v in c)


def shade(c, by):
    return clamp(tuple(v + by for v in c))


def mix(a, b, t):
    return clamp(tuple(a[i] * (1 - t) + b[i] * t for i in range(3)))


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


def face(img, rnd, skin, eyes, glow=False):
    box(img, HEAD, skin, 6, rnd)
    fx, fy, fw, fh = HEAD["front"]
    for ex in (fx + 1, fx + 5):
        px(img, ex, fy + 3, shade(skin, -40))
        px(img, ex + 1, fy + 3, shade(skin, -40))
        px(img, ex, fy + 4, eyes if glow else (240, 240, 240))
        px(img, ex + 1, fy + 4, eyes)
    px(img, fx + 3, fy + 5, shade(skin, -18))
    px(img, fx + 4, fy + 5, shade(skin, -18))


def robe_limbs(img, rnd, cloth, cuff, hands, skirt=True):
    """Sleeves to the wrist (hands showing, a cuff of trim), and the robe down over the legs to the ankle."""
    for arm in ARMS:
        box(img, arm, cloth, 9, rnd)
        for name in SIDES:
            rx, ry, rw, rh = arm[name]
            paint(img, (rx, ry + rh - 2, rw, 2), hands, 5, rnd)
            for x in range(rx, rx + rw):
                px(img, x, ry + rh - 3, cuff)
        paint(img, arm["bottom"], hands, 5, rnd)
    for leg in LEGS:
        box(img, leg, cloth, 9, rnd)
        paint(img, leg["bottom"], (40, 30, 25), 4, rnd)
        for name in SIDES:
            rx, ry, rw, rh = leg[name]
            if skirt:
                for x in range(rx, rx + rw):
                    px(img, x, ry + rh - 2, cuff)
                paint(img, (rx, ry + rh - 1, rw, 1), (40, 30, 25), 4, rnd)


def save(img, name):
    path = ROOT + name + ".png"
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


# ---- the necromancer ------------------------------------------------------------------------------------------------

def necromancer(i):
    rnd = random.Random("necromancer-%d" % i)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin = [(178, 182, 166), (160, 168, 158)][i]
    violet = (176, 70, 230)
    face(img, rnd, skin, violet, glow=True)
    fx, fy, fw, fh = HEAD["front"]
    # sunken cheeks, a thin grey mouth, a scar or a stitched lip
    px(img, fx + 1, fy + 5, shade(skin, -30))
    px(img, fx + 6, fy + 5, shade(skin, -30))
    for x in range(fx + 2, fx + 6):
        px(img, x, fy + 6, (70, 66, 64))
    if i == 0:
        for y in range(fy + 1, fy + 4):
            px(img, fx + 6, y, shade(skin, -45))
    else:
        for x in (fx + 2, fx + 4):
            px(img, x, fy + 7, (60, 40, 60))
    # the hood (hat layer): black, lined in violet, over the crown and back, open at the face
    hood = (28, 24, 34)
    for name in ("top", "back", "right", "left"):
        paint(img, HAT[name], hood, 6, rnd)
    hx, hy, hw, hh = HAT["front"]
    paint(img, (hx, hy, hw, 2), hood, 6, rnd)
    for y in range(hy + 2, hy + hh):
        px(img, hx, y, hood)
        px(img, hx + hw - 1, y, hood)
    for x in range(hx + 1, hx + hw - 1):
        px(img, x, hy + 2, (90, 30, 120))
    # the robe: black over violet, a bone belt, a skull clasp at the chest, bones hung at the hip
    cloth = (34, 28, 40)
    box(img, BODY, cloth, 7, rnd)
    bx, by, bw, bh = BODY["front"]
    for y in range(by, by + bh):
        px(img, bx + 3, y, (70, 26, 96))
        px(img, bx + 4, y, (70, 26, 96))
    for name in SIDES:
        rx, ry, rw, rh = BODY[name]
        for x in range(rx, rx + rw):
            px(img, x, ry + 7, shade(BONE, rnd.randint(-20, 0)))
    # the skull: 3x3, two sockets
    for dx in range(3):
        for dy in range(3):
            px(img, bx + 2 + dx + (1 if dx == 2 else 0), by + 2 + dy, BONE)
    px(img, bx + 2, by + 3, (20, 16, 20))
    px(img, bx + 5, by + 3, (20, 16, 20))
    px(img, bx + 3, by + 4, (120, 110, 90))
    for k in range(3):
        px(img, bx + 1 + 2 * k, by + 9, BONE)
        px(img, bx + 1 + 2 * k, by + 10, shade(BONE, -30))
    robe_limbs(img, rnd, cloth, (90, 30, 120), skin)
    save(img, "entity/sorcery/necromancer_%d" % i)


# ---- the shaman -----------------------------------------------------------------------------------------------------

def shaman(dye, paint_colour):
    rnd = random.Random("shaman-" + dye)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin = (176, 128, 92)
    face(img, rnd, skin, (60, 40, 25))
    fx, fy, fw, fh = HEAD["front"]
    # war paint in the camp's colour: a band across the eyes, stripes down the chin
    for x in range(fx, fx + fw):
        if x not in (fx + 2, fx + 6):
            px(img, x, fy + 3, paint_colour)
    for x in (fx + 2, fx + 5):
        px(img, x, fy + 6, paint_colour)
        px(img, x, fy + 7, shade(paint_colour, -20))
    for x in range(fx + 3, fx + 5):
        px(img, x, fy + 6, (70, 40, 30))
    # long dark braids down the sides
    for name in ("right", "left", "back"):
        rx, ry, rw, rh = HEAD[name]
        paint(img, (rx, ry, rw, 3), (40, 30, 24), 6, rnd)
        for y in range(ry + 3, ry + rh):
            px(img, rx + (1 if name != "back" else 2), y, (40, 30, 24))
            px(img, rx + rw - 2, y, (40, 30, 24))
    paint(img, HEAD["top"], (40, 30, 24), 6, rnd)
    # the headdress (hat layer): a wolf's skull over the brow, feathers up the back
    hx, hy, hw, hh = HAT["front"]
    for x in range(hx, hx + hw):
        px(img, x, hy, shade(BONE, rnd.randint(-15, 5)))
        px(img, x, hy + 1, shade(BONE, -10))
    px(img, hx + 2, hy + 1, (25, 20, 18))
    px(img, hx + 5, hy + 1, (25, 20, 18))
    for x in (hx + 1, hx + 6):
        px(img, x, hy + 2, BONE)
    paint(img, HAT["top"], (110, 100, 90), 12, rnd)
    for name in ("right", "left"):
        rx, ry, rw, rh = HAT[name]
        paint(img, (rx, ry, rw, 2), (110, 100, 90), 12, rnd)
    bx_, by_, bw_, bh_ = HAT["back"]
    for k, colour in enumerate([paint_colour, (240, 240, 230), (40, 40, 40), paint_colour, (240, 240, 230), (40, 40, 40), paint_colour, (240, 240, 230)]):
        top = 0 if k % 2 == 0 else 1
        for y in range(by_ + top, by_ + 6 - (k % 3)):
            px(img, bx_ + k, y, shade(colour, -12 * (y - by_) // 2))
    # bare chest under a fur mantle, a bone necklace, a hide kilt
    box(img, BODY, skin, 6, rnd)
    fur = (96, 74, 52)
    for name in SIDES:
        rx, ry, rw, rh = BODY[name]
        paint(img, (rx, ry, rw, 3 if name == "front" else 6), fur, 14, rnd)
        paint(img, (rx, ry + 8, rw, 4), (122, 82, 48), 10, rnd)
        for x in range(rx, rx + rw):
            px(img, x, ry + 8, (70, 45, 28))
    paint(img, BODY["top"], fur, 14, rnd)
    bx, by, bw, bh = BODY["front"]
    for k in range(bw):
        px(img, bx + k, by + 3 + (1 if 2 <= k <= 5 else 0), BONE if k % 2 == 0 else (70, 50, 30))
    px(img, bx + 3, by + 5, BONE)
    px(img, bx + 4, by + 5, BONE)
    # paint on the chest too
    px(img, bx + 1, by + 6, paint_colour)
    px(img, bx + 6, by + 6, paint_colour)
    for arm in ARMS:
        box(img, arm, skin, 6, rnd)
        for name in SIDES:
            rx, ry, rw, rh = arm[name]
            paint(img, (rx, ry, rw, 4), fur, 14, rnd)
            for x in range(rx, rx + rw):
                px(img, x, ry + 8, (90, 60, 35))
                px(img, x, ry + 9, paint_colour)
    for leg in LEGS:
        box(img, leg, (122, 82, 48), 10, rnd)
        paint(img, leg["bottom"], (60, 40, 25), 4, rnd)
        for name in SIDES:
            rx, ry, rw, rh = leg[name]
            paint(img, (rx, ry + 5, rw, 4), skin, 6, rnd)
            paint(img, (rx, ry + 9, rw, 3), (90, 66, 44), 8, rnd)
    save(img, "entity/sorcery/shaman/" + dye)


# ---- the court wizard -----------------------------------------------------------------------------------------------

def wizard(dye, colour):
    rnd = random.Random("wizard-" + dye)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin = (214, 172, 140)
    face(img, rnd, skin, (60, 90, 160))
    fx, fy, fw, fh = HEAD["front"]
    grey = (205, 205, 210)
    # bushy brows, a long grey beard from the cheeks down (and on down the robe's front)
    for x in range(fx + 1, fx + 3):
        px(img, x, fy + 2, grey)
    for x in range(fx + 5, fx + 7):
        px(img, x, fy + 2, grey)
    for y in range(fy + 5, fy + fh):
        for x in range(fx, fx + fw):
            if y == fy + 5 and 2 <= x - fx <= 5:
                continue
            px(img, x, y, shade(grey, rnd.randint(-25, 10)))
    for name in ("right", "left"):
        rx, ry, rw, rh = HEAD[name]
        paint(img, (rx, ry + 5, rw, 3), grey, 14, rnd)
    paint(img, HEAD["top"], grey, 10, rnd)
    paint(img, HEAD["back"], grey, 10, rnd)
    # his hat's band on the hat layer, in the robe's colour, gold beneath
    for name in SIDES:
        rx, ry, rw, rh = HAT[name]
        paint(img, (rx, ry, rw, 2), colour, 10, rnd)
        for x in range(rx, rx + rw):
            px(img, x, ry + 2, GOLD)
    paint(img, HAT["top"], colour, 10, rnd)
    # the robe in the realm's colour, trimmed gold down the front, a belt, a scatter of stars
    dark = shade(colour, -35) if sum(colour) > 120 else shade(colour, 25)
    box(img, BODY, colour, 10, rnd)
    bx, by, bw, bh = BODY["front"]
    for y in range(by, by + bh):
        px(img, bx + 3, y, GOLD)
    paint(img, (bx + 2, by, 4, 5), grey, 14, rnd)
    for name in SIDES:
        rx, ry, rw, rh = BODY[name]
        for x in range(rx, rx + rw):
            px(img, x, ry + 7, (80, 55, 35))
        px(img, rx + rnd.randrange(rw), ry + 9 + rnd.randrange(2), (250, 236, 150))
    robe_limbs(img, rnd, colour, GOLD, skin)
    for part in ARMS + LEGS:
        for name in SIDES:
            rx, ry, rw, rh = part[name]
            if rnd.random() < 0.7:
                px(img, rx + rnd.randrange(rw), ry + 2 + rnd.randrange(rh - 5), (250, 236, 150))
    # the pointed hat: brim and tiers in the robe's colour, a gold band on the lowest tier, the tip bent dark
    box(img, BRIM, dark, 8, rnd)
    for k, tier in enumerate(TIERS):
        box(img, tier, colour, 10, rnd)
        if k == 0:
            for name in SIDES:
                rx, ry, rw, rh = tier[name]
                for x in range(rx, rx + rw):
                    px(img, x, ry + rh - 1, GOLD)
                px(img, rx + rw // 2, ry, (250, 236, 150))
    box(img, TIP, dark, 6, rnd)
    save(img, "entity/sorcery/wizard/" + dye)


# ---- items ----------------------------------------------------------------------------------------------------------

def item(name, pixels):
    """A 16x16 item from rows of characters keyed in {@code pixels[1]}."""
    rows, key = pixels
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != "." and ch in key:
                px(img, x, y, key[ch])
    save(img, "item/" + name)


def staff_shaft(rows, ch, start, end):
    """A diagonal shaft from bottom-left (start) towards top-right (end), one pixel wide with a second for weight."""
    for i in range(start, end):
        x, y = i, 15 - i
        rows[y] = rows[y][:x] + ch + rows[y][x + 1:]
        if x + 1 < 16:
            rows[y] = rows[y][:x + 1] + ("s" if ch == "w" else ch.upper()) + rows[y][x + 2:]


def staffs():
    blank = ["." * 16 for _ in range(16)]
    # the bone staff: a pale shaft of knuckled bone, a little skull atop
    rows = list(blank)
    staff_shaft(rows, "b", 1, 11)
    for (x0, y0), row in zip([(9, 1), (9, 2), (9, 3), (9, 4), (9, 5)], ["kkk", "kkkk", "kdkd", "kkkk", "kk."]):
        rows[y0] = rows[y0][:x0] + row + rows[y0][x0 + len(row):]
    item("bone_staff", (rows, {"b": BONE, "B": shade(BONE, -45), "k": (236, 230, 210), "d": (40, 20, 50)}))
    # the totem staff: dark wood, a skull bound with red cloth, two feathers
    rows = list(blank)
    staff_shaft(rows, "w", 0, 11)
    for (x0, y0), row in zip([(9, 2), (9, 3), (8, 4), (9, 5), (11, 6), (11, 7)], ["kkk", "kdkd", "rrrrr", "kkk", "f.", "fF"]):
        rows[y0] = rows[y0][:x0] + row + rows[y0][x0 + len(row):]
    for (x0, y0), row in zip([(13, 1), (13, 2), (14, 3)], ["F", "F", "f"]):
        rows[y0] = rows[y0][:x0] + row + rows[y0][x0 + len(row):]
    item("totem_staff", (rows, {"w": (92, 62, 36), "s": (60, 40, 24), "k": BONE, "d": (30, 20, 20), "r": (170, 40, 34),
                                "f": (240, 240, 230), "F": (40, 40, 40)}))
    # the wizard's staff: pale wood, a gold claw holding a blue crystal
    rows = list(blank)
    staff_shaft(rows, "w", 0, 11)
    for (x0, y0), row in zip([(11, 1), (10, 2), (10, 3), (11, 4), (10, 5)], ["cC", "cCCc", "Cccg", "gCg", "g..g"]):
        rows[y0] = rows[y0][:x0] + row + rows[y0][x0 + len(row):]
    item("wizard_staff", (rows, {"w": (176, 138, 92), "s": (130, 98, 60), "g": GOLD, "c": (120, 210, 255), "C": (60, 140, 230)}))


def scroll(name, seal, glow):
    """A rolled parchment, tied with a ribbon and sealed in the spell's colour, a rune glowing on its face."""
    rnd = random.Random(name)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    parch = (232, 214, 170)
    # the sheet, on the diagonal (u along it, v across), rolled up at both ends
    for y in range(16):
        for x in range(16):
            u, v = x - y, x + y - 15
            if abs(v) <= 3 and abs(u) <= 11:
                if abs(u) >= 9:
                    px(img, x, y, shade(parch, -25 - 10 * abs(v) + rnd.randint(-6, 6)))
                else:
                    px(img, x, y, shade(parch, rnd.randint(-10, 6) - 3 * abs(v)))
    # the rune
    for (x, y) in ((7, 7), (8, 8), (8, 6), (6, 8), (9, 9)):
        px(img, x, y, glow)
    # the ribbon and the seal
    for (x, y) in ((10, 9), (11, 10), (10, 10), (11, 11)):
        px(img, x, y, shade(seal, -20))
    for dx in range(2):
        for dy in range(2):
            px(img, 9 + dx, 8 + dy, seal)
    save(img, "item/" + name)


def items():
    staffs()
    scroll("scroll_of_raising", (60, 40, 70), (176, 70, 230))
    scroll("scroll_of_draining", (140, 20, 40), (176, 70, 230))
    scroll("scroll_of_fire", (200, 70, 20), (255, 170, 40))
    scroll("scroll_of_warding", (40, 80, 160), (250, 236, 150))
    scroll("scroll_of_arcane_bolt", (30, 60, 140), (120, 210, 255))


if __name__ == "__main__":
    necromancer(0)
    necromancer(1)
    for d, c in DYES.items():
        shaman(d, c)
        wizard(d, c)
    items()
    print("sorcery drawn")
