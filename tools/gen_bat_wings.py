"""Draws Kingdoms' vampire bat wings (0.7.609, BatWings): a 64x64 texture for a real bat wing, an arm membrane and a
hand membrane hung from their bones, finger bones fanning out of the wrist, the trailing edge scalloped between them.
Run from the repo root: python3 tools/gen_bat_wings.py

Layout (BatWings.Model): each membrane is a flat box (w x h x 0): its front face at (u, v) w x h, its back face at
(u + w, v), the back a mirror of the front. On the front, x grows to the right, so the root of each piece (the body for
the arm, the wrist for the hand) is its RIGHT edge. Arm membrane 9 x 13 at 0,0; hand membrane 12 x 14 at 0,16; the arm
bone (9x1x1) at 0,40; the hand bone (12x1x1) at 0,44; the thumb claw (1x2x1) at 0,48."""
import math
import random
from PIL import Image

OUT = "kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/vampire/bat_wings.png"
ARM, ARM_DEPTH, HAND, HAND_DEPTH = 9, 13, 12, 14
random.seed(26)
img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
px = img.load()

MEMBRANE = [(46, 30, 38), (52, 34, 42), (42, 27, 35), (57, 37, 45)]
THIN = [(78, 44, 52), (84, 48, 56)]  # near the edge the membrane thins and shows redder
BONE = (24, 18, 22)
BONE_LIT = (40, 30, 34)
CLAW = (196, 186, 170)


def membrane(x, y, edge):
    """A membrane pixel; edge = how close (0..1) to the trailing edge."""
    c = random.choice(THIN if edge > 0.82 and random.random() < 0.6 else MEMBRANE)
    # faint veins running out from the root
    if (x * 5 + y * 3) % 13 == 0:
        c = (c[0] + 16, c[1] + 2, c[2] + 4)
    return c + (255,)


def piece(u0, v0, w, h, depth_at, fingers):
    """Paints a membrane w x h whose root is on the right; depth_at(x) is how far it hangs at column x (0 = far end);
    fingers are (x_end, y_end) points the finger bones run to from the root's top (w-1, 0)."""
    front = [[None] * h for _ in range(w)]
    for x in range(w):
        d = depth_at(x)
        for y in range(h):
            if y < d:
                front[x][y] = membrane(x, y, y / max(d, 1))
    for (xe, ye) in fingers:
        steps = 40
        for i in range(steps + 1):
            t = i / steps
            x = round((w - 1) + (xe - (w - 1)) * t)
            y = round(0 + (ye - 0) * t)
            if 0 <= x < w and 0 <= y < h and front[x][y] is not None:
                front[x][y] = (BONE_LIT if t < 0.5 else BONE) + (255,)
    # the leading edge
    for x in range(w):
        if front[x][0] is not None:
            front[x][0] = BONE + (255,)
    for x in range(w):
        for y in range(h):
            if front[x][y] is not None:
                px[u0 + x, v0 + y] = front[x][y]
                px[u0 + w + (w - 1 - x), v0 + y] = front[x][y]  # the back face, mirrored


# the arm's membrane: from the flank (right) to the wrist (left), full depth at both, its edge scalloped once between
def arm_depth(x):
    base = ARM_DEPTH
    scallop = 3.0 * math.sin(math.pi * x / (ARM - 1))
    return round(base - scallop)


piece(0, 0, ARM, ARM_DEPTH, arm_depth, [])

# the hand's membrane: fingers fanning from the wrist (right) to the tip (left), scallops between them
FINGER_X = [0, 3, 7]  # where each finger meets the trailing edge
FINGER_Y = [1, 6, 11]


def hand_depth(x):
    # the edge runs through each finger end and bows up between them
    pts = [(FINGER_X[0], FINGER_Y[0]), (FINGER_X[1], FINGER_Y[1]), (FINGER_X[2], FINGER_Y[2]), (HAND - 1, HAND_DEPTH)]
    for (xa, ya), (xb, yb) in zip(pts, pts[1:]):
        if xa <= x <= xb:
            t = (x - xa) / max(xb - xa, 1)
            straight = ya + (yb - ya) * t
            return round(straight - 3.2 * math.sin(math.pi * t)) + 1
    return 1


piece(0, 16, HAND, HAND_DEPTH, hand_depth, [(FINGER_X[i], FINGER_Y[i] - 1) for i in range(3)])

# bones: the whole unwrapped box region, dark with a lighter ridge on top
for (v0, length) in ((40, ARM), (44, HAND)):
    for u in range(0, 2 + 2 * length):
        for v in range(v0, v0 + 2):
            px[u, v] = (BONE_LIT if v == v0 else BONE) + (255,)
# the thumb claw (1x2x1: u 0..4, v 48..51), pale horn going dark at the root
for u in range(0, 4):
    for v in range(48, 51):
        px[u, v] = (CLAW if v < 50 else BONE_LIT) + (255,)

img.save(OUT)
print("wrote", OUT)
