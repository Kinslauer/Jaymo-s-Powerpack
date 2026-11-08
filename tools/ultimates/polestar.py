"""(0.21.37, Ultimates patch 2) Polestar's Ultimate, Lodestar: its skin and glow map, 128x128, for client/.../ultimate/UltimateLodestar.java.

Polestar's own body (the same dark lodestone grey with the red and silver magnet bands, on the vanilla layout in the top-left 64x64)
under heavier plates of dark lodestone: a thick chest plate with the insignia set in it, a shoulder yoke, pauldrons, bracers, greaves
and a brow, spikes on the shoulders, elbows and back, four loose iron shards, and long curved horseshoe prongs. The seams glow magnet
red (0xD84A4A) and the prong tips glow blue. The glow map holds every seam, the eyes and the prong tips (the layer draws it fullbright).

    python3 tools/ultimates/polestar.py            paint both skins
    python3 tools/ultimates/polestar.py layout     print the UV table (the Java model holds the same table)
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from PIL import Image  # noqa: E402

import gen_xenoform as gx  # noqa: E402
import gen_xeno_ultimates as gxu  # noqa: E402
from gen_xenoform import CLEAR, faces, rect, save, shade  # noqa: E402

SIZE = 128

# every extra box of the model, (w, h, d); the vanilla humanoid boxes keep the vanilla layout in the top-left 64x64
DIMS = {
    "prong": (2, 8, 2), "tip": (2, 6, 2), "brow": (10, 2, 2),
    "yoke": (14, 4, 7), "chest": (10, 8, 1), "back": (9, 9, 1),
    "pauld": (7, 4, 7), "bracer": (5, 5, 5), "greave": (5, 5, 5),
    "spike": (1, 5, 1), "bspike": (1, 3, 1), "shard": (2, 6, 2),
}
PLATES = ("yoke", "chest", "back", "pauld", "bracer", "greave", "brow")
SIDES = ("front", "back", "left", "right")
VANILLA = gx.BASE_PARTS


def pack(dims):
    """Shelf-packs every box's net (2d+2w wide, d+h tall) in rows below the vanilla layout (y 64 and down): name -> (u, v)."""
    at = {}
    x, y, row = 0, 64, 0
    for name, (w, h, d) in dims.items():
        nw, nh = 2 * d + 2 * w, d + h
        if x + nw > SIZE:
            x, y, row = 0, y + row, 0
        at[name] = (x, y)
        x += nw
        row = max(row, nh)
    assert y + row <= SIZE, "layout overflows %d (needs %d)" % (SIZE, y + row)
    return at


UV = pack(DIMS)

LODE = (52, 54, 62)          # dark lodestone
LODE_LIGHT = (86, 90, 102)
IRON = (150, 156, 168)       # the loose shards
RED = (216, 74, 74)          # 0xD84A4A, the magnet red
RED_HOT = (255, 150, 140)
BLUE = (96, 168, 255)        # the prong tips
BLUE_HOT = (214, 236, 255)
SILVER = (214, 220, 228)
DARK_RED = (74, 22, 24)


def patch(name):
    u, v = UV[name]
    w, h, d = DIMS[name]
    return (u, v, w, h, d)


def lit(skin, glow, x, y, color, strength=1.0):
    """A glowing pixel: a dim version of the colour in the skin (a seam in daylight) and the colour itself in the glow map."""
    skin.putpixel((x, y), shade(color, 0.45 + gx.rng.random() * 0.15))
    glow.putpixel((x, y), tuple(int(c) for c in color) + (int(255 * strength),))


def seam_rim(skin, glow, box, color=RED, strength=0.8):
    """The red line between plates: the bottom and right rim of a plate face glows, in dashes."""
    x0, y0, x1, y1 = box
    for x in range(x0, x1):
        if (x + y1) % 4 != 0:
            lit(skin, glow, x, y1 - 1, color, strength)
    for y in range(y0, y1 - 1):
        if (y + x1) % 3 != 0:
            lit(skin, glow, x1 - 1, y, color, strength)


def field_lines(skin, glow, box, n):
    """Thin red field lines curving across a plate: wandering one-pixel runs in the skin and the glow."""
    x0, y0, x1, y1 = box
    if x1 - x0 < 3 or y1 - y0 < 3:
        return
    for _ in range(n):
        x, y = gx.rng.randrange(x0 + 1, x1 - 1), gx.rng.randrange(y0 + 1, y1 - 1)
        hot = gx.rng.random() < 0.25
        for _ in range(gx.rng.randrange(3, 7)):
            lit(skin, glow, x, y, RED_HOT if hot else RED, 0.8 + gx.rng.random() * 0.2)
            x = min(x1 - 2, max(x0 + 1, x + gx.rng.choice((-1, 0, 1))))
            y = min(y1 - 2, max(y0 + 1, y + gx.rng.choice((0, 1))))


def plate(skin, glow, name):
    """A lodestone plate: dark grainy rock, a lit rim, a red seam along its lower edges and a few red field lines."""
    part = patch(name)
    gx.box(skin, part, LODE, 0.22, edge=0.75)
    for fname, box in faces(*part).items():
        x0, y0, x1, y1 = box
        if x1 - x0 < 2 or y1 - y0 < 2:
            continue
        if fname in SIDES + ("top",):
            gxu.bevel(skin, box, shade(LODE_LIGHT, 1.15 if fname == "top" else 0.95))
        if fname in SIDES:
            seam_rim(skin, glow, box)
        field_lines(skin, glow, box, max(0, (x1 - x0) * (y1 - y0) // 40))


def paint():
    gx.rng.seed("polestar_ultimate")
    skin = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    glow = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    # the body: dark lodestone with an iron sheen (Polestar's own paint, darker)
    gx.parts(skin, (66, 68, 78), 0.2)
    for part in VANILLA:
        for name, b in faces(*part).items():
            gx.speckle(skin, b, shade((66, 68, 78, 255), 1.4), (b[2] - b[0]) * (b[3] - b[1]) // 12)
    # the magnet's bands: red on the upper arms and thighs, silver at the wrists and ankles
    for part in gx.LIMBS:
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name in SIDES:
                rect(skin, (x0, y0, x1, y0 + 4), RED + (255,))
                rect(skin, (x0, y1 - 3, x1, y1 - 1), SILVER + (255,))
                for x in range(x0, x1):
                    lit(skin, glow, x, y0 + 3, RED_HOT, 0.6)
    # field lines across the chest and back of the body
    for box in (gx.front(gx.BODY), faces(*gx.BODY)["back"]):
        for y in range(box[1] + 1, box[3] - 1, 3):
            for x in range(box[0], box[2]):
                if (x + y) % 4 == 0:
                    skin.putpixel((x, y), (150, 160, 190, 255))
    # the face: spark eyes that shine, a hard jaw line
    hx0, hy0, hx1, hy1 = gx.front(gx.HEAD)
    for x in (9, 10, 13, 14):
        lit(skin, glow, x, 11, BLUE_HOT, 1.0)
        skin.putpixel((x, 11), BLUE_HOT + (255,))
    for x in (9, 10, 13, 14):
        lit(skin, glow, x, 12, BLUE, 0.7)
    rect(skin, (9, 14, 15, 15), (30, 32, 38, 255))
    gx.clear_hat(skin)
    # the plates
    for name in PLATES:
        plate(skin, glow, name)
    # spikes: lodestone, with a red point
    for name in ("spike", "bspike"):
        part = patch(name)
        gx.box(skin, part, shade(LODE_LIGHT, 0.9), 0.2, edge=0.75)
        for fname, (x0, y0, x1, y1) in faces(*part).items():
            if fname in SIDES:
                lit(skin, glow, x0, y0, RED_HOT, 0.9)
    # the chest plate carries the insignia on its front, on a field of red lines
    cx0, cy0, cx1, cy1 = gx.front(patch("chest"))
    gx.insignia(skin, (cx0 + cx1) // 2, cy0 + 1, small=True, rim=(30, 30, 36, 255))
    # the iron shards: bright iron with a red seam down the edge
    shard = patch("shard")
    gx.box(skin, shard, IRON, 0.25, edge=0.7)
    for fname, (x0, y0, x1, y1) in faces(*shard).items():
        if fname in SIDES:
            for y in range(y0, y1, 2):
                lit(skin, glow, x0, y, RED, 0.7)
        elif fname == "top":
            rect(skin, (x0, y0, x1, y1), shade(IRON, 1.3))
    # the prongs: red lodestone below, silver bands, and the tips shine blue (white-hot at the point)
    prong = patch("prong")
    gx.box(skin, prong, RED, 0.12, edge=0.8)
    for fname, (x0, y0, x1, y1) in faces(*prong).items():
        if fname in SIDES:
            for y in range(y0 + 1, y1, 3):
                rect(skin, (x0, y, x1, y + 1), SILVER + (255,))
            lit(skin, glow, x0, y1 - 1, RED_HOT, 0.8)
    tip = patch("tip")
    gx.box(skin, tip, SILVER, 0.1, edge=0.85)
    for fname, (x0, y0, x1, y1) in faces(*tip).items():
        h = y1 - y0
        for y in range(y0, y1):
            t = (y - y0) / max(1, h - 1)  # 0 at the point, 1 at the root
            for x in range(x0, x1):
                if fname == "top":
                    color = BLUE_HOT
                elif fname == "bottom":
                    continue
                elif t < 0.45:
                    color = gx.lerp(BLUE_HOT, BLUE, t / 0.45)[:3]
                else:
                    color = gx.lerp(BLUE, SILVER, (t - 0.45) / 0.55)[:3]
                skin.putpixel((x, y), tuple(color) + (255,))
                glow.putpixel((x, y), tuple(color) + (255 if t < 0.45 or fname == "top" else int(255 * (1.0 - t) * 1.2),))
    # the glow is drawn only where the skin is solid
    for y in range(SIZE):
        for x in range(SIZE):
            if skin.getpixel((x, y))[3] == 0:
                glow.putpixel((x, y), CLEAR)
    save(skin, "entity/xenoform_polestar_ultimate.png")
    save(glow, "entity/xenoform_polestar_ultimate_glow.png")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "layout":
        for key, (u, v) in UV.items():
            print('uv("%s", %d, %d),' % (key, u, v))
    else:
        paint()
        print("Lodestar skins written under", gx.ROOT)
