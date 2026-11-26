"""Vanilla 26.3's spawn egg: an egg with a lit rim top-left, a dark rim bottom-right and a few spots, in a mob's two
colours (docs/Vanilla-Style.md). The outline is the base colour's own darkest shade, never black.

    from spawn_egg import egg
    egg(0xb08a38, 0x7a6230).save("zander_spawn_egg.png")
"""
from PIL import Image

# the egg's silhouette, row by row: (first x, last x), as vanilla's pig egg
ROWS = {1: (6, 9), 2: (5, 10), 3: (4, 11), 4: (3, 12), 5: (3, 12), 6: (2, 13), 7: (2, 13), 8: (2, 13), 9: (2, 13),
		10: (2, 13), 11: (2, 13), 12: (3, 12), 13: (4, 11), 14: (5, 10)}
SPOTS = [(7, 4), (8, 4), (7, 5), (4, 7), (5, 7), (4, 8), (10, 6), (11, 6), (11, 7), (7, 9), (8, 9), (8, 10), (9, 10),
		 (4, 10), (5, 10), (11, 10), (12, 10)]
HIGHLIGHT = [(6, 3), (5, 5), (4, 6)]


def _rgb(c):
	return ((c >> 16) & 255, (c >> 8) & 255, c & 255) if isinstance(c, int) else tuple(c[:3])


def _shade(c, f):
	return tuple(max(0, min(255, int(round(v * f)))) for v in c)


def _light(c, f):
	return tuple(min(255, int(round(v + (255 - v) * f))) for v in c)


def egg(base, spot):
	"""16x16 egg: `base` colour with `spot` colour spots (0xRRGGBB or rgb tuples)."""
	base, spot = _rgb(base), _rgb(spot)
	fill = {(x, y) for y, (a, b) in ROWS.items() for x in range(a, b + 1)}
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	px = img.load()
	for x, y in fill:
		edge = {d for d, (dx, dy) in dict(u=(0, -1), l=(-1, 0), d=(0, 1), r=(1, 0)).items() if (x + dx, y + dy) not in fill}
		if edge:
			dark = bool(edge & {"d", "r"})
			c = _shade(base, 0.52) if dark else _shade(base, 0.74)
			if edge & {"d", "r"} and edge & {"u", "l"}:
				c = _shade(base, 0.6)
		elif (x, y) in SPOTS:
			c = _shade(spot, 0.8) if (x + 1, y) not in SPOTS and (x, y + 1) not in SPOTS else spot
		elif (x, y) in HIGHLIGHT:
			c = _light(base, 0.45)
		else:
			s = x + y
			c = _light(base, 0.18) if s <= 11 else (base if s <= 17 else _shade(base, 0.84))
		px[x, y] = c + (255,)
	return img
