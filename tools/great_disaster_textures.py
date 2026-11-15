"""Draws the great disasters' sprites (medieval patch 9): Kingdoms' ash layer and the Last Bell; Powers' meteoric iron
heart and its lumps. Run from the repository root: python -I tools/great_disaster_textures.py"""
import math
import random
from PIL import Image

KINGDOMS = "kingdoms/src/main/resources/assets/jaymoskingdoms/textures/"
POWERS = "src/main/resources/assets/jaymospowers/textures/"


def clamp(v):
    return max(0, min(255, int(v)))


def ash():
    """Fine grey ash: mottled, a few darker cinders and pale flecks."""
    rnd = random.Random(9101)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            v = 96 + rnd.randint(-10, 10) + int(6 * math.sin(x * 0.9 + y * 0.4))
            im.putpixel((x, y), (clamp(v), clamp(v - 3), clamp(v - 5), 255))
    for _ in range(14):
        x, y = rnd.randrange(16), rnd.randrange(16)
        v = rnd.choice([52, 60, 150, 160])
        im.putpixel((x, y), (v, v - 2, v - 4, 255))
    for _ in range(3):
        # a cinder still glowing
        x, y = rnd.randrange(16), rnd.randrange(16)
        im.putpixel((x, y), (150, 62, 30, 255))
    return im


def bell():
    """A dark iron bell, cracked through with a dull red glow, hung from a black yoke."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    yoke = (34, 30, 30, 255)
    for x in range(5, 11):
        im.putpixel((x, 1), yoke)
    im.putpixel((7, 2), yoke)
    im.putpixel((8, 2), yoke)
    # the bell's profile: half-widths down its height
    widths = {3: 2, 4: 3, 5: 3, 6: 3, 7: 4, 8: 4, 9: 4, 10: 5, 11: 5, 12: 6, 13: 6}
    for y, w in widths.items():
        for x in range(8 - w, 8 + w):
            t = (x - (8 - w)) / max(1, 2 * w - 1)
            light = 1.0 - abs(t - 0.35) * 1.4
            v = 46 + 50 * max(0.0, light)
            im.putpixel((x, y), (clamp(v + 6), clamp(v), clamp(v - 2), 255))
    # the rim, and the clapper showing under it
    for x in range(2, 14):
        im.putpixel((x, 14), (28, 24, 24, 255))
    im.putpixel((7, 15), (70, 20, 12, 255))
    im.putpixel((8, 15), (70, 20, 12, 255))
    # the crack, glowing red
    crack = [(9, 4), (9, 5), (8, 6), (8, 7), (9, 8), (10, 9), (9, 10), (8, 11), (8, 12), (9, 13)]
    for i, (x, y) in enumerate(crack):
        g = 1.0 - i / len(crack) * 0.4
        im.putpixel((x, y), (clamp(200 * g), clamp(40 * g), clamp(20 * g), 255))
    return im


def heart():
    """A star's iron heart: black-blue pitted iron with bright metal showing through, scorched round the edges."""
    rnd = random.Random(9103)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            v = 42 + rnd.randint(-8, 8)
            edge = min(x, y, 15 - x, 15 - y)
            if edge == 0:
                v -= 12
            im.putpixel((x, y), (clamp(v - 4), clamp(v - 2), clamp(v + 6), 255))
    # bright veins of metal (Widmanstatten lines, crossing)
    for k in range(-16, 16, 5):
        for x in range(16):
            y = x + k
            if 0 <= y < 16 and rnd.random() < 0.8:
                v = 150 + rnd.randint(-15, 25)
                im.putpixel((x, y), (clamp(v - 6), clamp(v), clamp(v + 10), 255))
    for k in range(4, 32, 6):
        for x in range(16):
            y = k - x
            if 0 <= y < 16 and rnd.random() < 0.6:
                v = 120 + rnd.randint(-10, 20)
                im.putpixel((x, y), (clamp(v - 6), clamp(v), clamp(v + 8), 255))
    # pits
    for _ in range(10):
        x, y = rnd.randrange(1, 15), rnd.randrange(1, 15)
        im.putpixel((x, y), (18, 18, 24, 255))
    # a faint ember in a few
    for _ in range(3):
        x, y = rnd.randrange(1, 15), rnd.randrange(1, 15)
        im.putpixel((x, y), (170, 70, 30, 255))
    return im


def lump():
    """A lump of meteoric iron: a rounded, pitted nugget with a bright cut face."""
    rnd = random.Random(9104)
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    cx, cy = 7.5, 8.5
    for y in range(16):
        for x in range(16):
            dx, dy = (x - cx) / 6.0, (y - cy) / 5.2
            r = math.hypot(dx, dy) + 0.08 * math.sin(math.atan2(dy, dx) * 5)
            if r > 1.0:
                continue
            light = 1.0 - (dx + 0.3) * 0.5 - (dy + 0.3) * 0.5
            v = 40 + 40 * max(0.0, min(1.0, light)) + rnd.randint(-6, 6)
            if r > 0.86:
                v -= 16
            im.putpixel((x, y), (clamp(v - 4), clamp(v - 2), clamp(v + 6), 255))
    # the cut face: bright crossing lines
    for x in range(4, 10):
        for y in range(6, 10):
            if (x + y) % 3 == 0 or (x - y) % 4 == 0:
                im.putpixel((x, y), (176, 186, 204, 255))
            else:
                im.putpixel((x, y), (128, 136, 152, 255))
    for _ in range(5):
        x, y = rnd.randrange(3, 13), rnd.randrange(4, 14)
        if im.getpixel((x, y))[3]:
            im.putpixel((x, y), (20, 20, 26, 255))
    return im


if __name__ == "__main__":
    ash().save(KINGDOMS + "block/ash_layer.png")
    bell().save(KINGDOMS + "item/last_bell.png")
    heart().save(POWERS + "block/tinkering/meteoric_iron_heart.png")
    lump().save(POWERS + "item/tinkering/meteoric_iron.png")
    print("drawn")
