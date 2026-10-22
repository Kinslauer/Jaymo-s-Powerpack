#!/usr/bin/env python3
"""Checks the Aether's worldgen data (Track W): every JSON parses, every id a feature, biome or tag names exists, every
biome list is cut from one order, every sapling has its tree, every block it uses is registered.
Run from the repo root: python3 -I tools/check_aether_world.py   (stdlib only)."""
import glob
import json
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
DATA = os.path.join(ROOT, 'src/main/resources/data/jaymospowers')
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/jaymospowers')
NS = 'jaymospowers:'
JAVA_FEATURES = {'golden_oak', 'crystal_island', 'aercloud', 'quicksoil_shelf', 'cloudbed', 'ice_spike', 'ferrosite_spire', 'moa_nest', 'pointed_holystone',
                'sword_stone', 'sword_stone_deep', 'sword_stone_end', 'sword_stone_frost', 'sword_stone_nether', 'web_crater', 'web_red_crater'}  # the last seven belong to other powers
SAPLINGS = ['skyplane', 'skybirch', 'skypine', 'wisproot', 'wisptop', 'greatroot', 'greatoak', 'greatboa', 'amberoot']
BIOMES = ['frozen_lakes', 'sheer_tundra', 'enduring_woodland', 'frigid_sierra', 'shimmering_basin', 'flourishing_field', 'verdant_woods', 'shrouded_forest',
          'violet_highwoods', 'turquoise_forest', 'glistening_swamp', 'magnetic_scar', 'battleground_wastes', 'contaminated_jungle', 'hestveil_caverns', 'expanse']
problems = []


def bad(msg):
    problems.append(msg)


def names(folder):
    return {os.path.splitext(os.path.basename(p))[0] for p in glob.glob(os.path.join(DATA, folder, '*.json'))}


# every json parses
for path in glob.glob(os.path.join(DATA, '**/*.json'), recursive=True) + glob.glob(os.path.join(ASSETS, 'lang/parts/*.json')):
    try:
        with open(path, encoding='utf-8') as f:
            json.load(f)
    except Exception as e:  # noqa
        bad('bad json %s: %s' % (os.path.relpath(path, ROOT), e))

blocks = {os.path.splitext(os.path.basename(p))[0] for p in glob.glob(os.path.join(ASSETS, 'blockstates/*.json'))}
features, placed, biomes = names('worldgen/feature'), names('worldgen/placed_feature'), names('worldgen/biome')
btags = names('tags/block')
density = {os.path.relpath(p, os.path.join(DATA, 'worldgen/density_function')).replace('\\', '/')[:-5] for p in glob.glob(os.path.join(DATA, 'worldgen/density_function/**/*.json'), recursive=True)}
noises = {os.path.relpath(p, os.path.join(DATA, 'worldgen/noise')).replace('\\', '/')[:-5] for p in glob.glob(os.path.join(DATA, 'worldgen/noise/**/*.json'), recursive=True)}


def walk(node, fn, where):
    if isinstance(node, dict):
        for k, v in node.items():
            fn(k, v, where)
            walk(v, fn, where)
    elif isinstance(node, list):
        for v in node:
            walk(v, fn, where)


def check_ids(k, v, where):
    if isinstance(v, str) and v.startswith(NS) and k in ('id', 'Name', 'result_state', 'to_place', 'barrier', 'state', 'data'):
        name = v[len(NS):]
        if name not in blocks and name not in features and name not in placed:
            bad('%s: %s names an unknown block %s' % (where, k, v))
    if k == 'type' and isinstance(v, str) and v.startswith(NS):
        t = v[len(NS):]
        if t not in JAVA_FEATURES and t != 'aether':
            bad('%s: unknown type %s' % (where, v))
    if k == 'feature' and isinstance(v, str) and v.startswith(NS):
        t = v[len(NS):]
        if t not in features and t not in placed:
            bad('%s: unknown feature %s' % (where, v))
    if isinstance(v, str) and v.startswith('#' + NS):
        if v[len(NS) + 1:] not in btags:
            bad('%s: unknown tag %s' % (where, v))
    if k in ('tag',) and isinstance(v, str) and v.startswith(NS):
        if v[len(NS):] not in btags:
            bad('%s: unknown tag %s' % (where, v))
    if k == 'noise' and isinstance(v, str) and v.startswith(NS + 'aether/'):
        if v[len(NS):].split('aether/', 1)[1] not in {n.split('aether/', 1)[-1] for n in noises}:
            bad('%s: unknown noise %s' % (where, v))


for folder in ('worldgen/feature', 'worldgen/placed_feature', 'worldgen/material_rule', 'worldgen/noise_settings', 'worldgen/density_function'):
    for path in glob.glob(os.path.join(DATA, folder, '**/*.json'), recursive=True):
        rel = os.path.relpath(path, DATA)
        with open(path, encoding='utf-8') as f:
            node = json.load(f)
        walk(node, check_ids, rel)
        if folder == 'worldgen/placed_feature':
            ref = node['feature']
            if isinstance(ref, str) and ref.startswith(NS) and ref[len(NS):] not in features:
                bad('%s: placed feature of unknown feature %s' % (rel, ref))

# density function refs
for path in glob.glob(os.path.join(DATA, 'worldgen/**/*.json'), recursive=True):
    with open(path, encoding='utf-8') as f:
        text = f.read()
    for m in re.finditer(r'"jaymospowers:(aether/[a-z_/0-9]+)"', text):
        name = m.group(1)
        if name not in density and name not in noises:
            bad('%s: unknown density function or noise %s' % (os.path.relpath(path, DATA), name))

# biomes: files, features, tags, one master order
order = {}
for b in BIOMES:
    path = os.path.join(DATA, 'worldgen/biome/%s.json' % b)
    if not os.path.exists(path):
        bad('missing biome ' + b)
        continue
    with open(path, encoding='utf-8') as f:
        biome = json.load(f)
    if len(biome['features']) != 11:
        bad('%s: needs 11 feature steps' % b)
    for step, lst in enumerate(biome['features']):
        for fid in lst:
            n = fid[len(NS):]
            if n not in placed:
                bad('%s: unknown placed feature %s' % (b, fid))
            order.setdefault(step, {})
            order[step].setdefault(fid, set())
        for i, fid in enumerate(lst):
            for later in lst[i + 1:]:
                order[step].setdefault(fid, set()).add(later)
for step, graph in order.items():
    for a, afters in graph.items():
        for z in afters:
            if a in graph.get(z, ()):
                bad('step %d: %s and %s are listed in both orders (feature cycle)' % (step, a, z))
for p in glob.glob(os.path.join(DATA, 'worldgen/biome/skyroot_*.json')):
    bad('old biome file ' + p)
if 'worldgen/biome' and not set(BIOMES) <= biomes:
    bad('biomes missing')
for tag, members in {'is_aether': BIOMES, 'is_aether_highfields': BIOMES[4:8], 'is_aether_magnetic': BIOMES[8:12], 'is_aether_arctic': BIOMES[0:4],
                     'is_aether_irradiated': BIOMES[12:14]}.items():
    path = os.path.join(DATA, 'tags/worldgen/biome/%s.json' % tag)
    if not os.path.exists(path):
        bad('missing biome tag ' + tag)
        continue
    got = {v[len(NS):] for v in json.load(open(path, encoding='utf-8'))['values']}
    if got != set(members):
        bad('biome tag %s is wrong: %s' % (tag, sorted(got ^ set(members))))

# dimension: every biome it uses exists
with open(os.path.join(DATA, 'dimension/aether.json'), encoding='utf-8') as f:
    dim = json.load(f)
used = {e['biome'][len(NS):] for e in dim['generator']['biome_source']['biomes']}
for u in used:
    if u not in biomes:
        bad('dimension uses missing biome ' + u)
for b in BIOMES:
    if b not in used and b != 'expanse':
        bad('dimension never places ' + b)
if 'expanse' in used:
    bad('expanse must stay unplaced')

# saplings and trees
for s in SAPLINGS:
    if s + '_tree' not in features:
        bad('sapling %s has no %s_tree feature' % (s, s))
for t in ('skyroot_tree', 'golden_oak_tree'):
    if t not in features:
        bad('missing ' + t)

# lang
lang = {}
for p in glob.glob(os.path.join(ASSETS, 'lang/parts/*.json')):
    for k in json.load(open(p, encoding='utf-8')):
        if k in lang:
            bad('duplicate lang key %s (%s and %s)' % (k, lang[k], os.path.basename(p)))
        lang[k] = os.path.basename(p)
for b in BIOMES:
    if 'biome.jaymospowers.' + b not in lang:
        bad('no lang name for biome ' + b)

# nothing left of the old world
for pat in ('timeline/aether_noon.json', 'tags/timeline/in_aether.json'):
    if os.path.exists(os.path.join(DATA, pat)):
        bad('old file ' + pat)
with open(os.path.join(DATA, 'dimension_type/aether.json'), encoding='utf-8') as f:
    dt = json.load(f)
if dt.get('timelines') != '#minecraft:in_overworld' or dt['min_y'] != -32 or dt['height'] != 416:
    bad('dimension type is not the day/night -32..384 one')

for p in problems:
    print('PROBLEM', p)
print('%d problems' % len(problems))
sys.exit(1 if problems else 0)
