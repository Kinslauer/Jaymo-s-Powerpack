"""
The held musket (WeaponMod's, in our held_guns pack) as 9greenworks' "Flintlock Musket Rifle" (Blockbench Workshop,
CC BY 4.0; credits in the pack's assets/jaymospowers/textures/item/gun/CREDITS.txt). Its cubes are laid along the
diagonal the old voxelised sprite lay on (muzzle up and left, stock down and right, trigger guard below), at the
sprite's length, so the hand-held placements tuned for the sprite still fit. Its muzzle flash, cartridge and ball are
left out. The texture is padded onto a 256 square.

Run from the project root:  python -I tools/gen_flintlock_rifle.py
"""
import json
import math
import os

from PIL import Image
import base64
import io

SRC = "tools/bbmodels/flintlock-musket-rifle-commission.bbmodel"
PACK = "src/main/resources/resourcepacks/held_guns/assets/jaymospowers"
DROP = {"gun_flash2", "gunpowder_casing2", "bullet2"}
# the gun's length on the sprite's diagonal, as a share of the model's own (40.5 long): (0.18.70) a fifth longer than
# the sprite, Andy: "musket should be a bit larger". Its first-person display (level at the hip) and the sights it's
# raised to (MusketSights) were worked out for this size; change them with it.
SCALE = 0.6
# where the middle of the gun's barrel line goes (item space), and the sprite's diagonal it's laid along
CENTRE = (7.5, 9.5, 8.0)
SIZE = 256


def rz_ry_rx(x, y, z):
	x, y, z = (math.radians(v) for v in (x, y, z))
	cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
	X = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
	Y = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
	Z = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
	return mm(Z, mm(Y, X))


def mm(a, b):
	return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def mv(a, v):
	return [sum(a[i][k] * v[k] for k in range(3)) for i in range(3)]


def euler(m):
	"""M = Rz * Ry * Rx back to (x, y, z) degrees."""
	b = math.asin(max(-1.0, min(1.0, -m[2][0])))
	if abs(math.cos(b)) > 1e-6:
		a = math.atan2(m[2][1], m[2][2])
		c = math.atan2(m[1][0], m[0][0])
	else:
		a = math.atan2(-m[1][2], m[1][1])
		c = 0.0
	return [round(math.degrees(v), 4) for v in (a, b, c)]


def main():
	data = json.load(open(SRC, encoding="utf-8"))
	tw, th = data["resolution"]["width"], data["resolution"]["height"]
	tex = data["textures"][0]
	img = Image.open(io.BytesIO(base64.b64decode(tex["source"].split(",", 1)[1]))).convert("RGBA")
	padded = Image.new("RGBA", (SIZE, SIZE))
	padded.paste(img, (0, 0))
	os.makedirs(f"{PACK}/textures/item/gun", exist_ok=True)
	padded.save(f"{PACK}/textures/item/gun/flintlock_rifle.png")

	# the gun's frame onto the sprite's: its muzzle (-z) up-left, its top (+y) up-right, its side (x) out of the sprite
	r2 = 1 / math.sqrt(2)
	ez, ey = [r2, -r2, 0.0], [r2, r2, 0.0]
	ex = [ey[1] * ez[2] - ey[2] * ez[1], ey[2] * ez[0] - ey[0] * ez[2], ey[0] * ez[1] - ey[1] * ez[0]]
	G = [[ex[i], ey[i], ez[i]] for i in range(3)]
	pivot = [0.0, 14.0, -13.75]  # the middle of the barrel line, muzzle to butt

	elements = {e["uuid"]: e for e in data["elements"]}
	groups = {g["uuid"]: g for g in data.get("groups", []) or []}
	out = []

	def place(p):
		q = mv(G, [p[i] - pivot[i] for i in range(3)])
		return [CENTRE[i] + q[i] * SCALE for i in range(3)]

	def walk(node):
		if isinstance(node, str):
			e = elements[node]
			if e.get("type", "cube") != "cube":
				return
			eo = e.get("origin", [0, 0, 0])
			R = mm(G, rz_ry_rx(*e.get("rotation", [0, 0, 0])))
			o = place(eo)
			frm = [o[i] + (e["from"][i] - eo[i]) * SCALE for i in range(3)]
			to = [o[i] + (e["to"][i] - eo[i]) * SCALE for i in range(3)]
			faces = {}
			for name, f in e["faces"].items():
				if f.get("texture") is None:
					continue
				u0, v0, u1, v1 = f["uv"]
				face = {"uv": [round(u0 * 16 / SIZE, 4), round(v0 * 16 / SIZE, 4), round(u1 * 16 / SIZE, 4), round(v1 * 16 / SIZE, 4)],
						"texture": "#gun"}
				if f.get("rotation"):
					face["rotation"] = f["rotation"]
				faces[name] = face
			x, y, z = euler(R)
			out.append({"from": [round(v, 4) for v in frm], "to": [round(v, 4) for v in to],
						"rotation": {"origin": [round(v, 4) for v in o], "x": x, "y": y, "z": z}, "faces": faces})
			return
		g = dict(groups.get(node.get("uuid"), {}))
		g.update(node)
		if g.get("name") in DROP:
			return
		for c in node.get("children", []):
			walk(c)

	for n in data["outliner"]:
		walk(n)
	for e in out:
		for v in e["from"] + e["to"]:
			assert -16 <= v <= 32, e
	for name in ("musket", "musket_reload"):
		path = f"{PACK}/models/item/gun/{name}.json"
		model = json.load(open(path, encoding="utf-8"))
		model["textures"] = {"gun": "jaymospowers:item/gun/flintlock_rifle", "particle": "jaymospowers:item/gun/flintlock_rifle"}
		model["elements"] = out
		with open(path, "w", encoding="utf-8", newline="\n") as f:
			json.dump(model, f, indent=1)
	print(len(out), "cubes")


if __name__ == "__main__":
	main()
