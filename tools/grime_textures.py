"""Paints the armour grime masks (dust and mud) that ArmourGrimeTextures paints into dirty armour.
Run from the repository root: python -I tools/grime_textures.py

Each mask is a 64x32 armour sheet (the humanoid and humanoid_leggings layout), a 16x16 item icon, or a 64x64 skin
(a body's own clothes and skin).
  alpha = how early that pixel gets dirty (255 = the first speck, low = only when filthy, 0 = never)
  grey  = how dark the grime is there (white = the grime colour as is, darker = a thicker, darker clot)
Dirt gathers low on every face (feet, shins, the hem of the chest), as it does when you walk through it.
Our own pixels, drawn by this script; nothing is taken from any pack."""
import math
import random
from PIL import Image

ROOT = "src/main/resources/assets/jaymospowers/textures/grime/"

# The side faces of the humanoid armour sheet (x0, y0, x1, y1): where "low" on the body is the bottom of the face.
# Head sides, hat sides, body sides, arm sides, leg sides; the top/bottom caps get only a little.
SIDES = [(0, 8, 32, 16), (32, 8, 64, 16), (16, 20, 40, 32), (40, 20, 56, 32), (0, 20, 16, 32)]
# How dirty each part gets for its height: legs most, head least (but the head still gets its share: a long, dusty walk
# cakes the whole set).
PART_WEIGHT = [0.5, 0.45, 0.8, 0.7, 1.0]


def lowness(x, y):
    """0 at the top of a side face, 1 at its bottom, times how much that part gets; caps get a little."""
    for (x0, y0, x1, y1), w in zip(SIDES, PART_WEIGHT):
        if x0 <= x < x1 and y0 <= y < y1:
            return w * (y - y0 + 0.5) / (y1 - y0)
    return 0.3


def blobs(rnd, w, h, count, rmin, rmax, stretch=1.0):
    """A field of soft blobs: each pixel's value is the strongest blob covering it."""
    field = [[0.0] * w for _ in range(h)]
    for _ in range(count):
        cx, cy = rnd.uniform(0, w), rnd.uniform(0, h)
        r = rnd.uniform(rmin, rmax)
        lobe = rnd.uniform(0, math.tau)
        for y in range(max(0, int(cy - r * stretch - 2)), min(h, int(cy + r * stretch + 3))):
            for x in range(max(0, int(cx - r - 2)), min(w, int(cx + r + 3))):
                dx, dy = (x + 0.5 - cx) / r, (y + 0.5 - cy) / (r * stretch)
                a = math.atan2(dy, dx)
                d = math.hypot(dx, dy) * (1 + 0.25 * math.sin(3 * a + lobe))
                if d < 1:
                    field[y][x] = max(field[y][x], 1 - d)
    return field


def mask(seed, w, h, kind, low_of):
    rnd = random.Random(seed)
    if kind == "dust":
        # a fine film of specks and small, pale patches
        field = blobs(rnd, w, h, int(w * h / 6), 0.8, 2.4)
        speck = 0.7
    else:
        # fewer, chunkier clots, a little taller than wide, with drips running down
        field = blobs(rnd, w, h, int(w * h / 14), 1.3, 3.6, 1.25)
        speck = 0.4
        for _ in range(int(w * h / 60)):
            x, y = rnd.randrange(w), rnd.randrange(h)
            for i in range(rnd.randint(2, 4)):
                if y + i < h:
                    field[y + i][x] = max(field[y + i][x], 0.7 - 0.12 * i)
    im = Image.new("LA", (w, h), (0, 0))
    for y in range(h):
        for x in range(w):
            low = low_of(x, y)
            f = field[y][x]
            if f <= 0 and rnd.random() < speck * (0.3 + low):
                f = rnd.uniform(0.15, 0.5)
            if f <= 0:
                continue
            order = f * 0.55 + low * 0.55 + rnd.uniform(-0.08, 0.08)
            order = max(0.0, min(1.0, order))
            if order < 0.06:
                continue
            a = int(round(20 + order * 235))
            # thicker clots are darker; mud darker at its heart than dust
            dark = (0.4 if kind == "mud" else 0.3) * f + rnd.uniform(0, 0.2)
            g = int(round(255 * (1 - dark)))
            im.putpixel((x, y), (g, a))
    return im


# A 64x64 skin (the player's layout, which most humanoid mobs share): the side faces of each body part, and how dirty
# that part gets for its height. Clothes and skin get it legs first; the outer layer (hat, jacket, sleeves, trousers) is
# left blank, as the client paints each outer pixel from the body pixel under it.
BODY_SIDES = [
    ((0, 8, 32, 16), 0.15),    # head
    ((16, 20, 40, 32), 0.6),   # body
    ((40, 20, 56, 32), 0.5),   # right arm
    ((32, 52, 48, 64), 0.5),   # left arm
    ((0, 20, 16, 32), 1.0),    # right leg
    ((16, 52, 32, 64), 1.0),   # left leg
]
BODY_CAPS = [(8, 0, 24, 8), (20, 16, 36, 20), (44, 16, 52, 20), (36, 48, 44, 52), (4, 16, 12, 20), (20, 48, 28, 52)]


def body_low(x, y):
    for (x0, y0, x1, y1), w in BODY_SIDES:
        if x0 <= x < x1 and y0 <= y < y1:
            return w * (0.2 + 0.8 * (y - y0 + 0.5) / (y1 - y0))
    for x0, y0, x1, y1 in BODY_CAPS:
        if x0 <= x < x1 and y0 <= y < y1:
            return 0.2
    return None


def body_mask(seed, kind):
    """The body mask: as mask(), but blank off the body parts' own faces."""
    im = mask(seed, 64, 64, kind, lambda x, y: body_low(x, y) or 0.0)
    for y in range(64):
        for x in range(64):
            if body_low(x, y) is None:
                im.putpixel((x, y), (0, 0))
    return im


def item_low(x, y):
    return 0.2 + 0.8 * (y + 0.5) / 16


def main():
    for kind, seed in (("dust", 11), ("mud", 23)):
        mask(seed, 64, 32, kind, lowness).convert("RGBA").save(ROOT + "armour_" + kind + ".png")
        mask(seed + 100, 16, 16, kind, item_low).convert("RGBA").save(ROOT + "item_" + kind + ".png")
        body_mask(seed + 200, kind).convert("RGBA").save(ROOT + "body_" + kind + ".png")


if __name__ == "__main__":
    main()
