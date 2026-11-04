"""(0.20.21, Ultimates patch 1) Pyre's Ultimate, Sunheart: its skin and glow map, 128x128, for client/.../ultimate/UltimateSunheart.java.

Pyre's body of living flame (the same fire_body paint on the vanilla layout in the top-left 64x64) with plates of cooled black
basalt on the shoulders, chest, back, belt, forearms and shins, a basalt crown ringed with flame-tipped spikes, and white-yellow
cracks in the plates. The glow map holds the flame parts (fullbright, so it shines in the dark) and every crack.

    python3 tools/ultimates/pyre.py            paint both skins
    python3 tools/ultimates/pyre.py layout     print the UV table (the Java model holds the same table)
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
    "crown": (9, 2, 9), "cspike": (1, 5, 1), "hair": (5, 10, 5), "tip": (3, 7, 3),
    "chestplate": (9, 8, 2), "core": (5, 7, 1), "backplate": (8, 8, 1), "belt": (9, 2, 6),
    "pauld": (6, 3, 6), "bracer": (5, 5, 5), "bspike": (1, 3, 1), "greave": (5, 5, 5),
    "plume": (3, 6, 3), "lick": (2, 5, 2),
}
FLAME_PARTS = ("hair", "tip", "plume", "lick")
PLATES = ("crown", "chestplate", "backplate", "belt", "pauld", "bracer", "greave")
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

BASALT = (36, 33, 40)
BASALT_LIGHT = (66, 62, 72)
SEAM = (255, 236, 140)      # white-yellow
SEAM_HOT = (255, 252, 214)
FLAME_LUM = 150


def patch(name):
    u, v = UV[name]
    w, h, d = DIMS[name]
    return (u, v, w, h, d)


def net(part):
    """The rectangle (x0, y0, x1, y1) a box's net covers."""
    u, v, w, h, d = part
    return (u, v, u + 2 * d + 2 * w, v + d + h)


def spike(skin, glow, part):
    """A basalt spike whose tip is flame: black at the root, ember, orange, then white-gold at the point."""
    for name, (x0, y0, x1, y1) in faces(*part).items():
        if name in ("top", "bottom"):
            rect(skin, (x0, y0, x1, y1), shade(BASALT, 0.9))
            continue
        h = y1 - y0
        for y in range(y0, y1):
            t = (y - y0) / max(1, h - 1)  # 0 at the tip, 1 at the root
            for x in range(x0, x1):
                if t < 0.35:
                    c = gx.lerp(gx.FIRE_PALE, gx.FIRE_GOLD, t / 0.35)
                    skin.putpixel((x, y), c)
                    glow.putpixel((x, y), c)
                elif t < 0.6:
                    skin.putpixel((x, y), gx.lerp(gx.FIRE_ORANGE, gx.FIRE_RED, (t - 0.35) / 0.25))
                    glow.putpixel((x, y), gx.lerp(gx.FIRE_ORANGE, gx.FIRE_RED, (t - 0.35) / 0.25)[:3] + (170,))
                else:
                    skin.putpixel((x, y), shade(BASALT, 0.85 + gx.rng.random() * 0.3))


def plate(skin, glow, name, side_seams=True):
    """A basalt plate: dark noisy rock, a lit rim, and white-yellow cracks and seams that glow."""
    part = patch(name)
    gx.box(skin, part, BASALT, 0.25, edge=0.8)
    for fname, box in faces(*part).items():
        x0, y0, x1, y1 = box
        if x1 - x0 < 2 or y1 - y0 < 2:
            continue
        if fname in gxu.SIDES + ("top",):
            gxu.bevel(skin, box, shade(BASALT_LIGHT, {"top": 1.2}.get(fname, 0.95)))
        area = (x1 - x0) * (y1 - y0)
        gxu.cracks(skin, glow, box, max(1, area // 22), length=(3, 8), color=SEAM)
        if side_seams and fname in gxu.SIDES:
            gxu.seam_rim(skin, glow, box, SEAM, 0.8)


def flame_glow(skin, glow, rect_box):
    """The flame in this rectangle shines: the hot pixels fully, the cooler ones dimmer; cracks already drawn are kept."""
    x0, y0, x1, y1 = rect_box
    for y in range(y0, y1):
        for x in range(x0, x1):
            r, g, b, a = skin.getpixel((x, y))
            if a and glow.getpixel((x, y))[3] == 0:
                glow.putpixel((x, y), (r, g, b, 255 if (r + g + b) / 3 > FLAME_LUM else 130))


def paint():
    gx.rng.seed("pyre_ultimate")
    skin = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    glow = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    # the body of living flame on the vanilla layout, with a hotter heart
    for part in VANILLA:
        gx.fire_body(skin, part)
        gx.veins(skin, gx.front(part), gx.FIRE_GOLD, gx.FIRE_WHITE, 2, length=(3, 6))
    for part in gx.LIMBS:
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name == "bottom":
                gx.noise_fill(skin, gx.FIRE_PALE, 0.08, (x0, y0, x1, y1))
            elif name != "top":
                for x in range(x0, x1):
                    skin.putpixel((x, y1 - 1), gx.lerp(gx.FIRE_PALE, gx.FIRE_WHITE, gx.rng.random()))
                    if gx.rng.random() < 0.5:
                        skin.putpixel((x, y1 - 2), gx.FIRE_YELLOW + (255,))
    # the face: a darker mask of flame round white-hot eyes
    hx0, hy0, hx1, hy1 = gx.front(gx.HEAD)
    for y in range(hy0 + 2, hy1):
        for x in range(hx0 + 1, hx1 - 1):
            skin.putpixel((x, y), gx.lerp(gx.FIRE_RED, gx.FIRE_ORANGE, gx.rng.random() * 0.8))
    rect(skin, (9, 10, 15, 11), gx.FIRE_EMBER + (255,))
    rect(skin, (9, 11, 11, 12), gx.FIRE_WHITE + (255,))
    rect(skin, (13, 11, 15, 12), gx.FIRE_WHITE + (255,))
    rect(skin, (9, 12, 11, 13), gx.FIRE_PALE + (255,))
    rect(skin, (13, 12, 15, 13), gx.FIRE_PALE + (255,))
    rect(skin, (11, 14, 13, 15), gx.FIRE_EMBER + (255,))
    for x in range(hx0, hx1):
        if gx.rng.random() < 0.6:
            skin.putpixel((x, hy0 + 1), gx.FIRE_YELLOW + (255,))
    # the loose flames
    for name in FLAME_PARTS:
        gx.flame_part(skin, patch(name))
    # the plates, and the spikes
    for name in PLATES:
        plate(skin, glow, name)
    spike(skin, glow, patch("cspike"))
    gx.box(skin, patch("bspike"), shade(BASALT, 1.1), 0.2, edge=0.8)
    for fname, (x0, y0, x1, y1) in faces(*patch("bspike")).items():
        if fname in gxu.SIDES:
            gxu.glow_px(skin, glow, x0, y0, SEAM_HOT, 0.9)
    # the sun in the chest: pale-hot, a ring of gold, the xeno insignia on it
    core = patch("core")
    gx.box(skin, core, gx.FIRE_PALE, 0.08)
    cx0, cy0, cx1, cy1 = gx.front(core)
    gx.outline(skin, (cx0, cy0, cx1, cy1), gx.FIRE_GOLD + (255,))
    gx.insignia(skin, (cx0 + cx1) // 2, cy0 + 1, small=True, fill=gx.GREEN, rim=gx.FIRE_RED + (255,), heart=(240, 255, 240, 255))
    # the crown's front plate gets a molten brow line
    bx0, by0, bx1, by1 = faces(*patch("crown"))["front"]
    for x in range(bx0, bx1):
        gxu.glow_px(skin, glow, x, by1 - 1, SEAM_HOT, 0.9)
    # the flame glows
    for part in VANILLA:
        flame_glow(skin, glow, net(part))
    for name in FLAME_PARTS + ("core",):
        flame_glow(skin, glow, net(patch(name)))
    # the glow is only drawn where the skin is solid
    for y in range(SIZE):
        for x in range(SIZE):
            if skin.getpixel((x, y))[3] == 0:
                glow.putpixel((x, y), CLEAR)
    save(skin, "entity/xenoform_pyre_ultimate.png")
    save(glow, "entity/xenoform_pyre_ultimate_glow.png")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "layout":
        for key, (u, v) in UV.items():
            print('uv("%s", %d, %d),' % (key, u, v))
    else:
        paint()
        print("Sunheart skins written under", gx.ROOT)
