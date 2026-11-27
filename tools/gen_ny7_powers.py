#!/usr/bin/env python3
"""Night's Blood patch 7 (Powers side): the garlic-and-silver oil (the fifth blade oil), its pot liquid and crucible recipes,
and the farmer's wild garlic trade. Run from the repo root: python3 tools/gen_ny7_powers.py. Safe to run again."""
import json, os
from PIL import Image

PA = 'src/main/resources/assets/jaymospowers'
PD = 'src/main/resources/data/jaymospowers'
PM = 'src/main/resources/data/minecraft'
COL = 0xCFD8E0

FLASK = [
	'................', '.......kk.......', '......kcck......', '......kcck......', '.......gg.......', '......kggk......',
	'.....kgwggk.....', '....kgwlllgk....', '....kglllllk....', '....kllllllk....', '....kllLllLk....', '....kllllllk....',
	'....kdllllLk....', '.....kddddk.....', '......kkkk......', '................']


def write(path, data):
	os.makedirs(os.path.dirname(path), exist_ok=True)
	with open(path, 'w', encoding='utf-8') as f:
		json.dump(data, f, indent=2)
		f.write('\n')


def rgb(c):
	return ((c >> 16) & 255, (c >> 8) & 255, c & 255)


def shade(c, k):
	return tuple(max(0, min(255, int(v * k))) for v in c)


def png(path, rows, pal):
	im = Image.new('RGBA', (len(rows[0]), len(rows)), (0, 0, 0, 0))
	for y, row in enumerate(rows):
		for x, ch in enumerate(row):
			if ch != '.':
				v = pal[ch]
				im.putpixel((x, y), v if len(v) == 4 else v + (255,))
	os.makedirs(os.path.dirname(path), exist_ok=True)
	im.save(path)


def main():
	c = rgb(COL)
	# a pale silver oil with a green fleck of garlic in it
	pal = {'k': (40, 36, 44), 'c': (150, 110, 70), 'g': (200, 220, 230), 'w': (255, 255, 255), 'l': c,
		'L': (150, 190, 120), 'd': shade(c, 0.7)}
	png(f'{PA}/textures/item/alchemy/oil_cursed.png', FLASK, pal)
	write(f'{PA}/items/oil_cursed.json', {'model': {'type': 'minecraft:model', 'model': 'jaymospowers:item/alchemy/oil_cursed'}})
	write(f'{PA}/models/item/alchemy/oil_cursed.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jaymospowers:item/alchemy/oil_cursed'}})

	# the oil pot's liquid
	liquid = shade(c, 1.0)
	png(f'{PA}/textures/block/alchemy/oil_liquid_cursed.png', ['L' * 16 if y % 5 else 'M' * 16 for y in range(16)], {'L': liquid, 'M': shade(liquid, 1.2)})
	state_path = f'{PA}/blockstates/oil_pot.json'
	state = json.load(open(state_path, encoding='utf-8'))
	state['multipart'] = [m for m in state['multipart'] if m.get('when', {}).get('oil') != 'cursed']
	for fill in range(1, 5):
		write(f'{PA}/models/block/alchemy/oil_pot_cursed_{fill}.json', {
			'textures': {'particle': 'jaymospowers:block/alchemy/oil_liquid_cursed', 'o': 'jaymospowers:block/alchemy/oil_liquid_cursed'},
			'elements': [{'from': [4, 1, 4], 'to': [12, 1 + 2 * fill, 12], 'faces': {
				d: {'uv': [0, 0, 16, 16], 'texture': '#o'} for d in ('down', 'up', 'north', 'south', 'east', 'west')}}]})
		state['multipart'].append({'when': {'oil': 'cursed', 'fill': str(fill)}, 'apply': {'model': f'jaymospowers:block/alchemy/oil_pot_cursed_{fill}'}})
	write(state_path, state)

	# the crucible: dead grease, the garlic's plant and the silver's metal and light
	names = ['plain', 'fine', 'expert']
	mult = [1.0, 1.5, 2.2]
	base = {'plant': 5, 'metal': 4, 'light': 3}
	for g in range(3):
		name = 'oil_cursed' + ('' if g == 0 else '_' + names[g])
		write(f'{PD}/alchemy/crucible/{name}.json', {
			'catalyst': 'jaymoskitchen:dead_grease', 'essences': {k: max(1, round(v * mult[g])) for k, v in base.items()},
			'result': {'item': 'jaymospowers:oil_cursed', 'count': 1, 'grade': names[g]}, 'research': 'hunters_oils'})

	# a farmer sells wild garlic (Remedies' own herb)
	write(f'{PD}/villager_trade/farmer/wild_garlic.json', {
		'gives': {'count': 4, 'id': 'jaymospowers:wild_garlic'}, 'max_uses': 12, 'reputation_discount': 0.05,
		'wants': {'count': 1, 'id': 'minecraft:emerald'}, 'xp': 5})
	write(f'{PM}/tags/villager_trade/farmer/level_2.json', {'values': ['jaymospowers:farmer/wild_garlic']})


main()
