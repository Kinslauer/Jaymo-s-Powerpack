#!/usr/bin/env python3
"""Qiviut art (Kingdoms, QiviutClothes): the raw qiviut tuft, the cap, jumper, trousers and socks icons, and their worn layers.

Qiviut is the muskox's underwool: brown-grey, soft and fine, and eight times warmer than sheep's wool. Everything is drawn
here. The clothes are our own wool clothes (tools/vanilla_style/wool_clothes.py's greyscale icons and worn layers, which are
already in the repository) repainted on a brown-grey qiviut ramp, with a scatter of fine lighter and darker fibres over them
so the knit reads as a halo of fuzz; the raw tuft is drawn from a mask below. Seeded, so reruns give the same art.

Run from the repository root: python3 tools/qiviut_assets.py
Writes kingdoms/src/main/resources/assets/jaymoskingdoms/textures/item/qiviut*.png and
textures/entity/equipment/{humanoid,humanoid_leggings}/qiviut.png.
"""
import os
import random

from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms", "textures")
# dark to light: the underwool's brown-grey, from its shadowed roots to its pale tips
RAMP = [(54, 42, 36), (84, 68, 58), (114, 94, 80), (142, 120, 102), (170, 148, 126), (198, 178, 154), (222, 206, 182)]
CLOTHES = ["cap", "shirt", "pants", "socks"]
WOOL_GREY_LOW, WOOL_GREY_HIGH = 70, 245

# the raw tuft: '#' body, 'h' lit tip, 'd' shadow, 'o' outline
TUFT = """
................
................
.....oo..oo.....
....ohhoohho....
...ohh#hh#hho...
..ohh#####hhho..
..oh###dd###ho..
.oh##d####d##ho.
.oh#####d#####o.
..o##d######do..
..o###d##d###o..
...o##########o.
....oo#dd#doo...
......oooooo....
................
................
"""


def ramp_at(t):
    t = max(0.0, min(1.0, t)) * (len(RAMP) - 1)
    i = min(len(RAMP) - 2, int(t))
    f = t - i
    return tuple(round(RAMP[i][k] * (1 - f) + RAMP[i + 1][k] * f) for k in range(3))


def repaint(src, seed, fuzz):
    """The wool image's greyscale pixels onto the qiviut ramp, with a scatter of finer fibres over opaque pixels."""
    rng = random.Random(seed)
    im = Image.open(src).convert("RGBA")
    px = im.load()
    w, h = im.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            grey = (r + g + b) / 3.0
            t = (grey - WOOL_GREY_LOW) / float(WOOL_GREY_HIGH - WOOL_GREY_LOW)
            if rng.random() < fuzz:
                t += rng.choice((-0.2, 0.2, 0.28))
            px[x, y] = ramp_at(t) + (a,)
    return im


def tuft():
    colours = {"o": RAMP[0], "d": RAMP[2], "#": RAMP[3], "h": RAMP[5]}
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rows = TUFT.strip("\n").split("\n")
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in colours:
                im.putpixel((x, y), colours[ch] + (255,))
    return im


def main():
    wool_item = os.path.join(OUT, "item", "wool_%s.png")
    for i, name in enumerate(CLOTHES):
        repaint(wool_item % name, "qiviut-" + name, 0.18).save(os.path.join(OUT, "item", "qiviut_%s.png" % name))
    tuft().save(os.path.join(OUT, "item", "qiviut.png"))
    for layer in ("humanoid", "humanoid_leggings"):
        folder = os.path.join(OUT, "entity", "equipment", layer)
        repaint(os.path.join(folder, "wool.png"), "qiviut-worn-" + layer, 0.22).save(os.path.join(folder, "qiviut.png"))


if __name__ == "__main__":
    main()
