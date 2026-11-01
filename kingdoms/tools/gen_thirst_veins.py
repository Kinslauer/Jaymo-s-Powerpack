"""Draws the blood-thirst veins that creep in from a vampire's screen edges (client ThirstVeins).

Six layers, textures/gui/thirst_veins_1..6.png (320x180, drawn stretched over the screen with nearest filtering so the
pixels stay chunky like the rest of the pack). Layer 1 is the stubs at the very edge; each next layer is the next ring
of growth inward, so drawing layers 1..n shows veins reaching further the higher n is. A pixel's layer is the deepest
point its branch had reached by then, so every layer joins onto the ones before it.

Run from kingdoms/: python3 tools/gen_thirst_veins.py
"""
import math
import random
from pathlib import Path

from PIL import Image

W, H = 320, 180
STAGES = 6
# how far in the veins may reach at full thirst (0 = edge, 1 = centre); the middle stays clear to see by
REACH = 0.62
# how round the rings of growth are (2 = an ellipse, higher = squarer)
ROUND = 3.0
OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/jaymoskingdoms/textures/gui"

# palette: bruise haze, the vein's dark wall, its lit middle
HAZE = (58, 0, 12, 60)
WALL = (74, 8, 18, 236)
CORE = (138, 22, 34, 245)
TIP = (92, 12, 22, 190)

rng = random.Random(0x7EEF)
CX, CY = (W - 1) / 2, (H - 1) / 2


def inward(x, y):
    """0 at the screen's edge, 1 at its centre, on rounded-rectangle rings (corners reach in furthest)."""
    return 1.0 - ((abs(x - CX) / CX) ** ROUND + (abs(y - CY) / CY) ** ROUND) ** (1 / ROUND)


# per pixel: (stage, kind) with kind 1 haze, 2 wall, 3 core, 4 tip; the earliest stage wins, then the stronger kind
grid = {}


def mark(x, y, stage, kind):
    if not (0 <= x < W and 0 <= y < H):
        return
    old = grid.get((x, y))
    if old is None or stage < old[0] or (stage == old[0] and kind > old[1]):
        grid[(x, y)] = (stage, kind)


def stage_of(depth):
    return max(0, min(STAGES - 1, int(depth / REACH * STAGES)))


def grow(x, y, angle, width, budget, depth, reach):
    """One branch: a wandering line that thins as it goes and sometimes forks, stopping at its own reach."""
    length = 0
    while budget > 0:
        here = inward(x, y)
        if here > reach or not (0 <= x < W and 0 <= y < H) and length > 4:
            break
        depth = max(depth, here)
        s = stage_of(depth)
        ix, iy = int(round(x)), int(round(y))
        w = width if budget > 6 else 1
        if w >= 3:
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    if dx == 0 or dy == 0:
                        mark(ix + dx, iy + dy, s, 2)
            mark(ix, iy, s, 3)
        elif w == 2:
            for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
                mark(ix + dx, iy + dy, s, 2)
            mark(ix, iy, s, 3)
        else:
            mark(ix, iy, s, 4 if budget <= 6 else 2)
        # bruise round the vein
        r = w + 1
        for dx in range(-r, r + 1):
            for dy in range(-r, r + 1):
                if dx * dx + dy * dy <= r * r:
                    mark(ix + dx, iy + dy, s, 1)
        # wander, with a pull toward the centre so they creep inward
        to_centre = math.atan2(CY - y, CX - x)
        diff = (to_centre - angle + math.pi) % (2 * math.pi) - math.pi
        angle += rng.gauss(0.0, 0.28) + diff * 0.08
        x += math.cos(angle)
        y += math.sin(angle)
        budget -= 1
        length += 1
        if length % 14 == 0 and width > 1 and rng.random() < 0.35:
            width -= 1
        if length > 8 and rng.random() < 0.035:
            side = rng.choice((-1, 1))
            grow(x, y, angle + side * rng.uniform(0.5, 1.1), max(1, width - 1), int(budget * rng.uniform(0.3, 0.6)), depth,
                 reach * rng.uniform(0.8, 1.0))


def seeds():
    """Trunk roots along every edge, thicker and closer together near the corners."""
    out = []
    for edge in range(4):
        span = W if edge < 2 else H
        t = rng.uniform(0, 10)
        while t < span:
            corner = min(t, span - t) / span  # 0 at a corner, 0.5 mid-edge
            if edge == 0:
                p, a = (t, -1.0), math.pi / 2
            elif edge == 1:
                p, a = (t, H), -math.pi / 2
            elif edge == 2:
                p, a = (-1.0, t), 0.0
            else:
                p, a = (W, t), math.pi
            width = 3 if corner < 0.22 or rng.random() < 0.35 else 2
            out.append((p, a + rng.uniform(-0.5, 0.5), width))
            t += rng.uniform(12, 20) + corner * (40 if edge < 2 else 16)
    return out


for (sx, sy), a, w in seeds():
    grow(sx, sy, a, w, rng.randint(90, 170), -1.0, REACH * rng.uniform(0.55, 1.0))

colours = {1: HAZE, 2: WALL, 3: CORE, 4: TIP}
OUT.mkdir(parents=True, exist_ok=True)
for s in range(STAGES):
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    for (x, y), (stage, kind) in grid.items():
        if stage == s:
            px[x, y] = colours[kind]
    img.save(OUT / f"thirst_veins_{s + 1}.png")
print("wrote", STAGES, "layers to", OUT)
