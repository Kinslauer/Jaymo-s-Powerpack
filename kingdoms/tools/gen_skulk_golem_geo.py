"""The skulk golem's GeckoLib model: elma_taburlas' Skulk golem (Blockbench Workshop, CC BY 4.0) with animations of ours.

The Workshop model came with `walk`, `estatue` (its statue pose) and `attack`, and no idle: our old loader stood it in its
rest pose, its long arms hanging three pixels into the ground. This keeps those three as drawn and adds, as keyframes:
`idle` (arms held a little forward, as in its walk, so they clear the ground; breathing, the antennae twitching),
`statue` (`estatue` held as a loop), `wake` (from the statue pose to fighting: a shudder, then it rears up, arms flung
wide, antennae flared), `settle` (from standing back down into the statue pose), `hurt` (a flinch: body, head and
antennae only, so the legs and arms go on with what they were doing) and `death` (it reels back, then topples forward
and lies face down).

Writes kingdoms/art/skulk_golem.bbmodel (the editable project, with its credit beside it), then the GeckoLib files
with tools/bbmodel_geckolib.py. Keyframes here are the game's way (degrees; rotations as ModelPart angles, positions x
and y turned round), as the project's "modded entity" format stores them. Every bone of this model rests at 0, so a key
is the bone's angle. Negative x pitches a part forward (the statue's bow, the attack's lean).
Run from the project root: python3 -I kingdoms/tools/gen_skulk_golem_geo.py
"""
import json
import math
import os
import subprocess
import sys
import uuid

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SOURCE = os.path.join(ROOT, "tools", "bbmodels", "skulk-golem.bbmodel")
ART = os.path.join(ROOT, "kingdoms", "art", "skulk_golem.bbmodel")
ASSETS = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms")
NAME = "skulk_golem"

# ---- the standing pose ---------------------------------------------------------------------------------------------
# arms held forward and a little out (degrees), as in its walk (about -30), so their fists clear the ground
ARM_FORWARD, ARM_OUT = -30.0, 8.0
# the idle's breath: arm sway, body pitch (degrees) and rise (pixels), head nod, antennae twitch
BREATH_ARM, BREATH_BODY, BREATH_RISE, BREATH_HEAD, TWITCH = 2.5, 1.5, 0.4, 2.0, 7.0

# animation lengths (seconds)
IDLE_LEN, WAKE_LEN, SETTLE_LEN, HURT_LEN, DEATH_LEN = 3.0, 1.4, 1.6, 0.4, 1.0
# the attack's own length is the server's BLOW_TICKS (30 ticks): leave it at 1.5 s


def add(a, b):
	return tuple(x + y for x, y in zip(a, b))


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
		c = Pose()
		c.rot = {b: v for b, v in self.rot.items() if b in bones}
		c.pos = {b: v for b, v in self.pos.items() if b in bones}
		return c

	def full(self, bones):
		"""Every bone in `bones` keyed (at rest if this pose leaves it alone), so a move hides whatever is underneath."""
		c = self.copy()
		for b in bones:
			c.rot.setdefault(b, (0, 0, 0))
			c.pos.setdefault(b, (0, 0, 0))
		return c


UPPER = ("body", "head", "antena", "antena2", "left_arm", "right_arm")
ALL = ("todo", "left_leg", "right_leg") + UPPER


def standing(phase=0.0):
	"""The idle's pose at `phase` (radians round its breath)."""
	w = math.sin(phase)
	p = Pose()
	p.r("left_arm", (ARM_FORWARD + w * BREATH_ARM, 0, -ARM_OUT - w))
	p.r("right_arm", (ARM_FORWARD + w * BREATH_ARM, 0, ARM_OUT + w))
	p.r("body", (-w * BREATH_BODY, 0, 0)).p("body", (0, (w + 1) * 0.5 * BREATH_RISE, 0))
	p.r("head", (math.sin(phase - 0.8) * BREATH_HEAD, 0, 0))
	p.r("antena", (math.sin(phase * 2) * TWITCH, 0, 0)).r("antena2", (math.sin(phase * 2 + 1.3) * TWITCH, 0, 0))
	for b in UPPER:
		p.pos.setdefault(b, (0, 0, 0))
	return p


def statue(source):
	"""The file's `estatue` pose."""
	est = next(a for a in source["animations"] if a["name"].split(".")[-1] == "estatue")
	p = Pose()
	for an in est["animators"].values():
		for k in an.get("keyframes", []):
			v = tuple(float(k["data_points"][0].get(a, 0) or 0) for a in "xyz")
			(p.r if k["channel"] == "rotation" else p.p)(an["name"], v)
	return p.full(UPPER)


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
		return {"uuid": str(uuid.uuid4()), "name": self.name, "loop": self.loop, "override": False,
			"length": self.length, "snapping": 24, "selected": False, "anim_time_update": "", "blend_weight": "",
			"start_delay": "", "loop_delay": "", "animators": animators}


def build(source):
	anims = []
	stone = statue(source)
	stand = standing(0.0)

	idle = Anim("idle", IDLE_LEN, "loop")
	steps = 12
	for i in range(steps + 1):
		idle.key(IDLE_LEN * i / steps, standing(2 * math.pi * i / steps))
	anims.append(idle)

	anims.append(Anim("statue", 0.05, "loop").key(0, stone, "linear"))

	# wake: a shudder in the stone, then it rears up, arms flung wide and antennae flared, and drops into its stance
	wake = Anim("wake", WAKE_LEN, "once")
	wake.key(0.0, stone)
	wake.key(0.12, stone.copy().r("body", (0, 0, 3)).r("head", (0, 4, 0)))
	wake.key(0.24, stone.copy().r("body", (0, 0, -3)).r("head", (0, -4, 0)))
	wake.key(0.36, stone.copy().r("body", (-3, 0, 0)))
	rear = Pose().full(UPPER)
	rear.r("body", (8, 0, 0)).p("body", (0, 1.0, 0)).r("head", (18, 0, 0))
	rear.r("left_arm", (-70, 0, -32)).r("right_arm", (-70, 0, 32)).r("antena", (-25, 0, 0)).r("antena2", (-25, 0, 0))
	wake.key(0.7, rear)
	brace = stand.copy().r("body", (-6, 0, 0)).r("head", (-4, 0, 0)).r("left_arm", (-8, 0, -3)).r("right_arm", (-8, 0, 3))
	wake.key(1.05, brace)
	wake.key(WAKE_LEN, stand)
	anims.append(wake)

	# settle: the arms lower and draw in, it bows and goes still
	settle = Anim("settle", SETTLE_LEN, "once")
	settle.key(0.0, stand)
	sag = stand.copy().r("body", (-10, 0, 0)).r("head", (-22, 0, 0)).r("left_arm", (25, 0, 0)).r("right_arm", (25, 0, 0))
	sag.r("antena", (25, 0, 0)).r("antena2", (25, 0, 0))
	settle.key(0.7, sag)
	settle.key(SETTLE_LEN, stone)
	anims.append(settle)

	# hurt: rocked back (body, head, antennae only)
	hurt = Anim("hurt", HURT_LEN, "once")
	calm = Pose().full(("body", "head", "antena", "antena2"))
	hurt.key(0.0, calm)
	hurt.key(0.1, calm.copy().r("body", (9, 0, -4)).p("body", (0, 0, 0.8)).r("head", (14, 0, 0)).r("antena", (-30, 0, 0)).r("antena2", (-22, 0, 0)))
	hurt.key(HURT_LEN, calm)
	anims.append(hurt)

	# death: reels back, arms thrown up, then topples forward onto its face (the whole golem turning on its feet)
	death = Anim("death", DEATH_LEN, "hold_on_last_frame")
	start = stand.full(ALL)
	death.key(0.0, start)
	reel = start.copy().r("todo", (10, 0, 0)).r("head", (25, 0, 0)).r("left_arm", (-60, 0, -20)).r("right_arm", (-60, 0, 20))
	reel.r("antena", (-30, 0, 0)).r("antena2", (-30, 0, 0))
	death.key(0.3, reel)
	fall = start.copy().r("todo", (-88, 0, 0)).p("todo", (0, 6, 0)).r("head", (20, 0, 0)).r("left_arm", (-130, 0, -12))
	fall.r("right_arm", (-130, 0, 12)).r("left_leg", (8, 0, 0)).r("right_leg", (8, 0, 0)).r("antena", (40, 0, 0)).r("antena2", (40, 0, 0))
	death.key(0.8, fall, "linear")
	thud = fall.copy().r("todo", (3, 0, 0))
	death.key(0.88, thud)
	death.key(DEATH_LEN, fall)
	anims.append(death)
	return anims


def main():
	with open(SOURCE, encoding="utf-8") as f:
		data = json.load(f)
	uuids = {g["name"]: g["uuid"] for g in data["groups"]}
	data["animations"] += [a.blockbench(uuids) for a in build(data)]
	data["name"] = NAME
	os.makedirs(os.path.dirname(ART), exist_ok=True)
	with open(ART, "w", encoding="utf-8", newline="\n") as f:
		json.dump(data, f, indent="\t")
	print("wrote", os.path.relpath(ART, ROOT))
	subprocess.run([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_geckolib.py"), ART, ASSETS, NAME], check=True)


if __name__ == "__main__":
	main()
