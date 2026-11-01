"""(Kingdoms 0.7.443, Havens patch 7b) The more monstrous looks' textures, our own pixels:

- textures/entity/heritage/stoneborn_cracks.png: a 64x64 player-skin overlay of dark cracks over the face, the neck and
  the chest (drawn over a Stoneborn's marks).
- textures/entity/heritage/stoneborn_brow.png: the heavy stone brow ridge (a 32x32 sheet, box at 0,0, 8x2x2).
- textures/entity/heritage/warrior_tail.png: a Warrior-born villager's tail segment (a 32x32 sheet, box at 0,0,
  3x3x4), brown fur.

Run: python3 kingdoms/tools/gen_monstrous_looks.py
"""
import os
import random
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "jaymoskingdoms", "textures", "entity", "heritage")


def cracks():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    rnd = random.Random(7443)
    dark, edge = (40, 36, 44, 235), (120, 112, 120, 160)

    def crack(x, y, steps, box):
        x0, y0, x1, y1 = box
        for _ in range(steps):
            if x0 <= x < x1 and y0 <= y < y1:
                img.putpixel((x, y), dark)
                if x + 1 < x1 and rnd.random() < 0.35:
                    img.putpixel((x + 1, y), edge)
            x += rnd.choice((-1, 0, 0, 1))
            y += 1 if rnd.random() < 0.7 else 0
    # face (head front 8..16 x 8..16): a crack down from the brow past one eye, one up the jaw
    crack(10, 8, 7, (8, 8, 16, 16))
    crack(14, 13, 4, (8, 8, 16, 16))
    # head sides and back
    crack(3, 9, 6, (0, 8, 8, 16))
    crack(28, 8, 7, (24, 8, 32, 16))
    # neck and chest (body front 20..28 x 20..32)
    crack(22, 20, 8, (20, 20, 28, 32))
    crack(26, 22, 6, (20, 20, 28, 32))
    img.save(os.path.join(OUT, "stoneborn_cracks.png"))


def brow():
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    rnd = random.Random(443)
    for x in range(20):
        for y in range(4):
            g = 104 + rnd.randint(-14, 14)
            img.putpixel((x, y), (g, g - 2, g + 6, 255))
    for x in range(2, 10):
        img.putpixel((x, 2), (60, 56, 66, 255))  # a crack along the front
    img.save(os.path.join(OUT, "stoneborn_brow.png"))


def tail():
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    rnd = random.Random(442)
    for x in range(14):
        for y in range(7):
            b = rnd.randint(-12, 12)
            img.putpixel((x, y), (112 + b, 72 + b, 40 + b // 2, 255))
    img.save(os.path.join(OUT, "warrior_tail.png"))


if __name__ == "__main__":
    cracks()
    brow()
    tail()
