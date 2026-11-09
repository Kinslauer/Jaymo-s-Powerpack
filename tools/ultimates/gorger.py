"""(0.21.37, Ultimates patch 2) Gorger's Ultimate, Worldmaw: its skin and glow map, 128x128, for client/.../ultimate/UltimateWorldmaw.java.

Gorger's dusky pink-brown armadillo hide, now under thick stone-grey plates: the six shell bands, a brow plate and a guard on each
leg, with a row of short bone spikes along each band and down the tail; a forked bone horn on a wider pale snout and a hinged jaw.
Ember orange (0xFF7828) light shows through the cracks between the bands: the glow map holds the seams along the bands' edges, the
cracks in the plates, the spike and horn tips, the eyes and the inside of the mouth (fullbright).

    python3 tools/ultimates/gorger.py            paint both skins
    python3 tools/ultimates/gorger.py layout     print the UV table (the Java model holds the same table)
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from PIL import Image  # noqa: E402

import gen_xenoform as gx  # noqa: E402
import gen_xeno_ultimates as gxu  # noqa: E402
from gen_xenoform import CLEAR, faces, rect, save, shade  # noqa: E402

SIZE = 128

# every box of the model, (w, h, d); boxes that look the same on both sides share one patch of the skin
DIMS = {
    "head": (5, 5, 3), "brow": (5, 1, 3), "snout": (4, 3, 3), "jaw": (4, 1, 3), "horn": (1, 3, 1), "ear": (2, 2, 1),
    "torso": (8, 5, 12),
    "band_f": (11, 9, 3), "band_b": (11, 8, 2), "band_c": (11, 9, 2), "band_r": (11, 9, 3), "spike": (1, 2, 1),
    "leg": (4, 5, 4), "greave": (5, 2, 5), "arm": (4, 12, 4),
    "tail": (3, 3, 5), "tail2": (2, 2, 4),
}
UV = gxu.pack(DIMS)

HIDE = (150, 98, 82)          # dusky pink-brown hide
PALE = (206, 156, 138)        # belly and snout
STONE = (116, 112, 108)       # stone-grey plate
STONE_D = (64, 60, 60)        # the rock under the plates
BONE = (226, 214, 190)
CLAW = (54, 36, 30)
EMBER = (255, 120, 40)        # 0xFF7828
HOT = (255, 202, 96)
DARK = (74, 30, 14, 255)      # what a seam looks like unlit
MOUTH = (120, 40, 36, 255)
SIDES = ("front", "back", "left", "right")

PLATES = ("band_f", "band_b", "band_c", "band_r", "brow", "greave", "tail", "tail2")
HIDES = ("head", "torso", "leg", "arm", "ear")


def patch(name):
    u, v = UV[name]
    w, h, d = DIMS[name]
    return (u, v, w, h, d)


def glow_px(skin, glow, x, y, color=EMBER, strength=1.0):
    """A glowing pixel: dark ember in the skin (so it reads as a hot crack in daylight) and the colour in the glow map."""
    skin.putpixel((x, y), shade(DARK, 1.0 + gx.rng.random() * 0.6))
    glow.putpixel((x, y), tuple(int(c) for c in color) + (int(255 * strength),))


def seam_rim(skin, glow, box, strength=0.75):
    """The ember line between bands: the top and bottom rim of a plate face glows, broken here and there."""
    x0, y0, x1, y1 = box
    for x in range(x0, x1):
        if (x + y1) % 3 != 0:
            glow_px(skin, glow, x, y1 - 1, EMBER, strength)
        if (x + y0) % 4 == 0 and y1 - y0 > 3:
            glow_px(skin, glow, x, y0, EMBER, strength * 0.7)


def cracks(skin, glow, box, n, length=(2, 5)):
    """Wandering one-pixel cracks of ember light kept inside the box."""
    x0, y0, x1, y1 = box
    if x1 - x0 < 3 or y1 - y0 < 3:
        return
    for _ in range(n):
        x, y = gx.rng.randrange(x0, x1), gx.rng.randrange(y0, y1)
        hot = gx.rng.random() < 0.25
        for _ in range(gx.rng.randrange(*length)):
            glow_px(skin, glow, x, y, HOT if hot else EMBER, 0.7 + gx.rng.random() * 0.3)
            x = min(x1 - 1, max(x0, x + gx.rng.choice((-1, 0, 1))))
            y = min(y1 - 1, max(y0, y + gx.rng.choice((-1, 0, 1))))


def plate(skin, glow, name):
    """A thick stone plate: bevelled, ruled across, bricks along its top, an ember seam along its edges and a crack or two."""
    part = patch(name)
    gx.box(skin, part, STONE, 0.16, edge=0.75)
    for fname, box in faces(*part).items():
        x0, y0, x1, y1 = box
        if x1 - x0 < 2 or y1 - y0 < 2:
            continue
        if fname in SIDES + ("top",):
            gxu.bevel(skin, box, shade(STONE, {"top": 1.25}.get(fname, 0.95)))
        if fname in SIDES and y1 - y0 >= 6:
            rect(skin, (x0 + 1, (y0 + y1) // 2, x1 - 1, (y0 + y1) // 2 + 1), shade(STONE, 0.55))
        if fname == "top":
            for y in range(y0, y1, 2):
                for x in range(x0 + (y - y0) % 4, x1, 4):
                    skin.putpixel((x, y), shade(STONE, 0.62))
        if fname in SIDES and name.startswith("band"):
            seam_rim(skin, glow, box)
            cracks(skin, glow, box, 1 + (x1 - x0) * (y1 - y0) // 40)
        elif fname in SIDES:
            cracks(skin, glow, box, 1, (2, 4))


def hide(skin, name, base=HIDE):
    part = patch(name)
    gx.box(skin, part, base, 0.16)
    for fname, (x0, y0, x1, y1) in faces(*part).items():
        if fname in SIDES and y1 - y0 >= 4:
            rect(skin, (x0, y0 + 2, x1, y0 + 3), shade(base, 0.72))
        elif fname == "bottom":
            gx.noise_fill(skin, PALE, 0.1, (x0, y0, x1, y1))


def paint():
    gx.rng.seed("gorger_ultimate")
    skin = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    glow = Image.new("RGBA", (SIZE, SIZE), CLEAR)

    # the hide: head, belly, legs, the first-person arm, ears
    for name in HIDES:
        hide(skin, name)
    for fname, (x0, y0, x1, y1) in faces(*patch("torso")).items():
        if fname in SIDES:
            gx.vgrad(skin, (x0, y0, x1, y1), (150, 96, 80), (206, 160, 142), 0.1)
    # claws: dark nails at the foot of every leg and the arm
    for name in ("leg", "arm"):
        for fname, (x0, y0, x1, y1) in faces(*patch(name)).items():
            if fname in SIDES:
                for x in range(x0, x1, 2):
                    rect(skin, (x, y1 - 1, x + 1, y1), CLAW)
            elif fname == "bottom":
                gx.noise_fill(skin, CLAW, 0.2, (x0, y0, x1, y1))
    # the ears: pink inside on the front
    ex0, ey0, ex1, ey1 = gx.front(patch("ear"))
    rect(skin, (ex0, ey0 + 1, ex1, ey1), (214, 150, 140, 255))

    # the head: beady black eyes with an ember spark, a stone brow over them
    fx0, fy0, fx1, fy1 = gx.front(patch("head"))
    for ex in (fx0, fx1 - 1):
        skin.putpixel((ex, fy0 + 1), (16, 12, 12, 255))
        glow_px(skin, glow, ex, fy0 + 1, HOT, 1.0)
    rx0, ry0, rx1, ry1 = faces(*patch("head"))["right"]
    lx0, ly0, lx1, ly1 = faces(*patch("head"))["left"]
    glow_px(skin, glow, rx1 - 1, ry0 + 1, EMBER, 0.8)
    glow_px(skin, glow, lx0, ly0 + 1, EMBER, 0.8)
    # the snout: pale and wide, two nostrils
    snout = patch("snout")
    gx.box(skin, snout, PALE, 0.1)
    sx0, sy0, sx1, sy1 = gx.front(snout)
    skin.putpixel((sx0 + 1, sy0 + 1), (70, 40, 36, 255))
    skin.putpixel((sx1 - 2, sy0 + 1), (70, 40, 36, 255))
    rect(skin, (sx0, sy1 - 1, sx1, sy1), shade(PALE, 0.78))
    # the jaw: the mouth inside glowing a faint ember, teeth along the front
    jaw = patch("jaw")
    gx.box(skin, jaw, shade(PALE, 0.9), 0.1)
    jt = faces(*jaw)["top"]
    rect(skin, jt, MOUTH)
    for x in range(jt[0] + 1, jt[2] - 1):
        for y in range(jt[1] + 1, jt[3] - 1):
            if (x + y) % 2 == 0:
                glow.putpixel((x, y), EMBER + (150,))
    jx0, jy0, jx1, jy1 = gx.front(jaw)
    for x in range(jx0, jx1):
        if x % 2 == 0:
            skin.putpixel((x, jy0), BONE)

    # the plates: the bands (the rump's carries the insignia on its back), brow, leg guards, the tail's rings
    for name in PLATES:
        plate(skin, glow, name)
    bx0, by0, bx1, by1 = faces(*patch("band_r"))["back"]
    gx.insignia(skin, (bx0 + bx1) // 2, by0 + 1, small=True, rim=STONE_D + (255,))
    for y in range(by0, by1):
        for x in range(bx0, bx1):
            glow.putpixel((x, y), CLEAR)

    # the spikes along the bands and the tail: bone, the point glowing
    spike = patch("spike")
    gx.box(skin, spike, BONE, 0.12, edge=0.8)
    for fname, (x0, y0, x1, y1) in faces(*spike).items():
        if fname in SIDES:
            glow_px(skin, glow, x0, y0, HOT, 0.95)
            glow_px(skin, glow, x0, y0 + 1, EMBER, 0.45)
    # the forked horn: bone, banded, the tip glowing
    horn = patch("horn")
    gx.box(skin, horn, BONE, 0.1, edge=0.85)
    for fname, (x0, y0, x1, y1) in faces(*horn).items():
        if fname in SIDES:
            glow_px(skin, glow, x0, y0, HOT, 1.0)
            if y1 - y0 > 2:
                rect(skin, (x0, y0 + 2, x1, y0 + 3), shade(BONE, 0.75))

    # the glow is only drawn where the skin is solid
    for y in range(SIZE):
        for x in range(SIZE):
            if skin.getpixel((x, y))[3] == 0:
                glow.putpixel((x, y), CLEAR)
    save(skin, "entity/xenoform_gorger_ultimate.png")
    save(glow, "entity/xenoform_gorger_ultimate_glow.png")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "layout":
        for key, (u, v) in UV.items():
            print('uv("%s", %d, %d),' % (key, u, v))
    else:
        paint()
        print("Worldmaw skins written under", gx.ROOT)
