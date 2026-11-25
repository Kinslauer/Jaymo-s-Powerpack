"""
The harness's waist, leg and foot pieces from Andy's Mordhau picks (Mordhau harness 4), and the new HOSE place: our own cubes and
paint of the historical armour and clothing each pick is based on (Mordhau's meshes, textures and names belong to Triternion and
none of them is used here). Built the way gen_heraldry_armour.py, gen_heraldry_hoods.py and gen_heraldry_body.py build the rest of
the harness, with their painters and helpers, so only this file needs running for these pieces (run it after those three):

- waist (WAIST): the long tassets (lames to the knee), the coat-of-plates skirt, the lamellar skirt and the leather lamellar skirt,
  the quilted (padded) skirt, six cloth skirts (the knee-length skirt, the short skirt, the long skirt, the apron, the pleated
  "base", the embroidered skirt) and the scale skirt;
- thighs (THIGHS): the fluted cuisses, the brigandine cuisses, the leather cuisses, the mail chausses (thigh to foot, laced
  behind);
- shins (SHINS): the schynbald (a plate over the front of the shin only) and the leg wraps;
- feet (the boots slot): the scale sabatons and the mail shoes;
- hose (HOSE, drawn as a sheet on the legs of the under-armour layer, UnderArmourLayer): shorts, breeches, baggy breeches, linen hose,
  joined hose (a colour on each leg), riding hose and the slashed and puffed Landsknecht hose.

A dyed piece is painted in sheets on the same cubes (as gen_heraldry_body.py's): heraldry_<piece> (pale cloth, tinted with its dye),
heraldry_<piece>_fixed (what is drawn as it is) and, for a two-coloured piece, heraldry_<piece>_second (tinted with
jaymoskingdoms:second_colour, which the dye given when it is made sets). A sheet of hose is textures/entity/under_armour/<piece>.png
(+ _second): the 64x32 layer's right leg at (0, 16), which the game mirrors for the left.

Run from the project root: python3 -I kingdoms/tools/gen_heraldry_legs.py [--preview <dir>]   (needs Pillow)
"""
import math
import os
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image  # noqa: E402

import gen_heraldry_armour as g  # noqa: E402
import gen_heraldry_body as bd  # noqa: E402
import gen_heraldry_hoods as h  # noqa: E402
import gen_reverent as r  # noqa: E402
from gen_heraldry_armour import (P, P_ALL, both, bones, flutes, lames, mul, on_sides, pleats, plate, put, quilting, rings, shade,  # noqa: E402
	studs, rivets)

ASSETS, DATA, ROOT = g.ASSETS, g.DATA, g.ROOT
UNDER = os.path.join(ASSETS, "textures", "entity", "under_armour")
DYED_MATS, SECOND_MAT = h.DYED_MATS, h.SECOND_MAT
CLOTH = g.FIXED["cloth"]


# ---- painters ---------------------------------------------------------------------------------------------------

def scales(img, rect):
	"""Scale armour: rows of small overlapping scales, the top of each row bright and its foot in shadow, every row offset."""
	x0, y0, x1, y1 = rect
	for y in range(y0, y1):
		band = (y - y0) // 3
		for x in range(x0, x1):
			k = (1.2, 1.0, 0.7)[(y - y0) % 3] * (0.9 if (x - x0 + band) % 3 == 1 else 1.0)
			mul(img, x, y, k)


def lamellar(img, rect):
	"""Laced lamellar: small plates in rows, a lacing line between them every other column and a darker seam below each row."""
	x0, y0, x1, y1 = rect
	for y in range(y0, y1):
		for x in range(x0, x1):
			k = 1.0
			if (x - x0 + ((y - y0) // 3) % 2) % 2 == 0:
				k *= 0.78
			if (y - y0) % 3 == 2:
				k *= 0.7
			mul(img, x, y, k)


def leather_pad(img, rect):
	"""Boiled leather: darker edges and a stitch along them."""
	x0, y0, x1, y1 = rect
	for x in range(x0, x1):
		mul(img, x, y0, 0.78)
		mul(img, x, y1 - 1, 0.72)
	for y in range(y0, y1):
		mul(img, x0, y, 0.84)
		mul(img, x1 - 1, y, 0.84)
	for x in range(x0 + 1, x1 - 1, 2):
		put(img, x, y0 + 1, shade(g.FIXED["strap"], 1.5))


def binding(img, rect):
	"""A leg wrap: cloth wound on the slant, a darker line where each turn overlaps."""
	x0, y0, x1, y1 = rect
	for y in range(y0, y1):
		for x in range(x0, x1):
			if (x - x0 + (y - y0) * 2) % 4 == 0:
				mul(img, x, y, 0.78)
			else:
				mul(img, x, y, 1.04)


def shin_ridge(img, rect):
	plate(img, rect)
	x0, y0, x1, y1 = rect
	mid = (x0 + x1) // 2
	for y in range(y0 + 1, y1 - 1):
		mul(img, mid, y, 1.3)
		mul(img, mid - 1, y, 0.8)


# ---- the waist (WAIST) ------------------------------------------------------------------------------------------

def long_tassets(b):
	"""The long tassets: hoops of plate round the hips and lames hung before each thigh down to the knee, a plate down each side."""
	bones(b, "body", "legs")
	o = "armorBody"
	leg = "armor{}Leg"
	b.cube(o, (-4.9, 10.6, -3.1), (4.9, 12.4, 3.1), "plate", faces_paint=P)
	b.cube(o, (-5.1, 9.0, -3.3), (5.1, 10.8, 3.3), "plate", faces_paint=P)
	for i, y0 in enumerate((8.0, 6.4, 4.8, 3.2)):
		both(b, leg, (-0.3, y0, -3.5 - 0.1 * i), (4.5, y0 + 2.0, -2.6), "plate", faces_paint={**P_ALL, "north": lames(1)})
	both(b, leg, (4.4, 4.0, -2.4), (5.0, 9.6, 2.4), "plate", faces_paint=P_ALL)
	both(b, leg, (-0.3, 5.0, 2.6), (4.5, 9.0, 3.2), "plate", faces_paint={**P_ALL, "south": lames(1)})


def skirt_body(b, mat, paint, drop=5.2, belt=True):
	"""A skirt of {mat} cut: a ring round the hips and a panel down each thigh, {paint} on all its faces."""
	bones(b, "body", "legs")
	o = "armorBody"
	leg = "armor{}Leg"
	b.cube(o, (-4.7, 10.2, -2.9), (4.7, 12.6, 2.9), mat, faces_paint=on_sides(paint))
	both(b, leg, (-0.3, 10.4 - drop, -2.6), (4.3, 10.4, 2.6), mat, faces_paint={**on_sides(paint), "down": paint})
	if belt:
		b.cube(o, (-4.8, 12.2, -3.0), (4.8, 13.0, 3.0), "strap")
		b.cube(o, (-0.7, 11.9, -3.3), (0.7, 12.9, -3.0), "brass")


def coat_of_plates_skirt(b):
	"""The coat-of-plates skirt: a cloth skirt with plates riveted inside it (the rivet heads in rows), belted."""
	skirt_body(b, "cloth", rivets)


def lamellar_skirt(b):
	"""The lamellar skirt: rows of small iron plates laced together, hung from a belt."""
	skirt_body(b, "plate", lamellar, 5.6)


def leather_lamellar_skirt(b):
	"""The leather lamellar skirt: the same rows laced in boiled leather."""
	skirt_body(b, "strap", lamellar, 5.6)


def padded_skirt(b):
	"""The padded skirt: the long quilted skirt of an aketon worn alone at the hips."""
	skirt_body(b, "cloth", quilting, 6.4)


def scale_skirt(b):
	"""The scale skirt: the mail skirt's cut in overlapping scales."""
	bones(b, "body", "legs")
	o = "armorBody"
	leg = "armor{}Leg"
	b.cube(o, (-4.7, 10.2, -2.9), (4.7, 12.6, 2.9), "scale", faces_paint=on_sides(scales))
	b.cube(o, (-4.8, 12.2, -3.0), (4.8, 13.0, 3.0), "strap")
	both(b, leg, (-0.3, 6.4, -2.6), (4.3, 10.4, 2.6), "scale", faces_paint=on_sides(scales))


def cloth(b, drop, flare=0.0, front_only=False, band=False, fold=None):
	"""A belted cloth skirt: its ring at the hip and its panels hanging {drop} below, flared {flare} at the hem; {front_only}: an
	apron before the legs only; {band}: a second-colour hem and waistband."""
	bones(b, "body", "legs")
	o = "armorBody"
	leg = "armor{}Leg"
	fold = fold or pleats
	bottom = 10.4 - drop
	if not front_only:
		b.cube(o, (-4.7, 10.2, -2.9), (4.7, 12.6, 2.9), "cloth", faces_paint=on_sides(fold))
		both(b, leg, (-0.3 - flare, bottom, -2.6 - flare), (4.3 + flare, 10.4, 2.6 + flare), "cloth", faces_paint={**on_sides(fold), "down": fold})
	else:
		b.cube(o, (-4.2, 10.2, -3.1), (4.2, 12.6, -2.7), "cloth", faces_paint=on_sides(fold))
		both(b, leg, (-0.3, bottom, -2.9), (4.3, 10.4, -2.6), "cloth", faces_paint={**on_sides(fold), "down": fold})
		b.cube(o, (-4.8, 11.9, -3.2), (4.8, 12.7, 3.0), "strap")
	if band:
		both(b, leg, (-0.35 - flare, bottom - 0.05, -2.65 - flare), (4.35 + flare, bottom + 1.2, 2.65 + flare), SECOND_MAT, faces_paint=on_sides(fold))
		b.cube(o, (-4.8, 12.0, -3.0), (4.8, 13.0, 3.0), SECOND_MAT)
	elif not front_only:
		b.cube(o, (-4.8, 12.2, -3.0), (4.8, 13.0, 3.0), "strap")


def cloth_skirt(b):
	cloth(b, 6.0)


def short_skirt(b):
	cloth(b, 3.0)


def long_skirt(b):
	cloth(b, 9.0)


def apron(b):
	"""The apron: a panel of cloth hung from the waist before the legs, tied behind."""
	cloth(b, 6.5, front_only=True)


def pleated_base(b):
	"""The pleated base: a bell of cloth in deep folds that stands out from the hip, worn over leg harness."""
	cloth(b, 8.0, flare=0.9)


def embroidered_skirt(b):
	"""The embroidered skirt: the cloth skirt with a hem and a waistband in a second colour (the embroidery)."""
	cloth(b, 7.0, band=True)


# ---- thighs (THIGHS), shins (SHINS) and feet ---------------------------------------------------------------------

def fluted_cuisses(b):
	"""The fluted cuisses: Maximilian plate round each thigh in ridges down its length, a roped lame at the top, and the poleyn
	with its side wing."""
	bones(b, "legs")
	leg = "armor{}Leg"
	fl = {s: flutes for s in g.SIDES}
	both(b, leg, (-0.4, 6.8, -2.5), (4.4, 11.6, 2.5), "plate", faces_paint=fl)
	both(b, leg, (0.6, 7.2, -2.9), (3.4, 11.2, -2.5), "plate", faces_paint={**fl, "up": plate})
	both(b, leg, (-0.45, 11.2, -2.55), (4.45, 11.9, 2.55), "plate", faces_paint=on_sides(lames(1)))
	both(b, leg, (-0.3, 4.8, -3.0), (4.3, 7.1, -1.8), "plate", faces_paint=P_ALL)
	both(b, leg, (4.3, 4.9, -1.6), (5.0, 6.9, 1.6), "plate", faces_paint=P_ALL)


def brigandine_cuisses(b):
	"""The brigandine cuisses: cloth round each thigh with plates riveted inside (the rivet heads in rows), a little plate poleyn."""
	bones(b, "legs")
	leg = "armor{}Leg"
	both(b, leg, (-0.4, 6.8, -2.6), (4.4, 11.8, 2.6), "brig", faces_paint=on_sides(studs))
	both(b, leg, (-0.5, 9.0, -2.7), (4.5, 9.6, 2.7), "strap")
	both(b, leg, (-0.2, 4.8, -3.0), (4.2, 7.1, -1.8), "plate", faces_paint=P_ALL)


def leather_cuisses(b):
	"""The leather leg pads: boiled leather round each thigh, a cop of it over the knee, buckled at the side."""
	bones(b, "legs")
	leg = "armor{}Leg"
	both(b, leg, (-0.4, 6.8, -2.6), (4.4, 11.6, 2.6), "strap", faces_paint=on_sides(leather_pad))
	both(b, leg, (-0.2, 4.8, -3.0), (4.2, 7.1, -1.8), "strap", faces_paint={**on_sides(leather_pad), "up": leather_pad})
	both(b, leg, (4.3, 8.4, -0.5), (4.7, 9.4, 0.5), "brass")


def mail_chausses(b):
	"""The mail chausses: leggings of mail from the thigh to the toe, laced behind the leg."""
	bones(b, "legs", "boots")
	leg = "armor{}Leg"
	boot = "armor{}Boot"
	r_ = on_sides(rings)
	both(b, leg, (-0.3, 6.0, -2.45), (4.3, 11.4, 2.45), "mail", faces_paint={**r_, "up": rings})
	both(b, leg, (-0.35, 4.6, -2.5), (4.35, 6.2, 2.5), "mail", faces_paint=r_)
	both(b, leg, (-0.3, 1.6, -2.4), (4.3, 4.8, 2.4), "mail", faces_paint=r_)
	both(b, boot, (-0.3, 0, -3.1), (4.3, 1.8, 2.4), "mail", faces_paint={**r_, "up": rings})
	both(b, leg, (1.2, 1.8, 2.5), (2.6, 10.8, 2.7), "strap")


def schynbald(b):
	"""The schynbald: a plate strapped over the front of each shin only, with its ridge, and buckled behind the calf."""
	bones(b, "legs")
	leg = "armor{}Leg"
	both(b, leg, (0.0, 1.6, -2.9), (3.8, 5.4, -2.4), "plate", faces_paint={**P_ALL, "north": shin_ridge})
	both(b, leg, (-0.4, 1.8, -2.4), (4.4, 5.2, -0.2), "plate", faces_paint=P)
	both(b, leg, (-0.45, 2.0, 0.1), (4.45, 2.6, 2.5), "strap")
	both(b, leg, (-0.45, 4.0, 0.1), (4.45, 4.6, 2.5), "strap")


def leg_wraps(b):
	"""The leg wraps: bands of cloth wound round the calf from the ankle to below the knee."""
	bones(b, "legs")
	leg = "armor{}Leg"
	for i in range(6):
		y0 = 0.8 + i * 0.85
		d = 0.06 * (i % 2)
		both(b, leg, (-0.25 - d, y0, -2.25 - d), (4.25 + d, y0 + 0.95, 2.25 + d), "cloth", faces_paint={**on_sides(binding), "up": binding})


def scale_sabatons(b):
	"""The scale sabatons: the sabatons' point and ankle plate in overlapping scales."""
	bones(b, "boots")
	boot = "armor{}Boot"
	sc = on_sides(scales)
	both(b, boot, (-0.3, 0, -3.2), (4.3, 2.2, 2.4), "plate", faces_paint={**sc, "up": scales})
	both(b, boot, (0.5, 0, -4.4), (3.5, 1.6, -3.2), "plate", faces_paint={**sc, "up": scales})
	both(b, boot, (1.3, 0, -5.3), (2.7, 1.1, -4.4), "plate", faces_paint={**sc, "up": scales})
	both(b, boot, (-0.4, 2.2, -2.5), (4.4, 3.6, 2.5), "plate", faces_paint=sc)


def mail_shoes(b):
	"""The mail shoes: a foot of mail over the whole foot and ankle, a strap at the instep."""
	bones(b, "boots")
	boot = "armor{}Boot"
	r_ = on_sides(rings)
	both(b, boot, (-0.3, 0, -3.2), (4.3, 2.2, 2.4), "mail", faces_paint={**r_, "up": rings})
	both(b, boot, (-0.4, 2.0, -2.5), (4.4, 3.6, 2.5), "mail", faces_paint=r_)
	both(b, boot, (-0.35, 1.2, -1.2), (4.35, 1.8, -0.6), "strap")


# ---- hose (HOSE): puffs and bags hung with the sheet, and the sheets on the under-armour layer's legs ---------------

def baggy_bulge(b):
	"""Baggy breeches: the cloth bagging out over the thigh, gathered in at the knee."""
	bones(b, "legs")
	both(b, "armor{}Leg", (-0.5, 6.6, -2.7), (4.5, 10.8, 2.7), "cloth", faces_paint={**on_sides(pleats), "up": pleats})


def puffs(b):
	"""The Landsknecht hose's puffs: the thighs puffed and slashed (the second colour pulled through), the left leg's a size more."""
	bones(b, "body", "legs")
	leg = "armor{}Leg"
	for side, s, grow in (("Right", 1, 0.0), ("Left", -1, 0.35)):
		x0, x1 = sorted((-0.4 * s - grow * s, 4.4 * s + grow * s))
		b.cube(leg.format(side), (x0, 7.4, -2.65 - grow), (x1, 11.0, 2.65 + grow), "cloth", faces_paint={**on_sides(lambda i, rc: slashed(i, rc)), "up": pleats})
		b.cube(leg.format(side), (x0, 7.2, -2.7 - grow), (x1, 8.2, 2.7 + grow), SECOND_MAT, faces_paint=on_sides(pleats))
		b.cube(leg.format(side), (x0 - 0.05, 10.6, -2.7 - grow), (x1 + 0.05, 11.2, 2.7 + grow), SECOND_MAT, faces_paint=on_sides(pleats))


def slashed(img, rect):
	h.slashes(img, rect)


def leg_sheet(paint, rows=12):
	"""A hose sheet: the right leg's box at (0, 16) on a 64x32 layer ({paint}(x, y, row) -> colour or None; row 0 is the hip, 11 the
	ankle); only its first {rows} rows."""
	img = Image.new("RGBA", (64, 32))
	for y in range(16, 32):
		for x in range(0, 16):
			if y < 20 and not 4 <= x < 12:
				continue
			row = y - 20
			if row >= rows:
				continue
			c = paint(x, y, row)
			if c is not None:
				img.putpixel((x, y), tuple(c[:3]) + (255,))
	return img


def weave(x, y, k=1.0):
	return shade(CLOTH, k * (0.94 if (x + y) % 2 else 1.0))


def front_leg(x, y):
	return 4 <= x < 8 and y >= 20


def shorts_paint(x, y, row):
	k = 0.8 if row == 3 else 1.0
	return weave(x, y, k)


def breeches_paint(x, y, row):
	k = 0.78 if row in (6, 5) and row == 6 else 1.0
	if row == 0:
		k *= 0.85
	return weave(x, y, k)


def hose_paint(x, y, row):
	k = 1.0
	if row == 0:
		k *= 0.85
	if row == 11:
		k *= 0.82
	return weave(x, y, k)


def riding_paint(x, y, row):
	k = 0.88 if row in (5, 6, 7) or row >= 10 else 1.0
	if front_leg(x, y) and row in (6, 7) and x in (5, 6):
		k *= 0.8
	if row == 0:
		k *= 0.85
	return weave(x, y, k)


def landsknecht_paint(x, y, row):
	if row <= 5 and (x + 2 * (row // 2)) % 4 in (1, 2) and row % 2 == 0 and x % 4 != 0:
		return None
	return weave(x, y, 0.9 if row == 0 else 1.0)


def landsknecht_fill(x, y, row):
	if row <= 5 and (x + 2 * (row // 2)) % 4 in (1, 2) and row % 2 == 0 and x % 4 != 0:
		return weave(x, y)
	return None


# piece -> (paint, second paint or None, rows, mode); mode: both (the one colour on both legs), split (the dye on the wearer's left leg
# and the second colour on the right), layered (the second colour showing through the slits of the dye's)
HOSE = {
	"shorts": (shorts_paint, None, 4, "both"), "breeches": (breeches_paint, None, 7, "both"), "baggy_breeches": (breeches_paint, None, 7, "both"),
	"linen_hose": (hose_paint, None, 12, "both"), "joined_hose": (hose_paint, hose_paint, 12, "split"),
	"riding_hose": (riding_paint, None, 12, "both"), "landsknecht_hose": (landsknecht_paint, landsknecht_fill, 12, "layered"),
}
SKIRTS = {"baggy_breeches": baggy_bulge, "landsknecht_hose": puffs}


def hose_sheet(name, which):
	paint, other, rows, mode = HOSE[name]
	return leg_sheet(paint if which == 0 else other, rows)


def pants_icon(out, paint):
	"""A 16x16 icon of a pair of hose: a waistband and two legs, outlined, drawn from {paint}(x, y) (None leaves a pixel clear)."""
	img = Image.new("RGBA", (16, 16))
	outline = (58, 40, 24)
	body = [(x, y) for y in range(2, 14) for x in range(4, 12) if y < 5 or x not in (7, 8)]
	for x, y in body:
		c = paint(x, y)
		if c is not None:
			put(img, x, y, c)
	for x, y in body:
		for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
			if (x + dx, y + dy) not in body and 0 <= x + dx < 16 and 0 <= y + dy < 16 and img.getpixel((x + dx, y + dy))[3] == 0:
				put(img, x + dx, y + dy, outline)
	os.makedirs(os.path.dirname(out), exist_ok=True)
	img.save(out)


def icon_paint(name, second):
	paint, other, rows, mode = HOSE[name]
	if second:
		use = other
	elif other and mode == "layered":
		use = lambda x, y, row: paint(x, y, row) or other(x, y, row)  # noqa: E731
	else:
		use = paint

	def at(x, y):
		# (the icon's legs are 4 wide and 12 tall: the sheet's front of the leg, x 4..8, y 20..31)
		col = 4 + ((x - 4) % 4 if x < 8 else (x - 8) % 4)
		return use(col, 20 + (y - 2), y - 2)
	return at


# ---- what there is ------------------------------------------------------------------------------------------------

PLATE = {"long_tassets": long_tassets, "fluted_cuisses": fluted_cuisses, "schynbald": schynbald, "scale_sabatons": scale_sabatons}
PLAIN = {"lamellar_skirt": lamellar_skirt, "leather_lamellar_skirt": leather_lamellar_skirt, "scale_skirt": scale_skirt, "brigandine_cuisses": brigandine_cuisses,
	"leather_cuisses": leather_cuisses, "mail_chausses": mail_chausses, "mail_shoes": mail_shoes}
CLOTH_MODELS = {"coat_of_plates_skirt": coat_of_plates_skirt, "padded_skirt": padded_skirt, "cloth_skirt": cloth_skirt, "short_skirt": short_skirt,
	"long_skirt": long_skirt, "apron": apron, "pleated_base": pleated_base, "embroidered_skirt": embroidered_skirt, "leg_wraps": leg_wraps}
# the colour an undyed piece shows and its second colour (HeraldryLayer / UnderArmourLayer read the same numbers)
UNDYED = {"coat_of_plates_skirt": (0x7A2E2A, None), "padded_skirt": (0xD8CCB0, None), "cloth_skirt": (0x6B5A44, None), "short_skirt": (0x6E7F4A, None),
	"long_skirt": (0x4A5568, None), "apron": (0xCEBE96, None), "pleated_base": (0x8E2B2B, None), "embroidered_skirt": (0x2B4A8E, 0xE8C83A),
	"leg_wraps": (0xB8A878, None), "shorts": (0xB8A878, None), "breeches": (0x6B5A44, None), "baggy_breeches": (0x7A6A48, None),
	"linen_hose": (0xECE6D6, None), "joined_hose": (0x8E2B2B, 0x2B4A8E), "riding_hose": (0x5E4A32, None), "landsknecht_hose": (0x2B4A8E, 0xD8B034)}
NAMES = {
	"long_tassets": "Long Tassets", "coat_of_plates_skirt": "Coat-of-Plates Skirt", "lamellar_skirt": "Lamellar Skirt",
	"leather_lamellar_skirt": "Leather Lamellar Skirt", "padded_skirt": "Padded Skirt", "cloth_skirt": "Cloth Skirt", "short_skirt": "Short Skirt",
	"long_skirt": "Long Skirt", "apron": "Apron", "pleated_base": "Pleated Base", "embroidered_skirt": "Embroidered Skirt", "scale_skirt": "Scale Skirt",
	"fluted_cuisses": "Fluted Cuisses", "brigandine_cuisses": "Brigandine Cuisses", "leather_cuisses": "Leather Cuisses", "mail_chausses": "Mail Chausses",
	"schynbald": "Schynbald", "leg_wraps": "Leg Wraps", "scale_sabatons": "Scale Sabatons", "mail_shoes": "Mail Shoes",
	"shorts": "Shorts", "breeches": "Breeches", "baggy_breeches": "Baggy Breeches", "linen_hose": "Linen Hose", "joined_hose": "Joined Hose",
	"riding_hose": "Riding Hose", "landsknecht_hose": "Slashed Hose",
}
WOOL, STRING, LEATHER, FEATHER, NUGGET = h.WOOL, h.STRING, h.LEATHER, h.FEATHER, h.NUGGET
IRON, IRON_NUGGET = "minecraft:iron_ingot", "minecraft:iron_nugget"
RECIPES = {
	"coat_of_plates_skirt": [WOOL, WOOL, WOOL, IRON, IRON, STRING], "leather_lamellar_skirt": [LEATHER, LEATHER, LEATHER, LEATHER, LEATHER, STRING, STRING],
	"leather_cuisses": [LEATHER, LEATHER, LEATHER, LEATHER, STRING, STRING], "padded_skirt": [WOOL, WOOL, WOOL, WOOL, STRING, STRING, STRING],
	"cloth_skirt": [WOOL, WOOL, WOOL, LEATHER], "short_skirt": [WOOL, WOOL, STRING, LEATHER, LEATHER], "long_skirt": [WOOL, WOOL, WOOL, WOOL, LEATHER],
	"apron": [LEATHER, LEATHER, LEATHER, STRING], "pleated_base": [WOOL, WOOL, WOOL, WOOL, STRING, STRING, NUGGET],
	"leg_wraps": [STRING, STRING, STRING, LEATHER], "shorts": [STRING, STRING, STRING, WOOL], "breeches": [STRING, STRING, STRING, STRING, WOOL],
	"baggy_breeches": [STRING, STRING, STRING, STRING, WOOL, WOOL], "linen_hose": [STRING, STRING, STRING, STRING, STRING, STRING],
	"riding_hose": [STRING, STRING, STRING, STRING, LEATHER],
}
SECOND_RECIPES = {"embroidered_skirt": [WOOL, WOOL, WOOL, STRING, NUGGET, NUGGET, NUGGET], "joined_hose": [STRING, STRING, STRING, STRING, WOOL, WOOL, WOOL],
	"landsknecht_hose": [STRING, STRING, STRING, WOOL, WOOL, WOOL, LEATHER]}
DYE_RECIPES = ("coat_of_plates_skirt", "padded_skirt", "cloth_skirt", "short_skirt", "long_skirt", "apron", "pleated_base", "embroidered_skirt", "leg_wraps",
	"shorts", "breeches", "baggy_breeches", "linen_hose", "joined_hose", "riding_hose", "landsknecht_hose")


def main(argv):
	preview = argv[argv.index("--preview") + 1] if "--preview" in argv else None
	finishes = [f for f in g.FINISHES if f != "covered"]
	for name, build in PLATE.items():
		icons = {}
		for finish in finishes:
			b, model = g.make(build, 64, finish)
			if finish == "":
				g.bake(model, ASSETS, "heraldry_" + name)
			g.entity_texture(b.img, "heraldry_" + name + ("_" + finish if finish else ""))
			icons[finish] = name + ("_" + finish if finish else "")
			h.icon(b, b.img, icons[finish])
		g.item(name, {"type": "minecraft:select", "property": "minecraft:component", "component": "jaymoskingdoms:finish",
			"cases": [{"when": f, "model": g.flat(icons[f])} for f in finishes if f], "fallback": g.flat(icons[""])})
		h.dents(name)
	for name, build in PLAIN.items():
		b, model = g.make(build, 64)
		g.bake(model, ASSETS, "heraldry_" + name)
		g.entity_texture(b.img, "heraldry_" + name)
		h.icon(b, b.img, name)
		g.item(name, g.flat(name))
		if name == "mail_shoes":
			# (the boots slot's pieces count as plate, so the game looks for their dents: a mail foot has none to show)
			for stage in (1, 2):
				g.entity_texture(Image.new("RGBA", b.img.size, (0, 0, 0, 0)), "heraldry_%s_dents%d" % (name, stage))
	for name, build in CLOTH_MODELS.items():
		write_cloth(name, build)
	for name in HOSE:
		write_hose(name)
	for name, build in SKIRTS.items():
		b, model = g.make(build, 128)
		g.bake(model, ASSETS, "heraldry_" + name)
		bd.write_cloth_sheets(name, b, name)
	for name, ingredients in RECIPES.items():
		h.recipe(name, ingredients)
	for name, ingredients in SECOND_RECIPES.items():
		for dye, rgb in h.DYES.items():
			h.recipe(name, ingredients + ["minecraft:%s_dye" % dye], {"count": 1, "id": "jaymoskingdoms:" + name,
				"components": {"jaymoskingdoms:second_colour": rgb}}, "_" + dye)
	for name in DYE_RECIPES:
		g.dye_recipe(name)
	r.write(os.path.join(ASSETS, "lang", "parts", "heraldry-legs.json"), {**{"item.jaymoskingdoms." + n: t for n, t in NAMES.items()},
		"item.jaymoskingdoms.harness.hose": "Hose and breeches", "harness.jaymoskingdoms.slot.hose": "Hose"})
	if preview:
		previews(preview)
	print("heraldry legs:", len(NAMES), "pieces")


def write_cloth(name, build):
	"""{name}'s model and sheets (dyed cloth, what is drawn as it is, the second colour's), icon and item."""
	b, model = g.make(build, 128)
	g.bake(model, ASSETS, "heraldry_" + name)
	fixed = bd.write_cloth_sheets(name, b, name)
	second = SECOND_MAT in h.mats_of(b)
	base = h.split(b, DYED_MATS)
	layers = [name]
	h.icon(b, base, name)
	trim = Image.new("RGBA", b.img.size, (0, 0, 0, 0))
	if fixed:
		trim.alpha_composite(h.split(b, fixed))
	if second:
		trim.alpha_composite(g.tinted(h.split(b, (SECOND_MAT,)), UNDYED[name][1]))
	if fixed or second:
		h.icon(b, trim, name + "_trim")
		layers.append(name + "_trim")
	g.flat(name, layers)
	g.item(name, {"type": "minecraft:model", "model": "jaymoskingdoms:item/" + name, "tints": [{"type": "minecraft:dye", "default": UNDYED[name][0] - (1 << 24)}]})


def write_hose(name):
	paint, other, rows, mode = HOSE[name]
	os.makedirs(UNDER, exist_ok=True)
	hose_sheet(name, 0).save(os.path.join(UNDER, name + ".png"))
	if other:
		hose_sheet(name, 1).save(os.path.join(UNDER, name + "_second.png"))
	pants_icon(os.path.join(ASSETS, "textures", "item", name + ".png"), icon_paint(name, False))
	layers = [name]
	if other:
		trim = os.path.join(ASSETS, "textures", "item", name + "_trim.png")
		pants_icon(trim, icon_paint(name, True) if mode == "layered" else (lambda x, y, p=icon_paint(name, True): p(x, y) if x >= 8 else None))
		g.tinted(Image.open(trim).convert("RGBA"), UNDYED[name][1]).save(trim)
		layers.append(name + "_trim")
	g.flat(name, layers)
	g.item(name, {"type": "minecraft:model", "model": "jaymoskingdoms:item/" + name, "tints": [{"type": "minecraft:dye", "default": UNDYED[name][0] - (1 << 24)}]})


# ---- previews (not in the game's files) ----------------------------------------------------------------------------

def leg_cubes(b, right, left):
	"""The two hose sheets on the figure's legs: each leg 0.25 out from the skin, the right leg's sheet at (0, 16) and the left's
	laid out the same on its own corner (the game mirrors the one sheet; the pieces' per-leg colours are the preview's)."""
	for bone, origin in (("bipedRightLeg", (1.9, 12, 0)), ("bipedLeftLeg", (-1.9, 12, 0))):
		b.bone(bone, None, origin)
	b.bone("armorRightLeg", "bipedRightLeg", (1.9, 12, 0))
	b.bone("armorLeftLeg", "bipedLeftLeg", (-1.9, 12, 0))
	b.cx, b.cy, b.row = 0, 16, 0
	b.cube("armorRightLeg", (0, 0, -2), (4, 12, 2), "cloth", inflate=0.25)
	b.cx, b.cy, b.row = 0, 48, 0
	b.cube("armorLeftLeg", (-4, 0, -2), (0, 12, 2), "cloth", inflate=0.25)
	b.cx, b.cy, b.row = 0, 64, 0
	sheet = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
	sheet.alpha_composite(right, (0, 0))
	sheet.alpha_composite(left.crop((0, 16, 16, 32)), (0, 48 - 0))
	return sheet


def previews(out):
	os.makedirs(out, exist_ok=True)
	plate_pieces = {n: (bld, 64) for n, bld in PLATE.items()}
	for name, build in PLATE.items():
		for finish in ("", "gilded"):
			b, _ = g.make(build, 64, finish, figure=True)
			bd.preview_png(b, name + ("_" + finish if finish else ""), out)
	for name, build in PLAIN.items():
		b, _ = g.make(build, 64, figure=True)
		bd.preview_png(b, name, out)
	for name, build in CLOTH_MODELS.items():
		def over(b, build=build, name=name):
			made = b.bone
			b.bone = lambda n, parent, origin, rotation=g.Z: b.bones[n] if n in b.bones else made(n, parent, origin, rotation)
			build(b)
		b, _ = g.make(over, 128, figure=True)
		img = b.img.copy()
		img.alpha_composite(g.tinted(h.split(b, DYED_MATS), UNDYED[name][0]))
		if SECOND_MAT in h.mats_of(b):
			img.alpha_composite(g.tinted(h.split(b, (SECOND_MAT,)), UNDYED[name][1]))
		bd.preview_png(b, name, out, img)
	# a skirt and tassets over the thigh and shin plate, as they hang on a knight
	for name, build in (("pleated_base", pleated_base), ("long_tassets", long_tassets), ("scale_skirt", scale_skirt)):
		def worn(b, build=build):
			made = b.bone
			b.bone = lambda n, parent, origin, rotation=g.Z: b.bones[n] if n in b.bones else made(n, parent, origin, rotation)
			g.cuisses(b)
			g.greaves(b)
			g.sabatons(b)
			build(b)
		b, _ = g.make(worn, 128, figure=True)
		img = b.img.copy()
		if name in UNDYED:
			img.alpha_composite(g.tinted(h.split(b, DYED_MATS), UNDYED[name][0]))
		bd.preview_png(b, name + "_over_leg_harness", out, img)
	for name in HOSE:
		paint, other, rows, mode = HOSE[name]
		colours = UNDYED[name]
		base = g.tinted(hose_sheet(name, 0), colours[0])
		second = g.tinted(hose_sheet(name, 1), colours[1]) if other else None
		right, left = base, base
		if mode == "split":
			left, right = base, second
		elif mode == "layered":
			base = base.copy()
			base.alpha_composite(second)
			left = right = base
		skirt = SKIRTS.get(name)

		def build(b, skirt=skirt, r_=right, l_=left):
			sheet = leg_cubes(b, r_, l_)
			b.preview_sheet = sheet
			if skirt:
				made = b.bone
				b.bone = lambda n, parent, origin, rotation=g.Z: b.bones[n] if n in b.bones else made(n, parent, origin, rotation)
				skirt(b)
		b, _ = g.make(build, 128, figure=True)
		img = b.img.copy()
		for y in range(0, 128):
			for x in range(0, 16):
				if y in range(16, 32) or y in range(48, 64):
					img.putpixel((x, y), (0, 0, 0, 0))
		img.alpha_composite(b.preview_sheet, (0, 0))
		if skirt:
			cloth_part = g.tinted(h.split(b, DYED_MATS), colours[0])
			img.alpha_composite(cloth_part.crop((0, 64, b.img.width, b.img.height)), (0, 64))
			if SECOND_MAT in h.mats_of(b):
				img.alpha_composite(g.tinted(h.split(b, (SECOND_MAT,)), colours[1]).crop((0, 64, b.img.width, b.img.height)), (0, 64))
		bd.preview_png(b, name, out, img)


if __name__ == "__main__":
	main(sys.argv[1:])
