"""Paints the Magic FX textures (core/fx/MagicFx): the mote's glint, the miasma's puff, the spark's crackle, the
rune's marks, the sigil's circle of marks, the wave's running water, the stroke's brush mark and the pool with its ripples.
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


def spark(frame, rnd):
    """A crackle, 9x9: a short jagged fork of light, a different one each frame."""
    img = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
    px = img.load()
    x, y = rnd.choice((1, 2)), rnd.choice((0, 1))
    path = [(x, y)]
    while y < 8 and x < 8:
        step = rnd.choice(((1, 1), (1, 1), (0, 1), (1, 0), (-1, 1)))
        x, y = max(0, min(8, x + step[0])), min(8, y + step[1])
        path.append((x, y))
    # a fork off the middle
    fx, fy = path[len(path) // 2]
    fork = []
    for _ in range(3):
        fx, fy = min(8, fx + 1), max(0, fy + rnd.choice((-1, 0)))
        fork.append((fx, fy))
    for qx, qy in fork:
        px[qx, qy] = (255, 255, 255, 170)
    for qx, qy in path:
        px[qx, qy] = (255, 255, 255, 255)
    return img


def rune(index):
    """A mark, 7x7: strokes only (uprights, bars, slants, dots), never a letter of any alphabet."""
    marks = [
        ["..#....", "..#....", ".###...", "..#..#.", "..#.#..", "..##...", "..#...."],
        ["#.....#", ".#...#.", "..#.#..", "...#...", "...#...", "...#...", ".#####."],
        [".#####.", ".#.....", ".#.###.", ".#...#.", ".###.#.", ".....#.", ".#####."],
        ["...#...", "..#.#..", ".#...#.", "#..#..#", ".#...#.", "..#.#..", "...#..."],
        ["#..#..#", "#..#..#", "#..#..#", "#######", "...#...", "...#...", "..###.."],
        ["..###..", ".#...#.", ".#.#.#.", ".#...#.", "..###..", "...#...", ".#.#.#."],
    ]
    img = Image.new("RGBA", (7, 7), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(marks[index]):
        for x, ch in enumerate(row):
            if ch == "#":
                px[x, y] = (255, 255, 255, 255)
    return img


def wave():
    """Water running up a wave, 32x32, tiling both ways: streaks of lighter and darker run, a few gaps."""
    rnd = random.Random(51)
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = img.load()
    for x in range(32):
        base = rnd.choice((150, 170, 190, 210, 230))
        solid = rnd.random() > 0.12
        for y in range(32):
            px[x, y] = (base, base, base, 255 if solid else 150)
    for _ in range(26):
        x, y, n = rnd.randrange(32), rnd.randrange(32), rnd.randint(4, 12)
        v = rnd.choice((255, 255, 120))
        for i in range(n):
            px[x, (y + i) % 32] = (v, v, v, 255)
    for _ in range(40):
        px[rnd.randrange(32), rnd.randrange(32)] = (255, 255, 255, 255)
    return img


def stroke():
    """One brush mark, 64x16, heavy at the left and dry at the right: a blob where the brush lands, a full body, then
    the bristles part into streaks that break up and run out."""
    rnd = random.Random(52)
    img = Image.new("RGBA", (64, 16), (0, 0, 0, 0))
    px = img.load()
    bristles = [rnd.random() for _ in range(16)]
    for x in range(64):
        t = x / 63.0
        # half height of the mark at this point: lands fat, settles, thins toward the end
        if t < 0.12:
            half = 3.0 + 4.6 * math.sin(t / 0.12 * math.pi / 2)
        else:
            half = 7.6 - 3.4 * ((t - 0.12) / 0.88) ** 1.4
        half += rnd.choice((-0.5, 0.0, 0.0, 0.5))
        dry = max(0.0, (t - 0.45) / 0.55)
        for y in range(16):
            d = abs(y - 7.5)
            if d > half:
                continue
            # dry brush: each bristle row gives out at its own point along the mark
            if dry > 0 and bristles[y] < dry * 1.15 and rnd.random() < 0.55 + dry * 0.45:
                continue
            a = 255
            if half - d < 1.0 and rnd.random() < 0.5:
                a = 150
            if dry > 0.5 and rnd.random() < 0.3:
                a = 150
            v = 255 if rnd.random() > 0.15 else 215
            px[x, y] = (v, v, v, a)
    # a few flecks thrown off where it lands and where it ends
    for fx, fy in ((1, 2), (3, 14), (0, 9), (60, 3), (62, 11), (57, 13)):
        px[fx, fy] = (255, 255, 255, 200)
    return img


def pool():
    """A sheet lying on the ground, 64x64: a solid disc with a ragged, lapping rim."""
    rnd = random.Random(53)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()
    lobes = [(rnd.uniform(0, math.tau), rnd.choice((3, 5, 7, 9)), rnd.uniform(0.3, 0.7)) for _ in range(4)]
    for y in range(64):
        for x in range(64):
            dx, dy = x - 31.5, y - 31.5
            d = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            rim = 28.5 + sum(amp * math.sin(a * n + ph) for ph, n, amp in lobes)
            if d > rim:
                continue
            edge = rim - d
            alpha = 255 if edge > 2.5 else (190 if edge > 1.2 else 120)
            v = 255 if edge > 2.5 or rnd.random() > 0.3 else 200
            px[x, y] = (v, v, v, alpha)
    return img


def ripple():
    """The ripples that run out over a pool, 64x64: two broken rings."""
    rnd = random.Random(54)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()
    for radius, gaps in ((27.0, 5), (17.0, 3)):
        cuts = [(rnd.uniform(0, math.tau), rnd.uniform(0.25, 0.6)) for _ in range(gaps)]
        steps = int(radius * 8)
        for i in range(steps):
            a = i / steps * math.tau
            if any(abs((a - c + math.pi) % math.tau - math.pi) < w / 2 for c, w in cuts):
                continue
            x, y = int(round(31.5 + math.cos(a) * radius)), int(round(31.5 + math.sin(a) * radius))
            px[x, y] = (255, 255, 255, 255)
    return img


def main():
    save(wave(), "fx", "wave.png")
    save(stroke(), "fx", "stroke.png")
    save(pool(), "fx", "pool.png")
    save(ripple(), "fx", "ripple.png")
    for f in range(4):
        save(mote(f), "particle", "magic", "mote_%d.png" % f)
    rnd = random.Random(26)
    for f in range(5):
        rnd.seed(26)  # the same lumps every frame, so it thins rather than jumps
        save(miasma(f, rnd), "particle", "magic", "miasma_%d.png" % f)
    save(sigil(), "fx", "sigil.png")
    rnd = random.Random(63)
    for f in range(4):
        save(spark(f, rnd), "particle", "magic", "spark_%d.png" % f)
    for f in range(6):
        save(rune(f), "particle", "magic", "rune_%d.png" % f)


if __name__ == "__main__":
    main()
