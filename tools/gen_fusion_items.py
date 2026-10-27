"""Draws the fusion items (0.17.51, Andy's NOEA picks): the Bonding Earring and the Fusion Arm Band, 16x16 item icons in
vanilla style (a one-pixel dark outline, three tones lit from the top left), plus the item model and item definition for
each. Everything is our own art. Run it from the root:

    python -I tools/gen_fusion_items.py

26.3 item models name their faces through texture variables (``layer0``), never raw sprite ids, or they render purple;
this writes the same shape as the other simple items (``parent: minecraft:item/generated`` + ``textures.layer0``).
"""
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "jaymospowers")
NS = "jaymospowers"

OUTLINE = (40, 28, 20, 255)
GOLD = ((255, 232, 120, 255), (222, 170, 48, 255), (150, 98, 24, 255))  # light, mid, dark
JADE = ((190, 255, 236, 255), (70, 214, 176, 255), (24, 120, 104, 255))
CLOTH = ((236, 236, 242, 255), (176, 180, 196, 255), (110, 114, 136, 255))
RED = ((255, 160, 130, 255), (214, 64, 52, 255), (130, 30, 34, 255))


def put(img, x, y, color):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), color)


def outline(img, color):
    """Every empty pixel touching a painted one (up, down, left, right) becomes the outline."""
    marks = []
    for y in range(img.height):
        for x in range(img.width):
            if img.getpixel((x, y))[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < img.width and 0 <= ny < img.height and img.getpixel((nx, ny))[3] != 0:
                        marks.append((x, y))
                        break
    for x, y in marks:
        img.putpixel((x, y), color)


def disc(img, cx, cy, r, tones):
    """A round stud lit from the top left."""
    light, mid, dark = tones
    for y in range(cy - r, cy + r + 1):
        for x in range(cx - r, cx + r + 1):
            d = (x - cx) ** 2 + (y - cy) ** 2
            if d <= r * r + r // 2:
                color = mid
                if x - cx + y - cy <= -r:
                    color = light
                elif x - cx + y - cy >= r:
                    color = dark
                put(img, x, y, color)


def earring():
    """A gold hook with a jade drop: the loop at the top, the hook curving down and a teardrop gem below it."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    # the loop: a ring of gold at the top
    for x, y in ((6, 2), (7, 1), (8, 1), (9, 2), (9, 3), (9, 4), (6, 3), (6, 4)):
        put(img, x, y, GOLD[1])
    for x, y in ((7, 1), (6, 2), (6, 3)):
        put(img, x, y, GOLD[0])
    for x, y in ((9, 3), (9, 4), (8, 5)):
        put(img, x, y, GOLD[2])
    # the hook down to the drop
    for x, y in ((7, 5), (7, 6), (8, 6)):
        put(img, x, y, GOLD[1])
    put(img, 7, 5, GOLD[0])
    put(img, 8, 6, GOLD[2])
    # the gem: a teardrop in jade
    disc(img, 8, 10, 3, JADE)
    put(img, 8, 7, JADE[1])
    put(img, 8, 8, JADE[1])
    put(img, 7, 9, JADE[0])
    put(img, 7, 8, JADE[0])
    put(img, 9, 12, JADE[2])
    put(img, 8, 13, JADE[2])
    outline(img, OUTLINE)
    return img


def band():
    """A cloth arm band wrapped round the arm with a gold clasp and a red stripe: seen from the side, slightly tilted."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(5, 12):
        shift = (y - 5) // 3
        for x in range(2 + shift, 14 + shift - 2):
            tone = CLOTH[1]
            if y == 5:
                tone = CLOTH[0]
            elif y == 11:
                tone = CLOTH[2]
            put(img, x, y, tone)
    for x in range(3, 14):
        put(img, x, 8, RED[1])
    for x in range(3, 14):
        put(img, x, 9, RED[2])
    # the gold clasp at the middle
    for y in range(6, 11):
        for x in range(7, 10):
            tone = GOLD[1]
            if x == 7 or y == 6:
                tone = GOLD[0]
            if x == 9 or y == 10:
                tone = GOLD[2]
            put(img, x, y, tone)
    put(img, 8, 8, JADE[1])
    put(img, 8, 7, JADE[0])
    outline(img, OUTLINE)
    return img


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        f.write(text)


def main():
    for name, painter in (("bonding_earring", earring), ("fusion_arm_band", band)):
        png = os.path.join(ASSETS, "textures", "item", name + ".png")
        os.makedirs(os.path.dirname(png), exist_ok=True)
        painter().save(png)
        write(os.path.join(ASSETS, "models", "item", name + ".json"),
              json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}}, indent=2) + "\n")
        write(os.path.join(ASSETS, "items", name + ".json"),
              json.dumps({"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}}, indent=2) + "\n")
        print(name, "written")


if __name__ == "__main__":
    main()
