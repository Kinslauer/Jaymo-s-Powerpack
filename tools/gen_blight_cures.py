"""Draws the Hollow's blight cures' item icons (Andy's picks, 2026-11-15), our own art in the vanilla item style:
kingdoms/src/main/resources/assets/jaymoskingdoms/textures/item/{seared_flesh,gilded_flesh,hollows_cure}.png.
Run it from the repository root:

    python3 -I tools/gen_blight_cures.py

- seared_flesh: a torn strip of flesh cooked grey-brown, charred dark at the edges, a pale fat streak.
- gilded_flesh: the same strip worked through with streaks and flecks of gold leaf.
- hollows_cure: a round-bellied bottle with a cork, the draught a cloudy pale gold-green, a highlight on the glass.
Every pixel is placed here from fixed shapes and palettes (no vanilla texture is read or copied).
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms", "textures", "item")

# the strip of flesh: rows of (start, end) columns, top to bottom, a ragged diagonal slab
FLESH = [None, None, (9, 12), (7, 13), (5, 13), (4, 13), (3, 12), (3, 12), (2, 11), (2, 10), (2, 9), (3, 8), (3, 7), (4, 6), None, None]
SEARED = {"edge": (52, 38, 32), "dark": (84, 62, 50), "mid": (112, 86, 68), "light": (138, 110, 88), "fat": (186, 168, 140)}
GOLD = {"dark": (176, 122, 30), "mid": (232, 182, 54), "light": (255, 236, 130)}


def flesh(img, palette):
    px = img.load()
    for y, span in enumerate(FLESH):
        if span is None:
            continue
        a, b = span
        for x in range(a, b + 1):
            edge = x in (a, b) or FLESH[y - 1] is None or not (FLESH[y - 1][0] <= x <= FLESH[y - 1][1]) \
                or y + 1 >= len(FLESH) or FLESH[y + 1] is None or not (FLESH[y + 1][0] <= x <= FLESH[y + 1][1])
            if edge:
                c = palette["edge"]
            else:
                # light toward the top-left, darker toward the bottom-right, a little mottled
                shade = (x - y) % 5
                c = palette["light"] if x + y < 15 and shade < 3 else palette["mid"] if shade < 4 else palette["dark"]
            px[x, y] = c + (255,)
    # a pale streak of fat along the strip
    for x, y in ((10, 3), (9, 4), (8, 5), (7, 6), (6, 7), (5, 8)):
        px[x, y] = palette["fat"] + (255,)


def gilded(img):
    px = img.load()
    streaks = [((11, 4), "mid"), ((10, 5), "light"), ((9, 6), "mid"), ((6, 9), "mid"), ((5, 10), "light"), ((4, 11), "dark"),
               ((12, 6), "mid"), ((3, 8), "dark"), ((8, 10), "mid"), ((9, 9), "light")]
    for (x, y), tone in streaks:
        px[x, y] = GOLD[tone] + (255,)


def cure(img):
    px = img.load()
    glass, glass_dark, shine = (196, 214, 222), (120, 140, 150), (240, 250, 255)
    brew, brew_dark, brew_light = (172, 186, 96), (120, 136, 58), (214, 222, 150)
    cork, cork_dark = (150, 104, 62), (102, 68, 40)
    # the cork and the neck
    for x in (7, 8):
        px[x, 1] = cork + (255,)
        px[x, 2] = cork_dark + (255,)
    for y in (3, 4):
        px[6, y] = glass_dark + (255,)
        px[9, y] = glass_dark + (255,)
        px[7, y] = glass + (255,)
        px[8, y] = glass + (255,)
    # the round belly: a disc of radius 5 round (7.5, 10)
    for y in range(5, 16):
        for x in range(2, 14):
            d = ((x - 7.5) ** 2 + (y - 10.0) ** 2) ** 0.5
            if d <= 5.0:
                if d > 4.2:
                    c = glass_dark
                elif y <= 6:
                    c = glass
                else:
                    c = brew_light if x < 6 and y < 10 else brew if (x + y) % 4 else brew_dark
                px[x, y] = c + (255,)
    # a highlight on the glass
    for x, y in ((4, 8), (4, 9), (5, 7)):
        px[x, y] = shine + (255,)


def save(img, name):
    os.makedirs(OUT, exist_ok=True)
    img.save(os.path.join(OUT, name + ".png"))


def main():
    seared = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    flesh(seared, SEARED)
    save(seared, "seared_flesh")
    gild = seared.copy()
    gilded(gild)
    save(gild, "gilded_flesh")
    bottle = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    cure(bottle)
    save(bottle, "hollows_cure")
    print("wrote 3 icons to", OUT)


if __name__ == "__main__":
    main()
