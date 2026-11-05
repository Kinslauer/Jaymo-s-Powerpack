"""
Paints the grizzly mini-boss's coat and spawn egg (Kingdoms wild/Grizzly) from Bear Minimum's brown bear (Apache-2.0,
by Startraveler / scientistknight1, https://github.com/Thomas1034/bear-minimum). Changes made here: the coat is darkened,
silver-tipped along the hump, back and crown, given a pale muzzle, black claws and old scars; the egg is re-tinted.

python -I tools/gen_grizzly.py <bearminimum-fabric-26.3-1.0.5.jar> <kingdoms assets dir>

The UV map is the brown bear's (128 x 64): head 9x8x9 at (0,0), muzzle 5x4x4 at (0,56), ears at (21,61), rear body
14x14x11 at (0,30), shoulders 12x12x12 at (37,0), hind legs 4x10x8 at (50,30), front legs 4x10x6 at (50,48). Both body
boxes lie on their front face, so a box's back face is the bear's back. Our shoulder hump is 8x9x3 at (88,0), over the
brown bear's palette swatches (not on any of its boxes).
"""
import io
import random
import sys
import zipfile

from PIL import Image

SEED = 1871


def faces(u, v, w, h, d):
	"""A box's faces on the sheet: name -> (x0, y0, x1, y1)."""
	return {
		"top": (u + d, v, u + d + w, v + d),
		"bottom": (u + d + w, v, u + d + 2 * w, v + d),
		"right": (u, v + d, u + d, v + d + h),
		"front": (u + d, v + d, u + d + w, v + d + h),
		"left": (u + d + w, v + d, u + 2 * d + w, v + d + h),
		"back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
	}


HEAD = faces(0, 0, 9, 8, 9)
MUZZLE = faces(0, 56, 5, 4, 4)
REAR = faces(0, 30, 14, 14, 11)
SHOULDERS = faces(37, 0, 12, 12, 12)
HIND = faces(50, 30, 4, 10, 8)
FRONT = faces(50, 48, 4, 10, 6)
# (ours) the shoulder hump, 8x9x3 at (88,0), where the brown bear's sheet has only its palette swatches
HUMP = faces(88, 0, 8, 9, 3)

# the grizzly's palette: deep brown base, silver-blond tips, cream muzzle, scar pink-grey, claw black
BASE_DARKEN = 0.78
TIP = (178, 156, 118)
MUZZLE_TONE = (150, 118, 84)
SCAR = (124, 86, 78)
CLAW = (28, 22, 20)


def mix(a, b, t):
	return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def each(box):
	x0, y0, x1, y1 = box
	for y in range(y0, y1):
		for x in range(x0, x1):
			yield x, y


def paint(coat):
	rnd = random.Random(SEED)
	px = coat.load()
	w, h = coat.size

	# 1. the whole coat a shade darker and a touch warmer
	for y in range(h):
		for x in range(w):
			r, g, b, a = px[x, y]
			if a:
				px[x, y] = (int(r * BASE_DARKEN), int(g * BASE_DARKEN * 0.96), int(b * BASE_DARKEN * 0.9), a)

	def tip(box, chance, fade=None):
		"""Silver tips scattered over a face, thicker toward one edge (fade: 'up' = toward its top row)."""
		x0, y0, x1, y1 = box
		for x, y in each(box):
			r, g, b, a = px[x, y]
			if not a:
				continue
			p = chance
			if fade == "up":
				p *= 1.0 - (y - y0) / max(1, y1 - y0)
			elif fade == "down":
				p *= (y - y0 + 1) / max(1, y1 - y0)
			# frosted: every hair a little toward the tip colour, some much more
			t = p * (0.5 + rnd.random() * 0.7)
			if rnd.random() < p * 0.35:
				t += 0.25
			px[x, y] = mix((r, g, b), TIP, min(0.85, t)) + (a,)

	# 1b. the swatch corner cleared, and the hump's faces filled with the shoulders' fur (painted on in step 2)
	for y in range(h):
		for x in range(86, w):
			px[x, y] = (0, 0, 0, 0)
	sx0, sy0, sx1, sy1 = SHOULDERS["left"]
	for box in HUMP.values():
		for x, y in each(box):
			px[x, y] = px[sx0 + (x * 7 + y) % (sx1 - sx0), sy0 + (y * 3 + x) % (sy1 - sy0)]

	# 2. the grizzled saddle: the back and the shoulder hump thickest, fading down the flanks; the crown and neck
	tip(REAR["back"], 0.5)
	tip(SHOULDERS["back"], 0.7)
	for side in ("left", "right"):
		# the box's sides are the bear's flanks; its back edge is the side face's... far column, so fade by column
		for box, chance in ((REAR[side], 0.4), (SHOULDERS[side], 0.55)):
			x0, y0, x1, y1 = box
			for x, y in each(box):
				r, g, b, a = px[x, y]
				if not a:
					continue
				# the flank's dorsal edge is the column next to the back face: right face's left edge, left face's right edge
				t = (x - x0) / max(1, x1 - x0 - 1)
				near_back = 1.0 - t if side == "right" else t
				p = chance * near_back ** 1.5
				t = p * (0.5 + rnd.random() * 0.7) + (0.25 if rnd.random() < p * 0.35 else 0.0)
				px[x, y] = mix((r, g, b), TIP, min(0.85, t)) + (a,)
	tip(SHOULDERS["top"], 0.45)
	tip(HUMP["back"], 0.85)
	tip(HUMP["left"], 0.6)
	tip(HUMP["right"], 0.6)
	tip(HUMP["top"], 0.6)
	tip(HUMP["bottom"], 0.6)
	tip(HEAD["top"], 0.5)
	tip(HEAD["back"], 0.5)
	tip(HEAD["left"], 0.35, "up")
	tip(HEAD["right"], 0.35, "up")
	tip(HEAD["front"], 0.2, "up")

	# 3. a pale, dirty muzzle (not its black nose: keep the darkest pixels)
	for name in ("front", "top", "left", "right", "bottom"):
		for x, y in each(MUZZLE[name]):
			r, g, b, a = px[x, y]
			if a and r + g + b > 90:
				px[x, y] = mix((r, g, b), MUZZLE_TONE, 0.6) + (a,)

	# 4. dark stockings and black claws: the legs darker toward the paw, the last row of the front face claws
	for legs in (HIND, FRONT):
		for name in ("front", "back", "left", "right"):
			x0, y0, x1, y1 = legs[name]
			for x, y in each(legs[name]):
				r, g, b, a = px[x, y]
				if a:
					t = (y - y0) / max(1, y1 - y0 - 1)
					k = 1.0 - 0.35 * t
					px[x, y] = (int(r * k), int(g * k), int(b * k), a)
		x0, y0, x1, y1 = legs["front"]
		for x in range(x0, x1):
			if (x - x0) % 2 == 0:
				px[x, y1 - 1] = CLAW + (255,)

	# 5. old scars: two raked across the left of its face, two across its right flank
	def rake(x, y, n, step, length):
		for i in range(n):
			for j in range(length):
				sx, sy = x + i * step + j, y + j
				if 0 <= sx < w and 0 <= sy < h and px[sx, sy][3]:
					px[sx, sy] = SCAR + (255,)

	hx0, hy0, hx1, hy1 = HEAD["front"]
	rake(hx0 + 1, hy0 + 1, 2, 3, 3)
	rx0, ry0, rx1, ry1 = REAR["right"]
	rake(rx0 + 3, ry0 + 4, 2, 3, 5)
	return coat


def egg(src):
	"""Bear Minimum's brown bear egg, re-tinted: darker shell, silver spots."""
	src = src.convert("RGBA")
	px = src.load()
	for y in range(src.size[1]):
		for x in range(src.size[0]):
			r, g, b, a = px[x, y]
			if not a:
				continue
			lum = (r + g + b) / 3
			if lum > 120:
				px[x, y] = mix((r, g, b), TIP, 0.7) + (a,)
			else:
				px[x, y] = (int(r * 0.7), int(g * 0.66), int(b * 0.6), a)
	return src


def main():
	jar, assets = sys.argv[1], sys.argv[2].rstrip("/")
	with zipfile.ZipFile(jar) as z:
		coat = Image.open(io.BytesIO(z.read("assets/bearminimum/textures/entity/bear/brown_bear.png"))).convert("RGBA")
		shell = Image.open(io.BytesIO(z.read("assets/bearminimum/textures/item/brown_bear_spawn_egg.png")))
	paint(coat).save(assets + "/textures/entity/wild/grizzly.png")
	egg(shell).save(assets + "/textures/item/grizzly_spawn_egg.png")


if __name__ == "__main__":
	main()
