"""Our horses (0.7.302): the shapes of every build (tools/horse_rig.py) written out for BreedHorseModel, and every coat
painted for them, all original and procedural. It replaces tools/gen_moc_horses.py, which painted our coats over Mo'
Creatures' horse.

- assets/jaymoskingdoms/horse/builds.json: each build's parts (bones with the old horse's names and pivots, and the
  pieces hung from them) and where each box lies in its texture.
- textures/entity/breed_horse/<breed>/<coat>.png: the 36 breed coats (specs from tools/gen_equestrian.py), the
  nightmare (and its flicker), the bat horse, and the skeleton (bone, sooty, mossy) and zombie (rotten, pale, mottled)
  horses, each at twice the model's texture size. The vanilla saddle, headstall, bit, reins and stirrups the model
  draws are painted on every coat.
- textures/entity/breed_horse/armor/<namespace>/<asset>/<build>.png: horse armour on each build (iron, gold, diamond,
  copper, netherite, leather to be dyed, and our chain and plate barding), drawn over the coat on a slightly larger model.

Patterns (dapples, pinto, leopard spots, the dorsal stripe, socks) are laid in the horse's own space, so they run on
from one box to the next. Seeded, so every run draws the same pixels. Previews go to /tmp/claude-0/horses/.
Run from kingdoms/: python3 -I tools/gen_horses.py"""
import colorsys
import json
import math
import os
import random
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.dont_write_bytecode = True
sys.path.insert(0, HERE)
import gen_equestrian as ge  # noqa: E402  (the coat specs)
import horse_preview as hp  # noqa: E402
import horse_rig as hr  # noqa: E402

ASSETS = os.path.normpath(os.path.join(HERE, "..", "src/main/resources/assets/jaymoskingdoms"))
OUT = os.path.join(ASSETS, "textures/entity/breed_horse")
PREVIEW = "/tmp/claude-0/horses"
S = 2  # pixels a texel

# ---- noise in the horse's own space ---------------------------------------------------------------------------------------
def h3(ix, iy, iz, seed):
    n = (ix * 73856093) ^ (iy * 19349663) ^ (iz * 83492791) ^ (seed * 2654435761)
    n = (n ^ (n >> 13)) * 1274126177
    return ((n ^ (n >> 16)) & 0xFFFFFF) / float(0xFFFFFF)


def vnoise3(p, cell, seed):
    """Smooth value noise in [0, 1], blobs about `cell` model pixels across."""
    x, y, z = (v / cell for v in p)
    ix, iy, iz = math.floor(x), math.floor(y), math.floor(z)
    fx, fy, fz = x - ix, y - iy, z - iz
    fx, fy, fz = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy), fz * fz * (3 - 2 * fz)
    out = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (fx if dx else 1 - fx) * (fy if dy else 1 - fy) * (fz if dz else 1 - fz)
                out += w * h3(ix + dx, iy + dy, iz + dz, seed)
    return out


def nearest(p, spacing, seed):
    """Distance to the nearest of a jittered grid of points `spacing` apart, and that point's own random number."""
    x, y, z = (v / spacing for v in p)
    ix, iy, iz = math.floor(x), math.floor(y), math.floor(z)
    best, tag = 9.0, 0.0
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                cx, cy, cz = ix + dx, iy + dy, iz + dz
                px_ = cx + h3(cx, cy, cz, seed)
                py_ = cy + h3(cx, cy, cz, seed + 1)
                pz_ = cz + h3(cx, cy, cz, seed + 2)
                d = math.sqrt((x - px_) ** 2 + (y - py_) ** 2 + (z - pz_) ** 2)
                if d < best:
                    best, tag = d, h3(cx, cy, cz, seed + 3)
    return best * spacing, tag


def clamp(c):
    return tuple(max(0, min(255, int(round(v)))) for v in c)


def shade(c, by):
    return clamp(tuple(v + by for v in c))


def scale(c, f):
    return clamp(tuple(v * f for v in c))


def mix(a, b, t):
    return clamp(tuple(a[k] * (1 - t) + b[k] * t for k in range(3)))


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


# ---- every pixel of a build's texture: which box, which face, where on the horse --------------------------------------------
class Px:
    __slots__ = ("part", "cube", "face", "I", "J", "pw", "ph", "s", "t", "q", "w", "X", "Y")


def pixels(parts):
    chains = hr.transforms(parts)
    out = []
    for p in parts:
        for c in p.cubes:
            if c.share is not None:
                continue
            for face, (u, v, fw, fh) in hr.faces(c).items():
                pw, ph = fw * S, fh * S
                for J in range(ph):
                    for I in range(pw):
                        x = Px()
                        x.part, x.cube, x.face, x.I, x.J, x.pw, x.ph = p.name, c, face, I, J, pw, ph
                        x.s, x.t = (I + 0.5) / pw, (J + 0.5) / ph
                        x.q = hr.local(c, face, x.s, x.t)
                        x.w = hr.apply(chains[p.name], x.q)
                        x.X, x.Y = u * S + I, v * S + J
                        out.append(x)
    return out


def light(x, c, strength=1.0):
    """The shading baked into a box face: lit from above, darker down the sides, darkest underneath."""
    f = x.face
    if f == "top":
        k = 1.08
    elif f == "bottom":
        k = 0.8
    else:
        k = 1.05 - 0.17 * x.t
        if f in ("front", "back"):
            k -= 0.03
    return scale(c, 1 + (k - 1) * strength)


def along(x):
    """For a hair box: how far along its strands this pixel is, 0 at the roots, 1 at the ends."""
    c = x.cube
    k = "xyz".index(c.axis)
    u = (x.q[k] - c.pos[k]) / c.size[k] if c.size[k] else 0.5
    return u if c.tip > 0 else 1 - u


def strand(x, seed):
    """Which strand of hair a pixel lies on (the same strand on every face it shows on)."""
    c = x.cube
    k = "xyz".index(c.axis)
    other = [x.q[i] for i in range(3) if i != k]
    return h3(int(math.floor(other[0] * 2 + 0.001)), int(math.floor(other[1] * 2 + 0.001)), k, seed)


def ragged(x, seed, start=0.78, holes=0.55):
    """True where a hair box's ends are cut ragged (left clear)."""
    u = along(x)
    if u < start:
        return False
    return h3(x.X, x.Y, 7, seed) < holes * (u - start) / (1 - start) + (0.15 if u > 0.95 else 0)


TACK_LEATHER = (112, 70, 40)
TACK_DARK = (78, 46, 26)
IRON = (168, 170, 176)
BRASS = (204, 164, 70)


def tack(x):
    """The saddle, stirrups, headstall, bit and reins the model draws for a vanilla saddle (the same on every coat)."""
    c, f = x.cube, x.face
    k = c.kind
    q = x.q
    if k == "saddle":
        base = TACK_LEATHER if c.region is None else TACK_DARK
        if c.region is None and f == "top":
            edge = x.I < 2 or x.J < 2 or x.I >= x.pw - 2 or x.J >= x.ph - 2
            stitch = (x.I in (3, x.pw - 4) or x.J in (3, x.ph - 4)) and (x.I + x.J) % 2 == 0 and not edge
            col = shade(base, -26) if edge else shade(base, 22) if stitch else shade(base, 8 * math.sin(x.s * 3.1))
        elif c.region is not None and f == "top":
            col = shade(base, 18 if x.J < 2 else 0)
        else:
            col = shade(base, -16)
        return light(x, shade(col, int(4 * (h3(x.X, x.Y, 1, 3) - 0.5)))) + (255,)
    if k == "strap":
        col = shade(TACK_DARK, 10 if x.I % 2 == 0 else 0)
        return light(x, col) + (255,)
    if k == "iron":
        col = IRON if f in ("top", "a", "b") else shade(IRON, -40)
        if f in ("a", "b") and 0 < x.I < x.pw - 1 and 0 < x.J < x.ph - 1:
            col = shade(IRON, -70)  # the ring's hole
        return light(x, col) + (255,)
    if k == "rein":
        sag = 1.0 + 3.0 * math.sin(math.pi * x.s)
        if abs(x.J + 0.5 - sag) < 1.0:
            return shade(TACK_DARK, 8 if x.J % 2 else 0) + (255,)
        return None
    if k == "headstall":
        # head-space straps: noseband, cheek pieces, browband/crown and throatlatch
        z, y = q[2], q[1]
        nose = -5.2 <= z <= -4.1
        cheek = 1.0 <= z <= 2.1 and f in ("a", "b")
        crown = 3.4 <= z <= 4.5 and f in ("a", "b", "top", "bottom")
        brow = f == "top" and 2.1 <= z <= 3.4 and abs(q[0]) > 1.6
        if not (nose or cheek or crown or brow):
            return None
        col = TACK_DARK
        if nose and f in ("a", "b") and abs(z + 4.65) < 0.6 and y > -8.5:
            col = BRASS  # the noseband's rings
        if (cheek and -7.0 <= y <= -6.0) or (crown and f in ("a", "b") and -10 <= y <= -9.0):
            col = BRASS
        return light(x, shade(col, 6 if (x.I + x.J) % 3 == 0 else 0)) + (255,)
    return None


# ---- the breed coats ------------------------------------------------------------------------------------------------------
LEG_TOP, GROUND = 9.0, 24.1
HEAD_REGIONS = ("head", "jowl", "muzzle", "lip", "ear")
BODY_REGIONS = ("body", "chest", "withers", "croup", "belly", "neck")


def eye(x):
    """On the head's sides: 2 = the eye, 3 = its glint, 1 = the brow over it, 0 = neither."""
    if x.cube.region != "head" or x.face not in ("a", "b"):
        return 0
    z, y = x.q[2], x.q[1]
    top = x.cube.pos[1]
    ez, ey = x.cube.pos[2] + 1.75, top + 1.6
    if abs(z - ez) < 0.75 and abs(y - ey) < 0.5:
        glint = (z < ez - 0.25) and (y < ey)
        return 3 if glint else 2
    if abs(z - ez) < 1.0 and ey - 1.0 <= y < ey - 0.5:
        return 1
    return 0


def mouth(x, col, tint):
    """The muzzle and lips: darker toward the nose, nostrils, the lips' parting, the inside of the mouth and the teeth."""
    c, f, q = x.cube, x.face, x.q
    front = c.pos[2]
    near = max(0.0, 1.0 - (q[2] - front) / max(1.0, c.size[2]))  # 1 at the nose
    col = mix(col, tint, 0.18 + 0.32 * near if f != "bottom" else 0.4)
    if c.region == "muzzle":
        if f == "front":
            nx = c.pos[0] + c.size[0] / 2
            if min(abs(q[0] - (nx - c.size[0] * 0.3)), abs(q[0] - (nx + c.size[0] * 0.3))) < 0.5 and q[1] < c.pos[1] + 1.4 \
                    and q[1] > c.pos[1] + 0.4:
                return (38, 26, 24)
        if f == "bottom":
            return (220, 212, 192) if q[2] - front < 0.6 else (118, 62, 60)
    if c.region == "lip":
        if f == "top":
            return (220, 212, 192) if q[2] - front < 0.6 else (130, 70, 66)
        if f in ("front", "a", "b") and x.J == 0:
            return shade(tint, -40)
    return col


def coat(build, breed, name, spec, pxs, seed):
    marks = spec["marks"] | ({"feather"} if build == "draft" else set())
    hair = spec["hair"]
    stripe = spec["stripe"] or shade(hair, -20)
    noise = ge.NOISE.get(breed, 5)
    hairn = ge.HAIRN.get(breed, 12)
    tint = spec["tint"] or (34, 28, 26)
    points_y = LEG_TOP + (GROUND - 3 - LEG_TOP) * spec["points_from"] / 9

    def body(x, col):
        w = x.w
        if "dapple" in marks and x.face != "bottom":
            d, _ = nearest(w, 2.6, seed + 11)
            k = 0.75 if d < 0.6 else 0.35 if d < 0.95 else 0.0
            col = mix(col, spec["spot"], k * (0.5 if x.cube.region == "neck" else 1.0))
        if "flea" in marks and h3(x.X // 2, x.Y // 2, 2, seed) < 0.11:
            col = mix(col, spec["spot"], 0.8)
        if "roan" in marks and h3(x.X // 2, x.Y // 2, 3, seed) < 0.3:
            col = mix(col, spec["spot"], 0.32 if h3(x.X // 2, x.Y // 2, 4, seed) < 0.7 else 0.5)
        if "pinto" in marks:
            n = vnoise3(w, 5.0, seed + 21)
            if n > (0.42 if x.face == "bottom" else 0.6 if x.cube.region == "neck" else 0.52):
                col = spec["white"]
        if "leopard" in marks:
            d, tag = nearest(w, 3.0, seed + 31)
            dense = 0.25 + 0.75 * max(0.0, min(1.0, (w[2] + 4) / 16))  # spottier toward the hindquarters
            if d < 0.55 + 0.45 * tag and tag < dense:
                col = spec["spot"]
        if "dorsal" in marks and x.cube.region in ("body", "withers", "croup") and abs(w[0]) < 0.8 and w[1] < 4.0:
            col = mix(col, stripe, 0.8)
        return col

    def hide(x):
        r = x.cube.region
        col = spec["base"]
        if r in HEAD_REGIONS and spec["head"]:
            col = spec["head"]
        if r in ("leg_a", "leg_b", "fetlock") and spec["legs"]:
            col = spec["legs"]
        if r in BODY_REGIONS:
            col = body(x, col)
        if r in ("head", "muzzle") and "leopard" in marks and h3(x.X, x.Y, 5, seed) < 0.06:
            col = spec["spot"]
        if r in ("head", "muzzle") and "flea" in marks and h3(x.X, x.Y, 6, seed) < 0.08:
            col = mix(col, spec["spot"], 0.7)
        if r == "ear":
            if spec["points"] and (x.face == "top" or x.t < 0.34):
                col = spec["points"]
            if x.face == "front" and x.t > 0.3:
                col = mix(col, ge.DARK, 0.5)  # the inside of the ear
            return col
        if r in ("leg_a", "leg_b", "fetlock"):
            y = x.w[1]
            if spec["points"] and y >= points_y:
                col = spec["points"]
            elif "bars" in marks and r == "leg_a" and int(y * 1.0) % 2 == 1 and x.face not in ("top", "bottom"):
                col = mix(col, stripe, 0.35)
            if "leopard" in marks and r == "leg_a":
                d, tag = nearest(x.w, 2.4, seed + 41)
                if d < 0.6 and tag < 0.6:
                    col = spec["spot"]
            if "socks" in marks and y >= GROUND - 5.2:
                col = spec["white"]
            if "stockings" in marks and y >= GROUND - 10.5:
                col = spec["white"]
            if r == "fetlock":
                col = shade(col, -10)
            return col
        if r in ("head", "muzzle", "jowl") and x.face == "top" or r == "muzzle" and x.face == "front":
            half = abs(x.q[0])
            if "blaze" in marks and half < (0.9 if r == "muzzle" else 0.75):
                col = spec["white"]
            if "star" in marks and r == "head" and x.face == "top" and abs(x.q[0]) + abs(x.q[2] - 2.0) < 1.1:
                col = spec["white"]
        if r in ("muzzle", "lip"):
            col = mouth(x, col, tint)
        e = eye(x)
        if e:
            col = (30, 22, 20) if e == 2 else (176, 160, 140) if e == 3 else shade(col, -26)
        return col

    def hairpx(x):
        r = x.cube.region
        col = spec["feather"] if r == "feather" else hair
        if r == "feather" and "feather" not in marks:
            col = mix(hair, spec["legs"] or spec["base"], 0.5)
        if "pinto" in marks and r in ("mane", "tail", "forelock") and vnoise3(x.w, 4.0, seed + 22) > 0.58:
            col = spec["white"]
        if "leopard" in marks and int(strand(x, seed) * 9) % 3 == 0:
            col = (226, 220, 210)
        if "dorsal" in marks and r == "mane" and abs(x.q[0]) < 0.6 and x.face in ("top", "back"):
            col = mix(col, stripe, 0.7)
        u = along(x)
        col = shade(col, int((strand(x, seed) - 0.5) * hairn) - (10 if u < 0.12 else 0) + (6 if u > 0.85 else 0))
        if "shimmer" in marks and h3(x.X, x.Y, 8, seed) < 0.05:
            col = shade(col, 26)
        return col

    out = {}
    for x in pxs:
        c = x.cube
        if c.kind in ("saddle", "strap", "iron", "rein", "headstall", "wing"):
            p = tack(x) if c.kind != "wing" else wing(x, (58, 46, 44))
            if p:
                out[(x.X, x.Y)] = p
            continue
        if c.kind == "hair" or c.region == "dock":
            if c.kind == "hair" and ragged(x, seed):
                continue
            if c.region == "dock":
                col = shade(hair, int((h3(x.X, x.Y, 9, seed) - 0.5) * hairn))
            else:
                col = hairpx(x)
            out[(x.X, x.Y)] = light(x, col, 0.6) + (255,)
            continue
        if c.kind == "hoof":
            hoof = spec["hoof"]
            if ("socks" in marks or "stockings" in marks) and hoof == ge.HOOF:
                hoof = mix(ge.HOOF_PALE, hoof, 0.35 if h3(x.I, 0, 1, seed) < 0.5 else 0.0)
            col = shade(hoof, 14) if x.face in ("a", "b", "front", "back") and x.J < 2 else hoof
            col = shade(col, -16) if x.face == "bottom" else shade(col, int(8 * (h3(x.I, x.face == "a", 2, seed) - 0.5)))
            out[(x.X, x.Y)] = light(x, col, 0.6) + (255,)
            continue
        col = hide(x)
        col = shade(col, int((h3(x.X, x.Y, 10, seed) - 0.5) * 2 * noise))
        if "shimmer" in marks and (x.face == "top" or x.t < 0.45) and h3(x.X, x.Y, 11, seed) < 0.07 and eye(x) == 0:
            col = shade(col, 30)
        out[(x.X, x.Y)] = light(x, col) + (255,)
    return out


def wing(x, base):
    """The bat horse's wings: dark membrane between finger bones, lighter at the leading edge."""
    c = x.cube
    q = x.q
    span = (q[0] - c.pos[0]) / c.size[0] if c.size[0] else 0
    if c.pos[0] < 0:
        span = 1 - span
    depth = (q[2] - c.pos[2]) / c.size[2]
    finger = abs(((depth * 4 + span * 1.2) % 1.0) - 0.5) < 0.08 and depth > 0.15
    col = base
    if depth < 0.12:
        col = shade(base, 26)  # the arm along the front
    elif finger:
        col = shade(base, 14)
    else:
        col = shade(base, -10 + int(10 * vnoise3(q, 3.0, 5)))
    if c.region == "tip" and depth > 0.85 and h3(x.X, x.Y, 12, 1) < 0.3:
        return None
    return light(x, col, 0.5) + (255,)


def nightmare(pxs, seed):
    """Black, with ember eyes and a mane and tail of fire (two textures that the nightmare flickers between)."""
    spec = ge.coat((30, 24, 27), (40, 20, 16), hoof=(28, 22, 22), tint=(60, 30, 26))
    out = coat("courser", "nightmare", "nightmare", spec, pxs, seed)
    for x in pxs:
        c = x.cube
        if c.kind == "hair" or c.region == "dock":
            u = along(x) if c.kind == "hair" else 0.0
            n = vnoise3((x.w[0], x.w[1] + seed * 3.7, x.w[2]), 1.6, seed)
            heat = u * 0.8 + n * 0.5
            if c.kind == "hair" and heat > 0.95:
                out.pop((x.X, x.Y), None)
                continue
            col = mix((150, 26, 8), (244, 120, 26), min(1.0, heat * 1.3)) if heat < 0.6 else mix((244, 120, 26), (255, 226, 110), min(1.0, (heat - 0.6) * 2.8))
            out[(x.X, x.Y)] = col + (255,)
        elif eye(x) in (2, 3):
            out[(x.X, x.Y)] = ((255, 168, 60) if eye(x) == 3 else (236, 72, 24)) + (255,)
        elif c.kind == "hoof" and h3(x.X, x.Y, 13, seed) < 0.12:
            out[(x.X, x.Y)] = (210, 70, 20, 255)
    return out


def bat(pxs, seed):
    spec = ge.coat((64, 52, 50), (32, 26, 26), head=(56, 46, 44), legs=(50, 40, 38), hoof=(36, 30, 28), tint=(96, 70, 66))
    out = coat("bat_horse", "bat_horse", "bat", spec, pxs, seed)
    for x in pxs:
        if eye(x) in (2, 3):
            out[(x.X, x.Y)] = ((240, 200, 110) if eye(x) == 3 else (190, 120, 30)) + (255,)
    return out


# ---- skeleton and zombie horses -----------------------------------------------------------------------------------------
BONE = (224, 216, 192)
BONE_DARK = (150, 140, 118)


def skeleton(pxs, seed):
    out = {}
    for x in pxs:
        c, f, w, q = x.cube, x.face, x.w, x.q
        r = c.region
        if c.kind in ("saddle", "strap", "iron", "rein", "headstall"):
            p = tack(x)
            if p:
                out[(x.X, x.Y)] = p
            continue
        if c.kind == "hair" or r in ("ear", "belly", "jowl") or (c.kind == "hair"):
            continue
        col = shade(BONE, int((h3(x.X, x.Y, 14, seed) - 0.5) * 14))
        if r == "body":
            ribs = -9.5 <= w[2] <= 5.0
            if f in ("a", "b") and ribs and w[1] > 4.2:
                band = (w[2] + 9.5) % 2.5
                if band > 1.25:
                    continue
                col = shade(col, -10 if band < 0.3 else 0)
            elif f == "top":
                if abs(w[0]) > 1.2:
                    band = (w[2] + 9.5) % 2.5
                    if ribs and band > 1.25:
                        continue
                else:
                    col = shade(col, -18 if (w[2] % 1.5) < 0.35 else 6)  # the spine
            elif f == "bottom":
                if not (abs(w[0]) < 1.1 and w[2] < -2):
                    continue
            elif f == "front" and abs(w[0]) > 2.5 and w[1] > 6:
                continue
        elif r == "neck":
            back = c.pos[2] + c.size[2]
            if q[2] < back - 2.4 and f != "back":
                continue
            col = shade(col, -22 if (q[1] % 2.3) < 0.45 else 0)  # the vertebrae
        elif r in ("leg_a", "leg_b"):
            if x.t < 0.15 and f in ("a", "b", "front", "back"):
                col = shade(col, -18)  # the joint
        elif r == "fetlock":
            col = shade(col, -14)
        elif r == "dock":
            col = shade(col, -20 if (q[2] % 1.2) < 0.3 else 0)
        elif c.kind == "hoof":
            col = shade(BONE_DARK, -30 + int(10 * h3(x.X, x.Y, 15, seed)))
        elif r == "head":
            e = eye(x)
            if e or (f in ("a", "b") and abs(q[2] - (c.pos[2] + 1.75)) < 1.1 and abs(q[1] - (c.pos[1] + 1.6)) < 0.9):
                col = (34, 28, 24)
        elif r == "muzzle":
            if f == "front":
                col = (40, 32, 28) if abs(q[0]) < 1.4 and q[1] < c.pos[1] + 1.8 else col
            if f == "bottom" or (f in ("a", "b") and q[1] > c.pos[1] + c.size[1] - 0.6):
                col = (236, 230, 210) if int(q[2] * 2) % 2 == 0 else (60, 50, 44)  # teeth
        elif r == "lip":
            if f == "top" or (f in ("a", "b") and q[1] < c.pos[1] + 0.6):
                col = (236, 230, 210) if int(q[2] * 2) % 2 == 0 else (60, 50, 44)
        out[(x.X, x.Y)] = light(x, col) + (255,)
    return out


def zombie(pxs, seed):
    hidec, hairc = (102, 116, 86), (52, 46, 40)
    out = {}
    for x in pxs:
        c, f, w, q = x.cube, x.face, x.w, x.q
        r = c.region
        if c.kind in ("saddle", "strap", "iron", "rein", "headstall"):
            p = tack(x)
            if p:
                out[(x.X, x.Y)] = p
            continue
        if c.kind == "hair":
            if ragged(x, seed, start=0.4, holes=0.8) or vnoise3(w, 2.0, seed + 5) > 0.62:
                continue
            col = shade(hairc, int((strand(x, seed) - 0.5) * 18))
            out[(x.X, x.Y)] = light(x, col, 0.6) + (255,)
            continue
        if c.kind == "hoof":
            out[(x.X, x.Y)] = light(x, shade((48, 44, 38), int(12 * (h3(x.X, x.Y, 16, seed) - 0.5)))) + (255,)
            continue
        n = vnoise3(w, 3.0, seed)
        col = mix(hidec, (70, 82, 58), 0.6) if n > 0.6 else hidec
        col = shade(col, int((h3(x.X, x.Y, 17, seed) - 0.5) * 14))
        if r in ("muzzle", "lip"):
            col = mouth(x, mix(col, (120, 118, 104), 0.3), (78, 70, 64))
        # the ribs show through a torn flank
        if r == "body" and f == "a" and ((w[2] + 2) / 4.0) ** 2 + ((w[1] - 7.5) / 2.2) ** 2 < 0.6 + 0.6 * vnoise3(w, 1.5, seed + 3):
            col = BONE if (w[2] % 1.6) < 0.8 else (92, 22, 22)
        elif r in BODY_REGIONS + ("leg_a", "head") and nearest(w, 4.5, seed + 9)[0] < 0.55:
            col = (128, 30, 28) if nearest(w, 4.5, seed + 9)[0] < 0.35 else (70, 34, 28)  # wounds
        e = eye(x)
        if e:
            col = (196, 206, 186) if e != 1 else shade(col, -24)
        out[(x.X, x.Y)] = light(x, col) + (255,)
    return out


def recolour(out, fn, seed):
    """Every opaque pixel that isn't tack, through fn(colour, noise, rnd)."""
    rnd = random.Random(seed)
    res = {}
    for (X, Y), p in out.items():
        res[(X, Y)] = clamp(fn(p[:3], h3(X // 6, Y // 6, 0, seed), rnd)) + (p[3],)
    return res


def is_tack(pxs):
    return {(x.X, x.Y) for x in pxs if x.cube.kind in ("saddle", "strap", "iron", "rein", "headstall")}


def undead(pxs):
    tackpx = is_tack(pxs)

    def keep_tack(base, made):
        for k in tackpx:
            if k in base:
                made[k] = base[k]
        return made

    bone = skeleton(pxs, 101)

    def sooty(c, n, rnd):
        c = scale(c, 0.42)
        return mix(c, (40, 36, 34), 0.6) if n > 0.6 else c

    def mossy(c, n, rnd):
        if lum(c) > 120 and n > 0.55:
            return shade((78, 110, 46), rnd.choice((-14, 0, 0, 12, 22)))
        return mix(c, (196, 188, 156), 0.2)

    rotten = zombie(pxs, 202)

    def sat(c):
        return colorsys.rgb_to_hsv(*(v / 255 for v in c))[1]

    def pale(c, n, rnd):
        if sat(c) > 0.45 and c[0] > c[1]:
            return c  # wounds stay red
        g = lum(c) / 110
        out = tuple(v * g for v in (150, 160, 172))
        return mix(out, (110, 116, 140), 0.35) if n > 0.6 else out

    def mottled(c, n, rnd):
        if sat(c) > 0.45 and c[0] > c[1]:
            return c
        g = lum(c) / 100
        out = tuple(v * g for v in (112, 86, 60))
        return mix(out, (106, 128, 66), 0.8) if n > 0.58 else out

    return {
        "skeleton/bone": bone,
        "skeleton/sooty": keep_tack(bone, recolour(bone, sooty, 103)),
        "skeleton/mossy": keep_tack(bone, recolour(bone, mossy, 104)),
        "zombie/rotten": rotten,
        "zombie/pale": keep_tack(rotten, recolour(rotten, pale, 203)),
        "zombie/mottled": keep_tack(rotten, recolour(rotten, mottled, 204)),
    }


# ---- horse armour -----------------------------------------------------------------------------------------------------------
def armour_base(pxs, chain):
    """The armour's shape, in greys (each material is a ramp over these): a chanfron down the face, a crinet along
    the neck, a peytral over the chest and plates down the flanks and over the croup, riveted at the edges."""
    out = {}
    for x in pxs:
        c, f, w, q = x.cube, x.face, x.w, x.q
        r = c.region
        if c.kind != "hide" or r is None:
            continue
        top = None  # how far down from the armour's lower edge (negative: below it)
        if r in ("body", "croup", "withers"):
            if f == "bottom":
                continue
            edge = 10.0
            top = edge - w[1]
        elif r == "chest":
            if f == "bottom":
                continue
            top = 11.5 - w[1]
        elif r == "neck":
            back = c.pos[2] + c.size[2]
            if f == "front":
                continue
            top = q[2] - (back - c.size[2] * 0.85)  # all but the throat
        elif r == "head":
            if f == "bottom" or (f in ("a", "b") and q[1] > c.pos[1] + 2.4) or eye(x) or \
                    (f in ("a", "b") and abs(q[2] - (c.pos[2] + 1.75)) < 1.2 and abs(q[1] - (c.pos[1] + 1.6)) < 1.0):
                continue
            top = 1.0
        elif r == "muzzle":
            if f not in ("top", "a", "b") or (f in ("a", "b") and q[1] > c.pos[1] + 1.2) or q[2] < c.pos[2] + 1.0:
                continue
            top = 1.0
        else:
            continue
        if top < 0:
            continue
        if chain:
            g = 160 if (x.I // 1 + x.J // 1) % 2 == 0 else 104
            if (x.I % 4 in (1, 2)) and (x.J % 4 in (1, 2)):
                g = 190
            col = (g, g, g)
            if top < 0.6:
                col = (84, 70, 56)  # the leather trim
        else:
            g = 176 + int(12 * (1 - x.t)) if f in ("a", "b", "front", "back") else 196
            g += int(6 * (h3(x.X, x.Y, 18, 1) - 0.5))
            col = (g, g, g)
            if r in ("body", "croup") and f in ("a", "b") and (w[2] + 10) % 6.0 < 0.5:
                col = (112, 112, 112)  # the seams between the plates
            if top < 0.55:
                col = (224, 224, 224) if top > 0.25 else (96, 96, 96)  # the rolled edge
                if top > 0.25 and int((w[2] + w[0]) * 1.5) % 3 == 0:
                    col = (70, 70, 70)  # rivets
            if r == "head" and f == "top" and abs(q[0]) < 0.5:
                col = (232, 232, 232)  # the ridge down the chanfron
        out[(x.X, x.Y)] = light(x, col, 0.7) + (255,)
    return out


def ramp(out, stops):
    res = {}
    for k, p in out.items():
        t = lum(p) / 255
        for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
            if t <= t1 or (t1, c1) == stops[-1]:
                kk = 0 if t1 == t0 else max(0.0, min(1.0, (t - t0) / (t1 - t0)))
                res[k] = mix(c0, c1, kk) + (p[3],)
                break
    return res


def armours(pxs):
    plate, mail = armour_base(pxs, False), armour_base(pxs, True)
    return {
        "minecraft/iron": ramp(plate, [(0.0, (46, 48, 54)), (0.4, (120, 124, 132)), (0.75, (190, 194, 200)), (1.0, (240, 242, 246))]),
        "minecraft/gold": ramp(plate, [(0.0, (88, 54, 10)), (0.45, (196, 146, 38)), (0.8, (250, 214, 84)), (1.0, (255, 248, 196))]),
        "minecraft/diamond": ramp(plate, [(0.0, (16, 64, 72)), (0.45, (54, 170, 180)), (0.8, (130, 236, 230)), (1.0, (226, 255, 255))]),
        "minecraft/copper": ramp(plate, [(0.0, (60, 30, 18)), (0.35, (150, 78, 46)), (0.7, (214, 128, 84)), (1.0, (250, 196, 150))]),
        "minecraft/netherite": ramp(plate, [(0.0, (20, 16, 18)), (0.4, (58, 50, 54)), (0.75, (98, 88, 92)), (1.0, (150, 138, 140))]),
        "minecraft/leather": ramp(plate, [(0.0, (90, 90, 90)), (0.5, (200, 200, 200)), (1.0, (255, 255, 255))]),
        "jaymoskingdoms/chain_barding": ramp(mail, [(0.0, (40, 40, 44)), (0.4, (110, 114, 120)), (1.0, (200, 204, 210))]),
        "jaymoskingdoms/plate_barding": ramp(plate, [(0.0, (70, 72, 78)), (0.3, (150, 154, 160)), (0.7, (206, 210, 216)), (1.0, (245, 247, 250))]),
    }


# ---- writing it all out -----------------------------------------------------------------------------------------------------
def image(out, size):
    img = Image.new("RGBA", (size[0] * S, size[1] * S), (0, 0, 0, 0))
    pix = img.load()
    for (X, Y), p in out.items():
        pix[X, Y] = p
    return img


def builds_json(rigs):
    """The builds for BreedHorseModel: each part (parents first) with its pose and boxes."""
    data = {"builds": {}}
    data["texture_size"] = list(hr.TEXTURE)
    for b, parts in rigs.items():
        hr.texture_size(parts)
        data["builds"][b] = {
            "parts": [{
                "name": p.name,
                "parent": p.parent or "",
                "pose": [round(v, 4) for v in p.pose],
                "cubes": [{"uv": list(c.uv), "pos": list(c.pos), "size": list(c.size), **({"grow": c.grow} if c.grow else {})}
                          for c in p.cubes],
            } for p in parts],
        }
    path = os.path.join(ASSETS, "horse", "builds.json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as fh:
        json.dump(data, fh, indent=None, separators=(",", ":"))
        fh.write("\n")


HIDDEN = ("upper_mouth_open", "lower_mouth_open", "head_saddle", "saddle", "saddle_b", "saddle_c", "stirrup_left",
          "stirrup_left_iron", "stirrup_right", "stirrup_right_iron", "bit_left", "bit_right", "rein_left", "rein_right")


def preview(rigs, written):
    """Every coat (and armour, on its build's first coat) drawn on its horse, a sheet a build."""
    os.makedirs(PREVIEW, exist_ok=True)
    by = {}
    for rel, (build, img) in written.items():
        if not rel.startswith("armor/"):
            by.setdefault(build, []).append(hp.render(hp.quads(rigs[build], img, hidden=HIDDEN), px=5))
    for build, tiles in by.items():
        w, h = max(t.width for t in tiles), max(t.height for t in tiles)
        sheet = Image.new("RGBA", (w * min(6, len(tiles)), h * ((len(tiles) + 5) // 6)), (58, 66, 74, 255))
        for k, t in enumerate(tiles):
            sheet.alpha_composite(t, ((k % 6) * w, (k // 6) * h))
        sheet.save(os.path.join(PREVIEW, build + ".png"))


def main():
    rigs = {b: hr.rig(b) for b in hr.BUILDS}
    builds_json(rigs)
    pxs = {b: pixels(parts) for b, parts in rigs.items()}
    written = {}

    def save(out, build, rel):
        img = image(out, hr.texture_size(rigs[build]))
        path = os.path.join(OUT, rel + ".png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        img.save(path)
        written[rel] = (build, img)

    for breed, coats in ge.COATS.items():
        for k, (name, spec) in enumerate(coats):
            save(coat(breed, breed, name, spec, pxs[breed], 1000 + 37 * k + 7 * len(breed)), breed, f"{breed}/{name}")
    save(nightmare(pxs["courser"], 1), "courser", "nightmare/nightmare")
    save(nightmare(pxs["courser"], 2), "courser", "nightmare/nightmare_1")
    save(bat(pxs["bat_horse"], 3), "bat_horse", "bat_horse/bat")
    for rel, out in undead(pxs["palfrey"]).items():
        save(out, "palfrey", rel)
    for build in hr.BUILDS:
        for asset, out in armours(pxs[build]).items():
            save(out, build, f"armor/{asset}/{build}")
    preview(rigs, written)
    print(f"wrote {len(written)} textures under {OUT}; previews in {PREVIEW}")


if __name__ == "__main__":
    main()
