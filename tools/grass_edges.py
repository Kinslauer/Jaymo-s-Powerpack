"""Generates the "living world" built-in pack's grass and snow edges: a grass block's top edges get a fringe of its own
side-overlay blades leaning out over each side, and a snowy grass block a lip of snow, so ledges and hills have soft,
overhanging edges instead of a hard line. Each lip hides when a block stands against that side. Only vanilla's own
textures are referenced. Run from the root after tools/bushy_leaves.py: python -I tools/grass_edges.py"""
import json
import os

PACK = "src/main/resources/resourcepacks/living_world/"
# how far down a lip hangs (pixels) and how far it leans out
GRASS_DROP, SNOW_DROP, LEAN = 6, 3, 22.5
# side -> (from, to, rotation axis, angle sign), each a plane hinged on that side's top edge
SIDES = {
    "north": ("z", 0, "x", 1, ("north", "south")),
    "south": ("z", 16, "x", -1, ("north", "south")),
    "west": ("x", 0, "z", -1, ("west", "east")),
    "east": ("x", 16, "z", 1, ("west", "east")),
}


def lip(side, drop, texture, tinted):
    axis, at, rot_axis, sign, faces = SIDES[side]
    lo = 16 - drop
    if axis == "z":
        frm, to, origin = [0, lo, at], [16, 16, at], [8, 16, at]
    else:
        frm, to, origin = [at, lo, 0], [at, 16, 16], [at, 16, 8]
    face = {"uv": [0, 0, 16, drop], "texture": texture, "cullface": side}
    if tinted:
        face["tintindex"] = 0
    return {"from": frm, "to": to, "rotation": {"origin": origin, "axis": rot_axis, "angle": sign * LEAN},
            "faces": {f: face for f in faces}}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    models = PACK + "assets/jaymospowers/models/block/edges/"
    write(models + "grass_lips.json", {"ambientocclusion": False,
        "textures": {"particle": "minecraft:block/grass_block_side_overlay", "overlay": "minecraft:block/grass_block_side_overlay"},
        "elements": [lip(s, GRASS_DROP, "#overlay", True) for s in SIDES]})
    write(models + "snow_lips.json", {"ambientocclusion": False,
        "textures": {"particle": "minecraft:block/snow", "snow": "minecraft:block/snow"},
        "elements": [lip(s, SNOW_DROP, "#snow", False) for s in SIDES]})
    plain = [{"model": "minecraft:block/grass_block"}] + [{"model": "minecraft:block/grass_block", "y": y} for y in (90, 180, 270)]
    write(PACK + "assets/minecraft/blockstates/grass_block.json", {"multipart": [
        {"when": {"snowy": "false"}, "apply": plain},
        {"when": {"snowy": "false"}, "apply": {"model": "jaymospowers:block/edges/grass_lips"}},
        {"when": {"snowy": "true"}, "apply": {"model": "minecraft:block/grass_block_snow"}},
        {"when": {"snowy": "true"}, "apply": {"model": "jaymospowers:block/edges/snow_lips"}},
    ]})
    write(PACK + "pack.mcmeta", {"pack": {"description": "Jaymo's Powers: bushy leaves, grass and snow edges",
                                          "min_format": 97, "max_format": 97}})


if __name__ == "__main__":
    main()
