"""
Bakes a Blockbench .bbmodel (any format: free, modded entity, Bedrock, GeckoLib, Java block) into what the mods'
in-game loader (client/bb/BbModel in Powers and Kingdoms) reads: a JSON of bones and ready-made textured quads, plus
the textures as PNGs. Per-face UVs, box UVs, rotated cubes and inflate all come through as the model was drawn,
because every face is baked as its own quad (vanilla's ModelPart only knows box UVs).

Space: Blockbench works y-up with the model's front at -z; the game's models are y-down with the feet at y=24. A point
(x, y, z) in Blockbench is (-x, 24 - y, z) in the model (a half turn about z, so nothing is mirrored), and a rotation
(rx, ry, rz) becomes (-rx, -ry, rz). Animation keyframes are stored the way Blockbench shows them, which is already
the game's way for rotations; positions flip y.

Run from the project root:
  python -I tools/bbmodel_bake.py <model.bbmodel> <assets dir> <name> [--keep a,b] [--drop a,b] [--anims a,b]
writes <assets dir>/bbmodels/<name>.json and <assets dir>/textures/entity/bb/<name>[_<n>].png
--keep: only these bones (and their children); --drop: leave these bones (and their children) out.
--origin Bone=x:y:z,Other=x:y:z: move a bone's pivot (Blockbench units) without moving its cubes, e.g. a barrel onto
its trunnions so the game can swing it there.
"""
import base64
import io
import json
import math
import os
import sys

from PIL import Image


def rot_matrix(r):
	"""Blockbench's (and the game's) Euler order: R = Rz * Ry * Rx, degrees."""
	rx, ry, rz = (math.radians(v) for v in r)
	cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
	X = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
	Y = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
	Z = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
	return mm(Z, mm(Y, X))


def mm(a, b):
	return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def mv(a, v):
	return [sum(a[i][k] * v[k] for k in range(3)) for i in range(3)]


def faces_of(e):
	"""Each face's corners in Blockbench space, in the order its UV rectangle runs: (u0,v0) (u1,v0) (u1,v1) (u0,v1)."""
	f, t = e["from"], e["to"]
	g = e.get("inflate", 0) or 0
	x0, y0, z0 = f[0] - g, f[1] - g, f[2] - g
	x1, y1, z1 = t[0] + g, t[1] + g, t[2] + g
	return {"north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
			"south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
			"east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
			"west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
			"up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
			"down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)]}


def to_model(p):
	return [-p[0], 24.0 - p[1], p[2]]


def sub(a, b):
	return [a[i] - b[i] for i in range(3)]


def cross(a, b):
	return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]


def norm(v):
	n = math.sqrt(sum(c * c for c in v)) or 1.0
	return [c / n for c in v]


def r4(v):
	return round(v, 4)


def load_textures(data):
	"""Every texture as an RGBA image plus the UV size its faces are measured in."""
	out = []
	for t in data["textures"]:
		src = t.get("source", "")
		img = Image.open(io.BytesIO(base64.b64decode(src.split(",", 1)[1]))).convert("RGBA")
		uw = t.get("uv_width") or data.get("resolution", {}).get("width") or img.width
		uh = t.get("uv_height") or data.get("resolution", {}).get("height") or img.height
		out.append((t, img, uw, uh))
	return out


def bake(data, keep=None, drop=None, anim_names=None):
	textures = load_textures(data)
	tex_index = {t.get("uuid"): i for i, (t, _, _, _) in enumerate(textures)}
	used = set()
	for e in data["elements"]:
		for fc in e.get("faces", {}).values():
			if isinstance(fc, dict) and fc.get("texture") is not None:
				ti = fc["texture"]
				used.add(tex_index.get(ti, ti) if not isinstance(ti, int) else ti)
	used = sorted(used)
	# the textures faces use go into one atlas, stacked; the rest (eyes, other skins) come out as their own files
	aw = max(textures[i][1].width for i in used) if used else 16
	ah = sum(textures[i][1].height for i in used) if used else 16
	atlas = Image.new("RGBA", (aw, ah))
	place = {}
	y = 0
	for i in used:
		img = textures[i][1]
		atlas.paste(img, (0, y))
		place[i] = (y, img.width, img.height)
		y += img.height
	extras = [textures[i][1] for i in range(len(textures)) if i not in used]

	elements = {e["uuid"]: e for e in data["elements"]}
	groups = {g["uuid"]: g for g in data.get("groups", []) or []}
	bones = []

	def group_of(node):
		g = dict(groups.get(node.get("uuid"), {}))
		g.update({k: v for k, v in node.items() if k != "children"})
		return g

	def add_bone(name, parent, origin, rotation, parent_origin):
		if parent < 0:
			pivot = to_model(origin)
		else:
			d = sub(origin, parent_origin)
			pivot = [-d[0], -d[1], d[2]]
		bones.append({"name": name, "parent": parent, "pivot": [r4(v) for v in pivot],
					  "rot": [r4(math.radians(-rotation[0])), r4(math.radians(-rotation[1])), r4(math.radians(rotation[2]))],
					  "quads": []})
		return len(bones) - 1

	def add_cube(bone, origin, e):
		if e.get("type", "cube") != "cube" or e.get("export") is False:
			return
		eo = e.get("origin", [0, 0, 0])
		R = rot_matrix(e.get("rotation", [0, 0, 0]))
		for fn, quad in faces_of(e).items():
			fc = e.get("faces", {}).get(fn)
			if not fc or fc.get("texture") is None:
				continue
			ti = fc["texture"]
			ti = tex_index.get(ti, ti) if not isinstance(ti, int) else ti
			if ti not in place:
				continue
			_, img, uw, uh = textures[ti]
			yoff, tw, th = place[ti]
			u0, v0, u1, v1 = fc["uv"]
			corners = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
			k = (int(fc.get("rotation", 0) or 0) // 90) % 4
			corners = corners[k:] + corners[:k]
			pts = []
			for p in quad:
				q = mv(R, sub(p, eo))
				q = [q[i] + eo[i] for i in range(3)]
				d = sub(q, origin)
				pts.append([-d[0], -d[1], d[2]])
			if abs(u1 - u0) < 1e-6 and abs(v1 - v0) < 1e-6:
				continue
			uvs = [((u / uw) * tw / aw, (yoff + (v / uh) * th) / ah) for (u, v) in corners]
			n = norm(cross(sub(pts[1], pts[0]), sub(pts[3], pts[0])))
			# the game wants (v1 - v0) x (v2 - v0) pointing out of the cube; turn the quad round if it doesn't
			centre = [sum(p[i] for p in pts) / 4 for i in range(3)]
			box_c = [((e["from"][i] + e["to"][i]) / 2) for i in range(3)]
			bc = mv(R, sub(box_c, eo))
			bc = [bc[i] + eo[i] for i in range(3)]
			bd = sub(bc, origin)
			bc = [-bd[0], -bd[1], bd[2]]
			out = sub(centre, bc)
			if sum(n[i] * out[i] for i in range(3)) < 0:
				pts.reverse()
				uvs.reverse()
				n = [-c for c in n]
			flat = []
			for p, (u, v) in zip(pts, uvs):
				flat += [r4(p[0]), r4(p[1]), r4(p[2]), round(u, 6), round(v, 6)]
			flat += [r4(n[0]), r4(n[1]), r4(n[2])]
			bones[bone]["quads"].append(flat)

	def walk(node, parent, parent_origin, keeping):
		if isinstance(node, str):
			e = elements.get(node)
			if e is not None and keeping:
				if parent < 0:
					parent = ensure_root()
					parent_origin = [0, 0, 0]
				add_cube(parent, parent_origin, e)
			return
		g = group_of(node)
		name = g.get("name", "bone")
		if drop and name in drop:
			return
		keeping = keeping or (keep is None) or (name in keep)
		if g.get("export") is False:
			return
		origin = ORIGINS.get(name, g.get("origin", [0, 0, 0]))
		me = add_bone(name, parent, origin, g.get("rotation", [0, 0, 0]), parent_origin)
		for c in node.get("children", []):
			walk(c, me, origin, keeping)

	root_holder = []

	def ensure_root():
		if not root_holder:
			root_holder.append(add_bone("bb_root", -1, [0, 0, 0], [0, 0, 0], [0, 0, 0]))
		return root_holder[0]

	for n in data["outliner"]:
		walk(n, -1, [0, 0, 0], keep is None)

	anims = {}
	names = {b["name"] for b in bones}
	for a in data.get("animations", []):
		nm = a["name"].split(".")[-1]
		if anim_names and nm not in anim_names:
			continue
		tracks = []
		for an in a.get("animators", {}).values():
			if an.get("type", "bone") != "bone" or an.get("name") not in names:
				continue
			by = {}
			for k in an.get("keyframes", []):
				by.setdefault(k["channel"], []).append(k)
			for ch, kfs in by.items():
				if ch not in ("rotation", "position", "scale"):
					continue
				kfs.sort(key=lambda k: k["time"])
				keys = []
				for k in kfs:
					dp = k["data_points"][-1] if len(k["data_points"]) > 1 else k["data_points"][0]
					vals = []
					for ax in "xyz":
						v = dp.get(ax, 0)
						if isinstance(v, str):
							s = v.strip()
							try:
								v = float(s) if s else 0.0
							except ValueError:
								v = s
						vals.append(v)
					if ch == "position":
						y = vals[1]
						vals[1] = -y if not isinstance(y, str) else "-(" + y + ")"
					interp = {"catmullrom": 1, "step": 2}.get(k.get("interpolation"), 0)
					keys.append([r4(k["time"])] + [r4(v) if not isinstance(v, str) else v for v in vals] + [interp])
				tracks.append({"bone": an["name"], "ch": {"rotation": "rot", "position": "pos", "scale": "scale"}[ch], "keys": keys})
		anims[nm] = {"len": r4(a.get("length", 0)), "loop": a.get("loop", "once"), "tracks": tracks}
	return {"tex": [aw, ah], "bones": bones, "anims": anims}, atlas, extras


ORIGINS = {}


def main():
	src, assets, name = sys.argv[1], sys.argv[2], sys.argv[3]
	opts = {}
	for i in range(4, len(sys.argv) - 1, 2):
		opts[sys.argv[i].lstrip("-")] = set(sys.argv[i + 1].split(","))
	for o in opts.pop("origin", ()):
		bone, at = o.split("=")
		ORIGINS[bone] = [float(v) for v in at.split(":")]
	data = json.load(open(src, encoding="utf-8"))
	model, atlas, extras = bake(data, opts.get("keep"), opts.get("drop"), opts.get("anims"))
	os.makedirs(f"{assets}/bbmodels", exist_ok=True)
	os.makedirs(f"{assets}/textures/entity/bb", exist_ok=True)
	with open(f"{assets}/bbmodels/{name}.json", "w", encoding="utf-8", newline="\n") as f:
		json.dump(model, f, separators=(",", ":"))
	atlas.save(f"{assets}/textures/entity/bb/{name}.png")
	for i, img in enumerate(extras):
		img.save(f"{assets}/textures/entity/bb/{name}_{i + 1}.png")
	print(name, len(model["bones"]), "bones", sum(len(b["quads"]) for b in model["bones"]), "quads", model["tex"], "anims", list(model["anims"]), "extra textures", len(extras))


if __name__ == "__main__":
	main()
