"""The symbiote's black webs and its V shot (the symbiote mass).

  textures/block/web_symbiote_web.png  16x16 black cobweb (cross model, like a cobweb)
  textures/item/web_symbiote_mass.png  the web ball's sprite, turned to black goo
Run: python tools/gen_web_symbiote_web.py
"""
import math
import os
import random
from PIL import Image

random.seed(11)
base = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "jaymospowers", "textures"))

# --- the black cobweb: spokes from the middle and a few rings, a little irregular
web = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
px = web.load()


def put(x, y, a=255):
    if 0 <= x < 16 and 0 <= y < 16:
        v = random.randint(8, 22)
        px[x, y] = (v, v, v + 4, a)


cx, cy = 7.5, 7.5
for k in range(8):
    angle = k * math.pi / 4 + 0.2
    for r in range(0, 11):
        put(int(round(cx + math.cos(angle) * r)), int(round(cy + math.sin(angle) * r)))
for ring in (2.5, 4.5, 6.5):
    for step in range(64):
        angle = step * math.pi * 2 / 64
        wobble = ring + math.sin(step * 0.9) * 0.4
        put(int(round(cx + math.cos(angle) * wobble)), int(round(cy + math.sin(angle) * wobble)), 220)
# a glossy fleck or two
for _ in range(5):
    x, y = random.randrange(16), random.randrange(16)
    if px[x, y][3]:
        px[x, y] = (52, 50, 58, 255)
web.save(os.path.join(base, "block", "web_symbiote_web.png"))

# --- the symbiote mass: the web ball's shape, in glossy black
ball = Image.open(os.path.join(base, "item", "web_ball.png")).convert("RGBA")
bp = ball.load()
for x in range(ball.width):
    for y in range(ball.height):
        r, g, b, a = bp[x, y]
        if a:
            light = (r + g + b) / 765.0
            v = int(6 + 40 * light ** 2)
            bp[x, y] = (v, v, v + 5, a)
ball.save(os.path.join(base, "item", "web_symbiote_mass.png"))
print("ok")
