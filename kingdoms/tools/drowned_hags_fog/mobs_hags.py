"""Row B3: mire_hag, mud_scoop, and grave_hag — original Reason-look hags and bank-scoop block:
  - mire_hag: stooped fen-crone draped in a peat-moss & reed shawl with hanging reed-locks, bog-gourd hump pack,
    crooked cackling jaw, mud-hemmed skirt, and broad scooping claws with an animated/toggleable mud_clump bone.
  - mud_scoop: claw-furrowed riverbank mud depression left behind when a mire_hag scoops mud (.bbmodel + GeckoLib + Java block).
  - grave_hag: gaunt cemetery mourner-hag in a tattered burial veil with a necklace of trophy skulls across her ribs
    and a 3-bone barbed whipping tongue (tongue_base -> tongue_mid -> tongue_tip) coiled inside her jaw at rest."""
from __future__ import annotations

from .common import (
	IndexedSheet,
	RigBuilder,
	Z,
	closed_loop,
	make_anim,
	mirror_rot,
	paint_barbed_tongue,
	paint_claw_tip,
	paint_hag_face,
	paint_jaw_maw,
	paint_mini_skull,
	paint_mud_caked,
	paint_mud_clump,
	paint_reed_shawl,
	paint_ribbed_torso,
	paint_tattered_cloth,
)

ZERO_SCALE = (0.0, 0.0, 0.0)
UNIT_SCALE = (1.0, 1.0, 1.0)


# =====================================================================
# B3a: MIRE_HAG — swamp mud-scooping & mud-throwing fen-crone
# =====================================================================

def build_mire_hag() -> tuple[RigBuilder, list[dict]]:
	name = "mire_hag"
	sheet = IndexedSheet(64, 64)
	uv = {
		"head": sheet.alloc_box("head", 6, 6, 6, "hide", detail_fn=paint_hag_face(eye_row=2)),
		"nose": sheet.alloc_box("nose", 2, 3, 3, "hide", hi=5, mid=4, side=3, back=3, lo=2, dark=1),
		"jaw": sheet.alloc_box("jaw", 5, 2, 5, "hide", detail_fn=paint_jaw_maw()),
		"hood": sheet.alloc_box("hood", 8, 4, 7, "shroud", detail_fn=paint_reed_shawl),
		"hair": sheet.alloc_box("hair", 2, 7, 2, "shroud", detail_fn=paint_reed_shawl),
		"neck": sheet.alloc_box("neck", 3, 3, 3, "hide", hi=4, mid=3, side=2, back=2, lo=1, dark=1),
		"chest": sheet.alloc_box("chest", 8, 6, 6, "hide", detail_fn=paint_ribbed_torso("bone", "hide", "hide")),
		"shawl": sheet.alloc_box("shawl", 10, 6, 7, "shroud", detail_fn=paint_reed_shawl),
		"gourd": sheet.alloc_box("gourd", 3, 4, 3, "filth", hi=5, mid=4, side=3, back=3, lo=2, dark=1),
		"root_fetish": sheet.alloc_box("root_fetish", 1, 4, 2, "bone", detail_fn=paint_claw_tip),
		"body": sheet.alloc_box("body", 6, 5, 5, "shroud", detail_fn=paint_tattered_cloth("shroud")),
		"waist": sheet.alloc_box("waist", 7, 4, 5, "shroud", detail_fn=paint_mud_caked("shroud")),
		"skirt": sheet.alloc_box("skirt", 7, 6, 1, "shroud", detail_fn=paint_mud_caked("shroud")),
		"arm": sheet.alloc_box("arm", 2, 7, 3, "hide", hi=5, mid=4, side=3, back=3, lo=2, dark=1),
		"forearm": sheet.alloc_box("forearm", 3, 7, 3, "hide", detail_fn=paint_mud_caked("hide")),
		"hand": sheet.alloc_box("hand", 3, 3, 3, "filth", hi=4, mid=3, side=2, back=2, lo=1, dark=1),
		"claw": sheet.alloc_box("claw", 1, 3, 1, "bone", detail_fn=paint_claw_tip),
		"mud_ball": sheet.alloc_box("mud_ball", 4, 4, 4, "filth", detail_fn=paint_mud_clump),
		"leg": sheet.alloc_box("leg", 3, 6, 3, "hide", hi=4, mid=3, side=3, back=2, lo=2, dark=1),
		"shin": sheet.alloc_box("shin", 2, 6, 2, "hide", detail_fn=paint_mud_caked("hide")),
		"foot": sheet.alloc_box("foot", 3, 2, 5, "filth", hi=4, mid=3, side=2, back=2, lo=1, dark=1),
	}

	b = RigBuilder(name, sheet)
	b.bone("root", None, (0, 0, 0))

	# Bowed swamp-wading crone pelvis & mud-hemmed reed skirt (~29 units = 1.81 blocks tall)
	hip_y, hip_z = 11.8, 0.8
	b.bone("waist", "root", (0, hip_y, hip_z))
	b.cube("waist", "pelvis", (-3.5, hip_y - 1.8, hip_z - 2.4), (3.5, hip_y + 2.0, hip_z + 2.6), uv["waist"])
	b.cube("waist", "skirt_front", (-3.4, hip_y - 6.8, hip_z - 2.6), (3.4, hip_y - 1.0, hip_z - 1.6), uv["skirt"], rotation=(-8, 0, 0), origin=(0, hip_y - 1.2, hip_z - 2.2))
	b.cube("waist", "skirt_back", (-3.6, hip_y - 7.2, hip_z + 1.8), (3.6, hip_y - 1.0, hip_z + 2.8), uv["skirt"], rotation=(12, 0, 0), origin=(0, hip_y - 1.2, hip_z + 2.2))

	# Crooked mud-caked bare legs & splayed bog-feet
	for side, sx, mir in (("right", 1, False), ("left", -1, True)):
		lx = 2.2 * sx
		b.bone(f"{side}_leg", "waist", (lx, hip_y - 0.8, hip_z), (14, 0, 6 * sx))
		b.cube(f"{side}_leg", "thigh", (lx - 1.3, 5.8, hip_z - 1.4), (lx + 1.3, hip_y, hip_z + 1.4), uv["leg"], mirror=mir)

		b.bone(f"{side}_shin", f"{side}_leg", (lx, 6.0, hip_z), (-20, 0, -6 * sx))
		b.cube(f"{side}_shin", "calf", (lx - 1.0, 1.4, hip_z - 1.1), (lx + 1.0, 6.2, hip_z + 1.1), uv["shin"], mirror=mir)

		b.bone(f"{side}_foot", f"{side}_shin", (lx, 1.5, hip_z), (6, 0, 0))
		b.cube(f"{side}_foot", "bog_sole", (lx - 1.5, 0.0, hip_z - 3.6), (lx + 1.5, 1.6, hip_z + 1.4), uv["foot"], mirror=mir)

	# Stooped abdomen & wide peat-moss shawl-draped hunchback chest
	abd_y, abd_z = 13.4, 0.8
	b.bone("body", "waist", (0, abd_y, abd_z), (-14, 0, 0))
	b.cube("body", "midriff", (-3.0, abd_y - 0.4, abd_z - 2.2), (3.0, abd_y + 4.8, abd_z + 2.4), uv["body"])

	chest_y, chest_z = 17.8, 0.4
	b.bone("chest", "body", (0, chest_y, chest_z), (-22, 0, 0))
	b.cube("chest", "thorax", (-3.8, chest_y - 0.2, chest_z - 2.8), (3.8, chest_y + 5.8, chest_z + 3.0), uv["chest"])

	# Layered sodden peat-reed shawl & strapped bog-gourd fetishes on her hump
	b.bone("shawl", "chest", (0, chest_y + 5.0, chest_z + 0.4))
	b.cube("shawl", "mantle", (-4.9, chest_y + 0.4, chest_z - 3.3), (4.9, chest_y + 6.2, chest_z + 3.7), uv["shawl"])
	b.cube("shawl", "bog_gourd_r", (1.2, chest_y + 1.4, chest_z + 3.4), (4.0, chest_y + 5.4, chest_z + 6.2), uv["gourd"], rotation=(-14, 12, -10), origin=(2.6, chest_y + 3.4, chest_z + 3.6))
	b.cube("shawl", "bog_gourd_l", (-3.8, chest_y + 2.0, chest_z + 3.2), (-1.2, chest_y + 5.6, chest_z + 5.8), uv["gourd"], mirror=True, rotation=(-10, -14, 8), origin=(-2.5, chest_y + 3.8, chest_z + 3.5))
	b.cube("shawl", "root_fetish", (-0.6, chest_y + 3.6, chest_z + 3.4), (0.6, chest_y + 7.6, chest_z + 5.2), uv["root_fetish"], rotation=(-28, 0, 6), origin=(0, chest_y + 4.0, chest_z + 3.6))

	# Craned crone neck, hooded skull, hooked nose, reed-locks & crooked cackling jaw
	neck_y, neck_z = chest_y + 5.2, chest_z - 1.2
	b.bone("neck", "chest", (0, neck_y, neck_z), (26, 0, 0))
	b.cube("neck", "throat", (-1.5, neck_y - 0.8, neck_z - 2.2), (1.5, neck_y + 2.2, neck_z + 0.8), uv["neck"])

	head_y, head_z = neck_y + 1.4, neck_z - 1.2
	b.bone("head", "neck", (0, head_y, head_z), (12, 0, 0))
	b.cube("head", "skull", (-3.0, head_y - 0.6, head_z - 5.2), (3.0, head_y + 5.4, head_z + 1.0), uv["head"])
	b.cube("head", "hook_nose", (-0.9, head_y + 0.2, head_z - 7.6), (0.9, head_y + 3.0, head_z - 4.8), uv["nose"], rotation=(18, 0, 0), origin=(0, head_y + 2.4, head_z - 5.2))
	b.cube("head", "reed_cowl", (-3.9, head_y + 2.8, head_z - 5.8), (3.9, head_y + 6.6, head_z + 1.6), uv["hood"], rotation=(6, 0, 0), origin=(0, head_y + 4.4, head_z - 2.0))

	# Hanging sodden reed-hair tresses framing her gaunt face
	for side, sx, mir in (("right", 1, False), ("left", -1, True)):
		hx0, hx1 = sorted((2.7 * sx, 4.1 * sx))
		b.bone(f"hair_{side}", "head", (3.2 * sx, head_y + 3.2, head_z - 3.4), (6, 0, 8 * sx))
		b.cube(f"hair_{side}", "tress", (hx0, head_y - 3.8, head_z - 4.4), (hx1, head_y + 3.4, head_z - 2.4), uv["hair"], mirror=mir)

	jaw_y, jaw_z = head_y - 0.4, head_z - 1.0
	b.bone("jaw", "head", (0, jaw_y, jaw_z), (-12, 0, 3))
	b.cube("jaw", "mandible", (-2.4, jaw_y - 1.8, jaw_z - 4.6), (2.4, jaw_y + 0.2, jaw_z + 0.4), uv["jaw"])
	b.cube("jaw", "chin_wart", (-0.8, jaw_y - 2.4, jaw_z - 5.2), (0.8, jaw_y - 0.8, jaw_z - 3.8), uv["nose"])

	# Gnarled mud-caked arms & wide scooping claws + cupped mud_clump bone on right_hand
	sh_y, sh_z = chest_y + 4.4, chest_z - 0.4
	arm_len, fore_len = 6.6, 6.8
	for side, sx, mir in (("right", 1, False), ("left", -1, True)):
		ax = 4.6 * sx
		b.bone(f"{side}_arm", "chest", (ax, sh_y, sh_z), (42, 0, 12 * sx))
		b.cube(f"{side}_arm", "humerus", (ax - 1.2, sh_y - arm_len, sh_z - 1.4), (ax + 1.2, sh_y + 0.8, sh_z + 1.4), uv["arm"], mirror=mir)

		elb_y = sh_y - arm_len
		b.bone(f"{side}_forearm", f"{side}_arm", (ax, elb_y, sh_z), (28, 0, -6 * sx))
		b.cube(f"{side}_forearm", "ulna", (ax - 1.4, elb_y - fore_len, sh_z - 1.4), (ax + 1.4, elb_y + 0.4, sh_z + 1.4), uv["forearm"], mirror=mir)

		wr_y = elb_y - fore_len
		b.bone(f"{side}_hand", f"{side}_forearm", (ax, wr_y, sh_z), (-12, 0, -6 * sx))
		b.cube(f"{side}_hand", "scoop_palm", (ax - 1.6, wr_y - 2.4, sh_z - 1.6), (ax + 1.6, wr_y + 0.2, sh_z + 1.4), uv["hand"], mirror=mir)
		for ci, (ox, oz, rx_c, rz_c) in enumerate(((-1.0, -1.2, 24, -10), (0.0, -1.4, 20, 0), (1.0, -1.0, 16, 10))):
			cx = ax + ox * sx
			cz = sh_z + oz
			x0, x1 = sorted((cx - 0.4, cx + 0.4))
			b.cube(
				f"{side}_hand", f"claw_{ci}",
				(x0, wr_y - 4.8, cz - 0.45), (x1, wr_y - 2.0, cz + 0.45),
				uv["claw"], mirror=mir, rotation=(rx_c, 0, rz_c * sx), origin=(cx, wr_y - 2.1, cz),
			)
		if side == "right":
			# Dripping swamp-mud projectile clump cupped in front of her right scooping claw
			mc_x, mc_y, mc_z = ax - 1.2, wr_y - 3.2, sh_z - 2.6
			b.bone("mud_clump", "right_hand", (mc_x, mc_y, mc_z))
			b.cube("mud_clump", "core", (mc_x - 2.1, mc_y - 2.0, mc_z - 2.1), (mc_x + 2.1, mc_y + 2.0, mc_z + 2.1), uv["mud_ball"])
			b.cube("mud_clump", "drip", (mc_x - 1.1, mc_y - 3.1, mc_z - 1.0), (mc_x + 1.1, mc_y - 1.6, mc_z + 1.0), uv["hand"])

	# ---------------------------------------------------------------- Animations (7)
	hide_mud = {"scale": [(0.0, ZERO_SCALE), (0.01, ZERO_SCALE)]}

	# 1. idle (loop): crooked swamp-crone sway, reed-locks pendulating, cackling jaw, empty claws
	idle = make_anim(name, "idle", 3.0, "loop", {
		"waist": {"rotation": closed_loop(3.0, (0.0, (0, 0, -2)), (0.5, (2, 0, 2)))},
		"chest": {"rotation": closed_loop(3.0, (0.0, Z), (0.5, (-5, 0, 2)))},
		"shawl": {"rotation": closed_loop(3.0, (0.0, Z), (0.5, (3, 0, -2)))},
		"head": {"rotation": closed_loop(3.0, (0.0, (0, -6, -2)), (0.4, (4, 8, 3)), (0.75, (-3, -4, -2)))},
		"jaw": {"rotation": closed_loop(3.0, (0.0, (2, 0, 0)), (0.35, (18, 0, 0)), (0.55, (6, 0, 0)), (0.75, (16, 0, 0)))},
		"hair_right": {"rotation": closed_loop(3.0, (0.0, (0, 0, 2)), (0.5, (6, 0, 8)))},
		"hair_left": {"rotation": closed_loop(3.0, (0.0, (0, 0, -2)), (0.5, (6, 0, -8)))},
		**mirror_rot("arm", closed_loop(3.0, (0.0, Z), (0.5, (-6, 0, 5)))),
		**mirror_rot("hand", closed_loop(3.0, (0.0, Z), (0.5, (10, 0, 6)))),
		"mud_clump": {"scale": [(0.0, ZERO_SCALE), (3.0, ZERO_SCALE)]},
	})

	# 2. walk (loop): hunched swamp-wading stalk, high-stepping feet out of thick mire
	walk = make_anim(name, "walk", 1.0, "loop", {
		"root": {"position": closed_loop(1.0, (0.0, Z), (0.25, (0, 0.7, 0)), (0.5, Z), (0.75, (0, 0.7, 0)))},
		"waist": {"rotation": closed_loop(1.0, (0.0, (3, -8, -4)), (0.5, (3, 8, 4)))},
		"chest": {"rotation": closed_loop(1.0, (0.0, (6, 9, 4)), (0.5, (6, -9, -4)))},
		"shawl": {"rotation": closed_loop(1.0, (0.0, (-3, -4, 0)), (0.5, (4, 4, 0)))},
		"head": {"rotation": closed_loop(1.0, (0.0, (-4, -6, -2)), (0.5, (-4, 6, 2)))},
		"jaw": {"rotation": closed_loop(1.0, (0.0, (6, 0, 0)), (0.5, (14, 0, 0)))},
		"right_leg": {"rotation": closed_loop(1.0, (0.0, (-30, 0, 0)), (0.5, (24, 0, 0)))},
		"left_leg": {"rotation": closed_loop(1.0, (0.0, (24, 0, 0)), (0.5, (-30, 0, 0)))},
		"right_shin": {"rotation": closed_loop(1.0, (0.0, (12, 0, 0)), (0.5, (28, 0, 0)))},
		"left_shin": {"rotation": closed_loop(1.0, (0.0, (28, 0, 0)), (0.5, (12, 0, 0)))},
		"right_arm": {"rotation": closed_loop(1.0, (0.0, (20, 0, 6)), (0.5, (-26, 0, 4)))},
		"left_arm": {"rotation": closed_loop(1.0, (0.0, (-26, 0, -4)), (0.5, (20, 0, -6)))},
		"mud_clump": {"scale": [(0.0, ZERO_SCALE), (1.0, ZERO_SCALE)]},
	})

	# 3. run (loop): skittering hunched bog rush
	run = make_anim(name, "run", 0.65, "loop", {
		"root": {"position": closed_loop(0.65, (0.0, Z), (0.25, (0, 1.2, 0)), (0.5, Z), (0.75, (0, 1.2, 0)))},
		"waist": {"rotation": closed_loop(0.65, (0.0, (8, -10, -5)), (0.5, (8, 10, 5)))},
		"chest": {"rotation": closed_loop(0.65, (0.0, (12, 10, 4)), (0.5, (12, -10, -4)))},
		"head": {"rotation": closed_loop(0.65, (0.0, (-12, -5, 0)), (0.5, (-12, 5, 0)))},
		"jaw": {"rotation": closed_loop(0.65, (0.0, (16, 0, 0)), (0.5, (26, 0, 0)))},
		"hair_right": {"rotation": closed_loop(0.65, (0.0, (22, 0, 10)), (0.5, (14, 0, 6)))},
		"hair_left": {"rotation": closed_loop(0.65, (0.0, (14, 0, -6)), (0.5, (22, 0, -10)))},
		"right_leg": {"rotation": closed_loop(0.65, (0.0, (-38, 0, 0)), (0.5, (32, 0, 0)))},
		"left_leg": {"rotation": closed_loop(0.65, (0.0, (32, 0, 0)), (0.5, (-38, 0, 0)))},
		**mirror_rot("arm", closed_loop(0.65, (0.0, (-38, 0, 12)), (0.5, (-22, 0, 8)))),
		"mud_clump": {"scale": [(0.0, ZERO_SCALE), (0.65, ZERO_SCALE)]},
	})

	# 4. attack (once): vicious claw rake + cackling snap
	attack = make_anim(name, "attack", 0.60, "once", {
		"chest": {"rotation": [(0.0, Z), (0.18, (-12, 22, 0)), (0.34, (18, -24, 0)), (0.60, Z)]},
		"head": {"rotation": [(0.0, Z), (0.18, (-10, -14, 0)), (0.34, (10, 14, 0)), (0.60, Z)]},
		"jaw": {"rotation": [(0.0, Z), (0.18, (28, 0, 0)), (0.34, (-4, 0, 0)), (0.60, Z)]},
		"right_arm": {"rotation": [(0.0, Z), (0.18, (-94, 18, 28)), (0.34, (-16, -32, -14)), (0.60, Z)]},
		"right_forearm": {"rotation": [(0.0, Z), (0.18, (-24, 0, 0)), (0.34, (10, 0, -10)), (0.60, Z)]},
		"left_arm": {"rotation": [(0.0, Z), (0.18, (18, -10, -10)), (0.34, (-62, 16, -20)), (0.60, Z)]},
		"mud_clump": hide_mud,
	})

	# 5. mud_scoop (once, 0.95s): stoops deep to the muddy riverbank (0.38s), plunges both scooping claws into the ground,
	# gouges out a dripping mud_clump (scales 0 -> 1 at 0.36..0.44s), and hauls it up to chest height!
	mud_scoop = make_anim(name, "mud_scoop", 0.95, "once", {
		"root": {"position": [(0.0, Z), (0.38, (0, -4.6, -1.2)), (0.56, (0, -4.2, -0.8)), (0.95, (0, -0.6, 0))]},
		"waist": {"rotation": [(0.0, Z), (0.38, (24, 0, 0)), (0.56, (20, 0, 0)), (0.95, (4, 0, 0))]},
		"body": {"rotation": [(0.0, Z), (0.38, (18, 0, 0)), (0.56, (14, 0, 0)), (0.95, Z)]},
		"chest": {"rotation": [(0.0, Z), (0.38, (22, 0, 0)), (0.56, (16, 0, 0)), (0.95, (-6, 0, 0))]},
		"head": {"rotation": [(0.0, Z), (0.38, (-28, 0, 0)), (0.56, (-22, 0, 0)), (0.95, (-4, 0, 0))]},
		"jaw": {"rotation": [(0.0, Z), (0.38, (12, 0, 0)), (0.56, (28, 0, 0)), (0.95, (14, 0, 0))]},
		**mirror_rot("leg", [(0.0, Z), (0.38, (-32, 0, 6)), (0.56, (-28, 0, 6)), (0.95, (-6, 0, 0))]),
		**mirror_rot("shin", [(0.0, Z), (0.38, (36, 0, 0)), (0.56, (32, 0, 0)), (0.95, (8, 0, 0))]),
		"right_arm": {"rotation": [(0.0, Z), (0.38, (-36, -16, 6)), (0.56, (-48, -20, 4)), (0.95, (-44, -18, 6))]},
		"left_arm": {"rotation": [(0.0, Z), (0.38, (-36, 16, -6)), (0.56, (-48, 20, -4)), (0.95, (-40, 16, -6))]},
		**mirror_rot("forearm", [(0.0, Z), (0.38, (-12, 0, 0)), (0.56, (-34, 0, 8)), (0.95, (-38, 0, 10))]),
		**mirror_rot("hand", [(0.0, Z), (0.38, (24, 0, -10)), (0.56, (-18, 0, 12)), (0.95, (-14, 0, 10))]),
		"mud_clump": {"scale": [(0.0, ZERO_SCALE), (0.34, ZERO_SCALE), (0.44, UNIT_SCALE), (0.95, UNIT_SCALE)]},
	})

	# 6. mud_throw (once, 0.80s): winds back clutching mud_clump high (0.26s), whips forward to hurl it (0.42s, mud_clump releases -> 0)!
	mud_throw = make_anim(name, "mud_throw", 0.80, "once", {
		"root": {"position": [(0.0, (0, -0.6, 0)), (0.26, (0, 0.6, 1.2)), (0.44, (0, -1.0, -1.6)), (0.80, Z)]},
		"waist": {"rotation": [(0.0, (4, 0, 0)), (0.26, (-12, 22, 0)), (0.44, (16, -24, 0)), (0.80, Z)]},
		"chest": {"rotation": [(0.0, (-6, 0, 0)), (0.26, (-18, 28, -4)), (0.44, (24, -30, 6)), (0.80, Z)]},
		"head": {"rotation": [(0.0, (-4, 0, 0)), (0.26, (-8, -22, 0)), (0.44, (8, 20, 0)), (0.80, Z)]},
		"jaw": {"rotation": [(0.0, (14, 0, 0)), (0.26, (22, 0, 0)), (0.44, (36, 0, 0)), (0.80, Z)]},
		"right_arm": {"rotation": [(0.0, (-44, -18, 6)), (0.26, (-118, 22, 34)), (0.44, (-24, -28, -12)), (0.80, Z)]},
		"right_forearm": {"rotation": [(0.0, (-38, 0, 10)), (0.26, (-26, 0, 0)), (0.44, (12, 0, -8)), (0.80, Z)]},
		"right_hand": {"rotation": [(0.0, (-14, 0, 10)), (0.26, (-20, 0, 8)), (0.44, (28, 0, -10)), (0.80, Z)]},
		"left_arm": {"rotation": [(0.0, (-40, 16, -6)), (0.26, (-64, 10, -18)), (0.44, (24, -14, -16)), (0.80, Z)]},
		"mud_clump": {"scale": [(0.0, UNIT_SCALE), (0.38, UNIT_SCALE), (0.42, ZERO_SCALE), (0.80, ZERO_SCALE)]},
	})

	# 7. scoop_and_throw (once, 1.45s): complete stoop -> gouge bank -> wind back -> hurl sequence in one animation
	scoop_and_throw = make_anim(name, "scoop_and_throw", 1.45, "once", {
		"root": {"position": [
			(0.0, Z), (0.36, (0, -4.6, -1.2)), (0.54, (0, -4.0, -0.6)),
			(0.88, (0, 0.6, 1.2)), (1.08, (0, -1.0, -1.6)), (1.45, Z),
		]},
		"waist": {"rotation": [
			(0.0, Z), (0.36, (24, 0, 0)), (0.54, (18, 0, 0)),
			(0.88, (-12, 22, 0)), (1.08, (16, -24, 0)), (1.45, Z),
		]},
		"chest": {"rotation": [
			(0.0, Z), (0.36, (24, 0, 0)), (0.54, (16, 0, 0)),
			(0.88, (-18, 28, -4)), (1.08, (24, -30, 6)), (1.45, Z),
		]},
		"head": {"rotation": [
			(0.0, Z), (0.36, (-28, 0, 0)), (0.54, (-20, 0, 0)),
			(0.88, (-8, -22, 0)), (1.08, (8, 20, 0)), (1.45, Z),
		]},
		"jaw": {"rotation": [
			(0.0, Z), (0.36, (14, 0, 0)), (0.54, (26, 0, 0)),
			(0.88, (22, 0, 0)), (1.08, (36, 0, 0)), (1.45, Z),
		]},
		**mirror_rot("leg", [(0.0, Z), (0.36, (-32, 0, 6)), (0.54, (-26, 0, 6)), (0.88, (6, 0, 0)), (1.45, Z)]),
		**mirror_rot("shin", [(0.0, Z), (0.36, (36, 0, 0)), (0.54, (30, 0, 0)), (0.88, (-4, 0, 0)), (1.45, Z)]),
		"right_arm": {"rotation": [
			(0.0, Z), (0.36, (-36, -16, 6)), (0.54, (-48, -20, 4)),
			(0.88, (-118, 22, 34)), (1.08, (-24, -28, -12)), (1.45, Z),
		]},
		"right_forearm": {"rotation": [
			(0.0, Z), (0.36, (-12, 0, 0)), (0.54, (-36, 0, 8)),
			(0.88, (-26, 0, 0)), (1.08, (12, 0, -8)), (1.45, Z),
		]},
		"left_arm": {"rotation": [
			(0.0, Z), (0.36, (-36, 16, -6)), (0.54, (-48, 20, -4)),
			(0.88, (-64, 10, -18)), (1.08, (24, -14, -16)), (1.45, Z),
		]},
		"mud_clump": {"scale": [
			(0.0, ZERO_SCALE), (0.34, ZERO_SCALE), (0.42, UNIT_SCALE),
			(1.02, UNIT_SCALE), (1.06, ZERO_SCALE), (1.45, ZERO_SCALE),
		]},
	})

	return b, [idle, walk, run, attack, mud_scoop, mud_throw, scoop_and_throw]


# =====================================================================
# B3b: MUD_SCOOP — claw-furrowed mud scoop left behind in the riverbank
# =====================================================================

def build_mud_scoop() -> tuple[RigBuilder, list[dict]]:
	name = "mud_scoop"
	sheet = IndexedSheet(64, 64)
	uv = {
		"floor": sheet.alloc_box("floor", 16, 4, 16, "filth", detail_fn=paint_mud_clump),
		"puddle": sheet.alloc_box("puddle", 9, 1, 9, "hide", hi=4, mid=3, side=2, back=2, lo=1, dark=0),
		"wall_ns": sheet.alloc_box("wall_ns", 16, 6, 3, "filth", detail_fn=paint_mud_clump),
		"wall_ew": sheet.alloc_box("wall_ew", 3, 6, 10, "filth", detail_fn=paint_mud_clump),
		"clod": sheet.alloc_box("clod", 4, 3, 4, "filth", detail_fn=paint_mud_clump),
		"furrow": sheet.alloc_box("furrow", 1, 2, 7, "hide", hi=3, mid=2, side=2, back=1, lo=1, dark=0),
		"pebble": sheet.alloc_box("pebble", 2, 1, 2, "bone", hi=5, mid=4, side=3, back=3, lo=2, dark=1),
	}

	b = RigBuilder(name, sheet)
	b.bone("root", None, (0, 0, 0))

	# Organic riverbank mud mound [y=0..3.6] + seeping mire sludge puddle at the bottom of the gouged hollow (9 bones total)
	b.bone("bank_base", "root", (0, 0, 0))
	b.cube("bank_base", "sub_mud", (-7.0, 0.0, -7.0), (7.0, 3.6, 7.0), uv["floor"])
	b.cube("bank_base", "apron_nw", (-7.8, 0.0, -7.6), (-1.6, 2.4, -1.6), uv["clod"])
	b.cube("bank_base", "apron_se", (1.6, 0.0, 1.6), (7.8, 2.6, 7.6), uv["clod"])

	b.bone("sludge_pool", "bank_base", (0, 3.6, 0))
	b.cube("sludge_pool", "mire_water", (-4.2, 3.6, -4.2), (4.2, 4.3, 4.2), uv["puddle"])

	# Stepped gouged bank rim walls leaving a deep Asymmetric scooped basin in the middle (open toward north front!)
	b.bone("rim_south", "bank_base", (0, 3.6, 5.6))
	b.cube("rim_south", "wall_south", (-6.8, 3.6, 4.0), (6.8, 9.6, 7.2), uv["wall_ns"])

	b.bone("rim_north", "bank_base", (0, 3.6, -5.6))
	b.cube("rim_north", "lip_north", (-6.4, 3.6, -7.0), (6.4, 6.2, -4.4), uv["wall_ns"])

	b.bone("rim_east", "bank_base", (5.6, 3.6, 0))
	b.cube("rim_east", "wall_east", (4.0, 3.6, -4.6), (7.2, 8.8, 4.4), uv["wall_ew"])

	b.bone("rim_west", "bank_base", (-5.6, 3.6, 0))
	b.cube("rim_west", "wall_west", (-7.2, 3.6, -4.6), (-4.0, 8.4, 4.4), uv["wall_ew"])

	# Three parallel claw-furrow gouge ridges across the scooped basin floor
	b.bone("claw_furrows", "bank_base", (0, 4.2, 0))
	for fi, fx in enumerate((-2.2, 0.0, 2.2)):
		b.cube("claw_furrows", f"gouge_{fi}", (fx - 0.45, 4.0, -3.6), (fx + 0.45, 5.3, 3.4), uv["furrow"])

	# Displaced mud clods & unearthed river pebble kicked onto the rim (single-axis 22.5 deg angles for Java block JSON)
	b.bone("clods", "bank_base", (0, 8.6, 0))
	b.cube("clods", "clod_sw", (-6.8, 8.0, 2.8), (-2.8, 11.0, 6.8), uv["clod"], rotation=(0, 22.5, 0), origin=(-4.8, 9.0, 4.8))
	b.cube("clods", "clod_ne", (2.8, 5.8, -6.8), (6.6, 8.6, -3.2), uv["clod"], rotation=(0, -22.5, 0), origin=(4.7, 7.0, -5.0))
	b.cube("clods", "clod_se", (3.0, 8.8, 3.2), (6.8, 11.6, 6.8), uv["clod"])
	b.cube("clods", "pebble", (-3.6, 6.0, -6.6), (-1.6, 7.2, -4.6), uv["pebble"], rotation=(0, 22.5, 0), origin=(-2.6, 6.6, -5.6))

	idle = make_anim(name, "idle", 3.0, "loop", {
		"bank_base": {"rotation": closed_loop(3.0, (0.0, Z), (0.5, Z))},
	})
	settle = make_anim(name, "settle", 0.50, "once", {
		"clods": {"position": [(0.0, (0, 1.4, 0)), (0.24, (0, -0.2, 0)), (0.50, Z)]},
	})
	return b, [idle, settle]


# =====================================================================
# B3c: GRAVE_HAG — cemetery mourner-crone with skull collar & barbed tongue
# =====================================================================

def build_grave_hag() -> tuple[RigBuilder, list[dict]]:
	name = "grave_hag"
	sheet = IndexedSheet(64, 64)
	uv = {
		"head": sheet.alloc_box("head", 6, 7, 6, "hide", detail_fn=paint_hag_face(eye_row=2)),
		"jaw": sheet.alloc_box("jaw", 5, 2, 6, "hide", detail_fn=paint_jaw_maw()),
		"veil_top": sheet.alloc_box("veil_top", 7, 3, 7, "shroud", detail_fn=paint_tattered_cloth("shroud")),
		"veil_back": sheet.alloc_box("veil_back", 7, 11, 1, "shroud", detail_fn=paint_tattered_cloth("shroud")),
		"neck": sheet.alloc_box("neck", 3, 4, 3, "hide", hi=4, mid=3, side=2, back=2, lo=1, dark=1),
		"chest": sheet.alloc_box("chest", 7, 7, 5, "hide", detail_fn=paint_ribbed_torso("bone", "hide", "hide")),
		"trophy_skull": sheet.alloc_box("trophy_skull", 3, 3, 3, "bone", detail_fn=paint_mini_skull),
		"hair_pin": sheet.alloc_box("hair_pin", 1, 1, 5, "bone", detail_fn=paint_claw_tip),
		"body": sheet.alloc_box("body", 5, 5, 4, "hide", hi=4, mid=3, side=2, back=2, lo=1, dark=1),
		"waist": sheet.alloc_box("waist", 6, 4, 4, "shroud", detail_fn=paint_tattered_cloth("shroud")),
		"shroud_strip": sheet.alloc_box("shroud_strip", 5, 9, 1, "shroud", detail_fn=paint_tattered_cloth("shroud")),
		"tongue": sheet.alloc_box("tongue", 2, 1, 3, "sinew", detail_fn=paint_barbed_tongue),
		"tongue_tip": sheet.alloc_box("tongue_tip", 2, 1, 2, "sinew", detail_fn=paint_barbed_tongue),
		"arm": sheet.alloc_box("arm", 2, 8, 2, "hide", hi=5, mid=4, side=3, back=3, lo=2, dark=1),
		"forearm": sheet.alloc_box("forearm", 2, 9, 2, "hide", detail_fn=paint_mud_caked("hide")),
		"hand": sheet.alloc_box("hand", 2, 3, 3, "filth", hi=4, mid=3, side=2, back=2, lo=1, dark=1),
		"claw": sheet.alloc_box("claw", 1, 4, 1, "bone", detail_fn=paint_claw_tip),
		"leg": sheet.alloc_box("leg", 2, 8, 3, "hide", hi=4, mid=3, side=3, back=2, lo=2, dark=1),
		"shin": sheet.alloc_box("shin", 2, 8, 2, "hide", detail_fn=paint_mud_caked("hide")),
		"foot": sheet.alloc_box("foot", 3, 2, 5, "filth", hi=4, mid=3, side=2, back=2, lo=1, dark=1),
	}

	b = RigBuilder(name, sheet)
	b.bone("root", None, (0, 0, 0))

	# Tall, famine-gaunt cemetery hag (~33.5 units = 2.09 blocks tall, stark angular silhouette)
	hip_y, hip_z = 15.0, 0.6
	b.bone("waist", "root", (0, hip_y, hip_z))
	b.cube("waist", "pelvis", (-2.9, hip_y - 1.8, hip_z - 1.9), (2.9, hip_y + 1.8, hip_z + 2.1), uv["waist"])
	b.cube("waist", "shroud_front", (-2.2, hip_y - 9.4, hip_z - 2.2), (2.2, hip_y - 0.8, hip_z - 1.3), uv["shroud_strip"], rotation=(-4, 0, 0), origin=(0, hip_y - 1.0, hip_z - 1.8))

	b.bone("shroud_back", "waist", (0, hip_y - 0.8, hip_z + 2.0), (8, 0, 0))
	b.cube("shroud_back", "tail_cloth", (-2.6, hip_y - 10.0, hip_z + 1.6), (2.6, hip_y - 0.6, hip_z + 2.5), uv["shroud_strip"])

	# Long gaunt graveyard-stalking legs
	for side, sx, mir in (("right", 1, False), ("left", -1, True)):
		lx = 2.1 * sx
		knee_y = 8.0
		b.bone(f"{side}_leg", "waist", (lx, hip_y - 0.6, hip_z), (16, 0, 3 * sx))
		b.cube(f"{side}_leg", "thigh", (lx - 1.1, knee_y, hip_z - 1.4), (lx + 1.1, hip_y + 0.2, hip_z + 1.4), uv["leg"], mirror=mir)

		b.bone(f"{side}_shin", f"{side}_leg", (lx, knee_y, hip_z), (-22, 0, -3 * sx))
		b.cube(f"{side}_shin", "calf", (lx - 0.95, 1.5, hip_z - 1.0), (lx + 0.95, knee_y + 0.3, hip_z + 1.0), uv["shin"], mirror=mir)

		b.bone(f"{side}_foot", f"{side}_shin", (lx, 1.5, hip_z), (6, 0, 0))
		b.cube(f"{side}_foot", "sole", (lx - 1.3, 0.0, hip_z - 3.6), (lx + 1.3, 1.6, hip_z + 1.2), uv["foot"], mirror=mir)
		b.cube(f"{side}_foot", "toe_spur", (lx - 0.4, 0.0, hip_z - 4.7), (lx + 0.4, 1.0, hip_z - 3.4), uv["claw"], mirror=mir)

	# Narrow starved waist rising into an angular exposed-rib chest
	abd_y, abd_z = 16.4, 0.6
	b.bone("body", "waist", (0, abd_y, abd_z), (-8, 0, 0))
	b.cube("body", "abdomen", (-2.3, abd_y - 0.2, abd_z - 1.8), (2.3, abd_y + 5.0, abd_z + 1.8), uv["body"])

	chest_y, chest_z = 21.0, 0.2
	b.bone("chest", "body", (0, chest_y, chest_z), (-14, 0, 0))
	b.cube("chest", "ribcage", (-3.5, chest_y - 0.2, chest_z - 2.6), (3.5, chest_y + 6.4, chest_z + 2.2), uv["chest"])

	# Grisly trophy necklace of 3 small skulls across her collarbone ("stronger with each kill in a row")
	b.bone("skull_collar", "chest", (0, chest_y + 5.2, chest_z - 2.4))
	b.cube("skull_collar", "skull_mid", (-1.4, chest_y + 2.6, chest_z - 4.4), (1.4, chest_y + 5.4, chest_z - 1.8), uv["trophy_skull"], rotation=(12, 0, 0), origin=(0, chest_y + 4.8, chest_z - 2.4))
	b.cube("skull_collar", "skull_r", (1.6, chest_y + 3.2, chest_z - 4.0), (4.1, chest_y + 5.8, chest_z - 1.6), uv["trophy_skull"], rotation=(8, -22, -14), origin=(2.6, chest_y + 5.0, chest_z - 2.2))
	b.cube("skull_collar", "skull_l", (-4.1, chest_y + 3.2, chest_z - 4.0), (-1.6, chest_y + 5.8, chest_z - 1.6), uv["trophy_skull"], mirror=True, rotation=(8, 22, 14), origin=(-2.6, chest_y + 5.0, chest_z - 2.2))

	# Long sinewy neck, corpse-pallid skull, widow's mourning veil & bone hairpins
	neck_y, neck_z = chest_y + 6.0, chest_z - 1.0
	b.bone("neck", "chest", (0, neck_y, neck_z), (16, 0, 0))
	b.cube("neck", "throat", (-1.4, neck_y - 0.4, neck_z - 2.0), (1.4, neck_y + 3.2, neck_z + 0.8), uv["neck"])

	head_y, head_z = neck_y + 2.6, neck_z - 0.8
	b.bone("head", "neck", (0, head_y, head_z), (8, 0, 0))
	b.cube("head", "cranium", (-2.9, head_y - 0.4, head_z - 5.0), (2.9, head_y + 6.2, head_z + 1.0), uv["head"])
	b.cube("head", "veil_crown", (-3.5, head_y + 4.2, head_z - 5.4), (3.5, head_y + 7.0, head_z + 1.6), uv["veil_top"])
	b.cube("head", "bone_pin_r", (1.2, head_y + 5.4, head_z - 1.2), (2.0, head_y + 6.2, head_z + 4.0), uv["hair_pin"], rotation=(16, 20, 0), origin=(1.5, head_y + 5.8, head_z))
	b.cube("head", "bone_pin_l", (-2.0, head_y + 5.4, head_z - 1.2), (-1.2, head_y + 6.2, head_z + 4.0), uv["hair_pin"], mirror=True, rotation=(16, -20, 0), origin=(-1.5, head_y + 5.8, head_z))

	# Trailing widow's burial veil down her back
	b.bone("veil_back", "head", (0, head_y + 4.8, head_z + 1.2), (14, 0, 0))
	b.cube("veil_back", "drape", (-3.4, head_y - 6.4, head_z + 0.8), (3.4, head_y + 4.8, head_z + 1.8), uv["veil_back"])

	# Deeply hinged distensible lower jaw
	jaw_y, jaw_z = head_y - 0.3, head_z - 0.6
	b.bone("jaw", "head", (0, jaw_y, jaw_z), (-10, 0, 0))
	b.cube("jaw", "mandible", (-2.4, jaw_y - 1.8, jaw_z - 4.8), (2.4, jaw_y + 0.1, jaw_z + 0.4), uv["jaw"])

	# 3-segment barbed whipping tongue coiled cleanly inside the maw at rest (overlapping joints so zero gaps when stretched!)
	tb_y, tb_z = head_y - 0.35, head_z - 0.8
	b.bone("tongue_base", "head", (0, tb_y, tb_z))
	b.cube("tongue_base", "root_seg", (-0.85, tb_y - 0.4, tb_z - 1.9), (0.85, tb_y + 0.4, tb_z + 0.2), uv["tongue"])

	tm_z = tb_z - 1.6
	b.bone("tongue_mid", "tongue_base", (0, tb_y, tm_z))
	b.cube("tongue_mid", "shaft_seg", (-0.72, tb_y - 0.36, tm_z - 1.8), (0.72, tb_y + 0.36, tm_z + 0.35), uv["tongue"])

	tt_z = tm_z - 1.5
	b.bone("tongue_tip", "tongue_mid", (0, tb_y, tt_z))
	b.cube("tongue_tip", "lance_tip", (-0.9, tb_y - 0.42, tt_z - 1.25), (0.9, tb_y + 0.42, tt_z + 0.35), uv["tongue_tip"])
	b.cube("tongue_tip", "barb_r", (0.6, tb_y - 0.25, tt_z - 1.1), (1.35, tb_y + 0.25, tt_z - 0.2), uv["claw"], rotation=(0, -28, 0), origin=(0.8, tb_y, tt_z - 0.9))
	b.cube("tongue_tip", "barb_l", (-1.35, tb_y - 0.25, tt_z - 1.1), (-0.6, tb_y + 0.25, tt_z - 0.2), uv["claw"], mirror=True, rotation=(0, 28, 0), origin=(-0.8, tb_y, tt_z - 0.9))

	# Long skeletal grave-raking arms with long ossified bone spindle-claws
	sh_y, sh_z = chest_y + 5.4, chest_z - 0.4
	arm_len, fore_len = 8.0, 8.6
	for side, sx, mir in (("right", 1, False), ("left", -1, True)):
		ax = 4.3 * sx
		b.bone(f"{side}_arm", "chest", (ax, sh_y, sh_z), (24, 0, 9 * sx))
		b.cube(f"{side}_arm", "humerus", (ax - 1.0, sh_y - arm_len, sh_z - 1.1), (ax + 1.0, sh_y + 0.6, sh_z + 1.1), uv["arm"], mirror=mir)

		elb_y = sh_y - arm_len
		b.bone(f"{side}_forearm", f"{side}_arm", (ax, elb_y, sh_z), (24, 0, -4 * sx))
		b.cube(f"{side}_forearm", "ulna", (ax - 0.95, elb_y - fore_len, sh_z - 1.0), (ax + 0.95, elb_y + 0.3, sh_z + 1.0), uv["forearm"], mirror=mir)

		wr_y = elb_y - fore_len
		b.bone(f"{side}_hand", f"{side}_forearm", (ax, wr_y, sh_z), (-8, 0, -4 * sx))
		b.cube(f"{side}_hand", "palm", (ax - 1.2, wr_y - 2.4, sh_z - 1.4), (ax + 1.2, wr_y + 0.2, sh_z + 1.2), uv["hand"], mirror=mir)
		for ci, (ox, oz, rx_c, rz_c) in enumerate(((-0.75, -1.1, 20, -10), (0.0, -1.3, 14, 0), (0.75, -0.9, 10, 10))):
			cx = ax + ox * sx
			cz = sh_z + oz
			x0, x1 = sorted((cx - 0.35, cx + 0.35))
			b.cube(
				f"{side}_hand", f"spindle_claw_{ci}",
				(x0, wr_y - 5.8, cz - 0.38), (x1, wr_y - 2.1, cz + 0.38),
				uv["claw"], mirror=mir, rotation=(rx_c, 0, rz_c * sx), origin=(cx, wr_y - 2.2, cz),
			)

	# ---------------------------------------------------------------- Animations (7)

	# 1. idle (loop): eerie cemetery sway, skull_collar shifting, veil drifting, barbed tongue-tip glinting behind fangs
	idle = make_anim(name, "idle", 3.0, "loop", {
		"waist": {"rotation": closed_loop(3.0, (0.0, Z), (0.5, (2, 0, 2)))},
		"chest": {"rotation": closed_loop(3.0, (0.0, Z), (0.5, (-4, 0, -2)))},
		"skull_collar": {"rotation": closed_loop(3.0, (0.0, Z), (0.5, (-5, 0, 2)))},
		"veil_back": {"rotation": closed_loop(3.0, (0.0, (0, 0, -2)), (0.5, (6, 0, 2)))},
		"head": {"rotation": closed_loop(3.0, (0.0, (0, -6, 0)), (0.4, (4, 8, 3)), (0.75, (-3, -4, -3)))},
		"jaw": {"rotation": closed_loop(3.0, (0.0, (4, 0, 0)), (0.5, (18, 0, 0)))},
		"tongue_tip": {"rotation": closed_loop(3.0, (0.0, Z), (0.5, (-8, 8, 0)))},
		**mirror_rot("arm", closed_loop(3.0, (0.0, Z), (0.5, (-6, 0, 4)))),
		**mirror_rot("hand", closed_loop(3.0, (0.0, Z), (0.5, (12, 0, 6)))),
	})

	# 2. walk (loop): stalking predatory graveyard prowl
	walk = make_anim(name, "walk", 0.95, "loop", {
		"root": {"position": closed_loop(0.95, (0.0, Z), (0.25, (0, 0.6, 0)), (0.5, Z), (0.75, (0, 0.6, 0)))},
		"waist": {"rotation": closed_loop(0.95, (0.0, (3, -7, -3)), (0.5, (3, 7, 3)))},
		"chest": {"rotation": closed_loop(0.95, (0.0, (5, 8, 3)), (0.5, (5, -8, -3)))},
		"skull_collar": {"rotation": closed_loop(0.95, (0.0, (-4, -5, 0)), (0.5, (4, 5, 0)))},
		"veil_back": {"rotation": closed_loop(0.95, (0.0, (8, -4, 0)), (0.5, (14, 4, 0)))},
		"head": {"rotation": closed_loop(0.95, (0.0, (-4, -6, 0)), (0.5, (-4, 6, 0)))},
		"jaw": {"rotation": closed_loop(0.95, (0.0, (6, 0, 0)), (0.5, (14, 0, 0)))},
		"right_leg": {"rotation": closed_loop(0.95, (0.0, (-28, 0, 0)), (0.5, (24, 0, 0)))},
		"left_leg": {"rotation": closed_loop(0.95, (0.0, (24, 0, 0)), (0.5, (-28, 0, 0)))},
		"right_shin": {"rotation": closed_loop(0.95, (0.0, (10, 0, 0)), (0.5, (26, 0, 0)))},
		"left_shin": {"rotation": closed_loop(0.95, (0.0, (26, 0, 0)), (0.5, (10, 0, 0)))},
		"right_arm": {"rotation": closed_loop(0.95, (0.0, (18, 0, 6)), (0.5, (-24, 0, 4)))},
		"left_arm": {"rotation": closed_loop(0.95, (0.0, (-24, 0, -4)), (0.5, (18, 0, -6)))},
	})

	# 3. run (loop): lunging graveyard sprint, veil streaming, spindle-claws reaching
	run = make_anim(name, "run", 0.60, "loop", {
		"root": {"position": closed_loop(0.60, (0.0, Z), (0.25, (0, 1.4, 0)), (0.5, Z), (0.75, (0, 1.4, 0)))},
		"waist": {"rotation": closed_loop(0.60, (0.0, (8, -9, -4)), (0.5, (8, 9, 4)))},
		"chest": {"rotation": closed_loop(0.60, (0.0, (12, 10, 4)), (0.5, (12, -10, -4)))},
		"veil_back": {"rotation": closed_loop(0.60, (0.0, (28, 0, -4)), (0.5, (34, 0, 4)))},
		"head": {"rotation": closed_loop(0.60, (0.0, (-12, -5, 0)), (0.5, (-12, 5, 0)))},
		"jaw": {"rotation": closed_loop(0.60, (0.0, (18, 0, 0)), (0.5, (28, 0, 0)))},
		"right_leg": {"rotation": closed_loop(0.60, (0.0, (-40, 0, 0)), (0.5, (34, 0, 0)))},
		"left_leg": {"rotation": closed_loop(0.60, (0.0, (34, 0, 0)), (0.5, (-40, 0, 0)))},
		**mirror_rot("arm", closed_loop(0.60, (0.0, (-46, 0, 12)), (0.5, (-28, 0, 8)))),
	})

	# 4. attack (once): double spindle-claw rend
	attack = make_anim(name, "attack", 0.55, "once", {
		"chest": {"rotation": [(0.0, Z), (0.16, (-14, 18, 0)), (0.32, (18, -20, 0)), (0.55, Z)]},
		"head": {"rotation": [(0.0, Z), (0.16, (-10, -12, 0)), (0.32, (8, 12, 0)), (0.55, Z)]},
		"jaw": {"rotation": [(0.0, Z), (0.16, (28, 0, 0)), (0.32, (-4, 0, 0)), (0.55, Z)]},
		"right_arm": {"rotation": [(0.0, Z), (0.16, (-98, 16, 28)), (0.32, (-16, -28, -14)), (0.55, Z)]},
		"right_forearm": {"rotation": [(0.0, Z), (0.16, (-24, 0, 0)), (0.32, (12, 0, -8)), (0.55, Z)]},
		"left_arm": {"rotation": [(0.0, Z), (0.16, (20, -10, -12)), (0.32, (-70, 16, -22)), (0.55, Z)]},
	})

	# 5. tongue_lash (once, 0.85s): signature move!
	# Recoils head & unhinges jaw wide (0.22s), then snaps neck forward horizontally while tongue_base scales 4.2x along z
	# so the continuous 3-segment barbed tongue whips straight out ~1.35 blocks ahead (0.40..0.54s), retracting cleanly by 0.85s!
	tongue_lash = make_anim(name, "tongue_lash", 0.85, "once", {
		"root": {"position": [(0.0, Z), (0.22, (0, -0.8, 1.8)), (0.40, (0, 0.2, -2.2)), (0.58, (0, 0.0, -1.2)), (0.85, Z)]},
		"waist": {"rotation": [(0.0, Z), (0.22, (-10, 0, 0)), (0.40, (10, 0, 0)), (0.85, Z)]},
		"chest": {"rotation": [(0.0, Z), (0.22, (-18, 0, 0)), (0.40, (14, 0, 0)), (0.56, (10, 0, 0)), (0.85, Z)]},
		"skull_collar": {"rotation": [(0.0, Z), (0.22, (10, 0, 0)), (0.40, (-16, 0, 0)), (0.85, Z)]},
		"neck": {"rotation": [(0.0, Z), (0.22, (-18, 0, 0)), (0.40, (8, 0, 0)), (0.56, (6, 0, 0)), (0.85, Z)]},
		"head": {"rotation": [(0.0, Z), (0.22, (-16, 0, 0)), (0.40, (-20, -4, 0)), (0.54, (-18, 6, 0)), (0.85, Z)]},
		"jaw": {"rotation": [(0.0, Z), (0.22, (34, 0, 0)), (0.38, (54, 0, 0)), (0.58, (48, 0, 0)), (0.85, Z)]},
		"tongue_base": {
			"scale": [(0.0, UNIT_SCALE), (0.22, (1.0, 1.0, 1.15)), (0.40, (1.0, 1.0, 4.2)), (0.54, (1.0, 1.0, 3.9)), (0.74, UNIT_SCALE), (0.85, UNIT_SCALE)],
			"position": [(0.0, Z), (0.22, (0, -0.2, -0.2)), (0.40, (0, -0.5, -1.2)), (0.54, (0, -0.5, -1.0)), (0.74, Z), (0.85, Z)],
			"rotation": [(0.0, Z), (0.22, (8, 0, 0)), (0.40, (6, -8, 0)), (0.54, (4, 10, 0)), (0.85, Z)],
		},
		"tongue_mid": {
			"rotation": [(0.0, Z), (0.40, (-8, 14, 0)), (0.54, (8, -16, 0)), (0.85, Z)],
		},
		"tongue_tip": {
			"rotation": [(0.0, Z), (0.40, (10, -14, 0)), (0.54, (-10, 16, 0)), (0.85, Z)],
		},
		**mirror_rot("arm", [(0.0, Z), (0.22, (24, 12, 32)), (0.40, (36, 18, 44)), (0.56, (28, 12, 36)), (0.85, Z)]),
	})

	# 6. kill_surge (once, 0.95s): empowerment roar after claiming a kill in a row — throws arms out, rattles skull_collar, flicks tongue out of open maw!
	kill_surge = make_anim(name, "kill_surge", 0.95, "once", {
		"chest": {"rotation": [(0.0, Z), (0.24, (12, 0, 0)), (0.48, (-24, 0, 0)), (0.76, (-18, 0, 0)), (0.95, Z)]},
		"skull_collar": {"rotation": [(0.0, Z), (0.36, (-22, -8, 6)), (0.54, (-18, 8, -6)), (0.74, (-20, -6, 4)), (0.95, Z)]},
		"head": {"rotation": [(0.0, Z), (0.24, (10, 0, 0)), (0.48, (-24, -6, 0)), (0.76, (-20, 6, 0)), (0.95, Z)]},
		"jaw": {"rotation": [(0.0, Z), (0.24, (8, 0, 0)), (0.46, (48, 0, 0)), (0.76, (42, 0, 0)), (0.95, Z)]},
		"tongue_base": {
			"scale": [(0.0, UNIT_SCALE), (0.48, (1.0, 1.0, 2.5)), (0.76, (1.0, 1.0, 2.1)), (0.95, UNIT_SCALE)],
			"position": [(0.0, Z), (0.48, (0, -0.5, -0.6)), (0.76, (0, -0.5, -0.5)), (0.95, Z)],
			"rotation": [(0.0, Z), (0.48, (18, 8, 0)), (0.76, (16, -10, 0)), (0.95, Z)],
		},
		**mirror_rot("arm", [(0.0, Z), (0.24, (-24, 0, 10)), (0.48, (28, 16, 48)), (0.76, (24, 14, 42)), (0.95, Z)]),
	})

	# 7. feed (loop, 1.2s): crouched over an old grave, raking grave-earth and feeding
	feed = make_anim(name, "feed", 1.20, "loop", {
		"root": {"position": closed_loop(1.20, (0.0, (0, -4.4, 0)), (0.55, (0, -3.2, 0.5)))},
		"waist": {"rotation": closed_loop(1.20, (0.0, (22, 0, 0)), (0.55, (14, 0, 0)))},
		"chest": {"rotation": closed_loop(1.20, (0.0, (24, 0, 0)), (0.55, (10, 0, 0)))},
		"head": {"rotation": closed_loop(1.20, (0.0, (12, -8, 0)), (0.55, (-14, 8, 0)))},
		"jaw": {"rotation": closed_loop(1.20, (0.0, (28, 0, 0)), (0.55, (8, 0, 0)))},
		**mirror_rot("leg", closed_loop(1.20, (0.0, (-34, 0, 6)), (0.55, (-28, 0, 6)))),
		**mirror_rot("shin", closed_loop(1.20, (0.0, (38, 0, 0)), (0.55, (32, 0, 0)))),
		**mirror_rot("arm", closed_loop(1.20, (0.0, (-42, -12, 8)), (0.55, (-24, -8, 12)))),
	})

	return b, [idle, walk, run, attack, tongue_lash, kill_surge, feed]
