"""Jaymo's Powers armour in vanilla's style: clean shading, solid outlines, one ramp per set.
The one-off gear that had placeholder art (Traceur boots and gloves, training weights, Weighted Gi, Sealed Helm's
worn layer) is redrawn at the end.

Item icons start from vanilla's iron (metal sets) or leather (cloth and hide sets) armour icons, painted in each
set's 7-step ramp by brightness rank, with a trim row where the set has one. Worn textures (the 64x32 humanoid and
humanoid_leggings layers) start from vanilla's iron or leather worn layers the same way: vanilla's worn armour uses
the upper part of its material ramp, so the worn ramp runs from the set's mid tone to its glint.

Run from the repository root with the vanilla 26.3 `assets/minecraft/textures` folder (from the client jar):
  python -I tools/vanilla_style/armour.py <vanilla textures folder>
Writes into the built-in vanilla_style pack; the sources are never touched."""
import sys, os; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image
from vstyle import hx

def H(*cs): return [hx(c) for c in cs]
def lum(c): return .299 * c[0] + .587 * c[1] + .114 * c[2]
def mix(a, b, t=.5): return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))

GOLD = H('825d16', 'dc9613', 'e9b115', 'fdff76')        # vanilla gold, dark to light
SETS = {
    # name: (base, icon ramp dark -> glint, trim colours dark -> light or None, item folder, piece names)
    'zanite':         ('iron', H('1a0d2e', '2e1650', '4a2580', '6a3bb0', '8c5ad6', 'b08cf0', 'e8dcff'), None, 'aether'),
    'gravitite':      ('iron', H('2a0a1e', '4e1238', '8a1f63', 'b82c86', 'dc55ac', 'f08ccc', 'ffe0f2'), None, 'aether'),
    'arkenium':       ('iron', H('0e1a2e', '1f3456', '3a5f8e', '5f8cc0', '8fb8e0', 'bcd8f2', 'f4faff'),
                       H('8fb8e0', 'f4faff'), 'aether'),
    'neptune':        ('iron', H('06222c', '0c3e50', '136a84', '1c96b4', '38bfd8', '7ddcec', 'd8f8ff'), GOLD, 'aether'),
    'burrukai_plate': ('iron', H('2a1c10', '4a3220', '6e5034', '8e6e4c', 'ae8e68', 'c8ac84', 'eadcc0'), GOLD, 'aether'),
    'bog_iron':       ('iron', H('140b07', '25140c', '583522', '7b4e34', 'a26d4b', 'b17a54', 'd1956a'), None, 'armor'),
    'beast_pelt':     ('leather', H('2c1a0c', '4c3018', '70482a', '92643a', 'b08452', 'c8a06c', 'e2c690'),
                       H('cfc2ac', 'efe6d2'), 'aether'),
    'caveman':        ('leather', H('2a1814', '382321', '52322a', '664133', '79503d', '946247', 'ab7657'),
                       H('8a7a66', 'cfc2ac'), 'armory'),
    'ichorcloth':     ('leather', H('1c1030', '34204e', '4e3272', '6a4894', '8664b0', 'a284c8', 'c6aee4'), GOLD, 'alchemy'),
}
PIECES = ('helmet', 'chestplate', 'leggings', 'boots')
# icons already in this style: ichorcloth's hood and robe keep their own shapes, bog iron's are vanilla's iron in its ramp
KEEP_ICONS = {'ichorcloth', 'bog_iron'}
TRIM_ROWS = {'helmet': (4,), 'chestplate': (12,), 'leggings': (3,), 'boots': (4,)}   # icon rows that carry a trim
FUR_ROWS = {'helmet': (3, 4), 'chestplate': (2, 12)}                         # hide sets: fur brow, collar and hem

VAN = None
def van(p): return Image.open(f'{VAN}/{p}.png').convert('RGBA')

def by_rank(im, ramp):
    """repaint every opaque pixel by its brightness rank among the image's colours, onto ramp"""
    px = im.load(); w, h = im.size
    cols = sorted({px[x, y][:3] for y in range(h) for x in range(w) if px[x, y][3]}, key=lum)
    n = len(cols)
    m = {c: ramp[min(len(ramp) - 1, round(i * (len(ramp) - 1) / max(1, n - 1)))] for i, c in enumerate(cols)}
    lv = {c: i / max(1, n - 1) for i, c in enumerate(cols)}
    out = Image.new('RGBA', im.size); po = out.load(); lev = {}
    for y in range(h):
        for x in range(w):
            c = px[x, y]
            if c[3]: po[x, y] = m[c[:3]] + (255,); lev[x, y] = lv[c[:3]]
    return out, lev

def base_icon(base, piece):
    im = van(f'item/{base}_{piece}')
    return im

def with_overlay(im, lev, ov, ramp):
    """leather's undyed straps, patches and soles (its overlay) in the darker half of the set's ramp"""
    if not ov.getbbox(): return im, lev
    o, ol = by_rank(ov, ramp)
    im.alpha_composite(o); lev.update(ol)
    return im, lev

def trim_px(trim, l): return trim[-1] if l > .6 else trim[len(trim) // 2 - 1 if len(trim) > 2 else 0]

def icon(name, piece):
    base, ramp, trim, _ = SETS[name]
    im, lev = by_rank(base_icon(base, piece), ramp)
    if base == 'leather': im, lev = with_overlay(im, lev, van(f'item/leather_{piece}_overlay'), ramp[:5])
    px = im.load()
    if trim:                                       # trim rows across the piece, keeping the outline
        rows = TRIM_ROWS[piece]
        if base == 'leather': rows = FUR_ROWS.get(piece, rows)
        for y in rows:
            for x in range(16):
                if (x, y) in lev and lev[x, y] > .25: px[x, y] = trim_px(trim, lev[x, y]) + (255,)
    return im

# ---- caveman: its own ragged hide shapes (cap, shoulder pelt, loincloth, foot wraps), shaded like vanilla ----
CAVE = {
'helmet': """
................
................
................
.....HHHHHH.....
....HHHHHHHH....
...HHfHHdHHHH...
...HHHHHHHfHH...
...HdHHHdHHHH...
...HHHdHHHHdH...
...H.HHHHH.HH...
......HH...H....
................
................
................
................
................""",
'chestplate': """
................
................
....H...........
....H...........
...HHH..........
...HHHH.........
..HHHfH.........
..HHdHfH........
..HHHHHfH.......
..HHdHHHfH......
...HHHHdHfH.....
...HHHdHHHfH....
....HHHHdHHfH...
....HHdHHHHH....
.....HHHHHH.....
................""",
'leggings': """
................
................
....HHHHHHHH....
...HttttffttH...
...HHHHHHHHHH...
...HHHHHHHHHH...
...HHHH.HH.HH...
...HH.H..H..H...
................
................
................
................
................
................
................
................""",
'boots': """
................
................
................
................
.....H....HH....
...HHHH..HHHH...
...HHHH..HHHH...
...HttH..HttH...
...HHHH..HHHH...
..HHHHH..HHHHH..
.HHHHHH..HHHHHH.
.HHHHH....HHHHH.
.HHHH......HHHH.
................
................
................"""}

def caveman_icon(piece):
    from vstyle import parts
    return parts(CAVE[piece], {'H': SETS['caveman'][1]}, accents={'f': ('H', 'bd9573'), 't': ('H', '382321'), 'd': ('H', '52322a')})

# ---- worn layers ----
def worn_ramp(ramp): return [ramp[3], mix(ramp[3], ramp[4]), ramp[4], mix(ramp[4], ramp[5]), ramp[5],
                             mix(ramp[5], ramp[6]), ramp[6]]

def rows_in(lev, x0, x1, y0, y1):
    return sorted({y for (x, y) in lev if x0 <= x < x1 and y0 <= y < y1})

def worn(name, layer, mask=None):
    base, ramp, trim, _ = SETS[name]
    src = van(f'entity/equipment/{layer}/{base}')
    im, lev = by_rank(src, worn_ramp(ramp))
    if base == 'leather':
        im, lev = with_overlay(im, lev, van(f'entity/equipment/{layer}/leather_overlay'), ramp[1:6])
    px = im.load()
    if mask is not None:                           # keep the set's own cut (caveman's scraps)
        mk = mask.load(); mid = worn_ramp(ramp)[2]
        for y in range(32):
            for x in range(64):
                if not mk[x, y][3]: px[x, y] = (0, 0, 0, 0); lev.pop((x, y), None)
                elif (x, y) not in lev:
                    px[x, y] = mid + (255,); lev[x, y] = .4
    def band(x0, x1, y):
        for x in range(x0, x1):
            if (x, y) in lev: px[x, y] = (trim_px(trim, lev[x, y])) + (255,)
    if trim:
        if layer == 'humanoid':
            r = rows_in(lev, 0, 32, 8, 16)
            if r: band(0, 32, r[-1])               # helmet rim
            r = rows_in(lev, 20, 28, 20, 32)
            if r: band(16, 40, r[-1])              # hem
            r = rows_in(lev, 4, 8, 20, 32)
            if r: band(0, 16, r[0])                # boot cuff
            if base == 'leather' and name != 'ichorcloth':
                r = rows_in(lev, 20, 28, 20, 32)
                if r: band(16, 40, r[0])           # fur collar
        else:
            r = rows_in(lev, 20, 28, 20, 32)
            if r: band(16, 40, r[0])               # waistband
    return im

# ---- one-off gear: placeholder art redrawn (Andy, 2026-11-10) ----
TRACEUR = H('2a1a16', '46302a', '6e4c41', '8d6e63', 'a8846f', 'c59a7a', 'e0bc9c')
GI = H('4a1e08', '7a3410', 'b45519', 'd06a20', 'e17828', 'f09444', 'ffc080')
BELT = H('28283c', '3c3c5a')
STEEL = H('1e1a24', '3a3c46', '5c5f68', '8c8f98', 'c4c6ce', 'e0e2e8', 'ffffff')
VISOR = H('4682be', '78bee6', 'd6eefa')

def grid(g, pal):
    rows = g.strip('\n').split('\n')
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    im = Image.new('RGBA', (16, 16)); px = im.load()
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch != '.': px[x, y] = hx(pal[ch]) + (255,)
    return im

CUFFS = """
................
................
................
..oooooo........
.oLCCCCCo.......
.oWwWwWwo.......
.oVvVvVvo.......
.oCCCCCDo.......
..oooooo........
........oooooo..
.......oLCCCCCo.
.......oWwWwWwo.
.......oVvVvVvo.
.......oCCCCCDo.
........oooooo..
................"""
GLOVE = """
................
.....oooooo.....
....oLLCCCCo....
...oLCCCCCCDo...
...oLCoCoCoDo...
...oLCoCoCoDooo.
...oLCCCCCCDCDo.
...oLCCPPCCCDo..
...oCCCPPCCDo...
...oCCCCCCDDo...
....oCCCCDDo....
....oSSSSSTo....
....oSTTTTTo....
....oCCCCDDo....
.....oooooo.....
................"""

def gear_icons():
    out = {}
    im, lev = by_rank(van('item/leather_boots'), TRACEUR)
    im, lev = with_overlay(im, lev, van('item/leather_boots_overlay'), H('17100e', '2a1a16', '3a2620', '46302a', '5a3e36'))
    out['traceur_boots'] = im
    out['traceur_gloves'] = grid(GLOVE, dict(o='2a1a16', L='c59a7a', C='8d6e63', D='6e4c41', P='a8846f', S='e0bc9c', T='a8846f'))
    out['training_weights'] = grid(CUFFS, dict(o='4a1e08', L='f09444', C='d06a20', D='b45519',
                                               W='d8d8d8', w='969696', V='969696', v='444444'))
    out['heavy_training_weights'] = grid(CUFFS, dict(o='2e0c0a', L='c03c2c', C='96281e', D='6a1a14',
                                                     W='867b86', w='4f3c3e', V='5d565d', v='322727'))
    im, lev = by_rank(van('item/leather_chestplate'), GI); px = im.load()     # the gi: tunic, belt, weights
    for x in range(16):
        if (x, 11) in lev and lev[x, 11] > .25: px[x, 11] = BELT[1 if lev[x, 11] > .6 else 0] + (255,)
    for (x, y), c in {(2, 4): 'c4c6ce', (3, 4): '8c8f98', (12, 4): 'c4c6ce', (13, 4): '8c8f98',
                      (5, 8): 'c4c6ce', (6, 8): '8c8f98', (9, 8): 'c4c6ce', (10, 8): '8c8f98',
                      (5, 9): '8c8f98', (6, 9): '5c5f68', (9, 9): '8c8f98', (10, 9): '5c5f68'}.items():
        px[x, y] = hx(c) + (255,)
    out['weighted_gi'] = im
    return out

def region(im, box):
    """only the given box of a worn layer"""
    out = Image.new('RGBA', im.size); out.paste(im.crop(box), box[:2]); return out

def shaded(src, ramp, old, fill=2):
    """vanilla's shading in ramp under the old texture's cut; where vanilla has no pixel, a flat mid tone"""
    im, lev = by_rank(src, worn_ramp(ramp)); px = im.load(); mk = old.load(); mid = worn_ramp(ramp)[fill]
    for y in range(32):
        for x in range(64):
            if not mk[x, y][3]: px[x, y] = (0, 0, 0, 0)
            elif (x, y) not in lev: px[x, y] = mid + (255,)
    return im

def gear_worn(src_root):
    out = {}
    def old(n): return Image.open(f'{src_root}/entity/equipment/humanoid/{n}.png').convert('RGBA')
    leather = van('entity/equipment/humanoid/leather'); iron = van('entity/equipment/humanoid/iron')
    # traceur boots: vanilla's leather boots, dark soles
    im, lev = by_rank(region(leather, (0, 16, 16, 32)), worn_ramp(TRACEUR))
    im, lev = with_overlay(im, lev, region(van('entity/equipment/humanoid/leather_overlay'), (0, 16, 16, 32)), TRACEUR[:4])
    out['traceur_boots'] = im
    # training weights: a cloth band with iron blocks round each ankle
    im = Image.new('RGBA', (64, 32)); px = im.load()
    for x in range(16):
        blk = x % 4 in (1, 2)
        px[x, 26] = hx('e17828') + (255,); px[x, 29] = hx('b45519') + (255,)
        px[x, 27] = hx('d8d8d8' if blk else 'd06a20') + (255,); px[x, 28] = hx('969696' if blk else 'b45519') + (255,)
    out['training_weights'] = im
    # weighted gi: leather's shading in orange under the gi's cut; belt, collar and weights where they were
    o = old('weighted_gi'); im = shaded(leather, GI, o); px = im.load(); op = o.load()
    for y in range(32):
        for x in range(64):
            c = op[x, y]
            if not c[3]: continue
            if c[:3] == hx('28283c'): px[x, y] = BELT[1 if y == 29 else 0] + (255,)
            elif c[:3] == hx('6e737d'): px[x, y] = hx('c4c6ce' if op[x, y - 1][:3] != c[:3] else '8c8f98') + (255,)
            elif c[:3] == hx('b45519'): px[x, y] = GI[2] + (255,)
    out['weighted_gi'] = im
    # sealed helm: vanilla's iron helmet closed over the face, a glass visor in front
    o = old('sealed_helm'); im = shaded(region(iron, (0, 0, 32, 16)), STEEL, o, fill=3); px = im.load()
    wr = worn_ramp(STEEL)
    for x in range(32):
        px[x, 15] = wr[0] + (255,)                                  # rim
    for x in range(8, 16):
        for y in range(8, 15): px[x, y] = wr[3 if y < 10 else 2] + (255,)   # face plate
    for x in range(9, 15):
        px[x, 9] = wr[0] + (255,); px[x, 13] = wr[0] + (255,)       # visor frame
        px[x, 10] = VISOR[2 if x < 11 else 1] + (255,); px[x, 11] = VISOR[1] + (255,); px[x, 12] = VISOR[0] + (255,)
    out['sealed_helm'] = im
    return out

def build(src_root):
    items, layers = {}, {}
    for name, (base, ramp, trim, folder) in SETS.items():
        for p in PIECES if name not in KEEP_ICONS else ():
            items[f'{folder}/{name}_{p}'] = caveman_icon(p) if name == 'caveman' else icon(name, p)
        for layer in ('humanoid', 'humanoid_leggings'):
            mask = None
            if name == 'caveman':
                mask = Image.open(f'{src_root}/entity/equipment/{layer}/caveman.png').convert('RGBA')
            layers[f'{layer}/{name}'] = worn(name, layer, mask)
    items.update(gear_icons())
    layers.update({f'humanoid/{n}': im for n, im in gear_worn(src_root).items()})
    return items, layers

if __name__ == '__main__':
    VAN = sys.argv[1]
    src = 'src/main/resources/assets/jaymospowers/textures'
    out = sys.argv[2] if len(sys.argv) > 2 else 'src/main/resources/resourcepacks/vanilla_style/assets/jaymospowers/textures'
    items, layers = build(src)
    for n, im in items.items():
        assert os.path.exists(f'{src}/item/{n}.png'), n
        os.makedirs(os.path.dirname(f'{out}/item/{n}.png'), exist_ok=True); im.save(f'{out}/item/{n}.png')
    for n, im in layers.items():
        assert os.path.exists(f'{src}/entity/equipment/{n}.png'), n
        os.makedirs(os.path.dirname(f'{out}/entity/equipment/{n}.png'), exist_ok=True); im.save(f'{out}/entity/equipment/{n}.png')
    print(len(items), 'icons,', len(layers), 'worn layers')
