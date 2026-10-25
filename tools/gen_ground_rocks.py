"""Ground rocks and loose flint (com.jaymospowers.ground): blockstates, models, item definitions, loot tables, the
4-rocks-to-a-block recipes and the worldgen features. Run from the repo root: python -I tools/gen_ground_rocks.py

The rocks reuse vanilla block textures (no new art). Keep VARIANTS in step with GroundRocks.Variant.
"""
import json
import os

NS = 'jaymospowers'
ROOT = os.path.join('src', 'main', 'resources')
ASSETS = os.path.join(ROOT, 'assets', NS)
DATA = os.path.join(ROOT, 'data', NS)

# (the rocks) id, texture (vanilla block texture), what 4 rocks craft into
VARIANTS = [
    ('stone', 'stone', 'cobblestone'),
    ('mossy', 'mossy_cobblestone', 'mossy_cobblestone'),
    ('granite', 'granite', 'granite'),
    ('diorite', 'diorite', 'diorite'),
    ('andesite', 'andesite', 'andesite'),
    ('deepslate', 'cobbled_deepslate', 'cobbled_deepslate'),
    ('tuff', 'tuff', 'tuff'),
    ('sandstone', 'sandstone_top', 'sandstone'),
    ('red_sandstone', 'red_sandstone_top', 'red_sandstone'),
]
FLINT_TEXTURE = 'minecraft:block/smooth_basalt'
# (0.16.24) rocks lying in water are salt crystals; picked up, they're Kitchen's salt
SALT_TEXTURE = 'minecraft:block/calcite'

# pebbles per pile size, [from, to] in model units; each size adds one to the last (like candles)
ROCKS = [
    ([5.5, 0, 6], [10, 3, 10.5]),
    ([9.5, 0, 9.5], [13, 2, 13]),
    ([3, 0, 10], [6.5, 1.5, 13]),
    ([3.5, 0, 3], [7, 2.5, 6]),
]
FLINTS = [
    ([6, 0, 5.5], [10, 1, 9]),
    ([9.5, 0, 9.5], [12.5, 1.5, 12]),
    ([4, 0, 10], [7, 1, 13]),
]
# pebbles are tiny: blow them up in the inventory and the hand
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
                {'model': f'{NS}:block/ground/{model}_{n}', **({'y': y} if y else {})} for y in (0, 90, 180, 270)]
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
                # (0.16.24) 26.3's format, as vanilla's candles (the old "functions" key was silently ignored: a
                # pile always dropped one)
                'modifier': [{
                    'type': 'minecraft:set_count',
                    'condition': {'type': 'minecraft:match_block', 'blocks': f'{NS}:{block}', 'state': {prop: str(n)}},
                    'count': n,
                } for n in range(2, count + 1)] + [{'type': 'minecraft:explosion_decay'}],
            }],
        }],
        'random_sequence': f'{NS}:blocks/{block}',
    })


def main():
    templates('rocks', ROCKS)
    templates('flints', FLINTS)
    for vid, tex, result in VARIANTS:
        block = f'{vid}_rock'
        blockstate(block, 'rocks', len(ROCKS), block)
        models(block, 'rocks', len(ROCKS), f'minecraft:block/{tex}')
        write(os.path.join(ASSETS, 'items', f'{block}.json'),
              {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/ground/{block}_1'}})
        loot(block, f'{NS}:{block}', 'rocks', len(ROCKS))
        write(os.path.join(DATA, 'recipe', f'{result}_from_{block}s.json'), {
            'type': 'minecraft:crafting_shaped',
            'category': 'building',
            'group': 'rocks_to_block',
            'pattern': ['##', '##'],
            'key': {'#': f'{NS}:{block}'},
            'result': {'id': f'minecraft:{result}', 'count': 1},
        })
    blockstate('loose_flint', 'flints', len(FLINTS), 'loose_flint')
    models('loose_flint', 'flints', len(FLINTS), FLINT_TEXTURE)
    loot('loose_flint', 'minecraft:flint', 'flints', len(FLINTS))
    blockstate('salt_crystals', 'rocks', len(ROCKS), 'salt_crystals')
    models('salt_crystals', 'rocks', len(ROCKS), SALT_TEXTURE)
    loot('salt_crystals', 'jaymoskitchen:salt', 'rocks', len(ROCKS))

    # worldgen: the features pick the stone and the pile size themselves (GroundRocksFeature)
    for fid in ('loose_rocks', 'loose_flint'):
        write(os.path.join(DATA, 'worldgen', 'feature', f'{fid}.json'), {'type': f'{NS}:{fid}'})
    surface = [{'type': 'minecraft:in_square'},
               {'type': 'minecraft:heightmap', 'heightmap': 'OCEAN_FLOOR_WG'},
               {'type': 'minecraft:biome'}]
    write(os.path.join(DATA, 'worldgen', 'placed_feature', 'loose_rocks.json'), {
        'feature': f'{NS}:loose_rocks',
        'placement': [{'type': 'minecraft:count', 'count': 5}] + surface,
    })
    write(os.path.join(DATA, 'worldgen', 'placed_feature', 'loose_flint.json'), {
        'feature': f'{NS}:loose_flint',
        'placement': [{'type': 'minecraft:count', 'count': 2}] + surface,
    })
    write(os.path.join(DATA, 'worldgen', 'placed_feature', 'loose_rocks_cave.json'), {
        'feature': f'{NS}:loose_rocks',
        'placement': [
            {'type': 'minecraft:count', 'count': 24},
            {'type': 'minecraft:in_square'},
            {'type': 'minecraft:height_range', 'height': {
                'type': 'minecraft:uniform', 'min_inclusive': {'above_bottom': 8}, 'max_inclusive': {'absolute': 60}}},
            {'type': 'minecraft:environment_scan', 'direction_of_search': 'down', 'max_steps': 12,
             'allowed_search_condition': {'type': 'minecraft:matching_block_tag', 'tag': 'minecraft:air'},
             'target_condition': {'type': 'minecraft:has_sturdy_face', 'direction': 'up'}},
            {'type': 'minecraft:offset', 'x': 0, 'y': 1, 'z': 0},
            {'type': 'minecraft:biome'},
        ],
    })



# ---------------------------------------------------------------- RLCraft's start (FirstTools)

def sprite(name, pixels, palette):
    """A 16x16 item sprite from rows of palette letters ('.' is clear)."""
    from PIL import Image
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(pixels):
        for x, c in enumerate(row):
            if c != '.':
                img.putpixel((x, y), palette[c])
    path = os.path.join(ASSETS, 'textures', 'item', 'ground', f'{name}.png')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


SHARD = [
    '................',
    '................',
    '................',
    '..........a.....',
    '.........abb....',
    '........abbcb...',
    '.......abbbcb...',
    '......abbcbbc...',
    '.....abbbbcc....',
    '....abbcbbc.....',
    '....abbbbc......',
    '...abbbcc.......',
    '...accc.........',
    '................',
    '................',
    '................',
]
FIBRE = [
    '................',
    '..........a.....',
    '.....a....b.....',
    '.....b...ba.....',
    '......b..b......',
    '......b.ba..a...',
    '.....ab.b..ab...',
    '.....b.bb.ab....',
    '......bcb.b.....',
    '......bbccb.....',
    '.......cbb......',
    '.......cb.......',
    '......cb........',
    '......c.........',
    '................',
    '................',
]
STRING = [
    '................',
    '................',
    '.........aab....',
    '........a...b...',
    '.......a....b...',
    '......abb..b....',
    '.....a..bbb.....',
    '....a...........',
    '...a..aab.......',
    '..a..a...b......',
    '..a.a....b......',
    '...a....b.......',
    '....bbbb........',
    '................',
    '................',
    '................',
]


def first_tools():
    sprite('flint_shard', SHARD, {'a': (150, 150, 158, 255), 'b': (62, 62, 68, 255), 'c': (36, 36, 40, 255)})
    sprite('plant_fiber', FIBRE, {'a': (150, 196, 92, 255), 'b': (96, 148, 56, 255), 'c': (70, 108, 40, 255)})
    sprite('plant_string', STRING, {'a': (196, 186, 120, 255), 'b': (150, 140, 84, 255)})
    for item in ('flint_shard', 'plant_fiber', 'plant_string'):
        write(os.path.join(ASSETS, 'models', 'item', 'ground', f'{item}.json'),
              {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/ground/{item}'}})
        write(os.path.join(ASSETS, 'items', f'{item}.json'),
              {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/ground/{item}'}})
    write(os.path.join(DATA, 'recipe', 'ground', 'plant_string.json'), {
        'type': 'minecraft:crafting_shapeless', 'category': 'misc',
        'ingredients': [f'{NS}:plant_fiber'] * 3,
        'result': {'id': f'{NS}:plant_string', 'count': 1},
    })
    # (0.16.24) Andy: "plant fiber should craft string with 8 in a crafting square"
    write(os.path.join(DATA, 'recipe', 'ground', 'string_from_plant_fiber.json'), {
        'type': 'minecraft:crafting_shaped', 'category': 'misc',
        'pattern': ['###', '# #', '###'], 'key': {'#': f'{NS}:plant_fiber'},
        'result': {'id': 'minecraft:string', 'count': 1},
    })
    write(os.path.join(DATA, 'recipe', 'ground', 'white_wool_from_plant_string.json'), {
        'type': 'minecraft:crafting_shaped', 'category': 'building',
        'pattern': ['##', '##'], 'key': {'#': f'{NS}:plant_string'},
        'result': {'id': 'minecraft:white_wool', 'count': 1},
    })
    write(os.path.join(DATA, 'tags', 'item', 'fiber_knives.json'), {'values': [
        f'{NS}:primitive_knife', f'{NS}:bone_knife',
        {'id': '#c:tools/knife', 'required': False}]})
    write(os.path.join(DATA, 'tags', 'block', 'fiber_plants.json'), {'values': [
        'minecraft:short_grass', 'minecraft:tall_grass', 'minecraft:fern', 'minecraft:large_fern',
        'minecraft:short_dry_grass', 'minecraft:tall_dry_grass', 'minecraft:bush']})


if __name__ == '__main__':
    main()
    first_tools()
