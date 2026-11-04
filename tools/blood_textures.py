"""Paints the blood textures for bloodied gear and bodies, bloody footprints and blood running down walls.
Run from the repository root: python -I tools/blood_textures.py

Masks (read by client/wet/ArmourGrimeTextures, the same way as the grime masks):
  textures/blood/armour_blood.png  64x32 armour sheet
  textures/blood/item_blood.png    16x16 item icon (held weapons, armour icons)
  textures/blood/body_blood.png    64x64 skin (any mob; a player's outer layer is left clean)
  alpha = how early that pixel gets bloody (255 = the first spatter, low = only when soaked, 0 = never)
  grey  = how dark the blood is there (white = the blood colour as is, darker = a thicker clot)
Particle sprites (grey, tinted with the blood's colour like the puddles):
  textures/particle/blood/print_1.png, print_2.png   a boot print and a bare-ish sole, toes up the sprite
  textures/particle/blood/run_1.png, run_2.png       a streak running down a wall, its drip at the bottom
Our own pixels, drawn by this script; nothing is taken from any pack (Hematic was ideas only)."""
import math
import random
from PIL import Image

ROOT = "src/main/resources/assets/jaymospowers/textures/"
SHADES = [(170, 174, 173), (210, 214, 213), (250, 255, 254)]


def blobs(rnd, w, h, count, rmin, rmax, weight=None, stretch=1.0):
    """Soft splats: each pixel's value is the strongest splat covering it. weight(x, y) biases where they land."""
    field = [[0.0] * w for _ in range(h)]
    placed = 0
    tries = 0
    while placed < count and tries < count * 40:
        tries += 1
        cx, cy = rnd.uniform(0, w), rnd.uniform(0, h)
        if weight is not None and rnd.random() > weight(int(cx), int(cy)):
            continue
        placed += 1
        r = rnd.uniform(rmin, rmax)
        lobe = rnd.uniform(0, math.tau)
        for y in range(max(0, int(cy - r * stretch - 2)), min(h, int(cy + r * stretch + 3))):
            for x in range(max(0, int(cx - r - 2)), min(w, int(cx + r + 3))):
                dx, dy = (x + 0.5 - cx) / r, (y + 0.5 - cy) / (r * stretch)
                a = math.atan2(dy, dx)
                d = math.hypot(dx, dy) * (1 + 0.3 * math.sin(4 * a + lobe))
                if d < 1:
                    field[y][x] = max(field[y][x], 1 - d)
        # a run or two down from a bigger splat
        if r > rmin + (rmax - rmin) * 0.5:
            for _ in range(rnd.randint(0, 2)):
                x = int(cx + rnd.uniform(-r * 0.6, r * 0.6))
                for i in range(rnd.randint(2, 5)):
                    y = int(cy + r * 0.5) + i
                    if 0 <= x < w and 0 <= y < h:
                        field[y][x] = max(field[y][x], 0.75 - 0.1 * i)
    return field


def mask(seed, w, h, count, rmin, rmax, weight=None, blank=None, specks=0.15):
    rnd = random.Random(seed)
    field = blobs(rnd, w, h, count, rmin, rmax, weight)
    im = Image.new("LA", (w, h), (0, 0))
    for y in range(h):
        for x in range(w):
            if blank is not None and blank(x, y):
                continue
            f = field[y][x]
            bias = weight(x, y) if weight is not None else 0.5
            if f <= 0 and rnd.random() < specks * bias:
                f = rnd.uniform(0.2, 0.55)
            if f <= 0:
                continue
            order = max(0.0, min(1.0, f * 0.6 + bias * 0.45 + rnd.uniform(-0.08, 0.08)))
            if order < 0.06:
                continue
            a = int(round(20 + order * 235))
            dark = 0.4 * f + rnd.uniform(0, 0.15)
            im.putpixel((x, y), (int(round(255 * (1 - dark))), a))
    return im


# The armour sheet: chest and arms take the most (the front of a fight), then the legs, the helmet least.
ARMOUR_PARTS = [((0, 0, 64, 16), 0.35), ((16, 16, 40, 32), 1.0), ((40, 16, 56, 32), 0.85), ((0, 16, 16, 32), 0.6)]


def armour_weight(x, y):
    for (x0, y0, x1, y1), wgt in ARMOUR_PARTS:
        if x0 <= x < x1 and y0 <= y < y1:
            return wgt
    return 0.2


def item_weight(x, y):
    # an item icon's blade runs bottom left to top right: the blood gathers toward the tip and the edge
    along = (x + (15 - y)) / 30.0
    return 0.25 + 0.75 * along


# A player-layout skin's second layer (hat, jacket, sleeves, trousers) stays clean so no blood floats off the body.
OUTER = [(32, 0, 64, 16), (16, 32, 40, 48), (40, 32, 56, 48), (0, 32, 16, 48), (0, 48, 16, 64), (48, 48, 64, 64)]


def body_blank(x, y):
    return any(x0 <= x < x1 and y0 <= y < y1 for x0, y0, x1, y1 in OUTER)


def body_weight(x, y):
    # chest and arms most, the head least
    if y < 16:
        return 0.35
    if 16 <= y < 32 and 16 <= x < 56:
        return 1.0
    return 0.7


def sprite(w, h, cells):
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for (x, y), s in cells.items():
        if 0 <= x < w and 0 <= y < h:
            r, g, b = SHADES[s]
            im.putpixel((x, y), (r, g, b, 255))
    return im


def print_sprite(seed, boot):
    """A left sole seen from above, toes at the top: a ball and a heel, the arch between them narrow."""
    rnd = random.Random(seed)
    cells = {}
    for y in range(1, 15):
        if boot:
            half = 2.6 if y < 8 else (1.7 if y < 10 else 2.2)
        else:
            half = 2.4 if 3 <= y < 8 else (1.4 if y < 10 else 2.0)
            if y < 3:
                half = 0
        cx = 7.5 + (0.6 if y < 8 else 0.0)
        for x in range(16):
            d = abs(x + 0.5 - cx)
            if d <= half:
                edge = half - d < 0.9
                if rnd.random() < (0.55 if edge else 0.9):
                    cells[(x, y)] = 0 if edge else (2 if rnd.random() < 0.6 else 1)
    if boot:
        # tread: a few clean bars across the sole
        for y in (3, 6, 12):
            for x in range(16):
                if (x, y) in cells and rnd.random() < 0.7:
                    del cells[(x, y)]
    else:
        # toes
        for i, tx in enumerate((5, 7, 9, 11)):
            if rnd.random() < 0.9:
                cells[(tx, 1 + (i % 2))] = 2
    return sprite(16, 16, cells)


def run_sprite(seed):
    """A streak from the top edge down: wide where it starts, thinning, a bead of a drip at the bottom."""
    rnd = random.Random(seed)
    cells = {}
    cx = 7.5 + rnd.uniform(-0.6, 0.6)
    for y in range(16):
        t = y / 15.0
        half = 2.6 * (1 - t) ** 1.4 + 0.7
        if y >= 13:
            half = 1.4 if y == 14 else 1.1
        wob = math.sin(y * 0.7 + seed) * 0.4
        for x in range(16):
            d = abs(x + 0.5 - (cx + wob))
            if d <= half:
                cells[(x, y)] = 0 if half - d < 0.7 else (2 if y < 3 or y >= 13 else 1)
    # a thin side trickle
    sx = int(cx) - 3 if seed % 2 else int(cx) + 3
    for y in range(1, rnd.randint(5, 9)):
        cells[(sx, y)] = 1
    return sprite(16, 16, cells)


def main():
    mask(11, 64, 32, 70, 1.0, 3.2, armour_weight).save(ROOT + "blood/armour_blood.png")
    mask(12, 16, 16, 9, 0.8, 2.0, item_weight, specks=0.2).save(ROOT + "blood/item_blood.png")
    mask(13, 64, 64, 110, 1.0, 3.4, body_weight, body_blank).save(ROOT + "blood/body_blood.png")
    print_sprite(21, True).save(ROOT + "particle/blood/print_1.png")
    print_sprite(22, False).save(ROOT + "particle/blood/print_2.png")
    run_sprite(31).save(ROOT + "particle/blood/run_1.png")
    run_sprite(32).save(ROOT + "particle/blood/run_2.png")


if __name__ == "__main__":
    main()
