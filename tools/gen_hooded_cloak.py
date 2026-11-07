"""Our own boxes and our own pixels for the hooded cloaks (Andy's REA rule).

Writes one of src/main/resources/assets/jaymospowers/textures/entity/worn/hooded_cloak_<hood>.png (64x64),
read by client/costume/HoodedCloakLayer. Each hood gets its own palette; every texel is painted
from flat colours, per-face shading, seeded noise, a darker rim and a weave.

Boxes and texture offsets must match the BOXES table in HoodedCloakLayer.

Run:
  python3 tools/gen_hooded_cloak.py archer            -> hooded_cloak.png
  python3 tools/gen_hooded_cloak.py ranger            -> hooded_cloak_ranger.png
  python3 tools/gen_hooded_cloak.py netherite_ranger  -> hooded_cloak_netherite_ranger.png
"""
import os
import random
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')

HOOD = sys.argv[1] if len(sys.argv) > 1 else 'archer'

# Palettes per hood, sampled from the Archers jar (assets/archers/textures/armor/
# ranger_armor.png bright green, netherite_ranger_armor.png teal) and from the cloak
# spec (archer_armor_head #6d3e26 leather). The cloak wool is a shade deeper than the
# hood so the hood reads as a separate piece. Base colours are brightened so that, after
# the per-face shading, edge darkening and weave grain are applied, the cloak still reads
# as the hood's colour rather than near-black.
PALETTES = {
    'archer': {  # Archers' archer hood: from docs/Hooded-Cloak-Spec.md (#6d3e26 leather)
        'hood': (0x6d, 0x3e, 0x26),
        'hood_in': (0x37, 0x25, 0x23),
        'wool': (0x4f, 0x34, 0x24),
        'lining': (0x2f, 0x22, 0x1c),
        'trim': (0x83, 0x4d, 0x32),
        'clasp': (0xa8, 0x8a, 0x52),
    },
    'ranger': {  # Ranger's hood: bright green from ranger_armor.png (#257145 main), brightened
        # so the shaded cloak still reads green
        'hood': (0x4a, 0xa0, 0x65),
        'hood_in': (0x2a, 0x50, 0x33),
        'wool': (0x3a, 0x8a, 0x55),
        'lining': (0x1e, 0x3e, 0x2a),
        'trim': (0x6a, 0xc0, 0x75),
        'clasp': (0x55, 0x7f, 0x5b),
    },
    'netherite_ranger': {  # Netherite Ranger's hood: teal from netherite_ranger_armor.png
        # (#11545a main), brightened so the shaded cloak still reads teal
        'hood': (0x3a, 0x90, 0x85),
        'hood_in': (0x1a, 0x48, 0x45),
        'wool': (0x2a, 0x80, 0x75),
        'lining': (0x0e, 0x2e, 0x2b),
        'trim': (0x5a, 0xb0, 0xa5),
        'clasp': (0x49, 0x6f, 0x6b),
    },
}

if HOOD not in PALETTES:
    print(f'Unknown hood {HOOD}; choose from {sorted(PALETTES)}')
    sys.exit(1)

PALETTE = PALETTES[HOOD]

# name: (u, v, (w, h, d), material, dagged hem)
BOXES = {
    'collar': (0, 0, (11, 3, 6), 'wool', False),
    'back_upper': (0, 9, (11, 10, 1), 'wool', False),
    'back_lower': (24, 9, (12, 10, 1), 'wool', True),
    'mantle': (0, 20, (6, 4, 7), 'wool', True),
    'cowl': (26, 20, (9, 4, 4), 'hood', False),
    'point': (0, 31, (5, 4, 1), 'hood', True),
    'clasp': (12, 31, (2, 2, 1), 'clasp', False),
}

rng = random.Random(1137)
img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
suffix = '' if HOOD == 'archer' else f'_{HOOD}'
OUT = os.path.join(ROOT, f'src/main/resources/assets/jaymospowers/textures/entity/worn/hooded_cloak{suffix}.png')


def shade(rgb, f):
    return tuple(max(0, min(255, int(c * f))) for c in rgb) + (255,)


def faces(u, v, w, h, d):
    """Vanilla's box unwrap: name -> (x, y, width, height)."""
    return {
        'top': (u + d, v, w, d),
        'bottom': (u + d + w, v, w, d),
        'east': (u, v + d, d, h),
        'front': (u + d, v + d, w, h),
        'west': (u + d + w, v + d, d, h),
        'back': (u + d + w + d, v + d, w, h),
    }


LIGHT = {'top': 1.12, 'bottom': 0.7, 'east': 0.86, 'west': 0.86, 'front': 1.0, 'back': 0.95}


def paint(name, u, v, size, material, dagged):
    w, h, d = size
    for face, (x0, y0, fw, fh) in faces(u, v, w, h, d).items():
        base = PALETTE[material]
        if name.startswith('back') and face == 'front':
            base = PALETTE['lining']
        if name == 'cowl' and face == 'top':
            base = PALETTE['hood_in']
        for y in range(fh):
            for x in range(fw):
                f = LIGHT[face] * (0.93 + rng.random() * 0.12)
                if material == 'wool' and (x + y * 2) % 4 == 0:
                    f *= 0.94  # a twill weave
                if material == 'hood' and (x * 3 + y) % 5 == 0:
                    f *= 0.92  # leather grain
                if material == 'clasp' and (x + y) % 2 == 0:
                    f *= 1.15
                rgb = base
                edge = x == 0 or y == 0 or x == fw - 1 or y == fh - 1
                if material in ('wool', 'hood') and face in ('front', 'back', 'east', 'west') and y == fh - 1:
                    rgb = PALETTE['trim'] if material == 'wool' else PALETTE['hood_in']
                elif edge and fw > 2 and fh > 2:
                    f *= 0.82
                if name.startswith('back') and face == 'back' and x == fw // 2 and y % 2 == 0:
                    f *= 0.78  # stitched seam down the middle of the back panel
                px = shade(rgb, f)
                if dagged and face in ('front', 'back', 'east', 'west') and y == fh - 1 and (x // 2) % 2 == 1:
                    px = (0, 0, 0, 0)  # a dagged hem: every other pair of texels on the bottom row cut away
                img.putpixel((x0 + x, y0 + y), px)


for name, (u, v, size, material, dagged) in BOXES.items():
    paint(name, u, v, size, material, dagged)

os.makedirs(os.path.dirname(OUT), exist_ok=True)
img.save(OUT)
print('wrote', OUT)
