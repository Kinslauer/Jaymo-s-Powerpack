"""(0.20.21, Ultimates patch 1) Gremlin's Ultimate, Swarm: its skin and glow map, 128x128, for client/.../ultimate/UltimateSwarm.java.

Gremlin's grey, warty hide (the same mottling, pale belly, black claws and yellow-eyed grin) with a plated brow and a crest of small
horns on the skull, plates on the shoulders, forearms, shins and over the knuckles, flank plates and spines down the back, and a
tail that grows barbs and a stinger. Green seams (0x9ADC5A) glow along the plates, round the horns and in the eyes. The glow map holds
the seams, the eyes and the tips of the horns and the stinger (fullbright).

    python3 tools/ultimates/gremlin.py            paint both skins
    python3 tools/ultimates/gremlin.py layout     print the UV table (the Java model holds the same table)
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
    "skull": (10, 10, 10), "brow": (10, 2, 5), "jaw": (7, 3, 6), "ear": (5, 6, 1),
    "torso": (7, 6, 9), "flank": (1, 4, 6), "spike": (1, 3, 1),
    "uarm": (3, 8, 3), "farm": (2, 8, 2), "fist": (3, 3, 4), "fplate": (4, 4, 5), "pauld": (5, 2, 5), "guard": (3, 3, 3),
    "thigh": (3, 6, 3), "shin": (2, 7, 2), "foot": (3, 2, 5),
    "tail": (1, 1, 5), "stinger": (1, 1, 3),
}
UV = gxu.pack(DIMS)

GREY = (118, 120, 126)
BELLY = (168, 166, 164)
WART = shade(GREY, 0.72)
PLATE = (78, 88, 76)          # dark green-grey iron
SPIKE = (58, 62, 56)
SEAM = (154, 220, 90)         # 0x9ADC5A
SEAM_HOT = (226, 255, 170)
EYE = (250, 230, 80)
DARK = (24, 36, 20, 255)
SIDES = ("front", "back", "left", "right")

PLATES = ("brow", "flank", "fplate", "pauld", "guard")
HIDE = ("torso", "uarm", "farm", "fist", "thigh", "shin", "foot")


def patch(name):
    u, v = UV[name]
    w, h, d = DIMS[name]
    return (u, v, w, h, d)


def glow_px(skin, glow, x, y, color=SEAM, strength=1.0):
    """A glowing seam pixel: a dark green in the skin (so it reads as a seam in daylight) and the colour in the glow map."""
    skin.putpixel((x, y), shade(DARK, 1.0 + gx.rng.random() * 0.5))
    glow.putpixel((x, y), tuple(int(c) for c in color) + (int(255 * strength),))


def seam_rim(skin, glow, box, strength=0.75):
    """The green line between plates: the bottom and right rim of a plate face glows."""
    x0, y0, x1, y1 = box
    for x in range(x0, x1):
        if (x + y1) % 3 != 0:
            glow_px(skin, glow, x, y1 - 1, SEAM, strength)
    for y in range(y0, y1 - 1):
        if (y + x1) % 3 != 0:
            glow_px(skin, glow, x1 - 1, y, SEAM, strength)


def plate(skin, glow, name, rim=True):
    """A raised plate: iron-green, bevelled, a green seam round its sides."""
    part = patch(name)
    gx.box(skin, part, PLATE, 0.2, edge=0.78)
    for fname, box in faces(*part).items():
        x0, y0, x1, y1 = box
        if x1 - x0 < 2 or y1 - y0 < 2:
            continue
        if fname in SIDES + ("top",):
            gxu.bevel(skin, box, shade(PLATE, {"top": 1.15}.get(fname, 0.92)))
        if rim and fname in SIDES:
            seam_rim(skin, glow, box)


def paint():
    gx.rng.seed("gremlin_ultimate")
    skin = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    glow = Image.new("RGBA", (SIZE, SIZE), CLEAR)

    # warts and mottling everywhere on the hide
    for name in HIDE:
        part = patch(name)
        gx.box(skin, part, GREY, 0.26)
        for fname, b in faces(*part).items():
            speckle(skin, b, WART, (b[2] - b[0]) * (b[3] - b[1]) // 6)
            speckle(skin, b, shade(GREY, 1.2), (b[2] - b[0]) * (b[3] - b[1]) // 10)
    torso = patch("torso")
    # a pale belly: the underside, and the lower rows of the sides and chest
    bx0, by0, bx1, by1 = faces(*torso)["bottom"]
    gx.vgrad(skin, (bx0, by0, bx1, by1), BELLY, shade(BELLY, 0.85), 0.12)
    for fname in ("front", "left", "right"):
        x0, y0, x1, y1 = faces(*torso)[fname]
        rect(skin, (x0, y1 - 2, x1, y1), shade(BELLY, 0.92))
    # the back: a dark spine-line down each edge, the insignia on top
    tx0, ty0, tx1, ty1 = faces(*torso)["top"]
    for y in range(ty0, ty1, 2):
        rect(skin, (tx0, y, tx0 + 1, y + 1), (60, 58, 62, 255))
        rect(skin, (tx1 - 1, y, tx1, y + 1), (60, 58, 62, 255))
    gx.insignia(skin, torso[0] + 12, torso[1] + 1, rim=(40, 40, 44, 255))
    # knobbly joints, calloused fists, black claws
    for name in ("farm", "shin"):
        for fname, (x0, y0, x1, y1) in faces(*patch(name)).items():
            if fname in SIDES:
                rect(skin, (x0, y0, x1, y0 + 1), WART)
    for name in ("uarm", "thigh"):
        for fname, (x0, y0, x1, y1) in faces(*patch(name)).items():
            if fname in SIDES:
                rect(skin, (x0, y1 - 1, x1, y1), WART)
    for fname, b in faces(*patch("fist")).items():
        speckle(skin, b, shade(GREY, 0.55), 5)
    gx.claws(skin, (28, 26, 30, 255), which=(patch("fist"), patch("foot")))

    # the skull: mottled, big yellow eyes with slit pupils (they glow), nostrils, a wide grin of white teeth
    skull = patch("skull")
    gx.box(skin, skull, shade(GREY, 1.04), 0.22)
    for fname, b in faces(*skull).items():
        speckle(skin, b, WART, 12)
        speckle(skin, b, shade(GREY, 1.25), 6)
    sx0, sy0, sx1, sy1 = gx.front(skull)
    rect(skin, (sx0 + 1, sy0 + 1, sx1 - 1, sy0 + 2), WART)
    for ex0, ex1 in ((sx0 + 1, sx0 + 4), (sx1 - 4, sx1 - 1)):
        rect(skin, (ex0, sy0 + 2, ex1, sy0 + 6), EYE + (255,))
        for y in range(sy0 + 2, sy0 + 6):
            for x in range(ex0, ex1):
                glow.putpixel((x, y), (255, 244, 120, 200))
    for px in (sx0 + 2, sx1 - 3):
        rect(skin, (px, sy0 + 3, px + 1, sy0 + 6), (20, 20, 20, 255))
        for y in range(sy0 + 3, sy0 + 6):
            glow.putpixel((px, y), CLEAR)
    for px in (sx0 + 1, sx1 - 2):
        skin.putpixel((px, sy0 + 2), (255, 250, 200, 255))
        glow.putpixel((px, sy0 + 2), (255, 255, 230, 255))
    skin.putpixel((sx0 + 4, sy0 + 7), (40, 36, 40, 255))
    skin.putpixel((sx1 - 5, sy0 + 7), (40, 36, 40, 255))
    rect(skin, (sx0 + 1, sy0 + 8, sx1 - 1, sy0 + 10), (50, 22, 26, 255))
    for x in range(sx0 + 1, sx1 - 1):
        skin.putpixel((x, sy0 + (8 if x % 2 == 0 else 9)), (240, 236, 220, 255))
    # the jaw: grey chin, a dark mouth inside it and a row of lower fangs
    jaw = patch("jaw")
    gx.box(skin, jaw, shade(GREY, 0.95), 0.2)
    for fname, b in faces(*jaw).items():
        speckle(skin, b, WART, 4)
    rect(skin, faces(*jaw)["top"], (50, 22, 26, 255))
    jx0, jy0, jx1, jy1 = gx.front(jaw)
    for x in range(jx0, jx1):
        if x % 2 == 1:
            skin.putpixel((x, jy0), (240, 236, 220, 255))
    # the ears: notched, lined with pink, a green seam round the rim
    ear = patch("ear")
    gx.box(skin, ear, shade(GREY, 0.95), 0.2)
    ex0, ey0, ex1, ey1 = gx.front(ear)
    rect(skin, (ex0 + 1, ey0 + 1, ex1 - 1, ey1 - 1), (214, 150, 150, 255))
    rect(skin, (ex0, ey0, ex0 + 1, ey0 + 1), CLEAR)
    rect(skin, (ex0 + 2, ey1 - 2, ex0 + 3, ey1 - 1), CLEAR)
    for x in range(ex0 + 1, ex1):
        if skin.getpixel((x, ey1 - 1))[3]:
            glow_px(skin, glow, x, ey1 - 1, SEAM, 0.55)

    # the plates: brow, flank, knuckles, shoulders and guards
    for name in PLATES:
        plate(skin, glow, name)
    # the brow's front is a ridge with a green line along its lower edge
    bx0, by0, bx1, by1 = gx.front(patch("brow"))
    rect(skin, (bx0, by0, bx1, by0 + 1), shade(PLATE, 0.5))
    # the knuckle plates: a ridge of three glowing studs across the top-front
    fx0, fy0, fx1, fy1 = gx.front(patch("fplate"))
    for x in range(fx0 + 1, fx1 - 1, 1):
        glow_px(skin, glow, x, fy0 + 1, SEAM_HOT if x % 2 == 0 else SEAM, 0.9)

    # the spikes (horn crest, spines, barbs): dark iron with a glowing point
    spike = patch("spike")
    gx.box(skin, spike, SPIKE, 0.12, edge=0.8)
    for fname, (x0, y0, x1, y1) in faces(*spike).items():
        if fname in SIDES:
            glow_px(skin, glow, x0, y0, SEAM_HOT, 0.95)
            if y1 - y0 > 2:
                skin.putpixel((x0, y0 + 1), shade(SEAM, 0.45))
                glow.putpixel((x0, y0 + 1), SEAM + (110,))

    # the tail: grey like the hide with ticks along it; the stinger dark, going to a glowing point
    tail = patch("tail")
    gx.box(skin, tail, GREY, 0.2)
    for fname, (x0, y0, x1, y1) in faces(*tail).items():
        if fname in ("left", "right", "top", "bottom"):
            for x in range(x0, x1, 2):
                skin.putpixel((x, y0), WART)
    sting = patch("stinger")
    gx.box(skin, sting, SPIKE, 0.12, edge=0.8)
    for fname, (x0, y0, x1, y1) in faces(*sting).items():
        if fname in ("left", "right", "top", "bottom"):
            for x in range(x0, x1):
                t = (x - x0) / max(1, x1 - x0 - 1)
                if t > 0.4:
                    glow_px(skin, glow, x, y0, SEAM_HOT if t > 0.8 else SEAM, 0.5 + 0.5 * t)
    # a seam down each side of the flank plates' bevel is already drawn; a few cracks of green across the back plates of the hide
    for fname, b in faces(*torso).items():
        if fname in SIDES:
            x0, y0, x1, y1 = b
            for _ in range(2):
                x, y = gx.rng.randrange(x0, x1), gx.rng.randrange(y0, y1 - 2)
                for _ in range(gx.rng.randrange(2, 5)):
                    glow_px(skin, glow, x, y, SEAM, 0.7)
                    x = min(x1 - 1, max(x0, x + gx.rng.choice((-1, 0, 1))))
                    y = min(y1 - 1, y + 1)

    # the glow is only drawn where the skin is solid
    for y in range(SIZE):
        for x in range(SIZE):
            if skin.getpixel((x, y))[3] == 0:
                glow.putpixel((x, y), CLEAR)
    save(skin, "entity/xenoform_gremlin_ultimate.png")
    save(glow, "entity/xenoform_gremlin_ultimate_glow.png")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "layout":
        for key, (u, v) in UV.items():
            print('uv("%s", %d, %d),' % (key, u, v))
    else:
        paint()
        print("Swarm skins written under", gx.ROOT)
