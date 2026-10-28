"""Makes the coach's item files for every wood: the 16x16 sprite, the item definition and model, and the crafting recipe.
(The lang entries are in lang/en_us.json; the 3D model is code, in NiftyCartModels.coach.)

Run from the repository root:  python -I kitchen/tools/gen_coach.py

The sprite is drawn on the Canvas of tools/vanilla_style (parts, outlined like vanilla icons). Its wood colours are
taken from that wood's existing animal cart sprite, so every coach matches the other carts of its wood.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, '..', '..'))
sys.path.insert(0, os.path.join(ROOT, 'tools', 'vanilla_style'))
from canvas import Canvas  # noqa: E402
from PIL import Image  # noqa: E402

RES = os.path.join(HERE, '..', 'src', 'main', 'resources')
SPRITES = os.path.join(RES, 'assets', 'jaymoskitchen', 'textures', 'item')
ITEMS = os.path.join(RES, 'assets', 'jaymoskitchen', 'items')
MODELS = os.path.join(RES, 'assets', 'jaymoskitchen', 'models', 'item')
RECIPES = os.path.join(RES, 'data', 'jaymoskitchen', 'recipe')

WOODS = ['oak', 'spruce', 'birch', 'acacia', 'cherry', 'jungle', 'dark_oak', 'pale_oak', 'crimson', 'warped',
         'mangrove', 'bamboo']
LOGS = {'crimson': 'crimson_stem', 'warped': 'warped_stem', 'bamboo': 'bamboo_block'}
# the greys of the cart sprites' iron and stone, which are not the wood
GREYS = {(81, 81, 81), (99, 99, 99), (113, 113, 113)}


def wood_ramp(wood):
    """Seven shades of the wood, darkest first, from its animal cart sprite."""
    sprite = Image.open(os.path.join(SPRITES, f'{wood}_animal_cart.png')).convert('RGBA')
    colours = {p[:3] for p in sprite.getdata() if p[3] and p[:3] not in GREYS}
    ordered = sorted(colours, key=lambda c: 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2])
    picks = [ordered[round(i * (len(ordered) - 1) / 6)] for i in range(7)]
    return [c for c in picks]


def coach(wood):
    c = Canvas()
    # far wheels, peeking out from under the bed
    c.new_part()
    c.grid(7, 12, ['.RR.', 'RggR', '.RR.'], {'R': ('w', 1), 'g': ('w', 2)})
    c.grid(13, 12, ['.RR.', 'RggR', '.RR.'], {'R': ('w', 1), 'g': ('w', 2)})
    # shafts out front
    c.new_part()
    c.line(3, 9, 0, 12, 'w', 5)
    c.line(4, 10, 1, 13, 'w', 3)
    # the three benches' backrests, standing in the bed
    for x in (3, 7, 11):
        c.new_part()
        c.rect(x, 4, x + 2, 7, 'w', 3)
        c.line(x, 4, x + 2, 4, 'w', 5)
        c.px(x + 1, 6, 'w', 2)
    # the long bed: a rim along the top, the plank side below it
    c.new_part()
    c.rect(2, 8, 15, 8, 'w', 6)
    c.rect(2, 9, 15, 11, 'w', 4)
    c.line(2, 10, 15, 10, 'w', 3)
    c.line(15, 8, 15, 11, 'w', 2)
    # near wheels
    for cx in (5, 11):
        c.new_part()
        c.grid(cx - 3, 9, ['..RRR..', '.RgSgR.', 'RgSSSgR', 'RSSoSSR', 'RgSSSgR', '.RgSgR.', '..RRR..'],
               {'R': ('w', 3), 'g': ('w', 1), 'S': ('w', 5), 'o': ('i', 2)})
    ramp = wood_ramp(wood)
    return c.finish({'w': ramp, 'i': [(60, 60, 60), (81, 81, 81), (99, 99, 99), (113, 113, 113)]})


def write(path, data):
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def main():
    for wood in WOODS:
        name = f'{wood}_coach'
        coach(wood).save(os.path.join(SPRITES, f'{name}.png'))
        write(os.path.join(ITEMS, f'{name}.json'), {'model': {'type': 'minecraft:model', 'model': f'jaymoskitchen:item/{name}'}})
        write(os.path.join(MODELS, f'{name}.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'jaymoskitchen:item/{name}'}})
        # four planks over a log, with four wheels at the corners of the lower two rows
        write(os.path.join(RECIPES, f'{name}.json'), {
            'type': 'minecraft:crafting_shaped', 'category': 'misc',
            'pattern': ['ppp', 'wlw', 'wpw'],
            'key': {'p': f'minecraft:{wood}_planks', 'l': f'minecraft:{LOGS.get(wood, wood + "_log")}', 'w': 'jaymoskitchen:wheel'},
            'result': {'id': f'jaymoskitchen:{name}'}})
    print(len(WOODS), 'coaches')


if __name__ == '__main__':
    main()
