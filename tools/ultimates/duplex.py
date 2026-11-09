"""(0.21.37, Ultimates patch 2) Duplex's Ultimate, Copper Storm: its skin and glow map, 128x128, for client/.../ultimate/UltimateCopperStorm.java.

The copper golem's riveted copper (the same seams, rivets and verdigris creeping in, a dark visor slot with pale cyan eyes, a darker
belt with a buckle, the small insignia on the chest) with patina-green plates over the head, shoulders, forearms, back and boots,
dark copper spikes on the crown, shoulders and back, a bright copper coil wound under the rod's polished ball tip, and electric
blue-white seams (0xAAE1FF) round the plates, along the coil, in the eyes and along the belt. The glow map holds those seams, the eyes
and the tips of the spikes (fullbright).

    python3 tools/ultimates/duplex.py            paint both skins
    python3 tools/ultimates/duplex.py layout     print the UV table (the Java model holds the same table)
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from PIL import Image  # noqa: E402

import gen_xenoform as gx  # noqa: E402
import gen_xeno_ultimates as gxu  # noqa: E402
from gen_xenoform import CLEAR, faces, rect, save, shade, speckle  # noqa: E402

SIZE = 128

# every box of the model, (w, h, d); boxes that look the same on both sides share one patch of the skin
DIMS = {
    "head": (8, 5, 10), "nose": (2, 3, 2), "crown": (9, 1, 11), "cheek": (1, 4, 6),
    "rod": (2, 6, 2), "coil": (4, 1, 4), "knob": (4, 4, 4),
    "body": (8, 6, 6), "belt": (8, 2, 6), "backplate": (6, 4, 1),
    "arm": (3, 10, 4), "pauld": (5, 2, 6), "bracer": (4, 5, 5),
    "leg": (4, 5, 4), "boot": (5, 2, 6), "spike": (1, 3, 1),
}
UV = gxu.pack(DIMS)

COPPER = (190, 104, 66)
BRIGHT = (232, 150, 96)
PATINA = (74, 150, 124)       # the green plates
VERDIGRIS = (88, 168, 136)
DARK = (66, 40, 30)           # dark copper (spikes, the seams between plates)
SEAM = (170, 225, 255)        # 0xAAE1FF: electric blue-white
SEAM_HOT = (240, 250, 255)
EYE = (170, 245, 240)
SIDES = ("front", "back", "left", "right")

PLATES = ("crown", "cheek", "pauld", "bracer", "boot", "backplate")
COPPERS = ("head", "nose", "rod", "body", "arm", "leg")


def patch(name):
    u, v = UV[name]
    w, h, d = DIMS[name]
    return (u, v, w, h, d)


def glow_px(skin, glow, x, y, color=SEAM, strength=1.0):
    """A glowing seam pixel: a pale blue in the skin (so it reads in daylight) and the colour in the glow map."""
    skin.putpixel((x, y), gx.lerp(shade(COPPER, 0.9), color, 0.7))
    glow.putpixel((x, y), tuple(int(c) for c in color) + (int(255 * strength),))


def seam_rim(skin, glow, box, strength=0.75):
    """The blue-white line between plates: the bottom and right rim of a plate face glows."""
    x0, y0, x1, y1 = box
    for x in range(x0, x1):
        if (x + y1) % 3 != 0:
            glow_px(skin, glow, x, y1 - 1, SEAM, strength)
    for y in range(y0, y1 - 1):
        if (y + x1) % 3 != 0:
            glow_px(skin, glow, x1 - 1, y, SEAM, strength)


def plate(skin, glow, name, rim=True):
    """A raised patina-green plate: bevelled, rivets at its corners, a blue-white seam round its sides."""
    part = patch(name)
    gx.box(skin, part, PATINA, 0.18, edge=0.74)
    for fname, box in faces(*part).items():
        x0, y0, x1, y1 = box
        if x1 - x0 < 2 or y1 - y0 < 2:
            continue
        if fname in SIDES + ("top",):
            gxu.bevel(skin, box, shade(PATINA, {"top": 1.15}.get(fname, 0.92)))
        if x1 - x0 > 3 and y1 - y0 > 3:
            for (x, y) in ((x0 + 1, y0 + 1), (x1 - 2, y0 + 1), (x0 + 1, y1 - 2), (x1 - 2, y1 - 2)):
                skin.putpixel((x, y), (200, 238, 214, 255))
        speckle(skin, box, shade(PATINA, 1.25), max(1, (x1 - x0) * (y1 - y0) // 14))
        if rim and fname in SIDES:
            seam_rim(skin, glow, box)


def copper_box(skin, name):
    """Riveted copper: a seam round each face, a rivet at its corners, verdigris creeping in from an edge or two."""
    part = patch(name)
    gx.box(skin, part, COPPER, 0.14)
    seam = shade(COPPER, 0.62)
    rivet = (238, 170, 120, 255)
    for fname, b in faces(*part).items():
        x0, y0, x1, y1 = b
        gx.outline(skin, b, seam)
        for (x, y) in ((x0, y0), (x1 - 1, y0), (x0, y1 - 1), (x1 - 1, y1 - 1)):
            if x1 - x0 > 2 and y1 - y0 > 2:
                skin.putpixel((x, y), rivet)
        small = min(x1 - x0, y1 - y0) < 5
        reach = 1.6 if small else 2.6
        for _ in range(1 if small else gx.rng.randrange(1, 3)):
            cx, cy = gx.rng.choice(((x0, gx.rng.randrange(y0, y1)), (x1 - 1, gx.rng.randrange(y0, y1)), (gx.rng.randrange(x0, x1), y1 - 1)))
            for y in range(y0, y1):
                for x in range(x0, x1):
                    d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
                    if d < reach and gx.rng.random() < 0.75:
                        skin.putpixel((x, y), gx.lerp(skin.getpixel((x, y)), VERDIGRIS + (255,), 0.85 * (1 - d / (reach + 0.6))))
        speckle(skin, b, shade(COPPER, 1.18), max(1, (x1 - x0) * (y1 - y0) // 14))


def paint():
    gx.rng.seed("duplex_ultimate")
    skin = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    glow = Image.new("RGBA", (SIZE, SIZE), CLEAR)

    for name in COPPERS:
        copper_box(skin, name)
    # a seam across the forearms, where the golem's hands begin
    for fname, (x0, y0, x1, y1) in faces(*patch("arm")).items():
        if fname in SIDES:
            rect(skin, (x0, y0 + 6, x1, y0 + 7), shade(COPPER, 0.62))

    # the face (8x5): a dark visor slot with two pale cyan eyes above the nose; the eyes glow
    fx0, fy0, fx1, fy1 = gx.front(patch("head"))
    rect(skin, (fx0 + 1, fy0 + 1, fx1 - 1, fy0 + 3), (40, 22, 16, 255))
    for ex in (fx0 + 2, fx0 + 5):
        rect(skin, (ex, fy0 + 1, ex + 1, fy0 + 3), EYE + (255,))
        skin.putpixel((ex, fy0 + 1), (235, 255, 255, 255))
        for y in (fy0 + 1, fy0 + 2):
            glow.putpixel((ex, y), SEAM_HOT + (255,))
    rect(skin, (fx0 + 1, fy1 - 1, fx1 - 1, fy1), shade(COPPER, 0.62))
    gx.box(skin, patch("nose"), shade(COPPER, 1.05), 0.1)

    # the insignia, small enough for the golem's short chest: a green hourglass with a pale heart
    cx0, cy0, cx1, cy1 = gx.front(patch("body"))
    mx = cx0 + 3
    rim = (60, 30, 18, 255)
    rect(skin, (mx - 1, cy0 + 1, mx + 4, cy0 + 4), rim)
    rect(skin, (mx, cy0 + 1, mx + 3, cy0 + 2), gx.GREEN)
    skin.putpixel((mx + 1, cy0 + 2), gx.PALE_GREEN)
    rect(skin, (mx, cy0 + 3, mx + 3, cy0 + 4), gx.GREEN)

    # the belt: dark copper, a bright buckle at the front, sparks crawling along it (glowing every other pixel)
    belt = patch("belt")
    gx.box(skin, belt, shade(COPPER, 0.7), 0.1)
    for fname, b in faces(*belt).items():
        speckle(skin, b, VERDIGRIS + (255,), 2)
        if fname in SIDES:
            x0, y0, x1, y1 = b
            for x in range(x0, x1):
                if (x + y0) % 2 == 0:
                    glow_px(skin, glow, x, y0, SEAM, 0.85)
    lx0, ly0, lx1, ly1 = gx.front(belt)
    rect(skin, (lx0 + 2, ly0, lx0 + 6, ly1), (240, 196, 110, 255))
    rect(skin, (lx0 + 3, ly0, lx0 + 5, ly1), (120, 70, 40, 255))
    for x in (lx0 + 3, lx0 + 4):
        glow_px(skin, glow, x, ly0 + 1, SEAM_HOT, 0.9)

    # the plates over the head, shoulders, forearms, back and boots
    for name in PLATES:
        plate(skin, glow, name)

    # the coil: bright copper rings with a glowing wire round each
    coil = patch("coil")
    gx.box(skin, coil, BRIGHT, 0.1, edge=0.8)
    for fname, (x0, y0, x1, y1) in faces(*coil).items():
        if fname in SIDES:
            for x in range(x0, x1):
                glow_px(skin, glow, x, y0, SEAM, 0.8 if (x + y0) % 2 == 0 else 0.45)
    # the rod keeps its dull copper, a glowing thread down one edge of each side
    for fname, (x0, y0, x1, y1) in faces(*patch("rod")).items():
        if fname in SIDES:
            for y in range(y0, y1):
                if y % 2 == 0:
                    glow_px(skin, glow, x0, y, SEAM, 0.6)
    # the ball tip: polished, a hot spot on each face, glowing
    knob = patch("knob")
    gx.box(skin, knob, BRIGHT, 0.08)
    for fname, (x0, y0, x1, y1) in faces(*knob).items():
        skin.putpixel((x0, y0), (255, 214, 170, 255))
        if fname in SIDES + ("top",):
            glow_px(skin, glow, x0 + 1, y0 + 1, SEAM_HOT, 0.95)
            glow_px(skin, glow, x1 - 2, y1 - 2, SEAM, 0.6)

    # the spikes: dark copper, a glowing point
    spike = patch("spike")
    gx.box(skin, spike, DARK, 0.12, edge=0.8)
    for fname, (x0, y0, x1, y1) in faces(*spike).items():
        if fname in SIDES:
            glow_px(skin, glow, x0, y0, SEAM_HOT, 0.95)
            if y1 - y0 > 2:
                skin.putpixel((x0, y0 + 1), shade(DARK, 1.4))
                glow.putpixel((x0, y0 + 1), SEAM + (110,))

    # a few crackles of blue-white through the copper of the body and arms
    for name in ("body", "arm", "leg"):
        for fname, b in faces(*patch(name)).items():
            if fname in SIDES:
                x0, y0, x1, y1 = b
                x, y = gx.rng.randrange(x0, x1), gx.rng.randrange(y0, y1 - 2)
                for _ in range(gx.rng.randrange(2, 5)):
                    glow_px(skin, glow, x, y, SEAM, 0.6)
                    x = min(x1 - 1, max(x0, x + gx.rng.choice((-1, 0, 1))))
                    y = min(y1 - 1, y + 1)

    # the glow is only drawn where the skin is solid
    for y in range(SIZE):
        for x in range(SIZE):
            if skin.getpixel((x, y))[3] == 0:
                glow.putpixel((x, y), CLEAR)
    save(skin, "entity/xenoform_duplex_ultimate.png")
    save(glow, "entity/xenoform_duplex_ultimate_glow.png")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "layout":
        for key, (u, v) in UV.items():
            print('uv("%s", %d, %d),' % (key, u, v))
    else:
        paint()
        print("Copper Storm skins written under", gx.ROOT)
