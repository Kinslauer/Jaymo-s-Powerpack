"""(0.7.323) The torn cloth item's icon (TornCloth): a ragged scrap of woven cloth, drawn in light greys so the item's dye tint
(the colour of the rags the wearer's beast wears) colours it. Our own: plain ramps and a seeded scatter.
Run from the kingdoms folder:  python -I tools/gen_torn_cloth.py"""
import os
import random
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "jaymoskingdoms",
                   "textures", "item", "torn_cloth.png")


def scrap():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    rng = random.Random(7)
    light, mid, dark, edge = (236, 236, 236, 255), (212, 212, 212, 255), (184, 184, 184, 255), (140, 140, 140, 255)
    # a slanted rag: the left edge cut straight, the right and bottom torn into points
    right = [12, 13, 13, 12, 14, 13, 13, 11, 12, 10, 11, 9]
    for i, y in enumerate(range(2, 14)):
        left = 3 + (1 if i % 5 == 2 else 0)
        for x in range(left, right[i] + 1):
            shade = mid if (x + y) % 2 == 0 else light
            if rng.random() < 0.12:
                shade = dark
            px[x, y] = shade
        px[left, y] = edge
        px[right[i], y] = edge
    for x in range(3, 13):
        if px[x, 2][3]:
            px[x, 2] = edge
    # the weave: a dark thread every third row
    for y in range(3, 13, 3):
        for x in range(4, 13):
            if px[x, y][3] and px[x, y] != edge:
                px[x, y] = dark
    # loose threads hanging off the torn side
    for x, y in ((14, 6), (15, 7), (14, 8), (12, 12), (11, 13), (10, 14), (9, 13)):
        px[x, y] = dark if (x + y) % 2 else edge
    return img


if __name__ == "__main__":
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    scrap().save(OUT)
    print("wrote", OUT)
