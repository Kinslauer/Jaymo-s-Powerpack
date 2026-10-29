"""
(0.7.334) The friendly war barrel's item icon (its baked model drawn three-quarter on by tools/bbmodel_preview.py,
cropped and shrunk to 16x16, as gen_clockwork_golem.py does), its item model, and its infusion recipe (Golemancy).

Run from the repo root:  python3 -I kingdoms/tools/gen_war_barrel.py
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
		subprocess.run([sys.executable, '-I', 'tools/bbmodel_preview.py', ASSETS, 'war_barrel', sheet], check=True, capture_output=True)
		view = Image.open(sheet).convert('RGB').crop((0, 0, 360, 360))
	rgba = Image.new('RGBA', view.size)
	px, out = view.load(), rgba.load()
	for y in range(view.height):
		for x in range(view.width):
			if px[x, y] != BG:
				out[x, y] = px[x, y] + (255,)
	img = rgba.crop(rgba.getbbox())
	side = max(img.size)
	square = Image.new('RGBA', (side, side))
	square.paste(img, ((side - img.width) // 2, (side - img.height) // 2))
	small = square.resize((16, 16), Image.LANCZOS)
	px = small.load()
	for y in range(16):
		for x in range(16):
			r, g, b, a = px[x, y]
			px[x, y] = (r, g, b, 255) if a >= 110 else (0, 0, 0, 0)
	small.save(ASSETS + 'textures/item/war_barrel.png')


def main():
	icon()
	write(ASSETS + 'items/war_barrel.json', {'model': {'type': 'minecraft:model', 'model': 'jaymoskingdoms:item/war_barrel'}})
	write(ASSETS + 'models/item/war_barrel.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jaymoskingdoms:item/war_barrel'}})
	# No grid recipe: like the clockwork golem, it is made on Powers' infusion altar once Golemancy is known.
	write(DATA + 'alchemy/infusion/war_barrel.json', {
		'centre': 'minecraft:barrel',
		'components': ['minecraft:iron_block'] * 2 + ['minecraft:spruce_log'] * 2 + ['minecraft:lantern', 'minecraft:redstone_block']
			+ ['jaymospowers:ichor'] * 2,
		'essences': {'metal': 12, 'motion': 12, 'craft': 12, 'spirit': 6},
		'instability': 8,
		'result': {'item': 'jaymoskingdoms:war_barrel', 'count': 1},
		'research': 'golemancy',
		'flux': 6})


if __name__ == '__main__':
	main()
