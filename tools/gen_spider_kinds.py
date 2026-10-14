"""Jaymo's Kingdoms: the six spider kinds' skins (wild/SpiderKinds), drawn from scratch on the spider model's 64x32
box layout (head 32,4 8x8x8; neck 0,0 6x6x6; abdomen 0,12 10x8x12; legs 18,0 16x2x2). Our own pixels, no game art.
Run: python3 tools/gen_spider_kinds.py"""
import random
from PIL import Image

OUT = "kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/spider/{}.png"


def faces(u, v, w, h, d):
    """Each face of a box as (name, x0, y0, width, height) in texture pixels."""
    return [("top", u + d, v, w, d), ("bottom", u + d + w, v, w, d),
            ("right", u, v + d, d, h), ("front", u + d, v + d, w, h),
            ("left", u + d + w, v + d, d, h), ("back", u + 2 * d + w, v + d, w, h)]


HEAD, NECK, BODY, LEG = (32, 4, 8, 8, 8), (0, 0, 6, 6, 6), (0, 12, 10, 8, 12), (18, 0, 16, 2, 2)


def shade(c, f):
    return tuple(max(0, min(255, int(x * f))) for x in c)


def fur(img, rnd, box, base, dark, light):
    for name, x0, y0, w, h in faces(*box):
        for y in range(h):
            for x in range(w):
                r = rnd.random()
                c = dark if r < 0.25 else light if r > 0.85 else base
                if name == "bottom":
                    c = shade(c, 0.7)
                img.putpixel((x0 + x, y0 + y), c + (255,))


def legs(img, rnd, base, band, dark):
    for name, x0, y0, w, h in faces(*LEG):
        for y in range(h):
            for x in range(w):
                c = band if name in ("front", "back", "top") and (x // 3) % 2 == 1 else base
                if rnd.random() < 0.2:
                    c = dark
                img.putpixel((x0 + x, y0 + y), c + (255,))


def abdomen_top(box):
    _, x0, y0, w, h = faces(*box)[0]
    return x0, y0, w, h


def eyes_dark(img, colour):
    # the head's front stays dark where the eyes sit, so the glowing eyes read
    _, x0, y0, w, h = faces(*HEAD)[3]
    for y in range(1, 4):
        for x in range(1, w - 1):
            img.putpixel((x0 + x, y0 + y), colour + (255,))


def make(kind, seed, base, dark, light, leg_band, pattern):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    for box in (HEAD, NECK, BODY):
        fur(img, rnd, box, base, dark, light)
    legs(img, rnd, base, leg_band, dark)
    pattern(img, rnd)
    eyes_dark(img, shade(dark, 0.6))
    img.save(OUT.format(kind))


def top_and_back(img, fn):
    for name, x0, y0, w, h in faces(*BODY):
        if name in ("top", "back", "left", "right"):
            for y in range(h):
                for x in range(w):
                    c = fn(name, x, y, w, h)
                    if c:
                        img.putpixel((x0 + x, y0 + y), c + (255,))


def birch(img, rnd):
    # pale bark with black flecks
    top_and_back(img, lambda n, x, y, w, h: (20, 20, 18) if rnd.random() < 0.12 and y % 3 == 0 else None)


def desert(img, rnd):
    # brown bands across the abdomen, pale spine tips
    def f(n, x, y, w, h):
        if n == "top" and y % 4 == 1:
            return (122, 84, 44)
        if rnd.random() < 0.08:
            return (250, 236, 200)
        return None
    top_and_back(img, f)


def swamp(img, rnd):
    # a lily pad on its back: a round lighter disc with a notch and veins
    def f(n, x, y, w, h):
        if n != "top":
            return (54, 74, 40) if rnd.random() < 0.15 else None
        cx, cy = (w - 1) / 2, (h - 1) / 2
        dx, dy = x - cx, y - cy
        if dx * dx + dy * dy <= 4.6 ** 2 and not (abs(dx) < 0.8 and dy < 0):
            if x == round(cx) or y == round(cy):
                return (70, 112, 44)
            return (104, 156, 62) if rnd.random() > 0.2 else (88, 138, 52)
        return None
    top_and_back(img, f)


def cavern(img, rnd):
    # gems in the stony back: amethyst, emerald, a diamond
    gems = [(170, 110, 220), (190, 140, 240), (60, 200, 110), (110, 230, 230)]
    def f(n, x, y, w, h):
        if n == "top" and rnd.random() < 0.13:
            return rnd.choice(gems)
        return None
    top_and_back(img, f)


def jungle(img, rnd):
    # yellow stripes down the abdomen
    def f(n, x, y, w, h):
        if n == "top" and x in (2, 7):
            return (226, 204, 60)
        if n == "back" and x in (2, 7):
            return (226, 204, 60)
        return None
    top_and_back(img, f)


def ice(img, rnd):
    # frost cracks
    def f(n, x, y, w, h):
        if (x + y) % 5 == 0 and rnd.random() < 0.6:
            return (244, 252, 255)
        return None
    top_and_back(img, f)


make("birch", 1, (214, 208, 190), (160, 154, 140), (236, 232, 220), (60, 56, 50), birch)
make("desert", 2, (196, 160, 98), (150, 116, 66), (222, 196, 140), (110, 78, 42), desert)
make("swamp", 3, (66, 90, 50), (44, 62, 34), (86, 116, 62), (34, 48, 28), swamp)
make("cavern", 4, (66, 62, 74), (44, 42, 52), (88, 84, 98), (34, 32, 40), cavern)
make("jungle", 5, (60, 112, 40), (40, 80, 28), (84, 140, 56), (180, 160, 40), jungle)
make("ice", 6, (170, 210, 230), (124, 168, 196), (214, 238, 248), (90, 130, 160), ice)
