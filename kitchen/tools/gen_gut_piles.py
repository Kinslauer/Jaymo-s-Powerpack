"""Intestines and brain matter lying on the ground (butchery/OffalPileBlock, BrainMatterBlock): block models for 1-4
pieces, their textures and item icons, blockstates, item models and loot tables.
Run from the repo root: python3 -I kitchen/tools/gen_gut_piles.py"""
import json, os, random
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources')
ASSETS = os.path.join(ROOT, 'assets', 'jaymoskitchen')
DATA = os.path.join(ROOT, 'data', 'jaymoskitchen')
rng = random.Random(1119)
OUTLINE = (58, 18, 26, 255)
GUT = [(244, 170, 170), (226, 140, 146), (204, 112, 122), (170, 82, 96), (128, 52, 68)]
BRAIN = [(238, 208, 204), (222, 186, 184), (200, 160, 164), (170, 128, 136), (134, 96, 106)]
BONE = [(252, 251, 237), (230, 226, 207), (197, 192, 165)]
BLOOD = [(125, 31, 32), (90, 20, 22)]


def save(img, *path):
    out = os.path.join(ASSETS, *path)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.save(out)


def write(base, rel, data):
    out = os.path.join(base, rel)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, 'w') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


# ---- textures ----

def gut_texture():
    """Pink gut, a darker ring every other pixel along it, a fleck of blood here and there."""
    img = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            k = 1 + rng.choice((-1, 0, 0, 1)) + (2 if x % 2 else 0)
            c = GUT[min(4, k)]
            if rng.random() < 0.05:
                c = rng.choice(BLOOD)
            img.putpixel((x, y), c + (255,))
    return img


def brain_texture():
    """Brain on the left 12 columns (pale, folded), bone chips' white on the right 4."""
    img = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            if x >= 12:
                c = rng.choice(BONE)
            else:
                k = 1 + rng.choice((-1, 0, 0, 1)) + (2 if rng.random() < 0.3 else 0)
                c = BRAIN[min(4, k)]
                if rng.random() < 0.05:
                    c = rng.choice(BLOOD)
            img.putpixel((x, y), c + (255,))
    return img


def icon(cells, palette, extra=None):
    """A 16x16 item icon from a list of lit pixels, shaded by row, with a dark outline."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    lit = set(cells)
    for (x, y) in lit:
        k = 1 + (1 if y > 9 else 0) + rng.choice((-1, 0, 0, 1)) + (1 if (x + y) % 3 == 0 else 0)
        img.putpixel((x, y), palette[max(0, min(len(palette) - 1, k))] + (255,))
    for (x, y), c in (extra or {}).items():
        img.putpixel((x, y), c + (255,))
        lit.add((x, y))
    for (x, y) in list(lit):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, y + dy)
            if 0 <= n[0] < 16 and 0 <= n[1] < 16 and n not in lit and img.getpixel(n)[3] == 0:
                img.putpixel(n, OUTLINE)
    return img


def thick_path(points, r=1):
    cells = set()
    for (a, b) in zip(points, points[1:]):
        steps = max(abs(b[0] - a[0]), abs(b[1] - a[1]), 1)
        for i in range(steps + 1):
            x = round(a[0] + (b[0] - a[0]) * i / steps)
            y = round(a[1] + (b[1] - a[1]) * i / steps)
            for dx in range(-r + 1, r + 1):
                for dy in range(-r + 1, r + 1):
                    if 1 <= x + dx < 15 and 1 <= y + dy < 15:
                        cells.add((x + dx, y + dy))
    return cells


def intestines_icon():
    # a coil of gut, its end trailing
    path = [(3, 12), (3, 5), (6, 3), (11, 3), (13, 6), (12, 10), (8, 11), (6, 8), (8, 6), (10, 7), (11, 13), (13, 13)]
    return icon(thick_path(path), GUT)


def brain_icon():
    lumps = [(4, 6, 3), (9, 5, 2), (10, 10, 3), (5, 11, 2)]
    cells = set()
    for (cx, cy, r) in lumps:
        for y in range(cy - r, cy + r):
            for x in range(cx - r, cx + r):
                if (x - cx + 0.5) ** 2 + (y - cy + 0.5) ** 2 <= r * r:
                    cells.add((x, y))
    chips = {(13, 4): BONE[0], (12, 4): BONE[1], (7, 13): BONE[0], (8, 13): BONE[2], (2, 10): BONE[1]}
    return icon(cells, BRAIN, chips)


# ---- block models ----

def box(f, t, uv_up, uv_side, tex='#all'):
    faces = {}
    for face in ('north', 'south', 'east', 'west'):
        faces[face] = {'texture': tex, 'uv': uv_side}
    faces['up'] = {'texture': tex, 'uv': uv_up}
    faces['down'] = {'texture': tex, 'uv': uv_up, 'cullface': 'down'}
    return {'from': f, 'to': t, 'faces': faces}


def rope(points, y, h=1.5):
    """A rope of gut lying flat along axis-aligned legs between points (x, z), 1.5 thick."""
    out = []
    for (a, b) in zip(points, points[1:]):
        x0, x1 = sorted((a[0], b[0]))
        z0, z1 = sorted((a[1], b[1]))
        x1, z1 = max(x1, x0 + 1.5), max(z1, z0 + 1.5)
        u = [x0 % 8, z0 % 8, x0 % 8 + min(8, x1 - x0), z0 % 8 + min(8, z1 - z0)]
        out.append(box([x0, y, z0], [x1, y + h, z1], u, [0, 0, min(16, max(x1 - x0, z1 - z0)), h]))
    return out


# each piece adds a loop of gut; the third and fourth lie on top of the others
GUT_LOOPS = [
    [(4.5, 5), (10, 5), (10, 9), (6, 9), (6, 7)],
    [(3, 11), (8, 11), (8, 13), (12.5, 13), (12.5, 8)],
    [(5, 4), (5, 10.5), (9, 10.5), (9, 6.5), (11.5, 6.5)],
    [(3.5, 7), (7.5, 7), (7.5, 12), (11, 12), (11, 3.5)],
]
GUT_Y = [0, 0, 1.3, 2.4]


def gut_model(n):
    elements = []
    for i in range(n):
        elements += rope(GUT_LOOPS[i], GUT_Y[i])
    return {'parent': 'minecraft:block/block', 'textures': {'all': 'jaymoskitchen:block/intestines_pile',
            'particle': 'jaymoskitchen:block/intestines_pile'}, 'elements': elements}


# brain matter: little lumps (x, z, size, height), and chips of skull (white, the texture's right columns)
BRAIN_LUMPS = [
    [(7, 7, 2, 1.2), (9.5, 8.5, 1, 0.8)],
    [(5, 9, 1.5, 1), (10, 6, 1.5, 1)],
    [(6, 5, 1, 0.8), (8, 10, 2, 1.2), (11, 10, 1, 0.7)],
    [(4, 6, 1.5, 1), (12, 8, 1.5, 1), (9, 4, 1, 0.7)],
]
BONE_CHIPS = [[(5.5, 8)], [(11, 9.5)], [(4, 11)], [(10, 12), (7, 4)]]


def brain_model(n):
    elements = []
    for i in range(n):
        for (x, z, s, h) in BRAIN_LUMPS[i]:
            u = [x % 10, z % 10, x % 10 + s, z % 10 + s]
            elements.append(box([x, 0, z], [x + s, h, z + s], u, [x % 10, 0, x % 10 + s, h]))
        for (x, z) in BONE_CHIPS[i]:
            elements.append(box([x, 0, z], [x + 1, 0.4, z + 0.6], [12, z % 15, 13, z % 15 + 0.6], [13, 0, 14, 0.4]))
    return {'parent': 'minecraft:block/block', 'textures': {'all': 'jaymoskitchen:block/brain_matter_pile',
            'particle': 'jaymoskitchen:block/brain_matter_pile'}, 'elements': elements}


def blockstate(name):
    return {'variants': {f'pieces={n}': [{'model': f'jaymoskitchen:block/{name}_{n}', 'y': y} for y in (0, 90, 180, 270)]
                         for n in range(1, 5)}}


def loot(block, item):
    """The gut pile's loot table (a piece for each piece in the pile), for this block and item."""
    with open(os.path.join(DATA, 'loot_table', 'blocks', 'offal_pile.json')) as f:
        text = f.read()
    return json.loads(text.replace('jaymoskitchen:offal_pile', f'jaymoskitchen:{block}').replace('jaymoskitchen:offal"', f'jaymoskitchen:{item}"')
                      .replace('jaymoskitchen:blocks/offal_pile', f'jaymoskitchen:blocks/{block}'))


if __name__ == '__main__':
    save(gut_texture(), 'textures', 'block', 'intestines_pile.png')
    save(brain_texture(), 'textures', 'block', 'brain_matter_pile.png')
    save(intestines_icon(), 'textures', 'item', 'intestines.png')
    save(brain_icon(), 'textures', 'item', 'brain_matter.png')
    for block, item, model in (('intestines_pile', 'intestines', gut_model), ('brain_matter_pile', 'brain_matter', brain_model)):
        for n in range(1, 5):
            write(ASSETS, f'models/block/{block}_{n}.json', model(n))
        write(ASSETS, f'blockstates/{block}.json', blockstate(block))
        write(ASSETS, f'models/item/{item}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'jaymoskitchen:item/{item}'}})
        write(ASSETS, f'items/{item}.json', {'model': {'type': 'minecraft:model', 'model': f'jaymoskitchen:item/{item}'}})
        write(DATA, f'loot_table/blocks/{block}.json', loot(block, item))
