"""
(0.7.321) Notus's and Boreas's hides and glowing markings, from nyxaris' Fire Dragon (Blockbench Workshop, CC BY 4.0;
credits in textures/entity/bb/CREDITS.txt), whose texture tools/bbmodel_bake.py put at textures/entity/bb/fire_dragon.png.

Notus wears it as drawn (hide_0), and three shades of it: redder, more golden, darker. Boreas wears it turned to ice:
the embers pale blue and white, the dark hide slate, in four shades. The texture's right half is the dragon's glow
(its overlay cubes); that half alone is each species' markings, drawn bright in the dark as every dragon's are.

Run from kingdoms/:  python -I tools/gen_fire_dragons.py
"""
import colorsys
import os

from PIL import Image

ASSETS = 'src/main/resources/assets/jaymoskingdoms/textures/entity/'
SOURCE = ASSETS + 'bb/fire_dragon.png'
# per hide: hue turn (degrees), saturation and brightness scales
NOTUS = [(0, 1.0, 1.0), (-9, 1.05, 0.95), (10, 1.0, 1.05), (0, 0.9, 0.78)]
# Boreas: the hue every warm colour turns to, how much colour it keeps, brightness
BOREAS = [(205, 0.55, 1.08), (195, 0.45, 1.12), (215, 0.6, 1.0), (200, 0.35, 0.92)]
# Boreas's glow: the same turn, paler
BOREAS_GLOW = [(200, 0.5, 1.1), (190, 0.35, 1.15), (210, 0.55, 1.05), (195, 0.25, 1.1)]


def recolour(img, fn):
	out = img.copy()
	px = out.load()
	for y in range(out.height):
		for x in range(out.width):
			r, g, b, a = px[x, y]
			if a == 0:
				continue
			h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
			h, s, v = fn(h * 360, s, v)
			r, g, b = colorsys.hsv_to_rgb((h % 360) / 360, max(0, min(1, s)), max(0, min(1, v)))
			px[x, y] = (round(r * 255), round(g * 255), round(b * 255), a)
	return out


def shade(turn, sat, bright):
	return lambda h, s, v: (h + turn, s * sat, v * bright)


def ice(hue, sat, bright):
	return lambda h, s, v: (hue, s * sat, v * bright + (1 - v) * 0.08)


def halves(img):
	"""The hide (the glow half left clear, as the model draws it under the glow), and the glow alone."""
	w = img.width // 2
	glow = Image.new('RGBA', img.size)
	glow.paste(img.crop((w, 0, img.width, img.height)), (w, 0))
	return img, glow


def main():
	src = Image.open(SOURCE).convert('RGBA')
	for species, hides, glows in (('notus', [shade(*n) for n in NOTUS], [shade(*n) for n in NOTUS]),
								  ('boreas', [ice(*b) for b in BOREAS], [ice(*b) for b in BOREAS_GLOW])):
		folder = ASSETS + 'dragon/' + species + '/'
		os.makedirs(folder, exist_ok=True)
		for i, fn in enumerate(hides):
			recolour(src, fn).save(folder + 'hide_%d.png' % i)
		for i, fn in enumerate(glows):
			halves(recolour(src, fn))[1].save(folder + 'marks_%d.png' % i)
	print('notus and boreas: 4 hides, 4 markings each')


if __name__ == '__main__':
	main()
