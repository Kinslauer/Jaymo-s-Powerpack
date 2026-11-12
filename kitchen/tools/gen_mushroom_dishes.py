"""Draws the balloon mushroom dish icons (16x16), our own pixels."""
from pathlib import Path
from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/jaymoskitchen/textures/item"
K = (40, 24, 12, 255)
BOWL = (139, 90, 43, 255)
BOWL_HI = (176, 122, 68, 255)
BROTH = (214, 120, 170, 255)
BROTH_HI = (240, 170, 205, 255)
CAP = (226, 82, 160, 255)
CAP_HI = (250, 150, 205, 255)
STEM = (240, 228, 205, 255)
STICK = (150, 105, 60, 255)
EGG_Y = (246, 206, 72, 255)
EGG_W = (250, 244, 226, 255)
EGG_EDGE = (214, 168, 70, 255)
MEAT = (120, 62, 40, 255)
CRUST = (196, 120, 70, 255)


def put(img, pts, c):
    for x, y in pts:
        img.putpixel((x, y), c)


def soup():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(7, 13):
        inset = 0 if y < 11 else (y - 10)
        for x in range(1 + inset, 15 - inset):
            img.putpixel((x, y), BOWL)
    for x in range(1, 15):
        img.putpixel((x, 7), BOWL_HI)
    for x in range(3, 13):
        img.putpixel((x, 8), BROTH)
    put(img, [(5, 8), (6, 8), (9, 8)], BROTH_HI)
    for x in range(5, 11):
        img.putpixel((x, 6), CAP)
    for x in range(6, 10):
        img.putpixel((x, 5), CAP)
    put(img, [(7, 5), (6, 6)], CAP_HI)
    for x in range(2, 14):
        img.putpixel((x, 13), K)
    for y in range(8, 13):
        img.putpixel((0, y), K)
        img.putpixel((15, y), K)
    return img


def skewer():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for i in range(14):
        img.putpixel((1 + i, 14 - i), STICK)
    for cx, cy in ((10, 5), (6, 9)):
        for dx in range(-2, 3):
            img.putpixel((cx + dx, cy), CAP)
        for dx in range(-1, 2):
            img.putpixel((cx + dx, cy - 1), CAP)
        img.putpixel((cx - 1, cy - 1), CAP_HI)
        img.putpixel((cx, cy + 1), STEM)
    return img


def omelette():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    # a folded half-moon of egg with pink caps peeping out of the fold
    for y in range(6, 13):
        for x in range(2, 14):
            dx, dy = (x - 7.5) / 6.0, (y - 6) / 6.5
            if dx * dx + dy * dy <= 1.0:
                img.putpixel((x, y), EGG_Y)
    for x in range(2, 14):
        img.putpixel((x, 6), EGG_EDGE)
    put(img, [(4, 9), (5, 10), (9, 8), (10, 10), (7, 11)], EGG_W)
    for cx in (5, 9):
        put(img, [(cx, 5), (cx + 1, 5), (cx + 2, 5), (cx + 1, 4)], CAP)
        img.putpixel((cx + 1, 5), CAP_HI)
    for x in range(3, 13):
        img.putpixel((x, 13), K)
    return img


def stuffed():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    # three upturned caps, each filled with browned meat, on a dark tray line
    for cx, cy in ((4, 10), (11, 10), (7, 6)):
        for dx in range(-3, 4):
            img.putpixel((cx + dx, cy), CAP)
        for dx in range(-2, 3):
            img.putpixel((cx + dx, cy + 1), CAP)
        img.putpixel((cx - 3, cy), CAP_HI)
        for dx in range(-2, 3):
            img.putpixel((cx + dx, cy - 1), MEAT)
        put(img, [(cx - 1, cy - 2), (cx, cy - 2), (cx + 1, cy - 2)], CRUST)
    for x in range(1, 15):
        img.putpixel((x, 13), K)
    return img


soup().save(OUT / "balloon_mushroom_soup.png")
omelette().save(OUT / "balloon_mushroom_omelette.png")
stuffed().save(OUT / "stuffed_balloon_mushrooms.png")
skewer().save(OUT / "balloon_mushroom_skewer.png")
