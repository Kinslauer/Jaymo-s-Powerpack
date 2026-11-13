"""
The zombie knight's own head and empty sword hand, on yair's "Zombie Knight" (tools/bbmodels/zombie-knight.bbmodel,
CC BY 4.0). Andy: "this zombie knight needs a different head and should wield a sword from our pack, and correctly."

- Head: the closed helm with red eyes goes. In its place a long villager's head with its nose, grey-green and sunken (the
  old dead are villagers), under an open kettle helm: a cap and a brim. Painted here from nothing, in the model's own
  greys and greens, on the free lower part of its sheet.
- Sword: the three cubes of its sword go; the "sword" bone stays, empty, as the hand the game draws a real item in.

Writes kingdoms/art/zombie_knight.bbmodel, then bake it:
  python3 kingdoms/tools/gen_zombie_knight.py
  python3 -I tools/bbmodel_bake.py kingdoms/art/zombie_knight.bbmodel kingdoms/src/main/resources/assets/jaymoskingdoms zombie_knight
"""
import base64
import io
import json
import os
import random
import uuid

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, '..', '..', 'tools', 'bbmodels', 'zombie-knight.bbmodel')
OUT = os.path.join(HERE, '..', 'art', 'zombie_knight.bbmodel')

SKIN = [(107, 115, 93), (125, 132, 109), (142, 149, 124), (160, 166, 140)]
STEEL = [(88, 88, 85), (115, 115, 110), (141, 141, 135), (168, 168, 160)]
DARK, PALE, BROW, RUST, WOUND = (34, 30, 28), (176, 182, 158), (70, 76, 58), (150, 92, 62), (112, 58, 50)


def fill(px, rect, tones, rng, top_light=True):
    """A face of mottled tones, a little lighter towards the top."""
    x1, y1, x2, y2 = rect
    for y in range(y1, y2):
        for x in range(x1, x2):
            t = rng.choice([1, 1, 1, 2, 0]) + (1 if top_light and y == y1 else 0) - (1 if y == y2 - 1 else 0)
            px[x, y] = tones[max(0, min(len(tones) - 1, t))] + (255,)


def face(rect):
    return {'uv': list(rect), 'texture': 0}


def cube(name, frm, to, faces, inflate=None):
    e = {'name': name, 'box_uv': False, 'rescale': False, 'locked': False, 'render_order': 'default', 'allow_mirror_modeling': True,
         'from': frm, 'to': to, 'autouv': 0, 'color': 0, 'origin': [0, 24, 0], 'type': 'cube', 'uuid': str(uuid.UUID(int=random.getrandbits(128))),
         'faces': {k: face(v) for k, v in faces.items()}}
    if inflate:
        e['inflate'] = inflate
    return e


def main():
    random.seed(7)
    rng = random.Random(11)
    data = json.load(open(SRC))
    tex = data['textures'][0]
    img = Image.open(io.BytesIO(base64.b64decode(tex['source'].split(',', 1)[1]))).convert('RGBA')
    px = img.load()

    # ---- the sheet: head sides at y 56-66, its top and underside above them; the nose; the cap; the brim ----
    head = {'east': (0, 56, 8, 66), 'north': (8, 56, 16, 66), 'west': (16, 56, 24, 66), 'south': (24, 56, 32, 66),
            'up': (8, 48, 16, 56), 'down': (16, 48, 24, 56)}
    for r in head.values():
        fill(px, r, SKIN, rng)
    fx, fy = 8, 56  # the face: a heavy brow, sunken eyes with no light in them, a slack mouth, an old wound
    for x in range(1, 7):
        px[fx + x, fy + 3] = BROW + (255,)
    for x in (1, 2, 5, 6):
        px[fx + x, fy + 4] = DARK + (255,)
    px[fx + 2, fy + 4] = PALE + (255,)
    px[fx + 5, fy + 4] = PALE + (255,)
    for x in (1, 2, 5, 6):
        px[fx + x, fy + 5] = SKIN[0] + (255,)
    for x in range(2, 6):
        px[fx + x, fy + 8] = DARK + (255,)
    px[fx + 6, fy + 7] = WOUND + (255,)
    px[fx + 6, fy + 6] = WOUND + (255,)
    nose = {'north': (34, 58, 36, 62), 'east': (32, 58, 34, 62), 'west': (36, 58, 38, 62), 'south': (34, 58, 36, 62),
            'up': (34, 56, 36, 58), 'down': (36, 56, 38, 58)}
    for r in nose.values():
        fill(px, r, SKIN[1:], rng)
    cap = {'east': (0, 76, 8, 81), 'north': (8, 76, 16, 81), 'west': (16, 76, 24, 81), 'south': (24, 76, 32, 81),
           'up': (8, 68, 16, 76), 'down': (16, 68, 24, 76)}
    for r in cap.values():
        fill(px, r, STEEL, rng)
    for x in range(8, 16):  # a ridge over the crown, front to back
        if x in (11, 12):
            for y in range(68, 81):
                px[x, y] = STEEL[3] + (255,)
    brim = {'up': (40, 48, 52, 60), 'down': (52, 48, 64, 60), 'north': (40, 60, 52, 61), 'south': (40, 61, 52, 62),
            'east': (52, 60, 64, 61), 'west': (52, 61, 64, 62)}
    for k, r in brim.items():
        fill(px, r, STEEL[:3] if k == 'down' else STEEL, rng, top_light=False)
    for _ in range(26):  # rust
        r = rng.choice(list(cap.values()) + [brim['up']])
        px[rng.randrange(r[0], r[2]), rng.randrange(r[1], r[3])] = RUST + (255,)

    # ---- the cubes ----
    drop = {'head', 'helm', 'grip', 'guard', 'blade'}
    gone = {e['uuid'] for e in data['elements'] if e['name'] in drop}
    data['elements'] = [e for e in data['elements'] if e['uuid'] not in gone]
    new = [cube('head', [-4, 24, -4], [4, 34, 4], head), cube('nose', [-1, 25, -6], [1, 29, -4], nose),
           cube('helm_cap', [-4, 31.5, -4], [4, 35, 4], cap, inflate=0.6), cube('helm_brim', [-6, 31, -6], [6, 32, 6], brim)]
    data['elements'] += new

    def walk(nodes):
        for n in nodes:
            if isinstance(n, dict):
                n['children'] = [c for c in n.get('children', []) if isinstance(c, dict) or c not in gone]
                if n['name'] == 'hi_head':
                    n['children'] += [e['uuid'] for e in new]
                walk(n['children'])
    walk(data['outliner'])

    buf = io.BytesIO()
    img.save(buf, 'PNG')
    tex['source'] = 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()
    json.dump(data, open(OUT, 'w'))
    print('wrote', os.path.normpath(OUT))


if __name__ == '__main__':
    main()
