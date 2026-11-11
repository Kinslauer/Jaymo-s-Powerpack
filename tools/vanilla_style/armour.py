"""Jaymo's Powers armour in vanilla's style: clean shading, solid outlines, one ramp per set.

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
    'caveman':        ('leather', H('241208', '3e200e', '5c3218', '7a4624', '965c34', 'b07448', 'c89060'),
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

def build(src_root):
    items, layers = {}, {}
    for name, (base, ramp, trim, folder) in SETS.items():
        for p in PIECES if name not in KEEP_ICONS else ():
            items[f'{folder}/{name}_{p}'] = icon(name, p)
        for layer in ('humanoid', 'humanoid_leggings'):
            mask = None
            if name == 'caveman':
                mask = Image.open(f'{src_root}/entity/equipment/{layer}/caveman.png').convert('RGBA')
            layers[f'{layer}/{name}'] = worn(name, layer, mask)
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
