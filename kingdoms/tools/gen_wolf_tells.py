"""(0.7.332) The tells of a werewolf's hidden change (HiddenChange / WolfTells), as overlays on the 64x64 player skin layout:

  werewolf/hairy_hands.png   dark, coarse fur over the hands and forearms (and their sleeve layer), thicker toward the fingers
  werewolf/eyes.png          the stock eye spots in wolf yellow (drawn emissive; each skin gets its own, found like a vampire's)
  werewolf/torn_cloth.png    (the item) is gen_torn_cloth.py's

Everything is our own: plain colour ramps and a seeded scatter, nothing copied. Run from the kingdoms folder:
python -I tools/gen_wolf_tells.py
"""
import os
import random
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "jaymoskingdoms",
                   "textures", "entity", "werewolf")
os.makedirs(OUT, exist_ok=True)

FUR = [(30, 23, 19, 255), (46, 36, 29, 255), (62, 49, 38, 255), (84, 68, 52, 255), (104, 86, 66, 255)]
# the arms' four side faces, as (x0, x1, y_top, y_bottom): the arm and its sleeve layer, the right arm then the left
SIDES = ((40, 56, 20, 32), (40, 56, 36, 48), (32, 48, 52, 64), (48, 64, 52, 64))
# the hands' bottom faces, as (x0, x1, y0, y1) with the ends left out (classic; slim's is a column to the left, so both get it)
HANDS = ((47, 52, 16, 20), (47, 52, 32, 36), (39, 44, 48, 52), (55, 60, 48, 52))
FOREARM = 6  # rows from the hand up that carry fur


def fur(img, rng, x, y, density):
    if rng.random() < density:
        px = img.load()
        px[x, y] = FUR[min(len(FUR) - 1, int(rng.random() ** 1.5 * len(FUR)))]


def hairy_hands():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    rng = random.Random(323)
    for x0, x1, top, bottom in SIDES:
        for y in range(bottom - FOREARM, bottom):
            # thin at the elbow end of the forearm, thick over the hand
            density = 0.35 + 0.65 * (y - (bottom - FOREARM) + 1) / FOREARM
            for x in range(x0, x1):
                fur(img, rng, x, y, density)
    for x0, x1, y0, y1 in HANDS:
        for y in range(y0, y1):
            for x in range(x0, x1):
                fur(img, rng, x, y, 0.9)
    return img


def eyes():
    src = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "jaymoskingdoms",
                       "textures", "entity", "vampire", "eyes.png")
    img = Image.open(src).convert("RGBA")
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            if px[x, y][3] > 0:
                px[x, y] = (255, 212, 36, 255)
    return img


if __name__ == "__main__":
    hairy_hands().save(os.path.join(OUT, "hairy_hands.png"))
    eyes().save(os.path.join(OUT, "eyes.png"))
    print("wrote", OUT)
