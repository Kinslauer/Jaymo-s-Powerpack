"""The symbiote's maw for Consume (WebMouthLayer): textures/entity/web_symbiote_mouth.png, 64x64.

Regions (box UVs, see WebMouthLayer):
  (0,0)   44x20  the skull: black, glossy, a few red veins
  (0,20)  44x15  the lower jaw: the same
  (0,40)  24x12  upper fangs, pointing down (opaque from the top)
  (0,52)  24x12  lower fangs, pointing up (opaque from the bottom)
  (32,40) 32x10  the inside of the mouth: wet dark red
Run: python tools/gen_web_maw.py
"""
import os
import random
from PIL import Image

random.seed(7)
img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
px = img.load()


def skin(x0, y0, w, h):
    for x in range(x0, x0 + w):
        for y in range(y0, y0 + h):
            v = random.randint(6, 16)
            px[x, y] = (v, v, v + 3, 255)
    # a few dark red veins
    for _ in range(4):
        x, y = random.randrange(x0, x0 + w), random.randrange(y0, y0 + h)
        for _ in range(random.randint(4, 9)):
            if x0 <= x < x0 + w and y0 <= y < y0 + h:
                px[x, y] = (70, 8, 14, 255)
            x += random.choice((-1, 0, 1))
            y += random.choice((-1, 1))


def fangs(x0, y0, w, h, down):
    for x in range(x0, x0 + w):
        # long fang, short tooth, gap...
        k = (x - x0) % 3
        length = h if k == 0 else h * 2 // 3 if k == 1 else 0
        for i in range(length):
            y = y0 + i if down else y0 + h - 1 - i
            shade = 235 - i * 6
            px[x, y] = (shade, shade - 5, shade - 25, 255)


def maw(x0, y0, w, h):
    for x in range(x0, x0 + w):
        for y in range(y0, y0 + h):
            r = random.randint(90, 130)
            px[x, y] = (r, 10, 18 + random.randint(0, 10), 255)


skin(0, 0, 44, 20)
skin(0, 20, 44, 15)
fangs(0, 40, 24, 12, True)
fangs(0, 52, 24, 12, False)
maw(32, 40, 32, 10)

out = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "jaymospowers", "textures", "entity",
                   "web_symbiote_mouth.png")
img.save(os.path.normpath(out))
print("wrote", os.path.normpath(out))
