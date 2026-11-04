"""Draws the icons of the war journal (an orc's), the goblin's ledger and the Lore Codex (16x16, vanilla's own shading).
Run from the kingdoms project root: python -I tools/gen_lore_books.py"""
from PIL import Image
import os

OUT = "src/main/resources/assets/jaymoskingdoms/textures/item/"

PAGE = [(176, 160, 126), (214, 200, 164)]


def book(ramp, mark, extra, name):
    """A book 11 wide, 14 tall, spine on the left, page block showing at the bottom and right; ramp is dark edge to light."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))

    def put(x, y, c):
        img.putpixel((x, y), tuple(c) + (255,))

    x0, y0, x1, y1 = 2, 1, 12, 13
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            edge = x in (x0, x1) or y in (y0, y1)
            put(x, y, ramp[0] if edge else ramp[2])
    for y in range(y0 + 1, y1):
        put(x0 + 1, y, ramp[1])
        put(x0 + 2, y, ramp[3] if y % 2 else ramp[2])
    for x in range(x0 + 1, x1):
        put(x, y0 + 1, ramp[3])
    for y in range(y0 + 1, y1):
        put(x1 + 1, y, PAGE[0])
    for x in range(x0 + 1, x1 + 1):
        put(x, y1 + 1, PAGE[1])
    put(x1 + 1, y1 + 1, PAGE[0])
    extra(put)
    for (x, y) in mark:
        put(x, y, mark[(x, y)])
    os.makedirs(OUT, exist_ok=True)
    img.save(OUT + name + ".png")
    print("wrote", name)


def orc(put):
    # torn corner, a bone clasp across the front, ragged page edge
    for (x, y) in ((12, 1), (11, 1), (12, 2)):
        img_clear(put, x, y)
    for y in (5, 6, 7):
        put(10, y, (226, 220, 196))
    put(10, 8, (190, 182, 156))
    # a red-brown hand scrawl: three crooked strokes
    for (x, y) in ((5, 4), (6, 5), (5, 6), (7, 8), (6, 9), (8, 10)):
        put(x, y, (150, 36, 28))
    put(5, 11, (150, 36, 28))
    put(6, 11, (110, 24, 20))


def img_clear(put, x, y):
    put(x, y, (0, 0, 0))   # replaced below by transparency


def goblin(put):
    # a gold coin on the cover, a yellow ribbon marker, a stitched edge
    for (x, y) in ((6, 5), (7, 5), (5, 6), (8, 6), (5, 7), (8, 7), (6, 8), (7, 8)):
        put(x, y, (232, 190, 58))
    put(6, 6, (255, 232, 120))
    put(7, 7, (176, 130, 28))
    put(6, 7, (255, 220, 96))
    put(7, 6, (255, 220, 96))
    for y in (10, 11):
        put(8, y + 3, (214, 176, 48))
    for x in range(5, 12, 2):
        put(x, 12, (170, 214, 110))


def codex(put):
    # gold corners and a pale four-pointed star
    for (x, y) in ((3, 2), (4, 2), (3, 3), (11, 2), (11, 3), (3, 12), (4, 12), (3, 11), (11, 12), (11, 11)):
        put(x, y, (232, 190, 58))
    for (x, y) in ((7, 4), (7, 5), (6, 6), (7, 6), (8, 6), (5, 7), (6, 7), (7, 7), (8, 7), (9, 7), (6, 8), (7, 8), (8, 8), (7, 9), (7, 10)):
        put(x, y, (206, 220, 255) if (x, y) != (7, 7) else (255, 255, 255))
    put(7, 3, (150, 170, 230))


def main():
    brown = [(52, 32, 20), (84, 54, 34), (108, 72, 44), (130, 92, 58)]
    green = [(24, 56, 28), (40, 84, 44), (58, 110, 56), (82, 138, 72)]
    blue = [(24, 28, 66), (40, 48, 104), (58, 70, 140), (84, 96, 170)]
    book(brown, {}, orc, "war_journal")
    book(green, {}, goblin, "goblin_ledger")
    book(blue, {}, codex, "lore_codex")
    # the torn corner of the war journal is transparent
    path = OUT + "war_journal.png"
    img = Image.open(path)
    for (x, y) in ((12, 1), (11, 1), (12, 2)):
        img.putpixel((x, y), (0, 0, 0, 0))
    img.save(path)


main()
