"""Grades our own textures toward Excalibur's look (darker, less saturated, warmer greys), in place.

grade.json (fit.py) holds the shift. Every PNG under textures/{item,block,entity,clothing} of Jaymo's Powers, Kingdoms
and Kitchen, plus the built-in packs' own textures for those mods, is graded once. graded.txt records each graded
file's SHA-1, so a rerun only grades files that are new or were redrawn since (a generator rerun, another PR's art):
nothing is ever graded twice. Glows, effects and see-through textures are left alone (SKIP_WORDS, SEE_THROUGH).

    python -I tools/excalibur_style/grade.py            grade whatever is new or changed (run from the repo root)
    python -I tools/excalibur_style/grade.py --check    list what would be graded, change nothing
    python -I tools/excalibur_style/grade.py --one <in.png> <out.png>   grade one file (previews)
"""
import colorsys
import hashlib
import json
import os
import re
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
MODEL = json.load(open(os.path.join(HERE, 'grade.json')))
BANDS = len(MODEL['bins'])
LEDGER = os.path.join(HERE, 'graded.txt')

ROOTS = [
    'src/main/resources/assets/jaymospowers/textures',
    'kingdoms/src/main/resources/assets/jaymoskingdoms/textures',
    'kitchen/src/main/resources/assets/jaymoskitchen/textures',
    'src/main/resources/resourcepacks/vanilla_style/assets/jaymospowers/textures',
    'src/main/resources/resourcepacks/vanilla_style/assets/jaymoskingdoms/textures',
    'src/main/resources/resourcepacks/vanilla_style/assets/jaymoskitchen/textures',
]
KINDS = ('item', 'block', 'entity', 'clothing')
# light, magic and effects keep their own colours: a file is skipped when a word of its name (split on _ - .) is one
# of these. 'fire' only counts for an entity's fire layer (ogre_fire); fire tomes, firebombs and fire_dragon are graded.
SKIP_WORDS = {'glow', 'emissive', 'fx', 'e', 'aura', 'beam', 'blast', 'flame', 'flames', 'spark', 'sparks', 'energy',
              'halo', 'portal', 'rune', 'runes', 'ki', 'nova', 'flash', 'wisp', 'ghost', 'plasma', 'laser', 'puff', 'eyes'}
ENTITY_SKIP_WORDS = {'fire'}


def skipped_name(path):
    parts = re.split(r'[_\-.]', os.path.basename(path)[:-4].lower())
    words = set(parts)
    # a fire layer is named after its mob (ogre_fire); a mob named for fire (fire_dragon) is graded
    return bool(words & SKIP_WORDS) or ('/entity/' in path.replace(os.sep, '/') and bool(set(parts[1:]) & ENTITY_SKIP_WORDS))


SEE_THROUGH = 0.25   # skip textures where more than this share of the visible pixels are half see-through


def grade_rgb(r, g, b):
    h, l, s = colorsys.rgb_to_hls(r, g, b)
    if s < 0.12:
        c = MODEL['grey']
        return colorsys.hls_to_rgb(h, float(np.interp(l, *c['L'])), float(np.interp(s, *c['S'])))
    f = h * BANDS - 0.5
    i = int(np.floor(f)) % BANDS
    j = (i + 1) % BANDS
    t = f - np.floor(f)
    a, b2 = MODEL['bins'][i], MODEL['bins'][j]
    L = (1 - t) * np.interp(l, *a['L']) + t * np.interp(l, *b2['L'])
    S = (1 - t) * np.interp(s, *a['S']) + t * np.interp(s, *b2['S'])
    dh = (1 - t) * a['dh'] + t * b2['dh']
    return colorsys.hls_to_rgb((h + dh) % 1, float(L), float(S))


_cache = {}


def grade_image(im):
    im = im.convert('RGBA')
    a = np.asarray(im).copy()
    flat = a.reshape(-1, 4)
    vis = flat[:, 3] > 0
    keys, inv = np.unique(flat[vis][:, :3], axis=0, return_inverse=True)
    out = np.empty_like(keys)
    for n, k in enumerate(map(tuple, keys)):
        if k not in _cache:
            _cache[k] = tuple(int(round(min(1, max(0, c)) * 255)) for c in grade_rgb(*(x / 255 for x in k)))
        out[n] = _cache[k]
    flat[np.flatnonzero(vis), :3] = out[inv.reshape(-1)]
    return Image.fromarray(a, 'RGBA')


def wanted(path):
    if skipped_name(path):
        return False
    im = Image.open(path)
    if im.mode not in ('RGBA', 'LA', 'P', 'RGB', 'L'):
        return False
    a = np.asarray(im.convert('RGBA'))[..., 3]
    vis = a > 0
    return vis.any() and ((a > 0) & (a < 250)).sum() <= SEE_THROUGH * vis.sum()


def sha1(path):
    with open(path, 'rb') as f:
        return hashlib.sha1(f.read()).hexdigest()


def main(check):
    ledger = {}
    if os.path.exists(LEDGER):
        for line in open(LEDGER):
            if line.strip():
                h, p = line.rstrip('\n').split(' ', 1)
                ledger[p] = h
    todo = []
    for root in ROOTS:
        for kind in KINDS:
            for d, _, files in os.walk(os.path.join(root, kind)):
                for n in sorted(files):
                    p = os.path.join(d, n).replace(os.sep, '/')
                    if n.endswith('.png') and ledger.get(p) != sha1(p) and wanted(p):
                        todo.append(p)
    if check:
        print('\n'.join(todo))
        print(len(todo), 'to grade')
        return
    for p in todo:
        im = Image.open(p)
        graded = grade_image(im)
        graded.save(p, optimize=True)
        ledger[p] = sha1(p)
    with open(LEDGER, 'w') as f:
        for p in sorted(ledger):
            f.write(f'{ledger[p]} {p}\n')
    print(len(todo), 'graded')


if __name__ == '__main__':
    if sys.argv[1:2] == ['--one']:
        grade_image(Image.open(sys.argv[2])).save(sys.argv[3])
    else:
        main('--check' in sys.argv)
