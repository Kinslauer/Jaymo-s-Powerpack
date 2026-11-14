"""Draws a Blockbench project (one of ours: cubes with per-face UVs on one texture) with its texture on, from a little
to one side, texel by texel: a picture to check a model or show it without opening Blockbench or the game.

    python3 -I kingdoms/tools/bbmodel_render.py <model.bbmodel> <out.png> [--hide bone,bone] [--anim name] [--t seconds]
        [--yaw degrees] [--scale px per unit] [--tex other.png]

--hide leaves bones (and what hangs off them) out, as a renderer that shows only some of a model's parts does.
--anim/--t pose it at a moment of one of its animations. From Python: render(path, hide=..., anim=..., t=...) gives the
image, lineup([...]) sets several side by side.
"""
import base64
import io
import json
import math
import sys

from PIL import Image, ImageDraw


def rot(rx, ry, rz):
	a, b, c = (math.radians(v) for v in (rx, ry, rz))
	ca, sa, cb, sb, cc, sc = math.cos(a), math.sin(a), math.cos(b), math.sin(b), math.cos(c), math.sin(c)
	X = [[1, 0, 0], [0, ca, -sa], [0, sa, ca]]
	Y = [[cb, 0, sb], [0, 1, 0], [-sb, 0, cb]]
	Z = [[cc, -sc, 0], [sc, cc, 0], [0, 0, 1]]
	return mm(mm(Z, Y), X)


def mm(A, B):
	return [[sum(A[i][k] * B[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def mv(A, v):
	return [sum(A[i][k] * v[k] for k in range(3)) for i in range(3)]


def sample(kfs, t):
	if not kfs:
		return None
	kfs = sorted(kfs, key=lambda k: k[0])
	if t <= kfs[0][0]:
		return kfs[0][1]
	for (t0, v0), (t1, v1) in zip(kfs, kfs[1:]):
		if t <= t1:
			f = (t - t0) / (t1 - t0) if t1 > t0 else 0
			return [a + (b - a) * f for a, b in zip(v0, v1)]
	return kfs[-1][1]


# each face's corners in the order its UV runs (top left, top right, bottom right, bottom left, seen from outside):
# which of from (0) / to (1) each of x, y, z takes
FACES = {
	"north": ((1, 1, 0), (0, 1, 0), (0, 0, 0), (1, 0, 0)), "south": ((0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1)),
	"east": ((1, 1, 1), (1, 1, 0), (1, 0, 0), (1, 0, 1)), "west": ((0, 1, 0), (0, 1, 1), (0, 0, 1), (0, 0, 0)),
	"up": ((0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)), "down": ((0, 0, 1), (1, 0, 1), (1, 0, 0), (0, 0, 0)),
}
LIGHT = {"up": 1.0, "down": 0.55, "north": 0.9, "south": 0.7, "east": 0.78, "west": 0.78}


def render(path, hide=(), anim=None, t=0.0, yaw=35.0, scale=8.0, tex=None, background=(0, 0, 0, 0)):
	d = json.load(open(path))
	cubes = {e["uuid"]: e for e in d["elements"]}
	if tex is None:
		tex = Image.open(io.BytesIO(base64.b64decode(d["textures"][0]["source"].split(",", 1)[1])))
	else:
		tex = Image.open(tex)
	tex = tex.convert("RGBA")
	px = tex.load()
	bones = {}

	def walk(node, parent, hidden):
		hidden = hidden or node["name"] in hide
		bones[node["uuid"]] = (node, parent, hidden)
		for ch in node["children"]:
			if isinstance(ch, dict):
				walk(ch, node["uuid"], hidden)

	for n in d["outliner"]:
		walk(n, None, False)
	tracks = {}
	if anim:
		a = next(x for x in d["animations"] if x["name"].split(".")[-1] == anim)
		old = [int(x) for x in str(d["meta"].get("format_version", "5.0")).split(".")[:2]] < [5, 0]
		for bu, an in a["animators"].items():
			for k in an["keyframes"]:
				p = k["data_points"][0]
				v = [float(p["x"]), float(p["y"]), float(p["z"])]
				if old and k["channel"] in ("rotation", "position"):
					v[0] = -v[0]
					if k["channel"] == "rotation":
						v[1] = -v[1]
				tracks.setdefault((bu, k["channel"]), []).append((float(k["time"]), v))

	def world(bu):
		node, parent, _ = bones[bu]
		r = sample(tracks.get((bu, "rotation")), t) or [0, 0, 0]
		p = sample(tracks.get((bu, "position")), t) or [0, 0, 0]
		rest = node.get("rotation") or [0, 0, 0]
		R = mm(rot(*rest), rot(*r))
		piv = node["origin"]
		up = world(parent) if parent else None

		def f(v):
			q = mv(R, [v[i] - piv[i] for i in range(3)])
			q = [q[i] + piv[i] + p[i] for i in range(3)]
			return up(q) if up else q
		return f

	view = mm(rot(-12, 0, 0), rot(0, yaw, 0))
	quads = []
	for bu, (node, _, hidden) in bones.items():
		if hidden:
			continue
		W = world(bu)
		for ch in node["children"]:
			if isinstance(ch, dict):
				continue
			e = cubes[ch]
			g = e.get("inflate", 0)
			lo = [e["from"][i] - g for i in range(3)]
			hi = [e["to"][i] + g for i in range(3)]
			er = e.get("rotation") or [0, 0, 0]
			ER = rot(*er) if any(er) else None
			eo = e.get("origin") or [0, 0, 0]

			def place(c):
				if ER:
					c = mv(ER, [c[i] - eo[i] for i in range(3)])
					c = [c[i] + eo[i] for i in range(3)]
				return mv(view, W(c))

			for name, corners in FACES.items():
				face = e["faces"].get(name)
				if not face or face.get("texture") is None:
					continue
				u0, v0, u1, v1 = face["uv"]
				c = [place([(lo, hi)[pick[i]][i] for i in range(3)]) for pick in corners]
				nu, nv = max(1, round(abs(u1 - u0))), max(1, round(abs(v1 - v0)))
				for iu in range(nu):
					for iv in range(nv):
						tx = int(min(u0, u1) + (iu if u1 >= u0 else nu - 1 - iu))
						ty = int(min(v0, v1) + (iv if v1 >= v0 else nv - 1 - iv))
						if not (0 <= tx < tex.width and 0 <= ty < tex.height):
							continue
						r_, g_, b_, a_ = px[tx, ty]
						if a_ < 8:
							continue

						def at(fu, fv):
							top = [c[0][i] + (c[1][i] - c[0][i]) * fu for i in range(3)]
							bot = [c[3][i] + (c[2][i] - c[3][i]) * fu for i in range(3)]
							return [top[i] + (bot[i] - top[i]) * fv for i in range(3)]

						q = [at(iu / nu, iv / nv), at((iu + 1) / nu, iv / nv), at((iu + 1) / nu, (iv + 1) / nv), at(iu / nu, (iv + 1) / nv)]
						k = LIGHT[name]
						quads.append((sum(p_[2] for p_ in q) / 4, q, (int(r_ * k), int(g_ * k), int(b_ * k), 255)))
	if not quads:
		return Image.new("RGBA", (8, 8), background)
	xs = [-p_[0] * scale for _, q, _ in quads for p_ in q]
	ys = [-p_[1] * scale for _, q, _ in quads for p_ in q]
	pad = 6
	w, h = int(max(xs) - min(xs)) + 2 * pad, int(max(ys) - min(ys)) + 2 * pad
	img = Image.new("RGBA", (w, h), background)
	draw = ImageDraw.Draw(img)
	ox, oy = pad - min(xs), pad - min(ys)
	# the camera is out front (at -z) looking back: the far texels first
	for _, q, col in sorted(quads, key=lambda x: -x[0]):
		draw.polygon([(ox - p_[0] * scale, oy - p_[1] * scale) for p_ in q], fill=col)
	return img


def lineup(images, gap=16, background=(34, 36, 42, 255)):
	"""Several renders side by side, standing on one line."""
	h = max(i.height for i in images)
	out = Image.new("RGBA", (sum(i.width for i in images) + gap * (len(images) + 1), h + 2 * gap), background)
	x = gap
	for i in images:
		out.alpha_composite(i, (x, gap + h - i.height))
		x += i.width + gap
	return out


def main(argv):
	opts = {"hide": "", "anim": None, "t": "0", "yaw": "35", "scale": "8", "tex": None}
	rest = []
	it = iter(argv)
	for a in it:
		if a.startswith("--"):
			opts[a[2:]] = next(it)
		else:
			rest.append(a)
	img = render(rest[0], hide=tuple(x for x in opts["hide"].split(",") if x), anim=opts["anim"], t=float(opts["t"]),
		yaw=float(opts["yaw"]), scale=float(opts["scale"]), tex=opts["tex"])
	lineup([img]).save(rest[1])


if __name__ == "__main__":
	main(sys.argv[1:])
