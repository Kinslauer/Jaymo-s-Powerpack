"""Varasuchus's art and data (kingdoms/src/main/java/com/jaymoskingdoms/dragons/VarasuchusDragon), all our own design.
Its moves are ported from Saint's Dragons by Leon Saint (MIT code); none of that mod's art, models or sounds are used
(they are All Rights Reserved), see docs/SaintsDragons-Plan.md.
A crocodile dragon, high on long sprawling legs: a broad armoured body under two rows of bony scutes, a thick short
neck, a long-jawed head with eyes on its crown and horns swept back from it (a nose horn on some), and a great tail
with a double crest that runs into one.
- the body plan (GEOMETRY), printed as the Java for VarasuchusDragonModel's createBodyLayer;
- four hides (swamp olive, river brown, mangrove black, pale sand) and four glowing patterns (bands, spots, scute rims,
  jaw line), a marsh-light green;
- the spawn egg's icon and item model, and the loot table;
- a preview render of all four hides.
Run from kingdoms/: python3 -I tools/gen_varasuchus.py [preview.png]"""
import os, random, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image, ImageDraw
from gen_dragons import rgb, jitter, mix, shade, write, entry, egg, ASSETS, DATA, ITEM, SIZE
from lesser_dragon_art import Plan, chain, noise_field, FACE_I

# ---- the body plan -------------------------------------------------------------------------------------------------
# (Andy: "longer limbs ... and be taller, to be imposing") its belly 20 pixels off the ground on long sprawling legs
NECK = [('neck0', (16, 12, 8), -0.3), ('neck1', (15, 11, 7), -0.1)]
# (Andy: "the tail a lot longer so it can realistically wrap around to the front for the parry/attacks") twelve segments,
# about 110 pixels: long enough to curl round its flank to its head
TAIL = [('tail0', (17, 12, 10), -0.2), ('tail1', (16, 11, 10), -0.1), ('tail2', (15, 10, 10), -0.08), ('tail3', (14, 9, 10), -0.05),
        ('tail4', (12, 9, 10), 0.0), ('tail5', (11, 8, 10), 0.04), ('tail6', (10, 7, 10), 0.05), ('tail7', (9, 6, 10), 0.05),
        ('tail8', (8, 6, 9), 0.04), ('tail9', (6, 5, 9), 0.03), ('tail10', (5, 4, 9), 0.03), ('tail11', (4, 3, 9), 0.03)]
SCUTE_Z = [-15.0, -7.5, 0.0, 7.5, 15.0]

GEOMETRY = [
    ('body', None, 'body', (-11, -7, -20, 22, 14, 40), (0, -9, 0, 0, 0, 0), False),
    ('belly', 'body', 'belly', (-9, 0, -16, 18, 2, 32), (0, 7, 0, 0, 0, 0), False),
]
chain(GEOMETRY, 'body', (0, -1, -19), NECK, -1)
GEOMETRY += [
    # (Andy: "the head needs to be blockier and built") a deep square skull, a broad blunt snout, a heavy jaw, a ridge of
    # bone over the eyes and bosses on the cheeks
    ('head', 'neck1', 'skull', (-9, -6.5, -14, 18, 12, 14), (0, 0, -6, 0.36, 0, 0), False),
    ('snout', 'head', 'snout', (-7, -3.5, -18, 14, 8, 18), (0, -0.5, -14, 0, 0, 0), False),
    ('snout_tip', 'snout', 'snout_tip', (-7.5, -4, -4, 15, 8, 4), (0, 0, -15, 0, 0, 0), False),
    ('jaw', 'head', 'jaw', (-7.5, 0, -33, 15, 4, 33), (0, 5.5, -1, 0, 0, 0), False),
    ('brow', 'head', 'brow', (-9.5, -1.5, -2.5, 19, 3, 5), (0, -6.5, -10, 0, 0, 0), False),
    ('cheek_l', 'head', 'cheek', (0, -3.5, -4.5, 3, 7, 9), (9, 1, -6, 0, 0, 0), False),
    ('cheek_r', 'head', 'cheek', (-3, -3.5, -4.5, 3, 7, 9), (-9, 1, -6, 0, 0, 0), True),
    ('eye_l', 'head', 'eye', (-1.5, -2, -1.5, 3, 2, 3), (6, -7, -8, 0, 0, 0), False),
    ('eye_r', 'head', 'eye', (-1.5, -2, -1.5, 3, 2, 3), (-6, -7, -8, 0, 0, 0), False),
    ('horn_l', 'head', 'horn', (-2, -2, 0, 4, 4, 14), (6.5, -5.5, -3, 0.62, 0.38, 0), False),
    ('horn_r', 'head', 'horn', (-2, -2, 0, 4, 4, 14), (-6.5, -5.5, -3, 0.62, -0.38, 0), False),
    ('horn_tip_l', 'horn_l', 'horn_tip', (-1.5, -1.5, 0, 3, 3, 7), (0, 0, 13, 0.4, 0, 0), False),
    ('horn_tip_r', 'horn_r', 'horn_tip', (-1.5, -1.5, 0, 3, 3, 7), (0, 0, 13, 0.4, 0, 0), False),
    ('short_horn_l', 'head', 'horn_short', (-2, -2, 0, 4, 4, 8), (6.5, -5.5, -3, 0.7, 0.38, 0), False),
    ('short_horn_r', 'head', 'horn_short', (-2, -2, 0, 4, 4, 8), (-6.5, -5.5, -3, 0.7, -0.38, 0), False),
    ('nose_horn', 'snout', 'nose_horn', (-1.5, -5, -1.5, 3, 5, 3), (0, -3.5, -13, -0.35, 0, 0), False),
]
chain(GEOMETRY, 'body', (0, -2, 19), TAIL, 1, 0.3)
# the tail's crest: two rows down its root, one row toward its tip
for i, (tn, (w, h, d), _) in enumerate(TAIL[1:], start=1):
    if i <= 5:
        for side, sx in (('l', 1), ('r', -1)):
            GEOMETRY.append(('crest%d_%s' % (i, side), tn, 'crest', (-0.5, -3, 0, 1, 3, 7), (sx * (w / 2 - 2), -h / 2, 1.5, 0, 0, 0), side == 'r'))
    else:
        GEOMETRY.append(('crest%d' % i, tn, 'crest_tip', (-0.5, -3, 0, 1, 3, 6), (0, -h / 2, 1.5, 0, 0, 0), False))
# the bony scutes down its back (the middle three give way to the saddle)
for i in range(5):
    for side, sx in (('l', 1), ('r', -1)):
        GEOMETRY.append(('scute%d_%s' % (i, side), 'body', 'scute', (-1.5, -3, -2.5, 3, 3, 5), (sx * 4, -7, SCUTE_Z[i] + (0 if side == 'l' else 3), 0, 0, 0),
                         side == 'r'))
GEOMETRY += [
    ('saddle', 'body', 'saddle', (-7, -2, -6, 14, 2, 12), (0, -7, -6, 0, 0, 0), False),
    ('pommel', 'saddle', 'pommel', (-2, -3, 0, 4, 3, 2), (0, -2, -6, 0, 0, 0), False),
    ('cantle', 'saddle', 'cantle', (-6, -3, 0, 12, 3, 2), (0, -2, 4, 0, 0, 0), False),
    ('strap', 'body', 'strap', (-11.5, -7.5, -1.5, 23, 15, 3), (0, 0, -6, 0, 0, 0), False),
    ('pad', 'body', 'pad', (-6, -2, -4, 12, 2, 8), (0, -7, 7, 0, 0, 0), False),
]
# long legs splayed out from the shoulder and hip, the shin back under them, broad clawed feet
SPLAY = 0.35
LEGS = [('left_front', 9.5, -13.0, True), ('right_front', -9.5, -13.0, True), ('left_hind', 9.5, 13.0, False), ('right_hind', -9.5, 13.0, False)]
for name, x, z, front in LEGS:
    m, out = x < 0, (1 if x > 0 else -1)
    thigh = (-3.5, -2, -3.5, 7, 15, 7) if front else (-4.5, -2, -5, 9, 15, 10)
    shin = (-2.5, 0, -2.5, 5, 16, 5) if front else (-3, 0, -3, 6, 16, 6)
    GEOMETRY.append((name + '_leg', 'body', 'front_upper' if front else 'back_upper', thigh, (x, 3, z, 0, 0, -out * SPLAY), m))
    GEOMETRY.append((name + '_shin', name + '_leg', 'front_lower' if front else 'back_lower', shin, (0, 12.7, 0, 0, 0, out * SPLAY), m))
    GEOMETRY.append((name + '_foot', name + '_shin', 'foot', (-4, 0, -7, 8, 3, 10), (0, 15, 0, 0, 0, 0), m))

PLAN = Plan(GEOMETRY)
PARTS, UV = PLAN.parts, PLAN.uv

# ---- the hides -----------------------------------------------------------------------------------------------------
# (main, dark, belly, scute)
HIDES = [
    (0x5C6B3A, 0x343E20, 0xC8C08A, 0x3E4628),  # swamp olive
    (0x7A6444, 0x46382A, 0xD2C29A, 0x4E3E2C),  # river brown
    (0x34402F, 0x182018, 0x8E9676, 0x232B22),  # mangrove black
    (0xB4A47C, 0x786848, 0xEDE3C6, 0x86765A),  # pale sand
]
HORN, HORN_TIP, CLAW = rgb(0x2A261E), rgb(0xC8BC98), rgb(0x1A1812)
EYE, PUPIL, MOUTH, TEETH = rgb(0xE8C838), rgb(0x100C04), rgb(0x8A3A30), rgb(0xF0E8D0)
LEATHER, LEATHER_DARK, BRASS, CLOTH = rgb(0x6B3F22), rgb(0x4A2A16), rgb(0xC89838), rgb(0x6A2A28)
MARSH, MARSH_HOT, MARSH_DIM = rgb(0xC8F060), rgb(0xF4FFB4), rgb(0x86B030)

BONY = {'scute', 'crest', 'crest_tip'}
HORNY = {'horn', 'horn_tip', 'horn_short', 'nose_horn'}
BELLIES = {'body', 'belly', 'neck0', 'neck1', 'jaw'} | {'tail%d' % i for i in range(9)}
LEGS_UV = {'front_upper', 'front_lower', 'back_upper', 'back_lower'}
TACK = {'saddle', 'pommel', 'cantle', 'strap', 'pad'}


def hide_texture(variant):
    main, dark, belly, scute = (rgb(c) for c in HIDES[variant])
    scute_c = scute
    rnd = random.Random(9100 + variant)
    field = noise_field(510 + variant)
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))

    def skin(name, face, x, y, fw, fh, base):
        u, v = UV[name]
        n = field(u + x * 1.1 + FACE_I.index(face) * 7, v + y * 1.1)
        c = base
        # square armour scales in a grid, the seams dark; the tops knobbled
        if x % 3 == 2 or y % 3 == 2:
            c = mix(c, dark, 0.3)
        elif face == 'top' and (x + y) % 2 == 0 and rnd.random() < 0.3:
            c = mix(c, scute, 0.5)
        if n > 0.6:
            c = mix(c, dark, min(1.0, (n - 0.6) * 2.0) * 0.55)
        elif n < 0.3:
            c = mix(c, belly, (0.3 - n) * 0.35)
        # mud dried on its legs and flanks
        if name in LEGS_UV and y > fh * 0.6 and rnd.random() < 0.4:
            c = mix(c, rgb(0x5A4A32), 0.45)
        return shade(jitter(c, 4, rnd), face, x, y, fw, fh)

    for name, w, h, d in PARTS:
        if name in BONY:
            def bone(face, x, y, fw, fh):
                c = mix(scute, dark, 0.3) if y == fh - 1 else scute
                if face == 'top' or y == 0:
                    c = mix(scute, belly, 0.3)
                if (x + y) % 3 == 0:
                    c = mix(c, dark, 0.2)
                return shade(jitter(c, 5, rnd), face, x, y, fw, fh)
            PLAN.paint(img, name, bone)
        elif name in HORNY:
            def horn(face, x, y, fw, fh, name=name):
                t = (x / max(1, fw - 1) if face in ('top', 'bottom') else 0.3) * 0.9
                if face == 'south' or name == 'nose_horn' and y == 0:
                    t = 1.0
                return shade(jitter(mix(HORN, HORN_TIP, min(1.0, t)), 5, rnd), face, x, y, fw, fh)
            PLAN.paint(img, name, horn)
        elif name in TACK:
            def tack(face, x, y, fw, fh, name=name):
                if name == 'strap':
                    c = LEATHER_DARK if y in (0, fh - 1) or x in (0, fw - 1) else LEATHER
                    if face in ('east', 'west') and fh // 2 - 1 <= y <= fh // 2 + 1 and fw // 2 - 1 <= x <= fw // 2:
                        c = BRASS
                    return shade(jitter(c, 4, rnd), face, x, y, fw, fh)
                c = LEATHER_DARK if x in (0, fw - 1) or y in (0, fh - 1) else LEATHER
                if name == 'pad' and face == 'top':
                    c = CLOTH if (x // 2 + y // 2) % 2 == 0 else mix(CLOTH, rgb(0x000000), 0.3)
                if face == 'top' and name == 'saddle' and (x, y) in ((1, 1), (fw - 2, 1), (1, fh - 2), (fw - 2, fh - 2)):
                    c = BRASS
                return shade(jitter(c, 5, rnd), face, x, y, fw, fh)
            PLAN.paint(img, name, tack)
        elif name == 'eye':
            def eye(face, x, y, fw, fh):
                if face in ('east', 'west'):
                    # a slit pupil
                    return PUPIL if x == 1 else jitter(EYE, 6, rnd)
                return shade(jitter(mix(main, scute, 0.4), 4, rnd), face, x, y, fw, fh)
            PLAN.paint(img, name, eye)
        else:
            def body(face, x, y, fw, fh, name=name):
                if name == 'foot':
                    if face == 'north':
                        return jitter(CLAW, 4, rnd) if x in (0, 2, 5, 7) and y >= 1 else shade(jitter(dark, 4, rnd), face, x, y, fw, fh)
                    if face == 'bottom':
                        return shade(jitter(dark, 4, rnd), face, x, y, fw, fh)
                if name in LEGS_UV and face == 'bottom':
                    return shade(jitter(dark, 4, rnd), face, x, y, fw, fh)
                if name == 'jaw' and face == 'top':
                    # the long mouth: red inside, a row of teeth round its rim
                    return TEETH if x in (0, fw - 1) and y % 2 == 0 else jitter(MOUTH, 6, rnd)
                if name == 'jaw' and face in ('east', 'west') and y == 0:
                    return TEETH if x % 3 == 0 else shade(jitter(mix(main, dark, 0.3), 4, rnd), face, x, y, fw, fh)
                if name == 'snout_tip' and face == 'top' and y in (1, 2) and x in (3, fw - 4):
                    return rgb(0x100E0A)
                if name == 'snout_tip' and face in ('east', 'west', 'north') and y == fh - 1:
                    return TEETH if x % 2 == 1 else jitter(MOUTH, 4, rnd)
                if name == 'brow':
                    return shade(jitter(mix(scute_c, dark, 0.2) if y == fh - 1 else scute_c, 5, rnd), face, x, y, fw, fh)
                if name == 'snout' and face in ('east', 'west') and y == fh - 1:
                    return TEETH if x % 3 == 1 else jitter(MOUTH, 4, rnd)
                if name == 'snout' and face == 'top' and y in (1, 2) and x in (2, fw - 3):
                    return rgb(0x100E0A)
                if face == 'bottom' and name in BELLIES:
                    return shade(jitter(mix(belly, dark, 0.2) if y % 3 == 2 or x % 4 == 3 else belly, 5, rnd), face, x, y, fw, fh)
                if name in BELLIES and face in ('east', 'west', 'north', 'south') and fh >= 6 and y >= fh - 2:
                    return shade(jitter(mix(belly, main, 0.45), 5, rnd), face, x, y, fw, fh)
                return skin(name, face, x, y, fw, fh, main)
            PLAN.paint(img, name, body)
    return img


MARKS = ['bands', 'spots', 'rims', 'jaw']


def marks_texture(pattern):
    rnd = random.Random(9600 + pattern)
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    kind = MARKS[pattern]

    def glow(c):
        return jitter(c, 10, rnd)
    for name, w, h, d in PARTS:
        if kind == 'bands' and name.startswith(('body', 'tail', 'neck')):
            def band(face, x, y, fw, fh):
                if face == 'top':
                    return glow(MARSH) if y % 6 in (2, 3) and 1 <= x < fw - 1 and rnd.random() < 0.85 else None
                if face in ('east', 'west'):
                    return glow(MARSH_DIM) if x % 6 in (2, 3) and y < fh // 3 and rnd.random() < 0.7 else None
                return None
            PLAN.paint(img, name, band)
        elif kind == 'spots' and name in ('body', 'neck0', 'skull', 'snout', 'snout_tip', 'tail0', 'tail1', 'tail2', 'tail3', 'tail4', 'tail5', 'front_upper', 'back_upper'):
            px = img.load()
            for face, (fx, fy, fw, fh) in PLAN.faces(name).items():
                if face in ('top', 'east', 'west') and fw > 4 and fh > 4:
                    for _ in range(max(2, fw * fh // 55)):
                        cx, cy, r = rnd.randint(1, fw - 2), rnd.randint(1, fh - 2), rnd.choice((0, 0, 1))
                        c = glow(rnd.choice((MARSH, MARSH_DIM, MARSH_HOT)))
                        for yy in range(-r, r + 1):
                            for xx in range(-r, r + 1):
                                if xx * xx + yy * yy <= r * r and 0 <= cx + xx < fw and 0 <= cy + yy < fh:
                                    px[fx + cx + xx, fy + cy + yy] = c
        elif kind == 'rims' and name in BONY:
            PLAN.paint(img, name, lambda face, x, y, fw, fh: glow(MARSH_HOT if face == 'top' else MARSH) if face == 'top' or y == 0 else None)
        elif kind == 'jaw' and name in ('jaw', 'snout', 'snout_tip', 'skull'):
            def jl(face, x, y, fw, fh, name=name):
                if face in ('east', 'west'):
                    if name == 'jaw' and y == 1 or name in ('snout', 'snout_tip') and y == fh - 2 or name == 'skull' and y == fh - 2 and x % 2 == 0:
                        return glow(MARSH)
                return None
            PLAN.paint(img, name, jl)
        if name == 'eye':
            PLAN.paint(img, name, lambda face, x, y, fw, fh: (None if x == 1 else glow(MARSH_HOT)) if face in ('east', 'west') else None)
    return img


def preview(path, hides, marks):
    W, Hh = 1400, 760
    img = Image.new('RGB', (W, Hh), (34, 38, 44))
    dr = ImageDraw.Draw(img)
    for i, hide in enumerate(hides):
        col, row = i % 2, i // 2
        ox, oy = 360 + col * 680, 300 + row * 370
        saddled = i == 0
        hidden = set() if saddled else set(TACK)
        if saddled:
            hidden |= {'scute%d_%s' % (k, s) for k in (1, 2, 3) for s in 'lr'}
        hidden |= {'short_horn_l', 'short_horn_r'} if i != 2 else {'horn_l', 'horn_r', 'horn_tip_l', 'horn_tip_r'}
        if i != 3:
            hidden.add('nose_horn')
        PLAN.render(hide, img, ox, oy, 2.4 if col == 0 else 1.5708, 2.3, hidden)
        dr.text((ox - 320, oy - 260), ['swamp olive, saddled', 'river brown', 'mangrove black, short horns', 'pale sand, nose horn'][i],
                fill=(220, 220, 220))
    img.save(path)


if __name__ == '__main__':
    for d in (ITEM, ASSETS + 'items/', ASSETS + 'models/item/', DATA + 'jaymoskingdoms/loot_table/entities/'):
        os.makedirs(d, exist_ok=True)
    folder = ASSETS + 'textures/entity/dragon/varasuchus/'
    os.makedirs(folder, exist_ok=True)
    hides = [hide_texture(i) for i in range(len(HIDES))]
    marks = [marks_texture(i) for i in range(len(MARKS))]
    for i, h in enumerate(hides):
        h.save(folder + 'hide_%d.png' % i)
    for i, m in enumerate(marks):
        m.save(folder + 'marks_%d.png' % i)
    egg('varasuchus_dragon_spawn_egg', rgb(0x5C6B3A), rgb(0xC8F060), 115)
    write(ASSETS + 'items/varasuchus_dragon_spawn_egg.json', {'model': {'type': 'minecraft:model', 'model': 'jaymoskingdoms:item/varasuchus_dragon_spawn_egg'}})
    write(ASSETS + 'models/item/varasuchus_dragon_spawn_egg.json',
          {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jaymoskingdoms:item/varasuchus_dragon_spawn_egg'}})
    write(DATA + 'jaymoskingdoms/loot_table/entities/varasuchus_dragon.json',
          {'type': 'minecraft:entity', 'pools': [{'rolls': 1, 'entries': [e]} for e in (
              entry('minecraft:leather', 4, 8), entry('minecraft:bone', 3, 6), entry('minecraft:salmon', 2, 5),
              entry('minecraft:turtle_scute', 0, 2), entry('minecraft:armadillo_scute', 1, 3), entry('minecraft:mud', 1, 3))],
           'random_sequence': 'jaymoskingdoms:entities/varasuchus_dragon'})
    out = sys.argv[1] if len(sys.argv) > 1 else 'tools/out/varasuchus-preview.png'
    os.makedirs(os.path.dirname(out) or '.', exist_ok=True)
    preview(out, hides, marks)
    sys.stderr.write('top of the model: %.1f px over the ground\n' % (24 - PLAN.top_y()))
    print(PLAN.java())
