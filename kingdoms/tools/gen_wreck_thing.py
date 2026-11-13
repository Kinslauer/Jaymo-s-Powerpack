"""Paints the hide of the thing under the wreck (kingdoms WreckThingModel: keep the boxes the same) and its spawn egg. Run from the repo root."""
import random
from PIL import Image

W, H = 128, 64
random.seed(11)
im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = im.load()
HIDE = [(122, 44, 70), (104, 36, 62), (86, 28, 54), (66, 22, 46)]
SPOT = (156, 78, 96)
PALE = [(214, 176, 182), (190, 146, 158)]
SUCKER = (238, 214, 214)
EYE = [(226, 198, 84), (196, 150, 48)]
PUPIL = (18, 10, 16)
BEAK = [(46, 34, 30), (26, 18, 16)]


def box(u, v, w, h, d):
    """The six faces of a box at (u, v): name -> (x, y, width, height)."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "east": (u, v + d, d, h), "north": (u + d, v + d, w, h),
            "west": (u + d + w, v + d, d, h), "south": (u + d + w + d, v + d, w, h)}


def hide(x0, y0, w, h):
    for x in range(x0, x0 + w):
        for y in range(y0, y0 + h):
            c = HIDE[min(3, ((y - y0) * 4) // h)]
            r = random.random()
            if r < 0.07:
                c = SPOT
            elif r < 0.2:
                c = tuple(max(0, v - 12) for v in c)
            px[x, y] = c + (255,)


def eye(x0, y0):
    """A wide eye, 8 by 6, with a bar of a pupil."""
    for i in range(8):
        for j in range(6):
            if (i in (0, 7)) and (j in (0, 5)):
                continue
            c = EYE[0] if 0 < j < 5 and 0 < i < 7 else EYE[1]
            if j in (2, 3) and 1 <= i <= 6:
                c = PUPIL
            px[x0 + i, y0 + j] = c + (255,)


body = box(0, 0, 24, 20, 24)
for name, (x, y, w, h) in body.items():
    if name == "top":
        for i in range(w):
            for j in range(h):
                edge = min(i, j, w - 1 - i, h - 1 - j)
                c = HIDE[1] if edge < 3 else PALE[1] if edge < 6 else PALE[0]
                if random.random() < 0.1:
                    c = tuple(max(0, v - 14) for v in c)
                px[x + i, y + j] = c + (255,)
    elif name == "bottom":
        for i in range(x, x + w):
            for j in range(y, y + h):
                px[i, j] = HIDE[3] + (255,)
    else:
        hide(x, y, w, h)
        if name in ("east", "west"):
            eye(x + 8, y + 5)
for name, (x, y, w, h) in box(0, 44, 6, 4, 6).items():
    for i in range(x, x + w):
        for j in range(y, y + h):
            px[i, j] = BEAK[(i + j) % 2] + (255,)
for name, (x, y, w, h) in box(96, 0, 5, 12, 5).items():
    if name == "south":
        # the inner face: pale, with a row of suckers
        for i in range(w):
            for j in range(h):
                c = PALE[(i + j) % 2]
                if i == w // 2 and j % 3 == 1:
                    c = SUCKER
                px[x + i, y + j] = c + (255,)
    elif name in ("top", "bottom"):
        for i in range(x, x + w):
            for j in range(y, y + h):
                px[i, j] = HIDE[2] + (255,)
    else:
        hide(x, y, w, h)
im.save("kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/beasts/wreck_thing.png")

# the spawn egg: the worm's egg in its own colours
egg = Image.open("kingdoms/src/main/resources/assets/jaymoskingdoms/textures/item/sand_worm_spawn_egg.png").convert("RGBA")
ep = egg.load()
SPOTS = {(6, 4), (9, 6), (5, 8), (10, 10), (7, 11), (4, 11), (9, 13), (11, 7), (7, 7)}
for x in range(egg.width):
    for y in range(egg.height):
        if ep[x, y][3]:
            sx, sy = x * 16 // egg.width, y * 16 // egg.height
            base = SUCKER if (sx, sy) in SPOTS else HIDE[min(3, sy // 4)]
            ep[x, y] = base + (255,)
egg.save("kingdoms/src/main/resources/assets/jaymoskingdoms/textures/item/wreck_thing_spawn_egg.png")
