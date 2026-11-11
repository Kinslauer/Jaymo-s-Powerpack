"""
The town golems' Blockbench projects, kingdoms/art/town_golem_<kind>.bbmodel: three bodies of our own (cubes, paint and
animations all made here; docs/TownGolems-Spec.md is the spec they follow), then the game's GeckoLib files written
from them (tools/bbmodel_geckolib.py), each one's glow sheet (what shines in the dark) and the three item icons.

  gilt     the lamp warden: a lean sandstone lamplighter banded in gold whose head is a lantern cage with a crystal
           flame turning in it (a brighter flame, drawn only at night, is the bone "flame_lit")
  verdant  the mender: a squat, mossy river-stone boulder with long arms and mitten hands, a small head sunk in front
           of its hump, and emerald crystals growing from its back
  diamond  the throne golem: a walking castle keep of dark masonry set with diamond, turrets for shoulders and great
           bracers for forearms. It folds into a real throne: it sits (thighs the seat, shins the step before it), its
           keep slides back and telescopes a plinth down to the ground (the back), its forearms swing level (the
           armrests), and a crenellated crest rises out of the keep behind its head (the high back).

Blockbench space: y up, feet at 0, the front at -z, the model's right at +x.
Animation keys are the game's way, added to a bone's resting pose: rotation x - swings a limb forward, lifts the head
and leans the body back; z + lifts the right arm out; the left side mirrors y and z (both()). Positions: +y up, -z ahead.

Run from the project root: py -3 -I kingdoms/tools/gen_town_golems.py [--preview <dir>]   (needs Pillow)
--preview also renders each animation's key poses into <dir> (tools/bbmodel_bake.py + tools/bbmodel_preview.py).
"""
import json
import math
import os
import subprocess
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import gen_tomb_undead as kit  # noqa: E402
from gen_tomb_undead import Builder, anim, add_animations, bone_uuids_of, shade  # noqa: E402

ROOT = kit.ROOT
ASSETS = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms")
Z = (0, 0, 0)
FACES = ("up", "down", "north", "south", "east", "west")

# ---------------------------------------------------------------- colours (ours)

GILT = {"stone": (206, 180, 128), "seam": (150, 124, 84), "dark": (164, 136, 92), "gold": (228, 172, 44),
		"gold_hi": (255, 226, 110), "crystal": (150, 104, 40), "glow": (255, 236, 130), "hand": (132, 106, 70)}
VERDANT = {"stone": (112, 122, 106), "seam": (78, 88, 76), "dark": (88, 98, 86), "moss": (72, 124, 56),
		   "moss_hi": (112, 164, 72), "crystal": (38, 176, 92), "glow": (150, 255, 180), "hand": (92, 100, 88)}
DIAMOND = {"stone": (66, 68, 80), "seam": (36, 37, 46), "dark": (48, 50, 60), "trim": (51, 220, 196),
		   "glow": (164, 253, 240), "gem": (120, 240, 226), "hand": (40, 41, 50)}

# ---------------------------------------------------------------- painting

GLOW = {"img": None}  # the sheet the glowing pixels are copied to while a model is built


def lit(img, x, y, c, a=255):
	img.putpixel((x, y), c + (255,))
	GLOW["img"].putpixel((x, y), c + (a,))


def masonry(course, joint):
	"""Courses of dressed stone: a dark seam under each course, upright joints staggered from course to course, each
	stone lit along its top."""
	def fx(img, r):
		x0, y0, x1, y1 = r
		seam = kit.PALETTE["seam"]
		for y in range(y0, y1):
			row = (y - y0) // course
			for x in range(x0, x1):
				c = img.getpixel((x, y))[:3]
				ly, lx = (y - y0) % course, (x - x0 + (joint // 2 if row % 2 else 0)) % joint
				if ly == course - 1 or lx == joint - 1:
					c = tuple((c[i] + 2 * seam[i]) // 3 for i in range(3))
				elif ly == 0:
					c = shade(c, 1.12)
				img.putpixel((x, y), c + (255,))
	return fx


def cobble(img, r):
	"""A river stone's face: darker pits and a few pale flecks, no courses."""
	x0, y0, x1, y1 = r
	for y in range(y0, y1):
		for x in range(x0, x1):
			h = (x * 7 + y * 13 + (x * y) % 5) % 11
			c = img.getpixel((x, y))[:3]
			if h == 0:
				c = kit.PALETTE["seam"]
			elif h == 5:
				c = shade(c, 1.18)
			img.putpixel((x, y), c + (255,))


def moss(img, r):
	"""Moss: tufts light and dark, a ragged lower edge hanging over the stone."""
	x0, y0, x1, y1 = r
	for y in range(y0, y1):
		for x in range(x0, x1):
			c = kit.PALETTE["moss_hi"] if (x * 5 + y * 3) % 4 == 0 else img.getpixel((x, y))[:3]
			img.putpixel((x, y), c + (255,))


def band(rows, key="trim", glow=0):
	"""A band of the trim colour across these rows of a face (counted from its top; negative from its foot)."""
	def fx(img, r):
		x0, y0, x1, y1 = r
		for row in rows:
			y = y0 + row if row >= 0 else y1 + row
			if y0 <= y < y1:
				for x in range(x0, x1):
					c = shade(kit.PALETTE[key], 1.0 if (x - x0) % 3 else 0.82)
					if glow:
						lit(img, x, y, c, glow)
					else:
						img.putpixel((x, y), c + (255,))
	return fx


def gem(img, r):
	"""A cut stone that shines: its rim in the trim colour, the glow colour inside, a white heart."""
	x0, y0, x1, y1 = r
	rim = kit.PALETTE.get("trim") or kit.PALETTE["crystal"]
	cx, cy = (x0 + x1 - 1) / 2, (y0 + y1 - 1) / 2
	for y in range(y0, y1):
		for x in range(x0, x1):
			edge = x in (x0, x1 - 1) or y in (y0, y1 - 1)
			heart = abs(x - cx) < 1 and abs(y - cy) < 1
			lit(img, x, y, rim if edge else (255, 255, 255) if heart else kit.PALETTE["glow"])


def shine(img, r):
	"""A whole face that shines, facets lighter and darker."""
	x0, y0, x1, y1 = r
	for y in range(y0, y1):
		for x in range(x0, x1):
			lit(img, x, y, shade(kit.PALETTE["glow"], 1.0 if (x + y) % 3 else 0.86))


def facets(img, r):
	"""A dull crystal: slanting facets."""
	x0, y0, x1, y1 = r
	for y in range(y0, y1):
		for x in range(x0, x1):
			img.putpixel((x, y), shade(kit.PALETTE["crystal"], (0.82, 1.0, 1.2)[(x + 2 * y) % 3]) + (255,))


def glints(alpha):
	"""A crystal that keeps a little light: its facets, the bright ones glowing faintly."""
	def fx(img, r):
		facets(img, r)
		x0, y0, x1, y1 = r
		for y in range(y0, y1):
			for x in range(x0, x1):
				if (x + 2 * y) % 3 == 2:
					GLOW["img"].putpixel((x, y), kit.PALETTE["glow"] + (alpha,))
	return fx


def pixels(points, key="glow", glow=255):
	"""These pixels of a face (x, y from its top left) in a colour, shining if glow."""
	def fx(img, r):
		x0, y0, x1, y1 = r
		for px, py in points:
			x, y = x0 + px, y0 + py
			if x0 <= x < x1 and y0 <= y < y1:
				if glow:
					lit(img, x, y, kit.PALETTE[key], glow)
				else:
					img.putpixel((x, y), kit.PALETTE[key] + (255,))
	return fx


def chain(*fxs):
	def fx(img, r):
		for f in fxs:
			if f:
				f(img, r)
	return fx


def box(b, bone, frm, to, mat, base=None, sides=None, front=None, back=None, top=None, **kw):
	"""A cube whose every face gets `base`, its four upright faces `sides` too, and the front, back and top their own."""
	paint = {}
	for f in FACES:
		extra = {"north": front, "south": back, "up": top}.get(f)
		paint[f] = chain(base, sides if f in ("north", "south", "east", "west") else None, extra)
	return b.cube(bone, frm, to, mat, faces_paint=paint, **kw)


def pair(b, name, parent, origin, cubes, rotation=Z):
	"""A right-hand bone and its cubes (at +x) and its left twin (x flipped). cubes: (from, to, material, box() keywords)."""
	for side, s in (("right", 1), ("left", -1)):
		o = (origin[0] * s, origin[1], origin[2])
		b.bone(f"{side}_{name}", f"{side}_{parent[0]}" if isinstance(parent, tuple) else parent, o,
			   (rotation[0], rotation[1] * s, rotation[2] * s))
		for frm, to, mat, kw in cubes:
			x0, x1 = sorted((frm[0] * s, to[0] * s))
			box(b, f"{side}_{name}", (x0, frm[1], frm[2]), (x1, to[1], to[2]), mat, **kw)


def new(palette, w, h, seed):
	kit.PALETTE.update(palette)
	b = Builder(w, h, seed)
	GLOW["img"] = Image.new("RGBA", (w, h), (0, 0, 0, 0))
	b.bone("root", None, Z)
	return b


# ---------------------------------------------------------------- the lamp warden

def gilt():
	b = new(GILT, 128, 64, 7)
	st = {"base": masonry(3, 5)}
	gold = {"base": band(range(0, 40), "gold"), "top": band((0,), "gold_hi")}
	b.bone("waist", "root", (0, 12, 0))
	box(b, "waist", (-4, 11, -2.5), (4, 14, 2.5), "dark", sides=band((0,), "gold"))
	box(b, "waist", (-2, 5.5, -3.1), (2, 11.5, -2.6), "gold", **gold)  # the tabard hung from its belt
	pair(b, "leg", "waist", (2.2, 12, 0), [((0.4, 6, -1.75), (4, 12, 1.75), "stone", st)])
	pair(b, "shin", ("leg",), (2.2, 6, 0), [((0.2, 0, -2), (4.2, 6, 2), "stone", dict(st, sides=band((1,), "gold"))),
											((0.2, 0, -3.5), (4.2, 1.5, -2), "dark", {})])
	b.bone("body", "waist", (0, 14, 0))
	box(b, "body", (-3.5, 14, -2.5), (3.5, 17.5, 2.5), "dark", **st)
	box(b, "body", (-6, 17, -3.5), (6, 24, 3.5), "stone", sides=band((0, -1), "gold"), **st)
	box(b, "body", (-1.5, 24, -1.5), (1.5, 25.5, 1.5), "gold", **gold)
	# its core: a ring of gold round a small light in the chest (the light a bone of its own, drawn only at night)
	box(b, "body", (-2.5, 18.5, -3.9), (2.5, 22.5, -3.5), "gold", **gold)
	b.bone("core_lit", "body", (0, 20.5, -4))
	box(b, "core_lit", (-1.5, 19.5, -4.2), (1.5, 21.5, -3.9), "glow", front=shine)
	# the head: a lantern, a plate, four posts and a cap with a ring to hang it by
	b.bone("head", "body", (0, 25.5, 0))
	box(b, "head", (-4, 25.5, -4), (4, 26.5, 4), "gold", **gold)
	for sx in (-1, 1):
		for sz in (-1, 1):
			x0, z0 = (3 if sx > 0 else -4), (3 if sz > 0 else -4)
			box(b, "head", (x0, 26.5, z0), (x0 + 1, 32.5, z0 + 1), "gold", **gold)
	box(b, "head", (-4.5, 32.5, -4.5), (4.5, 34, 4.5), "gold", **gold)
	box(b, "head", (-2.5, 34, -2.5), (2.5, 35.5, 2.5), "gold", **gold)
	box(b, "head", (-1, 35.5, -0.5), (1, 37.5, 0.5), "dark")
	# the flame: a crystal that turns in the cage, dull by day; "flame_lit" is the same crystal alight
	b.bone("flame", "head", (0, 29.5, 0))
	box(b, "flame", (-1.5, 27.5, -1.5), (1.5, 31.5, 1.5), "crystal", base=facets)
	b.bone("flame_lit", "flame", (0, 29.5, 0))
	box(b, "flame_lit", (-2, 27, -2), (2, 32, 2), "glow", base=shine)
	pair(b, "arm", "body", (7.5, 22.5, 0), [((6, 17, -1.5), (9, 23.5, 1.5), "stone", st),
											((5.8, 22, -2.2), (9.7, 24.6, 2.2), "gold", gold)])
	pair(b, "forearm", ("arm",), (7.5, 17, 0), [((5.8, 9.5, -1.7), (9.2, 17, 1.7), "stone", dict(st, sides=band((-2, -1), "gold"))),
												((6, 8, -1.5), (9, 9.5, 1.5), "hand", {})])
	return b


def gilt_animations():
	idle = anim("idle", 4.0, "loop", {
		**both("arm", loop(4.0, (0, (0, 0, 4)), (0.5, (-3, 0, 6)))),
		**both("forearm", loop(4.0, (0, (-10, 0, 0)), (0.5, (-16, 0, 0)))),
		"body": {"rotation": loop(4.0, (0, (3, 0, 0)), (0.5, (5, 0, 0)))},
		"head": {"rotation": loop(4.0, (0, (0, 0, 2)), (0.5, (0, 0, -2)))},
		"flame": {"rotation": [(0, Z), (2.0, (0, 180, 0)), (4.0, (0, 360, 0))],
				  "position": loop(4.0, (0, Z), (0.5, (0, 0.5, 0)))},
	})
	walk = anim("walk", 1.0, "loop", {
		"right_leg": {"rotation": loop(1.0, (0, (-30, 0, 0)), (0.5, (26, 0, 0)))},
		"left_leg": {"rotation": loop(1.0, (0, (26, 0, 0)), (0.5, (-30, 0, 0)))},
		"right_shin": {"rotation": loop(1.0, (0, (6, 0, 0)), (0.5, (14, 0, 0)), (0.75, (46, 0, 0)))},
		"left_shin": {"rotation": loop(1.0, (0, (14, 0, 0)), (0.25, (46, 0, 0)), (0.5, (6, 0, 0)))},
		"right_arm": {"rotation": loop(1.0, (0, (24, 0, 4)), (0.5, (-22, 0, 4)))},
		"left_arm": {"rotation": loop(1.0, (0, (-22, 0, -4)), (0.5, (24, 0, -4)))},
		**both("forearm", loop(1.0, (0, (-12, 0, 0)), (0.5, (-26, 0, 0))), loop(1.0, (0, (-26, 0, 0)), (0.5, (-12, 0, 0)))),
		"waist": {"rotation": loop(1.0, (0, (0, 5, 0)), (0.5, (0, -5, 0)))},
		"body": {"rotation": loop(1.0, (0, (6, -4, 0)), (0.5, (6, 4, 0)))},
		# the lantern swings a beat behind the step
		"head": {"rotation": loop(1.0, (0, (0, 0, -3)), (0.15, (0, 0, -5)), (0.5, (0, 0, 3)), (0.65, (0, 0, 5)))},
		"flame": {"rotation": [(0, Z), (1.0, (0, 360, 0))]},
		"root": {"position": loop(1.0, (0, Z), (0.25, (0, 0.5, 0)), (0.5, Z), (0.75, (0, 0.5, 0)))},
	})
	# the beam: it rears back with its arms thrown wide while the flame gathers small, then bows the lantern at its
	# mark as the flame bursts to twice its size (the harm lands on the bow, a tenth of a second in)
	beam = anim("beam", 0.6, "once", {
		**both("arm", [(0, (0, 0, 4)), (0.06, (24, 0, 42)), (0.16, (-46, 0, 18)), (0.6, (0, 0, 4))]),
		**both("forearm", [(0, (-10, 0, 0)), (0.06, (-30, 0, 0)), (0.16, (-4, 0, 0)), (0.6, (-10, 0, 0))]),
		"body": {"rotation": [(0, (3, 0, 0)), (0.06, (-12, 0, 0)), (0.16, (12, 0, 0)), (0.6, (3, 0, 0))]},
		"head": {"rotation": [(0, Z), (0.06, (-18, 0, 0)), (0.16, (16, 0, 0)), (0.6, Z)]},
		"flame": {"scale": [(0, (1, 1, 1)), (0.06, (0.6, 0.6, 0.6)), (0.16, (2.0, 2.0, 2.0)), (0.3, (1.3, 1.3, 1.3)), (0.6, (1, 1, 1))]},
	})
	hurt = anim("hurt", 0.35, "once", {
		**both("arm", [(0, (0, 0, 4)), (0.08, (14, 0, 30)), (0.35, (0, 0, 4))]),
		"body": {"rotation": [(0, (3, 0, 0)), (0.08, (-10, 6, 0)), (0.35, (3, 0, 0))]},
		"head": {"rotation": [(0, Z), (0.08, (-8, 0, 14)), (0.2, (0, 0, -8)), (0.35, Z)]},
	})
	# death: the flame gutters out, its knees give and it folds down onto them, the lantern bowed
	death = anim("death", 1.0, "hold", {
		**both("leg", [(0, Z), (0.5, (-58, 0, 0)), (1.0, (-70, 0, 0))]),
		**both("shin", [(0, Z), (0.5, (104, 0, 0)), (1.0, (128, 0, 0))]),
		**both("arm", [(0, (0, 0, 4)), (0.3, (10, 0, 26)), (1.0, (18, 0, 6))]),
		**both("forearm", [(0, (-10, 0, 0)), (1.0, (-4, 0, 0))]),
		"body": {"rotation": [(0, (3, 0, 0)), (0.3, (-6, 0, 0)), (1.0, (38, 0, 0))]},
		"head": {"rotation": [(0, Z), (0.3, (-10, 0, 0)), (1.0, (46, 0, 8))]},
		"flame": {"scale": [(0, (1, 1, 1)), (0.2, (1.4, 1.4, 1.4)), (0.6, (0.3, 0.3, 0.3)), (1.0, (0.05, 0.05, 0.05))]},
		"root": {"position": [(0, Z), (0.5, (0, -4.5, 0)), (1.0, (0, -6.4, 0))]},
	})
	return [idle, walk, beam, hurt, death]


# ---------------------------------------------------------------- the mender

def verdant():
	b = new(VERDANT, 128, 128, 11)
	st = {"base": cobble}
	mo = {"base": moss}
	b.bone("waist", "root", (0, 7, 0))
	pair(b, "leg", "waist", (3.5, 7, 0), [((1, 0, -2.5), (6, 7.5, 2.5), "dark", st),
										  ((1, 0, -4), (6, 2, -2.5), "dark", st)])
	b.bone("body", "waist", (0, 7, 0))
	box(b, "body", (-8, 6, -6), (8, 18, 6), "stone", **st)
	box(b, "body", (-6.5, 18, -5), (6.5, 22, 5), "stone", **st)
	box(b, "body", (-7, 21.4, -5.5), (7, 23, 5.5), "moss", **mo)
	box(b, "body", (-8.4, 14, 1), (8.4, 18.6, 6.4), "moss", **mo)
	# its core: an emerald set in the belly
	box(b, "body", (-2.5, 9.5, -6.5), (2.5, 14.5, -6), "crystal", front=gem)
	# the crystals on its back: one tall, two leaning out from it
	b.bone("crystals", "body", (0, 22, 3))
	gl = {"base": glints(110)}
	box(b, "crystals", (-1.5, 22, 1.5), (1.5, 31, 4.5), "crystal", rotation=(-10, 0, 0), origin=(0, 22, 3), **gl)
	box(b, "crystals", (2, 21, 1), (4.5, 27.5, 3.5), "crystal", rotation=(-8, 0, -24), origin=(3, 21, 2), **gl)
	box(b, "crystals", (-4.5, 21, 2), (-2.5, 26, 4), "crystal", rotation=(-14, 0, 22), origin=(-3.5, 21, 3), **gl)
	box(b, "crystals", (-1, 20, 4.5), (1, 24.5, 6.5), "crystal", rotation=(-34, 0, 0), origin=(0, 20, 5.5), **gl)
	# the head: small, sunk in front of the hump, two soft eyes
	b.bone("head", "body", (0, 18, -5))
	box(b, "head", (-3.5, 15.5, -9.5), (3.5, 21.5, -4.5), "stone", front=pixels([(1, 2), (1, 3), (5, 2), (5, 3)]), **st)
	box(b, "head", (-3.8, 20.6, -9.8), (3.8, 22, -5), "moss", **mo)
	pair(b, "arm", "body", (10, 18, 0), [((8, 11, -2.5), (12, 19.5, 2.5), "stone", st),
										 ((7.6, 17.6, -3), (12.6, 20.2, 3), "moss", mo)])
	pair(b, "forearm", ("arm",), (10, 11, 0), [((7.5, 3, -3), (12.5, 11, 3), "stone", st),
											   ((7, 0.5, -3.5), (13, 3.5, 3.5), "hand", st)])
	return b


def verdant_animations():
	glowing = lambda k: (k, k, k)  # noqa: E731
	idle = anim("idle", 4.0, "loop", {
		**both("arm", loop(4.0, (0, (0, 0, 3)), (0.5, (-2, 0, 6)))),
		**both("forearm", loop(4.0, (0, (-6, 0, 0)), (0.5, (-10, 0, 0)))),
		"body": {"rotation": loop(4.0, (0, (2, 0, -1.5)), (0.5, (3, 0, 1.5))),
				 "position": loop(4.0, (0, Z), (0.25, (0, -0.4, 0)), (0.5, Z), (0.75, (0, -0.4, 0)))},
		"head": {"rotation": loop(4.0, (0, (0, -8, 0)), (0.3, (4, 10, 0)), (0.7, (-3, -4, 0)))},
		"crystals": {"scale": loop(4.0, (0, glowing(1.0)), (0.5, glowing(1.06)))},
	})
	walk = anim("walk", 1.2, "loop", {
		"right_leg": {"rotation": loop(1.2, (0, (-32, 0, 0)), (0.5, (30, 0, 0)))},
		"left_leg": {"rotation": loop(1.2, (0, (30, 0, 0)), (0.5, (-32, 0, 0)))},
		"right_arm": {"rotation": loop(1.2, (0, (20, 0, 4)), (0.5, (-18, 0, 4)))},
		"left_arm": {"rotation": loop(1.2, (0, (-18, 0, -4)), (0.5, (20, 0, -4)))},
		**both("forearm", loop(1.2, (0, (-6, 0, 0)), (0.5, (-18, 0, 0))), loop(1.2, (0, (-18, 0, 0)), (0.5, (-6, 0, 0)))),
		# a waddle: the boulder rolls over whichever foot is down
		"body": {"rotation": loop(1.2, (0, (4, -3, 7)), (0.5, (4, 3, -7)))},
		"head": {"rotation": loop(1.2, (0, (0, 0, -5)), (0.5, (0, 0, 5)))},
		"crystals": {"rotation": loop(1.2, (0, (0, 0, -3)), (0.2, (0, 0, -5)), (0.5, (0, 0, 3)), (0.7, (0, 0, 5)))},
		"root": {"position": loop(1.2, (0, (0, -0.5, 0)), (0.25, (0, 0.4, 0)), (0.5, (0, -0.5, 0)), (0.75, (0, 0.4, 0)))},
	})
	# the mending: it leans in and holds both hands out, palms to the hurt, while its crystals swell twice with light
	# (the mending is given as the hands come up)
	mend = anim("mend", 1.2, "once", {
		**both("arm", [(0, (0, 0, 3)), (0.25, (-82, 0, -10)), (0.8, (-86, 0, -12)), (1.2, (0, 0, 3))]),
		**both("forearm", [(0, (-6, 0, 0)), (0.25, (-18, 0, 0)), (0.8, (-12, 0, 0)), (1.2, (-6, 0, 0))]),
		"body": {"rotation": [(0, (2, 0, 0)), (0.25, (10, 0, 0)), (0.8, (12, 0, 0)), (1.2, (2, 0, 0))]},
		"head": {"rotation": [(0, Z), (0.25, (-16, 0, 0)), (0.8, (-18, 0, 0)), (1.2, Z)]},
		"crystals": {"scale": [(0, glowing(1.0)), (0.25, glowing(1.32)), (0.5, glowing(1.1)), (0.75, glowing(1.32)), (1.2, glowing(1.0))]},
	})
	hurt = anim("hurt", 0.35, "once", {
		**both("arm", [(0, (0, 0, 3)), (0.08, (-20, 0, 24)), (0.35, (0, 0, 3))]),
		"body": {"rotation": [(0, (2, 0, 0)), (0.08, (-8, 0, 5)), (0.35, (2, 0, 0))]},
		"head": {"rotation": [(0, Z), (0.08, (14, 0, -10)), (0.35, Z)]},
	})
	# death: its light goes out of the crystals and it settles onto the ground, a boulder again
	death = anim("death", 1.0, "hold", {
		**both("leg", [(0, Z), (0.6, (-80, 0, 12)), (1.0, (-88, 0, 16))]),
		**both("arm", [(0, (0, 0, 3)), (0.3, (-10, 0, 30)), (1.0, (6, 0, 44))]),
		**both("forearm", [(0, (-6, 0, 0)), (1.0, (-30, 0, 0))]),
		"body": {"rotation": [(0, (2, 0, 0)), (0.3, (-5, 0, 0)), (1.0, (9, 0, 0))]},
		"head": {"rotation": [(0, Z), (1.0, (30, 0, 0))], "position": [(0, Z), (1.0, (0, -1.5, 2))]},
		"crystals": {"scale": [(0, glowing(1.0)), (0.2, glowing(1.25)), (1.0, glowing(0.35))]},
		"root": {"position": [(0, Z), (0.6, (0, -5.2, 0)), (1.0, (0, -6.0, 0))]},
	})
	return [idle, walk, mend, hurt, death]


# ---------------------------------------------------------------- the throne golem

# Sizes (Blockbench units, 16 to a block, before the golem's own scale). Standing: the hips at HIP, each leg a thigh
# and a shin that hinges at the back of the knee; the keep (its body) on the hips; the arm an upper arm and a forearm.
HIP, THIGH, SHIN, LEG_D = 14.0, 8.0, 6.0, 3.5
KEEP_Y, KEEP_TOP, KEEP_D = 17.0, 31.0, 5.0
ELBOW = 21.5
# The throne: the keep slides this far back (its face ends level with the back of the hips), the plinth telescopes this
# far down out of it, the crest this far up, and the arms drop this much to bring the armrests to a sitter's elbows.
KEEP_BACK, PLINTH_DOWN, CREST_UP, ARM_DROP = KEEP_D + LEG_D, 13.0, 12.0, 2.5
# How far the hips sink as the thighs swing to `a` degrees with the shins kept upright and the feet on the ground.
SEAT_TOP = SHIN + 2 * LEG_D  # 13: the seat's height, the thighs' upper face once it has sat


def sink(a):
	r = math.radians(a)
	return THIGH - THIGH * math.cos(r) - LEG_D * math.sin(r)


def diamond():
	b = new(DIAMOND, 128, 128, 5)
	st = {"base": masonry(4, 6)}
	tr = dict(st, sides=band((0,)))
	b.bone("waist", "root", (0, HIP, 0))
	# the hips: a block whose top ends level with the seat (a hair under the thighs' faces, so the two don't fight)
	box(b, "waist", (-8, 12, -LEG_D), (8, HIP + LEG_D - 0.1, LEG_D), "dark", sides=band((1,)), **st)
	# tassets at the hips: hanging plates that close the throne's sides under the seat
	for s in (1, -1):
		x0, x1 = sorted((8 * s, 10 * s))
		box(b, "waist", (x0, 4.6, -4.5), (x1, 14, 4), "dark", sides=band((-2,)), **st)
	pair(b, "leg", "waist", (4, HIP, 0), [((0.5, HIP - THIGH, -LEG_D), (7.5, HIP, LEG_D), "stone", st)])
	pair(b, "shin", ("leg",), (4, HIP - THIGH, LEG_D), [((0, 0, -LEG_D - 1), (8, SHIN, LEG_D), "stone", dict(st, sides=band((1,))))])
	b.bone("body", "waist", (0, KEEP_Y, 0))
	rune = [(x, 2) for x in range(2, 7)] + [(x, 2) for x in range(13, 18)] + [(6, 3), (13, 3), (9, 11), (10, 11), (9, 12), (10, 12)]
	back_rune = [(x, 3) for x in range(6, 14)] + [(6, y) for y in range(4, 10)] + [(13, y) for y in range(4, 10)] + [(x, 10) for x in range(6, 14)] \
		+ [(9, 6), (10, 6), (9, 7), (10, 7)]
	box(b, "body", (-10, KEEP_Y, -KEEP_D), (10, KEEP_TOP, KEEP_D), "stone", sides=band((0, -1)), front=pixels(rune, "trim", 170),
		back=pixels(back_rune, "trim", 170), **st)
	# its core: a diamond set in a ring on the keep's face
	box(b, "body", (-3.5, 20.5, -KEEP_D - 0.4), (3.5, 27.5, -KEEP_D), "dark", front=band((0, -1)))
	box(b, "body", (-2, 22, -KEEP_D - 0.8), (2, 26, -KEEP_D - 0.4), "gem", front=gem)
	# the plinth: a course of masonry telescoped up inside the keep, let down to the ground as the throne's foot
	b.bone("plinth", "body", (0, KEEP_Y, 0))
	box(b, "plinth", (-9.5, KEEP_Y + 0.5, -KEEP_D + 0.5), (9.5, KEEP_Y + 0.5 + PLINTH_DOWN - 0.5, KEEP_D - 0.5), "dark", sides=band((-2,)), **st)
	# the crest: the throne's high back, a crenellated slab telescoped down inside the keep behind the head
	b.bone("crest", "body", (0, KEEP_TOP, 3.5))
	crown = [(7, 2), (8, 2), (6, 3), (9, 3), (5, 4), (10, 4), (5, 5), (10, 5), (6, 6), (7, 6), (8, 6), (9, 6), (7, 4), (8, 4)]
	box(b, "crest", (-8, 19, 2.5), (8, 28, 4.5), "stone", sides=band((0,)), front=pixels(crown, "trim", 200),
		back=pixels(crown, "trim", 200), **st)
	for x0 in (-8, -2, 4):
		box(b, "crest", (x0, 28, 2.5), (x0 + 4, 30.8, 4.5), "stone", top=band((0,)), **st)
	# the head: a small keep-top, a brow over two eyes, a merlon at each corner
	b.bone("head", "body", (0, KEEP_TOP, -1))
	eyes = [(1, 3), (2, 3), (5, 3), (6, 3)]
	brow = [(x, 2) for x in range(8)]
	box(b, "head", (-4, KEEP_TOP, -4.5), (4, KEEP_TOP + 7, 2.5), "stone", front=chain(pixels(brow, "seam", 0), pixels(eyes)), **st)
	for sx in (-4, 2):
		for sz in (-4.5, 0.5):
			box(b, "head", (sx, KEEP_TOP + 7, sz), (sx + 2, KEEP_TOP + 8.5, sz + 2), "dark", top=band((0,)))
	# the arms: a turret on each shoulder, and forearms like tower bracers that end in fists
	pair(b, "arm", "body", (13, 28, 0), [((10.5, 21, -3), (15.5, 29.5, 3), "dark", st),
										 ((10, 28.5, -4), (17, 32.5, 4), "stone", tr),
										 ((10, 32.5, -4), (12, 34, -2), "dark", {}), ((15, 32.5, -4), (17, 34, -2), "dark", {}),
										 ((10, 32.5, 2), (12, 34, 4), "dark", {}), ((15, 32.5, 2), (17, 34, 4), "dark", {})])
	pair(b, "forearm", ("arm",), (13, ELBOW, 0), [((10, 9, -3.5), (16, 22, 3.5), "stone", dict(st, sides=band((1, -2)))),
												  ((9.5, 5, -4), (16.5, 9, 4), "hand", st)])
	return b


def ramp(t, a, b_):
	k = max(0.0, min(1.0, (t - a) / (b_ - a)))
	return k * k * (3 - 2 * k)


def bump(t, a, b_):
	return math.sin(math.pi * max(0.0, min(1.0, (t - a) / (b_ - a))))


FOLD = 1.5  # seconds to fold into the throne or out of it (TownGolem.FOLD_TICKS is the same, in ticks)


def fold_pose(t):
	"""The throne golem's bones `t` seconds into folding: it braces, sits, slides its keep back and lets the plinth
	down, levels its forearms, and last raises the crest (which lands with a small knock)."""
	a = 90 * ramp(t, 0.2, 0.85)
	arm = ramp(t, 0.75, 1.2)
	return {
		"waist": {"position": (0, -sink(a), 0)},
		"right_leg": {"rotation": (-a, 0, 0)}, "left_leg": {"rotation": (-a, 0, 0)},
		"right_shin": {"rotation": (a, 0, 0)}, "left_shin": {"rotation": (a, 0, 0)},
		"body": {"position": (0, 0, KEEP_BACK * ramp(t, 0.45, 0.95)), "rotation": (9 * bump(t, 0.0, 0.6), 0, 0)},
		"plinth": {"position": (0, -PLINTH_DOWN * ramp(t, 0.7, 1.1), 0)},
		"right_arm": {"position": (0, -ARM_DROP * arm, 0), "rotation": (0, 0, 14 * bump(t, 0.0, 0.9))},
		"left_arm": {"position": (0, -ARM_DROP * arm, 0), "rotation": (0, 0, -14 * bump(t, 0.0, 0.9))},
		"right_forearm": {"rotation": (-90 * arm, 0, 0)}, "left_forearm": {"rotation": (-90 * arm, 0, 0)},
		"crest": {"position": (0, CREST_UP * ramp(t, 1.0, 1.38) + 0.5 * bump(t, 1.38, 1.5), 0)},
		"head": {"rotation": (10 * bump(t, 0.2, 1.0), 0, 0)},
		"root": {"position": (0, -0.5 * bump(t, 0.8, 0.95), 0)},
	}


def sampled(pose_at, length, step=0.05, reverse=False):
	"""Tracks sampled every `step` seconds from a function of time, so the fold moves exactly as fold_pose says."""
	tracks = {}
	n = round(length / step)
	for i in range(n + 1):
		t = round(i * step, 4)
		for bone, channels in pose_at(length - t if reverse else t).items():
			for ch, v in channels.items():
				tracks.setdefault(bone, {}).setdefault(ch, []).append((t, tuple(round(x, 3) for x in v)))
	return tracks


def diamond_animations():
	seated = fold_pose(FOLD)
	idle = anim("idle", 4.0, "loop", {
		**both("arm", loop(4.0, (0, (0, 0, 3)), (0.5, (-2, 0, 5)))),
		**both("forearm", loop(4.0, (0, (-8, 0, 0)), (0.5, (-12, 0, 0)))),
		"body": {"rotation": loop(4.0, (0, (1, 0, 0)), (0.5, (3, 0, 0)))},
		"head": {"rotation": loop(4.0, (0, (0, -6, 0)), (0.35, (0, 8, 0)), (0.7, (0, -2, 0)))},
	})
	# a slow, heavy tread: each foot lifted and set down flat, the keep rocking over it, a jolt as it lands
	walk = anim("walk", 1.6, "loop", {
		"right_leg": {"rotation": loop(1.6, (0, (-26, 0, 0)), (0.5, (22, 0, 0)))},
		"left_leg": {"rotation": loop(1.6, (0, (22, 0, 0)), (0.5, (-26, 0, 0)))},
		"right_shin": {"rotation": loop(1.6, (0, (26, 0, 0)), (0.12, (4, 0, 0)), (0.5, (2, 0, 0)), (0.78, (38, 0, 0)))},
		"left_shin": {"rotation": loop(1.6, (0, (2, 0, 0)), (0.28, (38, 0, 0)), (0.5, (26, 0, 0)), (0.62, (4, 0, 0)))},
		"right_arm": {"rotation": loop(1.6, (0, (16, 0, 4)), (0.5, (-16, 0, 4)))},
		"left_arm": {"rotation": loop(1.6, (0, (-16, 0, -4)), (0.5, (16, 0, -4)))},
		**both("forearm", loop(1.6, (0, (-10, 0, 0)), (0.5, (-22, 0, 0))), loop(1.6, (0, (-22, 0, 0)), (0.5, (-10, 0, 0)))),
		"waist": {"rotation": loop(1.6, (0, (0, 4, 0)), (0.5, (0, -4, 0)))},
		"body": {"rotation": loop(1.6, (0, (4, -3, 3)), (0.5, (4, 3, -3)))},
		"head": {"rotation": loop(1.6, (0, (0, 0, -2)), (0.5, (0, 0, 2)))},
		"root": {"position": loop(1.6, (0, (0, 0.6, 0)), (0.12, (0, -0.5, 0)), (0.2, Z), (0.5, (0, 0.6, 0)), (0.62, (0, -0.5, 0)), (0.7, Z))},
	})
	# the blow: the right fist driven straight out from the shoulder with the keep turned behind it (it lands at once,
	# as the game deals the hurt), then drawn back
	smash = anim("smash", 0.6, "once", {
		"right_arm": {"rotation": [(0, (0, 0, 3)), (0.08, (-96, 0, -8)), (0.2, (-84, 0, -6)), (0.6, (0, 0, 3))]},
		"right_forearm": {"rotation": [(0, (-8, 0, 0)), (0.08, (-4, 0, 0)), (0.3, (-30, 0, 0)), (0.6, (-8, 0, 0))]},
		"left_arm": {"rotation": [(0, (0, 0, -3)), (0.08, (28, 0, -12)), (0.6, (0, 0, -3))]},
		"body": {"rotation": [(0, (1, 0, 0)), (0.08, (10, 18, 0)), (0.2, (8, 14, 0)), (0.6, (1, 0, 0))]},
		"head": {"rotation": [(0, Z), (0.08, (-6, -14, 0)), (0.6, Z)]},
	})
	hurt = anim("hurt", 0.35, "once", {
		"body": {"rotation": [(0, (1, 0, 0)), (0.08, (-6, 0, 0)), (0.35, (1, 0, 0))]},
		"head": {"rotation": [(0, Z), (0.08, (-12, 0, 8)), (0.35, Z)]},
	})
	throne_tracks = {bone: {ch: [(0, v), (6.0, v)] for ch, v in channels.items()} for bone, channels in seated.items()}
	# as a throne it keeps still, but for its head: a slow look to one side and the other over the sitter's shoulders
	throne_tracks["head"] = {"rotation": loop(6.0, (0, Z), (0.2, (0, 16, 0)), (0.45, (0, 16, 0)), (0.6, (0, -16, 0)), (0.85, (0, -16, 0)))}
	throne = anim("throne", 6.0, "loop", throne_tracks)
	kneel = anim("kneel", FOLD, "hold", sampled(fold_pose, FOLD))
	rise = anim("rise", FOLD, "once", sampled(fold_pose, FOLD, reverse=True))
	# death: the magic lets go: it drops onto its folded legs, the keep pitches forward, the arms fall away from the
	# shoulders and the head rolls off
	death = anim("death", 1.0, "hold", {
		"waist": {"position": [(0, Z), (0.4, (0, -sink(70), 0)), (1.0, (0, -sink(90), 0))]},
		**both("leg", [(0, Z), (0.4, (-70, 0, 0)), (1.0, (-90, 0, 0))]),
		**both("shin", [(0, Z), (0.4, (70, 0, 0)), (1.0, (90, 0, 0))]),
		"body": {"rotation": [(0, (1, 0, 0)), (0.3, (-6, 0, 0)), (1.0, (34, 0, 0))]},
		"right_arm": {"rotation": [(0, (0, 0, 3)), (0.5, (-10, 0, 30)), (1.0, (-20, 0, 58))], "position": [(0, Z), (0.5, (1, -2, 0)), (1.0, (3, -9, 0))]},
		"left_arm": {"rotation": [(0, (0, 0, -3)), (0.5, (-10, 0, -30)), (1.0, (-20, 0, -58))], "position": [(0, Z), (0.5, (-1, -2, 0)), (1.0, (-3, -9, 0))]},
		**both("forearm", [(0, (-8, 0, 0)), (1.0, (-40, 0, 0))]),
		"head": {"rotation": [(0, Z), (0.4, (20, 0, 0)), (1.0, (96, 0, 14))], "position": [(0, Z), (0.4, (0, 0, -1)), (1.0, (0, -3, -6))]},
	})
	return [idle, walk, smash, hurt, kneel, throne, rise, death]


# ---------------------------------------------------------------- animation helpers

def loop(length, *keys):
	"""Keys given as (fraction of the loop, value); the first is repeated at the end so the loop closes."""
	out = [(round(length * f, 4), v) for f, v in keys]
	return out + [(length, keys[0][1])]


def both(name, right, left=None):
	"""A pair of bones: the left one mirrors the right (y and z turned round) unless given."""
	if left is None:
		left = [(t, (v[0], -v[1], -v[2])) for t, v in right]
	return {f"right_{name}": {"rotation": right}, f"left_{name}": {"rotation": left}}


# ---------------------------------------------------------------- item icons

# 16x16, lit from the top left. Letters: s stone, d dark stone, k seam, t trim (gold, moss or diamond), g glow, . clear.
ICONS = {
	"gilt": """
.......dd.......
......tttt......
.....tttttt.....
.....t.gg.t.....
.....t.gg.t.....
.....tttttt.....
.......tt.......
....ttsssstt....
....sstggtss....
....sstttsss....
....s.dssd.s....
....d.sttd.d....
......ss.ss.....
......st.st.....
.....dss.ssd....
................
""",
	"verdant": """
........g.......
.......gg..g....
....g..gg.gg....
....gg.tttg.....
...ttttttttt....
..tsssssssstt...
..ssskkssssss...
.dssgsgsssssd...
.dsskkksggssd...
.dsssssgggssd...
.ddsssssgsssd...
.dd.sssssss.dd..
.dd..dd.dd..dd..
.....dd.dd......
....ddd.ddd.....
................
""",
	"diamond": """
..ss.ss.ss.ss...
..ssssssssssss..
..sktttttttks...
..sks.gg.sks....
..sksssssssks...
..sksstttssks...
..skstgggtsks...
.ssksstttssksss.
.sdkssssssskds..
.sdddddddddds...
.sddddddddddds..
.ssssssssssssss.
.sk.sdddddds.ks.
.sk.sssssss..ks.
.sk.sk...ks..ks.
................
""",
}


def icons():
	for name, pal in (("gilt", GILT), ("verdant", VERDANT), ("diamond", DIAMOND)):
		colours = {"s": pal["stone"], "d": pal["dark"], "k": pal["seam"], "g": pal["glow"],
				   "t": pal.get("trim") or pal.get("gold") or pal["moss"]}
		img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
		for y, row in enumerate(ICONS[name].strip("\n").split("\n")):
			for x, ch in enumerate(row):
				if ch != ".":
					k = 1.1 if x + y < 12 else 0.92 if x + y < 20 else 0.8
					img.putpixel((x, y), shade(colours[ch], 1.0 if ch == "g" else k) + (255,))
		img.save(os.path.join(ASSETS, "textures", "item", f"{name}_golem.png"))
	print("wrote 3 item icons")


# ---------------------------------------------------------------- main

KINDS = {
	"gilt": (gilt, gilt_animations, [("idle", 0.0), ("walk", 0.25), ("beam", 0.06), ("beam", 0.16), ("death", 1.0)]),
	"verdant": (verdant, verdant_animations, [("idle", 0.0), ("walk", 0.3), ("mend", 0.5), ("death", 1.0)]),
	"diamond": (diamond, diamond_animations, [("idle", 0.0), ("walk", 0.4), ("smash", 0.08), ("kneel", 0.6), ("kneel", 1.0),
											   ("kneel", 1.5), ("throne", 1.2), ("rise", 0.75), ("death", 1.0)]),
}


def main(argv):
	saved = dict(kit.PALETTE)
	for kind, (build, animations, previews) in KINDS.items():
		name = f"town_golem_{kind}"
		b = build()
		model = b.project(name)
		add_animations(model, animations(), bone_uuids_of(model))
		art = os.path.join(kit.ART, f"{name}.bbmodel")
		with open(art, "w", encoding="utf-8", newline="\n") as f:
			json.dump(model, f)
		print(f"wrote kingdoms/art/{name}.bbmodel")
		subprocess.check_call([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_geckolib.py"), art, ASSETS, name])
		GLOW["img"].save(os.path.join(ASSETS, "textures", "entity", f"{name}_glow.png"))
		print(f"wrote textures/entity/{name}_glow.png")
		kit.PALETTE.clear()
		kit.PALETTE.update(saved)
		if "--preview" in argv:
			out = argv[argv.index("--preview") + 1]
			os.makedirs(out, exist_ok=True)
			subprocess.check_call([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_bake.py"), art, out, name])
			for an, t in previews:
				subprocess.check_call([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_preview.py"), out, name,
									   os.path.join(out, f"{kind}-{an}-{t}.png"), an, str(t)])
	icons()


if __name__ == "__main__":
	main(sys.argv[1:])
