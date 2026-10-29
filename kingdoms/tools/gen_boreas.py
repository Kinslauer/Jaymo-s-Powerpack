"""Boreas, the north wind dragon's art and data (kingdoms/src/main/java/com/jaymoskingdoms/dragons/BoreasDragon.java),
all our own design:
- four hides (frost blue, white, silver-grey, glacier teal: subtle variations) and four glowing marking patterns
  (ice-blue and cyan, with the eyes) for the client's BoreasDragonModel, painted in the way gen_dragons.py paints Notus
  (no outlines, light from the top-left, scale rows, a pale belly), using that script's helpers;
- the spawn egg's icon and item model, and the loot table.
The UV table (PARTS, packed into 256x256 by gen_dragons.pack()) is the one the client's BoreasDragonModel uses: change a
size here and the printed table tells you the texOffs to put there.
Run from kingdoms/: python3 -I tools/gen_boreas.py"""
import os, random, sys

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_dragons as g
from PIL import Image

SIZE = g.SIZE

# Every textured box of Boreas's body plan: name -> (w, h, d). Mirrored left/right parts share one entry.
PARTS = [
    ('body', 12, 10, 34),
    ('wing_skin', 34, 0, 14), ('tip_skin', 38, 0, 10),
    ('neck0', 7, 7, 7), ('neck1', 6, 6, 7), ('neck2', 6, 6, 7), ('neck3', 5, 5, 7), ('neck4', 5, 5, 7),
    ('skull', 8, 6, 9), ('snout', 5, 3, 7), ('jaw', 4, 2, 7),
    ('tail0', 6, 6, 8), ('tail1', 5, 5, 8), ('tail2', 5, 4, 8), ('tail3', 4, 4, 8), ('tail4', 4, 3, 8), ('tail5', 3, 3, 8),
    ('tail6', 3, 2, 8),
    ('fin', 12, 1, 10), ('ridge', 1, 4, 7),
    ('wing_bone', 34, 2, 2), ('tip_bone', 38, 2, 2),
    ('front_upper', 4, 9, 4), ('front_lower', 3, 6, 3), ('foot', 4, 2, 6),
    ('back_upper', 5, 10, 6), ('back_lower', 4, 6, 4),
    ('spike', 2, 6, 2), ('small_spike', 2, 3, 2),
    ('horn', 2, 2, 12), ('horn_short', 2, 2, 6), ('brow_horn', 2, 2, 5),
    ('saddle', 10, 2, 12), ('pommel', 3, 3, 2), ('cantle', 8, 3, 2), ('strap', 13, 11, 3), ('pad', 9, 2, 9),
]
# gen_dragons' paint() and faces() read its UV and DIMS tables: point them at ours
g.PARTS[:] = PARTS
g.UV.clear()
g.UV.update(g.pack())
g.DIMS.clear()
g.DIMS.update({n: (w, h, d) for n, w, h, d in PARTS})
UV, DIMS = g.UV, g.DIMS

rgb, mix, jitter, shade, paint = g.rgb, g.mix, g.jitter, g.shade, g.paint

# hide shades (main, dark, belly, membrane): frost blue (the usual), white, silver-grey, glacier teal
BOREAS_HIDES = [
    (0x8FB8D8, 0x5A84AA, 0xE4F1FA, 0xA9CBE6),
    (0xDCE8EF, 0x9FB4C4, 0xFAFCFE, 0xC7D8E4),
    (0x9AA7B3, 0x65727F, 0xD6DEE5, 0x8795A3),
    (0x6FB0B6, 0x3E7C86, 0xCDE9EA, 0x7DBCC2),
]
HORN_ROOT, HORN_TIP, CLAW = rgb(0x4F647A), rgb(0xEAF8FF), rgb(0x33465A)
EYE, PUPIL, NOSTRIL, MOUTH, TOOTH = rgb(0xB8F2FF), rgb(0x0C2236), rgb(0x1E3448), rgb(0x2C4A66), rgb(0xF2FAFF)
ICICLE_ROOT = rgb(0x7FA0BE)
ICE, ICE_HOT, ICE_DIM = rgb(0x5ADCFF), rgb(0xB8F6FF), rgb(0x2E9CCC)

BELLIES = {'body', 'jaw'} | {'neck%d' % i for i in range(5)} | {'tail%d' % i for i in range(7)}
HORNY = {'spike', 'small_spike', 'horn', 'horn_short', 'brow_horn'}
TACK = ('saddle', 'pommel', 'cantle', 'strap', 'pad')
LEGS = ('front_upper', 'front_lower', 'back_upper', 'back_lower', 'foot')
FINGER, TIP_FINGER = 17, 12


def wing_edge(tip, along, depth):
    """The row (back from the bone) where the membrane's trailing edge falls at {along} the wing: the arm's is a long
    gentle sweep with two shallow scallops; the tip's runs down to a point."""
    if tip:
        length = DIMS['tip_skin'][0]
        return int((depth - 1) * (1.0 - (along / length) ** 1.6))
    length = DIMS['wing_skin'][0]
    t = (along % FINGER) / FINGER
    return depth - 1 - int(3.2 * along / length) - int(2 * (1 - abs(2 * t - 1)) ** 1.5)


def hide_texture(variant):
    main, dark, belly, membrane = (rgb(c) for c in BOREAS_HIDES[variant])
    rnd = random.Random(3000 + variant)
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    white = rgb(0xFFFFFF)

    def scales(face, x, y, fw, fh, base, lite):
        # fine rows of overlapping scales, a light rime on some
        row, col = y % 3, (x + (y // 3) * 2) % 4
        c = base
        if row == 2:
            c = mix(base, dark, 0.35)
        elif row == 0 and col == 0:
            c = mix(base, lite, 0.3)
        if rnd.random() < 0.04:
            c = mix(c, white, 0.55)
        return shade(jitter(c, 5, rnd), face, x, y, fw, fh)

    for name, w, h, d in PARTS:
        if name in ('wing_skin', 'tip_skin'):
            tip = name == 'tip_skin'

            def skin(face, x, y, fw, fh, tip=tip):
                if face not in ('top', 'bottom'):
                    return None
                along = x if face == 'top' else fw - 1 - x
                edge = wing_edge(tip, along, fh)
                if y > edge:
                    return None
                finger = TIP_FINGER if tip else FINGER
                c = membrane
                if along % finger == 0 and y > 1:
                    c = mix(membrane, dark, 0.55)
                elif y >= edge - 1:
                    c = mix(membrane, white, 0.45)
                elif y < 2:
                    c = mix(membrane, main, 0.4)
                return shade(jitter(c, 4, rnd), 'north' if face == 'top' else 'east', x, y, fw, fh)

            paint(img, name, skin)
        elif name in ('fin', 'ridge'):
            def blade(face, x, y, fw, fh, name=name):
                c = mix(membrane, white, 0.25) if (x + y) % 5 else mix(membrane, dark, 0.4)
                if x in (0, fw - 1) or y in (0, fh - 1):
                    c = mix(membrane, white, 0.55)
                return shade(jitter(c, 4, rnd), face, x, y, fw, fh)

            paint(img, name, blade)
        elif name in HORNY:
            def horn(face, x, y, fw, fh, name=name):
                # dark at the root, pale at the tip (the far end: the top of an icicle, the back of a horn)
                if name in ('horn', 'horn_short', 'brow_horn'):
                    t = (x / max(1, fw - 1)) if face in ('top', 'bottom') else (1 - x / max(1, fw - 1)) if face == 'east' else x / max(1, fw - 1)
                    if face == 'south':
                        t = 1.0
                    elif face == 'north':
                        t = 0.0
                    c = mix(HORN_ROOT, HORN_TIP, t)
                else:
                    t = 1.0 - y / max(1, fh - 1) if face not in ('top', 'bottom') else 1.0 if face == 'top' else 0.0
                    c = mix(ICICLE_ROOT, HORN_TIP, t)
                    if face in ('north', 'west') and x == 0:
                        c = mix(c, white, 0.5)
                return shade(jitter(c, 4, rnd), face, x, y, fw, fh)

            paint(img, name, horn)
        elif name in TACK:
            def tack(face, x, y, fw, fh, name=name):
                if name == 'strap':
                    c = g.LEATHER_DARK if y in (0, fh - 1) or x in (0, fw - 1) else g.LEATHER
                    if face in ('east', 'west') and fh // 2 - 1 <= y <= fh // 2 + 1 and fw // 2 - 1 <= x <= fw // 2:
                        c = g.GOLD
                    return shade(jitter(c, 4, rnd), face, x, y, fw, fh)
                c = g.LEATHER
                if x in (0, fw - 1) or y in (0, fh - 1):
                    c = g.LEATHER_DARK
                if face == 'top' and name == 'saddle' and (x, y) in ((1, 1), (fw - 2, 1), (1, fh - 2), (fw - 2, fh - 2)):
                    c = g.GOLD
                if name == 'pad' and face == 'top' and (x + y) % 4 == 0:
                    c = rgb(0x2A4C7A)
                return shade(jitter(c, 5, rnd), face, x, y, fw, fh)

            paint(img, name, tack)
        else:
            def hide(face, x, y, fw, fh, name=name):
                if name in LEGS and face == 'bottom':
                    return shade(jitter(CLAW, 4, rnd), face, x, y, fw, fh)
                if name == 'foot' and face == 'north' and x % 2 == 0:
                    return jitter(CLAW, 4, rnd)
                if name == 'jaw' and face == 'top':
                    return TOOTH if x in (0, fw - 1) or y == 0 else jitter(MOUTH, 6, rnd)
                if face == 'bottom' and name in BELLIES:
                    return shade(jitter(mix(belly, dark, 0.3) if y % 3 == 2 else belly, 5, rnd), face, x, y, fw, fh)
                if name in BELLIES and face in ('east', 'west', 'north', 'south') and y >= fh - 2 and fh >= 5:
                    return shade(jitter(mix(belly, main, 0.4), 5, rnd), face, x, y, fw, fh)
                if name == 'skull' and face in ('east', 'west'):
                    ex = fw - 3 if face == 'east' else 2
                    if y == 2 and abs(x - ex) <= 1:
                        return PUPIL if x == ex else EYE
                    if y == 1 and abs(x - ex) <= 1:
                        return jitter(dark, 4, rnd)
                if name == 'snout' and face == 'north':
                    if y == 1 and x in (1, fw - 2):
                        return NOSTRIL
                    if y == fh - 1:
                        return TOOTH if x % 2 == 0 else jitter(MOUTH, 4, rnd)
                if name == 'snout' and face in ('east', 'west') and y == fh - 1:
                    return TOOTH if x % 2 == 0 else jitter(MOUTH, 4, rnd)
                lite = mix(main, white, 0.3)
                base = mix(main, dark, 0.3) if face == 'top' and name.startswith(('neck', 'tail', 'body')) and abs(x - fw // 2) <= 0 else main
                return scales(face, x, y, fw, fh, base, lite)

            paint(img, name, hide)
    return img


# Emissive patterns: what glows (alpha 0 elsewhere). Every one has the eyes; the rest differ.
MARKS = ['rime', 'bands', 'speckle', 'throat']


def marks_texture(pattern):
    rnd = random.Random(4000 + pattern)
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    kind = MARKS[pattern]

    def glow(c):
        return jitter(c, 10, rnd)

    def eyes(face, x, y, fw, fh):
        if face in ('east', 'west'):
            ex = fw - 3 if face == 'east' else 2
            if y == 2 and abs(x - ex) <= 1:
                return glow(ICE_HOT) if x != ex else rgb(0xFFFFFF)
        return None

    paint(img, 'skull', eyes)

    for name, w, h, d in PARTS:
        if kind == 'rime' and (name.startswith('neck') or name in ('wing_bone', 'tip_bone')):
            # a crooked frost crack, one pixel wide, wandering by the row
            def crack(f, x, y, fw, fh, n=name):
                if f not in ('east', 'west', 'top'):
                    return None
                return glow(ICE if (x + (y * 7 + len(n)) % 5) % 5 == 0 else ICE_DIM) if (x + (y * 7 + len(n)) % 5) % 5 == 0 else None
            paint(img, name, crack)
        elif kind == 'rime' and name in ('wing_skin', 'tip_skin'):
            # frost ferns: a branching line out from each finger
            def fern(f, x, y, fw, fh, n=name):
                if f not in ('top', 'bottom'):
                    return None
                finger = TIP_FINGER if n == 'tip_skin' else FINGER
                along = x if f == 'top' else fw - 1 - x
                edge = wing_edge(n == 'tip_skin', along, fh)
                if y >= edge - 1:
                    return None
                off = (along % finger)
                return glow(ICE_DIM) if y > 1 and (off == 1 and y % 2 == 0 or off == 2 + (y % 3) and y % 4 == 3) else None
            paint(img, name, fern)
        elif kind == 'bands' and name in ('body', 'tail0', 'tail1', 'tail2', 'neck0'):
            def band(f, x, y, fw, fh):
                if f not in ('east', 'west'):
                    return None
                return glow(ICE) if (x // 2) % 4 == 0 and 1 <= y <= fh - 3 and (y + x // 8) % 3 != 0 else None
            paint(img, name, band)
        elif kind == 'speckle' and name in ('body', 'neck0', 'neck1', 'neck2', 'skull', 'snout', 'tail0', 'tail1', 'tail2', 'tail3', 'fin'):
            def fleck(f, x, y, fw, fh):
                if f == 'bottom':
                    return None
                return glow(ICE_HOT if rnd.random() < 0.35 else ICE) if rnd.random() < 0.06 else None
            paint(img, name, fleck)
        elif kind == 'throat' and (name.startswith('neck') or name in ('jaw', 'body', 'tail0', 'tail1', 'ridge')):
            def line(f, x, y, fw, fh, n=name):
                if n == 'ridge':
                    return glow(ICE) if f in ('east', 'west') and 1 <= y <= fh - 2 else None
                if f == 'bottom':
                    if n == 'body' and y > 24:
                        return None
                    return glow(ICE_HOT if abs(x - fw // 2) <= 0 else ICE) if abs(x - fw // 2) <= 1 or (y % 4 == 0 and abs(x - fw // 2) <= 3) else None
                return None
            paint(img, name, line)
    paint(img, 'skull', eyes)
    return img


def species(name, hides):
    folder = g.ASSETS + 'textures/entity/dragon/' + name + '/'
    os.makedirs(folder, exist_ok=True)
    for i in range(len(hides)):
        hide_texture(i).save(folder + 'hide_%d.png' % i)
    for i in range(len(MARKS)):
        marks_texture(i).save(folder + 'marks_%d.png' % i)


if __name__ == '__main__':
    for d in (g.ITEM, g.ASSETS + 'items/', g.ASSETS + 'models/item/', g.DATA + 'jaymoskingdoms/loot_table/entities/'):
        os.makedirs(d, exist_ok=True)
    # (0.7.321) Boreas wears an icy recolour of nyxaris' Fire Dragon now: tools/gen_fire_dragons.py
    # species('boreas', BOREAS_HIDES)
    g.egg('boreas_dragon_spawn_egg', rgb(0xA9CBE6), rgb(0x5ADCFF), 78)
    g.write(g.ASSETS + 'items/boreas_dragon_spawn_egg.json',
            {'model': {'type': 'minecraft:model', 'model': 'jaymoskingdoms:item/boreas_dragon_spawn_egg'}})
    g.write(g.ASSETS + 'models/item/boreas_dragon_spawn_egg.json',
            {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jaymoskingdoms:item/boreas_dragon_spawn_egg'}})
    g.write(g.DATA + 'jaymoskingdoms/loot_table/entities/boreas_dragon.json',
            {'type': 'minecraft:entity', 'pools': [{'rolls': 1, 'entries': [e]} for e in (
                g.entry('minecraft:leather', 3, 6), g.entry('minecraft:bone', 2, 5), g.entry('minecraft:packed_ice', 2, 6),
                g.entry('minecraft:snowball', 4, 10), g.entry('minecraft:prismarine_crystals', 1, 3))],
             'random_sequence': 'jaymoskingdoms:entities/boreas_dragon'})
    for name, (u, v) in sorted(UV.items(), key=lambda kv: (kv[1][1], kv[1][0])):
        print('%-12s texOffs(%d, %d)  %s' % (name, u, v, DIMS[name]))
