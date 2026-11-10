"""The redstone golem's GeckoLib model: polyjo's Redstone Golem (Minecraft Dungeons) (Blockbench Workshop, CC BY 4.0)
with animations of ours.

The Workshop model came with an idle and a walk. This adds the rest as keyframes, so the golem is animated by
GeckoLib instead of posed in code (BeastsClient.golemArms did it before):
  pound     both fists swung up overhead, then brought down on the ground (lands at tick 18 of 30, as RedstoneGolem)
  eruption  the same, slower, the fists held on the ground while the rock below its foes burns (lands at tick 28 of 40)
  hit       a flinch: the torso and head knocked back (torso and up only, so the legs keep walking)
  death     its knees give, then it topples forward onto its face (held; vanilla removes it after 20 ticks)
The old code's pose was the model's rest pose with both arms turned by ARMS_UP (-160) and ARMS_DOWN (-35); these keep
those arm angles at the same ticks and add the torso, forearms, head and hips around them.

Writes kingdoms/art/redstone_golem.bbmodel (the editable project, its credit beside it), then the GeckoLib files with
tools/bbmodel_geckolib.py, and the glow sheet (the model's emissive texture, same layout) as
textures/entity/redstone_golem_glow.png.

Keyframes here are the game's way (degrees; rotations as ModelPart angles, positions x and y turned round), as the
project's "free" format stores them, and as tools/bbmodel_preview.py draws them.
Run from the project root: python3 -I kingdoms/tools/gen_redstone_golem_geo.py
"""
import base64
import json
import os
import subprocess
import sys
import uuid

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SOURCE = os.path.join(ROOT, "tools", "bbmodels", "redstone-golem-minecraft-dungeons.bbmodel")
ART = os.path.join(ROOT, "kingdoms", "art", "redstone_golem.bbmodel")
ASSETS = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms")
NAME = "redstone_golem"
P = "redstone_golem_"

# the server's timing (RedstoneGolem): length and landing tick of each move
TICK = 0.05
POUND_TICKS, POUND_HIT = 30, 18
ERUPT_TICKS, ERUPT_HIT = 40, 28
# the old code's arm angles (BeastsClient): raised overhead, and brought down
ARMS_UP, ARMS_DOWN = -160.0, -35.0
HIT_LEN, DEATH_LEN = 0.4, 1.0
# toppling about its feet: how far it is raised (pixels) at each angle (degrees) so its toes, then its chest and arms,
# stay on the ground (measured with tools/bbmodel_preview.py's extents)
DEATH_LIFT = ((0, 0.0), (30, 3.6), (50, 5.0), (67, 10.5), (88, 26.0))


def lift(tip):
	for (a0, l0), (a1, l1) in zip(DEATH_LIFT, DEATH_LIFT[1:]):
		if tip <= a1:
			return l0 + (l1 - l0) * (tip - a0) / (a1 - a0)
	return DEATH_LIFT[-1][1]

ARMS = (P + "arm_left", P + "arm_right")
FOREARMS = (P + "forearm_left", P + "forearm_right")
FINGERS = tuple(P + "finger_%s_%d" % (s, i) for s in ("left", "right") for i in (1, 2, 3))


class Pose:
	"""Rotations and positions added to the resting pose, bone by bone (the game's way)."""

	def __init__(self):
		self.rot, self.pos = {}, {}

	def r(self, bone, v):
		self.rot[bone] = tuple(a + b for a, b in zip(self.rot.get(bone, (0, 0, 0)), v))
		return self

	def p(self, bone, v):
		self.pos[bone] = tuple(a + b for a, b in zip(self.pos.get(bone, (0, 0, 0)), v))
		return self


class Anim:
	def __init__(self, name, length, loop):
		self.name, self.length, self.loop = name, length, loop
		self.keys = {}

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
		return {"uuid": str(uuid.uuid4()), "name": "animation.redstone_golem." + self.name, "loop": self.loop, "override": False,
			"length": self.length, "snapping": 20, "selected": False, "anim_time_update": "", "blend_weight": "",
			"start_delay": "", "loop_delay": "", "animators": animators}


def slam(arm, lean=0.0, head=0.0, forearm=0.0, fist=0.0, hip=0.0):
	"""Both arms at `arm` (the old code's angle), the torso leaning `lean` (+ back; a ModelPart's -x), the head tipped against it, the
	forearms bent, the fingers curled into fists, the hips dropped `hip` pixels."""
	p = Pose()
	for a in ARMS:
		p.r(a, (arm, 0, 0))
	for f in FOREARMS:
		p.r(f, (forearm, 0, 0))
	for i, f in enumerate(FINGERS):
		p.r(f, (0, 0, fist * (1 if i % 3 == 2 else -1) * (1 if "left" in f else -1)))
	p.r(P + "torso", (-lean, 0, 0)).r(P + "head", (-head, 0, 0))
	p.p(P + "hip", (0, -hip, 0))
	for leg in (P + "leg_left", P + "leg_right"):
		p.r(leg, (0, 0, 0))
	return p


def smash(name, ticks, hit, held):
	"""The old pose's timeline: arms up over 8 ticks, held until 4 ticks before the hit, down by the hit, held, back.
	`held` adds a tremble while the fists are on the ground (the eruption)."""
	s = lambda tick: tick * TICK
	a = Anim(name, ticks * TICK, "once")
	rest = slam(0)
	a.key(0, rest)
	a.key(s(8), slam(ARMS_UP, lean=12, head=-8, forearm=25, fist=20, hip=0))
	a.key(s(hit - 4), slam(ARMS_UP - 6, lean=16, head=-10, forearm=30, fist=25))
	a.key(s(hit - 1), slam(-80, lean=-6, head=4, forearm=5, fist=25, hip=1), "linear")
	a.key(s(hit), slam(ARMS_DOWN, lean=-22, head=12, forearm=0, fist=25, hip=4), "linear")
	if held:
		end = ticks - 6
		t = hit + 2
		k = 0
		while t < end:
			a.key(s(t), slam(ARMS_DOWN + (2 if k % 2 else -2), lean=-20 + (1.5 if k % 2 else -1.5), head=10, fist=25, hip=3.5))
			t += 2
			k += 1
		a.key(s(end), slam(ARMS_DOWN, lean=-20, head=10, fist=25, hip=3.5))
	else:
		a.key(s(hit + 2), slam(ARMS_DOWN + 4, lean=-19, head=10, fist=25, hip=3))
		a.key(s(hit + 6), slam(ARMS_DOWN, lean=-18, head=10, fist=20, hip=3))
	a.key(s(ticks), rest)
	return a


def build():
	anims = [smash("pound", POUND_TICKS, POUND_HIT, False), smash("eruption", ERUPT_TICKS, ERUPT_HIT, True)]

	# hit: knocked back a little and recovering (torso up only: the legs go on walking)
	hit = Anim("hit", HIT_LEN, "once")
	flinch = lambda k: Pose().r(P + "torso", (-14 * k, 0, 3 * k)).r(P + "head", (-10 * k, 0, -4 * k)) \
		.r(ARMS[0], (-8 * k, 0, -10 * k)).r(ARMS[1], (-8 * k, 0, 10 * k))
	hit.key(0, flinch(0)).key(0.08, flinch(1)).key(0.18, flinch(0.8)).key(HIT_LEN, flinch(0))
	anims.append(hit)

	# death: knees give and arms hang, then it topples forward about its feet onto its face, with a small bounce
	death = Anim("death", DEATH_LEN, "hold")
	def fall(tip, sag, slump):
		p = Pose().r("redstone_golem", (tip, 0, 0)).p(P + "hip", (0, -sag, 0)).p("redstone_golem", (0, lift(tip), 0))
		p.r(P + "torso", (slump * 20, 0, 0)).r(P + "head", (-slump * 25, 0, 0))
		for a, side in zip(ARMS, (1, -1)):
			p.r(a, (slump * 10, 0, side * slump * 12))
		for f in FOREARMS:
			p.r(f, (-slump * 10, 0, 0))
		return p
	# tip: forward about its feet (degrees, a ModelPart's +x); sag: hips dropped (pixels); slump: torso, arms and head
	death.key(0, fall(0, 0, 0)).key(0.3, fall(4, 4, 1)).key(0.55, fall(30, 4, 1), "linear")
	for i in range(1, 6):  # falling faster as it goes (keyed often, so the lift keeps its chest on the ground)
		k = i / 5
		death.key(0.55 + 0.25 * k, fall((30 + 58 * k * k), 4 - k, 1 - 0.2 * k), "linear")
	death.key(0.9, fall(82, 3, 0.8)).key(DEATH_LEN, fall(88, 3, 0.8))
	anims.append(death)
	return anims


def main():
	with open(SOURCE, encoding="utf-8") as f:
		data = json.load(f)
	uuids = {g["name"]: g["uuid"] for g in data["groups"]}
	for a in data["animations"]:
		a["name"] = {"redstone_golem_idle_animation_1": "animation.redstone_golem.idle",
			"redstone_golem_walk_animation_1": "animation.redstone_golem.walk"}.get(a["name"], a["name"])
	data["animations"] += [a.blockbench(uuids) for a in build()]
	data["name"] = NAME
	os.makedirs(os.path.dirname(ART), exist_ok=True)
	with open(ART, "w", encoding="utf-8", newline="\n") as f:
		json.dump(data, f, indent="\t")
	print("wrote", os.path.relpath(ART, ROOT))
	subprocess.run([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_geckolib.py"), ART, ASSETS, NAME], check=True)
	glow = next(t for t in data["textures"] if "emissive" in t["name"])
	out = os.path.join(ASSETS, "textures", "entity", NAME + "_glow.png")
	with open(out, "wb") as f:
		f.write(base64.b64decode(glow["source"].split(",", 1)[1]))
	print("wrote", os.path.relpath(out, ROOT))


if __name__ == "__main__":
	main()
