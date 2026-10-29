"""
(0.7.326) The clockwork golem's item icon: its baked model (tools/bbmodel_bake.py) drawn three-quarter on by
tools/bbmodel_preview.py, cropped and shrunk to 16x16. Its infusion recipe, loot and item model are written here too.

Run from the project root:  python -I kingdoms/tools/gen_clockwork_golem.py
"""
import json
import os
import subprocess
import sys
import tempfile

from PIL import Image

ASSETS = 'kingdoms/src/main/resources/assets/jaymoskingdoms/'
DATA = 'kingdoms/src/main/resources/data/jaymoskingdoms/'
BG = (150, 170, 190)


def write(path, obj):
	os.makedirs(os.path.dirname(path), exist_ok=True)
	with open(path, 'w', encoding='utf-8', newline='\n') as f:
		json.dump(obj, f, indent=2)
		f.write('\n')


def icon():
	with tempfile.TemporaryDirectory() as tmp:
		sheet = tmp + '/sheet.png'
		subprocess.run([sys.executable, '-I', 'tools/bbmodel_preview.py', ASSETS, 'clockwork_golem', sheet], check=True, capture_output=True)
		view = Image.open(sheet).convert('RGB').crop((0, 0, 360, 360))
	rgba = Image.new('RGBA', view.size)
	px, out = view.load(), rgba.load()
	for y in range(view.height):
		for x in range(view.width):
			if px[x, y] != BG:
				out[x, y] = px[x, y] + (255,)
	box = rgba.getbbox()
	img = rgba.crop(box)
	side = max(img.size)
	square = Image.new('RGBA', (side, side))
	square.paste(img, ((side - img.width) // 2, (side - img.height) // 2))
	small = square.resize((16, 16), Image.LANCZOS)
	px = small.load()
	for y in range(16):
		for x in range(16):
			r, g, b, a = px[x, y]
			px[x, y] = (r, g, b, 255) if a >= 110 else (0, 0, 0, 0)
	os.makedirs(ASSETS + 'textures/item', exist_ok=True)
	small.save(ASSETS + 'textures/item/clockwork_golem.png')


def rogue_textures():
	"""(0.7.333) A rogue golem's lights burn red: its lit texture with every light (what differs from the dark one) turned
	red, and a glow texture holding only those lights, drawn bright in the dark."""
	bb = ASSETS + 'textures/entity/bb/'
	lit = Image.open(bb + 'clockwork_golem.png').convert('RGBA')
	dark = Image.open(bb + 'clockwork_golem_1.png').convert('RGBA')
	rogue = lit.copy()
	glow = Image.new('RGBA', lit.size)
	pl, pd, pr, pg = lit.load(), dark.load(), rogue.load(), glow.load()
	for y in range(lit.height):
		for x in range(lit.width):
			if pl[x, y] != pd[x, y]:
				r, g, b, a = pl[x, y]
				bright = (r + g + b) / (3 * 255)
				red = (255, int(40 * bright), int(24 * bright), a)
				pr[x, y] = red
				pg[x, y] = red
	rogue.save(bb + 'clockwork_golem_rogue.png')
	glow.save(bb + 'clockwork_golem_rogue_glow.png')


def main():
	icon()
	rogue_textures()
	write(ASSETS + 'items/clockwork_golem.json', {'model': {'type': 'minecraft:model', 'model': 'jaymoskingdoms:item/clockwork_golem'}})
	write(ASSETS + 'models/item/clockwork_golem.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jaymoskingdoms:item/clockwork_golem'}})
	# No grid recipe: it is made on Powers' infusion altar once the Golemancy research is known (read by Powers' loader).
	write(DATA + 'alchemy/infusion/clockwork_golem.json', {
		'centre': 'minecraft:clock',
		'components': ['minecraft:copper_block'] * 4 + ['minecraft:redstone_block'] * 2 + ['jaymospowers:ichor'] * 2,
		'essences': {'metal': 16, 'motion': 12, 'craft': 10, 'spirit': 6},
		'instability': 8,
		'result': {'item': 'jaymoskingdoms:clockwork_golem', 'count': 1},
		'research': 'golemancy',
		'flux': 6})
	write(DATA + 'loot_table/entities/clockwork_golem.json', {
		'type': 'minecraft:entity',
		'pools': [
			{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'minecraft:copper_ingot', 'functions': [
				{'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': 4, 'max': 9}}]}]},
			{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'minecraft:redstone', 'functions': [
				{'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': 2, 'max': 5}}]}]},
			{'rolls': 1, 'conditions': [{'condition': 'minecraft:random_chance', 'chance': 0.5}],
			 'entries': [{'type': 'minecraft:item', 'name': 'minecraft:clock'}]}],
		'random_sequence': 'jaymoskingdoms:entities/clockwork_golem'})


if __name__ == '__main__':
	main()
