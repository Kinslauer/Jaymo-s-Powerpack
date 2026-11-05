"""
(0.7.329) Eurus's and Aeolus's hides and glowing markings, from eanncrod's Kintsugi wyvern (Blockbench Workshop,
CC BY 4.0; credits in textures/entity/bb/CREDITS.txt), whose texture tools/bbmodel_bake.py put at
textures/entity/bb/kintsugi.png: a near-black hide broken by gold seams, as a pot mended with gold.

Aeolus, the father of the winds, is worn grey-white with age: the hide lifted pale in four shades, the gold seams kept.
His markings are those seams glowing: gold, then patched in his four sons' colours (Notus's ember, Boreas's ice,
Zephyrus's acid green, Eurus's storm violet), white-gold, and silver. Eurus is the storm: the hide slate and
blue-violet in four shades, his seams glowing electric pale cyan.

(0.7.522) Ignivorus, the Nether's fire dragon (docs/SaintsDragons-Plan.md, its moves after Saint's Dragons' Ignivorus,
none of that mod's art used), is the same wyvern cooled from lava: obsidian, basalt ash, ember and crimson hides, the
seams turned to lava cracks glowing orange, magma yellow, white-hot or crimson fire.

Run from kingdoms/:  python -I tools/gen_kintsugi_dragons.py
"""
import colorsys
import os

from PIL import Image

ASSETS = 'src/main/resources/assets/jaymoskingdoms/textures/entity/'
SOURCE = ASSETS + 'bb/kintsugi.png'
# Aeolus's hides: how pale the darkest hide goes, how much of the drawn shading is kept, a warm tint (hue, saturation)
AEOLUS = [(0.52, 0.45, 40, 0.06), (0.6, 0.38, 40, 0.04), (0.45, 0.5, 30, 0.08), (0.55, 0.42, 210, 0.05)]
# Eurus's hides: floor, shading kept, hue, saturation (storm slate, blue-violet, charcoal, pale thundercloud)
EURUS = [(0.16, 0.7, 225, 0.3), (0.14, 0.7, 255, 0.4), (0.08, 0.6, 230, 0.15), (0.3, 0.65, 215, 0.2)]
# Ignivorus's hides: obsidian, basalt ash, ember, crimson
IGNIVORUS = [(0.05, 0.5, 8, 0.45), (0.1, 0.55, 20, 0.12), (0.1, 0.62, 12, 0.7), (0.12, 0.68, 350, 0.78)]
# the four winds' colours (RGB) for Aeolus's patched seams, and the patch size (texture pixels)
WINDS = [(255, 120, 40), (150, 220, 255), (150, 255, 70), (170, 110, 255)]
PATCH = 48


def seam(r, g, b):
	"""A gold seam pixel: warm yellow and bright."""
	h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
	return s > 0.25 and 0.08 < h < 0.2 and v > 0.45


def recolour(img, hide):
	floor, keep, hue, sat = hide
	out = img.copy()
	px = out.load()
	for y in range(out.height):
		for x in range(out.width):
			r, g, b, a = px[x, y]
			if a == 0 or seam(r, g, b):
				continue
			v = max(r, g, b) / 255
			v = floor + v * keep
			rr, gg, bb = colorsys.hsv_to_rgb(hue / 360, sat, min(1.0, v))
			px[x, y] = (round(rr * 255), round(gg * 255), round(bb * 255), a)
	return out


def marks(img, colour):
	"""The seams alone, each pixel coloured by colour(x, y, brightness)."""
	out = Image.new('RGBA', img.size)
	src, px = img.load(), out.load()
	for y in range(img.height):
		for x in range(img.width):
			r, g, b, a = src[x, y]
			if a and seam(r, g, b):
				px[x, y] = colour(x, y, max(r, g, b) / 255) + (255,)
	return out


def tint(rgb):
	return lambda x, y, v: tuple(min(255, round(c * (0.6 + 0.5 * v))) for c in rgb)


def winds(x, y, v):
	return tint(WINDS[(x // PATCH + y // PATCH) % 4])(x, y, v)


def main():
	src = Image.open(SOURCE).convert('RGBA')
	for species, hides, glows in (
		('aeolus', AEOLUS, [tint((255, 200, 70)), winds, tint((255, 240, 190)), tint((215, 225, 235))]),
		('eurus', EURUS, [tint((150, 235, 255)), tint((120, 200, 255)), tint((190, 240, 255)), tint((170, 160, 255))]),
		('ignivorus', IGNIVORUS, [tint((255, 110, 20)), tint((255, 190, 50)), tint((255, 236, 200)), tint((255, 50, 36))]),
	):
		folder = ASSETS + 'dragon/' + species + '/'
		os.makedirs(folder, exist_ok=True)
		for i, hide in enumerate(hides):
			recolour(src, hide).save(folder + 'hide_%d.png' % i)
		for i, glow in enumerate(glows):
			marks(src, glow).save(folder + 'marks_%d.png' % i)
	print('aeolus, eurus and ignivorus: 4 hides, 4 markings each')
	ignivorus_extras()


def ignivorus_extras():
	"""(0.7.522) Ignivorus's spawn egg, its item model and its loot."""
	import sys
	sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
	from gen_dragons import rgb, write, entry, egg, DATA, ITEM
	for d in (ITEM, 'src/main/resources/assets/jaymoskingdoms/items/', 'src/main/resources/assets/jaymoskingdoms/models/item/',
			DATA + 'jaymoskingdoms/loot_table/entities/'):
		os.makedirs(d, exist_ok=True)
	egg('ignivorus_dragon_spawn_egg', rgb(0x1E1414), rgb(0xFF7A1E), 131)
	write('src/main/resources/assets/jaymoskingdoms/items/ignivorus_dragon_spawn_egg.json',
		{'model': {'type': 'minecraft:model', 'model': 'jaymoskingdoms:item/ignivorus_dragon_spawn_egg'}})
	write('src/main/resources/assets/jaymoskingdoms/models/item/ignivorus_dragon_spawn_egg.json',
		{'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jaymoskingdoms:item/ignivorus_dragon_spawn_egg'}})
	write(DATA + 'jaymoskingdoms/loot_table/entities/ignivorus_dragon.json',
		{'type': 'minecraft:entity', 'pools': [{'rolls': 1, 'entries': [e]} for e in (
			entry('minecraft:leather', 5, 10), entry('minecraft:bone', 4, 8), entry('minecraft:magma_cream', 2, 6),
			entry('minecraft:blaze_powder', 2, 5), entry('minecraft:obsidian', 1, 3), entry('minecraft:netherite_scrap', 0, 1))],
		 'random_sequence': 'jaymoskingdoms:entities/ignivorus_dragon'})


if __name__ == '__main__':
	main()
