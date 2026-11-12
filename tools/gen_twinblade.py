"""Draws the Twinblade (two blades on one long grip) and its schematic. Our own pixel art, no source texture.

    python -I tools/gen_twinblade.py
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "jaymospowers", "textures", "item", "armory")

OUTLINE = (28, 30, 38, 255)
STEEL = (176, 184, 196, 255)
STEEL_LIGHT = (226, 232, 240, 255)
STEEL_DARK = (112, 120, 136, 255)
WRAP = (92, 58, 36, 255)
WRAP_DARK = (62, 38, 24, 255)
COLLAR = (168, 132, 60, 255)
COLLAR_DARK = (116, 88, 38, 255)
PARCHMENT = (214, 196, 150, 255)
PARCHMENT_DARK = (176, 154, 108, 255)
INK = (64, 48, 36, 255)


def twinblade():
    """32 by 32, lying from the bottom-left corner to the top-right: blade, collar, grip, collar, blade."""
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = img.load()

    def dot(x, y, c):
        if 0 <= x < 32 and 0 <= y < 32:
            px[x, 31 - y] = c

    # along the diagonal: i = 1..30; the grip is the middle third
    for i in range(1, 31):
        grip = 12 <= i <= 19
        collar = i in (11, 20)
        tip = i <= 2 or i >= 29
        if grip:
            body = [(0, WRAP if i % 2 else WRAP_DARK)]
        elif collar:
            body = [(-1, COLLAR_DARK), (0, COLLAR), (1, COLLAR)]
        elif tip:
            body = [(0, STEEL_LIGHT)]
        else:
            body = [(-1, STEEL_DARK), (0, STEEL), (1, STEEL_LIGHT)]
        for off, colour in body:
            # (off: across the blade, toward the upper left when positive)
            dot(i - off, i + off, colour)
            if off == 0 and not grip:
                dot(i - 1, i, STEEL if not collar else COLLAR)
    # the outline: every empty pixel that touches a drawn one
    drawn = [(x, y) for x in range(32) for y in range(32) if px[x, y][3]]
    for x, y in drawn:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < 32 and 0 <= ny < 32 and not px[nx, ny][3]:
                px[nx, ny] = OUTLINE
    img.save(os.path.join(OUT, "twinblade.png"))


def schematic():
    """16 by 16: a worn sheet with the weapon inked across it."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for x in range(2, 14):
        for y in range(1, 15):
            edge = x in (2, 13) or y in (1, 14)
            px[x, y] = PARCHMENT_DARK if edge or (x * 7 + y * 3) % 11 == 0 else PARCHMENT
    # torn corners
    for x, y in ((2, 1), (13, 14), (13, 1)):
        px[x, y] = (0, 0, 0, 0)
    # the weapon, corner to corner, with its grip marked
    for i in range(4, 12):
        px[i, 15 - i] = INK
    for x, y in ((7, 9), (8, 8), (6, 8), (9, 7), (7, 7), (8, 6)):
        px[x, y] = INK
    for x, y in ((4, 4), (5, 4), (6, 4), (10, 12), (11, 12)):
        px[x, y] = PARCHMENT_DARK
    img.save(os.path.join(OUT, "twinblade_schematic.png"))


if __name__ == "__main__":
    twinblade()
    schematic()
