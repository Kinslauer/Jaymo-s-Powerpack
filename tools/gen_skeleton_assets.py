#!/usr/bin/env python3
"""Kitchen's skeleton display blocks and items (butchery/Skeletons): the bone stand's and beetle box's block models and
textures, the frame bones' item art, loot tables and recipes. Run from the repo root: python3 tools/gen_skeleton_assets.py
"""
import json
import os
from PIL import Image

R = 'kitchen/src/main/resources'
A = R + '/assets/jaymoskitchen'
D = R + '/data/jaymoskitchen'


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def bones_icon(path, bone, shade, gore):
    """A bundle of long bones, crossed, with knuckle ends."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    def px(x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            img.putpixel((x, y), c)
    for i in range(10):
        # two bones crossed in an X, and a rib curve
        px(3 + i, 12 - i, bone)
        px(3 + i, 13 - i, shade)
        px(3 + i, 3 + i, bone)
        px(4 + i, 3 + i, shade)
    for (x, y) in ((2, 12), (3, 13), (2, 2), (3, 2), (12, 2), (13, 3), (12, 13), (13, 12)):
        px(x, y, bone)
        px(x + 1, y, bone)
        px(x, y + 1, shade)
    for x in range(5, 11):
        px(x, 14 - abs(x - 8) // 2, shade)
    if gore:
        for (x, y) in ((6, 9), (9, 6), (7, 7), (10, 10), (5, 5)):
            px(x, y, gore)
    img.save(path)


def box_textures():
    wood, dark, gap = (110, 82, 52, 255), (82, 60, 38, 255), (34, 24, 16, 255)
    side = Image.new('RGBA', (16, 16), wood)
    top = Image.new('RGBA', (16, 16), wood)
    for y in range(16):
        for x in range(16):
            if y % 5 == 0 or x in (0, 15):
                side.putpixel((x, y), dark)
            if x in (0, 15) or y in (0, 15):
                top.putpixel((x, y), dark)
    for y in (2, 7, 12):  # air slots in the sides
        for x in range(4, 12):
            side.putpixel((x, y), gap)
    for y in range(3, 13, 2):  # a mesh lid
        for x in range(3, 13, 2):
            top.putpixel((x, y), gap)
    side.save(A + '/textures/block/beetle_box_side.png')
    top.save(A + '/textures/block/beetle_box_top.png')


def main():
    os.makedirs(A + '/textures/item', exist_ok=True)
    bones_icon(A + '/textures/item/frame_bones.png', (214, 196, 150, 255), (160, 140, 96, 255), (130, 40, 36, 255))
    bones_icon(A + '/textures/item/cleaned_bones.png', (240, 236, 224, 255), (196, 190, 176, 255), None)
    for name in ('frame_bones', 'cleaned_bones'):
        write(A + f'/models/item/{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'jaymoskitchen:item/{name}'}})
        write(A + f'/items/{name}.json', {'model': {'type': 'minecraft:model', 'model': f'jaymoskitchen:item/{name}'}})

    # the bone stand: an oak plinth with an iron collar the rod stands in
    write(A + '/models/block/bone_stand.json', {
        'parent': 'minecraft:block/block',
        'textures': {'particle': 'minecraft:block/oak_planks', 'wood': 'minecraft:block/oak_planks', 'iron': 'minecraft:block/iron_block'},
        'elements': [
            {'from': [1, 0, 1], 'to': [15, 3, 15], 'faces': {f: {'texture': '#wood'} for f in ('north', 'south', 'east', 'west', 'up', 'down')}},
            {'from': [2, 3, 2], 'to': [14, 4, 14], 'faces': {f: {'texture': '#wood'} for f in ('north', 'south', 'east', 'west', 'up')}},
            {'from': [7, 4, 7], 'to': [9, 5, 9], 'faces': {f: {'texture': '#iron'} for f in ('north', 'south', 'east', 'west', 'up')}},
        ]})
    write(A + '/blockstates/bone_stand.json', {'variants': {
        f'facing={d}': ({'model': 'jaymoskitchen:block/bone_stand', 'y': y} if y else {'model': 'jaymoskitchen:block/bone_stand'})
        for d, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270))}})
    write(A + '/items/bone_stand.json', {'model': {'type': 'minecraft:model', 'model': 'jaymoskitchen:block/bone_stand'}})

    box_textures()
    write(A + '/models/block/beetle_box.json', {'parent': 'minecraft:block/cube_bottom_top', 'textures': {
        'top': 'jaymoskitchen:block/beetle_box_top', 'bottom': 'minecraft:block/spruce_planks', 'side': 'jaymoskitchen:block/beetle_box_side'}})
    write(A + '/blockstates/beetle_box.json', {'variants': {'': {'model': 'jaymoskitchen:block/beetle_box'}}})
    write(A + '/items/beetle_box.json', {'model': {'type': 'minecraft:model', 'model': 'jaymoskitchen:block/beetle_box'}})

    for name in ('bone_stand', 'beetle_box'):
        write(D + f'/loot_table/blocks/{name}.json', {'type': 'minecraft:block', 'pools': [{
            'condition': {'type': 'minecraft:survives_explosion'}, 'entries': [{'type': 'minecraft:item', 'name': f'jaymoskitchen:{name}'}],
            'rolls': 1}], 'random_sequence': f'jaymoskitchen:blocks/{name}'})
    # recipes: the stand also comes from the Tool Station in Jaymo's Powers (a wooden plate and a metal handle), which
    # takes its grid recipe away when Powers is in
    write(D + '/recipe/bone_stand.json', {'type': 'minecraft:crafting_shaped', 'category': 'building', 'pattern': [' I ', 'SSS'],
        'key': {'I': 'minecraft:iron_ingot', 'S': 'minecraft:oak_slab'}, 'result': {'id': 'jaymoskitchen:bone_stand', 'count': 1}})
    write(D + '/recipe/beetle_box.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['PSP', 'PRP', 'PPP'],
        'key': {'P': '#minecraft:planks', 'S': '#minecraft:wooden_slabs', 'R': 'minecraft:rotten_flesh'},
        'result': {'id': 'jaymoskitchen:beetle_box', 'count': 1}})


if __name__ == '__main__':
    main()
