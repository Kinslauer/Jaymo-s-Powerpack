"""Draws Kingdoms' snail slime icon, and two particle textures other mods name but never shipped (our own drawings, not
their art): Knight Armor and Weapons' broken throwing axe and Psithurism's onsen water.

    python3 -I tools/gen_snail_slime_and_particles.py

Re-running overwrites the same files.
"""
import random
from pathlib import Path

from PIL import Image

ASSETS = Path(__file__).resolve().parent.parent / "kingdoms/src/main/resources/assets"
ROOT = ASSETS / "jaymoskingdoms/textures"
ITEM = ROOT / "item"


def snail_slime(rnd):
    """A glistening smear of snail slime."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    deep, body, light, shine = (82, 132, 74, 230), (118, 190, 109, 215), (168, 222, 150, 210), (236, 255, 228, 240)
    cx, cy = 7.5, 8.5
    for y in range(16):
        for x in range(16):
            d = ((x - cx) / 6.0) ** 2 + ((y - cy) / 4.2) ** 2
            if d > 1.0 - rnd.random() * 0.15:
                continue
            img.putpixel((x, y), deep if d > 0.7 else body if d > 0.3 else light)
    for x, y in ((5, 7), (6, 6), (7, 6), (10, 9)):
        img.putpixel((x, y), shine)
    for x, y in ((3, 12), (12, 4), (13, 5)):
        img.putpixel((x, y), body)
    return img


def broken_axe():
    """A throwing axe snapped short: a stub of haft and a chipped head (only its break particles use it)."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    wood, wood_dark = (122, 86, 52, 255), (86, 58, 34, 255)
    steel, steel_dark, edge = (150, 156, 164, 255), (98, 104, 112, 255), (214, 220, 226, 255)
    for i in range(7):
        img.putpixel((3 + i, 12 - i), wood)
        img.putpixel((4 + i, 12 - i), wood_dark)
    for x, y in ((10, 4), (10, 5), (11, 4), (11, 5), (11, 6), (12, 3), (12, 4), (12, 5), (12, 6), (12, 7), (13, 4), (13, 5),
                 (13, 6), (9, 4), (9, 5)):
        img.putpixel((x, y), steel)
    for x, y in ((13, 3), (14, 4), (14, 5), (13, 7)):
        img.putpixel((x, y), edge)
    for x, y in ((9, 6), (10, 6), (11, 7)):
        img.putpixel((x, y), steel_dark)
    return img


def onsen_water(rnd):
    """Warm, milky spring water (only its particles use it: the water itself is drawn by the fluid renderer)."""
    img = Image.new("RGBA", (16, 16))
    deep, mid, milk = (88, 168, 176), (124, 196, 196), (186, 224, 216)
    for y in range(16):
        for x in range(16):
            r = rnd.random()
            c = milk if r < 0.15 else mid if r < 0.65 else deep
            img.putpixel((x, y), c + (200,))
    return img


def main():
    rnd = random.Random(2179)
    snail_slime(rnd).save(ITEM / "snail_slime.png")
    for path, img in ((ASSETS / "knightarmorweapons/textures/item/knight_throwing_axe_broken.png", broken_axe()),
                      (ASSETS / "psithurism/textures/block/onsen_water.png", onsen_water(rnd))):
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path)
    print("drawn")


if __name__ == "__main__":
    main()
