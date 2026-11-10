#!/usr/bin/env python3
"""Generate the Astronomer's Journal art: the open book's page spread and its inventory icon.

Original art in the shared astronomy palette (art/astronomy/README.md): sepia ink on warm paper, volume by parallel
hatching (never gradients), construction circles, centre points and guide lines left visible, and a working hand of
illegible notes in the margins. Deterministic: the same seed draws the same pages.

    python3 art/astronomy/generate_journal.py

writes
    kingdoms/src/main/resources/assets/jaymoskingdoms/textures/gui/astronomy/journal_spread.png  (384x240)
    kingdoms/src/main/resources/assets/jaymoskingdoms/textures/item/astronomers_journal.png      (16x16)
    art/astronomy/journal_preview.png                                                            (inspection)
"""
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "kingdoms/src/main/resources/assets/jaymoskingdoms/textures"
SPREAD_OUT = ASSETS / "gui/astronomy/journal_spread.png"
ICON_OUT = ASSETS / "item/astronomers_journal.png"
PREVIEW_OUT = Path(__file__).resolve().parent / "journal_preview.png"

# the shared palette
INK = (0x2B, 0x21, 0x1A)
INK_SOFT = (0x58, 0x43, 0x33)
GUIDE = (0x8D, 0x73, 0x51)
PAPER = (0xD0, 0xB9, 0x8C)  # the study paper (#c2a77a), lifted a little so the journal's text reads
PAPER_EDGE = (0xC2, 0xA7, 0x7A)
WOOD = (0x75, 0x52, 0x39)
WOOD_LIGHT = (0xA0, 0x78, 0x52)
WOOD_DARK = (0x49, 0x34, 0x26)
BRASS = (0xA4, 0x7C, 0x36)
BRASS_LIGHT = (0xD0, 0xAB, 0x5A)
RUBRIC = (0x7A, 0x2E, 0x1E)

W, H = 384, 240
GUTTER = W // 2
PAGE_TOP, PAGE_BOTTOM = 7, 233
LEFT_PAGE = (9, GUTTER - 1)
RIGHT_PAGE = (GUTTER + 1, W - 9)


def rgba(c, a):
    return (c[0], c[1], c[2], a)


def paper(img, rng):
    """Warm paper with a fine fibre noise and a few foxing spots (hard-edged, no gradient)."""
    px = img.load()
    for y in range(PAGE_TOP, PAGE_BOTTOM):
        for x in list(range(*LEFT_PAGE)) + list(range(*RIGHT_PAGE)):
            n = rng.randint(-5, 5)
            px[x, y] = (PAPER[0] + n, PAPER[1] + n, PAPER[2] + n - 1, 255)
    d = ImageDraw.Draw(img)
    for _ in range(10):
        cx, cy = rng.randint(14, W - 14), rng.randint(PAGE_TOP + 6, PAGE_BOTTOM - 6)
        r = rng.randint(0, 1)
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(PAPER_EDGE[0] - 6, PAPER_EDGE[1] - 8, PAPER_EDGE[2] - 10, 255))


def cover(d):
    """The leather boards behind the pages, and the stacked page edges along the outside."""
    d.rounded_rectangle((0, 2, W - 1, H - 2), radius=5, fill=rgba(WOOD_DARK, 255), outline=rgba(INK, 255))
    d.rectangle((3, 4, W - 4, H - 4), fill=rgba(WOOD, 255))
    for i, x in enumerate((5, 6, 7)):
        d.line((x, PAGE_TOP + 1 - i, x, PAGE_BOTTOM - 1 + i), fill=rgba(PAPER_EDGE if i % 2 == 0 else GUIDE, 255))
        d.line((W - 1 - x, PAGE_TOP + 1 - i, W - 1 - x, PAGE_BOTTOM - 1 + i), fill=rgba(PAPER_EDGE if i % 2 == 0 else GUIDE, 255))
    for y in (PAGE_BOTTOM, PAGE_BOTTOM + 1):
        d.line((8, y, W - 9, y), fill=rgba(PAPER_EDGE, 255))
    d.line((8, PAGE_BOTTOM + 2, W - 9, PAGE_BOTTOM + 2), fill=rgba(GUIDE, 255))


def gutter(d, rng):
    """The pages curving into the spine: broken parallel hatching, closer together and darker toward the fold."""
    for side in (-1, 1):
        for i, a in enumerate((120, 95, 72, 52, 36)):
            x = GUTTER + side * (2 + i * (i + 3) // 3)
            y = PAGE_TOP + 3 + rng.randint(0, 6)
            while y < PAGE_BOTTOM - 3:
                run = rng.randint(7, 16)
                d.line((x, y, x, min(PAGE_BOTTOM - 3, y + run)), fill=rgba(INK_SOFT, a))
                y += run + rng.randint(2, 4)
    d.line((GUTTER, PAGE_TOP, GUTTER, PAGE_BOTTOM), fill=rgba(INK_SOFT, 200))
    d.line((LEFT_PAGE[0], PAGE_TOP, RIGHT_PAGE[1] - 1, PAGE_TOP), fill=rgba(GUIDE, 160))


def arc_points(cx, cy, rx, ry, a0, a1, tilt=0.0, steps=90):
    pts = []
    ct, st = math.cos(tilt), math.sin(tilt)
    for k in range(steps + 1):
        a = math.radians(a0 + (a1 - a0) * k / steps)
        x, y = rx * math.cos(a), ry * math.sin(a)
        pts.append((cx + x * ct - y * st, cy + x * st + y * ct))
    return pts


def faint(layer, alpha):
    d = ImageDraw.Draw(layer)
    return d, rgba(GUIDE, alpha), rgba(INK_SOFT, alpha)


def sphere_diagram(layer, cx, cy, r):
    """The celestial sphere seen from latitude 45: horizon, pole axis tilted 45 degrees, equator, a star's circle."""
    d, guide, ink = faint(layer, 70)
    d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=ink)
    d.ellipse((cx - r - 3, cy - r - 3, cx + r + 3, cy + r + 3), outline=guide)
    for deg in range(0, 360, 10):
        a = math.radians(deg)
        inner = r - (4 if deg % 30 == 0 else 2)
        d.line((cx + inner * math.cos(a), cy + inner * math.sin(a), cx + r * math.cos(a), cy + r * math.sin(a)), fill=ink)
    d.line((cx - r - 12, cy, cx + r + 12, cy), fill=ink)  # the horizon
    tilt = math.radians(-45)
    ax, ay = math.cos(tilt) * (r + 10), math.sin(tilt) * (r + 10)
    d.line((cx - ax, cy - ay, cx + ax, cy + ay), fill=ink)  # the pole axis, 45 degrees up
    d.line(arc_points(cx, cy, r, r * 0.28, 0, 360, tilt + math.pi / 2), fill=guide)  # the equator
    d.line(arc_points(cx, cy, r * 0.74, r * 0.2, 0, 360, tilt + math.pi / 2), fill=guide)  # a star's daily circle
    d.line(arc_points(cx, cy, r * 0.42, r * 0.42, 180, 225), fill=ink)  # the latitude angle
    d.ellipse((cx - 1, cy - 1, cx + 1, cy + 1), fill=ink)
    d.ellipse((cx + ax - 2, cy + ay - 2, cx + ax + 2, cy + ay + 2), outline=ink)
    # construction: the bounding square and its diagonals
    d.rectangle((cx - r, cy - r, cx + r, cy + r), outline=rgba(GUIDE, 35))
    d.line((cx - r, cy - r, cx + r, cy + r), fill=rgba(GUIDE, 30))
    d.line((cx - r, cy + r, cx + r, cy - r), fill=rgba(GUIDE, 30))


def rings_diagram(layer, cx, cy, r):
    """An armillary study: nested rings at several tilts on one centre, with a hatched brass band."""
    d, guide, ink = faint(layer, 62)
    for k, (rx, ry, tilt) in enumerate(((r, r, 0), (r, r * 0.34, 0.35), (r * 0.92, r * 0.92 * 0.3, -0.6), (r * 0.3, r, 0.2))):
        d.line(arc_points(cx, cy, rx, ry, 0, 360, tilt), fill=ink if k == 0 else guide)
    d.ellipse((cx - r - 4, cy - r - 4, cx + r + 4, cy + r + 4), outline=guide)
    for i in range(0, 60, 4):
        a = math.radians(200 + i)
        d.line((cx + (r - 3) * math.cos(a), cy + (r - 3) * math.sin(a), cx + (r + 3) * math.cos(a), cy + (r + 3) * math.sin(a)), fill=ink)
    d.line((cx, cy - r - 14, cx, cy + r + 14), fill=guide)
    d.ellipse((cx - 1, cy - 1, cx + 1, cy + 1), fill=ink)
    # the stand: a turned post on a foot
    d.line((cx, cy + r + 4, cx, cy + r + 18), fill=ink)
    d.line((cx - 14, cy + r + 18, cx + 14, cy + r + 18), fill=ink)


def quadrant(layer, x, y, r):
    """A quadrant: a quarter circle with its degree scale and a sight line."""
    d, guide, ink = faint(layer, 66)
    d.line(arc_points(x, y, r, r, 180, 270), fill=ink)
    d.line((x, y, x - r, y), fill=ink)
    d.line((x, y, x, y - r), fill=ink)
    for deg in range(0, 91, 5):
        a = math.radians(180 + deg)
        inner = r - (5 if deg % 15 == 0 else 2)
        d.line((x + inner * math.cos(a), y + inner * math.sin(a), x + r * math.cos(a), y + r * math.sin(a)), fill=ink)
    a = math.radians(180 + 38)
    d.line((x, y, x + (r + 16) * math.cos(a), y + (r + 16) * math.sin(a)), fill=guide)
    d.line(arc_points(x, y, r * 0.35, r * 0.35, 180, 218), fill=ink)


def triangle(layer, x, y, w, h):
    d, guide, ink = faint(layer, 55)
    d.polygon(((x, y), (x + w, y), (x + w, y - h)), outline=ink)
    d.line((x + w - 6, y, x + w - 6, y - 6), fill=guide)
    d.line((x + w - 6, y - 6, x + w, y - 6), fill=guide)
    d.line(arc_points(x, y, 14, 14, -math.degrees(math.atan2(h, w)), 0), fill=ink)


def script(layer, rng, x0, y0, width, lines, alpha, step=6):
    """Lines of an illegible working hand: cursive strokes up and down a baseline, a tall stroke now and then, gaps
    between words."""
    d = ImageDraw.Draw(layer)
    col = rgba(INK_SOFT, alpha)
    for ln in range(lines):
        y = y0 + ln * step
        x = x0 + rng.randint(0, 4)
        end = x0 + width - rng.randint(0, max(1, width // 4))
        while x < end:
            pts = [(x, y)]
            for _ in range(rng.randint(2, 6)):
                h = 4 if rng.random() < 0.18 else rng.choice((1, 2, 2, 3))
                w = rng.choice((2, 2, 3))
                pts.append((x + 1, y - h))
                pts.append((x + w, y))
                x += w
            d.line(pts, fill=col)
            x += rng.randint(3, 5)


def spread():
    rng = random.Random(1609)
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    cover(ImageDraw.Draw(img))
    paper(img, rng)

    # the faint studies, on their own layer so they sit under the hatching and the fold
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    sphere_diagram(layer, 112, 128, 58)
    quadrant(layer, 62, 214, 34)
    rings_diagram(layer, 290, 104, 52)
    triangle(layer, 236, 206, 64, 36)
    # the working hand: notes in the margins and over the studies
    script(layer, rng, 14, 14, 160, 1, 90)
    script(layer, rng, 210, 14, 160, 1, 90)
    script(layer, rng, 120, 196, 60, 3, 70)
    script(layer, rng, 312, 182, 54, 4, 70)
    script(layer, rng, 36, 52, 46, 3, 55)
    for y in range(40, 200, 24):
        script(layer, rng, 11, y, 12, 2, 85, step=4)
        script(layer, rng, W - 24, y, 12, 2, 85, step=4)
    # small numerals of a worked sum, in the red of a rubric
    d = ImageDraw.Draw(layer)
    for k in range(5):
        x, y = 336 + k * 5, 62
        d.line((x, y, x + 2, y - 4, x + 3, y), fill=rgba(RUBRIC, 70))
    img.alpha_composite(layer)
    gutter(ImageDraw.Draw(img), rng)
    return img


def icon():
    """16x16: a walnut-bound journal with an armillary ring on its board, cream page edges, one hatch direction."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()

    def put(x, y, c):
        px[x, y] = rgba(c, 255)

    # the pages, showing along the right and the foot
    for y in range(3, 15):
        put(13, y, PAPER)
        put(14, y, PAPER_EDGE if y % 2 else PAPER)
    for x in range(4, 15):
        put(x, 14, PAPER_EDGE if x % 2 else PAPER)
    # the board and its spine band
    for y in range(2, 14):
        for x in range(3, 13):
            put(x, y, WOOD)
        put(3, y, WOOD_DARK)
        put(4, y, WOOD_LIGHT if y % 4 == 1 else WOOD)
    # the outline
    for x in range(2, 14):
        put(x, 1, INK)
    put(14, 2, INK)
    for y in range(1, 15):
        put(2, y, INK)
    for y in range(3, 16):
        put(15, y, INK)
    for x in range(3, 16):
        put(x, 15, INK)
    put(13, 2, INK)
    # hatching on the board (one direction)
    for (x, y) in ((11, 3), (12, 4), (11, 12), (12, 13), (10, 12), (6, 12), (7, 13)):
        put(x, y, WOOD_DARK)
    # the brass ring, its equator and its axis
    for (x, y) in ((7, 5), (8, 5), (9, 5), (10, 6), (10, 7), (10, 8), (9, 9), (8, 9), (7, 9), (6, 8), (6, 7), (6, 6)):
        put(x, y, BRASS)
    for (x, y) in ((7, 5), (6, 6), (6, 7), (8, 7)):
        put(x, y, BRASS_LIGHT)
    for (x, y) in ((7, 7), (9, 7)):
        put(x, y, BRASS)
    for (x, y) in ((9, 4), (8, 6), (8, 8), (7, 10)):
        put(x, y, INK_SOFT)
    return img


def main():
    sp = spread()
    ic = icon()
    SPREAD_OUT.parent.mkdir(parents=True, exist_ok=True)
    sp.save(SPREAD_OUT)
    ic.save(ICON_OUT)
    preview = Image.new("RGBA", (W * 2 + 16 * 8 + 24, H * 2), (40, 34, 28, 255))
    preview.alpha_composite(sp.resize((W * 2, H * 2), Image.NEAREST), (0, 0))
    preview.alpha_composite(ic.resize((16 * 8, 16 * 8), Image.NEAREST), (W * 2 + 12, 12))
    preview.save(PREVIEW_OUT)
    print("wrote", SPREAD_OUT.relative_to(ROOT), ICON_OUT.relative_to(ROOT), PREVIEW_OUT.relative_to(ROOT))


if __name__ == "__main__":
    main()
