"""The Nightfall Plague's art and data (kingdoms/src/main/java/com/jaymoskingdoms/nightfall), all our own (the Forge mod
it's after is All Rights Reserved: nothing of it is used):
- the Night Bleeder's three looks (vanilla's illager model layout, 64x64), its Blightwing's three (vanilla's bat model,
  32x32, drawn on a far bigger bat) and the vampire bat;
- the items' icons (cursed tusk, cursed card, lute, dried meat, two spawn eggs) and the two effects' icons;
- the item models, loot tables, entity tags and brewing recipes.
Run from kingdoms/: python3 tools/gen_nightfall.py"""
import json, os, random
from PIL import Image

RES = 'src/main/resources/'
ASSETS = RES + 'assets/jaymoskingdoms/'
DATA = RES + 'data/'
ENT = ASSETS + 'textures/entity/nightfall/'
ITEM = ASSETS + 'textures/item/'
EFFECT = ASSETS + 'textures/mob_effect/'
for d in (ENT, ITEM, EFFECT, ASSETS + 'items/', ASSETS + 'models/item/', DATA + 'jaymoskingdoms/loot_table/entities/',
          DATA + 'jaymoskingdoms/recipe/brewing/', DATA + 'minecraft/tags/entity_type/'):
    os.makedirs(d, exist_ok=True)


def rgb(h, a=255):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255, a)


def jitter(c, n, rnd):
    k = rnd.randint(-n, n)
    return tuple(max(0, min(255, ch + k)) for ch in c[:3]) + (c[3] if len(c) > 3 else 255,)


def box(img, u, v, w, h, d, paint):
    """Calls paint(face, x, y, fw, fh) for each pixel of each face of a box's UV layout."""
    faces = {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'east': (u, v + d, d, h), 'north': (u + d, v + d, w, h),
             'west': (u + d + w, v + d, d, h), 'south': (u + d + w + d, v + d, w, h)}
    px = img.load()
    for face, (fx, fy, fw, fh) in faces.items():
        for y in range(fh):
            for x in range(fw):
                c = paint(face, x, y, fw, fh)
                if c is not None:
                    px[fx + x, fy + y] = c


# ---- the Night Bleeder ---------------------------------------------------------------------------------------------------

# (skin, hair, cloak, trim) for each look: the pale noble, the grave-grey, the ashen one in green
BLEEDERS = [(0xB9BEC9, 0x1B1418, 0x241016, 0x8E1A24), (0xA7A1AE, 0x3A3540, 0x1E1A2A, 0x5B2A6E), (0xC4B6AE, 0x5A2A1E, 0x1B2219, 0x7A6A2C)]
EYE, EYE_HOT, TUSK, MOUTH = rgb(0xC0141E), rgb(0xFF4A4A), rgb(0xEDE6D2), rgb(0x2A0A10)


def bleeder(i):
    skin, hair, cloak, trim = (rgb(c) for c in BLEEDERS[i])
    rnd = random.Random(100 + i)
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))

    def head(face, x, y, fw, fh):
        if face == 'top':
            return jitter(hair, 6, rnd)
        if face == 'bottom':
            return jitter(skin, 5, rnd)
        if y < 2 or face == 'south' and y < 6 or face in ('east', 'west') and y < 3 and x > 2:
            return jitter(hair, 6, rnd)
        c = jitter(skin, 6, rnd)
        if face == 'north':
            if y == 3 and x in (1, 2, 5, 6):
                return jitter(hair, 4, rnd)  # brows
            if y == 4 and x in (1, 2, 5, 6):
                return EYE_HOT if x in (2, 5) else EYE
            if y == 5 and x in (1, 6):
                return jitter(rgb(0x6A5A66), 4, rnd)  # hollows
            if y == 8 and 2 <= x <= 5:
                return MOUTH
            if x in (2, 5) and y in (8, 9):
                return TUSK
        return c
    box(img, 0, 0, 8, 10, 8, head)
    box(img, 24, 0, 2, 4, 2, lambda f, x, y, fw, fh: jitter(skin, 5, rnd))

    def coat(face, x, y, fw, fh):
        c = jitter(cloak, 5, rnd)
        if face == 'north':
            if x in (3, 4):
                return jitter(trim, 8, rnd)
            if y < 2:
                return jitter(trim, 8, rnd)  # the high collar
            if x in (2, 5) and y % 3 == 1:
                return rgb(0xB89A4A)  # buttons
        if face in ('east', 'west', 'south') and y < 2:
            return jitter(trim, 8, rnd)
        return c
    box(img, 16, 20, 8, 12, 6, coat)

    def robe(face, x, y, fw, fh):
        if face in ('top', 'bottom'):
            return jitter(cloak, 5, rnd)
        if y >= fh - 3 and rnd.random() < 0.18 * (y - fh + 4):
            return (0, 0, 0, 0)  # ragged hem
        if face == 'north' and x in (3, 4):
            return jitter(trim, 8, rnd)
        if y == fh - 4 or y == fh - 5 and face != 'north':
            return jitter(trim, 6, rnd)
        return jitter(cloak, 5, rnd)
    box(img, 0, 38, 8, 20, 6, robe)

    def sleeve(face, x, y, fw, fh):
        return jitter(trim, 6, rnd) if y == fh - 1 else jitter(cloak, 5, rnd)
    box(img, 44, 22, 4, 8, 4, sleeve)

    def crossed(face, x, y, fw, fh):
        # the crossed arms' middle: hands with long pale claws
        if face == 'north' and (x < 2 or x > fw - 3):
            return rgb(0xDDD6C8) if y == fh - 1 else jitter(skin, 5, rnd)
        return jitter(cloak, 5, rnd)
    box(img, 40, 38, 8, 4, 4, crossed)

    def leg(face, x, y, fw, fh):
        if y >= fh - 3:
            return jitter(rgb(0x1A1214), 4, rnd)  # boots
        return jitter(rgb(0x2C2428), 5, rnd)
    box(img, 0, 22, 4, 12, 4, leg)

    def arm(face, x, y, fw, fh):
        if face == 'bottom' or y >= fh - 3:
            return rgb(0xDDD6C8) if face in ('north', 'bottom') and y >= fh - 1 else jitter(skin, 5, rnd)
        return jitter(trim, 6, rnd) if y == fh - 4 else jitter(cloak, 5, rnd)
    box(img, 40, 46, 4, 12, 4, arm)
    img.save(ENT + 'night_bleeder_%d.png' % i)


# ---- the bats ------------------------------------------------------------------------------------------------------------

def bat(name, fur, membrane, vein, rnd, cloak=None, bald=False):
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))

    def body(face, x, y, fw, fh):
        if cloak is not None and y >= 2 and (x + y) % 5 != 0:
            return jitter(cloak, 6, rnd)  # the rags of its cloak
        return jitter(fur, 7 if not bald else 4, rnd)
    box(img, 0, 0, 3, 5, 2, body)

    def head(face, x, y, fw, fh):
        if face == 'north':
            if y == 1 and x in (0, 3):
                return EYE_HOT
            if y == 2 and x in (1, 2):
                return TUSK
        return jitter(fur, 6, rnd)
    box(img, 0, 7, 4, 3, 2, head)
    px = img.load()
    # the ears (flat), the wings and their tips (flat), the feet
    for (u, v, w, h) in ((1, 15, 3, 5), (8, 15, 3, 5)):
        for y in range(h):
            for x in range(w):
                if y > 0 or x == 1:
                    px[u + x, v + y] = px[u + w + x, v + y] = jitter(membrane, 6, rnd)
    for (u, v, w, h, tip) in ((12, 0, 2, 7, False), (16, 0, 6, 8, True), (12, 7, 2, 7, False), (16, 8, 6, 8, True)):
        for y in range(h):
            for x in range(w):
                if tip and y == h - 1 and x % 2 == 1:
                    continue  # scalloped edge
                c = jitter(membrane, 6, rnd)
                if tip and (x == w - 1 - y * w // (h + 2) or y == 1 and x < w - 1):
                    c = jitter(vein, 5, rnd)
                if not tip and x == 0:
                    c = jitter(vein, 5, rnd)
                px[u + x, v + y] = c
        # (the other side of each flat face)
        for y in range(h):
            for x in range(w):
                if u + w + x < 32:
                    px[u + w + x, v + y] = px[u + x, v + y]
    for y in range(2):
        for x in range(3):
            px[16 + x, 16 + y] = rgb(0x1A1012)
    img.save(ENT + name + '.png')


def bats():
    bat('vampire_bat', rgb(0x2A1A1E), rgb(0x3B1E26), rgb(0x7A1020), random.Random(7))
    for i, (skin, hair, cloak, trim) in enumerate(BLEEDERS):
        bat('blightwing_%d' % i, rgb(0x9C8F96), rgb(0x5E5058), rgb(0x8A6070), random.Random(20 + i), cloak=rgb(cloak), bald=True)


# ---- icons ---------------------------------------------------------------------------------------------------------------

def icon(name, size, pixels, folder=ITEM):
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    px = img.load()
    for (x, y), c in pixels.items():
        if 0 <= x < size and 0 <= y < size:
            px[x, y] = c
    img.save(folder + name + '.png')


def tusk():
    p, rnd = {}, random.Random(3)
    # a curved tusk from bottom-left up to top-right, ivory going yellow at the root, a dark red root
    pts = [(3, 13), (4, 12), (5, 12), (5, 11), (6, 10), (7, 10), (7, 9), (8, 8), (9, 7), (9, 6), (10, 5), (11, 4), (11, 3), (12, 2)]
    for i, (x, y) in enumerate(pts):
        t = i / len(pts)
        base = rgb(0x6A1018) if i < 2 else rgb(0xD8CCA0) if t < 0.4 else rgb(0xEFE8D4)
        thick = 2 if i < 9 else 1
        for k in range(thick):
            p[(x + k, y + 1)] = jitter(base, 8, rnd)
        p[(x, y)] = jitter(rgb(0xFFF8E8) if i >= 2 else rgb(0x8A1A22), 5, rnd)
    p[(13, 1)] = rgb(0xFFFFF4)
    icon('cursed_tusk', 16, p)


def card():
    p, rnd = {}, random.Random(4)
    for y in range(2, 15):
        for x in range(4, 12):
            edge = x in (4, 11) or y in (2, 14)
            p[(x, y)] = rgb(0xB89A4A) if edge else jitter(rgb(0x1C1018), 5, rnd)
    # a red bat sigil
    for (x, y) in ((7, 6), (8, 6), (6, 7), (7, 7), (8, 7), (9, 7), (5, 8), (7, 8), (8, 8), (10, 8), (7, 9), (8, 9), (7, 10), (8, 10)):
        p[(x, y)] = rgb(0xC0141E)
    p[(7, 7)] = p[(8, 7)] = rgb(0xFF4A4A)
    icon('cursed_card', 16, p)


def lute():
    p, rnd = {}, random.Random(5)
    # pear body bottom-left, neck to the top-right, pegbox bent back
    for y in range(16):
        for x in range(16):
            dx, dy = x - 5.0, y - 10.5
            if dx * dx / 16.0 + dy * dy / 20.0 <= 1.0:
                p[(x, y)] = jitter(rgb(0x9A5A2A), 8, rnd)
    for (x, y) in ((4, 9), (5, 9), (6, 9), (4, 10), (5, 10), (6, 10), (5, 11)):
        p[(x, y)] = rgb(0x2A160C)  # the rose
    for i in range(7):
        x, y = 7 + i, 7 - i
        p[(x, y)] = jitter(rgb(0x5A3418), 5, rnd)
        p[(x + 1, y + 1)] = jitter(rgb(0x3A220E), 5, rnd)
    p[(14, 0)] = p[(15, 1)] = p[(14, 1)] = rgb(0x3A220E)
    for i in range(5):
        p[(3 + i, 13 - i)] = rgb(0xE8E0C8)  # strings over the body
    icon('lute', 16, p)


def dried_meat():
    p, rnd = {}, random.Random(6)
    for i in range(10):
        x, y = 3 + i, 11 - i // 2
        for k in range(3):
            c = rgb(0x5A1A14) if k == 1 else rgb(0x7A2A1A)
            if (i + k) % 4 == 0:
                c = rgb(0xC8A090)  # fat
            p[(x, y + k)] = jitter(c, 8, rnd)
    p[(2, 12)] = p[(2, 13)] = rgb(0x3A120E)
    icon('dried_meat', 16, p)


def egg(name, base, spots, seed):
    p, rnd = {}, random.Random(seed)
    for y in range(16):
        for x in range(16):
            dx, dy = (x - 7.5) / 5.0, (y - 8.5) / 6.5
            if dx * dx + dy * dy * (1.0 + 0.25 * (8.5 - y) / 6.5) <= 1.0:
                c = spots if rnd.random() < 0.18 else base
                if x <= 5 and y <= 6:
                    c = jitter(c, 30, rnd)
                p[(x, y)] = jitter(c, 6, rnd)
    icon(name, 16, p)


def effects():
    p = {}
    for y in range(18):
        for x in range(18):
            dx, dy = x - 8.5, y - 10.5
            if dx * dx + dy * dy <= 30 and (y >= 6 or abs(dx) <= (y - 1) * 0.6):
                p[(x, y)] = rgb(0x6A1020) if dx < 0 else rgb(0x8A1A28)
    for (x, y) in ((8, 8), (9, 9), (8, 10), (9, 11), (9, 12)):
        p[(x, y)] = rgb(0x1A0408)  # a crack
    p[(6, 9)] = rgb(0xC04050)
    icon('atrophy', 18, p, EFFECT)
    p = {}
    for y in range(2, 16):
        for x in range(3, 15):
            if abs(x - 8.5) <= 5.5 - max(0, y - 10) * 0.9:
                p[(x, y)] = rgb(0xF2D25C) if (x + y) % 7 else rgb(0xFFF0A0)
    for y in range(5, 13):
        p[(8, y)] = p[(9, y)] = rgb(0xFFFFFF)
    for x in range(6, 12):
        p[(x, 7)] = p[(x, 8)] = rgb(0xFFFFFF)
    icon('immunity', 18, p, EFFECT)


# ---- data ----------------------------------------------------------------------------------------------------------------

def write(path, obj):
    with open(path, 'w', newline='\n') as f:
        json.dump(obj, f, indent='\t')
        f.write('\n')


def item_models():
    for name in ('cursed_tusk', 'cursed_card', 'lute', 'dried_meat', 'night_bleeder_spawn_egg', 'vampire_bat_spawn_egg'):
        write(ASSETS + 'items/' + name + '.json', {'model': {'type': 'minecraft:model', 'model': 'jaymoskingdoms:item/' + name}})
        parent = 'minecraft:item/handheld' if name in ('cursed_tusk', 'lute') else 'minecraft:item/generated'
        write(ASSETS + 'models/item/' + name + '.json', {'parent': parent, 'textures': {'layer0': 'jaymoskingdoms:item/' + name}})


def entry(item, lo, hi, looting=True):
    mods = [{'type': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': lo, 'max': hi}}]
    if looting:
        mods.append({'type': 'minecraft:enchanted_count_increase', 'count': {'type': 'minecraft:uniform', 'min': 0.0, 'max': 1.0},
                     'enchantment': 'minecraft:looting'})
    return {'type': 'minecraft:item', 'name': item, 'modifier': mods}


def loot():
    write(DATA + 'jaymoskingdoms/loot_table/entities/night_bleeder.json', {
        'type': 'minecraft:entity',
        'pools': [
            {'rolls': 1, 'entries': [entry('minecraft:emerald', 3, 5)]},
            {'rolls': 1, 'entries': [entry('jaymoskingdoms:cursed_tusk', 1, 2)]},
            {'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'minecraft:totem_of_undying'}],
             'condition': {'type': 'minecraft:all_of', 'terms': [{'type': 'minecraft:killed_by_player'},
                                                                 {'type': 'minecraft:random_chance', 'chance': 0.02}]}}],
        'random_sequence': 'jaymoskingdoms:entities/night_bleeder'})
    write(DATA + 'jaymoskingdoms/loot_table/entities/blightwing.json', {
        'type': 'minecraft:entity',
        'pools': [
            {'rolls': 1, 'entries': [entry('minecraft:emerald', 1, 3)]},
            {'rolls': 1, 'entries': [entry('jaymoskingdoms:cursed_tusk', 0, 1)]}],
        'random_sequence': 'jaymoskingdoms:entities/blightwing'})


def tags():
    for tag, add in (('undead', ['jaymoskingdoms:night_bleeder', 'jaymoskingdoms:blightwing', 'jaymoskingdoms:vampire_bat']),
                     ('burn_in_daylight', ['jaymoskingdoms:night_bleeder', 'jaymoskingdoms:blightwing']),
                     ('illager', ['jaymoskingdoms:night_bleeder'])):
        path = DATA + 'minecraft/tags/entity_type/' + tag + '.json'
        obj = json.load(open(path)) if os.path.exists(path) else {'replace': False, 'values': []}
        for v in add:
            if v not in obj['values']:
                obj['values'].append(v)
        write(path, obj)


def brewing():
    out = DATA + 'jaymoskingdoms/recipe/brewing/'
    containers = ('potion', 'splash_potion', 'lingering_potion')

    def mix(container, frm, reagent, to):
        name = '%s_%s_%s' % (container, frm.split(':')[1], reagent.split(':')[1])
        write(out + name + '.json', {
            'type': 'minecraft:brewing',
            'input': {'item': 'minecraft:' + container, 'potion_contents': {'potions': frm}},
            'output': {'components': {'minecraft:potion_contents': {'potion': to}}, 'id': 'minecraft:' + container},
            'reagent': {'item': reagent}})

    def transform(frm_container, reagent, to_container, potion):
        name = '%s_%s_%s' % (frm_container, potion.split(':')[1], reagent.split(':')[1])
        write(out + name + '.json', {
            'type': 'minecraft:brewing',
            'input': {'item': 'minecraft:' + frm_container, 'potion_contents': {'potions': potion}},
            'output': {'components': {'minecraft:potion_contents': {'potion': potion}}, 'id': 'minecraft:' + to_container},
            'reagent': {'item': reagent}})

    mixes = [('minecraft:awkward', 'jaymoskingdoms:cursed_tusk', 'jaymoskingdoms:immunity'),
             ('jaymoskingdoms:immunity', 'minecraft:redstone', 'jaymoskingdoms:long_immunity'),
             ('jaymoskingdoms:immunity', 'minecraft:fermented_spider_eye', 'jaymoskingdoms:atrophy'),
             ('jaymoskingdoms:long_immunity', 'minecraft:fermented_spider_eye', 'jaymoskingdoms:atrophy'),
             ('jaymoskingdoms:atrophy', 'minecraft:glowstone_dust', 'jaymoskingdoms:strong_atrophy')]
    for c in containers:
        for frm, reagent, to in mixes:
            mix(c, frm, reagent, to)
    for potion in ('jaymoskingdoms:immunity', 'jaymoskingdoms:long_immunity', 'jaymoskingdoms:atrophy', 'jaymoskingdoms:strong_atrophy'):
        transform('potion', 'minecraft:gunpowder', 'splash_potion', potion)
        transform('splash_potion', 'minecraft:dragon_breath', 'lingering_potion', potion)


for i in range(len(BLEEDERS)):
    bleeder(i)
bats()
tusk()
card()
lute()
dried_meat()
egg('night_bleeder_spawn_egg', rgb(0x241016), rgb(0xB9BEC9), 8)
egg('vampire_bat_spawn_egg', rgb(0x2A1A1E), rgb(0xC0141E), 9)
effects()
item_models()
loot()
tags()
brewing()
print('nightfall art and data written')
