"""Loose sticks on the ground (com.jaymospowers.ground.GroundSticks): the texture, block models, blockstate, loot table
and worldgen JSON. Run from the repo root: python -I tools/gen_loose_sticks.py

Keep MAX_STICKS in step with LooseSticksBlock.MAX_STICKS. The texture is our own (a few bark-brown strips).
"""
import json
import math
import os
import random

NS = 'jaymospowers'
ROOT = os.path.join('src', 'main', 'resources')
ASSETS = os.path.join(ROOT, 'assets', NS)
DATA = os.path.join(ROOT, 'data', NS)

TEXTURE = f'{NS}:block/ground/loose_sticks'
# Texture rows: each stick takes its own strip for its top, sides and underside; the cut ends are pale wood.
TOP_ROW, SIDE_ROW, UNDER_ROW = 0, 4, 8
END_UV = [0, 12, 1, 13]

# The sticks of a pile, in the order a pile grows (1 to 4). Each: start (x, z), heading in degrees (a turn about y),
# length and thickness, in model units; twigs branch off a stick: (distance along it, turn from its heading, length).
STICKS = [
    {'start': (3.0, 10.5), 'turn': 20, 'length': 11, 'thick': 1.25, 'twigs': [(6.5, -50, 3)]},
    {'start': (3.5, 3.0), 'turn': -15, 'length': 8, 'thick': 1.0, 'twigs': []},
    {'start': (5.5, 13.5), 'turn': 8, 'length': 9, 'thick': 1.25, 'twigs': [(3.0, -45, 2)]},
    {'start': (1.8, 12.5), 'turn': 95, 'length': 7, 'thick': 1.0, 'twigs': []},
]
MAX_STICKS = len(STICKS)


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def r(v):
    return round(v, 3)


def piece(x, z, turn, length, thick, row):
    """One straight bit of wood from (x, z), heading `turn` degrees about y, lying on the ground."""
    half = thick / 2
    faces = {
        'up': {'uv': [0, TOP_ROW + row, length, TOP_ROW + row + 1], 'texture': '#all'},
        'down': {'uv': [0, UNDER_ROW + row, length, UNDER_ROW + row + 1], 'texture': '#all'},
        'north': {'uv': [0, SIDE_ROW + row, length, SIDE_ROW + row + 1], 'texture': '#all'},
        'south': {'uv': [0, SIDE_ROW + row, length, SIDE_ROW + row + 1], 'texture': '#all'},
        'east': {'uv': END_UV, 'texture': '#all'},
        'west': {'uv': END_UV, 'texture': '#all'},
    }
    element = {'from': [r(x), 0, r(z - half)], 'to': [r(x + length), r(thick), r(z + half)], 'faces': faces}
    if turn:
        element['rotation'] = {'origin': [r(x), 0, r(z)], 'axis': 'y', 'angle': turn}
    return element


def stick_elements(stick, row):
    x, z = stick['start']
    out = [piece(x, z, stick['turn'], stick['length'], stick['thick'], row)]
    for along, turn, length in stick['twigs']:
        # a turn of a about +y carries +x to (cos a, -sin a) (joml's rotation, as the game bakes it)
        a = math.radians(stick['turn'])
        tx, tz = x + along * math.cos(a), z - along * math.sin(a)
        out.append(piece(tx, tz, stick['turn'] + turn, length, 0.75, row))
    return out


def texture():
    from PIL import Image
    rng = random.Random(1569170499)
    img = Image.new('RGBA', (16, 16))
    top = [(146, 112, 48), (132, 100, 41), (121, 92, 37)]
    side = [(104, 78, 30), (96, 72, 28), (86, 64, 25)]
    under = [(66, 49, 19), (58, 43, 16)]
    knot = (78, 58, 23)
    for y in range(16):
        palette = top if y < SIDE_ROW else side if y < UNDER_ROW else under if y < 12 else side
        for x in range(16):
            img.putpixel((x, y), (*rng.choice(palette), 255))
    # knots along the tops and sides
    for y in list(range(TOP_ROW, TOP_ROW + 4)) + list(range(SIDE_ROW, SIDE_ROW + 4)):
        for x in rng.sample(range(16), 1):
            img.putpixel((x, y), (*knot, 255))
    # the cut end: pale wood
    img.putpixel((END_UV[0], END_UV[1]), (182, 148, 92, 255))
    path = os.path.join(ASSETS, 'textures', 'block', 'ground', 'loose_sticks.png')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def main():
    texture()
    for n in range(1, MAX_STICKS + 1):
        elements = []
        for i, stick in enumerate(STICKS[:n]):
            elements += stick_elements(stick, i)
        write(os.path.join(ASSETS, 'models', 'block', 'ground', f'loose_sticks_{n}.json'), {
            'parent': 'minecraft:block/block',
            'ambientocclusion': False,
            'textures': {'all': TEXTURE, 'particle': TEXTURE},
            'elements': elements,
        })
    write(os.path.join(ASSETS, 'blockstates', 'loose_sticks.json'), {'variants': {
        f'sticks={n}': [{'model': f'{NS}:block/ground/loose_sticks_{n}', **({'y': y} if y else {})} for y in (0, 90, 180, 270)]
        for n in range(1, MAX_STICKS + 1)}})
    write(os.path.join(DATA, 'loot_table', 'blocks', 'loose_sticks.json'), {
        'type': 'minecraft:block',
        'pools': [{
            'rolls': 1,
            'entries': [{
                'type': 'minecraft:item',
                'name': 'minecraft:stick',
                'modifier': [{
                    'type': 'minecraft:set_count',
                    'condition': {'type': 'minecraft:match_block', 'blocks': f'{NS}:loose_sticks', 'state': {'sticks': str(n)}},
                    'count': n,
                } for n in range(2, MAX_STICKS + 1)] + [{'type': 'minecraft:explosion_decay'}],
            }],
        }],
        'random_sequence': f'{NS}:blocks/loose_sticks',
    })
    # worldgen: the feature finds the ground, checks for a canopy and picks the pile size itself (LooseSticksFeature)
    write(os.path.join(DATA, 'worldgen', 'feature', 'loose_sticks.json'), {'type': f'{NS}:loose_sticks'})
    write(os.path.join(DATA, 'worldgen', 'placed_feature', 'loose_sticks.json'), {
        'feature': f'{NS}:loose_sticks',
        'placement': [{'type': 'minecraft:count', 'count': 3},
                      {'type': 'minecraft:in_square'},
                      {'type': 'minecraft:heightmap', 'heightmap': 'MOTION_BLOCKING_NO_LEAVES'},
                      {'type': 'minecraft:biome'}],
    })


if __name__ == '__main__':
    main()
