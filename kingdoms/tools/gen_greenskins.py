"""Draws what goblins and orcs wear on top of MCA's body as villagers (Kingdoms 0.7.292, {@code GreenskinLooks}): an orc's
features (ears, brows, jaws in flesh; tusks in ivory; a goblin's are its own body's, 0.7.320, gen_goblin_body.py) and their marks (warts, wrinkles, blotches; an orc's scars and
war paint). The flesh and the marks are drawn in greys, tinted in game by each one's own green ({@code Greenskins.skin});
tusks, scars and paint keep their colours. Original art. Run from the kingdoms project root: python -I tools/gen_greenskins.py
(also writes a contact sheet to build/greenskins-preview.png).

Feature textures are 64x32; each cube's box is laid out where its texOffs in GreenskinLooks puts it (w, h, d: 2*(w+d) wide,
d+h tall). Marks are 64x64 in the player layout, on the base layer only (MCA's clothes hide what they cover)."""
from PIL import Image
import os
import random

ROOT = "src/main/resources/assets/jaymoskingdoms/textures/entity/greenskin/"
FLESH = (214, 214, 210)
IVORY = (236, 226, 196)

# the cubes of each shape: (u, v, w, h, d, bone). (0.7.320) A goblin's ears and nose are its own body's now (gen_goblin_body.py).
ORC_SHAPES = [
    # tusks up from the jaw, a heavy brow, small swept ears
    [(0, 16, 1, 2, 1, True), (4, 16, 1, 2, 1, True), (0, 0, 8, 1, 1, False), (0, 4, 1, 3, 2, False), (6, 4, 1, 3, 2, False)],
    # an underbite with long tusks, a heavier brow, one ear torn short
    [(0, 10, 6, 2, 1, False), (8, 16, 1, 3, 1, True), (12, 16, 1, 3, 1, True), (18, 0, 8, 2, 1, False), (12, 4, 1, 2, 2, False),
     (6, 4, 1, 3, 2, False)],
]

# the player layout's base-layer faces
HEAD = {"top": (8, 0, 8, 8), "right": (0, 8, 8, 8), "front": (8, 8, 8, 8), "left": (16, 8, 8, 8), "back": (24, 8, 8, 8)}
BODY = {"front": (20, 20, 8, 12), "back": (32, 20, 8, 12), "right": (16, 20, 4, 12), "left": (28, 20, 4, 12)}
ARMS = [{"front": (44, 20, 4, 12), "back": (52, 20, 4, 12), "right": (40, 20, 4, 12), "left": (48, 20, 4, 12)},
        {"front": (36, 52, 4, 12), "back": (44, 52, 4, 12), "right": (32, 52, 4, 12), "left": (40, 52, 4, 12)}]
LEGS = [{"front": (4, 20, 4, 12), "back": (12, 20, 4, 12), "right": (0, 20, 4, 12), "left": (8, 20, 4, 12)},
        {"front": (20, 52, 4, 12), "back": (28, 52, 4, 12), "right": (16, 52, 4, 12), "left": (24, 52, 4, 12)}]


def clamp(c):
    return tuple(max(0, min(255, int(v))) for v in c)


def shade(c, by):
    return clamp(tuple(v + by for v in c))


def px(img, x, y, colour, alpha=255):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), clamp(colour) + (alpha,))


def features(shapes, seed):
    """Every shape's cubes on one sheet: flesh mottled, darker at the edges and tips; tusks ivory, yellowed at the root."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    for shape in shapes:
        for u, v, w, h, d, bone in shape:
            ww, hh = 2 * (w + d), d + h
            for y in range(v, v + hh):
                for x in range(u, u + ww):
                    if bone:
                        root = (y - v) >= hh - 1
                        px(img, x, y, shade(IVORY, rnd.randint(-10, 6) - (34 if root else 0)))
                    else:
                        edge = x in (u, u + ww - 1) or y in (v, v + hh - 1)
                        c = shade(FLESH, rnd.randint(-14, 8) - (26 if edge else 0))
                        if rnd.random() < 0.12:
                            c = shade(c, -40)  # a wart, a pock
                        px(img, x, y, c)
    return img


def spot(img, rnd, rect, dark, size=1):
    """A wart or a blotch somewhere in {rect}: a dark ring round a pale crown."""
    x0, y0, w, h = rect
    x, y = x0 + rnd.randrange(max(1, w - size)), y0 + rnd.randrange(max(1, h - size))
    for dy in range(size):
        for dx in range(size):
            px(img, x + dx, y + dy, (dark, dark, dark))
    if size == 1 and rnd.random() < 0.5:
        px(img, x, y - 1, (238, 238, 232))


def marks(i, orc):
    """Marks on the skin, tinted by its green: warts (a goblin's many), blotches, brow wrinkles, sagging cheek lines; the
    eyes and mouth (head front rows 11-14 in the middle) kept clear for MCA's own face."""
    rnd = random.Random("%s-marks-%d" % ("orc" if orc else "goblin", i))
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    fx, fy, fw, fh = HEAD["front"]
    # wrinkles over the brow (two or three lines), deeper on an orc
    for k in range(2 + i % 2):
        y = fy + k
        for x in range(fx + 1, fx + fw - 1):
            if rnd.random() < 0.75:
                px(img, x, y, (150, 150, 146) if orc else (168, 168, 160))
    # cheek lines down from the eyes' outer corners
    for x in (fx, fx + fw - 1):
        for y in range(fy + 4, fy + 7):
            if rnd.random() < 0.8:
                px(img, x, y, (156, 156, 150))
    # warts on the face's edges, the chin, the head's sides and crown
    warts = 3 + i if not orc else 1 + i // 2
    for _ in range(warts):
        side = rnd.choice(["chin", "cheek", "right", "left", "top", "back"])
        if side == "chin":
            spot(img, rnd, (fx + 1, fy + fh - 1, fw - 2, 1), 112)
        elif side == "cheek":
            spot(img, rnd, (fx if rnd.random() < 0.5 else fx + fw - 2, fy + 3, 2, 4), 112)
        else:
            spot(img, rnd, HEAD[side], 118)
    # blotches over the body and limbs (the bits clothes leave bare show them)
    for part in [BODY] + ARMS + LEGS:
        for name, rect in part.items():
            for _ in range(rnd.randint(1, 3)):
                spot(img, rnd, rect, rnd.choice((128, 140, 150)), size=rnd.choice((1, 1, 2)))
    # a goblin's knuckles and an orc's forearm veins
    for arm in ARMS:
        x0, y0, w, h = arm["front"]
        if orc:
            for y in range(y0 + 5, y0 + 10):
                px(img, x0 + 1 + (y % 2), y, (150, 154, 146))
        else:
            for x in range(x0, x0 + w):
                if x % 2 == 0:
                    px(img, x, y0 + h - 2, (120, 120, 116))
    return img


PAINTS = [None, (40, 34, 30), (130, 30, 26), (222, 218, 206)]


def scars(i):
    """An orc's scars (pale, untinted) and, on three of the four, war paint: soot, blood or bone-white."""
    rnd = random.Random("orc-scars-%d" % i)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    scar = (196, 168, 150)
    fx, fy, fw, fh = HEAD["front"]
    paint = PAINTS[i % len(PAINTS)]
    if paint is not None:
        # a stripe down from the brow (not over the eyes), or a hand-print on the cheek
        if i % 2:
            for y in range(fy, fy + 3):
                px(img, fx + 3, y, paint)
                px(img, fx + 4, y, paint)
            for y in range(fy + 6, fy + fh):
                px(img, fx + 3, y, paint)
                px(img, fx + 4, y, paint)
        else:
            for dx, dy in ((0, 4), (1, 4), (0, 5), (1, 5), (0, 6), (6, 4), (7, 4), (6, 5), (7, 5), (7, 6)):
                px(img, fx + dx, fy + dy, paint)
        # stripes across the chest and up the arms
        bx, by, bw, bh = BODY["front"]
        for x in range(bx, bx + bw):
            px(img, x, by + 2, paint)
        for arm in ARMS:
            ax, ay, aw, ah = arm["front"]
            for y in range(ay + 1, ay + 4):
                px(img, ax + 1, y, paint)
                px(img, ax + 2, y, paint)
    # scars: one across the face's side, two to four over the body and arms
    sx, sy, sw, sh = HEAD["right" if i % 2 else "left"]
    for k in range(4):
        px(img, sx + 2 + k, sy + 1 + k, scar)
    for _ in range(2 + i % 3):
        part = rnd.choice([BODY] + ARMS)
        x0, y0, w, h = part[rnd.choice(["front", "back"])]
        x, y = x0 + rnd.randrange(max(1, w - 2)), y0 + rnd.randrange(h - 3)
        for k in range(3):
            px(img, x + k % 2, y + k, scar)
    return img


def save(img, name):
    path = ROOT + name + ".png"
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    print("drew", path)


def preview(drawn, path):
    """Each texture at four times its size on a green ground, in rows."""
    tiles = [(n, im) for n, im in drawn.items()]
    sheet = Image.new("RGBA", (4 * 64 * 4 + 40, ((len(tiles) + 3) // 4) * (4 * 64 + 10) + 10), (40, 44, 50, 255))
    for k, (n, im) in enumerate(tiles):
        ground = Image.new("RGBA", im.size, (110, 140, 70, 255))
        ground.alpha_composite(im)
        big = ground.resize((im.width * 4, im.height * 4), Image.NEAREST)
        sheet.alpha_composite(big, ((k % 4) * (64 * 4 + 10) + 10, (k // 4) * (64 * 4 + 10) + 10))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    sheet.save(path)
    print("preview", path)


if __name__ == "__main__":
    drawn = {"orc_features": features(ORC_SHAPES, "orc-features")}
    for i in range(4):
        drawn["goblin_marks_%d" % i] = marks(i, False)
        drawn["orc_marks_%d" % i] = marks(i, True)
        drawn["orc_scars_%d" % i] = scars(i)
    for name, img in drawn.items():
        save(img, name)
    preview(drawn, "build/greenskins-preview.png")
