"""Writes the Costume Table's assets and data (0.11.66): the costume pieces' mask textures, the table's block and item
models and textures, the costume item's textures, the recipe and the loot table. Our own art, drawn here.

    python -I tools/gen_costume.py

Every costume piece is drawn twice, once per colour, from a pair of greyscale textures: `<piece>_primary.png` holds the
pixels the primary dye tints and `<piece>_secondary.png` the secondary's; the two never overlap. Suits use the player
skin's layout (64x64, the base layer's boxes only; wide and slim arms each have their own), masks and capes their own
small layouts (see com.jaymospowers.client.costume.CostumeModels, which must agree with the UVs here).
"""
import json
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymospowers")
DATA = os.path.join(RES, "data", "jaymospowers")
NS = "jaymospowers"
COSTUME = os.path.join(ASSETS, "textures", "entity", "costume")

P, S = 1, 2  # primary, secondary


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def save(im, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)


# ---- box UV layout ------------------------------------------------------------------------------------------------

def box_faces(u, v, w, h, d):
    """A model box's faces in its texture: name -> (x, y, width, height), as Minecraft lays them out."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),       # the box's -x... the side seen on the wearer's right for limbs
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


def paint_box(primary, secondary, u, v, w, h, d, part, pattern, rng):
    """Calls pattern(part, face, x, y, w, h, rng) for every pixel of a box; P or S pixels go to that colour's image.
    A pattern may also return (P or S, shade): the shade is added to the cloth's (negative darkens the dye's colour)."""
    for face, (fx, fy, fw, fh) in box_faces(u, v, w, h, d).items():
        for y in range(fh):
            for x in range(fw):
                which = pattern(part, face, x, y, fw, fh, rng)
                if which is None:
                    continue
                which, extra = which if isinstance(which, tuple) else (which, 0)
                shade = max(0, min(255, cloth_shade(x, y, fw, fh, rng) + extra))
                (primary if which == P else secondary).putpixel((fx + x, fy + y), (shade, shade, shade, 255))


def cloth_shade(x, y, w, h, rng):
    """Near-white (the dye tints it), with a faint weave and darker seams at the face's edges."""
    base = 236 + ((x + y) % 2) * 6 + rng.randint(-5, 5)
    if x == 0 or y == 0 or x == w - 1 or y == h - 1:
        base -= 16
    return max(0, min(255, base))


# ---- the suits ----------------------------------------------------------------------------------------------------

def suit_boxes(slim):
    """The skin's base-layer boxes: part -> (u, v, w, h, d)."""
    aw = 3 if slim else 4
    return {
        "body": (16, 16, 8, 12, 4),
        "right_arm": (40, 16, aw, 12, 4),
        "left_arm": (32, 48, aw, 12, 4),
        "right_leg": (0, 16, 4, 12, 4),
        "left_leg": (16, 48, 4, 12, 4),
    }


def is_arm(part):
    return part.endswith("arm")


def is_leg(part):
    return part.endswith("leg")


def bodysuit(part, face, x, y, w, h, rng):
    """A clean bodysuit: gloves, boots, a belt and stripes down the sides in the secondary colour."""
    if is_arm(part):
        if face == "bottom" or (face != "top" and y >= 9):
            return S
        if face in ("right", "left") and x == w // 2 and y < 9 and part[0] == ("r" if face == "right" else "l"):
            return S
        return P
    if is_leg(part):
        if face == "bottom" or (face != "top" and y >= 8):
            return S
        return P
    # body
    if face in ("front", "back") and y in (10, 11):
        return S
    if face == "front" and 1 <= y <= 3 and abs(x - 3.5) < 4 - y:
        return S  # a chevron at the collar
    if face in ("right", "left") and x in (1, 2) and y < 10:
        return S
    return P


def armoured(part, face, x, y, w, h, rng):
    """Plated: a chest plate, bracers, knee guards and greaves in the secondary colour over a primary undersuit."""
    if is_arm(part):
        if face == "top":
            return S
        if face == "bottom" or y >= 7:
            return S
        return P
    if is_leg(part):
        if face == "bottom" or y >= 9:
            return S
        if face == "front" and 3 <= y <= 5:
            return S
        return P
    if face == "front" and 1 <= y <= 7 and 1 <= x <= 6:
        return S
    if face == "back" and 1 <= y <= 6 and 2 <= x <= 5:
        return S
    if face in ("front", "back") and y == 10:
        return S
    return P


def robes(part, face, x, y, w, h, rng):
    """Long robes: wide cuffs, a sash and a deep hem in the secondary colour."""
    if is_arm(part):
        if face == "bottom" or y >= 9:
            return S
        return P
    if is_leg(part):
        if face == "bottom" or y >= 9:
            return S
        if face == "front" and ((part == "right_leg" and x == w - 1) or (part == "left_leg" and x == 0)):
            return S  # the robe's opening down the middle
        return P
    if face == "front" and x in (3, 4):
        return S
    if y in (7, 8) and face != "top":
        return S
    return P


def coat(part, face, x, y, w, h, rng):
    """A long coat over secondary trousers and shirt: lapels, cuffs, the coat's skirts to the knee."""
    if is_arm(part):
        if face != "top" and y in (9, 10):
            return S
        if y == 11 or face == "bottom":
            return None  # bare hands
        return P
    if is_leg(part):
        if face == "bottom":
            return S
        if y < 6 and face != "front":
            return P
        if y < 6 and face == "front":
            return P if ((part == "right_leg" and x < w - 1) or (part == "left_leg" and x > 0)) else S
        return S
    if face == "front" and 2 <= x <= 5 and y < 9:
        return S if (x in (3, 4) or y < 2) else P
    return P


def hash_of(*parts):
    """A small stable hash (Python's own changes between runs)."""
    h = 2166136261
    for c in "|".join(str(p) for p in parts):
        h = (h ^ ord(c)) * 16777619 & 0xFFFFFFFF
    return h


def ragged(part, face, x, y, w, h, rng):
    """Worn-out rags: torn sleeves and trouser legs, holes and patches."""
    # patches in squares of 3, holes single pixels, both fixed per spot (not noise)
    patch = hash_of(part, face, x // 3, y // 3) % 6 == 0
    hole = hash_of(part, face, x, y) % 23 == 0
    if is_arm(part):
        if face == "bottom":
            return None
        if face != "top" and y >= 5 + (x * 7 + 3) % 3:
            return None
        return None if hole else (S if patch else P)
    if is_leg(part):
        if face == "bottom":
            return None
        if face != "top" and y >= 8 + (x * 5 + 1) % 3:
            return None
        return None if hole else (S if patch else P)
    if face in ("front", "back") and y >= 10 and (x + y) % 3 == 0:
        return None
    if face in ("front", "back") and y in (8,) :
        return S  # a rope belt
    return None if hole else (S if patch else P)


# ---- the power sets (0.11.78): one cut per power, offered at the table only while you have it -------------------

def ring(x, y, cx, cy):
    """The distance from (cx, cy), rounded to whole pixels."""
    return int(((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 + 0.5)


def web_lines(x, y, cx, cy):
    """A web round (cx, cy): eight spokes and a ring every 3 pixels."""
    dx, dy = x - cx, y - cy
    if abs(dx) < 0.6 or abs(dy) < 0.6 or abs(abs(dx) - abs(dy)) < 0.6:
        return True
    r = ring(x, y, cx, cy)
    return r > 1 and r % 3 == 0


def webbed(part, face, x, y, w, h, rng):
    """Web: a suit webbed all over in the secondary colour, and web gloves."""
    if is_arm(part):
        if face == "bottom" or (face != "top" and y >= 9):
            return S if not (face == "front" and x == w // 2) else (P, -20)  # the glove, a strand over its back
        if face == "top":
            return P
        return S if (y % 3 == 1 or x == w // 2) else P
    if is_leg(part):
        if face == "bottom":
            return S
        if face == "top":
            return P
        return S if (y % 3 == 1 or x == w // 2) else P
    if face in ("front", "back"):
        return S if web_lines(x, y, 3.5, 3.5) else P
    if face in ("left", "right"):
        return S if y % 3 == 0 else P
    return P


def windswept(part, face, x, y, w, h, rng):
    """Wind: gusts swept back across the suit, wrapped forearms and a sash."""
    if is_arm(part):
        if face == "bottom" or (face != "top" and y >= 8 and (y - x) % 2 == 0):
            return S  # wraps
        if face != "top" and (x + y) % 6 == 0:
            return S
        return P
    if is_leg(part):
        if face == "bottom" or (face != "top" and y >= 10):
            return S
        if face != "top" and (x + y) % 6 == 0:
            return S
        return P
    if face != "top" and y in (9, 10):
        return S  # the sash
    if face in ("front", "back", "left", "right") and (x + y) % 6 == 0:
        return S  # gusts
    return P


def dense(part, face, x, y, w, h, rng):
    """Gravity: heavy plates over a dark undersuit, a dark core in the chest ringed in the primary."""
    if is_arm(part) or is_leg(part):
        if face in ("top", "bottom"):
            return S
        if y % 4 == 3:
            return (P, -110)  # the seams between plates, the dark undersuit showing
        return (S, -10)
    if face == "front":
        r = ((x - 3.5) ** 2 + (y - 4.5) ** 2) ** 0.5
        if r < 1.6:
            return (S, -175)  # the core: almost black
        if r < 2.8:
            return (P, -50)
    if face != "top" and face != "bottom" and y % 4 == 3:
        return (P, -110)
    return (S, -10)


def clockwork(part, face, x, y, w, h, rng):
    """Chrono: clockwork trim (gear-toothed hems), a clock face on the chest and a gear on the back."""
    if is_arm(part) or is_leg(part):
        if face == "bottom" or (face != "top" and y >= 10):
            return S
        if face != "top" and y == 9 and x % 2 == 0:
            return S  # the teeth
        return P
    if face == "front":
        r = ((x - 3.5) ** 2 + (y - 4.5) ** 2) ** 0.5
        if 2.2 <= r < 3.2:
            return S  # the dial's rim
        if (x == 3 and 2 <= y <= 4) or (y == 4 and 3 <= x <= 5):
            return (S, -30)  # the hands, at a quarter past
        if y == 11 or (y == 10 and x % 2 == 0):
            return S
        return P
    if face == "back":
        r = ((x - 3.5) ** 2 + (y - 4.5) ** 2) ** 0.5
        if r < 1.2 or (2.0 <= r < 3.0 and (x + y) % 2 == 0):
            return S  # a gear
    if face != "top" and (y == 11 or (y == 10 and x % 2 == 0)):
        return S
    return P


def bandolier(part, face, x, y, w, h, rng):
    """Arsenal: a harness of crossed bandoliers with pouches, a belt, bracers, a thigh holster and boots."""
    if is_arm(part):
        if face != "top" and 8 <= y <= 10:
            return S  # bracers
        return P
    if is_leg(part):
        if face == "bottom" or (face != "top" and y >= 10):
            return S
        outer = (part == "right_leg" and face == "right") or (part == "left_leg" and face == "left")
        if outer and 1 <= y <= 5 and 0 < x < w - 1:
            return (S, -25)  # the holster
        if face != "top" and y == 2:
            return S  # its strap
        return P
    if face in ("front", "back") and y <= 9:
        a = round(y * 0.7)
        if x == a or x == 7 - a:
            return S
        if (x == a + 1 or x == 7 - a - 1) and y in (2, 5, 8):
            return (S, -30)  # cartridges
    if face != "top" and y == 10:
        return S  # the belt
    if face == "front" and y == 11 and x in (1, 2, 5, 6):
        return (S, -20)  # pouches
    if face == "top" and x in (1, 6):
        return S  # the straps over the shoulders
    return P


def wave(x, period=4):
    return (0, 1, 1, 0, -1, -1)[x % 6] if period == 6 else (0, 1, 0, -1)[x % 4]


def resonant(part, face, x, y, w, h, rng):
    """Resonance: rings of sound spreading from the chest, waves round the limbs."""
    if is_arm(part) or is_leg(part):
        if face == "bottom" or (face != "top" and y == 11):
            return S
        if face != "top" and (y + wave(x)) in (3, 7):
            return S
        return P
    if face == "front":
        r = ring(x, y, 3.5, 4.5)
        return S if r in (1, 3, 5) else P
    if face == "back":
        return S if ring(x, y, 3.5, 4.5) in (2, 5) else P
    if face in ("left", "right") and (y + wave(x)) % 4 == 0:
        return S
    return P


def tidal(part, face, x, y, w, h, rng):
    """Darktide: waves across a suit that darkens towards the feet, deep water below the knee."""
    depth = -(y * 4) if face != "top" else 0
    if is_leg(part):
        if face == "bottom" or (face != "top" and y >= 7):
            return (S, -50 - (y - 7) * 6)
        depth -= 50
    if face != "top" and face != "bottom" and (y + wave(x, 6)) % 5 == 0:
        return (S, depth)
    if is_arm(part) and (face == "bottom" or y >= 10):
        return (S, depth)
    return (P, depth)


def ash_wraps(part, face, x, y, w, h, rng):
    """Smoke: strips of ash-grey cloth wound round and round, gaps between them on the limbs, ragged at the ends."""
    band = (y + x // 4) % 3
    soot = -hash_of(part, face, x, y) % 25
    if is_arm(part) or is_leg(part):
        if face == "bottom":
            return None
        if face != "top" and (y + x // 2) % 5 == 4 and y >= 4:
            return None  # a gap between the wraps
        if is_arm(part) and face != "top" and y == 11 and x % 2 == 1:
            return None  # loose ends at the fingers
    if face == "top":
        return (P, soot)
    return (S, soot - 10) if band == 2 else (P, soot)


def gi(part, face, x, y, w, h, rng):
    """Z Fighter: a training gi, its top crossed over at the chest, a sash with hanging ends, wristbands and boots."""
    if is_arm(part):
        if face == "bottom" or (face != "top" and y == 11):
            return None  # bare hands
        if face != "top" and 8 <= y <= 10:
            return S  # wristbands
        if face != "top" and y >= 5:
            return None  # bare forearms
        return P
    if is_leg(part):
        if face == "bottom" or (face != "top" and y >= 9):
            return S  # boots
        return P
    if face == "front":
        if y < 6:
            left, right = 1 + round(y * 0.5), 6 - round(y * 0.5)
            if left < x < right:
                return None  # the V of the neck
            if x == left or x == right:
                return (P, -25)  # the collar
        if y in (8, 9):
            return S  # the sash
        if y in (10, 11) and x in (5, 6):
            return S  # its ends
        return P
    if face != "top" and y in (8, 9):
        return S
    return P


def carapace(part, face, x, y, w, h, rng):
    """Xenoform: a segmented shell, ridged and darker between the segments, a spine down the back."""
    if face == "back" and part == "body" and x in (3, 4):
        return (S, -15 if y % 2 else 0)
    if face in ("top", "bottom"):
        return S
    if y % 3 == 2:
        return (S, -60)
    edge = x == 0 or x == w - 1
    return (P, -25 if edge else 0)


def assassin(part, face, x, y, w, h, rng):
    """Assassin (0.14.3): a fitted robe crossed at the chest with a trimmed lapel, a sash and buckle at the waist,
    leather-dark forearms under the bracers, boots; the coat tails, bracers and mantle are separate boxes (ASSASSIN_BOXES)."""
    if is_arm(part):
        if face == "top":
            return P
        if y == 11 or face == "bottom":
            return None  # bare hands
        if y >= 7:
            return (S, -45)  # the forearm under the bracer
        return P
    if is_leg(part):
        if face == "bottom" or (face != "top" and y >= 9):
            return (S, -70)  # boots
        if face != "top" and y == 8:
            return S  # their cuffs
        return P
    if face == "top":
        return P
    if face in ("front", "back", "left", "right") and y in (8, 9):
        if face == "front" and x in (3, 4):
            return (S, -45) if y == 8 else (S, -20)  # the buckle
        return S
    if face == "front" and y < 8:
        left, right = 1 + y // 2, 6 - y // 2
        if x == left or x == right:
            return S  # the lapel's trim
        if left < x < right:
            return (P, -45)  # the shirt under the robe
        return P
    if face == "back" and y < 8 and x == 3:
        return (P, -30)  # the seam
    if face in ("left", "right") and y == 7:
        return (P, -25)
    return P


def assassin_tail(part, face, x, y, w, h, rng):
    """The assassin's coat tails: split up the middle, a trimmed hem, the inner edges in shade."""
    if face in ("front", "back"):
        split = 1 if part == "tail_front" else 3
        if x in (3, 4) and y >= split:
            return None
        if y == 0:
            return S  # under the sash
        if y >= h - 2:
            return S
        if x in (2, 5) and y >= split:
            return (P, -35)
        return P
    if face in ("top", "bottom"):
        return (P, -40)
    return S if y >= h - 2 else (P, -20)


def assassin_bracer(part, face, x, y, w, h, rng):
    """Leather bracers in the secondary colour's shade, strapped top and bottom."""
    if face in ("top", "bottom") or y in (0, h - 1):
        return (S, -105)
    return (S, -70)


def assassin_pad(part, face, x, y, w, h, rng):
    """The mantle's upper layer in the primary with a trimmed edge; the lower layer in the secondary, hem in shade."""
    if part == "pad":
        return S if (face not in ("top", "bottom") and y == h - 1) else P
    return (S, -60) if (face not in ("top", "bottom") and y == h - 1) else (S, -10)


# (0.14.3) the assassin's extra boxes: (name, texture u, v, w, h, d, pattern), per slim; CostumeModels.assassin
def assassin_boxes(slim):
    aw = 3 if slim else 4
    return [("pad", 32, 0, 5, 3, 5, assassin_pad), ("mantle", 32, 8, 5, 2, 5, assassin_pad),
            ("bracer", 40, 32, aw, 4, 4, assassin_bracer), ("bracer", 48, 48, aw, 4, 4, assassin_bracer),
            ("tail_front", 0, 32, 8, 11, 1, assassin_tail), ("tail_back", 20, 32, 8, 11, 1, assassin_tail)]


CUTS = {"bodysuit": bodysuit, "armoured": armoured, "robes": robes, "coat": coat, "ragged": ragged,
        "webbed": webbed, "windswept": windswept, "dense": dense, "clockwork": clockwork, "bandolier": bandolier,
        "resonant": resonant, "tidal": tidal, "ash_wraps": ash_wraps, "gi": gi, "carapace": carapace,
        "assassin": assassin}


def suits():
    for name, pattern in CUTS.items():
        for slim in (False, True):
            rng = random.Random(name)
            primary = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
            secondary = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
            for part, (u, v, w, h, d) in suit_boxes(slim).items():
                paint_box(primary, secondary, u, v, w, h, d, part, pattern, rng)
            if name in ("armoured", "dense"):
                # the shoulder pads (CostumeModels: texOffs 32,0, 5x3x5): secondary plates, a primary rim
                for face, (fx, fy, fw, fh) in box_faces(32, 0, 5, 3, 5).items():
                    for y in range(fh):
                        for x in range(fw):
                            rim = face != "top" and face != "bottom" and y == fh - 1
                            img = primary if rim else secondary
                            shade = cloth_shade(x, y, fw, fh, rng) - 10
                            img.putpixel((fx + x, fy + y), (shade, shade, shade, 255))
            if name == "assassin":
                for part, u, v, w, h, d, extra in assassin_boxes(slim):
                    paint_box(primary, secondary, u, v, w, h, d, part, extra, rng)
            tag = "_slim" if slim else ""
            save(primary, os.path.join(COSTUME, "suit", name + tag + "_primary.png"))
            save(secondary, os.path.join(COSTUME, "suit", name + tag + "_secondary.png"))


# ---- masks --------------------------------------------------------------------------------------------------------
# The head box at 0,0 (8x8x8) in a 64x32 texture; the helmet's crest (1x2x9) at 32,0. On the front face the eyes are
# on row 4 (the skin's usual eye row), columns 1-2 and 5-6.

EYES = {(1, 4), (2, 4), (5, 4), (6, 4)}


def half_mask(face, x, y):
    if face == "front":
        if (x, y) in EYES:
            return None
        if 3 <= y <= 5:
            return S if (y in (3, 5) and x in (1, 2, 5, 6)) else P
        return None
    if face in ("left", "right", "back") and y == 4:
        return P  # the strap
    return None


def full_mask(face, x, y):
    if face == "front" and (x, y) in EYES:
        return S
    if face == "front" and y == 6 and 2 <= x <= 5:
        return S
    if face == "back" and x in (3, 4):
        return S  # the lacing
    if face == "bottom":
        return None
    return P


def hood(face, x, y):
    if face == "front":
        if x == 0 or x == 7 or y == 0:
            return S  # the hood's edge
        return None
    if face == "bottom":
        return None
    if face == "back" and y >= 6:
        return S
    return P


def helmet(face, x, y):
    if face == "front":
        if y in (3, 4) and 1 <= x <= 6 or y in (5, 6) and x in (3, 4):
            return S  # the visor
        return P
    if face == "bottom":
        return None
    if face in ("left", "right") and y == 7:
        return S
    return P


def goggles(face, x, y):
    if face == "front":
        if y in (3, 4, 5) and x in (1, 2, 3, 4, 5, 6):
            lens = y == 4 and x in (1, 2, 5, 6)
            return S if lens else P
        return None
    if face in ("left", "right", "back") and y in (3, 4):
        return P
    return None


def web_mask(face, x, y):
    """Web (0.11.78): a full mask webbed in the secondary, with big pale lenses."""
    if face == "bottom":
        return None
    if face == "front":
        if (x, y) in {(1, 3), (2, 3), (1, 4), (2, 4), (5, 3), (6, 3), (5, 4), (6, 4), (2, 5), (5, 5)}:
            return (S, 15)  # the lenses
        return (S, -60) if web_lines(x, y, 3.5, 3.5) else P
    if face == "top":
        return (S, -60) if web_lines(x, y, 3.5, 3.5) else P
    return (S, -60) if (y % 3 == 0 or x == 3) else P


def ash_veil(face, x, y):
    """Smoke (0.11.78): wraps round the whole head, a slit left for the eyes."""
    if face == "bottom":
        return None
    if face == "front" and y == 4 and 1 <= x <= 6:
        return None  # the eye slit
    if (y + x // 4) % 3 == 2:
        return (S, -10)
    return P


def headband(face, x, y):
    """Z Fighter (0.11.78): a band round the brow, knotted at the back, its tails hanging."""
    if face in ("top", "bottom"):
        return None
    if y in (1, 2):
        return S if (face == "front" and 3 <= x <= 4) else P  # a plate on the brow
    if face == "back" and 3 <= y <= 6 and x in (3, 5):
        return P  # the tails
    if face == "back" and y == 3 and x == 4:
        return S  # the knot
    return None


def peaked_hood(face, x, y):
    """Assassin (0.14.3): a deep hood, the face left open but shaded under the peak; lined at the cheeks."""
    if face == "bottom":
        return None
    if face == "front":
        if y == 0:
            return P
        if y == 1:
            return (P, -70)
        if y == 2 and 1 <= x <= 6:
            return (P, -150)  # the shadow under the peak
        if x in (0, 7):
            return P
        return None
    if face in ("left", "right"):
        edge = (x == 0) if face == "left" else (x == 7)
        return (S, -30) if edge and y >= 2 else P
    if face == "back" and y == 7:
        return S
    return P


def peaked_brim(part, face, x, y, w, h, rng):
    """The peak (8x2x3 over the brow): a pointed beak, the underside as dark shade, a trimmed lip."""
    d = 3
    if part == "brim":
        if face == "front":
            if x in (0, 7) or (y == 1 and x in (1, 6)):
                return None  # the point
            return S if y == 1 else P
        if face == "top":
            if y == d - 1 and x in (0, 1, 6, 7) or y == d - 2 and x in (0, 7):
                return None
            return P
        if face == "bottom":
            return (P, -175)  # the underside, in shadow
        return (P, -30)
    # the cowl (8x2x6), falling onto the shoulders
    if face == "bottom":
        return (P, -120)
    if face in ("top",):
        return P
    return S if y == h - 1 else P


MASKS = {"half_mask": half_mask, "full_mask": full_mask, "hood": hood, "helmet": helmet, "goggles": goggles,
         "web_mask": web_mask, "ash_veil": ash_veil, "headband": headband,
         "peaked_hood": peaked_hood}


def masks():
    for name, pattern in MASKS.items():
        rng = random.Random(name)
        primary = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
        secondary = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
        for face, (fx, fy, fw, fh) in box_faces(0, 0, 8, 8, 8).items():
            for y in range(fh):
                for x in range(fw):
                    which = pattern(face, x, y)
                    if which is None:
                        continue
                    which, extra = which if isinstance(which, tuple) else (which, 0)
                    shade = max(0, min(255, cloth_shade(x, y, fw, fh, rng) + extra))
                    if name in ("helmet", "goggles") and which == S:
                        shade = min(255, shade + 10)  # polished
                    (primary if which == P else secondary).putpixel((fx + x, fy + y), (shade, shade, shade, 255))
        if name == "peaked_hood":
            for part, u, v, w, h, d in (("brim", 0, 16, 8, 2, 3), ("cowl", 24, 16, 8, 2, 6)):
                paint_box(primary, secondary, u, v, w, h, d, part, peaked_brim, rng)
        if name == "helmet":
            for face, (fx, fy, fw, fh) in box_faces(32, 0, 1, 2, 9).items():
                for y in range(fh):
                    for x in range(fw):
                        shade = cloth_shade(x, y, fw, fh, rng)
                        secondary.putpixel((fx + x, fy + y), (shade, shade, shade, 255))
        save(primary, os.path.join(COSTUME, "mask", name + "_primary.png"))
        save(secondary, os.path.join(COSTUME, "mask", name + "_secondary.png"))


# ---- capes --------------------------------------------------------------------------------------------------------
# One box (10 wide, `length` long, 1 deep) at 0,0 in a 64x32 texture: its north face is the outside (primary, with a
# secondary trim along the hem), its south face the lining (secondary).

CAPES = {"short": 11, "long": 20, "tattered": 19, "gale": 22, "tide": 20}
# (0.11.78) Wind's gale cloak: the hem split into three tails, the middle longest, swept with gusts; Darktide's tide
# cape: a wavy hem and waves breaking up its lower half. How many rows each column loses:
SHAPED = {"gale": [0, 1, 2, 8, 1, 0, 8, 2, 1, 0], "tide": [1, 2, 3, 2, 1, 0, 1, 2, 3, 2]}


def capes():
    for name, length in CAPES.items():
        rng = random.Random(name)
        primary = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
        secondary = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
        # tattered: a ragged hem (how many rows each column loses) and a few holes
        torn = SHAPED[name] if name in SHAPED else [rng.randint(0, 5) if name == "tattered" else 0 for _ in range(10)]
        holes = {(rng.randint(1, 8), rng.randint(6, length - 4)) for _ in range(5)} if name == "tattered" else set()
        for face, (fx, fy, fw, fh) in box_faces(0, 0, 10, length, 1).items():
            for y in range(fh):
                for x in range(fw):
                    which = P
                    if face in ("front", "back"):
                        col = x if face == "front" else 9 - x
                        if y >= length - torn[col] or (col, y) in holes:
                            continue
                        if face == "back":
                            which = S
                        elif y >= length - torn[col] - 1 and name != "tattered":
                            which = S
                        elif name == "gale" and (col + 2 * y) % 9 == 0:
                            which = S  # gusts
                        elif name == "tide" and y > length // 2 and (y + (0, 1, 1, 0, -1, -1)[col % 6]) % 4 == 0:
                            which = S  # waves
                    shade = cloth_shade(x, y, fw, fh, rng)
                    # folds: soft vertical shading
                    if face in ("front", "back"):
                        shade -= 8 if x % 3 == 1 else 0
                    (primary if which == P else secondary).putpixel((fx + x, fy + y), (shade, shade, shade, 255))
        save(primary, os.path.join(COSTUME, "cape", name + "_primary.png"))
        save(secondary, os.path.join(COSTUME, "cape", name + "_secondary.png"))


# ---- the table and the item ---------------------------------------------------------------------------------------

OAK = [(162, 130, 78), (150, 118, 70), (128, 98, 56), (184, 148, 92)]
DARK = (92, 68, 40)
CLOTH = [(150, 64, 120), (170, 80, 140), (128, 50, 102)]
THREADS = [(200, 60, 60), (60, 120, 200), (230, 200, 60), (70, 170, 90), (240, 240, 240)]


def planks(rng, size=16):
    im = Image.new("RGBA", (size, size))
    for y in range(size):
        for x in range(size):
            c = OAK[0] if (y // 4) % 2 == 0 else OAK[1]
            if y % 4 == 3:
                c = OAK[2]
            jitter = rng.randint(-6, 6)
            im.putpixel((x, y), tuple(max(0, min(255, v + jitter)) for v in c) + (255,))
    return im


def table_textures():
    rng = random.Random("costume_table")
    top = planks(rng)
    # a bolt of cloth across the top, a tape measure along it, and shears
    for y in range(3, 13):
        for x in range(1, 15):
            c = CLOTH[(x + y) % 2] if 4 <= y <= 11 else CLOTH[2]
            top.putpixel((x, y), c + (255,))
    for x in range(1, 15):
        top.putpixel((x, 13), (230, 200, 70, 255) if x % 2 else (40, 40, 40, 255))
    for (x, y) in [(10, 5), (11, 6), (12, 7), (12, 5), (11, 6), (10, 7), (9, 8), (13, 4), (9, 4), (13, 8)]:
        top.putpixel((x, y), (190, 195, 205, 255))
    for (x, y) in [(13, 4), (9, 8)]:
        top.putpixel((x, y), (60, 60, 70, 255))
    for x in range(16):
        top.putpixel((x, 0), DARK + (255,))
        top.putpixel((x, 15), DARK + (255,))
        top.putpixel((0, x), DARK + (255,))
        top.putpixel((15, x), DARK + (255,))

    def framed(rng):
        im = planks(rng)
        for i in range(16):
            for (x, y) in [(i, 0), (i, 15), (0, i), (15, i)]:
                im.putpixel((x, y), DARK + (255,))
        return im

    side = framed(rng)
    # a shelf of spools
    for x in range(1, 15):
        side.putpixel((x, 9), DARK + (255,))
    for i, c in enumerate(THREADS):
        sx = 2 + i * 3 - (1 if i > 3 else 0)
        for y in range(4, 9):
            for x in range(sx, sx + 2):
                end = y in (4, 8)
                side.putpixel((x, y), (OAK[3] if end else c) + (255,))
    front = framed(rng)
    # bolts of fabric hanging from a rail
    for x in range(1, 15):
        front.putpixel((x, 2), (120, 120, 130, 255))
    for i, c in enumerate(THREADS[:4]):
        sx = 2 + i * 3
        for y in range(3, 12 - (i % 2) * 2):
            for x in range(sx, sx + 3):
                shade = -18 if x == sx + 2 else 0
                front.putpixel((x, y), tuple(max(0, v + shade) for v in c) + (255,))
    bottom = planks(rng)
    tex = os.path.join(ASSETS, "textures", "block", "costume_table")
    for name, im in [("top", top), ("side", side), ("front", front), ("bottom", bottom)]:
        save(im, os.path.join(tex, name + ".png"))


def item_textures():
    """A folded costume: the cloth (tinted primary) and its trim, collar and belt (tinted secondary)."""
    cloth = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    trim = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rng = random.Random("costume_item")
    # a folded shirt seen from the front: shoulders, sleeves folded in, the body square
    for y in range(3, 14):
        for x in range(2, 14):
            if y < 5 and not (4 <= x <= 11):
                continue
            edge = x in (2, 13) or y in (3, 13)
            shade = (196 if edge else 236) + rng.randint(-6, 6)
            cloth.putpixel((x, y), (shade, shade, shade, 255))
    for (x, y) in [(6, 3), (7, 4), (8, 4), (9, 3), (7, 3), (8, 3)]:
        cloth.putpixel((x, y), (0, 0, 0, 0))
        trim.putpixel((x, y), (240, 240, 240, 255))
    for (x, y) in [(5, 3), (10, 3), (6, 4), (9, 4), (7, 5), (8, 5)]:
        trim.putpixel((x, y), (225, 225, 225, 255))
    for x in range(3, 13):
        trim.putpixel((x, 10), (232, 232, 232, 255))
        trim.putpixel((x, 11), (210, 210, 210, 255))
    for y in range(5, 13):
        trim.putpixel((2, y), (220, 220, 220, 255))
        trim.putpixel((13, y), (220, 220, 220, 255))
    tex = os.path.join(ASSETS, "textures", "item")
    save(cloth, os.path.join(tex, "costume.png"))
    save(trim, os.path.join(tex, "costume_trim.png"))


def json_files():
    write(os.path.join(ASSETS, "blockstates", "costume_table.json"),
          {"variants": {"": {"model": NS + ":block/costume_table"}}})
    t = NS + ":block/costume_table/"
    write(os.path.join(ASSETS, "models", "block", "costume_table.json"), {
        "parent": "minecraft:block/cube",
        "textures": {"particle": t + "front", "north": t + "front", "south": t + "front", "east": t + "side",
                     "west": t + "side", "up": t + "top", "down": t + "bottom"}})
    write(os.path.join(ASSETS, "items", "costume_table.json"),
          {"model": {"type": "minecraft:model", "model": NS + ":block/costume_table"}})
    write(os.path.join(ASSETS, "models", "item", "costume.json"), {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": NS + ":item/costume", "layer1": NS + ":item/costume_trim"}})
    write(os.path.join(ASSETS, "items", "costume.json"), {"model": {
        "type": "minecraft:model", "model": NS + ":item/costume",
        "tints": [{"type": "minecraft:custom_model_data", "index": 0, "default": 0x3C44AA},
                  {"type": "minecraft:custom_model_data", "index": 1, "default": 0xF9FFFE}]}})
    write(os.path.join(DATA, "recipe", "costume_table.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "key": {"W": "#minecraft:wool", "L": "minecraft:leather", "M": "minecraft:loom", "C": "minecraft:crafting_table"},
        "pattern": ["WLW", "LML", "WCW"],
        "result": {"id": NS + ":costume_table"}})
    write(os.path.join(DATA, "loot_table", "blocks", "costume_table.json"), {
        "type": "minecraft:block", "random_sequence": NS + ":blocks/costume_table",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": NS + ":costume_table"}],
                   "condition": {"type": "minecraft:survives_explosion"}}]})


if __name__ == "__main__":
    suits()
    masks()
    capes()
    table_textures()
    item_textures()
    json_files()
