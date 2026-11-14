"""
The armour sets of cleannrooster's Frontier Armaments (MIT, tools/bbmodels/frontier-armaments/), made into Kingdoms'
worn armour (FrontierArmour): each set's GeckoLib model turned into a Blockbench project, baked for the client's
ReverentLayer (as gen_reverent.py's sets are), its looks' textures, each piece's icon, item models, equipment assets and
names.

What is changed on the way:
- Faces the maker left in Blockbench's template colours are painted: a whole unpainted face takes the picture of a
  painted face of its own cube (darkened), stray pixels take their neighbour's colour.
- The Despot's set came as a breastplate and tassets only. Its tusked helm and greaves are ours, built from the
  breastplate's own gold.
- The settler's hat comes twice: the wide hat alone, and the hat over its full steel helm (the guards').
- The Foxshade mantle's skirt hangs with its chest, so the one piece draws all of it.
- The blue and purple Sanguine sheets came without their hat and boots: those are the red sheet's, turned to their hue.

Run from the project root:  python -I kingdoms/tools/gen_frontier_armour.py
"""
import colorsys
import copy
import json
import os
import subprocess
import sys
import tempfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', '..', 'tools'))
import gen_guns_geckolib as g
import gen_reverent as r

SRC = 'tools/bbmodels/frontier-armaments/armour/'
PIECES = {'helmet': 'armorHead', 'chestplate': 'armorBody,armorRightArm,armorLeftArm', 'leggings': 'armorWaist,armorRightLeg,armorLeftLeg',
		  'boots': 'armorRightBoot,armorLeftBoot'}
# model -> (its looks: look -> texture file), the pieces it has
MODELS = {
	'templar': ({'templar': 'templar', 'templar_steel': 'templar_steel'}, PIECES),
	'hedge': ({'hedge': 'hedge', 'hedge_elite': 'hedge_elite', 'hedge_ashen': 'hedge_ashen'}, PIECES),
	'primalist': ({'primalist': 'primalist'}, PIECES),
	'ranger': ({'ranger': 'ranger', 'ranger_steel': 'ranger_steel'}, PIECES),
	'settler': ({'settler': 'settler'}, PIECES),
	'juggernaut': ({'juggernaut': 'juggernaut'}, PIECES),
	'scoundrel': ({'scoundrel': 'scoundrel'}, PIECES),
	'sanguine': ({'sanguine': 'sanguine', 'sanguine_blue': 'sanguine_blue', 'sanguine_purple': 'sanguine_purple'}, PIECES),
	'despot': ({'despot': 'despot'}, PIECES),
	'foxshade': ({'foxshade': 'foxshade'}, {'chestplate': 'armorBody,armorRightArm,armorLeftArm'}),
}
NAMES = {
	'templar': ('Templar Great Helm', 'Templar Surcoat', 'Templar Tassets', 'Templar Sabatons'),
	'templar_steel': ('Black Templar Great Helm', 'Black Templar Surcoat', 'Black Templar Tassets', 'Black Templar Sabatons'),
	'hedge': ("Hedge Mage's Hat", "Hedge Mage's Robe", "Hedge Mage's Skirts", "Hedge Mage's Boots"),
	'hedge_elite': ("Wine-Red Hedge Hat", "Wine-Red Hedge Robe", "Wine-Red Hedge Skirts", "Wine-Red Hedge Boots"),
	'hedge_ashen': ("Ashen Hedge Hat", "Ashen Hedge Robe", "Ashen Hedge Skirts", "Ashen Hedge Boots"),
	'primalist': ('Primalist Hood', 'Primalist Wrap', 'Primalist Belt', 'Primalist Boots'),
	'ranger': ("Ranger's Plumed Helm", "Ranger's Harness", "Ranger's Tassets", "Ranger's Boots"),
	'ranger_steel': ("Blackened Ranger's Helm", "Blackened Ranger's Harness", "Blackened Ranger's Tassets", "Blackened Ranger's Boots"),
	'settler': ("Settler's Kettle Helm", "Settler's Coat", "Settler's Trousers", "Settler's Boots"),
	'juggernaut': ('Juggernaut Great Helm', 'Juggernaut Plate', 'Juggernaut Tassets', 'Juggernaut Sabatons'),
	'scoundrel': ("Scoundrel's Hood", "Scoundrel's Wraps", "Scoundrel's Leggings", "Scoundrel's Boots"),
	'sanguine': ('Sanguine Hat', 'Sanguine Coat', 'Sanguine Breeches', 'Sanguine Boots'),
	'sanguine_blue': ('Blue Sanguine Hat', 'Blue Sanguine Coat', 'Blue Sanguine Breeches', 'Blue Sanguine Boots'),
	'sanguine_purple': ('Purple Sanguine Hat', 'Purple Sanguine Coat', 'Purple Sanguine Breeches', 'Purple Sanguine Boots'),
	'despot': ("Despot's Tusked Helm", "Despot's Tusked Breastplate", "Despot's Tassets", "Despot's Greaves"),
	'foxshade': (None, 'Foxshade Mantle', None, None),
}
ORDER = ('helmet', 'chestplate', 'leggings', 'boots')
STANDARD = {'bipedHead': 'armorHead', 'bipedBody': 'armorBody', 'bipedWaist': 'armorWaist'}
# Blockbench's template colours (a face nobody painted), and its shaded underside
TEMPLATE = [(67, 232, 141), (246, 136, 136), (93, 190, 246), (248, 221, 114), (235, 247, 252), (180, 212, 225)]
SLATE = (85, 99, 118)


def near(p, colours, d):
	return any(sum((p[i] - c[i]) ** 2 for i in range(3)) < d * d for c in colours)


def convert(geo, name, texture):
	"""The geo's bones as a Blockbench project: x turned back, a mirrored box-uv cube's faces mirrored, and any stray
	bone under a biped bone (a hat's feather, an empty spare) hung under that part's armour bone."""
	bones = {b['name']: b for b in geo['bones']}
	nodes = {}
	for b in geo['bones']:
		cubes = []
		for c in b.get('cubes', []):
			e = g.frontier_cube(c, (0, 0, 0), (0, 0, 0), 1.0)
			if c.get('mirror') and isinstance(c['uv'], list):
				f = e['faces']
				f['east'], f['west'] = f['west'], f['east']
				for face in f.values():
					face['uv'] = [face['uv'][2], face['uv'][1], face['uv'][0], face['uv'][3]]
			if c.get('ours'):
				e['name'] = 'ours'
			cubes.append(e)
		p, rot = b.get('pivot', [0, 0, 0]), b.get('rotation', [0, 0, 0])
		nodes[b['name']] = g.group(b['name'], (-p[0], p[1], p[2]), cubes, (-rot[0], -rot[1], rot[2]))
	roots = []
	for b in geo['bones']:
		parent = b.get('parent')
		if parent in STANDARD and not b['name'].startswith('armor') and STANDARD[parent] in nodes:
			parent = STANDARD[parent]
		if b['name'] in ('armorBody2', 'armorbelt') and not b.get('cubes'):
			continue
		(nodes[parent]['children'] if parent in nodes else roots).append(nodes[b['name']])
	return g.project(name, roots, [g.texture_entry(name, texture)], (geo['description']['texture_width'], geo['description']['texture_height']))


def finish(data):
	"""Paints the faces left in Blockbench's template colours (see the top of the file); returns the texture."""
	im = g.image_of(data['textures'][0])
	px = im.load()

	def rect(face):
		u0, v0, u1, v1 = face['uv']
		return (max(0, int(min(u0, u1))), max(0, int(min(v0, v1))), min(im.width, int(round(max(u0, u1)))), min(im.height, int(round(max(v0, v1)))))

	def bare(box):
		x0, y0, x1, y1 = box
		pts = [px[x, y] for y in range(y0, y1) for x in range(x0, x1) if px[x, y][3]]
		bright = sum(1 for p in pts if near(p, TEMPLATE, 22))
		slate = sum(1 for p in pts if near(p, [SLATE], 20))
		return bright and (bright + slate) >= 0.6 * len(pts)

	def blank(box):
		return not any(px[x, y][3] for y in range(box[1], box[3]) for x in range(box[0], box[2]))

	def area(box):
		return max(0, box[2] - box[0]) * max(0, box[3] - box[1])

	cubes = [{f: rect(fc) for f, fc in e['faces'].items()} for e in data['elements']]
	ours = [e['name'] == 'ours' for e in data['elements']]
	painted = [(box, area(box)) for c in cubes for box in c.values() if area(box) and not bare(box) and not blank(box)]
	swatch = max(painted, key=lambda b: b[1])[0]
	fills = []
	for c, mine in zip(cubes, ours):
		good = sorted((box for box in c.values() if area(box) and not bare(box) and not blank(box)), key=area, reverse=True)
		for f, box in c.items():
			# (a cube of ours has no picture yet: every face of it is filled)
			if area(box) and (bare(box) or mine and blank(box)):
				fills.append((box, good[0] if good else swatch))
	for box, source in fills:
		patch = im.crop(source).resize((box[2] - box[0], box[3] - box[1]), Image.NEAREST)
		patch = Image.eval(patch.convert('RGB'), lambda v: int(v * 0.86)).convert('RGBA')
		im.paste(patch, box[:2])
	px = im.load()
	stray = [(x, y) for y in range(im.height) for x in range(im.width) if px[x, y][3] and near(px[x, y], TEMPLATE[:4], 22)]
	good = [(x, y) for y in range(im.height) for x in range(im.width) if px[x, y][3] and not near(px[x, y], TEMPLATE, 22)]
	for x, y in stray:
		gx, gy = min(good, key=lambda q: (q[0] - x) ** 2 + (q[1] - y) ** 2)
		px[x, y] = px[gx, gy]
	return im


def despot_extras(geo):
	"""The Despot's helm and greaves (ours): a gold pot helm with a brow ridge and two tusks curving up from the jaw, and
	gold greaves with a knee cap, drawn in the sheet's empty lower half (finish() gives them the breastplate's gold)."""
	by = {b['name']: b for b in geo['bones']}
	by['armorHead'].setdefault('cubes', []).extend([
		{'origin': [-4, 24, -4], 'size': [8, 9, 8], 'inflate': 0.75, 'uv': [0, 96], 'ours': True},
		{'origin': [-4.5, 30.2, -5.4], 'size': [9, 2, 2], 'uv': [36, 96], 'ours': True},
		{'origin': [-6.4, 24.5, -5.6], 'size': [2, 2, 2], 'uv': [100, 96], 'ours': True},
		{'origin': [4.4, 24.5, -5.6], 'size': [2, 2, 2], 'uv': [100, 96], 'ours': True},
		{'origin': [-6.7, 26.2, -6.2], 'size': [1, 3, 1], 'pivot': [-6.2, 26.2, -5.7], 'rotation': [-15, 0, 0], 'uv': [104, 100], 'ours': True},
		{'origin': [5.7, 26.2, -6.2], 'size': [1, 3, 1], 'pivot': [6.2, 26.2, -5.7], 'rotation': [-15, 0, 0], 'uv': [104, 100], 'ours': True},
	])
	for side, x in (('Right', -4.0), ('Left', 0.0)):
		by['armor' + side + 'Boot'].setdefault('cubes', []).extend([
			{'origin': [x, 0, -2], 'size': [4, 7, 4], 'inflate': 0.6, 'uv': [0, 114], 'ours': True},
			{'origin': [x + 0.5, 6, -3], 'size': [3, 3, 2], 'uv': [72, 96], 'ours': True},
		])


def despot_paint(im):
	"""The helm's face (a dark eye slit under a band of red stones) and its tusks' bone, over the gold finish() laid."""
	px = im.load()
	for x in range(9, 15):
		px[x, 108] = (28, 18, 10, 255)
	for x in range(8, 16):
		px[x, 106] = (150, 30, 30, 255) if x % 2 else (205, 62, 50, 255)
	for box in ((100, 96, 108, 100), (104, 100, 108, 104)):
		for y in range(box[1], box[3]):
			for x in range(box[0], box[2]):
				v = 0.82 + 0.18 * ((x * 7 + y * 3) % 3) / 2
				px[x, y] = (int(232 * v), int(222 * v), int(190 * v), 255)


# the blue and purple Sanguine sheets lack the hat and the boots: those come from the red sheet, turned to this hue
SANGUINE_HUES = {'sanguine_blue': 0.52, 'sanguine_purple': 0.76}


def sanguine_whole(look, texture):
	"""Where a coloured Sanguine sheet is empty and the red one isn't, the red one's pixel: its reds turned to the look's
	hue, its gold and feather kept."""
	red = Image.open(SRC + 'sanguine.png').convert('RGBA')
	rp, tp = red.load(), texture.load()
	for y in range(red.height):
		for x in range(red.width):
			if tp[x, y][3] or not rp[x, y][3]:
				continue
			r_, g_, b_, a = rp[x, y]
			h, sat, v = colorsys.rgb_to_hsv(r_ / 255, g_ / 255, b_ / 255)
			if (h < 0.06 or h > 0.94) and sat > 0.35:
				r_, g_, b_ = (int(c * 255) for c in colorsys.hsv_to_rgb(SANGUINE_HUES[look], sat * 0.9, v * 0.85))
			tp[x, y] = (r_, g_, b_, a)


def write_bb(data, path):
	with open(path, 'w', encoding='utf-8', newline='\n') as f:
		json.dump(data, f)


def bake(path, out, name, keep=None):
	cmd = [sys.executable, '-I', 'tools/bbmodel_bake.py', path, out, name]
	if keep:
		cmd += ['--keep', keep]
	subprocess.run(cmd, check=True, capture_output=True)


def item(name):
	r.write(r.ASSETS + 'items/' + name + '.json', {'model': {'type': 'minecraft:model', 'model': 'jaymoskingdoms:item/' + name}})
	r.write(r.ASSETS + 'models/item/' + name + '.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jaymoskingdoms:item/' + name}})


def main():
	lang = {}
	with tempfile.TemporaryDirectory() as tmp:
		for model, (looks, pieces) in MODELS.items():
			geo = json.load(open(SRC + model + '.geo.json', encoding='utf-8'))['minecraft:geometry'][0]
			first = True
			for look, tex in looks.items():
				texture = Image.open(SRC + tex + '.png').convert('RGBA')
				shape = copy.deepcopy(geo)
				if look in SANGUINE_HUES:
					sanguine_whole(look, texture)
				if model == 'despot':
					despot_extras(shape)
				if model == 'foxshade':
					by = {b['name']: b for b in shape['bones']}
					by['armorBody']['cubes'] += by['armorWaist'].pop('cubes')
				data = convert(shape, 'frontier_' + look, texture)
				painted = finish(data)
				if model == 'despot':
					despot_paint(painted)
				data['textures'] = [g.texture_entry('frontier_' + look, painted)]
				path = os.path.join(tmp, look + '.bbmodel')
				write_bb(data, path)
				if first:
					bake(path, r.ASSETS.rstrip('/'), 'frontier_' + model)
					first = False
				painted.save(r.ASSETS + 'textures/entity/bb/frontier_' + look + '.png')
				r.SOURCE = path
				for piece, bones in pieces.items():
					name = look + '_' + piece
					r.icon(bones, r.ASSETS + 'textures/item/' + name + '.png')
					item(name)
					lang['item.jaymoskingdoms.' + name] = NAMES[look][ORDER.index(piece)]
				r.write(r.ASSETS + 'equipment/' + look + '.json', {'layers': {}})
				if model == 'settler':
					# the hat alone: the model again without the full helm under it (the head's first cube)
					hat = copy.deepcopy(shape)
					{b['name']: b for b in hat['bones']}['armorHead']['cubes'].pop(0)
					hat_data = convert(hat, 'frontier_settler_hat', painted)
					hat_path = os.path.join(tmp, 'settler_hat.bbmodel')
					write_bb(hat_data, hat_path)
					bake(hat_path, r.ASSETS.rstrip('/'), 'frontier_settler_hat', 'armorHead')
					os.remove(r.ASSETS + 'textures/entity/bb/frontier_settler_hat.png')
					r.SOURCE = hat_path
					r.icon('armorHead', r.ASSETS + 'textures/item/settler_hat_helmet.png')
					item('settler_hat_helmet')
					lang['item.jaymoskingdoms.settler_hat_helmet'] = "Settler's Hat"
					r.write(r.ASSETS + 'equipment/settler_hat.json', {'layers': {}})
				print(look)
	r.write(r.ASSETS + 'lang/parts/frontier-armour-items.json', lang)


if __name__ == '__main__':
	main()
