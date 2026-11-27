"""The Octo Core's art (Jaymo's Powers, rig/RigOcto, client rig/RigOctoClient).

Run from the repo root: python3 tools/gen_rig_octo.py

- textures/entity/rig/octo.png (32x32) and octo_glow.png: the four arms' sheet. The Rig's gunmetal ramp (the same five
  shades as tools/gen_rig.py), placed as fixed plates and seams, never per-pixel noise (docs/Vanilla-Style.md): the shoulder
  strut at 0,0, the forearm at 0,12, the spine mount at 0,24, the elbow joint at 12,24 (lit cyan: it glows), the claw hub
  at 22,24 and the prongs at 22,28. Each cube's box faces are painted lit on top, darker underneath.
- textures/item/rig/octo_core.png (16x16): the Core's icon with the four arms curling out of its corners, painted pixel by
  pixel from the Core's own icon and palette.
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "jaymospowers")

SEAM = (30, 32, 38)
PLATE_LO = (52, 56, 64)
PLATE = (78, 84, 94)
PLATE_HI = (112, 120, 132)
PLATE_TOP = (140, 148, 160)
GLOW = (90, 230, 255)
GLOW_DIM = (40, 140, 170)
WHITE = (220, 255, 255)


def box(im, u, v, w, h, d, glow=None, lit=False):
    """Paints one cube's UV layout (vanilla's box unwrap) at u, v: w wide, h high, d deep, in whole pixels."""
    w, h, d = max(1, round(w)), max(1, round(h)), max(1, round(d))
    faces = {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h),
    }
    for face, (fx, fy, fw, fh) in faces.items():
        for y in range(fh):
            for x in range(fw):
                if lit:
                    c = WHITE if (x + y) % 3 == 0 and face in ("top", "front") else GLOW
                elif face == "top":
                    c = PLATE_TOP if y == 0 else PLATE_HI
                elif face == "bottom":
                    c = PLATE_LO
                else:
                    # a plate every 4 pixels along the length, a seam between, the upper edge catching the light
                    along = x if face in ("right", "left") else y
                    if along % 4 == 3:
                        c = SEAM
                    elif y == 0:
                        c = PLATE_HI
                    elif y == fh - 1:
                        c = PLATE_LO
                    else:
                        c = PLATE
                im.putpixel((fx + x, fy + y), c + (255,))
                if lit and glow is not None:
                    glow.putpixel((fx + x, fy + y), c + (255,))


def arms():
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    glow = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    box(im, 0, 0, 2, 2, 9)          # shoulder strut
    box(im, 0, 12, 1.5, 1.5, 8)     # forearm
    box(im, 0, 24, 3, 3, 2)         # the mount on the spine
    box(im, 12, 24, 2.5, 2.5, 2.5, glow, lit=True)  # the elbow joint, lit
    box(im, 22, 24, 2, 2, 1.5)      # claw hub
    box(im, 22, 28, 0.8, 0.8, 3)    # prong
    # a lit strip down each strut's outer face: the servo line (in the glow pass too)
    for y in range(9, 11):
        for x in range(0, 9, 4):
            for img in (im, glow):
                img.putpixel((x + 1, y), GLOW_DIM + (255,))
    im.save(os.path.join(ASSETS, "textures", "entity", "rig", "octo.png"))
    glow.save(os.path.join(ASSETS, "textures", "entity", "rig", "octo_glow.png"))


# The Core's icon, smaller, with an arm out of each corner ending in a lit claw: A seam, B lit plate, C plate, D low plate, E dim glow, F glow core.
ICON = [
    "EE............EE",
    "EAB..........DAE",
    ".AAB........DAA.",
    "..AABAAAAAADAA..",
    "...ABBBBBBBBA...",
    "...ABCCAACCDA...",
    "...ABCAEEACDA...",
    "...ABAEFFEADA...",
    "...ABCAEEACDA...",
    "...ABCCAACCDA...",
    "...ABCCCCCCDA...",
    "..AACDDDDDDDAA..",
    ".AAC.AAEEAA.DAA.",
    "EAC..........DAE",
    "EE............EE",
    "................",
]
ICON_COLOURS = {"A": SEAM, "B": PLATE_HI, "C": PLATE, "D": PLATE_LO, "E": GLOW_DIM, "F": WHITE}


def icon():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(ICON):
        for x, ch in enumerate(row):
            if ch in ICON_COLOURS:
                im.putpixel((x, y), ICON_COLOURS[ch] + (255,))
    im.save(os.path.join(ASSETS, "textures", "item", "rig", "octo_core.png"))


if __name__ == "__main__":
    arms()
    icon()
