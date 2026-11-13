"""Paints the sand worm's hide (kingdoms SandWormModel: keep the boxes the same). Run from the repo root."""
import random
from PIL import Image

W, H = 128, 64
random.seed(7)
im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = im.load()
SAND = [(214, 196, 148), (201, 181, 132), (186, 165, 118), (168, 146, 102)]
RIDGE = (132, 110, 76)
BELLY = (226, 212, 170)
MAW = [(92, 28, 30), (64, 16, 20), (40, 8, 12)]
TOOTH = [(238, 232, 214), (210, 200, 176)]


def box(u, v, w, h, d):
    """The six faces of a box at (u, v): name -> (x, y, width, height)."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "east": (u, v + d, d, h), "north": (u + d, v + d, w, h),
            "west": (u + d + w, v + d, d, h), "south": (u + d + w + d, v + d, w, h)}


def hide(x0, y0, w, h, rim):
    for x in range(x0, x0 + w):
        for y in range(y0, y0 + h):
            row = y - y0
            c = SAND[min(3, (row * 4) // h)] if not rim else SAND[row % 2]
            if not rim and row >= h - 3:
                c = RIDGE if row == h - 3 else SAND[3]
            if row == h - 1 or row == 0:
                c = RIDGE
            if random.random() < 0.12:
                c = tuple(max(0, v - 14) for v in c)
            if random.random() < 0.05:
                c = BELLY
            px[x, y] = c + (255,)


ring = box(0, 0, 18, 12, 18)
for name, (x, y, w, h) in ring.items():
    if name in ("top", "bottom"):
        for i in range(x, x + w):
            for j in range(y, y + h):
                px[i, j] = RIDGE + (255,)
    else:
        hide(x, y, w, h, False)
head = box(0, 30, 20, 8, 20)
for name, (x, y, w, h) in head.items():
    if name == "top":
        for i in range(w):
            for j in range(h):
                edge = min(i, j, w - 1 - i, h - 1 - j)
                c = SAND[1] if edge < 2 else MAW[min(2, (edge - 2) // 2)]
                if edge in (2, 4) and (i + j) % 2 == 0:
                    c = TOOTH[1]
                px[x + i, y + j] = c + (255,)
    elif name == "bottom":
        for i in range(x, x + w):
            for j in range(y, y + h):
                px[i, j] = RIDGE + (255,)
    else:
        hide(x, y, w, h, True)
for name, (x, y, w, h) in box(84, 0, 3, 8, 3).items():
    for i in range(x, x + w):
        for j in range(y, y + h):
            px[i, j] = TOOTH[(i + j) % 2] + (255,)
im.save("kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/beasts/sand_worm.png")
