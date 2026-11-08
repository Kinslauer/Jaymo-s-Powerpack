"""(0.21.37, Ultimates patch 2) Rivet's Ultimate, Ironpour: its skin and glow map, 128x128, for client/.../ultimate/UltimateIronpour.java.

Rivet's own build (the liquid-metal puddle that stands up to fight: pool, mound, lobes, wake, hunched torso, arms and legs) grown bigger
and plated: riveted gunmetal plates float on the metal in the puddle and ride the shoulders, belt, back, forearms and shins when it
stands, a ridge of gear-tooth spikes runs down the back (and rises from the puddle), a plated brow and crest sit on the head. The metal
is quicksilver in steel blue; its seams (circuit lines down the limbs, the rims of the plates, rings across the pool) glow steel blue
(0x9FB4C0) in the glow map and turn white-hot at the joints (wrists, ankles, plate edges, the gear tips, the eyes).

    python3 tools/ultimates/rivet.py            paint both skins
    python3 tools/ultimates/rivet.py layout     print the UV table (the Java model holds the same table)
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
    "head": (6, 5, 6), "brow": (7, 2, 3), "crown": (4, 1, 4),
    "body": (8, 9, 4), "pauld": (5, 2, 5), "belt": (9, 2, 5), "back": (6, 6, 1), "gear": (1, 3, 1), "tooth": (3, 1, 1),
    "arm": (4, 12, 4), "bracer": (5, 4, 5), "leg": (4, 12, 4), "greave": (5, 4, 5),
    "pool": (14, 2, 16), "mound": (10, 3, 10), "lobe": (4, 3, 4), "wake": (6, 2, 8), "plate": (5, 1, 5), "fin": (2, 4, 1),
}
UV = gxu.pack(DIMS)

STEEL = (159, 180, 192)      # 0x9FB4C0, the seams
BRIGHT = (196, 214, 226)
METAL = (150, 168, 182)
GUN = (84, 96, 108)          # riveted gunmetal plate
GUN_DARK = (50, 58, 68)
HOT = (255, 238, 205)        # white-hot
HEAT = (255, 188, 120)

PLATES = ("brow", "crown", "pauld", "belt", "back", "gear", "tooth", "bracer", "greave", "plate", "fin")
LIQUID = ("head", "body", "arm", "leg", "pool", "mound", "lobe", "wake")
SIDES = ("front", "back", "left", "right")


def patch(name):
    u, v = UV[name]
    w, h, d = DIMS[name]
    return (u, v, w, h, d)


def lit(skin, glow, x, y, color, strength=1.0):
    """A glowing pixel: a dim version in the skin (a seam in daylight) and the colour in the glow map."""
    skin.putpixel((x, y), shade(color, 0.7 + gx.rng.random() * 0.15))
    glow.putpixel((x, y), tuple(int(c) for c in color) + (int(255 * strength),))


def liquid(skin, name):
    """Quicksilver: a steel-blue sheen, bright drips down every side face."""
    for face, (x0, y0, x1, y1) in faces(*patch(name)).items():
        gx.vgrad(skin, (x0, y0, x1, y1), BRIGHT, shade(METAL, gx.LIGHT[face] * 0.65), 0.08)
        if face in SIDES:
            for _ in range(max(1, (x1 - x0) // 3)):
                x = gx.rng.randrange(x0, x1)
                top = gx.rng.randrange(y0, max(y0 + 1, y1 - 3))
                for y in range(top, min(y1, top + gx.rng.randint(2, 5))):
                    skin.putpixel((x, y), (240, 248, 255, 255))


def bevel(skin, box, base):
    """A lit top-left and a shaded bottom-right rim: reads as a raised plate."""
    x0, y0, x1, y1 = box
    if x1 - x0 < 3 or y1 - y0 < 3:
        return
    rect(skin, (x0, y0, x1, y0 + 1), shade(base, 1.7))
    rect(skin, (x0, y0, x0 + 1, y1), shade(base, 1.45))
    rect(skin, (x0, y1 - 1, x1, y1), shade(base, 0.6))
    rect(skin, (x1 - 1, y0, x1, y1), shade(base, 0.7))


def rivets(skin, box, base):
    """A bright rivet in each corner of a plate face."""
    x0, y0, x1, y1 = box
    if x1 - x0 < 5 or y1 - y0 < 4:
        return
    for x, y in ((x0 + 1, y0 + 1), (x1 - 2, y0 + 1), (x0 + 1, y1 - 2), (x1 - 2, y1 - 2)):
        skin.putpixel((x, y), shade(base, 1.9))
        if x + 1 < x1 - 1 and y + 1 < y1 - 1:
            skin.putpixel((x + 1, y + 1), shade(base, 0.45))


def seam_rim(skin, glow, box, strength=0.8):
    """The steel-blue line between plates: the bottom and right rim glows, in dashes, white-hot in the corner."""
    x0, y0, x1, y1 = box
    for x in range(x0, x1):
        if (x + y1) % 3 != 0:
            lit(skin, glow, x, y1 - 1, STEEL, strength)
    for y in range(y0, y1 - 1):
        if (y + x1) % 3 != 0:
            lit(skin, glow, x1 - 1, y, STEEL, strength)
    lit(skin, glow, x1 - 1, y1 - 1, HOT, 1.0)


def circuit(skin, glow, box, name):
    """The circuit lines of a limb or the torso: a seam down the middle of the outer faces, a white-hot ring at the joint."""
    x0, y0, x1, y1 = box
    cx = (x0 + x1) // 2
    for y in range(y0 + 1, y1 - 3):
        if (y - y0) % 4 != 3:
            lit(skin, glow, cx, y, STEEL, 0.85)
    for x in range(x0, x1):
        lit(skin, glow, x, y1 - 3, HEAT if (x + y1) % 2 == 0 else HOT, 1.0)
        skin.putpixel((x, y1 - 2), shade(GUN_DARK, 1.0))


def paint():
    """Ironpour: quicksilver in steel blue under riveted gunmetal plates, steel-blue seams going white-hot at the joints."""
    gx.rng.seed("rivet_ultimate")
    skin = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    glow = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    for name in DIMS:
        part = patch(name)
        if name in LIQUID:
            liquid(skin, name)
        else:
            gx.box(skin, part, GUN, 0.18, edge=0.7)
    for name in PLATES:
        for face, box in faces(*patch(name)).items():
            if name in ("gear", "tooth", "fin"):
                if face in SIDES:
                    x0, y0, x1, y1 = box
                    gx.vgrad(skin, box, shade(GUN, 1.15), shade(GUN_DARK, 0.9), 0.1)
                    lit(skin, glow, x0, y0, HOT, 1.0)
                continue
            if face in SIDES + ("top",):
                bevel(skin, box, GUN)
                rivets(skin, box, GUN)
            if face in SIDES:
                seam_rim(skin, glow, box)
    # the limbs: circuit lines on the outer faces, rings of heat at the wrist and ankle
    for name in ("arm", "leg"):
        for face, box in faces(*patch(name)).items():
            if face in SIDES:
                circuit(skin, glow, box, name)
    # the torso: a dark seam down each side of its front, steel-blue lines down its flanks, white-hot at the waist
    bx0, by0, bx1, by1 = faces(*patch("body"))["front"]
    for y in range(by0, by1):
        skin.putpixel((bx0, y), shade(GUN_DARK, 1.0))
        skin.putpixel((bx1 - 1, y), shade(GUN_DARK, 1.0))
    for face in ("left", "right"):
        x0, y0, x1, y1 = faces(*patch("body"))[face]
        for y in range(y0 + 1, y1 - 1):
            lit(skin, glow, x0 + 1, y, STEEL, 0.8)
    for x in range(bx0 + 1, bx1 - 1):
        lit(skin, glow, x, by1 - 1, HOT if x % 2 == 0 else HEAT, 1.0)
    # the insignia on the torso's front (the model puts it on the torso, over this patch)
    gx.insignia(skin, (bx0 + bx1) // 2, by0 + 2, small=True, rim=(30, 40, 52, 255))
    # the face: smooth, with two white-hot slit eyes
    fx0, fy0, fx1, fy1 = faces(*patch("head"))["front"]
    for ex in (fx0 + 1, fx1 - 3):
        lit(skin, glow, ex, fy0 + 2, HOT, 1.0)
        lit(skin, glow, ex + 1, fy0 + 2, HEAT, 1.0)
    # rings rippling out across the top of the pool and the mound, steel blue, with the odd white-hot fleck
    for name, insets in (("pool", (1, 3, 5)), ("mound", (1, 3))):
        x0, y0, x1, y1 = faces(*patch(name))["top"]
        for inset in insets:
            ix0, iy0, ix1, iy1 = x0 + inset, y0 + inset, x1 - inset, y1 - inset
            for x in range(ix0, ix1):
                for y in (iy0, iy1 - 1):
                    if (x + y) % 4 != 0:
                        lit(skin, glow, x, y, STEEL, 0.7)
            for y in range(iy0, iy1):
                for x in (ix0, ix1 - 1):
                    if (x + y) % 4 != 0:
                        lit(skin, glow, x, y, STEEL, 0.7)
        for _ in range(5):
            lit(skin, glow, gx.rng.randrange(x0 + 1, x1 - 1), gx.rng.randrange(y0 + 1, y1 - 1), HOT, 1.0)
    # the glow is drawn only where the skin is solid
    for y in range(SIZE):
        for x in range(SIZE):
            if skin.getpixel((x, y))[3] == 0:
                glow.putpixel((x, y), CLEAR)
    save(skin, "entity/xenoform_rivet_ultimate.png")
    save(glow, "entity/xenoform_rivet_ultimate_glow.png")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "layout":
        for key, (u, v) in UV.items():
            print('uv("%s", %d, %d),' % (key, u, v))
    else:
        paint()
        print("Ironpour skins written under", gx.ROOT)
