"""(0.20.21, Ultimates patch 1) Knell's Ultimate, The Tolling: its skin and glow map, 128x128, for client/.../ultimate/UltimateTolling.java.

Knell's own build (the same hide, bone horns and brow, tendrils at the chin, rib plates over a glowing chest cavity) grown heavier:
plates of sculk-black stone on the hump, the forearms and the ribs, a ridge of sculk spikes down the spine, longer horns with a
third curl, and teal seams (0x1FB8C8) threaded through the hide and between the plates. The glow map holds every seam and the
chest cavity (the layer draws it fullbright and the model pulses it with a heartbeat).

    python3 tools/ultimates/knell.py            paint both skins
    python3 tools/ultimates/knell.py layout     print the UV table (the Java model holds the same table)
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
    "head": (8, 6, 8), "brow": (9, 2, 3), "jaw": (7, 3, 6), "tendril": (1, 4, 1),
    "horn_b": (2, 7, 2), "horn_t": (2, 6, 2), "horn_c": (2, 4, 2),
    "neck": (6, 11, 6), "trunk": (11, 16, 7), "hump": (15, 6, 9), "hplate": (13, 2, 8),
    "rib": (5, 10, 1), "growth": (3, 5, 3), "spine": (2, 13, 2), "spike": (1, 4, 1),
    "arm": (5, 11, 5), "fore": (6, 10, 6), "fist": (6, 4, 7), "bracer": (7, 5, 7),
    "thigh": (5, 8, 5), "shin": (4, 7, 4), "foot": (5, 2, 7),
}
UV = gxu.pack(DIMS)

SCULK = (20, 44, 52)
BLACK = (14, 20, 24)         # sculk-black plate
COLD = (31, 184, 200)        # 0x1FB8C8
HOT = (120, 240, 250)
BONE = (206, 196, 176)
DEEP = (8, 28, 34)

PLATES = ("hplate", "bracer", "rib")
HIDE = ("head", "jaw", "neck", "trunk", "hump", "arm", "fore", "fist", "thigh", "shin")


def patch(name):
    u, v = UV[name]
    w, h, d = DIMS[name]
    return (u, v, w, h, d)


def lit(skin, glow, x, y, color, strength=1.0):
    """A glowing pixel: a dim teal in the skin (a seam in daylight) and the colour in the glow map."""
    skin.putpixel((x, y), shade(color, 0.55 + gx.rng.random() * 0.2))
    glow.putpixel((x, y), tuple(int(c) for c in color) + (int(255 * strength),))


def veins(skin, glow, box, n, length=(3, 6)):
    """Wandering one-pixel veins of teal light inside the box, in the skin and the glow."""
    x0, y0, x1, y1 = box
    if x1 <= x0 or y1 <= y0:
        return
    for _ in range(n):
        x, y = gx.rng.randrange(x0, x1), gx.rng.randrange(y0, y1)
        hot = gx.rng.random() < 0.3
        for _ in range(gx.rng.randrange(*length)):
            lit(skin, glow, x, y, HOT if hot else COLD, 0.8 + gx.rng.random() * 0.2)
            x = min(x1 - 1, max(x0, x + gx.rng.choice((-1, 0, 1))))
            y = min(y1 - 1, max(y0, y + gx.rng.choice((-1, 0, 1))))


def bevel(skin, box, base):
    """A lit top-left and a shaded bottom-right rim: reads as a raised plate."""
    x0, y0, x1, y1 = box
    if x1 - x0 < 3 or y1 - y0 < 3:
        return
    rect(skin, (x0, y0, x1, y0 + 1), shade(base, 1.9))
    rect(skin, (x0, y0, x0 + 1, y1), shade(base, 1.6))
    rect(skin, (x0, y1 - 1, x1, y1), shade(base, 0.6))
    rect(skin, (x1 - 1, y0, x1, y1), shade(base, 0.7))


def seam_rim(skin, glow, box, strength=0.75):
    """The teal line between plates: the bottom and right rim of a plate face glows, in dashes."""
    x0, y0, x1, y1 = box
    for x in range(x0, x1):
        if (x + y1) % 3 != 0:
            lit(skin, glow, x, y1 - 1, COLD, strength)
    for y in range(y0, y1 - 1):
        if (y + x1) % 3 != 0:
            lit(skin, glow, x1 - 1, y, COLD, strength)


def paint():
    """Knell's Ultimate: sculk hide threaded with teal light under sculk-black plates, bone horns and brow, a glowing chest cavity."""
    gx.rng.seed("knell_ultimate")
    skin = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    glow = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    for name in DIMS:
        part = patch(name)
        if name in ("horn_b", "horn_t", "horn_c", "spike", "brow"):
            base = BONE if name != "spike" else shade(SCULK, 0.7)
            for face, b in faces(*part).items():
                gx.vgrad(skin, b, shade(base, 1.1) if name != "horn_c" else (236, 230, 214), shade(base, 0.65), 0.1)
                if name.startswith("horn"):
                    for y in range(b[1] + 2, b[3], 2):
                        rect(skin, (b[0], y, b[2], y + 1), shade(base, 0.6))
                if name == "spike" and face in ("front", "back", "left", "right"):
                    lit(skin, glow, b[0], b[1], COLD, 0.9)
            continue
        if name == "tendril":
            gx.box(skin, part, shade(SCULK, 0.7), 0.2)
            for face, (x0, y0, x1, y1) in faces(*part).items():
                if face in ("front", "back", "left", "right"):
                    lit(skin, glow, x0, y1 - 1, COLD, 1.0)
            continue
        base = BLACK if name in PLATES else shade(SCULK, {"neck": 0.85, "jaw": 0.8, "hump": 0.75, "trunk": 0.8, "fore": 0.9}.get(name, 1.0))
        gx.box(skin, part, base, 0.2 if name in PLATES else 0.3, edge=0.7 if name in PLATES else None)
        for face, box in faces(*part).items():
            if name in PLATES:
                if face in ("front", "back", "left", "right", "top"):
                    bevel(skin, box, base)
                if face in ("front", "back", "left", "right"):
                    seam_rim(skin, glow, box)
            elif name in HIDE:
                veins(skin, glow, box, 1 if face in ("top", "bottom") else 3)
            elif name == "growth" and face == "top":
                gx.noise_fill(skin, HOT, 0.1, box)
                for x in range(box[0], box[2]):
                    for y in range(box[1], box[3]):
                        glow.putpixel((x, y), HOT + (255,))
            elif name == "spine":
                if face in ("front", "back", "left", "right"):
                    for y in range(box[1], box[3], 3):
                        lit(skin, glow, (box[0] + box[2]) // 2, y, COLD, 0.9)
    # the head: blind and smooth, a faint glow where the eyes would be
    hx0, hy0, hx1, hy1 = faces(*patch("head"))["front"]
    gx.noise_fill(skin, shade(SCULK, 0.9), 0.2, (hx0, hy0, hx1, hy1))
    for x in range(hx0 + 1, hx1 - 1):
        for y in (hy0 + 2, hy0 + 3):
            lit(skin, glow, x, y, COLD if y == hy0 + 2 else HOT, 0.7)
    rect(skin, (hx0 + 1, hy1 - 1, hx1 - 1, hy1), shade(SCULK, 0.55))
    # the jaw: bone teeth along its front, the roof of the mouth glowing
    for face, (x0, y0, x1, y1) in faces(*patch("jaw")).items():
        if face in ("front", "left", "right"):
            for x in range(x0, x1, 2):
                rect(skin, (x, y0, x + 1, y0 + 1), shade(BONE, 1.05))
        elif face == "top":
            for x in range(x0, x1):
                for y in range(y0, y1):
                    lit(skin, glow, x, y, COLD, 0.55)
    # the trunk: the chest a glowing cavity (seen between the rib plates), the insignia at its heart, bone belly plates below
    fx0, fy0, fx1, fy1 = faces(*patch("trunk"))["front"]
    for y in range(fy0 + 5, fy0 + 13):
        for x in range(fx0, fx1):
            d = ((x - (fx0 + 5.0)) ** 2 + ((y - (fy0 + 9.0)) / 1.2) ** 2) ** 0.5
            t = max(0.0, 1.0 - d / 6.0)
            c = gx.lerp(DEEP, HOT, t ** 1.6 * 0.8 + gx.rng.random() * 0.08)
            skin.putpixel((x, y), c)
            glow.putpixel((x, y), tuple(c[:3]) + (int(255 * min(1.0, 0.35 + t)),))
    rect(skin, (fx0, fy0 + 4, fx1, fy0 + 5), shade(SCULK, 0.6))
    rect(skin, (fx0, fy0 + 13, fx1, fy0 + 14), shade(SCULK, 0.55))
    for y in range(fy0 + 14, fy1, 3):
        rect(skin, (fx0 + 1, y, fx1 - 1, y + 1), shade(BONE, 0.75))
        rect(skin, (fx0 + 1, y + 1, fx1 - 1, y + 2), shade(BONE, 0.45))
    gx.insignia(skin, fx0 + 5, fy0 + 7, small=True, rim=(8, 24, 30, 255))
    # bone bands round the wrists and the knuckles, bone shins
    for name, rows in (("fore", 3), ("fist", 2), ("shin", 3)):
        for face, (x0, y0, x1, y1) in faces(*patch(name)).items():
            if face in ("front", "back", "left", "right"):
                for x in range(x0, x1, 2):
                    rect(skin, (x, y1 - rows, x + 1, y1), shade(BONE, 0.95))
                    rect(skin, (x + 1, y1 - rows, x + 2, y1), shade(BONE, 0.75))
                rect(skin, (x0, y1 - 1, x1, y1), shade(BONE, 0.5))
    for face, b in faces(*patch("foot")).items():
        if face == "bottom":
            gx.noise_fill(skin, (30, 36, 40, 255), 0.2, b)
    # the glow is drawn only where the skin is solid
    for y in range(SIZE):
        for x in range(SIZE):
            if skin.getpixel((x, y))[3] == 0:
                glow.putpixel((x, y), CLEAR)
    save(skin, "entity/xenoform_knell_ultimate.png")
    save(glow, "entity/xenoform_knell_ultimate_glow.png")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "layout":
        for key, (u, v) in UV.items():
            print('uv("%s", %d, %d),' % (key, u, v))
    else:
        paint()
        print("Knell's Ultimate skins written under", gx.ROOT)
