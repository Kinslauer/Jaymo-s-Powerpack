"""Zephyrus's editable Blockbench model and GeckoLib animations, rebuilt from our own Java model.

The geometry is the old ZephyrusDragonModel.createBodyLayer(), box for box, and its UVs are kingdoms/tools/gen_zephyrus.py's
256x256 table (PARTS / pack()), so the four hides and four marking sheets in textures/entity/dragon/zephyrus/ fit unchanged.

The old ZephyrusDragonModel.setupAnim is the animation spec: pose() below is it, line for line, and every animation is
sampled from it: idle, walk, fly (a wingbeat), hover (beating hard while it breathes in the air), glide, land, sit,
crash, swim. bite, sweep and death are new (the old model had none): the first two for WindDragon.bitten / swept,
the death in place of the vanilla sideways flip. What followed the game every frame (where it looks, its pitch with a climb, the breath's jaw
and neck) stays in ZephyrusDragonRenderer.

Keyframes are the game's way (degrees as ModelPart angles; positions with x and y turned round), as a "free" project
stores them. Run from the repository root: python3 -I kingdoms/tools/gen_zephyrus_geo.py
  --compare <dir>: also bake to scratch and render each state, the old pose beside the new animation.
"""
import base64
import json
import math
import os
import subprocess
import sys
import uuid

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, "kingdoms", "tools"))
import gen_zephyrus as art  # noqa: E402  (its UV table)

NAME = "zephyrus"
MODEL = os.path.join(ROOT, "kingdoms", "art", NAME + ".bbmodel")
ASSETS = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms")
HIDE = os.path.join(ASSETS, "textures", "entity", "dragon", "zephyrus", "hide_0.png")
SIZE = 256
PI = math.pi

# ---- the old model's tuning (ZephyrusDragonModel, radians and model pixels) ------------------------------------------
FLAP_DEPTH, GLIDE_CUT, TIP_LAG, TIP_SWING = 0.8, 0.8, 1.1, 0.5
FOLD_PITCH, FOLD_SWEEP, FOLD_DROOP, FOLD_TIP = -1.0, -1.45, -0.25, PI - 0.25
TIGHT_SWEEP, TIGHT_DROOP = -1.55, -0.05
NECK_GROUND, NECK_FLIGHT, NECK_CRASH, NECK_SWIM = [-0.35, 0.1, 0.15], [-0.1, 0.02, 0.03], [0.1, 0.0, 0.0], [-0.04, 0.06, 0.1]
TAIL_GROUND, TAIL_FLIGHT = [-0.1, 0.05, 0.06, 0.06, 0.05], [0.03, 0.02, 0.02, 0.02, 0.02]
CRASH_DROP, SIT_DROP, SWIM_DROP, SPLAY = 3.0, 3.0, 2.5, 0.3

# ---- WindDragon's client easing, which the controllers' speeds follow -------------------------------------------------
FLAP_RATE, HOVER_RATE, GLIDE_RATE = 0.32, 0.3, 0.32 * 0.3   # flapPhase a tick: level flight, breathing, gliding
WALK_AT, STEP = 0.6, 0.6662                                 # the walk speed the walk is keyed at; walkAnimationPos to step
LAND_TICKS = 1.0 / 0.06                                     # landAnim falls from 1 to 0
# loop lengths (ticks): the slow sways get a whole number of cycles in each
IDLE_TICKS = 40.0 * PI                                      # 6.28 s
FLY_BEATS = 4
FLY_TICKS, HOVER_TICKS, GLIDE_TICKS = FLY_BEATS * 2 * PI / FLAP_RATE, FLY_BEATS * 2 * PI / HOVER_RATE, 2 * PI / GLIDE_RATE
WALK_TICKS = 4 * PI / STEP / WALK_AT                        # two strides (its roll is half the stride)
BITE_LEN, SWEEP_LEN, DEATH_LEN = 0.6, 1.0, 1.0

LEGS = ["left_front", "right_front", "left_hind", "right_hind"]
NECK = ["neck0", "neck1", "neck2"]
TAIL = ["tail0", "tail1", "tail2", "tail3", "tail4"]

# ---- the geometry: ZephyrusDragonModel.createBodyLayer() ------------------------------------------------------------
# (bone, parent, pivot (game pixels, relative to the parent), rest rotation (radians), [(uv part, box, mirrored)])
# texOffs are checked against gen_zephyrus's packed table.
TEX = {"body": (0, 0), "dorsal": (32, 82), "saddle": (24, 66), "pommel": (182, 95), "cantle": (194, 95), "strap": (84, 66),
	   "pad": (46, 82), "neck0": (140, 66), "neck1": (176, 66), "neck2": (0, 82), "skull": (166, 44), "snout": (168, 82),
	   "jaw": (0, 95), "eye": (160, 95), "frill": (94, 82), "horn": (48, 95), "horn_tip": (80, 95), "horn_short": (136, 95),
	   "horn_short_tip": (172, 95), "brow_horn": (148, 95), "tail0": (108, 44), "tail1": (138, 44), "tail2": (220, 44),
	   "tail3": (0, 66), "tailfin": (210, 66), "paddle": (84, 44), "front_upper": (200, 82), "back_upper": (108, 82),
	   "front_lower": (64, 95), "back_lower": (28, 95), "front_foot": (220, 82), "back_foot": (132, 82),
	   "wing_bone": (94, 95), "wing_skin": (112, 0), "tip_bone": (0, 103), "tip_skin": (0, 44)}


def geometry():
	g = []

	def part(name, parent, pivot, rot, cubes):
		g.append((name, parent, pivot, rot, cubes))

	part("body", None, (0, 12, 0), (0, 0, 0), [("body", (-11, -5, -17, 22, 10, 34), False)])
	for i, z in enumerate((-12, -6, 0, 6, 12)):
		part("spike%d" % i, "body", (0, -5, z), (0, 0, 0), [("dorsal", (-0.5, -7, -3, 1, 7, 6), False)])
	part("saddle", "body", (0, -5, -4), (0, 0, 0), [("saddle", (-8, -2, -7, 16, 2, 14), False), ("pommel", (-2, -5, -7, 4, 3, 2), False),
												   ("cantle", (-6, -5, 5, 12, 3, 2), False)])
	part("strap", "body", (0, 0, -4), (0, 0, 0), [("strap", (-12.5, -6.5, -1.5, 25, 13, 3), False)])
	part("pad", "body", (0, -5, 9), (0, 0, 0), [("pad", (-7, -2, -5, 14, 2, 10), False)])
	parent = "body"
	for i, (w, h) in enumerate(((12, 8), (11, 8), (10, 7))):
		part("neck%d" % i, parent, (0, -1, -15) if i == 0 else (0, 0, -5), (0, 0, 0), [("neck%d" % i, (-w / 2, -h / 2, -6, w, h, 6), False)])
		parent = "neck%d" % i
	part("head", "neck2", (0, 0, -5), (0, 0, 0), [("skull", (-8, -3, -10, 16, 6, 11), False), ("snout", (-5, -1, -16, 10, 4, 6), False)])
	part("jaw", "head", (0, 3, -10), (0, 0, 0), [("jaw", (-4, 0, -6, 8, 2, 6), False)])
	part("eye_l", "head", (5.5, -3, -6), (0, 0, 0), [("eye", (-1.5, -2, -1.5, 3, 2, 3), False)])
	part("eye_r", "head", (-5.5, -3, -6), (0, 0, 0), [("eye", (-1.5, -2, -1.5, 3, 2, 3), False)])
	frill = [("frill", (-0.5, -2.5, 0, 1, 5, 6), False)]
	part("frill_l0", "head", (8, -1, -4), (0, 0.5, 0), frill)
	part("frill_l1", "head", (8, 1.5, -3), (0, 0.85, 0.3), frill)
	part("frill_r0", "head", (-8, -1, -4), (0, -0.5, 0), frill)
	part("frill_r1", "head", (-8, 1.5, -3), (0, -0.85, -0.3), frill)
	for name, side, base_uv, tip_uv, base, tip in (("horn_l", 1, "horn", "horn_tip", 6, 5), ("horn_r", -1, "horn", "horn_tip", 6, 5),
											   ("short_horn_l", 1, "horn_short", "horn_short_tip", 4, 3),
											   ("short_horn_r", -1, "horn_short", "horn_short_tip", 4, 3)):
		part(name, "head", (4 * side, -3, -1), (0.35, 0.3 * side, 0), [(base_uv, (-1, -1, 0, 2, 2, base), False)])
		part(name + "_tip", name, (0, 0, base), (0.5, 0, 0), [(tip_uv, (-1, -1, 0, 2, 2, tip), False)])
	part("brow_horn_l", "head", (7, -2.5, -6), (0.15, 0.9, 0), [("brow_horn", (-1, -1, 0, 2, 2, 4), False)])
	part("brow_horn_r", "head", (-7, -2.5, -6), (0.15, -0.9, 0), [("brow_horn", (-1, -1, 0, 2, 2, 4), False)])
	parent = "body"
	for i, (w, h) in enumerate(((6, 10), (5, 9), (4, 8), (3, 7))):
		part("tail%d" % i, parent, (0, -1, 16) if i == 0 else (0, 0, 8), (0, 0, 0),
			 [("tail%d" % i, (-w / 2, -h / 2, 0, w, h, 9), False), ("tailfin", (-0.5, -h / 2 - 5, 0, 1, 5, 9), False)])
		parent = "tail%d" % i
	part("tail4", "tail3", (0, 0, 8), (0, 0, 0), [("paddle", (-1, -6, 0, 2, 12, 10), False)])
	for name, mirror, x, z, front in (("left_front", False, 9, -10, True), ("right_front", True, -9, -10, True),
									  ("left_hind", False, 9, 11, False), ("right_hind", True, -9, 11, False)):
		up = ("front_upper", (-2.5, -1, -2.5, 5, 5, 5)) if front else ("back_upper", (-3, -1, -3, 6, 5, 6))
		shin = ("front_lower", (-2, 0, -2, 4, 3, 4)) if front else ("back_lower", (-2.5, 0, -2.5, 5, 3, 5))
		web = ("front_foot", (-4, 0, -5, 8, 2, 7)) if front else ("back_foot", (-4.5, 0, -6, 9, 2, 9))
		part(name + "_leg", "body", (x, 3, z), (0, 0, 0), [(up[0], up[1], mirror)])
		part(name + "_shin", name + "_leg", (0, 4, 0), (0, 0, 0), [(shin[0], shin[1], mirror)])
		part(name + "_foot", name + "_shin", (0, 3, 0), (0, 0, 0), [(web[0], web[1], mirror)])
	part("left_wing", "body", (10, -4, -9), (0, 0, 0), [("wing_bone", (0, -1.5, -1.5, 18, 3, 3), False), ("wing_skin", (0, 0, 1.5, 18, 0, 26), False)])
	part("left_wing_tip", "left_wing", (17, 0, 0), (0, 0, 0), [("tip_bone", (0, -1, -1, 20, 2, 2), False), ("tip_skin", (0, 0, 1, 20, 0, 22), False)])
	# (the right wing's builder is mirror()ed, so its skin is mirrored as well as its bones)
	part("right_wing", "body", (-10, -4, -9), (0, 0, 0), [("wing_bone", (-18, -1.5, -1.5, 18, 3, 3), True), ("wing_skin", (-18, 0, 1.5, 18, 0, 26), True)])
	part("right_wing_tip", "right_wing", (-17, 0, 0), (0, 0, 0), [("tip_bone", (-20, -1, -1, 20, 2, 2), True), ("tip_skin", (-20, 0, 1, 20, 0, 22), True)])
	return g


GEOMETRY = geometry()
REST = {name: rot for name, _, _, rot, _ in GEOMETRY}


def uid(*parts):
	return str(uuid.uuid5(uuid.NAMESPACE_URL, "jaymoskingdoms:zephyrus:" + ":".join(map(str, parts))))


def face_uv(part, mirror):
	"""Vanilla's box UV as Blockbench's per-face UVs (Blockbench's own box-UV maths), with CubeListBuilder.mirror()."""
	u, v = art.UV[part]
	w, h, d = art.DIMS[part]
	out = {"north": [u + d, v + d, u + d + w, v + d + h], "east": [u, v + d, u + d, v + d + h],
		   "south": [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h], "west": [u + d + w, v + d, u + 2 * d + w, v + d + h],
		   "up": [u + d + w, v + d, u + d, v], "down": [u + d + w, v, u + d + 2 * w, v + d]}
	if mirror:
		out["east"], out["west"] = out["west"], out["east"]
		for f in out.values():
			f[0], f[2] = f[2], f[0]
	return out


def make_model():
	for part, at in TEX.items():
		assert art.UV[part] == at, "gen_zephyrus's UV table moved %s: %s, not %s" % (part, art.UV[part], at)
	origins, elements, groups = {}, [], {}
	for name, parent, pivot, rot, cubes in GEOMETRY:
		po = origins[parent] if parent else [0.0, 24.0, 0.0]
		o = [po[0] - pivot[0], po[1] - pivot[1], po[2] + pivot[2]]
		origins[name] = o
		children = []
		for ci, (uv, (x, y, z, w, h, d), mirror) in enumerate(cubes):
			assert art.DIMS[uv] == (w, h, d), uv
			eid = uid("cube", name, ci)
			elements.append({"name": uv, "box_uv": False, "rescale": False, "locked": False,
							 "from": [o[0] - x - w, o[1] - y - h, o[2] + z], "to": [o[0] - x, o[1] - y, o[2] + z + d],
							 "autouv": 0, "color": len(elements) % 8, "origin": list(o), "inflate": 0,
							 "faces": {f: {"uv": r, "texture": 0} for f, r in face_uv(uv, mirror).items()}, "type": "cube", "uuid": eid})
			children.append(eid)
		groups[name] = {"name": name, "origin": o, "rotation": [-math.degrees(rot[0]), -math.degrees(rot[1]), math.degrees(rot[2])],
						"color": 0, "isOpen": False, "export": True, "visibility": True, "mirror_uv": False, "locked": False,
						"uuid": uid("bone", name), "children": children, "_parent": parent}
	outliner = []
	for name, gr in groups.items():
		parent = gr.pop("_parent")
		(groups[parent]["children"] if parent else outliner).append(gr)
	with open(HIDE, "rb") as f:
		png = base64.b64encode(f.read()).decode("ascii")
	return {"meta": {"format_version": "4.10", "model_format": "free", "box_uv": False}, "name": NAME, "model_identifier": NAME,
			"visible_box": [6, 4, 0], "resolution": {"width": SIZE, "height": SIZE}, "elements": elements, "outliner": outliner,
			"textures": [{"name": "hide_0.png", "folder": "zephyrus", "namespace": "jaymoskingdoms", "id": "0", "uuid": uid("texture"),
						  "relative_path": os.path.relpath(HIDE, os.path.dirname(MODEL)).replace(os.sep, "/"),
						  "source": "data:image/png;base64," + png, "width": SIZE, "height": SIZE, "uv_width": SIZE,
						  "uv_height": SIZE, "visible": True}],
			"animations": []}


# ---- the old setupAnim ------------------------------------------------------------------------------------------------

def lerp(k, a, b):
	return a + (b - a) * k


def pose(t=0.0, fly=0.0, crash=0.0, swim=0.0, sit=0.0, land=0.0, glide=0.0, walk=0.0, step=0.0, beat=0.0, hover=0.0,
		 loop=None, look=False, breath=0.0, yaw=0.0, pitch=0.0, head_pitch=0.0):
	"""ZephyrusDragonModel.setupAnim: each bone's angles (radians, the game's way) and the body's drop (pixels, down).

	Its per-frame parts (where it looks, its climb, the breath) only with look=True: the renderer adds them in game.
	`loop` (ticks): the slow sways round to a whole number of cycles over it, so the loop closes. `hover`: the breath's
	effect on the wingbeat (the hover animation)."""
	def f(freq):
		return freq if loop is None else max(1, round(freq * loop / (2 * PI))) * 2 * PI / loop

	sin = math.sin
	crash = crash * (1 - fly)
	swim = swim * (1 - fly) * (1 - crash)
	sit = sit * (1 - crash) * (1 - fly) * (1 - swim)
	land = land * (1 - fly)
	glide = glide * fly
	walk = min(walk, 1.0) * (1 - fly) * (1 - crash) * (1 - swim)
	paddle = t * 0.3
	r = {}
	drop = crash * CRASH_DROP + sit * SIT_DROP + swim * (SWIM_DROP + sin(t * f(0.11)) * 0.6) \
		- sin(beat) * 0.8 * fly * (1 - glide) + sin(t * f(0.1)) * 0.25
	r["body"] = [swim * sin(t * f(0.09)) * 0.03 + (pitch * fly if look else 0), 0.0,
				 sin(step * 0.5) * 0.06 * walk + swim * sin(paddle * 0.5) * 0.04]
	for i in range(3):
		x = lerp(fly, NECK_GROUND[i], NECK_FLIGHT[i])
		x = lerp(crash, x, NECK_CRASH[i])
		x = lerp(swim, x, NECK_SWIM[i])
		if look:
			x += breath * (-0.15 if i == 0 else 0.05) * (1 - fly)
		x += sin(t * f(0.08) - i * 0.7) * 0.03 * (1 + fly)
		y = sin(t * f(0.05) - i * 0.6) * 0.03 + swim * sin(paddle * 0.5 - i * 0.5) * 0.08 + (yaw / 4 if look else 0)
		r[NECK[i]] = [x, y, 0.0]
	head_x = lerp(swim, lerp(fly, 0.2, 0.08), 0.02) + lerp(crash, head_pitch * 0.5 if look else 0.0, 0.1)
	r["head"] = [head_x - (breath * 0.2 if look else 0), yaw / 4 if look else 0, 0.0]
	r["jaw"] = [0.04 + ((breath * 0.65 + sin(t * 0.6) * 0.05 * breath) if look else 0), 0.0, 0.0]
	sway = 0.07 + walk * 0.12
	tail_f = f(0.07) if loop is None or walk == 0 else 2 * PI / loop   # walking: one sway each two strides
	for i in range(5):
		x = lerp(crash, lerp(fly, TAIL_GROUND[i], TAIL_FLIGHT[i]), 0.0)
		x += sin(t * f(0.06) - i * 0.5) * 0.02 * fly
		wave = sin(t * tail_f - i * 0.6) * lerp(fly, sway, 0.04)
		r[TAIL[i]] = [x, lerp(swim, wave, sin(paddle - i * 0.8) * 0.34), 0.0]
	for i, name in enumerate(LEGS):
		front, left = i < 2, i % 2 == 0
		phase = 0.0 if front == left else PI
		up = math.cos(step + phase) * 0.5 * walk
		low = max(0.0, -sin(step + phase)) * 0.5 * walk
		up = lerp(fly, up, (0.25 if front else 1.0) + sin(t * f(0.1) + i) * 0.05)
		low = lerp(fly, low, 0.7 if front else 0.8)
		up = lerp(land, up, -0.5 if front else -0.3)
		if not front:
			up, low = lerp(sit, up, -0.7), lerp(sit, low, 1.2)
		stroke = sin(paddle + phase)
		up = lerp(swim, up, stroke * 0.7 + (-0.2 if front else 0.5))
		low = lerp(swim, low, 0.5 + max(0.0, -stroke) * 0.5)
		splay = SPLAY * (1 - fly * 0.7) + crash * 0.25 + swim * 0.7
		r[name + "_leg"] = [up, 0.0, -splay if left else splay]
		r[name + "_shin"] = [low, 0.0, splay * 0.6 if left else -splay * 0.6]
		r[name + "_foot"] = [lerp(fly, lerp(swim, -(up + low), -0.3 + stroke * 0.3), 0.5), 0.0, 0.0]
	opened = max(fly, land)
	hover = hover * fly
	stroke = sin(beat) * FLAP_DEPTH * (1 - glide * GLIDE_CUT) * (1 + hover * 0.3) * fly + glide * 0.08
	lift = stroke - land * 0.7
	open_sweep = -0.12 + hover * 0.3 + land * 0.3
	tip_up = (sin(beat - TIP_LAG) * TIP_SWING * (1 - glide) - 0.05) * fly
	fold_sweep, fold_droop = lerp(swim, FOLD_SWEEP, TIGHT_SWEEP), lerp(swim, FOLD_DROOP, TIGHT_DROOP)
	arm_x, arm_y, arm_z = lerp(opened, FOLD_PITCH, 0.0), lerp(opened, fold_sweep, open_sweep), lerp(opened, fold_droop, -lift)
	tip_z = lerp(opened, FOLD_TIP, -tip_up)
	arm_x, arm_y, arm_z, tip_z = lerp(crash, arm_x, -0.15), lerp(crash, arm_y, -0.25), lerp(crash, arm_z, 0.3), lerp(crash, tip_z, -0.2)
	arm_z += sin(t * f(0.1)) * 0.02 * (1 - opened)
	r["left_wing"], r["left_wing_tip"] = [arm_x, arm_y, arm_z], [0.0, 0.0, tip_z]
	r["right_wing"], r["right_wing_tip"] = [arm_x, -arm_y, -arm_z], [0.0, 0.0, -tip_z]
	return r, drop


# ---- keyframes --------------------------------------------------------------------------------------------------------

class Anim:
	def __init__(self, name, length, loop):
		self.name, self.length, self.loop = name, length, loop
		self.keys = {}

	def key(self, time, angles, drop=None, bones=None, interp="catmullrom"):
		"""Angles (radians, absolute) as keys off the rest pose; the body's drop as its position."""
		for bone, a in angles.items():
			if bones is not None and bone not in bones:
				continue
			rest = REST[bone]
			v = tuple(math.degrees(a[j] - rest[j]) for j in range(3))
			self.keys.setdefault(bone, {}).setdefault("rotation", []).append((time, v, interp))
		if drop is not None:
			self.keys.setdefault("body", {}).setdefault("position", []).append((time, (0.0, -drop, 0.0), interp))
		return self

	def blockbench(self, uuids):
		animators = {}
		for bone, chans in self.keys.items():
			kfs = []
			for ch, ks in chans.items():
				for i, (t, v, interp) in enumerate(ks):
					kfs.append({"channel": ch, "data_points": [{"x": round(v[0], 4), "y": round(v[1], 4), "z": round(v[2], 4)}],
								"uuid": uid("key", self.name, bone, ch, i), "time": round(t, 4), "color": -1, "interpolation": interp})
			animators[uuids[bone]] = {"name": bone, "type": "bone", "keyframes": kfs}
		return {"uuid": uid("animation", self.name), "name": "animation.zephyrus." + self.name, "loop": self.loop, "override": False,
				"length": round(self.length, 4), "snapping": 24, "selected": False, "anim_time_update": "", "blend_weight": "",
				"start_delay": "", "loop_delay": "", "animators": animators}


def looped(name, ticks, samples, state):
	"""A loop of `ticks`, sampled `samples` times from pose(**state(t)); its last key is its first."""
	a = Anim(name, ticks / 20.0, "loop")
	for i in range(samples + 1):
		t = ticks * (i % samples) / samples
		angles, drop = pose(loop=ticks, **state(t))
		a.key(ticks * i / samples / 20.0, angles, drop)
	return a


# each state at tick t of its loop (the same arguments the comparison renders the old pose with)
STATES = {
	"idle": (IDLE_TICKS, 32, lambda t: dict(t=t)),
	"walk": (WALK_TICKS, 16, lambda t: dict(t=t, walk=1.0, step=STEP * WALK_AT * t)),
	"fly": (FLY_TICKS, 16 * FLY_BEATS, lambda t: dict(t=t, fly=1.0, beat=FLAP_RATE * t)),
	"hover": (HOVER_TICKS, 16 * FLY_BEATS, lambda t: dict(t=t, fly=1.0, hover=1.0, beat=HOVER_RATE * t)),
	"glide": (GLIDE_TICKS, 16, lambda t: dict(t=t, fly=1.0, glide=1.0, beat=GLIDE_RATE * t)),
	"sit": (IDLE_TICKS, 16, lambda t: dict(t=t, sit=1.0)),
	"crash": (IDLE_TICKS, 16, lambda t: dict(t=t, crash=1.0)),
	"swim": (IDLE_TICKS, 96, lambda t: dict(t=t, swim=1.0)),
}
BITE_BONES = set(NECK + ["head", "jaw"])
SWEEP_BONES = {"body"} | set(TAIL)


def build():
	anims = [looped(n, *s) for n, s in STATES.items()]
	# land: wings flared and feet reaching as it touches down, easing to standing (landAnim falls 1 to 0)
	land = Anim("land", LAND_TICKS / 20.0, "once")
	for i in range(9):
		t = LAND_TICKS * i / 8
		angles, drop = pose(t=t, land=1 - i / 8)
		land.key(t / 20.0, angles, drop)
	anims.append(land)
	# bite (new): the short neck draws back with the jaw wide, then snaps forward shut; only the neck, head and jaw, so
	# the legs, wings and tail carry on underneath (it bites standing or swimming)
	base, _ = pose()
	bite = Anim("bite", BITE_LEN, "once")
	for time, neck, head, jaw in ((0.0, 0, 0, 0), (0.15, -0.25, -0.3, 0.7), (0.3, 0.35, 0.2, 0.0), (BITE_LEN, 0, 0, 0)):
		a = {b: list(v) for b, v in base.items() if b in BITE_BONES}
		a["neck0"][0] += neck
		a["neck1"][0] += neck * 0.5
		a["head"][0] += head
		a["jaw"][0] += jaw
		bite.key(time, a, bones=BITE_BONES)
	anims.append(bite)
	# sweep (new): it winds the other way, then swings its body round and lashes the flat tail after it
	sweep = Anim("sweep", SWEEP_LEN, "once")
	for time, body, lag in ((0.0, 0.0, 0.0), (0.25, -0.3, 0.45), (0.55, 0.6, -0.8), (0.75, 0.35, -0.35), (SWEEP_LEN, 0.0, 0.0)):
		a = {b: list(v) for b, v in base.items() if b in SWEEP_BONES}
		a["body"][1] += body
		for i, seg in enumerate(TAIL):
			a[seg][1] += lag * (i + 1) / len(TAIL)
		sweep.key(time, a, bones=SWEEP_BONES)
	anims.append(sweep)
	# death (new; the old model was flipped onto its side by the vanilla renderer): it rears its head with the jaw
	# wide, its legs buckle, then it rolls onto its side (the roll ends with its flank on the ground: the body's pivot
	# is 12 px up and its half width 11), its neck and tail going limp, and holds there as it fades
	death = Anim("death", DEATH_LEN, "hold")
	for time, k, rear in ((0.0, 0.0, 0.0), (0.3, 0.15, 1.0), (0.7, 0.85, 0.3), (DEATH_LEN, 1.0, 0.0)):
		a = {b: list(v) for b, v in base.items()}
		a["body"][2] += k * 1.45
		a["neck0"][0] += -0.35 * rear + 0.35 * k
		a["neck1"][0] += 0.2 * k
		a["head"][0] += -0.3 * rear + 0.25 * k
		a["jaw"][0] += 0.6 * rear + 0.3 * k
		for leg in LEGS:
			a[leg + "_leg"][0] += 0.4 * rear * (1 - k)
			a[leg + "_shin"][0] += 0.6 * (rear + k) * 0.5
		for i, seg in enumerate(TAIL):
			a[seg][0] -= TAIL_GROUND[i] * k
			a[seg][1] += 0.15 * k * (i + 1) / len(TAIL)
		death.key(time, a, drop=lerp(k, 0.0, 1.0) + 1.5 * rear * (1 - k))
	anims.append(death)
	return anims


def compare(out_dir):
	"""Bakes to scratch and renders each state: the old pose (POSE=, worked out by pose() at full state) over the new
	animation at the same moment."""
	from PIL import Image, ImageDraw
	scratch = os.path.join(out_dir, "scratch")
	subprocess.run([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_bake.py"), MODEL, scratch, NAME], check=True, stdout=subprocess.DEVNULL)
	shots = [("idle", 1.0), ("walk", 0.4), ("fly", 0.25), ("fly", 0.75), ("hover", 0.4), ("glide", 0.5), ("land", 0.2), ("sit", 0.0),
			 ("crash", 0.0), ("swim", 0.5), ("bite", 0.15), ("sweep", 0.55), ("death", 1.0)]
	rows = []
	for anim, sec in shots:
		new = os.path.join(out_dir, "%s_%s_new.png" % (anim, sec))
		old = os.path.join(out_dir, "%s_%s_old.png" % (anim, sec))
		env = dict(os.environ, POSE="")
		if anim in ("bite", "sweep"):
			# a move plays over the move controller's loop: the bones it doesn't key stand as in idle
			keyed = BITE_BONES if anim == "bite" else SWEEP_BONES
			under, drop = pose()
			env["POSE"] = ",".join(["%s:=%s" % (b, ":".join("%.3f" % math.degrees(x) for x in v)) for b, v in under.items() if b not in keyed]
								   + ["body@=0:%.3f:0" % drop])
		subprocess.run([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_preview.py"), scratch, NAME, new, anim, str(sec), HIDE],
					   check=True, env=env, stdout=subprocess.DEVNULL)
		if anim in STATES:
			ticks, _, state = STATES[anim]
			angles, drop = pose(**state(sec * 20.0))
		elif anim == "land":
			angles, drop = pose(t=sec * 20, land=1 - sec * 20 / LAND_TICKS)
		else:
			angles, drop = pose()   # the moves are new: the old model stood still
		spec = ["%s:=%s" % (b, ":".join("%.3f" % math.degrees(x) for x in v)) for b, v in angles.items()]
		spec.append("body@=0:%.3f:0" % drop)
		env["POSE"] = ",".join(spec)
		subprocess.run([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_preview.py"), scratch, NAME, old, "-", "0", HIDE],
					   check=True, env=env, stdout=subprocess.DEVNULL)
		rows.append(("%s @ %.2fs" % (anim, sec), Image.open(old), Image.open(new)))
	w, h = rows[0][1].size
	sheet = Image.new("RGB", (w, len(rows) * h * 2 + len(rows) * 18), (40, 40, 40))
	d = ImageDraw.Draw(sheet)
	y = 0
	for label, old, new in rows:
		d.text((6, y + 3), label + "   top: old ZephyrusDragonModel pose   bottom: new GeckoLib animation", fill=(255, 255, 255))
		y += 18
		sheet.paste(old, (0, y))
		sheet.paste(new, (0, y + h))
		y += 2 * h
	path = os.path.join(out_dir, "zephyrus-comparisons.png")
	sheet.save(path)
	print("wrote", path)


def main():
	model = make_model()
	uuids = {}

	def collect(node):
		if isinstance(node, dict):
			uuids[node["name"]] = node["uuid"]
			for c in node["children"]:
				collect(c)
	for n in model["outliner"]:
		collect(n)
	model["animations"] = [a.blockbench(uuids) for a in build()]
	with open(MODEL, "w", encoding="utf-8", newline="\n") as f:
		json.dump(model, f, indent="\t")
		f.write("\n")
	print("wrote", os.path.relpath(MODEL, ROOT))
	subprocess.run([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_geckolib.py"), MODEL, ASSETS, NAME], check=True)
	# the renderer draws the hides and markings in textures/entity/dragon/zephyrus/ by its DragonLook
	sheet = os.path.join(ASSETS, "textures", "entity", NAME + ".png")
	if os.path.exists(sheet):
		os.remove(sheet)
	if "--compare" in sys.argv:
		compare(sys.argv[sys.argv.index("--compare") + 1])


if __name__ == "__main__":
	main()
