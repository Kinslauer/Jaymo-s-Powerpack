"""Draws the open-cut texture worn on a bleeding body part (client/wounds/DressingsLayer). Our own pixel art.

It is laid over the same boxes a bandage uses, so it only needs to read on any face of any box: short red gashes
scattered over a clear sheet.

    python -I tools/gen_cut_marks.py
"""
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "jaymospowers", "textures", "entity", "wounds", "cut.png")

DEEP = (92, 8, 12, 255)
RED = (158, 20, 22, 255)
WET = (198, 44, 40, 255)


def main():
    rng = random.Random(1341)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()
    # one gash to a cell of a loose grid, so every face of every box carries one or two
    for cy in range(0, 64, 8):
        for cx in range(0, 64, 9):
            x = cx + rng.randint(0, 2)
            y = cy + rng.randint(0, 2)
            length = rng.randint(4, 6)
            rise = rng.choice((-1, 1))
            for i in range(length):
                gx = (x + i) % 64
                gy = (y + (i * rise) // 2) % 64
                px[gx, gy] = DEEP if 0 < i < length - 1 else RED
                if 0 < i < length - 1:
                    px[gx, (gy + 1) % 64] = RED
            # a bead of blood under the low end
            if rng.random() < 0.35:
                bx = (x + (0 if rise > 0 else length - 1)) % 64
                by = (y + 2 + (0 if rise > 0 else -(length // 2))) % 64
                px[bx, by] = WET
    img.save(OUT)


if __name__ == "__main__":
    main()
