"""The Fang Gauntlets worn on the hands (Powers 0.18.74): textures/entity/fang_gauntlets.png, 32x32, for FangGauntletsClient.

Colours are the held item's (textures/item/armory/fang_gauntlets.png): brown leather, dark iron, grey plate, bone-white fangs.
Boxes (texOffs, size w x h x d in pixels), unfolded the vanilla way (top/bottom strip of d rows, then the sides):
  cuff   (0, 0)    5 x 3 x 5     brown leather with a stitched seam and an iron buckle
  plate  (0, 10)   0.4 x 2.8 x 3 grey iron over the back of the hand, riveted
  claws  (0|8|16, 18)  0.5 x ~6 x 0.8  bone-white fangs, an edge highlight down one side
Run from the repo root: python -I tools/gen_fang_gauntlets.py
"""
import os
import random
from PIL import Image

OUT = os.path.join("src", "main", "resources", "assets", "jaymospowers", "textures", "entity", "fang_gauntlets.png")
random.seed(1874)
img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))


def jitter(rgb, amount):
    return tuple(max(0, min(255, c + random.randint(-amount, amount))) for c in rgb) + (255,)


def fill(x0, y0, x1, y1, rgb, amount):
    for x in range(x0, x1):
        for y in range(y0, y1):
            img.putpixel((x, y), jitter(rgb, amount))


# the cuff: 20 x 8 of unfolded 5x3x5 box (top/bottom strips 5 rows, sides 3 rows)
LEATHER, DARK_LEATHER = (122, 78, 44), (90, 58, 34)
fill(0, 0, 20, 8, LEATHER, 6)
fill(0, 5, 20, 6, DARK_LEATHER, 4)
for x in range(0, 20, 2):
    img.putpixel((x, 6), jitter((36, 30, 28), 4))
for y in range(5, 8):
    img.putpixel((7, y), jitter((118, 120, 126), 6))
    img.putpixel((8, y), jitter((170, 172, 178), 6))

# the plate: grey iron, a dark rim, a rivet at each corner of the top face
fill(0, 10, 16, 16, (118, 120, 126), 6)
for x in range(0, 16):
    img.putpixel((x, 15), (36, 30, 28, 255))
img.putpixel((4, 10), (170, 172, 178, 255))
img.putpixel((6, 12), (170, 172, 178, 255))

# the claws: bone white, one edge lit, a grey root, the tip row brightest
for claw in range(3):
    u = claw * 8
    fill(u, 18, u + 4, 26, (236, 236, 228), 4)
    for y in range(18, 26):
        img.putpixel((u + 1, y), (250, 250, 244, 255))
        img.putpixel((u + 3, y), (200, 200, 192, 255))
    for x in range(u, u + 4):
        img.putpixel((x, 18), (160, 108, 66, 255))
        img.putpixel((x, 19), (118, 120, 126, 255))

os.makedirs(os.path.dirname(OUT), exist_ok=True)
img.save(OUT)
print("wrote", OUT)
