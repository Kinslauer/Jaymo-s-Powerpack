"""The shapes of our horses (0.7.303): one rig, a build per breed. Shared by tools/gen_horses.py (which writes them to
assets/jaymoskingdoms/horse/builds.json for BreedHorseModel and paints the coats on them) and its preview renderer.

Every build keeps the bones of the horse we had (the Mo' Creatures port, 0.5.44) with the same names and the same
pivots, so the tack (tools/gen_tack.py), the bat horse's wings and the animation in BreedHorseModel carry over as they
are; what differs is the boxes on them and the extra pieces hung from them (chest, withers, croup, belly, jowls, a
forelock, a mane that falls over the neck, feathering, a longer tail). Model space is Minecraft's: x to the horse's
left, y down, the horse facing -z; sizes in model pixels."""
import math

DEG = math.pi / 180.0
TEXTURE = (128, 160)  # the model's texture space (every build's); the coats are painted at twice that

BUILDS = ("destrier", "courser", "palfrey", "draft", "steppe_pony", "desert_horse", "bat_horse")
# the breeds and undead that are drawn on another breed's build (the textures are laid out for it)
BUILD_OF = {"nightmare": "courser", "skeleton": "palfrey", "zombie": "palfrey"}


class Cube:
    """A box on a part: at `pos` (from the part's pivot), `size`, grown by `grow`. `kind` and `region` say how it's
    painted; a hair box also says which way its strands run (`axis`, and `tip` +1/-1: which end the ends are)."""

    def __init__(self, pos, size, kind="hide", region=None, grow=0.0, axis=None, tip=1, share=None):
        self.pos, self.size, self.grow = pos, size, grow
        self.kind, self.region, self.axis, self.tip = kind, region, axis, tip
        self.share = share  # another cube whose texture this one reuses (same size)
        self.uv = None


class Part:
    def __init__(self, name, parent, pose, cubes):
        self.name, self.parent, self.pose, self.cubes = name, parent, pose, cubes


HEAD_POSE = (0, 4, -10, 30.0, 0, 0)


def rig(build):
    """The parts of a build, parents first."""
    b = build
    slim = b in ("courser", "desert_horse")
    heavy = b == "draft"
    parts = []

    def add(name, parent, pose, *cubes):
        parts.append(Part(name, parent, pose, list(cubes)))
        return parts[-1]

    hind = {"draft": (5, 9, 6), "destrier": (5, 9, 5), "steppe_pony": (4, 9, 5)}.get(b, (3, 9, 4) if slim else (4, 9, 5))
    fore = {"draft": (4, 8, 5), "destrier": (4, 8, 4), "steppe_pony": (4, 8, 4)}.get(b, (3, 8, 3) if slim else (3, 8, 4))

    # ---- the body: the barrel, with a deep chest, the withers, a rounded croup and belly ----
    add("body", None, (0, 11, 9, 0, 0, 0), Cube((-5, -8, -19), (10, 10, 24), region="body"))
    chest_w = 11 if heavy else 10 if b == "destrier" else 8 if slim else 9
    add("chest", "body", (0, 0, 0, 0, 0, 0), Cube((-chest_w / 2, -6, -21), (chest_w, 8 if not slim else 7, 2), region="chest"))
    add("withers", "body", (0, 0, 0, 0, 0, 0), Cube((-1.5, -9, -18), (3, 1, 5), region="withers"))
    croup_w = 9 if heavy else 7 if slim else 8
    add("croup", "body", (0, 0, 0, 0, 0, 0), Cube((-croup_w / 2, -9, 0.5), (croup_w, 1, 4), region="croup"))
    if heavy:
        add("croup_split", "body", (0, 0, 0, 0, 0, 0), Cube((-3.5, -9.6, 1.5), (7, 1, 2), region="croup"))
    add("belly", "body", (0, 0, 0, 0, 0, 0), Cube((-4, 2, -15), (8, 1, 15 if not slim else 13), region="belly"))

    # the haunches, over the tops of the hind legs
    hw = 3.5 + hind[0] / 2 + 0.5  # the hind legs' outer sides, and a little over
    add("haunch", "body", (0, 0, 0, 0, 0, 0), Cube((-hw, -6, 0), (2 * hw, 7 if not slim else 6, 5), region="body"))  # (behind the blanket)

    # ---- the tail: three pieces (the dock and two of hair), longer and fuller by breed ----
    tail_b = {"draft": (4, 5, 8), "destrier": (3, 5, 8), "palfrey": (3, 4, 8), "steppe_pony": (3, 4, 7)}.get(b, (3, 4, 7))
    tail_c = {"draft": (4, 5, 9), "destrier": (4, 5, 9), "palfrey": (3, 4, 10), "bat_horse": (3, 4, 10),
              "steppe_pony": (3, 5, 9), "courser": (3, 4, 8), "desert_horse": (3, 4, 9)}[b]
    add("tail_a", None, (0, 3, 14, -1.134464 / DEG, 0, 0), Cube((-1, -1, 0), (2, 2, 3), region="dock"))
    add("tail_b", None, (0, 3, 14, -1.134464 / DEG, 0, 0),
        Cube((-tail_b[0] / 2, -2, 3), tail_b, kind="hair", region="tail", axis="z", tip=1))
    add("tail_c", None, (0, 3, 14, -1.40215 / DEG, 0, 0),
        Cube((-tail_c[0] / 2, -tail_c[1] + 0.5, 9), tail_c, kind="hair", region="tail", axis="z", tip=1))

    # ---- the legs: upper leg, cannon (with feathering on the heavy breeds), hoof; back left, back right, front left, front right ----
    cannon = (2, 5, 2) if slim else (3, 5, 3)
    for k, (x, z) in enumerate(((4, 11), (-4, 11), (4, -8), (-4, -8)), start=1):
        front = k > 2
        cx = (-0.5 if k == 1 else 0.5 if k == 2 else -0.4 if k == 3 else 0.4)  # the leg's centre line, as the old horse's
        upper = fore if front else hind
        top = -1 if front else -2
        zc = -0.1 if front else 0.0
        add(f"leg{k}_a", None, (x, 9, z, 0, 0, 0),
            Cube((cx - upper[0] / 2, top, zc - upper[2] / 2), upper, region="leg_a"))
        leg_b = add(f"leg{k}_b", None, (x, 16, z, 0, 0, 0),
                    Cube((cx - cannon[0] / 2, 0, zc - cannon[2] / 2), cannon, region="leg_b"))
        if heavy:
            leg_b.cubes.append(Cube((cx - 3, 2, zc - 3), (6, 3, 6), kind="hair", region="feather", axis="y", tip=1))
        elif b == "destrier":
            leg_b.cubes.append(Cube((cx - 2, 3, zc - 2), (4, 2, 4), kind="hair", region="feather", axis="y", tip=1))
        else:
            leg_b.cubes.append(Cube((cx - (cannon[0] + 1) / 2, 4, zc - (cannon[2] + 1) / 2), (cannon[0] + 1, 1, cannon[2] + 1),
                                    region="fetlock"))
        add(f"leg{k}_c", None, (x, 16, z, 0, 0, 0), Cube((cx - 2, 5.1, zc - 2), (4, 3, 4), kind="hoof", region="hoof"))

    # ---- the head and neck (all posed together); the mouth comes shut and open ----
    # (heads stay 5 wide and muzzles 4 at most, for the bridle and headstall, which are made for that)
    head = {"steppe_pony": (5, 6, 7), "desert_horse": (5, 5, 6), "draft": (5, 6, 7), "destrier": (5, 6, 7)}.get(b, (5, 5, 7))
    hx = -head[0] / 2
    add("head", None, HEAD_POSE, Cube((hx, -10, -1.5), head, region="head"))
    if b != "desert_horse":
        add("jowl", "head", (0, 0, 0, 0, 0, 0), Cube((hx + 0.5, -10 + head[1], 1.5), (head[0] - 1, 1, 3), region="jowl"))
    # the forelock over the brow
    fl = {"destrier": (3, 1, 5), "draft": (3, 1, 5), "palfrey": (3, 1, 5), "steppe_pony": (2, 1, 3)}.get(b, (2, 1, 4))
    add("forelock", "head", (0, 0, 0, 0, 0, 0),
        Cube((-fl[0] / 2, -10.6, 4.5 - fl[2]), fl, kind="hair", region="forelock", axis="z", tip=-1))
    if b in ("destrier", "palfrey", "draft"):
        add("forelock_tip", "head", (0, 0, 0, 0, 0, 0),
            Cube((-fl[0] / 2, -10.6, 4.5 - fl[2] - 1), (fl[0], 2, 1), kind="hair", region="forelock", axis="y", tip=1))

    # the muzzle: a Roman nose on the draft, a fine dished one on the desert horse
    if b == "desert_horse":
        upper, lower = Cube((-1.5, -9.5, -6.5), (3, 3, 5), region="muzzle"), Cube((-1.5, -6.5, -6), (3, 2, 4), region="lip")
    elif heavy:
        upper, lower = Cube((-2, -10.5, -7.5), (4, 4, 6), region="muzzle"), Cube((-2, -6.5, -7), (4, 2, 5), region="lip")
    elif b == "steppe_pony":
        upper, lower = Cube((-2, -10, -6), (4, 3, 5), region="muzzle"), Cube((-2, -7, -5.5), (4, 2, 4), region="lip")
    else:
        upper, lower = Cube((-2, -10, -7), (4, 3, 6), region="muzzle"), Cube((-2, -7, -6.5), (4, 2, 5), region="lip")
    add("upper_mouth", None, HEAD_POSE, upper)
    add("lower_mouth", None, HEAD_POSE, lower)
    add("upper_mouth_open", None, (0, 4, -10, 25.0, 0, 0),
        Cube((upper.pos[0], upper.pos[1], upper.pos[2] - 1), upper.size, region="muzzle", share=upper))
    add("lower_mouth_open", None, (0, 4, -10, 45.0, 0, 0),
        Cube((lower.pos[0], lower.pos[1], lower.pos[2] + 1), lower.size, region="lip", share=lower))

    ear = (2, 2, 1) if b == "steppe_pony" else (2, 3, 1)
    ear_y = -10 - ear[1]
    add("left_ear", None, HEAD_POSE, Cube((head[0] / 2 - 2.05, ear_y, 4), ear, region="ear"))
    add("right_ear", None, HEAD_POSE, Cube((-head[0] / 2 + 0.05, ear_y, 4), ear, region="ear"))

    # the neck: deep and crested on the war and draft horses, fine on the desert horse
    neck = {"destrier": (5, 14, 9), "draft": (6, 14, 9), "steppe_pony": (5, 13, 8), "desert_horse": (4, 15, 6),
            "courser": (4, 15, 7)}.get(b, (4, 14, 8))
    add("neck", None, HEAD_POSE, Cube((-neck[0] / 2 - 0.05, -9.8, -2), neck, region="neck"))
    # the mane: along the crest, and (on the long-maned breeds) falling over the neck's right side
    back = -2 + neck[2]  # the neck's crest line
    if b == "steppe_pony":
        mane = Cube((-1, -12, back - 1), (2, 16, 4), kind="hair", region="mane", axis="z", tip=1)  # stands up, cropped
    elif slim:
        mane = Cube((-1, -11.5, back - 1.5), (2, 16, 3), kind="hair", region="mane", axis="z", tip=1)
    else:
        mane = Cube((-1, -11.5, back - 2), (2, 16, 4), kind="hair", region="mane", axis="z", tip=1)
    add("mane", None, HEAD_POSE, mane)
    if b in ("destrier", "palfrey", "draft", "bat_horse"):
        flow_d = 6 if b in ("destrier", "draft") else 5
        add("mane_flow", "mane", (0, 0, 0, 0, 0, 0),
            Cube((neck[0] / 2 - 0.05 - 0.6, -11, back - flow_d + 0.5), (1, 15, flow_d), kind="hair", region="mane", axis="z", tip=-1))

    # ---- the vanilla saddle, headstall, bit and reins (as the old horse's) ----
    add("head_saddle", None, HEAD_POSE, Cube((-2.5, -10.1, -7), (5, 5, 12), kind="headstall", grow=0.2))
    add("saddle", None, (0, 2, 2, 0, 0, 0), Cube((-5, 0, -3), (10, 1, 8), kind="saddle"))
    add("saddle_b", None, (0, 2, 2, 0, 0, 0), Cube((-1.5, -1, -3), (3, 1, 2), kind="saddle", region="pommel"))
    add("saddle_c", None, (0, 2, 2, 0, 0, 0), Cube((-4, -1, 3), (8, 1, 2), kind="saddle", region="cantle"))
    add("stirrup_left", None, (5, 3, 2, 0, 0, 0), Cube((-0.5, 0, -0.5), (1, 6, 1), kind="strap"))
    add("stirrup_left_iron", None, (5, 3, 2, 0, 0, 0), Cube((-0.5, 6, -1), (1, 2, 2), kind="iron"))
    add("stirrup_right", None, (-5, 3, 2, 0, 0, 0), Cube((-0.5, 0, -0.5), (1, 6, 1), kind="strap"))
    add("stirrup_right_iron", None, (-5, 3, 2, 0, 0, 0), Cube((-0.5, 6, -1), (1, 2, 2), kind="iron"))
    add("bit_left", None, HEAD_POSE, Cube((1.5, -8, -4), (1, 2, 2), kind="iron"))
    add("bit_right", None, HEAD_POSE, Cube((-2.5, -8, -4), (1, 2, 2), kind="iron"))
    add("rein_left", None, (0, 4, -10, 0, 0, 0), Cube((2.6, -6, -6), (0, 3, 16), kind="rein"))
    add("rein_right", None, (0, 4, -10, 0, 0, 0), Cube((-2.6, -6, -6), (0, 3, 16), kind="rein"))

    # ---- the bat horse's wings (the old horse's pegasus wings) ----
    if b == "bat_horse":
        add("mid_wing", None, (5, 3, -6, 0, 5.0, 0), Cube((1, 0.1, 1), (12, 2, 11), kind="wing"))
        add("inner_wing", None, (5, 3, -6, 0, -20.0, 0), Cube((0, 0, 0), (7, 2, 11), kind="wing"))
        add("outer_wing", None, (17, 3, -6, 0, -18.5, 0), Cube((0, 0, 0), (22, 2, 11), kind="wing", region="tip"))
        add("inner_wing_r", None, (-5, 3, -6, 0, 20.0, 0), Cube((-7, 0, 0), (7, 2, 11), kind="wing"))
        add("mid_wing_r", None, (-5, 3, -6, 0, -5.0, 0), Cube((-13, 0.1, 1), (12, 2, 11), kind="wing"))
        add("outer_wing_r", None, (-17, 3, -6, 0, 18.5, 0), Cube((-22, 0, 0), (22, 2, 11), kind="wing", region="tip"))
    pack(parts)
    return parts


def footprint(c):
    w, h, d = (math.ceil(v) for v in c.size)
    return 2 * (w + d), d + h


def pack(parts):
    """Lays every box out in the texture (shelves, tallest first); a shared box takes its twin's place."""
    cubes = [c for p in parts for c in p.cubes if c.share is None]
    order = sorted(cubes, key=lambda c: (-footprint(c)[1], -footprint(c)[0]))
    x = y = shelf = 0
    for c in order:
        w, h = footprint(c)
        if x + w > TEXTURE[0]:
            x, y, shelf = 0, y + shelf, 0
        c.uv = (x, y)
        x += w
        shelf = max(shelf, h)
    for p in parts:
        for c in p.cubes:
            if c.share is not None:
                c.uv = c.share.uv


# ---- box faces in the texture, and where each texel of them lies on the box ----------------------------------------------
def texture_size(parts):
    """Every build's texture is the same size (the model is baked with one): 128 wide, as tall as the tallest needs."""
    rows = max(c.uv[1] + footprint(c)[1] for p in parts for c in p.cubes)
    if rows > TEXTURE[1]:
        raise SystemExit(f"the boxes need {rows} rows of texture, more than {TEXTURE[1]}")
    return TEXTURE


def faces(c):
    """{face: (u, v, width, height)} in texels; top/bottom/a(-x side)/front(-z)/b(+x side)/back(+z), as Minecraft lays a box out."""
    u, v = c.uv
    w, h, d = (math.ceil(s) for s in c.size)
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "a": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "b": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


def local(c, face, s, t):
    """The point on the box (part space) at (s, t) across a face (0-1 each, s across the texture, t down it)."""
    x0, y0, z0 = c.pos
    w, h, d = c.size
    x1, y1, z1 = x0 + w, y0 + h, z0 + d
    if face == "top":
        return (x0 + s * w, y0, z1 - t * d)
    if face == "bottom":
        return (x0 + s * w, y1, z0 + t * d)
    if face == "front":
        return (x0 + s * w, y0 + t * h, z0)
    if face == "back":
        return (x1 - s * w, y0 + t * h, z1)
    if face == "a":
        return (x0, y0 + t * h, z1 - s * d)
    return (x1, y0 + t * h, z0 + s * d)


NORMALS = {"top": (0, -1, 0), "bottom": (0, 1, 0), "front": (0, 0, -1), "back": (0, 0, 1), "a": (-1, 0, 0), "b": (1, 0, 0)}


# ---- poses --------------------------------------------------------------------------------------------------------------
def rot(p, rx, ry, rz):
    """Minecraft's ModelPart rotation (rotationZYX): x first, then y, then z."""
    x, y, z = p
    cx, sx = math.cos(rx), math.sin(rx)
    y, z = y * cx - z * sx, y * sx + z * cx
    cy, sy = math.cos(ry), math.sin(ry)
    x, z = x * cy + z * sy, -x * sy + z * cy
    cz, sz = math.cos(rz), math.sin(rz)
    x, y = x * cz - y * sz, x * sz + y * cz
    return (x, y, z)


def transforms(parts, poses=None):
    """Each part's (translate, rotation) chain to model space, from its parents'; `poses` overrides a part's pose."""
    by = {p.name: p for p in parts}
    out = {}

    def chain(p):
        if p.name in out:
            return out[p.name]
        pose = (poses or {}).get(p.name, p.pose)
        own = [(pose[:3], tuple(a * DEG for a in pose[3:]))]
        out[p.name] = (chain(by[p.parent]) if p.parent else []) + own
        return out[p.name]

    for p in parts:
        chain(p)
    return out


def apply(chain, point, normal=False):
    for (t, r) in reversed(chain):
        point = rot(point, *r)
        if not normal:
            point = (point[0] + t[0], point[1] + t[1], point[2] + t[2])
    return point
