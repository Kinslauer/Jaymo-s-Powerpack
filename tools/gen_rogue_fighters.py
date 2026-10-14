"""Draws the rogue fighters' 64x64 player skins (textures/entity/rogue_fighter/): tyrant.png (black and purple armour
over a dark undersuit, gold trim, pale skin, dark spiky-cut hair) and champion.png (a blue gi with an orange belt and
wristbands, tanned skin, short black hair). Standard player skin layout, wide arms, no outer layer."""
import random
import sys
from PIL import Image

rng = random.Random(3)


def cube(px, u, v, w, h, d, paint):
    """paint(face, x, y, w, h) for faces top/bottom/right/front/left/back."""
    faces = {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }
    for face, (x0, y0, fw, fh) in faces.items():
        for y in range(fh):
            for x in range(fw):
                c = paint(face, x, y, fw, fh)
                if c:
                    px[x0 + x, y0 + y] = c + (255,) if len(c) == 3 else c


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


def noisy(c):
    return shade(c, 0.92 + rng.random() * 0.14)


def skin(name, s):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()

    def head(face, x, y, w, h):
        if face == "top" or y < 2 or (face in ("left", "right", "back") and y < 5):
            return noisy(s["hair"])
        if face == "front":
            if y == 4 and x in (1, 2, 5, 6):
                return s["eye"] if x in (2, 5) else (240, 240, 240)
            if y == 3 and x in (1, 2, 5, 6):
                return shade(s["hair"], 0.8)
            if y == 6 and 3 <= x <= 4:
                return shade(s["skin"], 0.7)
        return noisy(s["skin"])

    def body(face, x, y, w, h):
        if face in ("front", "back") and y in (8, 9):
            return noisy(s["belt"])
        if face in ("left", "right") and y in (8, 9):
            return noisy(s["belt"])
        if face == "front" and s.get("trim") and (x in (0, w - 1) or y == 0):
            return s["trim"]
        if face == "front" and s.get("chest") and y < 7 and 1 <= x <= w - 2:
            return noisy(s["chest"])
        return noisy(s["suit"])

    def arm(face, x, y, w, h):
        if y < 4 and s.get("shoulder"):
            return noisy(s["shoulder"])
        if 8 <= y <= 9:
            return noisy(s["band"])
        if y >= 10:
            return noisy(s["skin"]) if not s.get("gloves") else noisy(s["gloves"])
        return noisy(s["sleeve"])

    def leg(face, x, y, w, h):
        if y >= 9:
            return noisy(s["boots"])
        return noisy(s["legs"])

    cube(px, 0, 0, 8, 8, 8, head)
    cube(px, 16, 16, 8, 12, 4, body)
    cube(px, 40, 16, 4, 12, 4, arm)
    cube(px, 32, 48, 4, 12, 4, arm)
    cube(px, 0, 16, 4, 12, 4, leg)
    cube(px, 16, 48, 4, 12, 4, leg)
    img.save(f"{sys.argv[1]}/{name}.png")


skin("tyrant", {
    "hair": (30, 24, 36), "skin": (222, 208, 200), "eye": (170, 30, 200),
    "suit": (34, 26, 44), "chest": (70, 40, 96), "trim": (212, 170, 60), "belt": (212, 170, 60),
    "shoulder": (70, 40, 96), "sleeve": (34, 26, 44), "band": (212, 170, 60), "gloves": (240, 236, 230),
    "legs": (34, 26, 44), "boots": (240, 236, 230),
})
skin("champion", {
    "hair": (24, 20, 18), "skin": (200, 150, 110), "eye": (40, 30, 24),
    "suit": (40, 80, 170), "belt": (240, 130, 30), "sleeve": (40, 80, 170), "band": (240, 130, 30),
    "legs": (40, 80, 170), "boots": (60, 40, 30),
})
