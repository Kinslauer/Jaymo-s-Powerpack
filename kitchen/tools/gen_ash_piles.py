"""Ash where a burnt corpse lay (corpse/AshPileBlock): heaped mound models for 1-8 layers (Andy: "they should resemble
mounded piles ... like the entrails, not sheets, until it starts to blow away and flatten out"), a turned copy of each,
and the blockstate (blown=false: the mounds, any of four turns; blown=true: the flat sheets, ash_pile_1..8, as before).
Run from the repo root: python3 -I kitchen/tools/gen_ash_piles.py"""
import json, os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources')
ASSETS = os.path.join(ROOT, 'assets', 'jaymoskitchen')


def write(rel, data):
    out = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, 'w', newline='\n') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def box(x0, y0, z0, x1, y1, z1, rotation=None):
    faces = {}
    for face in ('north', 'south', 'east', 'west'):
        w = (x1 - x0) if face in ('north', 'south') else (z1 - z0)
        faces[face] = {'texture': '#all', 'uv': [0, 16 - (y1 - y0), w, 16]}
    faces['up'] = {'texture': '#all', 'uv': [x0, z0, x1, z1]}
    faces['down'] = {'texture': '#all', 'uv': [x0, z0, x1, z1], 'cullface': 'down'}
    out = {'from': [x0, y0, z0], 'to': [x1, y1, z1], 'faces': faces}
    if rotation:
        out['rotation'] = rotation
    return out


def heap(cx, cz, radius, height, steps):
    """A heap: boxes stacked narrower as they go up, centred on (cx, cz)."""
    out = []
    step = height / steps
    for k in range(steps):
        r = radius * (1.0 - k / (steps + 0.5))
        x0, x1 = max(0.0, cx - r), min(16.0, cx + r)
        z0, z1 = max(0.0, cz - r * 0.85), min(16.0, cz + r * 0.85)
        out.append(box(round(x0, 2), round(k * step, 2), round(z0, 2), round(x1, 2), round((k + 1) * step, 2), round(z1, 2)))
    return out


# the main heap grows with the layers; from 3 layers up more little heaps sit round it
SIDE_HEAPS = [(4.5, 11.0, 2.0), (11.5, 4.5, 2.2), (12.0, 11.5, 1.8), (4.0, 4.5, 1.6)]


def mound(n):
    radius = 3.0 + n * 0.55
    height = min(12.0, 1.5 + n * 1.3)
    elements = heap(8.0, 8.0, radius, height, 2 + n // 2)
    for (x, z, r) in SIDE_HEAPS[:max(0, (n - 1) // 2)]:
        elements += heap(x, z, r + n * 0.15, 1.0 + n * 0.35, 2)
    return elements


def model(elements, turned):
    if turned:
        rot = {'origin': [8, 0, 8], 'axis': 'y', 'angle': 45}
        elements = [dict(e, rotation=rot) for e in elements]
    return {'parent': 'minecraft:block/block', 'ambientocclusion': False,
            'textures': {'all': 'jaymoskitchen:block/ash_pile', 'particle': 'jaymoskitchen:block/ash_pile'},
            'elements': elements}


variants = {}
for n in range(1, 9):
    write(f'models/block/ash_mound_{n}.json', model(mound(n), False))
    write(f'models/block/ash_mound_{n}_turned.json', model(mound(n), True))
    variants[f'blown=false,layers={n}'] = [{'model': f'jaymoskitchen:block/ash_mound_{n}{t}', 'y': y}
                                          for t in ('', '_turned') for y in (0, 90, 180, 270)]
    variants[f'blown=true,layers={n}'] = {'model': f'jaymoskitchen:block/ash_pile_{n}'}
write('blockstates/ash_pile.json', {'variants': variants})
