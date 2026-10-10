"""Draws the kingdom folk's skins (64x64, the humanoid layout): the guard (a soldier's face, cropped hair, a padded
gambeson under his armour) and the king (a greying beard, a gold crown set with rubies, a crimson robe trimmed with
ermine). Run from the project root."""
from PIL import Image
import random

OUT = "src/main/resources/assets/jaymoskingdoms/textures/entity/{}.png"

# (x, y, w, h) of each box's faces: top, bottom, right, front, left, back
def faces(ox, oy, w, h, d):
    return {"top": (ox + d, oy, w, d), "bottom": (ox + d + w, oy, w, d), "right": (ox, oy + d, d, h), "front": (ox + d, oy + d, w, h),
            "left": (ox + d + w, oy + d, d, h), "back": (ox + d + w + d, oy + d, w, h)}

HEAD = faces(0, 0, 8, 8, 8)
BODY = faces(16, 16, 8, 12, 4)
ARMS = [faces(40, 16, 4, 12, 4), faces(32, 48, 4, 12, 4)]
LEGS = [faces(0, 16, 4, 12, 4), faces(16, 48, 4, 12, 4)]


def noisy(img, rect, base, spread=10, seed=0):
    rnd = random.Random(seed)
    x0, y0, w, h = rect
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            n = rnd.randint(-spread, spread)
            img.putpixel((x, y), tuple(max(0, min(255, c + n)) for c in base) + (255,))


def fill_box(img, box, base, seed):
    for i, rect in enumerate(box.values()):
        noisy(img, rect, base, seed=seed + i)


def face(img, skin, hair, eyes, beard=None):
    fill_box(img, HEAD, skin, 1)
    x0, y0, w, h = HEAD["front"]
    # hair on top and the upper rows all round
    for name in ("top", "right", "left", "back"):
        rx, ry, rw, rh = HEAD[name]
        noisy(img, (rx, ry, rw, rh if name == "top" else 3 if name != "back" else 7), hair, seed=hash(name) % 50)
    noisy(img, (x0, y0, w, 2), hair, seed=7)
    # eyes, brows, nose, mouth
    for ex in (x0 + 1, x0 + 5):
        img.putpixel((ex, y0 + 4), (255, 255, 255, 255))
        img.putpixel((ex + 1, y0 + 4), eyes + (255,))
        img.putpixel((ex, y0 + 3), tuple(c - 30 for c in hair) + (255,))
        img.putpixel((ex + 1, y0 + 3), tuple(c - 30 for c in hair) + (255,))
    img.putpixel((x0 + 3, y0 + 5), tuple(c - 25 for c in skin) + (255,))
    img.putpixel((x0 + 4, y0 + 5), tuple(c - 25 for c in skin) + (255,))
    for mx in range(x0 + 2, x0 + 6):
        img.putpixel((mx, y0 + 6), (120, 60, 50, 255))
    if beard:
        noisy(img, (x0, y0 + 6, w, 2), beard, seed=9)
        img.putpixel((x0 + 3, y0 + 6), (110, 55, 50, 255))
        img.putpixel((x0 + 4, y0 + 6), (110, 55, 50, 255))
        for name in ("right", "left"):
            rx, ry, rw, rh = HEAD[name]
            noisy(img, (rx, ry + 5, rw, 3), beard, seed=11)


def guard():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    face(img, (196, 150, 120), (70, 50, 35), (60, 90, 140))
    gambeson = (150, 128, 90)
    fill_box(img, BODY, gambeson, 20)
    for arm in ARMS:
        fill_box(img, arm, gambeson, 30)
        noisy(img, (arm["front"][0], arm["front"][1] + 9, 4, 3), (196, 150, 120), seed=31)
    for leg in LEGS:
        fill_box(img, leg, (80, 70, 60), 40)
        noisy(img, (leg["front"][0], leg["front"][1] + 9, 4, 3), (50, 40, 30), seed=41)
    # quilted seams on the gambeson
    x0, y0, w, h = BODY["front"]
    for y in range(y0, y0 + h, 3):
        for x in range(x0, x0 + w):
            img.putpixel((x, y), tuple(c - 25 for c in gambeson) + (255,))
    img.save(OUT.format("guard"))


def king():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    face(img, (205, 160, 130), (150, 145, 140), (70, 60, 40), beard=(175, 170, 165))
    # the crown: a gold band round the head with points and rubies
    gold, ruby = (235, 190, 60), (190, 20, 40)
    for name in ("front", "right", "left", "back"):
        rx, ry, rw, rh = HEAD[name]
        noisy(img, (rx, ry, rw, 2), gold, spread=12, seed=60)
        for x in range(rx, rx + rw, 2):
            img.putpixel((x, ry), (0, 0, 0, 0) if name != "front" else gold + (255,))
        img.putpixel((rx + rw // 2, ry + 1), ruby + (255,))
    rx, ry, rw, rh = HEAD["top"]
    for x in range(rx, rx + rw):
        img.putpixel((x, ry), gold + (255,))
        img.putpixel((x, ry + rh - 1), gold + (255,))
    for y in range(ry, ry + rh):
        img.putpixel((rx, y), gold + (255,))
        img.putpixel((rx + rw - 1, y), gold + (255,))
    # the robe: crimson, trimmed with ermine
    robe, ermine = (140, 20, 35), (240, 238, 230)
    fill_box(img, BODY, robe, 70)
    x0, y0, w, h = BODY["front"]
    noisy(img, (x0 + 3, y0, 2, h), ermine, spread=6, seed=71)
    for y in range(y0 + 1, y0 + h, 3):
        img.putpixel((x0 + 3, y), (20, 20, 20, 255))
    for arm in ARMS:
        fill_box(img, arm, robe, 80)
        noisy(img, (arm["front"][0], arm["front"][1] + 8, 4, 1), ermine, seed=81)
        noisy(img, (arm["front"][0], arm["front"][1] + 9, 4, 3), (205, 160, 130), seed=82)
    for leg in LEGS:
        fill_box(img, leg, (90, 15, 25), 90)
    img.save(OUT.format("king"))


guard()
king()
print("folk drawn")
