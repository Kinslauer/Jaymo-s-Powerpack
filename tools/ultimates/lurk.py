"""(0.21.37, Ultimates patch 2) Lurk's Ultimate, Nightfall: its skin and glow map, 128x128, for client/.../ultimate/UltimateNightfall.java.

Lurk's own bruise-purple hide, long grey fingers and pale eyes, grown: bone-black plates on the shoulders, the forearms and down the
spine, a ridge of thin spikes, four eye domes, and violet seams (0x6E52B0) threaded through the hide and along the plate edges. The glow
map holds every seam and the eyes (the layer draws it fullbright and the model breathes it slowly).

    python3 tools/ultimates/lurk.py            paint both skins
    python3 tools/ultimates/lurk.py layout     print the UV table (the Java model holds the same table)
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
    "head": (6, 7, 6), "eye": (3, 3, 1), "dome": (2, 2, 1), "neck": (3, 4, 3),
    "torso": (5, 11, 3), "spine": (2, 11, 1), "spike": (1, 4, 1),
    "arm": (2, 9, 2), "pauldron": (4, 2, 4), "fore": (2, 10, 2), "bracer": (3, 5, 3), "hand": (3, 6, 2),
    "thigh": (2, 9, 2), "shin": (2, 8, 2), "foot": (3, 2, 5),
}
UV = gxu.pack(DIMS)

DARK = (40, 32, 56)          # the bruise-purple hide
PLATE = (16, 13, 24)         # bone-black plate
VIOLET = (110, 82, 176)      # 0x6E52B0
HOT = (176, 150, 238)
PALE = (236, 232, 248)
GREY = (96, 92, 108)
TIP = (20, 16, 26)

PLATES = ("pauldron", "bracer", "spine")
HIDE = ("head", "neck", "torso", "arm", "fore", "thigh", "shin")


def patch(name):
    u, v = UV[name]
    w, h, d = DIMS[name]
    return (u, v, w, h, d)


def lit(skin, glow, x, y, color, strength=1.0):
    """A glowing pixel: a dim violet in the skin (a seam in daylight) and the colour in the glow map."""
    skin.putpixel((x, y), shade(color, 0.55 + gx.rng.random() * 0.2))
    glow.putpixel((x, y), tuple(int(c) for c in color) + (int(255 * strength),))


def veins(skin, glow, box, n, length=(3, 6)):
    """Wandering one-pixel veins of violet light inside the box, in the skin and the glow."""
    x0, y0, x1, y1 = box
    if x1 <= x0 or y1 <= y0:
        return
    for _ in range(n):
        x, y = gx.rng.randrange(x0, x1), gx.rng.randrange(y0, y1)
        hot = gx.rng.random() < 0.25
        for _ in range(gx.rng.randrange(*length)):
            lit(skin, glow, x, y, HOT if hot else VIOLET, 0.8 + gx.rng.random() * 0.2)
            x = min(x1 - 1, max(x0, x + gx.rng.choice((-1, 0, 1))))
            y = min(y1 - 1, max(y0, y + gx.rng.choice((-1, 0, 1))))


def bevel(skin, box, base):
    """A lit top-left and a shaded bottom-right rim: reads as a raised plate."""
    x0, y0, x1, y1 = box
    if x1 - x0 < 3 or y1 - y0 < 3:
        return
    rect(skin, (x0, y0, x1, y0 + 1), shade(base, 2.0))
    rect(skin, (x0, y0, x0 + 1, y1), shade(base, 1.6))
    rect(skin, (x0, y1 - 1, x1, y1), shade(base, 0.6))
    rect(skin, (x1 - 1, y0, x1, y1), shade(base, 0.7))


def seam_rim(skin, glow, box, strength=0.75):
    """The violet line between plates: the bottom and right rim of a plate face glows, in dashes."""
    x0, y0, x1, y1 = box
    for x in range(x0, x1):
        if (x + y1) % 3 != 0:
            lit(skin, glow, x, y1 - 1, VIOLET, strength)
    for y in range(y0, y1 - 1):
        if (y + x1) % 3 != 0:
            lit(skin, glow, x1 - 1, y, VIOLET, strength)


def paint():
    """Lurk's Ultimate: bruise-purple hide threaded with violet light under bone-black plates, grey fingers, four pale eyes."""
    gx.rng.seed("lurk_ultimate")
    skin = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    glow = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    for name in DIMS:
        part = patch(name)
        if name == "spike":
            for face, b in faces(*part).items():
                gx.vgrad(skin, b, shade(PLATE, 1.6), shade(PLATE, 0.8), 0.08)
                if face in ("front", "back", "left", "right"):
                    lit(skin, glow, b[0], b[1], HOT, 0.9)
            continue
        if name in ("eye", "dome"):
            gx.box(skin, part, VIOLET, 0.1)
            for face, b in faces(*part).items():
                if face != "front":
                    gx.noise_fill(skin, shade(VIOLET, 0.5), 0.1, b)
            x0, y0, x1, y1 = faces(*part)["front"]
            rect(skin, (x0, y0, x1, y1), shade(VIOLET, 1.0))
            for cx, cy in ((x0, y0), (x1 - 1, y0), (x0, y1 - 1), (x1 - 1, y1 - 1)):
                skin.putpixel((cx, cy), shade(VIOLET, 0.7))
            mx, my = (x0 + x1) // 2, (y0 + y1) // 2
            skin.putpixel((mx, my), PALE + (255,))
            for y in range(y0, y1):
                for x in range(x0, x1):
                    glow.putpixel((x, y), (tuple(PALE) if (x, y) == (mx, my) else tuple(HOT)) + (255,))
            continue
        if name == "hand":
            gx.box(skin, part, GREY, 0.18)
            for face, (x0, y0, x1, y1) in faces(*part).items():
                if face in ("front", "back", "left", "right"):
                    rect(skin, (x0, y1 - 1, x1, y1), TIP + (255,))
            continue
        base = PLATE if name in PLATES else shade(DARK, {"neck": 0.85, "fore": 0.9, "shin": 0.9}.get(name, 1.0))
        gx.box(skin, part, base, 0.18 if name in PLATES else 0.2, edge=0.7 if name in PLATES else None)
        for face, box in faces(*part).items():
            if name in PLATES:
                if face in ("front", "back", "left", "right", "top"):
                    bevel(skin, box, base)
                if face in ("front", "back", "left", "right"):
                    seam_rim(skin, glow, box)
            elif name in HIDE:
                veins(skin, glow, box, 1 if face in ("top", "bottom") else max(1, (box[2] - box[0]) * (box[3] - box[1]) // 12))
    # the head: pale small eyes round the domes, a thin mouth under them
    hx0, hy0, hx1, hy1 = faces(*patch("head"))["front"]
    rect(skin, (hx0, hy0, hx1, hy1), shade(DARK, 0.8))
    for x, y in ((hx0 + 1, hy0), (hx1 - 2, hy0), (hx0, hy0 + 5), (hx1 - 1, hy0 + 5)):
        skin.putpixel((x, y), (214, 206, 236, 255))
        glow.putpixel((x, y), (214, 206, 236, 150))
    rect(skin, (hx0 + 1, hy1 - 1, hx1 - 1, hy1), TIP + (255,))
    for side in ("right", "left"):
        x0, y0, x1, y1 = faces(*patch("head"))[side]
        skin.putpixel(((x0 + x1) // 2, y0 + 2), (214, 206, 236, 255))
        glow.putpixel(((x0 + x1) // 2, y0 + 2), (214, 206, 236, 150))
    # the torso: thin ribs down the sides of the chest
    for face, (x0, y0, x1, y1) in faces(*patch("torso")).items():
        if face in ("front", "left", "right"):
            for y in range(y0 + 2, y1 - 1, 3):
                rect(skin, (x0, y, x1, y + 1), shade(DARK, 0.6))
    # pale knuckle bands round the wrists
    for name, rows in (("fore", 1), ("shin", 1)):
        for face, (x0, y0, x1, y1) in faces(*patch(name)).items():
            if face in ("front", "back", "left", "right"):
                rect(skin, (x0, y1 - rows, x1, y1), shade(GREY, 0.8))
    for face, b in faces(*patch("foot")).items():
        if face == "bottom":
            gx.noise_fill(skin, (30, 26, 38, 255), 0.2, b)
    # the glow is drawn only where the skin is solid
    for y in range(SIZE):
        for x in range(SIZE):
            if skin.getpixel((x, y))[3] == 0:
                glow.putpixel((x, y), CLEAR)
    save(skin, "entity/xenoform_lurk_ultimate.png")
    save(glow, "entity/xenoform_lurk_ultimate_glow.png")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "layout":
        for key, (u, v) in UV.items():
            print('uv("%s", %d, %d),' % (key, u, v))
    else:
        paint()
        print("Lurk's Ultimate skins written under", gx.ROOT)
