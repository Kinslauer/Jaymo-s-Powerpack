"""
The harness's body pieces from Andy's Mordhau picks (Mordhau harness 3): our own cubes and paint of the historical armour each
pick is based on (Mordhau's meshes, textures and names belong to Triternion and none of them is used here). Built the way
gen_heraldry_armour.py and gen_heraldry_hoods.py build the rest of the harness, with their painters and helpers, so only this
file needs running for these pieces (run it after those two: it adds a "covered" case to the item files of the plate pieces
that can be covered, which they write afresh):

- padding (PADDING, drawn as a sheet on the under-armour layer, UnderArmourLayer): the chequered gambeson (two colours in
  squares), the linen shirt, the wool tunic (with a short skirt), the cloth vest, the leather jack, the slashed doublet (a
  second colour showing through its slits, and puffed sleeves hung with the sheet as the slashed hose's puffs are);
- mail (MAIL): the long hauberk (full sleeves on the sheet, its skirt to the knee a model), mail with plates, the scale
  shirt, and the quilted coat's sheet and long skirt (it was drawn as a haubergeon till now);
- cuirasses (the chest slot): the Gothic (waisted, cusped plackart, fan fluting), the kastenbrust (a box chest and a long
  fauld), the peascod (centre ridge, belly drawn down to a point), the lamed hussar breastplate (brass edged), the muscle
  cuirass, the embossed parade cuirass, and the brigandine coat (dense rivet rows under cloth);
- the covered finish: cloth over the plate in the colour of the wool it was covered with (cuirasses and the sallets and the
  flat-topped helm);
- over-garments (OVER): the long surcoat and its torn twin, the monk's robe, the kaftan, the jester's motley (two colours),
  the sash;
- shoulders (SHOULDERS): the large pauldrons and the ailettes (which take the realm's arms as the tabard does); hands
  (HANDS): the mitten gauntlets.

A dyed piece is painted in sheets on the same cubes (as gen_heraldry_hoods.py's): heraldry_<piece> (pale cloth, tinted with its
dye), heraldry_<piece>_fixed (gilt, rope, bells: drawn as they are) and, for two-coloured pieces, heraldry_<piece>_second
(tinted with jaymoskingdoms:second_colour, which the dye given when it is made sets). A sheet on the under-armour layer is
textures/entity/under_armour/<piece>.png (+ _second).

Run from the project root: python3 -I kingdoms/tools/gen_heraldry_body.py [--preview <dir>]   (needs Pillow)
"""
import json
import math
import os
import subprocess
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image  # noqa: E402

import gen_heraldry_armour as g  # noqa: E402
import gen_heraldry_hoods as h  # noqa: E402
import gen_reverent as r  # noqa: E402
from gen_heraldry_armour import (P, P_ALL, both, bones, clear, keel, lames, mul, on_sides, pleats, put, quilting, rings, shade,  # noqa: E402
	studs, flutes, plate)

ASSETS, DATA, ROOT = g.ASSETS, g.DATA, g.ROOT
UNDER = os.path.join(ASSETS, "textures", "entity", "under_armour")
# the paint these pieces add
g.FIXED.update({"scale": (150, 160, 176), "jack": (126, 88, 54), "rope": (206, 192, 150), "brass": (200, 158, 64), "bell": (224, 184, 70)})
# the covered finish: cloth over the plate (pale, so the dye tints it), studs of the same cloth a shade lighter
COVERED = {"plate": (236, 236, 236), "edge": (255, 255, 255), "dark": (150, 150, 150), "rivet": (252, 252, 252)}
g.FINISHES["covered"] = COVERED
DYED_MATS, SECOND_MAT = h.DYED_MATS, h.SECOND_MAT


# ---- painters ---------------------------------------------------------------------------------------------------

def fan(img, rect):
	"""Gothic fluting: ridges fanning out from the foot of the plate, bright and dark by turns."""
	plate(img, rect)
	x0, y0, x1, y1 = rect
	mid = (x0 + x1 - 1) / 2
	for y in range(y0 + 1, y1 - 1):
		for x in range(x0 + 1, x1 - 1):
			ang = math.atan2(x - mid, (y1 + 4) - y)
			mul(img, x, y, 1.26 if int((ang + 3) * 5) % 2 else 0.74)


def scrolls(img, rect):
	"""Embossing: scrollwork in relief, bright where it is raised, dark in its hollows, and a grotesque face in the middle."""
	plate(img, rect)
	x0, y0, x1, y1 = rect
	cx, cy = (x0 + x1 - 1) / 2, (y0 + y1 - 1) / 2
	for y in range(y0 + 1, y1 - 1):
		for x in range(x0 + 1, x1 - 1):
			wave = int(2.2 * math.sin((y - y0) * 0.85 + (x - x0) * 0.35))
			mul(img, x, y, 1.3 if (x - x0 + wave) % 5 == 0 else 0.78 if (x - x0 + wave) % 5 == 2 else 1.0)
	dark = g.kit.PALETTE["dark"]
	for ex in (-2, 1):
		put(img, int(cx + ex), int(cy) - 1, dark)
		put(img, int(cx + ex), int(cy), dark)
	for x in range(int(cx) - 2, int(cx) + 2):
		put(img, x, int(cy) + 2, dark)


def muscles(img, rect):
	"""A muscle cuirass's front: the swell and its shadow, the line down the middle."""
	plate(img, rect)
	x0, y0, x1, y1 = rect
	mid = (x0 + x1) // 2
	for y in range(y0 + 1, y1 - 1):
		mul(img, mid, y, 0.7)
		mul(img, mid - 1, y, 1.2)
	for x in range(x0 + 1, x1 - 1):
		mul(img, x, (y0 + y1) // 2, 0.72)


def studded(img, rect):
	"""A brigandine's cloth: the rivet heads close set, row on row."""
	x0, y0, x1, y1 = rect
	for y in range(y0 + 1, y1 - 1, 2):
		for x in range(x0 + 1, x1 - 1, 2):
			put(img, x, y, g.kit.PALETTE["rivet"])
			mul(img, x, y + 1, 0.8)


def board(img, rect):
	"""An ailette: a flat board, a darker edge all round and the field inside it for the realm's arms."""
	x0, y0, x1, y1 = rect
	for x in range(x0, x1):
		mul(img, x, y0, 0.7)
		mul(img, x, y1 - 1, 0.7)
	for y in range(y0, y1):
		mul(img, x0, y, 0.7)
		mul(img, x1 - 1, y, 0.7)


def dagged(img, rect):
	"""Cloth in folds, its lower edge cut in a point."""
	pleats(img, rect)
	x0, y0, x1, y1 = rect
	mid = (x0 + x1 - 1) / 2
	for x in range(x0, x1):
		for y in range(y1 - int(abs(x - mid) * 0.8), y1):
			clear(img, x, y)


def sash_cloth(img, rect):
	x0, y0, x1, y1 = rect
	for y in range(y0, y1):
		for x in range(x0, x1):
			mul(img, x, y, 0.92 if (x + y) % 2 else 1.0)
	for x in range(x0, x1):
		mul(img, x, y1 - 1, 0.78)


# ---- cuirasses (the chest slot) ---------------------------------------------------------------------------------

def tassets(b, o="armorBody"):
	"""The fauld's lames and a tasset before each thigh, as the cuirass's own."""
	b.cube(o, (-4.3, 13, -2.6), (4.3, 14.8, 2.6), "plate", faces_paint=P)
	b.cube(o, (-4.7, 11.4, -3), (4.7, 13.2, 3), "plate", faces_paint=P)
	b.cube(o, (-5, 9.8, -3.2), (5, 11.6, 3.2), "plate", faces_paint=P)
	both(b, o, (0.4, 6.4, -3.5), (4.4, 10, -2.8), "plate", faces_paint={**P, "north": lames(2)})


def gothic_cuirass(b):
	"""The Gothic cuirass: a breast that swells over a narrow waist, a separate plackart below it cut in three cusped points,
	rippled fluting fanning out over both, and a fauld of lames."""
	bones(b, "body")
	o = "armorBody"
	b.cube(o, (-4.4, 18.8, -2.7), (4.4, 23.6, 2.7), "plate", faces_paint={**P, "north": fan, "south": lames(4, 5)})
	b.cube(o, (-4.0, 19.6, -3.8), (4.0, 23.0, -2.7), "plate", faces_paint={**P_ALL, "north": fan})
	b.cube(o, (-3.5, 15.6, -2.6), (3.5, 18.8, 2.6), "plate", faces_paint={**P, "south": lames(3, 3)})
	b.cube(o, (-3.3, 16.0, -3.3), (3.3, 19.4, -2.6), "plate", faces_paint={**P_ALL, "north": fan})
	b.cube(o, (-1.2, 13.0, -3.4), (1.2, 16.0, -2.7), "plate", faces_paint={**P_ALL, "north": keel})
	both(b, o, (1.2, 14.6, -3.4), (3.4, 16.0, -2.7), "plate", faces_paint=P_ALL)
	b.cube(o, (-4.3, 13, -2.6), (4.3, 15.6, 2.6), "plate", faces_paint=P)
	b.cube(o, (-4.7, 11.4, -3), (4.7, 13.2, 3), "plate", faces_paint=P)
	b.cube(o, (-5, 9.8, -3.2), (5, 11.6, 3.2), "plate", faces_paint=P)
	both(b, o, (0.4, 6.4, -3.5), (4.4, 10, -2.8), "plate", faces_paint={**P, "north": lames(2)})
	both(b, o, (2.2, 23.6, -1.6), (4.2, 24.4, 1.6), "strap")


def kastenbrust(b):
	"""The kastenbrust: a flat-fronted box of a chest with a sharp angle at the waist, then a long laminated fauld."""
	bones(b, "body")
	o = "armorBody"
	b.cube(o, (-4.5, 16.8, -2.7), (4.5, 23.8, 2.7), "plate", faces_paint={**P, "south": lames(4, 5)})
	b.cube(o, (-4.5, 17.0, -4.1), (4.5, 23.6, -2.7), "plate", faces_paint={**P_ALL, "north": lambda i, r: (plate(i, r), keel(i, r))[1]})
	b.cube(o, (-4.1, 14.2, -3.6), (4.1, 17.0, -2.6), "plate", faces_paint=P_ALL)
	b.cube(o, (-3.7, 12.6, -3.2), (3.7, 14.4, -2.6), "plate", faces_paint=P)
	b.cube(o, (-4.4, 12.6, 2.4), (4.4, 16.8, 3.0), "plate", faces_paint=P)
	for i, (y0, w) in enumerate(((11.4, 4.8), (9.8, 5.0), (8.2, 5.2), (6.6, 5.4))):
		b.cube(o, (-w, y0, -3.4 - i * 0.05), (w, y0 + 1.8, 3.4 + i * 0.05), "plate", faces_paint=P)
	both(b, o, (0.4, 5.2, -3.7), (4.6, 7.0, -3.0), "plate", faces_paint={**P, "north": lames(1)})
	both(b, o, (2.2, 23.6, -1.6), (4.2, 24.4, 1.6), "strap")


def peascod_cuirass(b):
	"""The peascod cuirass: a breast with a ridge down its middle and the belly drawn down over the waist to a point."""
	bones(b, "body")
	o = "armorBody"
	b.cube(o, (-4.4, 14.8, -2.7), (4.4, 23.6, 2.7), "plate", faces_paint={**P, "south": lames(4, 5)})
	b.cube(o, (-3.9, 17.6, -3.6), (3.9, 22.8, -2.7), "plate", faces_paint=P_ALL)
	b.cube(o, (-0.9, 16.4, -4.5), (0.9, 22.4, -3.6), "plate", faces_paint={**P_ALL, "north": keel})
	b.cube(o, (-3.4, 14.6, -3.9), (3.4, 17.8, -2.7), "plate", faces_paint=P_ALL)
	b.cube(o, (-2.6, 12.6, -4.5), (2.6, 14.8, -2.9), "plate", faces_paint=P_ALL)
	b.cube(o, (-1.8, 11.0, -4.9), (1.8, 12.8, -3.4), "plate", faces_paint=P_ALL)
	b.cube(o, (-0.8, 9.6, -5.2), (0.8, 11.2, -3.9), "plate", faces_paint=P_ALL)
	b.cube(o, (-4.3, 13, -2.6), (4.3, 14.8, 2.6), "plate", faces_paint=P)
	b.cube(o, (-4.7, 11.4, -3), (4.7, 13.2, 3), "plate", faces_paint=P)
	b.cube(o, (-5, 9.8, 0.6), (5, 11.6, 3.2), "plate", faces_paint=P)
	both(b, o, (3.0, 6.8, -3.2), (5.0, 10.0, -2.2), "plate", faces_paint={**P, "north": lames(2)})
	both(b, o, (2.2, 23.6, -1.6), (4.2, 24.4, 1.6), "strap")


def hussar_cuirass(b):
	"""The lamed hussar breastplate: a breast and its belly built of overlapping horizontal lames, edged in brass at the neck, the
	arms and each lame."""
	bones(b, "body")
	o = "armorBody"
	b.cube(o, (-4.4, 14.8, -2.7), (4.4, 23.6, 2.7), "plate", faces_paint={**P, "north": lames(2), "south": lames(2)})
	b.cube(o, (-3.9, 16.6, -3.5), (3.9, 22.6, -2.7), "plate", faces_paint={**P_ALL, "north": lames(2)})
	for i, y0 in enumerate((13.0, 11.4, 9.8)):
		w = 4.6 + 0.2 * i
		b.cube(o, (-w, y0, -3.3 - 0.1 * i), (w, y0 + 2.0, 3.3 + 0.1 * i), "plate", faces_paint={**P, "north": lames(2)})
		b.cube(o, (-w - 0.05, y0 - 0.1, -3.4 - 0.1 * i), (w + 0.05, y0 + 0.5, 3.4 + 0.1 * i), "brass")
	b.cube(o, (-3.2, 23.4, -3.1), (3.2, 24.2, -2.6), "brass")
	both(b, o, (4.3, 19.6, -2.0), (4.8, 23.0, 2.0), "brass")
	both(b, o, (0.4, 6.4, -3.5), (4.4, 9.8, -2.8), "plate", faces_paint={**P, "north": lames(2)})
	both(b, o, (2.2, 23.6, -1.6), (4.2, 24.4, 1.6), "strap")


def muscle_cuirass(b):
	"""The muscle cuirass: the antique torso beaten in metal, the swell of each breast, the ridge down the belly and its rows,
	a lion's mask on the breastbone, and a skirt of leather straps below."""
	bones(b, "body")
	o = "armorBody"
	b.cube(o, (-4.4, 11.0, -2.7), (4.4, 23.6, 2.7), "plate", faces_paint={**P, "south": lames(4, 5)})
	both(b, o, (0.1, 19.4, -3.7), (4.1, 22.6, -2.7), "plate", faces_paint={**P_ALL, "north": muscles})
	both(b, o, (0.3, 16.6, -3.4), (3.5, 19.4, -2.7), "plate", faces_paint={**P_ALL, "north": muscles})
	both(b, o, (0.3, 13.8, -3.2), (3.3, 16.6, -2.7), "plate", faces_paint={**P_ALL, "north": muscles})
	both(b, o, (0.4, 11.4, -3.0), (3.0, 13.8, -2.7), "plate", faces_paint={**P_ALL, "north": muscles})
	b.cube(o, (-0.9, 19.0, -4.1), (0.9, 21.0, -3.6), "gilt")
	b.cube(o, (-0.5, 18.4, -4.3), (0.5, 19.2, -3.9), "gilt")
	b.cube(o, (-4.6, 10.6, -3.0), (4.6, 11.6, 3.0), "gilt")
	for i in range(7):
		x0 = -4.5 + i * 1.3
		b.cube(o, (x0, 7.4, -3.4), (x0 + 1.1, 10.8, -2.9), "strap")
		b.cube(o, (x0, 7.4, 2.9), (x0 + 1.1, 10.8, 3.4), "strap")
	both(b, o, (2.2, 23.6, -1.6), (4.2, 24.4, 1.6), "strap")


def embossed_cuirass(b):
	"""The embossed parade cuirass: the rounded breastplate of the Milanese, its whole front worked in scrolls and a grotesque
	face."""
	bones(b, "body")
	o = "armorBody"
	b.cube(o, (-4.4, 13, -2.7), (4.4, 23.6, 2.7), "plate", faces_paint={**P, "south": lames(4, 5)})
	b.cube(o, (-4.0, 14.6, -3.4), (4.0, 22.6, -2.7), "plate", faces_paint={**P_ALL, "north": scrolls})
	b.cube(o, (-3.2, 15.6, -3.9), (3.2, 21.6, -3.4), "plate", faces_paint={**P_ALL, "north": scrolls})
	b.cube(o, (-2.0, 16.8, -4.2), (2.0, 20.4, -3.9), "plate", faces_paint={**P_ALL, "north": scrolls})
	b.cube(o, (-4.7, 11.4, -3), (4.7, 13.2, 3), "plate", faces_paint={**P, "north": scrolls})
	b.cube(o, (-5, 9.8, -3.2), (5, 11.6, 3.2), "plate", faces_paint={**P, "north": scrolls})
	b.cube(o, (-4.8, 23.4, -3.0), (4.8, 24.0, 3.0), "gilt")
	both(b, o, (2.2, 23.6, -1.6), (4.2, 24.4, 1.6), "strap")


def brigandine_coat(b):
	"""The brigandine coat: the coat of plates' cut, the cloth closer set with rivet heads, row on row, belted."""
	bones(b, "body")
	o = "armorBody"
	b.cube(o, (-4.5, 10.5, -2.9), (4.5, 24, 2.9), "cloth", faces_paint=on_sides(studded))
	both(b, o, (1.8, 23.8, -2.9), (4.5, 24.6, 2.9), "cloth")
	b.cube(o, (-4.7, 12.2, -3.1), (4.7, 13.2, 3.1), "strap")
	b.cube(o, (-0.7, 12.0, -3.3), (0.7, 13.4, -3.1), "brass")
	b.cube(o, (-4.7, 9.6, -3.1), (4.7, 10.8, 3.1), "cloth", faces_paint=on_sides(pleats))


# ---- shoulders and hands ----------------------------------------------------------------------------------------

def pauldrons(b):
	"""The large pauldrons: lames over each shoulder, deep as the upper arm, and more lames wrapping over the breast and the
	back, a standing guard at the neck."""
	bones(b, "body", "arms")
	o = "armorBody"
	a = "armor{}Arm"
	lame = b.lame
	both(b, a, (3.4, 22.6, -3.4), (9.8, 25.6, 3.4), b.mat, faces_paint=lame)
	both(b, a, (3.8, 20.8, -3.2), (10.0, 22.8, 3.2), b.mat, faces_paint=lame)
	both(b, a, (4.2, 19.0, -3.0), (9.8, 21.0, 3.0), b.mat, faces_paint=lame)
	both(b, a, (4.6, 17.4, -2.8), (9.4, 19.2, 2.8), b.mat, faces_paint=lame)
	both(b, a, (3.9, 25.4, -3.0), (5.0, 28.2, 3.0), "plate", faces_paint=P_ALL, rotation=(0, 0, -10), origin=(4.4, 25.4, 0))
	both(b, o, (0.6, 21.4, -4.9), (4.9, 23.4, -3.9), "plate", faces_paint={**P_ALL, "north": lames(1)})
	both(b, o, (0.8, 19.4, -4.8), (4.7, 21.6, -3.8), "plate", faces_paint={**P_ALL, "north": lames(1)})
	both(b, o, (0.6, 21.4, 3.5), (4.9, 23.4, 4.5), "plate", faces_paint={**P_ALL, "south": lames(1)})
	both(b, o, (0.8, 19.4, 3.4), (4.7, 21.6, 4.4), "plate", faces_paint={**P_ALL, "south": lames(1)})
	both(b, a, (8.8, 15.4, -1.2), (9.8, 18.6, 1.2), "plate", faces_paint={"east": plate, "west": plate})


def ailettes(b):
	"""The ailettes: a flat board tied upright on each shoulder, broad face to the front, bearing the wearer's arms (the
	realm's ordinary, as the tabard's); a strap crosses each shoulder under it."""
	bones(b, "body", "arms")
	a = "armor{}Arm"
	faces = []
	for side, s in (("Right", 1), ("Left", -1)):
		x0, x1 = sorted((4.0 * s, 8.4 * s))
		e = b.cube(a.format(side), (x0, 24.6, -0.6), (x1, 30.6, 0.6), "cloth", faces_paint={"north": board, "south": board, "east": board, "west": board,
			"up": board})
		faces.append(e["faces"]["north"]["uv"])
		faces.append(e["faces"]["south"]["uv"])
	b.arms = faces
	both(b, a, (3.6, 23.6, -2.6), (8.6, 24.8, 2.6), "strap")


def mitten_gauntlets(b):
	"""The mitten gauntlets: a long flared cuff, and one set of lames over all four fingers, with the thumb beside them."""
	bones(b, "arms")
	a = "armor{}Arm"
	both(b, a, (3.2, 14.2, -2.8), (8.8, 16.0, 2.8), "plate", faces_paint=P_ALL)
	both(b, a, (3.5, 12.2, -2.5), (8.5, 14.4, 2.5), "plate", faces_paint=P_ALL)
	both(b, a, (3.6, 10.4, -2.4), (8.4, 12.4, 2.4), "plate", faces_paint={**on_sides(lames(1)), "down": plate, "up": plate})
	both(b, a, (3.8, 9.4, -2.2), (8.2, 10.6, 2.2), "plate", faces_paint={**on_sides(lames(1)), "down": plate})
	both(b, a, (4.6, 11.0, -3.6), (6.4, 13.4, -2.4), "plate", faces_paint=P_ALL)
	both(b, a, (8.4, 12, -2), (8.9, 13, 2), "rivet")


# ---- over-garments (OVER) ---------------------------------------------------------------------------------------

def surcoat(b):
	"""The long surcoat: the crusader's sleeveless coat, a panel before and behind from the shoulders to the calves, open at the
	sides, belted at the waist."""
	bones(b, "body", "legs")
	o = "armorBody"
	leg = "armor{}Leg"
	fold = b.fold
	b.cube(o, (-4.6, 12.0, -4.7), (4.6, 24.2, -4.3), "cloth", faces_paint={"north": fold, "south": fold})
	b.cube(o, (-4.6, 12.0, 3.7), (4.6, 24.2, 4.1), "cloth", faces_paint={"north": fold, "south": fold})
	both(b, o, (1.6, 24.0, -4.7), (4.6, 24.7, 4.1), "cloth")
	both(b, leg, (-0.3, 1.8, -4.3), (4.3, 12.2, -3.9), "cloth", faces_paint={"north": fold, "south": fold, "east": fold, "west": fold})
	both(b, leg, (-0.3, 1.8, 3.3), (4.3, 12.2, 3.7), "cloth", faces_paint={"north": fold, "south": fold, "east": fold, "west": fold})
	b.cube(o, (-4.9, 12.4, -4.9), (4.9, 13.6, 4.3), "strap")
	b.cube(o, (-0.8, 12.2, -5.1), (0.8, 13.8, -4.8), "brass")


def monks_robe(b):
	"""The monk's habit: a robe to the feet with wide sleeves hanging from the arms, a rope tied at the waist with its ends
	hanging before."""
	bones(b, "body", "arms", "legs")
	o = "armorBody"
	fold = b.fold
	b.cube(o, (-4.9, 12.2, -4.6), (4.9, 24.4, 4.2), "cloth", faces_paint={**on_sides(fold), "up": fold})
	both(b, "armor{}Leg", (-0.4, 0.4, -4.1), (4.4, 12.4, 3.7), "cloth", faces_paint={**on_sides(fold), "down": fold})
	both(b, "armor{}Arm", (3.4, 10.4, -3.6), (9.8, 23.0, 3.6), "cloth", faces_paint={**on_sides(fold), "up": fold})
	b.cube(o, (-5.1, 12.6, -4.8), (5.1, 13.8, 4.4), "rope")
	b.cube(o, (0.4, 8.0, -5.0), (1.2, 12.8, -4.6), "rope")
	b.cube(o, (-1.4, 9.0, -5.0), (-0.6, 12.8, -4.6), "rope")


def kaftan(b):
	"""The kaftan: a long robe worn open over armour, a gilt braid down both edges of the opening, side slits, and wide sleeves
	ending in gilt cuffs."""
	bones(b, "body", "arms", "legs")
	o = "armorBody"
	fold = b.fold
	b.cube(o, (-4.9, 12.0, 3.2), (4.9, 24.4, 4.2), "cloth", faces_paint={**on_sides(fold), "up": fold})
	both(b, o, (0.7, 12.0, -4.6), (4.9, 24.4, 3.4), "cloth", faces_paint={**on_sides(fold), "up": fold})
	both(b, o, (0.2, 12.0, -4.8), (0.8, 24.4, -4.4), "brass")
	both(b, o, (4.7, 12.0, -4.7), (5.0, 24.2, 3.5), "brass")
	both(b, "armor{}Leg", (-0.3, 0.6, -4.3), (4.3, 12.2, -3.7), "cloth", faces_paint={**on_sides(fold), "down": fold})
	both(b, "armor{}Leg", (-0.3, 0.6, 3.3), (4.3, 12.2, 3.7), "cloth", faces_paint={**on_sides(fold), "down": fold})
	both(b, "armor{}Leg", (-0.2, 0.4, -4.4), (4.2, 1.4, -3.6), "brass")
	both(b, "armor{}Arm", (3.5, 12.0, -3.4), (9.6, 22.8, 3.4), "cloth", faces_paint={**on_sides(fold), "up": fold})
	both(b, "armor{}Arm", (3.4, 11.2, -3.5), (9.7, 12.6, 3.5), "brass")


def jesters_motley(b):
	"""The jester's motley: a parti-coloured coat, the wearer's left side in the dye and the right in the second colour, its
	hem cut in dags that each carry a bell."""
	bones(b, "body", "arms")
	o = "armorBody"
	fold = b.fold
	b.cube(o, (0.0, 11.4, -4.6), (4.9, 24.4, 4.2), SECOND_MAT, faces_paint={**on_sides(fold), "up": fold})
	b.cube(o, (-4.9, 11.4, -4.6), (0.0, 24.4, 4.2), "cloth", faces_paint={**on_sides(fold), "up": fold})
	b.cube("armorRightArm", (3.5, 12.6, -3.2), (8.4, 23.0, 3.2), SECOND_MAT, faces_paint={**on_sides(fold), "up": fold})
	b.cube("armorLeftArm", (-8.4, 12.6, -3.2), (-3.5, 23.0, 3.2), "cloth", faces_paint={**on_sides(fold), "up": fold})
	for i in range(5):
		x0 = -4.9 + i * 1.96
		mat = SECOND_MAT if i % 2 == 0 else "cloth"
		b.cube(o, (x0, 8.4, -4.6), (x0 + 1.96, 11.6, 4.2), mat, faces_paint={**on_sides(dagged), "down": fold})
		b.cube(o, (x0 + 0.5, 7.4, -4.8), (x0 + 1.5, 8.6, -3.8), "bell")
		b.cube(o, (x0 + 0.5, 7.4, 3.4), (x0 + 1.5, 8.6, 4.4), "bell")
	b.cube(o, (-4.9, 12.2, -4.8), (4.9, 13.2, 4.4), "black")


def sash(b):
	"""The sash: a band of cloth across the chest from the right shoulder to the left hip, knotted there with two ends
	hanging."""
	bones(b, "body")
	o = "armorBody"
	steps = ((2.4, 22.6), (0.6, 20.6), (-1.2, 18.6), (-3.0, 16.6), (-4.4, 14.6))
	for x0, y0 in steps:
		b.cube(o, (x0, y0, -4.9), (x0 + 2.8, y0 + 2.6, -4.3), "cloth", faces_paint=on_sides(sash_cloth))
	for x0, y0 in steps[:-1]:
		b.cube(o, (x0 - 0.4, y0, 4.1), (x0 + 2.4, y0 + 2.6, 4.7), "cloth", faces_paint=on_sides(sash_cloth))
	b.cube(o, (-5.4, 13.4, -5.3), (-3.2, 15.4, -4.5), "cloth", faces_paint=on_sides(sash_cloth))
	b.cube(o, (-5.2, 9.6, -5.1), (-4.0, 13.6, -4.6), "cloth", faces_paint=on_sides(sash_cloth))
	b.cube(o, (-3.8, 10.4, -5.1), (-2.6, 13.6, -4.6), "cloth", faces_paint=on_sides(sash_cloth))


# ---- skirts that hang from the under-armour sheets (a model on the legs, hung with the sheet) ---------------------

def hauberk_skirt(b):
	"""The hauberk's skirt: mail hung from the hip down to the knee, split at the front and back for riding."""
	bones(b, "body", "legs")
	o = "armorBody"
	b.cube(o, (-4.7, 10.2, -2.9), (4.7, 12.6, 2.9), "mail", faces_paint=on_sides(rings))
	both(b, "armor{}Leg", (-0.3, 4.8, -2.45), (4.3, 10.4, 2.45), "mail", faces_paint=on_sides(rings))
	both(b, "armor{}Leg", (-0.3, 4.4, -2.5), (4.3, 5.2, 2.5), "mail", faces_paint={**on_sides(rings), "down": rings})


def tunic_skirt(b):
	"""The wool tunic's skirt: cloth hanging from the hip to mid-thigh."""
	bones(b, "body", "legs")
	o = "armorBody"
	fold = b.fold
	b.cube(o, (-4.7, 10.4, -2.85), (4.7, 12.4, 2.85), "cloth", faces_paint=on_sides(fold))
	both(b, "armor{}Leg", (-0.3, 6.6, -2.45), (4.3, 10.6, 2.45), "cloth", faces_paint={**on_sides(fold), "down": fold})


def quilted_skirt(b):
	"""The quilted coat's skirt: padded cloth, quilted in channels, hung to below the knee."""
	bones(b, "body", "legs")
	o = "armorBody"
	b.cube(o, (-4.7, 10.2, -2.9), (4.7, 12.6, 2.9), "cloth", faces_paint=on_sides(quilting))
	both(b, "armor{}Leg", (-0.3, 3.6, -2.45), (4.3, 10.4, 2.45), "cloth", faces_paint={**on_sides(quilting), "down": quilting})


def slashed(img, rect):
	h.slashes(img, rect)


def puffed_sleeves(b):
	"""The slashed doublet's puffed sleeves (the slashed hose's thigh puffs, on the arms): the sleeve puffed out from the shoulder to
	above the elbow and slashed through, the second colour pulled through the slits from a lining inside, gathered in at both ends in
	that colour; the left one a size fuller."""
	bones(b, "arms")
	arm = "armor{}Arm"
	for side, s, grow in (("Right", 1, 0.0), ("Left", -1, 0.35)):
		x0, x1 = sorted(((3.0 - grow) * s, (9.0 + grow) * s))
		b.cube(arm.format(side), (x0, 17.8, -3.2 - grow), (x1, 23.4, 3.2 + grow), "cloth", faces_paint={**on_sides(slashed), "up": pleats})
		b.cube(arm.format(side), (x0 + 0.4, 18.0, -2.7 - grow), (x1 - 0.4, 23.2, 2.7 + grow), SECOND_MAT, faces_paint=on_sides(pleats))
		b.cube(arm.format(side), (x0 - 0.05, 22.6, -3.25 - grow), (x1 + 0.05, 23.6, 3.25 + grow), SECOND_MAT, faces_paint=on_sides(pleats))
		b.cube(arm.format(side), (x0 - 0.05, 17.6, -3.25 - grow), (x1 + 0.05, 18.4, 3.25 + grow), SECOND_MAT, faces_paint=on_sides(pleats))


# ---- sheets on the under-armour layer (64x32: body from (16,16), the arm from (40,16)) ----------------------------

def sheet(paint, sleeves=True, until=32, arm_until=32):
	"""A garment's sheet: {paint}(x, y, arm) gives each pixel's colour (None leaves it bare); the arms unless not {sleeves}, the
	body to row {until} and the arms to {arm_until}."""
	img = Image.new("RGBA", (64, 32))
	for y in range(16, 32):
		for x in range(16, 56):
			arm = x >= 40
			if y < 20 and not (20 <= x < 36 or 44 <= x < 52) or arm and not sleeves or (y >= arm_until if arm else y >= until):
				continue
			c = paint(x, y, arm)
			if c is not None:
				img.putpixel((x, y), tuple(c[:3]) + (255,))
	return img


def front(x, y):
	return 20 <= x < 28 and y >= 20


CLOTH = g.FIXED["cloth"]


def linen_paint(x, y, arm=False):
	k = 0.94 if (x + y) % 2 else 1.0
	if y == 31:
		k *= 0.84
	if front(x, y) and y == 20 and 22 <= x < 26:
		k *= 0.72
	if front(x, y) and x in (23, 24) and y in (21, 22):
		k *= 0.8
	return shade(CLOTH, k)


def tunic_paint(x, y, arm=False):
	k = 0.88 if x % 3 == 0 else 1.0
	if y in (30, 31):
		k *= 0.8
	if front(x, y) and y == 20 and 22 <= x < 26:
		k *= 0.7
	if (x - 16) % 5 == 0 and not arm and y > 24:
		k *= 0.92
	return shade(CLOTH, k)


def vest_paint(x, y, arm=False):
	if y >= 30:
		return None
	k = 1.0 if (x + y) % 4 else 0.9
	if front(x, y) and x in (23, 24) and y >= 21:
		k *= 0.55
	if front(x, y) and y in (20, 21) and (x in (22, 25)):
		return None
	if front(x, y) and x in (22, 25) and y % 3 == 0:
		return g.FIXED["gilt"]
	return shade(CLOTH, k)


def jack_paint(x, y, arm=False):
	k = 0.86 if x % 3 == 0 else 1.0
	if y == 29:
		k *= 0.7
	jack = g.FIXED["jack"]
	if front(x, y) and x in (23, 24):
		return shade((214, 196, 150), 1.0 if y % 2 else 0.8) if y >= 21 else shade(jack, 0.6)
	if x in (20, 27, 32, 39) and y > 21:
		k *= 0.7
	return shade(jack, k)


def hauberk_paint(x, y, arm=False):
	k = 1.2 if (x + y) % 2 == 0 else 0.82
	if y == 31:
		k *= 0.7
	return shade(g.FIXED["mail"], k)


def plated_paint(x, y, arm=False):
	base = hauberk_paint(x, y, arm)
	back = 32 <= x < 40
	if (front(x, y) or back) and 21 <= y <= 27 and not arm:
		fx = (x - 20) % 12 if front(x, y) else (x - 32)
		if 1 <= fx <= 6:
			if (fx in (2, 5)) and y in (22, 26):
				return (222, 226, 232)
			return shade((172, 180, 190), 1.1 if y == 21 else 0.72 if y == 27 else 1.0)
	if arm and y == 26:
		return shade(g.FIXED["mail"], 0.6)
	return base


def scale_paint(x, y, arm=False):
	row = (y - 16) % 3
	off = (((y - 16) // 3) % 2) * 1
	col = (x + off) % 3
	base = g.FIXED["scale"]
	k = (1.18 if row == 0 else 1.0 if row == 1 else 0.7) * (0.9 if col == 1 else 1.0)
	if y == 31:
		k *= 0.8
	return shade(base, k)


def quilted_paint(x, y, arm=False):
	k = 1.0
	if (x + y) % 4 == 0:
		k = 0.8
	elif (x - y) % 4 == 0:
		k = 0.9
	if y == 31:
		k *= 0.82
	if front(x, y) and y == 20 and 22 <= x < 26:
		k *= 0.7
	return shade(CLOTH, k)


def cell(x, y):
	return (x // 3 + y // 3) % 2


def chequer_a(x, y, arm=False):
	return quilted_paint(x, y, arm) if cell(x, y) == 0 else None


def chequer_b(x, y, arm=False):
	return quilted_paint(x, y, arm) if cell(x, y) == 1 else None


def slit_cell(x, y):
	"""A slit: two rows tall, three wide, staggered row on row, down the body and the upper arms."""
	if y >= 30:
		return False
	return (x + 2 * ((y // 3) % 2)) % 4 in (1, 2) and y % 3 in (0, 1) and (x % 4 != 0)


def doublet_base(x, y, arm=False):
	if slit_cell(x, y) and not (front(x, y) and x in (23, 24)):
		return None
	k = 0.92 if (x + y) % 2 else 1.0
	if front(x, y) and x in (23, 24):
		k *= 0.8
	return shade(CLOTH, k)


def doublet_second(x, y, arm=False):
	return shade(CLOTH, 1.0) if slit_cell(x, y) and not (front(x, y) and x in (23, 24)) else None


# sheet pieces: name -> (paint, second paint or None, sleeves, until, arm_until)
SHEETS = {
	"chequered_gambeson": (chequer_a, chequer_b, True, 32, 32),
	"linen_shirt": (linen_paint, None, True, 32, 32),
	"wool_tunic": (tunic_paint, None, True, 32, 32),
	"cloth_vest": (vest_paint, None, False, 32, 32),
	"leather_jack": (jack_paint, None, False, 31, 32),
	"landsknecht_doublet": (doublet_base, doublet_second, True, 32, 32),
	"mail_hauberk": (hauberk_paint, None, True, 32, 32),
	"plated_mail": (plated_paint, None, True, 32, 27),
	"scale_shirt": (scale_paint, None, True, 32, 27),
	"quilted_coat": (quilted_paint, None, True, 32, 32),
}
# the colour an undyed piece shows and its second colour (UnderArmourLayer / HeraldryLayer read the same numbers); -1: drawn as painted
UNDYED = {"chequered_gambeson": (0x8E2B2B, 0xE8E0CC), "linen_shirt": (0xECE6D6, None), "wool_tunic": (0x6B5A44, None), "cloth_vest": (0x7A6A48, None),
	"landsknecht_doublet": (0x2B4A8E, 0xD8B034), "quilted_coat": (0xCEBE96, None), "surcoat": (0x2B4A8E, None), "torn_surcoat": (0x5A5246, None),
	"monks_robe": (0x5A4632, None), "kaftan": (0x2F5A7A, None), "jesters_motley": (0xB02E26, 0xE8C83A), "sash": (0xB02E26, None),
	"ailettes": (0x2B4A8E, None), "brigandine_coat": (0x7A2E2A, None)}
# the models hung with a sheet piece (skirts, and the slashed doublet's puffed sleeves)
SKIRTS = {"mail_hauberk": hauberk_skirt, "wool_tunic": tunic_skirt, "quilted_coat": quilted_skirt, "landsknecht_doublet": puffed_sleeves}


def shirt_icon(out, paint, sleeves=True):
	"""A 16x16 icon of a shirt: its shape drawn from {paint}(x, y) (None leaves a pixel clear), outlined."""
	img = Image.new("RGBA", (16, 16))
	outline = (58, 40, 24)
	body = [(x, y) for y in range(3, 14) for x in range(5, 11)] + ([(x, y) for y in range(3, 9) for x in (2, 3, 4, 11, 12, 13)] if sleeves else [])
	body = [p for p in body if p not in ((7, 3), (8, 3))]
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
	"""The 16x16 icon painter for a sheet piece: its sheet's pixels stretched over the icon's body and sleeves (the second
	colour's cells only when {second})."""
	paint, other, sleeves, until, arm_until = SHEETS[name]
	if second:
		use = other
	elif other:
		use = lambda x, y, arm: paint(x, y, arm) or other(x, y, arm)  # noqa: E731
	else:
		use = paint

	def at(x, y):
		# (the icon's body is 6 wide, 11 tall; its sleeves 3 wide, 6 tall)
		if x in (2, 3, 4, 11, 12, 13):
			return use(44 + (x - 2 if x < 8 else x - 11) % 3, min(20 + (y - 3) * 2, 31), True)
		return use(20 + (x - 5), 20 + int((y - 3) * 1.09), False)
	return at


# ---- what there is ------------------------------------------------------------------------------------------------

# cuirasses and a coat: piece -> (build, the plate finishes it also comes in beyond the forge's bright, blackened, gilded)
CUIRASSES = {"gothic_cuirass": gothic_cuirass, "kastenbrust": kastenbrust, "peascod_cuirass": peascod_cuirass,
	"hussar_cuirass": hussar_cuirass, "muscle_cuirass": muscle_cuirass, "embossed_cuirass": embossed_cuirass}
PLATE = {**CUIRASSES, "pauldrons": pauldrons, "mitten_gauntlets": mitten_gauntlets}
# cloth models: piece -> build (sheets: heraldry_<piece>, _fixed, _second)
CLOTH_MODELS = {"brigandine_coat": brigandine_coat, "surcoat": surcoat, "monks_robe": monks_robe, "kaftan": kaftan, "jesters_motley": jesters_motley,
	"sash": sash, "ailettes": ailettes}
TORN = {"torn_surcoat": ("surcoat", surcoat, {"fold": g.ragged(g.TORN_STRIPS, ((2, 5), (7, 9)))})}
# the plate that can be covered in cloth at the anvil (the cuirasses, the sallets and the flat-topped helm)
COVERABLE = ("plate_cuirass", "rounded_breastplate", "fluted_cuirass", "gothic_cuirass", "kastenbrust", "peascod_cuirass", "sallet", "kettle_sallet",
	"archers_sallet", "flat_top_helm")
NAMES = {
	"chequered_gambeson": "Chequered Gambeson", "linen_shirt": "Linen Shirt", "wool_tunic": "Wool Tunic", "cloth_vest": "Cloth Vest",
	"leather_jack": "Leather Jack", "landsknecht_doublet": "Slashed Doublet", "mail_hauberk": "Long Hauberk", "plated_mail": "Mail with Plates",
	"scale_shirt": "Scale Shirt", "gothic_cuirass": "Gothic Cuirass", "kastenbrust": "Kastenbrust", "peascod_cuirass": "Peascod Cuirass",
	"hussar_cuirass": "Lamed Hussar Breastplate", "muscle_cuirass": "Muscle Cuirass", "embossed_cuirass": "Embossed Parade Cuirass",
	"brigandine_coat": "Brigandine Coat", "surcoat": "Long Surcoat", "torn_surcoat": "Torn Surcoat", "monks_robe": "Monk's Habit",
	"kaftan": "Kaftan", "jesters_motley": "Jester's Motley", "sash": "Sash", "pauldrons": "Pauldrons", "ailettes": "Ailettes",
	"mitten_gauntlets": "Mitten Gauntlets",
}
WOOL, STRING, LEATHER, FEATHER, NUGGET = h.WOOL, h.STRING, h.LEATHER, h.FEATHER, h.NUGGET
IRON, IRON_NUGGET, PLANKS = "minecraft:iron_ingot", "minecraft:iron_nugget", "#minecraft:planks"
RECIPES = {
	"linen_shirt": [STRING, STRING, STRING, STRING, STRING], "wool_tunic": [WOOL, WOOL, WOOL, STRING], "cloth_vest": [WOOL, WOOL, WOOL],
	"leather_jack": [LEATHER, LEATHER, LEATHER, LEATHER, STRING], "brigandine_coat": [WOOL, WOOL, WOOL, IRON, IRON, IRON_NUGGET, IRON_NUGGET, IRON_NUGGET, IRON_NUGGET],
	"coat_of_plates": [WOOL, WOOL, WOOL, IRON, IRON, IRON, STRING], "surcoat": [WOOL, WOOL, WOOL, WOOL, STRING, STRING],
	"monks_robe": [WOOL, WOOL, WOOL, WOOL, WOOL, STRING, STRING], "kaftan": [WOOL, WOOL, WOOL, WOOL, STRING, STRING, NUGGET, NUGGET],
	"sash": [WOOL, WOOL, LEATHER, STRING], "ailettes": [PLANKS, PLANKS, LEATHER, STRING, WOOL],
}
# made with a dye that gives the second colour
SECOND_RECIPES = {"chequered_gambeson": [WOOL, WOOL, STRING, STRING, STRING], "landsknecht_doublet": [WOOL, WOOL, WOOL, LEATHER, STRING],
	"jesters_motley": [WOOL, WOOL, WOOL, WOOL, STRING, NUGGET, NUGGET]}
# (dyed, as the realm's colour) pieces the crafting table dyes
DYE_RECIPES = ("chequered_gambeson", "linen_shirt", "wool_tunic", "cloth_vest", "landsknecht_doublet", "brigandine_coat", "surcoat", "torn_surcoat",
	"monks_robe", "kaftan", "jesters_motley", "sash", "ailettes")


def make_sheet(name, which):
	paint, other, sleeves, until, arm_until = SHEETS[name]
	return sheet(paint if which == 0 else other, sleeves, until, arm_until)


def covered_texture(name, build, size):
	"""{name}'s covered sheet (cloth over the plate), the cover's icon, and its case in the item's file (its icon is pale, and the game
	tints it with the item's dye)."""
	b, model = g.make(build, size, "covered")
	g.entity_texture(b.img, "heraldry_%s_covered" % name)
	h.icon(b, b.img, name + "_covered")
	g.flat(name + "_covered")
	path = os.path.join(ASSETS, "items", name + ".json")
	with open(path, encoding="utf-8") as f:
		item = json.load(f)
	cases = item["model"]["cases"]
	if not any(c["when"] == "covered" for c in cases):
		cases.append(covered_case(name))
	r.write(path, item)


COVER_UNDYED = 0x7A2E3A


def covered_case(name):
	return {"when": "covered", "model": {"type": "minecraft:model", "model": "jaymoskingdoms:item/%s_covered" % name,
		"tints": [{"type": "minecraft:dye", "default": COVER_UNDYED - (1 << 24)}]}}


def main(argv):
	preview = argv[argv.index("--preview") + 1] if "--preview" in argv else None
	# plate pieces: one item, an icon and a sheet for each finish, picked by the piece's finish; dents drawn over it
	for name, build in PLATE.items():
		icons = {}
		for finish in [f for f in g.FINISHES if f != "covered" or name in COVERABLE]:
			b, model = g.make(build, 64, finish)
			if finish == "":
				g.bake(model, ASSETS, "heraldry_" + name)
			g.entity_texture(b.img, "heraldry_" + name + ("_" + finish if finish else ""))
			icons[finish] = name + ("_" + finish if finish else "")
			h.icon(b, b.img, icons[finish])
		cases = [{"when": f, "model": g.flat(icons[f])} for f in icons if f and f != "covered"]
		if name in COVERABLE:
			cases.append(covered_case(name))
		g.item(name, {"type": "minecraft:select", "property": "minecraft:component", "component": "jaymoskingdoms:finish",
			"cases": cases, "fallback": g.flat(icons[""])})
		h.dents(name)
	# the covered finish on the pieces the other generators made
	older = {**{n: (v[0], v[1]) for n, v in g.PLATE.items()}}
	for name in COVERABLE:
		if name in PLATE:
			g.flat(name + "_covered")
		else:
			covered_texture(name, *older[name])
	# the brigandine coat and the cloth over-garments
	for name, build in CLOTH_MODELS.items():
		write_cloth(name, build, 128, name, whole=name in WHOLE)
	for name, (shape, build, marks) in TORN.items():
		write_cloth(name, build, 128, shape, bake=False, **marks)
	# the ailettes' arms: one sheet for each realm ordinary, only the boards' broad faces painted (as the tabard's)
	b, _ = g.make(ailettes, 128)
	for i, paint in enumerate(g.ORDINARIES):
		img = Image.new("RGBA", b.img.size, (0, 0, 0, 0))
		for x0, y0, x1, y1 in b.arms:
			w, hh = x1 - x0, y1 - y0
			for v in range(hh):
				for u in range(w):
					if paint(u, v, w, hh):
						put(img, x0 + u, y0 + v, b.img.getpixel((x0 + u, y0 + v))[:3])
		g.entity_texture(img, "heraldry_ailettes_arms_%d" % i)
	# the sheets on the under-armour layer
	os.makedirs(UNDER, exist_ok=True)
	for name in SHEETS:
		base = make_sheet(name, 0)
		base.save(os.path.join(UNDER, name + ".png"))
		second = make_sheet(name, 1) if SHEETS[name][1] else None
		if second is not None:
			second.save(os.path.join(UNDER, name + "_second.png"))
		write_sheet_item(name, base, second)
	for name, build in SKIRTS.items():
		b, model = g.make(build, 128)
		g.bake(model, ASSETS, "heraldry_" + name)
		if SECOND_MAT in h.mats_of(b):
			# (two colours: the dyed cloth and the second colour's, each in a sheet of its own)
			write_cloth_sheets(name, b, name)
		else:
			g.entity_texture(b.img, "heraldry_" + name)
	# crafting
	for name, ingredients in RECIPES.items():
		h.recipe(name, ingredients)
	for name, ingredients in SECOND_RECIPES.items():
		for dye, rgb in h.DYES.items():
			h.recipe(name, ingredients + ["minecraft:%s_dye" % dye], {"count": 1, "id": "jaymoskingdoms:" + name,
				"components": {"jaymoskingdoms:second_colour": rgb}}, "_" + dye)
	for name in DYE_RECIPES:
		g.dye_recipe(name)
	r.write(os.path.join(ASSETS, "lang", "parts", "heraldry-body.json"), {**{"item.jaymoskingdoms." + n: t for n, t in NAMES.items()},
		"item.jaymoskingdoms.finish.covered": "Covered %s"})
	if preview:
		previews(preview)
	print("heraldry body:", len(NAMES), "pieces")


def write_cloth_sheets(name, b, model_name):
	"""{name}'s sheets from its built cubes: the dyed cloth, what is drawn as it is, the second colour's cloth."""
	mats = h.mats_of(b)
	g.entity_texture(h.split(b, DYED_MATS), "heraldry_" + name)
	fixed = mats - set(DYED_MATS) - {SECOND_MAT}
	if fixed:
		g.entity_texture(h.split(b, fixed), "heraldry_%s_fixed" % name)
	if SECOND_MAT in mats:
		g.entity_texture(h.split(b, (SECOND_MAT,)), "heraldry_%s_second" % name)
	return fixed


# the chest-slot cloth: one whole sheet in the dye (the armour slots draw no _fixed pass), as the coat of plates' is
WHOLE = ("brigandine_coat",)


def write_cloth(name, build, size, model_name, bake=True, whole=False, **marks):
	"""{name}'s sheets, icon and item; its model too unless it is {bake} False (a torn re-cut hangs on its whole twin's model)."""
	b, model = g.make(build, size, **marks)
	if bake:
		g.bake(model, ASSETS, "heraldry_" + name)
	if whole:
		g.entity_texture(b.img, "heraldry_" + name)
		fixed, second, base = set(), False, b.img
	else:
		fixed = write_cloth_sheets(name, b, model_name)
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


def write_sheet_item(name, base, second):
	"""The icon of a sheet piece (a shirt) and its item file: tinted by its dye if it takes one, as painted if it doesn't."""
	out = os.path.join(ASSETS, "textures", "item", name + ".png")
	sleeves = SHEETS[name][2]
	shirt_icon(out, icon_paint(name, False), sleeves)
	layers = [name]
	if second is not None:
		trim = os.path.join(ASSETS, "textures", "item", name + "_trim.png")
		# (the second colour's cells, in the colour an unmade one shows)
		shirt_icon(trim, lambda x, y, p=icon_paint(name, True): (lambda c: None if c is None else shade(c[:3], 1.0))(p(x, y)), sleeves)
		img = g.tinted(Image.open(trim).convert("RGBA"), UNDYED[name][1])
		img.save(trim)
		layers.append(name + "_trim")
	g.flat(name, layers)
	if name in UNDYED:
		g.item(name, {"type": "minecraft:model", "model": "jaymoskingdoms:item/" + name, "tints": [{"type": "minecraft:dye", "default": UNDYED[name][0] - (1 << 24)}]})
	else:
		g.item(name, g.flat(name))


# ---- previews (not in the game's files): each piece worn on a grey figure, from the front, the side and behind ------

def preview_png(b, tag, out, img=None):
	scratch = tempfile.mkdtemp()
	model = b.project("piece")
	g.bake(model, scratch, tag)
	(img or b.img).save(os.path.join(scratch, "textures", "entity", "bb", tag + ".png"))
	subprocess.run([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_preview.py"), scratch, tag, os.path.join(out, tag + ".png")],
		check=True, capture_output=True, env={**os.environ, "YAWS": VIEWS})


# the views of a preview, left to right: the front, the side, the back, three-quarter front and three-quarter back (degrees)
VIEWS = "180,90,0,150,30"


def sheet_cubes(b, sheet_img):
	"""The under-armour sheet on the figure: a body and two arms 0.5 out from the skin, mapped as the game's armour layer maps them (the
	body from (16,16), the arm from (40,16), the left arm mirrored)."""
	b.bone("bipedBody", None, (0, 24, 0))
	b.bone("armorBody", "bipedBody", (0, 24, 0))
	for side, s in (("Right", 1), ("Left", -1)):
		b.bone(f"biped{side}Arm", None, (s * 5, 22, 0))
		b.bone(f"armor{side}Arm", f"biped{side}Arm", (s * 5, 22, 0))
	b.cx, b.cy, b.row = 16, 16, 0
	b.cube("armorBody", (-4, 12, -2), (4, 24, 2), "cloth", inflate=0.5)
	for side, s in (("Right", 1), ("Left", -1)):
		b.cx, b.cy, b.row = 40, 16, 0
		x0, x1 = sorted((s * 4, s * 8))
		e = b.cube(f"armor{side}Arm", (x0, 12, -2), (x1, 24, 2), "cloth", inflate=0.5)
		if s < 0:
			for f in e["faces"].values():
				f["uv"] = [f["uv"][2], f["uv"][1], f["uv"][0], f["uv"][3]]
	b.cx, b.cy, b.row = 0, 32, 0
	return sheet_img


def previews(out):
	os.makedirs(out, exist_ok=True)
	plate_pieces = {**g.PLATE, **{n: (bld, 64) for n, bld in PLATE.items()}}
	# plate and cloth models, worn
	for name, build in PLATE.items():
		for finish in ("", "gilded") if name != "hussar_cuirass" else ("", "gilt_banded"):
			b, _ = g.make(build, 64, finish, figure=True)
			preview_png(b, name + ("_" + finish if finish else ""), out)
	for name in COVERABLE:
		build, size = plate_pieces[name]
		b, _ = g.make(build, size, "covered", figure=True)
		img = b.img.copy()
		# (only the piece takes the dye, not the figure under it)
		img.alpha_composite(g.tinted(h.split(b, h.mats_of(b) - {"figure"}), COVER_UNDYED))
		preview_png(b, name + "_covered", out, img)
	for name, build in CLOTH_MODELS.items():
		b, _ = g.make(build, 128, figure=True)
		img = b.img.copy()
		if name in WHOLE:
			# (drawn as one sheet in the dye: the figure under it is not tinted)
			img.alpha_composite(g.tinted(h.split(b, h.mats_of(b) - {"figure"}), UNDYED[name][0]))
		else:
			img.alpha_composite(g.tinted(h.split(b, DYED_MATS), UNDYED[name][0]))
		if SECOND_MAT in h.mats_of(b):
			img.alpha_composite(g.tinted(h.split(b, (SECOND_MAT,)), UNDYED[name][1]))
		preview_png(b, name, out, img)
	for name, (shape, build, marks) in TORN.items():
		b, _ = g.make(build, 128, figure=True, **marks)
		img = b.img.copy()
		img.alpha_composite(g.tinted(h.split(b, DYED_MATS), UNDYED[name][0]))
		preview_png(b, name, out, img)
	# a surcoat and a pair of pauldrons over a cuirass; the cuirasses under the tabard-like garments
	for name, build in (("surcoat", surcoat), ("kaftan", kaftan), ("monks_robe", monks_robe), ("sash", sash)):
		def over(b, build=build):
			made = b.bone
			b.bone = lambda n, parent, origin, rotation=g.Z: b.bones[n] if n in b.bones else made(n, parent, origin, rotation)
			g.cuirass(b)
			g.spaulders(b)
			build(b)
		b, _ = g.make(over, 128, figure=True)
		img = b.img.copy()
		img.alpha_composite(g.tinted(h.split(b, DYED_MATS), UNDYED[name][0]))
		preview_png(b, name + "_over_plate", out, img)
	# the sheets, worn: the sheet over the figure (and the skirt model with it)
	for name, (paint, other, sleeves, until, arm_until) in SHEETS.items():
		base = make_sheet(name, 0)
		img_sheet = tint_split(base, make_sheet(name, 1) if other else None, UNDYED[name]) if name in UNDYED else base
		skirt = SKIRTS.get(name)

		def build(b, skirt=skirt, sh=img_sheet):
			sheet_cubes(b, sh)
			if skirt:
				made = b.bone
				b.bone = lambda n, parent, origin, rotation=g.Z: b.bones[n] if n in b.bones else made(n, parent, origin, rotation)
				skirt(b)
		b, _ = g.make(build, 128, figure=True)
		img = b.img.copy()
		# clear the sheet's region and lay the sheet in it
		for y in range(16, 32):
			for x in range(16, 56):
				img.putpixel((x, y), (0, 0, 0, 0))
		img.alpha_composite(img_sheet)
		if skirt and name in UNDYED:
			# (the skirt's cloth takes the piece's dye)
			cloth = g.tinted(h.split(b, DYED_MATS), UNDYED[name][0]).crop((0, 32, b.img.width, b.img.height))
			img.alpha_composite(cloth, (0, 32))
			if SECOND_MAT in h.mats_of(b):
				second = g.tinted(h.split(b, (SECOND_MAT,)), UNDYED[name][1]).crop((0, 32, b.img.width, b.img.height))
				img.alpha_composite(second, (0, 32))
		preview_png(b, name, out, img)
	# the two flat-top looks Andy asked this track for
	def royal(b):
		g.PLATE["flat_top_helm"][0](b)
		made = b.bone
		b.bone = lambda n, parent, origin, rotation=g.Z: b.bones[n] if n in b.bones else made(n, parent, origin, rotation)
		h.crest_crown(b)
	b, _ = g.make(royal, 64, figure=True)
	preview_png(b, "royal_flat_top", out)


def tint_split(base, second, colours):
	"""A sheet piece's sheet as the game draws it: the base tinted by its dye, the second layer by its second colour."""
	img = g.tinted(base, colours[0])
	if second is not None:
		img.alpha_composite(g.tinted(second, colours[1]))
	return img


if __name__ == "__main__":
	main(sys.argv[1:])
