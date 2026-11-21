"""
Turns a sprite stack (a strip of top-down pixel-art slices, ground slice on the left) into a cube model that fits the
pack's 16px art: every opaque pixel is one voxel, voxels are greedy-merged into as few cubes as possible, and each
cube face gets its own patch of one texture atlas holding the colours of the voxels on that face.

python3 -I tools/sprite_stack/stack2model.py <stack.png> <out dir> <namespace:block/name> [--slices N] [--pitch P]

Slice orientation: the top of each slice is north (-z), its left is west (-x), as seen from above.
--model-dir / --texture-dir send the .json / .png straight into a mod's assets.
Writes into <out dir>: <name>.json (vanilla block model, faces use #variables as 26.3 needs), <name>.png (texture),
<name>.bbmodel (to hand-tidy in Blockbench) and <name>_preview.png (four turns, drawn by mesh_to_cubes/bbrender.py).
"""
import argparse
import base64
import io
import json
import os
import sys
import uuid

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "mesh_to_cubes"))
import bbrender  # noqa: E402

ALPHA = 128  # a pixel at or above this alpha is solid


def load_stack(path, slices=None):
	"""(occupied[x, y, z], colour[x, y, z, 4]) from a horizontal strip, slice 0 = ground."""
	img = np.asarray(Image.open(path).convert("RGBA"))
	h, w = img.shape[:2]
	if slices is None:
		if w % h:
			sys.exit("can't tell the slice count: pass --slices (strip is %dx%d)" % (w, h))
		slices = w // h  # square slices by default
	if w % slices:
		sys.exit("strip width %d doesn't divide into %d slices" % (w, slices))
	sw = w // slices
	col = np.zeros((sw, slices, h, 4), np.uint8)
	for y in range(slices):
		s = img[:, y * sw:(y + 1) * sw]  # rows = z (north first), columns = x (west first)
		col[:, y, :, :] = s.transpose(1, 0, 2)
	return col[..., 3] >= ALPHA, col


def greedy(occ):
	"""Boxes (x0, y0, z0, x1, y1, z1), end exclusive, covering every solid voxel exactly once. Tries the six axis
	orders and keeps the one with the fewest boxes."""
	best = None
	for order in ((0, 2, 1), (2, 0, 1), (0, 1, 2), (1, 0, 2), (1, 2, 0), (2, 1, 0)):
		left = occ.copy()
		boxes = []
		for idx in np.argwhere(left):
			if not left[tuple(idx)]:
				continue
			lo = list(idx)
			hi = [i + 1 for i in idx]
			for ax in order:
				while hi[ax] < occ.shape[ax]:
					sl = [slice(lo[k], hi[k]) for k in range(3)]
					sl[ax] = slice(hi[ax], hi[ax] + 1)
					if not left[tuple(sl)].all():
						break
					hi[ax] += 1
			left[lo[0]:hi[0], lo[1]:hi[1], lo[2]:hi[2]] = False
			boxes.append(tuple(int(v) for v in (*lo, *hi)))
		if best is None or len(boxes) < len(best):
			best = boxes
	return best


# For each face: the outward step, and how a texel (u, v) maps to the voxel behind it, matching vanilla's default UV
# directions (north: u runs east->west, v top->bottom; up: v runs north->south; down: v runs south->north).
def face_texels(face, b):
	x0, y0, z0, x1, y1, z1 = b
	if face == "north":
		return (0, 0, -1), x1 - x0, y1 - y0, lambda u, v: (x1 - 1 - u, y1 - 1 - v, z0)
	if face == "south":
		return (0, 0, 1), x1 - x0, y1 - y0, lambda u, v: (x0 + u, y1 - 1 - v, z1 - 1)
	if face == "west":
		return (-1, 0, 0), z1 - z0, y1 - y0, lambda u, v: (x0, y1 - 1 - v, z0 + u)
	if face == "east":
		return (1, 0, 0), z1 - z0, y1 - y0, lambda u, v: (x1 - 1, y1 - 1 - v, z1 - 1 - u)
	if face == "up":
		return (0, 1, 0), x1 - x0, z1 - z0, lambda u, v: (x0 + u, y1 - 1, z0 + v)
	return (0, -1, 0), x1 - x0, z1 - z0, lambda u, v: (x0 + u, y0, z1 - 1 - v)


FACES = ("north", "south", "east", "west", "up", "down")


def faces_of(occ, col, boxes):
	"""[(box index, face, w, h, texel rgba array h x w)] for every face with at least one texel open to the air."""
	sx, sy, sz = occ.shape

	def solid(x, y, z):
		return 0 <= x < sx and 0 <= y < sy and 0 <= z < sz and occ[x, y, z]

	out = []
	for bi, b in enumerate(boxes):
		for f in FACES:
			step, w, h, at = face_texels(f, b)
			tex = np.zeros((h, w, 4), np.uint8)
			seen = False
			for v in range(h):
				for u in range(w):
					x, y, z = at(u, v)
					tex[v, u] = col[x, y, z]
					tex[v, u, 3] = 255
					if not solid(x + step[0], y + step[1], z + step[2]):
						seen = True
			if seen:
				out.append((bi, f, w, h, tex))
	return out


def pack(rects):
	"""Shelf-pack (w, h) rects into the smallest square power-of-two atlas. Returns (size, [(x, y)])."""
	order = sorted(range(len(rects)), key=lambda i: (-rects[i][1], -rects[i][0]))
	size = 16
	while True:
		pos = [None] * len(rects)
		x = y = shelf = 0
		ok = True
		for i in order:
			w, h = rects[i]
			if x + w > size:
				x, y, shelf = 0, y + shelf, 0
			if y + h > size or w > size:
				ok = False
				break
			pos[i] = (x, y)
			x += w
			shelf = max(shelf, h)
		if ok:
			return size, pos
		size *= 2


def r(v):
	return round(v, 4)


def edge(face, fr, to):
	"""The block side a face lies flat on, or None."""
	on = {"down": fr[1] == 0, "up": to[1] == 16, "north": fr[2] == 0, "south": to[2] == 16,
		  "west": fr[0] == 0, "east": to[0] == 16}
	return face if on[face] else None


def centred(bb):
	"""A copy moved so the block's middle sits on the origin, where bbrender turns the model."""
	bb = json.loads(json.dumps(bb))
	for e in bb["elements"]:
		for key in ("from", "to", "origin"):
			e[key] = [e[key][0] - 8, e[key][1], e[key][2] - 8]
	return bb


def convert(stack, out_dir, model_id, slices=None, pitch=1.0, model_dir=None, texture_dir=None):
	"""model_dir / texture_dir put the .json / .png straight into a mod's assets (default: out_dir)."""
	occ, col = load_stack(stack, slices)
	if not occ.any():
		sys.exit("no solid pixels in " + stack)
	boxes = greedy(occ)
	faces = faces_of(occ, col, boxes)
	size, pos = pack([(w, h) for _, _, w, h, _ in faces])
	atlas = np.zeros((size, size, 4), np.uint8)
	for (bi, f, w, h, tex), (px, py) in zip(faces, pos):
		atlas[py:py + h, px:px + w] = tex
	name = model_id.split("/")[-1]
	model_dir, texture_dir = model_dir or out_dir, texture_dir or out_dir
	for d in (out_dir, model_dir, texture_dir):
		os.makedirs(d, exist_ok=True)
	png = os.path.join(texture_dir, name + ".png")
	Image.fromarray(atlas, "RGBA").save(png)

	# centre the footprint on the block, ground slice at y = 0
	sx, sy, sz = occ.shape
	ox, oz = 8 - sx * pitch / 2, 8 - sz * pitch / 2
	k = 16 / size  # atlas pixel -> vanilla uv unit
	elements, bb_elements = [], []
	by_box = {}
	for (bi, f, w, h, tex), (px, py) in zip(faces, pos):
		by_box.setdefault(bi, {})[f] = (px, py, w, h)
	for bi, b in enumerate(boxes):
		if bi not in by_box:
			continue  # buried inside the model
		x0, y0, z0, x1, y1, z1 = b
		fr = [r(ox + x0 * pitch), r(y0 * pitch), r(oz + z0 * pitch)]
		to = [r(ox + x1 * pitch), r(y1 * pitch), r(oz + z1 * pitch)]
		fs, bfs = {}, {}
		for f, (px, py, w, h) in by_box[bi].items():
			fs[f] = {"uv": [r(px * k), r(py * k), r((px + w) * k), r((py + h) * k)], "texture": "#0"}
			side = edge(f, fr, to)
			if side:
				fs[f]["cullface"] = side  # lies on the block's own side: hidden when a solid block is there
			bfs[f] = {"uv": [px, py, px + w, py + h], "texture": 0}
		for f in FACES:
			bfs.setdefault(f, {"uv": [0, 0, 0, 0], "texture": None})
		elements.append({"from": fr, "to": to, "faces": fs})
		bb_elements.append({"name": "cube_%d" % bi, "box_uv": False, "rescale": False, "from": fr, "to": to,
							"origin": [8, 0, 8], "faces": bfs, "type": "cube", "uuid": str(uuid.uuid4())})

	tex_id = model_id
	model = {"parent": "minecraft:block/block", "textures": {"0": tex_id, "particle": tex_id}, "elements": elements}
	with open(os.path.join(model_dir, name + ".json"), "w") as fh:
		json.dump(model, fh, indent="\t")

	buf = io.BytesIO()
	Image.fromarray(atlas, "RGBA").save(buf, "PNG")
	bb = {"meta": {"format_version": "4.10", "model_format": "free", "box_uv": False}, "name": name,
		  "resolution": {"width": size, "height": size}, "elements": bb_elements,
		  "outliner": [{"name": name, "origin": [8, 0, 8], "uuid": str(uuid.uuid4()),
						"children": [e["uuid"] for e in bb_elements]}],
		  "textures": [{"name": name + ".png", "id": "0", "width": size, "height": size, "uv_width": size,
						"uv_height": size, "uuid": str(uuid.uuid4()),
						"source": "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()}]}
	with open(os.path.join(out_dir, name + ".bbmodel"), "w") as fh:
		json.dump(bb, fh)
	bbrender.render(centred(bb), os.path.join(out_dir, name + "_preview.png"), centre_y=sy * pitch / 2)
	return {"voxels": int(occ.sum()), "cubes": len(elements), "faces": len(faces), "atlas": size,
			"size": [sx, sy, sz]}


def main():
	ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
	ap.add_argument("stack")
	ap.add_argument("out_dir")
	ap.add_argument("model_id", help="texture/model id, e.g. jaymoskingdoms:block/oil_pot")
	ap.add_argument("--slices", type=int, help="slice count (default: strip width / height, square slices)")
	ap.add_argument("--pitch", type=float, default=1.0, help="model units per voxel (1 = one pixel of a 16px block)")
	ap.add_argument("--model-dir", help="write the .json here instead (e.g. a mod's models/block)")
	ap.add_argument("--texture-dir", help="write the .png here instead (e.g. a mod's textures/block)")
	a = ap.parse_args()
	print(json.dumps(convert(a.stack, a.out_dir, a.model_id, a.slices, a.pitch, a.model_dir, a.texture_dir)))


if __name__ == "__main__":
	main()
