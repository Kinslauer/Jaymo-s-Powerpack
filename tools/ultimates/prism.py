"""(0.20.21, Ultimates patch 1) Prism's Ultimate, Geode: its skin and glow map, 128x128, for client/.../ultimate/UltimateGeode.java.

Prism's faceted violet crystal grown heavier: a deep core cased in dark amethyst plates whose seams glow violet-white, two front
facets that glow brightest, taller faceted spires with pale sharp tips, plated hips on six shard legs. The insignia is set in the
core's top like Prism's. The glow map holds every seam, the two front facets and the spires' tips.

    python3 tools/ultimates/prism.py            paint both skins
    python3 tools/ultimates/prism.py layout     print the UV table (the Java model holds the same one)

128x128, shelf-packed from DIMS (the model's boxes, (w, h, d)); boxes that look the same share one patch of the skin.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from PIL import Image  # noqa: E402

import gen_xenoform as gx  # noqa: E402
import gen_xeno_ultimates as ult  # noqa: E402
from gen_xenoform import CLEAR, faces, rect, save, shade  # noqa: E402

SIZE = 128

# every box of the model, (w, h, d)
DIMS = {
    "core": (9, 6, 13), "side": (1, 3, 9), "front": (7, 4, 1), "back": (7, 4, 1), "rib": (1, 1, 10), "spike": (1, 4, 1),
    "leg_a": (3, 4, 3), "leg_b": (2, 4, 2), "leg_c": (1, 4, 1), "hip": (4, 2, 4),
    "cA": (4, 13, 4), "cB": (3, 10, 3), "cC": (3, 9, 3), "cD": (3, 7, 3), "cE": (2, 5, 2),
    "tA": (2, 4, 2), "tB": (1, 4, 1), "tC": (1, 3, 1),
}
UV = ult.pack(DIMS)

CRYSTAL = (146, 98, 208)    # Prism's amethyst
DEEP = (62, 36, 106)        # the core
PLATE = (46, 26, 80)        # dark amethyst plate
GLOW = (226, 196, 255)      # violet-white seam
HOT = (255, 250, 255)
PALE = (232, 218, 255)
FRACTURE = (40, 22, 70)


def patch(name):
    u, v = UV[name]
    w, h, d = DIMS[name]
    return (u, v, w, h, d)


def facets(skin, part, base, step=3, bands=(1.25, 1.0, 0.72)):
    """Diagonal bands of light and shade over each face of a part, lit per face."""
    for name, (x0, y0, x1, y1) in faces(*part).items():
        c = shade(base, gx.LIGHT[name])
        for y in range(y0, y1):
            for x in range(x0, x1):
                skin.putpixel((x, y), shade(c, bands[((x - x0) + (y - y0)) // step % 3] * (0.95 + gx.rng.random() * 0.1)))


def glow_px(skin, glow, x, y, color, strength=1.0, lit=True):
    """A glowing pixel: the colour in the skin and in the glow map (the map is drawn fullbright)."""
    skin.putpixel((x, y), shade(color, 0.85 + gx.rng.random() * 0.2) if lit else skin.getpixel((x, y)))
    glow.putpixel((x, y), tuple(int(c) for c in color) + (int(255 * strength),))


def seams(skin, glow, box, strength=0.8):
    """The glowing seam along a plate face's bottom and right edge, broken every few pixels."""
    x0, y0, x1, y1 = box
    for x in range(x0, x1):
        if (x + y1) % 4 != 0:
            glow_px(skin, glow, x, y1 - 1, GLOW, strength)
    for y in range(y0, y1 - 1):
        if (y + x1) % 3 != 0:
            glow_px(skin, glow, x1 - 1, y, GLOW, strength)


def fracture(skin, box, n=1):
    """A wandering dark crack down a face."""
    x0, y0, x1, y1 = box
    if y1 - y0 < 5 or x1 - x0 < 2:
        return
    for _ in range(n):
        x = gx.rng.randrange(x0, x1)
        for y in range(y0 + 1, y1 - 1):
            skin.putpixel((x, y), FRACTURE + (255,))
            x = min(x1 - 1, max(x0, x + gx.rng.choice((-1, 0, 0, 1))))


def paler(skin, part, amount, sides=("front", "back", "left", "right")):
    """Blends the side faces toward white-violet, more toward the top."""
    for name in sides:
        x0, y0, x1, y1 = faces(*part)[name]
        for y in range(y0, y1):
            t = 1.0 - (y - y0) / max(1, y1 - y0 - 1)
            for x in range(x0, x1):
                r, g, b, a = skin.getpixel((x, y))
                skin.putpixel((x, y), gx.lerp((r, g, b), PALE, t * amount))


def paint():
    gx.rng.seed("prism_ultimate")
    skin = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    glow = Image.new("RGBA", (SIZE, SIZE), CLEAR)

    # the core: deep violet facets with a violet-white glow through the middle of every side
    core = patch("core")
    facets(skin, core, DEEP, 4)
    for name, (x0, y0, x1, y1) in faces(*core).items():
        if name in ("top", "bottom"):
            continue
        cx, cy = (x0 + x1 - 1) / 2.0, (y0 + y1 - 1) / 2.0
        rx, ry = max(1.0, (x1 - x0) / 2.0), max(1.0, (y1 - y0) / 2.0)
        for y in range(y0, y1):
            for x in range(x0, x1):
                d = (((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2) ** 0.5
                k = max(0.0, 1.0 - d * 1.1) * 0.8
                if k > 0.05:
                    r, g, b, a = skin.getpixel((x, y))
                    skin.putpixel((x, y), gx.lerp((r, g, b), GLOW, k))
                    glow.putpixel((x, y), GLOW + (int(255 * k * 0.7),))
        gx.veins(skin, (x0, y0, x1, y1), shade(DEEP, 1.1), HOT, 2, (3, 6))
    bx0, by0, bx1, by1 = faces(*core)["bottom"]
    gx.noise_fill(skin, shade(DEEP, 0.55), 0.15, (bx0, by0, bx1, by1))
    tx0, ty0, tx1, ty1 = faces(*core)["top"]
    gx.noise_fill(skin, shade(DEEP, 1.25), 0.1, (tx0, ty0, tx1, ty1))
    gx.outline(skin, (tx0, ty0, tx1, ty1), shade(DEEP, 0.8))
    gx.insignia(skin, (tx0 + tx1) // 2, ty0 + 3, rim=(50, 24, 90, 255))

    # plates: dark amethyst, a lit rim and glowing seams
    for name in ("side", "front", "back", "rib"):
        part = patch(name)
        gx.box(skin, part, PLATE, 0.2, edge=0.75)
        for fname, box in faces(*part).items():
            x0, y0, x1, y1 = box
            if x1 - x0 < 1 or y1 - y0 < 1:
                continue
            if fname in ("front", "back", "left", "right", "top") and x1 - x0 >= 3 and y1 - y0 >= 3:
                gx.rect(skin, (x0, y0, x1, y0 + 1), shade(PLATE, 1.5))
                gx.rect(skin, (x0, y0, x0 + 1, y1), shade(PLATE, 1.3))
            if name == "rib" or (fname in ("front", "back", "left", "right") and x1 - x0 >= 3):
                seams(skin, glow, box, 0.85)
    # the two front facets glow brightest
    fx0, fy0, fx1, fy1 = faces(*patch("front"))["front"]
    for ex in (fx0 + 1, fx1 - 3):
        for dx in range(2):
            for dy in range(2):
                glow_px(skin, glow, ex + dx, fy0 + 1 + dy, HOT)
    # spikes: dark at the root, pale at the point
    for name in ("spike",):
        part = patch(name)
        for fname, (x0, y0, x1, y1) in faces(*part).items():
            for y in range(y0, y1):
                t = (y - y0) / max(1, y1 - y0 - 1) if fname not in ("top", "bottom") else 0.5
                for x in range(x0, x1):
                    skin.putpixel((x, y), gx.lerp(PALE, PLATE, min(1.0, t * 1.2)))
            if fname in ("front", "back", "left", "right"):
                glow.putpixel((x0, y0), GLOW + (200,))

    # legs: dark violet at the hip, amethyst in the middle, a pale white-hot point
    facets(skin, patch("leg_a"), DEEP, 3)
    facets(skin, patch("leg_b"), CRYSTAL, 3)
    facets(skin, patch("leg_c"), (214, 190, 250), 2, (1.05, 0.95, 0.85))
    for part in ("leg_a", "leg_b", "leg_c"):
        x0, y0, x1, y1 = faces(*patch(part))["bottom"]
        gx.noise_fill(skin, (250, 244, 255), 0.04, (x0, y0, x1, y1))
        if part != "leg_c":
            for fname in ("front", "back", "left", "right"):
                x0, y0, x1, y1 = faces(*patch(part))[fname]
                gx.rect(skin, (x0, y1 - 1, x1, y1), shade(CRYSTAL, 0.6))
    # plated hips
    part = patch("hip")
    gx.box(skin, part, PLATE, 0.2, edge=0.75)
    for fname, box in faces(*part).items():
        if fname in ("front", "back", "left", "right"):
            seams(skin, glow, box, 0.7)

    # the spires: pale-lit amethyst with fracture lines, bright tips that glow
    for name in ("cA", "cB", "cC", "cD", "cE"):
        part = patch(name)
        facets(skin, part, CRYSTAL, 3)
        paler(skin, part, 0.4)
        for fname in ("front", "back", "left", "right"):
            box = faces(*part)[fname]
            fracture(skin, box)
            # a glowing vein up the middle of the big faces
            x0, y0, x1, y1 = box
            if y1 - y0 >= 9:
                x = (x0 + x1) // 2
                for y in range(y0 + 2, y1 - 2, 2):
                    glow_px(skin, glow, x, y, GLOW, 0.55)
        x0, y0, x1, y1 = faces(*part)["bottom"]
        gx.noise_fill(skin, shade(CRYSTAL, 0.5), 0.1, (x0, y0, x1, y1))
    for name in ("tA", "tB", "tC"):
        part = patch(name)
        facets(skin, part, (232, 218, 255), 2, (1.05, 0.95, 0.82))
        x0, y0, x1, y1 = faces(*part)["top"]
        gx.noise_fill(skin, (252, 248, 255), 0.04, (x0, y0, x1, y1))
        x0, y0, x1, y1 = faces(*part)["bottom"]
        gx.noise_fill(skin, shade(CRYSTAL, 0.7), 0.1, (x0, y0, x1, y1))
        for fname in ("front", "back", "left", "right", "top"):
            x0, y0, x1, y1 = faces(*part)[fname]
            for y in range(y0, y1):
                for x in range(x0, x1):
                    r, g, b, a = skin.getpixel((x, y))
                    glow.putpixel((x, y), (r, g, b, 150))
    # the glow is drawn only where the skin is solid
    for y in range(SIZE):
        for x in range(SIZE):
            if skin.getpixel((x, y))[3] == 0:
                glow.putpixel((x, y), CLEAR)
    save(skin, "entity/xenoform_prism_ultimate.png")
    save(glow, "entity/xenoform_prism_ultimate_glow.png")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "layout":
        for key, (u, v) in UV.items():
            print('uv("%s", %d, %d),' % (key, u, v))
    else:
        paint()
        print("Geode skins written under", gx.ROOT)
