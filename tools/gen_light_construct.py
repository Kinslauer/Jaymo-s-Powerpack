"""Draws the texture of Lumen's block of light (assets/jaymospowers/textures/block/light_construct.png).

Run from the repo root: python -I tools/gen_light_construct.py
"""
from PIL import Image

SIZE = 16
img = Image.new("RGBA", (SIZE, SIZE))
px = img.load()
for y in range(SIZE):
    for x in range(SIZE):
        edge = min(x, y, SIZE - 1 - x, SIZE - 1 - y)
        # a pale blue glow, brightest at the heart, a bright rim and a faint criss-cross of lines
        d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5 / 10.6
        base = 1.0 - 0.45 * d
        r, g, b = int(154 + 90 * base * 0.6), int(220 + 30 * base * 0.5), int(244 + 10 * base)
        if edge == 0:
            r, g, b = 200, 238, 252
        elif (x + y) % 8 == 0:
            r, g, b = r - 10, g - 8, b
        px[x, y] = (min(r, 255), min(g, 255), min(b, 255), 255)
img.save("src/main/resources/assets/jaymospowers/textures/block/light_construct.png")
