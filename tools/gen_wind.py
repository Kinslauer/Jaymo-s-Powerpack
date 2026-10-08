"""Generates Wind Control's art: the Gale Heart item (16x16).

All original pixel art, drawn procedurally: a pale storm-teal core wound with a spiral of wind, a dark rim, and a few
loose wisps trailing off it. Run from the project root:  python tools/gen_wind.py

Output: src/main/resources/assets/jaymospowers/textures/item/wind_gale_heart.png
"""
import math
from pathlib import Path

from PIL import Image

OUT = (Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "jaymospowers"
       / "textures" / "item" / "wind_gale_heart.png")

RIM = (28, 58, 72, 255)
DEEP = (54, 112, 128, 255)
CORE = (110, 186, 190, 255)
BRIGHT = (191, 244, 234, 255)
GLINT = (255, 255, 255, 255)
WISP = (191, 244, 234, 160)


def main():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    cx, cy = 7.5, 8.0
    radius = 5.6
    # the body: a slightly heart-like orb (a notch at the top), shaded from the lower right
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy
            # two lobes on top make the "heart"
            lobe = min(math.hypot(x - 5.5, y - 6.0), math.hypot(x - 9.5, y - 6.0))
            body = math.hypot(dx * 0.95, dy * 1.05)
            inside = body < radius - 0.6 or (lobe < 3.4 and y < 9) or (abs(dx) < 4.2 - (y - 8) * 0.8 and 8 <= y <= 13)
            if y <= 3 and 6.5 < x < 8.5:
                inside = False  # the notch between the lobes
            if not inside:
                continue
            shade = (dx * -0.5 + dy * -0.6) / radius
            px[x, y] = CORE if shade > -0.15 else DEEP
    # the rim: any body pixel next to empty space
    body_pixels = [(x, y) for y in range(16) for x in range(16) if px[x, y][3] > 0]
    for x, y in body_pixels:
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if not (0 <= nx < 16 and 0 <= ny < 16) or px[nx, ny][3] == 0:
                px[x, y] = RIM
                break
    # the spiral of wind wound through it
    for i in range(46):
        t = i / 45.0
        angle = t * math.pi * 3.4
        r = 0.6 + t * 3.8
        x = int(round(cx + math.cos(angle) * r))
        y = int(round(cy + 0.3 + math.sin(angle) * r * 0.9))
        if 0 <= x < 16 and 0 <= y < 16 and px[x, y][3] > 0 and px[x, y] != RIM:
            px[x, y] = BRIGHT
    # a glint, upper left
    for x, y in ((5, 6), (4, 7)):
        px[x, y] = GLINT
    # loose wisps trailing off to the right
    for x, y in ((14, 5), (15, 4), (14, 11), (15, 12), (1, 13)):
        if px[x, y][3] == 0:
            px[x, y] = WISP
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
