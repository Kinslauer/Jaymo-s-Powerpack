#!/usr/bin/env python3
"""
Hunter's Road 4 — The drowned, hags and fog (Kingdoms monsters/) rows B2, B3, B4:
  - B2: river_drowned (river & swamp bank-lunge predator, fast horizontal swim, drag-under, clumsy land walk)
  - B3: mire_hag (swamp fen-crone in peat-reed shawl, stoops to scoop bank mud and hurls it) + mud_scoop block
  - B3: grave_hag (graveyard mourner-hag in widow's shroud, skull trophy collar, 3-bone barbed tongue lash)
  - B4: fog_wight & fog_wight_wisp (marsh-barrow wight with antler crown, hollow lure-ribcage, fog draw, fade & decoy wisp)

Follows docs/Vanilla-Style.md (the Reason look):
  - Every colour comes strictly from tools/vanilla_style/vstyle.py MAT ramps.
  - Zero per-pixel noise, zero soft gradients, zero half-transparent pixels; ~13-27 colours per sheet.
  - fog_wight and fog_wight_wisp share an exact 64x64 UV layout as a 1-to-1 spectral palette swap.

Run from the repository root:
  python -I kingdoms/tools/gen_drowned_hags_fog.py [--preview docs/previews/hunters-road-4-models]
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

from drowned_hags_fog.common import ART_DIR, ROOT, export_model_files  # noqa: E402
from drowned_hags_fog.mob_river_drowned import build_river_drowned  # noqa: E402
from drowned_hags_fog.mobs_fog_wight import build_fog_wight_pair  # noqa: E402
from drowned_hags_fog.mobs_hags import build_grave_hag, build_mire_hag, build_mud_scoop  # noqa: E402
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
	"""Runs bbmodel_preview.py and crops one 360x360 panel (0=30deg, 1=90deg side, 2=150deg front-3/4, 3=0deg back, 4=210deg)."""
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
			rest_drop = sp.get("rest_drop", "")
			_bake_for_preview(art_path, tmp, name)
			if rest_drop:
				_bake_for_preview(art_path, tmp, f"{name}_bare", drop=rest_drop)

			# 1. Full 5-angle turnaround sheet at rest (using bare claws for mire_hag so claws aren't obscured at rest)
			rest_src = f"{name}_bare" if rest_drop else name
			rest_out = os.path.join(preview_dir, f"{name}_rest.png")
			subprocess.run(
				[sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_preview.py"), tmp, rest_src, rest_out, "-", "0.0"],
				check=True,
			)
			print("rendered", os.path.relpath(rest_out, ROOT))

			# 2. Labeled animation keyframe strip for every creature / block
			if sp.get("pose_frames"):
				cards = [(lbl, _preview_panel(tmp, name, anim, t, panel)) for lbl, anim, t, panel in sp["pose_frames"]]
				_compose_strip(cards, os.path.join(preview_dir, f"{name}_moves.png"), title=f"{name} - key animation frames")

		# 3. Mire hag empty-claw vs mud_clump comparison & Fog-wight vs decoy wisp comparison
		mire_cards = [
			("mire_hag (empty claws, front-3/4)", _preview_panel(tmp, "mire_hag_bare", "-", 0.0, 2)),
			("mire_hag (empty claws, side)", _preview_panel(tmp, "mire_hag_bare", "-", 0.0, 1)),
			("mire_hag (mud_scoop hold, 0.75s)", _preview_panel(tmp, "mire_hag", "mud_scoop", 0.75, 2)),
			("mire_hag (mud_throw windup, 0.26s)", _preview_panel(tmp, "mire_hag", "mud_throw", 0.26, 2)),
		]
		_compose_strip(mire_cards, os.path.join(preview_dir, "mire_hag_variants.png"), title="mire_hag - bare claws vs scooped mud_clump")

		wight_cards = [
			("fog_wight (front-3/4)", _preview_panel(tmp, "fog_wight", "-", 0.0, 2)),
			("fog_wight (side)", _preview_panel(tmp, "fog_wight", "-", 0.0, 1)),
			("fog_wight_wisp decoy (front-3/4)", _preview_panel(tmp, "fog_wight_wisp", "-", 0.0, 2)),
			("fog_wight_wisp decoy (side)", _preview_panel(tmp, "fog_wight_wisp", "-", 0.0, 1)),
		]
		_compose_strip(wight_cards, os.path.join(preview_dir, "fog_wight_variants.png"), title="fog_wight vs fog_wight_wisp (1-to-1 UV decoy swap)")

		# 4. Uniform game-scale lineup (7 px/unit = 112 px/block, with 0b..3b reference height lines)
		scale_px = 7.0
		renders = []
		for sp in specs:
			name = sp["name"]
			art_path = os.path.join(ART_DIR, f"{name}.bbmodel")
			hide = tuple(filter(None, sp.get("rest_drop", "").split(",")))
			im = bbmodel_render.render(art_path, hide=hide, yaw=32.0, scale=scale_px)
			renders.append((name, im))

		gap = 24
		top_pad = 36
		bot_pad = 34
		max_h = max(max(im.height for _, im in renders), int(16 * 3 * scale_px))
		total_w = sum(im.width for _, im in renders) + gap * (len(renders) + 1) + 56
		total_h = max_h + top_pad + bot_pad
		lineup = Image.new("RGBA", (total_w, total_h), (32, 35, 42, 255))
		draw = ImageDraw.Draw(lineup)
		ground_y = top_pad + max_h
		for blk in range(0, 4):
			ry = int(round(ground_y - blk * 16 * scale_px))
			if 8 <= ry < total_h:
				col = (90, 96, 110, 255) if blk == 0 else (56, 62, 74, 255)
				draw.line([(44, ry), (total_w - 12, ry)], fill=col, width=1)
				draw.text((8, ry - 6), f"{blk}b", fill=(185, 192, 205, 255))
		draw.text((12, 10), "Hunter's Road 4 - Drowned, hags and fog at uniform game scale (1 block = 16 units)", fill=(240, 236, 226, 255))
		cx = 56
		for name, im in renders:
			lineup.alpha_composite(im, (cx, ground_y - im.height))
			draw.text((cx, ground_y + 8), name, fill=(224, 220, 212, 255))
			cx += im.width + gap
		lineup_path = os.path.join(preview_dir, "drowned_hags_fog_scale_lineup.png")
		lineup.convert("RGB").save(lineup_path)
		print("rendered", os.path.relpath(lineup_path, ROOT))


def main(argv: list[str]):
	drowned_rig, drowned_anims = build_river_drowned()
	mire_rig, mire_anims = build_mire_hag()
	scoop_rig, scoop_anims = build_mud_scoop()
	grave_rig, grave_anims = build_grave_hag()
	(wight_rig, wight_anims), (wisp_rig, wisp_anims) = build_fog_wight_pair()

	specs = [
		{
			"name": "river_drowned", "rig": drowned_rig, "anims": drowned_anims, "pal": "river_drowned", "block": False,
			"pose_frames": [
				("idle (1.4s)", "idle", 1.4, 2),
				("walk clumsy (0.24s)", "walk", 0.24, 2),
				("swim fast (0.14s)", "swim", 0.14, 1),
				("swim S-wave (0.40s)", "swim", 0.40, 1),
				("bank_lunge coil (0.26s)", "bank_lunge", 0.26, 1),
				("bank_lunge leap (0.50s)", "bank_lunge", 0.50, 2),
				("drag_under grab (0.24s)", "drag_under", 0.24, 2),
				("drag_under haul (0.86s)", "drag_under", 0.86, 1),
			],
		},
		{
			"name": "mire_hag", "rig": mire_rig, "anims": mire_anims, "pal": "mire_hag", "block": False,
			"rest_drop": "mud_clump",
			"pose_frames": [
				("idle (1.5s)", "idle", 1.5, 2),
				("walk (0.25s)", "walk", 0.25, 1),
				("attack (0.18s)", "attack", 0.18, 2),
				("mud_scoop stoop (0.38s)", "mud_scoop", 0.38, 1),
				("mud_scoop lift (0.90s)", "mud_scoop", 0.90, 2),
				("mud_throw windup (0.26s)", "mud_throw", 0.26, 2),
				("mud_throw release (0.44s)", "mud_throw", 0.44, 2),
			],
		},
		{
			"name": "mud_scoop", "rig": scoop_rig, "anims": scoop_anims, "pal": "mud_scoop", "block": True,
			"pose_frames": [
				("idle (front-3/4)", "idle", 0.0, 2),
				("idle (side)", "idle", 0.0, 1),
				("settle (0.0s)", "settle", 0.0, 2),
			],
		},
		{
			"name": "grave_hag", "rig": grave_rig, "anims": grave_anims, "pal": "grave_hag", "block": False,
			"pose_frames": [
				("idle (1.5s)", "idle", 1.5, 2),
				("walk (0.0s)", "walk", 0.0, 1),
				("attack (0.16s)", "attack", 0.16, 2),
				("lash recoil (0.22s)", "tongue_lash", 0.22, 1),
				("tongue_lash (0.40s)", "tongue_lash", 0.40, 1),
				("tongue_lash (3/4, 0.40s)", "tongue_lash", 0.40, 2),
				("kill_surge (0.48s)", "kill_surge", 0.48, 2),
			],
		},
		{
			"name": "fog_wight", "rig": wight_rig, "anims": wight_anims, "pal": "fog_wight", "block": False,
			"pose_frames": [
				("idle (1.6s)", "idle", 1.6, 2),
				("walk (0.0s)", "walk", 0.0, 1),
				("attack (0.18s)", "attack", 0.18, 2),
				("fog_draw open (0.48s)", "fog_draw", 0.48, 2),
				("fog_draw pull (0.88s)", "fog_draw", 0.88, 2),
				("fade wrap (0.45s)", "fade", 0.45, 2),
				("emerge (0.34s)", "emerge", 0.34, 2),
				("decoy_wisp (0.56s)", "decoy_wisp", 0.56, 2),
			],
		},
		{
			"name": "fog_wight_wisp", "rig": wisp_rig, "anims": wisp_anims, "pal": "fog_wight_wisp", "block": False,
			"pose_frames": [
				("idle hover (1.4s)", "idle", 1.4, 2),
				("walk glide (0.5s)", "walk", 0.5, 1),
				("run rush (0.3s)", "run", 0.3, 1),
				("lure beckon (0.7s)", "lure", 0.7, 2),
				("fade burst (0.35s)", "fade", 0.35, 2),
			],
		},
	]

	for sp in specs:
		export_model_files(sp["rig"], sp["anims"], sp["pal"], is_block=sp["block"])

	if "--preview" in argv:
		pdir = argv[argv.index("--preview") + 1]
		render_all_previews(pdir, specs)


if __name__ == "__main__":
	main(sys.argv[1:])
