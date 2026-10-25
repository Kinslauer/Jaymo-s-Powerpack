"""(0.7.210) The underworld's contraband icon: a paper-wrapped, twine-tied Shash brick (Pmeh is
Jaymo's Kitchen's).
Run from kingdoms/: python -I tools/gen_contraband.py"""
from PIL import Image

OUT = "src/main/resources/assets/jaymoskingdoms/textures/item/"


def shash_brick():
    # a dense brick in brown waxed paper, tied crossways with twine, a green-black corner showing through a tear
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    paper, light, dark, edge = (150, 112, 70, 255), (178, 138, 90, 255), (112, 80, 48, 255), (72, 50, 30, 255)
    twine, twine2 = (222, 204, 160, 255), (180, 160, 116, 255)
    shash, shash2 = (58, 74, 40, 255), (38, 50, 28, 255)
    # top face (a parallelogram), then the front
    for y in range(3, 7):
        for x in range(3 + (6 - y), 14 - (y - 3) // 3):
            px[x, y] = light
    for y in range(7, 13):
        for x in range(2, 14):
            px[x, y] = paper if x < 11 else dark
    for x in range(2, 14):
        px[x, 13] = edge
    for y in range(3, 14):
        px[2 if y >= 7 else 3 + (6 - y), y] = edge
        px[13, y] = edge
    for x in range(6, 14):
        px[x, 2] = edge
    # the twine: across and down
    for x in range(2, 14):
        px[x, 9] = twine if x % 2 else twine2
    for y in range(3, 14):
        px[8, y] = twine if y % 2 else twine2
    # a torn corner, the shash showing
    for (x, y) in [(3, 11), (4, 11), (3, 12), (4, 12), (5, 12), (3, 10)]:
        px[x, y] = shash if (x + y) % 2 else shash2
    img.save(OUT + "shash_brick.png")


shash_brick()
