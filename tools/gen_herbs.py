"""Jaymo's Powers 0.19.24 healing herbs: block textures (5 plants x 4 growth stages), item icons, models, blockstates,
loot tables, recipes, tags and worldgen JSON for the herbs, poultices and sickbed foods (docs/Healing-Plan.md).

Run from the repository root:  python3 -I tools/gen_herbs.py
Item icons follow docs/Vanilla-Style.md: 16x16, no black outlines (an edge is the darkest shade of its colour, the lit
top-left edge one step lighter), light from the top left. The herb items reuse their plant's grown texture, as vanilla's
flowers do. Item shapes are letter grids painted with tools/vanilla_style/vstyle.paint.
Keep HERBS in step with core/wounds/herbs/Herbs.java.
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), 'vanilla_style'))
from PIL import Image
from vstyle import paint

RES = 'src/main/resources'
ASSETS = RES + '/assets/jaymospowers'
DATA = RES + '/data/jaymospowers'
NS = 'jaymospowers'


def hx(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


# ---- the plants -------------------------------------------------------------------------------------------------------

# tag -> (dark, mid, light); every plant uses 'leaf', some add 'flower', 'center', 'spike'
HERBS = {
    'comfrey': {
        'leaf': ('2b5524', '3d7a31', '66a64c'),
        'flower': ('5b3380', '7d4ea6', 'a67bcc'),
        'stem': ('2b5524', '3d7a31', '66a64c'),
    },
    'yarrow': {
        'leaf': ('34602a', '55883f', '86b862'),
        'flower': ('c9bd96', 'efe8d2', 'fffdf2'),
        'stem': ('34602a', '55883f', '86b862'),
    },
    'plantain': {
        'leaf': ('29531f', '3f7a30', '6ba84c'),
        'spike': ('5a3c1f', '7d5630', 'a07a48'),
        'stem': ('4a6a2a', '6a8a3a', '8aaa52'),
    },
    'wild_garlic': {
        'leaf': ('2a6030', '46903f', '7cc25e'),
        'flower': ('c3c9b0', 'eef0e2', 'ffffff'),
        'stem': ('2a6030', '46903f', '7cc25e'),
    },
    'chamomile': {
        'leaf': ('396a30', '589446', '8ac468'),
        'flower': ('c6cabc', 'f0f1e8', 'ffffff'),
        'center': ('b8860f', 'e8b81c', 'ffd84a'),
        'stem': ('396a30', '589446', '8ac468'),
    },
}


class Canvas:
    def __init__(self):
        self.px = {}

    def put(self, x, y, tag):
        x, y = int(round(x)), int(round(y))
        if 0 <= x < 16 and 0 <= y < 16:
            self.px[(x, y)] = tag

    def line(self, x0, y0, x1, y1, tag):
        n = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
        for i in range(n + 1):
            t = i / n
            self.put(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, tag)

    def lens(self, x0, y0, x1, y1, width, tag):
        """A leaf: a lens from the base to the tip, widest in the middle."""
        dx, dy = x1 - x0, y1 - y0
        length = math.hypot(dx, dy) or 1.0
        nx, ny = -dy / length, dx / length
        steps = int(length * 3) + 1
        for i in range(steps + 1):
            t = i / steps
            half = width / 2.0 * math.sin(math.pi * min(0.999, max(0.08, t)))
            cx, cy = x0 + dx * t, y0 + dy * t
            k = 0.0
            while k <= half + 1e-6:
                self.put(cx + nx * k, cy + ny * k, tag)
                self.put(cx - nx * k, cy - ny * k, tag)
                k += 0.5
        self.put(x1, y1, tag)

    def disc(self, cx, cy, r, tag):
        for y in range(16):
            for x in range(16):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r * r + 0.25:
                    self.put(x, y, tag)

    def image(self, pal):
        """Shades by position: an edge open to the top or left is lit, one open below or to the right is dark."""
        im = Image.new('RGBA', (16, 16))
        out = im.load()
        for (x, y), tag in self.px.items():
            dark, mid, light = [hx(c) for c in pal[tag]]

            def open_(dx, dy):
                return (x + dx, y + dy) not in self.px or self.px[(x + dx, y + dy)] != tag
            colour = mid
            if open_(0, 1) or open_(1, 0):
                colour = dark
            if open_(0, -1) or open_(-1, 0):
                colour = light
            if open_(0, 1) and open_(0, -1):
                colour = mid
            out[x, y] = colour + (255,)
        return im


def comfrey(stage):
    c = Canvas()
    if stage == 0:
        for tip in ((6, 11), (10, 11), (8, 10)):
            c.lens(8, 15, tip[0], tip[1], 2.0, 'leaf')
    elif stage == 1:
        for tip in ((4, 9), (12, 9), (6, 8), (10, 8), (8, 7)):
            c.lens(8, 15, tip[0], tip[1], 2.2, 'leaf')
    else:
        for tip in ((1, 9), (15, 9), (3, 4), (13, 4)):
            c.lens(8, 15, tip[0], tip[1], 2.4, 'leaf')
        c.lens(8, 15, 8, 5 if stage == 2 else 3, 2.2, 'leaf')
    if stage == 3:
        for fx, fy in ((6, 2), (10, 3), (8, 0), (4, 4), (12, 5)):
            c.put(fx, fy, 'flower')
            c.put(fx, fy + 1, 'flower')
    return c


def yarrow(stage):
    c = Canvas()
    heights = {0: 5, 1: 8, 2: 11, 3: 12}
    top = 15 - heights[stage]
    c.line(8, 15, 8, top, 'stem')
    for y in range(top + 1, 15, 2):
        reach = min(4, 1 + (y - top) // 2)
        c.line(8, y, 8 - reach, y - 1, 'leaf')
        c.line(8, y + 1, 8 + reach, y, 'leaf')
        if stage >= 1 and reach > 1:
            c.put(8 - reach, y - 2, 'leaf')
            c.put(8 + reach, y - 1, 'leaf')
    if stage >= 1:
        c.line(5, 15, 4, 10 if stage == 1 else 8, 'leaf')
        c.line(11, 15, 12, 10 if stage == 1 else 8, 'leaf')
    if stage == 3:
        for fx, fy in ((6, 2), (7, 1), (8, 1), (9, 1), (10, 2), (7, 2), (8, 2), (9, 2), (7, 3), (8, 3), (9, 3), (5, 3), (11, 3)):
            c.put(fx, fy, 'flower')
    return c


def plantain(stage):
    c = Canvas()
    if stage == 0:
        for tip in ((5, 12), (11, 12), (8, 11)):
            c.lens(8, 15, tip[0], tip[1], 2.2, 'leaf')
    elif stage == 1:
        for tip in ((3, 11), (13, 11), (5, 9), (11, 9), (8, 9)):
            c.lens(8, 15, tip[0], tip[1], 2.6, 'leaf')
    else:
        for tip in ((1, 11), (15, 11), (3, 8), (13, 8), (5, 7), (11, 7)):
            c.lens(8, 15, tip[0], tip[1], 3.0, 'leaf')
        c.lens(8, 15, 8, 8, 2.6, 'leaf')
    if stage == 3:
        for x, top in ((6, 2), (9, 1), (11, 4)):
            c.line(x, 8 if x != 11 else 9, x, top + 2, 'stem')
            c.line(x, top + 2, x, top, 'spike')
            c.put(x, top - 1 if top > 0 else top, 'spike')
    return c


def wild_garlic(stage):
    c = Canvas()
    if stage == 0:
        for tip in ((6, 11), (8, 10), (10, 11)):
            c.lens(8, 15, tip[0], tip[1], 1.8, 'leaf')
    elif stage == 1:
        for tip in ((5, 9), (7, 7), (10, 8), (12, 10)):
            c.lens(8, 15, tip[0], tip[1], 2.4, 'leaf')
    else:
        for tip in ((3, 7), (6, 4), (10, 4), (13, 7), (8, 6)):
            c.lens(8, 15, tip[0], tip[1], 2.8, 'leaf')
    if stage == 3:
        c.line(8, 15, 8, 3, 'stem')
        for fx, fy in ((8, 1), (6, 2), (10, 2), (7, 3), (9, 3), (8, 2), (5, 4), (11, 4)):
            c.put(fx, fy, 'flower')
    return c


def chamomile(stage):
    c = Canvas()
    heights = {0: 5, 1: 8, 2: 10, 3: 10}
    top = 15 - heights[stage]
    c.line(8, 15, 8, top, 'stem')
    for y in range(top + 1, 15, 2):
        reach = min(4, 1 + (y - top) // 2)
        c.line(8, y, 8 - reach, y - 1, 'leaf')
        c.line(8, y + 1, 8 + reach, y, 'leaf')
    if stage >= 2:
        c.line(5, 15, 3, 9, 'leaf')
        c.line(11, 15, 13, 9, 'leaf')
    if stage == 3:
        for fx, fy in ((8, 3), (3, 7), (13, 7)):
            c.disc(fx, fy, 2.0, 'flower')
            c.put(fx, fy, 'center')
        c.line(8, top, 8, 4, 'stem')
        c.line(3, 9, 3, 8, 'stem')
        c.line(13, 9, 13, 8, 'stem')
    return c


PLANTS = {'comfrey': comfrey, 'yarrow': yarrow, 'plantain': plantain, 'wild_garlic': wild_garlic, 'chamomile': chamomile}

# ---- the items --------------------------------------------------------------------------------------------------------

LINEN = {'A': '6b5f4a', 'B': 'c9bfa6', 'C': 'e6dfcc', 'D': 'f7f3e8'}
LEATHER = {'o': '4a2c14', 'p': '6a4220', 'q': '8a5a2e', 'r': 'a8743c', 's': 'd6c06a', 't': '5a3818', 'e': 'e6dfcc'}
ICONS = {}

# a scrap of linen with a heap of pale powder on it
ICONS['yarrow_powder'] = ("""
................
................
................
.......QQ.......
......QPPR......
.....QPPSPR.....
....QPPPPPPR....
...QPSPPPPPPR...
..AAARRRRRRAAA..
.ADDCCCCCCCCCBA.
.ACCCCCCCCCCCBA.
..ABBBBBBBBBBA..
...AAAAAAAAAA...
................
................
................
""", 'iron', {**LINEN, 'P': 'efe6c6', 'Q': 'fffbe8', 'R': 'b9ad86', 'S': '9ab870'})

# a linen wrap round a lump of dark green paste, bound with string
ICONS['knitbone_poultice'] = ("""
................
................
....AAAAAAAA....
...ADDDDDDDDA...
..ADCGGGGGGCBA..
.ADCGHHGGGGGGBA.
.ADGHGGGGJGGGBA.
.ACGGGGGJJGGGBA.
.ACGGGKGGGGGJBA.
.ACGGGGGGGJJGBA.
..ABGGGGGJJJBA..
...ABBBJJJBBA...
....AAAAAAAA....
................
................
................
""", 'iron', {**LINEN, 'G': '4f7233', 'H': '7aa044', 'J': '34501f', 'K': '7b4f9a'})

# a little clay pot of pale green salve, a leaf on the lid
ICONS['burn_salve'] = ("""
................
................
.......MM.......
......MNNM......
.....OOOOOO.....
....OPPPPPQQO...
...OPRRRRRRQQO..
...OPRSRRRRRQO..
...OPRRRRRRRQO..
...OPPPPPPPQQO..
...OTPPPPPQQTO..
...OTTPPPQQTTO..
....OTTTTTTTO...
.....OOOOOOO....
................
................
""", 'iron', {'M': '2f6a2c', 'N': '5fa048', 'O': '5a3a20', 'P': 'b9794a', 'Q': '8c5530', 'R': 'a8c97a',
           'S': 'd8ecb0', 'T': '70452a'})

# a linen wrap round a pale paste with green flecks
ICONS['garlic_poultice'] = ("""
................
................
....AAAAAAAA....
...ADDDDDDDDA...
..ADCGGGGGGCBA..
.ADCGHHGGGGGGBA.
.ADGHHGGGJGGGBA.
.ACGGGGGGGGGGBA.
.ACGGKGGGGGJGBA.
.ACGGGGGGGGGJBA.
..ABGGGJGGGJBA..
...ABBBJJJBBA...
....AAAAAAAA....
................
................
................
""", 'iron', {**LINEN, 'G': 'e8e2c2', 'H': 'fffdf0', 'J': 'bdb48c', 'K': '6aa84a'})

# a scrap of linen, washed: whiter than the rag
ICONS['clean_rag'] = ("""
................
................
...LLL..........
..LONNL..LL.....
..LNMNNLLNOL....
.LNMMNNNNNMNL...
.LONMMNNNMMNL...
..LNNMMNMMNNL...
..LONNMMMNNOL...
.LNNNNMNNNMNL...
.LONMNNNNMMNL...
..LNMMNNNNNL....
..LLNNMMNNL.....
....LLNNLL......
......LL........
................
""", 'iron', {'L': '8a8472', 'M': 'cdc6b2', 'N': 'e8e2d0', 'O': 'f8f4e8'})

# a leather bag with a flap and clasp, a cord to carry it by
ICONS['healers_satchel'] = ("""
................
.....ooooooo....
....o.......o...
....o.......o...
...pppppppppppp.
..pqqqqqqqqqqqrp
..pqrrrrrrrrrrrp
..pqrrrrssrrrrtp
..pqqqqqssqqqqtp
..pqrrrrrrrrrrtp
..pqrrrrrrrrrrtp
..pqrrrrrrrrrrtp
..ptttttttttttto
...oooooooooooo.
................
................
""", 'iron', LEATHER)

BOWL_ROWS = """
................
................
................
................
................
................
..uuuuuuuuuuuu..
.uTTSSSSSSSSRRu.
.uTSSSSSSSSRRRu.
.uyyyyyyyyyyyyu.
..uzyyyyyyyyxu..
..uzzyyyyyxxu...
...uuzzyyxxuu...
....uuuuuuuu....
................
................
"""

# a bowl of broth, a bone in it
ICONS['bone_broth'] = ("""
................
................
................
................
.............ee.
............eEe.
..uuuuuuuuuueEu.
.uTTSSSSSSSSeRu.
.uTSSSSSSSSRRRu.
.uyyyyyyyyyyyyu.
..uzyyyyyyyyxu..
..uzzyyyyyxxu...
...uuzzyyxxuu...
....uuuuuuuu....
................
................
""", 'iron', {'T': 'e0c590', 'S': 'c79e5f', 'R': 'a67c42', 'e': 'f2ede0', 'E': 'c8c0ac'})

# a bowl of golden chamomile tea, a flower floating on it
ICONS['chamomile_tea'] = ("""
................
................
................
................
................
................
..uuuuuuuuuuuu..
.uTTSSSSSSSSRRu.
.uTSSWWSSSSRRRu.
.uyyWYWyyyyyyyu.
..uzyWWyyyyyxu..
..uzzyyyyyxxu...
...uuzzyyxxuu...
....uuuuuuuu....
................
................
""", 'iron', {'T': 'f4de7c', 'S': 'e2c249', 'R': 'b9972c', 'W': 'fbfbf4', 'Y': 'f0c020'})

# a bowl of creamy posset
ICONS['posset'] = ("""
................
................
................
................
................
................
..uuuuuuuuuuuu..
.uTTSSSSSSSSRRu.
.uTSSSSSSSSRRRu.
.uyyyyyyyyyyyyu.
..uzyyyyyyyyxu..
..uzzyyyyyxxu...
...uuzzyyxxuu...
....uuuuuuuu....
................
................
""", 'iron', {'T': 'fffaea', 'S': 'f0e6c8', 'R': 'd2c498'})

# ---- writing ----------------------------------------------------------------------------------------------------------


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', newline='\n') as f:
        f.write(text)


def write_json(path, obj):
    write(path, json.dumps(obj, indent=2) + '\n')


def item_assets(name, texture=None):
    write_json(f'{ASSETS}/items/{name}.json', {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{name}'}})
    write_json(f'{ASSETS}/models/item/{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{texture or name}'}})


def shapeless(name, ingredients, count):
    write_json(f'{DATA}/recipe/medicine/{name}.json', {
        'type': 'minecraft:crafting_shapeless', 'category': 'misc', 'ingredients': ingredients,
        'result': {'id': f'{NS}:{name}', 'count': count}})


def loot(name):
    """One herb when young, 1-3 when grown: the plant itself, which plants again."""
    pools = []
    for age in range(4):
        entry = {'type': 'minecraft:item', 'name': f'{NS}:{name}'}
        if age == 3:
            entry['modifier'] = [{'type': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': 1, 'max': 3}},
                                 {'type': 'minecraft:explosion_decay'}]
        pools.append({'rolls': 1, 'condition': {'type': 'minecraft:match_block', 'blocks': f'{NS}:{name}', 'state': {'age': str(age)}},
                      'entries': [entry]})
    write_json(f'{DATA}/loot_table/blocks/{name}.json', {'type': 'minecraft:block', 'pools': pools, 'random_sequence': f'{NS}:blocks/{name}'})


# how common each patch is: (chance per chunk = 1 in rarity, tries per patch)
PATCHES = {'comfrey': (3, 7), 'yarrow': (3, 7), 'plantain': (2, 8), 'wild_garlic': (2, 8), 'chamomile': (3, 8)}


def patch(name, rarity, tries):
    write_json(f'{DATA}/worldgen/feature/{name}_patch.json', {'type': 'minecraft:simple_block', 'to_place': {'id': f'{NS}:{name}'}})
    spread = lambda lo, hi: {'type': 'minecraft:trapezoid', 'min': lo, 'max': hi, 'plateau': 0}
    write_json(f'{DATA}/worldgen/placed_feature/{name}_patch.json', {'feature': f'{NS}:{name}_patch', 'placement': [
        {'type': 'minecraft:rarity_filter', 'chance': rarity},
        {'type': 'minecraft:in_square'},
        {'type': 'minecraft:heightmap', 'heightmap': 'WORLD_SURFACE_WG'},
        {'type': 'minecraft:biome'},
        {'type': 'minecraft:count', 'count': tries},
        {'type': 'minecraft:offset', 'x': spread(-6, 6), 'y': spread(-2, 2), 'z': spread(-6, 6)},
        {'type': 'minecraft:block_predicate_filter', 'predicate': {'type': 'minecraft:all_of', 'predicates': [
            {'type': 'minecraft:matching_block_tag', 'tag': 'minecraft:air'},
            {'type': 'minecraft:matching_block_tag', 'tag': 'minecraft:substrate_overworld', 'offset': [0, -1, 0]},
        ]}},
    ]})


def main():
    # plants: 4 stages each as cross models, a blockstate by age, the herb item on the grown texture
    for name, draw in PLANTS.items():
        for stage in range(4):
            path = f'{ASSETS}/textures/block/herbs/{name}_{stage}.png'
            os.makedirs(os.path.dirname(path), exist_ok=True)
            draw(stage).image(HERBS[name]).save(path)
            write_json(f'{ASSETS}/models/block/herbs/{name}_{stage}.json', {'parent': 'minecraft:block/cross',
                                                                          'textures': {'cross': f'{NS}:block/herbs/{name}_{stage}'}})
        write_json(f'{ASSETS}/blockstates/{name}.json', {'variants': {
            f'age={stage}': {'model': f'{NS}:block/herbs/{name}_{stage}'} for stage in range(4)}})
        # the herb's icon is its grown plant
        draw(3).image(HERBS[name]).save(f'{ASSETS}/textures/item/{name}.png')
        item_assets(name)
        loot(name)
        patch(name, *PATCHES[name])
    # item icons
    for name, (grid, mat, extra) in ICONS.items():
        paint(grid, mat, extra).save(f'{ASSETS}/textures/item/{name}.png')
        item_assets(name)
    # tag of the herbs, and the recipes
    write_json(f'{DATA}/tags/item/herbs.json', {'replace': False, 'values': [f'{NS}:{n}' for n in PLANTS]})
    shapeless('yarrow_powder', [f'{NS}:yarrow'], 2)
    shapeless('knitbone_poultice', [f'{NS}:comfrey', f'{NS}:comfrey', '#minecraft:leaves'], 2)
    shapeless('burn_salve', [f'{NS}:plantain', f'{NS}:plantain', '#minecraft:leaves'], 2)
    shapeless('garlic_poultice', [f'{NS}:wild_garlic', f'{NS}:wild_garlic', '#minecraft:leaves'], 2)
    shapeless('bone_broth', ['minecraft:bowl', 'minecraft:bone', f'#{NS}:herbs', 'minecraft:potion'], 1)
    shapeless('chamomile_tea', ['minecraft:bowl', f'{NS}:chamomile', f'{NS}:chamomile'], 1)
    shapeless('posset', ['minecraft:milk_bucket', 'minecraft:sugar', 'minecraft:bowl'], 1)
    write_json(f'{DATA}/recipe/medicine/healers_satchel.json', {
        'type': 'minecraft:crafting_shaped', 'category': 'misc',
        'key': {'S': 'minecraft:string', 'L': 'minecraft:leather'},
        'pattern': [' S ', 'L L', 'LLL'],
        'result': {'id': f'{NS}:healers_satchel', 'count': 1}})
    print('herbs written')


main()
