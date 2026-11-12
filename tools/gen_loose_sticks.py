"""Loose sticks on the ground (com.jaymospowers.ground): blockstate, models, loot table, worldgen feature and
placed feature, and a custom 16x16 stick texture. Run from the repo root: python -I tools/gen_loose_sticks.py

Sticks are vanilla minecraft:stick items; the block has no item of its own.
"""
import json
import os
from PIL import Image

NS = 'jaymospowers'
ROOT = os.path.join('src', 'main', 'resources')
ASSETS = os.path.join(ROOT, 'assets', NS)
DATA = os.path.join(ROOT, 'data', NS)

# One stick per pile size, each size adds one to the last (like candles). A stick is a thin rod lying along its
# own z axis, turned about y by `angle` (model rotations only take -45..45 in 22.5 steps). Sticks that cross get
# different heights so no two top faces sit in the same plane. `twig` = (offset along the stick, side -1/+1,
# length): a thin side shoot that leaves the stick 45 degrees off its line.
# Fields: centre x, centre z, length, width, height, angle, bark row, twig or None
STICKS = [
    (8.0, 8.5, 11.0, 1.25, 1.25, 22.5, 2, (1.5, -1, 3.0)),
    (8.5, 7.0, 9.0, 1.0, 1.0, -45.0, 4, None),
    (4.5, 9.0, 7.0, 1.0, 0.85, -22.5, 6, (-1.0, 1, 2.0)),
    (12.5, 8.5, 6.0, 0.9, 0.7, 0.0, 8, None),
]

TEX = 'jaymospowers:block/ground/loose_sticks'
FIREPIT_MAX_FUEL = 15  # FirepitBlock.MAX_FUEL

# display transforms from the rock piles
DISPLAY = {
    'gui': {'rotation': [30, 225, 0], 'translation': [0, 3, 0], 'scale': [1.6, 1.6, 1.6]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.75, 0.75, 0.75]},
    'fixed': {'rotation': [90, 0, 0], 'translation': [0, 0, -4], 'scale': [1.5, 1.5, 1.5]},
    'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 3, 1], 'scale': [0.8, 0.8, 0.8]},
    'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 4, 0], 'scale': [0.9, 0.9, 0.9]},
    'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 4, 0], 'scale': [0.9, 0.9, 0.9]},
}


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def element(box):
    faces = {d: {'texture': '#all'} for d in ('up', 'north', 'south', 'west', 'east')}
    faces['down'] = {'texture': '#all', 'cullface': 'down'}
    return {'from': box[0], 'to': box[1], 'faces': faces}


def templates(name, boxes):
    for n in range(1, len(boxes) + 1):
        write(os.path.join(ASSETS, 'models', 'block', 'ground', f'{name}_{n}.json'), {
            'parent': 'minecraft:block/block',
            'ambientocclusion': False,
            'display': DISPLAY,
            'textures': {'particle': '#all'},
            'elements': [element(b) for b in boxes[:n]],
        })


def blockstate(block, prop, count, model):
    variants = {}
    for n in range(1, count + 1):
        for water in ('false', 'true'):
            variants[f'{prop}={n},waterlogged={water}'] = [
                {'model': f'{NS}:block/ground/{model}_{n}', **({'y': y} if y else {})} for y in (0, 90, 180, 270)
            ]
    write(os.path.join(ASSETS, 'blockstates', f'{block}.json'), {'variants': variants})


def models(block, template, count, texture):
    for n in range(1, count + 1):
        write(os.path.join(ASSETS, 'models', 'block', 'ground', f'{block}_{n}.json'), {
            'parent': f'{NS}:block/ground/{template}_{n}',
            'textures': {'all': texture},
        })


def loot(block, item, prop, count):
    write(os.path.join(DATA, 'loot_table', 'blocks', f'{block}.json'), {
        'type': 'minecraft:block',
        'pools': [{
            'rolls': 1,
            'entries': [{
                'type': 'minecraft:item',
                'name': item,
                'modifier': [{
                    'type': 'minecraft:set_count',
                    'condition': {'type': 'minecraft:match_block', 'blocks': f'{NS}:{block}', 'state': {prop: str(n)}},
                    'count': n,
                } for n in range(2, count + 1)] + [{'type': 'minecraft:explosion_decay'}],
            }],
        }],
        'random_sequence': f'{NS}:blocks/{block}',
    })


def rod(x0, z0, x1, z1, height, angle, origin, row, ends, top_row=None):
    """A rod from (x0, z0) to (x1, z1), long along z, turned `angle` about `origin`. Sides and top take a
    lengthwise bark strip from texture row `row` (top faces one row lighter); `ends` = uv patch for end grain."""
    length = z1 - z0
    width = x1 - x0
    top = row - 1 if top_row is None else top_row
    u0 = (16 - length) / 2  # centre the strip so short sticks show the middle of the bark
    side = [u0, row, u0 + length, row + height]
    faces = {
        'up': {'uv': [u0, top, u0 + length, top + width], 'rotation': 90, 'texture': '#all'},
        'down': {'uv': [u0, 11, u0 + length, 11 + width], 'rotation': 90, 'texture': '#all', 'cullface': 'down'},
        'west': {'uv': side, 'texture': '#all'},
        'east': {'uv': [side[2], side[1], side[0], side[3]], 'texture': '#all'},
        'north': {'uv': ends, 'texture': '#all'},
        'south': {'uv': ends, 'texture': '#all'},
    }
    el = {'from': [x0, 0, z0], 'to': [x1, height, z1], 'faces': faces}
    if angle:
        el['rotation'] = {'angle': angle, 'axis': 'y', 'origin': origin}
    return el


def r3(v):
    return round(v, 4)


def stick_elements(stick):
    import math
    cx, cz, length, width, height, angle, row, twig = stick
    out = [rod(cx - width / 2, cz - length / 2, cx + width / 2, cz + length / 2, height, angle, [cx, 0, cz], row,
               [0.5, 12.5, 3.5, 15.5])]
    if twig:
        along, side, tlen = twig
        a = math.radians(angle)
        # point on the stick's side (a little inside it), local (side * (w/2 - 0.2), along) turned like the stick
        lx, lz = side * (width / 2 - 0.2), along
        px = cx + lx * math.cos(a) + lz * math.sin(a)
        pz = cz - lx * math.sin(a) + lz * math.cos(a)
        tw = 0.5
        th = min(0.6, height - 0.2)
        out.append(rod(r3(px - tw / 2), r3(pz), r3(px + tw / 2), r3(pz + tlen), th, angle + 45 * side,
                       [r3(px), 0, r3(pz)], 10, [4.5, 12.5, 5.5, 13.5], top_row=9))
    return out


def stick_bounds(n):
    """Selection box around the first n sticks, rounded out to half pixels (LooseSticksBlock.STICK_SHAPES)."""
    import math
    xs, zs, ys = [], [], []
    for cx, cz, length, width, height, angle, _, _ in STICKS[:n]:
        a = math.radians(angle)
        for lx in (-width / 2, width / 2):
            for lz in (-length / 2, length / 2):
                xs.append(cx + lx * math.cos(a) + lz * math.sin(a))
                zs.append(cz - lx * math.sin(a) + lz * math.cos(a))
        ys.append(height)
    lo = lambda v: max(0.0, math.floor(min(v) * 2) / 2)
    hi = lambda v: min(16.0, math.ceil(max(v) * 2) / 2)
    return [lo(xs), 0.0, lo(zs)], [hi(xs), max(1.0, math.ceil(max(ys) * 2) / 2), hi(zs)]


def stick_templates():
    for n in range(1, len(STICKS) + 1):
        elements = [e for s in STICKS[:n] for e in stick_elements(s)]
        write(os.path.join(ASSETS, 'models', 'block', 'ground', f'sticks_{n}.json'), {
            'parent': 'minecraft:block/block',
            'ambientocclusion': False,
            'display': DISPLAY,
            'textures': {'particle': '#all'},
            'elements': elements,
        })


def stick_texture():
    """16x16 stick texture, all opaque (the block renders solid, so clear pixels would show black).
    Rows 0-10: lengthwise bark strips (top rows lighter, for top faces); row 11: underside;
    rows 12-15 cols 0-3: end grain (pale wood in a bark ring); cols 4-5: twig tip."""
    import random
    rnd = random.Random(26_3)
    path = os.path.join(ASSETS, 'textures', 'block', 'ground', 'loose_sticks.png')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 255))
    light = [(150, 116, 70), (138, 106, 63), (127, 97, 57)]
    mid = [(112, 85, 50), (102, 77, 45), (93, 70, 41)]
    dark = [(80, 60, 35), (71, 53, 31)]
    knot = (58, 42, 24)
    for y in range(12):
        tones = light if y in (1, 3, 5, 7, 9) else mid if y < 11 else dark
        if y == 11:
            tones = dark
        prev = rnd.choice(tones)
        for x in range(16):
            c = prev if rnd.random() < 0.55 else rnd.choice(tones)  # streaky runs along the stick
            prev = c
            img.putpixel((x, y), c + (255,))
        if y < 11:
            for _ in range(2):  # knots and bark cracks
                img.putpixel((rnd.randrange(2, 14), y), knot + (255,))
    # end grain: bark ring, pale sapwood, darker heart
    ring = (84, 63, 37)
    for y in range(12, 16):
        for x in range(0, 4):
            img.putpixel((x, y), ring + (255,))
    for y in range(13, 15):
        for x in range(1, 3):
            img.putpixel((x, y), (184, 152, 101, 255))
    img.putpixel((2, 14), (160, 128, 82, 255))
    img.putpixel((1, 13), (196, 166, 114, 255))
    # twig tip
    for y in range(12, 14):
        for x in range(4, 6):
            img.putpixel((x, y), (150, 120, 76, 255) if (x + y) % 2 else (122, 93, 56, 255))
    # rest of the bottom rows: plain bark so particles look right
    for y in range(12, 16):
        for x in range(6, 16):
            img.putpixel((x, y), rnd.choice(mid) + (255,))
    for y in range(14, 16):
        for x in range(4, 6):
            img.putpixel((x, y), rnd.choice(mid) + (255,))
    img.save(path)


def firepit():
    """The firepit (FirepitBlock): an ash bed, the full stick pile while it has fuel, a flame while lit."""
    import random
    rnd = random.Random(7)
    path = os.path.join(ASSETS, 'textures', 'block', 'ground', 'firepit_ash.png')
    img = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            r = rnd.random()
            c = (52, 50, 48) if r < 0.35 else (78, 75, 72) if r < 0.7 else (104, 100, 96) if r < 0.93 else (32, 28, 26)
            img.putpixel((x, y), c + (255,))
    for _ in range(5):  # charcoal bits
        img.putpixel((rnd.randrange(16), rnd.randrange(16)), (24, 20, 18, 255))
    img.save(path)
    ground = os.path.join(ASSETS, 'models', 'block', 'ground')
    faces = {d: {'texture': '#ash'} for d in ('up', 'north', 'south', 'west', 'east')}
    faces['down'] = {'texture': '#ash', 'cullface': 'down'}
    write(os.path.join(ground, 'firepit_ash.json'), {
        'parent': 'minecraft:block/block',
        'textures': {'ash': f'{NS}:block/ground/firepit_ash', 'particle': f'{NS}:block/ground/firepit_ash'},
        'elements': [
            {'from': [3, 0, 3], 'to': [13, 0.5, 13], 'faces': faces},
            {'from': [4.5, 0.5, 4.5], 'to': [11.5, 0.75, 11.5], 'faces': {d: {'texture': '#ash'} for d in ('up', 'north', 'south', 'west', 'east')}},
        ],
    })
    write(os.path.join(ground, 'firepit_sticks.json'), {
        'parent': f'{NS}:block/ground/sticks_4',
        'textures': {'all': TEX},
    })
    flame = {'rotation': {'origin': [8, 0, 8], 'axis': 'y', 'angle': 45}, 'shade_direction_override': 'up'}
    write(os.path.join(ground, 'firepit_fire.json'), {
        'parent': 'minecraft:block/block',
        'ambientocclusion': False,
        'textures': {'fire': 'minecraft:block/campfire_fire', 'particle': 'minecraft:block/campfire_fire'},
        'elements': [
            {'from': [2.5, 0.5, 8], 'to': [13.5, 11.5, 8], **flame,
             'faces': {d: {'uv': [0, 0, 16, 16], 'texture': '#fire'} for d in ('north', 'south')}},
            {'from': [8, 0.5, 2.5], 'to': [8, 11.5, 13.5], **flame,
             'faces': {d: {'uv': [0, 0, 16, 16], 'texture': '#fire'} for d in ('west', 'east')}},
        ],
    })
    fuels = '|'.join(str(n) for n in range(1, FIREPIT_MAX_FUEL + 1))
    write(os.path.join(ASSETS, 'blockstates', 'firepit.json'), {'multipart': [
        {'apply': {'model': f'{NS}:block/ground/firepit_ash'}},
        {'when': {'fuel': fuels}, 'apply': {'model': f'{NS}:block/ground/firepit_sticks'}},
        {'when': {'lit': 'true'}, 'apply': {'model': f'{NS}:block/ground/firepit_fire'}},
    ]})
    # broken: the sticks still in it, at most a pile's worth
    write(os.path.join(DATA, 'loot_table', 'blocks', 'firepit.json'), {
        'type': 'minecraft:block',
        'pools': [{
            'rolls': 1,
            'entries': [{
                'type': 'minecraft:item',
                'name': 'minecraft:stick',
                'modifier': [{
                    'type': 'minecraft:set_count',
                    'condition': {'type': 'minecraft:match_block', 'blocks': f'{NS}:firepit', 'state': {'fuel': str(n)}},
                    'count': min(n, 4),
                } for n in range(0, FIREPIT_MAX_FUEL + 1) if n != 1] + [{'type': 'minecraft:explosion_decay'}],
            }],
        }],
        'random_sequence': f'{NS}:blocks/firepit',
    })
    # a campfire underneath: flint and steel, flint strikes, shovels, sitting by the fire all know the tag
    write(os.path.join(ROOT, 'data', 'minecraft', 'tags', 'block', 'campfires.json'), {'values': [f'{NS}:firepit']})


def main():
    stick_templates()
    for n in range(1, len(STICKS) + 1):
        print('sticks', n, 'shape', *stick_bounds(n))
    blockstate('loose_sticks', 'sticks', len(STICKS), 'loose_sticks')
    models('loose_sticks', 'sticks', len(STICKS), f'{NS}:block/ground/loose_sticks')
    loot('loose_sticks', 'minecraft:stick', 'sticks', len(STICKS))
    # the creative-tab block item shows a full pile
    write(os.path.join(ASSETS, 'items', 'loose_sticks.json'),
          {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/ground/loose_sticks_4'}})
    stick_texture()
    firepit()

    # worldgen: the feature picks the pile size itself (LooseSticksFeature)
    write(os.path.join(DATA, 'worldgen', 'feature', 'loose_sticks.json'), {'type': f'{NS}:loose_sticks'})
    surface = [
        {'type': 'minecraft:in_square'},
        {'type': 'minecraft:heightmap', 'heightmap': 'OCEAN_FLOOR_WG'},
        {'type': 'minecraft:biome'}
    ]
    write(os.path.join(DATA, 'worldgen', 'placed_feature', 'loose_sticks.json'), {
        'feature': f'{NS}:loose_sticks',
        'placement': [{'type': 'minecraft:count', 'count': 3}] + surface,
    })


if __name__ == '__main__':
    main()