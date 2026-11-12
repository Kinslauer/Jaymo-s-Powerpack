"""
Paints the muskox's two coats and its spawn egg from nothing (Kingdoms wild/Muskox), and renders previews of its model.
Model and coats follow docs/Muskox-Model-Spec.md (spec first, our own boxes and pixels only: no other mod's or pack's
boxes, UVs or pixels). PARTS below mirrors kingdoms/src/client/java/com/jaymoskingdoms/client/wild/MuskoxModel.java;
change both together.

python -I tools/gen_muskox.py <kingdoms assets dir>                    paint muskox_brown.png, muskox_black.png, the egg
python -I tools/gen_muskox.py <kingdoms assets dir> --preview <dir>/   also render the pose previews into <dir>
"""
import math
import random
import sys

import numpy as np
from PIL import Image, ImageDraw

SEED = 2611
W, H = 128, 64
GROUND = 24.0
# as MuskoxRenderer.SCALE and the calf's size and head (MuskoxModel.CALF_HEAD)
SCALE, CALF, CALF_HEAD = 1.1, 0.5, 1.25

# name -> (parent, pivot, [(x, y, z, w, h, d, u, v)])
PARTS = {
	"body": (None, (0, 0, 0), [(-6, 5, -10, 12, 11, 20, 0, 0), (-5, 2, -11, 10, 4, 10, 64, 0)]),
	"skirt": ("body", (0, 9, 0), [(-7, 0, -11, 14, 11, 22, 0, 31)]),
	"head": (None, (0, 9, -10), [(-4, -4, -7, 8, 8, 7, 72, 31), (-3, 0, -10, 6, 5, 3, 104, 0)]),
	"horns": ("head", (0, 0, 0), [(-4.5, -5, -6, 9, 2, 3, 104, 8),
		(4, -4, -5.5, 2, 8, 2, 64, 14), (-6, -4, -5.5, 2, 8, 2, 64, 14),
		(4, 3, -8.5, 2, 2, 3, 72, 14), (-6, 3, -8.5, 2, 2, 3, 72, 14),
		(5, 0, -8.5, 1, 3, 1, 82, 14), (-6, 0, -8.5, 1, 3, 1, 82, 14)]),
	"right_front_leg": (None, (3.5, 15, -7), [(-2, 0, -2, 4, 9, 4, 72, 46)]),
	"left_front_leg": (None, (-3.5, 15, -7), [(-2, 0, -2, 4, 9, 4, 72, 46)]),
	"right_hind_leg": (None, (3.5, 15, 7), [(-2, 0, -2, 4, 9, 4, 88, 46)]),
	"left_hind_leg": (None, (-3.5, 15, 7), [(-2, 0, -2, 4, 9, 4, 88, 46)]),
}

# the two coats (docs/Muskox-Model-Spec.md, section 4)
COATS = {
	"brown": {
		"hair": (74, 52, 37), "dark": (47, 33, 25), "light": (108, 79, 55), "wool": (136, 114, 92),
		"saddle": (192, 172, 136), "stocking": (216, 206, 186), "face": (54, 39, 30), "muzzle": (172, 156, 134),
	},
	"black": {
		"hair": (40, 35, 34), "dark": (22, 19, 19), "light": (68, 60, 56), "wool": (112, 102, 94),
		"saddle": (164, 154, 136), "stocking": (204, 200, 190), "face": (27, 23, 23), "muzzle": (150, 142, 130),
	},
}
HOOF = (38, 32, 28)
NOSE = (34, 27, 25)
EYE = (14, 11, 10)
EYE_RING = (150, 128, 100)
HORN = (224, 210, 174)
HORN_SHADE = (178, 160, 124)
HORN_TIP = (56, 46, 39)
INSIDE = (30, 22, 18)


def faces(u, v, w, h, d):
	"""A box's faces on the sheet (vanilla cube layout): name -> (x0, y0, x1, y1). `front` faces forward (-z); `right`
	and `left` are the two sides, each with its forward edge next to `front`; `top` has its forward edge at the bottom."""
	return {
		"top": (u + d, v, u + d + w, v + d),
		"bottom": (u + d + w, v, u + d + 2 * w, v + d),
		"right": (u, v + d, u + d, v + d + h),
		"front": (u + d, v + d, u + d + w, v + d + h),
		"left": (u + d + w, v + d, u + 2 * d + w, v + d + h),
		"back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
	}


BARREL = faces(0, 0, 12, 11, 20)
HUMP = faces(64, 0, 10, 4, 10)
SKIRT = faces(0, 31, 14, 11, 22)
SKULL = faces(72, 31, 8, 8, 7)
MUZZLE = faces(104, 0, 6, 5, 3)
BOSS = faces(104, 8, 9, 2, 3)
HORN_DOWN = faces(64, 14, 2, 8, 2)
HORN_HOOK = faces(72, 14, 2, 2, 3)
HORN_POINT = faces(82, 14, 1, 3, 1)
FORELEG = faces(72, 46, 4, 9, 4)
HINDLEG = faces(88, 46, 4, 9, 4)


def mix(a, b, t):
	t = max(0.0, min(1.0, t))
	return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def each(box):
	x0, y0, x1, y1 = box
	for y in range(y0, y1):
		for x in range(x0, x1):
			yield x, y


def paint(coat):
	c = COATS[coat]
	rnd = random.Random(SEED + (0 if coat == "brown" else 7))
	img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
	px = img.load()

	def hair(box, base=None, strand=5, darker_down=0.0, wool=0.0):
		"""Long guard hair over a face: each column a strand of its own tone, running `strand` texels or so before the
		next begins; darker toward the bottom rows by darker_down; `wool` = the share of texels where the pale
		underwool shows through."""
		base = base or c["hair"]
		x0, y0, x1, y1 = box
		for x in range(x0, x1):
			tone, left = 0.0, 0
			for y in range(y0, y1):
				if left <= 0:
					tone = rnd.uniform(-1.0, 1.0)
					left = max(2, int(rnd.gauss(strand, 1.5)))
				left -= 1
				col = mix(base, c["dark"], -tone * 0.55) if tone < 0 else mix(base, c["light"], tone * 0.45)
				if rnd.random() < 0.08:
					col = mix(col, c["light"], 0.5)
				if darker_down:
					col = mix(col, c["dark"], darker_down * (y - y0) / max(1, y1 - y0 - 1))
				if wool and rnd.random() < wool:
					col = mix(col, c["wool"], 0.55)
				px[x, y] = col + (255,)

	def flat(box, colour, noise=0.12):
		for x, y in each(box):
			px[x, y] = mix(colour, c["dark"] if rnd.random() < 0.5 else (255, 255, 255), rnd.random() * noise) + (255,)

	# the barrel: dark hair, with the pale saddle on the back behind the hump (F6), spilling a little down each flank
	hair(BARREL["top"], strand=3, wool=0.05)
	hair(BARREL["bottom"], base=c["dark"])
	for side in ("left", "right", "front", "back"):
		hair(BARREL[side], strand=6, darker_down=0.25)
	tx0, ty0, tx1, ty1 = BARREL["top"]
	for x, y in each(BARREL["top"]):
		# (the top's rows run back to front: row 0 is the rump, the last row the shoulders)
		row, col = y - ty0, x - tx0
		edge = min(row - 2, 11 - row, col + 1, 12 - col)
		if 2 <= row <= 11:
			t = 0.9 if edge >= 2 else 0.6 if edge >= 1 else 0.3
			px[x, y] = mix(px[x, y][:3], mix(c["saddle"], c["wool"], rnd.random() * 0.4), t * rnd.uniform(0.75, 1.0)) + (255,)
	for side in ("left", "right"):
		sx0, sy0, sx1, sy1 = BARREL[side]
		for x, y in each((sx0, sy0, sx1, sy0 + 3)):
			# (the side's columns: forward edge next to the front face)
			back = (sx1 - 1 - x) if side == "right" else (x - sx0)
			z = -10 + back
			if -1 <= z <= 8 and rnd.random() < (0.85 if y == sy0 else 0.55 if y == sy0 + 1 else 0.25):
				px[x, y] = mix(px[x, y][:3], c["saddle"], 0.7) + (255,)

	# the hump over the shoulders (F4): the darkest, thickest hair, a mane standing a little forward of the chest
	for name, box in HUMP.items():
		hair(box, base=mix(c["hair"], c["dark"], 0.35), strand=4 if name == "top" else 5)

	# the skirt (F5): long strands to the knees, darkening downward, the hem ragged (a cut-out here and there in the
	# last two rows so the hair ends unevenly)
	hair(SKIRT["top"], strand=3)
	for x, y in each(SKIRT["bottom"]):
		px[x, y] = mix(INSIDE, c["dark"], rnd.random() * 0.5) + (255,)
	for side in ("left", "right", "front", "back"):
		box = SKIRT[side]
		hair(box, strand=8, darker_down=0.4, wool=0.03)
		x0, y0, x1, y1 = box
		for x in range(x0, x1):
			r = rnd.random()
			cut = 2 if r < 0.22 else 1 if r < 0.55 else 0
			for y in range(y1 - cut, y1):
				px[x, y] = (0, 0, 0, 0)
			if cut < 2 and rnd.random() < 0.5:
				px[x, y1 - cut - 1] = mix(px[x, y1 - cut - 1][:3], c["light"], 0.35) + (255,)

	# the head (F8): a dark face, shaggy cheeks, small dark eyes set wide at the corners, the muzzle pale
	for name, box in SKULL.items():
		hair(box, base=c["face"], strand=4)
	fx0, fy0, fx1, fy1 = SKULL["front"]
	for ex in (fx0, fx1 - 1):
		px[ex, fy0 + 3] = EYE + (255,)
		px[ex, fy0 + 2] = mix(c["face"], EYE_RING, 0.35) + (255,)
	rx0, ry0, rx1, ry1 = SKULL["right"]
	px[rx1 - 1, ry0 + 3] = EYE + (255,)
	px[rx1 - 2, ry0 + 3] = mix(c["face"], EYE_RING, 0.3) + (255,)
	lx0, ly0, lx1, ly1 = SKULL["left"]
	px[lx0, ly0 + 3] = EYE + (255,)
	px[lx0 + 1, ly0 + 3] = mix(c["face"], EYE_RING, 0.3) + (255,)
	# a little paler down the bridge of the nose toward the muzzle
	for y in range(fy0 + 4, fy1):
		for x in range(fx0 + 2, fx1 - 2):
			px[x, y] = mix(px[x, y][:3], c["muzzle"], 0.12 + 0.1 * (y - fy0 - 4)) + (255,)
	for name, box in MUZZLE.items():
		if name == "top":
			# the face's dark hair runs down onto the bridge
			hair(box, base=mix(c["face"], c["muzzle"], 0.25), strand=3)
		else:
			flat(box, c["muzzle"] if name != "bottom" else mix(c["muzzle"], c["face"], 0.5), noise=0.08)
	mx0, my0, mx1, my1 = MUZZLE["front"]
	for x in range(mx0, mx1):
		px[x, my0] = mix(c["face"], c["muzzle"], 0.45) + (255,)
	# two nostrils, wide apart, and the line of the lips
	for x in (mx0 + 1, mx1 - 2):
		px[x, my0 + 2] = NOSE + (255,)
	for x in range(mx0 + 1, mx1 - 1):
		px[x, my1 - 1] = mix(c["muzzle"], NOSE, 0.4) + (255,)
	for side in ("left", "right"):
		sx0, sy0, sx1, sy1 = MUZZLE[side]
		for x in range(sx0, sx1):
			px[x, sy0] = mix(c["face"], c["muzzle"], 0.45) + (255,)
			px[x, sy1 - 1] = mix(c["muzzle"], NOSE, 0.4) + (255,)

	# the horns (F7): a broad pale boss across the brow, parted down the middle; each horn darkening to its tip
	def horn(box, shade_from=0.0, shade_to=0.0, down=True):
		x0, y0, x1, y1 = box
		for x, y in each(box):
			t = (y - y0) / max(1, y1 - y0 - 1) if down else 1.0 - (y - y0) / max(1, y1 - y0 - 1)
			col = mix(HORN, HORN_SHADE, shade_from + (shade_to - shade_from) * t + rnd.uniform(-0.08, 0.08))
			px[x, y] = col + (255,)

	for name, box in BOSS.items():
		horn(box, 0.05, 0.3)
		x0, y0, x1, y1 = box
		if name in ("top", "front", "back", "bottom"):
			for y in range(y0, y1):
				px[x0 + 4, y] = mix(HORN_SHADE, HORN_TIP, 0.45) + (255,)
			if name == "top":
				# growth ridges across each half
				for x in (x0 + 2, x0 + 6):
					for y in range(y0, y1):
						px[x, y] = mix(px[x, y][:3], HORN_SHADE, 0.6) + (255,)
	for box in HORN_DOWN.values():
		horn(box, 0.15, 0.75)
	for box in HORN_HOOK.values():
		for x, y in each(box):
			px[x, y] = mix(HORN_SHADE, HORN_TIP, rnd.uniform(0.35, 0.6)) + (255,)
	for box in HORN_POINT.values():
		x0, y0, x1, y1 = box
		for x, y in each(box):
			px[x, y] = mix(HORN_TIP, HORN_SHADE, 0.35 * (y - y0) / max(1, y1 - y0 - 1)) + (255,)

	# the legs (F9): short and stout, hair above, pale stockings below, dark hooves
	for legs in (FORELEG, HINDLEG):
		for name, box in legs.items():
			if name == "top":
				hair(box)
				continue
			if name == "bottom":
				flat(box, HOOF)
				continue
			x0, y0, x1, y1 = box
			hair(box, strand=4)
			for x, y in each(box):
				row = y - y0
				if row >= 8:
					px[x, y] = mix(HOOF, c["dark"], rnd.random() * 0.3) + (255,)
				elif row >= 4:
					px[x, y] = mix(c["stocking"], c["wool"], rnd.random() * 0.35 + (0.45 if row == 4 and rnd.random() < 0.6 else 0.0)) + (255,)
	return img


def egg():
	"""A spawn egg in vanilla's shape: dark hair shell, pale saddle-and-horn spots."""
	c = COATS["brown"]
	rnd = random.Random(SEED + 1)
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	px = img.load()
	for y in range(16):
		for x in range(16):
			cx, cy = 7.5, 8.6
			rx = 5.2 if y > 8 else 5.2 - (8.6 - y) * 0.28
			ry = 7.0
			r = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2
			if r <= 1.0:
				col = mix(c["hair"], c["dark"], 0.6) if r > 0.72 else c["hair"]
				if r <= 0.72 and x < cx - 1 and y < cy - 1:
					col = mix(col, c["light"], 0.5)
				px[x, y] = col + (255,)
	for sx, sy in ((5, 5), (9, 4), (10, 9), (6, 11), (8, 7)):
		for dx, dy in ((0, 0), (1, 0), (0, 1)):
			if px[sx + dx, sy + dy][3]:
				px[sx + dx, sy + dy] = mix(c["saddle"], HORN, rnd.random() * 0.5) + (255,)
	return img


# ---- preview: a small z-buffered software rasteriser of the boxes ----------------------------------------------------

def rot(v, r):
	x, y, z = v
	rx, ry, rz = r
	cs, sn = math.cos(rx), math.sin(rx)
	y, z = y * cs - z * sn, y * sn + z * cs
	cs, sn = math.cos(ry), math.sin(ry)
	x, z = x * cs + z * sn, -x * sn + z * cs
	cs, sn = math.cos(rz), math.sin(rz)
	x, y = x * cs - y * sn, x * sn + y * cs
	return (x, y, z)


def box_faces(x0, y0, z0, w, h, d, u, v):
	"""The six quads of a box with their texture corners, as the game's cube builder lays them, and each one's shade."""
	x1, y1, z1 = x0 + w, y0 + h, z0 + d
	V = [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]
	f4, f5, f6, f7, f8, f9 = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
	f10, f11, f12 = v, v + d, v + d + h
	polys = [((5, 4, 0, 1), f5, f10, f6, f11, 1.0), ((2, 3, 7, 6), f6, f11, f7, f10, 0.5), ((0, 4, 7, 3), f4, f11, f5, f12, 0.68),
		((1, 0, 3, 2), f5, f11, f6, f12, 0.84), ((5, 1, 2, 6), f6, f11, f8, f12, 0.68), ((4, 5, 6, 7), f8, f11, f9, f12, 0.84)]
	return [([V[i] for i in idx], [(u2, v1), (u1, v1), (u1, v2), (u2, v2)], shade) for idx, u1, v1, u2, v2, shade in polys]


def place(name, pose, v):
	"""A point of part `name` into model space, through its parents (pose: name -> (rx, ry, rz, dx, dy, dz[, scale]))."""
	parent, pivot, _ = PARTS[name]
	d = pose.get(name, (0, 0, 0, 0, 0, 0))
	s = d[6] if len(d) > 6 else 1.0
	p = rot((v[0] * s, v[1] * s, v[2] * s), d[:3])
	p = (p[0] + pivot[0] + d[3], p[1] + pivot[1] + d[4], p[2] + pivot[2] + d[5])
	return place(parent, pose, p) if parent else p


def hidden(name, hide):
	while name:
		if name in hide:
			return True
		name = PARTS[name][0]
	return False


class Canvas:
	def __init__(self, size, sky=(196, 214, 226), ground=(226, 232, 236), horizon=0.6):
		self.w, self.h = size
		self.rgb = np.zeros((self.h, self.w, 3), dtype=np.float32)
		self.rgb[:int(self.h * horizon)] = sky
		self.rgb[int(self.h * horizon):] = ground
		self.depth = np.full((self.h, self.w), 1e9, dtype=np.float32)

	def quad(self, pts, uv, tex, shade):
		for a, b, c in ((0, 1, 2), (0, 2, 3)):
			self.tri([pts[a], pts[b], pts[c]], [uv[a], uv[b], uv[c]], tex, shade)

	def tri(self, p, t, tex, shade):
		(x0, y0, z0), (x1, y1, z1), (x2, y2, z2) = p
		area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
		if abs(area) < 1e-9:
			return
		xa, xb = max(0, int(math.floor(min(x0, x1, x2)))), min(self.w - 1, int(math.ceil(max(x0, x1, x2))))
		ya, yb = max(0, int(math.floor(min(y0, y1, y2)))), min(self.h - 1, int(math.ceil(max(y0, y1, y2))))
		if xa > xb or ya > yb:
			return
		gx, gy = np.meshgrid(np.arange(xa, xb + 1) + 0.5, np.arange(ya, yb + 1) + 0.5)
		w1 = ((gx - x0) * (y2 - y0) - (x2 - x0) * (gy - y0)) / area
		w2 = ((x1 - x0) * (gy - y0) - (gx - x0) * (y1 - y0)) / area
		w0 = 1.0 - w1 - w2
		inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
		z = w0 * z0 + w1 * z1 + w2 * z2
		tu = np.clip((w0 * t[0][0] + w1 * t[1][0] + w2 * t[2][0]).astype(np.int32), 0, W - 1)
		tv = np.clip((w0 * t[0][1] + w1 * t[1][1] + w2 * t[2][1]).astype(np.int32), 0, H - 1)
		texel = tex[tv, tu]
		view = self.depth[ya:yb + 1, xa:xb + 1]
		ok = inside & (texel[..., 3] > 127) & (z < view)
		view[ok] = z[ok]
		self.rgb[ya:yb + 1, xa:xb + 1][ok] = texel[..., :3][ok] * shade

	def image(self):
		return Image.fromarray(np.clip(self.rgb, 0, 255).astype(np.uint8), "RGB")


def draw(canvas, tex, pose, at=(0.0, 0.0), size=1.0, hide=(), yaw=42.0, pitch=-14.0, zoom=9.0, origin=(0.5, 0.6)):
	"""Draws one muskox standing at `at` (model texels across, along) on the ground, `size` times life size."""
	ya, pi = math.radians(yaw), math.radians(pitch)
	ox, oy = canvas.w * origin[0], canvas.h * origin[1]
	for name, (_, _, boxes) in PARTS.items():
		if hidden(name, hide):
			continue
		for b in boxes:
			for verts, uv, shade in box_faces(*b):
				pts = []
				for vv in verts:
					x, y, z = place(name, pose, vv)
					x, y, z = x * size + at[0], (y - GROUND) * size, z * size + at[1]
					cs, sn = math.cos(ya), math.sin(ya)
					x, z = x * cs + z * sn, -x * sn + z * cs
					cs, sn = math.cos(pi), math.sin(pi)
					y, z = y * cs - z * sn, y * sn + z * cs
					pts.append((ox + x * zoom, oy + y * zoom, z))
				canvas.quad(pts, uv, tex, shade)


def shadow(canvas, at=(0.0, 0.0), size=1.0, yaw=42.0, pitch=-14.0, zoom=9.0, origin=(0.5, 0.6)):
	"""A soft oval on the snow under the animal."""
	ya, pi = math.radians(yaw), math.radians(pitch)
	ox, oy = canvas.w * origin[0], canvas.h * origin[1]
	pts = []
	for i in range(40):
		a = i / 40.0 * math.tau
		x, z = math.cos(a) * 9.0 * size + at[0], math.sin(a) * 16.0 * size + at[1] - 3.0 * size
		cs, sn = math.cos(ya), math.sin(ya)
		x, z = x * cs + z * sn, -x * sn + z * cs
		y = -z * math.sin(pi)
		pts.append((ox + x * zoom, oy + y * zoom))
	layer = Image.new("L", (canvas.w, canvas.h), 0)
	ImageDraw.Draw(layer).polygon(pts, fill=255)
	mask = np.asarray(layer) > 0
	canvas.rgb[mask] *= 0.86


def poses():
	"""The model's poses as MuskoxModel.setupAnim makes them."""
	walk = 0.45
	stride = {"right_front_leg": (-walk, 0, 0, 0, 0, 0), "left_front_leg": (walk, 0, 0, 0, 0, 0),
		"right_hind_leg": (walk, 0, 0, 0, 0, 0), "left_hind_leg": (-walk, 0, 0, 0, 0, 0)}
	graze = {"head": (1.0, 0, 0, 0, 3.0, 0)}
	run = {"right_front_leg": (-0.8, 0, 0, 0, 0, 0), "left_front_leg": (-0.8, 0, 0, 0, 0, 0), "right_hind_leg": (0.8, 0, 0, 0, 0, 0),
		"left_hind_leg": (0.8, 0, 0, 0, 0, 0), "head": (0.3, 0, 0, 0, 1.0, 0), "body": (-0.05, 0, 0, 0, 0, 0), "skirt": (0.08, 0, 0, 0, 0, 0)}
	calf = dict(stride, head=(0.0, 0.35, 0, 0, 0, 0, CALF_HEAD))
	return {"stand": {}, "walk": stride, "graze": graze, "run": run, "calf": calf}


def previews(coats, out):
	ps = poses()
	size = (560, 420)
	tex = {name: np.asarray(img).astype(np.float32) for name, img in coats.items()}
	scenes = []

	def scene(name, label, fn):
		canvas = Canvas(size)
		fn(canvas)
		img = canvas.image()
		ImageDraw.Draw(img).text((12, 10), label, fill=(30, 34, 40))
		img.save(out + "muskox_" + name + ".png")
		scenes.append(img)

	def one(pose, coat="brown", **view):
		def fn(canvas):
			shadow(canvas, size=SCALE, **view)
			draw(canvas, tex[coat], pose, size=SCALE, **view)
		return fn

	def pair(canvas):
		shadow(canvas, at=(-9.0, -8.0), size=SCALE)
		draw(canvas, tex["black"], ps["stand"], at=(-9.0, -8.0), size=SCALE)
		shadow(canvas, at=(19.0, 4.0), size=SCALE * CALF)
		draw(canvas, tex["brown"], ps["calf"], at=(19.0, 4.0), size=SCALE * CALF, hide=("horns", "skirt"))

	def turnaround(canvas):
		for i, (yaw, coat) in enumerate(((90.0, "brown"), (0.0, "black"), (180.0, "brown"))):
			draw(canvas, tex[coat], ps["stand"], size=SCALE, yaw=yaw, pitch=-4.0, zoom=5.6, origin=(0.27 + 0.28 * i, 0.72))

	scene("stand", "stand (brown coat)", one(ps["stand"]))
	scene("graze", "graze: head down to the lichen (brown coat)", one(ps["graze"]))
	scene("run", "run: fleeing gallop (black coat)", one(ps["run"], "black"))
	scene("calf", "calf (no horns, short coat) beside an adult (black coat)", pair)
	scene("turnaround", "side (brown), front (black), back (brown)", turnaround)
	sheet = Image.new("RGB", (size[0] * 2, size[1] * 2))
	for i, img in enumerate(scenes[:4]):
		sheet.paste(img, ((i % 2) * size[0], (i // 2) * size[1]))
	sheet.save(out + "muskox_poses.png")


def main():
	assets = sys.argv[1].rstrip("/")
	coats = {name: paint(name) for name in COATS}
	for name, img in coats.items():
		img.save(assets + "/textures/entity/wild/muskox_" + name + ".png")
	egg().save(assets + "/textures/item/muskox_spawn_egg.png")
	if "--preview" in sys.argv:
		out = sys.argv[sys.argv.index("--preview") + 1]
		previews(coats, out if out.endswith("/") else out + "/")


if __name__ == "__main__":
	main()
