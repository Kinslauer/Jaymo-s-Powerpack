"""The great spit's look (cooking/spit/GreatSpitBlock) and the spit's 3D roasts (SpitBlockEntityRenderer).

The great spit is the plain spit's frame (models/block/spit.json, made by gen_spit_frame.py) built wide for a whole beast:
its two A-frames stand SPREAD sixteenths further out each way and the crossbar runs on out to them, at the same height.
Its icon is the spit's, the bar drawn longer.

A piece of meat on either spit (Andy, 2026-11-19: "maybe animate meat shanks as 3d models for this spit", "mutton should
be a shank, they could put maybe 5 steaks on that thing") is drawn in 3D: mutton (and a leg of chicken or rabbit) as a
shank, a lump of meat round a bone whose ends stick out, the bone along the bar; anything else as a steak, a thick slab
with a rim of fat and a bit of bone, skewered through. Four textures, raw, browning, cooked and charred, one model each
of each look, picked by doneness (items/spit_shank.json, items/spit_steak.json).
Run from kitchen/: python3 -I tools/gen_great_spit.py
"""
import json, os, random
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'jaymoskitchen')
SPREAD = 8.0


def shift(e, dx):
    e = json.loads(json.dumps(e))
    e['from'][0] = round(e['from'][0] + dx, 3)
    e['to'][0] = round(e['to'][0] + dx, 3)
    if 'rotation' in e:
        e['rotation']['origin'][0] = round(e['rotation']['origin'][0] + dx, 3)
    return e


def great_spit():
    spit = json.load(open(os.path.join(ASSETS, 'models', 'block', 'spit.json')))
    out = []
    for e in spit['elements']:
        width = e['to'][0] - e['from'][0]
        if width > 16:
            # the crossbar, out to the spread frames
            e = json.loads(json.dumps(e))
            e['from'][0] = round(e['from'][0] - SPREAD, 3)
            e['to'][0] = round(e['to'][0] + SPREAD, 3)
            e['rotation']['origin'][0] = round(e['rotation']['origin'][0] - SPREAD, 3)
            out.append(e)
        elif e['from'][0] < 8:
            out.append(shift(e, -SPREAD))
        else:
            out.append(shift(e, SPREAD))
    for e in out:
        assert -16 <= e['from'][0] and e['to'][0] <= 32, e
    model = {'credit': 'The spit frame built wide; made by kitchen/tools/gen_great_spit.py',
             'ambientocclusion': False, 'textures': spit['textures'], 'elements': out}
    with open(os.path.join(ASSETS, 'models', 'block', 'great_spit.json'), 'w') as f:
        json.dump(model, f, indent=2)
        f.write('\n')
    # the bar along z: the same model turned a quarter
    with open(os.path.join(ASSETS, 'blockstates', 'great_spit.json'), 'w') as f:
        json.dump({'variants': {'axis=x': {'model': 'jaymoskitchen:block/great_spit'},
                                'axis=z': {'model': 'jaymoskitchen:block/great_spit', 'y': 90}}}, f, indent=2)
        f.write('\n')
    # icon: the spit's, its bar drawn on to the edges
    icon = Image.open(os.path.join(ASSETS, 'textures', 'item', 'spit.png')).convert('RGBA')
    big = Image.new('RGBA', (16, 16))
    # posts pushed out to the edges, the middle stretched between them
    big.paste(icon.crop((0, 0, 5, 16)), (0, 0))
    big.paste(icon.crop((11, 0, 16, 16)), (11, 0))
    mid = icon.crop((5, 0, 11, 16)).resize((6, 16), Image.NEAREST)
    big.paste(mid, (5, 0))
    big.save(os.path.join(ASSETS, 'textures', 'item', 'great_spit.png'))
    json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jaymoskitchen:item/great_spit'}},
              open(os.path.join(ASSETS, 'models', 'item', 'great_spit.json'), 'w'), indent=2)
    json.dump({'model': {'type': 'minecraft:model', 'model': 'jaymoskitchen:item/great_spit'}},
              open(os.path.join(ASSETS, 'items', 'great_spit.json'), 'w'), indent=2)


# the shank's sheet: meat in the top-left 12x12, the bone's cream in the top-right 4x4
STAGES = {
    'raw': ((176, 58, 62), (138, 36, 44), (226, 196, 176)),
    'browning': ((178, 98, 66), (140, 70, 46), (214, 170, 120)),
    'cooked': ((150, 84, 38), (112, 58, 26), (208, 150, 70)),
    'charred': ((58, 36, 26), (34, 22, 16), (92, 58, 34)),
}
BONE = ((232, 222, 196), (204, 190, 160))


def sheet(stage):
    meat, dark, fat = STAGES[stage]
    rnd = random.Random(7)
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(12):
        for x in range(12):
            r = rnd.random()
            c = fat if (y % 4 == 1 and r < 0.45) else dark if r < 0.3 else meat
            im.putpixel((x, y), c + (255,))
    for y in range(4):
        for x in range(12, 16):
            im.putpixel((x, y), (BONE[1] if (x + y) % 3 == 0 else BONE[0]) + (255,))
    # the fat, for a steak's rim
    for y in range(4, 8):
        for x in range(12, 16):
            im.putpixel((x, y), (fat if (x * 3 + y) % 4 else meat) + (255,))
    im.save(os.path.join(ASSETS, 'textures', 'block', 'spit_shank_%s.png' % stage))


UV = {'meat': [0, 0, 12, 12], 'bone': [12, 0, 16, 4], 'fat': [12, 4, 16, 8]}


def box(f, t, uv_meat=True, part=None):
    faces = {}
    for name in ('north', 'east', 'south', 'west', 'up', 'down'):
        faces[name] = {'uv': UV[part or ('meat' if uv_meat else 'bone')], 'texture': '#shank'}
    return {'from': f, 'to': t, 'faces': faces}


def write(look, elements):
    for stage in STAGES:
        model = {'credit': 'Made by kitchen/tools/gen_great_spit.py',
                 'textures': {'shank': 'jaymoskitchen:block/spit_shank_%s' % stage, 'particle': 'jaymoskitchen:block/spit_shank_%s' % stage},
                 'elements': elements,
                 'display': {'fixed': {'scale': [0.7, 0.7, 0.7]}, 'gui': {'rotation': [30, 45, 0], 'scale': [0.8, 0.8, 0.8]}}}
        with open(os.path.join(ASSETS, 'models', 'item', 'spit_%s_%s.json' % (look, stage)), 'w') as f:
            json.dump(model, f, indent=2)
            f.write('\n')
    # picked by doneness, carried in the stack's custom model data (0 raw, 1 cooked, 2 burnt)
    entries = [{'threshold': 0.5, 'model': {'type': 'minecraft:model', 'model': 'jaymoskitchen:item/spit_%s_browning' % look}},
               {'threshold': 1.0, 'model': {'type': 'minecraft:model', 'model': 'jaymoskitchen:item/spit_%s_cooked' % look}},
               {'threshold': 1.6, 'model': {'type': 'minecraft:model', 'model': 'jaymoskitchen:item/spit_%s_charred' % look}}]
    items = {'model': {'type': 'minecraft:range_dispatch', 'property': 'minecraft:custom_model_data', 'index': 0,
                       'entries': entries,
                       'fallback': {'type': 'minecraft:model', 'model': 'jaymoskitchen:item/spit_%s_raw' % look}}}
    with open(os.path.join(ASSETS, 'items', 'spit_%s.json' % look), 'w') as f:
        json.dump(items, f, indent=2)
        f.write('\n')


def steak():
    write('steak', [
        box([2, 6.5, 3], [14, 9.5, 13]),                # the slab, skewered through its middle
        box([2, 6.5, 13], [14, 9.5, 14.5], part='fat'),  # its rim of fat
        box([3, 6.5, 2], [13, 9.5, 3], part='fat'),
        box([7.5, 6.4, 4], [8.5, 9.6, 12], False),      # a bit of bone through it
    ])


def shank():
    for stage in STAGES:
        sheet(stage)
    write('shank', [
        box([1, 7, 7], [15, 9, 9], False),   # the bone, its ends out of the meat
        box([4, 5, 5], [12, 11, 11]),        # the meat
        box([5, 4, 6], [11, 12, 10]),        # rounded top and bottom
        box([5, 6, 4], [11, 10, 12]),        # rounded front and back
        box([0, 6.5, 6.5], [1.5, 9.5, 9.5], False),  # the bone's knuckles
        box([14.5, 6.5, 6.5], [16, 9.5, 9.5], False),
    ])


if __name__ == '__main__':
    great_spit()
    shank()
    steak()
