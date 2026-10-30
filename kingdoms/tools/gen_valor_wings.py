"""Valor wings (Kingdoms 0.7.410): a Skyward Valor's wings (ValorWings), one 64x64 texture per Valor.Look, drawn by
ValorWingsLook over its own wing model (each wing a bone with a membrane trailing behind it, and a tip with its own):

  membrane       0,0   50x12 (the box's up and down faces; rows run from the bone back to the trailing edge)
  tip membrane   0,14  44x10
  bone           0,26  30x4
  tip bone       0,32  26x2

  wings_<look>.png   the wing in its look: feathers of iron plate, stone, scale, ash, heat haze or black lacquer, a ragged
                     feathered trailing edge
  wings_veins.png    the bones and each feather's shaft in white, drawn emissive in the soul colour under King's coating

Everything is our own: plain colour ramps and patterns. Run from the kingdoms folder:  python -I tools/gen_valor_wings.py
"""
import math
import os
import random
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "jaymoskingdoms",
                   "textures", "entity", "valor")
os.makedirs(OUT, exist_ok=True)

MEMBRANES = ((0, 0, 50, 12), (0, 14, 44, 10))
BONES = ((0, 26, 30, 4), (0, 32, 26, 2))
LOOKS = {
    "iron": ((0x3A, 0x3F, 0x47), (0x8A, 0x92, 0xA0), 255),
    "stone": ((0x5A, 0x54, 0x4C), (0x80, 0x78, 0x6C), 255),
    "scale": ((0x1F, 0x3A, 0x2E), (0x3E, 0x6E, 0x54), 255),
    "ash": ((0x6E, 0x6E, 0x6E), (0xAA, 0xA8, 0xA4), 230),
    "haze": ((0xC8, 0x6A, 0x20), (0xFF, 0xC8, 0x60), 160),
    "lacquer": ((0x0D, 0x0D, 0x12), (0x70, 0x70, 0x98), 255),
}
FEATHER = 3


def mix(a, b, k):
    return tuple(int(a[i] + (b[i] - a[i]) * k) for i in range(3))


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


def jitter(c, r, n):
    d = r.randint(-n, n)
    return tuple(max(0, min(255, v + d)) for v in c)


def ragged(c, row, h):
    """The trailing edge: each feather ends in a point, the middle one longest."""
    f = c % FEATHER
    cut = 2 if f == 0 else 1 if f == 2 else 0
    return row >= h - cut


def membrane_pixel(look, r, c, row, h, cracks):
    base, light, alpha = LOOKS[look]
    k = row / max(1, h - 1)
    f = c % FEATHER
    if look == "iron":
        rgb = mix(base, light, 0.15 + 0.3 * k)
        if f == 0:
            rgb = shade(rgb, 0.6)
        elif f == 1 and row % 4 == 1:
            rgb = light
        return rgb, alpha
    if look == "stone":
        if (c, row) in cracks:
            return (0x2A, 0x26, 0x22), alpha
        return jitter(mix(base, light, 0.3 * k), r, 9), alpha
    if look == "scale":
        off = 1 if (row // 2) % 2 else 0
        dx, dy = (c + off) % FEATHER, row % 2
        if dy == 0 and dx == 0:
            return (0x0B, 0x18, 0x12), alpha
        return (mix(base, light, 0.5) if dy == 1 and dx == 1 else jitter(base, r, 4)), alpha
    if look == "ash":
        if r.random() < 0.08:
            return (0x34, 0x30, 0x30), alpha
        rgb = jitter(mix(base, light, 0.4 * k), r, 10)
        return (shade(rgb, 0.8) if f == 0 else rgb), alpha - (40 if row >= h - 3 else 0)
    if look == "haze":
        wave = math.sin((row + c * 0.5) * 1.3)
        return mix(base, light, (wave + 1) / 2), int(alpha - 60 * k + 30 * wave)
    if look == "lacquer":
        d = (c + row) % 7
        if d == 0:
            return light, alpha
        if d in (1, 6):
            return (0x2C, 0x2C, 0x3C), alpha
        return (shade(base, 0.3) if f == 0 else base), alpha
    raise ValueError(look)


def wing(look, veins):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    r = random.Random(sum(map(ord, look)) if not veins else 1)
    for i, (x0, y0, w, h) in enumerate(MEMBRANES):
        cr = random.Random(11 + i)
        cracks = set()
        for _ in range(6):
            c, row = cr.randrange(w), 0
            while row < h:
                cracks.add((c % w, row))
                c += cr.choice((-1, 0, 1))
                row += 1
        for c in range(w):
            for row in range(h):
                if ragged(c, row, h):
                    continue
                if veins:
                    if c % FEATHER == 1 and row < h - 1:
                        img.putpixel((x0 + c, y0 + row), (255, 255, 255, 255))
                    continue
                rgb, a = membrane_pixel(look, r, c, row, h, cracks)
                img.putpixel((x0 + c, y0 + row), (*rgb, max(0, min(255, a))))
    for x0, y0, w, h in BONES:
        base, light, alpha = LOOKS[look]
        for c in range(w):
            for row in range(h):
                if veins:
                    img.putpixel((x0 + c, y0 + row), (255, 255, 255, 255))
                    continue
                rgb = shade(mix(base, light, 0.5), 0.75 if row == h - 1 else 1.0)
                img.putpixel((x0 + c, y0 + row), (*jitter(rgb, r, 5), max(alpha, 200)))
    return img


for look in LOOKS:
    wing(look, False).save(os.path.join(OUT, "wings_" + look + ".png"))
wing("iron", True).save(os.path.join(OUT, "wings_veins.png"))
print("wrote", len(LOOKS) + 1, "wing textures to", os.path.normpath(OUT))
