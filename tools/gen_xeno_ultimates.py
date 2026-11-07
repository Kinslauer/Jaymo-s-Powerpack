"""(0.20.9, Ultimates patch 0) The Ultimates' own skins: one painter per Ultimate, each writing
textures/entity/xenoform_<id>_ultimate.png and xenoform_<id>_ultimate_glow.png for that Ultimate's own model.

The older skins (gen_xenoform.ultimate()) recolour the base alien's skin on the base UV layout; an evolved Ultimate has its own
model (client/power/xenoform/ultimate/Ultimate<Name>.java) and so its own layout, 128x128 here (what its `bake` was given).
Each painter seeds gen_xenoform.rng with "<id>_ultimate" first, because gen_xenoform's helpers draw from that module-level
stream, not from the global `random`; that way no painter shifts another skin's speckle.

    python3 tools/gen_xeno_ultimates.py            paint every Ultimate (the later ones through tools/ultimates/*.py)
    python3 tools/gen_xeno_ultimates.py layout     print Titan's UV table (the Java model holds the same table)

When an alien's Ultimate lands here, take it out of gen_xenoform's ultimate() loops (OWN_ULTIMATES there).
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from PIL import Image  # noqa: E402

import gen_xenoform as gx  # noqa: E402
from gen_xenoform import CLEAR, faces, rect, save, shade  # noqa: E402

SIZE = 128


def pack(dims, size=SIZE):
    """Shelf-packs every box's net (2d+2w wide, d+h tall) into size x size, in order: name -> (u, v)."""
    at = {}
    x = y = row = 0
    for name, (w, h, d) in dims.items():
        nw, nh = 2 * d + 2 * w, d + h
        if x + nw > size:
            x, y, row = 0, y + row, 0
        at[name] = (x, y)
        x += nw
        row = max(row, nh)
    assert y + row <= size, "layout overflows %d (needs %d)" % (size, y + row)
    return at


# ---- Bulwark -> Titan ----
# every box of the model, (w, h, d); boxes that look the same on both sides share one patch of the skin
TITAN_DIMS = {
    "head": (7, 7, 7), "brow": (9, 2, 4), "jaw": (7, 2, 4), "tusk": (1, 4, 1), "horn": (2, 4, 2),
    "chest": (12, 9, 6), "waist": (5, 5, 4), "belt": (7, 2, 5), "yoke": (14, 2, 7), "backplate": (9, 6, 1),
    "spine": (2, 9, 2), "spike": (1, 3, 1),
    "ua_up": (5, 8, 5), "pauld": (7, 4, 7), "fa_up": (6, 6, 6), "fi_up": (7, 5, 7),
    "ua_lo": (6, 8, 6), "fa_lo": (7, 6, 7), "fi_lo": (8, 5, 8), "knuckle": (1, 2, 1),
    "thigh": (4, 7, 4), "shin": (4, 7, 4), "greave": (5, 4, 5), "foot": (5, 3, 7),
}
TITAN_UV = pack(TITAN_DIMS)

STONE = (104, 106, 112)       # iron-grey stone plate
BASALT = (58, 56, 62)         # the dark rock under the plates
BONE = (222, 212, 184)
LAVA = (255, 122, 30)
HOT = (255, 206, 96)
EMBER = (70, 26, 10)

PLATES = ("yoke", "pauld", "fa_up", "fi_up", "fa_lo", "fi_lo", "greave", "backplate", "brow", "jaw", "foot", "belt")
CORE = ("chest", "waist", "ua_up", "ua_lo", "thigh", "shin", "spine", "head")
SIDES = ("front", "back", "left", "right")


def patch(name):
    u, v = TITAN_UV[name]
    w, h, d = TITAN_DIMS[name]
    return (u, v, w, h, d)


def glow_px(skin, glow, x, y, color, strength=1.0):
    """A glowing pixel: ember-dark in the skin (so it reads as a hot crack in daylight) and the colour in the glow map."""
    skin.putpixel((x, y), shade(EMBER, 0.8 + gx.rng.random() * 0.5))
    glow.putpixel((x, y), tuple(int(c) for c in color) + (int(255 * strength),))


def cracks(skin, glow, box, n, length=(3, 7), color=LAVA):
    """Wandering one-pixel cracks of molten rock inside the box, in the skin and the glow."""
    x0, y0, x1, y1 = box
    if x1 <= x0 or y1 <= y0:
        return
    for _ in range(n):
        x, y = gx.rng.randrange(x0, x1), gx.rng.randrange(y0, y1)
        hot = gx.rng.random() < 0.3
        for _ in range(gx.rng.randrange(*length)):
            glow_px(skin, glow, x, y, HOT if hot else color, 0.85 + gx.rng.random() * 0.15)
            x = min(x1 - 1, max(x0, x + gx.rng.choice((-1, 0, 1))))
            y = min(y1 - 1, max(y0, y + gx.rng.choice((-1, 0, 1))))


def bevel(skin, box, base):
    """A lit top-left and a shaded bottom-right rim: reads as a raised plate."""
    x0, y0, x1, y1 = box
    if x1 - x0 < 3 or y1 - y0 < 3:
        return
    rect(skin, (x0, y0, x1, y0 + 1), shade(base, 1.25))
    rect(skin, (x0, y0, x0 + 1, y1), shade(base, 1.15))
    rect(skin, (x0, y1 - 1, x1, y1), shade(base, 0.6))
    rect(skin, (x1 - 1, y0, x1, y1), shade(base, 0.7))


def seam_rim(skin, glow, box, color=LAVA, strength=0.7):
    """The molten line between plates: the bottom and right rim of a plate face glows."""
    x0, y0, x1, y1 = box
    for x in range(x0, x1):
        if (x + y1) % 4 != 0:
            glow_px(skin, glow, x, y1 - 1, color, strength)
    for y in range(y0, y1 - 1):
        if (y + x1) % 3 != 0:
            glow_px(skin, glow, x1 - 1, y, color, strength)


def titan():
    """Bulwark's Titan: iron-grey stone plates over dark basalt, molten orange seams and cracks between them, bone-pale spikes."""
    gx.rng.seed("bulwark_ultimate")
    skin = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    glow = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    for name in TITAN_DIMS:
        part = patch(name)
        if name in ("spike", "knuckle", "horn", "tusk"):
            base = BONE if name in ("tusk", "knuckle") else shade(BASALT, 0.8)
            gx.box(skin, part, base, 0.18, edge=0.75)
            continue
        base = STONE if name in PLATES else BASALT
        gx.box(skin, part, base, 0.18 if name in PLATES else 0.25, edge=0.78)
        for fname, box in faces(*part).items():
            if name in PLATES:
                if fname in SIDES + ("top",):
                    bevel(skin, box, shade(base, {"top": 1.18}.get(fname, 0.9)))
                if fname in SIDES:
                    # a mortar line across tall plates, with a rivet-like stud where it meets the edge
                    x0, y0, x1, y1 = box
                    if y1 - y0 >= 6:
                        rect(skin, (x0, (y0 + y1) // 2, x1, (y0 + y1) // 2 + 1), shade(base, 0.55))
                    seam_rim(skin, glow, box)
            else:
                x0, y0, x1, y1 = box
                area = (x1 - x0) * (y1 - y0)
                cracks(skin, glow, box, max(1, area // 30))
    # the head: a dark visor slit with two molten eyes, a mouth line under the jaw, a crack up the brow
    hx0, hy0, hx1, hy1 = faces(*patch("head"))["front"]
    rect(skin, (hx0, hy0 + 3, hx1, hy0 + 5), (24, 18, 16, 255))
    for ex in (hx0 + 1, hx1 - 3):
        for dx in range(2):
            for dy in range(2):
                glow_px(skin, glow, ex + dx, hy0 + 3 + dy, HOT if dy == 0 else LAVA)
    bx0, by0, bx1, by1 = faces(*patch("brow"))["front"]
    rect(skin, (bx0, by0, bx1, by0 + 1), shade(STONE, 0.5))
    jx0, jy0, jx1, jy1 = faces(*patch("jaw"))["front"]
    rect(skin, (jx0 + 1, jy0, jx1 - 1, jy0 + 1), (24, 18, 16, 255))
    for x in range(jx0 + 1, jx1 - 1, 2):
        glow_px(skin, glow, x, jy0, LAVA, 0.6)
    # the chest: the xeno insignia stamped on the breastplate, a molten channel down the middle of the back plate
    cx0, cy0, cx1, cy1 = faces(*patch("chest"))["front"]
    rect(skin, (cx0 + 1, cy0 + 1, cx1 - 1, cy1 - 1), shade(STONE, 0.8))
    bevel(skin, (cx0 + 1, cy0 + 1, cx1 - 1, cy1 - 1), shade(STONE, 0.8))
    gx.insignia(skin, (cx0 + cx1) // 2, cy0 + 2, small=True)
    seam_rim(skin, glow, (cx0, cy0, cx1, cy1), LAVA, 0.8)
    # black ash at the tips of the knuckle spikes and the feet
    for name in ("foot",):
        for fname, (x0, y0, x1, y1) in faces(*patch(name)).items():
            if fname == "bottom":
                gx.noise_fill(skin, (30, 30, 34, 255), 0.2, (x0, y0, x1, y1))
    # lava under every glow pixel is also kept off the skin's alpha holes: the glow is drawn only where the skin is solid
    for y in range(SIZE):
        for x in range(SIZE):
            if skin.getpixel((x, y))[3] == 0:
                glow.putpixel((x, y), CLEAR)
    save(skin, "entity/xenoform_bulwark_ultimate.png")
    save(glow, "entity/xenoform_bulwark_ultimate_glow.png")


PAINTERS = (titan,)


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "layout":
        for key, (u, v) in TITAN_UV.items():
            print('"%s", %d, %d,' % (key, u, v))
    else:
        for painter in PAINTERS:
            painter()
        # (0.20.21) the later Ultimates have a painter of their own in tools/ultimates/<id>.py, each with paint()
        import importlib
        sys.path.insert(0, str(Path(__file__).resolve().parent / "ultimates"))
        for name in ("pyre", "blur", "prism", "wisp", "gremlin", "knell"):
            importlib.import_module(name).paint()
        print("Ultimate skins written under", gx.ROOT)
