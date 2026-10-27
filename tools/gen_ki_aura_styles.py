"""(0.17.46, Andy's NOEA picks) Generates the textures of the Z Fighter aura styles beside the flame aura's
(tools/gen_ki_aura.py): the Smooth shell's slow shimmer, the Godly wisps' strands, and the Sparking aura's little
electric bolts and the Godly motes (one strip of five 16 px cells: four bolts and a soft dot). All additive (dark is
clear, light glows); the first two tile both ways so they can scroll up and wrap round. Original art, painterly pixel:
the greys are posterised into a few steps with a little grain, like the flame aura's. Run from the repo root:
python3 -I tools/gen_ki_aura_styles.py"""
import math
import random
from PIL import Image

DIR = "src/main/resources/assets/jaymospowers/textures/entity/"
W, H = 64, 128
TAU = 2 * math.pi


def posterise(light, rng, steps=14):
    light = min(1.0, max(0.0, light + rng.uniform(-0.025, 0.025)))
    return int(255 * round(light * steps) / steps)


def smooth():
    """A calm shell: broad soft bands drifting diagonally, a finer slow shimmer on them, a few faint glints."""
    rng = random.Random(41)
    glints = [(rng.random(), rng.random(), rng.uniform(0.03, 0.06)) for _ in range(9)]
    img = Image.new("RGB", (W, H))
    for y in range(H):
        for x in range(W):
            u, v = x / W, y / H
            light = 0.50
            light += 0.16 * math.sin(TAU * (2 * u + 1 * v) + 0.6)
            light += 0.10 * math.sin(TAU * (3 * u - 2 * v) + 2.1)
            light += 0.05 * math.sin(TAU * (5 * u + 4 * v) + 4.0)
            for gx, gy, r in glints:
                dx, dy = (u - gx + 0.5) % 1.0 - 0.5, (v - gy + 0.5) % 1.0 - 0.5
                d = math.hypot(dx * 1.0, dy * 0.5) / r
                if d < 1.0:
                    light += 0.22 * (1.0 - d * d)
            c = posterise(light * 0.9, rng)
            img.putpixel((x, y), (c, c, c))
    img.save(DIR + "zfighter_ki_aura_smooth.png")


def godly():
    """Thin sinuous strands rising, each fading in at its foot and out at its tip, soft and see-through."""
    rng = random.Random(77)
    strands = [(rng.random(), rng.uniform(0.012, 0.03), rng.uniform(0.015, 0.05), rng.randint(1, 3), rng.random(),
                rng.uniform(0.35, 0.8)) for _ in range(20)]
    img = Image.new("RGB", (W, H))
    for y in range(H):
        for x in range(W):
            u, v = x / W, y / H
            light = 0.03
            for cx, width, sway, waves, start, bright in strands:
                phase = (v - start) % 1.0
                env = math.sin(math.pi * phase) ** 1.6
                centre = cx + sway * math.sin(TAU * (waves * v + start))
                d = abs((u - centre + 0.5) % 1.0 - 0.5) / (width * (0.4 + 0.6 * env) + 0.003)
                if d < 1.0:
                    light += bright * env * (1.0 - d * d)
            c = posterise(min(0.85, light), rng, 12)
            img.putpixel((x, y), (c, c, c))
    img.save(DIR + "zfighter_ki_aura_godly.png")


def line(img, x0, y0, x1, y1, core, halo):
    n = max(abs(x1 - x0), abs(y1 - y0), 1)
    for i in range(n + 1):
        x = round(x0 + (x1 - x0) * i / n)
        y = round(y0 + (y1 - y0) * i / n)
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                px, py = x + dx, y + dy
                if 0 <= px < img.width and 0 <= py < img.height and (dx or dy):
                    cur = img.getpixel((px, py))[0]
                    img.putpixel((px, py), (max(cur, halo),) * 3)
    for i in range(n + 1):
        x = round(x0 + (x1 - x0) * i / n)
        y = round(y0 + (y1 - y0) * i / n)
        if 0 <= x < img.width and 0 <= y < img.height:
            img.putpixel((x, y), (core,) * 3)


def sparks():
    """Four little forked bolts (cells 0-3, top to bottom, centred) and a soft dot (cell 4)."""
    rng = random.Random(5)
    img = Image.new("RGB", (80, 16))
    for cell in range(4):
        ox = cell * 16
        x, y = ox + rng.randint(6, 9), 1
        while y < 14:
            nx = max(ox + 2, min(ox + 13, x + rng.choice((-4, -3, 3, 4))))
            ny = min(14, y + rng.randint(3, 4))
            line(img, x, y, nx, ny, 255, 70)
            if rng.random() < 0.5:
                bx = max(ox + 1, min(ox + 14, nx + rng.choice((-4, 4))))
                line(img, nx, ny, bx, min(15, ny + 3), 200, 50)
            x, y = nx, ny
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5) / 7.5
            if d < 1.0:
                c = int(255 * (1.0 - d) ** 1.6)
                img.putpixel((64 + x, y), (c, c, c))
    img.save(DIR + "zfighter_ki_aura_sparks.png")


smooth()
godly()
sparks()
print("wrote the three aura style textures in", DIR)
