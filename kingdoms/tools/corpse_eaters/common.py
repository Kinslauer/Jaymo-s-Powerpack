"""Shared Reason-style indexed sheet painter, UV skyline packer, Blockbench/GeckoLib rig builder,
Java block model exporter, and preview renderer for Hunter's Road 3 (Corpse-eaters)."""
from __future__ import annotations

import base64
import io
import json
import math
import os
import subprocess
import sys
import tempfile
import uuid

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
sys.path.insert(0, os.path.join(ROOT, "tools", "vanilla_style"))
from vstyle import MAT  # noqa: E402

ASSETS = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms")
ART_DIR = os.path.join(ROOT, "kingdoms", "art")
Z = (0.0, 0.0, 0.0)

# ---------------------------------------------------------------- Reason-look palettes (all shades strictly from vstyle.py MAT)

# 5-to-7 shade ramps built exclusively from vstyle.py MAT tuples, ordered darkest (0) -> lightest (6):
# - MAT['netherite']: bruised livid corpse hide / dark carrion
# - MAT['stone']:     weathered grave-bone / pallid corpse grey
# - MAT['iron']:      stark bleached skull-bone / ancient steel
# - MAT['wood']:      grave-filth, caked peat & dark earth
# - MAT['copper']:    torn raw sinew, viscera, gore & rust
# - MAT['gold']:      hungry yellow eyes, jaundiced bile & tarnished clasp
# - MAT['diamond']:   putrid cave-rot verdigris (steps 0..4 + stone pale highlight)

LIVID_DARK = [
	MAT["iron"][0], MAT["netherite"][0], MAT["netherite"][1],
	MAT["netherite"][2], MAT["netherite"][3], MAT["netherite"][4], MAT["netherite"][5],
]
# Dark cave-bile & putrid moss-rot (dark steps of diamond + dark wood/stone, no bright cyan!)
PUTRID_BILE = [
	MAT["diamond"][0], MAT["diamond"][1], MAT["diamond"][2],
	MAT["wood"][2], MAT["wood"][3], MAT["wood"][4], MAT["wood"][5],
]
AGED_BONE = [
	MAT["stone"][0], MAT["stone"][1], MAT["stone"][2],
	MAT["stone"][3], MAT["stone"][4], MAT["stone"][5], MAT["iron"][5],
]
BLEACHED_BONE = [
	MAT["iron"][0], MAT["iron"][1], MAT["iron"][2],
	MAT["iron"][3], MAT["iron"][4], MAT["iron"][5], MAT["iron"][6],
]
DARK_FILTH = [
	MAT["wood"][0], MAT["wood"][1], MAT["wood"][2],
	MAT["wood"][3], MAT["wood"][4], MAT["wood"][5], MAT["wood"][6],
]
MIRE_SLUDGE = [
	MAT["netherite"][0], MAT["diamond"][0], MAT["diamond"][1],
	MAT["diamond"][2], MAT["netherite"][2], MAT["wood"][2], MAT["wood"][3],
]
RAW_SINEW = [
	MAT["copper"][0], MAT["copper"][1], MAT["copper"][2],
	MAT["copper"][3], MAT["copper"][4], MAT["copper"][5], MAT["copper"][6],
]
DARK_GORE = [
	MAT["netherite"][0], MAT["copper"][0], MAT["copper"][1],
	MAT["copper"][2], MAT["copper"][3], MAT["copper"][4], MAT["copper"][5],
]
# Tarnished bronze & dark iron-rust for ancient weapons/armour (no salmon-pink highlights!)
BRONZE_RUST = [
	MAT["wood"][0], MAT["copper"][0], MAT["copper"][1],
	MAT["copper"][2], MAT["gold"][1], MAT["gold"][2], MAT["gold"][3],
]
AMBER_EYES = [
	MAT["gold"][0], MAT["gold"][1], MAT["gold"][2],
	MAT["gold"][3], MAT["gold"][4], MAT["gold"][4], MAT["gold"][5],
]

PALETTES = {
	# B1: grave-gnawer & spined pack leader (exact 1-to-1 palette swap on shared UV sheet)
	"grave_gnawer": {
		"hide": MAT["netherite"],
		"bone": BLEACHED_BONE,
		"filth": DARK_FILTH,
		"sinew": RAW_SINEW,
		"eyes": AMBER_EYES,
	},
	"grave_gnawer_leader": {
		"hide": LIVID_DARK,
		"bone": AGED_BONE,
		"filth": DARK_FILTH,
		"sinew": DARK_GORE,
		"eyes": AMBER_EYES,
	},
	# B5: bloated dead & spined variant (exact 1-to-1 palette swap on shared UV sheet)
	"bloated_dead": {
		"hide": MAT["stone"],
		"bone": BLEACHED_BONE,
		"filth": DARK_FILTH,
		"sinew": RAW_SINEW,
		"eyes": AMBER_EYES,
	},
	"bloated_dead_spined": {
		"hide": MAT["netherite"],
		"bone": BLEACHED_BONE,
		"filth": DARK_FILTH,
		"sinew": DARK_GORE,
		"eyes": AMBER_EYES,
	},
	# B6: grave-brute (deep-cave miniboss)
	"grave_brute": {
		"hide": LIVID_DARK,
		"bone": AGED_BONE,
		"filth": DARK_FILTH,
		"sinew": RAW_SINEW,
		"eyes": AMBER_EYES,
	},
	# B7: bone-burrower & rot-sick heavy burrower (exact 1-to-1 palette swap on shared UV sheet)
	"bone_burrower": {
		"hide": MAT["stone"],
		"bone": BLEACHED_BONE,
		"filth": DARK_FILTH,
		"sinew": RAW_SINEW,
		"eyes": AMBER_EYES,
	},
	"bone_burrower_heavy": {
		"hide": PUTRID_BILE,
		"bone": AGED_BONE,
		"filth": MIRE_SLUDGE,
		"sinew": DARK_GORE,
		"eyes": AMBER_EYES,
	},
	# F1: nest of bones and filth
	"corpse_nest": {
		"hide": LIVID_DARK,
		"bone": BLEACHED_BONE,
		"filth": DARK_FILTH,
		"sinew": DARK_GORE,
		"eyes": BRONZE_RUST,
	},
	# F2: old skeleton & fallen warrior
	"old_skeleton": {
		"hide": LIVID_DARK,
		"bone": AGED_BONE,
		"filth": DARK_FILTH,
		"sinew": BLEACHED_BONE,  # weathered steel blade
		"eyes": BRONZE_RUST,     # tarnished bronze guard & rust accents
	},
	"fallen_warrior": {
		"hide": MAT["netherite"],  # tattered dark surcoat & straps
		"bone": AGED_BONE,         # old warrior bones
		"filth": DARK_FILTH,       # leather grip & cave dust
		"sinew": BLEACHED_BONE,    # battered steel helm, cuirass, pauldrons & blade
		"eyes": BRONZE_RUST,       # tarnished bronze crest, guard & corrosion
	},
}


def uid(model_name: str, tag: str) -> str:
	return str(uuid.uuid5(uuid.NAMESPACE_OID, f"jaymoskingdoms.{model_name}.{tag}"))


# ---------------------------------------------------------------- Indexed Reason-look sheet & UV packer

class IndexedSheet:
	"""Stores (slot, step) at each (x, y) so variants are exact 1-to-1 palette swaps with zero half-alpha or noise."""

	def __init__(self, w: int, h: int):
		self.w, self.h = w, h
		self.grid: list[list[tuple[str, int] | None]] = [[None] * w for _ in range(h)]
		self.patches: dict[str, dict[str, tuple[int, int, int, int]]] = {}
		self.occ = [[False] * w for _ in range(h)]

	def put(self, x: int, y: int, slot: str, step: int):
		if 0 <= x < self.w and 0 <= y < self.h:
			self.grid[y][x] = (slot, max(0, min(6, int(step))))

	def get(self, x: int, y: int) -> tuple[str, int] | None:
		if 0 <= x < self.w and 0 <= y < self.h:
			return self.grid[y][x]
		return None

	def alloc_box(self, key: str, sx: int, sy: int, sz: int, slot: str, detail_fn=None, **shade_kw) -> dict[str, tuple[int, int, int, int]]:
		if key in self.patches:
			return self.patches[key]
		sx, sy, sz = max(1, int(round(sx))), max(1, int(round(sy))), max(1, int(round(sz)))
		rw, rh = 2 * (sx + sz), sy + sz
		best_x, best_y = None, None
		for y in range(0, self.h - rh + 1):
			row0 = self.occ[y]
			for x in range(0, self.w - rw + 1):
				if row0[x]:
					continue
				if all(not any(self.occ[yy][x : x + rw]) for yy in range(y, y + rh)):
					best_x, best_y = x, y
					break
			if best_x is not None:
				break
		if best_x is None:
			raise AssertionError(f"UV sheet {self.w}x{self.h} full when placing {key} ({rw}x{rh})")
		for yy in range(best_y, best_y + rh):
			for xx in range(best_x, best_x + rw):
				self.occ[yy][xx] = True
		u, v = best_x, best_y
		rects = {
			"up": (u + sz, v, u + sz + sx, v + sz),
			"down": (u + sz + sx, v, u + sz + 2 * sx, v + sz),
			"east": (u, v + sz, u + sz, v + sz + sy),
			"north": (u + sz, v + sz, u + sz + sx, v + sz + sy),
			"west": (u + sz + sx, v + sz, u + 2 * sz + sx, v + sz + sy),
			"south": (u + 2 * sz + sx, v + sz, u + 2 * sz + 2 * sx, v + sz + sy),
		}
		paint_shaded_box(self, rects, slot, **shade_kw)
		if detail_fn is not None:
			detail_fn(self, rects)
		self.patches[key] = rects
		return rects

	def render(self, palette: dict[str, list[tuple[int, int, int]]]) -> Image.Image:
		img = Image.new("RGBA", (self.w, self.h), (0, 0, 0, 0))
		px = img.load()
		for y in range(self.h):
			for x in range(self.w):
				cell = self.grid[y][x]
				if cell is not None:
					slot, step = cell
					ramp_cols = palette[slot]
					c = ramp_cols[max(0, min(len(ramp_cols) - 1, step))]
					px[x, y] = c + (255,)
		return img


def face_uvs(rects: dict[str, tuple[int, int, int, int]], mirror: bool = False) -> dict[str, dict]:
	out = {}
	for fn, r in rects.items():
		uv = [r[0], r[3], r[2], r[1]] if fn == "down" else [r[0], r[1], r[2], r[3]]
		out[fn] = {"uv": uv, "texture": 0}
	if mirror:
		out["east"], out["west"] = dict(out["west"]), dict(out["east"])
		for fn in out:
			u0, v0, u1, v1 = out[fn]["uv"]
			out[fn] = {"uv": [u1, v0, u0, v1], "texture": 0}
	return out


def paint_shaded_box(sheet: IndexedSheet, rects, slot: str, hi=5, mid=4, side=3, back=3, lo=2, dark=1):
	"""Directional Reason-style shading from above: top lightest, upright sides stepped cleanly, underside dark."""
	for fn, (x0, y0, x1, y1) in rects.items():
		w, h = x1 - x0, y1 - y0
		for ly in range(h):
			for lx in range(w):
				if fn == "up":
					edge = lx == 0 or ly == 0 or lx == w - 1 or ly == h - 1
					step = mid if edge and w > 2 and h > 2 else hi
					if w >= 4 and h >= 4 and 1 <= lx <= w - 2 and ly == 1:
						step = min(6, hi + 1)
				elif fn == "down":
					step = dark
				else:
					base = mid if fn == "north" else (side if fn in ("east", "west") else back)
					if ly == 0 and h >= 3:
						step = min(6, base + 1)
					elif ly == h - 1 and h >= 2:
						step = max(0, lo - (1 if fn == "south" else 0))
					elif ly >= (h * 2) // 3 and h >= 4:
						step = lo
					else:
						step = base
					if w >= 4 and (lx == 0 or lx == w - 1) and 0 < ly < h - 1:
						step = max(1, step - 1)
				sheet.put(x0 + lx, y0 + ly, slot, step)


# ---------------------------------------------------------------- Reusable anatomical & surface shape painters (zero noise!)

def paint_feral_skull(eye_row=2, bone_snout=False):
	"""Clean skull/corpse-head face: dark eye sockets, 1-2px glowing eyes, nasal slit, bared teeth over sinew maw."""
	def fn(sheet: IndexedSheet, rects):
		x0, y0, x1, y1 = rects["north"]
		w, h = x1 - x0, y1 - y0
		slot_main = "bone" if bone_snout else "hide"
		ey = min(h - 2, max(1, eye_row))
		# Heavy furrowed brow bar above eyes
		if ey - 1 >= 0:
			for lx in range(w):
				sheet.put(x0 + lx, y0 + ey - 1, slot_main, 5 if lx in (1, w - 2) else 2)
		# Eye sockets + vivid yellow-gold 2px eyes (steps 4 & 3)
		for lx in range(w):
			sheet.put(x0 + lx, y0 + ey, slot_main, 1)
		if w >= 6:
			sheet.put(x0 + 1, y0 + ey, "eyes", 4)
			sheet.put(x0 + 2, y0 + ey, "eyes", 3)
			sheet.put(x1 - 2, y0 + ey, "eyes", 4)
			sheet.put(x1 - 3, y0 + ey, "eyes", 3)
		elif w >= 4:
			sheet.put(x0 + 0, y0 + ey, "eyes", 4)
			sheet.put(x1 - 1, y0 + ey, "eyes", 4)
		# Nasal cavity
		if ey + 1 < h - 1 and w >= 4:
			mid_l, mid_r = (w - 1) // 2, w // 2
			sheet.put(x0 + mid_l, y0 + ey + 1, slot_main, 0)
			sheet.put(x0 + mid_r, y0 + ey + 1, slot_main, 0)
		# Snarling upper maw & bone teeth along bottom row
		if h >= 4:
			for lx in range(1, w - 1):
				sheet.put(x0 + lx, y1 - 1, "bone" if lx % 2 == 1 else "sinew", 5 if lx % 2 == 1 else 1)
		# Underside roof of mouth
		dx0, dy0, dx1, dy1 = rects["down"]
		for y in range(dy0 + 1, dy1 - 1):
			for x in range(dx0 + 1, dx1 - 1):
				sheet.put(x, y, "sinew", 1)
	return fn


def paint_hollow_skull(sheet: IndexedSheet, rects):
	"""Bleached skeleton skull face with deep dark eye sockets, nasal triangle, and tooth row."""
	x0, y0, x1, y1 = rects["north"]
	w, h = x1 - x0, y1 - y0
	if h >= 6 and w >= 6:
		for lx in (1, 2, w - 3, w - 2):
			sheet.put(x0 + lx, y0 + 2, "hide", 0)
			sheet.put(x0 + lx, y0 + 3, "hide", 1)
		sheet.put(x0 + w // 2 - 1, y0 + 4, "hide", 0)
		sheet.put(x0 + w // 2, y0 + 4, "hide", 0)
		for lx in range(1, w - 1):
			sheet.put(x0 + lx, y1 - 1, "bone", 5 if lx % 2 == 1 else 2)


def paint_jaw_maw(sheet: IndexedSheet, rects):
	ux0, uy0, ux1, uy1 = rects["up"]
	for y in range(uy0, uy1):
		for x in range(ux0, ux1):
			rim = x in (ux0, ux1 - 1) or y == uy0
			sheet.put(x, y, "bone" if rim else "sinew", 5 if rim else 2)
	nx0, ny0, nx1, ny1 = rects["north"]
	for lx in range(nx1 - nx0):
		sheet.put(nx0 + lx, ny0, "bone" if lx % 2 == 1 else "sinew", 5 if lx % 2 == 1 else 1)


def paint_ribbed_torso(sheet: IndexedSheet, rects):
	"""Exposed pale bone ribs wrapping raw sinew & dark bruised hide on chest/back."""
	nx0, ny0, nx1, ny1 = rects["north"]
	w, h = nx1 - nx0, ny1 - ny0
	mid_l, mid_r = (w - 1) // 2, w // 2
	for ly in range(1, h - 1):
		rib_row = (ly % 2 == 1)
		for lx in range(1, w - 1):
			if lx in (mid_l, mid_r):
				sheet.put(nx0 + lx, ny0 + ly, "bone" if rib_row else "sinew", 4 if rib_row else 2)
			elif rib_row:
				sheet.put(nx0 + lx, ny0 + ly, "bone", 5 if ly < h // 2 else 4)
			else:
				sheet.put(nx0 + lx, ny0 + ly, "sinew", 2 if lx in (1, w - 2) else 1)
	# Dorsal spine bumps down back
	sx0, sy0, sx1, sy1 = rects["south"]
	sw, sh = sx1 - sx0, sy1 - sy0
	for ly in range(1, sh - 1):
		for lx in ((sw - 1) // 2, sw // 2):
			sheet.put(sx0 + lx, sy0 + ly, "bone" if ly % 2 == 1 else "hide", 5 if ly % 2 == 1 else 2)


def paint_swollen_belly(sheet: IndexedSheet, rects):
	"""Distended taut gore-belly: light upper dome, dark branched pressure veins, jaundiced blister spots."""
	for fn in ("north", "east", "west", "up"):
		x0, y0, x1, y1 = rects[fn]
		w, h = x1 - x0, y1 - y0
		if w < 4 or h < 4:
			continue
		# Clean diagonal/vertical vein lines (2px steps, no noise)
		cx = w // 2
		for ly in range(1, h - 1):
			vx = cx + (1 if (ly // 2) % 2 == 0 else -1)
			if 1 <= vx < w - 1:
				sheet.put(x0 + vx, y0 + ly, "sinew", 1)
		# Blister highlight cluster near upper-left of north/up face
		if fn in ("north", "up") and w >= 6 and h >= 6:
			for dx in range(2):
				for dy in range(2):
					sheet.put(x0 + 2 + dx, y0 + 2 + dy, "eyes", 4 if (dx + dy) == 0 else 3)


def paint_filth_caked(slot_top="hide"):
	"""Limb or lower body caked in dark grave-filth along the lower half, clean 2px stepped seam."""
	def fn(sheet: IndexedSheet, rects):
		for face in ("north", "south", "east", "west"):
			x0, y0, x1, y1 = rects[face]
			w, h = x1 - x0, y1 - y0
			cut = max(1, h // 2)
			for ly in range(h):
				zig = 1 if (ly == cut and w >= 3) else 0
				for lx in range(w):
					if ly > cut or (zig and lx % 2 == 0):
						step = 3 if ly == cut or ly == cut + 1 else (2 if ly < h - 1 else 1)
						sheet.put(x0 + lx, y0 + ly, "filth", step)
					elif ly == 0:
						sheet.put(x0 + lx, y0 + ly, slot_top, 4)
		dx0, dy0, dx1, dy1 = rects["down"]
		for y in range(dy0, dy1):
			for x in range(dx0, dx1):
				sheet.put(x, y, "filth", 1)
	return fn


def paint_claw_tip(sheet: IndexedSheet, rects):
	"""Dark filth/keratin base stepping cleanly to razor-pale bone tip."""
	for fn in ("north", "south", "east", "west"):
		x0, y0, x1, y1 = rects[fn]
		h = y1 - y0
		for ly in range(h):
			step = 3 if ly == 0 else (5 if ly < h - 1 else 6)
			for lx in range(x1 - x0):
				sheet.put(x0 + lx, y0 + ly, "bone", step)
	dx0, dy0, dx1, dy1 = rects["down"]
	for y in range(dy0, dy1):
		for x in range(dx0, dx1):
			sheet.put(x, y, "bone", 6)


def paint_osteoderm_shell(sheet: IndexedSheet, rects):
	"""Segmented calcified bone carapace plate for bone-burrower & grave-brute armor."""
	ux0, uy0, ux1, uy1 = rects["up"]
	uw, uh = ux1 - ux0, uy1 - uy0
	for ly in range(uh):
		for lx in range(uw):
			if ly == 0:
				step = 6
			elif ly == uh - 1:
				step = 2
			elif lx in (0, uw - 1):
				step = 3
			else:
				step = 5 if ly < uh // 2 else 4
			sheet.put(ux0 + lx, uy0 + ly, "bone", step)
	for fn in ("east", "west", "north", "south"):
		x0, y0, x1, y1 = rects[fn]
		for lx in range(x1 - x0):
			if y1 - y0 >= 2:
				sheet.put(x0 + lx, y0, "bone", 5)
				sheet.put(x0 + lx, y1 - 1, "sinew", 2)


def paint_mound_filth(sheet: IndexedSheet, rects):
	"""Charnel nest mound: broad 2x2 / 3x2 clods of grave-earth, dark carrion pockets, and embedded bone splinters."""
	for fn, (x0, y0, x1, y1) in rects.items():
		w, h = x1 - x0, y1 - y0
		for ly in range(h):
			for lx in range(w):
				bx, by = lx // 2, ly // 2
				k = (bx * 3 + by * 5) % 7
				if fn == "up":
					if k == 0 and w >= 4 and h >= 4 and 0 < lx < w - 1 and 0 < ly < h - 1:
						sheet.put(x0 + lx, y0 + ly, "bone", 4)
					elif k in (1, 2):
						sheet.put(x0 + lx, y0 + ly, "hide", 3)
					else:
						sheet.put(x0 + lx, y0 + ly, "filth", 4 if k < 5 else 3)
				elif fn != "down":
					if ly >= (h * 2) // 3:
						sheet.put(x0 + lx, y0 + ly, "filth", 1 if ly == h - 1 else 2)
					elif k == 0 and 0 < lx < w - 1:
						sheet.put(x0 + lx, y0 + ly, "sinew", 2)
					elif k == 3:
						sheet.put(x0 + lx, y0 + ly, "hide", 2)


def paint_rusted_armour(sheet: IndexedSheet, rects):
	"""Battered ancient steel armor (slot 'sinew') with top edge glint and stepped rust patches (slot 'eyes')."""
	for fn in ("north", "south", "east", "west", "up"):
		x0, y0, x1, y1 = rects[fn]
		w, h = x1 - x0, y1 - y0
		for ly in range(h):
			for lx in range(w):
				if fn == "up":
					edge = lx in (0, w - 1) or ly in (0, h - 1)
					sheet.put(x0 + lx, y0 + ly, "sinew", 3 if edge else 5)
				else:
					if ly == 0 and h >= 3:
						sheet.put(x0 + lx, y0 + ly, "sinew", 5)
					elif ly >= h - 2 and ((lx // 2 + ly) % 3 == 0):
						sheet.put(x0 + lx, y0 + ly, "eyes", 3 if ly == h - 2 else 2)
					elif lx in (0, w - 1):
						sheet.put(x0 + lx, y0 + ly, "sinew", 2)
					else:
						sheet.put(x0 + lx, y0 + ly, "sinew", 4 if ly < h // 2 else 3)


# ---------------------------------------------------------------- RigBuilder & Animation helpers

class RigBuilder:
	def __init__(self, name: str, sheet: IndexedSheet):
		self.name = name
		self.sheet = sheet
		self.bones: dict[str, dict] = {}
		self.bone_parents: dict[str, str | None] = {}
		self.outliner: list[dict] = []
		self.elements: list[dict] = []
		self.cube_bones: dict[str, str] = {}

	def bone(self, name: str, parent: str | None, pivot, rotation=Z) -> dict:
		assert name not in self.bones, f"Duplicate bone {name} in {self.name}"
		node = {
			"name": name,
			"origin": [round(v, 4) for v in pivot],
			"rotation": [round(v, 4) for v in rotation],
			"uuid": uid(self.name, "bone." + name),
			"export": True,
			"isOpen": True,
			"color": 0,
			"children": [],
		}
		self.bones[name] = node
		self.bone_parents[name] = parent
		if parent is None:
			self.outliner.append(node)
		else:
			self.bones[parent]["children"].append(node)
		return node

	def uv(self, key: str, sx: int, sy: int, sz: int, slot: str, detail_fn=None, **shade_kw):
		return self.sheet.alloc_box(key, sx, sy, sz, slot, detail_fn=detail_fn, **shade_kw)

	def cube(self, bone: str, tag: str, frm, to, rects, mirror=False, inflate=0.0, rotation=Z, origin=None):
		el = {
			"name": f"{bone}_{tag}",
			"box_uv": False,
			"rescale": False,
			"locked": False,
			"render_order": "default",
			"allow_mirror_modeling": True,
			"from": [round(v, 4) for v in frm],
			"to": [round(v, 4) for v in to],
			"autouv": 0,
			"color": 0,
			"inflate": round(inflate, 4),
			"origin": [round(v, 4) for v in (origin or self.bones[bone]["origin"])],
			"rotation": [round(v, 4) for v in rotation],
			"faces": face_uvs(rects, mirror=mirror),
			"type": "cube",
			"uuid": uid(self.name, f"cube.{bone}.{tag}"),
		}
		self.elements.append(el)
		self.bones[bone]["children"].append(el["uuid"])
		self.cube_bones[el["uuid"]] = bone
		return el


def closed_loop(length: float, *pts):
	out = [(round(length * f, 4), v) for f, v in pts]
	out.append((round(length, 4), pts[0][1]))
	return out


def mirror_rot(name: str, right_rot, left_rot=None):
	if left_rot is None:
		left_rot = [(t, (v[0], -v[1], -v[2])) for t, v in right_rot]
	return {f"right_{name}": {"rotation": right_rot}, f"left_{name}": {"rotation": left_rot}}


def make_anim(model_name: str, short_name: str, length: float, loop_mode: str, tracks: dict, interp="catmullrom"):
	return {
		"name": f"animation.{model_name}.{short_name}",
		"short": short_name,
		"length": length,
		"loop": loop_mode,
		"tracks": tracks,
		"interp": interp,
	}


def to_bbmodel(rig: RigBuilder, anims: list[dict], palette: dict) -> dict:
	tex_img = rig.sheet.render(palette)
	buf = io.BytesIO()
	tex_img.save(buf, "PNG")
	src = "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()

	bone_uuids = {name: node["uuid"] for name, node in rig.bones.items()}
	bb_anims = []
	for a in anims:
		animators = {}
		interp = a.get("interp", "catmullrom")
		for bone, channels in a["tracks"].items():
			if bone not in bone_uuids:
				continue
			kfs = []
			for ch, frames in channels.items():
				for fi, (t, v) in enumerate(frames):
					sx_, sy_ = (-1, -1) if ch == "rotation" else (1, 1)
					kfs.append({
						"channel": ch,
						"data_points": [{"x": round(sx_ * v[0], 4) + 0.0, "y": round(sy_ * v[1], 4) + 0.0, "z": round(v[2], 4) + 0.0}],
						"uuid": uid(rig.name, f"kf.{a['name']}.{bone}.{ch}.{fi}"),
						"time": round(t, 4),
						"color": -1,
						"interpolation": interp,
					})
			animators[bone_uuids[bone]] = {"name": bone, "type": "bone", "keyframes": kfs}
		bb_anims.append({
			"uuid": uid(rig.name, "anim." + a["name"]),
			"name": a["name"],
			"loop": a["loop"],
			"override": False,
			"length": a["length"],
			"snapping": 24,
			"selected": False,
			"anim_time_update": "",
			"blend_weight": "",
			"start_delay": "",
			"loop_delay": "",
			"animators": animators,
		})

	return {
		"meta": {"format_version": "4.10", "model_format": "free", "box_uv": False},
		"name": rig.name,
		"model_identifier": rig.name,
		"credit": "Original Reason-style art for Jaymo's Kingdoms; no external assets used.",
		"resolution": {"width": rig.sheet.w, "height": rig.sheet.h},
		"elements": rig.elements,
		"outliner": rig.outliner,
		"textures": [{
			"path": "",
			"name": f"{rig.name}.png",
			"folder": "",
			"namespace": "jaymoskingdoms",
			"id": "0",
			"width": rig.sheet.w,
			"height": rig.sheet.h,
			"uv_width": rig.sheet.w,
			"uv_height": rig.sheet.h,
			"particle": False,
			"render_mode": "default",
			"visible": True,
			"mode": "bitmap",
			"saved": True,
			"uuid": uid(rig.name, "texture.0"),
			"source": src,
		}],
		"animations": bb_anims,
	}


def descendants_of(rig: RigBuilder, drop_roots: set[str]) -> set[str]:
	drop = set(drop_roots)
	changed = True
	while changed:
		changed = False
		for b, par in rig.bone_parents.items():
			if par in drop and b not in drop:
				drop.add(b)
				changed = True
	return drop


def export_java_block_json(rig: RigBuilder, tex_rel: str, drop_bones: set[str] = frozenset()) -> dict:
	"""Exports a [0..16] vanilla Java block model JSON from a block-centered [-8..8] RigBuilder (bones at 0 rest rot)."""
	drop_all = descendants_of(rig, set(drop_bones))
	tw, th = rig.sheet.w, rig.sheet.h
	sx, sy = 16.0 / tw, 16.0 / th
	elements = []
	for e in rig.elements:
		bname = rig.cube_bones[e["uuid"]]
		if bname in drop_all:
			continue
		frm = [round(e["from"][0] + 8.0, 4), round(e["from"][1], 4), round(e["from"][2] + 8.0, 4)]
		to = [round(e["to"][0] + 8.0, 4), round(e["to"][1], 4), round(e["to"][2] + 8.0, 4)]
		faces = {}
		for fn, fc in e["faces"].items():
			u0, v0, u1, v1 = fc["uv"]
			faces[fn] = {"uv": [round(u0 * sx, 4), round(v0 * sy, 4), round(u1 * sx, 4), round(v1 * sy, 4)], "texture": "#0"}
		out_el = {"name": e["name"], "from": frm, "to": to, "faces": faces}
		rot = e.get("rotation", [0, 0, 0])
		axes = [(i, val) for i, val in enumerate(rot) if abs(val) > 1e-6]
		if axes:
			assert len(axes) == 1, f"{rig.name}/{e['name']}: block model cube rotation must use single axis, got {rot}"
			axis_idx, angle = axes[0]
			assert angle in (-45.0, -22.5, 22.5, 45.0), f"{rig.name}/{e['name']}: invalid block rotation angle {angle}"
			orig = e["origin"]
			out_el["rotation"] = {
				"origin": [round(orig[0] + 8.0, 4), round(orig[1], 4), round(orig[2] + 8.0, 4)],
				"axis": "xyz"[axis_idx],
				"angle": angle,
			}
		elements.append(out_el)
	return {
		"credit": "Original Reason-style art for Jaymo's Kingdoms; no external assets used.",
		"parent": "minecraft:block/block",
		"texture_size": [tw, th],
		"textures": {"0": f"jaymoskingdoms:{tex_rel}", "particle": f"jaymoskingdoms:{tex_rel}"},
		"elements": elements,
	}


def export_model_files(rig: RigBuilder, anims: list[dict], palette_key: str, is_block: bool = False, extra_block_variants=()):
	"""Writes kingdoms/art/<name>.bbmodel, runs bbmodel_geckolib.py (with animation.<model>.<name> keys!),
	and for block models also writes geckolib/models/block/, geckolib/animations/block/, textures/block/, and models/block/."""
	name = rig.name
	pal = PALETTES[palette_key]
	bb = to_bbmodel(rig, anims, pal)

	os.makedirs(ART_DIR, exist_ok=True)
	art_path = os.path.join(ART_DIR, f"{name}.bbmodel")
	with open(art_path, "w", encoding="utf-8", newline="\n") as f:
		json.dump(bb, f, indent="\t")

	# Rename short names -> animation.<name>.<short> so .animation.json has full GeckoLib names ("animation.<model>.<name>")
	rename_arg = ",".join(f"{a['short']}=animation.{name}.{a['short']}" for a in anims)
	cmd = [
		sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_geckolib.py"),
		art_path, ASSETS, name, "--rename", rename_arg,
	]
	subprocess.run(cmd, check=True)

	# Overwrite the entity texture directly from our clean IndexedSheet to guarantee exact palette bytes
	img = rig.sheet.render(pal)
	ent_tex = os.path.join(ASSETS, "textures", "entity", f"{name}.png")
	img.save(ent_tex)
	cols = {img.getpixel((x, y))[:3] for y in range(img.height) for x in range(img.width) if img.getpixel((x, y))[3] > 0}
	print(f"exported {name}: {len(rig.bones)} bones, {len(rig.elements)} cubes, {img.width}x{img.height} ({len(cols)} colours), {len(anims)} anims")

	if is_block:
		# Also provide GeckoLib block paths + textures/block/<name>.png + vanilla Java block JSON(s)
		blk_tex = os.path.join(ASSETS, "textures", "block", f"{name}.png")
		os.makedirs(os.path.dirname(blk_tex), exist_ok=True)
		img.save(blk_tex)

		ent_geo = os.path.join(ASSETS, "geckolib", "models", "entity", f"{name}.geo.json")
		ent_anim = os.path.join(ASSETS, "geckolib", "animations", "entity", f"{name}.animation.json")
		blk_geo = os.path.join(ASSETS, "geckolib", "models", "block", f"{name}.geo.json")
		blk_anim = os.path.join(ASSETS, "geckolib", "animations", "block", f"{name}.animation.json")
		os.makedirs(os.path.dirname(blk_geo), exist_ok=True)
		os.makedirs(os.path.dirname(blk_anim), exist_ok=True)
		with open(ent_geo, encoding="utf-8") as f_in, open(blk_geo, "w", encoding="utf-8", newline="\n") as f_out:
			f_out.write(f_in.read())
		with open(ent_anim, encoding="utf-8") as f_in, open(blk_anim, "w", encoding="utf-8", newline="\n") as f_out:
			f_out.write(f_in.read())

		blk_model_dir = os.path.join(ASSETS, "models", "block")
		os.makedirs(blk_model_dir, exist_ok=True)
		if extra_block_variants:
			for out_name, drop_set in extra_block_variants:
				jdata = export_java_block_json(rig, f"block/{name}", drop_bones=drop_set)
				with open(os.path.join(blk_model_dir, f"{out_name}.json"), "w", encoding="utf-8", newline="\n") as f:
					json.dump(jdata, f, indent=2)
					f.write("\n")
		else:
			jdata = export_java_block_json(rig, f"block/{name}")
			with open(os.path.join(blk_model_dir, f"{name}.json"), "w", encoding="utf-8", newline="\n") as f:
				json.dump(jdata, f, indent=2)
				f.write("\n")
	return art_path
