"""
The harness's hoods, hats, crests and neck pieces from Andy's Mordhau picks (Mordhau harness 2): our own cubes and paint
of the historical pieces each pick is based on (Mordhau's meshes, textures and names belong to Triternion and none of
them is used here). Built the way gen_heraldry_armour.py builds the rest of the harness, with its painters and helpers,
so only this file needs running for these pieces:

- hoods: the tight hood, the monk's cowl, the cleric's mantle, the headsman's hood; the arming cap and the linen coif
  (worn under a helm); the mail coif's cuts (lowered, square-cut, with a ventail, square-cut with a ventail);
- hats (HOOD, worn bare-headed: the game hides them under a helm): the bag hat, the archer's cap, the field hat (straw), the
  hunter's hat, the slashed bonnet, the fur hat, the turban and the veiled turban, the jester's hood, the victor's wreath;
- crests: the orle (a padded roll, worn in the torse's place), the helm crown (a gold circlet round the helm, no torse),
  antlers, and the mantling (cloth hung from the crown of the helm, under the torse);
- neck: the armet's wrapper, a plate in every finish.

A dyed piece is painted in three sheets on the same cubes: heraldry_<piece> (pale cloth, tinted with its dye),
heraldry_<piece>_fixed (feathers, bells, gilt: drawn as they are) and, for the bonnet and the jester's hood,
heraldry_<piece>_second (tinted with its second colour, jaymoskingdoms:second_colour, which the dye given when it is
made sets).

Run from the project root: python3 -I kingdoms/tools/gen_heraldry_hoods.py [--preview <dir>]   (needs Pillow)
"""
import json
import os
import random
import subprocess
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image  # noqa: E402

import gen_heraldry_armour as g  # noqa: E402
import gen_reverent as r  # noqa: E402
from gen_heraldry_armour import (P, P_ALL, both, bones, clear, lames, mul, on_sides, opening, pleats, quilting, rings,  # noqa: E402
	fur, twist, feathers, ragged, put)

ASSETS, DATA, ROOT = g.ASSETS, g.DATA, g.ROOT
# the paint these pieces add (the cloth they share is gen_heraldry_armour's pale cloth, which the game tints)
g.FIXED.update({"second": g.FIXED["cloth"], "black": (40, 38, 42), "straw": (214, 184, 112), "leaf": (78, 124, 56),
	"antler": (150, 116, 80), "jewel": (176, 30, 44), "felt": g.FIXED["cloth"]})
# the cubes of these materials take the piece's dye; "second" its second colour; anything else is drawn as it is
DYED_MATS, SECOND_MAT = ("cloth", "cloth_dark", "felt"), "second"


# ---- painters ---------------------------------------------------------------------------------------------------

def straw(img, rect):
	"""Plaited straw: a weave of light and dark by turns, in rows."""
	x0, y0, x1, y1 = rect
	for y in range(y0, y1):
		for x in range(x0, x1):
			mul(img, x, y, 1.14 if (x // 2 + y) % 2 else 0.84)


def leaves(img, rect):
	"""Laurel: leaves laid along the stem, pointing one way, the light side and the dark by turns."""
	x0, y0, x1, y1 = rect
	for y in range(y0, y1):
		for x in range(x0, x1):
			mul(img, x, y, (1.25, 1.0, 0.78)[(x - x0 + y - y0) % 3])


def eyeholes(img, rect):
	"""The headsman's hood: black cloth over the whole face, two eyeholes cut in it."""
	x0, y0, x1, y1 = rect
	w = x1 - x0
	for x in (x0 + w // 2 - 3, x0 + w // 2 - 2, x0 + w // 2 + 1, x0 + w // 2 + 2):
		clear(img, x, y0 + 4)
	for x in range(x0, x1):
		mul(img, x, y1 - 1, 0.7)


def round_opening(left, right, top, bottom):
	"""A face opening with its corners left in: a round-cut coif."""
	def fx(img, rect):
		rings(img, rect)
		opening(left, right, top, bottom)(img, rect)
		x0, y0, x1, y1 = rect
		for x, y in ((x0 + left, y0 + top), (x1 - right - 1, y0 + top), (x0 + left, y1 - bottom - 1), (x1 - right - 1, y1 - bottom - 1)):
			put(img, x, y, g.FIXED["mail"])
	return fx


def square_opening(left, right, top, bottom):
	def fx(img, rect):
		rings(img, rect)
		opening(left, right, top, bottom)(img, rect)
	return fx


def slashes(img, rect):
	"""Slashed cloth: short slits cut through it in staggered rows, the lining showing through (the slits are holes)."""
	pleats(img, rect)
	x0, y0, x1, y1 = rect
	for y in range(y0, y1):
		for x in range(x0, x1):
			if (x - x0 + 2 * ((y - y0) // 2)) % 4 == 1 and (y - y0) % 2 == 0 and y1 - y0 > 1:
				clear(img, x, y)


def wraps(img, rect):
	"""A turban's wound cloth: folds running round it on the slant."""
	x0, y0, x1, y1 = rect
	for y in range(y0, y1):
		for x in range(x0, x1):
			if (x - x0 - (y - y0)) % 3 == 0:
				mul(img, x, y, 0.8)


def stitched(img, rect):
	"""A padded roll: a seam along its middle and stitches across it."""
	x0, y0, x1, y1 = rect
	for x in range(x0, x1):
		mul(img, x, (y0 + y1) // 2, 0.78)
		if (x - x0) % 3 == 0:
			for y in range(y0, y1):
				mul(img, x, y, 0.9)


def dags(img, rect):
	"""The mantling's lower edge cut in leaf-shaped dags."""
	pleats(img, rect)
	x0, y0, x1, y1 = rect
	for x in range(x0, x1):
		for y in range(y1 - (0, 1, 2, 1)[(x - x0) % 4], y1):
			clear(img, x, y)


# ---- hoods ------------------------------------------------------------------------------------------------------

def tight_hood(b):
	"""The tight hood: cloth close round the head and under the chin, no point, a short cape over the shoulders."""
	bones(b, "head", "body")
	h = "armorHead"
	b.cube(h, (-4.6, 23.8, -4.6), (4.6, 32.7, 4.7), "cloth", faces_paint={"north": opening(2, 2, 2, 1), **{s: pleats for s in ("east", "west", "south")}})
	b.cube("armorBody", (-5.2, 21.4, -3.2), (5.2, 24.4, 3.4), "cloth", faces_paint=on_sides(pleats))


def monks_cowl(b):
	"""The monk's cowl: a deep hood with no point, its front standing forward round the face, and a cape to the chest."""
	bones(b, "head", "body")
	h = "armorHead"
	b.cube(h, (-5.6, 23.4, -5.2), (5.6, 34.2, 5.8), "cloth", faces_paint={"north": opening(2, 2, 2, 1), **{s: pleats for s in ("east", "west", "south")}})
	b.cube(h, (-5.6, 32.6, -6.6), (5.6, 34.2, -5.2), "cloth", faces_paint=on_sides(pleats))
	both(b, h, (4.4, 23.6, -6.6), (5.6, 32.6, -5.2), "cloth")
	b.cube("armorBody", (-6, 18.4, -3.6), (6, 24.4, 3.8), "cloth", faces_paint=on_sides(pleats))


def clerics_mantle(b):
	"""The cleric's mantle: a hood with a short point and a wide shoulder cape (the mozzetta), buttoned down the front."""
	bones(b, "head", "body")
	h = "armorHead"
	b.cube(h, (-5.3, 23.8, -5.2), (5.3, 33.6, 5.4), "cloth", faces_paint={"north": opening(2, 2, 3, 2), **{s: pleats for s in ("east", "west", "south")}})
	b.cube(h, (-1.6, 29.6, 5.4), (1.6, 32.6, 6.6), "cloth")
	o = "armorBody"
	b.cube(o, (-6.4, 17.4, -3.9), (6.4, 24.4, 4.1), "cloth", faces_paint=on_sides(pleats))
	for y in (18.2, 19.8, 21.4, 23.0):
		b.cube(o, (-0.4, y, -4.2), (0.4, y + 0.8, -3.9), "gilt")


def headsmans_hood(b):
	"""The headsman's hood: black cloth pulled down over the whole face, two eyeholes cut in it, and its cape to a point
	before."""
	bones(b, "head", "body")
	h = "armorHead"
	b.cube(h, (-4.5, 23.2, -4.5), (4.5, 32.9, 4.6), "black", faces_paint={"north": eyeholes, **{s: pleats for s in ("east", "west", "south")}})
	b.cube("armorBody", (-5.4, 20.6, -3.3), (5.4, 24.4, 3.5), "black", faces_paint=on_sides(pleats))
	b.cube("armorBody", (-2.2, 18.6, -3.5), (2.2, 20.6, -3.1), "black")


def arming_cap(b):
	"""The arming cap: a quilted linen cap close round the skull, padded at the brow and tied under the chin; it goes on
	under a helm."""
	bones(b, "head")
	h = "armorHead"
	b.cube(h, (-4.35, 27.4, -4.35), (4.35, 32.5, 4.35), "cloth", faces_paint={**on_sides(quilting), "up": quilting})
	b.cube(h, (-4.5, 27.2, -4.5), (4.5, 28.2, 4.5), "cloth", faces_paint=on_sides(quilting))
	both(b, h, (4.0, 24.6, -0.4), (4.45, 27.4, 0.4), "cloth")


def linen_coif(b):
	"""The linen coif: a plain cap of linen over the ears, tied under the chin with long ties."""
	bones(b, "head")
	h = "armorHead"
	b.cube(h, (-4.3, 26.2, -4.25), (4.3, 32.4, 4.3), "cloth", faces_paint={"north": opening(0, 0, 0, 3), **{s: pleats for s in ("east", "west", "south")}})
	both(b, h, (3.9, 23.8, -0.5), (4.35, 26.2, 0.3), "cloth")


def bag_hat(b):
	"""The bag hat: a soft sack of cloth on a band round the head, its top flopped over to one side."""
	bones(b, "head")
	h = "armorHead"
	b.cube(h, (-4.5, 29.4, -4.5), (4.5, 31.0, 4.5), "cloth_dark")
	b.cube(h, (-4.3, 31.0, -4.0), (4.3, 34.0, 4.6), "cloth", faces_paint={**on_sides(pleats), "up": pleats})
	b.cube(h, (0.2, 33.0, -3.4), (5.6, 34.6, 3.8), "cloth", faces_paint={**on_sides(pleats), "up": pleats}, rotation=(0, 0, -24), origin=(0.2, 34, 0))


def archers_cap(b):
	"""The archer's cap: a tall felt cap with its brim turned up round it and drawn out to a peak in front, a feather
	stuck in the side."""
	bones(b, "head")
	h = "armorHead"
	b.cube(h, (-4.2, 30.0, -4.0), (4.2, 34.0, 4.2), "felt", faces_paint=on_sides(pleats))
	b.cube(h, (-3.0, 34.0, -3.0), (3.0, 34.8, 3.2), "felt")
	b.cube(h, (-4.7, 29.4, -4.0), (4.7, 31.6, 4.7), "felt")
	b.cube(h, (-3.0, 29.6, -6.6), (3.0, 30.4, -4.0), "felt", rotation=(-24, 0, 0), origin=(0, 30, -4))
	b.cube(h, (3.9, 31.0, 1.0), (4.3, 37.6, 2.4), "feather", faces_paint={"east": feathers, "west": feathers}, rotation=(-18, 0, -12), origin=(4.1, 31, 1.6))


def field_hat(b):
	"""The field hat: plaited straw, a low round crown and a wide flat brim to keep the sun off a field hand or a fisher."""
	bones(b, "head")
	h = "armorHead"
	S = {**on_sides(straw), "up": straw, "down": straw}
	b.cube(h, (-7.8, 29.4, -7.8), (7.8, 30.1, 7.8), "straw", faces_paint=S)
	b.cube(h, (-4.4, 30.1, -4.4), (4.4, 33.0, 4.4), "straw", faces_paint=S)
	b.cube(h, (-3.2, 33.0, -3.2), (3.2, 33.6, 3.2), "straw", faces_paint=S)
	b.cube(h, (-4.5, 30.1, -4.5), (4.5, 30.9, 4.5), "strap")


def hunters_hat(b):
	"""The hunter's hat: felt, a rounded crown and a brim pinned up on the left, a long feather sweeping back from the pin."""
	bones(b, "head")
	h = "armorHead"
	b.cube(h, (-4.3, 30.2, -4.3), (4.3, 34.4, 4.3), "felt", faces_paint=on_sides(pleats))
	b.cube(h, (-3.2, 34.4, -3.2), (3.2, 35.2, 3.2), "felt")
	b.cube(h, (-4.6, 29.6, -6.0), (6.0, 30.4, 6.0), "felt")
	b.cube(h, (-5.4, 29.6, -4.8), (-4.6, 34.2, 4.8), "felt")
	b.cube(h, (-5.5, 32.2, -1.0), (-5.0, 33.0, -0.2), "gilt")
	b.cube(h, (-5.9, 32.4, -0.8), (-5.5, 40.0, 0.8), "feather", faces_paint={"east": feathers, "west": feathers}, rotation=(32, 0, 8), origin=(-5.7, 32.4, 0))


def landsknecht_bonnet(b):
	"""The slashed bonnet: a wide flat beret worn tilted over one ear, its cloth slashed to show a lining of another colour,
	on a band round the head and heaped with plumes."""
	bones(b, "head")
	h = "armorHead"
	b.cube(h, (-4.4, 30.0, -4.4), (4.4, 31.6, 4.4), "cloth", faces_paint=on_sides(pleats))
	b.bone("bonnet", h, (0, 31.4, 0), (0, 0, 12))
	B = "bonnet"
	b.cube(B, (-6.8, 31.4, -6.4), (6.8, 33.0, 6.2), "cloth", faces_paint={**on_sides(slashes), "up": slashes, "down": pleats})
	b.cube(B, (-6.6, 31.6, -6.2), (6.6, 32.8, 6.0), SECOND_MAT)
	for i, (x, z, lean, tip) in enumerate(((2.4, 1.0, 30, 40.4), (3.8, -0.4, 44, 39.4), (1.2, 2.6, 20, 41.0), (4.6, 2.0, 56, 38.6))):
		b.cube(B, (x, 32.6, z), (x + 0.5, tip, z + 1.6), "feather", faces_paint={"east": feathers, "west": feathers},
			rotation=(-8 - 4 * i, 0, -lean), origin=(x, 32.6, z + 0.8))


def fur_hat(b):
	"""The fur hat: a round cap of goatskin or fur, the hair outside, without the hood's shoulder pelts."""
	bones(b, "head")
	h = "armorHead"
	F = {**on_sides(fur), "up": fur}
	b.cube(h, (-4.9, 28.4, -4.9), (4.9, 33.4, 4.9), "fur", faces_paint=F)
	b.cube(h, (-4.0, 33.4, -4.0), (4.0, 34.4, 4.0), "fur", faces_paint=F)


def turban(b, veiled=False):
	"""The turban: a long cloth wound round and round the head, each turn crossing the last, a jewel pinned at the brow."""
	bones(b, "head")
	h = "armorHead"
	W = {**on_sides(wraps), "up": wraps}
	b.cube(h, (-4.9, 28.4, -4.9), (4.9, 30.4, 4.9), "cloth", faces_paint=W)
	b.cube(h, (-5.2, 30.2, -5.2), (5.2, 32.0, 5.2), "cloth", faces_paint=W, rotation=(0, 8, 0), origin=(0, 31, 0))
	b.cube(h, (-4.8, 31.8, -4.8), (4.8, 33.4, 4.8), "cloth", faces_paint=W, rotation=(0, -10, 0), origin=(0, 32, 0))
	b.cube(h, (-3.6, 33.2, -3.6), (3.6, 34.6, 3.6), "cloth", faces_paint=W)
	b.cube(h, (-0.6, 30.4, -5.5), (0.6, 31.8, -5.1), "jewel")
	b.cube(h, (-0.9, 30.2, -5.3), (0.9, 30.6, -5.0), "gilt")
	if veiled:
		# the veil: the cloth's end drawn across the face below the eyes and hung down the left side
		b.cube(h, (-4.3, 23.8, -4.7), (4.3, 28.6, -4.2), "cloth", faces_paint={"north": pleats})
		b.cube(h, (-5.0, 24.0, -4.4), (-4.4, 30.0, 2.0), "cloth", faces_paint=on_sides(pleats))


def veiled_turban(b):
	"""The veiled turban: the turban with the cloth's end drawn over the face below the eyes, against sand and sun."""
	turban(b, veiled=True)


def jesters_hood(b):
	"""The jester's hood: a hood cut in two colours (motley), its two points standing up and out like ass's ears with a
	bell on each tip, and a collar of points over the shoulders."""
	bones(b, "head", "body")
	h = "armorHead"
	for half, (x0, x1) in (("cloth", (0.0, 4.8)), (SECOND_MAT, (-4.8, 0.0))):
		face = {"north": opening(2 if x0 < 0 else 0, 0 if x0 < 0 else 2, 2, 2), "east": pleats, "west": pleats, "south": pleats}
		b.cube(h, (x0, 23.6, -4.8), (x1, 32.9, 4.9), half, faces_paint=face)
		b.cube("armorBody", (x0 * 1.12, 20.2, -3.4), (x1 * 1.12, 24.4, 3.6), half, faces_paint=on_sides(ragged((0, 2, 0, 2))))
	# the points, each the other colour from its side of the hood, and the bells
	for s, mat in ((1, SECOND_MAT), (-1, "cloth")):
		x0, x1 = sorted((s * 1.4, s * 3.4))
		b.cube(h, (x0, 32.4, -0.9), (x1, 36.2, 0.9), mat, rotation=(0, 0, -30 * s), origin=(s * 2.4, 32.4, 0))
		x0, x1 = sorted((s * 3.8, s * 5.2))
		b.cube(h, (x0, 35.0, -0.6), (x1, 38.2, 0.6), mat, rotation=(0, 0, -62 * s), origin=(s * 4.4, 35.0, 0))
		x0, x1 = sorted((s * 6.4, s * 7.4))
		b.cube(h, (x0, 35.2, -0.5), (x1, 36.2, 0.5), "gilt")


def laurel_wreath(b):
	"""The victor's wreath: two sprays of laurel bound round the bare head, open over the brow, their ribbon tied behind."""
	bones(b, "head")
	h = "armorHead"
	L = {**on_sides(leaves), "up": leaves}
	both(b, h, (0.8, 30.0, -4.7), (4.7, 31.4, -4.1), "leaf", faces_paint=L)
	both(b, h, (4.1, 30.0, -4.7), (4.7, 31.4, 4.7), "leaf", faces_paint=L)
	b.cube(h, (-4.7, 30.0, 4.1), (4.7, 31.4, 4.7), "leaf", faces_paint=L)
	b.cube(h, (-0.8, 27.6, 4.6), (0.8, 30.6, 5.0), "red")


# ---- the mail coif's cuts ---------------------------------------------------------------------------------------

def coif(b, face, ventail=None):
	"""The mail coif with {face} for its front, and (if {ventail}) a flap of mail laced across the mouth: its (left, right)."""
	bones(b, "head", "body")
	h = "armorHead"
	b.cube(h, (-4.9, 23.6, -4.9), (4.9, 33.2, 4.9), "mail", faces_paint={"north": face, "east": rings, "west": rings, "south": rings, "up": rings})
	b.cube("armorBody", (-5.6, 20.4, -3.4), (5.6, 24.4, 3.6), "mail", faces_paint={**on_sides(rings), "up": rings})
	if ventail:
		left, right = ventail
		b.cube(h, (left, 24.4, -5.4), (right, 27.6, -4.9), "mail", faces_paint={**on_sides(rings), "up": rings})
		b.cube(h, (right, 26.2, -5.5), (right + 0.6, 27.4, -4.9), "strap")


def lowered_mail_coif(b):
	"""The mail coif pushed back off the head: its hood lies bunched in a roll round the neck and down the back."""
	bones(b, "body")
	o = "armorBody"
	b.cube(o, (-5.6, 20.4, -3.4), (5.6, 24.4, 3.6), "mail", faces_paint={**on_sides(rings), "up": rings})
	b.cube(o, (-4.8, 23.8, -3.0), (4.8, 26.0, 3.8), "mail", faces_paint={**on_sides(rings), "up": rings})
	b.cube(o, (-3.8, 19.4, 3.6), (3.8, 25.4, 4.8), "mail", faces_paint={**on_sides(rings), "up": rings})


def square_mail_coif(b):
	"""The square-cut coif: the face opening cut square, from the brow to the chin."""
	coif(b, square_opening(2, 2, 2, 1))


def ventailed_mail_coif(b):
	"""The coif with a ventail: a round-cut face opening, its lower half closed by a flap of mail laced up at the side."""
	coif(b, round_opening(2, 2, 2, 2), ventail=(-3.6, 2.8))


def square_ventailed_mail_coif(b):
	"""The square-cut coif with a ventail: the square opening, the flap laced across it to the cheekbones."""
	coif(b, square_opening(2, 2, 2, 1), ventail=(-3.8, 3.0))


# ---- crests (on the helm's crown at y=33; the orle and the mantling are lifted with the torse for a taller helm) ------

def orle(b):
	"""The orle: a padded roll of cloth round the crown of the helm, plain, where a torse is twisted."""
	bones(b, "head")
	h = "armorHead"
	O = {**on_sides(stitched), "up": stitched}
	b.cube(h, (-3.2, 33, -3.2), (3.2, 34.5, -1.8), "cloth", faces_paint=O)
	b.cube(h, (-3.2, 33, 1.8), (3.2, 34.5, 3.2), "cloth", faces_paint=O)
	b.cube(h, (-3.2, 33, -1.8), (-1.8, 34.5, 1.8), "cloth", faces_paint=O)
	b.cube(h, (1.8, 33, -1.8), (3.2, 34.5, 1.8), "cloth", faces_paint=O)


# the crown's ring, sized to stand clear of every helm's skull (the widest, the veiled helm, is 5.0 each side) at the
# same height on all of them; it isn't lifted as a torse is
CROWN_SIDE, CROWN_FRONT, CROWN_BACK, CROWN_LOW, CROWN_HIGH = 5.1, -6.1, 5.3, 31.0, 32.6


def crest_crown(b):
	"""The helm crown: a gold circlet round the helm above the brow, set with stones and standing in points and leaves."""
	bones(b, "head")
	h = "armorHead"
	s, f, k, lo, hi = CROWN_SIDE, CROWN_FRONT, CROWN_BACK, CROWN_LOW, CROWN_HIGH
	b.cube(h, (-s, lo, f), (s, hi, f + 0.5), "gilt", faces_paint=P_ALL)
	b.cube(h, (-s, lo, k - 0.5), (s, hi, k), "gilt", faces_paint=P_ALL)
	both(b, h, (s - 0.5, lo, f + 0.5), (s, hi, k - 0.5), "gilt", faces_paint=P_ALL)
	for x in (-2.6, 0, 2.6):
		b.cube(h, (x - 0.5, lo + 0.4, f - 0.25), (x + 0.5, hi - 0.4, f), "jewel")
	# the points: a leaf in the middle of each side, a spike at each corner and between
	for x in (-3.8, 0, 3.8):
		leaf = x == 0
		b.cube(h, (x - (0.9 if leaf else 0.4), hi, f), (x + (0.9 if leaf else 0.4), hi + (1.2 if leaf else 1.6), f + 0.5), "gilt")
		if leaf:
			b.cube(h, (x - 0.4, hi + 1.2, f), (x + 0.4, hi + 2.2, f + 0.5), "gilt")
		b.cube(h, (x - 0.4, hi, k - 0.5), (x + 0.4, hi + 1.4, k), "gilt")
	both(b, h, (s - 0.5, hi, -2.4), (s, hi + 1.4, -1.6), "gilt")
	both(b, h, (s - 0.5, hi, 1.6), (s, hi + 1.4, 2.4), "gilt")
	both(b, h, (s - 0.9, hi, -0.6), (s, hi + 2.0, 0.6), "gilt")


def crest_antlers(b):
	"""Antlers: a stag's pair fixed to the sides of the helm, each beam sweeping up and out with its tines forward."""
	bones(b, "head")
	h = "armorHead"
	A = "antler"
	both(b, h, (3.6, 31.0, -0.7), (5.0, 32.4, 0.7), "strap")
	both(b, h, (4.6, 31.6, -0.4), (5.4, 36.6, 0.4), A, rotation=(0, 0, -22), origin=(4.6, 31.6, 0))
	both(b, h, (6.0, 35.8, -0.35), (6.7, 40.2, 0.35), A, rotation=(0, 0, -8), origin=(6.0, 35.8, 0))
	# the tines: brow tine forward, two up the beam, and the crown at the top
	both(b, h, (5.0, 33.0, -2.6), (5.6, 33.6, -0.3), A)
	both(b, h, (6.0, 36.6, -2.2), (6.6, 37.2, -0.3), A)
	both(b, h, (6.6, 38.4, -1.8), (7.2, 39.0, -0.3), A)
	both(b, h, (5.2, 39.6, -0.3), (5.8, 41.4, 0.3), A)
	both(b, h, (7.0, 39.8, -0.3), (7.6, 41.6, 0.3), A)


def crest_mantling(b):
	"""The mantling: a cloth in the realm's colour hung from the crown of the helm down its back and sides, its edge cut in
	dags, under the torse."""
	bones(b, "head")
	h = "armorHead"
	b.bone("mantle", h, (0, 33.4, 3.4), (12, 0, 0))
	m = "mantle"
	b.cube(m, (-4.6, 25.0, 3.4), (4.6, 33.4, 4.0), "cloth", faces_paint={"north": dags, "south": dags})
	both(b, m, (4.2, 26.0, -1.2), (4.8, 33.4, 3.4), "cloth", faces_paint={"east": dags, "west": dags})
	b.cube(h, (-3.0, 33.0, -1.4), (3.0, 33.6, 3.6), "cloth")


# Any crown can be worn over a helm in the crest's place (Andy, 2026-11-25: "you should be able to wear any crown"): the
# rulers' crowns (Regalia, gen_regalia.py) are painted on the humanoid armour layer's head, a band in the top four rows of
# each side, so one box drawn with whichever crown's own sheet carries any of them. It is the head's box made wider and
# deeper than every helm (the veiled helm is 5.0 each side and reaches 6.0 before) and raised so the band rings the
# helm's crown, its points above it.
REGALIA = ("royal_crown", "jeweled_crown", "iron_crown", "laurel_crown", "tiara", "circlet", "coronet")
OVER_HELM_FROM, OVER_HELM_TO = (-5.2, 26.6, -6.2), (5.2, 35.4, 5.4)


def regalia_sheet(name):
	return os.path.join(ASSETS, "textures", "entity", "equipment", "humanoid", name + ".png")


def crown_over_helm(sheet):
	"""The box a ruler's crown is drawn on over a helm, its faces mapped as the head's are on the armour layer's sheet."""
	b = g.Builder(64, 32, 1)
	bones(b, "head")
	b.img = Image.open(sheet).convert("RGBA")
	faces = {"up": [8, 0, 16, 8], "down": [16, 8, 24, 0], "east": [0, 8, 8, 16], "north": [8, 8, 16, 16], "west": [16, 8, 24, 16], "south": [24, 8, 32, 16]}
	e = {"name": "armorHead_crown", "box_uv": False, "rescale": False, "locked": False, "render_order": "default", "allow_mirror_modeling": True,
		"from": list(OVER_HELM_FROM), "to": list(OVER_HELM_TO), "autouv": 0, "color": 0, "inflate": 0.0, "origin": [0, 24, 0], "rotation": [0, 0, 0],
		"faces": {f: {"uv": uv, "texture": 0} for f, uv in faces.items()}, "type": "cube", "uuid": kit_uid()}
	b.elements.append(e)
	b.bones["armorHead"]["children"].append(e["uuid"])
	return b


def kit_uid():
	import gen_tomb_undead
	return gen_tomb_undead.uid()


# ---- neck ---------------------------------------------------------------------------------------------------------

def armet_wrapper(b):
	"""The wrapper: a rounded plate strapped round the lower face and chin over an armet's cheeks, rising higher than a
	bevor, a strap behind the neck."""
	bones(b, "body")
	o = "armorBody"
	# (it stands clear of the armet, whose chin reaches z -5.6)
	b.cube(o, (-4.4, 21.8, -6.4), (4.4, 27.6, -5.7), "plate", faces_paint={**P_ALL, "north": lames(3, 2)})
	b.cube(o, (-3.6, 21.2, -5.7), (3.6, 22.6, -3.4), "plate", faces_paint=P_ALL)
	both(b, o, (4.4, 22.2, -6.1), (5.1, 27.2, -1.2), "plate", faces_paint=P_ALL)
	b.cube(o, (-5.1, 22.6, -1.2), (5.1, 23.4, 2.8), "strap")
	both(b, o, (4.9, 24.4, -5.0), (5.3, 25.2, -4.2), "rivet")


# ---- what there is ------------------------------------------------------------------------------------------------

# cloth pieces the game tints: piece -> (build, the dye an undyed one shows (HeraldryLayer), its second colour or None)
DYED = {
	"tight_hood": (tight_hood, 0x6B5A44, None), "monks_cowl": (monks_cowl, 0x5A4632, None), "clerics_mantle": (clerics_mantle, 0x8E2B2B, None),
	"arming_cap": (arming_cap, 0xD8CCB0, None), "linen_coif": (linen_coif, 0xECE6D6, None), "bag_hat": (bag_hat, 0x6E7F4A, None),
	"archers_cap": (archers_cap, 0x3F6B3A, None), "hunters_hat": (hunters_hat, 0x5E4A32, None),
	"landsknecht_bonnet": (landsknecht_bonnet, 0xA02828, 0xF0C838), "turban": (turban, 0xE8E0CC, None),
	"veiled_turban": (veiled_turban, 0x3A5A8E, None), "jesters_hood": (jesters_hood, 0xB02E26, 0xE8C83A),
	# crests in the torse's dye (HeraldryLayer.TORSE_UNDYED)
	"orle": (orle, 0xB02E26, None), "crest_mantling": (crest_mantling, 0xB02E26, None),
}
# pieces drawn as they are painted
PLAIN = {"headsmans_hood": headsmans_hood, "field_hat": field_hat, "fur_hat": fur_hat, "laurel_wreath": laurel_wreath,
	"lowered_mail_coif": lowered_mail_coif, "square_mail_coif": square_mail_coif, "ventailed_mail_coif": ventailed_mail_coif,
	"square_ventailed_mail_coif": square_ventailed_mail_coif, "crest_crown": crest_crown, "crest_antlers": crest_antlers}
PLATE = {"armet_wrapper": armet_wrapper}
NAMES = {
	"tight_hood": "Tight Hood", "monks_cowl": "Monk's Cowl", "clerics_mantle": "Cleric's Mantle", "headsmans_hood": "Headsman's Hood",
	"arming_cap": "Arming Cap", "linen_coif": "Linen Coif", "bag_hat": "Bag Hat", "archers_cap": "Archer's Cap", "field_hat": "Field Hat",
	"hunters_hat": "Hunter's Hat", "landsknecht_bonnet": "Slashed Bonnet", "fur_hat": "Fur Hat", "turban": "Turban",
	"veiled_turban": "Veiled Turban", "jesters_hood": "Jester's Hood", "laurel_wreath": "Victor's Wreath",
	"lowered_mail_coif": "Lowered Mail Coif", "square_mail_coif": "Square-cut Mail Coif", "ventailed_mail_coif": "Mail Coif with Ventail",
	"square_ventailed_mail_coif": "Square-cut Coif with Ventail", "orle": "Orle", "crest_crown": "Helm Crown",
	"crest_antlers": "Crest of Antlers", "crest_mantling": "Mantling", "armet_wrapper": "Wrapper",
}
# crafting (shapeless): the cloth from a tailor's makings; the coifs and the wrapper are forged at Jaymo's Powers' anvil
WOOL, STRING, LEATHER, FEATHER, NUGGET = "#minecraft:wool", "minecraft:string", "minecraft:leather", "minecraft:feather", "minecraft:gold_nugget"
RECIPES = {
	"tight_hood": [WOOL, WOOL, STRING, STRING], "monks_cowl": [WOOL, WOOL, WOOL, STRING, STRING],
	"clerics_mantle": [WOOL, WOOL, WOOL, STRING, NUGGET], "headsmans_hood": ["minecraft:black_wool", "minecraft:black_wool", STRING, "minecraft:ink_sac"],
	"arming_cap": [STRING, STRING, WOOL, LEATHER], "linen_coif": [STRING, STRING, STRING, WOOL, WOOL],
	"bag_hat": [WOOL, WOOL, WOOL, STRING, LEATHER], "archers_cap": [WOOL, WOOL, LEATHER, FEATHER],
	"field_hat": ["minecraft:wheat"] * 5 + [STRING], "hunters_hat": [WOOL, WOOL, LEATHER, FEATHER, STRING],
	"fur_hat": ["#c:hides", "#c:hides", STRING], "turban": [WOOL, WOOL, WOOL, WOOL, STRING],
	"veiled_turban": ["jaymoskingdoms:turban", WOOL, STRING], "laurel_wreath": ["#minecraft:leaves"] * 3 + [STRING],
	"orle": [WOOL, WOOL, LEATHER], "crest_antlers": ["jaymoskingdoms:torse", "minecraft:bone", "minecraft:bone", "minecraft:bone"],
	"crest_mantling": ["jaymoskingdoms:torse", WOOL, WOOL],
}
# made with a dye that gives the second colour: piece -> its other makings
SECOND_RECIPES = {"landsknecht_bonnet": [WOOL, WOOL, WOOL, FEATHER, FEATHER], "jesters_hood": [WOOL, WOOL, WOOL, STRING, NUGGET, NUGGET]}
# the dyes' colours (DyeColor's texture colour in 26.3)
DYES = {"white": 15790320, "orange": 15435844, "magenta": 12801229, "light_blue": 6719955, "yellow": 14602026, "lime": 4312372,
	"pink": 14188952, "gray": 4408131, "light_gray": 11250603, "cyan": 2651799, "purple": 8073150, "blue": 2437522,
	"brown": 5320730, "green": 3887386, "red": 11743532, "black": 1973019}


def split(b, mats):
	"""A copy of the sheet {b} painted with only the cubes whose material is one of {mats}, the rest left clear."""
	keep = Image.new("RGBA", b.img.size, (0, 0, 0, 0))
	for e in b.elements:
		if e["name"].split("_", 1)[1] not in mats:
			continue
		for face in e["faces"].values():
			x0, y0, x1, y1 = face["uv"]
			for y in range(min(y0, y1), max(y0, y1)):
				for x in range(min(x0, x1), max(x0, x1)):
					keep.putpixel((x, y), b.img.getpixel((x, y)))
	return keep


def mats_of(b):
	return {e["name"].split("_", 1)[1] for e in b.elements}


def model_with(b, img):
	saved = b.img
	b.img = img
	model = b.project("piece")
	b.img = saved
	return model


def icon(b, img, name):
	g.icon(model_with(b, img), os.path.join(ASSETS, "textures", "item", name + ".png"))


def recipe(name, ingredients, result=None, suffix=""):
	r.write(os.path.join(DATA, "jaymoskingdoms", "recipe", name + suffix + ".json"), {"type": "minecraft:crafting_shapeless", "category": "equipment",
		"group": "jaymoskingdoms:crest" if name.startswith("crest_") or name == "orle" else "jaymoskingdoms:harness", "ingredients": ingredients,
		"result": result or {"count": 1, "id": "jaymoskingdoms:" + name}})


def dents(name):
	"""As gen_heraldry_armour's dents, for {name}: the plate's dents and scratches at each stage of wear."""
	bb = os.path.join(ASSETS, "textures", "entity", "bb")
	bright = Image.open(os.path.join(bb, "heraldry_%s.png" % name)).convert("RGBA")
	dark = Image.open(os.path.join(bb, "heraldry_%s_blackened.png" % name)).convert("RGBA")
	w, h = bright.size
	metal = {(x, y) for y in range(h) for x in range(w) if bright.getpixel((x, y))[3] and bright.getpixel((x, y))[:3] != dark.getpixel((x, y))[:3]}
	order = sorted(metal)
	random.Random(name).shuffle(order)
	for stage, share in g.DENT_STAGES:
		img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
		pits = set(order[:int(len(order) * share)])
		for x, y in pits:
			put(img, x, y, g.DENT)
			if (x, y + 1) in metal and (x, y + 1) not in pits:
				put(img, x, y + 1, g.DENT_LIP)
		g.entity_texture(img, "heraldry_%s_dents%d" % (name, stage))


def main(argv):
	preview = argv[argv.index("--preview") + 1] if "--preview" in argv else None
	made = []
	# the dyed cloth: three sheets, an icon of the dyed cloth tinted by the item's dye over one of the rest
	for name, (build, undyed, second) in DYED.items():
		b, model = g.make(build, 128)
		g.bake(model, ASSETS, "heraldry_" + name)
		mats = mats_of(b)
		base = split(b, DYED_MATS)
		g.entity_texture(base, "heraldry_" + name)
		fixed = mats - set(DYED_MATS) - {SECOND_MAT}
		layers = [name]
		trim = Image.new("RGBA", b.img.size, (0, 0, 0, 0))
		if fixed:
			sheet = split(b, fixed)
			g.entity_texture(sheet, "heraldry_%s_fixed" % name)
			trim.alpha_composite(sheet)
		if second is not None:
			sheet = split(b, (SECOND_MAT,))
			g.entity_texture(sheet, "heraldry_%s_second" % name)
			trim.alpha_composite(g.tinted(sheet, second))
		icon(b, base, name)
		if fixed or second is not None:
			icon(b, trim, name + "_trim")
			layers.append(name + "_trim")
		g.flat(name, layers)
		g.item(name, {"type": "minecraft:model", "model": "jaymoskingdoms:item/" + name, "tints": [{"type": "minecraft:dye", "default": undyed - (1 << 24)}]})
		g.dye_recipe(name)
		made.append((name, build, ""))
	for name, build in PLAIN.items():
		b, model = g.make(build, 64)
		g.bake(model, ASSETS, "heraldry_" + name)
		g.entity_texture(b.img, "heraldry_" + name)
		icon(b, b.img, name)
		g.item(name, g.flat(name))
		made.append((name, build, ""))
	for name, build in PLATE.items():
		icons = {}
		for finish in g.FINISHES:
			b, model = g.make(build, 64, finish)
			if finish == "":
				g.bake(model, ASSETS, "heraldry_" + name)
			g.entity_texture(b.img, "heraldry_" + name + ("_" + finish if finish else ""))
			icons[finish] = name + ("_" + finish if finish else "")
			icon(b, b.img, icons[finish])
		g.item(name, {"type": "minecraft:select", "property": "minecraft:component", "component": "jaymoskingdoms:finish",
			"cases": [{"when": f, "model": g.flat(icons[f])} for f in g.FINISHES if f], "fallback": g.flat(icons[""])})
		dents(name)
		made.append((name, build, ""))
	b = crown_over_helm(regalia_sheet("royal_crown"))
	g.bake(b.project("piece"), ASSETS, "heraldry_crown_over_helm")
	# (the game draws it with the crown's own sheet, not one of its own)
	os.remove(os.path.join(ASSETS, "textures", "entity", "bb", "heraldry_crown_over_helm.png"))
	for name, ingredients in RECIPES.items():
		recipe(name, ingredients)
	r.write(os.path.join(DATA, "jaymoskingdoms", "recipe", "crest_crown.json"), {"type": "minecraft:crafting_shaped", "category": "equipment",
		"group": "jaymoskingdoms:crest", "pattern": ["GEG", "GGG"], "key": {"G": "minecraft:gold_ingot", "E": "minecraft:emerald"},
		"result": {"count": 1, "id": "jaymoskingdoms:crest_crown"}})
	for name, ingredients in SECOND_RECIPES.items():
		for dye, rgb in DYES.items():
			recipe(name, ingredients + ["minecraft:%s_dye" % dye], {"count": 1, "id": "jaymoskingdoms:" + name,
				"components": {"jaymoskingdoms:second_colour": rgb}}, "_" + dye)
	r.write(os.path.join(ASSETS, "lang", "parts", "heraldry-hoods.json"), {"item.jaymoskingdoms." + n: t for n, t in NAMES.items()})
	if preview:
		previews(preview)
	print("heraldry hoods:", len(NAMES), "pieces")


# ---- previews (not in the game's files): each piece worn on a grey figure, from the front, the side and behind ------

# the helms a crest is shown on, and how far the game lifts a torse-borne crest on each (HeraldryArmour's crestRise)
SHOWN_ON = {"orle": [("great_helm", 0.0), ("armet", 0.9)], "crest_mantling": [("great_helm", 0.0), ("close_helm", 1.0)],
	"crest_crown": [("armet", 0.0), ("great_helm", 0.0), ("veiled_helm", 0.0), ("kettle_hat", 0.0), ("houndskull_bascinet", 0.0)],
	"crest_antlers": [("sallet", 0.0)], "armet_wrapper": [("armet", 0.0)], "ventailed_mail_coif": [(None, 0)]}
LIFTED = ("orle", "crest_mantling")


def previews(out):
	os.makedirs(out, exist_ok=True)
	# every ruler's crown over a helm
	for crown in REGALIA:
		for helm in ("armet", "great_helm", "veiled_helm"):
			def over(b, crown=crown, helm=helm):
				g.PLATE[helm][0](b)
			b, _ = g.make(over, 64, figure=True)
			tag = "%s_on_%s" % (crown, helm)
			c = crown_over_helm(regalia_sheet(crown))
			# the helm and figure on their own sheet, the crown on its: both baked into one model by putting the crown's
			# sheet below the other's
			sheet = Image.new("RGBA", (b.img.width, b.img.height + 32), (0, 0, 0, 0))
			sheet.paste(b.img, (0, 0))
			sheet.paste(c.img, (0, b.img.height))
			for e in c.elements:
				for face in e["faces"].values():
					face["uv"] = [face["uv"][0], face["uv"][1] + b.img.height, face["uv"][2], face["uv"][3] + b.img.height]
				b.elements.append(e)
				b.bones["armorHead"]["children"].append(e["uuid"])
			b.img, b.h = sheet, sheet.height
			scratch = tempfile.mkdtemp()
			g.bake(b.project("piece"), scratch, tag)
			sheet.save(os.path.join(scratch, "textures", "entity", "bb", tag + ".png"))
			subprocess.run([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_preview.py"), scratch, tag, os.path.join(out, tag + ".png")],
				check=True, capture_output=True)
	every = {**{n: (v[0], v[1], v[2]) for n, v in DYED.items()}, **{n: (bld, None, None) for n, bld in PLAIN.items()},
		**{n: (bld, None, None) for n, bld in PLATE.items()}}
	for name, (build, undyed, second) in every.items():
		for helm, rise in SHOWN_ON.get(name, [(None, 0)]):
			def both_builds(b, build=build, helm=helm, rise=rise, name=name):
				# (a helm and the piece both name the wearer's bones: each bone once)
				made_bone = b.bone
				b.bone = lambda n, parent, origin, rotation=g.Z: b.bones[n] if n in b.bones else made_bone(n, parent, origin, rotation)
				if helm:
					g.PLATE[helm][0](b)
				start = len(b.elements)
				build(b)
				if name in LIFTED:
					for e in b.elements[start:]:
						e["from"][1] += rise
						e["to"][1] += rise
						e["origin"][1] += rise
					if "mantle" in b.bones:
						b.bones["mantle"]["origin"][1] += rise
			b, _ = g.make(both_builds, 128, figure=True)
			if undyed is not None:
				img = b.img.copy()
				img.alpha_composite(g.tinted(split(b, DYED_MATS), undyed))
				if second is not None:
					img.alpha_composite(g.tinted(split(b, (SECOND_MAT,)), second))
				b.img = img
			# a crest's torse, as the game draws it under every crest but the crown and the orle
			if name.startswith("crest_") and name != "crest_crown":
				def torse_too(t, helm=helm, rise=rise, inner=both_builds):
					inner(t)
					start = len(t.elements)
					g.torse(t)
					for e in t.elements[start:]:
						for k in ("from", "to", "origin"):
							e[k][1] += rise
				b, _ = g.make(torse_too, 128, figure=True)
				img = b.img.copy()
				img.alpha_composite(g.tinted(split(b, DYED_MATS), 0xB02E26))
				b.img = img
			model = b.project("piece")
			tag = "%s%s" % (name, "_on_" + helm if helm else "")
			scratch = tempfile.mkdtemp()
			g.bake(model, scratch, tag)
			b.img.save(os.path.join(scratch, "textures", "entity", "bb", tag + ".png"))
			subprocess.run([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_preview.py"), scratch, tag, os.path.join(out, tag + ".png")],
				check=True, capture_output=True)


if __name__ == "__main__":
	main(sys.argv[1:])
