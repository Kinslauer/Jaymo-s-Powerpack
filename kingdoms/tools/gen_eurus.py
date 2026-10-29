"""Eurus' art and data (kingdoms/src/main/java/com/jaymoskingdoms/dragons/EurusDragon), all our own design:
- four hides (storm slate, blue-violet, charcoal, pale thundercloud; a pale grey belly, cloud-like mottling, long wind
  streaks) picked per dragon by its DragonLook, and four glowing marking patterns (forked bolts, streaks, sparks, arcs;
  electric pale cyan, with the eyes in every one) for the client's EurusDragonModel;
- the spawn egg's icon and item model, and the loot table.
The UV table (PARTS, packed into 256x256 by pack()) is the one EurusDragonModel's texOffs come from: change a size
here and the printed table tells you the texOffs to put there. It uses gen_dragons.py's helpers (not its UV table).
It never touches textures/entity/dragon/eurus/villager.png (the disguise's skin).
Run from kingdoms/: python3 -I tools/gen_eurus.py"""
import os, random, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image
import gen_dragons as g
from gen_dragons import rgb, jitter, mix, shade, write, entry, egg, ASSETS, DATA, ITEM, SIZE

# Every textured box of the body plan: name -> (w, h, d). Mirrored left/right parts share one entry.
PARTS = [
    ('body', 12, 9, 30),
    ('wing_skin', 28, 0, 16),
    ('tip_skin', 30, 0, 14),
    ('neck0', 7, 7, 6), ('neck1', 6, 6, 6), ('neck2', 6, 6, 6), ('neck3', 5, 5, 5),
    ('skull', 8, 5, 8), ('snout', 5, 3, 7), ('jaw', 4, 2, 7), ('eye', 2, 2, 2),
    ('crest', 1, 3, 6),
    ('tail0', 5, 5, 8), ('tail1', 4, 4, 8), ('tail2', 4, 4, 8), ('tail3', 3, 3, 8), ('tail4', 3, 3, 8), ('tail5', 2, 2, 8),
    ('fork', 5, 1, 10),
    ('wing_bone', 28, 3, 3), ('tip_bone', 30, 2, 2),
    ('front_upper', 3, 7, 3), ('front_lower', 3, 4, 3), ('front_foot', 3, 2, 5),
    ('back_upper', 4, 7, 4), ('back_lower', 3, 4, 3), ('back_foot', 3, 2, 6),
    ('spike', 1, 4, 3),
    ('horn', 2, 2, 8), ('horn_tip', 2, 2, 6), ('horn_short', 2, 2, 5), ('horn_short_tip', 2, 2, 4), ('brow_horn', 2, 2, 5),
    ('saddle', 10, 2, 10), ('pommel', 3, 3, 2), ('cantle', 8, 3, 2), ('strap', 14, 11, 3), ('pad', 9, 2, 8),
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
    assert y + shelf <= SIZE, "the parts don't fit"
    return at


UV = pack()
DIMS = {n: (w, h, d) for n, w, h, d in PARTS}


def faces(name):
    """Each face's rectangle on the texture (top, bottom, then the sides: east is the -x side, whose right edge meets
    the front; west the +x side, whose left edge meets the front)."""
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


# hide shades (main, dark, belly, membrane)
HIDES = [
    (0x5C6474, 0x353B49, 0xAEB6C6, 0x454C63),  # storm slate (the usual)
    (0x585A92, 0x34345E, 0xA8A8D2, 0x3E3F78),  # blue-violet
    (0x40454F, 0x23262E, 0x8C94A6, 0x30344A),  # charcoal (the darkest)
    (0x7E8698, 0x4C5266, 0xC6CCDA, 0x585F7C),  # pale thundercloud
]
STREAK = [0.4, 0.45, 0.5, 0.35]
HORN, HORN_TIP, CLAW = rgb(0x22252E), rgb(0x9AA2B8), rgb(0x16181E)
EYE, PUPIL, NOSTRIL, MOUTH = rgb(0xC8F8FF), rgb(0x0A1018), rgb(0x12141C), rgb(0x2A2438)
LEATHER, LEATHER_DARK, GOLD = rgb(0x5A4636), rgb(0x3E2E22), rgb(0xD8C070)
ARC, ARC_HOT, ARC_DIM = rgb(0xA8F4FF), rgb(0xE8FFFF), rgb(0x5CC8E0)
RAY = rgb(0x262A3C)

BELLIES = {'body', 'neck0', 'neck1', 'neck2', 'neck3', 'jaw', 'tail0', 'tail1', 'tail2', 'tail3', 'tail4', 'tail5'}
HORNY = {'horn', 'horn_tip', 'horn_short', 'horn_short_tip', 'brow_horn', 'crest', 'spike'}
FEET = {'front_foot', 'back_foot'}
LEGS = {'front_upper', 'front_lower', 'back_upper', 'back_lower'}
SKINNED = BELLIES | {'skull', 'snout'} | LEGS | FEET


def noise_field(seed, cells=40, scale=9.0, sx=1.0, sy=1.0):
    rnd = random.Random(seed)
    grid = [[rnd.random() for _ in range(cells)] for _ in range(cells)]

    def at(x, y):
        fx, fy = x / scale * sx, y / scale * sy
        x0, y0 = int(fx) % (cells - 1), int(fy) % (cells - 1)
        tx, ty = fx - int(fx), fy - int(fy)
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        a = grid[y0][x0] * (1 - tx) + grid[y0][x0 + 1] * tx
        b = grid[y0 + 1][x0] * (1 - tx) + grid[y0 + 1][x0 + 1] * tx
        return a * (1 - ty) + b * ty
    return at


FACE_NO = ['top', 'bottom', 'east', 'north', 'west', 'south']


def hide_texture(variant):
    main, dark, belly, membrane = (rgb(c) for c in HIDES[variant])
    rnd = random.Random(7000 + variant)
    cloud = noise_field(60 + variant)
    # long wind streaks: stretched along the part's length (the x of a side face)
    streaks = noise_field(90 + variant, scale=9.0, sx=0.35, sy=2.6)
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))

    def skin(name, face, x, y, fw, fh, base):
        u, v = UV[name]
        n = cloud(u + x * 1.2 + FACE_NO.index(face) * 5, v + y * 1.2)
        c = base
        if n > 0.55:
            c = mix(base, dark, min(1.0, (n - 0.55) * 2.4) * 0.55)
        elif n < 0.3:
            c = mix(base, rgb(0xFFFFFF), (0.3 - n) * 0.3)
        s = streaks(u + x * 1.0 + FACE_NO.index(face) * 7, v + y * 1.0)
        if s > 0.62:
            c = mix(c, belly, (s - 0.62) * 1.8 * STREAK[variant])
        elif s < 0.28:
            c = mix(c, dark, (0.28 - s) * 1.6 * STREAK[variant])
        r = rnd.random()
        if r < 0.025:
            c = mix(c, dark, 0.3)
        return shade(jitter(c, 3, rnd), face, x, y, fw, fh)

    for name, w, h, d in PARTS:
        if name in ('wing_skin', 'tip_skin'):
            tip = name == 'tip_skin'
            finger = 7
            torn = random.Random(177 + variant * 5 + tip)
            # a clean, swept membrane: it narrows toward the tip, with a scalloped trailing edge between the finger bones
            reach = []
            for along in range(w):
                t = (along % finger) / finger
                scallop = int(3.5 * (1 - abs(2 * t - 1)) ** 1.2)
                taper = int(along * (0.42 if tip else 0.3))
                r = d - 1 - taper - scallop
                if along % finger == 0:
                    r = d - 1 - taper + 1 - torn.randint(0, 1)   # the finger bones reach furthest
                reach.append(max(2, min(d - 1, r)))

            def web(face, x, y, fw, fh, reach=reach):
                if face not in ('top', 'bottom'):
                    return None
                along = x if face == 'top' else fw - 1 - x
                if y > reach[along]:
                    return None
                c = membrane
                if along % finger == 0:
                    c = mix(membrane, RAY, 0.65)
                elif y >= reach[along] - 1:
                    c = mix(membrane, belly, 0.3)
                if y < 2:
                    c = mix(c, main, 0.4)
                return shade(jitter(c, 4, rnd), 'north' if face == 'top' else 'east', x, y, fw, fh)

            paint(img, name, web)
        elif name in HORNY:
            def horn(face, x, y, fw, fh, name=name):
                # dark at the root, pale at the tip
                tipish = name.endswith('tip')
                t = (0.6 if tipish else 0.0) + (x / max(1, fw - 1) if face in ('top', 'bottom') else 0.2) * 0.4
                if name in ('crest', 'spike'):
                    t = 0.15 + (y / max(1, fh - 1)) * 0.0 + (0.4 if face == 'south' else 0.0)
                if face == 'south':
                    t = 1.0
                return shade(jitter(mix(HORN, HORN_TIP, min(1.0, t)), 5, rnd), face, x, y, fw, fh)

            paint(img, name, horn)
        elif name in ('saddle', 'pommel', 'cantle', 'strap', 'pad'):
            def tack(face, x, y, fw, fh, name=name):
                if name == 'strap':
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
                    c = rgb(0x4A2C6A)
                return shade(jitter(c, 5, rnd), face, x, y, fw, fh)

            paint(img, name, tack)
        elif name == 'fork':
            def fin(face, x, y, fw, fh):
                if face in ('top', 'bottom'):
                    # a pointed blade: it narrows toward its tip (y grows backward)
                    centre = (fw - 1) / 2.0
                    if abs(x - centre) > (fw / 2.0) * (1.0 - y / float(fh + 1)) + 0.3:
                        return None
                    c = mix(membrane, RAY, 0.55) if x % 2 == 0 else mix(membrane, belly, 0.25)
                    return shade(jitter(c, 4, rnd), face, x, y, fw, fh)
                return shade(jitter(mix(membrane, dark, 0.3), 4, rnd), face, x, y, fw, fh)

            paint(img, name, fin)
        elif name == 'eye':
            def eye(face, x, y, fw, fh):
                if face == 'bottom':
                    return shade(main, face, x, y, fw, fh)
                if face == 'top':
                    return shade(jitter(mix(main, EYE, 0.45), 4, rnd), face, x, y, fw, fh)
                return PUPIL if x == fw // 2 and y >= 0 and face in ('north', 'south') else jitter(EYE, 6, rnd)

            paint(img, name, eye)
        else:
            def body(face, x, y, fw, fh, name=name):
                if name in FEET and face == 'bottom':
                    return shade(jitter(mix(main, dark, 0.35), 4, rnd), face, x, y, fw, fh)
                if name in FEET and face == 'north':
                    # three sharp talons
                    return jitter(CLAW, 4, rnd) if y >= 0 and x % 2 == 0 else jitter(mix(main, dark, 0.3), 4, rnd)
                if name in LEGS and face == 'bottom':
                    return shade(jitter(dark, 4, rnd), face, x, y, fw, fh)
                if name == 'jaw' and face == 'top':
                    # the inside of the mouth, a row of small teeth round the edge
                    return rgb(0xE6E8F0) if x in (0, fw - 1) or y == 0 else jitter(MOUTH, 6, rnd)
                if face == 'bottom' and name in BELLIES:
                    return shade(jitter(mix(belly, dark, 0.3) if y % 3 == 2 else belly, 5, rnd), face, x, y, fw, fh)
                if name in BELLIES and face in ('east', 'west', 'north', 'south') and fh >= 5 and y >= fh - 2:
                    return shade(jitter(mix(belly, main, 0.35), 5, rnd), face, x, y, fw, fh)
                if name == 'snout' and face == 'top' and y == 1 and x in (1, fw - 2):
                    return NOSTRIL
                if name == 'snout' and face == 'north' and y == fh - 1:
                    return rgb(0xE6E8F0) if x % 2 == 0 else jitter(MOUTH, 4, rnd)
                if name == 'snout' and face in ('east', 'west') and y == fh - 1:
                    return rgb(0xE6E8F0) if x % 2 == 0 else jitter(MOUTH, 4, rnd)
                if name == 'skull' and face in ('east', 'west') and y == fh - 1:
                    return jitter(MOUTH, 4, rnd)
                base = main
                if face == 'top' and name in ('body', 'neck0', 'neck1', 'neck2', 'neck3') and abs(x - fw // 2) <= 0:
                    base = mix(main, dark, 0.3)
                return skin(name, face, x, y, fw, fh, base)

            paint(img, name, body)
    return img


MARKS = ['bolts', 'streaks', 'sparks', 'arcs']
SPOT_PARTS = ['body', 'neck0', 'neck1', 'skull', 'tail0', 'tail1', 'front_upper', 'back_upper']
LONG_PARTS = ['body', 'neck0', 'neck1', 'neck2', 'neck3', 'tail0', 'tail1', 'tail2', 'skull']


def disc(img, rect, cx, cy, r, color, ring=False):
    fx, fy, fw, fh = rect
    px = img.load()
    for y in range(fh):
        for x in range(fw):
            d = (x - cx) ** 2 + (y - cy) ** 2
            inside = d <= r * r + 1 and (not ring or d >= (r - 1) ** 2 + 1)
            if inside:
                px[fx + x, fy + y] = color


def marks_texture(pattern):
    rnd = random.Random(9000 + pattern)
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    kind = MARKS[pattern]
    px = img.load()

    def glow(c):
        return jitter(c, 10, rnd)

    def eyes(face, x, y, fw, fh):
        # a glowing eye round a dark slit
        if face in ('top', 'bottom'):
            return None
        return None if x == fw // 2 and face in ('north', 'south') else glow(ARC_HOT)

    def put(rect, x, y, c):
        fx, fy, fw, fh = rect
        if 0 <= x < fw and 0 <= y < fh:
            px[fx + x, fy + y] = c

    for name in [n for n, *_ in PARTS]:
        fc = faces(name)
        if kind == 'bolts' and name in ('body', 'neck0', 'neck1', 'tail0', 'tail1', 'skull', 'back_upper', 'front_upper'):
            # a forked bolt down each flank: a zigzag from the back with a branch off it
            for face in ('east', 'west'):
                rect = fc[face]
                fw, fh = rect[2], rect[3]
                if fw < 5 or fh < 4:
                    continue
                for start in range(2, fw - 2, 9 if name == 'body' else 7):
                    x = start
                    for y in range(0, fh - 1):
                        put(rect, x, y, glow(ARC))
                        put(rect, x + (1 if y % 2 else 0), y, glow(ARC_HOT))
                        x += 1 if (y // 2) % 2 == 0 else -1
                        if y == fh // 2 - 1:
                            for k in range(1, 4):
                                put(rect, x + k, y + k // 2, glow(ARC_DIM))
        elif kind == 'streaks' and name in LONG_PARTS:
            # long lines of light running the length of the beast
            for face in ('top', 'east', 'west'):
                rect = fc[face]
                fw, fh = rect[2], rect[3]
                for row in range(1 if face != 'top' else 2, fh - 1, 3):
                    if face == 'top':
                        continue
                    for x in range(0, fw):
                        if (x // 5 + row) % 2 == 0 and x % 7 != 6:
                            put(rect, x, row, glow(ARC_DIM if row % 2 else ARC))
                if face == 'top':
                    for col in range(2, fw - 1, 4):
                        for y in range(0, fh):
                            if y % 6 != 5:
                                put(rect, col, y, glow(ARC_DIM))
        elif kind == 'sparks' and name in SPOT_PARTS + ['neck2', 'tail2']:
            # scattered points of light, now and then a small + of them
            for face, rect in fc.items():
                if face in ('top', 'east', 'west') and rect[2] > 2 and rect[3] > 2:
                    for _ in range(max(2, rect[2] * rect[3] // 22)):
                        cx, cy = rnd.randint(1, rect[2] - 2), rnd.randint(1, rect[3] - 2)
                        c = glow(rnd.choice((ARC, ARC, ARC_HOT, ARC_DIM)))
                        put(rect, cx, cy, c)
                        if rnd.random() < 0.3:
                            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                                put(rect, cx + dx, cy + dy, glow(ARC_DIM))
        elif kind == 'arcs' and name in SPOT_PARTS:
            # ripples of light (rings) on the flanks and shoulders
            for face, rect in fc.items():
                if face in ('top', 'east', 'west') and rect[2] > 5 and rect[3] > 5:
                    for _ in range(max(1, rect[2] * rect[3] // 90)):
                        cx, cy = rnd.randint(2, rect[2] - 3), rnd.randint(2, rect[3] - 3)
                        disc(img, rect, cx, cy, 3 if name == 'body' else 2, glow(ARC), ring=True)
                        if name == 'body':
                            disc(img, rect, cx, cy, 1, glow(ARC_HOT))
        # the wings and fins carry their pattern too
        if name in ('wing_skin', 'tip_skin'):
            top = fc['top']
            fw, fh = top[2], top[3]
            bottom = fc['bottom']
            for rect, flip in ((top, False), (bottom, True)):
                if kind == 'bolts':
                    # a zigzag along the leading arm
                    for x in range(fw):
                        y = 2 + (x // 2) % 2
                        put(rect, fw - 1 - x if flip else x, y, glow(ARC))
                elif kind == 'streaks':
                    for row in (3, 6, 9):
                        if row < fh - 3:
                            for x in range(1, fw - 2):
                                if (x + row) % 4 != 3:
                                    put(rect, fw - 1 - x if flip else x, row, glow(ARC_DIM))
                elif kind == 'sparks':
                    for _ in range(fw * fh // 40):
                        put(rect, rnd.randint(1, fw - 2), rnd.randint(1, fh - 2), glow(ARC))
                elif kind == 'arcs':
                    disc(img, (rect[0], rect[1], rect[2], rect[3]), fw // 3, fh // 2, 3, glow(ARC), ring=True)
                    disc(img, (rect[0], rect[1], rect[2], rect[3]), fw // 3 * 2 if name == 'wing_skin' else fw // 2, fh // 3, 2, glow(ARC_DIM), ring=True)
        if name == 'fork':
            for face in ('top', 'bottom'):
                rect = fc[face]
                for y in range(rect[3]):
                    put(rect, rect[2] // 2, y, glow(ARC if kind != 'sparks' else ARC_DIM))
        if name == 'crest' or name.startswith('horn_tip') or name == 'horn_short_tip':
            # the horn tips and the crest spark faintly in every pattern
            for face, rect in fc.items():
                if face == 'south':
                    for y in range(rect[3]):
                        for x in range(rect[2]):
                            put(rect, x, y, glow(ARC_HOT))
        if name == 'tail5':
            for face in ('top', 'east', 'west'):
                rect = fc[face]
                for x in range(rect[2]):
                    for y in range(rect[3]):
                        if (x + y) % 3 == 0:
                            put(rect, x, y, glow(ARC_DIM))
    paint(img, 'eye', eyes)
    return img


def species():
    folder = ASSETS + 'textures/entity/dragon/eurus/'
    os.makedirs(folder, exist_ok=True)
    for i in range(len(HIDES)):
        hide_texture(i).save(folder + 'hide_%d.png' % i)
    for i in range(len(MARKS)):
        marks_texture(i).save(folder + 'marks_%d.png' % i)


if __name__ == '__main__':
    for d in (ITEM, ASSETS + 'items/', ASSETS + 'models/item/', DATA + 'jaymoskingdoms/loot_table/entities/'):
        os.makedirs(d, exist_ok=True)
    # (0.7.327) Eurus is drawn as eanncrod's Kintsugi wyvern now: tools/gen_kintsugi_dragons.py paints his hides
    # species()
    egg('eurus_dragon_spawn_egg', rgb(0x4A5068), rgb(0xA8F4FF), 123)
    write(ASSETS + 'items/eurus_dragon_spawn_egg.json', {'model': {'type': 'minecraft:model', 'model': 'jaymoskingdoms:item/eurus_dragon_spawn_egg'}})
    write(ASSETS + 'models/item/eurus_dragon_spawn_egg.json',
          {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jaymoskingdoms:item/eurus_dragon_spawn_egg'}})
    write(DATA + 'jaymoskingdoms/loot_table/entities/eurus_dragon.json',
          {'type': 'minecraft:entity', 'pools': [{'rolls': 1, 'entries': [e]} for e in (
              entry('minecraft:leather', 2, 4), entry('minecraft:bone', 2, 4), entry('minecraft:feather', 3, 8),
              entry('minecraft:copper_ingot', 1, 3), entry('minecraft:redstone', 2, 6))],
           'random_sequence': 'jaymoskingdoms:entities/eurus_dragon'})
    for name, (u, v) in sorted(UV.items(), key=lambda kv: (kv[1][1], kv[1][0])):
        print('%-14s texOffs(%d, %d)  %s' % (name, u, v, DIMS[name]))
