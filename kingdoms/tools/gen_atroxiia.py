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
# (Andy: "longer limbs and necks, and be taller, to be imposing") its back 40 pixels up, its head higher still
NECK = [('neck0', (11, 10, 8), -0.62), ('neck1', (10, 9, 7), -0.22), ('neck2', (9, 8, 7), -0.12), ('neck3', (8, 8, 7), 0.0),
        ('neck4', (8, 7, 6), 0.12), ('neck5', (7, 7, 6), 0.2)]
HEAD_TILT = 0.55
TAIL = [('tail0', (9, 9, 9), 0.22), ('tail1', (8, 8, 9), 0.26), ('tail2', (7, 7, 9), 0.3), ('tail3', (6, 6, 8), 0.34),
        ('tail4', (5, 5, 8), 0.36), ('tail5', (4, 4, 7), 0.34), ('tail6', (3, 3, 7), 0.3)]
SPINE_Z = [-11.0, -5.5, 0.0, 5.5, 11.0]
SPINE_H = [9, 12, 14, 12, 9]

GEOMETRY = [
    ('body', None, 'body', (-8, -8, -15, 16, 16, 30), (0, -8, 0, 0, 0, 0), False),
    ('chest', 'body', 'chest', (-7, 0, -7, 14, 4, 14), (0, 8, -7, 0, 0, 0), False),
    ('belly', 'body', 'belly', (-6, 0, -6, 12, 2, 12), (0, 8, 7, 0, 0, 0), False),
]
chain(GEOMETRY, 'body', (0, -3, -14), NECK, -1)
GEOMETRY += [
    ('head', 'neck5', 'skull', (-4.5, -4, -11, 9, 7, 11), (0, 0, -5, HEAD_TILT, 0, 0), False),
    ('snout', 'head', 'snout', (-3.5, -2.5, -8, 7, 5, 8), (0, -0.5, -11, 0, 0, 0), False),
    ('jaw', 'head', 'jaw', (-3.5, 0, -18, 7, 2, 18), (0, 3, 0, 0, 0, 0), False),
    ('brow', 'head', 'brow', (-5, -1, -2, 10, 2, 4), (0, -4, -8, 0, 0, 0), False),
    ('eye_l', 'head', 'eye', (-0.5, -1, -1, 1, 2, 2), (4.5, -2, -8, 0, 0, 0), False),
    ('eye_r', 'head', 'eye', (-0.5, -1, -1, 1, 2, 2), (-4.5, -2, -8, 0, 0, 0), False),
    ('horn_l', 'head', 'horn', (-1, -1, 0, 2, 2, 13), (3.5, -3.5, -3, 0.42, 0.28, 0), False),
    ('horn_r', 'head', 'horn', (-1, -1, 0, 2, 2, 13), (-3.5, -3.5, -3, 0.42, -0.28, 0), False),
    ('short_horn_l', 'head', 'horn_short', (-1, -1, 0, 2, 2, 6), (3.5, -3.5, -3, 0.5, 0.28, 0), False),
    ('short_horn_r', 'head', 'horn_short', (-1, -1, 0, 2, 2, 6), (-3.5, -3.5, -3, 0.5, -0.28, 0), False),
    ('crown_l', 'head', 'crown', (-0.5, -0.5, 0, 1, 1, 8), (4, -1.5, -5, 0.15, 0.62, 0), False),
    ('crown_r', 'head', 'crown', (-0.5, -0.5, 0, 1, 1, 8), (-4, -1.5, -5, 0.15, -0.62, 0), False),
    ('frill_l', 'head', 'frill', (0, -2.5, 0, 1, 5, 6), (4.5, 1, -4, 0, 0.35, 0), False),
    ('frill_r', 'head', 'frill', (-1, -2.5, 0, 1, 5, 6), (-4.5, 1, -4, 0, -0.35, 0), True),
]
chain(GEOMETRY, 'body', (0, -3, 14), TAIL, 1, 0.3)
GEOMETRY += [
    # the spear of ice at its tail's end: a shaft, a long blade and a crossguard of shards
    ('spear', 'tail6', 'spear', (-1, -1, 0, 2, 2, 6), (0, 0, 6, 0.1, 0, 0), False),
    ('blade', 'spear', 'blade', (-0.5, -2, 0, 1, 4, 10), (0, 0, 5, 0, 0, 0), False),
    ('guard', 'spear', 'guard', (-3.5, -0.5, 0, 7, 1, 2), (0, 0, 4, 0, 0, 0), False),
    ('tail_shard0', 'tail1', 'tail_shard', (-0.5, -4, -1.5, 1, 4, 3), (0, -3.5, 4, -0.3, 0, 0), False),
    ('tail_shard1', 'tail3', 'tail_shard', (-0.5, -4, -1.5, 1, 4, 3), (0, -2.5, 4, -0.3, 0, 0), False),
    ('saddle', 'body', 'saddle', (-6, -2, -6, 12, 2, 12), (0, -8, -4, 0, 0, 0), False),
    ('pommel', 'saddle', 'pommel', (-2, -3, 0, 4, 3, 2), (0, -2, -6, 0, 0, 0), False),
    ('cantle', 'saddle', 'cantle', (-5, -3, 0, 10, 3, 2), (0, -2, 4, 0, 0, 0), False),
    ('strap', 'body', 'strap', (-8.5, -8.5, -1.5, 17, 17, 3), (0, 0, -4, 0, 0, 0), False),
    ('pad', 'body', 'pad', (-5, -2, -4, 10, 2, 8), (0, -8, 7, 0, 0, 0), False),
]
for i in range(5):
    h = SPINE_H[i]
    GEOMETRY.append(('spine%d' % i, 'body', 'spine%d' % i, (-1, -h, -1.5, 2, h, 3), (0, -7, SPINE_Z[i], -0.32, 0, 0), False))
# shards on the shoulders
GEOMETRY.append(('shard_l', 'body', 'shard', (-0.5, -6, -1, 1, 6, 2), (6, -6, -10, -0.2, 0, 0.5), False))
GEOMETRY.append(('shard_r', 'body', 'shard', (-0.5, -6, -1, 1, 6, 2), (-6, -6, -10, -0.2, 0, -0.5), True))
# long legs: forelegs straight pillars, hind legs digitigrade (thigh forward, shin back, the foot flat)
LEGS = [('left_front', 7.0, -9.0, True), ('right_front', -7.0, -9.0, True), ('left_hind', 7.0, 10.0, False), ('right_hind', -7.0, 10.0, False)]
HIND_THIGH, HIND_SHIN = -0.3, 0.6
for name, x, z, front in LEGS:
    m = x < 0
    if front:
        GEOMETRY.append((name + '_leg', 'body', 'front_upper', (-3, -3, -3, 6, 15, 6), (x, 4, z, 0, 0, 0), m))
        GEOMETRY.append((name + '_shin', name + '_leg', 'front_lower', (-2.5, 0, -2.5, 5, 14, 5), (0, 12, 0, 0, 0, 0), m))
        GEOMETRY.append((name + '_foot', name + '_shin', 'foot', (-3, 0, -5, 6, 2, 8), (0, 14, 0, 0, 0, 0), m))
    else:
        GEOMETRY.append((name + '_leg', 'body', 'back_upper', (-3.5, -3, -4, 7, 15, 8), (x, 4, z, HIND_THIGH, 0, 0), m))
        GEOMETRY.append((name + '_shin', name + '_leg', 'back_lower', (-2.5, 0, -2.5, 5, 15, 5), (0, 12, 0, HIND_SHIN, 0, 0), m))
        GEOMETRY.append((name + '_foot', name + '_shin', 'foot', (-3, 0, -5, 6, 2, 8), (0, 15, 0, -(HIND_THIGH + HIND_SHIN), 0, 0), m))

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

ICE = {'spine%d' % i for i in range(5)} | {'shard', 'tail_shard', 'blade', 'guard', 'crown', 'spear'}
HORNY = {'horn', 'horn_short'}
BELLIES = {'body', 'chest', 'belly', 'neck0', 'neck1', 'neck2', 'jaw', 'tail0', 'tail1', 'tail2'}
LEGS_UV = {'front_upper', 'front_lower', 'back_upper', 'back_lower'}
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
        elif name in HORNY:
            def horn(face, x, y, fw, fh):
                t = (x / max(1, fw - 1) if face in ('top', 'bottom') else 0.35) * 0.9
                if face == 'south':
                    t = 1.0
                return shade(jitter(mix(HORN, HORN_TIP, min(1.0, t)), 5, rnd), face, x, y, fw, fh)
            PLAN.paint(img, name, horn)
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
        elif name == 'frill':
            def frill(face, x, y, fw, fh):
                # a thin fan of skin: ribbed, pale toward the edge
                c = mix(main, crystal, 0.3 + 0.4 * (x / max(1, fw - 1))) if x % 2 else mix(main, dark, 0.3)
                return shade(jitter(c, 5, rnd), face, x, y, fw, fh)
            PLAN.paint(img, name, frill)
        else:
            def body(face, x, y, fw, fh, name=name):
                if name == 'foot':
                    if face == 'north':
                        return jitter(CLAW, 4, rnd) if x in (0, 2, 3, 5) else shade(jitter(dark, 4, rnd), face, x, y, fw, fh)
                    if face == 'bottom':
                        return shade(jitter(dark, 4, rnd), face, x, y, fw, fh)
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
                if name == 'brow':
                    return shade(jitter(mix(main, crystal, 0.35) if y == 0 else mix(main, dark, 0.35), 4, rnd), face, x, y, fw, fh)
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
        elif kind == 'veins' and name in {'body', 'neck0', 'neck1', 'neck2', 'skull', 'tail0', 'tail1', 'tail2', 'front_upper', 'back_upper'}:
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
        elif kind == 'stars' and name in ('body', 'neck0', 'neck1', 'skull', 'tail0', 'tail1', 'front_upper', 'back_upper'):
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
        ox, oy = 340 + col * 700, 360 + row * 400
        saddled = i == 0
        hidden = set() if saddled else set(TACK)
        if saddled:
            hidden |= {'spine1', 'spine2', 'spine3'}
        hidden |= {'short_horn_l', 'short_horn_r'} if i != 2 else {'horn_l', 'horn_r'}
        if i != 3:
            hidden |= {'crown_l', 'crown_r'}
        PLAN.render(hide, img, ox, oy, 2.4 if col == 0 else 1.5708, 3.6, hidden)
        dr.text((ox - 300, oy - 330), ['glacier, saddled', 'deep ice', 'rime grey, short horns', 'midnight frost, crowned'][i], fill=(220, 220, 220))
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
