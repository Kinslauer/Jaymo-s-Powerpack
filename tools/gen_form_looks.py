"""Generates the Z Fighter metal-rebuild and android plating layers (64x64 player skin layout), original art.

zfighter_metal_plating.png: brushed steel plates over the chest, back, shoulders, forearms, hips and shins, rivets at the corners.
zfighter_android_panels.png: dark panels with glowing cyan seams over the chest, forearms and shins (a lighter cover, so the skin shows).
Run from the repo root: python3 -I tools/gen_form_looks.py
"""
import random
from PIL import Image

OUT = "src/main/resources/assets/jaymospowers/textures/entity/"

# the skin layout's faces: (x0, y0, x1, y1) of each limb's four sides and caps, as in tools/gen_primal_fur.py
BODY = {"top": (20, 16, 28, 20), "front": (20, 20, 28, 30), "right": (16, 20, 20, 30), "left": (28, 20, 32, 30), "back": (32, 20, 40, 30)}
ARMS = (((40, 16), "right"), ((32, 48), "left"))
LEGS = (((0, 16), "right"), ((16, 48), "left"))


def plate(img, rect, base, light, dark, seam, rng, rivets=True, brush=True):
    x0, y0, x1, y1 = rect
    for x in range(x0, x1):
        for y in range(y0, y1):
            r = rng.random()
            c = base
            if brush:
                # horizontal brushing: rows lean light or dark in streaks
                streak = ((y * 5 + x // 4) % 7)
                c = light if streak == 0 and r > 0.4 else dark if streak == 3 and r > 0.5 else base
            edge = x in (x0, x1 - 1) or y in (y0, y1 - 1)
            if edge:
                c = seam
            img.putpixel((x, y), c + (255,))
    if rivets and x1 - x0 >= 3 and y1 - y0 >= 3:
        for x, y in ((x0 + 1, y0 + 1), (x1 - 2, y0 + 1), (x0 + 1, y1 - 2), (x1 - 2, y1 - 2)):
            img.putpixel((x, y), light + (255,))


def limb_faces(origin):
    """The four side faces of an arm or leg (4 wide, 12 tall each) and their cap."""
    ox, oy = origin
    return [(ox + i * 4, oy + 4, ox + i * 4 + 4, oy + 16) for i in range(4)], (ox + 4, oy, ox + 8, oy + 4)


def metal():
    rng = random.Random(11)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    base, light, dark, seam = (0x98, 0xA2, 0xAC), (0xD2, 0xDA, 0xE2), (0x66, 0x6E, 0x78), (0x3A, 0x40, 0x48)
    for name, rect in BODY.items():
        plate(img, rect, base, light, dark, seam, rng)
    for origin, _ in ARMS + LEGS:
        sides, cap = limb_faces(origin)
        for x0, y0, x1, y1 in sides:
            # shoulder/hip cap plate and the lower half (forearm/shin guard); the middle stays bare for the joint
            plate(img, (x0, y0, x1, y0 + 3), base, light, dark, seam, rng, rivets=False)
            plate(img, (x0, y0 + 6, x1, y1), base, light, dark, seam, rng)
        plate(img, cap, base, light, dark, seam, rng, rivets=False)
    img.save(OUT + "zfighter_metal_plating.png")
    print("wrote", OUT + "zfighter_metal_plating.png")


def android():
    rng = random.Random(23)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    base, light, dark, seam = (0x1E, 0x2C, 0x38), (0x30, 0x46, 0x56), (0x14, 0x1E, 0x28), (0x40, 0xE0, 0xF0)
    # chest and back panels, glowing seam round them and down the middle
    for name in ("front", "back"):
        x0, y0, x1, y1 = BODY[name]
        plate(img, (x0, y0, x1, y0 + 6), base, light, dark, seam, rng, rivets=False)
        mid = (x0 + x1) // 2
        for y in range(y0, y1):
            img.putpixel((mid, y), seam + (255,))
    for origin, _ in ARMS + LEGS:
        sides, cap = limb_faces(origin)
        for x0, y0, x1, y1 in sides:
            plate(img, (x0, y0 + 7, x1, y1), base, light, dark, seam, rng, rivets=False)
            img.putpixel((x0 + 1, y0 + 9), (0xB8, 0xFF, 0xF8, 255))
    img.save(OUT + "zfighter_android_panels.png")
    print("wrote", OUT + "zfighter_android_panels.png")


if __name__ == "__main__":
    metal()
    android()
