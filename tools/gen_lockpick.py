"""Paints Kingdoms' lockpick icon (16px, vanilla style: a stick's wrapped grip, an iron shaft, a hooked tip).

Run from the repo root: python -I tools/gen_lockpick.py
"""
import os
from PIL import Image

OUT = 'kingdoms/src/main/resources/assets/jaymoskingdoms/textures/item/lockpick.png'

PALETTE = {
    '.': None,
    'O': (40, 40, 46, 255),     # outline
    'L': (232, 232, 232, 255),  # iron light
    'M': (170, 170, 178, 255),  # iron mid
    'D': (108, 108, 118, 255),  # iron dark
    'h': (156, 117, 58, 255),   # grip light (vanilla stick ramp)
    'H': (112, 82, 38, 255),    # grip mid
    'k': (70, 50, 22, 255),     # grip dark / wraps
}

GRID = [
    "................",
    "............OO..",
    "...........OLMO.",
    "..........OLOOO.",
    ".........OLMO...",
    "........OLMO....",
    ".......OLMO.....",
    "......OLMO......",
    ".....OMDO.......",
    "....OhHkO.......",
    "...OhHkO........",
    "..OkhHO.........",
    ".OhHkO..........",
    ".OHkO...........",
    "..OO............",
    "................",
]


def main():
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(GRID):
        assert len(row) == 16, (y, row)
        for x, c in enumerate(row):
            if PALETTE[c]:
                im.putpixel((x, y), PALETTE[c])
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    im.save(OUT)
    print('wrote', OUT)


if __name__ == '__main__':
    main()
