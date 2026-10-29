"""The symbiote's big body (the hulk): an original box model built over the player's skeleton (head, body, arms and legs
keep the player model's pivots, so every pose still drives it), and its texture, drawn here pixel by pixel (no art is
copied). A towering brute of symbiote goo congealed over the wearer like muscle (Andy: "the size should come more from
the goo congealing around the user like muscles ... by engulfing the player"): every muscle is a rounded mass (a few
overlapping boxes with their edges taken in, so it reads as a smooth lump, not a plate; see mass()), the masses
overlapping and flowing into each other: a deep ribcage under heavy pecs, lats flaring out of the back, a thick waist
and pelvis, great round shoulders on hunched traps, thick arms ending in big fists with bone spurs breaking out of the
forearms, legs like tree trunks with clawed toes, and the head up on the traps, its skull swelling at the back, with
big angular eyes, a hinged lower jaw and rows of fangs (cut out of the texture). The texture shades each mass as if it
were round (lit from above, darker where it tucks into its neighbours, a wet sheen and the strain's rim; paint_hulk()),
so the boxes read as glossy goo.

Every muscle is its own part (its boxes together), so the equip animation can flow them on one at a time, in ORDER,
and swell them (client WebHulk.Model.build, WebSuitGrowth): the order follows the film's transformation (Andy's clip):
the chest and arms first, then the shoulders, back and hips, down the legs, the head swallowed last. Each part's pivot
is where it grows from: on the bone (the spine, the arm's or leg's axis) at the top of a limb's muscle, so it flows down
the limb and swells out round it.

Also draws web_symbiote_flesh.png: four swatches (wet black, black with its sheen, bone, the tongue's red) for the
lengths drawn in code (the tongue, the weapon arm, the tendrils that wrap the body; client WebTubes).

Also Consume's maw (client WebMouthLayer) in the same style: the big body's head grown huge, hinged to gape and snap
(it replaced tools/gen_web_maw.py's plain black box; 0.17.172: rebuilt from rounded goo masses like the body, the flat
plates scrapped, Andy: "it doesnt fit the new aesthetic").

Writes:
  src/main/resources/assets/jaymospowers/textures/entity/web_symbiote_hulk.png
  src/main/resources/assets/jaymospowers/textures/entity/web_symbiote_flesh.png
  src/main/resources/assets/jaymospowers/textures/entity/web_symbiote_mouth.png
  src/client/java/com/jaymospowers/client/power/web/WebHulkMesh.java  (generated: don't edit by hand)
  src/client/java/com/jaymospowers/client/power/web/WebMawMesh.java  (generated: don't edit by hand)
Then run tools/gen_web_strains.py for the white and red strains' twins of the textures.
Run from the project root:  python3 -I tools/gen_web_hulk.py
"""
import math
import random
from pathlib import Path

from PIL import Image

PROJECT = Path(__file__).resolve().parent.parent
TEXTURES = PROJECT / "src" / "main" / "resources" / "assets" / "jaymospowers" / "textures" / "entity"
JAVA = PROJECT / "src" / "client" / "java" / "com" / "jaymospowers" / "client" / "power" / "web" / "WebHulkMesh.java"

TEX_W, TEX_H = 256, 128

BLACK = (16, 16, 21)
DEEP = (8, 8, 11)
SHEEN = (70, 84, 108)
TEAL = (52, 96, 112)
VEIN = (44, 38, 64)
EYE_WHITE = (240, 240, 246)
EYE_EDGE = (186, 190, 204)
BONE = (226, 222, 206)
BONE_SHADE = (168, 160, 140)
RED = (150, 22, 30)
RED_DEEP = (82, 8, 14)
RED_LIGHT = (196, 62, 70)

# The muscles: each one a part (part, name, its pivot relative to the part's, a step in the order the goo flows on,
# 0 first) holding one or more boxes (from (x, y, z) relative to the PART's pivot, size (w, h, d), paint, and the
# shape of the whole mass it belongs to, for the shading).
MUSCLES = []
# every box of every muscle, flat (for packing and painting)
BOXES = []


def cube(frm, size, paint, shape):
    return dict(frm=tuple(frm), size=tuple(size), paint=paint, shape=shape)


def muscle(part, name, pivot, order, cubes):
    MUSCLES.append(dict(part=part, name=name, pivot=tuple(pivot), order=order, cubes=cubes))


def plain(part, name, frm, size, paint, pivot, order):
    """A muscle of one plain box (the face, the fangs, bone)."""
    centre = tuple(frm[k] + size[k] / 2 for k in range(3))
    muscle(part, name, pivot, order, [cube(frm, size, paint, (centre, tuple(s / 2 for s in size)))])


def rounded(centre, size, paint, a, c=0):
    """
    The boxes of a rounded mass {@code size} (whole pixels) round {@code centre}: its edges taken in {@code a} pixels
    (a box long along each axis, the others inset), and with {@code c} its corners filled in a step further out (a
    fourth box inset {@code c} all round), so the edge steps twice. Axes too thin to round stay square.
    """
    w, h, d = size
    shape = (tuple(centre), (w / 2, h / 2, d / 2))
    axes = [k for k in range(3) if a > 0 and size[k] >= 2 * a + 1]

    def at(sz):
        return cube(tuple(centre[k] - sz[k] / 2 for k in range(3)), sz, paint, shape)

    if len(axes) < 2:
        return [at(size)]
    cubes = []
    for long in axes:
        cubes.append(at(tuple(size[k] if k == long or k not in axes else size[k] - 2 * a for k in range(3))))
    if c and len(axes) == 3:
        cubes.append(at(tuple(s - 2 * c for s in size)))
    return cubes


def mass(part, name, centre, size, paint, order, a, c=0, pivot=None):
    """A rounded muscle; its pivot (where it grows from) defaults to the bone: the spine at its height, a limb's axis at its top."""
    if pivot is None:
        pivot = (0, centre[1], 0) if part == "body" else (0, centre[1] - size[1] / 2, 0)
    muscle(part, name, pivot, order, rounded(centre, size, paint, a, c))


def mirrored(x):
    return -x


# ---- the frame: a tall behemoth, not a squat gremlin (Andy: "increase the size of his torso/ abdomen ... he needs a
# thinner waist and legs that extend out to the current upper body bulk"). On the big body (client WebHulk.shift) the
# hips sit LEG_LIFT higher than the player's, so the legs run 12 + LEG_LIFT down to the ground, and the neck BODY_LIFT
# higher, so the torso runs TORSO from the neck to the hips. Everything keeps the player model's joints.
BODY_LIFT, LEG_LIFT = 6, 4
TORSO = 12 - LEG_LIFT + BODY_LIFT
LEG = 12 + LEG_LIFT

# the order the goo flows on in (the film's transformation): the chest, the arms, down them, over the shoulders and the
# back, down the legs, the head swallowed last and its jaws closing over it
CHEST, ARMS, FOREARMS, SHOULDERS, THIGHS, SHINS, FEET, HEAD, JAWS = range(9)

# ---- the torso (pivot at the neck): a deep ribcage under heavy pecs, lats flaring out of the back, a thick waist and
# pelvis, the traps humped up behind the head. Every mass grows from the spine. ----
mass("body", "core", (0, 4.5, 0), (14, 9, 8), "goo", CHEST, 2, 1)
for side, sx in (("r", 1), ("l", -1)):
    mass("body", "pec_" + side, (-3.5 * sx, 4, -4), (7, 6, 3), "pec", CHEST, 1)
    mass("body", "lat_" + side, (-7 * sx, 5.5, 0.5), (5, 8, 7), "goo", FOREARMS, 2)
mass("body", "waist", (0, 10.5, 0), (10, 5, 7), "goo", ARMS, 1)
mass("body", "abs", (0, 10, -3), (8, 6, 2), "abs", ARMS, 1)
mass("body", "back", (0, 5, 3.5), (12, 9, 3), "back", FOREARMS, 1)
mass("body", "hips", (0, 13.5, 0.5), (11, 4, 7), "goo", SHOULDERS, 1)
mass("body", "traps", (0, -0.5, 1.5), (13, 5, 6), "goo", SHOULDERS, 2, 1)

# ---- the arms (right: pivot at the shoulder; the left is the mirror): great round shoulders, a thick upper arm with
# the bicep swelling out of it, heavy forearms, big fists; bone spurs breaking out along the outside of each forearm ----
for side, part, sx in (("r", "right_arm", 1), ("l", "left_arm", -1)):
    mass(part, "shoulder", (-1 * sx, -1, 0), (9, 8, 9), "goo", SHOULDERS, 2, 1, pivot=(0, -1, 0))
    mass(part, "upper", (-0.5 * sx, 6, 0), (7, 8, 7), "goo", ARMS, 1)
    mass(part, "bicep", (-0.5 * sx, 5.5, -3), (5, 5, 3), "goo", ARMS, 1, pivot=(0, 3, 0))
    mass(part, "fore", (-0.5 * sx, 12.5, -0.5), (8, 7, 8), "fore", FOREARMS, 2, 1)
    mass(part, "wrist", (-0.5 * sx, 16, -0.5), (6, 2, 6), "goo", FOREARMS, 1)
    mass(part, "fist", (-0.5 * sx, 18.5, -0.5), (7, 5, 7), "goo", SHOULDERS, 1)
    plain(part, "knuckles", (-3.5 if sx > 0 else -2.5, 16.5, -4.75), (6, 3, 1), "knuckle", (0, 17, -4), FEET)
    for i, (y, h) in enumerate(((10, 2), (12.5, 2), (14.5, 1))):
        z = (-1.5, 0, -1)[i]
        plain(part, "spur%d" % i, (-5.25 if sx > 0 else 4.25, y, z), (1, h, 1), "bone", (-4 * sx, 12, 0), FEET)

# ---- the legs (pivot at the hip, LEG long): thighs like tree trunks with the quads bulging over the knee, a heavy
# calf behind the shin, broad feet with clawed toes ----
for side, part, sx in (("r", "right_leg", 1), ("l", "left_leg", -1)):
    mass(part, "thigh", (0, 3, 0), (9, 9, 8), "goo", THIGHS, 2, 1, pivot=(0, 0, 0))
    mass(part, "quad", (0, 5.5, -3.5), (6, 5, 2), "goo", THIGHS, 1, pivot=(0, 3, 0))
    mass(part, "calf", (0, 10.5, 0.5), (7, 6, 7), "goo", SHINS, 1, pivot=(0, 8, 0))
    mass(part, "shin", (0, 11.5, -1), (5, 7, 5), "goo", SHINS, 1, pivot=(0, 8, 0))
    mass(part, "foot", (0, LEG - 1, -1.5), (8, 2, 8), "foot", FEET, 1)
    for i, x in enumerate((-3, -0.5, 2)):
        plain(part, "toe%d" % i, (x if sx > 0 else -x - 1, LEG - 1, -6.5), (1, 1, 2), "bone", (0, LEG - 1, -5), FEET)

# ---- the head (pivot at the neck; it grows up from it): the face (the skull's front, its big eyes), the cranium
# swelling round and out at the back, a heavy muzzle, a hinged jaw, fangs, a red throat ----
plain("head", "skull", (-4.5, -8, -4.5), (9, 6, 9), "skull", (0, 0, 0), HEAD)
mass("head", "cranium", (0, -6, 1), (10, 6, 8), "goo", HEAD, 2, 1, pivot=(0, 0, 0))
plain("head", "muzzle", (-4.5, -2, -5.5), (9, 2, 10), "muzzle", (0, -1, 0), HEAD)
plain("head", "fangs", (-4.5, 0, -5.5), (9, 3, 1), "fangs_down", (0, 0, -5), JAWS)
plain("head", "fangs_r", (-4.5, 0, -4.5), (1, 3, 5), "fangs_down", (0, 0, -5), JAWS)
plain("head", "fangs_l", (3.5, 0, -4.5), (1, 3, 5), "fangs_down", (0, 0, -5), JAWS)
plain("head", "throat", (-4, 0, 1), (8, 3, 1), "red", (0, 0, 1), JAWS)
# the jaw: its own part, hinged at the back (see JAW_PIVOT); its boxes hang straight off it
JAW_PIVOT = (0, 0.5, 2)
plain("jaw", "chin", (-4.5, 0.5, -5.5), (9, 2, 8), "chin", JAW_PIVOT, JAWS)
plain("jaw", "jaw_fangs", (-4, -2, -5), (8, 3, 1), "fangs_up", JAW_PIVOT, JAWS)
plain("jaw", "jaw_fangs_r", (-4.5, -2, -4), (1, 3, 5), "fangs_up", JAW_PIVOT, JAWS)
plain("jaw", "jaw_fangs_l", (3.5, -2, -4), (1, 3, 5), "fangs_up", JAW_PIVOT, JAWS)

for m in MUSCLES:
    BOXES.extend(m["cubes"])

# where each part's pivot is in the player model (the jaw's is in the head's space)
PART_PIVOTS = {
    "head": (0, 0, 0), "body": (0, 0, 0), "right_arm": (-5, 2, 0), "left_arm": (5, 2, 0),
    "right_leg": (-1.9, 12, 0), "left_leg": (1.9, 12, 0),
}
# the elbows and knees: the forearm (with its wrist, fist, knuckles and spurs) and the shin (calf, foot, toes) hang off
# a hinge of their own so they bend (client WebHulk.Model.limbs). Each hinge sits a little to the side the joint opens
# on (the elbow toward the back, the knee toward the front) so no gap shows as it bends.
ELBOW = (0, 9.5, 2)
KNEE = (0, 8.5, -2.5)
HINGES = {"right_arm": ("elbow", ELBOW), "left_arm": ("elbow", ELBOW), "right_leg": ("knee", KNEE), "left_leg": ("knee", KNEE)}
LOWER = {"fore", "wrist", "fist", "knuckles", "spur0", "spur1", "spur2", "calf", "shin", "foot", "toe0", "toe1", "toe2"}


def hinge(m):
    """The hinge a muscle hangs off (its name and pivot in the part's space), or None."""
    if m["part"] in HINGES and m["name"] in LOWER:
        return HINGES[m["part"]]
    return None


# how much further out each muscle on a part sits than the one before it (pixels; see java())
OVERLAP_STEP = 0.02

EMPTY_CHILD = {"head": "hat", "body": "jacket", "right_arm": "right_sleeve", "left_arm": "left_sleeve",
               "right_leg": "right_pants", "left_leg": "left_pants"}

def footprint(size):
    w, h, d = (math.ceil(v) for v in size)
    return 2 * d + 2 * w, d + h


def pack(boxes=None, tex_w=TEX_W, tex_h=TEX_H):
    """Shelf-packs every box's UV net into the texture (biggest first)."""
    boxes = BOXES if boxes is None else boxes
    order = sorted(range(len(boxes)), key=lambda i: -footprint(boxes[i]["size"])[1] * 1000 - footprint(boxes[i]["size"])[0])
    x = y = shelf = 0
    for i in order:
        fw, fh = footprint(boxes[i]["size"])
        if x + fw > tex_w:
            x, y, shelf = 0, y + shelf, 0
        boxes[i]["uv"] = (x, y)
        x += fw
        shelf = max(shelf, fh)
    assert y + shelf <= tex_h, ("texture too small", y + shelf)


def lerp(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[k] + (b[k] - a[k]) * t)) for k in range(3))


EYES = [
    ".........",
    "WWWk.kWWW",
    "eWWWkWWWe",
    ".eWWkWWe.",
    "..eWkWe..",
    "...k.k...",
]

# fangs: per column of a plate, how long the fang is (rows); 0 = gap
FANG_ROWS = [3, 1, 2, 3, 1, 3, 2, 1, 3]


def faces(b):
    """Each face of a box's net: name -> (x0, y0, w, h), in the texture."""
    u, v = b["uv"]
    w, h, d = (math.ceil(s) for s in b["size"])
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "east": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }


def paint(img, rnd, boxes=None):
    boxes = BOXES if boxes is None else boxes
    for b in boxes:
        f = faces(b)
        kind = b["paint"]
        for name, (x0, y0, w, h) in f.items():
            for yy in range(h):
                for xx in range(w):
                    c = lerp(DEEP, BLACK, 0.75 + rnd.random() * 0.25)
                    a = 255
                    side = name not in ("top", "bottom")
                    v = (yy + 0.5) / h
                    if side:
                        # wet sheen high on every face, a teal rim at the outer edges
                        glow = math.exp(-((v - 0.18) / 0.22) ** 2) * 0.38
                        edge = min(xx, w - 1 - xx)
                        if edge == 0 and w > 2:
                            c = lerp(c, TEAL, 0.35)
                        c = lerp(c, SHEEN, glow)
                    elif name == "top":
                        c = lerp(c, SHEEN, 0.3)
                    if kind == "pec" and side and yy % 2 == 1 and name == "front":
                        c = lerp(c, DEEP, 0.5)
                    if kind == "abs" and name == "front":
                        if xx == w // 2 or yy in (1, 3):
                            c = lerp(c, DEEP, 0.7)
                        else:
                            c = lerp(c, SHEEN, 0.2)
                    if kind in ("fore", "muscle") and side and (xx + int(yy * 0.4)) % 3 == 0 and rnd.random() < 0.35:
                        c = lerp(c, DEEP, 0.45)
                    if kind == "knuckle":
                        c = lerp(c, DEEP, 0.5) if side else lerp(c, SHEEN, 0.2)
                    if kind == "bone":
                        c = lerp(BONE, BONE_SHADE, v * 0.8 + rnd.random() * 0.1)
                    if kind == "red":
                        c = lerp(RED_DEEP, RED, rnd.random() * 0.6)
                    if kind in ("muzzle", "maw_skull") and name == "bottom" or kind == "chin" and name == "top" or kind == "maw_muzzle" and name in ("bottom", "back"):
                        # the roof of the mouth and its floor
                        c = lerp(RED_DEEP, RED, 0.3 + rnd.random() * 0.5)
                        if (xx + yy) % 4 == 0:
                            c = lerp(c, RED_LIGHT, 0.5)
                    if kind.startswith("fangs"):
                        a = 0
                        col = xx % len(FANG_ROWS)
                        length = FANG_ROWS[col]
                        row = yy if kind == "fangs_down" else h - 1 - yy
                        if name in ("top", "bottom"):
                            a = 0
                        elif row < length:
                            a = 255
                            c = lerp(BONE, BONE_SHADE, row / 3.0 + rnd.random() * 0.15)
                    if kind in ("skull", "maw_skull") and name == "front":
                        eyes = EYES if kind == "skull" else MAW_EYES
                        ch = eyes[yy][xx] if yy < len(eyes) and xx < len(eyes[yy]) else "."
                        if ch == "W":
                            c = EYE_WHITE
                        elif ch == "e":
                            c = EYE_EDGE
                        elif ch == "k":
                            c = (2, 2, 4)
                    img.putpixel((x0 + xx, y0 + yy), c + (a,))
    # veins: a few dark threads wandering over the bigger faces
    for b in boxes:
        if b["paint"] not in ("muscle", "fore", "back", "pec", "maw_skull", "chin"):
            continue
        for name, (x0, y0, w, h) in faces(b).items():
            if w < 4 or h < 4 or rnd.random() < 0.4:
                continue
            px, py = x0 + rnd.randrange(w), y0
            for _ in range(h * 2):
                if x0 <= px < x0 + w and y0 <= py < y0 + h:
                    old = img.getpixel((px, py))
                    img.putpixel((px, py), lerp(old[:3], VEIN, 0.6) + (old[3],))
                px += rnd.choice((-1, 0, 0, 1))
                py += rnd.choice((0, 1, 1))


def flesh():
    """Four 16x16 swatches: wet black, sheen, bone, red."""
    rnd = random.Random(77)
    img = Image.new("RGBA", (32, 32))
    for sy in range(2):
        for sx in range(2):
            swatch = sy * 2 + sx
            for y in range(16):
                for x in range(16):
                    t = rnd.random()
                    if swatch == 0:
                        c = lerp(DEEP, BLACK, 0.6 + t * 0.4)
                        if y % 5 == 0:
                            c = lerp(c, SHEEN, 0.15)
                    elif swatch == 1:
                        c = lerp(BLACK, SHEEN, 0.15 + 0.35 * math.exp(-((y - 4) / 4.0) ** 2) + t * 0.1)
                        if x % 4 == 0:
                            c = lerp(c, TEAL, 0.25)
                    elif swatch == 2:
                        c = lerp(BONE, BONE_SHADE, y / 16.0 * 0.6 + t * 0.15)
                    else:
                        c = lerp(RED_DEEP, RED_LIGHT, 0.35 + 0.35 * math.sin(x * 0.7) * 0.5 + t * 0.2)
                        if x in (7, 8):
                            # the groove down the middle of the tongue
                            c = lerp(c, RED_DEEP, 0.6)
                    img.putpixel((sx * 16 + x, sy * 16 + y), c + (255,))
    return img

# ---- the big body's texture: each mass shaded as if it were round ----
# the light (model space: y down, the front -z): from above, in front, a little to the wearer's left
LIGHT = (0.3, -1.0, -0.6)
# the masses' roundness for the shading (a superellipse's exponent: 2 an egg, higher squarer)
ROUNDNESS = 4.0


def norm(v):
    n = math.sqrt(sum(c * c for c in v)) or 1.0
    return tuple(c / n for c in v)


def face_point(b, name, xx, yy):
    """Where a texel of a box's face is (part space, the texel's middle), and the face's outward normal."""
    x0, y0, z0 = b["frm"]
    w, h, d = (math.ceil(s) for s in b["size"])
    u, v = xx + 0.5, yy + 0.5
    if name == "east":
        return (x0, y0 + v, z0 + d - u), (-1, 0, 0)
    if name == "front":
        return (x0 + u, y0 + v, z0), (0, 0, -1)
    if name == "west":
        return (x0 + w, y0 + v, z0 + u), (1, 0, 0)
    if name == "back":
        return (x0 + w - u, y0 + v, z0 + d), (0, 0, 1)
    if name == "top":
        return (x0 + u, y0, z0 + d - v), (0, -1, 0)
    return (x0 + u, y0 + h, z0 + d - v), (0, 1, 0)


def goo_shade(b, name, xx, yy, rnd):
    """The black of the goo at a texel: lit as a round mass, a wet sheen on top, dark where it tucks into its neighbours."""
    p, fn = face_point(b, name, xx, yy)
    centre, half = b["shape"]
    q = [(p[k] - centre[k]) / max(0.5, half[k]) for k in range(3)]
    # the rounded mass's normal there, a little of the flat face's in it
    n = norm(tuple(math.copysign(abs(q[k]) ** (ROUNDNESS - 1), q[k]) / max(0.5, half[k]) for k in range(3)))
    n = norm(tuple(n[k] * 0.8 + fn[k] * 0.2 for k in range(3)))
    light = norm(LIGHT)
    lit = max(0.0, sum(n[k] * light[k] for k in range(3)))
    # how far out toward the mass's rim the texel is (1 the middle of a face, more toward the edges and corners)
    r = sum(abs(c) ** ROUNDNESS for c in q) ** (1.0 / ROUNDNESS)
    tuck = max(0.0, min(1.0, (r - 1.04) / 0.22))
    c = lerp(DEEP, BLACK, 0.55 + 0.45 * lit * (1.0 - 0.6 * tuck) + rnd.random() * 0.1)
    # the wet sheen: a soft highlight where the mass faces the light, sharp in its middle
    gloss = lit ** 3 * (1.0 - tuck)
    c = lerp(c, SHEEN, 0.6 * gloss)
    # the strain's rim along the mass's upper edges
    rim = max(0.0, 1.0 - abs(r - 1.12) / 0.05) * max(0.0, -n[1] + 0.2)
    c = lerp(c, TEAL, 0.2 * min(1.0, rim))
    return c


def paint_hulk(img, rnd, boxes=None, eyes=None):
    """The big body (or the maw, 0.17.172): the goo shaded mass by mass; the face, mouth, fangs and bone flat (see paint)."""
    boxes = BOXES if boxes is None else boxes
    eyes = EYES if eyes is None else eyes
    for b in boxes:
        kind = b["paint"]
        for name, (x0, y0, w, h) in faces(b).items():
            side = name not in ("top", "bottom")
            for yy in range(h):
                for xx in range(w):
                    c = goo_shade(b, name, xx, yy, rnd)
                    a = 255
                    v = (yy + 0.5) / h
                    if kind == "pec" and name == "front" and yy % 2 == 1:
                        # the fibres of the chest, faint
                        c = lerp(c, DEEP, 0.3)
                    if kind == "abs" and name == "front":
                        if xx == w // 2 or yy in (1, 3):
                            c = lerp(c, DEEP, 0.6)
                    if kind == "back" and name == "back" and xx == w // 2:
                        # the furrow of the spine
                        c = lerp(c, DEEP, 0.6)
                    if kind == "fore" and side and (xx + int(yy * 0.4)) % 3 == 0 and rnd.random() < 0.25:
                        c = lerp(c, DEEP, 0.35)
                    if kind == "knuckle":
                        c = lerp(c, DEEP, 0.4) if side else lerp(c, SHEEN, 0.15)
                    if kind == "bone":
                        c = lerp(BONE, BONE_SHADE, v * 0.8 + rnd.random() * 0.1)
                    if kind == "red":
                        c = lerp(RED_DEEP, RED, rnd.random() * 0.6)
                    if kind == "muzzle" and name == "bottom" or kind == "chin" and name == "top":
                        # the roof of the mouth and its floor
                        c = lerp(RED_DEEP, RED, 0.3 + rnd.random() * 0.5)
                        if (xx + yy) % 4 == 0:
                            c = lerp(c, RED_LIGHT, 0.5)
                    if kind.startswith("fangs"):
                        a = 0
                        length = FANG_ROWS[xx % len(FANG_ROWS)]
                        row = yy if kind == "fangs_down" else h - 1 - yy
                        if side and row < length:
                            a = 255
                            c = lerp(BONE, BONE_SHADE, row / 3.0 + rnd.random() * 0.15)
                    if kind == "skull" and name == "front":
                        ch = eyes[yy][xx] if yy < len(eyes) and xx < len(eyes[yy]) else "."
                        if ch == "W":
                            c = EYE_WHITE
                        elif ch == "e":
                            c = EYE_EDGE
                        elif ch == "k":
                            c = (2, 2, 4)
                    img.putpixel((x0 + xx, y0 + yy), c + (a,))
    # veins: a few dark threads wandering over the bigger faces of the goo
    for b in boxes:
        if b["paint"] not in ("goo", "fore", "back", "pec"):
            continue
        for name, (x0, y0, w, h) in faces(b).items():
            if w < 4 or h < 4 or rnd.random() < 0.55:
                continue
            px, py = x0 + rnd.randrange(w), y0
            for _ in range(h * 2):
                if x0 <= px < x0 + w and y0 <= py < y0 + h:
                    old = img.getpixel((px, py))
                    img.putpixel((px, py), lerp(old[:3], VEIN, 0.5) + (old[3],))
                px += rnd.choice((-1, 0, 0, 1))
                py += rnd.choice((0, 1, 1))


def fmt(v):
    return ("%.2f" % v).rstrip("0").rstrip(".") + "F"


def java():
    lines = []
    lines.append("package com.jaymospowers.client.power.web;")
    lines.append("")
    lines.append("import net.minecraft.client.model.geom.PartPose;")
    lines.append("import net.minecraft.client.model.geom.builders.CubeDeformation;")
    lines.append("import net.minecraft.client.model.geom.builders.CubeListBuilder;")
    lines.append("import net.minecraft.client.model.geom.builders.LayerDefinition;")
    lines.append("import net.minecraft.client.model.geom.builders.MeshDefinition;")
    lines.append("import net.minecraft.client.model.geom.builders.PartDefinition;")
    lines.append("")
    lines.append("/**")
    lines.append(" * GENERATED by {@code tools/gen_web_hulk.py}: don't edit by hand. The big body's masses of goo over the player model's")
    lines.append(" * skeleton (every muscle its own part, a few rounded boxes, flowed on in {@link #ORDER} by WebSuitGrowth and WebHulk),")
    lines.append(" * UV-mapped onto {@code web_symbiote_hulk.png}.")
    lines.append(" */")
    lines.append("final class WebHulkMesh {")
    lines.append("\tstatic final int TEX_W = %d, TEX_H = %d;" % (TEX_W, TEX_H))
    lines.append("\t/** The jaw's hinge in the head's space (pixels). */")
    lines.append("\tstatic final float JAW_X = %s, JAW_Y = %s, JAW_Z = %s;" % tuple(fmt(v) for v in JAW_PIVOT))
    lines.append("\t/** The elbows' and knees' hinges in the arm's and leg's space (pixels): the forearms and shins hang off them. */")
    lines.append("\tstatic final float ELBOW_Y = %s, ELBOW_Z = %s, KNEE_Y = %s, KNEE_Z = %s;" % (fmt(ELBOW[1]), fmt(ELBOW[2]), fmt(KNEE[1]), fmt(KNEE[2])))
    lines.append("\t/**")
    lines.append("\t * The frame (pixels): on the big body the hips sit {@code LEG_LIFT} higher than the player's and the neck {@code BODY_LIFT}")
    lines.append("\t * higher (WebHulk.shift), so the legs run {@code LEG} to the ground and the torso {@code TORSO} from the neck to the hips.")
    lines.append("\t * The arms reach {@code ARM} from the shoulder to the bottom of the fist.")
    lines.append("\t */")
    arm = max(c["frm"][1] + c["size"][1] for m in MUSCLES if m["part"] == "right_arm" for c in m["cubes"])
    lines.append("\tstatic final float BODY_LIFT = %s, LEG_LIFT = %s, TORSO = %s, LEG = %s, ARM = %s;"
                 % (fmt(BODY_LIFT), fmt(LEG_LIFT), fmt(TORSO), fmt(LEG), fmt(arm)))
    names = []
    for m in MUSCLES:
        if m["part"] != "jaw":
            h = hinge(m)
            names.append(((m["part"], h[0], m["name"]) if h else (m["part"], m["name"]), m["order"]))
    names.append((("head", "jaw"), JAWS))
    lines.append("\t/** Each muscle part (its path of parts from the root, its name last) and when the goo flows it on (0 first, to {@link #LAST_ORDER}). */")
    lines.append("\tstatic final String[][] PARTS = {")
    for path, order in names:
        lines.append("\t\t{%s}," % ", ".join("\"%s\"" % n for n in path))
    lines.append("\t};")
    lines.append("\tstatic final int[] ORDER = {%s};" % ", ".join(str(o) for _, o in names))
    lines.append("\tstatic final int LAST_ORDER = %d;" % max(o for _, o in names))
    lines.append("")
    lines.append("\tprivate WebHulkMesh() {")
    lines.append("\t}")
    lines.append("")
    lines.append("\tstatic LayerDefinition create() {")
    lines.append("\t\tMeshDefinition mesh = new MeshDefinition();")
    lines.append("\t\tPartDefinition root = mesh.getRoot();")
    for part, (px, py, pz) in PART_PIVOTS.items():
        var = part.replace("_", "")
        lines.append("\t\tPartDefinition %s = root.addOrReplaceChild(\"%s\", CubeListBuilder.create(), PartPose.offset(%s, %s, %s));"
                     % (var, part, fmt(px), fmt(py), fmt(pz)))
        lines.append("\t\t%s.addOrReplaceChild(\"%s\", CubeListBuilder.create(), PartPose.ZERO);" % (var, EMPTY_CHILD[part]))
    for part, (name, (hx, hy, hz)) in HINGES.items():
        var = part.replace("_", "")
        lines.append("\t\tPartDefinition %s%s = %s.addOrReplaceChild(\"%s\", CubeListBuilder.create(), PartPose.offset(%s, %s, %s));"
                     % (var, name, var, name, fmt(hx), fmt(hy), fmt(hz)))
    lines.append("\t\tPartDefinition jaw = head.addOrReplaceChild(\"jaw\", CubeListBuilder.create(), PartPose.offset(%s, %s, %s));"
                 % tuple(fmt(v) for v in JAW_PIVOT))

    # each muscle on a part grown out a hair more than the last (OVERLAP_STEP), so masses that meet face to face don't
    # flicker against each other
    rank = {}

    def cubes(m, origin):
        n = rank[m["part"]] = rank.get(m["part"], 0) + 1
        grow = "%.3fF" % (OVERLAP_STEP * n)
        out = "CubeListBuilder.create()"
        for c in m["cubes"]:
            ox, oy, oz = (c["frm"][k] - origin[k] for k in range(3))
            out += ".texOffs(%d, %d).addBox(%s, %s, %s, %s, %s, %s, new CubeDeformation(%s))" % (
                c["uv"][0], c["uv"][1], fmt(ox), fmt(oy), fmt(oz), *(fmt(math.ceil(s)) for s in c["size"]), grow)
        return out

    for m in MUSCLES:
        var = m["part"].replace("_", "")
        px, py, pz = m["pivot"]
        if m["part"] == "jaw":
            # the jaw's boxes hang straight off it: their pivots are its own
            lines.append("\t\t%s.addOrReplaceChild(\"%s\", %s, PartPose.ZERO);" % (var, m["name"], cubes(m, JAW_PIVOT)))
            continue
        h = hinge(m)
        body = cubes(m, m["pivot"])
        if h:
            # hung off the elbow or knee: the same place, its pivot now from the hinge
            var += h[0]
            px, py, pz = px - h[1][0], py - h[1][1], pz - h[1][2]
        lines.append("\t\t%s.addOrReplaceChild(\"%s\", %s, PartPose.offset(%s, %s, %s));" % (var, m["name"], body, fmt(px), fmt(py), fmt(pz)))
    lines.append("\t\treturn LayerDefinition.create(mesh, TEX_W, TEX_H);")
    lines.append("\t}")
    lines.append("}")
    JAVA.write_text("\n".join(lines) + "\n")


# ---- Consume's maw (client WebMouthLayer). (0.17.172) Andy: "use the new living armor suit design for the big head that
# consumes, and scrap the old one. it doesnt fit the new aesthetic." So it's built as the big body is now: rounded
# masses of congealed goo (mass(), shaded round by paint_hulk) in place of the old flat plates: a swollen cranium with a
# crown lobe over it and heavy brows, the face a flat plate with the big eyes, cheeks of goo bulging either side of the
# muzzle, the muzzle and the chin lumpy slabs with the red mouth between, jowls under the jaw, and rows of cut-out fangs.
# Parts (all hinged at the back of the head, MAW_HINGE in the head's space): the throat (a red wall closing off the
# face behind it), the skull and the jaw. Boxes are relative to that hinge. ----
MAW_TEX_W, MAW_TEX_H = 128, 128
MAW_HINGE = (0, -3, 5)
MAW_BOXES = []


def maw_box(part, name, frm, size, paint):
    centre = tuple(frm[k] + size[k] / 2 for k in range(3))
    MAW_BOXES.append(dict(part=part, name=name, frm=tuple(frm), size=tuple(size), paint=paint, shape=(centre, tuple(v / 2 for v in size))))


def maw_mass(part, name, centre, size, paint, a, c=0):
    """A rounded mass of the maw: its boxes named name_0, name_1, ... (see rounded())."""
    for i, b in enumerate(rounded(centre, size, paint, a, c)):
        b["part"] = part
        b["name"] = "%s_%d" % (name, i)
        MAW_BOXES.append(b)


maw_box("throat", "wall", (-5, -6, -5), (10, 6, 1), "red")
# the skull: the cranium swelling out round and back, a lobe crowning it, brows humped over the eyes
maw_mass("skull", "cranium", (0, -6.5, -5), (12, 7, 12), "goo", 2, 1)
maw_mass("skull", "crown", (0, -9, -2), (8, 4, 8), "goo", 1)
for side, sx in (("r", -1), ("l", 1)):
    maw_mass("skull", "brow_" + side, (3 * sx, -9.5, -10), (5, 2, 4), "goo", 1)
    maw_mass("skull", "cheek_" + side, (6.5 * sx, -4, -6), (3, 5, 7), "goo", 1)
    maw_mass("jaw", "jowl_" + side, (5.5 * sx, 2, -4), (3, 3, 8), "goo", 1)
maw_box("skull", "face", (-5.5, -9, -11.5), (11, 6, 1), "skull")
maw_mass("skull", "snout", (0, -1.5, -6), (11, 3, 11), "muzzle", 1)
maw_box("skull", "fangs", (-5.5, 0, -11.5), (11, 3, 1), "fangs_down")
maw_box("skull", "fangs_r", (-5.5, 0, -10.5), (1, 3, 9), "fangs_down")
maw_box("skull", "fangs_l", (4.5, 0, -10.5), (1, 3, 9), "fangs_down")
# the jaw: a lumpy chin slab with the red floor of the mouth on top, jowls under it, fangs up
maw_mass("jaw", "chin", (0, 1.5, -5.5), (11, 3, 11), "chin", 1)
maw_box("jaw", "jaw_fangs", (-5, -3, -10.5), (10, 3, 1), "fangs_up")
maw_box("jaw", "jaw_fangs_r", (-5.5, -3, -9.5), (1, 3, 8), "fangs_up")
maw_box("jaw", "jaw_fangs_l", (4.5, -3, -9.5), (1, 3, 8), "fangs_up")

# the maw's eyes: 11 wide, angled in to meet over the snout
MAW_EYES = [
    "...........",
    "eWWWk.kWWWe",
    ".eWWWkWWWe.",
    "..eWWkWWe..",
    "...eWkWe...",
    "....k.k....",
]

MAW_JAVA = JAVA.with_name("WebMawMesh.java")


def maw_java():
    lines = []
    lines.append("package com.jaymospowers.client.power.web;")
    lines.append("")
    lines.append("import net.minecraft.client.model.geom.PartPose;")
    lines.append("import net.minecraft.client.model.geom.builders.CubeListBuilder;")
    lines.append("import net.minecraft.client.model.geom.builders.LayerDefinition;")
    lines.append("import net.minecraft.client.model.geom.builders.MeshDefinition;")
    lines.append("import net.minecraft.client.model.geom.builders.PartDefinition;")
    lines.append("")
    lines.append("/**")
    lines.append(" * GENERATED by {@code tools/gen_web_hulk.py}: don't edit by hand. Consume's maw (WebMouthLayer) built as the big body is")
    lines.append(" * (0.17.172): rounded masses of goo; a throat, a skull and a jaw, all hinged at the back of the head, UV-mapped onto")
    lines.append(" * {@code web_symbiote_mouth.png}.")
    lines.append(" */")
    lines.append("final class WebMawMesh {")
    lines.append("\tstatic final int TEX_W = %d, TEX_H = %d;" % (MAW_TEX_W, MAW_TEX_H))
    lines.append("")
    lines.append("\tprivate WebMawMesh() {")
    lines.append("\t}")
    lines.append("")
    lines.append("\tstatic LayerDefinition create() {")
    lines.append("\t\tMeshDefinition mesh = new MeshDefinition();")
    lines.append("\t\tPartDefinition root = mesh.getRoot();")
    hx, hy, hz = MAW_HINGE
    lines.append("\t\tPartDefinition throat = root.addOrReplaceChild(\"throat\", CubeListBuilder.create(), PartPose.offset(%s, %s, %s));" % (fmt(hx), fmt(hy), fmt(hz)))
    lines.append("\t\tPartDefinition skull = root.addOrReplaceChild(\"skull\", CubeListBuilder.create(), PartPose.offset(%s, %s, %s));" % (fmt(hx), fmt(hy), fmt(hz)))
    lines.append("\t\tPartDefinition jaw = root.addOrReplaceChild(\"jaw\", CubeListBuilder.create(), PartPose.offset(%s, %s, %s));" % (fmt(hx), fmt(hy), fmt(hz)))
    for b in MAW_BOXES:
        ox, oy, oz = b["frm"]
        lines.append("\t\t%s.addOrReplaceChild(\"%s\", CubeListBuilder.create().texOffs(%d, %d).addBox(%s, %s, %s, %d, %d, %d), PartPose.ZERO);"
                     % (b["part"], b["name"], b["uv"][0], b["uv"][1], fmt(ox), fmt(oy), fmt(oz), *(math.ceil(v) for v in b["size"])))
    lines.append("\t\treturn LayerDefinition.create(mesh, TEX_W, TEX_H);")
    lines.append("\t}")
    lines.append("}")
    MAW_JAVA.write_text("\n".join(lines) + "\n")


def maw():
    pack(MAW_BOXES, MAW_TEX_W, MAW_TEX_H)
    img = Image.new("RGBA", (MAW_TEX_W, MAW_TEX_H), (0, 0, 0, 0))
    paint_hulk(img, random.Random(4343), MAW_BOXES, MAW_EYES)
    img.save(TEXTURES / "web_symbiote_mouth.png")
    maw_java()


def main():
    pack(BOXES)
    rnd = random.Random(4242)
    img = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
    paint_hulk(img, rnd)
    TEXTURES.mkdir(parents=True, exist_ok=True)
    img.save(TEXTURES / "web_symbiote_hulk.png")
    flesh().save(TEXTURES / "web_symbiote_flesh.png")
    java()
    maw()
    print("muscles:", len(MUSCLES), "boxes:", len(BOXES), "maw boxes:", len(MAW_BOXES))


if __name__ == "__main__":
    main()
