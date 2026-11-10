"""
The Blockbench projects for Kingdoms' GeckoLib mobs, written to kingdoms/art/ (open and edit them in Blockbench; rerun
tools/bbmodel_geckolib.py on a project to update the game's files):

- draugr.bbmodel and barrow_beetle.bbmodel: our own models, cubes and textures made here (the texture painted face by
  face from each cube's material), with their animations.
- silver_skeleton.bbmodel: polyjo's Dead Traveler Skeleton (CC BY 4.0, tools/bbmodels/dead-traveler-skeleton.bbmodel)
  with our own "rise" (climbing up out of a coffin) added.
- skeleton_lord.bbmodel: the Blockbench Workshop's Skeleton Lord (CC BY 4.0, tools/bbmodels/skeleton-lord.bbmodel)
  with our own "staff_swing", "slam" and "grave_call" added.

Blockbench space: y up, feet at 0, the front at -z, the model's right at +x. Rotations as Blockbench shows them: +x
swings a hanging limb forward and up (and tips an upright part back), +z lifts the right arm (at +x) out to the side.

Run from the project root: py -3 -I kingdoms/tools/gen_tomb_undead.py [draugr ...]   (needs Pillow; names: only those)
"""
import base64
import io
import json
import math
import os
import random
import sys
import uuid

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ART = os.path.join(ROOT, "kingdoms", "art")
SRC = os.path.join(ROOT, "tools", "bbmodels")


def uid():
	return str(uuid.uuid4())


# ---------------------------------------------------------------- animations

def anim(name, length, loop, tracks):
	"""tracks: {bone: {"rotation"|"position"|"scale": [(time, (x, y, z)), ...]}}, all catmullrom-smoothed."""
	return {"name": name, "length": length, "loop": loop, "tracks": tracks}


def add_animations(model, anims, bone_uuids):
	for a in anims:
		animators = {}
		for bone, channels in a["tracks"].items():
			keys = []
			for ch, frames in channels.items():
				for t, v in frames:
					keys.append({"channel": ch, "data_points": [{"x": v[0], "y": v[1], "z": v[2]}], "uuid": uid(), "time": t,
								 "color": -1, "interpolation": "catmullrom"})
			animators[bone_uuids[bone]] = {"name": bone, "type": "bone", "keyframes": keys}
		model.setdefault("animations", []).append({"uuid": uid(), "name": a["name"], "loop": a["loop"], "override": False,
												   "length": a["length"], "snapping": 24, "selected": False, "anim_time_update": "",
												   "blend_weight": "", "start_delay": "", "loop_delay": "", "animators": animators})


def bone_uuids_of(model):
	out = {}

	def walk(n):
		if isinstance(n, dict):
			if "name" in n:
				out[n["name"]] = n["uuid"]
			for c in n.get("children", []):
				walk(c)
	for n in model["outliner"]:
		walk(n)
	for g in model.get("groups", []) or []:
		out.setdefault(g["name"], g["uuid"])
	return out


Z = (0, 0, 0)

# ---------------------------------------------------------------- painting

PALETTE = {
	"skin": (108, 104, 86), "bone": (214, 202, 160), "iron": (104, 110, 116), "mail": (82, 88, 96), "fur": (98, 78, 60),
	"cloth": (58, 82, 104), "leather": (88, 62, 40), "wood": (104, 72, 42), "gold": (184, 145, 58), "horn": (205, 191, 156),
	"rust": (122, 74, 44), "shell": (36, 50, 44), "shell_hi": (64, 88, 76), "leg": (22, 26, 24), "eye": (160, 232, 255),
	"steel": (168, 176, 184), "rime": (178, 196, 206),
}


def shade(c, k):
	return tuple(max(0, min(255, int(v * k))) for v in c)


def paint_face(img, box, mat, face, rng):
	x0, y0, x1, y1 = box
	base = PALETTE[mat]
	light = {"up": 1.12, "down": 0.72, "north": 1.0, "south": 0.9, "east": 0.94, "west": 0.94}[face]
	for y in range(y0, y1):
		for x in range(x0, x1):
			k = light * (0.9 + rng.random() * 0.18)
			c = base
			lx, ly = x - x0, y - y0
			if mat == "mail" and (lx + ly) % 2 == 0:
				k *= 1.25
			elif mat == "fur" and rng.random() < 0.35:
				k *= 0.78 if lx % 2 else 1.2
			elif mat in ("wood", "leather") and lx % 3 == 0:
				k *= 0.85
			elif mat in ("iron", "steel", "gold") and (ly == 0 or lx == 0):
				k *= 1.25
			elif mat == "skin" and rng.random() < 0.12:
				c = PALETTE["bone"] if rng.random() < 0.3 else shade(base, 0.7)
			elif mat == "shell" and ly == 0:
				c = PALETTE["shell_hi"]
			elif mat == "rime" and rng.random() < 0.45:
				c = PALETTE["fur"] if rng.random() < 0.6 else (226, 238, 244)  # fur under the frost, and the frost's glitter
			img.putpixel((x, y), shade(c, k) + (255,))


class Builder:
	"""Cubes and bones in Blockbench space; a box-UV region for each cube packed on the sheet and painted."""

	def __init__(self, tex_w, tex_h, seed):
		self.img = Image.new("RGBA", (tex_w, tex_h), (0, 0, 0, 0))
		self.w, self.h = tex_w, tex_h
		self.elements, self.outliner, self.bones = [], [], {}
		self.cx = self.cy = self.row = 0
		self.rng = random.Random(seed)
		self.extras = []

	def bone(self, name, parent, origin, rotation=Z):
		node = {"name": name, "origin": list(origin), "rotation": list(rotation), "uuid": uid(), "export": True, "isOpen": True,
				"color": 0, "children": []}
		self.bones[name] = node
		(self.bones[parent]["children"] if parent else self.outliner).append(node)
		return node

	def alloc(self, w, h):
		if self.cx + w > self.w:
			self.cx, self.cy, self.row = 0, self.cy + self.row, 0
		x, y = self.cx, self.cy
		self.cx += w
		self.row = max(self.row, h)
		if y + h > self.h:
			raise SystemExit("texture sheet full")
		return x, y

	def cube(self, bone, frm, to, mat, inflate=0.0, rotation=Z, origin=None, faces_paint=None):
		size = [to[i] - frm[i] for i in range(3)]
		sx, sy, sz = (max(1, round(s)) for s in size)
		u, v = self.alloc(2 * (sx + sz), sy + sz)
		# the box layout: up/down along the top row, then east, north, west, south along the bottom
		rects = {
			"up": (u + sz, v, u + sz + sx, v + sz), "down": (u + sz + sx, v, u + sz + 2 * sx, v + sz),
			"east": (u, v + sz, u + sz, v + sz + sy), "north": (u + sz, v + sz, u + sz + sx, v + sz + sy),
			"west": (u + sz + sx, v + sz, u + 2 * sz + sx, v + sz + sy), "south": (u + 2 * sz + sx, v + sz, u + 2 * sz + 2 * sx, v + sz + sy),
		}
		faces = {}
		for fn, r in rects.items():
			paint_face(self.img, r, mat, fn, self.rng)
			uv = list(r)
			if fn == "down":
				uv = [r[0], r[3], r[2], r[1]]
			faces[fn] = {"uv": uv, "texture": 0}
		if faces_paint:
			for fn, fx in faces_paint.items():
				fx(self.img, rects[fn])
		e = {"name": bone + "_" + mat, "box_uv": False, "rescale": False, "locked": False, "render_order": "default",
			 "allow_mirror_modeling": True, "from": list(frm), "to": list(to), "autouv": 0, "color": 0, "inflate": inflate,
			 "origin": list(origin or self.bones[bone]["origin"]), "rotation": list(rotation), "faces": faces, "type": "cube",
			 "uuid": uid()}
		self.elements.append(e)
		self.bones[bone]["children"].append(e["uuid"])
		return e

	def project(self, name):
		buf = io.BytesIO()
		self.img.save(buf, "PNG")
		src = "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()
		return {"meta": {"format_version": "4.10", "model_format": "free", "box_uv": False}, "name": name,
				"model_identifier": name, "resolution": {"width": self.w, "height": self.h}, "elements": self.elements,
				"outliner": self.outliner, "textures": [{"path": "", "name": name + ".png", "folder": "", "namespace": "",
				"id": "0", "width": self.w, "height": self.h, "uv_width": self.w, "uv_height": self.h, "particle": False,
				"render_mode": "default", "visible": True, "mode": "bitmap", "saved": True, "uuid": uid(), "source": src}],
				"animations": []}


def mirror(b, name, parent, origin, cubes, rotation=Z):
	"""A right-hand bone and its cubes (at +x) and its left twin (x flipped)."""
	for side, s in (("right", 1), ("left", -1)):
		o = (origin[0] * s, origin[1], origin[2])
		b.bone(f"{side}_{name}", parent if not isinstance(parent, tuple) else f"{side}_{parent[0]}", o,
			   (rotation[0], rotation[1] * s, rotation[2] * s))
		for frm, to, mat, *rest in cubes:
			x0, x1 = sorted((frm[0] * s, to[0] * s))
			b.cube(f"{side}_{name}", (x0, frm[1], frm[2]), (x1, to[1], to[2]), mat, *(rest or []))


def eyes(color, at, px=1):
	def fx(img, r):
		x0, y0, x1, y1 = r
		w = x1 - x0
		for ex in (x0 + at[0], x1 - at[0] - px):
			for dx in range(px):
				img.putpixel((ex + dx, y0 + at[1]), color + (255,))
	return fx


# ---------------------------------------------------------------- the draugr

# The tail: each segment's width and length (Blockbench units), the first one's droop and the rest's curl (degrees,
# +x hangs it lower), and how far it sways side to side (degrees) standing and walking.
TAIL = [(2.0, 3.0), (1.8, 3.0), (1.6, 2.8), (1.4, 2.6), (1.2, 2.4)]
TAIL_DROOP, TAIL_CURL = 55, -10
TAIL_IDLE_SWAY, TAIL_WALK_SWAY = 4, 7


def tail_sway(length, degrees):
	"""Side to side, each segment a little after the one before."""
	tracks = {}
	for i in range(1, len(TAIL)):
		lag = length * 0.12 * i
		keys = [(0, (0, degrees * math.sin(-lag / length * 2 * math.pi), 0))]
		for k in (0.25, 0.5, 0.75, 1.0):
			keys.append((round(length * k, 4), (0, degrees * math.sin((k - lag / length) * 2 * math.pi), 0)))
		tracks[f"tail_{i}"] = {"rotation": keys}
	return tracks


def draugr():
	b = Builder(128, 128, 11)
	b.bone("root", None, (0, 0, 0))
	mirror(b, "leg", "root", (2.5, 14, 0), [((0.5, 0, -2), (4.5, 14, 2), "skin"), ((0.25, 0, -2.5), (4.75, 5, 2.25), "fur", 0.25),
											  ((0.4, 9, -2.2), (4.6, 14, 2.2), "leather")])
	b.bone("waist", "root", (0, 14, 0))
	b.cube("waist", (-5, 13, -3), (5, 16, 3), "leather", 0.1)
	b.cube("waist", (-1, 13.5, -3.6), (1, 15.5, -3.1), "gold")
	b.cube("waist", (-5, 8, -3), (5, 13, 3), "mail", 0.05)
	b.bone("body", "waist", (0, 16, 0))
	b.cube("body", (-5, 16, -3), (5, 27, 3), "mail")
	b.cube("body", (-4, 18, -3.3), (4, 26, -3.0), "skin")
	b.cube("body", (-6, 24, -4), (6, 28, 4), "fur", 0.2)
	b.bone("head", "body", (0, 27, 0))
	face = eyes(PALETTE["eye"], (2, 4))
	b.cube("head", (-4, 27, -4), (4, 35, 4), "skin", faces_paint={"north": face})
	b.cube("head", (-3, 25.5, -4.6), (3, 28.5, -3.4), "fur")
	b.cube("head", (-4.5, 31, -4.5), (4.5, 36, 4.5), "iron", 0.1)
	b.cube("head", (-0.5, 28.5, -5.2), (0.5, 32, -4.6), "iron")
	mirror(b, "horn", "head", (4.5, 34, 0), [((4.5, 33, -1), (7, 35, 1), "horn"), ((6, 35, -0.75), (7.5, 39, 0.75), "horn")],
		   (0, 0, -15))
	mirror(b, "arm", "body", (6, 25, 0), [((5, 14, -2), (9, 27, 2), "skin"), ((4.5, 23, -2.5), (9.5, 28.5, 2.5), "iron", 0.1),
										  ((5, 14, -2.2), (9, 17, 2.2), "leather", 0.15)])
	b.bone("sword", "right_arm", (7, 15, 0))
	b.cube("sword", (6.5, 13.5, -1.5), (7.5, 14.75, 1.5), "leather")
	b.cube("sword", (5, 13.5, -2.5), (9, 15, -1.5), "gold")
	b.cube("sword", (6.25, 13.75, -16), (7.75, 14.75, -2.5), "steel")
	b.bone("axe", "right_arm", (7, 15, 0))
	b.cube("axe", (6.5, 13.5, -16), (7.5, 14.5, 4), "wood")
	b.cube("axe", (6.25, 14.5, -16), (7.75, 18, -12), "steel")
	b.cube("axe", (6.25, 10, -16), (7.75, 14.5, -13.5), "steel")
	b.bone("shield", "left_arm", (-7, 19, 0))
	b.cube("shield", (-10.5, 12, -6), (-9.5, 25, 6), "wood")
	b.cube("shield", (-10.6, 12, -6), (-9.4, 13, 6), "iron")
	b.cube("shield", (-10.6, 24, -6), (-9.4, 25, 6), "iron")
	b.cube("shield", (-11.5, 16.5, -1.5), (-8.5, 19.5, 1.5), "iron")
	# (last, so the rest of the sheet stays where it was) a Warrior-born's tail from under the mail, rimed with frost
	# toward the tip; each draugr's own is mangled in its own way (InhabitantsClient.DraugrRenderer)
	z, parent = 3.0, "waist"
	for i, (w, length) in enumerate(TAIL):
		b.bone(f"tail_{i}", parent, (0, 11, z), (TAIL_DROOP if i == 0 else TAIL_CURL, 0, 0))
		b.cube(f"tail_{i}", (-w / 2, 11 - w / 2, z), (w / 2, 11 + w / 2, z + length), "fur" if i < 2 else "rime")
		z, parent = z + length, f"tail_{i}"
	model = b.project("draugr")
	R = "root"
	walk = {
		"right_leg": {"rotation": [(0, (25, 0, 0)), (0.5, (-25, 0, 0)), (1.0, (25, 0, 0))]},
		"left_leg": {"rotation": [(0, (-25, 0, 0)), (0.5, (25, 0, 0)), (1.0, (-25, 0, 0))]},
		"right_arm": {"rotation": [(0, (5, 0, 4)), (0.5, (25, 0, 4)), (1.0, (5, 0, 4))]},
		"left_arm": {"rotation": [(0, (25, 0, -4)), (0.5, (5, 0, -4)), (1.0, (25, 0, -4))]},
		"waist": {"rotation": [(0, (0, 6, 0)), (0.5, (0, -6, 0)), (1.0, (0, 6, 0))]},
		"body": {"rotation": [(0, (-4, -4, 0)), (0.5, (-4, 4, 0)), (1.0, (-4, -4, 0))]},
		R: {"position": [(0, Z), (0.25, (0, 0.6, 0)), (0.5, Z), (0.75, (0, 0.6, 0)), (1.0, Z)]},
	}
	walk.update(tail_sway(1.0, TAIL_WALK_SWAY))
	anims = [
		anim("idle", 3.0, "loop", {
			**tail_sway(3.0, TAIL_IDLE_SWAY),
			"body": {"rotation": [(0, (-3, 0, 0)), (1.5, (-6, 0, 0)), (3.0, (-3, 0, 0))]},
			"head": {"rotation": [(0, (6, 0, 0)), (1.5, (10, 4, 2)), (3.0, (6, 0, 0))]},
			"right_arm": {"rotation": [(0, (25, 0, 6)), (1.5, (28, 0, 8)), (3.0, (25, 0, 6))]},
			"left_arm": {"rotation": [(0, (35, 0, -8)), (1.5, (38, 0, -10)), (3.0, (35, 0, -8))]},
			"shield": {"rotation": [(0, (0, 40, 0)), (3.0, (0, 40, 0))]},
		}),
		anim("walk", 1.0, "loop", walk),
		anim("attack_sword", 0.8, "once", {
			"body": {"rotation": [(0, Z), (0.25, (0, 30, 0)), (0.45, (-12, -30, 0)), (0.8, Z)]},
			"right_arm": {"rotation": [(0, (25, 0, 6)), (0.25, (150, 0, 35)), (0.45, (35, 0, -10)), (0.8, (25, 0, 6))]},
			"left_arm": {"rotation": [(0, (35, 0, -8)), (0.45, (20, 0, -12)), (0.8, (35, 0, -8))]},
		}),
		anim("attack_axe", 1.2, "once", {
			"body": {"rotation": [(0, Z), (0.45, (12, 0, 0)), (0.65, (-28, 0, 0)), (1.2, Z)]},
			"right_arm": {"rotation": [(0, (25, 0, 6)), (0.45, (175, 0, 10)), (0.65, (45, 0, 0)), (1.2, (25, 0, 6))]},
			"left_arm": {"rotation": [(0, (35, 0, -8)), (0.45, (170, 0, -10)), (0.65, (45, 0, 0)), (1.2, (35, 0, -8))]},
			R: {"position": [(0, Z), (0.65, (0, -1.5, 0)), (1.2, Z)]},
		}),
		anim("block", 0.5, "hold", {
			"left_arm": {"rotation": [(0, (35, 0, -8)), (0.2, (85, 0, 0)), (0.5, (85, 0, 0))]},
			"shield": {"rotation": [(0, (0, 40, 0)), (0.2, (0, 0, 90)), (0.5, (0, 0, 90))]},
			"body": {"rotation": [(0, Z), (0.2, (-8, 12, 0)), (0.5, (-8, 12, 0))]},
		}),
		anim("guard_broken", 1.0, "once", {
			"left_arm": {"rotation": [(0, (85, 0, 0)), (0.2, (20, 0, -60)), (1.0, (35, 0, -8))]},
			"shield": {"rotation": [(0, (0, 0, 90)), (0.2, (0, 40, 0)), (1.0, (0, 40, 0))]},
			"body": {"rotation": [(0, Z), (0.2, (12, -15, 0)), (1.0, Z)]},
			"head": {"rotation": [(0, Z), (0.2, (15, 0, 0)), (1.0, Z)]},
		}),
	]
	add_animations(model, anims, bone_uuids_of(model))
	return model


# ---------------------------------------------------------------- the barrow beetle (the example mob)

def beetle():
	b = Builder(64, 64, 5)
	b.bone("root", None, (0, 0, 0))
	b.bone("body", "root", (0, 3, 0))
	b.cube("body", (-3, 2, -3), (3, 5, 4), "shell")
	b.bone("head", "body", (0, 3.5, -3))
	b.cube("head", (-1.5, 2, -5.5), (1.5, 4.5, -3), "leg", faces_paint={"north": eyes(PALETTE["eye"], (0, 1))})
	mirror(b, "antenna", "head", (1, 4.5, -5.5), [((0.5, 4.5, -8.5), (1.0, 5.0, -5.5), "leg")], (20, 0, 0))
	b.bone("shell", "body", (0, 5, -2.5))
	mirror(b, "wing", "shell", (0.05, 5, -2.5), [((0.05, 4.8, -2.8), (3.4, 6.2, 4.6), "shell")])
	for i, z in enumerate((-2, 0.5, 3)):
		mirror(b, f"leg{i}", "body", (2.8, 2.6, z), [((2.8, 0, z - 0.35), (4.3, 2.6, z + 0.35), "leg")], (0, 0, 20))
	model = b.project("barrow_beetle")
	leg = {}
	for i, ph in enumerate((0, 0.15, 0.3)):
		for side, s in (("right", 1), ("left", -1)):
			sw = 25 if (i + (side == "left")) % 2 else -25
			leg[f"{side}_leg{i}"] = {"rotation": [(0, (0, sw, 20 * s)), (0.15, (0, -sw, 20 * s)), (0.3, (0, sw, 20 * s))]}
	anims = [
		anim("idle", 2.0, "loop", {
			"right_antenna": {"rotation": [(0, (20, 0, 0)), (1.0, (35, 10, 0)), (2.0, (20, 0, 0))]},
			"left_antenna": {"rotation": [(0, (20, 0, 0)), (1.0, (30, -10, 0)), (2.0, (20, 0, 0))]},
			"body": {"position": [(0, Z), (1.0, (0, 0.15, 0)), (2.0, Z)]},
		}),
		anim("walk", 0.3, "loop", dict(leg, body={"rotation": [(0, (0, 4, 0)), (0.15, (0, -4, 0)), (0.3, (0, 4, 0))]})),
		anim("flutter", 0.6, "once", {
			"right_wing": {"rotation": [(0, Z), (0.15, (-30, 0, -40)), (0.3, (-10, 0, -10)), (0.45, (-30, 0, -40)), (0.6, Z)]},
			"left_wing": {"rotation": [(0, Z), (0.15, (-30, 0, 40)), (0.3, (-10, 0, 10)), (0.45, (-30, 0, 40)), (0.6, Z)]},
		}),
	]
	add_animations(model, anims, bone_uuids_of(model))
	return model


# ---------------------------------------------------------------- new moves for the borrowed models

def silver_skeleton():
	with open(os.path.join(SRC, "dead-traveler-skeleton.bbmodel"), encoding="utf-8") as f:
		model = json.load(f)
	rise = anim("rise", 2.0, "once", {
		"root": {"rotation": [(0, (90, 0, 0)), (0.45, (88, 0, 0)), (0.95, (45, 0, 0)), (1.35, (8, 0, 0)), (1.6, (0, 0, 0)), (2.0, Z)],
				 "position": [(0, (0, 2, 0)), (0.95, (0, 1, 0)), (1.35, Z), (2.0, Z)]},
		"waist": {"rotation": [(0, Z), (0.95, (-25, 0, 0)), (1.35, (-15, 0, 0)), (1.6, (-5, 0, 0)), (2.0, Z)]},
		"head": {"rotation": [(0, (-20, 0, 0)), (0.45, (-35, 10, 0)), (0.95, (-10, 0, 0)), (1.6, (5, -20, 0)), (1.8, (5, 20, 0)), (2.0, Z)]},
		"lower_jaw": {"rotation": [(0, Z), (1.6, (25, 0, 0)), (1.8, (5, 0, 0)), (2.0, Z)]},
		"right_arm": {"rotation": [(0, (10, 0, 0)), (0.45, (155, 0, 10)), (0.95, (70, 0, 20)), (1.35, (20, 0, 10)), (2.0, Z)]},
		"left_arm": {"rotation": [(0, (10, 0, 0)), (0.3, (20, 0, -10)), (0.95, (60, 0, -25)), (1.35, (15, 0, -10)), (2.0, Z)]},
		"right_leg": {"rotation": [(0, Z), (0.95, (60, 0, 0)), (1.35, (20, 0, 0)), (2.0, Z)]},
		"left_leg": {"rotation": [(0, Z), (0.95, (45, 0, 0)), (1.35, (10, 0, 0)), (2.0, Z)]},
	})
	add_animations(model, [rise], bone_uuids_of(model))
	return model


def skeleton_lord():
	with open(os.path.join(SRC, "skeleton-lord.bbmodel"), encoding="utf-8") as f:
		model = json.load(f)
	swing = anim("staff_swing", 1.0, "once", {
		"chest": {"rotation": [(0, Z), (0.3, (5, 35, 0)), (0.45, (-15, -30, 0)), (1.0, Z)]},
		"waist": {"rotation": [(0, Z), (0.3, (0, 10, 0)), (0.45, (-5, -10, 0)), (1.0, Z)]},
		"L_arm": {"rotation": [(0, Z), (0.3, (150, 0, -25)), (0.45, (40, 0, -10)), (1.0, Z)]},
		"L_lowerarm": {"rotation": [(0, Z), (0.3, (40, 0, 0)), (0.45, (10, 0, 0)), (1.0, Z)]},
		"R_arm": {"rotation": [(0, Z), (0.3, (-20, 0, 20)), (0.45, (30, 0, 10)), (1.0, Z)]},
		"head": {"rotation": [(0, Z), (0.3, (0, -20, 0)), (0.45, (5, 15, 0)), (1.0, Z)]},
	})
	slam = anim("slam", 1.5, "once", {
		"skeleton": {"position": [(0, Z), (0.5, (0, 1, 0)), (0.75, (0, -5, 0)), (1.1, (0, -5, 0)), (1.5, Z)]},
		"chest": {"rotation": [(0, Z), (0.5, (15, 0, 0)), (0.75, (-35, 0, 0)), (1.1, (-30, 0, 0)), (1.5, Z)]},
		"waist": {"rotation": [(0, Z), (0.5, (5, 0, 0)), (0.75, (-12, 0, 0)), (1.1, (-10, 0, 0)), (1.5, Z)]},
		"L_arm": {"rotation": [(0, Z), (0.5, (150, 0, -5)), (0.75, (30, 0, 5)), (1.1, (28, 0, 5)), (1.5, Z)]},
		"R_arm": {"rotation": [(0, Z), (0.5, (150, 0, 5)), (0.75, (40, 0, -5)), (1.1, (38, 0, -5)), (1.5, Z)]},
		"L_leg": {"rotation": [(0, Z), (0.75, (-30, 0, 0)), (1.1, (-30, 0, 0)), (1.5, Z)]},
		"L_lowerleg": {"rotation": [(0, Z), (0.75, (45, 0, 0)), (1.1, (45, 0, 0)), (1.5, Z)]},
		"R_leg": {"rotation": [(0, Z), (0.75, (-30, 0, 0)), (1.1, (-30, 0, 0)), (1.5, Z)]},
		"R_lowerleg": {"rotation": [(0, Z), (0.75, (45, 0, 0)), (1.1, (45, 0, 0)), (1.5, Z)]},
		"jaw": {"rotation": [(0, Z), (0.75, (25, 0, 0)), (1.1, (10, 0, 0)), (1.5, Z)]},
	})
	call = anim("grave_call", 2.0, "once", {
		"chest": {"rotation": [(0, Z), (0.6, (15, 0, 0)), (1.2, (18, 0, 0)), (1.6, (-12, 0, 0)), (2.0, Z)]},
		"head": {"rotation": [(0, Z), (0.6, (25, 0, 0)), (1.2, (28, 0, 0)), (1.6, (-5, 0, 0)), (2.0, Z)]},
		"jaw": {"rotation": [(0, Z), (0.6, (30, 0, 0)), (0.9, (15, 0, 0)), (1.2, (30, 0, 0)), (1.6, (5, 0, 0)), (2.0, Z)]},
		"L_arm": {"rotation": [(0, Z), (0.6, (165, 0, -10)), (1.2, (168, 0, -12)), (1.6, (60, 0, -5)), (2.0, Z)]},
		"R_arm": {"rotation": [(0, Z), (0.6, (20, 0, 75)), (1.2, (-10, 0, 80)), (1.6, (40, 0, 30)), (2.0, Z)]},
		"R_hand": {"rotation": [(0, Z), (0.6, (0, 0, -30)), (1.2, (0, 0, -40)), (2.0, Z)]},
	})
	add_animations(model, [swing, slam, call], bone_uuids_of(model))
	return model


EGGS = {"draugr": ((84, 94, 104), (160, 232, 255)), "barrow_beetle": ((36, 50, 44), (120, 160, 140)),
		"bogre": ((92, 110, 70), (150, 112, 74))}


def eggs():
	"""Spawn eggs: the Skeleton Lord's egg's shape, shaded in each mob's two colours (base, spots)."""
	assets = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms")
	src = Image.open(os.path.join(assets, "textures", "item", "skeleton_lord_spawn_egg.png")).convert("RGBA")
	for name, (base, spots) in EGGS.items():
		img = Image.new("RGBA", src.size)
		for y in range(src.height):
			for x in range(src.width):
				r, g, b, a = src.getpixel((x, y))
				if a == 0:
					continue
				lum = (r * 0.3 + g * 0.59 + b * 0.11) / 255.0
				spot = (x * 7 + y * 13) % 11 < 2 and 0.35 < lum < 0.9
				img.putpixel((x, y), shade(spots if spot else base, 0.55 + lum * 0.9) + (a,))
		img.save(os.path.join(assets, "textures", "item", name + "_spawn_egg.png"))
		with open(os.path.join(assets, "items", name + "_spawn_egg.json"), "w", encoding="utf-8", newline="\n") as f:
			f.write('{\n\t"model": {\n\t\t"type": "minecraft:model",\n\t\t"model": "jaymoskingdoms:item/%s_spawn_egg"\n\t}\n}\n' % name)
		with open(os.path.join(assets, "models", "item", name + "_spawn_egg.json"), "w", encoding="utf-8", newline="\n") as f:
			f.write('{\n\t"parent": "minecraft:item/generated",\n\t"textures": {\n\t\t"layer0": "jaymoskingdoms:item/%s_spawn_egg"\n\t}\n}\n'
					% name)
		print("wrote the " + name + " spawn egg")


def main(only=()):
	"""Writes every project, or only those named on the command line (and the spawn eggs only with all of them)."""
	if not only:
		eggs()
	os.makedirs(ART, exist_ok=True)
	for name, make in (("draugr", draugr), ("barrow_beetle", beetle), ("silver_skeleton", silver_skeleton), ("skeleton_lord", skeleton_lord)):
		if only and name not in only:
			continue
		with open(os.path.join(ART, name + ".bbmodel"), "w", encoding="utf-8", newline="\n") as f:
			json.dump(make(), f)
		print("wrote kingdoms/art/" + name + ".bbmodel")


if __name__ == "__main__":
	main(sys.argv[1:])
