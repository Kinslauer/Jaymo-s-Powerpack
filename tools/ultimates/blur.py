"""(0.20.21) Velox's Ultimate, Standstill: its skin and glow map for client/.../ultimate/UltimateStandstill.java's own model.

    python3 tools/ultimates/blur.py            paint textures/entity/xenoform_blur_ultimate.png and xenoform_blur_ultimate_glow.png
    python3 tools/ultimates/blur.py layout     print the UV table (the Java model holds the same one)

128x128, shelf-packed from DIMS (the model's boxes, (w, h, d)); a box that looks the same on both sides shares one patch.
Blue-black plates over Velox's own dark blue hide, the pale chevron stripes and belly kept, and the seams glowing the light blue of
Velocity's streak (ChronoSpeed.SKY, 0x8FD8FF) in the glow map.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from PIL import Image  # noqa: E402

import gen_xenoform as gx  # noqa: E402
import gen_xeno_ultimates as ult  # noqa: E402
from gen_xenoform import CLEAR, LIGHT, faces, rect, save, shade  # noqa: E402

SIZE = 128

# every box of the model, (w, h, d)
DIMS = {
    "skull": (4, 4, 6), "snout": (3, 2, 5), "jaw": (3, 1, 7), "visor": (5, 2, 4), "vplate": (6, 1, 5),
    "crest": (1, 2, 4), "sweep": (1, 2, 6), "quill": (1, 1, 6),
    "torso": (5, 5, 8), "chest": (4, 4, 5), "splate": (3, 1, 2), "spike": (1, 2, 1),
    "neck1": (3, 5, 3), "neck2": (2, 5, 2),
    "tail1": (4, 3, 7), "tail2": (3, 2, 7), "tail3": (2, 2, 6), "tail4": (2, 1, 4),
    "tp1": (4, 1, 5), "tp2": (3, 1, 5), "tp3": (2, 1, 4), "blade": (1, 3, 6),
    "arm": (2, 5, 2), "hand": (2, 3, 2), "claw": (1, 2, 1), "aplate": (3, 2, 3),
    "thigh": (3, 7, 4), "tplate": (4, 3, 5), "shin": (2, 8, 3), "tarsus": (2, 6, 2), "foot": (4, 2, 5), "sickle": (1, 1, 4),
}
UV = ult.pack(DIMS)

HIDE = (24, 34, 62)        # Velox's blue-black hide
PLATE = (20, 26, 46)       # the plates over it: darker, blue-black
PLATE_HI = (44, 58, 96)
PALE = (222, 234, 250)
GLASS = (150, 226, 242)
BONE = (226, 224, 208)
SKY = (143, 216, 255)      # ChronoSpeed.SKY_DUST, 0x8FD8FF
SKY_HOT = (226, 246, 255)
DEEP = (10, 14, 28)

PLATES = ("vplate", "splate", "tp1", "tp2", "tp3", "aplate", "tplate", "chest")
BLADES = ("quill", "sweep", "crest", "blade", "sickle", "claw", "spike")


def patch(name):
    u, v = UV[name]
    w, h, d = DIMS[name]
    return (u, v, w, h, d)


def glow_px(skin, glow, x, y, color, strength=1.0):
    """A glowing pixel: the seam's deep blue in the skin (so it reads in daylight) and the light blue in the glow map."""
    skin.putpixel((x, y), shade((30, 60, 110), 0.85 + gx.rng.random() * 0.3))
    glow.putpixel((x, y), tuple(int(c) for c in color) + (int(255 * strength),))


def bevel(skin, box, base):
    """A lit top-left and a shaded bottom-right rim: a raised plate."""
    x0, y0, x1, y1 = box
    if x1 - x0 < 3 or y1 - y0 < 2:
        return
    rect(skin, (x0, y0, x1, y0 + 1), shade(base, 1.5))
    rect(skin, (x0, y0, x0 + 1, y1), shade(base, 1.3))
    rect(skin, (x0, y1 - 1, x1, y1), shade(base, 0.55))
    rect(skin, (x1 - 1, y0, x1, y1), shade(base, 0.7))


def seam_rim(skin, glow, box, strength=0.8):
    """The glowing line between plates: the lower and the far rim of a face."""
    x0, y0, x1, y1 = box
    for x in range(x0, x1):
        if (x + y1) % 4 != 0:
            glow_px(skin, glow, x, y1 - 1, SKY, strength)
    for y in range(y0, y1 - 1):
        if (y + x1) % 3 != 0:
            glow_px(skin, glow, x1 - 1, y, SKY, strength)


def chevrons(skin, glow, box, step):
    """Pale chevron stripes, with a faint sky glow in each, down a hide face."""
    x0, y0, x1, y1 = box
    mid = (x0 + x1 - 1) / 2.0
    for y in range(y0 + 1, y1, step):
        for x in range(x0, x1):
            yy = y + int(abs(x - mid) * 0.8)
            if yy < y1:
                skin.putpixel((x, yy), shade(PALE, 0.7))
                glow.putpixel((x, yy), SKY + (110,))


def blur():
    """Velox's Standstill: blue-black plates on the raptor's hide, bone-pale blade quills and sickle claws, light-blue seams."""
    gx.rng.seed("blur_ultimate")
    skin = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    glow = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    for name in DIMS:
        part = patch(name)
        if name in ("quill", "sweep", "crest", "blade"):
            gx.box(skin, part, shade(PLATE, 1.1), 0.14, edge=0.7)
            for fname, box in faces(*part).items():
                if fname in ("left", "right"):
                    # a bright edge along the blade, glowing
                    x0, y0, x1, y1 = box
                    rect(skin, (x0, y0, x1, y0 + 1), shade(SKY, 0.75))
                    for x in range(x0, x1):
                        glow.putpixel((x, y0), SKY + (200,))
                if fname == "top":
                    for x in range(box[0], box[2]):
                        for y in range(box[1], box[3]):
                            glow.putpixel((x, y), SKY + (150,))
            continue
        if name in ("sickle", "claw", "spike"):
            gx.box(skin, part, BONE, 0.12, edge=0.72)
            continue
        base = PLATE if name in PLATES else HIDE
        gx.box(skin, part, base, 0.14, edge=0.75)
        for fname, box in faces(*part).items():
            if name in PLATES:
                bevel(skin, box, shade(PLATE_HI, LIGHT[fname]))
                if fname not in ("top", "bottom"):
                    seam_rim(skin, glow, box)
                else:
                    # the plate's rim glows where it meets the hide
                    x0, y0, x1, y1 = box
                    for x in range(x0, x1):
                        glow_px(skin, glow, x, y1 - 1, SKY, 0.55)
            elif name in ("torso", "tail1", "tail2", "tail3", "thigh", "shin", "neck1", "neck2") and fname in ("left", "right", "front", "back"):
                chevrons(skin, glow, box, 3)
    # the belly and the throat stay pale: the underside of the torso and the jaw
    for name, base in (("torso", PALE), ("jaw", PALE), ("chest", PALE)):
        x0, y0, x1, y1 = faces(*patch(name))["bottom"]
        gx.noise_fill(skin, shade(base, 0.8), 0.1, (x0, y0, x1, y1))
    # the skull: pale crown stripe, cyan eyes on the sides, a dark mouth line
    sk = patch("skull")
    sx0, sy0, sx1, sy1 = faces(*sk)["top"]
    rect(skin, (sx0 + 1, sy0, sx0 + 3, sy1), shade(PALE, 0.8))
    for name, ex in (("right", 0), ("left", 1)):
        fx0, fy0, fx1, fy1 = faces(*sk)[name]
        ex0 = fx0 + 3 if name == "right" else fx0 + 1
        for dx in range(2):
            skin.putpixel((ex0 + dx, fy0 + 1), (190, 250, 255, 255))
            glow.putpixel((ex0 + dx, fy0 + 1), SKY_HOT + (255,))
        skin.putpixel((ex0 + (1 if name == "right" else 0), fy0 + 1), (20, 28, 50, 255))
    nx0, ny0, nx1, ny1 = faces(*patch("snout"))["front"]
    skin.putpixel((nx0, ny0), DEEP + (255,))
    skin.putpixel((nx1 - 1, ny0), DEEP + (255,))
    # the plated visor: a glass slit under the plate that glows
    vx0, vy0, vx1, vy1 = faces(*patch("visor"))["front"]
    rect(skin, (vx0, vy0, vx1, vy1), shade(GLASS, 0.9))
    for x in range(vx0, vx1):
        glow.putpixel((x, vy0), SKY_HOT + (230,))
        glow.putpixel((x, vy1 - 1), SKY + (160,))
    # the jaw's mouth is dark and its teeth bone
    jx0, jy0, jx1, jy1 = faces(*patch("jaw"))["top"]
    rect(skin, (jx0, jy0, jx1, jy1), (90, 30, 44, 255))
    for fname in ("left", "right"):
        x0, y0, x1, y1 = faces(*patch("jaw"))[fname]
        for x in range(x0 + 1, x1, 2):
            skin.putpixel((x, y0), BONE + (255,))
    # the insignia on the back of the torso (the hidden body lies face-up there); the torso's top
    tx0, ty0, tx1, ty1 = faces(*patch("torso"))["top"]
    gx.insignia(skin, (tx0 + tx1) // 2, ty0 + 2, small=True)
    # glossy near-black soles
    for name in ("foot",):
        x0, y0, x1, y1 = faces(*patch(name))["bottom"]
        gx.noise_fill(skin, (14, 16, 26, 255), 0.1, (x0, y0, x1, y1))
    # the glow is drawn only where the skin is solid
    for y in range(SIZE):
        for x in range(SIZE):
            if skin.getpixel((x, y))[3] == 0:
                glow.putpixel((x, y), CLEAR)
    save(skin, "entity/xenoform_blur_ultimate.png")
    save(glow, "entity/xenoform_blur_ultimate_glow.png")


def paint():
    blur()


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "layout":
        for key, (u, v) in UV.items():
            w, h, d = DIMS[key]
            print('uv("%s", %d, %d, %d, %d, %d),' % (key, u, v, w, h, d))
    else:
        paint()
        print("Standstill skins written under", gx.ROOT)
