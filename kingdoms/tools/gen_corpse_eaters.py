#!/usr/bin/env python3
"""
Hunter's Road 3 — Corpse-eaters (Kingdoms monsters/) rows B1, B5, B6, B7, F1, F2:
  - B1: grave_gnawer, grave_gnawer_leader (spined pack leader, 1-to-1 UV palette swap + dorsal spines)
  - B5: bloated_dead, bloated_dead_spined (visibly swelling torso & burst, 1-to-1 UV palette swap + spines)
  - B6: grave_brute (2.63-block deep-cave miniboss, broad shoulders, battering-ram skull, block-breaking charge)
  - B7: bone_burrower, bone_burrower_heavy (low digging spade-claws, curl & dive, rot-sick heavy variant)
  - F1: corpse_nest (charnel mound of bones, skulls, ribs & grave-filth)
  - F2: old_skeleton, fallen_warrior (slumped cave skeleton & kneeling armored warrior, optional blade bone)

Follows docs/Vanilla-Style.md (the Reason look):
  - Every colour comes strictly from tools/vanilla_style/vstyle.py MAT ramps (rotting hide, bone, filth, sinew, eyes).
  - Zero per-pixel noise, zero soft gradients, zero half-transparent pixels; ~26-34 colours per sheet.
  - Variants share their base mob's exact 64x64 UV layout as true 1-to-1 palette swaps.

Run from the repository root:
  python -I kingdoms/tools/gen_corpse_eaters.py [--preview docs/previews/hunters-road-3-models]
"""
from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from corpse_eaters.blocks_nest_bones import build_corpse_nest, build_fallen_warrior, build_old_skeleton  # noqa: E402
from corpse_eaters.common import ASSETS, ART_DIR, ROOT, export_model_files  # noqa: E402
from corpse_eaters.mobs_brute_burrower import build_burrowers, build_grave_brute  # noqa: E402
from corpse_eaters.mobs_gnawer_bloated import build_bloated, build_gnawers  # noqa: E402
import bbmodel_render  # noqa: E402


def _bake_for_preview(art_path: str, tmp_dir: str, name: str, drop: str = ""):
	"""Flips free-format rotation x/y to match bbmodel_geckolib's export and bakes into tmp_dir for bbmodel_preview."""
	with open(art_path, encoding="utf-8") as f:
		preview_bb = json.load(f)
	for anim in preview_bb.get("animations", []):
		for animator in anim.get("animators", {}).values():
			for kf in animator.get("keyframes", []):
				if kf["channel"] == "rotation":
					for dp in kf["data_points"]:
						dp["x"], dp["y"] = -dp["x"], -dp["y"]
	p_path = os.path.join(tmp_dir, f"{name}_preview.bbmodel")
	with open(p_path, "w", encoding="utf-8") as f:
		json.dump(preview_bb, f)
	cmd = [sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_bake.py"), p_path, tmp_dir, name]
	if drop:
		cmd += ["--drop", drop]
	subprocess.run(cmd, check=True)


def _preview_panel(tmp_dir: str, name: str, anim: str, t: float, panel_idx: int = 2) -> Image.Image:
	"""Runs bbmodel_preview.py and crops one 360x360 panel (default panel 2 = 150-deg front-3/4 view)."""
	out_png = os.path.join(tmp_dir, f"panel_{name}_{anim}_{t:.2f}_{panel_idx}.png")
	subprocess.run(
		[sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_preview.py"), tmp_dir, name, out_png, anim, str(t)],
		check=True,
	)
	im = Image.open(out_png).convert("RGBA")
	pw = im.width // 5
	return im.crop((pw * panel_idx, 0, pw * (panel_idx + 1), im.height))


def _compose_strip(cards: list[tuple[str, Image.Image]], out_path: str, title: str = ""):
	pad = 12
	header_h = 28 if title else 0
	label_h = 24
	cw = max(im.width for _, im in cards)
	ch = max(im.height for _, im in cards)
	w = len(cards) * (cw + pad) + pad
	h = header_h + ch + label_h + pad * 2
	sheet = Image.new("RGBA", (w, h), (26, 28, 34, 255))
	draw = ImageDraw.Draw(sheet)
	if title:
		draw.text((pad + 4, 8), title, fill=(242, 238, 228, 255))
	for idx, (lbl, im) in enumerate(cards):
		ox = pad + idx * (cw + pad)
		oy = header_h + pad + label_h
		sheet.alpha_composite(im, (ox + (cw - im.width) // 2, oy + (ch - im.height)))
		draw.text((ox + 8, header_h + pad + 4), lbl, fill=(224, 220, 212, 255))
	sheet.convert("RGB").save(out_path)
	print("rendered", os.path.relpath(out_path, ROOT))


def render_all_previews(preview_dir: str, specs: list[dict]):
	os.makedirs(preview_dir, exist_ok=True)
	with tempfile.TemporaryDirectory() as tmp:
		for sp in specs:
			name = sp["name"]
			art_path = os.path.join(ART_DIR, f"{name}.bbmodel")
			_bake_for_preview(art_path, tmp, name)

			# 1. Full 5-angle turnaround sheet at rest (30, 90, 150, 0, 210 deg)
			rest_out = os.path.join(preview_dir, f"{name}_rest.png")
			subprocess.run(
				[sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_preview.py"), tmp, name, rest_out, "-", "0.0"],
				check=True,
			)
			print("rendered", os.path.relpath(rest_out, ROOT))

			# 2. Labeled animation keyframe strip for every creature / animated block
			if sp.get("pose_frames"):
				cards = [(lbl, _preview_panel(tmp, name, anim, t, panel)) for lbl, anim, t, panel in sp["pose_frames"]]
				_compose_strip(cards, os.path.join(preview_dir, f"{name}_moves.png"), title=f"{name} - key animation frames")

		# 3. Variant & bare-vs-blade comparison sheets
		for bare_name in ("old_skeleton", "fallen_warrior"):
			art_path = os.path.join(ART_DIR, f"{bare_name}.bbmodel")
			_bake_for_preview(art_path, tmp, f"{bare_name}_bare", drop="blade")
			cards = [
				(f"{bare_name} (with blade, front-3/4)", _preview_panel(tmp, bare_name, "-", 0.0, 2)),
				(f"{bare_name} (with blade, side)", _preview_panel(tmp, bare_name, "-", 0.0, 1)),
				(f"{bare_name} (no blade, front-3/4)", _preview_panel(tmp, f"{bare_name}_bare", "-", 0.0, 2)),
				(f"{bare_name} (no blade, side)", _preview_panel(tmp, f"{bare_name}_bare", "-", 0.0, 1)),
			]
			_compose_strip(cards, os.path.join(preview_dir, f"{bare_name}_variants.png"), title=f"{bare_name} - with blade vs bare")

		# 4. True game-scale relative size lineup (using bbmodel_render at uniform 7 px/unit = 112 px/block!)
		scale_px = 7.0
		renders = []
		for sp in specs:
			name = sp["name"]
			art_path = os.path.join(ART_DIR, f"{name}.bbmodel")
			im = bbmodel_render.render(art_path, yaw=32.0, scale=scale_px)
			renders.append((name, im))

		gap = 20
		top_pad = 36
		bot_pad = 34
		max_h = max(max(im.height for _, im in renders), int(16 * 3 * scale_px))
		total_w = sum(im.width for _, im in renders) + gap * (len(renders) + 1) + 56
		total_h = max_h + top_pad + bot_pad
		lineup = Image.new("RGBA", (total_w, total_h), (32, 35, 42, 255))
		draw = ImageDraw.Draw(lineup)
		ground_y = top_pad + max_h
		# Draw 0b, 1b, 2b, 3b reference height rules across the lineup!
		for blk in range(0, 4):
			ry = int(round(ground_y - blk * 16 * scale_px))
			if 8 <= ry < total_h:
				col = (90, 96, 110, 255) if blk == 0 else (56, 62, 74, 255)
				draw.line([(44, ry), (total_w - 12, ry)], fill=col, width=1)
				draw.text((8, ry - 6), f"{blk}b", fill=(185, 192, 205, 255))
		draw.text((12, 10), "Hunter's Road 3 - Corpse-eaters at uniform game scale (1 block = 16 units)", fill=(240, 236, 226, 255))
		cx = 56
		for name, im in renders:
			lineup.alpha_composite(im, (cx, ground_y - im.height))
			short = name.replace("grave_", "g_").replace("bloated_", "b_").replace("bone_", "")
			draw.text((cx, ground_y + 8), short, fill=(224, 220, 212, 255))
			cx += im.width + gap
		lineup_path = os.path.join(preview_dir, "corpse_eaters_scale_lineup.png")
		lineup.convert("RGB").save(lineup_path)
		print("rendered", os.path.relpath(lineup_path, ROOT))


def main(argv: list[str]):
	(gnawer_rig, gnawer_anims), (leader_rig, leader_anims) = build_gnawers()
	(bloated_rig, bloated_anims), (spined_rig, spined_anims) = build_bloated()
	brute_rig, brute_anims = build_grave_brute()
	(burrower_rig, burrower_anims), (heavy_rig, heavy_anims) = build_burrowers()
	nest_rig, nest_anims = build_corpse_nest()
	skel_rig, skel_anims = build_old_skeleton()
	war_rig, war_anims = build_fallen_warrior()

	specs = [
		{
			"name": "grave_gnawer", "rig": gnawer_rig, "anims": gnawer_anims, "pal": "grave_gnawer", "block": False,
			"pose_frames": [
				("idle (1.2s)", "idle", 1.2, 2),
				("walk (0.0s)", "walk", 0.0, 1),
				("run (0.35s)", "run", 0.18, 1),
				("attack (0.30s)", "attack", 0.30, 2),
				("lunge coil (0.22s)", "lunge", 0.22, 1),
				("lunge air (0.46s)", "lunge", 0.46, 2),
				("knit_back down (0.0s)", "knit_back", 0.0, 2),
				("knit_back wrench (0.72s)", "knit_back", 0.72, 2),
			],
		},
		{
			"name": "grave_gnawer_leader", "rig": leader_rig, "anims": leader_anims, "pal": "grave_gnawer_leader", "block": False,
			"pose_frames": [
				("idle (1.2s)", "idle", 1.2, 2),
				("walk (0.0s)", "walk", 0.0, 1),
				("lunge air (0.46s)", "lunge", 0.46, 2),
				("knit_back (0.35s)", "knit_back", 0.35, 2),
				("roar (0.48s)", "roar", 0.48, 2),
			],
		},
		{
			"name": "bloated_dead", "rig": bloated_rig, "anims": bloated_anims, "pal": "bloated_dead", "block": False,
			"pose_frames": [
				("idle (1.5s)", "idle", 1.5, 2),
				("walk (0.25s)", "walk", 0.25, 2),
				("run (0.16s)", "run", 0.16, 1),
				("attack (0.22s)", "attack", 0.22, 2),
				("swell tell (1.1s)", "swell", 1.1, 2),
				("burst peak (0.24s)", "burst", 0.24, 2),
				("burst rupture (0.42s)", "burst", 0.42, 2),
			],
		},
		{
			"name": "bloated_dead_spined", "rig": spined_rig, "anims": spined_anims, "pal": "bloated_dead_spined", "block": False,
			"pose_frames": [
				("idle (1.5s)", "idle", 1.5, 2),
				("walk (0.25s)", "walk", 0.25, 1),
				("swell tell (1.1s)", "swell", 1.1, 2),
				("burst peak (0.24s)", "burst", 0.24, 2),
				("burst rupture (0.42s)", "burst", 0.42, 2),
			],
		},
		{
			"name": "grave_brute", "rig": brute_rig, "anims": brute_anims, "pal": "grave_brute", "block": False,
			"pose_frames": [
				("idle (1.5s)", "idle", 1.5, 2),
				("walk (0.0s)", "walk", 0.0, 1),
				("attack (0.25s)", "attack", 0.25, 2),
				("slam high (0.34s)", "slam", 0.34, 2),
				("slam impact (0.56s)", "slam", 0.56, 2),
				("charge tell (0.70s)", "charge_windup", 0.70, 1),
				("charge loop (0.0s)", "charge", 0.0, 1),
				("charge smash (0.24s)", "charge_smash", 0.24, 2),
			],
		},
		{
			"name": "bone_burrower", "rig": burrower_rig, "anims": burrower_anims, "pal": "bone_burrower", "block": False,
			"pose_frames": [
				("idle (1.2s)", "idle", 1.2, 2),
				("walk (0.0s)", "walk", 0.0, 2),
				("attack (0.18s)", "attack", 0.18, 2),
				("leap coil (0.22s)", "leap", 0.22, 1),
				("leap air (0.45s)", "leap", 0.45, 1),
				("curl ball (0.60s)", "curl", 0.60, 1),
				("burrow dive (0.52s)", "burrow", 0.52, 1),
				("emerge burst (0.32s)", "emerge", 0.32, 2),
			],
		},
		{
			"name": "bone_burrower_heavy", "rig": heavy_rig, "anims": heavy_anims, "pal": "bone_burrower_heavy", "block": False,
			"pose_frames": [
				("idle (1.2s)", "idle", 1.2, 2),
				("walk (0.0s)", "walk", 0.0, 1),
				("leap air (0.45s)", "leap", 0.45, 2),
				("curl ball (0.60s)", "curl", 0.60, 1),
				("burrow dive (0.52s)", "burrow", 0.52, 1),
			],
		},
		{
			"name": "corpse_nest", "rig": nest_rig, "anims": nest_anims, "pal": "corpse_nest", "block": True,
			"pose_frames": [
				("idle (0.0s)", "idle", 0.0, 2),
				("spawn open (0.55s)", "spawn", 0.55, 2),
				("hit rattle (0.12s)", "hit", 0.12, 1),
			],
		},
		{
			"name": "old_skeleton", "rig": skel_rig, "anims": skel_anims, "pal": "old_skeleton", "block": True,
			"block_variants": [("old_skeleton", {"blade"}), ("old_skeleton_blade", set())],
			"pose_frames": [
				("idle (front-3/4)", "idle", 0.0, 2),
				("idle (side)", "idle", 0.0, 1),
				("disturb (0.12s)", "disturb", 0.12, 2),
			],
		},
		{
			"name": "fallen_warrior", "rig": war_rig, "anims": war_anims, "pal": "fallen_warrior", "block": True,
			"block_variants": [("fallen_warrior", {"blade"}), ("fallen_warrior_blade", set())],
			"pose_frames": [
				("idle (front-3/4)", "idle", 0.0, 2),
				("idle (side)", "idle", 0.0, 1),
				("disturb (0.14s)", "disturb", 0.14, 2),
			],
		},
	]

	for sp in specs:
		export_model_files(
			sp["rig"], sp["anims"], sp["pal"],
			is_block=sp["block"],
			extra_block_variants=sp.get("block_variants", ()),
		)

	if "--preview" in argv:
		pdir = argv[argv.index("--preview") + 1]
		render_all_previews(pdir, specs)


if __name__ == "__main__":
	main(sys.argv[1:])
