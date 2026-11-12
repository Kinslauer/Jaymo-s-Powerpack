#!/usr/bin/env python3
"""Data mistakes that stop the server loading at all (26.3 refuses the whole datapack load for one bad value):
- a crafting/cooking recipe with a category the recipe book doesn't have ("tools" did, 2026-11-11);
- a recipe, loot table or advancement naming one of our items that doesn't exist (jaymospowers:zanite_sword did).
An item exists if it has assets/<mod>/items/<id>.json or blockstates/<id>.json, or its id appears as a string in the
mod's Java (items registered without a model file). Run by the PR checks workflow; exits 1 on any finding."""
import glob, json, os, re, sys

MODS = {'jaymospowers': 'src', 'jaymoskingdoms': 'kingdoms/src', 'jaymoskitchen': 'kitchen/src'}
CRAFT = {'building', 'redstone', 'equipment', 'misc'}
COOK = {'food', 'blocks', 'misc'}
REF = re.compile(r'"(?:item|id|name|result|ingredient|base|addition|template)"\s*:\s*"(jaymos(?:powers|kingdoms|kitchen)):([a-z0-9_/]+)"')


def stem(path):
	return os.path.splitext(os.path.basename(path))[0]


known = {}
for ns, root in MODS.items():
	ids = {stem(p) for p in glob.glob(f'{root}/**/assets/{ns}/items/*.json', recursive=True)}
	ids |= {stem(p) for p in glob.glob(f'{root}/**/assets/{ns}/blockstates/*.json', recursive=True)}
	for p in glob.glob(f'{root}/**/*.java', recursive=True):
		ids |= set(re.findall(r'"([a-z0-9_]+)"', open(p, encoding='utf-8', errors='ignore').read()))
	known[ns] = ids

problems = []
for ns, root in MODS.items():
	for f in glob.glob(f'{root}/main/resources/data/**/*.json', recursive=True):
		if '/tags/' in f:
			continue
		text = open(f, encoding='utf-8').read()
		if '/recipe/' in f:
			try:
				d = json.loads(text)
			except ValueError as e:
				problems.append(f'{f}: not valid JSON ({e})')
				continue
			kind, cat = d.get('type', ''), d.get('category')
			allowed = COOK if any(k in kind for k in ('smelting', 'blasting', 'smoking', 'campfire')) else CRAFT if kind.startswith('minecraft:crafting') else None
			if cat is not None and allowed is not None and cat not in allowed:
				problems.append(f'{f}: category "{cat}" isn\'t one of {sorted(allowed)}')
		for m in REF.finditer(text):
			if m.group(2) not in known[m.group(1)]:
				problems.append(f'{f}: {m.group(1)}:{m.group(2)} isn\'t an item or block of ours')

for p in problems:
	print(p)
print(f'Data references: {len(problems)} problem(s).')
sys.exit(1 if problems else 0)
