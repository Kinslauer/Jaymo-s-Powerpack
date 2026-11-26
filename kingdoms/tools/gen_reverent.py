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
ICON_SHADES = 12
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
	# The Reason look (docs/Vanilla-Style.md): the view folds into a few shades first, then each of the 14x14 cells takes
	# the shade most of its texels have (blending texels made a gradient of near-twin colours); the outline is the darkest
	# shade of what it wraps, as vanilla's icons have it, not one fixed brown.
	alpha = square.getchannel('A')
	rgb = Image.new('RGB', square.size, tuple(sum(c[i] for c in img.getdata() if c[3]) // max(1, sum(1 for c in img.getdata() if c[3])) for i in range(3)))
	rgb.paste(square.convert('RGB'), mask=alpha.point(lambda a: 255 if a >= 128 else 0))
	few = rgb.quantize(ICON_SHADES, method=Image.Quantize.MEDIANCUT).convert('RGB')
	icon = Image.new('RGBA', (16, 16))
	p = icon.load()
	fp, ap, cell = few.load(), alpha.load(), side / 14.0
	for cy in range(14):
		for cx in range(14):
			votes = {}
			opaque = 0
			for y in range(int(cy * cell), max(int(cy * cell) + 1, int((cy + 1) * cell))):
				for x in range(int(cx * cell), max(int(cx * cell) + 1, int((cx + 1) * cell))):
					if y < side and x < side and ap[x, y] >= 128:
						opaque += 1
						votes[fp[x, y]] = votes.get(fp[x, y], 0) + 1
			if votes and opaque * 2 >= int(cell) ** 2:
				p[cx + 1, cy + 1] = max(votes, key=votes.get) + (255,)
	edge = {}
	for y in range(16):
		for x in range(16):
			if p[x, y][3] == 0:
				near = [p[x + dx, y + dy] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))
					if 0 <= x + dx < 16 and 0 <= y + dy < 16 and p[x + dx, y + dy][3] == 255]
				if near:
					dark = min(near, key=lambda c: c[0] * 3 + c[1] * 6 + c[2])
					edge[x, y] = tuple(min(72, int(v * 0.36)) for v in dark[:3]) + (255,)
	for xy, c in edge.items():
		p[xy] = c
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
