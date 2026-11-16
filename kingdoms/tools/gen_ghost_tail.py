"""The grave ghost's wispy tail (Andy, 2026-11-16: "the ghosts should have wispy tails like wisp"): its two legs are taken
off the baked ghost model (bbmodels/grave_ghost.json) and one tapering tail of TAIL links hangs from the waist instead,
each link a bone under the one above, as Wisp's is (Powers' WispTail). Its sides read the skin's leg strip lower and
lower down, so the tail thins to nothing at the tip. GraveGhostTail poses it. Run from kingdoms/: python3 tools/gen_ghost_tail.py
(again after re-baking the model; it does nothing to a model that already has its tail)."""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
MODEL = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'jaymoskingdoms', 'bbmodels', 'grave_ghost.json')
# each link, waist to tip: width, length, depth (model pixels) and where its box UV starts on the skin (Powers'
# PlayerGhostRenderer builds the same tail on the same skin)
TAIL = [(8, 3, 4, 16, 16), (7, 3, 4, 16, 16), (6, 3, 3, 16, 16), (5, 3, 3, 16, 16),
        (4, 3, 2, 0, 20), (3, 3, 2, 0, 22), (2, 3, 1, 0, 26), (1, 4, 1, 0, 27)]
# each link starts this far up inside the one above
OVERLAP = 0.75
TEX = 64.0


def box(w, h, d, u, v):
    """A box hanging from its pivot (x and z centred), its six faces with box UVs, in the baked model's quad layout."""
    x0, x1, y0, y1, z0, z1 = -w / 2, w / 2, 0.0, float(h), -d / 2, d / 2
    faces = [
        ([(x0, y1, z0), (x1, y1, z0), (x1, y0, z0), (x0, y0, z0)], [(u + d, v + d + h), (u + d + w, v + d + h), (u + d + w, v + d), (u + d, v + d)], (0, 0, -1)),
        ([(x1, y1, z1), (x0, y1, z1), (x0, y0, z1), (x1, y0, z1)], [(u + 2 * d + w, v + d + h), (u + 2 * d + 2 * w, v + d + h), (u + 2 * d + 2 * w, v + d), (u + 2 * d + w, v + d)], (0, 0, 1)),
        ([(x0, y1, z1), (x0, y1, z0), (x0, y0, z0), (x0, y0, z1)], [(u, v + d + h), (u + d, v + d + h), (u + d, v + d), (u, v + d)], (-1, 0, 0)),
        ([(x1, y1, z0), (x1, y1, z1), (x1, y0, z1), (x1, y0, z0)], [(u + d + w, v + d + h), (u + 2 * d + w, v + d + h), (u + 2 * d + w, v + d), (u + d + w, v + d)], (1, 0, 0)),
        ([(x1, y0, z1), (x0, y0, z1), (x0, y0, z0), (x1, y0, z0)], [(u + d + w, v), (u + d, v), (u + d, v + d), (u + d + w, v + d)], (0, -1, 0)),
        ([(x1, y1, z0), (x0, y1, z0), (x0, y1, z1), (x1, y1, z1)], [(u + d + 2 * w, v + d), (u + d + w, v + d), (u + d + w, v), (u + d + 2 * w, v)], (0, 1, 0)),
    ]
    quads = []
    for verts, uvs, normal in faces:
        q = []
        for (x, y, z), (s, t) in zip(verts, uvs):
            q += [x, y, z, s / TEX, t / TEX]
        quads.append(q + list(normal))
    return quads


def main():
    with open(MODEL) as f:
        model = json.load(f)
    bones = model['bones']
    if any(b['name'].startswith('Tail') for b in bones):
        return
    legs = {i for i, b in enumerate(bones) if b['name'] in ('Right Leg', 'Left Leg')}
    assert legs and all(i > max(b['parent'] for j, b in enumerate(bones) if j not in legs) for i in legs), 'legs must be leaves at the end'
    bones = [b for i, b in enumerate(bones) if i not in legs]
    controller = next(i for i, b in enumerate(bones) if b['name'] == 'Controller')
    parent, above = controller, None
    for n, (w, h, d, u, v) in enumerate(TAIL):
        y = 0.0 if above is None else above - OVERLAP
        bones.append({'name': 'Tail %d' % n, 'parent': parent, 'pivot': [0, y, 0], 'rot': [0.0, 0.0, 0.0], 'quads': box(w, h, d, u, v)})
        parent, above = len(bones) - 1, h
    model['bones'] = bones
    for anim in model['anims'].values():
        anim['tracks'] = [t for t in anim['tracks'] if t['bone'] not in ('Right Leg', 'Left Leg')]
    with open(MODEL, 'w') as f:
        json.dump(model, f, separators=(',', ':'))


if __name__ == '__main__':
    main()
