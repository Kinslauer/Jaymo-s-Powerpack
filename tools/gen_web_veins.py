"""Generates the veins coursing over Living Armor's bulk: four frames (web_symbiote_veins_0..3.png, 64x64, the player
skin layout) of branching veins, a glossy grey against the black ((0.16.7) Andy: "blackish grey like before", not purple). Each frame lights a different stretch of each
vein, so cycling them reads as something pulsing along them and writhing. Run from the project root."""
from PIL import Image
import math, random

OUT = "src/main/resources/assets/jaymospowers/textures/entity/web_symbiote_veins_{}.png"
FRAMES = 4
# the skin's regions (x, y, w, h) on the base layer: head, body, arms, legs (front/back/sides all in each block)
REGIONS = [(0, 8, 32, 8), (16, 20, 24, 12), (40, 20, 16, 12), (32, 52, 16, 12), (0, 20, 16, 12), (16, 52, 16, 12)]
DARK = (38, 38, 44)
LIT = (118, 120, 128)
CORE = (212, 214, 222)

random.seed(26)
veins = []  # each: list of (x, y) along it
for (rx, ry, rw, rh) in REGIONS:
    for _ in range(max(2, rw * rh // 70)):
        x, y = rx + random.uniform(0, rw), ry + random.uniform(0, rh)
        angle = random.uniform(0, math.tau)
        path = []
        for step in range(random.randint(10, 22)):
            path.append((int(x), int(y)))
            angle += random.uniform(-0.6, 0.6)
            x += math.cos(angle)
            y += math.sin(angle)
            if not (rx <= x < rx + rw and ry <= y < ry + rh):
                break
            if random.random() < 0.12:
                # a branch
                bx, by, ba = x, y, angle + random.choice((-1, 1)) * random.uniform(0.6, 1.2)
                branch = []
                for _ in range(random.randint(3, 8)):
                    branch.append((int(bx), int(by)))
                    bx += math.cos(ba)
                    by += math.sin(ba)
                    if not (rx <= bx < rx + rw and ry <= by < ry + rh):
                        break
                veins.append(branch)
        veins.append(path)

for frame in range(FRAMES):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    phase = frame / FRAMES * math.tau
    for v, path in enumerate(veins):
        offset = v * 1.7
        for i, (x, y) in enumerate(path):
            if not (0 <= x < 64 and 0 <= y < 64):
                continue
            pulse = math.sin(i * 0.55 - phase + offset)
            colour = CORE if pulse > 0.85 else LIT if pulse > 0.1 else DARK
            img.putpixel((x, y), (*colour, 255))
    img.save(OUT.format(frame))
print(len(veins), "veins")
