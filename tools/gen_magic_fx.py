"""Paints the Magic FX textures (core/fx/MagicFx): the mote's glint, the miasma's puff and the sigil's circle of marks.
All white or grey: the game tints them. Run from the root: python -I tools/gen_magic_fx.py"""
import math
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "jaymospowers", "textures")


def save(img, *path):
    out = os.path.join(ROOT, *path)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.save(out)


def mote(frame):
    """A four-point glint, 9x9: a white heart, arms that shorten frame by frame, a dim halo."""
    img = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
    px = img.load()
    arm = [4, 3, 2, 1][frame]
    halo = [2, 2, 1, 1][frame]
    for y in range(9):
        for x in range(9):
            dx, dy = abs(x - 4), abs(y - 4)
            if dx + dy <= halo and (dx, dy) != (0, 0):
                px[x, y] = (255, 255, 255, 120 if dx + dy == halo else 200)
    for i in range(1, arm + 1):
        a = int(255 * (1.0 - (i - 1) / (arm + 0.5)) ** 1.3)
        for x, y in ((4 + i, 4), (4 - i, 4), (4, 4 + i), (4, 4 - i)):
            px[x, y] = (255, 255, 255, max(px[x, y][3], a))
    px[4, 4] = (255, 255, 255, 255)
    return img


def miasma(frame, rnd):
    """A puff, 16x16: lumps of cloud that break up and thin frame by frame. Lighter on top, darker beneath."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    lumps = [(7.5 + rnd.uniform(-3.4, 3.4), 7.5 + rnd.uniform(-3.4, 3.4), rnd.uniform(3.0, 4.4)) for _ in range(7)]
    shrink = [1.0, 0.92, 0.8, 0.66, 0.5][frame]
    holes = [0.0, 0.08, 0.2, 0.36, 0.55][frame]
    for y in range(16):
        for x in range(16):
            best = 0.0
            for cx, cy, r in lumps:
                d = math.hypot(x - cx, y - cy) / (r * shrink)
                best = max(best, 1.0 - d)
            if best <= 0.0 or rnd.random() < holes * (1.2 - best):
                continue
            edge = best < 0.28
            shade = 255 - int(70 * (y / 15.0)) - (30 if edge else 0)
            alpha = 150 if edge else 230
            px[x, y] = (shade, shade, shade, alpha)
    return img


def sigil():
    """A circle of marks, 64x64: two rings, ticks between them, a square set on its corner inside, a dot at each
    point. No letters: marks only."""
    n = 64
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    px = img.load()
    c = (n - 1) / 2.0

    def dot(x, y, a=255):
        xi, yi = int(round(x)), int(round(y))
        if 0 <= xi < n and 0 <= yi < n:
            px[xi, yi] = (255, 255, 255, max(px[xi, yi][3], a))

    def circle(r, a=255, gap=None):
        steps = int(r * 14)
        for i in range(steps):
            t = i / steps * math.tau
            if gap and int(i / steps * gap[0]) % gap[1] == 0:
                continue
            dot(c + math.cos(t) * r, c + math.sin(t) * r, a)

    def line(x0, y0, x1, y1, a=255):
        steps = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
        for i in range(steps + 1):
            t = i / steps
            dot(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, a)

    circle(30.5)
    circle(29.5, 150)
    circle(24.5)
    circle(12.5, 220, gap=(24, 3))
    # ticks between the two rings: long at the quarters, short between
    for k in range(24):
        t = k / 24 * math.tau
        r0 = 25.5 if k % 6 else 24.5
        r1 = 27.5 if k % 2 else 29.0
        line(c + math.cos(t) * r0, c + math.sin(t) * r0, c + math.cos(t) * r1, c + math.sin(t) * r1, 255 if k % 2 == 0 else 190)
    # two squares, one on its corner
    for turn in (0.0, math.pi / 4):
        pts = [(c + math.cos(turn + k * math.pi / 2) * 24.0, c + math.sin(turn + k * math.pi / 2) * 24.0) for k in range(4)]
        for k in range(4):
            line(*pts[k], *pts[(k + 1) % 4], 210)
    # a mark at each of the eight points
    for k in range(8):
        t = k * math.pi / 4
        x, y = c + math.cos(t) * 19.0, c + math.sin(t) * 19.0
        for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            dot(x + dx, y + dy)
    for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
        dot(c + dx, c + dy)
    return img


def main():
    for f in range(4):
        save(mote(f), "particle", "magic", "mote_%d.png" % f)
    rnd = random.Random(26)
    for f in range(5):
        rnd.seed(26)  # the same lumps every frame, so it thins rather than jumps
        save(miasma(f, rnd), "particle", "magic", "miasma_%d.png" % f)
    save(sigil(), "fx", "sigil.png")


if __name__ == "__main__":
    main()
