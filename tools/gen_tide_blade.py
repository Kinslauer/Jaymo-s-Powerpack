"""Generates the blade of dark water's item texture (Darktide's conjured katana), 16x16, held diagonally.

All original pixel art, drawn procedurally in the vanilla sword layout: the grip bottom-left, the tip top-right, a
one-pixel dark outline. The blade is dark water: an ink-black spine, deep navy body, a pale moonlit edge, and a drip
falling from it. The grip is the same water, wound darker.
Run from the project root:  python -I tools/gen_tide_blade.py

  textures/item/tide_blade.png
"""
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "jaymospowers" / "textures" / "item"

OUTLINE = (5, 8, 20, 255)
INK = (10, 16, 40, 255)
DEEP = (16, 40, 90, 255)
MID = (34, 78, 150, 255)
EDGE = (150, 200, 240, 255)
FOAM = (225, 240, 255, 255)
GRIP = (8, 12, 28, 255)
WRAP = (30, 56, 110, 255)

img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
px = img.load()

# the blade: a slightly curved diagonal, three pixels across (edge above-left, body, spine below-right)
for i in range(11):
    x, y = 4 + i, 11 - i
    if i >= 7:
        y += 1  # the katana's curve: the last third lifts less
    body = MID if i in (2, 5, 8) else DEEP
    for dx, dy, c in ((-1, 0, EDGE), (0, 0, body), (0, 1, INK)):
        if 0 <= x + dx < 16 and 0 <= y + dy < 16:
            px[x + dx, y + dy] = c
px[14, 2] = FOAM
px[13, 2] = EDGE
# the guard (a small round tsuba) and the grip
for x, y in ((3, 10), (4, 11), (5, 12), (2, 11), (5, 13)):
    px[x, y] = WRAP
for i in range(3):
    px[2 - i + 1, 12 + i] = GRIP if i % 2 else WRAP
    px[2 - i, 12 + i] = GRIP
px[0, 15] = WRAP
# a drip leaving the blade
px[9, 9] = MID
px[9, 11] = EDGE

# one-pixel outline round everything drawn
solid = [(x, y) for x in range(16) for y in range(16) if px[x, y][3]]
for x, y in solid:
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        nx, ny = x + dx, y + dy
        if 0 <= nx < 16 and 0 <= ny < 16 and not px[nx, ny][3]:
            px[nx, ny] = OUTLINE

OUT.mkdir(parents=True, exist_ok=True)
img.save(OUT / "tide_blade.png")
print("wrote", OUT / "tide_blade.png")
