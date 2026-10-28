"""(0.17.46, Andy's NOEA picks) Generates the Sparking aura's little electric bolts and the motes (one strip of five
16 px cells: four bolts and a soft dot). All additive (dark is clear, light glows). (0.17.85) The Smooth and Godly
shell textures went with the shells; the wisps are tools/gen_ki_wisp.py. Original art, painterly pixel:
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


sparks()
print("wrote the aura sparks texture in", DIR)
