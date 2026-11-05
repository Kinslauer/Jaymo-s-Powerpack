"""Valor wing sleeves (Kingdoms 0.7.480): a Skyward Valor's wings at rest, wrapped round the arms like a coating (Andy:
"they should wrap around the arms when not in use so they look like some kind of coating, but they unfurl to reveal").
ValorWingsLook draws them as a sleeve over each arm, a box over the arm's own (classic 4x12x4 at 0,0; slim 3x12x4 at
32,0), and draws them up off the arms into the wings on the back when they open:

  wings_sleeve_<look>.png   the wing wound round the arm: its feathers in the look's own pattern (gen_valor_wings.py),
                            laid in overlapping bands spiralling down the arm, the bone a ridge down the outer side
  wings_sleeve_veins.png    the bone and each feather's shaft in white, emissive in the soul colour under King's coating

Run from the kingdoms folder:  python -I tools/gen_valor_wing_sleeves.py
"""
import os
import random
import sys
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_valor_wings as wings  # noqa: E402

# the two sleeve boxes' sheets: x, y, arm width (box UV: d + w + d + w wide, d + 12 tall, depth 4)
BOXES = ((0, 0, 4), (32, 0, 3))
DEPTH, LENGTH = 4, 12
BAND = 4  # rows a turn of the wrapped wing covers


def sleeve(look, veins):
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    r = random.Random(sum(map(ord, look)) + 7 if not veins else 3)
    for x0, y0, w in BOXES:
        width, height = 2 * (DEPTH + w), DEPTH + LENGTH
        for x in range(width):
            for y in range(height):
                if y < DEPTH and not (DEPTH <= x < DEPTH + 2 * w):
                    continue  # outside the top and bottom faces
                # round the arm: the band spirals down, each turn's feathers overlapping the last
                row = (y + x * BAND / width) % BAND
                col = x + (int((y + x * BAND / width) // BAND) * 2)
                bone = y >= DEPTH and x in (1, 2)
                if veins:
                    if bone or (col % wings.FEATHER == 1 and row < BAND - 1):
                        img.putpixel((x0 + x, y0 + y), (255, 255, 255, 255))
                    continue
                base, light, alpha = wings.LOOKS[look]
                if bone:
                    rgb = wings.shade(wings.mix(base, light, 0.5), 0.75 if x == 2 else 1.0)
                    img.putpixel((x0 + x, y0 + y), (*wings.jitter(rgb, r, 5), max(alpha, 200)))
                    continue
                rgb, a = wings.membrane_pixel(look, r, col, int(row * 3), BAND * 3, set())
                if row >= BAND - 1:
                    rgb = wings.shade(rgb, 0.7)  # the next turn's edge lying over this one
                img.putpixel((x0 + x, y0 + y), (*rgb, max(0, min(255, a))))
    return img


if __name__ == "__main__":
    for look in wings.LOOKS:
        # wispy like the wings, a little less so: they lie close on the arm
        img = wings.wisp(sleeve(look, False), len(look))
        a = img.getchannel("A").point(lambda v: min(255, int(v * 1.6)))
        img.putalpha(a)
        img.save(os.path.join(wings.OUT, "wings_sleeve_" + look + ".png"))
    sleeve("iron", True).save(os.path.join(wings.OUT, "wings_sleeve_veins.png"))
    print("wrote", len(wings.LOOKS) + 1, "sleeve textures to", os.path.normpath(wings.OUT))
