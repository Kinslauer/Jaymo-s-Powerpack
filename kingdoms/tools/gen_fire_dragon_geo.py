"""Notus's and Boreas's GeckoLib model: nyxaris' Fire Dragon (Blockbench Workshop, CC BY 4.0) with animations of ours.

The Workshop model came with one animation, its wingbeat (`fly`). This adds the rest as keyframes, so the dragon is
animated by GeckoLib instead of posed in code: `idle` (on the ground, wings folded, breathing, the tail swaying),
`walk`, `sit`, `crash` (lying low, wings spread), `land` (wings flared, then folding), `glide` (the wingbeat held level),
`bite` and `sweep` (the tail swung round). The ground poses are the ones the old FireDragonModel worked out in code.

Writes kingdoms/art/fire_dragon.bbmodel (the editable project, with its credit beside it), then the GeckoLib files
with tools/bbmodel_geckolib.py. The hides and markings stay in textures/entity/dragon/<notus|boreas>/ (same UV layout),
so the stacked texture it writes is deleted; its texture as drawn stays at textures/entity/bb/fire_dragon.png, the
source kingdoms/tools/gen_fire_dragons.py paints the hides from.

Keyframes here are the game's way (degrees; rotations as ModelPart angles, positions x and y turned round), as the
project's "free" format stores them, and as tools/bbmodel_preview.py draws them.
Run from the project root: python3 -I kingdoms/tools/gen_fire_dragon_geo.py
"""
import json
import math
import os
import subprocess
import sys
import uuid

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SOURCE = os.path.join(ROOT, "tools", "bbmodels", "fire-dragon-minecraft.bbmodel")
ART = os.path.join(ROOT, "kingdoms", "art", "fire_dragon.bbmodel")
ASSETS = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms")
NAME = "fire_dragon"
# the small floating piece over its head, left out as the old bake left it out
DROP = "cosoflotante"

# ---- the ground pose (what FireDragonModel did in code) ---------------------------------------------------------------
# absolute angles on the ground (degrees, the game's way)
GROUND_BODY, GROUND_NECK, GROUND_HEAD = 17.0, -22.0, 15.0
# folded wings: added to the arm's angles; the outer wing folded back on it
FOLD_PITCH, FOLD_SWEEP, FOLD_DROOP, FOLD_TIP = 27.5, 70.0, -30.0, 100.0
FORELEG = -20.0
# raised onto its feet and pushed forward over its middle (pixels); how low it lies sitting and crashed
LIFT, FORWARD, SIT_DROP, CRASH_DROP = 19.0, 10.0, 7.0, 12.0
# the walk: how far each leg swings (degrees)
STRIDE_SWING = 28.0
# the wingbeat's moment with the wings held level (seconds), for gliding
GLIDE_AT = 0.3

HIND = ["leftPata", "pata1", "pata2", "pata3", "dedos", "rightPata", "pata4", "pata5", "pata6", "dedos4"]
FORE = ["mano_r", "mano1", "mano2", "dedos3", "dedoese", "mano_r2", "mano3", "mano4", "dedos2", "dedoese2"]
TAIL = ["cola0", "cola1", "cola2", "cola3", "cola4"]

# animation lengths (seconds)
IDLE_LEN, WALK_LEN, LAND_LEN, BITE_LEN, SWEEP_LEN = 4.5, 1.2, 0.85, 0.6, 1.0


def load():
	with open(SOURCE, encoding="utf-8") as f:
		return json.load(f)


def rests(data):
	"""Each bone's resting angles, the game's way (Blockbench's x and y turned round)."""
	out = {}
	for g in data.get("groups", []):
		rx, ry, rz = (g.get("rotation") or [0, 0, 0])
		out[g["name"]] = (-rx, -ry, rz)
	return out


def add(a, b):
	return tuple(x + y for x, y in zip(a, b))


def scale(a, s):
	return tuple(x * s for x in a)


class Pose:
	"""Rotations and positions added to the resting pose, bone by bone (the game's way)."""

	def __init__(self):
		self.rot, self.pos = {}, {}

	def r(self, bone, v):
		self.rot[bone] = add(self.rot.get(bone, (0, 0, 0)), v)
		return self

	def p(self, bone, v):
		self.pos[bone] = add(self.pos.get(bone, (0, 0, 0)), v)
		return self

	def copy(self):
		c = Pose()
		c.rot, c.pos = dict(self.rot), dict(self.pos)
		return c

	def only(self, bones):
		"""Just these bones: a move played over another leaves the rest (the legs walking, the wings) to that one."""
		c = Pose()
		c.rot = {b: v for b, v in self.rot.items() if b in bones}
		c.pos = {b: v for b, v in self.pos.items() if b in bones}
		return c


def ground(rest, sit=0.0, crash=0.0, fold=1.0):
	"""On the ground: levelled, neck up, legs under it, wings folded back over its spine (`fold` 0: flared)."""
	p = Pose()
	def absolute(bone, x, y=0.0, z=0.0):
		r = rest[bone]
		p.r(bone, (x - r[0], y - r[1], z - r[2]))
	absolute("body", GROUND_BODY - sit * 18.0)
	absolute("cuello1", GROUND_NECK + crash * 30.0)
	absolute("HEAD", GROUND_HEAD)
	for leg in HIND + FORE:
		absolute(leg, 0.0)
	p.r("leftPata", (sit * math.degrees(0.9), 0, 0)).r("rightPata", (sit * math.degrees(0.9), 0, 0))
	p.r("mano_r", (FORELEG, 0, 0)).r("mano_r2", (FORELEG, 0, 0))
	f = fold * (1.0 - crash * 0.6)
	p.r("leftAla", (FOLD_PITCH * f, FOLD_SWEEP * f, FOLD_DROOP * f)).r("righAla", (FOLD_PITCH * f, -FOLD_SWEEP * f, -FOLD_DROOP * f))
	p.r("ala1", (0, FOLD_TIP * f, 0)).r("ala3", (0, -FOLD_TIP * f, 0))
	spread = math.degrees(0.5) * crash
	p.r("leftAla", (0, 0, -spread)).r("righAla", (0, 0, spread))
	p.p("root", (0, LIFT - sit * SIT_DROP - crash * CRASH_DROP, -FORWARD))
	return p


class Anim:
	def __init__(self, name, length, loop):
		self.name, self.length, self.loop = name, length, loop
		self.keys = {}  # bone -> channel -> [(time, (x, y, z), interpolation)]

	def key(self, time, pose, interp="catmullrom"):
		for bone, v in pose.rot.items():
			self.keys.setdefault(bone, {}).setdefault("rotation", []).append((time, v, interp))
		for bone, v in pose.pos.items():
			self.keys.setdefault(bone, {}).setdefault("position", []).append((time, v, interp))
		return self

	def blockbench(self, uuids):
		animators = {}
		for bone, chans in self.keys.items():
			kfs = []
			for ch, ks in chans.items():
				for t, v, interp in ks:
					kfs.append({"channel": ch, "data_points": [{"x": round(v[0], 4), "y": round(v[1], 4), "z": round(v[2], 4)}],
						"uuid": str(uuid.uuid4()), "time": round(t, 4), "color": -1, "interpolation": interp})
			animators[uuids[bone]] = {"name": bone, "type": "bone", "keyframes": kfs}
		return {"uuid": str(uuid.uuid4()), "name": "animation.dragon." + self.name, "loop": self.loop, "override": False,
			"length": self.length, "snapping": 24, "selected": False, "anim_time_update": "", "blend_weight": "",
			"start_delay": "", "loop_delay": "", "animators": animators}


def sample(anim, bone, channel, time):
	"""The source wingbeat's value for a bone at `time` (its keyframes are linear)."""
	for an in anim["animators"].values():
		if an.get("name") != bone:
			continue
		ks = sorted((k for k in an["keyframes"] if k["channel"] == channel), key=lambda k: k["time"])
		if not ks:
			return None
		vals = [(k["time"], tuple(float(k["data_points"][0].get(a, 0) or 0) for a in "xyz")) for k in ks]
		if time <= vals[0][0]:
			return vals[0][1]
		for (t0, v0), (t1, v1) in zip(vals, vals[1:]):
			if t0 <= time <= t1:
				s = (time - t0) / (t1 - t0) if t1 > t0 else 0.0
				return tuple(a + (b - a) * s for a, b in zip(v0, v1))
		return vals[-1][1]
	return None


def build(data):
	rest = rests(data)
	fly = next(a for a in data["animations"] if a["name"].endswith(".fly"))
	anims = []

	# idle: standing, the folded wings breathing, the tail swaying (each segment a little behind the last)
	idle = Anim("idle", IDLE_LEN, "loop")
	steps = 8
	for i in range(steps + 1):
		t = IDLE_LEN * i / steps
		w = math.sin(2 * math.pi * i / steps)
		p = ground(rest).r("leftAla", (0, 0, w * 1.2)).r("righAla", (0, 0, -w * 1.2))
		for j, seg in enumerate(TAIL):
			p.r(seg, (0, math.degrees(math.sin(2 * math.pi * i / steps - j * 0.6) * 0.05), 0))
		p.r("cuello1", (w * 1.5, 0, 0)).p("body", (0, w * 0.3, 0))
		idle.key(t, p)
	anims.append(idle)

	# walk: the legs swing in opposite pairs (two strides a loop), the body rolls once, the tail sways more
	walk = Anim("walk", WALK_LEN, "loop")
	for i in range(steps + 1):
		t = WALK_LEN * i / steps
		step = 2 * math.pi * 2 * i / steps
		swing = math.sin(step) * STRIDE_SWING
		p = ground(rest).r("leftPata", (swing, 0, 0)).r("rightPata", (-swing, 0, 0))
		p.r("mano_r", (-swing, 0, 0)).r("mano_r2", (swing, 0, 0))
		p.r("body", (0, 0, math.degrees(math.sin(step * 0.5) * 0.03)))
		p.p("root", (0, abs(math.cos(step)) * 0.6, 0))
		for j, seg in enumerate(TAIL):
			p.r(seg, (0, math.degrees(math.sin(step * 0.5 - j * 0.6) * 0.13), 0))
		walk.key(t, p)
	anims.append(walk)

	# sit: settled back on its haunches; crash: lying low, neck down, wings spread on the ground
	anims.append(Anim("sit", 0.05, "loop").key(0, ground(rest, sit=1.0), "linear"))
	anims.append(Anim("crash", 0.05, "loop").key(0, ground(rest, crash=1.0), "linear"))

	# land: wings flared as it touches down, then folding over its back
	land = Anim("land", LAND_LEN, "once")
	land.key(0.0, ground(rest, fold=0.0)).key(0.25, ground(rest, fold=0.05).r("leftAla", (0, 0, -8)).r("righAla", (0, 0, 8)))
	land.key(LAND_LEN, ground(rest))
	anims.append(land)

	# glide: the wingbeat held at its level moment, the tail streaming out straighter
	glide = Anim("glide", 0.05, "loop")
	g = Pose()
	for an in fly["animators"].values():
		bone = an["name"]
		for ch in ("rotation", "position"):
			v = sample(fly, bone, ch, GLIDE_AT)
			if v is None:
				continue
			if ch == "rotation" and bone in TAIL:
				v = add(scale(v, 0.5), scale(rest[bone], -0.5))
			(g.r if ch == "rotation" else g.p)(bone, v)
	glide.key(0, g, "linear")
	anims.append(glide)

	# bite: a lunge of the neck, the jaw wide, then snapped shut (only the neck, head, jaw and the body's lunge: the
	# legs and wings go on with whatever it was doing)
	jaws = ("cuello1", "HEAD", "mandibula", "root")
	base = ground(rest).r("mandibula", (0, 0, 0)).only(jaws)
	bite = Anim("bite", BITE_LEN, "once")
	bite.key(0.0, base)
	bite.key(0.15, base.copy().r("cuello1", (-12, 0, 0)).r("HEAD", (-10, 0, 0)).r("mandibula", (35, 0, 0)).p("root", (0, 0, 2)))
	bite.key(0.3, base.copy().r("cuello1", (22, 0, 0)).r("HEAD", (8, 0, 0)).p("root", (0, 0, -4)))
	bite.key(BITE_LEN, base)
	anims.append(bite)

	# sweep: winds the other way, then swings the body and tail right round
	sweep = Anim("sweep", SWEEP_LEN, "once")
	swing = ground(rest).r("body", (0, 0, 0)).only(("body",) + tuple(TAIL))
	def turned(body, lag):
		p = swing.copy().r("body", (0, body, 0))
		for j, seg in enumerate(TAIL):
			p.r(seg, (0, lag * (j + 1) / len(TAIL), 0))
		return p
	sweep.key(0.0, turned(0, 0)).key(0.25, turned(-18, 25)).key(0.55, turned(35, -45)).key(0.75, turned(20, -20)).key(SWEEP_LEN, turned(0, 0))
	anims.append(sweep)
	return anims


def main():
	data = load()
	uuids = {g["name"]: g["uuid"] for g in data["groups"]}
	data["animations"] = [a for a in data["animations"] if a["name"].endswith(".fly")]
	data["animations"] += [a.blockbench(uuids) for a in build(data)]
	data["name"] = NAME
	os.makedirs(os.path.dirname(ART), exist_ok=True)
	with open(ART, "w", encoding="utf-8", newline="\n") as f:
		json.dump(data, f, indent="\t")
	print("wrote", os.path.relpath(ART, ROOT))
	subprocess.run([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_geckolib.py"), ART, ASSETS, NAME, "--drop", DROP], check=True)
	sheet = os.path.join(ASSETS, "textures", "entity", NAME + ".png")
	if os.path.exists(sheet):
		os.remove(sheet)  # the hides are drawn from textures/entity/dragon/<species>/


if __name__ == "__main__":
	main()
