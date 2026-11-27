"""Rows B6 (grave-brute miniboss) and B7 (bone-burrower & heavy rot-sick burrower)."""
from __future__ import annotations

from .common import (
	IndexedSheet,
	RigBuilder,
	Z,
	closed_loop,
	make_anim,
	mirror_rot,
	paint_claw_tip,
	paint_feral_skull,
	paint_filth_caked,
	paint_jaw_maw,
	paint_osteoderm_shell,
	paint_ribbed_torso,
	paint_swollen_belly,
)


# =====================================================================
# B6: GRAVE-BRUTE (128x128 sheet at exact 16px/block vanilla density)
# =====================================================================

def build_grave_brute():
	name = "grave_brute"
	sheet = IndexedSheet(128, 128)
	uv = {
		"chest": sheet.alloc_box("chest", 16, 12, 11, "hide", detail_fn=paint_ribbed_torso),
		"hump": sheet.alloc_box("hump", 12, 5, 9, "hide", hi=5, mid=4, side=3, back=3, lo=2, dark=1),
		"body": sheet.alloc_box("body", 12, 8, 9, "sinew", hi=4, mid=3, side=3, back=2, lo=2, dark=1),
		"waist": sheet.alloc_box("waist", 13, 6, 9, "filth", hi=4, mid=3, side=2, back=2, lo=1, dark=1),
		"pauldron": sheet.alloc_box("pauldron", 7, 6, 9, "bone", detail_fn=paint_osteoderm_shell),
		"head": sheet.alloc_box("head", 9, 8, 9, "hide", detail_fn=paint_feral_skull(eye_row=4)),
		"ram_plate": sheet.alloc_box("ram_plate", 11, 4, 10, "bone", detail_fn=paint_osteoderm_shell),
		"horn": sheet.alloc_box("horn", 2, 3, 7, "bone", detail_fn=paint_claw_tip),
		"jaw": sheet.alloc_box("jaw", 8, 4, 8, "hide", detail_fn=paint_jaw_maw),
		"tusk": sheet.alloc_box("tusk", 2, 4, 2, "bone", detail_fn=paint_claw_tip),
		"neck": sheet.alloc_box("neck", 7, 6, 7, "sinew", hi=4, mid=3, side=3, back=2, lo=2, dark=1),
		"arm": sheet.alloc_box("arm", 5, 11, 6, "hide", hi=5, mid=4, side=3, back=3, lo=2, dark=1),
		"forearm": sheet.alloc_box("forearm", 6, 12, 7, "hide", detail_fn=paint_filth_caked("hide")),
		"bracer": sheet.alloc_box("bracer", 2, 8, 3, "bone", detail_fn=paint_osteoderm_shell),
		"fist": sheet.alloc_box("fist", 7, 6, 7, "filth", hi=4, mid=3, side=2, back=2, lo=1, dark=1),
		"knuckle": sheet.alloc_box("knuckle", 2, 3, 2, "bone", detail_fn=paint_claw_tip),
		"leg": sheet.alloc_box("leg", 6, 9, 7, "hide", hi=4, mid=3, side=3, back=2, lo=2, dark=1),
		"shin": sheet.alloc_box("shin", 6, 9, 6, "hide", detail_fn=paint_filth_caked("hide")),
		"foot": sheet.alloc_box("foot", 6, 3, 8, "filth", hi=4, mid=3, side=2, back=2, lo=1, dark=1),
	}

	b = RigBuilder(name, sheet)
	b.bone("root", None, (0, 0, 0))

	# Heavy elephantine pillar legs & wide pelvis (standing ~42 units = 2.63 blocks tall!)
	hip_y = 16.5
	b.bone("waist", "root", (0, hip_y, 0.5))
	b.cube("waist", "pelvis", (-6.5, hip_y - 3.0, -4.0), (6.5, hip_y + 3.0, 5.0), uv["waist"])
	b.cube("waist", "hip_horn_r", (6.0, hip_y + 0.2, -2.5), (8.0, hip_y + 3.5, 1.5), uv["knuckle"])
	b.cube("waist", "hip_horn_l", (-8.0, hip_y + 0.2, -2.5), (-6.0, hip_y + 3.5, 1.5), uv["knuckle"], mirror=True)

	for side, sx, mir in (("right", 1, False), ("left", -1, True)):
		lx = 4.2 * sx
		b.bone(f"{side}_leg", "waist", (lx, hip_y - 1.5, 0.5), (8, 0, 4 * sx))
		b.cube(f"{side}_leg", "thigh", (lx - 3.0, 7.5, -3.0), (lx + 3.0, hip_y - 0.5, 4.0), uv["leg"], mirror=mir)
		b.cube(f"{side}_leg", "knee_plate", (lx - 2.0, 6.8, -4.2), (lx + 2.0, 10.2, -2.4), uv["knuckle"], mirror=mir)

		b.bone(f"{side}_shin", f"{side}_leg", (lx, 8.2, 0.5), (-12, 0, -4 * sx))
		b.cube(f"{side}_shin", "calf", (lx - 2.8, 2.2, -2.6), (lx + 2.8, 8.6, 3.6), uv["shin"], mirror=mir)

		b.bone(f"{side}_foot", f"{side}_shin", (lx, 2.5, 0.5), (4, 0, 0))
		b.cube(f"{side}_foot", "stomp_pad", (lx - 3.2, 0.0, -5.2), (lx + 3.2, 2.8, 3.2), uv["foot"], mirror=mir)

	# Thick muscular trunk rising into colossal sloped siege-shoulders
	b.bone("body", "waist", (0, 18.5, 0.5), (-6, 0, 0))
	b.cube("body", "gut", (-6.0, 18.0, -3.8), (6.0, 26.0, 4.8), uv["body"])

	chest_y = 25.0
	b.bone("chest", "body", (0, chest_y, 0.0), (-14, 0, 0))
	b.cube("chest", "thorax", (-8.2, chest_y - 0.5, -5.8), (8.2, chest_y + 11.5, 5.2), uv["chest"])
	# Towering nuchal muscle/bone hump behind the head (classic charging bison/rhino silhouette!)
	b.cube("chest", "nuchal_hump", (-6.0, chest_y + 10.2, -3.5), (6.0, chest_y + 15.2, 5.0), uv["hump"], rotation=(-8, 0, 0), origin=(0, chest_y + 11.0, 1.0))
	for vi, (vy, vz) in enumerate(((28.0, 5.0), (31.5, 4.8), (35.0, 4.2))):
		b.cube("chest", f"dorsal_ridge_{vi}", (-1.2, vy, vz), (1.2, vy + 2.8, vz + 2.6), uv["knuckle"], rotation=(-22, 0, 0), origin=(0, vy, vz))

	# Ossified bone-plate pauldrons over both broad shoulders
	for side, sx, mir in (("right", 1, False), ("left", -1, True)):
		px = 8.4 * sx
		py = chest_y + 9.5
		b.bone(f"{side}_pauldron", "chest", (px, py, -0.5), (0, 0, -14 * sx))
		x0, x1 = sorted((px - 2.2 * sx, px + 4.8 * sx))
		b.cube(f"{side}_pauldron", "plate", (x0, py - 3.2, -5.0), (x1, py + 2.8, 4.0), uv["pauldron"], mirror=mir)
		sx0, sx1 = sorted((px + 1.2 * sx, px + 3.4 * sx))
		b.cube(
			f"{side}_pauldron", "ridge_horn",
			(sx0, py + 2.0, -3.2), (sx1, py + 5.4, -0.4),
			uv["tusk"], mirror=mir, rotation=(-18, 0, 22 * sx), origin=(px + 2.0 * sx, py + 2.4, -1.8),
		)

	# Forward-set charging neck & armored battering-ram head!
	neck_y, neck_z = chest_y + 8.2, -4.8
	b.bone("neck", "chest", (0, neck_y, neck_z), (16, 0, 0))
	b.cube("neck", "bull_neck", (-3.6, neck_y - 3.0, neck_z - 5.2), (3.6, neck_y + 3.2, neck_z + 1.8), uv["neck"])

	head_y, head_z = neck_y - 0.5, neck_z - 4.4
	b.bone("head", "neck", (0, head_y, head_z), (6, 0, 0))
	b.cube("head", "cranium", (-4.5, head_y - 3.8, head_z - 8.2), (4.5, head_y + 4.2, head_z + 1.0), uv["head"])
	# Thick sloped cranial battering-ram dome (angled forward so lowering the head presents a solid stone-smashing wall!)
	b.cube(
		"head", "ram_plate",
		(-5.4, head_y + 2.2, head_z - 9.4), (5.4, head_y + 6.2, head_z + 0.6),
		uv["ram_plate"], rotation=(18, 0, 0), origin=(0, head_y + 4.0, head_z - 5.0),
	)
	# Twin heavy forward-swept ramming brow-horns
	for side, sx, mir in (("r", 1, False), ("l", -1, True)):
		hx0, hx1 = sorted((3.6 * sx, 6.0 * sx))
		b.cube(
			"head", f"ram_horn_{side}",
			(hx0, head_y + 1.6, head_z - 13.0), (hx1, head_y + 4.4, head_z - 5.8),
			uv["horn"], mirror=mir, rotation=(14, -16 * sx, -10 * sx), origin=(4.6 * sx, head_y + 3.0, head_z - 6.5),
		)

	# Heavy underbite jaw with thick upturned crushing tusks
	jaw_y, jaw_z = head_y - 3.2, head_z - 1.2
	b.bone("jaw", "head", (0, jaw_y, jaw_z), (-8, 0, 0))
	b.cube("jaw", "mandible", (-4.2, jaw_y - 3.0, jaw_z - 7.8), (4.2, jaw_y + 0.6, jaw_z + 0.6), uv["jaw"])
	for side, sx, mir in (("r", 1, False), ("l", -1, True)):
		tx0, tx1 = sorted((2.4 * sx, 4.1 * sx))
		b.cube(
			"jaw", f"tusk_{side}",
			(tx0, jaw_y + 0.2, jaw_z - 8.0), (tx1, jaw_y + 4.0, jaw_z - 6.0),
			uv["tusk"], mirror=mir, rotation=(-12, 0, 8 * sx), origin=(3.2 * sx, jaw_y + 0.4, jaw_z - 7.0),
		)

	# Massive gorilla-proportioned siege arms with bone-bracer ridges & stone-crushing fists
	sh_y, sh_z = chest_y + 8.5, -1.0
	for side, sx, mir in (("right", 1, False), ("left", -1, True)):
		ax = 10.6 * sx
		b.bone(f"{side}_arm", "chest", (ax, sh_y, sh_z), (20, 0, 10 * sx))
		b.cube(f"{side}_arm", "bicep", (ax - 2.6, sh_y - 11.0, sh_z - 3.0), (ax + 2.6, sh_y + 1.5, sh_z + 3.0), uv["arm"], mirror=mir)

		elb_y = sh_y - 10.5
		b.bone(f"{side}_forearm", f"{side}_arm", (ax + 0.4 * sx, elb_y, sh_z), (22, 0, -6 * sx))
		fx = ax + 0.4 * sx
		b.cube(f"{side}_forearm", "club_ulna", (fx - 3.2, elb_y - 12.0, sh_z - 3.5), (fx + 3.2, elb_y + 0.8, sh_z + 3.5), uv["forearm"], mirror=mir)
		bx0, bx1 = sorted((fx + 2.6 * sx, fx + 4.4 * sx))
		b.cube(f"{side}_forearm", "bone_bracer", (bx0, elb_y - 9.5, sh_z - 1.8), (bx1, elb_y - 1.5, sh_z + 1.8), uv["bracer"], mirror=mir)

		wr_y = elb_y - 11.6
		b.bone(f"{side}_fist", f"{side}_forearm", (fx, wr_y, sh_z), (-10, 0, -4 * sx))
		b.cube(f"{side}_fist", "maul_fist", (fx - 3.6, wr_y - 5.6, sh_z - 3.8), (fx + 3.6, wr_y + 0.4, sh_z + 3.4), uv["fist"], mirror=mir)
		for ki, kz in enumerate((-2.6, 0.0, 2.2)):
			kx0, kx1 = sorted((fx + 2.8 * sx, fx + 4.6 * sx))
			b.cube(f"{side}_fist", f"knuckle_{ki}", (kx0, wr_y - 5.2, sh_z + kz - 0.9), (kx1, wr_y - 2.4, sh_z + kz + 0.9), uv["knuckle"], mirror=mir)

	# Animations
	idle = make_anim(name, "idle", 3.2, "loop", {
		"waist": {"rotation": closed_loop(3.2, (0.0, Z), (0.5, (2, 0, 0)))},
		"chest": {"rotation": closed_loop(3.2, (0.0, Z), (0.5, (-4, 0, 0)))},
		"head": {"rotation": closed_loop(3.2, (0.0, (0, -6, 0)), (0.4, (4, 7, 2)), (0.75, (-3, -4, -2)))},
		"jaw": {"rotation": closed_loop(3.2, (0.0, (2, 0, 0)), (0.5, (14, 0, 0)))},
		**mirror_rot("pauldron", closed_loop(3.2, (0.0, Z), (0.5, (0, 0, 4)))),
		**mirror_rot("arm", closed_loop(3.2, (0.0, Z), (0.5, (-5, 0, 4)))),
		**mirror_rot("forearm", closed_loop(3.2, (0.0, Z), (0.5, (-8, 0, 0)))),
	})

	walk = make_anim(name, "walk", 1.1, "loop", {
		"root": {"position": closed_loop(1.1, (0.0, (0, 0, 0)), (0.25, (0, -1.0, 0)), (0.5, (0, 0, 0)), (0.75, (0, -1.0, 0)))},
		"waist": {"rotation": closed_loop(1.1, (0.0, (3, -7, -3)), (0.5, (3, 7, 3)))},
		"chest": {"rotation": closed_loop(1.1, (0.0, (5, 9, 4)), (0.5, (5, -9, -4)))},
		"head": {"rotation": closed_loop(1.1, (0.0, (-3, -6, -2)), (0.5, (-3, 6, 2)))},
		"jaw": {"rotation": closed_loop(1.1, (0.0, (4, 0, 0)), (0.5, (12, 0, 0)))},
		"right_leg": {"rotation": closed_loop(1.1, (0.0, (-28, 0, 0)), (0.5, (24, 0, 0)))},
		"left_leg": {"rotation": closed_loop(1.1, (0.0, (24, 0, 0)), (0.5, (-28, 0, 0)))},
		"right_shin": {"rotation": closed_loop(1.1, (0.0, (8, 0, 0)), (0.25, (4, 0, 0)), (0.5, (14, 0, 0)), (0.75, (28, 0, 0)))},
		"left_shin": {"rotation": closed_loop(1.1, (0.0, (14, 0, 0)), (0.25, (28, 0, 0)), (0.5, (8, 0, 0)), (0.75, (4, 0, 0)))},
		"right_arm": {"rotation": closed_loop(1.1, (0.0, (24, 0, 6)), (0.5, (-30, 0, 4)))},
		"left_arm": {"rotation": closed_loop(1.1, (0.0, (-30, 0, -4)), (0.5, (24, 0, -6)))},
		"right_forearm": {"rotation": closed_loop(1.1, (0.0, (-8, 0, 0)), (0.5, (-22, 0, 0)))},
		"left_forearm": {"rotation": closed_loop(1.1, (0.0, (-22, 0, 0)), (0.5, (-8, 0, 0)))},
	})

	run = make_anim(name, "run", 0.7, "loop", {
		"root": {"position": closed_loop(0.7, (0.0, (0, 0, 0)), (0.25, (0, 1.4, 0)), (0.5, (0, 0, 0)), (0.75, (0, 1.4, 0)))},
		"waist": {"rotation": closed_loop(0.7, (0.0, (8, -9, -3)), (0.5, (8, 9, 3)))},
		"chest": {"rotation": closed_loop(0.7, (0.0, (12, 11, 4)), (0.5, (12, -11, -4)))},
		"head": {"rotation": closed_loop(0.7, (0.0, (-8, -6, 0)), (0.5, (-8, 6, 0)))},
		"jaw": {"rotation": closed_loop(0.7, (0.0, (14, 0, 0)), (0.5, (22, 0, 0)))},
		"right_leg": {"rotation": closed_loop(0.7, (0.0, (-38, 0, 0)), (0.5, (34, 0, 0)))},
		"left_leg": {"rotation": closed_loop(0.7, (0.0, (34, 0, 0)), (0.5, (-38, 0, 0)))},
		"right_arm": {"rotation": closed_loop(0.7, (0.0, (34, 0, 10)), (0.5, (-44, 0, 8)))},
		"left_arm": {"rotation": closed_loop(0.7, (0.0, (-44, 0, -8)), (0.5, (34, 0, -10)))},
		**mirror_rot("forearm", closed_loop(0.7, (0.0, (-20, 0, 0)), (0.5, (-30, 0, 0)))),
	})

	attack = make_anim(name, "attack", 0.8, "once", {
		"body": {"rotation": [(0.0, Z), (0.25, (-8, 22, 0)), (0.46, (14, -28, 0)), (0.8, Z)]},
		"chest": {"rotation": [(0.0, Z), (0.25, (-14, 30, -5)), (0.46, (22, -34, 6)), (0.8, Z)]},
		"head": {"rotation": [(0.0, Z), (0.25, (-8, -18, 0)), (0.46, (6, 20, 0)), (0.8, Z)]},
		"jaw": {"rotation": [(0.0, Z), (0.25, (20, 0, 0)), (0.46, (34, 0, 0)), (0.65, (6, 0, 0)), (0.8, Z)]},
		"right_arm": {"rotation": [(0.0, Z), (0.25, (-115, 24, 38)), (0.46, (-18, -38, -16)), (0.8, Z)]},
		"right_forearm": {"rotation": [(0.0, Z), (0.25, (-28, 0, 0)), (0.46, (10, 0, -12)), (0.8, Z)]},
		"left_arm": {"rotation": [(0.0, Z), (0.25, (24, -10, -16)), (0.46, (-52, 16, -18)), (0.8, Z)]},
	})

	slam = make_anim(name, "slam", 0.95, "once", {
		"root": {"position": [(0.0, Z), (0.34, (0, 2.0, 0.5)), (0.56, (0, -3.6, -1.0)), (0.78, (0, -2.0, -0.5)), (0.95, Z)]},
		"waist": {"rotation": [(0.0, Z), (0.34, (-14, 0, 0)), (0.56, (22, 0, 0)), (0.95, Z)]},
		"chest": {"rotation": [(0.0, Z), (0.34, (-26, 0, 0)), (0.56, (32, 0, 0)), (0.95, Z)]},
		"head": {"rotation": [(0.0, Z), (0.34, (-20, 0, 0)), (0.56, (14, 0, 0)), (0.95, Z)]},
		"jaw": {"rotation": [(0.0, Z), (0.34, (34, 0, 0)), (0.56, (8, 0, 0)), (0.95, Z)]},
		**mirror_rot("arm", [(0.0, Z), (0.34, (-136, 10, 22)), (0.56, (-32, -8, 6)), (0.95, Z)]),
		**mirror_rot("forearm", [(0.0, Z), (0.34, (-18, 0, 0)), (0.56, (12, 0, 0)), (0.95, Z)]),
		**mirror_rot("leg", [(0.0, Z), (0.34, (8, 0, 0)), (0.56, (-22, 0, 6)), (0.95, Z)]),
		**mirror_rot("shin", [(0.0, Z), (0.56, (26, 0, 0)), (0.95, Z)]),
	})

	roar = make_anim(name, "roar", 1.2, "once", {
		"chest": {"rotation": [(0.0, Z), (0.25, (14, 0, 0)), (0.52, (-22, 0, 0)), (0.90, (-18, 0, 0)), (1.2, Z)]},
		"head": {"rotation": [(0.0, Z), (0.25, (12, 0, 0)), (0.52, (-24, -6, 0)), (0.72, (-22, 6, 0)), (0.92, (-24, -4, 0)), (1.2, Z)]},
		"jaw": {"rotation": [(0.0, Z), (0.25, (6, 0, 0)), (0.50, (42, 0, 0)), (0.92, (38, 0, 0)), (1.2, Z)]},
		**mirror_rot("pauldron", [(0.0, Z), (0.52, (0, 0, 14)), (0.92, (0, 0, 12)), (1.2, Z)]),
		**mirror_rot("arm", [(0.0, Z), (0.25, (-28, 0, 8)), (0.52, (28, 16, 44)), (0.92, (24, 14, 40)), (1.2, Z)]),
		**mirror_rot("forearm", [(0.0, Z), (0.25, (-28, 0, 0)), (0.52, (-12, 0, 14)), (1.2, Z)]),
	})

	# Red-tell windup before block-breaking charge: drops torso low, locks ram-plate skull forward, paws ground!
	charge_windup = make_anim(name, "charge_windup", 0.75, "once", {
		"root": {"position": [(0.0, Z), (0.35, (0, -2.6, 1.8)), (0.75, (0, -3.2, 2.2))]},
		"waist": {"rotation": [(0.0, Z), (0.35, (14, 0, 0)), (0.75, (18, 0, 0))]},
		"chest": {"rotation": [(0.0, Z), (0.35, (22, 0, 0)), (0.75, (28, 0, 0))]},
		"neck": {"rotation": [(0.0, Z), (0.35, (10, 0, 0)), (0.75, (14, 0, 0))]},
		"head": {"rotation": [(0.0, Z), (0.35, (18, 0, 0)), (0.75, (24, 0, 0))]},
		"jaw": {"rotation": [(0.0, Z), (0.35, (24, 0, 0)), (0.75, (-4, 0, 0))]},
		"right_leg": {"rotation": [(0.0, Z), (0.25, (-26, 0, 0)), (0.48, (28, 0, 0)), (0.75, (22, 0, 0))]},
		"left_leg": {"rotation": [(0.0, Z), (0.75, (-20, 0, 0))]},
		**mirror_rot("arm", [(0.0, Z), (0.40, (24, 0, 22)), (0.75, (32, 0, 26))]),
		**mirror_rot("forearm", [(0.0, Z), (0.75, (-38, 0, 0))]),
	})

	# Sustained block-breaking charge (loop: true!): battering-ram head & horns locked dead ahead, piston legs & arms!
	charge = make_anim(name, "charge", 0.45, "loop", {
		"root": {"position": closed_loop(0.45, (0.0, (0, -2.6, 0)), (0.25, (0, -0.8, -0.6)), (0.5, (0, -2.6, 0)), (0.75, (0, -0.8, -0.6)))},
		"waist": {"rotation": closed_loop(0.45, (0.0, (18, -8, 0)), (0.5, (18, 8, 0)))},
		"chest": {"rotation": closed_loop(0.45, (0.0, (28, 10, 4)), (0.5, (28, -10, -4)))},
		"neck": {"rotation": closed_loop(0.45, (0.0, (12, 0, 0)), (0.5, (14, 0, 0)))},
		"head": {"rotation": closed_loop(0.45, (0.0, (22, -4, 0)), (0.5, (22, 4, 0)))},
		"jaw": {"rotation": closed_loop(0.45, (0.0, (-4, 0, 0)), (0.5, (8, 0, 0)))},
		"right_leg": {"rotation": closed_loop(0.45, (0.0, (-44, 0, 0)), (0.5, (40, 0, 0)))},
		"left_leg": {"rotation": closed_loop(0.45, (0.0, (40, 0, 0)), (0.5, (-44, 0, 0)))},
		"right_shin": {"rotation": closed_loop(0.45, (0.0, (26, 0, 0)), (0.5, (8, 0, 0)))},
		"left_shin": {"rotation": closed_loop(0.45, (0.0, (8, 0, 0)), (0.5, (26, 0, 0)))},
		"right_arm": {"rotation": closed_loop(0.45, (0.0, (34, 0, 20)), (0.5, (-38, 0, 16)))},
		"left_arm": {"rotation": closed_loop(0.45, (0.0, (-38, 0, -16)), (0.5, (34, 0, -20)))},
		**mirror_rot("forearm", closed_loop(0.45, (0.0, (-42, 0, 0)), (0.5, (-32, 0, 0)))),
	})

	# Block-shattering horn gore & shoulder impact at the end of a charge!
	charge_smash = make_anim(name, "charge_smash", 0.7, "once", {
		"root": {"position": [(0.0, (0, -2.6, 0)), (0.24, (0, 1.4, -2.2)), (0.48, (0, 0.4, -0.8)), (0.7, Z)]},
		"chest": {"rotation": [(0.0, (28, 0, 0)), (0.24, (-22, 0, 0)), (0.48, (-8, 0, 0)), (0.7, Z)]},
		"neck": {"rotation": [(0.0, (14, 0, 0)), (0.24, (-20, 0, 0)), (0.7, Z)]},
		"head": {"rotation": [(0.0, (24, 0, 0)), (0.24, (-28, 0, 0)), (0.48, (-10, 0, 0)), (0.7, Z)]},
		"jaw": {"rotation": [(0.0, (-4, 0, 0)), (0.24, (36, 0, 0)), (0.7, Z)]},
		**mirror_rot("arm", [(0.0, (30, 0, 22)), (0.24, (-78, 16, 36)), (0.48, (-24, 8, 16)), (0.7, Z)]),
	})

	return b, [idle, walk, run, attack, slam, roar, charge_windup, charge, charge_smash]


# =====================================================================
# B7: BONE-BURROWER & HEAVY ROT-SICK BURROWER (shared 64x64 UV sheet)
# =====================================================================

def make_burrower_sheet() -> tuple[IndexedSheet, dict]:
	sheet = IndexedSheet(64, 64)
	uv = {
		"head": sheet.alloc_box("head", 6, 4, 6, "bone", detail_fn=paint_feral_skull(eye_row=1, bone_snout=True)),
		"snout": sheet.alloc_box("snout", 4, 3, 4, "bone", detail_fn=paint_osteoderm_shell),
		"dig_horn": sheet.alloc_box("dig_horn", 2, 2, 3, "bone", detail_fn=paint_claw_tip),
		"jaw": sheet.alloc_box("jaw", 5, 2, 6, "hide", detail_fn=paint_jaw_maw),
		"neck": sheet.alloc_box("neck", 5, 4, 4, "hide", hi=4, mid=3, side=3, back=2, lo=2, dark=1),
		"body_f": sheet.alloc_box("body_f", 8, 6, 7, "hide", detail_fn=paint_filth_caked("hide")),
		"body_m": sheet.alloc_box("body_m", 7, 6, 6, "hide", detail_fn=paint_filth_caked("hide")),
		"body_r": sheet.alloc_box("body_r", 6, 5, 5, "hide", detail_fn=paint_filth_caked("hide")),
		"plate_f": sheet.alloc_box("plate_f", 9, 2, 7, "bone", detail_fn=paint_osteoderm_shell),
		"plate_m": sheet.alloc_box("plate_m", 8, 2, 6, "bone", detail_fn=paint_osteoderm_shell),
		"plate_r": sheet.alloc_box("plate_r", 7, 2, 5, "bone", detail_fn=paint_osteoderm_shell),
		"tail_0": sheet.alloc_box("tail_0", 4, 3, 5, "bone", detail_fn=paint_osteoderm_shell),
		"tail_1": sheet.alloc_box("tail_1", 2, 2, 5, "bone", detail_fn=paint_claw_tip),
		"arm": sheet.alloc_box("arm", 3, 4, 4, "hide", hi=5, mid=4, side=3, back=3, lo=2, dark=1),
		"forearm": sheet.alloc_box("forearm", 3, 4, 4, "filth", hi=4, mid=3, side=2, back=2, lo=1, dark=1),
		"paw": sheet.alloc_box("paw", 4, 2, 4, "filth", hi=4, mid=3, side=2, back=2, lo=1, dark=1),
		"spade": sheet.alloc_box("spade", 1, 1, 5, "bone", detail_fn=paint_claw_tip),
		"leg": sheet.alloc_box("leg", 3, 5, 4, "hide", hi=4, mid=3, side=3, back=2, lo=2, dark=1),
		"shin": sheet.alloc_box("shin", 2, 4, 3, "filth", hi=4, mid=3, side=2, back=2, lo=1, dark=1),
		"foot": sheet.alloc_box("foot", 3, 2, 4, "filth", hi=4, mid=3, side=2, back=2, lo=1, dark=1),
		"rot_sac": sheet.alloc_box("rot_sac", 3, 3, 3, "sinew", detail_fn=paint_swollen_belly),
	}
	return sheet, uv


def _build_burrower_rig(name: str, sheet: IndexedSheet, uv: dict, scale: float, heavy: bool) -> RigBuilder:
	S = lambda v: round(v * scale, 4)
	w_mult = 1.14 if heavy else 1.0
	W = lambda v: round(v * scale * w_mult, 4)
	b = RigBuilder(name, sheet)
	b.bone("root", None, (0, 0, 0))

	# Low subterranean 3-segment curling body (ground clearance y~3.2, dorsal shell top y~12.5!)
	bf_y, bf_z = S(7.2), -S(3.2)
	b.bone("body_front", "root", (0, bf_y, bf_z))
	b.cube("body_front", "thorax", (-W(4.2), S(3.6), bf_z - S(3.8)), (W(4.2), S(10.0), bf_z + S(3.4)), uv["body_f"])
	b.cube("body_front", "carapace_f", (-W(4.7), S(9.4), bf_z - S(4.0)), (W(4.7), S(12.0), bf_z + S(3.6)), uv["plate_f"], rotation=(-4, 0, 0), origin=(0, S(10.5), bf_z))
	b.cube("body_front", "ridge_f", (-S(0.7), S(11.6), bf_z - S(2.8)), (S(0.7), S(13.4), bf_z + S(2.8)), uv["dig_horn"])

	bm_y, bm_z = S(7.4), bf_z + S(3.2)
	b.bone("body_mid", "body_front", (0, bm_y, bm_z))
	b.cube("body_mid", "lumbar", (-W(3.7), S(3.8), bm_z - S(0.4)), (W(3.7), S(9.8), bm_z + S(5.8)), uv["body_m"])
	b.cube("body_mid", "carapace_m", (-W(4.2), S(9.2), bm_z - S(0.6)), (W(4.2), S(11.6), bm_z + S(6.0)), uv["plate_m"], rotation=(4, 0, 0), origin=(0, S(10.2), bm_z + S(2.6)))
	b.cube("body_mid", "ridge_m", (-S(0.6), S(11.2), bm_z + S(0.4)), (S(0.6), S(12.8), bm_z + S(5.2)), uv["dig_horn"])

	br_y, br_z = S(6.8), bm_z + S(5.6)
	b.bone("body_rear", "body_mid", (0, br_y, br_z), (-6, 0, 0))
	b.cube("body_rear", "rump", (-W(3.2), S(3.6), br_z - S(0.4)), (W(3.2), S(8.8), br_z + S(5.0)), uv["body_r"])
	b.cube("body_rear", "carapace_r", (-W(3.6), S(8.2), br_z - S(0.6)), (W(3.6), S(10.4), br_z + S(5.2)), uv["plate_r"], rotation=(10, 0, 0), origin=(0, S(9.0), br_z + S(2.0)))

	# Heavy burrower rot-sick festering boils / sickness sacs along flanks & neck
	if heavy:
		b.bone("rot_sacs", "body_front", (0, bf_y + S(1.0), bf_z))
		boils = [
			(W(3.8), S(6.4), bf_z - S(1.2), (10, -14, -18)),
			(-W(3.8), S(6.0), bf_z + S(0.4), (-8, 16, 18)),
			(W(3.4), S(6.8), bm_z + S(2.2), (8, -12, -16)),
			(-W(3.5), S(6.2), bm_z + S(3.0), (-10, 14, 16)),
			(0.0, S(3.2), bf_z - S(1.6), (16, 0, 0)),
		]
		for bi, (bx, by, bz, brot) in enumerate(boils):
			b.cube("rot_sacs", f"pustule_{bi}", (bx - S(1.6), by - S(1.5), bz - S(1.6)), (bx + S(1.6), by + S(1.7), bz + S(1.6)), uv["rot_sac"], rotation=brot, origin=(bx, by, bz))

	# Armored curling tail
	t0_z = br_z + S(4.8)
	b.bone("tail_0", "body_rear", (0, S(6.2), t0_z), (-12, 0, 0))
	b.cube("tail_0", "seg_0", (-S(2.0), S(4.6), t0_z - S(0.4)), (S(2.0), S(7.6), t0_z + S(4.6)), uv["tail_0"])

	t1_z = t0_z + S(4.4)
	b.bone("tail_1", "tail_0", (0, S(5.8), t1_z), (-8, 0, 0))
	b.cube("tail_1", "seg_1", (-S(1.1), S(4.6), t1_z - S(0.4)), (S(1.1), S(6.8), t1_z + S(4.6)), uv["tail_1"])

	# Wedge subterranean shovel-skull & shearing mandibles
	neck_y, neck_z = S(7.4), bf_z - S(3.4)
	b.bone("neck", "body_front", (0, neck_y, neck_z), (8, 0, 0))
	b.cube("neck", "collar", (-W(2.8), S(4.8), neck_z - S(3.2)), (W(2.8), S(9.4), neck_z + S(0.8)), uv["neck"])

	head_y, head_z = S(7.0), neck_z - S(2.8)
	b.bone("head", "neck", (0, head_y, head_z), (6, 0, 0))
	b.cube("head", "skull", (-S(3.2), S(4.8), head_z - S(5.6)), (S(3.2), S(9.2), head_z + S(0.6)), uv["head"])
	b.cube("head", "shovel_snout", (-S(2.2), S(4.8), head_z - S(9.2)), (S(2.2), S(7.8), head_z - S(5.2)), uv["snout"], rotation=(8, 0, 0), origin=(0, S(6.4), head_z - S(5.6)))
	b.cube("head", "nasal_plow", (-S(0.9), S(6.6), head_z - S(11.2)), (S(0.9), S(8.8), head_z - S(7.8)), uv["dig_horn"], rotation=(-22, 0, 0), origin=(0, S(7.4), head_z - S(8.5)))

	jaw_y, jaw_z = S(5.0), head_z - S(1.0)
	b.bone("jaw", "head", (0, jaw_y, jaw_z), (-8, 0, 0))
	b.cube("jaw", "mandible", (-S(2.5), S(3.4), jaw_z - S(7.4)), (S(2.5), S(5.1), jaw_z + S(0.4)), uv["jaw"])

	# Enormous mole/badger splayed digging forelimbs with 4 flat curved bone spade-claws per paw!
	for side, sx, mir in (("right", 1, False), ("left", -1, True)):
		ax = W(4.4) * sx
		az = bf_z - S(1.4)
		b.bone(f"{side}_arm", "body_front", (ax, S(6.2), az), (12, -10 * sx, 22 * sx))
		x0, x1 = sorted((ax - S(1.4) * sx, ax + S(2.2) * sx))
		b.cube(f"{side}_arm", "humerus", (x0, S(3.2), az - S(1.8)), (x1, S(7.2), az + S(1.8)), uv["arm"], mirror=mir)

		fx = ax + S(1.4) * sx
		b.bone(f"{side}_forearm", f"{side}_arm", (fx, S(3.6), az - S(0.6)), (14, 8 * sx, -14 * sx))
		fx0, fx1 = sorted((fx - S(1.5), fx + S(1.5)))
		b.cube(f"{side}_forearm", "ulna", (fx0, S(1.2), az - S(3.2)), (fx1, S(4.2), az + S(0.8)), uv["forearm"], mirror=mir)

		px = fx + S(0.4) * sx
		pz = az - S(2.2)
		# Level the paw flat onto y=0 despite arm/forearm tilt
		b.bone(f"{side}_paw", f"{side}_forearm", (px, S(1.5), pz), (-26, 4 * sx, -8 * sx))
		px0, px1 = sorted((px - S(2.2), px + S(2.2)))
		b.cube(f"{side}_paw", "palm", (px0, 0.0, pz - S(3.2)), (px1, S(1.8), pz + S(1.0)), uv["paw"], mirror=mir)
		# 4 huge fanned digging spade-claws!
		for ci, (ox, yaw_c, len_c) in enumerate(((-1.5, 16, 4.4), (-0.5, 5, 5.2), (0.5, -5, 5.0), (1.5, -16, 4.0))):
			cx = px + S(ox) * sx
			cx0, cx1 = sorted((cx - S(0.45), cx + S(0.45)))
			b.cube(
				f"{side}_paw", f"spade_{ci}",
				(cx0, 0.0, pz - S(2.8 + len_c)), (cx1, S(1.3), pz - S(2.6)),
				uv["spade"], mirror=mir, rotation=(8, yaw_c * sx, 0), origin=(cx, S(0.7), pz - S(2.8)),
			)

	# Coiled crouched hind legs for cave leaping
	for side, sx, mir in (("right", 1, False), ("left", -1, True)):
		lx = W(3.4) * sx
		lz = br_z + S(1.8)
		b.bone(f"{side}_leg", "body_rear", (lx, S(6.2), lz), (22, 6 * sx, 12 * sx))
		b.cube(f"{side}_leg", "thigh", (lx - S(1.5), S(2.6), lz - S(1.8)), (lx + S(1.5), S(7.0), lz + S(1.8)), uv["leg"], mirror=mir)

		b.bone(f"{side}_shin", f"{side}_leg", (lx + S(0.3) * sx, S(3.0), lz), (-34, 0, -8 * sx))
		sx_c = lx + S(0.3) * sx
		b.cube(f"{side}_shin", "calf", (sx_c - S(1.2), S(0.8), lz - S(1.2)), (sx_c + S(1.2), S(3.6), lz + S(1.6)), uv["shin"], mirror=mir)

		b.bone(f"{side}_foot", f"{side}_shin", (sx_c, S(1.2), lz + S(0.4)), (18, 0, -4 * sx))
		b.cube(f"{side}_foot", "pad", (sx_c - S(1.5), 0.0, lz - S(2.8)), (sx_c + S(1.5), S(1.5), lz + S(1.4)), uv["foot"], mirror=mir)
	return b


def build_burrower_animations(name: str, scale: float) -> list[dict]:
	S = lambda v: round(v * scale, 4)

	idle = make_anim(name, "idle", 2.6, "loop", {
		"body_front": {"rotation": closed_loop(2.6, (0.0, Z), (0.5, (-2, 0, 0)))},
		"body_mid": {"rotation": closed_loop(2.6, (0.0, Z), (0.5, (3, 0, 0)))},
		"neck": {"rotation": closed_loop(2.6, (0.0, Z), (0.35, (4, -6, 0)), (0.7, (-3, 6, 0)))},
		"head": {"rotation": closed_loop(2.6, (0.0, (0, -5, 0)), (0.35, (5, 8, 2)), (0.7, (-3, -6, -2)))},
		"jaw": {"rotation": closed_loop(2.6, (0.0, (2, 0, 0)), (0.5, (14, 0, 0)))},
		"tail_0": {"rotation": closed_loop(2.6, (0.0, (0, -8, 0)), (0.5, (0, 8, 0)))},
		"tail_1": {"rotation": closed_loop(2.6, (0.0, (0, -12, 0)), (0.5, (0, 12, 0)))},
		**mirror_rot("paw", closed_loop(2.6, (0.0, Z), (0.4, (8, 0, 4)), (0.7, (-4, 0, -2)))),
	})

	walk = make_anim(name, "walk", 0.65, "loop", {
		"root": {"position": closed_loop(0.65, (0.0, (0, 0, 0)), (0.25, (0, S(0.45), 0)), (0.5, (0, 0, 0)), (0.75, (0, S(0.45), 0)))},
		"body_front": {"rotation": closed_loop(0.65, (0.0, (0, 6, 2)), (0.5, (0, -6, -2)))},
		"body_mid": {"rotation": closed_loop(0.65, (0.0, (0, -8, 0)), (0.5, (0, 8, 0)))},
		"body_rear": {"rotation": closed_loop(0.65, (0.0, (0, -6, -2)), (0.5, (0, 6, 2)))},
		"head": {"rotation": closed_loop(0.65, (0.0, (0, -5, 0)), (0.5, (0, 5, 0)))},
		"tail_0": {"rotation": closed_loop(0.65, (0.0, (0, 12, 0)), (0.5, (0, -12, 0)))},
		"right_arm": {"rotation": closed_loop(0.65, (0.0, (-28, 12, 6)), (0.5, (24, -10, 0)))},
		"left_arm": {"rotation": closed_loop(0.65, (0.0, (24, 10, 0)), (0.5, (-28, -12, -6)))},
		"right_paw": {"rotation": closed_loop(0.65, (0.0, (-14, 0, 0)), (0.25, (18, 0, 0)), (0.5, (12, 0, 0)), (0.75, (-18, 0, 0)))},
		"left_paw": {"rotation": closed_loop(0.65, (0.0, (12, 0, 0)), (0.25, (-18, 0, 0)), (0.5, (-14, 0, 0)), (0.75, (18, 0, 0)))},
		"right_leg": {"rotation": closed_loop(0.65, (0.0, (24, 0, 0)), (0.5, (-26, 0, 0)))},
		"left_leg": {"rotation": closed_loop(0.65, (0.0, (-26, 0, 0)), (0.5, (24, 0, 0)))},
	})

	run = make_anim(name, "run", 0.45, "loop", {
		"root": {"position": closed_loop(0.45, (0.0, (0, -S(0.4), 0)), (0.35, (0, S(1.4), -S(0.6))), (0.7, (0, -S(0.5), S(0.4))))},
		"body_front": {"rotation": closed_loop(0.45, (0.0, (8, 0, 0)), (0.4, (-10, 0, 0)), (0.75, (6, 0, 0)))},
		"body_mid": {"rotation": closed_loop(0.45, (0.0, (-12, 0, 0)), (0.4, (10, 0, 0)), (0.75, (-8, 0, 0)))},
		"body_rear": {"rotation": closed_loop(0.45, (0.0, (-10, 0, 0)), (0.4, (12, 0, 0)), (0.75, (-8, 0, 0)))},
		"jaw": {"rotation": closed_loop(0.45, (0.0, (8, 0, 0)), (0.4, (20, 0, 0)))},
		**mirror_rot("arm", closed_loop(0.45, (0.0, (28, -8, 6)), (0.45, (-38, 12, 12)))),
		**mirror_rot("leg", closed_loop(0.45, (0.0, (-30, 0, 0)), (0.45, (32, 0, 0)))),
	})

	attack = make_anim(name, "attack", 0.55, "once", {
		"root": {"position": [(0.0, Z), (0.18, (0, S(1.2), S(0.8))), (0.34, (0, S(0.4), -S(1.8))), (0.55, Z)]},
		"body_front": {"rotation": [(0.0, Z), (0.18, (-18, 0, 0)), (0.34, (14, 0, 0)), (0.55, Z)]},
		"neck": {"rotation": [(0.0, Z), (0.18, (-14, 0, 0)), (0.34, (12, 0, 0)), (0.55, Z)]},
		"head": {"rotation": [(0.0, Z), (0.18, (-16, 0, 0)), (0.34, (10, 0, 0)), (0.55, Z)]},
		"jaw": {"rotation": [(0.0, Z), (0.18, (32, 0, 0)), (0.34, (-8, 0, 0)), (0.55, Z)]},
		**mirror_rot("arm", [(0.0, Z), (0.18, (-58, 18, 28)), (0.34, (16, -18, -10)), (0.55, Z)]),
		**mirror_rot("paw", [(0.0, Z), (0.18, (-24, 0, 14)), (0.34, (26, 0, -12)), (0.55, Z)]),
	})

	# Yellow-tell cave leap: coils segmented spine back onto hind haunches (0.22s), springs into pounce (0.45s)!
	leap = make_anim(name, "leap", 0.85, "once", {
		"root": {"position": [
			(0.0, Z), (0.22, (0, -S(1.6), S(2.6))), (0.45, (0, S(5.4), -S(3.8))),
			(0.68, (0, -S(0.8), -S(1.0))), (0.85, Z),
		]},
		"body_front": {"rotation": [(0.0, Z), (0.22, (-18, 0, 0)), (0.45, (-22, 0, 0)), (0.68, (14, 0, 0)), (0.85, Z)]},
		"body_mid": {"rotation": [(0.0, Z), (0.22, (22, 0, 0)), (0.45, (-12, 0, 0)), (0.68, (8, 0, 0)), (0.85, Z)]},
		"body_rear": {"rotation": [(0.0, Z), (0.22, (18, 0, 0)), (0.45, (-14, 0, 0)), (0.85, Z)]},
		"head": {"rotation": [(0.0, Z), (0.22, (14, 0, 0)), (0.45, (-12, 0, 0)), (0.85, Z)]},
		"jaw": {"rotation": [(0.0, Z), (0.22, (12, 0, 0)), (0.45, (34, 0, 0)), (0.68, (-4, 0, 0)), (0.85, Z)]},
		**mirror_rot("arm", [(0.0, Z), (0.22, (-32, 0, 14)), (0.45, (-68, 16, 30)), (0.68, (-16, 0, 8)), (0.85, Z)]),
		**mirror_rot("paw", [(0.0, Z), (0.45, (-22, 0, 14)), (0.68, (18, 0, -6)), (0.85, Z)]),
		**mirror_rot("leg", [(0.0, Z), (0.22, (-34, 0, 0)), (0.45, (42, 0, 0)), (0.68, (-12, 0, 0)), (0.85, Z)]),
	})

	# Curl into an armored osteoderm ball! (Rear segments extend +z so negative x tucks them DOWN underneath,
	# while neck/head extend -z so positive x tucks the snout DOWN to meet the tail — plates stay on the outside!)
	curl = make_anim(name, "curl", 0.65, "once", {
		"root": {"position": [(0.0, Z), (0.35, (0, S(2.6), 0)), (0.65, (0, S(2.2), 0))]},
		"body_front": {"rotation": [(0.0, Z), (0.35, (24, 0, 0)), (0.65, (28, 0, 0))]},
		"body_mid": {"rotation": [(0.0, Z), (0.35, (-44, 0, 0)), (0.65, (-52, 0, 0))]},
		"body_rear": {"rotation": [(0.0, Z), (0.35, (-48, 0, 0)), (0.65, (-56, 0, 0))]},
		"tail_0": {"rotation": [(0.0, Z), (0.35, (-42, 0, 0)), (0.65, (-50, 0, 0))]},
		"tail_1": {"rotation": [(0.0, Z), (0.35, (-38, 0, 0)), (0.65, (-46, 0, 0))]},
		"neck": {"rotation": [(0.0, Z), (0.35, (38, 0, 0)), (0.65, (46, 0, 0))]},
		"head": {"rotation": [(0.0, Z), (0.35, (42, 0, 0)), (0.65, (50, 0, 0))]},
		**mirror_rot("arm", [(0.0, Z), (0.35, (-38, -22, -18)), (0.65, (-44, -26, -22))]),
		**mirror_rot("paw", [(0.0, Z), (0.65, (32, 0, 20))]),
		**mirror_rot("leg", [(0.0, Z), (0.65, (-40, 0, -14))]),
	})

	# Burrow (curl & dive!): curls carapace outward (0.24s), then pitches nose straight down (+68 deg) and paddles spade-claws into the earth!
	burrow = make_anim(name, "burrow", 1.0, "once", {
		"root": {"position": [
			(0.0, Z), (0.24, (0, S(3.2), S(1.0))),
			(0.52, (0, S(1.6), -S(1.4))), (0.78, (0, -S(6.5), -S(2.4))), (1.0, (0, -S(13.5), -S(3.0))),
		]},
		"body_front": {"rotation": [(0.0, Z), (0.24, (28, 0, 0)), (0.52, (64, 0, 0)), (0.78, (74, 0, 0)), (1.0, (80, 0, 0))]},
		"body_mid": {"rotation": [(0.0, Z), (0.24, (-46, 0, 0)), (0.52, (-18, 0, 0)), (0.78, (-8, 0, 0)), (1.0, Z)]},
		"body_rear": {"rotation": [(0.0, Z), (0.24, (-48, 0, 0)), (0.52, (-16, 0, 0)), (0.78, (-6, 0, 0)), (1.0, Z)]},
		"tail_0": {"rotation": [(0.0, Z), (0.24, (-40, 0, 0)), (0.52, (-12, 0, 0)), (1.0, Z)]},
		"neck": {"rotation": [(0.0, Z), (0.24, (36, 0, 0)), (0.52, (10, 0, 0)), (1.0, (6, 0, 0))]},
		"head": {"rotation": [(0.0, Z), (0.24, (40, 0, 0)), (0.52, (8, 0, 0)), (1.0, (4, 0, 0))]},
		# Furious breast-stroke digging paddle as it dives!
		**mirror_rot("arm", [
			(0.0, Z), (0.24, (-44, -18, -14)),
			(0.44, (-72, 20, 26)), (0.62, (32, -20, 12)),
			(0.80, (-72, 20, 26)), (1.0, (32, -20, 12)),
		]),
		**mirror_rot("paw", [
			(0.0, Z), (0.44, (-24, 0, 12)), (0.62, (34, 0, -14)),
			(0.80, (-24, 0, 12)), (1.0, (34, 0, -14)),
		]),
	})

	# Emerge: erupts nose-first UP out of the cave floor, flinging earth with spade-claws, landing flat!
	emerge = make_anim(name, "emerge", 0.9, "once", {
		"root": {"position": [
			(0.0, (0, -S(12.5), S(1.5))), (0.30, (0, S(2.6), -S(0.6))),
			(0.58, (0, S(3.2), -S(1.0))), (0.76, (0, -S(0.8), 0)), (0.9, Z),
		]},
		"body_front": {"rotation": [(0.0, (-68, 0, 0)), (0.30, (-48, 0, 0)), (0.58, (-16, 0, 0)), (0.76, (10, 0, 0)), (0.9, Z)]},
		"body_mid": {"rotation": [(0.0, (-14, 0, 0)), (0.30, (18, 0, 0)), (0.58, (22, 0, 0)), (0.9, Z)]},
		"body_rear": {"rotation": [(0.0, (-10, 0, 0)), (0.30, (20, 0, 0)), (0.58, (16, 0, 0)), (0.9, Z)]},
		"head": {"rotation": [(0.0, (-16, 0, 0)), (0.35, (-22, 0, 0)), (0.65, (8, 0, 0)), (0.9, Z)]},
		"jaw": {"rotation": [(0.0, (6, 0, 0)), (0.38, (34, 0, 0)), (0.72, (-4, 0, 0)), (0.9, Z)]},
		**mirror_rot("arm", [(0.0, (-64, 14, 18)), (0.35, (28, -18, 38)), (0.62, (-28, 10, 20)), (0.9, Z)]),
		**mirror_rot("paw", [(0.0, (-20, 0, 0)), (0.35, (28, 0, 16)), (0.9, Z)]),
	})

	return [idle, walk, run, attack, leap, curl, burrow, emerge]


def build_burrowers():
	sheet, uv = make_burrower_sheet()
	base_rig = _build_burrower_rig("bone_burrower", sheet, uv, scale=1.0, heavy=False)
	base_anims = build_burrower_animations("bone_burrower", scale=1.0)

	heavy_rig = _build_burrower_rig("bone_burrower_heavy", sheet, uv, scale=1.18, heavy=True)
	heavy_anims = build_burrower_animations("bone_burrower_heavy", scale=1.18)
	return (base_rig, base_anims), (heavy_rig, heavy_anims)
