"""(0.20.21, Ultimates patch 1) Wisp's Ultimate, Revenant: its skin and glow map, 128x128, for client/.../ultimate/UltimateRevenant.java.

Wisp's pale mist (the same folds and frayed hems on the vanilla layout in the top-left 64x64) with a tattered hood, a bone-pale mask
over the face, bone collar plates, pauldrons and bracers, a few bone spikes, long trailing tendrils off the hem, and thin ice-blue seams
running through the cloth and round the plates. The glow map holds the seams, the eyes and the tips of the tendrils (fullbright).

    python3 tools/ultimates/wisp.py            paint both skins
    python3 tools/ultimates/wisp.py layout     print the UV table (the Java model holds the same table)
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
    "hood": (10, 10, 10), "mask": (8, 7, 1), "collar": (12, 2, 6), "cspike": (1, 5, 1), "tatters": (8, 7, 4),
    "tend_s": (2, 9, 1), "tend_l": (2, 13, 1), "pauld": (6, 3, 6), "bracer": (5, 4, 5),
}
PLATES = ("collar", "pauld", "bracer")
VANILLA = gx.BASE_PARTS

MIST = (176, 216, 232)
DEEP = (60, 98, 124)
BONE = (226, 220, 198)
ICE = (140, 224, 255)
ICE_HOT = (222, 248, 255)
VOID = (8, 12, 22, 255)


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


def patch(name):
    u, v = UV[name]
    w, h, d = DIMS[name]
    return (u, v, w, h, d)


def net(part):
    """The rectangle (x0, y0, x1, y1) a box's net covers."""
    u, v, w, h, d = part
    return (u, v, u + 2 * d + 2 * w, v + d + h)


def seam_px(skin, glow, x, y, color=ICE, strength=1.0):
    """A glowing seam pixel: a pale ice in the skin (so it reads in daylight) and the colour in the glow map."""
    skin.putpixel((x, y), gx.lerp(MIST, color, 0.75))
    glow.putpixel((x, y), tuple(int(c) for c in color) + (int(255 * strength),))


def seams(skin, glow, box, n, length=(4, 9)):
    """Thin wandering ice-blue seams inside the box, mostly running down the cloth."""
    x0, y0, x1, y1 = box
    if x1 - x0 < 2 or y1 - y0 < 2:
        return
    for _ in range(n):
        x, y = gx.rng.randrange(x0, x1), gx.rng.randrange(y0, y1)
        hot = gx.rng.random() < 0.3
        for _ in range(gx.rng.randrange(*length)):
            seam_px(skin, glow, x, y, ICE_HOT if hot else ICE, 0.8 + gx.rng.random() * 0.2)
            x = min(x1 - 1, max(x0, x + gx.rng.choice((-1, 0, 0, 1))))
            y = min(y1 - 1, max(y0 + 0, y + gx.rng.choice((0, 1, 1))))
            if y >= y1:
                break


def ice_rim(skin, glow, box, strength=0.75):
    """The ice line between plates: the bottom and right rim of a plate face glows."""
    x0, y0, x1, y1 = box
    for x in range(x0, x1):
        if (x + y1) % 3 != 0:
            seam_px(skin, glow, x, y1 - 1, ICE, strength)
    for y in range(y0, y1 - 1):
        if (y + x1) % 3 != 0:
            seam_px(skin, glow, x1 - 1, y, ICE, strength)


def fray(skin, box, depth, chance, from_bottom=True):
    """Ragged holes eaten into the lower edge of a face: the hem and sleeves fray into nothing."""
    x0, y0, x1, y1 = box
    for x in range(x0, x1):
        if gx.rng.random() < chance:
            rect(skin, (x, y1 - 1 - gx.rng.randrange(0, depth), x + 1, y1), CLEAR)


def paint():
    gx.rng.seed("wisp_ultimate")
    skin = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    glow = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    fold = shade(MIST, 0.78)
    # the cloth body on the vanilla layout: wavering folds of shadow down every face
    gx.parts(skin, MIST, 0.2, which=VANILLA)
    for part in VANILLA:
        for name, (x0, y0, x1, y1) in faces(*part).items():
            if name in ("top", "bottom"):
                continue
            for x in range(x0 + 1, x1, 3):
                xx = x
                for y in range(y0, y1):
                    skin.putpixel((xx, y), fold if gx.rng.random() < 0.8 else shade(MIST, 0.9))
                    if gx.rng.random() < 0.25:
                        xx = min(x1 - 1, max(x0, xx + gx.rng.choice((-1, 1))))
    # the sleeves and the hem fray
    for part in gx.LIMBS + (gx.BODY,):
        for name, box in faces(*part).items():
            if name == "bottom":
                rect(skin, box, CLEAR)
            elif name != "top":
                fray(skin, box, 3, 0.6)
    # thin ice seams through the cloth, on the body and the arms
    for part in (gx.BODY,) + gx.ARMS:
        for name, box in faces(*part).items():
            if name in ("front", "back", "left", "right"):
                seams(skin, glow, box, 3)
    # the chest: the insignia showing through the cloth, ringed in ice
    bx0, by0, bx1, by1 = gx.front(gx.BODY)
    for y in range(by0 + 3, by0 + 11):
        for x in range(bx0 + 1, bx1 - 1):
            if gx.rng.random() < 0.5:
                skin.putpixel((x, y), gx.lerp(MIST, (170, 240, 200), 0.4))
    gx.chest_insignia(skin, fill=(120, 230, 160, 255), rim=(90, 140, 150, 255), heart=(220, 255, 230, 255))
    # the head under the hood and mask: dim, with two cold pits
    gx.noise_fill(skin, DEEP, 0.2, gx.front(gx.HEAD))
    gx.eyes(skin, (16, 24, 36, 255), 11, wide=True)
    gx.clear_hat(skin)

    # the hood: deep blue-grey cloth, ragged at the lip, a cavern at the front with a cold light deep in each eye
    hood = patch("hood")
    for name, (x0, y0, x1, y1) in faces(*hood).items():
        gx.vgrad(skin, (x0, y0, x1, y1), shade(DEEP, gx.LIGHT[name] * 1.1), shade(DEEP, gx.LIGHT[name] * 0.8), 0.15)
        if name in ("front", "back", "left", "right"):
            for x in range(x0 + 1, x1, 3):
                rect(skin, (x, y0 + 2, x + 1, y1), shade(DEEP, gx.LIGHT[name] * 0.7))
            fray(skin, (x0, y0, x1, y1), 3, 0.55)
            seams(skin, glow, (x0, y0, x1, y1), 2, length=(3, 6))
    hx0, hy0, hx1, hy1 = gx.front(hood)
    gx.noise_fill(skin, (20, 32, 46), 0.25, (hx0 + 2, hy0 + 3, hx1 - 2, hy1))
    rect(skin, (hx0 + 2, hy0 + 4, hx0 + 4, hy0 + 7), VOID)
    rect(skin, (hx1 - 4, hy0 + 4, hx1 - 2, hy0 + 7), VOID)

    # the mask: bone-pale, hollow eye slits with ice-blue fire, a nose slot and a row of teeth along the bottom
    mask = patch("mask")
    gx.box(skin, mask, BONE, 0.12, edge=0.8)
    mx0, my0, mx1, my1 = gx.front(mask)
    rect(skin, (mx0 + 1, my0 + 2, mx0 + 3, my0 + 4), VOID)
    rect(skin, (mx1 - 3, my0 + 2, mx1 - 1, my0 + 4), VOID)
    for ex in (mx0 + 1, mx1 - 2):
        seam_px(skin, glow, ex, my0 + 3, ICE_HOT, 1.0)
        seam_px(skin, glow, ex + (1 if ex < mx0 + 4 else -1) * 0, my0 + 2, ICE, 0.9)
    rect(skin, (mx0 + 3, my0 + 4, mx0 + 5, my0 + 6), shade(BONE, 0.45))
    for x in range(mx0 + 1, mx1 - 1, 2):
        rect(skin, (x, my1 - 1, x + 1, my1), shade(BONE, 0.4))
    gxu.bevel(skin, (mx0, my0, mx1, my1), shade(BONE, 0.95))
    # a hairline crack down the brow, in ice
    for y in range(my0, my0 + 3):
        seam_px(skin, glow, mx0 + 4, y, ICE, 0.7)

    # the bone plates: collar across the shoulders, pauldrons, bracers; an ice seam runs round each
    for name in PLATES:
        part = patch(name)
        gx.box(skin, part, BONE, 0.14, edge=0.78)
        for fname, box in faces(*part).items():
            x0, y0, x1, y1 = box
            if x1 - x0 < 2 or y1 - y0 < 2:
                continue
            if fname in gxu.SIDES + ("top",):
                gxu.bevel(skin, box, shade(BONE, {"top": 1.1}.get(fname, 0.92)))
            if fname in gxu.SIDES:
                ice_rim(skin, glow, box)
    # the spikes: bone, an ice point
    spike = patch("cspike")
    gx.box(skin, spike, shade(BONE, 0.95), 0.12, edge=0.8)
    for fname, (x0, y0, x1, y1) in faces(*spike).items():
        if fname in gxu.SIDES:
            seam_px(skin, glow, x0, y0, ICE_HOT, 0.9)

    # the tatters (the hem) and the tendrils off it: mist, ragged, going to ice-lit points
    tat = patch("tatters")
    for name, (x0, y0, x1, y1) in faces(*tat).items():
        gx.vgrad(skin, (x0, y0, x1, y1), shade(MIST, gx.LIGHT[name]), shade(MIST, gx.LIGHT[name] * 0.85), 0.2)
        if name == "bottom":
            rect(skin, (x0, y0, x1, y1), CLEAR)
        elif name != "top":
            fray(skin, (x0, y0, x1, y1), 4, 0.65)
            seams(skin, glow, (x0, y0, x1, y1), 2, length=(3, 6))
    for name in ("tend_s", "tend_l"):
        part = patch(name)
        for fname, (x0, y0, x1, y1) in faces(*part).items():
            if fname in ("top", "bottom"):
                rect(skin, (x0, y0, x1, y1), CLEAR)
                continue
            h = y1 - y0
            for y in range(y0, y1):
                t = (y - y0) / max(1, h - 1)  # 0 at the root, 1 at the tip
                for x in range(x0, x1):
                    if gx.rng.random() < t * 0.55:
                        continue
                    skin.putpixel((x, y), gx.lerp(MIST, ICE, t))
                    if t > 0.6:
                        glow.putpixel((x, y), ICE + (int(120 + 135 * t),))

    # the glow is only drawn where the skin is solid
    for y in range(SIZE):
        for x in range(SIZE):
            if skin.getpixel((x, y))[3] == 0:
                glow.putpixel((x, y), CLEAR)
    save(skin, "entity/xenoform_wisp_ultimate.png")
    save(glow, "entity/xenoform_wisp_ultimate_glow.png")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "layout":
        for key, (u, v) in UV.items():
            print('uv("%s", %d, %d),' % (key, u, v))
    else:
        paint()
        print("Revenant skins written under", gx.ROOT)
