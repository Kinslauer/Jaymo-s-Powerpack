"""The Overworld's old dead: skins for its zombies and husks, which are villagers who died a very long time ago.

Andy: "Zombies and husks should have villager like skins on the overworld too, as they are meant to be dead villagers
from time past. Like the mca zombies already in the game, except these are explained as being much older and much more
deteriorated." So they read apart from MCA's zombies (the recently dead, in their own clothes, gone green): these are
bone and parchment, no green in them, in villager robes faded to dun, torn, earth-stained, the hands and shins down to
bone. The villager shows in the robe and belt of its land (plains, taiga, snow, swamp, jungle, savanna; the husks the
desert's and the savanna's), now and then a trade's apron, hat or collar, the heavy brow and the long nose, rotted to
a hole.

- zombie_<n>.png (ZOMBIES of them), husk_<n>.png (HUSKS of them), in
  kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/old_dead/ (client/OldDeadSkins.java picks one by
  the mob's id).

Each is a 64x64 zombie texture (the game's humanoid layout: head, hat, body, right arm, right leg; the left limbs
mirror the right). Seeded, so reruns give the same skins.

Run from the project root: python3 kingdoms/tools/gen_old_dead.py   (needs Pillow)
"""
import os
import random
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms", "textures", "entity", "old_dead")
ZOMBIES, HUSKS = 32, 16

# skin gone to parchment (a zombie's) or leather (a husk's), and what shows through it
SKIN = [(132, 118, 100), (120, 108, 94), (142, 128, 108), (112, 100, 88), (126, 112, 92), (136, 124, 112)]
HUSK_SKIN = [(118, 90, 64), (104, 80, 58), (128, 98, 70), (96, 74, 54)]
BONE = (206, 196, 168)
HOLE = (34, 28, 26)
BROW = (58, 44, 36)
WISP = (150, 146, 138)
EARTH = (78, 62, 46)
MOSS = (84, 96, 62)
SAND = (186, 164, 120)
WRAP = (178, 164, 130)
STAIN = (64, 44, 34)
# everything they wore has faded toward this, by this much (a husk's toward SAND)
DUN = (118, 108, 92)
FADE = 0.5

# the robes of each land (main, dark, trim), after the game's villager types
TYPES = {
    "plains": ((110, 82, 66), (74, 52, 44), (140, 112, 72)),
    "taiga": ((80, 56, 46), (52, 36, 32), (120, 100, 80)),
    "snow": ((90, 120, 124), (64, 88, 90), (210, 206, 186)),
    "swamp": ((60, 44, 66), (40, 36, 54), (150, 86, 60)),
    "jungle": ((66, 70, 30), (44, 40, 30), (190, 170, 60)),
    "savanna": ((120, 46, 40), (80, 40, 36), (170, 120, 60)),
    "desert": ((206, 160, 96), (170, 126, 72), (180, 80, 50)),
}
ZOMBIE_TYPES = ["plains", "taiga", "plains", "snow", "swamp", "plains", "jungle", "savanna"]
HUSK_TYPES = ["desert", "desert", "desert", "savanna"]
# trades: what shows of each (an apron's colour, a robe over the land's, a hat on the hat layer, a collar)
TRADES = {
    "none": {},
    "farmer": {"hat": (196, 156, 64)},
    "fisherman": {"hat": (130, 84, 60), "apron": (120, 80, 56)},
    "shepherd": {"hat": (110, 64, 60), "collar": (226, 224, 204)},
    "fletcher": {"hat": (86, 62, 40), "feather": (170, 40, 40)},
    "librarian": {"robe": (200, 196, 176), "collar": (150, 40, 40)},
    "cartographer": {"collar": (140, 64, 40), "apron": (110, 60, 40)},
    "cleric": {"robe": (132, 60, 84), "collar": (200, 170, 70)},
    "armorer": {"apron": (40, 38, 38)},
    "weaponsmith": {"apron": (40, 36, 36), "eyepatch": True},
    "toolsmith": {"apron": (52, 40, 36)},
    "butcher": {"apron": (226, 224, 206), "collar": (150, 40, 40)},
    "leatherworker": {"apron": (110, 66, 40)},
    "mason": {"apron": (64, 60, 60)},
    "nitwit": {"robe": (84, 108, 76)},
}
TRADE_ORDER = list(TRADES)


def faces(u, v, w, h, d):
    """The six faces of a box at (u, v): name -> (x, y, width, height)."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}


def shade(c, k):
    return tuple(max(0, min(255, int(round(x * k)))) for x in c)


def mix(a, b, t):
    return tuple(int(round(a[i] * (1 - t) + b[i] * t)) for i in range(3))


def make(index, husk):
    r = random.Random((0x0D1D if husk else 0x01D) + index * 6151)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    land = (HUSK_TYPES if husk else ZOMBIE_TYPES)[index % (4 if husk else 8)]
    trade = TRADES[TRADE_ORDER[(index * 7 + (3 if husk else 0)) % len(TRADE_ORDER)]]
    robe, dark, trim = TYPES[land]
    robe = trade.get("robe", robe)
    skin = (HUSK_SKIN if husk else SKIN)[r.randrange(len(HUSK_SKIN if husk else SKIN))]
    fade_to = SAND if husk else DUN
    # how far gone: how much bone shows and how torn the robe is
    rot = 0.6 + r.random() * 0.4
    ribs = r.random() < 0.45
    hem = [r.randint(4, 8) for _ in range(16)]       # each leg column's torn hem (rows of robe below the waist)
    # where the skull shows through the skin: a few worn patches on the head (face, centre x, centre y, radius)
    skull = [(r.choice(["top", "left", "right", "back"]), r.randrange(8), r.randrange(8), 1.2 + r.random() * 1.6 * rot)
             for _ in range(r.randint(1, 3))]

    def faded(c):
        return mix(c, fade_to, FADE + r.random() * 0.12)

    def flesh():
        if r.random() < 0.03 * rot:
            return shade(BONE, 0.85 + r.random() * 0.2)
        return shade(skin, 0.82 + r.random() * 0.24)

    def bared(face, x, y):
        return any(f == face and (x - cx) ** 2 + (y - cy) ** 2 <= rad * rad for f, cx, cy, rad in skull)

    def cloth(c, face):
        k = 0.85 + r.random() * 0.2
        if face in ("left", "right"):
            k *= 0.9
        elif face == "back":
            k *= 0.94
        return faded(shade(c, k))

    def put(x, y, c, a=255):
        img.putpixel((x, y), tuple(c[:3]) + (a,))

    def paint(u, v, w, h, d, colour):
        for face, (fx, fy, fw, fh) in faces(u, v, w, h, d).items():
            for i in range(fw):
                for j in range(fh):
                    y = -1 if face == "top" else h if face == "bottom" else j
                    c = colour(face, i, y)
                    if c is not None:
                        put(fx + i, fy + j, c)

    def head(face, x, y):
        if face != "front" and face != "bottom" and bared(face, x, max(y, 0)):
            return shade(BONE, 0.8 + r.random() * 0.2)
        if face == "top" or (face in ("back", "left", "right") and y <= 1):
            if r.random() < 0.12:
                return shade(WISP, 0.8 + r.random() * 0.3)   # what's left of the hair
            return flesh()
        if face != "front":
            return flesh()
        # the face: a heavy brow, empty sockets, the long nose rotted to a hole, the teeth bare
        if y == 2 and 1 <= x <= 6:
            return shade(BROW, 0.8 + r.random() * 0.3) if r.random() < 0.8 else flesh()
        if y == 3 and x in (1, 2, 5, 6):
            if trade.get("eyepatch") and x in (5, 6):
                return (26, 22, 20)
            return HOLE if x in (2, 5) or r.random() < 0.6 else shade(HOLE, 1.6)
        if y == 4 and x in (2, 5):
            return shade(HOLE, 1.4)
        if x in (3, 4) and 3 <= y <= 4:
            return shade(skin, 1.08 if x == 3 else 0.96)     # the bridge of the nose
        if x in (2, 5) and 4 <= y <= 5:
            return shade(skin, 0.7)                          # its shadow down both sides
        if x in (3, 4) and y == 5:
            return shade(BONE, 0.7)
        if x in (3, 4) and y == 6:
            return HOLE                                      # where the nose was
        if y == 7 and 2 <= x <= 5:
            return BONE if (x + index) % 2 else shade(HOLE, 1.3)  # teeth
        if x in (1, 6) and y in (4, 5) and r.random() < rot * 0.7:
            return shade(BONE, 0.9)                          # cheekbones through the skin
        return flesh()

    def body(face, x, y):
        if face in ("top", "bottom"):
            return cloth(dark if face == "bottom" else robe, face)
        if face == "front" and y == 0 and x in (3, 4):
            return flesh()                                   # the neck
        if y == 8:
            # the belt and its buckle
            if face == "front" and x in (3, 4):
                return faded(shade(trim, 1.1))
            return cloth(shade(dark, 0.8), face)
        c = cloth(robe, face)
        if face == "front":
            if trade.get("collar") and y <= 1:
                return cloth(trade["collar"], face)
            if trade.get("apron") and 2 <= y and 1 <= x <= 6:
                return cloth(trade["apron"], face)
            if x in (3, 4) and y < 8:
                return cloth(dark, face)                     # the robe's front seam
            if ribs and 2 <= y <= 6 and 1 <= x <= 6 and not (x in (3, 4)):
                return shade(BONE, 0.9) if y % 2 == 0 else HOLE  # ribs through a rent in the robe
        if face == "back" and trade.get("collar") and y <= 1:
            return cloth(trade["collar"], face)
        if y in (0, 11) and land in ("snow", "taiga"):
            return cloth(trim, face)                         # fur trim
        return c

    def arm(face, x, y):
        if face == "bottom" or y >= 10:
            # finger bones
            if face == "bottom":
                return shade(BONE, 0.8)
            return shade(BONE, 0.85 + r.random() * 0.2) if (x + y) % 2 == 0 or r.random() < 0.3 else HOLE
        if face == "top":
            return cloth(robe, face)
        if y == 9:
            return cloth(trim, face)                         # the cuff
        if husk and y >= 6 and r.random() < 0.6:
            return shade(WRAP, 0.8 + r.random() * 0.25)      # grave-wraps
        return cloth(robe, face)

    def leg(face, x, y):
        col = {"right": 0, "front": 4, "left": 8, "back": 12}.get(face, 0) + (x if face not in ("top", "bottom") else 0)
        if face == "top":
            return cloth(robe, face)
        if face == "bottom":
            return shade(BONE, 0.7)
        if y < hem[col % 16]:
            return cloth(robe if y < hem[col % 16] - 1 else dark, face)
        if y >= 10:
            return shade(BONE, 0.8 + r.random() * 0.25) if r.random() < 0.7 else HOLE  # foot bones
        if husk and r.random() < 0.5:
            return shade(WRAP, 0.75 + r.random() * 0.25)
        # the shin down to bone, a strip of dry skin
        return shade(BONE, 0.82 + r.random() * 0.2) if x in (1, 2) or r.random() < rot * 0.5 else flesh()

    paint(0, 0, 8, 8, 8, head)
    paint(16, 16, 8, 12, 4, body)
    paint(40, 16, 4, 12, 4, arm)
    paint(0, 16, 4, 12, 4, leg)

    # the hat layer: a trade's hat or the desert's head-cloth, eaten through
    hat = trade.get("hat")
    if husk and land == "desert" and hat is None:
        hat = TYPES["desert"][2]
    if hat is not None:
        for face, (fx, fy, fw, fh) in faces(32, 0, 8, 8, 8).items():
            for i in range(fw):
                for j in range(fh):
                    if face == "top" or (face in ("front", "back", "left", "right") and j < 2):
                        if r.random() < 0.2:
                            continue                         # moth holes
                        c = trade["feather"] if trade.get("feather") and face == "left" and i == 2 and j < 2 else hat
                        put(fx + i, fy + j, cloth(c, face))

    # time: rents in the robe, old stains, the earth they lay in (a husk's sand), moss on the low parts
    regions = [(16, 20, 24, 12), (40, 20, 16, 12), (0, 20, 16, 12)]
    for _ in range(r.randint(6, 11)):
        x0, y0, w, h = regions[r.randrange(len(regions))]
        x, y = x0 + r.randrange(w), y0 + r.randrange(h)
        for _ in range(r.randint(2, 6)):
            if x0 <= x < x0 + w and y0 <= y < y0 + h:
                put(x, y, HOLE if r.random() < 0.55 else shade(BONE, 0.8))
            x += r.choice([-1, 0, 1])
            y += r.choice([0, 1])
    for _ in range(r.randint(3, 7)):
        x0, y0, w, h = regions[r.randrange(len(regions))]
        x, y = x0 + r.randrange(w), y0 + r.randrange(h)
        for k in range(r.randint(1, 3)):
            if y + k < y0 + h:
                c = img.getpixel((x, y + k))
                put(x, y + k, mix(c[:3], STAIN, 0.5))
    for x in range(64):
        for y in range(16, 32):
            c = img.getpixel((x, y))
            if not c[3]:
                continue
            low = (y - 20) / 12.0 if y >= 20 else 0.0     # rows further down each part
            if r.random() < 0.12 + 0.25 * low:
                put(x, y, mix(c[:3], SAND if husk else EARTH, 0.45))
            elif not husk and r.random() < 0.06 * low:
                put(x, y, mix(c[:3], MOSS, 0.6))
    for x in range(64):
        for y in range(0, 16):
            c = img.getpixel((x, y))
            if c[3] and r.random() < 0.05:
                put(x, y, mix(c[:3], SAND if husk else EARTH, 0.35))
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    for i in range(ZOMBIES):
        make(i, False).save(os.path.join(OUT, "zombie_%d.png" % i))
    for i in range(HUSKS):
        make(i, True).save(os.path.join(OUT, "husk_%d.png" % i))
    print("wrote %d zombie and %d husk skins to %s" % (ZOMBIES, HUSKS, os.path.relpath(OUT, ROOT)))


if __name__ == "__main__":
    main()
