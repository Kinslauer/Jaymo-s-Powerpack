"""(0.15.90) The bandages and splints worn on the body (client/wounds/DressingsLayer).

Painted as pixel grids in the item icons' own colours: linen_bandage.png's linen, rag.png's rag, and vanilla's stick
browns for the splint's slats (as splint.png). A wrap is turns of cloth: each turn a lit upper edge, the cloth, and the
shadow where the next turn overlaps it, stepping down as it goes round so the turns slant. The sheets are tiled, so
every box of the layer's model reads as wrapped cloth whatever its size.

Run from the repository root: python -I tools/vanilla_style/dressings.py
"""
import random
import sys
from pathlib import Path

from PIL import Image

OUT = Path("src/main/resources/assets/jaymospowers/textures/entity/wounds")

LINEN = {"lit": (0xF7, 0xF3, 0xE8), "cloth": (0xE6, 0xDF, 0xCC), "mid": (0xD8, 0xCF, 0xB8), "shade": (0xC9, 0xBF, 0xA6),
         "fold": (0xA8, 0x9C, 0x80)}
RAG = {"lit": (0xA8, 0x97, 0x7F), "cloth": (0x8C, 0x7B, 0x66), "mid": (0x7D, 0x6D, 0x5A), "shade": (0x6E, 0x60, 0x50),
       "fold": (0x4E, 0x43, 0x37)}
# vanilla's stick, light to dark
WOOD = [(0x89, 0x67, 0x27), (0x68, 0x4E, 0x1E), (0x49, 0x36, 0x15), (0x28, 0x1E, 0x0B)]


def cloth(px, x0, y0, w, h, pal, rng, stain=0.0):
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            # each turn three pixels tall, stepping down one every six across
            turn = (y + x // 6) % 3
            c = pal["lit"] if turn == 0 else pal["cloth"] if turn == 1 else pal["shade"]
            r = rng.random()
            if turn == 1 and r < 0.18:
                c = pal["mid"]  # the weave
            elif turn == 2 and r < 0.12:
                c = pal["fold"]  # a deeper crease where the turns overlap
            if stain and rng.random() < stain:
                c = pal["fold"]
            px[x, y] = c + (255,)


def wraps(name, pal, seed, stain=0.0):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    cloth(img.load(), 0, 0, 64, 44, pal, random.Random(seed), stain)
    img.save(OUT / name)


def splint():
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = img.load()
    rng = random.Random(7)
    # the slats (columns 0-7): long grain down the wood, lit on the left, a knot here and there
    for x in range(8):
        for y in range(12):
            edge = x % 4
            c = WOOD[0] if edge == 0 else WOOD[2] if edge == 3 else WOOD[1]
            if edge in (1, 2) and rng.random() < 0.15:
                c = WOOD[2]
            if (x, y) in ((1, 4), (5, 8)):
                c = WOOD[3]
            px[x, y] = c + (255,)
    # the ties (columns 8-31): linen cord, tight turns
    for x in range(8, 32):
        for y in range(12):
            turn = (y + x // 2) % 2
            c = LINEN["cloth"] if turn == 0 else LINEN["shade"]
            if y % 5 == 0:
                c = LINEN["lit"]
            px[x, y] = c + (255,)
    img.save(OUT / "splint.png")


if __name__ == "__main__":
    if len(sys.argv) > 1:
        OUT = Path(sys.argv[1])
    OUT.mkdir(parents=True, exist_ok=True)
    wraps("linen_wrap.png", LINEN, 3)
    wraps("rag_wrap.png", RAG, 5, stain=0.04)
    splint()
