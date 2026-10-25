"""Generates the Z Fighter charging aura's flame texture: tongues of light licking upward with dark gaps between them
(additive, so dark is clear and light glows), tiling both ways so it can scroll up the aura and wrap round it.
Original art; the look of a Dragon Ball style aura is only the idea. Run: python3 tools/gen_ki_aura.py"""
import math
import random
from PIL import Image

OUT = "src/main/resources/assets/jaymospowers/textures/entity/zfighter_ki_aura.png"
W, H = 64, 128
rng = random.Random(23)
# each tongue: centre (0-1 across), width, how far up it reaches before it thins out, its sway, and its brightness
tongues = [(rng.random(), rng.uniform(0.04, 0.08), rng.uniform(0.45, 0.9), rng.uniform(0.02, 0.06), rng.randint(1, 3),
            rng.uniform(0.0, 1.0), rng.uniform(0.5, 1.0)) for _ in range(22)]


def wrap(d):
    return d - round(d)


img = Image.new("RGB", (W, H))
for y in range(H):
    for x in range(W):
        u, v = x / W, y / H
        light = 0.0
        for cx, width, reach, sway, waves, start, bright in tongues:
            # how far up this tongue we are (0 at its root, 1 at its tip), wrapping round so the sheet tiles
            up = ((start - v) % 1.0) / reach
            if up > 1.0:
                continue
            centre = cx + sway * math.sin(2 * math.pi * (waves * v + start))
            # swells in from its root, thins to a point at its tip
            body = min(1.0, up / 0.18) ** 0.5 * (1.0 - up) ** 0.7
            w = width * body + 0.004
            d = abs(wrap(u - centre)) / w
            if d < 1.0:
                # brightest along the edge of the tongue, a softer core: light reads as a sheet of flame
                edge = 0.55 + 0.45 * d ** 2
                light += bright * edge * (1.0 - d ** 2) * body ** 0.6
        light = min(1.0, 0.04 + light + rng.uniform(-0.03, 0.03))
        c = max(0, min(255, int(255 * light)))
        img.putpixel((x, y), (c, c, c))
img.save(OUT)
print("wrote", OUT)
