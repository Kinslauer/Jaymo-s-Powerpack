"""Generates the wet-weather sprites (water puddles, underwater light shafts).
Run from the repository root: python -I tools/wet_textures.py"""
import math
import random
from PIL import Image

ROOT = "src/main/resources/assets/jaymospowers/textures/"


def puddle(seed, size=32):
    """A soft-edged blob, white with a brighter rim, so tinting gives a wet sheen."""
    rnd = random.Random(seed)
    lobes = [(rnd.uniform(0, math.tau), rnd.uniform(0.05, 0.16), rnd.randint(2, 5)) for _ in range(3)]
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    c = (size - 1) / 2.0
    for y in range(size):
        for x in range(size):
            dx, dy = (x - c) / c, (y - c) / c
            r = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            edge = 0.78 + sum(amp * math.sin(k * a + ph) for ph, amp, k in lobes)
            if r >= edge:
                continue
            t = r / edge
            alpha = 1.0 if t < 0.8 else (1.0 - (t - 0.8) / 0.2) ** 0.7
            shade = 200 + int(55 * (t ** 3))  # brighter towards the rim
            glint = 1.0 + 0.12 * math.sin(dx * 6 + dy * 3)
            v = max(0, min(255, int(shade * glint)))
            im.putpixel((x, y), (v, v, min(255, v + 4), int(255 * alpha)))
    return im


def shaft(w=16, h=64):
    """A vertical beam: bright at the top, fading down, soft at the sides."""
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for y in range(h):
        fall = (1.0 - y / (h - 1)) ** 1.4
        top = min(1.0, y / 4.0)
        for x in range(w):
            side = math.sin(math.pi * (x + 0.5) / w) ** 2
            im.putpixel((x, y), (255, 255, 255, int(255 * fall * top * side)))
    return im


def main():
    for i in range(4):
        puddle(100 + i).save(ROOT + f"particle/wet/puddle{i + 1}.png")
    shaft().save(ROOT + "particle/wet/light_shaft.png")


if __name__ == "__main__":
    main()
