"""The gone-before's art and data (kingdoms/src/main/java/com/jaymoskingdoms/remains/): our own bone textures (one tint per
place), the remains block's models (3 poses), its blockstate, the worldgen features and placed features, the loot tables,
the remains' token items, the weak floor block. Run from kingdoms/: python3 tools/gen_remains.py"""
import json, os, random
from PIL import Image

RES = 'src/main/resources'
A = RES + '/assets/jaymoskingdoms'
D = RES + '/data/jaymoskingdoms'
KINDS = ['snow', 'jungle', 'river', 'dripstone', 'cliff', 'desert', 'cave', 'sky', 'nether', 'end']
# bone shade, dark shade, and a stain that clings to it (frost, moss, silt, dust, soot...)
TINTS = {
    'snow': ((0xE6, 0xEC, 0xF2), (0xA9, 0xB8, 0xC9), (0xFF, 0xFF, 0xFF)),
    'jungle': ((0xC9, 0xC6, 0xA0), (0x7C, 0x80, 0x55), (0x3F, 0x7A, 0x32)),
    'river': ((0xB9, 0xB5, 0x9C), (0x76, 0x72, 0x5C), (0x5A, 0x55, 0x3C)),
    'dripstone': ((0xCF, 0xC2, 0xAC), (0x8F, 0x7F, 0x68), (0xA8, 0x8E, 0x6E)),
    'cliff': ((0xD8, 0xD2, 0xC0), (0x95, 0x8E, 0x7C), (0x6E, 0x6A, 0x62)),
    'desert': ((0xEC, 0xE0, 0xBC), (0xB5, 0xA4, 0x78), (0xD9, 0xBF, 0x80)),
    'cave': ((0xC8, 0xC4, 0xB8), (0x84, 0x80, 0x76), (0xE8, 0xE8, 0xE8)),
    'sky': ((0xF2, 0xEE, 0xE0), (0xC0, 0xBC, 0xA8), (0xFF, 0xF2, 0xB0)),
    'nether': ((0xA8, 0x9C, 0x8C), (0x5E, 0x54, 0x4A), (0x3A, 0x32, 0x2C)),
    'end': ((0xD6, 0xCF, 0xE0), (0x8C, 0x82, 0xA0), (0x9A, 0x6C, 0xC8)),
}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', newline='\n') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def bone_texture(kind):
    light, dark, stain = TINTS[kind]
    rnd = random.Random(kind)
    img = Image.new('RGBA', (16, 16))
    for x in range(16):
        for y in range(16):
            c = light if rnd.random() > 0.35 else dark
            if rnd.random() < 0.12:
                c = stain
            j = rnd.randint(-8, 8)
            img.putpixel((x, y), tuple(max(0, min(255, v + j)) for v in c) + (255,))
    save(img, A + '/textures/block/remains_%s.png' % kind)


def box(frm, to):
    w, h, d = [abs(to[i] - frm[i]) for i in range(3)]

    def face(a, b):
        return {'uv': [0, 0, min(16, a), min(16, b)], 'texture': '#bone'}
    return {'from': frm, 'to': to, 'faces': {
        'north': face(w, h), 'south': face(w, h), 'east': face(d, h), 'west': face(d, h), 'up': face(w, d), 'down': face(w, d)}}


# bones lying on the ground, three poses: on the back, curled on the side, face down with an arm out
POSES = [
    [((6.5, 0, 12), (9.5, 3, 15)), ((7.4, 0, 3), (8.6, 1, 12)), ((5, 0.5, 6.5), (11, 1.5, 7.5)), ((5.5, 0.5, 8), (10.5, 1.5, 9)),
     ((6, 0.5, 9.5), (10, 1.5, 10.5)), ((6, 0, 2), (10, 1.5, 3.5)), ((4, 0, 5), (5.2, 1, 10)), ((10.8, 0, 5), (12, 1, 10)),
     ((6.2, 0, 0), (7.4, 1, 2.5)), ((8.6, 0, 0), (9.8, 1, 2.5))],
    [((3, 0, 9), (6, 3, 12)), ((5, 0, 5), (6.2, 1.2, 9.5)), ((6, 0.5, 4), (12, 1.5, 5)), ((6.5, 0.5, 5.5), (11.5, 1.5, 6.5)),
     ((6, 0, 3), (10, 1.5, 4.5)), ((9, 0, 6), (10.2, 1, 12)), ((11, 0, 9), (14, 1, 10.2)), ((11, 0, 4), (14, 1, 5.2)),
     ((2, 0, 4), (5, 1, 5.2))],
    [((10, 0, 3), (13, 3, 6)), ((7.4, 0, 4), (8.6, 1, 11.5)), ((5, 0.5, 6), (11, 1.5, 7)), ((5.5, 0.5, 7.5), (10.5, 1.5, 8.5)),
     ((6, 0, 11.5), (10, 1.5, 13)), ((1, 0, 5), (6, 1, 6.2)), ((10.8, 0, 3), (12, 1, 6)), ((6.2, 0, 13), (7.4, 1, 16)),
     ((8.6, 0, 13), (9.8, 1, 16))],
]


def item_entry(name, weight, lo=1, hi=1):
    e = {'type': 'minecraft:item', 'name': name, 'weight': weight}
    if hi > 1:
        e['modifier'] = [{'type': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': lo, 'max': hi}}]
    return e


def table(entries, rolls=(1, 3)):
    return {'type': 'minecraft:chest', 'pools': [{'rolls': {'type': 'minecraft:uniform', 'min': rolls[0], 'max': rolls[1]},
                                                   'bonus_rolls': 0, 'entries': entries}]}


def main():
    for k in KINDS:
        bone_texture(k)
    for p, els in enumerate(POSES):
        for k in KINDS:
            write(A + '/models/block/remains_%s_%d.json' % (k, p), {
                'textures': {'bone': 'jaymoskingdoms:block/remains_' + k, 'particle': 'jaymoskingdoms:block/remains_' + k},
                'elements': [box(list(f), list(t)) for f, t in els]})
    variants = {}
    for ki, k in enumerate(KINDS):
        for p in range(3):
            for face, rot in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
                v = {'model': 'jaymoskingdoms:block/remains_%s_%d' % (k, p)}
                if rot:
                    v['y'] = rot
                variants['kind=%d,pose=%d,facing=%s' % (ki, p, face)] = v
    write(A + '/blockstates/remains.json', {'variants': variants})
    write(A + '/items/remains.json', {'model': {'type': 'minecraft:model', 'model': 'jaymoskingdoms:block/remains_cave_0'}})

    # the weak floor: a rotted plank floor over a pit
    img = Image.new('RGBA', (16, 16))
    rnd = random.Random('weak')
    for x in range(16):
        for y in range(16):
            base = (0x5E, 0x4A, 0x32) if y % 4 else (0x3C, 0x2E, 0x1E)
            j = rnd.randint(-10, 10)
            img.putpixel((x, y), tuple(max(0, min(255, v + j)) for v in base) + (255,))
    save(img, A + '/textures/block/weak_floor.png')
    write(A + '/models/block/weak_floor.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': 'jaymoskingdoms:block/weak_floor'}})
    write(A + '/blockstates/weak_floor.json', {'variants': {'': {'model': 'jaymoskingdoms:block/weak_floor'}}})
    write(A + '/items/weak_floor.json', {'model': {'type': 'minecraft:model', 'model': 'jaymoskingdoms:block/weak_floor'}})

    # the tokens: a tarnished ring and a carved spoon
    for name, base in (('remains_ring', (0xB8, 0xA0, 0x50)), ('remains_spoon', (0x8A, 0x62, 0x3A))):
        icon = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        dark = tuple(int(v * 0.6) for v in base)
        if name == 'remains_ring':
            for xy in [(6, 5), (7, 4), (8, 4), (9, 5), (10, 6), (10, 7), (10, 8), (9, 9), (8, 10), (7, 10), (6, 9), (5, 8), (5, 7), (5, 6)]:
                icon.putpixel(xy, base + (255,))
            for xy in [(7, 5), (8, 5), (9, 6)]:
                icon.putpixel(xy, dark + (255,))
            icon.putpixel((7, 3), (0x9A, 0xD8, 0xC8, 255))
        else:
            for xy in [(5, 3), (6, 3), (5, 4), (6, 4), (7, 4), (5, 5), (6, 5), (6, 6), (7, 7), (8, 8), (9, 9), (10, 10), (11, 11)]:
                icon.putpixel(xy, base + (255,))
            for xy in [(6, 4), (7, 5), (9, 8), (10, 9)]:
                icon.putpixel(xy, dark + (255,))
        save(icon, A + '/textures/item/%s.png' % name)
        write(A + '/models/item/%s.json' % name, {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jaymoskingdoms:item/' + name}})
        write(A + '/items/%s.json' % name, {'model': {'type': 'minecraft:model', 'model': 'jaymoskingdoms:item/' + name}})

    # the headless rider's head
    icon = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(3, 12):
        for x in range(4, 12):
            if (x in (4, 11) and y in (3, 11)) or (y == 11 and x in (4, 5, 10, 11)):
                continue
            icon.putpixel((x, y), (0xD0, 0xCA, 0xB6, 255))
    for xy in [(6, 6), (7, 6), (9, 6), (10, 6), (6, 7), (9, 7), (8, 9), (7, 10), (9, 10)]:
        icon.putpixel(xy, (0x24, 0x20, 0x20, 255))
    save(icon, A + '/textures/item/riders_head.png')
    write(A + '/models/item/riders_head.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jaymoskingdoms:item/riders_head'}})
    write(A + '/items/riders_head.json', {'model': {'type': 'minecraft:model', 'model': 'jaymoskingdoms:item/riders_head'}})

    # worldgen: one feature and one placed feature per kind, and the two last camps
    underground = {'dripstone': (-30, 40), 'cave': (-48, 40), 'nether': (32, 100)}
    for k in KINDS:
        write(D + '/worldgen/feature/remains_%s.json' % k, {'type': 'jaymoskingdoms:remains', 'kind': k})
        place = [{'type': 'minecraft:rarity_filter', 'chance': 3 if k == 'cliff' else 6}, {'type': 'minecraft:in_square'}]
        if k in underground:
            lo, hi = underground[k]
            place.append({'type': 'minecraft:height_range', 'height': {'type': 'minecraft:uniform', 'min_inclusive': {'absolute': lo}, 'max_inclusive': {'absolute': hi}}})
        elif k == 'river':
            place.append({'type': 'minecraft:heightmap', 'heightmap': 'OCEAN_FLOOR_WG'})
        else:
            place.append({'type': 'minecraft:heightmap', 'heightmap': 'WORLD_SURFACE_WG'})
        place.append({'type': 'minecraft:biome'})
        write(D + '/worldgen/placed_feature/remains_%s.json' % k, {'feature': 'jaymoskingdoms:remains_' + k, 'placement': place})
    for t in (0, 1):
        write(D + '/worldgen/feature/last_camp_%d.json' % t, {'type': 'jaymoskingdoms:last_camp', 'template': t})
        write(D + '/worldgen/placed_feature/last_camp_%d.json' % t, {'feature': 'jaymoskingdoms:last_camp_%d' % t, 'placement': [
            {'type': 'minecraft:rarity_filter', 'chance': 70}, {'type': 'minecraft:in_square'},
            {'type': 'minecraft:height_range', 'height': {'type': 'minecraft:uniform', 'min_inclusive': {'absolute': -52 if t else -20}, 'max_inclusive': {'absolute': -8 if t else 30}}},
            {'type': 'minecraft:biome'}]})

    # loot: what each one was carrying
    i = item_entry
    miner = [i('minecraft:iron_pickaxe', 4), i('minecraft:raw_iron', 5, 1, 4), i('minecraft:coal', 6, 2, 6), i('minecraft:torch', 5, 2, 6),
             i('minecraft:iron_helmet', 3), i('minecraft:raw_copper', 3, 1, 4), i('minecraft:bread', 3)]
    hunter = [i('minecraft:bow', 5), i('minecraft:arrow', 7, 3, 9), i('minecraft:leather_chestplate', 3), i('minecraft:string', 4, 1, 3),
              i('minecraft:cooked_porkchop', 2), i('minecraft:feather', 3, 1, 3), i('minecraft:chainmail_boots', 2)]
    sailor = [i('minecraft:lead', 6), i('minecraft:string', 5, 2, 6), i('minecraft:fishing_rod', 4), i('minecraft:iron_axe', 2),
              i('minecraft:cooked_cod', 3), i('minecraft:iron_boots', 2), i('minecraft:compass', 1)]
    walker = [i('minecraft:iron_sword', 3), i('minecraft:iron_chestplate', 3), i('minecraft:bread', 4), i('minecraft:emerald', 3),
              i('minecraft:iron_leggings', 2), i('minecraft:shield', 2), i('minecraft:arrow', 3, 2, 5)]
    loot = {'snow': hunter, 'jungle': hunter, 'river': sailor, 'dripstone': miner, 'cliff': walker, 'desert': walker, 'cave': miner,
            'sky': [i('minecraft:iron_sword', 2), i('minecraft:golden_boots', 2), i('minecraft:gold_nugget', 6, 2, 8), i('minecraft:bread', 3),
                    i('minecraft:feather', 5, 1, 4), i('minecraft:iron_chestplate', 2)],
            'nether': [i('minecraft:iron_sword', 3), i('minecraft:gold_ingot', 3), i('minecraft:iron_chestplate', 3), i('minecraft:cooked_porkchop', 3),
                       i('minecraft:soul_torch', 4, 1, 4), i('minecraft:iron_helmet', 2)],
            'end': [i('minecraft:iron_sword', 3), i('minecraft:ender_pearl', 3), i('minecraft:iron_chestplate', 3), i('minecraft:chorus_fruit', 4, 1, 3),
                    i('minecraft:iron_leggings', 2), i('minecraft:bread', 3)]}
    for k, entries in loot.items():
        write(D + '/loot_table/remains/%s.json' % k, table(entries))
    # parts from the pack's own tinkering, where Powers is loaded
    t = table([i('jaymospowers:pick_head', 4), i('jaymospowers:bow_limb', 3), i('jaymospowers:tool_binding', 4)], (0, 1))
    t['fabric:load_conditions'] = [{'condition': 'fabric:registry_contains', 'registry': 'minecraft:item',
                                    'values': ['jaymospowers:pick_head', 'jaymospowers:bow_limb', 'jaymospowers:tool_binding']}]
    write(D + '/loot_table/remains/tinker_parts.json', t)
    write(D + '/loot_table/remains/camp_stores.json', table([i('minecraft:bread', 5, 1, 3), i('minecraft:torch', 4, 2, 6), i('minecraft:arrow', 4, 3, 9),
        i('minecraft:coal', 4, 2, 5), i('minecraft:iron_ingot', 3, 1, 3), i('minecraft:lead', 3), i('minecraft:cooked_beef', 3, 1, 2),
        i('minecraft:gold_nugget', 3, 2, 6)], (3, 6)))
    # a block's drops: the remains only with silk touch, the weak floor nothing
    write(D + '/loot_table/blocks/remains.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [
        {'type': 'minecraft:item', 'name': 'jaymoskingdoms:remains', 'condition': 'minecraft:tool/can_silk_touch'}]}],
        'random_sequence': 'jaymoskingdoms:blocks/remains'})
    write(D + '/loot_table/blocks/weak_floor.json', {'type': 'minecraft:block', 'pools': [], 'random_sequence': 'jaymoskingdoms:blocks/weak_floor'})


main()
