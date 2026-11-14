"""
The heraldry armour (HeraldryArmour, Harness): every model, texture, icon, item file and name, all our own cubes and
paint (the ideas are Andy's picks from Knights & Heraldry, which is all rights reserved: nothing of it is used here).

Each piece is a small Blockbench project built here from cubes, baked by tools/bbmodel_bake.py into
assets/jaymoskingdoms/bbmodels/heraldry_<piece>.json and textures/entity/bb/heraldry_<piece>.png, and hung on the
wearer's bones by the client's HeraldryLayer (bones named as ReverentLayer names them: armorHead, armorBody,
armorRightArm, armorRightLeg, armorRightBoot and their Left twins; a helm's moving visor is the bone "visor").

- eight late-medieval helms: frogmouth, houndskull bascinet, armet, close helm, savoyard, sallet with bevor, kettle
  hat, morion;
- the plate cuirass, spaulders (with elbow discs and raised neck guards, in plate, mail and brigandine), arm harness,
  gauntlets, cuisses, greaves and sabatons;
- every plate piece painted three times: bright, blackened and gilded (the same cubes, another palette);
- the giornea, the cloak and the hood (pale cloth: the game tints them with the piece's dye), and their torn twins;
- the torse and the crests that stand on it: dragon, lion, stag, boar, eagle, pegasus, unicorn, snake, horns, wings;
- the mail haubergeon's sheet for the under-armour layer (UnderArmourLayer), and icons for it and the arming doublet.

Blockbench space: y up, feet at 0, the front at -z, the wearer's right at +x.

Run from the project root: python3 -I kingdoms/tools/gen_heraldry_armour.py [--preview <dir>]   (needs Pillow)
The preview stands each piece on a grey figure, which is not in the game's files.
"""
import json
import os
import subprocess
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image  # noqa: E402

import gen_reverent as r  # noqa: E402
import gen_tomb_undead as kit  # noqa: E402
from gen_tomb_undead import Builder, shade  # noqa: E402

ROOT = kit.ROOT
ASSETS = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms")
DATA = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "data")
SIDES = ("north", "south", "east", "west")
Z = (0, 0, 0)

# ---- the palettes: one for each finish of plate, and the things that never change -----------------------------------
FINISHES = {
	"": {"plate": (172, 180, 190), "edge": (228, 234, 240), "dark": (36, 38, 46), "rivet": (120, 126, 136)},
	"blackened": {"plate": (44, 44, 52), "edge": (112, 114, 128), "dark": (12, 12, 16), "rivet": (150, 152, 162)},
	"gilded": {"plate": (212, 170, 64), "edge": (252, 230, 146), "dark": (40, 28, 12), "rivet": (255, 244, 196)},
}
FIXED = {"mail": (124, 132, 142), "strap": (96, 66, 40), "brig": (124, 44, 38), "padding": (206, 190, 150),
	"cloth": (236, 236, 236), "cloth_dark": (150, 150, 150), "bone": (226, 216, 186), "gilt": (214, 172, 66),
	"figure": (120, 124, 130),
	# the crests' paint
	"dragon": (58, 128, 62), "dragon_wing": (176, 50, 40), "lion": (214, 164, 54), "mane": (158, 104, 34),
	"stag": (134, 88, 52), "boar": (54, 46, 42), "eagle": (38, 38, 46), "white": (238, 238, 242), "snake": (62, 124, 70),
	"belly": (212, 196, 110), "red": (190, 40, 36), "feather": (230, 230, 236), "horn": (222, 212, 186), "pink": (206, 140, 130)}


def put(img, x, y, c, a=255):
	if 0 <= x < img.width and 0 <= y < img.height:
		img.putpixel((x, y), tuple(c) + (a,))


def mul(img, x, y, k):
	if 0 <= x < img.width and 0 <= y < img.height:
		c = img.getpixel((x, y))
		img.putpixel((x, y), shade(c[:3], k) + (c[3],))


def clear(img, x, y):
	put(img, x, y, (0, 0, 0), 0)


# ---- painters (each takes the sheet and one face's rectangle) --------------------------------------------------------

def plate(img, rect):
	"""A plate: a bright upper and leading edge, a dark lower edge, a rivet in each upper corner of a big one."""
	x0, y0, x1, y1 = rect
	for x in range(x0, x1):
		mul(img, x, y0, 1.28)
		mul(img, x, y1 - 1, 0.66)
	for y in range(y0, y1):
		mul(img, x0, y, 1.14)
	if x1 - x0 >= 6 and y1 - y0 >= 5:
		for x in (x0 + 1, x1 - 2):
			put(img, x, y0 + 1, kit.PALETTE["rivet"])


def lames(step, first=None):
	"""Plates overlapping downwards: a dark seam every {step} rows with a bright lip under it."""
	def fx(img, rect):
		plate(img, rect)
		x0, y0, x1, y1 = rect
		for y in range(y0 + (step if first is None else first), y1 - 1, step):
			for x in range(x0, x1):
				mul(img, x, y, 0.58)
				mul(img, x, y + 1, 1.2)
	return fx


def keel(img, rect):
	"""A breastplate's front: a raised line down the middle."""
	plate(img, rect)
	x0, y0, x1, y1 = rect
	mid = (x0 + x1) // 2
	for y in range(y0 + 1, y1 - 1):
		mul(img, mid, y, 1.3)
		mul(img, mid - 1, y, 0.8)


def slit(row, pad=1, gap=None):
	"""A sight: a dark line {row} rows down, {pad} in from each side; with {gap}, parted by a nasal that wide."""
	def fx(img, rect):
		plate(img, rect)
		x0, y0, x1, y1 = rect
		mid = (x0 + x1) // 2
		for x in range(x0 + pad, x1 - pad):
			if gap and abs(x - mid + 0.5) < gap / 2:
				continue
			put(img, x, y0 + row, kit.PALETTE["dark"])
	return fx


def breaths(img, rect):
	"""Breathing holes: a few dark dots."""
	plate(img, rect)
	x0, y0, x1, y1 = rect
	for y in range(y0 + 1, y1 - 1, 2):
		for x in range(x0 + 1 + (y - y0) % 4 // 2, x1 - 1, 2):
			put(img, x, y, kit.PALETTE["dark"])


def bellows(img, rect):
	"""A close helm's visor: the sight, then ridges across it, each with its row of breaths."""
	slit(1)(img, rect)
	x0, y0, x1, y1 = rect
	for y in range(y0 + 3, y1 - 1, 2):
		for x in range(x0 + 1, x1 - 1):
			mul(img, x, y, 0.7 if x % 2 else 0.5)


def death(img, rect):
	"""The savoyard's face: two round eyes rimmed bright, a nose hole, a row of teeth slits."""
	plate(img, rect)
	x0, y0, x1, y1 = rect
	dark = kit.PALETTE["dark"]
	for ex in (x0 + 1, x1 - 3):
		for dx in range(2):
			for dy in range(2):
				put(img, ex + dx, y0 + 1 + dy, dark)
		for dx in range(-1, 3):
			mul(img, ex + dx, y0, 1.25)
	for x in range((x0 + x1 - 1) // 2, (x0 + x1) // 2 + 1):
		put(img, x, y0 + 3, dark)
	for x in range(x0 + 2, x1 - 2, 2):
		put(img, x, y1 - 2, dark)


def studs(img, rect):
	"""Brigandine: cloth over small plates, a rivet head for each."""
	x0, y0, x1, y1 = rect
	for y in range(y0, y1):
		for x in range(x0, x1):
			if (x - x0) % 2 == 1 and (y - y0) % 2 == 1:
				put(img, x, y, kit.PALETTE["gilt"])
	for x in range(x0, x1):
		mul(img, x, y1 - 1, 0.7)


def bands(img, rect):
	"""A horn's rings, or a snake's: a darker row now and then."""
	x0, y0, x1, y1 = rect
	for y in range(y0 + 1, y1, 3):
		for x in range(x0, x1):
			mul(img, x, y, 0.72)


def pleats(img, rect):
	"""Cloth hanging in folds: a shadow, a ridge and the plain cloth by turns, and a darker hem."""
	x0, y0, x1, y1 = rect
	for x in range(x0, x1):
		k = (0.72, 1.0, 0.9)[(x - x0) % 3]
		for y in range(y0, y1):
			mul(img, x, y, k * (0.8 if y == y1 - 1 else 1.0))


def ragged(depths, holes=()):
	"""Cloth as {pleats}, its lower edge torn away in strips ({depths}: rows lost, strip by strip) and worn through."""
	def fx(img, rect):
		pleats(img, rect)
		x0, y0, x1, y1 = rect
		for x in range(x0, x1):
			lost = depths[(x - x0) % len(depths)]
			for y in range(y1 - lost, y1):
				clear(img, x, y)
			if lost < y1 - y0:
				mul(img, x, y1 - lost - 1, 0.7)
		for hx, hy in holes:
			if x0 + hx < x1 and y0 + hy < y1:
				clear(img, x0 + hx, y0 + hy)
				clear(img, x0 + hx, y0 + hy + 1)
	return fx


def opening(left, right, top, bottom):
	"""A hood's front: cloth round an opening for the face."""
	def fx(img, rect):
		x0, y0, x1, y1 = rect
		for y in range(y0 + top, y1 - bottom):
			for x in range(x0 + left, x1 - right):
				clear(img, x, y)
		for x in range(x0 + left - 1, x1 - right + 1):
			mul(img, x, y0 + top - 1, 0.75)
		for y in range(y0 + top, y1 - bottom):
			mul(img, x0 + left - 1, y, 0.75)
			mul(img, x1 - right, y, 0.75)
	return fx


def torn_opening(left, right, top, bottom):
	def fx(img, rect):
		opening(left, right, top, bottom)(img, rect)
		x0, y0, x1, y1 = rect
		for i, x in enumerate(range(x0, x1)):
			for y in range(y1 - (0, 2, 1, 0, 3, 1)[i % 6], y1):
				clear(img, x, y)
	return fx


def twist(img, rect):
	"""The torse: two cloths wound together, light and dark by turns on the slant."""
	x0, y0, x1, y1 = rect
	for y in range(y0, y1):
		for x in range(x0, x1):
			if (x - x0 + y - y0) // 2 % 2:
				mul(img, x, y, 0.55)


def feathers(img, rect):
	"""A wing's face: rows of feather tips."""
	x0, y0, x1, y1 = rect
	for y in range(y0, y1):
		for x in range(x0, x1):
			if (y - y0) % 3 == 2:
				mul(img, x, y, 0.72)
			elif (x - x0 + (y - y0) // 3) % 2:
				mul(img, x, y, 0.9)


def on_sides(fx):
	return {s: fx for s in SIDES}


P = on_sides(plate)
P_ALL = {**P, "up": plate}


def both(b, bone, frm, to, mat, **kw):
	"""A cube on the wearer's right (as given, at +x) and its twin on the left; {bone} has "{}" where Right/Left goes."""
	for side, s in (("Right", 1), ("Left", -1)):
		x0, x1 = sorted((frm[0] * s, to[0] * s))
		k = dict(kw)
		if "rotation" in k:
			rx, ry, rz = k["rotation"]
			k["rotation"] = (rx, ry * s, rz * s)
		if "origin" in k:
			ox, oy, oz = k["origin"]
			k["origin"] = (ox * s, oy, oz)
		b.cube(bone.format(side), (x0, frm[1], frm[2]), (x1, to[1], to[2]), mat, **k)


def bones(b, *parts):
	"""The wearer's bones the piece hangs on: "head", "body", "arms", "legs", "boots"."""
	if "head" in parts:
		b.bone("bipedHead", None, (0, 24, 0))
		b.bone("armorHead", "bipedHead", (0, 24, 0))
	if "body" in parts:
		b.bone("bipedBody", None, (0, 24, 0))
		b.bone("armorBody", "bipedBody", (0, 24, 0))
	for side, s in (("Right", 1), ("Left", -1)):
		if "arms" in parts:
			b.bone(f"biped{side}Arm", None, (s * 5, 22, 0))
			b.bone(f"armor{side}Arm", f"biped{side}Arm", (s * 5, 22, 0))
		if "legs" in parts or "boots" in parts:
			b.bone(f"biped{side}Leg", None, (s * 1.9, 12, 0))
		if "legs" in parts:
			b.bone(f"armor{side}Leg", f"biped{side}Leg", (s * 1.9, 12, 0))
		if "boots" in parts:
			b.bone(f"armor{side}Boot", f"biped{side}Leg", (s * 1.9, 12, 0))


def visor(b, y=29.0):
	"""The bone a helm's visor turns on, at the temples."""
	b.bone("visor", "armorHead", (0, y, 0))


# ---- the helms -------------------------------------------------------------------------------------------------------

def frogmouth(b):
	"""The jousting helm: a tall smooth drum bolted to the breast, its lower front thrust out like a frog's mouth so the
	only sight is the gap under the brow."""
	bones(b, "head")
	h = "armorHead"
	b.cube(h, (-4.5, 24, -4.5), (4.5, 32, 4.5), "plate", faces_paint=P)
	b.cube(h, (-4, 32, -4), (4, 33, 4), "plate", faces_paint=P_ALL)
	# the sight: dark behind the gap
	b.cube(h, (-4.3, 28.8, -4.8), (4.3, 29.9, -4.4), "dark")
	# the brow over it, and the mouth below, thrust forward
	b.cube(h, (-4.6, 29.8, -5.6), (4.6, 32, -4.5), "plate", faces_paint=P_ALL)
	b.cube(h, (-4.7, 23, -6.4), (4.7, 28.9, -4.5), "plate", faces_paint={**P_ALL, "north": lames(3)})
	b.cube(h, (-3.4, 23.6, -7.4), (3.4, 28, -6.4), "plate", faces_paint=P_ALL)
	# the plate down onto the breast and back, and its bolt
	b.cube(h, (-5, 22, -5), (5, 24, 5), "plate", faces_paint=P)
	b.cube(h, (-1, 22.2, -7.6), (1, 23.6, -6.6), "rivet")


def houndskull(b):
	"""The houndskull bascinet: a skull drawn up to a point at the back, a snouted visor, a mail aventail."""
	bones(b, "head")
	h = "armorHead"
	b.cube(h, (-4.5, 25, -4.5), (4.5, 31.5, 4.5), "plate", faces_paint=P)
	b.cube(h, (-3.5, 31.5, -3), (3.5, 33.5, 4), "plate", faces_paint=P_ALL)
	b.cube(h, (-2.2, 33.5, -1), (2.2, 35, 3.6), "plate", faces_paint=P_ALL)
	b.cube(h, (-1, 35, 0.6), (1, 36.3, 3.2), "plate", faces_paint=P_ALL)
	b.cube(h, (-5, 21.8, -5), (5, 25.2, 5), "mail")
	visor(b)
	v = "visor"
	b.cube(v, (-4.6, 25.4, -5.4), (4.6, 30.8, -4.4), "plate", faces_paint={**P_ALL, "north": slit(1, 1, 2)})
	b.cube(v, (-3, 25.6, -7), (3, 29.4, -5.4), "plate", faces_paint={**P_ALL, "east": breaths, "west": breaths})
	b.cube(v, (-2, 26.2, -8.6), (2, 28.8, -7), "plate", faces_paint={**P_ALL, "east": breaths})
	b.cube(v, (-1, 26.8, -9.9), (1, 28.2, -8.6), "plate", faces_paint=P_ALL)
	both(b, "visor", (4.5, 28.4, -0.6), (5.2, 29.6, 0.6), "rivet")


def armet(b):
	"""The armet: close to the head, cheek plates shut under the chin, a short pointed visor, a low comb, and a rondel on
	its stalk behind."""
	bones(b, "head")
	h = "armorHead"
	b.cube(h, (-4.5, 24, -4.5), (4.5, 32.4, 4.5), "plate", faces_paint=P)
	b.cube(h, (-3.5, 32.4, -3.5), (3.5, 33.2, 3.5), "plate", faces_paint=P_ALL)
	b.cube(h, (-0.5, 32.4, -4.5), (0.5, 33.9, 4.5), "plate", faces_paint=P_ALL)
	b.cube(h, (-4.7, 23.4, -5.2), (4.7, 27.2, -4.3), "plate", faces_paint={**P_ALL, "north": keel})
	b.cube(h, (-4.9, 23.2, -4.9), (4.9, 24.2, 4.9), "plate", faces_paint=P)
	b.cube(h, (-0.5, 26, 4.5), (0.5, 27, 6), "rivet")
	b.cube(h, (-1.5, 25, 6), (1.5, 28, 6.6), "plate", faces_paint={"south": plate})
	visor(b)
	v = "visor"
	b.cube(v, (-4.7, 27.2, -5.6), (4.7, 31.2, -4.4), "plate", faces_paint={**P_ALL, "north": slit(1)})
	b.cube(v, (-2.4, 27.3, -6.6), (2.4, 30, -5.6), "plate", faces_paint={**P_ALL, "east": breaths, "west": breaths})
	b.cube(v, (-1.1, 27.6, -7.4), (1.1, 29.4, -6.6), "plate", faces_paint=P_ALL)
	both(b, "visor", (4.5, 28.4, -0.6), (5.2, 29.6, 0.6), "rivet")


def close_helm(b):
	"""The close helm: a tall comb, a visor ridged like bellows over a full bevor, and a gorget of lames round the neck."""
	bones(b, "head")
	h = "armorHead"
	b.cube(h, (-4.5, 24.4, -4.5), (4.5, 32.2, 4.5), "plate", faces_paint=P)
	b.cube(h, (-3.5, 32.2, -3.5), (3.5, 33, 3.5), "plate", faces_paint=P_ALL)
	b.cube(h, (-0.6, 32.2, -3.8), (0.6, 35.2, 4.2), "plate", faces_paint=P_ALL)
	b.cube(h, (-0.6, 32.8, -4.6), (0.6, 34.4, -3.8), "plate", faces_paint=P_ALL)
	b.cube(h, (-4.8, 23.8, -5.4), (4.8, 27.3, -4.3), "plate", faces_paint={**P_ALL, "north": keel})
	b.cube(h, (-3, 24, -6.1), (3, 26.6, -5.4), "plate", faces_paint=P_ALL)
	b.cube(h, (-5.3, 22, -5.3), (5.3, 23.3, 5.3), "plate", faces_paint=P_ALL)
	b.cube(h, (-4.9, 23.3, -4.9), (4.9, 24.4, 4.9), "plate", faces_paint=P_ALL)
	visor(b)
	v = "visor"
	b.cube(v, (-4.7, 27.3, -5.7), (4.7, 31.3, -4.4), "plate", faces_paint={**P_ALL, "north": bellows})
	both(b, "visor", (4.5, 28.4, -0.6), (5.2, 29.6, 0.6), "rivet")


def savoyard(b):
	"""The savoyard: the death's head. A round skull, a flat face with two round eyes, a nose hole and teeth, and a wide
	flange round the neck."""
	bones(b, "head")
	h = "armorHead"
	b.cube(h, (-4.5, 24.2, -4.5), (4.5, 32.4, 4.5), "plate", faces_paint=P)
	b.cube(h, (-3.5, 32.4, -3.5), (3.5, 33.3, 3.5), "plate", faces_paint=P_ALL)
	b.cube(h, (-0.5, 33.3, -2), (0.5, 34, 3), "plate", faces_paint=P_ALL)
	b.cube(h, (-6, 23.2, -6), (6, 24.2, 6), "plate", faces_paint=P_ALL)
	visor(b, 30.0)
	v = "visor"
	b.cube(v, (-4.4, 24.6, -5.4), (4.4, 30.8, -4.4), "plate", faces_paint={**P_ALL, "north": death})
	b.cube(v, (-4.6, 30.6, -5.8), (4.6, 31.7, -4.4), "plate", faces_paint=P_ALL)
	both(b, "visor", (4.5, 29.4, -0.6), (5.2, 30.6, 0.6), "rivet")


def sallet(b):
	"""The sallet with its bevor: a low skull with a long tail swept out behind, a half visor with one sight, and the
	bevor strapped on under it over chin and throat."""
	bones(b, "head")
	h = "armorHead"
	b.cube(h, (-4.6, 27, -4.6), (4.6, 32.2, 4.6), "plate", faces_paint=P)
	b.cube(h, (-3.6, 32.2, -3.6), (3.6, 33, 4), "plate", faces_paint=P_ALL)
	b.cube(h, (-0.5, 32.2, -4.6), (0.5, 33.5, 5), "plate", faces_paint=P_ALL)
	# the tail
	b.cube(h, (-4.3, 25.8, 4.6), (4.3, 28.4, 6.6), "plate", faces_paint=P_ALL)
	b.cube(h, (-3.5, 24.8, 6.6), (3.5, 26.8, 8.6), "plate", faces_paint=P_ALL)
	b.cube(h, (-2.4, 24.2, 8.6), (2.4, 25.6, 10), "plate", faces_paint=P_ALL)
	# the bevor
	b.cube(h, (-4.3, 22.8, -5.7), (4.3, 27.1, -4.3), "plate", faces_paint={**P_ALL, "north": keel})
	b.cube(h, (-3, 21.8, -5), (3, 23.2, -3.6), "plate", faces_paint=P_ALL)
	both(b, h, (4.3, 23, -4.6), (4.9, 26.6, -0.6), "plate", faces_paint=P_ALL)
	visor(b, 29.6)
	v = "visor"
	b.cube(v, (-4.8, 27.3, -5.7), (4.8, 30.8, -4.5), "plate", faces_paint={**P_ALL, "north": slit(1)})
	both(b, "visor", (4.6, 29, -0.6), (5.3, 30.2, 0.6), "rivet")


def kettle_hat(b):
	"""The kettle hat: a round bowl and a wide brim all the way round, a strap under the chin. Nothing over the face."""
	bones(b, "head")
	h = "armorHead"
	b.cube(h, (-4.5, 28.4, -4.5), (4.5, 32, 4.5), "plate", faces_paint=P)
	b.cube(h, (-3.5, 32, -3.5), (3.5, 33, 3.5), "plate", faces_paint=P_ALL)
	b.cube(h, (-2, 33, -2), (2, 33.6, 2), "plate", faces_paint=P_ALL)
	b.cube(h, (-7.2, 27.4, -7.2), (7.2, 28.4, 7.2), "plate", faces_paint=P_ALL)
	b.cube(h, (-5.6, 28.4, -5.6), (5.6, 29, 5.6), "plate", faces_paint=P_ALL)
	both(b, h, (4.2, 24.4, -1), (4.7, 28.4, 1), "strap")


def morion(b):
	"""The morion: a high comb from front to back, and a brim drawn up to a point before and behind like a boat."""
	bones(b, "head")
	h = "armorHead"
	b.cube(h, (-4.5, 28, -4.5), (4.5, 32, 4.5), "plate", faces_paint=P)
	b.cube(h, (-3.5, 32, -4), (3.5, 33.2, 4), "plate", faces_paint=P_ALL)
	b.cube(h, (-0.6, 32, -5), (0.6, 35.6, 5), "plate", faces_paint=P_ALL)
	b.cube(h, (-0.6, 35.6, -3.4), (0.6, 36.7, 3.4), "plate", faces_paint=P_ALL)
	b.cube(h, (-6, 27.2, -3.2), (6, 28.2, 3.2), "plate", faces_paint=P_ALL)
	for s in (-1, 1):
		for (x, ya, za, zb) in ((5, 27.8, 3.2, 5.6), (3.4, 28.6, 5.6, 7.6), (1.6, 29.4, 7.6, 9.2)):
			z0, z1 = sorted((s * za, s * zb))
			b.cube(h, (-x, ya, z0), (x, ya + 1, z1), "plate", faces_paint=P_ALL)
	both(b, h, (4.3, 24.4, -2), (4.8, 28, 2), "plate", faces_paint=P)


# ---- the body and limbs ----------------------------------------------------------------------------------------------

def cuirass(b):
	"""The cuirass: breast and back, the breast swelling over a pointed plackart, a fauld of two lames at the waist and
	a tasset hung before each thigh."""
	bones(b, "body")
	o = "armorBody"
	b.cube(o, (-4.4, 14.8, -2.7), (4.4, 23.6, 2.7), "plate", faces_paint={**P, "south": lames(4, 5)})
	b.cube(o, (-3.7, 17.4, -3.5), (3.7, 22.8, -2.7), "plate", faces_paint={**P_ALL, "north": keel})
	b.cube(o, (-4.2, 14.2, -3.2), (4.2, 17.8, -2.6), "plate", faces_paint={**P_ALL, "north": keel})
	b.cube(o, (-4.3, 13, -2.6), (4.3, 14.8, 2.6), "plate", faces_paint=P)
	b.cube(o, (-4.7, 11.4, -3), (4.7, 13.2, 3), "plate", faces_paint=P)
	b.cube(o, (-5, 9.8, -3.2), (5, 11.6, 3.2), "plate", faces_paint=P)
	both(b, o, (0.4, 6.4, -3.5), (4.4, 10, -2.8), "plate", faces_paint={**P, "north": lames(2)})
	both(b, o, (2.2, 23.6, -1.6), (4.2, 24.4, 1.6), "strap")


def spaulders(b):
	"""Shoulder and elbow plates: a spaulder of three lames, a raised guard for the neck standing on it, and a round
	disc at the elbow."""
	bones(b, "arms")
	a = "armor{}Arm"
	lame = b.lame
	both(b, a, (3.6, 22.2, -3), (9, 25, 3), b.mat, faces_paint=lame)
	both(b, a, (4, 20.4, -2.8), (9.3, 22.2, 2.8), b.mat, faces_paint=lame)
	both(b, a, (4.4, 19, -2.6), (9.1, 20.4, 2.6), b.mat, faces_paint=lame)
	# the neck guard: upright on the spaulder, leaning out a little
	both(b, a, (3.9, 25, -2.6), (4.8, 27.8, 2.6), "plate", faces_paint=P_ALL, rotation=(0, 0, -10), origin=(4.4, 25, 0))
	# the elbow disc, round as cubes go, and its boss
	both(b, a, (8.6, 15.4, -1.2), (9.3, 18.6, 1.2), "plate", faces_paint={"east": plate, "west": plate})
	both(b, a, (8.6, 16, -1.9), (9.3, 18, 1.9), "plate", faces_paint={"east": plate, "west": plate})
	both(b, a, (9.3, 16.5, -0.5), (9.8, 17.5, 0.5), "rivet")


def arm_harness(b):
	"""Arm harness: the upper cannon, the couter over the elbow with its wing, and the lower cannon to the wrist."""
	bones(b, "arms")
	a = "armor{}Arm"
	both(b, a, (3.7, 18.8, -2.3), (8.3, 22.2, 2.3), "plate", faces_paint=P)
	both(b, a, (3.5, 16.6, -2.6), (8.5, 19, 2.6), "plate", faces_paint=P_ALL)
	both(b, a, (5, 16.2, 2.6), (7, 19.4, 3.2), "plate", faces_paint=P_ALL)
	both(b, a, (3.7, 13.6, -2.3), (8.3, 16.8, 2.3), "plate", faces_paint=on_sides(lames(2)))


def gauntlets(b):
	"""Gauntlets: a flared cuff, the back of the hand, and a ridge over the knuckles."""
	bones(b, "arms")
	a = "armor{}Arm"
	both(b, a, (3.4, 14, -2.6), (8.6, 15.8, 2.6), "plate", faces_paint=P_ALL)
	both(b, a, (3.6, 11.8, -2.4), (8.4, 14, 2.4), "plate", faces_paint=on_sides(lames(1)))
	both(b, a, (8.4, 12, -2), (8.9, 13, 2), "rivet")


def cuisses(b):
	"""Cuisses: a plate round each thigh with a ridge down it, and the poleyn over the knee with its side wing."""
	bones(b, "legs")
	leg = "armor{}Leg"
	both(b, leg, (-0.4, 6.8, -2.5), (4.4, 11.6, 2.5), "plate", faces_paint=P)
	both(b, leg, (0.6, 7.2, -2.9), (3.4, 11.2, -2.5), "plate", faces_paint={**P_ALL, "north": keel})
	both(b, leg, (-0.3, 4.8, -3), (4.3, 7.1, -1.8), "plate", faces_paint=P_ALL)
	both(b, leg, (4.3, 4.9, -1.6), (5, 6.9, 1.6), "plate", faces_paint=P_ALL)


def greaves(b):
	"""Greaves: a plate round each shin, a ridge down the front."""
	bones(b, "legs")
	leg = "armor{}Leg"
	both(b, leg, (-0.4, 1.6, -2.4), (4.4, 5.2, 2.4), "plate", faces_paint=P)
	both(b, leg, (1.4, 1.8, -2.8), (2.6, 5, -2.4), "plate", faces_paint=P_ALL)


def sabatons(b):
	"""Sabatons: lames over the foot drawn out to a point at the toe, and a plate round the ankle."""
	bones(b, "boots")
	boot = "armor{}Boot"
	both(b, boot, (-0.3, 0, -3.2), (4.3, 2.2, 2.4), "plate", faces_paint={**P, "up": lames(2)})
	both(b, boot, (0.5, 0, -4.4), (3.5, 1.6, -3.2), "plate", faces_paint=P_ALL)
	both(b, boot, (1.3, 0, -5.3), (2.7, 1.1, -4.4), "plate", faces_paint=P_ALL)
	both(b, boot, (-0.4, 2.2, -2.5), (4.4, 3.6, 2.5), "plate", faces_paint=P)


# ---- cloth ---------------------------------------------------------------------------------------------------------

def giornea(b):
	"""The giornea: a pleated coat open down both sides, hung from the shoulders before and behind over the armour and
	belted at the waist."""
	bones(b, "body")
	o = "armorBody"
	fold = b.fold
	b.cube(o, (-4.8, 6.5, -4.3), (4.8, 24.2, -3.7), "cloth", faces_paint={"north": fold, "south": fold})
	b.cube(o, (-4.8, 6.5, 3.3), (4.8, 24.2, 3.9), "cloth", faces_paint={"north": fold, "south": fold})
	both(b, o, (1.7, 24, -4.3), (4.8, 24.7, 3.9), "cloth")
	b.cube(o, (-5, 12.6, -4.6), (5, 13.8, 4.2), "cloth_dark")


def cloak(b):
	"""The cloak: a collar round the shoulders clasped at the throat, and the cloth falling from it to the calves."""
	bones(b, "body")
	o = "armorBody"
	fold = b.fold
	b.cube(o, (-5.2, 22.6, -2.9), (5.2, 24.6, 3.5), "cloth")
	b.cube(o, (-1, 22.8, -3.3), (1, 24.2, -2.9), "gilt")
	both(b, o, (4.6, 15, -1.4), (5.7, 23, 3.4), "cloth", faces_paint={"east": fold, "west": fold})
	b.bone("cape", o, (0, 24, 3.2), (-7, 0, 0))
	b.cube("cape", (-5.6, 3, 3.2), (5.6, 24, 3.8), "cloth", faces_paint={"north": fold, "south": fold})


def hood(b):
	"""The hood: wide enough to go on over a helm, its point hanging down behind, and a short cape over the shoulders."""
	bones(b, "head", "body")
	h = "armorHead"
	face = b.face
	b.cube(h, (-5.4, 23.6, -5.3), (5.4, 33.8, 5.6), "cloth", faces_paint={"north": face, "east": pleats, "west": pleats, "south": pleats})
	b.cube(h, (-2.6, 30.8, 5.6), (2.6, 33.2, 7.2), "cloth")
	b.cube(h, (-1.1, 26.4, 6.2), (1.1, 31, 7.4), "cloth", faces_paint=on_sides(pleats))
	b.cube("armorBody", (-5.8, 20.6, -3.5), (5.8, 24.4, 3.7), "cloth", faces_paint=on_sides(b.fold))


# ---- the torse and the crests (they stand on a helm's crown at y=33; the game lifts them for a taller helm) ----------

def torse(b):
	"""The torse: a wreath of two cloths twisted together, round the crown of the helm."""
	bones(b, "head")
	h = "armorHead"
	T = on_sides(twist)
	b.cube(h, (-2.6, 33, -2.6), (2.6, 34.2, -1.5), "cloth", faces_paint={**T, "up": twist})
	b.cube(h, (-2.6, 33, 1.5), (2.6, 34.2, 2.6), "cloth", faces_paint={**T, "up": twist})
	b.cube(h, (-2.6, 33, -1.5), (-1.5, 34.2, 1.5), "cloth", faces_paint={**T, "up": twist})
	b.cube(h, (1.5, 33, -1.5), (2.6, 34.2, 1.5), "cloth", faces_paint={**T, "up": twist})


def wing(b, x, z, spans, mat, lean, origin):
	"""A wing each side, standing {lean} degrees out from the helm: a thin slat for each (bottom, top) of {spans}, front
	to back from {z}, so its outline steps down to the trailing tips."""
	for i, (y0, y1) in enumerate(spans):
		both(b, "armorHead", (x, y0, z + i), (x + 0.4, y1, z + i + 1), mat, faces_paint={"east": feathers, "west": feathers},
			rotation=(0, 0, -lean), origin=origin)


def crest_dragon(b):
	bones(b, "head")
	h = "armorHead"
	b.cube(h, (-1, 34.2, -1), (1, 36.6, 2), "dragon", faces_paint={"north": lambda i, q: belly(i, q)})
	b.cube(h, (-0.7, 36, -2.2), (0.7, 38.6, -0.8), "dragon")
	b.cube(h, (-0.9, 38, -4), (0.9, 39.4, -1.6), "dragon", faces_paint={"east": eye, "west": eye})
	both(b, h, (0.3, 39.4, -2.2), (0.8, 40.4, -1.7), "horn")
	wing(b, 1, -0.4, ((35.6, 40.2), (36.8, 40), (37.6, 39.4), (38.2, 38.8)), "dragon_wing", 24, (1, 35.6, 0))
	b.cube(h, (-0.5, 34.2, 2), (0.5, 35.2, 4), "dragon")
	b.cube(h, (-0.4, 35.2, 3.3), (0.4, 36.6, 4), "dragon")


def crest_lion(b):
	bones(b, "head")
	h = "armorHead"
	b.cube(h, (-1.3, 34.2, -1.2), (1.3, 37, 2), "lion")
	b.cube(h, (-1.9, 36.2, -3), (1.9, 39.6, -0.6), "mane")
	b.cube(h, (-1.5, 36.4, -3.6), (1.5, 39, -3), "lion", faces_paint={"north": lion_face})
	both(b, h, (1, 39.4, -2.2), (1.7, 40.2, -1.4), "mane")
	both(b, h, (0.4, 34.2, -2.2), (1.2, 36.2, -1.2), "lion")
	b.cube(h, (-0.3, 35, 2), (0.3, 38.6, 2.6), "lion")
	b.cube(h, (-0.6, 38.6, 1.8), (0.6, 39.8, 3), "mane")


def crest_stag(b):
	bones(b, "head")
	h = "armorHead"
	b.cube(h, (-1, 34.2, -0.8), (1, 37.4, 1.2), "stag")
	b.cube(h, (-0.9, 36.6, -3.2), (0.9, 38.2, -0.6), "stag", faces_paint={"north": muzzle, "east": eye, "west": eye})
	both(b, h, (0.9, 37.8, -0.2), (1.7, 38.4, 0.4), "stag")
	# the antlers: a beam each side, and its tines
	both(b, h, (0.5, 38.2, -0.3), (1.1, 42, 0.3), "bone", rotation=(0, 0, -18), origin=(0.8, 38.2, 0))
	both(b, h, (1.6, 39.6, -1.8), (2.2, 40.2, 0), "bone")
	both(b, h, (2, 40.8, -0.3), (2.6, 42.6, 0.3), "bone")
	both(b, h, (1.2, 41.6, 0.2), (1.8, 42.2, 1.6), "bone")


def crest_boar(b):
	bones(b, "head")
	h = "armorHead"
	b.cube(h, (-1.8, 34.2, -1.5), (1.8, 37.4, 2.6), "boar", faces_paint={"east": eye, "west": eye})
	b.cube(h, (-1.2, 34.4, -3.6), (1.2, 36.4, -1.5), "boar")
	b.cube(h, (-0.9, 34.6, -3.9), (0.9, 36.2, -3.6), "pink")
	both(b, h, (1.1, 35.2, -3.3), (1.6, 37.2, -2.8), "white")
	b.cube(h, (-0.4, 37.4, -1.2), (0.4, 38.5, 2.6), "boar", faces_paint={"east": bands, "west": bands})
	both(b, h, (1, 37.2, 0.6), (1.8, 38.3, 1.4), "boar")


def crest_eagle(b):
	bones(b, "head")
	h = "armorHead"
	b.cube(h, (-1, 34.2, -0.6), (1, 38, 0.6), "eagle")
	b.cube(h, (-0.8, 38, -0.9), (0.8, 39.6, 0.7), "eagle", faces_paint={"east": eye, "west": eye})
	b.cube(h, (-0.3, 38.5, -1.9), (0.3, 39.1, -0.9), "gilt")
	b.cube(h, (-0.3, 38, -1.9), (0.3, 38.5, -1.5), "gilt")
	# displayed: the wings spread out to either side, the long feathers hanging
	both(b, h, (1, 35, -0.3), (4.4, 39.8, 0.3), "eagle", faces_paint={"north": bands, "south": bands})
	both(b, h, (2.4, 33.4, -0.3), (4.8, 35, 0.3), "eagle", faces_paint={"north": bands, "south": bands})
	both(b, h, (0.2, 33.6, -0.5), (0.8, 34.2, 0.5), "gilt")


def crest_pegasus(b):
	bones(b, "head")
	h = "armorHead"
	b.cube(h, (-1.1, 34.2, -0.8), (1.1, 37.2, 1.8), "white")
	b.cube(h, (-0.8, 36.6, -1.9), (0.8, 39.6, -0.3), "white")
	b.cube(h, (-0.7, 38.6, -3.6), (0.7, 40, -1.4), "white", faces_paint={"east": eye, "west": eye})
	both(b, h, (0.2, 40, -1.8), (0.6, 40.7, -1.4), "white")
	b.cube(h, (-0.25, 36.8, -0.3), (0.25, 40.3, 0.3), "cloth_dark")
	both(b, h, (0.4, 35.2, -2.6), (1, 36, -0.8), "white")
	wing(b, 1.1, 0, ((36, 41.4), (36.8, 41), (37.8, 40.2), (38.8, 39.4)), "feather", 20, (1.1, 36, 0))


def crest_unicorn(b):
	bones(b, "head")
	h = "armorHead"
	b.cube(h, (-1, 34.2, -0.6), (1, 38.2, 1.4), "white")
	b.cube(h, (-0.8, 37.4, -3.2), (0.8, 39, -0.6), "white", faces_paint={"east": eye, "west": eye})
	b.cube(h, (-0.25, 39, -2.4), (0.25, 42.6, -1.9), "gilt", faces_paint=on_sides(bands), rotation=(-22, 0, 0), origin=(0, 39, -2.1))
	b.cube(h, (-0.3, 35, 1.4), (0.3, 39.8, 2), "cloth_dark")
	b.cube(h, (-0.2, 36.4, -2.9), (0.2, 37.4, -2.5), "cloth_dark")
	both(b, h, (0.3, 39, -1), (0.7, 39.8, -0.6), "white")


def crest_snake(b):
	bones(b, "head")
	h = "armorHead"
	S = on_sides(bands)
	b.cube(h, (-2.3, 34.2, -2.3), (2.3, 35.3, 2.3), "snake", faces_paint=S)
	b.cube(h, (-1.6, 35.3, -1.6), (1.6, 36.4, 1.6), "snake", faces_paint=S)
	b.cube(h, (-0.6, 36.4, -0.9), (0.6, 39.6, 0.3), "snake", faces_paint={"north": lambda i, q: belly(i, q)})
	b.cube(h, (-0.9, 39.2, -2.7), (0.9, 40.4, -0.3), "snake", faces_paint={"east": eye, "west": eye})
	b.cube(h, (-0.15, 39.4, -3.7), (0.15, 39.7, -2.7), "red")


def crest_horns(b):
	"""A pair of horns, rising from the sides of the helm and curving in at their tips."""
	bones(b, "head")
	h = "armorHead"
	S = on_sides(bands)
	both(b, h, (3.6, 31, -0.9), (5.8, 32.8, 0.9), "horn", faces_paint=S)
	both(b, h, (4.6, 32.8, -0.8), (6.2, 36, 0.8), "horn", faces_paint=S)
	both(b, h, (4.3, 36, -0.7), (5.6, 38.8, 0.7), "horn", faces_paint=S)
	both(b, h, (3.4, 38.6, -0.5), (4.5, 40.6, 0.5), "horn", faces_paint=S)


def crest_wings(b):
	"""A pair of wings, one standing up from each side of the helm."""
	bones(b, "head")
	h = "armorHead"
	both(b, h, (3.6, 31, -0.6), (4.8, 33, 1.6), "feather")
	wing(b, 4.3, -0.4, ((32, 41), (33, 40.8), (34.4, 40.2), (36, 39.4), (37.6, 38.6)), "feather", 12, (4.5, 32, 0))


def eye(img, rect):
	x0, y0, x1, y1 = rect
	put(img, (x0 + x1) // 2, y0, (20, 16, 14))


def lion_face(img, rect):
	x0, y0, x1, y1 = rect
	put(img, x0, y0, (20, 16, 14))
	put(img, x1 - 1, y0, (20, 16, 14))
	put(img, (x0 + x1) // 2, y1 - 2, (60, 36, 20))


def muzzle(img, rect):
	x0, y0, x1, y1 = rect
	for x in range(x0, x1):
		put(img, x, y1 - 1, (30, 22, 18))


def belly(img, rect):
	x0, y0, x1, y1 = rect
	for y in range(y0, y1):
		for x in range(x0, x1):
			put(img, x, y, shade(FIXED["belly"], 0.9 if (y - y0) % 2 else 1.05))


# ---- what there is ---------------------------------------------------------------------------------------------------

# piece (item id) -> (build, sheet size, the bones its icon shows); the plate pieces, painted in each finish
PLATE = {
	"frogmouth_helm": (frogmouth, 64), "houndskull_bascinet": (houndskull, 64), "armet": (armet, 64), "close_helm": (close_helm, 64),
	"savoyard_helm": (savoyard, 64), "sallet": (sallet, 64), "kettle_hat": (kettle_hat, 64), "morion": (morion, 64),
	"plate_cuirass": (cuirass, 64), "plate_spaulders": (spaulders, 64), "plate_arm_harness": (arm_harness, 64),
	"plate_gauntlets": (gauntlets, 64), "plate_cuisses": (cuisses, 64), "plate_greaves": (greaves, 64), "plate_sabatons": (sabatons, 64),
}
CRESTS = {"crest_dragon": crest_dragon, "crest_lion": crest_lion, "crest_stag": crest_stag, "crest_boar": crest_boar,
	"crest_eagle": crest_eagle, "crest_pegasus": crest_pegasus, "crest_unicorn": crest_unicorn, "crest_snake": crest_snake,
	"crest_horns": crest_horns, "crest_wings": crest_wings}
TORN_STRIPS = (0, 9, 4, 13, 6, 1, 11, 3, 8, 15, 2, 7)
NAMES = {
	"frogmouth_helm": "Frogmouth Helm", "houndskull_bascinet": "Houndskull Bascinet", "armet": "Armet", "close_helm": "Close Helm",
	"savoyard_helm": "Savoyard Helm", "sallet": "Sallet and Bevor", "kettle_hat": "Kettle Hat", "morion": "Morion",
	"plate_cuirass": "Plate Cuirass", "plate_spaulders": "Plate Spaulders", "mail_spaulders": "Mail Spaulders",
	"brigandine_spaulders": "Brigandine Spaulders", "plate_arm_harness": "Arm Harness", "plate_gauntlets": "Plate Gauntlets",
	"plate_cuisses": "Cuisses", "plate_greaves": "Greaves", "plate_sabatons": "Sabatons", "arming_doublet": "Arming Doublet",
	"mail_haubergeon": "Mail Haubergeon", "giornea": "Giornea", "cloak": "Cloak", "torn_cloak": "Torn Cloak", "hood": "Hood",
	"torn_hood": "Torn Hood", "torse": "Torse", "crest_dragon": "Dragon Crest", "crest_lion": "Lion Crest", "crest_stag": "Stag Crest",
	"crest_boar": "Boar Crest", "crest_eagle": "Eagle Crest", "crest_pegasus": "Pegasus Crest", "crest_unicorn": "Unicorn Crest",
	"crest_snake": "Serpent Crest", "crest_horns": "Crest of Horns", "crest_wings": "Crest of Wings",
}
# the cloth the game tints with the piece's dye, and the colour its icon and an undyed one show
DYED = {"arming_doublet": 0xA06540, "giornea": 0x8E2B2B, "cloak": 0x4A5568, "torn_cloak": 0x5A5246, "hood": 0x4A5568, "torn_hood": 0x5A5246,
	"torse": 0xB02E26}


def make(build, size, finish="", seed=7, figure=False, **marks):
	"""One piece built in one finish's palette: the Builder, its cubes painted."""
	saved = dict(kit.PALETTE)
	kit.PALETTE.update(FIXED)
	kit.PALETTE.update(FINISHES[finish])
	b = Builder(max(size, 64) if figure else size, size + 96 if figure else size, seed)
	b.mat, b.lame, b.fold, b.face = "plate", {**P_ALL, "east": lames(2), "west": lames(2)}, pleats, opening(2, 2, 3, 2)
	for k, v in marks.items():
		setattr(b, k, v)
	try:
		build(b)
	except SystemExit as full:
		raise SystemExit("%s: %s" % (build.__name__, full))
	if figure:
		# (preview only) a grey figure under the piece
		b.bone("figure", None, Z)
		for frm, to in (((-4, 24, -4), (4, 32, 4)), ((-4, 12, -2), (4, 24, 2)), ((4, 12, -2), (8, 24, 2)), ((-8, 12, -2), (-4, 24, 2)),
			((0, 0, -2), (4, 12, 2)), ((-4, 0, -2), (0, 12, 2))):
			b.cube("figure", frm, to, "figure")
	model = b.project("piece")
	kit.PALETTE.clear()
	kit.PALETTE.update(saved)
	return b, model


def bake(model, assets, name, keep=None):
	path = os.path.join(tempfile.mkdtemp(), name + ".bbmodel")
	with open(path, "w", encoding="utf-8", newline="\n") as f:
		json.dump(model, f)
	cmd = [sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_bake.py"), path, assets, name]
	if keep:
		cmd += ["--keep", keep]
	subprocess.run(cmd, check=True, capture_output=True)
	return path


def tinted(img, rgb):
	"""{img} as the game draws it under a dye: every colour times the dye's."""
	out = img.copy()
	px = out.load()
	for y in range(out.height):
		for x in range(out.width):
			c = px[x, y]
			px[x, y] = (c[0] * (rgb >> 16 & 255) // 255, c[1] * (rgb >> 8 & 255) // 255, c[2] * (rgb & 255) // 255, c[3])
	return out


ARMOUR_BONES = "armorHead,armorBody,armorRightArm,armorLeftArm,armorRightLeg,armorLeftLeg,armorRightBoot,armorLeftBoot"


def icon(model, out):
	"""A piece's icon: its model drawn three-quarter and shrunk (gen_reverent.icon)."""
	r.SOURCE = bake(model, tempfile.mkdtemp(), "piece")
	os.makedirs(os.path.dirname(out), exist_ok=True)
	cwd = os.getcwd()
	os.chdir(ROOT)
	try:
		r.icon(ARMOUR_BONES, out)
	finally:
		os.chdir(cwd)


def item(name, model):
	r.write(os.path.join(ASSETS, "items", name + ".json"), {"model": model})


def flat(name, layers=None):
	"""An item model of flat layers (the icon itself unless {layers} names more)."""
	textures = {"layer%d" % i: "jaymoskingdoms:item/" + t for i, t in enumerate(layers or [name])}
	r.write(os.path.join(ASSETS, "models", "item", name + ".json"), {"parent": "minecraft:item/generated", "textures": textures})
	return {"type": "minecraft:model", "model": "jaymoskingdoms:item/" + name}


def dye_recipe(name):
	r.write(os.path.join(DATA, "jaymoskingdoms", "recipe", name + "_dyed.json"), {"type": "minecraft:crafting_dye", "dye": "#minecraft:dyes",
		"group": "dyed_armor", "result": {"id": "jaymoskingdoms:" + name}, "target": "jaymoskingdoms:" + name})


def entity_texture(img, name):
	path = os.path.join(ASSETS, "textures", "entity", "bb", name + ".png")
	img.save(path)


def shirt(out, paint):
	"""An icon for a shirt worn under armour (it has no model of its own): body and sleeves, painted by {paint}(x, y)."""
	img = Image.new("RGBA", (16, 16))
	outline = (58, 40, 24)
	body = [(x, y) for y in range(3, 14) for x in range(5, 11)] + [(x, y) for y in range(3, 9) for x in (2, 3, 4, 11, 12, 13)]
	body = [p for p in body if p not in ((7, 3), (8, 3))]
	for x, y in body:
		put(img, x, y, paint(x, y))
	for x, y in body:
		for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
			if (x + dx, y + dy) not in body and 0 <= x + dx < 16 and 0 <= y + dy < 16:
				put(img, x + dx, y + dy, outline)
	os.makedirs(os.path.dirname(out), exist_ok=True)
	img.save(out)


def haubergeon_sheet():
	"""The mail haubergeon on the under-armour layer's 64x32 sheet: mail over the body and down the arms to the elbow."""
	img = Image.new("RGBA", (64, 32))
	mail = FIXED["mail"]
	for y in range(16, 32):
		for x in range(16, 56):
			arm = x >= 40
			if arm and y >= 27 or y < 20 and not (20 <= x < 36 or 44 <= x < 52):
				continue
			k = 1.2 if (x + y) % 2 == 0 else 0.82
			if y == (26 if arm else 31):
				k *= 0.7
			put(img, x, y, shade(mail, k))
	return img


def main(argv):
	preview = argv[argv.index("--preview") + 1] if "--preview" in argv else None
	lang = {}
	sheets = {}

	def piece(name, build, size, finishes=("",), dye=None, **marks):
		"""Bakes {name}'s model, saves a sheet for each finish and makes its icons; returns the icon names by finish."""
		icons = {}
		for finish in finishes:
			b, model = make(build, size, finish, **marks)
			tex = "heraldry_" + name + ("_" + finish if finish else "")
			if finish == finishes[0]:
				bake(model, ASSETS, "heraldry_" + name)
			entity_texture(b.img, tex)
			shown = model
			if dye is not None:
				b2, shown = make(build, size, finish, **marks)
				painted = tinted(b2.img, dye)
				b2.img = painted
				shown = b2.project("piece")
			icons[finish] = name + ("_" + finish if finish else "")
			icon(shown, os.path.join(ASSETS, "textures", "item", icons[finish] + ".png"))
			if preview:
				sheets[tex] = (build, size, finish, marks, dye)
		return icons

	# the plate pieces: one item, an icon for each finish, picked by the piece's finish
	for name, (build, size) in PLATE.items():
		icons = piece(name, build, size, tuple(FINISHES))
		item(name, {"type": "minecraft:select", "property": "minecraft:component", "component": "jaymoskingdoms:finish",
			"cases": [{"when": f, "model": flat(icons[f])} for f in FINISHES if f], "fallback": flat(icons[""])})
	# the shoulder plates in mail and in brigandine: the same cubes, their lames another make
	mail_lame = {s: None for s in ()}
	piece("mail_spaulders", spaulders, 64, mat="mail", lame=mail_lame)
	item("mail_spaulders", flat("mail_spaulders"))
	piece("brigandine_spaulders", spaulders, 64, mat="brig", lame={**on_sides(studs), "up": studs})
	item("brigandine_spaulders", flat("brigandine_spaulders"))
	for extra in ("mail_spaulders", "brigandine_spaulders"):
		os.remove(os.path.join(ASSETS, "bbmodels", "heraldry_" + extra + ".json"))
	# cloth: pale sheets the game tints; the torn ones are the same cubes with the cloth torn away
	tint = lambda name: {"type": "minecraft:model", "model": "jaymoskingdoms:item/" + name,  # noqa: E731
		"tints": [{"type": "minecraft:dye", "default": DYED[name] - (1 << 24)}]}
	for name, build, marks in (("giornea", giornea, {}), ("cloak", cloak, {}), ("torn_cloak", cloak, {"fold": ragged(TORN_STRIPS, ((2, 5), (7, 9)))}),
		("hood", hood, {}), ("torn_hood", hood, {"fold": ragged((0, 2, 1, 3, 0, 1)), "face": torn_opening(2, 2, 3, 2)})):
		piece(name, build, 128, dye=None, **marks)
		flat(name)
		item(name, tint(name))
		dye_recipe(name)
	for extra in ("torn_cloak", "torn_hood"):
		os.remove(os.path.join(ASSETS, "bbmodels", "heraldry_" + extra + ".json"))
	# the torse (tinted), and the crests that stand on it: the icon is the torse (tinted) under the beast
	piece("torse", torse, 32)
	flat("torse")
	item("torse", tint("torse"))
	dye_recipe("torse")
	for name, build in CRESTS.items():
		piece(name, build, 64)
		flat(name, ["torse", name])
		item(name, {"type": "minecraft:model", "model": "jaymoskingdoms:item/" + name, "tints": [{"type": "minecraft:dye", "default": DYED["torse"] - (1 << 24)}]})
		dye_recipe(name)
	# the under-armour: the haubergeon's sheet, and an icon each for it and the arming doublet (tinted by its dye)
	under = os.path.join(ASSETS, "textures", "entity", "under_armour", "haubergeon.png")
	haubergeon_sheet().save(under)
	shirt(os.path.join(ASSETS, "textures", "item", "mail_haubergeon.png"), lambda x, y: shade(FIXED["mail"], 1.2 if (x + y) % 2 == 0 else 0.82))
	flat("mail_haubergeon")
	item("mail_haubergeon", flat("mail_haubergeon"))
	shirt(os.path.join(ASSETS, "textures", "item", "arming_doublet.png"),
		lambda x, y: shade(FIXED["cloth"], 0.72 if x in (7, 8) or (x + 2 * y) % 5 == 0 else 1.0))
	flat("arming_doublet")
	item("arming_doublet", tint("arming_doublet"))
	dye_recipe("arming_doublet")
	for name, text in NAMES.items():
		lang["item.jaymoskingdoms." + name] = text
	r.write(os.path.join(ASSETS, "lang", "parts", "heraldry-armour-items.json"), lang)
	if preview:
		os.makedirs(preview, exist_ok=True)
		for tex, (build, size, finish, marks, dye) in sheets.items():
			scratch = tempfile.mkdtemp()
			b, model = make(build, size, finish, figure=True, **marks)
			bake(model, scratch, tex)
			subprocess.run([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_preview.py"), scratch, tex, os.path.join(preview, tex + ".png")],
				check=True, capture_output=True)
	print("heraldry armour:", len(NAMES), "pieces")


if __name__ == "__main__":
	main(sys.argv[1:])
