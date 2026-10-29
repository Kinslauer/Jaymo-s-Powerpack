"""Draws the seven baubles (0.17.184, Andy asked for Baubles): 16x16 item icons in vanilla style (a one-pixel dark
outline, three tones lit from the top left), plus each item's model and item definition. Everything is our own art.
Run it from the root:

    python -I tools/gen_baubles.py [preview.png]

With a path it also writes an 8x preview sheet of all seven there.
"""
import json
import math
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "jaymospowers")
NS = "jaymospowers"

OUTLINE = (40, 28, 20, 255)
GOLD = ((255, 236, 130, 255), (226, 176, 52, 255), (152, 100, 26, 255))  # light, mid, dark
COPPER = ((246, 168, 120, 255), (196, 106, 66, 255), (122, 58, 38, 255))
IRON = ((236, 236, 236, 255), (178, 178, 184, 255), (108, 108, 118, 255))
RUBY = ((255, 150, 150, 255), (214, 40, 56, 255), (122, 16, 34, 255))
SKY = ((220, 252, 255, 255), (120, 214, 236, 255), (48, 128, 168, 255))
AMETHYST = ((236, 196, 255, 255), (168, 102, 222, 255), (96, 52, 150, 255))
TEAL = ((190, 255, 236, 255), (86, 198, 176, 255), (34, 116, 110, 255))
LEATHER = ((186, 124, 76, 255), (140, 86, 50, 255), (90, 52, 30, 255))
CLOTH = ((240, 240, 246, 255), (190, 194, 208, 255), (120, 124, 148, 255))
FUR = ((246, 230, 200, 255), (210, 180, 136, 255), (150, 118, 82, 255))
STRING = (220, 220, 220, 255)


def blank():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def put(img, x, y, color):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), color)


def outline(img, color=OUTLINE):
    """Every empty pixel touching a painted one (up, down, left, right) becomes the outline."""
    marks = []
    for y in range(img.height):
        for x in range(img.width):
            if img.getpixel((x, y))[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < img.width and 0 <= ny < img.height and img.getpixel((nx, ny))[3] != 0 \
                            and img.getpixel((nx, ny)) != OUTLINE:
                        marks.append((x, y))
                        break
    for x, y in marks:
        img.putpixel((x, y), color)


def lit(dx, dy, r, tones):
    """The tone for a point (dx, dy) from a shape's middle: light to the top left, dark to the bottom right."""
    light, mid, dark = tones
    s = (dx + dy) / max(r, 1)
    if s <= -0.7:
        return light
    if s >= 0.7:
        return dark
    return mid


def disc(img, cx, cy, r, tones):
    """A round gem or stud."""
    for y in range(int(cy - r) - 1, int(cy + r) + 2):
        for x in range(int(cx - r) - 1, int(cx + r) + 2):
            if (x - cx) ** 2 + (y - cy) ** 2 <= r * r + 0.3:
                put(img, x, y, lit(x - cx, y - cy, r, tones))
    put(img, int(round(cx - r / 2)), int(round(cy - r / 2)), tones[0])


def band(img, cx, cy, rx, ry, thick, tones):
    """An upright ring seen a little from above: an ellipse band, its far side dark, its near side lit."""
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy
            outer = (dx / rx) ** 2 + (dy / ry) ** 2
            inner = (dx / max(rx - thick, 0.5)) ** 2 + (dy / max(ry - thick, 0.5)) ** 2
            if outer <= 1.0 and inner > 1.0:
                if dy < 0:
                    tone = tones[0] if dx < 0 else tones[1]
                else:
                    tone = tones[1] if dx < 0 else tones[2]
                put(img, x, y, tone)


def heartstone_amulet():
    """A gold chain hanging in a V from the top corners to a red heart-shaped stone in a gold setting."""
    img = blank()
    for i in range(6):
        put(img, 3 + i, 1 + i, GOLD[1] if i % 2 else GOLD[0])
        put(img, 12 - i, 1 + i, GOLD[2] if i % 2 else GOLD[1])
    # the setting
    for x in range(5, 11):
        for y in range(7, 13):
            if abs(x - 7.5) + abs(y - 9.5) <= 4.2:
                put(img, x, y, GOLD[1])
    # the heart
    heart = [(6, 8), (7, 8), (9, 8), (10, 8), (5, 9), (6, 9), (7, 9), (8, 9), (9, 9), (10, 9), (6, 10), (7, 10), (8, 10),
             (9, 10), (7, 11), (8, 11), (8, 12)]
    heart = [(x - 0, y) for x, y in heart]
    for x, y in heart:
        put(img, x, y, lit(x - 8, y - 10, 2.5, RUBY))
    put(img, 6, 8, RUBY[0])
    put(img, 6, 9, RUBY[0])
    put(img, 7, 9, RUBY[0])
    outline(img)
    return img


def ring(metal, gem):
    """A metal ring with a round stone set on top."""
    img = blank()
    band(img, 7.5, 9.5, 5.5, 4.5, 1.6, metal)
    # the bezel and the stone
    for x in range(5, 11):
        put(img, x, 5, metal[2])
    disc(img, 7.5, 3.5, 2.2, gem)
    outline(img)
    return img


def swiftfoot_ring():
    return ring(GOLD, SKY)


def delvers_ring():
    return ring(COPPER, AMETHYST)


def brawlers_belt():
    """A leather belt lying in a loose diagonal with an iron buckle in the middle."""
    img = blank()
    for i in range(16):
        y = int(round(11 - i * 0.5))
        for t in range(3):
            put(img, i, y + t, LEATHER[0] if t == 0 else (LEATHER[1] if t == 1 else LEATHER[2]))
    # stitching
    for i in range(1, 16, 3):
        put(img, i, int(round(11 - i * 0.5)) + 1, LEATHER[0])
    # the buckle: a hollow iron square
    for x in range(5, 10):
        for y in range(5, 11):
            edge = x in (5, 9) or y in (5, 10)
            if edge:
                put(img, x, y, IRON[0] if (x == 5 or y == 5) else IRON[2])
            elif x == 7:
                put(img, x, y, IRON[1])
    outline(img)
    return img


def divers_circlet():
    """A thin gold circlet seen from the front and a little above, with a sea-green stone at the brow."""
    img = blank()
    band(img, 7.5, 8.5, 6.5, 3.5, 1.2, GOLD)
    disc(img, 7.5, 11.0, 1.8, TEAL)
    put(img, 7, 9, GOLD[0])
    put(img, 8, 9, GOLD[1])
    outline(img)
    return img


def featherfall_cloak():
    """A pale cloak hanging from a gold clasp, flaring at the hem, with a feather on the clasp."""
    img = blank()
    for y in range(3, 15):
        half = 2 + (y - 3) * 0.45
        for x in range(16):
            dx = x - 7.5
            if abs(dx) <= half:
                tone = CLOTH[1]
                if dx < -half + 1.2:
                    tone = CLOTH[0]
                elif dx > half - 1.2:
                    tone = CLOTH[2]
                # folds
                if y > 7 and int(round(dx)) in (-2, 2):
                    tone = CLOTH[2] if dx > 0 else CLOTH[0]
                put(img, x, y, tone)
    # the hood's dark inside at the top
    for x in range(6, 10):
        put(img, x, 3, CLOTH[2])
    # the clasp and feather
    disc(img, 7.5, 5.5, 1.2, GOLD)
    for i in range(4):
        put(img, 9 + i, 5 - i, (255, 255, 255, 255))
        put(img, 10 + i, 5 - i, CLOTH[1])
    outline(img)
    return img


def rabbit_foot_charm():
    """A rabbit's foot on a string loop, capped with gold."""
    img = blank()
    # the string loop
    for x, y in ((6, 1), (7, 0), (8, 0), (9, 1), (5, 2), (10, 2), (5, 3), (10, 3), (6, 4), (9, 4)):
        put(img, x, y, STRING)
    # the gold cap
    for x in range(6, 10):
        put(img, x, 5, GOLD[0] if x < 8 else GOLD[1])
        put(img, x, 6, GOLD[1] if x < 8 else GOLD[2])
    # the furry foot, curving down to the right
    for y in range(7, 15):
        cx = 7.5 + (y - 7) * 0.35
        half = 2.0 if y < 12 else 2.0 - (y - 11) * 0.5
        for x in range(16):
            dx = x - cx
            if abs(dx) <= half:
                put(img, x, y, lit(dx, (y - 10) * 0.4, half, FUR))
    # toes
    for x, y in ((9, 14), (10, 13)):
        put(img, x, y, FUR[2])
    outline(img)
    return img


PAINTERS = (
    ("heartstone_amulet", heartstone_amulet), ("swiftfoot_ring", swiftfoot_ring), ("delvers_ring", delvers_ring),
    ("brawlers_belt", brawlers_belt), ("divers_circlet", divers_circlet), ("featherfall_cloak", featherfall_cloak),
    ("rabbit_foot_charm", rabbit_foot_charm),
)


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        f.write(text)


def main():
    icons = []
    for name, painter in PAINTERS:
        img = painter()
        icons.append(img)
        png = os.path.join(ASSETS, "textures", "item", "bauble", name + ".png")
        os.makedirs(os.path.dirname(png), exist_ok=True)
        img.save(png)
        write(os.path.join(ASSETS, "models", "item", name + ".json"),
              json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/bauble/{name}"}}, indent=2) + "\n")
        write(os.path.join(ASSETS, "items", name + ".json"),
              json.dumps({"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}}, indent=2) + "\n")
        print(name, "written")
    if len(sys.argv) > 1:
        scale = 8
        sheet = Image.new("RGBA", (len(icons) * (16 * scale + 8) + 8, 16 * scale + 16), (198, 198, 198, 255))
        for i, img in enumerate(icons):
            sheet.alpha_composite(img.resize((16 * scale, 16 * scale), Image.NEAREST), (8 + i * (16 * scale + 8), 8))
        sheet.save(sys.argv[1])


if __name__ == "__main__":
    main()
