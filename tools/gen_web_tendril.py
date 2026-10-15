"""Generates the symbiote's tendril and spike texture (web_symbiote_tendril.png, 32x32): matte black faceted shafts
with a dark grey bevel along the top face's edge and a few grey glints, no purple, matching the Spike Burst's spikes.
Drawn from code, nothing copied. Run from the project root:  python tools/gen_web_tendril.py
"""
import random

from PIL import Image

OUT = "src/main/resources/assets/jaymospowers/textures/entity/web_symbiote_tendril.png"
BASE = (18, 18, 22)
DEEP = (9, 9, 12)
BEVEL = (52, 55, 64)
EDGE = (34, 36, 43)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


rnd = random.Random(4242)
img = Image.new("RGBA", (32, 32))
for y in range(32):
    for x in range(32):
        c = lerp(DEEP, BASE, 0.55 + rnd.random() * 0.45)
        # the boxes are cut at texOffs (0, 0) and (0, 8): their top faces sit in rows 0-4 and 8-12 (columns 3-9),
        # and the sides' top edges just under them
        band = y % 8
        if band <= 4 and 3 <= x <= 9:
            c = lerp(c, BEVEL, 0.55 if x in (3, 4) else 0.3)
        elif band == 5:
            c = lerp(c, EDGE, 0.8)
        if rnd.random() < 0.02:
            c = lerp(c, BEVEL, 0.6)
        img.putpixel((x, y), c + (255,))
img.save(OUT)
print("tendril texture written")
