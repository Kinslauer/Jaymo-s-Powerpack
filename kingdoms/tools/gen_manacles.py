"""(0.7.414) The watch recruit's manacles: textures/item/manacles.png, 16x16, drawn in the vanilla style (a few flat colours, a
dark outline, a lit edge and a shaded one). Two iron cuffs joined by a short chain, hung slightly askew.
Run from kingdoms/: python -I tools/gen_manacles.py"""
from PIL import Image

OUT = "src/main/resources/assets/jaymoskingdoms/textures/item/manacles.png"

OUTLINE = (34, 34, 40, 255)
IRON = (118, 122, 132, 255)
LIGHT = (176, 180, 192, 255)
DARK = (78, 80, 90, 255)
HOLE = (20, 20, 26, 255)
LINK = (150, 154, 164, 255)

img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
px = img.load()


def cuff(cx, cy):
    """A cuff: a ring 6 across seen from the front, its open middle dark, lit top-left and shaded bottom-right."""
    ring = [(0, -3), (1, -3), (2, -2), (3, -1), (3, 0), (3, 1), (2, 2), (1, 3), (0, 3), (-1, 3), (-2, 2), (-3, 1), (-3, 0), (-3, -1),
            (-2, -2), (-1, -3)]
    for dx, dy in ring:
        px[cx + dx, cy + dy] = OUTLINE
    inner = [(0, -2), (1, -2), (2, -1), (2, 0), (2, 1), (1, 2), (0, 2), (-1, 2), (-2, 1), (-2, 0), (-2, -1), (-1, -2)]
    for dx, dy in inner:
        # lit on the upper left, shaded on the lower right
        px[cx + dx, cy + dy] = LIGHT if dx + dy < -1 else DARK if dx + dy > 1 else IRON
    for dx, dy in [(0, -1), (1, -1), (-1, -1), (-1, 0), (0, 0), (1, 0), (-1, 1), (0, 1), (1, 1)]:
        px[cx + dx, cy + dy] = HOLE
    # the rivet on the hinge
    px[cx + 2, cy - 1] = LIGHT


cuff(4, 4)
cuff(11, 11)

# the chain between them: alternating links down the slope, each with a dark edge
chain = [(7, 6), (8, 7), (8, 8), (7, 9)]
for i, (x, y) in enumerate(chain):
    px[x, y] = LINK if i % 2 == 0 else IRON
for x, y in [(6, 6), (9, 7), (9, 8), (6, 9), (8, 9), (7, 5), (8, 6), (7, 10)]:
    if px[x, y][3] == 0:
        px[x, y] = OUTLINE

img.save(OUT)
print("wrote", OUT)
