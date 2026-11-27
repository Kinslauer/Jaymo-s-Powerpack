#!/usr/bin/env python3
"""
Night's Blood 4 — the vampire player's fifth shape ("a true shape of the night"):
  kingdoms/art/night_horror.bbmodel
  kingdoms/src/main/resources/assets/jaymoskingdoms/geckolib/models/entity/night_horror.geo.json
  kingdoms/src/main/resources/assets/jaymoskingdoms/geckolib/animations/entity/night_horror.animation.json
  kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/night_horror.png
  kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/night_horror_<skin>.png  (pale, dusk, russet, ebon)
  kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/night_horror_glow.png

Original vanilla-style design (not a wolf, not a bat, nothing from Nycto):
a tall, starved, gaunt winged horror (~2.45 blocks tall) with digitigrade sinewy legs, a sunken pelvis
and carved ribcage, a dorsal spine ridge, a craned gaunt skull with brow crests, swept pointed knife-ears,
glowing red eyes and a hinged fanged jaw, long crooked arms ending in splayed raking talons, and two-part
scalloped membrane wings folded close onto the back at rest that unfurl wide on its air-leap and roar.

Follows the Reason look (docs/Vanilla-Style.md):
- 64x64 mob texture sheet, well under 60 colours (~20-23 colours), zero half-transparent pixels.
- Zero per-pixel noise and zero soft gradients: every material uses a 4-to-7-step ramp taken from
  tools/vanilla_style/vstyle.py MAT, lit from above with clean hand-placed pixel shapes.
- Vampire skin variants (pale, dusk, russet, ebon) are exact 1-to-1 palette swaps of the same pixel grid.

Run from the repository root:
  python -I kingdoms/tools/gen_night_horror.py [--preview docs/previews/nycto-4-models]
"""
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

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, "tools", "vanilla_style"))
from vstyle import MAT, ramp  # noqa: E402

NAME = "night_horror"
ART_PATH = os.path.join(ROOT, "kingdoms", "art", f"{NAME}.bbmodel")
ASSETS = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms")
TEX_W, TEX_H = 64, 64
Z = (0.0, 0.0, 0.0)

# 4-step crimson accent ramp for glowing red eyes, inner maw and subtle membrane veins
CRIMSON = ramp("3b0a10", "6e1018", "c01824", "ff2e24")

# Dark leathery night-membrane ramps built strictly from vstyle.py MAT shades so wings stay dark on every coat
MEMBRANE_DARK = [
	MAT["netherite"][0], MAT["netherite"][1], MAT["netherite"][2],
	MAT["netherite"][3], MAT["netherite"][4], MAT["netherite"][5], MAT["netherite"][6],
]
MEMBRANE_DEEP = [
	MAT["netherite"][0], MAT["iron"][0], MAT["netherite"][1],
	MAT["netherite"][2], MAT["netherite"][3], MAT["netherite"][4], MAT["netherite"][5],
]
MEMBRANE_BLOOD = [
	MAT["wood"][0], MAT["copper"][0], MAT["copper"][1],
	MAT["copper"][2], MAT["copper"][3], MAT["wood"][4], MAT["wood"][5],
]
EBON_HIDE = [
	MAT["iron"][0], MAT["stone"][0], MAT["netherite"][1],
	MAT["iron"][1], MAT["stone"][1], MAT["stone"][2], MAT["iron"][2],
]
PALE_HIDE = [
	MAT["iron"][1], MAT["iron"][2], MAT["stone"][3],
	MAT["iron"][3], MAT["iron"][4], MAT["iron"][5], MAT["iron"][6],
]

# Vampire skin variants: exact 1-to-1 palette swaps of the same indexed pixel grid using vstyle.py MAT shades.
SKINS = {
	"night_horror": {
		"hide": MAT["stone"],
		"membrane": MEMBRANE_DARK,
		"bone": MAT["iron"],
		"crimson": CRIMSON,
	},
	"night_horror_pale": {
		"hide": PALE_HIDE,
		"membrane": MEMBRANE_DARK,
		"bone": MAT["stone"],
		"crimson": CRIMSON,
	},
	"night_horror_dusk": {
		"hide": MAT["netherite"],
		"membrane": MEMBRANE_DEEP,
		"bone": MAT["iron"],
		"crimson": CRIMSON,
	},
	"night_horror_russet": {
		"hide": MAT["wood"],
		"membrane": MEMBRANE_BLOOD,
		"bone": MAT["stone"],
		"crimson": CRIMSON,
	},
	"night_horror_ebon": {
		"hide": EBON_HIDE,
		"membrane": MEMBRANE_DEEP,
		"bone": MAT["stone"],
		"crimson": CRIMSON,
	},
}


# Wing rest pose (right side; the left mirrors y and z): both wings fold back and down flat against the back, the
# inner wing swept back from a shoulder root set behind the ribcage and spine ridge, the outer wing folded onto it.
# WING_OLD_* is the earlier half-spread rest the unfurl keyframes were authored around: their absolute poses are kept.
WING_Z = 1.8
OUTER_OFFSET = 0.25  # the folded outer membrane lies just outboard of the inner one, so the two planes never share depth
WING_REST_IN = (0.0, -72.0, 4.0)
WING_REST_OUT = (0.0, 0.0, -165.0)
WING_OLD_IN = (8.0, -58.0, 54.0)
WING_OLD_OUT = (-6.0, 28.0, -132.0)

# ---------------------------------------------------------------- digitigrade leg constants
# Absolute segment angles in degrees from straight down (positive = lower end swings forward, toward -z): thigh forward to the
# knee, shin back to the hock, metatarsal forward and down to the toe joint. Toes lie flat on the ground.
HIP_Y = 17.5
LEG_X = 2.3
L_THIGH, L_SHIN = 7.0, 8.0
LEG_ABS = (35.0, -40.0, 22.0)
TOE_H = 0.6  # half thickness of the toe pads: the toe joint sits this high when the sole is on the ground
L_META = (HIP_Y - TOE_H - L_THIGH * math.cos(math.radians(LEG_ABS[0])) - L_SHIN * math.cos(math.radians(LEG_ABS[1]))) \
	/ math.cos(math.radians(LEG_ABS[2]))
# Blockbench's x rotation swings the lower end of a limb toward -z (forward), so bone rotations are these angles as is.
ROT_SIGN = 1.0
LEG_REST_REL = tuple(ROT_SIGN * v for v in (LEG_ABS[0], LEG_ABS[1] - LEG_ABS[0], LEG_ABS[2] - LEG_ABS[1]))


def uid(tag):
	"""Deterministic UUID5 per bone/cube/animation name so re-running produces clean diffs."""
	return str(uuid.uuid5(uuid.NAMESPACE_OID, f"jaymoskingdoms.{NAME}.{tag}"))


# ---------------------------------------------------------------- indexed Reason-style grid painter

class IndexedSheet:
	"""Stores (slot, step_index) at each (x, y) of the 64x64 sheet so every skin variant is an exact palette swap."""

	def __init__(self, w, h):
		self.w, self.h = w, h
		self.grid = [[None] * w for _ in range(h)]
		self.glow_pixels = set()

	def put(self, x, y, slot, step, glow=False):
		if 0 <= x < self.w and 0 <= y < self.h:
			self.grid[y][x] = (slot, int(step))
			if glow:
				self.glow_pixels.add((x, y))
			else:
				self.glow_pixels.discard((x, y))

	def clear(self, x, y):
		if 0 <= x < self.w and 0 <= y < self.h:
			self.grid[y][x] = None
			self.glow_pixels.discard((x, y))

	def render(self, palette):
		img = Image.new("RGBA", (self.w, self.h), (0, 0, 0, 0))
		px = img.load()
		for y in range(self.h):
			for x in range(self.w):
				cell = self.grid[y][x]
				if cell is not None:
					slot, step = cell
					cols = palette[slot]
					c = cols[max(0, min(len(cols) - 1, step))]
					px[x, y] = c + (255,)
		return img

	def render_glow(self, palette):
		img = Image.new("RGBA", (self.w, self.h), (0, 0, 0, 0))
		px = img.load()
		for (x, y) in sorted(self.glow_pixels):
			cell = self.grid[y][x]
			if cell is not None:
				slot, step = cell
				cols = palette[slot]
				c = cols[max(0, min(len(cols) - 1, step))]
				px[x, y] = c + (255,)
		return img


def box_rects(u, v, sx, sy, sz):
	"""Standard Blockbench box-UV face rectangles (x0, y0, x1, y1) for integer UV size (sx, sy, sz)."""
	return {
		"up": (u + sz, v, u + sz + sx, v + sz),
		"down": (u + sz + sx, v, u + sz + 2 * sx, v + sz),
		"east": (u, v + sz, u + sz, v + sz + sy),
		"north": (u + sz, v + sz, u + sz + sx, v + sz + sy),
		"west": (u + sz + sx, v + sz, u + 2 * sz + sx, v + sz + sy),
		"south": (u + 2 * sz + sx, v + sz, u + 2 * sz + 2 * sx, v + sz + sy),
	}


def face_uvs(rects, mirror=False):
	"""Face UVs [u0, v0, u1, v1] matching Blockbench free-format quads (and mirrored twin when mirror=True)."""
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


def paint_shaded_box(sheet, rects, slot, hi=5, mid=4, side=3, back=3, lo=2, dark=1):
	"""Reason-look directional lighting from above: top lightest, upright sides stepped top-to-bottom, underside dark."""
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


# ---------------------------------------------------------------- custom anatomical detail painters

def paint_head_details(sheet, rects):
	# North face (6 x 7): gaunt hollow-cheeked vampire horror skull, furrowed brow, 2px crimson eyes, slit nose & fanged maw
	x0, y0, x1, y1 = rects["north"]
	sheet.put(x0 + 0, y0 + 0, "hide", 2)
	sheet.put(x1 - 1, y0 + 0, "hide", 2)
	sheet.put(x0 + 2, y0 + 0, "hide", 3)
	sheet.put(x0 + 3, y0 + 0, "hide", 3)
	# Furrowed V-brow ridge at y0+1
	sheet.put(x0 + 0, y0 + 1, "hide", 4)
	sheet.put(x0 + 1, y0 + 1, "hide", 5)
	sheet.put(x0 + 2, y0 + 1, "hide", 2)
	sheet.put(x0 + 3, y0 + 1, "hide", 2)
	sheet.put(x0 + 4, y0 + 1, "hide", 5)
	sheet.put(x0 + 5, y0 + 1, "hide", 4)
	# Deep eye sockets & glowing 2px crimson eyes at y0+2
	sheet.put(x0 + 0, y0 + 2, "crimson", 2, glow=True)
	sheet.put(x0 + 1, y0 + 2, "crimson", 3, glow=True)
	sheet.put(x0 + 2, y0 + 2, "hide", 3)
	sheet.put(x0 + 3, y0 + 2, "hide", 3)
	sheet.put(x0 + 4, y0 + 2, "crimson", 3, glow=True)
	sheet.put(x0 + 5, y0 + 2, "crimson", 2, glow=True)
	# Sharp cheekbones & nasal bridge at y0+3
	sheet.put(x0 + 0, y0 + 3, "hide", 5)
	sheet.put(x0 + 1, y0 + 3, "hide", 1)
	sheet.put(x0 + 2, y0 + 3, "hide", 4)
	sheet.put(x0 + 3, y0 + 3, "hide", 4)
	sheet.put(x0 + 4, y0 + 3, "hide", 1)
	sheet.put(x0 + 5, y0 + 3, "hide", 5)
	# Slit nostrils & sunken cheek hollows at y0+4
	sheet.put(x0 + 0, y0 + 4, "hide", 2)
	sheet.put(x0 + 1, y0 + 4, "hide", 0)
	sheet.put(x0 + 2, y0 + 4, "hide", 1)
	sheet.put(x0 + 3, y0 + 4, "hide", 1)
	sheet.put(x0 + 4, y0 + 4, "hide", 0)
	sheet.put(x0 + 5, y0 + 4, "hide", 2)
	# Drawn-back upper lip & fangs over dark maw at y0+5..6
	sheet.put(x0 + 0, y0 + 5, "hide", 2)
	sheet.put(x0 + 5, y0 + 5, "hide", 2)
	for lx in (1, 2, 3, 4):
		sheet.put(x0 + lx, y0 + 5, "crimson", 0)
	sheet.put(x0 + 0, y0 + 6, "hide", 1)
	sheet.put(x0 + 1, y0 + 6, "bone", 5)
	sheet.put(x0 + 2, y0 + 6, "crimson", 1)
	sheet.put(x0 + 3, y0 + 6, "crimson", 1)
	sheet.put(x0 + 4, y0 + 6, "bone", 5)
	sheet.put(x0 + 5, y0 + 6, "hide", 1)

	dx0, dy0, dx1, dy1 = rects["down"]
	for y in range(dy0 + 1, dy1 - 1):
		for x in range(dx0 + 1, dx1 - 1):
			sheet.put(x, y, "crimson", 1)

	for fn in ("east", "west"):
		sx0, sy0, sx1, sy1 = rects[fn]
		for ly in (2, 3, 4):
			sheet.put(sx0 + 2, sy0 + ly, "hide", 1)
			sheet.put(sx0 + 3, sy0 + ly, "hide", 2)

	bx0, by0, bx1, by1 = rects["south"]
	for ly in range(1, 6):
		sheet.put(bx0 + 2, by0 + ly, "hide", 4 if ly < 4 else 2)
		sheet.put(bx0 + 3, by0 + ly, "hide", 4 if ly < 4 else 2)


def paint_jaw_details(sheet, rects):
	ux0, uy0, ux1, uy1 = rects["up"]
	for y in range(uy0, uy1):
		for x in range(ux0, ux1):
			edge = x in (ux0, ux1 - 1) or y == uy0
			sheet.put(x, y, "bone" if edge else "crimson", 4 if edge else 1)
	nx0, ny0, nx1, ny1 = rects["north"]
	for lx in range(nx1 - nx0):
		sheet.put(nx0 + lx, ny0, "crimson" if lx in (1, 2, 3) else "hide", 0 if lx in (1, 2, 3) else 3)
		sheet.put(nx0 + lx, ny0 + 1, "hide", 4 if lx == 2 else 2)


def paint_ear_details(sheet, rects):
	# Darker inner concha hollow along the front/inner face of the swept knife-ear, bright upper cartilage rim
	for fn in ("north", "west"):
		x0, y0, x1, y1 = rects[fn]
		for ly in range(y1 - y0):
			for lx in range(x1 - x0):
				if ly == 0:
					sheet.put(x0 + lx, y0 + ly, "hide", 5)
				else:
					sheet.put(x0 + lx, y0 + ly, "crimson" if lx > 0 and lx < (x1 - x0 - 1) else "hide", 0 if lx > 0 and lx < (x1 - x0 - 1) else 2)


def paint_chest_details(sheet, rects):
	nx0, ny0, nx1, ny1 = rects["north"]
	for lx in (1, 2, 5, 6):
		sheet.put(nx0 + lx, ny0 + 0, "hide", 5)
	for lx in (3, 4):
		sheet.put(nx0 + lx, ny0 + 0, "hide", 1)
	for ly in range(1, 7):
		for lx in range(8):
			if lx in (3, 4):
				sheet.put(nx0 + lx, ny0 + ly, "hide", 2 if ly % 2 == 1 else 1)
			elif lx in (0, 7):
				sheet.put(nx0 + lx, ny0 + ly, "hide", 2)
			elif ly in (1, 3, 5):
				sheet.put(nx0 + lx, ny0 + ly, "hide", 5 if lx in (1, 2, 5, 6) and ly < 5 else 4)
			else:
				sheet.put(nx0 + lx, ny0 + ly, "hide", 1)

	sx0, sy0, sx1, sy1 = rects["south"]
	for ly in range(1, 6):
		sheet.put(sx0 + 3, sy0 + ly, "hide", 1)
		sheet.put(sx0 + 4, sy0 + ly, "hide", 1)
		if ly in (1, 2, 3):
			for lx in (1, 2, 5, 6):
				sheet.put(sx0 + lx, sy0 + ly, "hide", 4 if ly > 1 else 5)


def paint_abdomen_details(sheet, rects):
	nx0, ny0, nx1, ny1 = rects["north"]
	for ly in range(ny1 - ny0):
		sheet.put(nx0 + 2, ny0 + ly, "hide", 1)
		sheet.put(nx0 + 1, ny0 + ly, "hide", 3 if ly % 2 == 0 else 2)
		sheet.put(nx0 + 3, ny0 + ly, "hide", 3 if ly % 2 == 0 else 2)


def paint_limb_sinew(sheet, rects):
	for fn in ("north", "south", "east", "west"):
		x0, y0, x1, y1 = rects[fn]
		w, h = x1 - x0, y1 - y0
		if w >= 2 and h >= 5:
			for ly in range(1, h - 1):
				if fn in ("north", "east"):
					sheet.put(x0 + 0, y0 + ly, "hide", 4 if ly < h // 2 else 3)
					sheet.put(x0 + w - 1, y0 + ly, "hide", 2 if ly > 2 else 3)
				else:
					sheet.put(x0 + 0, y0 + ly, "hide", 3)
					sheet.put(x0 + w - 1, y0 + ly, "hide", 1 if ly > h // 2 else 2)


def paint_claw_details(sheet, rects):
	"""Dark keratin/bone root (step 3) tapering down to razor-pale tip (step 6) at the bottom."""
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


def line_cells(a, b):
	"""Clean 1px Bresenham line between two integer cells (inclusive)."""
	(x0, y0), (x1, y1) = a, b
	cells = []
	dx, dy = abs(x1 - x0), -abs(y1 - y0)
	sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
	err = dx + dy
	while True:
		cells.append((x0, y0))
		if (x0, y0) == (x1, y1):
			break
		e2 = 2 * err
		if e2 >= dy:
			err += dy
			x0 += sx
		if e2 <= dx:
			err += dx
			y0 += sy
	return cells


def paint_wing_membrane(sheet, rects, inner=True):
	"""Paints a leathery membrane as flat shapes: a 1px lighter bone strut along the arm and down each finger, broad
	horizontal shade bands (lightest beside the arm bone, darker toward the trailing edge, lit from above) and a
	continuous 1px dark rim along the scalloped trailing edge. No dots, no veins, no gradients.
	UV orientation: lx=0 is the outer +x end of the north face (the wrist on the inner wing, the wingtip on the outer
	wing) and lx=w-1 is the root end (the body on the inner wing, the wrist on the outer wing)."""
	for fn, (x0, y0, x1, y1) in rects.items():
		for y in range(y0, y1):
			for x in range(x0, x1):
				sheet.clear(x, y)

	nx0, ny0, nx1, ny1 = rects["north"]
	sx0, sy0, sx1, sy1 = rects["south"]
	w, h = nx1 - nx0, ny1 - ny0

	def depth_at(lx):
		if inner:
			t = lx / max(1, w - 1)
			arch = round(3.0 * math.sin(math.pi * t))
			return max(5, h - arch - (1 if lx > w // 2 else 0))
		pts = [(0, 2), (4, 8), (8, 14), (w - 1, 12)]
		for (xa, ya), (xb, yb) in zip(pts, pts[1:]):
			if xa <= lx <= xb:
				t = (lx - xa) / max(1, xb - xa)
				straight = ya + (yb - ya) * t
				scallop = 2.6 * math.sin(math.pi * t)
				return max(2, min(h, round(straight - scallop)))
		return 12

	depth = [depth_at(lx) for lx in range(w)]
	mask = {(lx, ly) for lx in range(w) for ly in range(depth[lx])}

	# finger struts: from the wrist down to the lowest point of each scallop
	if inner:
		wrist = (0, 0)
		ends = [(3, depth[3] - 1), (7, depth[7] - 1)]
	else:
		wrist = (w - 1, 0)
		ends = [(4, depth[4] - 1), (8, depth[8] - 1), (w - 1, depth[w - 1] - 1)]
	strut = set()
	for e in ends:
		for c in line_cells(wrist, e):
			if c in mask:
				strut.add(c)
	strut.update((lx, 0) for lx in range(w))

	def band(ly):
		return 4 if ly <= 3 else (3 if ly <= 7 else 2)

	cell = {}
	for (lx, ly) in mask:
		edge = any(
			0 <= lx + dx < w and 0 <= ly + dy < h and (lx + dx, ly + dy) not in mask
			for dx, dy in ((1, 0), (-1, 0), (0, 1))
		) or ly == h - 1
		if (lx, ly) in strut:
			cell[(lx, ly)] = 5
		elif edge:
			cell[(lx, ly)] = 1
		else:
			cell[(lx, ly)] = band(ly)

	# fold any one-pixel island into its neighbours so the shapes stay broad and flat
	for _ in range(3):
		for (lx, ly), step in list(cell.items()):
			if step in (5, 1):
				continue
			same = sum(cell.get((lx + dx, ly + dy)) == step for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
			if same == 0:
				for dx, dy in ((0, -1), (-1, 0), (1, 0), (0, 1)):
					nb = cell.get((lx + dx, ly + dy))
					if nb not in (None, 5, 1):
						cell[(lx, ly)] = nb
						break

	for (lx, ly), step in cell.items():
		sheet.put(nx0 + lx, ny0 + ly, "membrane", step)
		sheet.put(sx0 + (w - 1 - lx), sy0 + ly, "membrane", step)


# ---------------------------------------------------------------- model builder

class RigBuilder:
	def __init__(self):
		self.sheet = IndexedSheet(TEX_W, TEX_H)
		self.bones = {}
		self.outliner = []
		self.elements = []
		self.uv_patches = {}
		self.uv_bounds = []

	def bone(self, name, parent, pivot, rotation=Z):
		node = {
			"name": name,
			"origin": [round(v, 4) for v in pivot],
			"rotation": [round(v, 4) for v in rotation],
			"uuid": uid("bone." + name),
			"export": True,
			"isOpen": True,
			"color": 0,
			"children": [],
		}
		self.bones[name] = node
		if parent is None:
			self.outliner.append(node)
		else:
			self.bones[parent]["children"].append(node)
		return node

	def uv_box(self, key, u, v, sx, sy, sz, slot, detail_fn=None, **shade_kw):
		if key not in self.uv_patches:
			rw, rh = 2 * (sx + sz), sy + sz
			assert u + rw <= TEX_W and v + rh <= TEX_H, f"{key}: UV ({u},{v})+({rw},{rh}) exceeds {TEX_W}x{TEX_H}"
			for ok, ox0, oy0, ox1, oy1 in self.uv_bounds:
				overlap = not (u + rw <= ox0 or ox1 <= u or v + rh <= oy0 or oy1 <= v)
				assert not overlap, f"UV overlap between {key} ({u},{v},{u+rw},{v+rh}) and {ok} ({ox0},{oy0},{ox1},{oy1})"
			self.uv_bounds.append((key, u, v, u + rw, v + rh))
			rects = box_rects(u, v, sx, sy, sz)
			paint_shaded_box(self.sheet, rects, slot, **shade_kw)
			if detail_fn is not None:
				detail_fn(self.sheet, rects)
			self.uv_patches[key] = rects
		return self.uv_patches[key]

	def cube(self, bone, tag, frm, to, rects, mirror=False, inflate=0.0, rotation=Z, origin=None):
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
			"uuid": uid(f"cube.{bone}.{tag}"),
		}
		self.elements.append(el)
		self.bones[bone]["children"].append(el["uuid"])
		return el


def build_rig():
	b = RigBuilder()

	# ---- UV layout on 64x64 sheet (mathematically verified non-overlapping) ----
	# Band 0 (y = 0..13)
	uv_head = b.uv_box("head", 0, 0, 6, 7, 6, "hide", detail_fn=paint_head_details, hi=5, mid=4, side=3, back=3, lo=2, dark=1)
	uv_jaw = b.uv_box("jaw", 24, 0, 5, 2, 5, "hide", detail_fn=paint_jaw_details, hi=4, mid=3, side=3, back=2, lo=2, dark=1)
	uv_neck = b.uv_box("neck", 44, 0, 3, 4, 3, "hide", detail_fn=paint_limb_sinew, hi=4, mid=3, side=3, back=2, lo=2, dark=1)
	uv_spine = b.uv_box("spine", 56, 0, 1, 9, 1, "bone", hi=6, mid=5, side=4, back=4, lo=3, dark=2)
	uv_ear = b.uv_box("ear", 44, 7, 1, 2, 5, "hide", detail_fn=paint_ear_details, hi=5, mid=4, side=3, back=3, lo=2, dark=1)
	uv_fang = b.uv_box("fang", 24, 8, 1, 2, 1, "bone", detail_fn=paint_claw_details, hi=6, mid=6, side=5, back=5, lo=4, dark=3)
	uv_claw = b.uv_box("claw", 30, 8, 1, 3, 1, "bone", detail_fn=paint_claw_details, hi=6, mid=5, side=5, back=4, lo=3, dark=2)
	uv_spur = b.uv_box("spur", 36, 8, 1, 2, 1, "bone", hi=5, mid=4, side=4, back=3, lo=3, dark=2)

	# Band 1 (y = 13..24)
	uv_chest = b.uv_box("chest", 0, 13, 8, 7, 4, "hide", detail_fn=paint_chest_details, hi=5, mid=4, side=3, back=3, lo=2, dark=1)
	uv_body = b.uv_box("body", 24, 13, 5, 5, 3, "hide", detail_fn=paint_abdomen_details, hi=4, mid=3, side=3, back=2, lo=2, dark=1)
	uv_waist = b.uv_box("waist", 40, 14, 5, 3, 3, "hide", hi=4, mid=3, side=2, back=2, lo=1, dark=1)
	uv_brow = b.uv_box("brow", 56, 14, 2, 1, 2, "hide", hi=6, mid=5, side=4, back=4, lo=3, dark=2)

	# Band 2 (y = 24..36)
	uv_arm = b.uv_box("arm", 0, 24, 2, 9, 2, "hide", detail_fn=paint_limb_sinew, hi=5, mid=4, side=3, back=3, lo=2, dark=1)
	uv_forearm = b.uv_box("forearm", 8, 24, 2, 10, 2, "hide", detail_fn=paint_limb_sinew, hi=4, mid=3, side=3, back=2, lo=2, dark=1)
	uv_hand = b.uv_box("hand", 16, 24, 2, 3, 2, "hide", hi=4, mid=3, side=2, back=2, lo=2, dark=1)
	uv_leg = b.uv_box("leg", 24, 24, 2, 7, 3, "hide", detail_fn=paint_limb_sinew, hi=5, mid=4, side=3, back=3, lo=2, dark=1)
	uv_shin = b.uv_box("shin", 34, 24, 2, 8, 2, "hide", detail_fn=paint_limb_sinew, hi=4, mid=3, side=3, back=2, lo=2, dark=1)
	uv_foot = b.uv_box("foot", 42, 24, 2, 2, 4, "hide", hi=4, mid=3, side=3, back=2, lo=2, dark=1)
	uv_meta = b.uv_box("meta", 54, 24, 2, 5, 2, "hide", detail_fn=paint_limb_sinew, hi=4, mid=3, side=3, back=2, lo=2, dark=1)

	# Band 3 (y = 36..52): inner (9x12x1 -> 20x13) & outer (12x15x1 -> 26x16) wing membranes
	uv_wing_in = b.uv_box("wing_in", 0, 36, 11, 15, 1, "membrane", detail_fn=lambda s, r: paint_wing_membrane(s, r, inner=True))
	uv_wing_out = b.uv_box("wing_out", 24, 36, 15, 18, 1, "membrane", detail_fn=lambda s, r: paint_wing_membrane(s, r, inner=False))

	# Band 4 (y = 53..55): wing arm & finger leading bones
	uv_wing_arm = b.uv_box("wing_arm", 0, 57, 11, 1, 1, "bone", hi=5, mid=4, side=4, back=4, lo=4, dark=3)
	
	# ---- Skeleton & Geometry (Blockbench space: y up, feet at 0, front at -z, right at +x) ----
	ABDOMEN_Y = 19.2
	CHEST_Y = 23.6
	NECK_Y = 30.0
	HEAD_Y = 32.4

	b.bone("root", None, (0, 0, 0))

	# Pelvis / waist
	b.bone("waist", "root", (0, HIP_Y, 0.4))
	b.cube("waist", "pelvis", (-2.5, 16.4, -1.1), (2.5, 19.4, 1.9), uv_waist)
	b.cube("waist", "crest_r", (2.1, 17.6, -1.3), (2.9, 19.2, 0.7), uv_spur)
	b.cube("waist", "crest_l", (-2.9, 17.6, -1.3), (-2.1, 19.2, 0.7), uv_spur, mirror=True)

	# Digitigrade legs. The rest pose is carried by the bone rotations (a straight stack of hip, knee, hock and toe
	# joints, bent by LEG_REST_REL): thigh angled forward to the knee, shin angled back to the raised hock, a long
	# metatarsal angled forward and down to the toe joint, and only the toes and talons on the ground (y = 0).
	for side, sx, mir in (("right", 1, False), ("left", -1, True)):
		cx = LEG_X * sx
		z0 = 0.4
		hip_y, knee_y, hock_y, toe_y = HIP_Y, HIP_Y - L_THIGH, HIP_Y - L_THIGH - L_SHIN, HIP_Y - L_THIGH - L_SHIN - L_META
		b.bone(f"{side}_leg", "waist", (cx, hip_y, z0), (LEG_REST_REL[0], 0, 0))
		b.cube(f"{side}_leg", "thigh", (cx - 1.2, knee_y - 0.3, z0 - 1.5), (cx + 1.2, hip_y + 0.5, z0 + 1.5), uv_leg, mirror=mir)
		b.cube(f"{side}_leg", "knee", (cx - 0.5, knee_y - 0.6, z0 - 2.4), (cx + 0.5, knee_y + 0.8, z0 - 1.3), uv_spur, mirror=mir)

		b.bone(f"{side}_shin", f"{side}_leg", (cx, knee_y, z0), (LEG_REST_REL[1], 0, 0))
		b.cube(f"{side}_shin", "calf", (cx - 0.9, hock_y - 0.3, z0 - 1.0), (cx + 0.9, knee_y + 0.4, z0 + 1.0), uv_shin, mirror=mir)
		b.cube(f"{side}_shin", "hock_spur", (cx - 0.5, hock_y - 0.3, z0 + 0.9), (cx + 0.5, hock_y + 1.7, z0 + 2.2), uv_spur, mirror=mir)

		b.bone(f"{side}_foot", f"{side}_shin", (cx, hock_y, z0), (LEG_REST_REL[2], 0, 0))
		b.cube(f"{side}_foot", "metatarsal", (cx - 0.7, toe_y + 0.2, z0 - 0.9), (cx + 0.7, hock_y + 0.4, z0 + 0.9), uv_meta, mirror=mir)
		# toes and talons are levelled back to the ground against the foot bone's tilt (toe joint is the pivot)
		level = (-ROT_SIGN * LEG_ABS[2], 0, 0)
		for ti, tx_off in enumerate((-0.6, 0.6)):
			tx0, tx1 = sorted(((tx_off - 0.4) * sx + cx, (tx_off + 0.4) * sx + cx))
			b.cube(
				f"{side}_foot", f"toe_{ti}",
				(tx0, toe_y - TOE_H, z0 - 3.6), (tx1, toe_y + TOE_H, z0 + 0.5),
				uv_foot, mirror=mir, rotation=level, origin=(cx, toe_y, z0),
			)
			b.cube(
				f"{side}_foot", f"talon_{ti}",
				(tx0 + 0.15 * sx, toe_y - TOE_H, z0 - 4.8), (tx1 - 0.15 * sx, toe_y + 0.3, z0 - 3.5),
				uv_fang, mirror=mir, rotation=level, origin=(cx, toe_y, z0),
			)
		b.cube(
			f"{side}_foot", "hallux",
			(cx - 0.35, toe_y - TOE_H, z0 + 0.4), (cx + 0.35, toe_y + 0.4, z0 + 2.0),
			uv_spur, mirror=mir, rotation=level, origin=(cx, toe_y, z0),
		)

	# Predatory hunched torso: narrow starved abdomen curving forward into a broad, hollow-ribbed chest
	b.bone("body", "waist", (0, ABDOMEN_Y, 0.4))
	b.cube(
		"body", "abdomen",
		(-2.2, ABDOMEN_Y - 0.2, -1.1), (2.2, CHEST_Y + 0.4, 1.6),
		uv_body, rotation=(-8, 0, 0), origin=(0, ABDOMEN_Y, 0.4),
	)

	b.bone("chest", "body", (0, CHEST_Y, -0.2))
	b.cube(
		"chest", "ribcage",
		(-3.9, CHEST_Y - 0.2, -2.9), (3.9, NECK_Y + 0.3, 1.3),
		uv_chest, rotation=(-14, 0, 0), origin=(0, CHEST_Y, -0.2),
	)
	# Dorsal spine ridge & vertebral spurs following the arched hunch
	b.bone("spine", "chest", (0, CHEST_Y, 1.1))
	b.cube("spine", "ridge", (-0.5, CHEST_Y - 1.5, 0.9), (0.5, NECK_Y + 0.2, 2.0), uv_spine, rotation=(-14, 0, 0), origin=(0, CHEST_Y, -0.2))
	for si, (sy, sz_off) in enumerate(((23.0, 1.5), (25.2, 1.0), (27.4, 0.5), (29.4, 0.0))):
		b.cube("spine", f"vertebra_{si}", (-0.4, sy, sz_off), (0.4, sy + 1.4, sz_off + 1.2), uv_spur, rotation=(-26, 0, 0), origin=(0, sy, sz_off))

	# Forward-craned sinewy neck & angular gaunt vampire-horror skull
	b.bone("neck", "chest", (0, NECK_Y - 0.2, -1.8))
	b.cube(
		"neck", "throat",
		(-1.5, NECK_Y - 0.6, -3.2), (1.5, HEAD_Y + 0.6, -0.2),
		uv_neck, rotation=(-22, 0, 0), origin=(0, NECK_Y - 0.2, -1.8),
	)

	b.bone("head", "neck", (0, HEAD_Y, -2.8))
	b.cube("head", "cranium", (-2.8, HEAD_Y - 0.2, -6.5), (2.8, HEAD_Y + 6.4, -0.5), uv_head)
	# Sculpted brow crests sweeping back from above the glowing red eyes (breaks up any flat box silhouette!)
	b.cube("head", "brow_r", (0.7, HEAD_Y + 4.2, -6.9), (3.1, HEAD_Y + 5.3, -4.5), uv_brow, rotation=(8, 0, -10), origin=(1.8, HEAD_Y + 4.7, -6.5))
	b.cube("head", "brow_l", (-3.1, HEAD_Y + 4.2, -6.9), (-0.7, HEAD_Y + 5.3, -4.5), uv_brow, mirror=True, rotation=(8, 0, 10), origin=(-1.8, HEAD_Y + 4.7, -6.5))
	# Sagittal / occipital skull crest
	b.cube("head", "occiput", (-1.1, HEAD_Y + 5.5, -5.2), (1.1, HEAD_Y + 6.9, 0.2), uv_brow, rotation=(6, 0, 0), origin=(0, HEAD_Y + 6.0, -3.0))
	# Upper fangs descending from maxilla
	b.cube("head", "fang_r", (1.2, HEAD_Y - 1.6, -6.4), (2.0, HEAD_Y + 0.1, -5.6), uv_fang)
	b.cube("head", "fang_l", (-2.0, HEAD_Y - 1.6, -6.4), (-1.2, HEAD_Y + 0.1, -5.6), uv_fang, mirror=True)

	# Hinged lower jaw
	b.bone("jaw", "head", (0, HEAD_Y - 0.2, -2.2))
	b.cube("jaw", "mandible", (-2.4, HEAD_Y - 2.0, -6.6), (2.4, HEAD_Y - 0.2, -1.8), uv_jaw)
	b.cube("jaw", "tooth_r", (1.1, HEAD_Y - 0.5, -6.5), (1.8, HEAD_Y + 0.6, -5.8), uv_spur)
	b.cube("jaw", "tooth_l", (-1.8, HEAD_Y - 0.5, -6.5), (-1.1, HEAD_Y + 0.6, -5.8), uv_spur, mirror=True)

	# Tall, swept-upward-and-back pointed vampiric knife-ears (2 tapered segments per ear for a sharp silhouette)
	for side, sx, mir in (("right", 1, False), ("left", -1, True)):
		ex0, ex1 = sorted((2.6 * sx, 3.5 * sx))
		b.bone(f"{side}_ear", "head", (2.8 * sx, HEAD_Y + 3.4, -3.6), (34, -22 * sx, -18 * sx))
		b.cube(f"{side}_ear", "base", (ex0, HEAD_Y + 2.7, -3.8), (ex1, HEAD_Y + 4.8, 0.4), uv_ear, mirror=mir)
		tx0, tx1 = sorted((2.75 * sx, 3.35 * sx))
		b.cube(f"{side}_ear", "tip", (tx0, HEAD_Y + 3.3, 0.2), (tx1, HEAD_Y + 4.6, 3.2), uv_ear, mirror=mir)

	# Long, thin, jointed arms + splayed 3-talon + thumb raking hands!
	SHOULDER_Y = 29.0
	ELBOW_Y = 20.0
	WRIST_Y = 10.6
	for side, sx, mir in (("right", 1, False), ("left", -1, True)):
		ax0, ax1 = sorted((3.9 * sx, 5.9 * sx))
		b.bone(f"{side}_arm", "chest", (4.6 * sx, SHOULDER_Y, -1.5), (8, 0, 7 * sx))
		b.cube(f"{side}_arm", "humerus", (ax0, ELBOW_Y, -2.5), (ax1, SHOULDER_Y + 0.6, -0.5), uv_arm, mirror=mir)
		px0, px1 = sorted((4.4 * sx, 5.7 * sx))
		b.cube(f"{side}_arm", "shoulder_spur", (px0, SHOULDER_Y + 0.3, -2.0), (px1, SHOULDER_Y + 2.1, -0.8), uv_spur, mirror=mir)

		fx0, fx1 = sorted((4.0 * sx, 5.8 * sx))
		b.bone(f"{side}_forearm", f"{side}_arm", (4.9 * sx, ELBOW_Y, -1.5), (22, 0, -3 * sx))
		b.cube(f"{side}_forearm", "ulna", (fx0, WRIST_Y, -2.4), (fx1, ELBOW_Y + 0.2, -0.6), uv_forearm, mirror=mir)
		ox0, ox1 = sorted((4.5 * sx, 5.3 * sx))
		b.cube(f"{side}_forearm", "elbow_spur", (ox0, ELBOW_Y - 0.3, -0.7), (ox1, ELBOW_Y + 1.2, 0.5), uv_spur, mirror=mir)

		hx0, hx1 = sorted((3.8 * sx, 6.0 * sx))
		b.bone(f"{side}_hand", f"{side}_forearm", (4.9 * sx, WRIST_Y, -1.5), (10, 0, -5 * sx))
		b.cube(f"{side}_hand", "palm", (hx0, WRIST_Y - 2.4, -2.5), (hx1, WRIST_Y + 0.1, -0.5), uv_hand, mirror=mir)

		# Three splayed raking talons across X and Z (so all three read distinctly from the front AND side!)
		talon_specs = [
			("claw_0", 3.95, -2.35, 3.2, 16, -10),
			("claw_1", 4.85, -1.65, 3.6, 12, 0),
			("claw_2", 5.75, -0.95, 3.0, 8, 10),
		]
		for tname, tx_c, tz_c, tlen, rx_t, rz_t in talon_specs:
			cx0, cx1 = sorted(((tx_c - 0.35) * sx, (tx_c + 0.35) * sx))
			b.cube(
				f"{side}_hand", tname,
				(cx0, WRIST_Y - 2.3 - tlen, tz_c - 0.35), (cx1, WRIST_Y - 2.2, tz_c + 0.35),
				uv_claw, mirror=mir, rotation=(rx_t, 0, rz_t * sx), origin=(tx_c * sx, WRIST_Y - 2.3, tz_c),
			)
		# Inward hooked dewclaw / thumb talon
		th0, th1 = sorted((3.3 * sx, 4.0 * sx))
		b.cube(
			f"{side}_hand", "thumb_claw",
			(th0, WRIST_Y - 3.6, -2.6), (th1, WRIST_Y - 1.4, -1.9),
			uv_fang, mirror=mir, rotation=(22, 0, -24 * sx), origin=(3.7 * sx, WRIST_Y - 1.5, -2.2),
		)

	# Two-part scalloped membrane wings rooted on the shoulder blades.
	# Modeled extending outward along +x / -x, and folded tightly onto the back in the resting pose:
	# - Inner wing rises steeply up & back (+54 deg z, -58 deg y) so its hooked wrist-claw crests behind the shoulder!
	# - Outer wing folds sharply down (-132 deg z, net -78 deg) along the spine/flanks like a folded gargoyle cloak!
	WING_Y = 28.2
	INNER_SPAN, OUTER_SPAN = 11.0, 15.0
	for side, sx, mir in (("right", 1, False), ("left", -1, True)):
		root_x = 1.6 * sx
		wrist_x = (1.6 + INNER_SPAN) * sx
		tip_x = (1.6 + INNER_SPAN + OUTER_SPAN) * sx

		b.bone(f"{side}_wing", "chest", (root_x, WING_Y, WING_Z), (WING_REST_IN[0], WING_REST_IN[1] * sx, WING_REST_IN[2] * sx))
		wx0, wx1 = sorted((root_x, wrist_x))
		b.cube(f"{side}_wing", "arm_bone", (wx0, WING_Y - 0.45, WING_Z - 0.45), (wx1, WING_Y + 0.45, WING_Z + 0.45), uv_wing_arm, mirror=mir)
		b.cube(f"{side}_wing", "membrane_in", (wx0, WING_Y - 15.0, WING_Z - 0.05), (wx1, WING_Y - 0.15, WING_Z + 0.05), uv_wing_in, mirror=mir)
		b.bone(f"{side}_wing_outer", f"{side}_wing", (wrist_x, WING_Y, WING_Z - OUTER_OFFSET), (WING_REST_OUT[0], WING_REST_OUT[1] * sx, WING_REST_OUT[2] * sx))
		ox0, ox1 = sorted((wrist_x, tip_x))
		b.cube(f"{side}_wing_outer", "membrane_out", (ox0, WING_Y - 18.0, WING_Z - OUTER_OFFSET - 0.05), (ox1, WING_Y - 0.15, WING_Z - OUTER_OFFSET + 0.05), uv_wing_out, mirror=mir)

	return b


# ---------------------------------------------------------------- animations

def closed_loop(length, *pts):
	out = [(round(length * f, 4), v) for f, v in pts]
	out.append((round(length, 4), pts[0][1]))
	return out


def mirror_tracks(name, right_rot, left_rot=None):
	if left_rot is None:
		left_rot = [(t, (v[0], -v[1], -v[2])) for t, v in right_rot]
	return {f"right_{name}": {"rotation": right_rot}, f"left_{name}": {"rotation": left_rot}}


def make_anim(short_name, length, loop_mode, tracks, interp="catmullrom"):
	return {"name": f"animation.{NAME}.{short_name}", "length": length, "loop": loop_mode, "tracks": tracks, "interp": interp}


# ---------------------------------------------------------------- digitigrade leg solver
# Every moving animation plants the toe joint with a small two-bone solver (thigh + shin reach the hock, the metatarsal
# hangs from it at a chosen angle), so the toes stay on the ground through the stance and lift in the swing with the
# knee always bent forward and the hock back. Animation rotations are deltas on the rest bend (LEG_REST_REL).

_RAD = math.pi / 180.0
REST_TOE_F = sum(L * math.sin(a * _RAD) for L, a in zip((L_THIGH, L_SHIN, L_META), LEG_ABS))
LEG_SAMPLE_STEP = 0.05


def smooth(a, b, u):
	k = u * u * (3 - 2 * u)
	return a + (b - a) * k


def curve(keys, t):
	"""Smoothstep through [(time, value)] keys; value is a float or a tuple."""
	if t <= keys[0][0]:
		return keys[0][1]
	for (ta, va), (tb, vb) in zip(keys, keys[1:]):
		if ta <= t <= tb:
			u = 0.0 if tb - ta < 1e-9 else (t - ta) / (tb - ta)
			if isinstance(va, tuple):
				return tuple(smooth(x, y, u) for x, y in zip(va, vb))
			return smooth(va, vb, u)
	return keys[-1][1]


def solve_leg(hip_y, toe_f, toe_y, meta_abs, waist_x=0.0):
	"""Keyframe deltas (leg, shin, foot) in degrees from the rest bend, written the game's way (x turned round). toe_f is the toe joint's forward distance from the hip."""
	hock_f = toe_f - L_META * math.sin(meta_abs * _RAD)
	hock_y = toe_y + L_META * math.cos(meta_abs * _RAD)
	df, dy = hock_f, hock_y - hip_y
	d = math.hypot(df, dy)
	lo, hi = abs(L_THIGH - L_SHIN) + 0.5, L_THIGH + L_SHIN - 0.05
	if not lo <= d <= hi:
		print(f"  warning: leg reach {d:.2f} outside {lo:.2f}..{hi:.2f} (hip {hip_y:.2f}, toe {toe_f:.2f},{toe_y:.2f})")
		d = max(lo, min(hi, d))
	phi = math.atan2(df, -dy)
	alpha = math.acos(max(-1.0, min(1.0, (L_THIGH ** 2 + d ** 2 - L_SHIN ** 2) / (2 * L_THIGH * d))))
	a1 = phi + alpha
	knee_f, knee_y = L_THIGH * math.sin(a1), hip_y - L_THIGH * math.cos(a1)
	a2 = math.atan2(hock_f - knee_f, -(hock_y - knee_y))
	a1d, a2d = a1 / _RAD, a2 / _RAD
	rel = (ROT_SIGN * (a1d - waist_x), ROT_SIGN * (a2d - a1d), ROT_SIGN * (meta_abs - a2d))
	return tuple(round(rest - r, 3) + 0.0 for r, rest in zip(rel, LEG_REST_REL))


def plant_legs(tracks, length, foot_fn, step=LEG_SAMPLE_STEP):
	"""Adds right/left leg, shin and foot tracks to `tracks`. foot_fn(side, t) -> (world forward, height, metatarsal
	angle) of the toe joint. The root's position and the waist's rotation are re-keyed at the same dense times so the
	hip the legs solve for is exactly the hip the game draws."""
	n = max(1, round(length / step))
	times = [round(length * i / n, 4) for i in range(n + 1)]
	root_keys = tracks.get("root", {}).get("position") or [(0.0, Z)]
	waist_keys = tracks.get("waist", {}).get("rotation") or [(0.0, Z)]
	out = {f"{s}_{p}": {"rotation": []} for s in ("right", "left") for p in ("leg", "shin", "foot")}
	for t in times:
		rx, ry, rz = curve(root_keys, t)
		wx = -curve(waist_keys, t)[0]  # keys are the game's way; the solver works in Blockbench angles
		for side in ("right", "left"):
			f, y, meta = foot_fn(side, t)
			d = solve_leg(HIP_Y + ry, f + rz, y, meta, wx)
			for part, v in zip(("leg", "shin", "foot"), d):
				out[f"{side}_{part}"]["rotation"].append((t, (v, 0, 0)))
	if "root" in tracks:
		tracks["root"]["position"] = [(t, tuple(round(c, 4) for c in curve(root_keys, t))) for t in times]
	if "waist" in tracks:
		tracks["waist"]["rotation"] = [(t, tuple(round(c, 4) for c in curve(waist_keys, t))) for t in times]
	tracks.update(out)


def stride_feet(length, stance, center, reach, lift, toe_up, f0=None):
	"""Feet that alternate: planted while the body passes over (sliding back), then swung forward in an arc.
	The right foot's stance is centred on cycle phase `center` and spans `stance` of the cycle."""
	f0 = REST_TOE_F if f0 is None else f0

	def foot(side, t):
		p = (t / length + (0.0 if side == "right" else 0.5) - center + stance / 2) % 1.0
		if p < stance:
			u = p / stance
			return f0 + reach - 2 * reach * u, TOE_H, LEG_ABS[2]
		u = (p - stance) / (1 - stance)
		return (f0 - reach + 2 * reach * smooth(0, 1, u), TOE_H + lift * math.sin(math.pi * u),
				LEG_ABS[2] + toe_up * math.sin(math.pi * u))
	return foot


def rebase_wings(anim, threshold=0.0):
	"""Wing keyframes are deltas on the rest pose. Re-key those that spread the wings (any component over `threshold`)
	so they reach the same absolute pose as before the wings were folded; small keys and rest keys stay as they are."""
	for name, old, new in (("wing", WING_OLD_IN, WING_REST_IN), ("wing_outer", WING_OLD_OUT, WING_REST_OUT)):
		shift = (-(old[0] - new[0]), -(old[1] - new[1]), old[2] - new[2])
		for side, sg in (("right", 1), ("left", -1)):
			track = anim["tracks"].get(f"{side}_{name}")
			if not track:
				continue
			fixed = []
			for t, v in track["rotation"]:
				if max(abs(c) for c in v) > threshold:
					v = (v[0] + shift[0], v[1] + sg * shift[1], v[2] + sg * shift[2])
				fixed.append((t, tuple(round(c, 4) for c in v)))
			track["rotation"] = fixed


def build_animations():
	# Wing unfold reference (added to rest pose right_wing=[8, -58, 54] -> model [-8, +58, +54], right_wing_outer=[-6, 28, -132] -> model [+6, -28, -132]):
	# Full open wingspan: right_wing += (-6, -46, -24), right_wing_outer += (-4, +24, +116) -> inner at +30 deg z, outer at -16 deg z, wide open!
	# Half-flare (strike): right_wing += (-4, -26, -12), right_wing_outer += (-2, +14, +68)!

	# 1. idle (3.2s, loop): taut predatory breathing, slow jaw hiss, prowling head, twitching talons & subtly shifting folded wings
	idle = make_anim("idle", 3.2, "loop", {
		"waist": {"rotation": closed_loop(3.2, (0.0, (0, 0, 0)), (0.5, (2, 0, 0)))},
		"chest": {"rotation": closed_loop(3.2, (0.0, (0, 0, 0)), (0.5, (-4, 0, 0)))},
		"neck": {"rotation": closed_loop(3.2, (0.0, (0, 0, 0)), (0.5, (3, 0, 0)))},
		"head": {"rotation": closed_loop(3.2, (0.0, (0, -6, 0)), (0.35, (-3, 7, 2)), (0.7, (2, -2, -2)))},
		"jaw": {"rotation": closed_loop(3.2, (0.0, (3, 0, 0)), (0.45, (13, 0, 0)), (0.75, (5, 0, 0)))},
		**mirror_tracks("ear", closed_loop(3.2, (0.0, (0, 0, 0)), (0.4, (-5, 4, 4)), (0.55, (2, 0, -2)))),
		**mirror_tracks("arm", closed_loop(3.2, (0.0, (0, 0, 0)), (0.5, (-6, 0, 4)))),
		**mirror_tracks("forearm", closed_loop(3.2, (0.0, (0, 0, 0)), (0.5, (-8, 0, 0)))),
		**mirror_tracks("hand", closed_loop(3.2, (0.0, (0, 0, 0)), (0.3, (8, 0, 5)), (0.6, (-6, 0, -4)))),
		**mirror_tracks("wing", closed_loop(3.2, (0.0, (0, 0, 0)), (0.5, (0, -5, -3)))),
		**mirror_tracks("wing_outer", closed_loop(3.2, (0.0, (0, 0, 0)), (0.5, (0, 4, 10)))),
	})

	plant_legs(idle["tracks"], 3.2, lambda side, t: (REST_TOE_F, TOE_H, LEG_ABS[2]), step=0.2)

	# 2. walk (0.8s, loop): stalking digitigrade stride, low hunched ribcage, counter-swinging clawed arms, folded wings swaying
	walk = make_anim("walk", 0.8, "loop", {
		"root": {"position": closed_loop(0.8, (0.0, (0, 0.0, 0)), (0.25, (0, 0.65, 0)), (0.5, (0, 0.0, 0)), (0.75, (0, 0.65, 0)))},
		"waist": {"rotation": closed_loop(0.8, (0.0, (4, -5, -2)), (0.5, (4, 5, 2)))},
		"body": {"rotation": closed_loop(0.8, (0.0, (4, 3, 0)), (0.5, (4, -3, 0)))},
		"chest": {"rotation": closed_loop(0.8, (0.0, (6, 6, 2)), (0.5, (6, -6, -2)))},
		"neck": {"rotation": closed_loop(0.8, (0.0, (-4, -3, 0)), (0.5, (-4, 3, 0)))},
		"head": {"rotation": closed_loop(0.8, (0.0, (-6, -3, -1)), (0.25, (-4, 0, 0)), (0.5, (-6, 3, 1)), (0.75, (-4, 0, 0)))},
		"jaw": {"rotation": closed_loop(0.8, (0.0, (5, 0, 0)), (0.5, (9, 0, 0)))},
		"right_arm": {"rotation": closed_loop(0.8, (0.0, (22, 0, 6)), (0.5, (-28, 0, 4)))},
		"left_arm": {"rotation": closed_loop(0.8, (0.0, (-28, 0, -4)), (0.5, (22, 0, -6)))},
		"right_forearm": {"rotation": closed_loop(0.8, (0.0, (-6, 0, 0)), (0.5, (-22, 0, 0)))},
		"left_forearm": {"rotation": closed_loop(0.8, (0.0, (-22, 0, 0)), (0.5, (-6, 0, 0)))},
		"right_wing": {"rotation": closed_loop(0.8, (0.0, (2, -5, -2)), (0.5, (-2, 2, 2)))},
		"left_wing": {"rotation": closed_loop(0.8, (0.0, (-2, -2, -2)), (0.5, (2, 5, 2)))},
		"right_wing_outer": {"rotation": closed_loop(0.8, (0.0, (0, 4, 10)), (0.5, (0, -2, -4)))},
		"left_wing_outer": {"rotation": closed_loop(0.8, (0.0, (0, 2, 4)), (0.5, (0, -4, -10)))},
	})

	plant_legs(walk["tracks"], 0.8, stride_feet(0.8, 0.55, 0.25, 4.0, 3.5, 10.0))

	# 3. run (0.5s, loop): low predatory sprint, deep forward hunch, bounding stride, claws cocked to rend
	run = make_anim("run", 0.5, "loop", {
		"root": {"position": closed_loop(0.5, (0.0, (0, 0.0, 0)), (0.25, (0, 1.8, 0)), (0.5, (0, 0.0, 0)), (0.75, (0, 1.8, 0)))},
		"waist": {"rotation": closed_loop(0.5, (0.0, (10, -6, 0)), (0.5, (10, 6, 0)))},
		"body": {"rotation": closed_loop(0.5, (0.0, (8, 4, 0)), (0.5, (8, -4, 0)))},
		"chest": {"rotation": closed_loop(0.5, (0.0, (14, 8, 3)), (0.5, (14, -8, -3)))},
		"neck": {"rotation": closed_loop(0.5, (0.0, (-10, 0, 0)), (0.5, (-12, 0, 0)))},
		"head": {"rotation": closed_loop(0.5, (0.0, (-14, -4, 0)), (0.5, (-14, 4, 0)))},
		"jaw": {"rotation": closed_loop(0.5, (0.0, (14, 0, 0)), (0.5, (18, 0, 0)))},
		"right_arm": {"rotation": closed_loop(0.5, (0.0, (34, 0, 12)), (0.5, (-48, 0, 8)))},
		"left_arm": {"rotation": closed_loop(0.5, (0.0, (-48, 0, -8)), (0.5, (34, 0, -12)))},
		**mirror_tracks("forearm", closed_loop(0.5, (0.0, (-26, 0, 0)), (0.5, (-34, 0, 0)))),
		**mirror_tracks("wing", closed_loop(0.5, (0.0, (4, -14, -6)), (0.5, (-2, -8, -2)))),
		**mirror_tracks("wing_outer", closed_loop(0.5, (0.0, (0, 10, 26)), (0.5, (0, 6, 14)))),
	})

	plant_legs(run["tracks"], 0.5, stride_feet(0.5, 0.32, 0.0, 6.0, 7.0, 10.0))

	# 4. leap (0.9s, once): grounded coil (0.16s), explosive wing-unfurling bound into the air (0.38s-0.58s), fold on landing (0.9s)
	leap = make_anim("leap", 0.9, "once", {
		"root": {
			"position": [(0.0, Z), (0.16, (0, -2.1, 0.4)), (0.38, (0, 5.0, -1.5)), (0.58, (0, 5.6, -2.0)), (0.78, (0, -0.8, -0.4)), (0.9, Z)],
		},
		"waist": {"rotation": [(0.0, Z), (0.16, (18, 0, 0)), (0.38, (-12, 0, 0)), (0.58, (8, 0, 0)), (0.78, (10, 0, 0)), (0.9, Z)]},
		"chest": {"rotation": [(0.0, Z), (0.16, (20, 0, 0)), (0.38, (-18, 0, 0)), (0.58, (12, 0, 0)), (0.78, (8, 0, 0)), (0.9, Z)]},
		"head": {"rotation": [(0.0, Z), (0.16, (-20, 0, 0)), (0.38, (-6, 0, 0)), (0.58, (-12, 0, 0)), (0.9, Z)]},
		"jaw": {"rotation": [(0.0, Z), (0.16, (8, 0, 0)), (0.38, (30, 0, 0)), (0.58, (24, 0, 0)), (0.9, Z)]},
		# Arms sweep wide & forward with splayed claws ready to seize prey (angled apart so they frame the face instead of hiding it!)
		**mirror_tracks("arm", [(0.0, Z), (0.16, (28, 0, 14)), (0.38, (-92, 16, 32)), (0.58, (-76, 12, 24)), (0.78, (-18, 0, 10)), (0.9, Z)]),
		**mirror_tracks("forearm", [(0.0, Z), (0.16, (-24, 0, 0)), (0.38, (-14, 0, 8)), (0.58, (-10, 0, 6)), (0.9, Z)]),
		**mirror_tracks("hand", [(0.0, Z), (0.38, (-16, 0, 12)), (0.58, (14, 0, -6)), (0.9, Z)]),
		# Folded wings snap WIDE open on launch and sweep down in an air-jump stroke, then fold back onto the spine!
		**mirror_tracks("wing", [(0.0, Z), (0.16, (4, 6, 4)), (0.34, (-6, -48, -22)), (0.54, (8, -42, -38)), (0.76, (2, -16, -6)), (0.9, Z)]),
		**mirror_tracks("wing_outer", [(0.0, Z), (0.16, (0, -4, -8)), (0.34, (-4, 24, 116)), (0.54, (6, 20, 104)), (0.76, (0, 8, 36)), (0.9, Z)]),
	})

	# The grounded toes stay put through the coil, push off with the hips, trail and tuck in the air, then land flat.
	leap_feet = [
		(0.0, (REST_TOE_F, TOE_H, LEG_ABS[2])), (0.16, (REST_TOE_F, TOE_H, LEG_ABS[2])),
		(0.30, (REST_TOE_F - 0.5, 2.0, LEG_ABS[2])), (0.38, (REST_TOE_F - 4.5, 6.5, LEG_ABS[2] - 25)),
		(0.58, (REST_TOE_F + 0.5, 8.5, LEG_ABS[2] - 20)), (0.70, (REST_TOE_F + 2.5, 4.0, LEG_ABS[2] - 5)),
		(0.78, (REST_TOE_F + 2.5, TOE_H, LEG_ABS[2])), (0.9, (REST_TOE_F, TOE_H, LEG_ABS[2])),
	]
	plant_legs(leap["tracks"], 0.9, lambda side, t: curve(leap_feet, t), step=0.045)

	# 5. strike (0.55s, once): upper-body-only raking claw slash + jaw snap + menacing wing flare (legs keep walking!)
	strike = make_anim("strike", 0.55, "once", {
		"body": {"rotation": [(0.0, Z), (0.16, (-6, 18, 0)), (0.30, (12, -22, 0)), (0.42, (8, -12, 0)), (0.55, Z)]},
		"chest": {"rotation": [(0.0, Z), (0.16, (-12, 26, -4)), (0.30, (20, -32, 6)), (0.42, (12, -16, 3)), (0.55, Z)]},
		"neck": {"rotation": [(0.0, Z), (0.16, (6, -12, 0)), (0.30, (-8, 14, 0)), (0.55, Z)]},
		"head": {"rotation": [(0.0, Z), (0.16, (-6, -16, -4)), (0.30, (-4, 18, 6)), (0.42, (-2, 8, 2)), (0.55, Z)]},
		"jaw": {"rotation": [(0.0, Z), (0.16, (22, 0, 0)), (0.30, (32, 0, 0)), (0.42, (6, 0, 0)), (0.55, Z)]},
		"right_arm": {"rotation": [(0.0, Z), (0.16, (-122, 22, 36)), (0.30, (-34, -36, -14)), (0.42, (-22, -20, -6)), (0.55, Z)]},
		"right_forearm": {"rotation": [(0.0, Z), (0.16, (-24, 0, 0)), (0.30, (8, 0, -12)), (0.42, (-6, 0, -4)), (0.55, Z)]},
		"right_hand": {"rotation": [(0.0, Z), (0.16, (-22, 0, 12)), (0.30, (24, 0, -14)), (0.55, Z)]},
		"left_arm": {"rotation": [(0.0, Z), (0.16, (26, -12, -16)), (0.30, (-72, 22, -22)), (0.42, (-46, 12, -10)), (0.55, Z)]},
		"left_forearm": {"rotation": [(0.0, Z), (0.16, (-14, 0, 0)), (0.30, (-20, 0, 10)), (0.42, (-12, 0, 4)), (0.55, Z)]},
		"left_hand": {"rotation": [(0.0, Z), (0.30, (18, 0, 10)), (0.55, Z)]},
		**mirror_tracks("wing", [(0.0, Z), (0.16, (-4, -28, -12)), (0.30, (4, -20, -18)), (0.55, Z)]),
		**mirror_tracks("wing_outer", [(0.0, Z), (0.16, (-2, 16, 72)), (0.30, (2, 12, 54)), (0.55, Z)]),
	})

	# 6. roar (1.1s, once): upper-body terror display — wings snap wide open into a giant silhouette, head rears back, jaw unhinges
	roar = make_anim("roar", 1.1, "once", {
		"body": {"rotation": [(0.0, Z), (0.22, (8, 0, 0)), (0.45, (-10, 0, 0)), (0.82, (-8, 0, 0)), (1.1, Z)]},
		"chest": {"rotation": [(0.0, Z), (0.22, (12, 0, 0)), (0.45, (-18, 0, 0)), (0.64, (-16, 2, 0)), (0.82, (-18, -2, 0)), (1.1, Z)]},
		"neck": {"rotation": [(0.0, Z), (0.22, (8, 0, 0)), (0.45, (-12, 0, 0)), (0.82, (-10, 0, 0)), (1.1, Z)]},
		"head": {"rotation": [(0.0, Z), (0.22, (10, 0, 0)), (0.45, (-22, -5, 0)), (0.64, (-20, 5, 0)), (0.82, (-22, -3, 0)), (1.1, Z)]},
		"jaw": {"rotation": [(0.0, Z), (0.22, (4, 0, 0)), (0.42, (36, 0, 0)), (0.64, (30, 0, 0)), (0.84, (36, 0, 0)), (1.1, Z)]},
		**mirror_tracks("ear", [(0.0, Z), (0.42, (12, -8, -10)), (0.84, (12, -8, -10)), (1.1, Z)]),
		**mirror_tracks("arm", [(0.0, Z), (0.22, (-38, 0, 8)), (0.45, (24, 14, 42)), (0.82, (20, 12, 38)), (1.1, Z)]),
		**mirror_tracks("forearm", [(0.0, Z), (0.22, (-32, 0, 0)), (0.45, (-10, 0, 14)), (0.82, (-14, 0, 12)), (1.1, Z)]),
		**mirror_tracks("hand", [(0.0, Z), (0.45, (-20, 0, 14)), (0.82, (-18, 0, 12)), (1.1, Z)]),
		**mirror_tracks("wing", [(0.0, Z), (0.22, (4, 6, 4)), (0.45, (-6, -48, -20)), (0.82, (-4, -46, -24)), (1.1, Z)]),
		**mirror_tracks("wing_outer", [(0.0, Z), (0.22, (0, -4, -8)), (0.45, (-4, 24, 118)), (0.82, (-2, 22, 112)), (1.1, Z)]),
	})

	rebase_wings(run, -1.0)
	for anim in (leap, strike, roar):
		rebase_wings(anim, 11.0)

	return [idle, walk, run, leap, strike, roar]


def to_bbmodel(rig, anims):
	tex_img = rig.sheet.render(SKINS["night_horror"])
	buf = io.BytesIO()
	tex_img.save(buf, "PNG")
	src = "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()

	bone_uuids = {name: node["uuid"] for name, node in rig.bones.items()}
	bb_anims = []
	for a in anims:
		animators = {}
		interp = a.get("interp", "catmullrom")
		for bone, channels in a["tracks"].items():
			kfs = []
			for ch, frames in channels.items():
				for fi, (t, v) in enumerate(frames):
					sx_, sy_ = (-1, -1) if ch == "rotation" else (1, 1)
					kfs.append({
						"channel": ch,
						"data_points": [{"x": round(sx_ * v[0], 4) + 0.0, "y": round(sy_ * v[1], 4) + 0.0, "z": round(v[2], 4)}],
						"uuid": uid(f"kf.{a['name']}.{bone}.{ch}.{fi}"),
						"time": round(t, 4),
						"color": -1,
						"interpolation": interp,
					})
			animators[bone_uuids[bone]] = {"name": bone, "type": "bone", "keyframes": kfs}
		bb_anims.append({
			"uuid": uid("anim." + a["name"]),
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
		"name": NAME,
		"model_identifier": NAME,
		"resolution": {"width": TEX_W, "height": TEX_H},
		"elements": rig.elements,
		"outliner": rig.outliner,
		"textures": [{
			"path": "",
			"name": f"{NAME}.png",
			"folder": "",
			"namespace": "jaymoskingdoms",
			"id": "0",
			"width": TEX_W,
			"height": TEX_H,
			"uv_width": TEX_W,
			"uv_height": TEX_H,
			"particle": False,
			"render_mode": "default",
			"visible": True,
			"mode": "bitmap",
			"saved": True,
			"uuid": uid("texture.0"),
			"source": src,
		}],
		"animations": bb_anims,
	}


def render_previews(preview_dir):
	"""Renders rest pose, key animation frames, and a skin-variants comparison sheet via bbmodel_bake + bbmodel_preview."""
	os.makedirs(preview_dir, exist_ok=True)
	tex_dir = os.path.join(ASSETS, "textures", "entity")
	with tempfile.TemporaryDirectory() as tmp:
		# bbmodel_bake reads keyframe rotations the game's way, but this free-format project stores them Blockbench's way
		# (bbmodel_geckolib turns x and y round on export), so turn them round here too: the previews then pose the
		# model exactly as the exported animations do in game.
		with open(ART_PATH, encoding="utf-8") as f:
			preview_bb = json.load(f)
		for anim in preview_bb["animations"]:
			for animator in anim["animators"].values():
				for kf in animator["keyframes"]:
					if kf["channel"] == "rotation":
						for dp in kf["data_points"]:
							dp["x"], dp["y"] = -dp["x"], -dp["y"]
		preview_bb_path = os.path.join(tmp, f"{NAME}_preview.bbmodel")
		with open(preview_bb_path, "w", encoding="utf-8") as f:
			json.dump(preview_bb, f)
		subprocess.run([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_bake.py"), preview_bb_path, tmp, NAME], check=True)
		frames = [
			("rest", "-", 0.0, f"{NAME}_rest.png", None),
			("idle", "idle", 1.5, f"{NAME}_idle.png", None),
			("walk_a", "walk", 0.0, f"{NAME}_walk_0.png", None),
			("walk_b", "walk", 0.4, f"{NAME}_walk_1.png", None),
			("run", "run", 0.12, f"{NAME}_run.png", None),
			("leap_coil", "leap", 0.16, f"{NAME}_leap_coil.png", None),
			("leap_air", "leap", 0.38, f"{NAME}_leap_air.png", None),
			("strike_windup", "strike", 0.16, f"{NAME}_strike_windup.png", None),
			("strike_slash", "strike", 0.30, f"{NAME}_strike_slash.png", None),
			("roar", "roar", 0.45, f"{NAME}_roar.png", None),
		]
		for _, anim, at, fn, custom_tex in frames:
			out = os.path.join(preview_dir, fn)
			cmd = [sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_preview.py"), tmp, NAME, out, anim, str(at)]
			if custom_tex:
				cmd.append(custom_tex)
			subprocess.run(cmd, check=True)
			print("rendered", os.path.relpath(out, ROOT))

		# Render front-three-quarter (panel 2) + side (panel 1) of every vampire skin variant onto one clean comparison PNG
		variant_strips = []
		for skin_name in SKINS:
			v_out = os.path.join(tmp, f"var_{skin_name}.png")
			tex_file = os.path.join(tex_dir, f"{skin_name}.png")
			subprocess.run([
				sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_preview.py"),
				tmp, NAME, v_out, "idle", "1.5", tex_file,
			], check=True)
			im = Image.open(v_out).convert("RGBA")
			pw = im.width // 5
			front34 = im.crop((pw * 2, 0, pw * 3, im.height))
			side = im.crop((pw * 1, 0, pw * 2, im.height))
			pair = Image.new("RGBA", (pw * 2, im.height), (0, 0, 0, 0))
			pair.paste(front34, (0, 0))
			pair.paste(side, (pw, 0))
			variant_strips.append((skin_name, pair))

		pad = 12
		label_h = 26
		cell_w = max(c.width for _, c in variant_strips)
		cell_h = max(c.height for _, c in variant_strips)
		sheet = Image.new("RGBA", (len(variant_strips) * (cell_w + pad) + pad, cell_h + label_h + pad * 2), (26, 26, 30, 255))
		draw = ImageDraw.Draw(sheet)
		for idx, (skin_name, crop) in enumerate(variant_strips):
			ox = pad + idx * (cell_w + pad)
			sheet.alpha_composite(crop, (ox, pad + label_h))
			short = skin_name.replace(NAME + "_", "") if skin_name != NAME else "ashen (default)"
			draw.text((ox + 10, pad + 6), short, fill=(230, 226, 220, 255))
		comp_path = os.path.join(preview_dir, f"{NAME}_skins.png")
		sheet.save(comp_path)
		print("rendered", os.path.relpath(comp_path, ROOT))


def main(argv):
	rig = build_rig()
	anims = build_animations()
	bb = to_bbmodel(rig, anims)

	os.makedirs(os.path.dirname(ART_PATH), exist_ok=True)
	with open(ART_PATH, "w", encoding="utf-8", newline="\n") as f:
		json.dump(bb, f, indent="\t")
	print("wrote", os.path.relpath(ART_PATH, ROOT))

	# Export GeckoLib 5 model, animations and default texture (night_horror.png)
	subprocess.run([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_geckolib.py"), ART_PATH, ASSETS, NAME], check=True)

	# Write palette-swapped vampire skin variants + emissive eye layer
	tex_dir = os.path.join(ASSETS, "textures", "entity")
	os.makedirs(tex_dir, exist_ok=True)
	for skin_name, pal in SKINS.items():
		img = rig.sheet.render(pal)
		out = os.path.join(tex_dir, f"{skin_name}.png")
		img.save(out)
		cols = {img.getpixel((x, y))[:3] for y in range(img.height) for x in range(img.width) if img.getpixel((x, y))[3] > 0}
		print(f"wrote {os.path.relpath(out, ROOT)} ({len(cols)} opaque colours)")

	glow = rig.sheet.render_glow(SKINS["night_horror"])
	glow_path = os.path.join(tex_dir, f"{NAME}_glow.png")
	glow.save(glow_path)
	print("wrote", os.path.relpath(glow_path, ROOT))

	if "--preview" in argv:
		pdir = argv[argv.index("--preview") + 1]
		render_previews(pdir)


if __name__ == "__main__":
	main(sys.argv[1:])
