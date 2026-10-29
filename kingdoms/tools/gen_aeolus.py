"""Aeolus' art (kingdoms/src/main/java/com/jaymoskingdoms/dragons/AeolusDragon.java), our own design: the father of the
winds, a huge ancient dragon with a grey-white hide worn with age, and the four winds' colours (Notus' ember, Boreas'
ice, Zephyrus' acid green, Eurus' storm violet) in his glowing markings. Paints hide_0..3 and marks_0..3 for the client's
AeolusDragonModel (whose texOffs are the UV table printed at the end), his spawn egg's icon and item model, and his loot
table. It borrows gen_dragons.py's painter (patching its parts table and palette) and never touches villager.png (the
disguise's skin).
Run from kingdoms/: python3 tools/gen_aeolus.py"""
import os, random, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image
import gen_dragons as g
from gen_dragons import rgb, jitter, mix, shade, write, entry, paint, ASSETS, DATA, ITEM

# Every textured box of Aeolus' body plan: name -> (w, h, d), in the model's pixels (it is drawn 2.2x as big)
PARTS = [
    ('body', 20, 18, 38), ('hump', 14, 6, 14),
    ('wing_skin', 30, 0, 26), ('tip_skin', 38, 0, 30), ('wing_bone', 30, 4, 4), ('tip_bone', 38, 3, 3),
    ('neck0', 12, 12, 9), ('neck1', 11, 11, 9), ('neck2', 10, 10, 9), ('neck3', 10, 9, 9),
    ('skull', 14, 11, 12), ('snout', 10, 6, 10), ('jaw', 9, 2, 10), ('beard', 3, 6, 3),
    ('tail0', 12, 11, 10), ('tail1', 11, 10, 10), ('tail2', 10, 9, 10), ('tail3', 9, 8, 10), ('tail4', 8, 7, 10),
    ('tail5', 7, 6, 10), ('tail6', 6, 5, 10), ('spade', 16, 1, 16), ('tail_blade', 3, 2, 8),
    ('front_upper', 8, 11, 8), ('front_lower', 7, 7, 7), ('foot', 9, 2, 11), ('back_upper', 9, 12, 10), ('back_lower', 7, 9, 8),
    ('spike', 3, 7, 3), ('small_spike', 2, 3, 2),
    ('horn', 3, 3, 14), ('horn_short', 3, 3, 8), ('brow_horn', 2, 2, 7), ('crown_horn', 2, 2, 9), ('cheek_horn', 2, 2, 6),
    ('mane_a', 3, 8, 3), ('mane_b', 2, 5, 2),
]
BELLIES = {'body', 'neck0', 'neck1', 'neck2', 'neck3', 'jaw', 'tail0', 'tail1', 'tail2', 'tail3', 'tail4', 'tail5', 'tail6'}
HORNY = {'spike', 'small_spike', 'horn', 'horn_short', 'brow_horn', 'crown_horn', 'cheek_horn', 'tail_blade'}

# hide shades (main, dark, belly, membrane): worn stone grey, bone white, cold slate, and near-white (the oldest)
HIDES = [
    (0xB6BABD, 0x7A8286, 0xE6E2D6, 0x8C949A),
    (0xC8C5BC, 0x8A857A, 0xEFE9D8, 0x9C978B),
    (0xA2A9AE, 0x687077, 0xD6D9D8, 0x7A838A),
    (0xD2D4D6, 0x929A9E, 0xF2F0E8, 0xA8AEB3),
]
# the four winds, in the order Aeolus breathes them: Notus, Boreas, Zephyrus, Eurus
WIND = [rgb(0xFF8A1A), rgb(0x7FD8FF), rgb(0x8CE02A), rgb(0xB07CFF)]
WIND_HOT = [rgb(0xFFD25A), rgb(0xD8F6FF), rgb(0xD6FF7A), rgb(0xE2D0FF)]
IVORY, IVORY_DARK = rgb(0xE8E4D4), rgb(0x4A4A52)


def setup(size):
    g.SIZE = size
    g.PARTS = PARTS
    g.UV = g.pack()
    g.DIMS = {n: (w, h, d) for n, w, h, d in PARTS}
    g.NOTUS_HIDES = HIDES
    g.BELLIES = BELLIES
    g.HORNY = HORNY
    g.HORN, g.HORN_TIP, g.EYE, g.PUPIL = IVORY_DARK, IVORY, rgb(0xEAF6FF), rgb(0x0A1018)


def pick_size():
    for size in (256, 512):
        try:
            setup(size)
            return size
        except AssertionError:
            continue
    raise SystemExit('parts do not fit')


SIZE = pick_size()


def weather(img, variant):
    """Age: pale worn patches and dark scratches over the plain hide, and a ragged wispy mane."""
    rnd = random.Random(3000 + variant)
    px = img.load()
    main, dark = rgb(HIDES[variant][0]), rgb(HIDES[variant][1])
    for name in BELLIES | {'hump', 'front_upper', 'back_upper'}:
        for face, (fx, fy, fw, fh) in g.faces(name).items():
            if face == 'bottom':
                continue
            for _ in range(max(2, fw * fh // 40)):
                x, y = rnd.randrange(fw), rnd.randrange(fh)
                length = rnd.randint(2, 5)
                scar = rnd.random() < 0.6
                for i in range(length):
                    xx, yy = x + i, y + (i // 2)
                    if xx < fw and yy < fh:
                        c = px[fx + xx, fy + yy]
                        px[fx + xx, fy + yy] = mix(c, dark, 0.55) if scar else mix(c, rgb(0xF4F2EA), 0.35)
    for name in ('mane_a', 'mane_b', 'beard'):
        def wisp(face, x, y, fw, fh):
            t = y / max(1, fh - 1)
            base = mix(rgb(0xDDDDD8), rgb(0x8F969B), 0.35 + 0.5 * ((x + y) % 3) / 2.0)
            c = mix(base, rgb(0xF6F6F2), 0.3 * (1.0 - t))
            return shade(jitter(c, 8, rnd), face, x, y, fw, fh)
        paint(img, name, wisp)
    for name in ('crown_horn', 'cheek_horn'):
        def horn(face, x, y, fw, fh, name=name):
            t = x / max(1, fw - 1) if face in ('top', 'bottom', 'north', 'south') else (1 - x / max(1, fw - 1) if face == 'east' else x / max(1, fw - 1))
            return shade(jitter(mix(IVORY_DARK, IVORY, 0.15 + t * 0.75), 5, rnd), face, x, y, fw, fh)
        paint(img, name, horn)


def hide_texture(variant):
    img = g.hide_texture(variant)
    weather(img, variant)
    return img


MARKS = ['bands', 'wings', 'runes', 'crown']


def marks_texture(pattern):
    rnd = random.Random(4000 + pattern)
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    kind = MARKS[pattern]

    def glow(i, hot=False):
        return jitter((WIND_HOT if hot else WIND)[i % 4], 10, rnd)

    def eyes(face, x, y, fw, fh):
        if face in ('east', 'west'):
            ex = fw - 3 if face == 'east' else 2
            if y == 3 and abs(x - ex) <= 1:
                return rgb(0xFFFFFF) if x == ex else rgb(0xBFE6FF)
        return None

    if kind == 'bands':
        # a band of each wind in turn down the flank; one wind to each segment of the neck and tail
        def band(face, x, y, fw, fh):
            if face in ('east', 'west') and 3 <= y <= fh - 5 and x % 8 < 5:
                return glow(x // 8)
            if face == 'top' and abs(x - fw // 2) <= 1 and (y % 6) < 4:
                return glow(y // 6, True)
            return None
        paint(img, 'body', band)
        for i in range(4):
            paint(img, 'neck%d' % i, lambda f, x, y, fw, fh, i=i: glow(i) if f in ('east', 'west') and 2 <= y <= fh - 3 and x % 3 != 1 else None)
        for i in range(7):
            paint(img, 'tail%d' % i, lambda f, x, y, fw, fh, i=i: glow(i, True) if f == 'top' and abs(x - fw // 2) <= 1 else
                  (glow(i) if f in ('east', 'west') and fh // 2 - 1 <= y <= fh // 2 and x % 4 != 0 else None))
    elif kind == 'wings':
        for n in ('wing_bone', 'tip_bone'):
            paint(img, n, lambda f, x, y, fw, fh: glow(x * 4 // fw) if f in ('top', 'north', 'south') and y % 2 == 0 else None)

        def fingers(face, x, y, fw, fh):
            if face not in ('top', 'bottom'):
                return None
            finger = 10 if fw == 38 else 13
            along = x if face == 'top' else fw - 1 - x
            return glow(along // finger) if along % finger == 1 and y < fh - 7 and y % 2 == 0 else None
        for n in ('wing_skin', 'tip_skin'):
            paint(img, n, fingers)
        paint(img, 'hump', lambda f, x, y, fw, fh: glow(x // 3 + y // 3) if f in ('top', 'north', 'south') and (x + y) % 5 == 0 else None)
    elif kind == 'runes':
        # four-armed glyphs, 4x4 cells over his flanks and back, each in a wind's colour
        def runes(face, x, y, fw, fh):
            if face == 'bottom':
                return None
            cx, cy = x // 5, y // 5
            lx, ly = x % 5, y % 5
            if (cx * 7 + cy * 3) % 3 != 0:
                return None
            cross = (lx == 2 and 0 <= ly <= 4) or (ly == 2 and 0 <= lx <= 4)
            return glow(cx + cy) if cross and (lx, ly) != (2, 2) else (glow(cx + cy, True) if (lx, ly) == (2, 2) else None)
        for n in ('body', 'hump', 'front_upper', 'back_upper', 'neck0', 'neck1'):
            paint(img, n, runes)
    else:
        # the crown: mane, beard, horns and the spade of his tail glow in turn
        for n in ('mane_a', 'mane_b', 'beard'):
            paint(img, n, lambda f, x, y, fw, fh: glow(x + y + (f == 'north'), y > fh // 2) if (x + y) % 2 == 0 else None)
        for n in ('crown_horn', 'cheek_horn', 'brow_horn'):
            paint(img, n, lambda f, x, y, fw, fh: glow(x * 4 // max(1, fw), True) if x >= fw // 2 else None)
        paint(img, 'spade', lambda f, x, y, fw, fh: glow((x >= fw // 2) * 2 + (y >= fh // 2)) if f in ('top', 'bottom') and
              (x in (0, fw - 1) or y in (0, fh - 1) or abs(x - fw // 2) <= 0 or abs(y - fh // 2) <= 0) else None)
        for i in range(4):
            paint(img, 'neck%d' % i, lambda f, x, y, fw, fh, i=i: glow(i) if f == 'bottom' and abs(x - fw // 2) <= 1 else None)
        paint(img, 'jaw', lambda f, x, y, fw, fh: glow(x * 4 // fw, True) if f == 'bottom' and y % 3 == 0 else None)
    paint(img, 'skull', eyes)
    return img


def spawn_egg(name):
    """A grey-white egg spotted in the four winds' colours."""
    rnd = random.Random(88)
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    base = rgb(0xC4C6C4)
    for y in range(16):
        for x in range(16):
            dx, dy = (x - 7.5) / 5.0, (y - 8.5) / 6.5
            if dx * dx + dy * dy * (1.0 + 0.25 * (8.5 - y) / 6.5) <= 1.0:
                c = WIND[(x // 3 + y // 3) % 4] if rnd.random() < 0.22 else base
                if x <= 5 and y <= 6:
                    c = jitter(c, 26, rnd)
                px[x, y] = jitter(c, 6, rnd)
    img.save(ITEM + name + '.png')


def main():
    for d in (ITEM, ASSETS + 'items/', ASSETS + 'models/item/', DATA + 'jaymoskingdoms/loot_table/entities/'):
        os.makedirs(d, exist_ok=True)
    folder = ASSETS + 'textures/entity/dragon/aeolus/'
    os.makedirs(folder, exist_ok=True)
    # (villager.png in this folder is the disguise's skin, from Andy's friend: never written here)
    # (0.7.329) Aeolus is drawn as eanncrod's Kintsugi wyvern now: tools/gen_kintsugi_dragons.py paints his hides
    # for i in range(len(HIDES)):
    #     hide_texture(i).save(folder + 'hide_%d.png' % i)
    # for i in range(len(MARKS)):
    #     marks_texture(i).save(folder + 'marks_%d.png' % i)
    spawn_egg('aeolus_dragon_spawn_egg')
    write(ASSETS + 'items/aeolus_dragon_spawn_egg.json', {'model': {'type': 'minecraft:model', 'model': 'jaymoskingdoms:item/aeolus_dragon_spawn_egg'}})
    write(ASSETS + 'models/item/aeolus_dragon_spawn_egg.json',
          {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jaymoskingdoms:item/aeolus_dragon_spawn_egg'}})
    write(DATA + 'jaymoskingdoms/loot_table/entities/aeolus_dragon.json',
          {'type': 'minecraft:entity', 'pools': [{'rolls': 1, 'entries': [e]} for e in (
              entry('minecraft:leather', 8, 16), entry('minecraft:bone', 6, 12), entry('minecraft:breeze_rod', 6, 12),
              entry('minecraft:wind_charge', 12, 24), entry('minecraft:diamond', 2, 5))],
           'random_sequence': 'jaymoskingdoms:entities/aeolus_dragon'})
    print('texture size', SIZE)
    for name, (u, v) in sorted(g.UV.items(), key=lambda kv: (kv[1][1], kv[1][0])):
        print('%-12s texOffs(%d, %d)  %s' % (name, u, v, g.DIMS[name]))


if __name__ == '__main__':
    main()
