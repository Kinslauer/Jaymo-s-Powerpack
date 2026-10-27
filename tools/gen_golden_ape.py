"""Recolours the Great Ape's skin gold (textures/entity/zfighter_great_ape_golden.png, 128x128) for the golden Great Ape.

Reads the brown ape painted by gen_great_ape.py and keeps its layout, shading and red eyes: fur becomes a gold ramp by
brightness, bare skin a pale warm gold. Run from the repo root: python -I tools/gen_golden_ape.py
"""
import os
from PIL import Image

ROOT = os.path.join("src", "main", "resources", "assets", "jaymospowers", "textures", "entity")
SRC = os.path.join(ROOT, "zfighter_great_ape.png")
DST = os.path.join(ROOT, "zfighter_great_ape_golden.png")

FUR_DARK = (158, 104, 18)
FUR_BRIGHT = (255, 214, 74)
SKIN_DARK = (214, 170, 96)
SKIN_BRIGHT = (255, 236, 184)
FUR_LUM_LOW, FUR_LUM_HIGH = 40.0, 100.0
SKIN_LUM_LOW, SKIN_LUM_HIGH = 110.0, 175.0
FUR_LIMIT = 108.0


def lerp(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


img = Image.open(SRC).convert("RGBA")
px = img.load()
for y in range(img.height):
    for x in range(img.width):
        r, g, b, a = px[x, y]
        if a == 0:
            continue
        lum = 0.3 * r + 0.59 * g + 0.11 * b
        if r > 130 and g < 80 and b < 80:
            continue  # the red eyes stay red
        if lum < FUR_LIMIT:
            c = lerp(FUR_DARK, FUR_BRIGHT, (lum - FUR_LUM_LOW) / (FUR_LUM_HIGH - FUR_LUM_LOW))
        else:
            c = lerp(SKIN_DARK, SKIN_BRIGHT, (lum - SKIN_LUM_LOW) / (SKIN_LUM_HIGH - SKIN_LUM_LOW))
        px[x, y] = c + (a,)
img.save(DST)
print("wrote", DST)
