"""The dragons' art and data (kingdoms/src/main/java/com/jaymoskingdoms/dragons), all our own design (Berk's Dragon
Academy, which the idea of riding dragons brings to mind, is All Rights Reserved: nothing of it is used):
- each species' hides (a few shades, picked per dragon by its DragonLook) and its glowing marking patterns (an
  emissive layer: ember veins and the eyes) for the client's dragon model, painted in vanilla's way (no outlines,
  light from the top-left, scale rows, a pale belly);
- the spawn egg's icon and item model, and the loot table.
The UV table (PARTS, packed into 256x256 by pack()) is the one the client's NotusDragonModel uses: change a size here
and the printed table tells you the texOffs to put there.
Run from kingdoms/: python3 -I tools/gen_dragons.py"""
import json, os, random
from PIL import Image

RES = 'src/main/resources/'
ASSETS = RES + 'assets/jaymoskingdoms/'
DATA = RES + 'data/'
ITEM = ASSETS + 'textures/item/'
SIZE = 256

# Every textured box of the dragon body plan: name -> (w, h, d). Mirrored left/right parts share one entry.
PARTS = [
    ('body', 18, 16, 36),
    ('wing_skin', 26, 0, 22),
    ('tip_skin', 30, 0, 24),
    ('neck0', 10, 10, 8), ('neck1', 9, 9, 8), ('neck2', 8, 8, 8), ('neck3', 8, 7, 8),
    ('skull', 12, 9, 11), ('snout', 8, 5, 9), ('jaw', 7, 2, 9),
    ('tail0', 10, 9, 9), ('tail1', 9, 8, 9), ('tail2', 8, 7, 9), ('tail3', 7, 6, 9), ('tail4', 6, 5, 9), ('tail5', 5, 4, 9),
    ('club', 9, 7, 9), ('nub', 3, 3, 3),
    ('wing_bone', 26, 3, 3), ('tip_bone', 30, 2, 2),
    ('front_upper', 6, 9, 6), ('front_lower', 5, 6, 5), ('foot', 6, 2, 8),
    ('back_upper', 7, 10, 8), ('back_lower', 5, 8, 6),
    ('spike', 2, 5, 3), ('small_spike', 2, 3, 2),
    ('horn', 2, 2, 10), ('horn_short', 2, 2, 6), ('brow_horn', 2, 2, 5),
    ('saddle', 14, 2, 14), ('pommel', 4, 3, 2), ('cantle', 10, 3, 2), ('strap', 19, 17, 3), ('pad', 12, 2, 10),
]


def region(w, h, d):
    return 2 * (w + d), h + d


def pack():
    """Shelf-packs PARTS (tallest first) into the texture; returns name -> (u, v)."""
    order = sorted(PARTS, key=lambda p: -region(p[1], p[2], p[3])[1])
    at, x, y, shelf = {}, 0, 0, 0
    for name, w, h, d in order:
        rw, rh = region(w, h, d)
        if x + rw > SIZE:
            x, y, shelf = 0, y + shelf, 0
        at[name] = (x, y)
        x += rw
        shelf = max(shelf, rh)
    assert y + shelf <= SIZE, 'the parts don\'t fit'
    return at


UV = pack()
DIMS = {n: (w, h, d) for n, w, h, d in PARTS}


def rgb(h, a=255):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255, a)


def jitter(c, n, rnd):
    k = rnd.randint(-n, n)
    return tuple(max(0, min(255, ch + k)) for ch in c[:3]) + (c[3] if len(c) > 3 else 255,)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def faces(name):
    """Each face's rectangle on the texture: top, bottom, then the four sides (east is the -x side, whose right
    edge meets the front; west the +x side, whose left edge meets the front)."""
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


# ---- Notus ---------------------------------------------------------------------------------------------------------
# hide shades (main, dark, belly, membrane): rust (the usual), oxblood, cinder (sooty), and sunbaked (the badlands' clay)
NOTUS_HIDES = [
    (0x8A3320, 0x5A1E14, 0xD08A3A, 0x5A1C14),
    (0x74241C, 0x4A1410, 0xC27A34, 0x4A1410),
    (0x5E3028, 0x3A1C18, 0xB8783A, 0x3E1A16),
    (0x9A4626, 0x642A18, 0xDA9A48, 0x66261A),
]
HORN, HORN_TIP, CLAW = rgb(0x2E2622), rgb(0x8C7A66), rgb(0x1E1814)
EYE, PUPIL, NOSTRIL, MOUTH = rgb(0xFFD23A), rgb(0x1A0A04), rgb(0x1C0C08), rgb(0x3A0E0A)
LEATHER, LEATHER_DARK, GOLD = rgb(0x6B3F22), rgb(0x4A2A16), rgb(0xE8B830)
EMBER, EMBER_HOT, EMBER_DIM = rgb(0xFF8A1A), rgb(0xFFD25A), rgb(0xC0400E)

BELLIES = {'body', 'neck0', 'neck1', 'neck2', 'neck3', 'jaw', 'tail0', 'tail1', 'tail2', 'tail3', 'tail4', 'tail5'}
HORNY = {'spike', 'small_spike', 'horn', 'horn_short', 'brow_horn', 'nub', 'club'}


def shade(c, face, x, y, fw, fh):
    """Light from the top-left: tops lightest, the -x side and bottoms darkest, a soft band on the lower edge."""
    k = {'top': 1.12, 'north': 1.0, 'west': 0.95, 'east': 0.82, 'south': 0.9, 'bottom': 0.78}[face]
    if face not in ('top', 'bottom') and fh > 3 and y >= fh - 1:
        k *= 0.88
    return tuple(max(0, min(255, int(ch * k))) for ch in c[:3]) + (255,)


def hide_texture(variant):
    main, dark, belly, membrane = (rgb(c) for c in NOTUS_HIDES[variant])
    rnd = random.Random(1000 + variant)
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))

    def scales(face, x, y, fw, fh, base, lite):
        # rows of overlapping scales: each scale's lower edge dark, its upper-left lit
        row, col = y % 3, (x + (y // 3) * 2) % 4
        c = base
        if row == 2:
            c = mix(base, dark, 0.45)
        elif row == 0 and col == 0:
            c = mix(base, lite, 0.25)
        return shade(jitter(c, 6, rnd), face, x, y, fw, fh)

    for name, w, h, d in PARTS:
        if name in ('wing_skin', 'tip_skin'):
            tip = name == 'tip_skin'

            def skin(face, x, y, fw, fh, tip=tip):
                if face not in ('top', 'bottom'):
                    return None
                # (x runs along the wing, y back from the bone): the trailing edge scalloped between the fingers
                depth = fh
                along = x if face == 'top' else fw - 1 - x
                finger = 10 if tip else 13
                t = (along % finger) / finger
                edge = depth - 1 - int(5 * (1 - abs(2 * t - 1)) ** 1.5)
                if tip:
                    edge = min(edge, depth - 1 - int(along * 0.35))
                if y > edge:
                    return None
                c = membrane
                if along % finger == 0 or (along % finger == 1 and y < depth // 2):
                    c = mix(membrane, dark, 0.6)
                elif y >= edge - 1:
                    c = mix(membrane, dark, 0.35)
                if face == 'top' and y < 2:
                    c = mix(c, main, 0.3)
                return shade(jitter(c, 5, rnd), 'north' if face == 'top' else 'east', x, y, fw, fh)

            paint(img, name, skin)
        elif name in HORNY:
            def horn(face, x, y, fw, fh, name=name):
                if name == 'club':
                    c = mix(main, dark, 0.5) if (x + y) % 3 else HORN
                    return shade(jitter(c, 6, rnd), face, x, y, fw, fh)
                # darker at the root, pale at the tip (the tip is the far end: the top of a spike, the back of a horn)
                if name in ('horn', 'horn_short', 'brow_horn'):
                    t = (x / max(1, fw - 1)) if face in ('top', 'bottom') else (1 - x / max(1, fw - 1)) if face == 'east' else x / max(1, fw - 1)
                    if face == 'south':
                        t = 1.0
                    elif face == 'north':
                        t = 0.0
                else:
                    t = 1.0 - y / max(1, fh - 1) if face not in ('top', 'bottom') else 1.0 if face == 'top' else 0.0
                return shade(jitter(mix(HORN, HORN_TIP, t * 0.8), 5, rnd), face, x, y, fw, fh)

            paint(img, name, horn)
        elif name in ('saddle', 'pommel', 'cantle', 'strap', 'pad'):
            def tack(face, x, y, fw, fh, name=name):
                if name == 'strap':
                    # a girth band: only its outer faces show; the inside sits in the hide
                    c = LEATHER_DARK if y in (0, fh - 1) or x in (0, fw - 1) else LEATHER
                    if face in ('east', 'west') and fh // 2 - 1 <= y <= fh // 2 + 1 and fw // 2 - 1 <= x <= fw // 2:
                        c = GOLD
                    return shade(jitter(c, 4, rnd), face, x, y, fw, fh)
                c = LEATHER
                if x in (0, fw - 1) or y in (0, fh - 1):
                    c = LEATHER_DARK
                if face == 'top' and name == 'saddle' and (x, y) in ((1, 1), (fw - 2, 1), (1, fh - 2), (fw - 2, fh - 2)):
                    c = GOLD
                if name == 'pad' and face == 'top' and (x + y) % 4 == 0:
                    c = rgb(0x7A1E1A)
                return shade(jitter(c, 5, rnd), face, x, y, fw, fh)

            paint(img, name, tack)
        else:
            def hide(face, x, y, fw, fh, name=name):
                belly_face = face == 'bottom' and name in BELLIES
                if name in ('front_upper', 'front_lower', 'back_upper', 'back_lower', 'foot') and face == 'bottom':
                    return shade(jitter(CLAW, 4, rnd), face, x, y, fw, fh)
                if name == 'foot' and face == 'north' and x % 2 == 0:
                    return jitter(CLAW, 4, rnd)
                if name == 'jaw' and face == 'top':
                    # the inside of the mouth, with a row of teeth round its edge
                    return rgb(0xE8E0C8) if x in (0, fw - 1) or y == 0 else jitter(MOUTH, 6, rnd)
                if belly_face:
                    # pale belly plates, a dark seam every third row
                    return shade(jitter(mix(belly, dark, 0.35) if y % 3 == 2 else belly, 6, rnd), face, x, y, fw, fh)
                if name in BELLIES and face in ('east', 'west', 'north', 'south') and y >= fh - 2 and fh >= 5:
                    return shade(jitter(mix(belly, main, 0.4), 6, rnd), face, x, y, fw, fh)
                if name == 'skull' and face in ('east', 'west'):
                    ex = fw - 3 if face == 'east' else 2
                    if y == 3 and abs(x - ex) <= 1:
                        return PUPIL if x == ex else EYE
                    if y == 2 and abs(x - ex) <= 1:
                        return jitter(dark, 4, rnd)
                if name == 'snout' and face == 'north':
                    if y == 1 and x in (1, fw - 2):
                        return NOSTRIL
                    if y == fh - 1:
                        return rgb(0xE8E0C8) if x % 2 == 0 else jitter(MOUTH, 4, rnd)
                if name == 'snout' and face in ('east', 'west') and y == fh - 1:
                    return rgb(0xE8E0C8) if x % 2 == 0 else jitter(MOUTH, 4, rnd)
                lite = mix(main, rgb(0xFFFFFF), 0.25)
                base = mix(main, dark, 0.3) if face == 'top' and name.startswith(('neck', 'tail', 'body')) and abs(x - fw // 2) <= 1 else main
                return scales(face, x, y, fw, fh, base, lite)

            paint(img, name, hide)
    return img


# Emissive patterns: what glows (alpha 0 elsewhere). Every one has the eyes; the rest differ.
MARKS = ['veins', 'flanks', 'freckles', 'throat']


def marks_texture(pattern):
    rnd = random.Random(2000 + pattern)
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    kind = MARKS[pattern]

    def glow(c):
        return jitter(c, 10, rnd)

    def eyes(face, x, y, fw, fh):
        if face in ('east', 'west'):
            ex = fw - 3 if face == 'east' else 2
            if y == 3 and abs(x - ex) <= 1:
                return glow(EMBER_HOT) if x != ex else rgb(0xFFF0B0)
        return None

    paint(img, 'skull', eyes)

    def vein_line(face, x, y, fw, fh, period, phase):
        # a crooked crack: one pixel wide, wandering by the row
        if face not in ('east', 'west', 'top'):
            return None
        wander = (y * 7 + phase) % period
        return glow(EMBER if (x + wander) % period == 0 else EMBER_DIM) if (x + wander) % period in (0,) else None

    for name, w, h, d in PARTS:
        if kind == 'veins' and (name.startswith('neck') or name in ('wing_bone', 'tip_bone')):
            paint(img, name, lambda f, x, y, fw, fh, n=name: vein_line(f, x, y, fw, fh, 5, len(n)))
        elif kind == 'veins' and name in ('wing_skin', 'tip_skin'):
            def wv(f, x, y, fw, fh):
                if f not in ('top', 'bottom'):
                    return None
                finger = 10 if fw == 30 else 13
                along = x if f == 'top' else fw - 1 - x
                return glow(EMBER_DIM) if along % finger == 1 and y < fh - 6 and y % 2 == 0 else None
            paint(img, name, wv)
        elif kind == 'flanks' and name in ('body', 'tail0', 'tail1', 'tail2'):
            def fl(f, x, y, fw, fh):
                if f not in ('east', 'west'):
                    return None
                return glow(EMBER) if (x // 2) % 4 == 0 and 2 <= y <= fh - 4 and (y + x // 8) % 3 != 0 else None
            paint(img, name, fl)
        elif kind == 'freckles' and name in ('body', 'neck0', 'neck1', 'skull', 'tail0', 'tail1', 'tail2', 'tail3', 'club'):
            def fr(f, x, y, fw, fh):
                if f == 'bottom':
                    return None
                return glow(EMBER_HOT if rnd.random() < 0.3 else EMBER) if rnd.random() < 0.05 else None
            paint(img, name, fr)
        elif kind == 'throat' and (name.startswith('neck') or name in ('jaw', 'body')):
            def th(f, x, y, fw, fh, n=name):
                if f == 'bottom':
                    if n == 'body' and y > 12:
                        return None
                    return glow(EMBER_HOT if abs(x - fw // 2) <= 1 else EMBER) if abs(x - fw // 2) <= 2 or y % 3 == 0 else None
                return None
            paint(img, name, th)
    paint(img, 'skull', eyes)
    return img


def egg(name, base, spots, seed):
    rnd = random.Random(seed)
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            dx, dy = (x - 7.5) / 5.0, (y - 8.5) / 6.5
            if dx * dx + dy * dy * (1.0 + 0.25 * (8.5 - y) / 6.5) <= 1.0:
                c = spots if rnd.random() < 0.18 else base
                if x <= 5 and y <= 6:
                    c = jitter(c, 30, rnd)
                px[x, y] = jitter(c, 6, rnd)
    img.save(ITEM + name + '.png')


def write(path, obj):
    with open(path, 'w', newline='\n') as f:
        json.dump(obj, f, indent='\t')
        f.write('\n')


def entry(item, lo, hi):
    return {'type': 'minecraft:item', 'name': item, 'modifier': [
        {'type': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': lo, 'max': hi}},
        {'type': 'minecraft:enchanted_count_increase', 'count': {'type': 'minecraft:uniform', 'min': 0.0, 'max': 1.0},
         'enchantment': 'minecraft:looting'}]}


def species(name, hides):
    folder = ASSETS + 'textures/entity/dragon/' + name + '/'
    os.makedirs(folder, exist_ok=True)
    for i in range(len(hides)):
        hide_texture(i).save(folder + 'hide_%d.png' % i)
    for i in range(len(MARKS)):
        marks_texture(i).save(folder + 'marks_%d.png' % i)


if __name__ == '__main__':
    for d in (ITEM, ASSETS + 'items/', ASSETS + 'models/item/', DATA + 'jaymoskingdoms/loot_table/entities/'):
        os.makedirs(d, exist_ok=True)
    # (0.7.326) Notus wears nyxaris' Fire Dragon now: its hides and markings come from tools/gen_fire_dragons.py
    # species('notus', NOTUS_HIDES)
    egg('notus_dragon_spawn_egg', rgb(0x8A3320), rgb(0xFF8A1A), 77)
    write(ASSETS + 'items/notus_dragon_spawn_egg.json', {'model': {'type': 'minecraft:model', 'model': 'jaymoskingdoms:item/notus_dragon_spawn_egg'}})
    write(ASSETS + 'models/item/notus_dragon_spawn_egg.json',
          {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jaymoskingdoms:item/notus_dragon_spawn_egg'}})
    write(DATA + 'jaymoskingdoms/loot_table/entities/notus_dragon.json',
          {'type': 'minecraft:entity', 'pools': [{'rolls': 1, 'entries': [e]} for e in (
              entry('minecraft:leather', 4, 8), entry('minecraft:bone', 3, 6), entry('minecraft:blaze_powder', 2, 5),
              entry('minecraft:magma_cream', 1, 3))],
           'random_sequence': 'jaymoskingdoms:entities/notus_dragon'})
    for name, (u, v) in sorted(UV.items(), key=lambda kv: (kv[1][1], kv[1][0])):
        print('%-12s texOffs(%d, %d)  %s' % (name, u, v, DIMS[name]))
