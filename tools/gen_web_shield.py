"""Generates the web shield's worn texture (textures/entity/web_shield.png, 32x32): a round web disc, drawn on the two
crossed boxes of client/power/web/WebShieldClient. Our own art. Run from the project root:  python -I tools/gen_web_shield.py
"""
import math
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "jaymospowers" / "textures" / "entity" / "web_shield.png"
BASE, LINE, RIM, HUB = (190, 198, 212, 255), (246, 248, 252, 255), (116, 124, 140, 255), (255, 255, 255, 255)

img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
px = img.load()


def fill(x0, y0, w, h, fn):
    for j in range(h):
        for i in range(w):
            px[x0 + i, y0 + j] = fn(i - (w - 1) / 2.0, j - (h - 1) / 2.0)


def face(dz, dy):
    r = math.hypot(dz, dy)
    if r >= 3.0:
        return RIM
    if r < 0.8:
        return HUB
    spoke = abs(dz) < 0.6 or abs(dy) < 0.6 or abs(abs(dz) - abs(dy)) < 0.6
    ring = abs(r - 2.0) < 0.55
    return LINE if spoke or ring else BASE


def edge(a, b):
    return RIM


# box A: 1 x 7 x 5 at (0,0): top/bottom, west/east faces 5x7, front/back 1x7
fill(5, 0, 1, 5, edge); fill(6, 0, 1, 5, edge)
fill(0, 5, 5, 7, face); fill(6, 5, 5, 7, face)
fill(5, 5, 1, 7, edge); fill(11, 5, 1, 7, edge)
# box B: 1 x 5 x 7 at (0,14): west/east faces 7x5
fill(7, 14, 1, 7, edge); fill(8, 14, 1, 7, edge)
fill(0, 21, 7, 5, face); fill(8, 21, 7, 5, face)
fill(7, 21, 1, 5, edge); fill(15, 21, 1, 5, edge)
OUT.parent.mkdir(parents=True, exist_ok=True)
img.save(OUT)
print("wrote", OUT)
