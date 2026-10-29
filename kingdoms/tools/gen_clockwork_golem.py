"""
(0.7.326) The clockwork golem's item icon: its baked model (tools/bbmodel_bake.py) drawn three-quarter on by
tools/bbmodel_preview.py, cropped and shrunk to 16x16. Its recipe, loot and item model are written here too.

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


def main():
	icon()
	write(ASSETS + 'items/clockwork_golem.json', {'model': {'type': 'minecraft:model', 'model': 'jaymoskingdoms:item/clockwork_golem'}})
	write(ASSETS + 'models/item/clockwork_golem.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jaymoskingdoms:item/clockwork_golem'}})
	write(DATA + 'recipe/clockwork_golem.json', {
		'type': 'minecraft:crafting_shaped', 'category': 'misc',
		'pattern': [' K ', 'CRC', ' C '],
		'key': {'K': 'minecraft:clock', 'R': 'minecraft:redstone_block', 'C': 'minecraft:copper_block'},
		'result': {'id': 'jaymoskingdoms:clockwork_golem'}})
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
