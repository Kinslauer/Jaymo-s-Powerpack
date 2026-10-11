"""Draws Jaymo's Kingdoms' item sprites (original art). Run from kingdoms/: python -I tools/gen_items.py

plot_stake: a pointed wooden stake with a little red pennant tied on near the top.
"""
import os

from PIL import Image

OUT = os.path.join("src", "main", "resources", "assets", "jaymoskingdoms", "textures", "item")

WOOD_DARK = (92, 62, 34, 255)
WOOD = (140, 98, 56, 255)
WOOD_LIGHT = (176, 132, 82, 255)
CLOTH = (186, 38, 36, 255)
CLOTH_DARK = (128, 24, 24, 255)
STRING = (222, 214, 190, 255)


def plot_stake():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    # the stake: a diagonal shaft from the top right down to a point at the bottom left
    for i in range(12):
        x, y = 13 - i, 2 + i
        px[x, y] = WOOD
        if x - 1 >= 0:
            px[x - 1, y] = WOOD_DARK
        if y - 1 >= 0 and i < 11:
            px[x, y - 1] = WOOD_LIGHT
    px[1, 14] = WOOD_DARK
    px[0, 15] = WOOD_DARK
    # the cap at the top
    px[14, 1] = WOOD_DARK
    px[13, 1] = WOOD_LIGHT
    # the pennant, tied on with a twist of string
    px[11, 4] = STRING
    px[10, 3] = STRING
    for y, (x0, x1) in enumerate([(5, 9), (4, 9), (5, 8), (6, 8), (7, 8)]):
        for x in range(x0, x1 + 1):
            px[x, 1 + y] = CLOTH if (x + y) % 3 else CLOTH_DARK
    os.makedirs(OUT, exist_ok=True)
    img.save(os.path.join(OUT, "plot_stake.png"))


if __name__ == "__main__":
    plot_stake()
    print("drew", OUT)
