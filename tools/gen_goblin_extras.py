#!/usr/bin/env python3
"""Goblin extras art (Kingdoms): the decoy, the engineer's hammer and the mushroom ale (16x16 item textures, with their
item definitions and models), and the goblins' robot's skins (the clockwork golem's, patched green and rusty).

    python3 tools/gen_goblin_extras.py

Writes under kingdoms/src/main/resources/assets/jaymoskingdoms/. Everything is original pixel work or a recolour of the
mod's own clockwork golem skins (textures/entity/bb/clockwork_golem*.png); needs Pillow.
"""
import json
import random
from pathlib import Path

from PIL import Image

ASSETS = Path(__file__).resolve().parent.parent / "kingdoms/src/main/resources/assets/jaymoskingdoms"

PALETTE = {
    ".": (0, 0, 0, 0),
    "o": (60, 40, 25, 255),      # outline
    "S": (176, 146, 96, 255),    # sack
    "s": (140, 112, 70, 255),    # sack shade
    "h": (214, 190, 90, 255),    # hay
    "t": (110, 80, 40, 255),     # the tie
    "g": (85, 96, 46, 255),      # goblin rag, green
    "G": (60, 70, 30, 255),      # rag, deep
    "k": (150, 105, 60, 255),    # cork
    "w": (200, 225, 235, 255),   # glass, bright
    "l": (150, 185, 200, 255),   # glass
    "a": (176, 104, 34, 255),    # ale
    "A": (214, 140, 52, 255),    # ale, bright
    "f": (240, 228, 190, 255),   # foam
    "r": (190, 50, 40, 255),     # red cap
    "p": (245, 240, 230, 255),   # spot
}

DECOY = [
    "................",
    "......h.hh......",
    ".....hhhhhh.....",
    "......ottto.....",
    ".....oSSSSSo....",
    "....oSSSSSSSo...",
    "...oSSSsSSSSSo..",
    "...oSSSSSSsSSo..",
    "...oSSSSSSSSSo..",
    "...oSgSSSSSgSo..",
    "...oSSgGSSSgSo..",
    "....oSSGGSSSo...",
    "....oSsSSSsSo...",
    ".....oSSSSSo....",
    "......ooooo.....",
    "................",
]

ALE = [
    "................",
    "......kkkk......",
    "......kkkk......",
    ".......ll.......",
    ".......lw.......",
    "......llwl......",
    ".....lffffl.....",
    "....laAAAAal....",
    "....laAaaAal....",
    "....lwarrral....",
    "....lwaprpal....",
    "....lwaaaaal....",
    "....laaaaaal....",
    ".....llllll.....",
    "................",
    "................",
]


def grid(rows):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), "a 16x16 grid"
    im = Image.new("RGBA", (16, 16))
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            im.putpixel((x, y), PALETTE[c])
    return im


def hammer():
    """A handle running up to the right, a patched iron head with a green rag round its neck."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    wood, wood_dark, outline = (130, 90, 52, 255), (96, 64, 36, 255), (45, 32, 22, 255)
    for i in range(10):
        x, y = 2 + i, 13 - i
        im.putpixel((x, y), wood)
        if x + 1 < 16:
            im.putpixel((x + 1, y), wood_dark)
        if y + 1 < 16 and i > 0:
            im.putpixel((x, y + 1), outline)
    iron, light, rust = (140, 145, 150, 255), (190, 195, 200, 255), (150, 80, 36, 255)
    for x in range(8, 14):
        for y in range(1, 6):
            edge = x in (8, 13) or y in (1, 5)
            im.putpixel((x, y), outline if edge else iron)
    for x in range(9, 12):
        im.putpixel((x, 2), light)
    im.putpixel((12, 4), rust)
    im.putpixel((10, 4), rust)
    im.putpixel((9, 3), rust)
    rag, rag_dark = PALETTE["g"], PALETTE["G"]
    for x, y, c in [(7, 7, rag), (8, 6, rag_dark), (6, 8, rag_dark), (7, 8, rag)]:
        im.putpixel((x, y), c)
    return im


def robot_skins():
    """The clockwork golem's skins, patched green and rusty: the same on both, so the patches match when its lights go out."""
    for name in ("clockwork_golem", "clockwork_golem_1"):
        src = Image.open(ASSETS / f"textures/entity/bb/{name}.png").convert("RGBA")
        out = src.copy()
        px = out.load()
        for y in range(out.height):
            for x in range(out.width):
                r, g, b, a = px[x, y]
                if a:
                    px[x, y] = (min(255, int(0.2 * r + 0.6 * g + 0.1 * b)), min(255, int(0.45 * r + 0.55 * g + 0.1 * b)),
                                min(255, int(0.3 * b + 0.25 * g)), a)
        rng = random.Random(0x60B11)
        patches = [(107, 90, 58), (150, 70, 30), (110, 115, 110), (78, 96, 52)]
        opaque = [(x, y) for y in range(src.height) for x in range(src.width) if src.getpixel((x, y))[3]]
        for _ in range(140):
            cx, cy = rng.choice(opaque)
            colour = rng.choice(patches)
            for dx in range(rng.randint(1, 3)):
                for dy in range(rng.randint(1, 2)):
                    x, y = cx + dx, cy + dy
                    if 0 <= x < out.width and 0 <= y < out.height and px[x, y][3]:
                        px[x, y] = colour + (255,)
        out.save(ASSETS / f"textures/entity/bb/{name.replace('clockwork_golem', 'clockwork_golem_goblin', 1)}.png")


def item(name, texture, parent="minecraft:item/generated"):
    texture.save(ASSETS / f"textures/item/{name}.png")
    (ASSETS / f"items/{name}.json").write_text(json.dumps({"model": {"type": "minecraft:model", "model": f"jaymoskingdoms:item/{name}"}}, indent=2) + "\n")
    (ASSETS / f"models/item/{name}.json").write_text(json.dumps({"parent": parent, "textures": {"layer0": f"jaymoskingdoms:item/{name}"}}, indent=2) + "\n")


if __name__ == "__main__":
    item("goblin_decoy", grid(DECOY))
    item("engineers_hammer", hammer(), "minecraft:item/handheld")
    item("mushroom_ale", grid(ALE))
    robot_skins()
