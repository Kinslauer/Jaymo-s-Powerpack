"""Zephyrus' art and data (kingdoms/src/main/java/com/jaymoskingdoms/dragons/ZephyrusDragon), all our own design:
- four hides (swamp green, murky teal, mud brown-green, mottled olive; a pale yellow-green belly, soft blotches, a
  wet sheen) picked per dragon by its DragonLook, and four glowing marking patterns (spots, stripes, a lateral line,
  rings; toxic yellow-green, with the eyes in every one) for the client's ZephyrusDragonModel;
- the spawn egg's icon and item model, and the loot table.
The UV table (PARTS, packed into 256x256 by pack()) is the one ZephyrusDragonModel's texOffs come from: change a size
here and the printed table tells you the texOffs to put there. It uses gen_dragons.py's helpers (not its UV table).
Run from kingdoms/: python3 tools/gen_zephyrus.py"""
import os, random, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image
import gen_dragons as g
from gen_dragons import rgb, jitter, mix, shade, write, entry, egg, ASSETS, DATA, ITEM, SIZE

# Every textured box of the body plan: name -> (w, h, d). Mirrored left/right parts share one entry.
PARTS = [
    ('body', 22, 10, 34),
    ('wing_skin', 18, 0, 26),
    ('tip_skin', 20, 0, 22),
    ('neck0', 12, 8, 6), ('neck1', 11, 8, 6), ('neck2', 10, 7, 6),
    ('skull', 16, 6, 11), ('snout', 10, 4, 6), ('jaw', 8, 2, 6), ('eye', 3, 2, 3),
    ('frill', 1, 5, 6),
    ('tail0', 6, 10, 9), ('tail1', 5, 9, 9), ('tail2', 4, 8, 9), ('tail3', 3, 7, 9), ('paddle', 2, 12, 10),
    ('tailfin', 1, 5, 9), ('dorsal', 1, 7, 6),
    ('wing_bone', 18, 3, 3), ('tip_bone', 20, 2, 2),
    ('front_upper', 5, 5, 5), ('front_lower', 4, 3, 4), ('back_upper', 6, 5, 6), ('back_lower', 5, 3, 5),
    ('front_foot', 8, 2, 7), ('back_foot', 9, 2, 9),
    ('horn', 2, 2, 6), ('horn_tip', 2, 2, 5), ('horn_short', 2, 2, 4), ('horn_short_tip', 2, 2, 3), ('brow_horn', 2, 2, 4),
    ('saddle', 16, 2, 14), ('pommel', 4, 3, 2), ('cantle', 12, 3, 2), ('strap', 25, 13, 3), ('pad', 14, 2, 10),
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
    (0x4C7A3A, 0x2E4A24, 0xD6DE8A, 0x3E6A44),  # swamp green
    (0x3F7068, 0x234640, 0xCFDB94, 0x2E5E5C),  # murky teal
    (0x6B6B3A, 0x433F22, 0xD2CF8C, 0x56552E),  # mud brown-green
    (0x7C8238, 0x4A4F22, 0xDBE29A, 0x5F6A30),  # mottled olive (the blotchiest)
]
BLOTCH = [0.45, 0.5, 0.5, 0.8]
HORN, HORN_TIP, CLAW = rgb(0x2A2A1E), rgb(0x9A9068), rgb(0x1E1E14)
EYE, PUPIL, NOSTRIL, MOUTH = rgb(0xE8D23A), rgb(0x10140A), rgb(0x14180C), rgb(0x40301C)
LEATHER, LEATHER_DARK, GOLD = rgb(0x6B3F22), rgb(0x4A2A16), rgb(0xE8B830)
TOXIC, TOXIC_HOT, TOXIC_DIM = rgb(0xB8F020), rgb(0xE6FF5A), rgb(0x78C41A)
RAY = rgb(0x2A3A1C)

BELLIES = {'body', 'neck0', 'neck1', 'neck2', 'jaw', 'tail0', 'tail1', 'tail2', 'tail3', 'paddle'}
HORNY = {'horn', 'horn_tip', 'horn_short', 'horn_short_tip', 'brow_horn'}
FEET = {'front_foot', 'back_foot'}
LEGS = {'front_upper', 'front_lower', 'back_upper', 'back_lower'}


def noise_field(seed, cells=26, scale=9.0):
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


def hide_texture(variant):
    main, dark, belly, membrane = (rgb(c) for c in HIDES[variant])
    rnd = random.Random(3000 + variant)
    blotches = noise_field(40 + variant)
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))

    def skin(name, face, x, y, fw, fh, base, belly_k=0.0):
        u, v = UV[name]
        n = blotches(u + x * 1.3 + ['top','bottom','east','north','west','south'].index(face) * 5, v + y * 1.3)
        c = base
        if n > 0.55:
            c = mix(base, dark, min(1.0, (n - 0.55) * 2.6) * BLOTCH[variant])
        elif n < 0.3:
            c = mix(base, rgb(0xFFFFFF), (0.3 - n) * 0.35)
        r = rnd.random()
        if r < 0.04:
            c = mix(c, dark, 0.35)           # a wart
        elif r < 0.06:
            c = mix(c, rgb(0xFFFFE0), 0.2)   # wet sheen
        if face == 'top' and (x + y) % 5 == 0 and rnd.random() < 0.5:
            c = mix(c, rgb(0xFFFFE0), 0.18)
        return shade(jitter(c, 4, rnd), face, x, y, fw, fh)

    for name, w, h, d in PARTS:
        if name in ('wing_skin', 'tip_skin'):
            tip = name == 'tip_skin'
            finger = 6
            torn = random.Random(77 + variant * 5 + tip)
            # a ragged, webbed edge: each column's reach (the same for the top and the bottom), a few holes
            depth = d
            reach = []
            for along in range(w):
                t = (along % finger) / finger
                scallop = int(7 * (1 - abs(2 * t - 1)) ** 1.3)
                r = depth - 1 - scallop - torn.randint(0, 4)
                if tip:
                    r = min(r, depth - 1 - int(along * 0.5))
                if along % finger == 0:
                    r = depth - 2 - torn.randint(0, 2)   # the finger bones reach furthest
                reach.append(max(3, r))
            holes = {(torn.randint(2, w - 3), torn.randint(6, max(7, depth - 7))) for _ in range(3)}

            def web(face, x, y, fw, fh, reach=reach, holes=holes):
                if face not in ('top', 'bottom'):
                    return None
                along = x if face == 'top' else fw - 1 - x
                if y > reach[along] or any(abs(along - hx) <= 1 and abs(y - hy) <= 1 for hx, hy in holes):
                    return None
                c = membrane
                if along % finger == 0:
                    c = mix(membrane, RAY, 0.65)
                elif y >= reach[along] - 1:
                    c = mix(membrane, dark, 0.4)
                if y < 2:
                    c = mix(c, main, 0.35)
                return shade(jitter(c, 5, rnd), 'north' if face == 'top' else 'east', x, y, fw, fh)

            paint(img, name, web)
        elif name in HORNY:
            def horn(face, x, y, fw, fh, name=name):
                # dark at the root, pale at the tip
                tip = name.endswith('tip')
                t = (0.6 if tip else 0.0) + (x / max(1, fw - 1) if face in ('top', 'bottom') else 0.2) * 0.4
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
                    c = rgb(0x7A1E1A)
                return shade(jitter(c, 5, rnd), face, x, y, fw, fh)

            paint(img, name, tack)
        elif name in ('frill', 'dorsal', 'tailfin'):
            def fin(face, x, y, fw, fh, name=name):
                if face not in ('east', 'west'):
                    # the thin edges are the rim
                    return shade(jitter(mix(membrane, dark, 0.3), 4, rnd), face, x, y, fw, fh)
                if name == 'frill':
                    # rays run back from the cheek (the back is x=0 on the east side); alternate rays are longer
                    back = x if face == 'east' else fw - 1 - x
                    cut = 0 if y % 2 == 0 else 2
                    if back < cut:
                        return None
                    c = mix(membrane, RAY, 0.55) if y % 2 == 0 else mix(membrane, rgb(0xE0E890), 0.25)
                else:
                    # rays stand up from the back; alternate rays are taller
                    col = x if face == 'east' else fw - 1 - x
                    cut = 0 if col % 2 == 0 else 2
                    if y < cut:
                        return None
                    c = mix(membrane, RAY, 0.55) if col % 2 == 0 else mix(membrane, rgb(0xE0E890), 0.2)
                    if y >= fh - 1:
                        c = main
                return shade(jitter(c, 4, rnd), face, x, y, fw, fh)

            paint(img, name, fin)
        elif name == 'eye':
            def eye(face, x, y, fw, fh):
                if face == 'bottom':
                    return shade(main, face, x, y, fw, fh)
                if face == 'top':
                    return shade(jitter(mix(main, EYE, 0.45), 4, rnd), face, x, y, fw, fh)
                return PUPIL if x == fw // 2 else jitter(EYE, 6, rnd)

            paint(img, name, eye)
        else:
            def body(face, x, y, fw, fh, name=name):
                if name in FEET and face == 'bottom':
                    return shade(jitter(mix(main, dark, 0.35), 4, rnd), face, x, y, fw, fh)
                if name in FEET and face == 'north':
                    # webbed toes: three claws with skin between
                    return jitter(CLAW, 4, rnd) if x % 3 == 0 else jitter(mix(membrane, dark, 0.3), 4, rnd)
                if name in LEGS and face == 'bottom':
                    return shade(jitter(dark, 4, rnd), face, x, y, fw, fh)
                if name == 'jaw' and face == 'top':
                    # the inside of the mouth, a row of small teeth round the edge
                    return rgb(0xE0D8B8) if x in (0, fw - 1) or y == 0 else jitter(MOUTH, 6, rnd)
                belly_face = face == 'bottom' and name in BELLIES
                if belly_face:
                    return shade(jitter(mix(belly, dark, 0.3) if y % 3 == 2 else belly, 5, rnd), face, x, y, fw, fh)
                if name in BELLIES and face in ('east', 'west', 'north', 'south') and fh >= 5 and y >= fh - 2:
                    return shade(jitter(mix(belly, main, 0.35), 5, rnd), face, x, y, fw, fh)
                if name == 'snout' and face == 'top' and y == 1 and x in (2, fw - 3):
                    return NOSTRIL
                if name == 'snout' and face == 'north':
                    if y == fh - 1:
                        return rgb(0xE0D8B8) if x % 2 == 0 else jitter(MOUTH, 4, rnd)
                if name == 'snout' and face in ('east', 'west') and y == fh - 1:
                    return rgb(0xE0D8B8) if x % 2 == 0 else jitter(MOUTH, 4, rnd)
                if name == 'skull' and face in ('east', 'west') and y == fh - 1:
                    return jitter(MOUTH, 4, rnd)
                if name == 'paddle' and face in ('east', 'west') and x % 3 == 1:
                    return shade(jitter(mix(main, RAY, 0.45), 4, rnd), face, x, y, fw, fh)
                base = main
                if face == 'top' and name in ('body', 'neck0', 'neck1', 'neck2') and abs(x - fw // 2) <= 1:
                    base = mix(main, dark, 0.3)
                return skin(name, face, x, y, fw, fh, base)

            paint(img, name, body)
    return img


MARKS = ['spots', 'stripes', 'lateral', 'rings']
SPOT_PARTS = ['body', 'neck0', 'neck1', 'skull', 'tail0', 'tail1', 'tail2', 'paddle', 'front_upper', 'back_upper']


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
    rnd = random.Random(5000 + pattern)
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    kind = MARKS[pattern]

    def glow(c):
        return jitter(c, 10, rnd)

    def eyes(face, x, y, fw, fh):
        # a glowing ring round a dark slit
        if face == 'top' or face == 'bottom':
            return None
        return None if x == fw // 2 else glow(TOXIC_HOT)

    paint(img, 'eye', eyes)

    for name in [n for n, *_ in PARTS]:
        if kind == 'spots' and name in SPOT_PARTS:
            for face, rect in faces(name).items():
                if face in ('top', 'east', 'west') and rect[2] > 3 and rect[3] > 3:
                    for _ in range(max(2, rect[2] * rect[3] // 55)):
                        disc(img, rect, rnd.randint(1, rect[2] - 2), rnd.randint(1, rect[3] - 2), rnd.choice((0, 1, 1, 2)),
                             glow(rnd.choice((TOXIC, TOXIC, TOXIC_DIM, TOXIC_HOT))))
        elif kind == 'stripes' and name in ('body', 'tail0', 'tail1', 'tail2', 'neck0', 'neck1', 'neck2', 'skull'):
            def st(f, x, y, fw, fh, n=name):
                if f == 'top':
                    return glow(TOXIC) if y % 4 in (1, 2) and 1 <= x <= fw - 2 else None
                if f in ('east', 'west') and n != 'skull':
                    return glow(TOXIC_DIM) if x % 5 in (2, 3) and 1 <= y <= fh - 4 else None
                return None
            paint(img, name, st)
        elif kind == 'lateral' and name in ('body', 'neck0', 'neck1', 'neck2', 'tail0', 'tail1', 'tail2', 'tail3', 'skull', 'snout', 'paddle'):
            def lat(f, x, y, fw, fh, n=name):
                if f in ('east', 'west'):
                    return glow(TOXIC) if y == max(1, fh // 2 - 1) and x % 2 == 0 else None
                return None
            paint(img, name, lat)
        elif kind == 'rings' and name in SPOT_PARTS:
            for face, rect in faces(name).items():
                if face in ('top', 'east', 'west') and rect[2] > 6 and rect[3] > 6:
                    for _ in range(max(1, rect[2] * rect[3] // 140)):
                        disc(img, rect, rnd.randint(2, rect[2] - 3), rnd.randint(2, rect[3] - 3), 2, glow(TOXIC), ring=True)
        if kind in ('lateral', 'rings') and name in ('frill', 'dorsal', 'tailfin'):
            def fr(f, x, y, fw, fh, n=name):
                if f not in ('east', 'west'):
                    return None
                if n == 'frill':
                    back = x if f == 'east' else fw - 1 - x
                    return glow(TOXIC) if back <= 1 and y % 2 == 0 else None
                return glow(TOXIC) if y <= 1 and (x % 2 == 0) else None
            paint(img, name, fr)
    paint(img, 'eye', eyes)
    return img


def species():
    folder = ASSETS + 'textures/entity/dragon/zephyrus/'
    os.makedirs(folder, exist_ok=True)
    for i in range(len(HIDES)):
        hide_texture(i).save(folder + 'hide_%d.png' % i)
    for i in range(len(MARKS)):
        marks_texture(i).save(folder + 'marks_%d.png' % i)


if __name__ == '__main__':
    for d in (ITEM, ASSETS + 'items/', ASSETS + 'models/item/', DATA + 'jaymoskingdoms/loot_table/entities/'):
        os.makedirs(d, exist_ok=True)
    species()
    egg('zephyrus_dragon_spawn_egg', rgb(0x4C7A3A), rgb(0xB8F020), 99)
    write(ASSETS + 'items/zephyrus_dragon_spawn_egg.json', {'model': {'type': 'minecraft:model', 'model': 'jaymoskingdoms:item/zephyrus_dragon_spawn_egg'}})
    write(ASSETS + 'models/item/zephyrus_dragon_spawn_egg.json',
          {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jaymoskingdoms:item/zephyrus_dragon_spawn_egg'}})
    write(DATA + 'jaymoskingdoms/loot_table/entities/zephyrus_dragon.json',
          {'type': 'minecraft:entity', 'pools': [{'rolls': 1, 'entries': [e]} for e in (
              entry('minecraft:leather', 2, 5), entry('minecraft:bone', 2, 5), entry('minecraft:slime_ball', 3, 8),
              entry('minecraft:spider_eye', 1, 3), entry('minecraft:fermented_spider_eye', 0, 2), entry('minecraft:lily_pad', 2, 6))],
           'random_sequence': 'jaymoskingdoms:entities/zephyrus_dragon'})
    for name, (u, v) in sorted(UV.items(), key=lambda kv: (kv[1][1], kv[1][0])):
        print('%-14s texOffs(%d, %d)  %s' % (name, u, v, DIMS[name]))
