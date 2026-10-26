"""Draws Jaymo's Kingdoms' mana-infused wand (0.7.234, original art), vanilla style (16x16, no black outline, light from
the top-left): a short dark-wood wand lying corner to corner, a gold band below the head, and an amethyst at its tip with
mana's blue light caught in it.
Run from kingdoms/: python -I tools/gen_mana_wand.py"""
import os

from PIL import Image

OUT = os.path.join("src", "main", "resources", "assets", "jaymoskingdoms", "textures", "item", "mana_wand.png")

WOOD = (74, 48, 30)
WOOD_LIT = (104, 70, 44)
GOLD = (226, 180, 60)
GOLD_DARK = (166, 122, 36)
AMETHYST = (138, 92, 200)
AMETHYST_DARK = (92, 58, 150)
AMETHYST_LIT = (204, 168, 240)
MANA = (122, 178, 255)
MANA_HOT = (214, 234, 255)

ROWS = [
    "................",
    "...........m....",
    "..........MaAm..",
    ".........maAAa..",
    "..........daAM..",
    "..........gda...",
    ".........Gg.m...",
    "........wW......",
    ".......wW.......",
    "......wW........",
    ".....wW.........",
    "....wW..........",
    "...wW...........",
    "..wW............",
    ".wW.............",
    "................",
]
KEY = {"w": WOOD, "W": WOOD_LIT, "g": GOLD, "G": GOLD_DARK, "a": AMETHYST, "A": AMETHYST_LIT, "d": AMETHYST_DARK,
       "m": MANA, "M": MANA_HOT}


def main():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(ROWS):
        for x, ch in enumerate(row):
            if ch in KEY:
                img.putpixel((x, y), KEY[ch] + (255,))
    img.save(OUT)


if __name__ == "__main__":
    main()
