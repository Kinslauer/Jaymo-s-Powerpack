"""Draws the sky folk's clothes (Uniforms.SKY_DIVINE, SKY_HALFWAY, SKY_LORDS, SKY_WARDENS), worn by the villagers of the Aether's
villages and the sky castle.

- sky/divine_<n>: the Divine-born's long robe, cloud white to the ankle, a gold cord at the waist, gold at collar and hems.
- sky/halfway_<n>: the halfway folk's tunic in a colour of the sunrise, a white sash, a small sun on the chest; breeches kept.
- sky/lord_<n>: the robe with a deep blue mantle over the shoulders and a wide gold belt.
- sky/warden_<n>: the castle's wardens: a deep blue gambeson under a white tabard with the sun on it, a gold belt.

Each one is a plain tunic of ours (skins/clothing/poor/rags_<n>.png, tools/gen_nobody_rags.py) woven over. Original art.
Run from the kingdoms project root: python -I tools/gen_sky_folk.py"""
from PIL import Image
import json
import random

SRC = "src/main/resources/assets/jaymoskingdoms/skins/clothing/poor/rags_{}.png"
OUT = "src/main/resources/assets/jaymoskingdoms/skins/clothing/sky/{}_{}.png"
DATA = "src/main/resources/data/jaymoskingdoms/skins/clothing/neutral.json"
COUNT = 4

# cloud white, warm white, pale dawn grey, cream
WHITES = [(238, 238, 232), (240, 232, 214), (222, 226, 232), (234, 226, 200)]
GOLD = (214, 172, 72)
# saffron, amber, dawn rose, pale gold
SUNS = [(222, 150, 54), (204, 122, 48), (214, 132, 120), (226, 196, 110)]
SASH = (236, 232, 222)
MANTLES = [(44, 62, 122), (58, 52, 116), (36, 78, 110), (70, 44, 96)]
SANDAL = (150, 118, 70)
# the sun on a chest: (row from the shoulder, column)
SUN = ((2, 23), (2, 24), (3, 22), (3, 23), (3, 24), (3, 25), (4, 23), (4, 24))


def body(x, y):
    return 16 <= x < 40 and 20 <= y < 32


def arms(x, y):
    return 40 <= x < 56 and 20 <= y < 32 or 32 <= x < 48 and 52 <= y < 64


def tops(x, y):
    """The shoulders' and the body's top faces."""
    return 20 <= x < 28 and 16 <= y < 20 or 44 <= x < 48 and 16 <= y < 20 or 36 <= x < 40 and 48 <= y < 52


def legs(x, y):
    return 0 <= x < 16 and 20 <= y < 32 or 16 <= x < 32 and 52 <= y < 64


def row(y):
    """A part's row from its top (0) to its bottom (11), whichever half of the sheet it is on."""
    return y - 20 if y < 32 else y - 52


def shade(c, d):
    return tuple(max(0, min(255, v + d)) for v in c) + (255,)


def robe(n, lord):
    src = Image.open(SRC.format(n)).convert("RGBA")
    out = src.copy()
    rnd = random.Random(7300 + n + (50 if lord else 0))
    cloth, mantle = WHITES[n], MANTLES[n]
    for y in range(64):
        for x in range(64):
            p = src.getpixel((x, y))
            if p[3] == 0 and not legs(x, y):
                continue
            fold = -9 if x % 4 == 1 else 0
            if tops(x, y):
                out.putpixel((x, y), shade(mantle if lord else cloth, rnd.randint(-4, 4)))
            elif 4 <= x < 8 and 16 <= y < 20 or 20 <= x < 24 and 48 <= y < 52 or 28 <= x < 36 and 16 <= y < 20:
                # under the skirt
                out.putpixel((x, y), shade(cloth, -12 + rnd.randint(-4, 3)))
            elif body(x, y) or arms(x, y):
                r = row(y)
                if lord and r < 4:
                    # the mantle, its edge in gold
                    out.putpixel((x, y), shade(GOLD, rnd.randint(-8, 6)) if r == 3 else shade(mantle, rnd.randint(-5, 5)))
                elif r == 0 and body(x, y):
                    out.putpixel((x, y), shade(GOLD, rnd.randint(-8, 6)))
                elif body(x, y) and (r == 8 or lord and r == 9):
                    # the cord (a lord's belt is two rows)
                    out.putpixel((x, y), shade(GOLD, rnd.randint(-10, 4) - (14 if x % 3 == 0 else 0)))
                elif arms(x, y) and r == 8:
                    out.putpixel((x, y), shade(GOLD, rnd.randint(-8, 6)))
                elif arms(x, y) and r > 8:
                    # hands: left bare
                    out.putpixel((x, y), (0, 0, 0, 0))
                else:
                    out.putpixel((x, y), shade(cloth, fold + rnd.randint(-4, 3)))
            elif legs(x, y):
                r = row(y)
                if r < 9:
                    out.putpixel((x, y), shade(cloth, fold - 4 + rnd.randint(-4, 3)))
                elif r == 9:
                    out.putpixel((x, y), shade(GOLD, rnd.randint(-8, 6)))
                elif r == 10:
                    # bare ankle under the hem
                    out.putpixel((x, y), (0, 0, 0, 0))
                else:
                    out.putpixel((x, y), shade(SANDAL, rnd.randint(-8, 6)))
    out.save(OUT.format("lord" if lord else "divine", n))


def tabard(x):
    """The tabard's columns on the body's front and back."""
    return 21 <= x < 27 or 33 <= x < 39


def tunic(n, warden=False):
    src = Image.open(SRC.format(n)).convert("RGBA")
    out = src.copy()
    rnd = random.Random(7400 + n + (50 if warden else 0))
    cloth = MANTLES[n] if warden else SUNS[n]
    for y in range(64):
        for x in range(64):
            p = src.getpixel((x, y))
            if p[3] == 0:
                continue
            if tops(x, y):
                out.putpixel((x, y), shade(cloth, rnd.randint(-5, 4)))
            elif body(x, y) or arms(x, y):
                r = row(y)
                if body(x, y) and r == 8:
                    out.putpixel((x, y), shade(GOLD if warden else SASH, rnd.randint(-8, 3)))
                elif warden and body(x, y) and tabard(x) and r < 11 and not (20 <= x < 28 and (r, x) in SUN):
                    out.putpixel((x, y), shade(WHITES[n], (-9 if x % 3 == 0 else 0) + rnd.randint(-4, 3)))
                elif body(x, y) and 20 <= x < 28 and (r, x) in SUN:
                    # the sun they walked up after, on the chest
                    out.putpixel((x, y), shade(GOLD if warden else (250, 236, 170), rnd.randint(-6, 4)))
                elif body(x, y) and r > 9 or arms(x, y) and r > 8:
                    continue
                elif r == 9 or arms(x, y) and r == 8:
                    out.putpixel((x, y), shade(cloth, -22 + rnd.randint(-4, 3)))
                else:
                    out.putpixel((x, y), shade(cloth, rnd.randint(-7, 5)))
    out.save(OUT.format("warden" if warden else "halfway", n))


def register():
    """Lists them in MCA's clothing data as uniforms (only Uniforms hands them out)."""
    with open(DATA) as f:
        rows = json.load(f)
    have = {r["id"] for r in rows}
    for kind in ("divine", "halfway", "lord", "warden"):
        for n in range(COUNT):
            i = "jaymoskingdoms:skins/clothing/sky/{}_{}.png".format(kind, n)
            if i not in have:
                rows.append({"id": i, "profession": "jaymoskingdoms:uniform", "chance": 1.0})
    with open(DATA, "w") as f:
        json.dump(rows, f, indent=2)
        f.write("\n")


if __name__ == "__main__":
    for i in range(COUNT):
        robe(i, False)
        robe(i, True)
        tunic(i)
        tunic(i, True)
    register()
