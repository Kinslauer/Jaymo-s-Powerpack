"""
(0.7.326) The Reverent Warplate's items (Armory.REVERENT_PIECES): each piece's icon is its part of eanncrod's model
(baked by tools/bbmodel_bake.py) drawn front three-quarter by tools/bbmodel_preview.py and shrunk to 16x16; plus its
item models, its equipment asset (no flat layer: the client's ReverentLayer draws the model) and names.

Run from the project root:  python -I kingdoms/tools/gen_reverent.py
"""
import json
import os
import subprocess
import sys
import tempfile

from PIL import Image

ASSETS = 'kingdoms/src/main/resources/assets/jaymoskingdoms/'
SOURCE = 'tools/bbmodels/reverent-warplate.bbmodel'
BG = (150, 170, 190)
OUTLINE = (58, 40, 24, 255)
PIECES = {'helmet': 'armorHead', 'chestplate': 'armorBody,armorRightArm,armorLeftArm', 'leggings': 'armorWaist,armorRightLeg,armorLeftLeg',
		  'boots': 'armorRightBoot,armorLeftBoot'}
NAMES = {'helmet': 'Reverent Helm', 'chestplate': 'Reverent Warplate', 'leggings': 'Reverent Tassets', 'boots': 'Reverent Sabatons'}


def write(path, obj):
	os.makedirs(os.path.dirname(path), exist_ok=True)
	with open(path, 'w', encoding='utf-8', newline='\n') as f:
		json.dump(obj, f, indent=2)
		f.write('\n')


def icon(bones, out):
	with tempfile.TemporaryDirectory() as tmp:
		subprocess.run([sys.executable, '-I', 'tools/bbmodel_bake.py', SOURCE, tmp, 'piece', '--keep', bones], check=True, capture_output=True)
		subprocess.run([sys.executable, '-I', 'tools/bbmodel_preview.py', tmp, 'piece', tmp + '/sheet.png'], check=True, capture_output=True)
		view = Image.open(tmp + '/sheet.png').convert('RGB').crop((720, 0, 1080, 360))
	rgba = Image.new('RGBA', view.size)
	px, o = view.load(), rgba.load()
	for y in range(view.height):
		for x in range(view.width):
			if px[x, y] != BG:
				o[x, y] = px[x, y] + (255,)
	img = rgba.crop(rgba.getbbox())
	side = max(img.size)
	square = Image.new('RGBA', (side, side))
	square.paste(img, ((side - img.width) // 2, (side - img.height) // 2))
	# 14 across, then a dark outline round it (white enamel on nothing reads as a smudge without one)
	small = square.resize((14, 14), Image.LANCZOS)
	icon = Image.new('RGBA', (16, 16))
	icon.paste(small, (1, 1))
	p = icon.load()
	for y in range(16):
		for x in range(16):
			r, g, b, a = p[x, y]
			p[x, y] = (r, g, b, 255) if a >= 110 else (0, 0, 0, 0)
	edge = [(x, y) for y in range(16) for x in range(16) if p[x, y][3] == 0 and any(
		0 <= x + dx < 16 and 0 <= y + dy < 16 and p[x + dx, y + dy][3] == 255 and p[x + dx, y + dy] != OUTLINE
		for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
	for x, y in edge:
		p[x, y] = OUTLINE
	icon.save(out)


def main():
	os.makedirs(ASSETS + 'textures/item', exist_ok=True)
	for piece, bones in PIECES.items():
		name = 'reverent_' + piece
		icon(bones, ASSETS + 'textures/item/' + name + '.png')
		write(ASSETS + 'items/' + name + '.json', {'model': {'type': 'minecraft:model', 'model': 'jaymoskingdoms:item/' + name}})
		write(ASSETS + 'models/item/' + name + '.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jaymoskingdoms:item/' + name}})
	write(ASSETS + 'equipment/reverent.json', {'layers': {}})
	print('\n'.join('\t"item.jaymoskingdoms.reverent_%s": "%s",' % (p, n) for p, n in NAMES.items()))


if __name__ == '__main__':
	main()
