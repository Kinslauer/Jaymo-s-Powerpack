"""Draws Kingdoms' vampire bat wings (0.7.58): an elytra-layout texture (64x32), leathery membrane with finger bones
and a scalloped trailing edge. Run from the repo root: python3 tools/gen_bat_wings.py"""
import math
import random
from PIL import Image

OUT = "kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/vampire/bat_wings.png"
random.seed(26)
img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
px = img.load()
MEMBRANE = [(38, 26, 32), (44, 30, 37), (33, 23, 29), (50, 33, 40)]
BONE = (88, 70, 74)
# the elytra's left-wing box (10 x 20 x 2) is unwrapped at u 22..46, v 0..22
for u in range(22, 46):
    for v in range(0, 22):
        c = random.choice(MEMBRANE)
        # veins: faint, a little redder
        if (u * 7 + v * 3) % 11 == 0:
            c = (c[0] + 18, c[1] + 4, c[2] + 6)
        px[u, v] = c + (255,)
# finger bones fanning from the shoulder (top edge of the front face, u 24..34)
for i, ang in enumerate([8, 26, 44, 62]):
    a = math.radians(ang)
    for t in range(0, 26):
        u = int(round(25 + math.sin(a) * t * 0.45))
        v = int(round(2 + math.cos(a) * t * 0.8))
        if 24 <= u < 34 and 2 <= v < 22:
            px[u, v] = BONE + (255,)
            px[u + 12, v] = BONE + (255,)  # the back face mirrors it
# the leading bone along the top
for u in range(22, 46):
    px[u, 2] = BONE + (255,)
# scalloped trailing edge: arcs bitten out of the bottom of the front and back faces
for base in (24, 36):
    for u in range(base, base + 10):
        k = (u - base) % 5
        depth = [4, 2, 1, 2, 4][k]
        for v in range(22 - depth, 22):
            px[u, v] = (0, 0, 0, 0)
img.save(OUT)
print("wrote", OUT)
