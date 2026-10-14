"""Draws the Great Ape's skin (textures/entity/zfighter_great_ape.png, 128x128) for ZFighterApeModel.

Each cube's faces are laid out the vanilla way from its texOffs (u, v) and size (w, h, d):
top (u+d, v), bottom (u+d+w, v); then at v+d: right side (u), front (u+d), left side (u+d+w), back (u+2d+w).
Everything is shaggy brown fur; the face, muzzle, chest and palms are bare tan skin; the eyes glow red.
"""
import random
import sys
from PIL import Image

FUR = (92, 52, 28)
FUR_DARK = (60, 32, 16)
FUR_LIGHT = (122, 74, 40)
SKIN = (196, 150, 108)
SKIN_DARK = (160, 112, 78)

img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
px = img.load()
rng = random.Random(7)


def fur(x, y):
    r = rng.random()
    if r < 0.18:
        return FUR_DARK + (255,)
    if r < 0.30:
        return FUR_LIGHT + (255,)
    return FUR + (255,)


def skin(x, y):
    return (SKIN_DARK if rng.random() < 0.15 else SKIN) + (255,)


def rect(x0, y0, w, h, paint):
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            px[x, y] = paint(x, y)


def cube(u, v, w, h, d, front=None):
    """Fur all over; front (u+d, v+d, w x h) painted by front(x, y, fx, fy) if given."""
    rect(u + d, v, w, d, fur)
    rect(u + d + w, v, w, d, fur)
    rect(u, v + d, d, h, fur)
    rect(u + d, v + d, w, h, fur)
    rect(u + d + w, v + d, d, h, fur)
    rect(u + 2 * d + w, v + d, w, h, fur)
    if front:
        for fy in range(h):
            for fx in range(w):
                c = front(fx, fy)
                if c:
                    px[u + d + fx, v + d + fy] = c


def head_face(fx, fy):
    # 10 x 9: bare face below the brow, red eyes
    if fy in (3, 4) and fx in (2, 3, 6, 7):
        return (255, 40, 30, 255) if fy == 3 else (150, 10, 10, 255)
    if 2 <= fy <= 8 and 1 <= fx <= 8:
        return skin(fx, fy)
    return None


def muzzle_face(fx, fy):
    # 7 x 5: nostrils up top, a dark mouth line, fangs
    if fy == 1 and fx in (2, 4):
        return (40, 20, 12, 255)
    if fy == 3:
        return (230, 225, 210, 255) if fx in (1, 5) else (70, 20, 16, 255)
    return skin(fx, fy)


def chest_front(fx, fy):
    # 12 x 13: a broad bare chest and belly
    if 2 <= fx <= 9 and 1 <= fy <= 11:
        return skin(fx, fy)
    return None


def palm_front(fx, fy):
    # arms 5 x 17: bare hands at the bottom
    return skin(fx, fy) if fy >= 13 else None


cube(0, 0, 10, 9, 9, head_face)      # head
cube(40, 0, 7, 5, 3, muzzle_face)    # muzzle
cube(64, 0, 10, 2, 2)                # brow
cube(90, 0, 2, 3, 1, lambda fx, fy: skin(fx, fy))  # ears
cube(0, 20, 12, 13, 7, chest_front)  # body
cube(40, 20, 10, 4, 4)               # shoulder hump
cube(0, 44, 5, 17, 5, palm_front)    # right arm
cube(24, 44, 5, 17, 5, palm_front)   # left arm
cube(48, 44, 6, 12, 6)               # right leg
cube(76, 44, 6, 12, 6)               # left leg
cube(0, 70, 2, 2, 12)                # tail

img.save(sys.argv[1])
