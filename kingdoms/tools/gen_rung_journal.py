"""Draws the Rung Journal's icon (16x16, vanilla's own shading): a small cracked leather book with a ladder of four rungs
stitched up its cover and a ribbon marker hanging from the bottom. Run from the kingdoms project root:
python -I tools/gen_rung_journal.py"""
from PIL import Image
import os

OUT = "src/main/resources/assets/jaymoskingdoms/textures/item/rung_journal.png"

LEATHER = [(58, 36, 22), (86, 56, 34), (112, 76, 46), (136, 96, 60)]   # dark edge .. light
PAGE = [(196, 184, 150), (222, 212, 180)]
THREAD = (226, 196, 112)
RIBBON = [(150, 40, 36), (190, 62, 52)]


def main():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))

    def put(x, y, c):
        img.putpixel((x, y), tuple(c) + (255,))

    # the book: 11 wide, 14 tall, spine on the left
    x0, y0, x1, y1 = 2, 1, 12, 13
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            edge = x in (x0, x1) or y in (y0, y1)
            put(x, y, LEATHER[0] if edge else LEATHER[2])
    for y in range(y0 + 1, y1):
        put(x0 + 1, y, LEATHER[1])      # spine shade
        put(x0 + 2, y, LEATHER[3] if y % 2 else LEATHER[2])
    for x in range(x0 + 1, x1):
        put(x, y0 + 1, LEATHER[3])      # top light edge
    # page block showing along the bottom and right
    for y in range(y0 + 1, y1):
        put(x1 + 1, y, PAGE[0])
    for x in range(x0 + 1, x1 + 1):
        put(x, y1 + 1, PAGE[1])
    put(x1 + 1, y1 + 1, PAGE[0])
    # a ladder stitched up the cover: two rails, four rungs
    left, right = 5, 9
    for y in range(3, 12):
        put(left, y, THREAD)
        put(right, y, THREAD)
    for y in (4, 6, 8, 10):
        for x in range(left + 1, right):
            put(x, y, THREAD)
    # crack across the lower corner
    for (x, y) in ((10, 11), (11, 11), (11, 10)):
        put(x, y, LEATHER[0])
    # ribbon marker
    for y in (y1 + 1, y1 + 2):
        put(6, y, RIBBON[1])
        put(7, y, RIBBON[0])
    put(6, y1 + 2, RIBBON[0])
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print("wrote", OUT)


main()
