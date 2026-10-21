"""Mining charge textures (BWM port): a bundle of nine red sticks, strapped with twine, on a slime pad. Our own art.

Vanilla style (docs/Vanilla-Style.md): 16x16, painted only in ramps read off vanilla 26.3's tnt_top/tnt_side/tnt_bottom
(the reds, the paper band's greys, the fuse hole) and slime_block (the pad's greens, made opaque). The model
(models/block/mining_charge.json) shows a 12x12 front and back (pixels 2..13) and 12x5 edges (rows 0..4 of the side).
- mining_charge_front: the stick ends, nine of TNT's 4x4 tube ends, twine across them, the fuse in the middle one.
- mining_charge_side: the sticks' sides with TNT's paper band between two rows of red.
- mining_charge_back: TNT's bottom with slime smeared on it, the side that sticks to the rock.
Run from the repository root: python -I tools/gen_mining_charge.py
"""
from PIL import Image

OUT = "src/main/resources/assets/jaymospowers/textures/block/"

# tnt_top / tnt_side / tnt_bottom
RED_LIT = (234, 67, 24)
RED = (219, 47, 26)
RED_DARK = (177, 21, 39)
RED_SHADE = (145, 45, 17)
GREY = (142, 142, 142)
GREY_DARK = (86, 86, 86)
HOLE = (17, 17, 30)
PAPER = (255, 255, 255)
PAPER_LIGHT = (221, 217, 217)
PAPER_MID = (206, 206, 206)
PAPER_DARK = (190, 178, 179)
INK = (55, 54, 86)
INK_DARK = (27, 26, 60)
# slime_block, opaque
SLIME_LIGHT = (123, 206, 106)
SLIME = (115, 194, 98)
SLIME_MID = (98, 182, 74)
SLIME_DARK = (90, 170, 67)

# one stick end: tnt_top's 4x4 cell at (0, 0)
TUBE = [
    [RED, RED_LIT, RED_LIT, RED],
    [RED_LIT, GREY, GREY, RED_DARK],
    [RED_LIT, GREY, GREY_DARK, RED_SHADE],
    [RED_DARK, RED_DARK, RED_SHADE, RED_SHADE],
]


def save(img, name):
    img.save(OUT + name + ".png")


def front():
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), TUBE[(y - 2) % 4][(x - 2) % 4] + (255,))
    # the fuse goes into the middle stick (cells start at 2, 6, 10)
    for x, y in ((7, 7), (8, 7), (7, 8)):
        img.putpixel((x, y), HOLE + (255,))
    img.putpixel((8, 8), GREY_DARK + (255,))
    img.putpixel((8, 6), PAPER_DARK + (255,))
    img.putpixel((9, 5), PAPER_MID + (255,))
    # twine across the bundle, lit from the top-left
    for i in range(16):
        img.putpixel((i, 5), (PAPER_LIGHT if i < 8 else PAPER_MID) + (255,))
        img.putpixel((i, 10), (PAPER_MID if i < 8 else PAPER_DARK) + (255,))
    for i in (4, 11):
        img.putpixel((i, 5), PAPER_DARK + (255,))
        img.putpixel((i, 10), PAPER_DARK + (255,))
    save(img, "mining_charge_front")


def side():
    img = Image.new("RGBA", (16, 16))
    # rows 0..4 are what the model shows: lit edge, stick, paper band, stick, shade; then it repeats
    stick = [RED, RED, RED_DARK, RED_SHADE]
    lit = [RED_LIT, RED_LIT, RED, RED_DARK]
    shade = [RED_DARK, RED_DARK, RED_DARK, RED_SHADE]
    for y in range(16):
        row = y % 5
        for x in range(16):
            c = x % 4
            if row == 0:
                col = lit[c]
            elif row == 2:
                col = PAPER if x < 2 else PAPER_LIGHT if x < 8 else PAPER_MID if x < 14 else PAPER_DARK
                if x in (5, 6, 9) or x == 11:
                    col = INK if x != 6 else INK_DARK
            elif row == 4:
                col = shade[c]
            else:
                col = stick[c]
            img.putpixel((x, y), col + (255,))
    save(img, "mining_charge_side")


def back():
    img = Image.new("RGBA", (16, 16))
    bottom = [
        [RED_DARK, RED, RED, RED_DARK],
        [RED, GREY, GREY, RED_SHADE],
        [RED, GREY, GREY, RED_SHADE],
        [RED_SHADE, RED_SHADE, RED_SHADE, RED_SHADE],
    ]
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), bottom[(y - 2) % 4][(x - 2) % 4] + (255,))
    # a smear of slime over the middle: lit top-left, darker bottom-right
    blob = [
        "....aab.....",
        "..aabbbbc...",
        ".abbbbbbbc..",
        ".abbbbbbccd.",
        "abbbbbbbbcd.",
        "abbbbbbbccd.",
        ".bbbbbbbccd.",
        ".bcbbbbcccd.",
        "..ccbbccdd..",
        "...cddccd...",
        ".....dd.....",
    ]
    ramp = {"a": SLIME_LIGHT, "b": SLIME, "c": SLIME_MID, "d": SLIME_DARK}
    for j, line in enumerate(blob):
        for i, ch in enumerate(line):
            if ch in ramp:
                img.putpixel((2 + i, 3 + j), ramp[ch] + (255,))
    save(img, "mining_charge_back")


if __name__ == "__main__":
    front()
    side()
    back()
