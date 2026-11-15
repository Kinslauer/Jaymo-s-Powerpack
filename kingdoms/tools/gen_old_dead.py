"""The old dead of the present day: skins for its zombies and husks, which are villagers who died a very long time ago.

Andy: "Zombies and husks should have villager like skins on the overworld too, as they are meant to be dead villagers
from time past. Like the mca zombies already in the game, except these are explained as being much older and much more
deteriorated." So they read apart from MCA's zombies (the recently dead, in their own clothes, gone green): these are
bone and parchment, no green in them, in villager robes faded to dun, torn, earth-stained, the hands and shins down to
bone. The villager shows in the robe and belt of its land (plains, taiga, snow, swamp, jungle, savanna; the husks the
desert's and the savanna's), now and then a trade's apron, hat or collar, the heavy brow and the long nose, rotted to
a hole.

The zombies (risen/, by risen()) are drawn by where they rose and how far gone they are (Kingdoms' RisenLooks stores
the place and the land on each zombie where it first stood; client/OldDeadSkins picks a skin from them and its id):
- the place (PLACES): plain (earth and moss), dried (desert, badlands, savanna: leathered skin split along the
  creases, cloth bleached to sand, dust), bloated (swamp, river, beach: swollen grey-violet skin, dark sodden cloth,
  silt and weed), pale (caves, the deep dark, anywhere deep and sunless: chalk-white skin, cloth gone grey, stone dust
  and cobweb), frostbitten (snowy and frozen lands: blue-grey skin, black fingers and nose, rime on every upward face);
- the land's clothes (PLACE_LANDS: only the lands each place can rise in are drawn; the Java falls back to the first);
- the stage of rot (STAGES): 0 still has its face and its nose, 1 is down to bone at the hands and shins, 2 is a
  skull in rags with the ribs showing;
- VARIANTS of each, with their own trade, hat and wear.

- risen/<place>/<land>_<stage>_<variant>.png: the zombies'. husk_<n>.png (HUSKS of them, by make()): the husks'.
- eyes.png, eyes_baby.png, eyes_future.png: what glows in the dark (client/DeadEyes): the sockets of these skins, of
  the game's baby zombie layout, and of Jaymo's Powers' future dead (tools/gen_future_zombies.py: the eyes one row
  lower). Only the eyes are drawn; the rest is clear.

All in kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/old_dead/. Each skin is a 64x64 zombie
texture (the game's humanoid layout: head, hat, body, right arm, right leg; the left limbs mirror the right). Seeded,
so reruns give the same skins. Our own art: nothing is taken from the game's textures or any pack.

Run from the project root: python3 kingdoms/tools/gen_old_dead.py   (needs Pillow)
"""
import os
import random
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms", "textures", "entity", "old_dead")
HUSKS = 16

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


# ---------------------------------------------------------------- the risen: by place, land and stage of rot

PLACES = ["plain", "dried", "bloated", "pale", "frostbitten"]
# the lands each place is drawn in; keep in step with RisenLooks.Place in the Java (the first is the fallback)
PLACE_LANDS = {
    "plain": ["plains", "taiga", "snow", "swamp", "jungle", "savanna", "desert"],
    "dried": ["desert", "savanna"],
    "bloated": ["plains", "swamp"],
    "pale": ["plains", "taiga", "snow", "swamp", "jungle", "savanna", "desert"],
    "frostbitten": ["snow", "taiga", "plains"],
}
STAGES, VARIANTS = 3, 3

# each place's skin, and what its clothes fade toward and how far
PLACE_SKIN = {
    "plain": SKIN,
    "dried": [(150, 114, 78), (138, 104, 72), (160, 124, 86), (128, 98, 70)],
    "bloated": [(132, 134, 128), (120, 128, 128), (140, 130, 136), (116, 124, 116)],
    "pale": [(200, 200, 196), (190, 192, 194), (208, 204, 196), (182, 186, 190)],
    "frostbitten": [(152, 166, 178), (142, 158, 172), (162, 172, 180), (134, 150, 166)],
}
PLACE_FADE = {"plain": (DUN, 0.5), "dried": ((200, 180, 140), 0.58), "bloated": ((48, 52, 46), 0.42),
              "pale": ((132, 132, 136), 0.62), "frostbitten": ((150, 164, 178), 0.42)}
# what each stage leaves: how far gone (bone through the skin), the torn hem's rows, worn patches on the skull, rents
ROT = [0.3, 0.65, 0.95]
HEMS = [(6, 9), (4, 8), (2, 6)]
SKULLS = [(0, 1), (1, 3), (3, 5)]
RENTS = [(3, 5), (6, 11), (10, 15)]
CRACK = (72, 46, 30)
BRUISE = (98, 74, 106)
SHEEN = (186, 192, 188)
SILT = (58, 56, 42)
WEED = (60, 92, 50)
STONE = (118, 118, 120)
WEB = (228, 230, 232)
RIME = (224, 236, 244)
ICE = (166, 206, 232)
FROSTBITE = (40, 42, 58)
# a land's own headwear, when the trade gives none: its colour and how often
LAND_HATS = {"snow": ((226, 222, 210), 0.5), "taiga": ((96, 70, 50), 0.35), "desert": ((210, 186, 140), 0.6),
             "savanna": ((170, 60, 44), 0.3), "jungle": ((70, 96, 40), 0.3), "swamp": ((70, 60, 80), 0.3),
             "plains": ((196, 156, 64), 0.25)}
# the boxes: head, body, arm, leg (u, v, w, h, d)
BOXES = [(0, 0, 8, 8, 8), (16, 16, 8, 12, 4), (40, 16, 4, 12, 4), (0, 16, 4, 12, 4)]


def risen(place, land, stage, v):
    """One of the risen: a villager of {land}, risen from {place}, at {stage} of rot (variant {v})."""
    r = random.Random(((PLACES.index(place) * 131 + list(TYPES).index(land) * 17 + stage * 5 + v) * 7919) ^ 0x515E)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    trade = dict(TRADES[TRADE_ORDER[r.randrange(len(TRADE_ORDER))]])
    trade.pop("eyepatch", None)                          # it would hide an eye that glows (client/DeadEyes)
    robe, dark, trim = TYPES[land]
    robe = trade.get("robe", robe)
    skin = PLACE_SKIN[place][r.randrange(len(PLACE_SKIN[place]))]
    fade_to, fade = PLACE_FADE[place]
    rot = ROT[stage] + r.random() * 0.08
    ribs = stage == 2 or (stage == 1 and r.random() < 0.45)
    hem = [r.randint(*HEMS[stage]) for _ in range(16)]
    skull = [(r.choice(["top", "left", "right", "back"]), r.randrange(8), r.randrange(8), 1.2 + r.random() * 1.6 * rot)
             for _ in range(r.randint(*SKULLS[stage]))]
    flesh_px = set()                                     # where skin shows (for what the place does to it after)

    def faded(c):
        return mix(c, fade_to, fade + r.random() * 0.12)

    def flesh():
        if r.random() < 0.03 * rot:
            return shade(BONE, 0.85 + r.random() * 0.2)
        return shade(skin, 0.82 + r.random() * 0.24)

    def bone(k=0.82):
        return shade(BONE, k + r.random() * 0.2)

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

    def paint(u, v_, w, h, d, colour):
        for face, (fx, fy, fw, fh) in faces(u, v_, w, h, d).items():
            for i in range(fw):
                for j in range(fh):
                    y = -1 if face == "top" else h if face == "bottom" else j
                    c = colour(face, i, y)
                    if c is not None:
                        if c[0] == "skin":
                            flesh_px.add((fx + i, fy + j))
                            c = c[1]
                        put(fx + i, fy + j, c)

    def sk(c=None):
        """Skin (marked, so the place's wear finds it)."""
        return ("skin", flesh() if c is None else c)

    def head(face, x, y):
        if face != "front" and face != "bottom" and bared(face, x, max(y, 0)):
            return bone(0.8)
        if face == "top" or (face in ("back", "left", "right") and y <= 1):
            if r.random() < 0.12:
                return shade(WISP, 0.8 + r.random() * 0.3)   # what's left of the hair
            return sk()
        if face != "front":
            return sk()
        # the face: a heavy brow, sockets where the eyes were (they glow in the dark: eyes.png), the long nose
        if y == 2 and 1 <= x <= 6:
            return shade(BROW, 0.8 + r.random() * 0.3) if r.random() < 0.8 else sk()
        if y == 3 and x in (1, 2, 5, 6):
            return HOLE if x in (2, 5) or r.random() < 0.6 else shade(HOLE, 1.6)
        if y == 4 and x in (2, 5):
            return shade(HOLE, 1.4)
        if stage == 2:
            # a skull: bone, the nose a hole, the teeth bare, a scrap of skin at the edges
            if y == 4 and x in (1, 6):
                return shade(HOLE, 1.8)
            if x in (3, 4) and 5 <= y <= 6:
                return HOLE if y == 6 or x == 3 else shade(HOLE, 1.5)
            if y == 7 and 1 <= x <= 6:
                return BONE if (x + v) % 2 else shade(HOLE, 1.3)
            if x in (0, 7) and r.random() < 0.4:
                return sk()
            return bone(0.78)
        if x in (3, 4) and 3 <= y <= 4:
            return sk(shade(skin, 1.08 if x == 3 else 0.96))  # the bridge of the nose
        if x in (2, 5) and 4 <= y <= 5:
            return sk(shade(skin, 0.7))                      # its shadow down both sides
        if stage == 0:
            # the nose still there, its tip dark; the lips drawn back over a few teeth
            if x in (3, 4) and y == 5:
                return sk(shade(skin, 1.02 if x == 3 else 0.92))
            if x in (3, 4) and y == 6:
                return sk(shade(skin, 0.72))
            if y == 7 and 2 <= x <= 5:
                return shade(BONE, 0.85) if x == 3 + v % 2 or place == "dried" and (x + v) % 2 else shade(skin, 0.45)
            return sk()
        if x in (3, 4) and y == 5:
            return bone(0.7)
        if x in (3, 4) and y == 6:
            return HOLE                                      # where the nose was
        if y == 7 and 2 <= x <= 5:
            return BONE if (x + v) % 2 else shade(HOLE, 1.3)  # teeth
        if x in (1, 6) and y in (4, 5) and r.random() < rot * 0.7:
            return bone(0.9)                                 # cheekbones through the skin
        return sk()

    def body(face, x, y):
        if face in ("top", "bottom"):
            return cloth(dark if face == "bottom" else robe, face)
        if face == "front" and y == 0 and x in (3, 4):
            return sk()                                      # the neck
        if y == 8:
            # the belt and its buckle
            if face == "front" and x in (3, 4):
                return faded(shade(trim, 1.1))
            return cloth(shade(dark, 0.8), face)
        if face == "front":
            if trade.get("collar") and y <= 1:
                return cloth(trade["collar"], face)
            if ribs and 2 <= y <= 6 and 1 <= x <= 6 and x not in (3, 4):
                return bone(0.8) if y % 2 == 0 else HOLE     # ribs through a rent in the robe
            if trade.get("apron") and 2 <= y and 1 <= x <= 6:
                return cloth(trade["apron"], face)
            if x in (3, 4) and y < 8:
                return cloth(dark, face)                     # the robe's front seam
        if face == "back" and trade.get("collar") and y <= 1:
            return cloth(trade["collar"], face)
        if face == "back" and stage == 2 and 3 <= y <= 7 and x in (3, 4):
            return bone(0.75) if y % 2 else HOLE             # the spine through the back
        if y in (0, 11) and land in ("snow", "taiga"):
            return cloth(trim, face)                         # fur trim
        return cloth(robe, face)

    sleeve = [9, 9, r.randint(4, 6)][stage]                  # rows of sleeve left

    def arm(face, x, y):
        if face == "top":
            return cloth(robe, face)
        if face == "bottom" or y >= 10:
            if place == "frostbitten" and stage < 2:
                return shade(FROSTBITE, 0.8 + r.random() * 0.4)  # black fingers
            if stage == 0 and face != "bottom" and y == 10:
                return sk()                                  # the hand still skinned, the fingertips bare
            if face == "bottom":
                return bone(0.7)
            return bone(0.85) if (x + y) % 2 == 0 or r.random() < 0.3 else HOLE  # finger bones
        if y < sleeve:
            return cloth(robe, face)
        if y == sleeve and stage < 2:
            return cloth(trim, face)                         # the cuff
        if stage == 2:
            # the forearm's two bones, nothing between
            return bone(0.8) if x in (0, 3) or r.random() < 0.25 else HOLE
        return sk()

    def leg(face, x, y):
        col = {"right": 0, "front": 4, "left": 8, "back": 12}.get(face, 0) + (x if face not in ("top", "bottom") else 0)
        if face == "top":
            return cloth(robe, face)
        if face == "bottom":
            return bone(0.6) if stage else shade(dark, 0.6)
        if y < hem[col % 16]:
            return cloth(robe if y < hem[col % 16] - 1 else dark, face)
        if y >= 10:
            if stage == 0:
                return cloth(shade(dark, 0.7), face) if r.random() < 0.8 else bone(0.8)  # what's left of the shoes
            return bone(0.8) if r.random() < 0.7 else HOLE   # foot bones
        if stage == 2:
            return bone(0.8) if x in (1, 2) else HOLE if r.random() < 0.6 else bone(0.7)
        # the shin down to bone, a strip of dry skin
        return bone(0.82) if x in (1, 2) and r.random() < rot or r.random() < rot * 0.4 else sk()

    paint(0, 0, 8, 8, 8, head)
    paint(16, 16, 8, 12, 4, body)
    paint(40, 16, 4, 12, 4, arm)
    paint(0, 16, 4, 12, 4, leg)

    # the hat layer: a trade's hat, else now and then the land's own (a hood, a head-cloth, a band), eaten through
    hat = trade.get("hat")
    if hat is None:
        colour, chance = LAND_HATS[land]
        if r.random() < chance:
            hat = colour
    if hat is not None:
        for face, (fx, fy, fw, fh) in faces(32, 0, 8, 8, 8).items():
            for i in range(fw):
                for j in range(fh):
                    if face == "top" or (face in ("front", "back", "left", "right") and j < 2):
                        if r.random() < 0.15 + 0.1 * stage:
                            continue                         # moth holes
                        c = trade["feather"] if trade.get("feather") and face == "left" and i == 2 and j < 2 else hat
                        put(fx + i, fy + j, cloth(c, face))

    # time: rents in the robe and old stains, more the further gone
    regions = [(16, 20, 24, 12), (40, 20, 16, 12), (0, 20, 16, 12)]
    for _ in range(r.randint(*RENTS[stage])):
        x0, y0, w, h = regions[r.randrange(len(regions))]
        x, y = x0 + r.randrange(w), y0 + r.randrange(h)
        for _ in range(r.randint(2, 6)):
            if x0 <= x < x0 + w and y0 <= y < y0 + h:
                put(x, y, HOLE if r.random() < 0.55 else shade(BONE, 0.8))
                flesh_px.discard((x, y))
            x += r.choice([-1, 0, 1])
            y += r.choice([0, 1])
    for _ in range(r.randint(3, 7)):
        x0, y0, w, h = regions[r.randrange(len(regions))]
        x, y = x0 + r.randrange(w), y0 + r.randrange(h)
        for k in range(r.randint(1, 3)):
            if y + k < y0 + h:
                c = img.getpixel((x, y + k))
                put(x, y + k, mix(c[:3], STAIN, 0.5))
    PLACE_WEAR[place](img, r, flesh_px, put)
    return img


def upward():
    """Every pixel that faces up or sits on a part's top rows: (x, y) -> how much it faces up (1 on top)."""
    up = {}
    for box in BOXES:
        for face, (fx, fy, fw, fh) in faces(*box).items():
            for i in range(fw):
                for j in range(fh):
                    if face == "top":
                        up[(fx + i, fy + j)] = 1.0
                    elif face != "bottom" and j < 2:
                        up[(fx + i, fy + j)] = 0.5 - 0.2 * j
    return up


def lows():
    """The pixels on the lower half of the body's and limbs' sides: (x, y) -> how low (0..1)."""
    low = {}
    for box in BOXES[1:]:
        h = box[3]
        for face, (fx, fy, fw, fh) in faces(*box).items():
            if face in ("top", "bottom"):
                continue
            for i in range(fw):
                for j in range(fh):
                    if j >= h // 2:
                        low[(fx + i, fy + j)] = (j - h // 2) / (h / 2.0)
    return low


UPWARD = upward()
LOWS = lows()


def wear_plain(img, r, flesh_px, put):
    """The earth they lay in, moss on the low parts."""
    for x in range(64):
        for y in range(0, 32):
            c = img.getpixel((x, y))
            if not c[3]:
                continue
            low = LOWS.get((x, y), 0.0)
            if r.random() < (0.05 if y < 16 else 0.12) + 0.25 * low:
                put(x, y, mix(c[:3], EARTH, 0.45 if y >= 16 else 0.35))
            elif r.random() < 0.06 * low:
                put(x, y, mix(c[:3], MOSS, 0.6))


def wear_dried(img, r, flesh_px, put):
    """Leathered skin split along the creases, dust over everything."""
    for (x, y) in sorted(flesh_px):
        if r.random() < 0.12:
            put(x, y, mix(img.getpixel((x, y))[:3], CRACK, 0.7))
    for x in range(64):
        for y in range(0, 32):
            c = img.getpixel((x, y))
            if c[3] and r.random() < 0.1 + 0.3 * LOWS.get((x, y), 0.0) + 0.2 * UPWARD.get((x, y), 0.0):
                put(x, y, mix(c[:3], SAND, 0.45))


def wear_bloated(img, r, flesh_px, put):
    """Swollen and blotched where the skin shows, wet on the upward faces, silt and weed low down."""
    for (x, y) in sorted(flesh_px):
        c = img.getpixel((x, y))[:3]
        roll = r.random()
        if roll < 0.22:
            put(x, y, mix(c, BRUISE, 0.55))
        elif roll < 0.4:
            put(x, y, shade(c, 1.14))
    for (x, y), up in sorted(UPWARD.items()):
        c = img.getpixel((x, y))
        if c[3] and r.random() < 0.3 * up:
            put(x, y, mix(c[:3], SHEEN, 0.35))
    for (x, y), low in sorted(LOWS.items()):
        c = img.getpixel((x, y))
        if c[3] and r.random() < 0.15 + 0.5 * low:
            put(x, y, mix(c[:3], SILT, 0.5))
    # weed caught on the limbs, hanging in strands
    for _ in range(r.randint(3, 6)):
        u, v, w, h, d = BOXES[r.randrange(1, 4)]
        x = u + r.randrange(2 * (w + d))
        y = v + d + r.randrange(h // 2)
        for k in range(r.randint(2, 5)):
            if y + k < v + d + h and img.getpixel((x, y + k))[3]:
                put(x, y + k, shade(WEED, 0.8 + r.random() * 0.4))


def wear_pale(img, r, flesh_px, put):
    """Stone dust, and cobweb strung across them from lying still so long."""
    for x in range(64):
        for y in range(0, 32):
            c = img.getpixel((x, y))
            if c[3] and r.random() < 0.08 + 0.2 * LOWS.get((x, y), 0.0):
                put(x, y, mix(c[:3], STONE, 0.4))
    for _ in range(r.randint(3, 5)):
        fx, fy, fw, fh = faces(*BOXES[r.randrange(4)])[r.choice(["front", "back", "left", "right"])]
        x, y = fx + r.randrange(fw), fy + r.randrange(fh)
        dx = r.choice([-1, 1])
        for _ in range(r.randint(3, 6)):
            if fx <= x < fx + fw and fy <= y < fy + fh:
                put(x, y, mix(img.getpixel((x, y))[:3], WEB, 0.65))
            x += dx
            y += 1


def wear_frostbitten(img, r, flesh_px, put):
    """Rime on every upward face, frost through the cloth, ice under the hems, the nose and lips black."""
    for (x, y), up in sorted(UPWARD.items()):
        c = img.getpixel((x, y))
        if c[3] and r.random() < 0.25 + 0.6 * up:
            put(x, y, mix(c[:3], RIME, 0.55 + r.random() * 0.35))
    for x in range(64):
        for y in range(0, 32):
            c = img.getpixel((x, y))
            if c[3] and r.random() < 0.08:
                put(x, y, mix(c[:3], RIME, 0.4))
    # where the cloth ends over skin or bone, the row under it gone to ice here and there
    for x in range(64):
        for y in range(17, 31):
            here, below = img.getpixel((x, y)), img.getpixel((x, y + 1))
            if here[3] and below[3] and here[:3] != below[:3] and r.random() < 0.12:
                put(x, y + 1, mix(below[:3], ICE, 0.7))
    for (x, y) in ((11, 13), (12, 13), (11, 14), (12, 14), (11, 15), (12, 15)):
        if (x, y) in flesh_px:
            put(x, y, mix(img.getpixel((x, y))[:3], FROSTBITE, 0.7))


PLACE_WEAR = {"plain": wear_plain, "dried": wear_dried, "bloated": wear_bloated, "pale": wear_pale,
              "frostbitten": wear_frostbitten}

# ---------------------------------------------------------------- eyes that glow

EYE = (214, 240, 244)
EYE_EDGE = (140, 196, 214)
# (x, y) -> (colour, alpha): our skins' sockets (the head's front at 8, 8: rows 3 and 4), the game's baby zombie
# layout's two-wide eyes (row 4 of its head's front), and Jaymo's Powers' future dead (their eyes a row lower than ours)
EYES = {
    "eyes": {(10, 11): (EYE, 255), (13, 11): (EYE, 255), (9, 11): (EYE_EDGE, 140), (14, 11): (EYE_EDGE, 140),
             (10, 12): (EYE_EDGE, 150), (13, 12): (EYE_EDGE, 150)},
    "eyes_baby": {(10, 12): (EYE, 255), (13, 12): (EYE, 255), (9, 12): (EYE_EDGE, 170), (14, 12): (EYE_EDGE, 170)},
    "eyes_future": {(10, 12): (EYE, 255), (13, 12): (EYE, 255), (10, 11): (EYE_EDGE, 120), (13, 11): (EYE_EDGE, 120)},
}


def eyes(points):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for (x, y), (c, a) in points.items():
        img.putpixel((x, y), c + (a,))
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    count = 0
    for place in PLACES:
        os.makedirs(os.path.join(OUT, "risen", place), exist_ok=True)
        for land in PLACE_LANDS[place]:
            for stage in range(STAGES):
                for v in range(VARIANTS):
                    risen(place, land, stage, v).save(os.path.join(OUT, "risen", place, "%s_%d_%d.png" % (land, stage, v)))
                    count += 1
    for i in range(HUSKS):
        make(i, True).save(os.path.join(OUT, "husk_%d.png" % i))
    for name, points in EYES.items():
        eyes(points).save(os.path.join(OUT, name + ".png"))
    print("wrote %d risen and %d husk skins and the eyes to %s" % (count, HUSKS, os.path.relpath(OUT, ROOT)))


if __name__ == "__main__":
    main()
