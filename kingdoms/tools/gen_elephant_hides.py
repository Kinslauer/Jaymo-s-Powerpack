"""Builds the war elephant and woolly mammoth hides (256x512, the 128x256 model drawn at twice the size) from DrZhark's
Mo' Creatures elephant textures in tools/mocreatures/ (GPL-3, see src/main/resources/licenses/mocreatures). The Indian,
African and royal (painted) elephants are theirs as they are; the woolly mammoth too, with two coats tinted from it
(russet, and the rare frost). Every hide also gets the mammoth's wooden war platform (our howdah) and our crimson sun
banner. Run from kingdoms/: python3 -I tools/gen_elephant_hides.py"""
import colorsys
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "mocreatures")
OUT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "jaymoskingdoms", "textures", "entity", "war_elephant")
CRIMSON, GOLD, WOOD, DARK_WOOD = (152, 26, 34, 255), (216, 172, 62, 255), (104, 66, 38, 255), (72, 44, 24, 255)


def load(name):
    return Image.open(os.path.join(SRC, name + ".png")).convert("RGBA")


def banner():
    """The banner pole (1x10x1 at 88,41) and flag (0x6x5 at 94,41), at 1x: a dark-grained pole, a crimson flag with a gold sun."""
    img = Image.new("RGBA", (16, 11), (0, 0, 0, 0))
    for y in range(11):
        for x in range(4):
            img.putpixel((x, y), DARK_WOOD if (x + y) % 3 == 0 else WOOD)
    for y in range(5, 11):
        for x in range(6, 16):
            img.putpixel((x, y), CRIMSON)
    for cx in (8, 13):
        for x, y in ((cx, 7), (cx - 1, 8), (cx, 8), (cx + 1, 8), (cx, 9)):
            img.putpixel((x, y), GOLD)
    return img.resize((32, 22), Image.NEAREST)


FORT = load("mammoth").crop((0, 352, 164, 392))  # Mo' Creatures' war platform (on the mammoth only), shared by every hide
BANNER = banner()


def finish(img):
    img = img.copy()
    for x in range(FORT.width):
        for y in range(FORT.height):
            p = FORT.getpixel((x, y))
            if p[3] and not img.getpixel((x, 352 + y))[3]:
                img.putpixel((x, 352 + y), p)
    img.paste(BANNER, (176, 82))
    return img


def tint(img, fn):
    img = img.copy()
    px = img.load()
    for x in range(img.width):
        for y in range(256):  # the hide only, not its harness and platform
            r, g, b, a = px[x, y]
            if a and (r + g + b) / 3 < 150:  # the fur, not the ivory
                px[x, y] = fn(r, g, b) + (a,)
    return img


def russet(r, g, b):
    h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
    r2, g2, b2 = colorsys.hls_to_rgb(0.04, min(1, l * 1.25), min(1, s * 1.6 + 0.15))
    return int(r2 * 255), int(g2 * 255), int(b2 * 255)


def frost(r, g, b):
    v = int(120 + (r + g + b) / 3 * 0.9)
    return min(255, v), min(255, v + 4), min(255, v + 10)


def main():
    mammoth = load("mammoth")
    for name, img in (("indian", load("elephantindian")), ("african", load("elephantafrican")), ("royal", load("elephantindianpretty")),
                      ("woolly", mammoth), ("russet", tint(mammoth, russet)), ("frost", tint(mammoth, frost))):
        finish(img).save(os.path.join(OUT, name + ".png"))
    print("wrote 6 hides to " + os.path.normpath(OUT))


if __name__ == "__main__":
    main()
