"""Bio-built forms (Kingdoms 0.7.310, Andy: "drastically different from each other ... more akin to Cell"). Each form is
a whole new body over the player's, with its own model parts, in three palettes (the heritage's variants):

  form 0  Imperfect     hunched-insect grotesque: segmented carapace, beak, long swept-back V horns, folded beetle wings,
                        claws, growths, a long segmented tail with a stinger that reaches the ground
  form 1  Semi-Perfect  hulking: bigger and broader, crown of horns, a ball on the brow, carapace pauldrons, black
                        forearms and shins, a thick banded tail
  form 2  Perfect       sleek and stable: a human face with stripes, crown horns and the brow ball, long folded wings,
                        black chest and shoulders, the tail drawn in to a stinger at the small of the back

  palettes  seamed (green), brassbound (bronze and verdigris), opaline (pearl, mint and lilac)

Every form keeps the Tinkerer's work: stitched seams and amber nodes, and a strand of twelve coloured beads down the spine,
one for each heritage whose blood went into the vats (all but the Dawnborn and the Divine-born).

Writes textures/entity/heritage/bioform/<palette>_<form>_skin.png, _glow.png (the body, on the 64x64 player layout),
<palette>_<form>_parts.png, _parts_glow.png (the model parts) and <palette>_tail.png (the absorbing tail drawn in the
world), and prints the parts table for client/BioForms.java. Everything is our own, drawn from plain ramps and noise.
Run from the kingdoms folder:  python -I tools/gen_bio_forms.py
"""
import math
import os
import sys
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "jaymoskingdoms",
                   "textures", "entity", "heritage", "bioform")


def hexc(v, a=255):
    return ((v >> 16) & 255, (v >> 8) & 255, v & 255, a)


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (a[3] if len(a) > 3 else 255,)


def shade(c, f):
    return tuple(max(0, min(255, round(c[i] * f))) for i in range(3)) + (c[3] if len(c) > 3 else 255,)


def hsh(*v):
    """A repeatable hash of integers to 0..1."""
    h = 2166136261
    for x in v:
        h = ((h ^ (int(x) & 0xFFFFFFFF)) * 16777619) & 0xFFFFFFFF
    h ^= h >> 13
    h = (h * 1274126177) & 0xFFFFFFFF
    return (h & 0xFFFF) / 65535.0


# ---- palettes: what each role of the body is painted in
PALETTES = {
    "seamed": dict(light=0x9ED64A, mid=0x4FA845, dark=0x2C6E3A, spot=0x15180F, accent=0xF0902A, accent_dark=0xB4581A,
                   black=0x17171C, face=0xF6F8F0, lip=0x6E4A78, sclera0=0xF2C0CE, sclera=0xF4F4F0, iris1=0x6FB6F0,
                   iris2=0xE85AA8, stripe=0x7C3FA8, light2=0xB8E39A, spot2=0x3E7E3A, membrane=0x2C5A34, node=0xFFB040),
    "brassbound": dict(light=0xD9A26A, mid=0xB07440, dark=0x6E4424, spot=0x2A1A10, accent=0x4FB8A2, accent_dark=0x2C7A6C,
                       black=0x26201C, face=0xF0DCC4, lip=0x7A3E2E, sclera0=0xF0D8A8, sclera=0xF6F0E6, iris1=0x4FC8B0,
                       iris2=0xF07A3A, stripe=0x2C8A7A, light2=0xE8C08E, spot2=0x8A5A30, membrane=0x5A3A22, node=0xFFB040),
    "opaline": dict(light=0xDDEEE6, mid=0xB8D8CC, dark=0x8C9EC0, spot=0x4A4E6E, accent=0xEFA0B0, accent_dark=0xB86A86,
                    black=0x2A2640, face=0xF6F4F8, lip=0x9A6A9A, sclera0=0xE8D8F4, sclera=0xFBFBFF, iris1=0x8AC8F0,
                    iris2=0xC878E8, stripe=0x8A6AD0, light2=0xF0F6FA, spot2=0xA6A0D0, membrane=0x6A6E9A, node=0xC8F0FF),
}
# the twelve heritages' beads down the spine (not the Dawnborn's, not the Divine-born's)
DNA = [0xC8B8A0, 0x8E8E92, 0x3FB8C8, 0x6FC04A, 0x4E9A3A, 0xF06A2A, 0xBFE6FF, 0x8A2A3A, 0xFFD84A, 0x7FD0FF, 0xB070E0, 0xC8402A]


def pal(name):
    return {k: hexc(v) for k, v in PALETTES[name].items()}


# ---- the player skin layout: (part, face) -> (x, y, w, h) on the 64x64 sheet (classic arms; slim arms read a strip
# 3 wide of the same, so arm patterns run round the arm rather than across a face)
def box_faces(u, v, w, h, d):
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}


SKIN = {"head": box_faces(0, 0, 8, 8, 8), "body": box_faces(16, 16, 8, 12, 4), "rarm": box_faces(40, 16, 4, 12, 4),
        "larm": box_faces(32, 48, 4, 12, 4), "rleg": box_faces(0, 16, 4, 12, 4), "lleg": box_faces(16, 48, 4, 12, 4)}


def paint_skin(fn, glowfn):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    glow = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for part, faces in SKIN.items():
        for face, (x0, y0, w, h) in faces.items():
            for j in range(h):
                for i in range(w):
                    c = fn(part, face, i, j, w, h)
                    if c is not None:
                        img.putpixel((x0 + i, y0 + j), c)
                    g = glowfn(part, face, i, j, w, h)
                    if g is not None:
                        glow.putpixel((x0 + i, y0 + j), g)
    return img, glow


def around(face, i, w):
    """A column number running round a limb (front, left, back, right), so patterns wrap."""
    order = {"front": 0, "left": 1, "back": 2, "right": 3}
    if face not in order:
        return i
    return order[face] * 4 + i


# ---- the bodies -------------------------------------------------------------------------------------------------------

def carapace(p, part, face, i, j, w, h, form, seed):
    """The green spotted hide with segment bands (forms 0 and 1) or the smooth spotted skin (form 2)."""
    col = around(face, i, w)
    if form == 2:
        base = mix(p["light2"], p["light"], 0.3 + 0.15 * hsh(seed, col // 2, j // 2))
        if hsh(seed + 7, col, j) > 0.83 and hsh(seed + 7, col - 1, j) <= 0.83 and hsh(seed + 7, col, j - 1) <= 0.83:
            base = mix(p["spot2"], p["dark"], 0.35 * hsh(seed, col, j))
        return base
    t = 0.5 + 0.5 * math.sin((col + seed) * 0.9) * 0.4 + 0.3 * (hsh(seed, col, j) - 0.5)
    base = mix(p["mid"], p["light"], t)
    if hsh(seed + 3, col // 2, (j + seed) // 2) > (0.74 if form == 0 else 0.8):
        base = mix(p["spot"], p["dark"], 0.25 * hsh(seed, col, j))
    if j % 4 == 3:
        base = shade(base, 0.72)
    return base


def seams(p, part, face, i, j, w, h, form):
    """The Tinkerer's stitches: a dark seam with cross-stitches round the upper arms and thighs, down the chest's side."""
    if part in ("rarm", "larm", "rleg", "lleg") and face in ("front", "left", "back", "right") and j == (3 if form == 2 else 4):
        return shade(p["black"], 1.2)
    if part in ("rarm", "larm", "rleg", "lleg") and face in ("front", "left", "back", "right") and j in (3, 5) and form < 2 \
            and around(face, i, w) % 3 == 0:
        return shade(p["black"], 1.6)
    return None


def node_at(part, face, i, j, form):
    """Amber nodes sunk in the hide (they glow): on the seams, the chest and the brow."""
    if part in ("rarm", "larm") and face == "front" and j == (3 if form == 2 else 4) and i == 1:
        return True
    if part in ("rleg", "lleg") and face == "front" and j == (3 if form == 2 else 4) and i == 2:
        return True
    if part == "body" and face == "front" and (i, j) in ((1, 2), (6, 2)) and form < 2:
        return True
    if part == "body" and face == "front" and (i, j) in ((3, 6), (4, 6)) and form == 2:
        return True
    return False


def body_fn(pname, form):
    p = pal(pname)
    seed = {"seamed": 3, "brassbound": 11, "opaline": 19}[pname] + form * 5

    def fn(part, face, i, j, w, h):
        c = carapace(p, part, face, i, j, w, h, form, seed + sum(map(ord, part)) % 7)
        s = seams(p, part, face, i, j, w, h, form)
        if s is not None:
            c = s
        if node_at(part, face, i, j, form):
            c = p["node"]
        # ---- head
        if part == "head":
            return head(p, face, i, j, form, c, seed)
        # ---- body
        if part == "body":
            if face == "front":
                if form == 0:
                    # black plates on the chest, an orange segmented belly
                    if j < 5 and (i < 3 or i > 4) and not (j == 0):
                        c = shade(p["black"], 1.0 + 0.25 * (j % 2))
                    if j >= 6:
                        c = mix(p["accent"], p["accent_dark"], (j % 2) * 0.6 + 0.1 * hsh(i, j))
                        if i in (0, 7):
                            c = shade(p["dark"], 0.8)
                    if i in (3, 4) and j < 6:
                        # a sternum ridge
                        c = shade(p["mid"], 0.9 + 0.1 * (j % 2))
                elif form == 1:
                    if j < 5 and (i < 3 or i > 4):
                        c = shade(p["black"], 1.15)
                    if 6 <= j <= 11:
                        # an orange belly with muscle lines
                        c = mix(p["accent"], p["accent_dark"], 0.15 + 0.5 * (j % 2 == 1 and i in (3, 4)))
                        if i in (0, 7):
                            c = shade(p["mid"], 0.8)
                else:
                    if j < 4:
                        c = shade(p["black"], 1.1 + 0.15 * (i in (3, 4)))
                    if j >= 4 and i in (3, 4) and j % 2 == 1:
                        c = shade(c, 0.88)
            if face == "back":
                if form == 0:
                    if 2 <= i <= 5 and j >= 7:
                        c = mix(p["accent"], p["accent_dark"], 0.3 * (j % 2))
                elif form == 2 and j < 3:
                    c = shade(p["black"], 1.1)
                # the strand of beads down the spine, one for each heritage that went into the vats
                if i in (3, 4) and 0 < j < 12:
                    c = shade(p["black"], 1.3)
                    if i == 3 and j % 1 == 0:
                        c = hexc(DNA[(j - 1) % len(DNA)])
            if face in ("right", "left") and form == 2 and j < 3:
                c = shade(p["black"], 1.1)
            if face == "top" and form == 2:
                c = shade(p["black"], 1.1)
            return c
        # ---- arms
        if part in ("rarm", "larm"):
            if form == 0 and j >= 9:
                # clawed hands: dark, three claws at the fingertips
                c = shade(p["dark"], 0.85)
                if j == 11 and face == "front" and i % 2 == 0:
                    c = p["sclera"]
            if face == "bottom" and form == 0:
                c = shade(p["dark"], 0.7)
            if form == 1 and j >= 5:
                c = shade(p["black"], 1.0 + 0.2 * (hsh(i, j) > 0.7))
            if form == 2 and j < 2 and face in ("front", "back", "right", "left"):
                c = shade(p["black"], 1.1)
            if face == "top" and form >= 1:
                c = shade(p["black"], 1.1)
            return c
        # ---- legs
        if part in ("rleg", "lleg"):
            if form == 0:
                if j >= 9:
                    c = shade(p["dark"], 0.8)
                    if j == 11 and face == "front" and i in (0, 3):
                        c = p["sclera"]
                if j < 3 and face == "front":
                    c = mix(p["accent"], p["accent_dark"], 0.3)
            if form == 1 and j >= 5:
                c = shade(p["black"], 1.0 + 0.2 * (hsh(i, j, 3) > 0.7))
            if form == 2 and j >= 10:
                # shoe-like feet
                c = shade(p["black"], 1.2 if j == 10 else 1.0)
            if form == 1 and j >= 10:
                c = shade(p["black"], 0.85)
            return c
        return c

    def glowfn(part, face, i, j, w, h):
        if node_at(part, face, i, j, form):
            return p["node"]
        if part == "head" and face == "front":
            e = eye(p, i, j, form)
            if e is not None:
                return e[1]
        if part == "body" and face == "back" and i == 3 and 0 < j < 12:
            return hexc(DNA[(j - 1) % len(DNA)])
        return None

    return fn, glowfn


def eye(p, i, j, form):
    """(colour, glow) of the eye pixel at (i, j) on the head's front, or None. Two eyes, 2 wide each, on row 3 (row 4 too)."""
    left, right = (1, 2), (5, 6)
    if form == 0:
        # slit pupils on pink, no iris, set wide and angled
        if j == 3 and (i in left or i in right):
            slit = i in (2, 5)
            return (p["black"] if slit else p["sclera0"]), (hexc(0x2A0A10) if slit else p["sclera0"])
        if j == 2 and i in (1, 6):
            return p["sclera0"], p["sclera0"]
        return None
    if j == 3 and (i in left or i in right):
        iris = i in (2, 5)
        col = p["iris1"] if form == 1 else p["iris2"]
        return (col if iris else p["sclera"]), (col if iris else None)
    return None


def head(p, face, i, j, form, c, seed):
    if face == "front":
        if form == 0:
            # a hard insect face: carapace with a black brow plate, a beak (a part) over an orange mouth slit
            c = mix(p["mid"], p["light"], 0.3 + 0.3 * hsh(seed, i, j))
            if j <= 1 and 2 <= i <= 5:
                c = shade(p["black"], 1.1)
            if j == 6 and 2 <= i <= 5:
                c = p["accent_dark"]
            if j == 7:
                c = shade(p["dark"], 0.9)
            if i in (0, 7) and j >= 4:
                c = shade(p["dark"], 1.0)
        elif form == 1:
            c = mix(p["face"], p["light"], 0.55)
            if j <= 1:
                c = mix(p["mid"], p["light"], 0.4)
            if j == 0 and i in (3, 4):
                c = shade(p["black"], 1.2)
            # no nose; big lips
            if j in (5, 6) and 1 <= i <= 6:
                c = p["lip"] if not (j == 5 and i in (1, 6)) else c
                if j == 5 and 2 <= i <= 5:
                    c = shade(p["lip"], 1.25)
            if i in (0, 7):
                c = mix(p["mid"], p["light"], 0.3)
        else:
            c = p["face"]
            if j <= 0:
                c = mix(p["light2"], p["light"], 0.4)
            # a thin stripe down each cheek from under the eye
            if i in (1, 6) and 4 <= j <= 6:
                c = p["stripe"]
            # a nose, a small mouth
            if j == 4 and i in (3, 4):
                c = shade(p["face"], 0.88)
            if j == 6 and 3 <= i <= 4:
                c = shade(p["lip"], 1.1)
        e = eye(p, i, j, form)
        if e is not None:
            c = e[0]
        return c
    if face == "top":
        if form == 0:
            return shade(p["black"], 1.1) if 2 <= i <= 5 and j >= 4 else c
        return mix(p["light"], p["mid"], 0.4) if form == 1 else mix(p["light2"], p["light"], 0.4 * hsh(i, j))
    if face == "back":
        if form == 0:
            return mix(p["accent"], p["accent_dark"], 0.5) if j >= 5 and 2 <= i <= 5 else c
        return c
    if face in ("right", "left"):
        if form == 2 and j >= 5 and ((face == "right" and i == 7) or (face == "left" and i == 0)):
            return p["stripe"]
        if form >= 1 and j >= 6:
            return mix(p["face"], p["light"], 0.6) if form == 1 else p["face"]
        return c
    return c


# ---- the parts: (attach, name, pivot, rotation, [(w, h, d, x, y, z, paint)], child-of) ------------------------------------
# paint: a role name the part's faces are painted in (ramped, banded)

def parts_for(form):
    P = []
    if form == 0:
        for s in (-1, 1):
            n = "r" if s < 0 else "l"
            P.append(("head", "horn_" + n, (s * 2.4, -7.0, -1.5), (-0.55, 0.0, s * 0.42), [(2, 7, 2, -1, -7, -1, "horn")], None))
            P.append(("head", "horn_tip_" + n, (0.0, -6.6, 0.0), (-0.45, 0.0, s * 0.1), [(1, 6, 1, -0.5, -6, -0.5, "horn")], "horn_" + n))
            P.append(("body", "wing_" + n, (s * 0.6, 0.6, 2.05), (0.14, 0.0, -s * 0.07), [(4, 11, 1, -4 if s < 0 else 0, 0, 0, "elytra")], None))
            arm = "rarm" if s < 0 else "larm"
            P.append((arm, "claw_" + n, (0.0, 0.0, 0.0), (0.0, 0.0, 0.0),
                      [(1, 2, 1, (-2.6 if s < 0 else 1.6), 9.5, -2.4, "claw"), (1, 2, 1, (-0.8 if s < 0 else -0.2), 9.5, -2.4, "claw")], None))
            leg = "rleg" if s < 0 else "lleg"
            P.append((leg, "toes_" + n, (0.0, 0.0, 0.0), (0.0, 0.0, 0.0),
                      [(1, 1, 2, -2.2, 11, -3.4, "claw"), (1, 1, 2, -0.5, 11, -3.6, "claw"), (1, 1, 2, 1.2, 11, -3.4, "claw"),
                       (1, 2, 1, -0.5, 9.5, 2, "claw")], None))
        P.append(("head", "beak", (0.0, -2.6, -4.0), (0.28, 0.0, 0.0), [(3, 2, 3, -1.5, -1, -3, "beak"), (2, 1, 2, -1, 1, -2.4, "beak")], None))
        P.append(("head", "brow", (0.0, -5.4, -4.0), (0.0, 0.0, 0.0), [(8, 1, 1, -4, -0.5, -0.6, "black")], None))
        P.append(("body", "ridge", (0.0, 0.0, 2.0), (0.0, 0.0, 0.0), [(1, 10, 1, -0.5, 0.8, 0, "spine")], None))
        P.append(("rarm", "growth", (0.0, 0.0, 0.0), (0.0, 0.0, 0.0), [(3, 2, 3, -3.4, -3.0, -1.0, "growth"), (2, 2, 2, -2.8, -4.4, 0.2, "growth")], None))
        P.append(("larm", "spur", (0.0, 0.0, 0.0), (0.0, 0.0, 0.0), [(1, 3, 1, 1.6, 4.0, 1.6, "claw")], None))
    elif form == 1:
        for s in (-1, 1):
            n = "r" if s < 0 else "l"
            P.append(("head", "crown_" + n, (s * 3.2, -7.4, -0.6), (-0.12, 0.0, s * 0.3), [(2, 6, 2, -1, -6, -1, "horn")], None))
            P.append(("head", "crown_tip_" + n, (0.0, -5.6, 0.0), (0.05, 0.0, -s * 0.42), [(1, 4, 1, -0.5, -4, -0.5, "horn")], "crown_" + n))
            P.append(("head", "crown_back_" + n, (s * 2.0, -7.6, 2.4), (-0.35, 0.0, s * 0.18), [(1, 4, 1, -0.5, -4, -0.5, "horn")], None))
            arm = "rarm" if s < 0 else "larm"
            P.append((arm, "pauldron_" + n, (0.0, 0.0, 0.0), (0.0, 0.0, 0.0),
                      [((5, 3, 5, -3.5, -2.7, -2.5, "plate") if s < 0 else (5, 3, 5, -1.5, -2.7, -2.5, "plate"))], None))
        P.append(("head", "ball", (0.0, -7.6, -4.0), (0.0, 0.0, 0.0), [(2, 2, 1, -1, -0.4, -0.7, "black")], None))
        P.append(("body", "ridge", (0.0, 0.0, 2.0), (0.0, 0.0, 0.0), [(2, 6, 1, -1, 0.5, 0, "plate")], None))
    else:
        for s in (-1, 1):
            n = "r" if s < 0 else "l"
            P.append(("head", "crown_" + n, (s * 3.3, -7.4, -0.4), (-0.1, 0.0, s * 0.22), [(1, 6, 1, -0.5, -6, -0.5, "horn")], None))
            P.append(("head", "crown_tip_" + n, (0.0, -5.6, 0.0), (0.05, 0.0, -s * 0.38), [(1, 3, 1, -0.5, -3, -0.5, "horn")], "crown_" + n))
            P.append(("head", "crown_back_" + n, (s * 1.8, -7.6, 2.6), (-0.3, 0.0, s * 0.15), [(1, 3, 1, -0.5, -3, -0.5, "horn")], None))
            P.append(("body", "wing_" + n, (s * 0.5, 0.2, 2.05), (0.07, 0.0, -s * 0.11), [(5, 17, 1, -5 if s < 0 else 0, 0, 0, "wing")], None))
        P.append(("head", "ball", (0.0, -7.6, -4.0), (0.0, 0.0, 0.0), [(2, 2, 1, -1, -0.4, -0.7, "black")], None))
        P.append(("body", "stinger", (0.0, 9.5, 2.0), (0.5, 0.0, 0.0), [(2, 2, 2, -1, -1, 0, "tail"), (1, 1, 2, -0.5, -0.5, 1.8, "sting")], None))
    return P


# the idle tail (forms 0 and 1): segments, like the Warrior-born's, from the small of the back
TAILS = {0: dict(length=2.8, thick=2.6, taper=0.93, sway=0.12, bends=[-0.72, -0.12, -0.08, 0.0, 0.08, 0.14, 0.22, 0.3, 0.38, 0.42]),
         1: dict(length=2.8, thick=3.0, taper=0.9, sway=0.16, bends=[-1.0, 0.1, 0.25, 0.35, 0.45, 0.5, 0.5])}
SEGMENT = (4, 4, 3)   # the tail segment's box (w, h, d), scaled per tail
STING = [(2, 2, 3, -1, -1, 0), (1, 1, 3, -0.5, -0.5, 2.6)]


def part_color(p, paint, form, face, i, j, w, h, k):
    """The colour of a part's texel: per paint role, lit from the top, banded along its length."""
    if paint == "horn":
        base = mix(p["black"], p["dark"], 0.35) if form == 0 else mix(p["light"], p["mid"], 0.3 + 0.2 * (j / max(1, h)))
        if form == 0 and j < 2:
            base = mix(p["accent_dark"], p["black"], 0.3)
        if form >= 1 and j >= h - 1:
            base = shade(p["black"], 1.2)
        return shade(base, 1.0 + 0.12 * (face == "top") - 0.12 * (face in ("back", "right")))
    if paint == "beak":
        return shade(mix(p["accent"], p["accent_dark"], 0.3 + 0.4 * (j % 2)), 1.05 if face in ("top", "front") else 0.85)
    if paint in ("elytra", "wing") and face in ("front", "back") and outside_wing(i, j, w, h, face, paint):
        return (0, 0, 0, 0)
    if paint == "elytra":
        base = mix(p["dark"], p["mid"], 0.35 + 0.25 * hsh(i, j, k))
        if i == 0 or i == w - 1:
            base = mix(p["light"], p["mid"], 0.3)
        if j % 3 == 2:
            base = shade(base, 0.82)
        if hsh(i // 1, j // 2, k + 5) > 0.82:
            base = mix(p["spot"], p["dark"], 0.3)
        return base
    if paint == "wing":
        base = mix(p["membrane"], p["dark"], 0.2 + 0.25 * hsh(i, j // 3, k))
        if i == 0 or j == 0 or (j > 2 and (i * 3 + j) % 7 == 0):
            base = mix(p["light"], p["mid"], 0.5)
        if face not in ("front", "back"):
            base = shade(p["black"], 1.3)
        return base
    if paint == "claw":
        return shade(mix(p["sclera"], p["black"], 0.35 + 0.4 * (j / max(1, h))), 0.95)
    if paint == "spine":
        c = shade(p["black"], 1.3)
        return hexc(DNA[j % len(DNA)]) if face in ("back",) and j < 10 else c
    if paint == "growth":
        return mix(p["accent"], p["mid"], 0.4 + 0.4 * hsh(i, j, k)) if hsh(i, j, k + 1) > 0.3 else shade(p["dark"], 0.8)
    if paint == "plate":
        base = mix(p["mid"], p["light"], 0.4 + 0.2 * hsh(i, j, k))
        if hsh(i // 2, j, k + 9) > 0.75:
            base = mix(p["spot"], p["dark"], 0.3)
        if j == h - 1 and face not in ("top", "bottom"):
            base = shade(p["black"], 1.1)
        return shade(base, 1.1 if face == "top" else 0.95)
    if paint == "black":
        return shade(p["black"], 1.25 if face in ("top", "front") else 1.0)
    if paint == "tail":
        if form == 1:
            band = mix(p["accent"], p["accent_dark"], 0.3) if (j // 2) % 2 == 0 else shade(p["black"], 1.15)
        else:
            band = mix(p["mid"], p["light"], 0.3 + 0.3 * hsh(i, j, k))
            if j % 3 == 2:
                band = shade(band, 0.75)
            if face == "bottom":
                band = mix(p["accent"], p["accent_dark"], 0.4)
            if hsh(i, j, k + 3) > 0.84:
                band = mix(p["spot"], p["dark"], 0.3)
        return band
    if paint == "sting":
        return shade(mix(p["sclera"], p["accent_dark"], 0.25 + 0.5 * (j / max(1, h))), 1.0)
    return (255, 0, 255, 255)


def outside_wing(i, j, w, h, face, paint):
    """Whether a wing texel lies outside the wing's shape: rounded at the shoulder, narrowing to a point at the bottom."""
    # columns counted from the wing's outer edge (the back face is mirrored)
    edge = i if face == "back" else w - 1 - i
    left_wing = None
    from_bottom = h - 1 - j
    if from_bottom < 4:
        # the point: the outer columns end first
        return edge < (4 - from_bottom) * w // 4
    if j == 0 and edge == 0:
        return True
    return False


class Packer:
    """Lays boxes out on the parts sheet, row by row."""

    def __init__(self, width):
        self.width, self.x, self.y, self.row = width, 0, 0, 0

    def place(self, w, h, d):
        bw, bh = 2 * d + 2 * w, d + h
        bw, bh = math.ceil(bw), math.ceil(bh)
        if self.x + bw > self.width:
            self.x, self.y, self.row = 0, self.y + self.row, 0
        at = (self.x, self.y)
        self.x += bw
        self.row = max(self.row, bh)
        return at


def uv_faces(u, v, w, h, d):
    w, h, d = math.ceil(w), math.ceil(h), math.ceil(d)
    return box_faces(u, v, w, h, d)


def build(pname, form):
    """The parts sheet and its layout (every box's texture offset) for one palette and form."""
    p = pal(pname)
    sheet = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    glow = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    pack = Packer(64)
    layout = []
    k = 0
    for attach, name, pivot, rot, boxes, parent in parts_for(form):
        placed = []
        for (w, h, d, x, y, z, paint) in boxes:
            u, v = pack.place(w, h, d)
            placed.append((u, v, w, h, d, x, y, z, paint))
            for face, (x0, y0, fw, fh) in uv_faces(u, v, w, h, d).items():
                for j in range(fh):
                    for i in range(fw):
                        sheet.putpixel((x0 + i, y0 + j), part_color(p, paint, form, face, i, j, fw, fh, k))
                        if paint == "spine" and face == "back" and j < 10:
                            glow.putpixel((x0 + i, y0 + j), hexc(DNA[j % len(DNA)]))
            k += 1
        layout.append((attach, name, pivot, rot, placed, parent))
    tail_uv = None
    if form in TAILS:
        w, h, d = SEGMENT
        u, v = pack.place(w, h, d)
        tail_uv = (u, v)
        for face, (x0, y0, fw, fh) in uv_faces(u, v, w, h, d).items():
            for j in range(fh):
                for i in range(fw):
                    sheet.putpixel((x0 + i, y0 + j), part_color(p, "tail", form, face, i, j, fw, fh, 40))
    sting_uv = []
    if form in TAILS:
        for (w, h, d, x, y, z) in STING:
            u, v = pack.place(w, h, d)
            sting_uv.append((u, v))
            for face, (x0, y0, fw, fh) in uv_faces(u, v, w, h, d).items():
                for j in range(fh):
                    for i in range(fw):
                        sheet.putpixel((x0 + i, y0 + j), part_color(p, "sting", form, face, i, j, fw, fh, 41))
    return sheet, glow, layout, tail_uv, sting_uv


def tail_strip(pname):
    """The absorbing tail drawn in the world: 16 wide round it, 64 along it (body end at the top), the stinger's sheath
    from row 48 down."""
    p = pal(pname)
    img = Image.new("RGBA", (16, 64), (0, 0, 0, 0))
    for j in range(64):
        for i in range(16):
            if j < 48:
                band = mix(p["mid"], p["light"], 0.3 + 0.3 * hsh(i, j, 77))
                if j % 4 == 3:
                    band = shade(band, 0.72)
                if i in (6, 7, 8, 9):
                    band = mix(p["accent"], p["accent_dark"], 0.35 + 0.3 * (j % 4 == 3))
                if hsh(i, j // 2, 78) > 0.86:
                    band = mix(p["spot"], p["dark"], 0.3)
            else:
                # the sheath that opens over the foe: wet, ridged, darker inside
                band = mix(p["accent_dark"], p["dark"], 0.3 + 0.3 * hsh(i, j, 79))
                if (j - 48) % 3 == 0:
                    band = shade(band, 0.75)
                if i % 4 == 0:
                    band = mix(p["accent"], band, 0.5)
            img.putpixel((i, j), band)
    return img


def java_table(layout, tail_uv, sting_uv, form):
    """The parts as Java lines for client/BioForms.java."""
    out = []
    for attach, name, pivot, rot, placed, parent in layout:
        boxes = ", ".join("box(%d, %d, %sF, %sF, %sF, %sF, %sF, %sF)" % (u, v, fx(x), fx(y), fx(z), fx(w), fx(h), fx(d))
                          for (u, v, w, h, d, x, y, z, paint) in placed)
        out.append('\t\t\tpart(%d, Attach.%s, "%s", %s, %sF, %sF, %sF, %sF, %sF, %sF, %s),' % (
            form, {"head": "HEAD", "body": "BODY", "rarm": "RIGHT_ARM", "larm": "LEFT_ARM", "rleg": "RIGHT_LEG", "lleg": "LEFT_LEG"}[attach],
            name, "null" if parent is None else '"%s"' % parent, fx(pivot[0]), fx(pivot[1]), fx(pivot[2]), fx(rot[0]), fx(rot[1]), fx(rot[2]), boxes))
    if tail_uv:
        out.append("\t\t\t// tail segment uv %d,%d; stinger %s" % (tail_uv[0], tail_uv[1], sting_uv))
    return "\n".join(out)


def fx(v):
    s = ("%.3f" % v).rstrip("0").rstrip(".")
    return s + (".0" if "." not in s else "")


def main():
    os.makedirs(OUT, exist_ok=True)
    tables = []
    for pname in PALETTES:
        for form in range(3):
            fn, gfn = body_fn(pname, form)
            skin, glow = paint_skin(fn, gfn)
            skin.save(os.path.join(OUT, "%s_%d_skin.png" % (pname, form)))
            glow.save(os.path.join(OUT, "%s_%d_glow.png" % (pname, form)))
            sheet, pglow, layout, tail_uv, sting_uv = build(pname, form)
            sheet.save(os.path.join(OUT, "%s_%d_parts.png" % (pname, form)))
            pglow.save(os.path.join(OUT, "%s_%d_parts_glow.png" % (pname, form)))
            if pname == "seamed":
                tables.append(java_table(layout, tail_uv, sting_uv, form))
        tail_strip(pname).save(os.path.join(OUT, "%s_tail.png" % pname))
    if "--java" in sys.argv:
        print("\n".join(tables))


if __name__ == "__main__":
    main()
