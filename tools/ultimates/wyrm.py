"""(0.21.37, Ultimates patch 2) Wyrm's Ultimate, Elder Wyrm: its skin and glow map, 128x128, for client/.../ultimate/UltimateElderWyrm.java.

Wyrm's dark violet scales in a brick of lighter crescents with pale belly plates, now under black scale plates down the back and
along the flanks, guards on the thighs and a brow plate; long spikes of black horn; a barbed blade for the spade; a swept-back crown
of bone horns. Void purple seams (0xC060E0) glow along the plates, in the cracks between the scales, at the tips of the spikes,
horns and barbs and in the eyes. The glow map holds exactly those (fullbright).

    python3 tools/ultimates/wyrm.py            paint both skins
    python3 tools/ultimates/wyrm.py layout     print the UV table (the Java model holds the same table)
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
    "torso": (7, 6, 11), "chest": (7, 7, 4), "neck1": (3, 3, 6), "neck2": (3, 3, 5), "skull": (5, 4, 5), "snout": (3, 2, 4),
    "jaw": (3, 1, 6), "horn": (2, 2, 7), "crown": (1, 1, 5), "brow": (6, 1, 4),
    "tail1": (4, 4, 7), "tail2": (3, 3, 7), "tail3": (2, 2, 6), "blade": (1, 7, 6), "cross": (7, 1, 5), "barb": (1, 3, 2),
    "thigh": (3, 5, 3), "shin": (2, 5, 2), "foot": (3, 2, 4), "spike": (1, 4, 2),
    "plate": (5, 1, 3), "flank": (1, 4, 4), "guard": (4, 3, 4), "arm": (4, 12, 4),
}
UV = gxu.pack(DIMS)

SCALE = (30, 22, 42)          # dark violet scale
BELLY = (104, 90, 120)        # pale belly plates
PLATE = (24, 20, 32)          # black scale plate
SPIKE = (20, 16, 26)          # black horn
BONE = (176, 164, 154)        # the crown, claws, teeth
SEAM = (192, 96, 224)         # 0xC060E0
SEAM_HOT = (240, 196, 255)
DARK = (28, 12, 40, 255)      # what a seam looks like unlit
MOUTH = (104, 36, 66, 255)
SIDES = ("front", "back", "left", "right")

SCALED = ("torso", "chest", "neck1", "neck2", "skull", "snout", "tail1", "tail2", "tail3", "thigh", "shin", "foot", "arm")
BELLIED = {"torso": ("bottom",), "chest": ("bottom", "front"), "neck1": ("bottom",), "neck2": ("bottom",), "tail1": ("bottom",),
           "tail2": ("bottom",), "arm": ("front",)}
PLATES = ("plate", "flank", "guard", "brow")
SPIKES = ("spike",)
BONES = ("horn", "crown")
BLADES = ("blade", "cross", "barb")


def patch(name):
    u, v = UV[name]
    w, h, d = DIMS[name]
    return (u, v, w, h, d)


def glow_px(skin, glow, x, y, color=SEAM, strength=1.0):
    """A glowing seam pixel: a dark violet in the skin (so it reads as a seam in daylight) and the colour in the glow map."""
    skin.putpixel((x, y), shade(DARK, 1.0 + gx.rng.random() * 0.5))
    glow.putpixel((x, y), tuple(int(c) for c in color) + (int(255 * strength),))


def seam_rim(skin, glow, box, strength=0.75):
    """The violet line between plates: the bottom and right rim of a plate face glows."""
    x0, y0, x1, y1 = box
    for x in range(x0, x1):
        if (x + y1) % 3 != 0:
            glow_px(skin, glow, x, y1 - 1, SEAM, strength)
    for y in range(y0, y1 - 1):
        if (y + x1) % 3 != 0:
            glow_px(skin, glow, x1 - 1, y, SEAM, strength)


def scales(skin, name):
    """The brick of lighter crescents on every face of a box; the faces named in BELLIED are pale plates instead."""
    part = patch(name)
    gx.box(skin, part, SCALE, 0.25)
    for fname, (x0, y0, x1, y1) in faces(*part).items():
        if fname in BELLIED.get(name, ()):
            for y in range(y0, y1, 2):
                rect(skin, (x0, y, x1, y + 1), shade(BELLY, (0.9 + ((y - y0) // 2) % 2 * 0.14) * gx.LIGHT[fname] ** 0.5))
                rect(skin, (x0, y + 1, x1, min(y + 2, y1)), shade(SCALE, 0.8))
            continue
        for y in range(y0, y1, 3):
            for x in range(x0 + (y // 3) % 2 * 2, x1, 4):
                rect(skin, (x, y, min(x + 2, x1), y + 1), shade(SCALE, 1.35 * gx.LIGHT[fname]))
                if x + 1 < x1 and y + 1 < y1:
                    skin.putpixel((x + 1, y + 1), shade(SCALE, 0.7))


def plate(skin, glow, name, rim=True):
    """A raised black plate, bevelled, a violet seam round its sides."""
    part = patch(name)
    gx.box(skin, part, PLATE, 0.2, edge=0.78)
    for fname, box in faces(*part).items():
        x0, y0, x1, y1 = box
        if x1 - x0 < 2 or y1 - y0 < 2:
            continue
        if fname in SIDES + ("top",):
            gxu.bevel(skin, box, shade(PLATE, {"top": 1.4}.get(fname, 1.1)))
        if rim and fname in SIDES:
            seam_rim(skin, glow, box)


def cracks(skin, glow, box, n, length=(2, 5)):
    """Wandering one-pixel cracks of void light between the scales."""
    x0, y0, x1, y1 = box
    if x1 - x0 < 3 or y1 - y0 < 3:
        return
    for _ in range(n):
        x, y = gx.rng.randrange(x0, x1), gx.rng.randrange(y0, y1)
        hot = gx.rng.random() < 0.25
        for _ in range(gx.rng.randrange(*length)):
            glow_px(skin, glow, x, y, SEAM_HOT if hot else SEAM, 0.7 + gx.rng.random() * 0.3)
            x = min(x1 - 1, max(x0, x + gx.rng.choice((-1, 0, 1))))
            y = min(y1 - 1, max(y0, y + gx.rng.choice((-1, 0, 1))))


def paint():
    gx.rng.seed("wyrm_ultimate")
    skin = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    glow = Image.new("RGBA", (SIZE, SIZE), CLEAR)

    # the hide: dark violet scales, pale belly plates, a darker band down the middle of the back and a few cracks of void light
    for name in SCALED:
        scales(skin, name)
    for name in ("torso", "tail1", "tail2", "neck1", "neck2"):
        x0, y0, x1, y1 = faces(*patch(name))["top"]
        rect(skin, ((x0 + x1 - 1) // 2, y0, (x0 + x1 - 1) // 2 + 1, y1), shade(SCALE, 0.55))
    for name in ("torso", "chest", "tail1", "thigh", "arm"):
        for fname, b in faces(*patch(name)).items():
            if fname in SIDES + ("top",):
                cracks(skin, glow, b, 2 if name in ("torso", "chest") else 1)
    # the claws: bone at the foot of every foot and arm
    for name in ("foot", "arm"):
        for fname, (x0, y0, x1, y1) in faces(*patch(name)).items():
            if fname in SIDES:
                for x in range(x0, x1, 2):
                    rect(skin, (x, y1 - 1, x + 1, y1), BONE)
            elif fname == "bottom":
                gx.noise_fill(skin, BONE, 0.2, (x0, y0, x1, y1))

    # the head: glowing slit eyes, a pale snout with nostrils, the mouth and its teeth inside the jaw
    sx0, sy0, sx1, sy1 = gx.front(patch("skull"))
    rect(skin, (sx0, sy0 + 1, sx0 + 2, sy0 + 2), (255, 255, 255, 255))
    rect(skin, (sx1 - 2, sy0 + 1, sx1, sy0 + 2), (255, 255, 255, 255))
    for ex in (sx0, sx1 - 2):
        for dx in range(2):
            glow_px(skin, glow, ex + dx, sy0 + 1, SEAM_HOT, 1.0)
    skin.putpixel((sx0 + 2, sy0 + 3), shade(SCALE, 0.5))
    nx0, ny0, nx1, ny1 = gx.front(patch("snout"))
    rect(skin, (nx0, ny0, nx1, ny1), shade(SCALE, 1.1))
    skin.putpixel((nx0, ny0), shade(SCALE, 0.4))
    skin.putpixel((nx1 - 1, ny0), shade(SCALE, 0.4))
    jaw = patch("jaw")
    gx.box(skin, jaw, shade(SCALE, 0.9), 0.2)
    rect(skin, faces(*jaw)["top"], MOUTH)
    jx0, jy0, jx1, jy1 = gx.front(jaw)
    for x in range(jx0, jx1):
        if x % 2 == 0:
            skin.putpixel((x, jy0), BONE)

    # the plates: back, flanks, thigh guards and the brow
    for name in PLATES:
        plate(skin, glow, name)

    # the spikes along the back, neck and tail: black horn with a glowing point
    spike = patch("spike")
    gx.box(skin, spike, SPIKE, 0.12, edge=0.8)
    for fname, (x0, y0, x1, y1) in faces(*spike).items():
        if fname in SIDES:
            glow_px(skin, glow, x0, y0, SEAM_HOT, 0.95)
            glow_px(skin, glow, x0, y0 + 1, SEAM, 0.55)
            if x1 - x0 > 1:
                glow_px(skin, glow, x1 - 1, y0, SEAM_HOT, 0.95)

    # the crown and the horns: bone, banded, point glowing
    for name in BONES:
        part = patch(name)
        gx.box(skin, part, BONE, 0.16, edge=0.8)
        for fname, (x0, y0, x1, y1) in faces(*part).items():
            if fname in SIDES + ("top", "bottom"):
                for y in range(y0, y1):
                    for x in range(x0, x1):
                        if (x + y) % 5 == 0:
                            skin.putpixel((x, y), shade(BONE, 0.7))
        # the point is the far end along the horn's length (depth d): the left/right/top/bottom faces' last columns are the point
        w, h, d = DIMS[name]
        for fname, (x0, y0, x1, y1) in faces(*part).items():
            if fname in ("top", "bottom"):
                for y in range(y0, min(y0 + 2, y1)):
                    for x in range(x0, x1):
                        glow_px(skin, glow, x, y, SEAM_HOT if y == y0 else SEAM, 0.9 if y == y0 else 0.55)
            elif fname in ("left", "right"):
                for y in range(y0, y1):
                    for x in (x0, x0 + 1):
                        if x < x1:
                            glow_px(skin, glow, x, y, SEAM_HOT if x == x0 else SEAM, 0.9 if x == x0 else 0.55)

    # the blade and its barbs: black steel-violet, a glowing edge all round
    for name in BLADES:
        part = patch(name)
        gx.box(skin, part, PLATE, 0.18, edge=0.75)
        for fname, b in faces(*part).items():
            x0, y0, x1, y1 = b
            if fname in SIDES + ("top", "bottom"):
                for x in range(x0, x1):
                    glow_px(skin, glow, x, y1 - 1, SEAM, 0.8)
                for y in range(y0, y1):
                    if (y + x0) % 2 == 0:
                        glow_px(skin, glow, x1 - 1, y, SEAM_HOT, 0.9)

    # the glow is only drawn where the skin is solid
    for y in range(SIZE):
        for x in range(SIZE):
            if skin.getpixel((x, y))[3] == 0:
                glow.putpixel((x, y), CLEAR)
    save(skin, "entity/xenoform_wyrm_ultimate.png")
    save(glow, "entity/xenoform_wyrm_ultimate_glow.png")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "layout":
        for key, (u, v) in UV.items():
            print('uv("%s", %d, %d),' % (key, u, v))
    else:
        paint()
        print("Elder Wyrm skins written under", gx.ROOT)
