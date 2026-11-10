"""Stegonaut's art and data (kingdoms/src/main/java/com/jaymoskingdoms/dragons/StegonautDragon), all our own design.
Its moves are ported from Saint's Dragons by Leon Saint (MIT code); none of that mod's art, models or sounds are used
(they are All Rights Reserved), see docs/SaintsDragons-Plan.md.
- the body plan (GEOMETRY: every box, its parent, its pose), which tools/gen_stegonaut_geo.py turns into the GeckoLib
  model (kingdoms/art/stegonaut.bbmodel), so the model and the texture can't drift apart;
- four hides (moss stone, jungle green, sandstone, slate; plates of a darker horn with pale worn rims, a pale belly,
  scale rows) picked per dragon by its DragonLook, and four glowing marking patterns (glow-lichen teal: plate rims,
  cracks, spots, a spine line; the eyes in every one);
- the spawn egg's icon and item model, and the loot table;
- a preview render (tools/out/stegonaut-preview.png, or the path given) of all four hides.
Run from kingdoms/: python3 -I tools/gen_stegonaut.py [preview.png]"""
import math, os, random, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image, ImageDraw
import gen_dragons as g
from gen_dragons import rgb, jitter, mix, shade, write, entry, egg, ASSETS, DATA, ITEM, SIZE

# ---- the body plan -------------------------------------------------------------------------------------------------
# (name, parent, uv part, (x, y, z, w, h, d), (px, py, pz, rx, ry, rz), mirror). Model pixels, y down, the ground at 24;
# -z is the front. Parts named in the Java model by these names.
PLATE_Z = [-12.0, -5.0, 2.0, 9.0, 15.0]
PLATE_H = [10, 14, 17, 14, 10]
PLATE_D = [7, 9, 11, 9, 7]
# (0.7.522, Andy: "longer limbs and necks, and be taller, to be imposing") high on four long pillar legs, a long neck
# raised up and forward in four segments, the head levelled again at its end, a long drooping tail
NECK = [('neck0', (13, 11, 8), -0.55), ('neck1', (12, 10, 7), -0.25), ('neck2', (11, 9, 7), -0.1), ('neck3', (10, 9, 6), 0.1)]
HEAD_TILT = 0.75
TAIL = [('tail0', (12, 11, 10), 0.18), ('tail1', (10, 9, 10), 0.08), ('tail2', (8, 7, 10), 0.0), ('tail3', (6, 6, 9), -0.04),
        ('tail4', (5, 5, 9), -0.06)]
GEOMETRY = [
    ('body', None, 'body', (-12, -8, -18, 24, 16, 36), (0, -4, 0, 0, 0, 0), False),
    ('belly', 'body', 'belly', (-10, 0, -13, 20, 3, 26), (0, 8, 0, 0, 0, 0), False),
]
# each neck segment hangs from the front of the one before
parent, parent_d = 'body', None
for n, (w, h, d), rx in NECK:
    pose = (0, -3, -17, rx, 0, 0) if parent == 'body' else (0, 0, -(parent_d - 1), rx, 0, 0)
    GEOMETRY.append((n, parent, n, (-w / 2, -h / 2, -d, w, h, d), pose, False))
    parent, parent_d = n, d
GEOMETRY += [
    ('head', 'neck3', 'skull', (-7, -5, -10, 14, 9, 10), (0, 0, -5, HEAD_TILT, 0, 0), False),
    ('snout', 'head', 'snout', (-5, -3, -7, 10, 6, 7), (0, 0, -10, 0, 0, 0), False),
    ('brow', 'head', 'brow', (-7.5, -1.5, -2, 15, 3, 4), (0, -4.5, -7, 0, 0, 0), False),
    ('jaw', 'head', 'jaw', (-5.5, 0, -16, 11, 3, 16), (0, 4, 0, 0, 0, 0), False),
    ('eye_l', 'head', 'eye', (-0.5, -1.5, -1.5, 1, 3, 3), (7.0, -2.0, -6.5, 0, 0, 0), False),
    ('eye_r', 'head', 'eye', (-0.5, -1.5, -1.5, 1, 3, 3), (-7.0, -2.0, -6.5, 0, 0, 0), False),
    ('horn_l', 'head', 'horn', (-1, -1, 0, 2, 2, 9), (5.5, -4.5, -4, 0.45, 0.35, 0), False),
    ('horn_r', 'head', 'horn', (-1, -1, 0, 2, 2, 9), (-5.5, -4.5, -4, 0.45, -0.35, 0), False),
    ('short_horn_l', 'head', 'horn_short', (-1, -1, 0, 2, 2, 4), (5.5, -4.5, -4, 0.5, 0.35, 0), False),
    ('short_horn_r', 'head', 'horn_short', (-1, -1, 0, 2, 2, 4), (-5.5, -4.5, -4, 0.5, -0.35, 0), False),
    ('nose_horn', 'snout', 'nose_horn', (-1, -4, -1, 2, 4, 2), (0, -3, -4, -0.35, 0, 0), False),
]
parent = 'body'
for n, (w, h, d), rx in TAIL:
    pose = (0, -2, 17, rx, 0, 0) if parent == 'body' else (0, 0.5, [pd for pn, (pw, ph, pd), _ in TAIL if pn == parent][0] - 1, rx, 0, 0)
    GEOMETRY.append((n, parent, n, (-w / 2, -h / 2, 0, w, h, d), pose, False))
    parent = n
GEOMETRY += [
    ('thag0', 'tail4', 'thag', (-1, -1, 0, 2, 2, 9), (2, -1.5, 5, -0.7, 1.1, 0), False),
    ('thag1', 'tail4', 'thag', (-1, -1, 0, 2, 2, 9), (-2, -1.5, 5, -0.7, -1.1, 0), False),
    ('thag2', 'tail4', 'thag', (-1, -1, 0, 2, 2, 8), (2, -1.5, 1, -0.9, 0.9, 0), False),
    ('thag3', 'tail4', 'thag', (-1, -1, 0, 2, 2, 8), (-2, -1.5, 1, -0.9, -0.9, 0), False),
    ('tail_plate0', 'tail0', 'tail_plate0', (-1, -7, -3.5, 2, 7, 7), (0, -5.5, 5, 0, 0, 0), False),
    ('tail_plate1', 'tail1', 'tail_plate1', (-1, -5, -2.5, 2, 5, 5), (0, -4.5, 5, 0, 0, 0), False),
    ('saddle', 'body', 'saddle', (-8, -2, -7, 16, 2, 14), (0, -8, -1, 0, 0, 0), False),
    ('pommel', 'saddle', 'pommel', (-2, -3, 0, 4, 3, 2), (0, -2, -7, 0, 0, 0), False),
    ('cantle', 'saddle', 'cantle', (-6, -3, 0, 12, 3, 2), (0, -2, 5, 0, 0, 0), False),
    ('strap', 'body', 'strap', (-12.5, -8.5, -1.5, 25, 17, 3), (0, 0, -1, 0, 0, 0), False),
    ('pad', 'body', 'pad', (-7, -2, -5, 14, 2, 10), (0, -8, 9, 0, 0, 0), False),
]
for i in range(5):
    h, d = PLATE_H[i], PLATE_D[i]
    # two staggered rows, leaning out a little
    GEOMETRY.append(('plate%d_l' % i, 'body', 'plate%d' % i, (-1, -h, -d / 2, 2, h, d), (2.5, -7, PLATE_Z[i], 0, 0, 0.16), False))
    GEOMETRY.append(('plate%d_r' % i, 'body', 'plate%d' % i, (-1, -h, -d / 2, 2, h, d), (-2.5, -7, PLATE_Z[i] + 2.5, 0, 0, -0.16), True))
LEGS = [('left_front', 9.0, -11.0, True), ('right_front', -9.0, -11.0, True), ('left_hind', 9.5, 12.0, False), ('right_hind', -9.5, 12.0, False)]
for name, x, z, front in LEGS:
    mirror = x < 0
    # long pillars (Andy: "longer cannons"): thigh 13, a long slimmer shin 14, foot 2, from a hip 26 pixels up
    up = (-4.5, -3, -4.5, 9, 13, 9) if front else (-5, -3, -5, 10, 13, 10)
    GEOMETRY.append((name + '_leg', 'body', 'front_upper' if front else 'back_upper', up, (x, 2, z, 0, 0, 0), mirror))
    GEOMETRY.append((name + '_shin', name + '_leg', 'front_lower' if front else 'back_lower',
                     (-3.5, 0, -3.5, 7, 14, 7) if front else (-4, 0, -4, 8, 14, 8), (0, 10, 0, 0, 0, 0), mirror))
    GEOMETRY.append((name + '_foot', name + '_shin', 'foot', (-5, 0, -6.5, 10, 2, 11), (0, 14, 0, 0, 0, 0), mirror))

PARTS = []
for _, _, uv, (x, y, z, w, h, d), _, _ in GEOMETRY:
    if uv not in [p[0] for p in PARTS]:
        PARTS.append((uv, w, h, d))


def region(w, h, d):
    return 2 * (w + d), h + d


def pack():
    order = sorted(PARTS, key=lambda p: -region(p[1], p[2], p[3])[1])
    at, x, y, shelf = {}, 0, 0, 0
    for name, w, h, d in order:
        rw, rh = region(math.ceil(w), math.ceil(h), math.ceil(d))
        if x + rw > SIZE:
            x, y, shelf = 0, y + shelf, 0
        at[name] = (x, y)
        x += rw
        shelf = max(shelf, rh)
    assert y + shelf <= SIZE, "the parts don't fit"
    return at


UV = pack()
DIMS = {n: (int(math.ceil(w)), int(math.ceil(h)), int(math.ceil(d))) for n, w, h, d in PARTS}


def faces(name):
    u, v = UV[name]
    w, h, d = DIMS[name]
    return {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'east': (u, v + d, d, h), 'north': (u + d, v + d, w, h),
            'west': (u + d + w, v + d, d, h), 'south': (u + d + w + d, v + d, w, h)}


def paint(img, name, fn):
    px = img.load()
    for face, (fx, fy, fw, fh) in faces(name).items():
        for y in range(fh):
            for x in range(fw):
                c = fn(face, x, y, fw, fh)
                if c is not None:
                    px[fx + x, fy + y] = c


# ---- the hides -----------------------------------------------------------------------------------------------------
# (main, dark, belly, plate)
HIDES = [
    (0x6E7A5A, 0x434C36, 0xC9C3A0, 0x55503E),  # moss stone
    (0x4E6E3C, 0x2E4424, 0xC2C690, 0x4A4632),  # jungle green
    (0x9C8460, 0x62523A, 0xDCCDA6, 0x6A5236),  # sandstone
    (0x66707A, 0x3C434A, 0xC4C6C0, 0x4A4C52),  # slate
]
MOSS = [rgb(0x5E7A32), rgb(0x4A6A2A), rgb(0x7A7A3A), rgb(0x5A6A44)]
PLATE_RIM = rgb(0xD8D0B0)
HORN, HORN_TIP, CLAW = rgb(0x2E2A22), rgb(0xB0A482), rgb(0x1E1C16)
EYE, PUPIL, NOSTRIL, MOUTH, TEETH = rgb(0xE8B040), rgb(0x140E08), rgb(0x1A1610), rgb(0x4A2A1C), rgb(0xE2DAC0)
LEATHER, LEATHER_DARK, GOLD, CLOTH = rgb(0x6B3F22), rgb(0x4A2A16), rgb(0xE8B830), rgb(0x2E5A4A)
LICHEN, LICHEN_HOT, LICHEN_DIM = rgb(0x5AE8C0), rgb(0xB0FFE8), rgb(0x2EAA8A)

BELLIES = {'body', 'belly', 'neck0', 'neck1', 'jaw', 'tail0', 'tail1', 'tail2', 'tail3'}
PLATES = {'plate%d' % i for i in range(5)} | {'tail_plate0', 'tail_plate1'}
HORNY = {'horn', 'horn_short', 'nose_horn', 'thag'}
LEGS_UV = {'front_upper', 'front_lower', 'back_upper', 'back_lower'}
TACK = {'saddle', 'pommel', 'cantle', 'strap', 'pad'}


def noise_field(seed, cells=30, scale=7.0):
    rnd = random.Random(seed)
    grid = [[rnd.random() for _ in range(cells)] for _ in range(cells)]

    def at(x, y):
        fx, fy = x / scale, y / scale
        x0, y0 = int(fx) % (cells - 1), int(fy) % (cells - 1)
        tx, ty = fx - int(fx), fy - int(fy)
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        a = grid[y0][x0] * (1 - tx) + grid[y0][x0 + 1] * tx
        b = grid[y0 + 1][x0] * (1 - tx) + grid[y0 + 1][x0 + 1] * tx
        return a * (1 - ty) + b * ty
    return at


FACE_I = ['top', 'bottom', 'east', 'north', 'west', 'south']


def hide_texture(variant):
    main, dark, belly, plate = (rgb(c) for c in HIDES[variant])
    moss = MOSS[variant]
    rnd = random.Random(7100 + variant)
    field = noise_field(310 + variant)
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))

    def skin(name, face, x, y, fw, fh, base):
        u, v = UV[name]
        n = field(u + x * 1.2 + FACE_I.index(face) * 7, v + y * 1.2)
        c = base
        # big rounded scutes: a darker seam every few pixels, staggered by row
        row = y // 4
        if y % 4 == 3 and rnd.random() < 0.8 or (x + (row % 2) * 3) % 6 == 5 and y % 4 != 0:
            c = mix(c, dark, 0.22)
        if n > 0.62:
            c = mix(c, dark, min(1.0, (n - 0.62) * 2.2) * 0.6)
        elif n < 0.28:
            c = mix(c, rgb(0xFFFFFF), (0.28 - n) * 0.3)
        # moss on its back (the tops), as on an old stone
        if face == 'top' and n > 0.5 and rnd.random() < 0.55:
            c = mix(c, moss, 0.55)
        return shade(jitter(c, 4, rnd), face, x, y, fw, fh)

    for name, w, h, d in PARTS:
        if name in PLATES:
            def pl(face, x, y, fw, fh):
                # horn plates: dark, with ridges running up, the rim (top and the outer edge) worn pale
                c = plate
                if face in ('east', 'west', 'north', 'south'):
                    edge = y <= 1
                    side_edge = face in ('east', 'west') and (x == 0 or x == fw - 1)
                    if edge or side_edge:
                        c = mix(plate, PLATE_RIM, 0.55 if edge else 0.35)
                    elif x % 3 == 1:
                        c = mix(plate, dark, 0.4)
                    if y >= fh - 2:
                        c = mix(c, main, 0.6)  # where it grows from the hide
                elif face == 'top':
                    c = mix(plate, PLATE_RIM, 0.6)
                return shade(jitter(c, 5, rnd), face, x, y, fw, fh)
            paint(img, name, pl)
        elif name in HORNY:
            def horn(face, x, y, fw, fh, name=name):
                t = (x / max(1, fw - 1) if face in ('top', 'bottom') else 0.3) * 0.9
                if face == 'south' or name == 'nose_horn' and y < 2:
                    t = 1.0
                return shade(jitter(mix(HORN, HORN_TIP, min(1.0, t)), 5, rnd), face, x, y, fw, fh)
            paint(img, name, horn)
        elif name in TACK:
            def tack(face, x, y, fw, fh, name=name):
                if name == 'strap':
                    c = LEATHER_DARK if y in (0, fh - 1) or x in (0, fw - 1) else LEATHER
                    if face in ('east', 'west') and fh // 2 - 1 <= y <= fh // 2 + 1 and fw // 2 - 1 <= x <= fw // 2:
                        c = GOLD
                    return shade(jitter(c, 4, rnd), face, x, y, fw, fh)
                c = LEATHER
                if x in (0, fw - 1) or y in (0, fh - 1):
                    c = LEATHER_DARK
                if name == 'pad' and face == 'top':
                    c = CLOTH if (x // 2 + y // 2) % 2 == 0 else mix(CLOTH, rgb(0x000000), 0.3)
                if face == 'top' and name == 'saddle' and (x, y) in ((1, 1), (fw - 2, 1), (1, fh - 2), (fw - 2, fh - 2)):
                    c = GOLD
                return shade(jitter(c, 5, rnd), face, x, y, fw, fh)
            paint(img, name, tack)
        elif name == 'eye':
            def eye(face, x, y, fw, fh):
                if face in ('east', 'west'):
                    return PUPIL if y == 1 and x == 1 else jitter(EYE, 6, rnd)
                return shade(dark, face, x, y, fw, fh)
            paint(img, name, eye)
        else:
            def body(face, x, y, fw, fh, name=name):
                if name == 'foot':
                    if face == 'bottom':
                        return shade(jitter(dark, 4, rnd), face, x, y, fw, fh)
                    if face == 'north':
                        # three blunt toenails
                        return jitter(CLAW, 4, rnd) if x in (1, 4, 7) and y == fh - 1 or x in (1, 4, 7) else shade(jitter(dark, 4, rnd), face, x, y, fw, fh)
                if name in LEGS_UV and face == 'bottom':
                    return shade(jitter(dark, 4, rnd), face, x, y, fw, fh)
                if name == 'jaw' and face == 'top':
                    # a broad shovel of a mouth, flat grinding teeth round its rim
                    return TEETH if x in (0, fw - 1) or y == 0 else jitter(MOUTH, 6, rnd)
                if name == 'jaw' and face == 'north':
                    return TEETH if y == 0 and x % 2 == 0 else shade(jitter(mix(main, dark, 0.25), 4, rnd), face, x, y, fw, fh)
                if name == 'snout' and face == 'north':
                    if y == fh - 1:
                        return TEETH if x % 2 == 1 else jitter(MOUTH, 4, rnd)
                    if y == 1 and x in (2, fw - 3):
                        return NOSTRIL
                if name == 'snout' and face in ('east', 'west') and y == fh - 1:
                    return TEETH if x % 2 == 0 else jitter(MOUTH, 4, rnd)
                if name == 'brow':
                    return shade(jitter(mix(main, dark, 0.4) if y == fh - 1 else mix(main, PLATE_RIM, 0.15), 4, rnd), face, x, y, fw, fh)
                if face == 'bottom' and name in BELLIES:
                    return shade(jitter(mix(belly, dark, 0.25) if y % 3 == 2 else belly, 5, rnd), face, x, y, fw, fh)
                if name in BELLIES and face in ('east', 'west', 'north', 'south') and fh >= 6 and y >= fh - 2:
                    return shade(jitter(mix(belly, main, 0.4), 5, rnd), face, x, y, fw, fh)
                base = main
                if face == 'top' and name in ('body', 'neck0', 'neck1', 'tail0', 'tail1') and abs(x - fw // 2) <= 1:
                    base = mix(main, dark, 0.3)
                return skin(name, face, x, y, fw, fh, base)
            paint(img, name, body)
    return img


MARKS = ['rims', 'cracks', 'spots', 'spine']


def marks_texture(pattern):
    rnd = random.Random(7600 + pattern)
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    kind = MARKS[pattern]

    def glow(c):
        return jitter(c, 10, rnd)

    def eyes(face, x, y, fw, fh):
        if face in ('east', 'west'):
            return None if y == 1 and x == 1 else glow(LICHEN_HOT)
        return None
    for name, w, h, d in PARTS:
        if name in PLATES and kind in ('rims', 'spine'):
            def rim(face, x, y, fw, fh):
                if face in ('east', 'west'):
                    return glow(LICHEN) if y <= (1 if kind == 'rims' else 0) or kind == 'rims' and (x == 0 or x == fw - 1) and y < fh - 2 else None
                if face == 'top':
                    return glow(LICHEN_HOT)
                return None
            paint(img, name, rim)
        elif kind == 'cracks' and name in {'body', 'neck0', 'neck1', 'skull', 'tail0', 'tail1', 'tail2', 'front_upper', 'back_upper'} | PLATES:
            for face, (fx, fy, fw, fh) in faces(name).items():
                if face == 'bottom' or fw < 4 or fh < 4:
                    continue
                px = img.load()
                for _ in range(max(1, fw * fh // 90)):
                    x, y = rnd.randrange(fw), rnd.randrange(fh)
                    for _ in range(rnd.randint(4, 9)):
                        if 0 <= x < fw and 0 <= y < fh:
                            px[fx + x, fy + y] = glow(rnd.choice((LICHEN, LICHEN_DIM)))
                        x += rnd.choice((-1, 0, 1))
                        y += rnd.choice((0, 1, 1))
        elif kind == 'spots' and name in ('body', 'neck0', 'skull', 'tail0', 'tail1', 'tail2', 'back_upper', 'front_upper'):
            for face, (fx, fy, fw, fh) in faces(name).items():
                if face in ('top', 'east', 'west') and fw > 4 and fh > 4:
                    px = img.load()
                    for _ in range(max(2, fw * fh // 60)):
                        cx, cy, r = rnd.randint(1, fw - 2), rnd.randint(1, fh - 2), rnd.choice((0, 1, 1))
                        c = glow(rnd.choice((LICHEN, LICHEN_DIM, LICHEN_HOT)))
                        for yy in range(-r, r + 1):
                            for xx in range(-r, r + 1):
                                if xx * xx + yy * yy <= r * r + 1 and 0 <= cx + xx < fw and 0 <= cy + yy < fh:
                                    px[fx + cx + xx, fy + cy + yy] = c
        elif kind == 'spine' and name in ('body', 'neck0', 'neck1', 'skull', 'snout', 'tail0', 'tail1', 'tail2', 'tail3'):
            def sp(face, x, y, fw, fh):
                return glow(LICHEN) if face == 'top' and abs(x - fw // 2) == 0 and y % 2 == 0 else None
            paint(img, name, sp)
    paint(img, 'eye', eyes)
    return img


# ---- the preview ---------------------------------------------------------------------------------------------------

def rotm(rx, ry, rz):
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    X = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    Y = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    Z = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    return mm(mm(Z, Y), X)


def mm(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def mv(a, v):
    return [sum(a[i][k] * v[k] for k in range(3)) for i in range(3)]


def world_transforms(hidden, extra):
    by = {gm[0]: gm for gm in GEOMETRY}
    cache = {}

    def tf(name):
        if name in cache:
            return cache[name]
        _, parent, _, _, (px, py, pz, rx, ry, rz), _ = by[name]
        ex = extra.get(name, (0, 0, 0))
        R = rotm(rx + ex[0], ry + ex[1], rz + ex[2])
        if parent is None:
            res = (R, [px, py, pz])
        else:
            PR, PT = tf(parent)
            res = (mm(PR, R), [PT[k] + mv(PR, [px, py, pz])[k] for k in range(3)])
        cache[name] = res
        return res
    return tf


def render(tex, out_img, ox, oy, yaw, scale, hidden=(), extra=None):
    extra = extra or {}
    tf = world_transforms(hidden, extra)
    px = tex.load()
    polys = []
    cy_, sy_ = math.cos(yaw), math.sin(yaw)
    pitch = 0.42
    cp, sp = math.cos(pitch), math.sin(pitch)

    def proj(p):
        # model: x left(-)/right, y down, z back. View from the front-left, a little above.
        x, y, z = p[0], -p[1] + 24, p[2]
        X = x * cy_ - z * sy_
        Z = x * sy_ + z * cy_
        Y = y * cp + Z * sp
        D = Z * cp - y * sp
        return (ox + X * scale, oy - Y * scale, D)

    for name, parent, uv, (x, y, z, w, h, d), _, mirror in GEOMETRY:
        if name in hidden:
            continue
        R, T = tf(name)
        W, H, Dd = DIMS[uv]

        def P(a, b, c):
            q = mv(R, [a, b, c])
            return proj([q[k] + T[k] for k in range(3)])
        fr = faces(uv)
        # each face: origin corner, u axis, v axis (model units per texel), texture rect
        x0, y0, z0, x1, y1, z1 = x, y, z, x + w, y + h, z + d
        sx, sy, sz = w / W, h / H, d / Dd
        spec = {
            'top': ((x0, y0, z0), (sx, 0, 0), (0, 0, sz)),
            'bottom': ((x0, y1, z0), (sx, 0, 0), (0, 0, sz)),
            'north': ((x0, y0, z0), (sx, 0, 0), (0, sy, 0)),
            'south': ((x1, y0, z1), (-sx, 0, 0), (0, sy, 0)),
            'east': ((x0, y0, z1), (0, 0, -sz), (0, sy, 0)),
            'west': ((x1, y0, z0), (0, 0, sz), (0, sy, 0)),
        }
        for face, (o, ua, va) in spec.items():
            fx, fy, fw, fh = fr[face]
            for ty in range(fh):
                for txx in range(fw):
                    tx = fw - 1 - txx if mirror and face in ('top', 'bottom', 'north', 'south') else txx
                    c = px[fx + tx, fy + ty]
                    if c[3] == 0:
                        continue
                    a = [o[k] + ua[k] * txx + va[k] * ty for k in range(3)]
                    b = [a[k] + ua[k] for k in range(3)]
                    cc = [b[k] + va[k] for k in range(3)]
                    dd = [a[k] + va[k] for k in range(3)]
                    pts = [P(*q) for q in (a, b, cc, dd)]
                    depth = sum(p[2] for p in pts) / 4
                    polys.append((depth, [(p[0], p[1]) for p in pts], c[:3]))
    polys.sort(key=lambda p: -p[0])
    dr = ImageDraw.Draw(out_img)
    for _, pts, c in polys:
        dr.polygon(pts, fill=c)


def preview(path, hides, marks):
    W, Hh = 1400, 760
    img = Image.new('RGB', (W, Hh), (34, 38, 44))
    dr = ImageDraw.Draw(img)
    for i, hide in enumerate(hides):
        col, row = i % 2, i // 2
        ox, oy = 360 + col * 680, 330 + row * 370
        saddled = i == 0
        hidden = set() if saddled else {'saddle', 'pommel', 'cantle', 'strap', 'pad'}
        if saddled:
            hidden |= {'plate1_l', 'plate1_r', 'plate2_l', 'plate2_r', 'plate3_l', 'plate3_r'}
        hidden |= {'short_horn_l', 'short_horn_r'} if i != 2 else {'horn_l', 'horn_r'}
        if i != 3:
            hidden.add('nose_horn')
        comp = Image.alpha_composite(hide, marks[i]) if False else hide
        render(comp, img, ox, oy, 2.4 if col == 0 else 1.5708, 4.0, hidden)
        dr.text((ox - 300, oy - 300), ['moss stone, saddled', 'jungle green', 'sandstone, short horns', 'slate, nose horn'][i], fill=(220, 220, 220))
    img.save(path)


def species():
    folder = ASSETS + 'textures/entity/dragon/stegonaut/'
    os.makedirs(folder, exist_ok=True)
    hides = [hide_texture(i) for i in range(len(HIDES))]
    marks = [marks_texture(i) for i in range(len(MARKS))]
    for i, h in enumerate(hides):
        h.save(folder + 'hide_%d.png' % i)
    for i, m in enumerate(marks):
        m.save(folder + 'marks_%d.png' % i)
    return hides, marks


if __name__ == '__main__':
    for d in (ITEM, ASSETS + 'items/', ASSETS + 'models/item/', DATA + 'jaymoskingdoms/loot_table/entities/'):
        os.makedirs(d, exist_ok=True)
    hides, marks = species()
    egg('stegonaut_dragon_spawn_egg', rgb(0x6E7A5A), rgb(0x5AE8C0), 113)
    write(ASSETS + 'items/stegonaut_dragon_spawn_egg.json', {'model': {'type': 'minecraft:model', 'model': 'jaymoskingdoms:item/stegonaut_dragon_spawn_egg'}})
    write(ASSETS + 'models/item/stegonaut_dragon_spawn_egg.json',
          {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jaymoskingdoms:item/stegonaut_dragon_spawn_egg'}})
    write(DATA + 'jaymoskingdoms/loot_table/entities/stegonaut_dragon.json',
          {'type': 'minecraft:entity', 'pools': [{'rolls': 1, 'entries': [e]} for e in (
              entry('minecraft:leather', 3, 6), entry('minecraft:bone', 2, 5), entry('minecraft:beef', 3, 6),
              entry('minecraft:flint', 1, 4), entry('minecraft:moss_block', 0, 2), entry('minecraft:armadillo_scute', 0, 2))],
           'random_sequence': 'jaymoskingdoms:entities/stegonaut_dragon'})
    out = sys.argv[1] if len(sys.argv) > 1 else 'tools/out/stegonaut-preview.png'
    os.makedirs(os.path.dirname(out) or '.', exist_ok=True)
    preview(out, hides, marks)
