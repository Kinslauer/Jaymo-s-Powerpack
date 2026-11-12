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

# One stick per pile size, each size adds one to the last (like candles). A stick is a thin rod lying along its
# own z axis, turned about y by `angle` (model rotations only take -45..45 in 22.5 steps). Sticks that cross get
# different heights so no two top faces sit in the same plane. `twig` = (offset along the stick, side -1/+1,
# length): a thin side shoot that leaves the stick 45 degrees off its line.
# Fields: centre x, centre z, length, width, height, angle, bark row, twig or None
STICKS = [
    (8.0, 8.5, 11.0, 1.25, 1.25, 22.5, 2, (1.5, -1, 3.0)),
    (8.5, 7.0, 9.0, 1.0, 1.0, -45.0, 4, None),
    (4.5, 9.0, 7.0, 1.0, 0.85, -22.5, 6, (-1.0, 1, 2.0)),
    (12.5, 8.5, 6.0, 0.9, 0.7, 0.0, 8, None),
]

TEX = 'jaymospowers:block/ground/loose_sticks'
FIREPIT_MAX_FUEL = 15  # FirepitBlock.MAX_FUEL
BONFIRE_MAX_FUEL = 63  # BonfireBlock.MAX_FUEL

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


def rod(x0, z0, x1, z1, height, angle, origin, row, ends, top_row=None):
    """A rod from (x0, z0) to (x1, z1), long along z, turned `angle` about `origin`. Sides and top take a
    lengthwise bark strip from texture row `row` (top faces one row lighter); `ends` = uv patch for end grain."""
    length = z1 - z0
    width = x1 - x0
    top = row - 1 if top_row is None else top_row
    u0 = (16 - length) / 2  # centre the strip so short sticks show the middle of the bark
    side = [u0, row, u0 + length, row + height]
    faces = {
        'up': {'uv': [u0, top, u0 + length, top + width], 'rotation': 90, 'texture': '#all'},
        'down': {'uv': [u0, 11, u0 + length, 11 + width], 'rotation': 90, 'texture': '#all', 'cullface': 'down'},
        'west': {'uv': side, 'texture': '#all'},
        'east': {'uv': [side[2], side[1], side[0], side[3]], 'texture': '#all'},
        'north': {'uv': ends, 'texture': '#all'},
        'south': {'uv': ends, 'texture': '#all'},
    }
    el = {'from': [x0, 0, z0], 'to': [x1, height, z1], 'faces': faces}
    if angle:
        el['rotation'] = {'angle': angle, 'axis': 'y', 'origin': origin}
    return el


def r3(v):
    return round(v, 4)


def stick_elements(stick):
    import math
    cx, cz, length, width, height, angle, row, twig = stick
    out = [rod(cx - width / 2, cz - length / 2, cx + width / 2, cz + length / 2, height, angle, [cx, 0, cz], row,
               [0.5, 12.5, 3.5, 15.5])]
    if twig:
        along, side, tlen = twig
        a = math.radians(angle)
        # point on the stick's side (a little inside it), local (side * (w/2 - 0.2), along) turned like the stick
        lx, lz = side * (width / 2 - 0.2), along
        px = cx + lx * math.cos(a) + lz * math.sin(a)
        pz = cz - lx * math.sin(a) + lz * math.cos(a)
        tw = 0.5
        th = min(0.6, height - 0.2)
        out.append(rod(r3(px - tw / 2), r3(pz), r3(px + tw / 2), r3(pz + tlen), th, angle + 45 * side,
                       [r3(px), 0, r3(pz)], 10, [4.5, 12.5, 5.5, 13.5], top_row=9))
    return out


def stick_bounds(n):
    """Selection box around the first n sticks, rounded out to half pixels (LooseSticksBlock.STICK_SHAPES)."""
    import math
    xs, zs, ys = [], [], []
    for cx, cz, length, width, height, angle, _, _ in STICKS[:n]:
        a = math.radians(angle)
        for lx in (-width / 2, width / 2):
            for lz in (-length / 2, length / 2):
                xs.append(cx + lx * math.cos(a) + lz * math.sin(a))
                zs.append(cz - lx * math.sin(a) + lz * math.cos(a))
        ys.append(height)
    lo = lambda v: max(0.0, math.floor(min(v) * 2) / 2)
    hi = lambda v: min(16.0, math.ceil(max(v) * 2) / 2)
    return [lo(xs), 0.0, lo(zs)], [hi(xs), max(1.0, math.ceil(max(ys) * 2) / 2), hi(zs)]


def stick_templates():
    for n in range(1, len(STICKS) + 1):
        elements = [e for s in STICKS[:n] for e in stick_elements(s)]
        write(os.path.join(ASSETS, 'models', 'block', 'ground', f'sticks_{n}.json'), {
            'parent': 'minecraft:block/block',
            'ambientocclusion': False,
            'display': DISPLAY,
            'textures': {'particle': '#all'},
            'elements': elements,
        })


def stick_texture():
    """16x16 stick texture, all opaque (the block renders solid, so clear pixels would show black).
    Rows 0-10: lengthwise bark strips (top rows lighter, for top faces); row 11: underside;
    rows 12-15 cols 0-3: end grain (pale wood in a bark ring); cols 4-5: twig tip."""
    import random
    rnd = random.Random(26_3)
    path = os.path.join(ASSETS, 'textures', 'block', 'ground', 'loose_sticks.png')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 255))
    light = [(150, 116, 70), (138, 106, 63), (127, 97, 57)]
    mid = [(112, 85, 50), (102, 77, 45), (93, 70, 41)]
    dark = [(80, 60, 35), (71, 53, 31)]
    knot = (58, 42, 24)
    for y in range(12):
        tones = light if y in (1, 3, 5, 7, 9) else mid if y < 11 else dark
        if y == 11:
            tones = dark
        prev = rnd.choice(tones)
        for x in range(16):
            c = prev if rnd.random() < 0.55 else rnd.choice(tones)  # streaky runs along the stick
            prev = c
            img.putpixel((x, y), c + (255,))
        if y < 11:
            for _ in range(2):  # knots and bark cracks
                img.putpixel((rnd.randrange(2, 14), y), knot + (255,))
    # end grain: bark ring, pale sapwood, darker heart
    ring = (84, 63, 37)
    for y in range(12, 16):
        for x in range(0, 4):
            img.putpixel((x, y), ring + (255,))
    for y in range(13, 15):
        for x in range(1, 3):
            img.putpixel((x, y), (184, 152, 101, 255))
    img.putpixel((2, 14), (160, 128, 82, 255))
    img.putpixel((1, 13), (196, 166, 114, 255))
    # twig tip
    for y in range(12, 14):
        for x in range(4, 6):
            img.putpixel((x, y), (150, 120, 76, 255) if (x + y) % 2 else (122, 93, 56, 255))
    # rest of the bottom rows: plain bark so particles look right
    for y in range(12, 16):
        for x in range(6, 16):
            img.putpixel((x, y), rnd.choice(mid) + (255,))
    for y in range(14, 16):
        for x in range(4, 6):
            img.putpixel((x, y), rnd.choice(mid) + (255,))
    img.save(path)


STONE_TEXTURES = ['minecraft:block/stone', 'minecraft:block/andesite', 'minecraft:block/cobblestone']
PIT_RING = 8  # LooseSticksBlock.RING
STONE_ROCK = f'{NS}:stone_rock'


def stone(cx, cz, theta, tangent, radial, height, tex):
    """A boulder centred at (cx, cz), long side along the ring's tangent at angle theta (degrees, a multiple of 22.5),
    with a smaller cap on top so it reads round. Model rotations only go -45..45, so the box is turned the short way."""
    t = (theta + 90) % 180
    a = (-t) % 180
    if a > 90:
        a -= 180
    w, d = tangent, radial
    if abs(a) > 45:
        a -= 90 if a > 0 else -90
        w, d = d, w
    out = []
    for (sw, sd, y0, y1) in ((w, d, 0, height), (w - 1.5, d - 1.2, height, height + 0.9)):
        faces = {f: {'texture': f'#{tex}'} for f in ('up', 'north', 'south', 'west', 'east')}
        faces['down'] = {'texture': f'#{tex}', 'cullface': 'down'}
        el = {'from': [r3(cx - sw / 2), y0, r3(cz - sd / 2)], 'to': [r3(cx + sw / 2), r3(y1), r3(cz + sd / 2)], 'faces': faces}
        if a:
            el['rotation'] = {'angle': a, 'axis': 'y', 'origin': [r3(cx), 0, r3(cz)]}
        out.append(el)
    return out


def ring(n, radius, tangent, radial, height, centre=(8.0, 8.0), first=0.0):
    """Boulders evenly round a ring: [(x, z, theta, elements)]."""
    import math
    stones = []
    for k in range(n):
        theta = first + k * 360.0 / n
        x = centre[0] + radius * math.cos(math.radians(theta))
        z = centre[1] + radius * math.sin(math.radians(theta))
        tex = 's%d' % (k % len(STONE_TEXTURES))
        stones.append((x, z, theta, stone(x, z, theta, tangent - 0.4 * (k % 2), radial, height - 0.5 * (k % 3 == 1), tex)))
    return stones


def stone_textures():
    t = {'s%d' % i: tex for i, tex in enumerate(STONE_TEXTURES)}
    t['particle'] = STONE_TEXTURES[0]
    return t


def lean(bx, bz, toward, length, width, tilt, tex, row=None):
    """A stick or log stood at (bx, bz) leaning `tilt` degrees toward the centre (toward = 'n','s','e','w': which side of
    the centre it stands on), for a teepee."""
    half = width / 2
    el = {'from': [r3(bx - half), 0, r3(bz - half)], 'to': [r3(bx + half), length, r3(bz + half)]}
    # standing on the north side (low z), it leans toward +z: about x by +tilt; south: -tilt; west: about z by -tilt...
    axis, angle = {'n': ('x', tilt), 's': ('x', -tilt), 'w': ('z', -tilt), 'e': ('z', tilt)}[toward]
    el['rotation'] = {'angle': angle, 'axis': axis, 'origin': [r3(bx), 0, r3(bz)]}
    if row is None:  # a log: bark round, rings at the ends
        el['faces'] = {f: {'texture': '#log'} for f in ('north', 'south', 'west', 'east')}
        el['faces'].update({f: {'texture': '#log_top'} for f in ('up', 'down')})
    else:
        u0 = (16 - length) / 2
        strip = {'uv': [row, u0, row + width, u0 + length], 'texture': f'#{tex}'}
        el['faces'] = {f: dict(strip) for f in ('north', 'south', 'west', 'east')}
        el['faces'].update({f: {'uv': [0.5, 12.5, 3.5, 15.5], 'texture': f'#{tex}'} for f in ('up', 'down')})
    return el


def ash_box(x0, z0, x1, z1, y=0.5):
    faces = {d: {'texture': '#ash'} for d in ('up', 'north', 'south', 'west', 'east')}
    faces['down'] = {'texture': '#ash', 'cullface': 'down'}
    return {'from': [x0, 0, z0], 'to': [x1, y, z1], 'faces': faces}


def flames(x0, x1, height, z=8.0, angle=45, y0=0.5):
    out = []
    for f in (('north', 'south'), ('west', 'east')):
        if f[0] == 'north':
            el = {'from': [x0, y0, z], 'to': [x1, height, z]}
        else:
            el = {'from': [z, y0, x0], 'to': [z, height, x1]}
        if angle:
            el['rotation'] = {'origin': [8, 0, 8], 'axis': 'y', 'angle': angle}
        el['shade_direction_override'] = 'up'
        el['faces'] = {d: {'uv': [0, 0, 16, 16], 'texture': '#fire'} for d in f}
        out.append(el)
    return out


def grate_rod(x0, z0, x1, z1, y0, y1, tex):
    length_x = x1 - x0 > z1 - z0
    faces = {}
    for d in ('up', 'down', 'north', 'south', 'west', 'east'):
        if tex == 'bark':
            faces[d] = {'uv': [1, 2, 15, 3] if length_x or d in ('north', 'south') else [1, 4, 15, 5],
                        'texture': '#bark', **({'rotation': 90} if d in ('up', 'down') and not length_x else {})}
        else:
            faces[d] = {'texture': '#iron'}
    return {'from': [x0, y0, z0], 'to': [x1, y1, z1], 'faces': faces}


def grate(tex):
    """A grate a pan sits on, across the top of the pit (its top at y 15.75, just under a pan in the block above),
    on four legs standing on the ring."""
    els = []
    for z in (3.0, 5.5, 8.0, 10.5, 13.0):
        els.append(grate_rod(1.5, z - 0.4, 14.5, z + 0.4, 15.0, 15.75, tex))
    for x in (2.5, 13.5):
        els.append(grate_rod(x - 0.45, 1.5, x + 0.45, 14.5, 14.2, 15.0, tex))
    for x, z in ((2.5, 2.5), (13.5, 2.5), (2.5, 13.5), (13.5, 13.5)):
        els.append(grate_rod(x - 0.3, z - 0.3, x + 0.3, z + 0.3, 1.0, 14.2, tex))
    return els


def ash_texture():
    import random
    rnd = random.Random(7)
    img = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            r = rnd.random()
            c = (52, 50, 48) if r < 0.35 else (78, 75, 72) if r < 0.7 else (104, 100, 96) if r < 0.93 else (32, 28, 26)
            img.putpixel((x, y), c + (255,))
    for _ in range(6):  # charcoal and a few embers
        img.putpixel((rnd.randrange(16), rnd.randrange(16)), (24, 20, 18, 255))
    for _ in range(3):
        img.putpixel((rnd.randrange(16), rnd.randrange(16)), (150, 62, 24, 255))
    img.save(os.path.join(ASSETS, 'textures', 'block', 'ground', 'firepit_ash.png'))


def block_model(path, textures, elements, ao=False):
    write(os.path.join(ASSETS, 'models', 'block', 'ground', path + '.json'), {
        'parent': 'minecraft:block/block', 'ambientocclusion': ao, 'textures': textures, 'elements': elements})


def count_modifiers(block, prop, values, count_of):
    return [{'type': 'minecraft:set_count',
             'condition': {'type': 'minecraft:match_block', 'blocks': f'{NS}:{block}', 'state': {prop: str(v)}},
             'count': count_of(v)} for v in values]


def loot_entries(block, entries):
    write(os.path.join(DATA, 'loot_table', 'blocks', f'{block}.json'), {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': name, 'modifier': mods}]} for name, mods in entries],
        'random_sequence': f'{NS}:blocks/{block}',
    })


def firepit():
    """The firepit (FirepitBlock), the ring stones on a stick pile, and the bonfire (BonfireBlock, BonfireEdgeBlock)."""
    ash_texture()
    ash = f'{NS}:block/ground/firepit_ash'
    stones = ring(PIT_RING, 6.0, 4.4, 2.8, 3.0, first=0.0)
    # one model per ring stone, so a stick pile shows its ring growing
    for i, (_, _, _, els) in enumerate(stones, 1):
        block_model(f'pit_stone_{i}', stone_textures(), els, ao=True)
    block_model('firepit_ring', stone_textures(), [e for st in stones for e in st[3]], ao=True)
    block_model('firepit_ash', {'ash': ash, 'particle': ash}, [ash_box(3.5, 3.5, 12.5, 12.5), ash_box(5, 5, 11, 11, 0.8)])
    teepee = [lean(8, 4.6, 'n', 8.0, 1.0, 22.5, 'all', 2), lean(8, 11.4, 's', 7.5, 1.0, 22.5, 'all', 4),
              lean(4.6, 8, 'w', 7.5, 0.9, 22.5, 'all', 6), lean(11.4, 8, 'e', 8.0, 0.9, 22.5, 'all', 8)]
    kindling = [rod(7.5, 4.5, 8.5, 11.5, 0.9, 45, [8, 0, 8], 2, [0.5, 12.5, 3.5, 15.5]),
                rod(7.6, 5.0, 8.4, 11.0, 0.7, -45, [8, 0, 8], 6, [0.5, 12.5, 3.5, 15.5])]
    block_model('firepit_sticks', {'all': TEX, 'particle': TEX}, kindling + teepee)
    block_model('firepit_fire', {'fire': 'minecraft:block/campfire_fire', 'particle': 'minecraft:block/campfire_fire'},
                flames(3.0, 13.0, 12.0))
    block_model('firepit_grate_sticks', {'bark': TEX, 'particle': TEX}, grate('bark'))
    block_model('firepit_grate_iron', {'iron': 'minecraft:block/anvil', 'particle': 'minecraft:block/anvil'}, grate('iron'))
    fuels = '|'.join(str(n) for n in range(1, FIREPIT_MAX_FUEL + 1))
    m = lambda n: {'model': f'{NS}:block/ground/{n}'}
    write(os.path.join(ASSETS, 'blockstates', 'firepit.json'), {'multipart': [
        {'apply': m('firepit_ring')},
        {'apply': m('firepit_ash')},
        {'when': {'fuel': fuels}, 'apply': m('firepit_sticks')},
        {'when': {'lit': 'true'}, 'apply': m('firepit_fire')},
        {'when': {'grate': 'sticks'}, 'apply': m('firepit_grate_sticks')},
        {'when': {'grate': 'iron'}, 'apply': m('firepit_grate_iron')},
    ]})
    # the stick pile: its sticks turned four ways, and the ring stones laid so far
    parts = []
    for n in range(1, len(STICKS) + 1):
        parts.append({'when': {'sticks': str(n)}, 'apply': [
            {'model': f'{NS}:block/ground/loose_sticks_{n}', **({'y': y} if y else {})} for y in (0, 90, 180, 270)]})
    for i in range(1, PIT_RING + 1):
        parts.append({'when': {'stones': '|'.join(str(k) for k in range(i, PIT_RING + 1))}, 'apply': m(f'pit_stone_{i}')})
    write(os.path.join(ASSETS, 'blockstates', 'loose_sticks.json'), {'multipart': parts})

    # bonfire: a ring of big boulders round the 3x3, ash inside, a log teepee and a tall fire in the middle
    big = ring(16, 19.5, 7.0, 4.5, 5.0, first=0.0)
    parts_off = {'north': (0, -1), 'south': (0, 1), 'west': (-1, 0), 'east': (1, 0),
                 'north_west': (-1, -1), 'north_east': (1, -1), 'south_west': (-1, 1), 'south_east': (1, 1)}
    import math
    for part, (dx, dz) in parts_off.items():
        ox, oz = dx * 16, dz * 16
        els = []
        for (x, z, theta, _) in big:
            if ox <= x < ox + 16 and oz <= z < oz + 16:
                tex = 's%d' % (int(theta / 22.5) % len(STONE_TEXTURES))
                els += stone(x - ox, z - oz, theta, 7.0 - 0.6 * (int(theta / 22.5) % 2), 4.5, 5.0 - (int(theta / 22.5) % 3 == 1), tex)
        # ash inside the ring, in 2-pixel rows
        for zz in range(0, 16, 2):
            run = None
            for xx in range(0, 17, 2):
                inside = xx < 16 and math.hypot(ox + xx + 1 - 8, oz + zz + 1 - 8) < 17.0
                if inside and run is None:
                    run = xx
                if not inside and run is not None:
                    els.append({**ash_box(run, zz, xx, zz + 2, 0.4), 'faces': {**ash_box(run, zz, xx, zz + 2, 0.4)['faces'],
                                **{k: {'texture': '#a'} for k in ()}}})
                    run = None
        for e in els:
            for f in e['faces'].values():
                if f['texture'] == '#ash':
                    f['texture'] = '#ash'
        tex = stone_textures()
        tex['ash'] = ash
        block_model(f'bonfire_{part}', tex, els, ao=True)
    logs = {'log': 'minecraft:block/oak_log', 'log_top': 'minecraft:block/oak_log_top', 'particle': 'minecraft:block/oak_log'}
    teepee = [lean(8, -2.5, 'n', 22.0, 2.0, 22.5, None), lean(8, 18.5, 's', 21.0, 2.0, 22.5, None),
              lean(-2.5, 8, 'w', 21.0, 2.0, 22.5, None), lean(18.5, 8, 'e', 22.0, 2.0, 22.5, None)]
    block_model('bonfire_logs', logs, teepee)
    block_model('bonfire_ash', {'ash': ash, 'particle': ash}, [ash_box(0, 0, 16, 16, 0.4), ash_box(2, 2, 14, 14, 1.0)])
    block_model('bonfire_fire', {'fire': 'minecraft:block/campfire_fire', 'particle': 'minecraft:block/campfire_fire'},
                flames(-9.0, 25.0, 30.0) + flames(-4.0, 20.0, 24.0, angle=0))
    bfuels = '|'.join(str(n) for n in range(1, BONFIRE_MAX_FUEL + 1))
    write(os.path.join(ASSETS, 'blockstates', 'bonfire.json'), {'multipart': [
        {'apply': m('bonfire_ash')},
        {'when': {'fuel': bfuels}, 'apply': m('bonfire_logs')},
        {'when': {'lit': 'true'}, 'apply': m('bonfire_fire')},
    ]})
    write(os.path.join(ASSETS, 'blockstates', 'bonfire_edge.json'), {'variants': {
        f'lit={lit},part={part}': m(f'bonfire_{part}') for part in parts_off for lit in ('false', 'true')}})

    # loot: the rocks of the ring, the sticks still unburnt (at most a pile), the grate
    explode = [{'type': 'minecraft:explosion_decay'}]
    loot_entries('firepit', [
        ('minecraft:stick', count_modifiers('firepit', 'fuel', range(0, FIREPIT_MAX_FUEL + 1), lambda v: min(v, 4)) + explode),
        (STONE_ROCK, [{'type': 'minecraft:set_count', 'count': PIT_RING}] + explode),
        ('minecraft:iron_bars', count_modifiers('firepit', 'grate', ('none', 'sticks'), lambda v: 0)),
        ('minecraft:stick', count_modifiers('firepit', 'grate', ('none', 'iron', 'sticks'), lambda v: 4 if v == 'sticks' else 0)),
    ])
    loot_entries('bonfire', [
        ('minecraft:stick', count_modifiers('bonfire', 'fuel', range(0, BONFIRE_MAX_FUEL + 1), lambda v: min(v, 4)) + explode),
        (STONE_ROCK, [{'type': 'minecraft:set_count', 'count': PIT_RING}] + explode),
    ])
    loot_entries('bonfire_edge', [(STONE_ROCK, [{'type': 'minecraft:set_count', 'count': PIT_RING}] + explode)])
    # a campfire underneath: flint and steel, flint strikes, shovels, sitting by the fire all know the tag
    write(os.path.join(ROOT, 'data', 'minecraft', 'tags', 'block', 'campfires.json'),
          {'values': [f'{NS}:firepit', f'{NS}:bonfire']})
    # Kitchen's pot and pan heat over a lit firepit (only set on a grate: GroundSticks); harmless without Kitchen
    write(os.path.join(ROOT, 'data', 'jaymoskitchen', 'tags', 'block', 'heat_sources.json'),
          {'replace': False, 'values': [{'id': f'{NS}:firepit', 'required': False}]})


def main():
    stick_templates()
    for n in range(1, len(STICKS) + 1):
        print('sticks', n, 'shape', *stick_bounds(n))
    models('loose_sticks', 'sticks', len(STICKS), f'{NS}:block/ground/loose_sticks')
    loot_entries('loose_sticks', [
        ('minecraft:stick', count_modifiers('loose_sticks', 'sticks', range(2, len(STICKS) + 1), lambda v: v)
         + [{'type': 'minecraft:explosion_decay'}]),
        (STONE_ROCK, count_modifiers('loose_sticks', 'stones', range(0, PIT_RING + 1), lambda v: v)),
    ])
    # the creative-tab block item shows a full pile
    write(os.path.join(ASSETS, 'items', 'loose_sticks.json'),
          {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/ground/loose_sticks_4'}})
    stick_texture()
    firepit()

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