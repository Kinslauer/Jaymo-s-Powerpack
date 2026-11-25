"""The Stormwyrm and Venomscale heritage looks: the Rimewyrm's and Goldscale's textures, hue-shifted to storm violet and acid green.

Run from the repository root: python -I tools/gen_dragonblood_looks.py
"""
import colorsys
import os

from PIL import Image

DIR = "kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/heritage/variant"
# (new look, look it is made from, hue to move every colour to (0 to 1), saturation floor)
MAKES = [("stormwyrm", "rimewyrm", 0.72, 0.35), ("venomscale", "goldscale", 0.27, 0.45)]


def recolour(src, dst, hue, floor):
    im = Image.open(src).convert("RGBA")
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
            if s > 0.04 or v < 0.9:
                s = max(s, floor)
            r, g, b = colorsys.hsv_to_rgb(hue, s, v)
            px[x, y] = (round(r * 255), round(g * 255), round(b * 255), a)
    im.save(dst)


for new, old, hue, floor in MAKES:
    for part in ("skin", "glow", "eyes"):
        recolour(os.path.join(DIR, f"{old}_{part}.png"), os.path.join(DIR, f"{new}_{part}.png"), hue, floor)
print("done")
