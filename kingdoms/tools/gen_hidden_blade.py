"""The hidden blade worn on the wrist (Kingdoms 0.7.113): textures/entity/hidden_blade.png, 32x32, for HiddenBladeClient.

Boxes (texOffs, size w x h x d in pixels), unfolded the vanilla way (top/bottom strip of d rows, then the sides):
  bracer   (0, 0)   5 x 3 x 5   dark leather with a stitched seam and a brass buckle
  housing  (0, 10)  1 x 4 x 2   blued steel, the spring's case on the palm side
  blade    (0, 18)  1 x 9 x 1   bright steel, an edge highlight down one side
Run from kingdoms/: python -I tools/gen_hidden_blade.py
"""
import os
import random
from PIL import Image

OUT = os.path.join("src", "main", "resources", "assets", "jaymoskingdoms", "textures", "entity", "hidden_blade.png")
random.seed(113)
img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))


def jitter(rgb, amount):
    return tuple(max(0, min(255, c + random.randint(-amount, amount))) for c in rgb) + (255,)


def box_area(u, v, w, h, d):
    """Every pixel a box of this size samples: the top/bottom strip and the side strip."""
    for x in range(u + d, u + d + 2 * w):
        for y in range(v, v + d):
            yield x, y, "cap"
    for x in range(u, u + 2 * (d + w)):
        for y in range(v + d, v + d + h):
            yield x, y, "side"


# the bracer: dark leather, a lighter stitched seam across its middle row, a brass buckle on one face
LEATHER = (74, 46, 28)
for x, y, part in box_area(0, 0, 5, 3, 5):
    img.putpixel((x, y), jitter(LEATHER, 8))
for x in range(0, 20):
    if x % 2 == 0:
        img.putpixel((x, 6), jitter((150, 112, 72), 6))
for y in range(5, 8):
    img.putpixel((7, y), jitter((196, 158, 64), 10))
    img.putpixel((8, y), jitter((170, 132, 50), 10))
# edges a shade darker, top and bottom rows of the sides
for x in range(0, 20):
    img.putpixel((x, 5), jitter((56, 34, 20), 5))
    img.putpixel((x, 7), jitter((60, 37, 22), 5))

# the housing: blued steel with a rivet
STEEL = (88, 96, 112)
for x, y, part in box_area(0, 10, 1, 4, 2):
    img.putpixel((x, y), jitter(STEEL, 6))
img.putpixel((2, 13), (170, 176, 186, 255))

# the blade: bright steel, one edge lit, the tip row brightest
BLADE = (196, 202, 210)
for x, y, part in box_area(0, 18, 1, 9, 1):
    img.putpixel((x, y), jitter(BLADE, 5))
for y in range(19, 28):
    img.putpixel((1, y), (236, 240, 244, 255))
    img.putpixel((3, y), (150, 156, 166, 255))
img.putpixel((1, 18), (250, 252, 255, 255))
img.putpixel((2, 18), (250, 252, 255, 255))

os.makedirs(os.path.dirname(OUT), exist_ok=True)
img.save(OUT)
print("wrote", OUT)
