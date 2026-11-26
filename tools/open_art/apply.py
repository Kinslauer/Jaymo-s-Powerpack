#!/usr/bin/env python3
"""Puts the openly licensed art (tools/open_art/sources/, docs/Vanilla-Style.md "Open art sets") onto our textures.

Claude threads don't paint (Andy, 2026-11-26): these are other artists' CC0 and MIT pixels, used as they are. Run it
after the old generators (gen_*.py, vanilla_style/*.py), which would otherwise draw these textures again:

    python3 tools/open_art/apply.py
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent.parent
SRC = Path(__file__).resolve().parent / "sources"
PW = "src/main/resources/assets/jaymospowers/textures"
KD = "kingdoms/src/main/resources/assets/jaymoskingdoms/textures"

# Shade's steel sheet: (row, column) of a 16 px cell.
WEAPONS = {
	f"{KD}/item/silver_sword.png": (0, 3),
	f"{KD}/item/hidden_blade.png": (10, 6),
	f"{KD}/item/engineers_hammer.png": (10, 21),
}
# Whole 16 px files: target -> source.
FILES = {
	f"{PW}/block/farworld/moon_rock.png": "block_set/stone_generic.png",
	f"{PW}/block/farworld/glacia_rock.png": "block_set/ice_glacier.png",
	f"{PW}/block/farworld/verdant_rock.png": "block_set/serpentine.png",
	f"{PW}/block/gravity_machine_side.png": "militech/machine_core_6.png",
	f"{PW}/block/gravity_machine_side_on.png": "militech/machine_core_7.png",
	f"{PW}/block/gravity_machine_top.png": "militech/machine_plating_block.png",
	f"{PW}/block/gravity_machine_bottom.png": "militech/machine_frame_block.png",
	f"{PW}/block/fuel_works_side.png": "militech/iron_plating_block.png",
	f"{PW}/block/fuel_works_top.png": "militech/machine_plating_block.png",
	f"{PW}/block/landing_beacon.png": "militech/iron_plating_block.png",
}
# Master484's ship sheet: (colour group 0 blue, 1 green, 2 red, 3 yellow, 4 pink; row; column), 16 px cells on black.
# Pods by who flies them (Andy, 2026-11-26): your pod, the warrior-born raiders', a derelict's.
SHIPS = {
	f"{PW}/item/space_pod.png": (3, 9, 1),
	f"{PW}/gui/pod_raider.png": (2, 1, 2),
	f"{PW}/gui/pod_wreck.png": (3, 9, 0),
}


def ship(sheet, group, row, col):
	"""One ship, its black ground made clear (the ships' own darkest pixels are greys, never pure black)."""
	x, y = 28 + 110 * group + 20 * col, 42 + 20 * row
	cell = sheet.crop((x, y, x + 16, y + 16))
	px = cell.load()
	for i in range(16):
		for j in range(16):
			if px[i, j][:3] == (0, 0, 0):
				px[i, j] = (0, 0, 0, 0)
	return cell


def main():
	sheet = Image.open(SRC / "shade_weapons/steel-weapons.png").convert("RGBA")
	for target, (r, c) in WEAPONS.items():
		sheet.crop((c * 16, r * 16, c * 16 + 16, r * 16 + 16)).save(ROOT / target)
	ships = Image.open(SRC / "ship_collection/16ShipCollection.png").convert("RGBA")
	for target, spot in SHIPS.items():
		ship(ships, *spot).save(ROOT / target)
	for target, source in FILES.items():
		Image.open(SRC / source).convert("RGBA").save(ROOT / target)
	print(f"{len(WEAPONS) + len(SHIPS) + len(FILES)} textures from the open art sets")


if __name__ == "__main__":
	main()
