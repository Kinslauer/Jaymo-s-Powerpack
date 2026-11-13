"""The spit's look (cooking/spit/SpitBlock): the forked posts and crossbar of the camping_campfire Blockbench model Andy
gave us (tools/bbmodels/camping-campfire.bbmodel, a Java block model), standing over whatever fire the spit sits on. The
spit is the block above the fire, so the frame is set down by DROP: its feet reach the ground beside the fire and its
crossbar runs through the low half of the spit's own block, where the roast turns. Each post is carried on down to the
ground by a short second piece of the same strip of bark. The model's own logs, stones and flames are left out: the
fire underneath has its own.
Run from kitchen/: python3 -I tools/gen_spit_frame.py   (writes models/block/spit.json and textures/block/spit_frame.png)"""
import base64, io, json, os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SOURCE = os.path.join(HERE, '..', '..', 'tools', 'bbmodels', 'camping-campfire.bbmodel')
ASSETS = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'jaymoskitchen')
# how far the frame is set down (sixteenths), and the lowest a model's cube may reach
DROP, FLOOR = 11.5, -16.0
# the frame's cubes in the file: taller than this is a post, wider than this the crossbar
POST_HEIGHT, BAR_WIDTH = 15.0, 20.0
FACES = ('north', 'east', 'south', 'west', 'up', 'down')


def element(e, lo, hi, v_cut=None):
    """One model element from cube {@code e}, its height replaced by lo..hi (file units); v_cut keeps only that many
    texels of its side faces' strips, from the top."""
    f, t = list(e['from']), list(e['to'])
    f[1], t[1] = lo, hi
    out = {'from': [round(f[0], 3), round(f[1] - DROP, 3), round(f[2], 3)],
           'to': [round(t[0], 3), round(t[1] - DROP, 3), round(t[2], 3)], 'faces': {}}
    rot = e.get('rotation') or [0, 0, 0]
    turned = [(axis, angle) for axis, angle in zip('xyz', rot) if angle]
    assert len(turned) <= 1, 'a block model cube turns about one axis'
    if turned:
        o = e['origin']
        out['rotation'] = {'origin': [round(o[0], 3), round(o[1] - DROP, 3), round(o[2], 3)], 'axis': turned[0][0], 'angle': turned[0][1]}
    for name in FACES:
        face = e['faces'].get(name)
        if not face or face.get('texture') is None:
            continue
        u0, v0, u1, v1 = face['uv']
        if v_cut is not None and name in ('north', 'east', 'south', 'west'):
            v1 = v0 + v_cut
        # the file's UVs are in texels of a 32-wide sheet; a model's run 0-16
        out['faces'][name] = {'uv': [u0 / 2.0, v0 / 2.0, u1 / 2.0, v1 / 2.0], 'texture': '#frame'}
    return out


def main():
    data = json.load(open(SOURCE))
    elements = []
    for e in data['elements']:
        height, width = e['to'][1] - e['from'][1], e['to'][0] - e['from'][0]
        if height > POST_HEIGHT:
            elements.append(element(e, e['from'][1], e['to'][1]))
            # on down to the ground
            foot = FLOOR + DROP
            elements.append(element(e, foot, e['from'][1], v_cut=e['from'][1] - foot))
        elif width > BAR_WIDTH:
            elements.append(element(e, e['from'][1], e['to'][1]))
    assert len(elements) == 9, len(elements)
    model = {'credit': 'From the camping_campfire Blockbench model; made by kitchen/tools/gen_spit_frame.py',
             'ambientocclusion': False,
             'textures': {'frame': 'jaymoskitchen:block/spit_frame', 'particle': 'jaymoskitchen:block/spit_frame'},
             'elements': elements}
    with open(os.path.join(ASSETS, 'models', 'block', 'spit.json'), 'w') as out:
        json.dump(model, out, indent=2)
        out.write('\n')
    sheet = Image.open(io.BytesIO(base64.b64decode(data['textures'][0]['source'].split(',', 1)[1]))).convert('RGBA')
    # (the sheet is three frames of flame, one under the other; the frame's own texels are the same in each)
    sheet.crop((0, 0, 32, 32)).save(os.path.join(ASSETS, 'textures', 'block', 'spit_frame.png'))
    bar = [e for e in data['elements'] if e['to'][0] - e['from'][0] > BAR_WIDTH][0]
    print('crossbar centre (blocks above the spit block\'s floor):', round(((bar['from'][1] + bar['to'][1]) / 2 - DROP) / 16, 3))


if __name__ == '__main__':
    main()
