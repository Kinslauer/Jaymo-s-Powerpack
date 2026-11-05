"""Atroxiia's art and data (kingdoms/src/main/java/com/jaymoskingdoms/dragons/AtroxiiaDragon), all our own design.
Its moves are ported from Saint's Dragons by Leon Saint (MIT code); none of that mod's art, models or sounds are used
(they are All Rights Reserved), see docs/SaintsDragons-Plan.md.
A tall, gaunt frost wyrm: high shoulders on long thin forelegs, digitigrade hind legs, a long serpent's neck in six
segments, a narrow head under a crown of swept-back horns, a ridge of ice crystals down its back, and a long tail that
arches up over its hips and ends in a spear of ice (its Gungnir stab).
- the body plan (GEOMETRY), printed as the Java for AtroxiiaDragonModel's createBodyLayer;
- four hides (glacier, deep ice, rime grey, midnight frost) and four glowing frost patterns (veins, rims, stars, spine);
- the spawn egg's icon and item model, and the loot table;
- a preview render of all four hides.
Run from kingdoms/: python3 -I tools/gen_atroxiia.py [preview.png]"""
import os, random, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image, ImageDraw
from gen_dragons import rgb, jitter, mix, shade, write, entry, egg, ASSETS, DATA, ITEM, SIZE
from lesser_dragon_art import Plan, chain, noise_field, FACE_I

# ---- the body plan -------------------------------------------------------------------------------------------------
# (0.7.522, Andy: "atroxiia does NOT look right") a serpent with two great arms and no hind legs: its chest held high on
# the arms, the serpent body running back down to the ground and along it as a long tail (its third support), rising at
# the end into a spear of gold-bound ice; a short raised neck and a head under a pharaoh's headdress of hide (two lappets
# hanging behind it, a band across the brow). Our own design throughout.
NECK = [('neck0', (11, 11, 8), -0.75), ('neck1', (10, 10, 7), -0.25), ('neck2', (9, 9, 6), 0.15)]
HEAD_TILT = 0.75
TAIL = [('tail0', (16, 14, 12), -0.5), ('tail1', (15, 13, 12), -0.55), ('tail2', (14, 12, 12), -0.45), ('tail3', (13, 11, 12), 0.9),
        ('tail4', (12, 10, 12), 0.5), ('tail5', (11, 9, 12), 0.15), ('tail6', (10, 8, 11), 0.0), ('tail7', (8, 7, 11), 0.0),
        ('tail8', (7, 6, 10), 0.22), ('tail9', (6, 5, 10), 0.3), ('tail10', (4, 4, 9), 0.35)]
SPINE_ON = ['tail0', 'tail1', 'tail2', 'tail3', 'tail4']
SPINE_H = [10, 12, 11, 9, 7]

GEOMETRY = [
    ('body', None, 'body', (-9, -8, -10, 18, 16, 20), (0, -14, -6, 0, 0, 0), False),
    ('chest', 'body', 'chest', (-8, 0, -8, 16, 3, 14), (0, 8, -1, 0, 0, 0), False),
    ('collar', 'body', 'collar', (-9.5, -2, -1.5, 19, 4, 3), (0, -5, -9, 0, 0, 0), False),
]
chain(GEOMETRY, 'body', (0, -4, -9), NECK, -1)
GEOMETRY += [
    ('head', 'neck2', 'skull', (-6, -5, -11, 12, 9, 11), (0, 0, -5, HEAD_TILT, 0, 0), False),
    ('snout', 'head', 'snout', (-4.5, -3, -9, 9, 6, 9), (0, 0, -11, 0, 0, 0), False),
    ('jaw', 'head', 'jaw', (-4.5, 0, -19, 9, 3, 19), (0, 3.5, 0, 0, 0, 0), False),
    ('eye_l', 'head', 'eye', (-0.5, -1, -1.5, 1, 2, 3), (6, -2, -8, 0, 0, 0), False),
    ('eye_r', 'head', 'eye', (-0.5, -1, -1.5, 1, 2, 3), (-6, -2, -8, 0, 0, 0), False),
    # the headdress: a band over the brow, two long lappets hanging behind (short ones on some), a crystal at the brow on others
    ('cap', 'head', 'cap', (-6.5, -1, -5, 13, 2, 10), (0, -5, -5.5, 0, 0, 0), False),
    ('band', 'head', 'band', (-6.5, -1, -1.5, 13, 2, 3), (0, -5, -10, 0, 0, 0), False),
    ('nemes_l', 'head', 'nemes', (0, 0, 0, 1, 14, 11), (6, -4, -6, 0.32, 0.22, -0.18), False),
    ('nemes_r', 'head', 'nemes', (-1, 0, 0, 1, 14, 11), (-6, -4, -6, 0.32, -0.22, 0.18), True),
    ('short_nemes_l', 'head', 'nemes_short', (0, 0, 0, 1, 8, 9), (6, -4, -6, 0.32, 0.22, -0.18), False),
    ('short_nemes_r', 'head', 'nemes_short', (-1, 0, 0, 1, 8, 9), (-6, -4, -6, 0.32, -0.22, 0.18), True),
    ('braid', 'head', 'braid', (-2, 0, 0, 4, 9, 2), (0, -3, 0, 0.35, 0, 0), False),
    ('uraeus', 'head', 'uraeus', (-1, -6, -1, 2, 6, 2), (0, -5.5, -9, -0.35, 0, 0), False),
]
chain(GEOMETRY, 'body', (0, -2, 9), TAIL, 1)
GEOMETRY += [
    # the spear at its tail's end: a gold binding, a long blade of ice
    ('spear', 'tail10', 'spear', (-1.5, -1.5, 0, 3, 3, 4), (0, 0, 8, 0.1, 0, 0), False),
    ('blade', 'spear', 'blade', (-1, -3.5, 0, 2, 7, 16), (0, 0, 3.5, 0, 0, 0), False),
    ('guard', 'spear', 'guard', (-5, -1, 0, 10, 2, 2), (0, 0, 2, 0, 0, 0), False),
    ('saddle', 'body', 'saddle', (-7, -2, -6, 14, 2, 12), (0, -8, 1, 0, 0, 0), False),
    ('pommel', 'saddle', 'pommel', (-2, -3, 0, 4, 3, 2), (0, -2, -6, 0, 0, 0), False),
    ('cantle', 'saddle', 'cantle', (-5, -3, 0, 10, 3, 2), (0, -2, 4, 0, 0, 0), False),
    ('strap', 'body', 'strap', (-9.5, -8.5, -1.5, 19, 17, 3), (0, 0, 1, 0, 0, 0), False),
    ('pad', 'tail0', 'pad', (-6, -2, -4, 12, 2, 8), (0, -7, 6, 0, 0, 0), False),
]
for i, seg in enumerate(SPINE_ON):
    h = SPINE_H[i]
    w = [t[1] for t in TAIL if t[0] == seg][0]
    GEOMETRY.append(('spine%d' % i, seg, 'spine%d' % i, (-1, -h, -1.5, 2, h, 3), (0, -w[1] / 2 + 1, 6, -0.4, 0, 0), False))
# the arms: a broad shoulder, a long upper arm bowed out, a long forearm straight down, a gold cuff, a hand of three
# clawed fingers and a thumb
ARM_OUT = 0.45
for side, sx in (('left', 1), ('right', -1)):
    m = sx < 0
    GEOMETRY.append((side + '_arm', 'body', 'upper_arm', (-5, -3, -5, 10, 21, 10), (sx * 10, -3, -5, -0.1, 0, -sx * ARM_OUT), m))
    GEOMETRY.append((side + '_shoulder', side + '_arm', 'shoulder', (-6.5, -5, -6.5, 13, 10, 13), (0, 0, 0, 0, 0, 0), m))
    GEOMETRY.append((side + '_forearm', side + '_arm', 'forearm', (-4.5, 0, -4.5, 9, 22, 9), (0, 17.5, 0, 0.1, 0, sx * ARM_OUT), m))
    GEOMETRY.append((side + '_cuff', side + '_forearm', 'cuff', (-5, 0, -5, 10, 3, 10), (0, 3, 0, 0, 0, 0), m))
    GEOMETRY.append((side + '_hand', side + '_forearm', 'hand', (-5.5, 0, -7, 11, 3, 10), (0, 22.3, 0, 0, 0, 0), m))
    for f, fx in enumerate((-3, 0, 3)):
        GEOMETRY.append(('%s_finger%d' % (side, f), side + '_hand', 'finger', (-1, 0, -5, 2, 2, 5), (fx * 1.2, 1, -7, 0, (fx / 3.0) * -0.15, 0), m))
    GEOMETRY.append((side + '_thumb', side + '_hand', 'finger', (-1, 0, -5, 2, 2, 5), (-sx * 5.5, 1, -3, 0, sx * 0.7, 0), m))

PLAN = Plan(GEOMETRY)
PARTS, UV = PLAN.parts, PLAN.uv

# ---- the hides -----------------------------------------------------------------------------------------------------
# (main, dark, belly, crystal)
HIDES = [
    (0xB4C8D6, 0x6A8496, 0xE6EEF2, 0xA6E4FF),  # glacier
    (0x5A80A6, 0x32506C, 0xC0D6E6, 0x84DAFF),  # deep ice
    (0x9A9EA8, 0x5A5E68, 0xD8DADE, 0xCCF2FF),  # rime grey
    (0x2E3648, 0x181C2A, 0x8C98AC, 0x6CCAF2),  # midnight frost
]
HORN, HORN_TIP, CLAW = rgb(0x3A4050), rgb(0xD8E8F0), rgb(0x20242E)
EYE, PUPIL, MOUTH, TEETH = rgb(0x9AE8FF), rgb(0x0A1420), rgb(0x3A2A3A), rgb(0xEEF4F8)
LEATHER, LEATHER_DARK, SILVER, CLOTH = rgb(0x5A3A2A), rgb(0x3A2418), rgb(0xD0D8E0), rgb(0x2A3E6A)
FROST, FROST_HOT, FROST_DIM = rgb(0x8FE8FF), rgb(0xE4FCFF), rgb(0x4AA8D0)

ICE = {'spine%d' % i for i in range(5)} | {'blade', 'uraeus'}
GOLD = {'cuff', 'collar', 'spear', 'guard', 'band'}
GOLD_C, GOLD_DARK, LAPIS = rgb(0xE8C048), rgb(0xA07820), rgb(0x2E4A8A)
BELLIES = {'body', 'chest', 'neck0', 'neck1', 'neck2', 'jaw'} | {'tail%d' % i for i in range(11)}
LEGS_UV = {'upper_arm', 'forearm', 'shoulder'}
TACK = {'saddle', 'pommel', 'cantle', 'strap', 'pad'}


def hide_texture(variant):
    main, dark, belly, crystal = (rgb(c) for c in HIDES[variant])
    rnd = random.Random(8100 + variant)
    field = noise_field(410 + variant)
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))

    def skin(name, face, x, y, fw, fh, base):
        u, v = UV[name]
        n = field(u + x * 1.3 + FACE_I.index(face) * 7, v + y * 1.3)
        c = base
        # small tight scales in diagonal rows, rimed pale at their tips
        if (x + y) % 4 == 0:
            c = mix(c, dark, 0.2)
        elif (x - y) % 5 == 0 and y % 2 == 0:
            c = mix(c, rgb(0xFFFFFF), 0.12)
        if n > 0.64:
            c = mix(c, dark, min(1.0, (n - 0.64) * 2.0) * 0.55)
        elif n < 0.3:
            c = mix(c, rgb(0xFFFFFF), (0.3 - n) * 0.45)
        # frost settled on its back
        if face == 'top' and rnd.random() < 0.18:
            c = mix(c, rgb(0xF4FAFF), 0.5)
        return shade(jitter(c, 4, rnd), face, x, y, fw, fh)

    for name, w, h, d in PARTS:
        if name in ICE:
            def ice(face, x, y, fw, fh, name=name):
                # clear ice: pale at the edges, deeper inside, a glint here and there
                edge = x in (0, fw - 1) or y in (0, fh - 1)
                c = mix(crystal, rgb(0xFFFFFF), 0.45) if edge else mix(crystal, dark, 0.15 + 0.2 * (y / max(1, fh - 1)))
                if rnd.random() < 0.08:
                    c = rgb(0xFFFFFF)
                return shade(jitter(c, 6, rnd), face, x, y, fw, fh)
            PLAN.paint(img, name, ice)
        elif name in GOLD:
            def gold(face, x, y, fw, fh, name=name):
                # beaten gold: a darker line round the rim, chased with a band of frost-blue inlay on the bigger pieces
                c = GOLD_DARK if y in (0, fh - 1) and fh > 2 else GOLD_C
                if name in ('collar', 'band') and face in ('north', 'east', 'west', 'south') and y == fh // 2 and x % 3 == 1:
                    c = crystal
                if rnd.random() < 0.06:
                    c = rgb(0xFFF0B0)
                return shade(jitter(c, 6, rnd), face, x, y, fw, fh)
            PLAN.paint(img, name, gold)
        elif name in ('nemes', 'nemes_short', 'braid', 'cap'):
            def nemes(face, x, y, fw, fh, name=name):
                # the headdress of hide: stripes of gold and the hide's own shade running down it, a gold hem
                stripe = ((x if name == 'cap' and face == 'top' else y) // 2) % 2 == 0
                c = GOLD_C if stripe else mix(main, LAPIS, 0.35)
                if y == fh - 1:
                    c = GOLD_DARK
                if name == 'braid':
                    c = GOLD_C if y % 3 == 0 else mix(main, dark, 0.2)
                return shade(jitter(c, 5, rnd), face, x, y, fw, fh)
            PLAN.paint(img, name, nemes)
        elif name in TACK:
            def tack(face, x, y, fw, fh, name=name):
                if name == 'strap':
                    c = LEATHER_DARK if y in (0, fh - 1) or x in (0, fw - 1) else LEATHER
                    if face in ('east', 'west') and fh // 2 - 1 <= y <= fh // 2 + 1 and fw // 2 - 1 <= x <= fw // 2:
                        c = SILVER
                    return shade(jitter(c, 4, rnd), face, x, y, fw, fh)
                c = LEATHER_DARK if x in (0, fw - 1) or y in (0, fh - 1) else LEATHER
                if name == 'pad' and face == 'top':
                    c = CLOTH if (x // 2 + y // 2) % 2 == 0 else mix(CLOTH, rgb(0xFFFFFF), 0.25)
                if face == 'top' and name == 'saddle' and (x, y) in ((1, 1), (fw - 2, 1), (1, fh - 2), (fw - 2, fh - 2)):
                    c = SILVER
                return shade(jitter(c, 5, rnd), face, x, y, fw, fh)
            PLAN.paint(img, name, tack)
        elif name == 'eye':
            def eye(face, x, y, fw, fh):
                if face in ('east', 'west'):
                    return PUPIL if x == 1 and y == 1 else jitter(EYE, 6, rnd)
                return shade(dark, face, x, y, fw, fh)
            PLAN.paint(img, name, eye)
        elif name in ('hand', 'finger'):
            def claw(face, x, y, fw, fh, name=name):
                # broad hands, the fingers ending in long dark claws
                if name == 'finger' and (face == 'north' or face in ('top', 'bottom', 'east', 'west') and (y < 2 if face in ('top', 'bottom') else x < 2)):
                    return shade(jitter(CLAW if face != 'top' else HORN_TIP, 4, rnd), face, x, y, fw, fh)
                return shade(jitter(mix(main, dark, 0.15) if face != 'bottom' else dark, 4, rnd), face, x, y, fw, fh)
            PLAN.paint(img, name, claw)
        else:
            def body(face, x, y, fw, fh, name=name):
                if name == 'skull' and face in ('east', 'west') and y in (3, 4) and 1 <= x <= 5:
                    # dark lines drawn round the eyes, sweeping back
                    return rgb(0x161A26)
                if name in LEGS_UV and face == 'bottom':
                    return shade(jitter(dark, 4, rnd), face, x, y, fw, fh)
                if name == 'jaw' and face == 'top':
                    # a long narrow mouth lined with needle teeth
                    return TEETH if x in (0, fw - 1) and y % 2 == 0 else jitter(MOUTH, 6, rnd)
                if name == 'jaw' and face in ('east', 'west') and y == 0:
                    return TEETH if x % 2 == 0 else jitter(MOUTH, 4, rnd)
                if name == 'snout' and face in ('east', 'west') and y == fh - 1:
                    return TEETH if x % 2 == 1 else jitter(MOUTH, 4, rnd)
                if name == 'snout' and face == 'north' and y == 1 and x in (1, fw - 2):
                    return rgb(0x10141C)
                if face == 'bottom' and name in BELLIES:
                    return shade(jitter(mix(belly, main, 0.25) if y % 3 == 2 else belly, 5, rnd), face, x, y, fw, fh)
                if name in BELLIES and face in ('east', 'west', 'north', 'south') and fh >= 6 and y >= fh - 2:
                    return shade(jitter(mix(belly, main, 0.45), 5, rnd), face, x, y, fw, fh)
                base = main
                if face == 'top' and abs(x - fw // 2) <= 0 and name.startswith(('body', 'neck', 'tail')):
                    base = mix(main, crystal, 0.4)
                return skin(name, face, x, y, fw, fh, base)
            PLAN.paint(img, name, body)
    return img


MARKS = ['veins', 'rims', 'stars', 'spine']


def marks_texture(pattern):
    rnd = random.Random(8600 + pattern)
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    kind = MARKS[pattern]

    def glow(c):
        return jitter(c, 10, rnd)
    for name, w, h, d in PARTS:
        if name in ICE and kind in ('rims', 'spine'):
            def rim(face, x, y, fw, fh, name=name):
                if kind == 'spine' and not name.startswith(('spine', 'blade')):
                    return None
                edge = x in (0, fw - 1) or y == 0
                return glow(FROST_HOT if face == 'top' else FROST) if edge else None
            PLAN.paint(img, name, rim)
        elif kind == 'veins' and name in {'body', 'neck0', 'neck1', 'neck2', 'skull', 'tail0', 'tail1', 'tail2', 'upper_arm', 'forearm'}:
            px = img.load()
            for face, (fx, fy, fw, fh) in PLAN.faces(name).items():
                if face == 'bottom' or fw < 4 or fh < 4:
                    continue
                for _ in range(max(1, fw * fh // 80)):
                    x, y = rnd.randrange(fw), rnd.randrange(fh)
                    for _ in range(rnd.randint(4, 10)):
                        if 0 <= x < fw and 0 <= y < fh:
                            px[fx + x, fy + y] = glow(rnd.choice((FROST, FROST_DIM)))
                        x += rnd.choice((-1, 1, 1))
                        y += rnd.choice((-1, 0, 1))
        elif kind == 'stars' and name in ('body', 'neck0', 'neck1', 'skull', 'tail0', 'tail1', 'upper_arm', 'forearm'):
            px = img.load()
            for face, (fx, fy, fw, fh) in PLAN.faces(name).items():
                if face in ('top', 'east', 'west') and fw > 4 and fh > 4:
                    for _ in range(max(2, fw * fh // 70)):
                        cx, cy = rnd.randint(1, fw - 2), rnd.randint(1, fh - 2)
                        for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
                            px[fx + cx + dx, fy + cy + dy] = glow(FROST_HOT if (dx, dy) == (0, 0) else FROST_DIM)
        elif kind == 'spine' and name.startswith(('body', 'neck', 'skull', 'snout', 'tail')):
            def sp(face, x, y, fw, fh):
                return glow(FROST) if face == 'top' and x == fw // 2 and y % 2 == 0 else None
            PLAN.paint(img, name, sp)
        if name == 'eye':
            PLAN.paint(img, name, lambda face, x, y, fw, fh: (None if x == 1 and y == 1 else glow(FROST_HOT)) if face in ('east', 'west') else None)
    return img


def preview(path, hides, marks):
    W, Hh = 1400, 820
    img = Image.new('RGB', (W, Hh), (34, 38, 44))
    dr = ImageDraw.Draw(img)
    for i, hide in enumerate(hides):
        col, row = i % 2, i // 2
        ox, oy = 360 + col * 640, 330 + row * 400
        saddled = i == 0
        hidden = set() if saddled else set(TACK)
        if saddled:
            hidden |= {'spine1', 'spine2', 'spine3'}
        hidden |= {'short_nemes_l', 'short_nemes_r'} if i != 2 else {'nemes_l', 'nemes_r'}
        if i != 3:
            hidden.add('uraeus')
        PLAN.render(hide, img, ox, oy, 2.4 if col == 0 else 1.5708, 2.5, hidden)
        dr.text((ox - 300, oy - 330), ['glacier, saddled', 'deep ice', 'rime grey, short headdress', 'midnight frost, brow crystal'][i],
                fill=(220, 220, 220))
    img.save(path)


if __name__ == '__main__':
    for d in (ITEM, ASSETS + 'items/', ASSETS + 'models/item/', DATA + 'jaymoskingdoms/loot_table/entities/'):
        os.makedirs(d, exist_ok=True)
    folder = ASSETS + 'textures/entity/dragon/atroxiia/'
    os.makedirs(folder, exist_ok=True)
    hides = [hide_texture(i) for i in range(len(HIDES))]
    marks = [marks_texture(i) for i in range(len(MARKS))]
    for i, h in enumerate(hides):
        h.save(folder + 'hide_%d.png' % i)
    for i, m in enumerate(marks):
        m.save(folder + 'marks_%d.png' % i)
    egg('atroxiia_dragon_spawn_egg', rgb(0xB4C8D6), rgb(0x5AA8E0), 114)
    write(ASSETS + 'items/atroxiia_dragon_spawn_egg.json', {'model': {'type': 'minecraft:model', 'model': 'jaymoskingdoms:item/atroxiia_dragon_spawn_egg'}})
    write(ASSETS + 'models/item/atroxiia_dragon_spawn_egg.json',
          {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jaymoskingdoms:item/atroxiia_dragon_spawn_egg'}})
    write(DATA + 'jaymoskingdoms/loot_table/entities/atroxiia_dragon.json',
          {'type': 'minecraft:entity', 'pools': [{'rolls': 1, 'entries': [e]} for e in (
              entry('minecraft:leather', 2, 5), entry('minecraft:bone', 3, 6), entry('minecraft:cod', 2, 5),
              entry('minecraft:packed_ice', 2, 5), entry('minecraft:blue_ice', 0, 2), entry('minecraft:prismarine_shard', 1, 3))],
           'random_sequence': 'jaymoskingdoms:entities/atroxiia_dragon'})
    out = sys.argv[1] if len(sys.argv) > 1 else 'tools/out/atroxiia-preview.png'
    os.makedirs(os.path.dirname(out) or '.', exist_ok=True)
    preview(out, hides, marks)
    sys.stderr.write('top of the model: %.1f px over the ground\n' % (24 - PLAN.top_y()))
    print(PLAN.java())
