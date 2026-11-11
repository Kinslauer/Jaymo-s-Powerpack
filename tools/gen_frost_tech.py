"""Draws the Frostborn-made limb a Frostborn villager wears (Kingdoms' FrostTech, the client's FrostTechLayer): one 16x16
sheet for a 4x12x4 box (an arm or a leg, the same box turned), in the greys and the green light of Jaymo's Powers'
scouter so the two read as one people's work, plus its glow twin (the lights only, drawn unlit). Our own art.
Run it from the root:

    python -I tools/gen_frost_tech.py [preview.png]

With a path it also writes an 8x preview of the two sheets there.

The box's layout (vanilla's, texOffs 0,0): top 4,0 and bottom 8,0 (4x4 each); then a row 12 tall from y 4: the right
side at x 0, the front at 4, the left at 8, the back at 12.
"""
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms", "textures", "entity", "frost_tech")

# the scouter's own tones (light plate, mid, dark seam) and its lens
LIGHT = (196, 198, 206, 255)
MID = (140, 143, 152, 255)
DARK = (92, 95, 104, 255)
SEAM = (58, 60, 70, 255)
LENS = (62, 200, 124, 255)
LENS_HOT = (176, 255, 206, 255)
LENS_DIM = (22, 112, 72, 255)

# rows of the 12-tall sides, from the shoulder (or hip) down
SHOULDER, UPPER_END, JOINT, LOWER_END, WRIST, HAND = 0, 4, 5, 9, 10, 11


def side(img, glow, x0, face):
    """One 4x12 side at x0. face: 'front', 'back', 'outer' (the right side) or 'inner'."""
    for y in range(12):
        for x in range(4):
            if y == SHOULDER:
                c = DARK
            elif y == JOINT or y == WRIST:
                c = SEAM
            elif y == HAND:
                c = DARK if x in (0, 3) else MID
            else:
                # a plate lit from the left edge, shaded on the right
                c = LIGHT if x == 0 else DARK if x == 3 else MID
                if y in (UPPER_END, LOWER_END) or y == JOINT + 1:
                    c = MID if c == LIGHT else DARK
            img.putpixel((x0 + x, 4 + y), c)
    if face == "outer":
        # a strip of light down the upper plate, and one lamp under the joint
        for y in (1, 2, 3):
            img.putpixel((x0 + 1, 4 + y), LENS if y != 2 else LENS_HOT)
            glow.putpixel((x0 + 1, 4 + y), LENS if y != 2 else LENS_HOT)
        img.putpixel((x0 + 2, 4 + 7), LENS_DIM)
        glow.putpixel((x0 + 2, 4 + 7), LENS_DIM)
    elif face == "front":
        # two rivets on the upper plate, a lamp on the back of the hand
        img.putpixel((x0 + 1, 4 + 2), SEAM)
        img.putpixel((x0 + 2, 4 + 2), SEAM)
        img.putpixel((x0 + 1, 4 + 8), LENS)
        glow.putpixel((x0 + 1, 4 + 8), LENS)
    elif face == "back":
        # a ridge down the back of the lower plate
        for y in (6, 7, 8):
            img.putpixel((x0 + 2, 4 + y), SEAM)


def cap(img, x0, top):
    for y in range(4):
        for x in range(4):
            edge = x in (0, 3) or y in (0, 3)
            img.putpixel((x0 + x, y), DARK if edge else (MID if top else SEAM))


def draw():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    glow = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    cap(img, 4, True)
    cap(img, 8, False)
    side(img, glow, 0, "outer")
    side(img, glow, 4, "front")
    side(img, glow, 8, "inner")
    side(img, glow, 12, "back")
    return img, glow


def main():
    os.makedirs(OUT, exist_ok=True)
    img, glow = draw()
    img.save(os.path.join(OUT, "limb.png"))
    glow.save(os.path.join(OUT, "limb_glow.png"))
    if len(sys.argv) > 1:
        sheet = Image.new("RGBA", (16 * 2 + 2, 16), (30, 30, 36, 255))
        sheet.alpha_composite(img, (0, 0))
        sheet.alpha_composite(glow, (18, 0))
        sheet.resize((sheet.width * 8, sheet.height * 8), Image.NEAREST).save(sys.argv[1])


if __name__ == "__main__":
    main()
