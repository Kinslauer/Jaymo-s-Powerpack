"""
Draws a model baked by bbmodel_bake.py the way the game will: ModelPart pivots and rotations, the entity's flip into
the world, back faces culled (so a face wound the wrong way shows up as a hole). For checking a bake before it goes
in game.

python -I tools/bbmodel_preview.py <assets dir> <name> <out.png> [animation] [time] [texture png]
"""
import json
import math
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, __file__.rsplit("/", 1)[0])


def rz_ry_rx(x, y, z):
	cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
	X = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
	Y = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
	Z = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
	return mm(Z, mm(Y, X))


def mm(a, b):
	return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def mv(a, v):
	return [sum(a[i][k] * v[k] for k in range(3)) for i in range(3)]


def molang(expr, t):
	e = expr.replace("query.anim_time", "(%r)" % t).replace("q.anim_time", "(%r)" % t)
	env = {"math": type("M", (), {"sin": staticmethod(lambda d: math.sin(math.radians(d))),
								  "cos": staticmethod(lambda d: math.cos(math.radians(d))),
								  "abs": staticmethod(abs), "max": staticmethod(max), "min": staticmethod(min),
								  "sqrt": staticmethod(math.sqrt), "pi": math.pi,
								  "clamp": staticmethod(lambda v, a, b: max(a, min(b, v)))})}
	return float(eval(e, {"__builtins__": {}}, env))


def sample(keys, t):
	def val(k):
		return [molang(v, t) if isinstance(v, str) else v for v in k[1:4]]
	if t <= keys[0][0]:
		return val(keys[0])
	for i in range(len(keys) - 1):
		a, b = keys[i], keys[i + 1]
		if a[0] <= t <= b[0]:
			if b[0] - a[0] < 1e-9 or b[4] == 2:
				return val(a)
			f = (t - a[0]) / (b[0] - a[0])
			va, vb = val(a), val(b)
			return [va[j] + (vb[j] - va[j]) * f for j in range(3)]
	return val(keys[-1])


def main():
	assets, name, out = sys.argv[1], sys.argv[2], sys.argv[3]
	anim = sys.argv[4] if len(sys.argv) > 4 else None
	at = float(sys.argv[5]) if len(sys.argv) > 5 else 0.0
	model = json.load(open(f"{assets}/bbmodels/{name}.json"))
	tex = Image.open(sys.argv[6] if len(sys.argv) > 6 else f"{assets}/textures/entity/bb/{name}.png").convert("RGBA")
	T = tex.load()
	bones = model["bones"]
	pose = {i: (list(b["pivot"]), list(b["rot"]), [1, 1, 1]) for i, b in enumerate(bones)}
	if anim:
		a = model["anims"][anim]
		idx = {b["name"]: i for i, b in enumerate(bones)}
		for tr in a["tracks"]:
			i = idx[tr["bone"]]
			v = sample(tr["keys"], at)
			p, r, s = pose[i]
			if tr["ch"] == "rot":
				r[0] += math.radians(v[0]); r[1] += math.radians(v[1]); r[2] += math.radians(v[2])
			elif tr["ch"] == "pos":
				p[0] += v[0]; p[1] += v[1]; p[2] += v[2]
			else:
				s[0] *= v[0]; s[1] *= v[1]; s[2] *= v[2]
	# world matrix of each bone: parent * translate(pivot) * rotate * scale
	world = {}

	def wm(i):
		if i in world:
			return world[i]
		p, r, s = pose[i]
		R = rz_ry_rx(*r)
		R = [[R[a][b] * s[b] for b in range(3)] for a in range(3)]
		par = bones[i]["parent"]
		if par < 0:
			M, o = [[1, 0, 0], [0, 1, 0], [0, 0, 1]], [0, 0, 0]
		else:
			M, o = wm(par)
		o2 = [o[k] + mv(M, p)[k] for k in range(3)]
		world[i] = (mm(M, R), o2)
		return world[i]

	quads = []
	for i, b in enumerate(bones):
		M, o = wm(i)
		for q in b["quads"]:
			pts = []
			for k in range(4):
				x, y, z, u, v = q[k * 5:k * 5 + 5]
				w = mv(M, [x, y, z])
				w = [w[j] + o[j] for j in range(3)]
				# into the world: scale(-1,-1,1), feet at y=24, facing +z (yaw 180 from the model's -z)
				wx, wy, wz = -w[0] / 16, (24 - w[1]) / 16, w[2] / 16
				pts.append(((-wx, wy, -wz), (u, v)))
			quads.append(pts)
	allp = [p for q in quads for p, _ in q]
	lo = [min(p[k] for p in allp) for k in range(3)]
	hi = [max(p[k] for p in allp) for k in range(3)]
	c = [(lo[k] + hi[k]) / 2 for k in range(3)]
	ext = max(hi[k] - lo[k] for k in range(3)) or 1
	size, yaws = 360, (30, 90, 150, 0, 210)
	sheet = Image.new("RGB", (size * len(yaws), size), (150, 170, 190))
	d = ImageDraw.Draw(sheet)
	px = size * 0.78 / ext
	for vi, yaw in enumerate(yaws):
		ya, pi = math.radians(yaw), math.radians(-15)

		def proj(p):
			x, y, z = p[0] - c[0], p[1] - c[1], p[2] - c[2]
			x, z = x * math.cos(ya) - z * math.sin(ya), x * math.sin(ya) + z * math.cos(ya)
			y, z = y * math.cos(pi) - z * math.sin(pi), y * math.sin(pi) + z * math.cos(pi)
			# camera looks along +z from -z: screen right is -x
			return (size * vi + size / 2 - x * px, size / 2 - y * px, z)
		polys = []
		for q in quads:
			P = [proj(p) for p, _ in q]
			# cull: vanilla's winding, (v1-v0)x(v2-v0) facing the camera (towards -z) is drawn
			ax, ay = P[1][0] - P[0][0], P[1][1] - P[0][1]
			bx, by = P[2][0] - P[0][0], P[2][1] - P[0][1]
			if ax * by - ay * bx > 0:
				continue
			uv = [t for _, t in q]
			n = 6
			for a_ in range(n):
				for b_ in range(n):
					s0, s1, t0, t1 = a_ / n, (a_ + 1) / n, b_ / n, (b_ + 1) / n

					def at(s, t, A):
						top = [A[0][j] + (A[1][j] - A[0][j]) * s for j in range(len(A[0]))]
						bot = [A[3][j] + (A[2][j] - A[3][j]) * s for j in range(len(A[0]))]
						return [top[j] + (bot[j] - top[j]) * t for j in range(len(A[0]))]
					u, v = at((s0 + s1) / 2, (t0 + t1) / 2, uv)
					col = T[min(tex.width - 1, max(0, int(u * tex.width))), min(tex.height - 1, max(0, int(v * tex.height)))]
					if col[3] < 30:
						continue
					corners = [at(s0, t0, P), at(s1, t0, P), at(s1, t1, P), at(s0, t1, P)]
					z = sum(p[2] for p in corners) / 4
					polys.append((z, [(p[0], p[1]) for p in corners], col[:3]))
		polys.sort(key=lambda p: -p[0])
		for z, q, col in polys:
			d.polygon(q, fill=col)
	sheet.save(out)


if __name__ == "__main__":
	main()
