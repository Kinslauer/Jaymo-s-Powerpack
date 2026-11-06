"""
Paints the grizzly mini-boss's coat and spawn egg from nothing (Kingdoms wild/Grizzly), and renders previews of its model.
Model and coat follow docs/Grizzly-Model-Spec.md (REA method: spec first, our own boxes and pixels only). PARTS below
mirrors kingdoms/src/client/java/com/jaymoskingdoms/client/wild/GrizzlyModel.java; change both together.

python -I tools/gen_grizzly.py <kingdoms assets dir>                 paint grizzly.png and grizzly_spawn_egg.png
python -I tools/gen_grizzly.py <kingdoms assets dir> --preview out.png   also render the poses sheet
"""
import math
import random
import sys

from PIL import Image, ImageDraw

SEED = 1871
W, H = 128, 64

# name -> (parent, pivot, [(x, y, z, w, h, d, u, v)])
PARTS = {
	"body": (None, (0, 14, 8), [(-7.5, -11, -20, 15, 14, 12, 0, 0), (-5, -14, -19, 10, 4, 9, 54, 0), (-6.5, -8, -8, 13, 12, 12, 0, 26),
		(-1.5, -5, 4, 3, 3, 2, 114, 4)]),
	"head": ("body", (0, -5, -20), [(-5, -5, -8, 10, 9, 8, 54, 13), (-3, -1.5, -13, 6, 4, 5, 92, 0), (-5.5, -7, -4, 3, 3, 1, 114, 0),
		(2.5, -7, -4, 3, 3, 1, 114, 0)]),
	"jaw": ("head", (0, 2.5, -8), [(-2.5, 0, -5, 5, 2, 5, 92, 9)]),
	"right_front_leg": ("body", (4.5, 0, -14), [(-2.5, 0, -3, 5, 10, 6, 54, 30), (-2.5, 9, -5, 5, 1, 2, 100, 30)]),
	"left_front_leg": ("body", (-4.5, 0, -14), [(-2.5, 0, -3, 5, 10, 6, 54, 30), (-2.5, 9, -5, 5, 1, 2, 100, 30)]),
	"right_hind_leg": (None, (4.5, 14, 8), [(-2.5, 0, -3, 5, 10, 7, 76, 30)]),
	"left_hind_leg": (None, (-4.5, 14, 8), [(-2.5, 0, -3, 5, 10, 7, 76, 30)]),
}

BASE = (82, 56, 36)
DARK = (58, 39, 26)
MID = (108, 74, 46)
TIP = (182, 162, 124)
LEG = (46, 31, 21)
MUZZLE = (140, 106, 72)
NOSE = (28, 22, 20)
CLAW = (204, 190, 160)
MOUTH = (120, 52, 50)
TEETH = (224, 218, 200)
EYE = (18, 14, 12)
SCAR = (150, 118, 100)


def faces(u, v, w, h, d):
	"""A box's faces on the sheet (vanilla cube layout): name -> (x0, y0, x1, y1). top = up, front = facing forward."""
	return {
		"top": (u + d, v, u + d + w, v + d),
		"bottom": (u + d + w, v, u + d + 2 * w, v + d),
		"right": (u, v + d, u + d, v + d + h),
		"front": (u + d, v + d, u + d + w, v + d + h),
		"left": (u + d + w, v + d, u + 2 * d + w, v + d + h),
		"back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
	}


CHEST = faces(0, 0, 15, 14, 12)
RUMP = faces(0, 26, 13, 12, 12)
HUMP = faces(54, 0, 10, 4, 9)
SKULL = faces(54, 13, 10, 9, 8)
SNOUT = faces(92, 0, 6, 4, 5)
JAW = faces(92, 9, 5, 2, 5)
EAR = faces(114, 0, 3, 3, 1)
TAIL = faces(114, 4, 3, 3, 2)
FORELEG = faces(54, 30, 5, 10, 6)
HINDLEG = faces(76, 30, 5, 10, 7)
CLAWS = faces(100, 30, 5, 1, 2)


def mix(a, b, t):
	t = max(0.0, min(1.0, t))
	return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def each(box):
	x0, y0, x1, y1 = box
	for y in range(y0, y1):
		for x in range(x0, x1):
			yield x, y


def paint():
	rnd = random.Random(SEED)
	img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
	px = img.load()

	def fur(box, colour=BASE, frost=0.0, frost_from=None, dark_to=0.0):
		"""Fur over a face: noise round the colour, short vertical streaks, frost toward TIP (thicker toward frost_from:
		'up' = the face's top rows, 'all'), darker toward the bottom rows by dark_to."""
		x0, y0, x1, y1 = box
		streak = {x: rnd.random() for x in range(x0, x1)}
		for x, y in each(box):
			n = rnd.random()
			c = mix(colour, DARK, 0.45) if n < 0.18 else mix(colour, MID, 0.6) if n > 0.85 else colour
			if (y + int(streak[x] * 5)) % 4 == 0 and rnd.random() < 0.35:
				c = mix(c, DARK, 0.18)
			hgt = (y - y0) / max(1, y1 - y0 - 1)
			if dark_to:
				c = mix(c, LEG, dark_to * hgt)
			if frost:
				p = frost * ((1.0 - hgt) ** 1.4 if frost_from == "up" else 1.0)
				t = p * (0.4 + rnd.random() * 0.7) + (0.3 if rnd.random() < p * 0.3 else 0.0)
				c = mix(c, TIP, min(0.85, t))
			px[x, y] = c + (255,)

	# the body: frosted saddle over hump, chest and rump, thinning down the flanks
	for name, box in HUMP.items():
		fur(box, frost=0.9 if name in ("top", "back", "left", "right") else 0.6, frost_from=None if name == "top" else "up")
	for part, top in ((CHEST, 0.7), (RUMP, 0.5)):
		fur(part["top"], frost=top)
		fur(part["bottom"], colour=DARK)
		for side in ("left", "right", "front", "back"):
			fur(part[side], frost=top * 0.8, frost_from="up", dark_to=0.35)
	for box in TAIL.values():
		fur(box, frost=0.3)

	# the head: frosted crown, paler cheeks, dark eyes high by the brow, small dark ears
	for name, box in SKULL.items():
		if name == "bottom":
			for x, y in each(box):
				px[x, y] = MOUTH + (255,)
			continue
		fur(box, frost=0.6 if name in ("top", "back") else 0.4, frost_from=None if name in ("top", "back") else "up")
	fx0, fy0, fx1, fy1 = SKULL["front"]
	for ex in (fx0 + 2, fx1 - 3):
		px[ex, fy0 + 3] = EYE + (255,)
		px[ex, fy0 + 2] = mix(BASE, DARK, 0.7) + (255,)
	# two old scars raked over the left of the face (a mini-boss tell)
	for i in range(2):
		for j in range(3):
			x, y = fx1 - 3 + j - i * 2, fy0 + 1 + j + i
			if fx0 <= x < fx1:
				px[x, y] = SCAR + (255,)
	for box in EAR.values():
		fur(box, colour=DARK)
	ex0, ey0, ex1, ey1 = EAR["front"]
	for x, y in each((ex0 + 1, ey0 + 1, ex1 - 1, ey1)):
		px[x, y] = mix(DARK, NOSE, 0.6) + (255,)

	# the muzzle and jaw: tan, a black nose, the mouth's roof and teeth showing when the jaw drops
	for name, box in SNOUT.items():
		if name == "bottom":
			for x, y in each(box):
				px[x, y] = MOUTH + (255,)
			continue
		fur(box, colour=MUZZLE)
	sx0, sy0, sx1, sy1 = SNOUT["front"]
	for x, y in each((sx0 + 2, sy0, sx1 - 2, sy0 + 2)):
		px[x, y] = NOSE + (255,)
	px[sx0 + 1, sy0] = mix(NOSE, MUZZLE, 0.5) + (255,)
	px[sx1 - 2, sy0] = mix(NOSE, MUZZLE, 0.5) + (255,)
	for x in range(sx0, sx1):
		px[x, sy1 - 1] = mix(MUZZLE, DARK, 0.35) + (255,)
	# the upper teeth along the front of the muzzle's underside: seen only when the jaw drops
	bx0, by0, bx1, by1 = SNOUT["bottom"]
	for x in range(bx0, bx1):
		px[x, by1 - 1] = TEETH + (255,)
	for name, box in JAW.items():
		if name == "top":
			for x, y in each(box):
				px[x, y] = MOUTH + (255,)
			jx0, jy0, jx1, jy1 = box
			for x in range(jx0, jx1):
				px[x, jy1 - 1] = TEETH + (255,)
			continue
		fur(box, colour=mix(MUZZLE, BASE, 0.4))

	# legs: dark stockings, darker to the paw; pale horn claws on the forepaws
	for legs in (FORELEG, HINDLEG):
		for name, box in legs.items():
			fur(box, colour=mix(BASE, LEG, 0.25), dark_to=0.5 if name not in ("top", "bottom") else 0.0)
	for name, box in CLAWS.items():
		for x, y in each(box):
			px[x, y] = (CLAW if (x % 2 == 0 or name in ("top", "bottom")) else mix(CLAW, DARK, 0.5)) + (255,)
	return img


def egg():
	"""A spawn egg in vanilla's shape: dark brown shell, silver-tan spots."""
	rnd = random.Random(SEED + 1)
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	px = img.load()
	for y in range(16):
		for x in range(16):
			# an egg: narrower at the top
			cx, cy = 7.5, 8.6
			rx = 5.2 if y > 8 else 5.2 - (8.6 - y) * 0.28
			ry = 7.0
			if ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0:
				edge = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 > 0.72
				c = mix(BASE, DARK, 0.5) if edge else BASE
				if not edge and x < cx - 1 and y < cy - 1:
					c = mix(c, MID, 0.5)
				px[x, y] = c + (255,)
	for sx, sy in ((5, 6), (9, 4), (10, 9), (6, 11), (8, 8)):
		for dx, dy in ((0, 0), (1, 0), (0, 1)):
			if px[sx + dx, sy + dy][3]:
				px[sx + dx, sy + dy] = mix(TIP, MUZZLE, rnd.random() * 0.4) + (255,)
	return img


# ---- preview -------------------------------------------------------------------------------------------------------

def rot(v, r):
	x, y, z = v
	rx, ry, rz = r
	c, s = math.cos(rx), math.sin(rx)
	y, z = y * c - z * s, y * s + z * c
	c, s = math.cos(ry), math.sin(ry)
	x, z = x * c + z * s, -x * s + z * c
	c, s = math.cos(rz), math.sin(rz)
	x, y = x * c - y * s, x * s + y * c
	return (x, y, z)


def box_faces(x0, y0, z0, w, h, d, u, v):
	x1, y1, z1 = x0 + w, y0 + h, z0 + d
	V = [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]
	f4, f5, f6, f7, f8, f9 = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
	f10, f11, f12 = v, v + d, v + d + h
	polys = [((5, 4, 0, 1), f5, f10, f6, f11), ((2, 3, 7, 6), f6, f11, f7, f10), ((0, 4, 7, 3), f4, f11, f5, f12),
		((1, 0, 3, 2), f5, f11, f6, f12), ((5, 1, 2, 6), f6, f11, f8, f12), ((4, 5, 6, 7), f8, f11, f9, f12)]
	return [([V[i] for i in idx], [(u2, v1), (u1, v1), (u1, v2), (u2, v2)]) for idx, u1, v1, u2, v2 in polys]


def world(name, pose, v):
	"""A point of part `name` into model space, through its parents (pose: name -> (rx, ry, rz, dx, dy, dz))."""
	parent, pivot, _ = PARTS[name]
	d = pose.get(name, (0, 0, 0, 0, 0, 0))
	p = rot(v, d[:3])
	p = (p[0] + pivot[0] + d[3], p[1] + pivot[1] + d[4], p[2] + pivot[2] + d[5])
	return world(parent, pose, p) if parent else p


def render(tex, pose, yaw, pitch, scale=8.0, size=(460, 400), cy=0.42):
	img = Image.new("RGBA", size, (0, 0, 0, 0))
	faces_ = []
	for name, (_, _, boxes) in PARTS.items():
		for b in boxes:
			for verts, uv in box_faces(*b):
				wv = []
				for vv in verts:
					x, y, z = world(name, pose, vv)
					c, s = math.cos(yaw), math.sin(yaw)
					x, z = x * c + z * s, -x * s + z * c
					c, s = math.cos(pitch), math.sin(pitch)
					y, z = y * c - z * s, y * s + z * c
					wv.append((x, y, z))
				(ax, ay, _), (bx, by, _), (cx_, cy_, _) = wv[0], wv[1], wv[2]
				if (bx - ax) * (cy_ - ay) - (by - ay) * (cx_ - ax) <= 0:
					continue
				faces_.append((sum(p[2] for p in wv) / 4, wv, uv))
	faces_.sort(key=lambda f: -f[0])
	ox, oy = size[0] / 2, size[1] * cy
	for _, wv, uv in faces_:
		pts = [(ox + p[0] * scale, oy + (p[1] - 14) * scale) for p in wv]
		(x0, y0), (x1, y1), (x3, y3) = pts[0], pts[1], pts[3]
		(u0, v0), (u1, v1), (u3, v3) = uv[0], uv[1], uv[3]
		det = (x1 - x0) * (y3 - y0) - (x3 - x0) * (y1 - y0)
		if abs(det) < 1e-6:
			continue

		def solve(q0, q1, q3):
			a = ((q1 - q0) * (y3 - y0) - (q3 - q0) * (y1 - y0)) / det
			b = ((x1 - x0) * (q3 - q0) - (x3 - x0) * (q1 - q0)) / det
			return a, b, q0 - a * x0 - b * y0
		a, b, c = solve(u0, u1, u3)
		d, e, f = solve(v0, v1, v3)
		layer = tex.transform(size, Image.AFFINE, (a, b, c, d, e, f), resample=Image.NEAREST)
		mask = Image.new("L", size, 0)
		ImageDraw.Draw(mask).polygon(pts, fill=255)
		img.paste(layer, (0, 0), Image.composite(layer.split()[3], Image.new("L", size, 0), mask))
	return img


def poses():
	"""The model's poses as GrizzlyModel.setupAnim makes them (at the move's peak)."""
	up = 1.0
	rear = {"body": (-1.15 * up, 0, 0, 0, 0, 0), "right_front_leg": (0.55, 0, 0, 0, 0, 0), "left_front_leg": (0.55, 0, 0, 0, 0, 0),
		"head": (0.75, 0, 0, 0, 0, 0)}
	roar = dict(rear, head=(0.35, 0, 0, 0, 0, 0), jaw=(0.75, 0, 0, 0, 0, 0))
	swipe = {"left_front_leg": (-1.5, 0, -0.5, 0, -2, 0), "head": (0, 0.3, 0, 0, 0, 0), "body": (0, -0.08, 0, 0, 0, 0)}
	charge = {"right_front_leg": (-0.9, 0, 0, 0, 0, 0), "left_front_leg": (-0.9, 0, 0, 0, 0, 0), "right_hind_leg": (0.9, 0, 0, 0, 0, 0),
		"left_hind_leg": (0.9, 0, 0, 0, 0, 0), "head": (0.3, 0, 0, 0, 0, 0), "body": (0.06, 0, 0, 0, 0, 0)}
	maul = {"body": (0.18, 0, 0, 0, 0, 0), "right_front_leg": (-0.75, 0, 0, 0, 0, 0), "left_front_leg": (-0.75, 0, 0, 0, 0, 0),
		"head": (0.5, 0.2, 0, 0, 0, 0), "jaw": (0.6, 0, 0, 0, 0, 0)}
	return [("at rest", {}), ("reared up (warning, slam)", rear), ("enraged roar", roar), ("paw swipe", swipe), ("charge", charge),
		("maul (biting)", maul)]


def sheet(tex, out):
	ps = poses()
	cw, ch = 460, 400
	img = Image.new("RGBA", (cw * 3, ch * 2), (135, 160, 120, 255))
	d = ImageDraw.Draw(img)
	for i, (label, pose) in enumerate(ps):
		v = render(tex, pose, math.radians(-125), math.radians(-12), size=(cw, ch), cy=0.62 if "rear" in label or "roar" in label else 0.42)
		img.alpha_composite(v, ((i % 3) * cw, (i // 3) * ch))
		d.text(((i % 3) * cw + 12, (i // 3) * ch + 10), label, fill=(20, 20, 20, 255))
	side = render(tex, {}, math.radians(90), 0.0, size=(cw, ch))
	img.save(out)
	return side


def main():
	assets = sys.argv[1].rstrip("/")
	coat = paint()
	coat.save(assets + "/textures/entity/wild/grizzly.png")
	egg().save(assets + "/textures/item/grizzly_spawn_egg.png")
	if "--preview" in sys.argv:
		sheet(coat, sys.argv[sys.argv.index("--preview") + 1])


if __name__ == "__main__":
	main()
