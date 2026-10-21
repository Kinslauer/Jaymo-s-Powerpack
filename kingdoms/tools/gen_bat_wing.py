"""Draws Jaymo's Kingdoms' bat wing item (0.7.103, original art): a small leathery wing, vanilla style (16x16, no black
outline, light from the top-left), on the browns of vanilla's own bat texture with its ear purples for the veins.
The arm rises from the shoulder (left) to the wrist (top); four fingers fan out from the wrist, the first along the
leading edge to the tip (right); the membrane between them is bitten in to a scalloped trailing edge.
Run from kingdoms/: python -I tools/gen_bat_wing.py"""
import os

from PIL import Image

OUT = os.path.join("src", "main", "resources", "assets", "jaymoskingdoms", "textures", "item", "bat_wing.png")

# vanilla bat.png's browns, darkest to lightest, and its ear purples
BAT = [(0x1b, 0x17, 0x15), (0x26, 0x21, 0x1f), (0x31, 0x28, 0x22), (0x43, 0x37, 0x2f), (0x5f, 0x50, 0x46)]
VEIN = (0x5a, 0x3f, 0x58)
GLINT = (0x82, 0x5f, 0x7f)

SHOULDER, WRIST = (1.0, 7.0), (6.0, 2.0)
# finger tips, leading edge first, round to the shoulder's side
TIPS = [(14.5, 3.0), (14.5, 10.0), (10.5, 14.5), (4.5, 14.0)]
# how far each scallop between two tips is pulled in toward the wrist
SCALLOP = 0.38


def toward(a, b, t):
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)


def inside(poly, x, y):
    hit = False
    j = len(poly) - 1
    for i in range(len(poly)):
        (xi, yi), (xj, yj) = poly[i], poly[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
            hit = not hit
        j = i
    return hit


def line(a, b):
    """The pixels on a line from a to b."""
    out = []
    for s in range(61):
        p = toward(a, b, s / 60)
        q = (min(15, int(p[0])), min(15, int(p[1])))
        if q not in out:
            out.append(q)
    return out


rim = [TIPS[0], TIPS[1], TIPS[2], TIPS[3], SHOULDER]
poly = [SHOULDER, WRIST]
for a, b in zip(rim, rim[1:]):
    poly.append(a)
    poly.append(toward(toward(a, b, 0.5), WRIST, SCALLOP))

mask = {(x, y) for x in range(16) for y in range(16) if inside(poly, x + 0.5, y + 0.5)}
arm = set(line(SHOULDER, WRIST)) | set(line(WRIST, TIPS[0]))
fingers = set()
for tip in TIPS[1:]:
    fingers |= set(line(WRIST, toward(WRIST, tip, 0.97)))
mask |= arm

img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
px = img.load()
for (x, y) in mask:
    lower_right = (x + 1, y) not in mask or (x, y + 1) not in mask
    if (x, y) in arm:
        c = BAT[4]
    elif lower_right:
        c = BAT[0]
    elif (x, y) in fingers:
        c = BAT[3]
    else:
        # membrane: darker away from the light (top-left)
        c = BAT[2] if x + y < 15 else BAT[1]
    px[x, y] = c + (255,)

# veins in the membrane, a glint at the wrist
for (x, y) in [(4, 8), (9, 7), (12, 9), (8, 11)]:
    if (x, y) in mask and px[x, y][:3] in (BAT[1], BAT[2]):
        px[x, y] = VEIN + (255,)
wx, wy = int(WRIST[0]), int(WRIST[1])
px[wx, wy] = GLINT + (255,)

img.save(OUT)
print("wrote", OUT)
