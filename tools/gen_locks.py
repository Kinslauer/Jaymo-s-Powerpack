"""Paints Kingdoms' lock, key, blank key and key ring icons (16px, vanilla style), (0.7.497).

Run from the repo root: python -I tools/gen_locks.py
Our own art, in the palette of tools/gen_lockpick.py.
"""
import math
import os
from PIL import Image

OUT = 'kingdoms/src/main/resources/assets/jaymoskingdoms/textures/item/'

OUTLINE = (40, 40, 46, 255)
IRON = {'L': (232, 232, 232, 255), 'M': (170, 170, 178, 255), 'D': (108, 108, 118, 255)}
BRASS = {'L': (222, 180, 82, 255), 'M': (170, 128, 48, 255), 'D': (110, 80, 28, 255)}

LOCK_GRID = [
    "................",
    "................",
    "......OOOO......",
    ".....OLMMDO.....",
    "....OLOOOODO....",
    "....OMO..OMO....",
    "....OMO..OMO....",
    "....OOOOOOOO....",
    "....OLLLLLMO....",
    "....OLMMMMDO....",
    "....OLMOODDO....",
    "....OLMMODDO....",
    "....OMMMMDDO....",
    "....OOOOOOOO....",
    "................",
    "................",
]


def new():
    return Image.new('RGBA', (16, 16), (0, 0, 0, 0))


def outline(im):
    """Puts the dark outline round every painted pixel."""
    src = im.copy()
    for y in range(16):
        for x in range(16):
            if src.getpixel((x, y))[3]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and src.getpixel((nx, ny))[3] and src.getpixel((nx, ny)) != OUTLINE:
                    im.putpixel((x, y), OUTLINE)
                    break


def shade(pixels, palette, im):
    """Paints pixels with light from the top left: the lighter ramp on the upper left of the part."""
    cx = sum(p[0] for p in pixels) / len(pixels)
    cy = sum(p[1] for p in pixels) / len(pixels)
    for x, y in pixels:
        d = (x - cx) + (y - cy)
        im.putpixel((x, y), palette['L'] if d < -0.6 else palette['D'] if d > 0.6 else palette['M'])


def lock():
    im = new()
    pal = {'O': OUTLINE, **IRON}
    for y, row in enumerate(LOCK_GRID):
        assert len(row) == 16, (y, row)
        for x, c in enumerate(row):
            if c != '.':
                im.putpixel((x, y), pal[c])
    return im


def key(teeth):
    im = new()
    ring = [(x, y) for x in range(2, 6) for y in range(9, 13) if not (3 <= x <= 4 and 10 <= y <= 11)]
    shaft = [(6 + i, 8 - i) for i in range(7)]
    palette = BRASS if teeth else IRON
    shade(ring + shaft, palette, im)
    if teeth:
        for p in ((11, 4), (9, 6)):
            im.putpixel(p, palette['D'])
    outline(im)
    return im


def key_ring():
    im = new()
    hoop = []
    for x in range(16):
        for y in range(16):
            d = math.hypot(x - 7.5, y - 5.5)
            if 4.1 <= d <= 5.4:
                hoop.append((x, y))
    shade(hoop, IRON, im)
    brass = [(6, y) for y in range(10, 15)] + [(7, 13), (7, 14)]
    iron = [(9, y) for y in range(10, 15)] + [(8, 13)]
    shade(brass, BRASS, im)
    shade(iron, IRON, im)
    outline(im)
    return im


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, im in (('lock', lock()), ('key', key(True)), ('blank_key', key(False)), ('key_ring', key_ring())):
        im.save(OUT + name + '.png')
        print('wrote', OUT + name + '.png')


if __name__ == '__main__':
    main()
