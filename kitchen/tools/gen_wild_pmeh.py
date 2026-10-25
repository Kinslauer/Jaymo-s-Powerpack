"""(0.5.29) Wild Pmeh, a cross-model plant: a low bush of curling dark leaves with pale violet buds.
Run from kitchen/: python -I tools/gen_wild_pmeh.py"""
from PIL import Image

img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
px = img.load()
stem, leaf, leaf2, dark = (70, 96, 40, 255), (84, 132, 58, 255), (110, 156, 72, 255), (48, 80, 36, 255)
bud, bud2 = (204, 178, 232, 255), (164, 128, 204, 255)
# stems fanning up from the root
for (x0, lean) in [(7, -1), (8, 1), (8, 0), (7, 0)]:
    x = x0
    for i, y in enumerate(range(15, 5, -1)):
        if i % 3 == 2:
            x += lean
        if 0 <= x < 16:
            px[x, y] = stem
# curling leaves along them
leaves = [(4, 12), (5, 11), (3, 11), (11, 12), (10, 11), (12, 11), (5, 9), (4, 8), (6, 8), (10, 9), (11, 8), (9, 8),
          (6, 6), (7, 5), (9, 6), (8, 5), (3, 13), (12, 13), (6, 13), (9, 13)]
for i, (x, y) in enumerate(leaves):
    px[x, y] = leaf if i % 3 else leaf2
for (x, y) in [(4, 13), (11, 13), (5, 10), (10, 10), (6, 7), (9, 7)]:
    px[x, y] = dark
# the buds
for (x, y) in [(7, 3), (8, 4), (6, 4), (9, 3), (3, 7), (12, 7), (2, 10), (13, 10)]:
    px[x, y] = bud if (x + y) % 2 else bud2
img.save("src/main/resources/assets/jaymoskitchen/textures/block/wild_pmeh.png")
