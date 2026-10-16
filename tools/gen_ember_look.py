"""Emberborn player looks (Kingdoms 0.7.75): overlays on the 64x64 player skin layout, both drawn emissive (they glow in
the dark, added over the player's own skin).

marks.png - embers under the skin of the hands and forearms: a glowing hand, cracks of fire climbing toward the elbow,
            a few sparks on the upper arm (classic and slim arms both).
eyes.png  - the stock eye spots in ember gold (the fallback; each skin gets its own eyes in code, VampireLooks.glowingEyes).
Nothing is copied; colours are vanilla's fire ramp. Run from the project root:  python -I tools/gen_ember_look.py
"""
import os
import random
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "kingdoms", "src", "main", "resources", "assets",
                   "jaymoskingdoms", "textures", "entity", "ember")
os.makedirs(OUT, exist_ok=True)

# vanilla's fire ramp (campfire flames), hottest first; additive, so these are how much light each adds
GOLD = (233, 189, 57, 255)
ORANGE = (201, 108, 3, 255)
RED = (120, 34, 0, 255)
EYE = (255, 180, 60, 255)
rng = random.Random(2675)

# the arms' side faces: (x0, x1) across all four sides, from the arm's top row (y0, below the 4 rows of top/bottom faces)
# right arm at (40,16), left arm at (32,48); 4x12x4 (slim: 3 wide, its faces end a column sooner)
ARMS = ((40, 56, 20), (32, 48, 52))
# the hand's bottom face (classic; slim's is a column to the left, so both get it)
HANDS = ((47, 16, 52, 20), (39, 48, 44, 52))


def marks():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for x0, x1, y0 in ARMS:
        hand = y0 + 12
        for x in range(x0, x1):
            # the hand smoulders: the bottom row gold, the one above orange, then red
            img.putpixel((x, hand - 1), GOLD if rng.random() < 0.6 else ORANGE)
            img.putpixel((x, hand - 2), ORANGE if rng.random() < 0.6 else RED)
            if rng.random() < 0.6:
                img.putpixel((x, hand - 3), RED)
        # cracks of fire climbing toward the elbow, fading as they go
        for _ in range(9):
            x, y = rng.randrange(x0, x1), hand - 3
            length = rng.randrange(3, 8)
            for i in range(length):
                t = i / length
                img.putpixel((x, y), ORANGE if t < 0.4 else RED)
                y -= 1
                if rng.random() < 0.4:
                    x = min(x1 - 1, max(x0, x + rng.choice((-1, 1))))
        # a few sparks higher up
        for _ in range(3):
            img.putpixel((rng.randrange(x0, x1), rng.randrange(y0, y0 + 5)), RED)
    for x0, y0, x1, y1 in HANDS:
        for x in range(x0, x1):
            for y in range(y0, y1):
                img.putpixel((x, y), GOLD if rng.random() < 0.5 else ORANGE)
    img.save(os.path.join(OUT, "marks.png"))


def eyes():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for x in (9, 10, 13, 14):
        img.putpixel((x, 12), EYE)
    img.save(os.path.join(OUT, "eyes.png"))


marks()
eyes()
print("Emberborn looks written to", os.path.normpath(OUT))
