"""Our own undead civilian skins for the future's plain zombies (Chrono's Los Perrito future).

Each is a 64x64 zombie texture (the game's humanoid layout: head, hat, body, right arm, right leg; the left limbs
mirror the right). An outfit is drawn row by row down each part, then rotted: grey-green skin, sunken eyes, torn
cloth showing flesh, blood and grime. Seeded, so reruns give the same skins.

    python3 tools/gen_future_zombies.py
"""
import os
import random
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "jaymospowers", "textures",
                   "entity", "future")
COUNT = 16

SKINS = [(122, 140, 110), (138, 146, 128), (112, 128, 118), (150, 150, 136), (104, 118, 96), (128, 120, 112),
         (96, 104, 92), (140, 132, 118)]
HAIR = [(40, 32, 26), (70, 52, 34), (24, 22, 20), (120, 96, 60), (90, 86, 80), (140, 120, 80), (60, 30, 22), None]
FLESH = (110, 30, 28)
BLOOD = (78, 14, 14)

# outfits: top, bottoms, shoes, extras
OUTFITS = [
    dict(top=(60, 80, 140), sleeves="short", legs=(48, 64, 104), legtype="long", shoes=(40, 36, 34), extra=None),       # tee + jeans
    dict(top=(40, 42, 50), sleeves="long", legs=(36, 38, 44), legtype="long", shoes=(20, 18, 18), extra="tie"),         # suit
    dict(top=(110, 110, 112), sleeves="long", legs=(50, 62, 96), legtype="long", shoes=(220, 220, 220), extra="hood"),  # hoodie
    dict(top=(40, 56, 96), sleeves="short", legs=(36, 48, 84), legtype="long", shoes=(20, 20, 24), extra="police"),     # police
    dict(top=(110, 170, 160), sleeves="short", legs=(110, 170, 160), legtype="long", shoes=(220, 220, 220), extra="scrubs"),
    dict(top=(220, 200, 40), sleeves="short", legs=(70, 68, 60), legtype="long", shoes=(90, 60, 30), extra="hivis"),    # builder
    dict(top=(200, 200, 196), sleeves="none", legs=(150, 130, 90), legtype="shorts", shoes=(200, 60, 50), extra=None),   # tank + shorts
    dict(top=(150, 50, 70), sleeves="short", legs=(150, 50, 70), legtype="skirt", shoes=(30, 24, 24), extra=None),      # dress
    dict(top=(230, 230, 226), sleeves="long", legs=(30, 30, 34), legtype="long", shoes=(20, 20, 20), extra="apron"),     # waiter
    dict(top=(120, 40, 40), sleeves="long", legs=(60, 70, 110), legtype="long", shoes=(80, 56, 36), extra="flannel"),   # flannel
    dict(top=(70, 110, 60), sleeves="short", legs=(160, 140, 100), legtype="long", shoes=(60, 44, 30), extra=None),     # khakis
    dict(top=(180, 150, 200), sleeves="long", legs=(40, 40, 46), legtype="long", shoes=(30, 30, 34), extra=None),       # sweater
    dict(top=(240, 120, 30), sleeves="short", legs=(240, 120, 30), legtype="long", shoes=(30, 30, 30), extra="jumpsuit"),
    dict(top=(30, 30, 32), sleeves="short", legs=(46, 46, 50), legtype="long", shoes=(30, 30, 30), extra="band"),       # black tee
    dict(top=(200, 60, 50), sleeves="none", legs=(40, 40, 120), legtype="shorts", shoes=(240, 240, 240), extra="jersey"),
    dict(top=(90, 70, 50), sleeves="long", legs=(56, 48, 40), legtype="long", shoes=(40, 30, 22), extra="coat"),       # coat
]


def faces(u, v, w, h, d):
    """The six faces of a box at (u, v): name -> (x, y, width, height)."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}


def shade(c, k):
    return tuple(max(0, min(255, int(round(x * k)))) for x in c)


def mix(a, b, t):
    return tuple(int(round(a[i] * (1 - t) + b[i] * t)) for i in range(3))


class Painter:
    def __init__(self, seed):
        self.r = random.Random(seed)
        self.img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))

    def put(self, x, y, c, a=255):
        self.img.putpixel((x, y), (c[0], c[1], c[2], a))

    def box(self, u, v, w, h, d, colour):
        """colour(face, x, y) -> rgb, where y runs 0..h-1 down the sides (top/bottom get y = -1 / h)."""
        for name, (fx, fy, fw, fh) in faces(u, v, w, h, d).items():
            for i in range(fw):
                for j in range(fh):
                    y = -1 if name == "top" else h if name == "bottom" else j
                    c = colour(name, i, y, fw)
                    if c is not None:
                        self.put(fx + i, fy + j, c)


def skin_noise(r, c):
    return shade(c, 0.88 + r.random() * 0.2)


def make(index):
    r = random.Random(0xF00D + index * 7919)
    p = Painter(index)
    skin = SKINS[r.randrange(len(SKINS))]
    hair = HAIR[r.randrange(len(HAIR))]
    o = OUTFITS[index % len(OUTFITS)]
    hair_rows = r.choice([1, 2, 2, 3])
    long_hair = hair is not None and r.random() < 0.3

    def head(face, x, y, fw):
        c = skin_noise(r, skin)
        if hair is not None:
            if face == "top" or (face != "bottom" and y < hair_rows) or (long_hair and face in ("back", "left", "right") and y < 7):
                return shade(hair, 0.85 + r.random() * 0.25)
        if face == "front":
            if y == 3 and x in (1, 2, 5, 6):
                return (26, 22, 22)                       # sunken sockets
            if y == 4 and x in (1, 6):
                return (40, 30, 30)
            if y == 4 and x in (2, 5):
                return (210, 200, 150) if r.random() < 0.6 else (20, 18, 18)  # clouded eyes
            if y == 5 and x in (3, 4):
                return shade(skin, 0.7)                   # nose shadow
            if y == 6 and 2 <= x <= 5:
                return (60, 12, 12) if x in (3, 4) else (90, 26, 24)
            if y == 7 and x in (3, 4) and r.random() < 0.5:
                return BLOOD
        if r.random() < 0.06:
            return FLESH
        return c

    def cloth(base, x, y, face):
        c = shade(base, 0.85 + r.random() * 0.2)
        if face in ("left", "right"):
            c = shade(c, 0.9)
        elif face == "back":
            c = shade(c, 0.95)
        return c

    def body(face, x, y, fw):
        top = o["top"]
        extra = o["extra"]
        if face == "top":
            return skin_noise(r, skin) if x in (3, 4) and o["sleeves"] != "long" else cloth(top, x, y, face)
        if face == "bottom":
            return cloth(o["legs"], x, 0, face)
        if y >= 10 and o["legtype"] != "skirt" and extra not in ("jumpsuit", "scrubs"):
            c = cloth(o["legs"], x, y, face)                 # waistband
            return (40, 30, 24) if y == 10 and face == "front" and r.random() < 0.5 else c
        c = cloth(top, x, y, face)
        if face == "front":
            if y == 0 and x in (3, 4):
                return skin_noise(r, skin)                   # neck
            if extra == "tie" and x in (3, 4) and y >= 1:
                return (120, 20, 26) if y < 9 else (90, 14, 20)
            if extra == "tie" and x in (2, 5) and y < 3:
                return (226, 226, 220)                       # shirt collar
            if extra == "police" and y == 2 and x == 1:
                return (200, 170, 60)                        # badge
            if extra == "hivis" and y in (4, 8):
                return (200, 200, 200)
            if extra == "apron" and 3 <= y <= 11 and 1 <= x <= 6:
                return cloth((40, 38, 36), x, y, face)
            if extra == "coat" and x in (3, 4):
                return cloth((60, 48, 36), x, y, face)
            if extra == "coat" and x == 2 and y in (3, 6, 9):
                return (30, 24, 20)
            if extra == "band" and 3 <= y <= 5 and 2 <= x <= 5:
                return (200, 200, 190) if r.random() < 0.6 else c
            if extra == "jersey" and 3 <= y <= 5 and x in (3, 4):
                return (240, 240, 240)
            if extra == "hood" and 4 <= y <= 6 and 1 <= x <= 6:
                return shade(top, 0.75)                      # pocket
        if extra == "hood" and face == "back" and y <= 2:
            return shade(top, 0.8)
        if extra == "flannel" and (x % 3 == 0 or y % 3 == 0):
            return shade(c, 0.6)
        if extra == "hivis" and y in (4, 8):
            return (200, 200, 200)
        return c

    def arm(face, x, y, fw):
        sleeves = o["sleeves"]
        sleeve_end = {"none": 0, "short": 4, "long": 11}[sleeves]
        if face == "top":
            return cloth(o["top"], x, 0, face) if sleeves != "none" else skin_noise(r, skin)
        if face == "bottom" or y >= sleeve_end:
            if y >= 11 or face == "bottom":
                return shade(skin_noise(r, skin), 0.85)      # hand
            return skin_noise(r, skin) if r.random() > 0.05 else FLESH
        c = cloth(o["top"], x, y, face)
        if o["extra"] == "flannel" and (x % 3 == 0 or y % 3 == 0):
            c = shade(c, 0.6)
        if o["extra"] == "hivis" and y == 2:
            c = (200, 200, 200)
        return c

    def leg(face, x, y, fw):
        if face == "bottom" or y >= 11:
            return cloth(o["shoes"], x, 0, face)
        if y == 10 and o["legtype"] != "shorts":
            return cloth(o["shoes"], x, y, face)
        end = {"long": 10, "shorts": 5, "skirt": 4}[o["legtype"]]
        if face != "top" and y >= end:
            return skin_noise(r, skin) if r.random() > 0.05 else FLESH
        c = cloth(o["legs"], x, y, face)
        if o["extra"] == "police" and face in ("left", "right") and x == 1:
            return (200, 170, 60)
        return c

    p.box(0, 0, 8, 8, 8, head)
    p.box(16, 16, 8, 12, 4, body)
    p.box(40, 16, 4, 12, 4, arm)
    p.box(0, 16, 4, 12, 4, leg)
    # caps on the hat layer now and then
    if o["extra"] == "police" or r.random() < 0.15:
        cap = (30, 40, 80) if o["extra"] == "police" else r.choice([(160, 30, 30), (30, 30, 30), (40, 90, 160)])
        for name, (fx, fy, fw, fh) in faces(32, 0, 8, 8, 8).items():
            for i in range(fw):
                for j in range(fh):
                    if name == "top" or (name in ("front", "back", "left", "right") and j < 2):
                        p.put(fx + i, fy + j, shade(cap, 0.85 + r.random() * 0.2))

    # rot: torn cloth showing flesh, blood, grime
    img = p.img
    regions = [(16, 20, 24, 12), (40, 20, 16, 12), (0, 20, 16, 12)]
    for _ in range(r.randint(5, 10)):
        x0, y0, w, h = regions[r.randrange(len(regions))]
        x, y = x0 + r.randrange(w), y0 + r.randrange(h)
        for _ in range(r.randint(2, 6)):
            if x0 <= x < x0 + w and y0 <= y < y0 + h:
                img.putpixel((x, y), FLESH + (255,) if r.random() < 0.6 else shade(skin, 0.8) + (255,))
            x += r.choice([-1, 0, 1])
            y += r.choice([0, 1])
    for _ in range(r.randint(6, 14)):
        x0, y0, w, h = regions[r.randrange(len(regions))]
        x, y = x0 + r.randrange(w), y0 + r.randrange(h)
        for k in range(r.randint(1, 4)):
            if y + k < y0 + h:
                c = img.getpixel((x, y + k))
                img.putpixel((x, y + k), mix(c[:3], BLOOD, 0.55 + r.random() * 0.35) + (255,))
    for x in range(64):
        for y in range(16, 32):
            c = img.getpixel((x, y))
            if c[3] and r.random() < 0.18:
                img.putpixel((x, y), mix(c[:3], (70, 64, 52), 0.35) + (255,))
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    for i in range(COUNT):
        make(i).save(os.path.join(OUT, "civilian_%d.png" % i))
    print("wrote %d skins to %s" % (COUNT, os.path.normpath(OUT)))


if __name__ == "__main__":
    main()
