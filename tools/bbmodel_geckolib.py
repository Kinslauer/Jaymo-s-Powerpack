"""
Turns a Blockbench .bbmodel (any format) into the three files GeckoLib 5 reads, the way Blockbench's own GeckoLib
export writes them:
  <assets>/geckolib/models/entity/<name>.geo.json        bones and cubes (Bedrock geometry 1.12.0)
  <assets>/geckolib/animations/entity/<name>.animation.json
  <assets>/textures/entity/<name>.png                    every texture the faces use, stacked on one sheet

GeckoLib draws a model with one texture, so a model painted on several (a body, a sword, a buckler) has them stacked
top to bottom and every face given its own UV on the sheet (per-face UVs, which also keep different resolutions
apart). Box-UV cubes are written per face as well; their faces already hold the box layout (mirroring included).

Space and signs are Blockbench's Bedrock export's: a bone's pivot and a cube's origin flip x (a cube's origin is its
corner at -to.x), bone and cube rotations flip x and y, and the up and down faces' UVs are written from the far corner.
Animation keyframes are written as Blockbench stores them (GeckoLib reads them that way).

Run from the project root (needs Pillow):
  py -3 -I tools/bbmodel_geckolib.py <model.bbmodel> <assets dir> <name> [--drop a,b] [--anims a,b] [--rename old=new,...]
--drop: leave these bones (and their children) out. --anims: only these animations (their names after the last dot).
--rename: give animations new names (after --anims picks them).
"""
import base64
import io
import json
import os
import sys

from PIL import Image

POSE_LENGTH = 0.05


def r4(v):
	return round(v, 4) if isinstance(v, float) else v


def load_textures(data):
	out = []
	for t in data["textures"]:
		img = Image.open(io.BytesIO(base64.b64decode(t["source"].split(",", 1)[1]))).convert("RGBA")
		uw = t.get("uv_width") or data.get("resolution", {}).get("width") or img.width
		uh = t.get("uv_height") or data.get("resolution", {}).get("height") or img.height
		frame = round(img.width * uh / uw)
		if 0 < frame < img.height and img.height % frame == 0:
			img = img.crop((0, 0, img.width, frame))
		out.append((t, img, uw, uh))
	return out


def number(v):
	if isinstance(v, (int, float)):
		return v
	s = str(v).strip()
	if not s:
		return 0
	try:
		return float(s)
	except ValueError:
		return s


def convert(data, name, drop=(), anim_names=None, rename=None):
	textures = load_textures(data)
	tex_index = {t.get("uuid"): i for i, (t, _, _, _) in enumerate(textures)}

	def tex_of(fc):
		ti = fc.get("texture")
		if ti is None or ti is False:
			return None
		return ti if isinstance(ti, int) else tex_index.get(ti)

	used = sorted({tex_of(fc) for e in data["elements"] for fc in e.get("faces", {}).values()
				   if isinstance(fc, dict) and tex_of(fc) is not None})
	aw = max(textures[i][1].width for i in used)
	ah = sum(textures[i][1].height for i in used)
	sheet = Image.new("RGBA", (aw, ah))
	place = {}
	y = 0
	for i in used:
		img = textures[i][1]
		sheet.paste(img, (0, y))
		place[i] = (y, img.width / textures[i][2], img.height / textures[i][3])
		y += img.height

	elements = {e["uuid"]: e for e in data["elements"]}
	groups = {g["uuid"]: g for g in data.get("groups", []) or []}
	bones = []

	def cube(e):
		f, t = e["from"], e["to"]
		size = [t[i] - f[i] for i in range(3)]
		c = {"origin": [r4(-(f[0] + size[0])), r4(f[1]), r4(f[2])], "size": [r4(s) for s in size]}
		if e.get("inflate"):
			c["inflate"] = r4(e["inflate"])
		rot = e.get("rotation", [0, 0, 0])
		if any(rot):
			o = e.get("origin", [0, 0, 0])
			c["pivot"] = [r4(-o[0]), r4(o[1]), r4(o[2])]
			c["rotation"] = [r4(-rot[0]), r4(-rot[1]), r4(rot[2])]
		uv = {}
		for fn, fc in e.get("faces", {}).items():
			ti = tex_of(fc) if isinstance(fc, dict) else None
			if ti is None or ti not in place:
				continue
			yoff, sx, sy = place[ti]
			u0, v0, u1, v1 = fc["uv"]
			u0, u1, v0, v1 = u0 * sx, u1 * sx, v0 * sy + yoff, v1 * sy + yoff
			face = {"uv": [u0, v0], "uv_size": [u1 - u0, v1 - v0]}
			if fn in ("up", "down"):
				face = {"uv": [u1, v1], "uv_size": [u0 - u1, v0 - v1]}
			face = {k: [r4(float(x)) for x in v] for k, v in face.items()}
			if fc.get("rotation"):
				face["uv_rotation"] = int(fc["rotation"])
			uv[fn] = face
		if not uv:
			return None
		c["uv"] = uv
		return c

	def group_of(node):
		g = dict(groups.get(node.get("uuid"), {}))
		g.update({k: v for k, v in node.items() if k != "children"})
		return g

	root = []

	def root_bone():
		if not root:
			bones.append({"name": "bb_root", "pivot": [0, 0, 0], "cubes": []})
			root.append(bones[-1])
		return root[0]

	def walk(node, parent):
		if isinstance(node, str):
			e = elements.get(node)
			if e is None or e.get("type", "cube") != "cube" or e.get("export") is False:
				return
			c = cube(e)
			if c:
				(parent or root_bone()).setdefault("cubes", []).append(c)
			return
		g = group_of(node)
		if g.get("name") in drop or g.get("export") is False:
			return
		o = g.get("origin", [0, 0, 0])
		b = {"name": g["name"]}
		if parent:
			b["parent"] = parent["name"]
		b["pivot"] = [r4(-o[0]), r4(o[1]), r4(o[2])]
		rot = g.get("rotation", [0, 0, 0])
		if any(rot):
			b["rotation"] = [r4(-rot[0]), r4(-rot[1]), r4(rot[2])]
		if g.get("mirror_uv"):
			b["mirror"] = True
		bones.append(b)
		for ch in node.get("children", []):
			walk(ch, b)

	for n in data["outliner"]:
		walk(n, None)
	names = [b["name"] for b in bones]
	if len(names) != len(set(names)):
		dup = sorted({n for n in names if names.count(n) > 1})
		raise SystemExit("bone names must be unique for GeckoLib: " + ", ".join(dup))

	lo = [min(c["origin"][i] for b in bones for c in b.get("cubes", [])) for i in range(3)]
	hi = [max(c["origin"][i] + c["size"][i] for b in bones for c in b.get("cubes", [])) for i in range(3)]
	geo = {"format_version": "1.12.0", "minecraft:geometry": [{
		"description": {"identifier": "geometry." + name, "texture_width": aw, "texture_height": ah,
						"visible_bounds_width": round(max(hi[0] - lo[0], hi[2] - lo[2]) / 16 + 1, 1),
						"visible_bounds_height": round(hi[1] / 16 + 1, 1), "visible_bounds_offset": [0, round(hi[1] / 32, 2), 0]},
		"bones": bones}]}

	anims = {}
	bone_names = set(names)
	for a in data.get("animations", []):
		nm = a["name"].split(".")[-1]
		if anim_names and nm not in anim_names:
			continue
		nm = (rename or {}).get(nm, nm)
		out = {}
		if a.get("loop") == "loop":
			out["loop"] = True
		elif a.get("loop") == "hold":
			out["loop"] = "hold_on_last_frame"
		# a pose (keyframes at 0 only) is 0 s long in Blockbench; give it a frame so a looping one has something to loop
		out["animation_length"] = max(r4(float(a.get("length", 0))), POSE_LENGTH)
		bones_out = {}
		for an in (a.get("animators") or {}).values():
			if an.get("type", "bone") != "bone" or an.get("name") not in bone_names:
				continue
			channels = {}
			for k in sorted(an.get("keyframes", []), key=lambda k: k["time"]):
				ch = k["channel"]
				if ch not in ("rotation", "position", "scale"):
					continue
				pts = [[number(dp.get(ax, 0)) for ax in "xyz"] for dp in k["data_points"]]
				pts = [[r4(float(v)) if isinstance(v, (int, float)) else v for v in p] for p in pts]
				interp = k.get("interpolation", "linear")
				if len(pts) > 1:
					kf = {"pre": pts[0], "post": pts[1]}
				elif interp == "linear":
					kf = pts[0]
				else:
					kf = {"vector": pts[0]}
				if interp == "catmullrom":
					kf = kf if isinstance(kf, dict) else {"vector": kf}
					kf["lerp_mode"] = "catmullrom"
				elif interp == "step":
					kf = kf if isinstance(kf, dict) else {"vector": kf}
					kf["lerp_mode"] = "step"
				channels.setdefault(ch, {})[str(r4(float(k["time"])))] = kf
			if channels:
				bones_out[an["name"]] = channels
		out["bones"] = bones_out
		anims[nm] = out
	return geo, {"format_version": "1.8.0", "animations": anims, "geckolib_format_version": 2}, sheet


def main():
	args = sys.argv[1:]
	opts = {}
	while len(args) > 3 and args[-2].startswith("--"):
		opts[args[-2][2:]] = args[-1]
		args = args[:-2]
	src, assets, name = args
	drop = set(filter(None, opts.get("drop", "").split(",")))
	anims = set(filter(None, opts.get("anims", "").split(","))) or None
	rename = dict(p.split("=", 1) for p in opts.get("rename", "").split(",") if "=" in p)
	with open(src, encoding="utf-8") as f:
		data = json.load(f)
	geo, anim, sheet = convert(data, name, drop, anims, rename)
	paths = {
		"geo": os.path.join(assets, "geckolib", "models", "entity", name + ".geo.json"),
		"anim": os.path.join(assets, "geckolib", "animations", "entity", name + ".animation.json"),
		"tex": os.path.join(assets, "textures", "entity", name + ".png"),
	}
	for p in paths.values():
		os.makedirs(os.path.dirname(p), exist_ok=True)
	with open(paths["geo"], "w", encoding="utf-8", newline="\n") as f:
		json.dump(geo, f, separators=(",", ":"))
	with open(paths["anim"], "w", encoding="utf-8", newline="\n") as f:
		json.dump(anim, f, indent="\t")
	sheet.save(paths["tex"])
	b = geo["minecraft:geometry"][0]
	print(name, len(b["bones"]), "bones,", sum(len(x.get("cubes", [])) for x in b["bones"]), "cubes, texture",
		  b["description"]["texture_width"], "x", b["description"]["texture_height"], ", animations:", ", ".join(anim["animations"]))


if __name__ == "__main__":
	main()
