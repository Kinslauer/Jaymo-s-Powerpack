"""Draws the three End dishes made after Ender's Delight (its art is all rights reserved, so these are our own).

Hand-drawn 16x16 grids like tools/gen_assets.py (whose palette is full), with their own palette.
Run from kitchen/:  python tools/gen_end_dishes.py
"""
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "jaymoskitchen" / "textures" / "item"

PALETTE = {
    ".": (0, 0, 0, 0),
    "k": (40, 24, 12, 255),     # outline
    "b": (139, 90, 43, 255),    # bowl wood
    "B": (101, 63, 30, 255),    # bowl shadow
    "h": (176, 122, 68, 255),   # bowl highlight
    "s": (92, 64, 110, 255),    # End broth
    "S": (66, 44, 84, 255),     # broth shadow
    "c": (120, 214, 206, 255),  # celestial chanterelle
    "C": (70, 160, 160, 255),   # celestial shadow
    "m": (236, 120, 60, 255),   # murublight chanterelle
    "M": (180, 74, 40, 255),    # murublight shadow
    "y": (226, 205, 120, 255),  # potato
    "j": (214, 170, 236, 255),  # drift jelly
    "J": (168, 120, 200, 255),  # jelly shadow
    "w": (250, 240, 255, 255),  # shine
    "d": (226, 170, 80, 255),   # choux pastry
    "D": (186, 124, 50, 255),   # pastry shadow
    "p": (150, 70, 170, 255),   # puruberry icing
    "P": (106, 44, 128, 255),   # icing shadow
}

SPRITES = {
    "chanterelle_stew": [
        "................",
        "................",
        "................",
        "................",
        "....kkkkkkkk....",
        "..kksscsmsySkk..",
        ".kscCsmMsscCysk.",
        ".kSsysmssCcsSsSk",
        ".khbbbbbbbbbbbBk",
        "..khbbbbbbbbbBk.",
        "..khbbbbbbbbbBk.",
        "...khbbbbbbbBk..",
        "....kBBbbbbBk...",
        ".....kkkkkkk....",
        "................",
        "................",
    ],
    "drift_jelly_pudding": [
        "................",
        "................",
        "......kkkk......",
        ".....kjwjjk.....",
        "....kjwjjjJk....",
        "....kjjjjjJk....",
        "..kkjjjjjJJJkk..",
        ".kJjjjjjjjjJJJk.",
        ".kJJJJJJJJJJJJk.",
        ".khbbbbbbbbbbBk.",
        "..khbbbbbbbbBk..",
        "..khbbbbbbbbBk..",
        "...khbbbbbbBk...",
        "....kBBbbbBk....",
        ".....kkkkkk.....",
        "................",
    ],
    "strange_eclair": [
        "................",
        "................",
        "................",
        "...........kkk..",
        "..........kppPk.",
        ".........kpwpPk.",
        "........kpppPDk.",
        ".......kpppPdDk.",
        "......kpwpPdDk..",
        ".....kpppPdDk...",
        "....kpppPdDk....",
        "...kpwpPdDk.....",
        "..kpppPdDk......",
        "..kPPPdDk.......",
        "..kDDDDk........",
        "...kkkk.........",
    ],
}


def render(rows):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), "sprites are 16x16"
    img = Image.new("RGBA", (16, 16))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            img.putpixel((x, y), PALETTE[ch])
    return img


if __name__ == "__main__":
    for name, rows in SPRITES.items():
        render(rows).save(OUT / f"{name}.png")
    print(f"wrote {len(SPRITES)} End dishes to {OUT}")
