"""Draws the Scouter (0.17.42, Andy's NOEA picks): a 16x16 item icon in vanilla style (a one-pixel dark outline, three tones
lit from the top left, a green lens), the 64x32 layer it is drawn with on the head, and the item model, item definition and
equipment asset that go with them. Everything is our own art. Run it from the root:

    python -I tools/gen_scouter.py

26.3 item models name their faces through texture variables (``layer0``), never raw sprite ids, or they render purple;
this writes the same shape as the other simple items (``parent: minecraft:item/generated`` + ``textures.layer0``).
"""
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "jaymospowers")
NS = "jaymospowers"

OUTLINE = (30, 30, 36, 255)
IRON = ((196, 198, 206, 255), (140, 143, 152, 255), (92, 95, 104, 255))  # light, mid, dark
LENS = ((176, 255, 206, 255), (62, 200, 124, 255), (22, 112, 72, 255))  # glint, mid, deep
RED = (196, 44, 44, 255)


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


def shaded_rect(img, x0, y0, x1, y1, tones):
    """A box lit from the top left: light on its top and left edge, dark on its bottom and right, mid between."""
    light, mid, dark = tones
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            color = mid
            if y == y0 or x == x0:
                color = light
            if y == y1 or x == x1:
                color = dark
            put(img, x, y, color)


def icon():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    # the headband: an arch over the top, two pixels thick, lit on its outer edge
    arch = [(2, 9), (2, 8), (2, 7), (3, 5), (4, 4), (5, 3), (6, 3), (7, 3), (8, 3), (9, 3), (10, 4)]
    for x, y in arch:
        put(img, x, y, IRON[1])
    for x, y in ((3, 6), (3, 7), (3, 8), (4, 5), (5, 4), (6, 4), (7, 4), (8, 4), (9, 4)):
        put(img, x, y, IRON[2])
    for x, y in ((2, 7), (3, 5), (4, 4), (5, 3), (6, 3), (7, 3)):
        put(img, x, y, IRON[0])
    # the ear cup at the left end of the band
    shaded_rect(img, 1, 9, 4, 13, IRON)
    put(img, 2, 11, RED)
    put(img, 3, 11, RED)
    # the boom out to the lens
    for x in range(5, 9):
        put(img, x, 11, IRON[1])
        put(img, x, 12, IRON[2])
    # the lens: a frame round a green pane with a glint
    shaded_rect(img, 9, 7, 14, 13, IRON)
    for y in range(8, 13):
        for x in range(10, 14):
            put(img, x, y, LENS[1])
    for x in range(10, 14):
        put(img, x, 12, LENS[2])
    put(img, 13, 11, LENS[2])
    put(img, 13, 10, LENS[2])
    put(img, 10, 8, LENS[0])
    put(img, 11, 8, LENS[0])
    put(img, 10, 9, LENS[0])
    put(img, 12, 9, LENS[0])
    outline(img, OUTLINE)
    return img


def worn():
    """The head layer (64x32, the vanilla armour layout): a band round the head, the ear cup on its left side and the
    lens over the left eye on the front."""
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    # the four sides of the head are 8 wide at x 0-8 (right), 8-16 (front), 16-24 (left), 24-32 (back); rows 8-15
    for x in range(0, 32):
        put(img, x, 10, IRON[0])
        put(img, x, 11, IRON[2])
    # the ear cup on the left side
    shaded_rect(img, 18, 11, 21, 15, IRON)
    put(img, 19, 13, RED)
    put(img, 20, 13, RED)
    # the boom along the left side to the front, and the lens over the left eye
    for x in range(16, 18):
        put(img, x, 12, IRON[1])
    shaded_rect(img, 12, 11, 15, 15, IRON)
    for y in range(12, 15):
        for x in range(13, 15):
            put(img, x, y, LENS[1])
    put(img, 13, 12, LENS[0])
    put(img, 14, 14, LENS[2])
    return img


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        f.write(text)


def main():
    item_png = os.path.join(ASSETS, "textures", "item", "scouter.png")
    os.makedirs(os.path.dirname(item_png), exist_ok=True)
    icon().save(item_png)
    worn_png = os.path.join(ASSETS, "textures", "entity", "equipment", "humanoid", "scouter.png")
    os.makedirs(os.path.dirname(worn_png), exist_ok=True)
    worn().save(worn_png)
    write(os.path.join(ASSETS, "models", "item", "scouter.json"),
          json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/scouter"}}, indent=2) + "\n")
    write(os.path.join(ASSETS, "items", "scouter.json"),
          json.dumps({"model": {"type": "minecraft:model", "model": f"{NS}:item/scouter"}}, indent=2) + "\n")
    write(os.path.join(ASSETS, "equipment", "scouter.json"),
          json.dumps({"layers": {"humanoid": [{"texture": f"{NS}:scouter"}]}}, indent=2) + "\n")
    print("scouter written")


if __name__ == "__main__":
    main()
