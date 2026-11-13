#!/usr/bin/env python3
"""Each animal's hide rug (Kitchen HideOrigin): the plain hide rug's grain, recoloured to the animal's coat.

Reads kitchen/.../textures/block/hide_rug.png and writes <animal>_hide_rug.png beside it, with the block state, block
and item models, item definition and loot table for each. Run from the repo root: python3 tools/gen_hide_rugs.py
"""
import json
import os
from PIL import Image

ROOT = 'kitchen/src/main/resources'
A = ROOT + '/assets/jaymoskitchen'
BASE = A + '/textures/block/hide_rug.png'

# animal: (main coat, second colour, pattern)
COATS = {
    'cow': ((92, 62, 40), (226, 220, 210), 'patches'),
    'mooshroom': ((150, 34, 30), (222, 214, 206), 'patches'),
    'horse': ((122, 78, 44), None, None),
    'donkey': ((128, 118, 108), None, None),
    'mule': ((82, 54, 36), None, None),
    'llama': ((214, 198, 160), None, None),
    'camel': ((206, 162, 98), None, None),
    'goat': ((226, 220, 204), None, None),
    'pig': ((228, 160, 150), None, None),
    'sheep': ((226, 214, 186), None, None),
    'hoglin': ((170, 104, 82), (110, 70, 52), 'bristle'),
    'zebra': ((232, 230, 226), (34, 32, 32), 'stripes'),
    'deer': ((170, 112, 62), (236, 226, 206), 'spots'),
    'rhino': ((132, 130, 128), None, None),
    'alligator': ((70, 86, 52), (44, 54, 34), 'scales'),
}


def pattern(kind, x, y):
    if kind == 'patches':
        return ((x * 7 + y * 3) % 11 < 4) and ((x // 4 + y // 5) % 2 == 0)
    if kind == 'stripes':
        return (x + y // 2) % 4 < 2
    if kind == 'spots':
        return (x * 5 + y * 7) % 13 == 0
    if kind == 'bristle':
        return (x * 3 + y) % 5 == 0
    if kind == 'scales':
        return (x % 4 == 0) or ((y + (x // 4) * 2) % 4 == 0)
    return False


def main():
    base = Image.open(BASE).convert('RGBA')
    w, h = base.size
    lum = [[sum(base.getpixel((x, y))[:3]) / 3.0 for x in range(w)] for y in range(h)]
    mean = sum(sum(row) for row in lum) / (w * h)
    for animal, (main_c, second, kind) in COATS.items():
        out = Image.new('RGBA', (w, h))
        for y in range(h):
            for x in range(w):
                shade = max(0.45, min(1.35, lum[y][x] / mean))
                colour = second if (kind and second and pattern(kind, x, y)) else main_c
                out.putpixel((x, y), tuple(max(0, min(255, int(c * shade))) for c in colour) + (255,))
        name = animal + '_hide_rug'
        out.save(A + '/textures/block/' + name + '.png')
        write(A + '/blockstates/' + name + '.json', {'variants': {'': {'model': 'jaymoskitchen:block/' + name}}})
        write(A + '/models/block/' + name + '.json', {'parent': 'minecraft:block/carpet', 'textures': {'wool': 'jaymoskitchen:block/' + name}})
        write(A + '/items/' + name + '.json', {'model': {'type': 'minecraft:model', 'model': 'jaymoskitchen:block/' + name}})
        write(ROOT + '/data/jaymoskitchen/loot_table/blocks/' + name + '.json', {
            'type': 'minecraft:block',
            'pools': [{'condition': {'type': 'minecraft:survives_explosion'},
                       'entries': [{'type': 'minecraft:item', 'name': 'jaymoskitchen:' + name}], 'rolls': 1}],
            'random_sequence': 'jaymoskitchen:blocks/' + name})


def write(path, data):
    with open(path, 'w') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


if __name__ == '__main__':
    main()
