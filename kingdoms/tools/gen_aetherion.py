"""Aetherion's art and data (kingdoms/src/main/java/com/jaymoskingdoms/dragons/AetherionDragon), our own model. Andy sent
an Aetherion (a public-domain, CC0, GeckoLib dragon, not Saint's Dragons'): "the wings and head might need upgrading".
Its body plan is kept (a four-legged flier, four neck segments, seven tail segments ending in a fan) and its wings and
head are drawn anew: each wing an arm, a forearm and a hand of three fanned finger bones with a thumb claw, a membrane
stretched between them with a scalloped trailing edge; a horned head with a brow ridge, two swept horns, cheek frills,
a jaw that opens, and a crest of crystal spines down the neck and back.
- the body plan (GEOMETRY), printed as the Java for AetherionDragonModel's createBodyLayer (a 512x512 sheet);
- four hides (night sky, pale silver, dusk violet, dawn gold) and four glowing patterns (constellations on the membranes,
  starlit veins, rims, a sky full of stars), a star-white light, and its eyes;
- the spawn egg's icon and item model, and the loot table;
- a preview render of all four hides, wings spread and folded.
Run from kingdoms/: python3 -I tools/gen_aetherion.py [preview.png]"""
import math, os, random, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image, ImageDraw
from gen_dragons import rgb, jitter, mix, shade, write, entry, egg, ASSETS, DATA, ITEM
import lesser_dragon_art
from lesser_dragon_art import Plan, chain, noise_field, FACE_I

SIZE = 512
lesser_dragon_art.SIZE = SIZE

# ---- the body plan -------------------------------------------------------------------------------------------------
# high on four long legs (the belly 31 pixels up), a deep chest, the neck rising in an S, a long tail ending in a fan
NECK = [('neck0', (12, 11, 9), -0.7), ('neck1', (11, 10, 8), -0.35), ('neck2', (10, 9, 8), -0.05), ('neck3', (9, 8, 7), 0.3)]
TAIL = [('tail0', (14, 11, 10), -0.3), ('tail1', (12, 10, 10), -0.08), ('tail2', (11, 9, 10), 0.0), ('tail3', (9, 8, 10), 0.04),
        ('tail4', (8, 7, 10), 0.05), ('tail5', (6, 5, 10), 0.06), ('tail6', (5, 4, 9), 0.06)]

GEOMETRY = [
    ('body', None, 'body', (-10, -8, -17, 20, 16, 34), (0, -15, 0, 0, 0, 0), False),
    ('chest', 'body', 'chest', (-9, -6, -6, 18, 13, 12), (0, 2, -12, 0.2, 0, 0), False),
    ('belly', 'body', 'belly', (-8, 0, -14, 16, 2, 28), (0, 8, 0, 0, 0, 0), False),
]
chain(GEOMETRY, 'body', (0, -3, -16), NECK, -1)
GEOMETRY += [
    # the head: a long skull under a heavy brow, a tapering snout, a jaw hinged under the skull's back
    ('head', 'neck3', 'skull', (-7, -6, -12, 14, 10, 12), (0, -0.5, -5, 0.45, 0, 0), False),
    ('snout', 'head', 'snout', (-5, -3.5, -12, 10, 7, 12), (0, -0.5, -11, 0.06, 0, 0), False),
    ('nose', 'snout', 'nose', (-4, -2.5, -3, 8, 5, 3), (0, -1, -11.5, 0.1, 0, 0), False),
    ('jaw', 'head', 'jaw', (-4.5, 0, -22, 9, 3, 22), (0, 4, -1.5, 0, 0, 0), False),
    ('brow_l', 'head', 'brow', (-1.5, -1.5, -5, 3, 3, 10), (5.5, -6, -7, 0.12, 0.12, 0), False),
    ('brow_r', 'head', 'brow', (-1.5, -1.5, -5, 3, 3, 10), (-5.5, -6, -7, 0.12, -0.12, 0), True),
    ('eye_l', 'head', 'eye', (-1, -1, -1.5, 2, 2, 3), (6.5, -3, -8, 0, 0, 0), False),
    ('eye_r', 'head', 'eye', (-1, -1, -1.5, 2, 2, 3), (-6.5, -3, -8, 0, 0, 0), True),
    # two great horns swept back and up from the brow, in two pieces; a short pair beneath; frills at the cheeks
    ('horn_l', 'head', 'horn', (-2, -2, 0, 4, 4, 13), (4.5, -6, -1, 0.95, 0.3, 0), False),
    ('horn_r', 'head', 'horn', (-2, -2, 0, 4, 4, 13), (-4.5, -6, -1, 0.95, -0.3, 0), True),
    ('horn_tip_l', 'horn_l', 'horn_tip', (-1.5, -1.5, 0, 3, 3, 10), (0, 0, 12, -0.25, 0, 0), False),
    ('horn_tip_r', 'horn_r', 'horn_tip', (-1.5, -1.5, 0, 3, 3, 10), (0, 0, 12, -0.25, 0, 0), True),
    ('low_horn_l', 'head', 'low_horn', (-1, -1, 0, 2, 2, 7), (6.5, -1, 0, 0.2, 0.55, 0), False),
    ('low_horn_r', 'head', 'low_horn', (-1, -1, 0, 2, 2, 7), (-6.5, -1, 0, 0.2, -0.55, 0), True),
    ('frill_l', 'head', 'frill', (0, -4, 0, 1, 8, 9), (7, 1, -3, 0.35, 0.5, 0), False),
    ('frill_r', 'head', 'frill', (-1, -4, 0, 1, 8, 9), (-7, 1, -3, 0.35, -0.5, 0), True),
]
chain(GEOMETRY, 'body', (0, -4, 16), TAIL, 1, 0.3)
GEOMETRY += [
    # the tail's fan: two broad vanes crossed into a star
    ('fan', 'tail6', 'fan', (-8, -0.5, 0, 16, 1, 12), (0, 0, 7, 0, 0, 0), False),
    ('fan_tip', 'fan', 'fan_tip', (-3, -0.5, 0, 6, 1, 6), (0, 0, 11, 0, 0, 0), False),
    ('fan_crest', 'tail6', 'fan_crest', (-0.5, -5, 0, 1, 5, 12), (0, -1, 5, 0, 0, 0), False),
]
# crystal spines down the neck, back and tail (the back's five are its look's spikes; the middle three give way to the saddle)
for i, (n, (w, h, d), _) in enumerate(NECK):
    GEOMETRY.append(('nspine%d' % i, n, 'spine_s', (-1, -4, -2, 2, 4, 3), (0, -h / 2, -d / 2, -0.4, 0, 0), False))
for i in range(5):
    GEOMETRY.append(('spine%d' % i, 'body', 'spine', (-1.5, -7, -2, 3, 7, 4), (0, -8, -13 + i * 6.5, -0.45, 0, 0), False))
for i, (n, (w, h, d), _) in enumerate(TAIL[:6]):
    GEOMETRY.append(('tspine%d' % i, n, 'spine_s', (-1, -4, -2, 2, 4, 3), (0, -h / 2, d / 2, -0.5, 0, 0), False))
GEOMETRY += [
    ('saddle', 'body', 'saddle', (-7, -2, -6, 14, 2, 12), (0, -8, -3, 0, 0, 0), False),
    ('pommel', 'saddle', 'pommel', (-2, -3, 0, 4, 3, 2), (0, -2, -6, 0, 0, 0), False),
    ('cantle', 'saddle', 'cantle', (-6, -3, 0, 12, 3, 2), (0, -2, 4, 0, 0, 0), False),
    ('strap', 'body', 'strap', (-10.5, -8.5, -1.5, 21, 17, 3), (0, 0, -3, 0, 0, 0), False),
]
# four long legs; the hind ones with a backward ankle
LEGS = [('left_front', 7.5, -11.0, True), ('right_front', -7.5, -11.0, True), ('left_hind', 7.5, 12.0, False), ('right_hind', -7.5, 12.0, False)]
for name, x, z, front in LEGS:
    m = x < 0
    if front:
        GEOMETRY.append((name + '_leg', 'body', 'front_upper', (-3, -2, -3.5, 6, 16, 7), (x, 6, z, 0.1, 0, 0), m))
        GEOMETRY.append((name + '_shin', name + '_leg', 'front_lower', (-2.5, 0, -2.5, 5, 17, 5), (0, 13.5, 0, -0.2, 0, 0), m))
        GEOMETRY.append((name + '_foot', name + '_shin', 'foot', (-3.5, 0, -6, 7, 3, 8), (0, 16.5, 0, 0.1, 0, 0), m))
    else:
        GEOMETRY.append((name + '_leg', 'body', 'back_upper', (-4, -3, -5, 8, 15, 10), (x, 5, z, -0.3, 0, 0), m))
        GEOMETRY.append((name + '_shin', name + '_leg', 'back_lower', (-2.5, 0, -2.5, 5, 21, 5), (0, 11.5, 1.5, 0.55, 0, 0), m))
        GEOMETRY.append((name + '_foot', name + '_shin', 'foot', (-3.5, 0, -6, 7, 3, 8), (0, 20, 0, -0.25, 0, 0), m))

# the wings: arm, forearm, hand, each with its membrane behind it; the hand's three fingers fan out and back, each with
# a membrane panel; a thumb claw at the wrist. Spread (the rest pose here) they're level; the model folds them.
FINGERS = [(42, 0.2, 30), (36, 1.0, 26), (30, 1.75, 18)]
for side, sx in (('l', 1), ('r', -1)):
    m = sx < 0

    def bx(x, y, z, w, h, d):
        # a box running out along +x on the left, mirrored onto -x on the right
        return (x, y, z, w, h, d) if sx > 0 else (-x - w, y, z, w, h, d)
    GEOMETRY += [
        ('wing_%s' % side, 'body', 'wing_arm', bx(0, -2, -2, 26, 4, 4), (sx * 9, -6, -8, 0, 0, 0), m),
        ('wing_mem_%s' % side, 'wing_%s' % side, 'wing_mem_arm', bx(0, -0.5, 1, 26, 1, 34), (0, 0, 0, 0, 0, 0), m),
        ('wing_fore_%s' % side, 'wing_%s' % side, 'wing_fore', bx(0, -1.5, -1.5, 30, 3, 3), (sx * 25.5, 0, 0, 0, 0, 0), m),
        ('wing_fmem_%s' % side, 'wing_fore_%s' % side, 'wing_mem_fore', bx(0, -0.5, 1, 30, 1, 34), (0, 0, 0, 0, 0, 0), m),
        ('wing_thumb_%s' % side, 'wing_fore_%s' % side, 'thumb', bx(0, -1, -1, 2, 2, 5), (sx * 29.5, 0, -1, 0, sx * -0.9, 0), m),
        ('wing_hand_%s' % side, 'wing_fore_%s' % side, 'wing_hand', bx(0, -1.5, -1.5, 3, 3, 3), (sx * 29.5, 0, 0, 0, 0, 0), m),
    ]
    for f, (length, spread, width) in enumerate(FINGERS):
        GEOMETRY.append(('finger%d_%s' % (f, side), 'wing_hand_%s' % side, 'finger%d' % f, bx(0, -1, -1, length, 2, 2),
                         (sx * 1.5, 0, 0, 0, -sx * spread, 0), m))
        GEOMETRY.append(('fmem%d_%s' % (f, side), 'finger%d_%s' % (f, side), 'fmem%d' % f, bx(0, -0.5, 1, length, 1, width), (0, 0, 0, 0, 0, 0), m))

PLAN = Plan(GEOMETRY)
PARTS, UV = PLAN.parts, PLAN.uv

# ---- the hides -----------------------------------------------------------------------------------------------------
# (main, dark, belly, membrane, spine crystal)
HIDES = [
    (0x2A3460, 0x141A36, 0x8A94C0, 0x3A2E6A, 0xB8E4FF),  # night sky
    (0xBCC4D4, 0x7A849C, 0xF0F2F8, 0x8C9AC4, 0xE8F6FF),  # pale silver
    (0x4A3468, 0x261A3A, 0xA890C0, 0x6A3C7A, 0xF0B8FF),  # dusk violet
    (0xC8A050, 0x7C5C28, 0xF4E2B0, 0xC07840, 0xFFF0B8),  # dawn gold
]
HORN, HORN_TIP, CLAW = rgb(0x2A2A3A), rgb(0xE8ECF8), rgb(0x18181E)
EYE, PUPIL, MOUTH, TEETH = rgb(0xBFE8FF), rgb(0x0A1020), rgb(0x5A2A48), rgb(0xF0F0F8)
LEATHER, LEATHER_DARK, SILVER, CLOTH = rgb(0x3E2E4A), rgb(0x261C30), rgb(0xD0D8E8), rgb(0x2A3A7A)
STAR, STAR_HOT, STAR_DIM = rgb(0xCFE8FF), rgb(0xFFFFFF), rgb(0x7FA8E8)

MEMBRANES = {'wing_mem_arm', 'wing_mem_fore', 'fmem0', 'fmem1', 'fmem2'}
BONES = {'wing_arm', 'wing_fore', 'wing_hand', 'finger0', 'finger1', 'finger2'}
CRYSTAL = {'spine', 'spine_s', 'fan_crest'}
HORNY = {'horn', 'horn_tip', 'low_horn', 'thumb'}
FANS = {'fan', 'fan_tip'}
TACK = {'saddle', 'pommel', 'cantle', 'strap'}
BELLIES = {'body', 'chest', 'belly', 'jaw'} | {n for n, _, _ in NECK} | {n for n, _, _ in TAIL}
LEGS_UV = {'front_upper', 'front_lower', 'back_upper', 'back_lower'}


def scallop(name, face, x, y, fw, fh):
    """A membrane's trailing edge (its back, +z, rows of the top and bottom faces) cut in scallops between the bones."""
    if name not in MEMBRANES:
        return False
    if face == 'south':
        return True
    if face in ('east', 'west'):
        # the thin ends: kept only along the bone at the front
        return x > 3 if face == 'west' else x < fw - 4
    if face not in ('top', 'bottom'):
        return False
    # (both faces run front to back down their rows)
    depth = fh - 1 - y
    # scallops along its length: deepest halfway between the ends
    k = (x + 0.5) / fw
    # each panel tapers toward its outer end (the arm's a little, the forearm's to meet the wrist, a finger's to its tip),
    # with a scallop sagging between the bones
    taper = {'wing_mem_arm': 7.0, 'wing_mem_fore': 20.0}.get(name, fh * 0.8)
    cut = 2.0 * math.sin(k * math.pi) ** 2 + taper * k ** 1.5
    return depth < cut


def hide_texture(variant):
    main, dark, belly, membrane, crystal = (rgb(c) for c in HIDES[variant])
    rnd = random.Random(7300 + variant)
    field = noise_field(730 + variant)
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))

    def skin(name, face, x, y, fw, fh, base):
        u, v = UV[name]
        n = field(u + x * 1.1 + FACE_I.index(face) * 7, v + y * 1.1)
        c = base
        # small overlapping scales: a dark rim on every other row
        if y % 2 == 1 and (x + y // 2) % 3 == 0:
            c = mix(c, dark, 0.35)
        if n > 0.62:
            c = mix(c, dark, min(1.0, (n - 0.62) * 2.0) * 0.5)
        elif n < 0.3:
            c = mix(c, belly, (0.3 - n) * 0.3)
        return shade(jitter(c, 4, rnd), face, x, y, fw, fh)

    for name, w, h, d in PARTS:
        if name in MEMBRANES:
            def mem(face, x, y, fw, fh, name=name):
                if scallop(name, face, x, y, fw, fh):
                    return (0, 0, 0, 0)
                c = membrane
                # veins running out along the membrane, and a darker rim at its trailing edge
                if face in ('top', 'bottom') and (x % 7 == 3):
                    c = mix(c, dark, 0.35)
                if face in ('top', 'bottom'):
                    depth = fh - 1 - y
                    if depth < 4:
                        c = mix(c, dark, 0.25)
                    if face == 'bottom':
                        c = mix(c, belly, 0.15)
                return jitter(c, 5, rnd)
            PLAN.paint(img, name, mem)
        elif name in BONES:
            PLAN.paint(img, name, lambda face, x, y, fw, fh: shade(jitter(mix(main, dark, 0.15) if (x // 3) % 2 else main, 4, rnd), face, x, y, fw, fh))
        elif name in CRYSTAL:
            def cry(face, x, y, fw, fh):
                c = mix(crystal, main, 0.35) if y >= fh - 1 else crystal
                if (x + y) % 3 == 0:
                    c = mix(c, rgb(0xFFFFFF), 0.3)
                return shade(jitter(c, 6, rnd), face, x, y, fw, fh)
            PLAN.paint(img, name, cry)
        elif name in FANS:
            def fan(face, x, y, fw, fh, name=name):
                if face in ('top', 'bottom'):
                    # a diamond: the corners cut away
                    cx, cy = (x + 0.5) / fw - 0.5, (y + 0.5) / fh
                    if abs(cx) * 2 > (1.0 - cy) * 1.1 + 0.25 and name == 'fan':
                        return (0, 0, 0, 0)
                    c = mix(membrane, crystal, 0.25) if (x + y) % 4 else crystal
                    return jitter(c, 5, rnd)
                return shade(jitter(membrane, 5, rnd), face, x, y, fw, fh)
            PLAN.paint(img, name, fan)
        elif name in HORNY:
            def horn(face, x, y, fw, fh, name=name):
                t = 0.25 if face not in ('top', 'bottom', 'south') else 0.25 + y / max(1, fh - 1) * 0.6
                if face == 'south' or name == 'horn_tip' and face != 'north':
                    t = 0.4 + 0.6 * (y / max(1, fh - 1) if face in ('top', 'bottom') else 1.0)
                return shade(jitter(mix(HORN, HORN_TIP, min(1.0, t)), 5, rnd), face, x, y, fw, fh)
            PLAN.paint(img, name, horn)
        elif name == 'frill':
            def frill(face, x, y, fw, fh):
                if face in ('east', 'west') and (y < 2 and x < 3 or y < 1):
                    return (0, 0, 0, 0)
                return shade(jitter(mix(membrane, crystal, 0.2) if x % 2 else membrane, 5, rnd), face, x, y, fw, fh)
            PLAN.paint(img, name, frill)
        elif name in TACK:
            def tack(face, x, y, fw, fh, name=name):
                c = LEATHER_DARK if x in (0, fw - 1) or y in (0, fh - 1) else LEATHER
                if name == 'saddle' and face == 'top' and 2 <= x <= fw - 3 and 2 <= y <= fh - 3:
                    c = CLOTH if (x + y) % 5 else SILVER
                if name == 'strap' and face in ('east', 'west') and fh // 2 - 1 <= y <= fh // 2 + 1 and fw // 2 - 1 <= x <= fw // 2:
                    c = SILVER
                return shade(jitter(c, 4, rnd), face, x, y, fw, fh)
            PLAN.paint(img, name, tack)
        elif name == 'eye':
            PLAN.paint(img, name, lambda face, x, y, fw, fh: (PUPIL if x == 1 else jitter(EYE, 6, rnd)) if face in ('east', 'west')
                       else shade(jitter(dark, 4, rnd), face, x, y, fw, fh))
        else:
            def body(face, x, y, fw, fh, name=name):
                if name == 'foot':
                    if face == 'north' and x in (0, 3, 6) and y >= 1:
                        return jitter(CLAW, 4, rnd)
                    if face == 'bottom':
                        return shade(jitter(dark, 4, rnd), face, x, y, fw, fh)
                if name == 'jaw' and face == 'top':
                    return TEETH if (x in (0, fw - 1) and y % 2 == 0) else jitter(MOUTH, 6, rnd)
                if name == 'jaw' and face in ('east', 'west') and y == 0:
                    return TEETH if x % 3 == 0 else shade(jitter(dark, 4, rnd), face, x, y, fw, fh)
                if name == 'snout' and face in ('east', 'west') and y == fh - 1:
                    return TEETH if x % 3 == 1 else jitter(MOUTH, 4, rnd)
                if name == 'nose' and face == 'north' and y == 1 and x in (1, fw - 2):
                    return rgb(0x080A14)
                if name == 'brow':
                    return shade(jitter(mix(dark, crystal, 0.15) if y == 0 else dark, 4, rnd), face, x, y, fw, fh)
                if face == 'bottom' and name in BELLIES:
                    return shade(jitter(mix(belly, dark, 0.2) if y % 3 == 2 else belly, 5, rnd), face, x, y, fw, fh)
                if name in BELLIES and face in ('east', 'west', 'north', 'south') and fh >= 6 and y >= fh - 2:
                    return shade(jitter(mix(belly, main, 0.5), 5, rnd), face, x, y, fw, fh)
                return skin(name, face, x, y, fw, fh, main)
            PLAN.paint(img, name, body)
    return img


MARKS = ['constellations', 'veins', 'rims', 'stars']


def marks_texture(pattern):
    rnd = random.Random(7600 + pattern)
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    kind = MARKS[pattern]

    def glow(c):
        return jitter(c, 10, rnd)
    for name, w, h, d in PARTS:
        fr = PLAN.faces(name)
        px = img.load()
        if name == 'eye':
            PLAN.paint(img, name, lambda face, x, y, fw, fh: (None if x == 1 else glow(STAR_HOT)) if face in ('east', 'west') else None)
            continue
        if name in CRYSTAL:
            # the crystal spines always glow faintly at their tips
            PLAN.paint(img, name, lambda face, x, y, fw, fh: glow(STAR_DIM) if y <= 1 and face != 'bottom' else None)
        if kind in ('constellations', 'stars') and (name in MEMBRANES or kind == 'stars' and name not in TACK):
            for face in ('top', 'bottom') if name in MEMBRANES else ('top', 'east', 'west'):
                fx, fy, fw, fh = fr[face]
                count = max(1, fw * fh // (14 if name in MEMBRANES else 40))
                pts = []
                for _ in range(count):
                    x, y = rnd.randrange(fw), rnd.randrange(fh)
                    if scallop(name, face, x, y, fw, fh):
                        continue
                    pts.append((x, y))
                    px[fx + x, fy + y] = glow(rnd.choice((STAR, STAR_HOT, STAR_DIM)))
                if kind == 'constellations' and name in MEMBRANES and len(pts) > 2:
                    # a few stars joined by faint lines
                    dr = ImageDraw.Draw(img)
                    for a, b in zip(pts[:5], pts[1:6]):
                        dr.line([(fx + a[0], fy + a[1]), (fx + b[0], fy + b[1])], fill=tuple(STAR_DIM[:3]) + (255,))
                    for x, y in pts[:6]:
                        px[fx + x, fy + y] = tuple(STAR_HOT[:3]) + (255,)
        elif kind == 'veins' and (name in MEMBRANES or name in BONES):
            PLAN.paint(img, name, lambda face, x, y, fw, fh, name=name: glow(STAR) if face in ('top', 'bottom') and
                       (name in BONES and y == fh // 2 or name in MEMBRANES and x % 7 == 3 and not scallop(name, face, x, y, fw, fh)) else None)
        elif kind == 'rims' and (name in MEMBRANES or name in FANS or name == 'frill'):
            def rim(face, x, y, fw, fh, name=name):
                if face not in ('top', 'bottom') and name != 'frill':
                    return None
                if name in MEMBRANES:
                    depth = fh - 1 - y
                    edge = not scallop(name, face, x, y, fw, fh) and (scallop(name, face, x, y + 1, fw, fh) or depth == 0)
                    return glow(STAR) if edge else None
                return glow(STAR) if x in (0, fw - 1) or y in (0, fh - 1) else None
            PLAN.paint(img, name, rim)
    return img


def beat_pose(phase):
    """Mid-wingbeat, as the model beats them: the arm swung up (phase pi/2) or down, each later joint a beat behind."""
    e = {}
    for side, sx in (('l', 1), ('r', -1)):
        e['wing_%s' % side] = (0, sx * math.cos(phase) * 0.14, -sx * (0.1 + 0.65 * math.sin(phase)))
        e['wing_fore_%s' % side] = (0, 0, -sx * 0.32 * math.sin(phase - 0.7))
        e['wing_hand_%s' % side] = (0, 0, -sx * 0.28 * math.sin(phase - 1.4))
    return e


def folded_pose():
    """Wings tucked: the arm raised, the forearm folded down along it, the hand up again, the fingers closed."""
    e = {}
    for side, sx in (('l', 1), ('r', -1)):
        e['wing_%s' % side] = (0, sx * -0.35, sx * -1.25)
        e['wing_fore_%s' % side] = (0, sx * 0.2, sx * 2.75)
        e['wing_hand_%s' % side] = (0, 0, sx * -2.55)
        for f, (_, spread, _) in enumerate(FINGERS):
            e['finger%d_%s' % (f, side)] = (0, sx * spread * 0.8, 0)
    return e


def preview(path, hides, marks):
    W, Hh = 1800, 1100
    img = Image.new('RGB', (W, Hh), (34, 38, 44))
    dr = ImageDraw.Draw(img)
    labels = ['night sky, constellations, saddled', 'pale silver, wings folded', 'dusk violet, starlit veins, upstroke',
              'dawn gold, wings folded']
    for i, hide in enumerate(hides):
        tex = hide.copy()
        tex.alpha_composite(marks[i])
        col, row = i % 2, i // 2
        ox, oy = 450 + col * 900, 380 + row * 540
        hidden = set() if i == 0 else set(TACK)
        extra = folded_pose() if i in (1, 3) else beat_pose(math.pi / 2) if i == 2 else {}
        PLAN.render(tex, img, ox, oy, 2.5 if col == 0 else 1.75, 2.5, hidden, extra)
        dr.text((ox - 430, oy - 360), labels[i], fill=(220, 220, 220))
    img.save(path)


if __name__ == '__main__':
    for d_ in (ITEM, ASSETS + 'items/', ASSETS + 'models/item/', DATA + 'jaymoskingdoms/loot_table/entities/'):
        os.makedirs(d_, exist_ok=True)
    folder = ASSETS + 'textures/entity/dragon/aetherion/'
    os.makedirs(folder, exist_ok=True)
    hides = [hide_texture(i) for i in range(len(HIDES))]
    marks = [marks_texture(i) for i in range(len(MARKS))]
    for i, h in enumerate(hides):
        h.save(folder + 'hide_%d.png' % i)
    for i, m in enumerate(marks):
        m.save(folder + 'marks_%d.png' % i)
    egg('aetherion_dragon_spawn_egg', rgb(0x2A3460), rgb(0xCFE8FF), 141)
    write(ASSETS + 'items/aetherion_dragon_spawn_egg.json', {'model': {'type': 'minecraft:model', 'model': 'jaymoskingdoms:item/aetherion_dragon_spawn_egg'}})
    write(ASSETS + 'models/item/aetherion_dragon_spawn_egg.json',
          {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jaymoskingdoms:item/aetherion_dragon_spawn_egg'}})
    write(DATA + 'jaymoskingdoms/loot_table/entities/aetherion_dragon.json',
          {'type': 'minecraft:entity', 'pools': [{'rolls': 1, 'entries': [e]} for e in (
              entry('minecraft:leather', 5, 10), entry('minecraft:bone', 4, 8), entry('minecraft:phantom_membrane', 2, 5),
              entry('minecraft:amethyst_shard', 3, 8), entry('minecraft:glowstone_dust', 2, 6), entry('minecraft:diamond', 0, 2))],
           'random_sequence': 'jaymoskingdoms:entities/aetherion_dragon'})
    out = sys.argv[1] if len(sys.argv) > 1 else 'tools/out/aetherion-preview.png'
    os.makedirs(os.path.dirname(out) or '.', exist_ok=True)
    preview(out, hides, marks)
    sys.stderr.write('top of the model: %.1f px over the ground\n' % (24 - PLAN.top_y()))
    print(PLAN.java())
