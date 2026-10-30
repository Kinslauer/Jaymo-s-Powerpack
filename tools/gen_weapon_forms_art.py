"""Writes the 16x16 vanilla-style art of Weapon Forms patch 3 (our own drawing, nothing taken from another mod):

    python -I tools/gen_weapon_forms_art.py

Kingdoms: the bolas. Powers: the Fang Gauntlets and their forged head (stone and iron, drawn in weapon_parts_assets.py), the Clockwork Hornet Hive and the
Clockwork Hornet it looses. '.' is clear; the letters pick from each picture's palette.
"""
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
POWERS = os.path.join(ROOT, "src", "main", "resources", "assets", "jaymospowers", "textures", "item")
KINGDOMS = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms", "textures", "item")

BOLAS = [
    "................",
    "...ooo..........",
    "..oGGGo.........",
    "..oGgGo.........",
    "..oGGGo.........",
    "...ooo.c........",
    ".......c........",
    "........c.......",
    ".........c......",
    "..........c.....",
    "..........c.ooo.",
    ".........c.oGGGo",
    "........c..oGgGo",
    "..........oGGGGo",
    "...........ooooo",
    "................",
]
BOLAS_PAL = {"o": (40, 40, 44), "G": (126, 128, 132), "g": (176, 178, 182), "c": (176, 140, 84)}

# the gauntlet: a leather-and-iron glove with three iron fangs along the knuckles
GAUNTLETS = [
    "................",
    "..f..f..f.......",
    "..F..F..F.......",
    "..FffFffF.......",
    "..oIIIIIIo......",
    ".oIiiIIiiIo.....",
    ".oIIIIIIIIo.....",
    ".oLLLLLLLLo.....",
    ".oLlLLLLlLLo....",
    "..oLLLLLLLLo....",
    "..oLLLLLLLLo....",
    "...oKKKKKKo.....",
    "...oKkKKkKo.....",
    "...oKKKKKKo.....",
    "....oooooo......",
    "................",
]
GAUNTLETS_PAL = {"o": (36, 30, 28), "f": (236, 236, 228), "F": (200, 200, 192), "I": (118, 120, 126), "i": (170, 172, 178),
                 "L": (122, 78, 44), "l": (160, 108, 66), "K": (90, 58, 34), "k": (124, 84, 52)}

HIVE = [
    "................",
    "......oooo......",
    "....ooBBBBoo....",
    "...oBBbBBbBBo...",
    "..oBBBBBBBBBBo..",
    "..oBbBGGGGBbBo..",
    "..oBBGgoogGBBo..",
    "..oBBGoRRoGBBo..",
    "..oBbGoRRoGbBo..",
    "..oBBGgoogGBBo..",
    "..oBBBGGGGBBBo..",
    "...oBBbBBbBBo...",
    "....ooBBBBoo....",
    ".....ooTTooo....",
    "......oTTo......",
    "................",
]
HIVE_PAL = {"o": (44, 30, 14), "B": (176, 118, 52), "b": (210, 156, 78), "G": (96, 92, 88), "g": (146, 142, 136),
            "R": (200, 60, 40), "T": (120, 78, 36)}

HORNET = [
    "................",
    "................",
    "....ww....ww....",
    "...wWWw..wWWw...",
    "...wWWWwwWWWw...",
    "....wWWooWWw....",
    ".....oAAAAo.....",
    "....oAkAAkAo....",
    "....oAAAAAAo.e..",
    ".....okAAko.ee..",
    ".....oAAAAo.e...",
    "......oAAo......",
    ".......oo.......",
    "......s..s......",
    "................",
    "................",
]
HORNET_PAL = {"o": (40, 28, 12), "A": (216, 168, 56), "k": (56, 40, 20), "w": (120, 140, 150), "W": (198, 224, 232), "e": (210, 60, 40),
              "s": (90, 90, 96)}


def draw(rows, palette, path):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        assert len(row) == 16, (path, y, len(row))
        for x, c in enumerate(row):
            if c != ".":
                img.putpixel((x, y), palette[c] + (255,))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    return img


def as_stone(img):
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            r, g, b, a = img.getpixel((x, y))
            if a > 0:
                v = int(40 + (0.3 * r + 0.59 * g + 0.11 * b) / 255.0 * 110)
                out.putpixel((x, y), (v + 4, v + 2, v, a))
    return out


def main():
    draw(BOLAS, BOLAS_PAL, os.path.join(KINGDOMS, "bolas.png"))
    draw(GAUNTLETS, GAUNTLETS_PAL, os.path.join(POWERS, "armory", "fang_gauntlets.png"))
    # the forged head is drawn in tools/weapon_parts_assets.py (DRAWN); its textures are written here for the one part
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import weapon_parts_assets
    head = os.path.join(POWERS, "tinkering", "weapon_part")
    os.makedirs(head, exist_ok=True)
    weapon_parts_assets.drawn("fang_blades", "iron").save(os.path.join(head, "fang_blades_iron.png"))
    as_stone(weapon_parts_assets.drawn("fang_blades", "iron")).save(os.path.join(head, "fang_blades_stone.png"))
    draw(HIVE, HIVE_PAL, os.path.join(POWERS, "alchemy", "clockwork_hornet_hive.png"))
    draw(HORNET, HORNET_PAL, os.path.join(POWERS, "alchemy", "clockwork_hornet.png"))


if __name__ == "__main__":
    main()
