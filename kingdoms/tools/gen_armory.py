"""Draws Jaymo's Kingdoms' armour sets (original art and shapes). Run from kingdoms/: python -I tools/gen_armory.py

Four sets, each with a flat layer drawn on the vanilla armour model (equipment textures) and raised 3D pieces drawn by
the client's armour extras layer (crests, pauldrons, horns, skirts):
  paladin   - a knight's harness: great helm with a gilt comb and a plume, round pauldrons, a tabard in the realm's colour
  gladiator - bronze arena kit: a broad-brimmed grille helm with a crest, a banded sword-arm, a belt and greaves
  ronin     - lacquered lamellar: a horned kabuto with a neck guard, hanging shoulder plates and a plated skirt
  warlord   - a raider chief's furs: a horned iron cap, a fur mantle and pelt, bone pauldron, a hide kilt

Writes, for each set:
  assets/jaymoskingdoms/armory/<set>.json                      the raised pieces (read by the client at startup)
  textures/entity/armory/<set>.png, <set>_cloth.png             their textures (cloth is greyscale: tinted by dye)
  textures/entity/equipment/humanoid[_leggings]/<set>*.png      the flat layers
  equipment/<set>.json                                          which flat layers are drawn, and which take dye
  textures/item/<set>_<piece>.png, items/, models/item/         the item sprites
"""
import json
import os
import random

from PIL import Image

ROOT = os.path.join("src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "jaymoskingdoms")
DATA = os.path.join(ROOT, "data", "jaymoskingdoms")
NS = "jaymoskingdoms"
PIECES = ["helmet", "chestplate", "leggings", "boots"]

# ---------------------------------------------------------------------------------------------------------------- colours


def rgb(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


MATS = {
    "steel": rgb(0xB4BAC4),
    "dark_steel": rgb(0x6C7280),
    "iron": rgb(0x7E7A78),
    "gold": rgb(0xE2B23C),
    "bronze": rgb(0xB87A3A),
    "leather": rgb(0x7A4E2E),
    "dark_leather": rgb(0x4E3220),
    "fur": rgb(0x7C6248),
    "pale_fur": rgb(0xB8A68A),
    "bone": rgb(0xE4D8B8),
    "lacquer": rgb(0x2A2628),
    "navy": rgb(0x2C3448),
    "linen": rgb(0xE6DFCC),
    "cloth": (226, 226, 226),
}

_rng = random.Random(1234)
NOISE = [[_rng.random() for _ in range(256)] for _ in range(256)]


def noise(x, y, salt=0):
    return NOISE[(y * 7 + salt * 13) % 256][(x * 11 + salt * 5) % 256]


METALS = ("steel", "dark_steel", "iron", "gold", "bronze", "lacquer")


def surface(mat, x, y, w, h, salt=0, edge=True):
    """A material's pixel at (x, y) of a w*h face: clean metal with a top-lit gradient, a sheen and a crisp rim; grained
    leather with stitched edges; folded cloth; streaked fur."""
    base = MATS[mat]
    n = noise(x, y, salt) - 0.5
    t = y / (h - 1) if h > 1 else 0.5
    if mat in ("fur", "pale_fur"):
        f = 0.78 + 0.45 * (n + 0.5)
        if (x + y * 3 + salt) % 5 == 0:
            f *= 0.78
        if edge and y == h - 1:
            f *= 0.8
        return shade(base, f) + (255,)
    if mat in METALS:
        f = 1.14 - 0.32 * t + 0.03 * n
        if w > 2:
            sheen = 1 - abs(x - (w - 1) * 0.3) / max(1.0, w * 0.22)
            f += 0.16 * max(0.0, sheen)
    elif mat in ("leather", "dark_leather"):
        f = 1.06 - 0.18 * t + 0.14 * n
        if edge and w > 3 and h > 3 and (y == 1 or y == h - 2) and x % 2 == 0 and 0 < x < w - 1:
            return shade(MATS["linen"], 0.8) + (255,)
    elif mat == "cloth":
        f = 0.97 - 0.1 * t + (0.06 if x % 3 == 0 else -0.08 if x % 3 == 2 else 0) + 0.03 * n
    elif mat == "bone":
        f = 1.08 - 0.22 * t + 0.05 * n - (0.1 if y % 3 == 2 else 0)
    else:
        f = 1.0 - 0.12 * t + 0.06 * n
    if edge:
        if h > 1 and y == 0:
            f *= 1.22
        elif h > 2 and y == h - 1:
            f *= 0.62
        if w > 2 and (x == 0 or x == w - 1):
            f *= 0.74
    return shade(base, f) + (255,)


def nub_pixel(mat, face):
    """A rivet or stud: one bright pixel on top, lit sides, a dark underside."""
    f = {"top": 1.28, "bottom": 0.55, "front": 1.08, "back": 0.8, "left": 0.92, "right": 0.92}[face]
    return shade(MATS[mat], f) + (255,)


# ---------------------------------------------------------------------------------------------------- box UV painting

FACES = ("top", "bottom", "right", "front", "left", "back")


def face_rects(u, v, w, h, d):
    """Where each face of a box sits in its texture (vanilla box UV): face -> (x, y, width, height)."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),  # the wearer's right (model -x)
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


def paint_box(img, u, v, w, h, d, fn):
    px = img.load()
    for face, (fx, fy, fw, fh) in face_rects(u, v, w, h, d).items():
        for y in range(fh):
            for x in range(fw):
                c = fn(face, x, y, fw, fh)
                if c is not None:
                    px[fx + x, fy + y] = c


# --------------------------------------------------------------------------------------------------------- the sets

# A raised piece: slot (head/chest/legs/feet), bone it rides (head/body/right_arm/left_arm/right_leg/left_leg), layer
# (main/cloth), pos, size, material, and optionally a pivot and rotation (radians) to stand it at an angle. Pieces on
# the right limbs are mirrored onto the left ones unless "single".


def P(slot, bone, pos, size, mat, layer="main", pivot=None, rot=None, deco=None, single=False, grow=0.0, nub=False):
    return dict(slot=slot, bone=bone, pos=list(pos), size=list(size), mat=mat, layer=layer, pivot=pivot, rot=rot,
                deco=deco, single=single, grow=grow, nub=nub)


def N(slot, bone, centre, mat="steel", r=0.3, single=False):
    """A rivet or stud: a small raised cube of half-size r centred on a point of a plate's surface."""
    cx, cy, cz = centre
    return P(slot, bone, (cx - 0.5, cy - 0.5, cz - 0.5), (1, 1, 1), mat, grow=r - 0.5, single=single, nub=True)


def ridge(slot, bone, pos, size, mat="steel", grow=-0.2, single=False):
    """A raised ridge, comb or band: a box shrunk a little so it stands proud of the plate it lies along."""
    return P(slot, bone, pos, size, mat, grow=grow, single=single)


SETS = {}

SETS["paladin"] = dict(
    cloth=0x3C44AA, sound="iron", durability=22, defense=(2, 6, 7, 3), enchant=10, toughness=1.0, knockback=0.05,
    repair="minecraft:repairs_iron_armor",
    pieces=[
        # the crown narrows in two steps to the comb, the plume rising from it and falling behind
        P("head", "head", (-4.5, -10, -4.5), (9, 1, 9), "steel", deco="gilt_bottom"),
        P("head", "head", (-3.5, -11, -3.5), (7, 1, 7), "steel"),
        P("head", "head", (-0.5, -12.5, -3), (1, 2, 6), "gold"),
        P("head", "head", (-1, -15.5, -2.5), (2, 3, 6), "cloth", layer="cloth"),
        P("head", "head", (-1, -14.5, 2.5), (2, 5, 2), "cloth", layer="cloth"),
        # a faceplate standing proud of the bowl: brow plate and bevor with the eye slit between, split by a nasal bar
        P("head", "head", (-5, -9.25, -5.8), (10, 4, 1), "steel", deco="gilt_bottom"),
        P("head", "head", (-5, -4, -5.8), (10, 5, 1), "steel", deco="breaths"),
        P("head", "head", (-0.5, -9.25, -6.3), (1, 10, 1), "gold", grow=-0.2),
        P("chest", "body", (-5.5, -1.5, -3.5), (11, 2, 7), "steel", deco="gilt_bottom"),
        P("chest", "body", (-3.5, 12, -3.4), (7, 5, 1), "cloth", layer="cloth", deco="hem"),
        P("chest", "body", (-3.5, 12, 2.4), (7, 5, 1), "cloth", layer="cloth", deco="hem"),
        P("chest", "right_arm", (-5, -4, -3.5), (6, 4, 7), "steel", deco="gilt_bottom"),
        P("chest", "right_arm", (-5, 0, -3), (5, 2, 6), "steel"),
        P("chest", "right_arm", (-4.6, 4, -1.5), (1, 3, 3), "steel", deco="rivet"),
        P("chest", "right_arm", (-4.5, 8, -3.5), (7, 2, 7), "steel"),
        P("legs", "right_leg", (-2, 4, -3.5), (4, 3, 1), "steel", deco="rivet"),
        P("legs", "right_leg", (-3, -1, -3.3), (6, 4, 1), "steel", deco="gilt_bottom"),
        P("feet", "right_leg", (-2.5, 11, -4.2), (5, 2, 1), "steel"),
        # rivets on the helm's brow and cheeks, and along the gorget
        *[N("head", "head", (sx * 4.2, y, -5.8), r=0.16) for sx in (-1, 1) for y in (-8.4, -0.4)],
        *[N("head", "head", (sx * 5, -1.6, z), r=0.16) for sx in (-1, 1) for z in (-2.6, 2.6)],
        *[N("chest", "body", (sx * 3.5, -0.5, -3.5), "gold") for sx in (-1, 1)],
        # the breastplate's flanks, a sword belt with a gilt buckle
        *[N("chest", "body", (sx * 4.4, y, -3)) for sx in (-1, 1) for y in (1.2, 6.5)],
        P("chest", "body", (-5.5, 10, -3.5), (11, 1, 7), "dark_leather", grow=0.05),
        N("chest", "body", (0, 10.5, -3.6), "gold", r=0.45),
        # pauldron: a comb along the top, rivets on the face and lame
        ridge("chest", "right_arm", (-3.5, -4.8, -3.5), (1, 1, 7), grow=-0.15),
        N("chest", "right_arm", (-5, -2.5, -2.4)), N("chest", "right_arm", (-5, -2.5, 2.4)),
        N("chest", "right_arm", (-5, 1, 0)),
        N("chest", "right_arm", (-4.6, 5.5, 0), r=0.35),
        N("chest", "right_arm", (-4.5, 9, -1.8)), N("chest", "right_arm", (-4.5, 9, 1.8)),
        # knee cop wing and boss, tasset rivets, shin ridge, toe boss
        P("legs", "right_leg", (-3.2, 4.5, -3), (1, 2, 2), "steel"),
        N("legs", "right_leg", (0, 5.5, -3.5), r=0.4),
        N("legs", "right_leg", (-2, 0, -3.3), "gold"), N("legs", "right_leg", (2, 0, -3.3), "gold"),
        ridge("feet", "right_leg", (-0.5, 4.5, -3.5), (1, 6, 1)),
        N("feet", "right_leg", (0, 12, -4.2)),
    ],
)

SETS["gladiator"] = dict(
    cloth=0xB02E26, sound="copper", durability=14, defense=(1, 4, 6, 3), enchant=14, toughness=0.0, knockback=0.0,
    repair="minecraft:repairs_copper_armor",
    pieces=[
        P("head", "head", (-6, -7, -6), (12, 1, 12), "bronze", deco="studs"),
        P("head", "head", (-1, -11, -5), (2, 2, 10), "bronze"),
        P("head", "head", (-1, -14, -5.5), (2, 3, 11), "cloth", layer="cloth", deco="bristle"),
        P("chest", "body", (-5.5, 9, -3.5), (11, 3, 7), "leather"),
        P("chest", "body", (-2.5, 12, -3.3), (5, 5, 1), "cloth", layer="cloth", deco="hem"),
        *[P("chest", "right_arm", (-4, -3 + 3 * k, -3), (6, 2, 6), "bronze" if k % 2 == 0 else "leather",
            single=True, grow=0.35, deco="studs") for k in range(4)],
        P("chest", "left_arm", (-1.5, -4, -3.5), (6, 2, 7), "bronze", single=True),
        P("chest", "left_arm", (3.5, -7, -3), (1, 3, 6), "bronze", single=True),
        P("feet", "right_leg", (-2.5, 3, -3.6), (5, 8, 1), "bronze", deco="ridge"),
        P("feet", "right_leg", (-3, 0, -3.9), (6, 3, 1), "bronze"),
        # studs round the brim, on the cheeks and the crest holder
        *[N("head", "head", (x, -6.5, -6), "gold") for x in (-4.5, -1.5, 1.5, 4.5)],
        *[N("head", "head", (sx * 6, -6.5, z), "gold") for sx in (-1, 1) for z in (-3, 0, 3)],
        *[N("head", "head", (sx * 4, -2, -5)) for sx in (-1, 1)],
        *[N("head", "head", (sx * 1, -10, z), "gold") for sx in (-1, 1) for z in (-3, 3)],
        ridge("head", "head", (-0.5, -6.5, -5.6), (1, 6, 1), "bronze"),
        # a row of studs along the belt, studs on each band of the sword arm and on the shoulder guard
        *[N("chest", "body", (x, 10.5, -3.5), "bronze" if abs(x) > 1 else "gold", r=0.35) for x in (-4.5, -3, -1.5, 0, 1.5, 3, 4.5)],
        *[N("chest", "right_arm", (-4.35, -2 + 3 * k, z), "gold", single=True) for k in range(4) for z in (-1.5, 1.5)],
        N("chest", "left_arm", (4.5, -3, -2), "gold", single=True), N("chest", "left_arm", (4.5, -3, 2), "gold", single=True),
        # greaves: a shin ridge and a studded knee guard
        ridge("feet", "right_leg", (-0.5, 3.5, -4.1), (1, 7, 1), "bronze"),
        # pteruges: leather strips hanging from the belt, in front of the loincloth, each tipped with a bronze stud
        *[P("chest", "body", (x, 12, -3.8), (1, 4, 1), "leather", grow=-0.05) for x in (-4.5, -3, 2, 3.5)],
        *[N("chest", "body", (x + 0.5, 15.3, -3.8), "bronze", r=0.25) for x in (-4.5, -3, 2, 3.5)],
        *[N("feet", "right_leg", (x, 1.5, -3.9), "gold") for x in (-2, 0, 2)],
    ],
)

SETS["ronin"] = dict(
    cloth=0x9E2B25, sound="chain", durability=18, defense=(2, 5, 6, 2), enchant=12, toughness=0.5, knockback=0.0,
    repair="minecraft:repairs_iron_armor",
    pieces=[
        P("head", "head", (-5.5, -4, 4.5), (11, 3, 1), "cloth", layer="cloth", deco="lames"),
        P("head", "head", (-5.5, -4, -3), (1, 3, 8), "cloth", layer="cloth", deco="lames"),
        P("head", "head", (4.5, -4, -3), (1, 3, 8), "cloth", layer="cloth", deco="lames"),
        P("head", "head", (-6, -1, 5), (12, 3, 1), "cloth", layer="cloth", deco="lames"),
        P("head", "head", (-6.5, -8, -4.5), (1, 4, 3), "lacquer", deco="gilt_bottom"),
        P("head", "head", (5.5, -8, -4.5), (1, 4, 3), "lacquer", deco="gilt_bottom"),
        P("head", "head", (-1, -10, -5.6), (2, 2, 1), "gold"),
        P("head", "head", (-0.5, -7, -0.5), (1, 7, 1), "gold", pivot=(-1, -9, -5.3), rot=(0, 0, -0.55)),
        P("head", "head", (-0.5, -7, -0.5), (1, 7, 1), "gold", pivot=(1, -9, -5.3), rot=(0, 0, 0.55)),
        P("chest", "right_arm", (-5.2, -3.5, -3.5), (1, 7, 7), "cloth", layer="cloth", deco="lames"),
        P("chest", "right_arm", (-5, -4.2, -3.5), (5, 1, 7), "lacquer"),
        P("legs", "right_leg", (-3.4, -1, -2.5), (1, 6, 5), "cloth", layer="cloth", deco="lames"),
        P("legs", "right_leg", (-2.5, -1, 2.4), (5, 6, 1), "cloth", layer="cloth", deco="lames"),
        P("feet", "right_leg", (-2, 3, -3.6), (4, 8, 1), "lacquer"),
        # the kabuto's ribs over the crown, studded with gilt rivets (hoshi)
        *[ridge("head", "head", (x - 0.5, -9.6, -5), (1, 1, 10), "lacquer", grow=-0.22) for x in (-3, -1, 1, 3)],
        *[N("head", "head", (x, -9.3, z), "gold", r=0.22) for x in (-3, -1, 1, 3) for z in (-3, 0, 3)],
        *[N("head", "head", (sx * 6.5, -6, -3), "gold") for sx in (-1, 1)],
        # gilt rivets at the top of each shoulder plate
        N("chest", "right_arm", (-5.2, -2.8, -2.4), "gold"), N("chest", "right_arm", (-5.2, -2.8, 2.4), "gold"),
        # the do: four lacquered lames stepping out down the chest, laced with raised cords, a gilt mon at the top
        *[P("chest", "body", (-4.5, 0.5 + 3 * k, -3.5 - 0.15 * k), (9, 3, 1), "cloth", layer="cloth", deco="lame_edge") for k in range(4)],
        *[ridge("chest", "body", (x - 0.5, 0.5, -4.6), (1, 12, 1), "navy", grow=-0.32) for x in (-3, -1, 1, 3)],
        P("chest", "body", (-1, 1, -4.4), (2, 2, 1), "gold", deco="mon"),
        # the sode's lames raised and corded
        *[ridge("chest", "right_arm", (-5.6, -3 + 2 * k, -3.5), (1, 1, 7), "lacquer", grow=-0.2) for k in range(3)],
        *[ridge("chest", "right_arm", (-5.95, -3.5, z - 0.5), (1, 7, 1), "navy", grow=-0.32) for z in (-2, 0, 2)],
        # the kusazuri in three stepped lames, corded
        *[P("legs", "right_leg", (-2.6, -1 + 2 * k, -3.6 - 0.2 * k), (5, 2, 1), "cloth", layer="cloth", deco="lame_edge") for k in range(3)],
        *[ridge("legs", "right_leg", (x - 0.5, -1, -4.7), (1, 6, 1), "navy", grow=-0.32) for x in (-1.2, 1.2)],
        # three splints down each shin
        *[ridge("feet", "right_leg", (x, 3.5, -4.0), (1, 7, 1), "dark_steel", grow=-0.25) for x in (-1.75, -0.5, 0.75)],
    ],
)

def horns():
    """The warlord's horns: a root at each temple, a span curving out and up, an upright, and a tip (right side, then mirrored)."""
    right = [
        ((-1.5, -1, -1), (2, 2, 2), (-5, -7, 0), (0, 0, 0)),
        ((-3, -1, -1), (3, 2, 2), (-6.5, -7, 0), (0, 0, 0.4)),
        ((-1, -4, -0.5), (2, 4, 1), (-9.0, -7.9, 0), (0, 0, -0.3)),
        ((-0.5, -2, -0.5), (1, 2, 1), (-10.0, -11.4, 0), (0, 0, -0.6)),
    ]
    out = []
    for pos, size, pivot, rot in right:
        out.append(P("head", "head", pos, size, "bone", pivot=pivot, rot=rot))
        out.append(P("head", "head", (-(pos[0] + size[0]), pos[1], pos[2]), size, "bone", pivot=(-pivot[0], pivot[1], pivot[2]),
                     rot=(rot[0], -rot[1], -rot[2])))
    return out


SETS["warlord"] = dict(
    cloth=0x6E4A2C, sound="leather", durability=16, defense=(2, 5, 6, 3), enchant=12, toughness=0.0, knockback=0.1,
    repair="minecraft:repairs_leather_armor",
    pieces=[
        P("head", "head", (-0.5, -6, -5.6), (1, 4, 1), "iron"),
        *horns(),
        P("chest", "body", (-6, -2, -3.5), (12, 3, 7), "fur"),
        P("chest", "body", (-5, 1, 3.1), (10, 11, 1), "fur", deco="ragged"),
        P("chest", "right_arm", (-5, -4, -3.5), (5, 3, 7), "bone", single=True),
        P("chest", "right_arm", (-4, -6, -2), (1, 2, 1), "bone", single=True),
        P("chest", "right_arm", (-4, -6, 1), (1, 2, 1), "bone", single=True),
        P("chest", "left_arm", (-1, -3, -3.5), (5, 2, 7), "fur", single=True),
        P("chest", "right_arm", (-4.5, 5, -3.5), (7, 4, 7), "leather"),
        *[N("chest", "right_arm", (-4.5, y, z), "iron", r=0.35) for y in (6, 8) for z in (-2, 0, 2)],
        P("legs", "right_leg", (-2.5, -1, -3.2), (5, 6, 1), "cloth", layer="cloth", deco="ragged"),
        P("legs", "right_leg", (-3.2, -1, -2.5), (1, 6, 5), "cloth", layer="cloth", deco="ragged"),
        P("legs", "right_leg", (-2.5, -1, 2.2), (5, 6, 1), "cloth", layer="cloth", deco="ragged"),
        P("feet", "right_leg", (-3.5, 7, -3.5), (7, 2, 7), "fur"),
        # the cap's crossed bands and riveted brow band
        ridge("head", "head", (-0.5, -9.6, -5), (1, 1, 10), "dark_steel", grow=-0.15),
        ridge("head", "head", (-5, -9.6, -0.5), (10, 1, 1), "dark_steel", grow=-0.15),
        ridge("head", "head", (-5, -5.5, -5.4), (10, 1, 1), "dark_steel", grow=-0.1),
        ridge("head", "head", (-5, -5.5, 4.4), (10, 1, 1), "dark_steel", grow=-0.1),
        ridge("head", "head", (-5.4, -5.5, -5), (1, 1, 10), "dark_steel", grow=-0.1),
        ridge("head", "head", (4.4, -5.5, -5), (1, 1, 10), "dark_steel", grow=-0.1),
        *[N("head", "head", (x, -5, -5.5), "iron") for x in (-3.5, -1.8, 1.8, 3.5)],
        N("head", "head", (0, -9.4, 0), "iron", r=0.45),
        # the cuirass in layers: a cloth tunic hanging below, three rows of overlapping hide scales, a sash across
        P("chest", "body", (-4.5, 10, -3.35), (9, 5, 1), "cloth", layer="cloth", deco="ragged"),
        *[P("chest", "body", (-4.5, 0.5 + 3 * k, -3.45 - 0.15 * k), (9, 3, 1), "leather", deco="scales") for k in range(3)],
        *[P("chest", "body", (-4.5, 0.5 + 3 * k, 2.45 + 0.15 * k), (9, 3, 1), "leather", deco="scales") for k in range(3)],
        P("chest", "body", (-1, -6.5, -4.1), (2, 13, 1), "cloth", layer="cloth", pivot=(0, 5.5, 0), rot=(0, 0, 0.6)),
        # a broad belt with a bone buckle
        P("chest", "body", (-5.5, 10, -3.5), (11, 2, 7), "dark_leather", grow=0.05),
        N("chest", "body", (0, 11, -3.7), "bone", r=0.6),
    ],
)


def expand(pieces):
    """Mirrors right-limb pieces onto the left limbs."""
    out = []
    for p in pieces:
        out.append(p)
        if p["single"] or p["bone"] not in ("right_arm", "right_leg"):
            continue
        q = dict(p)
        q["bone"] = p["bone"].replace("right", "left")
        x, y, z = p["pos"]
        w = p["size"][0]
        q["pos"] = [-(x + w), y, z]
        if p["pivot"]:
            px_, py_, pz_ = p["pivot"]
            q["pivot"] = [-px_, py_, pz_]
        if p["rot"]:
            rx, ry, rz = p["rot"]
            q["rot"] = [rx, -ry, -rz]
        q["mirrored"] = True
        out.append(q)
    return out


def deco_pixel(p, face, x, y, fw, fh, c):
    """Small details on a raised piece: rivets, gilt edges, lacing, studs."""
    deco = p["deco"]
    if deco is None or c is None:
        return c
    if deco == "rivet" and face in ("front", "left", "right") and (x, y) == (fw // 2, fh // 2):
        return shade(c[:3], 1.35) + (255,)
    if deco == "gilt_bottom" and face not in ("top", "bottom") and y == fh - 1:
        return surface("gold", x, y, fw, fh, edge=False)
    if deco == "ridge" and face == "front" and x == fw // 2:
        return shade(c[:3], 1.2) + (255,)
    if deco == "studs" and face not in ("top", "bottom") and x % 2 == 1 and y == fh // 2:
        return surface("bronze" if p["mat"] != "bronze" else "gold", x, y, fw, fh, edge=False)
    if deco == "studs" and face in ("top", "bottom") and (x + y) % 3 == 0 and 0 < x < fw - 1 and 0 < y < fh - 1:
        return surface("gold", x, y, fw, fh, edge=False)
    if deco == "lames" and face not in ("top", "bottom"):
        if y % 2 == 1:
            return shade(c[:3], 0.7) + (255,)
        if x % 3 == 1:
            return shade(c[:3], 1.15) + (255,)
    if deco == "hem" and face not in ("top", "bottom") and y == fh - 1:
        return shade(c[:3], 0.7) + (255,)
    if deco == "bristle" and face in ("left", "right", "front", "back") and x % 2 == 0:
        return shade(c[:3], 0.82) + (255,)
    if deco == "splints" and face == "front" and x % 2 == 1:
        return surface("dark_steel", x, y, fw, fh, edge=False)
    if deco == "breaths" and face == "front" and y in (1, 3) and x in (6, 8) :
        return (22, 22, 26, 255)
    if deco == "breaths" and face == "front" and y == 2 and x == 7:
        return (22, 22, 26, 255)
    if deco == "lame_edge" and face not in ("top", "bottom"):
        if y == fh - 1:
            return surface("gold", x, y, fw, fh, edge=False)
        if y == 0:
            return shade(c[:3], 1.25) + (255,)
    if deco == "mon" and face == "front":
        return surface("gold", x, y, fw, fh, edge=False) if (x + y) % 2 == 0 else shade(MATS["gold"], 0.6) + (255,)
    if deco == "scales" and face == "front":
        if y == fh - 1 or (y == fh - 2 and x % 3 == 1):
            return shade(c[:3], 0.62) + (255,)
        if x % 3 == 0:
            return shade(c[:3], 0.82) + (255,)
        if x % 3 == 1 and y < fh - 2:
            return surface("iron", x, y, fw, fh, edge=False)
    if deco == "ragged" and face in ("front", "back", "left", "right") and y == fh - 1 and (x * 7) % 3 == 0:
        return None
    return c


def pack(pieces, tex_w):
    """Shelf-packs each piece's box UV into the texture; returns the texture height needed."""
    x = y = shelf = 0
    shared = {}
    for p in pieces:
        if p["nub"]:
            key = (p["mat"], p["layer"])
            if key in shared:
                p["uv"] = shared[key]
                continue
        w, h, d = p["size"]
        bw, bh = 2 * d + 2 * w, d + h
        if x + bw > tex_w:
            x, y, shelf = 0, y + shelf, 0
        p["uv"] = [x, y]
        if p["nub"]:
            shared[(p["mat"], p["layer"])] = p["uv"]
        x += bw
        shelf = max(shelf, bh)
    height = y + shelf
    return 1 << max(5, (height - 1).bit_length())


def write_extras(name, spec):
    pieces = expand(spec["pieces"])
    out = []
    for layer in ("main", "cloth"):
        group = [p for p in pieces if p["layer"] == layer]
        tex_w = 64
        tex_h = pack(group, tex_w) if group else 32
        img = Image.new("RGBA", (tex_w, tex_h), (0, 0, 0, 0))
        for i, p in enumerate(group):
            w, h, d = p["size"]

            def fn(face, x, y, fw, fh, p=p, i=i):
                if p["nub"]:
                    return nub_pixel(p["mat"], face)
                c = surface(p["mat"], x, y, fw, fh, salt=i)
                return deco_pixel(p, face, x, y, fw, fh, c)

            paint_box(img, p["uv"][0], p["uv"][1], w, h, d, fn)
        tex_dir = os.path.join(ASSETS, "textures", "entity", "armory")
        os.makedirs(tex_dir, exist_ok=True)
        img.save(os.path.join(tex_dir, name + ("_cloth" if layer == "cloth" else "") + ".png"))
        for p in group:
            entry = dict(slot=p["slot"], bone=p["bone"], layer=layer, pos=p["pos"], size=p["size"], uv=p["uv"])
            if p["pivot"]:
                entry["pivot"] = p["pivot"]
            if p["rot"]:
                entry["rot"] = p["rot"]
            if p["grow"]:
                entry["grow"] = p["grow"]
            out.append(entry)
        if layer == "main":
            main_size = [tex_w, tex_h]
        else:
            cloth_size = [tex_w, tex_h]
    model_dir = os.path.join(ASSETS, "armory")
    os.makedirs(model_dir, exist_ok=True)
    with open(os.path.join(model_dir, name + ".json"), "w") as f:
        json.dump(dict(texture_size=main_size, cloth_texture_size=cloth_size, cloth_color=spec["cloth"], pieces=out),
                  f, indent=1)


# ------------------------------------------------------------------------------------------------------ flat layers
# Vanilla armour UVs (64x32): head (0,0, 8x8x8), body (16,16, 8x12x4), right arm (40,16, 4x12x4), right leg (0,16,
# 4x12x4); the left limbs reuse the right ones' texture. The humanoid layer is drawn on the helmet, chestplate and boots;
# the leggings layer on the leggings (only its body waist and legs).

HEAD, BODY, ARM, LEG = (0, 0, 8, 8, 8), (16, 16, 8, 12, 4), (40, 16, 4, 12, 4), (0, 16, 4, 12, 4)


def blank():
    return Image.new("RGBA", (64, 32), (0, 0, 0, 0))


def flat(name, humanoid, leggings, extra_layers=()):
    """Saves the flat layers and the equipment asset that names them (extra layers: (suffix, image, dye colour or None))."""
    for kind, img in (("humanoid", humanoid), ("humanoid_leggings", leggings)):
        d = os.path.join(ASSETS, "textures", "entity", "equipment", kind)
        os.makedirs(d, exist_ok=True)
        img.save(os.path.join(d, name + ".png"))
    layers = {"humanoid": [{"texture": f"{NS}:{name}"}], "humanoid_leggings": [{"texture": f"{NS}:{name}"}]}
    for suffix, kind, img, dye in extra_layers:
        d = os.path.join(ASSETS, "textures", "entity", "equipment", kind)
        img.save(os.path.join(d, f"{name}_{suffix}.png"))
        layer = {"texture": f"{NS}:{name}_{suffix}"}
        if dye is not None:
            layer["dyeable"] = {"color_when_undyed": dye}
        layers[kind].append(layer)
    d = os.path.join(ASSETS, "equipment")
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, name + ".json"), "w") as f:
        json.dump({"layers": layers}, f, indent=2)


def box(img, b, fn):
    """Paints a flat-layer box, giving every side face a crisp rim: a lit top row, darker sides and a dark bottom row."""

    def rimmed(face, x, y, w, h):
        c = fn(face, x, y, w, h)
        if c is None or face in ("top", "bottom"):
            return c
        f = 1.0
        if y == 0:
            f = 1.15
        elif y == h - 1:
            f = 0.68
        if x == 0 or x == w - 1:
            f *= 0.86
        return shade(c[:3], f) + (c[3],)

    paint_box(img, *b, rimmed)


def paladin_flat():
    hum, leg = blank(), blank()

    def helm(face, x, y, w, h):
        c = surface("steel", x, y, w, h, edge=False)
        if face == "front":
            if y == 3:
                return None  # the eye slit is cut through: the face shows behind the faceplate's gap
            if x in (3, 4) and y < 7:
                return surface("gold", x, y, w, h, edge=False)
        if face in ("front", "left", "right", "back") and y == h - 1:
            return surface("gold", x, y, w, h, edge=False)
        if face in ("front", "left", "right", "back") and y == 0:
            return shade(c[:3], 1.15) + (255,)
        return c

    box(hum, HEAD, helm)

    def chest(face, x, y, w, h):
        if face in ("top", "bottom"):
            return surface("steel", x, y, w, h)
        c = surface("steel", x, y, w, h, edge=False)
        if face == "front" and x in (3, 4):
            c = shade(c[:3], 1.12) + (255,)
        if y == 0:
            return surface("gold", x, y, w, h, edge=False)
        if y in (8, 10):
            return shade(c[:3], 0.7) + (255,)
        return c

    box(hum, BODY, chest)

    def arm(face, x, y, w, h):
        c = surface("steel", x, y, w, h, edge=False)
        if y in (3, 6):
            return shade(c[:3], 0.7) + (255,)
        if y >= 9:
            return surface("dark_steel", x, y, w, h, edge=False)
        return c

    box(hum, ARM, arm)

    def boots(face, x, y, w, h):
        if face in ("top",):
            return None
        if face == "bottom" or y >= 8:
            c = surface("steel", x, y, w, h, edge=False)
            return shade(c[:3], 0.75) + (255,) if y in (8, 10) else c
        return None

    box(hum, LEG, boots)

    def waist(face, x, y, w, h):
        if face in ("top", "bottom"):
            return None
        if y >= 8:
            c = surface("steel", x, y, w, h, edge=False)
            return shade(c[:3], 0.72) + (255,) if y in (9, 11) else c
        return None

    box(leg, BODY, waist)

    def cuisses(face, x, y, w, h):
        c = surface("steel", x, y, w, h, edge=False)
        if y in (4, 5) and face == "front":
            return shade(c[:3], 1.2) + (255,)
        if y in (3, 6):
            return shade(c[:3], 0.72) + (255,)
        return c

    box(leg, LEG, cuisses)

    # the tabard (dyed) over the breastplate, and its gilt cross (undyed)
    tabard, cross = blank(), blank()

    def tab(face, x, y, w, h):
        if face in ("front", "back") and 1 <= x <= 6 and y >= 1:
            c = surface("cloth", x, y, w, h, edge=False)
            return shade(c[:3], 0.8) + (255,) if x in (1, 6) else c
        return None

    box(tabard, BODY, tab)

    def crs(face, x, y, w, h):
        if face in ("front", "back") and ((x in (3, 4) and 2 <= y <= 9) or (y in (4, 5) and 2 <= x <= 5)):
            return surface("gold", x, y, w, h, edge=False)
        return None

    box(cross, BODY, crs)
    flat("paladin", hum, leg, [("tabard", "humanoid", tabard, SETS["paladin"]["cloth"]),
                                ("cross", "humanoid", cross, None)])


def gladiator_flat():
    hum, leg = blank(), blank()

    def helm(face, x, y, w, h):
        c = surface("bronze", x, y, w, h, edge=False)
        if face == "front" and 2 <= y <= 6 and 1 <= x <= 6:
            return None if (x % 2 == 0 and y % 2 == 1) else shade(c[:3], 0.85) + (255,)
        if face in ("front", "left", "right", "back") and y == h - 1:
            return shade(c[:3], 0.7) + (255,)
        return c

    box(hum, HEAD, helm)

    def chest(face, x, y, w, h):
        # bare chest under a crossed leather harness
        if face == "front" or face == "back":
            if abs((x if face == "front" else w - 1 - x) - y * 0.6) < 1.0:
                return surface("leather", x, y, w, h, edge=False)
            if x in (1, 2) and y < 3:
                return surface("leather", x, y, w, h, edge=False)
        if face in ("left", "right") and y < 2:
            return surface("leather", x, y, w, h, edge=False)
        return None

    box(hum, BODY, chest)

    def arm(face, x, y, w, h):
        if y >= 9:
            c = surface("leather", x, y, w, h, edge=False)
            return shade(c[:3], 0.8) + (255,) if y == 10 else c
        return None

    box(hum, ARM, arm)

    def sandals(face, x, y, w, h):
        if face == "bottom":
            return surface("dark_leather", x, y, w, h, edge=False)
        if face != "top" and y >= 6 and (y % 2 == 0 or y == 11):
            return surface("leather", x, y, w, h, edge=False)
        return None

    box(hum, LEG, sandals)

    def waist(face, x, y, w, h):
        if face in ("top", "bottom"):
            return None
        if y >= 8:
            return surface("linen", x, y, w, h, edge=False)
        return None

    box(leg, BODY, waist)

    def wraps(face, x, y, w, h):
        if face == "top":
            return None
        if y <= 2:
            return surface("linen", x, y, w, h, edge=False)
        if 4 <= y <= 9 and (x + y) % 3 != 0:
            c = surface("linen", x, y, w, h, edge=False)
            return shade(c[:3], 0.9) + (255,)
        return None

    box(leg, LEG, wraps)
    flat("gladiator", hum, leg)


def ronin_flat():
    hum, leg = blank(), blank()

    def kabuto(face, x, y, w, h):
        c = surface("lacquer", x, y, w, h, edge=False)
        if face == "top":
            return surface("gold", x, y, w, h, edge=False) if x % 3 == 1 and y % 2 == 0 else c
        if face == "front":
            if y <= 1:
                return surface("gold", x, y, w, h, edge=False) if y == 1 else c
            if 5 <= y <= 7:
                return (60, 20, 18, 255) if not (y == 5 and 2 <= x <= 5) else (230, 230, 230, 255)
            return None
        if face in ("left", "right", "back") and x % 3 == 1 and y < 4:
            return surface("gold", x, y, w, h, edge=False)
        return c

    box(hum, HEAD, kabuto)

    def do(face, x, y, w, h):
        if face in ("top", "bottom"):
            return surface("lacquer", x, y, w, h)
        c = surface("lacquer", x, y, w, h, edge=False)
        return shade(c[:3], 1.6) + (255,) if y % 3 == 2 else c

    box(hum, BODY, do)

    def kote(face, x, y, w, h):
        if y >= 6:
            c = surface("dark_steel", x, y, w, h, edge=False)
            return shade(c[:3], 0.7) + (255,) if x % 2 else c
        return surface("navy", x, y, w, h, edge=False)

    box(hum, ARM, kote)

    def tabi(face, x, y, w, h):
        if face == "bottom":
            return surface("linen", x, y, w, h, edge=False)
        if face != "top" and y >= 7:
            return surface("navy", x, y, w, h, edge=False) if y < 11 else surface("linen", x, y, w, h, edge=False)
        return None

    box(hum, LEG, tabi)

    def waist(face, x, y, w, h):
        if face in ("top", "bottom"):
            return None
        return surface("navy", x, y, w, h, edge=False) if y >= 8 else None

    box(leg, BODY, waist)

    def hakama(face, x, y, w, h):
        c = surface("navy", x, y, w, h, edge=False)
        if face == "front" and 2 <= y <= 6:
            return shade(surface("dark_steel", x, y, w, h, edge=False)[:3], 0.8 if x % 2 else 1.0) + (255,)
        return c

    box(leg, LEG, hakama)

    # the do's lacing takes the set's dye, like its lames
    lacing = blank()

    def lace(face, x, y, w, h):
        if face in ("front", "back", "left", "right") and y % 3 != 2 and x % 2 == 0:
            return surface("cloth", x, y, w, h, edge=False)
        return None

    box(lacing, BODY, lace)
    flat("ronin", hum, leg, [("lacing", "humanoid", lacing, SETS["ronin"]["cloth"])])


def warlord_flat():
    hum, leg = blank(), blank()

    def cap(face, x, y, w, h):
        c = surface("iron", x, y, w, h, edge=False)
        if face == "top":
            return shade(c[:3], 0.8) + (255,) if x in (3, 4) or y in (3, 4) else c
        if face == "front":
            return c if y <= 2 else None
        if y == 4:
            return surface("pale_fur", x, y, w, h, edge=False)
        return c if y < 4 else surface("fur", x, y, w, h) if face == "back" else None

    box(hum, HEAD, cap)

    def jerkin(face, x, y, w, h):
        if face in ("top", "bottom"):
            return surface("leather", x, y, w, h)
        c = surface("leather", x, y, w, h, edge=False)
        if (x + (y // 2) % 2) % 2 == 0 and y % 2 == 0:
            return surface("iron", x, y, w, h, edge=False)
        if y == 10:
            return surface("dark_leather", x, y, w, h, edge=False)
        return c

    box(hum, BODY, jerkin)

    def arm(face, x, y, w, h):
        if y >= 7 and (y + x) % 3 != 0:
            return surface("dark_leather", x, y, w, h, edge=False)
        return None

    box(hum, ARM, arm)

    def boots(face, x, y, w, h):
        if face == "bottom":
            return surface("dark_leather", x, y, w, h, edge=False)
        if face != "top" and y >= 6:
            return surface("fur", x, y, w, h) if y < 10 else surface("dark_leather", x, y, w, h, edge=False)
        return None

    box(hum, LEG, boots)

    def waist(face, x, y, w, h):
        if face in ("top", "bottom"):
            return None
        return surface("dark_leather", x, y, w, h, edge=False) if y >= 8 else None

    box(leg, BODY, waist)

    def trousers(face, x, y, w, h):
        c = surface("dark_leather", x, y, w, h, edge=False)
        if face != "top" and (x + y) % 4 == 0 and y > 5:
            return surface("linen", x, y, w, h, edge=False)
        return c

    box(leg, LEG, trousers)
    flat("warlord", hum, leg)


# --------------------------------------------------------------------------------------------------------- item sprites

SHAPES = {
    "helmet": [
        "................",
        "................",
        "................",
        ".....######.....",
        "....########....",
        "...##########...",
        "...##########...",
        "...###....###...",
        "...##......##...",
        "...##......##...",
        "................",
    ],
    "chestplate": [
        "................",
        "..####....####..",
        ".######..######.",
        ".##############.",
        ".##############.",
        "..############..",
        "....########....",
        "....########....",
        "....########....",
        "....########....",
        "....########....",
        "....########....",
        ".....######.....",
    ],
    "leggings": [
        "................",
        "................",
        "...##########...",
        "...##########...",
        "...####..####...",
        "...###....###...",
        "...###....###...",
        "...###....###...",
        "...###....###...",
        "...###....###...",
        "...###....###...",
        "...###....###...",
        "...###....###...",
    ],
    "boots": [
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "...###....###...",
        "...###....###...",
        "...###....###...",
        "...###....###...",
        "..####....####..",
        ".#####....#####.",
        ".#####....#####.",
    ],
}

ITEM_MATS = {
    "paladin": ("steel", "gold"),
    "gladiator": ("bronze", "leather"),
    "ronin": ("lacquer", "gold"),
    "warlord": ("leather", "fur"),
}


def sprite(name, piece):
    spec = SETS[name]
    main, accent = ITEM_MATS[name]
    cloth = rgb(spec["cloth"])
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    rows = SHAPES[piece]
    filled = {(x, y + 1) for y, row in enumerate(rows) for x, ch in enumerate(row) if ch == "#"}
    for (x, y) in filled:
        c = surface(main, x, y, 16, 16, edge=False)
        if (x, y - 1) not in filled:
            c = shade(c[:3], 1.25) + (255,)
        if (x, y + 1) not in filled or (x + 1, y) not in filled:
            c = shade(c[:3], 0.7) + (255,)
        px[x, y] = c
    # an outline
    for (x, y) in list(filled):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y + dy)
            if q not in filled and 0 <= q[0] < 16 and 0 <= q[1] < 16 and px[q][3] == 0:
                px[q] = shade(MATS[main], 0.35) + (255,)
    acc = lambda x, y: surface(accent, x, y, 16, 16, edge=False)
    if piece == "helmet":
        if name == "paladin":
            for x in range(5, 11):
                px[x, 7] = (24, 24, 28, 255)
            for y in range(1, 4):
                for x in range(7, 10):
                    px[x, y] = shade(cloth, 0.9 + 0.1 * (x % 2)) + (255,)
            px[8, 4] = acc(8, 4)
        elif name == "gladiator":
            for x in range(1, 15):
                px[x, 7] = acc(x, 7) if x % 2 else shade(MATS["bronze"], 0.8) + (255,)
            for x in range(4, 13):
                px[x, 2] = shade(cloth, 0.95) + (255,)
                px[x, 3] = shade(cloth, 0.8) + (255,)
        elif name == "ronin":
            for i in range(4):
                px[4 - i, 3 - i] = acc(0, 0)
                px[11 + i, 3 - i] = acc(0, 0)
            px[7, 4] = px[8, 4] = acc(0, 0)
            for x in range(3, 13):
                px[x, 9] = shade(cloth, 0.9) + (255,)
        elif name == "warlord":
            bone = MATS["bone"]
            for (x, y) in ((2, 5), (1, 4), (1, 3), (2, 2), (13, 5), (14, 4), (14, 3), (13, 2)):
                px[x, y] = shade(bone, 0.95) + (255,)
    elif piece == "chestplate":
        if name == "paladin":
            for y in range(4, 13):
                for x in range(5, 11):
                    if px[x, y][3]:
                        px[x, y] = shade(cloth, 0.85 + 0.1 * noise(x, y)) + (255,)
            for y in range(5, 11):
                px[7, y] = px[8, y] = acc(7, y)
            for x in range(6, 10):
                px[x, 7] = acc(x, 7)
        elif name == "gladiator":
            for i in range(10):
                x, y = 3 + i, 3 + i
                if px[x, y][3]:
                    px[x, y] = acc(x, y)
            for x in range(4, 12):
                px[x, 11] = acc(x, 11)
        elif name == "ronin":
            for y in range(3, 14, 2):
                for x in range(16):
                    if px[x, y][3]:
                        px[x, y] = shade(cloth, 0.9) + (255,)
        elif name == "warlord":
            for y in range(2, 5):
                for x in range(1, 15):
                    if px[x, y][3]:
                        px[x, y] = surface("fur", x, y, 16, 16, edge=False)
    elif piece == "leggings":
        if name in ("ronin", "warlord"):
            for y in range(5, 10):
                for x in range(16):
                    if px[x, y][3]:
                        px[x, y] = shade(cloth, 0.85 + 0.15 * ((x + y) % 2)) + (255,)
        if name == "paladin":
            for x in (4, 5, 10, 11):
                px[x, 8] = shade(MATS["steel"], 1.3) + (255,)
    elif piece == "boots":
        if name == "warlord":
            for y in (7, 8):
                for x in range(16):
                    if px[x, y][3]:
                        px[x, y] = surface("fur", x, y, 16, 16, edge=False)
        if name == "gladiator":
            for x in range(16):
                if px[x, 9][3]:
                    px[x, 9] = acc(x, 9)
    d = os.path.join(ASSETS, "textures", "item")
    os.makedirs(d, exist_ok=True)
    img.save(os.path.join(d, f"{name}_{piece}.png"))


def has_cloth(name, piece):
    slot = {"helmet": "head", "chestplate": "chest", "leggings": "legs", "boots": "feet"}[piece]
    cloth_pieces = any(p["layer"] == "cloth" and p["slot"] == slot for p in SETS[name]["pieces"])
    flat_dye = (name, piece) in (("paladin", "chestplate"), ("ronin", "chestplate"))
    return cloth_pieces or flat_dye


def item_json(name, piece):
    item_id = f"{name}_{piece}"
    model = {"type": "minecraft:model", "model": f"{NS}:item/{item_id}"}
    with open(os.path.join(ASSETS, "items", item_id + ".json"), "w") as f:
        json.dump({"model": model}, f, indent=2)
    with open(os.path.join(ASSETS, "models", "item", item_id + ".json"), "w") as f:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{item_id}"}}, f, indent=2)


RECIPES = {
    # set: (main ingredient, accent)
    "paladin": ("minecraft:iron_ingot", "minecraft:gold_ingot"),
    "gladiator": ("minecraft:copper_ingot", "minecraft:leather"),
    "ronin": ("minecraft:iron_ingot", "minecraft:string"),
    "warlord": ("minecraft:leather", "minecraft:bone"),
}
PATTERNS = {
    "helmet": ["XAX", "X X"],
    "chestplate": ["X X", "XAX", "XXX"],
    "leggings": ["XXX", "A A", "X X"],
    "boots": ["A A", "X X"],
}


def recipes(name, piece):
    main, accent = RECIPES[name]
    item_id = f"{NS}:{name}_{piece}"
    d = os.path.join(DATA, "recipe")
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, f"{name}_{piece}.json"), "w") as f:
        json.dump({"type": "minecraft:crafting_shaped", "category": "equipment", "key": {"X": main, "A": accent},
                   "pattern": PATTERNS[piece], "result": {"id": item_id}}, f, indent=2)
    if has_cloth(name, piece):
        with open(os.path.join(d, f"{name}_{piece}_dyed.json"), "w") as f:
            json.dump({"type": "minecraft:crafting_dye", "dye": "#minecraft:dyes", "group": "dyed_armor",
                       "result": {"id": item_id}, "target": item_id}, f, indent=2)


def stats():
    """The sets' numbers for Armory.java (printed, so the Java constants can be checked against them)."""
    for name, s in SETS.items():
        print(name, "durability", s["durability"], "defense", s["defense"], "toughness", s["toughness"],
              "knockback", s["knockback"], "dyeable", [p for p in PIECES if has_cloth(name, p)])


if __name__ == "__main__":
    os.makedirs(os.path.join(ASSETS, "items"), exist_ok=True)
    os.makedirs(os.path.join(ASSETS, "models", "item"), exist_ok=True)
    for name, spec in SETS.items():
        write_extras(name, spec)
        for piece in PIECES:
            sprite(name, piece)
            item_json(name, piece)
            recipes(name, piece)
    paladin_flat()
    gladiator_flat()
    ronin_flat()
    warlord_flat()
    stats()
