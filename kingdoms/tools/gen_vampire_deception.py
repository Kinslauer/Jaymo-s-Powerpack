"""Draws the art for the vampire deception patch (0.7.332), all original. Run from kingdoms/: python -I tools/gen_vampire_deception.py

garlic: a 16x16 head of garlic, vanilla style (no black outline, light from the top-left): a papery cream bulb with a
few pale violet streaks, a short dry stalk on top and a tuft of roots below.
bite_marks: a 64x64 player-skin-layout overlay with two small red punctures on the front of the neck (the top rows of the
torso, just under the chin), for SleepingPrey's marks layer.
"""
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "jaymoskingdoms", "textures")


def garlic():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    light, mid, shade, dark = (246, 240, 224, 255), (226, 216, 194, 255), (196, 184, 160, 255), (160, 146, 124, 255)
    streak = (186, 160, 190, 255)
    stalk, stalk_dark = (200, 184, 130, 255), (160, 142, 96, 255)
    root = (214, 200, 168, 255)
    # the bulb: a squat teardrop, widest at row 9
    rows = {5: (7, 8), 6: (6, 9), 7: (5, 10), 8: (4, 11), 9: (3, 12), 10: (3, 12), 11: (3, 12), 12: (4, 11), 13: (5, 10)}
    for y, (x0, x1) in rows.items():
        for x in range(x0, x1 + 1):
            t = (x - x0) / max(1, x1 - x0)
            c = light if t < 0.3 and y < 11 else mid if t < 0.65 else shade
            if y >= 12 or x == x1:
                c = shade if y < 13 else dark
            px[x, y] = c
    # clove lines
    for y in range(7, 13):
        px[6, y] = mid if y > 8 else px[6, y]
        px[9, y] = shade if y > 8 else px[9, y]
    for x, y in ((5, 10), (9, 9), (7, 11), (10, 11)):
        px[x, y] = streak
    # the stalk
    for y in (1, 2, 3, 4):
        px[7, y] = stalk
        px[8, y] = stalk_dark
    px[8, 1] = (0, 0, 0, 0)
    # the roots
    for x in (6, 7, 8, 9):
        px[x, 14] = root
    for x in (5, 8, 10):
        px[x, 15] = root
    img.save(os.path.join(ROOT, "item", "garlic.png"))


def bite_marks():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()
    # the torso's front is u 20-27, v 20-31 on the skin; its top row sits right under the chin
    for x, y in ((22, 20), (25, 20)):
        px[x, y] = (150, 12, 16, 255)
    for x, y in ((22, 21), (25, 21)):
        px[x, y] = (96, 8, 12, 200)
    os.makedirs(os.path.join(ROOT, "entity", "vampire"), exist_ok=True)
    img.save(os.path.join(ROOT, "entity", "vampire", "bite_marks.png"))


if __name__ == "__main__":
    garlic()
    bite_marks()
