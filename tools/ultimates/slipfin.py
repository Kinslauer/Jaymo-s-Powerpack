"""(0.21.37, Ultimates patch 2) Slipfin's Ultimate, Brinecrown: its skin and glow map, 128x128, for client/.../ultimate/UltimateBrinecrown.java.

Slipfin's pink axolotl grown long and plated: pearly scale plates down the back, a spiked fin sail along the spine and tail, six long
frilled gill crowns, spikes at the brow and cheeks. The seams between the plates, the sail's edge, the gill tips and the spike points
glow coral pink (0xF08CB8): the glow map is drawn fullbright by the layer. The insignia is set in the front trunk's back.

    python3 tools/ultimates/slipfin.py            paint both skins
    python3 tools/ultimates/slipfin.py layout     print the UV table (the Java model holds the same table)

128x128, shelf-packed from DIMS (the model's boxes, (w, h, d)); boxes that look the same share one patch of the skin.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from PIL import Image  # noqa: E402

import gen_xenoform as gx  # noqa: E402
import gen_xeno_ultimates as gxu  # noqa: E402
from gen_xenoform import CLEAR, faces, rect, save, shade  # noqa: E402

SIZE = 128

# every box of the model, (w, h, d)
DIMS = {
    "hub": (6, 4, 8), "rear": (6, 4, 8), "skull": (9, 3, 9), "jaw": (7, 1, 5),
    "gill": (6, 1, 2), "gfan": (1, 4, 2), "gtip": (2, 3, 2), "leg": (2, 3, 3),
    "t0": (4, 5, 6), "t1": (3, 5, 6), "t2": (2, 4, 6), "t3": (2, 3, 5),
    "scale": (5, 1, 3), "sail": (1, 4, 3), "sails": (1, 3, 2), "spike": (1, 3, 1),
}
UV = gxu.pack(DIMS)

PINK = (238, 150, 186)       # Slipfin's skin
PEARL = (244, 226, 236)      # the scale plates
ROSE = (150, 60, 96)         # seams and the dark of the plates
CORAL = (240, 140, 184)      # 0xF08CB8, the glow
HOT = (255, 214, 232)
SAIL = (184, 72, 124)
BELLY = (250, 206, 220)
SIDES = ("front", "back", "left", "right")


def patch(name):
    u, v = UV[name]
    w, h, d = DIMS[name]
    return (u, v, w, h, d)


def glow_px(skin, glow, x, y, color=CORAL, strength=1.0):
    """A glowing pixel: the colour in the skin and in the glow map (the map is drawn fullbright)."""
    skin.putpixel((x, y), shade(color, 0.85 + gx.rng.random() * 0.2))
    glow.putpixel((x, y), tuple(int(c) for c in color) + (int(255 * strength),))


def flesh(skin, name, tint=1.0, spots=14):
    """Pink, lit per face, with a wet gradient and darker spots."""
    base = PINK + (255,)
    for fname, box in faces(*patch(name)).items():
        gx.vgrad(skin, box, shade(base, gx.LIGHT[fname] * 1.08 * tint), shade(base, gx.LIGHT[fname] * 0.9 * tint), 0.06)
        x0, y0, x1, y1 = box
        gx.speckle(skin, box, shade(base, 0.78), max(1, (x1 - x0) * (y1 - y0) // spots))
        if fname in SIDES and x1 - x0 > 2:
            skin.putpixel((x0 + 1, y0), (255, 236, 244, 255))


def belly(skin, name, rows=1):
    f = faces(*patch(name))
    gx.vgrad(skin, f["bottom"], BELLY, (240, 186, 204), 0.05)
    for fname in SIDES:
        x0, y0, x1, y1 = f[fname]
        gx.vgrad(skin, (x0, y1 - rows, x1, y1), (248, 200, 216), (252, 214, 226), 0.04)


def seam_rim(skin, glow, box, strength=0.8):
    """The glowing seam along a plate face's bottom and right edge, broken every few pixels."""
    x0, y0, x1, y1 = box
    for x in range(x0, x1):
        if (x + y1) % 4 != 0:
            glow_px(skin, glow, x, y1 - 1, CORAL, strength)
    for y in range(y0, y1 - 1):
        if (y + x1) % 3 != 0:
            glow_px(skin, glow, x1 - 1, y, CORAL, strength)


def paint():
    gx.rng.seed("slipfin_ultimate")
    skin = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    glow = Image.new("RGBA", (SIZE, SIZE), CLEAR)

    # the trunks: pink with a darker stripe down the back; the front one carries the insignia
    for name in ("hub", "rear"):
        flesh(skin, name)
        belly(skin, name)
        tx0, ty0, tx1, ty1 = faces(*patch(name))["top"]
        gx.rect(skin, ((tx0 + tx1) // 2 - 1, ty0, (tx0 + tx1) // 2 + 1, ty1), shade(PINK + (255,), 0.8))
    tx0, ty0, tx1, ty1 = faces(*patch("hub"))["top"]
    gx.insignia(skin, (tx0 + tx1) // 2, ty0 + 2, small=True, rim=(120, 40, 80, 255))
    # a coral seam along each flank of the trunks, between the pink above and the pale belly below
    for name in ("hub", "rear"):
        for fname in ("left", "right"):
            x0, y0, x1, y1 = faces(*patch(name))[fname]
            for x in range(x0, x1):
                if x % 3 != 0:
                    glow_px(skin, glow, x, y1 - 2, CORAL, 0.55)

    # the head: wide and flat, eyes on top by the front edge, nostrils, a blush, the roof of the mouth
    flesh(skin, "skull", spots=18)
    sf = faces(*patch("skull"))
    ex0, ey0, ex1, ey1 = sf["top"]
    for ex in (ex0 + 1, ex1 - 2):
        skin.putpixel((ex, ey1 - 3), shade(PINK + (255,), 0.62))
        skin.putpixel((ex, ey1 - 2), (16, 12, 20, 255))
        glow.putpixel((ex, ey1 - 2), HOT + (150,))
    fx0, fy0, fx1, fy1 = sf["front"]
    skin.putpixel((fx0 + 2, fy0 + 1), ROSE + (255,))
    skin.putpixel((fx1 - 3, fy0 + 1), ROSE + (255,))
    for fname in ("left", "right"):
        x0, y0, x1, y1 = sf[fname]
        rect(skin, (x1 - 3, y0 + 1, x1 - 1, y0 + 2), (232, 110, 150, 255))
    rect(skin, sf["bottom"], shade((170, 70, 100), 1.0))
    gx.speckle(skin, sf["bottom"], (196, 96, 122, 255), 8)
    flesh(skin, "jaw", spots=10)
    belly(skin, "jaw", 0)
    jf = faces(*patch("jaw"))
    rect(skin, jf["top"], (170, 70, 100, 255))
    jx0, jy0, jx1, jy1 = jf["top"]
    rect(skin, (jx0 + 1, jy0 + 1, jx1 - 1, jy1 - 1), (220, 112, 134, 255))
    for x in range(jx0 + 1, jx1 - 1):
        for y in range(jy0 + 1, jy1 - 1):
            if (x + y) % 3 == 0:
                glow.putpixel((x, y), CORAL + (110,))

    # the gill crowns: a long stalk, a frill standing up off it, a bright glowing tip
    gill = patch("gill")
    gx.box(skin, gill, (214, 56, 132), 0.16)
    for fname, (x0, y0, x1, y1) in faces(*gill).items():
        for x in range(x0, x1):
            if (x - x0) % 2 == 1:
                rect(skin, (x, y0, x + 1, y1), shade((255, 110, 176), gx.LIGHT[fname] * 0.95))
        if fname in SIDES + ("top",):
            for x in range(x0, x1, 3):
                glow_px(skin, glow, x, y0, CORAL, 0.55)
    fan = patch("gfan")
    gx.box(skin, fan, (236, 92, 156), 0.14)
    for fname, (x0, y0, x1, y1) in faces(*fan).items():
        for y in range(y0, y1):
            if (y - y0) % 2 == 0:
                glow_px(skin, glow, x0, y, CORAL, 0.45)
    tip = patch("gtip")
    gx.box(skin, tip, CORAL, 0.1)
    for fname, (x0, y0, x1, y1) in faces(*tip).items():
        for y in range(y0, y1):
            for x in range(x0, x1):
                r, g, b, a = skin.getpixel((x, y))
                glow.putpixel((x, y), (r, g, b, 235))

    # legs: pink paddles with darker toes
    flesh(skin, "leg", spots=8)
    px0, py0, px1, py1 = faces(*patch("leg"))["bottom"]
    rect(skin, (px0, py0, px1, py1), (196, 100, 140, 255))
    for x in range(px0, px1):
        skin.putpixel((x, py0), ROSE + (255,))

    # the tail: deep and flat, a pale fin edge top and bottom, a coral seam along its middle
    for i, name in enumerate(("t0", "t1", "t2", "t3")):
        flesh(skin, name, tint=1.0 - 0.04 * i, spots=12)
        for fname, (x0, y0, x1, y1) in faces(*patch(name)).items():
            if fname in ("left", "right"):
                rect(skin, (x0, y1 - 1, x1, y1), (250, 196, 216, 255))
                for x in range(x0, x1):
                    if (x + i) % 3 != 0:
                        glow_px(skin, glow, x, (y0 + y1) // 2, CORAL, 0.5)

    # the scale plates: pearl, bevelled, a shaded rim, a coral seam round the edge
    sc = patch("scale")
    gx.box(skin, sc, PEARL, 0.12, edge=0.8)
    for fname, box in faces(*sc).items():
        x0, y0, x1, y1 = box
        if fname == "top":
            gxu.bevel(skin, box, shade(PEARL, 1.0))
            for y in range(y0 + 1, y1 - 1):
                for x in range(x0 + 1, x1 - 1):
                    if (x + y) % 3 == 0:
                        skin.putpixel((x, y), shade(PEARL, 0.88))
            for x in range(x0, x1):
                glow_px(skin, glow, x, y1 - 1, CORAL, 0.75)
        elif fname in SIDES:
            for x in range(x0, x1):
                glow_px(skin, glow, x, y1 - 1, CORAL, 0.8)

    # the fin sail: dark rose with a coral edge, pearl at the points
    for name in ("sail", "sails"):
        part = patch(name)
        gx.box(skin, part, SAIL, 0.16, edge=0.8)
        for fname, (x0, y0, x1, y1) in faces(*part).items():
            if fname in SIDES:
                for y in range(y0, y1):
                    t = (y - y0) / max(1, y1 - y0 - 1)
                    for x in range(x0, x1):
                        skin.putpixel((x, y), gx.lerp(PEARL, SAIL, min(1.0, t * 1.3)))
                glow_px(skin, glow, x0, y0, HOT, 1.0)
                if y1 - y0 > 2:
                    glow_px(skin, glow, x0, y0 + 1, CORAL, 0.6)
                for y in range(y0 + 2, y1):
                    if (y + x0) % 2 == 0:
                        glow_px(skin, glow, x1 - 1, y, CORAL, 0.4)

    # the spikes at the brow and cheeks: pearl, the point glowing
    spike = patch("spike")
    gx.box(skin, spike, PEARL, 0.1, edge=0.85)
    for fname, (x0, y0, x1, y1) in faces(*spike).items():
        if fname in SIDES:
            glow_px(skin, glow, x0, y0, HOT, 1.0)
            glow_px(skin, glow, x0, y0 + 1, CORAL, 0.5)

    # the glow is drawn only where the skin is solid
    for y in range(SIZE):
        for x in range(SIZE):
            if skin.getpixel((x, y))[3] == 0:
                glow.putpixel((x, y), CLEAR)
    save(skin, "entity/xenoform_slipfin_ultimate.png")
    save(glow, "entity/xenoform_slipfin_ultimate_glow.png")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "layout":
        for key, (u, v) in UV.items():
            print('uv("%s", %d, %d),' % (key, u, v))
    else:
        paint()
        print("Brinecrown skins written under", gx.ROOT)
