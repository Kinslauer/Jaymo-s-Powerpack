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

# pile sizes, [from, to] in model units; each size adds one to the last (like candles)
STICKS = [
    ([5.5, 0, 6], [10, 3, 10.5]),
    ([9.5, 0, 9.5], [13, 2, 13]),
    ([3, 0, 10], [6.5, 1.5, 13]),
    ([3.5, 0, 3], [7, 2.5, 6]),
]

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


def stick_texture():
    """Generate a 16x16 stick texture (brown, matching vanilla stick colour)."""
    path = os.path.join(ASSETS, 'textures', 'block', 'ground', 'loose_sticks.png')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    # A simple brown stick: vertical line in the middle, slightly tapered
    # Colours sampled from vanilla stick texture (approx #442c1e brown)
    brown = (68, 44, 30, 255)
    dark = (48, 32, 22, 255)
    for y in range(16):
        if y < 4:
            # top: single pixel
            img.putpixel((7, y), brown)
        elif y < 10:
            # middle: 2 pixels wide
            img.putpixel((6, y), dark)
            img.putpixel((7, y), brown)
        else:
            # bottom: 3 pixels wide
            img.putpixel((5, y), dark)
            img.putpixel((6, y), brown)
            img.putpixel((7, y), dark)
    img.save(path)


def main():
    templates('sticks', STICKS)
    blockstate('loose_sticks', 'sticks', len(STICKS), 'loose_sticks')
    models('loose_sticks', 'sticks', len(STICKS), f'{NS}:block/ground/loose_sticks')
    loot('loose_sticks', 'minecraft:stick', 'sticks', len(STICKS))
    stick_texture()

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