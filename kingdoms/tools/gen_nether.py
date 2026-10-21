"""The Nether faction's art and data (kingdoms/src/main/java/com/jaymoskingdoms/nether), all our own (Just-In NETHER,
the mod it's after, is All Rights Reserved: nothing of it is used):
- the four Hornkin skins (warrior, hurler, brute, zombified) for the client's HornkinModel: the humanoid layout of a
  64x64 skin above, the muzzle, nose ring, horns, ears and loincloth below;
- the four spawn eggs' icons and item models, and the Hornkin's loot tables.
Run from kingdoms/: python3 tools/gen_nether.py"""
import json, os, random
from PIL import Image

RES = 'src/main/resources/'
ASSETS = RES + 'assets/jaymoskingdoms/'
DATA = RES + 'data/'
ENT = ASSETS + 'textures/entity/nether/'
ITEM = ASSETS + 'textures/item/'
for d in (ENT, ITEM, ASSETS + 'items/', ASSETS + 'models/item/', DATA + 'jaymoskingdoms/loot_table/entities/'):
    os.makedirs(d, exist_ok=True)


def rgb(h, a=255):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255, a)


def jitter(c, n, rnd):
    k = rnd.randint(-n, n)
    return tuple(max(0, min(255, ch + k)) for ch in c[:3]) + (c[3] if len(c) > 3 else 255,)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


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


# (hide, dark hide, muzzle, horn, cloth, trim, eyes, paint) for each kind
KINDS = {
    'warrior': (0x7A3B2A, 0x4A2018, 0xB07A5A, 0xE8DCC0, 0x8E1B22, 0xF2C230, 0xFFB000, None),
    'hurler': (0x5C2E25, 0x381A15, 0x946450, 0xD8C8A8, 0x2A1A1A, 0xC9A227, 0xFF6A00, 0xFF7A1A),
    'brute': (0x2E1E1C, 0x1A100F, 0x6A4A40, 0x3A3030, 0x4A0E12, 0xF2C230, 0xFF3A1A, None),
    'zombified': (0x6E7A5C, 0x4A5440, 0x8E8C70, 0xB8B09A, 0x4A2E22, 0x8A7A3A, 0xC8E070, None),
    # (0.7.142) the war forts' warlord: near-black hide, bronze horns tipped in gold, a gold crown (on the hat layer)
    'warlord': (0x3A1512, 0x200A08, 0x7A4A3A, 0x9A6A30, 0x1A1414, 0xF2C230, 0xFF2A00, None),
}
BONE, HOOF, NOSTRIL, MOUTH = rgb(0xD8D0B8), rgb(0x1E1412), rgb(0x150A08), rgb(0x2A0E0A)


def skin(name):
    hide, dark, muzzle, horn, cloth, trim, eyes, paint = KINDS[name]
    hide, dark, muzzle, horn, cloth, trim, eyes = (rgb(c) for c in (hide, dark, muzzle, horn, cloth, trim, eyes))
    paint = rgb(paint) if paint is not None else None
    zombie = name == 'zombified'
    rnd = random.Random(sum(map(ord, name)))
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))

    def fur(c, n=7):
        if zombie and rnd.random() < 0.08:
            return jitter(BONE, 10, rnd)
        return jitter(c, n, rnd)

    def head(face, x, y, fw, fh):
        if face == 'top':
            # a darker tuft between the horns
            return fur(dark if 2 <= x <= 5 and y >= 3 else hide)
        if face == 'north':
            if y == 2 and x in (1, 2, 5, 6):
                return jitter(dark, 4, rnd)
            if y == 3 and x in (1, 2, 5, 6):
                return eyes if x in (2, 5) else jitter(mix(eyes, dark, 0.5), 3, rnd)
            if paint is not None and y in (4, 5) and x in (0, 7):
                return jitter(paint, 10, rnd)
            if y >= 6:
                return jitter(muzzle, 6, rnd)
        if face == 'bottom':
            return jitter(muzzle, 6, rnd)
        return fur(hide)

    def body(face, x, y, fw, fh):
        if y in (10, 11):
            # a belt with a gold boss in front
            if face == 'north' and 3 <= x <= 4:
                return jitter(trim, 8, rnd)
            return jitter(rgb(0x3A2418), 6, rnd)
        if face == 'north' and y <= 6 and 1 <= x <= 6:
            # shaggy chest
            return fur(dark)
        if paint is not None and face == 'north' and y == 8 and x % 2 == 0:
            return jitter(paint, 10, rnd)
        return fur(hide)

    def arm(face, x, y, fw, fh):
        if name in ('brute', 'warlord') and y in (2, 3) and face != 'top':
            return jitter(trim, 10, rnd)
        if 8 <= y <= 10 and face != 'top':
            return jitter(rgb(0x4A2E1E), 6, rnd)
        if face == 'bottom' or y == 11:
            return jitter(dark, 5, rnd)
        if paint is not None and y == 5 and face == 'north':
            return jitter(paint, 10, rnd)
        return fur(hide)

    def leg(face, x, y, fw, fh):
        if face == 'bottom' or y >= 10:
            return jitter(HOOF, 4, rnd)
        if y >= 8:
            return fur(dark)
        return fur(hide)

    def muzzle_box(face, x, y, fw, fh):
        if face == 'north' and y == 1 and x in (1, 4):
            return jitter(NOSTRIL, 3, rnd)
        if face == 'north' and y == 3:
            return jitter(MOUTH, 3, rnd)
        if face == 'top':
            return jitter(mix(muzzle, hide, 0.4), 5, rnd)
        return jitter(muzzle, 6, rnd)

    def ring(face, x, y, fw, fh):
        return jitter(trim, 10, rnd)

    def horn_box(face, x, y, fw, fh):
        # pale at the root, darker towards the outer end (the right horn's outer end is its west face side)
        t = x / max(1, fw - 1) if face in ('north', 'south', 'top', 'bottom') else 0.5
        c = mix(horn, dark, 0.15 + 0.25 * t)
        if name in ('brute', 'warlord'):
            c = mix(c, trim, 0.25) if face != 'bottom' else c
        return jitter(c, 6, rnd)

    def horn_tip(face, x, y, fw, fh):
        if face == 'top':
            return jitter(trim if name in ('brute', 'warlord') else mix(horn, dark, 0.7), 6, rnd)
        t = 1.0 - y / max(1, fh - 1)
        return jitter(mix(horn, dark, 0.3 + 0.4 * t), 6, rnd)

    def ear(face, x, y, fw, fh):
        if face == 'north':
            return jitter(mix(muzzle, cloth, 0.2), 6, rnd)
        return fur(hide)

    def loin(face, x, y, fw, fh):
        if face in ('top', 'bottom'):
            return None
        if face in ('east', 'west'):
            return None
        if zombie and y >= 4 and rnd.random() < 0.4:
            return None
        if y == fh - 1:
            return jitter(trim, 8, rnd)
        return jitter(cloth, 8, rnd)

    box(img, 0, 0, 8, 8, 8, head)
    if name == 'warlord':
        # a gold crown round the top of the head: a band two pixels deep, points between
        def crown(face, x, y, fw, fh):
            if face in ('top', 'bottom'):
                return None
            if y == 0 and x % 2 == 0:
                return jitter(trim, 12, rnd)
            if y in (1, 2):
                return jitter(rgb(0xFF2A00) if x == fw // 2 and y == 2 and face == 'north' else trim, 12, rnd)
            return None
        box(img, 32, 0, 8, 8, 8, crown)
    box(img, 16, 16, 8, 12, 4, body)
    box(img, 40, 16, 4, 12, 4, arm)
    box(img, 0, 16, 4, 12, 4, leg)
    box(img, 0, 32, 6, 4, 3, muzzle_box)
    box(img, 18, 32, 2, 2, 0, ring)
    box(img, 24, 32, 4, 2, 2, horn_box)
    box(img, 36, 32, 2, 3, 2, horn_tip)
    box(img, 44, 32, 3, 2, 1, ear)
    box(img, 0, 40, 6, 6, 5, loin)
    img.save(ENT + name + '.png')


def icon(name, size, pixels, folder=ITEM):
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    px = img.load()
    for (x, y), c in pixels.items():
        if 0 <= x < size and 0 <= y < size:
            px[x, y] = c
    img.save(folder + name + '.png')


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


EGGS = {'hornkin_spawn_egg': (0x7A3B2A, 0xF2C230), 'hornkin_hurler_spawn_egg': (0x5C2E25, 0xFF7A1A),
        'hornkin_brute_spawn_egg': (0x2E1E1C, 0xF2C230), 'hornkin_warlord_spawn_egg': (0x3A1512, 0xE0B040), 'zombified_hornkin_spawn_egg': (0x6E7A5C, 0xD8D0B8)}


def write(path, obj):
    with open(path, 'w', newline='\n') as f:
        json.dump(obj, f, indent='\t')
        f.write('\n')


def item_models():
    for name in EGGS:
        write(ASSETS + 'items/' + name + '.json', {'model': {'type': 'minecraft:model', 'model': 'jaymoskingdoms:item/' + name}})
        write(ASSETS + 'models/item/' + name + '.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jaymoskingdoms:item/' + name}})


def entry(item, lo, hi, looting=True):
    mods = [{'type': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': lo, 'max': hi}}]
    if looting:
        mods.append({'type': 'minecraft:enchanted_count_increase', 'count': {'type': 'minecraft:uniform', 'min': 0.0, 'max': 1.0},
                     'enchantment': 'minecraft:looting'})
    return {'type': 'minecraft:item', 'name': item, 'modifier': mods}


def table(name, pools):
    write(DATA + 'jaymoskingdoms/loot_table/entities/' + name + '.json',
          {'type': 'minecraft:entity', 'pools': [{'rolls': 1, 'entries': [e]} for e in pools],
           'random_sequence': 'jaymoskingdoms:entities/' + name})


def loot():
    table('hornkin', [entry('minecraft:leather', 0, 2), entry('minecraft:gold_nugget', 1, 4)])
    table('hornkin_hurler', [entry('minecraft:magma_cream', 0, 2), entry('minecraft:gold_nugget', 1, 3)])
    table('hornkin_brute', [entry('minecraft:leather', 1, 3), entry('minecraft:gold_ingot', 1, 3), entry('minecraft:bone', 1, 2)])
    table('hornkin_warlord', [entry('minecraft:gold_ingot', 4, 8), entry('minecraft:gold_block', 1, 1, False),
                              entry('minecraft:leather', 2, 4)])
    table('zombified_hornkin', [entry('minecraft:rotten_flesh', 0, 2), entry('minecraft:gold_nugget', 0, 2)])


if __name__ == '__main__':
    for kind in KINDS:
        skin(kind)
    for i, (name, (base, spots)) in enumerate(EGGS.items()):
        egg(name, rgb(base), rgb(spots), 40 + i)
    item_models()
    loot()
