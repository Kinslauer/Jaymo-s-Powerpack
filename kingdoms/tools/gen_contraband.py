"""(0.7.210) The underworld's contraband icons: a cloth twist of Pmeh and a paper-wrapped, twine-tied Shash brick.
Run from kingdoms/: python -I tools/gen_contraband.py"""
from PIL import Image

OUT = "src/main/resources/assets/jaymoskingdoms/textures/item/"


def pmeh():
    # a little cloth pouch tied at the neck, pale violet powder spilling from its mouth
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    cloth, shade, dark, cord = (196, 182, 150, 255), (160, 146, 116, 255), (110, 98, 76, 255), (120, 70, 40, 255)
    powder, powder2 = (214, 196, 236, 255), (176, 150, 212, 255)
    body = {9: (4, 11), 10: (3, 12), 11: (3, 12), 12: (3, 12), 13: (4, 11), 14: (5, 10)}
    for y, (a, b) in body.items():
        for x in range(a, b + 1):
            px[x, y] = shade if x >= b - 2 or y == 14 else cloth
        px[a, y] = dark
        px[b, y] = dark
    for x in range(5, 11):
        px[x, 15] = dark
    for x in range(6, 10):
        px[x, 8] = cord
    for y, (a, b) in {5: (6, 9), 6: (5, 10), 7: (6, 9)}.items():
        for x in range(a, b + 1):
            px[x, y] = cloth if (x + y) % 3 else shade
        px[a, y] = dark
        px[b, y] = dark
    for (x, y) in [(7, 4), (8, 4), (8, 3), (9, 3), (10, 4), (11, 5), (12, 6), (11, 7), (9, 4)]:
        px[x, y] = powder if (x + y) % 2 else powder2
    for (x, y) in [(12, 12), (13, 13), (12, 14), (2, 14)]:
        px[x, y] = powder2
    img.save(OUT + "pmeh.png")


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


pmeh()
shash_brick()
