"""The Ocellan, the Pale Moon's small bug-eyed people who made the Xeno-Band: the skins for client/OcellanBody.java.

An Ocellan has the body of Powers' Gremlin (tools/gen_xenoform.py gremlin_paint: grey hide, wide flat head, two great
yellow-green eyes that shine) dressed as the high-born folk they are: a robe to the ankles with a trimmed hem and a line
of trim down the front, a sash, wide cuffs, a short mantle over the shoulders, and a monocle on a chain over the right eye. Three robes
(plum, teal, umber), picked per Ocellan. Everything is drawn from code; nothing is copied.

Sheet 64x64: the Gremlin's boxes where the Gremlin has them (head 8x7x6 at (0,0), eye 4x4x3 at (32,0), body 5x7x3 at
(16,16), arm 2x7x2 at (40,16), leg 2x6x2 at (0,16), foot 3x1x4 at (0,32)), then monocle 5x5x1 at (48,0), chain 1x3x1 at
(60,0), cuff 3x2x3 at (48,16), skirt 7x6x5 at (16,32), mantle 7x2x5 at (0,44).

    python3 kingdoms/tools/gen_ocellan.py        (from the repo root)
"""
import sys
from pathlib import Path

from PIL import Image

REPO = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(REPO / "tools"))
import gen_xenoform as gx  # noqa: E402
from gen_xenoform import CLEAR, faces, rect, shade  # noqa: E402

OUT = REPO / "kingdoms" / "src" / "main" / "resources" / "assets" / "jaymoskingdoms" / "textures" / "entity" / "ocellan"
SIZE = 64
MONOCLE = (48, 0, 5, 5, 1)
CHAIN = (60, 0, 1, 3, 1)
CUFF = (48, 16, 3, 2, 3)
SKIRT = (16, 32, 7, 6, 5)
COLLAR = (0, 44, 7, 2, 5)
SIDES = ("front", "back", "left", "right")
GOLD = (222, 178, 74)
CREAM = (226, 214, 184)
# (cloth, trim)
ROBES = (((92, 48, 100), GOLD), ((34, 92, 100), GOLD), ((110, 76, 52), CREAM))


def cloth(img, part, colour, spread=0.1):
    """A part in cloth: lit per face, a fold line every other column on its sides."""
    gx.box(img, part, colour, spread)
    for name in SIDES:
        x0, y0, x1, y1 = faces(*part)[name]
        for x in range(x0 + 1, x1, 3):
            for y in range(y0, y1):
                img.putpixel((x, y), shade(img.getpixel((x, y)), 0.86))


def robe(img, colour, trim):
    body, arm = gx.GREMLIN_BODY, gx.GREMLIN_ARM
    dark = shade(colour, 0.7)
    # the body: cloth, a line of trim down the front, a sash round the waist
    cloth(img, body, colour)
    x0, y0, x1, y1 = gx.front(body)
    rect(img, (x0 + 2, y0, x0 + 3, y1), shade(trim, 1.0))
    for name in SIDES:
        fx0, fy0, fx1, fy1 = faces(*body)[name]
        rect(img, (fx0, fy1 - 2, fx1, fy1 - 1), shade(trim, 0.92 if name == "front" else 0.74))
    img.putpixel((x0 + 2, y1 - 2), shade(trim, 1.25))
    # the sleeves: cloth down to the wrist, the grey hand and its claws left showing
    for name, (fx0, fy0, fx1, fy1) in faces(*arm).items():
        if name in SIDES:
            for y in range(fy0, fy1 - 2):
                for x in range(fx0, fx1):
                    img.putpixel((x, y), shade(colour, gx.LIGHT[name] * (0.95 + gx.rng.random() * 0.1)))
        elif name == "top":
            rect(img, (fx0, fy0, fx1, fy1), shade(colour, 1.15))
    # wide cuffs, trimmed at the edge
    cloth(img, CUFF, colour)
    for name in SIDES:
        fx0, fy0, fx1, fy1 = faces(*CUFF)[name]
        rect(img, (fx0, fy1 - 1, fx1, fy1), shade(trim, 0.95 if name == "front" else 0.78))
    rect(img, faces(*CUFF)["bottom"], dark)
    # the skirt: cloth to the ankles, the trim carried down the front, a trimmed hem, dark inside
    cloth(img, SKIRT, colour)
    x0, y0, x1, y1 = gx.front(SKIRT)
    rect(img, (x0 + 3, y0, x0 + 4, y1), trim + (255,))
    for name in SIDES:
        fx0, fy0, fx1, fy1 = faces(*SKIRT)[name]
        rect(img, (fx0, fy1 - 1, fx1, fy1), shade(trim, 0.95 if name == "front" else 0.76))
    rect(img, faces(*SKIRT)["bottom"], shade(colour, 0.4))
    # a short mantle over the shoulders, a shade darker, trimmed along its lower edge, a clasp at the throat
    cloth(img, COLLAR, shade(colour, 0.82))
    for name in SIDES:
        fx0, fy0, fx1, fy1 = faces(*COLLAR)[name]
        rect(img, (fx0, fy1 - 1, fx1, fy1), shade(trim, 0.95 if name == "front" else 0.78))
    x0, y0, x1, y1 = gx.front(COLLAR)
    img.putpixel((x0 + 3, y0), shade(trim, 1.25))
    rect(img, faces(*COLLAR)["bottom"], shade(colour, 0.4))


def monocle(img):
    """A gold ring with a glint of glass, and a fine chain down from it."""
    gx.box(img, MONOCLE, GOLD, 0.08)
    for name in ("front", "back"):
        x0, y0, x1, y1 = faces(*MONOCLE)[name]
        rect(img, (x0 + 1, y0 + 1, x1 - 1, y1 - 1), CLEAR)
        for corner in ((x0, y0), (x1 - 1, y0), (x0, y1 - 1), (x1 - 1, y1 - 1)):
            img.putpixel(corner, CLEAR)
        if name == "front":
            img.putpixel((x0 + 1, y0 + 1), (240, 250, 255, 255))
    gx.box(img, CHAIN, shade(GOLD, 0.9), 0.06)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    glow = None
    for i, (colour, trim) in enumerate(ROBES):
        base = Image.new("RGBA", (64, 128))
        lit = Image.new("RGBA", (64, 128))
        gx.gremlin_paint(base, lit)
        img = base.crop((0, 0, SIZE, SIZE))
        # (no jaw on an Ocellan: its patch is left clear)
        rect(img, (32, 8, 64, 16), CLEAR)
        gx.rng.seed("ocellan%d" % i)
        robe(img, colour, trim)
        monocle(img)
        img.save(OUT / ("ocellan_%d.png" % i))
        glow = lit.crop((0, 0, SIZE, SIZE))
    glow.save(OUT / "ocellan_glow.png")


if __name__ == "__main__":
    main()
