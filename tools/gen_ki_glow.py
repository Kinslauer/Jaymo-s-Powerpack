"""Generates the Z Fighter ki glow's swirl texture: soft grey wisps that scroll over the glow shells (additive, so dark
is clear and light glows). Run: python3 tools/gen_ki_glow.py"""
import math
import random
from PIL import Image

OUT = "src/main/resources/assets/jaymospowers/textures/entity/zfighter_ki_glow.png"
N = 64
rng = random.Random(7)
# a few soft vertical flames: brightness rises in streaks that taper, wrapping round so it tiles
waves = [(rng.uniform(0, 2 * math.pi), rng.uniform(1, 4), rng.uniform(0.2, 0.6)) for _ in range(6)]
img = Image.new("RGB", (N, N))
for y in range(N):
    for x in range(N):
        v = 0.0
        for phase, freq, amp in waves:
            v += amp * math.sin(2 * math.pi * freq * x / N + phase + 2 * math.pi * y / N * (freq % 2 + 1))
        v = 0.55 + 0.25 * v / 2.0 + rng.uniform(-0.08, 0.08)
        c = max(0, min(255, int(255 * v)))
        img.putpixel((x, y), (c, c, c))
img.save(OUT)
print("wrote", OUT)
