"""Draws Jaymo's Kingdoms' item sprites (original art). Run from kingdoms/: python -I tools/gen_items.py

plot_stake: a pointed wooden stake with a little red pennant tied on near the top.
hidden_blade (0.7.98): a brown leather bracer with a spring blade run out of it, vanilla style (handle bottom-left, blade
top-right, no black outlines, light from the top-left, vanilla's own iron ramp).
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


# vanilla's iron ramp (tools/vanilla_style/vstyle.py MAT['iron'], steps 0-6: outline .. glint); leather browns A (darkest) .. E (lit)
IRON = [(0x18, 0x18, 0x18), (0x44, 0x44, 0x44), (0x6b, 0x6b, 0x6b), (0x96, 0x96, 0x96), (0xbe, 0xbe, 0xbe), (0xd8, 0xd8, 0xd8), (0xff, 0xff, 0xff)]
LEATHER = {"A": (0x35, 0x1e, 0x10), "B": (0x4f, 0x2f, 0x18), "C": (0x6e, 0x43, 0x24), "D": (0x8a, 0x58, 0x2e), "E": (0xa8, 0x72, 0x3e)}

# (first column, shades left to right) per row: the blade (iron steps) leaves the cuff at the top right
HIDDEN_BLADE = {
    1: (14, "4"),
    2: (12, "642"),
    3: (11, "542"),
    4: (10, "542"),
    5: (9, "542"),
    6: (8, "542"),
    7: (7, "542"),
    8: (4, "DC32BA"),
    9: (3, "EDCCBA"),
    10: (2, "EDCCBA"),
    11: (1, "EDCCBA"),
    12: (0, "EDCCBA"),
    13: (0, "DCCBA"),
    14: (1, "CBA"),
}
# the two straps across the cuff (cells where x - y is -6 and -9), a buckle on the first
STRAPS = ((3, 9), (4, 10), (5, 11), (1, 10), (2, 11), (3, 12))


def hidden_blade():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y, (x0, shades) in HIDDEN_BLADE.items():
        for i, ch in enumerate(shades):
            px[x0 + i, y] = (*IRON[int(ch)], 255) if ch.isdigit() else (*LEATHER[ch], 255)
    for x, y in STRAPS:
        px[x, y] = (*LEATHER["B"], 255) if px[x, y][:3] != LEATHER["A"] else (*LEATHER["A"], 255)
    px[4, 10] = (*IRON[4], 255)
    os.makedirs(OUT, exist_ok=True)
    img.save(os.path.join(OUT, "hidden_blade.png"))


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
    hidden_blade()
    print("drew", OUT)
