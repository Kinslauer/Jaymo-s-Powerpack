"""Draws the class items' sprites (original art). Run from kingdoms/: python -I tools/gen_classes.py

writ_of_rebirth: a rolled parchment, unrolled a little at the top, with a purple wax seal and a line of script.
"""
import os

from PIL import Image

OUT = os.path.join("src", "main", "resources", "assets", "jaymoskingdoms", "textures", "item")

PAPER = (232, 214, 170, 255)
PAPER_DARK = (196, 172, 124, 255)
PAPER_EDGE = (140, 112, 70, 255)
INK = (76, 58, 40, 255)
WAX = (128, 48, 160, 255)
WAX_DARK = (86, 28, 112, 255)
WAX_LIGHT = (176, 104, 208, 255)


def writ():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    # the sheet
    for y in range(2, 14):
        for x in range(3, 13):
            px[x, y] = PAPER
        px[3, y] = PAPER_EDGE
        px[12, y] = PAPER_EDGE
    for x in range(3, 13):
        px[x, 13] = PAPER_EDGE
    # the roll at the top, a shade darker where it curls
    for x in range(2, 14):
        px[x, 1] = PAPER_EDGE
        px[x, 2] = PAPER_DARK
    px[2, 2] = PAPER_EDGE
    px[13, 2] = PAPER_EDGE
    # lines of script
    for y, (x0, x1) in enumerate([(5, 10), (5, 9), (5, 10), (5, 8)]):
        for x in range(x0, x1 + 1):
            if (x + y) % 2 == 0:
                px[x, 4 + y] = INK
    # the seal
    for (x, y) in [(8, 10), (9, 10), (10, 10), (8, 11), (9, 11), (10, 11), (9, 9), (9, 12)]:
        px[x, y] = WAX
    px[8, 11] = WAX_DARK
    px[10, 11] = WAX_DARK
    px[9, 12] = WAX_DARK
    px[9, 10] = WAX_LIGHT
    img.save(os.path.join(OUT, "writ_of_rebirth.png"))


if __name__ == "__main__":
    writ()
    print("ok")
