"""Generates the Super 4 primal fur layer (64x64 player skin layout).

Fur covers the back, sides, shoulders and arms; the chest, head, hands and legs stay bare.
Run: python3 tools/gen_primal_fur.py
"""
import random
from PIL import Image

OUT = "src/main/resources/assets/jaymospowers/textures/entity/zfighter_primal_fur.png"
BASE, DARK, LIGHT = (0xA0, 0x18, 0x18), (0x6A, 0x0E, 0x0E), (0xC8, 0x30, 0x30)

rng = random.Random(4)
img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))


def fur(x, y):
    # vertical strands: each column leans dark or light, with noise
    r = rng.random() + (0.25 if (x * 7 + y // 3) % 5 == 0 else 0) - (0.2 if x % 3 == 0 else 0)
    c = DARK if r < 0.3 else LIGHT if r > 0.85 else BASE
    img.putpixel((x, y), c + (255,))


def rect(x0, y0, x1, y1):
    for x in range(x0, x1):
        for y in range(y0, y1):
            fur(x, y)


# body: top, right side, back, left side
rect(20, 16, 28, 20)
rect(16, 20, 20, 30)
rect(28, 20, 32, 30)
rect(32, 20, 40, 30)
# body front: shoulder rows and side columns only, chest bare
rect(20, 20, 28, 22)
rect(20, 22, 21, 30)
rect(27, 22, 28, 30)

# arms (right 40..56,16..32 / left 32..48,48..64): top + side faces, hands bare
for ax, ay in ((40, 16), (32, 48)):
    rect(ax + 4, ay, ax + 8, ay + 4)          # top face
    rect(ax, ay + 4, ax + 16, ay + 13)        # four sides, bottom 3 rows bare

img.save(OUT)
print("wrote", OUT)
