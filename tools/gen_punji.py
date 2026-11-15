"""Jaymo's Kingdoms: the punji stakes' texture (creatures/PunjiBlock, GTNH patch 6), drawn for the game's cross model:
sticks set upright at different heights, bark on the shafts, whittled pale toward the points and fire-hardened dark at
the tips. Our own pixels, no game art. Run: python3 tools/gen_punji.py"""
import random
from PIL import Image

OUT = "kingdoms/src/main/resources/assets/jaymoskingdoms/textures/block/punji_stakes.png"

BARK = [(96, 70, 42), (110, 80, 48), (84, 60, 36)]
WOOD = [(196, 160, 108), (182, 146, 96), (208, 176, 124)]
TIP = (58, 40, 26)
# (x of the stake's left column, height in pixels from the bottom, width)
STAKES = [(1, 9, 2), (4, 13, 2), (7, 10, 2), (10, 14, 2), (13, 8, 2)]


def main():
    rnd = random.Random(6)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x0, height, width in STAKES:
        top = 16 - height
        for y in range(top, 16):
            from_tip = y - top
            for dx in range(width):
                # the point narrows to one pixel at the very top
                if from_tip == 0 and dx == 1:
                    continue
                if from_tip == 0:
                    c = TIP
                elif from_tip == 1:
                    c = TIP if dx == 0 else rnd.choice(WOOD)
                elif from_tip <= 4:
                    c = rnd.choice(WOOD)
                else:
                    c = rnd.choice(BARK)
                img.putpixel((x0 + dx, y), c + (255,))
    img.save(OUT)


if __name__ == "__main__":
    main()
