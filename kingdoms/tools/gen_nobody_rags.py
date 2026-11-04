"""Draws the Nobodies' plain clothes (Uniforms.POOR): a roughspun tunic in an undyed or cheaply dyed wool, a rope or
leather belt, dark breeches and shoes, no hat. Andy (2026-11-04): the three hired Nobodies of the Nobodies start wore
the pirate deckhands' striped shirts and bandanas, which "arent medieval attire".

Each one is a deckhand's slops (skins/clothing/crew/deckhand_<n>.png, our own art from tools/gen_uniforms.py) with the
striped shirt woven over in one plain cloth, the sash turned to a belt, and the bandana taken off; breeches and shoes are
kept. Original art. Run from the kingdoms project root: python -I tools/gen_nobody_rags.py"""
from PIL import Image
import random

SRC = "src/main/resources/assets/jaymoskingdoms/skins/clothing/crew/deckhand_{}.png"
OUT = "src/main/resources/assets/jaymoskingdoms/skins/clothing/poor/rags_{}.png"

# undyed linen, dun, grey wool, faded russet, dull weld green, faded woad
CLOTHS = [(196, 182, 150), (134, 104, 74), (128, 126, 118), (138, 78, 56), (98, 106, 66), (86, 98, 112)]
BREECHES = (58, 52, 44)
BELTS = [(92, 66, 42), (70, 50, 34), (150, 128, 88), (64, 46, 30), (110, 84, 52), (78, 58, 40)]


def shirt(x, y):
    """The shirt's pixels in the humanoid layout (body front/back/sides and both arms; not the trousers' faces)."""
    body = 16 <= x < 40 and 20 <= y < 28 or 20 <= x < 28 and 16 <= y < 20
    right_arm = 40 <= x < 56 and 20 <= y < 28 or 44 <= x < 48 and 16 <= y < 20
    left_arm = 32 <= x < 48 and 52 <= y < 60 or 36 <= x < 40 and 48 <= y < 52
    return body or right_arm or left_arm


def shade(c, d):
    return tuple(max(0, min(255, v + d)) for v in c) + (255,)


def draw(n):
    src = Image.open(SRC.format(n)).convert("RGBA")
    out = src.copy()
    rnd = random.Random(4100 + n)
    cloth, belt = CLOTHS[n], BELTS[n]
    for y in range(64):
        for x in range(64):
            p = src.getpixel((x, y))
            if y < 16:
                # the bandana off: the villager's own hair shows
                out.putpixel((x, y), (0, 0, 0, 0))
            elif p[3] == 0:
                continue
            elif shirt(x, y):
                # roughspun: a little uneven, darker in a few threads
                out.putpixel((x, y), shade(cloth, rnd.randint(-7, 5) - (10 if rnd.random() < 0.08 else 0)))
            elif 16 <= x < 40 and y == 28:
                # the sash's row: a belt, its buckle kept as dull iron
                buckle = p[0] > 150 and p[1] > 110 and p[2] < 100
                out.putpixel((x, y), (104, 100, 92, 255) if buckle else shade(belt, rnd.randint(-5, 4)))
            elif (40 <= x < 56 and y == 28) or (32 <= x < 48 and y == 60):
                # the sleeves' hems
                out.putpixel((x, y), shade(cloth, -18 + rnd.randint(-4, 3)))
            elif 16 <= x < 40 and y == 29:
                # the tunic's hem, over the breeches
                out.putpixel((x, y), shade(cloth, -14 + rnd.randint(-4, 3)))
            elif 16 <= x < 40 and 30 <= y < 32 or 28 <= x < 36 and 16 <= y < 20:
                # the breeches' waist and the body's underside (the slops' coloured waistband)
                out.putpixel((x, y), shade(BREECHES, rnd.randint(-5, 4)))
    out.save(OUT.format(n))


if __name__ == "__main__":
    import os
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    for i in range(len(CLOTHS)):
        draw(i)
