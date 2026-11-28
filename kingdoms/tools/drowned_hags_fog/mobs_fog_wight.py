"""Row B4: fog_wight and fog_wight_wisp — swamp & dark-forest mist hunter and its spectral decoy wisp.
  - fog_wight: tall (~2.32 blocks to antler-branch tips), gaunt marsh-barrow wight with a deadwood/bog-antler
    skull crown, hollow split ribcage housing a cold glowing marsh-lantern lure_core, billowing mist_mantle_right /
    mist_mantle_left vapour shrouds, long spectral raking claws, and stork-like digitigrade wading legs.
  - fog_wight_wisp: exact 1-to-1 pale-mist palette swap on the shared 64x64 UV sheet so its upper silhouette,
    antler crown, glowing lure_core, and claws match fog_wight through fog, while below the waist its legs dissolve
    into a 3-segment curling mist tail (wisp_upper -> wisp_mid -> wisp_tip)."""
from __future__ import annotations

from .common import (
	IndexedSheet,
	RigBuilder,
	Z,
	closed_loop,
	make_anim,
	mirror_rot,
	paint_claw_tip,
	paint_hollow_ribcage,
	paint_jaw_maw,
	paint_lure_orb,
	paint_mud_caked,
	paint_tattered_cloth,
	paint_wight_skull,
)


def make_fog_wight_sheet() -> tuple[IndexedSheet, dict]:
	sheet = IndexedSheet(64, 64)
	uv = {
		"head": sheet.alloc_box("head", 6, 7, 6, "bone", detail_fn=paint_wight_skull),
		"jaw": sheet.alloc_box("jaw", 5, 2, 5, "bone", detail_fn=paint_jaw_maw("bone", "hide")),
		"antler_stem": sheet.alloc_box("antler_stem", 1, 6, 2, "crown", hi=5, mid=4, side=3, back=3, lo=2, dark=1),
		"antler_tine": sheet.alloc_box("antler_tine", 1, 4, 1, "crown", detail_fn=paint_claw_tip),
		"neck": sheet.alloc_box("neck", 3, 4, 3, "hide", hi=4, mid=3, side=2, back=2, lo=1, dark=1),
		"chest": sheet.alloc_box("chest", 8, 7, 5, "hide", detail_fn=paint_hollow_ribcage),
		"lure_core": sheet.alloc_box("lure_core", 3, 3, 3, "lure", detail_fn=paint_lure_orb),
		"mantle": sheet.alloc_box("mantle", 6, 12, 1, "mist", detail_fn=paint_tattered_cloth("mist")),
		"mist_tail": sheet.alloc_box("mist_tail", 5, 10, 1, "mist", detail_fn=paint_tattered_cloth("mist")),
		"body": sheet.alloc_box("body", 5, 5, 3, "hide", hi=4, mid=3, side=2, back=2, lo=1, dark=1),
		"waist": sheet.alloc_box("waist", 5, 3, 4, "hide", hi=4, mid=3, side=2, back=2, lo=1, dark=1),
		"arm": sheet.alloc_box("arm", 2, 9, 2, "hide", hi=5, mid=4, side=3, back=3, lo=2, dark=1),
		"forearm": sheet.alloc_box("forearm", 2, 10, 2, "hide", detail_fn=paint_mud_caked("hide")),
		"hand": sheet.alloc_box("hand", 2, 3, 3, "hide", hi=4, mid=3, side=2, back=2, lo=1, dark=1),
		"claw": sheet.alloc_box("claw", 1, 4, 1, "bone", detail_fn=paint_claw_tip),
		"leg": sheet.alloc_box("leg", 2, 8, 3, "hide", hi=4, mid=3, side=3, back=2, lo=2, dark=1),
		"shin": sheet.alloc_box("shin", 2, 9, 2, "hide", detail_fn=paint_mud_caked("hide")),
		"foot": sheet.alloc_box("foot", 3, 2, 5, "hide", hi=4, mid=3, side=2, back=2, lo=1, dark=1),
		"wisp_upper": sheet.alloc_box("wisp_upper", 5, 6, 4, "mist", detail_fn=paint_tattered_cloth("mist")),
		"wisp_mid": sheet.alloc_box("wisp_mid", 4, 6, 3, "mist", detail_fn=paint_tattered_cloth("mist")),
		"wisp_tip": sheet.alloc_box("wisp_tip", 2, 5, 2, "mist", detail_fn=paint_tattered_cloth("mist")),
	}
	return sheet, uv


def _build_upper_wight(b: RigBuilder, uv: dict, hip_y: float):
	"""Builds the shared upper torso, hollow lantern-ribcage, mist-mantles, antler-crowned skull & raking claws."""
	abd_y, abd_z = hip_y + 1.6, 0.4
	b.bone("body", "waist", (0, abd_y, abd_z), (-8, 0, 0))
	b.cube("body", "spine_waist", (-2.2, abd_y - 0.2, abd_z - 1.5), (2.2, abd_y + 4.8, abd_z + 1.5), uv["body"])

	chest_y, chest_z = abd_y + 4.4, 0.0
	b.bone("chest", "body", (0, chest_y, chest_z), (-12, 0, 0))
	# Back thoracic wall + sculpted left/right rib-bars framing an open thoracic cavity so lure_core shines out at rest!
	b.cube("chest", "ribcage_back", (-4.0, chest_y - 0.2, chest_z - 0.6), (4.0, chest_y + 6.8, chest_z + 2.4), uv["chest"])
	for side, sx, mir in (("r", 1, False), ("l", -1, True)):
		px0, px1 = sorted((2.2 * sx, 4.0 * sx))
		b.cube("chest", f"pillar_{side}", (px0, chest_y - 0.2, chest_z - 2.6), (px1, chest_y + 6.8, chest_z - 0.5), uv["chest"], mirror=mir)
		for ri, ry in enumerate((0.4, 2.4, 4.6)):
			rx0, rx1 = sorted((0.9 * sx, 3.8 * sx))
			b.cube("chest", f"rib_bar_{side}_{ri}", (rx0, chest_y + ry, chest_z - 2.9), (rx1, chest_y + ry + 1.0, chest_z - 1.9), uv["claw"], mirror=mir)

	# Cold glowing marsh-light lure_core suspended visibly inside the open ribcage!
	b.bone("lure_core", "chest", (0, chest_y + 3.3, chest_z - 1.6))
	b.cube("lure_core", "orb", (-1.5, chest_y + 1.8, chest_z - 3.0), (1.5, chest_y + 4.8, chest_z - 0.2), uv["lure_core"])

	# Billowing shoulder mist-mantle wings/shrouds (flare wide on fog_draw, wrap inward on fade!)
	for side, sx, mir in (("right", 1, False), ("left", -1, True)):
		mx = 3.6 * sx
		my = chest_y + 6.0
		b.bone(f"mist_mantle_{side}", "chest", (mx, my, chest_z + 1.6), (16, -24 * sx, 16 * sx))
		x0, x1 = sorted((mx - 0.6 * sx, mx + 5.4 * sx))
		b.cube(f"mist_mantle_{side}", "veil_wing", (x0, my - 12.0, chest_z + 1.2), (x1, my + 0.4, chest_z + 2.2), uv["mantle"], mirror=mir)

	# Forward-craned neck & gaunt barrow-skull crowned with gnarled deadwood marsh-antlers
	neck_y, neck_z = chest_y + 6.4, chest_z - 1.0
	b.bone("neck", "chest", (0, neck_y, neck_z), (14, 0, 0))
	b.cube("neck", "throat", (-1.4, neck_y - 0.4, neck_z - 1.8), (1.4, neck_y + 3.2, neck_z + 1.0), uv["neck"])

	head_y, head_z = neck_y + 2.6, neck_z - 0.8
	b.bone("head", "neck", (0, head_y, head_z), (6, 0, 0))
	b.cube("head", "skull", (-2.9, head_y - 0.4, head_z - 4.8), (2.9, head_y + 6.2, head_z + 1.2), uv["head"])
	for side, sx, mir in (("r", 1, False), ("l", -1, True)):
		ax0, ax1 = sorted((2.1 * sx, 3.2 * sx))
		b.cube(
			"head", f"antler_main_{side}",
			(ax0, head_y + 4.8, head_z - 2.6), (ax1, head_y + 11.2, head_z - 0.6),
			uv["antler_stem"], mirror=mir, rotation=(-16, 0, -22 * sx), origin=(2.6 * sx, head_y + 5.2, head_z - 1.6),
		)
		tx0, tx1 = sorted((3.2 * sx, 4.1 * sx))
		b.cube(
			"head", f"antler_tine_{side}",
			(tx0, head_y + 7.4, head_z - 4.0), (tx1, head_y + 11.4, head_z - 2.8),
			uv["antler_tine"], mirror=mir, rotation=(24, 0, -34 * sx), origin=(3.5 * sx, head_y + 7.6, head_z - 3.2),
		)

	jaw_y, jaw_z = head_y - 0.3, head_z - 0.6
	b.bone("jaw", "head", (0, jaw_y, jaw_z), (-10, 0, 0))
	b.cube("jaw", "mandible", (-2.3, jaw_y - 1.8, jaw_z - 4.6), (2.3, jaw_y + 0.1, jaw_z + 0.4), uv["jaw"])

	# Long gaunt fog-raking arms & splayed spectral talons
	sh_y, sh_z = chest_y + 5.8, chest_z - 0.2
	arm_len, fore_len = 8.6, 9.4
	for side, sx, mir in (("right", 1, False), ("left", -1, True)):
		ax = 4.6 * sx
		b.bone(f"{side}_arm", "chest", (ax, sh_y, sh_z), (18, 0, 9 * sx))
		b.cube(f"{side}_arm", "humerus", (ax - 1.0, sh_y - arm_len, sh_z - 1.0), (ax + 1.0, sh_y + 0.6, sh_z + 1.0), uv["arm"], mirror=mir)

		elb_y = sh_y - arm_len
		b.bone(f"{side}_forearm", f"{side}_arm", (ax, elb_y, sh_z), (24, 0, -4 * sx))
		b.cube(f"{side}_forearm", "ulna", (ax - 0.95, elb_y - fore_len, sh_z - 1.0), (ax + 0.95, elb_y + 0.3, sh_z + 1.0), uv["forearm"], mirror=mir)

		wr_y = elb_y - fore_len
		b.bone(f"{side}_hand", f"{side}_forearm", (ax, wr_y, sh_z), (-6, 0, -4 * sx))
		b.cube(f"{side}_hand", "palm", (ax - 1.1, wr_y - 2.6, sh_z - 1.4), (ax + 1.1, wr_y + 0.2, sh_z + 1.2), uv["hand"], mirror=mir)
		for ci, (ox, oz, rx_c, rz_c) in enumerate(((-0.75, -1.1, 18, -10), (0.0, -1.3, 12, 0), (0.75, -0.9, 8, 10))):
			cx = ax + ox * sx
			cz = sh_z + oz
			x0, x1 = sorted((cx - 0.35, cx + 0.35))
			b.cube(
				f"{side}_hand", f"talon_{ci}",
				(x0, wr_y - 6.2, cz - 0.38), (x1, wr_y - 2.3, cz + 0.38),
				uv["claw"], mirror=mir, rotation=(rx_c, 0, rz_c * sx), origin=(cx, wr_y - 2.4, cz),
			)


def build_fog_wight_pair() -> tuple[tuple[RigBuilder, list[dict]], tuple[RigBuilder, list[dict]]]:
	sheet, uv = make_fog_wight_sheet()

	# =====================================================================
	# 1. FOG_WIGHT — full marsh-stalking wight (~37.2 units = 2.32 blocks tall)
	# =====================================================================
	name = "fog_wight"
	b = RigBuilder(name, sheet)
	b.bone("root", None, (0, 0, 0))

	hip_y, hip_z = 16.8, 0.5
	b.bone("waist", "root", (0, hip_y, hip_z))
	b.cube("waist", "pelvis", (-2.6, hip_y - 1.6, hip_z - 1.8), (2.6, hip_y + 1.8, hip_z + 2.0), uv["waist"])

	b.bone("mist_tail", "waist", (0, hip_y - 0.6, hip_z + 1.8), (12, 0, 0))
	b.cube("mist_tail", "vapor_train", (-2.5, hip_y - 11.0, hip_z + 1.4), (2.5, hip_y - 0.4, hip_z + 2.4), uv["mist_tail"])

	# Tall stork-like marsh-wading digitigrade legs
	for side, sx, mir in (("right", 1, False), ("left", -1, True)):
		lx = 2.1 * sx
		knee_y = 9.0
		b.bone(f"{side}_leg", "waist", (lx, hip_y - 0.6, hip_z), (16, 0, 3 * sx))
		b.cube(f"{side}_leg", "thigh", (lx - 1.1, knee_y, hip_z - 1.4), (lx + 1.1, hip_y + 0.2, hip_z + 1.4), uv["leg"], mirror=mir)

		b.bone(f"{side}_shin", f"{side}_leg", (lx, knee_y, hip_z), (-22, 0, -3 * sx))
		b.cube(f"{side}_shin", "calf", (lx - 0.95, 1.5, hip_z - 1.0), (lx + 0.95, knee_y + 0.4, hip_z + 1.0), uv["shin"], mirror=mir)

		b.bone(f"{side}_foot", f"{side}_shin", (lx, 1.5, hip_z), (6, 0, 0))
		b.cube(f"{side}_foot", "sole", (lx - 1.3, 0.0, hip_z - 3.6), (lx + 1.3, 1.6, hip_z + 1.2), uv["foot"], mirror=mir)
		b.cube(f"{side}_foot", "talon", (lx - 0.4, 0.0, hip_z - 4.8), (lx + 0.4, 1.0, hip_z - 3.4), uv["claw"], mirror=mir)

	_build_upper_wight(b, uv, hip_y)

	# Fog-wight animations (8)
	idle = make_anim(name, "idle", 3.2, "loop", {
		"root": {"position": closed_loop(3.2, (0.0, Z), (0.5, (0, 0.5, 0)))},
		"waist": {"rotation": closed_loop(3.2, (0.0, Z), (0.5, (2, 0, 2)))},
		"chest": {"rotation": closed_loop(3.2, (0.0, Z), (0.5, (-4, 0, -2)))},
		"lure_core": {"scale": closed_loop(3.2, (0.0, (1.0, 1.0, 1.0)), (0.5, (1.16, 1.16, 1.16)))},
		"mist_mantle_right": {"rotation": closed_loop(3.2, (0.0, Z), (0.5, (6, 10, 8)))},
		"mist_mantle_left": {"rotation": closed_loop(3.2, (0.0, Z), (0.5, (6, -10, -8)))},
		"mist_tail": {"rotation": closed_loop(3.2, (0.0, (0, -6, 0)), (0.5, (6, 6, 0)))},
		"head": {"rotation": closed_loop(3.2, (0.0, (0, -6, 0)), (0.4, (3, 7, 3)), (0.75, (-3, -4, -3)))},
		"jaw": {"rotation": closed_loop(3.2, (0.0, (2, 0, 0)), (0.5, (14, 0, 0)))},
		**mirror_rot("arm", closed_loop(3.2, (0.0, Z), (0.5, (-5, 0, 4)))),
	})

	walk = make_anim(name, "walk", 1.10, "loop", {
		"root": {"position": closed_loop(1.10, (0.0, Z), (0.25, (0, 0.6, 0)), (0.5, Z), (0.75, (0, 0.6, 0)))},
		"waist": {"rotation": closed_loop(1.10, (0.0, (3, -6, -2)), (0.5, (3, 6, 2)))},
		"chest": {"rotation": closed_loop(1.10, (0.0, (5, 7, 2)), (0.5, (5, -7, -2)))},
		"mist_mantle_right": {"rotation": closed_loop(1.10, (0.0, (10, 6, 6)), (0.5, (16, -4, 10)))},
		"mist_mantle_left": {"rotation": closed_loop(1.10, (0.0, (16, 4, -10)), (0.5, (10, -6, -6)))},
		"mist_tail": {"rotation": closed_loop(1.10, (0.0, (10, -8, 0)), (0.5, (14, 8, 0)))},
		"head": {"rotation": closed_loop(1.10, (0.0, (-4, -5, 0)), (0.5, (-4, 5, 0)))},
		"right_leg": {"rotation": closed_loop(1.10, (0.0, (-26, 0, 0)), (0.5, (22, 0, 0)))},
		"left_leg": {"rotation": closed_loop(1.10, (0.0, (22, 0, 0)), (0.5, (-26, 0, 0)))},
		"right_shin": {"rotation": closed_loop(1.10, (0.0, (10, 0, 0)), (0.5, (26, 0, 0)))},
		"left_shin": {"rotation": closed_loop(1.10, (0.0, (26, 0, 0)), (0.5, (10, 0, 0)))},
		"right_arm": {"rotation": closed_loop(1.10, (0.0, (16, 0, 5)), (0.5, (-22, 0, 4)))},
		"left_arm": {"rotation": closed_loop(1.10, (0.0, (-22, 0, -4)), (0.5, (16, 0, -5)))},
	})

	run = make_anim(name, "run", 0.65, "loop", {
		"root": {"position": closed_loop(0.65, (0.0, Z), (0.25, (0, 1.5, 0)), (0.5, Z), (0.75, (0, 1.5, 0)))},
		"waist": {"rotation": closed_loop(0.65, (0.0, (10, -8, -4)), (0.5, (10, 8, 4)))},
		"chest": {"rotation": closed_loop(0.65, (0.0, (14, 9, 4)), (0.5, (14, -9, -4)))},
		"mist_mantle_right": {"rotation": closed_loop(0.65, (0.0, (32, 12, 16)), (0.5, (24, 8, 12)))},
		"mist_mantle_left": {"rotation": closed_loop(0.65, (0.0, (24, -8, -12)), (0.5, (32, -12, -16)))},
		"mist_tail": {"rotation": closed_loop(0.65, (0.0, (30, -8, 0)), (0.5, (30, 8, 0)))},
		"head": {"rotation": closed_loop(0.65, (0.0, (-14, -4, 0)), (0.5, (-14, 4, 0)))},
		"jaw": {"rotation": closed_loop(0.65, (0.0, (16, 0, 0)), (0.5, (26, 0, 0)))},
		"right_leg": {"rotation": closed_loop(0.65, (0.0, (-38, 0, 0)), (0.5, (32, 0, 0)))},
		"left_leg": {"rotation": closed_loop(0.65, (0.0, (32, 0, 0)), (0.5, (-38, 0, 0)))},
		**mirror_rot("arm", closed_loop(0.65, (0.0, (-52, 0, 14)), (0.5, (-34, 0, 10)))),
	})

	attack = make_anim(name, "attack", 0.60, "once", {
		"chest": {"rotation": [(0.0, Z), (0.18, (-14, 22, 0)), (0.36, (18, -24, 0)), (0.60, Z)]},
		"mist_mantle_right": {"rotation": [(0.0, Z), (0.18, (8, 28, 24)), (0.36, (18, -14, 8)), (0.60, Z)]},
		"mist_mantle_left": {"rotation": [(0.0, Z), (0.18, (14, 10, -8)), (0.36, (8, -28, -24)), (0.60, Z)]},
		"head": {"rotation": [(0.0, Z), (0.18, (-10, -14, 0)), (0.36, (8, 14, 0)), (0.60, Z)]},
		"jaw": {"rotation": [(0.0, Z), (0.18, (30, 0, 0)), (0.36, (-4, 0, 0)), (0.60, Z)]},
		"right_arm": {"rotation": [(0.0, Z), (0.18, (-104, 18, 30)), (0.36, (-18, -32, -16)), (0.60, Z)]},
		"right_forearm": {"rotation": [(0.0, Z), (0.18, (-24, 0, 0)), (0.36, (10, 0, -10)), (0.60, Z)]},
		"left_arm": {"rotation": [(0.0, Z), (0.18, (22, -10, -12)), (0.36, (-72, 18, -24)), (0.60, Z)]},
	})

	# 5. fog_draw (once, 1.2s): raises long arms wide and billows mist_mantle shrouds out (0.48s),
	# then sweeps arms inward to pull thick fog around itself while lure_core flares (0.88s)!
	fog_draw = make_anim(name, "fog_draw", 1.20, "once", {
		"root": {"position": [(0.0, Z), (0.48, (0, 1.8, 0)), (0.88, (0, -0.6, 0)), (1.20, Z)]},
		"chest": {"rotation": [(0.0, Z), (0.48, (-20, 0, 0)), (0.88, (14, 0, 0)), (1.20, Z)]},
		"lure_core": {"scale": [(0.0, (1, 1, 1)), (0.48, (1.45, 1.45, 1.45)), (0.88, (1.25, 1.25, 1.25)), (1.20, (1, 1, 1))]},
		"mist_mantle_right": {"rotation": [(0.0, Z), (0.48, (-8, 38, 42)), (0.88, (-14, -44, -18)), (1.20, Z)]},
		"mist_mantle_left": {"rotation": [(0.0, Z), (0.48, (-8, -38, -42)), (0.88, (-14, 44, 18)), (1.20, Z)]},
		"head": {"rotation": [(0.0, Z), (0.48, (-24, 0, 0)), (0.88, (12, 0, 0)), (1.20, Z)]},
		"jaw": {"rotation": [(0.0, Z), (0.48, (34, 0, 0)), (0.88, (8, 0, 0)), (1.20, Z)]},
		"right_arm": {"rotation": [(0.0, Z), (0.48, (-76, 28, 62)), (0.88, (-58, -38, -14)), (1.20, Z)]},
		"left_arm": {"rotation": [(0.0, Z), (0.48, (-76, -28, -62)), (0.88, (-58, 38, 14)), (1.20, Z)]},
		**mirror_rot("forearm", [(0.0, Z), (0.48, (-12, 0, 0)), (0.88, (-42, 0, 14)), (1.20, Z)]),
	})

	# 6. fade (once, 0.85s): wraps mist_mantle & arms tight across chest and dissolves low into the marsh fog!
	fade = make_anim(name, "fade", 0.85, "once", {
		"root": {
			"position": [(0.0, Z), (0.36, (0, -2.4, 0.8)), (0.70, (0, -8.5, 1.6)), (0.85, (0, -11.0, 2.0))],
			"scale": [(0.0, (1, 1, 1)), (0.45, (0.92, 0.96, 0.92)), (0.75, (0.45, 0.65, 0.45)), (0.85, (0.15, 0.30, 0.15))],
		},
		"chest": {"rotation": [(0.0, Z), (0.40, (22, 0, 0)), (0.85, (30, 0, 0))]},
		"head": {"rotation": [(0.0, Z), (0.40, (20, 0, 0)), (0.85, (28, 0, 0))]},
		"mist_mantle_right": {"rotation": [(0.0, Z), (0.40, (-18, -56, -22)), (0.85, (-22, -68, -28))]},
		"mist_mantle_left": {"rotation": [(0.0, Z), (0.40, (-18, 56, 22)), (0.85, (-22, 68, 28))]},
		"right_arm": {"rotation": [(0.0, Z), (0.40, (-54, -42, -18)), (0.85, (-62, -48, -22))]},
		"left_arm": {"rotation": [(0.0, Z), (0.40, (-54, 42, 18)), (0.85, (-62, 48, 22))]},
	})

	# 7. emerge (once, 0.75s): condenses up out of the fog behind prey, flinging mist-mantle & talons wide!
	emerge = make_anim(name, "emerge", 0.75, "once", {
		"root": {
			"position": [(0.0, (0, -9.0, 1.6)), (0.34, (0, 1.4, -0.6)), (0.75, Z)],
			"scale": [(0.0, (0.35, 0.50, 0.35)), (0.34, (1.04, 1.04, 1.04)), (0.75, (1, 1, 1))],
		},
		"chest": {"rotation": [(0.0, (24, 0, 0)), (0.34, (-16, 0, 0)), (0.75, Z)]},
		"mist_mantle_right": {"rotation": [(0.0, (-18, -50, -20)), (0.34, (-6, 36, 38)), (0.75, Z)]},
		"mist_mantle_left": {"rotation": [(0.0, (-18, 50, 20)), (0.34, (-6, -36, -38)), (0.75, Z)]},
		"head": {"rotation": [(0.0, (20, 0, 0)), (0.34, (-14, 0, 0)), (0.75, Z)]},
		"jaw": {"rotation": [(0.0, (4, 0, 0)), (0.34, (32, 0, 0)), (0.75, Z)]},
		**mirror_rot("arm", [(0.0, (-50, -34, -14)), (0.34, (-68, 24, 46)), (0.75, Z)]),
	})

	# 8. decoy_wisp (once, 0.95s): reaches left claw into glowing lure_core (0.30s) and sweeps right arm forward
	# to tear off and project a spectral decoy wisp of itself into the fog (0.56s)!
	decoy_wisp = make_anim(name, "decoy_wisp", 0.95, "once", {
		"chest": {"rotation": [(0.0, Z), (0.30, (-8, 24, 0)), (0.56, (14, -28, 0)), (0.95, Z)]},
		"lure_core": {
			"scale": [(0.0, (1, 1, 1)), (0.30, (1.55, 1.55, 1.55)), (0.56, (1.30, 1.30, 1.30)), (0.95, (1, 1, 1))],
			"position": [(0.0, Z), (0.30, (0, 0, -1.2)), (0.56, (0, 0.4, -2.0)), (0.95, Z)],
		},
		"mist_mantle_right": {"rotation": [(0.0, Z), (0.30, (0, 26, 22)), (0.56, (-10, 42, 34)), (0.95, Z)]},
		"head": {"rotation": [(0.0, Z), (0.30, (-6, -18, 0)), (0.56, (-4, 16, 0)), (0.95, Z)]},
		"jaw": {"rotation": [(0.0, Z), (0.30, (18, 0, 0)), (0.56, (30, 0, 0)), (0.95, Z)]},
		"left_arm": {"rotation": [(0.0, Z), (0.30, (-52, 34, 12)), (0.56, (-44, 28, 8)), (0.95, Z)]},
		"left_forearm": {"rotation": [(0.0, Z), (0.30, (-46, 0, 14)), (0.56, (-38, 0, 10)), (0.95, Z)]},
		"right_arm": {"rotation": [(0.0, Z), (0.30, (-48, -32, -10)), (0.56, (-86, 20, 32)), (0.95, Z)]},
		"right_forearm": {"rotation": [(0.0, Z), (0.30, (-44, 0, -10)), (0.56, (-8, 0, 0)), (0.95, Z)]},
	})

	wight_anims = [idle, walk, run, attack, fog_draw, fade, emerge, decoy_wisp]

	# =====================================================================
	# 2. FOG_WIGHT_WISP — spectral decoy wisp of itself (shared UV sheet!)
	# =====================================================================
	wname = "fog_wight_wisp"
	wb = RigBuilder(wname, sheet)
	wb.bone("root", None, (0, 0, 0))

	wb.bone("waist", "root", (0, hip_y, hip_z))
	wb.cube("waist", "pelvis_mist", (-2.5, hip_y - 1.4, hip_z - 1.6), (2.5, hip_y + 1.8, hip_z + 1.8), uv["waist"])

	# Tapering 3-segment hovering vapour tail replacing the real wight's solid legs!
	wu_y = hip_y - 1.0
	wb.bone("wisp_upper", "waist", (0, wu_y, hip_z), (10, 0, 0))
	wb.cube("wisp_upper", "plume_0", (-2.4, wu_y - 5.6, hip_z - 1.8), (2.4, wu_y + 0.2, hip_z + 2.0), uv["wisp_upper"])

	wm_y = wu_y - 5.2
	wb.bone("wisp_mid", "wisp_upper", (0, wm_y, hip_z + 0.4), (16, 0, 0))
	wb.cube("wisp_mid", "plume_1", (-1.8, wm_y - 5.6, hip_z - 1.0), (1.8, wm_y + 0.2, hip_z + 2.0), uv["wisp_mid"])

	wt_y = wm_y - 5.2
	wb.bone("wisp_tip", "wisp_mid", (0, wt_y, hip_z + 0.8), (18, 0, 0))
	wb.cube("wisp_tip", "plume_2", (-1.0, wt_y - 4.6, hip_z - 0.2), (1.0, wt_y + 0.2, hip_z + 1.8), uv["wisp_tip"])

	_build_upper_wight(wb, uv, hip_y)

	# Decoy wisp animations (6)
	w_idle = make_anim(wname, "idle", 2.8, "loop", {
		"root": {"position": closed_loop(2.8, (0.0, (0, 0.4, 0)), (0.5, (0, 1.8, 0)))},
		"chest": {"rotation": closed_loop(2.8, (0.0, Z), (0.5, (-4, 0, 0)))},
		"lure_core": {"scale": closed_loop(2.8, (0.0, (1.05, 1.05, 1.05)), (0.5, (1.30, 1.30, 1.30)))},
		"wisp_upper": {"rotation": closed_loop(2.8, (0.0, (0, -8, -4)), (0.5, (4, 8, 4)))},
		"wisp_mid": {"rotation": closed_loop(2.8, (0.0, (0, 14, 6)), (0.5, (6, -14, -6)))},
		"wisp_tip": {"rotation": closed_loop(2.8, (0.0, (0, -20, -8)), (0.5, (8, 20, 8)))},
		"mist_mantle_right": {"rotation": closed_loop(2.8, (0.0, Z), (0.5, (8, 14, 10)))},
		"mist_mantle_left": {"rotation": closed_loop(2.8, (0.0, Z), (0.5, (8, -14, -10)))},
		"head": {"rotation": closed_loop(2.8, (0.0, (0, -6, 0)), (0.5, (4, 6, 0)))},
		**mirror_rot("arm", closed_loop(2.8, (0.0, Z), (0.5, (-8, 0, 6)))),
	})

	w_walk = make_anim(wname, "walk", 1.0, "loop", {
		"root": {"position": closed_loop(1.0, (0.0, (0, 0.6, 0)), (0.5, (0, 1.8, 0)))},
		"waist": {"rotation": closed_loop(1.0, (0.0, (10, -5, 0)), (0.5, (10, 5, 0)))},
		"chest": {"rotation": closed_loop(1.0, (0.0, (6, 6, 0)), (0.5, (6, -6, 0)))},
		"wisp_upper": {"rotation": closed_loop(1.0, (0.0, (14, -12, 0)), (0.5, (14, 12, 0)))},
		"wisp_mid": {"rotation": closed_loop(1.0, (0.0, (16, 18, 0)), (0.5, (16, -18, 0)))},
		"wisp_tip": {"rotation": closed_loop(1.0, (0.0, (18, -24, 0)), (0.5, (18, 24, 0)))},
		**mirror_rot("arm", closed_loop(1.0, (0.0, (-22, 0, 8)), (0.5, (-12, 0, 4)))),
	})

	w_run = make_anim(wname, "run", 0.60, "loop", {
		"root": {"position": closed_loop(0.60, (0.0, (0, 0.8, 0)), (0.5, (0, 2.2, 0)))},
		"waist": {"rotation": closed_loop(0.60, (0.0, (22, -6, 0)), (0.5, (22, 6, 0)))},
		"chest": {"rotation": closed_loop(0.60, (0.0, (14, 6, 0)), (0.5, (14, -6, 0)))},
		"wisp_upper": {"rotation": closed_loop(0.60, (0.0, (24, -16, 0)), (0.5, (24, 16, 0)))},
		"wisp_mid": {"rotation": closed_loop(0.60, (0.0, (22, 22, 0)), (0.5, (22, -22, 0)))},
		"head": {"rotation": closed_loop(0.60, (0.0, (-18, 0, 0)), (0.5, (-14, 0, 0)))},
		"jaw": {"rotation": closed_loop(0.60, (0.0, (18, 0, 0)), (0.5, (28, 0, 0)))},
		**mirror_rot("arm", closed_loop(0.60, (0.0, (-64, 8, 18)), (0.5, (-50, 4, 12)))),
	})

	w_attack = make_anim(wname, "attack", 0.55, "once", {
		"chest": {"rotation": [(0.0, Z), (0.18, (-12, 18, 0)), (0.34, (18, -20, 0)), (0.55, Z)]},
		"jaw": {"rotation": [(0.0, Z), (0.18, (30, 0, 0)), (0.34, (-4, 0, 0)), (0.55, Z)]},
		**mirror_rot("arm", [(0.0, Z), (0.18, (-92, 16, 28)), (0.34, (-20, -24, -12)), (0.55, Z)]),
	})

	# 5. lure (loop, 1.4s): beckons travellers off the road in the fog, glowing lure_core pulsing & right hand waving!
	w_lure = make_anim(wname, "lure", 1.40, "loop", {
		"root": {"position": closed_loop(1.40, (0.0, (0, 0.8, 0)), (0.5, (0, 2.2, 0)))},
		"lure_core": {"scale": closed_loop(1.40, (0.0, (1.15, 1.15, 1.15)), (0.5, (1.50, 1.50, 1.50)))},
		"head": {"rotation": closed_loop(1.40, (0.0, (4, -8, -6)), (0.5, (-4, 8, 6)))},
		"right_arm": {"rotation": closed_loop(1.40, (0.0, (-72, -10, 14)), (0.5, (-84, 12, 22)))},
		"right_forearm": {"rotation": closed_loop(1.40, (0.0, (-28, 0, 0)), (0.5, (-52, 0, 12)))},
		"right_hand": {"rotation": closed_loop(1.40, (0.0, (-18, 0, 0)), (0.5, (22, 0, 0)))},
		"wisp_mid": {"rotation": closed_loop(1.40, (0.0, (8, -16, 0)), (0.5, (8, 16, 0)))},
	})

	# 6. fade (once, 0.65s): decoy wisp bursts upward & vanishes into thin vapour when struck or expired!
	w_fade = make_anim(wname, "fade", 0.65, "once", {
		"root": {
			"position": [(0.0, Z), (0.30, (0, 3.6, 0.8)), (0.65, (0, 7.2, 1.6))],
			"scale": [(0.0, (1, 1, 1)), (0.30, (1.12, 1.18, 1.12)), (0.65, (0.08, 0.25, 0.08))],
		},
		"mist_mantle_right": {"rotation": [(0.0, Z), (0.35, (-12, 44, 48)), (0.65, (-18, 60, 58))]},
		"mist_mantle_left": {"rotation": [(0.0, Z), (0.35, (-12, -44, -48)), (0.65, (-18, -60, -58))]},
		**mirror_rot("arm", [(0.0, Z), (0.35, (-68, 22, 52)), (0.65, (-84, 30, 64))]),
	})

	return (b, wight_anims), (wb, [w_idle, w_walk, w_run, w_attack, w_lure, w_fade])
