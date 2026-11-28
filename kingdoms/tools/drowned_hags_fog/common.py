"""Shared Reason-style indexed sheet painter, UV packer, Blockbench/GeckoLib rig builder,
Java block model exporter, and surface detail painters for Hunter's Road 4 (rows B2, B3, B4)."""
from __future__ import annotations

import base64
import io
import json
import os
import subprocess
import sys
import uuid

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
sys.path.insert(0, os.path.join(ROOT, "tools", "vanilla_style"))
from vstyle import MAT  # noqa: E402

ASSETS = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms")
ART_DIR = os.path.join(ROOT, "kingdoms", "art")
Z = (0.0, 0.0, 0.0)

# ---------------------------------------------------------------- Reason-look MAT ramps (strictly from vstyle.py MAT)
# Every shade is an exact tuple from vstyle.MAT, ordered darkest (0) -> lightest (6), with clean luminance steps:

# Brackish waterlogged river-corpse hide (deep river-teal shadows into pallid grey-green dorsal skin)
RIVER_HIDE = [
	MAT["diamond"][0], MAT["diamond"][1], MAT["diamond"][2],
	MAT["diamond"][3], MAT["stone"][4], MAT["stone"][5], MAT["iron"][5],
]
# Dark river-weed, fin-membranes & wet river-silt
RIVER_KELP = [
	MAT["netherite"][0], MAT["diamond"][0], MAT["wood"][1],
	MAT["wood"][2], MAT["wood"][3], MAT["wood"][4], MAT["wood"][5],
]
# Sallow fen-crone skin
MIRE_HIDE = [
	MAT["wood"][0], MAT["netherite"][1], MAT["netherite"][2],
	MAT["stone"][3], MAT["stone"][4], MAT["stone"][5], MAT["iron"][5],
]
# Sodden peat-moss & woven bog-reed cowl/skirt
BOG_SHAWL = [
	MAT["diamond"][0], MAT["diamond"][1], MAT["wood"][2],
	MAT["wood"][3], MAT["wood"][4], MAT["wood"][5], MAT["wood"][6],
]
# Heavy riverbank mud & peat clods
DARK_MUD = [
	MAT["netherite"][0], MAT["wood"][0], MAT["wood"][1],
	MAT["wood"][2], MAT["wood"][3], MAT["wood"][4], MAT["wood"][5],
]
# Brackish mire puddle sludge
MIRE_SLUDGE = [
	MAT["netherite"][0], MAT["diamond"][0], MAT["diamond"][1],
	MAT["diamond"][2], MAT["wood"][2], MAT["wood"][3], MAT["wood"][4],
]
# Corpse-pallid grave-hag skin
GRAVE_HIDE = [
	MAT["iron"][0], MAT["stone"][1], MAT["stone"][2],
	MAT["stone"][3], MAT["stone"][4], MAT["stone"][5], MAT["stone"][6],
]
# Tattered mourning veil & grave-winding cloth
GRAVE_SHROUD = [
	MAT["netherite"][0], MAT["netherite"][1], MAT["netherite"][2],
	MAT["netherite"][3], MAT["netherite"][4], MAT["netherite"][5], MAT["netherite"][6],
]
# Cold drowned-barrow wight skin
WIGHT_HIDE = [
	MAT["netherite"][0], MAT["stone"][0], MAT["stone"][1],
	MAT["stone"][2], MAT["stone"][3], MAT["stone"][4], MAT["iron"][4],
]
# Condensed dark-forest vapour cloak & mist tendrils
WIGHT_MIST = [
	MAT["diamond"][0], MAT["netherite"][1], MAT["netherite"][3],
	MAT["stone"][3], MAT["stone"][5], MAT["iron"][5], MAT["iron"][6],
]
# Pale spectral decoy-wisp swaps (exact 1-to-1 palette swap on fog_wight's UV grid, ~22 colours)
WISP_BODY = [
	MAT["diamond"][0], MAT["diamond"][1], MAT["diamond"][2],
	MAT["stone"][3], MAT["stone"][4], MAT["iron"][4], MAT["iron"][5],
]
WISP_VAPOUR = [
	MAT["diamond"][1], MAT["diamond"][2], MAT["diamond"][3],
	MAT["diamond"][4], MAT["iron"][4], MAT["diamond"][6], MAT["iron"][6],
]
WISP_CROWN = [
	MAT["wood"][1], MAT["wood"][2], MAT["wood"][3],
	MAT["stone"][2], MAT["stone"][3], MAT["stone"][5], MAT["iron"][5],
]
# Cold marsh-lantern lure light & wight eyes
MARSH_LURE = [
	MAT["diamond"][1], MAT["diamond"][2], MAT["diamond"][3],
	MAT["diamond"][4], MAT["diamond"][5], MAT["gold"][4], MAT["gold"][5],
]
WISP_LURE = [
	MAT["gold"][1], MAT["gold"][2], MAT["gold"][3],
	MAT["diamond"][4], MAT["diamond"][5], MAT["gold"][4], MAT["gold"][5],
]
# Shared bone, raw maw/tongue sinew & amber eyes
AGED_BONE = [
	MAT["stone"][0], MAT["stone"][1], MAT["stone"][2],
	MAT["stone"][3], MAT["stone"][4], MAT["stone"][5], MAT["iron"][5],
]
BLEACHED_BONE = MAT["iron"]
RAW_SINEW = MAT["copper"]
AMBER_EYES = [
	MAT["gold"][0], MAT["gold"][1], MAT["gold"][2],
	MAT["gold"][3], MAT["gold"][4], MAT["gold"][5], MAT["gold"][5],
]

PALETTES = {
	# B2: river_drowned (original river-bank ambush predator)
	"river_drowned": {
		"hide": RIVER_HIDE,
		"filth": RIVER_KELP,
		"bone": BLEACHED_BONE,
		"sinew": RAW_SINEW,
		"eyes": AMBER_EYES,
	},
	# B3: mire_hag (swamp crone that scoops & hurls bank mud)
	"mire_hag": {
		"hide": MIRE_HIDE,
		"shroud": BOG_SHAWL,
		"filth": DARK_MUD,
		"bone": BLEACHED_BONE,
		"sinew": RAW_SINEW,
		"eyes": AMBER_EYES,
	},
	# B3: mud_scoop (hollowed claw-furrowed mud scoop left behind on the bank)
	"mud_scoop": {
		"hide": MIRE_SLUDGE,
		"filth": DARK_MUD,
		"bone": AGED_BONE,
	},
	# B3: grave_hag (graveyard crone with skull collar & barbed whipping tongue)
	"grave_hag": {
		"hide": GRAVE_HIDE,
		"shroud": GRAVE_SHROUD,
		"filth": DARK_MUD,
		"bone": BLEACHED_BONE,
		"sinew": RAW_SINEW,
		"eyes": AMBER_EYES,
	},
	# B4: fog_wight & fog_wight_wisp (exact 1-to-1 palette swap on shared 64x64 UV sheet)
	"fog_wight": {
		"hide": WIGHT_HIDE,
		"mist": WIGHT_MIST,
		"crown": DARK_MUD,
		"filth": DARK_MUD,
		"bone": BLEACHED_BONE,
		"lure": MARSH_LURE,
	},
	"fog_wight_wisp": {
		"hide": WISP_BODY,
		"mist": WISP_VAPOUR,
		"crown": WISP_CROWN,
		"filth": WISP_VAPOUR,
		"bone": BLEACHED_BONE,
		"lure": WISP_LURE,
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
		self.glow_pixels: set[tuple[int, int]] = set()

	def put(self, x: int, y: int, slot: str, step: int, glow: bool = False):
		if 0 <= x < self.w and 0 <= y < self.h:
			self.grid[y][x] = (slot, max(0, min(6, int(step))))
			if glow:
				self.glow_pixels.add((x, y))
			else:
				self.glow_pixels.discard((x, y))

	def clear(self, x: int, y: int):
		if 0 <= x < self.w and 0 <= y < self.h:
			self.grid[y][x] = None
			self.glow_pixels.discard((x, y))

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

	def render_glow(self, palette: dict[str, list[tuple[int, int, int]]]) -> Image.Image | None:
		if not self.glow_pixels:
			return None
		img = Image.new("RGBA", (self.w, self.h), (0, 0, 0, 0))
		px = img.load()
		for (x, y) in sorted(self.glow_pixels):
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
	"""Directional Reason-style lighting from above: top lightest, upright sides stepped cleanly, underside dark."""
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


# ---------------------------------------------------------------- Reusable anatomical & surface shape painters (zero noise)

def paint_amphibian_skull(sheet: IndexedSheet, rects):
	"""River-predator skull: ridged brow, wide-set river-amber glowing eyes, gill slits on sides, pike-toothed maw."""
	x0, y0, x1, y1 = rects["north"]
	w, h = x1 - x0, y1 - y0
	if h >= 5 and w >= 6:
		for lx in range(w):
			sheet.put(x0 + lx, y0 + 1, "hide", 5 if lx in (1, w - 2) else 2)
			sheet.put(x0 + lx, y0 + 2, "eyes", 1 if lx in (2, w - 3) else 0)
		sheet.put(x0 + 0, y0 + 2, "eyes", 3, glow=True)
		sheet.put(x0 + 1, y0 + 2, "eyes", 5, glow=True)
		sheet.put(x1 - 2, y0 + 2, "eyes", 5, glow=True)
		sheet.put(x1 - 1, y0 + 2, "eyes", 3, glow=True)
		sheet.put(x0 + w // 2 - 1, y0 + 3, "hide", 0)
		sheet.put(x0 + w // 2, y0 + 3, "hide", 0)
		for lx in range(1, w - 1):
			sheet.put(x0 + lx, y1 - 1, "bone" if lx % 2 == 1 else "sinew", 5 if lx % 2 == 1 else 2)
	# Layered crimson-copper gill slits on east/west cheeks
	for fn in ("east", "west"):
		sx0, sy0, sx1, sy1 = rects[fn]
		sw, sh = sx1 - sx0, sy1 - sy0
		if sw >= 5 and sh >= 4:
			for gx, step in ((sw - 4, 3), (sw - 3, 2), (sw - 2, 0)):
				if 0 <= gx < sw:
					for gy in range(1, sh - 1):
						sheet.put(sx0 + gx, sy0 + gy, "sinew" if step > 0 else "hide", step)
	dx0, dy0, dx1, dy1 = rects["down"]
	for y in range(dy0 + 1, dy1 - 1):
		for x in range(dx0 + 1, dx1 - 1):
			sheet.put(x, y, "sinew", 1 if (x + y) % 2 == 0 else 3)


def paint_hag_face(eye_row: int = 2):
	"""Gaunt crone face: furrowed brow, deep eye hollows with 2px amber eyes, sunken cheeks, jagged snaggle-teeth."""
	def fn(sheet: IndexedSheet, rects):
		x0, y0, x1, y1 = rects["north"]
		w, h = x1 - x0, y1 - y0
		ey = min(h - 3, max(1, eye_row))
		# Furrowed V-brow
		for lx in range(w):
			sheet.put(x0 + lx, y0 + ey - 1, "hide", 5 if lx in (1, w - 2) else 1)
			sheet.put(x0 + lx, y0 + ey, "hide", 0)
		if w >= 6:
			sheet.put(x0 + 1, y0 + ey, "eyes", 5, glow=True)
			sheet.put(x0 + 2, y0 + ey, "eyes", 3, glow=True)
			sheet.put(x1 - 3, y0 + ey, "eyes", 3, glow=True)
			sheet.put(x1 - 2, y0 + ey, "eyes", 5, glow=True)
		# Cheekbones & sunken hollows below eyes
		if ey + 1 < h - 1:
			for lx in range(w):
				sheet.put(x0 + lx, y0 + ey + 1, "hide", 4 if lx in (0, w - 1, w // 2 - 1, w // 2) else 1)
		if ey + 2 < h - 1:
			for lx in range(w):
				sheet.put(x0 + lx, y0 + ey + 2, "hide", 1 if lx in (1, w - 2) else 2)
		# Snarling upper gums & crooked teeth
		for lx in range(1, w - 1):
			sheet.put(x0 + lx, y1 - 1, "bone" if lx in (1, w - 2, w // 2) else "sinew", 5 if lx in (1, w - 2, w // 2) else 1)
		dx0, dy0, dx1, dy1 = rects["down"]
		for y in range(dy0 + 1, dy1 - 1):
			for x in range(dx0 + 1, dx1 - 1):
				sheet.put(x, y, "sinew", 1)
	return fn


def paint_wight_skull(sheet: IndexedSheet, rects):
	"""Hollow barrow-wight skull: dark eye caverns with cold marsh-lure glowing eyes, nasal hollow, stark teeth."""
	x0, y0, x1, y1 = rects["north"]
	w, h = x1 - x0, y1 - y0
	if w >= 6 and h >= 6:
		for lx in range(w):
			sheet.put(x0 + lx, y0 + 1, "bone", 5 if lx in (1, w - 2) else 2)
			sheet.put(x0 + lx, y0 + 2, "hide", 0)
			sheet.put(x0 + lx, y0 + 3, "bone", 4 if lx in (0, w - 1) else 1)
		sheet.put(x0 + 1, y0 + 2, "lure", 5, glow=True)
		sheet.put(x0 + 2, y0 + 2, "lure", 3, glow=True)
		sheet.put(x1 - 3, y0 + 2, "lure", 3, glow=True)
		sheet.put(x1 - 2, y0 + 2, "lure", 5, glow=True)
		sheet.put(x0 + w // 2 - 1, y0 + 4, "hide", 0)
		sheet.put(x0 + w // 2, y0 + 4, "hide", 0)
		for lx in range(1, w - 1):
			sheet.put(x0 + lx, y1 - 1, "bone" if lx % 2 == 1 else "hide", 5 if lx % 2 == 1 else 0)


def paint_jaw_maw(tooth_slot: str = "bone", gum_slot: str = "sinew"):
	def fn(sheet: IndexedSheet, rects):
		ux0, uy0, ux1, uy1 = rects["up"]
		for y in range(uy0, uy1):
			for x in range(ux0, ux1):
				rim = x in (ux0, ux1 - 1) or y == uy0
				sheet.put(x, y, tooth_slot if rim else gum_slot, 5 if rim else 2)
		nx0, ny0, nx1, ny1 = rects["north"]
		for lx in range(nx1 - nx0):
			sheet.put(nx0 + lx, ny0, tooth_slot if lx % 2 == 1 else gum_slot, 5 if lx % 2 == 1 else 1)
	return fn


def paint_ribbed_torso(rib_slot: str = "bone", cavity_slot: str = "sinew", back_slot: str = "hide"):
	def fn(sheet: IndexedSheet, rects):
		nx0, ny0, nx1, ny1 = rects["north"]
		w, h = nx1 - nx0, ny1 - ny0
		mid_l, mid_r = (w - 1) // 2, w // 2
		for ly in range(1, h - 1):
			rib_row = (ly % 2 == 1)
			for lx in range(1, w - 1):
				if lx in (mid_l, mid_r):
					sheet.put(nx0 + lx, ny0 + ly, rib_slot if rib_row else cavity_slot, 4 if rib_row else 1)
				elif rib_row:
					sheet.put(nx0 + lx, ny0 + ly, rib_slot, 5 if ly < h // 2 else 4)
				else:
					sheet.put(nx0 + lx, ny0 + ly, cavity_slot, 2 if lx in (1, w - 2) else 1)
		sx0, sy0, sx1, sy1 = rects["south"]
		sw, sh = sx1 - sx0, sy1 - sy0
		for ly in range(1, sh - 1):
			for lx in ((sw - 1) // 2, sw // 2):
				sheet.put(sx0 + lx, sy0 + ly, rib_slot if ly % 2 == 1 else back_slot, 5 if ly % 2 == 1 else 2)
	return fn


def paint_hollow_ribcage(sheet: IndexedSheet, rects):
	"""Split barrow-wight ribcage with pitch-dark hollow centre framing the glowing lure-heart inside."""
	nx0, ny0, nx1, ny1 = rects["north"]
	w, h = nx1 - nx0, ny1 - ny0
	for ly in range(1, h - 1):
		for lx in range(1, w - 1):
			in_gap = (w // 2 - 2) <= lx <= (w // 2 + 1) and 1 <= ly <= h - 2
			if in_gap:
				sheet.put(nx0 + lx, ny0 + ly, "hide", 0)
			elif ly % 2 == 1:
				sheet.put(nx0 + lx, ny0 + ly, "bone", 5 if ly < h // 2 else 4)
			else:
				sheet.put(nx0 + lx, ny0 + ly, "hide", 1)
	sx0, sy0, sx1, sy1 = rects["south"]
	sw, sh = sx1 - sx0, sy1 - sy0
	for ly in range(1, sh - 1):
		for lx in ((sw - 1) // 2, sw // 2):
			sheet.put(sx0 + lx, sy0 + ly, "bone" if ly % 2 == 1 else "hide", 5 if ly % 2 == 1 else 1)


def paint_reed_shawl(sheet: IndexedSheet, rects):
	"""Clean vertical reed-thatch & peat-moss bands with a stepped muddy hem."""
	for fn in ("north", "south", "east", "west"):
		x0, y0, x1, y1 = rects[fn]
		w, h = x1 - x0, y1 - y0
		for ly in range(h):
			for lx in range(w):
				band = (lx // 2) % 2
				if ly == 0 and h >= 3:
					sheet.put(x0 + lx, y0 + ly, "shroud", 5 if band == 0 else 4)
				elif ly >= h - 2 and h >= 4:
					notch = (ly == h - 1) and (lx % 3 == 1)
					sheet.put(x0 + lx, y0 + ly, "filth" if notch else "shroud", 1 if notch else 2)
				else:
					step = (4 if band == 0 else 3) if ly < h // 2 else (3 if band == 0 else 2)
					sheet.put(x0 + lx, y0 + ly, "shroud", step)


def paint_tattered_cloth(slot: str = "shroud"):
	"""Clean vertical drapery folds with dark inner pleats and stepped ragged bottom hem."""
	def fn(sheet: IndexedSheet, rects):
		for face in ("north", "south", "east", "west"):
			x0, y0, x1, y1 = rects[face]
			w, h = x1 - x0, y1 - y0
			for ly in range(h):
				for lx in range(w):
					pleat = (lx % 3 == 1)
					if ly == 0 and h >= 3:
						step = 5 if not pleat else 3
					elif ly == h - 1:
						step = 1 if (lx % 2 == 0) else 2
					elif ly >= (h * 2) // 3:
						step = 2 if not pleat else 1
					else:
						step = 4 if not pleat else 2
					sheet.put(x0 + lx, y0 + ly, slot, step)
	return fn


def paint_mud_caked(slot_top: str = "hide"):
	"""Lower limb caked in dark swamp/grave mud along the bottom half with a clean 2px stepped seam."""
	def fn(sheet: IndexedSheet, rects):
		for face in ("north", "south", "east", "west"):
			x0, y0, x1, y1 = rects[face]
			w, h = x1 - x0, y1 - y0
			cut = max(1, h // 2)
			for ly in range(h):
				zig = 1 if (ly == cut and w >= 3) else 0
				for lx in range(w):
					if ly > cut or (zig and lx % 2 == 0):
						step = 3 if ly in (cut, cut + 1) else (2 if ly < h - 1 else 1)
						sheet.put(x0 + lx, y0 + ly, "filth", step)
					elif ly == 0:
						sheet.put(x0 + lx, y0 + ly, slot_top, 4)
		dx0, dy0, dx1, dy1 = rects["down"]
		for y in range(dy0, dy1):
			for x in range(dx0, dx1):
				sheet.put(x, y, "filth", 1)
	return fn


def paint_claw_tip(sheet: IndexedSheet, rects):
	"""Dark base stepping cleanly to a razor-pale bone tip."""
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


def paint_fin_membrane(ray_slot: str = "bone", web_slot: str = "filth"):
	"""Clean fin rays alternating with dark aquatic webbing."""
	def fn(sheet: IndexedSheet, rects):
		for face in ("east", "west", "north", "south"):
			x0, y0, x1, y1 = rects[face]
			w, h = x1 - x0, y1 - y0
			for ly in range(h):
				for lx in range(w):
					if ly == 0 or (lx % 2 == 0 and ly < h - 1):
						sheet.put(x0 + lx, y0 + ly, ray_slot, 5 if ly == 0 else 4)
					else:
						sheet.put(x0 + lx, y0 + ly, web_slot, 3 if ly < h // 2 else 1)
	return fn


def paint_barbed_tongue(sheet: IndexedSheet, rects):
	"""Glistening raw crimson-copper tongue muscle with pale rasping barbs along the edges."""
	for fn, (x0, y0, x1, y1) in rects.items():
		w, h = x1 - x0, y1 - y0
		for ly in range(h):
			for lx in range(w):
				if fn == "up":
					barb = (ly % 2 == 1) and lx in (0, w - 1) and w >= 2
					sheet.put(x0 + lx, y0 + ly, "bone" if barb else "sinew", 5 if barb else (5 if ly < h // 2 else 4))
				elif fn == "down":
					sheet.put(x0 + lx, y0 + ly, "sinew", 2)
				else:
					sheet.put(x0 + lx, y0 + ly, "sinew", 4 if ly == 0 else 3)


def paint_mud_clump(sheet: IndexedSheet, rects):
	"""Blocky clods of wet dark riverbank mud & embedded peat."""
	for fn, (x0, y0, x1, y1) in rects.items():
		w, h = x1 - x0, y1 - y0
		for ly in range(h):
			for lx in range(w):
				k = ((lx // 2) * 3 + (ly // 2) * 5) % 5
				if fn == "up":
					sheet.put(x0 + lx, y0 + ly, "filth", 5 if k in (0, 1) else (4 if k < 4 else 3))
				elif fn == "down":
					sheet.put(x0 + lx, y0 + ly, "filth", 1)
				else:
					sheet.put(x0 + lx, y0 + ly, "filth", 4 if ly == 0 else (3 if k < 3 and ly < h - 1 else 2))


def paint_lure_orb(sheet: IndexedSheet, rects):
	"""Cold glowing marsh-lantern core inside the fog-wight's ribcage."""
	for fn, (x0, y0, x1, y1) in rects.items():
		w, h = x1 - x0, y1 - y0
		for ly in range(h):
			for lx in range(w):
				edge = lx in (0, w - 1) or ly in (0, h - 1)
				step = 4 if edge else 6
				sheet.put(x0 + lx, y0 + ly, "lure", step, glow=True)


def paint_mini_skull(sheet: IndexedSheet, rects):
	"""Small trophy skull for grave_hag's collar necklace."""
	x0, y0, x1, y1 = rects["north"]
	w, h = x1 - x0, y1 - y0
	if w >= 3 and h >= 3:
		sheet.put(x0 + 0, y0 + 1, "hide", 0)
		sheet.put(x1 - 1, y0 + 1, "hide", 0)
		sheet.put(x0 + w // 2, y1 - 1, "hide", 1)


# ---------------------------------------------------------------- RigBuilder & Exporters

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


def export_java_block_json(rig: RigBuilder, tex_rel: str) -> dict:
	"""Exports a [0..16] vanilla Java block model JSON from a block-centered [-8..8] RigBuilder."""
	tw, th = rig.sheet.w, rig.sheet.h
	sx, sy = 16.0 / tw, 16.0 / th
	elements = []
	for e in rig.elements:
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


def export_model_files(rig: RigBuilder, anims: list[dict], palette_key: str, is_block: bool = False):
	"""Writes kingdoms/art/<name>.bbmodel, runs bbmodel_geckolib.py (with animation.<model>.<name> keys),
	writes optional <name>_glow.png, and for block models also writes block assets."""
	name = rig.name
	pal = PALETTES[palette_key]
	bb = to_bbmodel(rig, anims, pal)

	os.makedirs(ART_DIR, exist_ok=True)
	art_path = os.path.join(ART_DIR, f"{name}.bbmodel")
	with open(art_path, "w", encoding="utf-8", newline="\n") as f:
		json.dump(bb, f, indent="\t")

	rename_arg = ",".join(f"{a['short']}=animation.{name}.{a['short']}" for a in anims)
	cmd = [
		sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_geckolib.py"),
		art_path, ASSETS, name, "--rename", rename_arg,
	]
	subprocess.run(cmd, check=True)

	img = rig.sheet.render(pal)
	ent_tex = os.path.join(ASSETS, "textures", "entity", f"{name}.png")
	img.save(ent_tex)

	glow_img = rig.sheet.render_glow(pal)
	if glow_img is not None and not is_block:
		glow_tex = os.path.join(ASSETS, "textures", "entity", f"{name}_glow.png")
		glow_img.save(glow_tex)

	cols = {img.getpixel((x, y))[:3] for y in range(img.height) for x in range(img.width) if img.getpixel((x, y))[3] > 0}
	print(f"exported {name}: {len(rig.bones)} bones, {len(rig.elements)} cubes, {img.width}x{img.height} ({len(cols)} colours), {len(anims)} anims")

	if is_block:
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
		jdata = export_java_block_json(rig, f"block/{name}")
		with open(os.path.join(blk_model_dir, f"{name}.json"), "w", encoding="utf-8", newline="\n") as f:
			json.dump(jdata, f, indent=2)
			f.write("\n")
	return art_path
