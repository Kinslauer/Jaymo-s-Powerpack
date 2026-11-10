"""The Omen's GeckoLib models: "The Omen Red" and "The Omen Blue" (Blockbench Workshop, CC BY 4.0) with animations of ours.

The Workshop models came with one pose each (`pose_1`: buckled, arms thrown). This keeps it and adds the rest as
keyframes, so the Omen is animated by GeckoLib instead of posed in code (the old client/power/chrono/OmenRenderer):
  idle        standing, the torso breathing, the collar cloths and loincloth stirring
  walk        the old code's stride (thighs, knees, counter-swinging arms), the cloths swinging with it
  run         a longer stride, leaning in, arms pumping (chasing a target)
  turn_left, turn_right   shuffling round on the spot
  smash       both arms up and down together (the old 12-tick slam, with a wind-up and a lean into the blow)
  fire        the cannon arms' recoil, below the shoulder (the renderer aims the shoulders every frame)
  hurt        a flinch
  death_red, death_blue   eases into that build's own pose_1 (they differ) and holds it, as the old renderer did
  pose_1      the Workshop pose, unchanged

Red and blue have the same bones, so they share one animation file (omen.animation.json); each keeps its own geometry
(omen_red.geo.json, omen_blue.geo.json) and is drawn with its existing skin, textures/entity/bb/omen_<build>.png
(so the excalibur_style resource pack still reskins it). The stacked textures the converter writes are deleted.

Keyframes are the game's way (degrees, rotations as ModelPart angles; positions with +y up), as the project's "free"
format stores them. Every bone rests at 0, so a key is the old code's ModelPart angle in degrees.
Run from the project root: python3 -I tools/gen_omen_geo.py
"""
import json
import math
import os
import subprocess
import sys
import uuid

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCES = {b: os.path.join(ROOT, "tools", "bbmodels", "the-omen-" + b + ".bbmodel") for b in ("red", "blue")}
ART = os.path.join(ROOT, "art")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "jaymospowers")
ANIM_NAME = "omen"

# ---- the old OmenRenderer's numbers (radians there, degrees here) ----------------------------------------------------
STRIDE = math.degrees(0.75)      # thigh swing at full stride
KNEE = 0.9                       # the knee bends this much of the forward swing
ARM_SWAY = 0.5                   # the arms counter-swing this much of the stride
SLAM = math.degrees(-2.5)        # the smash's arms at the top of the swing
SMASH_TICKS = 12

WALK_LEN = 1.05                  # one stride pair; the old cos(walkPos * 0.5) at a walk speed of 0.6 (FutureOmen.WALK_AT)
RUN_LEN = 0.8
IDLE_LEN = 4.0
TURN_LEN = 0.9

LEFT_LEG = ("left_thigh_joint", "left_calf_joint", "left_foot_joint")
RIGHT_LEG = ("right_thigh_joint", "right_calf_joint", "right_foot_joint")
CLOTHS = ("collar_guard_left_cloth", "collar_guard_left_cloth2", "collar_guard_right_cloth", "collar_guard_right_cloth2", "loincloth")


class Pose:
	"""Rotations and positions (the game's way) bone by bone; every bone of the Omen rests at 0."""

	def __init__(self):
		self.rot, self.pos = {}, {}

	def r(self, bone, v):
		old = self.rot.get(bone, (0.0, 0.0, 0.0))
		self.rot[bone] = tuple(a + b for a, b in zip(old, v))
		return self

	def p(self, bone, v):
		old = self.pos.get(bone, (0.0, 0.0, 0.0))
		self.pos[bone] = tuple(a + b for a, b in zip(old, v))
		return self

	def copy(self):
		c = Pose()
		c.rot, c.pos = dict(self.rot), dict(self.pos)
		return c

	def with_bones(self, rot_bones=(), pos_bones=()):
		"""The same pose with these bones keyed (at 0 if unset), so every key of an animation has every bone."""
		c = self.copy()
		for b in rot_bones:
			c.rot.setdefault(b, (0.0, 0.0, 0.0))
		for b in pos_bones:
			c.pos.setdefault(b, (0.0, 0.0, 0.0))
		return c


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
						"uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, "jaymospowers:omen:%s:%s:%s:%s" % (self.name, bone, ch, t))),
						"time": round(t, 4), "color": -1, "interpolation": interp})
			animators[uuids[bone]] = {"name": bone, "type": "bone", "keyframes": kfs}
		return {"uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, "jaymospowers:omen:animation:" + self.name)),
			"name": "animation.omen." + self.name, "loop": self.loop, "override": False, "length": self.length,
			"snapping": 20, "selected": False, "anim_time_update": "", "blend_weight": "", "start_delay": "",
			"loop_delay": "", "animators": animators}


def num(v):
	try:
		return float(str(v).strip() or 0)
	except ValueError:
		return 0.0


def pose_1(data):
	"""The Workshop pose as a Pose (its stray newlines and blanks read as 0)."""
	a = next(a for a in data["animations"] if a["name"].endswith("pose_1"))
	p = Pose()
	for an in a["animators"].values():
		for k in an.get("keyframes", []):
			v = tuple(num(k["data_points"][0].get(ax, 0)) for ax in "xyz")
			(p.r if k["channel"] == "rotation" else p.p)(an["name"], v)
	return p


def stride_pose(phase, stride, knee, arm, lean=0.0, bob=0.0, cloth=0.0):
	"""One moment of a stride (`phase` in radians), the old setupAnim's way: negative thigh angles swing forward."""
	s = math.cos(phase) * stride
	p = Pose()
	p.r("left_thigh_joint", (-s, 0, 0)).r("right_thigh_joint", (s, 0, 0))
	p.r("left_calf_joint", (max(0.0, s) * knee, 0, 0)).r("right_calf_joint", (max(0.0, -s) * knee, 0, 0))
	# the foot stays level-ish under a bent knee
	p.r("left_foot_joint", (-max(0.0, s) * knee * 0.35, 0, 0)).r("right_foot_joint", (-max(0.0, -s) * knee * 0.35, 0, 0))
	p.r("left_arm_joint", (s * arm, 0, 0)).r("right_arm_joint", (-s * arm, 0, 0))
	# the hips twist a little with the stride, the torso against them; the weight comes down twice a stride
	p.r("hips", (lean * 0.4, -math.cos(phase) * 4.0, 0)).r("torso", (lean * 0.6, math.cos(phase) * 6.0, 0))
	p.p("the_omen", (0, -abs(math.sin(phase)) * bob, 0))
	p.r("head", (-lean * 0.8, -math.cos(phase) * 3.0, 0))
	# the cloths trail and flap with each step
	flap = math.sin(phase * 2.0) * cloth
	p.r("collar_guard_left_cloth", (-flap, 0, 0)).r("collar_guard_right_cloth", (flap, 0, 0))
	p.r("collar_guard_left_cloth2", (-flap * 0.6, 0, 0)).r("collar_guard_right_cloth2", (flap * 0.6, 0, 0))
	p.r("loincloth", (abs(math.cos(phase)) * cloth * 0.8 + lean * 0.5, 0, 0))
	return p


def build(finals):
	anims = []
	steps = 16

	idle = Anim("idle", IDLE_LEN, "loop")
	for i in range(9):
		t = IDLE_LEN * i / 8
		w = math.sin(2 * math.pi * i / 8)
		c = math.sin(2 * math.pi * i / 8 - 0.9)
		p = Pose().r("torso", (w * 1.5, 0, 0)).p("torso", (0, w * 0.3, 0)).r("head", (-w * 1.2, 0, 0))
		p.r("left_arm_joint", (w * 1.5, 0, -w * 1.0)).r("right_arm_joint", (w * 1.5, 0, w * 1.0))
		p.r("left_arm_joint2", (-w * 2.0, 0, 0)).r("right_arm_joint2", (-w * 2.0, 0, 0))
		p.r("collar_guard_left_cloth", (c * 2.5, 0, 0)).r("collar_guard_right_cloth", (c * 2.5, 0, 0))
		p.r("collar_guard_left_cloth2", (c * 1.5, 0, 0)).r("collar_guard_right_cloth2", (c * 1.5, 0, 0))
		p.r("loincloth", (c * 2.0, 0, 0)).p("the_omen", (0, -abs(w) * 0.15, 0))
		idle.key(t, p)
	anims.append(idle)

	walk = Anim("walk", WALK_LEN, "loop")
	for i in range(steps + 1):
		walk.key(WALK_LEN * i / steps, stride_pose(2 * math.pi * i / steps, STRIDE, KNEE, ARM_SWAY, bob=0.8, cloth=6.0))
	anims.append(walk)

	run = Anim("run", RUN_LEN, "loop")
	for i in range(steps + 1):
		phase = 2 * math.pi * i / steps
		p = stride_pose(phase, STRIDE * 1.3, 1.1, 0.0, lean=12.0, bob=1.6, cloth=12.0)
		# fists pumping, elbows bent
		pump = math.cos(phase) * 35.0
		p.r("left_arm_joint", (pump - 10, 0, 0)).r("right_arm_joint", (-pump - 10, 0, 0))
		p.r("left_arm_joint2", (-35, 0, 0)).r("right_arm_joint2", (-35, 0, 0))
		run.key(RUN_LEN * i / steps, p)
	anims.append(run)

	# shuffling round: one foot lifted and set down turned, then the other; the torso leads the turn
	for name, sign in (("turn_left", -1.0), ("turn_right", 1.0)):
		turn = Anim(name, TURN_LEN, "loop")
		lead, follow = (LEFT_LEG, RIGHT_LEG) if sign < 0 else (RIGHT_LEG, LEFT_LEG)
		hip_lead = "left_hip_joint" if sign < 0 else "right_hip_joint"
		hip_follow = "right_hip_joint" if sign < 0 else "left_hip_joint"
		for i in range(steps + 1):
			ph = 2 * math.pi * i / steps
			up_a = max(0.0, math.sin(ph))       # the leading foot's lift (first half)
			up_b = max(0.0, -math.sin(ph))      # the other's (second half)
			p = Pose()
			for leg, hip, up in ((lead, hip_lead, up_a), (follow, hip_follow, up_b)):
				p.r(leg[0], (-up * 22.0, 0, 0)).r(leg[1], (up * 32.0, 0, 0)).r(leg[2], (-up * 10.0, 0, 0))
				# a lifted leg swings its hip round towards the turn (the game's y is the other way to a yaw)
				p.r(hip, (0, -sign * up * 14.0, 0))
			p.r("torso", (0, -sign * 6.0, 0)).r("head", (0, -sign * 6.0, 0))
			p.r("left_arm_joint", (0, 0, -sign * 4.0 * (up_a + up_b))).r("right_arm_joint", (0, 0, -sign * 4.0 * (up_a + up_b)))
			p.p("the_omen", (0, -(up_a + up_b) * 0.5, 0))
			turn.key(TURN_LEN * i / steps, p)
		anims.append(turn)

	# the smash: the old slam's arc (-2.5 * sin(pi p) over 12 ticks), sharpened into a wind-up and a fast blow, the
	# torso rearing back and coming down into it, the knees giving at the impact
	smash = Anim("smash", SMASH_TICKS / 20.0, "once")
	keys = ((0.0, 0.0, 0.0, 0.0, 0.0), (0.2, SLAM * 0.95, -10.0, -30.0, 0.0), (0.3, SLAM, -12.0, -40.0, 0.0),
			(0.42, -25.0, 16.0, -5.0, 1.0), (0.5, -15.0, 12.0, 0.0, 0.8), (0.6, 0.0, 0.0, 0.0, 0.0))
	for t, arm, torso, elbow, crouch in keys:
		p = Pose().r("left_arm_joint", (arm, 0, 0)).r("right_arm_joint", (arm, 0, 0))
		p.r("left_arm_joint2", (elbow, 0, 0)).r("right_arm_joint2", (elbow, 0, 0))
		p.r("torso", (torso, 0, 0)).r("head", (-torso * 0.6, 0, 0))
		p.r("left_thigh_joint", (-12 * crouch, 0, 0)).r("right_thigh_joint", (-12 * crouch, 0, 0))
		p.r("left_calf_joint", (22 * crouch, 0, 0)).r("right_calf_joint", (22 * crouch, 0, 0))
		p.r("left_foot_joint", (-10 * crouch, 0, 0)).r("right_foot_joint", (-10 * crouch, 0, 0))
		p.p("the_omen", (0, -2.0 * crouch, 0))
		smash.key(t, p)
	anims.append(smash)

	# the cannons' kick: forearms and the hand only, so the renderer's aim on the shoulders and the legs' walk go on
	fire = Anim("fire", 0.2, "once")
	for t, kick in ((0.0, 0.0), (0.04, -9.0), (0.1, 2.0), (0.2, 0.0)):
		fire.key(t, Pose().r("left_arm_joint2", (kick, 0, 0)).r("right_arm_joint2", (kick, 0, 0)).r("right_hand", (kick * 0.5, 0, 0)), "linear")
	anims.append(fire)

	# a flinch: the torso knocked back and a little round, the head after it
	hurt = Anim("hurt", 0.4, "once")
	for t, body, twist in ((0.0, 0.0, 0.0), (0.06, -9.0, 4.0), (0.18, 3.0, -1.5), (0.4, 0.0, 0.0)):
		p = Pose().r("torso", (body, twist, 0)).r("head", (body * 1.3, twist, 0))
		p.r("left_arm_joint", (-body * 0.6, 0, -abs(body) * 0.5)).r("right_arm_joint", (-body * 0.6, 0, abs(body) * 0.5))
		hurt.key(t, p)
	anims.append(hurt)

	# dying: the old renderer eased into the build's pose_1 over its first 8 ticks; here it jolts as the hatch pops,
	# then buckles into the same pose and holds it. Red's and blue's pose_1 differ, so each build has its own.
	for build_name, final in finals.items():
		bones_r, bones_p = tuple(final.rot), tuple(final.pos)
		death = Anim("death_" + build_name, 0.6, "hold")
		death.key(0.0, Pose().with_bones(bones_r, bones_p))
		jolt = Pose().r("torso", (-8, 0, 0)).r("head", (-12, 0, 0)).r("left_arm_joint", (-20, 0, -10)).r("right_arm_joint", (-20, 0, 10))
		death.key(0.12, jolt.with_bones(bones_r, bones_p))
		death.key(0.4, final.with_bones(bones_r, bones_p))
		death.key(0.6, final.with_bones(bones_r, bones_p))
		anims.append(death)
	return anims


def main():
	sources = {}
	for build_name, src in SOURCES.items():
		with open(src, encoding="utf-8") as f:
			sources[build_name] = json.load(f)
	finals = {b: pose_1(d) for b, d in sources.items()}
	for build_name, data in sources.items():
		uuids = {g["name"]: g["uuid"] for g in data["groups"]}
		data["animations"] = [a for a in data["animations"] if a["name"].endswith("pose_1")]
		data["animations"] += [a.blockbench(uuids) for a in build(finals)]
		name = "omen_" + build_name
		data["name"] = name
		art = os.path.join(ART, "the_omen_" + build_name + ".bbmodel")
		with open(art, "w", encoding="utf-8", newline="\n") as f:
			json.dump(data, f, indent="\t")
		print("wrote", os.path.relpath(art, ROOT))
		subprocess.run([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_geckolib.py"), art, ASSETS, name], check=True)
		os.remove(os.path.join(ASSETS, "textures", "entity", name + ".png"))  # drawn with textures/entity/bb/<name>.png
		anim = os.path.join(ASSETS, "geckolib", "animations", "entity", name + ".animation.json")
		if build_name == "red":
			os.replace(anim, os.path.join(ASSETS, "geckolib", "animations", "entity", ANIM_NAME + ".animation.json"))
		else:
			os.remove(anim)  # the same bones: red's animations serve both


if __name__ == "__main__":
	main()
